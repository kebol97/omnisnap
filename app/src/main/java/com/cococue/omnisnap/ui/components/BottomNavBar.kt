package com.cococue.omnisnap.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Scanner : Screen("scanner", "Scan", Icons.Default.DocumentScanner)
    object TimestampCamera : Screen("timestamp_camera", "Timestamp", Icons.Default.CameraAlt)
    object Files : Screen("files", "My Files", Icons.Default.Folder)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Scanner,
    Screen.TimestampCamera,
    Screen.Files,
    Screen.Settings
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(screen.route) },
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) }
            )
        }
    }
}
