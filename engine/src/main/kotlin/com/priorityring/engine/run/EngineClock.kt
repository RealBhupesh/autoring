package com.priorityring.engine.run

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Time source for the engine; injected so tests can control time. */
interface EngineClock {
    fun nowMillis(): Long
    fun zone(): ZoneId = ZoneId.systemDefault()
    fun now(): ZonedDateTime = Instant.ofEpochMilli(nowMillis()).atZone(zone())

    object System : EngineClock {
        override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
    }
}
