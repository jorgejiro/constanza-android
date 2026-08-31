# Habit Entry Tracking Specification

## Purpose

Defines `Entry` states, slot independence, the midnight transition, the provisional-missed correction rule, and the day-level rollup. `:domain` requirements are framework-agnostic.

## Requirements

### Requirement: Entry States

Each occurrence (habit × date × slot) MUST be represented by exactly one `Entry` with status `COMPLETED`, `MISSED`, `SKIPPED`, or `UNKNOWN`. `SKIPPED` MUST be settable only through an explicit in-app user action, never through a notification action.

#### Scenario: Unanswered occurrence starts unknown
- GIVEN a newly due occurrence
- WHEN no action has been taken
- THEN its `Entry` status is `UNKNOWN`

#### Scenario: User skips in-app
- GIVEN a due occurrence
- WHEN the user marks it skipped from within the app
- THEN its `Entry` status becomes `SKIPPED`

### Requirement: Slot Independence

For a `TIMES_PER_DAY` habit, each `ReminderSlot` occurrence on a given date MUST have its own `Entry` and MUST be answerable independently of every other slot that date.

#### Scenario: Answering one slot leaves another untouched
- GIVEN a habit with two slots due today, both `UNKNOWN`
- WHEN the user answers Yes to the first slot
- THEN the second slot's `Entry` remains `UNKNOWN`

### Requirement: Midnight Transition

At the local midnight boundary following a due occurrence's date, any `Entry` still `UNKNOWN` for that occurrence MUST transition to `MISSED`, with two exceptions. The transition MUST NOT apply to an occurrence with a live snooze, defined as `state = SNOOZED AND snoozeUntil > now`; the `Entry` MUST remain `UNKNOWN` while the snooze is live. The transition MUST NOT apply to any occurrence of an `N_TIMES_PER_WEEK` habit, because that schedule kind carries no determinate per-date obligation (see the occurrence-due predicate in `habit-scheduling`).

#### Scenario: Unanswered slot becomes missed after midnight
- GIVEN an occurrence due 2026-09-01, still `UNKNOWN` at 23:59, with no snooze outstanding
- WHEN local midnight passes into 2026-09-02
- THEN the 2026-09-01 `Entry` becomes `MISSED`

#### Scenario: Live snooze survives midnight as unknown
- GIVEN an occurrence due 2026-09-01 with a live snooze (`snoozeUntil` after local midnight)
- WHEN local midnight passes into 2026-09-02
- THEN the 2026-09-01 `Entry` remains `UNKNOWN`, and no `MISSED` row is written

#### Scenario: N_TIMES_PER_WEEK never receives a dated missed at midnight
- GIVEN an N_TIMES_PER_WEEK habit with an unmet weekly quota
- WHEN local midnight passes for any date in that week
- THEN no `Entry` for that habit is written to `MISSED` by the midnight transition

### Requirement: Provisional-Missed Correction

The midnight transition MUST NOT write `MISSED` over a live snooze (see Midnight Transition); consequently, the pending occurrence's `Entry` stays `UNKNOWN` for as long as the snooze remains live, and no provisional `MISSED` row exists to correct in that case. The `MISSED → COMPLETED` correction remains MANDATORY for the three paths that still resolve an occurrence to `MISSED` before an answer arrives: an occurrence force-resolved to `MISSED` (by grace expiry or the hard resolve deadline, see Abandoned Snooze Resolution) that is later answered from a notification or in-app; a manual in-app edit of a past day; and an import. WHEN any of those three paths later answers the same occurrence, the system MUST update the SAME `Entry` (same habitId, date, slotId), crediting the ORIGINALLY-SCHEDULED date regardless of the calendar date the answer was given on. Every reader of `Entry` status (streak, compliance, today screen, future digest) MUST recompute from current `Entry` state rather than caching a pre-correction `MISSED` result.

#### Scenario: Happy path — live snooze never becomes missed, then completes
- GIVEN a slot due 2026-09-01 22:00 with an outstanding, live snooze
- WHEN midnight passes and the snooze is still live
- THEN the 2026-09-01 `Entry` remains `UNKNOWN`, not `MISSED`
- WHEN the user answers Yes at 2026-09-02 00:40 from the snoozed notification
- THEN the same 2026-09-01 `Entry` transitions from `UNKNOWN` to `COMPLETED`, and no `Entry` is created for 2026-09-02 for that slot

#### Scenario: Force-resolved missed corrected by a late answer
- GIVEN an occurrence force-resolved to `MISSED` by the hard resolve deadline
- WHEN the user answers Yes from a later notification or in-app
- THEN the same `Entry` transitions from `MISSED` to `COMPLETED` on its originally-scheduled date

#### Scenario: Manual in-app edit corrects a past missed day
- GIVEN a past `Entry` recorded `MISSED`
- WHEN the user edits that day in-app to `COMPLETED`
- THEN the `Entry` reflects `COMPLETED` and every reader recomputes from that state

#### Scenario: Streak interaction
- GIVEN a streak calculation that currently treats 2026-09-01 as broken because its `Entry` is `MISSED`
- WHEN a later correction transitions that `Entry` to `COMPLETED`
- THEN a streak calculation run after the correction MUST show an unbroken streak through 2026-09-01

### Requirement: Abandoned Snooze Resolution

An occurrence with a live snooze that is never answered MUST still resolve to `MISSED` within a bounded time, so that `UNKNOWN` cannot leak indefinitely and inflate compliance. Resolution MUST occur through whichever of two paths comes first: grace expiry, when a snoozed occurrence's snooze lapses without a new snooze being armed; or a hard resolve deadline of `scheduledAt + 24h`, clamped to the next occurrence of the same slot. Once force-resolved, the `Entry` for that occurrence's originally-scheduled date becomes `MISSED` and remains `MISSED` unless later corrected per the Provisional-Missed Correction requirement. **The `N_TIMES_PER_WEEK` exception of the Midnight Transition requirement applies here too**: force-resolution MUST NOT write a dated `MISSED` for a schedule kind whose unit of obligation is the week (design D8), because one unmet weekly quota would otherwise fabricate a dated failure. The occurrence is still resolved so it stops being rescanned; only the dated row is withheld. Both write paths MUST share one gate — an earlier implementation applied it at midnight but not on abandonment, and the same phantom failure arrived through the other door. This bound is NOT a cap on how many times an occurrence may be snoozed — snoozing itself remains unlimited; only the calendar date's resolution is bounded.

#### Scenario: Grace expiry resolves an abandoned snooze
- GIVEN an occurrence with a live snooze whose alarm fires and is dismissed without an answer, and no further snooze is armed
- WHEN the grace period following that dismissal elapses
- THEN the occurrence's `Entry` becomes `MISSED` on its originally-scheduled date

#### Scenario: Hard resolve deadline bounds an ever-snoozing occurrence
- GIVEN an occurrence repeatedly re-snoozed without ever being answered
- WHEN the resolve deadline of `scheduledAt + 24h` (clamped to the next same-slot occurrence) is reached
- THEN the occurrence's `Entry` becomes `MISSED` on its originally-scheduled date, and no further snooze is offered for that occurrence

#### Scenario: An abandoned weekly-quota occurrence receives no dated missed
- GIVEN an `N_TIMES_PER_WEEK` occurrence that is snoozed and then abandoned past its resolve deadline
- WHEN force-resolution runs
- THEN no `Entry` row is written for that date, and the occurrence is still marked resolved

#### Scenario: Unlimited snoozing is unaffected by the resolve bound
- GIVEN an occurrence snoozed many times within the 24-hour resolve window
- WHEN each snooze is answered by another snooze rather than Yes/No
- THEN every snooze is accepted with no count limit, and only elapsed calendar time — never the snooze count — determines when resolution becomes mandatory

### Requirement: Day-Level Rollup and Per-Slot Display

(Orchestrator assumption pending user confirmation at spec review: both the rollup function and the per-slot UI are treated as required and non-overlapping, per decision 2's slot independence.)

The `:domain` module MUST expose a pure function that collapses all of a habit's `Entry` rows for a single date into one day-level status, independent of any UI. Separately, the today screen MUST display each due occurrence's state per slot rather than only the collapsed day-level status.

#### Scenario: Day rollup reports partial completion
- GIVEN a 3-slot day with 2 `COMPLETED` and 1 `UNKNOWN`
- WHEN the day-level rollup function evaluates that date
- THEN it reports a partially-completed day status

#### Scenario: Today screen shows independent slot rows
- GIVEN a 3-slot habit due today
- WHEN the today screen renders that habit
- THEN it shows three independently answerable rows, not one collapsed row
