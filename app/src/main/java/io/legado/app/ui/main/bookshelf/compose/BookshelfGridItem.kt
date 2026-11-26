package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.AppBookCover

/** 网格书名显示位置：0 封面下方 / 1 不显示 / 2 叠在封面上（与 AppConfig.showBookname 取值一致） */
private const val SHOW_BOOK_NAME_BELOW = 0
private const val SHOW_BOOK_NAME_ON_COVER = 2

/** 网格书名：字号 12sp、行高 1.2 倍、行数不设上限，书名块至少两行高（见 BookshelfGridName） */
private const val SHELF_GRID_NAME_TEXT_SIZE = 12f
private const val SHELF_GRID_NAME_LINE_HEIGHT_RATIO = 1.2f
/** 书名块的最小高度：两行。只当下限，书名更长时块自动长高 */
private const val SHELF_GRID_NAME_MIN_LINES = 2f
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
 * 网格书名：整名换行，行数不设上限，绝不加省略号。
 *
 * 走过的弯路：早先拆「主标题 + 副标题」，主标题只准一行；后来整名换行却给书名块写死高度
 * （42dp / 46dp），而 Compose 的 Text 在"高度不够"时也会加省略号，长名第三行被截
 * （"用九千万亿警用金在缅北开派出所"就是这么被截的）。
 * 现在：字号固定 12sp、行高 1.2 倍、量行与绘制同一套样式（显式 letterSpacing=0），
 * 行数不设上限（maxLines=Int.MAX_VALUE + Clip），书名块只给两行高的下限、内容多就自己长高；
 * 纵向留白交给「书架间距」设置：间距调大，长书名可以舒舒服服显示三四行，不靠缩字号硬塞。
 */
@Composable
private fun BookshelfGridName(name: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val baseStyle = LocalTextStyle.current
    val minHeight = remember(density) {
        with(density) {
            (SHELF_GRID_NAME_TEXT_SIZE * SHELF_GRID_NAME_LINE_HEIGHT_RATIO * SHELF_GRID_NAME_MIN_LINES)
                .sp.toDp()
        }
    }
    val nameStyle = remember(baseStyle) {
        baseStyle.copy(
            fontSize = SHELF_GRID_NAME_TEXT_SIZE.sp,
            lineHeight = (SHELF_GRID_NAME_TEXT_SIZE * SHELF_GRID_NAME_LINE_HEIGHT_RATIO).sp,
            // 主题可能带字距，这里显式清零，行宽与设计一致
            letterSpacing = 0.sp,
        )
    }
    Column(
        modifier = modifier
            // 只当下限：短名（1~2 行）块高统一，长名自己长高，永远不截字
            .heightIn(min = minHeight),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth(),
            style = nameStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
        )
    }
}
