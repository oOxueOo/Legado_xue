package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils


class NameListPreference(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {

    private val isBottomBackground: Boolean

    init {
        layoutResource = R.layout.view_preference
        widgetLayoutResource = R.layout.item_fillet_text
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.Preference)
        isBottomBackground = typedArray.getBoolean(R.styleable.Preference_isBottomBackground, false)
        typedArray.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val v = Preference.bindView<TextView>(
            context, holder, icon, title, summary, widgetLayoutResource,
            R.id.text_view, isBottomBackground = isBottomBackground
        )
        if (v is TextView) {
            // 选项文案常带「主名（补充说明）」的长尾巴，整段塞进右侧胶囊会把左侧标题挤成「书架图…」。
            // 这里只显示主名，完整文案留给弹窗列表和 contentDescription。
            v.text = shortEntry(entry)
            v.contentDescription = entry
            if (isBottomBackground) {
                val bgColor = context.bottomBackground
                val pTextColor = context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
                v.setTextColor(pTextColor)
            }
        }
        super.onBindViewHolder(holder)
    }

    /** 取「主名（说明）」里的主名；主名仍过长时截断 */
    private fun shortEntry(entry: CharSequence?): CharSequence {
        val s = entry?.toString()?.trim().orEmpty()
        if (s.isEmpty()) return s
        val head = s.substringBefore('（').substringBefore('(').trim()
        val base = head.ifEmpty { s }
        return if (base.length > 8) base.take(7) + "…" else base
    }

}