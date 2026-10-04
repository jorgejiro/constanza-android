package com.jjrapps.constanza.core.data.migration

import kotlin.test.Test
import kotlin.test.assertEquals

/** The retired `BLUE_GREY` preset (Blue Grey 400), literal rather than `HabitColor.BLUE_GREY`
 *  because that member no longer exists — the same class of literal [HabitColorRetireRemap]'s own
 *  KDoc requires for a value one side of a frozen map used to mean. */
private const val RETIRED_BLUE_GREY = 0xFF849FAC.toInt()

/** The 21-preset palette's `CYAN` and `TEAL`, literal because the graphite palette (schema
 *  version 8) retired both; this map's meaning is frozen at schema version 6. */
private const val SCHEMA_V6_CYAN = 0xFF00ABBD.toInt()
private const val SCHEMA_V6_TEAL = 0xFF00AE9D.toInt()

/**
 * [HabitColorRetireRemap] is the "22-preset legible-band palette" -> "21-preset legible-band
 * palette" analogue of [HabitColorRetoneRemap]'s v4->v5 map, so this test follows that file's shape:
 * pin the map's one entry, pin the fall-through behaviour for a schema v6 preset and for a genuine
 * custom colour, and pin the mapped value as a literal.
 */
class HabitColorRetireRemapTest {

    @Test
    fun `the retired blue grey preset maps to cyan specifically`() {
        assertEquals(SCHEMA_V6_CYAN, HabitColorRetireRemap.normalize(RETIRED_BLUE_GREY))
    }

    @Test
    fun `the map holds exactly one entry`() {
        assertEquals(1, HabitColorRetireRemap.LEGACY_TO_CURRENT.size, "one retirement so far: BLUE_GREY")
    }

    /** Renamed from "…is a current preset": the graphite palette retired `CYAN` too, so the frozen
     *  map is pinned to the value it has always written rather than to today's palette. */
    @Test
    fun `the mapped value is the schema v6 cyan`() {
        assertEquals(listOf(SCHEMA_V6_CYAN), HabitColorRetireRemap.LEGACY_TO_CURRENT.values.toList())
    }

    /** Every schema v6 preset carries no entry — it did not move value, so it must pass through
     *  [HabitColorRetireRemap.normalize] unchanged via the [clampToLegacyHabitBand] fallthrough. */
    @Test
    fun `a current preset passes through unchanged`() {
        val current = SCHEMA_V6_CYAN

        assertEquals(current, HabitColorRetireRemap.normalize(current))
    }

    /** A colour that was never any version of this palette also falls through to the clamp — if it
     *  is already legible it survives untouched, exactly as [clampToLegacyHabitBand] documents. */
    @Test
    fun `a custom colour already inside the band passes through unchanged`() {
        val alreadyLegible = SCHEMA_V6_TEAL

        assertEquals(alreadyLegible, HabitColorRetireRemap.normalize(alreadyLegible))
    }
}
