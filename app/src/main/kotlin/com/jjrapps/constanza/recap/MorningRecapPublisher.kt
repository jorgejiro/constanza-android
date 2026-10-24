package com.jjrapps.constanza.recap

import com.jjrapps.constanza.tracking.DayHabitRowsAssembler
import java.time.LocalDate
import javax.inject.Inject

/** Posts the morning recap of [publish]'s day when the owner has it on and the day had something
 *  to count — the same two gates as [WeeklyRecapPublisher]. */
class MorningRecapPublisher @Inject constructor(
    private val settingsStore: MorningRecapSettingsStore,
    private val rowsAssembler: DayHabitRowsAssembler,
    private val notificationPoster: MorningRecapNotificationPoster,
) {
    suspend fun publish(day: LocalDate) {
        if (!settingsStore.currentEnabled()) return
        val summary = summarizeDay(day, rowsAssembler.rowsFor(day))
        if (summary.total > 0) notificationPoster.postRecap(summary)
    }
}
