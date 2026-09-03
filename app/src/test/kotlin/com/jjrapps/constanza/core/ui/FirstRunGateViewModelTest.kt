package com.jjrapps.constanza.core.ui

import app.cash.turbine.test
import com.jjrapps.constanza.localization.AppLanguage
import com.jjrapps.constanza.localization.AppLocaleController
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
 * first-run-onboarding design.md §10 row 1 (onboarding spec "Once-Per-Install Onboarding Gate"),
 * extended by app-localization design.md D3, which folds the resolved language into this same gate
 * rather than adding a second one.
 *
 * [ReminderSettingsStore] and [AppLocaleController] are faked with MockK rather than backed by a
 * real `DataStore`, since the tri-state contract under test needs precise control over exactly when
 * each upstream flow emits — a real `DataStore` file cannot be held mid-read deterministically.
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
    fun `startupState is null before the store's flow emits, then reflects the store`() = runTest {
        val upstream = MutableSharedFlow<Boolean>(replay = 0)
        val viewModel = viewModel(onboardingDone = upstream)

        viewModel.startupState.test {
            assertNull(awaitItem())

            upstream.emit(false)
            assertEquals(StartupState(onboardingDone = false, language = AppLanguage.SystemDefault), awaitItem())

            upstream.emit(true)
            assertEquals(StartupState(onboardingDone = true, language = AppLanguage.SystemDefault), awaitItem())
        }
    }

    /**
     * app-localization D3: the gate must hold blank until BOTH facts are known, not just the
     * onboarding flag. If it opened on the flag alone, the first frame would render in whatever
     * language happened to be the default — which is exactly the flash this design exists to avoid.
     */
    @Test
    fun `startupState stays null while only the onboarding flag has emitted`() = runTest {
        val language = MutableSharedFlow<AppLanguage>(replay = 0)
        val viewModel = viewModel(
            onboardingDone = MutableStateFlow(true),
            language = language,
        )

        viewModel.startupState.test {
            assertNull(awaitItem())
            expectNoEvents()

            language.emit(AppLanguage.Spanish)
            assertEquals(StartupState(onboardingDone = true, language = AppLanguage.Spanish), awaitItem())
        }
    }

    @Test
    fun `startupState surfaces false for a fresh install`() = runTest {
        val viewModel = viewModel(onboardingDone = MutableStateFlow(false))

        viewModel.startupState.test {
            assertEquals(StartupState(onboardingDone = false, language = AppLanguage.SystemDefault), awaitItem())
        }
    }

    @Test
    fun `startupState surfaces true for an install that already completed onboarding`() = runTest {
        val viewModel = viewModel(onboardingDone = MutableStateFlow(true))

        viewModel.startupState.test {
            assertEquals(StartupState(onboardingDone = true, language = AppLanguage.SystemDefault), awaitItem())
        }
    }

    /** An explicit override reaches the gate, so the very first composed frame is already in the
     *  chosen language rather than correcting itself a frame later. */
    @Test
    fun `startupState carries an explicit language override`() = runTest {
        val viewModel = viewModel(
            onboardingDone = MutableStateFlow(true),
            language = MutableStateFlow(AppLanguage.Spanish),
        )

        viewModel.startupState.test {
            assertEquals(StartupState(onboardingDone = true, language = AppLanguage.Spanish), awaitItem())
        }
    }

    private fun viewModel(
        onboardingDone: kotlinx.coroutines.flow.Flow<Boolean>,
        language: kotlinx.coroutines.flow.Flow<AppLanguage> = MutableStateFlow(AppLanguage.SystemDefault),
    ): FirstRunGateViewModel {
        val settingsStore = mockk<ReminderSettingsStore> {
            every { this@mockk.onboardingDone } returns onboardingDone
        }
        val appLocaleController = mockk<AppLocaleController> { every { observe() } returns language }
        return FirstRunGateViewModel(settingsStore, appLocaleController)
    }
}
