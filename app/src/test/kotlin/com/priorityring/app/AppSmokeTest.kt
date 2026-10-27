package com.priorityring.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Launches the real MainActivity on the JVM (Robolectric) with the real Hilt graph, Room and
 * DataStore, and clicks through every tab. It proves the app starts and the screens compose
 * without crashing; it says nothing about real-device behaviour (that's the phone checklist).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class AppSmokeTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app starts and every tab renders`() {
        compose.onNodeWithText("Master switch").assertIsDisplayed()
        // Notification access is never granted in a test environment, so Home must say so.
        compose.onNodeWithText("NEEDS ATTENTION").assertIsDisplayed()

        compose.onNodeWithText("Macros").performClick()
        compose.onNodeWithText("No macros yet").assertIsDisplayed()

        compose.onNodeWithText("Activity").performClick()
        compose.onNodeWithText("Nothing has happened yet").assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Notification access").assertIsDisplayed()
    }

    @Test
    fun `master switch pauses monitoring`() {
        compose.onNodeWithText("Master switch").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("PAUSED").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Everything is paused").assertIsDisplayed()
    }

    @Test
    fun `sample macro is saved and unknown blocks show as missing`() {
        compose.onNodeWithText("Macros").performClick()
        compose.onNodeWithText("Add sample", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("VIP email alarm").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Uses blocks this version doesn't have.").assertIsDisplayed()
        compose.onNodeWithText("Missing block (trigger.notification_posted)").assertIsDisplayed()
        // Known core blocks are summarised in plain English.
        val conditions = compose.onAllNodesWithText("{sender} contains “Rahul Sharma”", substring = true)
            .fetchSemanticsNodes()
        assertTrue(conditions.isNotEmpty())
    }
}
