package com.jjrapps.constanza.habit

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.domain.model.Habit
import com.jjrapps.constanza.domain.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val WAIT_TIMEOUT_MS = 5_000L
private const val HABIT_NAME = "Read"

/**
 * Task 6a.4 (habit-management: Habit Archiving, Un-archiving does not back-fill). Every
 * post-click assertion is a bounded [waitUntil][
 * androidx.compose.ui.test.junit4.ComposeContentTestRule.waitUntil] poll rather than a bare
 * `assertExists()`, since [HabitRepository.setArchived] writes through Room and re-emits via
 * [HabitRepository.observeAll] asynchronously relative to the click that triggered it.
 */
@RunWith(AndroidJUnit4::class)
class HabitListArchiveComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fixture: HabitRepositoryTestFixture

    @Before
    fun setUp() {
        fixture = HabitRepositoryTestFixture(ApplicationProvider.getApplicationContext<Context>())
        runBlocking {
            fixture.habitRepository.create(
                Habit(
                    id = 0,
                    name = HABIT_NAME,
                    question = null,
                    colorArgb = 0,
                    notes = null,
                    archived = false,
                    archivedAt = null,
                    createdAt = Instant.parse("2026-09-01T08:00:00Z"),
                    sortOrder = 0,
                ),
                Schedule.Daily(),
            )
        }
    }

    @After
    fun tearDown() = fixture.close()

    private fun text(resId: Int) = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    private fun waitForNodeWithText(text: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoNodeWithText(text: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun archivingAndUnArchivingRoundTripsTheHabitThroughTheListsFilter() {
        val viewModel = fixture.habitListViewModel()
        composeTestRule.setContent {
            HabitListRoute(onCreateHabit = {}, onEditHabit = {}, viewModel = viewModel)
        }
        val archiveLabel = text(R.string.habit_list_archive)
        val unarchiveLabel = text(R.string.habit_list_unarchive)
        val showArchivedLabel = text(R.string.habit_list_show_archived)

        // Starts visible in the active (default) filter.
        waitForNodeWithText(HABIT_NAME)
        composeTestRule.onNodeWithText(archiveLabel).performClick()
        waitForNoNodeWithText(HABIT_NAME) // archiving moves it out of the active filter

        // Switching the filter reveals it again, now with an "un-archive" action.
        composeTestRule.onNodeWithText(showArchivedLabel).performClick()
        waitForNodeWithText(HABIT_NAME)
        composeTestRule.onNodeWithText(unarchiveLabel).performClick()
        waitForNoNodeWithText(HABIT_NAME) // un-archiving moves it out of the archived filter

        // Switching back to the active filter shows it round-tripped there.
        composeTestRule.onNodeWithText(showArchivedLabel).performClick()
        waitForNodeWithText(HABIT_NAME)
        composeTestRule.onNodeWithText(archiveLabel).assertExists()
    }
}
