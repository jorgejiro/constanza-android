package com.jjrapps.constanza.portability

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Surfaced by [DataPortabilityViewModel.importResult] so the UI can render a result message
 *  without re-deriving it from an exception type. */
sealed interface ImportResult {
    data object Idle : ImportResult
    data object Success : ImportResult
    data class Failed(val message: String) : ImportResult
}

/**
 * Tasks 7.2/7.3/7.4 — the SAF read/write boundary around [BackupExporter]/[BackupImporter], which
 * stay Android-free themselves. [confirmImport] is the ONLY entry point that writes anything, and
 * it is reached exclusively from the destructive-confirmation dialog's confirm action (task 7.4;
 * data-portability: Declined confirmation changes nothing) — there is no other call site.
 */
@HiltViewModel
class DataPortabilityViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
) : ViewModel() {

    private val importResultState = MutableStateFlow<ImportResult>(ImportResult.Idle)
    val importResult: StateFlow<ImportResult> = importResultState.asStateFlow()

    fun suggestedFileName(): String = exporter.fileName()

    fun export(uri: Uri) {
        viewModelScope.launch {
            val json = exporter.serialize(exporter.buildBackup())
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
    }

    fun confirmImport(uri: Uri) {
        viewModelScope.launch {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            importResultState.value = runImport(text)
        }
    }

    private suspend fun runImport(text: String?): ImportResult {
        if (text == null) return ImportResult.Failed("Could not read the selected file.")
        return try {
            importer.replaceAll(importer.parseAndValidate(text))
            ImportResult.Success
        } catch (e: MalformedBackupException) {
            ImportResult.Failed(e.message.orEmpty())
        } catch (e: UnsupportedBackupVersionException) {
            ImportResult.Failed(e.message.orEmpty())
        }
    }

    fun dismissImportResult() {
        importResultState.value = ImportResult.Idle
    }
}
