# Delta for Habit Management

## MODIFIED Requirements

### Requirement: Habit Creation

The system MUST allow creating a `Habit` with a name, optional colour, optional notes, and
exactly one attached `Schedule` of any supported frequency kind.
(Previously: also required an optional guiding question field, now removed from the product.)

#### Scenario: Create a daily habit
- GIVEN no existing habits
- WHEN the user creates a habit named "Drink water" with a DAILY schedule
- THEN a new `Habit` exists with that name and a DAILY `Schedule`

#### Scenario: Creation requires a name
- GIVEN the user leaves the name field empty
- WHEN they attempt to save the habit
- THEN the system MUST reject the save and MUST NOT create a `Habit`

## ADDED Requirements

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
