package com.jjrapps.constanza.tracking

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionHeader

/**
 * The header above one of Today's three grouped sections (today-grouped-
 * sections, design.md: "Ahora" / "Más tarde" / "Hecho"). Split out of `TodayScreen.kt` the same
 * way `TodayDateBar`, `TodayBanners` and `TodayAddHabitAction` already are — that file lays a
 * screen out, and by this point it was already at detekt's file-level `TooManyFunctions` ceiling.
 *
 * settings-section-headings: the actual rendering moved out to
 * [com.jjrapps.constanza.core.ui.component.SectionHeader] /
 * [com.jjrapps.constanza.core.ui.component.SectionDivider] so Settings' three sections could adopt
 * the same treatment. Both functions here stay as thin wrappers rather than being inlined at their
 * call site in `TodayScreen.kt` — mapping [TodaySectionKind] to a string resource is Today-specific
 * vocabulary that has no business living in a shared `core.ui` component.
 *
 * Graphite redesign: the hairline rule under each header is gone — the uppercase muted label and
 * the gap above it already separate the sections, and a rule per section added three more lines
 * to a screen meant to read calm.
 */
private fun TodaySectionKind.titleRes(): Int = when (this) {
    TodaySectionKind.NOW -> R.string.today_section_now
    TodaySectionKind.LATER -> R.string.today_section_later
    TodaySectionKind.DONE -> R.string.today_section_done
}

@Composable
internal fun TodaySectionHeader(kind: TodaySectionKind) {
    SectionHeader(stringResource(kind.titleRes()), startInset = ROW_CONTENT_INSET)
}

