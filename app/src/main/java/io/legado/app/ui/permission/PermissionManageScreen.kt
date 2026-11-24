package io.legado.app.ui.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.legado.app.R
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.widget.components.AppPageTopBar
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.navigationBarBottomInset
import io.legado.app.ui.widget.components.settings.AppPanelListRow
import io.legado.app.ui.widget.components.settings.AppSettingsPanel
import io.legado.app.ui.widget.components.settings.AppSettingsSectionTitle

/**
 * 权限管理页。
 *
 * 视觉与「我的」页统一：每个分组一块半透明圆角面板，分组标题在面板首行，
 * 面板内的行沿用「我的」页行样式（60dp 行高、16/10dp 内边距、bodyLarge / bodySmall、1dp 分隔线），
 * 行首图标按「我的」页口径保留为 22dp 强调色图标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManageScreen(
    onBackClick: () -> Unit
) {
    AppScaffold(
        topBar = {
            AppPageTopBar(
                title = stringResource(R.string.permission_manage),
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                top = AppDimens.panelTopPadding,
                bottom = AppDimens.panelSpacing + navigationBarBottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.panelSpacing)
        ) {
            items(permissionSections, key = { it.titleRes }) { section ->
                PermissionSectionCard(section = section)
            }
        }
    }
}

@Composable
private fun PermissionSectionCard(
    modifier: Modifier = Modifier,
    section: PermissionSection
) {
    AppSettingsPanel(
        modifier = modifier.padding(horizontal = AppDimens.panelHorizontalPadding)
    ) {
        AppSettingsSectionTitle(title = stringResource(section.titleRes))
        section.items.forEachIndexed { index, item ->
            AppPanelListRow(
                title = stringResource(item.titleRes),
                summary = stringResource(item.descRes),
                showDivider = index < section.items.lastIndex,
                leading = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = pageAccentColor(),
                        modifier = Modifier.size(AppDimens.panelRowIconSize)
                    )
                }
            )
        }
    }
}

@Preview
@Composable
private fun PermissionSectionCardPreview() {
    PermissionSectionCard(
        section = permissionSections.first()
    )
}
