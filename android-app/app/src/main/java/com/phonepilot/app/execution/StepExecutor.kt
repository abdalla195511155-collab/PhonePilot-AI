package com.phonepilot.app.execution

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.phonepilot.app.models.ExecutionState
import com.phonepilot.app.models.StepExecutionResult
import com.phonepilot.app.privacy.PrivacyFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * منفذ خطوة واحدة (Phase 6 — Intelligent Execution).
 *
 * ينفذ إجراءً واحدًا على عنصر واجهة مستخدم، ثم يقرأ حالة الشاشة الجديدة
 * ويتحقق من النتيجة. لا يُنفّذ أي إجراء حساس دون موافقة المستخدم.
 */
class StepExecutor(
    private val accessibilityService: AccessibilityService?,
    private val elementResolver: ElementResolver,
    private val coroutineScope: CoroutineScope,
) {

    /** الحد الأقصى لمحاولات الاستعادة لكل خطوة. */
    private const val MAX_RECOVERY_ATTEMPTS = 2

    /**
     * ينفذ خطوة واحدة.
     *
     * التدفق:
     * 1. قراءة حالة الشاشة الحالية
     * 2. حل العنصر المستهدف
     * 3. التحقق من عدم وجود غموض خطير
     * 4. تنفيذ الإجراء المسموح
     * 5. قراءة الحالة الجديدة
     * 6. التحقق من النتيجة
     * 7. إرجاع نتيجة تنفيذ الخطوة
     */
    suspend fun executeStep(
        stepNumber: Int,
        stepDescription: String,
        action: String, // click, input, scroll, back, ...
        targetText: String?,
        targetContentDescription: String?,
        targetViewId: String?,
        targetRole: String?,
        requiresUserConfirmation: Boolean = false,
        userApproved: Boolean = false,
    ): StepExecutionResult {
        // إذا كانت الخطوة تتطلب موافقة المستخدم ولم تُعطَ، تُرفض
        if (requiresUserConfirmation && !userApproved) {
            return StepExecutionResult(
                stepNumber = stepNumber,
                stepDescription = stepDescription,
                success = false,
                verified = false,
                elementResolved = false,
                failureReason = "تتطلب موافقة المستخدم ولم تُمنح",
            )
        }

        // 1. حل العنصر المستهدف من شجرة الوصولية الحالية
        val rootNode = accessibilityService?.rootInActiveWindow
        val candidates = elementResolver.resolveElement(
            stepDescription = stepDescription,
            elementRole = targetRole,
            elementText = targetText,
            elementContentDescription = targetContentDescription,
            elementViewId = targetViewId,
            rootNode = rootNode,
        )

        if (candidates.isEmpty()) {
            return StepExecutionResult(
                stepNumber = stepNumber,
                stepDescription = stepDescription,
                success = false,
                verified = false,
                elementResolved = false,
                failureReason = "لم يتم العثور على العنصر المستهدف",
            )
        }

        // 2. التحقق من الغموض
        if (elementResolver.detectAmbiguity(candidates)) {
            return StepExecutionResult(
                stepNumber = stepNumber,
                stepDescription = stepDescription,
                success = false,
                verified = false,
                elementResolved = false,
                failureReason = "غموض في العنصر المستهدف: ${candidates.size} عناصر متطابقة",
            )
        }

        val bestCandidate = candidates.first()

        // 3. قراءة حالة الشاشة قبل الإجراء للمقارنة لاحقًا
        val screenBefore = readScreenSnapshot()

        // 4. تنفيذ الإجراء
        val actionTaken = performAction(
            action = action,
            candidate = bestCandidate,
            inputText = targetText,
        )

        // 5. قراءة حالة الشاشة بعد الإجراء
        val screenAfter = readScreenSnapshot()

        // 6. التحقق من النتيجة (مقارنة الحالة قبل وبعد)
        val verified = verifyStepResult(
            action = action,
            candidate = bestCandidate,
            screenBefore = screenBefore,
            screenAfter = screenAfter,
        )

        return StepExecutionResult(
            stepNumber = stepNumber,
            stepDescription = stepDescription,
            success = verified,
            verified = verified,
            elementResolved = true,
            elementId = bestCandidate.elementId,
            actionTaken = actionTaken,
            failureReason = if (verified) null else "فشل التحقق من نتيجة الخطوة",
            timestamp = System.currentTimeMillis(),
            screenAfter = screenAfter,
        )
    }

    /**
     * ينفذ إجراءً فعلياً على العنصر عبر خدمة الوصولية.
     */
    private fun performAction(
        action: String,
        candidate: ResolvedElement,
        inputText: String?,
    ): String {
        val service = accessibilityService ?: return "no_service"
        val rootNode = service.rootInActiveWindow

        if (rootNode == null) return "no_root_node"

        // البحث عن العقدة المطابقة
        val targetNode = findNodeByViewId(rootNode, candidate.elementViewId ?: candidate.elementId)
        if (targetNode == null) return "node_not_found"

        return when (action.lowercase()) {
            "click" -> {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                "clicked"
            }
            "long_click" -> {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                "long_clicked"
            }
            "input" -> {
                if (inputText.isNotBlank()) {
                    // إ_clear النص الحالي ثم إدخال الجديد
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    // في التنفيذ الفعلي، يُستخدم setText عبر accessibility
                    "input_$inputText"
                } else {
                    "input_empty"
                }
            }
            "scroll" -> {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                "scrolled"
            }
            "back" -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "back"
            }
            else -> "unknown_action"
        } + " (masked: ${PrivacyFilter.isSensitiveText(inputText)})"
    }

    /** البحث عن عقدة الوصولية بمعرّف العرض. */
    private fun findNodeByViewId(node: AccessibilityNodeInfo, viewId: String?): AccessibilityNodeInfo? {
        if (viewId.isNullOrBlank()) return null
        if (viewId == node.viewId) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findNodeByViewId(child, viewId)
                if (result != null) return result
            }
        }
        return null
    }

    /** قراءة لقطة الشجرة الحالية من خدمة الوصولية للمقارنة قبل وبعد الإجراء. */
    private suspend fun readScreenSnapshot(): String? {
        return withContext(Dispatchers.IO) {
            val root = accessibilityService?.rootInActiveWindow ?: return@withContext null
            val sb = StringBuilder()
            collectNodeText(root, sb)
            sb.toString().takeIf { it.isNotEmpty() }
        }
    }

    /** جمع نصوص العقد وشجرة الوصولية بشكل تكراري (للمقارنة فقط، لا يُخزّن). */
    private fun collectNodeText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        sb.append(node.className ?: "")
        sb.append("|")
        sb.append(node.viewIdResourceName ?: "")
        sb.append("|")
        sb.append(node.text ?: "")
        sb.append("|")
        sb.append(node.contentDescription ?: "")
        sb.append("||")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodeText(child, sb)
        }
    }

    /** التحقق من نتيجة الخطوة بمقارنة الحالة قبل وبعد الإجراء. */
    private suspend fun verifyStepResult(
        action: String,
        candidate: ResolvedElement,
        screenBefore: String?,
        screenAfter: String?,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            // إذا لم تتوفر لقطات قبل وبعد، لا يمكن التحقق — لا تُفترض نجاحاً
            if (screenBefore == null || screenAfter == null) return@withContext false
            // إذا لم يتغير شيء بعد الإجراء، الإجراء لم يُحدث تأثيراً
            if (screenBefore == screenAfter) return@withContext false
            // تغيير في الشجرة يُعتبر دليلاً أولياً على نجاح الإجراء
            true
        }
    }
}

/**
 * مدقق تنفيذ (Phase 6 — Intelligent Execution).
 *
 * يتحقق من نجاح كل خطوة قبل الانتقال إلى الخطوة التالية.
 * إذا فشل التحقق، تُنتقل السيطرة إلى مدير الاستعادة.
 */
class ExecutionVerifier {

    /** التحقق من أن التنفيذ يمكن أن يستمر إلى الخطوة التالية. */
    fun canProceed(stepResult: StepExecutionResult): Boolean {
        return stepResult.success && stepResult.verified
    }

    /** التحقق من أن الخطوة تتطلب استعادة. */
    fun requiresRecovery(stepResult: StepExecutionResult): Boolean {
        return !stepResult.success && stepResult.recoveryAttempted == false
    }

    /** إنشاء رسالة فشل للخطوة. */
    fun failureMessage(stepResult: StepExecutionResult): String {
        return stepResult.failureReason ?: "فشل غير محدد في الخطوة ${stepResult.stepNumber}"
    }
}

/**
 * مدير الاستعادة (Phase 6 — Intelligent Execution).
 *
 * يدير محاولات الاستعادة عند فشل الخطوات:
 * - إعادة المحاولة للعمليات الآمنة
 * - إعادة قراءة الشاشة وتحديد العنصر مرة أخرى
 * - العودة إلى آخر حالة آمنة عند الإمكان
 * - التوقف عند تكرار الفشل
 */
class RecoveryManager(
    private val stepExecutor: StepExecutor,
) {

    /** الحد الأقصى لمحاولات الاستعادة. */
    private const val MAX_RECOVERY_ATTEMPTS = 2

    /**
     * يحاول استعادة خطوة فاشلة.
     *
     * يعيد نتيجة الاستعادة أو null إذا لم يمكن الاستعادة.
     */
    suspend fun attemptRecovery(
        stepNumber: Int,
        stepDescription: String,
        action: String,
        targetText: String?,
        targetContentDescription: String?,
        targetViewId: String?,
        targetRole: String?,
        previousFailure: String,
        requiresUserConfirmation: Boolean = false,
        userApproved: Boolean = false,
    ): StepExecutionResult? {
        for (attempt in 1..MAX_RECOVERY_ATTEMPTS) {
            // إعادة قراءة الشاشة وتحديد العنصر مرة أخرى (بسيطة)
            val result = stepExecutor.executeStep(
                stepNumber = stepNumber,
                stepDescription = "$stepDescription (استعادة المحاولة $attempt)",
                action = action,
                targetText = targetText,
                targetContentDescription = targetContentDescription,
                targetViewId = targetViewId,
                targetRole = targetRole,
                requiresUserConfirmation = requiresUserConfirmation,
                userApproved = userApproved,
            ).copy(
                recoveryAttempted = true,
                recoverySucceeded = result.success,
            )

            if (result.success) {
                return result
            }
        }

        return null // استنفدت جميع محاولات الاستعادة
    }
}
