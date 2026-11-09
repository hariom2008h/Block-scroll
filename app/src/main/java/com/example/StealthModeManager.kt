package com.example

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object StealthModeManager {
    private const val PREFS_NAME = "shorts_blocker_prefs"
    const val KEY_HIDE_LAUNCHER_ICON = "hide_launcher_icon"
    const val KEY_STEALTH_USE_GHOST_ALIAS = "stealth_use_ghost_alias"

    fun isStealthEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_LAUNCHER_ICON, false)
    }

    fun useGhostAliasOnModernAndroid(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(
            KEY_STEALTH_USE_GHOST_ALIAS,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        )
    }

    /**
     * Only called when the user explicitly toggles the Stealth Mode switch or Ghost Alias option.
     * Never call this in Activity.onCreate(), because PackageManager.setComponentEnabledSetting()
     * broadcasts ACTION_PACKAGE_CHANGED which causes Android's AccessibilityManagerService to
     * unbind/reset the running AccessibilityService when MainActivity is recreated.
     */
    fun setStealthEnabled(
        context: Context,
        hidden: Boolean,
        useGhostAlias: Boolean = useGhostAliasOnModernAndroid(context)
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HIDE_LAUNCHER_ICON, hidden)
            .putBoolean(KEY_STEALTH_USE_GHOST_ALIAS, useGhostAlias)
            .apply()

        applyComponentStates(context, hidden, useGhostAlias)
        if (context is Activity) {
            syncRecentsExclusion(context, hidden)
        }
    }

    fun syncRecentsExclusion(activity: Activity, exclude: Boolean = isStealthEnabled(activity)) {
        try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.appTasks?.forEach { task ->
                task.setExcludeFromRecents(exclude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyComponentStates(
        context: Context,
        hidden: Boolean,
        useGhostAlias: Boolean
    ) {
        val pm = context.packageManager
        val pkg = context.packageName

        val splashComponent = ComponentName(pkg, "com.example.SplashActivity")
        val defaultAlias = ComponentName(pkg, "com.example.DefaultLauncherAlias")
        val ghostAlias = ComponentName(pkg, "com.example.GhostLauncherAlias")

        try {
            // Ensure SplashActivity stays enabled if it was previously disabled by older versions
            setComponentStateIfChanged(
                pm = pm,
                componentName = splashComponent,
                enable = true,
                manifestDefaultEnabled = true
            )

            if (!hidden) {
                setComponentStateIfChanged(
                    pm = pm,
                    componentName = defaultAlias,
                    enable = true,
                    manifestDefaultEnabled = true
                )
                setComponentStateIfChanged(
                    pm = pm,
                    componentName = ghostAlias,
                    enable = false,
                    manifestDefaultEnabled = false
                )
            } else {
                val shouldEnableGhost = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && useGhostAlias
                if (shouldEnableGhost) {
                    setComponentStateIfChanged(
                        pm = pm,
                        componentName = ghostAlias,
                        enable = true,
                        manifestDefaultEnabled = false
                    )
                    setComponentStateIfChanged(
                        pm = pm,
                        componentName = defaultAlias,
                        enable = false,
                        manifestDefaultEnabled = true
                    )
                } else {
                    setComponentStateIfChanged(
                        pm = pm,
                        componentName = ghostAlias,
                        enable = false,
                        manifestDefaultEnabled = false
                    )
                    setComponentStateIfChanged(
                        pm = pm,
                        componentName = defaultAlias,
                        enable = false,
                        manifestDefaultEnabled = true
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setComponentStateIfChanged(
        pm: PackageManager,
        componentName: ComponentName,
        enable: Boolean,
        manifestDefaultEnabled: Boolean
    ) {
        val currentState = pm.getComponentEnabledSetting(componentName)
        val isCurrentlyEnabled = when (currentState) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> manifestDefaultEnabled
        }
        if (isCurrentlyEnabled != enable) {
            val targetState = if (enable) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                componentName,
                targetState,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
