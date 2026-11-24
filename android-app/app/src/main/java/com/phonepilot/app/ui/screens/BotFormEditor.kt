package com.phonepilot.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.phonepilot.app.bot.ActionKind
import com.phonepilot.app.bot.BotAction
import com.phonepilot.app.bot.BotCondition
import com.phonepilot.app.bot.BotDefinition
import com.phonepilot.app.bot.BotForm
import com.phonepilot.app.bot.BotStep
import com.phonepilot.app.bot.BotTrigger
import com.phonepilot.app.bot.TriggerKind
import com.phonepilot.app.ui.theme.Space

private fun triggerLabel(k: TriggerKind) = when (k) {
    TriggerKind.MANUAL -> "يدوي"
    TriggerKind.DAILY -> "يوميًا في وقت"
    TriggerKind.EVERY -> "كل عدد دقائق"
    TriggerKind.NOTIFICATION -> "عند إشعار من تطبيق"
}

private fun actionLabel(k: ActionKind) = when (k) {
    ActionKind.OPEN_APP -> "فتح تطبيق"
    ActionKind.TYPE_TEXT -> "كتابة نص"
    ActionKind.CLICK -> "نقر على نص"
    ActionKind.TAP -> "نقر بالإحداثيات"
    ActionKind.SWIPE -> "سحب"
    ActionKind.BACK -> "رجوع"
    ActionKind.WAIT_FOR_TEXT -> "انتظار ظهور نص"
}

@Composable
private fun TextF(label: String, value: String, tag: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun NumF(label: String, value: Int, tag: String, onValue: (Int) -> Unit) {
    // نص محلي حتى يمكن مسح الحقل بالكامل أثناء الكتابة؛ يُزامَن فقط عند تغيّر القيمة من الخارج.
    var text by remember { mutableStateOf(value.toString()) }
    if (BotForm.digitsToInt(text) != value) text = value.toString()
    OutlinedTextField(
        value = text,
        onValueChange = {
            val digits = it.filter { c -> c in '0'..'9' }.take(6)
            text = digits
            onValue(BotForm.digitsToInt(digits))
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** محرر نموذج لتعريف البوت. يعدّل [BotDefinition] مباشرة؛ الحفظ والتحقق خارجه. */
@Composable
fun BotFormEditor(def: BotDefinition, onChange: (BotDefinition) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xSmall)) {
        TextF("اسم البوت", def.name, "form_name") { onChange(def.copy(name = it)) }

        Text("المحفّز", style = MaterialTheme.typography.titleSmall)
        val kind = BotForm.triggerKind(def.trigger)
        TriggerKind.values().forEach { k ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onChange(BotForm.withTriggerKind(def, k)) }.testTag("form_trigger_${k.name}"),
            ) {
                RadioButton(selected = kind == k, onClick = { onChange(BotForm.withTriggerKind(def, k)) })
                Text(triggerLabel(k))
            }
        }
        when (val t = def.trigger) {
            BotTrigger.Manual -> Unit
            is BotTrigger.DailyAt -> {
                NumF("الساعة (0-23)", t.hour, "form_hour") { onChange(def.copy(trigger = t.copy(hour = it))) }
                NumF("الدقيقة (0-59)", t.minute, "form_minute") { onChange(def.copy(trigger = t.copy(minute = it))) }
            }
            is BotTrigger.EveryMinutes ->
                NumF("كل كم دقيقة (15 على الأقل)", t.minutes, "form_minutes") { onChange(def.copy(trigger = t.copy(minutes = it))) }
            is BotTrigger.NotificationFrom -> {
                TextF("اسم حزمة التطبيق", t.packageName, "form_notif_package") { onChange(def.copy(trigger = t.copy(packageName = it))) }
                TextF("نص في عنوان الإشعار (اختياري)", t.titleContains ?: "", "form_notif_title") {
                    onChange(def.copy(trigger = t.copy(titleContains = it.ifBlank { null })))
                }
            }
        }

        NumF("أقصى عدد تشغيلات في اليوم", def.maxRunsPerDay, "form_max_runs") { onChange(def.copy(maxRunsPerDay = it)) }

        Text("الشروط (كلها يجب أن تتحقق قبل التشغيل)", style = MaterialTheme.typography.titleSmall)
        def.conditions.forEachIndexed { i, c ->
            Card(modifier = Modifier.fillMaxWidth().testTag("form_condition_$i")) {
                Column(Modifier.padding(Space.small), verticalArrangement = Arrangement.spacedBy(Space.xSmall)) {
                    when (c) {
                        is BotCondition.ForegroundPackageIs ->
                            TextF("التطبيق المفتوح يجب أن يكون (اسم الحزمة)", c.packageName, "form_cond_${i}_value") {
                                onChange(BotForm.replaceCondition(def, i, c.copy(packageName = it)))
                            }
                        is BotCondition.ScreenContainsText ->
                            TextF("نص يجب أن يظهر على الشاشة", c.text, "form_cond_${i}_value") {
                                onChange(BotForm.replaceCondition(def, i, c.copy(text = it)))
                            }
                    }
                    TextButton(onClick = { onChange(BotForm.removeCondition(def, i)) }) { Text("حذف الشرط") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            TextButton(modifier = Modifier.testTag("form_add_cond_text"), onClick = { onChange(BotForm.addCondition(def, true)) }) { Text("+ شرط نص") }
            TextButton(modifier = Modifier.testTag("form_add_cond_pkg"), onClick = { onChange(BotForm.addCondition(def, false)) }) { Text("+ شرط تطبيق") }
        }

        Text("الخطوات (${def.steps.size})", style = MaterialTheme.typography.titleSmall)
        def.steps.forEachIndexed { i, step ->
            StepCard(def, i, step, onChange)
        }
        Text("إضافة خطوة", style = MaterialTheme.typography.labelMedium)
        ActionKind.values().forEach { k ->
            TextButton(modifier = Modifier.testTag("form_add_${k.name}"), onClick = { onChange(BotForm.addStep(def, k)) }) {
                Text("+ ${actionLabel(k)}")
            }
        }
        if (def.allowedPackages.isNotEmpty()) {
            Text(
                "الحزم المسموحة الإضافية: ${def.allowedPackages.sorted().joinToString("، ")} (تُعدَّل من وضع JSON)",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun StepCard(def: BotDefinition, i: Int, step: BotStep, onChange: (BotDefinition) -> Unit) {
    fun upd(s: BotStep) = onChange(BotForm.replaceStep(def, i, s))
    Card(modifier = Modifier.fillMaxWidth().testTag("form_step_$i")) {
        Column(Modifier.padding(Space.small), verticalArrangement = Arrangement.spacedBy(Space.xSmall)) {
            Text("خطوة ${i + 1}: ${actionLabel(BotForm.actionKind(step.action))}", style = MaterialTheme.typography.titleSmall)
            when (val a = step.action) {
                is BotAction.OpenApp -> TextF("اسم الحزمة", a.packageName, "form_step_${i}_a") { upd(step.copy(action = a.copy(packageName = it))) }
                is BotAction.TypeText -> {
                    TextF("النص", a.text, "form_step_${i}_a") { upd(step.copy(action = a.copy(text = it))) }
                    TextF("نص الحقل المستهدف (اختياري)", a.target ?: "", "form_step_${i}_b") {
                        upd(step.copy(action = a.copy(target = it.ifBlank { null })))
                    }
                }
                is BotAction.Click -> TextF("نص الزر أو وصفه", a.target, "form_step_${i}_a") { upd(step.copy(action = a.copy(target = it))) }
                is BotAction.TapAt -> {
                    NumF("x", a.x, "form_step_${i}_a") { upd(step.copy(action = a.copy(x = it))) }
                    NumF("y", a.y, "form_step_${i}_b") { upd(step.copy(action = a.copy(y = it))) }
                }
                is BotAction.Swipe -> {
                    NumF("x1", a.x1, "form_step_${i}_a") { upd(step.copy(action = a.copy(x1 = it))) }
                    NumF("y1", a.y1, "form_step_${i}_b") { upd(step.copy(action = a.copy(y1 = it))) }
                    NumF("x2", a.x2, "form_step_${i}_c") { upd(step.copy(action = a.copy(x2 = it))) }
                    NumF("y2", a.y2, "form_step_${i}_d") { upd(step.copy(action = a.copy(y2 = it))) }
                    NumF("المدة بالملّي ثانية", a.durationMs.toInt(), "form_step_${i}_e") { upd(step.copy(action = a.copy(durationMs = it.toLong()))) }
                }
                BotAction.Back -> Unit
                is BotAction.WaitForText -> {
                    TextF("النص المنتظر", a.text, "form_step_${i}_a") { upd(step.copy(action = a.copy(text = it))) }
                    NumF("المهلة بالملّي ثانية", a.timeoutMs.toInt(), "form_step_${i}_b") { upd(step.copy(action = a.copy(timeoutMs = it.toLong()))) }
                }
            }
            TextF("نص يجب أن يظهر بعد الخطوة للتحقق (اختياري)", step.verifyText ?: "", "form_step_${i}_verify") {
                upd(step.copy(verifyText = it.ifBlank { null }))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    modifier = Modifier.testTag("form_step_${i}_external"),
                    checked = step.externalEffect,
                    onCheckedChange = { upd(step.copy(externalEffect = it)) },
                )
                Text("خطوة بأثر خارجي لا يُتراجع عنه (مثل إرسال رسالة)", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xSmall)) {
                TextButton(modifier = Modifier.testTag("form_step_${i}_up"), onClick = { onChange(BotForm.moveStep(def, i, -1)) }) { Text("أعلى") }
                TextButton(modifier = Modifier.testTag("form_step_${i}_down"), onClick = { onChange(BotForm.moveStep(def, i, 1)) }) { Text("أسفل") }
                TextButton(modifier = Modifier.testTag("form_step_${i}_delete"), onClick = { onChange(BotForm.removeStep(def, i)) }) { Text("حذف الخطوة") }
            }
        }
    }
}
