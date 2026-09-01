# UI Adaptive Layout Specification

## Purpose

Defines the minimal large-screen and orientation resilience the UI MUST provide. `targetSdk = 37`
removes the SDK 36 opt-out for large-screen orientation, resizability, and aspect-ratio constraints:
the system ignores those restrictions on any screen with `sw >= 600dp`. This is a mandatory
consequence of the target SDK, not optional polish, and it is deliberately minimal — no dedicated
tablet layouts are required in the MVP.

## Requirements

### Requirement: Minimal Adaptive Resilience

Every screen MUST NOT break, clip, overlap, or lose content when rendered at a large-screen width
(`sw >= 600dp`) or in any orientation the system permits, including a runtime orientation change
while the screen is open. This applies at minimum to the today screen and the habit create/edit
screen. Meeting this bar MUST NOT require, and the MVP MUST NOT include, any dedicated large-screen
layout such as a two-pane master/detail arrangement or a landscape-specific grid; a single responsive
layout that avoids breakage is sufficient. The absence of dedicated adaptive layouts is a deliberate
MVP scope decision, not an oversight, and MUST be revisited only as a later, explicit change.

#### Scenario: Today screen remains usable at a large-screen width
- GIVEN the today screen rendered at `sw = 600dp` or wider
- WHEN the screen displays habits due today, including multi-slot habits
- THEN no content is clipped, overlapping, or missing, even though no dedicated tablet layout is used

#### Scenario: Habit create/edit screen survives a landscape rotation
- GIVEN the habit create/edit screen open in portrait with in-progress input
- WHEN the device rotates to landscape
- THEN the screen re-renders without breaking, clipping, or losing entered content, using the same
  responsive layout rather than a dedicated landscape layout

#### Scenario: Dedicated tablet layouts are explicitly out of MVP scope
- GIVEN a reviewer evaluating whether a two-pane master/detail or landscape grid layout is missing
- WHEN they check this requirement
- THEN they find dedicated adaptive layouts explicitly deferred by design decision, not omitted by
  oversight

### Requirement: Soft Keyboard Visibility Not Assumed Across Configuration Change

On Android 17, soft-keyboard (IME) visibility MUST NOT be assumed to survive an unhandled
configuration change such as rotation. The habit create/edit screen MUST NOT rely on the keyboard
remaining visible after such a change; if keyboard visibility matters at that point, the screen MUST
request it explicitly rather than assume it persisted.

#### Scenario: Keyboard visibility is re-requested after rotation, not assumed
- GIVEN the habit create/edit screen with the soft keyboard visible while editing the name field
- WHEN the device rotates and triggers a configuration change
- THEN the screen does not assume the keyboard is still visible, and requests visibility explicitly
  if the field should remain focused and editable
