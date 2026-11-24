package com.phonepilot.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.currentBackStackEntryAsState
import com.phonepilot.app.data.SkillRepository
import com.phonepilot.app.ui.screens.SettingsHubScreen
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

private val bottomRoutes = listOf(
    AppRoutes.Dashboard,
    AppRoutes.SkillLibrary,
    AppRoutes.AgentControl,
    AppRoutes.Settings,
)

private fun bottomLabel(route: String): String = when (route) {
    AppRoutes.Dashboard -> "الرئيسية"
    AppRoutes.SkillLibrary -> "المهارات"
    AppRoutes.AgentControl -> "الوكيل"
    AppRoutes.Settings -> "الإعدادات"
    else -> AppRoutes.label(route)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    onRouteChange: ((String) -> Unit)? = null,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.Dashboard

    val go: (String) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
            if (route in bottomRoutes) {
                popUpTo(AppRoutes.Dashboard) { saveState = true }
                restoreState = true
            }
        }
        onRouteChange?.invoke(route)
    }

    Scaffold(
        modifier = Modifier.testTag("root"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (currentRoute !in bottomRoutes) {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.testTag("back"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                bottomRoutes.forEach { route ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_$route"),
                        selected = currentRoute == route,
                        onClick = { go(route) },
                        icon = { Icon(imageVector = AppRoutes.icon(route), contentDescription = null) },
                        label = { Text(text = bottomLabel(route)) },
                    )
                }
            }
        },
    ) { innerPadding ->
    NavHost(
        navController = navController,
        startDestination = AppRoutes.Dashboard,
        modifier = Modifier.padding(innerPadding),
    ) {
        composable(route = AppRoutes.Dashboard) {
            DashboardScreen(onNavigateTo = go)
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
            TrainingStudioScreen(taskId = taskId, onNavigateTo = go)
        }
        composable(route = AppRoutes.SkillLibrary) {
            SkillLibraryScreen(onNavigateTo = go)
        }
        composable(route = AppRoutes.AgentControl) {
            AgentControlScreen(onNavigateTo = go)
        }
        composable(route = AppRoutes.ExecutionMonitor) {
            ExecutionMonitorScreen(onNavigateTo = go)
        }
        composable(route = AppRoutes.AiModelsApiSettings) {
            AiModelsApiSettingsScreen(onNavigateTo = go)
        }
        composable(route = AppRoutes.LogsHistory) {
            LogsHistoryScreen(onNavigateTo = go)
        }
        composable(route = AppRoutes.PrivacyPermissions) {
            PrivacyPermissionsScreen(onNavigateTo = go)
        }
        composable(route = AppRoutes.AiAssistant) {
            AiAssistantScreen(onNavigateTo = go)
        }
        composable(route = AppRoutes.AgentState) {
            AgentStateScreen(
                orchestrator = androidx.compose.runtime.remember(
                    com.phonepilot.app.recording.PhonePilotAccessibilityService.instance,
                ) {
                    AgentOrchestrator(
                        context = context,
                        taskDatabaseHelper = TaskDatabaseHelper.getInstance(context.applicationContext),
                        accessibilityService = com.phonepilot.app.recording.PhonePilotAccessibilityService.instance,
                        scope = scope,
                    )
                },
                onNavigateTo = go,
            )
        }
        composable(route = AppRoutes.Settings) {
            SettingsHubScreen(onNavigateTo = go)
        }
        composable(route = AppRoutes.AgentApproval) {
            AgentApprovalScreen(
                onApprove = { skillId ->
                    SkillRepository.recordSkillApproval(skillId, "APPROVED", System.currentTimeMillis(), true)
                },
                onReject = { skillId ->
                    SkillRepository.recordSkillApproval(skillId, "REJECTED", System.currentTimeMillis(), true)
                },
                onNavigateTo = go,
            )
        }

    }
    }
}
