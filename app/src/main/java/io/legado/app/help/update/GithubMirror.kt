package io.legado.app.help.update

import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * GitHub 访问镜像。
 *
 * 国内直连 api.github.com、releases 资产经常超时或连不上，表现为「检查更新卡住」「下载失败」。
 * 统一改成「镜像前缀 + 原地址」；镜像本身也会失效，所以按顺序逐个试，全部失败再退回直连兜底。
 *
 * 下载直链复用 [workingPrefix]：检测时哪个镜像通了，下载就跟哪个，避免检测走 A 镜像、
 * 下载却仍指向已失效的固定镜像。
 */
object GithubMirror {

    /** 候选镜像前缀，按可用概率排序；空前缀代表直连兜底（必须在最后） */
    private val PREFIXES = listOf(
        "https://gh-proxy.com/",
        "https://shturl.cc/",
        "https://shturl.cc/k/",
        "",
    )

    /**
     * 单次请求的调用上限。
     *
     * 检查更新是串行试前缀，单个请求给到全局那套 60 秒就没有意义了：一个前缀挂住，
     * 后面几个根本没机会试。6 秒足够镜像返回 JSON。
     */
    private const val CALL_TIMEOUT_SECONDS = 6L

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** 最近一次请求成功的镜像前缀，空串表示成功的是直连 */
    @Volatile
    private var workingPrefix: String? = null

    fun isGitHub(url: String): Boolean =
        url.contains("github.com") || url.contains("githubusercontent.com")

    /** 候选地址：镜像前缀逐个套一遍，最后一个是不加前缀的直连原址 */
    fun candidates(raw: String): List<String> {
        if (!isGitHub(raw)) return listOf(raw)
        return PREFIXES.map { prefix -> if (prefix.isEmpty()) raw else prefix + raw }
    }

    /**
     * 下载直链：优先挂最近一次成功的镜像，没有可用记录时退回首个镜像。
     * 镜像返回 302 跳回真实文件，下载端会跟随重定向。
     */
    fun mirror(raw: String): String {
        if (!isGitHub(raw)) return raw
        if (raw.startsWith("https://gh-proxy.com/") ||
            raw.startsWith("https://shturl.cc/")
        ) {
            return raw
        }
        val prefix = workingPrefix ?: PREFIXES.first()
        return if (prefix.isEmpty()) raw else prefix + raw
    }

    /**
     * 依次尝试候选镜像，返回首个成功的响应文本；全部失败抛出最后一次的错误。
     */
    suspend fun fetchText(raw: String): String {
        var lastError: Exception? = null
        for (candidate in candidates(raw)) {
            val prefix = prefixOf(candidate)
            try {
                client.newCallResponse { url(candidate) }.use { response ->
                    if (!response.isSuccessful) {
                        lastError = NoStackTraceException("获取新版本出错(${response.code})")
                        return@use
                    }
                    val body = response.body.text()
                    // 镜像有时会用 200 回一张错误页，只有拿到 JSON 才算这个前缀可用
                    val trimmed = body.trimStart()
                    if (trimmed.isEmpty() || trimmed[0] != '[' && trimmed[0] != '{') {
                        lastError = NoStackTraceException("获取新版本出错")
                        return@use
                    }
                    workingPrefix = prefix
                    return body
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: NoStackTraceException("获取新版本出错")
    }

    fun prefixOf(candidate: String): String =
        PREFIXES.firstOrNull { it.isNotEmpty() && candidate.startsWith(it) } ?: ""
}
