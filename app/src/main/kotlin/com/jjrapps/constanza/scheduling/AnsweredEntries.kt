package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.data.entity.EntryEntity

/**
 * design.md D11: `entries.slotId` is `NOT NULL DEFAULT 0` with no foreign key, and `0` is the
 * sentinel for "the habit had no distinguishable slot when this answer was written". Room
 * autogenerates `reminder_slots.id` from 1, so `0` is never a real slot id and the two meanings
 * can never collide.
 *
 * The sentinel is load-bearing here rather than incidental: a habit saved with no reminder time
 * and answered from the Today screen stores its answer under `slotId = 0`, and a reminder time
 * added afterwards gets a fresh slot id. Matching on the concrete slot id alone would miss that
 * answer entirely and arm a reminder for a day the user has already settled.
 */
private const val ENTRY_SLOT_SENTINEL = 0L

/**
 * The two statuses that mean "the user has settled this slot for this date".
 *
 * `MISSED` is deliberately absent. `habit-entry-tracking`'s Provisional-Missed Correction names an
 * import as one of the three paths that MUST be able to turn a `MISSED` into a `COMPLETED`, so a
 * `MISSED` row is a provisional verdict awaiting an answer, not an answer. Treating it as answered
 * would close a route the specification requires to stay open. `SKIPPED` is the opposite case: the
 * specification restricts it to an explicit in-app user action, so re-asking would override a
 * decision the user made on purpose.
 */
private val ANSWERED_ENTRY_STATUSES = setOf("COMPLETED", "SKIPPED")

/**
 * Whether any of these entries — all of them for one `(habitId, date)` — already answers [slotId],
 * either under its own id or under the [ENTRY_SLOT_SENTINEL].
 *
 * One predicate with three callers on purpose. [OccurrencePlanner] uses it so an answered slot is
 * never armed, [ReminderFireHandler] uses it so an armed-before-the-answer occurrence never posts,
 * and [OccurrenceResolver] uses it so the midnight sweep never writes `MISSED` over an answer.
 * Three separate definitions of "already answered" would drift, and the three sites guard the same
 * invariant from three different distances.
 */
internal fun List<EntryEntity>.answerSlot(slotId: Long): Boolean = any { it.answers(slotId) }

private fun EntryEntity.answers(slotId: Long): Boolean =
    status in ANSWERED_ENTRY_STATUSES && (this.slotId == slotId || this.slotId == ENTRY_SLOT_SENTINEL)
