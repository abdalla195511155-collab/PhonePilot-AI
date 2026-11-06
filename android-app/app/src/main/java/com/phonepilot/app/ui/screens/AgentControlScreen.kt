package com.phonepilot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepilot.app.components.ScreenHeader
import com.phonepilot.app.components.PrimaryButton
import com.phonepilot.app.components.SecondaryButton
import com.phonepilot.app.repository.RecordingRepository
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
fun AgentControlScreen(onNavigateTo: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val state = RecordingRepository.recordingState.value
    val isMonitoringEnabled = remember { mutableStateOf(true) }
    val isAutoRecoveryEnabled = remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.medium),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "تحكم الوكيل",
            subtitle = "مراقبة وتحكم في سلوك وكيل التسجيل",
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.small),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = 1.dp,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
            ) {
                Text(
                    text = "إعدادات سلوك الوكيل",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = isMonitoringEnabled.value,
                        onCheckedChange = { isMonitoringEnabled.value = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        text = "تفعيل المراقبة المستمرة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = isAutoRecoveryEnabled.value,
                        onCheckedChange = { isAutoRecoveryEnabled.value = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        text = "التعافي التلقائي من الانقطاع",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Text(
            text = "حالة التسجيل الحالية",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = 2.dp,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
            ) {
                Text(
                    text = when (state) {
                        is RecordingState.Idle -> "الوكيل متوقف"
                        is RecordingState.Recording -> "الوكيل يسجل الأحداث حالياً"
                        is RecordingState.Interrupted -> "تم قطع التسجيل - يمكن الاستئناف"
                        is RecordingState.Completed -> "تم اكتمال التسجيل"
                        is RecordingState.Stopping -> "يتم إيقاف التسجيل..."
                        is RecordingState.Error -> "حدث خطأ في الوكيل"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        PrimaryButton(
            text = when (state) {
                is RecordingState.Idle -> "بدء التحكم بالتسجيل"
                is RecordingState.Recording -> "إيقاف التحكم بالتسجيل"
                is RecordingState.Interrupted -> "استئناف التسجيل"
                is RecordingState.Completed -> "بدء تسجيل جديد"
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
            text = "التنقل إلى المكتبة",
            onClick = { onNavigateTo("skill_library") },
        )
    }
}
