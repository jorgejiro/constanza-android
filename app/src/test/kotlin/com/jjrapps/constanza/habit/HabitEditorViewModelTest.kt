package com.jjrapps.constanza.habit

import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.Schedule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.DayOfWeek
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EXISTING_HABIT_ID = 7L
private val NOW = Instant.parse("2026-09-01T08:00:00Z")

/**
 * Task 6a.2/6a.3 (habit-management: Creation requires a name, Habit Editing). [habitRepository]
 * is mocked with explicit stubs everywhere a branch depends on its return value — a relaxed mock
 * would silently return `null`/defaults and mask exactly the create-vs-update branch this suite
 * tests (a lesson from this project's notification-response work).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HabitEditorViewModelTest {

    private val habitRepository = mockk<HabitRepository>()
    private val timeProvider = mockk<TimeProvider> {
        every { now() } returns NOW
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = HabitEditorViewModel(habitRepository, timeProvider)

    @Test
    fun `save is blocked and no habit is created while the name is empty`() = runTest {
        val viewModel = newViewModel()

        viewModel.save()

        assertTrue(viewModel.uiState.value.nameError)
        coVerify(exactly = 0) { habitRepository.create(any(), any()) }
    }

    @Test
    fun `save is blocked while the name is whitespace-only`() = runTest {
        val viewModel = newViewModel()
        viewModel.onNameChange("   ")

        viewModel.save()

        assertTrue(viewModel.uiState.value.nameError)
        coVerify(exactly = 0) { habitRepository.create(any(), any()) }
    }

    @Test
    fun `changing the name after a blocked save clears the error`() = runTest {
        val viewModel = newViewModel()
        viewModel.save()
        assertTrue(viewModel.uiState.value.nameError)

        viewModel.onNameChange("Drink water")

        assertFalse(viewModel.uiState.value.nameError)
    }

    @Test
    fun `a valid save creates a new habit with a trimmed name and a DAILY schedule`() = runTest {
        val habitSlot = slot<Habit>()
        val scheduleSlot = slot<Schedule>()
        coEvery { habitRepository.create(capture(habitSlot), capture(scheduleSlot)) } returns 42L
        val viewModel = newViewModel()
        viewModel.onNameChange("  Drink water  ")
        viewModel.onQuestionChange("Did you drink water?")
        viewModel.onNotesChange("  ")

        viewModel.save()

        assertEquals("Drink water", habitSlot.captured.name)
        assertEquals("Did you drink water?", habitSlot.captured.question)
        assertNull(habitSlot.captured.notes)
        assertEquals(Schedule.Daily(DayOfWeek.MONDAY), scheduleSlot.captured)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `starting an edit loads the existing habit and its persisted schedule`() = runTest {
        val existing = Habit(
            id = EXISTING_HABIT_ID,
            name = "Read",
            question = "Did you read?",
            colorArgb = 0xFF1E88E5.toInt(),
            notes = "Any book counts",
            archived = false,
            archivedAt = null,
            createdAt = NOW,
            sortOrder = 0,
        )
        coEvery { habitRepository.findById(EXISTING_HABIT_ID) } returns existing
        coEvery { habitRepository.findScheduleFor(EXISTING_HABIT_ID) } returns Schedule.Weekly(DayOfWeek.TUESDAY)
        val viewModel = newViewModel()

        viewModel.startEdit(EXISTING_HABIT_ID)

        val state = viewModel.uiState.value
        assertEquals(EXISTING_HABIT_ID, state.habitId)
        assertEquals("Read", state.name)
        assertEquals(Schedule.Weekly(DayOfWeek.TUESDAY), state.schedule)
    }

    @Test
    fun `saving an edited habit calls update, not create, and preserves its loaded schedule`() = runTest {
        val existing = Habit(
            id = EXISTING_HABIT_ID,
            name = "Read",
            question = null,
            colorArgb = 0,
            notes = null,
            archived = false,
            archivedAt = null,
            createdAt = NOW,
            sortOrder = 0,
        )
        coEvery { habitRepository.findById(EXISTING_HABIT_ID) } returns existing
        coEvery { habitRepository.findScheduleFor(EXISTING_HABIT_ID) } returns Schedule.Weekly(DayOfWeek.TUESDAY)
        coEvery { habitRepository.update(any(), any()) } returns Unit
        val viewModel = newViewModel()
        viewModel.startEdit(EXISTING_HABIT_ID)

        viewModel.onNameChange("Read daily")
        viewModel.save()

        coVerify(exactly = 1) {
            habitRepository.update(match { it.name == "Read daily" }, Schedule.Weekly(DayOfWeek.TUESDAY))
        }
        coVerify(exactly = 0) { habitRepository.create(any(), any()) }
    }
}
