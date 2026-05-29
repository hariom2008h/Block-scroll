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
                    val isTargetActive = activePackage.contains("youtube") || activePackage.contains("instagram")
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
            val isTargetEvent = packageName.isNotEmpty() && (packageName.contains("youtube") || packageName.contains("instagram"))
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
                    
                    val enteredPassword = passwordInput?.text.toString()

                    if (enteredPassword == correctPassword) {
                        lastUnlockTime = System.currentTimeMillis()
                        removeFrictionOverlay()
                    } else {
                        passwordInput?.error = "Incorrect Password"
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
                        if (pkgName.contains("youtube") || pkgName.contains("instagram")) {
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

    private fun redirectToSafeFeed() {
        var isInsta = false
        var targetPackage = "com.google.android.youtube"
        
        try {
            val root = getTargetAppRootNode()
            if (root != null) {
                val pkgName = root.packageName?.toString() ?: ""
                if (pkgName.contains("instagram")) {
                    isInsta = true
                    targetPackage = pkgName
                } else if (pkgName.contains("youtube")) {
                    targetPackage = pkgName
                }
                root.recycle()
            }
        } catch (e: Exception) {}

        try {
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
