package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.AppBookCover

/** 横卡比例：固定 16:9，不再跟随封面 3:4 */
const val WIDE_MEDIA_COVER_ASPECT = 16f / 9f

/** 横卡书名最多折 3 行；超出直接截断，不加省略号 */
private const val WIDE_MEDIA_NAME_MAX_LINES = 3

/** 横卡行间额外留白（左右方向不加，宽度要满格） */
private val WIDE_MEDIA_ROW_EXTRA_GAP = 6.dp

/**
 * 音频 / 视频的横版条目。
 *
 * 两列一排：封面 16:9 整图不裁切、空处同帧模糊或主色补满；书名在卡下方，可折 1–3 行、不加省略号。
 * 宫格列数与列表/紧凑列表切换对这两组无效——横卡固定两列，所以不带列数参数。
 * 横版下不叠立体封面，只保留平面书框（cornerRadius 沿用书架的圆角）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfWideMediaItem(
    modifier: Modifier = Modifier,
    bookItem: BookshelfBookItem,
    displayConfig: BookshelfDisplayConfig,
    onClick: (BookshelfBookItem) -> Unit,
    onLongClick: (BookshelfBookItem) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onClick(bookItem) }, onLongClick = { onLongClick(bookItem) })
            // 横卡比网格条目高，行间再补一点留白，书名多行时不至于黏在一起
            .padding(AppDimens.shelfGridItemPadding)
            .padding(vertical = WIDE_MEDIA_ROW_EXTRA_GAP),
    ) {
        AppBookCover(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(WIDE_MEDIA_COVER_ASPECT),
            name = bookItem.display.name,
            author = bookItem.display.author,
            coverPath = bookItem.display.getDisplayCover(),
            galleryIdentity = bookItem.display.bookUrl,
            sourceOrigin = bookItem.display.origin,
            contentDescription = bookItem.display.name,
            cornerRadius = AppDimens.shelfCoverCornerRadius,
            // 横版卡只留平面书框：立体封面会按 4:3 的比例参数去画折面/书脊，放到 16:9 上必然错位
            coverStyle = AppDimens.SHELF_COVER_STYLE_NONE,
            wideFill = true,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimens.shelfGridNameSpacing),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = bookItem.display.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AppDimens.shelfTitleStartPadding),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = SHELF_TITLE_TEXT_SIZE.sp,
                lineHeight = SHELF_TITLE_LINE_HEIGHT.sp,
                // 书名不省略：按宽度折行，最多 3 行；再长就截断，不加省略号
                maxLines = WIDE_MEDIA_NAME_MAX_LINES,
                overflow = TextOverflow.Clip,
            )
            BookshelfItemStatus(
                modifier = Modifier.padding(start = AppDimens.shelfBadgeRowSpacing),
                bookItem = bookItem,
                showUnread = displayConfig.showUnread,
                loadingSize = AppDimens.shelfListLoadingSize,
            )
        }
        // 横卡不留作者行，也不放章节/简介/标签：书名已占 1–3 行，再加就会把卡撑高
    }
}
