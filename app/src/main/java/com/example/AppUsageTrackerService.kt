package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Calendar

class AppUsageTrackerService : Service() {

    private val NOTIFICATION_ID = 5050
    private val CHANNEL_ID = "AppUsageTrackerChannel"
    private var sharedPreferences: SharedPreferences? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("BlockerPrefs", Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Usage Tracker Active")
            .setContentText("Monitoring app usage limits efficiently.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
        
        startPolling()
        
        return START_STICKY
    }
    
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            while (isActive) {
                if (!powerManager.isInteractive) {
                    delay(30_000L) // Sleep longer if screen is off
                    continue
                }
                
                val sessionMinutes = sharedPreferences?.getInt("session_duration_minutes", 1) ?: 1
                val limitMs = sessionMinutes * 60 * 1000L
                
                val blockYT = sharedPreferences?.getBoolean("block_youtube", true) ?: true
                val blockIG = sharedPreferences?.getBoolean("block_instagram", true) ?: true
                val blockSC = sharedPreferences?.getBoolean("block_snapchat", true) ?: true
                
                val currentTime = System.currentTimeMillis()
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = calendar.timeInMillis
                
                val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, currentTime)
                
                if (blockYT) checkLimit(stats, "com.google.android.youtube", limitMs)
                if (blockIG) checkLimit(stats, "com.instagram.android", limitMs)
                if (blockSC) checkLimit(stats, "com.snapchat.android", limitMs)
                
                delay(10_000L) // Poll every 10 seconds while screen is on
            }
        }
    }
    
    private fun checkLimit(stats: List<android.app.usage.UsageStats>?, packageName: String, limitMs: Long) {
        val packageStat = stats?.find { it.packageName == packageName }
        val currentUsageMs = packageStat?.totalTimeInForeground ?: 0L
        
        // We need to keep track if we already fired for this limit to prevent spamming
        // For simplicity, if currentUsageMs > limitMs, we fire. 
        // We'll store the "last blocked time" in SharedPreferences to implement the cooldown.
        val prefs = sharedPreferences ?: return
        val lastUnlock = prefs.getLong("last_unlock_$packageName", 0L)
        val currentTime = System.currentTimeMillis()
        
        // Cooldown period: once unlocked, give them another `limitMs` before blocking again.
        // We need to know how much usage they had AT unlock time.
        val usageAtUnlock = prefs.getLong("usage_at_unlock_$packageName", 0L)
        
        val effectiveLimit = if (usageAtUnlock > 0L && currentTime - lastUnlock < limitMs * 2) { 
            usageAtUnlock + limitMs 
        } else { 
            limitMs 
        }
        
        if (currentUsageMs > effectiveLimit) {
            val intent = Intent(this, AppUsageReceiver::class.java).apply {
                action = "com.example.ACTION_USAGE_LIMIT_REACHED"
                putExtra("PACKAGE_NAME", packageName)
                putExtra("CURRENT_USAGE", currentUsageMs)
            }
            sendBroadcast(intent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Usage Tracker",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            if (manager != null) {
                manager.createNotificationChannel(channel)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
