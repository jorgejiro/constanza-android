package com.jjrapps.constanza.habit

import androidx.room.withTransaction
import com.jjrapps.constanza.core.data.AppDatabase
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.ReminderSlotDao
import javax.inject.Inject

/**
 * Pays D11's explicit cost of dropping `entries.slotId`'s foreign key: since Room can no longer
 * cascade a `reminder_slots` delete into `entries`, [deleteSlot] does it by hand inside one
 * transaction. `AppDatabase.withTransaction` (room-ktx) is used rather than a DAO-level
 * `@Transaction` method because the transaction spans two DAOs, which a single `@Transaction`
 * method cannot express.
 *
 * Entries recorded against the deleted slot are removed rather than reassigned to the `0`
 * sentinel: reassigning risks colliding with an existing `(habitId, date, 0)` row and violating
 * the very `UNIQUE(habitId, date, slotId)` constraint D11 exists to enforce, whereas the slot no
 * longer exists to answer for that history once deleted.
 */
class HabitRepository @Inject constructor(
    private val database: AppDatabase,
    private val reminderSlotDao: ReminderSlotDao,
    private val entryDao: EntryDao,
) {
    suspend fun deleteSlot(habitId: Long, slotId: Long) {
        database.withTransaction {
            entryDao.deleteBySlot(habitId, slotId)
            reminderSlotDao.deleteById(slotId)
        }
    }
}
