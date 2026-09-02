package com.jjrapps.constanza.tracking

import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.reminding.NotificationPermission
import com.jjrapps.constanza.reminding.NotificationPoster
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.scheduling.AlarmScheduler
import io.mockk.every
import io.mockk.mockk

/**
 * A [TodayViewModel] over this fixture's real in-memory database, already registered for teardown.
 *
 * **Why this is an extension rather than a fixture member.** [TodayViewModel]'s collaborators live
 * in `tracking` and `reminding`; [HabitRepositoryTestFixture] lives in `habit`. Wiring them here
 * keeps the fixture from depending upward on packages it otherwise knows nothing about.
 *
 * **Why a factory at all.** The six call sites this replaced were byte-identical, and each of them
 * separately had to remember to cancel `viewModelScope` before `fixture.close()` —
 * `openspec/config.yaml`'s `compose-test-db-teardown-race`. Six copies of one ordering invariant is
 * five too many. [HabitRepositoryTestFixture.close] owns that ordering now, and going through
 * [HabitRepositoryTestFixture.register], as this factory does, is what enrols a ViewModel in it. A
 * ViewModel the fixture never learns about is invisible to teardown, so `ViewModelTeardownCallSiteTest`
 * in `src/test` fails the build if a call site reverts to the bare constructor.
 *
 * **Why the alarm scheduler is stubbed rather than left relaxed** — the note that used to be
 * repeated at every one of those call sites, folded into one place rather than lost with them.
 * `mockk(relaxed = true)` answers a `Boolean` with `false`, so a relaxed [AlarmScheduler] reports
 * that exact alarms are *not* permitted, which arms task 6b.9's exact-alarm banner and prepends an
 * extra item to the Today list. None of these tests are about that banner, and all of them are
 * about something the extra item perturbs: which node comes first, what "centred" means on an empty
 * screen, how much vertical space a row has to be measured within, and whether a label wraps.
 * Stubbing it is part of the scenario, not boilerplate — this codebase's own established
 * `mockk(relaxed = true)` lesson. See [exactAlarmsAllowedScheduler].
 *
 * Every parameter defaults to the collaborator those call sites used verbatim, so calling this with
 * no arguments reproduces them exactly. Override only what a scenario is actually about:
 * `EntryWriteParityTest` passes its own [entryWriter] because it drives that adapter directly and
 * needs the very instance the ViewModel holds. [grantedNotificationPermission] and
 * [neverAskedReminderSettingsStore] (`NotificationPermissionTestDoubles.kt`) are the matching halves
 * of the same "render no banner" setup, reused rather than restated.
 */
fun HabitRepositoryTestFixture.todayViewModel(
    entryWriter: EntryWriter = entryWriter(),
    alarmScheduler: AlarmScheduler = exactAlarmsAllowedScheduler(),
    notificationPermission: NotificationPermission = grantedNotificationPermission(),
    reminderSettingsStore: ReminderSettingsStore = neverAskedReminderSettingsStore(),
): TodayViewModel = register(
    TodayViewModel(
        habitRepository, database.entryDao(), database.reminderOccurrenceDao(),
        entryWriter, alarmScheduler, notificationPermission, reminderSettingsStore, timeProvider,
    ),
)

/**
 * The real [EntryWriter] over this fixture's database, with only [AlarmScheduler] relaxed-mocked —
 * the same wiring [todayViewModel] uses by default, exposed separately for the one test that
 * asserts against the writer as well as through the screen.
 *
 * The scheduler here is left relaxed on purpose: unlike the ViewModel's, this one only re-arms
 * occurrences after a write, and no test observes that. It is [todayViewModel]'s scheduler, the one
 * the banner branch reads, that has to be stubbed explicitly.
 */
fun HabitRepositoryTestFixture.entryWriter(): EntryWriter = EntryWriter(
    database, database.entryDao(), database.reminderOccurrenceDao(),
    mockk<AlarmScheduler>(relaxed = true), NotificationPoster(context), timeProvider,
)

/**
 * A relaxed [AlarmScheduler] whose [AlarmScheduler.canScheduleExactAlarms] is stubbed to `true`.
 *
 * Stubbed rather than left to the relaxed default for the reason [todayViewModel]'s KDoc sets out:
 * `false` silently arms task 6b.9's banner and shifts the assertions of every test that is not
 * about banners.
 */
fun exactAlarmsAllowedScheduler(): AlarmScheduler {
    val alarmScheduler = mockk<AlarmScheduler>(relaxed = true)
    every { alarmScheduler.canScheduleExactAlarms() } returns true
    return alarmScheduler
}
