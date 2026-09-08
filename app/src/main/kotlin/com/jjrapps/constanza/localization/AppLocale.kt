package com.jjrapps.constanza.localization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * app-localization: `AppLocaleInstrumentedTest`'s assertions are exactly this contract.
 * Returns [base] unchanged when [tag] is `null` (`AppLanguage.SystemDefault`) — there is nothing
 * to override. The configuration override is stubbed out under AGP's mockable-jar unit-test path
 * (the same trap already documented at `NotificationPoster.kt`), so this is instrumented-only to
 * exercise for real.
 *
 * **The result MUST stay a [android.content.ContextWrapper] around [base], and this is a
 * correctness requirement rather than a style one.** This used to return
 * `base.createConfigurationContext(configuration)`, which hands back a fresh detached
 * `ContextImpl` with no wrapper chain at all. [ProvideAppLocale] then publishes that as
 * `LocalContext`, and everything downstream that needs to reach the Activity through
 * `ContextWrapper.baseContext` stops being able to — most importantly `hiltViewModel()`, which
 * walks exactly that chain and threw `IllegalStateException: Expected an activity context for
 * creating a HiltViewModelFactory`. Every screen in this app resolves a view model that way, so
 * picking any language other than the system one crashed the app on launch, on every API level
 * where this function does anything at all (below 33; above it [ProvideAppLocale] is a
 * pass-through, which is the only reason this shipped unnoticed).
 *
 * [LocalizedContext] below is a `ContextWrapper` around [base], so [base] stays reachable, and it
 * serves resources built from the overridden configuration, so the strings actually change.
 * `LocalizedContextChainInstrumentedTest` and `LanguageOverrideComposeTest` assert one half each.
 */
fun localizedContext(base: Context, tag: String?): Context {
    if (tag == null) return base
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
    return LocalizedContext(base, configuration)
}

/**
 * Wraps [base] — the Activity — and serves resources from a configuration context built off it.
 *
 * **Both halves are load-bearing, and each one alone is a bug this file has already shipped.**
 * Returning `base.createConfigurationContext(configuration)` directly gets the resources right and
 * the chain wrong: that is a detached `ContextImpl`, so `hiltViewModel()` — which reaches the
 * Activity by walking `ContextWrapper.baseContext` — threw `IllegalStateException: Expected an
 * activity context`, crashing every screen in the app whenever a language was chosen. Wrapping the
 * Activity in a `ContextThemeWrapper` with `applyOverrideConfiguration` gets the chain right and
 * the resources wrong: no crash, but `stringResource` keeps resolving in the device language, so
 * the override silently does nothing. `LanguageOverrideComposeTest` catches the second failure and
 * `LocalizedContextChainInstrumentedTest` the first; a change here has to keep both green.
 *
 * The resources are resolved once at construction rather than per call: [ProvideAppLocale]
 * `remember`s this object against base and language, so building it repeatedly would be waste, and
 * a `Resources` created per `getResources()` call would defeat Compose's own caching downstream.
 */
private class LocalizedContext(base: Context, configuration: Configuration) : ContextWrapper(base) {
    private val localized: Resources = base.createConfigurationContext(configuration).resources

    override fun getResources(): Resources = localized
}

/**
 * design.md's Compose-root override (Findings A/B). On API 33+ the platform's own `LocaleManager`
 * override is already applied before this composable ever runs, and [AppLanguage.SystemDefault]
 * has nothing to override either way, so both cases are a pass-through.
 *
 * Below 33 with an explicit [language], provides **exactly** [LocalContext] and
 * [LocalConfiguration] and nothing else:
 * - [LocalContext] is *[remember]*ed against [base]/[language] so it is not rebuilt on every
 *   recomposition.
 * - `LocalResources` MUST NOT be provided (Finding A) — it is a *computed* local
 *   (`compositionLocalWithComputedDefaultOf`) that recomputes from these exact two locals at every
 *   `stringResource` read site; providing it would pin a stale `Resources` instead of letting it
 *   recompute.
 * - `LocalLocale`/`LocalLocaleList` CANNOT be provided (Finding B) — their backing local is
 *   private to compose-ui and is fed from the Activity's own configuration, not from composition.
 */
@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || language.tag == null) {
        content()
        return
    }
    val wrapped = remember(base, language) { localizedContext(base, language.tag) }
    CompositionLocalProvider(
        LocalContext provides wrapped,
        LocalConfiguration provides wrapped.resources.configuration,
        content = content,
    )
}
