package com.jjrapps.constanza.core.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

private const val MINUTES_PER_HOUR = 60

/**
 * The one place in `:app` allowed to read the ambient clock (design.md §4). Every other call
 * site — mappers, repositories, workers, receivers — takes time as an injected parameter through
 * this interface instead of calling `Instant.now()` / `LocalDate.now()` / `ZoneId.systemDefault()`
 * directly. `:domain` enforces the same ban compile-adjacently via the `ForbiddenMethodCall`
 * detekt rule; `:app` has no such rule, so this abstraction is the boundary here.
 */
interface TimeProvider {
    fun now(): Instant
    fun today(): LocalDate
    fun zone(): ZoneId
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun today(): LocalDate = LocalDate.now(zone())
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Milliseconds from [TimeProvider.now] to the next local midnight, read through the injected
 *  [TimeProvider] and never the ambient clock (design.md §4). Clamped at zero so a provider whose
 *  `now()` has already passed its own `today() + 1` boundary yields an immediate run rather than a
 *  negative delay, which `setInitialDelay` would reject.
 *
 *  today-midnight-rollover, design.md decision 4: moved here from `scheduling/WorkScheduler.kt`
 *  (its original, `internal`-only home) so `core/time/CurrentDateSource.kt` can use it too, without
 *  making `core/time` depend on `scheduling` — the wrong direction. Public rather than `internal`
 *  for that same reason; behaviour and KDoc carried over verbatim. */
fun TimeProvider.millisUntilNextMidnight(): Long {
    val nextMidnight = today().plusDays(1).atStartOfDay(zone())
    return Duration.between(now(), nextMidnight.toInstant()).toMillis().coerceAtLeast(0)
}

/** Milliseconds from [TimeProvider.now] to the next local occurrence of [minuteOfDay] (0..1439),
 *  read through the injected [TimeProvider] exactly as [millisUntilNextMidnight] does — [today]'s
 *  date combined with the wall-clock time first, THEN resolved against [zone], never a
 *  [Duration]-based offset added to a [java.time.ZonedDateTime]. That ordering matters across a DST
 *  transition day: adding elapsed minutes to a zoned start-of-day would walk past a skipped or
 *  repeated local hour and land on the wrong wall-clock time, while building the local date-time
 *  first and resolving it against the zone afterwards (the same shape [millisUntilNextMidnight]
 *  already uses for midnight) lands on the correct one.
 *
 *  Rolls to tomorrow when today's occurrence of [minuteOfDay] has already passed — including the
 *  exact instant of it, mirroring [millisUntilNextMidnight]'s "standing exactly on the boundary
 *  anchors a full day ahead, not zero" rule. Clamped at zero for the same reason
 *  [millisUntilNextMidnight] is: `setInitialDelay` rejects a negative delay. */
fun TimeProvider.millisUntilNextLocalTime(minuteOfDay: Int): Long {
    val time = LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
    val todayAtTime = today().atTime(time).atZone(zone())
    val target = if (todayAtTime.toInstant().isAfter(now())) {
        todayAtTime
    } else {
        today().plusDays(1).atTime(time).atZone(zone())
    }
    return Duration.between(now(), target.toInstant()).toMillis().coerceAtLeast(0)
}
