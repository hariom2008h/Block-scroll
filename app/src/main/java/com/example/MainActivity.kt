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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
    if (sharedPrefs.contains("crash_log")) {
      sharedPrefs.edit().remove("crash_log").apply()
    }

    StealthModeManager.applyRecentsStealth(this)

    val isLaunchedFromHomeScreen = intent?.action == Intent.ACTION_MAIN &&
        intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true
    if (isLaunchedFromHomeScreen &&
        StealthModeManager.isStealthEnabled(this) &&
        StealthModeManager.isAccessibilityServiceRunning(this)
    ) {
      Toast.makeText(
          this,
          "Stealth Mode ON: Open via Settings → Accessibility → Shorts Blocker",
          Toast.LENGTH_SHORT
      ).show()
      finish()
      return
    }

    enableEdgeToEdge()
    setContent {
      
      var themeMode by remember { mutableIntStateOf(sharedPrefs.getInt("theme_mode", 0)) } // 0: Auto, 1: Light, 2: Dark
      var useDynamicColor by remember { mutableStateOf(sharedPrefs.getBoolean("material_you_dynamic", true)) }

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
        var availableUpdate by remember { mutableStateOf<UpdateResult.NewVersionAvailable?>(null) }

        LaunchedEffect(currentScreen) {
            if (currentScreen == "home" && availableUpdate == null) {
                UpdateChecker.checkForUpdateOnLaunch(this@MainActivity) { result ->
                    availableUpdate = result
                }
            }
        }

        availableUpdate?.let { update ->
            val hasIndusStore = remember {
                UpdateChecker.isInstalledFromIndusAppstore(this@MainActivity) ||
                    UpdateChecker.isIndusAppstoreInstalled(this@MainActivity)
            }
            AlertDialog(
                onDismissRequest = {
                    UpdateChecker.dismissUpdateVersion(this@MainActivity, update.latestVersion)
                    availableUpdate = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "New Update Available",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "v${update.currentVersion.removePrefix("v")} → v${update.latestVersion.removePrefix("v")}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (update.releaseNotes.isNotBlank()) {
                            Text(
                                text = update.releaseNotes.take(280),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (hasIndusStore) {
                            Text(
                                text = "Update directly via Indus Appstore for seamless installation.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (hasIndusStore) {
                                UpdateChecker.openInIndusAppstore(this@MainActivity)
                            } else if (update.downloadUrl.endsWith(".apk")) {
                                Toast.makeText(this@MainActivity, "Downloading update...", Toast.LENGTH_SHORT).show()
                                UpdateChecker.downloadAndInstallApk(
                                    context = this@MainActivity,
                                    downloadUrl = update.downloadUrl,
                                    onProgress = {},
                                    onError = { err ->
                                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                UpdateChecker.openInIndusAppstore(this@MainActivity)
                            }
                            availableUpdate = null
                        }
                    ) {
                        Text(if (hasIndusStore) "Update on Indus" else "Update Now", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            UpdateChecker.dismissUpdateVersion(this@MainActivity, update.latestVersion)
                            availableUpdate = null
                        }
                    ) {
                        Text("Later")
                    }
                }
            )
        }
        
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
              androidx.activity.compose.BackHandler { currentScreen = "home" }
              ShortsBlockerSettingsScreen(
                  modifier = screenModifier,
                  onNavigateBack = { currentScreen = "home" },
                  onNavigateToSystemSettings = { currentScreen = "system_settings" }
              )
            }
            "system_settings" -> {
              androidx.activity.compose.BackHandler { currentScreen = "settings" }
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
                      sharedPrefs.edit()
                          .putBoolean("material_you_dynamic", it)
                          .putBoolean("dynamic_color", it)
                          .apply()
                  },
                  onNavigateBack = { currentScreen = "settings" },
                  onNavigateToPermissions = { currentScreen = "permissions" },
                  onNavigateToPrivacyPolicy = { currentScreen = "privacy_policy" },
                  onNavigateToAbout = { currentScreen = "about" },
                  onNavigateToHelp = { currentScreen = "help" }
              )
            }
            "permissions" -> {
                androidx.activity.compose.BackHandler { currentScreen = "system_settings" }
                PermissionsScreen(onNavigateBack = { currentScreen = "system_settings" })
            }
            "privacy_policy" -> {
                androidx.activity.compose.BackHandler { currentScreen = "system_settings" }
                PrivacyPolicyScreen(onNavigateBack = { currentScreen = "system_settings" })
            }
            "about" -> {
                androidx.activity.compose.BackHandler { currentScreen = "system_settings" }
                val currentVersion = remember { UpdateChecker.getCurrentVersion(this@MainActivity) }
                AboutScreen(currentVersion = currentVersion, onNavigateBack = { currentScreen = "system_settings" })
            }
            "help" -> {
                androidx.activity.compose.BackHandler { currentScreen = "system_settings" }
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

    var savedPassword by remember {
        mutableStateOf(sharedPrefs.getString("master_password", "I will not waste my time") ?: "I will not waste my time")
    }
    var isCustomPasswordSet by remember {
        mutableStateOf(
            sharedPrefs.getBoolean("is_custom_password_set", false) ||
                savedPassword != "I will not waste my time"
        )
    }
    var password by remember {
        mutableStateOf(if (isCustomPasswordSet) savedPassword else "")
    }
    var showCurrentPassword by remember { mutableStateOf(!isCustomPasswordSet) }
    val passwordSuggestions = remember { listOf("Focus@247", "Mind#582", "Goal$369", "Stop!481", "Calm%739") }
    var suggestionIdx by remember { mutableIntStateOf(0) }

    val cooldownOptions = listOf(1, 2, 3, 4, 5)
    var cooldownIndex by remember {
        val mins = sharedPrefs.getInt("session_duration_minutes", 5)
        val initialIdx = cooldownOptions.indexOfLast { it <= mins }.coerceAtLeast(0)
        mutableFloatStateOf(initialIdx.toFloat())
    }
    
    var allowanceMins by remember {
        val raw = sharedPrefs.getInt("daily_relief_minutes", 30)
        val clamped = raw.coerceIn(0, 30)
        if (raw != clamped) {
            sharedPrefs.edit().putInt("daily_relief_minutes", clamped).apply()
        }
        mutableFloatStateOf(clamped.toFloat())
    }
    var allowYt by remember { mutableStateOf(sharedPrefs.getBoolean("allowance_youtube", true)) }
    var allowIg by remember { mutableStateOf(sharedPrefs.getBoolean("allowance_instagram", true)) }
    var allowSnap by remember { mutableStateOf(sharedPrefs.getBoolean("allowance_snapchat", true)) }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var bypassCount by remember { mutableIntStateOf(sharedPrefs.getInt("bypass_count", 0)) }
    var isPasswordSectionExpanded by remember { mutableStateOf(!isCustomPasswordSet) }
    var showPostOnboardingPasswordSheet by remember {
        mutableStateOf(
            !isCustomPasswordSet &&
                !sharedPrefs.getBoolean("post_onboarding_password_prompt_shown", false)
        )
    }
    
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                bypassCount = sharedPrefs.getInt("bypass_count", 0)
                allowanceMins = sharedPrefs.getInt("daily_relief_minutes", 30).coerceIn(0, 30).toFloat()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPostOnboardingPasswordSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                sharedPrefs.edit().putBoolean("post_onboarding_password_prompt_shown", true).apply()
                showPostOnboardingPasswordSheet = false
                isPasswordSectionExpanded = true
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            val hasMinLength = password.length >= 6
            val hasUpper = password.any { it.isUpperCase() }
            val hasLower = password.any { it.isLowerCase() }
            val hasDigit = password.any { it.isDigit() }
            val hasSpecial = password.any { !it.isLetterOrDigit() }
            val hasNoRepeats = password.isNotEmpty() && password.toSet().size == password.length
            val isValidPass = hasMinLength && hasUpper && hasLower && hasDigit && hasSpecial && hasNoRepeats

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Set Friction Password",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Shorts और Reels खोलते समय रुकावट पैदा करने के लिए एक पासवर्ड सेट करें।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Friction Password") },
                    placeholder = { Text("e.g. Focus@247") },
                    trailingIcon = {
                        Surface(
                            onClick = {
                                password = passwordSuggestions[suggestionIdx % passwordSuggestions.size]
                                suggestionIdx++
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Auto Generate",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PasswordCriterion(text = "कम से कम 6 अक्षर होने चाहिए", met = hasMinLength)
                    PasswordCriterion(text = "एक बड़ा अक्षर हो (A-Z)", met = hasUpper)
                    PasswordCriterion(text = "एक छोटा अक्षर हो (a-z)", met = hasLower)
                    PasswordCriterion(text = "एक नंबर हो (0-9)", met = hasDigit)
                    PasswordCriterion(text = "एक स्पेशल चिन्ह हो (@ # $ % !)", met = hasSpecial)
                    PasswordCriterion(text = "कोई भी अक्षर दोबारा न हो", met = hasNoRepeats)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        sharedPrefs.edit()
                            .putString("master_password", password)
                            .putBoolean("is_custom_password_set", true)
                            .putBoolean("post_onboarding_password_prompt_shown", true)
                            .apply()
                        savedPassword = password
                        isCustomPasswordSet = true
                        showCurrentPassword = false
                        isPasswordSectionExpanded = false
                        showPostOnboardingPasswordSheet = false
                        Toast.makeText(context, "Friction Password Saved!", Toast.LENGTH_SHORT).show()
                    },
                    enabled = isValidPass,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save Password", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Shorts Blocker",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.padding(end = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Hero Card: Material You Secondary Container
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Active Protection",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Friction is enabled for selected apps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Material You Inner Surface Stats Card
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // Stat 1: Times Bypassed
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
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
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = bypassCount.toString(),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Times Bypassed",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )

                                    // Stat 2: Daily Relief Limit
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Timer,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${allowanceMins.roundToInt()}m",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Daily Allowance",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Mindful Friction Section
                item {
                    Text(
                        text = "Scroll Friction",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isPasswordSectionExpanded = !isPasswordSectionExpanded }
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Friction Password",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isCustomPasswordSet && !showCurrentPassword) {
                                            "Saved • Tap Edit to change"
                                        } else if (isCustomPasswordSet) {
                                            "Current: $savedPassword"
                                        } else {
                                            "Shorts/Reels खोलते समय रुकावट के लिए पासवर्ड"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isCustomPasswordSet && !isPasswordSectionExpanded) {
                                    TextButton(
                                        onClick = { showCurrentPassword = !showCurrentPassword }
                                    ) {
                                        Text(
                                            text = if (showCurrentPassword) "Hide" else "Show",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                FilledTonalIconButton(
                                    onClick = { isPasswordSectionExpanded = !isPasswordSectionExpanded },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isPasswordSectionExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
                                        contentDescription = "Edit"
                                    )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(visible = isPasswordSectionExpanded) {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("Friction Password") },
                                        placeholder = { Text("e.g. Focus@247") },
                                        trailingIcon = {
                                            Surface(
                                                onClick = {
                                                    password = passwordSuggestions[suggestionIdx % passwordSuggestions.size]
                                                    suggestionIdx++
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Refresh,
                                                        contentDescription = "Auto Generate",
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Auto",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
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

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PasswordCriterion(text = "कम से कम 6 अक्षर होने चाहिए", met = hasMinLength)
                                        PasswordCriterion(text = "एक बड़ा अक्षर हो (A-Z)", met = hasUpper)
                                        PasswordCriterion(text = "एक छोटा अक्षर हो (a-z)", met = hasLower)
                                        PasswordCriterion(text = "एक नंबर हो (0-9)", met = hasDigit)
                                        PasswordCriterion(text = "एक स्पेशल चिन्ह हो (@ # $ % !)", met = hasSpecial)
                                        PasswordCriterion(text = "कोई भी अक्षर दोबारा न हो", met = hasNoRepeats)
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            sharedPrefs.edit()
                                                .putString("master_password", password)
                                                .putBoolean("is_custom_password_set", true)
                                                .apply()
                                            savedPassword = password
                                            isCustomPasswordSet = true
                                            showCurrentPassword = false
                                            Toast.makeText(context, "Friction Password Saved!", Toast.LENGTH_SHORT).show()
                                            isPasswordSectionExpanded = false
                                        },
                                        enabled = isValidPassword,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Save Password", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Daily Allowance Section
                item {
                    Text(
                        text = "Daily Allowance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Daily Relief Time",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Daily limit for selected addictive apps.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Timer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${allowanceMins.roundToInt()} min",
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                            Slider(
                                value = allowanceMins.coerceIn(0f, 30f),
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
                            Spacer(modifier = Modifier.height(10.dp))
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

                // Session Cooldown Section
                item {
                    Text(
                        text = "Session Cooldown",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Post-Unlock Grace Period",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "How long until you are asked for standard password again.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "${cooldownOptions[cooldownIndex.roundToInt()]} min",
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
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
}

@Composable
fun AppSelectionChip(label: String, isSelected: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle(!isSelected) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
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
        mutableStateOf(StealthModeManager.isStealthEnabled(context))
    }

    var showDeveloperSettings by remember {
        mutableStateOf(sharedPrefs.getBoolean("show_developer_settings", false))
    }
    var settingsClickCount by remember { mutableIntStateOf(0) }
    var enableFloatingTimer by remember {
        mutableStateOf(sharedPrefs.getBoolean("enable_floating_timer", false))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
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
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // System Settings Navigation Card (Material You Tonal Banner)
                ElevatedCard(
                    onClick = onNavigateToSystemSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Material You, Overlay, Permissions, Updates & About",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Target Apps Configuration
                Column {
                    Text(
                        text = "Target Apps Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)

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

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.surfaceVariant)

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
                        }
                    }
                }

                // Stealth Mode Section
                Column {
                    Text(
                        text = "Stealth Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hide App Icon",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Hides app from Home Screen, App Drawer & Recent Apps. Reopen only via Accessibility Settings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = hideLauncherIcon,
                                    onCheckedChange = { isHidden ->
                                        hideLauncherIcon = isHidden
                                        StealthModeManager.setStealthEnabled(
                                            context = context,
                                            hidden = isHidden
                                        )
                                        Toast.makeText(
                                            context,
                                            if (isHidden) "Stealth Mode ON: Open via Accessibility Settings" else "Home screen access restored",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(visible = hideLauncherIcon) {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "How to open app while hidden",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Go to Phone Settings → Accessibility → Downloaded Apps → Shorts Blocker → tap Settings (⚙️) to open this screen.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            FilledTonalButton(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Unable to open Accessibility Settings", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(50),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Settings,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Verify in Accessibility Settings", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showDeveloperSettings) {
                    Column {
                        Text(
                            text = "Developer Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Show Floating Timer",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Display on-screen cooldown timer overlay when watching shorts.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = enableFloatingTimer,
                                        onCheckedChange = { isEnabled ->
                                            enableFloatingTimer = isEnabled
                                            sharedPrefs.edit().putBoolean("enable_floating_timer", isEnabled).apply()
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                FilledTonalButton(
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
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text("Force Open MIUI Battery Saver", fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        showDeveloperSettings = false
                                        settingsClickCount = 0
                                        sharedPrefs.edit().putBoolean("show_developer_settings", false).apply()
                                        Toast.makeText(context, "Developer Settings Hidden", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text("Hide Developer Settings")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                text = appName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(checked = isBlocked, onCheckedChange = onBlockChange)
        }
        if (isBlocked) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onStrictChange(!isStrict) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Checkbox(checked = isStrict, onCheckedChange = onStrictChange)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Strict Mode (No Password)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


