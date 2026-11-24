package com.phonepilot.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phonepilot.app.ui.navigation.AppRoutes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Navigates every screen from the real UI and saves a PNG of each one. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-rSA-w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }
    private val failures = mutableListOf<String>()

    private fun shot(name: String) {
        try {
            rule.mainClock.advanceTimeBy(1000)
            var bmp: Bitmap? = null
            rule.runOnUiThread {
                val v = rule.activity.window.decorView
                bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp!!))
            }
            File(outDir, "$name.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Throwable) {
            failures += "shot $name: ${e::class.simpleName} ${e.message?.take(200)}"
        }
    }

    private fun click(tag: String) {
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode("no node $tag")
        val action = node.config[SemanticsActions.OnClick].action
        rule.runOnUiThread { action?.invoke() }
        rule.mainClock.advanceTimeBy(800)
    }

    private fun step(name: String, block: () -> Unit) {
        try { block() } catch (e: Throwable) {
            failures += "step $name: ${e::class.simpleName} ${e.message?.take(300)}"
        }
    }

    @Test
    fun walkAllScreens() {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)
        shot("01_dashboard")
        step("skills") { click("nav_${AppRoutes.SkillLibrary}") }
        shot("02_skills")
        step("agent") { click("nav_${AppRoutes.AgentControl}") }
        shot("03_agent")
        step("settings") { click("nav_${AppRoutes.Settings}") }
        shot("04_settings")
        val sub = listOf(
            AppRoutes.AiModelsApiSettings to "05_api_keys",
            AppRoutes.PrivacyPermissions to "06_privacy",
            AppRoutes.AgentApproval to "07_approvals",
            AppRoutes.AiAssistant to "08_assistant",
            AppRoutes.ExecutionMonitor to "09_monitor",
            AppRoutes.LogsHistory to "10_logs",
        )
        for ((route, name) in sub) {
            step("open $name") { click("settings_$route") }
            shot(name)
            step("back $name") { click("back") }
        }
        File(outDir, "failures.txt").writeText(failures.joinToString("\n"))
        assertTrue("UI walk failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
