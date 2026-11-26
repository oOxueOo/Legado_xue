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

/** 网格书名：两行内 12sp；需要三行时降到 11sp。块高固定三行，格子高度一致 */
private const val SHELF_GRID_NAME_TEXT_SIZE = 12
private const val SHELF_GRID_NAME_LINE_HEIGHT = 15
private const val SHELF_GRID_NAME_DENSE_TEXT_SIZE = 11
private const val SHELF_GRID_NAME_DENSE_LINE_HEIGHT = 14
private const val SHELF_GRID_NAME_MAX_LINES = 3
private const val SHELF_GRID_NAME_NORMAL_LINES = 2
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
 * 网格书名：整名最多三行。
 *
 * 上一版按标点把书名拆成「主标题 singleLine + 副标题」两行：主标题只准占一行，
 * 放不下就从 12sp 逐档降到 10.5sp，再不行就省略号——所以"我，最强毒士…"这类长名
 * 永远显示不全。现在不拆主副标题，整名按宽度换行：
 * 两行能装下就用 12sp；要三行才装得下就降到 11sp（行高 14sp）。
 * 块高固定三行（3 × 14sp），不管书名几行，格子高度都一样、搁板不歪。
 */
@Composable
private fun BookshelfGridName(name: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val baseStyle = LocalTextStyle.current
    val blockHeight = remember(density) {
        with(density) { (SHELF_GRID_NAME_DENSE_LINE_HEIGHT * SHELF_GRID_NAME_MAX_LINES).sp.toDp() }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val measurer = rememberTextMeasurer()
        // 先按 12sp 量一遍：两行装得下就不降字号，否则走三行 11sp
        val needsThreeLines = remember(name, widthPx, baseStyle) {
            measurer.measure(
                text = AnnotatedString(name),
                style = baseStyle.copy(
                    fontSize = SHELF_GRID_NAME_TEXT_SIZE.sp,
                    lineHeight = SHELF_GRID_NAME_LINE_HEIGHT.sp,
                ),
                maxLines = Int.MAX_VALUE,
                softWrap = true,
                constraints = Constraints(maxWidth = widthPx),
            ).lineCount > SHELF_GRID_NAME_NORMAL_LINES
        }
        val nameStyle = remember(needsThreeLines, baseStyle) {
            if (needsThreeLines) {
                baseStyle.copy(
                    fontSize = SHELF_GRID_NAME_DENSE_TEXT_SIZE.sp,
                    lineHeight = SHELF_GRID_NAME_DENSE_LINE_HEIGHT.sp,
                )
            } else {
                baseStyle.copy(
                    fontSize = SHELF_GRID_NAME_TEXT_SIZE.sp,
                    lineHeight = SHELF_GRID_NAME_LINE_HEIGHT.sp,
                )
            }
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
