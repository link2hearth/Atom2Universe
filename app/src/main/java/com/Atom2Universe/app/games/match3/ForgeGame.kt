package com.Atom2Universe.app.games.match3

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Bounded contracts; the sensor supplies gravity independently of the interface. */
class ForgeGame(val seed: Int = Random.nextInt(), cols: Int = 8, rows: Int = 10,
    val endless: Boolean = false, private val landscapeLayout: Boolean = cols > rows, rounds: Int = 6,
    val slagEndless: Boolean = false) {
    companion object { const val CORE = 5; const val RIVET = -2; const val EMPTY = -1; const val BLAST_RADIUS = 3 }
    data class Run(val cells: List<Int>, val horizontal: Boolean)
    data class Beam(val origin: Int, val horizontal: Boolean)
    data class Burst(val cleared: Set<Int>, val beams: List<Beam>, val bombs: List<Int>)
    var cols = cols; private set
    var rows = rows; private set
    var cells = IntArray(cols * rows); private set
    var coreEdges = IntArray(cells.size) { -1 }; private set
    val activeDeliveryEdges get() = cells.indices.filter { cells[it] == CORE }.map { coreEdges[it] }.toSet()
    fun pieceKey(i: Int) = if (cells[i] == CORE) CORE + coreEdges[i] else cells[i]
    var slag = BooleanArray(cells.size); private set
    var slagLayers = IntArray(cells.size); private set
    var slagRules = IntArray(cells.size); private set // 0: any match, 1: beam, 2: blast
    private var slagGoal = 0
    var spawned = BooleanArray(cells.size); private set
    var score = 0L
    var elapsedMs = 0L
    private var stageStartScore = 0L
    private var stageRewarded = false
    var stage = 0
    var roundLimit = rounds.takeIf { it in listOf(3, 6, 12) } ?: 6; private set
    val expeditionComplete get() = !endless && won && stage >= roundLimit - 1
    var moves = 24
    var collected = 0
    var secondaryCollected = 0
    var dualRecipe = false
    var coreGoal = 1
    var gravity = 0 // down, left, right, up
    var deliveryEdge = 0
    val kind get() = if (slagEndless) 1 else if (endless) 2 else stage % 3
    val material get() = (kotlin.math.abs(seed.toLong()) % 5).toInt()
    val secondaryMaterial get() = (material + 2) % 5
    val target get() = when (kind) { 0 -> if (dualRecipe) 18 else 24 + stage * 2; 1 -> slagGoal; else -> coreGoal }
    val won get() = collected >= target && (!dualRecipe || secondaryCollected >= target)
    private var random = Random(seed)

    init { startStage(0) }

    fun startStage(index: Int) {
        if (index == stage) score = stageStartScore else stageStartScore = score
        stage = index
        stageRewarded = false
        random = Random(seed xor (stage * 7919))
        if (endless && !slagEndless) {
            val shapes = arrayOf(6 to 8, 8 to 8, 8 to 10, 9 to 11)
            val shape = shapes[Math.floorMod(seed, shapes.size).let { (it + stage % shapes.size) % shapes.size }]
            resize(if (landscapeLayout) shape.second else shape.first, if (landscapeLayout) shape.first else shape.second)
        }
        moves = if (endless) 28 + (cols * rows / 16) else if (kind == 2) 34 else 24
        collected = 0; secondaryCollected = 0
        dualRecipe = kind == 0 && stage >= 3
        coreGoal = if (endless) (1 + stage / 4).coerceAtMost(3) else if (stage >= 5) 2 else 1
        gravity = 0
        deliveryEdge = if (kind == 2) random.nextInt(4) else 0
        slag.fill(false); slagLayers.fill(0); slagRules.fill(0); cells.fill(EMPTY); coreEdges.fill(-1)
        if (!slagEndless && (endless || stage >= 2)) placeRivets()
        val directions = listOf(deliveryEdge) + (0..3).filter { it != deliveryEdge }.shuffled(random)
        val directionCount = if (endless) (1 + stage / 4).coerceAtMost(3) else 1
        if (kind == 2) repeat(coreGoal) { n ->
            val edge = directions[n % directionCount]
            val crossCol = (n + 1) * (cols - 1) / (coreGoal + 1)
            val crossRow = (n + 1) * (rows - 1) / (coreGoal + 1)
            val preferred = when (edge) {
                1 -> crossRow * cols + cols - 2
                2 -> crossRow * cols + 1
                3 -> (rows - 2) * cols + crossCol
                else -> cols + crossCol
            }
            // Spawn opposite this core's exit, without replacing another core or a rivet.
            val i = cells.indices.filter { cells[it] == EMPTY && when (edge) {
                1 -> it % cols == cols - 2
                2 -> it % cols == 1
                3 -> it / cols == rows - 2
                else -> it / cols == 1
            } }.minBy { kotlin.math.abs(it % cols - preferred % cols) + kotlin.math.abs(it / cols - preferred / cols) }
            cells[i] = CORE
            coreEdges[i] = edge
        }
        generateMetals()
        if (kind == 1) {
            val candidates = cells.indices.filter { cells[it] in 0..4 }.shuffled(random)
            slagGoal = (if (slagEndless) 12 + stage.coerceAtMost(100) * 3 else 12 + stage).coerceAtMost(candidates.size)
            val targets = candidates.take(slagGoal)
            targets.forEachIndexed { n, i ->
                slag[i] = true
                slagLayers[i] = if (slagEndless && stage >= 3 && n % 3 == 0) {
                    if (stage >= 9 && n % 2 == 0) 3 else 2
                } else 1
            }
            if (slagEndless) {
                val specialCount = if (stage >= 5) (1 + (stage - 5) / 3).coerceAtMost(8) else 0
                targets.take(specialCount).forEach { slagRules[it] = 1; slagLayers[it] = 1 }
                if (stage >= 7) targets.drop(specialCount).take((1 + (stage - 7) / 3).coerceAtMost(8))
                    .forEach { slagRules[it] = 2; slagLayers[it] = 1 }
                moves = 28 + slagLayers.sum() / 3 + slagRules.count { it != 0 } * 3
            }
        }
        ensurePlayable()
    }

    private fun resize(width: Int, height: Int) {
        cols = width; rows = height
        cells = IntArray(cols * rows); slag = BooleanArray(cells.size); spawned = BooleanArray(cells.size)
        coreEdges = IntArray(cells.size) { -1 }
        slagLayers = IntArray(cells.size); slagRules = IntArray(cells.size)
    }

    private fun placeRivets() {
        val pattern = (Math.floorMod(seed, 5) + stage % 5) % 5
        for (r in 2 until rows - 2) for (c in 2 until cols - 2) {
            val blocked = when (pattern) {
                0 -> r == rows / 2 && c != cols / 2 // perforated partition
                1 -> (r == rows / 3 && c < cols / 2) || (r == rows * 2 / 3 && c > cols / 2)
                2 -> r % 3 == 2 && c % 3 == 2 // separate pillars
                3 -> c == 2 + (r - 2) % (cols - 4) // staircase
                else -> c == cols / 2 || ((r == 2 || r == rows - 3) && c > cols / 2) // open bracket
            }
            if (!blocked) continue
            val i = r * cols + c
            cells[i] = RIVET
            // Reject enclosed pockets, including diagonals that seal off a chamber.
            val seen = mutableSetOf<Int>()
            val queue = java.util.ArrayDeque<Int>().apply { add(0) }
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (!seen.add(next)) continue
                for (j in neighbours(next)) if (cells[j] != RIVET && j !in seen) queue.add(j)
            }
            if (seen.size != cells.count { it != RIVET }) cells[i] = EMPTY
        }
    }

    fun scoreBurst(burst: Burst, cascade: Int) {
        score += (burst.cleared.size * 10L + burst.beams.size * 40L + burst.bombs.size * 100L) * cascade.coerceIn(1, 5)
    }

    fun completeStage() {
        if (won && !stageRewarded) { score += 100L + moves * 15L; stageRewarded = true }
    }

    /** Refill only metals: neither cores nor rivets move during a reshuffle. */
    private fun generateMetals() {
        for (i in cells.indices) {
            if (cells[i] == RIVET || cells[i] == CORE) continue
            cells[i] = (0..4).filter { value ->
                !(i % cols >= 2 && cells[i - 1] == value && cells[i - 2] == value) &&
                    !(i >= cols * 2 && cells[i - cols] == value && cells[i - cols * 2] == value)
            }.random(random)
        }
    }

    fun swappable(a: Int, b: Int): Boolean = a in cells.indices && b in cells.indices &&
        cells[a] >= 0 && cells[b] >= 0 &&
        kotlin.math.abs(a % cols - b % cols) + kotlin.math.abs(a / cols - b / cols) == 1

    fun swap(a: Int, b: Int) {
        val temp = cells[a]; cells[a] = cells[b]; cells[b] = temp
        val edge = coreEdges[a]; coreEdges[a] = coreEdges[b]; coreEdges[b] = edge
    }

    fun matches(): Set<Int> = Match3Patterns(cells, cols).matches()

    fun burst(): Burst = Match3Patterns(cells, cols).burst()

    private fun neighbours(i: Int): List<Int> = buildList {
        if (i % cols > 0) add(i - 1)
        if (i % cols < cols - 1) add(i + 1)
        if (i >= cols) add(i - cols)
        if (i < cells.size - cols) add(i + cols)
    }

    fun clear(matches: Set<Int>, effect: Burst? = null) {
        for (i in matches) {
            if (cells[i] !in 0..4) continue // Cores and structural rivets survive all bonuses.
            if (kind == 0 && cells[i] == material) collected++
            if (dualRecipe && cells[i] == secondaryMaterial) secondaryCollected++
            if (kind == 1 && slag[i]) {
                val accepted = when (slagRules[i]) {
                    1 -> effect?.beams?.any { if (it.horizontal) it.origin / cols == i / cols else it.origin % cols == i % cols } == true
                    2 -> effect?.bombs?.any { origin ->
                        val dx = origin % cols - i % cols; val dy = origin / cols - i / cols
                        dx * dx + dy * dy <= BLAST_RADIUS * BLAST_RADIUS
                    } == true
                    else -> true
                }
                if (accepted) {
                    slagLayers[i]--
                    if (slagLayers[i] <= 0) { slag[i] = false; collected++; if (slagEndless) score += 25 }
                }
            }
            cells[i] = EMPTY
        }
    }

    /** Rivets split each gravity lane into independent chambers. */
    fun collapse(): IntArray {
        val source = IntArray(cells.size) { it }
        spawned.fill(false)
        val vertical = gravity == 0 || gravity == 3
        repeat(if (vertical) cols else rows) { lane ->
            val line = when (gravity) {
                0 -> (rows - 1 downTo 0).map { it * cols + lane }
                3 -> (0 until rows).map { it * cols + lane }
                1 -> (0 until cols).map { lane * cols + it }
                else -> (cols - 1 downTo 0).map { lane * cols + it }
            }
            val segment = mutableListOf<Int>()
            fun fill(openEnd: Boolean) {
                if (segment.isEmpty()) return
                val kept = segment.filter { cells[it] >= 0 }.map { Triple(it, cells[it], coreEdges[it]) }
                for ((position, destination) in segment.withIndex()) {
                    if (position < kept.size) {
                        source[destination] = kept[position].first; cells[destination] = kept[position].second
                        coreEdges[destination] = kept[position].third
                    } else {
                        cells[destination] = random.nextInt(5); spawned[destination] = true
                        coreEdges[destination] = -1
                        source[destination] = if (openEnd) -1 - (position - kept.size) else segment.last()
                    }
                }
                segment.clear()
            }
            for (i in line) {
                if (cells[i] == RIVET) fill(false) else segment.add(i)
            }
            fill(true)
        }
        return source
    }

    fun exitingCores(): Set<Int> = if (kind != 2) emptySet() else cells.indices.filter { i ->
        cells[i] == CORE && when (coreEdges[i]) {
            1 -> i % cols == 0
            2 -> i % cols == cols - 1
            3 -> i < cols
            else -> i >= (rows - 1) * cols
        }
    }.toSet()

    fun deliver(): Boolean {
        val exiting = exitingCores()
        for (i in exiting) { collected++; score += 250; cells[i] = EMPTY; coreEdges[i] = -1 }
        return exiting.isNotEmpty()
    }

    private fun hasMove(): Boolean {
        for (i in cells.indices) for (j in intArrayOf(i + 1, i + cols)) {
            if (!swappable(i, j)) continue
            swap(i, j); val found = matches().isNotEmpty(); swap(i, j)
            if (found) return true
        }
        return false
    }

    fun ensurePlayable(): Boolean {
        if (hasMove()) return false
        repeat(100) { generateMetals(); if (hasMove()) return true }
        // Our generated boards keep their outer two rows free of rivets. Find a
        // metal-only 2x3 patch, then plant a swap without disturbing cores/obstacles.
        for (r in 0 until rows - 1) for (c in 0 until cols - 2) {
            val a = r * cols + c
            val patch = listOf(a, a + 1, a + 2, a + cols, a + cols + 1, a + cols + 2)
            if (patch.any { cells[it] !in 0..4 }) continue
            for (v in 0..4) for (other in 0..4) {
                if (v == other) continue
                val old = patch.map { cells[it] }
                cells[a] = v; cells[a + 1] = other; cells[a + 2] = v; cells[a + cols + 1] = v
                if (matches().isEmpty() && hasMove()) return true
                patch.forEachIndexed { index, cell -> cells[cell] = old[index] }
            }
        }
        return true
    }

    fun save(): String = JSONObject().apply {
        put("cols", cols); put("rows", rows); put("seed", seed); put("stage", stage); put("moves", moves)
        put("endless", endless); put("score", score); put("elapsedMs", elapsedMs)
        put("roundLimit", roundLimit)
        put("slagEndless", slagEndless); put("slagGoal", slagGoal)
        put("slagLayers", JSONArray(slagLayers.toList())); put("slagRules", JSONArray(slagRules.toList()))
        put("stageStartScore", stageStartScore); put("stageRewarded", stageRewarded)
        put("collected", collected); put("gravity", gravity); put("deliveryEdge", deliveryEdge)
        put("secondaryCollected", secondaryCollected); put("dualRecipe", dualRecipe); put("coreGoal", coreGoal)
        put("cells", JSONArray(cells.toList())); put("slag", JSONArray(slag.toList()))
        put("coreEdges", JSONArray(coreEdges.toList()))
    }.toString()

    fun restore(json: JSONObject) {
        val width = json.optInt("cols", cols); val height = json.optInt("rows", rows)
        if (width != cols || height != rows) resize(width, height)
        stage = json.getInt("stage"); moves = json.getInt("moves")
        roundLimit = json.optInt("roundLimit", 6).takeIf { it in listOf(3, 6, 12) } ?: 6
        score = json.optLong("score", 0L); elapsedMs = json.optLong("elapsedMs", 0L)
        stageStartScore = json.optLong("stageStartScore", score); stageRewarded = json.optBoolean("stageRewarded", false)
        collected = json.getInt("collected"); gravity = json.getInt("gravity")
        deliveryEdge = json.optInt("deliveryEdge", 0)
        secondaryCollected = json.optInt("secondaryCollected", 0)
        dualRecipe = json.optBoolean("dualRecipe", false); coreGoal = json.optInt("coreGoal", 1)
        for (i in cells.indices) { cells[i] = json.getJSONArray("cells").getInt(i); slag[i] = json.getJSONArray("slag").getBoolean(i) }
        slagGoal = json.optInt("slagGoal", 12 + stage)
        for (i in cells.indices) {
            slagLayers[i] = if (slag[i]) (json.optJSONArray("slagLayers")?.optInt(i, 1) ?: 1).coerceIn(1, 3) else 0
            slagRules[i] = (json.optJSONArray("slagRules")?.optInt(i, 0) ?: 0).coerceIn(0, 2)
        }
        val savedEdges = json.optJSONArray("coreEdges")
        for (i in cells.indices) coreEdges[i] = if (cells[i] == CORE)
            (savedEdges?.optInt(i, deliveryEdge) ?: deliveryEdge).coerceIn(0, 3) else -1
        random = Random(seed xor (stage * 7919) xor moves)
    }
}
