package com.jjrapps.constanza.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.Spacing

/**
 * Today's add-habit action and the empty-state sentence it used to be paired with, kept out of
 * [TodayScreen] for the same reason [ExactAlarmBanner] and [NotificationPermissionBanner] live in
 * `TodayBanners.kt`: the file that owns the habit list should not also own every affordance that
 * happens to share the screen with it.
 *
 * There is now exactly ONE add-habit affordance here — [TodayAddHabitFab] — whichever state the
 * screen is in. It used to be two: a centred filled `Button` inside the empty state, and a second
 * centred `Button` after the last habit row. See [TodayAddHabitFab] for why that was reversed.
 */

/**
 * Non-visual hook for Today's add-habit FAB. The button carries an icon rather than a label, so
 * `R.string.today_add_habit` reaches the tree only as a `contentDescription`; a tag is still the
 * finder tests use, for the same reason as
 * [com.jjrapps.constanza.core.ui.component.HABIT_COLOR_DOT_TEST_TAG] — a tag adds no accessibility
 * announcement, so nothing a screen reader says changes because of it.
 */
const val TODAY_ADD_HABIT_FAB_TEST_TAG = "today_add_habit_fab"

/**
 * The empty presentation (today-add-habit): what the state IS, said in one sentence. The call to
 * action is [TodayAddHabitFab], floating over this, not inside it.
 *
 * The caller passes the height — in practice `Modifier.weight(1f)` of the space left under any
 * permission banner — and this centres within it. That indirection is the fix for a real defect:
 * sized against the whole viewport instead, with both banners showing, the "centred" content landed
 * in the bottom third of the screen.
 */
@Composable
internal fun TodayEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.today_empty))
    }
}

/**
 * today-add-habit-is-not-a-fab: Today creates a habit through the same affordance
 * [com.jjrapps.constanza.habit.HabitListScreen] does — a [FloatingActionButton] with
 * [Icons.Filled.Add] in the `Scaffold` floating-action slot, bottom-right. Deliberately identical,
 * down to the icon and the slot, so that the current shape reads as a decision rather than as two
 * screens that drifted apart.
 *
 * This REVERSES what `TrailingAddHabitAction` recorded here before, which argued that a FAB on
 * Today would "make the two screens disagree about what creating a habit looks like". That reading
 * had it backwards: the habit list is one tap away behind "Manage habits" and already uses a FAB,
 * so it is the centred `Button` that made them disagree. The other argument the old shape rested on
 * — that a trailing list item could be misread as a habit row, since every row leads with a colour
 * dot — is answered outright by a floating button, which sits above the list and belongs to no row.
 *
 * There is exactly ONE of these per screen state, and no centred `Button` beside it: two create
 * affordances on one screen is worse than either alone, so the empty state now carries the sentence
 * only.
 *
 * Absent, not disabled, while a past day is on screen — see the call site in [TodayScreen] and
 * `today-past-day-correction` design decision 5: a habit's schedule starts when it is created, so
 * dating one three weeks back means nothing, and that change's own convention is that unavailable
 * navigation is structurally absent rather than greyed out.
 */
@Composable
internal fun TodayAddHabitFab(onAddHabit: () -> Unit) {
    FloatingActionButton(
        onClick = onAddHabit,
        modifier = Modifier.testTag(TODAY_ADD_HABIT_FAB_TEST_TAG),
    ) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.today_add_habit))
    }
}
