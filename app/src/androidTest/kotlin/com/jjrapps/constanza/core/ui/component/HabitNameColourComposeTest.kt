package com.jjrapps.constanza.core.ui.component

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitListRoute
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.tracking.TodayRoute
import com.jjrapps.constanza.tracking.todayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val WAIT_TIMEOUT_MS = 5_000L
private const val HABIT_NAME = "Stretch"
private const val HABIT_COLOR_ARGB = 0xFF2196F3.toInt() // HabitColor.BLUE, arbitrary for this test
private const val OTHER_HABIT_COLOR_ARGB = 0xFF4CAF50.toInt() // HabitColor.GREEN, arbitrary for this test

/**
 * Colour overhaul, replacing `HabitColorDotComposeTest` (deleted with `HabitColorDot`, which this
 * class used to exercise). Task 4.7's spec requirement — `habit-management`: "Habit Colour Visible
 * Where Habits Are Listed" — is unchanged: a habit's colour must still be visibly rendered on both
 * listing screens. What moved is WHERE that colour is now drawn: onto the habit's own name text
 * rather than a leading dot beside it, because a future habits-by-days report needs colour ON the
 * name to follow a row across a grid, which a dot cannot do (see `TodayScreen.HabitRollupHeader`'s
 * own KDoc). This class asserts the same two on-screen scenarios `HabitColorDotComposeTest` did —
 * colour visible on the today screen, and two habits distinguished by colour on the habit list —
 * against the new location, and additionally names the row by its still-present habit name rather
 * than by a now-deleted test tag.
 *
 * Asserted through the real [androidx.compose.ui.text.AnnotatedString] Compose exposes on
 * [SemanticsProperties.Text], not a `captureToImage` pixel sample. Neither `TodayScreen`'s
 * `demotedSuffix` nor `HabitListScreen`'s `HabitRow` pass the habit colour through `Text`'s own
 * `color` parameter — that paints only at the layout layer and never reaches semantics — both build
 * an `AnnotatedString` with an explicit `SpanStyle(color = …)` instead, so the colour is part of the
 * rendered content itself and is exactly what a screen reader's text object would carry. Reading
 * [SemanticsProperties.Text] back off a node is already this suite's own precedent
 * ([com.jjrapps.constanza.localization.LanguageOverrideComposeTest],
 * [com.jjrapps.constanza.portability.ImportResultMessageComposeTest]); this only reaches one layer
 * deeper, into the span carried alongside the string rather than the plain string alone. No
 * precedent in this suite asserts a *painted* pixel colour, and none is added here.
 */
@RunWith(AndroidJUnit4::class)
class HabitNameColourComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fixture: HabitRepositoryTestFixture

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
    }

    /** Ordering lives in [HabitRepositoryTestFixture.close] — see its KDoc for why the ViewModel
     *  scopes must die before the database. */
    @After
    fun tearDown() = fixture.close()

    @Test
    fun theHabitNameRendersInTheHabitsOwnColourOnTheTodayScreen() = runBlocking {
        fixture.habitRepository.create(habitWithColor(HABIT_NAME, HABIT_COLOR_ARGB), Schedule.Daily())
        val viewModel = fixture.todayViewModel()

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        awaitNodeWithText(HABIT_NAME)

        assertNameColour(HABIT_NAME, HABIT_COLOR_ARGB)
    }

    @Test
    fun theHabitNameRendersInEachHabitsOwnColourOnTheHabitListScreenAndDistinguishesTwoHabits() = runBlocking {
        fixture.habitRepository.create(habitWithColor("Read", HABIT_COLOR_ARGB), Schedule.Daily())
        fixture.habitRepository.create(habitWithColor("Journal", OTHER_HABIT_COLOR_ARGB), Schedule.Daily())
        val viewModel = fixture.habitListViewModel()

        composeTestRule.setContent {
            HabitListRoute(onBack = {}, onCreateHabit = {}, onEditHabit = {}, viewModel = viewModel)
        }
        awaitNodeWithText("Read")

        assertNameColour("Read", HABIT_COLOR_ARGB)
        assertNameColour("Journal", OTHER_HABIT_COLOR_ARGB)
    }

    private fun habitWithColor(name: String, colorArgb: Int) = Habit(
        id = 0, name = name, colorArgb = colorArgb, notes = null,
        archived = false, archivedAt = null, createdAt = Instant.parse("2026-09-01T08:00:00Z"), sortOrder = 0,
    )

    private fun awaitNodeWithText(label: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Reads the rendered [androidx.compose.ui.text.AnnotatedString] back off the node found by
     *  [name] and asserts a span covering the substring [name] carries [colorArgb] — the exact
     *  colour the habit was created with, on the exact substring that IS its name (a collapsed
     *  multi-slot row appends a demoted day-status suffix after it, which must NOT share this span —
     *  see `TodayScreen.demotedSuffix`'s own KDoc). */
    private fun assertNameColour(name: String, colorArgb: Int) {
        val node = composeTestRule.onNodeWithText(name, substring = true).fetchSemanticsNode()
        val annotated = node.config[SemanticsProperties.Text].first { it.text.contains(name) }
        val start = annotated.text.indexOf(name)
        val end = start + name.length
        val nameColour = annotated.spanStyles
            .firstOrNull { spanRange -> spanRange.start <= start && spanRange.end >= end }
            ?.item
            ?.color
        assertEquals("\"$name\" must render in its own habit colour", Color(colorArgb), nameColour)
    }
}
