package com.jjrapps.constanza.reminding

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
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

private const val OCCURRENCE_ID = 42L

/**
 * reminder-response: Notification Actions / Notification Permission Scope (task 5.1). Mocks
 * [NotificationManagerCompat]'s static factory the same way `AlarmSchedulerTest` mocks
 * [android.app.PendingIntent.getBroadcast] — no Robolectric, no real `Notification` construction
 * in this test class. [canPost]'s decision table is exercised here; the actual notification
 * content and its three actions are proven on-device (`NotificationPosterInstrumentedTest`).
 */
class NotificationPosterTest {

    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<NotificationManagerCompat>(relaxUnitFun = true)
    private lateinit var poster: NotificationPoster

    @Before
    fun setUp() {
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(context) } returns manager
        poster = NotificationPoster(context)
    }

    @After
    fun tearDown() = unmockkStatic(NotificationManagerCompat::class)

    @Test
    fun `notifications disabled at the system level blocks posting`() {
        every { manager.areNotificationsEnabled() } returns false

        assertFalse(poster.canPost())
    }

    @Test
    fun `enabled with no channel queried yet defaults to postable`() {
        every { manager.areNotificationsEnabled() } returns true
        every { manager.getNotificationChannel(any()) } returns null

        assertTrue(poster.canPost())
    }

    @Test
    fun `enabled but the channel itself is muted blocks posting`() {
        val mutedChannel = mockk<NotificationChannel>()
        every { mutedChannel.importance } returns NotificationManager.IMPORTANCE_NONE
        every { manager.areNotificationsEnabled() } returns true
        every { manager.getNotificationChannel(any()) } returns mutedChannel

        assertFalse(poster.canPost())
    }

    @Test
    fun `enabled with an active channel is postable`() {
        val activeChannel = mockk<NotificationChannel>()
        every { activeChannel.importance } returns NotificationManager.IMPORTANCE_HIGH
        every { manager.areNotificationsEnabled() } returns true
        every { manager.getNotificationChannel(any()) } returns activeChannel

        assertTrue(poster.canPost())
    }

    @Test
    fun `the gate is re-checked on every call, never cached from a prior post`() {
        every { manager.areNotificationsEnabled() } returns true andThen false
        every { manager.getNotificationChannel(any()) } returns null

        assertTrue(poster.canPost())
        assertFalse(poster.canPost())
        verify(exactly = 2) { manager.areNotificationsEnabled() }
    }

    @Test
    fun `postReminder skips notify entirely when posting is blocked`() {
        every { manager.areNotificationsEnabled() } returns false

        poster.postReminder(OCCURRENCE_ID, "Meditate", "Did you meditate today?", 0)

        verify(exactly = 0) { manager.notify(any<Int>(), any()) }
    }
}
