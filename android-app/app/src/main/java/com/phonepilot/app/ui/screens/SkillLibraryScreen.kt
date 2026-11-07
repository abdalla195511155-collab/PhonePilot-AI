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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.data.SkillRepository
import com.phonepilot.app.models.SkillEntity
import com.phonepilot.app.models.SkillLifecycleState
import com.phonepilot.app.ui.components.EmptyState
import com.phonepilot.app.ui.components.PrimaryButton
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.navigation.AppRoutes
import com.phonepilot.app.ui.theme.Space

@Composable
fun SkillLibraryScreen(onNavigateTo: (String) -> Unit) {
    val skills by SkillRepository.skills.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "مكتبة المهارات",
            subtitle = "المهارات المتعلَّمة من جلسات التسجيل",
        )

        when {
            skills.isEmpty() -> {
                EmptyState(
                    title = "لا توجد مهارات متعلّمة",
                    description = "أنشئ مهمة وسجّل عروضاً متعددة في استديو التدريب لبدء استخراج المهارات",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Space.xSmall),
                ) {
                    items(items = skills, key = { it.id }) { skill ->
                        SkillCard(
                            skill = skill,
                            onPromoteToReview = {
                                // promote to review - يتطلب State holder للتحديث
                            },
                            onArchive = {
                                // archive - يتطلب State holder للتحديث
                            },
                        )
                    }
                }
            }
        }

        PrimaryButton(
            text = "بدء تسجيل مهمة",
            onClick = { onNavigateTo(AppRoutes.TrainingStudio) },
        )
    }
}

@Composable
private fun SkillCard(
    skill: SkillEntity,
    onPromoteToReview: () -> Unit,
    onArchive: () -> Unit,
) {
    val state = skill.lifecycleState

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
                        text = "المستوى: ${skill.level}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "الحالة: ${state.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when (state) {
                            SkillLifecycleState.READY -> MaterialTheme.colorScheme.primary
                            SkillLifecycleState.FAILED -> MaterialTheme.colorScheme.error
                            SkillLifecycleState.NEEDS_REVIEW -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
                when (state) {
                    SkillLifecycleState.NEEDS_REVIEW -> {
                        TextButton(
                            onClick = { onPromoteToReview() },
                        ) {
                            Text(text = "إرسال للمراجعة")
                        }
                    }
                    SkillLifecycleState.FAILED -> {
                        TextButton(
                            onClick = { onArchive() },
                        ) {
                            Text(text = "أرشفة")
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}
