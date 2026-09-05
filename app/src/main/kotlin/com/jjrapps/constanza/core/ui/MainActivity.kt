package com.jjrapps.constanza.core.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var replanOnResumeObserver: ReplanOnResumeObserver

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
                FirstRunGate()
            }
        }
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
 *  a normal route entry rather than a second navigation mechanism. */
@Composable
private fun ConstanzaApp(startRoute: ConstanzaRoute = ConstanzaRoute.Today) {
    var route by rememberSaveable { mutableStateOf(startRoute) }
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
 */
@Composable
private fun FirstRunGate(viewModel: FirstRunGateViewModel = hiltViewModel()) {
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
                ConstanzaApp(startRoute = startRoute)
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
