package com.jjrapps.constanza.localization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * app-localization: [localizedContext]'s two cases. `createConfigurationContext` is stubbed out
 * under AGP's mockable-jar unit-test path (the same trap `NotificationPoster.kt` already
 * documents), so this is instrumented rather than a plain JVM unit test.
 */
@RunWith(AndroidJUnit4::class)
class AppLocaleInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aSpanishTagWrapsTheContextAndResolvesSpanishStrings() {
        val wrapped = localizedContext(context, "es")

        assertEquals("Sí", wrapped.getString(R.string.notification_action_yes))
    }

    @Test
    fun aNullTagReturnsTheExactSameContextInstance() {
        assertSame(context, localizedContext(context, null))
    }
}
