package com.jjrapps.constanza.portability

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.jjrapps.constanza.R
import com.jjrapps.constanza.core.ui.component.SectionHeader
import com.jjrapps.constanza.core.ui.component.SettingsNavigationRow
import com.jjrapps.constanza.core.ui.component.SettingsRowDivider
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Spacing

private const val BACKUP_JSON_MIME_TYPE = "application/json"

/**
 * Tasks 7.2/7.3/7.4. Rendered as an extra section on the existing Settings screen
 * ([com.jjrapps.constanza.reminding.SnoozeSettingsScreen]) rather than a destination of its own —
 * export/import is two buttons and one confirmation dialog, which does not justify a new
 * [com.jjrapps.constanza.core.ui.MainActivity] route and its navigation/rotation-survival cost.
 *
 * Export/import are worded rows (graphite redesign: list rows with a trailing chevron) because the
 * words are clearer than any glyph anyone could pick for them.
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

    DataPortabilitySectionContent(
        importResult = importResult,
        onExport = { exportLauncher.launch(viewModel.suggestedFileName()) },
        onImport = { importLauncher.launch(arrayOf(BACKUP_JSON_MIME_TYPE)) },
        onDismissImportResult = viewModel::dismissImportResult,
    )

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
 * Presentational half of [DataPortabilitySection]: the heading, the two actions as graphite list
 * rows with a trailing chevron, and the last import's outcome. No launcher and no ViewModel, so a
 * Compose test or a render can draw it directly.
 */
@Composable
fun DataPortabilitySectionContent(
    importResult: ImportResult,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismissImportResult: () -> Unit,
) {
    Column {
        SectionHeader(stringResource(R.string.portability_section_title))
        SettingsNavigationRow(title = stringResource(R.string.portability_export_action), onClick = onExport)
        SettingsRowDivider()
        SettingsNavigationRow(title = stringResource(R.string.portability_import_action), onClick = onImport)
        SettingsRowDivider()
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            ImportResultMessage(importResult, onDismiss = onDismissImportResult)
        }
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
        ImportResult.Success -> Text(
            stringResource(R.string.portability_import_success),
            modifier = Modifier.padding(top = Spacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = ConstanzaColors.OnBackgroundVariant,
        )
        is ImportResult.Failed -> Text(
            importFailureMessage(result.failure),
            modifier = Modifier.padding(top = Spacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
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
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.portability_import_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
