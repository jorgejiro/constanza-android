package com.jjrapps.constanza.recap

import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.RecapMark
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitRepository
import com.jjrapps.constanza.habit.HabitSchedule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** weekly-recap S4/S6: which habits the week shows, with what names and counts. */
class WeeklyRecapLoaderTest {

    private val weekStart = LocalDate.parse("2026-10-12")
    private val habitRepository = mockk<HabitRepository>()
    private val entryDao = mockk<EntryDao>()
    private val timeProvider = mockk<TimeProvider> { every { zone() } returns ZoneId.of("Europe/Madrid") }

    private fun habit(
        id: Long,
        name: String,
        createdAt: String = "2026-01-01T00:00:00Z",
        archivedAt: LocalDate? = null,
    ) = Habit(
        id = id,
        name = name,
        colorArgb = id.toInt(),
        notes = null,
        archived = archivedAt != null,
        archivedAt = archivedAt,
        createdAt = Instant.parse(createdAt),
    )

    private fun entry(habitId: Long, date: String, status: String) = EntryEntity(
        habitId = habitId, date = date, slotId = 0, status = status,
        value = null, answeredAt = "${date}T08:00:00Z", source = "IN_APP",
    )

    private fun stub(habits: List<Habit>, entries: Map<Long, List<EntryEntity>>) {
        every { habitRepository.observeAll() } returns flowOf(habits)
        val daily = HabitSchedule(Schedule.Daily(), emptyList())
        every { habitRepository.schedules } returns flowOf(habits.associate { it.id to daily })
        habits.forEach { h ->
            coEvery {
                entryDao.findByHabitIdBetweenDates(h.id, "2026-10-12", "2026-10-18")
            } returns entries[h.id].orEmpty()
        }
    }

    @Test
    fun `rows carry each habit's name, colour, marks and counts, and the totals add up`() = runTest {
        stub(
            listOf(habit(1L, "Read"), habit(2L, "Walk")),
            mapOf(
                1L to listOf(entry(1L, "2026-10-12", "COMPLETED"), entry(1L, "2026-10-13", "MISSED")),
                2L to listOf(entry(2L, "2026-10-18", "COMPLETED")),
            ),
        )

        val recap = WeeklyRecapLoader(habitRepository, entryDao, timeProvider).load(weekStart)

        assertEquals(listOf("Read", "Walk"), recap.rows.map { it.name })
        assertEquals(1, recap.rows[0].colorArgb)
        assertEquals(RecapMark.DONE, recap.rows[0].marks[0])
        assertEquals(RecapMark.MISSED, recap.rows[0].marks[1])
        assertEquals(RecapMark.DONE, recap.rows[1].marks[6])
        assertEquals(2, recap.done)
        assertEquals(3, recap.total)
        assertEquals(67, recap.percent)
    }

    @Test
    fun `habits archived before the week or created after it are left out`() = runTest {
        stub(
            listOf(
                habit(1L, "Kept"),
                habit(2L, "Archived before", archivedAt = LocalDate.parse("2026-10-12")),
                habit(3L, "Created after", createdAt = "2026-10-19T08:00:00Z"),
            ),
            mapOf(
                1L to listOf(entry(1L, "2026-10-12", "COMPLETED")),
                2L to listOf(entry(2L, "2026-10-12", "COMPLETED")),
                3L to listOf(entry(3L, "2026-10-12", "COMPLETED")),
            ),
        )

        val recap = WeeklyRecapLoader(habitRepository, entryDao, timeProvider).load(weekStart)

        assertEquals(listOf("Kept"), recap.rows.map { it.name })
    }

    @Test
    fun `entries on or after a mid-week archive date are not counted`() = runTest {
        stub(
            listOf(habit(1L, "Archived Wednesday", archivedAt = LocalDate.parse("2026-10-14"))),
            mapOf(
                1L to listOf(
                    entry(1L, "2026-10-12", "COMPLETED"),
                    entry(1L, "2026-10-14", "MISSED"),
                    entry(1L, "2026-10-15", "MISSED"),
                ),
            ),
        )

        val recap = WeeklyRecapLoader(habitRepository, entryDao, timeProvider).load(weekStart)

        assertEquals(1, recap.rows.single().done)
        assertEquals(1, recap.rows.single().total)
    }
}
