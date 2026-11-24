package com.phonepilot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phonepilot.app.ui.screens.ApprovalSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the unattended-approval section (unchecked, then ticked by a real click) and saves PNGs. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-rSA-w411dp-h891dp-xxhdpi")
class ApprovalScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

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
    fun approvalSectionUncheckedThenChecked() {
        rule.setContent {
            MaterialTheme {
                var checked by androidx.compose.runtime.remember { mutableStateOf(false) }
                Column(Modifier.background(androidx.compose.ui.graphics.Color.White).padding(16.dp)) {
                    Text("تعديل البوت (JSON)")
                    ApprovalSection(checked = checked, onChange = { checked = it })
                }
            }
        }
        rule.onNodeWithTag("bots_approval_warning").assertExists()
        rule.onNodeWithTag("bots_approval_checkbox").assertIsOff()
        save("12_approval_unchecked")
        rule.onNodeWithTag("bots_approval_checkbox").performClick()
        rule.onNodeWithTag("bots_approval_checkbox").assertIsOn()
        save("13_approval_checked")
    }
}
