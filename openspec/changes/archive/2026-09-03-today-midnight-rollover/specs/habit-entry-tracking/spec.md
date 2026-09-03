# Delta for Habit Entry Tracking

## ADDED Requirements

### Requirement: In-App Answer Date Attribution

When the user answers an occurrence from the today screen, the system MUST record the answer against the local date currently displayed at the moment of the answer, never the date the screen was constructed with. If local midnight has passed since construction, the write MUST target the new date's entry, not the previous date's.

#### Scenario: Answer given before midnight is recorded on that date

- GIVEN the today screen displays 2026-09-01 and an occurrence due that date is `UNKNOWN`
- WHEN the user answers Yes at 23:50 on 2026-09-01
- THEN the `Entry` for 2026-09-01 becomes `COMPLETED`

#### Scenario: Answer given after midnight is recorded on the new date, not the old one

- GIVEN the screen was constructed while 2026-09-01 was current, and midnight has since passed into 2026-09-02
- WHEN the user answers Yes at 00:15 on 2026-09-02, after the screen updates to 2026-09-02
- THEN the write targets the 2026-09-02 entry, and no entry is written against 2026-09-01

## MODIFIED Requirements

### Requirement: Day-Level Rollup and Per-Slot Display

(Ratified 2026-09-01: both the rollup function and the per-slot UI are required and non-overlapping, per decision 2's slot independence.)

The `:domain` module MUST expose a pure function that collapses all of a habit's `Entry` rows for a single date into one day-level status, independent of any UI. Separately, the today screen MUST display each due occurrence's state per slot rather than only the collapsed day-level status.

A pending slot (`UNKNOWN`) MUST display its three answer actions (Yes, No, Skip). An answered slot (`COMPLETED`, `MISSED`, or `SKIPPED`) MUST instead display text naming its specific answer (Done, Missed, or Skipped — never a generic "answered" label) plus exactly one control to change it, never relying on colour or position alone. That control MUST be reachable and operable without a gesture, with an accessible label distinguishing its slot from every other slot on screen. This applies identically to the single-slot row and an expanded multi-slot row; the day-level rollup row is unaffected and out of scope.

While displayed, the today screen MUST track the current local date rather than the date at construction. Crossing local midnight MUST re-query and re-render the per-slot display and rollup against the new date, with no user interaction. If backgrounded across midnight, resuming MUST re-read the current local date and correct the display by that resume. This obligation covers midnight rollover while displayed and resume after backgrounding; it excludes a timezone change while continuously foregrounded, a named, accepted scoping boundary.
(Previously: covered per-slot display and answer controls only, with no obligation to track the local date across midnight.)

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

#### Scenario: Today screen rolls over at local midnight while displayed

- GIVEN the today screen is open showing 2026-09-01, with a habit due that date
- WHEN local midnight passes into 2026-09-02 while the screen stays open
- THEN the screen re-queries and re-renders 2026-09-02's occurrences and rollup, with no user action

#### Scenario: A backgrounded app corrects the date on resume

- GIVEN the screen showed 2026-09-01 when backgrounded, and midnight has since passed into 2026-09-02
- WHEN the user resumes the app
- THEN the screen re-reads the current date and renders 2026-09-02's occurrences and rollup

#### Scenario: Foregrounded timezone travel remains a known, accepted gap

- GIVEN the today screen is continuously foregrounded, never backgrounded
- WHEN the device's timezone changes such that midnight has already passed in the new zone
- THEN the screen MAY keep showing the prior date until the user leaves and returns; this gap is an accepted scoping boundary, not a defect
