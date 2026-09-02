package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Fixed clock for work unit 4b's instrumented worker tests (task 4b.4) — the ambient clock is
 *  never read, matching design.md §4's `TimeProvider` boundary. [instant] is reassignable so a test
 *  can stage two cold starts minutes apart, which is what [WorkSchedulerTest] needs to prove the
 *  midnight sweep's anchor no longer moves between them (task G.4). */
class FakeTimeProvider(var instant: Instant, private val zoneId: ZoneId = ZoneId.of("UTC")) : TimeProvider {
    override fun now(): Instant = instant

    /**
     * `instant.atZone(zoneId).toLocalDate()`, NOT `LocalDate.ofInstant(instant, zoneId)`.
     *
     * They compute the same date — `ofInstant` is defined as exactly this — but `ofInstant` is a
     * Java 9 addition that Android's `java.time` did not gain until API 34, while `minSdk` here is
     * 31. On an API 31 device every test touching this class died with
     * `NoSuchMethodError: No static method ofInstant(...) in class Ljava/time/LocalDate;`, and each
     * one that merely *awaited* a save through `HabitRepository` timed out instead, because the
     * `NoSuchMethodError` was thrown inside the coroutine they were waiting on.
     *
     * Found by the API 31 leg of the device-free matrix on its first run: 38 of 70 instrumented
     * tests, across nine classes that have nothing to do with time, all failing on this one call.
     * Nothing else caught it — lint's `NewApi` does not analyse `androidTest` sources, the JVM unit
     * tests run on Java 21 where the method exists, and every instrumented run before this one had
     * been on API 36/37 hardware. It is exactly the class of defect that leg exists to find.
     */
    override fun today(): LocalDate = instant.atZone(zoneId).toLocalDate()

    override fun zone(): ZoneId = zoneId
}
