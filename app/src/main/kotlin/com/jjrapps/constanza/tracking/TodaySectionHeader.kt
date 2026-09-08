package com.jjrapps.constanza.tracking

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionDivider
import com.jjrapps.constanza.core.ui.component.SectionHeader

/**
 * The header and hairline rule above one of Today's three grouped sections (today-grouped-
 * sections, design.md: "Ahora" / "Más tarde" / "Hecho"). Split out of `TodayScreen.kt` the same
 * way `TodayDateBar`, `TodayBanners` and `TodayAddHabitAction` already are — that file lays a
 * screen out, and by this point it was already at detekt's file-level `TooManyFunctions` ceiling.
 *
 * settings-section-headings: the actual rendering moved out to
 * [com.jjrapps.constanza.core.ui.component.SectionHeader] /
 * [com.jjrapps.constanza.core.ui.component.SectionDivider] so Settings' three sections could adopt
 * the same treatment. Both functions here stay as thin wrappers rather than being inlined at their
 * call site in `TodayScreen.kt` — mapping [TodaySectionKind] to a string resource is Today-specific
 * vocabulary that has no business living in a shared `core.ui` component, and keeping both names
 * meant `TodayScreen.kt` itself needed zero changes, which is the strongest guarantee that Today's
 * layout did not shift.
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

@Composable
internal fun TodaySectionDivider() {
    SectionDivider(startInset = ROW_CONTENT_INSET)
}
