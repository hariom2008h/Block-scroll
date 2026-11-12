package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsBlockerSystemSettingsScreen(
    modifier: Modifier = Modifier, 
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToHelp: () -> Unit
) {
    val context = LocalContext.current
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val sharedPrefs = remember {
        context.getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
    }
    
    val gitHubOwner = "hariom2008h"
    val gitHubRepo = "Block-scroll"
    
    var updateCheckStatus by remember { mutableStateOf<String?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<UpdateResult.NewVersionAvailable?>(null) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var feedbackImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSendingFeedback by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        feedbackImages = uris
    }

    var pauseForPayments by remember { mutableStateOf(sharedPrefs.getBoolean("pause_for_payments", true)) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
        ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "System Settings",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
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
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.primary,
            )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Card Header with CloudDownload Icon and App Version Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "App Updates",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "App Updates",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "GitHub-based updater",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Premium Version Badge
                        val currentAppVersion = remember { UpdateChecker.getCurrentVersion(context) }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "v$currentAppVersion",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Check for official application updates and view recent release logs directly from GitHub.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Buttons and States
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status indicator
                        Box(
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            updateCheckStatus?.let { status ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val (icon, color) = when {
                                        status.contains("Error", true) || status.contains("failed", true) -> {
                                            Icons.Rounded.Warning to MaterialTheme.colorScheme.error
                                        }
                                        status.contains("up to date", true) || status.contains("App is up to date", true) -> {
                                            Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
                                        }
                                        else -> {
                                            Icons.Rounded.Info to MaterialTheme.colorScheme.secondary
                                        }
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = color,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                updateChecking = true
                                updateCheckStatus = "Checking GitHub..."
                                UpdateChecker.checkForUpdate(
                                    context = context,
                                    owner = gitHubOwner,
                                    repo = gitHubRepo
                                ) { result ->
                                    updateChecking = false
                                    when (result) {
                                        is UpdateResult.NewVersionAvailable -> {
                                            updateCheckStatus = "Update available: v${result.latestVersion}"
                                            showUpdateDialog = result
                                        }
                                        is UpdateResult.UpToDate -> {
                                            updateCheckStatus = "App is up to date!"
                                        }
                                        is UpdateResult.Error -> {
                                            updateCheckStatus = result.message
                                        }
                                    }
                                }
                            },
                            enabled = !updateChecking,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            if (updateChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking...")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Check Update")
                            }
                        }
                    }

                    // Download progress section inside the card
                    downloadProgress?.let { progress ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading installation package...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$progress%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        }
                    }

                    downloadError?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        "System Access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsListItem(
                        icon = Icons.Rounded.Shield,
                        title = "Manage Permissions",
                        onClick = onNavigateToPermissions
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Pause for Payment Apps", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Automatically pause accessibility service when a payment app is opened",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = pauseForPayments,
                            onCheckedChange = {
                                pauseForPayments = it
                                sharedPrefs.edit().putBoolean("pause_for_payments", it).apply()
                            }
                        )
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        "Appearance & Material You",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Dynamic Colors (Material You)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Automatically adapts the app's color palette to match your phone's wallpaper and Material You system theme.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useDynamicColor,
                                onCheckedChange = { onDynamicColorChange(it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text("Theme Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val options = listOf("Auto", "Light", "Dark")
                        options.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = themeMode == index,
                                onClick = { onThemeModeChange(index) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                            ) {
                                Text(label, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        "Help & Policy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsListItem(
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        title = "Help & FAQs",
                        onClick = onNavigateToHelp
                    )
                    SettingsListItem(
                        icon = Icons.Rounded.Description,
                        title = "Terms of Service & Privacy Policy",
                        onClick = onNavigateToPrivacyPolicy
                    )
                    SettingsListItem(
                        icon = Icons.Rounded.ChatBubbleOutline,
                        title = "Send feedback",
                        onClick = { showFeedbackDialog = true }
                    )
                    SettingsListItem(
                        icon = Icons.Rounded.Info,
                        title = "About",
                        onClick = onNavigateToAbout
                    )
                }
            }

            if (showFeedbackDialog) {
                AlertDialog(
                    onDismissRequest = { showFeedbackDialog = false },
                    title = { Text("Send Feedback") },
                    text = {
                        Column {
                            Text("Tell us what you think or report an issue:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = feedbackText,
                                onValueChange = { feedbackText = it },
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                maxLines = 5,
                                placeholder = { Text("Enter your feedback here...") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                ) {
                                    Text("Add Screenshots (${feedbackImages.size}/5)")
                                }
                            }
                            if (feedbackImages.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(feedbackImages.size) { index ->
                                        Box(
                                            modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Img ${index+1}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (feedbackText.isNotBlank() || feedbackImages.isNotEmpty()) {
                                    var isAccessibilityEnabled = false
                                    try {
                                        val accessibilityEnabled = android.provider.Settings.Secure.getInt(
                                            context.contentResolver,
                                            android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
                                        )
                                        if (accessibilityEnabled == 1) {
                                            val settingValue = android.provider.Settings.Secure.getString(
                                                context.contentResolver,
                                                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                                            )
                                            if (settingValue?.contains(context.packageName) == true) {
                                                isAccessibilityEnabled = true
                                            }
                                        }
                                    } catch (e: Exception) {}
                                    
                                    val currentText = feedbackText
                                    val currentImages = feedbackImages.toList()
                                    
                                    showFeedbackDialog = false
                                    feedbackText = ""
                                    feedbackImages = emptyList()
                                    Toast.makeText(context, "Sending feedback in background...", Toast.LENGTH_SHORT).show()
                                    
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val success = sendFeedbackToTelegram(context, currentText, currentImages)
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (success) {
                                                Toast.makeText(context, "फीडबैक के लिए धन्यवाद!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Failed to send feedback.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = feedbackText.isNotBlank() || feedbackImages.isNotEmpty()
                        ) {
                            Text("Submit")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showFeedbackDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }

    // Full screen overlays using AnimatedVisibility
    androidx.compose.animation.AnimatedVisibility(
        visible = showUpdateDialog != null,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
    ) {
        showUpdateDialog?.let { updateInfo ->
            UpdateDetailsScreen(
                updateInfo = updateInfo,
                onNavigateBack = { showUpdateDialog = null },
                onDownload = {
                    downloadError = null
                    downloadProgress = 0
                    UpdateChecker.downloadAndInstallApk(
                        context = context,
                        downloadUrl = updateInfo.downloadUrl,
                        onProgress = { progress ->
                            downloadProgress = progress
                        },
                        onError = { err ->
                            downloadProgress = null
                            downloadError = err
                        }
                    )
                },
                downloadProgress = downloadProgress,
                downloadError = downloadError
            )
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityGranted by remember { mutableStateOf(false) }
    var isBatteryOptimizationDisabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            isOverlayGranted = Settings.canDrawOverlays(context)
            
            var isAccEnabled = false
            try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
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
                    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    isAccEnabled = enabledServices?.contains(context.packageName) == true
                } catch (e: Exception) { e.printStackTrace() }
            }
            isAccessibilityGranted = isAccEnabled
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                isBatteryOptimizationDisabled = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                isBatteryOptimizationDisabled = true
            }
            
            kotlinx.coroutines.delay(500)
        }
    }

    androidx.activity.compose.BackHandler { onNavigateBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Manage Permissions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val overlayColor by animateColorAsState(if (isOverlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                val overlayIcon = if (isOverlayGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = overlayIcon,
                            contentDescription = null,
                            tint = overlayColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlay Permission",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isOverlayGranted) "Active" else "Required to show block prompt",
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
                            shape = RoundedCornerShape(12.dp),
                            colors = if (isOverlayGranted) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()
                        ) {
                            Text(if (isOverlayGranted) "Manage" else "Grant", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                val accColor by animateColorAsState(if (isAccessibilityGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                val accIcon = if (isAccessibilityGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Settings

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = accIcon,
                            contentDescription = null,
                            tint = accColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accessibility Service",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isAccessibilityGranted) "Active" else "Required to intercept scrolls",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = if (isAccessibilityGranted) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()
                        ) {
                            Text(if (isAccessibilityGranted) "Manage" else "Enable", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                val requiresAutoStart = remember { needsAutoStart() }
                
                if (requiresAutoStart) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto Start",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Required for background block",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    openAutoStartSettings(context)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Manage", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    val batteryColor by animateColorAsState(if (isBatteryOptimizationDisabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    val batteryIcon = if (isBatteryOptimizationDisabled) Icons.Rounded.CheckCircle else Icons.Rounded.Warning

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = batteryIcon,
                                contentDescription = null,
                                tint = batteryColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Battery Optimization",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isBatteryOptimizationDisabled) "Unrestricted" else "Recommended for background block",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e2: Exception) {
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = if (isBatteryOptimizationDisabled) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()
                            ) {
                                Text(if (isBatteryOptimizationDisabled) "Manage" else "Fix", fontWeight = FontWeight.SemiBold)
                            }
                    }
                }
            }
        }
    }
}
}

@Composable
fun SettingsListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

suspend fun sendFeedbackToTelegram(context: Context, feedback: String, imageUris: List<Uri>): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val token = BuildConfig.TELEGRAM_BOT_TOKEN
            val chatId = BuildConfig.TELEGRAM_CHAT_ID
            val threadId = BuildConfig.TELEGRAM_THREAD_ID
            
            if (token.isEmpty() || chatId.isEmpty()) {
                return@withContext false
            }

            val client = OkHttpClient()
            val url = "https://api.telegram.org/bot$token/sendMessage"
            
            // Extract Diagnostics
            val deviceManufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            val deviceModel = android.os.Build.MODEL
            val androidVersion = android.os.Build.VERSION.RELEASE
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) { "Unknown" }
            
            val isOverlayGranted = Settings.canDrawOverlays(context)
            val isNotificationsEnabled = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.areNotificationsEnabled()
            } else {
                true
            }

            val userFeedback = feedback.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

            val timeStamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

            val telegramMessage = """
                <b>📥 NEW FEEDBACK RECEIVED</b>
                ⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯
                
                <b>💬 User Message:</b>
                <i>$userFeedback</i>
                
                <b>📅 Date &amp; Time:</b> $timeStamp
                
                ⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯
                <b>📱 Device Diagnostics:</b>
                <code>• Manufacturer : $deviceManufacturer</code>
                <code>• Device Model : $deviceModel</code>
                <code>• Android Ver. : $androidVersion</code>
                <code>• App Version  : $appVersion</code>
                
                <b>⚙️ System Permissions:</b>
                <code>• Overlay      : ${if (isOverlayGranted) "✅ GRANTED" else "❌ DENIED"}</code>
                <code>• Notification : ${if (isNotificationsEnabled) "✅ ENABLED" else "❌ DISABLED"}</code>
                ⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯
            """.trimIndent()
            
            val jsonObject = JSONObject()
            jsonObject.put("chat_id", chatId)
            if (threadId.isNotEmpty()) {
                jsonObject.put("message_thread_id", threadId)
            }
            jsonObject.put("text", telegramMessage)
            jsonObject.put("parse_mode", "HTML")
            
            val body = RequestBody.create(
                "application/json; charset=utf-8".toMediaType(),
                jsonObject.toString()
            )
            
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
                
            var textSuccess = false    
            client.newCall(request).execute().use { response ->
                textSuccess = response.isSuccessful
            }
            
            var imagesSuccess = true
            if (imageUris.isNotEmpty()) {
                if (imageUris.size == 1) {
                    try {
                        val uri = imageUris.first()
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        if (bytes != null) {
                            val multipartBuilder = MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("chat_id", chatId)
                                
                            if (threadId.isNotEmpty()) {
                                multipartBuilder.addFormDataPart("message_thread_id", threadId)
                            }
    
                            val requestBody = multipartBuilder
                                .addFormDataPart("photo", "screenshot_${System.currentTimeMillis()}.jpg", RequestBody.create("image/jpeg".toMediaType(), bytes))
                                .build()
                            val photoRequest = Request.Builder()
                                .url("https://api.telegram.org/bot$token/sendPhoto")
                                .post(requestBody)
                                .build()
                            client.newCall(photoRequest).execute().use { photoResponse ->
                                if (!photoResponse.isSuccessful) {
                                    imagesSuccess = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        imagesSuccess = false
                    }
                } else {
                    val chunks = imageUris.chunked(10)
                    for (chunk in chunks) {
                        try {
                            val mediaArray = JSONArray()
                            val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                            multipartBuilder.addFormDataPart("chat_id", chatId)
                            if (threadId.isNotEmpty()) {
                                multipartBuilder.addFormDataPart("message_thread_id", threadId)
                            }
                            
                            for ((index, uri) in chunk.withIndex()) {
                                val inputStream = context.contentResolver.openInputStream(uri)
                                val bytes = inputStream?.readBytes()
                                inputStream?.close()
                                if (bytes != null) {
                                    val attachName = "photo$index"
                                    val mediaObj = JSONObject()
                                    mediaObj.put("type", "photo")
                                    mediaObj.put("media", "attach://$attachName")
                                    mediaArray.put(mediaObj)
                                    
                                    multipartBuilder.addFormDataPart(attachName, "screenshot_$index.jpg", RequestBody.create("image/jpeg".toMediaType(), bytes))
                                }
                            }
                            
                            if (mediaArray.length() > 0) {
                                multipartBuilder.addFormDataPart("media", mediaArray.toString())
                                val requestBody = multipartBuilder.build()
                                val mediaGroupRequest = Request.Builder()
                                    .url("https://api.telegram.org/bot$token/sendMediaGroup")
                                    .post(requestBody)
                                    .build()
                                    
                                client.newCall(mediaGroupRequest).execute().use { response ->
                                    if (!response.isSuccessful) {
                                        imagesSuccess = false
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            imagesSuccess = false
                        }
                    }
                }
            }
            
            textSuccess && (imageUris.isEmpty() || imagesSuccess)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

fun needsAutoStart(): Boolean {
    val manufacturer = android.os.Build.MANUFACTURER.lowercase(java.util.Locale.getDefault())
    val autoStartBrands = listOf("xiaomi", "oppo", "vivo", "letv", "honor", "asus", "oneplus", "realme", "poco", "iqoo")
    return autoStartBrands.any { manufacturer.contains(it) }
}

fun openAutoStartSettings(context: Context) {
    try {
        val intents = arrayOf(
            Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(android.content.ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"))
        )
        for (intent in intents) {
            if (context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        }
        Toast.makeText(context, "Please allow Auto Start in App Settings manually", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Please allow Auto Start in App Settings manually", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val privacyPolicyUrl = "https://hariom2008h.github.io/Shorts-blocker-pages/#privacy"
    var isLoadingWebPage by remember { mutableStateOf(true) }
    var webLoadFailed by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler { onNavigateBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoadingWebPage && !webLoadFailed) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!webLoadFailed) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoadingWebPage = false
                                    }

                                    override fun onReceivedError(
                                        view: android.webkit.WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            isLoadingWebPage = false
                                            webLoadFailed = true
                                        }
                                    }
                                }
                                loadUrl(privacyPolicyUrl)
                            }
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Privacy-First Architecture",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Shorts Blocker operates entirely on your device. It utilizes Android Accessibility Services solely to detect and intercept short-form video algorithms (YouTube Shorts, Instagram Reels, and Snapchat Spotlight).\n\n• Zero Data Tracking: We do not collect, monitor, store, or transmit your search history, personal messages, or screen contents.\n• Local Logic: All blocking heuristics and cooldown timers are calculated completely offline.\n• Minimal Permissions: Permissions granted to this app are used strictly to provide the mindful friction overlay.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl))
                                            context.startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("View Full Policy Online ($privacyPolicyUrl)", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("I Understand", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpFAQScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val helpUrl = "https://hariom2008h.github.io/Shorts-blocker-pages/#help"
    var isLoadingWebPage by remember { mutableStateOf(true) }
    var webLoadFailed by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler { onNavigateBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoadingWebPage && !webLoadFailed) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!webLoadFailed) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoadingWebPage = false
                                    }

                                    override fun onReceivedError(
                                        view: android.webkit.WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            isLoadingWebPage = false
                                            webLoadFailed = true
                                        }
                                    }
                                }
                                loadUrl(helpUrl)
                            }
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            FAQItem("How does Shorts Blocker work?", "When you swipe into YouTube Shorts, Instagram Reels, or Snapchat Spotlight, the blocker detects the screen and displays an intentional scroll speed-breaker prompt requesting your friction password.")
                        }
                        item {
                            FAQItem("Why is Accessibility Service required?", "Android Accessibility enables the app to know which screen you are viewing without needing invasive system permissions or internet access. It is processed 100% locally on your phone.")
                        }
                        item {
                            FAQItem("Why is Auto-Start / Battery Unrestricted required?", "OEM Android operating systems (like MIUI/HyperOS, ColorOS, One UI) frequently stop background services to conserve power. Granting unrestricted battery access ensures the blocker remains active after locks and reboots.")
                        }
                        item {
                            FAQItem("What is Daily Relief Time?", "You can configure a daily allowance of mindful short-form viewing. Once this allowance is exhausted, strict scroll friction is enforced.")
                        }
                        item {
                            FAQItem("Does the app send any of my data online?", "No. The app does not transmit any screen content or personal activity. It works completely on-device without cloud telemetry.")
                        }
                        item {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl))
                                        context.startActivity(browserIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("View Full Help & FAQs Online ($helpUrl)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FAQItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context ->
            android.widget.TextView(context).apply {
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setTextColor(color.toArgb())
            }
        },
        update = { 
            it.setTextColor(color.toArgb())
            it.text = androidx.core.text.HtmlCompat.fromHtml(html, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT) 
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(currentVersion: String, onNavigateBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onNavigateBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Shorts Blocker",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Version v$currentVersion",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    val context = LocalContext.current
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Shorts Blocker helps you reclaim your time, attention span, and mental peace by applying friction to infinite short-video doomscrolling on social media.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val browserIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://hariom2008h.github.io/Shorts-blocker-pages/#privacy")
                                    )
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Official Privacy Policy Page", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val browserIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://hariom2008h.github.io/Shorts-blocker-pages/#help")
                                    )
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Official Help & FAQs Page", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDetailsScreen(
    updateInfo: UpdateResult.NewVersionAvailable,
    onNavigateBack: () -> Unit,
    onDownload: () -> Unit,
    downloadProgress: Int?,
    downloadError: String?
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Update Available") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "A new version is ready!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Current: v${updateInfo.currentVersion}  →  Latest: v${updateInfo.latestVersion}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Release Notes:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    HtmlText(
                        html = updateInfo.releaseNotes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
                
                if (downloadError != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Error: $downloadError",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (downloadProgress != null) {
                        Text(
                            text = if (downloadProgress == 100) "Download complete. Starting install..." else "Downloading: $downloadProgress%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.width(280.dp).align(Alignment.CenterHorizontally).height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Download & Install", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
