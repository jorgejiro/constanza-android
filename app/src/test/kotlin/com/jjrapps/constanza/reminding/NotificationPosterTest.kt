package com.jjrapps.constanza.reminding

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.jjrapps.constanza.localization.AppLocaleController
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
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
    private val appLocaleController = mockk<AppLocaleController>()
    private lateinit var poster: NotificationPoster

    @Before
    fun setUp() {
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(context) } returns manager
        coEvery { appLocaleController.localizedApplicationContext() } returns context
        poster = NotificationPoster(context, appLocaleController)
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

    /** design.md §13.4 finding 1 (task G.3): the gate's "never a silent lie about delivery" half is
     *  only keepable if the caller can tell a gated call from a posted one, so the skip is reported
     *  rather than swallowed. The `true` return is proven on-device instead, where a real
     *  `Notification` can be built (`NotificationPosterInstrumentedTest`). */
    @Test
    fun `postReminder skips notify entirely and reports no post when posting is blocked`() = runTest {
        every { manager.areNotificationsEnabled() } returns false

        assertFalse(poster.postReminder(OCCURRENCE_ID, "Meditate", "Did you meditate today?", 0))

        verify(exactly = 0) { manager.notify(any<Int>(), any()) }
    }
}
