package io.legado.app.ui.debug

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.widget.components.AppPageTopBar
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.navigationBarBottomInset
import io.legado.app.ui.widget.components.settings.AppPanelListRow
import io.legado.app.ui.widget.components.settings.AppPanelListSurface

data class DebugTool(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val activityClass: Class<*>
)

/**
 * 调试工具列表。
 *
 * 视觉与「我的」页统一：透明页面底 + 整块圆角面板 + 面板行（60dp 行高、16/10dp 内边距、
 * bodyLarge 标题 / bodySmall 副标题、1dp 行分隔线），行首图标按「我的」页口径保留为 22dp 强调色图标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugToolsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val tools = listOf(
        DebugTool(
            titleRes = R.string.debug_encode_tools,
            descRes = R.string.debug_encode_tools_desc,
            icon = Icons.Default.Code,
            activityClass = EncodeToolsActivity::class.java
        ),
        DebugTool(
            titleRes = R.string.debug_http_request,
            descRes = R.string.debug_http_request_desc,
            icon = Icons.Default.Http,
            activityClass = HttpDebugActivity::class.java
        ),
        DebugTool(
            titleRes = R.string.debug_curl_test,
            descRes = R.string.debug_curl_test_desc,
            icon = Icons.Default.Terminal,
            activityClass = CurlTestActivity::class.java
        ),
        DebugTool(
            titleRes = R.string.debug_ping_test,
            descRes = R.string.debug_ping_test_desc,
            icon = Icons.Default.Wifi,
            activityClass = PingTestActivity::class.java
        ),
        DebugTool(
            titleRes = R.string.debug_regex_test,
            descRes = R.string.debug_regex_test_desc,
            icon = Icons.Default.TextFields,
            activityClass = RegexTestActivity::class.java
        ),
        DebugTool(
            titleRes = R.string.debug_timestamp,
            descRes = R.string.debug_timestamp_desc,
            icon = Icons.Default.Schedule,
            activityClass = TimestampConvertActivity::class.java
        )
    )

    AppScaffold(
        topBar = {
            AppPageTopBar(
                title = stringResource(R.string.debug_tools),
                subtitle = stringResource(R.string.debug_tools_desc),
                onBackClick = onBackClick
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
                itemsIndexed(tools) { index, tool ->
                    AppPanelListRow(
                        title = stringResource(tool.titleRes),
                        summary = stringResource(tool.descRes),
                        showDivider = index < tools.lastIndex,
                        leading = {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = null,
                                tint = pageAccentColor(),
                                modifier = Modifier.size(AppDimens.panelRowIconSize)
                            )
                        },
                        onClick = {
                            context.startActivity(Intent(context, tool.activityClass))
                        }
                    )
                }
            }
        }
    }
}
