package com.jjrapps.constanza.habit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard behind `openspec/config.yaml`'s `compose-test-db-teardown-race`: no instrumented test
 * may build one of this app's ViewModels by bare constructor.
 *
 * **The defect.** Every ViewModel in [GUARDED_VIEW_MODELS] that exposes `stateIn(viewModelScope,
 * SharingStarted.Eagerly, …)` starts collecting a Room `Flow` the moment it is constructed. An
 * instrumented test builds one by bare constructor rather than through a `ViewModelProvider`, so
 * nothing ever calls `onCleared` and nothing ever cancels `viewModelScope`. When
 * `HabitRepositoryTestFixture.close()` shuts the in-memory database, that collector's next query
 * finds a closed `SQLiteConnectionPool`, and the throw surfaces **asynchronously** in the shared
 * instrumentation process — attributed to whichever test is running at that moment, and sometimes
 * killing the process. It has fired for real on the matrix, in a class that had nothing to do with
 * it.
 *
 * The fix is ordering: cancel every scope, then close the database.
 * `HabitRepositoryTestFixture.close` owns that ordering now, and a ViewModel only reaches it by
 * going through a fixture factory or `HabitRepositoryTestFixture.register`. This test is what makes
 * that compulsory rather than customary — the same division of labour `ControlStrokeCallSiteTest`
 * already draws between a rule and a call site that never opts in.
 *
 * **Why a text-scanning unit test rather than a detekt rule, measured rather than assumed.** A
 * `ForbiddenMethodCall` ban needs type resolution, and :app has exactly one type-resolved detekt
 * task: `detektMain`, registered by hand in `app/build.gradle.kts` with
 * `setSource(files("src/main/kotlin"))`. Main sources only. The plain `detekt` task the plugin
 * registers is PSI-only — detekt 1.23.8 hooks no AGP 9 variant API, as that build file's own
 * comment records — so it resolves nothing. A ban on these constructors would therefore not fire in
 * `androidTest` at all. Covering it properly would mean registering a third type-resolved detekt
 * task wired to `debugAndroidTestCompileClasspath`, with the `android-classes-jar` view and
 * `android.jar` that `detektMain` needed. That is the considered alternative; this test is the
 * lighter guard, and it reuses a precedent this repository already has.
 *
 * **What it cannot see.** It reads text. A ViewModel reached through a `typealias`, reflection, or
 * a helper in another module slips past, and so does one constructed into a local and registered on
 * a later line (see [findOffenders] — deliberately the conservative direction). It says nothing
 * about whether the fixture's own teardown works, nor about a ViewModel the fixture is never told
 * about by some route this scan does not recognise. It also does not cover the `e2e` package, which
 * needs no cover: `CoreFlowE2ETest` and `TodayAddHabitE2ETest` construct no ViewModels themselves —
 * the Activity does, through Hilt, so `ActivityScenario.close()` clears the store — and
 * `CoreFlowTestFixture` opens its own separate `Room.databaseBuilder` handle rather than Hilt's
 * singleton, so its `close()` never shuts the database those ViewModels are reading.
 */
class ViewModelTeardownCallSiteTest {

    @Test
    fun `no instrumented test builds a guarded ViewModel by bare constructor`() {
        val offenders = androidTestSources()
            .filterNot { source -> source.name in FACTORY_FILES }
            .flatMap { source -> findOffenders(source.readText(), source.name) }
        assertTrue(
            actual = offenders.isEmpty(),
            message = "These bare ViewModel constructors leak an eager `stateIn` collector past " +
                "`fixture.close()`. The collector then queries a closed database, and the throw " +
                "lands asynchronously in the shared instrumentation process, where it is blamed on " +
                "whichever unrelated test is running at the time. Build it through the fixture " +
                "instead — `fixture.todayViewModel()`, `fixture.habitListViewModel()` or " +
                "`fixture.habitEditorViewModel()` — so its scope is cancelled before the database " +
                "closes; for a ViewModel with no factory yet, wrap the construction directly in " +
                "`fixture.register(…)` or add a factory beside the others:" +
                offenders.joinToString(separator = "") { offender -> "\n  - $offender" },
        )
    }

    /**
     * A scan that quietly reads nothing passes the test above, which would be a guard in name only
     * — and "a guard that quietly does nothing" is the same defect class one level up. So the
     * scan's own reach is asserted: the tree must be found, hold a plausible number of files, and
     * actually contain the fixture-factory call sites known to exist today.
     */
    @Test
    fun `the scan reaches the source tree it is meant to guard`() {
        val sources = androidTestSources()
        assertTrue(
            actual = sources.size >= MINIMUM_EXPECTED_SOURCE_FILES,
            message = "Scanned only ${sources.size} Kotlin files under ${androidTestSourceDirectory()}, " +
                "which cannot be the whole of :app's instrumented suite. The guard is reading the " +
                "wrong directory and is passing on nothing.",
        )
        val factoryCallSites = sources.sumOf { source ->
            FACTORY_CALL.findAll(blankOutCommentsAndStrings(source.readText())).count()
        }
        assertTrue(
            actual = factoryCallSites >= MINIMUM_EXPECTED_FACTORY_CALL_SITES,
            message = "Found $factoryCallSites fixture-factory call sites, but the suite has had at " +
                "least $MINIMUM_EXPECTED_FACTORY_CALL_SITES since fix/compose-teardown-race. Either " +
                "they were removed — in which case lower this floor deliberately — or the scanner " +
                "has stopped recognising call sites and is passing on nothing.",
        )
    }

    /**
     * Pins [findOffenders]' two judgement calls against inline sources rather than the real tree, so
     * neither can rot silently as the suite changes underneath it.
     *
     * The `register(…)` exemption is the delicate one: without it this guard would fail on the very
     * call shape it tells people to write, which is worse than having no guard at all.
     */
    @Test
    fun `the offender rule exempts register and ignores comments`() {
        assertEquals(
            expected = listOf("Sample.kt:1 TodayViewModel(…)"),
            actual = findOffenders("val vm = TodayViewModel(repo)", "Sample.kt"),
        )
        assertEquals(
            expected = emptyList<String>(),
            actual = findOffenders("fixture.register(ProgressViewModel(repo))", "Sample.kt"),
        )
        assertEquals(
            expected = emptyList<String>(),
            actual = findOffenders("// never write ProgressViewModel(repo) here", "Sample.kt"),
            message = "A `//` comment naming the constructor is prose, not a call site.",
        )
        assertEquals(
            expected = emptyList<String>(),
            actual = findOffenders("/** Prefer the factory over ProgressViewModel(repo). */", "Sample.kt"),
            message = "KDoc discussing these constructor shapes — as this very file's does — is prose.",
        )
    }

    // ----------------------------------------------------------------------------------------
    // Scanning
    // ----------------------------------------------------------------------------------------

    /**
     * Every bare construction of a [GUARDED_VIEW_MODELS] member in [source], as `file:line Name(…)`.
     *
     * Comments and string literals are blanked first, so the KDoc in this file and in the fixture —
     * which names these constructors repeatedly — is not read as code, and so a `register(` inside a
     * comment cannot launder a real offender.
     *
     * A match is exempt only when the non-whitespace text immediately before it ends with
     * `register(`, meaning the construction is the *direct* argument to
     * `HabitRepositoryTestFixture.register`. A ViewModel stashed in a local and registered on a
     * later line is still reported. That is deliberately the conservative direction: it can produce
     * a false alarm the author clears by inlining the call, never a missed leak.
     */
    private fun findOffenders(source: String, label: String): List<String> {
        val code = blankOutCommentsAndStrings(source)
        return CONSTRUCTOR_CALL.findAll(code)
            .filterNot { match -> isDirectRegisterArgument(code, match.range.first) }
            .map { match -> "$label:${lineAt(code, match.range.first)} ${match.groupValues[1]}(…)" }
            .toList()
    }

    private fun isDirectRegisterArgument(code: String, matchStart: Int): Boolean =
        code.substring(0, matchStart).trimEnd().endsWith("register(")

    private fun lineAt(code: String, index: Int): Int =
        code.substring(0, index).count { char -> char == '\n' } + 1

    private fun androidTestSourceDirectory(): File {
        val configured = System.getProperty(ANDROID_TEST_SOURCE_DIR_PROPERTY)
        val directory = if (configured != null) File(configured) else File("src/androidTest/kotlin")
        assertTrue(
            actual = directory.isDirectory,
            message = "Cannot find :app's instrumented sources at ${directory.absolutePath}. The " +
                "guard reads them as text, so it needs the real directory; app/build.gradle.kts is " +
                "meant to hand it over as the `$ANDROID_TEST_SOURCE_DIR_PROPERTY` system property.",
        )
        return directory
    }

    private fun androidTestSources(): List<File> = androidTestSourceDirectory()
        .walkTopDown()
        .filter { file -> file.isFile && file.extension == "kt" }
        .toList()

    private companion object {

        /**
         * Every ViewModel in `app/src/main/kotlin`, not only the three an instrumented test builds
         * today. Five of them collect eagerly right now — `TodayViewModel`, `HabitListViewModel`,
         * `ProgressViewModel`, `SnoozeSettingsViewModel` and `OnboardingViewModel` all carry a
         * `stateIn(viewModelScope, SharingStarted.Eagerly, …)`, and so does the `internal`
         * `FirstRunGateViewModel` in `MainActivity.kt`, which makes six — so a future test that bare-
         * constructs any of them reproduces this defect exactly. `HabitEditorViewModel` and
         * `DataPortabilityViewModel` are listed without one for the same reason
         * `ControlStrokeCallSiteTest` lists eight controls to cover two: a guard added after the
         * thing it guards has already failed once, and an eager collector is one refactor away.
         *
         * Only `TodayViewModel`, `HabitListViewModel` and `HabitEditorViewModel` have fixture
         * factories, because only they are built here today. The others are reached through
         * `HabitRepositoryTestFixture.register` until one earns a factory of its own.
         */
        val GUARDED_VIEW_MODELS = setOf(
            "TodayViewModel",
            "HabitListViewModel",
            "HabitEditorViewModel",
            "ProgressViewModel",
            "OnboardingViewModel",
            "SnoozeSettingsViewModel",
            "DataPortabilityViewModel",
            "FirstRunGateViewModel",
        )

        /** The two files allowed to name these constructors: the fixture and the Today factory. */
        val FACTORY_FILES = setOf("HabitRepositoryTestFixture.kt", "TodayViewModelTestFactory.kt")

        val CONSTRUCTOR_CALL =
            Regex("""(?<![A-Za-z0-9_.])(${GUARDED_VIEW_MODELS.joinToString(separator = "|")})\s*\(""")

        /** Fixture-factory call sites, counted to prove the scan reaches real code. */
        val FACTORY_CALL = Regex("""\.(todayViewModel|habitListViewModel|habitEditorViewModel)\s*\(""")

        /** Set by app/build.gradle.kts; the guard reads sources as text, so it needs a real path. */
        const val ANDROID_TEST_SOURCE_DIR_PROPERTY = "constanza.androidTestSourceDir"

        /** 43 Kotlin files under this root when the guard was written; a floor, not a count. */
        const val MINIMUM_EXPECTED_SOURCE_FILES = 25

        /** 15 call sites migrated by fix/compose-teardown-race; a floor, not a count. */
        const val MINIMUM_EXPECTED_FACTORY_CALL_SITES = 12

        /**
         * Replaces every comment and string literal with spaces, preserving length so line numbers
         * survive. Handles nested block comments, raw strings and backtick identifiers, all of which
         * Kotlin allows and any of which could otherwise be read as a call.
         *
         * Copied from `ControlStrokeCallSiteTest` rather than shared. The two guards live in
         * different packages of the same source set and sharing would mean a third production-shaped
         * file existing only for two tests; a duplicated scanner that each test pins with its own
         * cases is the cheaper trade while there are two of them. A third guard is the point to
         * revisit that.
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
