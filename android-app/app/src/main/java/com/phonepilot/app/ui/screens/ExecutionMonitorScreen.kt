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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.models.ExecutionState
import com.phonepilot.app.ui.components.EmptyState
import com.phonepilot.app.ui.components.ErrorState
import com.phonepilot.app.ui.components.LoadingState
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.monitor.ExecutionMonitorStateHolder
import com.phonepilot.app.ui.monitor.ExecutionMonitorUiState
import com.phonepilot.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExecutionMonitorScreen(onNavigateTo: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember { ExecutionMonitorStateHolder(context, scope) }
    val uiState by stateHolder.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "مراقب التنفيذ",
            subtitle = "مراقبة التسجيلات وتنفيذ المهارات",
        )

        // حالة الخدمة والتسجيل
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
                verticalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "خدمة الوصولية",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (uiState.serviceConnected) "متصل" else "غير متصل",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.serviceConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "حالة التسجيل",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = recordingStateLabel(uiState.recordingState),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // تنفيذ المهارات (Phase 6)
        Text(
            text = "تنفيذ المهارات (${uiState.skillExecutions.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        when {
            uiState.skillExecutions.isEmpty() -> {
                EmptyState(
                    title = "لا توجد عمليات تنفيذ للمهارات",
                    description = "بعد اعتماد مهارة وتشغيلها، ستظهر سجلات التنفيذ هنا",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Space.xSmall),
                ) {
                    items(items = uiState.skillExecutions, key = { it.execution.id }) { summary ->
                        ExecutionCard(
                            summary = summary,
                        )
                    }
                }
            }
        }

        // التسجيلات (السجل التاريخي)
        Text(
            text = "التسجيلات (${uiState.recordings.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        when {
            uiState.isLoading && uiState.recordings.isEmpty() -> {
                LoadingState(modifier = Modifier.weight(1f))
            }

            uiState.errorMessage != null && uiState.recordings.isEmpty() -> {
                ErrorState(
                    message = uiState.errorMessage ?: "تعذر تحميل البيانات",
                    onRetry = { stateHolder.refresh() },
                    modifier = Modifier.weight(1f),
                )
            }

            uiState.recordings.isEmpty() -> {
                EmptyState(
                    title = "لا توجد تسجيلات",
                    description = "ابدأ تسجيل مهمة لعرض الأحداث هنا",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xSmall),
                ) {
                    items(items = uiState.recordings, key = { it.recording.id }) { summary ->
                        RecordingCard(
                            summary = summary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionCard(summary: ExecutionSummary) {
    val exec = summary.execution
    val state = ExecutionState.valueOf(exec.state)

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.skillTitle ?: "مهارة #${exec.skillId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "الإصدار ${exec.skillVersion} • ${exec.completedSteps}/${exec.totalSteps} خطوة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = executionStateLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state) {
                        ExecutionState.COMPLETED -> MaterialTheme.colorScheme.primary
                        ExecutionState.FAILED -> MaterialTheme.colorScheme.error
                        ExecutionState.RUNNING -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = "البدء: ${formatTimestamp(exec.startedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exec.failureReason != null) {
                Text(
                    text = "السبب: ${exec.failureReason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun RecordingCard(summary: RecordingSummary) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.taskName ?: "مهمة #${summary.recording.taskId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "التكرار #${summary.recording.iterationNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = recordingStateLabel(RecordingState.Idle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "تسجيل #${summary.recording.id} • أحداث: ${summary.eventCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTimestamp(summary.recording.recordedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun executionStateLabel(state: ExecutionState): String = when (state) {
    ExecutionState.PENDING -> "في الانتظار"
    ExecutionState.IDLE -> "متوقف"
    ExecutionState.RUNNING -> "جارٍ التنفيذ"
    ExecutionState.VERIFYING -> "جارٍ التحقق"
    ExecutionState.PAUSED -> "متوقف مؤقتاً"
    ExecutionState.COMPLETED -> "مكتمل"
    ExecutionState.FAILED -> "فشل"
    ExecutionState.CANCELLED -> "ملغي"
    ExecutionState.INTERRUPTED -> "مقطوع"
    ExecutionState.NEEDS_USER_ACTION -> "ينتظر إجراء المستخدم"
}

private fun recordingStateLabel(state: RecordingState): String = when (state) {
    RecordingState.Idle -> "متوقف"
    is RecordingState.Recording -> "جارٍ التسجيل"
    is RecordingState.Interrupted -> "مقطوع"
    RecordingState.Completed -> "مكتمل"
}

private fun formatTimestamp(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
