package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Fixed clock for work unit 4b's instrumented worker tests (task 4b.4) — the ambient clock is
 *  never read, matching design.md §4's `TimeProvider` boundary. */
class FakeTimeProvider(private val instant: Instant, private val zoneId: ZoneId = ZoneId.of("UTC")) : TimeProvider {
    override fun now(): Instant = instant
    override fun today(): LocalDate = LocalDate.ofInstant(instant, zoneId)
    override fun zone(): ZoneId = zoneId
}
