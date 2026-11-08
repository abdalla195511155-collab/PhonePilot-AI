package com.phonepilot.app.execution

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.phonepilot.app.vision.AccessibilityTreeAnalyzer
import com.phonepilot.app.vision.UiElementRecognizer
import com.phonepilot.app.vision.UiElement
import com.phonepilot.app.vision.ElementSource
import com.phonepilot.app.vision.SemanticRole
import com.phonepilot.app.privacy.PrivacyFilter

/**
 * مُحلل العناصر (Phase 6 — Intelligent Execution).
 *
 * يحل العناصر المستهدفة في خطوات المهارة باستخدام شجرة الوصولية (Accessibility Tree)
 * ومحرك الرؤية (Vision Engine). لا يعتمد على الإحداثيات الثابتة وحدها؛ بل يستخدم
 * هوية العنصر الدلالية (viewId، contentDescription، text، role، bounds) لإعادة
 * التعرف على العنصر حتى لو تغيرت موضعه بين العروض.
 */
class ElementResolver(
    private val context: Context,
    private val accessibilityAnalyzer: AccessibilityTreeAnalyzer?,
    private val elementRecognizer: UiElementRecognizer?,
) {

    private val analyzer = accessibilityAnalyzer ?: AccessibilityTreeAnalyzer(context)

    /**
     * يحل عنصرًا واحدًا بناءً على وصف الخطوة.
     *
     * يعيد أفضل عناصر مطابقة، أو قائمة فارغة إذا لم يتم العثور على أي عنصر.
     * يُرتّب النتائج حسب درجة الثقة التنازلية.
     */
    fun resolveElement(
        stepDescription: String?,
        elementRole: String?,
        elementText: String?,
        elementContentDescription: String?,
        elementViewId: String?,
        rootNode: AccessibilityNodeInfo?,
        maxResults: Int = 5,
    ): List<ResolvedElement> {
        // لا تُخترع نتائج بدون شجرة وصولية حقيقية
        if (rootNode == null) return emptyList()

        val elements = analyzer.analyze(rootNode)

        val candidates = elements.filter { element ->
            val viewIdMatch = elementViewId.isNullOrBlank() || element.viewIdResourceName == elementViewId
            val textMatch = elementText.isNullOrBlank() || element.text?.contains(elementText, ignoreCase = true) == true
            val descMatch = elementContentDescription.isNullOrBlank() || element.contentDescription?.contains(elementContentDescription, ignoreCase = true) == true
            val roleMatch = elementRole.isNullOrBlank() || element.semanticRole?.name == elementRole
            viewIdMatch && textMatch && descMatch && roleMatch
        }

        return candidates.mapIndexed { index, element ->
            val maskedText = PrivacyFilter.maskEventText(element.text)
            val maskedDesc = PrivacyFilter.maskEventText(element.contentDescription)
            ResolvedElement(
                elementId = element.elementId,
                elementViewId = element.viewIdResourceName,
                text = maskedText,
                contentDescription = maskedDesc,
                semanticRole = element.semanticRole?.name,
                confidence = element.confidence,
                source = element.source,
                matchReason = if (elementViewId?.isNotBlank() == true) "view_id_match" else "semantic_match",
            )
        }.sortedByDescending { it.confidence }.take(maxResults)
    }

    /**
     * يكتشف الغموض الخطير: عندما يتطابق أكثر من عنصر واحد مع نفس المواصفات
     * ولا يمكن التمييز بينها دلالياً.
     */
    fun detectAmbiguity(candidates: List<ResolvedElement>): Boolean {
        return candidates.size > 1 && candidates.all { it.confidence < 0.8f }
    }

    /**
     * يُنشئ وصفًا دلاليًا لعنصر للمراجعة اليدوية عند الغموض.
     */
    fun describeElement(element: ResolvedElement): String {
        val parts = mutableListOf<String>()
        if (element.elementViewId?.isNotBlank() == true) parts.add("viewId=${element.elementViewId}")
        if (element.text?.isNotBlank() == true) parts.add("text=${element.text}")
        if (element.contentDescription?.isNotBlank() == true) parts.add("desc=${element.contentDescription}")
        if (element.semanticRole != null && element.semanticRole != SemanticRole.UNKNOWN.name) parts.add("role=${element.semanticRole}")
        return parts.joinToString(", ")
    }
}

/**
 * عنصر محلول مع معلومات المطابقة والثقة.
 */
data class ResolvedElement(
    val elementId: String,
    val elementViewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val semanticRole: String? = null,
    val confidence: Float = 0.0f,
    val source: ElementSource = ElementSource.ACCESSIBILITY,
    val matchReason: String? = null,
)
