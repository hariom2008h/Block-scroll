package com.example

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE) }
    
    val blockedCount = sharedPrefs.getInt("shorts_blocked_total", 0)
    val totalTimeSavedMins = blockedCount * 5
    val timeSavedStr = if (totalTimeSavedMins >= 60) {
        "${totalTimeSavedMins / 60}h ${totalTimeSavedMins % 60}m"
    } else {
        "$totalTimeSavedMins mins"
    }
    
    val installTime = sharedPrefs.getLong("app_install_date", System.currentTimeMillis())
    if (!sharedPrefs.contains("app_install_date")) {
        sharedPrefs.edit().putLong("app_install_date", installTime).apply()
    }
    val lastWatchTime = sharedPrefs.getLong("last_shorts_watch_time", 0L)
    val referenceTime = if (lastWatchTime > 0) lastWatchTime else installTime
    val diffMs = System.currentTimeMillis() - referenceTime
    val streakDays = (diffMs / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    val points = streakDays * 50 + blockedCount * 10

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Dashboard", fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Rounded.Lock, contentDescription = "Stats", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "$blockedCount", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(text = "Blocks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    
                    Surface(modifier = Modifier.width(1.dp).height(80.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)) {}
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = "Time Saved", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = timeSavedStr, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(text = "Time Saved", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }

            Text(text = "Rewards & Achievements", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Streak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$streakDays", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(" Days", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Points", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Rounded.Star, contentDescription = "Points", tint = androidx.compose.ui.graphics.Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("$points", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Badges Earned", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
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
        }
    }
}
