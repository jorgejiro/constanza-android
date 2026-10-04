package com.jjrapps.constanza.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.jjrapps.constanza.core.ui.theme.Dimens

/**
 * Graphite redesign: the screen's one primary action — a full-width 52dp pill, light-filled
 * ([com.jjrapps.constanza.core.ui.theme.ConstanzaColors.ChromeInteractive] through M3 `primary`)
 * with dark ink, flat. The editor's Save and the onboarding primary share it. Disabled keeps
 * Material's own disabled treatment, so a blocked Save still reads as unavailable.
 */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(Dimens.PrimaryButtonHeight),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}
