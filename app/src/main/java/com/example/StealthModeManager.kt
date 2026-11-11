package com.example

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object StealthModeManager {
    private const val PREFS_NAME = "shorts_blocker_prefs"
    const val KEY_HIDE_LAUNCHER_ICON = "hide_launcher_icon"
    const val KEY_FORCE_COMPONENT_HIDE = "stealth_force_component_hide"

    fun isStealthEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_LAUNCHER_ICON, false)
    }

    /**
     * On Android 10+ (especially Vivo, Xiaomi, Oppo, Realme, Samsung), disabling the main
     * launcher Activity via PackageManager.setComponentEnabledSetting causes the OEM Security
     * Manager to immediately revoke/unbind AccessibilityService in microseconds.
     *
     * Therefore, on Android 10+, Stealth Mode defaults to Safe Launcher Lock (blocking
     * SplashActivity from opening MainActivity so the app can ONLY be opened via Accessibility
     * Settings) without disabling the manifest component, unless the user explicitly opts into
     * legacy component disabling.
     */
    fun useLegacyComponentDisable(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(
            KEY_FORCE_COMPONENT_HIDE,
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        )
    }

    fun setStealthEnabled(
        context: Context,
        hidden: Boolean,
        forceComponentDisable: Boolean = useLegacyComponentDisable(context)
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HIDE_LAUNCHER_ICON, hidden)
            .putBoolean(KEY_FORCE_COMPONENT_HIDE, forceComponentDisable)
            .apply()

        val shouldDisableComponent = hidden && forceComponentDisable
        setSplashComponentDisabled(context, shouldDisableComponent)
    }

    /**
     * Repairs SplashActivity if it was left in COMPONENT_ENABLED_STATE_DISABLED from an older APK
     * installation when legacy component disable is not active.
     */
    fun ensureSplashEnabledIfNeeded(context: Context) {
        val shouldBeDisabled = isStealthEnabled(context) && useLegacyComponentDisable(context)
        if (!shouldBeDisabled) {
            setSplashComponentDisabled(context, false)
        }
    }

    private fun setSplashComponentDisabled(context: Context, disable: Boolean) {
        try {
            val pm = context.packageManager
            val splashComponent = ComponentName(context.packageName, "com.example.SplashActivity")
            val currentState = pm.getComponentEnabledSetting(splashComponent)
            if (disable) {
                if (currentState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    pm.setComponentEnabledSetting(
                        splashComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            } else {
                if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                ) {
                    pm.setComponentEnabledSetting(
                        splashComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
