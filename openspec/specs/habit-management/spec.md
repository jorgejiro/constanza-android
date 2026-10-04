# Habit Management Specification

## Purpose

Defines creation, editing, and archiving of `Habit` records and their attached `Schedule`, independent of persistence or UI technology.

## Requirements

### Requirement: Habit Creation

The system MUST allow creating a `Habit` with a name, optional colour, optional notes, and
exactly one attached `Schedule` of any supported frequency kind.
(Previously: also required an optional guiding question field, removed from the product because a
habit's name already is the question. This annotation was itself deleted in error during that
change's archive, on the mistaken reasoning that a main spec should carry no history — this repo's
convention is the opposite, and every modified requirement here records what it previously said.)

#### Scenario: Create a daily habit
- GIVEN no existing habits
- WHEN the user creates a habit named "Drink water" with a DAILY schedule
- THEN a new `Habit` exists with that name and a DAILY `Schedule`

#### Scenario: Creation requires a name
- GIVEN the user leaves the name field empty
- WHEN they attempt to save the habit
- THEN the system MUST reject the save and MUST NOT create a `Habit`

### Requirement: Habit Editing

The system MUST allow editing a habit's name, colour, notes, and attached `Schedule` (including changing frequency kind) without deleting existing `Entry` history.

Editing the `Schedule` MUST be treated as an in-app schedule edit and MUST trigger an immediate reminder reschedule (see `reminder-delivery`).

#### Scenario: Editing the schedule reschedules reminders
- GIVEN a habit with a WEEKLY schedule and pending reminders
- WHEN the user changes it to DAILY
- THEN existing pending reminders for the old schedule are cancelled and new ones are armed for DAILY

#### Scenario: Editing preserves past entries
- GIVEN a habit with 10 days of recorded entries
- WHEN the user edits its name
- THEN all 10 `Entry` records remain unchanged and queryable

### Requirement: Habit Deletion

The system MUST support permanently deleting a `Habit` together with its `Schedule`, all its
`ReminderSlot`s, all its `Entry` records, and all its reminder occurrence records. Deletion MUST be
irreversible in-app: none of the deleted records MUST be recoverable through any in-app action after
deletion completes. The only recovery path is importing a previously exported backup, per the
`data-portability` `Import` requirement's full-replace guarantee.

Before deletion completes, the user MUST be shown a confirmation that names the habit and states the
exact number of recorded `Entry` records that will be destroyed, stating zero explicitly when the
habit has no history. Deletion MUST NOT proceed without confirmation, and declining the confirmation
MUST leave the habit and all its records unchanged.

After a habit is deleted, no reminder MUST fire for that habit again, regardless of whether a reminder
was armed for it at the moment of deletion.

Deletion MUST behave identically for a habit with entry history and a habit with none: only the
confirmation's stated count differs, never the outcome.

Deletion is a distinct operation from archiving. Deleting a habit MUST NOT read or modify the archived
flag, and MUST NOT alter archiving's behavior for any other habit.

#### Scenario: Deleting a habit with history removes it and all its records
- GIVEN a habit with a schedule, reminder slots, and 10 recorded entries
- WHEN the user confirms deletion
- THEN the habit, its schedule, its reminder slots, its entries, and its reminder occurrences no
  longer exist

#### Scenario: Deleting a habit with no history behaves the same as one with history
- GIVEN a newly created habit with zero recorded entries
- WHEN the user confirms deletion
- THEN the habit, its schedule, and its reminder slots no longer exist, and the confirmation shown
  before the user confirmed stated 0 recorded answers

#### Scenario: Confirmation states the exact recorded-answer count
- GIVEN a habit with 7 recorded entries
- WHEN the user opens the delete confirmation for that habit
- THEN the dialog names the habit and states exactly 7 recorded answers will be destroyed

#### Scenario: Declining the confirmation changes nothing
- GIVEN the delete confirmation is shown for a habit
- WHEN the user declines it
- THEN the habit, its schedule, its entries, and its reminder occurrences all remain unchanged

#### Scenario: No reminder fires for a deleted habit
- GIVEN a habit with a reminder armed for a future occurrence
- WHEN the user deletes that habit
- THEN no reminder for that habit is delivered afterward

#### Scenario: Deletion does not affect archiving
- GIVEN two habits, one archived and one active
- WHEN the active habit is deleted
- THEN the archived habit's archived state and entry history remain unchanged, and it continues to
  be excluded from compliance exactly as before

### Requirement: Habit Archiving

The system MUST support archiving a habit as a reversible flag. Archiving MUST NOT delete the habit,
its `Schedule`, its `ReminderSlot`s, or any `Entry` history: archiving preserves every record it
touches. Deleting a habit is a separate operation, defined by the Habit Deletion requirement, and
archiving MUST NOT be implemented as, and MUST NOT be conflated with, that deletion.

An archived habit MUST stop firing reminders and MUST be excluded from streak and compliance
calculations for any date on or after the archive date, while `Entry` history before that date MUST
remain intact and queryable. Un-archiving MUST resume reminder scheduling from the moment of
un-archival, without back-filling reminders for dates missed while archived.

#### Scenario: Archiving stops reminders
- GIVEN an active habit with an armed reminder
- WHEN the user archives it
- THEN the pending reminder is cancelled and no future reminder is armed

#### Scenario: Archived habit excluded from compliance going forward
- GIVEN a habit archived on 2026-09-10 with prior history
- WHEN compliance is computed for a window spanning before and after 2026-09-10
- THEN dates on or after 2026-09-10 are excluded from the calculation, and dates before remain included

#### Scenario: Un-archiving does not back-fill missed slots
- GIVEN a habit archived for 5 days
- WHEN the user un-archives it
- THEN reminders resume from now onward and no `Entry` is retroactively created for the archived window

### Requirement: Habit Colour Palette

The system MUST offer a palette of exactly twelve standard, muted, mutually distinguishable colours
in the habit colour picker, and MUST additionally allow any colour to be chosen freely. A habit's
colour is shown as a small identity dot beside its name, never as the name's text colour, so the
palette is chosen for calm on the neutral graphite ground (see `visual-design-system`): desaturated
tones across the hue families a person names (greens, blues, sands, reds, violets and neutrals), no
two of which read as the same colour.

Every offered colour MUST clear the non-text contrast floor (3:1) against every surface the dot is
drawn on. A freely chosen colour MUST be produced inside the custom-colour band the visual design
system defines, which guarantees that same floor and keeps it no louder than the lightest preset.

(Previously: a palette of twenty-one saturated colours chosen for breadth of coverage, painted on
the habit's name text and therefore held to a `[7:1, 11:1]` text band; before that, exactly six
colours, every one a member of the warm-dark palette.)

(Previously: required exactly six colours, every one a member of the warm-dark palette. That
constraint was the defect rather than the selection: confined to the accent's lightness band, whole
families were unreachable — a pastel brown is beige and a pastel navy is just blue — and the two
closest swatches measured only 23.9 apart. Free choice and the collapsed-row obligations did not
exist.)

The picker MUST open collapsed, showing a single row of five colours plus the free-choice
affordance, and MUST offer a way to reveal the remaining seven. The colours in that collapsed row MUST be chosen for
maximum mutual distinguishability rather than being the first N of the full set, and the default
colour for a new habit MUST be one the collapsed row draws.

#### Scenario: Picker opens collapsed and can be expanded
- GIVEN the colour picker shown during habit creation or editing
- WHEN the user opens it
- THEN a single row of standard colours is shown alongside a free-choice affordance, and the
  remaining colours are revealed only after the user asks for them

#### Scenario: A new habit's default colour is visible without expanding
- GIVEN habit creation is started and no colour has been chosen
- WHEN the picker renders
- THEN the pre-selected colour is one drawn in the collapsed row, never one reachable only by
  expanding and never the free-choice affordance

#### Scenario: Any colour can be chosen freely
- GIVEN the colour picker
- WHEN the user chooses the free-choice affordance and picks a colour that is not in the offered
  palette
- THEN that colour is accepted and persisted unchanged

### Requirement: A Persisted Habit Colour Survives A Palette Change

A habit's persisted colour MUST survive unchanged when the offered palette changes, and MUST remain
selectable and visible in the picker even when it is no longer an offered colour. The system MUST
NOT rewrite a habit's colour merely because the offered palette moved beneath it.

(Previously: titled "Persisted Habit Colour Stays On-Palette Across A Palette Change" and required
the exact opposite — every already-persisted colour MUST be rewritten onto the new palette, by a
one-to-one mapping, so that no habit was left holding an off-palette colour.)

That obligation existed because an off-palette colour was unreachable and therefore an orphan: a
habit holding one could not be re-selected in a picker that did not contain it. Free choice removes
that premise. An off-palette colour is now an ordinary, representable state, and rewriting a
colour the user had deliberately chosen would be data loss rather than repair.

The one-to-one rewrite behaviour is retained where it is still needed: importing a backup whose
declared schema version predates the first palette change (see `data-portability`).

**Exception: design-system repaints.** When the design system itself retires the offered presets,
a database migration MUST rewrite every habit still holding a retired *preset* onto its designated
counterpart in the new palette, by a fixed table, and backup import MUST apply the same table to
files older than that repaint. Colours that were never presets are not touched by such a repaint.
This is what happened at database version 8 (graphite redesign): the 21 legible-band presets were
mapped onto the 12 muted ones by nearest perceptual distance within the same hue family, so a
habit the user had put on "green" stays green. A preset is a choice of *family* made from a list the
app offered; a repaint of that list carries the choice forward instead of orphaning it. (The
earlier legible-band re-tone and `BLUE_GREY` retirement, database versions 5 and 6, were repaints
of the same kind; they additionally pulled out-of-band custom colours into the then-required text
band.)

#### Scenario: A retired preset is repainted onto its muted counterpart
- GIVEN a habit persisted on one of the retired legible-band presets
- WHEN the app upgrades its database to version 8
- THEN the habit holds the muted preset that preset maps to, and the picker shows it selected

#### Scenario: A custom colour survives a design-system repaint
- GIVEN a habit persisted with a freely chosen colour that was never an offered preset
- WHEN the app upgrades its database to version 8
- THEN the habit's colour is unchanged

#### Scenario: An existing habit keeps its colour after the palette widens
- GIVEN a habit persisted with a colour that the newly offered palette does not contain
- WHEN the palette change ships and the user opens that habit for editing
- THEN the habit's colour is unchanged, and the picker shows it as the current selection rather
  than showing nothing selected

### Requirement: Habit Colour Visible Where Habits Are Listed

A habit's colour MUST be rendered as a visible identity marker on every screen where habits are
listed, not only within the habit editor.

#### Scenario: Colour visible on the today screen
- GIVEN a habit with an assigned colour and a due occurrence today
- WHEN the user views the today screen
- THEN that habit's colour is visibly rendered alongside its entry

#### Scenario: Colour visible on the habit list
- GIVEN two habits with different assigned colours
- WHEN the user views the habit list screen
- THEN each habit's row visibly displays its own colour, distinguishing the two habits from each
  other

### Requirement: Habit List Row Actions And Name Display

The habit list row's trailing content MUST show only one overflow menu launcher; it MUST NOT
render Progress, Archive/Un-archive, or Delete as always-visible buttons. Opening the menu MUST
offer Progress, Archive or Un-archive (matching the habit's current state), and Delete as items.
The habit's name MUST wrap across up to two lines before truncating, and MUST be ellipsized only
once both lines are full.

#### Scenario: The row shows only the overflow launcher
- GIVEN a habit list row
- WHEN it renders
- THEN Progress, Archive/Un-archive, and Delete are not shown as always-visible buttons, and the
  row shows a single overflow menu launcher

#### Scenario: The overflow menu offers all three actions
- GIVEN a habit list row with the overflow menu closed
- WHEN the user opens it
- THEN Progress, Archive or Un-archive, and Delete all appear as menu items

#### Scenario: A long name wraps onto a second line before truncating
- GIVEN a habit name long enough to need two lines at the row's rendered width but no more
- WHEN the row renders
- THEN the full name is visible across two lines with no ellipsis

#### Scenario: A name exceeding two lines is ellipsized
- GIVEN a habit name that would need more than two lines at the row's rendered width
- WHEN the row renders
- THEN the name is capped at two lines and ends with an ellipsis
