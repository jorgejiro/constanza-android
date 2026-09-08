package com.jjrapps.constanza.portability

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionDivider
import com.jjrapps.constanza.core.ui.component.SectionHeader
import com.jjrapps.constanza.core.ui.theme.Spacing

private const val BACKUP_JSON_MIME_TYPE = "application/json"

/** Zero horizontal, so an action starts on the same left edge as the heading above it rather than
 *  12dp past it. The vertical half of `ButtonDefaults.TextButtonContentPadding` is kept: it is what
 *  gives the two stacked actions air between them, and Material's own minimum interactive size
 *  still guarantees the touch target regardless of what is set here. */
private val ACTION_CONTENT_PADDING = PaddingValues(horizontal = 0.dp, vertical = Spacing.sm)

/**
 * Tasks 7.2/7.3/7.4. Rendered as an extra section on the existing Settings screen
 * ([com.jjrapps.constanza.reminding.SnoozeSettingsScreen]) rather than a destination of its own —
 * export/import is two buttons and one confirmation dialog, which does not justify a new
 * [com.jjrapps.constanza.core.ui.MainActivity] route and its navigation/rotation-survival cost.
 *
 * Export/import are text-label buttons here because the words are clearer than any glyph anyone
 * could pick for them, not because this codebase avoids icons — it does not (`HabitListScreen`'s
 * add/overflow, `ReminderTimeField`'s dropdown, `HabitColorPicker`/`CustomColorDialog`'s swatch
 * tick, `HabitEditorScreen`'s back arrow, and Today's own settings gear are all icons). The one real
 * constraint is `material-icons-core` being the sole icon artifact this project depends on — no
 * `material-icons-extended` — so a new icon has to be chosen from that set, or adding the extended
 * artifact needs its own deliberate decision (see `HabitColorPicker`'s note on picking
 * `KeyboardArrowUp`/`Down` over `ExpandLess`/`ExpandMore` for exactly this reason). Icon versus text
 * is otherwise an ordinary per-control judgement call, made per screen.
 */
@Composable
fun DataPortabilitySection(viewModel: DataPortabilityViewModel = hiltViewModel()) {
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val importResult by viewModel.importResult.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_JSON_MIME_TYPE),
    ) { uri -> uri?.let(viewModel::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImportUri = uri
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // settings-section-headings: the Column above already supplies the 16dp screen inset every
        // row and button here shares, so the header's own horizontal inset is zeroed out — otherwise
        // it would sit indented past the very buttons it introduces.
        SectionHeader(stringResource(R.string.portability_section_title), startInset = 0.dp, endInset = 0.dp)
        SectionDivider(startInset = 0.dp, endInset = 0.dp)
        // A TextButton's default content padding is 12dp horizontal, which would start these two
        // labels 12dp past the heading that introduces them and past the radio rows in the sections
        // above and below — an indent that belongs to no edge on this screen. Zeroed horizontally so
        // the action lines up with its own heading; the vertical half is kept, and Material's own
        // minimum interactive size still guarantees the touch target.
        TextButton(
            onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
            contentPadding = ACTION_CONTENT_PADDING,
        ) {
            Text(stringResource(R.string.portability_export_action))
        }
        TextButton(
            onClick = { importLauncher.launch(arrayOf(BACKUP_JSON_MIME_TYPE)) },
            contentPadding = ACTION_CONTENT_PADDING,
        ) {
            Text(stringResource(R.string.portability_import_action))
        }
        ImportResultMessage(importResult, onDismiss = viewModel::dismissImportResult)
    }

    pendingImportUri?.let { uri ->
        ImportConfirmDialog(
            onConfirm = {
                viewModel.confirmImport(uri)
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null },
        )
    }
}

/**
 * `internal` rather than `private` so `ImportResultMessageComposeTest` can render it directly.
 * Nothing outside this file calls it in production; the widened visibility exists solely so an
 * instrumented test can drive each [ImportResult] case and assert which string resource the branch
 * selected — see [importFailureMessage]'s KDoc for why that assertion is worth the visibility.
 */
@Composable
internal fun ImportResultMessage(result: ImportResult, onDismiss: () -> Unit) {
    when (result) {
        ImportResult.Idle -> Unit
        ImportResult.Success -> Text(stringResource(R.string.portability_import_success))
        is ImportResult.Failed -> Text(importFailureMessage(result.failure))
    }
    if (result != ImportResult.Idle) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
    }
}

/**
 * app-localization: the one place an [ImportFailure] becomes words. `BackupImporter` is deliberately
 * Android-free and has no `Context`, so it can only report *which* failure occurred and with what
 * arguments; the resource lookup belongs here, where `stringResource` follows the resolved language.
 * The `when` is exhaustive on purpose — a new failure case will not compile until it has copy.
 *
 * `internal` rather than `private`, and the reason is the limit of that exhaustiveness: the
 * compiler proves every case is *handled*, never that a case picked the *right* resource. A
 * copy-paste that maps `MalformedFile` to the unreadable-file wording, or that swaps `%1$d` and
 * `%2$d` in the two-argument branch, compiles cleanly. `ImportResultMessageComposeTest` renders
 * this composable directly and names the expected resource id independently for each branch, which
 * is only possible if the declaration is visible from `androidTest`.
 */
@Composable
internal fun importFailureMessage(failure: ImportFailure): String = when (failure) {
    ImportFailure.UnreadableFile -> stringResource(R.string.portability_import_error_unreadable_file)
    ImportFailure.MalformedFile -> stringResource(R.string.portability_import_error_malformed_file)
    is ImportFailure.UnsupportedVersion ->
        stringResource(R.string.portability_import_error_unsupported_version, failure.fileVersion)
    is ImportFailure.UnknownSlotReference ->
        stringResource(
            R.string.portability_import_error_unknown_slot_reference,
            failure.habitId,
            failure.slotId,
        )

    is ImportFailure.UnsupportedScheduleKind ->
        stringResource(
            R.string.portability_import_error_unsupported_schedule_kind,
            failure.habitId,
            failure.kind,
        )
}

/** data-portability: Import MUST be preceded by an explicit confirmation stating the action is
 *  destructive and irreversible, and MUST NOT proceed without it. */
@Composable
private fun ImportConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.portability_import_confirm_title)) },
        text = { Text(stringResource(R.string.portability_import_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.portability_import_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
