package com.jjrapps.constanza.e2e

import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice

/** The "Wait" button of the system's "isn't responding" dialog. */
private const val ANR_WAIT_BUTTON_ID = "android:id/aerr_wait"

/**
 * Answers "Wait" on every OTHER app's "isn't responding" dialog currently on screen.
 *
 * The api37 leg runs on the full `google_apis` image (no ATD image exists at that level — see
 * `app/build.gradle.kts`), and right after boot its system apps routinely miss ANR deadlines:
 * captured runs logged `am_anr` for System UI, Play services, the launcher, the phone process and
 * Gboard, never this app. Their dialog takes the foreground, so the permission dialog's buttons
 * cannot be found and `pressBack()` dismisses the dialog instead of navigating. Compose actions go
 * straight to this app's window and are unaffected; only `UiDevice` interactions need this, so it
 * runs right before each of them (and inside the permission dialog's polling loop, since one can
 * pop up while it waits).
 *
 * The platform's `hide_error_dialogs` setting was tried first: set in `@Before`, the dialog still
 * covered the permission prompt in a captured api37 run, so the dialog is answered instead.
 */
fun UiDevice.dismissSystemAnrDialogs() {
    while (true) {
        val wait = findObject(By.res(ANR_WAIT_BUTTON_ID)) ?: return
        try {
            wait.click()
        } catch (_: StaleObjectException) {
            // The dialog closed on its own between the lookup and the tap — the app it was about
            // recovered. Nothing to answer; look again in case another one is queued behind it.
        }
        waitForIdle()
    }
}
