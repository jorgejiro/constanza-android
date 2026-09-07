package com.jjrapps.constanza.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing

/**
 * today-one-line-row: the pending Today slot's two answer controls, straight from the
 * owner-approved `TodayOneLineRowPrototype` (`ChipStyle.OUTLINED`). Outlined rather than plain
 * coloured text or a filled pill — the prototype's second round tried both and both lost:
 *
 * - Bare coloured text (`ChipStyle.PLAIN`) reads as a status *label* rather than a control, and
 *   colouring "Sí" green/"No" red made that WORSE, not better: a green word beside a habit name
 *   reads exactly like the glyph [AnsweredStatusRow] draws once the slot IS answered, so a still
 *   pending row looked already answered at a glance.
 * - A filled pill (`ChipStyle.FILLED`) fixes "is this tappable", but its own fill colour then
 *   out-weighs the habit name beside it — the identical competition-for-attention defect
 *   today-status-icons spent its whole change removing from the answered side of this same row.
 *
 * An outline lets the SHAPE carry "this is a button" and the colour carry "this is what tapping it
 * means", without spending enough ink to compete with the name. The tint previews its own result —
 * green "Sí" produces the same green tick [AnsweredStatusRow] draws once tapped, red "No" the same
 * red cross — so choosing an answer is choosing what the row will look like a moment later.
 */
@Composable
internal fun TodayAnswerPills(habitName: String, onAnswer: (InAppEntryStatus) -> Unit) {
    Row {
        AnswerPill(
            label = stringResource(R.string.today_answer_yes),
            description = stringResource(R.string.today_answer_yes_a11y, habitName),
            tint = ConstanzaColors.StatusCompleted,
            onClick = { onAnswer(InAppEntryStatus.COMPLETED) },
        )
        Spacer(Modifier.width(Spacing.xs))
        AnswerPill(
            label = stringResource(R.string.today_answer_no),
            description = stringResource(R.string.today_answer_no_a11y, habitName),
            tint = ConstanzaColors.StatusMissed,
            onClick = { onAnswer(InAppEntryStatus.MISSED) },
        )
    }
}

/** The pill's own border alpha — named rather than a bare literal at the call site, the exact
 *  value the owner already approved from a render (`TodayOneLineRowPrototype`'s `ChipStyle
 *  .OUTLINED`, `tint.copy(alpha = 0.55f)`). */
private const val ANSWER_PILL_BORDER_ALPHA = 0.55f

/** Painted at [Dimens.AnswerPillWidth]x[Dimens.AnswerPillHeight] but hit at
 *  [Dimens.AnswerPillTouchTarget] — the same paint/hit split [Dimens.Swatch]/[Dimens
 *  .SwatchTouchTarget] already established (`HabitColorPicker`'s swatch), so a compact 28dp-tall
 *  control never drops below the 48dp minimum touch target. [description] carries the accessible
 *  label rather than [label] alone: a bare "Sí"/"No" announces nothing about which habit it
 *  answers, and a screen full of pending rows would otherwise announce identically. The corner
 *  radius is derived from the pill's own height (half of it, a true stadium shape) rather than a
 *  second literal that would have to agree with [Dimens.AnswerPillHeight] by hand.
 *
 * today-one-line-row, vertical-rhythm fix: [reportPaintedHeight] is the OUTERMOST modifier here
 * rather than an afterthought — see its own KDoc for why this Box's true 48dp touch target must
 * report only [Dimens.AnswerPillHeight] to whatever `Row` it sits in. Without it, a pending row
 * was the one row on screen whose trailing content reported a full 48dp upward — its own `Row`
 * had no choice but to grow to fit, while a same-section ANSWERED row (glyph only, no touch
 * target of its own) stayed close to text height. Measured on a real render: consecutive-row gaps
 * of 10.7–17.9dp between answered rows against ~40dp between two pending rows — a defect, not the
 * deliberate muted/non-muted difference this design already carries elsewhere. */
@Composable
private fun AnswerPill(label: String, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .reportPaintedHeight(Dimens.AnswerPillHeight)
            .size(Dimens.AnswerPillTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.AnswerPillWidth, Dimens.AnswerPillHeight)
                .border(
                    BorderStroke(1.dp, tint.copy(alpha = ANSWER_PILL_BORDER_ALPHA)),
                    RoundedCornerShape(Dimens.AnswerPillHeight / 2),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * today-one-line-row, vertical-rhythm fix: reports [paintedHeight] upward to whatever measures
 * this element — typically a `Row` sizing itself against its tallest child — instead of this
 * element's own true measured height, and centres the real content around that smaller reported
 * box. Nothing about the element's REAL size, position or hit-test area changes: Compose's
 * semantics tree reads the actual placed child and its true coordinates, never what a `layout {}`
 * modifier merely reports to its own parent, so a test's `fetchSemanticsNode().boundsInRoot` on
 * whatever this wraps stays exactly what it was measured at. Only the space the immediate PARENT
 * reserves for this child shrinks — the true content overlaps into whatever padding or whitespace
 * already surrounds it, rather than pushing that parent to grow.
 *
 * This is the general mechanism behind [Dimens.AnswerPillTouchTarget] sitting inside a pending
 * row without inflating it — [Dimens.Swatch] sits inside [Dimens.SwatchTouchTarget] for free
 * because `HabitColorPicker`'s grid already allocates a 48dp cell per swatch; a Today row has no
 * such fixed cell, so the overlap has to be built by hand here instead. `internal`, not `private`
 * — `TodayScreen.kt`'s multi-slot header applies this identical fix to its own expand/collapse
 * control, whose own touch-target padding was the second half of the same class of defect.
 */
internal fun Modifier.reportPaintedHeight(paintedHeight: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val reportedHeight = paintedHeight.roundToPx()
    layout(placeable.width, reportedHeight) {
        placeable.place(0, (reportedHeight - placeable.height) / 2)
    }
}
