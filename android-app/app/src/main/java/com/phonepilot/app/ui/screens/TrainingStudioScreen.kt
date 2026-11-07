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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.models.TaskEntity
import com.phonepilot.app.ui.components.EmptyState
import com.phonepilot.app.ui.components.ErrorState
import com.phonepilot.app.ui.components.LoadingState
import com.phonepilot.app.ui.components.PrimaryButton
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.components.SecondaryButton
import com.phonepilot.app.ui.navigation.AppRoutes
import com.phonepilot.app.ui.theme.Space
import com.phonepilot.app.ui.training.TrainingStudioStateHolder
import com.phonepilot.app.ui.training.TrainingStudioUiState

@Composable
fun TrainingStudioScreen(
    taskId: Long = 0,
    onNavigateTo: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember { TrainingStudioStateHolder(context, scope) }
    val uiState by stateHolder.uiState.collectAsStateWithLifecycle()

    var taskName by remember { mutableStateOf("") }
    var taskDescription by remember { mutableStateOf("") }

    LaunchedEffect(taskId) {
        if (taskId > 0L) stateHolder.selectTask(taskId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "استديو التدريب",
            subtitle = "إنشاء وإدارة مهام التسجيل التدريبية",
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
                    text = "مهمة جديدة",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    label = { Text(text = "اسم المهمة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = taskDescription,
                    onValueChange = { taskDescription = it },
                    label = { Text(text = "وصف مختصر (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = "إنشاء مهمة",
                    enabled = taskName.isNotBlank() && !uiState.isBusy,
                    loading = uiState.isBusy,
                    onClick = {
                        stateHolder.createTask(taskName, taskDescription)
                        taskName = ""
                        taskDescription = ""
                    },
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

        // بطاقة حالة التعلم للمهمة النشطة
        uiState.activeTask?.let { activeTask ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.medium),
                    verticalArrangement = Arrangement.spacedBy(Space.small),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "حالة التعلم: ${activeTask.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "عدد العروض المسجلة: ${uiState.demonstrationCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            uiState.skillState?.let { state ->
                                Text(
                                    text = "حالة المهارة: $state",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    uiState.learningMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        PrimaryButton(
                            text = "بدء تحليل التعلم",
                            enabled = uiState.demonstrationCount >= 2 && !uiState.isLearning && !uiState.isBusy,
                            loading = uiState.isLearning,
                            onClick = { stateHolder.startLearning() },
                        )
                        SecondaryButton(
                            text = "تسجيل عرض جديد",
                            enabled = !uiState.isBusy,
                            onClick = { onNavigateTo(AppRoutes.AgentControl) },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "المهام (${uiState.tasks.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            uiState.activeTask?.let { active ->
                Text(
                    text = "النشطة: ${active.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        when {
            uiState.isLoading && uiState.tasks.isEmpty() -> {
                LoadingState(modifier = Modifier.weight(1f))
            }

            uiState.errorMessage != null && uiState.tasks.isEmpty() -> {
                ErrorState(
                    message = uiState.errorMessage ?: "تعذر تحميل المهام",
                    onRetry = { stateHolder.refresh() },
                    modifier = Modifier.weight(1f),
                )
            }

            uiState.tasks.isEmpty() -> {
                EmptyState(
                    title = "لا توجد مهام تدريبية",
                    description = "أنشئ مهمة من الحقل أعلاه لبدء التسجيل وربطها بلوحة التحكم",
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xSmall),
                ) {
                    items(items = uiState.tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            isActive = task.id == uiState.activeTask?.id,
                            onSelect = { stateHolder.selectTask(task.id) },
                        )
                    }
                }
            }
        }

        SecondaryButton(
            text = "فتح التحكم بالوكيل",
            onClick = { onNavigateTo(AppRoutes.AgentControl) },
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    isActive: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
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
                    text = task.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = task.description?.takeIf { it.isNotBlank() } ?: "بدون وصف",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "المهمة النشطة",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
