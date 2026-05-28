package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.PixelFormat
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Step A & B: Trigger overlay on UI scroll (TYPE_VIEW_SCROLLED)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Step C: Trigger custom password screen if within target applications
            if (packageName == "com.google.android.youtube" || packageName == "com.instagram.android") {
                showFrictionOverlay()
            }
        }
    }

    private fun showFrictionOverlay() {
        if (isOverlayShowing) return

        // Step C: Use WindowManager.addView() with TYPE_APPLICATION_OVERLAY
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        // Prevent taking focus initially if we don't want the keyboard popping up arbitrarily,
        // but since we want them to type the password, we shouldn't use FLAG_NOT_FOCUSABLE.
        layoutParams.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
        layoutParams.dimAmount = 0.7f

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_password, null)

        val passwordInput = overlayView?.findViewById<EditText>(R.id.password_input)
        val unlockButton = overlayView?.findViewById<Button>(R.id.unlock_button)

        unlockButton?.setOnClickListener {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                this,
                "shorts_blocker_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val correctPassword = sharedPreferences.getString("master_password", "I will not waste my time")
            val enteredPassword = passwordInput?.text.toString()

            // Step E: Verify and removeView()
            if (enteredPassword == correctPassword) {
                removeFrictionOverlay()
            } else {
                passwordInput?.error = "Incorrect Password"
            }
        }

        try {
            overlayView?.alpha = 0f
            windowManager.addView(overlayView, layoutParams)
            overlayView?.animate()?.alpha(1f)?.setDuration(300)?.start()
            isOverlayShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFrictionOverlay() {
        if (!isOverlayShowing || overlayView == null) return
        try {
            windowManager.removeView(overlayView)
            isOverlayShowing = false
            overlayView = null
        } catch (e: Exception) {
            e.printStackTrace()
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
