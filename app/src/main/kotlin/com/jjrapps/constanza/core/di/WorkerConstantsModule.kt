package com.jjrapps.constanza.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** design.md D3, task 4b.1: `ReconcileWorker`'s hourly period, which also doubles as the
 *  grace-expiry window for an abandoned snooze ("grace equals one reconcile period"). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReconcilePeriodHours

/** design.md D3, task 4a.7/4b.3: `resolveDeadline = scheduledAt + resolveDeadlineHours`. Lifted
 *  from a private literal in `OccurrencePlanner` (unit 4a) so it and `OccurrenceResolver` share one
 *  injected source of truth instead of two constants that could drift apart. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ResolveDeadlineHours

/** Task 4b.1/4b.3: named, not a literal buried in either worker. */
private const val RECONCILE_PERIOD_HOURS = 1L

/** Task 4b.3: named, not a literal buried in either call site. */
private const val RESOLVE_DEADLINE_HOURS = 24L

/** Provides work unit 4b's two tunable constants into the Hilt graph (design.md D5). */
@Module
@InstallIn(SingletonComponent::class)
object WorkerConstantsModule {
    @Provides
    @ReconcilePeriodHours
    fun provideReconcilePeriodHours(): Long = RECONCILE_PERIOD_HOURS

    @Provides
    @ResolveDeadlineHours
    fun provideResolveDeadlineHours(): Long = RESOLVE_DEADLINE_HOURS
}
