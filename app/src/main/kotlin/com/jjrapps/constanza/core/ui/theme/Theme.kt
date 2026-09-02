package com.jjrapps.constanza.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The app's single fixed dark colour scheme (spec `Dark-Only Rendering`). Built from
 * [ConstanzaColors] rather than left at M3's default `darkColorScheme()`, which is cool/violet-hued
 * (Engram #47) — every role that reads visibly in this app is repointed at the warm ramp.
 *
 * **Task 6.0 (found during unit 5, fixed here).** Unit 1 bound only 11 roles. That left `TopAppBar`,
 * `ListItem`, `ExposedDropdownMenu` (`ScheduleEditors.ScheduleKindPicker`), `AlertDialog`
 * (`DataPortabilityScreen.ImportConfirmDialog`), `FilterChip` (`ScheduleEditors.DayOfWeekPicker`) and
 * `Switch` (`ScheduleEditors.ReminderTimeEditor`) reading M3's built-in cool/violet defaults for the
 * roles below on every screen that uses them — exactly the failure this whole change exists to
 * prevent, just one layer further down than `background`/`surface`/`primary`. Reference: the sibling
 * app `sleep-noise-android`'s `Theme.kt`, which binds the same `surfaceContainer*`/`outlineVariant`
 * family from its own single-ramp palette; the mapping below mirrors it, except where noted for the
 * two `outline` roles.
 *
 * **The five-tone ramp maps onto the five `surfaceContainer*` roles one tier apart, with the two
 * lowest tiers collapsing onto [ConstanzaColors.Background].** This app has no container that reads
 * as more "recessed" than the screen it sits on — nothing in the component inventory (`ListItem`,
 * `TopAppBar`, `ExposedDropdownMenu`, `AlertDialog`) needs that distinction, confirmed by `rg`
 * across `app/src/main/kotlin` before writing this. `surfaceContainer`/`High`/`Highest` reuse
 * [ConstanzaColors.Surface]/[ConstanzaColors.SurfaceRaised]/[ConstanzaColors.SurfaceSelected] exactly
 * as the sibling app does, each one ramp step lighter than the last. [ConstanzaColors.Divider] is
 * deliberately NOT reused as a container fill (unlike a naive sixth ramp step would suggest): a
 * `FilterChip`'s unselected border reads an `outline*` role, so giving its fill the same value would
 * render an invisible border — fill and border blending into one flat colour.
 *
 * *(Correction on the record: this paragraph used to say that border reads `colorScheme.outline`. It
 * does not. `FilterChipTokens.FlatUnselectedOutlineColor` is `outlineVariant` in material3 1.4.0 —
 * M3 Expressive moved the chip/outlined-button/outlined-card family off `outline` — verified in the
 * resolved artifact's sources, not from memory. The conclusion above survives the correction, but
 * the reason it gives was wrong, and the consequences of the real assignment are in the paragraph
 * on `outlineVariant`'s control consumers further down.)*
 *
 * **`outline` and `outlineVariant` are two roles, not one — corrected here.** Both were bound to the
 * single old `ConstanzaColors.Outline` (`#28231E`, a hairline-divider tone). M3 separates them on
 * purpose and by roughly 4.5x in relative luminance: `outline` is the **control stroke** (switch
 * thumbs and unchecked track borders, unfocused `OutlinedTextField` borders, unselected chip and
 * checkbox outlines) while `outlineVariant` is the **decorative divider**. Collapsing both onto the
 * divider tone made every control in the app that strokes itself invisible *by construction* — the
 * reported symptom was the "Remind me" `Switch`, whose thumb measured 1.07:1 against its own track
 * and 1.26:1 against the page. `outline` now reads [ConstanzaColors.ControlStroke], which clears
 * WCAG 2.1 SC 1.4.11's 3:1 floor on all four surface tones; `outlineVariant` keeps the divider tone,
 * which SC 1.4.11 exempts as decorative. This is the one genuinely new hex value in the palette, and
 * it is new because no existing tone could do the job (see [ConstanzaColors.ControlStroke]).
 *
 * **`outlineVariant` is not purely decorative in material3 1.4.0, and three call sites compensate
 * for that by hand.** Verified against the resolved artifact's sources: `outlineVariant` backs
 * `DividerTokens.Color` (decorative, correct) but *also* `OutlinedButtonTokens.OutlineColor` and
 * `FilterChipTokens.FlatUnselectedOutlineColor` (both operable controls, and both therefore in scope
 * for SC 1.4.11). One token cannot serve both jobs in a ramp this dark, and the constraint is
 * arithmetic rather than aesthetic: clearing 3:1 against [ConstanzaColors.Background] takes relative
 * luminance >= 0.111, while a hairline that still reads as a hairline sits near 0.017. M3's own dark
 * baseline does not resolve this either — its `outlineVariant` measures 1.99:1, below the floor. So
 * the roles stay split by job, and the app's three operable consumers of the decorative role are
 * pointed back at `outline` where they are used: `ScheduleEditors`'s `NumberStepper` buttons and
 * `DayOfWeekPicker` chips, and `OnboardingScreen`'s inactive pager dots. Each carries its own
 * reasoning. Note the limit of the guard: `ColorContrastTest` can assert the *tones* below clear
 * their floors, but it cannot see a call site that forgets to use them, so a component added later
 * that strokes itself through `outlineVariant` will be quietly invisible again.
 *
 * **`secondary` reuses existing tones rather than introduce new hex values** (design.md decision 1's
 * "no new colour values" spirit, applied to a role addition instead of a token addition).
 * `secondary`/`onSecondary` collapse onto `primary`/`onPrimary`, mirroring the sibling app's
 * identical choice: this app has exactly one saturated accent (spec `Accent Reserved For Chrome`),
 * so a distinct "secondary" hue would contradict that requirement.
 *
 * **`onPrimaryContainer` no longer mirrors `onSecondaryContainer`/`onSurface` — corrected here, same
 * root cause as the `outline` split one paragraph up.** `primaryContainer` and
 * `surfaceContainerHighest` were bound to one tone AND `onPrimaryContainer` and `onSurface` to
 * another, so any M3 component that tells a selected part from an unselected one using those two
 * pairs rendered both parts pixel-identical. That is not hypothetical: it is what M3's time-picker
 * selector did on a real screenshot, leaving nothing on screen to say which of the hour/minute
 * halves was being edited. `onPrimaryContainer` now reads [ConstanzaColors.Accent] — a selection
 * indicator is precisely what that token's own KDoc reserves the accent for.
 *
 * The container *fill* is deliberately left at [ConstanzaColors.SurfaceSelected] rather than split
 * too, and the reason is arithmetic rather than taste: for a selected fill to clear 3:1 against
 * `surfaceContainerHighest` (relative luminance 0.0092) it would need luminance >= 0.1276, which in
 * this ramp is a light grey slab — brighter than the control stroke and far outside a deliberately
 * quiet dark app. No fill-carried distinction exists here at that strength, so the distinction is
 * carried by the content tone. Note the honest limit of that: amber-on-dark versus off-white-on-dark
 * is a ~1.7:1 *luminance* difference and reads mainly by hue. SC 1.4.11 imposes no state-to-state
 * ratio and both tones clear their own container by more than 4.5:1, so this is conformant, but a
 * component that must distinguish two states for a user who cannot separate those hues should add a
 * non-colour cue of its own rather than lean on this pair alone.
 *
 * **Audited and deliberately left at M3's default — stated here so no future omission is silent:**
 * - `tertiary`/`onTertiary`/`tertiaryContainer`/`onTertiaryContainer` — zero call sites anywhere in
 *   `app/src/main/kotlin` (`rg -i tertiary` confirms). Nothing renders a tertiary role.
 * - `error`/`onError`/`errorContainer`/`onErrorContainer` — only `colorScheme.error` is read
 *   (`ScheduleEditors`'s slot-count error text), and M3's baseline error red is not violet/cool-hued
 *   regardless of the seed primary, so it needs no repointing. No component renders a filled
 *   `errorContainer`; revisit if one is ever added.
 * - `inverseSurface`/`inverseOnSurface`/`inversePrimary` — no `Snackbar` or inverse-styled component
 *   exists in this codebase (`rg -i snackbar` finds nothing).
 * - `scrim` — dims `AlertDialog`'s backdrop; M3's default is a fixed black-with-alpha, not hue-derived,
 *   so it is already appropriate for a dark app regardless of ramp.
 * - `surfaceTint` — M3's default is a fixed violet constant, NOT auto-derived from the `primary`
 *   passed above, but every `Surface`/`Card`/`TopAppBar`/`Scaffold` in this app either sets an
 *   explicit `containerColor` or reads a now-warm container role at zero `tonalElevation`, so nothing
 *   currently blends it into a visible pixel. Left at default; revisit if a future `Card` introduces
 *   genuine tonal-elevation blending.
 * - `surfaceDim`/`surfaceBright` — zero call sites (`rg -i "surfaceDim|surfaceBright"` confirms).
 *
 * `internal` rather than `private` so `ColorContrastTest` can assert the WCAG floors against the
 * *bindings* below and not merely against the palette constants. That is not a convenience: both
 * defects corrected above were binding collisions between two roles that each held a perfectly good
 * colour, and no test that walks [ConstanzaColors] alone can see one.
 */
internal val DarkColors = darkColorScheme(
    background = ConstanzaColors.Background,
    onBackground = ConstanzaColors.OnBackground,
    surface = ConstanzaColors.Surface,
    onSurface = ConstanzaColors.OnBackground,
    surfaceVariant = ConstanzaColors.SurfaceRaised,
    onSurfaceVariant = ConstanzaColors.OnBackgroundVariant,
    surfaceContainerLowest = ConstanzaColors.Background,
    surfaceContainerLow = ConstanzaColors.Background,
    surfaceContainer = ConstanzaColors.Surface,
    surfaceContainerHigh = ConstanzaColors.SurfaceRaised,
    surfaceContainerHighest = ConstanzaColors.SurfaceSelected,
    outline = ConstanzaColors.ControlStroke,
    outlineVariant = ConstanzaColors.Divider,
    primary = ConstanzaColors.Accent,
    onPrimary = ConstanzaColors.OnAccent,
    primaryContainer = ConstanzaColors.SurfaceSelected,
    onPrimaryContainer = ConstanzaColors.Accent,
    secondary = ConstanzaColors.Accent,
    onSecondary = ConstanzaColors.OnAccent,
    secondaryContainer = ConstanzaColors.SurfaceSelected,
    onSecondaryContainer = ConstanzaColors.OnBackground,
)

/**
 * The app's Material 3 theme wrapper. **Dark-only, deliberately**: no `darkTheme` parameter, no
 * `isSystemInDarkTheme()` read, no `lightColorScheme()` anywhere in this file. The app MUST NOT vary
 * its colour scheme with the device's system-wide appearance setting or with wallpaper-derived
 * dynamic colour (spec `Dark-Only Rendering`) — a `darkTheme` seam here would be dead code by
 * construction (design.md "Migration / Rollout").
 */
@Composable
fun ConstanzaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = ConstanzaTypography,
        shapes = ConstanzaShapes,
        content = content,
    )
}
