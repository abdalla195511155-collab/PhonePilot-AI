package com.phonepilot.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepilot.app.components.ScreenHeader
import com.phonepilot.app.components.PrimaryButton
import com.phonepilot.app.components.EmptyState
import com.phonepilot.app.database.TaskDatabaseHelper
import com.phonepilot.app.models.TaskEntity
import com.phonepilot.app.repository.RecordingRepository
import com.phonepilot.app.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
fun TrainingStudioScreen(
    taskId: Long = 0,
    onNavigateTo: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val tasks = TaskDatabaseHelper.getInstance(context).getAllTasks()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.medium),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "استديو التدريب",
            subtitle = "إنشاء وإدارة مهام التسجيل التدريبية",
        )

        PrimaryButton(
            text = "إنشاء مهمة تدريبية جديدة",
            onClick = {
                RecordingRepository.startRecording(taskId = 1L, iterationNumber = 1)
            },
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.medium))

        Text(
            text = "المهام المتاحة",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (tasks.isEmpty()) {
            EmptyState(
                title = "لا توجد مهام تدريبية",
                description = "قم بإنشاء مهمة تدريبية جديدة للبدء",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = tasks, key = { it.id.toString() }) { task ->
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
                                .clickable { onNavigateTo("agent_control") }
                                .padding(horizontal = Space.medium, vertical = Space.small),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = task.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = task.description.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
