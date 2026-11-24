package com.phonepilot.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phonepilot.app.bot.BotAction
import com.phonepilot.app.bot.BotDefinition
import com.phonepilot.app.bot.BotTemplates
import com.phonepilot.app.bot.BotTrigger
import com.phonepilot.app.bot.BotValidator
import com.phonepilot.app.ui.screens.BotFormEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Drives the form editor with real clicks and typing, then saves a full-height PNG. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-rSA-w411dp-h4200dp-xxhdpi")
class BotFormEditorTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }
    private var current: BotDefinition = BotTemplates.scheduledWhatsApp("b1", "اسم الجهة", "نص الرسالة", 9, 0)

    private fun show() {
        rule.setContent {
            MaterialTheme {
                var def by remember { mutableStateOf(current) }
                current = def
                Column(Modifier.background(androidx.compose.ui.graphics.Color.White).padding(16.dp)) {
                    BotFormEditor(def = def, onChange = { def = it; current = it })
                }
            }
        }
    }

    private fun save(name: String) {
        rule.waitForIdle()
        var bmp: android.graphics.Bitmap? = null
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            bmp = android.graphics.Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(bmp!!))
        }
        File(outDir, "$name.png").outputStream().use { bmp!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun editNameTriggerStepsAndRender() {
        show()
        save("20_form_editor_template")
        rule.onNodeWithTag("form_name").performTextClearance()
        rule.onNodeWithTag("form_name").performTextInput("بوت تجريبي")
        assertEquals("بوت تجريبي", current.name)

        rule.onNodeWithTag("form_trigger_EVERY").performClick()
        assertTrue(current.trigger is BotTrigger.EveryMinutes)
        rule.onNodeWithTag("form_minutes").performTextClearance()
        rule.onNodeWithTag("form_minutes").performTextInput("30")
        assertEquals(BotTrigger.EveryMinutes(30), current.trigger)

        val before = current.steps.size
        rule.onNodeWithTag("form_add_CLICK").performClick()
        assertEquals(before + 1, current.steps.size)
        rule.onNodeWithTag("form_step_${before}_a").performTextInput("إرسال")
        assertEquals(BotAction.Click("إرسال"), current.steps.last().action)
        rule.onNodeWithTag("form_step_${before}_external").performClick()
        assertTrue(current.steps.last().externalEffect)
        rule.onNodeWithTag("form_step_${before}_external").assertIsOn()

        rule.onNodeWithTag("form_step_${before}_delete").performClick()
        assertEquals(before, current.steps.size)

        rule.onNodeWithTag("form_add_cond_text").performClick()
        rule.onNodeWithTag("form_cond_0_value").performTextInput("مرحبا")
        assertEquals(1, current.conditions.size)

        assertTrue(BotValidator.validate(current.copy(unattendedApproved = true)).isEmpty())
        save("21_form_editor_edited")
    }
}
