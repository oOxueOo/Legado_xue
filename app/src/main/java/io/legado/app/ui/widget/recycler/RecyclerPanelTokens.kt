package io.legado.app.ui.widget.recycler

import android.content.Context
import androidx.core.graphics.ColorUtils
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.ColorUtils as AppColorUtils

/**
 * View 体系里复刻「我的」页（Compose `AppSettingsPanel`）那套面板配色的取色口径。
 *
 * Compose 侧的取色链路是 `LegadoTheme.buildLegadoColorScheme`：
 * `bgColor = ThemeStore.backgroundColor`、`onBackground = ThemeStore.textColorPrimary`，
 * 因此这里必须用同一对值——用 `android.R.attr.colorBackground` 取到的是应用主题里
 * 没被覆盖的底色（偏白），算出来的面板会比「我的」页浅一档、描边也淡，实机上就是
 * 「底色还是不一样」。
 *
 * - 面板填充 = surfaceVariant（背景混入前景，白天 5% / 夜间 14%）× 70% 不透明度
 * - 面板描边 / 行分隔线 = outline（背景混入前景，白天 12% / 夜间 24%）× 75% 不透明度
 */
object RecyclerPanelTokens {

    /** 面板填充色：surfaceVariant @ 70% */
    fun fillColor(context: Context): Int {
        val background = ThemeStore.backgroundColor(context)
        val onBackground = ThemeStore.textColorPrimary(context)
        val variant = ColorUtils.blendARGB(
            background,
            onBackground,
            if (isLight(background)) 0.05f else 0.14f
        )
        return ColorUtils.setAlphaComponent(variant, (0.7f * 255).toInt())
    }

    /** 面板描边色，同时用作行分隔线色：outline @ 75% */
    fun outlineColor(context: Context): Int {
        val background = ThemeStore.backgroundColor(context)
        val onBackground = ThemeStore.textColorPrimary(context)
        val outline = ColorUtils.blendARGB(
            background,
            onBackground,
            if (isLight(background)) 0.12f else 0.24f
        )
        return ColorUtils.setAlphaComponent(outline, (0.75f * 255).toInt())
    }

    private fun isLight(background: Int): Boolean {
        return !AppConfig.isNightTheme && AppColorUtils.isColorLight(background)
    }
}
