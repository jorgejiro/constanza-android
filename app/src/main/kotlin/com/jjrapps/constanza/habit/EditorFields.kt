package com.jjrapps.constanza.habit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.ConstanzaControlDefaults
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing

/** The graphite field label: 13sp / 500 in the secondary text tone, standing above its field. */
private val FieldLabelSize = 13.sp
private val FieldLabelLineHeight = 18.sp

/**
 * Graphite redesign: a form field's label, drawn ABOVE a filled field instead of floating inside
 * its border.
 *
 * The visible text is cleared from semantics on purpose: the field it labels carries the same words
 * through [fieldLabel], so TalkBack still announces "Name, edit box" as one node — exactly what the
 * old in-border label gave it — and a test that finds the field by its label text still reaches the
 * editable node rather than this decorative copy. Two nodes with the same words would be read twice
 * and make `onNodeWithText(label)` ambiguous.
 */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .padding(bottom = Spacing.sm)
            .clearAndSetSemantics {},
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = FieldLabelSize,
            lineHeight = FieldLabelLineHeight,
            fontWeight = FontWeight.Medium,
        ),
        color = ConstanzaColors.OnBackgroundVariant,
    )
}

/** Puts [label] on the field's own semantics node — the accessible half of [FieldLabel]. */
internal fun Modifier.fieldLabel(label: String): Modifier = semantics { text = AnnotatedString(label) }

/**
 * The filled graphite field: [ConstanzaColors.Surface] inside, the control stroke
 * ([ConstanzaColors.ControlStroke] through M3 `outline`, 3.53:1) as its border at rest and the text
 * colour once focused. Callers pass `MaterialTheme.shapes.small` (12dp) as the shape.
 */
@Composable
internal fun editorFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = ConstanzaColors.Surface,
    unfocusedContainerColor = ConstanzaColors.Surface,
    disabledContainerColor = ConstanzaColors.Surface,
    errorContainerColor = ConstanzaColors.Surface,
    focusedBorderColor = MaterialTheme.colorScheme.onBackground,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    cursorColor = MaterialTheme.colorScheme.onBackground,
    focusedPlaceholderColor = ConstanzaColors.OnBackgroundMuted,
    unfocusedPlaceholderColor = ConstanzaColors.OnBackgroundMuted,
    focusedTrailingIconColor = ConstanzaColors.OnBackgroundVariant,
    unfocusedTrailingIconColor = ConstanzaColors.OnBackgroundVariant,
)

/** The graphite hairline between list rows: the decorative divider tone, never a control stroke. */
@Composable
internal fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** A graphite list row's main text: 17sp at regular weight in the text colour. */
@Composable
internal fun rowTextStyle() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal)

/** One neutral round −/+ button: a 40dp circle in the control stroke with the text colour. */
@Composable
internal fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(Dimens.Stepper),
        shape = CircleShape,
        contentPadding = PaddingValues(),
        border = ConstanzaControlDefaults.outlinedButtonBorder(enabled = enabled),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    ) {
        Text(text, style = rowTextStyle())
    }
}
