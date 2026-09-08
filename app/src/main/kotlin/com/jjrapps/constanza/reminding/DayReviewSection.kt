package com.jjrapps.constanza.reminding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionDivider
import com.jjrapps.constanza.core.ui.component.SectionHeader
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.habit.ReminderTimeField

/**
 * day-review, slice C (day-review-notification): the nightly day-review's own settings, as a
 * fourth section on this same Settings screen — [com.jjrapps.constanza.portability
 * .DataPortabilitySection] and [com.jjrapps.constanza.localization.LanguageSection]'s own
 * precedent, so no new route.
 */
@Composable
fun DayReviewSection(viewModel: DayReviewSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    DayReviewSectionContent(
        uiState = uiState,
        onReviewTimeChange = viewModel::setReviewTimeMinuteOfDay,
        onFiresEveryNightChange = viewModel::setReviewFiresEveryNight,
    )
}

/**
 * Presentational half, following this codebase's container/presentational split — see
 * [com.jjrapps.constanza.localization.LanguageSectionContent]'s own reasoning: it takes state and
 * callbacks and owns none itself, so a Compose test can drive it with `createComposeRule()` and no
 * Hilt-enabled Activity.
 *
 * The two controls: [ReminderTimeField], the app's one time-of-day control (not a second picker),
 * and two radio rows for the mode — [DayReviewModeRow], the same shape [SnoozeDurationRow] already
 * uses for a settings choice on this exact screen.
 *
 * **Radio rows rather than a `Switch`, and not because the app avoids switches** — it has two,
 * `HabitListScreen`'s "show archived" and `ScheduleEditors`' "remind me". Both are genuine on/off:
 * the off position means the thing does not happen. This setting is a boolean in Kotlin but not one
 * on screen, because its off position still posts a notification, just a conditional one. A switch
 * labelled "every night" would read as "notify me / do not notify me", which is the one thing it
 * does not mean. Two named options each say what they do.
 *
 * The 23:45 ceiling ([REVIEW_TIME_LATEST_MINUTE_OF_DAY]) is surfaced as supporting text under the
 * time field rather than blocked in the picker itself: [ReminderTimeField]'s dialog is shared with
 * the habit editor's own reminder times, which carry no such ceiling, so clamping inside it would
 * either leak this feature's rule into an unrelated control or need a second, near-identical
 * dialog. Stating the ceiling in copy keeps [DayReviewSettingsStore]'s silent clamp from reading as
 * a lie: an owner who tries 23:50 sees the 23:45 ceiling named — in their own device's time format,
 * via [rememberTimeOfDayFormat], so it can never disagree with what [ReminderTimeField] itself
 * shows — and why it exists, before the store ever has to silently correct them.
 */
@Composable
fun DayReviewSectionContent(
    uiState: DayReviewSettingsUiState,
    onReviewTimeChange: (Int) -> Unit,
    onFiresEveryNightChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        SectionHeader(stringResource(R.string.settings_day_review_section_title), startInset = 0.dp, endInset = 0.dp)
        SectionDivider(startInset = 0.dp, endInset = 0.dp)
        ReminderTimeField(
            minuteOfDay = uiState.reviewTimeMinuteOfDay,
            onMinuteOfDayChange = onReviewTimeChange,
            label = stringResource(R.string.settings_day_review_time_label),
        )
        val timeFormat = rememberTimeOfDayFormat()
        Text(
            stringResource(
                R.string.settings_day_review_time_supporting,
                timeFormat.format(REVIEW_TIME_LATEST_MINUTE_OF_DAY),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        DayReviewModeRow(
            labelRes = R.string.settings_day_review_mode_every_night,
            selected = uiState.reviewFiresEveryNight,
            onSelect = { onFiresEveryNightChange(true) },
        )
        DayReviewModeRow(
            labelRes = R.string.settings_day_review_mode_only_if_unanswered,
            selected = !uiState.reviewFiresEveryNight,
            onSelect = { onFiresEveryNightChange(false) },
        )
    }
}

/**
 * One of the two review-mode options — the same row shape [SnoozeDurationRow] already uses
 * (`selectable` + [RadioButton] + label) for this screen's other multi-option settings choice.
 * `internal` rather than `private`, for exactly [SnoozeDurationRow]'s own reason: so a Compose test
 * can render the real production row directly.
 */
@Composable
internal fun DayReviewModeRow(@StringRes labelRes: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(labelRes), modifier = Modifier.padding(start = 16.dp))
    }
}
