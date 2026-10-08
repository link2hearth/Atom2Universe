package com.Atom2Universe.app.games.match3

import com.Atom2Universe.app.games.match3.ForgeGame.Run
import com.Atom2Universe.app.games.match3.ForgeGame.Beam
import com.Atom2Universe.app.games.match3.ForgeGame.Burst
import com.Atom2Universe.app.games.match3.ForgeGame.Companion.BLAST_RADIUS

/** Shared natural matches and bonuses for expedition and overheat. */
internal class Match3Patterns(private val cells: IntArray, private val cols: Int) {
    private val rows = cells.size / cols
    fun runs(): List<Run> {
        val result = mutableListOf<Run>()
        for (horizontal in listOf(true, false)) {
            val length = if (horizontal) cols else rows
            repeat(if (horizontal) rows else cols) { lane ->
                fun index(p: Int) = if (horizontal) lane * cols + p else p * cols + lane
                var p = 0
                while (p < length) {
                    val start = p
                    val type = cells[index(p++)]
                    while (p < length && cells[index(p)] == type) p++
                    if (type in 0..4 && p - start >= 3) result.add(Run((start until p).map { index(it) }, horizontal))
                }
            }
        }
        return result
    }

    fun matches(): Set<Int> = runs().flatMap { it.cells }.toSet()

    fun burst(): Burst {
        val runs = runs()
        val cleared = runs.flatMap { it.cells }.toMutableSet()
        val horizontal = runs.filter { it.horizontal }
        val vertical = runs.filter { !it.horizontal }
        val crossings = horizontal.flatMap { h -> vertical.flatMap { v -> h.cells.intersect(v.cells.toSet()) } }.toSet()
        // One blast per connected matched shape, including larger crosses.
        val visited = mutableSetOf<Int>()
        val bombs = mutableListOf<Int>()
        for (origin in crossings.sorted()) {
            if (origin in visited) continue
            bombs.add(origin)
            val pending = java.util.ArrayDeque<Int>().apply { add(origin) }
            while (pending.isNotEmpty()) {
                val i = pending.removeFirst()
                if (!visited.add(i)) continue
                for (j in neighbours(i)) if (j in cleared && cells[j] == cells[origin] && j !in visited) pending.add(j)
            }
        }
        val beams = runs.filter { it.cells.size >= 4 }
            .map { Beam(it.cells[it.cells.size / 2], it.horizontal) }
        for (beam in beams) {
            if (beam.horizontal) for (c in 0 until cols) cleared.add(beam.origin / cols * cols + c)
            else for (r in 0 until rows) cleared.add(r * cols + beam.origin % cols)
        }
        for (origin in bombs) for (i in cells.indices) {
            val dx = i % cols - origin % cols; val dy = i / cols - origin / cols
            if (dx * dx + dy * dy <= BLAST_RADIUS * BLAST_RADIUS) cleared.add(i)
        }
        return Burst(cleared.filter { cells[it] in 0..4 }.toSet(), beams, bombs)
    }

    private fun neighbours(i: Int): List<Int> = buildList {
        if (i % cols > 0) add(i - 1)
        if (i % cols < cols - 1) add(i + 1)
        if (i >= cols) add(i - cols)
        if (i < cells.size - cols) add(i + cols)
    }

}
