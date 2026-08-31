package com.jjrapps.constanza.core.di

import android.app.AlarmManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Provides the single [AlarmManager] instance work unit 4a's `AlarmScheduler` schedules against
 *  (design.md D5). */
@Module
@InstallIn(SingletonComponent::class)
object AlarmModule {
    @Provides
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)
}
