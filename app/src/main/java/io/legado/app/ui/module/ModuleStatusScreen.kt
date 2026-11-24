package io.legado.app.ui.module

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.AppPageTopBar
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.navigationBarBottomInset
import io.legado.app.ui.widget.components.settings.AppPanelListRow
import io.legado.app.ui.widget.components.settings.AppPanelListSurface

/**
 * 模块状态主界面。
 *
 * 视觉与「我的」页统一：透明页面底 + 整块圆角面板，行内为「状态点（图标位）+ 模块名 + 状态文案」，
 * 行高、内边距、字号与「我的」页一致；状态点作为行首元素保留原样。
 *
 * @param viewModel ViewModel实例
 * @param onBackClick 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleStatusScreen(
    viewModel: ModuleStatusViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    // 收集状态流，自动更新UI
    val modules by viewModel.modules.collectAsState()
    AppScaffold(
        topBar = {
            AppPageTopBar(
                title = stringResource(R.string.module_status),
                onBackClick = onBackClick,
                actions = {
                    // 刷新按钮
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { paddingValues ->
        AppPanelListSurface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    start = AppDimens.panelHorizontalPadding,
                    end = AppDimens.panelHorizontalPadding,
                    top = AppDimens.panelTopPadding,
                    bottom = navigationBarBottomInset
                )
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(modules, key = { _, module -> module.name }) { index, module ->
                    AppPanelListRow(
                        title = module.name,
                        showDivider = index < modules.lastIndex,
                        leading = { StatusDot(module.status) },
                        trailing = {
                            Text(
                                text = module.status.label(),
                                style = MaterialTheme.typography.bodySmall,
                                color = module.status.color()
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * 状态指示点（圆形色块）
 * @param status 运行状态
 */
@Composable
private fun StatusDot(status: ModuleRunStatus) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(status.color(), CircleShape)
    )
}

/**
 * 获取状态对应的中文标签
 */
private fun ModuleRunStatus.label(): String {
    return when (this) {
        ModuleRunStatus.RUNNING -> "运行中"
        ModuleRunStatus.IDLE -> "空闲"
        ModuleRunStatus.ERROR -> "异常"
        ModuleRunStatus.DISABLED -> "未启用"
    }
}

/**
 * 获取状态对应的颜色
 */
@Composable
private fun ModuleRunStatus.color(): Color {
    return when (this) {
        ModuleRunStatus.RUNNING -> Color(0xFF2E7D32) // 绿色
        ModuleRunStatus.IDLE -> pageSecondaryTextColor()
        ModuleRunStatus.ERROR -> MaterialTheme.colorScheme.error // 红色
        ModuleRunStatus.DISABLED -> MaterialTheme.colorScheme.outline // 轮廓色
    }
}
