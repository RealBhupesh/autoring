package com.priorityring.blocks.action

import android.content.Context
import android.media.RingtoneManager
import androidx.core.net.toUri
import com.priorityring.engine.alert.AlertChannels
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.alert.AlertRequest
import com.priorityring.engine.alert.AlertResult
import com.priorityring.engine.alert.AlertSource
import com.priorityring.engine.alert.AlertState
import com.priorityring.engine.alert.SoundSpec
import com.priorityring.engine.alert.VibrationPattern
import com.priorityring.engine.block.ActionBlock
import com.priorityring.engine.block.ActionContext
import com.priorityring.engine.block.ActionResult
import com.priorityring.engine.block.BlockCategory
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockMeta
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.Requirement
import com.priorityring.engine.block.formatDuration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Rings like an alarm clock: alarm audio stream (plays in silent mode by default), looping, with an
 * alarm screen / notification offering STOP and SNOOZE. The actual ringing is owned by the app's
 * [AlertController], so two macros can never ring two sounds at once.
 */
class RingAlarmAction @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alerts: AlertController,
) : ActionBlock {

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.ACTION,
        name = "Ring alarm",
        description = "Ring an alarm sound until you stop it (or a time limit), with Stop and Snooze.",
        category = BlockCategory.ALERT,
        icon = "alarm",
        fields = listOf(
            ConfigField.SoundPicker("sound", "Sound", help = "Leave empty for the phone's default alarm sound."),
            ConfigField.Duration("duration", "Ring for up to", defaultMs = 2 * 60_000, minMs = 10_000, maxMs = 30 * 60_000),
            ConfigField.Toggle(
                "full_volume", "Ring at full alarm volume", defaultValue = true,
                help = "Raises the alarm volume while ringing and restores it afterwards.",
            ),
            ConfigField.Toggle("vibrate", "Also vibrate", defaultValue = true),
            ConfigField.Duration("snooze", "Snooze length", defaultMs = 5 * 60_000, minMs = 60_000, maxMs = 60 * 60_000),
            ConfigField.Number("max_snoozes", "Snoozes allowed", defaultValue = 3, min = 0, max = 10),
            ConfigField.Toggle(
                "full_screen", "Show full-screen alarm", defaultValue = true,
                help = "Needs the \"Full-screen alerts\" permission; otherwise a large notification is shown.",
            ),
            ConfigField.Toggle("show_preview", "Show message text on the alarm", defaultValue = false),
            ConfigField.Toggle(
                "wait", "Wait until the alarm stops", defaultValue = false,
                help = "Hold the next action until the alarm is stopped or times out.",
            ),
        ),
        requirements = listOf(Requirement.POST_NOTIFICATIONS),
    )

    override fun summarize(config: BlockConfig): String {
        val extras = buildList {
            if (config.boolean("vibrate")) add("vibrate")
        }
        return "Ring ${soundName(config.string("sound"))} for up to ${formatDuration(config.long("duration"))}" +
            if (extras.isEmpty()) "" else " and " + extras.joinToString(", ")
    }

    private fun soundName(uri: String): String {
        if (uri.isBlank()) return "default alarm"
        return runCatching { RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "chosen sound"
    }

    override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult {
        val v = context.variables
        val request = AlertRequest(
            source = AlertSource(
                runId = context.runId,
                macroId = context.macroId,
                macroName = context.macroName,
                title = v["sender"] ?: v["title"] ?: v["app"] ?: context.macroName,
                app = v["app"],
                preview = if (config.boolean("show_preview")) v["text"]?.take(300) else null,
            ),
            channels = AlertChannels(
                sound = SoundSpec(config.string("sound"), config.boolean("full_volume")),
                vibration = if (config.boolean("vibrate")) VibrationPattern.ALARM else null,
            ),
            maxDurationMs = config.long("duration"),
            snoozeMs = config.long("snooze"),
            maxSnoozes = config.int("max_snoozes"),
            fullScreen = config.boolean("full_screen"),
        )
        return when (val r = alerts.request(request)) {
            is AlertResult.Failed -> ActionResult.Failure(r.reason)
            is AlertResult.Ok -> {
                if (config.boolean("wait")) {
                    context.log("Waiting for the alarm to stop…")
                    alerts.state.first { it is AlertState.Idle }
                    ActionResult.Success("${r.detail}; alarm has stopped")
                } else ActionResult.Success(r.detail)
            }
        }
    }

    companion object {
        const val ID = "action.ring_alarm"
    }
}
