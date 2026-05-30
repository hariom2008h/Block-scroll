package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val sharedPrefs = remember { context.getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE) }
    val haptic = LocalHapticFeedback.current

    var password by remember { mutableStateOf(sharedPrefs.getString("master_password", "I will not waste my time") ?: "") }
    var strictMode by remember { mutableStateOf(sharedPrefs.getBoolean("strict_mode", false)) }
    var lockdownEndTime by remember { mutableStateOf(sharedPrefs.getLong("lockdown_end_time", 0L)) }
    var showLockdownDialog by remember { mutableStateOf(false) }
    val isLockdownActive = System.currentTimeMillis() < lockdownEndTime

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        CenterAlignedTopAppBar(
            title = { Text(text = "Settings", fontWeight = FontWeight.Bold) },
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
            Text(text = "System Access", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val overlayColor by animateColorAsState(if (isOverlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    val overlayIcon = if (isOverlayGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning
                    
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = overlayIcon, contentDescription = null, tint = overlayColor, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Overlay Permission", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = if (isOverlayGranted) "Active" else "Required", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }) { Text(if (isOverlayGranted) "Manage" else "Grant") }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Accessibility Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = "Required to intercept scrolls", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }) { Text("Enable") }
                    }
                }
            }

            Text(text = "Security & Feedback", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Master Password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "This password will be required when an addictive scroll is intercepted. Make it complex.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Custom Motivation/Warning Message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = "Display this message when shorts are blocked.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    var customMessage by remember { mutableStateOf(sharedPrefs.getString("custom_block_message", "") ?: "") }
                    OutlinedTextField(value = customMessage, onValueChange = { customMessage = it }, label = { Text("Message (e.g. Back to work!)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        sharedPrefs.edit().putString("master_password", password).putString("custom_block_message", customMessage).apply()
                        Toast.makeText(context, "Settings Saved securely", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.align(Alignment.End)) { Text("Save Settings") }
                }
            }

            Text(text = "Modes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Strict Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Failed password immediately kicks you back to the safe feed. No second chances.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = strictMode, onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); strictMode = it; sharedPrefs.edit().putBoolean("strict_mode", it).apply() })
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = if (isLockdownActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Lockdown Mode (2 Hours)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (isLockdownActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = if (isLockdownActive) "Active until ${java.text.SimpleDateFormat("hh:mm a").format(java.util.Date(lockdownEndTime))}. Cannot be disabled." else "Prevents disabling Accessibility or uninstalling the app for 2 hours.", style = MaterialTheme.typography.bodyMedium, color = if (isLockdownActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isLockdownActive, enabled = !isLockdownActive, onCheckedChange = { if (it) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showLockdownDialog = true } })
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
                Button(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val newEndTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000L)
                    sharedPrefs.edit().putLong("lockdown_end_time", newEndTime).apply()
                    lockdownEndTime = newEndTime
                    showLockdownDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("ACTIVATE") }
            },
            dismissButton = {
                TextButton(onClick = { showLockdownDialog = false }) { Text("Cancel") }
            }
        )
    }
}
