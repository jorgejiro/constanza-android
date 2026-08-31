package com.jjrapps.constanza.seed

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.mapper.toEntity
import com.jjrapps.constanza.core.time.SystemTimeProvider
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import com.jjrapps.constanza.scheduling.SchedulingDaos
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** How far into the future the seeded reminder fires. Near enough for a human to watch it fire,
 *  far enough that it cannot fire mid-seed. This is THE knob to change. */
private const val SEED_LEAD_MINUTES = 4

/** The habit's name, chosen to be unmistakable in `dumpsys`, logcat and notification inspection,
 *  and matching the name this project's other on-device checks already use. */
private const val SEED_HABIT_NAME = "Meditate"

/** The habit's question, so a posted notification carries recognisable body text too. */
private const val SEED_HABIT_QUESTION = "Did you meditate today?"

/**
 * Mirrors `WorkerConstantsModule`'s private `RESOLVE_DEADLINE_HOURS`, which Hilt injects into the
 * production [OccurrencePlanner] but which is not visible outside that module. It only widens the
 * occurrence's `resolveDeadlineMs`; it has no effect whatsoever on the fire time being seeded.
 */
private const val SEED_RESOLVE_DEADLINE_HOURS = 24L

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/** A visible teal, so the seeded habit is also identifiable in any future UI. */
private const val SEED_COLOR_ARGB = 0xFF00897B.toInt()

/** Grep this in logcat to read back everything the seed wrote. */
private const val TAG = "ConstanzaSeed"

/**
 * NOT A BEHAVIOURAL TEST — a manual, on-device seeding fixture for the API 37 reminder-delivery
 * matrix (task G.1).
 *
 * `MainActivity` renders an empty `Box`; habit CRUD is work unit 6a and does not exist yet, so
 * there is no product path that creates a habit. This fixture supplies one, writing to the app's
 * REAL database file and letting production code arm the real alarm, so the matrix observes exactly
 * what a user's action will produce once unit 6a lands. It adds no production code.
 *
 * Two properties make it trustworthy as matrix input:
 *
 * 1. **The real database file.** [AppDatabase] is opened with `Room.databaseBuilder` and
 *    [AppDatabase.DATABASE_NAME], matching `core/di/DatabaseModule.provideAppDatabase` exactly —
 *    same name, same builder, no extra migrations or callbacks, because that module configures
 *    none. It is emphatically NOT `inMemoryDatabaseBuilder`, which every other instrumented test in
 *    this module uses.
 * 2. **The real arming path.** The alarm is armed by [OccurrencePlanner.replanAll] driving the real
 *    [AlarmScheduler]; this fixture never constructs a `ReminderOccurrenceEntity` for the alarm and
 *    never touches `AlarmManager` itself. Whether the alarm ends up exact or degraded to an inexact
 *    window is therefore decided by production code re-checking
 *    `AlarmManager.canScheduleExactAlarms()`, and the outcome is persisted on the row as `exact`.
 *
 * The habit and schedule rows are written through the production DAOs and the production
 * `Schedule.toEntity` mapper. They are NOT written through `HabitRepository`, which today exposes
 * only `deleteSlot` — habit creation lands there in work unit 6a. When it does, this fixture should
 * be switched over to it.
 *
 * ## Running it
 *
 * `:app:connectedDebugAndroidTest` cannot be used: it excludes this class via [SeedOnly] (see
 * `app/build.gradle.kts`), and it uninstalls both APKs when it finishes, which would destroy the
 * seeded data. Install the two APKs once, then instrument this class directly:
 *
 * ```
 * ./gradlew :app:installDebug :app:installDebugAndroidTest
 * adb -s <serial> shell am instrument -w -r \
 *   -e class com.jjrapps.constanza.seed.ImminentReminderSeed \
 *   com.jjrapps.constanza.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Passing no `notAnnotation` argument is what lets [SeedOnly] run. The seeded ids and the exact
 * fire time are logged under the `ConstanzaSeed` logcat tag.
 *
 * Re-running is safe and idempotent: any previously seeded habit of the same name has its alarms
 * cancelled through the real [AlarmScheduler] and its rows deleted (foreign-key cascade) before a
 * fresh habit is inserted, so the device is never left with an orphan alarm pointing at a row that
 * no longer exists.
 */
@RunWith(AndroidJUnit4::class)
@SeedOnly
class ImminentReminderSeed {

    @Test
    fun seedHabitWithImminentArmedReminder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Matches DatabaseModule.provideAppDatabase exactly: same name, same builder, no
        // migrations and no callbacks, because that module adds none.
        val database = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()
        try {
            val timeProvider = SystemTimeProvider()
            val alarmScheduler = AlarmScheduler(context.getSystemService(AlarmManager::class.java), context)
            val daos = SchedulingDaos(
                habitDao = database.habitDao(),
                scheduleDao = database.scheduleDao(),
                reminderSlotDao = database.reminderSlotDao(),
                reminderOccurrenceDao = database.reminderOccurrenceDao(),
            )
            val planner = OccurrencePlanner(daos, alarmScheduler, timeProvider, SEED_RESOLVE_DEADLINE_HOURS)

            removePreviousSeeds(daos, alarmScheduler)

            val now = timeProvider.now()
            val zone = timeProvider.zone()
            val minuteOfDay = imminentMinuteOfDay(now, zone)
            val habitId = insertSeedHabit(daos, now)
            val slotId = daos.reminderSlotDao.insert(
                ReminderSlotEntity(habitId = habitId, minuteOfDay = minuteOfDay, enabled = true),
            )

            // The whole point of the fixture: production code, not this fixture, arms the alarm.
            planner.replanAll()

            reportSeed(daos, habitId, slotId, minuteOfDay, now, zone)
        } finally {
            database.close()
        }
    }

    /**
     * Cancels through the real [AlarmScheduler] before deleting, so a re-run cannot leave an alarm
     * armed against a deleted occurrence — [OccurrencePlanner] only ever cancels alarms it can
     * still see rows for, and the rows go away with the habit via foreign-key cascade.
     */
    private suspend fun removePreviousSeeds(daos: SchedulingDaos, alarmScheduler: AlarmScheduler) {
        daos.habitDao.findAllSnapshot()
            .filter { it.name == SEED_HABIT_NAME }
            .forEach { habit ->
                daos.reminderOccurrenceDao.findByHabitId(habit.id).forEach { alarmScheduler.cancel(it.id) }
                daos.habitDao.deleteById(habit.id)
                Log.i(TAG, "Removed a previously seeded habit: id=${habit.id}")
            }
    }

    /**
     * [OccurrencePlanner] resolves a slot's `minuteOfDay` against the calendar date, so the seed
     * cannot straddle midnight: a lead time that rolls past 23:59 would place today's occurrence in
     * the past and fire it instantly. That is reported as a hard failure rather than silently
     * clamped, since a fixture that quietly fires during seeding is worthless as matrix input.
     */
    private fun imminentMinuteOfDay(now: Instant, zone: ZoneId): Int {
        val local = now.atZone(zone)
        val minuteOfDay = local.hour * MINUTES_PER_HOUR + local.minute + SEED_LEAD_MINUTES
        assertTrue(
            "Local time is $local: a $SEED_LEAD_MINUTES-minute lead would roll past midnight, " +
                "which would schedule today's occurrence in the past. Re-run after midnight.",
            minuteOfDay < MINUTES_PER_DAY,
        )
        return minuteOfDay
    }

    private suspend fun insertSeedHabit(daos: SchedulingDaos, now: Instant): Long {
        val habitId = daos.habitDao.insert(
            HabitEntity(
                name = SEED_HABIT_NAME,
                question = SEED_HABIT_QUESTION,
                colorArgb = SEED_COLOR_ARGB,
                notes = null,
                archived = false,
                archivedAt = null,
                createdAt = now.toString(),
                sortOrder = 0,
            ),
        )
        // The production mapper, so the persisted schedule row is byte-for-byte what the app writes.
        daos.scheduleDao.upsert(Schedule.Daily().toEntity(habitId))
        return habitId
    }

    /**
     * Confirms only that the seed landed — that an armed occurrence exists for today at a future
     * instant. It deliberately asserts nothing about product behaviour beyond that.
     */
    private suspend fun reportSeed(
        daos: SchedulingDaos,
        habitId: Long,
        slotId: Long,
        minuteOfDay: Int,
        now: Instant,
        zone: ZoneId,
    ) {
        val today = now.atZone(zone).toLocalDate().toString()
        val occurrence = daos.reminderOccurrenceDao.findByHabitSlotDate(habitId, slotId, today)
        assertNotNull("No occurrence was planned for $today — the seed did not land.", occurrence)
        val planned = requireNotNull(occurrence)
        assertEquals("ARMED", planned.state)
        assertTrue(
            "Occurrence ${planned.id} is scheduled at ${planned.scheduledAtEpochMs}, " +
                "which is not in the future relative to ${now.toEpochMilli()}.",
            planned.scheduledAtEpochMs > now.toEpochMilli(),
        )
        val fireAt = Instant.ofEpochMilli(planned.scheduledAtEpochMs)
        Log.i(TAG, "SEEDED habitId=$habitId slotId=$slotId minuteOfDay=$minuteOfDay zone=$zone")
        Log.i(
            TAG,
            "SEEDED occurrenceId=${planned.id} (notification id AND PendingIntent request code) " +
                "state=${planned.state} exact=${planned.exact}",
        )
        Log.i(TAG, "SEEDED fireAt=$fireAt local=${fireAt.atZone(zone)} epochMs=${planned.scheduledAtEpochMs}")
        daos.reminderOccurrenceDao.findByHabitId(habitId).forEach {
            Log.i(
                TAG,
                "OCCURRENCE id=${it.id} date=${it.scheduledDate} state=${it.state} exact=${it.exact} " +
                    "at=${Instant.ofEpochMilli(it.scheduledAtEpochMs).atZone(zone)}",
            )
        }
    }
}
