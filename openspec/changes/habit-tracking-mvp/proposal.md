# Proposal: Habit-tracking MVP

Change: `habit-tracking-mvp` · Phase: `sdd-propose` · Date: 2026-08-31
Evidence base: `openspec/changes/habit-tracking-mvp/exploration.md`

## Intent

Ship a native Android habit tracker that reliably reminds the user about periodic habits and records the answer.

- **Problem**: the user wants dependable periodic-habit tracking with reminders. Loop Habit Tracker's reminder notification has no **Snooze** action, so a reminder arriving at a bad moment must be answered wrongly or dismissed and lost.
- **Current state**: greenfield repository — no source, no Gradle, no build system. Everything below is built from zero.
- **Success**: a habit with any of the six supported frequencies fires its reminder on time, can be answered **Yes / No / Snooze** from the notification drawer without opening the app, survives reboot, update, timezone and DST changes, and yields a streak and compliance figure the user can trust.

## Licensing constraint (restated, binding)

Loop Habit Tracker (`iSoron/uhabits`) is **GPL-3.0** (verified). Constanza MUST NOT copy, translate, or derive code, structure, or algorithms from it. Conceptual prior-art analysis is permitted; every design in this change is independent.

## Scope

### In Scope

| # | Deliverable |
|---|---|
| 1 | Gradle scaffolding: Kotlin DSL, version catalog, two modules (`:domain`, `:app`), manifest, theme |
| 2 | `:domain` model — `Habit`, `Schedule`, `ReminderSlot`, `Entry` (slot-aware, Option 1) with JVM unit tests |
| 3 | Six frequency kinds: `DAILY`, `TIMES_PER_DAY` (explicit clock times), `N_TIMES_PER_WEEK`, `WEEKLY`, `MONTHLY`, `EVERY_N_DAYS` |
| 4 | Two reusable `:domain` pure functions: occurrence-due predicate, and day-level multi-slot rollup |
| 5 | Room persistence (local-only) + compute-on-read `StreakCalculator` and `ComplianceCalculator` |
| 6 | Reminder pipeline: `AlarmManager.setExactAndAllowWhileIdle`, WorkManager reconcile/missed-sweep, five reschedule triggers |
| 7 | Notification actions Yes / No / Snooze; snooze default 20 min, configurable (10/20/30 min; 1/2/3/4 h), unlimited |
| 8 | Compose UI: today's habits list, habit create/edit, settings |
| 9 | Manual export/import to a file |

### Out of Scope (MVP), not architecturally blocked

- Numeric habit values — `Entry.value: Int?` ships as a reserved, nullable, unused column so numeric support is an **additive** migration.
- Proactive daily digest notification; weekly equivalent; printable "fridge-worthy" compliance grid — all three are unblocked by deliverable 4.
- Richer modularisation (`:feature-*`, `:core-ui`), deferred until a second surface (widget, Wear OS) or measurable build pain exists.
- EWMA/decay compliance scoring; persisted streak counters.

### Non-Goals (permanent)

- Cloud sync, multi-device conflict resolution, accounts. Storage is local-only **forever**.
- To-do / task management. Gamification. Social features.

## Capabilities

### New Capabilities

- `habit-management`: create, edit, archive habits and attach a schedule.
- `habit-scheduling`: the six frequency kinds, reminder slots, and the occurrence-due predicate.
- `habit-entry-tracking`: `completed` / `missed` / `skipped` / `unknown`, day rollup, midnight transition.
- `reminder-delivery`: exact-alarm scheduling, permission degradation, reschedule triggers, missed sweep.
- `reminder-response`: Yes / No / Snooze handling, snooze re-arm, origin-date crediting.
- `habit-progress`: compute-on-read streak and compliance.
- `data-portability`: manual file export/import round-trip, with destructive replace-all import semantics.
- `ui-adaptive-layout`: minimal large-screen and orientation resilience. **Added after this proposal was
  first written**, as a consequence of the discharged API 37 gate (design §5.7, C1): `targetSdk = 37`
  removes the SDK 36 opt-out for large-screen orientation, resizability and aspect-ratio constraints, so
  this is a platform obligation rather than optional polish. The user chose the minimal bar — layouts
  must not break, clip, overlap, or lose content at `sw >= 600dp` or in any orientation, with no
  dedicated tablet layouts (no two-pane master/detail, no landscape grid) in the MVP.

### Modified Capabilities

- None. `openspec/specs/` is empty; this is the first change.

**Capability count**: 8, not the 7 originally listed. `ui-adaptive-layout` was added during the spec
amendment of 2026-08-31; the size forecast below predates it. Its UI work is small (resilience, not
redesign) and belongs inside work unit 6, which was already flagged as the unit most likely to need a
further split.

## Approach

| Area | Decision | Rationale |
|---|---|---|
| Domain model | Slot-aware `Entry(habitId, date, slotId: Long?, status, value: Int? = null)` | Each clock-time slot is answered independently; `status` is a first-class enum, never overloaded with measured values |
| Calendar vs interval | Distinct `MONTHLY(dayOfMonth)` and `EVERY_N_DAYS(n, anchorDate)` kinds | No fraction can distinguish "once a month" from "every 30 days" |
| Modules | Exactly two: `:domain` (pure Kotlin, **no Android SDK imports**) and `:app` (Compose, Room, adapters, DI), `:app` organised by domain packages | `:domain` enables JVM unit tests without an emulator; deeper splits are unpaid ceremony at this size |
| Alarm firing | `setExactAndAllowWhileIdle` under **`SCHEDULE_EXACT_ALARM`** (never `USE_EXACT_ALARM`) | User-revocable and policy-safe; check `canScheduleExactAlarms()` before **every** scheduling call, degrade to an inexact window when false |
| Reliability net | Periodic WorkManager reconcile + missed-reminder sweep | AlarmManager alarms do not survive reboot and are silently droppable under Doze/OEM throttling |
| Action handling | `BroadcastReceiver` validates the intent only, then enqueues an **expedited** WorkManager job | `onReceive()` has a ~10 s budget; the Room write MUST NOT happen there |
| Scoring | Computed on read from indexed `(habitId, date)` queries | ~3,650 rows per decade of daily tracking; avoids counter drift on edit, skip, and import |
| Skipped semantics | Excluded from compliance numerator **and** denominator; never resets a streak | A skip is a neutral pass-through |

### Named behavioural requirement — provisional `missed`

Two ratified decisions interact and the spec MUST cover the interaction explicitly:

- An unanswered slot transitions to **`missed`** at the midnight boundary (not left `unknown`).
- A snoozed answer credits the **originally-scheduled date**, even when the notification fires after midnight — so the slot occurrence MUST persist its origin date.

**Therefore an `Entry` legitimately transitions `missed` → `completed`.** A `missed` state written by the midnight job is **provisional** for any slot with a live snooze outstanding, and every reader (streak, compliance, UI, future digest) MUST tolerate that late correction rather than treating `missed` as terminal.

### Five mandatory reschedule triggers

`BOOT_COMPLETED` · `MY_PACKAGE_REPLACED` · `ACTION_TIMEZONE_CHANGED` · `ACTION_DATE_CHANGED`/`ACTION_TIME_CHANGED` · in-app schedule edit. None may be skipped.

## Unratified stack (must be confirmed or revised by `sdd-design`)

| Item | Status |
|---|---|
| `openspec/config.yaml` → `stack.architecture` still says **full multi-module** | **Contradicted** by the ratified two-module decision. `sdd-design` MUST update that file. This phase does not edit it. |
| `minSdk = 31` | **Ratified by the user** (2026-08-31). Matches the sibling project `sleep-noise-android`, verified at `app/build.gradle.kts:31`. Supersedes the orchestrator's earlier assumption of 26. |
| `compileSdk = 37` | **Ratified by the user** (2026-08-31). API 37 is a released platform: the sibling project builds against it with `compileSdk { version = release(37) }` under AGP 9.3.2. |
| `targetSdk = 37` | **Ratified by the user** (2026-08-31), explicitly "already adapted". This EXCEEDS Google Play's current floor of API 36 (effective 2026-08-31), which is permitted. It is also the deliberate forward step that the sibling project's ADR 005 reserved for a decision made with tests in front of it — see the amendment below. |
| Room, Compose/Material 3, Kotlin, AGP, Gradle versions | Planned, no version pinned or verified |
| DI framework | Not chosen |
| JUnit5 unit / AndroidX Test + Espresso instrumented | Planned, unverified; JUnit5 on Android needs extra plugin wiring |
| Lint + formatter (ktlint / detekt) | Not chosen; `config.yaml` records both as unavailable |
| `./gradlew test`, `./gradlew connectedAndroidTest` | Never executed — no build system exists |
| `strict_tdd: false` | Consequence of the above. Revisit the moment `./gradlew test` is verified; Strict TDD is strongly recommended for `:domain` |

### Amendment — SDK levels ratified by the user (2026-08-31)

`minSdk = 31`, `compileSdk = 37`, `targetSdk = 37`. This replaces the orchestrator's earlier
`minSdk = 26` assumption and the `targetSdk = 36` recommendation.

Evidence gathered before accepting these values:

- `minSdk = 31` is read directly from the sibling project `sleep-noise-android`
  (`app/build.gradle.kts:31`), which the user named as the reference.
- API 37 is a real, released platform, not a preview: that project compiles against it with
  `compileSdk { version = release(37) }` under AGP 9.3.2 and Kotlin 2.4.10.
- That project deliberately runs `compileSdk 37` with `targetSdk 36`, documented in its
  `docs/decisions/005-compilesdk-37-con-targetsdk-36.md` (accepted 2026-08-24). That ADR's
  stated reason for holding `targetSdk` at 36 was that raising it changes runtime behaviour and
  "se hace con pruebas por delante, no como efecto colateral de arreglar un build" — it
  deliberately left the `OldTargetApi` lint warning visible so the next bump would be a decision
  rather than an oversight. The user is now making that decision for Constanza. There is no
  contradiction to resolve.

**Consequences `sdd-design` MUST address, and MUST NOT treat as optional:**

1. **Enumerate the API 37 (Android 17) behaviour changes that affect alarms, exact-alarm
   permissions, notifications, foreground work, and background execution**, and state how each is
   handled. `targetSdk = 37` opts this app into every one of them, and this app's critical path is
   precisely alarm and notification delivery. This is the single highest-risk consequence of the
   change and must be researched against current documentation, not assumed.
2. **`minSdk = 31` removes an entire legacy branch**: `SCHEDULE_EXACT_ALARM` was introduced in
   API 31, so no pre-31 exact-alarm fallback path is needed. Do not write one.
3. **`POST_NOTIFICATIONS` is still API 33+**, so a runtime-permission branch IS required for
   API 31–32 devices, where notifications are granted implicitly.
4. The `android-37` platform must be present locally; AGP downloads it on first build.
5. No `OldTargetApi` lint suppression is needed here, since `targetSdk` is at the latest.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `settings.gradle.kts`, `gradle/libs.versions.toml`, root `build.gradle.kts` | New | Project scaffolding, version catalog, two modules |
| `domain/` | New | Pure-Kotlin model, calculators, occurrence predicate, day rollup |
| `app/src/main/AndroidManifest.xml` | New | Permissions, receivers, notification channel |
| `app/.../data/` | New | Room entities, DAOs, database, mappers, export/import |
| `app/.../reminder/` | New | Alarm scheduler adapter, receivers, WorkManager workers |
| `app/.../ui/` | New | Compose today list, habit editor, settings |
| `openspec/config.yaml` | Modified (by `sdd-design`, not here) | `stack.architecture`, `minSdk`, testing status |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| MVP scope far exceeds the 400-line review budget (see forecast) | **High** | Split into the reviewable work units below; the delivery decision belongs to the user after `sdd-tasks` |
| OEM battery optimisation (Samsung One UI sleep, Xiaomi MIUI) delays or drops alarms | High | WorkManager missed-reminder sweep fires late rather than losing the reminder; reactive in-app OEM guidance only after repeated detected misses |
| `SCHEDULE_EXACT_ALARM` denied or later revoked | Medium | Check `canScheduleExactAlarms()` before every scheduling call and in `onResume()`; degrade to a ≥10 min inexact window; listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` |
| `POST_NOTIFICATIONS` denied twice — system blocks the dialog permanently | Medium | Request contextually after the first reminder is created; in-app today list stays fully usable without notifications; recovery path opens system settings |
| Provisional-`missed` correction produces a visibly wrong streak between midnight and the snoozed answer | Medium | Make late correction a first-class spec requirement; recompute on read so no stale counter persists |
| Exact-alarm eligibility is a Play **policy** call, not only technical | Low | `SCHEDULE_EXACT_ALARM` is already the chosen default, so no fallback work is needed if review disagrees |
| Unratified toolchain versions cause scaffolding churn | Medium | `sdd-design` pins the catalog before any feature task; scaffolding is work unit 1 and independently revertible |
| Accidental GPL contamination from prior-art reading | Low | No file may be copied or translated; designs are independent and reviewed against this constraint |
| `minSdk = 26` unverified — may exclude needed APIs or wanted devices | Medium | `sdd-design` ratifies it before scaffolding is written |

## Size Forecast

**The MVP as scoped clearly exceeds the 400-line review budget.** Stating this plainly rather than shrinking the MVP: scaffolding a Gradle project from nothing, plus a domain model, plus persistence, plus a reminder pipeline, plus UI is realistically **~2,500–3,000 changed lines**.

Proposed work-unit boundaries, each with an autonomous scope, its own verification, and a clean rollback:

| # | Work unit | Est. lines | Verification |
|---|---|---|---|
| 1 | Gradle scaffolding, version catalog, two empty modules, manifest, theme | ~250–350 | `./gradlew assembleDebug` succeeds; app launches empty |
| 2 | `:domain` model + occurrence predicate + day rollup + streak/compliance, with JVM tests | ~700 | `./gradlew :domain:test` green; no Android SDK import in `:domain` |
| 3 | Room persistence, DAOs, mappers, repositories | ~350 | Instrumented DAO tests; schema exported |
| 4 | Reminder pipeline: alarm scheduler, five reschedule triggers, reconcile + sweep workers | ~450 | Alarm arms and re-arms across reboot, timezone change, and permission revocation |
| 5 | Notifications: channel, builder, Yes/No/Snooze receiver + expedited worker, snooze settings | ~350 | Each action writes the correct `Entry`; snooze re-arms and credits the origin date |
| 6 | Compose UI: today list + habit create/edit + settings | ~600 (may need a further split) | Six frequency kinds are creatable and answerable in-app |
| 7 | Manual export/import | ~150 | Round-trip export → wipe → import restores all habits and entries |

Units 1 → 2 → 3 must be sequential. 4 and 5 depend on 3. 6 depends on 3. 7 depends on 3.

**This proposal does not choose the delivery strategy.** `delivery_strategy` is `ask-on-risk`, so the split decision goes to the user after `sdd-tasks`.

## Rollback Plan

Required by `config.yaml`'s proposal rules for anything touching scheduling/alarms or persisted data.

### Scheduling / alarm work (units 4–5)

1. **Cancel before revert**: shipping a build that removes the receivers leaves orphaned `PendingIntent`s registered with the system. The rollback build MUST cancel every known alarm on first run (`AlarmManager.cancel` over all persisted slot occurrences) before the scheduling code is removed.
2. **Feature-flag the pipeline** behind a single settings/build flag so reminders can be disabled without a code revert. Disabling the flag must cancel alarms, not merely stop scheduling new ones.
3. **WorkManager**: call `cancelUniqueWork` for the reconcile and sweep workers in the rollback path; enqueued periodic work otherwise survives app update.
4. **Revert order** is the inverse of the dependency order: 5 → 4. Per-unit git revert is safe once alarms are cancelled.
5. **No data loss on rollback**: entries already written by notification actions stay valid; only scheduling stops.

### Persisted data (unit 3, and any later migration)

1. **Room schema export ON** (`room.schemaLocation`) from the first commit, so every version is diffable and reversible.
2. **Additive-only migrations.** `Entry.value` ships nullable and unused precisely so numeric habits never require a destructive migration. No `DROP COLUMN`, no type narrowing.
3. **Pre-migration backup**: before applying any migration, write an automatic export file. A failed migration recovers by clearing app data and importing that file (unit 7 is the recovery mechanism, which is why it stays in MVP scope).
4. **Never ship `fallbackToDestructiveMigration()`** in a release build.
5. **Rolling a schema version backwards is not supported by Room** — the downgrade path is uninstall/clear-data plus import of the pre-migration export.

## Dependencies

- Android SDK 36 platform + build tools; Gradle with Kotlin DSL.
- Room, Jetpack Compose + Material 3, WorkManager (versions unpinned — `sdd-design`).
- `sdd-design` must ratify `minSdk`, the toolchain versions, the DI choice, and update `openspec/config.yaml`'s `stack.architecture` to two modules.

## Success Criteria

- [ ] A habit can be created with each of the six frequency kinds and answered in-app.
- [ ] A `TIMES_PER_DAY` habit fires one notification per clock-time slot, each satisfied or missed independently.
- [ ] Yes / No / Snooze all work from the notification drawer without opening the app.
- [ ] Snooze defaults to 20 min, is configurable among 10/20/30 min and 1/2/3/4 h, and is unlimited.
- [ ] A snoozed answer given after midnight credits the originally-scheduled date, transitioning that `Entry` from `missed` to `completed`.
- [ ] An unanswered slot becomes `missed` at the midnight boundary.
- [ ] `skipped` neither breaks a streak nor counts as a failure, and is excluded from both sides of the compliance ratio.
- [ ] Alarms are correctly re-armed after each of the five mandatory triggers.
- [ ] With `SCHEDULE_EXACT_ALARM` denied, reminders still arrive in an inexact window and nothing crashes.
- [ ] With `POST_NOTIFICATIONS` denied, the in-app today list remains fully usable.
- [ ] `:domain` contains zero Android SDK imports and its tests run on the JVM via `./gradlew :domain:test`.
- [ ] Export → wipe → import restores every habit, schedule, slot, and entry unchanged.
- [ ] No file in the repository is copied, translated, or derived from `iSoron/uhabits`.
