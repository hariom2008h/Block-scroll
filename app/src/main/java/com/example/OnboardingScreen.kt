package com.example

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ArrowForward
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> OnboardingPage(
                        icon = Icons.Rounded.Lock,
                        title = "Take Back Control",
                        description = "Shorts Blocker helps you break free from the endless scrolling loop and reclaim your focused time."
                    ) {
                        AnimatedLockVisual()
                    }
                    1 -> OnboardingPage(
                        icon = Icons.Rounded.Info,
                        title = "Why Accessibility?",
                        description = "To block short videos, we need permission to see when you've opened a target app like YouTube or Instagram."
                    ) {
                        AnimatedShieldVisual()
                    }
                    2 -> OnboardingPage(
                        icon = Icons.Rounded.Check,
                        title = "Enable The Service",
                        description = "When prompted, scroll down, find 'Shorts Blocker', and turn on the switch to start blocking."
                    ) {
                        AnimatedHandVisual()
                    }
                }
            }
            
            // Bottom bar with indicators and buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
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
                        if (pagerState.currentPage < 2) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinish()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(if (pagerState.currentPage == 2) "Get Started" else "Next", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(if (pagerState.currentPage == 2) Icons.Rounded.Check else Icons.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
fun OnboardingPage(icon: ImageVector, title: String, description: String, visual: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            visual()
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
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
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
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
