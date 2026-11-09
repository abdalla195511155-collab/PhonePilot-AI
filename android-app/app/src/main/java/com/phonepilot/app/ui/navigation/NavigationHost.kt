package com.phonepilot.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.ui.screens.AiAssistantScreen
import com.phonepilot.app.ui.screens.AiModelsApiSettingsScreen
import com.phonepilot.app.ui.screens.AgentApprovalScreen
import com.phonepilot.app.ui.screens.AgentControlScreen
import com.phonepilot.app.ui.screens.AgentStateScreen
import com.phonepilot.app.ui.screens.DashboardScreen
import com.phonepilot.app.ui.screens.ExecutionMonitorScreen
import com.phonepilot.app.ui.screens.LogsHistoryScreen
import com.phonepilot.app.ui.screens.PrivacyPermissionsScreen
import com.phonepilot.app.ui.screens.SkillLibraryScreen
import com.phonepilot.app.ui.screens.TrainingStudioScreen
import com.phonepilot.app.agent.AgentOrchestrator

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
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong("taskId") ?: 0L
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
        composable(route = AppRoutes.AgentState) {
            AgentStateScreen(
                orchestrator = AgentOrchestrator(
                    context = LocalContext.current,
                    taskDatabaseHelper = TaskDatabaseHelper.getInstance(LocalContext.current.applicationContext),
                    accessibilityService = null,
                    scope = rememberCoroutineScope(),
                ),
                onNavigateTo = { route ->
                    navController.navigate(route)
                    onRouteChange?.invoke(route)
                },
            )
        }
        composable(route = AppRoutes.AgentApproval) {
            AgentApprovalScreen(
                onApprove = { skillId ->
                    // TODO: استدعاء recordSkillApproval في SkillRepository
                    onRouteChange?.invoke(AppRoutes.SkillLibrary)
                },
                onReject = { skillId ->
                    // TODO: استدعاء recordSkillApproval مع REJECTED
                    onRouteChange?.invoke(AppRoutes.SkillLibrary)
                },
                onNavigateTo = { route ->
                    navController.navigate(route)
                    onRouteChange?.invoke(route)
                },
            )
        }
    }
}
