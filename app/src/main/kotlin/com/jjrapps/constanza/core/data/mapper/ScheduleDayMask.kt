package com.jjrapps.constanza.core.data.mapper

import java.time.DayOfWeek

/**
 * The `daysOfWeekMask` bitmask encoding/decoding for [Schedule.DaysOfWeek][com.jjrapps.constanza
 * .domain.model.Schedule.DaysOfWeek] (weekday-only-schedule design.md decision 1). Kept in its own
 * file, not folded into `Mappers.kt`, purely to stay under detekt's per-file `TooManyFunctions`
 * threshold — no behavioral reason for the split.
 *
 * Bit `n` == `DayOfWeek.value - 1`. `:domain` never learns this encoding.
 */
internal fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

internal fun Int.toDaySet(): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(mutableSetOf()) { this and (1 shl (it.value - 1)) != 0 }
