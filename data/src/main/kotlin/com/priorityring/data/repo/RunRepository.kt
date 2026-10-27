package com.priorityring.data.repo

import com.priorityring.data.db.RunDao
import com.priorityring.data.db.RunEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Run history (traces). The engine's trace model is serialised into [RunEntity.traceJson] in M2. */
@Singleton
class RunRepository @Inject constructor(
    private val dao: RunDao,
) {
    fun recent(limit: Int = 200): Flow<List<RunEntity>> = dao.observeRecent(limit)

    fun observe(id: String): Flow<RunEntity?> = dao.observe(id)

    suspend fun save(run: RunEntity) = dao.upsert(run)

    /** Applies the retention setting. Returns how many runs were deleted. */
    suspend fun prune(retentionDays: Int, now: Long = System.currentTimeMillis()): Int =
        dao.deleteOlderThan(now - retentionDays * DAY_MS)

    suspend fun deleteAll() = dao.deleteAll()

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
