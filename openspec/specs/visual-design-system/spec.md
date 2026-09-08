# Visual Design System Specification

## Purpose

Defines the app's single dark visual scheme, the accessibility contract for every colour it
renders, and cold-start rendering behaviour. Exact hex/oklch token values, spacing scale, and
shape scale are implementation detail and belong in `design.md`, not here.

## Requirements

### Requirement: Dark-Only Rendering

The system MUST render exclusively in one fixed dark colour scheme. The system MUST NOT provide a
light colour scheme, MUST NOT derive any colour from the device wallpaper (dynamic/"Material You"
colour), and MUST NOT vary its colour scheme based on the device's system-wide light/dark setting.

#### Scenario: App ignores system light mode
- GIVEN the device's system-wide appearance setting is light
- WHEN the app is launched
- THEN the app renders in the fixed dark scheme, not a light one

#### Scenario: App ignores wallpaper-derived dynamic colour
- GIVEN the device wallpaper would produce a dynamic ("Material You") colour palette
- WHEN the app is launched
- THEN the app's chrome and habit colours match the fixed palette, not a wallpaper-derived one

### Requirement: Habit Colour Contrast Floor

Every colour a habit's identity may take MUST meet a contrast ratio of at least 4.5:1 against
both the app's background surface and its raised/selected surface. This applies to the offered
presets and to any colour reachable through the free custom-colour picker alike, because a habit's
colour is rendered as its name's text colour and not merely as a swatch beside it.

#### Scenario: Sub-floor colour is rejected
- GIVEN a candidate colour measuring below 4.5:1 against the app background
- WHEN it is evaluated against this floor
- THEN it MUST NOT be offered as a habit colour

#### Scenario: Ratified palette clears the floor
- GIVEN every ratified habit colour
- WHEN each is measured against the background and the raised/selected surface
- THEN every measurement is at or above 4.5:1 on both surfaces

#### Scenario: A custom colour below the floor is brought up to it
- GIVEN the user picks a custom colour measuring below the floor against the app background
- WHEN that colour is committed to the habit
- THEN the stored colour is one inside the legible band, with the picked hue preserved

### Requirement: Habit Colour Is The Only Chroma

The app's chrome MUST be achromatic: no saturated accent may be used for app bars, selection
indicators, primary controls or any other chrome role. A habit's own colour is the only chroma the
interface carries, apart from the two reserved semantic tones that mark an answer as done or not
done.

This supersedes the retired requirement `Accent Reserved For Chrome`, which required the opposite
arrangement — a single reserved chrome accent, excluded from the habit palette. That accent was
removed because it competed with the habit colours it sat beside, and because reserving it cost the
habit palette the whole neighbourhood of its hue.

#### Scenario: No chrome role resolves to a saturated colour
- GIVEN the app's colour scheme
- WHEN each chrome role is inspected
- THEN none of them resolves to a saturated colour

#### Scenario: Every hue is available to a habit
- GIVEN the set of colours offered when creating or editing a habit
- WHEN the user opens the colour picker
- THEN no colour is withheld from it on the grounds of being reserved for chrome

### Requirement: Cold-Start Window Background And System Bar Icons

The pre-Compose window background MUST match the app's dark surface colour. System-bar icon
appearance MUST be pinned to the style appropriate for a dark background, regardless of the
device's system-wide light/dark setting.

#### Scenario: No light flash on cold start
- GIVEN the app process is not yet running
- WHEN the user launches the app
- THEN the first rendered frame shows the dark surface colour, never a light background

#### Scenario: System-bar icons stay legible when the device is set to light mode
- GIVEN the device's system-wide appearance setting is light
- WHEN the app is in the foreground
- THEN the status and navigation bar icons render in dark-background style, not the device's
  light-mode style

### Requirement: Contrast Floors Asserted By Automated Test

The contrast floor MUST be asserted by an automated test runnable in the JVM unit test suite, not
documented only. The test MUST fail if any offered habit colour drops below 4.5:1 against either
the background or the raised/selected surface.

#### Scenario: Automated test fails a sub-floor colour
- GIVEN a colour value below 4.5:1 against the app background is introduced into the palette
- WHEN the automated contrast test suite runs
- THEN the test suite fails

#### Scenario: Automated test passes the ratified palette
- GIVEN every ratified habit colour
- WHEN the automated contrast test suite runs
- THEN the test suite passes with no floor violation
