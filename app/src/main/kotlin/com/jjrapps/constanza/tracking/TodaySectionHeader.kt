package com.jjrapps.constanza.tracking

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.Spacing

/**
 * The header and hairline rule above one of Today's three grouped sections (today-grouped-
 * sections, design.md: "Ahora" / "Más tarde" / "Hecho"). Split out of `TodayScreen.kt` the same
 * way `TodayDateBar`, `TodayBanners` and `TodayAddHabitAction` already are — that file lays a
 * screen out, and by this point it was already at detekt's file-level `TooManyFunctions` ceiling.
 */

/** today-grouped-sections, design.md: the classic section-label treatment — ALL CAPS, small,
 *  heavier weight, generous letter-spacing — so a header reads as STRUCTURE rather than more body
 *  text. Built off [MaterialTheme.typography.labelMedium] (its 12sp size is a real token, not a
 *  guessed literal) with only weight and letter-spacing added. */
private val SECTION_HEADER_LETTER_SPACING = 1.5.sp

private fun TodaySectionKind.titleRes(): Int = when (this) {
    TodaySectionKind.NOW -> R.string.today_section_now
    TodaySectionKind.LATER -> R.string.today_section_later
    TodaySectionKind.DONE -> R.string.today_section_done
}

/** today-grouped-sections, design.md: identical across all three sections — same style, same
 *  colour, no alpha — because a structural label carries no emphasis of its own; only a row's own
 *  text mutes for [TodaySectionKind.DONE]. `.uppercase()` is applied here, in the composable, so
 *  the string resource itself stays normal-case and fit for localisation. */
@Composable
internal fun TodaySectionHeader(kind: TodaySectionKind) {
    Text(
        stringResource(kind.titleRes()).uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ROW_CONTENT_INSET, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.xs),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = SECTION_HEADER_LETTER_SPACING,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** today-grouped-sections, design.md: a 1dp hairline directly under each section header, starting
 *  at [ROW_CONTENT_INSET] — NEVER full-bleed, or it would compete with the one left edge every
 *  other line on this screen shares — and ending at the header's own [Spacing.lg] right margin.
 *  Coloured with the app's own real divider role (`ConstanzaColors.Divider`, bound to
 *  `colorScheme.outlineVariant`), never an invented low-alpha overlay. */
@Composable
internal fun TodaySectionDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth().padding(start = ROW_CONTENT_INSET, end = Spacing.lg),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
