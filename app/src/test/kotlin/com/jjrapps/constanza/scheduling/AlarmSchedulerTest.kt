package com.jjrapps.constanza.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val EPOCH_MILLIS = 1_800_000_000_000L
private const val OCCURRENCE_ID = 42L

/** reminder-delivery: Exact-Alarm Scheduling, Exact-Alarm Permission States (task 4a.6). Mocks
 *  [AlarmManager] and the static [PendingIntent.getBroadcast] via MockK — no Robolectric — so
 *  `AGP`'s mockable `android.jar` never has a real body invoked for either. */
class AlarmSchedulerTest {

    private val alarmManager = mockk<AlarmManager>(relaxUnitFun = true)
    private val context = mockk<Context>(relaxed = true)
    private val pendingIntent = mockk<PendingIntent>()
    private val scheduler = AlarmScheduler(alarmManager, context)

    @Before
    fun setUp() {
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pendingIntent
    }

    @After
    fun tearDown() {
        unmockkStatic(PendingIntent::class)
    }

    @Test
    fun `exact-alarm eligibility granted schedules exactly and reports exact`() {
        every { alarmManager.canScheduleExactAlarms() } returns true

        val exact = scheduler.schedule(OCCURRENCE_ID, EPOCH_MILLIS)

        assertTrue(exact)
        verify { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, EPOCH_MILLIS, pendingIntent) }
        verify(exactly = 0) { alarmManager.setWindow(any(), any(), any(), any()) }
    }

    @Test
    fun `exact-alarm eligibility denied degrades to a window of at least 10 minutes and reports inexact`() {
        every { alarmManager.canScheduleExactAlarms() } returns false

        val exact = scheduler.schedule(OCCURRENCE_ID, EPOCH_MILLIS)

        assertFalse(exact)
        val tenMinutesMs = 10 * 60 * 1000L
        verify { alarmManager.setWindow(AlarmManager.RTC_WAKEUP, EPOCH_MILLIS, tenMinutesMs, pendingIntent) }
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun `eligibility is re-checked on every call, not cached from a prior schedule`() {
        every { alarmManager.canScheduleExactAlarms() } returns true
        scheduler.schedule(OCCURRENCE_ID, EPOCH_MILLIS)
        every { alarmManager.canScheduleExactAlarms() } returns false

        val secondCallExact = scheduler.schedule(OCCURRENCE_ID, EPOCH_MILLIS)

        assertFalse(secondCallExact)
        verify(exactly = 2) { alarmManager.canScheduleExactAlarms() }
    }

    @Test
    fun `cancel cancels the alarm armed for that occurrence id`() {
        scheduler.cancel(OCCURRENCE_ID)

        verify { alarmManager.cancel(pendingIntent) }
    }
}
