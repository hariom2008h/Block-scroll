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
    private val BACK_NAVIGATION_COOLDOWN_MS = 2500L // Small cooldown to prevent loop after pressing Go Back
    
    private var lastBlockedPackage = ""

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
            val packageName = event.packageName?.toString() ?: ""

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
                         val hasOurApp = textContent.contains("shorts blocker") || 
                                         textContent.contains("shortsblocker") || 
                                         textContent.contains("blocker") ||
                                         textContent.contains(this.packageName.lowercase())
                         
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
                                                  activePackage.contains("packageinstaller")
                         
                         if (hasOurApp && hasDangerousAction) {
                             android.widget.Toast.makeText(applicationContext, "Lockdown Mode is Active! Settings blocked.", android.widget.Toast.LENGTH_SHORT).show()
                             performGlobalAction(GLOBAL_ACTION_HOME)
                             try { activeRoot.recycle() } catch (e: Exception) {}
                             return
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

            // 2. Filter accessibility events only for our targets
            val isTargetEvent = packageName.isNotEmpty() && (packageName.contains("youtube") || packageName.contains("instagram") || packageName.contains("snapchat"))
            val isSystemApp = packageName == "com.android.systemui" || packageName == "android"
            val isKeyboard = packageName.contains("inputmethod") || packageName.contains("keyboard") || packageName.contains("gboard")

            if (isSystemApp || isKeyboard) {
                // Ignore background system UI events
                return
            }

            if (!isTargetEvent) {
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

            val eventType = event.eventType
            // Trigger on any scroll, content change, or window state change to guarantee it fires
            if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                var isAddictiveMedia = false

                // Check event source node if available
                val eventSource = event.source
                if (eventSource != null) {
                    isAddictiveMedia = checkNodeForShortsOrReels(eventSource)
                    try {
                        eventSource.recycle()
                    } catch (e: Exception) { /* ignore */ }
                }

                // Fallback: check whole visible active window layout hierarchy if the event source did not match
                if (!isAddictiveMedia) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        isAddictiveMedia = checkNodeForShortsOrReels(rootNode)
                        try {
                            rootNode.recycle()
                        } catch (e: Exception) { /* ignore */ }
                    }
                }

                if (isAddictiveMedia) {
                    lastBlockedPackage = packageName
                    
                    if (shouldBlockShorts()) {
                        showFrictionOverlay()
                        isShortsOnScreen = false
                    } else {
                        // User is allowed to watch, start tracking if quota is enabled
                        isShortsOnScreen = true
                        if (!isQuotaTrackerRunning) {
                            isQuotaTrackerRunning = true
                            mainHandler.postDelayed(quotaTrackerRunnable, 1000)
                        }
                    }
                } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    isShortsOnScreen = false // Left shorts completely
                    if (isOverlayShowing) {
                        // Only remove if it's a WINDOW_STATE_CHANGED (e.g. going back to the home feed)
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
        
        if (!Settings.canDrawOverlays(this)) {
            mainHandler.post {
                Toast.makeText(this, "Shorts Blocker: Overlay Permission is required! Redirecting to feed...", Toast.LENGTH_LONG).show()
                redirectToSafeFeed()
            }
            return
        }

        // Execute firmly on the UI Thread
        mainHandler.post {
            if (isOverlayShowing) return@post
            
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT
            )
            layoutParams.dimAmount = 0.65f

            // Add Apple-style native blur support on Android 12 (API 31) and above
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                layoutParams.blurBehindRadius = 60
            }

            try {
                val inflater = LayoutInflater.from(this)
                overlayView = inflater.inflate(R.layout.overlay_password, null)

                val customMessageText = overlayView?.findViewById<android.widget.TextView>(R.id.custom_message_text)
                val msg = sharedPreferences?.getString("custom_block_message", "")
                if (!msg.isNullOrEmpty()) {
                    customMessageText?.text = msg
                }

                val passwordInput = overlayView?.findViewById<EditText>(R.id.password_input)
                val unlockButton = overlayView?.findViewById<Button>(R.id.unlock_button)
                val exitButton = overlayView?.findViewById<Button>(R.id.exit_button)

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
                // Fallback: If overlay fails on MIUI/POCO, just redirect immediately so it still blocks!
                android.widget.Toast.makeText(applicationContext, "Shorts Blocked! (Overlay failed, auto-redirecting)", android.widget.Toast.LENGTH_SHORT).show()
                redirectToSafeFeed()
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
