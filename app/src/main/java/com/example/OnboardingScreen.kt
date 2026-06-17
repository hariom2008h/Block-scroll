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

@Composable
fun ShortsBlockerOnboardingScreen(
    modifier: Modifier = Modifier,
    onFinishOnboarding: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityGranted by remember { mutableStateOf(isAccessibilityPermissionGranted(context)) }
    var isBatteryIgnored by remember {
        mutableStateOf(
            (context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isOverlayGranted = Settings.canDrawOverlays(context)
                isAccessibilityGranted = isAccessibilityPermissionGranted(context)
                isBatteryIgnored = (context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName)
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    title = "Welcome to Shorts Blocker",
                    description = "Take back control of your time. Stop mindless scrolling before it starts.",
                    icon = Icons.Rounded.Shield,
                    isAnimated = true,
                    animationType = "bounce"
                )
                1 -> OnboardingPage(
                    title = "How It Works",
                    description = "We intercept addictive feeds on YouTube, Instagram, and Snapchat, giving you a chance to pause and exit.",
                    icon = Icons.Rounded.Block,
                    isAnimated = false
                )
                2 -> OnboardingPage(
                    title = "Overlay Permission",
                    description = "We need 'Display over other apps' to show the block screen over the addictive app.",
                    icon = Icons.Rounded.Layers,
                    isAnimated = true,
                    animationType = "pulse"
                )
                3 -> OnboardingPage(
                    title = "Accessibility Permission",
                    description = "To know when you scroll into a short video, we need Accessibility Permission. We do NOT read any personal data.",
                    icon = Icons.Rounded.VisibilityOff,
                    isAnimated = true,
                    animationType = "pulse"
                )
                4 -> OnboardingPage(
                    title = "Background Stability",
                    description = "On phone's like POCO/Xiaomi, please enable 'Auto-start' and set Battery to 'No Restrictions' to keep blocker working.",
                    icon = Icons.Rounded.BatteryChargingFull,
                    isAnimated = true,
                    animationType = "bounce"
                )
                5 -> OnboardingPage(
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
            TextButton(
                onClick = onFinishOnboarding,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text("Skip")
            }

            when (pagerState.currentPage) {
                2 -> {
                    if (!isOverlayGranted) {
                        Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } }) {
                                Text("Next")
                            }
                            Spacer(modifier=Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Grant Overlay")
                            }
                        }
                    } else {
                        Button(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Next")
                        }
                    }
                }
                3 -> {
                    if (!isAccessibilityGranted) {
                         Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(4) } }) {
                                Text("Next")
                            }
                            Spacer(modifier=Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Grant Accessibility")
                            }
                        }
                    } else {
                        Button(
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(4) } },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Next")
                        }
                    }
                }
                4 -> {
                    Column(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             TextButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(5) } }) {
                                Text("Skip")
                            }
                            Spacer(modifier=Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    openAutoStartSettings(context)
                                }
                            ) {
                                Text("Auto-start Settings")
                            }
                        }
                        
                        if (!isBatteryIgnored) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Battery: No Restriction")
                            }
                        } else {
                             Button(
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(5) } },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Next")
                            }
                        }
                    }
                }
                5 -> {
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

private fun openAutoStartSettings(context: android.content.Context) {
    val intents = listOf(
        Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(android.content.ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
        Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent().setComponent(android.content.ComponentName("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity")),
        Intent().setComponent(android.content.ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"))
    )

    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // Check next
        }
    }
    
    // Fallback to application details if no specific intent works
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Total fallback
    }
}

// Simple helper inside here just to detect if it's on without throwing errors, 
// using the same logic we've used in the rest of the application
private fun isAccessibilityPermissionGranted(context: android.content.Context): Boolean {
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabledServices?.contains(context.packageName) == true
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
