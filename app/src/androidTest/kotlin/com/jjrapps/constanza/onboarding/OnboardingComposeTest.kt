package com.jjrapps.constanza.onboarding

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.jjrapps.constanza.R
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * first-run-onboarding design.md — screen 2's two-row page, rendered over a hand-built
 * [OnboardingUiState]. No [OnboardingViewModel], no Hilt, no Room: [OnboardingViewModel] is one of
 * `ViewModelTeardownCallSiteTest.GUARDED_VIEW_MODELS`, and its only bare-constructor exemption
 * route (`HabitRepositoryTestFixture.register`) would drag an in-memory Room database into a test
 * about two presentational text rows that touch no database at all.
 *
 * This is also the strongest form of the project's device-free requirement: both the API 31 and
 * API 37 emulator legs of the matrix run these exact same assertions against injected state,
 * rather than against whatever grant defaults each emulator image happens to boot with.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    /** Screen 2, both rows applicable — index 1 is deliberately the last page here, mirroring the
     *  common two-row leg (API 33+ fresh install) rather than the one-row API 31 leg. */
    private fun twoRowState(permission: NotificationPermissionDecision, canScheduleExactAlarms: Boolean) =
        OnboardingUiState(
            pages = listOf(OnboardingPage.Intro, OnboardingPage.Permissions),
            index = 1,
            permission = permission,
            canScheduleExactAlarms = canScheduleExactAlarms,
        )

    private fun setPermissionsPage(state: OnboardingUiState) {
        composeTestRule.setContent {
            OnboardingScaffold(state = state, onPrimaryAction = {}) {
                OnboardingPermissionsPage(
                    permission = state.permission,
                    canScheduleExactAlarms = state.canScheduleExactAlarms,
                    onPermissionRequested = {},
                )
            }
        }
    }

    /** onboarding: Two-Screen Flow, Applicability-Derived — both rows render together on the
     *  applicable leg, and the notification row's copy sits above the exact-alarm row's, matching
     *  the spec's ordering (delivery severity, not a ranking of the two asks: a denied notification
     *  silences the app, a denied exact alarm only widens the delivery window). */
    @Test
    fun bothRowsRenderTogetherWithNotificationsAboveExactAlarms() {
        setPermissionsPage(
            twoRowState(NotificationPermissionDecision.SHOULD_REQUEST, canScheduleExactAlarms = false),
        )

        val notificationTop = composeTestRule
            .onNodeWithText(text(R.string.onboarding_permission_should_request_body))
            .fetchSemanticsNode()
            .boundsInRoot.top
        val exactAlarmTop = composeTestRule
            .onNodeWithText(text(R.string.onboarding_exact_alarm_denied_body))
            .fetchSemanticsNode()
            .boundsInRoot.top

        assert(notificationTop < exactAlarmTop) {
            "expected the notification row (top=$notificationTop) above the exact-alarm row " +
                "(top=$exactAlarmTop)"
        }
    }

    /** onboarding: Exact-Alarm Onboarding Row — copy must state degradation, never silence, and
     *  the granted state is a plain confirmation line with no button, exactly like the notification
     *  row's own [NotificationPermissionDecision.GRANTED] treatment (design decision 2). */
    @Test
    fun theGrantedExactAlarmRowIsAConfirmationLineWithNoButton() {
        setPermissionsPage(twoRowState(NotificationPermissionDecision.GRANTED, canScheduleExactAlarms = true))

        composeTestRule.onNodeWithText(text(R.string.onboarding_exact_alarm_granted_body)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.onboarding_exact_alarm_denied_body)).assertDoesNotExist()
        composeTestRule.onNodeWithText(text(R.string.onboarding_exact_alarm_denied_action)).assertDoesNotExist()
    }

    // onboarding: Non-Blocking Permission Ask — the bottom-slot primary action is a sibling of the
    // row content and stays present and enabled across all four live-state combinations the two
    // rows can independently land in; the flow's forward path never routes through either row.
    // Four separate tests, deliberately, rather than one test looping over `setContent`: a compose
    // rule's `setContent` may only be called once per test, so a second call in the same test
    // throws `IllegalStateException("has already set content")` instead of recomposing.

    @Test
    fun thePrimaryActionStaysEnabledWhenNotificationsShouldBeRequestedAndExactAlarmsAreDenied() {
        setPermissionsPage(twoRowState(NotificationPermissionDecision.SHOULD_REQUEST, canScheduleExactAlarms = false))
        composeTestRule.onNodeWithText(text(R.string.onboarding_action_finish)).assertIsEnabled()
    }

    @Test
    fun thePrimaryActionStaysEnabledWhenNotificationsShouldBeRequestedAndExactAlarmsAreGranted() {
        setPermissionsPage(twoRowState(NotificationPermissionDecision.SHOULD_REQUEST, canScheduleExactAlarms = true))
        composeTestRule.onNodeWithText(text(R.string.onboarding_action_finish)).assertIsEnabled()
    }

    @Test
    fun thePrimaryActionStaysEnabledWhenNotificationsAreBlockedAndExactAlarmsAreDenied() {
        setPermissionsPage(twoRowState(NotificationPermissionDecision.BLOCKED, canScheduleExactAlarms = false))
        composeTestRule.onNodeWithText(text(R.string.onboarding_action_finish)).assertIsEnabled()
    }

    @Test
    fun thePrimaryActionStaysEnabledWhenNotificationsAndExactAlarmsAreBothGranted() {
        setPermissionsPage(twoRowState(NotificationPermissionDecision.GRANTED, canScheduleExactAlarms = true))
        composeTestRule.onNodeWithText(text(R.string.onboarding_action_finish)).assertIsEnabled()
    }

    /** onboarding: Exact-Alarm Onboarding Row — "the screen MUST NOT auto-launch that intent on
     *  its own". Composing the denied row and letting the composition settle must never leave the
     *  system settings app in the foreground; only a deliberate tap does. */
    @Test
    fun theDeniedExactAlarmRowNeverAutoLaunchesTheSettingsIntent() {
        setPermissionsPage(twoRowState(NotificationPermissionDecision.GRANTED, canScheduleExactAlarms = false))
        composeTestRule.waitForIdle()

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(context.packageName, device.currentPackageName)
    }
}
