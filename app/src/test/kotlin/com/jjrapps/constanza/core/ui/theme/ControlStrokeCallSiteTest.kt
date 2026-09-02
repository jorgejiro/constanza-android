package com.jjrapps.constanza.core.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The half of the control-stroke guard that detekt cannot express, closing the hole
 * [ConstanzaControlDefaults]' own KDoc names: `ColorContrastTest` asserts the *tones* and the
 * *role bindings*, so it catches a re-tone or a rebinding, but it cannot see a call site that
 * never opts in.
 *
 * **Why detekt is not enough on its own, measured rather than assumed.**
 * `config/detekt/detekt.yml` now bans the Material default border factories through
 * `ForbiddenMethodCall`, and that ban is real — it fires on a planted
 * `border = ButtonDefaults.outlinedButtonBorder(enabled = true)` in any file but
 * `ControlDefaults.kt`. It is also, on its own, blind to the exact defect the ban was filed for. A
 * fourth outlined control added like this:
 *
 * ```
 * OutlinedButton(onClick = {}) { Text("…") }
 * ```
 *
 * calls nothing forbidden. The default border is evaluated inside material3, from
 * `OutlinedButtonTokens.OutlineColor`, so no banned name appears at our call site at all —
 * `:app:detektMain` reports nothing and the control ships invisible. That was probed on exactly
 * that snippet before this test was written, and it is the whole reason this test exists rather
 * than a fourth line in the detekt config.
 *
 * So the two guards divide the work by what each can see: detekt catches a border sourced from the
 * wrong place, and this catches a border never sourced at all.
 *
 * **What it enforces.** Every invocation of a composable in [GUARDED_CONTROLS] anywhere under
 * `app/src/main/kotlin` must pass `border = ConstanzaControlDefaults.…`. That catches the fourth
 * control that forgets, and it equally catches an edit that strips the argument back off one of the
 * three that currently remember.
 *
 * **How [GUARDED_CONTROLS] was chosen.** By reading the resolved tokens in
 * `material3-android-1.4.0-sources.jar`, not by listing everything that looks outlined. A control
 * is guarded when its default border token resolves to `ColorSchemeKeyTokens.OutlineVariant` — the
 * decorative-divider role, a 1.26:1 hairline in this palette where WCAG 2.1 SC 1.4.11 wants 3:1.
 * `OutlinedTextField` is deliberately absent: `OutlinedTextFieldTokens.OutlineColor` resolves to
 * `ColorSchemeKeyTokens.Outline`, the control-stroke role, so its default is already correct here
 * and the five `OutlinedTextField` call sites in `HabitEditorScreen`/`ScheduleEditors` rightly
 * carry no override.
 *
 * **What it cannot see.** It reads source text, so it enforces a convention at the call site rather
 * than a rendered colour. It also cannot notice a material3 upgrade re-pointing a token: if
 * `OutlinedTextFieldTokens.OutlineColor` moved to `OutlineVariant` tomorrow, that control would
 * need adding to [GUARDED_CONTROLS] by hand and nothing here would say so.
 */
class ControlStrokeCallSiteTest {

    @Test
    fun `every outlined control takes its border from ConstanzaControlDefaults`() {
        val offenders = mainSources().flatMap { source ->
            source.callsTo(GUARDED_CONTROLS)
                .filterNot { call -> call.arguments.any(::isConstanzaBorderArgument) }
                .map { call -> "${source.file.name}:${call.line} ${call.callee}(…)" }
        }
        assertTrue(
            actual = offenders.isEmpty(),
            message = "These outlined controls take Material's default border, which resolves to " +
                "`outlineVariant` — the decorative-divider role, 1.26:1 against this palette's " +
                "background, where WCAG 2.1 SC 1.4.11 wants 3:1. They will be invisible on screen. " +
                "Pass `border = ConstanzaControlDefaults.…`; if this control has no factory there " +
                "yet, add one rather than inlining a colour, so the next call site inherits it:" +
                offenders.joinToString(separator = "") { offender -> "\n  - $offender" },
        )
    }

    /**
     * A scan that quietly reads nothing passes the test above, which would be a guard in name only
     * — the exact failure mode this whole test exists to prevent, one level up. So the scan's own
     * reach is asserted: the source tree must be found, it must hold a plausible number of files,
     * and the guarded call sites known to exist today must actually be seen.
     */
    @Test
    fun `the scan reaches the source tree it is meant to guard`() {
        val sources = mainSources()
        assertTrue(
            actual = sources.size >= MINIMUM_EXPECTED_SOURCE_FILES,
            message = "Scanned only ${sources.size} Kotlin files under ${mainSourceDirectory()}, " +
                "which cannot be the whole of :app. The guard is reading the wrong directory and " +
                "is passing on nothing.",
        )
        val guardedCallSites = sources.sumOf { source -> source.callsTo(GUARDED_CONTROLS).size }
        assertTrue(
            actual = guardedCallSites >= MINIMUM_EXPECTED_GUARDED_CALL_SITES,
            message = "Found $guardedCallSites call sites to $GUARDED_CONTROLS, but the app has had " +
                "at least $MINIMUM_EXPECTED_GUARDED_CALL_SITES since fix/control-contrast (two " +
                "stepper buttons and a day-of-week chip in ScheduleEditors.kt). Either they were " +
                "removed — in which case lower this floor deliberately — or the scanner has stopped " +
                "recognising call sites and is passing on nothing.",
        )
    }

    // ----------------------------------------------------------------------------------------
    // Scanning
    // ----------------------------------------------------------------------------------------

    private fun mainSourceDirectory(): File {
        val configured = System.getProperty(MAIN_SOURCE_DIR_PROPERTY)
        val directory = if (configured != null) File(configured) else File("src/main/kotlin")
        assertTrue(
            actual = directory.isDirectory,
            message = "Cannot find :app's main sources at ${directory.absolutePath}. The guard " +
                "reads them as text, so it needs the real directory; app/build.gradle.kts is " +
                "meant to hand it over as the `$MAIN_SOURCE_DIR_PROPERTY` system property.",
        )
        return directory
    }

    private fun mainSources(): List<KotlinSource> = mainSourceDirectory()
        .walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" }
        .map { file -> KotlinSource(file, blankOutCommentsAndStrings(file.readText())) }
        .toList()

    /**
     * One Kotlin file with its comments and string literals blanked out.
     *
     * Blanked rather than deleted, so every remaining character keeps its original offset and a
     * finding can still name the line it came from. Without this step the KDoc in
     * `ControlDefaults.kt`, which discusses these very call shapes by name at length, would be read
     * as code.
     */
    private class KotlinSource(val file: File, private val code: String) {

        fun callsTo(callees: Set<String>): List<Call> {
            val pattern = Regex("""(?<![A-Za-z0-9_.])(${callees.joinToString(separator = "|")})\s*\(""")
            return pattern.findAll(code).mapNotNull { match ->
                val arguments = argumentsAt(match.range.last) ?: return@mapNotNull null
                Call(
                    callee = match.groupValues[1],
                    line = lineAt(match.range.first),
                    arguments = splitTopLevel(arguments),
                )
            }.toList()
        }

        /** The text between [openParen] and its matching close paren, or null if it never closes. */
        private fun argumentsAt(openParen: Int): String? {
            var depth = 0
            for (index in openParen until code.length) {
                when (code[index]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return code.substring(openParen + 1, index)
                    }
                }
            }
            return null
        }

        private fun lineAt(index: Int): Int = code.substring(0, index).count { char -> char == '\n' } + 1
    }

    private data class Call(val callee: String, val line: Int, val arguments: List<String>)

    private companion object {

        /**
         * Composables whose default border resolves to `ColorSchemeKeyTokens.OutlineVariant` in
         * material3 1.4.0, read from the resolved artifact's own token sources:
         * `OutlinedButtonTokens.OutlineColor`, `OutlinedIconButtonTokens.OutlineColor`,
         * `OutlinedCardTokens.OutlineColor`, `FilterChipTokens.FlatUnselectedOutlineColor`,
         * `AssistChipTokens.FlatOutlineColor`, `SuggestionChipTokens.FlatOutlineColor` and
         * `InputChipTokens.UnselectedOutlineColor`.
         *
         * Only two of these are used today. The other six are listed anyway, which is the whole
         * point: a guard added after the control it guards has already failed once.
         */
        val GUARDED_CONTROLS = setOf(
            "OutlinedButton",
            "OutlinedIconButton",
            "OutlinedIconToggleButton",
            "OutlinedCard",
            "FilterChip",
            "AssistChip",
            "SuggestionChip",
            "InputChip",
        )

        /** Set by app/build.gradle.kts; the guard reads sources as text, so it needs a real path. */
        const val MAIN_SOURCE_DIR_PROPERTY = "constanza.mainSourceDir"

        /** :app held 72 Kotlin files under this root when the guard was written; a floor, not a count. */
        const val MINIMUM_EXPECTED_SOURCE_FILES = 40

        /** Two `NumberStepper` buttons and one `DayOfWeekPicker` chip, all in ScheduleEditors.kt. */
        const val MINIMUM_EXPECTED_GUARDED_CALL_SITES = 3

        val CONSTANZA_BORDER_ARGUMENT = Regex("""^\s*border\s*=\s*ConstanzaControlDefaults\s*\.""")

        fun isConstanzaBorderArgument(argument: String): Boolean =
            CONSTANZA_BORDER_ARGUMENT.containsMatchIn(argument)

        /**
         * Splits an argument list on its top-level commas.
         *
         * Top-level matters: without it a `border = ConstanzaControlDefaults.…` buried in a nested
         * call or a content lambda would satisfy the enclosing control, which is the one shape a
         * naive substring search gets wrong in the direction that lets a defect through.
         */
        fun splitTopLevel(arguments: String): List<String> {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var depth = 0
            arguments.forEach { char ->
                when {
                    char in "([{" -> depth++
                    char in ")]}" -> depth--
                    char == ',' && depth == 0 -> {
                        parts += current.toString()
                        current.clear()
                        return@forEach
                    }
                }
                current.append(char)
            }
            parts += current.toString()
            return parts
        }

        /**
         * Replaces every comment and string literal with spaces, preserving length so line numbers
         * survive. Handles nested block comments, raw strings and backtick identifiers, all of which
         * Kotlin allows and any of which could otherwise be read as a call.
         */
        fun blankOutCommentsAndStrings(source: String): String {
            val out = StringBuilder(source)
            var index = 0
            while (index < source.length) {
                index = when {
                    source.startsWith("//", index) -> blank(out, source, index, source.lineEndAfter(index))
                    source.startsWith("/*", index) -> blank(out, source, index, source.blockCommentEndAfter(index))
                    source.startsWith("\"\"\"", index) -> blank(out, source, index, source.rawStringEndAfter(index))
                    source[index] in DELIMITERS ->
                        blank(out, source, index, source.delimitedEndAfter(index, source[index]))
                    else -> index + 1
                }
            }
            return out.toString()
        }

        /** Quote, character-literal and backtick-identifier delimiters, all closed on the same line. */
        const val DELIMITERS = "\"'`"

        /** Blanks `[from, until)`, keeping newlines so line numbering is unaffected. */
        fun blank(out: StringBuilder, source: String, from: Int, until: Int): Int {
            for (index in from until until) {
                if (source[index] != '\n') out[index] = ' '
            }
            return until
        }

        fun String.lineEndAfter(start: Int): Int = indexOf('\n', start).takeIf { end -> end >= 0 } ?: length

        /** Kotlin block comments nest, so this counts depth rather than stopping at the first close. */
        fun String.blockCommentEndAfter(start: Int): Int {
            var depth = 0
            var index = start
            while (index < length) {
                when {
                    startsWith("/*", index) -> {
                        depth++
                        index += 2
                    }
                    startsWith("*/", index) -> {
                        depth--
                        index += 2
                        if (depth == 0) return index
                    }
                    else -> index++
                }
            }
            return length
        }

        fun String.rawStringEndAfter(start: Int): Int {
            val close = indexOf("\"\"\"", start + 3)
            return if (close < 0) length else close + 3
        }

        fun String.delimitedEndAfter(start: Int, delimiter: Char): Int {
            var index = start + 1
            while (index < length && this[index] != '\n') {
                when (this[index]) {
                    '\\' -> index += 2
                    delimiter -> return index + 1
                    else -> index++
                }
            }
            return minOf(index, length)
        }
    }
}
