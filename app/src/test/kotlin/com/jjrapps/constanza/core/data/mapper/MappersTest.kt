package com.jjrapps.constanza.core.data.mapper

import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Pure JVM tests — Room entities are plain annotated data classes and need no Android runtime to
 * construct or map. Runs via `./gradlew :app:testDebugUnitTest`.
 */
class MappersTest {

    @Test
    fun `habit round-trips through its entity`() {
        val habit = Habit(
            id = 7,
            name = "Meditate",
            colorArgb = -1,
            notes = null,
            archived = true,
            archivedAt = LocalDate.of(2026, 9, 10),
            createdAt = Instant.parse("2026-01-01T08:00:00Z"),
            sortOrder = 3,
        )

        assertEquals(habit, habit.toEntity().toDomain())
    }

    @Test
    fun `habit with null archivedAt round-trips`() {
        val entity = HabitEntity(
            id = 1,
            name = "Drink water",
            colorArgb = 0,
            notes = null,
            archived = false,
            archivedAt = null,
            createdAt = "2026-01-01T00:00:00Z",
            sortOrder = 0,
        )

        assertNull(entity.toDomain().archivedAt)
    }

    @Test
    fun `every schedule kind round-trips through its entity`() {
        val habitId = 42L
        val schedules = listOf(
            Schedule.Daily(DayOfWeek.MONDAY),
            Schedule.TimesPerDay(DayOfWeek.MONDAY),
            Schedule.NTimesPerWeek(times = 3, weekStart = DayOfWeek.SUNDAY),
            Schedule.Weekly(dayOfWeek = DayOfWeek.WEDNESDAY, weekStart = DayOfWeek.MONDAY),
            Schedule.Monthly(dayOfMonth = 15, weekStart = DayOfWeek.MONDAY),
            Schedule.EveryNDays(n = 2, anchor = LocalDate.of(2026, 1, 1), weekStart = DayOfWeek.MONDAY),
        )

        schedules.forEach { schedule ->
            assertEquals(schedule, schedule.toEntity(habitId).toDomain())
        }
    }

    @Test
    fun `reminder slot round-trips through its entity`() {
        val slot = ReminderSlot(id = 10, habitId = 1, minuteOfDay = 480, enabled = true)

        assertEquals(slot, slot.toEntity().toDomain())
    }

    @Test
    fun `entry slotId sentinel zero maps to domain null`() {
        val entity = EntryEntity(
            habitId = 1,
            date = "2026-09-01",
            slotId = 0,
            status = "COMPLETED",
            value = null,
            answeredAt = "2026-09-01T08:00:00Z",
            source = "IN_APP",
        )

        assertNull(entity.toDomain().slotId)
    }

    @Test
    fun `entry domain null slotId maps to entity sentinel zero`() {
        val entry = Entry(
            habitId = 1,
            date = LocalDate.of(2026, 9, 1),
            slotId = null,
            status = EntryStatus.COMPLETED,
            answeredAt = Instant.parse("2026-09-01T08:00:00Z"),
        )

        assertEquals(0L, entry.toEntity(source = "IN_APP").slotId)
    }

    @Test
    fun `entry with a real slotId round-trips without becoming the sentinel`() {
        val entry = Entry(
            habitId = 1,
            date = LocalDate.of(2026, 9, 1),
            slotId = 10,
            status = EntryStatus.MISSED,
            answeredAt = Instant.parse("2026-09-01T08:00:00Z"),
        )

        assertEquals(entry, entry.toEntity(source = "SWEEP").toDomain())
    }

    @Test
    fun `mapping an UNKNOWN entry to an entity is rejected`() {
        val entry = Entry(
            habitId = 1,
            date = LocalDate.of(2026, 9, 1),
            slotId = null,
            status = EntryStatus.UNKNOWN,
        )

        assertFailsWith<IllegalArgumentException> { entry.toEntity(source = "IN_APP") }
    }

    @Test
    fun `mapping a persisted UNKNOWN status back to domain is rejected`() {
        val entity = EntryEntity(
            habitId = 1,
            date = "2026-09-01",
            slotId = 0,
            status = "UNKNOWN",
            value = null,
            answeredAt = "2026-09-01T08:00:00Z",
            source = "IN_APP",
        )

        assertFailsWith<IllegalArgumentException> { entity.toDomain() }
    }
}
