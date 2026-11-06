package com.phonepilot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phonepilot.app.components.AppScaffold
import com.phonepilot.app.components.ScreenHeader
import com.phonepilot.app.components.PrimaryButton
import com.phonepilot.app.components.SecondaryButton
import com.phonepilot.app.ui.theme.Space

@Composable
fun AiModelsApiSettingsScreen(onNavigateTo: (String) -> Unit) {
    val apiEndpoint = remember { mutableStateOf("") }
    val apiKey = remember { mutableStateOf("") }
    val modelName = remember { mutableStateOf("gpt-4") }
    val isEnabled = remember { mutableStateOf(false) }
    val temperature = remember { mutableStateOf(0.7f) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.medium),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "نماذج الذكاء الاصطناعي وإعدادات API",
            subtitle = "تكوين إعدادات الاتصال بنماذج الذكاء الاصطناعي (واجهة مستخدم فقط)",
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
                    text = "تكوين conexión API",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                TextField(
                    value = apiEndpoint.value,
                    onValueChange = { apiEndpoint.value = it },
                    label = { Text(text = "نقطة نهاية API (Endpoint)") },
                    placeholder = { Text(text = "https://api.example.com/v1") },
                    leadingIcon = { androidx.compose.material3.Icon(imageVector = androidx.compose.material.icons.Icons.Default.Link, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                TextField(
                    value = apiKey.value,
                    onValueChange = { apiKey.value = it },
                    label = { Text(text = "مفتاح API") },
                    placeholder = { Text(text = "••••••••") },
                    visualTransformation = androidx.compose.foundation.text.VisualTransformation.Password,
                    leadingIcon = { androidx.compose.material3.Icon(imageVector = androidx.compose.material.icons.Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                TextField(
                    value = modelName.value,
                    onValueChange = { modelName.value = it },
                    label = { Text(text = "اسم النموذج") },
                    placeholder = { Text(text = "gpt-4") },
                    leadingIcon = { androidx.compose.material3.Icon(imageVector = androidx.compose.material.icons.Icons.Default.SmartToy, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = isEnabled.value,
                        onCheckedChange = { isEnabled.value = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        text = "تفعيل الاتصال بالذكاء الاصطناعي",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        androidx.compose.material3.Text(
            text = "ملاحظة: هذه الإعدادات لواجهة المستخدم فقط في هذه المرحلة. لا يتم تنفيذ اتصال فعلي بـ API أو نموذج ذكاء اصطناعي.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )

        PrimaryButton(
            text = "حفظ الإعدادات",
            onClick = {
                // حفظ الإعدادات محلياً (واجهة مستخدم فقط)
            },
            enabled = apiEndpoint.value.isNotBlank() && apiKey.value.isNotBlank(),
        )

        SecondaryButton(
            text = "إعادة تعيين الإعدادات",
            onClick = {
                apiEndpoint.value = ""
                apiKey.value = ""
                modelName.value = "gpt-4"
                isEnabled.value = false
            },
        )
    }
}
