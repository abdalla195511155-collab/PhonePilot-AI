package com.phonepilot.app.ui.navigation

import androidx.compose.material.icons.filled.CheckCircle as CheckCircleIcon
import androidx.compose.material.icons.filled.Dashboard as DashboardIcon
import androidx.compose.material.icons.filled.History as HistoryIcon
import androidx.compose.material.icons.filled.Home as HomeIcon
import androidx.compose.material.icons.filled.LibraryAdd as LibraryAddIcon
import androidx.compose.material.icons.filled.MonitorHeart as MonitorHeartIcon
import androidx.compose.material.icons.filled.People as PeopleIcon
import androidx.compose.material.icons.filled.School as SchoolIcon
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Shield as ShieldIcon
import androidx.compose.material.icons.filled.SmartToy as SmartToyIcon
import androidx.compose.material.icons.filled.Tune as TuneIcon
import androidx.compose.ui.graphics.vector.ImageVector

object AppRoutes {
    const val Dashboard = "dashboard"
    const val TrainingStudio = "training_studio"
    const val SkillLibrary = "skill_library"
    const val AgentControl = "agent_control"
    const val ExecutionMonitor = "execution_monitor"
    const val AiModelsApiSettings = "ai_models_api_settings"
    const val LogsHistory = "logs_history"
    const val PrivacyPermissions = "privacy_permissions"
    const val AiAssistant = "ai_assistant"
    const val AgentState = "agent_state"
    const val AgentApproval = "agent_approval"

    val allScreens = listOf(
        Dashboard,
        TrainingStudio,
        SkillLibrary,
        AgentControl,
        ExecutionMonitor,
        AiModelsApiSettings,
        LogsHistory,
        PrivacyPermissions,
        AiAssistant,
        AgentState,
        AgentApproval,
    )

    fun label(route: String): String = when (route) {
        Dashboard -> "لوحة التحكم"
        TrainingStudio -> "استديو التدريب"
        SkillLibrary -> "مكتبة المهارات"
        AgentControl -> "تحكم الوكيل"
        ExecutionMonitor -> "مراقب التنفيذ"
        AiModelsApiSettings -> "نماذج الذكاء الاصطناعي وإعدادات API"
        LogsHistory -> "السجلات والتاريخ"
        PrivacyPermissions -> "الخصوصية والأذونات"
        AiAssistant -> "المساعد الذكي"
        AgentState -> "حالة الوكيل"
        AgentApproval -> "موافقات المهارات"
        else -> route
    }

    fun icon(route: String): ImageVector = when (route) {
        Dashboard -> DashboardIcon
        TrainingStudio -> SchoolIcon
        SkillLibrary -> LibraryAddIcon
        AgentControl -> TuneIcon
        ExecutionMonitor -> MonitorHeartIcon
        AiModelsApiSettings -> SettingsIcon
        LogsHistory -> HistoryIcon
        PrivacyPermissions -> ShieldIcon
        AiAssistant -> SmartToyIcon
        AgentState -> PeopleIcon
        AgentApproval -> CheckCircleIcon
        else -> HomeIcon
    }
}
