package io.legado.app.lib.prefs

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.utils.ColorUtils


class PreferenceCategory(context: Context, attrs: AttributeSet) :
    PreferenceCategory(context, attrs) {

    init {
        isPersistent = true
        layoutResource = R.layout.view_preference_category
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val view = holder.findViewById(R.id.preference_title)
        if (view is TextView) {  //  && !view.isInEditMode
            view.text = title
            if (view.isInEditMode) return
            view.setTextColor(context.accentColor)
            view.isVisible = !title.isNullOrEmpty()

            val da = holder.findViewById(R.id.preference_divider_above)
            if (da is View) {
                // 这里的 10dp 占位只当「组与组之间的间距」用：
                // 分组卡片是从该占位下沿开始画的，占位一旦被 allowDividerAbove=false 隐藏，
                // 卡片就会上移 10dp，把标题自己的 12dp 上内边距吃掉，看起来贴着卡片边。
                // 因此这里恒显示、且不画任何颜色（透明），保证标题距卡片顶固定 12dp。
                da.setBackgroundColor(Color.TRANSPARENT)
                da.isVisible = true
            }
            val db = holder.findViewById(R.id.preference_divider_below)
            if (db is View) {
                db.setBackgroundColor(
                    if (AppConfig.isNightTheme) {
                        ColorUtils.withAlpha(
                            ColorUtils.shiftColor(context.backgroundColor, 1.05f), 0.5f
                        )
                    } else {
                        ColorUtils.withAlpha(
                            ColorUtils.shiftColor(context.backgroundColor, 0.95f), 0.5f
                        )
                    }
                )
                db.isVisible = holder.isDividerAllowedBelow
            }
        }
    }

}
