package com.jjrapps.constanza.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A throwaway prototype of the one-line Today row, for looking at only. Nothing here is wired into
 * `app/src/main`, and nothing in `app/src/main` imports it.
 *
 * It exists because the shipped three-line row was rejected on sight: the status glyph sat on its
 * own nearly-empty line, bottom-left, twelve characters away from the name it described. The shape
 * below is the owner's: state joins the name on the name's own line, and the row costs one line
 * (two when the name wraps) instead of three.
 *
 * Colours are duplicated as literals rather than imported because `ConstanzaColors` is `internal`
 * and this project's AGP setup does not reliably expose `internal` from `app/src/main` to
 * `app/src/androidTest`. A prototype holding stale copies is a cost worth paying for a file whose
 * whole life is one screenshot; production code must never do this.
 */

private val Background = Color(0xFF110B06)
private val OnBackground = Color(0xFFEFEAE6)
private val OnBackgroundMuted = Color(0xFF887E76)
private val Divider = Color(0xFF28231E)
private val ChromeInteractive = Color(0xFFC4BCB6)
private val StatusCompleted = Color(0xFF5FA867)
private val StatusMissed = Color(0xFFE07B74)

internal enum class ProtoState { PENDING, DONE, MISSED, SKIPPED }

/** How a Sí/No answer control says it is tappable at all — the open question this prototype's
 *  second round exists to answer. Bare coloured text risks reading as a status *label* rather than
 *  a control, which is worse than the neutral text it replaced. */
internal enum class ChipStyle { PLAIN, UNDERLINE, OUTLINED, FILLED }

internal data class ProtoSlot(val minute: String?, val state: ProtoState)

internal data class ProtoRow(
    val name: String,
    val color: Color,
    val slots: List<ProtoSlot>,
)

internal const val PROTO_TAG = "today-one-line-proto"

@Composable
internal fun TodayOneLinePrototype(
    later: List<ProtoRow>,
    answered: List<ProtoRow>,
    colouredAnswers: Boolean = false,
    chipStyle: ChipStyle = ChipStyle.PLAIN,
) {
    Column(Modifier.fillMaxWidth().background(Background)) {
        TopBar()
        DateBar()
        Section("M Á S   T A R D E", later, colouredAnswers, chipStyle)
        Spacer(Modifier.size(20.dp))
        Section("C O N T E S T A D O S", answered, colouredAnswers, chipStyle)
    }
}

@Composable
private fun TopBar() {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Hoy", color = OnBackground, fontSize = 22.sp)
        Spacer(Modifier.weight(1f))
        Text("Hábitos", color = ChromeInteractive, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Transparent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DateBar() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("7 sept 2026", color = OnBackground, fontSize = 16.sp)
    }
}

@Composable
private fun Section(title: String, rows: List<ProtoRow>, colouredAnswers: Boolean, chipStyle: ChipStyle) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(title, color = OnBackgroundMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.size(6.dp))
        HorizontalDivider(color = Divider)
        rows.forEach { HabitBlock(it, colouredAnswers, chipStyle) }
    }
}

@Composable
private fun HabitBlock(row: ProtoRow, colouredAnswers: Boolean, chipStyle: ChipStyle) {
    val single = row.slots.size == 1
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name,
                color = row.color,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                modifier = Modifier.weight(1f),
            )
            if (single) {
                Spacer(Modifier.width(8.dp))
                Trailing(row.slots.single(), colouredAnswers, chipStyle)
            }
        }
        if (!single) {
            row.slots.forEach { slot ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    Trailing(slot, colouredAnswers, chipStyle)
                }
            }
        }
    }
}

/** The whole point: time (only when a habit has several) then the state glyph, both hard right, on
 *  the name's own line. No status word, no "Omitir", no always-visible "Cambiar". */
@Composable
private fun Trailing(slot: ProtoSlot, colouredAnswers: Boolean, chipStyle: ChipStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        slot.minute?.let {
            Text(it, color = OnBackgroundMuted, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
        }
        when (slot.state) {
            ProtoState.PENDING -> {
                AnswerChip("Sí", if (colouredAnswers) StatusCompleted else ChromeInteractive, chipStyle)
                Spacer(Modifier.width(4.dp))
                AnswerChip("No", if (colouredAnswers) StatusMissed else ChromeInteractive, chipStyle)
            }
            ProtoState.DONE -> Icon(Icons.Filled.Check, "Hecho", tint = StatusCompleted, modifier = Modifier.size(20.dp))
            ProtoState.MISSED -> Icon(Icons.Filled.Close, "No hecho", tint = StatusMissed, modifier = Modifier.size(20.dp))
            ProtoState.SKIPPED -> Box(
                Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(12.dp, 2.dp).background(OnBackgroundMuted, RoundedCornerShape(1.dp))) }
        }
    }
}

/** Painted at 40dp but hit at 48dp — the same split `Dimens.Swatch`/`SwatchTouchTarget` already
 *  uses, so a compact control never drops below the minimum touch target. Only the paint is
 *  rendered here; the hit box is invisible in a screenshot. */
@Composable
private fun AnswerChip(label: String, tint: Color, style: ChipStyle) {
    val shape = RoundedCornerShape(14.dp)
    val base = Modifier.size(46.dp, 28.dp)
    val decorated = when (style) {
        ChipStyle.PLAIN, ChipStyle.UNDERLINE -> base
        ChipStyle.OUTLINED -> base.border(BorderStroke(1.dp, tint.copy(alpha = 0.55f)), shape)
        ChipStyle.FILLED -> base.background(tint.copy(alpha = 0.16f), shape)
    }
    Box(decorated, contentAlignment = Alignment.Center) {
        Text(
            label,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = if (style == ChipStyle.UNDERLINE) TextDecoration.Underline else null,
        )
    }
}

@Composable
internal fun protoMaterialShim(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
