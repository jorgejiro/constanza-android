package com.jjrapps.constanza.reminding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * design.md §14 (`reminding/` snooze settings): task 5.6's snooze-duration preference, default
 * [SnoozeDuration.DEFAULT], and task 5.2's "already asked once" flag that lets
 * [NotificationPermission] approximate the system's permanently-blocked state. Both live in the
 * same [DataStore] instance ([com.jjrapps.constanza.core.di.DataStoreModule]) since each is a
 * single scalar key with no schema in common to protect.
 *
 * [currentSnoozeDuration] is the one-shot suspend read work unit 5-ii's `SnoozeWorker` needs;
 * [snoozeDuration] is the continuously-observed [Flow] the future settings screen (task 6b.5)
 * binds to.
 *
 * [onboardingDone] and [setOnboardingDone] back `first-run-onboarding`'s once-per-install gate
 * (design.md §4.1, §8.1, A6). The companion object is `internal`, not `private`: the androidTest
 * `CoreFlowTestFixture` seeds through the app's own singleton `DataStore` (design.md §8.1, A5) and
 * references [ONBOARDING_DONE_KEY] directly, so a rename in production breaks the seeding fixture
 * at compile time instead of silently seeding a key nobody reads.
 */
class ReminderSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val snoozeDuration: Flow<SnoozeDuration> = dataStore.data.map { prefs ->
        SnoozeDuration.fromMinutes(prefs[SNOOZE_DURATION_MINUTES_KEY] ?: SnoozeDuration.DEFAULT.minutes)
    }

    suspend fun currentSnoozeDuration(): SnoozeDuration = snoozeDuration.first()

    /** Absent means `false` (design.md A6): every pre-existing install onboards exactly once,
     *  with no migration inferring prior use from [hasRequestedNotificationPermission]. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_DONE_KEY] ?: false }

    // RedundantSuspendModifier is suppressed on these three functions, not disabled project-wide:
    // `:app:detektMain`'s hand-rolled task cannot resolve `DataStore<Preferences>.edit`'s suspend
    // signature from the `datastore-preferences` AAR — the exact same class of type-resolution gap
    // this task's own KDoc already documents for `ForbiddenMethodCall` (app/build.gradle.kts).
    // Proven a false positive, not guessed: removing `suspend` here fails `compileDebugKotlin`
    // with "Suspend function ... can only be called from a coroutine or another suspend function"
    // at every one of these three call sites.
    @Suppress("RedundantSuspendModifier")
    suspend fun setSnoozeDuration(duration: SnoozeDuration) {
        dataStore.edit { it[SNOOZE_DURATION_MINUTES_KEY] = duration.minutes }
    }

    @Suppress("RedundantSuspendModifier")
    suspend fun hasRequestedNotificationPermission(): Boolean =
        dataStore.data.map { it[REQUESTED_NOTIFICATION_PERMISSION_KEY] ?: false }.first()

    @Suppress("RedundantSuspendModifier")
    suspend fun recordRequestedNotificationPermission() {
        dataStore.edit { it[REQUESTED_NOTIFICATION_PERMISSION_KEY] = true }
    }

    /** Onboarding's write-once completion flag (design.md §9). There is deliberately no way to
     *  un-onboard from production code — only `CoreFlowTestFixture.reset()` writes `false`, through
     *  this same shared [DataStore]. */
    @Suppress("RedundantSuspendModifier")
    suspend fun setOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE_KEY] = true }
    }

    /** app-localization: the below-API-33 language override (design.md D1). Absent means
     *  [AppLanguage.SystemDefault] on read — there is no third persisted value (design.md D7). On
     *  API 33+ this key is never written or read; [AppLocaleController] is the only place that API
     *  split lives. */
    val languageTag: Flow<String?> = dataStore.data.map { it[LANGUAGE_TAG_KEY] }

    @Suppress("RedundantSuspendModifier")
    suspend fun currentLanguageTag(): String? = languageTag.first()

    /** design.md D7 — the tri-state clear removes, it never stores a third value. `tag == null`
     *  (`AppLanguage.SystemDefault`) removes the key instead of writing one, so a later device
     *  locale change takes effect exactly as if no override had ever been set. */
    @Suppress("RedundantSuspendModifier")
    suspend fun setLanguageTag(tag: String?) {
        dataStore.edit { prefs ->
            if (tag == null) prefs.remove(LANGUAGE_TAG_KEY) else prefs[LANGUAGE_TAG_KEY] = tag
        }
    }

    internal companion object {
        val SNOOZE_DURATION_MINUTES_KEY = intPreferencesKey("snooze_duration_minutes")
        val REQUESTED_NOTIFICATION_PERMISSION_KEY = booleanPreferencesKey("requested_notification_permission")
        val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")
        val LANGUAGE_TAG_KEY = stringPreferencesKey("language_tag")
    }
}
