package io.legado.app.ui.book.storage.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.widget.components.settings.AppPanelListRow
import io.legado.app.utils.ConvertUtils

/**
 * 统计页顶部的汇总行。
 *
 * 原来是自绘渐变横幅，与「我的」页的面板观感冲突，这里改为面板首行：
 * 左侧「已缓存内容总大小」标题 + 条目数副标题，右侧总大小数值（强调色）。
 *
 * @param showDivider 面板首行下面还有行时为 true
 */
@Composable
fun CacheSummaryCard(
    totalSize: Long,
    itemCount: Int,
    showDivider: Boolean = true
) {
    AppPanelListRow(
        title = stringResource(R.string.storage_total_size),
        summary = stringResource(R.string.storage_cache_item_count, itemCount),
        showDivider = showDivider,
        leading = {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                tint = pageAccentColor(),
                modifier = Modifier.size(AppDimens.panelRowIconSize)
            )
        },
        trailing = {
            Text(
                text = ConvertUtils.formatFileSize(totalSize),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    )
}
