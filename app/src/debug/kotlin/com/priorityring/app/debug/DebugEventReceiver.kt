package com.priorityring.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.priorityring.app.runtime.ApplicationScope
import com.priorityring.app.runtime.EventDispatcher
import com.priorityring.app.runtime.Simulation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug-only: post a fake notification from your computer, e.g.
 *
 * ```
 * adb shell am broadcast -a com.priorityring.debug.SIMULATE \
 *   -n com.priorityring.app.debug/com.priorityring.app.debug.DebugEventReceiver \
 *   --es preset gmail --es sender "Rahul Sharma" --es text "Quarterly report" --es key test1
 * ```
 *
 * Extras: preset = gmail | whatsapp | whatsapp_group | custom; sender; text; group; package;
 * key (reuse the same key + text to test duplicate handling). Result goes to logcat tag
 * "PriorityRing" and the Activity screen.
 */
@AndroidEntryPoint
class DebugEventReceiver : BroadcastReceiver() {
    @Inject lateinit var dispatcher: EventDispatcher
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val preset = when (intent.getStringExtra("preset")?.lowercase()) {
            "whatsapp" -> Simulation.Preset.WHATSAPP
            "whatsapp_group" -> Simulation.Preset.WHATSAPP_GROUP
            "custom" -> Simulation.Preset.CUSTOM
            else -> Simulation.Preset.GMAIL
        }
        val raw = Simulation.build(
            preset = preset,
            sender = intent.getStringExtra("sender") ?: "Rahul Sharma",
            text = intent.getStringExtra("text") ?: "Test message",
            group = intent.getStringExtra("group").orEmpty(),
            packageName = intent.getStringExtra("package") ?: preset.packageName,
            key = intent.getStringExtra("key")?.let { "adb|$it" } ?: ("adb|" + System.nanoTime()),
        )
        val pending = goAsync()
        scope.launch {
            try {
                val r = dispatcher.dispatch(Simulation.event(raw))
                Log.i(TAG, "Simulated ${preset.name}: started=${r.started.map { it.macroName }} " +
                    "notRun=${r.notRun.map { "${it.macroName}: ${it.outcome} (${it.reason})" }}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PriorityRing"
    }
}
