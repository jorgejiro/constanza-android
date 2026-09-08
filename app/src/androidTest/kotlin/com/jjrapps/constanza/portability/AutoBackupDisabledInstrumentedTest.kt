package com.jjrapps.constanza.portability

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Auto Backup MUST stay off, asserted against the merged manifest as the platform actually parsed
 * it rather than against the source XML — a manifest attribute can be re-added by a merged library
 * manifest without anyone editing `app/src/main/AndroidManifest.xml`, and reading the source file
 * would not notice.
 *
 * **Why this is worth a test at all.** `android:allowBackup` defaults to `true`, so this is a
 * setting that comes back by itself if the attribute is ever dropped: deleting the line does not
 * read as a change of behaviour, it reads as tidying. The behaviour it restores is not subtle —
 * every habit reappears from the owner's Google Drive after a deliberate uninstall, which is the
 * report that prompted this — but it is invisible until someone reinstalls, and by then the data
 * has already been off the device for a while.
 *
 * Preserving data across a reinstall is `DataPortabilityScreen`'s export/import, an explicit file
 * the user chooses and keeps. This test guards the "on its own" half of that promise.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupDisabledInstrumentedTest {

    @Test
    fun theAppDoesNotAllowAutoBackup() {
        val info = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo
        assertEquals(
            "FLAG_ALLOW_BACKUP is set on the merged manifest. Android Auto Backup will copy the " +
                "habit database to the user's Google Drive and restore it after an uninstall. " +
                "Data leaves this device only through DataPortabilityScreen's export.",
            0,
            info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
    }
}
