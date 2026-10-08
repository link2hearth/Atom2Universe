package com.Atom2Universe.app.games.jigsaw

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class JigsawGameTest {
    private fun game(rotation: Boolean = false, size: JigsawSize = JigsawSize.MINI) =
        JigsawGame("Assets/Cartes/rainbow/11-chat.jpg", size, 17, rotation)
    private fun take(game: JigsawGame, id: Int, x: Float, y: Float): PieceGroup =
        game.groups.first { id in it.ids }.apply { inTray = false; offset = PuzzlePoint(x, y) }

    @Test fun `all sizes respect image ratio and include every piece exactly once`() {
        assertEquals(listOf(24, 54, 96, 150, 216, 294), JigsawSize.entries.map { it.count })
        for (size in JigsawSize.entries) for (rotation in listOf(false, true)) {
            val game = game(rotation, size)
            assertTrue(game.valid()); assertEquals(0, game.placed); assertFalse(game.solved)
            assertEquals(2 * size.rows, 3 * size.columns)
        }
    }

    @Test fun `only correctly aligned true neighbours can form a group`() {
        val game = game()
        val a = take(game, 0, 210f, 100f)
        val b = take(game, 1, 220f, 106f)
        take(game, 23, 220f, 106f) // Same offset, but not a neighbour.
        assertTrue(game.snap(a, 20f))
        assertEquals(setOf(0, 1), a.ids.toSet()); assertFalse(a.locked)
        assertFalse(b in game.groups); assertTrue(game.valid())
        val c = take(game, 2, 500f, 100f)
        assertFalse(game.snap(c, 20f)); assertEquals(1, c.ids.size)
    }

    @Test fun `pieces cannot join across row boundaries or different rotations`() {
        val game = game(true)
        val a = take(game, 3, 200f, 200f).apply { turns = 0 }
        take(game, 4, 200f, 200f).turns = 0
        assertFalse(game.snap(a, 20f))
        take(game, 2, 200f, 200f).turns = 1
        assertFalse(game.snap(a, 20f))
        assertTrue(game.valid())
    }

    @Test fun `four rotations preserve world position of a group`() {
        val game = game(true)
        val a = take(game, 5, 150f, 250f).apply { turns = 0 }
        take(game, 6, 150f, 250f).turns = 0
        game.snap(a, 10f)
        val before = a.offset; val center = game.groupCenter(a)
        repeat(4) { game.rotate(a); assertPoint(center, game.groupCenter(a)) }
        assertEquals(0, a.turns); assertPoint(before, a.offset)
        assertTrue(game.valid())
    }

    @Test fun `fixing a group locks all of its pieces and a solved board is immovable`() {
        val game = game(true)
        val a = take(game, 0, 200f, 200f).apply { turns = 0 }
        take(game, 1, 200f, 200f).turns = 0
        game.snap(a, 10f)
        a.offset = PuzzlePoint(5f, 4f)
        game.snap(a, 10f)
        assertEquals(2, game.placed)
        for (id in 2 until game.size.count) {
            val group = take(game, id, 0f, 0f).apply { turns = 0 }
            assertTrue(game.snap(group, 10f))
        }
        assertTrue(game.solved); assertEquals(1, game.groups.size); assertTrue(game.valid())
        game.rotate(game.groups.single()); game.tidy()
        assertTrue(game.solved); assertEquals(0, game.groups.single().turns)
    }

    @Test fun `tidying keeps assemblies and returns loose singles to the carousel`() {
        val game = game()
        val a = take(game, 0, 200f, 200f)
        take(game, 1, 200f, 200f)
        game.snap(a, 10f)
        val loose = take(game, 5, 400f, 400f)
        game.tidy()
        assertFalse(a.inTray); assertTrue(loose.inTray)
        assertEquals(setOf(0, 1), a.ids.toSet()); assertTrue(game.valid())
    }

    @Test fun `seeded geometry has closed paths and exactly complementary internal edges`() {
        for (size in JigsawSize.entries) for (seed in listOf(0, 17, -1, 421)) {
            val geometry = JigsawGeometry(size, seed)
            val copies = JigsawGeometry(size, seed)
            val all = (0 until size.count).associateWith { geometry.piece(it) }
            for ((id, curves) in all) {
                assertEquals(curves, copies.piece(id))
                curves.forEachIndexed { i, curve -> assertPoint(curve.end, curves[(i + 1) % curves.size].start) }
                if (id % size.columns < size.columns - 1) {
                    val neighbour = all.getValue(id + 1)
                    // Every right-side cubic (including the shoulders) is reused verbatim in reverse.
                    assertTrue(curves.count { it.reversed() in neighbour } >= 3)
                }
                if (id / size.columns < size.rows - 1) {
                    val neighbour = all.getValue(id + size.columns)
                    assertTrue(curves.count { it.reversed() in neighbour } >= 3)
                }
            }
        }
    }

    @Test fun `save restores groups rotations tray order timer and reward receipt identity`() {
        val game = game(true, JigsawSize.MASTER)
        val a = take(game, 15, 150f, 220f).apply { turns = 1 }
        take(game, 16, 150f, 220f).turns = 1
        game.snap(a, 10f)
        game.elapsedMs = 456789; game.rewardHandled = true
        val restored = JigsawCodec.decode(JigsawCodec.encode(game))!!
        assertEquals(game.imagePath, restored.imagePath); assertEquals(game.size, restored.size)
        assertEquals(game.sessionId, restored.sessionId); assertEquals(game.seed, restored.seed)
        assertEquals(game.rotation, restored.rotation); assertEquals(game.groups, restored.groups)
        assertEquals(game.elapsedMs, restored.elapsedMs)
        assertTrue(restored.rewardHandled); assertTrue(restored.valid())
    }

    @Test fun `damaged saves with missing or duplicate pieces are rejected`() {
        assertNull(JigsawCodec.decode("not json"))
        val missing = JSONObject(JigsawCodec.encode(game()))
        missing.getJSONArray("groups").remove(0)
        assertNull(JigsawCodec.decode(missing.toString()))
        val duplicate = JSONObject(JigsawCodec.encode(game()))
        val groups = duplicate.getJSONArray("groups")
        groups.getJSONObject(1).getJSONArray("ids").put(0, groups.getJSONObject(0).getJSONArray("ids").getInt(0))
        assertNull(JigsawCodec.decode(duplicate.toString()))
        val lockedInTray = JSONObject(JigsawCodec.encode(game()))
        lockedInTray.getJSONArray("groups").getJSONObject(0).put("locked", true)
        assertNull(JigsawCodec.decode(lockedInTray.toString()))
    }

    @Test fun `a fast horizontal swipe stays a scroll even when the finger drifts out of the tray`() {
        val swipe = JigsawTrayGesture(10f)
        assertEquals(TrayIntent.PENDING, swipe.move(-4f, -2f, true))
        assertEquals(TrayIntent.SCROLL, swipe.move(-45f, -6f, true))
        assertEquals(TrayIntent.SCROLL, swipe.move(-120f, -190f, true))
        assertEquals(TrayIntent.SCROLL, swipe.move(-160f, -300f, true))
        assertEquals(TrayIntent.EXTRACT, JigsawTrayGesture(10f).move(2f, -24f, true))
        assertEquals(TrayIntent.PENDING, JigsawTrayGesture(10f).move(2f, -24f, false))
    }

    @Test fun `free table starts scattered around an empty frame with all pieces available and no carousel`() {
        for (size in JigsawSize.entries) {
            val table = JigsawGame("image", size, 37, true, layout = JigsawLayout.TABLE)
            assertTrue(table.valid()); assertFalse(table.solved); assertEquals(0, table.placed)
            assertTrue(table.groups.none { it.inTray || it.locked })
            assertEquals(size.count, table.groups.size)
            assertTrue(table.groups.map { table.groupCenter(it) }.distinct().size > 1)
            assertTrue(table.groups.all { table.bounds(it).let { b ->
                b.right < 0f || b.left > 600f || b.bottom < 0f || b.top > 900f
            } })
            assertTrue(table.groups.any { table.groupCenter(it).x < 0f })
            assertTrue(table.groups.any { table.groupCenter(it).x > 600f })
            assertTrue(table.groups.any { table.groupCenter(it).y < 0f })
            assertTrue(table.groups.any { table.groupCenter(it).y > 900f })
            val copy = JigsawGame("image", size, 37, true, layout = JigsawLayout.TABLE)
            assertEquals(table.groups, copy.groups)
        }
        val table = JigsawGame("image", JigsawSize.MASTER, 37, false, layout = JigsawLayout.TABLE)
        val restored = JigsawCodec.decode(JigsawCodec.encode(table))!!
        assertEquals(JigsawLayout.TABLE, restored.layout); assertEquals(table.groups, restored.groups)
    }

    @Test fun `dropping outside the image preserves the piece position and it is included in show all`() {
        for (layout in JigsawLayout.entries) {
            val game = JigsawGame("image", JigsawSize.MINI, 17, false, layout = layout)
            for ((id, position) in listOf(PuzzlePoint(-500f, -400f), PuzzlePoint(1400f, 250f), PuzzlePoint(500f, 1600f)).withIndex()) {
                val group = take(game, id, position.x, position.y)
                val index = game.groups.indexOf(group)
                assertFalse(game.release(group, 10f))
                assertEquals(position, group.offset); assertFalse(group.inTray); assertFalse(group.locked)
                assertEquals(index, game.groups.indexOf(group))
                val bounds = game.bounds(group); val table = game.tableBounds()
                assertTrue(table.left <= bounds.left && table.right >= bounds.right)
                assertTrue(table.top <= bounds.top && table.bottom >= bounds.bottom)
            }
            val restored = JigsawCodec.decode(JigsawCodec.encode(game))!!
            assertEquals(game.groups, restored.groups); assertTrue(restored.valid())
        }
    }

    @Test fun `spreading the free table keeps fixed pieces and assemblies and reveals loose singles`() {
        val game = JigsawGame("image", JigsawSize.MASTER, 17, false, layout = JigsawLayout.TABLE)
        val group = take(game, 0, -2000f, 400f)
        take(game, 1, -2000f, 400f)
        game.snap(group, 10f)
        val fixed = take(game, 5, 0f, 0f)
        game.snap(fixed, 10f)
        val before = group.copyGroup()
        game.tidy()
        assertEquals(before, group); assertTrue(fixed.locked); assertEquals(1, game.placed)
        assertTrue(game.groups.none { it.inTray }); assertTrue(game.valid())
        val singles = game.groups.filter { !it.locked && it.ids.size == 1 }
        assertEquals(singles.size, singles.map { game.groupCenter(it) }.distinct().size)
        assertTrue(singles.all { g -> game.groupCenter(g).let { it.x < 0 || it.x > 600 || it.y < 0 || it.y > 900 } })
    }

    @Test fun `saves from another format version are rejected`() {
        val game = game()
        game.release(take(game, 7, -240f, 1060f), 10f)
        for (version in listOf(1, 3, 999)) {
            val other = JSONObject(JigsawCodec.encode(game)).put("version", version)
            assertNull(JigsawCodec.decode(other.toString()))
        }
    }

    @Test fun `free table release chooses the closest neighbour and never chains across a pile`() {
        val game = JigsawGame("image", JigsawSize.MINI, 17, false, layout = JigsawLayout.TABLE)
        val a = take(game, 0, 600f, 500f)
        take(game, 1, 612f, 500f)
        val farther = take(game, 4, 615f, 500f)
        val continuation = take(game, 2, 625f, 500f)
        assertTrue(game.release(a, 999f))
        assertEquals(setOf(0, 1), a.ids.toSet())
        assertTrue(farther in game.groups); assertTrue(continuation in game.groups)
        assertEquals(23, game.groups.size); assertFalse(a.locked); assertTrue(game.valid())
    }

    @Test fun `free table snap has a small relative tolerance even with a huge caller radius`() {
        val game = JigsawGame("image", JigsawSize.MINI, 17, false, layout = JigsawLayout.TABLE)
        game.groups.forEach { it.offset = PuzzlePoint(5000f, 5000f) }
        val a = take(game, 0, 600f, 500f)
        take(game, 1, 622f, 500f) // 22 units: outside the 18-unit tolerance for a 150-unit piece.
        assertFalse(game.release(a, 999f)); assertEquals(listOf(0), a.ids)
        a.offset = PuzzlePoint(612f, 500f)
        assertTrue(game.release(a, 999f)); assertEquals(setOf(0, 1), a.ids.toSet())
        val framePiece = take(game, 3, 22f, 0f)
        assertFalse(game.release(framePiece, 999f)); assertFalse(framePiece.locked)
        framePiece.offset = PuzzlePoint(17f, 0f)
        assertTrue(game.release(framePiece, 999f)); assertTrue(framePiece.locked)
        assertTrue(game.valid())
    }

    @Test fun `returning a loose piece to the carousel preserves its rotation and saved progress`() {
        val game = game(true)
        val piece = take(game, 7, -360f, 1050f).apply { turns = 3 }
        val ids = game.groups.flatMap { it.ids }.toSet()
        val offset = piece.offset
        game.elapsedMs = 45000
        assertTrue(game.returnToTray(piece))
        assertTrue(piece.inTray); assertEquals(3, piece.turns); assertEquals(offset, piece.offset)
        assertEquals(ids, game.groups.flatMap { it.ids }.toSet())
        assertEquals(45000L, game.elapsedMs); assertEquals(0, game.placed)
        val restored = JigsawCodec.decode(JigsawCodec.encode(game))!!
        assertEquals(game.groups, restored.groups); assertTrue(restored.valid())
    }

    @Test fun `return gesture only accepts loose singles in carousel mode`() {
        val game = game()
        val group = take(game, 0, 600f, 500f)
        take(game, 1, 600f, 500f)
        game.snap(group, 10f)
        assertFalse(game.returnToTray(group)); assertFalse(group.inTray)
        val fixed = take(game, 5, 0f, 0f)
        game.snap(fixed, 10f)
        assertFalse(game.returnToTray(fixed)); assertFalse(fixed.inTray)
        assertFalse(game.returnToTray(game.groups.first { it.inTray }))
        assertFalse(game.returnToTray(PieceGroup(mutableListOf(7), inTray = false)))
        val table = JigsawGame("image", JigsawSize.MINI, 17, false, layout = JigsawLayout.TABLE)
        assertFalse(table.returnToTray(table.groups.first())); assertTrue(table.valid())
        assertTrue(game.valid())
    }

    @Test fun `first corner pieces catch the actual frame in both layouts at every difficulty`() {
        for (size in JigsawSize.entries) for (layout in JigsawLayout.entries) {
            val game = JigsawGame("image", size, 17, false, layout = layout)
            game.groups.forEach { it.offset = PuzzlePoint(5000f, 5000f) }
            val corners = listOf(0, size.columns - 1, size.count - size.columns, size.count - 1)
            corners.forEach { id ->
                val corner = take(game, id, size.cell * .22f, -size.cell * .16f)
                assertTrue(game.release(corner, size.cell * .1f, size.cell * .32f))
                assertTrue(corner.locked); assertPoint(PuzzlePoint(0f, 0f), corner.offset)
            }
            assertEquals(4, game.placed); assertTrue(game.valid())
        }
    }

    @Test fun `more forgiving frame never attracts the wrong corner or strengthens loose neighbour magnets`() {
        val game = JigsawGame("image", JigsawSize.MINI, 17, true, layout = JigsawLayout.TABLE)
        game.groups.forEach { it.offset = PuzzlePoint(5000f, 5000f) }
        val wrongCorner = take(game, 0, 450f, 0f).apply { turns = 0 }
        assertFalse(game.release(wrongCorner, 18f, 999f)); assertFalse(wrongCorner.locked)
        val rotated = take(game, 3, 5f, 4f).apply { turns = 1 }
        assertFalse(game.release(rotated, 18f, 999f)); assertFalse(rotated.locked)
        val loose = take(game, 8, 600f, 500f).apply { turns = 0 }
        take(game, 9, 628f, 500f).turns = 0
        assertFalse(game.release(loose, 999f, 999f)); assertEquals(listOf(8), loose.ids)
    }

    @Test fun `paid guide persists without modifying placements hints timer or reward identity`() {
        val game = game()
        take(game, 7, 250f, 650f)
        game.elapsedMs = 47000
        val groups = game.groups.map { it.copyGroup() }; val session = game.sessionId
        game.guideUnlocked = true; game.guideVisible = true
        var restored = JigsawCodec.decode(JigsawCodec.encode(game))!!
        assertTrue(restored.guideUnlocked && restored.guideVisible)
        assertEquals(groups, restored.groups); assertEquals(session, restored.sessionId)
        assertEquals(47000L, restored.elapsedMs); assertFalse(restored.rewardHandled)
        restored.guideVisible = false
        restored = JigsawCodec.decode(JigsawCodec.encode(restored))!!
        assertTrue(restored.guideUnlocked); assertFalse(restored.guideVisible)
    }

    @Test fun `an unpaid visible guide is rejected`() {
        val invalid = JSONObject(JigsawCodec.encode(game())).put("guideVisible", true)
        assertNull(JigsawCodec.decode(invalid.toString()))
    }

    private fun assertPoint(a: PuzzlePoint, b: PuzzlePoint) {
        assertEquals(a.x, b.x, .001f); assertEquals(a.y, b.y, .001f)
    }
}
