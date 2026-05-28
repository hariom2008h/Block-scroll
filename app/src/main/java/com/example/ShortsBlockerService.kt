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

class ShortsBlockerService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var sharedPreferences: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var lastUnlockTime = 0L
    private val UNLOCK_COOLDOWN_MS = 15000L // 15 seconds of friction-free time before next block

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val packageName = event.packageName?.toString() ?: ""

            // Critical safety feature: If the event is from another app/launcher (not YouTube, not Instagram, and not our app),
            // we must immediately and automatically dismiss the blocking overlay to prevent trapping the user!
            if (packageName.isNotEmpty()) {
                val isTargetApp = packageName.contains("youtube") || packageName.contains("instagram")
                val isOurApp = packageName == this.packageName
                val isSystemApp = packageName == "com.android.systemui" || packageName == "android"
                val isKeyboard = packageName.contains("inputmethod") || packageName.contains("keyboard") || packageName.contains("gboard")

                if (!isTargetApp && !isOurApp && !isSystemApp && !isKeyboard) {
                    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                        removeFrictionOverlay()
                    }
                    return
                } else if (isSystemApp || isKeyboard) {
                    // Ignore system UI and keyboard events, they happen while overlay is active
                    return
                }
            }

            // Only run blocking checks when on YouTube or Instagram
            val isTargetApp = packageName.isNotEmpty() && (packageName.contains("youtube") || packageName.contains("instagram"))
            if (!isTargetApp) {
                return
            }

            // If user recently entered the correct password, do not show lock overlay during cooldown period
            val currentTime = System.currentTimeMillis()
            if (currentTime < lastUnlockTime + UNLOCK_COOLDOWN_MS) {
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Fallback: check whole visible active window layout hierarchy if the event source did not match
                if (!isAddictiveMedia) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        isAddictiveMedia = checkNodeForShortsOrReels(rootNode)
                        try {
                            rootNode.recycle()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (isAddictiveMedia) {
                    showFrictionOverlay()
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
                    removeFrictionOverlay()
                    try {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
