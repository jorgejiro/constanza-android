package com.jjrapps.constanza.seed

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.mapper.toEntity
import com.jjrapps.constanza.core.time.SystemTimeProvider
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import com.jjrapps.constanza.scheduling.SchedulingDaos
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** How far past the upcoming LOCAL midnight the seeded snooze expires. This is THE knob to change:
 *  it is what makes the snooze cross midnight while the occurrence keeps today's origin date. */
private const val SEED_SNOOZE_MINUTES_PAST_MIDNIGHT = 20L

/** How far ahead of "now" the slot's own reminder time is placed. It only has to stay in the future
 *  for the few milliseconds between planning and the snooze re-arm below, which replaces this
 *  alarm outright — so it is deliberately small. */
private const val SEED_LEAD_MINUTES = 3

/** The habit's name. Distinct from `ImminentReminderSeed`'s `Meditate` on purpose, so this fixture
 *  never collides with, deletes, or re-plans the habits that other on-device runs already seeded. */
private const val SEED_HABIT_NAME = "Stretch"

/** The habit's question, so a posted notification carries recognisable body text too. */
private const val SEED_HABIT_QUESTION = "Did you stretch today?"

/**
 * The state this fixture persists. The production vocabulary lives in `private const val STATE_*`
 * declarations that are not visible outside their files; this exact string is `STATE_SNOOZED` in
 * `reminding/SnoozeWorker.kt`, which is the sole source of truth for it. The column is a readable
 * `TEXT` value (see `ReminderOccurrenceEntity.state`), so no encoding is involved.
 */
private const val SEED_STATE_SNOOZED = "SNOOZED"

/**
 * Mirrors `WorkerConstantsModule`'s private `RESOLVE_DEADLINE_HOURS`, which Hilt injects into the
 * production [OccurrencePlanner] but which is not visible outside that module. It only widens the
 * occurrence's `resolveDeadlineMs`; [assertSnoozeWithinDeadline] then checks that the seeded
 * `snoozeUntil` sits inside it, which is what production's own clamp would otherwise enforce.
 */
private const val SEED_RESOLVE_DEADLINE_HOURS = 24L

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
private const val LAST_MINUTE_OF_DAY = MINUTES_PER_DAY - 1

/** A visible amber, so the seeded habit is also distinguishable from the teal `Meditate` seed. */
private const val SEED_COLOR_ARGB = 0xFFF57C00.toInt()

/** Grep this in logcat to read back everything the seed wrote. Deliberately distinct from
 *  `ConstanzaSeed` and `ConstanzaState`, so this fixture's output can be filtered on its own. */
private const val TAG = "ConstanzaSnoozeSeed"

/**
 * NOT A BEHAVIOURAL TEST — a manual, on-device seeding fixture that reproduces the exact state
 * design decision D3 protects: a LIVE SNOOZE THAT CROSSES MIDNIGHT.
 *
 * D3 credits a resolved reminder to the occurrence's ORIGIN DATE, not to the date on which the user
 * finally answers. The only way to observe that on a real device is to have a snoozed occurrence
 * whose `scheduledDate` is today but whose `snoozeUntil` lands after local midnight, and then let
 * the real midnight sweep run against it. This fixture seeds precisely that row and then gets out of
 * the way; it asserts nothing about what the sweep subsequently does.
 *
 * Same two trustworthiness properties as [ImminentReminderSeed]:
 *
 * 1. **The real database file.** [AppDatabase] is opened with `Room.databaseBuilder` and
 *    [AppDatabase.DATABASE_NAME], matching `core/di/DatabaseModule.provideAppDatabase` exactly —
 *    same name, same builder, no extra migrations or callbacks, because that module configures none.
 *    It is emphatically NOT `inMemoryDatabaseBuilder`.
 * 2. **The real production paths.** The habit, schedule and slot rows go in through the production
 *    DAOs and the production `Schedule.toEntity` mapper, and today's occurrence is planned by
 *    [OccurrencePlanner.replanAll] driving the real [AlarmScheduler] — this fixture never invents an
 *    occurrence row from nothing. The snooze itself then follows `SnoozeResponder.snooze`
 *    step for step: arm the real alarm at `snoozeUntil` on the SAME occurrence id (production
 *    re-arms on the same request code), persist `state`, `snoozeCount + 1`, `snoozeUntilEpochMs` and
 *    the `exact` result the scheduler actually returned.
 *
 * `SnoozeResponder` is not itself invoked because it reads the user's configured snooze duration
 * from `ReminderSettingsStore`; the whole point here is a duration measured from midnight, not from
 * now, which no setting can express.
 *
 * ## What it touches
 *
 * Only the habit named [SEED_HABIT_NAME]. A previous `Stretch` seed is cancelled through the real
 * [AlarmScheduler] and deleted (foreign-key cascade) before a fresh one is inserted, so re-running
 * cannot leave an orphan alarm behind. Habits seeded under any other name are never deleted.
 * [OccurrencePlanner.replanAll] is global by construction, so it does walk them — but it leaves any
 * non-`ARMED` occurrence untouched and re-plans an `ARMED` one to the identical instant, so an
 * in-flight measurement on another habit is not disturbed.
 *
 * ## Running it
 *
 * `:app:connectedDebugAndroidTest` cannot be used: it excludes this class via [SeedOnly] (see
 * `app/build.gradle.kts`), and it uninstalls both APKs when it finishes, which would destroy the
 * seeded data. Install the test APK, then instrument this class directly:
 *
 * ```
 * ./gradlew :app:installDebugAndroidTest
 * adb -s <serial> shell am instrument -w -r \
 *   -e class com.jjrapps.constanza.seed.LiveSnoozeAcrossMidnightSeed \
 *   com.jjrapps.constanza.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Passing no `notAnnotation` argument is what lets [SeedOnly] run. Read the seeded ids, the origin
 * date, the state and both renderings of `snoozeUntil` back with
 * `adb -s <serial> logcat -d -s ConstanzaSnoozeSeed`.
 */
@RunWith(AndroidJUnit4::class)
@SeedOnly
class LiveSnoozeAcrossMidnightSeed {

    @Test
    fun seedHabitWithLiveSnoozeCrossingMidnight() = runBlocking {
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
            val today = now.atZone(zone).toLocalDate()
            val habitId = insertSeedHabit(daos, now)
            val slotId = daos.reminderSlotDao.insert(
                ReminderSlotEntity(habitId = habitId, minuteOfDay = leadMinuteOfDay(now, zone), enabled = true),
            )

            // Production code, not this fixture, plans today's occurrence and arms its alarm.
            planner.replanAll()

            val planned = requirePlannedOccurrence(daos, habitId, slotId, today)
            val snoozed = snoozeAcrossMidnight(daos, alarmScheduler, planned, today, zone)

            reportSeed(habitId, slotId, snoozed, today, zone, now)
        } finally {
            database.close()
        }
    }

    /**
     * Cancels through the real [AlarmScheduler] before deleting, so a re-run cannot leave an alarm
     * armed against a deleted occurrence. Scoped to [SEED_HABIT_NAME] by name: every other habit in
     * the database is left exactly as it was found.
     */
    private suspend fun removePreviousSeeds(daos: SchedulingDaos, alarmScheduler: AlarmScheduler) {
        daos.habitDao.findAllSnapshot()
            .filter { it.name == SEED_HABIT_NAME }
            .forEach { habit ->
                daos.reminderOccurrenceDao.findByHabitId(habit.id).forEach { alarmScheduler.cancel(it.id) }
                daos.habitDao.deleteById(habit.id)
                Log.i(TAG, "Removed a previously seeded habit: id=${habit.id} name=\"${habit.name}\"")
            }
    }

    /**
     * [OccurrencePlanner] resolves a slot's `minuteOfDay` against the calendar date, so a time in
     * the past would arm an alarm that fires instantly and could flip the row to `FIRED` underneath
     * the snooze written below. The lead is therefore kept in the future and clamped to the last
     * minute of today rather than rolling into tomorrow; a run started in the final minute of the
     * day cannot satisfy that and fails loudly instead of seeding something misleading.
     */
    private fun leadMinuteOfDay(now: Instant, zone: ZoneId): Int {
        val local = now.atZone(zone)
        val currentMinuteOfDay = local.hour * MINUTES_PER_HOUR + local.minute
        val minuteOfDay = minOf(currentMinuteOfDay + SEED_LEAD_MINUTES, LAST_MINUTE_OF_DAY)
        assertTrue(
            "Local time is $local: there is no remaining minute of today to place the slot in. " +
                "Re-run after midnight, which is also when today's origin date changes.",
            minuteOfDay > currentMinuteOfDay,
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

    /** Confirms the production planner actually planned today before anything is snoozed. */
    private suspend fun requirePlannedOccurrence(
        daos: SchedulingDaos,
        habitId: Long,
        slotId: Long,
        today: LocalDate,
    ): ReminderOccurrenceEntity {
        val occurrence = daos.reminderOccurrenceDao.findByHabitSlotDate(habitId, slotId, today.toString())
        assertNotNull("No occurrence was planned for $today — the seed did not land.", occurrence)
        val planned = requireNotNull(occurrence)
        assertEquals("ARMED", planned.state)
        return planned
    }

    /**
     * Follows `SnoozeResponder.snooze` step for step, with `snoozeUntil` measured from the upcoming
     * local midnight instead of from a configured duration. `scheduledDate` is carried over
     * untouched: keeping today's origin date on a row that will only be answerable tomorrow is the
     * entire point of the fixture.
     */
    private suspend fun snoozeAcrossMidnight(
        daos: SchedulingDaos,
        alarmScheduler: AlarmScheduler,
        planned: ReminderOccurrenceEntity,
        today: LocalDate,
        zone: ZoneId,
    ): ReminderOccurrenceEntity {
        val snoozeUntil = localMidnightAfter(today, zone)
            .plusSeconds(SEED_SNOOZE_MINUTES_PAST_MIDNIGHT * SECONDS_PER_MINUTE)
        assertSnoozeWithinDeadline(planned, snoozeUntil, zone)
        // Same occurrence id, hence the same PendingIntent request code production re-arms on: this
        // replaces the pre-midnight alarm rather than adding a second one.
        val exact = alarmScheduler.schedule(planned.id, snoozeUntil.toEpochMilli())
        val snoozed = planned.copy(
            state = SEED_STATE_SNOOZED,
            snoozeCount = planned.snoozeCount + 1,
            snoozeUntilEpochMs = snoozeUntil.toEpochMilli(),
            exact = exact,
        )
        daos.reminderOccurrenceDao.upsert(snoozed)
        assertEquals(
            "scheduledDate must stay on today's origin date.",
            today.toString(),
            snoozed.scheduledDate,
        )
        return snoozed
    }

    /**
     * Production clamps `snoozeUntil` to `resolveDeadlineMs`. Rather than reimplement that clamp,
     * this fails loudly if the seeded time would have been clamped — a clamped seed would not cross
     * midnight, which would silently invalidate the whole scenario.
     */
    private fun assertSnoozeWithinDeadline(
        planned: ReminderOccurrenceEntity,
        snoozeUntil: Instant,
        zone: ZoneId,
    ) {
        val deadline = Instant.ofEpochMilli(planned.resolveDeadlineMs)
        assertTrue(
            "snoozeUntil ${snoozeUntil.atZone(zone)} is past resolveDeadline ${deadline.atZone(zone)}; " +
                "production would clamp it and the snooze would no longer cross midnight.",
            !snoozeUntil.isAfter(deadline),
        )
    }

    private fun reportSeed(
        habitId: Long,
        slotId: Long,
        snoozed: ReminderOccurrenceEntity,
        today: LocalDate,
        zone: ZoneId,
        now: Instant,
    ) {
        val midnight = localMidnightAfter(today, zone)
        val snoozeUntilMs = requireNotNull(snoozed.snoozeUntilEpochMs)
        Log.i(TAG, "SEEDED habitId=$habitId slotId=$slotId name=\"$SEED_HABIT_NAME\" zone=$zone")
        Log.i(TAG, "SEEDED now=${epochAndLocalTime(now.toEpochMilli(), zone)}")
        Log.i(
            TAG,
            "SEEDED occurrenceId=${snoozed.id} scheduledDate=${snoozed.scheduledDate} " +
                "state=${snoozed.state} snoozeCount=${snoozed.snoozeCount} exact=${snoozed.exact}",
        )
        Log.i(TAG, "SEEDED crossingMidnight=${epochAndLocalTime(midnight.toEpochMilli(), zone)}")
        Log.i(
            TAG,
            "SEEDED snoozeUntil=${epochAndLocalTime(snoozeUntilMs, zone)} " +
                "(+$SEED_SNOOZE_MINUTES_PAST_MIDNIGHT min past midnight, " +
                "crossesMidnight=${snoozeUntilMs > midnight.toEpochMilli()})",
        )
        Log.i(
            TAG,
            "SEEDED scheduledAt=${epochAndLocalTime(snoozed.scheduledAtEpochMs, zone)} " +
                "resolveDeadline=${epochAndLocalTime(snoozed.resolveDeadlineMs, zone)}",
        )
    }
}

/** The next local midnight after [today] — the instant the seeded snooze has to outlive. */
private fun localMidnightAfter(today: LocalDate, zone: ZoneId): Instant =
    today.plusDays(1).atStartOfDay(zone).toInstant()

/** Both representations on every timestamp, matching [DatabaseStateReport]: the epoch millis a
 *  reader can compare against an alarm dump, and the local time that needs no arithmetic. */
private fun epochAndLocalTime(epochMs: Long, zone: ZoneId): String =
    "${epochMs}ms (${Instant.ofEpochMilli(epochMs).atZone(zone)})"
