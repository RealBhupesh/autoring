package com.priorityring.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps a block's icon key (BlockMeta.icon) to a vector. Keys keep the engine free of Android
 * drawables; unknown keys fall back to a generic icon, so a new block never breaks the UI.
 */
object BlockIcons {
    fun of(key: String): ImageVector = when (key) {
        "notification" -> Icons.Filled.Notifications
        "alarm" -> Icons.Filled.Warning
        "vibration" -> Icons.Filled.Phone
        "flashlight" -> Icons.Filled.Star
        "notification_add" -> Icons.Filled.AddCircle
        "text" -> Icons.Filled.Create
        "schedule", "calendar" -> Icons.Filled.DateRange
        "timer" -> Icons.Filled.Refresh
        "stop" -> Icons.Filled.Close
        else -> Icons.Filled.Build
    }
}
