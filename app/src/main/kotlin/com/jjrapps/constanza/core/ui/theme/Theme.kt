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
 * identical choice: this app's chrome is achromatic — the habit's own colour is the app's only
 * chroma — so a distinct "secondary" hue would contradict that.
 *
 * **`onPrimaryContainer` no longer mirrors `onSecondaryContainer`/`onSurface` — corrected here, same
 * root cause as the `outline` split one paragraph up.** `primaryContainer` and
 * `surfaceContainerHighest` were bound to one tone AND `onPrimaryContainer` and `onSurface` to
 * another, so any M3 component that tells a selected part from an unselected one using those two
 * pairs rendered both parts pixel-identical. That is not hypothetical: it is what M3's time-picker
 * selector did on a real screenshot, leaving nothing on screen to say which of the hour/minute
 * halves was being edited. `onPrimaryContainer` now reads [ConstanzaColors.ChromeInteractive] — a
 * selection indicator is precisely what that token's own KDoc says it draws.
 *
 * The container *fill* is deliberately left at [ConstanzaColors.SurfaceSelected] rather than split
 * too, and the reason is arithmetic rather than taste: for a selected fill to clear 3:1 against
 * `surfaceContainerHighest` (relative luminance 0.0092) it would need luminance >= 0.1276, which in
 * this ramp is a light grey slab — brighter than the control stroke and far outside a deliberately
 * quiet dark app. No fill-carried distinction exists here at that strength, so the distinction is
 * carried by the content tone. Note the honest limit of that: [ConstanzaColors.ChromeInteractive]
 * against [ConstanzaColors.OnBackground] is a 1.57:1 *luminance* difference, and with the chrome
 * achromatic that difference is now the whole signal — no hue is left to carry any of it. The amber
 * this replaced measured 1.72:1 on the same pair but separated mainly by hue, so the brightness step
 * is barely changed while the hue step is gone entirely. SC 1.4.11 imposes no state-to-state ratio
 * and both tones clear their own container by more than 4.5:1, so this is conformant, but a
 * component that must distinguish two states should add a non-colour cue of its own rather than
 * lean on this pair alone.
 *
 * **`tertiaryContainer`/`onTertiaryContainer` are bound as of fix/time-format-consistency, and the
 * audit below no longer lists them.** They used to be audited as unbound on the grounds that
 * nothing rendered them, and `habit.ReminderTimeField` hardcoded its time picker to 24-hour partly
 * to keep that true. Now that the picker follows the device's 12/24-hour setting, its AM/PM period
 * selector renders — and in material3 1.4.0 that selector is the only consumer of the tertiary
 * family in either `TimePickerTokens` or `TimeInputTokens`
 * (`PeriodSelectorSelectedContainerColor` plus the four `PeriodSelectorSelected*LabelTextColor`
 * entries, read from the resolved artifact's sources). Left unbound, a US phone set to 12-hour
 * would have put M3's stock violet in the middle of the warm ramp.
 *
 * **They no longer take the same content tone as `primaryContainer`/`onPrimaryContainer`, and that
 * asymmetry is forced rather than chosen.** Both roles mean "this half of a two-part selector is the
 * one you picked", the AM/PM toggle sitting a few dp from the hour/minute selector inside the same
 * dialog, so one treatment for one idea is what you would want — and it is how these were bound
 * while the chrome still had an accent. It stopped being reachable when the accent went, because M3
 * gives the two selectors *different unselected baselines*: the hour/minute half that is not being
 * edited draws in `onSurface` ([ConstanzaColors.OnBackground]), while the unselected AM/PM label
 * draws in `onSurfaceVariant` ([ConstanzaColors.OnBackgroundVariant]). One accent could differ from
 * both of those at once, by hue. An achromatic tone cannot: brightness is the only axis left, so
 * each selected tone has to step away from *its own* sibling, and the two siblings sit at different
 * brightnesses. Hence `onPrimaryContainer` steps down to [ConstanzaColors.ChromeInteractive] while
 * `onTertiaryContainer` steps up to [ConstanzaColors.OnBackground].
 *
 * Both land on the same 1.57:1 luminance separation from the half they must be told apart from, and
 * the AM/PM pair is the one that gains: amber against `onSurfaceVariant` measured 1.10:1 and was very
 * nearly pure hue, which is exactly the distinction that would have vanished here.
 * `ColorContrastTest` pins both pairs apart rather than trusting this paragraph.
 *
 * The selection is still carried by the *content* tone rather than the fill, which is the trade
 * `onPrimaryContainer`'s paragraph above already spells out and which the period selector survives
 * better than most: M3 strokes the whole toggle with `outline` ([ConstanzaColors.ControlStroke],
 * 3.41:1 on the dialog's `surfaceContainerHigh`), so the component's own boundary is never in
 * question — only which half is active.
 *
 * `tertiary`/`onTertiary` follow `secondary`/`onSecondary` onto the achromatic pair. Nothing
 * renders them today; they are bound anyway so that no member of this family can put a violet on
 * screen later, which is exactly the failure this change had to go back and fix.
 *
 * **Audited and deliberately left at M3's default — stated here so no future omission is silent:**
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
    primary = ConstanzaColors.ChromeInteractive,
    onPrimary = ConstanzaColors.OnChromeInteractive,
    primaryContainer = ConstanzaColors.SurfaceSelected,
    onPrimaryContainer = ConstanzaColors.ChromeInteractive,
    secondary = ConstanzaColors.ChromeInteractive,
    onSecondary = ConstanzaColors.OnChromeInteractive,
    secondaryContainer = ConstanzaColors.SurfaceSelected,
    onSecondaryContainer = ConstanzaColors.OnBackground,
    tertiary = ConstanzaColors.ChromeInteractive,
    onTertiary = ConstanzaColors.OnChromeInteractive,
    tertiaryContainer = ConstanzaColors.SurfaceSelected,
    onTertiaryContainer = ConstanzaColors.OnBackground,
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
