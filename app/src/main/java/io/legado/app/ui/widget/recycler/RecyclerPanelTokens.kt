package io.legado.app.ui.widget.recycler

import android.content.Context
import androidx.core.graphics.ColorUtils
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeUtils

/**
 * View 体系里复刻「我的」页（Compose `AppSettingsPanel`）那套面板配色的取色口径。
 *
 * Compose 侧颜色来自 `LegadoTheme.buildLegadoColorScheme`，这里按同一算法从主题里现算：
 * - 面板填充 = surfaceVariant（背景混入前景，白天 5% / 夜间 14%）× 70% 不透明度
 * - 面板描边 / 行分隔线 = outline（背景混入前景，白天 12% / 夜间 24%）× 75% 不透明度
 *
 * 集中在一处，避免每个列表页各抄一份取色公式导致日夜两套色值再次走偏。
 */
object RecyclerPanelTokens {

    /** 面板填充色 */
    fun fillColor(context: Context): Int {
        val variant = surfaceVariant(context)
        return ColorUtils.setAlphaComponent(variant, (0.7f * 255).toInt())
    }

    /** 面板描边色，同时用作行分隔线色 */
    fun outlineColor(context: Context): Int {
        val background = ThemeUtils.resolveColor(context, android.R.attr.colorBackground)
        val onBackground = ThemeUtils.resolveColor(context, android.R.attr.textColorPrimary)
        val ratio = if (isLight(context, background)) 0.12f else 0.24f
        val outline = ColorUtils.blendARGB(background, onBackground, ratio)
        return ColorUtils.setAlphaComponent(outline, (0.75f * 255).toInt())
    }

    private fun surfaceVariant(context: Context): Int {
        val background = ThemeUtils.resolveColor(context, android.R.attr.colorBackground)
        val onBackground = ThemeUtils.resolveColor(context, android.R.attr.textColorPrimary)
        return ColorUtils.blendARGB(
            background,
            onBackground,
            if (isLight(context, background)) 0.05f else 0.14f
        )
    }

    private fun isLight(context: Context, background: Int): Boolean {
        return !AppConfig.isNightTheme && ColorUtils.isColorLight(background)
    }
}
