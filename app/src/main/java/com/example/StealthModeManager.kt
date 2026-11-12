package com.example

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object StealthModeManager {
    private const val PREFS_NAME = "shorts_blocker_prefs"
    const val KEY_HIDE_LAUNCHER_ICON = "hide_launcher_icon"

    fun isStealthEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_LAUNCHER_ICON, false)
    }

    /**
     * Enables or disables Stealth Mode without modifying AndroidManifest component states via
     * PackageManager.setComponentEnabledSetting.
     *
     * Why: Calling PackageManager.setComponentEnabledSetting on modern Android (Android 10+ / OEM
     * ROMs like Vivo, Xiaomi, Oppo, Realme, Samsung) broadcasts ACTION_PACKAGE_CHANGED, which
     * causes the OS AccessibilityManagerService to immediately unbind/revoke AccessibilityService
     * in microseconds.
     */
    fun setStealthEnabled(
        context: Context,
        hidden: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HIDE_LAUNCHER_ICON, hidden)
            .apply()

        if (context is Activity) {
            applyRecentsStealth(context, hidden)
        }
    }

    fun applyRecentsStealth(activity: Activity, hidden: Boolean = isStealthEnabled(activity)) {
        try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.forEach { task ->
                task.setExcludeFromRecents(hidden)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isAccessibilityServiceRunning(context: Context): Boolean {
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices != null) {
                for (service in enabledServices) {
                    if (service.resolveInfo?.serviceInfo?.packageName == context.packageName) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val enabledServicesStr = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (enabledServicesStr?.contains(context.packageName, ignoreCase = true) == true) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
