package com.sankare.wifikill.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sankare.wifikill.core.designsystem.component.EmptyState
import com.sankare.wifikill.feature.dashboard.DashboardScreen
import com.sankare.wifikill.feature.devices.DeviceDetailScreen
import com.sankare.wifikill.feature.devices.DevicesScreen
import com.sankare.wifikill.feature.discovery.DiscoveryScreen

@Composable
fun WiFiKillNavHost() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(
            navController = navController,
            startDestination = Destinations.DASHBOARD,
        ) {
            composable(Destinations.DASHBOARD) {
                DashboardScreen(
                    onOpenDiscovery = { navController.navigate(Destinations.DISCOVERY) },
                    onOpenDevices = { navController.navigate(Destinations.DEVICES) },
                )
            }
            composable(Destinations.DISCOVERY) {
                DiscoveryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDevice = { deviceId ->
                        navController.navigate(Destinations.deviceDetail(deviceId))
                    },
                )
            }
            composable(Destinations.DEVICES) {
                DevicesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDevice = { deviceId ->
                        navController.navigate(Destinations.deviceDetail(deviceId))
                    },
                )
            }
            composable(
                route = Destinations.DEVICE_DETAIL,
                arguments = listOf(
                    navArgument(Destinations.DEVICE_ID_ARG) {
                        type = NavType.StringType
                    },
                ),
            ) {
                DeviceDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.MONITORING) {
                PlaceholderScreen("Monitoring")
            }
            composable(Destinations.ACCESS) {
                PlaceholderScreen("Access Management")
            }
            composable(Destinations.ROUTERS) {
                PlaceholderScreen("Router Management")
            }
            composable(Destinations.DIAGNOSTICS) {
                PlaceholderScreen("Diagnostics")
            }
            composable(Destinations.HISTORY) {
                PlaceholderScreen("History")
            }
            composable(Destinations.SETTINGS) {
                PlaceholderScreen("Settings")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    EmptyState(
        title = title,
        message = "This module is part of the next milestone.",
        modifier = Modifier.fillMaxSize(),
    )
}
