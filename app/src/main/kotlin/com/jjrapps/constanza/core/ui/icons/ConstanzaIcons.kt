package com.jjrapps.constanza.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The graphite design's thin stroke glyphs (direction E), drawn here because `material-icons-core`
 * (the only icon artifact this project depends on) ships filled glyphs only and has no list or
 * sliders icon at all; pulling in `material-icons-extended` for a handful of shapes is not worth
 * its size.
 *
 * Each glyph is the approved board's own SVG path data on a 24x24 viewport, stroked with round
 * caps and joins in black. `Icon` tints through a colour filter, so the black never reaches the
 * screen: the caller's `tint` does.
 */
object ConstanzaIcons {

    /** Today's "habit list" action: three rows with a bullet each. */
    val List: ImageVector by lazy {
        strokeIcon("List", STROKE_REGULAR, "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01")
    }

    /** Today's "settings" action: two sliders with their knobs. */
    val Sliders: ImageVector by lazy {
        strokeIcon(
            "Sliders",
            STROKE_REGULAR,
            "M4 7h10M18 7h2M4 17h4M12 17h8M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }

    /** "Yes" and the completed glyph. */
    val Check: ImageVector by lazy { strokeIcon("Check", STROKE_HEAVY, "M5 12.5l4.5 4.5L19 7.5") }

    /** "No" and the missed glyph. */
    val Close: ImageVector by lazy { strokeIcon("Close", STROKE_MEDIUM, "M7 7l10 10M17 7L7 17") }

    /** The multi-reminder expander; the caller rotates it half a turn while expanded. */
    val ChevronDown: ImageVector by lazy { strokeIcon("ChevronDown", STROKE_REGULAR, "M6 9l6 6 6-6") }

    /** Previous day. Auto-mirrored, so it points "back" in a right-to-left layout too. */
    val ChevronStart: ImageVector by lazy {
        strokeIcon("ChevronStart", STROKE_REGULAR, "M15 6l-6 6 6 6", autoMirror = true)
    }

    /** Next day. Auto-mirrored, like [ChevronStart]. */
    val ChevronEnd: ImageVector by lazy {
        strokeIcon("ChevronEnd", STROKE_REGULAR, "M9 6l6 6-6 6", autoMirror = true)
    }
}

private const val VIEWPORT = 24f
private const val STROKE_REGULAR = 1.6f
private const val STROKE_MEDIUM = 1.8f
private const val STROKE_HEAVY = 2f

private fun strokeIcon(
    name: String,
    strokeWidth: Float,
    pathData: String,
    autoMirror: Boolean = false,
): ImageVector = ImageVector.Builder(
    name = "Constanza.$name",
    defaultWidth = VIEWPORT.dp,
    defaultHeight = VIEWPORT.dp,
    viewportWidth = VIEWPORT,
    viewportHeight = VIEWPORT,
    autoMirror = autoMirror,
).addPath(
    pathData = addPathNodes(pathData),
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = strokeWidth,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
).build()
