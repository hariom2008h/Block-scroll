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

    private fun checkNodeForShortsOrReels(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        try {
            val viewId = node.viewIdResourceName ?: ""

            // Filter YouTube Shorts player elements precisely (prevent normal feed/search scrolls from being blocked)
            if (viewId.contains("com.google.android.youtube:id/shorts_player") ||
                viewId.contains("com.google.android.youtube:id/shorts_video_player") ||
                viewId.contains("com.google.android.youtube:id/reel_recycler") ||
                viewId.contains("com.google.android.youtube:id/reel_container") ||
                viewId.contains("com.google.android.youtube:id/shorts_container") ||
                viewId.contains("com.google.android.youtube:id/player_view_front_interface") ||
                viewId.contains("com.google.android.youtube:id/reel_sheet_container") ||
                viewId.contains("com.google.android.youtube:id/panel_container")) {
                return true
            }

            // Filter Instagram Reels elements precisely
            if (viewId.contains("com.instagram.android:id/clips_video_container") ||
                viewId.contains("com.instagram.android:id/reels_viewer_pager") ||
                viewId.contains("com.instagram.android:id/clips_viewer_container") ||
                viewId.contains("com.instagram.android:id/reels_video_player_layout") ||
                viewId.contains("com.instagram.android:id/clips_post_container") ||
                viewId.contains("com.instagram.android:id/clips_layout") ||
                viewId.contains("com.instagram.android:id/reels_clip_container")) {
                return true
            }

            // Filter Snapchat Spotlight elements precisely
            if (viewId.contains("com.snapchat.android:id/spotlight") ||
                viewId.contains("com.snapchat.android:id/ngs_spotlight") ||
                viewId.contains("com.snapchat.android:id/discover_playback") ||
                viewId.contains("com.snapchat.android:id/neon_spotlight")) {
                return true
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
                        if (isStrictMode) {
                            android.widget.Toast.makeText(applicationContext, "Strict Mode: Access Denied", android.widget.Toast.LENGTH_SHORT).show()
                            exitButton?.performClick()
                        } else {
                            passwordInput?.error = "Incorrect Password"
                        }
                    }
                }

                exitButton?.setOnClickListener {
                    lastBackNavigationTime = System.currentTimeMillis()
                    
                    // Route the user out of the shorts feed forcefully
                    redirectToSafeFeed()
                    
                    // Delay removing the overlay slightly so the user doesn't see the short while the app transitions
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        removeFrictionOverlay()
                    }, 400)
                }

                overlayView?.alpha = 0f
                windowManager.addView(overlayView, layoutParams)
                overlayView?.animate()?.alpha(1f)?.setDuration(250)?.start()
                isOverlayShowing = true
                
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
        var isInsta = false
        var isSnapchat = false
        var targetPackage = "com.google.android.youtube"
        
        try {
            if (lastBlockedPackage.contains("instagram")) {
                isInsta = true
                targetPackage = lastBlockedPackage
            } else if (lastBlockedPackage.contains("snapchat")) {
                isSnapchat = true
                targetPackage = lastBlockedPackage
            } else if (lastBlockedPackage.contains("youtube")) {
                targetPackage = lastBlockedPackage
            } else {
                val root = getTargetAppRootNode()
                if (root != null) {
                    val pkgName = root.packageName?.toString() ?: ""
                    if (pkgName.contains("instagram")) {
                        isInsta = true
                        targetPackage = pkgName
                    } else if (pkgName.contains("snapchat")) {
                        isSnapchat = true
                        targetPackage = pkgName
                    } else if (pkgName.contains("youtube")) {
                        targetPackage = pkgName
                    }
                    root.recycle()
                }
            }
        } catch (e: Exception) {}

        try {
            if (isSnapchat) {
                var clickedChat = false
                try {
                    val root = getTargetAppRootNode()
                    if (root != null) {
                        // First try to click Chat tab via UI node
                        clickedChat = clickNodeByContentDescription(root, listOf("Chat", "Chats", "चैट"))
                        try { root.recycle() } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}

                if (!clickedChat) {
                    try {
                        // Hard fallback: Restart app which goes to Camera/Home
                        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        } else {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    } catch (e: Exception) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }
                return
            }

            // Force the app to open its main web feed. The deep link intent intercepts and forces YouTube 
            // home activity, cleanly escaping the shorts backstack without relying on fragile GLOBAL_ACTION_BACK
            val url = if (isInsta) "https://www.instagram.com/" else "https://www.youtube.com/"
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                setPackage(targetPackage)
                // CLEAR_TOP pops the shorts activity if Home exists below it. SINGLE_TOP resumes it.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Hard fallback if the intent fails for any reason
            performGlobalAction(GLOBAL_ACTION_HOME)
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
