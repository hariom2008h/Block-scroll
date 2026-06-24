package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AppUsageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.ACTION_USAGE_LIMIT_REACHED") {
            val packageName = intent.getStringExtra("PACKAGE_NAME") ?: return
            val currentUsage = intent.getLongExtra("CURRENT_USAGE", 0L)
            
            // Show the overlay
            OverlayManager.showFrictionOverlay(context, packageName, currentUsage)
        }
    }
}
