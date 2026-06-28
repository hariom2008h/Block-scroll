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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
            )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text("System Access", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
            Spacer(modifier = Modifier.height(8.dp))
            
            val overlayColor by animateColorAsState(if (isOverlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            val overlayIcon = if (isOverlayGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning

            SettingsListItem(
                icon = Icons.Rounded.Shield,
                title = "Manage Permissions",
                onClick = onNavigateToPermissions
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            Text("Appearance", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
            Spacer(modifier = Modifier.height(8.dp))

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dynamic Color", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Use Android 12+ wallpaper-based colors",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useDynamicColor,
                        onCheckedChange = { onDynamicColorChange(it) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Text("Theme Mode", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))

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
                        Text(label)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            Text("Help and policy", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
                                    
                                    val isOverlayEnabled = Settings.canDrawOverlays(context)
                                    val appVersion = UpdateChecker.getCurrentVersion(context)
                                    val manufacturer = android.os.Build.MANUFACTURER
                                    val model = android.os.Build.MODEL
                                    val androidVersion = android.os.Build.VERSION.RELEASE
                                    
                                    val deviceInfo = """
                                        |
                                        |---
                                        |📱 Device Info:
                                        |Device: $manufacturer $model
                                        |Android Version: $androidVersion
                                        |App Version: $appVersion
                                        |
                                        |⚙️ Settings Status:
                                        |Overlay Permission: ${if (isOverlayEnabled) "ON" else "OFF"}
                                        |Accessibility Service: ${if (isAccessibilityEnabled) "ON" else "OFF"}
                                    """.trimMargin()
                                    
                                    val currentText = feedbackText + "\n" + deviceInfo
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Manage Permissions") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val overlayColor by animateColorAsState(if (isOverlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            val overlayIcon = if (isOverlayGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = overlayIcon,
                    contentDescription = null,
                    tint = overlayColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Overlay Permission",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isOverlayGranted) "Active" else "Required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
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
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Accessibility",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "To intercept scrolls",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                ) {
                    Text("Enable")
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Start / Battery",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Required for background block",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
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
                        openAutoStartSettings(context)
                    },
                ) {
                    Text("Fix")
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
            
            val jsonObject = JSONObject()
            jsonObject.put("chat_id", chatId)
            if (threadId.isNotEmpty()) {
                jsonObject.put("message_thread_id", threadId)
            }
            
            val timeStamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val formattedMessage = """
                🚀 **NEW FEEDBACK RECEIVED**
                ----------------------------------
                📱 **Application:** Shorts Blocker
                👤 **User/Chat ID:** $chatId
                📅 **Date & Time:** $timeStamp

                🎯 **Type:** General Feedback
                🚨 **Priority:** 🟢 Low

                📝 **Message:**
                "$feedback"
                
                📎 **Attachments:** ${imageUris.size}
                -----------------------------
            """.trimIndent()
            
            jsonObject.put("text", formattedMessage)
            
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
            for (uri in imageUris) {
                try {
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
            }
            
            textSuccess && (imageUris.isEmpty() || imagesSuccess)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service & Privacy Policy") },
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "This application respects your privacy and operates locally on your device. It requires Accessibility Services solely for the purpose of detecting and intercepting infinite scrolling features on short-form video platforms. We do not collect, store, or transmit your personal data, scroll history, or any contents of your screen.\n\nBy using this application, you agree to these terms and understand that the app enforces focus by blocking specific UI elements on supported platforms.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OK")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpFAQScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Help & FAQs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FAQItem("How does this app block Shorts?", "The app uses Android's Accessibility Services to detect when you open a short-form video feed (like YouTube Shorts or Instagram Reels) and displays a blocking overlay.")
            }
            item {
                FAQItem("Why do I need to enable Accessibility?", "We need Accessibility permissions to detect which screen of an app you are on without accessing your personal data. This is strictly locally processed.")
            }
            item {
                FAQItem("What does 'Auto Start / Battery' permission do?", "Many device manufacturers kill background apps to save battery. Giving Auto Start and Battery unrestricted access ensures the blocker service stays running in the background so it can block seamlessly when needed.")
            }
            item {
                FAQItem("How can I pause the blocker?", "You can change your session settings and duration in the app's home screen. The app does not completely limit usage but enforces a mindful cooldown time.")
            }
            item {
                FAQItem("Will my data be sent to a server?", "No, all processing happens locally on your device. We respect your privacy and do not transmit your screen content or activity.")
            }
        }
    }
}

@Composable
fun FAQItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                // Use Material 3 onSurfaceVariant text color
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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("About Shorts Blocker") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Shorts Blocker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Version: v$currentVersion",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Shorts Blocker helps you reclaim your time and focus by preventing doomscrolling on addictive social media platforms.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
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
                            modifier = Modifier.fillMaxWidth().height(50.dp),
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
