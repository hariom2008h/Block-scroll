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
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Share
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
    
    // App Target State
    var blockYoutube by remember { mutableStateOf(sharedPrefs.getBoolean("block_youtube", true)) }
    var blockInstagram by remember { mutableStateOf(sharedPrefs.getBoolean("block_instagram", true)) }
    var blockSnapchat by remember { mutableStateOf(sharedPrefs.getBoolean("block_snapchat", true)) }

    val haptic = LocalHapticFeedback.current
    
    val blockedCount = sharedPrefs.getInt("shorts_blocked_total", 0)
    val totalTimeSavedMins = blockedCount * 5 // 5 minutes saved per block
    val timeSavedStr = if (totalTimeSavedMins >= 60) {
        "${totalTimeSavedMins / 60}h ${totalTimeSavedMins % 60}m"
    } else {
        "$totalTimeSavedMins mins"
    }
    
    // Gamification
    val installTime = sharedPrefs.getLong("app_install_date", System.currentTimeMillis())
    if (!sharedPrefs.contains("app_install_date")) {
        sharedPrefs.edit().putLong("app_install_date", installTime).apply()
    }
    val lastWatchTime = sharedPrefs.getLong("last_shorts_watch_time", 0L)
    val referenceTime = if (lastWatchTime > 0) lastWatchTime else installTime
    val diffMs = System.currentTimeMillis() - referenceTime
    val streakDays = (diffMs / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    val points = streakDays * 50 + blockedCount * 10

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Stats",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$blockedCount",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Blocks",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    
    Surface(
        modifier = Modifier.width(1.dp).height(80.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
    ) {}
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "Time Saved",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(36.dp)
        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = timeSavedStr,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Time Saved",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Gamification Section
            Text(
                text = "Rewards & Achievements",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Streak",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$streakDays",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = " Days",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Total Points",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Star, 
                                    contentDescription = "Points",
                                    tint = androidx.compose.ui.graphics.Color(0xFFFFD700),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$points",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Badges Earned",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (streakDays >= 1 || blockedCount >= 5) {
                            BadgeItem(title = "Starter", icon = Icons.Rounded.PlayArrow)
                        } else {
                            Text("No badges yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        if (streakDays >= 3 || blockedCount >= 20) {
                            BadgeItem(title = "Focused", icon = Icons.Rounded.CheckCircle)
                        }
                        if (streakDays >= 7) {
                            BadgeItem(title = "Master", icon = Icons.Rounded.Star)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { shareStatsImage(context, streakDays, blockedCount, timeSavedStr) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Rounded.Share, contentDescription = "Share")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Progress to WhatsApp")
                    }
                }
            }

            // Section 1: App Targets
            Text(
                text = "Target Apps",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "YouTube Shorts", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = blockYoutube,
                            onCheckedChange = { 
                                blockYoutube = it
                                sharedPrefs.edit().putBoolean("block_youtube", it).apply()
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Instagram Reels", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = blockInstagram,
                            onCheckedChange = { 
                                blockInstagram = it
                                sharedPrefs.edit().putBoolean("block_instagram", it).apply()
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Snapchat Spotlight", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = blockSnapchat,
                            onCheckedChange = { 
                                blockSnapchat = it
                                sharedPrefs.edit().putBoolean("block_snapchat", it).apply()
                            }
                        )
                    }
                }
            }

            // Section 2: Permissions
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

@Composable
fun BadgeItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
    }
}

fun shareStatsImage(context: android.content.Context, streakDays: Int, blockedCount: Int, timeSavedStr: String) {
    val width = 1080
    val height = 1080
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    val h = height.toFloat()
    val w = width.toFloat()
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
    }
    
    val bgColors = intArrayOf(
        android.graphics.Color.parseColor("#4B0082"),
        android.graphics.Color.parseColor("#121212")
    )
    paint.shader = android.graphics.LinearGradient(
        0f, 0f, 0f, h,
        bgColors, null, android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 100f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("SHORTS BLOCKER", w / 2, 200f, paint)
    
    paint.color = android.graphics.Color.CYAN
    paint.textSize = 50f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    canvas.drawText("Reclaiming focus & digital detox!", w / 2, 300f, paint)

    paint.color = android.graphics.Color.parseColor("#33FFFFFF")
    canvas.drawRoundRect(100f, 400f, w - 100f, 950f, 40f, 40f, paint)
    
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 140f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("$streakDays DAYS", w / 2, 550f, paint)
    
    paint.textSize = 50f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    canvas.drawText("Current Focus Streak", w / 2, 630f, paint)
    
    paint.color = android.graphics.Color.YELLOW
    paint.textSize = 60f
    canvas.drawText("$blockedCount Shorts Blocked", w / 2, 750f, paint)
    
    paint.color = android.graphics.Color.parseColor("#A8E6CF")
    paint.textSize = 55f
    canvas.drawText("Time Saved: $timeSavedStr", w / 2, 850f, paint)
    
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = java.io.FileOutputStream("$cachePath/share_stats.png")
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        val imagePath = java.io.File(context.cacheDir, "images/share_stats.png")
        val contentUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imagePath)
        
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(contentUri, "image/png")
            putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
            putExtra(android.content.Intent.EXTRA_TEXT, "See my detox progress! 🚀 Try Shorts Blocker and reclaim your time too! #ShortsBlocker #DigitalDetox")
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share your progress"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}