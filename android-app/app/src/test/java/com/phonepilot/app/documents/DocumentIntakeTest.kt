package com.phonepilot.app.documents

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentIntakeTest {
    private fun ok(t: String) = (DocumentIntake.parseToken(t) as DocumentIntake.Parsed.Ok).value
    private fun unclear(t: String) = DocumentIntake.parseToken(t) is DocumentIntake.Parsed.Unclear

    @Test fun `plain and grouped numbers parse`() {
        assertEquals(BigDecimal("1500"), ok("1500"))
        assertEquals(BigDecimal("1234"), ok("1,234"))
        assertEquals(BigDecimal("1234567.89"), ok("1,234,567.89"))
        assertEquals(BigDecimal("1234.5"), ok("1234.5"))
        assertEquals(BigDecimal("1234.56"), ok("1.234,56"))
    }

    @Test fun `ambiguous formats are not guessed`() {
        assertTrue(unclear("1.234"))
        assertTrue(unclear("12,34"))
        assertTrue(unclear("1,2345"))
        assertTrue(unclear("1.2.3"))
    }

    @Test fun `arabic indic digits are normalized`() {
        assertEquals("1234.5", DocumentIntake.normalizeDigits("١٢٣٤٫٥"))
        assertEquals("1,234", DocumentIntake.normalizeDigits("١٬٢٣٤"))
    }

    @Test fun `totals only include amounts with one clear currency`() {
        val f = DocumentIntake.analyze(
            """
            فاتورة رقم 1042
            خدمات 1,000.50 SAR
            ضريبة 150.08 ريال
            شحن 200 USD
            ملاحظة 7
            """.trimIndent(),
        )
        assertEquals(BigDecimal("1150.58"), f.totalsByCurrency["SAR"])
        assertEquals(BigDecimal("200"), f.totalsByCurrency["USD"])
        // 1042 و 7 بدون عملة
        assertEquals(2, f.unclassifiedCount)
    }

    @Test fun `line with two currencies is not classified`() {
        val f = DocumentIntake.analyze("100 SAR = 26 USD")
        assertTrue(f.totalsByCurrency.isEmpty())
        assertEquals(2, f.unclassifiedCount)
        assertNull(DocumentIntake.currencyOfLine("100 SAR = 26 USD"))
    }

    @Test fun `negative amounts and parentheses`() {
        val f = DocumentIntake.analyze("خصم -50 SAR\nمرتجع (20) SAR\nبيع 100 SAR")
        assertEquals(BigDecimal("30"), f.totalsByCurrency["SAR"])
    }

    @Test fun `ambiguous numbers are listed and excluded from totals`() {
        val f = DocumentIntake.analyze("بند 1.234 SAR\nبند 100 SAR")
        assertEquals(BigDecimal("100"), f.totalsByCurrency["SAR"])
        assertEquals(1, f.ambiguous.size)
        assertEquals(1, f.ambiguous[0].line)
    }

    @Test fun `long documents are truncated and flagged`() {
        val f = DocumentIntake.analyze("1 SAR\n".repeat(20_000))
        assertTrue(f.truncated)
    }

    @Test fun `prompt carries computed totals and the do not recompute rule, and is not sent anywhere`() {
        val text = "خدمة 100 SAR"
        val p = DocumentIntake.buildAnalysisPrompt(text, DocumentIntake.analyze(text))
        assertTrue(p.contains("SAR: 100"))
        assertTrue(p.contains("لا تعِد حساب المجاميع"))
        assertTrue(p.endsWith(text))
        assertFalse(p.contains("http"))
    }

    @Test fun `iso dates on a currency line are excluded not summed`() {
        val f = DocumentIntake.analyze("بتاريخ 2026-11-24 خدمة 100 SAR")
        assertEquals(BigDecimal("100"), f.totalsByCurrency["SAR"])
        assertTrue(f.excluded.any { it.reason.contains("تاريخ") })
    }

    @Test fun `slash dates percentages and long ids are excluded`() {
        val f = DocumentIntake.analyze("فاتورة 123456789 في 24/11/2026 ضريبة 15% مبلغ 200 SAR")
        assertEquals(BigDecimal("200"), f.totalsByCurrency["SAR"])
        assertEquals(5, f.excluded.size)
    }

    @Test fun `line with two numbers is withheld and reviewed`() {
        val f = DocumentIntake.analyze("15% ثم 40 SAR و 60 SAR")
        assertTrue(f.totalsByCurrency.isEmpty())
        assertEquals(1, f.review.size)
    }

    @Test fun `small invoice id on a currency line withholds the line`() {
        val f = DocumentIntake.analyze("فاتورة 12 خدمات 500 SAR\nشحن 30 SAR")
        assertEquals(BigDecimal("30"), f.totalsByCurrency["SAR"])
        assertEquals(1, f.review.size)
        assertEquals(1, f.review[0].line)
    }

    @Test fun `quantity times price line is withheld`() {
        val f = DocumentIntake.analyze("3 x 50 SAR")
        assertTrue(f.totalsByCurrency.isEmpty())
        assertEquals(1, f.review.size)
    }

    @Test fun `hyphenated reference is not an amount and not negative`() {
        val f = DocumentIntake.analyze("INV-12 مبلغ 100 SAR")
        assertEquals(BigDecimal("100"), f.totalsByCurrency["SAR"])
        assertTrue(f.excluded.any { it.raw == "12" })
        val g = DocumentIntake.analyze("A-100 SAR")
        assertTrue(g.totalsByCurrency.isEmpty())
    }

    @Test fun `number before and after the currency word is ambiguous`() {
        val f = DocumentIntake.analyze("بند 7 SAR 100")
        assertTrue(f.totalsByCurrency.isEmpty())
        assertEquals(1, f.review.size)
    }

    @Test fun `number far from currency word is not summed`() {
        val f = DocumentIntake.analyze("المبلغ 250 المستحق بالريال السعودي SAR")
        assertTrue(f.totalsByCurrency.isEmpty())
        assertEquals(1, f.review.size)
    }

    @Test fun `prompt lists summed line items and the not-an-amount warning`() {
        val text = "خدمة 100 SAR"
        val p = DocumentIntake.buildAnalysisPrompt(text, DocumentIntake.analyze(text))
        assertTrue(p.contains("البنود التي دخلت في المجاميع"))
        assertTrue(p.contains("سطر 1: 100 SAR"))
        assertTrue(p.contains("ليس مبلغًا"))
    }
}
