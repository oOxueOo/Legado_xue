package io.legado.app.ui.widget.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.HtmlCoverRenderer
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.model.BookCover
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.textHeight
import io.legado.app.utils.toStringArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 封面请求失败后的重试次数。
 *
 * View 版列表每次条目绑定都会重新发起请求（RecyclerView 复用即重试），Compose 条目只在
 * 首次组合时请求一次：一次失败就永久停在默认封面上，只有滚出屏幕再回来才会重来。
 * 用户反馈的"列表不显示封面、长按弹窗却能看到"正是这个差异（弹窗每次都新发请求）。
 */
private const val COVER_LOAD_ATTEMPTS = 2

/** 两次尝试之间的间隔：短退避即可，长等只会让封面"卡在占位图"更久 */
private const val COVER_LOAD_RETRY_DELAY_MS = 250L

/**
 * 单次封面请求的上限。
 *
 * `OkHttpStreamFetcher` 的读流/解密跑在自己的协程里，抛出的异常会被协程包装吞掉、
 * 不回调 Glide，请求于是永远停在 pending：挂起的加载既不成功也不失败，条目就一直
 * 保持占位封面。超时把这个"静默挂起"转成失败，交给上面的重试。
 */
private const val COVER_LOAD_TIMEOUT_MS = 12_000L

/** 横版卡背景模糊半径与压暗：既让空处不刺眼，也不抢前景 */
private val WIDE_COVER_BLUR = 18.dp
private const val WIDE_COVER_SCRIM = 0.18f

/**
 * 封面请求并发闸门。
 *
 * 从后台切回、主题重建后，整屏条目同帧发请求、同帧 decode，主线程会被一连串
 * 大图解码压住。这里限 3 个并发，并让同批请求按序号错帧进入，把解码摊到多帧上。
 */
private val coverLoadSemaphore = Semaphore(permits = 3)

/** 请求序号：只用来算错帧延迟，不求精确 */
private val coverLoadTicket = AtomicInteger(0)

// ── 立体封面（书架专用绘制）──────────────────────────────────────────────
// 参数与效果图一一对应：折面宽 4dp、色带宽 4dp、折痕区 4dp、圆角 2dp、竹节 3 道 0.7dp，
// 全部以 66dp 封面宽为基准按比例换算（见 AppDimens.SHELF_COVER_*_RATIO）。

/** 竹节位置：3 道，按封面高度百分比 */
private val COVER_RIDGE_POSITIONS = floatArrayOf(0.16f, 0.47f, 0.78f)

/** 右下压边颜色：比纯黑偏暖一点，像墨/纸的边而不是电子黑 */
private val COVER_PRESS_EDGE = Color(0xFF181410)

/** 内描边颜色（压住封面缩放后的毛边） */
private val COVER_INNER_STROKE = Color(0xFF968C82)

/**
 * 定向光影的软影层。
 *
 * 把同一个圆角矩形按 [blur] 由外向内叠 [steps] 层填充：最外层 alpha 趋 0、逐层向内累积到
 * [alpha]，形成连续衰减。不用 `BlurMaskFilter`（GPU 画布上会被忽略，直接变成一圈硬边），
 * 也不用 `Modifier.shadow`（那是四周均匀的 elevation，出不来"只往右下压"的实物感）。
 */
private fun DrawScope.softCoverShadow(
    rectSize: Size,
    corner: Float,
    offset: Offset,
    blur: Float,
    alpha: Float,
    steps: Int = 7,
) {
    if (blur <= 0f || alpha <= 0f) return
    var prev = 0f
    for (i in 1..steps) {
        // i = 1 是最外层（最大外扩），i = steps 是贴着封面轮廓的一层
        val grow = blur * (steps - i + 1).toFloat() / steps
        val t = (grow / blur).coerceIn(0f, 1f)
        val target = alpha * (1f - t) * (1f - t)
        val layerAlpha = (1f - (1f - target) / (1f - prev)).coerceIn(0f, 1f)
        prev = target
        if (layerAlpha <= 0f) continue
        drawRoundRect(
            color = Color.Black.copy(alpha = layerAlpha),
            topLeft = Offset(offset.x - grow, offset.y - grow),
            size = Size(rectSize.width + grow * 2, rectSize.height + grow * 2),
            cornerRadius = CornerRadius(corner + grow),
        )
    }
}

/** 立体封面的投影：远场 + 近场软影（向右下偏移）+ 只压右侧和下侧的接触影 */
private fun DrawScope.drawShelfCoverShadow(cornerPx: Float) {
    val w = size.width
    val h = size.height
    // 1 个"设计 dp"对应的像素数：封面宽 / 66
    val u = w / AppDimens.SHELF_COVER_STYLE_BASE_WIDTH
    val body = Size(w, h)

    // 远场：偏移 3 / 4.5dp，模糊 8dp，alpha 0.20
    softCoverShadow(body, cornerPx, Offset(3f * u, 4.5f * u), 8f * u, 0.20f)
    // 近场：偏移 1.5 / 2.5dp，模糊 4dp，alpha 0.36
    softCoverShadow(body, cornerPx, Offset(1.5f * u, 2.5f * u), 4f * u, 0.36f)

    // 接触影：贴着封面右沿/下沿往外 1.6dp 衰减到 0，是"书压在桌面上"的那道深线
    val contact = 1.6f * u
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
            startX = w,
            endX = w + contact,
        ),
        topLeft = Offset(w, cornerPx),
        size = Size(contact, (h - cornerPx * 2).coerceAtLeast(0f)),
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
            startY = h,
            endY = h + contact,
        ),
        topLeft = Offset(cornerPx, h),
        size = Size((w - cornerPx * 2).coerceAtLeast(0f), contact),
    )
}

/** 按亮度系数 [k] 与基础色混出膏体色：色带要用主色的明暗分层，而不是纯色块 */
private fun Color.shade(k: Float, alpha: Float): Color = Color(
    red = (red * k).coerceIn(0f, 1f),
    green = (green * k).coerceIn(0f, 1f),
    blue = (blue * k).coerceIn(0f, 1f),
    alpha = alpha,
)

/** 只加一道折痕：靠明暗把封面"折"出一道脊，不占任何颜色 */
private fun DrawScope.drawCoverCrease(u: Float) {
    val h = size.height
    val bw = AppDimens.SHELF_COVER_CREASE_RATIO * size.width
    // 折痕外侧（书脊面）微亮，往折线收
    val lit = bw * 0.42f
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.02f)),
            startX = 0f,
            endX = lit,
        ),
        topLeft = Offset.Zero,
        size = Size(lit, h),
    )
    // 折线一侧压暗
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f)),
            startX = lit,
            endX = bw,
        ),
        topLeft = Offset(lit, 0f),
        size = Size(bw - lit, h),
    )
    val lw = max(1f, 0.5f * u)
    // 折线（暗）+ 封面侧高光，一道折痕的"硬边界"就是这两根线
    drawRect(Color.Black.copy(alpha = 0.60f), Offset(bw - lw, 0f), Size(lw, h))
    drawRect(Color.White.copy(alpha = 0.29f), Offset(bw, 0f), Size(lw, h))
    // 高光向封面内侧渐隐 2dp
    val fade = 2f * u
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
            startX = bw + lw,
            endX = bw + lw + fade,
        ),
        topLeft = Offset(bw + lw, 0f),
        size = Size(fade, h),
    )
}

/** 半透明竹节书脊：主色 72% 叠在封面上，圆柱受光 + 3 道竹节 */
private fun DrawScope.drawCoverSpineBand(base: Color, u: Float) {
    val h = size.height
    val bw = AppDimens.SHELF_COVER_BAND_RATIO * size.width
    val bandAlpha = 0.72f
    // 横向圆柱受光：0.86 + 0.22·sin(0.8πt) - 0.34·max(0, t-0.5)，取 5 个色标近似
    val ks = floatArrayOf(0.86f, 0.99f, 1.07f, 0.98f, 0.82f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                base.shade(ks[0], bandAlpha),
                base.shade(ks[1], bandAlpha),
                base.shade(ks[2], bandAlpha),
                base.shade(ks[3], bandAlpha),
                base.shade(ks[4], bandAlpha),
            ),
            startX = 0f,
            endX = bw,
        ),
        topLeft = Offset.Zero,
        size = Size(bw, h),
    )
    val lw = max(1f, 0.5f * u)
    // 带内折线
    drawRect(Color.Black.copy(alpha = 0.20f), Offset(0.9f * u, 0f), Size(lw, h))
    // 右边界压暗 + 封面侧高光
    drawRect(Color.Black.copy(alpha = 0.43f), Offset(bw - lw, 0f), Size(lw, h))
    drawRect(Color.White.copy(alpha = 0.15f), Offset(bw, 0f), Size(lw, h))
    // 3 道竹节：每道一暗一亮
    val ridge = max(1f, 0.7f * u)
    COVER_RIDGE_POSITIONS.forEach { pos ->
        val y = h * pos
        drawRect(Color.Black.copy(alpha = 0.38f), Offset(0f, y), Size(bw, ridge))
        drawRect(Color.White.copy(alpha = 0.13f), Offset(0f, y + ridge), Size(bw, max(1f, 0.5f * u)))
    }
}

/** 立体封面的装饰层：折面 → 折痕/书脊 → 右下压边 → 内描边 */
private fun DrawScope.drawShelfCoverDecor(style: Int, cornerPx: Float, spineBase: Color) {
    val w = size.width
    val h = size.height
    val u = w / AppDimens.SHELF_COVER_STYLE_BASE_WIDTH
    val bevel = AppDimens.SHELF_COVER_BEVEL_RATIO * w

    // ① 左侧受光亮面
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
            startX = 0f,
            endX = bevel,
        ),
        topLeft = Offset.Zero,
        size = Size(bevel, h),
    )
    // ② 右侧背光暗面 + 向内 1.4dp 的余晕（避免 4dp 暗面收得太突然）
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
            startX = w - bevel,
            endX = w,
        ),
        topLeft = Offset(w - bevel, 0f),
        size = Size(bevel, h),
    )
    val tail = 1.4f * u
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.078f)),
            startX = w - bevel - tail,
            endX = w - bevel,
        ),
        topLeft = Offset(w - bevel - tail, 0f),
        size = Size(tail, h),
    )

    // ③ 档位装饰
    when (style) {
        AppDimens.SHELF_COVER_STYLE_CREASE -> drawCoverCrease(u)
        AppDimens.SHELF_COVER_STYLE_SPINE -> drawCoverSpineBand(spineBase, u)
    }

    // ④ 右下压边：给"书沿"一条硬边界，同时压住右侧暗面最重的一段
    val edge = max(1f, 0.7f * u)
    drawRect(
        color = COVER_PRESS_EDGE.copy(alpha = 0.59f),
        topLeft = Offset(w - edge, 0f),
        size = Size(edge, h),
    )
    drawRect(
        color = COVER_PRESS_EDGE.copy(alpha = 0.59f),
        topLeft = Offset(0f, h - edge),
        size = Size(w, edge),
    )

    // ⑤ 内描边：压住封面缩放后的毛边，整圈轮廓才干净。描边居中在轮廓线上，
    // 内缩半个线宽才不会被外层圆角裁剪掉一半
    val strokeWidth = max(1f, 0.5f * u)
    val half = strokeWidth / 2f
    drawRoundRect(
        color = COVER_INNER_STROKE.copy(alpha = 0.35f),
        topLeft = Offset(half, half),
        size = Size(w - strokeWidth, h - strokeWidth),
        cornerRadius = CornerRadius((cornerPx - half).coerceAtLeast(0f)),
        style = Stroke(width = strokeWidth),
    )
}

/**
 * 是否近似纯色封面：横版卡遇到纯色图不铺模糊（模糊纯色等于一块灰），直接填主色。
 * 采样 8×8 看通道极差，超过阈值就当作有内容。
 */
internal fun isFlatColorCover(drawable: Drawable?): Boolean {
    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return false
    return runCatching {
        val side = 8
        val scaled = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== bitmap) scaled.recycle()
        var minL = 255
        var maxL = 0
        pixels.forEach { px ->
            val l = (((px shr 16 and 0xFF) * 299 + (px shr 8 and 0xFF) * 587 +
                (px and 0xFF) * 114) / 1000)
            if (l < minL) minL = l
            if (l > maxL) maxL = l
        }
        maxL - minL <= FLAT_COVER_LUMA_RANGE
    }.getOrDefault(false)
}

/** 纯色判定阈值：亮度极差在这个范围内按纯色处理 */
private const val FLAT_COVER_LUMA_RANGE = 18

/**
 * 取封面主色：缩成 16×16 后按 S·V 加权求色相均值，饱和度 ×1.15、明度 ×0.46。
 *
 * 与效果图脚本 `dominant_color()` 同一套算法：先按权重求色相单位向量，再回推色相；
 * 明度压低 0.46 是为了让色带比封面更暗一档，看起来像"书脊比封面受光更少"。
 * 非位图封面（动图等）返回 null，调用方退回主题主色。
 */
internal fun dominantCoverColor(drawable: Drawable?): Color? {
    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null
    return runCatching {
        val side = 16
        val scaled = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== bitmap) scaled.recycle()

        val hsv = FloatArray(3)
        var weight = 0f
        var hx = 0f
        var hy = 0f
        var satSum = 0f
        var valSum = 0f
        pixels.forEach { px ->
            val r = (px shr 16 and 0xFF) / 255f
            val g = (px shr 8 and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            android.graphics.Color.RGBToHSV(
                (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv
            )
            val h = hsv[0] / 360f
            val s = hsv[1]
            val v = hsv[2]
            if (s > 0.18f && v > 0.12f) {
                val wt = s * v
                weight += wt
                hx += cos(2f * PI.toFloat() * h) * wt
                hy += sin(2f * PI.toFloat() * h) * wt
                satSum += s * wt
                valSum += v * wt
            }
        }
        if (weight <= 0f) return@runCatching null
        var hue = atan2(hy, hx) / (2f * PI.toFloat())
        if (hue < 0f) hue += 1f
        val sat = (satSum / weight * 1.15f).coerceIn(0f, 1f)
        val value = (valSum / weight * 0.46f).coerceIn(0f, 1f)
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, sat, value)))
    }.getOrNull()
}

/**
 * 书籍 / 分组封面的 Compose 实现。
 *
 * 与 View 版 `CoverImageView` 保持同一套取图优先级，顺序不能调换：
 * 封面图集默认封面 → HTML 模板封面 → "使用默认封面"开关 → 真实封面图片 → 默认封面；
 * 图片缺失或加载失败时在封面上叠加竖排书名与作者（由封面设置控制）。
 *
 * 加载走 Glide 的 **Drawable** 链路 + 显式 override 尺寸，与首页 `GlideImage`、旧 View 版
 * `CoverImageView` 同一条链路（`asBitmap()` 会丢掉 GIF/WebP 的动画帧）；
 * 组合离开时取消在途请求（`LaunchedEffect` 取消 → `clear` target），不借用 View 版 API。
 *
 * @param name 书名 / 分组名，用于默认封面的竖排书名与 HTML 模板变量
 * @param author 作者，用于默认封面的竖排作者与 HTML 模板变量
 * @param coverPath 已解析的展示封面路径（调用方传 `getDisplayCover()`），可为空
 * @param galleryIdentity 封面图集取图身份：书籍传 bookUrl、分组传 `bookGroup:{分组id}`
 * @param contentDescription 无障碍描述，一般传书名
 * @param sourceOrigin 书源来源标识，影响网络请求头与图集身份回退
 * @param cornerRadius 封面圆角
 * @param coverStyle 书架「立体封面」档位（无 / 光影 / 折痕 / 书脊）。只给书架（网格 / 列表）
 *   开启，搜索结果、书籍详情等封面传默认的 0，保持干净的纯图。
 * @param loadOnlyWifi 是否只允许 WiFi 下加载网络封面
 */
@Composable
fun AppBookCover(
    modifier: Modifier = Modifier,
    name: String?,
    author: String?,
    coverPath: String?,
    galleryIdentity: String?,
    contentDescription: String?,
    sourceOrigin: String? = null,
    cornerRadius: Dp = AppDimens.bookCoverCornerRadius,
    coverStyle: Int = AppDimens.SHELF_COVER_STYLE_NONE,
    loadOnlyWifi: Boolean = false,
    /** 横版填充（16:9 音视频卡）：整图 contain，空处用同帧模糊或主色补满 */
    wideFill: Boolean = false,
) {
    val context = LocalContext.current
    // 图集取图是"库查询 + 文件读取"（顺序模式还要回写缓存）：必须按取图身份记忆。
    // 放在组合里裸调会在每次重组时都查一次库，列表滚动时把主线程压满，封面迟迟画不出来
    val galleryCover = remember(galleryIdentity, coverPath) {
        BookCover.getGalleryDefaultCover(galleryIdentity, coverPath)
    }
    val realPath = galleryCover ?: coverPath?.takeIf { it.isNotBlank() }
    // 图集默认封面优先于"强制默认封面"：命中图集时仍显示图集封面
    val useDefaultCover = AppConfig.useDefaultCover && galleryCover == null
    // HTML 模板封面在 View 版里优先于"使用默认封面"，两者顺序不能调换
    val htmlCover = realPath == null && HtmlCoverRenderer.isApplicable(name)
    val drawName = BookCover.drawBookName && !name.isNullOrBlank()
    // 最终显示的是默认封面：既没有真实图片，或用户开启了"使用默认封面"。
    // 此时书名叠加属于"封面本身"的一部分，而不是加载失败的兜底
    val defaultCoverShown = !htmlCover && (realPath == null || useDefaultCover)

    val styled = coverStyle != AppDimens.SHELF_COVER_STYLE_NONE
    val shape = RoundedCornerShape(cornerRadius)
    // 主题主色的压暗版：封面取不到主色（动图 / 取色失败）时给色带兜底
    val themePrimary = MaterialTheme.colorScheme.primary
    val fallbackSpine = remember(themePrimary) {
        Color(
            red = themePrimary.red * 0.55f,
            green = themePrimary.green * 0.55f,
            blue = themePrimary.blue * 0.55f,
        )
    }
    var spineColor by remember(realPath, coverStyle) { mutableStateOf<Color?>(null) }

    var bounds by remember { mutableStateOf(IntSize.Zero) }
    // 拼请求键：滚动时条目会大量重组，用 remember 挡住每次的 list/字符串分配
    val requestKey = remember(realPath, sourceOrigin, htmlCover, useDefaultCover, name, author) {
        "$realPath|$sourceOrigin|$htmlCover|$useDefaultCover|$name|$author"
    }
    // 图集命中时 realPath 是图集里的图，失败后要退回的"真实封面"就是用户书源的封面地址
    val fallbackPath = coverPath?.takeIf { it.isNotBlank() && it != realPath }
    // 初值就是默认封面：与 View 版 placeholder(defaultDrawable) 一致，避免加载期间露出壁纸
    var drawable by remember(requestKey) { mutableStateOf(defaultCoverDrawable()) }
    var loadFailed by remember(requestKey) { mutableStateOf(false) }
    // 上次生效的缓存代际：只有代际真的变了才做"图还活着吗"的检查
    var seenGeneration by remember(requestKey) { mutableIntStateOf(CoverCacheSignal.generation) }

    LaunchedEffect(requestKey, bounds, CoverCacheSignal.generation) {
        // 等控件测量出尺寸后再发请求，保证 override 的是真实显示尺寸
        if (bounds.width <= 0 || bounds.height <= 0) return@LaunchedEffect
        // 缓存代际变化（清缓存 / 内存被回收 / 回到前台）：手上这张图还能画就直接用，
        // 不重发请求；只有已被回收、或本来就停在失败态时才补发，避免整屏重取图
        val generation = CoverCacheSignal.generation
        if (generation != seenGeneration) {
            seenGeneration = generation
            if (!loadFailed && isDrawableUsable(drawable)) return@LaunchedEffect
        }
        val loaded = when {
            htmlCover -> htmlCoverDrawable(context, name.orEmpty(), author)
            useDefaultCover -> defaultCoverDrawable()
            realPath != null -> loadCoverDrawable(
                context = context,
                path = realPath,
                sourceOrigin = sourceOrigin,
                loadOnlyWifi = loadOnlyWifi,
                requestSize = bounds,
            ) ?: fallbackPath?.let {
                // 取图优先级是"图集 → 真实图片 → HTML → 默认封面"，图集那张取不到时必须继续往下走，
                // 否则图集文件缺失/损坏会表现为"整屏封面都退化成默认封面"
                loadCoverDrawable(
                    context = context,
                    path = it,
                    sourceOrigin = sourceOrigin,
                    loadOnlyWifi = loadOnlyWifi,
                    requestSize = bounds,
                )
            }

            else -> defaultCoverDrawable()
        }
        // 加载失败回退默认封面（同名叠层由 defaultCoverShown/loadFailed 决定）
        val shown = loaded ?: defaultCoverDrawable()
        drawable = shown
        startIfAnimatable(shown)
        loadFailed = loaded == null
    }

    // 书脊档要取封面主色：16×16 采样是纯 CPU 活，放到后台线程，避免列表滚动时卡主线程。
    // 单独放一个 effect、以 drawable 为键：切档位不必重发封面请求，图换了才重算一次。
    LaunchedEffect(drawable, coverStyle) {
        if (coverStyle == AppDimens.SHELF_COVER_STYLE_SPINE) {
            spineColor = withContext(Dispatchers.Default) { dominantCoverColor(drawable) }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { bounds = it }
            .then(
                // 投影画在裁剪之前：clip 之后阴影会被一起裁掉，投影必须落在外层。
                // 立体封面自己画定向光影，这里只负责"无"档的原 elevation 软影。
                if (!styled && AppConfig.bookCoverShadow) {
                    Modifier.shadow(
                        elevation = AppDimens.bookCoverShadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.16f),
                        spotColor = Color.Black.copy(alpha = 0.26f),
                    )
                } else {
                    Modifier
                }
            )
    ) {
        if (styled) {
            // 定向光影要落到封面外侧，只能画在未被裁剪的外层上
            Canvas(modifier = Modifier.matchParentSize()) {
                drawShelfCoverShadow(cornerRadius.toPx())
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    if (AppConfig.bookCoverShadow || styled) MaterialTheme.colorScheme.background
                    else Color.Transparent
                )
        ) {
            val cover = drawable
            val withText = drawName && !htmlCover && (defaultCoverShown || loadFailed)
            if (cover != null) {
                val painter = remember(cover) { AppDrawablePainter(cover) }
                if (wideFill) {
                    // 横版卡：背景层铺满（纯色封面填主色，其余用同帧模糊），前景层整图不裁切。
                    // 模糊在 API 31 以下不生效，背景退化成"同图裁切"——比留黑边好接受
                    val flat = remember(cover) { isFlatColorCover(cover) }
                    val tint = remember(cover) { dominantCoverColor(cover) }
                    if (flat && tint != null) {
                        Box(modifier = Modifier.fillMaxSize().background(tint)) { }
                    } else {
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().blur(WIDE_COVER_BLUR),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = WIDE_COVER_SCRIM))
                        ) { }
                    }
                    Image(
                        painter = painter,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Image(
                        painter = painter,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            // 有真实图片时只在加载失败后叠加书名；默认封面场景则始终叠加
            if (withText) {
                BookCoverTextOverlay(
                    name = name.orEmpty(),
                    author = author.orEmpty(),
                    drawAuthor = BookCover.drawBookAuthor,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // 立体封面装饰压在图上：折面、折痕、色带、压边、内描边
            if (styled) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawShelfCoverDecor(
                        style = coverStyle,
                        cornerPx = cornerRadius.toPx(),
                        spineBase = spineColor ?: fallbackSpine,
                    )
                }
            }
        }
    }
}

/**
 * 封面上的竖排书名 / 作者叠层。
 *
 * 书名从左上 20% 位置竖排、作者从右下 95% 位置向上竖排，均带主题背景色描边；
 * 与 View 版 `CoverImageView.generateCoverBitmap` 的绘制参数保持一致。
 */
@Composable
internal fun BookCoverTextOverlay(
    name: String,
    author: String,
    drawAuthor: Boolean,
    modifier: Modifier = Modifier,
) {
    // 描边取主题背景色、文字取强调色，与 View 版 generateCoverBitmap 的取色一致
    val bgColor = MaterialTheme.colorScheme.background.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    // 画笔复用：Canvas 的绘制 lambda 每帧都会跑，原来在里面 new 两个 TextPaint 是每帧一分配
    val namePaint = remember { TextPaint() }
    val authorPaint = remember { TextPaint() }

    Canvas(modifier = modifier) {
        val viewWidth = size.width
        val viewHeight = size.height
        val canvas = drawContext.canvas.nativeCanvas

        namePaint.apply {
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = viewWidth / 7f
            strokeWidth = textSize / 6f
        }

        val nameChars = name.toStringArray()
        var startX = viewWidth * 0.2f
        var startY = viewHeight * 0.2f
        var line = 0
        nameChars.forEachIndexed { index, char ->
            namePaint.color = bgColor
            namePaint.style = Paint.Style.STROKE
            canvas.drawText(char, startX, startY, namePaint)
            namePaint.color = accentColor
            namePaint.style = Paint.Style.FILL
            canvas.drawText(char, startX, startY, namePaint)
            startY += namePaint.textHeight
            if (startY > viewHeight * 0.9f) {
                if ((nameChars.size - index - 1) == 1) {
                    startY -= namePaint.textHeight / 5f
                    namePaint.textSize = viewWidth / 9f
                    return@forEachIndexed
                }
                startX += namePaint.textSize
                line++
                namePaint.textSize = viewWidth / 10f
                startY = viewHeight * 0.2f + namePaint.textHeight * line
            } else if (startY > viewHeight * 0.8f && (nameChars.size - index - 1) > 2) {
                startX += namePaint.textSize
                line++
                namePaint.textSize = viewWidth / 10f
                startY = viewHeight * 0.2f + namePaint.textHeight * line
            }
        }

        if (drawAuthor && author.isNotBlank()) {
            authorPaint.apply {
                typeface = Typeface.DEFAULT
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = viewWidth / 10f
                strokeWidth = textSize / 5f
            }
            val authorChars = author.toStringArray()
            startX = viewWidth * 0.8f
            startY = viewHeight * 0.95f - authorChars.size * authorPaint.textHeight
            startY = maxOf(startY, viewHeight * 0.3f)
            authorChars.forEach {
                authorPaint.color = bgColor
                authorPaint.style = Paint.Style.STROKE
                canvas.drawText(it, startX, startY, authorPaint)
                authorPaint.color = accentColor
                authorPaint.style = Paint.Style.FILL
                canvas.drawText(it, startX, startY, authorPaint)
                startY += authorPaint.textHeight
                if (startY > viewHeight * 0.95f) return@forEach
            }
        }
    }
}

/**
 * 加载真实封面图片为 [Drawable]。
 *
 * 必须走 Drawable 而不是 Bitmap：Glide 的 `asBitmap()` 对 GIF/WebP 动图只解出第一帧，
 * 动画信息在目标类型处就丢了。Drawable 链路与旧 View 版 `CoverImageView`、首页 `GlideImage`
 * 一致，动图能正常播放，静态图（含透明 PNG）行为也不变。
 *
 * 单次请求带超时、失败后有界重试，原因见 [COVER_LOAD_TIMEOUT_MS]、[COVER_LOAD_ATTEMPTS]。
 *
 * @param centerCrop 请求是否 centerCrop；瀑布流等自由比例场景传 false，
 *   保持图片原始宽高比（对齐 View 版 CoverLoader 的 fixedRatio = false）
 */
internal suspend fun loadCoverDrawable(
    context: Context,
    path: String,
    sourceOrigin: String?,
    loadOnlyWifi: Boolean,
    requestSize: IntSize,
    centerCrop: Boolean = true,
): Drawable? {
    // 同批请求按序号错开 0–24ms 再进闸门：让解码落在不同帧上，而不是挤在同一帧
    val ticket = coverLoadTicket.getAndIncrement()
    if (ticket % 4 != 0) delay((ticket % 4) * 8L)
    return coverLoadSemaphore.withPermit {
        repeat(COVER_LOAD_ATTEMPTS) { attempt ->
            val loaded = withTimeoutOrNull(COVER_LOAD_TIMEOUT_MS) {
                loadCoverDrawableOnce(
                    context, path, sourceOrigin, loadOnlyWifi, requestSize, centerCrop
                )
            }
            if (loaded != null) return@withPermit loaded
            if (attempt < COVER_LOAD_ATTEMPTS - 1) delay(COVER_LOAD_RETRY_DELAY_MS)
        }
        null
    }
}

/**
 * 手上的封面图还能不能画。
 *
 * 位图被回收（OOM 回收、Glide 清缓存）后 Drawable 本身仍在，画出来却是空白，
 * 这种情况必须当成"没有图"重取；动图等非位图 Drawable 一律按可用处理。
 */
private fun isDrawableUsable(drawable: Drawable?): Boolean = when (drawable) {
    null -> false
    is BitmapDrawable -> drawable.bitmap?.let { !it.isRecycled } ?: false
    else -> true
}

/** [loadCoverDrawable] 的单次请求实现 */
private suspend fun loadCoverDrawableOnce(
    context: Context,
    path: String,
    sourceOrigin: String?,
    loadOnlyWifi: Boolean,
    requestSize: IntSize,
    centerCrop: Boolean,
): Drawable? = suspendCancellableCoroutine { cont ->
    // 先在协程存活时取到 RequestManager：取消回调里 Activity 可能已 destroy，
    // 那时再 Glide.with(context) 会抛 "You cannot start a load for a destroyed activity"
    val requestManager = Glide.with(context)
    val resumed = AtomicBoolean(false)
    val target = object : CustomTarget<Drawable>() {
        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
            if (resumed.compareAndSet(false, true) && cont.isActive) {
                cont.resume(resource)
            }
        }

        override fun onLoadCleared(placeholder: Drawable?) = Unit

        override fun onLoadFailed(errorDrawable: Drawable?) {
            if (resumed.compareAndSet(false, true) && cont.isActive) {
                cont.resume(null)
            }
        }
    }
    cont.invokeOnCancellation {
        runCatching { requestManager.clear(target) }
    }
    var options = RequestOptions().set(OkHttpModelLoader.loadOnlyWifiOption, loadOnlyWifi)
    if (sourceOrigin != null) {
        options = options.set(OkHttpModelLoader.sourceOriginOption, sourceOrigin)
    }
    var builder = ImageLoader.load(context, path).apply(options)
    if (centerCrop) {
        builder = builder.centerCrop()
    }
    // 高清封面设置开启时不做降采样，与 View 版行为一致；
    // override 返回的是同一个 RequestBuilder（原地修改），这里回写只是让 CheckResult 告警消失
    if (!AppConfig.loadCoverHighQuality) {
        builder = builder.override(requestSize.width, requestSize.height)
    }
    builder.into(target)
}

/**
 * 默认封面 Drawable。
 *
 * [BookCover.defaultDrawable] 由 600x900 的位图或内置 jpg 构造，两种情况下都是 BitmapDrawable，
 * 直接沿用这个实例即可（与 View 版 `placeholder(defaultDrawable)` 取的是同一个对象）。
 */
private fun defaultCoverDrawable(): Drawable? = runCatching { BookCover.defaultDrawable }.getOrNull()

/** HTML 模板封面渲染成位图后包成 Drawable，与真实封面走同一条绘制链路 */
private suspend fun htmlCoverDrawable(context: Context, name: String, author: String?): Drawable? =
    runCatching { HtmlCoverRenderer.load(name, author) }
        .getOrNull()
        ?.let { BitmapDrawable(context.resources, it) }

// ── 预览（navigation-preview.md §10.1 强制）────────────────────────────────

@Preview(name = "无封面（叠加书名）")
@Composable
private fun AppBookCoverPreview() {
    LegadoTheme {
        AppBookCover(
            modifier = Modifier.size(width = 66.dp, height = 88.dp),
            name = "剑来",
            author = "烽火戏诸侯",
            coverPath = null,
            galleryIdentity = null,
            contentDescription = null,
        )
    }
}

@Preview(name = "有封面", showBackground = true)
@Composable
private fun AppBookCoverWithPathPreview() {
    LegadoTheme {
        AppBookCover(
            modifier = Modifier.size(width = 66.dp, height = 88.dp),
            name = "剑来",
            author = "烽火戏诸侯",
            coverPath = "file:///android_asset/preview_cover.jpg",
            galleryIdentity = "preview",
            contentDescription = null,
        )
    }
}

@Preview(name = "立体封面·书脊档", showBackground = true)
@Composable
private fun AppBookCoverSpinePreview() {
    LegadoTheme {
        AppBookCover(
            modifier = Modifier.size(width = 66.dp, height = 88.dp),
            name = "剑来",
            author = "烽火戏诸侯",
            coverPath = "file:///android_asset/preview_cover.jpg",
            galleryIdentity = "preview",
            contentDescription = null,
            cornerRadius = AppDimens.shelfCoverCornerRadius,
            coverStyle = AppDimens.SHELF_COVER_STYLE_SPINE,
        )
    }
}
