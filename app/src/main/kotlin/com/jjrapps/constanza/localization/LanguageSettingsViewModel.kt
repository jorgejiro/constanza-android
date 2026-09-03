package com.jjrapps.constanza.localization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * app-localization: Three-State Language Override, and the in-app half of API 33+ System-Settings
 * Parity.
 *
 * This deliberately does NOT observe [AppLocaleController.observe] (design.md D2). On API 33+ the
 * authoritative store is the platform's `LocaleManager`, which exposes no `Flow`: a user who
 * changes Constanza's language from Android Settings while this screen sits in the background
 * would come back to a picker still showing the old selection. [refresh] re-reads the authoritative
 * surface for the running API level, and the section calls it on every `ON_START`, which makes that
 * case deterministic instead of relying on Activity recreation happening to occur.
 *
 * Below 33 the DataStore-backed flow would have worked, but one code path that is correct on every
 * API level beats two that each only work on one.
 */
@HiltViewModel
class LanguageSettingsViewModel @Inject constructor(
    private val appLocaleController: AppLocaleController,
) : ViewModel() {

    private val _selected = MutableStateFlow(AppLanguage.SystemDefault)

    /** Seeded with [AppLanguage.SystemDefault] rather than `null`: the picker has no meaningful
     *  empty state, and the first [refresh] lands within a frame of the section appearing. */
    val selected: StateFlow<AppLanguage> = _selected.asStateFlow()

    /** Called on every `ON_START`. See the class KDoc for why this is a re-read and not a subscription. */
    fun refresh() {
        viewModelScope.launch { _selected.value = appLocaleController.current() }
    }

    /**
     * Writes through [AppLocaleController], which owns the API-33 split. The local value is updated
     * from the store's own answer rather than optimistically from [language], so the picker can
     * never show a selection the store did not actually accept.
     */
    fun select(language: AppLanguage) {
        viewModelScope.launch {
            appLocaleController.set(language)
            _selected.value = appLocaleController.current()
        }
    }
}
