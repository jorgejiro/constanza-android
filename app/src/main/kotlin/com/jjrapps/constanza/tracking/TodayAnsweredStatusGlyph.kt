package com.jjrapps.constanza.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.domain.model.EntryStatus

/**
 * today-status-icons: what an answered Today slot renders instead of the status WORD it used to
 * draw for it — "Hecho"/"No hecho"/"Omitido" become a glyph, because a green tick or a red cross
 * carries the same information at a glance and a repeated row of answered habits used to read as a
 * wall of near-identical sentences.
 *
 * `SlotRow`/`SingleSlotRow` only reach this for a slot that is actually answered
 * (`COMPLETED`/`MISSED`/`SKIPPED`) — a pending or snoozed slot shows [TodayAnswerPills] instead,
 * which is why [EntryStatus.UNKNOWN] has no branch below.
 *
 * today-one-line-row: this used to also draw the habit's scheduled slot time beside the glyph, and
 * `muted`-recolour it. Both now belong to the caller (`TodaySlotTrailing` in `TodayScreen.kt`),
 * which renders time once, uniformly, ahead of EITHER this glyph or the pending pills — the exact
 * shape `TodayOneLineRowPrototype`'s own `Trailing` composable already established. This function is
 * left with the one thing only it can draw: the glyph itself.
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
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = ConstanzaColors.StatusCompleted,
            modifier = modifier.size(Dimens.StatusGlyph),
        )
        EntryStatus.MISSED -> Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = ConstanzaColors.StatusMissed,
            modifier = modifier.size(Dimens.StatusGlyph),
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
 * `material-icons-core` 1.7.8 — the only icon artifact this project depends on
 * (`app/build.gradle.kts`, no `material-icons-extended`) — has no minus/dash glyph: `Icons.Filled`
 * ships `Check`, `Clear`, `Close`, `Create`, `Delete`, `Done`, `Edit`, `Settings` and roughly forty
 * more, but nothing shaped like a horizontal dash. Adding `material-icons-extended` for one
 * rectangle is not a trade worth making, so `SKIPPED` draws its own: a small rounded bar centred in
 * the same [Dimens.StatusGlyph] square [Icons.Filled.Check]/[Icons.Filled.Close] occupy, so all
 * three glyphs share one optical baseline. [Dimens.StatusGlyphDashWidth]/[Dimens.StatusGlyphDashHeight]
 * approximate the visual width those two vectors actually draw inside that square (a Material glyph
 * does not fill its own viewport) rather than spanning the whole box, which would read as a much
 * heavier mark than a tick or a cross.
 *
 * Tinted [ConstanzaColors.OnBackgroundMuted] rather than either of [ConstanzaColors.StatusCompleted]/
 * [ConstanzaColors.StatusMissed] — `SKIPPED` is neither a pass nor a fail, so it borrows the app's
 * existing "quiet, no verdict" tone instead of a new one measured for this shape alone.
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
