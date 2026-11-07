package com.phonepilot.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepilot.app.data.ai.AiModelSpec
import com.phonepilot.app.data.ai.AiProtocol
import com.phonepilot.app.data.ai.AiProviderSpec
import com.phonepilot.app.data.ai.CapabilityEvidence
import com.phonepilot.app.data.ai.LimitSource
import com.phonepilot.app.data.ai.LimitWindow
import com.phonepilot.app.data.ai.ModelCapability
import com.phonepilot.app.data.ai.NvidiaCatalog
import com.phonepilot.app.data.ai.RateLimitFormatter
import com.phonepilot.app.data.ai.RateLimitSpec
import com.phonepilot.app.data.ai.UsageCounter
import com.phonepilot.app.ui.ai.AiProvidersStateHolder
import com.phonepilot.app.ui.ai.AiProvidersUiState
import com.phonepilot.app.ui.components.PrimaryButton
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiModelsApiSettingsScreen(onNavigateTo: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember { AiProvidersStateHolder(context, scope) }
    val uiState by stateHolder.uiState.collectAsStateWithLifecycle()

    var showAddProvider by remember { mutableStateOf(false) }
    var showAddModel by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.medium),
    ) {
        item {
            ScreenHeader(
                title = "مزودو الذكاء الاصطناعي",
                subtitle = "كتالوج NVIDIA المجاني + مزودون مخصصون",
            )
        }

        item {
            ProviderSelector(
                uiState = uiState,
                onSelect = stateHolder::selectProvider,
                onAddToggle = {
                    showAddProvider = !showAddProvider
                    showAddModel = false
                },
            )
        }

        uiState.activeProvider?.let { provider ->
            item {
                ProviderDetails(
                    provider = provider,
                    isSyncing = uiState.isSyncing,
                    onSaveKey = { key -> stateHolder.saveKey(provider.id, key) },
                    onClearKey = { stateHolder.clearKey(provider.id) },
                    onSync = { stateHolder.sync(provider.id) },
                    onDelete = { stateHolder.deleteProvider(provider.id) },
                    onToggleAddModel = { showAddModel = !showAddModel },
                    onToggleEnabled = { enabled -> stateHolder.setEnabled(provider.id, enabled) },
                    onTestConnection = { stateHolder.testConnection(provider.id) },
                    onUpdate = { name, baseUrl ->
                        stateHolder.updateProvider(
                            providerId = provider.id,
                            name = name,
                            baseUrl = baseUrl,
                            protocol = provider.protocol,
                            limitRequests = provider.accountRateLimit?.requests,
                            limitWindow = provider.accountRateLimit?.window ?: LimitWindow.HOUR,
                        )
                    },
                )
            }
        }

        uiState.message?.let { message ->
            item {
                MessageRow(text = message, onDismiss = stateHolder::clearMessage)
            }
        }

        item {
            CapabilityFilters(
                selected = uiState.capabilityFilter,
                onToggle = stateHolder::toggleCapability,
                onClear = stateHolder::clearFilters,
            )
        }

        item {
            TextField(
                value = uiState.query,
                onValueChange = stateHolder::setQuery,
                label = { Text(text = "بحث في النماذج") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val models = uiState.visibleModels
        if (models.isEmpty()) {
            item {
                Text(
                    text = if (uiState.activeProvider == null) {
                        "اختر مزودًا أولاً"
                    } else {
                        "لا توجد نماذج مطابقة. مزامنة الكتالوج أو إضافة نموذج يدويًا متاحة من بطاقة المزود."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(items = models, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    isSelected = model.id == uiState.activeModelId,
                    usage = uiState.counters["${uiState.activeProviderId}|${model.id}"],
                    canEdit = uiState.activeProvider?.isBuiltIn == false,
                    onRename = { newName ->
                        stateHolder.renameModel(uiState.activeProviderId ?: "", model.id, newName)
                    },
                    onDelete = {
                        stateHolder.deleteModel(uiState.activeProviderId ?: "", model.id)
                    },
                    onSelect = {
                        stateHolder.selectModel(
                            uiState.activeProviderId ?: return@ModelCard,
                            model.id,
                        )
                    },
                )
            }
        }

        if (showAddModel && uiState.activeProvider?.isBuiltIn == false) {
            item {
                AddModelForm(
                    onAdd = { id, name ->
                        val providerId = uiState.activeProviderId ?: return@AddModelForm
                        if (stateHolder.addManualModel(providerId, id, name)) {
                            showAddModel = false
                        }
                    },
                )
            }
        }

        if (showAddProvider) {
            item {
                AddProviderForm(
                    onAdd = { name, baseUrl, protocol, key, limit, window ->
                        val newId = stateHolder.addProvider(name, baseUrl, protocol, limit, window)
                        if (newId != null) {
                            if (key.isNotBlank()) stateHolder.saveKey(newId, key)
                            stateHolder.selectProvider(newId)
                            showAddProvider = false
                        }
                    },
                    onCancel = { showAddProvider = false },
                )
            }
        }
    }
}

@Composable
private fun ProviderSelector(
    uiState: AiProvidersUiState,
    onSelect: (String) -> Unit,
    onAddToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
    ) {
        uiState.providers.forEach { provider ->
            SelectableChip(
                label = if (provider.isBuiltIn) "NVIDIA" else provider.name,
                selected = provider.id == uiState.activeProviderId,
                onClick = { onSelect(provider.id) },
            )
        }
        SelectableChip(
            label = "+ مزود",
            selected = false,
            onClick = onAddToggle,
        )
    }
}

@Composable
private fun ProviderDetails(
    provider: AiProviderSpec,
    isSyncing: Boolean,
    onSaveKey: (String) -> Boolean,
    onClearKey: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onToggleAddModel: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onUpdate: (String, String) -> Unit,
) {
    var keyInput by remember(provider.id) { mutableStateOf("") }
    var keyMessage by remember(provider.id) { mutableStateOf<String?>(null) }
    var editMode by remember(provider.id) { mutableStateOf(false) }
    var editName by remember(provider.id) { mutableStateOf(provider.name) }
    var editBaseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }

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
                text = provider.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (provider.isBuiltIn) "كتالوج مدمج • OpenAI-compatible" else "مزود مخصص • ${provider.protocol.label()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (provider.enabled) "المزود مفعّل" else "المزود معطّل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = provider.enabled,
                    onCheckedChange = { onToggleEnabled(it) },
                )
            }
            Text(
                text = provider.baseUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (provider.hasApiKey) "المفتاح محفوظ ومشفّر (Android Keystore)" else "لا يوجد مفتاح محفوظ",
                style = MaterialTheme.typography.labelSmall,
                color = if (provider.hasApiKey) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            TextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text(text = "API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
            ) {
                PrimaryButton(
                    text = "حفظ المفتاح",
                    enabled = keyInput.isNotBlank(),
                    onClick = {
                        keyMessage = if (onSaveKey(keyInput)) {
                            keyInput = ""
                            "تم حفظ المفتاح مشفرًا"
                        } else {
                            "تعذر حفظ المفتاح"
                        }
                    },
                )
            }
            if (provider.hasApiKey) {
                SecondaryTextAction(text = "حذف المفتاح", onClick = onClearKey)
            }
            keyMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = "الحدود المعلنة",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = RateLimitFormatter.describe(provider.accountRateLimit)
                    ?: "لم يُعلن هذا المزود حدًا صريحًا. لا يُفترض أي رقم (مثل 200/ساعة) من تلقاء نفسه.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            provider.accountRateLimit?.let { limit ->
                Text(
                    text = "المصدر: ${RateLimitFormatter.sourceLabel(limit.source)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (provider.isBuiltIn) {
                Text(
                    text = NvidiaCatalog.LIMITS_NOTE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "قائمة النماذج الرسمية: ${NvidiaCatalog.MODEL_LIST_URL}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            provider.lastSyncedAtEpochMs?.let { timestamp ->
                Text(
                    text = "آخر مزامنة: ${formatTimestamp(timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            provider.syncError?.let { error ->
                Text(
                    text = "خطأ المزامنة: $error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
            ) {
                PrimaryButton(
                    text = if (isSyncing) "جاري المزامنة..." else "تحديث الكتالوج",
                    enabled = !isSyncing,
                    onClick = onSync,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
            ) {
                SecondaryTextAction(text = "اختبار الاتصال", onClick = onTestConnection)
                if (!provider.isBuiltIn) {
                    SecondaryTextAction(
                        text = if (editMode) "إغلاق التعديل" else "تعديل المزود",
                        onClick = { editMode = !editMode },
                    )
                }
            }

            if (editMode && !provider.isBuiltIn) {
                TextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(text = "اسم المزود") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = editBaseUrl,
                    onValueChange = { editBaseUrl = it },
                    label = { Text(text = "Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xSmall)) {
                    PrimaryButton(
                        text = "حفظ التعديل",
                        enabled = editName.isNotBlank() && editBaseUrl.isNotBlank(),
                        onClick = {
                            onUpdate(editName, editBaseUrl)
                            editMode = false
                        },
                    )
                }
            }

            if (!provider.isBuiltIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
                ) {
                    SecondaryTextAction(text = "إضافة نموذج يدويًا", onClick = onToggleAddModel)
                    SecondaryTextAction(text = "حذف المزود", onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun CapabilityFilters(
    selected: Set<ModelCapability>,
    onToggle: (ModelCapability) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xSmall)) {
        Text(
            text = "تصفية حسب القدرات الموثقة",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
        ) {
            SelectableChip(
                label = "الكل",
                selected = selected.isEmpty(),
                onClick = onClear,
            )
            ModelCapability.entries.forEach { capability ->
                SelectableChip(
                    label = capability.label(),
                    selected = selected.contains(capability),
                    onClick = { onToggle(capability) },
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: AiModelSpec,
    isSelected: Boolean,
    usage: UsageCounter?,
    canEdit: Boolean,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
) {
    var renaming by remember(model.id) { mutableStateOf(false) }
    var renameText by remember(model.id) { mutableStateOf(model.displayName) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
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
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "النموذج المحدد",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = model.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (model.capabilities.isEmpty()) {
                Text(
                    text = "القدرات غير معلنة من المصدر — لا تُعرض افتراضات",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xxSmall)) {
                    model.capabilities.distinctBy { it.capability }.forEach { evidence ->
                        CapabilityBadge(evidence)
                    }
                }
            }

            model.contextTokens?.let { tokens ->
                Text(
                    text = "السياق: $tokens توكن (بيان المزود)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.pricing?.let { pricing ->
                val input = pricing.inputPerMillionUsd?.let { formatPrice(it) } ?: "-"
                val output = pricing.outputPerMillionUsd?.let { formatPrice(it) } ?: "-"
                Text(
                    text = "التكلفة لكل مليون توكن: $input / $output USD",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.rateLimit?.let { limit ->
                Text(
                    text = "حد النموذج: ${RateLimitFormatter.describe(limit)} (${RateLimitFormatter.sourceLabel(limit.source)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.deprecatedAt?.let { date ->
                Text(
                    text = "إيقاف معلن: $date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (model.capabilities.isNotEmpty()) {
                Text(
                    text = "دليل القدرات: ${hostOf(model.capabilities.first().sourceUrl)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            usage?.let { counter ->
                Text(
                    text = "الاستخدام: نجاح ${counter.success} • فشل ${counter.failure}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canEdit) {
                if (renaming) {
                    TextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(text = "اسم العرض") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        SecondaryTextAction(
                            text = "حفظ",
                            onClick = {
                                onRename(renameText)
                                renaming = false
                            },
                        )
                        SecondaryTextAction(text = "إلغاء", onClick = { renaming = false })
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        SecondaryTextAction(text = "تعديل الاسم", onClick = { renaming = true })
                        SecondaryTextAction(text = "حذف النموذج", onClick = onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityBadge(evidence: CapabilityEvidence) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "${evidence.capability.label()} ✓",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.xSmall, vertical = 2.dp),
        )
    }
}

@Composable
private fun AddProviderForm(
    onAdd: (String, String, AiProtocol, String, Int?, LimitWindow) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(AiProtocol.OPENAI_COMPATIBLE) }
    var limitText by remember { mutableStateOf("") }
    var window by remember { mutableStateOf(LimitWindow.HOUR) }

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
                text = "إضافة مزود مخصص",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = "الاسم") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(text = "Base URL (https://host/v1)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(text = "API Key (يُشفَّر عند الحفظ)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "البروتوكول",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
            ) {
                AiProtocol.entries.forEach { option ->
                    SelectableChip(
                        label = option.label(),
                        selected = protocol == option,
                        onClick = { protocol = option },
                    )
                }
            }
            TextField(
                value = limitText,
                onValueChange = { limitText = it.filter(Char::isDigit) },
                label = { Text(text = "حد الطلبات (اختياري)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xSmall)) {
                LimitWindow.entries.forEach { option ->
                    SelectableChip(
                        label = option.label(),
                        selected = window == option,
                        onClick = { window = option },
                    )
                }
            }
            Text(
                text = "الحد يُسجَّل كمعلومة أدخلها المستخدم، ولا يُستنتج من أي مزود آخر.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = "إضافة المزود",
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                onClick = {
                    onAdd(
                        name,
                        baseUrl,
                        protocol,
                        key,
                        limitText.toIntOrNull(),
                        window,
                    )
                },
            )
            SecondaryTextAction(text = "إلغاء", onClick = onCancel)
        }
    }
}

@Composable
private fun AddModelForm(onAdd: (String, String) -> Unit) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

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
                text = "إضافة نموذج يدويًا",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text(text = "معرّف النموذج") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(text = "اسم العرض (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "الإضافة اليدوية لا تمنح قدرات؛ تظهر قدرات النموذج فقط إن أعلنها المزود عبر /models.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                text = "إضافة النموذج",
                enabled = modelId.isNotBlank(),
                onClick = { onAdd(modelId, displayName) },
            )
        }
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = Space.medium, vertical = Space.xSmall),
        )
    }
}

@Composable
private fun SecondaryTextAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = Space.xxSmall),
    )
}

@Composable
private fun MessageRow(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDismiss() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xSmall),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun ModelCapability.label(): String = when (this) {
    ModelCapability.TEXT -> "نص"
    ModelCapability.VISION -> "صور"
    ModelCapability.REASONING -> "تفكير"
    ModelCapability.TOOLS -> "أدوات"
    ModelCapability.STREAMING -> "بث"
}

private fun AiProtocol.label(): String = when (this) {
    AiProtocol.OPENAI_COMPATIBLE -> "OpenAI-compatible"
    AiProtocol.ANTHROPIC_MESSAGES -> "Anthropic Messages"
    AiProtocol.CUSTOM_ADAPTER -> "محول مخصص"
}

private fun LimitWindow.label(): String = when (this) {
    LimitWindow.MINUTE -> "دقيقة"
    LimitWindow.HOUR -> "ساعة"
    LimitWindow.DAY -> "يوم"
    LimitWindow.MONTH -> "شهر"
}

private fun LimitSource.label(): String = RateLimitFormatter.sourceLabel(this)

private fun formatPrice(value: Double): String = String.format(Locale.US, "%.4f", value)

private fun hostOf(url: String): String = runCatching {
    java.net.URI(url).host ?: url
}.getOrDefault(url)

private fun formatTimestamp(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
