package com.jjrapps.constanza.localization

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.di.ReminderSettingsDataStoreEntryPoint
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Carried-forward item `device-locale-resolution-has-no-runtime-test`. Covers the requirements
 * `Device-Locale Resolution`, `Supported Language Set And Universal Fallback` and
 * `First-Install Resolution Needs No User Action` — the three that describe what happens *before*
 * a user has touched the picker, and the only ones that had no runtime coverage at all.
 *
 * **This is deliberately not another test of the override path.** `AppLocaleInstrumentedTest` and
 * `LanguageOverrideStoreInstrumentedTest` both start by *storing* something; every scenario here
 * starts by making sure nothing is stored, and then asks two separate questions:
 *
 *  1. With no override, does the app wrap anything? It must not —
 *     [AppLocaleController.localizedApplicationContext] has to hand back the very same
 *     [Context] instance, which is what leaves resolution to the platform on a first install.
 *  2. Given a *device* [Configuration], does Android pick the right resource folder? `es-ES`
 *     must select `values-es/`, and an unsupported locale must fall through to `values/`.
 *
 * **What this stands in for, and what it does not.** AGP's `ManagedVirtualDevice` exposes no locale
 * property, so neither leg of the `emulatorMatrix` can be booted in Spanish; the configuration here
 * is copied from [Resources.getSystem] — the device-wide configuration, not this app's — and has
 * only its locale list replaced, which is the closest a test running on an English emulator can get
 * to a Spanish-locale device. It therefore proves Android's qualifier resolution and the absence of
 * wrapping. It does NOT prove the behaviour of a device actually booted in Spanish, where the
 * locale arrives through the system configuration at process start rather than through
 * `createConfigurationContext`. That last slice stays uncovered and is honest about it.
 *
 * `notification_action_yes` is the probe string for the same reason `AppLocaleInstrumentedTest`
 * uses it: it is short, present in both `values/` and `values-es/`, and its two spellings differ.
 */
@RunWith(AndroidJUnit4::class)
class DeviceLocaleResolutionInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStore =
        EntryPointAccessors.fromApplication(context, ReminderSettingsDataStoreEntryPoint::class.java)
            .reminderSettingsDataStore()
    private val settingsStore = ReminderSettingsStore(dataStore)
    private val controller = AppLocaleController(context, settingsStore)

    /** A leaked override from an earlier class in the same run would invalidate every scenario
     *  here, all of which are about the no-override state. Cleared on both sides, as every other
     *  test in this package does. */
    @Before
    fun clearAnyPriorOverride() = runBlocking {
        controller.set(AppLanguage.SystemDefault)
    }

    @After
    fun tearDown() = runBlocking {
        controller.set(AppLanguage.SystemDefault)
    }

    /** The precondition the other scenarios rest on, asserted rather than assumed: this is the
     *  state of a first install. */
    @Test
    fun withNoOverrideStoredNeitherSurfaceHoldsALanguage() = runBlocking {
        assertEquals(AppLanguage.SystemDefault, controller.current())
        assertNull(
            "a first install must hold no persisted language tag",
            settingsStore.currentLanguageTag(),
        )
    }

    /**
     * `First-Install Resolution Needs No User Action`: with nothing stored there is nothing to
     * override, so the poster's context must be the application context *itself*. [assertSame]
     * rather than an equality check — a wrapper that happened to resolve the same strings would
     * still mean the app had inserted itself into a decision the platform owns.
     */
    @Test
    fun withNoOverrideStoredTheApplicationContextIsNotWrappedAtAll() = runBlocking {
        assertSame(context, controller.localizedApplicationContext())
    }

    /**
     * `Device-Locale Resolution`. A device configuration whose locale list is `es-ES` must select
     * `values-es/`, with no override anywhere in the picture.
     */
    @Test
    fun aSpanishDeviceConfigurationResolvesTheSpanishResourceFolder() {
        val spanishDevice = contextForDeviceLocale(SPAIN)

        assertEquals("Sí", spanishDevice.getString(R.string.notification_action_yes))
    }

    /**
     * `Supported Language Set And Universal Fallback`. French is not a supported language and has
     * no `values-fr/`, so it must fall through to the base `values/` folder — the same folder an
     * English device resolves, asserted both ways: equal to the English rendering, and different
     * from the Spanish one so a test that resolved nothing at all could not pass.
     */
    @Test
    fun anUnsupportedDeviceLocaleFallsBackToTheBaseResourceFolder() {
        val frenchDevice = contextForDeviceLocale(FRANCE)
        val englishDevice = contextForDeviceLocale(Locale.US)

        val french = frenchDevice.getString(R.string.notification_action_yes)
        assertEquals("Yes", french)
        assertEquals(
            "an unsupported locale must resolve exactly what the base values/ folder holds",
            englishDevice.getString(R.string.notification_action_yes),
            french,
        )
        assertNotEquals(
            "French must not resolve values-es/",
            contextForDeviceLocale(SPAIN).getString(R.string.notification_action_yes),
            french,
        )
    }

    /**
     * A [Context] over the *device* configuration with only its locale list replaced. Copied from
     * [Resources.getSystem] rather than from this app's own resources on purpose: the system
     * configuration is the one a device's language setting actually feeds, and copying the app's
     * would silently carry over anything the test process had already applied to itself.
     */
    private fun contextForDeviceLocale(locale: Locale): Context {
        val deviceConfiguration = Configuration(Resources.getSystem().configuration)
        deviceConfiguration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(deviceConfiguration)
    }

    private companion object {
        val SPAIN: Locale = Locale.forLanguageTag("es-ES")
        val FRANCE: Locale = Locale.forLanguageTag("fr-FR")
    }
}
