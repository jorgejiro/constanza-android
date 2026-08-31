package com.jjrapps.constanza

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.jjrapps.constanza.scheduling.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

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
    }
}
