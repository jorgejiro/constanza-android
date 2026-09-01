package com.jjrapps.constanza.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

/**
 * The app's type scale. `FontFamily.Default` only — Roboto reads close enough to the Public Sans
 * used in the artboards that a packaged font is not worth the APK weight (Engram #47, point 11).
 *
 * Only the seven roles this codebase actually reads are overridden, each pinned explicitly to
 * `FontFamily.Default` rather than left to Compose's implicit default, so a future font swap has one
 * place to edit. Size, weight, line height and letter spacing are left at the Material 3 baseline —
 * this change is visual-scheme, not type-scale, work.
 */
private val baseline = Typography()

internal val ConstanzaTypography = baseline.copy(
    titleLarge = baseline.titleLarge.copy(fontFamily = FontFamily.Default),
    titleMedium = baseline.titleMedium.copy(fontFamily = FontFamily.Default),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = FontFamily.Default),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = FontFamily.Default),
    bodySmall = baseline.bodySmall.copy(fontFamily = FontFamily.Default),
    labelLarge = baseline.labelLarge.copy(fontFamily = FontFamily.Default),
    labelMedium = baseline.labelMedium.copy(fontFamily = FontFamily.Default),
)
