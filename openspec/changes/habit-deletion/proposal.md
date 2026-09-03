# Proposal: Habit Deletion

## Intent

Constanza can archive a habit but cannot delete one. Archiving fits a habit paused over a holiday.
It does not fit the two cases the maintainer named: a habit created by mistake, and a habit
deliberately discarded. Delete means delete — the habit and all its history go together, behind a
confirmation that states what is lost. Settled, not reopened here.

## Scope

### In Scope
- `HabitRepository.delete(habitId)`: cascade delete plus alarm cancellation.
- Delete affordance on the habit list, beside archive.
- Confirmation dialog naming the habit and its recorded-answer count.
- An entries `COUNT(*)` query for that dialog.
- `habit-management` spec delta; unit and device-free instrumented coverage.
- Correct `CoreFlowE2ETest`'s standing claim that "archive IS this app's removal gesture".

### Out of Scope
- Undo, trash, tombstones, or an export-before-delete prompt.
- Any change to archiving's semantics.
- Bulk delete; deleting from the habit editor.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `habit-management`: ADD `Habit Deletion`; MODIFY `Habit Archiving` so "never a deletion" reads as
  scoped to archiving rather than forbidding a separate delete.

Checked, and deliberately unchanged: `habit-entry-tracking` (no requirement asserts history
permanence), `habit-progress` (pure functions over supplied history — deletion removes caller and
input alike, unlike archiving), `data-portability` (`Import` already mandates full replace, so an
older backup restores a deleted habit by design).

## Approach

1. Read the habit's armed occurrence ids and entry count **before** writing.
2. Delete the habit row in one transaction; SQLite cascades schedule, slots, entries, occurrences.
3. Cancel those alarms **after** the transaction commits.

This is `BackupImporter.replaceAll`'s proven ordering, not archiving's. `setArchived` calls
`replanAll()` inside its transaction; here that is useless, because `cancelAllFor` reads occurrence
rows the cascade already removed. No `replanAll()` — no other habit's plan is affected.

**Affordance: habit list only.** The editor's `BackHandler` is interlocked with exactly one dialog,
create mode has no habit to delete, and delete belongs beside archive so the choice between them is
visible where it is made. The row's trailing area already holds two buttons; a third needs a design
decision (overflow menu recommended).

**Confirmation** names the habit and its recorded-answer count, rendering zero honestly. The count
is copy, not a behaviour branch.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `habit/HabitRepository.kt` | Modified | New `delete`; capture-then-cascade-then-cancel |
| `core/data/dao/Daos.kt` | Modified | Entry count query; `habitDao.deleteById` gains its first production caller |
| `habit/HabitListViewModel.kt` | Modified | Delete action, pending-confirmation state |
| `habit/HabitListScreen.kt` | Modified | Delete affordance, confirmation dialog |
| `res/values/strings.xml` | Modified | Dialog copy, count plural |
| `e2e/CoreFlowE2ETest.kt` | Modified | Its "no hard delete" assertion is now false |
| `openspec/specs/habit-management/` | Modified | Delta |

No Room schema change: all four child entities already declare `onDelete = CASCADE` and the cascade
is live in production today (it is what `deleteAll()`'s replace-all import relies on). Confirmed
against `app/schemas/.../2.json`. If a migration proves necessary, that reopens this proposal.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Process death between commit and cancel leaves an armed orphan alarm | Low | Bounded and benign: occurrence ids are `AUTOINCREMENT` so never reused, and `ReminderFireHandler.fire` does `findById(...) ?: return`. Worst case is one wasted wakeup |
| User deletes intending to archive | Medium | Irreversible-action dialog stating the habit name and exact count lost |
| Spec reads as self-contradictory if the archiving clause is left alone | High | It is in scope, not deferred |
| Cascade assumed rather than proven | Low | DAO-level test asserting all four child tables empty after delete |

## Rollback Plan

Revert the commit. No schema change, so nothing to migrate back and no data shape to repair. Already
deleted user data is **not** recoverable by rollback — a backup taken before the delete is the only
recovery path, and that is the intended semantics, not a gap.

## Dependencies

None. Stack treated as ratified per `openspec/config.yaml` (`testing.status: verified`); this change
introduces no new component and leaves nothing above it unratified.

## Size Forecast

Measured against the 800-line budget, not guessed. Code-only ≈ 350–450 changed lines (~120
production, ~230 test). All-in ≈ 650–800 including planning docs, which land in their own commits
and are excluded from the code PR per repository convention. Single PR; no chaining needed.

## Success Criteria

- [ ] Deleting a habit removes it and every schedule, slot, entry and occurrence row.
- [ ] Every alarm armed for that habit is cancelled; none fires afterwards.
- [ ] The confirmation states the habit name and exact recorded-answer count, and cancelling changes nothing.
- [ ] Archiving behaves exactly as before, and the spec reads unambiguously for both operations.
- [ ] `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` green on API 31 and API 37, nothing attached.
