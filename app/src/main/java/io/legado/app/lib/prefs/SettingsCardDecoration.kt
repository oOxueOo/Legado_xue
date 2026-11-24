package io.legado.app.lib.prefs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.lib.theme.UiCorner
import io.legado.app.ui.widget.recycler.RecyclerPanelTokens
import java.util.WeakHashMap

/**
 * 设置页卡片底衬。
 *
 * [androidx.preference] 的列表每行都是一个独立条目，没有「卡片」这一层容器；把圆角背景挂到
 * 条目上会得到一排互不相连的小方块。这里改成在列表底下画一整块卡片：从第一条内容起，
 * 一路铺到列表最底部，分类标题自然落在卡片内部，滚动时卡片跟着内容走。
 *
 * 观感对齐「我的」页 `AppSettingsPanel`：同样是 surfaceVariant + 70% 不透明度，
 * **外加一圈 outlineVariant 描边**——只画填充时，5% 混色在壁纸上几乎看不出边界，
 * 描边才是那块卡能一眼看见的原因。
 */
class SettingsCardDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val inset = 12f * density
    private val radius = UiCorner.panelRadius(context)
    private val strokeWidth = 1f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        strokePaint.strokeWidth = strokeWidth
        fillPaint.color = panelColor(parent.context)
        strokePaint.color = strokeColor(parent.context)

        val bottom = parent.height - parent.paddingBottom
        val top = if (parent.canScrollVertically(-1)) {
            0f
        } else {
            val firstTop = firstContentTop(parent)
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
        rect.inset(strokeWidth / 2f, strokeWidth / 2f)
        c.drawRoundRect(rect, radius, radius, strokePaint)
    }

    /**
     * 与「我的」页 `pagePanelContainerColor()` 同算法，
     * 具体取色集中在 [RecyclerPanelTokens]，与普通列表页共用一份实现。
     */
    private fun panelColor(context: Context): Int = RecyclerPanelTokens.fillColor(context)

    /**
     * 与「我的」页面板描边同算法（outline × 75%）。
     */
    private fun strokeColor(context: Context): Int = RecyclerPanelTokens.outlineColor(context)

    private fun firstContentTop(parent: RecyclerView): Float {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return parent.paddingTop.toFloat()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            if (adapter.getItem(position) is PreferenceCategory) continue
            return child.top.toFloat()
        }
        return -1f
    }
}

/**
 * 给设置列表挂上卡片底衬，同一列表只挂一次（其余空白处留给其它 ItemDecoration，不能按数量判断）。
 */
fun RecyclerView.applySettingsCard() {
    if (attachedCards.containsKey(this)) return
    val decoration = SettingsCardDecoration(context)
    addItemDecoration(decoration)
    attachedCards[this] = decoration
}

private val attachedCards = WeakHashMap<RecyclerView, SettingsCardDecoration>()
