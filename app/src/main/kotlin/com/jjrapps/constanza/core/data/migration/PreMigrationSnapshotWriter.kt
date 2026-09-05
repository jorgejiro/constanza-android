package com.jjrapps.constanza.core.data.migration

import android.database.Cursor
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "PreMigrationSnapshot"
private const val SNAPSHOT_SUBDIR = "pre-migration"

/** SQLite's own bookkeeping tables and Room's identity-hash table — replaying a stale
 *  `room_master_table` row would be actively harmful, not merely useless (design.md decision 4). */
private const val SKIPPED_TABLE_PREFIX = "sqlite_"
private val SKIPPED_EXACT_TABLE_NAMES = setOf("android_metadata", "room_master_table")

/**
 * Task 3.1 (design.md decision 4). Dumps every user table of [db] to a replayable `.sql` file
 * *before* [AppMigrations.migration1To2] mutates a single row — the recovery artifact
 * `config.yaml`'s carried-forward item 7.5 asked for, and what the `data-portability` spec's
 * "Automatic Pre-Migration Snapshot" requirement calls for.
 *
 * **This is the most dangerous path in the whole change (design.md decision 4), and [write] is
 * built around never being the thing that bricks a database.** Five load-bearing properties:
 *
 * 1. **Catches [Exception], deliberately NOT [Throwable].** `SQLiteException`, `IOException`, a
 *    stray `RuntimeException` from a malformed cursor — all recoverable, all swallowed. An [Error]
 *    such as `OutOfMemoryError` is allowed to propagate: Room's migration transaction then rolls
 *    back cleanly and the file stays a valid, openable v1 database. Swallowing a JVM-level failure
 *    to keep writing to disk mid-migration would be *worse* than the abort. **Do not "fix" this
 *    into `catch (Throwable e)`** — it is a stated decision, not an oversight.
 * 2. **Temp file, then atomic rename**, and only after the last row. `pre-migration-v1.sql.tmp` is
 *    written first in `<targetDir>/pre-migration/`; [Files.move] with `ATOMIC_MOVE` renames it to
 *    `pre-migration-v1.sql` only once every row has been written, so nothing ever observes a
 *    partial dump wearing the final name. The temp file is deleted on the failure path.
 * 3. **Streamed row by row** through a single [BufferedWriter] — nothing accumulates in memory, so
 *    a large database degrades in time, never in `OutOfMemoryError`. Every [Cursor] opened here is
 *    closed via [use].
 * 4. **Version-derived filename, no clock.** No `System.currentTimeMillis()` — banned by
 *    `detekt.yml`'s `ForbiddenMethodCall` plus this project's `TimeProvider` convention — and
 *    unnecessary here: a migration runs at most once per install, so injecting a `TimeProvider` to
 *    name a file written exactly once would be ceremony, not correctness. The name itself is
 *    `pre-migration-v${db.version}.sql` (design.md decision 3), not a hardcoded `v1` constant: on a
 *    1→3 upgrade Room runs `migration1To2` then `migration2To3` against the SAME [db] handed to
 *    `migrate()` in each step, and [SupportSQLiteDatabase.getVersion] keeps reporting the
 *    PRE-migration version throughout that call — Room does not bump it until after `migrate()`
 *    returns — so the 1→2 step still writes `pre-migration-v1.sql` byte-identically and the 2→3
 *    step writes `pre-migration-v2.sql` instead of silently overwriting it under the same name.
 * 5. **Its result never surfaces as a migration failure.** [write] returns `false` instead of
 *    throwing; [AppMigrations.migration1To2] discards that result and adds no logging of its own.
 *    Success is silent — no log call at all. Failure logs once, at WARN, via [Log.w]. Neither path
 *    is user-visible (design.md decision 4: no toast, no notification, no persisted flag, on
 *    success or failure).
 *
 * Format: `sqlite_master.sql` verbatim as the `CREATE TABLE` line for each table, then one
 * `INSERT INTO` per row with every value escaped by [Cursor.getType] (`NULL`, numeric verbatim,
 * `''`-doubled text, `x'…'` blobs). Uses the [db] handed to `migrate()`; never opens a second
 * connection.
 */
internal class PreMigrationSnapshotWriter(private val targetDir: File) {

    /** Never throws for any recoverable cause. Returns `false` when no snapshot was written. */
    fun write(db: SupportSQLiteDatabase): Boolean {
        val snapshotDir = File(targetDir, SNAPSHOT_SUBDIR)
        // Declared outside the try, but only ever ASSIGNED inside it: reading [db.version] itself
        // is exactly the kind of recoverable database access point 1 below promises never escapes
        // this method, so it must fail through the same catch as everything else, not before it.
        var tempFile: File? = null
        return try {
            val snapshotFileName = "pre-migration-v${db.version}.sql"
            tempFile = File(snapshotDir, "$snapshotFileName.tmp")
            snapshotDir.mkdirs()
            BufferedWriter(FileWriter(tempFile)).use { writer -> dumpTables(db, writer) }
            val finalFile = File(snapshotDir, snapshotFileName)
            Files.move(
                tempFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            true
        } catch (expectedFailure: Exception) {
            // Deviation, flagged loudly (same class of issue as unit 1's `Color.kt` rename and
            // unit 2/3's `object`-not-`class` calls): detekt's default `TooGenericExceptionCaught`
            // flags any catch of a name in its exceptionNames list (`Exception` among them). The
            // fix is the rule's own configured escape — `allowedExceptionNameRegex` (default
            // `_|(ignore|expected).*`) — not `@Suppress`, for the same reason decision 1 rejected
            // one for `MagicNumber`. `expectedFailure` also states the intent plainly: everything
            // caught here (SQLiteException, IOException, a stray RuntimeException) is anticipated,
            // recoverable, and deliberately NOT `Throwable` — see the class KDoc.
            Log.w(TAG, "pre-migration snapshot skipped", expectedFailure)
            tempFile?.delete()
            false
        }
    }

    private fun dumpTables(db: SupportSQLiteDatabase, writer: BufferedWriter) {
        db.query("SELECT name, sql FROM sqlite_master WHERE type = 'table'").use { tables ->
            while (tables.moveToNext()) {
                val tableName = tables.getString(0)
                val createTableSql = tables.getString(1)
                if (createTableSql == null || isSkippedTable(tableName)) continue
                writer.write(createTableSql)
                writer.write(";")
                writer.newLine()
                dumpRows(db, tableName, writer)
            }
        }
    }

    private fun dumpRows(db: SupportSQLiteDatabase, tableName: String, writer: BufferedWriter) {
        db.query("SELECT * FROM \"$tableName\"").use { rows ->
            val quotedColumns = rows.columnNames.joinToString(separator = ", ") { "\"$it\"" }
            while (rows.moveToNext()) {
                val values = rows.columnNames.indices.joinToString(separator = ", ") { formatValue(rows, it) }
                writer.write("INSERT INTO \"$tableName\" ($quotedColumns) VALUES ($values);")
                writer.newLine()
            }
        }
    }

    private fun isSkippedTable(tableName: String): Boolean =
        tableName.startsWith(SKIPPED_TABLE_PREFIX) || tableName in SKIPPED_EXACT_TABLE_NAMES

    private fun formatValue(cursor: Cursor, columnIndex: Int): String = when (cursor.getType(columnIndex)) {
        Cursor.FIELD_TYPE_NULL -> "NULL"
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(columnIndex).toString()
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(columnIndex).toString()
        Cursor.FIELD_TYPE_STRING -> "'${cursor.getString(columnIndex).replace("'", "''")}'"
        Cursor.FIELD_TYPE_BLOB -> "x'${cursor.getBlob(columnIndex).toHexString()}'"
        else -> error("unrecognized SQLite column type at index $columnIndex for table dump")
    }
}
