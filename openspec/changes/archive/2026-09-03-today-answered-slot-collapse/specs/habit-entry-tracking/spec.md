# Delta for Habit Entry Tracking

## MODIFIED Requirements

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

### Requirement: Day-Level Rollup and Per-Slot Display

(Ratified 2026-09-01: both the rollup function and the per-slot UI are required and non-overlapping, per decision 2's slot independence.)

The `:domain` module MUST expose a pure function that collapses all of a habit's `Entry` rows for a single date into one day-level status, independent of any UI. Separately, the today screen MUST display each due occurrence's state per slot rather than only the collapsed day-level status.

A pending slot (`UNKNOWN`) MUST display its three answer actions (Yes, No, Skip). An answered slot (`COMPLETED`, `MISSED`, or `SKIPPED`) MUST instead display text naming its specific answer (Done, Missed, or Skipped — never a generic "answered" label) plus exactly one control to change it, never relying on colour or position alone. That control MUST be reachable and operable without a gesture, with an accessible label distinguishing its slot from every other slot on screen. This applies identically to the single-slot row and an expanded multi-slot row; the day-level rollup row is unaffected and out of scope.
(Previously: required per-slot state display only; now also requires distinct answered-state text, a change route, and gesture-free, individually-labelled reachability.)

**Rollup precedence (ratified 2026-09-01, task 6b.10):** the collapsed status MUST lead with progress, not failure. A day with at least one `COMPLETED` slot and at least one `MISSED` slot MUST report a partially-completed status, never a missed-day status. A missed-day status MUST be reported only when a day has no `COMPLETED` slot at all and at least one `MISSED` slot. The full precedence, most to least specific: all slots `UNKNOWN` reports pending; all slots `COMPLETED` reports fully-completed; all slots `SKIPPED` reports fully-skipped; no `COMPLETED` slot and at least one `MISSED` slot reports missed; every other mix (including any `COMPLETED` slot alongside any `MISSED` slot) reports partial completion.

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
