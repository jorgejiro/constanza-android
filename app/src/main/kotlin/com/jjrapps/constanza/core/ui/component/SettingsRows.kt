package com.jjrapps.constanza.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.icons.ConstanzaIcons
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens
import com.jjrapps.constanza.core.ui.theme.Spacing

private val TrailingChevronSize = 20.dp

/**
 * Graphite redesign: one Settings list row — a title in the text colour, an optional [subtitle] in
 * the secondary tone beneath it, and a trailing chevron saying the row opens something. The whole
 * row is one [Role.Button] node, so TalkBack reads title and subtitle together as one control.
 * Callers draw the hairline below it with [SettingsRowDivider].
 */
@Composable
fun SettingsNavigationRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.SettingsRow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Normal),
                color = ConstanzaColors.OnBackground,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConstanzaColors.OnBackgroundVariant,
                )
            }
        }
        // Decorative: the row's own text already says what it opens.
        Icon(
            ConstanzaIcons.ChevronEnd,
            contentDescription = null,
            tint = ConstanzaColors.OnBackgroundVariant,
            modifier = Modifier.padding(start = Spacing.md).size(TrailingChevronSize),
        )
    }
}

/** The hairline between Settings rows, inset to the screen margin like the rows' own text. */
@Composable
fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A small single-choice dialog: one radio row per option (each paired with its label) ([Role.RadioButton] inside a
 * `selectableGroup`, so a test can ask which option is selected), on the neutral raised dialog
 * surface. Choosing an option applies it at once — the platform's own single-choice list
 * behaviour — and [onDismiss] closes the dialog; the one button is Cancel.
 */
@Composable
fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { (option, label) ->
                    ChoiceRow(label = label, selected = option == selected, onClick = { onSelect(option) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = Spacing.lg), color = ConstanzaColors.OnBackground)
    }
}
