package com.example

import android.app.Application
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class DailyReliefViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPrefs = application.getSharedPreferences("shorts_blocker_prefs", Context.MODE_PRIVATE)
    
    private val _reliefMinutes = MutableStateFlow(sharedPrefs.getInt("daily_relief_minutes", 0))
    val reliefMinutes = _reliefMinutes.asStateFlow()

    private val _usedReliefMs = MutableStateFlow(sharedPrefs.getLong("used_relief_ms", 0L))
    val usedReliefMs = _usedReliefMs.asStateFlow()
    
    private val _lastReliefDate = MutableStateFlow(sharedPrefs.getString("last_relief_date", "") ?: "")
    val lastReliefDate = _lastReliefDate.asStateFlow()

    private val _isReliefActive = MutableStateFlow(sharedPrefs.getBoolean("is_relief_active", false))
    val isReliefActive = _isReliefActive.asStateFlow()

    fun setReliefMinutes(minutes: Int) {
        _reliefMinutes.value = minutes
        sharedPrefs.edit().putInt("daily_relief_minutes", minutes).apply()
    }

    fun toggleReliefActive() {
        val newState = !_isReliefActive.value
        _isReliefActive.value = newState
        sharedPrefs.edit().putBoolean("is_relief_active", newState).apply()
    }

    fun applyPreset(presetMinutes: Int) {
        setReliefMinutes(presetMinutes)
    }

    suspend fun startTimer() {
        while (true) {
            delay(1000)
            _usedReliefMs.value = sharedPrefs.getLong("used_relief_ms", 0L)
            _lastReliefDate.value = sharedPrefs.getString("last_relief_date", "") ?: ""
            _isReliefActive.value = sharedPrefs.getBoolean("is_relief_active", false)
        }
    }
}

val CobaltBlue = Color(0xFF0047AB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReliefBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        DailyReliefContent(onDismiss)
    }
}

@Composable
private fun DailyReliefContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: DailyReliefViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    )

    val reliefMinutes by viewModel.reliefMinutes.collectAsState()
    val usedReliefMs by viewModel.usedReliefMs.collectAsState()
    val lastReliefDate by viewModel.lastReliefDate.collectAsState()
    val isReliefActive by viewModel.isReliefActive.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startTimer()
    }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember(usedReliefMs) { sdf.format(Date()) }
    
    val currentUsedMs = if (today == lastReliefDate) usedReliefMs else 0L
    val remainingMs = maxOf(0L, (reliefMinutes * 60 * 1000L) - currentUsedMs)

    var showPresets by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Daily Allowance",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Set an uninterrupted time limit for daily viewing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Professional Radial Timer
        Box(contentAlignment = Alignment.Center) {
            CircularSlider(
                value = reliefMinutes,
                onValueChange = { viewModel.setReliefMinutes(it) },
                modifier = Modifier
                    .size(280.dp)
                    .padding(16.dp),
                maxValue = 240 // Allow up to 4 hours (240 mins)
            )
            
            // Center content
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HourglassIcon()
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedNumberOdometer(value = reliefMinutes)
                Text(
                    text = "MINS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Clean Info Display
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    RemainingTimeDisplay(remainingMs = remainingMs)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Resets daily at midnight (12:00 AM)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CobaltBlue,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preset Button
            Box {
                FilledIconButton(
                    onClick = { showPresets = true },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CobaltBlue.copy(alpha = 0.1f),
                        contentColor = CobaltBlue
                    )
                ) {
                    Icon(Icons.Rounded.MoreTime, contentDescription = "Presets")
                }
                
                DropdownMenu(
                    expanded = showPresets,
                    onDismissRequest = { showPresets = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("15 Minutes") },
                        onClick = { viewModel.applyPreset(15); showPresets = false }
                    )
                    DropdownMenuItem(
                        text = { Text("30 Minutes") },
                        onClick = { viewModel.applyPreset(30); showPresets = false }
                    )
                    DropdownMenuItem(
                        text = { Text("1 Hour") },
                        onClick = { viewModel.applyPreset(60); showPresets = false }
                    )
                }
            }
            
            // Done Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // Enable Now Toggle
            FilledIconButton(
                onClick = { viewModel.toggleReliefActive() },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isReliefActive) CobaltBlue else CobaltBlue.copy(alpha = 0.1f),
                    contentColor = if (isReliefActive) Color.White else CobaltBlue
                )
            ) {
                Icon(
                    if (isReliefActive) Icons.Rounded.Power else Icons.Rounded.PowerOff, 
                    contentDescription = "Toggle Relief"
                )
            }
        }
    }
}

@Composable
fun HourglassIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "hourglass_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hourglass_angle"
    )

    Icon(
        imageVector = Icons.Rounded.HourglassBottom,
        contentDescription = null,
        tint = CobaltBlue,
        modifier = Modifier
            .size(28.dp)
            .rotate(angle)
    )
}

@Composable
fun AnimatedNumberOdometer(value: Int) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (targetState > initialState) {
                (slideInVertically(animationSpec = tween(150)) { height -> height } + fadeIn(animationSpec = tween(150))) togetherWith
                (slideOutVertically(animationSpec = tween(150)) { height -> -height } + fadeOut(animationSpec = tween(150)))
            } else {
                (slideInVertically(animationSpec = tween(150)) { height -> -height } + fadeIn(animationSpec = tween(150))) togetherWith
                (slideOutVertically(animationSpec = tween(150)) { height -> height } + fadeOut(animationSpec = tween(150)))
            }.using(SizeTransform(clip = false))
        },
        label = "odometer_anim"
    ) { targetValue ->
        Text(
            text = "$targetValue",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 64.sp,
                fontWeight = FontWeight.Black
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun RemainingTimeDisplay(remainingMs: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = remainingMs,
            transitionSpec = {
                if (targetState < initialState) {
                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                    (slideOutVertically { height -> height } + fadeOut())
                } else {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                    (slideOutVertically { height -> -height } + fadeOut())
                }.using(SizeTransform(clip = false))
            },
            label = "minutes_anim"
        ) { targetRemaining ->
            val targetMins = (targetRemaining / 60000).toInt()
            Text(
                text = String.format(Locale.getDefault(), "%02d", targetMins),
                style = MaterialTheme.typography.displayMedium,
                color = CobaltBlue,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = ":",
            style = MaterialTheme.typography.displayMedium,
            color = CobaltBlue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        AnimatedContent(
            targetState = remainingMs,
            transitionSpec = {
                if (targetState < initialState) {
                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                    (slideOutVertically { height -> height } + fadeOut())
                } else {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                    (slideOutVertically { height -> -height } + fadeOut())
                }.using(SizeTransform(clip = false))
            },
            label = "seconds_anim"
        ) { targetRemaining ->
            val targetSecs = ((targetRemaining % 60000) / 1000).toInt()
            Text(
                text = String.format(Locale.getDefault(), "%02d", targetSecs),
                style = MaterialTheme.typography.displayMedium,
                color = CobaltBlue,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CircularSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxValue: Int = 240
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    
    val startAngle = 135f
    val maxSweep = 270f
    
    var currentSweep by remember { mutableFloatStateOf(if (maxValue > 0) (value.toFloat() / maxValue) * maxSweep else 0f) }
    
    LaunchedEffect(value) {
        if (maxValue > 0) {
            val targetSweep = (value.toFloat() / maxValue) * maxSweep
            if (Math.abs(targetSweep - currentSweep) > 1f) {
                currentSweep = targetSweep
            }
        }
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primary,
        CobaltBlue
    )

    val thumbPaint = remember {
        Paint().apply {
            color = Color.White
            isAntiAlias = true
        }
    }
    val shadowColor = android.graphics.Color.argb(50, 0, 71, 171)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val touchAngle = atan2(change.position.y - center.y, change.position.x - center.x)
                        var angleInDegrees = Math.toDegrees(touchAngle.toDouble()).toFloat()
                        if (angleInDegrees < 0) angleInDegrees += 360f
                        
                        var relativeAngle = angleInDegrees - startAngle
                        if (relativeAngle < 0) relativeAngle += 360f
                        
                        if (relativeAngle > maxSweep) {
                            relativeAngle = if (relativeAngle < maxSweep + (360f - maxSweep) / 2) {
                                maxSweep
                            } else {
                                0f
                            }
                        }
                        
                        val newValue = ((relativeAngle / maxSweep) * maxValue).roundToInt().coerceIn(0, maxValue)
                        onValueChange(newValue)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val touchAngle = atan2(offset.y - center.y, offset.x - center.x)
                        var angleInDegrees = Math.toDegrees(touchAngle.toDouble()).toFloat()
                        if (angleInDegrees < 0) angleInDegrees += 360f
                        
                        var relativeAngle = angleInDegrees - startAngle
                        if (relativeAngle < 0) relativeAngle += 360f
                        
                        if (relativeAngle > maxSweep) {
                            relativeAngle = if (relativeAngle < maxSweep + (360f - maxSweep) / 2) {
                                maxSweep
                            } else {
                                0f
                            }
                        }
                        
                        val newValue = ((relativeAngle / maxSweep) * maxValue).roundToInt().coerceIn(0, maxValue)
                        onValueChange(newValue)
                    }
                }
        ) {
            center = Offset(size.width / 2, size.height / 2)
            val strokeWidth = 20.dp.toPx()
            val radius = size.minDimension / 2 - strokeWidth / 2
            
            val topLeftOffset = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            // Background Track
            drawArc(
                color = trackColor.copy(alpha = 0.4f),
                startAngle = startAngle,
                sweepAngle = maxSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = arcSize,
                topLeft = topLeftOffset
            )

            // Active Progress Gradient
            if (currentSweep > 0f) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset(center.x - radius, center.y + radius),
                        end = Offset(center.x + radius, center.y - radius)
                    ),
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = arcSize,
                    topLeft = topLeftOffset
                )
            }
            
            // White Pearl Handle
            val thumbAngle = Math.toRadians((startAngle + currentSweep).toDouble())
            val thumbX = center.x + radius * cos(thumbAngle).toFloat()
            val thumbY = center.y + radius * sin(thumbAngle).toFloat()
            
            drawIntoCanvas { canvas ->
                thumbPaint.asFrameworkPaint().apply {
                    setShadowLayer(
                        20f,
                        0f,
                        8f,
                        shadowColor
                    )
                }
                canvas.drawCircle(
                    Offset(thumbX, thumbY),
                    24.dp.toPx(),
                    thumbPaint
                )
            }
            
            // Inner pearl border for depth
            drawCircle(
                color = trackColor.copy(alpha = 0.3f),
                radius = 24.dp.toPx(),
                center = Offset(thumbX, thumbY),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

