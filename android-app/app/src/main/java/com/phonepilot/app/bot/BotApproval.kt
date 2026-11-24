package com.phonepilot.app.bot

/**
 * موافقة التشغيل بدون حضور. القرار يأتي من مربع موافقة صريح في المحرر، لا من حقل JSON.
 */
object BotApproval {
    /** هل يحتاج هذا البوت موافقة صريحة؟ (أثر خارجي + محفّز غير يدوي) */
    fun needsApproval(def: BotDefinition): Boolean =
        def.steps.any { it.externalEffect } && def.trigger != BotTrigger.Manual

    /**
     * يحسب قيمة الموافقة عند الحفظ.
     * - مربع الموافقة هو المصدر الوحيد؛ قيمة JSON المكتوبة يدويًا تُتجاهل.
     * - إذا تغيّرت الخطوات أو المحفّز أو الحزم عن النسخة المحفوظة ولم يلمس المستخدم المربع، تُلغى الموافقة.
     * - بوت لا يحتاج موافقة يُحفظ دائمًا بدون موافقة.
     */
    fun resolve(edited: BotDefinition, saved: BotDefinition?, checkbox: Boolean, checkboxTouched: Boolean): Boolean {
        if (!needsApproval(edited)) return false
        val changed = saved == null ||
            saved.steps != edited.steps ||
            saved.trigger != edited.trigger ||
            saved.allowedPackages != edited.allowedPackages
        if (changed && !checkboxTouched) return false
        return checkbox
    }
}
