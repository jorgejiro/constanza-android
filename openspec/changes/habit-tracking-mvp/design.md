# Design: Habit-tracking MVP

Change: `habit-tracking-mvp` · Phase: `sdd-design` · Date: 2026-08-31
Inputs: `proposal.md` (binding), `exploration.md` (evidence), `openspec/config.yaml` (`rules.design`)
Companion: `specs/*/spec.md` (written in parallel by `sdd-spec`; see §17 for the one divergence)

Constanza fires an exact alarm per reminder occurrence, answers it from the notification drawer
through a validate-only `BroadcastReceiver` plus an expedited worker, and derives every streak and
compliance figure on read from an indexed relational store. The whole scheduling and scoring engine
lives in a pure-Kotlin `:domain` module so it is provable on the JVM without an emulator; everything
Android lives in `:app`, organised by capability so the package tree names the product.

## Quick path for a reviewer

1. **§5 first.** `targetSdk = 37` is the highest-risk decision in this change and §5 states plainly
   what was verified locally and what could **not** be verified. Read the gap before the rest.
2. **§7 D3** — the provisional-`missed` problem. This design rejects both options the proposal
   offered and takes the third: the midnight job never writes `missed` over a live snooze.
   That is the one decision that diverges from the parallel spec (§17).
3. **§8** Room schema, and the additive migration path for numeric habits.
4. **§9** the two sequence diagrams `config.yaml` requires.
5. **§12** what happens when every permission is denied and the OEM throttles the app.

## 1. Binding constraints restated

### Licensing (binding, restated per the proposal)

Loop Habit Tracker (`iSoron/uhabits`) is **GPL-3.0**. No code, structure, or algorithm in Constanza
may be copied, translated, or derived from it. Conceptual prior-art discussion is permitted; every
decision in this document is reached independently from first principles and from the Android
platform contract. Concretely, the three Loop limitations named in `exploration.md` §C are avoided by
construction and not by adaptation: `Schedule` is `kind`-typed rather than a fraction, `Entry` is
slot-granular rather than day-granular, and `status` is a first-class enum that never carries a
measured value.

### Fixed product decisions (not re-opened here)

The twelve ratified decisions in the launch contract and `exploration.md` §"Fixed product decisions"
are treated as given: six frequency kinds; explicit clock times for several-times-per-day with
independent satisfaction; Yes/No only with an additive path to numeric; four entry states with
`skipped` neutral; Yes/No/Snooze actions with a 20-minute default and unlimited snoozing; local-only
storage forever with manual export/import and no conflict resolution; the three unblocked post-MVP
features; the permanent non-goals; two modules; midnight `missed` transition; and origin-date
crediting.

### Orchestrator assumptions carried into this design

Each was marked **`[OA]`** at the point of use and needed confirmation at design review. **That review never
happened as an event.** OA-3 was ratified 2026-09-01 only because task 6a.8 could not be built without it,
after surviving four work units unconfirmed; the rest were closed the same day once that near-miss made the
debt visible. All five are now settled.

| # | Assumption | Where used | Status |
|---|---|---|---|
| OA-1 | Export/import stays in MVP as the failed-migration recovery path | §8.4, §10 | **Settled** — already covered by ratified product decision 6 ("local-only forever with manual export/import to file"). Never actually an open question. |
| OA-2 | Today screen shows per-slot state; the day rollup is a separate `:domain` function | §7 D2, §11 | **REVISED and ratified 2026-09-01** — see below. |
| OA-3 | `N_TIMES_PER_WEEK` fires daily until the quota is met, then silent; nothing if no time is set | §7 D7, D8 | **Ratified 2026-09-01** as written. Already implemented and device-verified (§13.4's D8 result), so confirmation cost no rework. |
| OA-4 | Compliance is window-parameterised; the MVP UI passes 30 days | §11 | **Ratified 2026-09-01** as written: a fixed 30-day window in the MVP UI. The calculator stays parameterised, so a user-selectable window remains a cheap later addition. |
| OA-5 | Import replaces all data rather than merging | §8.4 | **Ratified 2026-09-01** as written. Replace-all keeps import a recovery path and avoids conflict-resolution logic, which ratified decision 6 rejected the cloud precisely to avoid. Work unit 7 must make the destructive nature explicit in the UI before it runs. |

**OA-2, as revised.** The today screen shows **one row per habit carrying the day rollup, expandable to that
habit's per-slot rows**, each slot independently answerable. A single-slot habit therefore reads as one plain
row.

The user's first answer was a day-level state only, and it was withdrawn once the conflict was laid out —
recorded here because the reasoning is the useful part. Four things already in the product require slot
independence: ratified product decision 2 ("each slot fires its own notification and is satisfied/missed
independently. NOT a counter-without-time model"); `habit-entry-tracking`'s **MUST** that each slot occurrence
"MUST have its own `Entry` and MUST be answerable independently of every other slot that date"; the shipped
schema's `UNIQUE(habitId, date, slotId)` on `entries` and its per-slot occurrence identity; and the shipped,
device-verified notification path, where each slot posts its own notification whose Yes/No/Snooze answers that
slot alone.

The decisive argument was not purity but a hole: a three-slot habit already sends three separate
notifications, so a day-level-only screen leaves a user who missed the 14:00 one with **no in-app way to answer
that slot** — either a day-level answer writes to some arbitrary slot, or single-slot answers exist only
through notifications. The expandable row keeps the uncluttered glance the day-level answer was reaching for
while preserving the capability the rest of the system already provides.

## 2. Ratified stack

| Item | Value | Basis |
|---|---|---|
| `minSdk` | **31** | Ratified by the user 2026-08-31 |
| `compileSdk` | **37** | Ratified 2026-08-31; `android-37.0` verified installed |
| `targetSdk` | **37** | Ratified 2026-08-31, deliberate forward step |
| Modules | `:domain` (Kotlin JVM), `:app` | Ratified; supersedes the full multi-module plan in `config.yaml` |
| Language / build | Kotlin, Gradle Kotlin DSL, version catalog | Carried from `config.yaml`, confirmed |
| UI | Compose + Material 3 | Carried, confirmed |
| Persistence | Room for domain data; DataStore Preferences for settings | Confirmed / added (§7 D9) |
| Scheduling | `AlarmManager` fires, `WorkManager` reconciles | Confirmed |
| DI | **Hilt with KSP** | Decided here (§7 D5) |
| Serialization | **kotlinx-serialization-json**, `:app` only | Decided here (§7 D9) |
| Unit test framework | **JUnit4** (revises the unratified JUnit5 plan) | Decided here (§7 D10) |
| Room / WorkManager versions | **Still unpinned** | Not resolvable offline; pin at scaffolding |
| Lint / formatter | **Still unchosen** | Out of this design's evidence base |

Toolchain versions verified working in the user's sibling project `sleep-noise-android`
(`gradle/libs.versions.toml`, `app/build.gradle.kts`) and therefore proposed as the starting catalog:
AGP `9.3.2`, Kotlin `2.4.10`, KSP `2.3.11`, Compose BOM `2026.08.00`, `material3 1.5.0-alpha26`,
Hilt `2.60.1`, JUnit `4.13.2`, MockK `1.14.11`, Turbine `1.2.1`, DataStore `1.2.1`, Java 11
source/target compatibility, and the AGP 9 `compileSdk { version = release(37) }` DSL. Those versions
are *evidence of a working combination*, not a build proof for this repository: work unit 1 must
build them here before any feature task depends on them.

## 3. `config.yaml` reconciliation

`openspec/config.yaml` is updated by this phase:

- `stack.architecture` rewritten from full multi-module to the ratified two modules with
  capability-first packaging inside `:app`.
- `stack.status` moved from `planned_unratified` to `partially_ratified`, split into
  `ratified` (user), `ratified_by_design` (this phase), and `still_unpinned`.
- `stack.sdk` added with `minSdk`/`compileSdk`/`targetSdk`, the `android-37.0` prerequisite, and the
  unverified-behaviour-change risk recorded in the file itself.
- `testing.design_intent` added, recording the commands and frameworks this design intends.
- `testing.*.available` flags and `strict_tdd` are **left untouched**: no build system exists, so
  those values are still honest. The flip gate is recorded as data, not applied.

## 4. Two-module boundary — exactly what may not cross

```
:domain   kotlin("jvm")            :app   com.android.application
  Habit, Schedule, ReminderSlot      Compose UI, Room, DataStore, Hilt,
  Entry, EntryStatus, Due            AlarmManager + WorkManager adapters,
  dueOn(), rollupDay(),              BroadcastReceivers, notifications,
  StreakCalculator,                  export/import, mappers
  ComplianceCalculator
        ▲                                        │
        └──────────── depends on ────────────────┘   (never the reverse)
```

**`:domain` allowed dependencies**: Kotlin stdlib and `java.time` only. `java.time` needs no core
library desugaring at `minSdk 31`, so `:domain` needs no Android Gradle plugin and no desugar
configuration at all.

**`:domain` forbidden, and why the ban is real rather than aspirational**:

| Forbidden in `:domain` | Enforcement |
|---|---|
| `android.*`, `androidx.*` (incl. Room and WorkManager annotations) | **Compile-time.** The module applies `kotlin("jvm")` and never `com.android.library`, so these symbols cannot resolve. No lint rule needed. |
| Hilt / `javax.inject` annotations | Not on the classpath; `:domain` types are plain constructors that `:app` wires. |
| Compose, `Parcelable`, `Context`, `Log`, `SharedPreferences` | Same compile-time bar. |
| JSON/serialization libraries | Not on the classpath; serialization is an `:app` adapter concern (§7 D9). |
| Filesystem, I/O, coroutines | Not on the classpath. Every `:domain` function is synchronous and pure. |
| `LocalDate.now()`, `System.currentTimeMillis()`, `ZoneId.systemDefault()` | **NOT compile-enforced.** Time enters `:domain` as an explicit parameter. Enforced by review, and by a detekt `ForbiddenMethodCall` rule once a linter is chosen. This is the one boundary rule that can be violated silently — flag it in review. |

**`:app` obligations**: no `:app` type may appear in a `:domain` signature. Room entities, Compose
state holders, and `Context` stop at `app/.../core/data/mapper`. `:domain` never learns that Room
exists.

**Why not more modules.** Gradle module boundaries pay for themselves at team-scale parallelism or
measurable incremental-build pain; neither exists here. The `:domain` split is bought for a concrete
return — JVM-only tests for the scheduling and scoring engine, which is what makes strict TDD
achievable for the riskiest logic. Deeper splits (`:feature-*`, `:core-ui`) wait for a second build
target (widget, Wear OS) or a measured build-time problem.

**Inside `:app`, packages are capability-first, not layer-first**, so the tree screams the domain:

```
app/src/main/kotlin/<pkg>/
  habit/          create, edit, archive; habit repository
  scheduling/     occurrence planner, AlarmScheduler adapter, reschedule receivers, workers
  tracking/       entry writes, today screen
  reminding/      channel, notification builder, action receiver, answer/snooze workers
  progress/       streak + compliance presentation
  portability/    export / import
  core/           database, di, time, permissions, ui theme
```

This refines the proposal's illustrative `data/ reminder/ ui/` paths. A layer-first tree would name
the framework rather than the product, which is the opposite of what ratified decision 9 asks for.

## 5. API 37 (Android 17) analysis — mandatory consequence 1

### 5.1 Verification status — read this before trusting anything below

**This phase had no web-search and no context7 tool available.** The `targetSdk`-gated behaviour
changes for Android 17 could therefore **not** be read from authoritative Android documentation.
What follows is split into (a) facts verified against the API 37 platform installed on this machine,
which is authoritative for *API surface*, and (b) an explicit list of what remains **unverified**.

Per the launch contract's instruction: where authoritative information could not be found, this
document says so instead of asserting safety. **§5.4 is a blocking gate, not a caveat.**

> **UPDATE 2026-08-31 — the documentation half of that gate has been discharged by the
> orchestrator, which does have web access. Read §5.7 before acting on §5.3: U1, U2, U3, U5 and
> U6 are now resolved as "no documented change", and three behaviour changes that DO affect this
> app were found and are recorded there. The on-device delivery matrix (§13.3) remains open.**

### 5.2 Verified locally against `$ANDROID_HOME/platforms/android-37.0`

| # | Fact | Evidence |
|---|---|---|
| V1 | API 37 is **Android 17**, a released platform, not a preview: `ro.build.version.release=17`, `ro.build.version.codename=REL`, `ro.build.version.preview_sdk=0`, `sdk_full=37.0`. Platform build 2026-05-26, security patch 2026-05-05. | `platforms/android-37.0/build.prop` |
| V2 | `ro.build.version.min_supported_target_sdk=28`, so `targetSdk = 37` sits far above the platform floor. | same |
| V3 | The platform is **already installed on this machine** (`android-37.0`), alongside `android-36.1`. | `platforms/` listing |
| V4 | All five reschedule-trigger broadcasts still exist in API 37: `android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, `android.intent.action.BOOT_COMPLETED`, `…DATE_CHANGED`, `…TIME_SET`, `…TIMEZONE_CHANGED`, `…MY_PACKAGE_REPLACED`. `LOCKED_BOOT_COMPLETED` also present. | `data/broadcast_actions.txt` |
| V5 | `AlarmManager.canScheduleExactAlarms()` (since 31), `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (since 31), `setExactAndAllowWhileIdle(int,long,PendingIntent)` (since 23), `setWindow(...)` and `setAndAllowWhileIdle(...)` are all present in API 37 and **not deprecated**. | `data/api-versions.xml:6255-6288` |
| V6 | API 37 **adds** a listener overload `setExactAndAllowWhileIdle(int, long, String, Executor, OnAlarmListener)` (`since="37.0"`). The `PendingIntent` overload this design uses is untouched. | same |
| V7 | Permission API levels unchanged: `SCHEDULE_EXACT_ALARM` 31, `USE_EXACT_ALARM` 33, `POST_NOTIFICATIONS` 33, `RECEIVE_BOOT_COMPLETED` 1, `FOREGROUND_SERVICE` 28. None deprecated or removed in 37. | `data/api-versions.xml` |
| V8 | **No** alarm-, notification-, foreground-service-, or JobScheduler-related API is deprecated at `deprecated="37.0"`. The only match in that keyword space is `MediaSession2Service$MediaNotification`, irrelevant here. | `data/api-versions.xml` |
| V9 | API 37 notification additions that are *available but not required*: `Notification.Action.Builder.setEmphasisHint(int)` and `setStyleHint(int)`; `Notification.Metric` / `MetricStyle`; `Notification.BridgedNotificationMetadata` and `ACTION_BRIDGED_NOTIFICATION_PREFERENCES`; `Notification.ProjectedExtender`. `JobScheduler.getPendingJobReasonStats(int)` is new and useful for diagnosing deferred expedited work. | `data/api-versions.xml` |

### 5.3 NOT verified — stated as gaps, not as safety

An SDK platform image encodes API *surface*. It does **not** encode `targetSdk`-gated *behaviour*.
Consequently this design **does not assert** that any of the following is unchanged at
`targetSdk = 37`:

| # | Unverified area | Why it matters here |
|---|---|---|
| U1 | Exact-alarm scheduling quotas, per-app rate limits, or new conditions under which `setExactAndAllowWhileIdle` is downgraded or refused | Directly on the critical path |
| U2 | Whether the `SCHEDULE_EXACT_ALARM` grant model tightened further (for example auto-revocation after app inactivity, or a new denied-by-default class) | Would change §12 row 1 from an edge case to the common case |
| U3 | Notification posting limits, per-app rate limiting, action-count limits, or drawer trimming rules | Yes/No/Snooze must all remain visible without an overflow |
| U4 | Foreground-service start restrictions and permitted types | Low exposure: this MVP starts **no** foreground service (§5.5) |
| U5 | Background execution: expedited `JobScheduler` quotas, Doze and App Standby bucket behaviour, `BOOT_COMPLETED` delivery conditions | The reconcile net and the answer-write path both depend on these |
| U6 | Whether `targetSdk = 37` changes broadcast receiver registration or delivery for the five triggers | V4 proves the actions exist, not that delivery is unchanged |

### 5.4 Blocking verification gate (must be discharged by `sdd-tasks` / work unit 1)

Before scaffolding merges, and before any release with `targetSdk = 37`:

- [x] **DISCHARGED 2026-08-31 by the orchestrator — see §5.7.** Read
      `developer.android.com/about/versions/17/behavior-changes-17` (apps targeting API 37 — note the
      correct slug is `-17`, not `-37`) and `…/behavior-changes-all` (all apps), and reconciled every
      entry against §5.5 and §12.
- [x] **DISCHARGED 2026-09-01 — see §13.4.** Re-read the exact-alarm guide
      (`developer.android.com/develop/background-work/services/alarms/schedule`). It settled three
      questions the matrix run raised; the background-work restrictions pages were not re-read, and
      that remains open below.
- [x] **DISCHARGED 2026-09-01 — see §13.4.** Record the reconciliation as an amendment to this
      document. If any item contradicts §5.5, the contradiction is a design change, not an
      implementation detail. Nothing found contradicts §5.5; §13.3's own recipes did not survive.
- [x] **DISCHARGED 2026-09-01 for the eight non-UI scenarios — see §13.4.** Run the **§13.3** (not
      §14.3 — that cross-reference was wrong) manual delivery matrix on an **API 37** image, not only
      on API 31. The four UI-dependent rows are deferred, with the reason recorded in §13.4.
- [ ] Re-read the background-work restrictions pages at API 37. Not covered by the §13.4 run.

Until that gate is discharged, `targetSdk = 37` carries an unquantified delivery risk. Lowering
`targetSdk` to 36 remains an available, cheap, one-line mitigation if the gate surfaces a blocker —
it does not invalidate anything else in this design.

### 5.5 Design measures that hold regardless of which behaviour changed

These are chosen precisely because they do not depend on knowing U1–U6:

| Measure | Effect |
|---|---|
| The alarm is best-effort; the **hourly reconcile worker is the correctness net**, not an optimisation | If API 37 defers or drops an alarm, the reminder arrives late instead of being lost |
| Every notification post is preceded by `areNotificationsEnabled()` **and** a channel-importance check; a failed post leaves the occurrence unresolved | A silently suppressed notification never becomes a false `missed` |
| **No foreground service anywhere in the MVP.** At `minSdk 31`, expedited `WorkManager` maps to expedited `JobScheduler` jobs, so the FGS-fallback path does not exist | Removes U4 from the risk surface entirely |
| `canScheduleExactAlarms()` before **every** scheduling call, plus the state-changed receiver, plus an `onResume()` re-check | A grant model that tightened at 37 degrades instead of crashing |
| Answer writes are idempotent upserts keyed on `(habitId, date, slotId)` | Redelivered broadcasts and retried workers cannot double-write |
| Occurrence state is persisted, so "which alarms should exist" is always recomputable from the database | Recovery after any platform-initiated cancellation is a query, not a guess |
| `PendingIntent.FLAG_IMMUTABLE` everywhere; `android:exported` explicit on every component | Both are already mandatory at API 31+; no 37-specific work |

### 5.6 The other four mandatory consequences

| # | Consequence | Handling |
|---|---|---|
| 2 | `minSdk = 31` ⇒ `SCHEDULE_EXACT_ALARM` always exists as a concept; **no pre-31 exact-alarm fallback** | The scheduler has exactly two modes, exact and inexact-window, selected by `canScheduleExactAlarms()`. No `Build.VERSION` branch exists in the alarm path at all. Designing a pre-31 path is explicitly forbidden. |
| 3 | `POST_NOTIFICATIONS` is API 33+ ⇒ a runtime-permission branch **is** required for 31–32 | Single gate in `core/permissions/NotificationPermission`: on `SDK_INT >= 33` request at a contextual moment; on 31–32 **never request** — the permission does not exist and requesting it is a no-op that would show nothing. Both branches then converge on `areNotificationsEnabled()` plus channel importance, which is the check that actually matters on 31–32 (the user can still mute the channel). |
| 4 | `android-37` platform must be present locally | Contributor prerequisite in the README: AGP downloads it on first build. Verified already installed on this machine as `android-37.0`; a fresh clone on another machine will trigger the download. |
| 5 | No `OldTargetApi` lint suppression needed | `targetSdk` equals `compileSdk` equals the newest platform, so the warning the sibling project deliberately left visible does not arise here. No suppression is added; adding one would be a code smell to reject in review. |

### 5.7 Gate discharge — authoritative documentation read 2026-08-31 (orchestrator)

Both Android 17 behaviour-change pages were fetched and reconciled against §5.3 and §5.5.

**Sources:** `developer.android.com/about/versions/17/behavior-changes-17` (16 entries, gated on
`targetSdk >= 37`) and `developer.android.com/about/versions/17/behavior-changes-all` (7 entries,
all apps regardless of `targetSdk`).

#### Resolution of the §5.3 gaps

| # | Area | Result |
|---|---|---|
| U1 | Exact-alarm quotas, rate limits, `setExactAndAllowWhileIdle` downgrade conditions | **No documented change in Android 17.** Neither page contains any AlarmManager or exact-alarm entry. |
| U2 | `SCHEDULE_EXACT_ALARM` grant model tightening | **No documented change.** The grant model is unchanged from Android 14's denied-by-default. §12 row 1 stays an edge case. |
| U3 | Notification posting limits, rate limiting, action-count limits, drawer trimming | **No documented change.** Yes / No / Snooze remain three ordinary actions. |
| U4 | Foreground-service restrictions | **Not applicable** — the MVP starts no foreground service (§5.5). |
| U5 | Expedited `JobScheduler` quotas, Doze and App Standby buckets, `BOOT_COMPLETED` delivery | **No documented change.** Neither page contains a background-execution, Doze, or App Standby entry. |
| U6 | Broadcast registration or delivery for the five triggers | **No documented change** to broadcast delivery. Combined with V4, the five triggers stand. |

**Honest limit of this result:** absence from the two behaviour-change pages is strong evidence, not
a proof of absence — Google documents behaviour changes there, but an undocumented tightening or an
OEM-specific deviation would not appear. The §13.3 on-device delivery matrix on an API 37 image
therefore remains a required, undischarged item. Do not mark it done on the strength of this section.

#### Three Android 17 behaviour changes that DO affect this app

None of these blocks `targetSdk = 37`, and none contradicts §5.5. Two are constraints on work not yet
written; one widens the UI work unit's scope.

| # | Change | Source | Consequence here |
|---|---|---|---|
| C1 | **Large screens: opt-out removed.** Orientation, resizability and aspect-ratio constraints are ignored on large screens (`sw >= 600dp`). The SDK 36 opt-out "will no longer be available for apps that target Android 17 (API level 37) or higher". | gated #15 | **Widens the UI work unit.** The Compose UI MUST work at tablet and foldable widths and in any orientation. This is not optional and cannot be opted out of at `targetSdk = 37`. Treat it as acceptance criteria for the today screen and the habit create/edit screen, not as a later polish task. |
| C2 | **RemoteViews memory limit.** The system enforces `1.5 * screenWidth * screenHeight * 4` against combined `Bitmap` and `Icon` memory in a `RemoteViews` parcel; exceeding it "throws a fatal `IllegalArgumentException` and crashes the app's process". | gated #1 | **No MVP impact** — reminder notifications use standard templates, not custom `RemoteViews`. It becomes a hard constraint on (a) any custom notification layout and (b) the future home-screen widget and printable-grid rendering. Per-habit colour must stay a tinted vector icon, never a large bitmap. Record it as a binding constraint on the deferred widget. |
| C3 | **Background audio hardening.** Background audio playback, audio-focus requests and volume-change APIs "fail silently without throwing an exception"; audio focus returns `AUDIOFOCUS_REQUEST_FAILED`. Exemption on the gated page: the app "must have the exact alarm permission and be interacting with `USAGE_ALARM` audio streams". | gated #14, all-apps #6 | **No MVP impact** — reminder sound is played by the system from the `NotificationChannel`, not by the app, so no app-side audio API is called. It becomes relevant only if a custom reminder sound is ever played by the app itself. Worth recording that Constanza *would* satisfy the exemption, since it holds `SCHEDULE_EXACT_ALARM` and would use `USAGE_ALARM`. |

#### Two minor items worth knowing

| # | Change | Source | Consequence here |
|---|---|---|---|
| C4 | IME visibility is not restored after an unhandled configuration change such as rotation. | all-apps #4 | The habit create/edit screen must not assume the soft keyboard survives rotation; request visibility explicitly if it matters. Interacts with C1, since rotation is now always reachable on large screens. |
| C5 | App memory limits based on device RAM; kills are reported as `ApplicationExitInfo` `REASON_OTHER` with a description containing `MemoryLimiter:AnonSwap`. | all-apps #1 | No expected impact at this app's footprint. Useful diagnostic string if a tester reports silent death. |

#### Items confirmed as irrelevant

Local network permission, ECH, Certificate Transparency, cross-profile loopback (all network — this
app is local-only and makes no network calls); SMS OTP protection; `MessageQueue` lock-free rewrite
and `static final` immutability (both only break reflection hacks, of which this design has none);
native DCL read-only enforcement (no native libraries); CP2 PII and strict-SQL restrictions (no
Contacts access); Bluetooth RFCOMM `read()` and autonomous re-pairing; touchpad pointer capture;
CJKV IME accessibility events.

#### Verdict

**`targetSdk = 37` is cleared for this app's critical path.** No documented Android 17 behaviour
change affects exact alarms, notification delivery, expedited background work, Doze, or the five
reschedule broadcasts. The `targetSdk = 36` fallback named in §5.4 is not needed on current evidence
and should not be taken pre-emptively. C1 must be folded into the UI work unit's scope; C2 must be
recorded as a binding constraint on the deferred widget.

## 6. Data flow

```
                    ┌──────────────── :domain (pure) ────────────────┐
                    │  dueOn(schedule, date, progress, weekStart)    │
                    │  rollupDay(schedule, date, entries)            │
                    │  StreakCalculator / ComplianceCalculator       │
                    └───────────▲───────────────────▲────────────────┘
                                │                   │
  OccurrencePlanner ────────────┘                   └──────── TodayViewModel
        │                                                        ▲
        │ upsert reminder_occurrences                            │ Flow<TodayState>
        ▼                                                        │
  ┌───────────── Room (habits, schedules, reminder_slots, entries, reminder_occurrences) ─────────────┐
        ▲                    ▲                        ▲
        │                    │                        │
  AlarmScheduler       AnswerWorker /           MidnightSweepWorker /
  (AlarmManager)       SnoozeWorker             ReconcileWorker
        │                    ▲
        │ PendingIntent      │ expedited enqueue
        ▼                    │
  ReminderReceiver ──► NotificationPoster ──► drawer ──► ActionReceiver
  (fire)                                                (Yes / No / Snooze)
```

Two invariants make the whole picture safe:

1. **`reminder_occurrences` is the single source of truth for "which alarms should exist."** The
   platform's alarm table is a cache of it, never the reverse. Every reschedule trigger is just
   "re-derive the alarms from this table".
2. **An `Entry`'s date always comes from `occurrence.scheduledDate`, never from `now()`.** That one
   rule implements ratified decision 12 (origin-date crediting) everywhere, including for a snoozed
   answer given after midnight.

## 7. Architecture decisions

### D1 — Exactly two Gradle modules

| Option | Trade-off | Decision |
|---|---|---|
| Full multi-module Clean split (`:core-*`, `:feature-*`) | Buys team parallelism and build isolation nobody needs yet; costs per-module build files and visibility plumbing | Rejected |
| Single `:app` module | Cheapest, but the scheduling and scoring engine then needs an Android runtime to test | Rejected |
| **`:domain` (Kotlin JVM) + `:app`** | One boundary, bought for JVM-testability of the riskiest logic; Clean/Hexagonal preserved by package discipline inside `:app` | **Chosen** |

Rationale: the boundary is justified by a concrete return (emulator-free tests of the engine), and
the module *type* enforces the Android-free rule at compile time rather than by convention. This
supersedes `config.yaml`'s full multi-module plan, which was marked `planned_unratified` precisely so
this phase could revise it.

### D2 — Slot-aware `Entry`, and a sealed due-result instead of a Boolean

Adopted from `exploration.md` Option 1: `Entry(habitId, date, slotId, status, value = null)`;
`Schedule` is `kind`-typed; `status` is a first-class enum; `value` is a reserved nullable column.

**One argued refinement.** The exploration specified the occurrence-due predicate as
`Schedule + date → Boolean`. A Boolean is not sufficient, and the reason is structural rather than
cosmetic: `N_TIMES_PER_WEEK` has no determinate obligation on any particular date, so "due" means
something different for it than for the other five kinds. With a Boolean, either the midnight sweep
or the fire-time suppression logic must re-branch on `Schedule.kind` **inside `:app`**, which pushes
a domain rule into the Android layer and duplicates it across two call sites. The predicate therefore
returns a sealed result:

```kotlin
sealed interface Due {
    data object NotDue : Due                                   // not an obligation on this date
    data object Required : Due                                 // determinate obligation on this date
    data class Candidate(val quotaRemaining: Int) : Due        // N_TIMES_PER_WEEK only
}
```

This still answers the exploration's question ("is this habit due?") while carrying the one extra bit
that both the reminder path and the scorer need. `Required` alone authorises a dated `missed` (D8).

Per **OA-2**, `rollupDay` exists as a separate `:domain` function even though the MVP Today screen
renders per-slot state: the digest and the printable grid need exactly that rule, and defining it
once now is what keeps them unblocked.

### D3 — Provisional `missed`: the midnight job does not overwrite a live snooze

This is the decision the launch contract asked to be solved rather than papered over. Ratified
decisions 10 (unanswered ⇒ `missed` at midnight) and 12 (a snoozed answer credits the origin date)
together mean an `Entry` can legitimately go `missed → completed`.

| Option | What it costs | Decision |
|---|---|---|
| **A. Write `missed`, hide the break in the UI** | The UI displays a streak that contradicts persisted state, potentially for hours. Two sources of truth for "is the streak broken", and the lie has to be replicated in the future digest and the printable grid or they will disagree with the screen. | **Rejected** |
| **B. Write `missed`, show a visible flicker** | A correct streak visibly breaks and un-breaks. The user's trust in the number is the product; a number that jumps around destroys it. Also makes `missed` a state the user sees for something they did not miss. | **Rejected** |
| **C. Do not write `missed` while a snooze is live** | Needs persisted snooze state, and needs an abandonment rule so an unresolved slot cannot linger forever | **Chosen** |

**Why C is not merely the least-bad option but the correct model.** `missed` is an assertion about
the user's behaviour. While a snooze is outstanding, that assertion is simply false: the user did not
fail to answer, they deferred, and the app itself is holding an armed alarm that proves it. A and B
are both attempts to manage the consequences of writing a fact that is not yet true. C declines to
write it. There is then nothing to hide and nothing to flicker, and the persisted state, the screen,
the digest, and the grid all agree without any of them special-casing anything.

**The state while pending is `unknown`, not a new fifth state.** Ratified decision 4 fixes the enum
at four values and this design does not extend it. `unknown` is exactly right: the slot is genuinely
undecided. Compliance already excludes `unknown` from both numerator and denominator, and the streak
treats it as pass-through (§11), so a pending slot perturbs no arithmetic. The Today screen renders
it as *pending, snoozed until 00:10* by reading `reminder_occurrences`, which is presentation detail,
not a new domain state.

**Cost C must pay: an abandoned snooze chain must still resolve.** Snoozing is unlimited (decision
11) and a user can dismiss a notification without answering. Without a rule, `unknown` leaks forever
and silently drops days from the compliance denominator, which would *inflate* the score — a worse
failure than a flicker. The rule has three layers and is fully deterministic:

1. **A snooze is never open-ended.** Every snooze arms a concrete alarm, so a live chain always has a
   `snoozeUntil` timestamp at most 4 h out (the largest configurable interval). "Live" means
   `state = SNOOZED AND snoozeUntil > now`.
2. **Grace expiry.** If the snooze alarm fires, the notification is re-posted, and the user dismisses
   it without answering, no new alarm is armed. The hourly `ReconcileWorker` then sees
   `state = SNOOZED AND snoozeUntil < now - grace` and resolves the occurrence to `missed` dated on
   `scheduledDate`, with `state = ABANDONED`. `grace` equals one reconcile period (1 h), so worst-case
   resolution lag after the last snooze is about 5 h.
3. **Hard resolve deadline.** Each occurrence stores
   `resolveDeadline = scheduledAt + 24h`, clamped to the next occurrence of the same slot. At the
   deadline any still-unresolved occurrence is force-resolved to `missed`, its pending alarm is
   cancelled, and its notification is dismissed. This bounds how long any date's arithmetic can stay
   open and guarantees **at most one live occurrence per slot**, which is also what keeps notification
   IDs and `PendingIntent` request codes unambiguous (§8.2).

This is **not** a snooze cap. The user may snooze any number of times; what is bounded is how long a
*calendar date* stays undecided, which is a storage and arithmetic concern, not a product limit.
Decision 11 is preserved intact.

**The `missed → completed` transition requirement survives and is still mandatory.** C removes the
flicker from the normal path; it does not remove the transition, because three paths still produce
it: a force-resolved occurrence that the user answers afterwards from the drawer or in-app, a manual
in-app edit of a past day, and an import. So the proposal's and the spec's requirement that every
reader tolerate a late correction and recompute rather than cache stays exactly as written. What
changes is only *when the midnight job is allowed to write*. See §17 for the spec amendment this
implies.

### D4 — `reminder_occurrences` as the scheduling source of truth

Alternatives: recompute alarms from `Schedule` on demand and keep no occurrence rows; or store only
"next fire time" on the habit.

Chosen: a persisted occurrence row per scheduled fire, because four separate requirements all need
the same record and none of them can be met without it — origin-date crediting needs a persisted
`scheduledDate`; the no-flicker rule (D3) needs persisted snooze state; the missed-reminder sweep
needs to know an alarm *should* have fired and did not; and reboot/update re-arming needs a
deterministic list rather than a re-derivation that could drift from what was actually armed.
The row's primary key also serves as the notification ID and the `PendingIntent` request code, which
makes collisions structurally impossible.

Occurrences are **planned forward with a bounded horizon** (all occurrences within the next 48 h, plus
the single next occurrence per slot beyond it) and pruned once resolved and older than the compliance
window's needs. They are transient scheduling state, so they are deliberately **excluded from export**
(§8.4): importing stale alarm state would arm alarms for a past device's clock.

### D5 — DI: Hilt with KSP

| Option | Trade-off | Decision |
|---|---|---|
| Manual constructor injection + hand-written `AppContainer` | Zero build cost, but `WorkManager` workers and `BroadcastReceiver`s are framework-instantiated, so it needs a hand-rolled `WorkerFactory` and a service locator reachable from receivers — extra bespoke code in the highest-risk area of the app | Rejected |
| Koin | Runtime resolution means wiring errors surface as runtime crashes, notably inside a worker at 08:00; no first-party WorkManager integration | Rejected |
| **Hilt (KSP)** | Build-time verification; first-class `@HiltWorker` + `HiltWorkerFactory` and `@AndroidEntryPoint`, which is precisely the awkward spot here; already proven at Hilt `2.60.1` in the user's verified AGP 9.3.2 / Kotlin 2.4.10 toolchain, removing version-compatibility risk | **Chosen** |

Cost accepted: KSP processing adds build time, and Hilt is arguably heavy for two modules. Mitigated
by confining it: **`:domain` has no DI at all** — its types are plain constructors and pure functions
that `:app` instantiates. That both keeps the domain clean and reinforces D1's boundary.

### D6 — Streaks and compliance computed on read

Adopted from `exploration.md` §D unchanged. Per-habit row counts are tiny (roughly 3,650 rows per
decade of daily tracking); persisted counters would still need a recompute-on-edit safety net, so
they add a second source of truth that can drift without removing the read-time calculation. Edits,
skips, imports, and D3's late corrections all become free. Queries are served by the
`(habitId, date)` index.

### D7 — Week boundary is ISO-8601, parameterised, and never locale-derived

`N_TIMES_PER_WEEK` needs a defined week boundary. Options: the device locale's first day of week, or
a fixed ISO-8601 Monday start.

**Chosen: ISO-8601 (Monday 00:00 local start), threaded as an explicit `weekStart: DayOfWeek`
parameter with default `MONDAY`, and persisted per schedule.**

Rationale: a locale-derived boundary would silently re-partition a habit's history if the user
changed device region, retroactively altering past compliance figures — a stored number changing
because of a settings change elsewhere is unacceptable for the one figure the product exists to
provide. Threading it as a parameter rather than hardcoding it means a future "week starts on Sunday"
setting is a value change, not a schema or signature change. Trade-off accepted: US-locale users see
a Monday week until that setting ships.

**OA-3 reminder semantics.** If a reminder time is set, the alarm is armed **unconditionally every
day** and the quota is re-evaluated at fire time; if the quota is already met, the notification is
suppressed (`state = SUPPRESSED`) and nothing is written. Arming unconditionally and suppressing late
is deliberate: a suppressed alarm costs nothing, whereas cancelling and re-arming alarms after every
answer creates a lost-reminder failure mode on every write path. If no reminder time is set, no
occurrence is planned at all and the habit stays trackable in-app only.

### D8 — Dated `missed` only for determinate obligations

A subtle correctness point the proposal did not settle. The midnight sweep writes a dated `missed`
row **only** where `dueOn(...) == Required` — that is, for `DAILY`, `TIMES_PER_DAY`, `WEEKLY(dayOfWeek)`,
`MONTHLY(dayOfMonth)`, and `EVERY_N_DAYS(n, anchor)`.

For `N_TIMES_PER_WEEK` the unit of obligation is the **week**, not the day, so no date can carry a
`missed`. Writing per-day `missed` rows would fabricate up to seven failures out of one unmet weekly
quota and corrupt compliance. Instead the weekly shortfall is derived on read at the week boundary:
`compliance = min(completed, n) / n` for the week, and the streak unit for that kind is consecutive
weeks meeting quota. This is consistent with D6 (nothing extra is persisted) and it is the only
arithmetic that matches what the user actually promised.

### D9 — Export format, serialization, and settings storage

- **Export/import file: a single UTF-8 JSON document**, written through SAF
  (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) so no storage permission is needed, named
  `constanza-backup-<yyyyMMdd-HHmmss>.json`. Format in §8.4.
- Rejected: CSV (multi-table relational data plus settings needs several files or a nested encoding
  inside cells, and the exploration's own goal was a *human-editable* single file); raw SQLite file
  copy (opaque, not editable, couples the backup to Room internals and schema version, and can carry
  WAL/`-shm` state that makes the copy inconsistent).
- **Serialization library: `kotlinx-serialization-json`, in `:app` only.** `:domain` stays free of
  serialization annotations (D1); export DTOs are `:app` types mapped from domain types. Rationale:
  serialization is a wire-format concern, and annotating domain types would let the file format
  dictate the model.
- **Settings live in DataStore Preferences, not Room.** Snooze default and week start are not
  relational, DataStore is already verified in the user's toolchain, and keeping them out of Room
  means a settings change never needs a schema migration. Cost: the export writer must read two
  stores; that is five lines and is explicit in §8.4's `settings` object.

### D10 — Test framework: JUnit4, revising the unratified JUnit5 plan

`config.yaml` planned "JUnit5 / Kotlin (planned, unratified)". This design revises it to **JUnit4**,
on evidence:

- Android instrumented tests run on `AndroidJUnitRunner`, which is JUnit4-based; Room's
  `MigrationTestHelper`, `androidx.work:work-testing`, and `compose-ui-test-junit4` are all JUnit4
  rules. Instrumented tests would stay JUnit4 regardless of what `:domain` uses.
- JUnit5 on the Android plugin requires an extra third-party Gradle plugin. Adopting it would mean
  two runners, two assertion idioms, and one unverified plugin in a repository that cannot yet run a
  single test.
- The user's sibling project verifies JUnit `4.13.2` + MockK + Turbine + `kotlinx-coroutines-test`
  working against exactly this AGP/Kotlin pair.

`:domain` tests use JUnit4 with `kotlin.test` assertions and are plain, dependency-free, and fast.
JUnit5 or Kotest remain a later, isolated change for `:domain` only if a real need appears.

### D11 — `entries.slotId` is `NOT NULL DEFAULT 0`, not a nullable foreign key

A real SQLite gotcha, caught here rather than in production. The obvious schema is
`slotId INTEGER NULL REFERENCES reminder_slots(id)` with `UNIQUE(habitId, date, slotId)`. **That
unique index does not work**: SQLite treats `NULL`s as distinct, so for single-occurrence habits
(where `slotId IS NULL`) the constraint permits unlimited duplicate rows for the same habit and date.
Duplicate entries would silently corrupt every compliance figure, which is the one number the product
sells. Room's `@Index` cannot express an index on `IFNULL(slotId, -1)`.

**Chosen:** `slotId INTEGER NOT NULL DEFAULT 0`, where `0` means "no slot", and **no** foreign key to
`reminder_slots`. `UNIQUE(habitId, date, slotId)` then actually enforces. The domain still models
`slotId: Long?`; the mapper converts `0 ↔ null` at the `:app` boundary, so the sentinel never reaches
`:domain`.

Cost accepted: losing the FK means slot deletion cannot cascade. Handled explicitly — deleting a
`ReminderSlot` runs a Room `@Transaction` in `HabitRepository` that reassigns or deletes the affected
entries in the same transaction. That is a deliberate, documented trade of automatic referential
integrity for enforceable uniqueness, and uniqueness is the constraint that protects the data the
user cares about.

## 8. Room schema

`version = 1`, `exportSchema = true`, `room.schemaLocation = app/schemas/` from the very first commit
so every version is diffable and reversible. `fallbackToDestructiveMigration()` is **never** present
in any build type.

### 8.1 Tables

```sql
habits(
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT    NOT NULL,
  question    TEXT,                       -- "Did you meditate today?"
  colorArgb   INTEGER NOT NULL,
  notes       TEXT,
  archived    INTEGER NOT NULL DEFAULT 0,
  archivedAt  TEXT,                       -- ISO date; compliance stops here forward
  createdAt   TEXT    NOT NULL,           -- ISO-8601 instant
  sortOrder   INTEGER NOT NULL DEFAULT 0
)

schedules(                                -- exactly one per habit
  habitId       INTEGER PRIMARY KEY REFERENCES habits(id) ON DELETE CASCADE,
  kind          TEXT    NOT NULL,         -- DAILY | TIMES_PER_DAY | N_TIMES_PER_WEEK
                                          -- | WEEKLY | MONTHLY | EVERY_N_DAYS
  timesPerWeek  INTEGER,                  -- N_TIMES_PER_WEEK
  dayOfWeek     INTEGER,                  -- WEEKLY (1=Mon .. 7=Sun)
  dayOfMonth    INTEGER,                  -- MONTHLY (1..31, clamped to month length)
  intervalDays  INTEGER,                  -- EVERY_N_DAYS
  anchorDate    TEXT,                     -- EVERY_N_DAYS, ISO date
  weekStart     INTEGER NOT NULL DEFAULT 1
)

reminder_slots(
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  habitId     INTEGER NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
  minuteOfDay INTEGER NOT NULL,           -- 0..1439; sortable, comparable, DST-free
  enabled     INTEGER NOT NULL DEFAULT 1,
  UNIQUE(habitId, minuteOfDay)
)

entries(
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  habitId     INTEGER NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
  date        TEXT    NOT NULL,           -- ISO yyyy-MM-dd: lexicographic == chronological
  slotId      INTEGER NOT NULL DEFAULT 0, -- 0 = no slot (see D11); no FK, deliberately
  status      TEXT    NOT NULL,           -- COMPLETED | MISSED | SKIPPED
  value       INTEGER,                    -- RESERVED, always NULL in the MVP
  answeredAt  TEXT    NOT NULL,           -- ISO instant
  source      TEXT    NOT NULL,           -- NOTIFICATION | IN_APP | SWEEP | IMPORT
  UNIQUE(habitId, date, slotId)
)
CREATE INDEX idx_entries_habit_date ON entries(habitId, date);

reminder_occurrences(                     -- transient scheduling state; never exported
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,  -- == notification id == PI request code
  habitId            INTEGER NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
  slotId             INTEGER NOT NULL DEFAULT 0,
  scheduledDate      TEXT    NOT NULL,    -- the ORIGIN date; the only source of an Entry's date
  scheduledAtEpochMs INTEGER NOT NULL,
  state              TEXT    NOT NULL,    -- ARMED | FIRED | SNOOZED | RESOLVED
                                          -- | SUPPRESSED | ABANDONED
  exact              INTEGER NOT NULL DEFAULT 1,  -- 0 when armed as an inexact window
  snoozeUntilEpochMs INTEGER,
  snoozeCount        INTEGER NOT NULL DEFAULT 0,
  notifiedAtEpochMs  INTEGER,             -- set ONLY when a notification really posted; NULL
                                          -- when canPost() gated it (§11, §13.4 finding 1)
  resolveDeadlineMs  INTEGER NOT NULL,    -- scheduledAt + 24h, clamped to next same-slot occurrence
  UNIQUE(habitId, slotId, scheduledDate)
)
CREATE INDEX idx_occ_state_snooze ON reminder_occurrences(state, snoozeUntilEpochMs);
CREATE INDEX idx_occ_deadline     ON reminder_occurrences(resolveDeadlineMs);
```

Two storage choices worth naming. Dates are **ISO-8601 `TEXT`**, not epoch-day integers: for ISO
dates lexicographic order equals chronological order so `BETWEEN` and `ORDER BY` work directly, the
exported schema JSON and any `adb` dump stay human-readable, and there is no epoch/timezone
arithmetic to get wrong. Times of day are **`minuteOfDay` integers**, not strings, because they are
compared and sorted constantly and carry no timezone by construction.

**`unknown` is never persisted.** Rows exist only for decided states. Absence of a row for a due
occurrence *is* `unknown`. `EntryStatus.UNKNOWN` remains in the `:domain` enum as the value returned
for absence (and for export fidelity), but no `INSERT` ever writes it. This makes the pending state
in D3 cost zero storage and makes `unknown` the natural default rather than a materialised fact.

### 8.2 Identity and idempotency

`reminder_occurrences.id` is the notification ID, the `PendingIntent` request code, and the
`WorkManager` unique-work name suffix (`answer-<id>`, `snooze-<id>`). One integer, three uses, no
collision possible. Combined with the `UNIQUE(habitId, date, slotId)` upsert on `entries`, every
write path is idempotent: a redelivered broadcast, a retried worker, and a duplicated alarm all
converge on the same single row.

### 8.3 Additive migration path for numeric habits (spelled out)

Numeric habits are out of MVP scope and must never require a destructive migration. Version 2 is
**pure `ALTER TABLE ADD COLUMN`**, and `entries` is not touched at all:

```sql
-- Migration 1 -> 2, additive only
ALTER TABLE habits ADD COLUMN valueKind  TEXT NOT NULL DEFAULT 'BOOLEAN';
ALTER TABLE habits ADD COLUMN targetValue INTEGER;      -- e.g. 8 (glasses of water)
ALTER TABLE habits ADD COLUMN unitLabel   TEXT;         -- e.g. "glasses"
-- entries.value already exists as INTEGER NULL: no change, no backfill, no rewrite.
```

Existing rows read as `valueKind = 'BOOLEAN'` with `value = NULL`, so their meaning is bit-identical
before and after. No `DROP COLUMN`, no type narrowing, no data rewrite, no nullability tightening.

**Why reserve `entries.value` now instead of adding it in migration 2?** Adding a column later would
also be additive, so the reason is not the migration itself: it is that the **export format already
carries the field from day one** (§8.4). A backup taken by the MVP therefore imports into a
numeric-capable build with no format-version bump and no import-side translation. Reserving the
column is what buys that, and it costs one always-`NULL` integer per row.

Migration verification: `MigrationTestHelper` tests against the checked-in `app/schemas/1.json`, plus
a round-trip test that a v1 export imports cleanly into a v2 database. Per the proposal's rollback
plan, an automatic export file is written **before** any migration runs, so a failed migration
recovers by clearing app data and importing that file — which is precisely why export/import stays in
MVP scope (**OA-1**).

### 8.4 Export/import file format

```json
{
  "format": "constanza.backup",
  "formatVersion": 1,
  "schemaVersion": 1,
  "exportedAt": "2026-08-31T10:15:00Z",
  "exportedAtZone": "Europe/Madrid",
  "settings": { "defaultSnoozeMinutes": 20 },
  "habits": [
    {
      "id": 1,
      "name": "Meditate",
      "question": "Did you meditate today?",
      "colorArgb": -14575885,
      "notes": null,
      "archived": false,
      "archivedAt": null,
      "createdAt": "2026-01-01T08:00:00Z",
      "sortOrder": 0,
      "schedule": { "kind": "TIMES_PER_DAY", "weekStart": "MONDAY" },
      "slots": [ { "id": 10, "minuteOfDay": 480, "enabled": true } ],
      "entries": [
        { "date": "2026-08-30", "slotId": 10, "status": "COMPLETED",
          "value": null, "answeredAt": "2026-08-30T08:03:00Z", "source": "NOTIFICATION" }
      ]
    }
  ]
}
```

| Aspect | Decision and reason |
|---|---|
| Shape | Nested by habit, not flat tables. A human can read one habit's whole history in one place, and import becomes one transaction per habit. |
| `reminder_occurrences` | **Deliberately excluded.** Transient scheduling state, fully rebuildable from `schedules`. Importing it would arm alarms derived from another device's clock. |
| Settings | Included, because a backup that loses the user's snooze default is not a backup. **Corrected 2026-09-01 while building work unit 7:** the example above used to also show `settings.weekStart`, and nothing was behind it. `weekStart` is a per-schedule column (`ScheduleEntity.weekStart`, and `Schedule.weekStart` in `:domain`); there is no global week-start setting, no DataStore entry for one, and the ratified decisions give the MVP none. Each habit's own `schedule.weekStart` is authoritative, and a DTO field for the global one would have had nothing to read or write. |
| `value` | Present and `null` from day one — the point of §8.3. |
| Forward compatibility | Unknown fields are ignored on import. A `formatVersion` higher than supported **refuses the whole import** with a clear message rather than importing partially. |
| Import semantics (**OA-5**) | **Replace-all inside a single Room transaction**, behind an explicit confirmation. Merging would be conflict resolution, which ratified decision 6 forbids designing. IDs are reassigned on import and `slotId` references remapped through an in-memory old→new map. **Ratified by the user 2026-09-01** — this row previously called itself "the design's recommendation, not a ruling" and said the spec flagged merge-vs-replace as an open product question. Neither is true any more: `data-portability` states replace-all, the confirmation, and atomicity as **MUST**s, and OA-5 is settled (§1). |
| Post-import | Cancel all alarms, truncate `reminder_occurrences`, re-plan from scratch. |
| Validation order | Parse and validate the **entire** file before touching the database. A malformed backup must never leave the user with neither their old data nor the new. |

## 9. Sequence diagrams (required by `config.yaml` `rules.design`)

### 9.1 Reminder / alarm flow — plan, arm, fire, answer

```
Planner        AlarmScheduler      AlarmManager   ReminderReceiver   Notifier    Room
   │
   │ plan(horizon = now + 48h)
   │  ├─ for each habit: dueOn(schedule, date, progress, weekStart)
   │  └─ upsert reminder_occurrences(state=ARMED, scheduledDate=D,
   │                                resolveDeadline=scheduledAt+24h) ─────────────► │
   │──────────────►│
   │               │ canScheduleExactAlarms()            ← checked before EVERY call
   │               │──────────────────►│
   │               │◄── true ──────────│
   │               │ setExactAndAllowWhileIdle(
   │               │   RTC_WAKEUP, scheduledAtEpochMs,
   │               │   getBroadcast(reqCode = occ.id, FLAG_IMMUTABLE))
   │               │──────────────────►│
   │               │ (false → setWindow(RTC_WAKEUP, at, 10 min); occ.exact = 0)
   │
   ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  time passes  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·
   │
   │                                   │◄─ alarm fires ─│
   │                                   │ load occurrence(id) ──────────────────────► │
   │                                   │ re-evaluate dueOn(...)                      │
   │                                   │   Candidate && quota met
   │                                   │     → state = SUPPRESSED; write nothing; stop
   │                                   │ areNotificationsEnabled() && channel.importance > NONE
   │                                   │     → false: state = FIRED, notifiedAt = NULL, no post;
   │                                   │       Today screen remains the fallback; stop
   │                                   │       (notifiedAt records delivery, so a gated post
   │                                   │        leaves it null — §13.4 finding 1, task G.3)
   │                                   │──────────────────►│
   │                                   │                   │ post(id = occ.id,
   │                                   │                   │   actions Yes | No | Snooze,
   │                                   │                   │   each PendingIntent.getBroadcast(
   │                                   │                   │     reqCode = occ.id, FLAG_IMMUTABLE))
   │                                   │ state = FIRED, notifiedAt = now ──────────► │
   │◄─ arm next occurrence for this slot ─┘
```

Answering, with the ~10 s `onReceive()` budget respected:

```
User taps "Yes" in the drawer
   │
   ▼
ActionReceiver.onReceive()          exported = false; validates extras ONLY.
   │                                No Room access. No notification cancel.
   │  enqueueUniqueWork("answer-<occId>", KEEP,
   │      OneTimeWorkRequest<AnswerWorker>()
   │        .setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST))   ← quota-safe fallback
   ▼
AnswerWorker (expedited; at minSdk 31 this is an expedited JobScheduler job, NOT an FGS)
   │  Room @Transaction:
   │    occ = load(occId)
   │    upsert entries(habitId, date = occ.scheduledDate,   ← ORIGIN date, never now()
   │                   slotId  = occ.slotId,
   │                   status  = COMPLETED, source = NOTIFICATION)
   │    occ.state = RESOLVED
   │  AlarmScheduler.cancelSnooze(occ.id)      (no-op if none armed)
   │  NotificationManagerCompat.cancel(occ.id) ← cancelled by the WORKER, never the
   │                                             receiver: never show "done" before
   │                                             the write has landed
   └─ streak and compliance need no update: they are computed on read (D6)
```

### 9.2 Snooze flow across midnight — the no-flicker path

```
20:00  Day D    alarm fires → notification posted
                occ#42 { scheduledDate = D, state = FIRED, resolveDeadline = D+1 20:00 }

23:50  Day D    user taps "Snooze"
                ActionReceiver (validate only) → expedited SnoozeWorker
                  occ#42.state       = SNOOZED
                  occ#42.snoozeCount = 1                    ← unlimited, never capped
                  occ#42.snoozeUntil = 00:10 Day D+1        ← clamped to resolveDeadline
                  AlarmScheduler.armSnooze(occ#42, snoozeUntil)   (same reqCode = 42)
                  NotificationManagerCompat.cancel(42)

00:00  Day D+1  MidnightSweepWorker  (triggered by a midnight RTC_WAKEUP alarm,
                                      by ACTION_DATE_CHANGED, and by the hourly
                                      ReconcileWorker — three redundant triggers)
                for each unresolved occurrence with scheduledDate <= D:
                    dueOn(...) != Required                  → skip   (D8: no dated missed)
                    state == SNOOZED && snoozeUntil > now   → SKIP   ◄── THE RULE (D3)
                    otherwise                               → upsert entries(
                                                                 date = scheduledDate,
                                                                 MISSED, source = SWEEP)
                occ#42 is skipped. Nothing is written. Day D stays `unknown`:
                no flicker, no hidden state, no UI that contradicts the database.

00:10  Day D+1  snooze alarm fires → notification re-posted for occ#42
                (still scheduledDate = D)

00:12  Day D+1  user taps "Yes"
                AnswerWorker → entries(habitId, date = D, slot, COMPLETED)
                Day D is credited. The streak never broke, because it was never broken.
```

Abandonment branches — how an unlimited snooze still terminates:

```
(A) User dismisses the 00:10 notification without answering. No new alarm is armed.
    01:00+  ReconcileWorker (hourly):
              state == SNOOZED && snoozeUntil < now - grace(1h)
                → upsert entries(date = D, MISSED, source = SWEEP)
                → occ#42.state = ABANDONED
            Worst-case resolution lag after the last snooze: 4h (max interval) + 1h (grace).

(B) User keeps snoozing past the hard deadline.
    D+1 20:00  resolveDeadline reached, occurrence still unresolved:
                 AlarmScheduler.cancel(occ#42); notification dismissed
                 → upsert entries(date = D, MISSED, source = SWEEP); state = ABANDONED
            Snoozing itself is never blocked or counted against the user; only the
            calendar date's arithmetic is bounded (D3).

(C) The user answers after (A) or (B) — from a later notification, or in-app.
    The upsert transitions the SAME row MISSED -> COMPLETED on the origin date.
    This is the residual `missed -> completed` path the spec requires readers to
    tolerate. C is now the rare case rather than the nightly norm.
```

### 9.3 Reschedule triggers

All five converge on one idempotent entry point, `OccurrencePlanner.replanAll()`, which reads
`reminder_occurrences`, cancels what should not exist, and arms what should:

| Trigger | Receiver / caller | Note |
|---|---|---|
| `BOOT_COMPLETED` | `BootReceiver` | **Not** `LOCKED_BOOT_COMPLETED`: the Room database lives in credential-encrypted storage and is unreadable before first unlock. |
| `MY_PACKAGE_REPLACED` | `PackageReplacedReceiver` | Alarms do not survive an update. |
| `ACTION_TIMEZONE_CHANGED` | `TimeChangeReceiver` | Wall-clock `RTC_WAKEUP` targets must be recomputed. |
| `ACTION_DATE_CHANGED` / `ACTION_TIME_CHANGED` (`TIME_SET`) | `TimeChangeReceiver` | Also a redundant midnight-sweep trigger. |
| In-app schedule edit | `HabitRepository` (same transaction as the write) | Replan is part of the edit, not a follow-up. |
| `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` | `ExactAlarmPermissionReceiver` | Not one of the five, but mandatory: upgrades inexact alarms to exact **on grant**. It does **not** fire on revoke — the platform sends this broadcast on grant only, so nothing arrives to downgrade with. An earlier version of this row claimed it downgraded on revoke; that was wrong, and §13.4 finding 3 records the measurement. The revoke path is covered by `OccurrenceResolver.reconcile()`'s re-arm and the `onResume()` re-check instead (task G.5). |

DST edge cases are resolved deterministically by computing targets as
`ZonedDateTime.of(date, time, zone).toInstant()`: a spring-forward nonexistent local time shifts
forward by the gap, and a fall-back ambiguous time resolves to the earlier offset and fires once.
Both are `java.time`'s documented default resolution, and the `TIMEZONE_CHANGED`/`TIME_SET` replan
covers the transition itself.

### 9.4 The reboot-to-first-unlock blind window (task G.6)

Between a reboot and the device's first unlock, **this app has no armed alarms at all.** Measured on
the Pixel 10 (API 37) while discharging the delivery matrix, §13.4 finding 4:

| Observation | Value |
|---|---|
| `getprop sys.boot_completed` | `1` — the boot itself finished |
| `dumpsys user` | `Started users state: [0=RUNNING_LOCKED]` |
| `dumpsys window` | `Keyguard=true` |
| `run-as … ls /data/data/…` | `couldn't stat /data/user/0/…: No such file or directory` |
| pending alarms for the app's uid | **none** |

After the unlock, `RUNNING_UNLOCKED`, `BOOT_COMPLETED` was delivered, and eight alarms came back
`window=0 exactAllowReason=permission`.

Two platform facts make this window unavoidable given the storage decision above. `AlarmManager`
alarms do not survive a reboot, so every one of them must be re-armed from scratch; and the app
cannot re-arm them before it can read `reminder_occurrences`, which lives in credential-encrypted
storage and is therefore unreadable — the data directory is not even `stat`-able — until the user
unlocks. `BOOT_COMPLETED` is withheld until then, which is exactly why §9.3 selects it over
`LOCKED_BOOT_COMPLETED`.

**Its length is not bounded by anything the app controls.** A device that reboots for an OS update at
03:00 and is not picked up until 08:00 spends five hours with no reminder armed. This is not a rare
path: an unattended overnight reboot is the normal way system updates land.

**Reminders in that window are late, not lost**, and only because of the recovery task G.5 added: an
occurrence whose time falls inside it stays `ARMED` with no alarm behind it, and the first
`OccurrenceResolver.reconcile()` pass after unlock sees `state == ARMED && scheduledAt < now` and
fires it immediately. The `onResume()` re-check makes that instant if the user opens the app. Before
G.5 the same window leaned entirely on the hourly pass. This is the same "late, not lost" guarantee
§5.5 makes for every other delivery hazard, and it is stated here so nobody has to re-derive it.

**The escape hatch, deliberately not taken.** Closing the window would mean handling
`LOCKED_BOOT_COMPLETED` and keeping a minimal "which alarms should exist" projection in
**device-encrypted** storage, readable before first unlock. That buys back the window at the cost of
a second storage area holding habit-derived data outside the credential-encrypted boundary, plus a
standing obligation to keep it in sync with the real table. This design does not adopt it: the MVP's
guarantee is late-not-lost rather than never-late, and the schedule of a user's habits is exactly the
kind of data that should stay behind the lock. Recorded as an option rather than a plan, so a future
decision to revisit it starts from the tradeoff and not from scratch.

## 10. `:domain` contracts

```kotlin
// ---- model ----
data class Habit(
    val id: Long, val name: String, val question: String?, val colorArgb: Int,
    val notes: String?, val archived: Boolean, val archivedAt: LocalDate?,
    val createdAt: Instant, val sortOrder: Int,
)

sealed interface Schedule {
    val weekStart: DayOfWeek                                   // default MONDAY (D7)
    data class Daily(override val weekStart: DayOfWeek) : Schedule
    data class TimesPerDay(override val weekStart: DayOfWeek) : Schedule   // slots carry the times
    data class NTimesPerWeek(val times: Int, override val weekStart: DayOfWeek) : Schedule
    data class Weekly(val dayOfWeek: DayOfWeek, override val weekStart: DayOfWeek) : Schedule
    data class Monthly(val dayOfMonth: Int, override val weekStart: DayOfWeek) : Schedule
    data class EveryNDays(val n: Int, val anchor: LocalDate, override val weekStart: DayOfWeek) : Schedule
}

data class ReminderSlot(val id: Long, val habitId: Long, val minuteOfDay: Int, val enabled: Boolean)

enum class EntryStatus { COMPLETED, MISSED, SKIPPED, UNKNOWN }   // exactly four (decision 4)

data class Entry(
    val habitId: Long,
    val date: LocalDate,
    val slotId: Long?,                    // null = single-occurrence; `0` sentinel is :app-only
    val status: EntryStatus,
    val value: Int? = null,               // RESERVED for numeric habits (§8.3)
    val answeredAt: Instant? = null,
)

// ---- the two mandated pure functions ----

/** Mandated function 1: the single authority for "is this due?", used by arming AND by scoring. */
fun dueOn(schedule: Schedule, date: LocalDate, progress: PeriodProgress): Due

/** Mandated function 2: collapses a multi-slot day to one status. Unblocks digest + printable grid. */
fun rollupDay(schedule: Schedule, date: LocalDate, slots: List<ReminderSlot>, entries: List<Entry>): DayStatus

data class PeriodProgress(val completedInWeek: Int, val completedInMonth: Int)
enum class DayStatus { ALL_COMPLETED, PARTIAL, ANY_MISSED, ALL_SKIPPED, NOT_DUE, PENDING }

// ---- scoring (compute-on-read, D6) ----

/** SKIPPED passes through. UNKNOWN passes through (a snoozed-pending day does not break a
 *  streak, D3). Only MISSED breaks. Walks OCCURRENCES, not calendar days, so MONTHLY and
 *  EVERY_N_DAYS work without special cases. For NTimesPerWeek the unit is the week (D8). */
object StreakCalculator { fun current(schedule: Schedule, entries: List<Entry>, today: LocalDate): Int
                          fun best(schedule: Schedule, entries: List<Entry>, today: LocalDate): Int }

/** completed / (completed + missed). SKIPPED and UNKNOWN excluded from BOTH sides.
 *  `windowDays` is a caller parameter; the MVP UI passes 30 (OA-4).
 *  For NTimesPerWeek the unit is the week (D8), exactly as for StreakCalculator: the ratio is
 *  sum over whole weeks in the window of min(completedInWeek, n), divided by sum of n over those
 *  same weeks. Summing before dividing, rather than averaging per-week ratios, keeps every week
 *  equally weighted and avoids a short partial week distorting the result. Partial weeks at the
 *  window edge are excluded, because a week that has not finished cannot have fallen short yet. */
object ComplianceCalculator { fun ratio(schedule: Schedule, entries: List<Entry>,
                                        today: LocalDate, windowDays: Int): Double }
```

`Instant`, `LocalDate`, and `DayOfWeek` cross the boundary; nothing else does. No function reads the
clock — `today` and `answeredAt` are always parameters (§4).

## 11. Error and degradation paths

| Condition | Detection | Behaviour |
|---|---|---|
| Exact alarm not permitted at schedule time | `canScheduleExactAlarms()` before **every** scheduling call, and again in `onResume()` | `setWindow(RTC_WAKEUP, target, 10 min)`; `occ.exact = 0`; a non-blocking banner explains reminders may arrive late, with one tap to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Nothing crashes, nothing is lost. |
| Exact-alarm permission revoked while running | `OccurrenceResolver.reconcile()`'s re-arm, and the `onResume()` re-check (task G.5) | The platform cancels every alarm and stops the process, and sends the state-changed broadcast **on grant only** — so no receiver fires here. Recovery re-arms every future `ARMED` occurrence in inexact mode from persisted state. This row previously credited that receiver, which never sees a revoke (§13.4 finding 3). |
| Exact-alarm permission granted later | same receiver | `replanAll()` upgrades armed inexact alarms to exact. |
| `POST_NOTIFICATIONS` denied (API 33+) | `areNotificationsEnabled()` | No post, and `notifiedAtEpochMs` stays **null** — the occurrence records `FIRED` without claiming a delivery (§13.4 finding 1, task G.3). Occurrence stays unresolved — **never** written `missed`, because a suppressed notification is the app's failure, not the user's. Today screen is a complete fallback. Requested contextually after the first habit with a reminder; after two denials the system blocks the dialog, so the only path offered is a deep link to system settings. |
| API 31–32 | `SDK_INT < 33` | The runtime request is **skipped entirely** — the permission does not exist and notifications are implicitly granted. Both branches converge on the enabled/importance check, which is what still matters on 31–32 because the user can mute the channel. (Mandatory consequence 3.) |
| Channel muted / `IMPORTANCE_NONE` | `getNotificationChannel(id).importance` | Treated exactly as denied — same single `canPost()` gate, so `notifiedAtEpochMs` stays null here too — with a deep link to the channel settings. |
| Doze, App Standby, OEM throttling (Samsung One UI sleep, Xiaomi MIUI) | `ReconcileWorker` finds `state = ARMED AND scheduledAt < now - tolerance` | Fire late rather than lose it; increment a late-delivery counter. Only after repeated detected misses does Settings surface per-OEM guidance. No unsolicited `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` on first launch — poor UX and Play-policy discouraged. |
| Expedited work quota exhausted | `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST` | The answer write still lands, just not instantly. The notification is cancelled by the worker, so the drawer keeps showing the reminder until the write is durable. |
| Answer arrives twice (redelivery, double tap) | `enqueueUniqueWork(..., KEEP)` + `UNIQUE(habitId, date, slotId)` upsert | Idempotent; the second attempt is a no-op. |
| Worker crashes mid-write | Room `@Transaction` | All-or-nothing; the occurrence stays unresolved and the sweep or a later answer resolves it. |
| Boot before first unlock | `BOOT_COMPLETED`, not `LOCKED_BOOT_COMPLETED` | Credential-encrypted storage is unreadable before unlock; re-arming waits for it. |
| Malformed or newer-version backup file | Full parse and validate before any write | Refuse the whole import with a clear message; the existing database is untouched. |
| Room migration fails | Automatic pre-migration export (§8.3) | Recovery is clear-data plus import. `fallbackToDestructiveMigration()` never ships. |

## 12. Trust boundaries

The `sdd-design` threat matrix (routing, shell commands, subprocesses, VCS/PR automation,
executable-file classification, process integration) is **N/A**: this change spawns no process, runs
no shell, does no VCS automation, and classifies no executable content. Two Android-specific trust
boundaries do exist and are handled explicitly:

| Boundary | Risk | Control |
|---|---|---|
| Inbound intents to `ActionReceiver`, `BootReceiver`, `TimeChangeReceiver` | A third-party app forging a Yes/No/Snooze intent could write false entries | Every receiver is `android:exported="false"` except those that must receive system broadcasts; `android:exported` is stated explicitly on every component (mandatory at API 31+). The receiver validates that the extras name an existing, unresolved occurrence before enqueuing anything, and system-broadcast receivers verify `intent.action` against an allow-list. |
| `PendingIntent` handed to the notification | A mutable `PendingIntent` lets the holder rewrite the intent | `FLAG_IMMUTABLE` on every `PendingIntent` (already mandatory at API 31+), plus `FLAG_UPDATE_CURRENT`, with `reqCode = occurrence.id`. |
| Imported backup file | User-supplied JSON reaching the database | Full parse and schema validation before any write; whole-file rejection on any failure; ID remapping so a crafted file cannot collide with existing rows; import runs in one transaction. |
| Exported backup file | Contains all habit history in cleartext | Written only to a user-chosen SAF location. No implicit external-storage write, no automatic upload, no network permission requested at all. |

The app requests **no** `INTERNET` permission. That is worth stating as a design constraint rather
than an accident: ratified decision 6 makes storage local forever, so declaring `INTERNET` would be
the first thing to challenge in review.

## 13. Testing strategy

### 13.1 Per module

| Layer | Module | What is tested | Framework | Command |
|---|---|---|---|---|
| Unit, pure | `:domain` | `dueOn` for all six kinds incl. month-length clamping and `EveryNDays` anchor arithmetic; `rollupDay`; streak with `SKIPPED`/`UNKNOWN` pass-through and `MISSED` break; compliance exclusions; weekly-unit scoring for `NTimesPerWeek`; DST-adjacent date arithmetic | JUnit4 + `kotlin.test` | `./gradlew :domain:test` |
| Unit, JVM | `:app` | Mappers (`0 ↔ null` slot sentinel), export/import DTO round-trip, occurrence planning arithmetic, permission-branch decision logic | JUnit4 + MockK | `./gradlew :app:testDebugUnitTest` |
| Instrumented, data | `:app` | Room DAOs, the `UNIQUE(habitId, date, slotId)` constraint actually rejecting duplicates for `slotId = 0`, and `MigrationTestHelper` 1→2 additive migration against `app/schemas/1.json` | AndroidX Test | `./gradlew :app:connectedDebugAndroidTest` |
| Instrumented, workers | `:app` | `AnswerWorker`, `SnoozeWorker`, `MidnightSweepWorker`, `ReconcileWorker` via `TestListenableWorkerBuilder` and `WorkManagerTestInitHelper`; **the D3 rule gets a dedicated test: a live snooze at midnight must leave no `entries` row** | `androidx.work:work-testing` | same |
| Instrumented, UI | `:app` | Creating each of the six kinds; answering per-slot; settings snooze options | `compose-ui-test-junit4` | same |
| Aggregate | both | — | — | `./gradlew check` |

### 13.2 `strict_tdd` gate

`strict_tdd` stays `false` and is **not** changed by this phase: no build system exists, so no
command above has ever run. The flip condition, recorded in `config.yaml` as
`testing.design_intent.strict_tdd_gate`: once `./gradlew :domain:test` runs green in this repository,
enable strict TDD **scoped to `:domain`** — that module is pure, fast, dependency-free, and holds the
logic where a silent error is most expensive. `:app` stays non-strict while its instrumented suite is
still unverified.

### 13.3 Manual delivery matrix (not automatable — the remaining half of the §5.4 gate)

> **Status**: the documentation half of the §5.4 gate is **discharged** (see §5.7). This matrix is the
> half that remains, and it can only run once the reminder pipeline and notification handling exist —
> i.e. after work units 4a, 4b and 5 are merged, not in work unit 1.

#### Device matrix — three devices, and none substitutes for another

| Device | OS | Proves | Cannot prove |
|---|---|---|---|
| **Pixel 10** (the project's Android 17 test device) | Android 17 **QPR beta**: `sdk_full 37.1`, `preview_sdk 3723`, `codename DEV`, `user`/`release-keys` | AOSP truth — exact alarms, notification actions, Doze, expedited jobs, the five reschedule broadcasts, and every API-37-**gated** behaviour change | Anything OEM-specific. Stock Android never reproduces One UI or MIUI throttling |
| **Galaxy Z Fold 7** | Latest Samsung **stable** | One UI "Put unused apps to sleep"; a genuine `sw >= 600dp` screen; fold/unfold as a real configuration change | API 37 gated behaviour, **if** it ships on Android 16 |
| **Galaxy S25** (owner's daily driver) | Samsung stable | Final confirmation on the device the app will actually live on | — use sparingly, at the owner's request |

**Every finding recorded from this matrix MUST carry the device fingerprint.** The Pixel is a preview
build (`preview_sdk 3723`, `codename DEV`), so a failure seen there may be beta-only and a pass is a
strong signal rather than a stable guarantee. QPR betas update monthly; a later re-run may not
reproduce an earlier result. The Pixel's fingerprint at the time of writing:
`google/frankel_beta/frankel:DEV/CP41.260731.005.B1/16056512:user/release-keys`.

**Large-screen testing without an Android 17 foldable.** The C1 large-screen change (§5.7) is gated on
the app targeting 37 **and** running on Android 17+. The only Android 17 device available is a
regular-width phone: the Pixel 10 measures `1080x2424` at density `420`, i.e. **sw ≈ 411dp**, below the
600dp threshold. Override it — `wm size` accepts dp units directly on this device:

```
adb shell wm size 800dpx1280dp   # forces sw >= 600dp, triggering the gated large-screen behaviour
adb shell wm size reset          # ALWAYS reset afterwards
# density alternative: 1080px / (280/160) = 617dp
adb shell wm density 280 ; adb shell wm density reset
```

If the Z Fold 7 ships on Android 17 stable it covers this natively and the override becomes a
convenience. If it ships on Android 16, the gated behaviour is observable only via this override. No
design change results either way, because the UI is built adaptive-resilient regardless of any opt-out.

**Recipes verified as available on the connected Pixel 10 on 2026-08-31**: `dumpsys deviceidle`,
`cmd appops get/set <pkg> SCHEDULE_EXACT_ALARM`, `am get-standby-bucket <pkg>`, `cmd jobscheduler run -f`,
`wm size` / `wm density` with dp units.

#### Scenarios


| Scenario | Recipe |
|---|---|
| Exact alarm revoked | `adb shell cmd appops set <pkg> SCHEDULE_EXACT_ALARM deny` then observe an inexact window |
| Armed alarms inspection | `adb shell dumpsys alarm \| rg <pkg>` |
| Doze | `adb shell dumpsys deviceidle force-idle`, then confirm the reconcile net fires late |
| Deferred expedited work | `adb shell cmd jobscheduler run -f <pkg> <jobId>`; on API 37 also read `JobScheduler.getPendingJobReasonStats` (V9) |
| Timezone change | `adb shell setprop persist.sys.timezone Europe/Madrid`; `adb shell am broadcast -a android.intent.action.TIMEZONE_CHANGED` |
| Notifications denied | `adb shell pm revoke <pkg> android.permission.POST_NOTIFICATIONS` |
| Reboot re-arm | `adb reboot`, unlock, confirm alarms re-armed after `BOOT_COMPLETED` |
| Snooze across midnight | Set device time to 23:50, snooze 20 min, confirm **no** `missed` row is written at 00:00 and the answer credits the previous day |
| Reconcile net without waiting an hour | `adb shell cmd jobscheduler run -f <pkg> <jobId>` force-runs the periodic `ReconcileWorker` immediately. **Without this, each iteration of testing the reminder-recovery net costs an hour of waiting.** Use it for every reconcile assertion |
| Large screen — C1 (§5.7) | `adb shell wm size 800dpx1280dp`, then confirm no layout breaks, clips, overlaps, or loses content on the today screen and the habit editor. Then `wm size reset`. Repeat natively on the Z Fold 7, unfolded |
| Orientation — C1 (§5.7) | Rotate on both devices at both widths; confirm the same. Required because `targetSdk = 37` removes the opt-out for orientation constraints on large screens |
| Soft keyboard after a config change — C4 (§5.7) | On the habit editor with the keyboard open, rotate (and on the Z Fold 7, fold/unfold). Android 17 does **not** restore IME visibility after an unhandled configuration change; confirm the screen does not assume it survives |
| OEM throttling survival | On the **Z Fold 7 and only there**: enable One UI's "Put unused apps to sleep" for the app, leave it idle for a multi-day window, and confirm reminders still arrive — late is acceptable, lost is not. This is the assertion the WorkManager missed-reminder sweep exists to satisfy, and no Pixel can make it |

### 13.4 Discharge record — manual delivery matrix run 2026-08-31/09-01 (task G.1)

Run on the Pixel 10 (`55221FDCR005RD`) against `main` at `d0adc17`, i.e. work units 1–5 merged.

**The device is no longer a preview build, and §13.3's caveat about that no longer applies.** §13.3
records `google/frankel_beta/frankel:DEV/CP41.260731.005.B1/16056512:user/release-keys` with
`preview_sdk 3723`, `codename DEV`. Measured at run time:

| Property | §13.3 records | Measured |
|---|---|---|
| fingerprint | `…frankel:DEV/CP41.260731.005.B1/16056512…` | `google/frankel_beta/frankel:17/CP41.260814.003.B1/16166531:user/release-keys` |
| `preview_sdk` | `3723` | `0` |
| `codename` | `DEV` | `REL` |

So these results come from a **released** Android 17, not a beta. §13.3's hedge — "a pass is a strong
signal rather than a stable guarantee" — is retired for this run.

#### Prerequisite the matrix did not anticipate: nothing can create a habit yet

`MainActivity` renders an empty `Box`, habit CRUD is work unit 6a, and the app's database did not
exist on the device. Every scenario needs a habit with an armed reminder, so **§13.3 as sequenced
cannot be run from the product surface until 6a ships**. Discharged instead with three `@SeedOnly`
fixtures in `app/src/androidTest/.../seed/`, excluded from ordinary verification by
`testInstrumentationRunnerArguments["notAnnotation"]` and run through `adb shell am instrument`
(never `connectedDebugAndroidTest`, which uninstalls both APKs and destroys the seeded data):
`ImminentReminderSeed`, `LiveSnoozeAcrossMidnightSeed`, and the read-only `DatabaseStateReport`.
Alarms are armed by the real `OccurrencePlanner.replanAll()` driving the real `AlarmScheduler`.

#### Results

| Scenario | Result | Evidence |
|---|---|---|
| Armed alarms inspection | **PASS** | Granted: `window=0 exactAllowReason=permission flags=0x5` (`setExactAndAllowWhileIdle`) |
| Exact alarm revoked | **PASS** | Denied: same occurrences re-armed `window=+10m0s0ms flags=0x0` |
| Doze | **PASS** | With `mState=IDLE`, the alarm fired, `ReminderFireWorker` ran, notification posted |
| Timezone change | **PASS** | Future occurrences shifted exactly +1h to hold local 23:23; the already-`FIRED` one kept its instant |
| Notifications denied | **PASS**, one finding | Alarm fired, occurrence `FIRED`, **no** notification posted, **no** false `MISSED` |
| Reboot re-arm | **PASS** | After unlock, 8 alarms re-armed `window=0 exactAllowReason=permission` |
| Snooze across midnight | **PASS** | See below — both halves, across real midnight |
| Deferred expedited work | **recipe invalid** | See "Recipes that do not work" |

**Delivery proved end to end on real hardware.** A posted reminder:
`NotificationRecord(pkg=com.jjrapps.constanza id=21 importance=4 channel=reminders contentView=null
actions=3 color=0xff00897b vis=PRIVATE)`. `id` equals `occurrence.id`, confirming §8.2's shared
request code; `contentView=null` confirms the standard template §5.7 C2 requires.

**Snooze across midnight — D3's rule, both directions in one sweep pass.** A live snooze
(`scheduledDate=2026-08-31`, `state=SNOOZED`, `snoozeUntil=2026-09-01T00:20+02:00`) crossed real
midnight and was still `SNOOZED` with no entry at 00:04, 00:06, 00:14 and 00:15. When the sweep
finally ran, the same pass wrote `ENTRY date=2026-08-31 status=MISSED source=SWEEP` for a different
occurrence that was genuinely unanswered — **on the origin date, not on today** — and left the live
snooze untouched. This is the strongest available confirmation of `OccurrenceResolver`'s one rule.

#### Recipes in §13.3 that do not work on a user build — replace them

Four of §13.3's recipes are unusable as written. Three of them fail for the same reason the manifest
comment beside `BootReceiver` already states: *"only the system UID may send them regardless of this
flag."* §13.3 prescribes sending them from `adb` anyway.

| §13.3 recipe | What actually happens | Use instead |
|---|---|---|
| `cmd jobscheduler run -f <pkg> <jobId>` to force the periodic `ReconcileWorker` | Refused: `WM-WorkerWrapper: Delaying execution … because it is being executed before schedule`. The job id also churns between attempts | No adb path found. Drive `OccurrenceResolver` from an instrumented test, or wait out the period |
| `setprop persist.sys.timezone <zone>` | `Failed to set property`; `suggest_manual_time_zone` then fails with `SecurityException: … does not have android.permission.SUGGEST_MANUAL_TIME_AND_ZONE` | `adb shell cmd time_zone_detector set_time_zone_state_for_tests --zone_id <Olson> --user_should_confirm_id false` — **verified working** |
| `am broadcast -a android.intent.action.TIMEZONE_CHANGED` / `DATE_CHANGED` | Protected broadcasts; the shell cannot send them. `am broadcast` reporting `result=0` means *sent*, not *delivered* | Change the zone with the command above and let the system broadcast; there is no adb path for `DATE_CHANGED` |
| `am broadcast` to drive a notification action | `ActionReceiver` is correctly `exported="false"`, so a shell broadcast is never delivered | Fire the notification's real `PendingIntent` from an instrumented test, as `NotificationActionWiringInstrumentedTest` does |

Also correct two id-reading traps: real WorkManager job ids come from
`dumpsys jobscheduler`'s `JOB androidx.work.systemjobscheduler:<uid>/<id>` lines, not the `#<uid>/<id>`
lines, which are history; and `cmd jobscheduler` needs `-n androidx.work.systemjobscheduler`. Live
alarms are `Pending alarms per uid: [… <uid>:N]`, and live notifications are
`NotificationRecord(0x…: pkg=<pkg> …)`. A match count over `dumpsys` output is not a presence check:
`numPostedByApp` is cumulative across test runs, and it produced two false readings during this run.

#### Findings — none contradicts §5.5, all four are worth acting on

1. **`notifiedAtEpochMs` is recorded for a notification that never reached the user.** With
   `POST_NOTIFICATIONS` denied the post is correctly suppressed and no false `MISSED` is written, but
   the occurrence still stores `notifiedAt` (measured: `notifiedAt=2026-08-31T23:36:01.461`). Any
   later reader that treats that field as "the user was told" would be wrong. PR #14 logged this as
   cosmetic; on-device it is a data-integrity claim.

   **Resolved 2026-09-01 (task G.3) by not writing the field on the suppressed branch.** The
   alternative — renaming the column to describe what it actually recorded — was rejected: the name
   was already correct and only the fire path disagreed with it, and a rename would have forced a
   schema version bump, a migration and a re-exported schema JSON to enshrine wrong behaviour.
   `NotificationPoster.postReminder` now reports whether it posted, and `ReminderFireHandler.fire`
   sets `notifiedAtEpochMs` only when it did. `canPost`'s own contract already promised "no post,
   never a silent lie about delivery"; the gate had held up its end and the caller then recorded
   exactly that lie. **No new state and no migration:** the occurrence still lands on `FIRED`, never
   `SUPPRESSED` (D8's terminal quota exit, excluded by `findUnresolved()`), so it stays unresolved
   for the reconcile net and the Today screen and never becomes a false `MISSED` (§5.5). A null
   `notifiedAtEpochMs` on an already-nullable column is the whole distinction between "fired but not
   notified" and "fired and notified".
2. **`WorkScheduler.scheduleAll()` re-anchors the midnight sweep on every `Application.onCreate`.**
   `ExistingPeriodicWorkPolicy.UPDATE` with `setInitialDelay(millisUntilNextMidnight())` recomputes
   the delay each cold start. Measured directly: after a process start at 00:04:55 the midnight
   sweep's next run read `Delay=+23h29m59s` — it had skipped the 00:00 boundary entirely. This also
   made the gate hard to measure: reading state started the process, which reprogrammed what was
   being measured.

   **Corrected 2026-09-01 while discharging task G.4.** As first written, this finding said *both*
   periodic workers were re-anchored, and that a user opening the app often enough could postpone the
   hourly reconcile indefinitely. That is wrong, and the wording is corrected rather than removed so
   the record shows what was actually measured. Neither the `ExistingPeriodicWorkPolicy` reference
   page nor the "update work" guide states what `UPDATE` does to an existing schedule's timing, so it
   was probed on the device against this project's WorkManager 2.11.2
   (`app/src/androidTest/kotlin/com/jjrapps/constanza/seed/PeriodicAnchorProbe.kt`, reading
   `WorkInfo.nextScheduleTimeMillis` before and after each re-enqueue):

   | Re-enqueue under a name that already exists | Next run time |
   |---|---|
   | `UPDATE`, same initial delay | unchanged |
   | `UPDATE`, initial delay 30m → 50m | shifted by exactly the delta: `+1200000ms`, `08:52:17.615` → `09:12:17.615` (identical milliseconds on both sides) |
   | `KEEP` | unchanged |

   So **`UPDATE` applies the new initial delay to the ORIGINAL enqueue instant, not to now** — a
   semantic worth recording here because it is in neither doc page. `ReconcileWorker` is therefore
   *not* affected: it is enqueued with no initial delay and an unchanging request shape, so every
   repeated `UPDATE` from a cold start leaves its anchor exactly where it was, and `UPDATE` remains
   what lets a tuned `ReconcilePeriodHours` reach an existing install. The sweep was the whole defect:
   its delay is recomputed on every launch, and `UPDATE` then applied that fresh delay to the
   first-ever enqueue instant, producing an anchor of `firstEnqueueTime + millisUntilNextMidnight(now)`
   — not midnight, and drifting arbitrarily.

   **Fix (task G.4).** The sweep is no longer periodic. It is unique **one-time** work delayed to the
   next local midnight under `ExistingWorkPolicy.KEEP`, so a cold start cannot move one that is
   already pending, and `MidnightSweepWorker` enqueues its own successor for the following midnight at
   the end of a successful run. Recomputing the anchor per run is what makes it self-correcting, and
   it absorbs the 23h and 25h local days a fixed 24h period cannot express. `ReconcileWorker`'s
   scheduling is unchanged. The chain is not a single point of failure, which is the only reason a
   self-rescheduling worker is acceptable here: §9.2's other two triggers stand behind it —
   `ReconcileWorker` sweeps on every hourly pass and `TimeChangeReceiver` sweeps on
   `ACTION_DATE_CHANGED` — and a cold start re-enqueues the chain, because `KEEP` defers only to a
   sweep that is still pending.
3. **After an exact-alarm revoke nothing re-arms until each occurrence's own time passes.** The
   platform cancels every alarm and stops the app (`Killing …: schedule_exact_alarm revoked`), and the
   guide confirms the state-changed broadcast fires on **grant only** — so `ExactAlarmPermissionReceiver`
   not running on revoke is correct. But `scheduleAll()` never calls `replanAll()`, and
   `reconcile()` re-arms only `STATE_ARMED && scheduledAt < now`. Opening the app does not re-arm:
   verified twice with zero pending alarms and both periodic jobs healthy. Delivery is "late, not
   lost" as §5.5 promises, with the whole burden on the hourly net.

   **Resolved 2026-09-01 while discharging task G.5: re-plan, do not wait out the net.** This was
   never an open design question — §5.5 already promised "`canScheduleExactAlarms()` before **every**
   scheduling call, plus the state-changed receiver, plus an `onResume()` re-check", already promised
   that "recovery after any platform-initiated cancellation is a query, not a guess", and §13.1's
   failure table already named the same `onResume()` re-check. What was missing was the code.

   Two changes, both reusing machinery that already existed rather than adding a path:

   - `OccurrenceResolver.reconcile()` gained the future case: an `ARMED` occurrence whose
     `scheduledAt` has **not** passed is re-armed at its own instant. Until this, "which alarms
     should exist is always recomputable from the database" was true of the schema and false of the
     code — only past-due occurrences were recovered. `AlarmScheduler.schedule()` re-checks
     `canScheduleExactAlarms()` on every call, so the same branch recovers correctly whether or not
     the permission has come back, and the re-arm now persists the mode it actually got instead of
     leaving the row claiming `exact = 1` for an inexact window (finding 1's class of stale claim).
   - `MainActivity` registers `ReplanOnResumeObserver`, which sends `ON_RESUME` through
     `OccurrencePlanner.replanAll()` — the same idempotent entry point all five §9.3 triggers use.
     `ON_RESUME` rather than `ON_START`/`ON_CREATE` because returning from the system's exact-alarm
     settings screen resumes the Activity without recreating it. No permission branch is written
     here, exactly as `ExactAlarmPermissionReceiver` writes none.

   **The re-arm condition is deliberately narrow: `state == ARMED` AND future.** `findUnresolved()`
   also returns `FIRED` and `SNOOZED`, and §8.2's `PendingIntent` request code is `occurrence.id`,
   shared by an occurrence's own alarm and its snooze alarm. Re-arming a `SNOOZED` row at its
   original `scheduledAt` would therefore overwrite the pending snooze alarm and fire the reminder at
   the wrong time; a `FIRED` row already reached the user and is waiting for an answer. Both
   exclusions are pinned by tests, not by comments
   (`aLiveSnoozeIsNeverReArmedAndItsSnoozeAlarmIsLeftIntact`,
   `aFiredOccurrenceAwaitingAnAnswerIsNotReArmed`), and the defect itself is pinned by
   `aFutureArmedOccurrenceIsReArmedAtItsOriginalInstant`, which fails against the previous
   `reconcile()` (`schedule(eq(1), eq(1788321600000)) was not called`) and passes after it.

   **§13.1's banner stays deferred to work unit 6b** — the non-blocking "reminders may arrive late"
   message with one tap to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` is UI, and `MainActivity` still
   renders nothing. It is named in that class's KDoc so the deferral is visible in the code, not only
   here.

   **Worth naming as a pattern, because this is now three for three.** §5.5's `onResume()` re-check
   was promised in two sections and owned by no numbered task, so nothing ever built it. That is the
   same class of gap as §9.1's fire-time wiring, which the design assigned to work unit 5 while no
   task owned it (added later as task 5.9, after slice i shipped a notification poster nothing
   called), and as `ActionReceiver` being implemented but never declared in the manifest (commit
   `b78075b`, PR #14). All three were silent: the build was green, every task was ticked, and a
   promise in prose had no owner. Any future task list for this change should be checked against
   §5.5, §9.1 and §13.1 promise by promise, not against its own numbering.
4. **Between a reboot and the first unlock there are zero armed alarms.** `AlarmManager` alarms do not
   survive a reboot and `BootReceiver` waits for `BOOT_COMPLETED`, which the platform withholds until
   the user unlocks (`Started users state: [0=RUNNING_LOCKED]`, and the app's data directory is not
   even stattable). This is the correct consequence of §9.3, not a defect, but it is a delivery
   window the design never names.

Two further observations, both weaker: WorkManager can hold periodic work `ENQUEUED` with
`Job Id = null` — a dead safety net nothing detects, repaired only by a cold start, reachable here
only via a developer-only forced run, so not demonstrably user-reachable; and one
`Worker result FAILURE` was seen after reboot but the logcat buffer rotated before it could be
attributed, so it is recorded as unattributed rather than assigned to this app.

#### Addendum 2026-09-01 — unowned-promise pattern, ninth instance, and the first found by a
full-change verification rather than by an implementer

`habit-management`'s Habit Archiving requirement promises an archived habit "MUST be excluded from
streak and compliance calculations for any date on or after the archive date." `:domain`'s
`StreakCalculator`/`ComplianceCalculator` correctly take no archive parameter (they stay pure
functions of schedule and entries, exactly as designed) — but no task ever assigned the archive
boundary to the one layer that could enforce it, `ProgressViewModel`. `HabitRepository.setArchived`
wires the reminder half correctly (`replanAll()` cancels every armed occurrence); the calculation
half had no expression anywhere. The behaviour read correct by accident: `dailyRatio` only counts
`COMPLETED`/`MISSED`, so a post-archive day with no entry drops out of the denominator on its own,
and archiving stops the midnight sweep from ever writing one. The boundary genuinely leaked,
though — an entry dated on or after `archivedAt` arriving from another source (an import, or an
answer given the same day just before archiving) counted anyway, and an `N_TIMES_PER_WEEK`
schedule's current streak would have silently zeroed rather than frozen, since an entry-less week
after archiving reads as a missed quota, not a neutral gap.

Same class as all eight prior instances: a normative promise with no numbered task owner, invisible
because every existing test happened to exercise only the paths where the accidental correctness
held. **Different in one respect worth recording**: the first eight were each found either as a
production defect or by an implementer flagging a gap mid-task; this one was caught by a
full-change `sdd-verify` pass reading the requirement against the code, with nothing yet broken in
production. That is evidence the verification step earns its cost, not just process overhead.

**Fixed** in `fix/archived-habit-progress-exclusion`: `ProgressViewModel.buildState` now filters
entries dated on or after `archivedAt` before either calculator sees them, and clamps `today` to
`archivedAt.minusDays(1)` so the compliance window and the streak walk's upper bound cannot extend
past the archive date either — freezing current streak at its value the moment the habit was
archived, leaving best streak numerically unaffected (no entry exists after that date to extend
it), and keeping the compliance window anchored to the pre-archive history rather than sliding past
it. `:domain` is untouched. Covered by `ProgressViewModelTest.kt`, including the spec's own
scenario pinned near-verbatim and the on/after-boundary case the spec's "on or after" wording makes
explicit.

#### Deferred, with reasons

- **Large screen (C1), orientation (C1), soft keyboard (C4)** — all three need the today screen and
  the habit editor, which are work units 6b and 6a. Not runnable now, and the `wm size 800dpx1280dp`
  override is untested for the same reason.
- **OEM throttling survival** — Z Fold 7 only, over a multi-day idle window. No Pixel can make this
  assertion, exactly as §13.3 says.
- **Natural-timing observation of the hourly net** — the periodic workers never ran on their own
  inside the observation window. Finding 2 explains why every attempt to observe the sweep moved it;
  the reconcile worker's own anchor, as that finding's correction records, was never moved at all.

### 13.5 Discharge record — the UI rows of the delivery matrix (task G.7)

Run 2026-09-01 on the Pixel 10 (`55221FDCR005RD`, released Android 17, API 37) against `main` with
work units 1–7 merged. **The Pixel-reachable rows are discharged; the Z Fold 7 rows are not, and
cannot be from this device.** Every override was reset afterwards and the reset verified.

| §13.3 row | Result |
|---|---|
| Large screen — C1, today screen | **PASS** at `wm size 800dpx1280dp`. Title, both top-bar actions, banner and habit row all render with no clipping or overlap. |
| Large screen — C1, habit editor | **PASS** at the same override. Name, question, notes, the six colour swatches, the frequency dropdown, the "Remind me" toggle with its hour/minute fields, and Save all render intact. |
| Orientation — C1 | **PASS** at both widths, portrait and landscape, with no clipping or overlap. |
| Soft keyboard after a config change — C4 | **Content PASSES, IME re-request FAILS.** See finding 2. |
| Z Fold 7: native large screen, fold/unfold | **PASS — run 2026-09-01 on the real device.** See §13.6. |
| Z Fold 7: OEM throttling survival | **NOT RUN** — the device is now available, but this row also needs a multi-day idle window. |

#### Finding 1 — the exact-alarm banner's action was off-screen, and the banner looked fine

`ExactAlarmBanner`'s `Row` used `SpaceBetween` with an unconstrained `Text`, so the text claimed the
full width and pushed the `TextButton` past the right edge. The banner rendered perfectly; the **one
tap to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` that §13.1 promises was simply unreachable** — on a
1080px-wide phone, the ordinary case.

Fixed here with `Modifier.weight(1f)` on the text, proven by before-and-after screenshots: the "Fix"
action is absent in the first and present in the second.

Worth recording *why the automated suite missed it*. Task 6b.8's `sw = 600dp` test asserts the habit
rows do not clip; it says nothing about the banner, and no test asserts that the banner's action is
visible at all. A promised affordance that renders off-screen fails no assertion — which is precisely
the class of defect a manual matrix exists to catch, and the second time in this change that a
promised tap turned out to be unreachable (the first was the show-archived row's label in unit 6a).

#### Finding 2 — the IME is not restored after rotation, though the code tries to

Measured on the habit editor with text typed into the name field and the keyboard up:

| Observation | Before rotation | After rotation |
|---|---|---|
| `dumpsys input_method` `mInputShown` | `true` | **`false`** |
| Typed content | `MeditateXYZ` | **`MeditateXYZ`** — preserved |
| Any field focused | yes | **none** |

So **C4's content half holds** — task 6a.7's no-content-loss guarantee survives a real device
rotation, not only `StateRestorationTester` — but task 6a.5's "re-request IME visibility explicitly
after rotation" does not happen.

`EditorNameField` intends to: it keeps `wasFocused` in `rememberSaveable` and, in a
`LaunchedEffect(Unit)`, calls `focusRequester.requestFocus()` and `keyboardController?.show()` when
that flag is set. The evidence says the flag is already `false` by then, because
`onFocusChanged { wasFocused = it.isFocused }` fires with `false` while the Activity is torn down and
overwrites the value before it is saved. That focus is *also* not restored is what distinguishes this
from a keyboard-controller timing problem: had the flag survived, `requestFocus()` would have put the
caret back even if the keyboard had not appeared.

Not fixed here. A latch-on-focus-gain would restore the keyboard but would also re-open one the user
had deliberately dismissed, so the correct behaviour is a product decision rather than a mechanical
change. Recorded as **task 6a.9** with this measurement.

### 13.6 Discharge record — the Galaxy Z Fold 7 rows (task G.7, second device)

The device became available on 2026-09-01, immediately before archive, so the rows §13.5 recorded as
hardware-blocked were run rather than archived as blocked. Device: `RFCY720PJKV`, `SM-F966B`,
**Android 16 / API 36**, One UI. Every override was reset afterwards and the reset verified.

**§13.3's own prediction is confirmed, and it matters.** That table says the Fold "cannot prove API 37
gated behaviour, **if** it ships on Android 16" — it does. So this device discharges the *layout and
configuration-change* rows and cannot speak to the API-37 gating at all; §13.4's Pixel run remains the
only evidence for that half. Two devices, two different jobs, exactly as §13.3 argued.

| Row | Result |
|---|---|
| Native large screen, unfolded | **PASS** at the real inner display, `1968x2184` — title, both top-bar actions, banner **with its "Fix" action visible**, and the habit row, no clipping or overlap. The banner fix from §13.5 holds at a width far greater than the `wm size` override it was found under. |
| Habit editor, unfolded | **PASS** — name, colour, frequency, the "Remind me" toggle and Save all present and intact. |
| Orientation, unfolded | **PASS** in landscape, all editor fields present, entered text preserved. |
| **Fold / unfold as a configuration change** | **PASS**, and this is the harshest case in the matrix. |
| One UI "put unused apps to sleep" survival | **NOT RUN** — the device is here, but this row needs a multi-day idle window. It stays open. |

#### The fold/unfold measurement

Typed into the editor's notes field with the keyboard up, then folded and unfolded:

| Observation | Before | Folded | After unfolding |
|---|---|---|---|
| Display | `1968x2184` (inner) | **`1080x2520` (cover)** | `1968x2184` |
| `dumpsys input_method` `mInputShown` | `true` | — | **`true`** |
| Focused field | notes | — | **notes** |
| Typed content | `FOLDTEST` | — | `FOLDTEST` |

Task 6a.9's focus latch survives a configuration change that **moves the app between two physical
displays** — considerably harsher than the rotation it was built for — and still restores the caret to
the notes field rather than stealing it back to the name field.

#### One more thing this device proved for free

The entire automated suite passes here too: **59 instrumented, 0 failures**, plus 52 `:domain` and 97
`:app` unit tests, on a different OEM at a different API level. Nothing in the suite was Pixel-specific.

Worth recording how the run started, because it is the third instance of the same trap: the first
attempt failed with **every** Compose UI test reporting
`IllegalStateException: No compose hierarchies found in the app`, and the cause was that the device was
`mWakefulness=Dozing` with `mCurrentFocus=null`. Not a keyguard this time, not a dependency, not an OEM
quirk — an asleep screen. **That error message means "no Activity resumed", and the first thing to
check is always whether the device is awake and interactive.** `adb shell screencap` on a foldable also
needs an explicit `-d <displayId>`, or it writes a warning where the PNG should be.

## 14. Module and file layout

| Path | Action | Contents |
|---|---|---|
| `settings.gradle.kts` | Create | Includes `:domain`, `:app`; repositories |
| `gradle/libs.versions.toml` | Create | Version catalog (§2) |
| `build.gradle.kts` (root) | Create | Plugin aliases, `apply false` |
| `domain/build.gradle.kts` | Create | `kotlin("jvm")` only — the boundary's enforcement (§4) |
| `domain/src/main/kotlin/.../` | Create | `Habit`, `Schedule`, `ReminderSlot`, `Entry`, `EntryStatus`, `Due`, `DayStatus`, `dueOn`, `rollupDay`, `StreakCalculator`, `ComplianceCalculator` |
| `app/build.gradle.kts` | Create | AGP 9 `compileSdk { version = release(37) }`, `minSdk 31`, `targetSdk 37`, Hilt + KSP, `room.schemaLocation` |
| `app/schemas/1.json` | Create (generated) | Exported Room schema, checked in |
| `app/src/main/AndroidManifest.xml` | Create | `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`; **no** `INTERNET`, **no** `USE_EXACT_ALARM`; explicit `android:exported` on every component |
| `app/.../habit/` | Create | Habit CRUD, repository, editor UI |
| `app/.../scheduling/` | Create | `OccurrencePlanner`, `AlarmScheduler`, reschedule receivers, `MidnightSweepWorker`, `ReconcileWorker` |
| `app/.../tracking/` | Create | Entry writes, Today screen |
| `app/.../reminding/` | Create | Channel, `NotificationPoster`, `ActionReceiver`, `AnswerWorker`, `SnoozeWorker`, snooze settings |
| `app/.../progress/` | Create | Streak/compliance presentation |
| `app/.../portability/` | Create | Export/import, SAF plumbing, DTOs |
| `app/.../core/` | Create | Room database + DAOs + mappers, Hilt modules, `TimeProvider`, permission gates, theme |
| `openspec/config.yaml` | Modify | §3 |
| `openspec/changes/habit-tracking-mvp/design.md` | Create | This document |

## 15. Migration / rollout

No data migration: this is the first version. The rollback plan in `proposal.md` is adopted unchanged
and this design adds the mechanisms it depends on:

- A single feature flag gates the whole reminder pipeline. **Disabling it cancels every armed alarm
  and calls `cancelUniqueWork` on the reconcile and sweep workers** — it does not merely stop
  scheduling new ones, because enqueued periodic work and registered `PendingIntent`s both survive an
  app update.
- `reminder_occurrences` makes "cancel every alarm we ever armed" a bounded query rather than a
  guess, which is what makes a rollback build able to clean up after itself (rollback step 1).
- Room schema export is on from the first commit; migrations are additive-only; the pre-migration
  auto-export is the documented downgrade path, since Room cannot roll a schema version backwards.
- Entries already written by notification actions stay valid across a rollback; only scheduling stops.

## 16. Divergence requiring a spec amendment

`sdd-spec` ran in parallel and its `habit-entry-tracking` spec encodes the pre-D3 model. This design
does not touch spec files. One amendment is needed and must go to the user:

| Spec requirement | Current text | Needed change |
|---|---|---|
| Midnight Transition | `UNKNOWN → MISSED` at local midnight, unconditionally | Add the exception: **not** for a slot whose occurrence has a live snooze (`state = SNOOZED AND snoozeUntil > now`), and **not** for `N_TIMES_PER_WEEK` (D8) |
| Provisional-Missed Correction | A midnight-written `MISSED` is provisional while a snooze is live | Reframe: the midnight job does not write `MISSED` over a live snooze at all, so the pending state is `UNKNOWN`. The late `MISSED → COMPLETED` correction requirement **remains mandatory** for the abandoned-then-answered, manual-edit, and import paths. Its "readers must recompute, never cache" clause is unaffected. |
| Abandoned snooze scenario | "Stays `MISSED` permanently" | End state is identical; the *timing* changes — `MISSED` is written by grace expiry or the 24 h resolve deadline instead of at midnight |
| Streak Calculation | `SKIPPED` passes through | Add explicitly that `UNKNOWN` also passes through without breaking a streak (required by D3, and already implied by compliance excluding `UNKNOWN`) |

Everything else in the spec set is consistent with this design.

## 17. Open questions

- [ ] **Blocking before scaffolding merges:** discharge the §5.4 API 37 behaviour-change gate against
      authoritative Android documentation. This design could not verify it (§5.3).
- [ ] Confirm the D3 decision and the spec amendment in §16.
- [ ] Confirm OA-1 … OA-5.
- [ ] Pin Room and WorkManager versions (not resolvable offline; work unit 1).
- [ ] Choose lint/formatter (ktlint vs detekt). Detekt is preferred because §4's clock-access ban
      needs a `ForbiddenMethodCall` rule, which ktlint cannot express.
- [ ] Confirm the hourly `ReconcileWorker` period and the 24 h `resolveDeadline`; both are the
      tunable constants behind D3's bounds.
- [ ] Confirm ISO-Monday week start as the MVP default (D7), and whether a week-start setting is
      wanted in the MVP or deferred.

## Note on document size

The `sdd-design` skill sets an 800-word budget for design artifacts. This document exceeds it
deliberately: the phase contract for this change mandates an API 37 behaviour-change analysis, two
sequence diagrams (`config.yaml` `rules.design`), a full Room schema with a spelled-out migration
path, an export file format, degradation paths, and per-module test commands. Content was compressed
into tables wherever prose was avoidable, but the mandated content cannot fit the default budget. No
task breakdown is included — that belongs to `sdd-tasks` — and no delivery strategy is chosen, since
`delivery_strategy` is `ask-on-risk` and the split decision goes to the user after `sdd-tasks`.
