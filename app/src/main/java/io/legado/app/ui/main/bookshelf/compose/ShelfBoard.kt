package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.theme.AppDimens
import kotlin.math.abs

/**
 * 网格底部的整条搁板（书架造型方案 B）。
 *
 * 每个条目在自己的底部画出板的一段，左右各越出半个列距，相邻两段拼成一条连续板。
 * 颜色不写死，从主题的 background / primary（强调色）现算，八套主题昼夜切换都跟着走；
 * 板色若与背景亮度太接近会自动再加一档，否则浅色主题上板会彻底消失。
 */
@Composable
fun Modifier.shelfBoard(
    overhang: Dp = AppDimens.shelfBoardOverhang,
): Modifier {
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val eInk = AppConfig.isEInkMode
    val colors = remember(background, accent, eInk) {
        ShelfBoardColors.of(background, accent, eInk)
    }
    val density = LocalDensity.current
    val metrics = remember(overhang, density, eInk) {
        with(density) {
            ShelfBoardMetrics(
                height = AppDimens.shelfBoardHeight.toPx(),
                shadow = AppDimens.shelfBoardShadow.toPx(),
                lineWidth = AppDimens.shelfBoardLineWidth.toPx(),
                overhang = overhang.toPx(),
                eInk = eInk,
            )
        }
    }
    return this.drawBehind {
        drawShelfBoard(metrics, colors)
    }
}

/** 搁板的像素几何，随密度算一次缓存，绘制时不再做 Dp 换算 */
@Immutable
private class ShelfBoardMetrics(
    val height: Float,
    val shadow: Float,
    val lineWidth: Float,
    val overhang: Float,
    val eInk: Boolean,
)

/** 搁板的派生色：板面 / 顶高光 / 底边 / 投影渐变 */
@Immutable
class ShelfBoardColors(
    val face: Color,
    val highlight: Color,
    val edge: Color,
    val shadowAlpha: Float,
) {

    /**
     * 投影渐变。
     *
     * 渐变的起止用相对坐标，绘制时自动铺满板下那块矩形，因此可以随颜色一起缓存；
     * 放在绘制里现建会让每个条目每帧都分配一个 Brush。
     */
    val shadowBrush: Brush = if (shadowAlpha <= 0f) {
        Brush.verticalGradient(0.0f to Color.Transparent, 1.0f to Color.Transparent)
    } else {
        Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = shadowAlpha),
            1.0f to Color.Transparent
        )
    }

    companion object {

        /** 亮度低于这个值按夜间主题处理：夜间板面靠提亮 + 顶高光立起来，投影几乎看不见 */
        private const val NIGHT_LUMINANCE = 0.35f

        /** 板面与背景的亮度差下限，低于它会自行再加一档混合 */
        private const val MIN_CONTRAST = 0.06f

        private val White = Color.White
        private val Black = Color.Black

        fun of(background: Color, accent: Color, eInk: Boolean): ShelfBoardColors {
            if (eInk) {
                return ShelfBoardColors(
                    face = lerp(background, accent, 0.55f),
                    highlight = Color.Transparent,
                    edge = Color.Transparent,
                    shadowAlpha = 0f,
                )
            }
            val isNight = background.luminance() < NIGHT_LUMINANCE
            val target = if (isNight) White else accent
            val base = 0.20f
            val boosted = 0.30f
            var face = lerp(background, target, base)
            if (abs(face.luminance() - background.luminance()) < MIN_CONTRAST) {
                face = lerp(background, target, boosted)
            }
            return ShelfBoardColors(
                face = face,
                highlight = White.copy(alpha = if (isNight) 0.90f else 0.40f),
                edge = lerp(face, Black, if (isNight) 0.40f else 0.22f),
                shadowAlpha = if (isNight) 0.60f else 0.32f,
            )
        }
    }
}

private fun DrawScope.drawShelfBoard(metrics: ShelfBoardMetrics, colors: ShelfBoardColors) {
    val left = -metrics.overhang
    val right = size.width + metrics.overhang
    val width = right - left
    if (width <= 0f) return

    if (metrics.eInk) {
        // 电子墨水：一条实线即可，渐变与投影只会糊成灰块
        drawRect(
            color = colors.face,
            topLeft = Offset(left, size.height - metrics.lineWidth),
            size = Size(width, metrics.lineWidth),
        )
        return
    }

    val top = size.height
    drawRect(
        brush = colors.shadowBrush,
        topLeft = Offset(left, top + metrics.height),
        size = Size(width, metrics.shadow),
    )
    // 板面用直角矩形而不是圆角：一段板只是整条的一节，做圆角会在每个接缝处留一个缺口，
    // 拼起来像虚线槽；整条的圆角只有行首行尾需要，在这里判断不到，索性保平整
    drawRect(
        color = colors.face,
        topLeft = Offset(left, top),
        size = Size(width, metrics.height),
    )
    val hairline = metrics.lineWidth / 2
    drawLine(
        color = colors.highlight,
        start = Offset(left, top + hairline),
        end = Offset(right, top + hairline),
        strokeWidth = metrics.lineWidth,
    )
    drawLine(
        color = colors.edge,
        start = Offset(left, top + metrics.height - hairline),
        end = Offset(right, top + metrics.height - hairline),
        strokeWidth = metrics.lineWidth,
    )
}
