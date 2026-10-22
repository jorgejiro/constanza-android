package com.jjrapps.constanza.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.icons.ConstanzaIcons
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing

/** The ✕/✓ glyph size inside a 44dp answer button: the board's 18/20px icons, rounded to one. */
private val ANSWER_ICON_SIZE = 20.dp

/**
 * The pending Today slot's two answer controls in the graphite design (direction E): round 44dp
 * buttons, "No" (✕) first and "Yes" (✓) second, neither coloured green or red — an outline lets
 * the SHAPE say "this is a button" and the glyph say what it means, without spending colour that
 * would compete with the habit's own dot.
 *
 * Both are outlined in every section, "Now" included. A light-filled ✓ used to emphasise the likely
 * answer there, but light fill with dark ink is this app's SELECTED state (the reminder-time
 * toggle), so an unanswered slot read as already answered "Yes". The ✓ keeps only a quieter
 * emphasis: its glyph in the primary text tone, the ✕ in the secondary one.
 *
 * The visible text is gone, so each button's accessible label is its contentDescription, which
 * names the habit — a screen full of bare "Yes" buttons would otherwise announce identically.
 */
@Composable
internal fun TodayAnswerButtons(habitName: String, onAnswer: (InAppEntryStatus) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnswerButton(
            icon = ConstanzaIcons.Close,
            description = stringResource(R.string.today_answer_no_a11y, habitName),
            tint = ConstanzaColors.OnBackgroundVariant,
            onClick = { onAnswer(InAppEntryStatus.MISSED) },
        )
        Spacer(Modifier.width(Spacing.xs))
        AnswerButton(
            icon = ConstanzaIcons.Check,
            description = stringResource(R.string.today_answer_yes_a11y, habitName),
            tint = ConstanzaColors.OnBackground,
            onClick = { onAnswer(InAppEntryStatus.COMPLETED) },
        )
    }
}

/**
 * Painted at [Dimens.AnswerButton], hit at [Dimens.AnswerButtonTouchTarget]: the 48dp box is the
 * clickable, the 44dp circle centred in it is only paint — the same paint/hit split the colour
 * swatch uses. The circle is a [ConstanzaColors.ControlStroke] ring, the operable-stroke tone that
 * clears 3:1 on every surface.
 */
@Composable
private fun AnswerButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Dimens.AnswerButtonTouchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.AnswerButton)
                .border(BorderStroke(1.dp, ConstanzaColors.ControlStroke), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(ANSWER_ICON_SIZE))
        }
    }
}
