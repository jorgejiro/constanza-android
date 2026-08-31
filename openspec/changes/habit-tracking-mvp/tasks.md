# Tasks: Habit-tracking MVP

Change: `habit-tracking-mvp` · Phase: `sdd-tasks` · Date: 2026-08-31
Inputs: `proposal.md`, `design.md`, `specs/*/spec.md` (8 capabilities), `openspec/config.yaml`

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~3,000–3,300 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | 10 work units, PR 1 → PR 10 (plus one code-free verification gate) |
| Delivery strategy | auto-chain (resolved before work unit 1 apply) |
| Chain strategy | stacked-to-main (resolved before work unit 1 apply) |

```text
Decision needed before apply: No — resolved: auto-chain / stacked-to-main
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High
```

### Suggested Work Units

| Unit | Goal | Est. lines | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|-----------|----------------------|-----------------|--------------------|
| 1 | Gradle scaffolding, pinned toolchain, detekt `ForbiddenMethodCall` rule | 320 | PR 1 | `./gradlew assembleDebug`, `./gradlew :domain:test` (empty suite) | N/A — app launches to an empty screen; no behavior to exercise | Delete scaffold files; no runtime state to unwind |
| 2a | `:domain` model + `dueOn` + `rollupDay`, JVM tests | 400 | PR 2 | `./gradlew :domain:test` | N/A — pure synchronous Kotlin | Revert model/predicate files; `:app` not yet coupled |
| 2b | `StreakCalculator` + `ComplianceCalculator`, JVM tests | 350 | PR 3 | `./gradlew :domain:test` | N/A — pure synchronous Kotlin | Revert the two calculator files independently |
| 3 | Room entities, DAOs, mappers, `HabitRepository` | 350 | PR 4 | `./gradlew :app:connectedDebugAndroidTest` | Inspect `app/schemas/1.json`; no scheduling exists yet | Drop Room module files; no armed alarms to cancel |
| 4a | `AlarmScheduler`, `OccurrencePlanner`, five reschedule receivers | 280 | PR 5 | `./gradlew :app:testDebugUnitTest` | `adb shell cmd appops set <pkg> SCHEDULE_EXACT_ALARM deny`; reboot re-arm | Cancel all alarms via `AlarmScheduler.cancelAll()` before reverting |
| 4b | `ReconcileWorker`, `MidnightSweepWorker`, injected tunables | 250 | PR 6 | `./gradlew :app:connectedDebugAndroidTest` | `adb shell dumpsys deviceidle force-idle` | `cancelUniqueWork` for reconcile/sweep before reverting |
| 5 | Notification channel/poster, Action/Answer/Snooze workers, snooze settings | 380 | PR 7 | `./gradlew :app:connectedDebugAndroidTest` | Set device time 23:50, snooze 20 min, confirm no `missed` row at 00:00 | Cancel armed snoozes before reverting; written entries stay valid |
| — | **Blocking gate**: API 37 on-device delivery matrix (§13.3) | 0 (code-free) | Gate, not a PR | N/A | Full §13.3 adb recipe on a real/emulator **API 37** image | N/A — failure amends `design.md` §5.4, not a revert |
| 6a | Habit CRUD UI (create/edit/archive, six kind pickers) + adaptive resilience | 350 | PR 8 | `./gradlew :app:connectedDebugAndroidTest` | Create each of six frequency kinds in-app; rotate mid-input | Revert editor composables; Room/repositories untouched |
| 6b | Today screen, progress display, settings UI + adaptive resilience | 380 | PR 9 | `./gradlew :app:connectedDebugAndroidTest` | Render at `sw=600dp`, rotate; answer one slot of a multi-slot habit | Revert today/progress composables; unit 5's write path untouched |
| 7 | Manual export/import | 150 | PR 10 | `./gradlew :app:connectedDebugAndroidTest` | Export → wipe app data → import, confirm restoration | Remove export/import use cases; no schema/scheduling change to undo |

**Dependency edges** (derive chain order from these, do not guess):
`1 → 2a → 2b → 3 → 4a → {4b, 5}`; `5` also needs `4a`'s `AlarmScheduler`; the **gate** needs `4a + 4b + 5` all merged; `4a → 6a`; `{3, 5} → 6b`; `7` depends only on `3` and can float anywhere after it.
Suggested linear chain: **1 → 2a → 2b → 3 → 4a → 4b → 5 → gate → 6a → 6b → 7**.

**Why 2 and 4 are pre-split, not only 6**: at the proposal's own line estimates, `:domain` (~750) and the
reminder pipeline (~480) each independently exceed 400 lines once strict-TDD RED/GREEN pairs and the
injected tunables are counted — not only the UI unit the proposal flagged. Each sub-unit above is
independently mergeable, testable, and revertible.

**Threat matrix**: N/A for every unit (design §12) — this change spawns no process, runs no shell, does
no VCS automation. No threat-matrix RED-test tasks are required.

## Phase 0: Strict TDD Gate (blocks Phase 2)

- [x] 0.1 After task 1.9 confirms `./gradlew :domain:test` runs green in this repository, set
      `strict_tdd: true` scoped to `:domain` in `openspec/config.yaml`; `:app` stays non-strict.

## Phase 1: Gradle Scaffolding & Toolchain (Work Unit 1)

- [x] 1.1 Create `settings.gradle.kts` including `:domain`, `:app`.
- [x] 1.2 Create `gradle/libs.versions.toml`: AGP 9.3.2, Kotlin 2.4.10, KSP 2.3.11, Compose BOM 2026.08.00, Hilt 2.60.1, JUnit 4.13.2, MockK 1.14.11, Turbine 1.2.1, DataStore 1.2.1; pin Room and WorkManager (still unpinned per design §17).
- [x] 1.3 Create root `build.gradle.kts` with plugin aliases (`apply false`).
- [x] 1.4 Create `domain/build.gradle.kts` applying `kotlin("jvm")` only — the compile-time Android-free enforcement (design §4).
- [x] 1.5 Create `app/build.gradle.kts`: `compileSdk { version = release(37) }`, `minSdk 31`, `targetSdk 37`, Hilt+KSP, `room.schemaLocation`.
- [x] 1.6 Configure detekt with a custom `ForbiddenMethodCall` rule banning `LocalDate.now()`, `System.currentTimeMillis()`, `ZoneId.systemDefault()` inside `:domain` (design §4 — the one boundary rule that is not compile-enforced).
- [x] 1.7 Create `app/src/main/AndroidManifest.xml`: `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`; no `INTERNET`, no `USE_EXACT_ALARM`; explicit `android:exported` on every component; minimal theme.
- [x] 1.8 Verify `./gradlew assembleDebug` succeeds and the app launches to an empty screen.
- [x] 1.9 Verify `./gradlew :domain:test` runs (empty suite acceptable) — precondition for 0.1.

## Phase 2a: `:domain` Model & Occurrence Predicate (Work Unit 2a) — strict TDD

**2a was split mid-apply on a budget stop, then reunited.** The unit measured 518 authored lines
against the original 400-line budget by task 2a.8, so the executor committed the in-budget slice
2a-i (2a.1–2a.6, 393 changed lines) and held 2a-ii GREEN but uncommitted rather than shrinking
scope. The user then **raised the review budget to 600 lines for `:domain` work units**, on the
evidence that 400 was systematically ~30% too tight for strict-TDD units where tests roughly
double the production code. Phase 2a therefore ships as a single PR of ~530 lines.

**Correction to 2a.9/2a.10 — DST tests were in the wrong unit.** `dueOn` and `rollupDay` take only
`LocalDate`, so a DST transition cannot skip or duplicate a *calendar day*; DST shifts wall-clock
times of day, and a time of day never enters `:domain`. What remains here is a characterization
guard (below). The substantive DST behaviour — which instant a 02:30 slot maps to on a
spring-forward day, when that local time does not exist, and what happens to a 01:30 slot on a
fall-back day, when it occurs twice — is a property of the `LocalTime`-to-`Instant` conversion in
`:app`'s alarm scheduler, and is **relocated to work unit 4a**.

- [x] 2a.1 [RED] Failing JVM tests for `Habit`, `Schedule` sealed hierarchy, `ReminderSlot`, `Entry`, `EntryStatus`, `Due`, `DayStatus` (habit-scheduling: Six Frequency Kinds; habit-entry-tracking: Entry States).
- [x] 2a.2 [GREEN] Implement the model types in `domain/src/main/kotlin/.../model`.
- [x] 2a.3 [RED] Failing tests for `dueOn()` across all six kinds, incl. `MONTHLY` month-length clamping and `EveryNDays` anchor arithmetic (habit-scheduling: Occurrence-Due Predicate; MONTHLY/EVERY_N_DAYS scenarios).
- [x] 2a.4 [GREEN] Implement `dueOn(schedule, date, progress): Due` (`weekStart` lives on `Schedule` — see apply-progress deviation note).
- [x] 2a.5 [RED] Failing tests for `N_TIMES_PER_WEEK` quota + injected ISO-Monday `weekStart` — quota-met silences remainder, resets at week boundary. **[Provisional — OA-3, unconfirmed]** (habit-scheduling: N_TIMES_PER_WEEK Reminder Semantics, Week Boundary).
- [x] 2a.6 [GREEN] Implement week-start-parameterised quota evaluation.
- [x] 2a.7 [RED] Failing tests for `rollupDay()` multi-slot collapse. **[Provisional — OA-2, unconfirmed]** (habit-entry-tracking: Day-Level Rollup and Per-Slot Display).
- [x] 2a.8 [GREEN] Implement `rollupDay(schedule, date, slots, entries): DayStatus`.
- [x] 2a.9 Characterization guard for DST-adjacent date arithmetic in `dueOn` (`DueOnDaylightSavingTest`, 5 tests): two-day cadence across the Europe/Madrid spring-forward (2026-03-29) and fall-back (2026-10-25) transitions, `MONTHLY` on a transition date, `DAILY` covering every calendar day of a transition week, and identical results under three default zones with different DST rules (Europe/Madrid, America/Santiago, Pacific/Kiritimati). **Not a RED/GREEN pair** — the behaviour was already correct, so this is a characterization test, not TDD. Its bite was proven instead by two probes: an off-by-one in the cadence (`% n == 1L`) failed 3 tests including both cadence tests, and inserting `ZoneId.systemDefault()` failed `detektMain` with `ForbiddenMethodCall`.
- [~] 2a.10 **Relocated to work unit 4a.** `ZonedDateTime` DST resolution (design §9.3) belongs to the alarm scheduler, which owns the `LocalTime`-to-`Instant` conversion. `:domain` must stay `LocalDate`-only; the guard in 2a.9 enforces that.
- [x] 2a.11 Verified zero `android.*`/`androidx.*` imports in `:domain` (`rg '^import android|^import androidx' domain/src` → no matches), compile-enforced by the `kotlin("jvm")` plugin.

## Phase 2b: Streak & Compliance Calculators (Work Unit 2b) — strict TDD

- [x] 2b.1 [RED] Failing tests: `SKIPPED`/`UNKNOWN` pass through without breaking a streak; only `MISSED` breaks; weekly-unit streak for `N_TIMES_PER_WEEK`; recompute-after-correction (habit-progress: Streak Calculation; habit-entry-tracking: Streak interaction).
- [x] 2b.2 [GREEN] Implement `StreakCalculator.current` / `.best`.
- [x] 2b.3 [RED] Failing tests: `completed / (completed + missed)`; `SKIPPED`/`UNKNOWN` excluded from both sides; caller-supplied `windowDays` (habit-progress: Compliance Calculation).
- [x] 2b.4 [GREEN] Implement `ComplianceCalculator.ratio`.

## Phase 3: Room Persistence (Work Unit 3)

- [ ] 3.1 Create entities `habits`, `schedules`, `reminder_slots`, `entries` (`slotId NOT NULL DEFAULT 0`, D11), `reminder_occurrences`, with indices (design §8.1).
- [ ] 3.2 Create DAOs incl. the `UNIQUE(habitId, date, slotId)` upsert query on `entries`.
- [ ] 3.3 Create `AppDatabase` (`version = 1`, `exportSchema = true`); commit generated `app/schemas/1.json`.
- [ ] 3.4 Create mappers translating `:app` entities ↔ `:domain` types, incl. the `0 ↔ null` slot sentinel (D11) and a `TimeProvider` abstraction (§4 — never read the clock directly).
- [ ] 3.5 Implement `HabitRepository.deleteSlot()` as a `@Transaction` reassigning/deleting affected entries (D11 cost of dropping the FK).
- [ ] 3.6 [Instrumented] DAO test: `UNIQUE(habitId, date, slotId)` actually rejects duplicates for `slotId = 0`.
- [ ] 3.7 [Instrumented] `MigrationTestHelper` harness test against `app/schemas/1.json` (establishes the harness for the future v1→v2 additive migration, §8.3).
- [ ] 3.8 Wire Hilt modules for the database/DAOs (D5); confirm `:domain` still carries zero DI annotations.

## Phase 4a: Alarm Scheduling & Reschedule Triggers (Work Unit 4a)

- [ ] 4a.1 Implement `OccurrencePlanner.replanAll()`: plan a 48h forward horizon + one occurrence per slot beyond it, upsert `reminder_occurrences` (design D4).
- [ ] 4a.2 Implement `AlarmScheduler`: `canScheduleExactAlarms()` before every call; `setExactAndAllowWhileIdle` under `SCHEDULE_EXACT_ALARM`; degrade to `setWindow` (≥10 min) when denied (reminder-delivery: Exact-Alarm Scheduling, Exact-Alarm Permission States).
- [ ] 4a.3 Implement `ExactAlarmPermissionReceiver` for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, upgrading/downgrading armed alarms.
- [ ] 4a.4 Implement `BootReceiver`, `PackageReplacedReceiver`, `TimeChangeReceiver` (`TIMEZONE_CHANGED`, `DATE_CHANGED`/`TIME_SET`) wired to `replanAll()` (reminder-delivery: Five Mandatory Reschedule Triggers).
- [ ] 4a.5 Wire in-app schedule edit to call `replanAll()` inside the same Room transaction (habit-management: Editing the schedule reschedules reminders).
- [ ] 4a.6 [Unit] Test planner arithmetic and permission-branch decision logic (`./gradlew :app:testDebugUnitTest`).
- [ ] 4a.7 **DST resolution — relocated here from 2a.10.** Decide and test what instant a reminder
      slot maps to on a daylight-saving transition, because this is where `LocalDate` + `LocalTime`
      becomes an `Instant`. Two cases, both real and both silent if unhandled:
      - **Spring forward**: a 02:30 slot on a spring-forward date has **no valid local time** — that
        instant does not exist. `ZonedDateTime.of(...)` silently shifts it forward by an hour rather
        than failing. Decide explicitly whether the reminder fires at 03:30, at 01:30, or is skipped
        for that date, and assert the choice.
      - **Fall back**: a 01:30 slot on a fall-back date occurs **twice**. Decide whether it fires on
        the first or second occurrence, and assert that it fires exactly **once**, never twice.
      Test against a fixed zone (`Europe/Madrid`: spring-forward 2026-03-29, fall-back 2026-10-25) with
      an injected `Clock`/`ZoneId` — never the ambient default. `:domain`'s
      `DueOnDaylightSavingTest` guard already proves the date predicate is DST-immune; this task
      covers the conversion that is not.

## Phase 4b: Reconcile & Midnight Sweep (Work Unit 4b) — depends on 4a

- [ ] 4b.1 Implement `ReconcileWorker` (hourly periodic; period injected as `RECONCILE_PERIOD_HOURS = 1`, a Hilt-provided constant, not a literal) — detects `ARMED` occurrences past due and fires late (reminder-delivery: Missed-Reminder Sweep).
- [ ] 4b.2 Implement `MidnightSweepWorker`: writes `MISSED` only where `dueOn(...) == Required` and NOT `(state = SNOOZED AND snoozeUntil > now)` (habit-entry-tracking: Midnight Transition, N_TIMES_PER_WEEK exception — D3/D8).
- [ ] 4b.3 Inject `RESOLVE_DEADLINE_HOURS = 24` as a tunable constant (`scheduledAt + resolveDeadline`, clamped to the next same-slot occurrence) driving both grace expiry and hard resolve (habit-entry-tracking: Abandoned Snooze Resolution).
- [ ] 4b.4 [Instrumented] `TestListenableWorkerBuilder`/`WorkManagerTestInitHelper` tests for both workers; **dedicated D3 test**: a live snooze at midnight leaves no `entries` row.

## Phase 5: Notifications & Responses (Work Unit 5) — depends on 4a

- [ ] 5.1 Create the notification channel and `NotificationPoster`: `areNotificationsEnabled()` + channel-importance check before every post; Yes/No/Snooze actions with `PendingIntent.FLAG_IMMUTABLE`, `reqCode = occurrence.id` (reminder-response: Notification Actions).
- [ ] 5.2 Implement `NotificationPermission` gate: `SDK_INT >= 33` contextual request; 31–32 skip entirely (reminder-response: Notification Permission Scope).
- [ ] 5.3 Implement `ActionReceiver` (`exported = false`, validate-only, no Room access) enqueuing expedited unique work per action (design §9.1).
- [ ] 5.4 Implement `AnswerWorker`: `@Transaction` upsert `Entry(date = occ.scheduledDate, …)`, `occ.state = RESOLVED`, cancel any armed snooze, cancel the notification only after the write lands (reminder-response: Notification Actions).
- [ ] 5.5 Implement `SnoozeWorker`: `snoozeCount++`, `snoozeUntil = now + duration` clamped to `resolveDeadline`, arm the same `reqCode` alarm, cancel the current notification (reminder-response: Snooze Configuration and Re-arm; habit-entry-tracking: Provisional-Missed happy path).
- [ ] 5.6 Implement snooze settings storage in DataStore: default 20 min; options 10/20/30 min, 1/2/3/4 h; unlimited (reminder-response: Snooze Configuration and Re-arm).
- [ ] 5.7 [Instrumented] `AnswerWorker`/`SnoozeWorker` tests: idempotent upsert on redelivery; after-midnight origin-date crediting (reminder-response: Origin-Date Crediting).
- [ ] 5.8 [Instrumented] Grace-expiry and hard-resolve-deadline force-resolve tests (habit-entry-tracking: Abandoned Snooze Resolution).

## Blocking Verification Gate — API 37 On-Device Delivery Matrix

Not a PR. Runs only once Phases 4a, 4b, and 5 are merged — the reminder pipeline and notification
handling must exist first. This discharges the **remaining** half of design §5.4's gate; the
documentation half was already discharged 2026-08-31 (design §5.7) and MUST NOT be re-done.

- [ ] G.1 Run the design §13.3 manual matrix on a real or emulator **API 37** image: exact-alarm revoke, `dumpsys alarm` inspection, Doze `force-idle`, deferred expedited work via `jobscheduler run -f`, timezone-changed broadcast, `POST_NOTIFICATIONS` revoke, reboot re-arm, snooze-across-midnight.
- [ ] G.2 Record the results as an amendment to `design.md` §5.4/§13.3. Any deviation is a design change, not an implementation detail. The change is not done until this gate passes.

## Phase 6a: Habit CRUD UI (Work Unit 6a) — depends on 3, 4a

- [ ] 6a.1 Implement the habit editor: name, question, colour, notes, schedule-kind picker for all six kinds, slot editor for `TIMES_PER_DAY` (habit-management: Habit Creation, Habit Editing; habit-scheduling: Six Frequency Kinds, Reminder Slots for TIMES_PER_DAY).
- [ ] 6a.2 Enforce name-required validation blocking save (habit-management: Creation requires a name).
- [ ] 6a.3 Wire schedule-edit save to the `HabitRepository` transaction that triggers `replanAll()` (habit-management: Editing reschedules reminders — depends on 4a.5).
- [ ] 6a.4 Implement archive/un-archive and a habit list with an archived filter (habit-management: Habit Archiving, Un-archiving does not back-fill).
- [ ] 6a.5 Apply single responsive layout (no dedicated tablet layout) to the editor; verify at `sw >= 600dp` and both orientations, re-requesting IME visibility explicitly after rotation (ui-adaptive-layout: Minimal Adaptive Resilience, Soft Keyboard Visibility).
- [ ] 6a.6 [Compose UI test] Create each of the six schedule kinds; verify the persisted `Habit` + `Schedule`.
- [ ] 6a.7 [Compose UI test] Rotate the editor mid-input; verify no content loss.

## Phase 6b: Today Screen, Progress & Settings UI (Work Unit 6b) — depends on 3, 5

- [ ] 6b.1 Implement the today screen: due habits, independent per-slot rows (habit-entry-tracking: Slot Independence, Day-Level Rollup and Per-Slot Display).
- [ ] 6b.2 Wire in-app Yes/No/Skip to write `Entry` through the same write path as notification actions (habit-entry-tracking: Entry States, Slot Independence).
- [ ] 6b.3 Render pending/snoozed state ("pending, snoozed until HH:mm") by reading `reminder_occurrences` (design §7 D3).
- [ ] 6b.4 Implement the progress view: current/best streak and compliance, calling `StreakCalculator`/`ComplianceCalculator` with `windowDays = 30`. **[Provisional — OA-4, unconfirmed]** (habit-progress: Streak Calculation, Compliance Calculation).
- [ ] 6b.5 Implement the snooze-default setting screen bound to the DataStore entry from 5.6 (reminder-response: Snooze Configuration and Re-arm).
- [ ] 6b.6 Apply single responsive layout to the today screen for multi-slot habits at `sw >= 600dp` and any orientation (ui-adaptive-layout: Today screen scenario).
- [ ] 6b.7 [Compose UI test] Answer one slot of a multi-slot habit; verify the sibling slot stays `UNKNOWN`.
- [ ] 6b.8 [Compose UI test] Render the today screen at `sw = 600dp` with a multi-slot habit due; verify no clipping/overlap.

## Phase 7: Data Portability (Work Unit 7) — depends on 3 only

- [ ] 7.1 Define `:app`-only export DTOs mirroring §8.4's JSON shape; set up `kotlinx-serialization-json` (design D9).
- [ ] 7.2 Implement export: read Room + DataStore settings, serialize to `constanza-backup-<yyyyMMdd-HHmmss>.json` via SAF `ACTION_CREATE_DOCUMENT` (data-portability: Export).
- [ ] 7.3 Implement import: full parse-and-validate before any write; refuse a newer `formatVersion`; on success, replace-all in one Room transaction with ID remapping, then cancel all alarms, truncate `reminder_occurrences`, and `replanAll()` (data-portability: Import, Round-Trip Fidelity).
- [ ] 7.4 Implement the destructive-import confirmation dialog gating the import call (data-portability: Declined confirmation changes nothing).
- [ ] 7.5 Wire an automatic pre-migration export hook, invoked before any future Room migration runs (proposal rollback plan).
- [ ] 7.6 [Instrumented] Round-trip test: export → wipe → import restores all habits/schedules/slots/entries, incl. archived history, byte-equivalent (data-portability: Round-Trip Fidelity).
- [ ] 7.7 [Instrumented] Malformed-file rejection test: existing dataset untouched (data-portability: Malformed file leaves data intact).

## Note on document size

This document exceeds the default 530-word task-artifact budget deliberately: 8 capabilities, a
pre-split 10-unit chain, and full requirement traceability cannot fit that budget without losing the
one thing the user asked for most — a decision-grade Review Workload Forecast. Content is
checklist-only, one to two lines per task, with no prose padding.
