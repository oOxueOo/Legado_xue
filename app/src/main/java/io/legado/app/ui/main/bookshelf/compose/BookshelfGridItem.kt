package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.AppBookCover

/** 网格书名显示位置：0 封面下方 / 1 不显示 / 2 叠在封面上（与 AppConfig.showBookname 取值一致） */
private const val SHOW_BOOK_NAME_BELOW = 0
private const val SHOW_BOOK_NAME_ON_COVER = 2

/**
 * 网格书名：整名换行，最多三行；三行装不下就逐档降字号，务必显示全名。
 *
 * 关键坑：Compose 的 Text 在"高度不够"时也会加省略号（不只是 maxLines 不够）。
 * 之前块高按 3×14sp=42dp 写死，而 12sp 的书名行高 15sp，三行要 45dp，
 * 于是"实测两行、实际三行"的长名被高度判定溢出，第二行末尾直接变省略号。
 * 现在：行高统一 1.2 倍字号（12sp→14.4sp），块高 46dp，三行最坏 43.2dp，留 2.8dp 余量；
 * 量行时与绘制用同一套样式（显式 letterSpacing=0），避免"量出来两行、画出来三行"。
 */
private const val SHELF_GRID_NAME_TEXT_SIZE = 12f
private const val SHELF_GRID_NAME_MAX_LINES = 3
private const val SHELF_GRID_NAME_NORMAL_LINES = 2
/** 三行时的降字号台阶：11 → 10.5 → 10 → 9.5 → 9，取第一个能三行装下的 */
private val SHELF_GRID_NAME_DENSE_STEPS = floatArrayOf(11f, 10.5f, 10f, 9.5f, 9f)
private const val SHELF_GRID_NAME_LINE_HEIGHT_RATIO = 1.2f
/** 书名块高：按三行 15sp 留足余量，任何字号三行都装得下，不会被高度截断 */
private const val SHELF_GRID_NAME_BLOCK_HEIGHT_SP = 46f
private const val SHELF_GRID_OVERLAY_NAME_TEXT_SIZE = 11
private const val SHELF_GRID_OVERLAY_NAME_LINE_HEIGHT = 14

/** 封面底部渐变遮罩，对齐原 bg_gradient_cover（底部黑 63% → 30% 处 38% → 顶部透明） */
private val gridNameOverlayBrush = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.3f to Color.Black.copy(alpha = 0.38f),
    1.0f to Color.Black.copy(alpha = 0.63f)
)

/**
 * 书架网格条目。
 *
 * 与 View 版网格适配器一致：封面、未读角标或更新中转圈、封面底部阅读进度条，
 * 书名的位置由"书名显示位置"开关决定（封面下方 / 不显示 / 叠在封面上）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfGridItem(
    modifier: Modifier = Modifier,
    bookItem: BookshelfBookItem,
    displayConfig: BookshelfDisplayConfig,
    boardOverhang: Dp = AppDimens.shelfBoardOverhang,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(bookItem) },
                onLongClick = { onLongClick(bookItem) },
            )
            // 搁板画在条目底边之下、向下溢进行距：绘制区取整格（含内边距），
            // 左右各越出半个列距，与邻格的板严丝合缝拼成一条
            .shelfBoard(overhang = boardOverhang)
            .padding(AppDimens.shelfGridItemPadding),
    ) {
        Box {
            AppBookCover(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / AppDimens.BOOK_COVER_ASPECT),
                name = bookItem.display.name,
                author = bookItem.display.author,
                coverPath = bookItem.display.getDisplayCover(),
                galleryIdentity = bookItem.display.bookUrl,
                sourceOrigin = bookItem.display.origin,
                contentDescription = bookItem.display.name,
                shelfStyle = true,
            )
            bookItem.readProgress?.let { progress ->
                // 进度条内嵌在封面里：左右与下沿各留 4dp、2dp 圆角圆头，不再贴底糊成一条边
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = AppDimens.shelfCoverProgressInset,
                            end = AppDimens.shelfCoverProgressInset,
                            bottom = AppDimens.shelfCoverProgressInset,
                        )
                        .height(AppDimens.shelfProgressThickness)
                        .clip(RoundedCornerShape(AppDimens.shelfCoverProgressCorner))
                        .background(
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = AppDimens.SHELF_PROGRESS_TRACK_ALPHA)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            BookshelfItemStatus(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppDimens.shelfBadgeMargin),
                bookItem = bookItem,
                showUnread = displayConfig.showUnread,
                loadingSize = AppDimens.shelfGridLoadingSize,
            )
            if (displayConfig.showBookName == SHOW_BOOK_NAME_ON_COVER) {
                Text(
                    text = bookItem.display.name,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(gridNameOverlayBrush)
                        .padding(
                            start = AppDimens.shelfGridOverlayNamePadding,
                            end = AppDimens.shelfGridOverlayNamePadding,
                            top = AppDimens.shelfMetaFirstSpacing,
                            bottom = AppDimens.shelfGridOverlayNamePadding,
                        ),
                    color = Color.White,
                    fontSize = SHELF_GRID_OVERLAY_NAME_TEXT_SIZE.sp,
                    lineHeight = SHELF_GRID_OVERLAY_NAME_LINE_HEIGHT.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (displayConfig.showBookName == SHOW_BOOK_NAME_BELOW) {
            BookshelfGridName(
                name = bookItem.display.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimens.shelfGridNameSpacing),
            )
        }
    }
}

/**
 * 网格书名：整名最多三行，尽量不省略。
 *
 * 早先按标点把书名拆成「主标题 singleLine + 副标题」，主标题只准一行，长名必然省略号；
 * 之后改成整名换行，但块高只按 3×14sp=42dp 留，而 12sp 的行高 15sp 三行要 45dp，
 * 只要"量出来两行、画出来三行"就会被高度判定溢出，第二行末尾变省略号
 * （"用九千万亿警用金在缅北开派出所"就是这么被截的）。
 * 现在：行高统一 1.2 倍字号，块高 46dp（三行最坏 43.2dp，留余量），
 * 量行与绘制用同一套样式（显式 letterSpacing=0），两行装得下用 12sp，否则逐档降到能三行装下的字号。
 */
@Composable
private fun BookshelfGridName(name: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val baseStyle = LocalTextStyle.current
    val blockHeight = remember(density) {
        with(density) { SHELF_GRID_NAME_BLOCK_HEIGHT_SP.sp.toDp() }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val measurer = rememberTextMeasurer()
        val nameStyle = remember(name, widthPx, baseStyle) {
            // 量行与绘制必须同一套样式：显式给 letterSpacing，避免主题默认字距把行数算少
            fun styleOf(size: Float) = baseStyle.copy(
                fontSize = size.sp,
                lineHeight = (size * SHELF_GRID_NAME_LINE_HEIGHT_RATIO).sp,
                letterSpacing = 0.sp,
            )
            fun linesAt(size: Float): Int = measurer.measure(
                text = AnnotatedString(name),
                style = styleOf(size),
                maxLines = Int.MAX_VALUE,
                softWrap = true,
                constraints = Constraints(maxWidth = widthPx),
            ).lineCount

            val size = when {
                // 两行内就用标准字号
                linesAt(SHELF_GRID_NAME_TEXT_SIZE) <= SHELF_GRID_NAME_NORMAL_LINES ->
                    SHELF_GRID_NAME_TEXT_SIZE
                // 需要三行：逐档降字号，取第一个能装下三行的
                else -> SHELF_GRID_NAME_DENSE_STEPS.firstOrNull {
                    linesAt(it) <= SHELF_GRID_NAME_MAX_LINES
                } ?: SHELF_GRID_NAME_DENSE_STEPS.last()
            }
            styleOf(size)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = name,
                modifier = Modifier.fillMaxWidth(),
                style = nameStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                minLines = 1,
                maxLines = SHELF_GRID_NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
