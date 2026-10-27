package com.priorityring.blocks.android

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.priorityring.engine.block.DeviceCapabilities
import com.priorityring.engine.block.Requirement
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "is this requirement granted right now?" for every [Requirement] id, and knows which
 * system screen lets the user grant it.
 *
 * Android has several *kinds* of permission, which is why each check looks different:
 * - runtime permissions (POST_NOTIFICATIONS): a dialog via ActivityResult; checked with checkSelfPermission.
 * - "special app access" (notification listener, DND, write settings, usage access, exact alarms,
 *   full-screen intents): no dialog exists — the user must flip a switch on a system settings
 *   screen, and we re-check when they come back.
 *
 * Results are never cached: users can revoke any of these at any time from Settings.
 */
@Singleton
class AndroidDeviceCapabilities @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceCapabilities {

    override val sdkInt: Int get() = Build.VERSION.SDK_INT

    override fun hasFeature(feature: String): Boolean =
        context.packageManager.hasSystemFeature(feature)

    override fun isGranted(requirement: Requirement): Boolean = when (requirement.id) {
        Requirement.NOTIFICATION_LISTENER.id ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        Requirement.POST_NOTIFICATIONS.id ->
            // Runtime permission only since Android 13 (API 33); before that it's always allowed
            // unless the user disabled notifications for the app.
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }

        Requirement.FULL_SCREEN_INTENT.id ->
            // Android 14+ made this revocable and, on Play, auto-granted only to calling/alarm apps.
            if (Build.VERSION.SDK_INT >= 34) {
                context.getSystemService<NotificationManager>()?.canUseFullScreenIntent() == true
            } else true

        Requirement.DND_ACCESS.id ->
            context.getSystemService<NotificationManager>()?.isNotificationPolicyAccessGranted == true

        Requirement.WRITE_SETTINGS.id -> Settings.System.canWrite(context)

        Requirement.USAGE_ACCESS.id -> hasUsageAccess()

        Requirement.EXACT_ALARMS.id ->
            // Denied by default for new installs on Android 14+ (API 34).
            if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
            } else true

        else -> false // Unknown requirement: report as missing rather than pretend it's fine.
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        // There is no non-deprecated public API for this check on API 37; the AppOps check is
        // still what the platform documents for detecting usage access.
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * The system screen where the user can grant [requirement], or null when it's a runtime
     * permission that must be requested with a dialog from an Activity instead.
     */
    fun settingsIntent(requirement: Requirement): Intent? {
        val pkg = Uri.fromParts("package", context.packageName, null)
        return when (requirement.id) {
            Requirement.NOTIFICATION_LISTENER.id -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Requirement.DND_ACCESS.id -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            Requirement.WRITE_SETTINGS.id -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            Requirement.USAGE_ACCESS.id -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            Requirement.EXACT_ALARMS.id ->
                if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else null
            Requirement.FULL_SCREEN_INTENT.id ->
                // The package: URI is mandatory for this action.
                if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg) else null
            Requirement.POST_NOTIFICATIONS.id ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        /** Requirements shown on the Settings / diagnostics screen, in display order. */
        val ALL = listOf(
            Requirement.NOTIFICATION_LISTENER,
            Requirement.POST_NOTIFICATIONS,
            Requirement.FULL_SCREEN_INTENT,
            Requirement.EXACT_ALARMS,
            Requirement.DND_ACCESS,
            Requirement.WRITE_SETTINGS,
            Requirement.USAGE_ACCESS,
        )
    }
}
