package com.jjrapps.constanza

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jjrapps.constanza.scheduling.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Hilt's DI entry point (design.md D5). Implements [Configuration.Provider] so `WorkManager`
 *  self-initialises with [HiltWorkerFactory] instead of its no-arg default, which could never
 *  construct the two framework-instantiated workers' injected dependencies (design.md D5). */
@HiltAndroidApp
class ConstanzaApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var workScheduler: WorkScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        workScheduler.scheduleAll()
    }
}
