package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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

      val isDark = when (themeMode) {
          1 -> false
          2 -> true
          else -> androidx.compose.foundation.isSystemInDarkTheme()
      }

      val context = LocalContext.current
      val lifecycleOwner = LocalLifecycleOwner.current

      var missingOverlay by remember { mutableStateOf(false) }
      var missingAccessibility by remember { mutableStateOf(false) }
      var missingNotification by remember { mutableStateOf(false) }
      var missingBattery by remember { mutableStateOf(false) }

      LaunchedEffect(Unit) {
          while (true) {
              missingOverlay = !android.provider.Settings.canDrawOverlays(context)
              
              var isAccEnabled = false
              try {
                  val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                  val enabledServicesList = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                  for (service in enabledServicesList) {
                      if (service.resolveInfo.serviceInfo.packageName == context.packageName) {
                          isAccEnabled = true
                          break
                      }
                  }
              } catch (e: Exception) { e.printStackTrace() }
              
              if (!isAccEnabled) {
                  try {
                      val enabledServices = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                      isAccEnabled = enabledServices?.contains(context.packageName) == true
                  } catch (e: Exception) { e.printStackTrace() }
              }
              missingAccessibility = !isAccEnabled
              
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                  missingNotification = androidx.core.content.ContextCompat.checkSelfPermission(
                      context,
                      android.Manifest.permission.POST_NOTIFICATIONS
                  ) != android.content.pm.PackageManager.PERMISSION_GRANTED
              } else {
                  missingNotification = false
              }
              
              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                  val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                  missingBattery = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
              }
              kotlinx.coroutines.delay(500)
          }
      }

      DisposableEffect(lifecycleOwner) {
          val observer = LifecycleEventObserver { _, event ->
              if (event == Lifecycle.Event.ON_RESUME) {
                  missingOverlay = !android.provider.Settings.canDrawOverlays(context)
                  var isAccEnabled = false
                  try {
                      val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                      val enabledServicesList = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                      for (service in enabledServicesList) {
                          if (service.resolveInfo.serviceInfo.packageName == context.packageName) {
                              isAccEnabled = true
                              break
                          }
                      }
                  } catch (e: Exception) { e.printStackTrace() }
                  
                  if (!isAccEnabled) {
                      try {
                          val enabledServices = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                          isAccEnabled = enabledServices?.contains(context.packageName) == true
                      } catch (e: Exception) { e.printStackTrace() }
                  }
                  missingAccessibility = !isAccEnabled
                  
                  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                      missingNotification = androidx.core.content.ContextCompat.checkSelfPermission(
                          context,
                          android.Manifest.permission.POST_NOTIFICATIONS
                      ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                  } else {
                      missingNotification = false
                  }
                  
                  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                      val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                      missingBattery = !powerManager.isIgnoringBatteryOptimizations(context.packageName)
                  }
              }
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose {
              lifecycleOwner.lifecycle.removeObserver(observer)
          }
      }

      val isFirstLaunch = sharedPrefs.getBoolean("is_first_launch", true)
      
      if (!isFirstLaunch && (missingOverlay || missingAccessibility || missingNotification)) {
          AlertDialog(
              onDismissRequest = { },
              title = { Text("Permissions Required") },
              text = { 
                  Column {
                      Text("The following permissions are disabled but required for the app to function:", style = MaterialTheme.typography.bodyMedium)
                      Spacer(modifier = Modifier.height(8.dp))
                      if (missingNotification) Text("• Notifications", fontWeight = FontWeight.Bold)
                      if (missingOverlay) Text("• Display over other apps", fontWeight = FontWeight.Bold)
                      if (missingAccessibility) Text("• Accessibility service", fontWeight = FontWeight.Bold)
                      Spacer(modifier = Modifier.height(16.dp))
                      Text("Please click below to restore them.", style = MaterialTheme.typography.bodyMedium)
                  }
              },
              confirmButton = {
                  Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                      if (missingNotification) {
                          TextButton(onClick = { 
                              if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                  val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                      putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                  }
                                  context.startActivity(intent)
                              } 
                          }) { Text("Fix Notifications") }
                      }
                      if (missingOverlay) {
                          TextButton(onClick = { 
                              try {
                                  val intent = android.content.Intent(
                                      android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                      android.net.Uri.parse("package:${context.packageName}")
                                  )
                                  context.startActivity(intent)
                              } catch (e: Exception) {
                                  android.widget.Toast.makeText(context, "Cannot open Overlay Settings", android.widget.Toast.LENGTH_SHORT).show()
                              }
                          }) { Text("Fix Overlay") }
                      }
                      if (missingAccessibility) {
                           TextButton(onClick = {
                               try {
                                   val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                   context.startActivity(intent)
                               } catch (e: Exception) {
                                   android.widget.Toast.makeText(context, "Cannot open Accessibility Settings", android.widget.Toast.LENGTH_SHORT).show()
                               }
                           }) { Text("Fix Accessibility") }
                      }
                  }
              }
          )
      }

      MyApplicationTheme(darkTheme = isDark, dynamicColor = useDynamicColor) {
        var currentScreen by remember { mutableStateOf(if (isFirstLaunch) "onboarding" else "home") }
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
          val screenModifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
          when (currentScreen) {
            "onboarding" -> {
              ShortsBlockerOnboardingScreen(
                  modifier = screenModifier,
                  onFinishOnboarding = {
                      sharedPrefs.edit().putBoolean("is_first_launch", false).apply()
                      currentScreen = "home"
                  }
              )
            }
            "home" -> {
              ShortsBlockerHomeScreen(
                  modifier = screenModifier,
                  onNavigateToSettings = { currentScreen = "settings" }
              )
            }
            "settings" -> {
              ShortsBlockerSettingsScreen(
                  modifier = screenModifier,
                  onNavigateBack = { currentScreen = "home" },
                  onNavigateToSystemSettings = { currentScreen = "system_settings" }
              )
            }
            "system_settings" -> {
              ShortsBlockerSystemSettingsScreen(
                  modifier = screenModifier,
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
                  onNavigateBack = { currentScreen = "settings" },
                  onNavigateToPermissions = { currentScreen = "permissions" },
                  onNavigateToPrivacyPolicy = { currentScreen = "privacy_policy" },
                  onNavigateToAbout = { currentScreen = "about" },
                  onNavigateToHelp = { currentScreen = "help" }
              )
            }
            "permissions" -> {
                PermissionsScreen(onNavigateBack = { currentScreen = "system_settings" })
            }
            "privacy_policy" -> {
                PrivacyPolicyScreen(onNavigateBack = { currentScreen = "system_settings" })
            }
            "about" -> {
                val currentVersion = remember { UpdateChecker.getCurrentVersion(this@MainActivity) }
                AboutScreen(currentVersion = currentVersion, onNavigateBack = { currentScreen = "system_settings" })
            }
            "help" -> {
                HelpFAQScreen(onNavigateBack = { currentScreen = "system_settings" })
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
    
    val cooldownOptions = listOf(1, 2, 3, 4, 5)
    var cooldownIndex by remember {
        val mins = sharedPrefs.getInt("session_duration_minutes", 5)
        val initialIdx = cooldownOptions.indexOfLast { it <= mins }.coerceAtLeast(0)
        mutableFloatStateOf(initialIdx.toFloat())
    }
    
    var allowanceMins by remember { mutableFloatStateOf(sharedPrefs.getInt("daily_relief_minutes", 30).toFloat()) }
    var allowYt by remember { mutableStateOf(sharedPrefs.getBoolean("allowance_youtube", true)) }
    var allowIg by remember { mutableStateOf(sharedPrefs.getBoolean("allowance_instagram", true)) }
    var allowSnap by remember { mutableStateOf(sharedPrefs.getBoolean("allowance_snapchat", true)) }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var bypassCount by remember { mutableIntStateOf(sharedPrefs.getInt("bypass_count", 0)) }
    var isPasswordSectionExpanded by remember { mutableStateOf(false) }
    
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                bypassCount = sharedPrefs.getInt("bypass_count", 0)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(48.dp)) // To balance the settings icon
                    Text(
                        text = "Shorts Blocker",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Active Protection",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Friction is enabled for selected apps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LockOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = bypassCount.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Times Bypassed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Security",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 32.dp, bottom = 12.dp)
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isPasswordSectionExpanded = !isPasswordSectionExpanded }
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Master Password",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Required when an addictive scroll is intercepted.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { isPasswordSectionExpanded = !isPasswordSectionExpanded }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(visible = isPasswordSectionExpanded) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary
                                    )
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
                                        isPasswordSectionExpanded = false
                                    },
                                    enabled = isValidPassword,
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Save Credentials")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Daily Allowance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 32.dp, bottom = 12.dp)
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Daily Relief Time",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${allowanceMins.roundToInt()} min",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Daily limit for selected addictive apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                        Slider(
                            value = allowanceMins,
                            onValueChange = { 
                                allowanceMins = it
                                sharedPrefs.edit().putInt("daily_relief_minutes", it.roundToInt()).apply() 
                            },
                            valueRange = 0f..30f,
                            steps = 29,
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(28.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    ),
                                    sliderState = sliderState,
                                    modifier = Modifier.height(16.dp)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Target Apps",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppSelectionChip(label = "YouTube", isSelected = allowYt, onToggle = {
                                allowYt = it
                                sharedPrefs.edit().putBoolean("allowance_youtube", it).apply()
                            }, modifier = Modifier.weight(1f))
                            AppSelectionChip(label = "Instagram", isSelected = allowIg, onToggle = {
                                allowIg = it
                                sharedPrefs.edit().putBoolean("allowance_instagram", it).apply()
                            }, modifier = Modifier.weight(1f))
                            AppSelectionChip(label = "Snapchat", isSelected = allowSnap, onToggle = {
                                allowSnap = it
                                sharedPrefs.edit().putBoolean("allowance_snapchat", it).apply()
                            }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Session Cooldown",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 32.dp, bottom = 12.dp)
                )
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Post-Unlock Grace Period",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${cooldownOptions[cooldownIndex.roundToInt()]} min",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "How long until you are asked for standard password again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                        Slider(
                            value = cooldownIndex,
                            onValueChange = { cooldownIndex = it },
                            onValueChangeFinished = { 
                                sharedPrefs.edit().putInt("session_duration_minutes", cooldownOptions[cooldownIndex.roundToInt()]).apply() 
                            },
                            valueRange = 0f..4f,
                            steps = 3,
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(28.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        activeTickColor = MaterialTheme.colorScheme.onPrimary,
                                        inactiveTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    ),
                                    sliderState = sliderState,
                                    modifier = Modifier.height(16.dp)
                                )
                            }
                        )
                    }
                }
            }
            
        }
    }
}

@Composable
fun AppSelectionChip(label: String, isSelected: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable { onToggle(!isSelected) }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
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

    var showDeveloperSettings by remember {
        mutableStateOf(sharedPrefs.getBoolean("show_developer_settings", false))
    }
    var settingsClickCount by remember { mutableIntStateOf(0) }
    var enableFloatingTimer by remember {
        mutableStateOf(sharedPrefs.getBoolean("enable_floating_timer", false))
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
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        settingsClickCount++
                        if (settingsClickCount >= 7) {
                            showDeveloperSettings = true
                            sharedPrefs.edit().putBoolean("show_developer_settings", true).apply()
                            Toast.makeText(context, "Developer Settings Unlocked", Toast.LENGTH_SHORT).show()
                        }
                    }
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

            Text("Target Apps Configuration", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
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

            Text("Stealth Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
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
                        
                        val componentName = ComponentName(context, "com.example.SplashActivity")
                        context.packageManager.setComponentEnabledSetting(
                            componentName,
                            if (isHidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                        )
                        Toast.makeText(context, if (isHidden) "App icon hidden" else "App icon restored", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (showDeveloperSettings) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                Text("Developer Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Floating Timer", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Display on-screen cooldown timer overlay when watching shorts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableFloatingTimer,
                        onCheckedChange = { isEnabled ->
                            enableFloatingTimer = isEnabled
                            sharedPrefs.edit().putBoolean("enable_floating_timer", isEnabled).apply()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        try {
                            val miuiIntent = android.content.Intent().apply {
                                setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                                putExtra("package_name", context.packageName)
                                putExtra("package_label", "Shorts Blocker")
                            }
                            context.startActivity(miuiIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Not a Xiaomi device or settings not found.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Force Open MIUI Battery Saver")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        showDeveloperSettings = false
                        settingsClickCount = 0
                        sharedPrefs.edit().putBoolean("show_developer_settings", false).apply()
                        Toast.makeText(context, "Developer Settings Hidden", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Hide Developer Settings")
                }
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


