# Habit Progress Specification

## Purpose

Defines pure, compute-on-read streak and compliance functions. Requirements here MUST be expressed framework-agnostically.

## Requirements

### Requirement: Streak Calculation

The `:domain` module MUST expose a pure function computing the current streak from `Entry` history, with no I/O. `SKIPPED` entries MUST NOT break a streak and MUST NOT themselves count as a completed day — a `SKIPPED` day passes through unaffected. `UNKNOWN` entries MUST likewise NOT break a streak and MUST NOT themselves count as a completed day — an occurrence still pending behind a live snooze passes through unaffected, exactly like `SKIPPED`. Only `MISSED` breaks a streak. An `Entry` later corrected to `COMPLETED` (from either `MISSED` or a pending `UNKNOWN`) MUST be reflected by any streak computation run after the correction.

#### Scenario: A skipped day does not break a streak
- GIVEN a streak of 5 completed days followed by 1 skipped day, followed by 3 more completed days
- WHEN the streak function evaluates today
- THEN it reports an unbroken 9-day streak

#### Scenario: An unknown day awaiting a snooze answer does not break a streak
- GIVEN a streak of 5 completed days followed by 1 day still `UNKNOWN` because its occurrence has a live, unanswered snooze
- WHEN the streak function evaluates today, before the snooze is answered
- THEN it reports an unbroken 5-day streak, with the `UNKNOWN` day passing through unaffected

#### Scenario: Streak recomputed after a late correction shows no break
- GIVEN a day currently `MISSED` inside an otherwise unbroken streak, because its occurrence was force-resolved
- WHEN a later answer corrects that day to `COMPLETED` and the streak is recomputed
- THEN the streak reports no break at that day

### Requirement: Compliance Calculation

The `:domain` module MUST expose a pure function computing compliance as `completed / (completed + missed)` over a caller-supplied window length in days — the window MUST NOT be hard-coded inside the function. `SKIPPED` and `UNKNOWN` entries MUST be excluded from both the numerator and the denominator.

(Orchestrator assumption pending user confirmation at spec review: the MVP UI evaluates this function with a 30-day rolling window; the function itself remains parameterised for other callers.)

#### Scenario: 30-day window excludes skipped days
- GIVEN a 30-day window with 20 completed, 5 missed, 5 skipped days
- WHEN compliance is computed for that window
- THEN the ratio is `20 / (20 + 5)`, with the 5 skipped days affecting neither side

#### Scenario: Different window length is independently correct
- GIVEN the same `Entry` history
- WHEN compliance is computed with a 7-day window instead of 30
- THEN the function returns a ratio based only on the last 7 days, independent of the 30-day result
