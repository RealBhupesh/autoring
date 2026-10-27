package com.priorityring.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One macro. Only what we need to query or sort by is a real column; the macro itself is the
 * versioned JSON in [json] (see `MacroJson`). New blocks therefore never need a Room migration.
 */
@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val json: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Manual ordering on the Macros screen. */
    val position: Int,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long? = null,
)

/**
 * One macro run *or skip* — everything the Activity screen shows. The step-by-step trace is JSON
 * so its shape can evolve with the engine.
 */
@Entity(
    tableName = "runs",
    indices = [Index("started_at"), Index("macro_id")],
)
data class RunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "macro_id") val macroId: String,
    /** Copied at run time so history still reads well after a macro is renamed or deleted. */
    @ColumnInfo(name = "macro_name") val macroName: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    /** e.g. "success", "failed", "skipped", "timed_out", "stopped". */
    val outcome: String,
    /** One-line headline, e.g. "Gmail · Rahul Sharma". */
    val headline: String,
    @ColumnInfo(name = "trace_json") val traceJson: String,
)
