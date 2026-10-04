package com.jjrapps.constanza.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.PrimaryPillButton
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing
import com.jjrapps.constanza.reminding.NotificationPermissionDecision

/** Graphite redesign: the onboarding page title — 32sp/600, tight tracking, balanced line breaks. */
private val OnboardingTitleSize = 32.sp
private val OnboardingTitleLineHeight = 38.sp
private const val ONBOARDING_TITLE_TRACKING = -0.02

/** The onboarding body copy: 17sp with a 1.5 line height (25.5sp), in the secondary text tone. */
private val OnboardingBodySize = 17.sp
private val OnboardingBodyLineHeight = 25.5.sp

/** Gap between pager segments, and between the page body and its permission rows. */
private val PagerSegmentGap = 6.dp

@Composable
internal fun onboardingTitleStyle(): TextStyle = MaterialTheme.typography.headlineSmall.copy(
    fontSize = OnboardingTitleSize,
    lineHeight = OnboardingTitleLineHeight,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = ONBOARDING_TITLE_TRACKING.em,
    lineBreak = LineBreak.Heading,
)

@Composable
internal fun onboardingBodyStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontSize = OnboardingBodySize,
    lineHeight = OnboardingBodyLineHeight,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.sp,
)

/**
 * design.md §6, §12: the frame every onboarding page renders inside. A `Scaffold` with a real
 * `bottomBar` slot, not a hand-rolled `Column` weight split — the app is edge-to-edge
 * (`MainActivity.onCreate`'s `enableEdgeToEdge`) and `Scaffold` already applies window insets,
 * which a bare `Column` would have to redo by hand.
 *
 * The bottom-slot primary action is a SIBLING of the page content, always present and always
 * enabled — design.md §6's structural answer to the reference app's dead-button defect: the flow's
 * forward path never routes through the permission control, so even a permission control that
 * somehow no-opped could not trap the user.
 *
 * Graphite redesign: the progress segments sit top-left; the page content is vertically centred
 * in the space between them and the bottom bar (scrolling when it does not fit); the primary
 * action is the full-width 52dp light [PrimaryPillButton]. The permission asks stay inside the
 * page as equal-weight outlined pills rather than moving into the bottom bar, because the
 * onboarding spec gives the notification and exact-alarm asks equal visual and interactive weight
 * and keeps the forward path free of either.
 */
@Composable
internal fun OnboardingScaffold(
    state: OnboardingUiState,
    onPrimaryAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.lg)) {
                val labelRes = if (state.isLastPage) {
                    R.string.onboarding_action_finish
                } else {
                    R.string.onboarding_action_continue
                }
                PrimaryPillButton(text = stringResource(labelRes), onClick = onPrimaryAction)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.xl)) {
            Box(modifier = Modifier.height(Spacing.xxl + Spacing.lg).padding(top = Spacing.xl)) {
                if (state.showsProgress) {
                    ProgressSegments(pageCount = state.pages.size, currentIndex = state.index)
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight),
                    verticalArrangement = Arrangement.Center,
                ) {
                    content()
                }
            }
        }
    }
}

/** design.md §12, graphite redesign: one 24x4dp segment per page, the current one in the text
 *  colour and the others in the control stroke. Renders only when
 *  [OnboardingUiState.showsProgress] is true (screen count > 1): a one-of-one indicator would tell
 *  the user there is somewhere else to go when there is not (design.md §7).
 *
 *  The other segments read `outline` (the control-stroke role, 3.53:1 on the background) and NOT
 *  `outlineVariant`, the decorative-divider role at 1.22:1: a pager indicator communicates state,
 *  so WCAG 2.1 SC 1.4.11's 3:1 floor applies and the decorative exemption does not. */
@Composable
private fun ProgressSegments(pageCount: Int, currentIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(PagerSegmentGap)) {
        repeat(pageCount) { index ->
            val color = if (index == currentIndex) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(width = Dimens.PagerSegmentWidth, height = Dimens.PagerSegmentHeight)
                    .background(color = color, shape = CircleShape),
            )
        }
    }
}

/** Screen 1 — always present regardless of API level (design.md §7). */
@Composable
internal fun OnboardingIntroPage() {
    Column {
        Text(
            stringResource(R.string.onboarding_screen1_title),
            style = onboardingTitleStyle(),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.onboarding_screen1_body),
            modifier = Modifier.padding(top = Spacing.lg),
            style = onboardingBodyStyle(),
            color = ConstanzaColors.OnBackgroundVariant,
        )
    }
}

/** Screen 2 — applicability-derived (design.md §7): present only when [OnboardingViewModel]
 *  includes [OnboardingPage.Permissions] in its page list. Hosts BOTH permission rows, the
 *  notification row first (spec ordering: delivery severity, not a ranking of the two asks — a
 *  denied notification silences the app, a denied exact alarm only widens the delivery window).
 *  Each row decides its own visibility: [OnboardingPermissionAction] renders nothing for
 *  [NotificationPermissionDecision.NOT_APPLICABLE], so on the API 31-32 leg where only the
 *  exact-alarm ask applies, this page shows exactly one row despite always composing both calls.
 *  [permission] and [canScheduleExactAlarms] are both LIVE, re-read on `ON_RESUME` by the caller —
 *  this composable stays presentational, state in, callback out. */
@Composable
internal fun OnboardingPermissionsPage(
    permission: NotificationPermissionDecision,
    canScheduleExactAlarms: Boolean,
    onPermissionRequested: () -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.onboarding_screen2_title),
            style = onboardingTitleStyle(),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        OnboardingPermissionAction(decision = permission, onRequested = onPermissionRequested)
        Spacer(modifier = Modifier.height(Spacing.xl))
        OnboardingExactAlarmAction(canSchedule = canScheduleExactAlarms)
    }
}
