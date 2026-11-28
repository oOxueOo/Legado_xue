package io.legado.app.ui.widget.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 封面缓存代际信号。
 *
 * 书架的 Compose 封面只在 `LaunchedEffect(requestKey, bounds)` 里取一次图，请求键只含
 * 路径/来源/书名：一旦 Glide 的内存缓存被清空（内存回收、设置页清理缓存、备份恢复），
 * 手上那张 Drawable 可能已经失效，而键没变、effect 不会重跑——于是表现为"封面不显示，
 * 得手动刷新一下才回来"。
 *
 * 这里用一个全局自增代际把"缓存已变"这件事推给所有已组合的封面：代际变化 →
 * effect 重跑 → 各自判断手上的图还能不能用，能用就跳过（不重发请求），失效才补发。
 * 未组合的条目（滚出屏幕的）不会被通知，也不会发请求，避免整屏封面同帧 decode。
 */
internal object CoverCacheSignal {

    var generation by mutableIntStateOf(0)
        private set

    /** 缓存被清空/失效时调用 */
    fun bump() {
        generation++
    }
}
