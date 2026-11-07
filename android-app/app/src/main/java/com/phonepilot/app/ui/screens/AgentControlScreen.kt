package com.phonepilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.models.RecordingState
import com.phonepilot.app.ui.agent.AgentControlStateHolder
import com.phonepilot.app.ui.components.PrimaryButton
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.components.SecondaryButton
import com.phonepilot.app.ui.navigation.AppRoutes
import com.phonepilot.app.ui.theme.Space

@Composable
fun AgentControlScreen(onNavigateTo: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val stateHolder = remember { AgentControlStateHolder(context, scope) }
    val uiState by stateHolder.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "تحكم الوكيل",
            subtitle = "التحكم في التسجيل الفعلي ومراقبة حالة الخدمة",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
                verticalArrangement = Arrangement.spacedBy(Space.xSmall),
            ) {
                Text(
                    text = "حالة الوكيل",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when (uiState.recordingState) {
                        RecordingState.Idle -> "الوكيل متوقف"
                        is RecordingState.Recording -> "الوكيل يسجل الأحداث حالياً"
                        RecordingState.Interrupted -> "تم قطع التسجيل — توجد جلسة تحتاج مراجعة"
                        RecordingState.Completed -> "تم اكتمال التسجيل"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "المقبولة ${uiState.acceptedCount} • المحفوظة ${uiState.savedCount} • الفاشلة ${uiState.failedCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
                verticalArrangement = Arrangement.spacedBy(Space.xSmall),
            ) {
                Text(
                    text = "معلومات التشغيل",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusRow(
                    label = "المهمة النشطة",
                    value = uiState.activeTask?.name ?: "غير محددة — اختر مهمة من استديو التدريب",
                )
                StatusRow(
                    label = "خدمة الوصولية",
                    value = if (uiState.serviceEnabled) "مفعّلة" else "غير مفعّلة",
                )
                StatusRow(
                    label = "الاتصال بالخدمة",
                    value = if (uiState.serviceConnected) "متصلة" else "غير متصلة",
                )
                StatusRow(
                    label = "استرداد معلّق",
                    value = if (uiState.hasPendingRecovery) "نعم" else "لا",
                )
            }
        }

        uiState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        PrimaryButton(
            text = if (uiState.isRecording) "إيقاف التسجيل" else "بدء التسجيل",
            loading = uiState.isBusy,
            enabled = !uiState.isBusy,
            onClick = {
                if (uiState.isRecording) stateHolder.stopRecording() else stateHolder.startRecording()
            },
        )

        if (uiState.hasPendingRecovery) {
            SecondaryButton(
                text = "استعادة الجلسة",
                enabled = !uiState.isBusy,
                onClick = { stateHolder.recoverSession() },
            )
        }

        if (uiState.hasInterruptFailure) {
            SecondaryButton(
                text = "إنهاء الجلسة المتعثرة",
                enabled = !uiState.isBusy,
                onClick = { stateHolder.abandonSession() },
            )
        }

        SecondaryButton(
            text = "التنقل إلى المكتبة",
            onClick = { onNavigateTo(AppRoutes.SkillLibrary) },
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Space.small),
        )
    }
}
