package io.legado.app.ui.widget.components.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.ui.theme.AppDimens
import io.legado.app.ui.theme.composePanelShape
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pagePanelContainerColor
import io.legado.app.ui.theme.pageSecondaryTextColor

/**
 * 整页列表的面板底板，与「我的」页 [AppSettingsPanel] 取同一套观感：
 * 半透明 `surfaceVariant` 填充 + `outlineVariant` 1dp 描边 + 主题面板圆角。
 *
 * 与面板组件不同的是它**铺满可用高度**：列表页条目数量不定，让面板随内容高度收缩会导致
 * 只有两三行的页面看起来像"半截卡片"，因此这里固定为整块底板，内容在其中滚动，
 * 多余区域留空即可（描边与圆角保证边界一眼可见）。
 *
 * @param modifier 外部修饰符，调用方负责四周留白（左右用 [AppDimens.panelHorizontalPadding]）
 * @param content 面板内容，通常是 LazyColumn / Column + 若干 [AppPanelListRow]
 */
@Composable
fun AppPanelListSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = composePanelShape()
    Box(
        modifier = modifier
            .clip(shape)
            .background(pagePanelContainerColor())
            .border(AppDimens.dividerThickness, MaterialTheme.colorScheme.outlineVariant, shape),
        content = content
    )
}

/**
 * 列表页中的一行：行首图标 + 标题 + 可选副标题 + 可选尾部控件。
 *
 * 尺寸、字号、颜色与「我的」页 [AppSettingsActionRow] 完全一致（60dp 最小行高、16/10dp 内边距、
 * bodyLarge 标题、bodySmall 副标题、1dp 行分隔线、ripple 按压反馈），
 * 区别只在于行首元素交给调用方：列表页的图标形态（矢量图标、封面、状态徽标）无法归一成一种，
 * 因此这里提供 [leadingIconRes] 与 [leading] 两种插槽，图标本身保持各页面原样。
 *
 * @param title 主文案
 * @param summary 副标题，空值时行收缩为单行高度
 * @param showDivider 是否绘制行底分隔线（面板最后一行传 false）
 * @param danger 危险操作行，标题转错误色
 * @param leadingIconRes 行首图标资源（22dp、强调色着色，与「我的」页行图标一致）
 * @param leading 行首自定义插槽（封面、徽标等），与 [leadingIconRes] 二选一
 * @param trailing 尾部插槽（开关、状态文字、操作按钮等）
 * @param onClick 整行点击；为 null 时该行不可点击（无 ripple）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppPanelListRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    showDivider: Boolean = true,
    danger: Boolean = false,
    @DrawableRes leadingIconRes: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val shape = composePanelShape()
    val interactionSource = remember { MutableInteractionSource() }
    val titleColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    val rowModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = AppDimens.panelRowMinHeight)
        .semantics(mergeDescendants = true) {}
        .appSettingsRowDecoration(
            shape = shape,
            dividerColor = MaterialTheme.colorScheme.outlineVariant,
            showDivider = showDivider
        )
        .then(
            if (onClick != null || onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick
                )
            } else {
                Modifier
            }
        )
        .padding(
            horizontal = AppDimens.panelRowHorizontalPadding,
            vertical = AppDimens.panelRowVerticalPadding
        )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier
    ) {
        if (leadingIconRes != null) {
            Image(
                painter = painterResource(leadingIconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (danger) MaterialTheme.colorScheme.error else pageAccentColor()
                ),
                modifier = Modifier.size(AppDimens.panelRowIconSize)
            )
            Spacer(modifier = Modifier.width(AppDimens.panelRowIconSpacing))
        } else if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(AppDimens.panelRowIconSpacing))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(AppDimens.panelRowTitleSpacing))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = pageSecondaryTextColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(AppDimens.panelRowTrailingSpacing))
            trailing()
        }
    }
}
