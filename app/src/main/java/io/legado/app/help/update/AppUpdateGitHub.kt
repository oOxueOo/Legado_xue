package io.legado.app.help.update

import androidx.annotation.Keep
import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CoroutineScope

@Keep
@Suppress("unused")
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    /**
     * Release 列表接口。
     *
     * 直连 api.github.com 在国内多数网络下拿不到响应，请求本身也要套镜像前缀；
     * 前缀与下载直链共用 [GithubMirror]，多镜像轮换 + 直连兜底都在那里统一处理。
     */
    private const val RELEASES_API =
        "https://api.github.com/repos/oOxueOo/Legado_xue/releases?per_page=20"

    /**
     * 检查更新的整体时限。
     *
     * 串行试前缀最坏要走满四个候选（三个镜像 + 直连，单个 6 秒），
     * 给到 26 秒才不会在第三个前缀还没试上时就被整体超时掐断。
     */
    private const val CHECK_UPDATE_TIMEOUT_MS = 26_000L

    private val checkVariant: AppVariant
        get() = when (AppConfig.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            "beta_legacy_version" -> AppVariant.BETA_LEGACY
            "beta_coexist_version" -> AppVariant.BETA_COEXIST
            "beta_releaseS_version" -> AppVariant.BETA_COEXIST
            else -> AppConst.appInfo.appVariant
        }

    /** 拉取全部 Release（含预发布），由下游按变体筛选 */
    private suspend fun fetchReleases(): List<AppReleaseInfo> {
        val body = GithubMirror.fetchText(RELEASES_API)
        return GSON.fromJsonArray<GithubRelease>(body)
            .getOrElse {
                throw NoStackTraceException("获取新版本出错 " + it.localizedMessage)
            }
            .flatMap { it.gitReleaseToAppReleaseInfo() }
    }

    override fun check(
        scope: CoroutineScope,
    ): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            fetchReleases()
                .sortedByDescending { it.createdAt }
                .filter { it.appVariant == checkVariant }
                .firstOrNull { it.isNewerThan(AppConst.appInfo.versionName) }
                ?.let {
                    return@async AppUpdate.UpdateInfo(
                        it.versionName,
                        it.note,
                        it.downloadUrl,
                        it.name
                    )
                }
                ?: throw NoStackTraceException("已是最新版本")
        }.timeout(CHECK_UPDATE_TIMEOUT_MS)
    }

    override fun getAllVariants(scope: CoroutineScope): Coroutine<List<AppUpdate.UpdateInfo>> {
        return Coroutine.async(scope) {
            fetchReleases()
                .sortedByDescending { it.createdAt }
                .groupBy { it.appVariant }
                .flatMap { (_, infos) ->
                    infos.firstOrNull()?.let {
                        listOf(
                            AppUpdate.UpdateInfo(
                                it.versionName,
                                it.note,
                                it.downloadUrl,
                                it.name
                            )
                        )
                    }.orEmpty()
                }
        }.timeout(CHECK_UPDATE_TIMEOUT_MS)
    }
}
