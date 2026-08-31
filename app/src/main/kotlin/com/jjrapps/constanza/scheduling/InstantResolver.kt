package com.jjrapps.constanza.scheduling

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val SECONDS_PER_MINUTE = 60L

/**
 * Converts a [LocalDate] plus a [ReminderSlot][com.jjrapps.constanza.domain.model.ReminderSlot]'s
 * `minuteOfDay` into the [Instant] `AlarmManager` fires at (design.md §9.3, task 4a.7). This is
 * the ONE place in the whole reminder pipeline where a `LocalTime` becomes an `Instant`.
 * `:domain`'s `dueOn`/`rollupDay` stay `LocalDate`-only and are DST-immune by construction — a
 * transition shifts wall-clock times of day, never the sequence of calendar days
 * (`DueOnDaylightSavingTest` pins that) — so this function owns the one conversion that is not.
 *
 * Two deliberate decisions, verified against `Europe/Madrid`'s real [java.time.zone.ZoneRules] for
 * 2026 and asserted by `InstantResolverDaylightSavingTest`:
 *
 * - **Spring forward** (2026-03-29, gap 02:00→03:00 local): a slot at 02:30 has no valid local
 *   time. `ZonedDateTime.of` does not fail — it shifts the local time forward by the length of the
 *   gap, so the reminder fires at **03:30 the same day**, one hour late. Chosen over skipping the
 *   date or firing before the gap (01:30, effectively a day early by clock time): firing slightly
 *   late is the same trade-off the whole design already accepts for a dropped alarm (design.md
 *   §5.5) — late beats lost, and the habit is still credited to the correct calendar date because
 *   `Entry.date` always comes from `scheduledDate`, never from the fire instant (D4).
 * - **Fall back** (2026-10-25, overlap 02:00–02:59 local occurs twice): a slot at 02:30 has TWO
 *   valid offsets, `+02:00` (before the clocks turn back) and `+01:00` (after). `ZonedDateTime.of`
 *   resolves to the EARLIER offset by default, i.e. the chronologically FIRST of the two instants,
 *   so the reminder fires exactly **once**. Resolving to the later offset instead would only delay
 *   the reminder by an hour for no benefit; firing at both would duplicate the notification and
 *   race two writes for the same `Entry` — exactly what `UNIQUE(habitId, slotId, scheduledDate)`
 *   (design.md §8.2) exists to make impossible at the row level, but a double *alarm* would still
 *   be a double notification even though only one occurrence row exists.
 *
 * Leaving either case to `ZonedDateTime`'s default without stating and testing the choice — the
 * one thing this task calls out as the wrong answer — is exactly what this function's KDoc and
 * `InstantResolverDaylightSavingTest` exist to avoid.
 */
fun resolveOccurrenceInstant(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Instant {
    val time = LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE)
    return ZonedDateTime.of(date, time, zone).toInstant()
}
