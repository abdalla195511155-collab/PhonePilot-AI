package com.phonepilot.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MonitorHeart
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
        Dashboard -> Icons.Filled.Home
        TrainingStudio -> Icons.Filled.Build
        SkillLibrary -> Icons.Filled.Add
        AgentControl -> Icons.Filled.Tune
        ExecutionMonitor -> Icons.Filled.MonitorHeart
        AiModelsApiSettings -> Icons.Filled.Settings
        LogsHistory -> Icons.Filled.History
        PrivacyPermissions -> Icons.Filled.Shield
        AiAssistant -> Icons.Filled.SmartToy
        AgentState -> Icons.Filled.People
        AgentApproval -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Home
    }
}
