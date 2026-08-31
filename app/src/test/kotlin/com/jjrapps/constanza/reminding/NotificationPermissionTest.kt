package com.jjrapps.constanza.reminding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SDK_31 = 31
private const val SDK_32 = 32
private const val SDK_33 = 33
private const val SDK_37 = 37

/**
 * reminder-response: Notification Permission Scope (task 5.2). [decideNotificationPermission] is
 * a pure decision table exercised with plain values; [NotificationPermission.hasPermission]
 * additionally mocks the static [ContextCompat] gate the same way `AlarmSchedulerTest` mocks
 * [android.app.PendingIntent.getBroadcast].
 */
class NotificationPermissionTest {

    private val context = mockk<Context>(relaxed = true)
    private val permission = NotificationPermission(context)

    @Before
    fun setUp() = mockkStatic(ContextCompat::class)

    @After
    fun tearDown() = unmockkStatic(ContextCompat::class)

    @Test
    fun `API 31 and 32 skip the runtime permission branch entirely regardless of grant state`() {
        assertEquals(
            NotificationPermissionDecision.NOT_APPLICABLE,
            decideNotificationPermission(SDK_31, hasPermission = false, hasRequestedBefore = false),
        )
        assertEquals(
            NotificationPermissionDecision.NOT_APPLICABLE,
            decideNotificationPermission(SDK_32, hasPermission = true, hasRequestedBefore = true),
        )
    }

    @Test
    fun `API 33+ already granted reports granted regardless of request history`() {
        assertEquals(
            NotificationPermissionDecision.GRANTED,
            decideNotificationPermission(SDK_33, hasPermission = true, hasRequestedBefore = false),
        )
    }

    @Test
    fun `API 33+ never requested before is the contextual moment to request`() {
        assertEquals(
            NotificationPermissionDecision.SHOULD_REQUEST,
            decideNotificationPermission(SDK_37, hasPermission = false, hasRequestedBefore = false),
        )
    }

    @Test
    fun `API 33+ denied after already being requested once is permanently blocked with no loop`() {
        assertEquals(
            NotificationPermissionDecision.BLOCKED,
            decideNotificationPermission(SDK_37, hasPermission = false, hasRequestedBefore = true),
        )
    }

    @Test
    fun `below API 33 hasPermission is implicitly true without consulting ContextCompat at all`() {
        assertTrue(permission.hasPermission(sdkInt = SDK_32))

        verify(exactly = 0) { ContextCompat.checkSelfPermission(any(), any()) }
    }

    @Test
    fun `API 33+ hasPermission defers to the live ContextCompat grant check`() {
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } returns PackageManager.PERMISSION_DENIED

        assertFalse(permission.hasPermission(sdkInt = SDK_37))
    }
}
