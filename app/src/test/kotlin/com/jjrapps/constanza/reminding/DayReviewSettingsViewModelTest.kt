package com.jjrapps.constanza.reminding

import app.cash.turbine.test
import com.jjrapps.constanza.scheduling.DayReviewAlarmScheduler
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

/**
 * day-review, slice C (day-review-notification): [DayReviewSettingsViewModel] against a mocked
 * [DayReviewSettingsStore] and [DayReviewAlarmScheduler] (day-review-exact-alarm) — following
 * [com.jjrapps.constanza.core.ui.FirstRunGateViewModelTest]'s own pattern (MockK rather than a real
 * `DataStore` file), since the write-then-reanchor sequencing under test needs a
 * [DayReviewAlarmScheduler] that can never touch a real `AlarmManager` on the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayReviewSettingsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState starts at the store's own defaults`() = runTest {
        val (viewModel, _, _) = newViewModel()

        viewModel.uiState.test {
            assertEquals(DayReviewSettingsUiState(), awaitItem())
        }
    }

    @Test
    fun `uiState reflects a non-default value already held by the store`() = runTest {
        val (viewModel, _, _) = newViewModel(
            reviewTimeMinuteOfDay = 20 * 60,
            reviewFiresEveryNight = false,
        )

        viewModel.uiState.test {
            assertEquals(
                DayReviewSettingsUiState(reviewTimeMinuteOfDay = 20 * 60, reviewFiresEveryNight = false),
                awaitItem(),
            )
        }
    }

    @Test
    fun `setReviewTimeMinuteOfDay writes the new time to the store`() = runTest {
        val (viewModel, settingsStore, _) = newViewModel()

        viewModel.setReviewTimeMinuteOfDay(20 * 60)

        coVerify { settingsStore.setReviewTimeMinuteOfDay(20 * 60) }
    }

    /** The whole reason [DayReviewAlarmScheduler] is a constructor dependency here at all: a time
     *  change that only reached the store would take effect after the next natural run or an app
     *  restart. */
    @Test
    fun `setReviewTimeMinuteOfDay re-anchors the day-review alarm`() = runTest {
        val (viewModel, _, dayReviewAlarmScheduler) = newViewModel()

        viewModel.setReviewTimeMinuteOfDay(20 * 60)

        coVerify { dayReviewAlarmScheduler.scheduleNext() }
    }

    @Test
    fun `setReviewFiresEveryNight writes to the store without touching the scheduler`() = runTest {
        val (viewModel, settingsStore, dayReviewAlarmScheduler) = newViewModel()

        viewModel.setReviewFiresEveryNight(false)

        coVerify { settingsStore.setReviewFiresEveryNight(false) }
        coVerify(exactly = 0) { dayReviewAlarmScheduler.scheduleNext() }
    }

    private fun newViewModel(
        reviewTimeMinuteOfDay: Int = DEFAULT_REVIEW_TIME_MINUTE_OF_DAY,
        reviewFiresEveryNight: Boolean = DEFAULT_REVIEW_FIRES_EVERY_NIGHT,
    ): Triple<DayReviewSettingsViewModel, DayReviewSettingsStore, DayReviewAlarmScheduler> {
        val settingsStore = mockk<DayReviewSettingsStore>(relaxUnitFun = true) {
            every { this@mockk.reviewTimeMinuteOfDay } returns MutableStateFlow(reviewTimeMinuteOfDay)
            every { this@mockk.reviewFiresEveryNight } returns MutableStateFlow(reviewFiresEveryNight)
        }
        val dayReviewAlarmScheduler = mockk<DayReviewAlarmScheduler>(relaxUnitFun = true)
        return Triple(DayReviewSettingsViewModel(settingsStore, dayReviewAlarmScheduler), settingsStore, dayReviewAlarmScheduler)
    }
}
