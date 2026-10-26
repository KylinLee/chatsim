package io.github.kylinlee.chatsim.data.background

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** 后台可靠性相关的系统设置入口。 */
object BackgroundIntents {
    fun batteryOptimization(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )

    fun exactAlarm(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            null
        }

    fun notifications(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun appDetails(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

    /** 尝试打开厂商自启动管理页；无法命中时回退到应用详情。 */
    fun autoStart(context: Context): Intent {
        val packageManager = context.packageManager
        autoStartComponents().forEach { component ->
            val intent = Intent().setComponent(component)
            if (packageManager.resolveActivity(intent, 0) != null) return intent
        }
        return appDetails(context)
    }

    private fun autoStartComponents(): List<ComponentName> = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.lool.activity.sm.BatteryActivity"),
        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
    )
}

/** 启动系统设置页，失败时回退到应用详情，避免 ActivityNotFound 崩溃。 */
fun Context.openBackgroundIntent(intent: Intent) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    runCatching { startActivity(intent.addFlags(flags)) }
        .onFailure { runCatching { startActivity(BackgroundIntents.appDetails(this).addFlags(flags)) } }
}
