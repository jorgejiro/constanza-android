package com.jjrapps.constanza.recap

import java.time.LocalDate
import javax.inject.Inject

/**
 * weekly-recap S3/S8: what one Monday fire does for the week starting [publish]'s `weekStart` —
 * nothing while the owner has the recap turned off (not even the read), nothing for a week with
 * nothing to count, otherwise the notification. Kept apart from
 * [com.jjrapps.constanza.scheduling.WeeklyRecapFireWorker] so these rules are unit-tested without a
 * worker; the worker keeps the timing and the re-arm.
 */
class WeeklyRecapPublisher @Inject constructor(
    private val settingsStore: WeeklyRecapSettingsStore,
    private val loader: WeeklyRecapLoader,
    private val notificationPoster: WeeklyRecapNotificationPoster,
) {
    suspend fun publish(weekStart: LocalDate) {
        if (!settingsStore.currentEnabled()) return
        val recap = loader.load(weekStart)
        if (recap.total > 0) notificationPoster.postRecap(recap)
    }
}
