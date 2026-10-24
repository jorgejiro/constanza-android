package com.jjrapps.constanza.recap

import app.cash.turbine.test
import com.jjrapps.constanza.scheduling.MorningRecapAlarmScheduler
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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MorningRecapSettingsViewModelTest {

    private val settingsStore = mockk<MorningRecapSettingsStore>(relaxUnitFun = true) {
        every { this@mockk.enabled } returns MutableStateFlow(false)
        every { this@mockk.timeMinuteOfDay } returns MutableStateFlow(8 * 60)
    }
    private val alarmScheduler = mockk<MorningRecapAlarmScheduler>(relaxUnitFun = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState reflects the store`() = runTest {
        val viewModel = MorningRecapSettingsViewModel(settingsStore, alarmScheduler)

        viewModel.uiState.test {
            assertEquals(MorningRecapSettingsUiState(enabled = false, timeMinuteOfDay = 8 * 60), awaitItem())
        }
    }

    @Test
    fun `setTimeMinuteOfDay writes the time and re-anchors the alarm`() = runTest {
        val viewModel = MorningRecapSettingsViewModel(settingsStore, alarmScheduler)

        viewModel.setTimeMinuteOfDay(6 * 60 + 30)

        coVerify { settingsStore.setTimeMinuteOfDay(6 * 60 + 30) }
        coVerify { alarmScheduler.scheduleNext() }
    }

    @Test
    fun `setEnabled writes to the store without touching the scheduler`() = runTest {
        val viewModel = MorningRecapSettingsViewModel(settingsStore, alarmScheduler)

        viewModel.setEnabled(true)

        coVerify { settingsStore.setEnabled(true) }
        coVerify(exactly = 0) { alarmScheduler.scheduleNext() }
    }
}
