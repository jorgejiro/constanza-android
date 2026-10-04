package com.jjrapps.constanza.tracking

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.core.ui.theme.ConstanzaTheme
import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.localization.AppLanguage
import com.jjrapps.constanza.localization.ProvideAppLocale
import com.jjrapps.constanza.domain.model.DayStatus
import com.jjrapps.constanza.domain.model.EntryStatus
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Not an assertion — a camera. Renders the real [TodayScreen] to a PNG at exactly 360dp/2.625 so a
 * human can look at the shipped composable rather than at a mock-up of it.
 *
 * It is a test only because that is the cheapest way to get a Compose tree onto a real Android
 * canvas. It asserts nothing beyond "this composed without throwing", and it is excluded from the
 * meaning of a green suite: deleting it would cost the project no coverage at all.
 *
 * Density is pinned INSIDE the composition rather than matched on the device, so the output is
 * identical whatever emulator runs it. Capture targets the tagged fixed-size [Box] and never
 * `onRoot()`, which would grab the whole test-activity window at the emulator's own size.
 *
 * The PNG lands in app-private `filesDir` (reachable through `adb shell am instrument`) and is also
 * logged as base64 chunks under the `TodayRealRender` tag, so a Gradle managed-device run — which
 * uninstalls the app, `filesDir` included — still leaves it in the test's pulled logcat.
 */
class TodayRealRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renderTodayScreenAtRealSize() {
        val zone = ZoneId.of("Europe/Madrid")
        // The approved graphite board's own day ("Domingo, 28 de septiembre"), so the render can be
        // laid beside it: one habit due now, two later (one of them a three-reminder habit with its
        // first reminder answered), and two answered — one done, one missed.
        val today = LocalDate.of(2025, 9, 28)
        val now = today.atTime(10, 0).atZone(zone).toInstant()

        val rows = listOf(
            row(1, "Caminar", HabitColor.SAGE, DayStatus.PENDING, slot(1, 9 * 60 + 30, EntryStatus.UNKNOWN)),
            row(
                2, "Beber agua", HabitColor.BLUE, DayStatus.PARTIAL,
                slot(2, 9 * 60, EntryStatus.COMPLETED), slot(3, 13 * 60, EntryStatus.UNKNOWN), slot(4, 18 * 60, EntryStatus.UNKNOWN),
            ),
            row(3, "Estirar", HabitColor.SAND, DayStatus.PENDING, slot(5, 19 * 60, EntryStatus.UNKNOWN)),
            row(4, "Leer 20 minutos", HabitColor.TAN, DayStatus.ALL_COMPLETED, slot(6, 8 * 60, EntryStatus.COMPLETED)),
            row(5, "Meditar", HabitColor.LAVENDER, DayStatus.ANY_MISSED, slot(7, 8 * 60 + 30, EntryStatus.MISSED)),
        )

        val state = TodayUiState(
            rows = rows,
            sections = groupTodayRows(rows, today, zone, now),
            zone = zone,
            date = today,
        )

        composeTestRule.setContent {
            // Spanish, because that is the app the owner actually looks at. `ProvideAppLocale` is
            // the app's own override and it only bites below API 33 (its KDoc, Finding A/D1), which
            // is exactly why this render runs on the API 31 emulator.
            ProvideAppLocale(AppLanguage.Spanish) {
                CompositionLocalProvider(LocalDensity provides Density(RENDER_DENSITY)) {
                    ConstanzaTheme {
                        Box(
                            Modifier
                                .size(RENDER_WIDTH_DP.dp, RENDER_HEIGHT_DP.dp)
                                .testTag(RENDER_TAG),
                        ) {
                            TodayScreen(
                                state = state,
                                onToggleExpanded = {},
                                onAnswer = { _, _, _ -> },
                                onManageHabits = {},
                            )
                        }
                    }
                }
            }
        }

        val bitmap = composeTestRule.onNodeWithTag(RENDER_TAG).captureToImage().asAndroidBitmap()
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        File(target.filesDir, RENDER_FILE_NAME).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
        }
        // Gradle managed devices uninstall the app (and `filesDir` with it) when the run ends, but
        // they do keep each test's logcat under `build/outputs/androidTest-results/`. The PNG is
        // logged there as numbered base64 chunks, which a script joins back into the file.
        val encoded = ByteArrayOutputStream().use { bytes ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, bytes)
            Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        }
        val chunks = encoded.chunked(LOG_CHUNK_SIZE)
        chunks.forEachIndexed { index, chunk -> Log.i(LOG_TAG, "${index + 1}/${chunks.size}:$chunk") }
    }

    private fun row(
        id: Long,
        name: String,
        color: HabitColor,
        dayStatus: DayStatus,
        vararg slots: TodaySlot,
    ) = TodayHabitRow(id, name, dayStatus, color.argb, slots.toList())

    private fun slot(id: Long, minuteOfDay: Int, status: EntryStatus) =
        TodaySlot(
            slotId = id,
            minuteOfDay = minuteOfDay,
            status = status,
            occurrenceId = null,
            snoozedUntilEpochMs = null,
        )

    private companion object {
        const val RENDER_TAG = "today-real-render"
        const val RENDER_FILE_NAME = "today-real.png"
        const val RENDER_DENSITY = 2.625f

        /** The repo's stated phone width, so the output is measurable against every other render. */
        const val RENDER_WIDTH_DP = 360

        /** A real phone's height, so the FAB lands where it does on a device. */
        const val RENDER_HEIGHT_DP = 800
        const val LOG_TAG = "TodayRealRender"
        const val LOG_CHUNK_SIZE = 3_000
        const val PNG_QUALITY = 100
    }
}
