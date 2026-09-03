# Design: Habit Deletion

## Technical Approach

One new repository method, one overflow affordance, one confirmation dialog. No new layer, no new
module, no schema change. The non-mechanical decisions are D1–D5 below. I read all six scenarios in
`specs/habit-management/spec.md` and believe every one is correct and literally satisfiable; nothing
below designs around a scenario, and the Spec Conformance table names the mechanism for each.

## Architecture Decisions

### D1 — Alarm ordering: snapshot armed ids, cascade inside the transaction, cancel after commit

**Choice.** `HabitRepository.delete(habitId)` reads `reminderOccurrenceDao.findByHabitId(habitId)`
and keeps the ids **before** opening the transaction; deletes the habit row inside
`database.withTransaction`, letting SQLite cascade `schedules`, `reminder_slots`, `entries`,
`reminder_occurrences`; then calls `alarmScheduler.cancel(id)` for each snapshotted id **after** the
transaction returns. No `replanAll()`.

This is `BackupImporter.replaceAll` (`portability/BackupImporter.kt:93-111`) applied to one habit. I
verified both readings the proposal asserted. `setArchived` (`habit/HabitRepository.kt:98`) calls
`occurrencePlanner.replanAll()` *inside* its transaction, which works for archiving only because the
row survives: `planHabit` sees `habit.archived` and routes to `cancelAllFor`, which reads
`reminder_occurrences` and cancels each. Copied here, the cascade has already deleted those rows
before `replanAll` could read them, so it would cancel nothing and every alarm would stay armed.
`replaceAll` instead snapshots ids at `:94-96`, cascades at `:104`, cancels post-commit at `:109` —
the same shape, proven in production by replace-all import. `replanAll()` is deliberately absent: no
other habit's schedule, slots or occurrences change, so a full replan would be a whole-table rescan
to reproduce the state it started from.

| Alternative rejected | Cost that rejected it |
|---|---|
| Widen `OccurrencePlanner.cancelAllFor` and call it *before* the transaction | Avoids a new `AlarmScheduler` dependency, but inverts the failure mode. Process death between cancel and commit leaves a **live** habit silently disarmed until the next replan trigger. The chosen order fails the other way: an orphan armed alarm, which `ReminderFireWorker.fire:39-41` swallows twice over (`occurrence ?: return`, then `habit ?: return`) for one wasted wakeup. Benign beats silent. |
| `replanAll()` after commit | Correct but wasteful, and it re-couples deletion to every other habit's plan — the coupling that made the archiving path hard to reason about here in the first place. |
| Cancel inside the transaction | `AlarmManager` is not transactional. A rolled-back transaction would leave the alarms cancelled anyway, so the atomicity is imaginary. |

**Dependency consequence.** `HabitDaos` gains `reminderOccurrenceDao` (5 fields); `HabitRepository`'s
constructor gains `alarmScheduler: AlarmScheduler` (6 params — `LongParameterList` is unconfigured in
`config/detekt/detekt.yml`, so the default constructor threshold of 7 applies and this stays under).

### D2 — Affordance: Progress and Archive stay inline; Delete goes behind a `MoreVert` overflow

**Choice.** `HabitRow`'s `trailingContent` keeps its two `TextButton`s and gains a trailing
`IconButton(Icons.Filled.MoreVert)` opening a `DropdownMenu` whose single item is Delete.

| Alternative rejected | Cost that rejected it |
|---|---|
| Third `TextButton` beside Archive | Widest option on a 360dp row, and it gives an irreversible action the same visual weight and one-tap cost as a reversible one — feeding the "user deletes intending to archive" risk directly. |
| Move all three actions into the overflow | Cleanest row, but it relocates the Archive label that `CoreFlowE2ETest:329` and `HabitListArchiveComposeTest` locate by text. The spec requires archiving behave exactly as before; churning its tests to ship a delete is the wrong bill to pay. |

`Icons.Filled.MoreVert` and `Icons.Filled.Delete` are both in `material-icons-core`, so this needs no
`material-icons-extended` (the constraint `DataPortabilityScreen:31-32` records), and the ~48dp
`IconButton` costs less width than a third `TextButton`.

**`ConstanzaControlDefaults` obligation: none arises, by choice.** `ControlStrokeCallSiteTest`'s
`GUARDED_CONTROLS` covers `OutlinedIconButton`, not the filled/standard `IconButton`, and
`DropdownMenu`/`DropdownMenuItem` draw no border, so this introduces no outlined control and no new
stroke. If an implementer reaches for `OutlinedIconButton` instead, that guard fails the build and
the fix is `border = ConstanzaControlDefaults.outlinedButtonBorder(…)`, not a suppression. All new
spacing uses `Spacing`/`Dimens`; no raw `.dp` in new code.

### D3 — The count is resident list state, never a query at dialog-open time

**Choice.** `EntryDao` gains `observeCountsByHabit(): Flow<List<HabitEntryCount>>`
(`SELECT habitId, COUNT(*) … GROUP BY habitId`). `HabitListViewModel` folds it into the existing
`combine`, exposing `entryCounts: Map<Long, Int>` on `HabitListUiState`. Opening the dialog reads
`state.entryCounts[habit.id] ?: 0` — a map lookup, no I/O, no suspension. A slow query cannot delay
the dialog because no query runs when the dialog opens.

**Why `?: 0` is honest rather than a guessed default.** `combine` emits nothing until *every* source
has emitted at least once, so a rendered habit row already implies the counts flow has produced a
value, and a habit absent from a `GROUP BY` result is absent precisely because it has zero entries.
There is no window in which a habit with history renders a false zero.

| Alternative rejected | Cost that rejected it |
|---|---|
| `suspend fun countFor(habitId)` awaited on menu tap | Puts an await between tap and dialog, and opens a window where the user confirms against a count that has not arrived. |
| Dialog opens immediately, count fills in asynchronously | Needs copy for a not-yet-known state, which "states the exact number" leaves no room for. |
| Per-habit `COUNT(*)` in the row mapper | N queries per emission instead of one aggregate. |

**Cost accepted.** One aggregate re-runs on every `entries` write while the list is observed. At this
app's scale (~1 row per habit per day) that is negligible.

### D4 — The confirmation dialog

Same M3 `AlertDialog` shape as `HabitEditorScreen`'s `DiscardChangesDialog:299-311` and
`DataPortabilityScreen`'s import confirmation, so it adds no theming surface. What differs is what is
at stake: the discard dialog risks unsaved edits that retyping recovers, so it names neither subject
nor quantity; this one destroys persisted records irreversibly, so it names the habit and the exact
entry count.

**Zero, worded honestly.** One `<plurals>` resource (the project's first), `one`/`other` only.
English has no CLDR `zero` category, so 0 falls to `other` and renders literally "0 recorded
answers" — exactly what spec scenario 2 asserts. A dedicated zero-case string is rejected: it would
be a copy branch, and the spec's "Deletion MUST behave identically … only the confirmation's stated
count differs" is a statement that nothing branches on the count.

**Confirm button** is labelled with the verb ("Delete"), not recoloured to `error`: `error`-on-dialog
contrast is not in `ColorContrastTest`'s audited set and accent is chrome-only, so an unaudited
destructive colour is scope the palette work has not paid for.

**Pending state** is `var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }` in
`HabitListScreen`, matching both existing dialogs' local-state precedent and surviving rotation.
Resolving the habit by id from `state.habits` means a habit that leaves the list dismisses its own
dialog rather than rendering a stale name.

### D5 — What replaces `CoreFlowE2ETest:309-317`

That comment's load-bearing claim — "nothing anywhere deletes a habit row, by design" — becomes false
with this change, and the maintainer reversed it knowing the history cost. Deleting the paragraph is
not enough: it justified *why the test asserts the habit row survives*, and that assertion is still
correct. Only its reason changes.

- **Rewritten rationale** for `removingAHabitThroughTheUiTakesItOffTodayAndOutOfTheSchedule`: the row
  survives because archiving is the **reversible** gesture and preserves every record it touches, not
  because the app lacks a delete. Its "gone from the database" scope narrows honestly to the
  scheduling rows `cancelAllFor` clears, and the `"archiving is this app's removal gesture"` assertion
  message at `:337` is reworded for the same reason.
- **New sibling E2E**, `deletingAHabitThroughTheUiRemovesItAndItsHistoryAndSilencesItsReminder`,
  carrying the counterpart rationale: delete is the irreversible gesture, and the only recovery is a
  pre-delete backup via `data-portability`'s full-replace `Import`.
- `REMOVED_HABIT` splits into `ARCHIVED_HABIT` and `DELETED_HABIT`; "remove" is now ambiguous.

## Data Flow

    HabitRow overflow ──tap──> pendingDeleteId = habit.id
        └─> DeleteHabitDialog(name, entryCounts[id] ?: 0) ──confirm──> HabitListViewModel.delete
              └─> HabitRepository.delete
                    (1) snapshot armed occurrence ids
                    (2) withTransaction { habitDao.deleteById }
                          └─cascade─> schedules, reminder_slots, entries, reminder_occurrences
                    (3) after commit: alarmScheduler.cancel(id) per snapshotted id

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `habit/HabitRepository.kt` | Modify | `delete(habitId)`; `HabitDaos` + `reminderOccurrenceDao`; constructor + `alarmScheduler` |
| `core/data/dao/Daos.kt` | Modify | `EntryDao.observeCountsByHabit()`, `HabitEntryCount` projection; first production caller of `HabitDao.deleteById` |
| `habit/HabitListViewModel.kt` | Modify | `delete(habitId)`; `entryCounts` folded into `combine`; `HabitListUiState.entryCounts` |
| `habit/HabitListScreen.kt` | Modify | Overflow menu, `DeleteHabitDialog`, `HabitListActions.onDeleteHabit`, saveable pending state |
| `res/values/strings.xml` | Modify | Menu label, dialog title, body plural, confirm label (dismiss reuses `action_cancel`) |
| `androidTest/.../habit/HabitRepositoryTestFixture.kt` | Modify | Hoist the relaxed `AlarmScheduler` mock to a field shared by planner and repository so cancellation is verifiable; wire the two new constructor arguments |
| `androidTest/.../e2e/CoreFlowTestFixture.kt` | Modify | Bounded negative wait (`assertNoNotificationPosted(id)`) |
| `androidTest/.../e2e/CoreFlowE2ETest.kt` | Modify | Per D5 |

## Interfaces / Contracts

```kotlin
// habit/HabitRepository.kt
/** habit-management: Habit Deletion. Irreversible; see design D1 for why the alarm cancellation
 *  runs after the transaction rather than inside it. */
suspend fun delete(habitId: Long) {
    val armedIds = daos.reminderOccurrenceDao.findByHabitId(habitId).map { it.id }
    database.withTransaction { daos.habitDao.deleteById(habitId) }
    armedIds.forEach { alarmScheduler.cancel(it) }
}

// core/data/dao/Daos.kt
data class HabitEntryCount(val habitId: Long, val count: Int)

@Query("SELECT habitId, COUNT(*) AS count FROM entries GROUP BY habitId")
fun observeCountsByHabit(): Flow<List<HabitEntryCount>>
```

## Spec Conformance

| Scenario | Mechanism |
|---|---|
| Deleting a habit with history removes it and all its records | D1 step 2 — four `ForeignKey.CASCADE` declarations |
| Deleting a habit with no history behaves the same | No count branch anywhere (D4); same `delete` path |
| Confirmation states the exact recorded-answer count | D3 resident count + D4 plural |
| Declining changes nothing | Nothing runs before confirm — not even the count query (D3) |
| No reminder fires for a deleted habit | D1 step 3 cancels; `ReminderFireWorker.fire:39-41` is the second line |
| Deletion does not affect archiving | `delete` never reads or writes `archived`; no `replanAll()` (D1) |

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit (`src/test`) | Nothing new required | `ControlStrokeCallSiteTest` and `ViewModelTeardownCallSiteTest` already guard the two call-site conventions this change touches; both must stay green |
| Instrumented repository (`src/androidTest`, in-memory Room) | Cascade; alarm cancellation; archiving untouched | Assert all four child tables empty after `delete`. `verify { alarmScheduler.cancel(id) }` per snapshotted id — this is where cancellation itself is proven. Delete one of two habits, assert the archived one's flag, `archivedAt` and entries are unchanged |
| Compose (`src/androidTest`, ViewModel built via `HabitRepositoryTestFixture.register`) | Dialog content and decline | Overflow opens; dialog shows the name and "7 recorded answers"; a zero-history habit shows "0 recorded answers"; declining leaves every table unchanged |
| E2E (`CoreFlowE2ETest`, device-free matrix) | Full flow and reminder silence | See below |

**Proving "no reminder fires" without the wall clock.** Read
`fixture.latestArmedOccurrenceFor(habit.id)` **before** deleting — after deletion there is no row to
read. Delete through the UI, then call `fixture.fireArmedAlarmFor(occurrence)`, which sends
`ReminderFireReceiver` exactly the broadcast `AlarmManager` would send at fire time
(`CoreFlowTestFixture:156-160`), and assert with a bounded negative wait that no notification with
that id is ever posted. State the division of proof in that test's KDoc, because the shape invites
over-claiming: driving the broadcast bypasses `AlarmManager`, so this proves the **second** line of
defence (the handler finds no occurrence and no habit, and returns). That `AlarmScheduler.cancel` was
actually called — the first line — is proven at the repository layer by mock verification, not here.

Whole suite green on `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` (API 31 + API 37, nothing
attached).

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary.

## Migration / Rollout

**No Room migration, confirmed rather than assumed.** All four child entities already declare
`onDelete = ForeignKey.CASCADE` against `HabitEntity` — `Entities.kt:34` (schedules), `:57`
(reminder_slots), `:83` (entries), `:114` (reminder_occurrences) — and the cascade runs in production
today via `HabitDao.deleteAll()`. This change adds only a DAO query and a projection data class;
Room's schema identity hash is computed from entities and views, not DAO methods, so
`app/schemas/…/2.json` is unchanged and no new schema file is generated. If a schema file does appear
in the diff, the entity set changed unintentionally and this section is wrong — stop and reopen the
proposal rather than writing a migration.

No feature flag, no phased rollout. Deletion is irreversible in-app by design; the only recovery is
importing a pre-delete backup, which `data-portability`'s full-replace `Import` already guarantees.

## Open Questions

None blocking.
