package com.jjrapps.constanza.portability

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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

private const val BACKUP_JSON_MIME_TYPE = "application/json"

/**
 * Tasks 7.2/7.3/7.4. Rendered as an extra section on the existing Settings screen
 * ([com.jjrapps.constanza.reminding.SnoozeSettingsScreen]) rather than a destination of its own —
 * export/import is two buttons and one confirmation dialog, which does not justify a new
 * [com.jjrapps.constanza.core.ui.MainActivity] route and its navigation/rotation-survival cost.
 *
 * Text-label buttons throughout (this project has `material-icons-core` only, no
 * `material-icons-extended`, the established fallback per prior work units).
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
        Text(stringResource(R.string.portability_section_title))
        TextButton(onClick = { exportLauncher.launch(viewModel.suggestedFileName()) }) {
            Text(stringResource(R.string.portability_export_action))
        }
        TextButton(onClick = { importLauncher.launch(arrayOf(BACKUP_JSON_MIME_TYPE)) }) {
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

@Composable
private fun ImportResultMessage(result: ImportResult, onDismiss: () -> Unit) {
    when (result) {
        ImportResult.Idle -> Unit
        ImportResult.Success -> Text(stringResource(R.string.portability_import_success))
        is ImportResult.Failed -> Text(result.message)
    }
    if (result != ImportResult.Idle) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
    }
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
