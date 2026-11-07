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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Event
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.models.TaskRecordingEntity
import com.phonepilot.app.ui.components.EmptyState
import com.phonepilot.app.ui.components.ErrorState
import com.phonepilot.app.ui.components.LoadingState
import com.phonepilot.app.ui.components.PrimaryButton
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.components.SecondaryButton
import com.phonepilot.app.ui.components.StatCard
import com.phonepilot.app.ui.dashboard.DashboardStateHolder
import com.phonepilot.app.ui.dashboard.DashboardUiState
import com.phonepilot.app.ui.navigation.AppRoutes
import com.phonepilot.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(onNavigateTo: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember { DashboardStateHolder(context, scope) }
    val uiState by stateHolder.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "لوحة التحكم",
            subtitle = "نظرة عامة على حالة التسجيل والأحداث",
        )

        when {
            uiState.isLoading && uiState.recordings.isEmpty() -> {
                LoadingState(
                    message = "جاري تحميل البيانات المحلية...",
                    modifier = Modifier.weight(1f),
                )
            }

            uiState.errorMessage != null && uiState.recordings.isEmpty() -> {
                ErrorState(
                    message = uiState.errorMessage ?: "تعذر تحميل البيانات المحلية",
                    onRetry = { stateHolder.refresh() },
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                DashboardContent(
                    uiState = uiState,
                    onPrimaryAction = {
                        when {
                            uiState.isRecording -> stateHolder.stopRecording()
                            !uiState.hasTask -> stateHolder.createDefaultTask()
                            else -> stateHolder.startRecording()
                        }
                    },
                    onNavigateTo = onNavigateTo,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onPrimaryAction: () -> Unit,
    onNavigateTo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
        ) {
            StatCard(
                title = "حالة التسجيل",
                value = recordingStateLabel(uiState.recordingState),
                icon = Icons.Default.Circle,
                accentColor = recordingStateColor(uiState.recordingState),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = "الأحداث المسجلة",
                value = uiState.acceptedCount.toString(),
                subtitle = "محفوظ ${uiState.savedCount} • فاشل ${uiState.failedCount}",
                icon = Icons.Default.Event,
                modifier = Modifier.weight(1f),
            )
        }

        if (!uiState.serviceEnabled) {
            Text(
                text = "خدمة الوصولية غير مفعلة — لن تُسجَّل أحداث جديدة حتى يتم تفعيلها من شاشة الخصوصية والصلاحيات.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            text = "المهمة النشطة: ${uiState.activeTask?.name ?: "غير محددة"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        uiState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "العمليات السريعة",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        PrimaryButton(
            text = when {
                uiState.isRecording -> "إيقاف التسجيل"
                !uiState.hasTask -> "إنشاء مهمة أساسية"
                else -> "بدء التسجيل"
            },
            loading = uiState.isBusy,
            enabled = !uiState.isBusy,
            onClick = onPrimaryAction,
        )

        SecondaryButton(
            text = "استديو التدريب",
            enabled = !uiState.isBusy,
            onClick = { onNavigateTo(AppRoutes.TrainingStudio) },
        )

        Text(
            text = "أحدث التسجيلات",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (uiState.recordings.isEmpty()) {
            EmptyState(
                title = "لا توجد تسجيلات محفوظة",
                description = "سجّل جلسة جديدة لتظهر هنا مباشرة من قاعدة البيانات",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.xSmall),
            ) {
                items(items = uiState.recordings, key = { it.id }) { recording ->
                    RecordingCard(recording = recording)
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(recording: TaskRecordingEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxSmall),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.medium, vertical = Space.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.small),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "تسجيل #${recording.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "المهمة ${recording.taskId} • ${formatTimestamp(recording.recordedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "التكرار #${recording.iterationNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun recordingStateLabel(state: RecordingState): String = when (state) {
    RecordingState.Idle -> "متوقف"
    is RecordingState.Recording -> "جارٍ"
    RecordingState.Interrupted -> "مقطوع"
    RecordingState.Completed -> "مكتمل"
}

@Composable
private fun recordingStateColor(state: RecordingState) = when (state) {
    RecordingState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    is RecordingState.Recording -> MaterialTheme.colorScheme.primary
    RecordingState.Interrupted -> MaterialTheme.colorScheme.error
    RecordingState.Completed -> MaterialTheme.colorScheme.tertiary
}

private fun formatTimestamp(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
