package io.legado.app.lib.prefs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import io.legado.app.lib.theme.UiCorner

/**
 * 设置页卡片底衬。
 *
 * [androidx.preference] 的列表每行都是一个独立条目，没有「卡片」这一层容器；把圆角背景挂到
 * 条目上会得到一排互不相连的小方块。这里改成在列表底下画一整块卡片：从第一条内容起，
 * 一路铺到列表最底部，分类标题自然落在卡片内部，滚动时卡片跟着内容走。
 *
 * 取色与「我的」页同源（surfaceVariant + 60% 透明度），圆角取同一条 ui_panel_radius。
 */
class SettingsCardDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val inset = 12f * density
    private val radius = UiCorner.panelRadius(context)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val surfaceVariant =
            MaterialColors.getColor(parent, MaterialR.attr.colorSurfaceVariant, Color.GRAY)
        fillPaint.color = ColorUtils.setAlphaComponent(surfaceVariant, (0.6f * 255).toInt())

        val firstTop = firstContentTop(parent, adapter)
        val bottom = parent.height - parent.paddingBottom
        val top = if (parent.canScrollVertically(-1)) {
            0f
        } else {
            if (firstTop < 0f) return
            firstTop
        }
        if (bottom <= top) return

        rect.set(
            parent.paddingLeft + inset,
            top,
            parent.width - parent.paddingRight - inset,
            bottom.toFloat()
        )
        c.drawRoundRect(rect, radius, radius, fillPaint)
    }

    private fun firstContentTop(parent: RecyclerView, adapter: PreferenceGroupAdapter): Float {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            val preference = adapter.getItem(position)
            if (preference is PreferenceCategory) continue
            return child.top.toFloat()
        }
        return -1f
    }
}
