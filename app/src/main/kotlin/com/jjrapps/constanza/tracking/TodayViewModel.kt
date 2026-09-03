package com.jjrapps.constanza.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.time.TimeProvider
import com.jjrapps.constanza.habit.HabitRepository
import com.jjrapps.constanza.reminding.NotificationPermission
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Both permission-backed banners folded into one value before the screen-wide `combine`.
 *  `combine`'s largest typed overload takes five flows and this screen already had five sources;
 *  bundling the two banner flags keeps the outer `combine` typed rather than dropping to the
 *  vararg overload, which erases every source to `Any?` and costs a cast per element. */
private data class PermissionBanners(
    val canScheduleExactAlarms: Boolean,
    val notificationPermission: NotificationPermissionDecision,
)

/** today-answered-slot-collapse, design.md decision 5: [expandedHabitIds] and [reopenedSlots] are
 *  bundled for the identical reason [PermissionBanners] is — the screen-wide `combine` below is
 *  already at its five-typed-source ceiling, so a sixth source is folded into an existing shape
 *  rather than dropping to the vararg overload, which erases every source to `Any?`. Both flags
 *  are presented state only, never persisted, since neither means anything once the day rolls
 *  over — the same reasoning [expandedHabitIds] already carried alone. */
private data class ExpansionState(
    val expandedHabitIds: Set<Long>,
    val reopenedSlots: Set<TodaySlotKey>,
)

/** Task 6b.1 (habit-entry-tracking: Day-Level Rollup and Per-Slot Display). [expandedHabitIds]
 *  is presented state only — which multi-slot rows the user opened — never persisted, since it is
 *  meaningless once the day rolls over. [answer] is the ONLY write path this screen uses; it
 *  always goes through [entryWriter] (task 6b.2), the same one the notification action route
 *  uses. [canScheduleExactAlarms] backs task 6b.9's non-blocking banner (design §12/§13.1); it is
 *  re-read via [refreshExactAlarmPermission], since the system permission can change while this
 *  screen is paused (the user granting it from Settings) with no Room write to react to.
 *
 *  [notificationPermissionDecision] backs the second, more consequential banner: without
 *  `POST_NOTIFICATIONS` no reminder arrives at all, so the Today screen is the one place that can
 *  offer a way out of the denied state. It follows exactly the same re-read discipline as the
 *  exact-alarm flag ([refreshNotificationPermission] on `ON_RESUME`), plus one extra write path —
 *  [recordNotificationPermissionRequested] — because the "already asked once" flag is what lets
 *  [NotificationPermission] tell `SHOULD_REQUEST` apart from `BLOCKED`. */
// LongParameterList is suppressed here as a TRUE finding, not a false positive, and narrowly on
// the constructor rather than anywhere wider: eight collaborators is genuinely at the edge, and
// the honest reading is that [NotificationPermission] and [ReminderSettingsStore] are only ever
// used as a pair (read the "already asked" flag, then decide) and would collapse into one injected
// gate. That refactor is deliberately not folded into this fix, which is already the first
// production consumer these two classes have ever had; it belongs in its own change, where the
// exact-alarm side can be considered for the same treatment rather than leaving the screen with
// one bundled permission collaborator and one raw one.
@HiltViewModel
@Suppress("LongParameterList")
class TodayViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val entryDao: EntryDao,
    private val reminderOccurrenceDao: ReminderOccurrenceDao,
    private val entryWriter: EntryWriter,
    private val alarmScheduler: AlarmScheduler,
    private val notificationPermission: NotificationPermission,
    private val reminderSettingsStore: ReminderSettingsStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val today = timeProvider.today()
    private val expandedHabitIds = MutableStateFlow<Set<Long>>(emptySet())

    /** today-answered-slot-collapse, design.md decision 1. Cleared per-slot by [answer], never
     *  otherwise, and dies with this ViewModel — see [ExpansionState]'s KDoc and the design's note
     *  on why `rememberSaveable` would restore a reopened slot against the wrong day. */
    private val reopenedSlots = MutableStateFlow<Set<TodaySlotKey>>(emptySet())
    private val canScheduleExactAlarms = MutableStateFlow(alarmScheduler.canScheduleExactAlarms())

    /** Seeded from the synchronous half of the decision only. `hasRequestedBefore` needs a suspend
     *  DataStore read, so it is assumed `false` here and corrected by the `init` refresh below;
     *  construction stays non-blocking. The seed is already correct whenever the permission is
     *  granted or does not exist (both ignore the flag) — the flag only ever chooses between
     *  `SHOULD_REQUEST` and `BLOCKED`, which is a difference of banner action, not of visibility. */
    private val notificationPermissionDecision =
        MutableStateFlow(notificationPermission.decide(hasRequestedBefore = false))

    private val permissionBanners = combine(
        canScheduleExactAlarms,
        notificationPermissionDecision,
    ) { exactAlarms, notifications -> PermissionBanners(exactAlarms, notifications) }

    private val expansionState = combine(
        expandedHabitIds,
        reopenedSlots,
    ) { expanded, reopened -> ExpansionState(expanded, reopened) }

    val uiState: StateFlow<TodayUiState> = combine(
        habitRepository.observeAll(),
        entryDao.observeByDate(today.toString()),
        reminderOccurrenceDao.observeUnresolved(),
        expansionState,
        permissionBanners,
    ) { habits, entriesToday, unresolved, expansion, banners ->
        val snapshot = TodaySnapshot(entriesToday, unresolved, today)
        val rows = habits.filterNot { it.archived }.mapNotNull { habit ->
            val schedule = habitRepository.findScheduleFor(habit.id) ?: return@mapNotNull null
            val slots = habitRepository.findSlotsFor(habit.id)
            buildTodayHabitRow(habit, schedule, slots, snapshot)
        }
        TodayUiState(
            rows = rows,
            expandedHabitIds = expansion.expandedHabitIds,
            reopenedSlots = expansion.reopenedSlots,
            zone = timeProvider.zone(),
            canScheduleExactAlarms = banners.canScheduleExactAlarms,
            notificationPermission = banners.notificationPermission,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodayUiState(zone = timeProvider.zone()))

    init {
        refreshNotificationPermission()
    }

    fun toggleExpanded(habitId: Long) = expandedHabitIds.update {
        if (habitId in it) it - habitId else it + habitId
    }

    /** today-answered-slot-collapse, design.md decision 1: reveals [slot]'s answer actions again.
     *  Called only from the Change control on an answered slot. */
    fun requestChange(key: TodaySlotKey) = reopenedSlots.update { it + key }

    /** Removing [slot]'s key happens synchronously, BEFORE the write coroutine is launched
     *  (design.md decision 4) — waiting for the Room round-trip would let the reopened buttons
     *  linger for a frame after the tap that is meant to collapse them. If the write itself fails,
     *  the slot still collapses back to its previous status with its own Change control intact, so
     *  nothing here is unrecoverable. */
    fun answer(habitId: Long, slot: TodaySlot, status: InAppEntryStatus) {
        reopenedSlots.update { it - slot.keyIn(habitId) }
        viewModelScope.launch {
            entryWriter.answerInApp(habitId, today, slot.slotId, status, slot.occurrenceId)
        }
    }

    /** Called from [TodayRoute] on `ON_RESUME` — the permission may have changed while this
     *  screen was paused (task 6b.9). */
    fun refreshExactAlarmPermission() {
        canScheduleExactAlarms.value = alarmScheduler.canScheduleExactAlarms()
    }

    /** The `POST_NOTIFICATIONS` counterpart of [refreshExactAlarmPermission], called from the same
     *  `ON_RESUME` hook: the user can grant or revoke the permission from system settings — the
     *  only route left once the decision is `BLOCKED` — and return with nothing to react to. */
    fun refreshNotificationPermission() {
        viewModelScope.launch { readNotificationPermission() }
    }

    /** Called once the native permission dialog has returned, whatever the user answered. The
     *  persisted flag means "we have asked", not "the user agreed": that is precisely the state
     *  [NotificationPermission] uses to approximate the system's no-more-prompting condition, so a
     *  denial must record it too or the banner would keep offering a prompt that never appears. */
    fun recordNotificationPermissionRequested() {
        viewModelScope.launch {
            reminderSettingsStore.recordRequestedNotificationPermission()
            readNotificationPermission()
        }
    }

    private suspend fun readNotificationPermission() {
        notificationPermissionDecision.value =
            notificationPermission.decide(reminderSettingsStore.hasRequestedNotificationPermission())
    }
}

data class TodayUiState(
    val rows: List<TodayHabitRow> = emptyList(),
    val expandedHabitIds: Set<Long> = emptySet(),
    /** today-answered-slot-collapse, design.md decision 1: which answered slots currently show
     *  their answer actions again instead of their status text and Change control. */
    val reopenedSlots: Set<TodaySlotKey> = emptySet(),
    val zone: ZoneId = ZoneId.of("UTC"),
    val canScheduleExactAlarms: Boolean = true,
    /** Defaults to [NotificationPermissionDecision.GRANTED] — the one value that renders nothing —
     *  so no existing preview, test or pre-emission state shows a banner it never asked for. */
    val notificationPermission: NotificationPermissionDecision = NotificationPermissionDecision.GRANTED,
)
