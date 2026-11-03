package com.reggiesoft.dbmeter

import android.Manifest
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.Espresso
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test

class MeterAppTest {
    @get:Rule(order = 0)
    val microphonePermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun liveMeterNavigationTransportAndSettingsWork() {
        compose.waitUntil(timeoutMillis = 10_000) { compose.textForTag("meter-reading-a") != "—" }
        compose.onNodeWithTag("meter-reading-a").assertExists()
        compose.onNodeWithTag("meter-reading-c").assertExists()

        compose.onNodeWithTag("speed-slow").performClick()
        compose.onNodeWithTag("speed-fast").performClick()
        compose.onNodeWithTag("transport-pause").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.descriptionForTag("transport-pause") == "Resume measurement" }

        compose.onNodeWithTag("transport-reset").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.textForTag("meter-reading-a") == "—" && compose.textForTag("meter-reading-c") == "—"
        }
        compose.onNodeWithTag("transport-pause").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.descriptionForTag("transport-pause") == "Pause measurement" }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.textForTag("meter-reading-a") != null && compose.textForTag("meter-reading-a") != "—"
        }

        compose.onNodeWithTag("nav-rta").performClick()
        compose.onNodeWithTag("spectrum-chart").assertExists()
        compose.onNodeWithTag("spectrum-chart").performTouchInput { click(center) }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("Microphone").assertExists()
        compose.onNodeWithText("Calibration file").assertExists()
        compose.onNodeWithText("Calibration").assertExists()

        compose.onNodeWithText("Keep screen awake").performClick()
        compose.onNodeWithText("Keep screen awake").performClick()

        compose.onAllNodesWithText("Microphone").onFirst().performClick()
        compose.onNodeWithText("Apply").assertExists()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onAllNodesWithText("Microphone").onFirst().performClick()
        compose.onAllNodesWithText("Automatic").onLast().performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithTag("nav-meter").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.descriptionForTag("transport-pause") == "Pause measurement" }
    }
}

private fun ComposeTestRule.textForTag(tag: String): String? {
    val node = onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull() ?: return null
    return node.config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(separator = "") { it.text }
}

private fun ComposeTestRule.descriptionForTag(tag: String): String? {
    val node = onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull() ?: return null
    return node.config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(separator = "")
}
