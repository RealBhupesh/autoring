package com.priorityring.app.runtime

import com.priorityring.engine.notification.ParsedNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** One captured notification for the inspector. Kept in memory only, never written to disk. */
data class CapturedNotification(
    val id: Long,
    val atMs: Long,
    val packageName: String,
    val app: String,
    val parsed: ParsedNotification,
    val redactedDump: String,
    val fullDump: String,
)

/** The last few notifications seen by the listener, for the debug inspector. */
@Singleton
class RecentNotifications @Inject constructor() {
    private val _items = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val items: StateFlow<List<CapturedNotification>> = _items.asStateFlow()

    fun add(item: CapturedNotification) = _items.update { (listOf(item) + it).take(MAX) }

    fun clear() = _items.update { emptyList() }

    private companion object {
        const val MAX = 25
    }
}
