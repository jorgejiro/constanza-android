package com.jjrapps.constanza.tracking

import androidx.room.withTransaction
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.entity.ReminderOccurrenceEntity
import com.jjrapps.constanza.core.data.mapper.toEntity
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.domain.model.Entry
import com.jjrapps.constanza.domain.model.EntryStatus
import com.jjrapps.constanza.reminding.NotificationPoster
import com.jjrapps.constanza.scheduling.AlarmScheduler
import java.time.LocalDate
import javax.inject.Inject

private const val ENTRY_SOURCE_NOTIFICATION = "NOTIFICATION"
private const val ENTRY_SOURCE_IN_APP = "IN_APP"
private const val STATE_RESOLVED = "RESOLVED"

/**
 * Entry states a notification action can reach: Yes/No only (reminder-response: Notification
 * Actions — "`SKIPPED` MUST NOT be offered as a notification action"). Wrapping [EntryStatus]
 * rather than passing it straight through means the notification route cannot construct a
 * `SKIPPED` write even by caller mistake — there is no such member to pass — expressing the
 * restriction in the type system instead of leaving it to a comment or a runtime check.
 */
enum class NotificationEntryStatus(val entryStatus: EntryStatus) {
    COMPLETED(EntryStatus.COMPLETED),
    MISSED(EntryStatus.MISSED),
}

/**
 * Entry states an in-app answer can reach (habit-entry-tracking: Entry States) — the full write
 * surface, including `SKIPPED`, which ratified decision 4 and the spec restrict to this route.
 */
enum class InAppEntryStatus(val entryStatus: EntryStatus) {
    COMPLETED(EntryStatus.COMPLETED),
    MISSED(EntryStatus.MISSED),
    SKIPPED(EntryStatus.SKIPPED),
}

/**
 * Task 6b.2 — the write path task 5.4's wording assumed already existed. [answerOccurrence] (the
 * notification action route, moved here unchanged from the former `AnswerResponder`) and
 * [answerInApp] (the Today screen route) both funnel a resolved occurrence through
 * [resolveOccurrenceAndWrite], so the two provably run the same transactional
 * upsert-then-resolve-then-cancel sequence rather than two paths that merely look alike.
 *
 * The upsert (`entryDao.upsert` → `OnConflictStrategy.REPLACE` against `UNIQUE(habitId, date,
 * slotId)`, design.md §8.2) stays idempotent regardless of caller — a redelivered notification
 * action and a repeated in-app tap both converge on the same single row. Crediting always comes
 * from the occurrence's own `scheduledDate`, never "today" (habit-entry-tracking: Provisional-
 * Missed Correction / Origin-Date Crediting), so an in-app answer given after midnight for a
 * still-live snooze credits the same date a notification answer would.
 */
class EntryWriter @Inject constructor(
    private val database: AppDatabase,
    private val entryDao: EntryDao,
    private val reminderOccurrenceDao: ReminderOccurrenceDao,
    private val alarmScheduler: AlarmScheduler,
    private val notificationPoster: NotificationPoster,
    private val timeProvider: TimeProvider,
) {
    /** Notification-action route. A missing/already-resolved [occurrenceId] is a no-op — a
     *  redelivered broadcast for a resolved occurrence must not resurrect it. */
    suspend fun answerOccurrence(occurrenceId: Long, status: NotificationEntryStatus) {
        val occ = reminderOccurrenceDao.findById(occurrenceId) ?: return
        resolveOccurrenceAndWrite(occ, status.entryStatus, ENTRY_SOURCE_NOTIFICATION)
    }

    /**
     * In-app Today screen route (task 6b.2/6b.3). [occurrenceId] is non-null for a slot with a
     * live/unresolved occurrence — a pending or snoozed slot (task 6b.3) — and that write is
     * routed through the exact same [resolveOccurrenceAndWrite] the notification action uses,
     * crediting the occurrence's origin date and cancelling its alarm and notification. It is
     * null for a slot with nothing armed at all (a habit saved without a reminder time, task
     * 6a.8), which credits [date] directly with nothing to resolve or cancel.
     */
    suspend fun answerInApp(
        habitId: Long,
        date: LocalDate,
        slotId: Long?,
        status: InAppEntryStatus,
        occurrenceId: Long? = null,
    ) {
        val occ = occurrenceId?.let { reminderOccurrenceDao.findById(it) }
        if (occ != null) {
            resolveOccurrenceAndWrite(occ, status.entryStatus, ENTRY_SOURCE_IN_APP)
        } else {
            writeEntry(habitId, date, slotId, status.entryStatus, ENTRY_SOURCE_IN_APP)
        }
    }

    private suspend fun resolveOccurrenceAndWrite(occ: ReminderOccurrenceEntity, status: EntryStatus, source: String) {
        val slotId = if (occ.slotId == 0L) null else occ.slotId
        database.withTransaction {
            writeEntry(occ.habitId, LocalDate.parse(occ.scheduledDate), slotId, status, source)
            reminderOccurrenceDao.upsert(occ.copy(state = STATE_RESOLVED))
        }
        alarmScheduler.cancel(occ.id)
        notificationPoster.cancel(occ.id)
    }

    private suspend fun writeEntry(habitId: Long, date: LocalDate, slotId: Long?, status: EntryStatus, source: String) {
        val entry = Entry(
            habitId = habitId, date = date, slotId = slotId, status = status, answeredAt = timeProvider.now(),
        )
        entryDao.upsert(entry.toEntity(source))
    }
}
