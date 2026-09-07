package com.jjrapps.constanza.tracking

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.jjrapps.constanza.core.ui.theme.ConstanzaTheme
import com.jjrapps.constanza.core.ui.theme.HabitColor
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Camera, not assertion — see [TodayRealRenderTest]'s KDoc. This one shoots the throwaway
 *  [TodayOneLinePrototype] rather than the shipped screen, so the owner can compare the proposed
 *  one-line row against the three-line row that ships today before any production code moves. */
class TodayOneLineRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test fun renderPlain() = render(ChipStyle.PLAIN)

    @Test fun renderUnderline() = render(ChipStyle.UNDERLINE)

    @Test fun renderOutlined() = render(ChipStyle.OUTLINED)

    @Test fun renderFilled() = render(ChipStyle.FILLED)

    private fun render(style: ChipStyle) {
        val later = listOf(
            ProtoRow("Cenar antes de las 10 de la noche", c(HabitColor.LILAC), listOf(ProtoSlot(null, ProtoState.PENDING))),
            ProtoRow("Relax a las 23h", c(HabitColor.BLUE), listOf(ProtoSlot(null, ProtoState.PENDING))),
            ProtoRow("Comer sanito, lento y pronto todos los días", c(HabitColor.MINT), listOf(ProtoSlot(null, ProtoState.PENDING))),
        )
        val answered = listOf(
            ProtoRow("Hacer movilidad al sol en la primera hora tras levantarme", c(HabitColor.PINK), listOf(ProtoSlot(null, ProtoState.DONE))),
            ProtoRow("Hacer descansos con movilidad", c(HabitColor.GREEN), listOf(ProtoSlot(null, ProtoState.DONE))),
            ProtoRow("Empezar a preparar la comida antes de las 14h", c(HabitColor.PEACH), listOf(ProtoSlot(null, ProtoState.MISSED))),
            ProtoRow("Estirar la espalda", c(HabitColor.TEAL), listOf(ProtoSlot(null, ProtoState.SKIPPED))),
            ProtoRow("Cenar antes de las 22", c(HabitColor.AMBER), listOf(ProtoSlot(null, ProtoState.DONE))),
            // The minority case: several times a day, so the time is the only thing telling the
            // rows apart and it sits immediately left of the glyph.
            ProtoRow("Beber agua", c(HabitColor.CYAN), listOf(ProtoSlot("11:00", ProtoState.DONE), ProtoSlot("16:00", ProtoState.MISSED))),
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                ConstanzaTheme {
                    Box(Modifier.size(WIDTH_DP.dp, HEIGHT_DP.dp).testTag(PROTO_TAG)) {
                        TodayOneLinePrototype(
                            later = later,
                            answered = answered,
                            colouredAnswers = true,
                            chipStyle = style,
                        )
                    }
                }
            }
        }

        val bitmap = composeTestRule.onNodeWithTag(PROTO_TAG).captureToImage().asAndroidBitmap()
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        File(target.filesDir, "chip-${style.name.lowercase()}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, it)
        }
    }

    private fun c(color: HabitColor) = Color(color.argb)

    private companion object {
        const val DENSITY = 2.625f
        const val WIDTH_DP = 360
        const val HEIGHT_DP = 700
        const val QUALITY = 100
    }
}
