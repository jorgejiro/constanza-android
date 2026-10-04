# Visual Design System Specification

## Purpose

Defines the app's single dark visual scheme — a neutral "graphite" ramp of near-achromatic greys —
its bundled typeface, the accessibility contract for every colour it renders, and cold-start
rendering behaviour. Exact hex token values, the type scale, spacing scale, and shape scale are
implementation detail and live in `core/ui/theme/` (`ConstanzaColors`, `Type.kt`, `Shape.kt`), not
here.

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

A habit's colour is rendered as a small identity dot beside its name — a non-text graphic — and the
habit's name is rendered in the text colour, never in the habit's colour. Every colour a habit's
identity may take MUST therefore meet the WCAG 2.1 SC 1.4.11 non-text floor of at least 3:1 against
the background, the surface and the raised surface. This applies to the offered presets and to any
colour reachable through the free custom-colour picker alike.

The custom-colour picker MUST produce colours inside a band measured against the background: a
floor of 3.6:1, which is what clears 3:1 on the raised surface (the lightest surface a dot is drawn
on), and a ceiling of 9:1, just above the lightest preset, so that no custom colour is louder than
the muted palette.

(Previously: a habit's colour was its name's text colour, so every colour was held to 4.5:1 and the
presets to a `[7:1, 11:1]` band.)

#### Scenario: Sub-floor colour is rejected
- GIVEN a candidate preset measuring below 3:1 against any of the background, surface or raised
  surface
- WHEN it is evaluated against this floor
- THEN it MUST NOT be offered as a habit colour

#### Scenario: Ratified palette clears the floor
- GIVEN every ratified habit colour
- WHEN each is measured against the background, the surface and the raised surface
- THEN every measurement is at or above 3:1

#### Scenario: A custom colour is produced inside the band
- GIVEN the user mixes a custom colour at any hue, saturation and slider position
- WHEN that colour is committed to the habit
- THEN it measures between 3.6:1 and 9:1 against the background, with the picked hue preserved,
  and at least 3:1 against the raised surface

#### Scenario: The habit name is not painted in the habit colour
- GIVEN a habit with any colour
- WHEN its name is rendered on a list or on the today screen
- THEN the name uses the text colour and the habit colour appears only as the identity dot

### Requirement: Habit Colour Is The Only Chroma

The app's chrome MUST be achromatic: no saturated accent may be used for app bars, selection
indicators, primary controls, answer controls or any other chrome role. A habit's own colour — as
its identity dot — is the only chroma the interface carries, with one exception: the destructive
tone, a desaturated warm red used only for destructive actions and errors. Answer controls and
answered-state glyphs are neutral. The reminder notification's accent is the neutral interactive
tone (#ECECEE) for every habit, not the habit's colour.

This supersedes the retired requirement `Accent Reserved For Chrome`, which required the opposite
arrangement — a single reserved chrome accent, excluded from the habit palette. That accent was
removed because it competed with the habit colours it sat beside. (Previously this requirement
also reserved two semantic tones for "done" and "not done" answers; the graphite redesign makes
answers neutral.)

#### Scenario: No chrome role resolves to a saturated colour
- GIVEN the app's colour scheme
- WHEN each chrome role is inspected
- THEN none of them resolves to a saturated colour, the destructive tone excepted

#### Scenario: The reminder notification carries the neutral accent
- GIVEN a habit whose colour is any palette or custom colour
- WHEN its reminder notification is posted
- THEN the notification's accent colour is the neutral interactive tone, not the habit's colour

#### Scenario: Every hue family is available to a habit
- GIVEN the set of colours offered when creating or editing a habit
- WHEN the user opens the colour picker
- THEN no colour is withheld from it on the grounds of being reserved for chrome

### Requirement: Chrome Text And Control Contrast Floors

Chrome text and operable-control tones MUST meet these contrast ratios against the background, the
surface and the raised surface: primary text at least 12:1, secondary text at least 7:1, muted
labels and the destructive tone at least 4.5:1. The stroke that draws an operable control (switch
thumbs and track borders, outlined-field borders, unselected chip outlines) MUST meet at least 3:1
(WCAG 2.1 SC 1.4.11) against every surface such a control can sit on. A purely decorative divider
is exempt and MUST stay quieter than the control stroke. Where a control marks its selected part by
fill alone (the time picker's hour/minute and AM/PM halves), the selected and unselected fills MUST
be at least 3:1 apart, and each part's numerals at least 4.5:1 on their own fill.

#### Scenario: A design tone below its floor is nudged, not shipped
- GIVEN a tone from the approved design that measures below its floor on any of those surfaces
- WHEN the theme is built
- THEN the shipped token is the minimal same-cast lighter step that clears the floor

#### Scenario: The time picker's selected half is distinguishable
- GIVEN the reminder-time picker with its hour half selected
- WHEN the hour and minute halves are compared
- THEN their fills measure at least 3:1 apart and each half's numerals at least 4.5:1 on their fill

#### Scenario: Divider and control stroke stay separate
- GIVEN the app's colour scheme
- WHEN the control-stroke role and the divider role are inspected
- THEN they resolve to different tones and the divider measures lower against the background

### Requirement: Bundled Typeface

The app MUST render its text in Geist, bundled in the APK under its SIL Open Font License, and MUST
NOT depend on a downloadable-font provider or any network fetch to obtain it, because builds without
Google Play Services must render identically.

#### Scenario: Typeface renders offline without Play Services
- GIVEN a device with no network and no Google Play Services
- WHEN the app is launched
- THEN its text renders in Geist

### Requirement: Cold-Start Window Background And System Bar Icons

The pre-Compose window background MUST match the app's dark background colour exactly. System-bar
icon appearance MUST be pinned to the style appropriate for a dark background, regardless of the
device's system-wide light/dark setting. The launcher icon's background layer is not bound by this
requirement and MAY keep its own colour.

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

Every contrast floor this specification states MUST be asserted by an automated test runnable in
the JVM unit test suite, not documented only: the habit-colour non-text floor for every preset on
the background, surface and raised surface; the custom-colour band and its 3:1 floor on the raised
surface; the chrome text floors (primary, secondary, muted, destructive) on every text surface; the
control-stroke floor; and the divider staying quieter than the control stroke. The test MUST fail if
any of them is violated.

#### Scenario: Automated test fails a sub-floor colour
- GIVEN a preset below 3:1 against any habit-dot surface is introduced into the palette
- WHEN the automated contrast test suite runs
- THEN the test suite fails

#### Scenario: Automated test passes the ratified palette
- GIVEN every ratified habit colour and chrome tone
- WHEN the automated contrast test suite runs
- THEN the test suite passes with no floor violation
