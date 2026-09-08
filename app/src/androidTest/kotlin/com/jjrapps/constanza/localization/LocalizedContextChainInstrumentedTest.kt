package com.jjrapps.constanza.localization

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The locale override MUST stay reachable back to the context it wrapped.
 *
 * **What this is protecting against, precisely.** `localizedContext` used to return
 * `base.createConfigurationContext(configuration)` — a fresh, detached `ContextImpl` with no
 * wrapper chain. [ProvideAppLocale] publishes its result as `LocalContext`, so everything
 * downstream that reaches the Activity by walking `ContextWrapper.baseContext` lost the ability to
 * do it. `hiltViewModel()` walks exactly that chain, so it threw
 * `IllegalStateException: Expected an activity context for creating a HiltViewModelFactory`, and
 * since every screen in this app resolves a view model that way, choosing any language other than
 * the system one crashed the app on launch.
 *
 * **Why it shipped.** [ProvideAppLocale] is a pass-through on API 33+, where the platform's own
 * `LocaleManager` has already applied the override before any composable runs. The only devices
 * that execute this code are API 31-32 — supported (`minSdk` is 31) but not the ones the app is
 * developed on. The existing coverage exercised `ProvideAppLocale` over a small composable subtree
 * that resolves no view model, so it passed throughout.
 *
 * The honest limit of this test: it pins the *property* whose absence caused the crash, not the
 * crash itself. Reproducing that needs a composable calling `hiltViewModel()` under a real
 * Activity, and this project has no `hilt-android-testing` wiring to build one. The fix was
 * verified by hand as well — installed on an API 31 emulator, language switched to Spanish,
 * zero `FATAL EXCEPTION` in logcat and the UI in Spanish — which is what caught it in the first
 * place.
 */
@RunWith(AndroidJUnit4::class)
class LocalizedContextChainInstrumentedTest {

    private val base: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun anOverriddenContextStillUnwrapsToTheContextItWrapped() {
        val wrapped = localizedContext(base, "es")

        assertNotSame("the override must be a distinct context, not the base returned as-is", base, wrapped)
        assertTrue(
            "localizedContext returned a ${wrapped.javaClass.name}, which is not a ContextWrapper. " +
                "Anything reaching the Activity through baseContext — hiltViewModel() above all — " +
                "cannot get there from a detached context.",
            wrapped is ContextWrapper,
        )

        var cursor: Context = wrapped
        while (cursor is ContextWrapper && cursor.baseContext !== base) {
            cursor = cursor.baseContext
        }
        assertSame(
            "walking baseContext from the overridden context never reached the context it wrapped",
            base,
            (cursor as ContextWrapper).baseContext,
        )
    }

    @Test
    fun theOverriddenContextActuallyCarriesTheRequestedLocale() {
        val wrapped = localizedContext(base, "es")

        assertEquals("es", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun aNullTagIsThePassThroughItClaimsToBe() {
        assertSame(base, localizedContext(base, null))
    }
}
