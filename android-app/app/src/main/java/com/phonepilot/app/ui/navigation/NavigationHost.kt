package com.phonepilot.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.navigation.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phonepilot.app.ui.screens.AiAssistantScreen
import com.phonepilot.app.ui.screens.AiModelsApiSettingsScreen
import com.phonepilot.app.ui.screens.AgentControlScreen
import com.phonepilot.app.ui.screens.DashboardScreen
import com.phonepilot.app.ui.screens.ExecutionMonitorScreen
import com.phonepilot.app.ui.screens.LogsHistoryScreen
import com.phonepilot.app.ui.screens.PrivacyPermissionsScreen
import com.phonepilot.app.ui.screens.SkillLibraryScreen
import com.phonepilot.app.ui.screens.TrainingStudioScreen

@Composable
fun AppNavigation(
    onRouteChange: ((String) -> Unit)? = null,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.Dashboard,
    ) {
        composable(route = AppRoutes.Dashboard) {
            DashboardScreen(onNavigateTo = { route ->
                navController.navigate(route)
                onRouteChange?.invoke(route)
            })
        }
        composable(
            route = AppRoutes.TrainingStudio,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) {
            val taskId = navController.currentBackStackEntryAsState()?.arguments?.getLong("taskId") ?: 0L
            TrainingStudioScreen(taskId = taskId, onNavigateTo = { route ->
                navController.navigate(route)
                onRouteChange?.invoke(route)
            })
        }
        composable(route = AppRoutes.SkillLibrary) {
            SkillLibraryScreen(onNavigateTo = { route ->
                navController.navigate(route)
                onRouteChange?.invoke(route)
            })
        }
        composable(route = AppRoutes.AgentControl) {
            AgentControlScreen(onNavigateTo = { route ->
                navController.navigate(route)
                onRouteChange?.invoke(route)
            })
        }
        composable(route = AppRoutes.ExecutionMonitor) {
            ExecutionMonitorScreen(onNavigateTo = { route ->
                navController.navigate(route)
                onRouteChange?.invoke(route)
            })
        }
        composable(route = AppRoutes.AiModelsApiSettings) {
            AiModelsApiSettingsScreen(onNavigateTo = { route ->
                navController.navigate(route)
                onRouteChange?.invoke(route)
            })
        }
        composable(route = AppRoutes.LogsHistory) {
            LogsHistoryScreen(onNavigateTo = { route ->
                navController.navigate(route)
                onRouteChange?.invoke(route)
            })
        }
        composable(route = AppRoutes.PrivacyPermissions) {
            PrivacyPermissionsScreen(onNavigateTo = { route ->
                navController.navigate(route)
                onRouteChange?.invoke(route)
            })
        }
        composable(route = AppRoutes.AiAssistant) {
            AiAssistantScreen(onNavigateTo = { route ->
                navController.navigate(route)
                onRouteChange?.invoke(route)
            })
        }
    }
}
