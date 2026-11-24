package com.phonepilot.app.documents

import java.math.BigDecimal

/**
 * استخراج حتمي (بدون نموذج) للأرقام والمبالغ من نص مستند.
 *
 * ما هو مستخرج هنا يُحسب بالكود. أي تفسير محاسبي يضيفه نموذج ذكاء اصطناعي لاحقًا يجب أن يُعرض
 * على أنه تفسير وليس حقيقة مستخرجة.
 */
data class AmountFinding(
    val line: Int,
    val raw: String,
    val value: BigDecimal,
    /** رمز العملة إن وُجدت علامة عملة واحدة واضحة في نفس السطر، وإلا null. */
    val currency: String?,
)

data class AmbiguousFinding(val line: Int, val raw: String, val reason: String)

data class DocumentFacts(
    val lineCount: Int,
    val truncated: Boolean,
    val amounts: List<AmountFinding>,
    val ambiguous: List<AmbiguousFinding>,
    /** مجموع المبالغ التي لها عملة واضحة، لكل عملة. لا يشمل الأرقام بدون عملة. */
    val totalsByCurrency: Map<String, BigDecimal>,
    val unclassifiedCount: Int,
)

object DocumentIntake {
    const val MAX_CHARS = 60_000

    private val CURRENCIES = listOf(
        "SAR" to listOf("sar", "ر.س", "ريال سعودي", "ريال", "﷼"),
        "EGP" to listOf("egp", "ج.م", "جنيه مصري", "جنيه"),
        "USD" to listOf("usd", "$", "دولار"),
        "AED" to listOf("aed", "د.إ", "درهم"),
        "EUR" to listOf("eur", "€", "يورو"),
    )

    private val TOKEN = Regex("""\d[\d.,]*\d|\d""")

    fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    '\u066B' -> '.'
                    '\u066C' -> ','
                    else -> c
                },
            )
        }
        return sb.toString()
    }

    /** يفسّر رمزًا رقميًا. يعيد القيمة، أو سببًا للغموض. */
    sealed class Parsed {
        data class Ok(val value: BigDecimal) : Parsed()
        data class Unclear(val reason: String) : Parsed()
    }

    fun parseToken(t: String): Parsed {
        val commas = t.count { it == ',' }
        val dots = t.count { it == '.' }
        return try {
            when {
                commas == 0 && dots == 0 -> Parsed.Ok(BigDecimal(t))
                commas == 0 && dots == 1 -> {
                    val frac = t.substringAfter('.')
                    if (frac.length == 3) Parsed.Unclear("فاصلة عشرية أم فاصل آلاف: $t")
                    else Parsed.Ok(BigDecimal(t))
                }
                commas == 0 -> Parsed.Unclear("أكثر من نقطة: $t")
                dots == 0 && commas == 1 && t.substringAfter(',').length != 3 ->
                    Parsed.Unclear("فاصلة عشرية أوروبية أم تنسيق غير معتاد: $t")
                dots == 0 -> {
                    val groups = t.split(',')
                    if (groups.drop(1).all { it.length == 3 } && groups.first().length in 1..3) {
                        Parsed.Ok(BigDecimal(t.replace(",", "")))
                    } else Parsed.Unclear("تجميع آلاف غير منتظم: $t")
                }
                else -> {
                    // نقاط وفواصل معًا: الأخيرة هي العشرية، والباقي يجب أن يكون تجميع آلاف منتظمًا
                    val lastDot = t.lastIndexOf('.')
                    val lastComma = t.lastIndexOf(',')
                    val dec = maxOf(lastDot, lastComma)
                    val intPart = t.substring(0, dec)
                    val frac = t.substring(dec + 1)
                    val sep = if (dec == lastDot) ',' else '.'
                    val groups = intPart.split(sep)
                    val regular = !intPart.contains(if (sep == ',') '.' else ',') &&
                        groups.drop(1).all { it.length == 3 } && groups.first().length in 1..3
                    if (regular && frac.length in 1..3) {
                        Parsed.Ok(BigDecimal(intPart.replace(sep.toString(), "") + "." + frac))
                    } else Parsed.Unclear("تنسيق رقم غير واضح: $t")
                }
            }
        } catch (e: NumberFormatException) {
            Parsed.Unclear("رقم غير مقروء: $t")
        }
    }

    private fun hasMark(lower: String, mark: String): Boolean =
        if (mark.all { it in 'a'..'z' }) Regex("\\b" + mark + "\\b").containsMatchIn(lower) else lower.contains(mark)

    fun currencyOfLine(line: String): String? {
        val lower = line.lowercase()
        val hits = CURRENCIES.filter { (_, marks) -> marks.any { hasMark(lower, it) } }.map { it.first }
        return if (hits.size == 1) hits[0] else null
    }

    fun analyze(raw: String): DocumentFacts {
        val truncated = raw.length > MAX_CHARS
        val text = normalizeDigits(raw.take(MAX_CHARS))
        val lines = text.lines()
        val amounts = mutableListOf<AmountFinding>()
        val ambiguous = mutableListOf<AmbiguousFinding>()
        lines.forEachIndexed { i, line ->
            val cur = currencyOfLine(line)
            for (m in TOKEN.findAll(line)) {
                val tok = m.value
                val before = line.substring(0, m.range.first).trimEnd()
                val after = line.substring(m.range.last + 1).trimStart()
                val negative = before.endsWith("-") || (before.endsWith("(") && after.startsWith(")"))
                when (val p = parseToken(tok)) {
                    is Parsed.Ok -> amounts += AmountFinding(i + 1, tok, if (negative) p.value.negate() else p.value, cur)
                    is Parsed.Unclear -> ambiguous += AmbiguousFinding(i + 1, tok, p.reason)
                }
            }
        }
        val totals = amounts.filter { it.currency != null }
            .groupBy { it.currency!! }
            .mapValues { (_, v) -> v.fold(BigDecimal.ZERO) { a, f -> a + f.value } }
        return DocumentFacts(
            lineCount = lines.size,
            truncated = truncated,
            amounts = amounts,
            ambiguous = ambiguous,
            totalsByCurrency = totals,
            unclassifiedCount = amounts.count { it.currency == null },
        )
    }

    /**
     * نص الطلب الذي يمكن إرساله إلى نموذج ذكاء اصطناعي. لا يُرسَل تلقائيًا.
     * الأرقام المحسوبة تُمرَّر كحقائق، والنموذج يُطلب منه التفسير فقط وعدم إعادة الحساب.
     */
    fun buildAnalysisPrompt(documentText: String, facts: DocumentFacts): String = buildString {
        appendLine("حلّل المستند التالي محاسبيًا.")
        appendLine("قواعد: اعتمد فقط على نص المستند. لا تخمّن أرقامًا غير موجودة. لا تعِد حساب المجاميع، استخدم المجاميع المحسوبة أدناه.")
        appendLine("افصل إجابتك إلى قسمين: (1) ما هو مكتوب في المستند، (2) تفسيرك. وضّح أي بند غير واضح أو ناقص بدل افتراضه.")
        appendLine()
        appendLine("المجاميع المحسوبة بالكود (للمبالغ التي لها عملة واضحة في نفس السطر فقط):")
        if (facts.totalsByCurrency.isEmpty()) appendLine("- لا توجد مبالغ بعملة واضحة")
        facts.totalsByCurrency.forEach { (c, v) -> appendLine("- $c: ${v.toPlainString()}") }
        appendLine("أرقام بدون عملة واضحة (غير مجمّعة): ${facts.unclassifiedCount}")
        if (facts.ambiguous.isNotEmpty()) {
            appendLine("أرقام بتنسيق غير واضح (غير محسوبة): " + facts.ambiguous.joinToString("، ") { "سطر ${it.line}: ${it.raw}" })
        }
        if (facts.truncated) appendLine("تنبيه: المستند اقتُطع عند $MAX_CHARS حرفًا.")
        appendLine()
        appendLine("نص المستند:")
        append(documentText.take(MAX_CHARS))
    }
}
