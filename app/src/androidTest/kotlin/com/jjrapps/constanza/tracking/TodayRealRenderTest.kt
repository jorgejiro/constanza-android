package com.jjrapps.constanza.tracking

import android.graphics.Bitmap
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
 * The PNG lands in app-private `filesDir`. Retrieving it needs `adb shell am instrument` rather
 * than Gradle's `connectedDebugAndroidTest`, because that task uninstalls the app when it finishes
 * and takes the file with it.
 */
class TodayRealRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renderTodayScreenAtRealSize() {
        val zone = ZoneId.of("Europe/Madrid")
        val today = LocalDate.of(2026, 9, 7)
        val now = today.atTime(14, 42).atZone(zone).toInstant()

        val rows = listOf(
            row(1, "Hacer movilidad al sol en la primera hora tras levantarme", HabitColor.PINK, DayStatus.ALL_COMPLETED, slot(1, 8 * 60, EntryStatus.COMPLETED)),
            row(2, "Hacer descansos con movilidad", HabitColor.GREEN, DayStatus.ALL_COMPLETED, slot(2, 10 * 60, EntryStatus.COMPLETED)),
            row(3, "Empezar a preparar la comida antes de las 14h", HabitColor.PEACH, DayStatus.ANY_MISSED, slot(3, 13 * 60 + 20, EntryStatus.MISSED)),
            row(4, "Comer sanito, lento y pronto todos los días", HabitColor.MINT, DayStatus.ANY_MISSED, slot(4, 14 * 60 + 52, EntryStatus.MISSED)),
            row(5, "Estirar la espalda", HabitColor.TEAL, DayStatus.ALL_SKIPPED, slot(5, 12 * 60, EntryStatus.SKIPPED)),
            // Multi-slot: the one case where the scheduled time still earns its place, because it
            // is the only thing telling these two rows apart.
            row(6, "Beber agua", HabitColor.CYAN, DayStatus.PARTIAL, slot(6, 11 * 60, EntryStatus.COMPLETED), slot(7, 16 * 60, EntryStatus.MISSED)),
            row(7, "Cenar antes de las 10 de la noche", HabitColor.LILAC, DayStatus.PENDING, slot(8, 21 * 60, EntryStatus.UNKNOWN)),
            row(8, "Relax a las 23h", HabitColor.BLUE, DayStatus.PENDING, slot(9, 23 * 60, EntryStatus.UNKNOWN)),
        )

        val state = TodayUiState(
            rows = rows,
            sections = groupTodayRows(rows, today, zone, now),
            expandedHabitIds = setOf(6L),
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

        /** Taller than a real screen on purpose: this is a contact sheet, and a scrolled-off row is
         *  a row nobody reviews. */
        const val RENDER_HEIGHT_DP = 1000
        const val PNG_QUALITY = 100
    }
}
