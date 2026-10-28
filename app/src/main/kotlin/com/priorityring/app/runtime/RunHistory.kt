package com.priorityring.app.runtime

import com.priorityring.data.db.RunEntity
import com.priorityring.data.repo.MacroRepository
import com.priorityring.data.repo.RunRepository
import com.priorityring.data.settings.SettingsRepository
import com.priorityring.engine.run.RunOutcome
import com.priorityring.engine.run.RunRecorder
import com.priorityring.engine.run.RunTrace
import com.priorityring.engine.run.TracePrivacy
import com.priorityring.engine.serialization.MacroJson
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves every run and skip to the database (the Activity screen), applying the privacy setting
 * first, and keeps history within the retention period.
 */
@Singleton
class RunHistory @Inject constructor(
    private val runs: RunRepository,
    private val macros: MacroRepository,
    private val settings: SettingsRepository,
) : RunRecorder {
    @Volatile private var lastPrune = 0L

    override suspend fun record(trace: RunTrace) {
        val s = settings.settings.first()
        val clean = TracePrivacy.apply(trace, s.storeNotificationText)
        runs.save(
            RunEntity(
                id = clean.runId,
                macroId = clean.macroId,
                macroName = clean.macroName,
                startedAt = clean.startedAtMs,
                finishedAt = clean.finishedAtMs,
                outcome = clean.outcome.storageName,
                headline = headline(clean),
                traceJson = MacroJson.json.encodeToString(RunTrace.serializer(), clean),
            ),
        )
        if (clean.outcome in ACTUAL_RUNS) macros.markRan(clean.macroId, clean.startedAtMs)

        val now = System.currentTimeMillis()
        if (now - lastPrune > PRUNE_EVERY_MS) {
            lastPrune = now
            runs.prune(s.retentionDays, now)
        }
    }

    private fun headline(t: RunTrace): String {
        val who = listOfNotNull(t.variables["app"], t.variables["sender"]).joinToString(" · ")
            .ifEmpty { t.eventType }
        val prefix = if (t.simulated) "Simulated · " else ""
        val why = t.reason?.takeIf { t.outcome != RunOutcome.SUCCESS }?.let { " — $it" }.orEmpty()
        return prefix + who + why
    }

    private companion object {
        val ACTUAL_RUNS = setOf(RunOutcome.SUCCESS, RunOutcome.PARTIAL, RunOutcome.FAILED, RunOutcome.TIMED_OUT, RunOutcome.STOPPED)
        const val PRUNE_EVERY_MS = 60 * 60_000L
    }
}
