package com.jjrapps.constanza.reminding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** design.md §5.6 consequence 3, §11: the runtime permission gate exists only from API 33
 *  (`Build.VERSION_CODES.TIRAMISU`), named here as a literal so it stays a `const val` Kotlin can
 *  fold at compile time regardless of how the platform constant itself is packaged. */
private const val RUNTIME_GATE_SDK = 33

/** reminder-response: Notification Permission Scope — the four states a caller (the future
 *  onboarding/settings UI, work unit 6a/6b) needs to branch on. */
enum class NotificationPermissionDecision {
    /** API 31-32: `POST_NOTIFICATIONS` does not exist. Never enter the runtime-permission branch
     *  at all — not even to check it. */
    NOT_APPLICABLE,

    /** API 33+ and already granted. */
    GRANTED,

    /** API 33+, not granted, never asked before: the contextual moment to request. */
    SHOULD_REQUEST,

    /** API 33+, not granted, already asked once. The system blocks a second prompt with no
     *  re-prompt path — the only offer left is a deep link to system settings (design.md §11). */
    BLOCKED,
}

/**
 * Pure decision table, no Android dependency, unit-testable with plain values.
 *
 * `hasRequestedBefore` approximates the system's own "denied twice, no more prompting" state
 * without needing an `Activity` reference here (`shouldShowRequestPermissionRationale` requires
 * one, and is indistinguishable between "never asked" and "permanently blocked" on its own). The
 * caller persists that flag once, the first time it actually shows the system dialog — see
 * [ReminderSettingsStore.recordRequestedNotificationPermission].
 */
fun decideNotificationPermission(
    sdkInt: Int,
    hasPermission: Boolean,
    hasRequestedBefore: Boolean,
): NotificationPermissionDecision = when {
    sdkInt < RUNTIME_GATE_SDK -> NotificationPermissionDecision.NOT_APPLICABLE
    hasPermission -> NotificationPermissionDecision.GRANTED
    hasRequestedBefore -> NotificationPermissionDecision.BLOCKED
    else -> NotificationPermissionDecision.SHOULD_REQUEST
}

/**
 * design.md §5.6 consequence 3 (reminder-response: Notification Permission Scope): the single
 * gate every caller consults instead of branching on `Build.VERSION.SDK_INT` itself. On 31-32,
 * [hasPermission] reports `true` unconditionally — the permission does not exist there, so it is
 * implicitly granted (spec: "delivered with no runtime prompt"). [NotificationPoster] still runs
 * its own `areNotificationsEnabled()` + channel-importance check regardless of this gate, since
 * that is "the check that actually matters" on 31-32 (the user can still mute the channel).
 */
class NotificationPermission @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt < RUNTIME_GATE_SDK ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun decide(hasRequestedBefore: Boolean, sdkInt: Int = Build.VERSION.SDK_INT): NotificationPermissionDecision =
        decideNotificationPermission(sdkInt, hasPermission(sdkInt), hasRequestedBefore)
}
