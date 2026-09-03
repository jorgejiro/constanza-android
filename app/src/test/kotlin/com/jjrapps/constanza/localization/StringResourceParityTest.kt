package com.jjrapps.constanza.localization

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

private val FORMAT_SPECIFIER_REGEX = Regex("""%(\d+\$)?[a-zA-Z]""")

/** One parsed `strings.xml`: every `<string>` key (with its `translatable` flag) and every
 *  `<plurals>` resource's quantity items, keyed by quantity name (`one`, `other`, ...). */
private class ParsedStringsXml(
    val plainStrings: Map<String, String>,
    val translatableKeys: Set<String>,
    val allKeys: Set<String>,
    val plurals: Map<String, Map<String, String>>,
)

private fun requireSystemProperty(name: String): String =
    requireNotNull(System.getProperty(name)) {
        "System property '$name' is not set. Run this test through Gradle (:app:testDebugUnitTest or " +
            ":app:test), which wires it in app/build.gradle.kts's tasks.withType<Test> block."
    }

private fun parseStringsXml(path: String): ParsedStringsXml {
    val file = File(path)
    require(file.isFile) { "No strings.xml file at $path" }
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    document.documentElement.normalize()

    val plainStrings = mutableMapOf<String, String>()
    val translatableKeys = mutableSetOf<String>()
    val allKeys = mutableSetOf<String>()
    val stringNodes = document.getElementsByTagName("string")
    for (i in 0 until stringNodes.length) {
        val element = stringNodes.item(i) as Element
        val name = element.getAttribute("name")
        allKeys += name
        plainStrings[name] = element.textContent
        if (element.getAttribute("translatable") != "false") {
            translatableKeys += name
        }
    }

    val plurals = mutableMapOf<String, Map<String, String>>()
    val pluralsNodes = document.getElementsByTagName("plurals")
    for (i in 0 until pluralsNodes.length) {
        val pluralsElement = pluralsNodes.item(i) as Element
        val name = pluralsElement.getAttribute("name")
        allKeys += name
        if (pluralsElement.getAttribute("translatable") != "false") {
            translatableKeys += name
        }
        val items = mutableMapOf<String, String>()
        val itemNodes = pluralsElement.getElementsByTagName("item")
        for (j in 0 until itemNodes.length) {
            val itemElement = itemNodes.item(j) as Element
            items[itemElement.getAttribute("quantity")] = itemElement.textContent
        }
        plurals[name] = items
    }

    return ParsedStringsXml(plainStrings, translatableKeys, allKeys, plurals)
}

/** The multiset of positional format specifiers in [value] — e.g. `"Delete %1$s?"` yields
 *  `["%1$s"]`, and `"%1$d%%"` yields `["%1$d"]` because `%%` is a literal percent sign, never a
 *  specifier. Deliberately a list, not a set: duplicate specifiers must still be caught as a count
 *  mismatch, and reordering `%1$s`/`%2$s` must NOT be caught, which is exactly what a multiset
 *  (grouped count) comparison gives and a plain equality-of-sequence comparison would not. */
private fun formatSpecifiersOf(value: String): List<String> =
    FORMAT_SPECIFIER_REGEX.findAll(value.replace("%%", ""))
        .map { it.value }
        .toList()

/**
 * design.md D9 — the load-bearing gate for `specs/app-localization/spec.md`'s
 * `Format-Argument And Plural Integrity Under Translation` requirement, covering all four of its
 * scenarios. Parses `app/src/main/res/values/strings.xml` and
 * `app/src/main/res/values-es/strings.xml` as plain XML on the JVM classpath-independent
 * filesystem — no Android framework, no mockable-jar stubbing traps — and asserts:
 *
 * 1. the format-specifier **multiset** is identical per key across both languages (a multiset,
 *    not a sequence, because Spanish word order may legitimately reorder `%1$s`/`%2$s` — see
 *    `today_slot_change_a11y` — while keeping both positional indices);
 * 2. key parity in both directions, modulo `translatable="false"` (`app_name`);
 * 3. `progress_compliance` keeps its literal `%%` in both languages;
 * 4. the Spanish `habit_delete_dialog_body` plurals resource carries both `one` and `other`, each
 *    with its own `%1$d`.
 */
class StringResourceParityTest {

    private val base = parseStringsXml(requireSystemProperty("constanza.stringsValuesXml"))
    private val es = parseStringsXml(requireSystemProperty("constanza.stringsValuesEsXml"))

    @Test
    fun `every translatable key in the base file has a Spanish counterpart`() {
        val missing = base.translatableKeys - es.allKeys
        assertTrue(missing.isEmpty(), "values-es/strings.xml is missing a translation for: $missing")
    }

    @Test
    fun `no stray key exists in the Spanish file that is absent from the base file`() {
        val stray = es.allKeys - base.allKeys
        assertTrue(stray.isEmpty(), "values-es/strings.xml has keys with no base counterpart: $stray")
    }

    @Test
    fun `format specifier multiset matches per key across both languages`() {
        val mismatches = base.plainStrings.keys.filter { it in es.plainStrings }.mapNotNull { key ->
            val baseSpecifiers = formatSpecifiersOf(base.plainStrings.getValue(key)).groupingBy { it }.eachCount()
            val esSpecifiers = formatSpecifiersOf(es.plainStrings.getValue(key)).groupingBy { it }.eachCount()
            if (baseSpecifiers != esSpecifiers) {
                "$key: base=$baseSpecifiers es=$esSpecifiers"
            } else {
                null
            }
        }
        assertTrue(mismatches.isEmpty(), "Format-specifier multiset mismatch:\n${mismatches.joinToString("\n")}")
    }

    @Test
    fun `progress_compliance keeps its literal percent sign in both languages`() {
        assertTrue(
            base.plainStrings.getValue("progress_compliance").contains("%%"),
            "The base progress_compliance string lost its literal %%",
        )
        assertTrue(
            es.plainStrings.getValue("progress_compliance").contains("%%"),
            "The Spanish progress_compliance string must keep its literal %% unescaped",
        )
    }

    @Test
    fun `Spanish habit_delete_dialog_body plurals carries both one and other with the argument`() {
        val plural = es.plurals["habit_delete_dialog_body"]
            ?: fail("values-es/strings.xml is missing the habit_delete_dialog_body <plurals> resource")
        val one = plural["one"] ?: fail("Spanish habit_delete_dialog_body has no 'one' quantity item")
        val other = plural["other"] ?: fail("Spanish habit_delete_dialog_body has no 'other' quantity item")
        assertTrue(one.contains("%1\$d"), "Spanish 'one' item must carry %1\$d: $one")
        assertTrue(other.contains("%1\$d"), "Spanish 'other' item must carry %1\$d: $other")
    }
}
