package com.jjrapps.constanza.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.core.data.migration.AppMigrations
import com.jjrapps.constanza.core.data.migration.HabitColorRemap
import com.jjrapps.constanza.core.data.migration.PreMigrationSnapshotWriter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TEST_DB_NAME = "migration-test"

/** An int with no counterpart in [HabitColorRemap.LEGACY_TO_CURRENT] — must survive the migration
 *  unchanged (the `WHERE colorArgb IN (...)` guard in `AppMigrations.MIGRATION_1_2`). */
private const val UNMAPPED_COLOR_ARGB = 0x00123456

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
}
