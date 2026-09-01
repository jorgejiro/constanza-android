# Tasks: Habit-tracking MVP

Change: `habit-tracking-mvp` · Phase: `sdd-tasks` · Date: 2026-08-31
Inputs: `proposal.md`, `design.md`, `specs/*/spec.md` (8 capabilities), `openspec/config.yaml`

## Review Workload Forecast (revised 2026-08-31 — measured-evidence re-forecast)

This is a re-forecast of the six undelivered work units, replacing the original intuition-based
estimates with arithmetic built from the four delivered units' measured actuals (units 1, 2a, 2b, 3).
Task content, ordering, and dependency edges are **unchanged**; only the size forecast and, for the
one unit that warrants it, a pre-split are revised.

| Field | Value |
|-------|-------|
| Estimated changed lines (remaining 6 units) | ~3,354 (was ~1,790 in the original forecast) |
| Review budget for this change | **700** (raised from 400 on measured evidence, per session context) |
| 400-line budget risk | Medium |
| Chained PRs recommended | Yes |
| Suggested split | 7 PRs (was 6) — Unit 5 precautionarily split into 5-i / 5-ii |
| Delivery strategy | auto-chain (resolved before work unit 1 apply) |
| Chain strategy | stacked-to-main (resolved before work unit 1 apply) |

```text
Decision needed before apply: No — resolved: auto-chain / stacked-to-main
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Medium
```

*(The literal "400-line" guard label is kept for downstream automation matching; the threshold
actually applied throughout this forecast is **700**, per session context. "Medium" rather than "Low"
because no point estimate below exceeds 700, but Unit 5 clears budget by only 14 lines and Units 6a/6b
rest on an unmeasured Compose-line guess — both carry real residual risk of a mid-unit budget stop,
exactly as happened twice already in the delivered units.)*

### Method — why one blanket multiplier is the wrong tool

Four delivered units give two separable, transferable signals, not one number:

1. **`:domain` logic-density factor ≈ 1.51–1.58×** (2a: 400→633 actual = 1.58×; 2b: 350→530 actual
   after its correction = 1.51×). Applies to *decision/branching/arithmetic* code wherever it lives,
   not only inside `:domain`.
2. **`:app` test:production ratio ≈ 0.75:1**, measured on unit 3 (529 production / 398 test). The
   generic ratio for ordinary JVM-tested `:app` code with no heavy instrumented suite.
3. **Instrumented-test line cost ≈ 50 lines/test**, measured directly from unit 3's three instrumented
   files (248 lines / 5 tests). The most transferable number here: Android test-harness ceremony
   (`TestListenableWorkerBuilder`, `WorkManagerTestInitHelper`, Compose semantics matchers,
   `MigrationTestHelper`) is roughly constant regardless of what is under test.
4. **Room's own 1.51× production-boilerplate / 1.75× total factor is deliberately NOT reapplied
   wholesale.** It is a property of building a 5-table schema plus DAOs, mappers, and a migration
   harness. None of the six remaining units create a new schema; reapplying it blindly would itself be
   the blanket multiplier this re-forecast exists to replace.

Per-unit method, chosen by dominant code shape:

- **4a** is decision/branching/arithmetic (permission state machine, DST resolution) — closer in kind
  to `:domain`'s calculators than to Room. Uses signal 1, split at the lighter `:app` ratio (signal 2),
  plus a named delta for the new 4a.7 DST task, which is not in the original 280 at all.
- **4b, 5** are Android-framework ceremony (Worker/Receiver classes, Hilt injection) — a lighter
  correction than Room's (1.3×, not 1.51×, because these add 2–6 small classes, not a 5-table schema),
  plus signal 3 counted directly against each unit's *named* test behaviors, not a flat ratio.
- **6a, 6b** are Compose UI. No delivered unit has shipped Compose code yet, so there is no measured
  analog — these are named-component build-ups, explicitly flagged low confidence, plus the
  `ui-adaptive-layout` capability's own tasks (6a.5–6a.7, 6b.6–6b.8) counted as a delta since C1/C4
  postdate the original 350/380 estimates.
- **7** is DTO/serialization/transaction code — closest to unit 3's *mapper* sub-component (JVM-tested
  translation, not schema-heavy), so it gets a light 1.15× correction plus signal 3 for its two
  instrumented tests, weighted slightly above baseline because round-trip fidelity assertions are
  heavier than a DAO uniqueness check.

### Revised per-unit projection

| Unit | Original | Method (arithmetic) | Production | Test | Revised total | Factor |
|---|---|---|---|---|---|---|
| 4a Alarm scheduling | 280 | `:domain` factor 1.55× on 280 → 434, split 0.75:1 → 248/186; **+ DST delta**: production +50 (`ZonedDateTime` resolution/decision + injected `Clock`/`ZoneId`), test +75 (3 JVM scenarios × 25: spring-forward, fall-back, transition-week guard) | 298 | 261 | **~560** | 2.0× |
| 4b Reconcile & sweep | 250 | Component build (2 workers + tunables + DI ≈ 210) × 1.3 ceremony correction; **6 named instrumented behaviors** (Reconcile sweep, Sweep Required-write, N_TIMES_PER_WEEK D8 exception, dedicated D3 live-snooze-no-row, grace-expiry, hard-resolve) × 50 | 273 | 300 | **~575** | 2.3× |
| 5 Notifications & responses | 380 | Component build (channel+poster 80, permission gate 35, ActionReceiver 45, AnswerWorker 65, SnoozeWorker 65, settings 45 ≈ 335) × 1.3; **5 named instrumented behaviors** (idempotent-redelivery ×2 workers, after-midnight crediting, grace-expiry, hard-deadline) × 50 | 436 | 250 | **~686** | 1.8× |
| 6a Habit CRUD UI + adaptive | 350 | Named-component build: form 60 + 6 kind pickers (6×35=210) + slot editor 45 + validation 15 + archive/list 80 + replan wiring 10 + adaptive delta 40 = 460; tests: create-six-kinds 150 + rotate-mid-input 50. **Low confidence — no Compose unit shipped yet** | 460 | 200 | **~660** | 1.9× |
| 6b Today/progress/settings UI + adaptive | 380 | Named-component build: today rows 90 + Yes/No/Skip 30 + pending render 35 + progress view 70 + snooze-setting screen 45 + adaptive delta 40 = 310; tests: answer-one-slot 60 + render-600dp 60. **Low confidence, smaller surface than 6a** | 310 | 120 | **~430** | 1.1× |
| 7 Export/import | 150 | Component build (DTOs+serde 55, export 55, import parse/validate/transaction/remap/cancel/truncate/replan 100, dialog 25, pre-migration hook 20 ≈ 255) × 1.15; 2 instrumented tests above baseline (round-trip 90, malformed-rejection 60) | 293 | 150 | **~443** | 3.0× |
| **Total** | **1,790** | | **2,070** | **1,367** | **~3,354** | **1.87×** |

### Pre-split — Unit 5 only, precautionary

No point estimate above clears 700 outright. **Unit 5 comes closest at ~686 — only 14 lines under
budget**, well inside the estimation noise this exercise exists to correct. It is also the most
component-dense remaining unit (6 production pieces) and the decision the design argues hardest for
(D3, §9.2's three abandonment branches), so it gets the same precautionary split the mandate requires
for anything landing at or over budget, rather than waiting to discover the overrun mid-unit the way
units 2a and 3 both were:

- **5-i — Notification posting, permission gate, action receiver, snooze settings** (tasks 5.1, 5.2,
  5.3, 5.6). No automated test task exists for these in the original breakdown — coverage is the
  runtime harness only (manual notification-post / permission-state check), worth flagging rather than
  silently assumed. Production ≈ 266, test 0, **total ≈ 266**.
- **5-ii — `AnswerWorker`/`SnoozeWorker` and the full D3 instrumented verification** (tasks 5.4, 5.5,
  5.7, 5.8). Tests stay with the code they verify, exactly as unit 3's split kept JVM tests with their
  mappers. Production ≈ 169, test 250 (5 named behaviors), **total ≈ 419**.

Both slices are independently under 700 with real margin (266, 419), independently revertable (5-i
touches only posting/permission/settings; 5-ii touches only the answer/snooze write path), and
independently verifiable (5-i via the manual harness; 5-ii via the instrumented worker suite plus the
existing snooze-across-midnight manual scenario). 266 + 419 = 685 ≈ the whole-unit 686 estimate.

4b (~575) and 6a (~660) are the next-closest units but both carry real margin (125 and 40 lines
respectively) — worth watching, not worth a mandatory split at this point estimate.

### Revised chain order

PRs 1–6 already exist on GitHub (docs, scaffolding, domain model, calculators, Room core, Room
on-device). Dependency edges are unchanged from the original suggested linear order.

| PR | Work unit | Lines | Status |
|---|---|---|---|
| 1 | docs | — | merged |
| 2 | scaffolding (WU 1) | 399 actual | merged |
| 3 | domain model (WU 2a) | 633 actual | merged |
| 4 | calculators (WU 2b) | 530 actual | merged |
| 5 | Room core (WU 3-i) | — actual | merged |
| 6 | Room on-device (WU 3-ii) | 965 actual (3-i+3-ii combined) | merged |
| 7 | Alarm scheduling (WU 4a) | ~560 | planned |
| 8 | Reconcile & sweep (WU 4b) | ~575 | planned, depends on PR 7 |
| 9 | Notification posting/permission/settings (WU 5-i) | ~266 | planned, depends on PR 7 (`AlarmScheduler` edge retained) |
| 10 | Answer/Snooze workers + D3 verification (WU 5-ii) | ~419 | planned, depends on PR 9 |
| — | **Blocking gate** (API 37 on-device matrix), code-free | 0 | planned, depends on PR 7 + 8 + 9 + 10 |
| 11 | Habit CRUD UI + adaptive (WU 6a) | ~660 | planned, depends on PR 6 + 7 |
| 12 | Today/progress/settings UI + adaptive (WU 6b) | ~430 | planned, depends on PR 6 + 9/10 |
| 13 | Export/import (WU 7) | ~443 | planned, depends on PR 6 only — can float earlier if throughput matters (unchanged flexibility from the original forecast) |

**New total projected for remaining work: ~3,354 changed lines across 7 PRs** (was ~1,790 across 6).

### Confidence — plain statement, not false precision

- **Trust**: 4a, 4b, 7 — each anchored to a measured signal (the `:domain` logic-density factor, the
  50-line instrumented-test cost, or unit 3's mapper analog) applied to a named component build, not a
  guess.
- **Moderate trust, borderline**: 5 — the component build is solid, but 6 production pieces
  compressed into one unit is exactly the shape that blew unit 3's budget; treat 686 as a floor, not a
  ceiling, which is why it gets the precautionary split anyway.
- **Low trust, explicitly flagged**: 6a and 6b — **no Compose UI unit has shipped in this change yet**,
  so there is no measured line-count analog for this codebase's Compose style, only a named-component
  guess. 6a (six distinct schedule-kind pickers) is the single least trustworthy number in this table.
  If it runs true to every other `:app` unit's pattern of underestimating framework ceremony, 660 could
  realistically become 850–1,000+; there is no evidence yet to bound that upside, only the delivered
  units' general lesson that estimates run low.
- Chained PRs recommended: **Yes**, unchanged.

**Dependency edges** (unchanged — derive chain order from these, do not guess):
`1 → 2a → 2b → 3 → 4a → {4b, 5}`; `5` also needs `4a`'s `AlarmScheduler`; the **gate** needs
`4a + 4b + 5` all merged; `4a → 6a`; `{3, 5} → 6b`; `7` depends only on `3` and can float anywhere
after it. The precautionary split of `5` into `5-i`/`5-ii` does not add or remove an edge: both slices
sit under the same `5` dependency, and the gate still requires all of `5` (both slices) merged.

**Why 2 and 4 were pre-split, not only 6** (carried from the original forecast, still accurate): at the
proposal's own line estimates, `:domain` (~750) and the reminder pipeline (~480) each independently
exceeded 400 lines once strict-TDD RED/GREEN pairs and the injected tunables were counted. This
re-forecast additionally flags unit 5 as borderline under the now-700 budget, for the reasons above.

**Threat matrix**: N/A for every unit (design §12) — unchanged from the original forecast. This change
spawns no process, runs no shell, does no VCS automation. No threat-matrix RED-test tasks are required.

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
spring-forward day, when that local time does not exist, and what happens to a 02:30 slot on a
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
- [x] 2b.5 [RED→GREEN] Fix: `StreakCalculator` treats an enclosed and a trailing `SKIPPED`/`UNKNOWN` day identically — only `COMPLETED` lengthens, only `MISSED` breaks (corrected `habit-progress` Streak Calculation scenario). Collapsed the tentative/confirmed run split, now unnecessary.
- [x] 2b.6 [RED→GREEN] Fix: `ComplianceCalculator.ratio` adds the `N_TIMES_PER_WEEK` weekly-quota branch from design D8/§10 (sum of `min(completedInWeek, n)` over whole weeks ÷ sum of `n`, partial edge weeks excluded).

## Phase 3: Room Persistence (Work Unit 3)

**Work unit 3 split on a budget stop.** The unit measured 965 authored lines against a 700-line
cap, so it ships as two PRs: **3-i** (tasks 3.1–3.5, 3.8 — schema, DAOs, mappers, DI, repository,
plus the JVM mapper tests) and **3-ii** (tasks 3.6–3.7 — the on-device DAO, migration and
transaction verification, with its androidTest Gradle wiring). Both slices were fully written and
green before the split; this was a commit-boundary decision, not missing work. 3-i was verified to
build and test standalone with the androidTest sources removed.

- [x] 3.1 Create entities `habits`, `schedules`, `reminder_slots`, `entries` (`slotId NOT NULL DEFAULT 0`, D11), `reminder_occurrences`, with indices (design §8.1).
- [x] 3.2 Create DAOs incl. the `UNIQUE(habitId, date, slotId)` upsert query on `entries`.
- [x] 3.3 Create `AppDatabase` (`version = 1`, `exportSchema = true`); commit generated `app/schemas/1.json`.
- [x] 3.4 Create mappers translating `:app` entities ↔ `:domain` types, incl. the `0 ↔ null` slot sentinel (D11) and a `TimeProvider` abstraction (§4 — never read the clock directly).
- [x] 3.5 Implement `HabitRepository.deleteSlot()` as a `@Transaction` reassigning/deleting affected entries (D11 cost of dropping the FK).
- [x] 3.6 [Instrumented] DAO test: `UNIQUE(habitId, date, slotId)` actually rejects duplicates for `slotId = 0`.
- [x] 3.7 [Instrumented] `MigrationTestHelper` harness test against `app/schemas/1.json` (establishes the harness for the future v1→v2 additive migration, §8.3).
- [x] 3.8 Wire Hilt modules for the database/DAOs (D5); confirm `:domain` still carries zero DI annotations.

## Phase 4a: Alarm Scheduling & Reschedule Triggers (Work Unit 4a)

- [x] 4a.1 Implement `OccurrencePlanner.replanAll()`: plan a 48h forward horizon + one occurrence per slot beyond it, upsert `reminder_occurrences` (design D4).
- [x] 4a.2 Implement `AlarmScheduler`: `canScheduleExactAlarms()` before every call; `setExactAndAllowWhileIdle` under `SCHEDULE_EXACT_ALARM`; degrade to `setWindow` (≥10 min) when denied (reminder-delivery: Exact-Alarm Scheduling, Exact-Alarm Permission States).
- [x] 4a.3 Implement `ExactAlarmPermissionReceiver` for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, upgrading/downgrading armed alarms.
- [x] 4a.4 Implement `BootReceiver`, `PackageReplacedReceiver`, `TimeChangeReceiver` (`TIMEZONE_CHANGED`, `DATE_CHANGED`/`TIME_SET`) wired to `replanAll()` (reminder-delivery: Five Mandatory Reschedule Triggers).
- [x] 4a.5 Wire in-app schedule edit to call `replanAll()` inside the same Room transaction (habit-management: Editing the schedule reschedules reminders).
- [x] 4a.6 [Unit] Test planner arithmetic and permission-branch decision logic (`./gradlew :app:testDebugUnitTest`).
- [x] 4a.7 **DST resolution — relocated here from 2a.10.** Decide and test what instant a reminder
      slot maps to on a daylight-saving transition, because this is where `LocalDate` + `LocalTime`
      becomes an `Instant`. Two cases, both real and both silent if unhandled:
      **Correction (verified against `java.time.zone.ZoneRules.getValidOffsets`).** An earlier draft of
      this task said the fall-back repeat was 01:30. That is the *US* convention. In `Europe/Madrid`
      **both** transitions pivot between 02:00 and 03:00 local: on 2026-03-29 `02:30` returns zero
      offsets (the gap) and on 2026-10-25 it returns two (the overlap), while `01:30` is unambiguous
      on both dates. Never take a DST window from a prose description — query the zone's rules.

      - **Spring forward**: a 02:30 slot on a spring-forward date has **no valid local time** — that
        instant does not exist. `ZonedDateTime.of(...)` silently shifts it forward by an hour rather
        than failing. Decide explicitly whether the reminder fires at 03:30, at 01:30, or is skipped
        for that date, and assert the choice.
      - **Fall back**: a 02:30 slot on a fall-back date occurs **twice**. Decide whether it fires on
        the first or second occurrence, and assert that it fires exactly **once**, never twice.
      Test against a fixed zone (`Europe/Madrid`: spring-forward 2026-03-29, fall-back 2026-10-25) with
      an injected `Clock`/`ZoneId` — never the ambient default. `:domain`'s
      `DueOnDaylightSavingTest` guard already proves the date predicate is DST-immune; this task
      covers the conversion that is not.

## Phase 4b: Reconcile & Midnight Sweep (Work Unit 4b) — depends on 4a

- [x] 4b.1 Implement `ReconcileWorker` (hourly periodic; period injected as `RECONCILE_PERIOD_HOURS = 1`, a Hilt-provided constant, not a literal) — detects `ARMED` occurrences past due and fires late (reminder-delivery: Missed-Reminder Sweep).
- [x] 4b.2 Implement `MidnightSweepWorker`: writes `MISSED` only where `dueOn(...) == Required` and NOT `(state = SNOOZED AND snoozeUntil > now)` (habit-entry-tracking: Midnight Transition, N_TIMES_PER_WEEK exception — D3/D8).
- [x] 4b.3 Inject `RESOLVE_DEADLINE_HOURS = 24` as a tunable constant (`scheduledAt + resolveDeadline`, clamped to the next same-slot occurrence) driving both grace expiry and hard resolve (habit-entry-tracking: Abandoned Snooze Resolution).
- [x] 4b.4 [Instrumented] `TestListenableWorkerBuilder`/`WorkManagerTestInitHelper` tests for both workers; **dedicated D3 test**: a live snooze at midnight leaves no `entries` row.

## Phase 5: Notifications & Responses (Work Unit 5) — depends on 4a

**Split into slice i (posting) and slice ii (responding), 2026-08-31 — a corrected boundary, not the
one the forecast proposed.** The forecast suggested splitting after 5.3, but the `ActionReceiver`
enqueues work by Worker class, and those workers (5.4/5.5) are the other slice — a stub receiver
enqueuing nothing would not be independently verifiable. The seam used instead is behavioural:
**5-i posts the reminder, 5-ii answers it.**

- [x] 5.1 Create the notification channel and `NotificationPoster`: `areNotificationsEnabled()` + channel-importance check before every post; Yes/No/Snooze actions with `PendingIntent.FLAG_IMMUTABLE`, `reqCode = occurrence.id` (reminder-response: Notification Actions).
- [x] 5.2 Implement `NotificationPermission` gate: `SDK_INT >= 33` contextual request; 31–32 skip entirely (reminder-response: Notification Permission Scope).
- [x] 5.3 **Slice ii.** Implement `ActionReceiver` (`exported = false`, validate-only, no Room access) enqueuing expedited unique work per action (design §9.1) — implements against the `ActionIntentContract` (action strings, extras, explicit receiver class name) already fixed by 5.1.
- [x] 5.4 **Slice ii.** Implement `AnswerWorker`: `@Transaction` upsert `Entry(date = occ.scheduledDate, …)`, `occ.state = RESOLVED`, cancel any armed snooze, cancel the notification only after the write lands (reminder-response: Notification Actions).
- [x] 5.5 **Slice ii.** Implement `SnoozeWorker`: `snoozeCount++`, `snoozeUntil = now + duration` clamped to `resolveDeadline`, arm the same `reqCode` alarm, cancel the current notification (reminder-response: Snooze Configuration and Re-arm; habit-entry-tracking: Provisional-Missed happy path).
- [x] 5.6 Implement snooze settings storage in DataStore: default 20 min; options 10/20/30 min, 1/2/3/4 h; unlimited (reminder-response: Snooze Configuration and Re-arm).
- [x] 5.9 **Slice ii. Wire `ReminderFireReceiver` to `NotificationPoster`, with the fire-time quota re-check.**
      Added 2026-08-31: design §9.1 assigns this step to work unit 5 and `ReminderFireReceiver`'s own
      KDoc says so, but no numbered task owned it — the alarm fired into a receiver that did nothing,
      so no reminder could reach the user at all. The gap surfaced when slice i shipped a poster that
      nothing calls. The receiver must load the occurrence by the id its `PendingIntent` carries,
      re-evaluate before posting, post via `NotificationPoster`, and record `state = FIRED` with
      `notifiedAtEpochMs`. The re-evaluation is not decoration: work unit 4a arms an alarm every day
      for `N_TIMES_PER_WEEK` precisely because D8 defers quota suppression to fire time, so **this is
      the one place a weekly habit whose quota is already met is silently suppressed**. Without it a
      "3 times per week" habit nags on all seven days. Keep the receiver validate-only — no Room
      access on the ~10s `onReceive` budget; enqueue expedited work, as 5.3 does for actions.

- [x] 5.7 **Slice ii.** [Instrumented] `AnswerWorker`/`SnoozeWorker` tests: idempotent upsert on redelivery; after-midnight origin-date crediting (reminder-response: Origin-Date Crediting).
- [x] 5.8 [Instrumented] Grace-expiry and hard-resolve-deadline force-resolve tests (habit-entry-tracking: Abandoned Snooze Resolution). **Already shipped in work unit 4b** — `graceExpiryForceResolvesAnAbandonedSnoozeToMissed` and `hardResolveDeadlineForceResolvesRegardlessOfState` in `ReconcileWorkerTest`.

## Blocking Verification Gate — API 37 On-Device Delivery Matrix

Not a PR. Runs only once Phases 4a, 4b, and 5 are merged — the reminder pipeline and notification
handling must exist first. This discharges the **remaining** half of design §5.4's gate; the
documentation half was already discharged 2026-08-31 (design §5.7) and MUST NOT be re-done.

- [x] G.1 Run the design §13.3 manual matrix on a real or emulator **API 37** image: exact-alarm revoke, `dumpsys alarm` inspection, Doze `force-idle`, deferred expedited work via `jobscheduler run -f`, timezone-changed broadcast, `POST_NOTIFICATIONS` revoke, reboot re-arm, snooze-across-midnight.
      Run 2026-08-31/09-01 on the Pixel 10, now on a **released** Android 17 (`preview_sdk 0`,
      `codename REL`), not the beta §13.3 assumed. Seven of the eight scenarios PASS, including
      end-to-end delivery through deep Doze and D3's snooze-across-real-midnight rule in both
      directions. The eighth — `jobscheduler run -f` — is an invalid recipe, not a product failure.
      Four of §13.3's recipes do not work on a user build; replacements are recorded in §13.4.
      Required a seeding harness (`app/src/androidTest/.../seed/`, `@SeedOnly`-excluded) because
      nothing in the product can create a habit until 6a.
- [x] G.2 Record the results as an amendment to `design.md` §5.4/§13.3. Any deviation is a design change, not an implementation detail. The change is not done until this gate passes.
      Recorded as **§13.4**, with §5.4's checklist updated. Nothing found contradicts §5.5, so
      `targetSdk = 37` does not need the mitigation §5.4 held in reserve. §13.3's own recipes are what
      failed. The gate raised four findings, tracked as G.3–G.6 below rather than folded in silently.

The gate itself is discharged. These follow-ups come out of it and need decisions, not just edits:

- [ ] G.3 Decide what `notifiedAtEpochMs` means when the post was suppressed. Today it is recorded even
      though nothing reached the user (§13.4 finding 1), so any future reader treating it as "the user
      was told" would be wrong. Either stop writing it on the suppressed branch or rename it to say
      what it actually records.
- [ ] G.4 Stop `WorkScheduler.scheduleAll()` re-anchoring the periodic workers on every cold start
      (§13.4 finding 2). Measured: a process start at 00:04:55 pushed the midnight sweep to
      `Delay=+23h29m59s`, skipping the boundary. A user opening the app often enough can postpone the
      hourly reconcile and the sweep indefinitely — starving the net §5.5 calls the correctness
      guarantee. `UPDATE` was chosen so tuning `ReconcilePeriodHours` reaches existing installs, so the
      fix must keep that without resetting the anchor each launch.
- [ ] G.5 Decide whether an exact-alarm revoke should re-plan instead of waiting out the hourly net
      (§13.4 finding 3). Delivery is "late, not lost" as designed, but nothing re-arms on app open, so
      every reminder after a revoke rests entirely on the reconcile period.
- [ ] G.6 Name the reboot-to-first-unlock blind window in §9.3 (§13.4 finding 4). Zero alarms are armed
      in that window and it is the correct consequence of reading Room from credential-encrypted
      storage — but the design should say so rather than leave it implicit.
- [ ] G.7 Run the four UI-dependent §13.3 rows once 6a and 6b ship: large screen (C1), orientation
      (C1), soft keyboard after a config change (C4), and the `wm size 800dpx1280dp` override. Plus the
      Z Fold 7 OEM-throttling assertion, which no Pixel can make.

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
