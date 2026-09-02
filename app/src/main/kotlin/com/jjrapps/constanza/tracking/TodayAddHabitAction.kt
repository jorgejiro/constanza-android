package com.jjrapps.constanza.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.Spacing

/**
 * Today's add-habit action, in the two presentations the screen switches between, kept out of
 * [TodayScreen] for the same reason [ExactAlarmBanner] and [NotificationPermissionBanner] live in
 * `TodayBanners.kt`: the file that owns the habit list should not also own every affordance that
 * happens to sit in the same `LazyColumn`.
 *
 * Both presentations are the SAME action and share one label. What differs is only where it sits
 * and how much room it is given, which is what [TodayContent] decides from `state.rows`.
 */

/**
 * Non-visual hooks for the two presentations of Today's add-habit action. Both render the SAME
 * `R.string.today_add_habit` label — they are one action, not two — so a text finder cannot tell
 * them apart, and a test that only asserted on the label could not prove the empty presentation is
 * absent once habits exist. Same reasoning, and the same shape, as
 * [com.jjrapps.constanza.core.ui.component.HABIT_COLOR_DOT_TEST_TAG]: a tag adds no accessibility
 * announcement, so nothing a screen reader says changes because of it.
 */
const val TODAY_ADD_HABIT_EMPTY_TEST_TAG = "today_add_habit_empty"

/** The populated-list presentation's hook; see [TODAY_ADD_HABIT_EMPTY_TEST_TAG]. */
const val TODAY_ADD_HABIT_TRAILING_TEST_TAG = "today_add_habit_trailing"

/**
 * The empty presentation (today-add-habit): the call to action, not a bare sentence. On a clean
 * install this screen is empty by definition, and an empty list with nothing to do on it is
 * indistinguishable from a broken one.
 *
 * The caller passes the height — in practice `Modifier.weight(1f)` of the space left under any
 * permission banner — and this centres within it. That indirection is the fix for a real defect:
 * sized against the whole viewport instead, with both banners showing, the "centred" action landed
 * in the bottom third of the screen.
 *
 * A filled [Button] deliberately, not an `OutlinedButton`: this is the screen's primary action, and
 * a filled container also means it takes no border from
 * [com.jjrapps.constanza.core.ui.theme.ConstanzaControlDefaults] — there is no stroke here to get
 * wrong.
 */
@Composable
internal fun TodayEmptyState(onAddHabit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.today_empty))
        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(onClick = onAddHabit, modifier = Modifier.testTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG)) {
            Text(stringResource(R.string.today_add_habit))
        }
    }
}

/**
 * The populated presentation (today-add-habit): the same action, after the last habit row.
 *
 * Centred and filled rather than left-aligned and flat, which is what keeps it from reading as one
 * more habit row: every row in this list starts with a colour dot at the leading edge and a name
 * beside it, so a centred container with no dot cannot be mistaken for one. The extra vertical
 * padding above it separates it from the last row for the same reason.
 *
 * Not a `FloatingActionButton`: [com.jjrapps.constanza.habit.HabitListScreen] already has one for
 * creating a habit, and a second, differently-shaped create affordance one tap away would make the
 * two screens disagree about what creating a habit looks like.
 */
@Composable
internal fun TrailingAddHabitAction(onAddHabit: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Button(onClick = onAddHabit, modifier = Modifier.testTag(TODAY_ADD_HABIT_TRAILING_TEST_TAG)) {
            Text(stringResource(R.string.today_add_habit))
        }
    }
}
