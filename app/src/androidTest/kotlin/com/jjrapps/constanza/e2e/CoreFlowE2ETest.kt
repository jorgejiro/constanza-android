package com.jjrapps.constanza.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.MainActivity
import com.jjrapps.constanza.domain.model.EntryStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** `POST_NOTIFICATIONS` exists from here up; below it, `NotificationPermission.decide` answers
 *  `NOT_APPLICABLE`. Spelled as a literal for the same reason `NotificationPermission` does. */
private const val RUNTIME_PERMISSION_SDK = 33

private const val UI_TIMEOUT_MS = 15_000L
private const val GRANT_TIMEOUT_MS = 10_000L
private const val GRANT_POLL_INTERVAL_MS = 50L
private const val SEND_TIMEOUT_SECONDS = 10L

private const val REMINDED_HABIT = "Meditate"
private const val ARCHIVED_HABIT = "Stretch"
private const val DELETED_HABIT = "Journal"

/**
 * The device-free core-flow proof: on an emulator with nothing plugged in, the app walks a
 * fresh-install user through onboarding (with the REAL system permission dialog), takes on a habit
 * through its own UI, delivers that habit's reminder as a real posted notification, records the
 * answer tapped on it, and lets the habit be removed again — each step driven and observed the way
 * a person would cause it, not by calling into a repository.
 *
 * See `openspec/config.yaml`'s "Device-free verification" convention for the standing requirement
 * this class exists to satisfy, the exact Gradle command that runs it on both API levels, and —
 * stated plainly there rather than implied here — what a green run does NOT prove about a physical
 * Galaxy S25.
 *
 * ## What is real and what is substituted
 *
 * Real: Hilt's own object graph, the singleton `AppDatabase` and its file, `MainActivity` and every
 * Compose screen — including the first-run onboarding gate (first-run-onboarding) — the
 * `com.android.permissioncontroller` dialog, the manifest-declared `ReminderFireReceiver` and
 * `ActionReceiver`, the process-wide `WorkManager` that `ConstanzaApplication` configured with
 * `HiltWorkerFactory`, and `NotificationPoster` posting to the actual `NotificationManager`.
 *
 * Substituted: exactly one thing — the alarm clock. See
 * [CoreFlowTestFixture.fireArmedAlarmFor] for why `androidx.work.testing` is the wrong lever here
 * and what is delivered instead.
 *
 * ## Method order is load bearing
 *
 * [MethodSorters.NAME_ASCENDING] is not decoration. Granting `POST_NOTIFICATIONS` is a one-way
 * door within an installation: revoking it — by `pm revoke`, by
 * `UiAutomation.revokeRuntimePermission`, by any route — kills the app process, and the
 * instrumentation lives in that process, so a test cannot put the permission back. This was
 * measured on the API 37 emulator, not assumed: the run dies with "Instrumentation run failed due
 * to Process crashed". [a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings] and
 * [a2AllowingTheOnboardingPromptLeavesTodayWithNoNotificationBanner] therefore observe the
 * ungranted/once-denied state before either answers the dialog for real, and their names sort
 * first (`a1` < `a2` < `a3` < `c` < `r`) so they do, ahead of every method that pre-seeds
 * `onboarding_done` and would otherwise skip past the dialog entirely.
 *
 * The same one-way door applies across classes. `NotificationPosterInstrumentedTest` and
 * `NotificationActionWiringInstrumentedTest` also grant the permission, and they are the only other
 * classes in this module that do; both live in `com.jjrapps.constanza.reminding`, which the runner
 * reaches after `com.jjrapps.constanza.e2e` because dex stores class definitions ordered by type
 * descriptor. [a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings] asserts its
 * ungranted precondition instead of trusting it, and says what to do when it is violated — so if a
 * future test class in an earlier-sorting package ever grants `POST_NOTIFICATIONS`, this fails
 * loudly rather than quietly stopping to test the dialog.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CoreFlowE2ETest {

    /** Empty rather than `createAndroidComposeRule<MainActivity>()`: a rule-launched Activity comes
     *  up before `@Before` runs, so the database would still hold the previous test's habits and
     *  the first Today emission would be built from them. Launching by hand in each test keeps
     *  "clean state, then start the app" in that order, and lets a test relaunch mid-way. */
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private lateinit var fixture: CoreFlowTestFixture
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        fixture = CoreFlowTestFixture(context)
        runBlocking { fixture.reset() }
    }

    @After
    fun tearDown() {
        scenario?.close()
        fixture.close()
    }

    /**
     * The corrected `BLOCKED`-reachability scenario (design.md §2.2): a denial is one recorded ask
     * plus no grant, reachable in a single instrumented step — not the "two denials, unreachable on
     * the matrix" claim `specs/onboarding/spec.md` carried before this change corrected it.
     *
     * Walks a fresh install through onboarding, denies the real system dialog on screen 2, finishes
     * onboarding (landing on the seeded habit editor per design.md §5.1), backs out to Today and
     * confirms its banner offers the settings deep link, then relaunches the app and confirms
     * onboarding does not reappear — design.md §5.3's "back from the seeded editor entry reaches
     * Today" and §9's "onboarding never repeats" made concrete in one run.
     */
    @Test
    fun a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings() {
        assumeTrue(
            "POST_NOTIFICATIONS does not exist below API $RUNTIME_PERMISSION_SDK; " +
                "a3ApiBelow33SkipsTheOnboardingPermissionScreenEntirely covers that leg",
            Build.VERSION.SDK_INT >= RUNTIME_PERMISSION_SDK,
        )
        assertFalse(
            "POST_NOTIFICATIONS is already granted, so no dialog can appear and this test would " +
                "prove nothing. Nothing may grant it before this class runs — see the class KDoc. " +
                "On a hand-run device, reinstall the app first; the matrix task always installs clean.",
            hasNotificationPermission(),
        )

        launchFirstRunApp()
        awaitText(string(R.string.onboarding_screen1_title))
        compose.onNodeWithText(string(R.string.onboarding_action_continue)).performClick()

        awaitText(string(R.string.onboarding_screen2_title))
        compose.onNodeWithText(string(R.string.onboarding_permission_should_request_action)).performClick()
        device.tapDenyOnTheSystemPermissionDialog()

        awaitText(string(R.string.onboarding_permission_blocked_body))
        assertFalse(
            "the dialog was denied, so POST_NOTIFICATIONS must still be missing",
            hasNotificationPermission(),
        )
        compose.onNodeWithText(string(R.string.onboarding_action_finish)).performClick()

        awaitContentDescription(string(R.string.action_back))
        compose.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        awaitText(string(R.string.today_notification_permission_open_settings))

        relaunchOnboardedApp()
        compose.onNodeWithText(string(R.string.onboarding_screen1_title)).assertDoesNotExist()
    }

    /**
     * The grant half of the deny/grant pair (design.md §8.3). The deny scenario above records the
     * "already asked" latch, so this scenario seeds it back to unasked first — restoring the app's
     * approximation to the system's real remaining-prompt state, which still permits one more
     * dialog after a single denial. That is the only place in the suite this happens, and the
     * design records why it is not cheating.
     */
    @Test
    fun a2AllowingTheOnboardingPromptLeavesTodayWithNoNotificationBanner() {
        assumeTrue(
            "POST_NOTIFICATIONS does not exist below API $RUNTIME_PERMISSION_SDK; " +
                "a3ApiBelow33SkipsTheOnboardingPermissionScreenEntirely covers that leg",
            Build.VERSION.SDK_INT >= RUNTIME_PERMISSION_SDK,
        )
        runBlocking { fixture.seedNotificationPermissionUnasked() }

        launchFirstRunApp()
        awaitText(string(R.string.onboarding_screen1_title))
        compose.onNodeWithText(string(R.string.onboarding_action_continue)).performClick()

        awaitText(string(R.string.onboarding_screen2_title))
        compose.onNodeWithText(string(R.string.onboarding_permission_should_request_action)).performClick()
        device.tapAllowOnTheSystemPermissionDialog()

        awaitText(string(R.string.onboarding_permission_granted_body))
        assertTrue(
            "the dialog was accepted, so the app must actually hold POST_NOTIFICATIONS",
            hasNotificationPermission(),
        )
        compose.onNodeWithText(string(R.string.onboarding_action_finish)).performClick()

        awaitContentDescription(string(R.string.action_back))
        compose.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        awaitText(string(R.string.today_title))
        compose.onNodeWithText(string(R.string.today_notification_permission_banner)).assertDoesNotExist()
    }

    /**
     * design.md §7's API 31-32 divergence, proven end to end: the permission screen never renders,
     * screen 1 is the last page so its primary action reads "Finish" rather than "Continue" — the
     * API-31 label trap `OnboardingUiStateTest` guards at the unit level — and Today shows no
     * banner once onboarding hands off, because the permission does not exist on this API level at
     * all.
     */
    @Test
    fun a3ApiBelow33SkipsTheOnboardingPermissionScreenEntirely() {
        assumeTrue(
            "This is the API 31-32 half of the permission boundary; API 33+ is covered by " +
                "a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings and " +
                "a2AllowingTheOnboardingPromptLeavesTodayWithNoNotificationBanner",
            Build.VERSION.SDK_INT < RUNTIME_PERMISSION_SDK,
        )

        launchFirstRunApp()
        awaitText(string(R.string.onboarding_screen1_title))
        compose.onNodeWithText(string(R.string.onboarding_screen2_title)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.onboarding_action_finish)).assertIsDisplayed()

        compose.onNodeWithText(string(R.string.onboarding_action_finish)).performClick()

        awaitContentDescription(string(R.string.action_back))
        compose.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        awaitText(string(R.string.today_title))
        compose.onNodeWithText(string(R.string.today_notification_permission_banner)).assertDoesNotExist()
        assertTrue(
            "Below API $RUNTIME_PERMISSION_SDK notifications are enabled with no runtime prompt",
            notificationManager.areNotificationsEnabled(),
        )
    }

    /**
     * Steps 2, 3 and 4: add a habit through the UI, see it on Today, have its reminder actually
     * arrive, and answer from the notification itself.
     *
     * `onboarding_done` is pre-seeded (design.md §8.4) — this test's own concern is the reminder
     * pipeline, not the gate, which [a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings]
     * and [a2AllowingTheOnboardingPromptLeavesTodayWithNoNotificationBanner] already cover.
     *
     * The answer half is included because the existing
     * `NotificationActionWiringInstrumentedTest` shape made it nearly free — the posted
     * `Notification`'s own `PendingIntent` is sent exactly as the system sends it on a tap — and
     * because it closes the loop the reminder opens: a reminder that cannot be answered is not a
     * delivered reminder.
     *
     * The `Entry` is asserted against the OCCURRENCE's date rather than today's. That is
     * origin-date crediting (design.md §9.1) working as specified, not a quirk of the test: the
     * occurrence driven here is the one furthest in the future, for the wall-clock reason
     * [CoreFlowTestFixture.latestArmedOccurrenceFor] documents, and an answer credits the day the
     * reminder was FOR.
     */
    @Test
    fun creatingAHabitThroughTheUiDeliversItsReminderAndRecordsTheAnswerTappedOnIt() {
        grantNotificationPermission()
        launchOnboardedApp()

        addHabitThroughTheUi(REMINDED_HABIT)
        relaunchOnboardedApp()
        awaitText(REMINDED_HABIT)

        val habit = runBlocking { fixture.requireHabitNamed(REMINDED_HABIT) }
        val occurrence = runBlocking { fixture.latestArmedOccurrenceFor(habit.id) }

        fixture.fireArmedAlarmFor(occurrence)
        val posted = fixture.awaitPostedNotification(occurrence.id.toInt())
        // reminder-response (MODIFIED): the title is the fixed, localized string; the habit name
        // now lives in the body, verbatim and never localized.
        assertEquals(
            string(R.string.notification_reminder_title),
            posted.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        )
        assertEquals(
            REMINDED_HABIT,
            posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )

        val yes = posted.notification.actions.first { it.title == string(R.string.notification_action_yes) }
        send(yes)

        val entry = fixture.awaitValue { runBlocking { fixture.entriesFor(habit.id).firstOrNull() } }
        assertNotNull("Tapping Yes must persist an Entry through AnswerWorker", entry)
        assertEquals(EntryStatus.COMPLETED.name, entry?.status)
        assertEquals("the answer credits the day the reminder was for", occurrence.scheduledDate, entry?.date)
        fixture.awaitNotificationCancelled(occurrence.id.toInt())
    }

    /**
     * Step 5: archive the habit through the UI, and see it gone from Today and out of the
     * schedule while its row and history survive.
     *
     * `onboarding_done` is pre-seeded (design.md §8.4), for the same reason as the reminder test
     * above.
     *
     * **Archiving is this app's REVERSIBLE removal gesture, not its only one.** Before this
     * change, this KDoc claimed "nothing anywhere deletes a habit row, by design" — that became
     * false once `HabitRepository.delete` shipped (habit-management: Habit Deletion, design.md
     * D1). The row survives archiving for a different reason: archiving preserves every record it
     * touches so the gesture can be undone, and un-archiving resumes reminders from exactly where
     * un-archival happens. So "gone from the database" is asserted where archiving actually
     * deletes: `OccurrencePlanner.replanAll`'s `cancelAllFor` cancels every alarm for the habit
     * and deletes its `reminder_occurrences` rows. The habit row, its schedule and its entries are
     * asserted to survive, archived and stamped — that is archiving's specified behaviour. The
     * counterpart below,
     * [deletingAHabitThroughTheUiRemovesItAndItsHistoryAndSilencesItsReminder], drives the
     * irreversible gesture and asserts the opposite: the row is gone.
     */
    @Test
    fun removingAHabitThroughTheUiTakesItOffTodayAndOutOfTheSchedule() {
        launchOnboardedApp()
        addHabitThroughTheUi(ARCHIVED_HABIT)
        val habit = runBlocking { fixture.requireHabitNamed(ARCHIVED_HABIT) }
        assertTrue(
            "the habit must be armed before removal, or its disappearance proves nothing",
            runBlocking { fixture.occurrencesFor(habit.id) }.isNotEmpty(),
        )

        // Only one active habit exists — setUp() cleared the rest — so this label is unambiguous.
        compose.onNodeWithText(string(R.string.habit_list_archive)).performClick()
        awaitTextGone(ARCHIVED_HABIT)

        // habit-list-back-navigation, proven with the REAL gesture on the real Activity, which is
        // the only place it can be: before this, back here reached MainActivity's default and
        // finished it, so a user who came to manage a habit had the app close on them instead of
        // returning to Today. This test is already standing on the list with an onboarded app, so
        // the proof costs one keypress rather than another launch.
        device.pressBack()
        awaitText(string(R.string.today_title))

        relaunchOnboardedApp()
        compose.onNodeWithText(ARCHIVED_HABIT).assertDoesNotExist()
        awaitText(string(R.string.today_empty))

        val archived = runBlocking { fixture.requireHabitNamed(ARCHIVED_HABIT) }
        assertTrue("archiving is this app's reversible removal gesture", archived.archived)
        assertNotNull("archiving stamps the date it happened", archived.archivedAt)
        assertTrue(
            "archiving must clear the habit's scheduling rows, not merely hide the habit",
            runBlocking { fixture.occurrencesFor(habit.id) }.isEmpty(),
        )
    }

    /**
     * Step 5b: delete the habit through the UI, and see it and its history gone from the
     * database, with no reminder ever arriving for it again.
     *
     * `onboarding_done` is pre-seeded (design.md §8.4), same reason as the reminder test above.
     * [fixture.latestArmedOccurrenceFor] is read BEFORE deleting — after deletion there is no row
     * left to read.
     *
     * **Division of proof (design.md's Testing Strategy).** This test proves only the SECOND line
     * of defence: driving the exact broadcast `AlarmManager` would have sent finds no occurrence
     * and no habit, so `ReminderFireHandler` returns without posting anything
     * ([fixture.assertNoNotificationPosted]). It does NOT prove `AlarmScheduler.cancel` was called
     * — that the FIRST line of defence actually ran is proven separately, by mock verification at
     * the repository layer, in
     * `HabitRepositoryDeleteTest.deletingAHabitCancelsEveryArmedOccurrenceItHad` (task 4.2). A
     * single test claiming both would be false coverage.
     */
    @Test
    fun deletingAHabitThroughTheUiRemovesItAndItsHistoryAndSilencesItsReminder() {
        launchOnboardedApp()
        addHabitThroughTheUi(DELETED_HABIT)
        val habit = runBlocking { fixture.requireHabitNamed(DELETED_HABIT) }
        val occurrence = runBlocking { fixture.latestArmedOccurrenceFor(habit.id) }

        // Only one active habit exists — setUp() cleared the rest — so these labels are unambiguous.
        compose.onNodeWithContentDescription(string(R.string.habit_list_more_options)).performClick()
        compose.onNodeWithText(string(R.string.habit_list_delete)).performClick()
        awaitText(string(R.string.habit_delete_dialog_confirm))
        compose.onNodeWithText(string(R.string.habit_delete_dialog_confirm)).performClick()
        awaitTextGone(DELETED_HABIT)

        relaunchOnboardedApp()
        compose.onNodeWithText(DELETED_HABIT).assertDoesNotExist()
        awaitText(string(R.string.today_empty))

        assertNull(
            "deleting a habit must remove its row, unlike archiving",
            runBlocking { fixture.habitNamed(DELETED_HABIT) },
        )
        assertTrue(
            "deleting a habit must remove every reminder occurrence it had",
            runBlocking { fixture.occurrencesFor(habit.id) }.isEmpty(),
        )
        assertTrue(
            "deleting a habit must remove every entry it had",
            runBlocking { fixture.entriesFor(habit.id) }.isEmpty(),
        )

        fixture.fireArmedAlarmFor(occurrence)
        fixture.assertNoNotificationPosted(occurrence.id.toInt())
    }

    /**
     * Today -> Manage habits -> "+" -> name -> "Remind me" -> Save, ending on the habit list with
     * the new habit visible. The reminder switch is what makes the habit worth reminding about:
     * `OccurrencePlanner` plans nothing for a habit with no enabled slot, so without this toggle
     * there would be no occurrence for the reminder test to fire.
     *
     * The switch is addressed as "the one toggleable node" rather than by its label, because unlike
     * the habit list's archived filter this row is not `toggleable` as a whole — tapping its label
     * does nothing, and a test that tapped the label would pass through and save a habit with no
     * reminder.
     */
    private fun addHabitThroughTheUi(name: String) {
        awaitText(string(R.string.today_manage_habits))
        compose.onNodeWithText(string(R.string.today_manage_habits)).performClick()

        awaitContentDescription(string(R.string.habit_list_add_habit))
        compose.onNodeWithContentDescription(string(R.string.habit_list_add_habit)).performClick()

        awaitText(string(R.string.habit_editor_name_label))
        compose.onNodeWithText(string(R.string.habit_editor_name_label)).performTextInput(name)
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.habit_editor_save)).performScrollTo().performClick()

        awaitText(name)
    }

    /** For a fresh-install world: onboarding is un-onboarded (design.md §8.2's `reset()` default),
     *  so the gate renders its first page rather than [MainActivity]'s post-onboarding app. */
    private fun launchFirstRunApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText(string(R.string.onboarding_screen1_title))
    }

    /** For a world that has already onboarded: seeds the flag durably before launch — `edit`'s
     *  suspend contract does not return until the write commits, so the gate's first read is
     *  guaranteed to observe it (design.md §8.3) — then awaits the post-onboarding app directly. No
     *  default `launchApp()` exists (design.md §8.4): every call site states which world it is in. */
    private fun launchOnboardedApp() {
        runBlocking { fixture.seedOnboardingDone() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText(string(R.string.today_title))
    }

    /** Re-opens the app on Today, which is where every caller needs to be next. The habit list can
     *  now be walked out of directly (habit-list-back-navigation, exercised in
     *  [removingAHabitThroughTheUiTakesItOffTodayAndOutOfTheSchedule]), so this is no longer the
     *  only way back — but a relaunch proves something the back arrow cannot: that what the UI just
     *  did survived the process, rather than only the composition. That is why these tests keep
     *  relaunching. Onboarding is already done in every caller, so it goes straight to the
     *  post-onboarding app. */
    private fun relaunchOnboardedApp() {
        scenario?.close()
        launchOnboardedApp()
    }

    /**
     * Grants `POST_NOTIFICATIONS` outright, and waits for `NotificationManager` to agree.
     *
     * This is the existing `NotificationPosterInstrumentedTest` shape and the same lesson: the
     * grant returns before the notification state reflects it, and posting into that window is
     * silently refused. The dialog route is covered by
     * [a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings] and
     * [a2AllowingTheOnboardingPromptLeavesTodayWithNoNotificationBanner]; repeating it here would
     * only make the reminder test fail for permission reasons rather than reminder reasons.
     */
    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < RUNTIME_PERMISSION_SDK) return
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val deadline = System.currentTimeMillis() + GRANT_TIMEOUT_MS
        while (!notificationManager.areNotificationsEnabled() && System.currentTimeMillis() < deadline) {
            Thread.sleep(GRANT_POLL_INTERVAL_MS)
        }
        assertTrue(
            "POST_NOTIFICATIONS was granted but notifications still read as disabled after " +
                "${GRANT_TIMEOUT_MS}ms; the reminder would be suppressed for a reason unrelated to " +
                "what this test asserts",
            notificationManager.areNotificationsEnabled(),
        )
    }

    /** Sends a posted action's real `PendingIntent` exactly as the system does on a tap, and blocks
     *  until `ActionReceiver.onReceive()` has run and returned — no arbitrary sleep. Same shape as
     *  `NotificationActionWiringInstrumentedTest`. */
    private fun send(action: Notification.Action) {
        val latch = CountDownLatch(1)
        action.actionIntent.send(context, 0, null, { _, _, _, _, _ -> latch.countDown() }, null)
        assertTrue(
            "PendingIntent.OnFinished never fired — the broadcast was not delivered",
            latch.await(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
    }

    private fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun string(resId: Int) = context.getString(resId)

    /** An idle composition is not the same as a rendered one: every screen here is fed by Room
     *  `Flow`s, so `performClick` on a node that has not arrived yet either misses it or hits a row
     *  whose state is still empty. The same discipline `TodayComposeTest` already documents. */
    private fun awaitText(label: String) {
        compose.waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTextGone(label: String) {
        compose.waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun awaitContentDescription(label: String) {
        compose.waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
