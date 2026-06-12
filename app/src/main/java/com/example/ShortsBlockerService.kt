package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityNodeInfo

class ShortsBlockerService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var sharedPreferences: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var lastUnlockTime = 0L
    private val UNLOCK_COOLDOWN_MS = 15000L // 15 seconds of friction-free time before next block
    
    private var lastBackNavigationTime = 0L
    private val BACK_NAVIGATION_COOLDOWN_MS = 3000L // Small cooldown to prevent loop after pressing Go Back
    private var lastBlockAttemptTime = 0L
    
    private var lastBlockedPackage = ""
    private var currentForegroundPackage = ""

    private fun isTargetPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("youtube") || lower.contains("instagram") || lower.contains("snapchat")
    }

    private fun isNeutralPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("inputmethod") || 
               lower.contains("keyboard") || 
               lower.contains("gboard") || 
               lower == "com.android.systemui" || 
               lower == "android"
    }

    private fun isTargetInForeground(): Boolean {
        val activeRoot = try { rootInActiveWindow } catch (e: Exception) { null }
        if (activeRoot != null) {
            val activePackage = activeRoot.packageName?.toString() ?: ""
            try { activeRoot.recycle() } catch (ex: Exception) {}
            if (activePackage.isNotEmpty()) {
                if (isTargetPackage(activePackage)) {
                    return true
                }
                if (isNeutralPackage(activePackage)) {
                    return isTargetPackage(currentForegroundPackage)
                }
                if (activePackage == this.packageName) {
                    return false
                }
                return false
            }
        }
        return isTargetPackage(currentForegroundPackage)
    }

    private var isShortsOnScreen = false
    private var isQuotaTrackerRunning = false

    private val quotaTrackerRunnable = object : Runnable {
        override fun run() {
            if (isShortsOnScreen) {
                val prefs = sharedPreferences ?: return
                val quotaEnabled = prefs.getBoolean("quota_enabled", false)
                if (quotaEnabled) {
                    var used = prefs.getLong("quota_used_ms", 0L)
                    val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
                    val lastDate = prefs.getString("quota_last_date", "")
                    if (dateStr != lastDate) {
                        used = 0L
                        prefs.edit().putString("quota_last_date", dateStr).apply()
                    }
                    used += 1000L
                    prefs.edit().putLong("quota_used_ms", used).apply()
                    
                    val limit = prefs.getLong("quota_limit_ms", 15 * 60 * 1000L)
                    if (used >= limit) {
                        mainHandler.post {
                            Toast.makeText(this@ShortsBlockerService, "Daily Quota Exceeded!", Toast.LENGTH_LONG).show()
                            showFrictionOverlay()
                        }
                        isShortsOnScreen = false // stop tracking to prevent loop
                        isQuotaTrackerRunning = false
                        return
                    }
                }
                mainHandler.postDelayed(this, 1000)
            } else {
                isQuotaTrackerRunning = false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val eventPackage = event.packageName?.toString() ?: ""
            val eventType = event.eventType

            // Update current foreground package tracking on window state changes
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventPackage.isNotEmpty()) {
                if (!isNeutralPackage(eventPackage) && eventPackage != this.packageName) {
                    currentForegroundPackage = eventPackage
                }
            }

            // 1. Check Active Window Package to detect if user left YouTube/Instagram completely
            val activeRoot = try { rootInActiveWindow } catch (e: Exception) { null }
            if (activeRoot != null) {
                val activePackage = activeRoot.packageName?.toString() ?: ""
                
                val lockdownEndTime = try {
                    sharedPreferences?.getLong("lockdown_end_time", 0L) ?: 0L
                } catch (e: Exception) { 0L }
                
                val isLockdownActive = System.currentTimeMillis() < lockdownEndTime

                // In lockdown mode, prevent access to settings and package installer to stop uninstalls/accessibility revokes
                if (isLockdownActive) {
                    val isSettingsOrInstaller = activePackage == "com.android.settings" || 
                                                activePackage.contains("packageinstaller") || 
                                                activePackage.contains("sec.android.app.myfiles") || 
                                                activePackage.contains("securitycenter") ||
                                                activePackage.contains("cleanmaster") ||
                                                activePackage.contains("settings")
                    
                    val isLauncher = activePackage.contains("launcher") || 
                                     activePackage.contains("home") || 
                                     activePackage.contains("miui.home") ||
                                     activePackage.contains("car") ||
                                     activePackage.contains("trebuchet")
                    
                    if (isSettingsOrInstaller && !isLauncher && activePackage != this.packageName) {
                         val textContent = getVisibleText(activeRoot).lowercase()
                         
                         // English & Hindi matches for our app name to prevent uninstalls
                         val hasOurApp = textContent.contains("shorts blocker") || 
                                         textContent.contains("shortsblocker") || 
                                         textContent.contains("शॉर्ट्स ब्लॉकर") || 
                                         textContent.contains(this.packageName.lowercase())
                         
                         // Support for both English and Hindi dangerous action strings (important for POCO/MIUI, Samsung, and Indian users)
                         val hasDangerousAction = textContent.contains("uninstall") || 
                                                  textContent.contains("delete") || 
                                                  textContent.contains("force stop") || 
                                                  textContent.contains("clear data") || 
                                                  textContent.contains("clear storage") || 
                                                  textContent.contains("stop shorts blocker") || 
                                                  textContent.contains("disable shorts blocker") || 
                                                  textContent.contains("turn off shorts blocker") ||
                                                  textContent.contains("use shorts blocker") || 
                                                  textContent.contains("shorts blocker shortcut") ||
                                                  textContent.contains("accessibility") ||
                                                  textContent.contains("जबरन रोकें") || 
                                                  textContent.contains("अनइंस्टॉल") || 
                                                  textContent.contains("डेटा साफ़") || 
                                                  textContent.contains("डेटा साफ") || 
                                                  textContent.contains("साफ़ करें") || 
                                                  textContent.contains("साफ करें") || 
                                                  textContent.contains("बंद करें") || 
                                                  textContent.contains("रोकें") || 
                                                  textContent.contains("हटाएं") ||
                                                  activePackage.contains("packageinstaller")
                         
                         if (hasOurApp && hasDangerousAction) {
                             // To avoid false positives on list screens (e.g. main settings, apps list, battery stats),
                             // verify if the screen displays multiple other app names. Our detail screen focuses exclusively on us.
                             val otherApps = listOf("youtube", "instagram", "whatsapp", "facebook", "chrome", "gmail", "spotify", "maps")
                             val otherAppsCount = otherApps.count { textContent.contains(it) }
                             
                             // If multiple other app names exist, it's just a general list screen, so DO NOT block.
                             if (otherAppsCount < 2) {
                                 android.widget.Toast.makeText(applicationContext, "Lockdown Mode is Active! Settings blocked.", android.widget.Toast.LENGTH_SHORT).show()
                                 performGlobalAction(GLOBAL_ACTION_HOME)
                                 try { activeRoot.recycle() } catch (e: Exception) {}
                                 return
                             }
                         }
                    }
                }

                if (activePackage.isNotEmpty()) {
                    val isTargetActive = activePackage.contains("youtube") || activePackage.contains("instagram") || activePackage.contains("snapchat")
                    val isOurAppActive = activePackage == this.packageName
                    val isSystemActive = activePackage == "com.android.systemui" || activePackage == "android" 
                    val isKeyboardActive = activePackage.contains("inputmethod") || activePackage.contains("keyboard") || activePackage.contains("gboard")

                    // If user is in a completely different app, remove overlay safely
                    if (!isTargetActive && !isOurAppActive && !isSystemActive && !isKeyboardActive) {
                        try { activeRoot.recycle() } catch (e: Exception) {}
                        if (isOverlayShowing) {
                            removeFrictionOverlay()
                        }
                        return
                    }
                }
                try { activeRoot.recycle() } catch (e: Exception) {}
            }

            // 2. Early exit check: are we actually in a YouTube/Instagram/Snapchat context?
            if (!isTargetInForeground()) {
                // If they are on the Launcher, our app, Settings, or another non-neutral app, dismiss the overlay safely and stop the quota timer!
                isShortsOnScreen = false
                if (isOverlayShowing) {
                    removeFrictionOverlay()
                }
                return
            }

            // 3. Only process block logic for target app events
            if (!isTargetPackage(eventPackage)) {
                return
            }

            // If user recently entered the correct password, do not show lock overlay during cooldown period
            val currentTime = System.currentTimeMillis()
            if (currentTime < lastUnlockTime + UNLOCK_COOLDOWN_MS) {
                return
            }

            // If user recently pressed "Go Back", wait for UI to exit shorts before checking again
            if (currentTime < lastBackNavigationTime + BACK_NAVIGATION_COOLDOWN_MS) {
                return
            }

            // Trigger detection on key events (scrolls, page transitions, window changes)
            if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                var isAddictiveMedia = false

                // Try to detect Reels/Shorts from the event source node
                val eventSource = event.source
                if (eventSource != null) {
                    isAddictiveMedia = checkNodeForShortsOrReels(eventSource)
                    try {
                        eventSource.recycle()
                    } catch (e: Exception) { /* ignore */ }
                }

                // If not found in the source event, scan the entire foreground window's layout tree
                if (!isAddictiveMedia) {
                    val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                    if (rootNode != null) {
                        isAddictiveMedia = checkNodeForShortsOrReels(rootNode)
                        try {
                            rootNode.recycle()
                        } catch (e: Exception) { /* ignore */ }
                    }
                }

                if (isAddictiveMedia) {
                    lastBlockedPackage = eventPackage
                    if (shouldBlockShorts()) {
                        showFrictionOverlay()
                        isShortsOnScreen = false
                    } else {
                        // User is in target feed and authorized (quota timer active)
                        isShortsOnScreen = true
                        if (!isQuotaTrackerRunning) {
                            isQuotaTrackerRunning = true
                            mainHandler.postDelayed(quotaTrackerRunnable, 1000)
                        }
                    }
                } else {
                    // It is a target app, but they've left the shorts/reels portion (e.g., watching regular videos, in profiles, in DM chat)
                    isShortsOnScreen = false
                    if (isOverlayShowing) {
                        removeFrictionOverlay()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shouldBlockShorts(): Boolean {
        val prefs = sharedPreferences ?: return true
        
        val scheduleEnabled = prefs.getBoolean("schedule_enabled", false)
        val quotaEnabled = prefs.getBoolean("quota_enabled", false)
        
        var block = true
        
        if (scheduleEnabled) {
            val sh = prefs.getInt("schedule_start_hour", 9)
            val sm = prefs.getInt("schedule_start_minute", 0)
            val eh = prefs.getInt("schedule_end_hour", 17)
            val em = prefs.getInt("schedule_end_minute", 0)
            
            val startMins = sh * 60 + sm
            val endMins = eh * 60 + em
            
            val c = java.util.Calendar.getInstance()
            val nowMins = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
            
            val inSchedule = if (startMins <= endMins) {
                nowMins in startMins..endMins
            } else {
                nowMins >= startMins || nowMins <= endMins
            }
            
            if (inSchedule) {
                return true // Definitely block if within restricted schedule
            } else {
                block = false // allowed outside schedule
            }
        }
        
        if (quotaEnabled) {
            val used = prefs.getLong("quota_used_ms", 0L)
            val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
            val lastDate = prefs.getString("quota_last_date", "")
            
            val actualUsed = if (dateStr == lastDate) used else 0L
            val limit = prefs.getLong("quota_limit_ms", 15 * 60 * 1000L)
            
            if (actualUsed >= limit) {
                return true // Definitely block if quota exceeded
            } else {
                block = false // allowed if quota remains
            }
        }
        
        if (!scheduleEnabled && !quotaEnabled) {
            return true // Default behavior: always block if both are disabled
        }
        
        return block
    }

    private fun getVisibleText(node: android.view.accessibility.AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val text = StringBuilder()
        if (node.text != null) text.append(node.text).append(" ")
        if (node.contentDescription != null) text.append(node.contentDescription).append(" ")
        
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (e: Exception) { null }
            if (child != null) {
                text.append(getVisibleText(child))
                try { child.recycle() } catch (e: Exception) {}
            }
        }
        return text.toString()
    }

    private fun checkNodeForShortsOrReels(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        try {
            val viewId = node.viewIdResourceName ?: ""
            val exactId = if (viewId.isNotEmpty()) viewId.substringAfterLast("id/") else ""
            val pkg = node.packageName?.toString() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            
            val prefs = sharedPreferences
            val blockYt = prefs?.getBoolean("block_youtube", true) ?: true
            val blockIg = prefs?.getBoolean("block_instagram", true) ?: true
            val blockSc = prefs?.getBoolean("block_snapchat", true) ?: true
            
            if (blockYt && pkg.contains("youtube")) {
                if (exactId == "shorts_player" || exactId == "reel_recycler" || exactId == "shorts_video_player" || exactId == "shorts_container") {
                    return true
                }
                if (exactId.contains("reel") || exactId.contains("shorts")) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    val screenHeight = resources.displayMetrics.heightPixels
                    if (rect.height() > screenHeight * 0.50) return true
                }
            }
            if (blockIg && pkg.contains("instagram")) {
                if (exactId == "clips_video_container" || exactId == "reels_viewer_pager" || exactId == "reels_video_player_layout" || exactId == "reels_clip_container" || exactId == "clips_layout" || exactId == "bottom_sheet_container_view" || exactId == "reels_viewer_container") {
                    return true
                }
                if (exactId.contains("reel") || exactId.contains("clips")) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    val screenHeight = resources.displayMetrics.heightPixels
                    if (rect.height() > screenHeight * 0.50) return true
                }
            }
            if (blockSc && pkg.contains("snapchat")) {
                if (exactId == "neon_spotlight" || exactId == "df_main_pager") {
                    return true
                }
                if (exactId.contains("spotlight") || exactId.contains("layered_video_view") || desc.contains("spotlight")) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    val screenHeight = resources.displayMetrics.heightPixels
                    if (rect.height() > screenHeight * 0.50) return true
                }
            }


            // Traverse hierarchy safely
            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                }
                if (child != null) {
                    val found = checkNodeForShortsOrReels(child)
                    try {
                        child.recycle()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (found) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun showFrictionOverlay() {
        if (isOverlayShowing) return
        
        val now = System.currentTimeMillis()
        if (now - lastBlockAttemptTime < 3000) {
            // Rate limit blocks to prevent endless fast looping
            return
        }
        lastBlockAttemptTime = now
        
        if (!Settings.canDrawOverlays(this)) {
            mainHandler.post {
                Toast.makeText(this, "Shorts Blocker: Overlay Permission is required! Redirecting to feed...", Toast.LENGTH_LONG).show()
                redirectToSafeFeed()
            }
            return
        }

        // Execute firmly on the UI Thread
        mainHandler.post {
            try {
                if (overlayView != null) {
                    try {
                        windowManager.removeView(overlayView)
                    } catch (ex: Exception) {
                        // ignored
                    }
                    overlayView = null
                }
                
                val themedContext = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_NoActionBar)
                val inflater = LayoutInflater.from(themedContext)
                overlayView = inflater.inflate(R.layout.overlay_password, null)

                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or 
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_SECURE,
                    PixelFormat.TRANSLUCENT
                )
                layoutParams.dimAmount = 0.65f

                // Add Apple-style native blur support on Android 12 (API 31) and above
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    layoutParams.blurBehindRadius = 60
                }

                val customMessageText = overlayView?.findViewById<android.widget.TextView>(R.id.custom_message_text)
                val msg = sharedPreferences?.getString("custom_block_message", "")
                if (!msg.isNullOrEmpty()) {
                    customMessageText?.text = msg
                }

                val passwordInput = overlayView?.findViewById<EditText>(R.id.password_input)
                val unlockButton = overlayView?.findViewById<Button>(R.id.unlock_button)
                val exitButton = overlayView?.findViewById<Button>(R.id.exit_button)
                val passwordToggle = overlayView?.findViewById<android.widget.ImageView>(R.id.password_toggle)

                var isPasswordVisible = false
                passwordToggle?.setOnClickListener {
                    isPasswordVisible = !isPasswordVisible
                    val typeface = passwordInput?.typeface
                    if (isPasswordVisible) {
                        passwordInput?.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        passwordToggle.setImageResource(R.drawable.ic_eye_hidden)
                    } else {
                        passwordInput?.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        passwordToggle.setImageResource(R.drawable.ic_eye_visible)
                    }
                    passwordInput?.typeface = typeface
                    passwordInput?.text?.let { text ->
                        passwordInput.setSelection(text.length)
                    }
                }

                unlockButton?.setOnClickListener {
                    val correctPassword = try {
                        sharedPreferences?.getString("master_password", "I will not waste my time") ?: "I will not waste my time"
                    } catch (e: Exception) {
                        "I will not waste my time" // Hard fallback
                    }
                    
                    val isStrictMode = try {
                        sharedPreferences?.getBoolean("strict_mode", false) ?: false
                    } catch (e: Exception) { false }
                    
                    val enteredPassword = passwordInput?.text.toString()

                    if (enteredPassword == correctPassword) {
                        lastUnlockTime = System.currentTimeMillis()
                        sharedPreferences?.edit()?.putLong("last_shorts_watch_time", lastUnlockTime)?.apply()
                        removeFrictionOverlay()
                    } else {
                        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            vibrator.vibrate(100)
                        }

                        if (isStrictMode) {
                            android.widget.Toast.makeText(applicationContext, "Strict Mode: Access Denied", android.widget.Toast.LENGTH_SHORT).show()
                            exitButton?.performClick()
                        } else {
                            passwordInput?.error = "Incorrect Password"
                            val shake = android.view.animation.TranslateAnimation(0f, 20f, 0f, 0f)
                            shake.duration = 50
                            shake.repeatMode = android.view.animation.Animation.REVERSE
                            shake.repeatCount = 5
                            passwordInput?.startAnimation(shake)
                        }
                    }
                }

                exitButton?.setOnClickListener {
                    lastBackNavigationTime = System.currentTimeMillis()
                    
                    // Remove overlay immediately so the underlying app regains window focus
                    removeFrictionOverlay()
                    
                    // Trigger global action back after a short delay so it successfully exits the shorts player
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        redirectToSafeFeed()
                    }, 150)
                }

                overlayView?.alpha = 0f
                windowManager.addView(overlayView, layoutParams)
                overlayView?.animate()?.alpha(1f)?.setDuration(250)?.start()
                isOverlayShowing = true
                
                try {
                    val totals = sharedPreferences?.getInt("shorts_blocked_total", 0) ?: 0
                    sharedPreferences?.edit()?.putInt("shorts_blocked_total", totals + 1)?.apply()
                } catch (e: Exception) {}
                
            } catch (e: Exception) {
                e.printStackTrace()
                isOverlayShowing = false
                overlayView = null
                lastBackNavigationTime = System.currentTimeMillis()
                
                // Fallback: If overlay fails on MIUI/POCO/Custom skins, minimize to home out of the dopamine loop!
                android.widget.Toast.makeText(applicationContext, "Shorts Blocked! (Minimizing feed to home)", android.widget.Toast.LENGTH_LONG).show()
                try {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } catch (homeEx: Exception) {
                    redirectToSafeFeed()
                }
            }
        }
    }

    private fun getTargetAppRootNode(): AccessibilityNodeInfo? {
        try {
            val windowsList = windows
            for (window in windowsList) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val root = try { window.root } catch (e: Exception) { null }
                    if (root != null) {
                        val pkgName = root.packageName?.toString() ?: ""
                        if (pkgName.contains("youtube") || pkgName.contains("instagram") || pkgName.contains("snapchat")) {
                            return root
                        }
                        root.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return try { rootInActiveWindow } catch (e: Exception) { null }
    }

    private fun clickNodeByContentDescription(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val desc = node.contentDescription?.toString()
        val text = node.text?.toString()
        
        for (keyword in keywords) {
            val matchDesc = desc != null && (desc.equals(keyword, ignoreCase = true) || 
                desc.startsWith("$keyword,", ignoreCase = true) || 
                desc.contains(keyword, ignoreCase = true))
                
            val matchText = text != null && (text.equals(keyword, ignoreCase = true) || 
                text.contains(keyword, ignoreCase = true))

            if (matchDesc || matchText) {
                var clickableNode: AccessibilityNodeInfo? = node
                var depth = 0
                while (clickableNode != null && depth < 5) {
                    if (clickableNode.isClickable) {
                        val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (success) return true
                    }
                    clickableNode = clickableNode.parent
                    depth++
                }
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (e: Exception) { null } ?: continue
            val clicked = clickNodeByContentDescription(child, keywords)
            try { child.recycle() } catch (e: Exception) {}
            if (clicked) return true
        }
        return false
    }

    private fun redirectToSafeFeed() {
        try {
            // Perform Global Action Back to get out of the shorts feed cleanly
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFrictionOverlay() {
        mainHandler.post {
            if (!isOverlayShowing || overlayView == null) return@post
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isOverlayShowing = false
                overlayView = null
            }
        }
    }

    override fun onInterrupt() {
        removeFrictionOverlay()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        removeFrictionOverlay()
    }
}
