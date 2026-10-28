package com.priorityring.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.priorityring.app.runtime.EventDispatcher
import com.priorityring.app.runtime.Simulation
import com.priorityring.data.repo.MacroRepository
import com.priorityring.data.repo.StoredMacro
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.blocks.core.NotificationPostedTrigger
import com.priorityring.engine.blocks.core.TextMatchCondition
import com.priorityring.engine.blocks.core.WaitAction
import com.priorityring.engine.model.ActionNode
import com.priorityring.engine.model.BlockInstance
import com.priorityring.engine.model.ConditionNode
import com.priorityring.engine.model.Macro
import com.priorityring.engine.notification.KnownApps
import com.priorityring.engine.run.RunOutcome
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Launches the real MainActivity on the JVM (Robolectric) with the real Hilt graph, Room, DataStore
 * and engine. Proves the app starts, screens render, the builder saves a macro, and a simulated
 * notification flows through parser → engine → history → Activity screen.
 * It says nothing about real-device behaviour (that's the phone checklist).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class AppSmokeTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var macros: MacroRepository
    @Inject lateinit var dispatcher: EventDispatcher
    @Inject lateinit var runs: com.priorityring.data.repo.RunRepository

    @Before fun inject() = hilt.inject()

    private fun waitForText(text: String, substring: Boolean = false) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("“$text” never appeared. Screen:\n" + compose.onRoot(useUnmergedTree = true).printToString(), e)
        }
    }

    @Test
    fun `app starts and every tab renders`() {
        compose.onNodeWithText("Master switch").assertIsDisplayed()
        // Notification access is never granted in a test environment, so Home must say so.
        waitForText("NEEDS ATTENTION")

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
        waitForText("PAUSED")
        compose.onNodeWithText("Everything is paused").assertIsDisplayed()
    }

    @Test
    fun `build a macro in the builder and save it`() {
        compose.onNodeWithText("Macros").performClick()
        compose.onNodeWithText("New macro", useUnmergedTree = true).performClick()
        waitForText("Add trigger")
        compose.onNodeWithText("Name").performTextInput("VIP email")

        compose.onNodeWithText("Add trigger").performClick()
        waitForText("Notification received")
        compose.onNodeWithText("Notification received").performClick()
        waitForText("Done")
        compose.onNodeWithText("Done").performClick()

        compose.onNodeWithTag("editor-list").performScrollToNode(hasText("Add action"))
        compose.onNodeWithText("Add action").performClick()
        waitForText("Search blocks")
        compose.onNodeWithText("Search blocks").performTextInput("wait")
        waitForText("Pause before the next action.")
        compose.onNodeWithText("Pause before the next action.").performClick()
        waitForText("Done")
        compose.onNodeWithText("Done").performClick()

        compose.onNodeWithText("Save").performClick()
        waitForText("VIP email")
        // Robolectric's screen is tiny (320×470 px), so check existence rather than visibility.
        waitForText("Notification from any app")
        waitForText("Wait 10 s")

        val saved = runBlocking { macros.getAll() }.single() as StoredMacro.Ok
        assertEquals("VIP email", saved.macro.name)
        assertEquals(NotificationPostedTrigger.ID, saved.macro.triggers.single().blockId)
    }

    @Test
    fun `saving without trigger or action shows what is missing`() {
        compose.onNodeWithText("Macros").performClick()
        compose.onNodeWithText("New macro", useUnmergedTree = true).performClick()
        waitForText("Save")
        compose.onNodeWithText("Save").performClick()
        waitForText("Add at least one trigger (WHEN).", substring = true)
        assertTrue(runBlocking { macros.getAll() }.isEmpty())
    }

    @Test
    fun `simulated gmail notification runs a matching macro and shows in activity`() {
        val vip = Macro(
            id = "vip", name = "VIP email",
            triggers = listOf(BlockInstance("t", NotificationPostedTrigger.ID, JsonObject(mapOf("apps" to JsonArray(listOf(JsonPrimitive(KnownApps.GMAIL))))))),
            conditions = ConditionNode.Group(children = listOf(ConditionNode.Leaf(BlockInstance("c", TextMatchCondition.ID,
                JsonObject(BlockConfig.defaults(TextMatchCondition.meta) + mapOf("variable" to JsonPrimitive("sender"), "pattern" to JsonPrimitive("Rahul Sharma"))))))),
            actions = listOf(ActionNode.Step(BlockInstance("a", WaitAction.ID, JsonObject(mapOf("duration" to JsonPrimitive(1_000)))))),
        )
        runBlocking { macros.save(vip) }

        val match = runBlocking { dispatcher.dispatch(Simulation.event(Simulation.build(Simulation.Preset.GMAIL, "Rahul Sharma", "Hi"))) }
        assertEquals(listOf("VIP email"), match.started.map { it.macroName })
        val noMatch = runBlocking { dispatcher.dispatch(Simulation.event(Simulation.build(Simulation.Preset.GMAIL, "Someone else", "Hi"))) }
        assertEquals(RunOutcome.CONDITIONS_NOT_MET, noMatch.notRun.single().outcome)
        val stored = runBlocking { runs.recent(10).first() }
        assertTrue("history rows: ${stored.map { it.outcome + " " + it.headline }}", stored.any { it.outcome == "conditions_not_met" })

        compose.onNodeWithText("Activity").performClick()
        waitForText("NOT MET") // status chips are uppercase
        compose.onNodeWithText("Simulated · Gmail · Someone else", substring = true).performClick()
        waitForText("STEPS") // section labels are uppercase
        waitForText("Gmail notification from Someone else")
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }
}
