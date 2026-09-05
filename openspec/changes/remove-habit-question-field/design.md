# Design: Remove the `question` field from the habit model

## Technical Approach

`question` is deleted from the domain model outward. The only non-mechanical part is the v2→v3
Room migration: a `habits` table rebuild that must not cascade-delete the four child tables
(`schedules`, `reminder_slots`, `entries`, `reminder_occurrences`, all
`ForeignKey(entity = HabitEntity::class, onDelete = CASCADE)` in `Entities.kt`). Everything else
follows the shapes already in the repo: `AppMigrations.migration1To2`'s factory function, the
`DropdownMenuItem` pattern `HabitRow` already uses for Delete, and this repo's KDoc density.

## Architecture Decisions

### D1 — Migration mechanism: 4-step rebuild inside Room's foreign-keys-off migration window

**Choice.** `CREATE TABLE _new_habits` (no `question`) → `INSERT INTO _new_habits (…) SELECT (…)
FROM habits` → `DROP TABLE habits` → `ALTER TABLE _new_habits RENAME TO habits`. No `PRAGMA` of any
kind. The `CREATE` string is copied verbatim from the regenerated
`app/build/generated/ksp/…/AppDatabase_Impl.kt` `createAllTables` line for `habits`, with the name
substituted — that is the only text `runMigrationsAndValidate` will accept. `habits` declares no
indices, so none are recreated.

| Alternative | Rejected because |
|---|---|
| `PRAGMA defer_foreign_keys = TRUE` (the mechanism exploration/proposal assumed) | **It does not work, and it fails silently.** Deferral postpones *violation reporting* to COMMIT; it does not stop `ON DELETE CASCADE` actions firing. `DROP TABLE` with FK enforcement on runs an implicit `DELETE FROM habits` that fires those actions (SQLite: the implicit delete fires no triggers but *does* fire foreign key actions). The children would be deleted, so at COMMIT there is no violation left to report and the commit **succeeds**. It converts total data loss from loud into silent. |
| `ALTER TABLE habits DROP COLUMN question` | Requires SQLite **3.35.0+**. `minSdk = 31` (`app/build.gradle.kts:43`); Android 12 ships SQLite **3.32.2**. Below the floor, and the platform's SQLite version is not a contract we control anyway. |
| Rebuild all five tables so no FK ever dangles | Multiplies the largest, least reversible statement block in the change by five for a branch that never executes. |

**Rationale.** Room 2.8.4 enables foreign keys *after* migrating: the repo's own generated delegate
executes `PRAGMA foreign_keys = ON` in `onOpen` (`AppDatabase_Impl.kt:89-90`) and `onPreMigrate` only
drops FTS sync triggers; `onOpen` runs after `onMigrate`. Android's `SQLiteDatabase` leaves
`foreign_keys` off unless `setForeignKeyConstraintsEnabled` is called, and Room never calls it. So
during `migrate()` the cascade is not armed — which is why Room's own generated auto-migrations drop
and rename with no pragma. D2 turns that from an assumption into a checked fact.

### D2 — Child-row survival is asserted at runtime, not just in a test

**Choice.** `migrate()` counts all four child tables in one query before the `DROP` and again after
the `RENAME`; a mismatch throws. Room's migration transaction rolls back, the file stays a valid v2
database, and the pre-migration snapshot — an ordinary file, outside the transaction — survives.

```kotlin
private fun childRowCounts(db: SupportSQLiteDatabase): List<Int> = db.query(
    "SELECT (SELECT COUNT(*) FROM schedules), (SELECT COUNT(*) FROM reminder_slots), " +
        "(SELECT COUNT(*) FROM entries), (SELECT COUNT(*) FROM reminder_occurrences)",
).use { it.moveToFirst(); List(it.columnCount) { i -> it.getInt(i) } }
```

**Alternatives.** Reading `PRAGMA foreign_keys` as a pre-flight guard — narrower (it catches only
one cause) for the same line cost. `PRAGMA foreign_key_check` — catches dangling references, not
missing rows, so it would pass on a total cascade. Trusting the test alone — `MigrationTestHelper`
need not mirror production's FK state, so a Room-side regression would ship green.

**Rationale.** This is the `data-portability` requirement *Child Records Survive A Schema Migration*
enforced on the device, and it is mechanism-independent exactly as that requirement is written. The
trade is explicit: on a future Room that enforces FKs during migration, the app throws at open
instead of losing every schedule, slot, entry and occurrence. Fail-closed with an intact database
and a snapshot beats a successful commit over an emptied one.

### D3 — The snapshot filename becomes version-derived

**Choice.** `PreMigrationSnapshotWriter` derives its name from `db.version` —
`pre-migration-v${db.version}.sql` and matching `.tmp`. Today the name is the hardcoded const
`pre-migration-v1.sql` with `REPLACE_EXISTING`, so on a 1→3 upgrade the v2→v3 snapshot silently
overwrites the v1→v2 one and the surviving file's name lies about its contents. During `migrate()`
`user_version` is still the *old* version, so the 1→2 path keeps writing `pre-migration-v1.sql`
byte-identically and `AppDatabaseMigrationTest`'s existing path assertion stays green untouched.

**Alternative rejected.** Accept the overwrite. The lost content is only the pre-remap colour ints
(a bijection, recoverable), but a recovery artifact whose name contradicts its contents is a hazard
precisely when someone is reading it under pressure.

### D4 — Notification: one new string key, a three-argument `postReminder`

`values/strings.xml` and `values-es/strings.xml` each drop `notification_default_question` and
`habit_editor_question_label` and gain **`notification_reminder_title`** (EN `Habit tracker`, ES
`Seguimiento de hábitos`) — placed beside the existing `notification_*` block, matching the
`notification_channel_reminders_name` / `notification_action_*` naming already there.
`postReminder(occurrenceId, habitName, colorArgb)` and `buildNotification(ctx, occurrenceId,
habitName, colorArgb)` drop the `question: String?` parameter; its only call site,
`ReminderFireWorker.kt:52`, drops `habit.question` in the same commit so detekt never sees an unused
parameter. Builder: `.setContentTitle(ctx.getString(R.string.notification_reminder_title))`,
`.setContentText(habitName)`, `.setStyle(NotificationCompat.BigTextStyle().bigText(habitName))`.
`REMINDER_CHANNEL_ID`, the notification id (`occurrenceId`) and the three `.addAction(...)` calls are
untouched — `CoreFlowTestFixture.awaitPostedNotification` depends on them. `postReminder`'s KDoc
currently says the localized context is used for "the channel name, the question, and all three
action labels"; it becomes "the channel name, the title, and all three action labels".

### D5 — `HabitRow`: overflow launcher alone, three menu items

`trailingContent` becomes the bare `Box { IconButton + DropdownMenu }` — the wrapping `Row` and both
`TextButton`s go (drop their imports if unused elsewhere in the file). Menu order **Progress,
Archive/Un-archive, Delete**: it matches the spec scenario's order and leaves the irreversible item
farthest from where the finger lands. Every item sets `menuExpanded = false` before invoking its
action, mirroring Delete's existing `onClick`. `headlineContent = { Text(habit.name, maxLines = 2,
overflow = TextOverflow.Ellipsis) }`. `supportingContent` is deleted. `HabitRow`'s KDoc restates D2
of `archive/2026-09-03-habit-deletion/design.md` verbatim as the reason Archive stays inline; it is
rewritten to record the supersession, the measured reason (509 px → 723 px name column at identical
168 px row height) and the accepted residual risk that **Archive and Delete are now adjacent menu
rows**, bounded by Delete's `DeleteHabitDialog` and by Archive being reversible.

### D6 — `HabitRepositoryCrudTest.updatingAHabitChangesItsStoredFields` repoints to `notes`, and gains `colorArgb`

`notes` is `question`'s exact structural twin — nullable `TEXT`, non-key, round-tripped by the same
mapper — and `assertEquals("20 pages", updated.notes)` is **already** at line 70. So deleting lines
63/69's `question` clauses preserves the "an update really changes a field" proof with no new lines.
One line is then added on purpose: `colorArgb` (non-null `INTEGER`) into the `copy(...)` and its
assertion. Two `TEXT` columns cannot detect a column-order or type regression in a rebuilt table,
and this change rewrites `habits`' column list — that is the one new failure mode worth a witness.

### D7 — The reserved-slot collision

`AppMigrations.kt:13-18`'s KDoc rollback recipe currently says "ship `Migration(2, 3)` inverting
`HabitColorRemap.LEGACY_TO_CURRENT`". Version 3 is consumed here, so it is re-pointed at
`Migration(3, 4)` **in the same commit** as the version bump. Its "never revert `version` back to 1"
sentence is generalised to cover 3.

## Data Flow

    ReminderFireWorker ──postReminder(id, habit.name, colorArgb)──→ NotificationPoster
                                                                          │
                          title = R.string.notification_reminder_title ◄───┤
                          text  = habitName  +  BigTextStyle(habitName) ◄──┘

    Room open (v2 file, v3 code), foreign_keys still OFF:
      writer.write(db) ──→ files/pre-migration/pre-migration-v2.sql   (outside the txn)
      counts(children) → CREATE _new_habits → INSERT…SELECT → DROP habits
                       → RENAME → counts(children) → equal? commit : throw ⇒ rollback
      onOpen: PRAGMA foreign_keys = ON

## File Changes

| File | Action | Description |
|---|---|---|
| `domain/…/model/Model.kt` | Modify | Drop `Habit.question` |
| `core/data/entity/Entities.kt` | Modify | Drop `HabitEntity.question` |
| `core/data/mapper/Mappers.kt` | Modify | Drop both `question = question` lines |
| `core/data/AppDatabase.kt` | Modify | `version = 3`; KDoc gains the v3 rebuild note |
| `app/schemas/…AppDatabase/3.json` | Create | Generated, committed (`exportSchema = true`) |
| `core/data/migration/AppMigrations.kt` | Modify | Add `migration2To3(writer)`; re-point the rollback KDoc at `Migration(3, 4)` (D7) |
| `core/data/migration/PreMigrationSnapshotWriter.kt` | Modify | Version-derived filename (D3) |
| `core/di/DatabaseModule.kt` | Modify | `.addMigrations(migration1To2(writer), migration2To3(writer))` — one `PreMigrationSnapshotWriter(context.filesDir)`, both migrations |
| `portability/BackupDto.kt`, `BackupMapper.kt` | Modify | Drop `BackupHabit.question` and both mapping lines |
| `reminding/NotificationPoster.kt` | Modify | D4 |
| `scheduling/ReminderFireWorker.kt` | Modify | Drop the `habit.question` argument |
| `habit/HabitListScreen.kt` | Modify | D5 |
| `habit/HabitEditorScreen.kt` | Modify | Delete the question `OutlinedTextField` and `FIELD_QUESTION`; notes gains `minLines = 3, maxLines = 5` |
| `habit/HabitEditorFormState.kt`, `HabitEditorViewModel.kt` | Modify | Drop `question`, `onQuestionChange`, both mapping lines |
| `res/values/strings.xml`, `res/values-es/strings.xml` | Modify | −2 keys, +`notification_reminder_title`, both locales |

## Interfaces / Contracts

```kotlin
// AppMigrations — same factory shape as migration1To2; writer.write(db) is the FIRST statement and
// its Boolean is discarded, for the reason migration1To2's KDoc already states.
fun migration2To3(writer: PreMigrationSnapshotWriter): Migration = object : Migration(2, 3) { … }

// NotificationPoster
suspend fun postReminder(occurrenceId: Long, habitName: String, colorArgb: Int): Boolean
```

## Testing Strategy

| Test | Action | What it must prove afterwards |
|---|---|---|
| `AppDatabaseMigrationTest` (new 2→3 case) | ADD | Seed a v2 habit **plus** one row in each of the four child tables, run `migration2To3`, assert: `question` gone from `PRAGMA table_info(habits)`, the habit row intact, **all four child rows still present and still linked**, `pre-migration-v2.sql` written. Add a second habit to cover the spec's "each habit's records survive independently" scenario. Table-drive the seed (one helper + four inserts) — it is the largest authored-line item in the change. Existing 1→2 cases and the `seedHabit` v1 SQL at line 114 stay **as-is**. |
| `SpanishColdProcessNotificationInstrumentedTest` | **REWRITE, never delete** | `EXTRA_TITLE` == `Seguimiento de hábitos`; `EXTRA_TEXT`/`EXTRA_BIG_TEXT` == the Spanish habit name verbatim; the three Spanish action labels and the Spanish channel name assertions are untouched. Still a cold process with no Activity created. |
| `HabitEditorViewModelTest` (`editing the guiding question…`, ~:434-443) | **DELETE** | Its only subject is `onQuestionChange`. |
| `HabitEditorViewModelTest` (:101, :107, :118, :144, :606) | REPOINT | Drop the `onQuestionChange` call, the `.question` assertion and the fixture lines; the name/notes assertions carry the tests. |
| `HabitRepositoryCrudTest.updatingAHabitChangesItsStoredFields` | REPOINT | D6 — `notes` keeps the proof, `colorArgb` is added. |
| `HabitListArchiveComposeTest` (:89, :95, :101) and `CoreFlowE2ETest:336` | REPOINT | Open the overflow menu first (`onNodeWithContentDescription(habit_list_more_options)`), reusing `HabitDeleteDialogComposeTest.kt:64`. The menu auto-dismisses after a click, so the closing `assertExists()` must reopen it. |
| New `HabitListScreen` compose coverage | ADD | The three ADDED `habit-management` scenarios: no always-visible action buttons; all three items present once opened; a long name capped at two lines. |
| `NotificationPosterTest`, `NotificationPosterInstrumentedTest`, `NotificationActionWiringInstrumentedTest`, `ReminderFireWorkerTest`, `ReminderWorkerTestFixtures` | REPOINT | Arity-only `postReminder` fixes; `ReminderWorkerTestFixtures` drops its `question` parameter. |
| `ModelTest`, `MappersTest`, `HabitListViewModelTest`, `ProgressViewModelTest`, `OccurrencePlannerTest`, `TodayViewModelTest`, `BackupImporterTest:115`, `BackupImporterNormalizationTest:50`, `HabitRepositoryDeleteSlotTest`, `HabitRepositoryTestFixture`, `EntryDaoUniqueConstraintTest`, `HabitColorDotComposeTest`, `ReconcileWorkerTest`, `MidnightSweepWorkerTest`, `PortabilityTestFixture`, `HabitListArchiveComposeTest:47`, `LiveSnoozeAcrossMidnightSeed`, `ImminentReminderSeed` | REPOINT | Mechanical fixture drops; `ModelTest`'s test name loses "question"; the seed scripts lose `SEED_HABIT_QUESTION`. |
| `StringResourceParityTest` | unchanged | Generic; symmetric edits in both locales satisfy it. |

Manual, not matrix-provable: One UI rendering of the notification template and the list row
(`testing.instrumented.device_free_matrix.limits`).

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The notification `PendingIntent`/`BroadcastReceiver` surface is
unchanged by this design.

## Migration / Rollout

Three sequential slices, as the proposal frames them. **This design adds ~20 authored lines to slice
3** (D2's guard ≈12 with KDoc, D6's `colorArgb` witness ≈3, D3's writer change ≈6): **270–380, still
plausibly over 400.** Exits, in order:

1. **Move D3 (the snapshot filename) into slice 2.** It touches no `question` site, compiles alone,
   and leaves the 1→2 snapshot test byte-identically green — a genuinely independent ~6–10 lines,
   and this design's own addition, so it is the first thing to move.
2. **`size:exception`.** Still the strongest exit for what remains. Room derives `3.json` from the
   entity, so a column-dropping migration cannot precede the field's removal without failing
   `identityHash` validation, and Kotlin gives no partial-compilation escape.
3. **Table-drive the 2→3 seed** (one helper, four inserts) — ~40 lines, no coverage lost.

Splitting the migration from the removal remains unavailable. Rollback is forward-only
(`Migration(3, 4)` re-adding a nullable `question`, schema not values); the real recovery artifact is
`pre-migration-v2.sql`.

## Open Questions

None.
