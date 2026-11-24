package com.phonepilot.app.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * تنفيذ [ScreenActions] عبر خدمة الوصولية الحية.
 * لم يُجرَّب على جهاز فعلي بعد: اختبارات الوحدة تغطي منطق المشغّل فقط وليس هذا الصنف.
 */
class AccessibilityScreenActions(
    private val serviceProvider: () -> AccessibilityService?,
) : ScreenActions {

    override suspend fun openApp(packageName: String): Boolean {
        val service = serviceProvider() ?: return false
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            service.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun typeText(text: String, target: String?): Boolean {
        val root = serviceProvider()?.rootInActiveWindow ?: return false
        var node: AccessibilityNodeInfo? = null
        if (target.isNullOrBlank()) {
            node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } else {
            val matches = findByLabel(root, target).filter { it.isEditable }
            if (matches.size == 1) node = matches[0]
        }
        if (node == null) return false
        if (!node.isEditable) return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override suspend fun click(target: String): Boolean {
        val root = serviceProvider()?.rootInActiveWindow ?: return false
        val matches = findByLabel(root, target)
        // غموض: أكثر من عنصر مطابق، لا نخمّن
        if (matches.size != 1) return false
        var node: AccessibilityNodeInfo? = matches[0]
        while (node != null && !node.isClickable) node = node.parent
        return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    override suspend fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatch(path, 50L)
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        return dispatch(path, durationMs)
    }

    override suspend fun back(): Boolean =
        serviceProvider()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false

    override fun screenText(): String? {
        val root = serviceProvider()?.rootInActiveWindow ?: return null
        val sb = StringBuilder()
        collect(root, sb, 0)
        return sb.toString()
    }

    override fun foregroundPackage(): String? =
        serviceProvider()?.rootInActiveWindow?.packageName?.toString()

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean {
        val service = serviceProvider() ?: return false
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        return suspendCancellableCoroutine { cont ->
            val accepted = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!accepted && cont.isActive) cont.resume(false)
        }
    }

    /** عقد نصها أو وصفها يطابق [label] تمامًا، وإن لم يوجد تطابق تام فالتي تحتويه. */
    private fun findByLabel(root: AccessibilityNodeInfo, label: String): List<AccessibilityNodeInfo> {
        val all = mutableListOf<AccessibilityNodeInfo>()
        gather(root, all, 0)
        val exact = all.filter { labelOf(it).equals(label, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact
        return all.filter { labelOf(it).contains(label, ignoreCase = true) && labelOf(it).isNotEmpty() }
    }

    private fun labelOf(n: AccessibilityNodeInfo): String =
        (n.text?.toString() ?: n.contentDescription?.toString() ?: "")

    private fun gather(n: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (depth > 60) return
        out += n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            gather(c, out, depth + 1)
        }
    }

    private fun collect(n: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 60) return
        n.text?.let { sb.append(it).append('\n') }
        n.contentDescription?.let { sb.append(it).append('\n') }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            collect(c, sb, depth + 1)
        }
    }
}
