package com.example

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME,
    HOW_IT_WORKS,
    NOTIFICATION,
    OVERLAY,
    ACCESSIBILITY,
    BATTERY_OPTIMIZATION,
    AUTO_START,
    ALL_SET
}

@Composable
fun ShortsBlockerOnboardingScreen(
    modifier: Modifier = Modifier,
    onFinishOnboarding: () -> Unit
) {
    val steps = remember {
        val list = mutableListOf(
            OnboardingStep.WELCOME,
            OnboardingStep.HOW_IT_WORKS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(OnboardingStep.NOTIFICATION)
        }
        list.add(OnboardingStep.OVERLAY)
        list.add(OnboardingStep.ACCESSIBILITY)
        if (needsAutoStart()) {
            list.add(OnboardingStep.AUTO_START)
        } else {
            list.add(OnboardingStep.BATTERY_OPTIMIZATION)
        }
        list.add(OnboardingStep.ALL_SET)
        list
    }

    val pagerState = rememberPagerState(pageCount = { steps.size })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityGranted by remember { mutableStateOf(isAccessibilityPermissionGranted(context)) }
    var isBatteryGranted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }
    var isAutoStartClicked by remember { mutableStateOf(false) }
    var isNotificationGranted by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val requestNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isNotificationGranted = isGranted || (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU)
    }

    LaunchedEffect(Unit) {
        while (true) {
            isOverlayGranted = Settings.canDrawOverlays(context)
            isAccessibilityGranted = isAccessibilityPermissionGranted(context)
            isNotificationGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                isBatteryGranted = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                isBatteryGranted = true
            }
            kotlinx.coroutines.delay(600)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isOverlayGranted = Settings.canDrawOverlays(context)
                isAccessibilityGranted = isAccessibilityPermissionGranted(context)
                isNotificationGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    isBatteryGranted = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                } else {
                    isBatteryGranted = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showHelpDialog by remember { mutableStateOf(false) }

    if (showHelpDialog) {
        val currentStep = steps[pagerState.currentPage]
        val helpTitle = when (currentStep) {
            OnboardingStep.NOTIFICATION -> "How to allow notifications"
            OnboardingStep.OVERLAY -> "How to allow overlay"
            OnboardingStep.ACCESSIBILITY -> "How to allow accessibility"
            OnboardingStep.BATTERY_OPTIMIZATION -> "How to fix battery optimization"
            OnboardingStep.AUTO_START -> "How to enable auto start"
            else -> "Help"
        }
        val helpText = when (currentStep) {
            OnboardingStep.NOTIFICATION -> "Tap 'Grant Notification' and choose 'Allow' so blocker service stays alive in background."
            OnboardingStep.OVERLAY -> "Tap 'Grant Overlay', select 'Shorts Blocker', and turn ON 'Allow display over other apps'."
            OnboardingStep.ACCESSIBILITY -> "Tap 'Grant Accessibility', locate 'Downloaded apps' or 'Shorts Blocker', and turn it ON to allow scroll detection."
            OnboardingStep.BATTERY_OPTIMIZATION -> "Select 'Unrestricted' or 'Don't optimize' so the blocker isn't stopped by power saver."
            OnboardingStep.AUTO_START -> "Allow Shorts Blocker in Auto-Start settings so protection remains active after phone restart."
            else -> "Follow the on-screen action to proceed."
        }

        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text(helpTitle, fontWeight = FontWeight.Bold) },
            text = { Text(helpText) },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Page Indicator Dots
                    Row(
                        modifier = Modifier.padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pagerState.pageCount) { iteration ->
                            val isSelected = pagerState.currentPage == iteration
                            val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            val width = if (isSelected) 24.dp else 8.dp
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .height(6.dp)
                                    .width(width)
                            )
                        }
                    }

                    // Bottom Action Row
                    val currentStep = steps[pagerState.currentPage]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 500.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Action
                        if (pagerState.currentPage > 0 && currentStep != OnboardingStep.ALL_SET) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }
                        } else if (currentStep != OnboardingStep.WELCOME && currentStep != OnboardingStep.ALL_SET) {
                            TextButton(
                                onClick = { showHelpDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Rounded.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Help")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Right Action
                        when (currentStep) {
                            OnboardingStep.WELCOME -> {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Get Started", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                            OnboardingStep.HOW_IT_WORKS -> {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Continue", fontWeight = FontWeight.Bold)
                                }
                            }
                            OnboardingStep.NOTIFICATION -> {
                                if (!isNotificationGranted) {
                                    Button(
                                        onClick = {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                requestNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Grant Permission", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Next", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            OnboardingStep.OVERLAY -> {
                                if (!isOverlayGranted) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Cannot open Overlay Settings", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Grant Overlay", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Next", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            OnboardingStep.ACCESSIBILITY -> {
                                if (!isAccessibilityGranted) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Cannot open Accessibility Settings", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Grant Accessibility", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Next", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            OnboardingStep.BATTERY_OPTIMIZATION -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!isBatteryGranted) {
                                        TextButton(
                                            onClick = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Skip")
                                        }
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                        data = android.net.Uri.parse("package:${context.packageName}")
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    try {
                                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                        context.startActivity(intent)
                                                    } catch (e2: Exception) {}
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Fix Battery", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Next", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            OnboardingStep.AUTO_START -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!isAutoStartClicked) {
                                        TextButton(
                                            onClick = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Skip")
                                        }
                                        Button(
                                            onClick = {
                                                isAutoStartClicked = true
                                                openAutoStartSettings(context)
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Enable Auto Start", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Next", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            OnboardingStep.ALL_SET -> {
                                Button(
                                    onClick = onFinishOnboarding,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Start Protecting", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            userScrollEnabled = false
        ) { page ->
            val step = steps[page]
            val (isGranted, isPermissionStep) = when (step) {
                OnboardingStep.NOTIFICATION -> isNotificationGranted to true
                OnboardingStep.OVERLAY -> isOverlayGranted to true
                OnboardingStep.ACCESSIBILITY -> isAccessibilityGranted to true
                OnboardingStep.BATTERY_OPTIMIZATION -> isBatteryGranted to true
                OnboardingStep.AUTO_START -> isAutoStartClicked to true
                else -> false to false
            }

            when (step) {
                OnboardingStep.WELCOME -> OnboardingPage(
                    title = "Take Back Control",
                    description = "Shorts Blocker introduces mindful friction to break dopamine loops before mindless scrolling consumes your day.",
                    icon = Icons.Rounded.Shield,
                    animationType = "bounce"
                )
                OnboardingStep.HOW_IT_WORKS -> OnboardingPage(
                    title = "How It Works",
                    description = "When you open Shorts, Reels, or Spotlight, we intercept the feed and require your discipline password to proceed.",
                    icon = Icons.Rounded.Lock,
                    animationType = "none"
                )
                OnboardingStep.NOTIFICATION -> OnboardingPage(
                    title = "Notification Access",
                    description = "Required to keep the blocker service active in the background and alert you of cooldown statuses.",
                    icon = Icons.Rounded.Notifications,
                    animationType = "pulse",
                    isGranted = isGranted,
                    isPermissionStep = isPermissionStep
                )
                OnboardingStep.OVERLAY -> OnboardingPage(
                    title = "Display Over Other Apps",
                    description = "Enables the prompt overlay to appear over YouTube, Instagram, and Snapchat when a short video is detected.",
                    icon = Icons.Rounded.Layers,
                    animationType = "pulse",
                    isGranted = isGranted,
                    isPermissionStep = isPermissionStep
                )
                OnboardingStep.ACCESSIBILITY -> OnboardingPage(
                    title = "Accessibility Service",
                    description = "Detects when short-video reels open so friction can be applied. All processing occurs 100% locally on your phone.",
                    icon = Icons.Rounded.Security,
                    animationType = "pulse",
                    isGranted = isGranted,
                    isPermissionStep = isPermissionStep
                )
                OnboardingStep.BATTERY_OPTIMIZATION -> OnboardingPage(
                    title = "Background Protection",
                    description = "Prevent the system from terminating the blocker in the background when your screen is locked.",
                    icon = Icons.Rounded.BatteryChargingFull,
                    animationType = "pulse",
                    isGranted = isGranted,
                    isPermissionStep = isPermissionStep
                )
                OnboardingStep.AUTO_START -> OnboardingPage(
                    title = "Auto-Start Permission",
                    description = "Keep protection active seamlessly even after your device restarts.",
                    icon = Icons.Rounded.RocketLaunch,
                    animationType = "pulse",
                    isGranted = isGranted,
                    isPermissionStep = isPermissionStep
                )
                OnboardingStep.ALL_SET -> OnboardingPage(
                    title = "You're All Set!",
                    description = "Your mind and focus are now protected. Let's reclaim your time.",
                    icon = Icons.Rounded.CheckCircle,
                    animationType = "none",
                    isGranted = true,
                    isPermissionStep = false
                )
            }
        }
    }
}

private fun isAccessibilityPermissionGranted(context: Context): Boolean {
    var isEnabled = false
    try {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == context.packageName) {
                isEnabled = true
                break
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    if (!isEnabled) {
        try {
            val enabledServicesStr = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            isEnabled = enabledServicesStr?.contains(context.packageName) == true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return isEnabled
}

@Composable
fun OnboardingPage(
    title: String,
    description: String,
    icon: ImageVector,
    animationType: String = "none",
    isGranted: Boolean = false,
    isPermissionStep: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        var offsetY = 0f
        var scale = 1f

        if (animationType == "bounce") {
            val infiniteTransition = rememberInfiniteTransition(label = "bounce")
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bounce_y"
            )
            offsetY = offset
        } else if (animationType == "pulse") {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val s by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )
            scale = s
        }

        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow / soft ring
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .offset(y = offsetY.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Inner icon card
                Surface(
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Optional Status Chip for permission steps
        if (isPermissionStep) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isGranted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isGranted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGranted) "Permission Granted" else "Required for Blocker",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
    }
}
