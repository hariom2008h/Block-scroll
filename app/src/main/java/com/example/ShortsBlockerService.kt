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
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.os.Build
import android.view.KeyEvent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat

class ShortsBlockerService : AccessibilityService() {
    private val NOTIFICATION_ID = 4040

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var sharedPreferences: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var lastUnlockTime = 0L
    
    // Check periodically for cooldown expiry and active shorts
    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            try {
                if (!isOverlayShowing) {
                    val currentTime = System.currentTimeMillis()
                    val sessionDurationMinutes = sharedPreferences?.getInt("session_duration_minutes", 2) ?: 2
                    val sessionCooldownMs = sessionDurationMinutes * 60 * 1000L
                    
                    if (currentTime >= lastUnlockTime + sessionCooldownMs) {
                        // Cooldown expired, check if watching shorts
                        val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                        if (rootNode != null) {
                            val packageName = rootNode.packageName?.toString() ?: ""
                            if (packageName.contains("youtube") || packageName.contains("instagram") || packageName.contains("snapchat")) {
                                val blockYT = sharedPreferences?.getBoolean("block_youtube", true) ?: true
                                val blockIG = sharedPreferences?.getBoolean("block_instagram", true) ?: true
                                val blockSC = sharedPreferences?.getBoolean("block_snapchat", true) ?: true
                                
                                val isAddictiveMedia = checkNodeForShortsOrReels(rootNode, blockYT, blockIG, blockSC, 0)
                                if (isAddictiveMedia) {
                                    val strictModeYT = sharedPreferences?.getBoolean("strict_mode_youtube", false) ?: false
                                    val strictModeIG = sharedPreferences?.getBoolean("strict_mode_instagram", false) ?: false
                                    val strictModeSC = sharedPreferences?.getBoolean("strict_mode_snapchat", false) ?: false
                                    
                                    val isStrictMode = when {
                                        packageName.contains("youtube") -> strictModeYT
                                        packageName.contains("instagram") -> strictModeIG
                                        packageName.contains("snapchat") -> strictModeSC
                                        else -> false
                                    }
                                    
                                    lastBlockedPackage = packageName
                                    
                                    if (isStrictMode) {
                                        lastBackNavigationTime = System.currentTimeMillis()
                                        redirectToSafeFeed()
                                    } else {
                                        showFrictionOverlay()
                                    }
                                }
                            }
                            try {
                                rootNode.recycle()
                            } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mainHandler.postDelayed(this, 1500L) // Check every 1.5 seconds
            }
        }
    }
    
    private var lastBackNavigationTime = 0L
    private val BACK_NAVIGATION_COOLDOWN_MS = 2500L // Small cooldown to prevent loop after pressing Go Back
    
    private var lastBlockedPackage = ""
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "shorts_blocker_channel",
                    "Service Status",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Keeps the blocker service alive."
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, "shorts_blocker_channel")
                .setContentTitle("Shorts Blocker")
                .setContentText("Running in background to block shorts")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        mainHandler.post(periodicCheckRunnable)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        mainHandler.removeCallbacks(periodicCheckRunnable)
        return super.onUnbind(intent)
    }

    private fun requestAudioFocusToPauseMedia() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setOnAudioFocusChangeListener { }
                        .build()
                }
                audioManager?.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    { },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
            
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager?.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun abandonAudioFocusToResumeMedia() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    audioManager?.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus { }
            }
            
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager?.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var lastProcessTime = 0L
    private val THROTTLE_MS = 600L

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
            val sessionDurationMinutes = sharedPreferences?.getInt("session_duration_minutes", 2) ?: 2
            val sessionCooldownMs = sessionDurationMinutes * 60 * 1000L
            
            if (currentTime < lastUnlockTime + sessionCooldownMs) {
                return
            }

            // If user recently pressed "Go Back", wait for UI to exit shorts before checking again
            if (currentTime < lastBackNavigationTime + BACK_NAVIGATION_COOLDOWN_MS) {
                return
            }
            
            // Debounce intensive checks to prevent ANRs which cause "This service is malfunctioning"
            if (currentTime - lastProcessTime < THROTTLE_MS && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                return
            }
            lastProcessTime = currentTime

            val eventType = event.eventType
            // Trigger on state change, scroll, or click. Limit CONTENT_CHANGED aggressively to prevent ANRs.
            if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                
                var isAddictiveMedia = false

                val blockYT = sharedPreferences?.getBoolean("block_youtube", true) ?: true
                val blockIG = sharedPreferences?.getBoolean("block_instagram", true) ?: true
                val blockSC = sharedPreferences?.getBoolean("block_snapchat", true) ?: true

                // Check event source node if available
                val eventSource = event.source
                if (eventSource != null) {
                    isAddictiveMedia = checkNodeForShortsOrReels(eventSource, blockYT, blockIG, blockSC, 0)
                    try {
                        eventSource.recycle()
                    } catch (e: Exception) { /* ignore */ }
                }

                // Fallback: check whole visible active window layout hierarchy if the event source did not match
                if (!isAddictiveMedia && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                    if (rootNode != null) {
                        isAddictiveMedia = checkNodeForShortsOrReels(rootNode, blockYT, blockIG, blockSC, 0)
                        try {
                            rootNode.recycle()
                        } catch (e: Exception) { /* ignore */ }
                    }
                }

                if (isAddictiveMedia) {
                    val strictModeYT = sharedPreferences?.getBoolean("strict_mode_youtube", false) ?: false
                    val strictModeIG = sharedPreferences?.getBoolean("strict_mode_instagram", false) ?: false
                    val strictModeSC = sharedPreferences?.getBoolean("strict_mode_snapchat", false) ?: false
                    
                    val isStrictMode = when {
                        packageName.contains("youtube") -> strictModeYT
                        packageName.contains("instagram") -> strictModeIG
                        packageName.contains("snapchat") -> strictModeSC
                        else -> false
                    }
                    
                    lastBlockedPackage = packageName
                    
                    if (isStrictMode) {
                        lastBackNavigationTime = System.currentTimeMillis()
                        redirectToSafeFeed()
                    } else {
                        showFrictionOverlay()
                    }
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

    private fun checkNodeForShortsOrReels(node: android.view.accessibility.AccessibilityNodeInfo?, blockYT: Boolean, blockIG: Boolean, blockSC: Boolean, depth: Int): Boolean {
        if (node == null || !node.isVisibleToUser) return false
        if (depth > 25) return false

        try {
            val viewId = node.viewIdResourceName ?: ""

            // Ignore bottom navigation bar elements so we don't block normal feeds accidentally
            val isNavElement = viewId.contains("tab") || viewId.contains("nav") || viewId.contains("bottom") || viewId.contains("bar") || viewId.contains("menu") || viewId.contains("icon")

            if (!isNavElement && viewId.isNotEmpty()) {
                // Filter YouTube Shorts player elements precisely
                if (blockYT && (viewId.contains("shorts_player") ||
                    viewId.contains("shorts_video_player") ||
                    viewId.contains("reel_recycler") ||
                    viewId.contains("reel_container") ||
                    viewId.contains("shorts_container") ||
                    viewId.contains("player_view_front_interface") ||
                    viewId.contains("reel_sheet_container") ||
                    viewId.contains("panel_container"))) {
                    return true
                }

                // Filter Instagram Reels elements precisely
                if (blockIG && (viewId.contains("clips_video_container") ||
                    viewId.contains("reels_viewer_pager") ||
                    viewId.contains("clips_viewer_container") ||
                    viewId.contains("reels_video_player_layout") ||
                    viewId.contains("clips_post_container") ||
                    viewId.contains("clips_layout") ||
                    viewId.contains("reels_clip_container") ||
                    viewId.contains("clips_viewer_view_pager"))) {
                    return true
                }

                // Filter Snapchat Spotlight elements precisely
                if (blockSC && (
                    viewId.contains("spotlight_video_container") || 
                    viewId.contains("discover_playback") ||
                    viewId.contains("spotlight_player") ||
                    viewId.contains("spotlight_video") ||
                    viewId.contains("neon_spotlight_play_view") || 
                    viewId.contains("neon_spotlight_playback_view") ||
                    viewId.contains("spotlight_page_content") ||
                    (viewId.contains("spotlight") && !viewId.contains("badge") && !viewId.contains("button"))
                )) {
                    return true
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
                    val found = checkNodeForShortsOrReels(child, blockYT, blockIG, blockSC, depth + 1)
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
                val innerView = inflater.inflate(R.layout.overlay_password, null)
                
                // Wrap in a custom container to reliably intercept the physical back button
                val container = object : android.widget.FrameLayout(this) {
                    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
                            if (event.action == KeyEvent.ACTION_UP) {
                                executeExitAndGoBack()
                            }
                            return true // Consume down and up events
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }
                container.addView(innerView)
                overlayView = container

                val passwordInput = innerView.findViewById<EditText>(R.id.password_input)
                val unlockButton = innerView.findViewById<Button>(R.id.unlock_button)
                val exitButton = innerView.findViewById<Button>(R.id.exit_button)

                unlockButton?.setOnClickListener {
                    val correctPassword = try {
                        sharedPreferences?.getString("master_password", "I will not waste my time") ?: "I will not waste my time"
                    } catch (e: Exception) {
                        "I will not waste my time" // Hard fallback
                    }
                    
                    val enteredPassword = passwordInput?.text.toString()

                    if (enteredPassword == correctPassword) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            unlockButton?.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        } else {
                            unlockButton?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        }

                        lastUnlockTime = System.currentTimeMillis()
                        removeFrictionOverlay()
                    } else {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            unlockButton?.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                        }
                        passwordInput?.error = "Incorrect Password"
                    }
                }

                exitButton?.setOnClickListener {
                    executeExitAndGoBack()
                }

                overlayView?.alpha = 0f
                windowManager.addView(overlayView, layoutParams)
                overlayView?.animate()?.alpha(1f)?.setDuration(250)?.start()
                isOverlayShowing = true
                requestAudioFocusToPauseMedia()

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    overlayView?.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                } else {
                    overlayView?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
                
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
                // For Snapchat, the most reliable way to exit Spotlight is to just trigger the back button.
                // Spotlight is usually opened as a layer over the Camera or from Discover.
                val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
                if (!backSuccess) {
                    var clickedChat = false
                    try {
                        val root = getTargetAppRootNode()
                        if (root != null) {
                            clickedChat = clickNodeByContentDescription(root, listOf("Chat", "Chats", "चैट", "Camera", "SnapMap"))
                            try { root.recycle() } catch (e: Exception) {}
                        }
                    } catch (e: Exception) {}
    
                    if (!clickedChat) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }
                return
            }

            // For YouTube and Instagram: Pressing the Back button naturally closes the Shorts/Reels overlay 
            // and returns to the previous screen the user was on.
            val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
            if (!backSuccess) {
                // If it fails, fallback
                val url = if (isInsta) "https://www.instagram.com/" else "https://www.youtube.com/"
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    setPackage(targetPackage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            // Hard fallback if everything fails
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun executeExitAndGoBack() {
        lastBackNavigationTime = System.currentTimeMillis()
        
        mainHandler.post {
            if (!isOverlayShowing || overlayView == null) {
                redirectToSafeFeed()
                return@post
            }
            try {
                windowManager.removeView(overlayView)
                abandonAudioFocusToResumeMedia()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                overlayView = null
                isOverlayShowing = false
                
                // Now that the overlay is definitely removed from the WindowManager, 
                // the injected back action will reliably reach the underlying app instead of getting trapped by our overlay.
                mainHandler.postDelayed({
                    redirectToSafeFeed()
                }, 200)
            }
        }
    }

    private fun removeFrictionOverlay() {
        mainHandler.post {
            if (!isOverlayShowing || overlayView == null) return@post
            try {
                windowManager.removeView(overlayView)
                abandonAudioFocusToResumeMedia()
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
