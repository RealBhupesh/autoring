package com.priorityring.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.priorityring.app.ui.activity.ActivityScreen
import com.priorityring.app.ui.home.HomeScreen
import com.priorityring.app.ui.macros.MacrosScreen
import com.priorityring.app.ui.settings.SettingsScreen
import com.priorityring.app.ui.theme.LocalReduceMotion
import com.priorityring.app.ui.theme.Motion
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// Type-safe routes: each destination is a @Serializable object (or class, when it has arguments).
@Serializable data object HomeRoute
@Serializable data object MacrosRoute
@Serializable data object ActivityRoute
@Serializable data object SettingsRoute

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, "Home", Icons.Filled.Home),
    Tab(MacrosRoute, MacrosRoute::class, "Macros", Icons.AutoMirrored.Filled.List),
    Tab(ActivityRoute, ActivityRoute::class, "Activity", Icons.Filled.DateRange),
    Tab(SettingsRoute, SettingsRoute::class, "Settings", Icons.Filled.Settings),
)

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val haptics = LocalHapticFeedback.current
    val fadeMs = if (LocalReduceMotion.current) 0 else Motion.SHORT_MS

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            nav.navigate(tab.route) {
                                // Standard bottom-nav behaviour: one copy of each tab, state kept per tab.
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) }, // label below is read instead
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(fadeMs)) },
            exitTransition = { fadeOut(tween(fadeMs)) },
        ) {
            composable<HomeRoute> {
                HomeScreen(onOpenMacros = { nav.navigate(MacrosRoute) { launchSingleTop = true } })
            }
            composable<MacrosRoute> { MacrosScreen() }
            composable<ActivityRoute> { ActivityScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}
