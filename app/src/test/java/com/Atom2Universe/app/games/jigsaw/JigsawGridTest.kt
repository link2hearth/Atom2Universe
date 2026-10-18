package com.Atom2Universe.app.games.jigsaw

import org.junit.Assert.*
import org.junit.Test

class JigsawGridTest {
    @Test fun `image formats keep their proportions and offer increasing nearly square grids`() {
        for ((width, height) in listOf(4000 to 3000, 3000 to 4000, 3000 to 3000, 3840 to 2160, 6000 to 1000)) {
            val grids = JigsawSize.entries.map { JigsawGrid.forImage(it, width, height) }
            assertEquals(6, grids.map { it.count }.distinct().size)
            assertEquals(grids.map { it.count }.sorted(), grids.map { it.count })
            grids.forEach { grid ->
                assertEquals(width.toFloat() / height, grid.width / grid.height, .00001f)
                assertTrue(grid.cellWidth / grid.cellHeight in .7f..1.4f)
            }
        }
    }

    @Test fun `original portrait format retains its existing piece counts`() {
        JigsawSize.entries.forEach { size ->
            assertEquals(JigsawGrid.portrait(size), JigsawGrid.forImage(size, 1200, 1800))
        }
    }

    @Test fun `landscape pieces share matching seams and finish on their own frame`() {
        val grid = JigsawGrid.forImage(JigsawSize.MINI, 4000, 3000)
        val geometry = JigsawGeometry(grid, 17)
        val game = JigsawGame("photo", JigsawSize.MINI, 17, false, grid = grid)
        for (id in 0 until grid.count) {
            val curves = geometry.piece(id)
            assertEquals(curves.first().start, curves.last().end)
            curves.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
            if (id % grid.columns < grid.columns - 1) {
                val neighbour = geometry.piece(id + 1)
                assertTrue(curves.count { it.reversed() in neighbour } >= 3)
            }
        }
        val last = game.center(grid.count - 1)
        assertEquals(grid.width - grid.cellWidth / 2, last.x, .0001f)
        assertEquals(grid.height - grid.cellHeight / 2, last.y, .0001f)
        game.groups.toList().forEach { group ->
            group.inTray = false
            assertTrue(game.snap(group, 1f))
        }
        assertTrue(game.solved)
        assertTrue(game.valid())
    }

    @Test fun `dynamic frame and piece positions survive save and resume`() {
        for (layout in JigsawLayout.entries) {
            val grid = JigsawGrid.forImage(JigsawSize.EASY, 4032, 3024)
            val game = JigsawGame("content://photos/vacation", JigsawSize.EASY, 91, true, layout = layout, grid = grid)
            game.rotate(game.groups.first())
            val restored = JigsawCodec.decode(JigsawCodec.encode(game))!!
            assertEquals(grid, restored.grid)
            assertEquals(game.groups, restored.groups)
            assertTrue(restored.valid())
        }
    }
}
