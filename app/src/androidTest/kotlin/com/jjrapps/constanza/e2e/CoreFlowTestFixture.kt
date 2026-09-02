package com.jjrapps.constanza.e2e

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.di.ReminderSettingsDataStoreEntryPoint
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.ReminderFireReceiver
import dagger.hilt.android.EntryPointAccessors

private const val POLL_TIMEOUT_MS = 15_000L
private const val POLL_INTERVAL_MS = 50L
private const val STATE_ARMED = "ARMED"

/**
 * Shared wiring for [CoreFlowE2ETest], in the same spirit as `HabitRepositoryTestFixture` and
 * `PortabilityTestFixture` — except for one deliberate difference that changes how it must be
 * used.
 *
 * **This fixture reads the app's REAL database file, not an in-memory one.** Every other
 * instrumented test in this module builds its own `Room.inMemoryDatabaseBuilder` and drives
 * production classes over that. `CoreFlowE2ETest` cannot: it drives the actual app through its
 * actual UI, so the rows it needs to inspect are the ones Hilt's singleton `AppDatabase` wrote.
 * The same `databaseBuilder`/`DATABASE_NAME` pairing `ImminentReminderSeed` already established is
 * reused here.
 *
 * Two consequences follow, and both are load bearing:
 *
 * 1. **This is a second Room instance over one file.** Room's invalidation tracker is per-instance
 *    and multi-instance invalidation is not enabled, so a write made here is NOT pushed to a `Flow`
 *    the app is already collecting. It IS seen by any query the app runs afterwards, because
 *    queries read SQLite rather than a cache. That is why [reset] is only ever called while no
 *    Activity is alive: the next `TodayViewModel` builds a fresh `Flow` whose first emission reads
 *    the cleaned database. Do not call it mid-test with the app on screen and expect the UI to
 *    react.
 * 2. **Every mutation a test asserts on goes through the UI, never through here.** [reset] clears
 *    the slate; the habit under test is created and archived by tapping the real screens, so the
 *    assertions are about the product's own write paths.
 *
 * No migration is registered, unlike `DatabaseModule`. That is correct rather than an omission: a
 * migration only runs on a version upgrade, and this instance either opens a file the app already
 * created at the current version or creates that file itself. There is no older schema for it to
 * ever meet.
 */
class CoreFlowTestFixture(private val context: Context) {

    val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** The app's own `reminder_settings` [DataStore], reached through [ReminderSettingsDataStoreEntryPoint]
     *  rather than a second `preferencesDataStore` delegate (design.md §8.1). */
    private val settings: DataStore<Preferences> =
        EntryPointAccessors.fromApplication(context, ReminderSettingsDataStoreEntryPoint::class.java)
            .reminderSettingsDataStore()

    /** Clears everything a previous test could have left behind. `habits` cascades through
     *  `schedules`, `reminder_slots`, `entries` and `reminder_occurrences` (design.md §8.1), so one
     *  statement empties the dataset. Alarms armed against the deleted occurrences survive in
     *  `AlarmManager`, which is harmless: `ReminderFireHandler` loads the occurrence first and
     *  returns when the row is gone.
     *
     *  The default is un-onboarded (design.md §8.2), which is the state a real clean install is in;
     *  a test that does not care about onboarding opts out by calling [seedOnboardingDone].
     *  Deliberately does NOT touch `requested_notification_permission` — granting
     *  `POST_NOTIFICATIONS` is a one-way door within an installation, and blanket-resetting that
     *  latch would desynchronise the app's approximation from the system's real state for whichever
     *  test ran next (design.md §8.2). */
    suspend fun reset() {
        database.habitDao().deleteAll()
        notificationManager.cancelAll()
        settings.edit { it[ReminderSettingsStore.ONBOARDING_DONE_KEY] = false }
    }

    /** Opts a test out of the onboarding gate entirely, so it can launch straight into
     *  [com.jjrapps.constanza.core.ui.MainActivity]'s post-onboarding app (design.md §8.1). `edit`
     *  does not return until the write is durable, so a caller that awaits this before
     *  `ActivityScenario.launch` is guaranteed the gate's first read observes it (design.md §8.3). */
    suspend fun seedOnboardingDone() {
        settings.edit { it[ReminderSettingsStore.ONBOARDING_DONE_KEY] = true }
    }

    /** Clears the "already asked" latch so a test can observe the real permission dialog a second
     *  time in one installation (design.md §8.3). This is the one deliberate exception to [reset]'s
     *  own rule of leaving that latch alone — used only by the scenario that needs the system's
     *  actual remaining-prompt budget rather than the app's conservative approximation of it. */
    suspend fun seedNotificationPermissionUnasked() {
        settings.edit { it[ReminderSettingsStore.REQUESTED_NOTIFICATION_PERMISSION_KEY] = false }
    }

    suspend fun habitNamed(name: String): HabitEntity? =
        database.habitDao().findAllSnapshot().firstOrNull { it.name == name }

    suspend fun requireHabitNamed(name: String): HabitEntity = requireNotNull(habitNamed(name)) {
        "No habit called '$name' is in the database. Habits present: " +
            database.habitDao().findAllSnapshot().map { it.name }
    }

    suspend fun occurrencesFor(habitId: Long): List<ReminderOccurrenceEntity> =
        database.reminderOccurrenceDao().findByHabitId(habitId)

    suspend fun entriesFor(habitId: Long): List<EntryEntity> = database.entryDao().findByHabitId(habitId)

    /**
     * The armed occurrence furthest in the future — with `OccurrencePlanner`'s 48h horizon that is
     * the one two days out.
     *
     * Picking the *latest* rather than today's is what keeps this test free of the wall clock.
     * Today's occurrence resolves to the slot's time of day, so for a habit saved with the editor's
     * default 08:00 reminder it is already in the past whenever the suite runs after breakfast:
     * `AlarmManager` then fires it on its own, immediately, and the occurrence leaves `ARMED`
     * before the test can drive it. The occurrence two days out cannot fire during a test run at
     * any hour, so [fireArmedAlarmFor] is guaranteed to be the thing that fires it.
     */
    suspend fun latestArmedOccurrenceFor(habitId: Long): ReminderOccurrenceEntity {
        val armed = occurrencesFor(habitId).filter { it.state == STATE_ARMED }
        return requireNotNull(armed.maxByOrNull { it.scheduledAtEpochMs }) {
            "Habit $habitId has no ARMED reminder occurrence. Occurrences present: " +
                occurrencesFor(habitId).map { "${it.id}:${it.scheduledDate}:${it.state}" }
        }
    }

    /**
     * Delivers exactly the broadcast `AlarmManager` delivers when the alarm goes off, so the
     * reminder arrives in milliseconds instead of days.
     *
     * **Why this and not `androidx.work.testing`.** The delay standing between "a habit exists" and
     * "a notification arrives" is an `AlarmManager` delay, not a `WorkManager` one:
     * `OccurrencePlanner` arms a `PendingIntent` for the slot's instant, and `WorkManager` only
     * enters the story after [ReminderFireReceiver] has already been woken. `TestDriver` has no
     * lever on an alarm — `setInitialDelayMet` can only advance work that is already enqueued, and
     * nothing is enqueued until the alarm fires. Worse, reaching for `TestDriver` means calling
     * `WorkManagerTestInitHelper.initializeTestWorkManager`, which replaces the process-wide
     * `WorkManager` that `ConstanzaApplication` configured with `HiltWorkerFactory` — and that
     * factory is precisely the wiring under test (see the manifest's removed `androidx.startup`
     * initializer and task 5.9's discovery). The test would swap out the thing it is supposed to
     * prove.
     *
     * So the clock is the only substitution made. Everything downstream of it is production: the
     * manifest-declared `ReminderFireReceiver`, the expedited unique work it enqueues, the real
     * `HiltWorkerFactory`, `ReminderFireHandler`'s re-evaluation against the real database, and
     * `NotificationPoster`. The intent below is byte-for-byte the one `AlarmScheduler` builds —
     * same component, same single extra under the same key.
     */
    fun fireArmedAlarmFor(occurrence: ReminderOccurrenceEntity) {
        val intent = Intent(context, ReminderFireReceiver::class.java)
        intent.putExtra(AlarmScheduler.EXTRA_OCCURRENCE_ID, occurrence.id)
        context.sendBroadcast(intent)
    }

    /**
     * `NotificationManager.notify` is a `oneway` Binder call, so a posted notification need not be
     * visible in `activeNotifications` by the time the poster returns — the same race
     * `NotificationPosterInstrumentedTest` and `NotificationActionWiringInstrumentedTest` already
     * pay for, with the same fix. The window is wider here because the post is several process
     * hops away (broadcast, then expedited work), so the bound is generous rather than tight.
     */
    fun awaitPostedNotification(notificationId: Int): StatusBarNotification {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var found = activeNotification(notificationId)
        while (found == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
            found = activeNotification(notificationId)
        }
        return requireNotNull(found) {
            "No notification with id $notificationId appeared within ${POLL_TIMEOUT_MS}ms. " +
                "Notifications currently posted: ${notificationManager.activeNotifications.map { it.id }}. " +
                "areNotificationsEnabled=${notificationManager.areNotificationsEnabled()}"
        }
    }

    /** The mirror image, for the answer path: `EntryWriter` cancels the notification as part of
     *  resolving the occurrence, and that cancellation is as asynchronous as the post was. */
    fun awaitNotificationCancelled(notificationId: Int) {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (activeNotification(notificationId) != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
        require(activeNotification(notificationId) == null) {
            "Notification $notificationId was still posted ${POLL_TIMEOUT_MS}ms after the answer " +
                "was sent; resolving an occurrence must cancel it."
        }
    }

    /** Polls a suspend read until it returns something, so a test never asserts against a write
     *  that the worker behind it has not finished yet. Returns `null` on timeout and lets the
     *  caller phrase the failure, since only the caller knows what it was waiting for. */
    fun <T : Any> awaitValue(read: () -> T?): T? {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var value = read()
        while (value == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS)
            value = read()
        }
        return value
    }

    fun close() = database.close()

    private fun activeNotification(notificationId: Int): StatusBarNotification? =
        notificationManager.activeNotifications.firstOrNull { it.id == notificationId }
}
