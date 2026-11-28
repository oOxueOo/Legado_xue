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
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.AppBookCover

/** 横卡比例：固定 16:9，不再跟随封面 3:4 */
const val WIDE_MEDIA_COVER_ASPECT = 16f / 9f

/**
 * 音频 / 视频的横版条目。
 *
 * 强制一排一个：封面 16:9 整图不裁切、空处同帧模糊或主色补满，书名在卡下一行。
 * 宫格列数与列表/紧凑列表切换对这两组无效——横卡只跟屏宽走，所以不带列数参数。
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
            .padding(AppDimens.shelfGridItemPadding),
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bookItem.display.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AppDimens.shelfTitleStartPadding),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = SHELF_TITLE_TEXT_SIZE.sp,
                lineHeight = SHELF_TITLE_LINE_HEIGHT.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BookshelfItemStatus(
                modifier = Modifier.padding(start = AppDimens.shelfBadgeRowSpacing),
                bookItem = bookItem,
                showUnread = displayConfig.showUnread,
                loadingSize = AppDimens.shelfListLoadingSize,
            )
        }
        bookItem.display.author.takeIf { it.isNotBlank() }?.let { author ->
            Text(
                text = author,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppDimens.shelfTitleStartPadding, top = AppDimens.shelfMetaSpacing),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = SHELF_META_TEXT_SIZE.sp,
                lineHeight = SHELF_META_LINE_HEIGHT.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 其余信息（章节、简介、标签）在横卡上会撑高卡片，按口径只留书名与作者
    }
}
