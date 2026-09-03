package com.jjrapps.constanza.localization

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.jjrapps.constanza.reminding.ReminderSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * app-localization: the single source of truth for the language override, split exactly at API 33
 * (design.md D1) — the two stores are never both written on the same device. This is the ONLY
 * class in the app that references `Build.VERSION.SDK_INT >= 33` / `LocaleManager` for this
 * feature; every other caller (the picker, [NotificationPoster]) goes through here.
 */
@Singleton
class AppLocaleController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: ReminderSettingsStore,
) {
    /** design.md D2: the picker calls this on every `ON_START` rather than observing, because a
     *  33+ system-Settings change while backgrounded is only observable by re-reading. */
    suspend fun current(): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            AppLanguage.fromTag(manager.applicationLocales.toLanguageTags().ifBlank { null })
        } else {
            AppLanguage.fromTag(settingsStore.currentLanguageTag())
        }

    /** app-localization: Three-State Language Override / API 33+ System-Settings Parity. Writes
     *  through whichever surface is authoritative on this API level; the other is never touched
     *  (design.md D1). [AppLanguage.SystemDefault] clears rather than stores a third value
     *  (design.md D7). */
    suspend fun set(language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            manager.applicationLocales =
                if (language.tag == null) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(language.tag)
                }
        } else {
            settingsStore.setLanguageTag(language.tag)
        }
    }

    /** Below API 33 only. There is no `Flow` API over `LocaleManager`, and design.md D2 makes that
     *  acceptable: the picker re-reads through [current] on every `ON_START` instead of relying on
     *  a continuously observed value, so this Flow's staleness on 33+ is never actually consulted
     *  by that call site. */
    fun observe(): Flow<AppLanguage> = settingsStore.languageTag.map { AppLanguage.fromTag(it) }

    /** [NotificationPoster]'s per-post wrap (design.md D4). On API 33+ the system already applied
     *  the override before this process ever existed, so [context] itself is already correct and
     *  is returned unchanged. Below 33, wraps [context] against the persisted tag via
     *  [localizedContext]. */
    suspend fun localizedApplicationContext(): Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context
        } else {
            localizedContext(context, settingsStore.currentLanguageTag())
        }
}
