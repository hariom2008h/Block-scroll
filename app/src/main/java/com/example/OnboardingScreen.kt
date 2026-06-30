package com.example

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
        list.add(OnboardingStep.BATTERY_OPTIMIZATION)
        list.add(OnboardingStep.ALL_SET)
        list
    }
    
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityGranted by remember { mutableStateOf(isAccessibilityPermissionGranted(context)) }
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

    val requestNotificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
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
            kotlinx.coroutines.delay(500) // check every 500ms
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
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var showHelpDialog by remember { mutableStateOf(false) }

        if (showHelpDialog) {
            val currentStep = steps[pagerState.currentPage]
            val helpTitle = when (currentStep) {
                OnboardingStep.NOTIFICATION -> "How to allow notifications"
                OnboardingStep.OVERLAY -> "How to allow overlay"
                OnboardingStep.ACCESSIBILITY -> "How to allow accessibility"
                OnboardingStep.BATTERY_OPTIMIZATION -> "How to fix battery optimization"
                else -> "Help"
            }
            val helpText = when (currentStep) {
                OnboardingStep.NOTIFICATION -> "Tap 'Grant Notification' and select 'Allow' on the popup."
                OnboardingStep.OVERLAY -> "Tap 'Grant Overlay', find 'Shorts Blocker' in the list, and turn on 'Allow display over other apps'."
                OnboardingStep.ACCESSIBILITY -> "Tap 'Grant Accessibility', look for 'Downloaded apps' or 'Installed services', select 'Shorts Blocker', and turn it on. If prompted, allow full control."
                OnboardingStep.BATTERY_OPTIMIZATION -> "Tap 'Fix Battery', select 'No restrictions' or 'Unrestricted' so the app can run in the background. If you have an Auto Start option, please enable it for Shorts Blocker."
                else -> "Please follow the instructions on the screen."
            }
            
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text(helpTitle) },
                text = { Text(helpText) },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false
        ) { page ->
            when (steps[page]) {
                OnboardingStep.WELCOME -> OnboardingPage(
                    title = "Welcome to Shorts Blocker",
                    description = "Take back control of your time. Stop mindless scrolling before it starts.",
                    icon = Icons.Rounded.Shield,
                    isAnimated = true,
                    animationType = "bounce"
                )
                OnboardingStep.HOW_IT_WORKS -> OnboardingPage(
                    title = "How It Works",
                    description = "We intercept addictive feeds on YouTube, Instagram, and Snapchat, giving you a chance to pause and exit.",
                    icon = Icons.Rounded.Block,
                    isAnimated = false
                )
                OnboardingStep.NOTIFICATION -> OnboardingPage(
                    title = "Notifications Permission",
                    description = "We need notification permission to keep the service alive in the background and send you reminders.",
                    icon = Icons.Rounded.Notifications,
                    isAnimated = true,
                    animationType = "pulse"
                )
                OnboardingStep.OVERLAY -> OnboardingPage(
                    title = "Overlay Permission",
                    description = "We need 'Display over other apps' to show the block screen over the addictive app.",
                    icon = Icons.Rounded.Layers,
                    isAnimated = true,
                    animationType = "pulse"
                )
                OnboardingStep.ACCESSIBILITY -> OnboardingPage(
                    title = "Accessibility Permission",
                    description = "To know when you scroll into a short video, we need Accessibility Permission. We do NOT read any personal data.",
                    icon = Icons.Rounded.VisibilityOff,
                    isAnimated = true,
                    animationType = "pulse"
                )
                OnboardingStep.BATTERY_OPTIMIZATION -> OnboardingPage(
                    title = if (needsAutoStart()) "Auto Start & Battery" else "Battery Optimization",
                    description = "Allow the app to run in the background without being killed by the system.",
                    icon = Icons.Rounded.BatteryStd,
                    isAnimated = true,
                    animationType = "pulse"
                )
                OnboardingStep.ALL_SET -> OnboardingPage(
                    title = "You're All Set",
                    description = "Permissions are set. Let's reclaim your time.",
                    icon = Icons.Rounded.CheckCircle,
                    isAnimated = false
                )
            }
        }

        // Pager indicators
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val width = if (pagerState.currentPage == iteration) 24.dp else 12.dp
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .height(8.dp)
                        .width(width)
                )
            }
        }

        // Bottom Actions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            val currentStep = steps[pagerState.currentPage]
            
            if (currentStep != OnboardingStep.ALL_SET && currentStep != OnboardingStep.WELCOME) {
                TextButton(
                    onClick = {
                        showHelpDialog = true
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text("Help")
                }
            }

            when (currentStep) {
                OnboardingStep.NOTIFICATION -> {
                    if (!isNotificationGranted) {
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    requestNotificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Grant Notification")
                        }
                    } else {
                        Button(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Next")
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
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Grant Overlay")
                        }
                    } else {
                        Button(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Next")
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
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Grant Accessibility")
                        }
                    } else {
                        Button(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Next")
                        }
                    }
                }
                OnboardingStep.BATTERY_OPTIMIZATION -> {
                    Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                            Text("Next")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
                                if (needsAutoStart()) {
                                    openAutoStartSettings(context)
                                }
                            }
                        ) {
                            Text("Fix Battery")
                        }
                    }
                }
                OnboardingStep.ALL_SET -> {
                    Button(
                        onClick = onFinishOnboarding,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text("Finish & Start")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

// Simple helper inside here just to detect if it's on without throwing errors, 
// using the same logic we've used in the rest of the application
private fun isAccessibilityPermissionGranted(context: android.content.Context): Boolean {
    var isEnabled = false
    try {
        val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
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
    isAnimated: Boolean,
    animationType: String = "none"
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        var offsetY = 0f
        var scale = 1f

        if (isAnimated) {
            val infiniteTransition = rememberInfiniteTransition()
            if (animationType == "bounce") {
                val offset by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = -20f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                offsetY = offset
            } else if (animationType == "pulse") {
                val s by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                scale = s
            }
        }

        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(y = offsetY.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
