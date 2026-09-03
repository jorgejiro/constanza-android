@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.jjrapps.constanza.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.core.data.dao.EntryDao
import com.jjrapps.constanza.core.data.dao.ReminderOccurrenceDao
import com.jjrapps.constanza.core.time.CurrentDateSource
import com.jjrapps.constanza.habit.HabitRepository
import com.jjrapps.constanza.reminding.NotificationPermission
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    private val currentDateSource: CurrentDateSource,
) : ViewModel() {

    /** today-midnight-rollover, design.md decision 2: the date the screen currently displays,
     *  seeded synchronously from [CurrentDateSource.today] and kept live by the [init] collection
     *  below plus [refreshDate]'s `ON_RESUME` correction. [uiState]'s `combine` sits inside a
     *  `flatMapLatest` keyed on this flow rather than reading it once, so a rollover re-subscribes
     *  every date-scoped source ([EntryDao.observeByDate] and [TodaySnapshot]) instead of leaving
     *  them pinned to the date the ViewModel happened to be constructed on. */
    private val observedDate = MutableStateFlow(currentDateSource.today())
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

    /** today-midnight-rollover, design.md decision 2: [observedDate] is a KEY outside the
     *  `combine`, never a sixth source inside it — the `combine` below keeps exactly the same five
     *  typed sources it always had. A rollover (or [refreshDate]'s resume correction) changes the
     *  key, `flatMapLatest` cancels the previous inner flow and re-subscribes
     *  [EntryDao.observeByDate] against the new date, and [TodaySnapshot]/[TodayUiState] both take
     *  that same date from the lambda parameter — never from [observedDate] read separately, which
     *  could have already moved on again by the time this lambda runs. */
    val uiState: StateFlow<TodayUiState> = observedDate.flatMapLatest { date ->
        combine(
            habitRepository.observeAll(),
            entryDao.observeByDate(date.toString()),
            reminderOccurrenceDao.observeUnresolved(),
            expansionState,
            permissionBanners,
        ) { habits, entriesToday, unresolved, expansion, banners ->
            val snapshot = TodaySnapshot(entriesToday, unresolved, date)
            val rows = habits.filterNot { it.archived }.mapNotNull { habit ->
                val schedule = habitRepository.findScheduleFor(habit.id) ?: return@mapNotNull null
                val slots = habitRepository.findSlotsFor(habit.id)
                buildTodayHabitRow(habit, schedule, slots, snapshot)
            }
            TodayUiState(
                rows = rows,
                expandedHabitIds = expansion.expandedHabitIds,
                reopenedSlots = expansion.reopenedSlots,
                zone = currentDateSource.zone(),
                date = date,
                canScheduleExactAlarms = banners.canScheduleExactAlarms,
                notificationPermission = banners.notificationPermission,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TodayUiState(zone = currentDateSource.zone(), date = observedDate.value),
    )

    init {
        refreshNotificationPermission()
        viewModelScope.launch { currentDateSource.dates().collect { observedDate.value = it } }
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
     *  nothing here is unrecoverable.
     *
     *  today-midnight-rollover, design.md decision 3: writes against [uiState]'s CURRENT `date`,
     *  never [observedDate] read directly and never a fresh clock read. Both of those can already
     *  have advanced past the date this row was drawn against by the time the user taps it; reading
     *  `uiState.value.date` instead makes "the date written" and "the date the tapped row belongs
     *  to" the same value by construction — this is the fix for the In-App Answer Date Attribution
     *  requirement. */
    fun answer(habitId: Long, slot: TodaySlot, status: InAppEntryStatus) {
        reopenedSlots.update { it - slot.keyIn(habitId) }
        val date = uiState.value.date
        viewModelScope.launch {
            entryWriter.answerInApp(habitId, date, slot.slotId, status, slot.occurrenceId)
        }
    }

    /** Called from [TodayRoute] on `ON_RESUME`, alongside [refreshExactAlarmPermission] and
     *  [refreshNotificationPermission] (task 2.7): corrects [observedDate] if the app was
     *  backgrounded across local midnight, per the spec's "backgrounded app corrects the date on
     *  resume" scenario. A rollover that already fired while foregrounded is a no-op here — the
     *  timer already advanced [observedDate], and `MutableStateFlow` conflates the identical value. */
    fun refreshDate() {
        observedDate.value = currentDateSource.today()
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
    /** today-midnight-rollover, design.md decisions 2-3: the date this state's [rows] and
     *  [TodaySnapshot] were built against — never re-derived from the clock at the point of use.
     *  [TodayViewModel] always constructs a real value from [CurrentDateSource]; the placeholder
     *  default here exists only so previews/tests that do not care about the date compile without
     *  naming one, the same reason [zone] defaults to a fixed value instead of an ambient read. */
    val date: LocalDate = LocalDate.EPOCH,
    val canScheduleExactAlarms: Boolean = true,
    /** Defaults to [NotificationPermissionDecision.GRANTED] — the one value that renders nothing —
     *  so no existing preview, test or pre-emission state shows a banner it never asked for. */
    val notificationPermission: NotificationPermissionDecision = NotificationPermissionDecision.GRANTED,
)
