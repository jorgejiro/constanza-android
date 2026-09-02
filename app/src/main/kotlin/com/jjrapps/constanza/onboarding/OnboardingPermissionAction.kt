package com.jjrapps.constanza.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.reminding.NotificationPermissionDecision

/**
 * Screen 2's four-state permission control (design.md §6). Its own [LocalContext], its own
 * launcher and its own `Intent` — the reason `OnboardingScreen` stays presentational, the same
 * argument `com.jjrapps.constanza.tracking.TodayBanners` makes for keeping the identical pieces
 * out of `TodayScreen`.
 *
 * **The structural fix for the reference app's dead-button defect is not only that
 * [NotificationPermissionDecision.BLOCKED] has a real action here.** It is that the caller
 * (`OnboardingScreen`'s bottom-slot primary action) never routes through this control at all —
 * design.md §6's non-blocking guarantee expressed in layout, not in a comment. This function
 * renders nothing for [NotificationPermissionDecision.NOT_APPLICABLE] (the page does not exist for
 * that decision, design.md §7) and only a confirmation line for
 * [NotificationPermissionDecision.GRANTED] — a button whose only honest action is "nothing" is
 * exactly the defect being avoided.
 */
@Composable
internal fun OnboardingPermissionAction(
    decision: NotificationPermissionDecision,
    onRequested: () -> Unit,
) {
    val context = LocalContext.current
    // The result is deliberately ignored: the flag records that the dialog was shown, not that it
    // was accepted, and the caller re-reads the real permission state regardless of the answer
    // (mirrors com.jjrapps.constanza.tracking.TodayBanners.NotificationPermissionBanner).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onRequested() }
    when (decision) {
        NotificationPermissionDecision.NOT_APPLICABLE -> Unit

        NotificationPermissionDecision.GRANTED -> Text(
            stringResource(R.string.onboarding_permission_granted_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NotificationPermissionDecision.SHOULD_REQUEST -> Column {
            Text(
                stringResource(R.string.onboarding_permission_should_request_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.padding(top = Spacing.md),
            ) {
                Text(stringResource(R.string.onboarding_permission_should_request_action))
            }
        }

        // Byte-for-byte the com.jjrapps.constanza.tracking.TodayBanners.kt:82-89 gesture — never
        // the launcher, since the system will silently refuse a permanently-blocked prompt.
        NotificationPermissionDecision.BLOCKED -> Column {
            Text(
                stringResource(R.string.onboarding_permission_blocked_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
                modifier = Modifier.padding(top = Spacing.md),
            ) {
                Text(stringResource(R.string.onboarding_permission_blocked_action))
            }
        }
    }
}

/**
 * Screen 2's exact-alarm row (design.md decisions 2, 3, 5, 6). A plain [Boolean], not a decision
 * enum — `SCHEDULE_EXACT_ALARM` has no four-state table the way `POST_NOTIFICATIONS` does, since
 * `minSdk = 31` means the permission concept always exists and the offer is repeatable, so there is
 * no `NOT_APPLICABLE` and no "we have asked" latch.
 *
 * [canSchedule] `true` renders one confirmation line and no button — a button whose only honest
 * action is "nothing" is exactly the dead-button defect this design avoids. `false` renders the
 * degradation copy plus a FILLED [Button] (design decision 5: sibling parity with
 * [OnboardingPermissionAction]'s own controls, not a lighter, "less important" outlined one) that
 * deep-links to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. No launcher and no callback (decision 6): the
 * only outbound edge is `startActivity`, and the caller re-reads the real state on `ON_RESUME`
 * regardless of what the user did in settings.
 */
@Composable
internal fun OnboardingExactAlarmAction(canSchedule: Boolean) {
    val context = LocalContext.current
    if (canSchedule) {
        Text(
            stringResource(R.string.onboarding_exact_alarm_granted_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column {
            Text(
                stringResource(R.string.onboarding_exact_alarm_denied_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
                modifier = Modifier.padding(top = Spacing.md),
            ) {
                Text(stringResource(R.string.onboarding_exact_alarm_denied_action))
            }
        }
    }
}
