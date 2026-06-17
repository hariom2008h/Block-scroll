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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.*
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    
    val sharedPrefs = getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
    val crashLog = sharedPrefs.getString("crash_log", null)
    
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        val sw = java.io.StringWriter()
        exception.printStackTrace(java.io.PrintWriter(sw))
        sharedPrefs.edit().putString("crash_log", sw.toString()).commit()
        kotlin.system.exitProcess(1)
    }

    enableEdgeToEdge()
    setContent {
      if (crashLog != null) {
          Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
              Column {
                  Text("CRASH DETECTED", color = Color.Red, fontWeight = FontWeight.Bold)
                  Text(crashLog, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState()))
                  Button(onClick = { sharedPrefs.edit().remove("crash_log").apply(); recreate() }) {
                      Text("Clear and Restart")
                  }
              }
          }
          return@setContent
      }
      
      var themeMode by remember { mutableIntStateOf(sharedPrefs.getInt("theme_mode", 0)) } // 0: Auto, 1: Light, 2: Dark
      var useDynamicColor by remember { mutableStateOf(sharedPrefs.getBoolean("dynamic_color", true)) }

      // We can update these by listening to changes in the sub-screens if we pass callbacks, or simply listen to sharedPrefs
      // Prefer passing callbacks or a Viewmodel but for simplicity we'll pass an update function down or use a wrapper
      // Actually we can pass nothing if they update sharedPrefs and we don't react immediately, but it's better to react.
      // So we will pass callbacks if needed, or better, keep the state here and pass it via Provide.
      // Easiest is to pass standard callbacks to the system settings screen.
      
      val isDark = when (themeMode) {
          1 -> false
          2 -> true
          else -> androidx.compose.foundation.isSystemInDarkTheme()
      }

      MyApplicationTheme(darkTheme = isDark, dynamicColor = useDynamicColor) {
        val isFirstLaunch = sharedPrefs.getBoolean("is_first_launch", true)
        var currentScreen by remember { mutableStateOf(if (isFirstLaunch) "onboarding" else "home") }
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
          when (currentScreen) {
            "onboarding" -> {
              ShortsBlockerOnboardingScreen(
                  modifier = Modifier.padding(innerPadding),
                  onFinishOnboarding = {
                      sharedPrefs.edit().putBoolean("is_first_launch", false).apply()
                      currentScreen = "home"
                  }
              )
            }
            "home" -> {
              ShortsBlockerHomeScreen(
                  modifier = Modifier.padding(innerPadding),
                  onNavigateToSettings = { currentScreen = "settings" }
              )
            }
            "settings" -> {
              ShortsBlockerSettingsScreen(
                  modifier = Modifier.padding(innerPadding),
                  onNavigateBack = { currentScreen = "home" },
                  onNavigateToSystemSettings = { currentScreen = "system_settings" }
              )
            }
            "system_settings" -> {
              ShortsBlockerSystemSettingsScreen(
                  modifier = Modifier.padding(innerPadding),
                  themeMode = themeMode,
                  onThemeModeChange = { 
                      themeMode = it
                      sharedPrefs.edit().putInt("theme_mode", it).apply()
                  },
                  useDynamicColor = useDynamicColor,
                  onDynamicColorChange = {
                      useDynamicColor = it
                      sharedPrefs.edit().putBoolean("dynamic_color", it).apply()
                  },
                  onNavigateBack = { currentScreen = "settings" }
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsBlockerHomeScreen(modifier: Modifier = Modifier, onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
    }

    var password by remember { 
        mutableStateOf(sharedPrefs.getString("master_password", "I will not waste my time") ?: "") 
    }
    
    var sessionDuration by remember {
        mutableFloatStateOf(sharedPrefs.getInt("session_duration_minutes", 2).toFloat())
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
                IconButton(onClick = onNavigateToSettings) {
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
            // Section: Device Stability (Optional)
            val isXiaomi = remember { 
                Build.MANUFACTURER.contains("Xiaomi", ignoreCase = true) || 
                Build.BRAND.contains("Xiaomi", ignoreCase = true) || 
                Build.BRAND.contains("POCO", ignoreCase = true) || 
                Build.BRAND.contains("Redmi", ignoreCase = true)
            }
            
            var isNotificationGranted by remember { 
                mutableStateOf(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                isNotificationGranted = isGranted
            }

            Text(
                text = "Stability Check",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "If the app stops working randomly, enable these options to keep it alive in the background.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    StabilityItem(
                        title = "Stability Notification",
                        description = "Keeps the service from being killed by Android.",
                        isDone = isNotificationGranted,
                        icon = Icons.Rounded.NotificationsActive,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )

                    if (isXiaomi) {
                        StabilityItem(
                            title = "Enable Auto-start",
                            description = "Prevents Poco/Xiaomi from force-closing the blocker.",
                            isDone = false, // We can't strictly check this easily, let them click
                            icon = Icons.Rounded.RocketLaunch,
                            onClick = {
                                try {
                                    val intent = Intent()
                                    intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        )
                    }
                }
            }
            
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

            // Section: Session Cooldown
            Text(
                text = "Session Cooldown",
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
                        Text("Post-Unlock Grace Period", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("${sessionDuration.toInt()} min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "How long until you are asked for standard password again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = sessionDuration,
                        onValueChange = { sessionDuration = it },
                        onValueChangeFinished = {
                            sharedPrefs.edit().putInt("session_duration_minutes", sessionDuration.toInt()).apply()
                        },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsBlockerSettingsScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit, onNavigateToSystemSettings: () -> Unit) {
    val context = LocalContext.current

    val sharedPrefs = remember {
        context.getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
    }

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
                    text = "Settings",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
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
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            ElevatedCard(
                onClick = onNavigateToSystemSettings,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                            text = "System Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Overlay, Accessibility, Updates & About",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
    }


}

@Composable
fun StabilityItem(title: String, description: String, isDone: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isDone) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Rounded.ChevronRight, null)
            }
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


