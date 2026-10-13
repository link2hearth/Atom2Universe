package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test

class HamletChronicleTest {
    @Test fun `journal is bounded newest first and keeps typed translatable data`() {
        val g = HamletGame({ 1000L })
        repeat(30) { g.chronicle.record(HamletChronicle.Kind.LEVEL, amount = it.toLong()) }
        assertEquals(24, g.chronicle.rows.size)
        assertEquals(29L, g.chronicle.rows.first().amount)
        assertEquals(6L, g.chronicle.rows.last().amount)
        assertEquals(HamletChronicle.Kind.LEVEL, g.chronicle.rows.first().kind)
    }

    @Test fun `restore bounds imported fields and journal operations do not grant rewards`() {
        val g = HamletGame({ 1000L }); g.coins = 10; g.gems = 3; g.xp = 4
        g.chronicle.restore(List(100) { HamletChronicle.Entry(HamletChronicle.Kind.REALM, -20, "x".repeat(300), "y".repeat(300), -50) })
        assertEquals(24, g.chronicle.rows.size)
        val row = g.chronicle.rows.first(); assertEquals(0L, row.at); assertEquals(0L, row.amount)
        assertEquals(80, row.subject.length); assertEquals(80, row.actor.length)
        g.chronicle.record(HamletChronicle.Kind.GIFT)
        assertEquals(10L, g.coins); assertEquals(3, g.gems); assertEquals(4L, g.xp)
    }
}
