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
import com.jjrapps.constanza.reminding.NotificationPermission
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
private const val REMOVED_HABIT = "Stretch"

/**
 * The device-free core-flow proof: on an emulator with nothing plugged in, the app grants the
 * notification permission through the REAL system dialog, takes on a habit through its own UI,
 * delivers that habit's reminder as a real posted notification, records the answer tapped on it,
 * and lets the habit be removed again — each step driven and observed the way a person would
 * cause it, not by calling into a repository.
 *
 * See `openspec/config.yaml`'s "Device-free verification" convention for the standing requirement
 * this class exists to satisfy, the exact Gradle command that runs it on both API levels, and —
 * stated plainly there rather than implied here — what a green run does NOT prove about a physical
 * Galaxy S25.
 *
 * ## What is real and what is substituted
 *
 * Real: Hilt's own object graph, the singleton `AppDatabase` and its file, `MainActivity` and every
 * Compose screen, the `com.android.permissioncontroller` dialog, the manifest-declared
 * `ReminderFireReceiver` and `ActionReceiver`, the process-wide `WorkManager` that
 * `ConstanzaApplication` configured with `HiltWorkerFactory`, and `NotificationPoster` posting to
 * the actual `NotificationManager`.
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
 * to Process crashed". The two permission tests therefore have to observe the ungranted state
 * before anything grants it, and their names sort first (`al` < `ap` < `c` < `r`) so they do.
 *
 * The same one-way door applies across classes. `NotificationPosterInstrumentedTest` and
 * `NotificationActionWiringInstrumentedTest` also grant the permission, and they are the only other
 * classes in this module that do; both live in `com.jjrapps.constanza.reminding`, which the runner
 * reaches after `com.jjrapps.constanza.e2e` because dex stores class definitions ordered by type
 * descriptor. [allowingNotificationsThroughTheRealSystemDialogClearsTheTodayBanner] asserts that
 * precondition instead of trusting it, and says what to do when it is violated — so if a future
 * test class in an earlier-sorting package ever grants `POST_NOTIFICATIONS`, this fails loudly
 * rather than quietly stopping to test the dialog.
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
     * Step 1, API 33+: start denied, and reach granted by tapping what a person taps.
     *
     * The banner's action is asserted to be "Allow" and not "Open settings" before it is clicked.
     * That is not cosmetic — the two labels are the visible difference between `SHOULD_REQUEST` and
     * `BLOCKED`, and only the first leads to the system dialog. Checking it turns "the dialog never
     * appeared" into a diagnosis one line earlier than the UiAutomator timeout would.
     */
    @Test
    fun allowingNotificationsThroughTheRealSystemDialogClearsTheTodayBanner() {
        assumeTrue(
            "POST_NOTIFICATIONS does not exist below API $RUNTIME_PERMISSION_SDK; the API 31 leg " +
                "of the matrix covers that half in apiBelow33ShowsNoNotificationBanner...",
            Build.VERSION.SDK_INT >= RUNTIME_PERMISSION_SDK,
        )
        assertFalse(
            "POST_NOTIFICATIONS is already granted, so no dialog can appear and this test would " +
                "prove nothing. Nothing may grant it before this class runs — see the class KDoc. " +
                "On a hand-run device, reinstall the app first; the matrix task always installs clean.",
            hasNotificationPermission(),
        )

        launchApp()
        awaitText(string(R.string.today_notification_permission_banner))
        compose.onNodeWithText(string(R.string.today_notification_permission_allow)).assertIsDisplayed()

        compose.onNodeWithText(string(R.string.today_notification_permission_allow)).performClick()
        device.tapAllowOnTheSystemPermissionDialog()

        awaitTextGone(string(R.string.today_notification_permission_banner))
        assertTrue(
            "The dialog was accepted, so the app must actually hold POST_NOTIFICATIONS",
            hasNotificationPermission(),
        )
        assertTrue(
            "A granted POST_NOTIFICATIONS must leave notifications enabled",
            notificationManager.areNotificationsEnabled(),
        )
    }

    /**
     * Step 1, inverted for API 31-32: there is no runtime permission here, so the banner must never
     * render — `NotificationPermission.decide` answers `NOT_APPLICABLE` and
     * `TodayBanners.needsBanner()` excludes it.
     *
     * This is the reason API 31 is in the matrix at all. Asserting the decision AND the absence of
     * the banner together is deliberate: the decision alone is already covered by a JVM unit test,
     * and the absence alone would also pass if the screen simply never rendered.
     */
    @Test
    fun apiBelow33ShowsNoNotificationBannerBecauseThePermissionDoesNotExist() {
        assumeTrue(
            "This is the API 31-32 half of the permission boundary; API 33+ is covered by " +
                "allowingNotificationsThroughTheRealSystemDialogClearsTheTodayBanner",
            Build.VERSION.SDK_INT < RUNTIME_PERMISSION_SDK,
        )
        assertEquals(
            NotificationPermissionDecision.NOT_APPLICABLE,
            NotificationPermission(context).decide(hasRequestedBefore = false),
        )

        launchApp()

        compose.onNodeWithText(string(R.string.today_notification_permission_banner)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.today_notification_permission_allow)).assertDoesNotExist()
        assertTrue(
            "Below API $RUNTIME_PERMISSION_SDK notifications are enabled with no runtime prompt",
            notificationManager.areNotificationsEnabled(),
        )
    }

    /**
     * Steps 2, 3 and 4: add a habit through the UI, see it on Today, have its reminder actually
     * arrive, and answer from the notification itself.
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
        launchApp()

        addHabitThroughTheUi(REMINDED_HABIT)
        relaunchApp()
        awaitText(REMINDED_HABIT)

        val habit = runBlocking { fixture.requireHabitNamed(REMINDED_HABIT) }
        val occurrence = runBlocking { fixture.latestArmedOccurrenceFor(habit.id) }

        fixture.fireArmedAlarmFor(occurrence)
        val posted = fixture.awaitPostedNotification(occurrence.id.toInt())
        assertEquals(
            REMINDED_HABIT,
            posted.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
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
     * Step 5: remove the habit through the UI, and see it gone from Today and from the database.
     *
     * **Archive IS this app's removal gesture; there is no hard delete.** `HabitRepository` exposes
     * `setArchived` and `deleteSlot`, and the habit list offers "Archive"/"Un-archive" — nothing
     * anywhere deletes a habit row, by design, because a deleted habit would take its history with
     * it. So "gone from the database" is asserted where the product actually deletes: archiving
     * runs `OccurrencePlanner.replanAll`, whose `cancelAllFor` cancels every alarm for the habit
     * and deletes its `reminder_occurrences` rows. The habit row itself is asserted to survive,
     * archived and stamped — that is the specified behaviour, and a test that demanded its
     * disappearance would be asserting a feature this app deliberately does not have.
     */
    @Test
    fun removingAHabitThroughTheUiTakesItOffTodayAndOutOfTheSchedule() {
        launchApp()
        addHabitThroughTheUi(REMOVED_HABIT)
        val habit = runBlocking { fixture.requireHabitNamed(REMOVED_HABIT) }
        assertTrue(
            "the habit must be armed before removal, or its disappearance proves nothing",
            runBlocking { fixture.occurrencesFor(habit.id) }.isNotEmpty(),
        )

        // Only one active habit exists — setUp() cleared the rest — so this label is unambiguous.
        compose.onNodeWithText(string(R.string.habit_list_archive)).performClick()
        awaitTextGone(REMOVED_HABIT)

        relaunchApp()
        compose.onNodeWithText(REMOVED_HABIT).assertDoesNotExist()
        awaitText(string(R.string.today_empty))

        val archived = runBlocking { fixture.requireHabitNamed(REMOVED_HABIT) }
        assertTrue("archiving is this app's removal gesture", archived.archived)
        assertNotNull("archiving stamps the date it happened", archived.archivedAt)
        assertTrue(
            "archiving must clear the habit's scheduling rows, not merely hide the habit",
            runBlocking { fixture.occurrencesFor(habit.id) }.isEmpty(),
        )
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

    private fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText(string(R.string.today_title))
    }

    /** The habit list has no route back to Today — `MainActivity` hoists a one-way `ConstanzaRoute`
     *  and registers no `BackHandler` — so re-opening the app is how a person gets back there after
     *  creating a habit, and it is what this does. */
    private fun relaunchApp() {
        scenario?.close()
        launchApp()
    }

    /**
     * Grants `POST_NOTIFICATIONS` outright, and waits for `NotificationManager` to agree.
     *
     * This is the existing `NotificationPosterInstrumentedTest` shape and the same lesson: the
     * grant returns before the notification state reflects it, and posting into that window is
     * silently refused. The dialog route is covered by
     * [allowingNotificationsThroughTheRealSystemDialogClearsTheTodayBanner]; repeating it here
     * would only make the reminder test fail for permission reasons rather than reminder reasons.
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
