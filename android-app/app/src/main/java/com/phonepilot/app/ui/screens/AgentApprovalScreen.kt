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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.data.SkillRepository
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.ui.components.EmptyState
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.navigation.AppRoutes
import com.phonepilot.app.ui.theme.Space

@Composable
fun AgentApprovalScreen(
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onNavigateTo: (String) -> Unit,
) {
    val skills by SkillRepository.skills.collectAsStateWithLifecycle()

    val pendingApprovals = skills.filter {
        it.lifecycleState == SkillLifecycleState.VALIDATING ||
            (it.lifecycleState == SkillLifecycleState.NEEDS_REVIEW && it.approvalState == "PENDING")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "موافقات المهارات",
            subtitle = "مراجعة واعتماد المهارات قبل التنفيذ",
        )

        Text(
            text = "المهام قيد المراجعة (${pendingApprovals.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        when {
            pendingApprovals.isEmpty() -> {
                EmptyState(
                    title = "لا توجد مهارات قيد المراجعة",
                    description = "بعد استخراج مهارة جديدة، ستظهر هنا للمراجعة والاعتماد",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Space.xSmall),
                ) {
                    items(items = pendingApprovals, key = { it.id }) { skill ->
                        ApprovalCard(
                            skill = skill,
                            onApprove = { onApprove(skill.id) },
                            onReject = { onReject(skill.id) },
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = { onNavigateTo(AppRoutes.SkillLibrary) },
        ) {
            Text(text = "عرض مكتبة المهارات")
        }
    }
}

@Composable
private fun ApprovalCard(
    skill: SkillEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
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
                        text = skill.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "الحالة: ${skill.lifecycleState} • مستوى الثقة: غير محدد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onReject,
                ) {
                    Text(text = "رفض", style = MaterialTheme.typography.labelSmall)
                }
                androidx.compose.material3.Button(
                    onClick = onApprove,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text(text = "اعتماد")
                }
            }
        }
    }
}
