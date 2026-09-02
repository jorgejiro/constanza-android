package com.jjrapps.constanza.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.reminding.NotificationPermission
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** first-run-onboarding design.md §7: the two-screen, applicability-derived flow.
 *  [OnboardingPage.Permissions] is the permissions screen, present only when at least one of the
 *  two asks it hosts currently applies and is not already satisfied; [OnboardingPage.Intro] always
 *  exists. */
enum class OnboardingPage { Intro, Permissions }

/**
 * design.md §7 — everything the API 31-32 divergence can break is derived from [pages], never
 * restated: [isLastPage] reads `pages.lastIndex`, never a literal `1`, since on API 31-32 screen 1
 * IS the last page and any code comparing against `1` breaks silently on that leg.
 */
data class OnboardingUiState(
    val pages: List<OnboardingPage>,
    val index: Int,
    val permission: NotificationPermissionDecision,
    val canScheduleExactAlarms: Boolean,
) {
    val page: OnboardingPage get() = pages[index]
    val isLastPage: Boolean get() = index == pages.lastIndex
    val showsProgress: Boolean get() = pages.size > 1
}

/**
 * first-run-onboarding design.md §4.1, A2, A4: the flow's own state machine, deliberately separate
 * from `FirstRunGateViewModel` so the gate's correctness never depends on it — the gate must be
 * provable by one question ("what does the flag say?"), and a combined holder would make its unit
 * test carry the whole flow.
 *
 * [pages] is computed ONCE, at construction, from API applicability alone (design.md A4). The live
 * permission decision drives only screen 2's content, never the page list, so granting the
 * permission mid-flow cannot delete the page the user is standing on — no index-out-of-bounds, no
 * silent jump, no "last page" label changing under the user's finger.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val notificationPermission: NotificationPermission,
    private val settingsStore: ReminderSettingsStore,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    // decide()'s first branch is `sdkInt < 33 -> NOT_APPLICABLE`, which ignores the flag entirely,
    // so passing `false` here is sound: the flag only ever discriminates SHOULD_REQUEST from
    // BLOCKED, and both of those are "applicable" — the same argument
    // com.jjrapps.constanza.tracking.TodayViewModel already makes for its own construction-time
    // seed (design.md §7).
    //
    // design.md decision 1: this is an OR of two independent applicability facts, notification and
    // exact-alarm — neither one alone decides whether screen 2 exists. Evaluated once, here, at
    // construction: a live re-derivation would be able to delete the page the user is standing on
    // (design.md decision 1's rejected alternative).
    private val includesPermissionPage =
        notificationPermission.decide(hasRequestedBefore = false) != NotificationPermissionDecision.NOT_APPLICABLE ||
            !alarmScheduler.canScheduleExactAlarms()

    private val pages: List<OnboardingPage> = buildList {
        add(OnboardingPage.Intro)
        if (includesPermissionPage) add(OnboardingPage.Permissions)
    }

    private val index = MutableStateFlow(0)

    /** Seeded assuming no prior ask; corrected by the `init` refresh below once the suspend
     *  `DataStore` read returns — construction stays non-blocking, mirroring
     *  `com.jjrapps.constanza.tracking.TodayViewModel`'s identical seed/refresh split. */
    private val permission = MutableStateFlow(notificationPermission.decide(hasRequestedBefore = false))

    /** design.md decision 3: a plain `Boolean`, seeded synchronously — `AlarmScheduler.canScheduleExactAlarms()`
     *  is a direct `AlarmManager` call, not a suspend `DataStore` read, so unlike [permission] it needs
     *  no separate construction-time/refresh split. */
    private val canScheduleExactAlarms = MutableStateFlow(alarmScheduler.canScheduleExactAlarms())

    val uiState: StateFlow<OnboardingUiState> = combine(
        index,
        permission,
        canScheduleExactAlarms,
    ) { currentIndex, decision, exactAlarms ->
        OnboardingUiState(pages, currentIndex, decision, exactAlarms)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        OnboardingUiState(pages, 0, permission.value, canScheduleExactAlarms.value),
    )

    init {
        refresh()
    }

    /** Only ever called from [OnboardingRoute] when `!state.isLastPage` (design.md §9's ordering
     *  contract), so `index + 1` is always a valid page. */
    fun next() {
        index.value += 1
    }

    /** Called from [OnboardingRoute] on `ON_RESUME` (design.md §6, decision 4) — the re-read is
     *  load-bearing, not symmetry: the `BLOCKED` action and the exact-alarm settings deep link both
     *  leave the app for system settings, and without re-reading both facts here the user grants one
     *  or the other there, comes back, and screen 2 still says they are denied. One method reading
     *  both facts in one coroutine, deliberately: two separate refresh methods would let a future
     *  lifecycle call site refresh one and forget the other. */
    fun refresh() {
        viewModelScope.launch {
            readPermission()
            canScheduleExactAlarms.value = alarmScheduler.canScheduleExactAlarms()
        }
    }

    /** Called once the native permission dialog has returned, whatever the user answered. The
     *  persisted flag means "we have asked", not "the user agreed" (design.md §2.2) — a denial
     *  must record it too, or the control would keep offering a prompt the system will never show
     *  again. */
    fun recordRequestedNotificationPermission() {
        viewModelScope.launch {
            settingsStore.recordRequestedNotificationPermission()
            readPermission()
        }
    }

    /** Onboarding's write-once completion commit (design.md §9): fired at handoff, never gated on
     *  whether a habit is subsequently created or saved. Idempotent — a repeat is a no-op edit. */
    fun finish() {
        viewModelScope.launch { settingsStore.setOnboardingDone() }
    }

    private suspend fun readPermission() {
        permission.value = notificationPermission.decide(settingsStore.hasRequestedNotificationPermission())
    }
}
