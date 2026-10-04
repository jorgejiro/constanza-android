package com.jjrapps.constanza.habit

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.core.ui.theme.ConstanzaTheme
import com.jjrapps.constanza.core.ui.theme.HabitColor
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.domain.model.Schedule
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Not an assertion — a camera, like `tracking.TodayRealRenderTest`: renders the real
 * [HabitListScreen] at 360dp/2.625 with the "E · Hábitos" board's habits, in Spanish, and logs the
 * PNG as numbered base64 chunks under [LOG_TAG] so a Gradle managed-device run leaves it in the
 * pulled logcat. It asserts nothing beyond "this composed without throwing".
 *
 * Spanish is provided by hand (a configuration context in [LocalContext] and its configuration in
 * [LocalConfiguration]) rather than through `ProvideAppLocale`, which only bites below API 33, so
 * the render is Spanish on any emulator; `LocalResources` derives from [LocalContext], so
 * `stringResource` and the subtitle copy both follow it.
 */
class HabitListRealRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renderHabitListAtRealSize() {
        val items = listOf(
            item(1, "Caminar", HabitColor.SAGE, Schedule.Daily(), 9 * 60 + 30),
            item(2, "Beber agua", HabitColor.BLUE, Schedule.TimesPerDay(), 9 * 60, 13 * 60, 18 * 60),
            item(
                3, "Estirar", HabitColor.SAND,
                Schedule.DaysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), 19 * 60,
            ),
            item(4, "Leer 20 minutos", HabitColor.CLAY, Schedule.Daily(), 8 * 60),
            item(5, "Meditar", HabitColor.LAVENDER, Schedule.EveryNDays(2, LocalDate.of(2025, 9, 1)), 7 * 60 + 30),
            item(6, "Nadar", HabitColor.TEAL, Schedule.NTimesPerWeek(3)),
        )
        val state = HabitListUiState(items = items)
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val spanish = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("es-ES")) }
        val spanishContext = base.createConfigurationContext(spanish)

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides spanishContext,
                LocalConfiguration provides spanishContext.resources.configuration,
                LocalDensity provides Density(RENDER_DENSITY),
            ) {
                ConstanzaTheme {
                    Box(Modifier.size(RENDER_WIDTH_DP.dp, RENDER_HEIGHT_DP.dp).testTag(RENDER_TAG)) {
                        HabitListScreen(
                            state = state,
                            actions = HabitListActions(
                                onBack = {},
                                onToggleShowArchived = {},
                                onArchiveToggle = { _, _ -> },
                                onCreateHabit = {},
                                onEditHabit = {},
                            ),
                        )
                    }
                }
            }
        }

        val bitmap = composeTestRule.onNodeWithTag(RENDER_TAG).captureToImage().asAndroidBitmap()
        val encoded = ByteArrayOutputStream().use { bytes ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, bytes)
            Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        }
        val chunks = encoded.chunked(LOG_CHUNK_SIZE)
        chunks.forEachIndexed { index, chunk -> Log.i(LOG_TAG, "${index + 1}/${chunks.size}:$chunk") }
    }

    private fun item(id: Long, name: String, color: HabitColor, schedule: Schedule, vararg minutes: Int) =
        HabitListItem(
            habit = Habit(
                id = id, name = name, colorArgb = color.argb, notes = null,
                archived = false, archivedAt = null, createdAt = Instant.parse("2025-09-01T08:00:00Z"),
            ),
            schedule = HabitSchedule(
                schedule,
                minutes.mapIndexed { index, minute -> ReminderSlot(id * 10 + index, id, minute, enabled = true) },
            ),
        )

    private companion object {
        const val RENDER_TAG = "habit-list-real-render"
        const val RENDER_DENSITY = 2.625f
        const val RENDER_WIDTH_DP = 360
        const val RENDER_HEIGHT_DP = 800
        const val LOG_TAG = "HabitListRealRender"
        const val LOG_CHUNK_SIZE = 3_000
        const val PNG_QUALITY = 100
    }
}
