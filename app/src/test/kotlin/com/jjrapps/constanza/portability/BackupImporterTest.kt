package com.jjrapps.constanza.portability

import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
import com.jjrapps.constanza.scheduling.OccurrencePlanner
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [BackupImporter.parseAndValidate] does no database I/O (task 7.3's own split — see its KDoc),
 * so every constructor dependency below can stay an untouched `mockk()`: none of these scenarios
 * exercise anything but pure JSON parsing and structural validation. Runs via
 * `./gradlew :app:testDebugUnitTest`.
 */
class BackupImporterTest {

    private val importer = BackupImporter(
        daos = mockk(),
        database = mockk<AppDatabase>(),
        alarmScheduler = mockk<AlarmScheduler>(),
        occurrencePlanner = mockk<OccurrencePlanner>(),
        settingsStore = mockk<ReminderSettingsStore>(),
    )

    @Test
    fun `a valid backup parses without throwing`() {
        val backup = importer.parseAndValidate(validBackupJson())

        assertEquals(1, backup.habits.size)
        assertEquals("Meditate", backup.habits.single().name)
    }

    @Test
    fun `unparseable text is rejected as malformed before any formatVersion check`() {
        assertFailsWith<MalformedBackupException> { importer.parseAndValidate("not json at all") }
    }

    @Test
    fun `json missing required fields is rejected as malformed`() {
        assertFailsWith<MalformedBackupException> { importer.parseAndValidate("""{"format":"constanza.backup"}""") }
    }

    @Test
    fun `a formatVersion newer than supported refuses the whole import`() {
        val json = validBackupJson().replace(""""formatVersion": 1""", """"formatVersion": 99""")

        val error = assertFailsWith<UnsupportedBackupVersionException> { importer.parseAndValidate(json) }
        assertTrue(error.message.orEmpty().contains("99"), "message should name the offending version")
    }

    @Test
    fun `an entry referencing a slot id absent from its habit's slots is malformed`() {
        val json = validBackupJson().replace(""""slotId": 10""", """"slotId": 999""")

        assertFailsWith<MalformedBackupException> { importer.parseAndValidate(json) }
    }

    @Test
    fun `an entry with a null slot id is valid regardless of the habit's own slots`() {
        val json = validBackupJson().replace(""""slotId": 10""", "\"slotId\": null")

        val backup = importer.parseAndValidate(json)

        assertEquals(null, backup.habits.single().entries.single().slotId)
    }

    // remapEntrySlotId — task 7.3's ID-remapping, pure and independent of BackupImporter itself.

    @Test
    fun `remapEntrySlotId resolves an old slot id through the map`() {
        assertEquals(99L, remapEntrySlotId(oldSlotId = 5L, slotIdMap = mapOf(5L to 99L)))
    }

    @Test
    fun `remapEntrySlotId returns the sentinel for a null slot id`() {
        assertEquals(0L, remapEntrySlotId(oldSlotId = null, slotIdMap = emptyMap()))
    }

    @Test
    fun `remapEntrySlotId fails loudly rather than silently dropping an unmapped reference`() {
        assertFailsWith<IllegalArgumentException> { remapEntrySlotId(oldSlotId = 7L, slotIdMap = emptyMap()) }
    }
}

private fun validBackupJson(): String = """
    {
      "format": "constanza.backup",
      "formatVersion": 1,
      "schemaVersion": 1,
      "exportedAt": "2026-08-31T10:15:00Z",
      "exportedAtZone": "Europe/Madrid",
      "settings": { "defaultSnoozeMinutes": 20 },
      "habits": [
        {
          "id": 1,
          "name": "Meditate",
          "question": "Did you meditate today?",
          "colorArgb": -14575885,
          "notes": null,
          "archived": false,
          "archivedAt": null,
          "createdAt": "2026-01-01T08:00:00Z",
          "sortOrder": 0,
          "schedule": { "kind": "TIMES_PER_DAY", "weekStart": "MONDAY" },
          "slots": [ { "id": 10, "minuteOfDay": 480, "enabled": true } ],
          "entries": [
            { "date": "2026-08-30", "slotId": 10, "status": "COMPLETED",
              "value": null, "answeredAt": "2026-08-30T08:03:00Z", "source": "NOTIFICATION" }
          ]
        }
      ]
    }
""".trimIndent()
