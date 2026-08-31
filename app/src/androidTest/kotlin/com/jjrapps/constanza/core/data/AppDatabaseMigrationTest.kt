package com.jjrapps.constanza.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

private const val TEST_DB_NAME = "migration-test"

/**
 * Task 3.7: establishes the `MigrationTestHelper` harness against the checked-in
 * `app/schemas/1.json` (design.md §8.3) that the future v1→v2 additive migration will build on.
 * There is no migration to run yet at `version = 1` — this test's whole job is proving the
 * harness can materialize a version-1 database purely from the exported schema file, which is
 * exactly what a `v1 -> v2` `MIGRATION_1_2` test will need as its starting point.
 */
class AppDatabaseMigrationTest {

    @get:Rule
    val migrationTestHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun version1SchemaCreatesFromTheCheckedInExport() {
        migrationTestHelper.createDatabase(TEST_DB_NAME, version = 1).close()
    }
}
