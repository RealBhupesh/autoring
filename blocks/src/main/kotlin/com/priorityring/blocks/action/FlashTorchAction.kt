package com.priorityring.blocks.action

import com.priorityring.blocks.hardware.Torch
import com.priorityring.engine.alert.AlertChannels
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.alert.AlertRequest
import com.priorityring.engine.alert.AlertResult
import com.priorityring.engine.alert.AlertSource
import com.priorityring.engine.alert.TorchPattern
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
import javax.inject.Inject

/**
 * Flashes the torch as part of the active alert, so the alarm's STOP turns it off too. If no alarm
 * is ringing, it starts a torch-only alert with its own STOP notification.
 */
class FlashTorchAction @Inject constructor(
    private val torch: Torch,
    private val alerts: AlertController,
) : ActionBlock {

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.ACTION,
        name = "Flash torch",
        description = "Flash the flashlight until stopped, together with any alarm.",
        category = BlockCategory.ALERT,
        icon = "flashlight",
        fields = listOf(
            ConfigField.Dropdown(
                "pattern", "Pattern",
                TorchPattern.entries.map { ConfigField.Option(it.name.lowercase(), it.label) },
                defaultValue = "blink",
            ),
            ConfigField.Duration("duration", "Stop by itself after", defaultMs = 2 * 60_000, minMs = 5_000, maxMs = 30 * 60_000),
        ),
    )

    private fun pattern(config: BlockConfig) =
        TorchPattern.entries.firstOrNull { it.name.equals(config.string("pattern"), true) } ?: TorchPattern.BLINK

    override fun summarize(config: BlockConfig) =
        "Flash torch (${pattern(config).label.lowercase()}) until stopped, max ${formatDuration(config.long("duration"))}"

    override fun extraAvailability(env: DeviceCapabilities): Availability? =
        if (!torch.isAvailable) Availability.Unsupported("This phone has no flashlight") else null

    override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult {
        if (!torch.isAvailable) return ActionResult.Failure("No flashlight on this phone")
        val r = alerts.request(
            AlertRequest(
                source = AlertSource(context.runId, context.macroId, context.macroName, context.macroName),
                channels = AlertChannels(torch = pattern(config)),
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

    companion object {
        const val ID = "action.flash_torch"
    }
}
