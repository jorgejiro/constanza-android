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
 * (design.md decision 5): revert the palette the picker offers, inverting
 * [HabitColorRemap.LEGACY_TO_CURRENT] (a bijection, so the inverse is exact) if a colour rollback
 * is ever needed. Unmapped ints were never written by this migration (see the `WHERE colorArgb IN
 * (...)` guard below), so they need no inverse. Never revert
 * [com.jjrapps.constanza.core.data.AppDatabase]'s `version` back to 1, 2, 3, 4, 5, or 6 — Room refuses
 * to open an already-upgraded file at a lower version, making the user's data unreachable.
 * (`Migration(2, 3)`, the slot this note used to reserve, was consumed by [migration2To3] — the
 * removal of `HabitEntity.question`. `Migration(3, 4)`, the slot that note then reserved, was
 * consumed by [migration3To4] — the `schedules.daysOfWeekMask` column (weekday-only-schedule
 * design.md decision 3). `Migration(4, 5)`, the slot that note reserved next, was consumed by
 * [migration4To5] — the colour re-tone into `HabitColor`'s `[7:1, 11:1]` band. `Migration(5, 6)`,
 * the slot that note reserved next, is now consumed by [migration5To6] — the retirement of
 * `BLUE_GREY` — so any future rollback recipe starts from version 7 onward. Unlike
 * [migration1To2]'s bijection, neither [HabitColorRetoneRemap.LEGACY_TO_CURRENT] nor
 * [HabitColorRetireRemap.LEGACY_TO_CURRENT] has an exact inverse to offer a rollback: both sit on a
 * clamp fallthrough ([HabitColorRetoneRemap.normalize], [HabitColorRetireRemap.normalize]) that is
 * lossy by construction — a rollback here would need to fall back to reverting the app version
 * instead.)
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

    /** Same `MagicNumber` reasoning as [SCHEMA_VERSION_3] — `4` needs a named constant too. */
    private const val SCHEMA_VERSION_4 = 4

    /** Same `MagicNumber` reasoning as [SCHEMA_VERSION_3] — `5` needs a named constant too. */
    private const val SCHEMA_VERSION_5 = 5

    /** Same `MagicNumber` reasoning as [SCHEMA_VERSION_3] — `6` needs a named constant too. */
    private const val SCHEMA_VERSION_6 = 6

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
     * Task 3.1 (weekday-only-schedule design.md decision 1/3). Additive `ALTER TABLE ADD COLUMN`
     * plus a data-only rewrite of the legacy `WEEKLY` rows — no table rebuild, unlike
     * [migration2To3], because a nullable column addition needs none.
     *
     * `dayOfWeek: Int?` (`Entities.kt`) is left dead and still declared: SQLite on `minSdk = 31`
     * (3.32.2) is below the 3.35 floor `ALTER TABLE ... DROP COLUMN` requires, and reusing the
     * column instead would silently widen its domain from `1..7` to `1..127` — an unmigrated row
     * would then be misread as a valid mask rather than failing loudly. See design.md decision 1
     * for the full rationale.
     *
     * [writer]`.write(db)` runs first, exactly as [migration1To2] and [migration2To3] do, for the
     * same reason: a recoverable snapshot failure must never fail the migration itself.
     *
     * **The straggler guard.** `dayOfWeek` is nullable and `kind` carries no `CHECK` constraint, so
     * a `WEEKLY` row with a `NULL` day is representable and would silently survive the `UPDATE`
     * below, then crash the next time `ScheduleEntity.toDomain()` reads it (`Mappers.kt`'s
     * `requireNotNull(daysOfWeekMask)`). Counting rows still carrying `kind = 'WEEKLY'` after the
     * rewrite and throwing on any survivor turns that into a loud migration failure with an intact,
     * rolled-back database and a surviving snapshot — fail loud beats fail silent.
     */
    fun migration3To4(writer: PreMigrationSnapshotWriter): Migration =
        object : Migration(SCHEMA_VERSION_3, SCHEMA_VERSION_4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                writer.write(db)

                db.execSQL("ALTER TABLE `schedules` ADD COLUMN `daysOfWeekMask` INTEGER")
                db.execSQL(
                    "UPDATE schedules SET daysOfWeekMask = 1 << (dayOfWeek - 1), " +
                        "kind = 'DAYS_OF_WEEK' WHERE kind = 'WEEKLY' AND dayOfWeek IS NOT NULL",
                )
                val stragglers = weeklyStragglerCount(db)
                check(stragglers == 0) { "migration3To4 left $stragglers un-rewritten WEEKLY rows" }
            }
        }

    /** Rows still carrying `kind = 'WEEKLY'` after [migration3To4]'s rewrite — every one is a row
     *  the `UPDATE` could not touch because `dayOfWeek` was `NULL`. */
    private fun weeklyStragglerCount(db: SupportSQLiteDatabase): Int =
        db.query("SELECT COUNT(*) FROM schedules WHERE kind = 'WEEKLY'").use {
            it.moveToFirst()
            it.getInt(0)
        }

    /**
     * Data-only colour re-tone: the "23-preset warm-dark palette" habit colours are rewritten to
     * their "22-preset legible-band palette" counterparts (`HabitColor`'s KDoc — the `[7:1, 11:1]`
     * contrast band), and anything left over — the retired `SILVER` preset and any genuinely custom
     * colour a habit already holds — is pulled into that same band exactly as a fresh custom pick
     * would be (`HabitColorBand.kt`'s `clampToHabitBand`). No column, table, or index changes, so
     * `5.json`'s `identityHash` is unchanged from `4.json`'s — the same shape [migration1To2]'s KDoc
     * documents for its own data-only change.
     *
     * **Why this cannot be a fixed `CASE WHEN` built from [HabitColorRetoneRemap.LEGACY_TO_CURRENT]
     * alone, unlike [migration1To2].** At schema version 1 the only colours that could ever exist
     * were the six legacy pastels — a closed, compile-time-known set, which is why [migration1To2]
     * can build its whole `UPDATE` from the map at compile time. By version 4 the free custom colour
     * wheel has shipped, so a habit's `colorArgb` can already be any `Int` at all. Building the
     * `UPDATE` from [HabitColorRetoneRemap.LEGACY_TO_CURRENT] alone would rewrite the 17 presets that
     * actually moved but leave `SILVER` and every custom colour untouched — precisely the defect this
     * migration exists to fix. So this migration reads back every **distinct** `colorArgb` actually
     * present in the table, runs each through [HabitColorRetoneRemap.normalize] here in Kotlin (map
     * lookup, then [com.jjrapps.constanza.core.ui.theme.clampToHabitBand] fallthrough), and writes
     * only the ones whose value actually changed — still via bound `Int` arguments in the `UPDATE`
     * itself, for the exact sign-trap reason [migration1To2]'s KDoc gives: the *comparison* against
     * `colorArgb` must never be built from inlined hex text either.
     *
     * [writer]`.write(db)` runs first, exactly as every earlier migration's does, for the same
     * reason: a recoverable snapshot failure must never fail the migration itself.
     */
    fun migration4To5(writer: PreMigrationSnapshotWriter): Migration =
        object : Migration(SCHEMA_VERSION_4, SCHEMA_VERSION_5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                writer.write(db)

                val changed = distinctHabitColors(db).mapNotNull { legacy ->
                    val current = HabitColorRetoneRemap.normalize(legacy)
                    if (current != legacy) legacy to current else null
                }
                if (changed.isEmpty()) return

                val caseWhenSql = "WHEN ? THEN ? ".repeat(changed.size)
                val inPlaceholders = changed.joinToString(separator = ",") { "?" }
                val caseArgs = changed.flatMap { (legacy, current) -> listOf(legacy, current) }
                val inArgs = changed.map { it.first }
                db.execSQL(
                    "UPDATE habits SET colorArgb = CASE colorArgb $caseWhenSql END " +
                        "WHERE colorArgb IN ($inPlaceholders)",
                    (caseArgs + inArgs).toTypedArray(),
                )
            }
        }

    /**
     * Data-only colour retirement: `BLUE_GREY` (`#849FAC`) is retired from `HabitColor`'s 22-preset
     * legible-band palette, taking it to 21 (`HabitColor`'s KDoc, "Why BLUE_GREY was retired"). No
     * column, table, or index changes, so `6.json`'s `identityHash` is unchanged from `5.json`'s —
     * the same shape [migration1To2]'s KDoc documents for its own data-only change.
     *
     * **Why [com.jjrapps.constanza.core.ui.theme.clampToHabitBand] alone cannot handle this one,
     * unlike a retirement whose colour was already out of band.** `#849FAC` measures 7.01:1, inside
     * the `[7:1, 11:1]` band, so the clamp would return it unchanged and a `BLUE_GREY` habit would
     * silently become a *custom* grey — exactly the near-neutral identity this retirement removes.
     * So, exactly as [migration4To5] does for its wider re-tone, this migration reads back every
     * **distinct** `colorArgb` actually present in the table, runs each through
     * [HabitColorRetireRemap.normalize] here in Kotlin (map lookup — one entry, `#849FAC` ->
     * [com.jjrapps.constanza.core.ui.theme.HabitColor.CYAN]'s `#00ABBD` — then
     * [com.jjrapps.constanza.core.ui.theme.clampToHabitBand] fallthrough for everything else), and
     * writes only the ones whose value actually changed — still via bound `Int` arguments in the
     * `UPDATE` itself, for the exact sign-trap reason [migration1To2]'s KDoc gives.
     *
     * [writer]`.write(db)` runs first, exactly as every earlier migration's does, for the same
     * reason: a recoverable snapshot failure must never fail the migration itself.
     */
    fun migration5To6(writer: PreMigrationSnapshotWriter): Migration =
        object : Migration(SCHEMA_VERSION_5, SCHEMA_VERSION_6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                writer.write(db)

                val changed = distinctHabitColors(db).mapNotNull { legacy ->
                    val current = HabitColorRetireRemap.normalize(legacy)
                    if (current != legacy) legacy to current else null
                }
                if (changed.isEmpty()) return

                val caseWhenSql = "WHEN ? THEN ? ".repeat(changed.size)
                val inPlaceholders = changed.joinToString(separator = ",") { "?" }
                val caseArgs = changed.flatMap { (legacy, current) -> listOf(legacy, current) }
                val inArgs = changed.map { it.first }
                db.execSQL(
                    "UPDATE habits SET colorArgb = CASE colorArgb $caseWhenSql END " +
                        "WHERE colorArgb IN ($inPlaceholders)",
                    (caseArgs + inArgs).toTypedArray(),
                )
            }
        }

    /** Every distinct `colorArgb` actually stored in `habits` right now — the set [migration4To5]
     *  checks against [HabitColorRetoneRemap.normalize] and [migration5To6] against
     *  [HabitColorRetireRemap.normalize], since (unlike schema version 1's six fixed pastels) it is
     *  not a compile-time-known set. */
    private fun distinctHabitColors(db: SupportSQLiteDatabase): List<Int> =
        db.query("SELECT DISTINCT colorArgb FROM habits").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) }
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
