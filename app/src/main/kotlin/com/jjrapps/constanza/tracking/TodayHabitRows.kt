package com.jjrapps.constanza.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.HabitDot
import com.jjrapps.constanza.core.ui.icons.ConstanzaIcons
import com.jjrapps.constanza.core.ui.rememberTimeOfDayFormat
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.domain.model.EntryStatus
import java.time.ZoneId

/** A live row's (Now/Later) minimum height, from the graphite board. */
private val LIVE_ROW_MIN_HEIGHT = 64.dp

/** An answered row's minimum height: tighter than a live one, still above the 48dp touch floor
 *  its own change-dialog click needs. */
private val DONE_ROW_MIN_HEIGHT = 52.dp

/** The gap between the habit dot and the name. */
private val DOT_TEXT_GAP = 14.dp

/** The gap between the reminder segments and their "x of N" line. */
private val SEGMENTS_TEXT_GAP = 10.dp

/** The gap between two reminder segments. */
private val SEGMENT_GAP = 3.dp

/** The board's 16px answered-row name, a step below the live 17sp. */
private val DONE_NAME_FONT_SIZE = 16.sp

/** The expander's chevron glyph inside its 44dp button. */
private val EXPANDER_ICON_SIZE = 20.dp

/** An answered row's dot, receding with its row rather than changing colour. */
private const val DONE_DOT_ALPHA = 0.5f

/** Half a turn: the expander's chevron points up while expanded. */
private const val EXPANDED_CHEVRON_ROTATION = 180f

/** Where an expanded slot line's time starts: under the habit name, not under the dot. */
private val NAME_COLUMN_INSET = ROW_CONTENT_INSET + Dimens.HabitDot + DOT_TEXT_GAP

/**
 * One habit on Today in the graphite design (direction E): its colour dot, its name in the text
 * colour (never the habit colour), a one-line subtitle, and its trailing controls.
 *
 * A single-slot habit is one line ([SingleSlotRow]). A multi-slot habit shows a summary line
 * ([MultiSlotHeader]: per-reminder segments plus "x of N · next HH:MM") and expands to one
 * independently answerable [SlotRow] per reminder.
 *
 * [section] carries the row's emphasis: [TodaySectionKind.NOW] fills the ✓ button, and
 * [TodaySectionKind.DONE] recedes the dot, the name's size and its tone. Neither ever touches which
 * colour the dot is — identity, not state.
 */
@Composable
internal fun TodayHabitRow(
    row: TodayHabitRow,
    section: TodaySectionKind,
    expanded: Boolean,
    zone: ZoneId,
    actions: SlotActions,
) {
    if (row.slots.size > 1) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MultiSlotHeader(row, section, expanded, actions)
            if (expanded) {
                row.slots.forEach { slot -> SlotRow(row, slot, section, zone, actions) }
            }
        }
    } else {
        SingleSlotRow(row, row.slots.single(), section, zone, actions)
    }
}

/**
 * The single-slot line: dot, name over its subtitle (the reminder time and, while snoozed, until
 * when), then either the two answer buttons or the answered glyph.
 *
 * An ANSWERED line is itself the tap target that opens [ChangeAnswerDialog] (today-one-line-row,
 * point 2), labelled by `today_slot_change_a11y`; a pending line has no click of its own, only its
 * buttons do. The row's minimum height keeps either kind at or above the 48dp touch floor.
 */
@Composable
private fun SingleSlotRow(
    row: TodayHabitRow,
    slot: TodaySlot,
    section: TodaySectionKind,
    zone: ZoneId,
    actions: SlotActions,
) {
    val done = section == TodaySectionKind.DONE
    val answered = slot.status != EntryStatus.UNKNOWN
    var rowModifier = Modifier.fillMaxWidth()
    if (answered) {
        val description = answeredRowDescription(row.habitName, slot, time = null)
        rowModifier = rowModifier
            .clickable(role = Role.Button, onClick = { actions.onOpenChangeDialog(row, slot) })
            .semantics { contentDescription = description }
    }
    rowModifier = rowModifier
        .heightIn(min = if (done) DONE_ROW_MIN_HEIGHT else LIVE_ROW_MIN_HEIGHT)
        .padding(horizontal = ROW_CONTENT_INSET)
    val subtitle = if (answered) null else pendingSubtitle(slot, zone)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        HabitDot(row.colorArgb, alpha = if (done) DONE_DOT_ALPHA else 1f)
        Spacer(Modifier.width(DOT_TEXT_GAP))
        Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            HabitName(row.habitName, done)
            subtitle?.let { Subtitle(it) }
        }
        Spacer(Modifier.width(Spacing.sm))
        SlotTrailing(row, slot, section, actions)
    }
}

/**
 * A multi-slot habit's own line: dot, name, and under it one segment per reminder (answered ones
 * lit) beside "x of N · next HH:MM", then the expand/collapse chevron.
 *
 * The day rollup word this line used to append ("In progress", "All done"…) is no longer drawn —
 * the segments say it at a glance — but it is still SPOKEN: the summary's accessible label leads
 * with it, so TalkBack hears "In progress, 1 of 3 · next 13:00" as one phrase.
 */
@Composable
private fun MultiSlotHeader(
    row: TodayHabitRow,
    section: TodaySectionKind,
    expanded: Boolean,
    actions: SlotActions,
) {
    val done = section == TodaySectionKind.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (done) DONE_ROW_MIN_HEIGHT else LIVE_ROW_MIN_HEIGHT)
            .padding(horizontal = ROW_CONTENT_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HabitDot(row.colorArgb, alpha = if (done) DONE_DOT_ALPHA else 1f)
        Spacer(Modifier.width(DOT_TEXT_GAP))
        Column(
            modifier = Modifier.weight(1f).padding(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            HabitName(row.habitName, done)
            ReminderSummary(row)
        }
        Spacer(Modifier.width(Spacing.sm))
        val labelRes = if (expanded) R.string.today_collapse else R.string.today_expand
        IconButton(
            onClick = { actions.onToggleExpanded(row.habitId) },
            modifier = Modifier.size(Dimens.CompactIconButton),
        ) {
            Icon(
                ConstanzaIcons.ChevronDown,
                contentDescription = stringResource(labelRes),
                tint = ConstanzaColors.OnBackgroundVariant,
                modifier = Modifier
                    .size(EXPANDER_ICON_SIZE)
                    .rotate(if (expanded) EXPANDED_CHEVRON_ROTATION else 0f),
            )
        }
    }
}

@Composable
private fun ReminderSummary(row: TodayHabitRow) {
    val progress = reminderProgress(row)
    val timeFormat = rememberTimeOfDayFormat()
    val text = progress.nextMinuteOfDay?.let { minute ->
        stringResource(
            R.string.today_reminders_progress_next,
            progress.answered,
            progress.total,
            timeFormat.format(minute),
        )
    } ?: stringResource(R.string.today_reminders_progress, progress.answered, progress.total)
    val spoken = "${stringResource(dayStatusLabel(row.dayStatus))}, $text"
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.slots.forEachIndexed { index, slot ->
            if (index > 0) Spacer(Modifier.width(SEGMENT_GAP))
            val answered = slot.status != EntryStatus.UNKNOWN
            Spacer(
                Modifier
                    .size(Dimens.ReminderSegmentWidth, Dimens.ReminderSegmentHeight)
                    .background(
                        if (answered) ConstanzaColors.OnBackground else ConstanzaColors.SurfaceSelected,
                        RoundedCornerShape(Dimens.ReminderSegmentHeight / 2),
                    ),
            )
        }
        Spacer(Modifier.width(SEGMENTS_TEXT_GAP))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ConstanzaColors.OnBackgroundVariant)
    }
}

/**
 * One reminder of an expanded multi-slot habit: its time (and snooze, if any) under the habit
 * name's left edge, then its answer buttons or answered glyph. An answered slot's line opens the
 * change dialog, labelled with its time first — that time is what tells sibling slots apart.
 */
@Composable
private fun SlotRow(
    row: TodayHabitRow,
    slot: TodaySlot,
    section: TodaySectionKind,
    zone: ZoneId,
    actions: SlotActions,
) {
    val timeFormat = rememberTimeOfDayFormat()
    val time = slot.minuteOfDay?.let(timeFormat::format)
    val answered = slot.status != EntryStatus.UNKNOWN
    var rowModifier = Modifier.fillMaxWidth()
    if (answered) {
        val description = answeredRowDescription(row.habitName, slot, time)
        rowModifier = rowModifier
            .clickable(role = Role.Button, onClick = { actions.onOpenChangeDialog(row, slot) })
            .semantics { contentDescription = description }
    }
    rowModifier = rowModifier
        .heightIn(min = Dimens.MinTouchTarget)
        .padding(start = NAME_COLUMN_INSET, end = ROW_CONTENT_INSET)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (time != null) Subtitle(time)
            if (!answered) {
                slot.snoozedUntilEpochMs?.let { epochMs ->
                    Spacer(Modifier.width(Spacing.sm))
                    Subtitle(snoozeSentence(epochMs, zone))
                }
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        SlotTrailing(row, slot, section, actions)
    }
}

/** The answer buttons for a pending slot, or the answered glyph — never a status word. */
@Composable
private fun SlotTrailing(row: TodayHabitRow, slot: TodaySlot, section: TodaySectionKind, actions: SlotActions) {
    if (slot.status == EntryStatus.UNKNOWN) {
        TodayAnswerButtons(row.habitName, filled = section == TodaySectionKind.NOW) { status ->
            actions.onAnswer(row.habitId, slot, status)
        }
    } else {
        // Inset so the glyph sits under the centre of where a ✓ button would be, not at the edge.
        AnsweredStatusRow(status = slot.status, modifier = Modifier.padding(end = Spacing.sm))
    }
}

/** The name in the text colour — never the habit colour, which the dot alone carries. An answered
 *  row's name steps down a size and to the secondary tone. */
@Composable
private fun HabitName(name: String, done: Boolean) {
    val style: TextStyle = if (done) {
        MaterialTheme.typography.bodyLarge.copy(fontSize = DONE_NAME_FONT_SIZE, fontWeight = FontWeight.Normal)
    } else {
        MaterialTheme.typography.bodyLarge
    }
    Text(
        name,
        style = style,
        color = if (done) ConstanzaColors.OnBackgroundVariant else ConstanzaColors.OnBackground,
    )
}

@Composable
private fun Subtitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = ConstanzaColors.OnBackgroundVariant)
}

/** A pending single slot's subtitle: its reminder time, then its snooze while snoozed; `null`
 *  when it has neither (a habit with no reminder time). */
@Composable
private fun pendingSubtitle(slot: TodaySlot, zone: ZoneId): String? {
    val timeFormat = rememberTimeOfDayFormat()
    val parts = listOfNotNull(
        slot.minuteOfDay?.let(timeFormat::format),
        slot.snoozedUntilEpochMs?.let { snoozeSentence(it, zone) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(SUBTITLE_SEPARATOR)
}

private const val SUBTITLE_SEPARATOR = " · "
