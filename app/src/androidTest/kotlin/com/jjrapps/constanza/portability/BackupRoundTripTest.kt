package com.jjrapps.constanza.portability

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.core.data.mapper.toMask
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.reminding.SnoozeDuration
import io.mockk.verify
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

private const val SLOT_MINUTE_OF_DAY = 480

/** [PortabilityTestFixture]'s clock is fixed at 2026-09-01T08:00:00Z, so 07:00 is unambiguously in
 *  the past at import time — which is exactly when `setExactAndAllowWhileIdle` fires immediately. */
private const val FIXTURE_TODAY = "2026-09-01"
private const val PAST_SLOT_MINUTE_OF_DAY = 7 * 60

private fun pastReminderInstantMillis(): Long =
    Instant.parse("${FIXTURE_TODAY}T07:00:00Z").toEpochMilli()

/**
 * Tasks 7.6/7.7 (data-portability: Round-Trip Fidelity; Import — Malformed file leaves data
 * intact). Real Room + real DataStore, no Compose involved — the connected-device Compose UI test
 * environment defect (Engram: "Compose UI tests cannot execute on the connected Pixel 10") does
 * not apply to this class at all.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fixture: PortabilityTestFixture

    @Before
    fun setUp() {
        fixture = PortabilityTestFixture(
            ApplicationProvider.getApplicationContext<Context>(),
            tempFolder.newFile("portability_settings_test.preferences_pb"),
        )
    }

    @After
    fun tearDown() = fixture.close()

    /** Task 7.6: export, wipe, import restores every habit/schedule/slot/entry, including an
     *  archived habit's pre-archive history — and proves the slotId remap is coherent, not just
     *  that "a" slot exists, by following the restored entry's own `slotId` back to a restored
     *  slot row rather than asserting a hardcoded id. */
    @Test
    fun exportWipeImportRestoresHabitsSchedulesSlotsAndEntriesIncludingArchivedHistory() = runBlocking {
        val activeHabitId = fixture.database.habitDao().insert(habitEntity(name = "Meditate"))
        fixture.database.scheduleDao().upsert(scheduleEntity(activeHabitId, kind = "TIMES_PER_DAY"))
        val slotId = fixture.database.reminderSlotDao().insert(
            ReminderSlotEntity(habitId = activeHabitId, minuteOfDay = SLOT_MINUTE_OF_DAY, enabled = true),
        )
        fixture.database.entryDao().insert(
            EntryEntity(
                habitId = activeHabitId, date = "2026-08-30", slotId = slotId, status = "COMPLETED",
                value = null, answeredAt = "2026-08-30T08:03:00Z", source = "NOTIFICATION",
            ),
        )
        val archivedHabitId = fixture.database.habitDao().insert(
            habitEntity(name = "Old habit", archived = true, archivedAt = "2026-06-01"),
        )
        fixture.database.scheduleDao().upsert(scheduleEntity(archivedHabitId, kind = "DAILY"))
        fixture.database.entryDao().insert(
            EntryEntity(
                habitId = archivedHabitId, date = "2026-05-30", slotId = 0, status = "MISSED",
                value = null, answeredAt = "2026-05-30T20:05:00Z", source = "SWEEP",
            ),
        )
        fixture.settingsStore.setSnoozeDuration(SnoozeDuration.THIRTY_MINUTES)

        val json = fixture.exporter.serialize(fixture.exporter.buildBackup())

        fixture.database.habitDao().deleteAll()
        assertTrue("wipe must actually clear the dataset", fixture.database.habitDao().findAllSnapshot().isEmpty())

        fixture.importer.replaceAll(fixture.importer.parseAndValidate(json))

        val restored = fixture.database.habitDao().findAllSnapshot()
        assertEquals(2, restored.size)
        val restoredActive = restored.single { it.name == "Meditate" }
        val restoredArchived = restored.single { it.name == "Old habit" }
        assertFalse(restoredActive.archived)
        assertTrue(restoredArchived.archived)
        assertEquals("2026-06-01", restoredArchived.archivedAt)

        val restoredSlot = fixture.database.reminderSlotDao().findByHabitId(restoredActive.id).single()
        val restoredEntry = fixture.database.entryDao().findByHabitId(restoredActive.id).single()
        assertEquals(
            "the entry's slotId must follow the SAME remap the slot itself got, not the old id",
            restoredSlot.id,
            restoredEntry.slotId,
        )
        assertEquals("COMPLETED", restoredEntry.status)

        val restoredArchivedEntry = fixture.database.entryDao().findByHabitId(restoredArchived.id).single()
        assertEquals("MISSED", restoredArchivedEntry.status)
        assertEquals(
            "archived history predates the archive and must survive with no slot",
            0L,
            restoredArchivedEntry.slotId,
        )

        assertEquals(SnoozeDuration.THIRTY_MINUTES, fixture.settingsStore.currentSnoozeDuration())
    }

    /**
     * **The structural gap that let the defect ship.** Every other assertion in this class is about
     * data fidelity, yet `replaceAll`'s contract includes `alarmScheduler.cancel` and
     * `occurrencePlanner.replanAll()` — it leaves SCHEDULING state behind, and nothing here ever
     * looked at it.
     *
     * The backup carries entries, not occurrences (design.md §8.4), and import wipes
     * `reminder_occurrences` by cascade before replanning. So a habit whose slot the user already
     * completed today came back with no occurrence row, and the planner's only "leave it alone"
     * gate was keyed on exactly that row. It armed a fresh `ARMED` occurrence for a reminder time
     * already in the past — `setExactAndAllowWhileIdle` fires those immediately — and the stale row
     * was then swept at midnight into a `MISSED` that REPLACED the restored `COMPLETED`.
     *
     * The fixture's clock is fixed at 08:00 UTC and the slot is at 07:00, so the reminder time is
     * unambiguously in the past at import.
     */
    @Test
    fun importDoesNotArmAReminderForASlotAlreadyCompletedToday() = runBlocking {
        val habitId = fixture.database.habitDao().insert(habitEntity(name = "Meditate"))
        fixture.database.scheduleDao().upsert(scheduleEntity(habitId, kind = "DAILY"))
        val slotId = fixture.database.reminderSlotDao().insert(
            ReminderSlotEntity(habitId = habitId, minuteOfDay = PAST_SLOT_MINUTE_OF_DAY, enabled = true),
        )
        fixture.database.entryDao().insert(
            EntryEntity(
                habitId = habitId, date = FIXTURE_TODAY, slotId = slotId, status = "COMPLETED",
                value = null, answeredAt = "${FIXTURE_TODAY}T07:03:00Z", source = "NOTIFICATION",
            ),
        )

        val json = fixture.exporter.serialize(fixture.exporter.buildBackup())
        fixture.database.habitDao().deleteAll()

        fixture.importer.replaceAll(fixture.importer.parseAndValidate(json))

        val restored = fixture.database.habitDao().findAllSnapshot().single()
        val restoredSlot = fixture.database.reminderSlotDao().findByHabitId(restored.id).single()
        val todaysOccurrences = fixture.database.reminderOccurrenceDao()
            .findByHabitId(restored.id)
            .filter { it.scheduledDate == FIXTURE_TODAY }
        assertTrue(
            "an already-completed slot must not be re-armed by an import, got $todaysOccurrences",
            todaysOccurrences.isEmpty(),
        )
        verify(exactly = 0) { fixture.alarmScheduler.schedule(any(), pastReminderInstantMillis()) }

        // The restored answer itself must survive untouched — the notification was the symptom,
        // rewritten history the defect.
        val restoredEntry = fixture.database.entryDao().findByHabitAndDate(restored.id, FIXTURE_TODAY).single()
        assertEquals("COMPLETED", restoredEntry.status)
        assertEquals(restoredSlot.id, restoredEntry.slotId)

        // Tomorrow is unaffected by today's answer and MUST still be armed, so this proves the
        // guard is date-scoped rather than a blanket "this habit is done" switch.
        assertTrue(
            "future dates must still be planned",
            fixture.database.reminderOccurrenceDao()
                .findByHabitId(restored.id)
                .any { it.scheduledDate > FIXTURE_TODAY && it.state == "ARMED" },
        )
    }

    /** Task 7.7: unparseable text is rejected before any write, and the existing dataset — not
     *  merely "no exception was thrown" — is proven byte-for-byte the same afterward. */
    @Test
    fun malformedFileIsRejectedAndTheExistingDatasetIsUnchanged() = runBlocking {
        val habitId = fixture.database.habitDao().insert(habitEntity(name = "Meditate"))
        fixture.database.scheduleDao().upsert(scheduleEntity(habitId, kind = "DAILY"))
        val before = fixture.database.habitDao().findAllSnapshot()

        assertTrue(
            "parseAndValidate must throw, not silently succeed",
            runCatching { fixture.importer.parseAndValidate("not a valid backup file") }.isFailure,
        )

        assertEquals(
            "rejecting a malformed file must not touch the existing dataset",
            before,
            fixture.database.habitDao().findAllSnapshot(),
        )
    }

    /** Same guarantee, exercised through the OTHER rejection path (design.md §8.4's Forward
     *  compatibility row): a `formatVersion` too new refuses the whole import, not just an
     *  unparseable file — leaving data intact either way. */
    @Test
    fun newerFormatVersionIsRejectedAndTheExistingDatasetIsUnchanged() = runBlocking {
        val habitId = fixture.database.habitDao().insert(habitEntity(name = "Meditate"))
        fixture.database.scheduleDao().upsert(scheduleEntity(habitId, kind = "DAILY"))
        val before = fixture.database.habitDao().findAllSnapshot()
        val tooNew = fixture.exporter.buildBackup().copy(formatVersion = CURRENT_BACKUP_FORMAT_VERSION + 1)
        val json = fixture.exporter.serialize(tooNew)

        assertTrue(
            "a too-new formatVersion must throw, not import partially",
            runCatching { fixture.importer.parseAndValidate(json) }.isFailure,
        )

        assertEquals(before, fixture.database.habitDao().findAllSnapshot())
    }

    /** weekday-only-schedule (habit-scheduling: Day-Set Due Behavior for DAYS_OF_WEEK). Proves the
     *  day set survives the full export -> JSON -> import round trip as [BackupSchedule.daysOfWeek]
     *  day NAMES, not as the raw bitmask — the backup file format design.md decision 2 chose,
     *  Mirroring `Mappers.kt`'s own encoding/decoding via [toMask]/`ScheduleEntity.toDomain`. */
    @Test
    fun multiDayHabitRoundTripsThroughDayNamesNotTheRawMask() = runBlocking {
        val habitId = fixture.database.habitDao().insert(habitEntity(name = "Laundry"))
        val monWedFriMask = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).toMask()
        fixture.database.scheduleDao().upsert(
            scheduleEntity(habitId, kind = "DAYS_OF_WEEK", daysOfWeekMask = monWedFriMask),
        )

        val json = fixture.exporter.serialize(fixture.exporter.buildBackup())
        fixture.database.habitDao().deleteAll()

        fixture.importer.replaceAll(fixture.importer.parseAndValidate(json))

        val restored = fixture.database.habitDao().findAllSnapshot().single { it.name == "Laundry" }
        val restoredSchedule = requireNotNull(fixture.database.scheduleDao().findByHabitId(restored.id))
        assertEquals("DAYS_OF_WEEK", restoredSchedule.kind)
        assertEquals(monWedFriMask, restoredSchedule.daysOfWeekMask)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            (restoredSchedule.toDomain() as Schedule.DaysOfWeek).days,
        )
    }
}

private fun scheduleEntity(habitId: Long, kind: String, daysOfWeekMask: Int? = null) = ScheduleEntity(
    habitId = habitId,
    kind = kind,
    timesPerWeek = null,
    dayOfWeek = null,
    dayOfMonth = null,
    intervalDays = null,
    anchorDate = null,
    weekStart = 1,
    daysOfWeekMask = daysOfWeekMask,
)
