package com.jjrapps.constanza.portability

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.data.entity.EntryEntity
import com.jjrapps.constanza.core.data.entity.ReminderSlotEntity
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import com.jjrapps.constanza.reminding.SnoozeDuration
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
}

private fun scheduleEntity(habitId: Long, kind: String) = ScheduleEntity(
    habitId = habitId,
    kind = kind,
    timesPerWeek = null,
    dayOfWeek = null,
    dayOfMonth = null,
    intervalDays = null,
    anchorDate = null,
    weekStart = 1,
)
