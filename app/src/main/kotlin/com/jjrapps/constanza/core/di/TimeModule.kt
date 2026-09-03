package com.jjrapps.constanza.core.di

import com.jjrapps.constanza.core.time.CurrentDateSource
import com.jjrapps.constanza.core.time.SelfReschedulingCurrentDateSource
import com.jjrapps.constanza.core.time.SystemTimeProvider
import com.jjrapps.constanza.core.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the single ambient-clock access point (design.md §4) into the Hilt graph. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    /** today-midnight-rollover, design.md decision 1. */
    @Binds
    @Singleton
    abstract fun bindCurrentDateSource(impl: SelfReschedulingCurrentDateSource): CurrentDateSource
}
