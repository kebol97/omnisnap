package com.cococue.omnisnap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cococue.omnisnap.ads.AdManager
import com.cococue.omnisnap.ui.components.BottomNavBar
import com.cococue.omnisnap.ui.components.Screen
import com.cococue.omnisnap.ui.screens.DocumentScannerScreen
import com.cococue.omnisnap.ui.screens.HomeScreen
import com.cococue.omnisnap.ui.screens.MyDocumentsScreen
import com.cococue.omnisnap.ui.screens.PdfSignatureScreen
import com.cococue.omnisnap.ui.screens.PdfToolsScreen
import com.cococue.omnisnap.ui.screens.PhotoConverterScreen
import com.cococue.omnisnap.ui.screens.SettingsScreen
import com.cococue.omnisnap.ui.screens.TimestampCameraScreen
import com.cococue.omnisnap.ui.screens.TimestampPreviewScreen
import com.cococue.omnisnap.ui.theme.OmniSnapTheme
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Fetch Remote Config from GitHub and setup UMP consent & Interstitial preloading
        lifecycleScope.launch {
            AdManager.fetchRemoteConfig()
            AdManager.requestConsent(this@MainActivity) {
                AdManager.loadInterstitialAd(this@MainActivity)
                AdManager.loadRewardedAd(this@MainActivity)
            }
        }

        setContent {
            OmniSnapTheme {
                OmniSnapMainApp()
            }
        }
    }
}

@Composable
fun OmniSnapMainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navigateToFilesAndClearStack = {
        navController.navigate(Screen.Files.route) {
            popUpTo(Screen.Home.route) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }

    Scaffold(
        bottomBar = {
            val mainTabRoutes = listOf(
                Screen.Home.route,
                Screen.Scanner.route,
                Screen.TimestampCamera.route,
                Screen.Files.route,
                Screen.Settings.route
            )
            if (currentRoute in mainTabRoutes) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToScan = { navController.navigate(Screen.Scanner.route) },
                    onNavigateToTimestamp = { navController.navigate(Screen.TimestampCamera.route) },
                    onNavigateToPdfTools = { navController.navigate("pdf_tools") },
                    onNavigateToConverter = { navController.navigate("photo_converter") },
                    onNavigateToFiles = { navController.navigate(Screen.Files.route) }
                )
            }

            composable(Screen.Scanner.route) {
                DocumentScannerScreen(
                    onScanSuccess = { navigateToFilesAndClearStack() }
                )
            }

            composable(Screen.TimestampCamera.route) {
                TimestampCameraScreen(
                    onCaptured = { imagePath, note, locationAddress, lat, lon ->
                        val encodedPath = URLEncoder.encode(imagePath, StandardCharsets.UTF_8.toString())
                        val encodedNote = URLEncoder.encode(note, StandardCharsets.UTF_8.toString())
                        val encodedLoc = URLEncoder.encode(locationAddress, StandardCharsets.UTF_8.toString())
                        val floatLat = lat.toFloat()
                        val floatLon = lon.toFloat()
                        navController.navigate("timestamp_preview?path=$encodedPath&note=$encodedNote&loc=$encodedLoc&lat=$floatLat&lon=$floatLon")
                    }
                )
            }

            composable(
                route = "timestamp_preview?path={path}&note={note}&loc={loc}&lat={lat}&lon={lon}",
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType },
                    navArgument("note") { type = NavType.StringType },
                    navArgument("loc") { type = NavType.StringType },
                    navArgument("lat") { type = NavType.FloatType; defaultValue = 0f },
                    navArgument("lon") { type = NavType.FloatType; defaultValue = 0f }
                )
            ) { backStackEntry ->
                val path = backStackEntry.arguments?.getString("path") ?: ""
                val note = backStackEntry.arguments?.getString("note") ?: ""
                val loc = backStackEntry.arguments?.getString("loc") ?: ""
                val lat = backStackEntry.arguments?.getFloat("lat")?.toDouble() ?: 0.0
                val lon = backStackEntry.arguments?.getFloat("lon")?.toDouble() ?: 0.0
                TimestampPreviewScreen(
                    imagePath = path,
                    initialNote = note,
                    locationAddress = loc,
                    latitude = lat,
                    longitude = lon,
                    onBack = { navController.popBackStack() },
                    onDone = { navigateToFilesAndClearStack() }
                )
            }

            composable(Screen.Files.route) {
                MyDocumentsScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable("pdf_tools") {
                PdfToolsScreen(
                    onNavigateToSignature = { navController.navigate("pdf_signature") },
                    onActionComplete = { navigateToFilesAndClearStack() }
                )
            }

            composable("pdf_signature") {
                PdfSignatureScreen(
                    onDone = { navigateToFilesAndClearStack() }
                )
            }

            composable("photo_converter") {
                PhotoConverterScreen(
                    onActionComplete = { navigateToFilesAndClearStack() }
                )
            }
        }
    }
}
