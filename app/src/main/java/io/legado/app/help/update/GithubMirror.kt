package io.legado.app.help.update

import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * GitHub 访问镜像。
 *
 * 两类请求的链路不一样，不能共用一套顺序：
 *
 * - **检测（api.github.com）**：实测 gh-proxy 对 api 路径直接回 403、shturl.cc 域名不通，
 *   而直连 api 反而 1 秒级返回。所以检测是「直连优先，镜像兜底」，否则先套前缀会白等十几秒。
 * - **下载（releases 资产）**：直连 objects.githubusercontent.com 在国内常断，镜像有效
 *   （gh-proxy 实测能下完整包），所以下载是「镜像优先，直连兜底」。
 *
 * 两边都是多候选逐个试，全失败才走下一档。
 */
object GithubMirror {

    /** 下载用前缀，按可用概率排序；空前缀代表直连兜底（放最后） */
    private val DOWNLOAD_PREFIXES = listOf(
        "https://gh-proxy.com/",
        "https://shturl.cc/",
        "https://shturl.cc/k/",
        "",
    )

    /** 检测用前缀：直连优先（实测最快且可用），gh-proxy 对 api 回 403 所以排最后 */
    private val API_PREFIXES = listOf(
        "",
        "https://shturl.cc/",
        "https://shturl.cc/k/",
        "https://gh-proxy.com/",
    )

    /**
     * 单次请求的调用上限。
     *
     * 串行试候选时单个请求不能等全局那套 60 秒：一个前缀挂住，后面几个根本没机会试。
     * 5 秒足够一个 JSON 接口返回。
     */
    private const val CALL_TIMEOUT_SECONDS = 5L

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun isGitHub(url: String): Boolean =
        url.contains("github.com") || url.contains("githubusercontent.com")

    /** 检测候选：直连优先，镜像兜底 */
    fun apiCandidates(raw: String): List<String> {
        if (!isGitHub(raw)) return listOf(raw)
        return API_PREFIXES.map { prefix -> if (prefix.isEmpty()) raw else prefix + raw }
    }

    /** 下载直链：镜像优先，直接返回首个镜像（302 跳回真实文件，下载端跟随重定向） */
    fun mirror(raw: String): String {
        if (!isGitHub(raw)) return raw
        if (DOWNLOAD_PREFIXES.any { it.isNotEmpty() && raw.startsWith(it) }) {
            return raw
        }
        return DOWNLOAD_PREFIXES.first() + raw
    }

    /**
     * 依次尝试检测候选，返回首个成功的 JSON 文本；全部失败抛最后一次的错误。
     */
    suspend fun fetchText(raw: String): String {
        var lastError: Exception? = null
        for (candidate in apiCandidates(raw)) {
            try {
                client.newCallResponse { url(candidate) }.use { response ->
                    if (!response.isSuccessful) {
                        lastError = NoStackTraceException("获取新版本出错(${response.code})")
                        return@use
                    }
                    val body = response.body.text()
                    // 镜像有时用 200 回一张错误页，只有拿到 JSON 才算这个候选可用
                    val trimmed = body.trimStart()
                    if (trimmed.isEmpty() || (trimmed[0] != '[' && trimmed[0] != '{')) {
                        lastError = NoStackTraceException("获取新版本出错")
                        return@use
                    }
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
}
