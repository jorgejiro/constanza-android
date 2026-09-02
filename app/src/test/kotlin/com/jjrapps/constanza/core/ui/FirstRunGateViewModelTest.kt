package com.jjrapps.constanza.core.ui

import app.cash.turbine.test
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * first-run-onboarding design.md §10 row 1 (onboarding spec "Once-Per-Install Onboarding Gate").
 * [ReminderSettingsStore] is faked with MockK rather than a real `DataStore`, since the tri-state
 * contract under test needs precise control over exactly when the upstream flow emits — a real
 * `DataStore` file cannot be held mid-read deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunGateViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The tri-state's whole reason to exist (design.md §4.2): `null` while the DataStore read is
     *  in flight, so the gate can hold blank instead of guessing. A [MutableSharedFlow] with no
     *  replay is used here specifically because it never emits until told to — a [MutableStateFlow]
     *  cannot represent "has not emitted yet" at all, since it always carries a value. */
    @Test
    fun `onboardingDone is null before the store's flow emits, then reflects the store`() = runTest {
        val upstream = MutableSharedFlow<Boolean>(replay = 0)
        val settingsStore = mockk<ReminderSettingsStore> { every { onboardingDone } returns upstream }
        val viewModel = FirstRunGateViewModel(settingsStore)

        viewModel.onboardingDone.test {
            assertNull(awaitItem())

            upstream.emit(false)
            assertEquals(false, awaitItem())

            upstream.emit(true)
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `onboardingDone surfaces false for a fresh install`() = runTest {
        val settingsStore = mockk<ReminderSettingsStore> {
            every { onboardingDone } returns MutableStateFlow(false)
        }
        val viewModel = FirstRunGateViewModel(settingsStore)

        viewModel.onboardingDone.test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `onboardingDone surfaces true for an install that already completed onboarding`() = runTest {
        val settingsStore = mockk<ReminderSettingsStore> {
            every { onboardingDone } returns MutableStateFlow(true)
        }
        val viewModel = FirstRunGateViewModel(settingsStore)

        viewModel.onboardingDone.test {
            assertEquals(true, awaitItem())
        }
    }
}
