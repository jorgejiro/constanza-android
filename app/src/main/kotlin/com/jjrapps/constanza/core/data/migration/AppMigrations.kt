package com.jjrapps.constanza.core.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Task 2.2 (design.md decision 3). Builds the mandatory `v1 -> v2` [Migration], registered by
 * `DatabaseModule.provideAppDatabase` via `.addMigrations(...)` (task 2.4 — a hard blocker: without
 * it Room throws `IllegalStateException` at first open on every existing install). Data-only
 * change: `app/schemas/.../2.json` differs from `1.json` only in `"version"`, so `identityHash` is
 * unchanged — asserted by `AppDatabaseMigrationTest.runMigrationsAndValidate(..., 2, true, ...)`.
 *
 * **Rollback recipe**, written here because this is the file someone reverting will open first
 * (design.md decision 5): ship `Migration(3, 4)` inverting [HabitColorRemap.LEGACY_TO_CURRENT] (a
 * bijection, so the inverse is exact) and revert the palette the picker offers. Unmapped ints were
 * never written by this migration (see the `WHERE colorArgb IN (...)` guard below), so they need no
 * inverse. Never revert [com.jjrapps.constanza.core.data.AppDatabase]'s `version` back to 1, 2, or
 * 3 — Room refuses to open an already-upgraded file at a lower version, making the user's data
 * unreachable. (`Migration(2, 3)`, the slot this note used to reserve, is now consumed by
 * [migration2To3] — the removal of `HabitEntity.question` — so any future rollback recipe starts
 * from version 4 onward.)
 *
 * **Deviation, flagged loudly (same class of issue as unit 1's `Color.kt` -> `ConstanzaColors.kt`
 * rename).** Declared as an `object`, not a `class`: detekt's default `VariableNaming` rule (active
 * under `buildUponDefaultConfig = true`) requires camelCase for a class member property, but
 * `ObjectPropertyNaming` — the rule that actually applies to a property declared directly inside a
 * Kotlin `object` — permits this `SCREAMING_SNAKE_CASE` mirroring Room's own migration-naming
 * convention. Neither `tasks.md` nor `design.md` anticipated this; `MIGRATION_1_2` as a `class`
 * member fails `:app:detektMain`, and a `@Suppress` was rejected for the same reason decision 1
 * rejected one for `MagicNumber`.
 *
 * **Second deviation, same root cause, flagged for unit 3.** Task 3.3 asked for the pre-migration
 * snapshot's `filesDir` to be "passed into `PreMigrationSnapshotWriter` at the migration call
 * site" — but an `object` cannot take a constructor parameter, and this file has to stay an
 * `object` for the reason above. Resolved with a factory function, [migration1To2], instead: the
 * caller (`DatabaseModule`) builds the [PreMigrationSnapshotWriter] with its own `filesDir` and
 * hands it in; the function returns a fresh [Migration] closing over that writer. A function name
 * is camelCase by `FunctionNaming`, so the naming tension this deviation is about never arises for
 * it — no `@Suppress` needed here either.
 */
internal object AppMigrations {

    /** detekt's `MagicNumber` ignores `-1, 0, 1, 2` by default (unconfigured here, same as every
     *  other rule this file already leans on the defaults for) — `3` is not in that list, so
     *  [migration2To3]'s `Migration(2, 3)` needs a named constant the same way task 3.5's own
     *  `@Suppress` was rejected for this exact rule (see this object's own KDoc, second deviation). */
    private const val SCHEMA_VERSION_3 = 3

    /**
     * **The sign trap.** `0xFF8E24AA.toInt()` is a *negative* `Int` once stored in the `colorArgb`
     * column (two's-complement), but the identical hex text written directly into a SQL string is
     * parsed by SQLite as a large *positive* number and matches nothing — a `CASE` built from
     * inlined hex literals would compile, run, report success, and rewrite zero rows. Every value
     * below is a **bound argument** taken from [HabitColorRemap.LEGACY_TO_CURRENT], never hex text
     * inside the SQL, so Kotlin's own `Int` conversion is the only place the sign is ever decided.
     *
     * **Task 3.2 (design.md decision 4).** [writer]`.write(db)` runs as the *first* statement,
     * before the `UPDATE` — the only point in this function where a genuine "before" state exists.
     * Its `Boolean` result is discarded on purpose: [PreMigrationSnapshotWriter.write] already logs
     * its own failure at WARN and never throws for a recoverable cause, so nothing here may turn
     * that outcome into a migration failure, on success or on failure.
     */
    fun migration1To2(writer: PreMigrationSnapshotWriter): Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                writer.write(db)

                val entries = HabitColorRemap.LEGACY_TO_CURRENT.entries.toList()
                val caseWhenSql = "WHEN ? THEN ? ".repeat(entries.size)
                val inPlaceholders = entries.joinToString(separator = ",") { "?" }
                val caseArgs = entries.flatMap { (legacy, current) -> listOf(legacy, current) }
                val inArgs = entries.map { it.key }
                db.execSQL(
                    "UPDATE habits SET colorArgb = CASE colorArgb $caseWhenSql END " +
                        "WHERE colorArgb IN ($inPlaceholders)",
                    (caseArgs + inArgs).toTypedArray(),
                )
            }
        }

    /**
     * Task 3.5 (design.md D1/D2). SQLite on `minSdk = 31` ships 3.32.2, below the 3.35 floor
     * `ALTER TABLE ... DROP COLUMN` requires, so `habits` is rebuilt instead: `CREATE TABLE
     * _new_habits` (the generated `createAllTables` DDL for `habits`, minus `question`, with the
     * name substituted) -> `INSERT ... SELECT` the remaining columns -> `DROP TABLE habits` ->
     * `RENAME TO habits`. No `PRAGMA` of any kind (design.md D1's rejected-alternatives table):
     * Room's own generated delegate turns foreign keys on only in `onOpen`, which runs after
     * `migrate()`, so the cascade is not armed while this rebuild runs — a checked fact, not an
     * assumption about Room's internal ordering.
     *
     * [writer]`.write(db)` runs first, exactly as [migration1To2] does and for the same reason:
     * its `Boolean` result is discarded because a recoverable snapshot failure must never fail the
     * migration itself.
     *
     * **The child-row guard (design.md D2).** `habits` is the parent of four
     * `ForeignKey(onDelete = CASCADE)` tables (`Entities.kt`) — `schedules`, `reminder_slots`,
     * `entries`, `reminder_occurrences`. A rebuild that ran with foreign keys enforced would
     * cascade-delete every row in all four the instant `DROP TABLE habits` ran. Counting all four
     * before the drop and again after the rename, and throwing on any mismatch, turns that failure
     * mode into a loud crash with an intact, rolled-back database and a surviving snapshot, rather
     * than a silent, successful wipe — the `data-portability` requirement *Child Records Survive A
     * Schema Migration* enforced on the device, mechanism-independent exactly as that requirement
     * is written.
     */
    fun migration2To3(writer: PreMigrationSnapshotWriter): Migration =
        object : Migration(2, SCHEMA_VERSION_3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                writer.write(db)

                val before = childRowCounts(db)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `_new_habits` (`id` INTEGER PRIMARY KEY " +
                        "AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorArgb` INTEGER NOT " +
                        "NULL, `notes` TEXT, `archived` INTEGER NOT NULL, `archivedAt` TEXT, " +
                        "`createdAt` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `_new_habits` (`id`, `name`, `colorArgb`, `notes`, `archived`, " +
                        "`archivedAt`, `createdAt`, `sortOrder`) SELECT `id`, `name`, `colorArgb`, " +
                        "`notes`, `archived`, `archivedAt`, `createdAt`, `sortOrder` FROM `habits`",
                )
                db.execSQL("DROP TABLE `habits`")
                db.execSQL("ALTER TABLE `_new_habits` RENAME TO `habits`")
                val after = childRowCounts(db)
                check(before == after) { "migration2To3 lost child rows: before=$before after=$after" }
            }
        }

    /**
     * design.md D2 — one query, four scalar subselects, in the fixed order `schedules`,
     * `reminder_slots`, `entries`, `reminder_occurrences`, matching every CASCADE child of
     * `habits` (`Entities.kt`).
     */
    private fun childRowCounts(db: SupportSQLiteDatabase): List<Int> = db.query(
        "SELECT (SELECT COUNT(*) FROM schedules), (SELECT COUNT(*) FROM reminder_slots), " +
            "(SELECT COUNT(*) FROM entries), (SELECT COUNT(*) FROM reminder_occurrences)",
    ).use {
        it.moveToFirst()
        List(it.columnCount) { i -> it.getInt(i) }
    }
}
