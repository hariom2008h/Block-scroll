package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (isAccessibilityServiceEnabled(context)) {
            // If it's enabled, don't show notification and don't schedule again
            return
        }

        // Show notification
        showNotification(context)

        // Schedule next alarm in 30 seconds
        scheduleNextReminder(context)
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals("${context.packageName}/${ShortsBlockerService::class.java.name}", ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "shorts_blocker_warning",
                "Important Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when the service needs attention."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            settingsIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val warningNotification = NotificationCompat.Builder(context, "shorts_blocker_warning")
            .setContentTitle("⚠️ Accessibility Service is OFF")
            .setContentText("Shorts Blocker requires Accessibility permissions.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Shorts Blocker requires Accessibility permissions to function. Please turn it back on to resume blocking shorts."))
            .setSmallIcon(R.drawable.ic_block)
            .setColor(android.graphics.Color.parseColor("#FF007F"))
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_block, "Turn On in Settings", pendingIntent)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(4041, warningNotification)
    }

    companion object {
        fun scheduleNextReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val triggerAtMillis = System.currentTimeMillis() + 30 * 1000 // 30 seconds
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
        
        fun cancelReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
