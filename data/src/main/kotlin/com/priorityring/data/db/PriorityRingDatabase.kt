package com.priorityring.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Bump [version] only when a *column* changes (rare: macro content is JSON) and add a migration
 * — never use destructive fallback, users would lose their macros.
 */
@Database(
    entities = [MacroEntity::class, RunEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PriorityRingDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    abstract fun runDao(): RunDao

    companion object {
        const val NAME = "priorityring.db"
    }
}
