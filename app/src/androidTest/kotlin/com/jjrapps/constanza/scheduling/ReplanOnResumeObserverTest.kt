package com.jjrapps.constanza.scheduling

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val REPLAN_TIMEOUT_SECONDS = 10L

/**
 * design.md §5.5/§13.1's `onResume()` re-check (task G.5). Driven through a REAL
 * [LifecycleRegistry] rather than by calling [ReplanOnResumeObserver.onResume] directly, so the
 * test proves the lifecycle dispatch itself — an observer that is never reached on `ON_RESUME`
 * would pass a direct-call test and fail here.
 *
 * The one thing this cannot assert is that `MainActivity` registers the observer: replacing an
 * injected dependency inside a Hilt-built Activity needs `hilt-android-testing` and a custom
 * runner, which this module does not have. That registration is a single line in
 * `MainActivity.onCreate`, and it is stated here rather than left as an unnoticed gap.
 */
@RunWith(AndroidJUnit4::class)
class ReplanOnResumeObserverTest {

    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun resumingTheHostReplansThroughTheSameIdempotentEntryPoint() {
        val replanned = CountDownLatch(1)
        val planner = mockk<OccurrencePlanner>()
        coEvery { planner.replanAll() } coAnswers { replanned.countDown() }
        val owner = FakeLifecycleOwner()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            owner.registry.addObserver(ReplanOnResumeObserver(planner))
            owner.registry.currentState = Lifecycle.State.RESUMED
        }

        assertTrue(
            "ON_RESUME must re-plan: after an exact-alarm revoke nothing else tells the app its " +
                "alarms are gone (design.md §13.4 finding 3)",
            replanned.await(REPLAN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
    }

    /** Reaching only `STARTED` must not re-plan — the trigger is `ON_RESUME`, and a host that is
     *  merely visible has not necessarily just come back from the exact-alarm settings screen. */
    @Test
    fun reachingOnlyStartedDoesNotReplan() {
        val replanned = CountDownLatch(1)
        val planner = mockk<OccurrencePlanner>()
        coEvery { planner.replanAll() } coAnswers { replanned.countDown() }
        val owner = FakeLifecycleOwner()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            owner.registry.addObserver(ReplanOnResumeObserver(planner))
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(1L, replanned.count)
    }
}
