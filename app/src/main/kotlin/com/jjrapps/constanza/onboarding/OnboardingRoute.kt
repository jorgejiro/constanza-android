package com.jjrapps.constanza.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Container: [hiltViewModel], `ON_RESUME` re-read mirroring
 * `com.jjrapps.constanza.tracking.TodayScreen`'s identical `DisposableEffect` +
 * `LifecycleEventObserver` idiom (design.md §6), and [onFinished] hoisted out to
 * `com.jjrapps.constanza.core.ui.MainActivity`'s gate.
 *
 * **The ordering contract (design.md §9) lives at this single call site:** [onFinished] runs
 * BEFORE [OnboardingViewModel.finish]'s suspend `DataStore` write is even requested, never after.
 * If the emission could ever beat the seed, the gate would compose the app at its default start
 * route and `rememberSaveable` would latch it permanently — ordering the two calls here removes
 * the need to rely on the write being slower than the click handler returning.
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    OnboardingScaffold(
        state = state,
        onPrimaryAction = {
            if (state.isLastPage) {
                onFinished()
                viewModel.finish()
            } else {
                viewModel.next()
            }
        },
    ) {
        when (state.page) {
            OnboardingPage.Intro -> OnboardingIntroPage()
            OnboardingPage.Permissions -> OnboardingPermissionsPage(
                permission = state.permission,
                onPermissionRequested = viewModel::recordRequestedNotificationPermission,
            )
        }
    }
}
