package com.Atom2Universe.app.games.pipetap

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor

class PipeTapGame {

    companion object {
        const val NORTH = 1
        const val EAST  = 2
        const val SOUTH = 4
        const val WEST  = 8
    }

    var difficulty = PipeTapDifficulty.MEDIUM
    var grid: Array<IntArray> = emptyArray()
    var source = Pair(0, 0)
    var moves = 0
    var elapsedSeconds = 0L
    var solved = false
    var rewardClaimed = false
    var seed = ""

    private var rngState = 0

    // ── RNG (mulberry32 + xmur3, ported from JS) ──────────────────────────────────
    private fun xmur3(str: String): Int {
        var h = -1640531527 xor str.length
        for (c in str) {
            h = h xor c.code
            h = (h.toLong() * 3432918353L).toInt()
            h = (h shl 13) or (h ushr 19)
        }
        h = (h.toLong() xor (h ushr 16).toLong()).toInt()
        h = (h.toLong() * -2048144789L).toInt()
        h = (h.toLong() xor (h ushr 13).toLong()).toInt()
        h = (h.toLong() * -1028477387L).toInt()
        h = h xor (h ushr 16)
        return h
    }

    private fun seeded(seedStr: String) {
        rngState = xmur3(seedStr)
    }

    private fun nextFloat(): Float {
        var t = rngState + 0x6D2B79F5
        rngState = t
        t = (t.toLong() xor (t ushr 15).toLong()).toInt()
        val a = t.toLong() * (t.or(1).toLong() and 0xFFFFFFFFL)
        t = a.toInt()
        t = t xor (t + (t.toLong() * (t.or(61).toLong() and 0xFFFFFFFFL)).toInt())
        return ((t xor (t ushr 14)) ushr 0).toLong().and(0xFFFFFFFFL).toFloat() / 4294967296f
    }

    private fun nextInt(n: Int): Int = floor(nextFloat() * n).toInt().coerceIn(0, n - 1)

    // ── Rotation ──────────────────────────────────────────────────────────────────
    private fun rotCW(mask: Int): Int {
        var v = 0
        if (mask and NORTH != 0) v = v or EAST
        if (mask and EAST  != 0) v = v or SOUTH
        if (mask and SOUTH != 0) v = v or WEST
        if (mask and WEST  != 0) v = v or NORTH
        return v
    }

    fun rotateTileAt(x: Int, y: Int) {
        if (solved) return
        grid[y][x] = rotCW(grid[y][x])
        moves++
        solved = isSolved()
    }

    // ── Neighbours ────────────────────────────────────────────────────────────────
    private data class Nb(val x: Int, val y: Int, val dir: Int, val opp: Int)

    private fun neighbours(x: Int, y: Int, size: Int): List<Nb> = buildList {
        if (y > 0)        add(Nb(x, y - 1, NORTH, SOUTH))
        if (x < size - 1) add(Nb(x + 1, y, EAST,  WEST))
        if (y < size - 1) add(Nb(x, y + 1, SOUTH, NORTH))
        if (x > 0)        add(Nb(x - 1, y, WEST,  EAST))
    }

    // ── Grid generation ───────────────────────────────────────────────────────────
    private fun generateGrid(size: Int): Array<IntArray> {
        val visited = Array(size) { BooleanArray(size) }
        val startX = nextInt(size)
        val startY = nextInt(size)
        val stack = ArrayDeque<Pair<Int,Int>>()
        stack.addLast(Pair(startX, startY))
        visited[startY][startX] = true

        data class Edge(val x: Int, val y: Int, val dir: Int)
        val edges = mutableListOf<Edge>()

        while (stack.isNotEmpty()) {
            val (cx, cy) = stack.last()
            val nbs = neighbours(cx, cy, size).filter { !visited[it.y][it.x] }.toMutableList()
            // Fisher-Yates shuffle
            for (i in nbs.size - 1 downTo 1) {
                val j = nextInt(i + 1)
                val tmp = nbs[i]; nbs[i] = nbs[j]; nbs[j] = tmp
            }
            val next = nbs.firstOrNull()
            if (next == null) {
                stack.removeLast()
                continue
            }
            edges += Edge(cx, cy, next.dir)
            edges += Edge(next.x, next.y, next.opp)
            visited[next.y][next.x] = true
            stack.addLast(Pair(next.x, next.y))
        }

        val g = Array(size) { IntArray(size) }
        for (e in edges) g[e.y][e.x] = g[e.y][e.x] or e.dir

        // Random rotation per tile
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (g[y][x] == 0) continue
                val rotations = nextInt(4)
                repeat(rotations) { g[y][x] = rotCW(g[y][x]) }
            }
        }
        return g
    }

    private fun findSource(g: Array<IntArray>): Pair<Int,Int> {
        for (y in g.indices) for (x in g[y].indices) if (g[y][x] != 0) return Pair(x, y)
        return Pair(0, 0)
    }

    // ── Solve check (BFS) ─────────────────────────────────────────────────────────
    fun isSolved(): Boolean {
        val size = grid.size
        val total = grid.sumOf { row -> row.count { it != 0 } }
        if (total == 0) return false
        val (sx, sy) = source
        val visited = HashSet<Int>(total * 2)
        val queue = ArrayDeque<Pair<Int,Int>>()
        queue.addLast(Pair(sx, sy))
        visited += sy * size + sx

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            val mask = grid[cy][cx]
            if (mask == 0) continue
            for (nb in neighbours(cx, cy, size)) {
                if (mask and nb.dir == 0) continue
                val nm = grid[nb.y][nb.x]
                if (nm and nb.opp == 0) return false
                val id = nb.y * size + nb.x
                if (id !in visited && nm != 0) {
                    visited += id
                    queue.addLast(Pair(nb.x, nb.y))
                }
            }
        }
        return visited.size == total
    }

    fun computeReachable(): Set<Int> {
        val size = grid.size
        val (sx, sy) = source
        val visited = HashSet<Int>()
        val queue = ArrayDeque<Pair<Int,Int>>()
        queue.addLast(Pair(sx, sy))
        visited += sy * size + sx

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            val mask = grid[cy][cx]
            if (mask == 0) continue
            for (nb in neighbours(cx, cy, size)) {
                if (mask and nb.dir == 0) continue
                val nm = grid[nb.y][nb.x]
                if (nm and nb.opp == 0) continue
                val id = nb.y * size + nb.x
                if (id !in visited) {
                    visited += id
                    if (nm != 0) queue.addLast(Pair(nb.x, nb.y))
                }
            }
        }
        return visited
    }

    // ── New game ──────────────────────────────────────────────────────────────────
    fun newGame(diff: PipeTapDifficulty, customSeed: String = "") {
        difficulty = diff
        seed = customSeed.ifBlank { System.currentTimeMillis().toString(36) }
        seeded(seed)
        grid = generateGrid(diff.gridSize)
        source = findSource(grid)
        moves = 0
        elapsedSeconds = 0
        solved = false
        rewardClaimed = false
    }

    // ── Serialization ─────────────────────────────────────────────────────────────
    fun serialize(): String {
        val size = difficulty.gridSize
        val gridArr = JSONArray()
        for (y in 0 until size) {
            val row = JSONArray()
            for (x in 0 until size) row.put(grid[y][x])
            gridArr.put(row)
        }
        return JSONObject().apply {
            put("version", 1)
            put("size", size)
            put("seed", seed)
            put("grid", gridArr)
            put("sourceX", source.first)
            put("sourceY", source.second)
            put("moves", moves)
            put("elapsed", elapsedSeconds)
            put("solved", solved)
            put("rewardClaimed", rewardClaimed)
        }.toString()
    }

    fun deserialize(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            if (obj.getInt("version") != 1) return false
            val size = obj.getInt("size")
            val diff = PipeTapDifficulty.fromSize(size)
            val gridArr = obj.getJSONArray("grid")
            val g = Array(size) { y ->
                val row = gridArr.getJSONArray(y)
                IntArray(size) { x -> row.getInt(x) and 0xF }
            }
            difficulty = diff
            seed = obj.optString("seed", "")
            grid = g
            source = Pair(obj.getInt("sourceX"), obj.getInt("sourceY"))
            moves = obj.getInt("moves")
            elapsedSeconds = obj.optLong("elapsed", 0L)
            solved = obj.getBoolean("solved")
            rewardClaimed = obj.optBoolean("rewardClaimed", false)
            true
        } catch (_: Exception) {
            false
        }
    }
}
