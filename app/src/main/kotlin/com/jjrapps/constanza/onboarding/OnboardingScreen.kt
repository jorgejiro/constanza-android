package com.jjrapps.constanza.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.reminding.NotificationPermissionDecision

/**
 * design.md §6, §12: the frame every onboarding page renders inside. A `Scaffold` with a real
 * `bottomBar` slot, not a hand-rolled `Column` weight split — the app is edge-to-edge
 * (`MainActivity.onCreate`'s `enableEdgeToEdge`) and `Scaffold` already applies window insets,
 * which a bare `Column` would have to redo by hand. The reference app's repeated `bottom = 160.dp`
 * padding across three pages exists because its button floats over the pager; this layout does not
 * make that structural choice.
 *
 * The bottom-slot primary action is a SIBLING of the page content, always present and always
 * enabled — design.md §6's structural answer to the reference app's dead-button defect: the flow's
 * forward path never routes through the permission control, so even a permission control that
 * somehow no-opped could not trap the user.
 */
@Composable
internal fun OnboardingScaffold(
    state: OnboardingUiState,
    onPrimaryAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
                if (state.showsProgress) {
                    ProgressDots(pageCount = state.pages.size, currentIndex = state.index)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
                Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                    val labelRes = if (state.isLastPage) {
                        R.string.onboarding_action_finish
                    } else {
                        R.string.onboarding_action_continue
                    }
                    Text(stringResource(labelRes))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg)) {
            content()
        }
    }
}

/** design.md §12: active `primary`, inactive `outline` — a pager dot is a selection indicator,
 *  chrome per `ConstanzaColors.Accent`'s own KDoc, reached here through the M3 role, never the
 *  token directly. Renders only when [OnboardingUiState.showsProgress] is true (screen count > 1):
 *  a one-of-one indicator would tell the user there is somewhere else to go when there is not
 *  (design.md §7).
 *
 *  The inactive dot reads `outline` (the control-stroke role) and NOT `outlineVariant`, which is
 *  what it used to read. `outlineVariant` is the decorative-divider role and measures 1.26:1 on the
 *  background, so the inactive dots were, quite literally, not on screen — "1 of 3" looked like
 *  "1 of 1" with two smudges. A pager dot communicates state, so WCAG 2.1 SC 1.4.11's 3:1 floor
 *  applies to it and the decorative exemption does not. `outline` clears that floor at 3.81:1. */
@Composable
private fun ProgressDots(pageCount: Int, currentIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(pageCount) { index ->
            val color = if (index == currentIndex) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            Box(modifier = Modifier.size(Dimens.PagerDot).background(color = color, shape = CircleShape))
        }
    }
}

/** Screen 1 — always present regardless of API level (design.md §7). */
@Composable
internal fun OnboardingIntroPage() {
    Column {
        Text(
            stringResource(R.string.onboarding_screen1_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.onboarding_screen1_body),
            modifier = Modifier.padding(top = Spacing.md),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Screen 2 — API-conditional (design.md §7): present only when [OnboardingViewModel] includes
 *  [OnboardingPage.Notifications] in its page list. [permission] is the LIVE decision, re-read on
 *  `ON_RESUME` by the caller — this composable stays presentational, state in, callback out. */
@Composable
internal fun OnboardingNotificationsPage(
    permission: NotificationPermissionDecision,
    onPermissionRequested: () -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.onboarding_screen2_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        OnboardingPermissionAction(decision = permission, onRequested = onPermissionRequested)
    }
}
