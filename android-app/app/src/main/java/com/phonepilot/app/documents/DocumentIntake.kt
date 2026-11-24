package com.phonepilot.app.documents

import java.math.BigDecimal

/**
 * استخراج حتمي (بدون نموذج) للأرقام والمبالغ من نص مستند.
 *
 * ما هو مستخرج هنا يُحسب بالكود. أي تفسير محاسبي يضيفه نموذج ذكاء اصطناعي لاحقًا يجب أن يُعرض
 * على أنه تفسير وليس حقيقة مستخرجة.
 *
 * حدود معروفة: الأرقام التي تبدو تواريخ أو معرّفات طويلة أو نسبًا مئوية تُستبعد وتُعرض سببًا،
 * لكن قد يتسلل رقم ليس مبلغًا إلى المجاميع رغم ذلك. المجاميع مساعد أولي يجب التحقق منه
 * مقابل المستند، وليست ناتجًا محاسبيًا موثوقًا.
 */
data class AmountFinding(
    val line: Int,
    val raw: String,
    val value: BigDecimal,
    /** رمز العملة إن وُجدت علامة عملة واحدة واضحة في نفس السطر، وإلا null. */
    val currency: String?,
    /** نص السطر الذي ورد فيه الرقم (مقتطع)، للمراجعة البشرية بندًا بندًا. */
    val lineText: String = "",
)

data class AmbiguousFinding(val line: Int, val raw: String, val reason: String)

/** رقم استُبعد من الحساب لأنه يبدو تاريخًا أو معرّفًا أو نسبة، لا مبلغًا. */
data class ExcludedFinding(val line: Int, val raw: String, val reason: String)

data class DocumentFacts(
    val lineCount: Int,
    val truncated: Boolean,
    val amounts: List<AmountFinding>,
    val ambiguous: List<AmbiguousFinding>,
    /** أرقام استُبعدت لأنها تبدو تواريخ أو معرّفات أو نسبًا. */
    val excluded: List<ExcludedFinding>,
    /** مجموع المبالغ التي لها عملة واضحة، لكل عملة. لا يشمل الأرقام بدون عملة ولا المستبعدة. */
    val totalsByCurrency: Map<String, BigDecimal>,
    val unclassifiedCount: Int,
    /** أسطر بعملة لم تُجمَّع لأن الاستخراج غير مؤكد (أكثر من رقم، أو رقم غير ملاصق للعملة). تحتاج مراجعة بشرية. */
    val review: List<ReviewFinding> = emptyList(),
)

data class ReviewFinding(val line: Int, val numbers: List<String>, val reason: String, val lineText: String)

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

    /** أنماط تاريخ شائعة؛ أي رقم داخل تطابق منها لا يُعدّ مبلغًا. */
    private val DATE_PATTERNS = listOf(
        Regex("""\d{4}-\d{1,2}-\d{1,2}"""),
        Regex("""\d{1,2}/\d{1,2}/\d{2,4}"""),
        Regex("""\d{1,2}-\d{1,2}-\d{2,4}"""),
        Regex("""\d{1,2}\.\d{1,2}\.\d{2,4}"""),
    )

    /** عدد صحيح طويل بلا فواصل: غالبًا معرّف أو هاتف أو رقم فاتورة، لا مبلغ. */
    private val LONG_ID = Regex("""^\d{7,}$""")

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

    /** سبب استبعاد الرقم من الحساب إن بدا تاريخًا أو نسبة أو معرّفًا، وإلا null. */
    fun exclusionReason(line: String, range: IntRange, token: String): String? {
        for (p in DATE_PATTERNS) {
            for (dm in p.findAll(line)) {
                if (range.first <= dm.range.last && dm.range.first <= range.last) return "يبدو جزءًا من تاريخ"
            }
        }
        if (range.last + 1 < line.length && line[range.last + 1] == '%') return "نسبة مئوية"
        if (range.first > 0 && line[range.first - 1] == '+') return "يبدو رقم هاتف"
        if (LONG_ID.matches(token)) return "رقم طويل بلا فواصل (معرّف أو هاتف أو رقم فاتورة محتمل)"
        return null
    }

    private val MARK_SPANS_CACHE = CURRENCIES.flatMap { it.second }

    /** مواضع علامات العملة في السطر (بعد تحويله لأحرف صغيرة). */
    private fun markSpans(lower: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        for (mark in MARK_SPANS_CACHE) {
            if (mark.all { it in 'a'..'z' }) {
                Regex("\\b" + mark + "\\b").findAll(lower).forEach { out += it.range }
            } else {
                var i = lower.indexOf(mark)
                while (i >= 0) { out += i until (i + mark.length); i = lower.indexOf(mark, i + 1) }
            }
        }
        return out
    }

    private val GAP = Regex("""^[\s)(:]{0,3}$""")

    /** هل الرقم ملاصق مباشرة لعلامة عملة (قبلها أو بعدها)؟ */
    private fun adjacentToMark(line: String, lower: String, range: IntRange, marks: List<IntRange>): Boolean =
        marks.any { m ->
            if (m.first > range.last) GAP.matches(line.substring(range.last + 1, m.first))
            else if (m.last < range.first) GAP.matches(line.substring(m.last + 1, range.first))
            else false
        }

    /** هل الرقم جزء من مرجع مثل INV-12 أو A-100 (حرف ثم شرطة ثم رقم)؟ */
    private fun looksLikeReference(line: String, range: IntRange): Boolean {
        val b = range.first
        if (b >= 2 && line[b - 1] == '-' && line[b - 2].isLetterOrDigit()) return true
        if (range.last + 2 < line.length && line[range.last + 1] == '-' && line[range.last + 2].isLetterOrDigit()) return true
        return false
    }

    fun analyze(raw: String): DocumentFacts {
        val truncated = raw.length > MAX_CHARS
        val text = normalizeDigits(raw.take(MAX_CHARS))
        val lines = text.lines()
        val amounts = mutableListOf<AmountFinding>()
        val ambiguous = mutableListOf<AmbiguousFinding>()
        val excluded = mutableListOf<ExcludedFinding>()
        val review = mutableListOf<ReviewFinding>()
        lines.forEachIndexed { i, line ->
            val cur = currencyOfLine(line)
            val lower = line.lowercase()
            val marks = if (cur != null) markSpans(lower) else emptyList()
            class Cand(val tok: String, val range: IntRange, val value: BigDecimal)
            val cands = mutableListOf<Cand>()
            var lineAmbiguous = false
            for (m in TOKEN.findAll(line)) {
                val tok = m.value
                val reason = exclusionReason(line, m.range, tok)
                    ?: if (looksLikeReference(line, m.range)) "جزء من مرجع (حرف-رقم) وليس مبلغًا" else null
                if (reason != null) { excluded += ExcludedFinding(i + 1, tok, reason); continue }
                val before = line.substring(0, m.range.first).trimEnd()
                val after = line.substring(m.range.last + 1).trimStart()
                val rawBefore = line.substring(0, m.range.first)
                val negative = (rawBefore.endsWith("-") && (rawBefore.length < 2 || !rawBefore[rawBefore.length - 2].isLetterOrDigit())) ||
                    (before.endsWith("(") && after.startsWith(")"))
                when (val p = parseToken(tok)) {
                    is Parsed.Ok -> cands += Cand(tok, m.range, if (negative) p.value.negate() else p.value)
                    is Parsed.Unclear -> { ambiguous += AmbiguousFinding(i + 1, tok, p.reason); lineAmbiguous = true }
                }
            }
            val lineText = line.trim().take(120)
            // مبلغ موثوق: سطر بعملة واحدة واضحة، رقم واحد فقط صالح فيه، وملاصق لعلامة العملة، ولا غموض آخر في السطر.
            val confident = cur != null && cands.size == 1 && !lineAmbiguous &&
                adjacentToMark(line, lower, cands[0].range, marks)
            if (confident) {
                val c = cands[0]
                amounts += AmountFinding(i + 1, c.tok, c.value, cur, lineText)
            } else {
                cands.forEach { amounts += AmountFinding(i + 1, it.tok, it.value, null, lineText) }
                if (cur != null && cands.isNotEmpty()) {
                    review += ReviewFinding(
                        i + 1, cands.map { it.tok },
                        "سطر بعملة فيه أكثر من رقم أو رقم غير ملاصق للعملة أو غموض: لم يُجمَّع، راجعه يدويًا",
                        lineText,
                    )
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
            excluded = excluded,
            totalsByCurrency = totals,
            unclassifiedCount = amounts.count { it.currency == null },
            review = review,
        )
    }

    /**
     * نص الطلب الذي يمكن إرساله إلى نموذج ذكاء اصطناعي. لا يُرسَل تلقائيًا.
     * الأرقام المحسوبة تُمرَّر كحقائق مع بنودها سطرًا سطرًا، والنموذج يُطلب منه التفسير
     * والتنبيه لأي بند يبدو أنه ليس مبلغًا، دون إعادة الحساب.
     */
    fun buildAnalysisPrompt(documentText: String, facts: DocumentFacts): String = buildString {
        appendLine("حلّل المستند التالي محاسبيًا.")
        appendLine("قواعد: اعتمد فقط على نص المستند. لا تخمّن أرقامًا غير موجودة. لا تعِد حساب المجاميع، استخدم المجاميع المحسوبة أدناه.")
        appendLine("افصل إجابتك إلى قسمين: (1) ما هو مكتوب في المستند، (2) تفسيرك. وضّح أي بند غير واضح أو ناقص بدل افتراضه.")
        appendLine("تنبيه مهم: المجاميع أدناه حسبها كود آلي وقد تتضمن رقمًا ليس مبلغًا (تاريخ أو رقم فاتورة أو معرّف) رغم الترشيح. إن بدا لك بند من البنود المدرجة أنه ليس مبلغًا فنبّه لذلك صراحة، ولا تغيّر المجاميع بنفسك.")
        appendLine()
        appendLine("المجاميع المحسوبة بالكود (للمبالغ التي لها عملة واضحة في نفس السطر فقط):")
        if (facts.totalsByCurrency.isEmpty()) appendLine("- لا توجد مبالغ بعملة واضحة")
        facts.totalsByCurrency.forEach { (c, v) -> appendLine("- $c: ${v.toPlainString()}") }
        val summed = facts.amounts.filter { it.currency != null }
        if (summed.isNotEmpty()) {
            appendLine("البنود التي دخلت في المجاميع:")
            summed.forEach { appendLine("- سطر ${it.line}: ${it.raw} ${it.currency} — ${it.lineText}") }
        }
        appendLine("أرقام بدون عملة واضحة (غير مجمّعة): ${facts.unclassifiedCount}")
        if (facts.review.isNotEmpty()) {
            appendLine("أسطر بعملة لم تُجمَّع لأن الاستخراج غير مؤكد (فسّرها أنت من النص ونبّه أنها غير محسوبة):")
            facts.review.forEach { appendLine("- سطر ${it.line}: ${it.numbers.joinToString("، ")} — ${it.lineText}") }
        }
        if (facts.excluded.isNotEmpty()) {
            appendLine("أرقام استُبعدت لأنها تبدو تواريخ أو معرّفات أو نسبًا (غير محسوبة): " + facts.excluded.joinToString("، ") { "سطر ${it.line}: ${it.raw}" })
        }
        if (facts.ambiguous.isNotEmpty()) {
            appendLine("أرقام بتنسيق غير واضح (غير محسوبة): " + facts.ambiguous.joinToString("، ") { "سطر ${it.line}: ${it.raw}" })
        }
        if (facts.truncated) appendLine("تنبيه: المستند اقتُطع عند $MAX_CHARS حرفًا.")
        appendLine()
        appendLine("نص المستند:")
        append(documentText.take(MAX_CHARS))
    }
}
