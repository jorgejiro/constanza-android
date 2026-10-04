package com.jjrapps.constanza.core.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import com.jjrapps.constanza.domain.model.ReminderSlot
import com.jjrapps.constanza.habit.HabitEditorActions
import com.jjrapps.constanza.habit.HabitEditorScreen
import com.jjrapps.constanza.habit.HabitEditorUiState
import com.jjrapps.constanza.localization.AppLanguage
import com.jjrapps.constanza.localization.LanguageSectionContent
import com.jjrapps.constanza.onboarding.OnboardingPage
import com.jjrapps.constanza.onboarding.OnboardingPermissionsPage
import com.jjrapps.constanza.onboarding.OnboardingScaffold
import com.jjrapps.constanza.onboarding.OnboardingUiState
import com.jjrapps.constanza.portability.DataPortabilitySectionContent
import com.jjrapps.constanza.portability.ImportResult
import com.jjrapps.constanza.progress.ProgressScreen
import com.jjrapps.constanza.progress.ProgressUiState
import com.jjrapps.constanza.reminding.DayReviewSectionContent
import com.jjrapps.constanza.reminding.DayReviewSettingsUiState
import com.jjrapps.constanza.reminding.NotificationPermissionDecision
import com.jjrapps.constanza.reminding.SettingsSections
import com.jjrapps.constanza.reminding.SnoozeDuration
import com.jjrapps.constanza.reminding.SnoozeSettingsScreen
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Not an assertion — a camera, like `tracking.TodayRealRenderTest` and `habit.HabitListRealRenderTest`:
 * renders the graphite editor, progress, settings and onboarding screens at 360dp/2.625 with the
 * board's data, in Spanish, and logs each PNG as numbered base64 chunks under its own log tag so a
 * Gradle managed-device run leaves it in the pulled logcat. Each asserts nothing beyond "this
 * composed without throwing".
 *
 * Spanish is provided by hand (a configuration context in [LocalContext] and its configuration in
 * [LocalConfiguration]) rather than through `ProvideAppLocale`, which only bites below API 33.
 */
class GraphiteScreensRealRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renderEditor() = render("GraphiteRenderEditor") {
        HabitEditorScreen(
            state = HabitEditorUiState(
                habitId = 1,
                name = "Caminar",
                colorArgb = HabitColor.SAGE.argb,
                slots = listOf(ReminderSlot(id = 1, habitId = 1, minuteOfDay = 9 * 60 + 30, enabled = true)),
            ),
            actions = HabitEditorActions(onNameChange = {}, onColorChange = {}, onNotesChange = {}, onSave = {}),
            onScheduleParamChange = {},
            onSlotAction = {},
        )
    }

    @Test
    fun renderProgress() = render("GraphiteRenderProgress") {
        ProgressScreen(
            ProgressUiState(
                habitName = "Caminar",
                habitColorArgb = HabitColor.SAGE.argb,
                currentStreak = 12,
                bestStreak = 21,
                complianceRatio = 0.87,
                loaded = true,
            ),
        )
    }

    @Test
    fun renderSettings() = render("GraphiteRenderSettings") {
        SnoozeSettingsScreen(
            current = SnoozeDuration.TWENTY_MINUTES,
            onSelect = {},
            sections = SettingsSections(
                dayReview = {
                    DayReviewSectionContent(
                        uiState = DayReviewSettingsUiState(
                            reviewTimeMinuteOfDay = 21 * 60 + 30,
                            reviewFiresEveryNight = false,
                        ),
                        onReviewTimeChange = {},
                        onFiresEveryNightChange = {},
                    )
                },
                language = { LanguageSectionContent(selected = AppLanguage.SystemDefault, onSelect = {}) },
                data = {
                    DataPortabilitySectionContent(
                        importResult = ImportResult.Idle,
                        onExport = {},
                        onImport = {},
                        onDismissImportResult = {},
                    )
                },
            ),
        )
    }

    @Test
    fun renderOnboarding() = render("GraphiteRenderOnboarding") {
        val state = OnboardingUiState(
            pages = listOf(OnboardingPage.Intro, OnboardingPage.Permissions),
            index = 1,
            permission = NotificationPermissionDecision.SHOULD_REQUEST,
            canScheduleExactAlarms = true,
        )
        OnboardingScaffold(state = state, onPrimaryAction = {}) {
            OnboardingPermissionsPage(
                permission = state.permission,
                canScheduleExactAlarms = state.canScheduleExactAlarms,
                onPermissionRequested = {},
            )
        }
    }

    private fun render(logTag: String, content: @Composable () -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val spanish = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("es-ES")) }
        val spanishContext = base.createConfigurationContext(spanish)

        composeTestRule.setContent {
            // Read before LocalContext is swapped: the Spanish configuration context is not an
            // Activity, so the onboarding permission launcher could no longer find its registry.
            val registryOwner = checkNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalContext provides spanishContext,
                LocalConfiguration provides spanishContext.resources.configuration,
                LocalDensity provides Density(RENDER_DENSITY),
            ) {
                ConstanzaTheme {
                    Box(Modifier.size(RENDER_WIDTH_DP.dp, RENDER_HEIGHT_DP.dp).testTag(RENDER_TAG)) { content() }
                }
            }
        }

        val bitmap = composeTestRule.onNodeWithTag(RENDER_TAG).captureToImage().asAndroidBitmap()
        val encoded = ByteArrayOutputStream().use { bytes ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, bytes)
            Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        }
        val chunks = encoded.chunked(LOG_CHUNK_SIZE)
        chunks.forEachIndexed { index, chunk -> Log.i(logTag, "${index + 1}/${chunks.size}:$chunk") }
    }

    private companion object {
        const val RENDER_TAG = "graphite-screen-real-render"
        const val RENDER_DENSITY = 2.625f
        const val RENDER_WIDTH_DP = 360
        const val RENDER_HEIGHT_DP = 800
        const val LOG_CHUNK_SIZE = 3_000
        const val PNG_QUALITY = 100
    }
}
