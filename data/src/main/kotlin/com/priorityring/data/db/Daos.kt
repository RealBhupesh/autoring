package com.priorityring.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {
    /** Emits again automatically whenever the table changes — this is what keeps the UI live. */
    @Query("SELECT * FROM macros ORDER BY position, created_at")
    fun observeAll(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros ORDER BY position, created_at")
    suspend fun getAll(): List<MacroEntity>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun get(id: String): MacroEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM macros")
    suspend fun nextPosition(): Int

    @Upsert
    suspend fun upsert(macro: MacroEntity)

    @Query("UPDATE macros SET enabled = :enabled, updated_at = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE macros SET last_run_at = :at WHERE id = :id")
    suspend fun setLastRun(id: String, at: Long)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM macros")
    suspend fun deleteAll()
}

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :id")
    fun observe(id: String): Flow<RunEntity?>

    @Upsert
    suspend fun upsert(run: RunEntity)

    @Query("DELETE FROM runs WHERE started_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM runs")
    suspend fun deleteAll()
}
