package com.jjrapps.constanza.core.ui.component

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.Schedule
import com.jjrapps.constanza.habit.HabitListRoute
import com.jjrapps.constanza.habit.HabitRepositoryTestFixture
import com.jjrapps.constanza.tracking.TodayRoute
import com.jjrapps.constanza.tracking.todayViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val WAIT_TIMEOUT_MS = 5_000L
private const val HABIT_NAME = "Stretch"
private const val HABIT_COLOR_ARGB = 0xFF2196F3.toInt() // HabitColor.BLUE, arbitrary for this test
private const val OTHER_HABIT_COLOR_ARGB = 0xFF4CAF50.toInt() // a retired green preset, arbitrary for this test

/**
 * `habit-management`: "Habit Colour Visible Where Habits Are Listed", in its graphite form
 * (`visual-design-system`: the habit colour is shown only as a dot). On both listing screens the
 * 8dp [HabitDot] paints the habit's colour and the name renders in the text colour; on the habit
 * list two habits stay distinguishable by their dots.
 *
 * The dot is read through its [HabitDotColor] semantics hook in the unmerged tree. The name's colour
 * is read from the laid-out text ([SemanticsActions.GetTextLayoutResult]), which is where `Text`'s
 * own `color` parameter ends up, plus a check that no span of the name carries the habit colour. No
 * painted pixel is sampled.
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

    /**
     * Graphite redesign (`visual-design-system`: the habit colour is shown only as a dot): on Today
     * the 8dp [HabitDot] carries the habit's colour and the name renders in the text colour.
     *
     * The dot is found through its [HabitDotColor] semantics hook in the unmerged tree. The name's
     * colour is read from the laid-out text itself ([SemanticsActions.GetTextLayoutResult]), which
     * is where `Text`'s own `color` parameter ends up — the span check alone could not see it.
     */
    @Test
    fun theDotCarriesTheHabitColourAndTheNameDoesNotOnTheTodayScreen() = runBlocking {
        fixture.habitRepository.create(habitWithColor(HABIT_NAME, HABIT_COLOR_ARGB), Schedule.Daily())
        val viewModel = fixture.todayViewModel()

        composeTestRule.setContent { TodayRoute(onManageHabits = {}, viewModel = viewModel) }
        awaitNodeWithText(HABIT_NAME)

        val dot = composeTestRule.onNode(SemanticsMatcher.keyIsDefined(HabitDotColor), useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals("the dot must paint the habit's own colour", Color(HABIT_COLOR_ARGB), dot.config[HabitDotColor])

        val nameNode = composeTestRule.onNodeWithText(HABIT_NAME).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        nameNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val nameColour = layouts.first().layoutInput.style.color
        assertEquals("the name must render in the text colour", ConstanzaColors.OnBackground, nameColour)
        assertNotEquals("the name must not render in the habit colour", Color(HABIT_COLOR_ARGB), nameColour)
        val annotated = nameNode.config[SemanticsProperties.Text].first { it.text.contains(HABIT_NAME) }
        assertTrue(
            "no span of the name may carry the habit colour",
            annotated.spanStyles.none { it.item.color == Color(HABIT_COLOR_ARGB) },
        )
    }

    /** Graphite redesign, habit list half: each row's [HabitDot] carries that habit's own colour —
     *  so two habits are still told apart by colour — and neither name is painted in it. The dot is
     *  matched to its row through their shared parent (the clickable row) in the unmerged tree. */
    @Test
    fun eachRowsDotCarriesItsHabitsColourAndTheNamesDoNotOnTheHabitListScreen() = runBlocking {
        fixture.habitRepository.create(habitWithColor("Read", HABIT_COLOR_ARGB), Schedule.Daily())
        fixture.habitRepository.create(habitWithColor("Journal", OTHER_HABIT_COLOR_ARGB), Schedule.Daily())
        val viewModel = fixture.habitListViewModel()

        composeTestRule.setContent {
            HabitListRoute(onBack = {}, onCreateHabit = {}, onEditHabit = {}, viewModel = viewModel)
        }
        awaitNodeWithText("Read")

        assertEquals(Color(HABIT_COLOR_ARGB), dotColourOfRow("Read"))
        assertEquals(Color(OTHER_HABIT_COLOR_ARGB), dotColourOfRow("Journal"))
        assertNameInTextColour("Read", HABIT_COLOR_ARGB)
        assertNameInTextColour("Journal", OTHER_HABIT_COLOR_ARGB)
    }

    private fun habitWithColor(name: String, colorArgb: Int) = Habit(
        id = 0, name = name, colorArgb = colorArgb, notes = null,
        archived = false, archivedAt = null, createdAt = Instant.parse("2026-09-01T08:00:00Z"),
    )

    private fun awaitNodeWithText(label: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The colour of the one [HabitDot] whose parent row also holds [name]. */
    private fun dotColourOfRow(name: String): Color =
        composeTestRule.onNode(
            SemanticsMatcher.keyIsDefined(HabitDotColor) and hasParent(hasAnyDescendant(hasText(name))),
            useUnmergedTree = true,
        ).fetchSemanticsNode().config[HabitDotColor]

    /** The name's own text node (unmerged, so the subtitle beside it is not in the way) lays out in
     *  the text colour, and carries no span in the habit colour [colorArgb]. */
    private fun assertNameInTextColour(name: String, colorArgb: Int) {
        val node = composeTestRule.onNode(hasText(name), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val nameColour = layouts.first().layoutInput.style.color
        assertEquals("\"$name\" must render in the text colour", ConstanzaColors.OnBackground, nameColour)
        val annotated = node.config[SemanticsProperties.Text].first { it.text.contains(name) }
        assertTrue(
            "no span of \"$name\" may carry its habit colour",
            annotated.spanStyles.none { it.item.color == Color(colorArgb) },
        )
    }
}
