package io.github.kylinlee.chatsim.data.background

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import io.github.kylinlee.chatsim.domain.background.BackgroundStatus

/** 读取当前后台可靠性相关的系统能力状态。 */
fun Context.readBackgroundStatus(): BackgroundStatus = BackgroundStatus(
    ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
    canScheduleExactAlarms = canScheduleExactAlarms(),
    notificationsGranted = notificationsGranted(),
    isDefaultSms = Telephony.Sms.getDefaultSmsPackage(this) == packageName,
    isDefaultDialer = (getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage == packageName,
)

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.canScheduleExactAlarms(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return true
    return alarmManager.canScheduleExactAlarms()
}

private fun Context.notificationsGranted(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
