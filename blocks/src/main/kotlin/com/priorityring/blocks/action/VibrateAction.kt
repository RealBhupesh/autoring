package com.priorityring.blocks.action

import com.priorityring.blocks.hardware.Vibration
import com.priorityring.engine.alert.AlertChannels
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.alert.AlertRequest
import com.priorityring.engine.alert.AlertResult
import com.priorityring.engine.alert.AlertSource
import com.priorityring.engine.alert.VibrationPattern
import com.priorityring.engine.block.ActionBlock
import com.priorityring.engine.block.ActionContext
import com.priorityring.engine.block.ActionResult
import com.priorityring.engine.block.Availability
import com.priorityring.engine.block.BlockCategory
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockMeta
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.DeviceCapabilities
import com.priorityring.engine.block.formatDuration
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Vibrates a pattern a few times, or keeps vibrating as part of the active alert (stopped by the
 * same STOP as the alarm sound).
 */
class VibrateAction @Inject constructor(
    private val vibration: Vibration,
    private val alerts: AlertController,
) : ActionBlock {

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.ACTION,
        name = "Vibrate",
        description = "Vibrate a pattern, once or until stopped.",
        category = BlockCategory.ALERT,
        icon = "vibration",
        fields = listOf(
            ConfigField.Dropdown(
                "pattern", "Pattern",
                VibrationPattern.entries.map { ConfigField.Option(it.name.lowercase(), it.label) },
            ),
            ConfigField.Dropdown(
                "mode", "How long",
                listOf(ConfigField.Option("times", "A number of times"), ConfigField.Option("until_stopped", "Until stopped")),
            ),
            ConfigField.Number("times", "Times", defaultValue = 2, min = 1, max = 10, help = "For “a number of times”."),
            ConfigField.Duration(
                "duration", "Stop by itself after", defaultMs = 60_000, minMs = 5_000, maxMs = 30 * 60_000,
                help = "For “until stopped”.",
            ),
        ),
    )

    private fun pattern(config: BlockConfig) =
        VibrationPattern.entries.firstOrNull { it.name.equals(config.string("pattern"), true) } ?: VibrationPattern.ALARM

    override fun summarize(config: BlockConfig): String {
        val p = pattern(config).label.lowercase()
        return if (config.string("mode") == "until_stopped") {
            "Vibrate ($p) until stopped, max ${formatDuration(config.long("duration"))}"
        } else {
            val n = config.int("times")
            "Vibrate ($p) ${if (n == 1) "once" else "$n times"}"
        }
    }

    override fun extraAvailability(env: DeviceCapabilities): Availability? =
        if (!vibration.isAvailable) Availability.Unsupported("This phone has no vibration motor") else null

    override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult {
        val pattern = pattern(config)
        if (config.string("mode") == "until_stopped") {
            val r = alerts.request(
                AlertRequest(
                    source = AlertSource(context.runId, context.macroId, context.macroName, context.macroName),
                    channels = AlertChannels(vibration = pattern),
                    maxDurationMs = config.long("duration"),
                    maxSnoozes = 0,
                    fullScreen = false,
                ),
            )
            return when (r) {
                is AlertResult.Ok -> ActionResult.Success(r.detail)
                is AlertResult.Failed -> ActionResult.Failure(r.reason)
            }
        }
        val times = config.int("times").coerceIn(1, 10)
        val timings = List(times) { pattern.timings }.flatten()
        if (!vibration.start(timings, repeat = false)) return ActionResult.Failure("No vibrator available")
        delay(timings.sum()) // keep the macro in step with the vibration
        return ActionResult.Success("Vibrated ${pattern.label.lowercase()} × $times")
    }

    companion object {
        const val ID = "action.vibrate"
    }
}
