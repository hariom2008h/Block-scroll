package com.example

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReliefBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        DailyReliefContent(onDismiss)
    }
}

@Composable
private fun DailyReliefContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
    }

    var reliefMinutes by remember {
        mutableIntStateOf(sharedPrefs.getInt("daily_relief_minutes", 0))
    }

    var usedReliefMs by remember { mutableLongStateOf(sharedPrefs.getLong("used_relief_ms", 0L)) }
    var lastReliefDate by remember { mutableStateOf(sharedPrefs.getString("last_relief_date", "") ?: "") }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            usedReliefMs = sharedPrefs.getLong("used_relief_ms", 0L)
            lastReliefDate = sharedPrefs.getString("last_relief_date", "") ?: ""
        }
    }

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = remember(usedReliefMs) { sdf.format(Date()) }
    
    val currentUsedMs = if (today == lastReliefDate) usedReliefMs else 0L
    val remainingMs = maxOf(0L, (reliefMinutes * 60 * 1000L) - currentUsedMs)
    
    val remainingMinutes = remainingMs / 1000 / 60
    val remainingSeconds = (remainingMs / 1000) % 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Timer,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Daily Allowance",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set an uninterrupted time limit for daily viewing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        CircularSlider(
            value = reliefMinutes,
            onValueChange = { newValue ->
                reliefMinutes = newValue
                sharedPrefs.edit().putInt("daily_relief_minutes", newValue).apply()
            },
            modifier = Modifier
                .size(240.dp)
                .padding(16.dp),
            maxValue = 30
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Time Remaining Today",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (reliefMinutes == 0) {
                    Text(
                        text = "Disabled",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = remainingMinutes,
                            transitionSpec = {
                                if (targetState < initialState) {
                                    slideInVertically { height -> -height } + fadeIn() togetherWith
                                    slideOutVertically { height -> height } + fadeOut()
                                } else {
                                    slideInVertically { height -> height } + fadeIn() togetherWith
                                    slideOutVertically { height -> -height } + fadeOut()
                                }.using(SizeTransform(clip = false))
                            }
                        ) { targetMins ->
                            Text(
                                text = String.format(Locale.getDefault(), "%02d", targetMins),
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        AnimatedContent(
                            targetState = remainingSeconds,
                            transitionSpec = {
                                if (targetState < initialState) {
                                    slideInVertically { height -> -height } + fadeIn() togetherWith
                                    slideOutVertically { height -> height } + fadeOut()
                                } else {
                                    slideInVertically { height -> height } + fadeIn() togetherWith
                                    slideOutVertically { height -> -height } + fadeOut()
                                }.using(SizeTransform(clip = false))
                            }
                        ) { targetSecs ->
                            Text(
                                text = String.format(Locale.getDefault(), "%02d", targetSecs),
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Done")
        }
    }
}

@Composable
fun CircularSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxValue: Int = 30
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    var currentAngle by remember { mutableFloatStateOf(if (maxValue > 0) (value.toFloat() / maxValue) * 360f else 0f) }
    
    // Sync currentAngle if value is changed externally
    LaunchedEffect(value) {
        if (maxValue > 0) {
            val targetAngle = (value.toFloat() / maxValue) * 360f
            if (Math.abs(targetAngle - currentAngle) > 1f) {
                currentAngle = targetAngle
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val touchAngle = atan2(change.position.y - center.y, change.position.x - center.x)
                        var angle = Math.toDegrees(touchAngle.toDouble()).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        
                        // Prevent jumping from 360 to 0 and vice versa
                        val currentMod = currentAngle % 360f
                        var delta = angle - currentMod
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        
                        val newTotalAngle = (currentAngle + delta).coerceIn(0f, 360f)
                        currentAngle = newTotalAngle
                        
                        val newValue = ((currentAngle / 360f) * maxValue).roundToInt().coerceIn(0, maxValue)
                        onValueChange(newValue)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val touchAngle = atan2(offset.y - center.y, offset.x - center.x)
                        var angle = Math.toDegrees(touchAngle.toDouble()).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        
                        val newValue = ((angle / 360f) * maxValue).roundToInt().coerceIn(0, maxValue)
                        onValueChange(newValue)
                    }
                }
        ) {
            center = Offset(size.width / 2, size.height / 2)
            val strokeWidth = 28.dp.toPx()
            val radius = size.minDimension / 2 - strokeWidth / 2

            // Track
            drawCircle(
                color = trackColor.copy(alpha = 0.5f),
                radius = radius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress Gradient
            val sweepAngle = currentAngle
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = gradientColors,
                        center = center
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
            }
            
            // Thumb
            val thumbAngle = Math.toRadians((sweepAngle - 90).toDouble())
            val thumbX = center.x + radius * cos(thumbAngle).toFloat()
            val thumbY = center.y + radius * sin(thumbAngle).toFloat()
            
            // Outer glow of thumb
            drawCircle(
                color = primaryColor.copy(alpha = 0.2f),
                radius = 24.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            
            drawCircle(
                color = onPrimaryColor,
                radius = 18.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = primaryColor,
                radius = 18.dp.toPx(),
                center = Offset(thumbX, thumbY),
                style = Stroke(width = 4.dp.toPx())
            )
        }
        
        // Text in center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                        slideOutVertically { height -> -height } + fadeOut()
                    } else {
                        slideInVertically { height -> -height } + fadeIn() togetherWith
                        slideOutVertically { height -> height } + fadeOut()
                    }.using(SizeTransform(clip = false))
                }
            ) { targetValue ->
                Text(
                    text = "$targetValue",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "MINS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )
        }
    }
}
