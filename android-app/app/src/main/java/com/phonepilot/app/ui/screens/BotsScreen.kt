package com.phonepilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.phonepilot.app.bot.AccessibilityScreenActions
import com.phonepilot.app.bot.BotApproval
import com.phonepilot.app.bot.BotDefinition
import com.phonepilot.app.bot.BotExecution
import com.phonepilot.app.bot.BotJson
import com.phonepilot.app.bot.BotRunner
import com.phonepilot.app.bot.BotScheduling
import com.phonepilot.app.bot.BotStore
import com.phonepilot.app.bot.BotSummary
import com.phonepilot.app.bot.BotTemplates
import com.phonepilot.app.bot.BotValidator
import com.phonepilot.app.bot.PrefsBotStore
import com.phonepilot.app.recording.PhonePilotAccessibilityService
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.theme.Space
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun BotsScreen(onNavigateTo: (String) -> Unit, storeOverride: BotStore? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { storeOverride ?: PrefsBotStore(context) }
    var bots by remember { mutableStateOf(store.list()) }
    var editing by remember { mutableStateOf<BotDefinition?>(null) }
    var editorText by remember { mutableStateOf("") }
    var approvalChecked by remember { mutableStateOf(false) }
    var approvalTouched by remember { mutableStateOf(false) }
    var editorErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmRun by remember { mutableStateOf<BotDefinition?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf<String?>(null) }

    fun refresh() { bots = store.list() }

    fun openEditor(def: BotDefinition) {
        editing = def
        editorText = BotJson.toPrettyJson(def)
        editorErrors = emptyList()
        approvalChecked = def.unattendedApproved
        approvalTouched = false
    }

    fun startRun(def: BotDefinition) {
        running = def.id
        message = null
        scope.launch {
            val runner = BotRunner(AccessibilityScreenActions { PhonePilotAccessibilityService.instance })
            val result = BotExecution.runOnce(def, store, runner, approvedForThisRun = true)
            running = null
            message = "نتيجة ${def.name}: ${result.status.name}" + (result.reason?.let { " ($it)" } ?: "")
            refresh()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.medium),
        verticalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        ScreenHeader(
            title = "البوتات",
            subtitle = "محفّز + شروط + خطوات ثابتة تراجعها بنفسك. لم تُجرَّب على جهاز فعلي بعد.",
        )
        if (PhonePilotAccessibilityService.instance == null) {
            Text(
                text = "خدمة الوصولية غير مفعّلة. فعّلها من الخصوصية والأذونات قبل تشغيل أي بوت.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("bots_a11y_warning"),
            )
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("bots_message")) }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            TextButton(
                modifier = Modifier.testTag("bots_new_blank"),
                onClick = { openEditor(BotTemplates.blankManual(UUID.randomUUID().toString())) },
            ) { Text("بوت جديد") }
            TextButton(
                modifier = Modifier.testTag("bots_new_whatsapp"),
                onClick = {
                    openEditor(
                        BotTemplates.scheduledWhatsApp(
                            UUID.randomUUID().toString(), "اسم الجهة", "نص الرسالة", 9, 0,
                        ),
                    )
                },
            ) { Text("قالب رسالة واتساب") }
        }
        if (bots.isEmpty()) {
            Text(
                text = "لا توجد بوتات بعد. ابدأ بقالب أو ببوت جديد، ثم عدّل الخطوات واحفظ.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("bots_empty"),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.small)) {
            items(bots, key = { it.id }) { def ->
                val state = store.state(def.id)
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("bot_${def.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.xSmall)) {
                        Text(def.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${BotSummary.trigger(def.trigger)} • ${def.steps.size} خطوة" +
                                (if (BotSummary.hasExternalEffect(def)) " • فيه خطوة بأثر خارجي" else "") +
                                if (BotApproval.needsApproval(def)) {
                                    if (def.unattendedApproved) " • مسموح بالتشغيل بدون حضور" else " • بدون موافقة: لن يعمل تلقائيًا"
                                } else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            BotSummary.status(state),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                            Switch(
                                modifier = Modifier.testTag("bot_switch_${def.id}"),
                                checked = state.enabled,
                                enabled = BotSummary.triggerSupported(def.trigger),
                                onCheckedChange = { on ->
                                    store.setState(def.id, state.copy(enabled = on))
                                    BotScheduling.reschedule(context, def, store.state(def.id))
                                    refresh()
                                },
                            )
                            TextButton(
                                enabled = running == null,
                                onClick = { if (BotSummary.hasExternalEffect(def)) confirmRun = def else startRun(def) },
                            ) { Text(if (running == def.id) "يعمل..." else "تشغيل الآن") }
                            TextButton(onClick = { openEditor(def) }) { Text("تعديل") }
                            TextButton(onClick = {
                                BotScheduling.cancel(context, def.id)
                                store.delete(def.id)
                                refresh()
                            }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }

    confirmRun?.let { def ->
        AlertDialog(
            onDismissRequest = { confirmRun = null },
            title = { Text("تأكيد التشغيل") },
            text = { Text("البوت \"${def.name}\" فيه خطوة بأثر خارجي (مثل إرسال رسالة) ولا تُعاد تلقائيًا. تشغّله الآن؟") },
            confirmButton = { TextButton(onClick = { confirmRun = null; startRun(def) }) { Text("نعم، شغّل") } },
            dismissButton = { TextButton(onClick = { confirmRun = null }) { Text("إلغاء") } },
        )
    }

    editing?.let {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("تعديل البوت (JSON)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xSmall)) {
                    OutlinedTextField(
                        value = editorText,
                        onValueChange = { editorText = it },
                        modifier = Modifier.fillMaxWidth().testTag("bots_editor"),
                        minLines = 8,
                        maxLines = 14,
                    )
                    val draft = try { BotJson.parse(editorText) } catch (e: BotJson.ParseException) { null }
                    if (draft != null && BotApproval.needsApproval(draft)) {
                        Text(
                            "تحذير: هذا البوت له خطوة بأثر خارجي (مثل إرسال رسالة) ومحفّزه تلقائي. لو وافقت سيُنفَّذ بدون أن تكون أمام الشاشة، ولا يوجد تراجع عن الرسالة بعد إرسالها.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.testTag("bots_approval_warning"),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                modifier = Modifier.testTag("bots_approval_checkbox"),
                                checked = approvalChecked,
                                onCheckedChange = { approvalChecked = it; approvalTouched = true },
                            )
                            Text("أوافق على تشغيل هذا البوت تلقائيًا بدون حضوري", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    editorErrors.forEach {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("bots_save"),
                    onClick = {
                        try {
                            val parsed = BotJson.parse(editorText)
                            val def = parsed.copy(
                                unattendedApproved = BotApproval.resolve(
                                    parsed, store.list().firstOrNull { it.id == parsed.id }, approvalChecked, approvalTouched,
                                ),
                            )
                            val errs = BotValidator.validate(def)
                            if (errs.isEmpty()) {
                                store.save(def)
                                BotScheduling.reschedule(context, def, store.state(def.id))
                                editing = null
                                refresh()
                            } else editorErrors = errs
                        } catch (e: BotJson.ParseException) {
                            editorErrors = listOf(e.message ?: "تعريف غير صالح")
                        }
                    },
                ) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("إلغاء") } },
        )
    }
}
