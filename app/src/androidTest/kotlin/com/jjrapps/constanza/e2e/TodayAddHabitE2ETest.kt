package com.jjrapps.constanza.e2e

import android.content.Context
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.MainActivity
import com.jjrapps.constanza.tracking.TODAY_ADD_HABIT_EMPTY_TEST_TAG
import com.jjrapps.constanza.tracking.TODAY_ADD_HABIT_TRAILING_TEST_TAG
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val UI_TIMEOUT_MS = 15_000L
private const val CREATED_HABIT = "Read"

/**
 * today-add-habit, end to end: Today's add action really reaches the habit editor, and leaving that
 * editor really comes back to Today.
 *
 * The return leg is the half worth a real `MainActivity`. The editor's two exits — `onDone` and
 * `onBack` — both follow `ConstanzaRoute.HabitEditor.origin`, and the habit list this app hoists
 * has no back route of its own, so an entry tagged with the wrong origin would drop a user who
 * tapped "Add habit" on Today onto a screen they never asked for and cannot leave. A route-level
 * Compose test cannot see that: it only sees a lambda being called. This can.
 *
 * Grants nothing and answers no system dialog. `POST_NOTIFICATIONS` is a one-way door within an
 * installation and [CoreFlowE2ETest]'s first methods depend on still being able to see the real
 * prompt, so this class must never be the thing that opens it — which is also why its name sorts
 * after `CoreFlowE2ETest` in the same package. Whether the notification banner happens to be on
 * screen is left unasserted for the same reason: it is above the content this test drives and does
 * not change it.
 */
@RunWith(AndroidJUnit4::class)
class TodayAddHabitE2ETest {

    /** Empty rather than `createAndroidComposeRule<MainActivity>()`, for the reason
     *  [CoreFlowE2ETest] documents: a rule-launched Activity comes up before `@Before` can clear
     *  the previous test's habits, so Today's first emission would be built from them — and this
     *  test's whole subject is which presentation an empty Today shows. */
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var fixture: CoreFlowTestFixture
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        fixture = CoreFlowTestFixture(context)
        runBlocking { fixture.reset() }
    }

    @After
    fun tearDown() {
        scenario?.close()
        fixture.close()
    }

    @Test
    fun theCentredAddActionOpensTheEditorAndBackingOutReturnsToToday() {
        launchOnboardedApp()

        awaitTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG)
        compose.onNodeWithTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG).performClick()
        awaitText(string(R.string.habit_editor_title_create))

        compose.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        awaitText(string(R.string.today_title))
        compose.onNodeWithText(string(R.string.habit_list_title)).assertDoesNotExist()
        compose.onNodeWithTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG).assertExists()
    }

    /**
     * The save leg, and with it the second presentation: a habit created from Today's empty state
     * lands back on Today with that habit on the list, where the SAME action is now offered at the
     * end of the list instead of centred in an empty one.
     */
    @Test
    fun savingFromTodaysAddActionReturnsToTodayWhereTheActionIsNowTrailing() {
        launchOnboardedApp()

        awaitTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG)
        compose.onNodeWithTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG).performClick()

        awaitText(string(R.string.habit_editor_name_label))
        compose.onNodeWithText(string(R.string.habit_editor_name_label)).performTextInput(CREATED_HABIT)
        // The reminder switch, addressed as the one toggleable node rather than by its label, for
        // the reason CoreFlowE2ETest documents: the row is not toggleable as a whole, so a tap on
        // the label passes straight through and saves a habit with no enabled slot.
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.habit_editor_save)).performScrollTo().performClick()

        awaitText(string(R.string.today_title))
        compose.onNodeWithText(string(R.string.habit_list_title)).assertDoesNotExist()

        awaitTag(TODAY_ADD_HABIT_TRAILING_TEST_TAG)
        compose.onNodeWithTag(TODAY_ADD_HABIT_EMPTY_TEST_TAG).assertDoesNotExist()
        compose.onNodeWithText(CREATED_HABIT).assertExists()
    }

    /** Seeds the onboarding flag durably before launch — `edit` does not return until the write
     *  commits — so the gate's first read is guaranteed to observe it and the app opens on Today.
     *  Same shape, and same reasoning, as [CoreFlowE2ETest]'s own helper. */
    private fun launchOnboardedApp() {
        runBlocking { fixture.seedOnboardingDone() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText(string(R.string.today_title))
    }

    private fun string(resId: Int) = context.getString(resId)

    /** An idle composition is not a rendered one: every screen here is fed by Room `Flow`s, so a
     *  click on a node that has not arrived yet either misses it or hits an empty row. */
    private fun awaitText(label: String) {
        compose.waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
