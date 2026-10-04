package com.jjrapps.constanza.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jjrapps.constanza.R

/**
 * Geist, bundled as one variable TTF (`res/font/geist_variable.ttf`, SIL OFL 1.1 — licence text in
 * `third_party/geist/OFL.txt`). Bundled rather than a Google downloadable font because the F-Droid
 * build has no Play Services to download it with.
 *
 * One file serves every weight: each [Font] entry points at the same resource and pins its `wght`
 * axis through [FontVariation.Settings], which Android honours from API 26 (minSdk is 31). Only the
 * three weights the graphite design uses are declared.
 */
internal val Geist = FontFamily(
    geistWeight(FontWeight.Normal),
    geistWeight(FontWeight.Medium),
    geistWeight(FontWeight.SemiBold),
)

private fun geistWeight(weight: FontWeight): Font = Font(
    resId = R.font.geist_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val baseline = Typography()

private fun TextStyle.geist(): TextStyle = copy(fontFamily = Geist)

/**
 * The app's type scale: Geist on every role, with the roles the graphite design names tuned to it.
 *
 * - `headlineMedium` — the Today title: 30sp / 600, tracking -0.02em.
 * - `titleLarge` — screen titles (`TopAppBar`): 22sp / 600.
 * - `bodyLarge` — habit names (`ListItem` headline): 17sp / 500.
 * - `bodyMedium` — detail and supporting text: 14sp / 400.
 * - `labelMedium` — section labels: 12sp / 500, tracking 0.08em. The uppercase transform is applied
 *   at the call site (`SectionHeader`), not here, because a `TextStyle` cannot carry it.
 *
 * Every other role keeps Material 3's baseline size and only gains the family, except
 * `headlineSmall`, which takes the design's 600 heading weight.
 */
internal val ConstanzaTypography = Typography(
    displayLarge = baseline.displayLarge.geist(),
    displayMedium = baseline.displayMedium.geist(),
    displaySmall = baseline.displaySmall.geist(),
    headlineLarge = baseline.headlineLarge.geist(),
    headlineMedium = baseline.headlineMedium.geist().copy(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = baseline.headlineSmall.geist().copy(fontWeight = FontWeight.SemiBold),
    titleLarge = baseline.titleLarge.geist().copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = baseline.titleMedium.geist(),
    titleSmall = baseline.titleSmall.geist(),
    bodyLarge = baseline.bodyLarge.geist().copy(
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyMedium = baseline.bodyMedium.geist().copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = baseline.bodySmall.geist(),
    labelLarge = baseline.labelLarge.geist(),
    labelMedium = baseline.labelMedium.geist().copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.08.em,
    ),
    labelSmall = baseline.labelSmall.geist(),
)
