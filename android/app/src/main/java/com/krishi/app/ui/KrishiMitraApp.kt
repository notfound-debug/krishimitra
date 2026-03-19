/*
 * KrishiMitraApp.kt — Root composable with Option D's dark bottom nav.
 *
 * Bottom nav: dark green background (#071207), lime green active,
 * ⌂ ✦ ◎ ▤ icons, Kannada labels.
 *
 * Navigation: 4 tabs — ಮುಖಪುಟ (Home), ಕೇಳಿ (Ask), ಬೆಲೆ (Prices), ಯೋಜನೆ (Schemes)
 */

package com.krishi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.krishi.app.KrishiApp
import com.krishi.app.data.remote.ApiClient
import com.krishi.app.data.remote.ProfileRequest
import com.krishi.app.ui.screens.ChatScreen
import com.krishi.app.ui.screens.HomeScreen
import com.krishi.app.ui.screens.OnboardingScreen
import com.krishi.app.ui.screens.PricesScreen
import com.krishi.app.ui.screens.SchemesScreen
import com.krishi.app.ui.theme.KrishiColors
import com.krishi.app.ui.viewmodel.ChatViewModel
import com.krishi.app.ui.viewmodel.HomeViewModel

// Nav tabs with D-style icons
sealed class Screen(val route: String, val labelKn: String, val labelEn: String, val icon: String) {
    data object Home : Screen("home", "ಮುಖಪುಟ", "Home", "⌂")
    data object Chat : Screen("chat", "ಕೇಳಿ", "Ask", "✦")
    data object Prices : Screen("prices", "ಬೆಲೆ", "Prices", "◎")
    data object Schemes : Screen("schemes", "ಯೋಜನೆ", "Schemes", "▤")
}

private val tabs = listOf(Screen.Home, Screen.Chat, Screen.Prices, Screen.Schemes)

@Composable
fun KrishiMitraApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as KrishiApp

    // Check onboarding state
    val isOnboarded by app.prefsStore.isOnboarded.collectAsState(initial = null)

    // Show loading while checking onboarding state
    if (isOnboarded == null) return

    // Show onboarding if not completed
    if (isOnboarded == false) {
        val scope = rememberCoroutineScope()
        OnboardingScreen { profile ->
            scope.launch {
                // Save locally (instant)
                app.prefsStore.completeOnboarding(
                    district = profile.district,
                    taluk = profile.taluk,
                    crops = profile.crops,
                    landAcres = profile.landAcres,
                    language = profile.language,
                )
                // Sync to Supabase in background (best-effort)
                try {
                    val deviceId = app.prefsStore.ensureDeviceId()
                    ApiClient.service.createProfile(
                        ProfileRequest(
                            device_id = deviceId,
                            district = profile.district,
                            taluk = profile.taluk,
                            crops = profile.crops,
                            land_acres = profile.landAcres,
                            language = profile.language,
                        )
                    )
                } catch (_: Exception) { /* offline — will sync later */ }
            }
        }
        return
    }

    // Create ViewModels
    val homeViewModel = remember { HomeViewModel(app.digestRepository, app.prefsStore) }
    val chatViewModel = remember { ChatViewModel(app.chatRepository, app.prefsStore) }
    val lang by homeViewModel.language.collectAsState()

    Scaffold(
        bottomBar = {
            // Dark bottom nav from Option D
            NavigationBar(
                containerColor = KrishiColors.PrimaryDark,
                contentColor = KrishiColors.TextOnDark,
                tonalElevation = 0.dp,
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                tabs.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .then(
                                        if (selected) Modifier.background(
                                            KrishiColors.Accent.copy(alpha = 0.2f),
                                            RoundedCornerShape(10.dp),
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = screen.icon,
                                    fontSize = 22.sp,
                                    color = if (selected) KrishiColors.Accent
                                    else KrishiColors.TextOnDark,
                                )
                            }
                        },
                        label = {
                            Text(
                                text = if (lang == "kn") screen.labelKn else screen.labelEn,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) KrishiColors.Accent
                                else KrishiColors.TextOnDark,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(viewModel = homeViewModel)
            }
            composable(Screen.Chat.route) {
                ChatScreen(viewModel = chatViewModel, language = lang)
            }
            composable(Screen.Prices.route) {
                PricesScreen(language = lang)
            }
            composable(Screen.Schemes.route) {
                val district by app.prefsStore.district.collectAsState(initial = "")
                val crops by app.prefsStore.crops.collectAsState(initial = emptyList())
                val landAcres by app.prefsStore.landAcres.collectAsState(initial = "")
                SchemesScreen(
                    language = lang,
                    district = district,
                    crops = crops,
                    landAcres = landAcres,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KrishiColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            fontSize = 18.sp,
            color = KrishiColors.TextSecondary,
        )
    }
}
