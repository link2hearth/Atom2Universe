package com.Atom2Universe.app.games.match3

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Small, bounded contracts. Physical device tilt controls gravity. */
class ForgeGame(val seed: Int = Random.nextInt(), val cols: Int = 8, val rows: Int = 10) {
    companion object { const val CORE = 5 }
    val cells = IntArray(cols * rows)
    val slag = BooleanArray(cells.size)
    var stage = 0
    var moves = 24
    var collected = 0
    var gravity = 0 // 0 down, 1 left, 2 right, 3 up (fixed game orientation)
    var deliveryEdge = 0 // Same direction codes; preserved when transposing a saved board.
    val kind get() = stage % 3
    val material get() = (seed.toLong().let { kotlin.math.abs(it) } % 5).toInt()
    val target get() = when (kind) { 0 -> 24 + stage * 2; 1 -> 12 + stage; else -> 1 }
    val won get() = collected >= target
    private var random = Random(seed)

    init { startStage(0) }

    fun startStage(index: Int) {
        stage = index
        random = Random(seed xor (stage * 7919))
        moves = 24 + if (kind == 2) 6 else 0
        collected = 0
        gravity = 0
        deliveryEdge = 0
        slag.fill(false)
        generateBoard()
        if (kind == 1) cells.indices.shuffled(random).take(target).forEach { slag[it] = true }
        if (kind == 2) cells[cols + cols / 2] = CORE
        ensurePlayable()
    }

    private fun generateBoard() {
        for (i in cells.indices) {
            val available = (0..4).filter { value ->
                !(i % cols >= 2 && cells[i - 1] == value && cells[i - 2] == value) &&
                    !(i >= cols * 2 && cells[i - cols] == value && cells[i - cols * 2] == value)
            }
            cells[i] = available.random(random)
        }
    }

    fun swap(a: Int, b: Int) { val temp = cells[a]; cells[a] = cells[b]; cells[b] = temp }

    fun matches(): Set<Int> {
        val result = mutableSetOf<Int>()
        for (i in cells.indices) {
            if (cells[i] !in 0..4) continue
            if (i % cols <= cols - 3 && cells[i] == cells[i + 1] && cells[i] == cells[i + 2]) {
                var j = i
                while (j / cols == i / cols && j < cells.size && cells[j] == cells[i]) result.add(j++)
            }
            if (i / cols <= rows - 3 && cells[i] == cells[i + cols] && cells[i] == cells[i + cols * 2]) {
                var j = i
                while (j < cells.size && cells[j] == cells[i]) { result.add(j); j += cols }
            }
        }
        return result
    }

    fun clear(matches: Set<Int>) {
        for (i in matches) {
            if (kind == 0 && cells[i] == material) collected++
            if (kind == 1 && slag[i]) { slag[i] = false; collected++ }
            cells[i] = -1
        }
    }

    /** Returns the source cell for each destination, including off-board spawn positions. */
    fun collapse(): IntArray {
        val source = IntArray(cells.size) { it }
        val vertical = gravity == 0 || gravity == 3
        repeat(if (vertical) cols else rows) { lane ->
            val line = if (gravity == 0) (rows - 1 downTo 0).map { it * cols + lane }
                else if (gravity == 3) (0 until rows).map { it * cols + lane }
                else if (gravity == 1) (0 until cols).map { lane * cols + it }
                else (cols - 1 downTo 0).map { lane * cols + it }
            val kept = line.filter { cells[it] >= 0 }.map { it to cells[it] }
            for ((position, destination) in line.withIndex()) {
                if (position < kept.size) {
                    source[destination] = kept[position].first
                    cells[destination] = kept[position].second
                } else {
                    cells[destination] = random.nextInt(5)
                    // Spawn offsets are represented separately by the view for horizontal lanes.
                    source[destination] = -1 - (position - kept.size)
                }
            }
        }
        return source
    }

    fun deliver(): Boolean {
        if (kind != 2) return false
        val i = cells.indexOf(CORE)
        val atExit = i >= 0 && when (deliveryEdge) {
            1 -> i % cols == 0
            2 -> i % cols == cols - 1
            3 -> i < cols
            else -> i >= (rows - 1) * cols
        }
        if (atExit) { collected = 1; return true }
        return false
    }

    private fun hasMove(): Boolean {
        for (i in cells.indices) for (j in intArrayOf(i + 1, i + cols)) {
            if (j >= cells.size || (j == i + 1 && i / cols != j / cols)) continue
            swap(i, j)
            val found = matches().isNotEmpty()
            swap(i, j)
            if (found) return true
        }
        return false
    }

    fun ensurePlayable(): Boolean {
        if (hasMove()) return false
        val core = cells.indexOf(CORE)
        repeat(100) {
            generateBoard()
            if (core >= 0) cells[core] = CORE
            if (hasMove()) return true
        }
        // Guaranteed horizontal match after swapping the two middle cells.
        cells[0] = 0; cells[1] = 1; cells[2] = 0; cells[cols + 1] = 0
        return true
    }

    fun save(): String = JSONObject().apply {
        put("cols", cols); put("rows", rows)
        put("seed", seed); put("stage", stage); put("moves", moves)
        put("collected", collected); put("gravity", gravity)
        put("deliveryEdge", deliveryEdge)
        put("cells", JSONArray(cells.toList())); put("slag", JSONArray(slag.toList()))
    }.toString()

    fun restore(json: JSONObject) {
        stage = json.getInt("stage"); moves = json.getInt("moves")
        collected = json.getInt("collected"); gravity = json.getInt("gravity")
        deliveryEdge = json.optInt("deliveryEdge", 0)
        for (i in cells.indices) { cells[i] = json.getJSONArray("cells").getInt(i); slag[i] = json.getJSONArray("slag").getBoolean(i) }
        random = Random(seed xor (stage * 7919) xor moves)
    }
}
