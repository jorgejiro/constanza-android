package com.jjrapps.constanza.core.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * `OutlinedButtonTokens.DisabledContainerOpacity` in material3 1.4.0, restated here because the
 * token is `internal`. Matching it keeps a disabled control looking exactly as Material intends.
 */
private const val DISABLED_BORDER_ALPHA = 0.1f

/**
 * `FilterChipTokens.FlatUnselectedOutlineWidth` in material3 1.4.0, restated for the same reason and
 * applied to the selected state too, whose own token is `0.dp` (Material draws no selected border).
 */
private val ChipBorderWidth = 1.dp

/**
 * Borders for the controls whose Material 3 defaults would otherwise draw them invisibly in this
 * palette. Every outlined control in the app should take its stroke from here rather than from
 * `ButtonDefaults`/`FilterChipDefaults` directly.
 *
 * **Why this object has to exist.** [Theme.kt][DarkColors] binds M3's two stroke roles by job:
 * `outline` is the control stroke ([ConstanzaColors.ControlStroke], 3.81:1 on the background) and
 * `outlineVariant` is the decorative hairline ([ConstanzaColors.Divider], 1.26:1, which WCAG 2.1
 * SC 1.4.11 permits for decoration). That split is correct and is what makes the `Switch` thumb
 * visible again. It is not, however, sufficient, because material3 1.4.0 does not honour the same
 * division: verified in the resolved artifact's sources, `outlineVariant` backs `DividerTokens.Color`
 * (decorative, fine) but *also* `OutlinedButtonTokens.OutlineColor` and
 * `FilterChipTokens.FlatUnselectedOutlineColor` — two operable controls, squarely in scope for the
 * 3:1 floor. M3 Expressive moved the chip/outlined-button/outlined-card family off `outline`.
 *
 * **Why the fix is here and not in the theme.** One token cannot do both jobs in a ramp this dark,
 * and the reason is arithmetic rather than taste: clearing 3:1 against [ConstanzaColors.Background]
 * requires relative luminance >= 0.111, while a hairline that still reads as a hairline sits near
 * 0.017. Raising `outlineVariant` to the floor would drag every future `HorizontalDivider` up with
 * it; lowering `outline` would undo the fix. M3's own dark baseline does not resolve the tension
 * either — its `outlineVariant` measures 1.99:1, under the floor. So the roles stay split by job and
 * the controls Material routes through the wrong one are pointed back by hand, in one place.
 *
 * **What guards this, and which guard catches what.** `ColorContrastTest` asserts that the *tones*
 * clear their floors, and it reads the real `ColorScheme` so it catches a role being rebound. It
 * cannot see a call site that forgets to come here, and two guards were added for that afterwards
 * because neither covers it alone. `config/detekt/detekt.yml` forbids the eight Material default
 * border factories that resolve to `outlineVariant`, with this file as the single exemption — that
 * catches a border sourced from the wrong place. `ControlStrokeCallSiteTest` requires every call to
 * a guarded outlined composable to pass `border = ConstanzaControlDefaults.…` — that catches a
 * border never sourced at all, which the detekt ban provably cannot see, since a call site that
 * simply omits the argument names nothing forbidden.
 */
object ConstanzaControlDefaults {

    /**
     * The border for an `OutlinedButton`, replacing `ButtonDefaults.outlinedButtonBorder`, whose
     * colour is `outlineVariant` in every state — enabled, focused, hovered and pressed alike.
     *
     * Disabled keeps Material's own treatment (the same tone at its `DisabledContainerOpacity`)
     * rather than being lifted to the floor: SC 1.4.11 exempts an inactive control, and a disabled
     * stepper button that looks as solid as an enabled one would be a worse lie than a faint one.
     */
    @Composable
    fun outlinedButtonBorder(enabled: Boolean): BorderStroke = BorderStroke(
        width = ButtonDefaults.outlinedButtonBorder(enabled).width,
        color = MaterialTheme.colorScheme.outline.let { stroke ->
            if (enabled) stroke else stroke.copy(alpha = DISABLED_BORDER_ALPHA)
        },
    )

    /**
     * The border for a `FilterChip`, correcting two separate things.
     *
     * *Unselected* is the same correction as [outlinedButtonBorder]: the default reads
     * `outlineVariant`, so an unselected chip was outlined in the 1.26:1 hairline and read as a bare
     * label with no chip around it.
     *
     * *Selected* is a defect the unselected fix would otherwise have introduced. Material draws a
     * selected filter chip with **no border at all** (`selectedBorderColor = Color.Transparent`,
     * `FlatSelectedOutlineWidth = 0.dp`) and lets its `secondaryContainer` fill carry the state. Here
     * that fill is [ConstanzaColors.SurfaceSelected], 1.17:1 against the background — so the moment
     * unselected chips gained a visible 3.81:1 stroke, the *selected* chip became the faintest in the
     * row, which is precisely backwards. Raising the fill is not available: 3:1 against the
     * background needs luminance >= 0.111 and would turn the chip into a light slab. So the selected
     * chip keeps its fill and gains an accent stroke at 9.50:1 against the background — a selection
     * indicator being exactly what [ConstanzaColors.Accent]'s own KDoc reserves the accent for.
     *
     * Both states carry a stroke of the same width, differing only in colour (amber vs warm grey,
     * 2.49:1 apart), so the row keeps a stable geometry and nothing reflows as the selection moves.
     */
    @Composable
    fun filterChipBorder(selected: Boolean): BorderStroke = FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = MaterialTheme.colorScheme.outline,
        selectedBorderColor = MaterialTheme.colorScheme.primary,
        borderWidth = ChipBorderWidth,
        selectedBorderWidth = ChipBorderWidth,
    )
}
