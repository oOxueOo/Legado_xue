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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.AppBookCover

/** 网格书名显示位置：0 封面下方 / 1 不显示 / 2 叠在封面上（与 AppConfig.showBookname 取值一致） */
private const val SHOW_BOOK_NAME_BELOW = 0
private const val SHOW_BOOK_NAME_ON_COVER = 2

/** 网格书名：主标题 12sp 单行、副标题 10sp 单行，两行总高恒定 */
private const val SHELF_GRID_NAME_TEXT_SIZE = 12
private const val SHELF_GRID_NAME_LINE_HEIGHT = 15
private const val SHELF_GRID_SUB_TEXT_SIZE = 10
private const val SHELF_GRID_SUB_LINE_HEIGHT = 13
private const val SHELF_GRID_OVERLAY_NAME_TEXT_SIZE = 11
private const val SHELF_GRID_OVERLAY_NAME_LINE_HEIGHT = 14

/**
 * 主标题放不下时逐档降字号。
 *
 * 原实现固定 12sp、两行换行，长书名只能硬断在字中间（"最强毒｜士"）；
 * 现在主标题单行不折，宽度不够先降字号（12 → 11.5 → 11 → 10.5），仍放不下才省略号。
 */
private val SHELF_GRID_NAME_FONT_STEPS = listOf(
    SHELF_GRID_NAME_TEXT_SIZE.toFloat(), 11.5f, 11f, 10.5f,
)

/** 书名主副标题的分隔符：冒号 / 破折号 / 间隔号 / 波浪号 / 括号 / 竖线 */
private val SHELF_NAME_SPLIT_MARKS = charArrayOf(
    '：', ':', '—', '－', '～', '·', '｜', '|', '（', '(', '【', '[',
)

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
 * 网格书名：主标题 + 副标题两行。
 *
 * 主标题按标点切出（"斗罗大陆：绝世唐门" → 主「斗罗大陆」、副「绝世唐门」），
 * 单行显示、放不下时逐档降字号，仍放不下才省略号；副标题固定 10sp 单行。
 * 两行总高恒定，不同书名之间格子高度不跳。
 */
@Composable
private fun BookshelfGridName(name: String, modifier: Modifier = Modifier) {
    val parts = remember(name) { splitShelfName(name) }
    val measurer = rememberTextMeasurer()
    val baseStyle = LocalTextStyle.current
    val density = LocalDensity.current
    // TextUnit 没有加法，两行高度分别换算再相加；随密度缓存，重排时不再重复换算
    val blockHeight = remember(density) {
        with(density) {
            SHELF_GRID_NAME_LINE_HEIGHT.sp.toDp() + SHELF_GRID_SUB_LINE_HEIGHT.sp.toDp()
        }
    }
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        // 留 2% 余量：测量宽度与真实排版有零点几像素的差，卡在临界上会偶发省略号
        val limitPx = widthPx.toFloat() * 0.98f
        val mainFontSize = remember(parts.first, widthPx, baseStyle) {
            SHELF_GRID_NAME_FONT_STEPS.firstOrNull { step ->
                measurer.measure(
                    text = parts.first,
                    style = baseStyle.copy(fontSize = step.sp),
                    maxLines = 1,
                ).size.width <= limitPx
            } ?: SHELF_GRID_NAME_FONT_STEPS.last()
        }
        // 样式随字号缓存：滚动时条目会反复重组，没必要每帧重建两个 TextStyle
        val mainStyle = remember(mainFontSize, baseStyle) {
            baseStyle.copy(
                fontSize = mainFontSize.sp,
                lineHeight = SHELF_GRID_NAME_LINE_HEIGHT.sp,
            )
        }
        val subStyle = remember(baseStyle) {
            baseStyle.copy(
                fontSize = SHELF_GRID_SUB_TEXT_SIZE.sp,
                lineHeight = SHELF_GRID_SUB_LINE_HEIGHT.sp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = parts.first,
                modifier = Modifier.fillMaxWidth(),
                style = mainStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (parts.second.isNotEmpty()) {
                Text(
                    text = parts.second,
                    modifier = Modifier.fillMaxWidth(),
                    style = subStyle,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 把书名切成「主标题 to 副标题」，没有分隔符时副标题为空 */
private fun splitShelfName(raw: String): Pair<String, String> {
    val text = raw.trim()
    val index = text.indexOfFirst { char -> SHELF_NAME_SPLIT_MARKS.contains(char) }
    // 分隔符出现在首位（书名本身以括号开头）时不拆，整体当主标题
    if (index <= 0) return text to ""
    val main = text.substring(0, index).trim().trimEnd('，', ',', '、').trim()
    val sub = text.substring(index).trim().trim(
        '（', '）', '(', ')', '【', '】', '[', ']', '：', ':', '—', '－', '～', '·', '｜', '|',
    )
    return main.ifEmpty { text } to sub
}
