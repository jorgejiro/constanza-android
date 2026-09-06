package com.jjrapps.constanza.core.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjrapps.constanza.core.ui.theme.ConstanzaTheme
import com.jjrapps.constanza.habit.HabitEditorRoute
import com.jjrapps.constanza.habit.HabitListRoute
import com.jjrapps.constanza.localization.AppLanguage
import com.jjrapps.constanza.localization.AppLocaleController
import com.jjrapps.constanza.localization.ProvideAppLocale
import com.jjrapps.constanza.onboarding.OnboardingRoute
import com.jjrapps.constanza.progress.ProgressRoute
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import com.jjrapps.constanza.reminding.SnoozeSettingsRoute
import com.jjrapps.constanza.scheduling.ReplanOnResumeObserver
import com.jjrapps.constanza.tracking.TodayRoute
import com.jjrapps.constanza.tracking.TodayViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The single launcher Activity. Hosts the Today screen (work unit 6b, the daily-use home), the
 * habit list, and the editor (work unit 6a, design.md §14).
 *
 * The one non-UI thing it does is register design.md §5.5/§13.1's `onResume()` re-check
 * (task G.5). The decision logic lives in [ReplanOnResumeObserver], not here.
 *
 * §13.1's non-blocking exact-alarm banner (task 6b.9) lives on the Today screen itself —
 * [com.jjrapps.constanza.tracking.TodayScreen] — not here.
 *
 * reminder-notification-tap-opens-today: [onNewIntent] is the other non-UI thing it does. The
 * reminder notification's content [android.app.PendingIntent] (built in
 * [com.jjrapps.constanza.reminding.NotificationPoster]) targets this Activity with
 * `FLAG_ACTIVITY_SINGLE_TOP` set on its own `Intent`, not a manifest `launchMode`: this is a
 * single-Activity app, so [MainActivity] is always alone at the top of its own task, and scoping
 * the flag to that one `Intent` keeps the behaviour change local to the reminder-tap path instead
 * of silently altering how every other future caller of this Activity gets launched. `singleTop`
 * routes a warm tap to [onNewIntent] instead of a stacked second instance; [resetToTodaySignal]
 * is how that event reaches the Compose tree held by [setContent]'s captured lambda, since Compose
 * state — not a plain `var` — is what triggers recomposition.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var replanOnResumeObserver: ReplanOnResumeObserver

    /** reminder-notification-tap-opens-today: starts at 0 (never fired) and counts up once per
     *  qualifying [onNewIntent] call, never resets in between — [ConstanzaApp] reacts to every
     *  distinct value via `LaunchedEffect(resetToTodaySignal)`, including a second tap that arrives
     *  while the app is already on Today. A rotation recreates this Activity and this field with
     *  it (no `android:configChanges`, see the class KDoc above), which is correct: there is no
     *  pending notification tap to replay across that recreation. */
    private var resetToTodaySignal by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pins both system bars to dark-background icon style regardless of the device's
        // system-wide light/dark setting — spec "Cold-Start Window Background And System Bar
        // Icons", scenario "System-bar icons stay legible when the device is set to light mode".
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        lifecycle.addObserver(replanOnResumeObserver)
        setContent {
            ConstanzaTheme {
                FirstRunGate(resetToTodaySignal = resetToTodaySignal)
            }
        }
    }

    /** reminder-notification-tap-opens-today: fires instead of a fresh [onCreate] whenever
     *  `FLAG_ACTIVITY_SINGLE_TOP` finds this Activity already at the top of its task — i.e. every
     *  warm case, since cold start already lands on Today via [ConstanzaApp]'s own default
     *  `startRoute`. [setIntent] keeps [getIntent] consistent with what actually launched this
     *  instance, matching the platform's own documented contract for this callback. Only
     *  [MainActivity.EXTRA_FROM_REMINDER_NOTIFICATION] bumps [resetToTodaySignal] — a future
     *  unrelated new intent to this same Activity must not be misread as a reminder tap. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FROM_REMINDER_NOTIFICATION, false)) {
            resetToTodaySignal++
        }
    }

    companion object {
        /** reminder-notification-tap-opens-today: the reminder notification's content
         *  [android.app.PendingIntent] sets this boolean extra so [onNewIntent] can tell "the user
         *  tapped the notification body" apart from any other way a new `Intent` could reach this
         *  already-running Activity. */
        const val EXTRA_FROM_REMINDER_NOTIFICATION = "com.jjrapps.constanza.EXTRA_FROM_REMINDER_NOTIFICATION"
    }
}

/**
 * Task 6a's navigation decision: no navigation library. A list plus one editor is small enough
 * that hoisted [ConstanzaRoute] state in this single Activity is defensible and cheaper than
 * adding a navigation dependency — work unit 6b adds more screens and can revisit (design.md §14).
 *
 * [java.io.Serializable] (task 6a.7, ui-adaptive-layout): this Activity declares no
 * `android:configChanges`, so a rotation destroys and recreates it (§5.7 C1/C4). Without a
 * [androidx.compose.runtime.saveable.Saver]-compatible route type, plain `remember`'d navigation
 * state does not survive that recreation and the app would silently drop back to the habit list
 * mid-edit — a worse content loss than anything inside the editor screen itself.
 */
private sealed interface ConstanzaRoute : java.io.Serializable {
    data object Today : ConstanzaRoute
    data object HabitList : ConstanzaRoute

    /** first-run-onboarding design.md §5.1: the editor is reachable from places that must leave to
     *  DIFFERENT screens — the habit list, which is its own caller, and the end of onboarding,
     *  whose user has never seen the list and has no reason to be dropped onto it.
     *
     *  [HabitList] does now have its own way back to [Today] (habit-list-back-navigation), so
     *  landing an onboarding user there would no longer strand them; it would merely put them
     *  somewhere they never asked to go, one extra tap from the screen they wanted. That is why
     *  this enum survives the fix rather than collapsing into a single exit.
     *
     *  [Today] (today-add-habit) is a third caller with the SAME exit as [Onboarding] and is
     *  deliberately not folded into it: they leave to the same screen today, but they are different
     *  journeys, and reusing `Onboarding` for a tap on Today would make the route lie about where
     *  the user came from the first time the two need to diverge. */
    enum class EditorOrigin { HabitList, Onboarding, Today }

    data class HabitEditor(val habitId: Long?, val origin: EditorOrigin = EditorOrigin.HabitList) : ConstanzaRoute
    data class Progress(val habitId: Long) : ConstanzaRoute
    data object Settings : ConstanzaRoute
}

/** Task 6b.1: Today is the daily-use home screen; [ConstanzaRoute.HabitList] is reached from its
 *  "Manage habits" action and returns here rather than staying its own top-level destination.
 *  Task 6b.4/6b.5 add two more leaf screens the same way: [ConstanzaRoute.Progress] from
 *  [com.jjrapps.constanza.habit.HabitListRoute]'s per-habit "Progress" action, and
 *  [ConstanzaRoute.Settings] from Today's own "Settings" action.
 *
 *  [startRoute] (first-run-onboarding design.md §5.1) lets [FirstRunGate] seed this composable's
 *  ONE `rememberSaveable` initial value with [ConstanzaRoute.HabitEditor] tagged
 *  [ConstanzaRoute.EditorOrigin.Onboarding], so the handoff from onboarding into habit creation is
 *  a normal route entry rather than a second navigation mechanism.
 *
 *  [resetToTodaySignal] (reminder-notification-tap-opens-today) is [MainActivity.onNewIntent]'s
 *  side of a warm reminder-notification tap. Landing on Today is not enough by itself: task 6b's
 *  [TodayViewModel] is scoped to this Activity's `ViewModelStore`, not to whichever composable
 *  currently has `route`, so it survives untouched while the user is away on another screen —
 *  including any past-day navigation left behind by [TodayViewModel.showPreviousDay]. Setting
 *  `route` back to [ConstanzaRoute.Today] alone would leave that past day showing the moment the
 *  Today screen recomposes. [TodayViewModel] is only obtained here, via [hiltViewModel], once a
 *  tap has actually happened (`resetToTodaySignal > 0`) — never unconditionally — so a session
 *  that never taps a notification never constructs it a moment earlier than [TodayRoute] itself
 *  would have. */
@Composable
private fun ConstanzaApp(startRoute: ConstanzaRoute = ConstanzaRoute.Today, resetToTodaySignal: Int = 0) {
    var route by rememberSaveable { mutableStateOf(startRoute) }
    if (resetToTodaySignal > 0) {
        val todayViewModel: TodayViewModel = hiltViewModel()
        LaunchedEffect(resetToTodaySignal) {
            route = ConstanzaRoute.Today
            todayViewModel.showToday()
        }
    }
    when (val current = route) {
        is ConstanzaRoute.Today -> TodayRoute(
            onManageHabits = { route = ConstanzaRoute.HabitList },
            // today-add-habit: tagged Today, never HabitList — the editor's exits both follow
            // `origin`, so a HabitList-tagged entry would drop a user who never asked for the list
            // onto it. That used to strand them outright; since habit-list-back-navigation gave the
            // list its own exit it only costs them an extra tap, but the tag is still wrong for the
            // same reason it always was: this journey started on Today and ends on Today.
            onAddHabit = {
                route = ConstanzaRoute.HabitEditor(
                    habitId = null,
                    origin = ConstanzaRoute.EditorOrigin.Today,
                )
            },
            onOpenSettings = { route = ConstanzaRoute.Settings },
        )

        is ConstanzaRoute.HabitList -> HabitListRoute(
            // habit-list-back-navigation: the list is reached from Today's "Manage habits" and
            // returns there, by the top bar's arrow or the system back gesture alike. Before this,
            // it had neither, so back fell through to the Activity default and closed the app.
            onBack = { route = ConstanzaRoute.Today },
            onCreateHabit = { route = ConstanzaRoute.HabitEditor(habitId = null) },
            onEditHabit = { habitId -> route = ConstanzaRoute.HabitEditor(habitId) },
            onShowProgress = { habitId -> route = ConstanzaRoute.Progress(habitId) },
        )

        // leaveTo branches on origin (first-run-onboarding design.md §5.1): the habit-list entry
        // returns to the list as before, but the onboarding-seeded entry has never seen the list,
        // so leaving it there would answer "I finished setting up" with a screen the user never
        // asked for. That used to be a dead end as well — the list had no route back to Today at
        // all — and habit-list-back-navigation has since fixed the dead end, not the mismatch:
        // finishing onboarding still belongs on Today. onBack mirrors onDone deliberately: both
        // land on the same destination,
        // and the editor itself owns whether backing out with unsaved edits confirms first
        // (design.md §2.1 — that decision belongs to the editor change, not this one).
        is ConstanzaRoute.HabitEditor -> {
            val leaveTo = when (current.origin) {
                ConstanzaRoute.EditorOrigin.HabitList -> ConstanzaRoute.HabitList
                ConstanzaRoute.EditorOrigin.Onboarding -> ConstanzaRoute.Today
                ConstanzaRoute.EditorOrigin.Today -> ConstanzaRoute.Today
            }
            HabitEditorRoute(
                habitId = current.habitId,
                onDone = { route = leaveTo },
                onBack = { route = leaveTo },
            )
        }

        is ConstanzaRoute.Progress -> ProgressRoute(
            habitId = current.habitId,
            onBack = { route = ConstanzaRoute.HabitList },
        )

        is ConstanzaRoute.Settings -> SnoozeSettingsRoute(
            onBack = { route = ConstanzaRoute.Today },
        )
    }
}

/**
 * The app's SECOND top-level state holder, above [ConstanzaApp]'s hoisted route
 * (first-run-onboarding design.md §4.1, A1, A2). It answers exactly one question — "has this
 * install completed onboarding?" — and nothing else; [OnboardingViewModel] owns the flow's own
 * state machine so the gate's correctness never depends on it.
 *
 * [onboardingDone] is a retained [StateFlow], not a cold [kotlinx.coroutines.flow.Flow] collected
 * with `collectAsState(initial = null)`: a cold flow re-holds `null` on every Activity recreation,
 * which would turn the one-frame blank hold at cold start (invisible, see [FirstRunGate]) into a
 * visible flash on every rotation mid-session. [kotlinx.coroutines.flow.SharingStarted.Eagerly]
 * starts the upstream read when this ViewModel is constructed, not when the first collector
 * subscribes, so the read is already in flight while Compose does its first layout pass.
 */
@HiltViewModel
internal class FirstRunGateViewModel @Inject constructor(
    settingsStore: ReminderSettingsStore,
    appLocaleController: AppLocaleController,
) : ViewModel() {
    /** `null` only while the first DataStore read is in flight. Retained across configuration
     *  change, so the blank hold happens at most once per process, not once per rotation.
     *
     *  app-localization (design.md D3) folds the language into this same gate rather than adding a
     *  second one. Below API 33 the language tag and [ReminderSettingsStore.onboardingDone] are
     *  both reads of the one `DataStore`, so combining them resolves at the same moment
     *  `onboardingDone` already did — the blank hold does not grow and there is no first frame in
     *  the wrong language. On API 33+ the platform applied the override before this process
     *  started, so the value carried here is inert and [ProvideAppLocale] passes straight through. */
    val startupState: StateFlow<StartupState?> =
        combine(
            settingsStore.onboardingDone,
            appLocaleController.observe(),
        ) { onboardingDone, language ->
            StartupState(onboardingDone = onboardingDone, language = language)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/** The two facts [FirstRunGate] must have before it can render anything at all. */
internal data class StartupState(
    val onboardingDone: Boolean,
    val language: AppLanguage,
)

/**
 * The tri-state wrapper [MainActivity.onCreate] renders instead of [ConstanzaApp] directly
 * (first-run-onboarding design.md §4.1). `null` renders NOTHING rather than a themed `Surface`:
 * with no composable emitted, the pixels on screen are still the ones
 * `android:windowBackground` already painted, so the blank hold is an extension of the cold-start
 * window, not a new visual state — see `res/values/colors.xml`'s note that `window_background`
 * must match [com.jjrapps.constanza.core.ui.theme.ConstanzaColors.Background] exactly.
 *
 * [resetToTodaySignal] (reminder-notification-tap-opens-today) passes straight through to
 * [ConstanzaApp] — see that composable's KDoc. It is meaningless during onboarding (no habit,
 * hence no reminder, can exist before onboarding finishes), so the [OnboardingRoute] branch below
 * simply never reads it.
 */
@Composable
private fun FirstRunGate(viewModel: FirstRunGateViewModel = hiltViewModel(), resetToTodaySignal: Int = 0) {
    val startupState by viewModel.startupState.collectAsState()
    // Write-once. Set synchronously inside onFinished, BEFORE the flag write is requested
    // (design.md §9). rememberSaveable, not remember: a rotation in the frame between onFinished
    // and the flag emission would otherwise reset the seed and drop the user on Today instead of
    // the editor.
    var startRoute by rememberSaveable { mutableStateOf<ConstanzaRoute>(ConstanzaRoute.Today) }
    when (val state = startupState) {
        // Still nothing, deliberately: the window background is already the right colour.
        null -> Unit
        // Everything below the gate renders in the resolved language, onboarding included — a user
        // whose device is in Spanish must not meet the first-run flow in English.
        else -> ProvideAppLocale(state.language) {
            if (state.onboardingDone) {
                ConstanzaApp(startRoute = startRoute, resetToTodaySignal = resetToTodaySignal)
            } else {
                OnboardingRoute(
                    onFinished = {
                        startRoute = ConstanzaRoute.HabitEditor(
                            habitId = null,
                            origin = ConstanzaRoute.EditorOrigin.Onboarding,
                        )
                    },
                )
            }
        }
    }
}
