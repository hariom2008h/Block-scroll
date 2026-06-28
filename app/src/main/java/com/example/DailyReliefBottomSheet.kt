package com.example

import android.content.Context
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    val usedReliefMs = remember {
        sharedPrefs.getLong("used_relief_ms", 0L)
    }
    
    val lastReliefDate = remember {
        sharedPrefs.getString("last_relief_date", "") ?: ""
    }

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = sdf.format(Date())
    
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
                .size(220.dp)
                .padding(16.dp),
            maxValue = 30
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Time Remaining Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (reliefMinutes == 0) "Disabled" else String.format("%02d:%02d", remainingMinutes, remainingSeconds),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
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
    maxValue: Int = 15
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val touchAngle = atan2(change.position.y - center.y, change.position.x - center.x)
                        var angle = Math.toDegrees(touchAngle.toDouble()).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        val newValue = ((angle / 360f) * maxValue).roundToInt().coerceIn(0, maxValue)
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
            val strokeWidth = 24.dp.toPx()
            val radius = size.minDimension / 2 - strokeWidth / 2

            // Track
            drawCircle(
                color = trackColor,
                radius = radius,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress
            val sweepAngle = if (maxValue > 0) (value.toFloat() / maxValue) * 360f else 0f
            if (value > 0) {
                drawArc(
                    color = primaryColor,
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
            
            drawCircle(
                color = onPrimaryColor,
                radius = 16.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = primaryColor,
                radius = 16.dp.toPx(),
                center = Offset(thumbX, thumbY),
                style = Stroke(width = 4.dp.toPx())
            )
        }
        
        // Text in center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$value",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "mins",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
