package com.jjrapps.constanza.seed

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.HabitEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.mapper.toEntity
import com.jjrapps.constanza.core.di.ReminderSettingsDataStoreEntryPoint
import com.jjrapps.constanza.core.time.SystemTimeProvider
import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.localization.AppLocaleController
import com.jjrapps.constanza.reminding.NotificationPoster
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import com.jjrapps.constanza.scheduling.SchedulingDaos
import com.jjrapps.constanza.tracking.EntryWriter
import com.jjrapps.constanza.tracking.InAppEntryStatus
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "ConstanzaListingSeed"

/** How many days of answer history to write per tracked habit (task T3: "about 30 days"). */
private const val HISTORY_DAYS = 30

/** Same knob [ImminentReminderSeed] uses: near enough to watch fire, far enough it cannot fire
 *  mid-seed. This is the ONE habit slot the notification-shade screenshot scene waits on. */
private const val SEED_LEAD_MINUTES = 2

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/** Mirrors [ImminentReminderSeed]'s constant of the same name. */
private const val SEED_RESOLVE_DEADLINE_HOURS = 24L

private const val ARG_LANGUAGE = "language"
private const val LANGUAGE_EN = "en"

private const val ENTRY_SOURCE_SWEEP = "SWEEP"

/** A single habit's insert result plus the slot ids the main test needs afterwards to resolve
 *  today's answers through [EntryWriter] — never invented, always the id Room actually assigned. */
private data class SeededHabit(val habitId: Long, val todaySlotId: Long?)

/**
 * NOT A BEHAVIOURAL TEST — a manual, on-device seeding fixture for the Google Play listing
 * screenshots (odd/tasks/play-store-listing.md, T3). Writes a small, deliberately varied set of
 * habits with ~30 days of realistic answer history to the app's REAL database, through the same
 * production DAOs, mappers and — for today's own answers — [EntryWriter] that [ImminentReminderSeed]
 * and the app's own Today screen use, so the capture pipeline photographs exactly what a real
 * long-time user's data looks like, never a mocked or hand-drawn screen.
 *
 * ## What it seeds
 *
 * Five habits, chosen so Today's three groups (`groupTodayRows` — Ahora/Más tarde/Contestados) are
 * ALL populated, deterministically, regardless of the real wall-clock time the pipeline happens to
 * run at:
 *
 * 1. **Water** ([Schedule.TimesPerDay], three reminders/day) — the notification-scene anchor. Its
 *    three historical reminder times are resolved as answered FOR TODAY too, through
 *    [EntryWriter.answerInApp] against their own real occurrences, and only the fourth, ad-hoc slot
 *    — armed [SEED_LEAD_MINUTES] minutes from seeding time — is left pending. Its `actionableInstant`
 *    is therefore always in the future at Today-capture time, so this row always lands in
 *    "Más tarde" showing "En progreso", never in "Ahora": the real posted reminder notification
 *    (Yes/No/Snooze) that scene waits for arrives a couple of minutes after Today is captured.
 * 2. **Read** ([Schedule.Daily]) — resolved COMPLETED (Sí) for today through the same production
 *    path, landing in "Contestados" with the completed glyph. Also the best-looking streak,
 *    reserved for the Progress screenshot.
 * 3. **Walk** ([Schedule.NTimesPerWeek]) — deliberately configured with NO reminder slot at all
 *    (`enabledSlots.isEmpty()` in `TodayModel.buildTodayHabitRow`), so its single synthetic slot is
 *    untimed. `TodayModel.placement`'s own rule — an untimed unanswered slot is always
 *    "overdue or untimed" — makes this row land in "Ahora" unconditionally, with no dependence on
 *    the clock at all. Its history also carries one under-quota week, so
 *    [com.jjrapps.constanza.domain.StreakCalculator]'s weekly streak visibly breaks once.
 * 4. **Meditate** ([Schedule.DaysOfWeek]) — resolved MISSED (No) for today through the same
 *    production path, landing in "Contestados" with the missed glyph — the deliberate "No" pairing
 *    Read's "Sí" needs so the group shows both answer glyphs, not just ticks. Its schedule is
 *    configured to include TODAY's weekday plus two others spread through the week, so it is always
 *    due today regardless of which real day the pipeline runs.
 * 5. **Stretch** ([Schedule.EveryNDays], anchored on today) — left unanswered, with its reminder a
 *    future offset from seeding time, so it always lands in "Más tarde" alongside Water.
 *
 * ## Why "later" reminder slots are offsets from now, never a fixed clock hour
 *
 * `OccurrencePlanner` arms whatever epoch millis a slot's minute-of-day resolves to for TODAY even
 * when that instant has already passed, and `AlarmManager` then fires an already-due alarm almost
 * immediately. A first full-pipeline run measured this directly: Meditate (fixed 07:30) and Stretch
 * (fixed 09:00) posted their own unplanned notifications during a mid-afternoon run, polluting the
 * Water-only notification scene. Every "later" slot here is therefore `now + offset minutes`
 * ([laterToday]), guaranteed to stay in the future for the whole capture session, while Water's own
 * dedicated near-term slot remains the only one anywhere close to firing.
 *
 * ## Why explicit MISSED rows in history, not gaps
 *
 * [com.jjrapps.constanza.domain.EntryResolution] resolves an absent row to `UNKNOWN`, a pass-through
 * that neither breaks nor extends a streak (design.md §8, StreakCalculator's own KDoc) — that is
 * the real midnight-sweep's job (`OccurrenceResolver`, `source = "SWEEP"`). A "few misses" therefore
 * has to be written as real `MISSED` rows, exactly as the sweep would leave them, or the streak
 * math would simply skip the gap and the history would look unrealistically perfect.
 *
 * ## Running it
 *
 * Same constraints as [ImminentReminderSeed]: a release build cannot be instrumented, and
 * `:app:connectedDebugAndroidTest` excludes this class via [SeedOnly] and uninstalls both APKs
 * when it finishes. Install once, then instrument directly, passing the target language:
 *
 * ```
 * ./gradlew :app:installDebug :app:installDebugAndroidTest
 * adb -s <serial> shell pm grant com.jjrapps.constanza android.permission.POST_NOTIFICATIONS
 * adb -s <serial> shell appops set com.jjrapps.constanza SCHEDULE_EXACT_ALARM allow
 * adb -s <serial> shell am instrument -w -r \
 *   -e class com.jjrapps.constanza.seed.ListingScreenshotSeed -e language es \
 *   com.jjrapps.constanza.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Re-running (including with a different `language`) is safe and idempotent: every existing habit
 * is deleted (cascading through schedules/slots/entries/occurrences) and its alarms cancelled
 * before the fresh set is inserted, exactly like [ImminentReminderSeed]'s own `removePreviousSeeds`.
 * The seeded ids and the imminent slot's fire time are logged under the `ConstanzaListingSeed` tag.
 */
@RunWith(AndroidJUnit4::class)
@SeedOnly
class ListingScreenshotSeed {

    @Test
    fun seedListingScreenshotData(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val language = InstrumentationRegistry.getArguments().getString(ARG_LANGUAGE) ?: "es"
        val english = language == LANGUAGE_EN

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
            val planner = OccurrencePlanner(daos, database.entryDao(), alarmScheduler, timeProvider, SEED_RESOLVE_DEADLINE_HOURS)
            val settings = reminderSettingsDataStore(context)
            val entryWriter = EntryWriter(
                database = database,
                entryDao = database.entryDao(),
                reminderOccurrenceDao = daos.reminderOccurrenceDao,
                alarmScheduler = alarmScheduler,
                notificationPoster = NotificationPoster(context, AppLocaleController(context, ReminderSettingsStore(settings))),
                timeProvider = timeProvider,
            )

            removeExistingHabits(daos, alarmScheduler)
            // A reminder posted by a previous run would otherwise still be in the shade, and the
            // capture pipeline's "wait for our notification" would pass on it at once — then watch
            // it disappear, because the occurrence it belongs to was just deleted above. Clearing
            // them here means the only Constanza notification that can appear is this run's.
            context.getSystemService(NotificationManager::class.java).cancelAll()
            markOnboardingDone(settings)

            val now = timeProvider.now()
            val zone = timeProvider.zone()
            val today = now.atZone(zone).toLocalDate()
            val nowMinuteOfDay = now.atZone(zone).let { it.hour * MINUTES_PER_HOUR + it.minute }

            val water = seedWaterHabit(database, daos, now, zone, today, english)
            val read = seedReadHabit(database, daos, now, today, english, nowMinuteOfDay)
            seedWalkHabit(database, daos, now, today, english)
            val meditate = seedMeditateHabit(database, daos, now, today, english, nowMinuteOfDay)
            seedStretchHabit(database, daos, now, today, english, nowMinuteOfDay)

            // Production code arms every alarm from here, including the imminent water slot the
            // notification-scene capture step waits on — this fixture never constructs an
            // occurrence or touches AlarmManager directly.
            planner.replanAll()

            // Today's "already answered" rows, THROUGH the exact same write path the Today screen
            // itself uses (never a raw EntryEntity insert): resolves the real occurrence armed by
            // replanAll() above, so the row's slotId, its occurrence state and its cancelled alarm
            // are all byte-for-byte what tapping Yes/No in the app would have produced.
            // Only the 3 FIXED slots, never `water.todaySlotId` — that id is the ad-hoc IMMINENT
            // slot itself (see seedWaterHabit), and resolving it here would answer the exact slot
            // the notification scene is waiting to fire, defeating the whole point of seeding it.
            resolveWaterFixedSlotsToday(entryWriter, daos, water.habitId, today)
            resolveTodayAnswer(entryWriter, daos, read.habitId, read.todaySlotId, today, InAppEntryStatus.COMPLETED)
            resolveTodayAnswer(entryWriter, daos, meditate.habitId, meditate.todaySlotId, today, InAppEntryStatus.MISSED)
            // Walk and Stretch are deliberately left unanswered — see the class KDoc for why each
            // lands in "Ahora" (Walk, untimed) or "Más tarde" (Stretch, a future offset) for it.

            Log.i(TAG, "SEEDED language=$language today=$today")
        } finally {
            database.close()
        }
    }

    private suspend fun removeExistingHabits(daos: SchedulingDaos, alarmScheduler: AlarmScheduler) {
        daos.habitDao.findAllSnapshot().forEach { habit ->
            daos.reminderOccurrenceDao.findByHabitId(habit.id).forEach { alarmScheduler.cancel(it.id) }
            daos.habitDao.deleteById(habit.id)
        }
        Log.i(TAG, "Removed any previously seeded habits.")
    }

    private fun reminderSettingsDataStore(context: Context): DataStore<Preferences> =
        EntryPointAccessors.fromApplication(context, ReminderSettingsDataStoreEntryPoint::class.java)
            .reminderSettingsDataStore()

    /** Onboarding's write-once completion flag, and the "already asked" notification-permission
     *  latch, both through [ReminderSettingsDataStoreEntryPoint] — the same sanctioned androidTest
     *  route `CoreFlowTestFixture` uses (design.md §8.1). Setting both means the app opens straight
     *  on Today, with no onboarding screen and no notification-permission banner, as long as the
     *  real system permission was also granted (`adb shell pm grant ... POST_NOTIFICATIONS`, a
     *  pipeline pre-step this fixture cannot itself perform: `am instrument` has no permission to
     *  grant permissions to the very APK it is instrumenting). */
    private suspend fun markOnboardingDone(settings: DataStore<Preferences>) {
        settings.edit {
            it[ReminderSettingsStore.ONBOARDING_DONE_KEY] = true
            it[ReminderSettingsStore.REQUESTED_NOTIFICATION_PERMISSION_KEY] = true
        }
    }

    /** Resolves ONE habit's today slot through [EntryWriter.answerInApp] — looks up the real
     *  occurrence [OccurrencePlanner.replanAll] just armed for it and answers exactly that, the
     *  same call `TodayViewModel.answer` makes. A habit with no slot at all ([todaySlotId] `null`,
     *  Walk's case) is never routed here — see the class KDoc for why it stays unanswered instead. */
    private suspend fun resolveTodayAnswer(
        entryWriter: EntryWriter,
        daos: SchedulingDaos,
        habitId: Long,
        todaySlotId: Long?,
        today: LocalDate,
        status: InAppEntryStatus,
    ) {
        if (todaySlotId == null) return
        val occurrence = daos.reminderOccurrenceDao.findByHabitSlotDate(habitId, todaySlotId, today.toString())
        entryWriter.answerInApp(habitId, today, todaySlotId, status, occurrence?.id)
    }

    /** Water's three historical reminder times (see [seedWaterHabit]) are ALSO resolved answered
     *  for today, through the same [EntryWriter] path, so the only slot left pending is the
     *  dedicated imminent one — deterministically, regardless of how many of the three fixed times
     *  have or have not "really" passed at seeding time. */
    private suspend fun resolveWaterFixedSlotsToday(
        entryWriter: EntryWriter,
        daos: SchedulingDaos,
        habitId: Long,
        today: LocalDate,
    ) {
        daos.reminderSlotDao.findByHabitId(habitId)
            .filter { it.minuteOfDay in WATER_FIXED_MINUTES }
            .forEach { slot ->
                val occurrence = daos.reminderOccurrenceDao.findByHabitSlotDate(habitId, slot.id, today.toString())
                entryWriter.answerInApp(habitId, today, slot.id, InAppEntryStatus.COMPLETED, occurrence?.id)
            }
    }

    // ---------------------------------------------------------------------------------------
    // Water — Schedule.TimesPerDay, the notification-scene anchor.
    // ---------------------------------------------------------------------------------------

    private suspend fun seedWaterHabit(
        database: AppDatabase,
        daos: SchedulingDaos,
        now: Instant,
        zone: ZoneId,
        today: LocalDate,
        english: Boolean,
    ): SeededHabit {
        val habitId = daos.habitDao.insert(
            HabitEntity(
                name = if (english) "Drink water" else "Beber agua",
                colorArgb = HabitColor.LIGHT_BLUE.argb,
                notes = if (english) "One glass at a time, no pressure." else "Un vaso cada vez, sin agobios.",
                archived = false,
                archivedAt = null,
                createdAt = now.minusSeconds(SEED_HABIT_AGE_SECONDS).toString(),
            ),
        )
        daos.scheduleDao.upsert(Schedule.TimesPerDay().toEntity(habitId))

        val fixedSlotIds = WATER_FIXED_MINUTES.map { minute ->
            minute to daos.reminderSlotDao.insert(ReminderSlotEntity(habitId = habitId, minuteOfDay = minute, enabled = true))
        }

        // History: 29 past days on the 3 fixed slots, an occasional MISSED row every sixth
        // occurrence, always COMPLETED for the most recent few so a current streak is visible.
        val dueDates = (1..HISTORY_DAYS - 1).map { today.minusDays(it.toLong()) }.sortedBy { it.toEpochDay() }
        fixedSlotIds.forEachIndexed { slotIndex, (_, slotId) ->
            dueDates.forEachIndexed { dateIndex, date ->
                val status = historyStatus(dateIndex, dueDates.size, offset = slotIndex)
                database.entryDao().insert(historyEntry(habitId, date, slotId, status, hour = 12))
            }
        }

        // The imminent slot — the ONLY slot left pending after resolveWaterFixedSlotsToday runs,
        // and the one the notification-scene capture step waits on.
        val nowMinuteOfDay = now.atZone(zone).let { it.hour * MINUTES_PER_HOUR + it.minute }
        val imminentMinute = nowMinuteOfDay + SEED_LEAD_MINUTES
        assertTrue(
            "Local time is ${now.atZone(zone)}: a $SEED_LEAD_MINUTES-minute lead would roll past " +
                "midnight, scheduling today's occurrence in the past. Re-run after midnight.",
            imminentMinute < MINUTES_PER_DAY,
        )
        val imminentSlotId = daos.reminderSlotDao.insert(ReminderSlotEntity(habitId = habitId, minuteOfDay = imminentMinute, enabled = true))
        Log.i(TAG, "SEEDED water habitId=$habitId imminentMinuteOfDay=$imminentMinute")
        return SeededHabit(habitId, imminentSlotId)
    }

    // ---------------------------------------------------------------------------------------
    // Read — Schedule.Daily, resolved Sí for today; also reserved as the Progress-screen hero.
    // ---------------------------------------------------------------------------------------

    private suspend fun seedReadHabit(
        database: AppDatabase,
        daos: SchedulingDaos,
        now: Instant,
        today: LocalDate,
        english: Boolean,
        nowMinuteOfDay: Int,
    ): SeededHabit {
        val habitId = daos.habitDao.insert(
            HabitEntity(
                name = if (english) "Read 20 minutes" else "Leer 20 minutos",
                colorArgb = HabitColor.AMBER.argb,
                notes = if (english) "No screens, paper or e-reader." else "Sin pantallas, papel o libro electrónico.",
                archived = false,
                archivedAt = null,
                createdAt = now.minusSeconds(SEED_HABIT_AGE_SECONDS).toString(),
            ),
        )
        daos.scheduleDao.upsert(Schedule.Daily().toEntity(habitId))
        val slotId = daos.reminderSlotDao.insert(
            ReminderSlotEntity(habitId = habitId, minuteOfDay = laterToday(nowMinuteOfDay, READ_OFFSET_MINUTES), enabled = true),
        )

        val dueDates = (1..HISTORY_DAYS - 1).map { today.minusDays(it.toLong()) }.sortedBy { it.toEpochDay() }
        dueDates.forEachIndexed { index, date ->
            // A rarer miss (every 10th, not every 6th) than the other habits: this is deliberately
            // the tidiest history, since it is the one the Progress screenshot shows.
            val status = if (index < dueDates.size - RECENT_STREAK_GUARD && index % READ_MISS_EVERY == READ_MISS_EVERY - 1) {
                EntryStatus.MISSED
            } else {
                EntryStatus.COMPLETED
            }
            database.entryDao().insert(historyEntry(habitId, date, slotId = 0, status = status, hour = 21))
        }
        Log.i(TAG, "SEEDED read habitId=$habitId")
        return SeededHabit(habitId, slotId)
    }

    // ---------------------------------------------------------------------------------------
    // Walk — Schedule.NTimesPerWeek(3), deliberately WITHOUT a reminder slot: TodayModel's own
    // "untimed unanswered is always Ahora" rule (see class KDoc) is what places this row, not the
    // clock. The WEEK is the unit of obligation (design D8), so history is written per-week, not
    // per-day — StreakCalculator's weekly path only counts COMPLETED rows against the week's quota
    // and never reads MISSED for this schedule kind at all.
    // ---------------------------------------------------------------------------------------

    private suspend fun seedWalkHabit(database: AppDatabase, daos: SchedulingDaos, now: Instant, today: LocalDate, english: Boolean) {
        val habitId = daos.habitDao.insert(
            HabitEntity(
                name = if (english) "Walk" else "Caminar",
                colorArgb = HabitColor.GREEN.argb,
                notes = if (english) "At least twenty minutes straight." else "Al menos veinte minutos seguidos.",
                archived = false,
                archivedAt = null,
                createdAt = now.minusSeconds(SEED_HABIT_AGE_SECONDS).toString(),
            ),
        )
        daos.scheduleDao.upsert(Schedule.NTimesPerWeek(times = WALK_TIMES_PER_WEEK).toEntity(habitId))

        val weekStart = today.minusDays((HISTORY_DAYS - 1).toLong()).with(DayOfWeek.MONDAY)
        var week = weekStart
        var weekIndex = 0
        while (!week.isAfter(today)) {
            // Every week meets quota except one, deliberately placed a few weeks back so both a
            // broken streak AND a healthy current one are visible in the same screenshot.
            val completedCount = if (weekIndex == WALK_UNDER_QUOTA_WEEK_INDEX) WALK_TIMES_PER_WEEK - 1 else WALK_TIMES_PER_WEEK
            WALK_WEEKDAYS.take(completedCount).forEach { dayOfWeek ->
                val date = week.with(dayOfWeek)
                // Strictly BEFORE today, never `!date.isAfter(today)`: today is itself one of
                // WALK_WEEKDAYS whenever the pipeline happens to run on a Monday, Wednesday or
                // Friday, and `!isAfter` would silently write today's own "history" entry —
                // exactly the bug a live run caught (Walk showing fully "Done" instead of
                // unanswered). Walk's whole point is staying unanswered for today — see the class
                // KDoc — so history must never reach into it.
                if (date.isBefore(today) && !date.isBefore(weekStart)) {
                    database.entryDao().insert(historyEntry(habitId, date, slotId = 0, status = EntryStatus.COMPLETED, hour = 18))
                }
            }
            week = week.plusWeeks(1)
            weekIndex++
        }
        Log.i(TAG, "SEEDED walk habitId=$habitId (no reminder slot — always due 'Ahora' when unanswered)")
    }

    // ---------------------------------------------------------------------------------------
    // Meditate — Schedule.DaysOfWeek, resolved No (MISSED) for today — Read's "Sí" counterpart, so
    // "Contestados" shows both answer glyphs, not just ticks. Configured around TODAY so it is
    // always due when the pipeline runs, whatever the real calendar day is.
    // ---------------------------------------------------------------------------------------

    private suspend fun seedMeditateHabit(
        database: AppDatabase,
        daos: SchedulingDaos,
        now: Instant,
        today: LocalDate,
        english: Boolean,
        nowMinuteOfDay: Int,
    ): SeededHabit {
        val habitId = daos.habitDao.insert(
            HabitEntity(
                name = if (english) "Meditate" else "Meditar",
                colorArgb = HabitColor.VIOLET.argb,
                notes = if (english) "Five minutes of breathing is enough." else "Cinco minutos de respiración basta.",
                archived = false,
                archivedAt = null,
                createdAt = now.minusSeconds(SEED_HABIT_AGE_SECONDS).toString(),
            ),
        )
        val days = setOf(today.dayOfWeek, today.dayOfWeek.plus(2), today.dayOfWeek.plus(4))
        daos.scheduleDao.upsert(Schedule.DaysOfWeek(days = days).toEntity(habitId))
        val slotId = daos.reminderSlotDao.insert(
            ReminderSlotEntity(habitId = habitId, minuteOfDay = laterToday(nowMinuteOfDay, MEDITATE_OFFSET_MINUTES), enabled = true),
        )

        val dueDates = (1..HISTORY_DAYS - 1)
            .map { today.minusDays(it.toLong()) }
            .filter { it.dayOfWeek in days }
            .sortedBy { it.toEpochDay() }
        dueDates.forEachIndexed { index, date ->
            val status = historyStatus(index, dueDates.size, offset = 0)
            database.entryDao().insert(historyEntry(habitId, date, slotId = 0, status = status, hour = MEDITATE_HISTORY_HOUR))
        }
        Log.i(TAG, "SEEDED meditate habitId=$habitId days=$days")
        return SeededHabit(habitId, slotId)
    }

    // ---------------------------------------------------------------------------------------
    // Stretch — Schedule.EveryNDays, anchored on today so it is always due when the pipeline runs.
    // Left unanswered with a future-offset reminder, so it always lands in "Más tarde".
    // ---------------------------------------------------------------------------------------

    private suspend fun seedStretchHabit(
        database: AppDatabase,
        daos: SchedulingDaos,
        now: Instant,
        today: LocalDate,
        english: Boolean,
        nowMinuteOfDay: Int,
    ) {
        val habitId = daos.habitDao.insert(
            HabitEntity(
                name = if (english) "Stretch" else "Estirar",
                colorArgb = HabitColor.ORANGE.argb,
                notes = if (english) "Back, legs and neck." else "Espalda, piernas y cuello.",
                archived = false,
                archivedAt = null,
                createdAt = now.minusSeconds(SEED_HABIT_AGE_SECONDS).toString(),
            ),
        )
        daos.scheduleDao.upsert(Schedule.EveryNDays(n = STRETCH_INTERVAL_DAYS, anchor = today).toEntity(habitId))
        daos.reminderSlotDao.insert(
            ReminderSlotEntity(habitId = habitId, minuteOfDay = laterToday(nowMinuteOfDay, STRETCH_OFFSET_MINUTES), enabled = true),
        )

        val dueDates = generateSequence(today.minusDays(STRETCH_INTERVAL_DAYS.toLong())) { it.minusDays(STRETCH_INTERVAL_DAYS.toLong()) }
            .takeWhile { !it.isBefore(today.minusDays((HISTORY_DAYS - 1).toLong())) }
            .sortedBy { it.toEpochDay() }
            .toList()
        dueDates.forEachIndexed { index, date ->
            val status = historyStatus(index, dueDates.size, offset = 0)
            database.entryDao().insert(historyEntry(habitId, date, slotId = 0, status = status, hour = 9))
        }
        Log.i(TAG, "SEEDED stretch habitId=$habitId")
    }

    // ---------------------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------------------

    /** A `MISSED` roughly every sixth due occurrence, guaranteed `COMPLETED` for the most recent
     *  [RECENT_STREAK_GUARD] so every screenshot always shows a live current streak regardless of
     *  which real day the pipeline runs on. [offset] staggers the pattern across the water habit's
     *  three slots so they do not all miss on the same day. */
    private fun historyStatus(indexFromOldest: Int, total: Int, offset: Int): EntryStatus =
        if (indexFromOldest < total - RECENT_STREAK_GUARD && (indexFromOldest + offset) % MISS_EVERY == MISS_EVERY - 1) {
            EntryStatus.MISSED
        } else {
            EntryStatus.COMPLETED
        }

    /**
     * A future clock-time slot for a habit whose reminder is not the notification-scene anchor
     * (Read, Meditate, Stretch) — never a fixed clock hour. See the class KDoc's "Why 'later'
     * reminder slots are offsets from now" section for the exact failure this avoids. Clamped
     * rather than asserted: unlike Water's imminent slot, a same-day roll-past-midnight here is
     * harmless (it only pushes a decorative slot time), so clamping to 23:59 is preferable to
     * failing the whole seed over it.
     */
    private fun laterToday(nowMinuteOfDay: Int, offsetMinutes: Int): Int =
        minOf(nowMinuteOfDay + offsetMinutes, MINUTES_PER_DAY - 1)

    private fun historyEntry(habitId: Long, date: LocalDate, slotId: Long, status: EntryStatus, hour: Int): EntryEntity {
        val answeredAt = date.atStartOfDay(ZoneId.systemDefault()).plusHours(hour.toLong()).toInstant()
        return EntryEntity(
            habitId = habitId,
            date = date.toString(),
            slotId = slotId,
            status = status.name,
            value = null,
            answeredAt = answeredAt.toString(),
            source = if (status == EntryStatus.MISSED) ENTRY_SOURCE_SWEEP else "IN_APP",
        )
    }

    private companion object {
        /** How long before today's history window each habit was "created" — comfortably before
         *  the 30-day history starts, so no history date ever precedes the habit's own creation. */
        const val SEED_HABIT_AGE_SECONDS = (HISTORY_DAYS + 15) * 24 * 60 * 60L

        const val RECENT_STREAK_GUARD = 4
        const val MISS_EVERY = 6
        const val READ_MISS_EVERY = 10

        const val WALK_TIMES_PER_WEEK = 3
        const val WALK_UNDER_QUOTA_WEEK_INDEX = 1
        val WALK_WEEKDAYS = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        const val MEDITATE_HISTORY_HOUR = 7
        const val STRETCH_INTERVAL_DAYS = 3

        val WATER_FIXED_MINUTES = listOf(8 * MINUTES_PER_HOUR, 13 * MINUTES_PER_HOUR, 19 * MINUTES_PER_HOUR)

        // Distinct, generously spaced offsets from "now" for every live reminder slot except
        // Water's dedicated SEED_LEAD_MINUTES one — see [laterToday]'s KDoc for why none of these
        // may ever be a fixed clock hour.
        const val READ_OFFSET_MINUTES = 45
        const val MEDITATE_OFFSET_MINUTES = 105
        const val STRETCH_OFFSET_MINUTES = 135
    }
}
