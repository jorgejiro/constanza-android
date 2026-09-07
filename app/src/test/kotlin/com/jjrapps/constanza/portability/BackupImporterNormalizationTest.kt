package com.jjrapps.constanza.portability

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 2.10 (data-portability: Backup Schema Version Read On Import, Legacy Habit Colour
 * Normalized On Import), extended by the colour overhaul's second and third habit-colour repaints.
 * Exercises the pure [normalizeHabitColors] directly — no [BackupImporter] instance, no mocked
 * collaborators, matching how [BackupImporterTest] already tests `remapEntrySlotId`. Runs via
 * `./gradlew :app:testDebugUnitTest`.
 *
 * Three colour epochs exist now, and [CURRENT_SCHEMA_VERSION] has moved past all of them. The
 * `schemaVersion 1` cases below chain through *all three* — [HabitColorRemap] first (six pastels ->
 * the 23-preset warm-dark palette), then [HabitColorRetoneRemap] (that palette -> the 22-preset
 * legible-band one), then [HabitColorRetireRemap] (that palette -> the current 21-preset one, minus
 * `BLUE_GREY`) — and their expected values are unchanged from before the second and third epochs
 * existed: both pastels these seeds remap to already sit inside [clampToHabitBand]'s tolerance, so
 * neither later hop moves them. The `schemaVersion 2` cases are unchanged from before the third
 * epoch existed for the same reason: a file at that version already skipped [HabitColorRemap] but
 * still needs [HabitColorRetoneRemap]'s hop, and neither seed there is `BLUE_GREY`, so
 * [HabitColorRetireRemap] leaves them untouched. The `schemaVersion 3` case is new: a file at that
 * version already skipped both earlier epochs (it was already past them) but still needs
 * [HabitColorRetireRemap]'s hop, which did not exist when `3` was [CURRENT_SCHEMA_VERSION].
 *
 * Right-hand values are deliberately spelled as literals rather than as `HabitColor` members: this
 * function's contract is about what each schema version *meant* at the time, not about what the
 * picker offers today (`HabitColorRemap`'s and `HabitColorRetoneRemap`'s own KDoc make the same
 * choice, for the same reason).
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

    /** New coverage for the colour overhaul: a `schemaVersion 2` file already skipped
     *  [HabitColorRemap] (that epoch is behind it) but still holds a 23-preset warm-dark colour that
     *  needs [HabitColorRetoneRemap]'s hop, which did not exist the last time `2` meant "current". */
    @Test
    fun `schemaVersion 2 normalizes an old-preset colour to its retoned counterpart`() {
        val oldRed = 0xFFF44336.toInt()
        val newRed = 0xFFFF6D48.toInt()
        val habits = listOf(habitWithColor(oldRed))

        val normalized = normalizeHabitColors(habits, schemaVersion = 2)

        assertEquals(newRed, normalized.single().colorArgb)
    }

    /** The retired `SILVER` preset gets no explicit `HabitColorRetoneRemap` entry — it falls through
     *  to `clampToHabitBand`, exactly as `HabitColorRetoneRemapTest` pins for the on-device migration.
     *  A `schemaVersion 2` backup holding it must retone the same way on import. */
    @Test
    fun `schemaVersion 2 normalizes the retired silver preset through the contrast clamp`() {
        val retiredSilver = 0xFFE0E0E0.toInt()
        val clampedSilver = 0xFFC2C2C2.toInt()
        val habits = listOf(habitWithColor(retiredSilver))

        val normalized = normalizeHabitColors(habits, schemaVersion = 2)

        assertEquals(clampedSilver, normalized.single().colorArgb)
    }

    /** New coverage for the colour overhaul's third repaint: a `schemaVersion 3` file already
     *  skipped both [HabitColorRemap] and [HabitColorRetoneRemap] (both epochs are behind it) but
     *  still holds the retired `BLUE_GREY` preset, which needs [HabitColorRetireRemap]'s hop that
     *  did not exist the last time `3` meant "current". */
    @Test
    fun `schemaVersion 3 normalizes the retired blue grey preset to cyan`() {
        val retiredBlueGrey = 0xFF849FAC.toInt()
        val cyan = 0xFF00ABBD.toInt()
        val habits = listOf(habitWithColor(retiredBlueGrey))

        val normalized = normalizeHabitColors(habits, schemaVersion = 3)

        assertEquals(cyan, normalized.single().colorArgb)
    }

    @Test
    fun `a current-schemaVersion file leaves colours byte-identical`() {
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
