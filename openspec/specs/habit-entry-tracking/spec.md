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

For a `TIMES_PER_DAY` habit, each `ReminderSlot` occurrence on a given date MUST have its own `Entry` and MUST be answerable independently of every other slot that date. A slot's controls and presentation MAY differ from another slot's based on its own `Entry` status; reopening or answering one slot MUST NOT affect any other slot's presentation, controls, or `Entry`, including a same-habit, same-date sibling.
(Previously: Entry-level independence only; now also covers UI presentation and control set.)

#### Scenario: Answering one slot leaves another untouched
- GIVEN a habit with two slots due today, both `UNKNOWN`
- WHEN the user answers Yes to the first slot
- THEN the second slot's `Entry` remains `UNKNOWN`

#### Scenario: Reopening one answered slot leaves a same-habit sibling slot collapsed
- GIVEN a habit with two answered slots on the same date, one Done and one Missed, both showing their state and a route to change it
- WHEN the user activates the first slot's route to change its answer
- THEN only the first slot reveals its three answer actions; the second slot continues showing its Missed state and its own untouched route

#### Scenario: A single-slot habit remains independently answerable
- GIVEN a habit with no reminder time configured, having exactly one due occurrence with a null slot identifier
- WHEN the user answers that occurrence
- THEN its `Entry` updates and that one slot reflects the new state, with no effect on any other habit's slots

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

For the manual in-app edit path specifically, the reach into the past MUST be unbounded, with no fixed window and no bound tied to a habit's creation date; a past date with nothing scheduled simply renders empty rather than blocking navigation. The edit MUST NOT be restricted to the `MISSED → COMPLETED` transition alone — any slot on a past day MUST be settable to any of `COMPLETED`, `MISSED`, or `SKIPPED`, and MAY be changed again afterward any number of times. The `MISSED → COMPLETED` floor stated above is satisfied as a subset of this broader capability.
(Previously: named the manual in-app edit path with no caller reaching it, and stated only the `MISSED → COMPLETED` floor with no stated reach or transition scope.)

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

#### Scenario: Unbounded backward reach for a manual edit
- GIVEN a past day arbitrarily distant from today, with no habit-creation-date bound and no fixed window
- WHEN the user navigates to and edits that day in-app
- THEN the edit is accepted and the `Entry` updates on that date, with no distance restriction applied

#### Scenario: Any past slot is freely re-editable to any status
- GIVEN a past slot currently recorded `COMPLETED`
- WHEN the user edits it in-app to `MISSED`, and then again to `SKIPPED`
- THEN each edit is accepted in turn and the `Entry` reflects the latest status

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

(Ratified 2026-09-01: both the rollup function and the per-slot UI are required and non-overlapping, per decision 2's slot independence.)

The `:domain` module MUST expose a pure function that collapses all of a habit's `Entry` rows for a single date into one day-level status, independent of any UI. Separately, the today screen MUST display each due occurrence's state per slot rather than only the collapsed day-level status.

A pending slot (`UNKNOWN`) MUST display its three answer actions (Yes, No, Skip). An answered slot (`COMPLETED`, `MISSED`, or `SKIPPED`) MUST instead display text naming its specific answer (Done, Missed, or Skipped — never a generic "answered" label) plus exactly one control to change it, never relying on colour or position alone. That control MUST be reachable and operable without a gesture, with an accessible label distinguishing its slot from every other slot on screen. This applies identically to the single-slot row and an expanded multi-slot row; the day-level rollup row is unaffected and out of scope.

While displaying the **live-today view** — meaning the user has not deliberately navigated to a past date — the today screen MUST track the current local date rather than the date at construction. Crossing local midnight MUST re-query and re-render the per-slot display and rollup against the new date, with no user interaction. If backgrounded across midnight, resuming MUST re-read the current local date and correct the display by that resume. This obligation covers midnight rollover while displayed and resume after backgrounding; it excludes a timezone change while continuously foregrounded, a named, accepted scoping boundary. Once the user has deliberately navigated to a past date, neither a midnight rollover nor a resume MUST move the displayed date away from that deliberately-viewed date; the screen resumes tracking the current local date only once the user returns to the live-today view.

**Date navigation:** from the live-today view, the user MAY navigate backward to any past date with no lower bound; a past date with nothing scheduled MUST render empty rather than an error. Forward navigation MUST NOT reach any date later than the current local date — the live-today view is the forward boundary. While viewing a past date, the add-habit affordance MUST be absent, and the screen MUST instead present past-day empty-state text where the affordance would otherwise be. Any per-slot UI-only state, such as which slot is expanded or reopened, MUST NOT carry over from one displayed date to another.

**Rollup precedence (ratified 2026-09-01, task 6b.10):** the collapsed status MUST lead with progress, not failure. A day with at least one `COMPLETED` slot and at least one `MISSED` slot MUST report a partially-completed status, never a missed-day status. A missed-day status MUST be reported only when a day has no `COMPLETED` slot at all and at least one `MISSED` slot. The full precedence, most to least specific: all slots `UNKNOWN` reports pending; all slots `COMPLETED` reports fully-completed; all slots `SKIPPED` reports fully-skipped; no `COMPLETED` slot and at least one `MISSED` slot reports missed; every other mix (including any `COMPLETED` slot alongside any `MISSED` slot) reports partial completion.
(Previously: the local-date-tracking obligation was unconditional and covered only midnight rollover and resume, with no concept of a deliberately navigated-away date and no navigation surface at all.)

#### Scenario: Day rollup reports partial completion
- GIVEN a 3-slot day with 2 `COMPLETED` and 1 `UNKNOWN`
- WHEN the day-level rollup function evaluates that date
- THEN it reports a partially-completed day status

#### Scenario: A missed slot alongside a completed slot reports partial, not missed
- GIVEN a 3-slot day with 2 `COMPLETED` and 1 `MISSED`
- WHEN the day-level rollup function evaluates that date
- THEN it reports a partially-completed day status, not a missed-day status, because at least one
  slot completed

#### Scenario: No completion at all still reports a missed day
- GIVEN a 3-slot day where every slot is `MISSED`
- WHEN the day-level rollup function evaluates that date
- THEN it reports a missed-day status, because zero slots completed

#### Scenario: Today screen shows independent slot rows
- GIVEN a 3-slot habit due today
- WHEN the today screen renders that habit
- THEN it shows three independently answerable rows, not one collapsed row

#### Scenario: An answered slot names its specific answer and offers one route, without colour
- GIVEN three slots recorded `COMPLETED`, `MISSED`, and `SKIPPED` respectively
- WHEN the today screen renders each, with no colour perception assumed (e.g. via TalkBack)
- THEN each shows its own distinct text — "Done", "Missed", or "Skipped", never a shared generic label — plus exactly one route to change it, with no Yes/No/Skip actions remaining

#### Scenario: The change route is reachable without a gesture and names its own slot
- GIVEN a two-slot habit where both slots are answered
- WHEN each slot's change route is reached via a standard tap/click or an accessibility-service action, with no swipe or other gesture
- THEN both routes are operable, and each one's accessible label distinguishes its own slot from the other — no two slots share an identical label

#### Scenario: Today screen rolls over at local midnight while displayed

- GIVEN the today screen is open showing 2026-09-01, with a habit due that date, and the user has not navigated away from the live-today view
- WHEN local midnight passes into 2026-09-02 while the screen stays open
- THEN the screen re-queries and re-renders 2026-09-02's occurrences and rollup, with no user action

#### Scenario: A backgrounded app corrects the date on resume

- GIVEN the screen showed 2026-09-01 when backgrounded, while at the live-today view, and midnight has since passed into 2026-09-02
- WHEN the user resumes the app
- THEN the screen re-reads the current date and renders 2026-09-02's occurrences and rollup

#### Scenario: Foregrounded timezone travel remains a known, accepted gap

- GIVEN the today screen is continuously foregrounded, never backgrounded
- WHEN the device's timezone changes such that midnight has already passed in the new zone
- THEN the screen MAY keep showing the prior date until the user leaves and returns; this gap is an accepted scoping boundary, not a defect

#### Scenario: Midnight rollover does not move a deliberately viewed past date

- GIVEN the today screen is navigated to and displaying 2026-08-20, a past date
- WHEN local midnight passes into a new current date while that past date is still displayed
- THEN the screen continues showing 2026-08-20, and does not snap back to the new current date

#### Scenario: Resume does not move a deliberately viewed past date

- GIVEN the today screen is displaying a past date the user deliberately navigated to
- WHEN the app is backgrounded and then resumed (`ON_RESUME`)
- THEN the screen still displays that same past date, unchanged

#### Scenario: Returning to the live edge resumes following the clock

- GIVEN the user navigates back from a past date to the live-today view
- WHEN local midnight later passes while the live-today view stays open
- THEN the screen re-queries and re-renders against the new current date, exactly as required at the live edge

#### Scenario: Forward navigation stops at today

- GIVEN the today screen is displaying a past date
- WHEN the user navigates forward repeatedly
- THEN navigation stops at the current local date, and no date later than today becomes reachable

#### Scenario: Add-habit affordance is absent on a past day

- GIVEN the today screen is displaying a past date with no occurrences scheduled
- WHEN the empty state renders
- THEN no add-habit action is shown, and past-day empty-state text is shown in its place

#### Scenario: Per-slot UI state does not leak across a navigation

- GIVEN a habit's slot is expanded, or a reopened-change control is showing, while viewing one date
- WHEN the user navigates to a different date and back
- THEN that slot is not left expanded or reopened on the newly displayed date as a side effect of the earlier navigation

### Requirement: In-App Answer Date Attribution

When the user answers an occurrence from the today screen, the system MUST record the answer against the local date the answered row was displaying at the moment of the answer — whether that is the live current date or a date the user has deliberately navigated to — never a stale date left over from construction or from before a navigation. If local midnight has passed while the live-today view is displayed, the write MUST target the new current date's entry, not the previous date's. If the user has deliberately navigated away from the live-today view, the write MUST target the navigated-to date regardless of what the live current date is at the moment of the answer.
(Previously: worded entirely around midnight passing while the screen showed today, with no language for a deliberately navigated date.)

#### Scenario: Answer given before midnight is recorded on that date

- GIVEN the today screen displays 2026-09-01 and an occurrence due that date is `UNKNOWN`
- WHEN the user answers Yes at 23:50 on 2026-09-01
- THEN the `Entry` for 2026-09-01 becomes `COMPLETED`

#### Scenario: Answer given after midnight is recorded on the new date, not the old one

- GIVEN the screen was constructed while 2026-09-01 was current, and midnight has since passed into 2026-09-02
- WHEN the user answers Yes at 00:15 on 2026-09-02, after the screen updates to 2026-09-02
- THEN the write targets the 2026-09-02 entry, and no entry is written against 2026-09-01

#### Scenario: Answering a navigated-to past day credits that date, not today

- GIVEN the user has navigated the today screen to a past date with an occurrence recorded `MISSED`
- WHEN the user answers that occurrence `COMPLETED` while the live current date is a later date
- THEN the `Entry` for the navigated-to past date becomes `COMPLETED`, and no `Entry` is written against the live current date

