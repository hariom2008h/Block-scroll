package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Live permission states
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityActive by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    
    // Check permission status whenever the activity is resumed
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isOverlayGranted = Settings.canDrawOverlays(context)
                isAccessibilityActive = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val pagerState = rememberPagerState(pageCount = { 3 })
    
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> OnboardingPageContent(
                        icon = Icons.Rounded.Lock,
                        title = "Take Back Control",
                        description = "Shorts Blocker helps you break free from the endless scrolling loop and reclaim your focused time.",
                        visual = { AnimatedLockVisual() }
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "To start blocking addictive shorts scrolling, we must enable two system-level access modes.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    1 -> OnboardingPageContent(
                        icon = Icons.Rounded.Info,
                        title = "1. Appear on Top (Overlay)",
                        description = "This allows us to display the high-contrast countdown password popup over addictive scrolling layouts.",
                        visual = { AnimatedShieldVisual() }
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOverlayGranted) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else 
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (isOverlayGranted) Icons.Rounded.Check else Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = if (isOverlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isOverlayGranted) "Overlay Access: ACTIVE" else "Overlay Access: REQUIRED",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isOverlayGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = if (isOverlayGranted) 
                                        "Beautiful! The blocker dialog can now overlap addictive interfaces successfully." 
                                    else 
                                        "Click below, find \"Shorts Blocker\" in the system list, and enable \"Allow display over other apps\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOverlayGranted) 
                                            MaterialTheme.colorScheme.secondary 
                                        else 
                                            MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isOverlayGranted) "Already Granted" else "Grant Overlay Permission", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    2 -> OnboardingPageContent(
                        icon = Icons.Rounded.Check,
                        title = "2. Core Detection Service",
                        description = "Our Accessibility service runs locally to stop addictive feeds. We never collect or send any personal data.",
                        visual = { AnimatedHandVisual() }
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAccessibilityActive) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else 
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (isAccessibilityActive) Icons.Rounded.Check else Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = if (isAccessibilityActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isAccessibilityActive) "Blocker Service: ACTIVE" else "Blocker Service: INACTIVE",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isAccessibilityActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = if (isAccessibilityActive) 
                                        "Fantastic! The short video detection and blocking engine is fully functional." 
                                    else 
                                        "Please tap below → Select \"Downloaded services\" or \"Installed services\" → Choose \"Shorts Blocker\" and turn the switch ON.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAccessibilityActive) 
                                            MaterialTheme.colorScheme.secondary 
                                        else 
                                            MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isAccessibilityActive) "Already Activated" else "Enable Accessibility Service", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            
            // Bottom bar with indicators and buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicators
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == index) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                        )
                    }
                }
                
                Button(
                    onClick = {
                        if (pagerState.currentPage == 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        } else if (pagerState.currentPage == 1) {
                            if (!isOverlayGranted) {
                                android.widget.Toast.makeText(
                                    context, 
                                    "Please grant Overlay (Appear on Top) permission first!", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            }
                        } else {
                            if (!isOverlayGranted || !isAccessibilityActive) {
                                android.widget.Toast.makeText(
                                    context, 
                                    "Both Overlay and Accessibility permissions are required to start blocking!", 
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                if (!isOverlayGranted) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { context.startActivity(intent) } catch (e: Exception) {}
                                } else if (!isAccessibilityActive) {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { context.startActivity(intent) } catch (e: Exception) {}
                                }
                            } else {
                                onFinish()
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(if (pagerState.currentPage == 2) "Get Started" else "Next", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(if (pagerState.currentPage == 2) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(
    icon: ImageVector, 
    title: String, 
    description: String, 
    visual: @Composable () -> Unit,
    extraContent: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            visual()
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        extraContent()
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = android.content.ComponentName(context, ShortsBlockerService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}

@Composable
fun AnimatedLockVisual() {
    val infiniteTransition = rememberInfiniteTransition(label = "Lock")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutQuart),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LockAnim"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        
        drawCircle(
            color = primaryColor.copy(alpha = 0.1f + (floatAnim * 0.1f)),
            radius = 150f + (floatAnim * 50f),
            center = center
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.2f + (floatAnim * 0.1f)),
            radius = 100f + (floatAnim * 30f),
            center = center
        )
        
        val lockWidth = 100f
        val lockHeight = 80f
        val lockLeft = center.x - lockWidth / 2
        val lockTop = center.y
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(lockLeft, lockTop),
            size = Size(lockWidth, lockHeight),
            cornerRadius = CornerRadius(20f, 20f)
        )
        
        val shackleHeight = 70f
        val shackleTop = lockTop - shackleHeight + (floatAnim * 20f) 
        val path = Path().apply {
            moveTo(lockLeft + 20f, lockTop)
            lineTo(lockLeft + 20f, shackleTop + 35f)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = lockLeft + 20f, 
                    top = shackleTop, 
                    right = lockLeft + lockWidth - 20f, 
                    bottom = shackleTop + 70f
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(lockLeft + lockWidth - 20f, lockTop)
        }
        
        drawPath(
            path = path,
            color = secondaryColor,
            style = Stroke(width = 15f)
        )
    }
}

@Composable
fun AnimatedShieldVisual() {
    val infiniteTransition = rememberInfiniteTransition(label = "Shield")
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShieldAnim"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        
        drawCircle(
            color = secondaryColor.copy(alpha = 0.3f * (1f - pulseAnim)),
            radius = 200f * pulseAnim,
            center = center,
            style = Stroke(width = 5f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.5f * (1f - pulseAnim)),
            radius = 150f * pulseAnim,
            center = center,
            style = Stroke(width = 10f)
        )
        
        drawCircle(
            color = primaryColor,
            radius = 70f,
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = 30f,
            center = center
        )
        drawCircle(
            color = primaryColor,
            radius = 15f,
            center = center
        )
    }
}

@Composable
fun AnimatedHandVisual() {
    val infiniteTransition = rememberInfiniteTransition(label = "Hand")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "HandAnim"
    )
    
    val handX = if (progress < 0.3f) {
        100f + (progress / 0.3f) * 50f
    } else if (progress < 0.6f) {
        150f + ((progress - 0.3f) / 0.3f) * 60f
    } else {
        210f + ((progress - 0.6f) / 0.4f) * 50f
    }
    
    val handY = if (progress < 0.3f) {
        200f - (progress / 0.3f) * 100f
    } else if (progress < 0.6f) {
        100f
    } else {
        100f + ((progress - 0.6f) / 0.4f) * 100f
    }
    
    val handAlpha = if (progress < 0.1f) {
        progress / 0.1f
    } else if (progress > 0.8f) {
        1f - ((progress - 0.8f) / 0.2f)
    } else {
        1f
    }
    
    val switchState = progress > 0.4f
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = if (switchState) primaryColor else Color.Gray
    val trackColor = if (switchState) primaryColor.copy(alpha = 0.5f) else surfaceColor
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val switchWidth = 140f
        val switchHeight = 80f
        
        val switchLeft = center.x - switchWidth / 2
        val switchTop = center.y - switchHeight / 2
        
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(switchLeft, switchTop),
            size = Size(switchWidth, switchHeight),
            cornerRadius = CornerRadius(switchHeight / 2, switchHeight / 2)
        )
        
        val thumbLeft = if (switchState) switchLeft + switchWidth - switchHeight else switchLeft
        drawCircle(
            color = thumbColor,
            radius = switchHeight / 2 - 10f,
            center = Offset(thumbLeft + switchHeight / 2, switchTop + switchHeight / 2)
        )
        
        val handCenter = Offset(switchLeft + handX, switchTop + handY)
        drawPath(
            path = Path().apply {
                moveTo(handCenter.x, handCenter.y)
                lineTo(handCenter.x - 20f, handCenter.y + 40f)
                lineTo(handCenter.x + 20f, handCenter.y + 50f)
                close()
            },
            color = Color.DarkGray.copy(alpha = handAlpha)
        )
        drawCircle(
            color = Color.DarkGray.copy(alpha = handAlpha),
            radius = 10f,
            center = handCenter
        )
        
        if (progress in 0.4f..0.5f) {
            drawCircle(
                color = primaryColor.copy(alpha = 0.5f * (1f - ((progress - 0.4f) / 0.1f))),
                radius = 50f * ((progress - 0.4f) / 0.1f),
                center = handCenter,
                style = Stroke(width = 4f)
            )
        }
    }
}
