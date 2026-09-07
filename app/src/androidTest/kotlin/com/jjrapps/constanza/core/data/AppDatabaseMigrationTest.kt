package com.jjrapps.constanza.core.data

import androidx.compose.ui.graphics.Color
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.core.data.entity.ScheduleEntity
import com.jjrapps.constanza.core.data.mapper.toDomain
import com.jjrapps.constanza.core.data.migration.AppMigrations
import com.jjrapps.constanza.core.data.migration.HabitColorRemap
import com.jjrapps.constanza.core.data.migration.HabitColorRetireRemap
import com.jjrapps.constanza.core.data.migration.HabitColorRetoneRemap
import com.jjrapps.constanza.core.data.migration.PreMigrationSnapshotWriter
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.HABIT_BAND_CEILING
import com.jjrapps.constanza.core.ui.theme.HABIT_BAND_FLOOR
import com.jjrapps.constanza.core.ui.theme.HABIT_BAND_TOLERANCE
import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.core.ui.theme.contrastRatio
import com.jjrapps.constanza.domain.model.Schedule
import java.io.File
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TEST_DB_NAME = "migration-test"

/** An int with no counterpart in [HabitColorRemap.LEGACY_TO_CURRENT] — must survive the migration
 *  unchanged (the `WHERE colorArgb IN (...)` guard in `AppMigrations.MIGRATION_1_2`). */
private const val UNMAPPED_COLOR_ARGB = 0x00123456

/** The retired `SILVER` preset (Grey 300) — literal rather than `HabitColor.SILVER`, which no longer
 *  exists, for the same reason [HabitColorRetoneRemap]'s own KDoc gives. */
private const val RETIRED_SILVER_ARGB = 0xFFE0E0E0.toInt()

/** [HabitColorRetoneRemap]'s measured fall-through result for [RETIRED_SILVER_ARGB]. */
private const val CLAMPED_SILVER_ARGB = 0xFFC2C2C2.toInt()

/** An arbitrary custom colour no version of this palette ever offered — navy, well outside the
 *  legible band (1.48:1 against `ConstanzaColors.Background`), the same case `HabitColorBandTest`
 *  pins on the JVM side. */
private const val OUT_OF_BAND_CUSTOM_ARGB = 0xFF1A237E.toInt()

/** The retired `BLUE_GREY` preset (Blue Grey 400) — literal rather than `HabitColor.BLUE_GREY`,
 *  which no longer exists, for the same reason [HabitColorRetireRemap]'s own KDoc gives. */
private const val RETIRED_BLUE_GREY_ARGB = 0xFF849FAC.toInt()

/**
 * Task 3.7 (base harness), **extended** by task 2.11 and task 3.5 — never recreated (correction
 * C2): establishes the `MigrationTestHelper` harness against the checked-in `app/schemas/1.json`
 * (design.md §8.3), then (2.11) proves `AppMigrations.migration1To2` actually rewrites the seeded
 * rows' values, and (3.5) proves the pre-migration snapshot file exists and holds the legacy rows.
 */
class AppDatabaseMigrationTest {

    @get:Rule
    val migrationTestHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val targetFilesDir: File
        get() = InstrumentationRegistry.getInstrumentation().targetContext.filesDir

    /** Builds a fresh migration instance per test — mirrors `DatabaseModule.provideAppDatabase`'s
     *  real call shape (task 3.3), rather than reusing a single instance across tests. */
    private fun migration1To2() = AppMigrations.migration1To2(PreMigrationSnapshotWriter(targetFilesDir))

    /** Same reasoning as [migration1To2]: a fresh instance per test. */
    private fun migration2To3() = AppMigrations.migration2To3(PreMigrationSnapshotWriter(targetFilesDir))

    /** Same reasoning as [migration1To2]: a fresh instance per test. */
    private fun migration3To4() = AppMigrations.migration3To4(PreMigrationSnapshotWriter(targetFilesDir))

    /** Same reasoning as [migration1To2]: a fresh instance per test. */
    private fun migration4To5() = AppMigrations.migration4To5(PreMigrationSnapshotWriter(targetFilesDir))

    /** Same reasoning as [migration1To2]: a fresh instance per test. */
    private fun migration5To6() = AppMigrations.migration5To6(PreMigrationSnapshotWriter(targetFilesDir))

    @Test
    fun version1SchemaCreatesFromTheCheckedInExport() {
        migrationTestHelper.createDatabase(TEST_DB_NAME, version = 1).close()
    }

    /**
     * Task 2.11: seeds all six legacy colours plus one unmapped colour into a real `version = 1`
     * database built from the checked-in `1.json`, runs the real `migration1To2`, then **reads the
     * post-migration rows back and asserts their actual `colorArgb` VALUES** — not merely that
     * `runMigrationsAndValidate` returned without throwing. A green-but-no-op migration (the sign
     * trap `AppMigrations.migration1To2`'s KDoc documents) would still pass a completion-only
     * assertion; it cannot pass this one. Satisfies habit-management spec "Persisted Habit Colour
     * Stays On-Palette Across A Palette Change".
     */
    @Test
    fun migration1To2RewritesEveryLegacyColourToItsCurrentCounterpart() {
        val legacyToExpected = HabitColorRemap.LEGACY_TO_CURRENT.toList()

        migrationTestHelper.createDatabase(TEST_DB_NAME, version = 1).use { db ->
            legacyToExpected.forEachIndexed { index, (legacyColor, _) ->
                seedHabit(db, id = index + 1L, colorArgb = legacyColor)
            }
            seedHabit(db, id = (legacyToExpected.size + 1).toLong(), colorArgb = UNMAPPED_COLOR_ARGB)
        }

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB_NAME,
            2,
            true,
            migration1To2(),
        )

        migratedDb.query("SELECT id, colorArgb FROM habits ORDER BY id").use { cursor ->
            legacyToExpected.forEachIndexed { index, (_, expectedColor) ->
                assertTrue("expected a row for legacy seed index $index", cursor.moveToNext())
                assertEquals((index + 1).toLong(), cursor.getLong(0))
                assertEquals("legacy colour at row ${index + 1} was not remapped", expectedColor, cursor.getInt(1))
            }
            assertTrue("expected the unmapped-colour row", cursor.moveToNext())
            assertEquals("an unmapped colour must survive unchanged", UNMAPPED_COLOR_ARGB, cursor.getInt(1))
        }
    }

    /**
     * Task 3.5 (design.md decision 4, spec `Automatic Pre-Migration Snapshot`). The real
     * `migration1To2` — run against the same checked-in `1.json` harness as the test above — writes
     * `pre-migration-v1.sql` as its first statement, before the `UPDATE`. Asserts the file exists
     * and that its contents are the **pre-migration** (legacy) colour, proving the dump happened
     * before the rewrite rather than merely at some point during the migration.
     */
    @Test
    fun migration1To2WritesAPreMigrationSnapshotContainingTheLegacyRows() {
        val snapshotFile = File(targetFilesDir, "pre-migration/pre-migration-v1.sql")
        snapshotFile.delete()
        val legacyColor = HabitColorRemap.LEGACY_TO_CURRENT.keys.first()

        migrationTestHelper.createDatabase(TEST_DB_NAME, version = 1).use { db ->
            seedHabit(db, id = 1L, colorArgb = legacyColor)
        }

        migrationTestHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, migration1To2())

        assertTrue("expected the pre-migration snapshot file to exist at $snapshotFile", snapshotFile.exists())
        val snapshotContents = snapshotFile.readText()
        assertTrue(
            "snapshot must contain the pre-migration (legacy) colour, not the post-migration one",
            snapshotContents.contains(legacyColor.toString()),
        )
    }

    private fun seedHabit(db: SupportSQLiteDatabase, id: Long, colorArgb: Int) {
        db.execSQL(
            "INSERT INTO habits (id, name, question, colorArgb, notes, archived, archivedAt, createdAt, sortOrder) " +
                "VALUES (?, ?, NULL, ?, NULL, 0, NULL, ?, 0)",
            arrayOf<Any>(id, "Habit $id", colorArgb, "2026-01-01T08:00:00Z"),
        )
    }

    /**
     * Task 3.1 (design.md D1/D2, `data-portability`: Child Records Survive A Schema Migration).
     * Seeds a real `version = 2` database (still carrying `question`, hence its own literal INSERT
     * rather than reusing [seedHabit]'s v1-shaped one) with two habits, each carrying exactly one
     * row in every CASCADE-child table of `habits` (`schedules`, `reminder_slots`, `entries`,
     * `reminder_occurrences` — `Entities.kt`), runs the real `migration2To3`, and asserts three
     * things: `question` is gone from `habits`, the pre-migration snapshot fired, and every child
     * row survived, unmixed between the two habits. That last assertion is what actually catches
     * the CASCADE trap design.md D1/D2 exist to prevent — a naive rebuild would pass every other
     * check here while silently emptying all four tables.
     */
    @Test
    fun migration2To3DropsTheQuestionColumnAndEveryHabitsChildRowsSurviveUnmixed() {
        val habitOneId = 1L
        val habitTwoId = 2L
        val slotOneId = 10L
        val slotTwoId = 20L

        migrationTestHelper.createDatabase(TEST_DB_NAME, version = 2).use { db ->
            seedHabitWithChildRows(db, habitId = habitOneId, slotId = slotOneId, occurrenceId = 100L)
            seedHabitWithChildRows(db, habitId = habitTwoId, slotId = slotTwoId, occurrenceId = 200L)
        }

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(TEST_DB_NAME, 3, true, migration2To3())

        migratedDb.query("PRAGMA table_info(habits)").use { cursor ->
            val columnNames = generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }.toList()
            assertFalse("question must be dropped from habits by the v2 to v3 rebuild", "question" in columnNames)
        }
        migratedDb.query("SELECT id FROM habits ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals("the first habit row must survive the rebuild", habitOneId, cursor.getLong(0))
            assertTrue(cursor.moveToNext())
            assertEquals("the second habit row must survive the rebuild", habitTwoId, cursor.getLong(0))
        }
        assertEveryChildRowSurvives(migratedDb, habitId = habitOneId, slotId = slotOneId)
        assertEveryChildRowSurvives(migratedDb, habitId = habitTwoId, slotId = slotTwoId)

        val snapshotFile = File(targetFilesDir, "pre-migration/pre-migration-v2.sql")
        assertTrue("expected the v2 pre-migration snapshot file to exist at $snapshotFile", snapshotFile.exists())
    }

    /** One habit plus exactly one row in each of `habits`' four CASCADE-child tables, table-driven
     *  so a second habit (needed to prove records survive independently) costs one extra call
     *  rather than a second copy of this SQL. */
    private fun seedHabitWithChildRows(db: SupportSQLiteDatabase, habitId: Long, slotId: Long, occurrenceId: Long) {
        db.execSQL(
            "INSERT INTO habits (id, name, question, colorArgb, notes, archived, archivedAt, createdAt, sortOrder) " +
                "VALUES (?, ?, NULL, 0, NULL, 0, NULL, ?, 0)",
            arrayOf<Any>(habitId, "Habit $habitId", "2026-01-01T08:00:00Z"),
        )
        db.execSQL(
            "INSERT INTO schedules (habitId, kind, timesPerWeek, dayOfWeek, dayOfMonth, intervalDays, " +
                "anchorDate, weekStart) VALUES (?, 'DAILY', NULL, NULL, NULL, NULL, NULL, 1)",
            arrayOf<Any>(habitId),
        )
        db.execSQL(
            "INSERT INTO reminder_slots (id, habitId, minuteOfDay, enabled) VALUES (?, ?, 480, 1)",
            arrayOf<Any>(slotId, habitId),
        )
        db.execSQL(
            "INSERT INTO entries (habitId, date, slotId, status, value, answeredAt, source) " +
                "VALUES (?, '2026-09-01', ?, 'COMPLETED', NULL, '2026-09-01T08:00:00Z', 'IN_APP')",
            arrayOf<Any>(habitId, slotId),
        )
        db.execSQL(
            "INSERT INTO reminder_occurrences (id, habitId, slotId, scheduledDate, scheduledAtEpochMs, " +
                "state, exact, snoozeUntilEpochMs, snoozeCount, notifiedAtEpochMs, resolveDeadlineMs) " +
                "VALUES (?, ?, ?, '2026-09-01', 0, 'ARMED', 1, NULL, 0, NULL, 0)",
            arrayOf<Any>(occurrenceId, habitId, slotId),
        )
    }

    /** Exactly one row per CASCADE-child table, scoped to [habitId] — proves survival AND that
     *  nothing bled between the two seeded habits (`data-portability`'s "each habit's records
     *  survive independently" scenario). */
    private fun assertEveryChildRowSurvives(db: SupportSQLiteDatabase, habitId: Long, slotId: Long) {
        assertRowCount(db, "SELECT COUNT(*) FROM schedules WHERE habitId = ?", habitId)
        assertRowCount(db, "SELECT COUNT(*) FROM reminder_slots WHERE habitId = ? AND id = ?", habitId, slotId)
        assertRowCount(db, "SELECT COUNT(*) FROM entries WHERE habitId = ? AND slotId = ?", habitId, slotId)
        assertRowCount(db, "SELECT COUNT(*) FROM reminder_occurrences WHERE habitId = ? AND slotId = ?", habitId, slotId)
    }

    private fun assertRowCount(db: SupportSQLiteDatabase, sql: String, vararg args: Long) {
        db.query(SimpleSQLiteQuery(sql, args.map { it as Any }.toTypedArray())).use { cursor ->
            cursor.moveToFirst()
            assertEquals("$sql ${args.toList()}", 1, cursor.getInt(0))
        }
    }

    /**
     * Task 3.2 (weekday-only-schedule design.md decision 3). Seeds a real `version = 3` database
     * with a legacy `kind = 'WEEKLY', dayOfWeek = 3` (Wednesday) row, runs the real
     * `migration3To4`, and asserts the rewritten row's actual `kind`/`daysOfWeekMask` VALUES, that
     * the pre-migration snapshot fired, and that the row reads back through `ScheduleEntity.toDomain()`
     * as `Schedule.DaysOfWeek(setOf(WEDNESDAY))` — not merely that the migration completed.
     */
    @Test
    fun migration3To4RewritesALegacyWeeklyRowIntoADaysOfWeekBitmask() {
        val habitId = 1L

        migrationTestHelper.createDatabase(TEST_DB_NAME, version = 3).use { db ->
            seedHabitV3(db, id = habitId)
            db.execSQL(
                "INSERT INTO schedules (habitId, kind, timesPerWeek, dayOfWeek, dayOfMonth, " +
                    "intervalDays, anchorDate, weekStart) VALUES (?, 'WEEKLY', NULL, 3, NULL, NULL, NULL, 1)",
                arrayOf<Any>(habitId),
            )
        }

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(TEST_DB_NAME, 4, true, migration3To4())

        migratedDb.query(
            SimpleSQLiteQuery("SELECT kind, daysOfWeekMask FROM schedules WHERE habitId = ?", arrayOf(habitId)),
        ).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals("DAYS_OF_WEEK", cursor.getString(0))
            // Wednesday.value == 3, bit n == value - 1, so 1 shl 2 == 4.
            assertEquals(4, cursor.getInt(1))
        }

        val snapshotFile = File(targetFilesDir, "pre-migration/pre-migration-v3.sql")
        assertTrue("expected the v3 pre-migration snapshot file to exist at $snapshotFile", snapshotFile.exists())

        val rewrittenEntity = ScheduleEntity(
            habitId = habitId, kind = "DAYS_OF_WEEK", timesPerWeek = null, dayOfWeek = 3,
            dayOfMonth = null, intervalDays = null, anchorDate = null, weekStart = 1, daysOfWeekMask = 4,
        )
        assertEquals(Schedule.DaysOfWeek(days = setOf(DayOfWeek.WEDNESDAY)), rewrittenEntity.toDomain())
    }

    /**
     * Task 3.3 (weekday-only-schedule design.md decision 3). `dayOfWeek` is nullable and `kind`
     * carries no `CHECK` constraint, so a `WEEKLY` row with a `NULL` day is representable and would
     * otherwise silently survive the `UPDATE`, only to crash on the next real read. Asserts the
     * migration itself throws — the guard lives in `migration3To4`, not only in this test.
     */
    @Test
    fun migration3To4FailsClosedWhenAWeeklyRowHasNoDayOfWeek() {
        val habitId = 1L

        migrationTestHelper.createDatabase(TEST_DB_NAME, version = 3).use { db ->
            seedHabitV3(db, id = habitId)
            db.execSQL(
                "INSERT INTO schedules (habitId, kind, timesPerWeek, dayOfWeek, dayOfMonth, " +
                    "intervalDays, anchorDate, weekStart) VALUES (?, 'WEEKLY', NULL, NULL, NULL, NULL, NULL, 1)",
                arrayOf<Any>(habitId),
            )
        }

        assertThrows(IllegalStateException::class.java) {
            migrationTestHelper.runMigrationsAndValidate(TEST_DB_NAME, 4, true, migration3To4())
        }
    }

    /** A `version = 3` `habits` row — `question` is already gone by this version, unlike
     *  [seedHabitWithChildRows]'s `version = 2` shape. */
    private fun seedHabitV3(db: SupportSQLiteDatabase, id: Long) {
        db.execSQL(
            "INSERT INTO habits (id, name, colorArgb, notes, archived, archivedAt, createdAt, sortOrder) " +
                "VALUES (?, ?, 0, NULL, 0, NULL, ?, 0)",
            arrayOf<Any>(id, "Habit $id", "2026-01-01T08:00:00Z"),
        )
    }

    /**
     * The colour overhaul's second habit-colour repaint (`AppMigrations.migration4To5`), tested the
     * same way [migration1To2RewritesEveryLegacyColourToItsCurrentCounterpart] tests the first one:
     * seed real rows, run the real migration, then read the post-migration VALUES back rather than
     * merely trusting `runMigrationsAndValidate` returned without throwing.
     *
     * Three cases in one seeded database, matching every fall-through [HabitColorRetoneRemap.normalize]
     * defines: every one of the 17 explicitly-remapped legacy presets comes out holding its retoned
     * counterpart; the retired `SILVER` preset — deliberately absent from
     * [HabitColorRetoneRemap.LEGACY_TO_CURRENT] — comes out `#C2C2C2` via the contrast clamp
     * fall-through; and an arbitrary out-of-band custom colour (never any version of this palette)
     * comes out somewhere inside the legible band rather than at an exact pinned hex, since the clamp's
     * own contract ([clampToHabitBand]'s KDoc) is "inside the band", not "this exact byte".
     */
    @Test
    fun migration4To5RetonesEveryLegacyPresetAndClampsTheRest() {
        val legacyToExpected = HabitColorRetoneRemap.LEGACY_TO_CURRENT.toList()
        val retiredSilverId = (legacyToExpected.size + 1).toLong()
        val outOfBandId = (legacyToExpected.size + 2).toLong()

        migrationTestHelper.createDatabase(TEST_DB_NAME, version = 4).use { db ->
            legacyToExpected.forEachIndexed { index, (legacyColor, _) ->
                seedHabitV4(db, id = index + 1L, colorArgb = legacyColor)
            }
            seedHabitV4(db, id = retiredSilverId, colorArgb = RETIRED_SILVER_ARGB)
            seedHabitV4(db, id = outOfBandId, colorArgb = OUT_OF_BAND_CUSTOM_ARGB)
        }

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, migration4To5())

        migratedDb.query("SELECT id, colorArgb FROM habits ORDER BY id").use { cursor ->
            legacyToExpected.forEachIndexed { index, (_, expectedColor) ->
                assertTrue("expected a row for legacy seed index $index", cursor.moveToNext())
                assertEquals((index + 1).toLong(), cursor.getLong(0))
                assertEquals("legacy colour at row ${index + 1} was not retoned", expectedColor, cursor.getInt(1))
            }

            assertTrue("expected the retired-SILVER row", cursor.moveToNext())
            assertEquals(retiredSilverId, cursor.getLong(0))
            assertEquals(
                "SILVER must fall through to the contrast clamp",
                CLAMPED_SILVER_ARGB,
                cursor.getInt(1),
            )

            assertTrue("expected the out-of-band custom colour row", cursor.moveToNext())
            assertEquals(outOfBandId, cursor.getLong(0))
            val clampedRatio = contrastRatio(Color(cursor.getInt(1)), ConstanzaColors.Background)
            assertTrue(
                "an out-of-band custom colour must land inside the legible band, measured %.2f:1".format(clampedRatio),
                clampedRatio in (HABIT_BAND_FLOOR - HABIT_BAND_TOLERANCE)..(HABIT_BAND_CEILING + HABIT_BAND_TOLERANCE),
            )
        }

        val snapshotFile = File(targetFilesDir, "pre-migration/pre-migration-v4.sql")
        assertTrue("expected the v4 pre-migration snapshot file to exist at $snapshotFile", snapshotFile.exists())
    }

    /** A `version = 4` `habits` row, parameterised by [colorArgb] — the `habits` table shape is
     *  unchanged from [seedHabitV3]'s version-3 one (`migration3To4` only touches `schedules`). */
    private fun seedHabitV4(db: SupportSQLiteDatabase, id: Long, colorArgb: Int) {
        db.execSQL(
            "INSERT INTO habits (id, name, colorArgb, notes, archived, archivedAt, createdAt, sortOrder) " +
                "VALUES (?, ?, ?, NULL, 0, NULL, ?, 0)",
            arrayOf<Any>(id, "Habit $id", colorArgb, "2026-01-01T08:00:00Z"),
        )
    }

    /**
     * The colour overhaul's third habit-colour repaint (`AppMigrations.migration5To6`), tested the
     * same way [migration4To5RetonesEveryLegacyPresetAndClampsTheRest] tests the second one: seed
     * real rows, run the real migration, then read the post-migration VALUES back rather than merely
     * trusting `runMigrationsAndValidate` returned without throwing.
     *
     * Two cases: a habit stored on the retired `BLUE_GREY` preset (`#849FAC`) comes out on
     * [HabitColor.CYAN] (`#00ABBD`) — the one entry [HabitColorRetireRemap.LEGACY_TO_CURRENT]
     * carries — and a habit already stored on a current preset that did not move
     * ([HabitColor.CYAN] itself) survives byte-identical, proving the migration does not touch rows
     * it has no reason to.
     */
    @Test
    fun migration5To6RetiresBlueGreyAndLeavesOtherPresetsUntouched() {
        val blueGreyHabitId = 1L
        val cyanHabitId = 2L
        val expectedCyan = HabitColorRetireRemap.LEGACY_TO_CURRENT.getValue(RETIRED_BLUE_GREY_ARGB)

        migrationTestHelper.createDatabase(TEST_DB_NAME, version = 5).use { db ->
            seedHabitV4(db, id = blueGreyHabitId, colorArgb = RETIRED_BLUE_GREY_ARGB)
            seedHabitV4(db, id = cyanHabitId, colorArgb = HabitColor.CYAN.argb)
        }

        val migratedDb = migrationTestHelper.runMigrationsAndValidate(TEST_DB_NAME, 6, true, migration5To6())

        migratedDb.query("SELECT id, colorArgb FROM habits ORDER BY id").use { cursor ->
            assertTrue("expected the retired-BLUE_GREY row", cursor.moveToNext())
            assertEquals(blueGreyHabitId, cursor.getLong(0))
            assertEquals("BLUE_GREY must be retired onto CYAN", expectedCyan, cursor.getInt(1))

            assertTrue("expected the untouched CYAN row", cursor.moveToNext())
            assertEquals(cyanHabitId, cursor.getLong(0))
            assertEquals(
                "a current preset that did not move must survive byte-identical",
                HabitColor.CYAN.argb,
                cursor.getInt(1),
            )
        }

        val snapshotFile = File(targetFilesDir, "pre-migration/pre-migration-v5.sql")
        assertTrue("expected the v5 pre-migration snapshot file to exist at $snapshotFile", snapshotFile.exists())
    }
}
