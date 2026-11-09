package com.example

import android.app.Activity
import android.os.Bundle

/**
 * Inert no-display trap activity used by .GhostLauncherAlias on Android 10+ (API 29+).
 *
 * Why this exists:
 * Starting in Android 10, LauncherAppsService.shouldShowSyntheticActivity() checks if an
 * installed application has 0 enabled CATEGORY_LAUNCHER components. If 0 are enabled, Android 10+
 * automatically injects a synthetic AppDetailsActivity icon using the app's real icon and name.
 *
 * By enabling .GhostLauncherAlias (which has a 100% transparent icon and blank zero-width label)
 * pointing to this GhostTrapActivity, Android 10+ sees 1 enabled launcher alias and does NOT
 * synthesize the visible Shorts Blocker icon. If anyone accidentally taps the invisible slot,
 * this Activity immediately finishes without displaying any UI, keeping the app inaccessible
 * from the home screen while remaining accessible via Accessibility Settings -> Shorts Blocker.
 */
class GhostTrapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
