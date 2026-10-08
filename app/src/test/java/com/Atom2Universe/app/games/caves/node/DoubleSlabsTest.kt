package com.Atom2Universe.app.games.caves.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleSlabsTest {
    private fun def(id: Int, slab: Boolean) = BlockDef(id.toShort(), "b$id", "t", "t", "t", null, null, null, null,
        0, 1f, false, false, false, false, false, 0, "", "", 0f, 1f, slab = slab)

    // Des numéros de dalles volontairement épars : le rang ne dépend que de leur ordre.
    private val defs = listOf(def(1, false), def(2500, true), def(2507, true), def(4100, true))
        .associateBy { it.id }
    private val doubles = DoubleSlabs.definitions(defs)

    @Test fun oneDoubleBlockPerSlabAndAxis() {
        assertEquals(3 * 3, doubles.size)
        assertEquals(doubles.size, doubles.map { it.id }.toSet().size)
        assertTrue(doubles.all { DoubleSlabs.isDouble(it.id) && !it.slab })
    }

    @Test fun standingSlabsKeepTheirAxis() {
        val (id, meta) = DoubleSlabs.combine(4100.toShort(), 2500.toShort(), DoubleSlabs.AXIS_Z)!!
        assertEquals(DoubleSlabs.AXIS_Z, DoubleSlabs.axis(id))
        assertEquals(4100.toShort() to 2500.toShort(), DoubleSlabs.materials(id, meta))
    }

    @Test fun twoDifferentSlabsComeBack() {
        val (id, meta) = DoubleSlabs.combine(2507, 4100)!!
        assertEquals(2507.toShort() to 4100.toShort(), DoubleSlabs.materials(id, meta))
    }

    @Test fun twoIdenticalSlabsStayTwoSlabs() {
        val (id, meta) = DoubleSlabs.combine(2500, 2500)!!
        assertEquals(2500.toShort() to 2500.toShort(), DoubleSlabs.materials(id, meta))
    }

    @Test fun notASlab() {
        assertNull(DoubleSlabs.combine(1, 2500))
        assertFalse(DoubleSlabs.isDouble(1))
        assertNull(DoubleSlabs.materials(1, 0))
    }

    private fun DoubleSlabs.combine(a: Int, b: Int) = combine(a.toShort(), b.toShort())
    private fun DoubleSlabs.materials(id: Int, meta: Int) = materials(id.toShort(), meta.toByte())
}
