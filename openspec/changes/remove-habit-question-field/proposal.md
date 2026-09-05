# Proposal: Remove the `question` field from the habit model

## Intent

The habit name *is* the question, so the separate `question` line adds nothing and costs the
notification its widest slot. Remove `question` from the product, then spend the freed room on the
name — in the notification and in the habit list row, both of which clip it today.

Origin: `openspec/config.yaml` item `reminder-notification-shows-a-question-and-truncates-long-habit-names`.
All seven decisions are settled in `exploration.md`; this proposal re-opens none of them.

## Scope

### In Scope

- Remove `question` from `Habit`, `HabitEntity`, `Mappers`, `BackupHabit`, `BackupMapper`,
  `HabitEditorFormState`/`Screen`/`ViewModel`, `NotificationPoster.postReminder`, and both `strings.xml`.
- Room `version = 3` with a committed `3.json`, reached by a **real `AppMigrations.migration2To3(writer)`**
  following `migration1To2`'s factory shape and carrying the same `PreMigrationSnapshotWriter`, wired at
  `DatabaseModule.kt:36`. SQLite cannot drop a column in place, so this is a table rebuild: create without
  `question`, copy, drop, rename. Extend `AppDatabaseMigrationTest` (today 1→2 only) to cover 2→3,
  including the snapshot firing and child-row survival.
- Re-point `AppMigrations.kt:13-18`'s KDoc rollback recipe, which currently reserves `Migration(2, 3)` for
  the colour-remap inverse, at `Migration(3, 4)`.
- Notification: fixed localized title (ES `Seguimiento de hábitos` / EN `Habit tracker`), habit name as
  `contentText`, `BigTextStyle().bigText(habitName)`. Actions, channel id and notification id unchanged.
- `HabitRow`: `trailingContent` becomes the overflow `IconButton` alone; Progress, Archive/Un-archive and
  Delete all become `DropdownMenuItem`s. `supportingContent` deleted. Name `maxLines = 2` + `Ellipsis`.
- Editor notes field `minLines = 3, maxLines = 5`.

### Out of Scope

- Backward compatibility for backups carrying `question` — the app was never published. (`ignoreUnknownKeys = true`
  at `BackupImporter.kt:14` already makes a stray key harmless; the `BackupImporterTest.kt:115` fixture literal
  is cleaned regardless.)
- A destructive fallback, `FLAG_DEBUGGABLE` scoping, and any data loss. **Reversed 2026-09-05** — see
  Approach. No `openspec/config.yaml` footgun item is filed, because nothing is left to replace.
- Habit-list back navigation — merged-pending in PR #79 (`fix/habit-list-back-navigation`). Assume `onBack` exists.
- Collapsed-view single-line ellipsis. A platform limit; the change widens the slot, it does not remove ellipsis.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `habit-management`: "Habit Creation" (spec.md:11) drops "an optional guiding question".
- `reminder-response`: "Notification Actions" (spec.md:11,18-22) currently requires the **notification body**
  to render in the resolved language. After this change the body carries the user's habit name verbatim
  (never localized) and the localized fixed string is the **title**. The requirement and its Spanish
  cold-process scenario must both move that guarantee from body to title.
- `app-localization`: no requirement text changes. Two keys removed and one added, in both locales;
  `StringResourceParityTest` covers the symmetry.

**`data-portability` needs no delta.** An earlier draft of this proposal listed one. The first approach —
a destructive fallback — would have run no `Migration` object, so `PreMigrationSnapshotWriter` (wired only
into `migration1To2` at `DatabaseModule.kt:36`) would never have fired, breaking the ratified **Automatic
Pre-Migration Snapshot** requirement (`spec.md:91-110`). That conflict was resolved **by changing the
approach, not by amending the spec**: `migration2To3` carries the same writer, so the requirement is
satisfied as written. This is worth stating explicitly, because writing the delta was the available
shortcut and it would have weakened a data-safety guarantee to accommodate an implementation choice that
turned out to be wrong for an unrelated reason.

## Approach

### Reversed decision: the Room migration

Settled constraint 3 originally read *"destructive fallback, no hand-written `Migration(2,3)`, scoped to
debuggable builds."* It was **reversed on 2026-09-05**. Two findings forced it, and the second is decisive:

1. A destructive fallback runs no `Migration` object, so `PreMigrationSnapshotWriter` never fires and the
   ratified **Automatic Pre-Migration Snapshot** requirement breaks silently.
2. **The maintainer's phone runs release builds.** `app/build.gradle.kts:78-83` signs the release build and
   sets `isMinifyEnabled = true`; distribution is `assembleRelease` → signed APK → GitHub Releases. So
   `FLAG_DEBUGGABLE` is **false** on the only device that exists. The old constraint's two halves were
   mutually destructive: *"I accept losing the data"* assumed the fallback would run, while *"scope it to
   debug so releases fail loudly"* guaranteed it would not — the release build would meet a v2 database
   with no path to v3 and throw at open. Not a wipe: **a crash loop, an app that does not start**.

With the supposed saving gone, the real migration is the better trade on every axis: no data loss, the
snapshot fires, the spec is satisfied as written, no permanent footgun.

### Slicing

Three chained slices, sequential (they share `NotificationPoster`, `HabitListScreen` and `HabitEditorScreen`,
so they must not run in parallel). The `question` removal is **atomically compile-bound** — Kotlin gives no
partial-compilation escape, so every reader of the field changes in one commit. That forces the slice shape:

| # | Slice | Why this boundary | Est. authored lines |
|---|---|---|---|
| 1 | Notification shape | Rewrites `SpanishColdProcessNotificationInstrumentedTest` **exactly once**. Drops the `question` param and its call-site argument (so detekt sees no unused parameter) while `Habit.question` still exists. | 80–120 |
| 2 | Habit row + editor notes | Touches no `question` site. Independently verifiable and reversible. | 80–120 |
| 3 | The atomic removal + `Migration(2,3)` | Model → entity → mapper → Room v3 → backup → editor → strings → ~30 fixtures. Last because the Room bump is the least reversible step. | **250–360** |

Naive layer slicing (model / notification / UI) was rejected: it does not compile, and folding the
notification shape into the removal would rewrite the headline Spanish test twice.

**Slice 3 re-estimated after the reversal, and it is now the budget risk.** It was 150–220. The migration
adds roughly: `migration2To3` with this repo's KDoc density (~35–50), the `AppMigrations` rollback-recipe
KDoc fix (~10), `DatabaseModule` wiring (~3), and the `AppDatabaseMigrationTest` 2→3 case (~60–90 — it must
seed a v2 habit *plus* a row in each of the four CASCADE-child tables to prove they survive, and that seed
SQL is the single largest authored-line item in the change). `3.json` is generated and excluded from
authored risk count. New total: **250–360, plausibly over 400** if the migration test seeds all four child
tables verbosely.

**Do not treat this chain as final — `sdd-tasks` owns the decision.** Exits if slice 3 forecasts over 400:

1. **`size:exception`.** The strongest candidate. The unit is compile-atomic *and* the migration must land
   in the same commit as the entity change — Room generates `3.json` from the entity, so a migration that
   drops the column cannot precede the field's removal without failing `identityHash` validation.
2. **Table-drive the migration test seed** — one helper plus four child inserts instead of repeated literal
   SQL. An honest ~40-line reduction that costs no coverage.
3. Splitting the migration away from the removal is **not available**, for the reason in (1). Do not let a
   line budget talk anyone into shipping the version bump and the field removal separately.

### Superseded prior decision

This deliberately supersedes **D2** in `openspec/changes/archive/2026-09-03-habit-deletion/design.md:41-53`
— *"Progress and Archive stay inline; Delete goes behind a `MoreVert` overflow."* D2 rejected moving all
three into the menu on two grounds: an irreversible action must not share a reversible one's one-tap weight,
and moving Archive would churn the tests that locate it by text.

The maintainer overrides D2 on a variable D2 never weighed: **row width**. Measured: the name column goes
509 px → 723 px, the 56-character name goes from 45 ellipsized characters to all 56, and row height is
**identical (168 px) either way** — so inline controls bought no vertical density at all. D2's test-churn
objection stands but is now paid knowingly; `HabitDeleteDialogComposeTest.kt:64` already establishes the
open-menu-then-click pattern to reuse.

D2's safety property survives: Delete still gates through `DeleteHabitDialog`, and Archive is reversible.
`HabitRow`'s KDoc, which restates D2 verbatim as the reason Archive stays inline, must be rewritten.

### Stack ratification

Nothing in `config.yaml`'s `stack:` is treated as unratified. The one unratified element is the
pre-migration-snapshot interaction below.

## Affected Areas

Roughly **35 `file:line` sites**, not the 9 the originating config item lists — full list in `exploration.md`.
Line numbers there were captured during exploration and have drifted (e.g. `HabitRow` now begins near
`HabitListScreen.kt:236`); treat file + symbol as authoritative.

| Area | Impact | Description |
|------|--------|-------------|
| `domain/.../model/Model.kt` | Removed | `Habit.question` |
| `app/.../core/data/` | Modified | `HabitEntity`, `Mappers`, `AppDatabase` v3, new `app/schemas/.../3.json` |
| `app/.../core/data/migration/AppMigrations.kt` | New + Modified | `migration2To3` table rebuild; KDoc rollback recipe re-pointed to `Migration(3, 4)` |
| `app/.../core/di/DatabaseModule.kt` | Modified | Register `migration2To3` with the same `PreMigrationSnapshotWriter` |
| `app/.../core/data/AppDatabaseMigrationTest.kt` | Modified | New 2→3 case: column gone, snapshot fired, child rows survived |
| `app/.../portability/` | Modified | `BackupDto`, `BackupMapper` |
| `app/.../reminding/NotificationPoster.kt` | Modified | Title/body/`BigTextStyle`, param drop |
| `app/.../habit/HabitListScreen.kt` | Modified | `HabitRow` trailing content, name `maxLines`, KDoc rewrite |
| `app/.../habit/HabitEditor*.kt` | Modified | Question field removed, notes multiline |
| `app/src/main/res/values{,-es}/strings.xml` | Modified | 2 keys removed, 1 added, both locales |
| `openspec/specs/{habit-management,reminder-response,data-portability}` | Modified | Delta specs |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| **The table rebuild can cascade-delete the entire database.** `schedules`, `reminder_slots`, `entries` and `reminder_occurrences` all declare `ForeignKey(entity = HabitEntity::class, onDelete = CASCADE)` (`Entities.kt:30-34,53-57,79-83,110-114`). A `DROP TABLE habits` with foreign keys enforced takes every one of their rows with it — the exact loss this migration exists to prevent, through the back door. | High | `PRAGMA foreign_keys` is inert inside a transaction and Room runs `migrate()` in one, so the usual `foreign_keys=OFF` recipe is unavailable; `PRAGMA defer_foreign_keys = TRUE` is the applicable mechanism. **`sdd-design` must confirm it, and the 2→3 test must assert child-row survival** — that assertion is what actually catches this, whichever mechanism is chosen. |
| **`AppMigrations.kt:13-18` already reserves `Migration(2, 3)`** for the colour-remap rollback inverse. This change consumes version 3. | Certain | Re-point the recipe at `Migration(3, 4)` in the same commit. Otherwise a future maintainer inherits a rollback instruction that collides with a shipped version. |
| **`SpanishColdProcessNotificationInstrumentedTest.kt:99,117-123` breaks by construction.** Its `EXTRA_TEXT` assertion on `"¿Lo has hecho?"` cannot survive. | Certain | Rewrite to assert the Spanish habit name in `EXTRA_TEXT`/`EXTRA_BIG_TEXT` and the fixed Spanish title in `EXTRA_TITLE`. **Never delete it** — it is the headline regression test for the whole localization change, per its own KDoc. |
| **Slice 3 forecasts 250–360 authored lines against a 400 budget** and cannot be split without breaking compilation. | Medium | Named exits under Approach: `size:exception` first, table-driven test seed second. |
| **Archive and Delete are now adjacent menu rows**, a smaller mis-tap surface than when Archive sat outside any menu. | Medium | Accepted. Delete's confirmation dialog bounds the consequence; Archive is reversible. |
| **`HabitRepositoryCrudTest.kt:63,69` loses its "an update actually changes a field" proof.** | Certain | Repoint the assertion at a remaining field — a `sdd-design` call, not decided here. |
| Four Archive-by-text test sites break: `CoreFlowE2ETest.kt:336` and `HabitListArchiveComposeTest.kt:89,95,101`. | Certain | Open the menu first; reuse `HabitDeleteDialogComposeTest.kt:64`. The `DropdownMenu` auto-dismisses after a click, so the final `assertExists()` must reopen it. |
| One UI renders neither the notification template nor the list row like API 37. | Medium | Manual check on the S25, matching `testing.instrumented.device_free_matrix.limits`. Not matrix-provable. |

## Rollback Plan

Required — this touches persisted data.

1. **Slices 1 and 2** revert by `git revert` of the slice commit. No persisted state involved.
2. **Slice 3 no longer destroys data, but it is still not a clean `git revert`.** Reverting the code to
   `version = 2` does not undo the on-disk upgrade: Room refuses to open a file at a version *lower* than
   the one recorded in it, so the app would fail to open. `AppMigrations.kt:17-18` already states this rule
   explicitly ("Never revert `AppDatabase`'s `version` back to 1"), and it applies identically here.
3. **The forward-only rollback** is therefore `Migration(3, 4)` re-adding a nullable `question` column, not
   a revert. It restores the schema but not the values: the v2→v3 rebuild does not carry `question` data
   forward, by design, since the whole change is that the field no longer exists.
4. **The genuine recovery artifact is the pre-migration snapshot**, which now fires — that is the point of
   the reversal. It captures the v2 state, including `question`, before the rebuild touches anything. A
   manual export via the portability path remains a sensible belt-and-braces step but is no longer the
   *only* net.
5. **If the CASCADE trap escapes review**, the failure is total and immediate (every schedule, slot, entry
   and occurrence gone). The snapshot is the recovery path; the 2→3 test asserting child-row survival is
   what should stop it ever reaching a device.

## Dependencies

- PR #79 (`fix/habit-list-back-navigation`) merges first, or slice 2 will conflict in `HabitListScreen.kt`.
- Room must regenerate and the repo must commit `app/schemas/com.jjrapps.constanza.core.data.AppDatabase/3.json`.

## Success Criteria

- [ ] No `question` identifier remains in `domain/`, `app/src/main`, `app/src/test`, or `app/src/androidTest`,
      except the v1 seed SQL in `AppDatabaseMigrationTest.kt:114`, which stays as-is.
- [ ] `./gradlew check` and `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` are green.
- [ ] `SpanishColdProcessNotificationInstrumentedTest` still proves Spanish cold-process rendering, against
      the new slots.
- [ ] `3.json` is committed and `AppDatabaseMigrationTest`'s 1→2 case still passes.
- [ ] `AppDatabaseMigrationTest`'s new 2→3 case proves all three: the column is gone, the pre-migration
      snapshot fired, and every CASCADE-child row survived the rebuild.
- [ ] A release build upgrading a real v2 database keeps its habits, schedules, slots and entries.
- [ ] `AppMigrations.kt`'s KDoc rollback recipe names `Migration(3, 4)`, not `Migration(2, 3)`.
- [ ] The maintainer's 56-character habit renders in full in the list row, and at 44+ characters in the
      collapsed notification.
- [ ] Delta specs exist for `habit-management` and `reminder-response`. `data-portability` needs none —
      its snapshot requirement is satisfied as written.
