package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

private val TODAY: LocalDate = LocalDate.parse("2026-09-06")
private val ZONE: ZoneId = ZoneId.of("UTC")

/** 14:42 — the same moment the approved design reference render was taken from. */
private val NOW: Instant = instantAt(14, 42)

private fun instantAt(hour: Int, minute: Int): Instant =
    TODAY.atStartOfDay(ZONE).plusMinutes((hour * 60 + minute).toLong()).toInstant()

private fun slot(
    minuteOfDay: Int? = null,
    status: EntryStatus = EntryStatus.UNKNOWN,
    snoozedUntilEpochMs: Long? = null,
    slotId: Long? = 1L,
) = TodaySlot(slotId, minuteOfDay, status, occurrenceId = null, snoozedUntilEpochMs = snoozedUntilEpochMs)

private fun row(id: Long, vararg slots: TodaySlot) =
    TodayHabitRow(habitId = id, habitName = "Habit$id", dayStatus = DayStatus.PENDING, colorArgb = 0, slots = slots.toList())

/**
 * Task today-grouped-sections, design.md: coverage for [groupTodayRows], the pure join that
 * decides which of "Ahora" / "Más tarde" / "Hecho" each habit lands in and how each section
 * orders. Deliberately over the pure function rather than [TodayViewModel] — [TodayViewModelTest]
 * already covers the day-rollup/slot-identity join and the ViewModel's own write path; this file
 * is the one place the placement/ordering rules themselves are asserted.
 */
class TodayGroupingTest {

    @Test
    fun `an untimed unanswered slot sorts above a timed one within Ahora`() {
        val timed = row(1, slot(minuteOfDay = 8 * 60)) // 08:00, already behind NOW (14:42)
        val untimed = row(2, slot(minuteOfDay = null))

        val sections = groupTodayRows(listOf(timed, untimed), TODAY, ZONE, NOW)

        val ahora = sections.single { it.kind == TodaySectionKind.NOW }
        assertEquals(listOf(2L, 1L), ahora.rows.map { it.habitId })
    }

    @Test
    fun `an overdue unanswered slot lands the habit in Ahora`() {
        val overdue = row(1, slot(minuteOfDay = 8 * 60)) // 08:00, behind NOW

        val sections = groupTodayRows(listOf(overdue), TODAY, ZONE, NOW)

        assertEquals(listOf(TodaySectionKind.NOW), sections.map { it.kind })
        assertEquals(listOf(1L), sections.single().rows.map { it.habitId })
    }

    @Test
    fun `a future unanswered slot lands the habit in Mas tarde`() {
        val future = row(1, slot(minuteOfDay = 21 * 60)) // 21:00, ahead of NOW

        val sections = groupTodayRows(listOf(future), TODAY, ZONE, NOW)

        assertEquals(listOf(TodaySectionKind.LATER), sections.map { it.kind })
        assertEquals(listOf(1L), sections.single().rows.map { it.habitId })
    }

    @Test
    fun `Mas tarde orders by nearest future time ascending`() {
        val at23 = row(1, slot(minuteOfDay = 23 * 60))
        val at21 = row(2, slot(minuteOfDay = 21 * 60))

        val sections = groupTodayRows(listOf(at23, at21), TODAY, ZONE, NOW)

        assertEquals(listOf(2L, 1L), sections.single().rows.map { it.habitId })
    }

    @Test
    fun `a snoozed slot is placed by its snooze time, not its original reminder time`() {
        // Original time (08:00) is well behind NOW, which would otherwise mean Ahora; the snooze
        // (15:02) is still ahead of NOW, so the habit belongs in Mas tarde instead.
        val snoozedIntoTheFuture = row(
            1,
            slot(minuteOfDay = 8 * 60, snoozedUntilEpochMs = instantAt(15, 2).toEpochMilli()),
        )

        val sections = groupTodayRows(listOf(snoozedIntoTheFuture), TODAY, ZONE, NOW)

        assertEquals(listOf(TodaySectionKind.LATER), sections.map { it.kind })
    }

    @Test
    fun `a snoozed slot whose snooze has already passed lands in Ahora, not by its original time`() {
        // Original time (21:00) is ahead of NOW, which would otherwise mean Mas tarde; the snooze
        // (14:00) has already passed, so the habit is actionable now instead.
        val snoozedIntoThePast = row(
            1,
            slot(minuteOfDay = 21 * 60, snoozedUntilEpochMs = instantAt(14, 0).toEpochMilli()),
        )

        val sections = groupTodayRows(listOf(snoozedIntoThePast), TODAY, ZONE, NOW)

        assertEquals(listOf(TodaySectionKind.NOW), sections.map { it.kind })
    }

    @Test
    fun `a habit whose every slot is answered lands in Hecho`() {
        val done = row(1, slot(minuteOfDay = 8 * 60, status = EntryStatus.COMPLETED))

        val sections = groupTodayRows(listOf(done), TODAY, ZONE, NOW)

        assertEquals(listOf(TodaySectionKind.DONE), sections.map { it.kind })
        assertEquals(listOf(1L), sections.single().rows.map { it.habitId })
    }

    @Test
    fun `Hecho treats a MISSED slot as answered, not pending`() {
        val missed = row(1, slot(minuteOfDay = 8 * 60, status = EntryStatus.MISSED))

        val sections = groupTodayRows(listOf(missed), TODAY, ZONE, NOW)

        assertEquals(listOf(TodaySectionKind.DONE), sections.map { it.kind })
    }

    @Test
    fun `a multi-slot habit with one overdue slot and one future slot lands in Ahora`() {
        val multiSlot = row(
            1,
            slot(slotId = 1L, minuteOfDay = 8 * 60), // overdue
            slot(slotId = 2L, minuteOfDay = 21 * 60), // future
        )

        val sections = groupTodayRows(listOf(multiSlot), TODAY, ZONE, NOW)

        assertEquals(listOf(TodaySectionKind.NOW), sections.map { it.kind })
        assertEquals(listOf(1L), sections.single().rows.map { it.habitId })
    }

    @Test
    fun `a multi-slot habit with one answered slot and one future slot lands in Mas tarde`() {
        val multiSlot = row(
            1,
            slot(slotId = 1L, minuteOfDay = 8 * 60, status = EntryStatus.COMPLETED),
            slot(slotId = 2L, minuteOfDay = 21 * 60),
        )

        val sections = groupTodayRows(listOf(multiSlot), TODAY, ZONE, NOW)

        assertEquals(listOf(TodaySectionKind.LATER), sections.map { it.kind })
    }

    @Test
    fun `an empty group produces no section at all`() {
        // Every habit already answered: Ahora and Mas tarde both have zero rows, so neither may
        // appear in the result — not even as a TodaySection carrying an empty list.
        val done = row(1, slot(minuteOfDay = 8 * 60, status = EntryStatus.COMPLETED))

        val sections = groupTodayRows(listOf(done), TODAY, ZONE, NOW)

        assertEquals(listOf(TodaySectionKind.DONE), sections.map { it.kind })
        assertEquals(false, sections.any { it.kind == TodaySectionKind.NOW })
        assertEquals(false, sections.any { it.kind == TodaySectionKind.LATER })
    }

    @Test
    fun `no rows at all produces no sections`() {
        assertEquals(emptyList(), groupTodayRows(emptyList(), TODAY, ZONE, NOW))
    }
}
