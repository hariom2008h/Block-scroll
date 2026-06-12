package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.GradientTop
import com.example.ui.theme.GradientBottom
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val context = LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
        var showSplash by remember { mutableStateOf(true) }
        var showOnboarding by remember { mutableStateOf(sharedPrefs.getBoolean("show_onboarding", true)) }

        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1800)
            showSplash = false
        }

        if (showSplash) {
            LoadingScreen()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(GradientTop, GradientBottom)))
            ) {
                if (showOnboarding) {
                    OnboardingScreen(onFinish = {
                        sharedPrefs.edit().putBoolean("show_onboarding", false).apply()
                        showOnboarding = false
                    })
                } else {
                    MainAppScreen()
                }
            }
        }
      }
    }
  }
}



@Composable
fun BadgeItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
    }
}

fun shareStatsImage(context: android.content.Context, streakDays: Int, blockedCount: Int, timeSavedStr: String) {
    val width = 1080
    val height = 1080
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    val h = height.toFloat()
    val w = width.toFloat()
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
    }
    
    val bgColors = intArrayOf(
        android.graphics.Color.parseColor("#4B0082"),
        android.graphics.Color.parseColor("#121212")
    )
    paint.shader = android.graphics.LinearGradient(
        0f, 0f, 0f, h,
        bgColors, null, android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 100f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("SHORTS BLOCKER", w / 2, 200f, paint)
    
    paint.color = android.graphics.Color.CYAN
    paint.textSize = 50f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    canvas.drawText("Reclaiming focus & digital detox!", w / 2, 300f, paint)

    paint.color = android.graphics.Color.parseColor("#33FFFFFF")
    canvas.drawRoundRect(100f, 400f, w - 100f, 950f, 40f, 40f, paint)
    
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 140f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    canvas.drawText("$streakDays DAYS", w / 2, 550f, paint)
    
    paint.textSize = 50f
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    canvas.drawText("Current Focus Streak", w / 2, 630f, paint)
    
    paint.color = android.graphics.Color.YELLOW
    paint.textSize = 60f
    canvas.drawText("$blockedCount Shorts Blocked", w / 2, 750f, paint)
    
    paint.color = android.graphics.Color.parseColor("#A8E6CF")
    paint.textSize = 55f
    canvas.drawText("Time Saved: $timeSavedStr", w / 2, 850f, paint)
    
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = java.io.FileOutputStream("$cachePath/share_stats.png")
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        val imagePath = java.io.File(context.cacheDir, "images/share_stats.png")
        val contentUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imagePath)
        
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(contentUri, "image/png")
            putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
            putExtra(android.content.Intent.EXTRA_TEXT, "See my detox progress! 🚀 Try Shorts Blocker and reclaim your time too! #ShortsBlocker #DigitalDetox")
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share your progress"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}