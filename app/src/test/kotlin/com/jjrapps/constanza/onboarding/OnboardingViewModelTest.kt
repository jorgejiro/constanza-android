package com.jjrapps.constanza.onboarding

import com.jjrapps.constanza.reminding.NotificationPermission
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        assertEquals(listOf(OnboardingPage.Intro, OnboardingPage.Permissions), viewModel.uiState.first().pages)
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

    /** onboarding: Two-Screen Flow, Applicability-Derived — the API 31 fresh-install leg. Exact
     *  alarms are granted by default pre-Android-14 and `POST_NOTIFICATIONS` does not exist below
     *  API 33, so neither ask applies and screen 2 does not exist. */
    @Test
    fun `the page list is intro-only on API 31 fresh install, where nothing applies`() = runTest {
        val viewModel = buildViewModel(
            notificationPermission = mockk {
                every { decide(any(), any()) } returns NotificationPermissionDecision.NOT_APPLICABLE
            },
            alarmScheduler = mockk { every { canScheduleExactAlarms() } returns true },
        )

        assertEquals(listOf(OnboardingPage.Intro), viewModel.uiState.first().pages)
    }

    /** onboarding: Two-Screen Flow, Applicability-Derived — the API 31 revoked-exact-alarms leg.
     *  Notification stays inapplicable, but the exact-alarm OR term alone must still admit screen 2
     *  with exactly one applicable row. */
    @Test
    fun `the page list includes the permissions page on API 31 with exact alarms revoked`() = runTest {
        val viewModel = buildViewModel(
            notificationPermission = mockk {
                every { decide(any(), any()) } returns NotificationPermissionDecision.NOT_APPLICABLE
            },
            alarmScheduler = mockk { every { canScheduleExactAlarms() } returns false },
        )

        assertEquals(listOf(OnboardingPage.Intro, OnboardingPage.Permissions), viewModel.uiState.first().pages)
    }

    /** design.md decision 4 — one [OnboardingViewModel.refresh] call updates both the notification
     *  permission and the exact-alarm eligibility, never only one of them. */
    @Test
    fun `refresh re-reads both the notification permission and exact-alarm eligibility from one call`() = runTest {
        // Driven by variables rather than a fixed `andThen` sequence, for the same reason
        // `refreshNotificationPermission picks up a permission granted while the screen was paused`
        // (com.jjrapps.constanza.tracking.TodayViewModelTest) is: construction seeds both flows with
        // one call each of its own before `init`'s `refresh()` reads them again, so a fixed call
        // sequence would encode that internal call count into the test.
        var grantedInSystemSettings = false
        var exactAlarmsGrantedInSystemSettings = false
        val notificationPermission = mockk<NotificationPermission> {
            every { decide(any(), any()) } answers {
                if (grantedInSystemSettings) {
                    NotificationPermissionDecision.GRANTED
                } else {
                    NotificationPermissionDecision.BLOCKED
                }
            }
        }
        val alarmScheduler = mockk<AlarmScheduler> {
            every { canScheduleExactAlarms() } answers { exactAlarmsGrantedInSystemSettings }
        }
        val viewModel = buildViewModel(notificationPermission = notificationPermission, alarmScheduler = alarmScheduler)
        assertEquals(NotificationPermissionDecision.BLOCKED, viewModel.uiState.first().permission)
        assertFalse(viewModel.uiState.first().canScheduleExactAlarms)

        grantedInSystemSettings = true
        exactAlarmsGrantedInSystemSettings = true
        viewModel.refresh()

        assertEquals(NotificationPermissionDecision.GRANTED, viewModel.uiState.first().permission)
        assertTrue(viewModel.uiState.first().canScheduleExactAlarms)
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
        // Defaulted to granted, never `mockk(relaxed = true)`: a relaxed mock answers `Boolean`
        // with `false`, which would silently add the exact-alarm page to every existing test above
        // that never meant to exercise it — the same trap `TodayViewModelTest.buildViewModel`'s
        // identical default exists for.
        alarmScheduler: AlarmScheduler = mockk {
            every { canScheduleExactAlarms() } returns true
        },
    ) = OnboardingViewModel(notificationPermission, settingsStore, alarmScheduler)
}
