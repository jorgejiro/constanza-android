package com.jjrapps.constanza.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** design.md §10 — the domain habit model. */
data class Habit(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val notes: String?,
    val archived: Boolean,
    val archivedAt: LocalDate?,
    val createdAt: Instant,
    val sortOrder: Int,
)

/**
 * `kind`-typed sealed hierarchy, never a numerator/denominator fraction (design.md D2): a
 * fraction cannot distinguish a true calendar month from "every 30 days".
 */
sealed interface Schedule {
    /** ISO-8601 week start, injected rather than hardcoded (design.md D7). */
    val weekStart: DayOfWeek

    data class Daily(override val weekStart: DayOfWeek = DayOfWeek.MONDAY) : Schedule

    /** Actual clock times live on [ReminderSlot]; this kind carries none of its own. */
    data class TimesPerDay(override val weekStart: DayOfWeek = DayOfWeek.MONDAY) : Schedule

    data class NTimesPerWeek(
        val times: Int,
        override val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ) : Schedule

    data class Weekly(
        val dayOfWeek: DayOfWeek,
        override val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ) : Schedule

    /** True calendar-month kind; clamped to the month's actual last day (habit-scheduling spec). */
    data class Monthly(
        val dayOfMonth: Int,
        override val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ) : Schedule

    /** Anchor arithmetic `(date - anchor) % n == 0`, never a fraction. */
    data class EveryNDays(
        val n: Int,
        val anchor: LocalDate,
        override val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ) : Schedule
}

data class ReminderSlot(val id: Long, val habitId: Long, val minuteOfDay: Int, val enabled: Boolean)

/** Exactly four values (ratified decision 4) — do not add a fifth. */
enum class EntryStatus { COMPLETED, MISSED, SKIPPED, UNKNOWN }

data class Entry(
    val habitId: Long,
    val date: LocalDate,
    val slotId: Long?,
    val status: EntryStatus,
    val value: Int? = null,
    val answeredAt: Instant? = null,
)

/**
 * A sealed due-result instead of a Boolean (design.md D2): a Boolean cannot distinguish
 * "required today" from "eligible but quota already met" from "not due". `Required` alone
 * authorises a dated `missed` write (D8).
 */
sealed interface Due {
    data object NotDue : Due
    data object Required : Due
    data class Candidate(val quotaRemaining: Int) : Due
}

data class PeriodProgress(val completedInWeek: Int, val completedInMonth: Int)

enum class DayStatus { ALL_COMPLETED, PARTIAL, ANY_MISSED, ALL_SKIPPED, NOT_DUE, PENDING }
