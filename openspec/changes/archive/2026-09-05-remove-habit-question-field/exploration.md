# Exploration: Remove the `question` field from the habit model

Origin: carried-forward open item `reminder-notification-shows-a-question-and-truncates-long-habit-names`
in `openspec/config.yaml` (starts line 1727).

Artifact store is `hybrid`. The Engram half is `sdd/remove-habit-question-field/explore`. This file is
the filesystem half, written by the orchestrator because the `sdd-explore` agent ran with no write tool.
It reconstructs both Engram writes to that topic — the original exploration and the later addendum
covering two `HabitListScreen` row defects — into one document. Nothing here is invented; where the
record is silent, this file says so.

## Legend

- **SETTLED** — decided by the maintainer. `sdd-propose` and `sdd-design` take these as premises and
  must not re-open them.

Nothing in this document is open. The two decisions that were open when this exploration was first
written — the notification shape and whether "Progress" stays inline in the habit row — were both
settled by the maintainer from measured renders on API 37. They are recorded below as settled
constraints 6 and 7, in the place the OPEN sections used to occupy.

## Current state

`Habit`/`HabitEntity` carry a nullable `question: String?` alongside `name`/`notes`.

`NotificationPoster.buildNotification`
(`app/src/main/kotlin/com/jjrapps/constanza/reminding/NotificationPoster.kt:139-151`) builds a plain
`NotificationCompat.Builder` with no `.setStyle(...)` — confirmed zero existing `BigTextStyle`/style
usage anywhere in the repo (grepped `app/src`). `.setContentTitle(habitName)` and
`.setContentText(question ?: default)` are both single-line and ellipsized in the collapsed (shade)
view; this is unconditional platform behaviour of the standard notification template, not something
any of today's builder calls can widen.

`AppDatabase` (`app/src/main/kotlin/com/jjrapps/constanza/core/data/AppDatabase.kt:28-38`) is
`version = 2`, `exportSchema = true`, schema location `app/schemas/`
(`app/build.gradle.kts:172`, `arg("room.schemaLocation", ...)`). `1.json`/`2.json` are checked in;
`3.json` is not.

The backup format (`portability/BackupDto.kt`) has an explicit `formatVersion` int
(`CURRENT_BACKUP_FORMAT_VERSION = 1`) gated in `BackupImporter.parseAndValidate` (throws
`UnsupportedBackupVersionException`/`ImportFailure.UnsupportedVersion` above it), and the JSON decoder
already runs with `Json { ignoreUnknownKeys = true }` (`BackupImporter.kt:14`) for forward-compatibility
reasons unrelated to this change.

## Settled constraints (maintainer decisions received during this exploration)

All five are **SETTLED**. Recorded as-is, not re-litigated.

1. **`question` is removed from the product entirely.** `Habit`/`HabitEntity` keep `name` + `notes`
   only. Confirmed 9 real (non-generic-word) production call sites, listed under Affected areas.
2. **`HabitListScreen` `supportingContent` is deleted, not repointed.** `habit/HabitListScreen.kt:195`
   — `supportingContent = habit.question?.let { question -> { Text(question) } },` is removed outright;
   the `ListItem` keeps `leadingContent` (colour dot), `headlineContent` (name), `trailingContent`
   (actions).
3. **Editor notes field becomes `minLines = 3, maxLines = 5`.** Today `habit/HabitEditorScreen.kt:228-236`
   renders notes as an ordinary one-line `OutlinedTextField` with no `minLines`.
4. **Backup format: no backward compatibility to preserve.** The app has never been published; no backup
   in the wild carries `question`. The field is removed outright from `BackupHabit`/`BackupMapper` — no
   version bump, no `ImportFailure.UnsupportedVersion` path, no silent-tolerance shim to design.
   Fact-check requested by the maintainer: the backup format *does* carry an explicit `formatVersion`
   (confirmed above) and `ignoreUnknownKeys = true` is already the decoder's standing behaviour
   (`BackupImporter.kt:14`) — so a stray `"question"` key in an old or test JSON is harmlessly ignored
   today regardless, but that is incidental, not a designed shim. One concrete test-blast-radius fact:
   `app/src/test/kotlin/com/jjrapps/constanza/portability/BackupImporterTest.kt:115`'s `validBackupJson()`
   fixture literal still contains a `"question": "Did you meditate today?"` line — it will keep parsing
   fine (`ignoreUnknownKeys`) but should be deleted from the fixture since it no longer models a real
   exported shape. `BackupRoundTripTest` (androidTest) asserts round-trip structurally, never a hardcoded
   JSON string, so it needs no changes beyond whatever `habitEntity()` fixture helper touches.
5. **Room v2 to v3: a real hand-written `Migration(2,3)`.** **REVERSED on 2026-09-05.** This constraint
   previously read "destructive fallback, not a hand-written `Migration(2,3)`, scoped to debuggable
   builds." That is dead. Do not carry any part of it forward. The rest of this document had already been
   swept for it.

   **What the migration must be:**

   - Add `AppMigrations.migration2To3(writer)` following `migration1To2`'s existing factory-function
     shape (`AppMigrations.kt:54-70`), taking the same `PreMigrationSnapshotWriter` and calling
     `writer.write(db)` as its **first** statement — that is the whole point of the reversal. Discard the
     `Boolean` result exactly as `migration1To2` does: the writer logs its own failure at WARN and never
     throws for a recoverable cause, so nothing in `migrate()` may turn that outcome into a migration
     failure.
   - Wire it at `DatabaseModule.kt:36` alongside the existing one.
   - Bump `@Database(version = 3, ...)` at `AppDatabase.kt:36` and commit
     `app/schemas/com.jjrapps.constanza.core.data.AppDatabase/3.json` (`exportSchema = true`;
     `AppDatabase.kt:16-21`'s KDoc states every version is committed).
   - SQLite cannot drop a column in place: create the new `habits` table without `question`, copy, drop
     the old, rename.
   - Extend `AppDatabaseMigrationTest` — today it exercises only 1 to 2 — to cover 2 to 3, including the
     snapshot firing.

   **Why it was reversed.** Two findings, and the second is decisive.

   1. A destructive fallback runs no `Migration` object, so `PreMigrationSnapshotWriter` — wired only at
      `DatabaseModule.kt:36` inside `.addMigrations(AppMigrations.migration1To2(...))` — would never
      fire, silently breaking the ratified `data-portability` requirement **Automatic Pre-Migration
      Snapshot** (`openspec/specs/data-portability/spec.md:91-110`), which demands a snapshot before
      *any* schema or data modification.
   2. **The maintainer's phone runs RELEASE builds.** `app/build.gradle.kts:78-83` signs the release
      build (`signingConfig = signingConfigs.findByName("release")`, `isMinifyEnabled = true`), and
      distribution is `assembleRelease` to a signed APK to GitHub Releases. So `FLAG_DEBUGGABLE` is
      **false** on the only device that exists. The two halves of the old constraint were mutually
      destructive: "I accept losing the data" assumed the fallback would run, while "scope it to debug so
      releases fail loudly" guaranteed it would not — the release build would meet a v2 database with no
      path to v3 and throw `IllegalStateException` at open. Not a wipe: a **crash loop**, an app that does
      not start at all.

   With the supposed saving gone, the real migration is simply the better trade: no data loss, the
   snapshot fires, the ratified requirement is satisfied as written, and no permanent footgun is left
   behind. There is no carried-forward `openspec/config.yaml` item to file, because there is nothing left
   to replace later.

   **Two traps this migration must clear, neither of them optional:**

   - **The CASCADE trap.** Four tables carry `ForeignKey(entity = HabitEntity::class, onDelete = CASCADE)`
     against `habits` — `schedules` (`Entities.kt:30-34`), `reminder_slots` (:53-57), `entries` (:79-83)
     and `reminder_occurrences` (:110-114). A naive rebuild that runs `DROP TABLE habits` with foreign
     keys enforced cascade-deletes **every schedule, slot, entry and occurrence in the database** — the
     exact data loss this reversal exists to prevent, arriving through the back door. `PRAGMA
     foreign_keys` is a no-op inside a transaction and Room runs `migrate()` inside one, so the
     conventional `PRAGMA foreign_keys=OFF` recipe is unavailable here; the applicable mechanism is
     `PRAGMA defer_foreign_keys = TRUE` as the first statement after the snapshot write, which defers
     enforcement to commit — by which point the rename has restored `habits`. **`sdd-design` must confirm
     the exact mechanism, and `AppDatabaseMigrationTest`'s 2 to 3 case must assert child rows survive.**
     That assertion is what actually catches this, whichever mechanism is chosen.
   - **The reserved-slot collision.** `AppMigrations.kt:13-18`'s KDoc already reserves `Migration(2, 3)`
     for something else — it is the documented rollback recipe for the v1 to v2 colour remap ("ship
     `Migration(2, 3)` inverting `HabitColorRemap.LEGACY_TO_CURRENT`"). This change consumes version 3
     for the column drop, so that recipe must be re-pointed at `Migration(3, 4)` and the KDoc updated in
     the same commit. Leaving it stale would hand a future maintainer a rollback instruction that
     silently collides with a shipped version.

   Unlike 1 to 2 — a data-only change whose `2.json` differs from `1.json` only in `"version"`, leaving
   `identityHash` unchanged — 2 to 3 is a genuine schema change, so `3.json` differs materially and
   `runMigrationsAndValidate` exercises a real schema comparison. `CoreFlowTestFixture`'s second
   `Room.databaseBuilder` (androidTest, same on-disk file) registers no migrations, by design; it never
   meets a stale file within one test run, so it still needs no change here.

## Affected areas — production code

Confirmed by direct read/grep, not generic English-word false positives.

- `domain/src/main/kotlin/com/jjrapps/constanza/domain/model/Model.kt:11` — `Habit.question: String?`
- `app/src/main/kotlin/com/jjrapps/constanza/core/data/entity/Entities.kt:17` — `HabitEntity.question: String?`
  (real Room column, hence the v2 to v3 change above)
- `app/src/main/kotlin/com/jjrapps/constanza/core/data/mapper/Mappers.kt:34,46` — `question = question,`
  both directions
- `app/src/main/kotlin/com/jjrapps/constanza/portability/BackupDto.kt:53` — `BackupHabit.question: String?`
- `app/src/main/kotlin/com/jjrapps/constanza/portability/BackupMapper.kt:23,40` — `question = question,`
  both directions
- `app/src/main/kotlin/com/jjrapps/constanza/reminding/NotificationPoster.kt:62,65,136,143` —
  `postReminder`/`buildNotification` drop the `question: String?` param entirely; the
  `.setContentText(...)` line is replaced per the notification-shape decision below
- `app/src/main/kotlin/com/jjrapps/constanza/scheduling/ReminderFireWorker.kt:52` — the only
  `postReminder` call site, drops the `habit.question` argument
- `app/src/main/kotlin/com/jjrapps/constanza/habit/HabitEditorFormState.kt:21,44` — drop
  `question: String = ""` field and its KDoc mention
- `app/src/main/kotlin/com/jjrapps/constanza/habit/HabitEditorScreen.kt:82,122,143,213-226,334` — remove
  the question `OutlinedTextField` block, the `onQuestionChange` callback plumbing, and the
  `FIELD_QUESTION` focus-restoration id (confirmed no shared list or array holds the three field ids —
  each is an independent `focusRestoring(FIELD_X, ...)` call, so nothing else references `FIELD_QUESTION`)
- `app/src/main/kotlin/com/jjrapps/constanza/habit/HabitEditorViewModel.kt:108,122,181` — drop
  `onQuestionChange`, and both `question = ...` mapping lines
- `app/src/main/kotlin/com/jjrapps/constanza/habit/HabitListScreen.kt:195` — delete the
  `supportingContent` line (settled constraint 2)
- `app/src/main/res/values/strings.xml:12,41` and `app/src/main/res/values-es/strings.xml:15,41` — remove
  `notification_default_question` and `habit_editor_question_label` from **both** files. No other
  question-related string exists (confirmed by full grep of both files). `StringResourceParityTest`
  (`app/src/test/kotlin/com/jjrapps/constanza/localization/StringResourceParityTest.kt`) enforces exact
  key-set symmetry between the two files but is otherwise generic — removing both keys from both files
  satisfies it with no special-case handling.
- `openspec/specs/habit-management/spec.md:11` — requirement text says "...an optional guiding question,
  optional colour, optional notes..."; needs a `MODIFIED Requirements` delta in the eventual `sdd-spec`
  phase (out of scope for this exploration, flagged for propose/spec).

## Affected areas — test blast radius

### Unit tests

- `domain/src/test/kotlin/com/jjrapps/constanza/domain/model/ModelTest.kt:17-21` — test named
  `Habit carries all fields including nullable question, notes and archivedAt`; rename and drop the
  `question = ...` fixture line.
- `app/src/test/kotlin/com/jjrapps/constanza/core/data/mapper/MappersTest.kt:30,47` — drop
  `question = ...` from both-direction fixtures.
- `app/src/test/kotlin/com/jjrapps/constanza/habit/HabitEditorViewModelTest.kt` — heaviest single file:
  - line 101 (`viewModel.onQuestionChange(...)`) and line 107
    (`assertEquals(..., habitSlot.captured.question)`) inside
    `a valid save creates a new habit with a trimmed name and a DAILY schedule` — drop both lines, keep
    the rest of the test (name/notes assertions untouched).
  - line 118 and line 144 — `Habit(...)` fixtures in `starting an edit loads...` /
    `saving an edited habit calls update...` — drop the `question = ...` line from each.
  - lines 434-443, test `editing the guiding question makes the form dirty and undoing it makes it clean again`
    — **delete this entire test**; its only subject is `onQuestionChange`, which will no longer exist.
  - line 606 — another `Habit(... question = "Did you read?" ...)` fixture — drop the line.
- `app/src/test/kotlin/com/jjrapps/constanza/habit/HabitListViewModelTest.kt:35`,
  `app/src/test/kotlin/com/jjrapps/constanza/progress/ProgressViewModelTest.kt:66`,
  `app/src/test/kotlin/com/jjrapps/constanza/scheduling/OccurrencePlannerTest.kt:56`,
  `app/src/test/kotlin/com/jjrapps/constanza/tracking/TodayViewModelTest.kt:62` — all `question = null`
  fixture lines, mechanical drop, no assertion depends on the value.
- `app/src/test/kotlin/com/jjrapps/constanza/portability/BackupImporterTest.kt:115` and
  `app/src/test/kotlin/com/jjrapps/constanza/portability/BackupImporterNormalizationTest.kt:50` — drop the
  fixture field / JSON line (see settled constraint 4).
- `app/src/test/kotlin/com/jjrapps/constanza/reminding/NotificationPosterTest.kt:101` —
  `poster.postReminder(OCCURRENCE_ID, "Meditate", "Did you meditate today?", 0)` — arity-only fix, no
  content assertion in this file (it only tests the `canPost` gate table plus the gated-skip path).

### Instrumented tests

- `app/src/androidTest/kotlin/com/jjrapps/constanza/core/data/AppDatabaseMigrationTest.kt:114` — the raw
  `INSERT INTO habits (..., question, ...)` seed SQL stays **as-is**: it seeds against the checked-in
  **v1** schema, which still has the column. **This file also GAINS a 2 to 3 case** (settled constraint 5,
  reversed): it must seed a v2 database with a habit plus at least one row in each of the four
  CASCADE-child tables, run the migration, and assert three things — the `question` column is gone, the
  pre-migration snapshot fired, and **every child row survived** (the CASCADE trap). Its seed SQL is the
  single largest authored-line item in the migration slice.
- `app/src/androidTest/kotlin/com/jjrapps/constanza/habit/HabitRepositoryCrudTest.kt:63,69` —
  `stored.copy(..., question = "Did you read?", ...)` and `assertEquals("Did you read?", updated.question)`
  prove a repository update actually changes a field; once `question` is gone this specific assertion must
  be dropped or repointed at a remaining field — needs a design-phase call, but the `question` line must
  go either way.
- `app/src/androidTest/kotlin/com/jjrapps/constanza/habit/HabitRepositoryDeleteSlotTest.kt:41,74`,
  `HabitListArchiveComposeTest.kt:47`, `HabitRepositoryTestFixture.kt:212`,
  `core/data/EntryDaoUniqueConstraintTest.kt:41,77`, `core/ui/component/HabitColorDotComposeTest.kt:91`,
  `scheduling/ReconcileWorkerTest.kt:82`, `scheduling/MidnightSweepWorkerTest.kt:77`,
  `portability/PortabilityTestFixture.kt:57` — all mechanical `question = null` fixture drops.
- `app/src/androidTest/kotlin/com/jjrapps/constanza/scheduling/ReminderWorkerTestFixtures.kt:26,29` —
  shared `insertHabitWithSchedule(..., question: String? = null, ...)` helper; drop the parameter and the
  `question = question,` argument into `HabitEntity(...)`. Used by `ReminderFireWorkerTest` and
  `SpanishColdProcessNotificationInstrumentedTest` (both below).
- `app/src/androidTest/kotlin/com/jjrapps/constanza/scheduling/ReminderFireWorkerTest.kt:71,90` — drop the
  `question = "Did you exercise?"` named argument from its `insertHabit` helper, and update
  `coVerify { notificationPoster.postReminder(occId, "Exercise", "Did you exercise?", 0) }` to the new
  three-argument `postReminder` signature.
- `app/src/androidTest/kotlin/com/jjrapps/constanza/reminding/NotificationPosterInstrumentedTest.kt:90,119`
  and `app/src/androidTest/kotlin/com/jjrapps/constanza/reminding/NotificationActionWiringInstrumentedTest.kt:109`
  — arity-only `postReminder(...)` call-site fixes.
- **`app/src/androidTest/kotlin/com/jjrapps/constanza/localization/SpanishColdProcessNotificationInstrumentedTest.kt:99,117-123`
  — HIGH RISK.** This is, by its own KDoc, "the headline test for this whole [app-localization] change."
  It currently asserts `posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()`
  equals the Spanish default question `"¿Lo has hecho?"` (line 119) and separately asserts the three
  Spanish action labels and the Spanish channel name (untouched by this change). Once `question` and its
  default string are gone, `EXTRA_TEXT` will no longer carry that string — this assertion must be
  rewritten to match whatever the chosen notification shape actually puts in
  `EXTRA_TITLE`/`EXTRA_TEXT`/`EXTRA_BIG_TEXT`, and it must keep proving Spanish-locale rendering survives
  a cold process with no Activity created (its actual purpose) rather than being deleted.
- `app/src/androidTest/kotlin/com/jjrapps/constanza/seed/LiveSnoozeAcrossMidnightSeed.kt:42-43,217` and
  `app/src/androidTest/kotlin/com/jjrapps/constanza/seed/ImminentReminderSeed.kt:35-36,186` — manual
  on-device seed scripts (used by the manual verification recipe already documented under
  `openspec/config.yaml`'s `testing.instrumented.device_free_matrix.limits`); each defines a
  `SEED_HABIT_QUESTION` constant and passes `question = SEED_HABIT_QUESTION` — drop the constant and the
  argument in both.

## Settled constraint 6 — notification shape

**SETTLED** by the maintainer from measured renders on an API 37 emulator. This supersedes the option
table that used to stand here (A: title-only; B: `setBigContentTitle`; C: name in the body). Option B was
this exploration's recommendation and it was **rejected on measurement**. `sdd-propose`/`sdd-spec` take
the shape below as a premise and lock `NotificationPoster`'s new signature and the
`SpanishColdProcessNotificationInstrumentedTest` rewrite against it.

### The decided shape

| Builder call | Value |
|---|---|
| `.setContentTitle(...)` | a fixed localized string — ES `Seguimiento de hábitos`, EN `Habit tracker` |
| `.setContentText(...)` | `habitName` |
| `.setStyle(...)` | `NotificationCompat.BigTextStyle().bigText(habitName)` |

The three actions, `REMINDER_CHANNEL_ID` and the notification id are **UNCHANGED**.

### Why — the maintainer's reasoning, and the measurements behind it

The choice is a slot-width choice, and the two slots are not the same width. Measured on API 37:

- The **collapsed title slot caps at 527 px** and shares its row with the timestamp.
- The **body slot is 765 px** and carries no timestamp.

Moving the habit name out of the title and into the body therefore takes the visible characters of the
maintainer's real 56-character habit from **29 to 44** — a 15-character gain bought purely by using the
wider slot. The fixed title string costs nothing: `Seguimiento de hábitos` measures **387 px** against a
527 px slot, so it never truncates and leaves 27% spare.

**Rejected, with the measured cost that rejected each:**

| Rejected | Cost |
|---|---|
| Name as title (the old options A and B) | 29 visible characters instead of 44, and an empty band where the body would be. The card is only 11 px shorter — it pays 15 characters to buy 11 pixels. |
| Name in both title and body | The same sentence truncated twice and stacked. |

`BigTextStyle().bigText(habitName)` is what lets the expanded view wrap the full name instead of clipping
it. Note this reverses the exploration's earlier objection to "moving the name to the body loses the bold
title weight": with a fixed title string there is now a natural title, so nothing is invented and no slot
is left empty — which was the whole basis of that objection.

### Direct consequence for `SpanishColdProcessNotificationInstrumentedTest`

`EXTRA_TEXT` no longer carries `"¿Lo has hecho?"`; it carries the habit name. The existing assertion is
rewritten to assert the Spanish habit name in `EXTRA_TEXT` (and/or `EXTRA_BIG_TEXT`), and the new fixed
Spanish title in `EXTRA_TITLE`. The test keeps proving Spanish-locale rendering survives a cold process
with no Activity created. It is never deleted.

### Retained platform facts (unchanged by the decision)

The three actions (yes/no/snooze) are added
via independent `.addAction(...)` calls (`NotificationPoster.kt:146-150`), `REMINDER_CHANNEL_ID = "reminders"`
is a private top-level const (`NotificationPoster.kt:17`), and the notification id is the caller-supplied
`occurrenceId` (`NotificationPoster.kt:93-95`) — none of these three are touched by the title/text/style
choice.

The *collapsed* (shade) view is single-line and ellipsized in both the title and the body slot, regardless
of style; only the *expanded* view (manual pull-down, or a transient heads-up peek on this channel's
`IMPORTANCE_HIGH`) can wrap. No `NotificationCompat` builder call removes collapsed-state ellipsis — that
is a genuine platform limit, and the decision above does not claim to remove it. What the decision buys in
the collapsed view is a **wider slot** (765 px vs 527 px, no timestamp competing for the row), which is why
the character count moves from 29 to 44 while the line stays a single ellipsized line. In the expanded
view, `bigText` wraps the name in full.

This repo has zero pre-existing `BigTextStyle` usage (grepped `app/src`), so `BigTextStyle` behaviour here
was not repo-evidenced when this exploration was written — it has since been confirmed by the API 37
renders that produced the pixel measurements above. Per this repo's standing practice, One UI rendering
specifically remains outside the automated matrix's proof
(`openspec/config.yaml`'s `testing.instrumented.device_free_matrix.limits`), so a One UI check stays a
manual step, not an unresolved decision.

## Addendum — two more `HabitListScreen.kt` row defects folded into this change

Maintainer scope addition, mid-exploration. Both touch `habit/HabitListScreen.kt:184-229` (`HabitRow`'s
`ListItem`), the same composable this change already edits for `supportingContent` removal.

**Out of scope** (separate branch `fix/habit-list-back-navigation`): the screen's missing back navigation —
do not touch `TopAppBar` or `HabitListRoute`'s signature.

### Defect 1 — habit name starved of width

Symptom: maintainer S25 screenshot, a long habit name wraps to roughly nine lines at about ten characters
wide.

Root cause, confirmed by reading `HabitRow` (`HabitListScreen.kt:192-228`): `trailingContent` is a `Row`
holding **two** always-visible `TextButton`s ("Progress", "Archive"/"Un-archive") plus a
`Box{IconButton+DropdownMenu}` (`MoreVert`). Material3's `ListItem` gives its middle headline/supporting
column the *remaining* width after leading and trailing are measured, but does not itself bound how much
width `trailingContent` may claim — so three always-visible controls (two text buttons whose label width
varies with locale, e.g. Spanish "Progreso"/"Archivar", plus an icon button) can consume most of a narrow
row and starve the headline column to near nothing. This is Compose Material3 library layout behaviour (not
this repo's source — `ListItem` is a library composable, no local override exists), stated at the confidence
level of "explains the observed symptom", not verified by reading Material3's own internals.

**Interaction with this change's own scope, as flagged by the maintainer:** removing `supportingContent`
(the `question` line) frees *vertical* space (one fewer text row) but does nothing about this *horizontal*
starvation — they are orthogonal problems on the same `ListItem`.

Fix options:

1. **Fold Archive into the overflow menu (that is Defect 2 below) plus bound the headline with
   `maxLines = 2, overflow = TextOverflow.Ellipsis`.** Removes one of the two always-visible `TextButton`s,
   materially shrinking trailing's claimed width, and caps the name at "at least one full line, ideally up
   to two" per the ask. Effort: Low. Risk: may still be tight for a very long name with the
   "Progress"/"Progreso" `TextButton` remaining inline — not guaranteed without a real rendered check (per
   this project's own convention: never offer a layout choice without rasterising it at real size).
2. **Also move Progress into the overflow menu**, leaving `trailingContent` as *only* the fixed ~48dp
   `MoreVert` `IconButton` — the most width freed for the headline while staying inside `ListItem`, same
   `DropdownMenuItem` pattern already used for Delete. Effort: Low (mechanical). More certain fix if option 1
   proves insufficient on the maintainer's actual reported long name.
3. **Replace `ListItem` with a custom `Row`**, explicit `Modifier.weight(1f)` on the name column, intrinsic
   width on trailing controls. Most robust fix regardless of future control count or locale, but diverges
   from `ListItem`'s built-in Material styling (paddings, min-height, text-style roles, ripple) which would
   need manual replication to avoid a visual regression. Effort: Medium-High. Not recommended unless 1 or 2
   prove insufficient.

**Resolved: option 2** — see settled constraint 7 below, which was decided from real renders and supersedes
this section's earlier "try option 1 first" recommendation.

### Defect 2 — "Archive" moves behind the overflow menu, deliberately superseding a ratified decision

**SETTLED** by the maintainer: Archive moves into the overflow menu.

The prior decision it supersedes was found and is quoted here.
`openspec/changes/archive/2026-09-03-habit-deletion/design.md:41-53`, decision **D2 — "Affordance: Progress
and Archive stay inline; Delete goes behind a `MoreVert` overflow"**:

> **Choice.** `HabitRow`'s `trailingContent` keeps its two `TextButton`s and gains a trailing
> `IconButton(Icons.Filled.MoreVert)` opening a `DropdownMenu` whose single item is Delete.
>
> | Alternative rejected | Cost that rejected it |
> |---|---|
> | Third `TextButton` beside Archive | Widest option on a 360dp row, and it gives an irreversible action the same visual weight and one-tap cost as a reversible one — feeding the "user deletes intending to archive" risk directly. |
> | Move all three actions into the overflow | Cleanest row, but it relocates the Archive label that `CoreFlowE2ETest:329` and `HabitListArchiveComposeTest` locate by text. The spec requires archiving behave exactly as before; churning its tests to ship a delete is the wrong bill to pay. |

`HabitRow`'s own KDoc in the live file (`HabitListScreen.kt:176-182`) restates this verbatim as the reason
Archive stays inline. **The maintainer is deliberately superseding D2**, for a reason D2 itself never
weighed: row real estate / horizontal starvation (Defect 1). D2 was written purely about tap-cost and visual
weight parity between reversible and irreversible actions, not about the row running out of width.

**What happens to D2's underlying principle.** D2's concern was that an irreversible action (Delete) must
not share a reversible action's (Archive's) one-tap visual weight and cost. Once Archive also moves into the
`DropdownMenu`, Archive and Delete become adjacent items in the *same* menu, reachable by the *same* two-tap
gesture (open menu, tap item) — the tap-count distinction D2 built around is gone. The underlying safety
property is *not* gone, though: Delete still gates through its own `DeleteHabitDialog` confirmation
(`HabitListScreen.kt:101-111`) before anything destructive happens, while Archive has no such gate and
remains reversible by nature (un-archive undoes it) — so a mis-tap on Archive costs nothing irreversible,
and a mis-tap on Delete still stops at a confirming dialog. The residual, genuinely new risk worth flagging:
Archive and Delete are now vertically stacked in one `DropdownMenu`, which is a smaller mis-tap-between-
adjacent-items surface than before (when Archive was spatially separate, outside any menu) — a real but
modest increase in mis-tap risk between two menu rows, not a reintroduction of "irreversible action costs as
little as a reversible one."

**Test blast radius for Defect 2 — verified and extended beyond the two sites originally named:**

- `app/src/androidTest/kotlin/com/jjrapps/constanza/e2e/CoreFlowE2ETest.kt:336` —
  `compose.onNodeWithText(string(R.string.habit_list_archive)).performClick()` inside
  `removingAHabitThroughTheUiTakesItOffTodayAndOutOfTheSchedule`. Must open the menu first — this exact test
  class already has the right precedent two-step pattern at its own lines 377-378 for Delete:
  `onNodeWithContentDescription(habit_list_more_options).performClick()` then
  `onNodeWithText(habit_list_delete).performClick()`.
- `app/src/androidTest/kotlin/com/jjrapps/constanza/habit/HabitListArchiveComposeTest.kt` — **three** sites,
  not one: line 89 (`onNodeWithText(archiveLabel).performClick()`), line 95
  (`onNodeWithText(unarchiveLabel).performClick()`), and line 101
  (`onNodeWithText(archiveLabel).assertExists()` at the end of the round-trip test) — all three need the
  menu opened (and, for the final `assertExists()`, either the menu still open or reopened) since a
  `DropdownMenu` auto-dismisses after an item click, matching the existing `menuExpanded = false` pattern
  Delete's `DropdownMenuItem.onClick` already uses (`HabitListScreen.kt:218-221`) — the new Archive
  `DropdownMenuItem` must do the same before calling `onArchiveToggle`.
- No test drives Progress by text today (grepped `habit_list_progress` under `app/src/androidTest` — zero
  hits), so moving Progress into the menu (settled constraint 7) carries no test blast radius of its own.
- `HabitDeleteDialogComposeTest.kt:64` already opens the overflow menu via
  `onNodeWithContentDescription(habit_list_more_options)` for Delete — confirming this is the established,
  reusable pattern for reaching a `DropdownMenuItem` in this codebase, not a novel one this change would
  introduce.

## Settled constraint 7 — the habit row's trailing controls

**SETTLED** by the maintainer from real renders. Neither "Progress" nor "Archive" stays inline.

### The decided row

`trailingContent` becomes the overflow `IconButton` **alone**. Progress, Archive/Un-archive and Delete are
all `DropdownMenuItem`s inside the one `DropdownMenu`. `supportingContent` is deleted. The name gets
`maxLines = 2, overflow = TextOverflow.Ellipsis`.

### Why — the measured evidence

| Measure | Before | After |
|---|---|---|
| Name column width | 509 px | **723 px** |
| Visible characters of the 56-char name | 45, ellipsized | **all 56** |
| `Andar al menos 8.000 pasos` | wraps | fits on one line |
| Row height | 168 px | 168 px |

The row-height figure is what decided it against keeping Progress inline: **the height is identical either
way**, so leaving Progress inline bought no vertical density — it only cost 214 px of name column. This is
the fact the exploration's earlier "try option 1 first, keep Progress inline" recommendation could not have
weighed without a render, and it is why that recommendation is superseded rather than merely refined.

`maxLines = 2` still applies: it bounds a name longer than the maintainer's 56-character one, which the
wider column postpones but does not eliminate.

## Risks

- **`SpanishColdProcessNotificationInstrumentedTest` is the highest-risk test in the blast radius.** It is
  the headline test of the app-localization change and its `EXTRA_TEXT` assertion breaks by construction. It
  must be rewritten to the new notification shape, never deleted.
- **The `Migration(2,3)` table rebuild can cascade-delete the whole database.** Four tables reference
  `habits` with `onDelete = CASCADE`; a `DROP TABLE habits` with foreign keys enforced takes every
  schedule, slot, entry and occurrence with it. `PRAGMA foreign_keys` is inert inside Room's migration
  transaction, so `defer_foreign_keys` is the applicable mechanism — `sdd-design` confirms it, and the
  2 to 3 migration test asserts child-row survival. See settled constraint 5.
- **`AppMigrations.kt:13-18` already reserves `Migration(2, 3)`** for the colour-remap rollback recipe.
  This change consumes that version, so the recipe must be re-pointed at `Migration(3, 4)` in the same
  commit or a future maintainer inherits a colliding instruction.
- **Collapsed-view single-line ellipsis is a platform limit no notification shape removes.** Settled
  constraint 6 widens the slot (527 px to 765 px, 29 characters to 44) but does not make the collapsed line
  wrap. Record it as a known limit, not an unmet requirement.
- **One UI rendering is still outside the automated matrix's proof.** The API 37 measurements behind settled
  constraints 6 and 7 do not transfer to Samsung's notification template or list styling; a One UI check
  stays a manual step, matching the limit `openspec/config.yaml` already documents.
- **Settled constraint 7 creates a modest new mis-tap risk** between Archive and Delete as adjacent
  `DropdownMenu` rows, now with Progress stacked above them. Delete's confirmation dialog still bounds the
  consequence, and Archive stays reversible.
- **`HabitRepositoryCrudTest.kt:63,69` loses its "an update actually changes a field" proof** unless the
  assertion is repointed at a remaining field. That repointing is a design-phase call, not decided here.

## Ready for proposal

Yes, without reservation. Nothing in this document is open.

All seven settled constraints carry forward as-is into propose, spec, design and tasks:

1. `question` removed from the product entirely
2. list-row `supportingContent` deleted
3. editor notes `minLines = 3, maxLines = 5`
4. no backup backward compatibility
5. Room v2 to v3 via a real `Migration(2,3)` carrying `PreMigrationSnapshotWriter` (**reversed
   2026-09-05** — the destructive fallback is dead)
6. notification shape — fixed localized title, habit name in the body, `BigTextStyle().bigText(habitName)`
7. habit row trailing content — overflow `IconButton` alone, all three actions in the menu, name
   `maxLines = 2`

Plus the settled Defect 2 supersession of `2026-09-03-habit-deletion/design.md` D2.

The blast radius in this document is roughly 35 `file:line` sites, not the 9 the originating
`openspec/config.yaml` item still lists. Line numbers cited here were captured during exploration and have
since drifted in at least `HabitListScreen.kt` (`HabitRow` now begins near line 236, `supportingContent` near
line 248); treat the file plus symbol name as authoritative and re-locate before editing.
