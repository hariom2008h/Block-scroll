package com.example

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

object OverlayManager {
    private var isOverlayShowing = false
    private var overlayView: View? = null

    fun showFrictionOverlay(context: Context, packageName: String, currentUsageMs: Long) {
        if (isOverlayShowing) return

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_password, null)
        overlayView = view

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.CENTER

        val unlockBtn = view.findViewById<Button>(R.id.unlock_button)
        val exitBtn = view.findViewById<Button>(R.id.exit_button)
        val etPassword = view.findViewById<EditText>(R.id.password_input)

        exitBtn?.setOnClickListener {
            removeFrictionOverlay(windowManager)
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        }

        // Just let them view the overlay but they have to interact to unlock
        layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()

        unlockBtn?.setOnClickListener {
            val pass = etPassword?.text.toString()
            val prefs = context.getSharedPreferences("BlockerPrefs", Context.MODE_PRIVATE)
            val savedPassword = prefs.getString("unlock_password", "1234")
            
            if (pass == savedPassword) {
                // Update cooldown
                prefs.edit().apply {
                    putLong("last_unlock_$packageName", System.currentTimeMillis())
                    putLong("usage_at_unlock_$packageName", currentUsageMs)
                }.apply()
                
                removeFrictionOverlay(windowManager)
                // Register new cooldown limits in service
                val serviceIntent = Intent(context, AppUsageTrackerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Toast.makeText(context, "Incorrect Password", Toast.LENGTH_SHORT).show()
            }
        }

        try {
            windowManager.addView(view, layoutParams)
            isOverlayShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeFrictionOverlay(windowManager: WindowManager) {
        if (isOverlayShowing && overlayView != null) {
            try {
                windowManager.removeView(overlayView)
                overlayView = null
                isOverlayShowing = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
