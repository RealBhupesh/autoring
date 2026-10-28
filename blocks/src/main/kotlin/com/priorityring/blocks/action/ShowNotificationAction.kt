package com.priorityring.blocks.action

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.priorityring.blocks.android.NotificationChannels
import com.priorityring.engine.block.ActionBlock
import com.priorityring.engine.block.ActionContext
import com.priorityring.engine.block.ActionResult
import com.priorityring.engine.block.BlockCategory
import com.priorityring.engine.block.BlockConfig
import com.priorityring.engine.block.BlockKind
import com.priorityring.engine.block.BlockMeta
import com.priorityring.engine.block.ConfigField
import com.priorityring.engine.block.Requirement
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Posts a notification with text that can include variables, e.g. "Urgent message from {sender}". */
class ShowNotificationAction @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActionBlock {

    override val meta = BlockMeta(
        id = ID,
        kind = BlockKind.ACTION,
        name = "Show notification",
        description = "Post your own notification. Text can include variables like {sender}.",
        category = BlockCategory.ALERT,
        icon = "notification_add",
        fields = listOf(
            ConfigField.Text("title", "Title", defaultValue = "{app}: {sender}", variables = true, required = true),
            ConfigField.Text("text", "Text", defaultValue = "{text}", multiline = true, variables = true),
            ConfigField.Dropdown(
                "importance", "Importance",
                listOf(
                    ConfigField.Option("high", "Urgent (pops up)"),
                    ConfigField.Option("default", "Normal"),
                    ConfigField.Option("low", "Quiet"),
                ),
            ),
        ),
        requirements = listOf(Requirement.POST_NOTIFICATIONS),
    )

    override fun summarize(config: BlockConfig) = "Show notification “${config.string("title")}”"

    override suspend fun execute(config: BlockConfig, context: ActionContext): ActionResult {
        val nm = NotificationManagerCompat.from(this.context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this.context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return ActionResult.Failure("Permission to show notifications hasn't been granted")
        }
        if (!nm.areNotificationsEnabled()) {
            return ActionResult.Failure("Notifications are turned off for PriorityRing")
        }
        NotificationChannels.ensureCreated(this.context)
        val channel = when (config.string("importance")) {
            "low" -> NotificationChannels.MACRO_LOW
            "default" -> NotificationChannels.MACRO_DEFAULT
            else -> NotificationChannels.MACRO_HIGH
        }
        val title = context.render(config.string("title")).ifBlank { context.macroName }
        val text = context.render(config.string("text"))
        val open = this.context.packageManager.getLaunchIntentForPackage(this.context.packageName)?.let {
            PendingIntent.getActivity(this.context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(this.context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(context.macroName)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val id = (context.runId + config.raw.hashCode()).hashCode()
        return try {
            nm.notify(id, notification)
            ActionResult.Success("Posted “$title”")
        } catch (e: SecurityException) {
            // Thrown when POST_NOTIFICATIONS was revoked between the check and the call.
            ActionResult.Failure("Not allowed to post notifications")
        }
    }

    companion object {
        const val ID = "action.show_notification"
    }
}
