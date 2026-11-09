package com.phonepilot.app.ui.navigation

import androidx.compose.material.icons.Icons
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
        Dashboard -> Icons.Default.Dashboard
        TrainingStudio -> Icons.Default.School
        SkillLibrary -> Icons.Default.LibraryAdd
        AgentControl -> Icons.Default.Tune
        ExecutionMonitor -> Icons.Default.MonitorHeart
        AiModelsApiSettings -> Icons.Default.Settings
        LogsHistory -> Icons.Default.History
        PrivacyPermissions -> Icons.Default.Shield
        AiAssistant -> Icons.Default.SmartToy
        AgentState -> Icons.Default.People
        AgentApproval -> Icons.Default.CheckCircle
        else -> Icons.Default.Home
    }
}
