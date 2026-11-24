package io.legado.app.ui.file.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.widget.components.settings.AppPanelListRow
import io.legado.app.ui.widget.components.settings.AppPanelListSurface
import java.io.File

/**
 * 文件列表。
 *
 * 视觉与「我的」页统一：整块圆角面板铺满，文件行沿用面板行样式
 * （60dp 行高、16/10dp 内边距、bodyLarge 标题、1dp 分隔线），行首图标保留。
 */
@Composable
fun FileList(
    files: List<File>,
    currentDir: File?,
    onFileClick: (File) -> Unit,
    onFileLongClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    AppPanelListSurface(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(files, key = { _, file -> file.absolutePath }) { index, file ->
                val isParentDir = file == currentDir
                // 根据类型选择图标与语义色
                val (icon, tint) = when {
                    isParentDir -> Icons.Default.SubdirectoryArrowLeft to MaterialTheme.colorScheme.onSurfaceVariant
                    file.isDirectory -> Icons.Default.Folder to MaterialTheme.colorScheme.primary
                    else -> Icons.Default.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
                }
                AppPanelListRow(
                    title = if (isParentDir) ".." else file.name,
                    showDivider = index < files.lastIndex,
                    leading = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimens.panelRowIconSize),
                            tint = tint
                        )
                    },
                    onClick = { onFileClick(file) },
                    onLongClick = { onFileLongClick(file) }
                )
            }
        }
    }
}
