package com.jjrapps.constanza.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.jjrapps.constanza.core.ui.theme.Dimens

/**
 * The habit's identity colour in the graphite design (direction E): an 8dp dot leading the row,
 * and the ONLY place a habit's colour is painted — the name itself renders in the text colour
 * (`visual-design-system`: "habit colour shown only as a dot").
 *
 * [alpha] lets a receding row (Today's answered section) dim the dot without changing which colour
 * it is. The dot is decorative to a screen reader — the habit name beside it already identifies
 * the habit — so it adds no content description; [HabitDotColor] exposes the painted colour to
 * tests only.
 */
@Composable
fun HabitDot(colorArgb: Int, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val color = Color(colorArgb)
    Box(
        modifier = modifier
            .size(Dimens.HabitDot)
            .semantics { habitDotColor = color }
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

/** The un-dimmed habit colour a [HabitDot] paints; a test hook, never announced. */
val HabitDotColor = SemanticsPropertyKey<Color>("HabitDotColor")

/** Receiver form of [HabitDotColor]. */
var SemanticsPropertyReceiver.habitDotColor by HabitDotColor
