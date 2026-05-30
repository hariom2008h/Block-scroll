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
                try { activeRoot.recycle() } catch (e: Exception) {}
                
                val lockdownEndTime = try {
                    sharedPreferences?.getLong("lockdown_end_time", 0L) ?: 0L
                } catch (e: Exception) { 0L }
                
                val isLockdownActive = System.currentTimeMillis() < lockdownEndTime

                // In lockdown mode, prevent access to settings and package installer to stop uninstalls/accessibility revokes
                if (isLockdownActive && (activePackage == "com.android.settings" || activePackage.contains("packageinstaller") || activePackage.contains("sec.android.app.myfiles"))) {
                     val textContent = getVisibleText(activeRoot).lowercase()
                     val hasOurApp = textContent.contains("shorts blocker") || textContent.contains(packageName.lowercase())
                     val hasDangerousAction = textContent.contains("uninstall") || textContent.contains("delete") || 
                                              textContent.contains("force stop") || textContent.contains("clear data") || 
                                              textContent.contains("clear storage") || textContent.contains("stop shorts blocker") || 
                                              textContent.contains("disable shorts blocker") || textContent.contains("turn off shorts blocker") ||
                                              textContent.contains("use shorts blocker") || textContent.contains("shorts blocker shortcut")
                     
                     if (hasOurApp && hasDangerousAction) {
                         android.widget.Toast.makeText(applicationContext, "Lockdown Mode is Active", android.widget.Toast.LENGTH_SHORT).show()
                         performGlobalAction(GLOBAL_ACTION_HOME)
                         return
                     }
                }

                if (activePackage.isNotEmpty()) {
                    val isTargetActive = activePackage.contains("youtube") || activePackage.contains("instagram") || activePackage.contains("snapchat")
                    val isOurAppActive = activePackage == this.packageName
                    val isSystemActive = activePackage == "com.android.systemui" || activePackage == "android" 
                    val isKeyboardActive = activePackage.contains("inputmethod") || activePackage.contains("keyboard") || activePackage.contains("gboard")

                    // If user is in a completely different app, remove overlay safely
                    if (!isTargetActive && !isOurAppActive && !isSystemActive && !isKeyboardActive) {
                        if (isOverlayShowing) {
                            removeFrictionOverlay()
                        }
                        return
                    }
                }
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
                    showFrictionOverlay()
                } else if (isOverlayShowing && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    // Only remove if it's a WINDOW_STATE_CHANGED (e.g. going back to the home feed)
                    // We don't do this on CONTENT_CHANGED to prevent flickering while shorts is still loading.
                    removeFrictionOverlay()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            if (viewId.isNotEmpty()) {
                val exactId = viewId.substringAfterLast("id/")
                var isSuspect = false
                
                if (viewId.contains("youtube") && (exactId == "shorts_player" || exactId == "reel_recycler" || exactId == "reel_container" || exactId == "shorts_video_player" || exactId == "shorts_container")) isSuspect = true
                if (viewId.contains("instagram") && (exactId == "clips_video_container" || exactId == "reels_viewer_pager" || exactId == "reels_video_player_layout" || exactId == "reels_clip_container" || exactId == "clips_layout")) isSuspect = true
                if (viewId.contains("snapchat") && (exactId == "spotlight" || exactId == "ngs_spotlight" || exactId == "neon_spotlight")) isSuspect = true

                if (isSuspect) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    val screenHeight = resources.displayMetrics.heightPixels
                    // If the shorts component takes up more than half the screen, it's the immersive feed, not a shelf!
                    if (rect.height() > screenHeight * 0.5) {
                        return true
                    }
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
                Toast.makeText(this, "Shorts Blocker: Overlay Permission is required!", Toast.LENGTH_LONG).show()
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
