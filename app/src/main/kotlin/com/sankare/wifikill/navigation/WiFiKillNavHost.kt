package com.sankare.wifikill.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.navArgument
import com.sankare.wifikill.R
import com.sankare.wifikill.StartupViewModel
import com.sankare.wifikill.core.designsystem.component.EmptyState
import com.sankare.wifikill.feature.accessmanagement.AccessManagementScreen
import com.sankare.wifikill.feature.dashboard.DashboardScreen
import com.sankare.wifikill.feature.devices.DeviceDetailScreen
import com.sankare.wifikill.feature.devices.DevicesScreen
import com.sankare.wifikill.feature.diagnostics.DiagnosticsScreen
import com.sankare.wifikill.feature.discovery.DiscoveryScreen
import com.sankare.wifikill.feature.monitoring.MonitoringScreen
import com.sankare.wifikill.feature.routermanagement.AddRouterScreen
import com.sankare.wifikill.feature.routermanagement.RouterManagementScreen
import com.sankare.wifikill.feature.settings.OnboardingScreen
import com.sankare.wifikill.feature.settings.SettingsScreen

@Composable
fun WiFiKillNavHost(
    startupViewModel: StartupViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val startDestination by startupViewModel.startDestination.collectAsStateWithLifecycle()

    if (startDestination == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.app_loading))
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(
            navController = navController,
            startDestination = requireNotNull(startDestination),
        ) {
            composable(Destinations.ONBOARDING) {
                OnboardingScreen(
                    onCompleted = {
                        navController.navigate(Destinations.DASHBOARD) {
                            popUpTo(Destinations.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Destinations.DASHBOARD) {
                DashboardScreen(
                    onOpenDiscovery = { navController.navigate(Destinations.DISCOVERY) },
                    onOpenDevices = { navController.navigate(Destinations.DEVICES) },
                    onOpenRouters = { navController.navigate(Destinations.ROUTERS) },
                    onOpenAccess = { navController.navigate(Destinations.ACCESS) },
                    onOpenMonitoring = { navController.navigate(Destinations.MONITORING) },
                    onOpenDiagnostics = { navController.navigate(Destinations.DIAGNOSTICS) },
                    onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
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
                MonitoringScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.ACCESS) {
                AccessManagementScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRouters = { navController.navigate(Destinations.ROUTERS) },
                )
            }
            composable(Destinations.ROUTERS) {
                RouterManagementScreen(
                    onBack = { navController.popBackStack() },
                    onAddRouter = { navController.navigate(Destinations.ROUTER_ADD) },
                )
            }
            composable(Destinations.ROUTER_ADD) {
                AddRouterScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(Destinations.DIAGNOSTICS) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
            }
            composable(Destinations.HISTORY) {
                PlaceholderScreen("History")
            }
            composable(Destinations.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
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
