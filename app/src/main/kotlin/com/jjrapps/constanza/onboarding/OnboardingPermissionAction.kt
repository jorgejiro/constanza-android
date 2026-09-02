package com.jjrapps.constanza.onboarding

import android.Manifest
import android.content.Intent
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
