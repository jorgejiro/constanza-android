package com.jjrapps.constanza.portability

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jjrapps.constanza.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Carried-forward item `import-failure-message-mapping-is-untested`. The `when` over
 * [ImportFailure] in `DataPortabilityScreen` is exhaustive, so the compiler already guarantees
 * every case is *handled*. It guarantees nothing about any case being handled *correctly*: a
 * branch pointing at a neighbouring resource id, or a two-argument branch with `%1$d` and `%2$d`
 * fed in the wrong order, compiles and ships.
 *
 * **The assertions here are deliberately not tautological.** Each scenario names the resource id it
 * expects *independently of the production `when`*, resolves it through
 * [Context.getString] with its own arguments, and compares that against the text the composable
 * actually rendered. What is under test is therefore which id a branch selected, not whether
 * `stringResource` works. It is also language-agnostic by construction: expected and actual are
 * resolved through the same configuration, so this passes unchanged on a Spanish device without
 * pinning a single Spanish sentence.
 *
 * The two-argument branch uses deliberately distinct, recognisable numbers ([HABIT_ID] and
 * [SLOT_ID]): equal placeholders would let a swapped `%1$d`/`%2$d` pass, which is precisely the
 * defect this scenario exists to catch.
 *
 * Only a presentational composable is rendered, per this codebase's container/presentational split,
 * so `createComposeRule()` suffices and no Hilt-enabled Activity is needed.
 */
@RunWith(AndroidJUnit4::class)
class ImportResultMessageComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun render(result: ImportResult) {
        composeTestRule.setContent {
            Column(modifier = Modifier.testTag(HOST_TAG)) {
                ImportResultMessage(result, onDismiss = {})
            }
        }
    }

    /**
     * The texts rendered by [ImportResultMessage], in composition order. The host [Column] does not
     * merge its descendants, so its children are exactly the message [androidx.compose.material3.Text]
     * (when there is one) and the dismiss button, which merges its own label.
     */
    private fun renderedTexts(): List<String> =
        composeTestRule.onNodeWithTag(HOST_TAG)
            .onChildren()
            .fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            }

    private fun assertMessageIs(expected: String) {
        assertEquals(
            "the rendered message must be the resource this test named, not another branch's",
            listOf(expected, context.getString(R.string.action_dismiss)),
            renderedTexts(),
        )
    }

    @Test
    fun unreadableFileSelectsTheUnreadableFileResource() {
        render(ImportResult.Failed(ImportFailure.UnreadableFile))

        assertMessageIs(context.getString(R.string.portability_import_error_unreadable_file))
    }

    @Test
    fun malformedFileSelectsTheMalformedFileResource() {
        render(ImportResult.Failed(ImportFailure.MalformedFile))

        assertMessageIs(context.getString(R.string.portability_import_error_malformed_file))
    }

    @Test
    fun unsupportedVersionSelectsItsResourceAndCarriesTheFileVersion() {
        render(ImportResult.Failed(ImportFailure.UnsupportedVersion(fileVersion = FILE_VERSION)))

        assertMessageIs(
            context.getString(R.string.portability_import_error_unsupported_version, FILE_VERSION),
        )
    }

    /**
     * The one branch with two interchangeable placeholders. Asserting equality against the resource
     * formatted with `habitId` *then* `slotId` is what pins the order; [HABIT_ID] and [SLOT_ID]
     * differ so that swapping them produces a different sentence and fails here.
     */
    @Test
    fun unknownSlotReferenceSelectsItsResourceWithHabitIdBeforeSlotId() {
        render(
            ImportResult.Failed(
                ImportFailure.UnknownSlotReference(habitId = HABIT_ID, slotId = SLOT_ID),
            ),
        )

        assertMessageIs(
            context.getString(
                R.string.portability_import_error_unknown_slot_reference,
                HABIT_ID,
                SLOT_ID,
            ),
        )
        val swapped = context.getString(
            R.string.portability_import_error_unknown_slot_reference,
            SLOT_ID,
            HABIT_ID,
        )
        assertNotEquals(
            "the two ids must not be interchangeable, or this scenario would prove nothing",
            swapped,
            renderedTexts().first(),
        )
    }

    @Test
    fun successSelectsTheSuccessResource() {
        render(ImportResult.Success)

        assertMessageIs(context.getString(R.string.portability_import_success))
    }

    /** Idle is the only case that renders nothing at all — no message and no way to dismiss one. */
    @Test
    fun idleRendersNeitherAMessageNorTheDismissAction() {
        render(ImportResult.Idle)

        assertEquals(emptyList<String>(), renderedTexts())
        composeTestRule.onNodeWithText(context.getString(R.string.action_dismiss)).assertDoesNotExist()
    }

    /** The complement of the scenario above: every non-Idle case offers the dismiss action. */
    @Test
    fun aNonIdleResultRendersTheDismissAction() {
        render(ImportResult.Failed(ImportFailure.MalformedFile))

        composeTestRule.onNodeWithText(context.getString(R.string.action_dismiss)).assertIsDisplayed()
    }

    private companion object {
        const val HOST_TAG = "import-result-message-host"
        const val FILE_VERSION = 99
        const val HABIT_ID = 71L
        const val SLOT_ID = 93L
    }
}
