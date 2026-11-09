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

    /**
     * On Android 10+ (API 29+), LauncherAppsService injects a synthetic visible launcher icon
     * if 0 CATEGORY_LAUNCHER components are enabled. Using the transparent GhostLauncherAlias
     * prevents Android 10+ from showing the synthetic icon while keeping the home screen slot
     * invisible and un-openable. Users can also toggle this off to force 0 launcher aliases on
     * custom OEM launchers that support complete slot removal.
     */
    fun useGhostAliasOnModernAndroid(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(
            KEY_STEALTH_USE_GHOST_ALIAS,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        )
    }

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

    fun syncStealthState(context: Context) {
        val hidden = isStealthEnabled(context)
        val useGhost = useGhostAliasOnModernAndroid(context)
        applyComponentStates(context, hidden, useGhost)
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
            // Ensure SplashActivity itself stays enabled so DefaultLauncherAlias can target it when visible
            if (pm.getComponentEnabledSetting(splashComponent) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(
                    splashComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            if (!hidden) {
                // Normal visible mode: enable DefaultLauncherAlias, disable GhostLauncherAlias
                setComponentStateIfChanged(
                    pm,
                    defaultAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                )
                setComponentStateIfChanged(
                    pm,
                    ghostAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                )
            } else {
                // Hidden Stealth Mode: disable DefaultLauncherAlias
                val shouldEnableGhost = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && useGhostAlias
                if (shouldEnableGhost) {
                    // Enable GhostLauncherAlias first so Android 10+ never sees 0 launcher activities
                    setComponentStateIfChanged(
                        pm,
                        ghostAlias,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    )
                    setComponentStateIfChanged(
                        pm,
                        defaultAlias,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    )
                } else {
                    // Full disable of all launcher aliases
                    setComponentStateIfChanged(
                        pm,
                        ghostAlias,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    )
                    setComponentStateIfChanged(
                        pm,
                        defaultAlias,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
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
        targetState: Int
    ) {
        val current = pm.getComponentEnabledSetting(componentName)
        if (current != targetState) {
            pm.setComponentEnabledSetting(
                componentName,
                targetState,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
