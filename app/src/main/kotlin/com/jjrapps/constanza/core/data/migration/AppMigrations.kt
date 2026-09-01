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
 * (design.md decision 5): ship `Migration(2, 3)` inverting [HabitColorRemap.LEGACY_TO_CURRENT] (a
 * bijection, so the inverse is exact) and revert the palette the picker offers. Unmapped ints were
 * never written by this migration (see the `WHERE colorArgb IN (...)` guard below), so they need no
 * inverse. Never revert [com.jjrapps.constanza.core.data.AppDatabase]'s `version` back to 1 — Room
 * refuses to open an already-upgraded file at a lower version, making the user's data unreachable.
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
}
