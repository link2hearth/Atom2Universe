package com.Atom2Universe.app.games.starbridges

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarBridgesGameTest {

    private fun game(size: Int, seed: String) = StarBridgesGame().apply { assertTrue(newGame(size, seed)) }

    /** La figure du générateur doit gagner, et les gestes doivent pouvoir la tracer. */
    @Test
    fun generatorSolutionWins() {
        for (size in StarBridgesGame.ALLOWED_SIZES) repeat(30) { i ->
            val g = game(size, "t$size-$i")
            for (key in g.solution) {
                val (a, b) = g.endsOf(key)
                assertTrue("le trait $key doit pouvoir se poser", g.toggleBridge(a, b))
            }
            assertTrue("grille $size-$i non résolue", g.solved)
        }
    }

    /**
     * Glisser d'une étoile vers une direction doit trouver exactement la voisine que le
     * trait relierait : chaque lien possible se retrouve depuis ses deux bouts.
     */
    @Test
    fun directionFindsEveryEdge() {
        val g = game(8, "dir")
        val byId = g.nodes.associateBy { it.id }
        for (e in g.edges) {
            val a = byId.getValue(e.from); val b = byId.getValue(e.to)
            val dx = Integer.signum(b.x - a.x); val dy = Integer.signum(b.y - a.y)
            assertEquals(b.id, g.neighborInDirection(a.id, dx, dy)?.id)
            assertEquals(a.id, g.neighborInDirection(b.id, -dx, -dy)?.id)
        }
    }

    @Test
    fun crossingIsRefused() {
        // Cherche deux liens possibles qui se croisent, et vérifie que le second est refusé.
        repeat(40) { i ->
            val g = game(7, "x$i")
            for (e1 in g.edges) for (e2 in g.edges) {
                if (e1.key == e2.key) continue
                val g2 = game(7, "x$i")
                assertTrue(g2.toggleBridge(e1.from, e1.to))
                if (!g2.canAddBridge(e2.key)) {
                    assertFalse(g2.toggleBridge(e2.from, e2.to))
                    assertFalse(g2.hasBridge(e2.key))
                    return
                }
            }
        }
    }

    @Test
    fun ignitionCoversEveryLine() {
        val g = game(7, "fire")
        for (key in g.solution) { val (a, b) = g.endsOf(key); g.toggleBridge(a, b) }
        val order = g.ignitionOrder()
        assertEquals(g.solution, order.toSet())
        assertEquals(order.size, order.toSet().size)
    }

    @Test
    fun nameIsStablePerSeed() {
        val a = game(6, "same").nameIndices(32, 28)
        val b = game(6, "same").nameIndices(32, 28)
        assertEquals(a, b)
        assertNotNull(a)
    }

    /** Une constellation écrite puis relue revient à l'identique, et reste compacte. */
    @Test
    fun atlasLineRoundTrip() {
        val g = game(8, "atlas")
        for (key in g.solution) { val (a, b) = g.endsOf(key); g.toggleBridge(a, b) }
        val index = g.nodes.withIndex().associate { it.value.id to it.index }
        val stars = IntArray(g.nodes.size * 3)
        g.nodes.forEachIndexed { i, n -> stars[i * 3] = n.x; stars[i * 3 + 1] = n.y; stars[i * 3 + 2] = n.required }
        val keys = g.bridges.keys.toList()
        val links = IntArray(keys.size * 2)
        keys.forEachIndexed { i, k -> val (a, b) = g.endsOf(k); links[i * 2] = index.getValue(a); links[i * 2 + 1] = index.getValue(b) }
        val entry = AtlasEntry(g.seed, 8, 5, 17, stars, links, -12.3f, 40.7f, -21.5f, 1_758_700_000_000L, 187L)
        val line = entry.toLine()
        println("Ligne d'atlas pour une grille 8×8 (${g.nodes.size} étoiles, ${keys.size} traits) : ${line.length} caractères")
        val back = AtlasEntry.fromLine(line)!!
        assertEquals(entry.seed, back.seed)
        assertEquals(entry.noun, back.noun); assertEquals(entry.adjective, back.adjective)
        assertTrue(entry.stars.contentEquals(back.stars))
        assertTrue(entry.links.contentEquals(back.links))
        assertEquals(entry.skyX, back.skyX, 0.05f); assertEquals(entry.skyY, back.skyY, 0.05f)
        assertEquals(entry.solvedAt, back.solvedAt); assertEquals(entry.seconds, back.seconds)
        assertTrue("ligne trop longue : ${line.length}", line.length < 300)
    }
}
