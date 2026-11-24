package com.phonepilot.app.documents

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.phonepilot.app.ui.components.PrimaryButton
import com.phonepilot.app.ui.components.ScreenHeader
import com.phonepilot.app.ui.theme.PhonePilotTheme

/**
 * يستقبل نصًا أو ملفًا نصيًا (txt/csv) من "مشاركة" في تطبيق آخر، ويستخرج الأرقام محليًا.
 * لا يرسل أي شيء إلى أي نموذج أو خدمة. المستخدم ينسخ طلب التحليل بنفسه إن أراد.
 * PDF والصور غير مدعومة بعد.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = readShared(intent)
        setContent {
            PhonePilotTheme(darkTheme = false, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) { IntakeContent(text) }
            }
        }
    }

    private fun readShared(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND) return null
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { return it }
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        val type = intent.type ?: contentResolver.getType(uri)
        if (type == null || !type.startsWith("text/")) return null
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(MAX_BYTES)
                var n = 0
                while (n < MAX_BYTES) {
                    val r = input.read(buf, n, MAX_BYTES - n)
                    if (r < 0) break
                    n += r
                }
                String(buf, 0, n, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_BYTES = 400_000
    }
}

@Composable
private fun IntakeContent(text: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "استقبال مستند", subtitle = "استخراج محلي للأرقام. لا يُرسل شيء لأي نموذج.")
        if (text == null) {
            Text(
                "لم أستطع قراءة المحتوى. المدعوم الآن: نص مشارك أو ملف txt/csv. PDF والصور غير مدعومة بعد.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("intake_unsupported"),
            )
            return@Column
        }
        val facts = remember(text) { DocumentIntake.analyze(text) }
        Text("الأسطر: ${facts.lineCount}" + if (facts.truncated) " (اقتُطع المستند)" else "")
        Text("مجاميع محسوبة بالكود (مبالغ بعملة واضحة فقط):", style = MaterialTheme.typography.titleMedium)
        if (facts.totalsByCurrency.isEmpty()) Text("لا توجد مبالغ بعملة واضحة")
        facts.totalsByCurrency.forEach { (c, v) -> Text("$c: ${v.toPlainString()}", modifier = Modifier.testTag("intake_total_$c")) }
        Text("أرقام بدون عملة واضحة (غير مجمّعة): ${facts.unclassifiedCount}")
        if (facts.ambiguous.isNotEmpty()) {
            Text(
                "أرقام بتنسيق غير واضح، لم تُحسب: " + facts.ambiguous.joinToString("، ") { "سطر ${it.line}: ${it.raw}" },
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text("التفسير المحاسبي يحتاج نموذجًا. انسخ الطلب والصقه في المساعد بنفسك بعد مراجعته؛ لن يُرسل تلقائيًا.")
        PrimaryButton(
            modifier = Modifier.testTag("intake_copy"),
            onClick = {
                val prompt = DocumentIntake.buildAnalysisPrompt(text, facts)
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("analysis", prompt))
                copied = true
            },
            text = if (copied) "تم النسخ" else "نسخ طلب التحليل",
        )
    }
}
