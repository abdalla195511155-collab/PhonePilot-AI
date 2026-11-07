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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.models.EventEntity
import com.phonepilot.app.ui.components.EmptyState
import com.phonepilot.app.ui.components.ErrorState
import com.phonepilot.app.ui.components.LoadingState
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.logs.LogRecordingSummary
import com.phonepilot.app.ui.logs.LogsHistoryStateHolder
import com.phonepilot.app.ui.logs.LogsHistoryUiState
import com.phonepilot.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsHistoryScreen(onNavigateTo: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember { LogsHistoryStateHolder(context, scope) }
    val uiState by stateHolder.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "السجلات والتاريخ",
            subtitle = "عرض تاريخ التسجيلات والأحداث المسجلة",
        )

        TextField(
            value = uiState.query,
            onValueChange = stateHolder::setQuery,
            label = { Text(text = "بحث برقم التسجيل أو المهمة") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            uiState.isLoading && uiState.recordings.isEmpty() -> {
                LoadingState(modifier = Modifier.weight(1f))
            }

            uiState.errorMessage != null && uiState.recordings.isEmpty() -> {
                ErrorState(
                    message = uiState.errorMessage ?: "تعذر تحميل السجلات",
                    onRetry = { stateHolder.refresh() },
                    modifier = Modifier.weight(1f),
                )
            }

            uiState.filteredRecordings.isEmpty() -> {
                EmptyState(
                    title = if (uiState.query.isBlank()) "لا توجد سجلات" else "لا نتائج مطابقة",
                    description = if (uiState.query.isBlank()) {
                        "سيظهر هنا سجل التشغيل والتاريخ عند بدء تسجيل المهام"
                    } else {
                        "جرّب رقم تسجيل أو اسم مهمة آخر"
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xSmall),
                ) {
                    items(
                        items = uiState.filteredRecordings,
                        key = { it.recording.id },
                    ) { summary ->
                        LogCard(
                            summary = summary,
                            isExpanded = uiState.expandedRecordingId == summary.recording.id,
                            isLoadingEvents = uiState.isLoadingEvents,
                            events = uiState.events,
                            onToggle = { stateHolder.toggleRecording(summary.recording.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(
    summary: LogRecordingSummary,
    isExpanded: Boolean,
    isLoadingEvents: Boolean,
    events: List<EventEntity>,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
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
                    text = summary.taskName ?: "مهمة #${summary.recording.taskId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "تسجيل #${summary.recording.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "التكرار #${summary.recording.iterationNumber} • أحداث: ${summary.eventCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTimestamp(summary.recording.recordedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isExpanded) {
                Text(
                    text = if (isLoadingEvents) "جاري تحميل الأحداث..." else "الأحداث (${events.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                events.take(30).forEach { event ->
                    Text(
                        text = "#${event.sequenceNumber} ${event.eventType} • ${event.packageName ?: "-"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    event.text?.takeIf { it.isNotBlank() }?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
