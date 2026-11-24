package io.legado.app.help

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.welcome.WelcomeActivity
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

/**
 * 更换桌面图标。
 *
 * 图标清单只认一处：res/values/array_values.xml 的 icons 数组。
 * 数组值形如 ic_launcher（主入口本身）、launcher1/launcher2/launcher3，
 * 后者对应同包的 Launcher1/Launcher2/Launcher3（AndroidManifest 里各自带 LAUNCHER 图标的 Activity）。
 * 显示名和缩略图都由这个数组推导，所以增删图标只需要改数组、清单和对应 mipmap。
 */
object LauncherIconHelp {
    /** 默认图标，对应 WelcomeActivity 本身 */
    const val ICON_MAIN = "ic_launcher"
    private const val ICON_PREFIX = "launcher"
    private const val TAG = "LauncherIconHelp"

    private val packageManager: PackageManager = appCtx.packageManager
    private val welcomeComponent = ComponentName(appCtx, WelcomeActivity::class.java.name)

    /** 入口组件所在包：取自 WelcomeActivity 自身，不能用 packageName（那带着 applicationId 后缀） */
    private val aliasPackage = WelcomeActivity::class.java.name.substringBeforeLast('.')

    /** 图标清单唯一数据源 */
    private val iconValues: List<String> by lazy {
        appCtx.resources.getStringArray(R.array.icons).toList()
    }

    /**
     * 图标重新编号前的老值 → 新值。
     * 老版本 icon2/6/7 的顺序保留下来，分别对应当前的 1/2/3；老版本其余图标已删除，一律回落默认。
     */
    private val legacyIconMap = mapOf(
        "launcher2" to "launcher1",
        "launcher6" to "launcher2",
        "launcher7" to "launcher3"
    )

    /** launcher1 → Launcher1 的入口组件；主图标没有独立入口，返回 null */
    private fun aliasComponent(value: String): ComponentName? {
        if (value == ICON_MAIN) return null
        val suffix = value.removePrefix(ICON_PREFIX)
        if (suffix.isEmpty()) return null
        return ComponentName(appCtx, "$aliasPackage.Launcher$suffix")
    }

    /**
     * 把任意写法（大小写不同、历史遗留值、已被删除的图标）折算成清单里的规范值，
     * 非法值一律回落到默认图标，避免界面出现“选不中”的空状态。
     */
    fun canonicalIcon(value: String?): String {
        if (value.isNullOrBlank()) return ICON_MAIN
        val trimmed = value.trim()
        return iconValues.firstOrNull { it.equals(trimmed, true) } ?: ICON_MAIN
    }

    /**
     * 组件是否实际启用。DEFAULT 表示跟随清单声明：
     * WelcomeActivity 声明为启用，各个图标入口声明为 android:enabled="false"。
     * 组件名和清单对不上时按“未启用”处理，不让它影响启动。
     */
    private fun isEffectiveEnabled(component: ComponentName, defaultEnabled: Boolean): Boolean {
        return runCatching {
            when (packageManager.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                else -> defaultEnabled
            }
        }.getOrDefault(defaultEnabled)
    }

    /** 组件写状态同样做兜底：清单里已经没有的图标不该让整个流程崩掉 */
    private fun setEnabled(component: ComponentName, enabled: Boolean) {
        runCatching {
            packageManager.setComponentEnabledSetting(
                component,
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /** 只让 activeValue 对应的入口生效，其余全部关掉 */
    private fun applyState(activeValue: String) {
        iconValues.forEach { value ->
            val component = aliasComponent(value) ?: return@forEach
            setEnabled(component, value == activeValue)
        }
        setEnabled(welcomeComponent, activeValue == ICON_MAIN)
    }

    /** 目标状态是否已经生效，避免每次启动都重复写组件状态 */
    private fun isStateConsistent(activeValue: String): Boolean {
        if (isEffectiveEnabled(welcomeComponent, defaultEnabled = true) != (activeValue == ICON_MAIN)) {
            return false
        }
        return iconValues.all { value ->
            val component = aliasComponent(value) ?: return@all true
            isEffectiveEnabled(component, defaultEnabled = false) == (value == activeValue)
        }
    }

    /**
     * 图标重新编号的一次性迁移：老值先换成新值，再按新值重建入口状态。
     * 之前选过已删除图标的，会回落默认图标，不会出现“装完更新桌面没入口”。
     */
    private fun migrateIconNumbers() {
        if (Build.VERSION.SDK_INT < 26) return
        if (appCtx.getPrefBoolean(PreferKey.launcherIconMigrated)) return
        val saved = appCtx.getPrefString(PreferKey.launcherIcon)
        if (!saved.isNullOrBlank()) {
            val migrated = legacyIconMap[saved.lowercase()] ?: canonicalIcon(saved)
            applyState(migrated)
            appCtx.putPrefString(PreferKey.launcherIcon, migrated)
        }
        appCtx.putPrefBoolean(PreferKey.launcherIconMigrated, true)
    }

    /** Debug 自检：数组里的每个图标都必须有对应入口，防止删改漏同步 */
    private fun checkIntegrity() {
        if (!BuildConfig.DEBUG) return
        iconValues.forEach { value ->
            val component = aliasComponent(value) ?: return@forEach
            @Suppress("DEPRECATION")
            val exists = runCatching {
                packageManager.getActivityInfo(component, 0)
                true
            }.getOrDefault(false)
            if (!exists) {
                Log.w(TAG, "图标清单里的 $value 在 AndroidManifest 中找不到入口 $component")
            }
        }
    }

    /**
     * 校正图标设置：偏好里存的值与实际启用的组件保持一致。
     * 升级安装、恢复备份、外部改动都可能让两者不一致，每次启动跑一次。
     */
    @Synchronized
    fun fixLauncherIconPref() {
        if (Build.VERSION.SDK_INT < 26) return
        migrateIconNumbers()
        checkIntegrity()

        val saved = appCtx.getPrefString(PreferKey.launcherIcon)

        // 以实际启用的入口为准；一个都没开就当作默认图标
        val enabledAlias = iconValues.firstOrNull { value ->
            val component = aliasComponent(value) ?: return@firstOrNull false
            isEffectiveEnabled(component, defaultEnabled = false)
        }
        val actual = enabledAlias ?: ICON_MAIN

        if (actual != saved) {
            appCtx.putPrefString(PreferKey.launcherIcon, actual)
        }
        if (isStateConsistent(actual)) return
        // 状态不一致（同时开了两个入口、入口是名单外的旧值等）时按实际启用的那个收口
        applyState(actual)
    }

    /**
     * 切换图标。[silent] 为 true 时不弹提示（恢复备份等自动流程用）。
     */
    @Synchronized
    fun changeIcon(icon: String?, silent: Boolean = false) {
        if (icon.isNullOrBlank()) return
        if (Build.VERSION.SDK_INT < 26) {
            if (!silent) appCtx.toastOnUi(R.string.change_icon_error)
            return
        }
        val target = canonicalIcon(icon)
        val saved = appCtx.getPrefString(PreferKey.launcherIcon)
        // 已经是这个图标且入口状态一致，直接返回，避免重复写组件状态和偏好（偏好回写会再次触发监听）
        if (saved == target && isStateConsistent(target)) return
        applyState(target)
        if (saved != target) {
            appCtx.putPrefString(PreferKey.launcherIcon, target)
        }
    }

    /**
     * 恢复备份后同步图标。
     * 低版本没有矢量图标可选，直接把偏好清成默认图标，避免“恢复成功”后又弹一句不支持提示。
     * 备份里带着已删除的图标值时，[changeIcon] 会回落到默认图标并写回偏好。
     */
    fun applyRestoredIcon() {
        if (Build.VERSION.SDK_INT < 26) {
            appCtx.putPrefString(PreferKey.launcherIcon, ICON_MAIN)
            return
        }
        changeIcon(appCtx.getPrefString(PreferKey.launcherIcon), silent = true)
    }
}
