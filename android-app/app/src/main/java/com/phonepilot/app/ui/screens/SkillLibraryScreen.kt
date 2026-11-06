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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepilot.app.components.AppScaffold
import com.phonepilot.app.components.ScreenHeader
import com.phonepilot.app.components.PrimaryButton
import com.phonepilot.app.ui.theme.Space

data class SkillItem(
    val id: String,
    val title: String,
    val description: String,
    val level: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

val SampleSkills = listOf(
    SkillItem(
        id = "skill_1",
        title = "تسجيل اللمس",
        description = "تعلم تسجيل لمسات الشاشة والتفاعل معها",
        level = "مبتدئ",
        icon = androidx.compose.material.icons.Icons.Default.TouchApp,
    ),
    SkillItem(
        id = "skill_2",
        title = "تسجيل الحركات",
        description = "تسجيل حركات السحب والإفلات والتنقل بين الشاشات",
        level = "متوسط",
        icon = androidx.compose.material.icons.Icons.Default.DragHandle,
    ),
    SkillItem(
        id = "skill_3",
        title = "تسجيل النصوص",
        description = "تسجيل الإدخال النصي والتحقق من صحة الحقول",
        level = "متقدم",
        icon = androidx.compose.material.icons.Icons.Default.Edit,
    ),
    SkillItem(
        id = "skill_4",
        title = "التحقق من الأحداث",
        description = "التحقق من ترتيب الأحداث وصحتها بعد التسجيل",
        level = "مبتدئ",
        icon = androidx.compose.material.icons.Icons.Default.CheckCircle,
    ),
    SkillItem(
        id = "skill_5",
        title = "التنفيذ التلقائي",
        description = "إعادة تنفيذ المهام المسجلة تلقائياً",
        level = "متقدم",
        icon = androidx.compose.material.icons.Icons.Default.Automation,
    ),
)

@Composable
fun SkillLibraryScreen(onNavigateTo: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.medium),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "مكتبة المهارات",
            subtitle = "تعلم المهارات المختلفة لتسجيل وتنفيذ المهام",
        )

        PrimaryButton(
            text = "استكشاف مهارة جديدة",
            onClick = { onNavigateTo("agent_control") },
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.medium))

        Text(
            text = "المهارات المتاحة",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = SampleSkills, key = { it.id }) { skill ->
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
                            .clickable { onNavigateTo("training_studio") }
                            .padding(horizontal = Space.medium, vertical = Space.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = skill.icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(Space.medium))
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = skill.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = skill.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        Text(
                            text = skill.level,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
