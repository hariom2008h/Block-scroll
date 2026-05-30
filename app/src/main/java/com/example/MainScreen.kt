package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class BottomNavItem(val title: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : BottomNavItem("Dashboard", "dashboard", Icons.Rounded.Home)
    object Targets : BottomNavItem("Targets", "targets", Icons.Rounded.List)
    object Settings : BottomNavItem("Settings", "settings", Icons.Rounded.Settings)
}

@Composable
fun MainAppScreen() {
    val navController = rememberNavController()
    
    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Targets,
        BottomNavItem.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = item.icon, contentDescription = item.title) },
                        label = { Text(text = item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Dashboard.route) { DashboardScreen() }
            composable(BottomNavItem.Targets.route) { TargetsScreen() }
            composable(BottomNavItem.Settings.route) { SettingsScreen() }
        }
    }
}
