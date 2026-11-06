package com.phonepilot.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepilot.app.components.ScreenHeader
import com.phonepilot.app.components.EmptyState
import com.phonepilot.app.models.TaskRecordingEntity
import com.phonepilot.app.repository.RecordingRepository
import com.phonepilot.app.ui.theme.Space

@Composable
fun LogsHistoryScreen(onNavigateTo: (String) -> Unit) {
    val recordings: List<TaskRecordingEntity> = RecordingRepository.getAllRecordings()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.medium),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "السجلات والتاريخ",
            subtitle = "عرض تاريخ التسجيلات والأحداث المسجلة",
        )

        if (recordings.isEmpty()) {
            EmptyState(
                title = "لا توجد سجلات",
                description = "سيظهر هنا سجل التشغيل والتاريخ عند بدء تسجيل المهام",
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
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.small),
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "تسجيل #${recording.id}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "التكرار #${recording.iterationNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "تسجيل ID: ${recording.id} • تم الإنشاء: ${recording.recordedAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
