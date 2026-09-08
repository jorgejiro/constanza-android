package com.jjrapps.constanza

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (!WorkManager.isInitialized()) WorkManager.initialize(this, workManagerConfiguration)
        workScheduler.scheduleAll()
        // day-review, slice B (day-review-notification): the third job's startup enrolment, kept as
        // its own line rather than folded into scheduleAll() itself. WorkScheduler.scheduleDayReview
        // is suspend — its anchor depends on a DataStore read (DayReviewSettingsStore) that
        // scheduleAll()'s other two jobs never needed — and onCreate() is not a suspend context, so
        // this mirrors RescheduleReceivers.replanAsync's existing fire-and-forget-coroutine idiom for
        // the same reason: a suspend reschedule triggered from a non-suspend Android callback.
        // ExistingWorkPolicy defaults to KEEP, exactly like scheduleAll()'s own midnight-sweep
        // enqueue, so a cold start never re-anchors an already-pending review.
        //
        // No dispatcher is named, for the identical reason ReplanOnResumeObserver.onResume names
        // none (see its KDoc): the DataStore read inside scheduleDayReview and the WorkManager write
        // it makes both already manage their own executors, so naming Dispatchers.IO here would
        // hardcode a dispatcher that changes nothing — which is also what detekt's InjectDispatcher
        // rule objects to. Application has no lifecycleScope/viewModelScope of its own to reuse
        // (unlike that observer), so a bare CoroutineScope(Job()) stands in for one.
        CoroutineScope(Job()).launch { workScheduler.scheduleDayReview() }
    }
}
