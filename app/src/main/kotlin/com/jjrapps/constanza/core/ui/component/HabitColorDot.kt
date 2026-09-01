package com.jjrapps.constanza.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.jjrapps.constanza.core.ui.theme.Dimens

private const val HALO_ALPHA = 0.16f

/** Non-visual hook [HabitColorDotComposeTest] asserts on. Not a `contentDescription` — it adds no
 *  accessibility announcement, so it does not weaken decision 6's "colour is a secondary channel"
 *  boundary below. */
const val HABIT_COLOR_DOT_TEST_TAG = "habit_color_dot"

/**
 * The shared habit-colour identity marker (design.md decision 6, work unit 4): a
 * [Dimens.HabitDotSlot] tinted halo (`alpha = `[HALO_ALPHA]) behind a solid [Dimens.HabitDot] core,
 * both derived from the same [argb] int — the same spine `HabitPalette`/`Habit.colorArgb`/
 * `NotificationPoster.setColor()` already share, so no colour ever gets reinterpreted on its way
 * into this composable.
 *
 * Placed via `ListItem(leadingContent = …)` everywhere it appears (`HabitListScreen.HabitRow`,
 * `TodayScreen.HabitRollupRow`'s multi-slot branch) so a row's measured height never changes — the
 * whole geometry argument decision 6 makes for why `TodayAdaptiveComposeTest` stays green at
 * `sw = 600dp` (task 4.8).
 *
 * No `contentDescription`: colour is a secondary recognition channel here, never the only one — the
 * habit name sitting beside every placement is the accessible label. This is deliberate, not an
 * oversight (design.md decision 6); do not add one.
 */
@Composable
fun HabitColorDot(argb: Int, modifier: Modifier = Modifier) {
    val color = Color(argb)
    Box(
        modifier = modifier.size(Dimens.HabitDotSlot).testTag(HABIT_COLOR_DOT_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.HabitDotSlot)
                .clip(CircleShape)
                .background(color.copy(alpha = HALO_ALPHA)),
        )
        Box(
            modifier = Modifier
                .size(Dimens.HabitDot)
                .clip(CircleShape)
                .background(color),
        )
    }
}
