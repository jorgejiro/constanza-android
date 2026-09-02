package com.jjrapps.constanza.core.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.test.core.app.ApplicationProvider

/**
 * Whether the device under test writes times as `21:05` or as `9:05 PM`.
 *
 * Every screen that renders a time now follows this setting ([TimeOfDayFormat]), so an instrumented
 * test that hardcodes one notation asserts something that is only true on half the devices it might
 * run on — and the emulators in `:app:emulatorMatrixGroupDebugAndroidTest` default to `en-US`, which
 * is the 12-hour half.
 */
fun deviceUses24HourTime(): Boolean =
    DateFormat.is24HourFormat(ApplicationProvider.getApplicationContext<Context>())

/**
 * The one of two **literal** expected strings that applies on this device.
 *
 * Deliberately not `TimeOfDayFormat(...).format(...)`: asking production code what it should print
 * and then asserting it printed that proves only that the screen called it. Both notations are
 * written out by hand here, so whichever branch runs still compares the screen against a string a
 * person decided on.
 *
 * The 12-hour literals assume the English `AM`/`PM` markers, which is what both matrix legs run;
 * a device in another language would fail this rather than silently weaken it, which is the right
 * way round for an assertion.
 */
fun expectedTimeOnDevice(inTwentyFourHour: String, inTwelveHour: String): String =
    if (deviceUses24HourTime()) inTwentyFourHour else inTwelveHour

/** The notation the device is **not** using — for asserting a screen did not ignore the setting. */
fun unexpectedTimeOnDevice(inTwentyFourHour: String, inTwelveHour: String): String =
    if (deviceUses24HourTime()) inTwelveHour else inTwentyFourHour
