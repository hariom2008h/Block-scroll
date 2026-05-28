package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
        
        try {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            sharedPreferences = EncryptedSharedPreferences.create(
                this,
                "shorts_blocker_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        if (!packageName.contains("youtube") && !packageName.contains("instagram")) {
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
                eventSource.recycle()
            }

            // Fallback: check whole visible active window layout hierarchy if the event source did not match
            if (!isAddictiveMedia) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    isAddictiveMedia = checkNodeForShortsOrReels(rootNode)
                    rootNode.recycle()
                }
            }

            if (isAddictiveMedia) {
                showFrictionOverlay()
            }
        }
    }

    private fun checkNodeForShortsOrReels(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

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

        // Traverse hierarchy
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = checkNodeForShortsOrReels(child)
                child.recycle()
                if (found) {
                    return true
                }
            }
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
            layoutParams.dimAmount = 0.85f

            try {
                val inflater = LayoutInflater.from(this)
                overlayView = inflater.inflate(R.layout.overlay_password, null)

                val passwordInput = overlayView?.findViewById<EditText>(R.id.password_input)
                val unlockButton = overlayView?.findViewById<Button>(R.id.unlock_button)

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
