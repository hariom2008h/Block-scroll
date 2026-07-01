package com.example

import kotlinx.coroutines.*
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
import android.widget.TextView
import android.widget.Toast
import android.view.Gravity

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
import android.os.PowerManager

class ShortsBlockerService : AccessibilityService() {
    private val NOTIFICATION_ID = 4040

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var sharedPreferences: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var lastUnlockTime = 0L
    private var lastShortsActivityTime = 0L
    
    private var checkJob: Job? = null
    private val isChecking = java.util.concurrent.atomic.AtomicBoolean(false)
    
    private var lastReliefUpdateTime = 0L

    private fun handleReliefTime(currentTime: Long, packageName: String): Boolean {
        val dailyReliefMinutes = sharedPreferences?.getInt("daily_relief_minutes", 0) ?: 0
        if (dailyReliefMinutes <= 0) return false
        
        val allowYt = sharedPreferences?.getBoolean("allowance_youtube", true) ?: true
        val allowIg = sharedPreferences?.getBoolean("allowance_instagram", true) ?: true
        val allowSnap = sharedPreferences?.getBoolean("allowance_snapchat", true) ?: true
        
        val isAppAllowedForRelief = when {
            packageName.contains("youtube") -> allowYt
            packageName.contains("instagram") -> allowIg
            packageName.contains("snapchat") -> allowSnap
            else -> false
        }
        
        if (!isAppAllowedForRelief) {
            lastReliefUpdateTime = 0L
            return false
        }
        
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val today = sdf.format(java.util.Date(currentTime))
        
        var lastDate = sharedPreferences?.getString("last_relief_date", "") ?: ""
        var usedReliefMs = sharedPreferences?.getLong("used_relief_ms", 0L) ?: 0L
        
        if (today != lastDate) {
            usedReliefMs = 0L
            lastDate = today
        }
        
        val delta = if (lastReliefUpdateTime > 0 && currentTime - lastReliefUpdateTime < 5000L) {
            currentTime - lastReliefUpdateTime
        } else {
            0L
        }
        lastReliefUpdateTime = currentTime
        
        if (delta > 0) {
            usedReliefMs += delta
            sharedPreferences?.edit()?.apply {
                putString("last_relief_date", lastDate)
                putLong("used_relief_ms", usedReliefMs)
                apply()
            }
        } else if (lastDate != sharedPreferences?.getString("last_relief_date", "")) {
            sharedPreferences?.edit()?.putString("last_relief_date", lastDate)?.putLong("used_relief_ms", 0L)?.apply()
        }
        
        val totalReliefMs = dailyReliefMinutes * 60 * 1000L
        return usedReliefMs < totalReliefMs
    }

    private fun startPeriodicCheck() {
        checkJob?.cancel()
        checkJob = serviceScope.launch {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            while(isActive) {
                try {
                    if (!powerManager.isInteractive) {
                        removeProgressOverlay()
                        delay(5000L) // Sleep longer if screen is off
                        continue
                    }
                    if (!isOverlayShowing) {
                        val currentTime = System.currentTimeMillis()
                        val sessionDurationMinutes = sharedPreferences?.getInt("session_duration_minutes", 2) ?: 2
                        val sessionCooldownMs = sessionDurationMinutes * 60 * 1000L
                        
                        // Use atomic compareAndSet to avoid stalling the loop if onAccessibilityEvent is checking
                        if (isChecking.compareAndSet(false, true)) {
                            var isWatchingShorts = false
                            try {
                                val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                                if (rootNode != null) {
                                    val packageName = rootNode.packageName?.toString() ?: ""
                                    if (packageName.contains("youtube") || packageName.contains("instagram") || packageName.contains("snapchat")) {
                                        val blockYT = sharedPreferences?.getBoolean("block_youtube", true) ?: true
                                        val blockIG = sharedPreferences?.getBoolean("block_instagram", true) ?: true
                                        val blockSC = sharedPreferences?.getBoolean("block_snapchat", true) ?: true
                                        isWatchingShorts = checkNodeForShortsOrReels(rootNode, blockYT, blockIG, blockSC, 0, IntArray(1) { 0 })
                                        if (isWatchingShorts) {
                                            val isReliefActive = handleReliefTime(currentTime, packageName)
                                            lastShortsActivityTime = currentTime
                                            if (!isReliefActive && currentTime >= lastUnlockTime + sessionCooldownMs) {
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
                                                    mainHandler.post { redirectToSafeFeed() }
                                                } else {
                                                    showFrictionOverlay()
                                                }
                                            }
                                        }
                                    }
                                    try {
                                        rootNode.recycle()
                                    } catch (e: Exception) {}
                                }
                            } finally {
                                isChecking.set(false)
                            }
                            
                            // Treat as watching if detected within last 3 seconds
                            val effectivelyWatching = isWatchingShorts || (currentTime - lastShortsActivityTime < 3000L)
                            
                            val enableFloatingTimer = sharedPreferences?.getBoolean("enable_floating_timer", false) ?: false
                            
                            if (enableFloatingTimer && effectivelyWatching && currentTime < lastUnlockTime + sessionCooldownMs) {
                                showOrUpdateProgressOverlay(lastUnlockTime + sessionCooldownMs - currentTime)
                            } else {
                                removeProgressOverlay()
                            }
                        }
                    } else {
                        removeProgressOverlay()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // Increase polling interval to save battery, as onAccessibilityEvent covers state changes
                delay(2000L) 
            }
        }
    }
    
    private var progressOverlayView: View? = null
    private var isProgressOverlayShowing = false

    private fun showOrUpdateProgressOverlay(timeLeftMs: Long) {
        if (!Settings.canDrawOverlays(this)) return

        val sessionDurationMinutes = sharedPreferences?.getInt("session_duration_minutes", 2) ?: 2
        val sessionCooldownMs = sessionDurationMinutes * 60 * 1000L

        mainHandler.post {
            if (progressOverlayView == null) {
                val context = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault)
                val container = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundColor(android.graphics.Color.parseColor("#99000000")) // Semi-transparent black
                    setPadding(24, 16, 24, 16)
                    
                    val shape = android.graphics.drawable.GradientDrawable()
                    shape.cornerRadius = 20f
                    shape.setColor(android.graphics.Color.parseColor("#99000000"))
                    background = shape
                }
                
                val text = TextView(context).apply {
                    tag = "progress_text"
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, 8)
                }
                
                val progressBar = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    tag = "progress_bar"
                    isIndeterminate = false
                    max = sessionCooldownMs.toInt()
                    progress = timeLeftMs.toInt()
                    layoutParams = android.widget.LinearLayout.LayoutParams(200, 10)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
                    }
                }
                
                container.addView(text)
                container.addView(progressBar)
                progressOverlayView = container
                
                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 32
                    y = 120 // Adjust to not overlap with status bar
                }
                
                try {
                    windowManager.addView(progressOverlayView, layoutParams)
                    isProgressOverlayShowing = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Update existing view
            progressOverlayView?.let { container ->
                val text = container.findViewWithTag<TextView>("progress_text")
                val bar = container.findViewWithTag<android.widget.ProgressBar>("progress_bar")
                
                val secondsLeft = timeLeftMs / 1000
                val min = secondsLeft / 60
                val sec = secondsLeft % 60
                text?.text = String.format("Block in %02d:%02d", min, sec)
                
                bar?.max = sessionCooldownMs.toInt()
                bar?.progress = timeLeftMs.toInt()
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (timeLeftMs < 10000) {
                        bar?.progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336")) // Red if < 10s
                    } else {
                        bar?.progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")) // Green
                    }
                }
            }
        }
    }

    private fun removeProgressOverlay() {
        mainHandler.post {
            if (isProgressOverlayShowing && progressOverlayView != null) {
                try {
                    windowManager.removeView(progressOverlayView)
                } catch (e: Exception) {}
                progressOverlayView = null
                isProgressOverlayShowing = false
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
        ReminderReceiver.cancelReminder(this)
        try {
            val notificationManagerCompat = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManagerCompat.cancel(4041)
            
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
                .setSmallIcon(R.drawable.ic_block)
                .setColor(android.graphics.Color.parseColor("#FF007F"))
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
        
        startPeriodicCheck()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        checkJob?.cancel()
        ReminderReceiver.scheduleNextReminder(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        checkJob?.cancel()
        serviceScope.cancel()
        removeFrictionOverlay()
        removeProgressOverlay()
    }

    private fun requestAudioFocusToPauseMedia() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setOnAudioFocusChangeListener { }
                        .setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                }
                audioManager?.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    { },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            
            // Dispatch Pause
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager?.dispatchMediaKeyEvent(upEvent)
            
            // Dispatch Stop as a fallback
            val downStop = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP)
            audioManager?.dispatchMediaKeyEvent(downStop)
            val upStop = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP)
            audioManager?.dispatchMediaKeyEvent(upStop)

            // Temporarily mute to assure the user feels it's stopped
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
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
            
            // Dispatch Play
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager?.dispatchMediaKeyEvent(upEvent)

            // Unmute
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
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
            val eventType = event.eventType

            val paymentApps = setOf(
                "com.google.android.apps.nbu.paisa.user", // GPay
                "net.one97.paytm", // Paytm
                "com.phonepe.app", // PhonePe
                "in.org.npci.upiapp", // BHIM
                "com.naviapp", // Navi
                "com.dreamplug.androidapp", // CRED
                "in.amazon.mShop.android.shopping", // Amazon (Amazon Pay)
                "com.mobikwik_new", // MobiKwik
                "com.freecharge.android" // Freecharge
            )
            
            if (paymentApps.contains(packageName)) {
                val pauseForPayments = sharedPreferences?.getBoolean("pause_for_payments", true) ?: true
                if (pauseForPayments) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        disableSelf()
                    }
                    return
                }
            }

            val isSystemApp = packageName == "com.android.systemui" || packageName == "android"
            val isKeyboard = packageName.contains("inputmethod") || packageName.contains("keyboard") || packageName.contains("gboard")

            if (isSystemApp || isKeyboard) {
                return
            }

            // Quick throttling before doing heavy IPC like rootInActiveWindow
            val currentTime = System.currentTimeMillis()
            val isUrgentEvent = (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED)
            val isThrottled = (currentTime - lastProcessTime < 400L)

            if (!isUrgentEvent && isThrottled) {
                return
            }
            lastProcessTime = currentTime

            // Trigger on state change, scroll, or click.
            if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                
                val blockYT = sharedPreferences?.getBoolean("block_youtube", true) ?: true
                val blockIG = sharedPreferences?.getBoolean("block_instagram", true) ?: true
                val blockSC = sharedPreferences?.getBoolean("block_snapchat", true) ?: true

                // Handle layout traversal on a background thread to prevent ANRs.
                if (!isChecking.compareAndSet(false, true)) {
                    return
                }
                serviceScope.launch {
                    try {
                        // 1. Check Active Window Package to detect if user left YouTube/Instagram completely
                        val activeRoot = try { rootInActiveWindow } catch (e: Exception) { null }
                        if (activeRoot != null) {
                            val activePackage = activeRoot.packageName?.toString() ?: ""
                            
                            if (activePackage.isNotEmpty()) {
                                val isTargetActive = activePackage.contains("youtube") || activePackage.contains("instagram") || activePackage.contains("snapchat")
                                val isOurAppActive = activePackage == this@ShortsBlockerService.packageName
                                val isSystemRoot = activePackage == "com.android.systemui" || activePackage == "android"
                                val isKeyboardRoot = activePackage.contains("inputmethod") || activePackage.contains("keyboard") || activePackage.contains("gboard")

                                // If user is in a completely different app, remove overlay safely
                                if (!isTargetActive && !isOurAppActive && !isSystemRoot && !isKeyboardRoot) {
                                    if (isOverlayShowing) {
                                        mainHandler.post { removeFrictionOverlay() }
                                    }
                                    try { activeRoot.recycle() } catch (e: Exception) {}
                                    return@launch
                                }
                            }
                        }

                        // Filter accessibility events only for our targets
                        val isTargetEvent = packageName.isNotEmpty() && (packageName.contains("youtube") || packageName.contains("instagram") || packageName.contains("snapchat"))

                        if (!isTargetEvent) {
                            try { activeRoot?.recycle() } catch (e: Exception) {}
                            return@launch
                        }

                        var isAddictiveMedia = false

                        // Check whole visible active window layout hierarchy
                        if (activeRoot != null) {
                            isAddictiveMedia = checkNodeForShortsOrReels(activeRoot, blockYT, blockIG, blockSC, 0, IntArray(1) { 0 })
                            try {
                                activeRoot.recycle()
                            } catch (e: Exception) { /* ignore */ }
                        } else {
                            val backupRoot = try { rootInActiveWindow } catch (e: Exception) { null }
                            if (backupRoot != null) {
                                isAddictiveMedia = checkNodeForShortsOrReels(backupRoot, blockYT, blockIG, blockSC, 0, IntArray(1) { 0 })
                                try { backupRoot.recycle() } catch (e: Exception) {}
                            }
                        }

                        if (isAddictiveMedia) {
                            val currentTimeMs = System.currentTimeMillis()
                            val isReliefActive = handleReliefTime(currentTimeMs, packageName)
                            lastShortsActivityTime = currentTimeMs
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
                            
                            val sessionDurationMinutes = sharedPreferences?.getInt("session_duration_minutes", 2) ?: 2
                            val sessionCooldownMs = sessionDurationMinutes * 60 * 1000L
                            
                            if (!isReliefActive && currentTimeMs >= lastUnlockTime + sessionCooldownMs) {
                                if (currentTimeMs < lastBackNavigationTime + BACK_NAVIGATION_COOLDOWN_MS) {
                                    return@launch
                                }
                                
                                if (isStrictMode) {
                                    lastBackNavigationTime = currentTimeMs
                                    mainHandler.post { redirectToSafeFeed() }
                                } else {
                                    showFrictionOverlay()
                                }
                            }
                        } else if (isOverlayShowing && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                            // Only remove if it's a WINDOW_STATE_CHANGED (e.g. going back to the home feed)
                            // We don't do this on CONTENT_CHANGED to prevent flickering while shorts is still loading.
                            removeFrictionOverlay()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isChecking.set(false)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkNodeForShortsOrReels(node: android.view.accessibility.AccessibilityNodeInfo?, blockYT: Boolean, blockIG: Boolean, blockSC: Boolean, depth: Int, nodeCount: IntArray): Boolean {
        if (node == null || !node.isVisibleToUser) return false
        if (depth > 40 || nodeCount[0] > 1000) return false // Max 40 depth, 1000 nodes
        nodeCount[0]++

        try {
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

            if (viewId.isNotEmpty()) {
                // Ignore Memories and Camera screens so they are never blocked
                if (viewId.contains("memories") || viewId.contains("gallery") || viewId.contains("camera")) {
                    return false
                }
            }

            // Ignore bottom navigation bar elements so we don't block normal feeds accidentally
            val isNavElement = viewId.contains("tab") || viewId.contains("nav") || viewId.contains("bottom") || viewId.contains("bar") || viewId.contains("menu") || viewId.contains("icon") || viewId.contains("badge") || viewId.contains("button")

            if (!isNavElement && viewId.isNotEmpty()) {
                // Filter YouTube Shorts player elements precisely
                if (blockYT && (
                    viewId == "com.google.android.youtube:id/reel_recycler" ||
                    viewId == "com.google.android.youtube:id/shorts_player" ||
                    viewId == "com.google.android.youtube:id/shorts_video_player" ||
                    viewId == "com.google.android.youtube:id/reel_video_player" ||
                    viewId.endsWith(":id/reel_recycler") ||
                    (viewId.contains("shorts_") && viewId.contains("player")) ||
                    (viewId.contains("reel_") && viewId.contains("player"))
                )) {
                    return true
                }

                // Filter Instagram Reels elements precisely
                if (blockIG && (
                    viewId.contains("clips_video") ||
                    viewId.contains("reels_viewer") ||
                    viewId.contains("clips_viewer") ||
                    viewId.contains("reels_video") ||
                    viewId.contains("clips_post") ||
                    viewId.contains("clips_layout") ||
                    viewId.contains("reels_clip") ||
                    viewId.contains("clips_item") ||
                    viewId.contains("layout_clips_viewer") ||
                    viewId.endsWith(":id/reels_viewer_root") ||
                    viewId.endsWith(":id/clips_viewer_root") ||
                    viewId.contains("reels_video_player_layout")
                )) {
                    return true
                }

                // Filter Snapchat Spotlight elements precisely
                if (blockSC && viewId.contains("spotlight")) {
                    return true
                }
            }
            
            // Fallback contentDescription checks for obfuscated UI
            if (contentDesc.isNotEmpty() && !isNavElement) {
                val packageName = node.packageName?.toString()?.lowercase() ?: ""
                if (blockIG && packageName.contains("instagram") && (contentDesc.contains("reels") || contentDesc.contains("clips")) && !contentDesc.contains("tab") && !contentDesc.contains("button") && !contentDesc.contains("tray")) {
                    return true
                }
                if (blockSC && packageName.contains("snapchat") && contentDesc.contains("spotlight") && !contentDesc.contains("tab") && !contentDesc.contains("button")) {
                    return true
                }
            }

            // Traverse hierarchy safely
            val childCount = node.childCount
            var foundShorts = false
            for (i in 0 until childCount) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                }
                if (child != null) {
                    val result = checkNodeForShortsOrReels(child, blockYT, blockIG, blockSC, depth + 1, nodeCount)
                    try {
                        child.recycle()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (result) {
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
                        val bypassCount = sharedPreferences?.getInt("bypass_count", 0) ?: 0
                        sharedPreferences?.edit()?.putInt("bypass_count", bypassCount + 1)?.apply()
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
                
                try {
                    val intent = Intent(this, PauseBackgroundActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

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

    private fun stopPauseActivity() {
        try {
            sendBroadcast(Intent("com.example.FINISH_PAUSE_ACTIVITY").apply {
                setPackage(packageName)
            })
        } catch (e: Exception) {
            e.printStackTrace()
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
                stopPauseActivity()
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
                stopPauseActivity()
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
}
