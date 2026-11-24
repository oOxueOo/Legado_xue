package io.legado.app.ui.widget.recycler

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.lib.theme.UiCorner
import java.util.WeakHashMap

/**
 * 列表页的整块面板底衬，观感对齐「我的」页 `AppSettingsPanel`。
 *
 * 为什么用 ItemDecoration 而不是给条目加背景：列表每行是独立条目，逐行加圆角会得到一排
 * 互不相连的小方块。这里在列表底下画一整块圆角卡（填充 + 1dp 描边），
 * 行分隔线在 `onDrawOver` 里逐行补上，首行/末行的圆角由卡片本身决定。
 *
 * 与设置页 `SettingsCardDecoration` 的区别：
 * - 只给普通列表用，不处理 `PreferenceCategory`，首条内容直接从列表第一条起画；
 * - 额外画行分隔线（左右各内缩 16dp，末行不画）。
 *
 * @param showDividers 是否绘制行间分隔线；展开式条目（如分组+明细）可关掉自绘
 */
class PanelCardDecoration(
    context: Context,
    private val showDividers: Boolean = true
) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density

    /** 面板左右内缩，与「我的」页 panelHorizontalPadding=12dp 同值 */
    private val inset = 12f * density

    /** 行分隔线左右内缩，与「我的」页行内边距 16dp 同值 */
    private val dividerInset = 16f * density
    private val radius = UiCorner.panelRadius(context)
    private val strokeWidth = 1f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidth
    }
    private val rect = RectF()

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        fillPaint.color = RecyclerPanelTokens.fillColor(parent.context)
        linePaint.color = RecyclerPanelTokens.outlineColor(parent.context)

        val bottom = parent.height - parent.paddingBottom
        val top = if (parent.canScrollVertically(-1)) {
            0f
        } else {
            val firstTop = firstChildTop(parent)
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
        c.drawRoundRect(rect, radius, radius, linePaint)
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (!showDividers) return
        val adapter = parent.adapter ?: return
        val itemCount = adapter.itemCount
        if (itemCount <= 1) return

        linePaint.color = RecyclerPanelTokens.outlineColor(parent.context)
        val left = parent.paddingLeft + inset + dividerInset
        val right = parent.width - parent.paddingRight - inset - dividerInset
        if (right <= left) return

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            // 末行不画分隔线，与「我的」页最后一行 showDivider=false 一致
            if (position >= itemCount - 1) continue
            val y = child.bottom.toFloat() - strokeWidth / 2f
            c.drawLine(left, y, right, y, linePaint)
        }
    }

    /** 第一条可见条目的顶部；列表没有子项时返回 -1，避免画出一块空卡 */
    private fun firstChildTop(parent: RecyclerView): Float {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i) ?: continue
            if (parent.getChildAdapterPosition(child) == RecyclerView.NO_POSITION) continue
            return child.top.toFloat()
        }
        return -1f
    }
}

private val attachedPanelCards = WeakHashMap<RecyclerView, PanelCardDecoration>()

/**
 * 给列表挂上面板底衬，同一列表只挂一次（普通列表可能已经自带分隔线装饰，不能按数量判断）。
 */
fun RecyclerView.applyPanelCard(showDividers: Boolean = true) {
    if (attachedPanelCards.containsKey(this)) return
    val decoration = PanelCardDecoration(context, showDividers)
    addItemDecoration(decoration)
    attachedPanelCards[this] = decoration
}
