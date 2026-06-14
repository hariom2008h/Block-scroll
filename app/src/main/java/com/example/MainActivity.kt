package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.material.icons.rounded.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    var showSystemAccessDialog by remember { mutableStateOf(false) }
    
    var strictModeYoutube by remember { mutableStateOf(sharedPrefs.getBoolean("strict_mode_youtube", false)) }
    var strictModeInstagram by remember { mutableStateOf(sharedPrefs.getBoolean("strict_mode_instagram", false)) }
    var strictModeSnapchat by remember { mutableStateOf(sharedPrefs.getBoolean("strict_mode_snapchat", false)) }
    var blockYoutube by remember {
        mutableStateOf(sharedPrefs.getBoolean("block_youtube", true))
    }
    var blockInstagram by remember {
        mutableStateOf(sharedPrefs.getBoolean("block_instagram", true))
    }
    var blockSnapchat by remember {
        mutableStateOf(sharedPrefs.getBoolean("block_snapchat", true))
    }
    var hideLauncherIcon by remember {
        mutableStateOf(sharedPrefs.getBoolean("hide_launcher_icon", false))
    }

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
            actions = {
                IconButton(onClick = { showSystemAccessDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "System Access Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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
            
            // Section 2: Security
            Text(
                text = "Security",
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

                    Spacer(modifier = Modifier.height(12.dp))

                    val hasMinLength = password.length >= 6
                    val hasUpper = password.any { it.isUpperCase() }
                    val hasLower = password.any { it.isLowerCase() }
                    val hasDigit = password.any { it.isDigit() }
                    val hasSpecial = password.any { !it.isLetterOrDigit() }
                    val hasNoRepeats = password.isNotEmpty() && password.toSet().size == password.length
                    val isValidPassword = hasMinLength && hasUpper && hasLower && hasDigit && hasSpecial && hasNoRepeats

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PasswordCriterion(text = "At least 6 characters", met = hasMinLength)
                        PasswordCriterion(text = "Contains uppercase letter", met = hasUpper)
                        PasswordCriterion(text = "Contains lowercase letter", met = hasLower)
                        PasswordCriterion(text = "Contains a number", met = hasDigit)
                        PasswordCriterion(text = "Contains a special symbol", met = hasSpecial)
                        PasswordCriterion(text = "No repeated characters", met = hasNoRepeats)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            sharedPrefs.edit().putString("master_password", password).apply()
                            Toast.makeText(context, "Password Saved securely", Toast.LENGTH_SHORT).show()
                        },
                        enabled = isValidPassword,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Credentials")
                    }
                }
            }
        }

        if (showSystemAccessDialog) {
            AlertDialog(
                onDismissRequest = { showSystemAccessDialog = false },
                title = { Text("Settings") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("System Access", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        
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
                                    style = MaterialTheme.typography.bodySmall,
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

                        Spacer(modifier = Modifier.height(12.dp))

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
                                    text = "Accessibility",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "To intercept scrolls",
                                    style = MaterialTheme.typography.bodySmall,
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

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        Text("Target Apps Configuration", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        AppFilterItem(
                            appName = "YouTube Shorts",
                            isBlocked = blockYoutube,
                            onBlockChange = { 
                                blockYoutube = it 
                                sharedPrefs.edit().putBoolean("block_youtube", it).apply()
                            },
                            isStrict = strictModeYoutube,
                            onStrictChange = { 
                                strictModeYoutube = it 
                                sharedPrefs.edit().putBoolean("strict_mode_youtube", it).apply()
                            }
                        )

                        AppFilterItem(
                            appName = "Instagram Reels",
                            isBlocked = blockInstagram,
                            onBlockChange = { 
                                blockInstagram = it 
                                sharedPrefs.edit().putBoolean("block_instagram", it).apply()
                            },
                            isStrict = strictModeInstagram,
                            onStrictChange = { 
                                strictModeInstagram = it 
                                sharedPrefs.edit().putBoolean("strict_mode_instagram", it).apply()
                            }
                        )

                        AppFilterItem(
                            appName = "Snapchat Spotlight",
                            isBlocked = blockSnapchat,
                            onBlockChange = { 
                                blockSnapchat = it 
                                sharedPrefs.edit().putBoolean("block_snapchat", it).apply()
                            },
                            isStrict = strictModeSnapchat,
                            onStrictChange = { 
                                strictModeSnapchat = it 
                                sharedPrefs.edit().putBoolean("strict_mode_snapchat", it).apply()
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        Text("Stealth Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hide App Icon", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Removes app from home screen. Access via Accessibility Settings in phone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = hideLauncherIcon,
                                onCheckedChange = { isHidden ->
                                    hideLauncherIcon = isHidden
                                    sharedPrefs.edit().putBoolean("hide_launcher_icon", isHidden).apply()
                                    
                                    val componentName = ComponentName(context, "com.example.LauncherActivity")
                                    context.packageManager.setComponentEnabledSetting(
                                        componentName,
                                        if (isHidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                        PackageManager.DONT_KILL_APP
                                    )
                                    Toast.makeText(context, if (isHidden) "App icon hidden" else "App icon restored", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSystemAccessDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun PasswordCriterion(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (met) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (met) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun AppFilterItem(appName: String, isBlocked: Boolean, onBlockChange: (Boolean) -> Unit, isStrict: Boolean, onStrictChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = isBlocked, onCheckedChange = onBlockChange)
        }
        if (isBlocked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(16.dp))
                Text("Strict Mode (No Password)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Checkbox(checked = isStrict, onCheckedChange = onStrictChange)
            }
        }
    }
}


