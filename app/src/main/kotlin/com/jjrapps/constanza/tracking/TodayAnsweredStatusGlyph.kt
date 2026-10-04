package com.jjrapps.constanza.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jjrapps.constanza.core.ui.icons.ConstanzaIcons
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.domain.model.EntryStatus

/**
 * today-status-icons: what an answered Today slot renders instead of the status WORD it used to
 * draw for it — "Hecho"/"No hecho"/"Omitido" become a glyph, because a tick or a cross carries the
 * same information at a glance and a repeated row of answered habits used to read as a wall of
 * near-identical sentences.
 *
 * Graphite redesign: the glyphs are neutral, never green or red. The tick is
 * [ConstanzaColors.OnBackgroundVariant]; the cross and the skipped dash recede one step further to
 * [ConstanzaColors.OnBackgroundMuted], so a day of answers reads as quiet text, not a scoreboard.
 *
 * `SlotRow`/`SingleSlotRow` only reach this for a slot that is actually answered
 * (`COMPLETED`/`MISSED`/`SKIPPED`) — a pending or snoozed slot shows [TodayAnswerButtons] instead,
 * which is why [EntryStatus.UNKNOWN] has no branch below.
 *
 * today-one-line-row: this used to also draw the habit's scheduled slot time beside the glyph, and
 * `muted`-recolour it. Both now belong to the caller (`SlotTrailing` in `TodayHabitRows.kt`),
 * which renders time once, uniformly, ahead of EITHER this glyph or the pending answer buttons.
 * This function is left with the one thing only it can draw: the glyph itself.
 */
@Composable
internal fun AnsweredStatusRow(status: EntryStatus, modifier: Modifier = Modifier) {
    val description = stringResource(slotStatusLabel(status))
    // The glyph is the ONLY carrier of the state now that the word is gone, so its own
    // contentDescription — not a wrapping row's — takes the existing status string resource
    // (via [slotStatusLabel]) rather than leaving TalkBack with nothing where a word used to be.
    StatusGlyph(status, modifier = modifier.semantics { contentDescription = description })
}

@Composable
private fun StatusGlyph(status: EntryStatus, modifier: Modifier = Modifier) {
    when (status) {
        EntryStatus.COMPLETED -> Icon(
            imageVector = ConstanzaIcons.Check,
            contentDescription = null,
            tint = ConstanzaColors.OnBackgroundVariant,
            modifier = modifier.size(Dimens.StatusGlyph),
        )
        EntryStatus.MISSED -> Icon(
            imageVector = ConstanzaIcons.Close,
            contentDescription = null,
            tint = ConstanzaColors.OnBackgroundMuted,
            // The board draws the cross a size below the tick (16 vs 18): a miss is noted, not shouted.
            modifier = modifier.size(Dimens.StatusGlyph).padding(1.dp),
        )
        EntryStatus.SKIPPED -> SkippedDash(modifier)
        // Unreachable: AnsweredStatusRow is only called for a slot SlotRow has already established
        // is answered. Written as an exhaustive branch rather than an `else` anyway, the same way
        // slotStatusLabel is — adding a fifth EntryStatus member must fail this compile rather than
        // silently fall through to one of these three glyphs.
        EntryStatus.UNKNOWN -> Unit
    }
}

/**
 * `SKIPPED` is neither a pass nor a fail, so it draws a short rounded dash rather than a tick or a
 * cross, centred in the same [Dimens.StatusGlyph] square so all three glyphs share one optical
 * baseline. [Dimens.StatusGlyphDashWidth]/[Dimens.StatusGlyphDashHeight] approximate the visual
 * width the tick and cross draw inside that square rather than spanning the whole box.
 */
@Composable
private fun SkippedDash(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(Dimens.StatusGlyph), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(Dimens.StatusGlyphDashWidth)
                .height(Dimens.StatusGlyphDashHeight)
                .background(
                    color = ConstanzaColors.OnBackgroundMuted,
                    shape = RoundedCornerShape(Dimens.StatusGlyphDashHeight / 2),
                ),
        )
    }
}
