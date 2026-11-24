package io.legado.app.lib.prefs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.lib.theme.UiCorner
import io.legado.app.ui.widget.recycler.RecyclerPanelTokens
import java.util.WeakHashMap

/**
 * 设置页的分组卡片底衬，观感对齐「我的」页 `MySettingsSectionCard`。
 *
 * 「我的」页每个分组是一块独立卡片：左上角一个分组标题，下面若干行，卡片之间留 10dp。
 * 原先这里是"整页一块卡铺到底"，与「我的」差在形态上——分组之间没有断开，视觉上像一整片。
 *
 * 现在按 [PreferenceCategory] 切分：每个分类 + 它后面的偏好项算一组，各画一块圆角卡片
 * （填充 surfaceVariant@70% + 1dp outlineVariant 描边），组内相邻两行之间补 1dp 分隔线。
 * 分组之间的 10dp 间距由分类项顶部的占位 View 提供（`view_preference_category.xml` 里的
 * `preference_divider_above`），因此卡片从该占位下方开始画。
 */
class SettingsCardDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val inset = 12f * density
    private val radius = UiCorner.panelRadius(context)
    private val strokeWidth = 1f * density

    /** 分组卡片之间的间距，与「我的」页 panelSpacing=10dp 同值 */
    private val groupGap = 10f * density

    /** 行分隔线左右内缩，与「我的」页行内边距 16dp 同值 */
    private val dividerInset = 16f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        fillPaint.color = panelColor(parent.context)
        strokePaint.color = strokeColor(parent.context)
        strokePaint.strokeWidth = strokeWidth

        val groups = splitGroups(adapter)
        if (groups.isEmpty()) return

        val visibleRange = visiblePositions(parent) ?: return
        val bottomLimit = parent.height - parent.paddingBottom

        for ((start, end, isCategoryHead) in groups) {
            if (end < visibleRange.first || start > visibleRange.last) continue

            val startTop = if (start >= visibleRange.first) {
                val view = parent.findViewHolderForAdapterPosition(start)?.itemView
                if (view == null) continue
                // 分类项顶部的 10dp 占位就是组间距，卡片从占位下方开始
                view.top.toFloat() + if (isCategoryHead) groupGap else 0f
            } else {
                0f
            }
            val endBottom = if (end <= visibleRange.last) {
                val view = parent.findViewHolderForAdapterPosition(end)?.itemView ?: continue
                view.bottom.toFloat()
            } else {
                bottomLimit.toFloat()
            }
            if (endBottom <= startTop) continue

            val left = parent.paddingLeft + inset
            val right = parent.width - parent.paddingRight - inset
            rect.set(left, startTop, right, endBottom)
            c.drawRoundRect(rect, radius, radius, fillPaint)
            rect.inset(strokeWidth / 2f, strokeWidth / 2f)
            c.drawRoundRect(rect, radius, radius, strokePaint)
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val itemCount = adapter.itemCount
        if (itemCount <= 1) return

        strokePaint.strokeWidth = strokeWidth
        strokePaint.color = strokeColor(parent.context)
        val left = parent.paddingLeft + inset + dividerInset
        val right = parent.width - parent.paddingRight - inset - dividerInset
        if (right <= left) return

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            val current = adapter.getItem(position)
            if (current is PreferenceCategory) continue
            if (current !is Preference) continue
            // 组内最后一行后面没有分隔线，与「我的」页 showDivider=false 一致
            if (position >= itemCount - 1) continue
            val next = adapter.getItem(position + 1)
            if (next is PreferenceCategory || next !is Preference) continue
            val y = child.bottom.toFloat() - strokeWidth / 2f
            c.drawLine(left, y, right, y, strokePaint)
        }
    }

    /**
     * 把设置项按分类切成组：第一个分类之前的内容算独立一组；
     * 之后每个 [PreferenceCategory] 与它后面的偏好项算一组。
     */
    private fun splitGroups(adapter: PreferenceGroupAdapter): List<Triple<Int, Int, Boolean>> {
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return emptyList()
        val groups = ArrayList<Triple<Int, Int, Boolean>>()
        var start = 0
        var isCategoryHead = false
        for (i in 0 until itemCount) {
            val item = adapter.getItem(i)
            if (item is PreferenceCategory) {
                if (i > start) groups.add(Triple(start, i - 1, isCategoryHead))
                start = i
                isCategoryHead = true
            }
        }
        if (start < itemCount) groups.add(Triple(start, itemCount - 1, isCategoryHead))
        return groups
    }

    private fun visiblePositions(parent: RecyclerView): IntRange? {
        var first = Int.MAX_VALUE
        var last = -1
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            if (position < first) first = position
            if (position > last) last = position
        }
        return if (last < 0) null else first..last
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
}

private val attachedCards = WeakHashMap<RecyclerView, SettingsCardDecoration>()

/**
 * 给设置列表挂上卡片底衬，同一列表只挂一次（其余空白处留给其它 ItemDecoration，不能按数量判断）。
 */
fun RecyclerView.applySettingsCard() {
    if (attachedCards.containsKey(this)) return
    val decoration = SettingsCardDecoration(context)
    addItemDecoration(decoration)
    attachedCards[this] = decoration
}
