package com.jjrapps.constanza.core.ui

import android.content.Context
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val NIGHT_MODE_TIMEOUT_MS = 10_000L
private const val NIGHT_MODE_POLL_INTERVAL_MS = 100L

/** `cmd uimode night <token>` — the light-mode token this test forces the device into. */
private const val NIGHT_MODE_LIGHT = "no"

/** Every token `cmd uimode night` will accept back, so a restore cannot write a value the
 *  framework rejects and silently leave the device on whatever this test set. */
private val ACCEPTED_NIGHT_MODES = setOf("yes", "no", "auto", "custom")

/**
 * Task 1.13 (`ui-design-system` spec "Cold-Start Window Background And System Bar Icons"). Closes
 * the automatable half of what that task recorded as a manual-only device check.
 *
 * **What this does NOT assert: the transient white cold-start flash itself.** A flash is a
 * frame-timing artifact — it exists in the handful of frames between the window being drawn and
 * Compose attaching, and no instrumented assertion can observe it without a frame capture harness
 * this project does not have. What is provable, and what design.md decision 9 identified as the
 * provable part, is the *mechanism* that prevents it: the pre-Compose window background resolving
 * to the same colour as the first Compose frame. If [windowBackgroundIsTheComposeBackground] is
 * green there is no colour for a flash to be, whatever the frame timing does. A future reader
 * should not read this class as coverage of the flash.
 *
 * ## Why the device is forced into light mode
 *
 * [systemBarIconsStayLightWhileTheDeviceIsInLightMode] is only meaningful against a device whose
 * system-wide setting disagrees with the app. That is the case that was conceptually failing: a
 * phone in light mode drawing dark bar icons onto this app's dark surface. On a night-mode device
 * the assertion would pass even if [MainActivity] used `SystemBarStyle.auto(...)`, which is exactly
 * the regression it exists to catch — so the light-mode setting is a precondition, asserted in
 * [setLightMode] rather than assumed.
 *
 * That switch is made **in-process**, through `UiAutomation.executeShellCommand`, and the device's
 * original setting is captured before the change and written back in [restoreNightMode]. There is
 * no out-of-band `adb` setup: this class passes on a device in any night-mode state and leaves the
 * setting as it found it.
 */
@RunWith(AndroidJUnit4::class)
class DarkChromeInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** The device's night-mode setting as found, restored verbatim in [restoreNightMode]. */
    private lateinit var originalNightMode: String

    @Before
    fun setLightMode() {
        originalNightMode = readNightMode()
        assertTrue(
            "`cmd uimode night` reported \"$originalNightMode\", which is not a token it accepts " +
                "back — refusing to change the device's night mode without a restorable original.",
            originalNightMode in ACCEPTED_NIGHT_MODES,
        )
        writeNightMode(NIGHT_MODE_LIGHT)
        awaitNightModeFlag(Configuration.UI_MODE_NIGHT_NO)
    }

    @After
    fun restoreNightMode() {
        writeNightMode(originalNightMode)
        // Awaited, not fired and forgotten: the configuration change is asynchronous, and a test
        // starting while it is still propagating would observe a night mode neither this class nor
        // that test chose.
        val restored = when (originalNightMode) {
            "yes" -> Configuration.UI_MODE_NIGHT_YES
            NIGHT_MODE_LIGHT -> Configuration.UI_MODE_NIGHT_NO
            else -> null
        }
        restored?.let { awaitNightModeFlag(it) }
    }

    /**
     * The mechanism behind the cold-start flash fix: `themes.xml`'s `Theme.Constanza` declares
     * `android:windowBackground` → `@color/window_background`, and that colour must be byte-equal
     * to [ConstanzaColors.Background], the colour the first Compose frame paints. The theme
     * attribute is resolved at runtime rather than the `colors.xml` literal being re-read, so the
     * whole chain is under assertion — a `themes.xml` that stopped pointing at the colour, or a
     * `colors.xml` that drifted from `ConstanzaColors`, both fail here.
     *
     * The colour-type check is deliberate: if `android:windowBackground` is ever changed to a
     * drawable, [TypedValue.data] stops being an ARGB int and a value comparison would be
     * meaningless. That fails loudly instead of comparing nonsense.
     */
    @Test
    fun windowBackgroundIsTheComposeBackground() {
        val theme = context.resources.newTheme()
        theme.applyStyle(R.style.Theme_Constanza, true)
        val resolved = TypedValue()
        assertTrue(
            "Theme.Constanza does not declare android:windowBackground at all.",
            theme.resolveAttribute(android.R.attr.windowBackground, resolved, true),
        )
        assertTrue(
            "android:windowBackground resolved to TypedValue type ${resolved.type}, not a colour. " +
                "A drawable cannot be compared against ConstanzaColors.Background as an ARGB int.",
            resolved.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT,
        )
        assertEquals(
            "Theme.Constanza's window background must equal ConstanzaColors.Background exactly, " +
                "or the pre-Compose frame is a different colour than the first Compose frame.",
            ConstanzaColors.Background.toArgb(),
            resolved.data,
        )
    }

    /**
     * [MainActivity] calls `enableEdgeToEdge` with `SystemBarStyle.dark(...)` for both bars, which
     * pins light bar icons regardless of the device's own light/dark setting. Read back off the
     * real window of a really-launched [MainActivity] — not a test harness `ComponentActivity`,
     * which is what design.md decision 9 said made this unassertable — while the device is in light
     * mode, so `SystemBarStyle.auto(...)` could not pass in its place.
     *
     * `isAppearanceLight…Bars == false` means light icons on a dark surface, which is this app's
     * fixed scheme.
     */
    @Test
    fun systemBarIconsStayLightWhileTheDeviceIsInLightMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Re-asserted against the ACTIVITY's own configuration, not just the application's:
                // this is what stops the two assertions below passing for the wrong reason. A false
                // reading on a night-mode device proves nothing, because that is also the default.
                assertEquals(
                    "MainActivity is not running in light mode, so a light-icon reading below " +
                        "would prove nothing — SystemBarStyle.auto(...) would pass it too.",
                    Configuration.UI_MODE_NIGHT_NO,
                    activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
                )
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertFalse(
                    "Status-bar icons must stay light: the app's surface is dark whatever the " +
                        "device's night-mode setting says.",
                    controller.isAppearanceLightStatusBars,
                )
                assertFalse(
                    "Navigation-bar icons must stay light, for the same reason as the status bar.",
                    controller.isAppearanceLightNavigationBars,
                )
            }
        }
    }

    private fun readNightMode(): String = shell("cmd uimode night").substringAfterLast(':').trim()

    private fun writeNightMode(mode: String) {
        shell("cmd uimode night $mode")
    }

    /**
     * A night-mode change reaches this process as an asynchronous configuration update, so the flag
     * is awaited rather than read once. Failing here rather than proceeding is the point: a test
     * that could not establish its own precondition must not go on to assert something that would
     * pass for the wrong reason.
     */
    private fun awaitNightModeFlag(expected: Int) {
        val deadline = System.currentTimeMillis() + NIGHT_MODE_TIMEOUT_MS
        while (nightModeFlag() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(NIGHT_MODE_POLL_INTERVAL_MS)
        }
        assertEquals(
            "The device's night-mode flag did not become $expected within ${NIGHT_MODE_TIMEOUT_MS}ms.",
            expected,
            nightModeFlag(),
        )
    }

    private fun nightModeFlag(): Int =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    /**
     * Runs through the instrumentation's own `UiAutomation`, so the night-mode switch is part of
     * the test rather than hand-prepared device state. `connectedDebugAndroidTest` uninstalls both
     * APKs when it finishes, so any setting a human set before a run is gone by the next one; a
     * test that only passes with out-of-band setup is a weaker guarantee than one that arranges its
     * own world.
     */
    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().decodeToString() }
    }
}
