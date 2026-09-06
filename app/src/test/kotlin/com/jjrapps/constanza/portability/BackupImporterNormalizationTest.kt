package com.jjrapps.constanza.portability

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 2.10 (data-portability: Backup Schema Version Read On Import, Legacy Habit Colour
 * Normalized On Import). Exercises the pure [normalizeHabitColors] directly — no [BackupImporter]
 * instance, no mocked collaborators, matching how [BackupImporterTest] already tests
 * `remapEntrySlotId`. Runs via `./gradlew :app:testDebugUnitTest`.
 *
 * The right-hand values below are `schemaVersion` 2's colours, and they are deliberately spelled as
 * literals rather than as `HabitColor` members. They stopped being the offered palette when the
 * picker moved to Material's families plus a free custom colour, and that changed nothing here:
 * this function's contract is about what each schema version *meant*, not about what the picker
 * offers today. A colour that is no longer a preset now imports as a custom colour instead of being
 * orphaned, which is why no `schemaVersion` 3 normalization step exists to test.
 */
class BackupImporterNormalizationTest {

    @Test
    fun `schemaVersion 1 normalizes an off-palette legacy colour`() {
        val legacyPurple = 0xFF8E24AA.toInt()
        val schemaV2Violet = 0xFFCBB2FF.toInt()
        val habits = listOf(habitWithColor(legacyPurple))

        val normalized = normalizeHabitColors(habits, schemaVersion = 1)

        assertEquals(schemaV2Violet, normalized.single().colorArgb)
    }

    @Test
    fun `schemaVersion 1 normalizes legacy orange to pink`() {
        val legacyOrange = 0xFFFB8C00.toInt()
        val schemaV2Pink = 0xFFFFA8DC.toInt()
        val habits = listOf(habitWithColor(legacyOrange))

        val normalized = normalizeHabitColors(habits, schemaVersion = 1)

        assertEquals(schemaV2Pink, normalized.single().colorArgb)
    }

    @Test
    fun `schemaVersion 2 leaves colours byte-identical`() {
        val schemaV2Violet = 0xFFCBB2FF.toInt()
        val habits = listOf(habitWithColor(schemaV2Violet))

        val normalized = normalizeHabitColors(habits, schemaVersion = CURRENT_SCHEMA_VERSION)

        assertEquals(schemaV2Violet, normalized.single().colorArgb)
    }

    /** data-portability "Round-Trip Fidelity": a current-version file's colours are imported
     *  unchanged whatever they are. That used to be a claim about six palette values; the custom
     *  picker makes it a claim about arbitrary ARGB ints, so it is asserted with one. */
    @Test
    fun `a current-version file imports an arbitrary custom colour unchanged`() {
        val customColor = 0xFF3D2B1F.toInt()
        val habits = listOf(habitWithColor(customColor))

        val normalized = normalizeHabitColors(habits, schemaVersion = CURRENT_SCHEMA_VERSION)

        assertEquals(customColor, normalized.single().colorArgb)
    }
}

private fun habitWithColor(colorArgb: Int): BackupHabit = BackupHabit(
    id = 1,
    name = "Meditate",
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
