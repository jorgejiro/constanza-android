package com.jjrapps.constanza.core.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jjrapps.constanza.core.ui.theme.ConstanzaTheme
import com.jjrapps.constanza.habit.HabitEditorRoute
import com.jjrapps.constanza.habit.HabitListRoute
import com.jjrapps.constanza.progress.ProgressRoute
import com.jjrapps.constanza.reminding.SnoozeSettingsRoute
import com.jjrapps.constanza.scheduling.ReplanOnResumeObserver
import com.jjrapps.constanza.tracking.TodayRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
                ConstanzaApp()
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
    data class HabitEditor(val habitId: Long?) : ConstanzaRoute
    data class Progress(val habitId: Long) : ConstanzaRoute
    data object Settings : ConstanzaRoute
}

/** Task 6b.1: Today is the daily-use home screen; [ConstanzaRoute.HabitList] is reached from its
 *  "Manage habits" action and returns here rather than staying its own top-level destination.
 *  Task 6b.4/6b.5 add two more leaf screens the same way: [ConstanzaRoute.Progress] from
 *  [com.jjrapps.constanza.habit.HabitListRoute]'s per-habit "Progress" action, and
 *  [ConstanzaRoute.Settings] from Today's own "Settings" action. */
@Composable
private fun ConstanzaApp() {
    var route by rememberSaveable { mutableStateOf<ConstanzaRoute>(ConstanzaRoute.Today) }
    when (val current = route) {
        is ConstanzaRoute.Today -> TodayRoute(
            onManageHabits = { route = ConstanzaRoute.HabitList },
            onOpenSettings = { route = ConstanzaRoute.Settings },
        )

        is ConstanzaRoute.HabitList -> HabitListRoute(
            onCreateHabit = { route = ConstanzaRoute.HabitEditor(habitId = null) },
            onEditHabit = { habitId -> route = ConstanzaRoute.HabitEditor(habitId) },
            onShowProgress = { habitId -> route = ConstanzaRoute.Progress(habitId) },
        )

        is ConstanzaRoute.HabitEditor -> HabitEditorRoute(
            habitId = current.habitId,
            onDone = { route = ConstanzaRoute.HabitList },
        )

        is ConstanzaRoute.Progress -> ProgressRoute(
            habitId = current.habitId,
            onBack = { route = ConstanzaRoute.HabitList },
        )

        is ConstanzaRoute.Settings -> SnoozeSettingsRoute(
            onBack = { route = ConstanzaRoute.Today },
        )
    }
}
