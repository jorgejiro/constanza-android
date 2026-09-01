# Delta for Habit Management

## ADDED Requirements

### Requirement: Habit Colour Palette

The system MUST offer exactly six colours in the habit colour picker, and every offered colour
MUST be a member of the current warm-dark palette (see `visual-design-system`).

#### Scenario: Picker offers exactly six colours
- GIVEN the colour picker shown during habit creation or editing
- WHEN the user opens it
- THEN exactly six colours are shown, matching the current warm-dark palette values

### Requirement: Persisted Habit Colour Stays On-Palette Across A Palette Change

When the offered habit colour palette changes, every already-persisted habit's colour MUST be
rewritten so it remains a member of the current palette; the system MUST NOT leave any habit
holding a colour absent from the current palette. The rewrite MUST be a one-to-one mapping (no two
distinct previous colours collapse onto the same new colour). Where a previous colour's hue has no
same-hue counterpart in the new palette, it MUST map to the one remaining unclaimed colour in the
new palette rather than collapse onto another habit's colour.

#### Scenario: Existing habit keeps a same-family colour
- GIVEN a habit persisted with a colour from the old palette that has a same-hue counterpart in
  the new palette (e.g. teal, blue, red, purple, or green)
- WHEN the palette change is applied
- THEN the habit's colour is rewritten to the corresponding new-palette colour of the same family

#### Scenario: Orange changes colour family to pink
- GIVEN a habit persisted with the old orange colour, whose hue has no counterpart in the new
  palette because that hue is reserved for the accent
- WHEN the palette change is applied
- THEN the habit's colour is rewritten to pink — the one colour in six whose family changes — and
  it remains distinguishable from the other five habit colours

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
