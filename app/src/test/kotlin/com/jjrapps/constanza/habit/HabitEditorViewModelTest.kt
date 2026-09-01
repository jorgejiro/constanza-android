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
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EXISTING_HABIT_ID = 7L
private val NOW = Instant.parse("2026-09-01T08:00:00Z")
private val TODAY = LocalDate.parse("2026-09-01")

/**
 * Task 6a.2/6a.3/6a.1 (habit-management: Creation requires a name, Habit Editing; habit-scheduling:
 * Six Frequency Kinds, Reminder Slots for TIMES_PER_DAY). [habitRepository] is mocked with explicit
 * stubs everywhere a branch depends on its return value — a relaxed mock would silently return
 * `null`/defaults and mask exactly the create-vs-update branch this suite tests (a lesson from this
 * project's notification-response work).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HabitEditorViewModelTest {

    private val habitRepository = mockk<HabitRepository>()
    private val timeProvider = mockk<TimeProvider> {
        every { now() } returns NOW
        every { today() } returns TODAY
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
        coEvery { habitRepository.findSlotsFor(EXISTING_HABIT_ID) } returns emptyList()
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
        coEvery { habitRepository.findSlotsFor(EXISTING_HABIT_ID) } returns emptyList()
        coEvery { habitRepository.update(any(), any(), any()) } returns Unit
        val viewModel = newViewModel()
        viewModel.startEdit(EXISTING_HABIT_ID)

        viewModel.onNameChange("Read daily")
        viewModel.save()

        coVerify(exactly = 1) {
            habitRepository.update(match { it.name == "Read daily" }, Schedule.Weekly(DayOfWeek.TUESDAY), emptyList())
        }
        coVerify(exactly = 0) { habitRepository.create(any(), any(), any()) }
    }

    // --- Task 6a.1, slice ii-a: schedule-kind picker and per-kind parameter editors ---

    @Test
    fun `switching to WEEKLY seeds Monday and switching to N_TIMES_PER_WEEK seeds a default quota`() = runTest {
        val viewModel = newViewModel()

        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.WEEKLY))
        assertEquals(Schedule.Weekly(DayOfWeek.MONDAY), viewModel.uiState.value.schedule)

        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.N_TIMES_PER_WEEK))
        assertEquals(3, (viewModel.uiState.value.schedule as Schedule.NTimesPerWeek).times)
    }

    @Test
    fun `switching to EVERY_N_DAYS seeds today as the anchor and switching away clears the anchor text`() = runTest {
        val viewModel = newViewModel()

        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.EVERY_N_DAYS))
        val everyNDays = viewModel.uiState.value.schedule as Schedule.EveryNDays
        assertEquals(TODAY, everyNDays.anchor)
        assertEquals(TODAY.toString(), viewModel.uiState.value.anchorDateText)

        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.DAILY))
        assertEquals("", viewModel.uiState.value.anchorDateText)
    }

    @Test
    fun `N_TIMES_PER_WEEK times cannot go below 1`() = runTest {
        val viewModel = newViewModel()
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.N_TIMES_PER_WEEK))

        viewModel.onScheduleParamChange(ScheduleParamAction.TimesPerWeek(0))

        assertEquals(1, (viewModel.uiState.value.schedule as Schedule.NTimesPerWeek).times)
    }

    @Test
    fun `MONTHLY day of month is clamped to the 1 to 31 schema bound`() = runTest {
        val viewModel = newViewModel()
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.MONTHLY))

        viewModel.onScheduleParamChange(ScheduleParamAction.DayOfMonth(99))

        assertEquals(31, (viewModel.uiState.value.schedule as Schedule.Monthly).dayOfMonth)
    }

    @Test
    fun `EVERY_N_DAYS n cannot go below 1`() = runTest {
        val viewModel = newViewModel()
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.EVERY_N_DAYS))

        viewModel.onScheduleParamChange(ScheduleParamAction.EveryNDays(0))

        assertEquals(1, (viewModel.uiState.value.schedule as Schedule.EveryNDays).n)
    }

    @Test
    fun `a schedule param action for a non-matching schedule kind is a no-op`() = runTest {
        val viewModel = newViewModel() // starts as DAILY

        viewModel.onScheduleParamChange(ScheduleParamAction.TimesPerWeek(5))

        assertEquals(Schedule.Daily(DayOfWeek.MONDAY), viewModel.uiState.value.schedule)
    }

    // --- Task 6a.1, slice ii-a: TIMES_PER_DAY reminder-slot editor ---

    @Test
    fun `adding a slot to a TIMES_PER_DAY schedule appends an enabled slot and clears the slots error`() = runTest {
        val viewModel = newViewModel()
        viewModel.onNameChange("Stretch")
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.TIMES_PER_DAY))
        viewModel.save() // no slots yet — triggers slotsError
        assertTrue(viewModel.uiState.value.slotsError)

        viewModel.onSlotAction(SlotAction.Add)

        val slots = viewModel.uiState.value.slots
        assertEquals(1, slots.size)
        assertTrue(slots.single().enabled)
        assertFalse(viewModel.uiState.value.slotsError)
    }

    @Test
    fun `removing a slot by index drops only that slot`() = runTest {
        val viewModel = newViewModel()
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.TIMES_PER_DAY))
        viewModel.onSlotAction(SlotAction.Add)
        viewModel.onSlotAction(SlotAction.Add)

        viewModel.onSlotAction(SlotAction.Remove(0))

        assertEquals(1, viewModel.uiState.value.slots.size)
    }

    @Test
    fun `disabling a slot flips only its enabled flag`() = runTest {
        val viewModel = newViewModel()
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.TIMES_PER_DAY))
        viewModel.onSlotAction(SlotAction.Add)

        viewModel.onSlotAction(SlotAction.SetEnabled(0, false))

        assertFalse(viewModel.uiState.value.slots.single().enabled)
    }

    @Test
    fun `changing a slot's time is bounded to a single day and reaches only the targeted slot`() = runTest {
        val viewModel = newViewModel()
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.TIMES_PER_DAY))
        viewModel.onSlotAction(SlotAction.Add)
        viewModel.onSlotAction(SlotAction.Add)

        viewModel.onSlotAction(SlotAction.SetTime(1, MINUTES_PER_DAY_TEST + 30))

        val slots = viewModel.uiState.value.slots
        assertEquals(480, slots[0].minuteOfDay)
        assertEquals(MINUTES_PER_DAY_TEST - 1, slots[1].minuteOfDay)
    }

    @Test
    fun `save is blocked for TIMES_PER_DAY with zero slots`() = runTest {
        val viewModel = newViewModel()
        viewModel.onNameChange("Stretch")
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.TIMES_PER_DAY))

        viewModel.save()

        assertTrue(viewModel.uiState.value.slotsError)
        coVerify(exactly = 0) { habitRepository.create(any(), any(), any()) }
    }

    @Test
    fun `save is blocked for an unparsable EVERY_N_DAYS anchor`() = runTest {
        val viewModel = newViewModel()
        viewModel.onNameChange("Water plants")
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.EVERY_N_DAYS))

        viewModel.onScheduleParamChange(ScheduleParamAction.AnchorDate("not-a-date"))
        viewModel.save()

        assertTrue(viewModel.uiState.value.anchorDateError)
        coVerify(exactly = 0) { habitRepository.create(any(), any(), any()) }
    }

    @Test
    fun `a valid TIMES_PER_DAY save passes its slots through to the repository`() = runTest {
        coEvery { habitRepository.create(any(), any(), any()) } returns 1L
        val viewModel = newViewModel()
        viewModel.onNameChange("Stretch")
        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.TIMES_PER_DAY))
        viewModel.onSlotAction(SlotAction.Add)

        viewModel.save()

        coVerify(exactly = 1) {
            habitRepository.create(any(), any(), match { it.size == 1 && it.single().enabled })
        }
    }

    // --- Task 6a.8: single optional reminder time for the five non-TIMES_PER_DAY kinds ---

    @Test
    fun `adding a reminder time to a DAILY habit adds exactly one slot`() = runTest {
        val viewModel = newViewModel() // starts as DAILY, no reminder time

        viewModel.onSlotAction(SlotAction.Add)

        assertEquals(1, viewModel.uiState.value.slots.size)
    }

    @Test
    fun `a second Add on a non-TIMES_PER_DAY kind is a no-op — only one reminder time is allowed`() = runTest {
        val viewModel = newViewModel()
        viewModel.onSlotAction(SlotAction.Add)

        viewModel.onSlotAction(SlotAction.Add)

        assertEquals(1, viewModel.uiState.value.slots.size)
    }

    @Test
    fun `removing the reminder time returns the habit to no reminder`() = runTest {
        val viewModel = newViewModel()
        viewModel.onSlotAction(SlotAction.Add)

        viewModel.onSlotAction(SlotAction.Remove(0))

        assertTrue(viewModel.uiState.value.slots.isEmpty())
    }

    @Test
    fun `the reminder time survives switching between two non-TIMES_PER_DAY kinds`() = runTest {
        val viewModel = newViewModel()
        viewModel.onSlotAction(SlotAction.Add)

        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.WEEKLY))

        assertEquals(1, viewModel.uiState.value.slots.size)
    }

    @Test
    fun `the reminder time is cleared when switching into or out of TIMES_PER_DAY`() = runTest {
        val viewModel = newViewModel()
        viewModel.onSlotAction(SlotAction.Add)

        viewModel.onScheduleParamChange(ScheduleParamAction.Kind(ScheduleKind.TIMES_PER_DAY))

        assertTrue(viewModel.uiState.value.slots.isEmpty())
    }

    @Test
    fun `save is never blocked by a missing reminder time — the habit is accepted with none armed`() = runTest {
        coEvery { habitRepository.create(any(), any(), any()) } returns 1L
        val viewModel = newViewModel()
        viewModel.onNameChange("Meditate") // DAILY, no reminder time set

        viewModel.save()

        assertFalse(viewModel.uiState.value.nameError)
        coVerify(exactly = 1) { habitRepository.create(any(), any(), emptyList()) }
    }

    @Test
    fun `a habit saved with a reminder time passes that single slot through to the repository`() = runTest {
        coEvery { habitRepository.create(any(), any(), any()) } returns 1L
        val viewModel = newViewModel()
        viewModel.onNameChange("Meditate")
        viewModel.onSlotAction(SlotAction.Add)

        viewModel.save()

        coVerify(exactly = 1) {
            habitRepository.create(any(), any(), match { it.size == 1 && it.single().enabled })
        }
    }
}

private const val MINUTES_PER_DAY_TEST = 24 * 60
