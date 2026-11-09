package com.phonepilot.app.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.agent.AgentExecutionState
import com.phonepilot.app.agent.AgentMemoryEntry
import com.phonepilot.app.agent.AgentOrchestrator
import com.phonepilot.app.models.ExecutionState
import com.phonepilot.app.ui.components.EmptyState
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.navigation.AppRoutes
import com.phonepilot.app.ui.theme.Space

@Composable
fun AgentStateScreen(
    orchestrator: AgentOrchestrator,
    onNavigateTo: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember { mutableStateOf(orchestrator.getAgentState()) }
    val recentMemory by remember { mutableStateOf(orchestrator.getRecentMemory(10)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "حالة الوكيل",
            subtitle = "مراقبة حالة الوكيل ejecución الحالي",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
                verticalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Text(
                    text = "الحالة الحالية",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = agentStateLabel(state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = agentStateColor(state),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = agentStateDescription(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "الذاكرة الأخيرة (${recentMemory.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        when {
            recentMemory.isEmpty() -> {
                EmptyState(
                    title = "لا توجد ذاكرة سابقة",
                    description = "بعد تنفيذ الأوامر، ستظهر سجلات الذاكرة هنا",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Space.xSmall),
                ) {
                    items(recentMemory, key = { it.taskId }) { entry ->
                        MemoryEntryCard(entry = entry)
                    }
                }
            }
        }

        TextButton(
            onClick = { onNavigateTo(AppRoutes.AgentControl) },
        ) {
            Text(text = "التحكم في التسجيل")
        }
    }
}

@Composable
private fun MemoryEntryCard(entry: AgentMemoryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.small),
            verticalArrangement = Arrangement.spacedBy(Space.xxSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.intent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = executionStateLabel(entry.executionState),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (entry.executionState) {
                        ExecutionState.COMPLETED -> MaterialTheme.colorScheme.primary
                        ExecutionState.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = "المهمة: ${entry.taskId} • المهارة: ${entry.skillId ?: "غير محددة"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.notes != null) {
                Text(
                    text = "ملاحظات: ${entry.notes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun agentStateLabel(state: AgentExecutionState): String = when (state) {
    AgentExecutionState.IDLE -> "معدّ"
    AgentExecutionState.INTERPRETING_INTENT -> "يُفسر الأمر"
    AgentExecutionState.PLANNING_TASK -> "يخطط المهمة"
    AgentExecutionState.SELECTING_SKILL -> "يختار المهارة"
    AgentExecutionState.WAITING_FOR_APPROVAL -> "ينتظر الموافقة"
    AgentExecutionState.EXECUTING -> "جارٍ التنفيذ"
    AgentExecutionState.COMPLETED -> "مكتمل"
    AgentExecutionState.FAILED -> "فشل"
    AgentExecutionState.CANCELLED -> "ملغي"
}

@Composable
private fun agentStateColor(state: AgentExecutionState) = when (state) {
    AgentExecutionState.IDLE -> MaterialTheme.colorScheme.primary
    AgentExecutionState.EXECUTING -> MaterialTheme.colorScheme.secondary
    AgentExecutionState.COMPLETED -> MaterialTheme.colorScheme.primary
    AgentExecutionState.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun agentStateDescription(state: AgentExecutionState): String = when (state) {
    AgentExecutionState.IDLE -> "الوكيل غير نشط وهو ينتظر الأوامر"
    AgentExecutionState.INTERPRETING_INTENT -> "يُجري تحليل الأمر لفهم النية"
    AgentExecutionState.PLANNING_TASK -> "يخطط لخطوات تنفيذ المهمة"
    AgentExecutionState.SELECTING_SKILL -> "يختار المهارة المناسبة للتنفيذ"
    AgentExecutionState.WAITING_FOR_APPROVAL -> "ينتظر موافقة المستخدم قبل المتابعة"
    AgentExecutionState.EXECUTING -> "ينفذ الخطوات حالياً"
    AgentExecutionState.COMPLETED -> "أكمل التنفيذ بنجاح"
    AgentExecutionState.FAILED -> "فشل في التنفيذ"
    AgentExecutionState.CANCELLED -> "تم إلغاء التنفيذ"
}

private fun executionStateLabel(state: com.phonepilot.app.models.ExecutionState): String = when (state) {
    com.phonepilot.app.models.ExecutionState.IDLE -> "متوقف"
    com.phonepilot.app.models.ExecutionState.RUNNING -> "جارٍ"
    com.phonepilot.app.models.ExecutionState.COMPLETED -> "مكتمل"
    com.phonepilot.app.models.ExecutionState.FAILED -> "فشل"
    com.phonepilot.app.models.ExecutionState.CANCELLED -> "ملغي"
    else -> state.name
}
