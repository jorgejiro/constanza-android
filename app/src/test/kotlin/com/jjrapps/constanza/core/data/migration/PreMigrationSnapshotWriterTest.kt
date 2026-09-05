package com.jjrapps.constanza.core.data.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Task 3.4 (design.md decision 4). Runs via `./gradlew :app:testDebugUnitTest`. The cheapest
 * possible place to prove the most dangerous property in the whole change: a read failure inside
 * [PreMigrationSnapshotWriter.write] must never propagate out of it, and must never leave a partial
 * dump behind wearing the final filename. Satisfies `data-portability` spec "Automatic Pre-Migration
 * Snapshot" scenario "Snapshot failure does not block the migration or the app opening".
 *
 * `app/build.gradle.kts`'s `isReturnDefaultValues = true` (work unit 4a) means `android.util.Log`
 * calls return harmlessly here instead of throwing "Method ... not mocked." — no Robolectric needed.
 */
class PreMigrationSnapshotWriterTest {

    private lateinit var targetDir: File

    @BeforeTest
    fun setUp() {
        targetDir = Files.createTempDirectory("pre-migration-snapshot-writer-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        targetDir.deleteRecursively()
    }

    @Test
    fun `write returns false and leaves no file when reading the database throws`() {
        val db = mockk<SupportSQLiteDatabase>()
        every { db.version } returns 1
        every { db.query(any<String>()) } throws RuntimeException("boom")

        val result = PreMigrationSnapshotWriter(targetDir).write(db)

        assertFalse(result, "a read failure must not be reported as a successful snapshot")
        assertFalse(
            File(targetDir, "pre-migration/pre-migration-v1.sql").exists(),
            "no final snapshot file may exist after a failed write",
        )
        assertFalse(
            File(targetDir, "pre-migration/pre-migration-v1.sql.tmp").exists(),
            "the temp file must be deleted on the failure path, not left behind",
        )
    }
}
