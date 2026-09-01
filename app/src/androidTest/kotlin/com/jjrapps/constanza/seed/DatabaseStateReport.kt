package com.jjrapps.constanza.seed

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import com.jjrapps.constanza.core.time.SystemTimeProvider
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Grep this in logcat to read back the whole report. Deliberately distinct from `ConstanzaSeed`
 *  so a state dump can be filtered without pulling in seeding output. */
private const val TAG = "ConstanzaState"

/** Unmistakable delimiters, so a reader can tell a truncated logcat buffer from a complete dump.
 *  If [END] is absent, the report was cut off and must not be read as an authoritative state. */
private const val REPORT_BEGIN = "===== STATE REPORT BEGIN ====="
private const val REPORT_END = "===== STATE REPORT END ====="

/** Printed for a table that has no rows at all. An empty table MUST produce a visible line: a
 *  silent absence is indistinguishable from a query that never ran, and reading "no output" as a
 *  verified negative is exactly the mistake this fixture exists to prevent. */
private const val NO_ROWS = "NONE"

/** Printed for a nullable timestamp column that holds SQL `NULL`. */
private const val NO_VALUE = "none"

/**
 * NOT A BEHAVIOURAL TEST — a manual, on-device, **strictly read-only** state-reporting fixture for
 * the API 37 reminder-delivery matrix.
 *
 * The Pixel 10 used for that matrix ships no `sqlite3` binary and is not rooted, so the app's
 * database cannot be inspected with `adb shell sqlite3` or pulled with `adb pull`. This fixture is
 * the substitute: it runs inside the app's own UID, opens the app's REAL on-disk database, and
 * writes every row to logcat under the [TAG] tag, so a human can read the app's true persisted
 * state back with `adb logcat -d -s ConstanzaState`.
 *
 * ## Read-only
 *
 * Every database call below is a `SELECT` issued through a production DAO's read method. This
 * fixture never inserts, updates, deletes, or migrates. Two caveats worth stating plainly, because
 * "read-only" is the entire value of this class:
 *
 * 1. Room offers no read-only builder flag, so the only write it could ever perform is *creating*
 *    an absent database file. [assertDatabaseFileExists] forecloses that: if the file is missing,
 *    the fixture fails loudly instead of reporting a fabricated all-[NO_ROWS] state against a
 *    database it just created itself.
 * 2. Opening at [AppDatabase]'s current `version` cannot trigger a migration while the installed
 *    APK declares that same version, which is the only configuration this fixture is ever run in.
 *
 * ## Why the real database, and how
 *
 * [AppDatabase] is opened with `Room.databaseBuilder` and [AppDatabase.DATABASE_NAME], matching
 * `core/di/DatabaseModule.provideAppDatabase` exactly — same name, same builder, no extra
 * migrations or callbacks, because that module configures none. It is emphatically NOT
 * `inMemoryDatabaseBuilder`, which every behavioural instrumented test in this module uses; an
 * in-memory database would report an empty state that looks like a successful query.
 *
 * ## Readable state names
 *
 * There is nothing to decode. `reminder_occurrences.state`, `entries.status` and `schedules.kind`
 * are all persisted as their readable names (`TEXT` columns), never as integers — see
 * `ReminderOccurrenceEntity.state`, `EntryEntity.status` and `ScheduleEntity.kind`. This fixture
 * therefore logs the persisted string verbatim, which cannot drift from production by construction.
 *
 * The occurrence-state vocabulary itself is defined by `private const val STATE_*` declarations
 * that are not visible outside their files: `STATE_ARMED` (`scheduling/OccurrencePlanner.kt`),
 * `STATE_FIRED` and `STATE_SUPPRESSED` (`scheduling/ReminderFireWorker.kt`), `STATE_SNOOZED`
 * (`reminding/SnoozeWorker.kt`), and `STATE_RESOLVED` / `STATE_ABANDONED`
 * (`scheduling/OccurrenceResolver.kt`). Because they are private, no parallel list is mirrored here
 * at all — the raw column value is reported and those files remain the sole source of truth.
 *
 * Entry statuses are different: [EntryStatus] is public in `:domain`, so [readableEntryStatus]
 * checks the persisted string against that production enum and flags anything it does not
 * recognise, rather than passing an unmappable value off as valid.
 *
 * ## Row coverage
 *
 * The production DAOs expose no unfiltered "select all" for schedules, slots, occurrences or
 * entries, only per-habit lookups, so the report walks [HabitDao.findAllSnapshot] and queries each
 * habit. That is complete rather than merely convenient: all four tables declare
 * `ForeignKey.CASCADE` on `habitId` and Room enables `PRAGMA foreign_keys = ON`, so a row whose
 * habit does not exist cannot be persisted. `ReminderOccurrenceDao.findUnresolved` is deliberately
 * NOT used, since it filters out the three terminal states this report must show.
 *
 * ## Running it
 *
 * `:app:connectedDebugAndroidTest` cannot be used: it excludes this class via [SeedOnly] (see
 * `app/build.gradle.kts`), and it uninstalls both APKs when it finishes, which would destroy the
 * very state this fixture exists to report. Install the two APKs once, then instrument this class
 * directly:
 *
 * ```
 * adb -s <serial> shell am instrument -w -r \
 *   -e class com.jjrapps.constanza.seed.DatabaseStateReport \
 *   com.jjrapps.constanza.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Passing no `notAnnotation` argument is what lets [SeedOnly] run. Read the report back with
 * `adb -s <serial> logcat -d -s ConstanzaState`.
 *
 * Unlike [ImminentReminderSeed], running this fixture repeatedly changes nothing, so it is safe to
 * interleave with delivery-matrix scenarios in progress on the same device.
 */
@RunWith(AndroidJUnit4::class)
@SeedOnly
class DatabaseStateReport {

    @Test
    fun reportDatabaseState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertDatabaseFileExists(context)
        // Matches DatabaseModule.provideAppDatabase exactly: same name, same builder, no
        // migrations and no callbacks, because that module adds none.
        val database = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()
        try {
            val zone = SystemTimeProvider().zone()
            Log.i(TAG, REPORT_BEGIN)
            try {
                reportHeader(context, zone)
                reportTables(database, zone)
            } finally {
                // Emitted even on failure: a report that stops mid-table must still be
                // distinguishable from one that was merely truncated by the logcat buffer.
                Log.i(TAG, REPORT_END)
            }
        } finally {
            database.close()
        }
    }

    /**
     * Fails rather than letting Room create an empty database. Without this, running the fixture
     * against a freshly installed app would emit a complete, well-formed, entirely [NO_ROWS] report
     * — the most dangerous possible output, because it is a fabricated negative that reads as a
     * verified one.
     */
    private fun assertDatabaseFileExists(context: Context) {
        val file = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        assertTrue(
            "No database at ${file.absolutePath}. The app has never created one, so there is no " +
                "state to report. Refusing to continue, because Room would create an empty " +
                "database here and this fixture would then report a fabricated empty state.",
            file.exists(),
        )
    }

    private fun reportHeader(context: Context, zone: ZoneId) {
        val now = Instant.now()
        Log.i(TAG, "META database=${context.getDatabasePath(AppDatabase.DATABASE_NAME).absolutePath}")
        Log.i(TAG, "META zone=$zone now=${epochAndLocal(now.toEpochMilli(), zone)}")
    }

    private suspend fun reportTables(database: AppDatabase, zone: ZoneId) {
        val habits = database.habitDao().findAllSnapshot().sortedBy { it.id }
        logRows("HABIT", habits) { habitLine(it) }

        // Collected across every habit first, so an empty table prints one NONE line for the table
        // rather than one per habit — and so a table that is empty only because there are no habits
        // still prints NONE.
        val schedules = mutableListOf<ScheduleEntity>()
        val slots = mutableListOf<ReminderSlotEntity>()
        val occurrences = mutableListOf<ReminderOccurrenceEntity>()
        val entries = mutableListOf<EntryEntity>()
        habits.forEach { habit ->
            database.scheduleDao().findByHabitId(habit.id)?.let(schedules::add)
            slots += database.reminderSlotDao().findByHabitId(habit.id)
            occurrences += database.reminderOccurrenceDao().findByHabitId(habit.id)
            entries += database.entryDao().findByHabitId(habit.id)
        }

        logRows("SCHEDULE", schedules) { scheduleLine(it) }
        logRows("SLOT", slots.sortedBy { it.id }) { slotLine(it) }
        logRows("OCCURRENCE", occurrences.sortedBy { it.id }) { occurrenceLine(it, zone) }
        logRows("ENTRY", entries.sortedBy { it.id }) { entryLine(it) }

        Log.i(
            TAG,
            "SUMMARY habits=${habits.size} schedules=${schedules.size} slots=${slots.size} " +
                "occurrences=${occurrences.size} entries=${entries.size}",
        )
    }

    /** Logs one line per row, or a single `<section> NONE` line when there are none. */
    private fun <T> logRows(section: String, rows: List<T>, line: (T) -> String) {
        if (rows.isEmpty()) {
            Log.i(TAG, "$section $NO_ROWS")
            return
        }
        rows.forEach { Log.i(TAG, "$section ${line(it)}") }
    }

    private fun habitLine(habit: HabitEntity): String =
        "id=${habit.id} name=\"${habit.name}\" archived=${habit.archived} " +
            "archivedAt=${habit.archivedAt ?: NO_VALUE} createdAt=${habit.createdAt} " +
            "sortOrder=${habit.sortOrder}"

    private fun scheduleLine(schedule: ScheduleEntity): String =
        "habitId=${schedule.habitId} kind=${schedule.kind} " +
            "timesPerWeek=${schedule.timesPerWeek ?: NO_VALUE} dayOfWeek=${schedule.dayOfWeek ?: NO_VALUE} " +
            "dayOfMonth=${schedule.dayOfMonth ?: NO_VALUE} intervalDays=${schedule.intervalDays ?: NO_VALUE} " +
            "anchorDate=${schedule.anchorDate ?: NO_VALUE} weekStart=${schedule.weekStart}"

    private fun slotLine(slot: ReminderSlotEntity): String =
        "habitId=${slot.habitId} slotId=${slot.id} minuteOfDay=${slot.minuteOfDay} " +
            "(${minuteOfDayAsClockTime(slot.minuteOfDay)}) enabled=${slot.enabled}"

    private fun occurrenceLine(occurrence: ReminderOccurrenceEntity, zone: ZoneId): String =
        "id=${occurrence.id} habitId=${occurrence.habitId} slotId=${occurrence.slotId} " +
            "scheduledDate=${occurrence.scheduledDate} state=${occurrence.state} " +
            "scheduledAt=${epochAndLocal(occurrence.scheduledAtEpochMs, zone)} exact=${occurrence.exact} " +
            "snoozeCount=${occurrence.snoozeCount} " +
            "snoozeUntil=${optionalEpochAndLocal(occurrence.snoozeUntilEpochMs, zone)} " +
            "notifiedAt=${optionalEpochAndLocal(occurrence.notifiedAtEpochMs, zone)} " +
            "resolveDeadline=${epochAndLocal(occurrence.resolveDeadlineMs, zone)}"

    private fun entryLine(entry: EntryEntity): String =
        "id=${entry.id} habitId=${entry.habitId} date=${entry.date} slotId=${entry.slotId} " +
            "status=${readableEntryStatus(entry.status)} value=${entry.value ?: NO_VALUE} " +
            "answeredAt=${entry.answeredAt} source=${entry.source}"
}

/** Both representations on every timestamp: the epoch millis a reader can compare against an
 *  alarm dump, and the local time that makes it legible without arithmetic. */
private fun epochAndLocal(epochMs: Long, zone: ZoneId): String =
    "${epochMs}ms (${Instant.ofEpochMilli(epochMs).atZone(zone)})"

private fun optionalEpochAndLocal(epochMs: Long?, zone: ZoneId): String =
    epochMs?.let { epochAndLocal(it, zone) } ?: NO_VALUE

/**
 * Reports the persisted status verbatim, marking anything the production [EntryStatus] enum does
 * not define. Validating against the real enum rather than a list copied into this file means a
 * future status cannot be silently reported as valid.
 */
private fun readableEntryStatus(persisted: String): String =
    if (EntryStatus.entries.any { it.name == persisted }) persisted else "$persisted (UNRECOGNISED)"

private const val MINUTES_PER_HOUR = 60

/** Purely a convenience rendering of the stored `minuteOfDay` integer; the integer itself is
 *  logged alongside it so nothing is lost to the translation. */
private fun minuteOfDayAsClockTime(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
