package io.legado.app.help.update

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.exception.NoStackTraceException
import java.time.Instant

data class AppReleaseInfo(
    val appVariant: AppVariant,
    val createdAt: Long,
    val note: String,
    val name: String,
    val downloadUrl: String,
    val assetUrl: String
) {
    val versionName: String by lazy {
        extractVersionName(name)
    }

    fun isNewerThan(currentVersionName: String): Boolean {
        return compareVersionName(versionName, currentVersionName) > 0
    }
}

enum class AppVariant {
    OFFICIAL,
    BETA_LEGACY,    // 兼容版 - io.legado.app - 可覆盖原版
    BETA_COEXIST,   // 共存版 - io.legado.app.yuedu.a - 测试版的共存包
    BETA_RELEASE,   // 测试版 - io.legado.app.yuedu - 原包名测试版
    UNKNOWN;

    fun isBeta(): Boolean {
        return this == BETA_RELEASE || this == BETA_LEGACY || this == BETA_COEXIST
    }

}

fun appVariantFromAssetName(name: String, preRelease: Boolean = false): AppVariant {
    return when {
        name.contains("releaseS", ignoreCase = true) || name.contains("正式版") -> AppVariant.BETA_COEXIST
        name.contains("legacy", ignoreCase = true) || name.contains("兼容版") -> AppVariant.BETA_LEGACY
        name.contains("release", ignoreCase = true) || name.contains("测试版") -> AppVariant.BETA_RELEASE
        preRelease -> AppVariant.BETA_RELEASE
        else -> AppVariant.OFFICIAL
    }
}

/** 版本号：主版本 + 可选的自建序号，例如 `3.26X7` */
private val versionNameRegex = Regex("""(\d+(?:\.\d+)*)(?:[Xx](\d+))?""")

private fun extractVersionName(name: String): String {
    val match = versionNameRegex.find(name) ?: return ""
    val base = match.groupValues[1]
    val serial = match.groupValues[2]
    return if (serial.isBlank()) base else "${base}X$serial"
}

/** 把 `3.26X7` 拆成 "3.26" 与 7；没有 X 序号时视为 0 */
private fun splitVersionSerial(version: String): Pair<String, Long> {
    val serialIndex = version.indexOf('X')
    if (serialIndex < 0) return version to 0L
    val base = version.substring(0, serialIndex)
    val serial = version.substring(serialIndex + 1).toLongOrNull() ?: 0L
    return base to serial
}

private fun compareVersionName(remoteVersionName: String, currentVersionName: String): Int {
    val remoteVersion = extractVersionName(remoteVersionName)
    val currentVersion = extractVersionName(currentVersionName)
    if (remoteVersion.isBlank() || currentVersion.isBlank()) {
        return remoteVersion.compareTo(currentVersion)
    }
    if (remoteVersion == currentVersion) {
        return 0
    }
    val (remoteBase, remoteSerial) = splitVersionSerial(remoteVersion)
    val (currentBase, currentSerial) = splitVersionSerial(currentVersion)
    // 仓库里遗留过日期式版本号（如 3.26.112314），段数与本自建版本不同：
    // 远端段数更多时是历史旧包，不能提示更新；反过来从旧日期版升到 X 序号版则放行
    val remoteSegments = remoteBase.count { it == '.' } + 1
    val currentSegments = currentBase.count { it == '.' } + 1
    if (remoteSegments != currentSegments) {
        return when {
            remoteSegments > currentSegments -> 0
            remoteSerial > 0 && currentSerial == 0L -> 1
            else -> 0
        }
    }
    val baseCompare = compareBaseVersion(remoteBase, currentBase)
    return if (baseCompare != 0) baseCompare else remoteSerial.compareTo(currentSerial)
}

private fun compareBaseVersion(remoteVersion: String, currentVersion: String): Int {
    val remoteParts = remoteVersion.split(".")
    val currentParts = currentVersion.split(".")
    val maxSize = maxOf(remoteParts.size, currentParts.size)
    for (index in 0 until maxSize) {
        val compare = comparePart(
            remoteParts.getOrElse(index) { "0" },
            currentParts.getOrElse(index) { "0" }
        )
        if (compare != 0) {
            return compare
        }
    }
    return 0
}

private fun comparePart(remote: String, current: String): Int {
    val remoteNum = remote.toLongOrNull()
    val currentNum = current.toLongOrNull()
    if (remoteNum != null && currentNum != null) {
        val maxLen = maxOf(remote.length, current.length)
        val isDateTimePart = maxLen > 5
        val remotePadded = if (isDateTimePart) remote.padEnd(maxLen, '0') else remote.padStart(maxLen, '0')
        val currentPadded = if (isDateTimePart) current.padEnd(maxLen, '0') else current.padStart(maxLen, '0')
        return remotePadded.compareTo(currentPadded)
    }
    return remote.compareTo(current)
}

@Keep
data class GithubRelease(
    val assets: List<Asset>?,
    val body: String,
    @SerializedName("prerelease")
    val isPreRelease: Boolean,
) {
    fun gitReleaseToAppReleaseInfo(): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")
        return assets
            .filter { it.isValid }
            .map { it.assetToAppReleaseInfo(isPreRelease, body) }
    }
}
@Keep
data class GiteeRelease(
    val assets: List<GiteeAsset>?,
    val body: String,
    @SerializedName("prerelease")
    val prerelease: Boolean,
) {
    fun gitReleaseToAppReleaseInfo(): List<AppReleaseInfo> {
        assets ?: throw NoStackTraceException("获取新版本出错")
        return assets
            .filter { it.isValid }
            .map { it.assetToAppReleaseInfo(prerelease, body) }
    }
}

@Keep
data class Asset(
    @SerializedName("browser_download_url")
    val apkUrl: String,
    @SerializedName("content_type")
    val contentType: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("download_count")
    val downloadCount: Int,
    val id: Int,
    val name: String,
    val state: String,
    val url: String
) {
    val isValid: Boolean
        get() = (contentType == "application/vnd.android.package-archive") && (state == "uploaded")

    fun assetToAppReleaseInfo(preRelease: Boolean, note: String): AppReleaseInfo {
        val instant = Instant.parse(createdAt)
        val timestamp: Long = instant.toEpochMilli()

        val appVariant = appVariantFromAssetName(name, preRelease)

        return AppReleaseInfo(appVariant, timestamp, note, name, mirrorUrl(apkUrl), url)
    }
}

/**
 * GitHub 发布包直链在国内经常连不上（表现为 App 内「下载失败」），统一改成走镜像前缀；
 * 镜像返回 302 跳回真实文件，下载端会跟随重定向。
 */
private const val GITHUB_MIRROR = "https://gh-proxy.com/"

private fun mirrorUrl(raw: String): String {
    if (raw.startsWith(GITHUB_MIRROR)) return raw
    return if (raw.contains("github.com") || raw.contains("githubusercontent.com")) {
        GITHUB_MIRROR + raw
    } else {
        raw
    }
}

@Keep
data class GiteeAsset(
    @SerializedName("browser_download_url")
    val apkUrl: String,
    @SerializedName("name")
    val name: String
) {
    val isValid: Boolean
        get() = apkUrl.contains(".apk")

    fun assetToAppReleaseInfo(preRelease: Boolean, note: String): AppReleaseInfo {
        val appVariant = appVariantFromAssetName(name, preRelease)

        return AppReleaseInfo(appVariant, 0, note, name, apkUrl, "")
    }
}
