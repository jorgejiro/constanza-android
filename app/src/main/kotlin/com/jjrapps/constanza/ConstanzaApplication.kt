package com.jjrapps.constanza

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.jjrapps.constanza.scheduling.DayReviewAlarmScheduler
import com.jjrapps.constanza.scheduling.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Hilt's DI entry point (design.md D5). Implements [Configuration.Provider] so every `WorkManager`
 * worker resolves through [HiltWorkerFactory]. `WorkManager.initialize` is called explicitly here
 * (manifest removes the default `androidx.startup` auto-initializer) — task 5.9 discovery: that
 * auto-initializer ran before Hilt injected [workerFactory], so every worker silently fell back to
 * a bare reflective constructor, which no `@AssistedInject`-only worker in this app has.
 */
@HiltAndroidApp
class ConstanzaApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var workScheduler: WorkScheduler

    @Inject lateinit var dayReviewAlarmScheduler: DayReviewAlarmScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (!WorkManager.isInitialized()) WorkManager.initialize(this, workManagerConfiguration)
        workScheduler.scheduleAll()
        // day-review-exact-alarm: the review's own startup arm, kept as its own line rather than
        // folded into scheduleAll() itself — scheduleAll() is `WorkManager`-only, while
        // DayReviewAlarmScheduler.scheduleNext arms an `AlarmManager` alarm instead, and is suspend
        // besides (its anchor depends on a DataStore read through DayReviewSettingsStore, which
        // scheduleAll()'s two `WorkManager` jobs never needed). onCreate() is not a suspend context,
        // so this mirrors RescheduleReceivers.replanAsync's existing fire-and-forget-coroutine idiom
        // for the same reason: a suspend reschedule triggered from a non-suspend Android callback.
        //
        // No dispatcher is named, for the identical reason ReplanOnResumeObserver.onResume names
        // none (see its KDoc): the DataStore read inside scheduleNext and the AlarmManager call it
        // makes both already manage their own executors, so naming Dispatchers.IO here would
        // hardcode a dispatcher that changes nothing — which is also what detekt's InjectDispatcher
        // rule objects to. Application has no lifecycleScope/viewModelScope of its own to reuse
        // (unlike that observer), so a bare CoroutineScope(Job()) stands in for one.
        CoroutineScope(Job()).launch { dayReviewAlarmScheduler.scheduleNext() }
    }
}
