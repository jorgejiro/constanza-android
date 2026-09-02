package com.jjrapps.constanza.tracking

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.ConstanzaShapes
import com.jjrapps.constanza.reminding.NotificationPermissionDecision

/**
 * The Today screen's two permission banners, kept together and out of [TodayScreen] so the file
 * that owns the habit list is not also the file that owns every permission affordance. They are
 * deliberately the same shape — the same tonal [Surface], the same load-bearing `weight(1f)` — so
 * that when both are showing they read as one family rather than two unrelated warnings.
 *
 * Each keeps its own `LocalContext` and its own launcher or [Intent]. That is what lets
 * [TodayScreen] stay presentational: state in, callbacks out, no Android entry point of its own.
 */

/**
 * Both banners for one [TodayUiState], emitted in the order the screen wants them and nothing at
 * all when neither applies.
 *
 * Kept as one composable rather than two `LazyColumn` items so that [TodayScreen]'s two layouts —
 * the scrolling list and the empty state's plain [androidx.compose.foundation.layout.Column] —
 * can each place the pair with a single call, and cannot drift apart on which banner comes first
 * or on whether one of them was forgotten.
 */
@Composable
internal fun TodayPermissionBanners(state: TodayUiState, onNotificationPermissionRequested: () -> Unit) {
    // Above the exact-alarm banner deliberately: a late reminder is a degraded reminder, a missing
    // permission is no reminder at all.
    if (state.notificationPermission.needsBanner()) {
        NotificationPermissionBanner(
            decision = state.notificationPermission,
            onPermissionRequested = onNotificationPermissionRequested,
        )
    }
    if (!state.canScheduleExactAlarms) {
        ExactAlarmBanner()
    }
}

/** Only the two actionable states put a banner on screen: `GRANTED` needs nothing and
 *  `NOT_APPLICABLE` (API 31-32) has no runtime permission to talk about at all. */
internal fun NotificationPermissionDecision.needsBanner(): Boolean =
    this == NotificationPermissionDecision.SHOULD_REQUEST || this == NotificationPermissionDecision.BLOCKED

/**
 * reminder-response: Notification Permission Scope — the missing consumer of
 * [com.jjrapps.constanza.reminding.NotificationPermission]. Unlike [ExactAlarmBanner] this one is
 * not merely informational: with `POST_NOTIFICATIONS` denied no reminder is posted at all, so this
 * is the only in-app way out of the denied state. Structurally it is the same banner — same
 * [Surface], same load-bearing `weight(1f)` on the text — so the two read as one family when both
 * happen to be showing.
 *
 * `SHOULD_REQUEST` launches the native prompt and records the "we have asked" flag on return,
 * whatever the answer was. `BLOCKED` means the system will no longer show that prompt, so the only
 * honest offer left is the one-tap deep link to the app's notification settings, exactly the
 * gesture [ExactAlarmBanner] already uses for its own permission.
 *
 * The launcher and the [Intent] live here rather than in [TodayScreen] for the same reason
 * [ExactAlarmBanner] keeps its own `LocalContext`: the screen stays presentational, state in and
 * callbacks out, with no Android entry point of its own.
 */
@Composable
internal fun NotificationPermissionBanner(
    decision: NotificationPermissionDecision,
    onPermissionRequested: () -> Unit,
) {
    val context = LocalContext.current
    val blocked = decision == NotificationPermissionDecision.BLOCKED
    // The result is deliberately ignored: the flag records that the dialog was shown, not that it
    // was accepted, and the refresh that follows re-reads the real permission state anyway.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onPermissionRequested() }
    Surface(color = ConstanzaColors.SurfaceRaised, shape = ConstanzaShapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // `weight(1f)` is load bearing here for the same reason it is on ExactAlarmBanner:
            // without it SpaceBetween pushes the action button off the right edge.
            Text(
                stringResource(R.string.today_notification_permission_banner),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            TextButton(onClick = {
                if (blocked) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) {
                val labelRes = if (blocked) {
                    R.string.today_notification_permission_open_settings
                } else {
                    R.string.today_notification_permission_allow
                }
                Text(stringResource(labelRes))
            }
        }
    }
}

/** Task 6b.9 (design §12/§13.1): non-blocking — reminders still fire, degraded to a 10-minute
 *  inexact window (design §13.4's measurement), so this is informational, not a gate. One tap
 *  deep-links to [Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM]; the default on a fresh install
 *  targeting API 33+ is denied, so this is the common path, not an edge case.
 *
 *  Wrapped in a tonal [Surface] (design.md decision 7 — the one deliberate structural surface this
 *  change adds): a banner with no container does not read as a banner, and unlike a per-row `Card`
 *  this is a single `LazyColumn` `item`, not a repeated row, so it costs no measured-height budget
 *  anywhere `TodayAdaptiveComposeTest` looks. */
@Composable
internal fun ExactAlarmBanner() {
    val context = LocalContext.current
    Surface(color = ConstanzaColors.SurfaceRaised, shape = ConstanzaShapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // `weight(1f)` is load bearing, not styling: without it the text takes whatever width
            // it wants and `SpaceBetween` pushes the action button clean off the right edge, so the
            // one tap §13.1 promises becomes unreachable while the banner still looks fine. Found
            // by task G.7's manual matrix on a 1080dp-wide Pixel 10 — the automated `sw = 600dp`
            // test (6b.8) asserts the habit rows, not this banner. Preserved verbatim through the
            // `Surface` wrap above (work unit 4) — this `Row` is not replaced.
            Text(
                stringResource(R.string.today_exact_alarm_banner),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            TextButton(onClick = {
                val uri = Uri.parse("package:${context.packageName}")
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri))
            }) {
                Text(stringResource(R.string.today_exact_alarm_banner_action))
            }
        }
    }
}
