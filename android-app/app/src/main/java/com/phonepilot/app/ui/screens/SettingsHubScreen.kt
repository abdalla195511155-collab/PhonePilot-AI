package com.phonepilot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.navigation.AppRoutes
import com.phonepilot.app.ui.theme.Space

private data class SettingsEntry(val route: String, val title: String, val description: String)

private val entries = listOf(
    SettingsEntry(
        AppRoutes.AiModelsApiSettings,
        "مزودو الذكاء الاصطناعي ومفاتيح API",
        "أضف مفتاح API واختر النموذج واختبر الاتصال",
    ),
    SettingsEntry(
        AppRoutes.PrivacyPermissions,
        "الخصوصية والأذونات",
        "تفعيل خدمة الوصولية وتصفية البيانات الحساسة",
    ),
    SettingsEntry(
        AppRoutes.AgentApproval,
        "موافقات المهارات",
        "راجع المهارات المستخرجة واعتمدها أو ارفضها",
    ),
    SettingsEntry(
        AppRoutes.AiAssistant,
        "المساعد الذكي",
        "اسأل المساعد عن استخدام التطبيق",
    ),
    SettingsEntry(
        AppRoutes.ExecutionMonitor,
        "مراقب التنفيذ",
        "تابع حالة تنفيذ المهارات",
    ),
    SettingsEntry(
        AppRoutes.LogsHistory,
        "السجلات والتاريخ",
        "سجل الأحداث والعمليات السابقة",
    ),
)

@Composable
fun SettingsHubScreen(onNavigateTo: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        item {
            ScreenHeader(title = "الإعدادات", subtitle = "المفاتيح والأذونات والمراجعة")
        }
        items(items = entries, key = { it.route }) { entry ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_${entry.route}")
                    .clickable { onNavigateTo(entry.route) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Space.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.medium),
                ) {
                    Icon(
                        imageVector = AppRoutes.icon(entry.route),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
