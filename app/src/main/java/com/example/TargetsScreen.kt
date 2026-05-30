package com.example

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE) }
    val haptic = LocalHapticFeedback.current

    var blockYoutube by remember { mutableStateOf(sharedPrefs.getBoolean("block_youtube", true)) }
    var blockInstagram by remember { mutableStateOf(sharedPrefs.getBoolean("block_instagram", true)) }
    var blockSnapchat by remember { mutableStateOf(sharedPrefs.getBoolean("block_snapchat", true)) }

    var scheduleEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("schedule_enabled", false)) }
    var scheduleStartHour by remember { mutableStateOf(sharedPrefs.getInt("schedule_start_hour", 9)) }
    var scheduleEndHour by remember { mutableStateOf(sharedPrefs.getInt("schedule_end_hour", 17)) }

    var quotaEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("quota_enabled", false)) }
    var quotaLimitMins by remember { mutableStateOf(sharedPrefs.getLong("quota_limit_ms", 15 * 60 * 1000L) / (60 * 1000L)) }
    var quotaUsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = sharedPrefs.getString("quota_last_date", "")
        quotaUsedMs = if (dateStr == lastDate) sharedPrefs.getLong("quota_used_ms", 0L) else 0L
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CenterAlignedTopAppBar(
            title = { Text(text = "Targets & Limits", fontWeight = FontWeight.Bold) },
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
            Text(text = "Target Apps", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "YouTube Shorts", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = blockYoutube, onCheckedChange = { blockYoutube = it; sharedPrefs.edit().putBoolean("block_youtube", it).apply() })
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Instagram Reels", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = blockInstagram, onCheckedChange = { blockInstagram = it; sharedPrefs.edit().putBoolean("block_instagram", it).apply() })
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Snapchat Spotlight", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = blockSnapchat, onCheckedChange = { blockSnapchat = it; sharedPrefs.edit().putBoolean("block_snapchat", it).apply() })
                    }
                }
            }

            Text(text = "Scheduling & Limits", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Focus Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = "Block shorts only during specific hours", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = scheduleEnabled, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); scheduleEnabled = it; sharedPrefs.edit().putBoolean("schedule_enabled", it).apply() })
                    }

                    if (scheduleEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Start Time", style = MaterialTheme.typography.labelMedium)
                                Slider(value = scheduleStartHour.toFloat(), onValueChange = { scheduleStartHour = it.toInt(); sharedPrefs.edit().putInt("schedule_start_hour", scheduleStartHour).apply() }, valueRange = 0f..23f, steps = 22, modifier = Modifier.width(120.dp))
                                Text("${String.format("%02d", scheduleStartHour)}:00")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("End Time", style = MaterialTheme.typography.labelMedium)
                                Slider(value = scheduleEndHour.toFloat(), onValueChange = { scheduleEndHour = it.toInt(); sharedPrefs.edit().putInt("schedule_end_hour", scheduleEndHour).apply() }, valueRange = 0f..23f, steps = 22, modifier = Modifier.width(120.dp))
                                Text("${String.format("%02d", scheduleEndHour)}:00")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Daily Quota", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = "Limit daily shorts time today: ${quotaUsedMs / 60000} mins used", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = quotaEnabled, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); quotaEnabled = it; sharedPrefs.edit().putBoolean("quota_enabled", it).apply() })
                    }

                    if (quotaEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Limit: $quotaLimitMins minutes", style = MaterialTheme.typography.labelMedium)
                            Slider(value = quotaLimitMins.toFloat(), onValueChange = { quotaLimitMins = it.toLong(); sharedPrefs.edit().putLong("quota_limit_ms", quotaLimitMins * 60 * 1000L).apply() }, valueRange = 5f..120f, steps = 22, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
