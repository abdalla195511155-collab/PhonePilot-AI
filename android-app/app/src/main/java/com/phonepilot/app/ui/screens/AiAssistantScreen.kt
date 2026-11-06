package com.phonepilot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
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
fun AiAssistantScreen(onNavigateTo: (String) -> Unit) {
    val userInput = remember { mutableStateOf("") }
    val assistantResponse = remember { mutableStateOf("") }
    val isProcessing = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.medium),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.medium),
    ) {
        ScreenHeader(
            title = "المساعد الذكي",
            subtitle = "اطلب المساعدة من الذكاء الاصطناعي في مهام التسجيل والتدريب",
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
                    text = "الدردشة مع المساعد",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.Top,
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = assistantResponse.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.medium))
                TextField(
                    value = userInput.value,
                    onValueChange = { userInput.value = it },
                    label = { Text(text = "اكتب سؤالك هنا...") },
                    placeholder = { Text(text = "مثال: كيف أبدأ تسجيل مهمة جديدة؟") },
                    leadingIcon = { androidx.compose.material3.Icon(imageVector = androidx.compose.material.icons.Icons.Default.Chat, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Space.small))
                PrimaryButton(
                    text = "إرسال",
                    onClick = {
                        if (userInput.value.isNotBlank()) {
                            isProcessing.value = true
                            assistantResponse.value = "جاري معالجة طلبك: ${userInput.value}"
                            // لا يوجد تكامل فعلي مع الذكاء الاصطناعي في هذه المرحلة
                            isProcessing.value = false
                            userInput.value = ""
                        }
                    },
                    enabled = userInput.value.isNotBlank() && !isProcessing.value,
                    loading = isProcessing.value,
                )
                SecondaryButton(
                    text = "مسح المحادثة",
                    onClick = {
                        userInput.value = ""
                        assistantResponse.value = ""
                    },
                )
            }
        }

        androidx.compose.material3.Text(
            text = "ملاحظة: المساعد الذكي في مرحلة التطوير. لا يوجد تكامل فعلي مع نموذج ذكاء اصطناعي خارجي حتى الآن.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}
