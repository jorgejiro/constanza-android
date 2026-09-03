# App Localization Specification

## Purpose

Defines the language the app presents itself in: device-locale resolution, the supported
English/Spanish set, the in-app override and its persistence, and the requirement that every
user-visible string — including one produced by a cold background process — renders in the
resolved language.

## Requirements

### Requirement: Device-Locale Resolution

When no explicit override is stored, the system MUST resolve its display language from the
device's current locale. Any `es-*` device locale MUST resolve to Spanish. Any `en-*` device
locale MUST resolve to English.

#### Scenario: Spanish device locale resolves to Spanish
- GIVEN no language override is stored
- WHEN the device locale is any `es-*` variant
- THEN the app presents itself in Spanish

#### Scenario: English device locale resolves to English
- GIVEN no language override is stored
- WHEN the device locale is any `en-*` variant
- THEN the app presents itself in English

### Requirement: Supported Language Set And Universal Fallback

The system MUST support exactly two languages, English and Spanish. Any device locale that is
neither `es-*` nor `en-*` MUST resolve to English, which serves as the universal fallback.

#### Scenario: An unlisted device locale falls back to English
- GIVEN no language override is stored
- WHEN the device locale is `fr-FR`
- THEN the app presents itself in English

### Requirement: First-Install Resolution Needs No User Action

On first install, before the user takes any action and with no override ever stored, the app
MUST already present itself in the language resolved from the device locale.

#### Scenario: Fresh Spanish-locale install shows Spanish immediately
- GIVEN a fresh install on a device with an `es-*` locale, no prior launch, no stored override
- WHEN the app launches for the first time
- THEN every screen renders in Spanish with no picker interaction

### Requirement: Three-State Language Override

The system MUST offer exactly three override options, in this order: System default, English,
Español. System default MUST NOT be a third stored language: selecting it MUST leave the system
in a state observationally identical to one where no override was ever set, so that a later
change of the device language takes effect. Selecting English or Español MUST persist that
explicit choice and MUST override device-locale resolution.

#### Scenario: Selecting Español overrides an English device locale
- GIVEN a device with an `en-*` locale and no stored override
- WHEN the user selects "Español"
- THEN the app immediately renders in Spanish, regardless of the device locale

#### Scenario: Selecting System default clears the override
- GIVEN an explicit override is currently stored
- WHEN the user selects "System default"
- THEN the stored override is removed and the app resolves from the device locale again, exactly
  as if no override had ever been set

### Requirement: Override Persistence Across Process Death

An explicit language override MUST survive process death and app restart.

#### Scenario: Override survives a killed and relaunched process
- GIVEN the user selected "Español" and the override was persisted
- WHEN the process is killed and the app is relaunched
- THEN the app still renders in Spanish with no reselection needed

### Requirement: API 33+ System-Settings Parity, In-App Picker Only Below

On API 33 and above, the in-app language override MUST also be exposed as the platform's per-app
language setting, so it is changeable from Android Settings > Apps > Language, and a change made
through either surface MUST be observable through the other, including when the app was
backgrounded at the time of the system-side change. Below API 33, no such platform surface exists;
the in-app picker MUST be the sole way to change the app's language. This asymmetry is deliberate,
not a defect — the API 33 floor for per-app language settings sits above this app's `minSdk`.

#### Scenario: An in-app language change is observable in system Settings on API 33+
- GIVEN a device on API 33 or above with no stored override
- WHEN the user selects "Español" from the in-app picker
- THEN Android Settings > Apps > Language for this app reports Spanish as the selected per-app
  language

#### Scenario: A system Settings language change is observable in-app, including while backgrounded
- GIVEN a device on API 33 or above with the app currently backgrounded
- WHEN the user changes this app's per-app language to English from Android Settings > Apps >
  Language
- THEN resuming the app renders it in English with no further in-app action required

### Requirement: Every User-Visible String Renders In The Resolved Language

Every user-visible string MUST render in the resolved language, including a string produced by a
cold background process with no Activity ever created (for example, a reminder notification fired
by `AlarmManager` after process death).

#### Scenario: A cold-process reminder notification arrives in the overridden language
- GIVEN the override is set to Español and the app process has been killed
- WHEN a scheduled reminder fires with no Activity created since the kill
- THEN the posted notification's channel name, body, and action labels all render in Spanish

### Requirement: Format-Argument And Plural Integrity Under Translation

Every string carrying positional format arguments MUST render with the same argument count and the
same positional indices in both languages; translation MUST NOT drop, reorder, or duplicate a
positional argument. A literal percent sign MUST render as a literal percent sign, never consumed
as part of a format specifier. The app's one `<plurals>` resource MUST provide both the `one` and
`other` quantity forms in Spanish, each carrying its own `%1$d`, and MUST select the correct form
for a given quantity.

#### Scenario: A two-argument accessibility string preserves order and count after translation
- GIVEN `today_slot_change_a11y` is resolved in Spanish, a string carrying two positional arguments
- WHEN the string is rendered with a slot time and a habit name
- THEN both arguments appear in their original positions, with no dropped, duplicated, or
  reordered argument and no `IllegalFormatException`

#### Scenario: A literal percent sign survives translation
- GIVEN `progress_compliance` is resolved in Spanish, a string containing one interpolated value
  and one literal `%%`
- WHEN the string is rendered
- THEN the literal percent sign renders as `%`, not consumed as part of a format specifier

#### Scenario: Spanish plural selects the "one" form for quantity 1
- GIVEN `habit_delete_dialog_body` is resolved in Spanish
- WHEN the quantity is 1
- THEN the "one" quantity form is selected, with `%1$d` substituted correctly

#### Scenario: Spanish plural selects the "other" form for quantity != 1
- GIVEN `habit_delete_dialog_body` is resolved in Spanish
- WHEN the quantity is any value other than 1, including 0
- THEN the "other" quantity form is selected, with `%1$d` substituted correctly

### Requirement: Locale-Sensitive Formatting Follows The Resolved Language

Time-of-day formatting and day-of-week names MUST follow the resolved language, not the device
default, whenever the two differ.

#### Scenario: Day-of-week names follow a Spanish override on an English device
- GIVEN a device with an `en-*` locale and the override set to Español
- WHEN a screen renders a day-of-week name
- THEN it renders in Spanish, not in the device's English default
