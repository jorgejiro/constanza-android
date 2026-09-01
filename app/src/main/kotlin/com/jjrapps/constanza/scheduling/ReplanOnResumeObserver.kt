package com.jjrapps.constanza.scheduling

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * design.md §5.5/§13.1, task G.5: the `onResume()` re-check both of those sections have promised
 * since work unit 4a — "`canScheduleExactAlarms()` before **every** scheduling call, plus the
 * state-changed receiver, plus an `onResume()` re-check" — which no numbered task ever owned and
 * which was therefore never written.
 *
 * A lifecycle observer rather than an override in [com.jjrapps.constanza.core.ui.MainActivity] so
 * the Activity holds no scheduling logic of its own, and so this trigger reads like the five
 * broadcast triggers in `RescheduleReceivers.kt`: it converges on the same idempotent entry point,
 * [OccurrencePlanner.replanAll] (design.md §9.3). A re-plan, not a permission check — the design's
 * "re-check" is satisfied for free, because [AlarmScheduler.schedule] re-checks
 * `canScheduleExactAlarms()` on every call and so upgrades or degrades every armed occurrence as a
 * side effect of re-planning it. No separate branch is written here, exactly as
 * [ExactAlarmPermissionReceiver] writes none.
 *
 * Why an app-open trigger is needed at all (design.md §13.4 finding 3): revoking
 * `SCHEDULE_EXACT_ALARM` makes the platform cancel every alarm this app owns and stop its process,
 * and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` fires on **grant only**, so no
 * broadcast announces the revoke. [OccurrenceResolver.reconcile] now recovers from that on its own
 * (also task G.5), which is the correctness net; this makes the recovery immediate when the user
 * opens the app instead of up to one reconcile period later.
 *
 * `ON_RESUME`, not `ON_START` or `ON_CREATE`: returning from the system's exact-alarm settings
 * screen resumes the Activity without recreating it, and that return is the single most likely
 * moment for the permission to have just changed.
 */
class ReplanOnResumeObserver @Inject constructor(
    private val occurrencePlanner: OccurrencePlanner,
) : DefaultLifecycleObserver {
    /**
     * [androidx.lifecycle.LifecycleOwner.lifecycleScope] rather than the receivers' bare
     * `CoroutineScope`: an Activity has a scope of its own and the re-plan should die with it,
     * whereas a broadcast receiver has none and has to build one.
     *
     * No dispatcher is passed. Every database call [OccurrencePlanner.replanAll] makes is a
     * `suspend` Room DAO function, and Room already moves those onto its own query executor, so
     * naming `Dispatchers.IO` here would hardcode a dispatcher that changes nothing — which is also
     * what detekt's `InjectDispatcher` rule objects to.
     */
    override fun onResume(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            occurrencePlanner.replanAll()
        }
    }
}
