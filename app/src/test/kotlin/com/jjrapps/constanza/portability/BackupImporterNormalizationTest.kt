package com.jjrapps.constanza.portability

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 2.10 (data-portability: Backup Schema Version Read On Import, Legacy Habit Colour
 * Normalized On Import). Exercises the pure [normalizeHabitColors] directly — no [BackupImporter]
 * instance, no mocked collaborators, matching how [BackupImporterTest] already tests
 * `remapEntrySlotId`. Runs via `./gradlew :app:testDebugUnitTest`.
 */
class BackupImporterNormalizationTest {

    @Test
    fun `schemaVersion 1 normalizes an off-palette legacy colour`() {
        val legacyPurple = 0xFF8E24AA.toInt()
        val currentViolet = 0xFFCBB2FF.toInt()
        val habits = listOf(habitWithColor(legacyPurple))

        val normalized = normalizeHabitColors(habits, schemaVersion = 1)

        assertEquals(currentViolet, normalized.single().colorArgb)
    }

    @Test
    fun `schemaVersion 1 normalizes legacy orange to pink`() {
        val legacyOrange = 0xFFFB8C00.toInt()
        val currentPink = 0xFFFFA8DC.toInt()
        val habits = listOf(habitWithColor(legacyOrange))

        val normalized = normalizeHabitColors(habits, schemaVersion = 1)

        assertEquals(currentPink, normalized.single().colorArgb)
    }

    @Test
    fun `schemaVersion 2 leaves colours byte-identical`() {
        val currentViolet = 0xFFCBB2FF.toInt()
        val habits = listOf(habitWithColor(currentViolet))

        val normalized = normalizeHabitColors(habits, schemaVersion = CURRENT_SCHEMA_VERSION)

        assertEquals(currentViolet, normalized.single().colorArgb)
    }
}

private fun habitWithColor(colorArgb: Int): BackupHabit = BackupHabit(
    id = 1,
    name = "Meditate",
    question = null,
    colorArgb = colorArgb,
    notes = null,
    archived = false,
    archivedAt = null,
    createdAt = "2026-01-01T08:00:00Z",
    sortOrder = 0,
    schedule = BackupSchedule(kind = "DAILY", weekStart = "MONDAY"),
    slots = emptyList(),
    entries = emptyList(),
)
