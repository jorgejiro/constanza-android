package com.jjrapps.constanza.localization

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * app-localization: `AppLocaleInstrumentedTest`'s two assertions are exactly this contract.
 * Returns [base] unchanged when [tag] is `null` (`AppLanguage.SystemDefault`) — there is nothing
 * to override. `createConfigurationContext` is stubbed out under AGP's mockable-jar unit-test
 * path (the same trap already documented at `NotificationPoster.kt`), so this is instrumented-only
 * to exercise for real.
 */
fun localizedContext(base: Context, tag: String?): Context {
    if (tag == null) return base
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
    return base.createConfigurationContext(configuration)
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
