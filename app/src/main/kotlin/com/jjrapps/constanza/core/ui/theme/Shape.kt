package com.jjrapps.constanza.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The app's corner-radius scale, tuned to the graphite design: `small` 12dp (text fields, chips),
 * `medium` 16dp (cards, the exact-alarm banner), `large` 18dp (the FAB, which M3 draws with the
 * `large` key). `extraSmall` (menus) and `extraLarge` (dialogs, sheets) keep Material 3's baseline.
 *
 * Note that M3's `OutlinedTextField` reads `extraSmall` by default, so a field that wants the 12dp
 * corner must pass `MaterialTheme.shapes.small` at its call site.
 */
internal val ConstanzaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
)
