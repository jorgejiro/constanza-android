package com.jjrapps.constanza.core.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

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
