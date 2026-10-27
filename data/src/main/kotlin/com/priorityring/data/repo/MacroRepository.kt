package com.priorityring.data.repo

import com.priorityring.data.db.MacroDao
import com.priorityring.data.db.MacroEntity
import com.priorityring.engine.model.Macro
import com.priorityring.engine.serialization.MacroDecodeResult
import com.priorityring.engine.serialization.MacroJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A row from storage: either a readable macro or a placeholder explaining why it isn't. */
sealed interface StoredMacro {
    val id: String
    val name: String
    val lastRunAt: Long?

    data class Ok(val macro: Macro, override val lastRunAt: Long?, val fromNewerVersion: Boolean) : StoredMacro {
        override val id get() = macro.id
        override val name get() = macro.name
    }

    /** Shown in the list with its reason; the raw JSON is kept so nothing is silently lost. */
    data class Unreadable(
        override val id: String,
        override val name: String,
        override val lastRunAt: Long?,
        val reason: String,
    ) : StoredMacro
}

@Singleton
class MacroRepository @Inject constructor(
    private val dao: MacroDao,
) {
    val macros: Flow<List<StoredMacro>> = dao.observeAll().map { rows -> rows.map(::toStored) }

    suspend fun getAll(): List<StoredMacro> = dao.getAll().map(::toStored)

    suspend fun get(id: String): StoredMacro? = dao.get(id)?.let(::toStored)

    /** Inserts or replaces. The name/enabled columns are kept in sync with the JSON. */
    suspend fun save(macro: Macro, now: Long = System.currentTimeMillis()) {
        val existing = dao.get(macro.id)
        dao.upsert(
            MacroEntity(
                id = macro.id,
                name = macro.name,
                enabled = macro.enabled,
                json = MacroJson.encode(macro),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                position = existing?.position ?: dao.nextPosition(),
                lastRunAt = existing?.lastRunAt,
            ),
        )
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val row = dao.get(id) ?: return
        // Keep the JSON's own "enabled" flag consistent with the column.
        val macro = (MacroJson.decode(row.json) as? MacroDecodeResult.Ok)?.macro
        if (macro != null) save(macro.copy(enabled = enabled)) else dao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    suspend fun duplicate(id: String): Macro? {
        val original = (get(id) as? StoredMacro.Ok)?.macro ?: return null
        val copy = original.copy(id = newId(), name = "${original.name} (copy)")
        save(copy)
        return copy
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun markRan(id: String, at: Long) = dao.setLastRun(id, at)

    private fun toStored(row: MacroEntity): StoredMacro = when (val r = MacroJson.decode(row.json)) {
        is MacroDecodeResult.Ok -> StoredMacro.Ok(r.macro, row.lastRunAt, r.fromNewerVersion)
        is MacroDecodeResult.Corrupt -> StoredMacro.Unreadable(row.id, row.name, row.lastRunAt, r.reason)
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
