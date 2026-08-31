package com.jjrapps.constanza.scheduling

import com.jjrapps.constanza.core.data.dao.HabitDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.data.dao.ReminderSlotDao
import com.jjrapps.constanza.core.data.dao.ScheduleDao
import javax.inject.Inject

/** Bundles the four DAOs [OccurrencePlanner] and [OccurrenceResolver] both need, keeping either
 *  constructor under detekt's `LongParameterList` threshold — same reasoning as `SlotPlan`. */
data class SchedulingDaos @Inject constructor(
    val habitDao: HabitDao,
    val scheduleDao: ScheduleDao,
    val reminderSlotDao: ReminderSlotDao,
    val reminderOccurrenceDao: ReminderOccurrenceDao,
)
