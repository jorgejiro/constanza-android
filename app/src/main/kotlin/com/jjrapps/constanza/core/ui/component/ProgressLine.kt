package com.jjrapps.constanza.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.jjrapps.constanza.core.ui.theme.ConstanzaColors
import com.jjrapps.constanza.core.ui.theme.Dimens

/**
 * Graphite redesign: a 4dp rounded line, [fraction] of it in the text colour over a dark track
 * ([ConstanzaColors.SurfaceSelected]). Exposes its value as progress-bar semantics, so TalkBack reads
 * it as progress rather than skipping a box. Same look as Today's own day-progress line
 * (`tracking.TodayHeader`), shared here for the Progress screen's compliance bar.
 */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val clamped = fraction.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(Dimens.ProgressBar / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.ProgressBar)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) }
            .background(ConstanzaColors.SurfaceSelected, shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .background(ConstanzaColors.OnBackground, shape),
        )
    }
}
