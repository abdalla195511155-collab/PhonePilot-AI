package com.phonepilot.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import com.phonepilot.app.bot.BotJson
import com.phonepilot.app.bot.BotTrigger
import com.phonepilot.app.bot.InMemoryBotStore
import com.phonepilot.app.ui.screens.BotsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Drives the real BotsScreen dialog on a phone-sized window (411x891dp, ar-SA):
 * form editing, JSON toggle round trip, save visibility, approval rule on save, re-edit.
 * The PNGs capture the dialog window itself.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-rSA-w411dp-h891dp-xxhdpi")
class BotsDialogScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }
    private val store = InMemoryBotStore()

    @Before
    fun initWork() {
        try {
            WorkManager.initialize(
                ApplicationProvider.getApplicationContext(),
                Configuration.Builder().setExecutor { it.run() }.build(),
            )
        } catch (e: IllegalStateException) {
            // already initialized
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val roots = rule.onAllNodes(isRoot())
        val last = roots.fetchSemanticsNodes().size - 1
        val bmp = roots[last].captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun text(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

    @Test
    fun fullDialogFlow() {
        rule.setContent { MaterialTheme { BotsScreen(onNavigateTo = {}, storeOverride = store) } }
        rule.onNodeWithTag("bots_new_whatsapp").performClick()

        // Save and the mode toggle are reachable without scrolling on a phone-sized window.
        rule.onNodeWithTag("bots_save").assertIsDisplayed()
        rule.onNodeWithTag("bots_mode_toggle").assertIsDisplayed()
        rule.onNodeWithTag("form_name").assertIsDisplayed()
        shot("22_dialog_form_top")

        // Form edit, then JSON toggle keeps every field.
        rule.onNodeWithTag("form_name").performTextReplacement("بوت من النموذج")
        rule.onNodeWithTag("bots_mode_toggle").performClick()
        rule.onNodeWithTag("bots_editor").assertIsDisplayed()
        val parsed = BotJson.parse(text("bots_editor"))
        assertEquals("بوت من النموذج", parsed.name)
        assertEquals(6, parsed.steps.size)
        assertEquals(BotTrigger.DailyAt(9, 0), parsed.trigger)
        assertTrue(parsed.steps.last().externalEffect)
        assertFalse(parsed.unattendedApproved)
        rule.onNodeWithTag("bots_save").assertIsDisplayed()
        shot("23_dialog_json")

        // Edit in JSON, toggle back: the form shows the JSON edit.
        rule.onNodeWithTag("bots_editor").performTextReplacement(BotJson.toPrettyJson(parsed.copy(name = "اسم من JSON")))
        rule.onNodeWithTag("bots_mode_toggle").performClick()
        rule.onNodeWithTag("form_name").assertIsDisplayed()
        assertEquals("اسم من JSON", text("form_name"))

        // Invalid JSON keeps the user in JSON mode instead of losing the draft.
        rule.onNodeWithTag("bots_mode_toggle").performClick()
        rule.onNodeWithTag("bots_editor").performTextReplacement("{")
        rule.onNodeWithTag("bots_mode_toggle").performClick()
        rule.onNodeWithTag("bots_editor").assertIsDisplayed()
        rule.onNodeWithTag("bots_editor").performTextReplacement(BotJson.toPrettyJson(parsed.copy(name = "اسم من JSON")))
        rule.onNodeWithTag("bots_mode_toggle").performClick()

        // Scheduled bot with an external step: saving without the approval checkbox is refused.
        rule.onNodeWithTag("bots_approval_checkbox").performScrollTo()
        rule.onNodeWithTag("bots_approval_checkbox").assertIsDisplayed()
        shot("24_dialog_approval_scrolled")
        rule.onNodeWithTag("bots_save").performClick()
        assertTrue(store.list().isEmpty())

        // Ticking the checkbox allows the save.
        rule.onNodeWithTag("bots_approval_checkbox").performClick()
        rule.onNodeWithTag("bots_save").performClick()
        assertEquals(1, store.list().size)
        val saved = store.list().first()
        assertEquals("اسم من JSON", saved.name)
        assertTrue(saved.unattendedApproved)
        assertEquals(6, saved.steps.size)

        // Re-open: form shows saved values. Changing the trigger without touching the checkbox drops approval, so save is refused.
        rule.onNodeWithText("تعديل").performClick()
        assertEquals("اسم من JSON", text("form_name"))
        rule.onNodeWithTag("form_trigger_EVERY").performClick()
        rule.onNodeWithTag("bots_save").performClick()
        assertEquals(BotTrigger.DailyAt(9, 0), store.list().first().trigger)
        assertTrue(store.list().first().unattendedApproved)
        assertNotNull(store.get(saved.id))
    }
}
