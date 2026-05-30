package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
          ShortsBlockerSettingsScreen(modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsBlockerSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    
    val sharedPrefs = remember {
        context.getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
    }

    var password by remember { 
        mutableStateOf(sharedPrefs.getString("master_password", "I will not waste my time") ?: "") 
    }
    
    var strictMode by remember {
        mutableStateOf(sharedPrefs.getBoolean("strict_mode", false))
    }

    var lockdownEndTime by remember {
        mutableStateOf(sharedPrefs.getLong("lockdown_end_time", 0L))
    }
    var showLockdownDialog by remember { mutableStateOf(false) }
    val isLockdownActive = System.currentTimeMillis() < lockdownEndTime
    
    // Limits State
    var scheduleEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("schedule_enabled", false)) }
    var scheduleStartHour by remember { mutableStateOf(sharedPrefs.getInt("schedule_start_hour", 9)) }
    var scheduleEndHour by remember { mutableStateOf(sharedPrefs.getInt("schedule_end_hour", 17)) }
    
    var quotaEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("quota_enabled", false)) }
    var quotaLimitMins by remember { mutableStateOf(sharedPrefs.getLong("quota_limit_ms", 15 * 60 * 1000L) / (60 * 1000L)) }
    // Fetch used quota today
    var quotaUsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = sharedPrefs.getString("quota_last_date", "")
        quotaUsedMs = if (dateStr == lastDate) sharedPrefs.getLong("quota_used_ms", 0L) else 0L
    }
    
    val haptic = LocalHapticFeedback.current
    
    val blockedCount = sharedPrefs.getInt("shorts_blocked_total", 0)


    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "Shorts Blocker",
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // Section 0: Stats
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Stats",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$blockedCount",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Distractions Blocked",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Section 1: Permissions
            Text(
                text = "System Access",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val overlayColor by animateColorAsState(if (isOverlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    val overlayIcon = if (isOverlayGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = overlayIcon,
                            contentDescription = null,
                            tint = overlayColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlay Permission",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isOverlayGranted) "Active" else "Required",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                        ) {
                            Text(if (isOverlayGranted) "Manage" else "Grant")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accessibility Service",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Required to intercept scrolls",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }



            // Section 2: Security & Feedback
            Text(
                text = "Security & Feedback",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Master Password",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "This password will be required when an addictive scroll is intercepted. Make it complex.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Custom Motivation/Warning Message",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Display this message when shorts are blocked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    var customMessage by remember { mutableStateOf(sharedPrefs.getString("custom_block_message", "") ?: "") }
                    
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        label = { Text("Message (e.g. Back to work!)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            sharedPrefs.edit()
                                .putString("master_password", password)
                                .putString("custom_block_message", customMessage)
                                .apply()
                            Toast.makeText(context, "Settings Saved securely", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Settings")
                    }
                }
            }

            // Section 3: Scheduling & Limits
            Text(
                text = "Scheduling & Limits",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Focus Schedule
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Focus Schedule",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Block shorts only during specific hours",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = scheduleEnabled,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scheduleEnabled = it
                                sharedPrefs.edit().putBoolean("schedule_enabled", it).apply()
                            }
                        )
                    }

                    if (scheduleEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Start Time", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = scheduleStartHour.toFloat(),
                                    onValueChange = { 
                                        scheduleStartHour = it.toInt() 
                                        sharedPrefs.edit().putInt("schedule_start_hour", scheduleStartHour).apply()
                                    },
                                    valueRange = 0f..23f,
                                    steps = 22,
                                    modifier = Modifier.width(120.dp)
                                )
                                Text("${String.format("%02d", scheduleStartHour)}:00")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("End Time", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = scheduleEndHour.toFloat(),
                                    onValueChange = { 
                                        scheduleEndHour = it.toInt() 
                                        sharedPrefs.edit().putInt("schedule_end_hour", scheduleEndHour).apply()
                                    },
                                    valueRange = 0f..23f,
                                    steps = 22,
                                    modifier = Modifier.width(120.dp)
                                )
                                Text("${String.format("%02d", scheduleEndHour)}:00")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    // Daily Quota
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily Quota",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Limit daily shorts time today: ${quotaUsedMs / 60000} mins used",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = quotaEnabled,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                quotaEnabled = it
                                sharedPrefs.edit().putBoolean("quota_enabled", it).apply()
                            }
                        )
                    }

                    if (quotaEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Limit: $quotaLimitMins minutes", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = quotaLimitMins.toFloat(),
                                onValueChange = { 
                                    quotaLimitMins = it.toLong()
                                    sharedPrefs.edit().putLong("quota_limit_ms", quotaLimitMins * 60 * 1000L).apply()
                                },
                                valueRange = 5f..120f,
                                steps = 22,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Section 4: Strict Mode
            Text(
                text = "Modes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Strict Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Failed password immediately kicks you back to the safe feed. No second chances.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = strictMode,
                        onCheckedChange = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            strictMode = it 
                            sharedPrefs.edit().putBoolean("strict_mode", it).apply()
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isLockdownActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lockdown Mode (2 Hours)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLockdownActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isLockdownActive) "Active until ${java.text.SimpleDateFormat("hh:mm a").format(java.util.Date(lockdownEndTime))}. Cannot be disabled." else "Prevents disabling Accessibility or uninstalling the app for 2 hours.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isLockdownActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isLockdownActive,
                        enabled = !isLockdownActive,
                        onCheckedChange = { 
                            if (it) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showLockdownDialog = true 
                            }
                        }
                    )
                }
            }
        }
    }

    if (showLockdownDialog) {
        AlertDialog(
            onDismissRequest = { showLockdownDialog = false },
            title = { Text("Activate Lockdown Mode?") },
            text = { Text("This will prevent you from uninstalling the app or disabling the Accessibility service for the next 2 hours. ARE YOU SURE?") },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val newEndTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000L) // 2 hours
                        sharedPrefs.edit().putLong("lockdown_end_time", newEndTime).apply()
                        lockdownEndTime = newEndTime
                        showLockdownDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("ACTIVATE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockdownDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

