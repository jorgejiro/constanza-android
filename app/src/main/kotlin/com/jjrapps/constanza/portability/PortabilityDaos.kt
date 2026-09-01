package com.jjrapps.constanza.portability

import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.HabitDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.dao.ReminderSlotDao
import com.jjrapps.constanza.core.data.dao.ScheduleDao
import javax.inject.Inject

/** Bundles the five DAOs [BackupExporter]/[BackupImporter] need, keeping their constructors under
 *  detekt's `LongParameterList` threshold — same reasoning as `habit.HabitDaos`/
 *  `scheduling.SchedulingDaos`. */
data class PortabilityDaos @Inject constructor(
    val habitDao: HabitDao,
    val scheduleDao: ScheduleDao,
    val reminderSlotDao: ReminderSlotDao,
    val entryDao: EntryDao,
    val reminderOccurrenceDao: ReminderOccurrenceDao,
)
