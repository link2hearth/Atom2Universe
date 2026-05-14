package com.Atom2Universe.app.games.theline

import kotlin.random.Random

object TheLineGenerator {

    private const val MAX_ATTEMPTS = 100
    private const val MAX_ITERATIONS = 50000

    fun generate(mode: TheLineMode, difficulty: TheLineDifficulty): TheLinePuzzle? {
        val sizes = difficulty.gridSizes
        val (width, height) = sizes[Random.nextInt(sizes.size)]
        val totalCells = width * height

        val holeMax = if (mode == TheLineMode.MULTI)
            minOf(difficulty.holeMax, maxOf(difficulty.holeMin, 3))
        else difficulty.holeMax
        val targetHoles = if (difficulty.holeMin >= holeMax) difficulty.holeMin
        else Random.nextInt(difficulty.holeMin, holeMax + 1)

        var bestPath: List<Int>? = null
        var bestTurns = -1

        repeat(MAX_ATTEMPTS) {
            val result = generateWithHoles(width, height, targetHoles) ?: return@repeat
            val (blocked, path) = result
            if (path.size < 2) return@repeat
            val turns = countTurns(path, width)
            if (mode == TheLineMode.SINGLE && turns < difficulty.minTurns) {
                if (turns > bestTurns) { bestPath = path; bestTurns = turns }
                return@repeat
            }
            return buildPuzzle(mode, width, height, blocked, path, difficulty)
        }

        val fallback = bestPath ?: createSimpleHamiltonianPath(width, height) ?: return null
        return buildPuzzle(mode, width, height, emptySet(), fallback, difficulty)
    }

    private fun generateWithHoles(width: Int, height: Int, targetHoles: Int): Pair<Set<Int>, List<Int>>? {
        if (targetHoles <= 0) {
            val path = createSimpleHamiltonianPath(width, height) ?: return null
            return emptySet<Int>() to path
        }
        for (holeCount in targetHoles downTo 1) {
            val blocked = generateBlockedSet(width, height, holeCount)
            if (blocked.isEmpty()) continue
            val path = findHamiltonianPath(width, height, blocked) ?: continue
            if (path.size >= 2) return blocked to path
        }
        return null
    }

    private fun generateBlockedSet(width: Int, height: Int, targetCount: Int): Set<Int> {
        val totalCells = width * height
        val maxCount = minOf(targetCount, (totalCells * 0.4).toInt())
        if (maxCount <= 0) return emptySet()

        val interior = mutableListOf<Int>()
        val edges = mutableListOf<Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val isCorner = (x == 0 || x == width - 1) && (y == 0 || y == height - 1)
                val isEdge = !isCorner && (x == 0 || x == width - 1 || y == 0 || y == height - 1)
                if (!isCorner) { if (isEdge) edges.add(idx) else interior.add(idx) }
            }
        }
        interior.shuffle()
        edges.shuffle()

        val blocked = mutableSetOf<Int>()
        for (candidate in interior + edges) {
            if (blocked.size >= maxCount) break
            blocked.add(candidate)
            if (!isConnected(width, height, blocked)) blocked.remove(candidate)
        }
        return blocked
    }

    private fun isConnected(width: Int, height: Int, blocked: Set<Int>): Boolean {
        val totalCells = width * height
        val available = (0 until totalCells).filter { it !in blocked }
        if (available.size <= 1) return available.size == 1
        val visited = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(available[0]); visited.add(available[0])
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val x = cur % width; val y = cur / width
            listOfNotNull(
                if (x > 0) cur - 1 else null,
                if (x < width - 1) cur + 1 else null,
                if (y > 0) cur - width else null,
                if (y < height - 1) cur + width else null
            ).forEach { n -> if (n !in blocked && n !in visited) { visited.add(n); queue.add(n) } }
        }
        return visited.size == available.size
    }

    private fun findHamiltonianPath(width: Int, height: Int, blocked: Set<Int>): List<Int>? {
        val totalCells = width * height
        val accessible = totalCells - blocked.size
        if (accessible <= 0) return null

        val neighborCache = Array(totalCells) { idx ->
            if (idx in blocked) intArrayOf()
            else {
                val x = idx % width; val y = idx / width
                intArrayOf(
                    if (x > 0 && idx - 1 !in blocked) idx - 1 else -1,
                    if (x < width - 1 && idx + 1 !in blocked) idx + 1 else -1,
                    if (y > 0 && idx - width !in blocked) idx - width else -1,
                    if (y < height - 1 && idx + width !in blocked) idx + width else -1
                ).filter { it >= 0 }.toIntArray()
            }
        }

        val starts = (0 until totalCells).filter { it !in blocked }.shuffled().take(6)
        for (start in starts) {
            val result = searchPath(start, accessible, neighborCache) ?: continue
            return result
        }
        return null
    }

    private fun searchPath(start: Int, target: Int, neighbors: Array<IntArray>): List<Int>? {
        val path = mutableListOf(start)
        val visited = mutableSetOf(start)
        val choiceStack = mutableListOf(0)
        var iterations = 0
        while (path.isNotEmpty() && iterations < MAX_ITERATIONS) {
            iterations++
            if (path.size == target) return path.toList()
            val cur = path.last()
            val ns = neighbors[cur]
            val candidates = ns.filter { it !in visited }
                .map { n -> n to neighbors[n].count { it !in visited } }
                .sortedBy { it.second }
            val ci = choiceStack.last()
            if (ci < candidates.size) {
                choiceStack[choiceStack.size - 1] = ci + 1
                val next = candidates[ci].first
                path.add(next); visited.add(next); choiceStack.add(0)
            } else {
                val removed = path.removeAt(path.lastIndex); visited.remove(removed)
                choiceStack.removeAt(choiceStack.size - 1)
            }
        }
        return null
    }

    private fun countTurns(path: List<Int>, width: Int): Int {
        if (path.size < 3) return 0
        var turns = 0; var prevDx = 0; var prevDy = 0
        for (i in 1 until path.size) {
            val dx = (path[i] % width) - (path[i - 1] % width)
            val dy = (path[i] / width) - (path[i - 1] / width)
            if (i > 1 && (dx != prevDx || dy != prevDy)) turns++
            prevDx = dx; prevDy = dy
        }
        return turns
    }

    private fun createSimpleHamiltonianPath(width: Int, height: Int): List<Int>? {
        val path = mutableListOf<Int>()
        for (y in 0 until height) {
            if (y % 2 == 0) for (x in 0 until width) path.add(y * width + x)
            else for (x in width - 1 downTo 0) path.add(y * width + x)
        }
        return if (path.size >= 2) path else null
    }

    private fun buildPuzzle(
        mode: TheLineMode, width: Int, height: Int,
        blocked: Set<Int>, path: List<Int>, difficulty: TheLineDifficulty
    ): TheLinePuzzle {
        val coords = path.map { TLCoord(it % width, it / width) }
        return if (mode == TheLineMode.MULTI) {
            val total = coords.size
            val maxPairs = maxOf(2, total / 4)
            val pMin = minOf(difficulty.multiPairsMin, maxPairs)
            val pMax = minOf(difficulty.multiPairsMax, maxPairs)
            val pairCount = if (pMin >= pMax) pMin else Random.nextInt(pMin, pMax + 1)
            val segments = splitIntoSegments(coords, pairCount)
            TheLinePuzzle(mode, width, height, blocked, coords, segments = segments)
        } else {
            TheLinePuzzle(mode, width, height, blocked, coords,
                endpoints = coords.first() to coords.last())
        }
    }

    private fun splitIntoSegments(path: List<TLCoord>, count: Int): List<TLSegment> {
        val segments = mutableListOf<TLSegment>()
        var cursor = 0
        var remaining = path.size
        for (i in 0 until count) {
            val segsLeft = count - i
            val length = if (i == count - 1) remaining else {
                val minLen = 2
                val maxLen = maxOf(minLen, remaining - 2 * (segsLeft - 1))
                Random.nextInt(minLen, maxLen + 1)
            }
            val seg = path.subList(cursor, cursor + length)
            segments.add(TLSegment(
                colorValue = THE_LINE_COLORS[i % THE_LINE_COLORS.size],
                cells = seg,
                start = seg.first(),
                end = seg.last()
            ))
            cursor += length; remaining -= length
        }
        return segments
    }
}
