package com.jjrapps.constanza.localization

/**
 * app-localization: Three-State Language Override. [SystemDefault] is deliberately not a third
 * persisted language (design.md D7) — its [tag] is `null`, and a `null` persisted tag is
 * observationally identical to no override ever having been set, so a later device-locale change
 * still takes effect.
 */
enum class AppLanguage(val tag: String?) {
    SystemDefault(null),
    English("en"),
    Spanish("es"),
    ;

    companion object {
        /** The inverse of [tag]. An unrecognised tag (a language this app does not support) falls
         *  back to [SystemDefault] rather than throwing — the same universal-fallback spirit as the
         *  Supported Language Set requirement, applied to a stored value instead of a device locale. */
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SystemDefault
    }
}
