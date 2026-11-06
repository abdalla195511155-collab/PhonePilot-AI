package com.phonepilot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepilot.app.components.StatCard
import com.phonepilot.app.components.ScreenHeader
import com.phonepilot.app.components.PrimaryButton
import com.phonepilot.app.components.SecondaryButton
import com.phonepilot.app.components.EmptyState
import com.phonepilot.app.repository.RecordingRepository
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.models.TaskRecordingEntity
import com.phonepilot.app.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(onNavigateTo: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val state = RecordingRepository.recordingState.value
    val acceptedCount = RecordingRepository.acceptedCount.value
    val recordings: List<TaskRecordingEntity> = RecordingRepository.getAllRecordings()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.medium),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "لوحة التحكم",
            subtitle = "نظرة عامة على حالة التسجيل والأحداث",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            StatCard(
                title = "حالة التسجيل",
                value = when (state) {
                    is RecordingState.Idle -> "متوقف"
                    is RecordingState.Recording -> "جارٍ"
                    is RecordingState.Interrupted -> "مقطوع"
                    is RecordingState.Completed -> "مكتمل"
                    is RecordingState.Stopping -> "جارٍ الإيقاف..."
                    is RecordingState.Error -> "خطأ"
                },
                icon = androidx.compose.material.icons.Icons.Default.Circle,
                accentColor = when (state) {
                    is RecordingState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                    is RecordingState.Recording -> MaterialTheme.colorScheme.primary
                    is RecordingState.Interrupted -> MaterialTheme.colorScheme.error
                    is RecordingState.Completed -> MaterialTheme.colorScheme.tertiary
                    is RecordingState.Stopping -> MaterialTheme.colorScheme.primary
                    is RecordingState.Error -> MaterialTheme.colorScheme.error
                },
            )
            StatCard(
                title = "الأحداث المسجلة",
                value = acceptedCount.toString(),
                subtitle = "إجمالي الأحداث المقبولة",
                icon = androidx.compose.material.icons.Icons.Default.Event,
            )
        }

        Text(
            text = "العمليات السريعة",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        PrimaryButton(
            text = when (state) {
                is RecordingState.Idle -> "بدء التسجيل"
                is RecordingState.Recording -> "إيقاف التسجيل"
                is RecordingState.Interrupted -> "استئناف التسجيل"
                is RecordingState.Completed -> "تسجيل جديد"
                is RecordingState.Stopping -> "جارٍ الإيقاف..."
                is RecordingState.Error -> "إعادة التشغيل"
            },
            onClick = {
                when (state) {
                    is RecordingState.Idle -> {
                        RecordingRepository.startRecording(taskId = 1L, iterationNumber = 1)
                    }
                    is RecordingState.Recording -> {
                        scope.launch { RecordingRepository.stopRecording() }
                    }
                    is RecordingState.Interrupted -> {
                        RecordingRepository.startRecording(taskId = 1L, iterationNumber = 1)
                    }
                    is RecordingState.Completed -> {
                        RecordingRepository.startRecording(taskId = 1L, iterationNumber = 1)
                    }
                    is RecordingState.Stopping -> { }
                    is RecordingState.Error -> {
                        RecordingRepository.startRecording(taskId = 1L, iterationNumber = 1)
                    }
                }
            },
            enabled = state !is RecordingState.Stopping,
        )

        SecondaryButton(
            text = "استديو التدريب",
            onClick = { onNavigateTo("training_studio") },
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.large))

        Text(
            text = "آخر التسجيلات",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (recordings.isEmpty()) {
            EmptyState(
                title = "لا توجد تسجيلات",
                description = "ابدأ تسجيلًا جديدًا ليظهر هنا سجل التسجيلات",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = recordings, key = { it.id.toString() }) { recording ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xSmall),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = 1.dp,
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.medium, vertical = Space.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "تسجيل #${recording.id}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            androidx.compose.foundation.Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "التكرار #${recording.iterationNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
