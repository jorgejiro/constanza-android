package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.reminding.NotificationPermission
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import io.mockk.coEvery
import io.mockk.mockk

/**
 * [TodayViewModel]'s two notification-permission collaborators, stubbed into the one combination
 * that renders no banner at all.
 *
 * Shared rather than repeated in each test for the same reason the alarm scheduler is stubbed
 * explicitly at every one of those call sites: the real [NotificationPermission] reads the actual
 * device grant, so on a device that has never been asked it would add an unrelated banner item to
 * the top of the Today list and quietly shift every assertion about clipping, ordering or tap
 * targets in tests that are not about notifications.
 */
fun grantedNotificationPermission(): NotificationPermission = mockk {
    coEvery { decide(any(), any()) } returns NotificationPermissionDecision.GRANTED
}

/** The matching store: nothing has ever been asked, so nothing is recorded. */
fun neverAskedReminderSettingsStore(): ReminderSettingsStore = mockk {
    coEvery { hasRequestedNotificationPermission() } returns false
}
