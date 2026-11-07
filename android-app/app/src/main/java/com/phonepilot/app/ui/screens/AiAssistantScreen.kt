package com.phonepilot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.phonepilot.app.data.ai.RateLimitFormatter
import com.phonepilot.app.data.ai.chat.ChatMessage
import com.phonepilot.app.data.ai.chat.ChatRole
import com.phonepilot.app.ui.ai.AiAssistantStateHolder
import com.phonepilot.app.ui.ai.AiAssistantUiState
import com.phonepilot.app.ui.components.PrimaryButton
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.components.SecondaryButton
import com.phonepilot.app.ui.navigation.AppRoutes
import com.phonepilot.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiAssistantScreen(onNavigateTo: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember { AiAssistantStateHolder(context, scope) }
    val uiState by stateHolder.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showClearDialog by remember { mutableStateOf(false) }

    val visibleMessages = uiState.messages.filter { it.role != ChatRole.SYSTEM }

    LaunchedEffect(visibleMessages.size, uiState.streamingText) {
        val target = visibleMessages.size + if (uiState.streamingText.isNotEmpty()) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        ScreenHeader(
            title = "المساعد الذكي",
            subtitle = "محادثة حقيقية عبر المزود والنموذج المحدد",
        )

        ChatStatusCard(
            uiState = uiState,
            onNavigateTo = onNavigateTo,
            onToggleStreaming = stateHolder::toggleStreaming,
        )

        when {
            uiState.provider == null || uiState.model == null -> {
                Text(
                    text = "اختر مزودًا ونموذجًا من شاشة إدارة المزودين لبدء المحادثة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            visibleMessages.isEmpty() && uiState.streamingText.isEmpty() -> {
                Text(
                    text = "لا توجد رسائل بعد. اكتب سؤالك بالأسفل — سيُرسل إلى النموذج المحدد فعليًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Unit
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.xSmall),
        ) {
            items(items = visibleMessages) { message ->
                ChatBubble(message = message)
            }
            if (uiState.streamingText.isNotEmpty()) {
                item {
                    ChatBubble(
                        message = ChatMessage(
                            role = ChatRole.ASSISTANT,
                            content = uiState.streamingText,
                        ),
                        streaming = true,
                    )
                }
            }
        }

        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(Space.small),
                    verticalArrangement = Arrangement.spacedBy(Space.xxSmall),
                ) {
                    Text(
                        text = error.userMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        TextButton(onClick = stateHolder::retry, enabled = !uiState.isSending) {
                            Text("إعادة المحاولة")
                        }
                        TextButton(onClick = stateHolder::dismissError) {
                            Text("إغلاق")
                        }
                    }
                }
            }
        }

        uiState.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        TextField(
            value = uiState.input,
            onValueChange = stateHolder::setInput,
            label = { Text(text = "سؤالك") },
            placeholder = { Text(text = "مثال: كيف أبدأ تسجيل مهمة جديدة؟") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4,
            enabled = !uiState.isSending,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
        ) {
            if (uiState.isSending) {
                SecondaryButton(
                    text = "إلغاء الطلب",
                    onClick = stateHolder::cancel,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PrimaryButton(
                    text = "إرسال",
                    enabled = uiState.canSend,
                    onClick = stateHolder::send,
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(
                text = "مسح",
                enabled = visibleMessages.isNotEmpty() && !uiState.isSending,
                onClick = { showClearDialog = true },
                modifier = Modifier.weight(1f),
            )
        }

        UsageRow(
            success = uiState.usage.success,
            failure = uiState.usage.failure,
            lastAt = uiState.usage.lastRequestAtEpochMs,
            limitText = uiState.provider?.let { RateLimitFormatter.describe(it.accountRateLimit) },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("مسح المحادثة") },
            text = { Text("سيُحذف سجل هذه المحادثة المحلي نهائيًا. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        stateHolder.clearConversation()
                        showClearDialog = false
                    },
                ) { Text("مسح") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun ChatStatusCard(
    uiState: AiAssistantUiState,
    onNavigateTo: (String) -> Unit,
    onToggleStreaming: () -> Unit,
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
            Text(
                text = if (uiState.provider != null && uiState.model != null) {
                    "${uiState.provider.name} • ${uiState.model.displayName}"
                } else {
                    "لم يتم اختيار مزود ونموذج"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    uiState.provider == null || uiState.model == null -> "افتح إدارة المزودين للاختيار"
                    uiState.model.capabilities.isEmpty() -> "القدرات غير معلنة من المصدر"
                    else -> "القدرات: " + uiState.model.capabilities
                        .distinctBy { it.capability }
                        .joinToString(" • ") { it.capability.label() }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.streamingSupported) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "بث الرد (موثق لهذا النموذج)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = uiState.useStreaming,
                        onCheckedChange = { onToggleStreaming() },
                    )
                }
            }
            Text(
                text = "تغيير المزود أو النموذج",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = Space.xxSmall)
                    .clickable { onNavigateTo(AppRoutes.AiModelsApiSettings) },
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, streaming: Boolean = false) {
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(Space.small),
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                message.error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Space.small, vertical = Space.xSmall),
                verticalArrangement = Arrangement.spacedBy(Space.xxSmall),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (streaming) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = Space.xxSmall)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageRow(
    success: Int,
    failure: Int,
    lastAt: Long?,
    limitText: String?,
) {
    val last = lastAt?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    } ?: "—"
    Text(
        text = "الاستخدام: نجاح $success • فشل $failure • آخر طلب $last" +
            (limitText?.let { " • الحد المعلن: $it" } ?: ""),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun com.phonepilot.app.data.ai.ModelCapability.label(): String = when (this) {
    com.phonepilot.app.data.ai.ModelCapability.TEXT -> "نص"
    com.phonepilot.app.data.ai.ModelCapability.VISION -> "صور"
    com.phonepilot.app.data.ai.ModelCapability.REASONING -> "تفكير"
    com.phonepilot.app.data.ai.ModelCapability.TOOLS -> "أدوات"
    com.phonepilot.app.data.ai.ModelCapability.STREAMING -> "بث"
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.then(Modifier.padding(0.dp)).let { base ->
        androidx.compose.foundation.clickable(
            enabled = true,
            onClick = onClick,
        ).let { base.then(it) }
    }
