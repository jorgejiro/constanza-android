package com.jjrapps.constanza.e2e

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.util.regex.Pattern

private const val DIALOG_TIMEOUT_MS = 15_000L
private const val DIALOG_POLL_INTERVAL_MS = 100L

/**
 * The grant button's resource id, in the order the platform has used it. Selecting by id rather
 * than by label is the whole point: the label is translated per locale and reworded per OEM, so a
 * text-first selector turns "this device speaks Spanish" into "the permission flow is broken".
 *
 * `permission_allow_button` is the plain grant. `permission_allow_foreground_only_button` is the
 * "While using the app" variant the controller shows for location-shaped permissions; it is
 * accepted here so this helper stays usable if a second runtime permission is ever added, not
 * because `POST_NOTIFICATIONS` uses it. `com.android.packageinstaller` is where the controller
 * lived before it was split out into its own package.
 */
private val ALLOW_BUTTON_IDS = listOf(
    "com.android.permissioncontroller:id/permission_allow_button",
    "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
    "com.android.packageinstaller:id/permission_allow_button",
)

/** The deny button's resource id, in the same "id first, translated text last" order as
 *  [ALLOW_BUTTON_IDS] and for the same reason (first-run-onboarding design.md §8.4, `a1...`). */
private val DENY_BUTTON_IDS = listOf(
    "com.android.permissioncontroller:id/permission_deny_button",
    "com.android.packageinstaller:id/permission_deny_button",
)

/**
 * The last-resort selector, used only when every id above is absent. It knows one language, and
 * that limit is deliberate rather than an oversight: a fallback that guesses at translations would
 * quietly click *something* on an unfamiliar dialog, which is worse than failing. On a device this
 * pattern cannot match, the failure below names the dialog it actually found.
 */
private val ALLOW_BUTTON_TEXT: Pattern = Pattern.compile("allow", Pattern.CASE_INSENSITIVE)

/** Same fallback, same one-language limit, for the deny path. */
private val DENY_BUTTON_TEXT: Pattern = Pattern.compile("deny|don't allow", Pattern.CASE_INSENSITIVE)

/**
 * Taps "Allow" on the REAL system permission dialog — the one owned by
 * `com.android.permissioncontroller`, in its own process, which is exactly why Compose's test rule
 * and Espresso cannot see it and UiAutomator is needed at all.
 *
 * Fails with a message naming the foreground package and the buttons it could actually see, rather
 * than blocking until the whole instrumentation run times out. A hang here reads as "the emulator
 * is slow"; a named failure reads as "the dialog never appeared, and here is what was on screen
 * instead", which is the difference between a five-minute diagnosis and an afternoon.
 */
fun UiDevice.tapAllowOnTheSystemPermissionDialog() {
    waitForIdle()
    val deadline = System.currentTimeMillis() + DIALOG_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        val button = ALLOW_BUTTON_IDS.firstNotNullOfOrNull { id -> findObject(By.res(id)) }
            ?: findObject(By.text(ALLOW_BUTTON_TEXT).clickable(true))
        if (button != null) {
            button.click()
            waitForIdle()
            return
        }
        Thread.sleep(DIALOG_POLL_INTERVAL_MS)
    }
    throw AssertionError(
        "No system permission-grant button appeared within ${DIALOG_TIMEOUT_MS}ms. " +
            "Foreground package was '$currentPackageName'. " +
            "Clickable elements on screen: ${visibleClickableDescriptions()}. " +
            "Looked for resource ids $ALLOW_BUTTON_IDS, then for clickable text matching " +
            "/${ALLOW_BUTTON_TEXT.pattern()}/i.",
    )
}

/**
 * The mirror image of [tapAllowOnTheSystemPermissionDialog], for `a1DenyingTheOnboardingPromptLeavesTodayOfferingNotificationSettings`
 * (first-run-onboarding design.md §2.2, §8.4) — the corrected `BLOCKED`-reachability scenario needs
 * a real recorded denial, not a seeded one, to prove the system's own dialog is reachable in the
 * first place.
 */
fun UiDevice.tapDenyOnTheSystemPermissionDialog() {
    waitForIdle()
    val deadline = System.currentTimeMillis() + DIALOG_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        val button = DENY_BUTTON_IDS.firstNotNullOfOrNull { id -> findObject(By.res(id)) }
            ?: findObject(By.text(DENY_BUTTON_TEXT).clickable(true))
        if (button != null) {
            button.click()
            waitForIdle()
            return
        }
        Thread.sleep(DIALOG_POLL_INTERVAL_MS)
    }
    throw AssertionError(
        "No system permission-deny button appeared within ${DIALOG_TIMEOUT_MS}ms. " +
            "Foreground package was '$currentPackageName'. " +
            "Clickable elements on screen: ${visibleClickableDescriptions()}. " +
            "Looked for resource ids $DENY_BUTTON_IDS, then for clickable text matching " +
            "/${DENY_BUTTON_TEXT.pattern()}/i.",
    )
}

/** What the failure above reports instead of a bare timeout: enough of the live hierarchy to tell
 *  "the dialog never opened" apart from "the dialog opened and its button is named something this
 *  helper does not know". */
private fun UiDevice.visibleClickableDescriptions(): List<String> =
    findObjects(By.clickable(true))
        .mapNotNull { it.text ?: it.contentDescription ?: it.resourceName }
        .filter { it.isNotBlank() }
