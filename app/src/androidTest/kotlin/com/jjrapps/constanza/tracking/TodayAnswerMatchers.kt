package com.jjrapps.constanza.tracking

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.test.core.app.ApplicationProvider
import com.jjrapps.constanza.R

/**
 * Graphite redesign: Today's answer controls are round ✕/✓ icon buttons with no visible text, so
 * a test finds them by their accessible label — "Answer yes for <habit>" / "Answer no for <habit>"
 * — rather than by a "Yes"/"No" word that is no longer on the row.
 *
 * Matched on the label's habit-independent part (the format with an empty habit name), so one
 * matcher finds every habit's button and a count over it is a count of pending answer controls.
 * The change dialog's own "Yes"/"No" options are plain text and never match these, which keeps
 * "the row's button" and "the dialog's option" distinguishable in the same test.
 */
private fun answerLabelPrefix(resId: Int): String =
    ApplicationProvider.getApplicationContext<Context>().getString(resId, "").trim()

/** Every row-level "Yes" (✓) answer button, whatever habit it answers. */
internal fun isAnswerYesButton(): SemanticsMatcher =
    hasContentDescription(answerLabelPrefix(R.string.today_answer_yes_a11y), substring = true)

/** Every row-level "No" (✕) answer button, whatever habit it answers. */
internal fun isAnswerNoButton(): SemanticsMatcher =
    hasContentDescription(answerLabelPrefix(R.string.today_answer_no_a11y), substring = true)
