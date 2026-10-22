package com.jjrapps.constanza.recap

import com.jjrapps.constanza.core.time.TimeProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** weekly-recap S4/S7: the notification names its week; opened from the app, it is last week. */
@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyRecapViewModelTest {

    private val loader = mockk<WeeklyRecapLoader>()
    private val timeProvider = mockk<TimeProvider>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { loader.load(any()) } answers {
            LoadedWeeklyRecap(firstArg(), rows = emptyList(), done = 0, total = 0, percent = 0)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opened from the app on a Thursday it shows the Monday-to-Sunday week before`() = runTest {
        every { timeProvider.today() } returns LocalDate.parse("2026-10-22")
        val viewModel = WeeklyRecapViewModel(loader, timeProvider)

        viewModel.load(weekStart = null)

        assertEquals(LocalDate.parse("2026-10-12"), viewModel.uiState.value.recap?.weekStart)
    }

    @Test
    fun `opened from the app on a Monday it shows the week that just ended`() = runTest {
        every { timeProvider.today() } returns LocalDate.parse("2026-10-19")
        val viewModel = WeeklyRecapViewModel(loader, timeProvider)

        viewModel.load(weekStart = null)

        assertEquals(LocalDate.parse("2026-10-12"), viewModel.uiState.value.recap?.weekStart)
    }

    @Test
    fun `opened from the notification it shows the week the notification named`() = runTest {
        val viewModel = WeeklyRecapViewModel(loader, timeProvider)

        viewModel.load(weekStart = LocalDate.parse("2026-09-28"))

        assertEquals(LocalDate.parse("2026-09-28"), viewModel.uiState.value.recap?.weekStart)
    }
}
