package com.jjrapps.constanza.onboarding

import com.jjrapps.constanza.reminding.NotificationPermission
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * first-run-onboarding design.md §7, §9, A4. [NotificationPermission] is mocked, never real —
 * mandatory, not preference: `Build.VERSION.SDK_INT` is `0` under `isReturnDefaultValues` on the
 * JVM unit-test runner, so an unmocked `decide()` would always answer `NOT_APPLICABLE` regardless
 * of what the test means to exercise (design.md §10 row 2, mirrors
 * `com.jjrapps.constanza.tracking.TodayViewModelTest`'s identical requirement).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Task 4.2: the page list, one source of truth for the API 31-32 divergence (design.md §7) --

    @Test
    fun `the page list includes the notifications page when the permission is applicable`() = runTest {
        val viewModel = buildViewModel(
            notificationPermission = mockk {
                every { decide(any(), any()) } returns NotificationPermissionDecision.SHOULD_REQUEST
            },
        )

        assertEquals(listOf(OnboardingPage.Intro, OnboardingPage.Notifications), viewModel.uiState.first().pages)
    }

    @Test
    fun `the page list is intro-only when the permission is not applicable, API 31-32`() = runTest {
        val viewModel = buildViewModel(
            notificationPermission = mockk {
                every { decide(any(), any()) } returns NotificationPermissionDecision.NOT_APPLICABLE
            },
        )

        assertEquals(listOf(OnboardingPage.Intro), viewModel.uiState.first().pages)
    }

    // -- Task 4.4: all four permission states surface into the ui state --

    @Test
    fun `all four permission decisions surface into the ui state`() = runTest {
        for (decision in NotificationPermissionDecision.entries) {
            val viewModel = buildViewModel(
                notificationPermission = mockk { every { decide(any(), any()) } returns decision },
            )
            assertEquals(decision, viewModel.uiState.first().permission)
        }
    }

    /** Onboarding spec "Non-Blocking Permission Ask": denying or granting is not read here at
     *  all — the flag means "we have asked", not "the user agreed" (design.md §2.2), the same
     *  argument `com.jjrapps.constanza.tracking.TodayViewModel` makes for its own identical flag. */
    @Test
    fun `recordRequestedNotificationPermission writes the flag and moves SHOULD_REQUEST to BLOCKED`() = runTest {
        var alreadyAsked = false
        val settingsStore = mockk<ReminderSettingsStore>(relaxUnitFun = true) {
            coEvery { hasRequestedNotificationPermission() } answers { alreadyAsked }
            coEvery { recordRequestedNotificationPermission() } answers { alreadyAsked = true }
        }
        val notificationPermission = mockk<NotificationPermission> {
            every { decide(any(), any()) } answers {
                if (firstArg<Boolean>()) {
                    NotificationPermissionDecision.BLOCKED
                } else {
                    NotificationPermissionDecision.SHOULD_REQUEST
                }
            }
        }
        val viewModel = buildViewModel(notificationPermission = notificationPermission, settingsStore = settingsStore)
        assertEquals(NotificationPermissionDecision.SHOULD_REQUEST, viewModel.uiState.first().permission)

        viewModel.recordRequestedNotificationPermission()

        coVerify(exactly = 1) { settingsStore.recordRequestedNotificationPermission() }
        assertEquals(NotificationPermissionDecision.BLOCKED, viewModel.uiState.first().permission)
    }

    /** Onboarding spec "Completion Commits At Handoff, Never On A Content Outcome" (design.md §9):
     *  [OnboardingViewModel.finish] writes unconditionally — it takes no argument about what the
     *  user answered, because it is called regardless of the answer. */
    @Test
    fun `finish writes onboardingDone through the store regardless of the permission answer`() = runTest {
        val settingsStore = mockk<ReminderSettingsStore>(relaxUnitFun = true) {
            coEvery { hasRequestedNotificationPermission() } returns false
        }
        val viewModel = buildViewModel(settingsStore = settingsStore)

        viewModel.finish()

        coVerify(exactly = 1) { settingsStore.setOnboardingDone() }
    }

    @Test
    fun `next advances the index by one`() = runTest {
        val viewModel = buildViewModel(
            notificationPermission = mockk {
                every { decide(any(), any()) } returns NotificationPermissionDecision.SHOULD_REQUEST
            },
        )
        assertEquals(0, viewModel.uiState.first().index)

        viewModel.next()

        assertEquals(1, viewModel.uiState.first().index)
    }

    private fun buildViewModel(
        notificationPermission: NotificationPermission = mockk {
            every { decide(any(), any()) } returns NotificationPermissionDecision.GRANTED
        },
        settingsStore: ReminderSettingsStore = mockk(relaxUnitFun = true) {
            coEvery { hasRequestedNotificationPermission() } returns false
        },
    ) = OnboardingViewModel(notificationPermission, settingsStore)
}
