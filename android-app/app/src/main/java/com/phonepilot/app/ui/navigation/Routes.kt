package com.phonepilot.app.ui.navigation

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
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
        Dashboard -> Dashboard
        TrainingStudio -> School
        SkillLibrary -> LibraryAdd
        AgentControl -> Tune
        ExecutionMonitor -> MonitorHeart
        AiModelsApiSettings -> Settings
        LogsHistory -> History
        PrivacyPermissions -> Shield
        AiAssistant -> SmartToy
        AgentState -> People
        AgentApproval -> CheckCircle
        else -> Home
    }
}
