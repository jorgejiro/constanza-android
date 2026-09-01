package com.jjrapps.constanza.habit

import app.cash.turbine.test
import com.jjrapps.constanza.domain.model.Habit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val CREATED_AT = Instant.parse("2026-09-01T08:00:00Z")

/** Task 6a.4 (habit-management: Habit Archiving, Un-archiving does not back-fill). */
@OptIn(ExperimentalCoroutinesApi::class)
class HabitListViewModelTest {

    private fun habit(id: Long, name: String, archived: Boolean) = Habit(
        id = id,
        name = name,
        question = null,
        colorArgb = 0,
        notes = null,
        archived = archived,
        archivedAt = null,
        createdAt = CREATED_AT,
        sortOrder = 0,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the active filter shows only non-archived habits by default`() = runTest {
        val habits = MutableStateFlow(listOf(habit(1, "Read", archived = false), habit(2, "Old", archived = true)))
        val habitRepository = mockk<HabitRepository> {
            every { observeAll() } returns habits
        }
        val viewModel = HabitListViewModel(habitRepository)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("Read"), state.habits.map { it.name })
            assertFalse(state.showArchived)
        }
    }

    @Test
    fun `toggling the filter shows only archived habits`() = runTest {
        val habits = MutableStateFlow(listOf(habit(1, "Read", archived = false), habit(2, "Old", archived = true)))
        val habitRepository = mockk<HabitRepository> {
            every { observeAll() } returns habits
        }
        val viewModel = HabitListViewModel(habitRepository)

        viewModel.uiState.test {
            assertEquals(listOf("Read"), awaitItem().habits.map { it.name }) // initial: active filter

            viewModel.toggleShowArchived()

            val state = awaitItem()
            assertEquals(listOf("Old"), state.habits.map { it.name })
            assertTrue(state.showArchived)
        }
    }

    @Test
    fun `archiving a habit calls the repository and moves it out of the active filter`() = runTest {
        val habits = MutableStateFlow(listOf(habit(1, "Read", archived = false)))
        val habitRepository = mockk<HabitRepository> {
            every { observeAll() } returns habits
            coEvery { setArchived(1L, true) } coAnswers {
                habits.value = habits.value.map { it.copy(archived = true) }
            }
        }
        val viewModel = HabitListViewModel(habitRepository)

        viewModel.uiState.test {
            assertEquals(listOf("Read"), awaitItem().habits.map { it.name }) // initial: active filter

            viewModel.setArchived(1L, true)

            val state = awaitItem()
            assertTrue(state.habits.isEmpty())
            coVerify(exactly = 1) { habitRepository.setArchived(1L, true) }
        }
    }

    @Test
    fun `un-archiving round-trips a habit back into the active filter`() = runTest {
        val habits = MutableStateFlow(listOf(habit(1, "Read", archived = true)))
        val habitRepository = mockk<HabitRepository> {
            every { observeAll() } returns habits
            coEvery { setArchived(1L, false) } coAnswers {
                habits.value = habits.value.map { it.copy(archived = false) }
            }
        }
        val viewModel = HabitListViewModel(habitRepository)

        viewModel.uiState.test {
            assertTrue(awaitItem().habits.isEmpty()) // initial: active filter, seeded habit is archived

            viewModel.setArchived(1L, false)

            val state = awaitItem()
            assertEquals(listOf("Read"), state.habits.map { it.name })
        }
    }
}
