# Exploration — Habit-tracking MVP

Change: `habit-tracking-mvp`
Phase: `sdd-explore`
Date: 2026-08-31
Status: done — ready for proposal

## Licensing constraint

Loop Habit Tracker (`iSoron/uhabits`) is **GPL-3.0** (verified via the GitHub API).
Its `Frequency`, `Entry`, and `Reminder` models are discussed below **only as conceptual
prior art**, to name known limitations Constanza must avoid. No code, structure, or
algorithm is copied, translated, or derived from it. Every recommendation here is an
independent design.

## Fixed product decisions (set by the user, not open for re-litigation)

1. Frequencies to support: daily; several specific times per day; N times per week;
   once a week; once a month; every N days.
2. "Several times per day" uses **explicit clock times** (e.g. 08:00, 14:00, 20:00).
   Each slot fires its own notification and is satisfied or missed **independently**.
   This is not a counter-without-time model.
3. Habit values are **Yes/No only** in the MVP. Numeric habits are out of MVP scope,
   but the schema must admit them later without a destructive migration.
4. A **`skipped`** state is required — it must neither break a streak nor count as a
   failure. Entry states: `completed` / `missed` / `skipped` / `unknown`.
5. Notification actions: **Yes**, **No**, **Snooze**. Snooze default 20 minutes,
   configurable in settings among 10/20/30 min and 1/2/3/4 h.
6. Storage is **local-only forever**, with manual export/import to a file. No cloud
   sync ever — do not design for multi-device conflict resolution.
7. Later phases (out of MVP, must not be architecturally blocked): proactive daily
   digest notification listing today's habits and status; weekly equivalent; printable
   compliance grid.
8. Permanently out of scope: to-do/task management, gamification, social features.

---

## A. Reminder delivery reliability

### AlarmManager vs WorkManager — where the boundary is

| Concern | Owner | Why |
| --- | --- | --- |
| Firing a notification at a user-specified clock time | **AlarmManager** (`setExactAndAllowWhileIdle`) | Habit reminders are core, user-facing, precisely-timed actions. WorkManager's minimum flex is 15 minutes; it cannot deliver "08:00 sharp". |
| Ensuring the right alarms exist after reboot / update / timezone / DST / midnight, and catching alarms the OS silently dropped | **WorkManager** (periodic reconciliation worker) | AlarmManager alarms do not survive reboot and are not designed for "verify state" work. |

**Verdict:** AlarmManager fires; WorkManager reconciles and acts as a safety net.
Neither replaces the other.

### SCHEDULE_EXACT_ALARM vs USE_EXACT_ALARM

**Use `SCHEDULE_EXACT_ALARM`. `USE_EXACT_ALARM` is not legitimately available to Constanza.**

Verified against `developer.android.com/develop/background-work/services/alarms/schedule`:

- `USE_EXACT_ALARM` is granted automatically, **cannot be revoked by the user**, is
  limited to specific use cases, and is subject to a Google Play policy. The
  documentation states: "If your app's core functionality depends on a precisely-timed
  alarm — such as for an alarm clock app or a calendar app — then it's OK to use an
  exact alarm instead."
- `SCHEDULE_EXACT_ALARM` is user-granted, revocable by user or system, and covers a
  broader set of use cases. Android 14 made it denied-by-default for newly installed
  apps targeting API 33+.

**Fallback when denied or revoked:** call `alarmManager.canScheduleExactAlarms()` before
**every** scheduling call and in `onResume()` — not once at startup. When it returns
false, degrade to `setAndAllowWhileIdle()` / `setWindow()` (an inexact window of at
least 10 minutes) rather than silently failing. Register a receiver for
`AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` to reschedule
proactively when the user later grants or revokes the permission, instead of waiting for
the next app launch.

### POST_NOTIFICATIONS (API 33+)

- Request it at a contextual moment — immediately after the user creates their first
  habit reminder — not on cold first launch.
- If denied twice, the system silently blocks all future requests without showing the
  dialog again. The only recovery path is sending the user to system settings.
- When denied, no drawer notifications are delivered at all. The in-app "today's habits"
  list must remain fully usable without push.

### Doze, App Standby, and OEM battery optimisation

Documented degradation, not speculation: Samsung One UI's "Put unused apps to sleep" can
silently re-throttle an app after a firmware update even when the user previously
excluded it; Xiaomi MIUI's aggressive battery saver is a common cause of delayed
notifications.

Mitigation that does not require an exemption prompt on first launch (Play policy
discourages unsolicited `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and it is poor
first-run UX):

1. A periodic WorkManager **missed-reminder sweep** that detects a slot which should
   have fired and did not, then fires the notification late rather than losing it
   silently.
2. Contextual in-app education — a settings-screen link to per-OEM guidance — offered
   only after the sweep actually detects repeated misses. Reactive, not a first-launch
   interruption.

### Mandatory rescheduling triggers

None of these may be skipped; each is a documented gap in AlarmManager's own persistence
model:

- `BOOT_COMPLETED` (device reboot)
- `MY_PACKAGE_REPLACED` (app update)
- `ACTION_TIMEZONE_CHANGED`
- `ACTION_DATE_CHANGED` / `ACTION_TIME_CHANGED` (manual clock change, DST, midnight rollover)
- Direct in-app reschedule whenever the user edits a habit's schedule

### Verified API levels

Verified against Google Play Console Help on 2026-08-31:

| Item | Value |
| --- | --- |
| Target API requirement for new apps and updates, effective **2026-08-31** | Android 16 / **API 36** |
| Extension available on request | to 2026-11-01 |
| `SCHEDULE_EXACT_ALARM` denied by default | since Android 14 |

Verbatim: "New apps and app updates must target Android 16 (API level 36) or higher to be
submitted to Google Play."

**Recommendation:** `compileSdk = 36`, `targetSdk = 36` from day one. `minSdk` is an
**open decision** — 26 is proposed as a starting point for `sdd-design` to confirm or revise.

---

## B. Notification action handling

- **Component:** a `BroadcastReceiver` receives the Yes / No / Snooze `PendingIntent`s
  from the notification action buttons. Never an Activity — the UI must never have to open.
- **Reliability detail:** `onReceive()` has a hard ~10-second wall-clock budget, which is
  risky under OEM throttling. The receiver should only validate the intent and enqueue an
  **expedited one-off WorkManager job** that performs the Room write, the streak
  recompute, and the notification dismiss or re-arm. Writing to the database directly in
  the receiver is a reliability risk, especially on the Samsung and Xiaomi devices flagged
  above.
- **Snooze re-arm:** the worker schedules a new exact alarm for `now + snoozeMinutes`
  (default 20, configurable per decision 5) for the same habit and slot.

### Open product decisions (not resolved by this exploration)

1. When a snooze pushes the notification past midnight, which calendar date does the
   eventual Yes/No answer credit — the originally-scheduled date, or the date it is
   actually answered on?
2. Should repeated snoozing be capped (for example, three then force a decision), or
   unlimited?
3. Should an unanswered slot auto-transition to `missed` at a defined day-boundary
   cutoff, or remain `unknown` indefinitely?
   - `missed` is decisive and keeps compliance arithmetic simple, but presumes an intent
     the user never expressed.
   - `unknown` is truthful, but forces every downstream compliance, streak, and digest
     calculation to handle an ambiguous third state instead of a clean binary.

---

## C. Domain model options

### Loop's known limitations (prior art, conceptual description only)

- `Frequency(numerator, denominator)` cannot express a true calendar month — a fraction
  cannot distinguish "once a month" from "every 30 days".
- Day-granularity `Entry` cannot express independent multiple-times-per-day.
- `Entry.value: Int` overloads sentinels (`NO=0`, `YES_AUTO=1`, `YES_MANUAL=2`, `SKIP=3`,
  `UNKNOWN=-1`) with real measured values — one field carrying two domains.

Constanza's design must avoid all three. None of the options below repeat them.

### Option 1 — Slot-aware Entry (recommended)

```
Habit(id, name, question, colour, notes, archived, createdAt)

Schedule(habitId,
         kind: DAILY | TIMES_PER_DAY | N_TIMES_PER_WEEK | WEEKLY | MONTHLY | EVERY_N_DAYS,
         params: Int / List<LocalTime> / anchorDate ...)

ReminderSlot(id, habitId, timeOfDay)      // populated only when kind == TIMES_PER_DAY

Entry(id, habitId, date, slotId: Long?,
      status: COMPLETED | MISSED | SKIPPED | UNKNOWN,
      value: Int? = null)                 // reserved for future numeric habits
```

- **Several times per day:** each explicit clock time is its own `ReminderSlot`; each
  fires and is answered independently through its own `Entry` row with `slotId` set.
  Single-occurrence schedules use `slotId = null`.
- **Calendar month vs every 30 days:** distinct `Schedule.kind` values —
  `MONTHLY(dayOfMonth)` with explicit last-day-of-month handling, versus
  `EVERY_N_DAYS(n, anchorDate)` computing occurrence as `(date - anchor) % n == 0`.
  No fraction is involved.
- **Yes/No now, numeric later:** `status` is a first-class field, never overloaded.
  `value` already exists as a nullable, unused column, so adding numeric support later is
  additive and non-destructive.
- **Skipped:** an ordinary `status` value, handled uniformly wherever `status` is read.
- **Export/import:** a plain relational snapshot serialises trivially to a
  human-editable JSON or CSV backup file. Decision 6 requires manual export/import
  forever with no sync, and a flat snapshot round-trips far more simply than an event log.

### Option 2 — Event-sourced occurrence ledger

An append-only `ScheduleOccurrence` plus `EntryEvent` log, with current state derived by
folding events. More powerful (full audit trail, trivial undo), but heavier for the MVP:
the export file becomes an event log rather than a snapshot, which is harder for a user
to inspect or edit by hand, and it adds complexity with no MVP-scoped payoff.

### Option 3 — Day-granularity single Entry (rejected)

Collapses all slots for a day into one `Entry`. Rejected outright: it reproduces Loop's
day-granularity limitation and directly violates decision 2.

### Recommendation

**Option 1.** It satisfies decisions 1–4 and 7 with the least structure, keeps the export
format human-readable, and avoids Loop's three documented flaws by construction.

---

## D. Streak and compliance calculation

| Approach | Description | Recommendation |
| --- | --- | --- |
| Compute-on-read | Derive streak and compliance from indexed `(habitId, date)` queries when needed | **Recommended for MVP.** Per-habit row counts are tiny — roughly 3,650 rows for a decade of daily tracking — and cheap on-device. Avoids denormalised-counter drift on entry edit, skip, and import. |
| Persisted counters on `Habit` | `currentStreak`, `bestStreak`, `complianceScore` updated transactionally on every write | Not recommended for MVP. It still needs a recompute-on-edit safety net, so it does not remove the read-time calculation — it just adds a second source of truth that can drift. |

- **Skipped interaction:** excluded from both numerator and denominator of any compliance
  ratio, and must not reset the current streak. A skip is a neutral pass-through, neither
  success nor failure.
- **Loop's exponential-decay score** (prior art, conceptual): a decayed weighted score
  that normalises daily and weekly habits onto one comparable scale. For the MVP, a
  simpler ratio-based score — `completed / (completed + missed)` over a rolling window,
  excluding skipped and unknown — is sufficient and easier to explain on a printable grid.
  An EWMA-style decay score is a possible post-MVP enhancement.

---

## E. Module boundaries — honest assessment

`openspec/config.yaml` currently plans a full multi-module Clean/Hexagonal/Screaming
split. At MVP size — one developer, one platform, no sync, Yes/No-only habits — a full
Gradle module split is **premature ceremony**, argued on evidence:

- Gradle module boundaries pay for themselves at team-scale parallelism or measurable
  incremental-build pain. Neither exists in a greenfield single-developer app.
- Per-module `build.gradle.kts` files and `api` / `implementation` visibility plumbing buy
  nothing until there is a second feature team or a real build-time problem.

The one boundary genuinely worth having from day one is a **pure-Kotlin `:domain`
module**, because it is what makes JVM unit testing of the scheduling and scoring engine
possible without an emulator — and therefore what makes this project's own strict-TDD
plan achievable.

| Module | Contents | Android SDK imports |
| --- | --- | --- |
| `:domain` | `Habit`, `Schedule`, `Entry`, `StreakCalculator`, `ComplianceCalculator`, occurrence-due predicates | **Forbidden** |
| `:app` | Compose UI, Room DAOs and entities, AlarmManager and WorkManager adapters, notification receivers, DI wiring; internally organised by package to preserve Clean/Hexagonal boundaries without Gradle overhead | Allowed |

**Recommendation:** start with exactly these two modules. Defer richer modularisation
(`:feature-habit`, `:feature-reminder`, `:core-ui`) until a real second surface — a Wear OS
companion, or a home-screen widget needing its own build target — or actual build-time
pain appears.

This is a recommendation for `sdd-design` to **revise** `config.yaml`'s
`stack.architecture`, not a silent override. That file already marks the stack
`planned_unratified` and assigns `sdd-design` to confirm or revise it.

---

## F. Later-phase enablers (model constraints only)

The daily/weekly digest and the printable grid are not architecturally blocked by
Option 1, provided the `:domain` module exposes two reusable pure functions rather than
burying them inside the Android-specific scheduling adapter:

1. **Occurrence-due predicate** — `Schedule` plus a date answers "is this habit due?".
   The same function that decides when to arm an alarm must be reusable unmodified by the
   future digest to compute what is due today or this week.
2. **Day-level rollup** — collapses a multi-slot day's `Entry` rows into one status
   ("all slots completed" versus "any slot missed"). Both the digest and the printable
   grid need this exact rule, so it must be defined once in `:domain`, not reinvented per
   feature.

No schema change is anticipated for either later feature under Option 1.

---

## Summary of recommendations

| Area | Recommendation | Effort |
| --- | --- | --- |
| Domain model | Option 1 — slot-aware `Entry` | Medium |
| Reminder delivery | `SCHEDULE_EXACT_ALARM` + WorkManager reconciliation, inexact fallback | Medium |
| Streak / compliance | Compute-on-read, ratio-based score | Low |
| Module structure | Two modules (`:domain`, `:app`) instead of full multi-module | Low |
| SDK levels | `compileSdk` / `targetSdk` 36; `minSdk` open (26 proposed) | — |

## Risks

- `USE_EXACT_ALARM` eligibility is a Play **policy** determination, not only a technical
  one. If Google's review disagrees with this reading at submission time, the fallback
  (`SCHEDULE_EXACT_ALARM`) is already the recommended default, so the risk is low but
  worth flagging.
- OEM battery-optimisation behaviour on Samsung and Xiaomi is not fully controllable by
  the app. The WorkManager missed-alarm sweep mitigates but does not eliminate delivery risk.
- Three notification-answer trade-offs (snooze across midnight, snooze cap, unanswered-slot
  transition) are unresolved and will block a complete spec if not decided.
- `minSdk` was not independently verified in this pass.

## Ready for proposal

**Yes.** Questions A–F all carry a stated recommendation and reasoning. Three
notification-behaviour decisions and `minSdk` remain open and must be confirmed or
explicitly deferred to `sdd-design`.
