package com.jjrapps.constanza.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.core.ui.theme.Spacing

/**
 * settings-section-headings: the section-label treatment originally built for Today
 * (today-grouped-sections, design.md: "Ahora" / "Más tarde" / "Hecho") — ALL CAPS, small, heavier
 * weight, generous letter-spacing — so a header reads as STRUCTURE rather than more body text.
 * Extracted out of `tracking/TodaySectionHeader.kt` so every screen with grouped sections shares
 * one definition instead of each writing its own. Settings' three section headings
 * (`SnoozeSettingsScreen`, `DataPortabilitySection`, `LanguageSection`) adopted it because none of
 * them had ANY heading style before this — each was a bare [Text] with no `style` argument at
 * all, inheriting `bodyLarge`, identical to the option labels sitting beside it.
 *
 * Built off [MaterialTheme.typography.labelMedium] (its 12sp size is a real token, not a guessed
 * literal) with only weight and letter-spacing added.
 */
private val SECTION_HEADER_LETTER_SPACING = 1.5.sp

/**
 * [title] is a plain [String] rather than a `@StringRes` id or an enum, so this component stays
 * free of any screen-specific vocabulary — every call site resolves its own resource (and, for
 * Today, maps its own `TodaySectionKind` first) before calling in. `.uppercase()` is applied here,
 * in the composable, so the string resource itself stays normal-case and fit for localisation.
 *
 * [startInset] defaults to [Spacing.lg], the same screen margin every screen without a
 * Today-specific row inset already uses; Today passes its own `ROW_CONTENT_INSET` explicitly so
 * the header keeps lining up with its rows even if that token ever diverges from [Spacing.lg].
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    startInset: Dp = Spacing.lg,
    endInset: Dp = Spacing.lg,
) {
    Text(
        title.uppercase(),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startInset, end = endInset, top = Spacing.xl, bottom = Spacing.xs),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = SECTION_HEADER_LETTER_SPACING,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * today-grouped-sections: a 1dp hairline directly under a [SectionHeader] — NEVER full-bleed, or
 * it would compete with the one left edge every other line on Today shares. Coloured with the
 * app's own real divider role (`ConstanzaColors.Divider`, bound to `colorScheme.outlineVariant`),
 * never an invented low-alpha overlay.
 */
@Composable
fun SectionDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = Spacing.lg,
    endInset: Dp = Spacing.lg,
) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth().padding(start = startInset, end = endInset),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
