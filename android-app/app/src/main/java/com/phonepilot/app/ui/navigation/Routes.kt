package com.phonepilot.app.ui.navigation

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

    fun icon(route: String): androidx.compose.ui.graphics.vector.ImageVector = when (route) {
        Dashboard -> androidx.compose.material.icons.Icons.Default.Dashboard
        TrainingStudio -> androidx.compose.material.icons.Icons.Default.School
        SkillLibrary -> androidx.compose.material.icons.Icons.Default.LibraryAdd
        AgentControl -> androidx.compose.material.icons.Icons.Default.Tune
        ExecutionMonitor -> androidx.compose.material.icons.Icons.Default.MonitorHeart
        AiModelsApiSettings -> androidx.compose.material.icons.Icons.Default.Settings
        LogsHistory -> androidx.compose.material.icons.Icons.Default.History
        PrivacyPermissions -> androidx.compose.material.icons.Icons.Default.Shield
        AiAssistant -> androidx.compose.material.icons.Icons.Default.SmartToy
        AgentState -> androidx.compose.material.icons.Icons.Default.People
        AgentApproval -> androidx.compose.material.icons.Icons.Default.CheckCircle
        else -> androidx.compose.material.icons.Icons.Default.Home
    }
}
