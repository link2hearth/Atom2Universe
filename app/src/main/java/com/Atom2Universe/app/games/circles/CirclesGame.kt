package com.Atom2Universe.app.games.circles

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.random.Random

enum class CirclesDifficulty(
    val label: String,
    val ringCounts: IntArray,
    val shuffleMin: Int,
    val shuffleMax: Int
) {
    EASY("Easy", intArrayOf(3), 6, 12),
    MEDIUM("Medium", intArrayOf(4), 10, 18),
    HARD("Hard", intArrayOf(5, 6), 18, 32)
}

class CirclesGame {

    companion object {
        const val SEGMENTS = 6
        val COLORS: IntArray by lazy {
            intArrayOf(
                Color.parseColor("#e74c3c"),
                Color.parseColor("#3498db"),
                Color.parseColor("#f1c40f"),
                Color.parseColor("#2ecc71"),
                Color.parseColor("#9b59b6"),
                Color.parseColor("#e67e22")
            )
        }
        val COLOR_OFFSETS = intArrayOf(0, 3, 2, 5, 1, 4)

        fun getNeutrinos(difficulty: CirclesDifficulty, ringCount: Int): Int = when (difficulty) {
            CirclesDifficulty.EASY   -> 1
            CirclesDifficulty.MEDIUM -> 2
            CirclesDifficulty.HARD   -> if (ringCount >= 6) 4 else 3
        }
    }

    data class MoveHint(val ringIndex: Int, val direction: Int)
    data class SolutionEntry(val distance: Int, val bestMove: MoveHint?)

    var difficulty = CirclesDifficulty.EASY
    var ringCount = 3
    var rings: Array<IntArray> = emptyArray()
    var rotations = IntArray(0)
    var initialRotations = IntArray(0)
    var rotationLinks = IntArray(0)
    var seed = ""
    var moves = 0
    var solved = false
    var rewardClaimed = false
    var hintUsed = false

    fun newGame(diff: CirclesDifficulty, rng: Random = Random.Default) {
        difficulty = diff
        ringCount = pickRingCount(diff, rng)
        rings = createRings(ringCount)
        rotationLinks = generateLinks(ringCount, rng)

        val shuffleMoves = rng.nextInt(diff.shuffleMin, diff.shuffleMax + 1)
        val base = solvedRotations()
        var attempts = 0
        do {
            rotations = scramble(ringCount, rotationLinks, shuffleMoves, base.copyOf(), rng)
            attempts++
        } while (checkSolved() && attempts < 32)

        initialRotations = rotations.copyOf()
        seed = buildSeed(8, rng)
        moves = 0
        solved = checkSolved()
        rewardClaimed = false
        hintUsed = false
    }

    fun restart() {
        rotations = initialRotations.copyOf()
        moves = 0
        solved = checkSolved()
        rewardClaimed = false
        hintUsed = false
    }

    fun rotateRing(index: Int, direction: Int): IntArray {
        if (solved || index < 0 || index >= ringCount) return IntArray(0)
        val affected = applyRotation(rotations, index, if (direction > 0) 1 else -1)
        moves++
        solved = checkSolved()
        return affected
    }

    fun checkSolved(): Boolean {
        if (rings.isEmpty() || rotations.size != ringCount) return false
        for (s in 0 until SEGMENTS) {
            val expected = rings[0][((s - rotations[0]) % SEGMENTS + SEGMENTS) % SEGMENTS]
            for (i in 1 until ringCount) {
                if (rings[i][((s - rotations[i]) % SEGMENTS + SEGMENTS) % SEGMENTS] != expected) return false
            }
        }
        return true
    }

    fun solvedRotations(): IntArray =
        IntArray(ringCount) { i -> COLOR_OFFSETS.getOrElse(i) { 0 } % SEGMENTS }

    fun buildSolutionMap(): Map<String, SolutionEntry> {
        val start = solvedRotations()
        val startKey = start.toKey()
        val queue = ArrayDeque<IntArray>()
        queue.add(start)
        val visited = HashMap<String, SolutionEntry>(64)
        visited[startKey] = SolutionEntry(0, null)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val info = visited[current.toKey()]!!
            for (ri in 0 until ringCount) {
                for (dir in intArrayOf(-1, 1)) {
                    val next = current.copyOf()
                    applyRotationToArray(next, ri, dir)
                    val key = next.toKey()
                    if (!visited.containsKey(key)) {
                        visited[key] = SolutionEntry(info.distance + 1, MoveHint(ri, -dir))
                        queue.add(next)
                    }
                }
            }
        }
        return visited
    }

    fun currentKey(): String = rotations.toKey()

    // ── Internal helpers ──────────────────────────────────────────────────────────

    private fun applyRotation(rots: IntArray, index: Int, dir: Int): IntArray {
        rots[index] = (rots[index] + dir + SEGMENTS * 2) % SEGMENTS
        val linked = rotationLinks.getOrElse(index) { -1 }
        return if (linked >= 0 && linked != index && linked < rots.size) {
            rots[linked] = (rots[linked] + dir + SEGMENTS * 2) % SEGMENTS
            intArrayOf(index, linked)
        } else {
            intArrayOf(index)
        }
    }

    private fun applyRotationToArray(rots: IntArray, index: Int, dir: Int) {
        rots[index] = (rots[index] + dir + SEGMENTS * 2) % SEGMENTS
        val linked = rotationLinks.getOrElse(index) { -1 }
        if (linked >= 0 && linked != index && linked < rots.size) {
            rots[linked] = (rots[linked] + dir + SEGMENTS * 2) % SEGMENTS
        }
    }

    private fun createRings(count: Int): Array<IntArray> =
        Array(count) { i ->
            val offset = COLOR_OFFSETS.getOrElse(i) { 0 }
            IntArray(SEGMENTS) { s -> COLORS[(s + offset) % SEGMENTS] }
        }

    private fun pickRingCount(diff: CirclesDifficulty, rng: Random): Int {
        val opts = diff.ringCounts
        return if (opts.size == 1) opts[0] else opts[rng.nextInt(opts.size)]
    }

    private fun generateLinks(count: Int, rng: Random): IntArray {
        val indices = IntArray(count) { it }
        for (i in count - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = indices[i]; indices[i] = indices[j]; indices[j] = tmp
        }
        val links = IntArray(count)
        for (i in 0 until count) {
            links[indices[i]] = indices[(i + 1) % count]
        }
        return links
    }

    private fun scramble(count: Int, links: IntArray, moveCount: Int, rots: IntArray, rng: Random): IntArray {
        repeat(moveCount) {
            val ri = rng.nextInt(count)
            val dir = if (rng.nextBoolean()) 1 else -1
            rots[ri] = (rots[ri] + dir + SEGMENTS * 2) % SEGMENTS
            val linked = links.getOrElse(ri) { -1 }
            if (linked >= 0 && linked != ri && linked < rots.size) {
                rots[linked] = (rots[linked] + dir + SEGMENTS * 2) % SEGMENTS
            }
        }
        return rots
    }

    private fun buildSeed(length: Int, rng: Random): String {
        val alpha = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return String(CharArray(length) { alpha[rng.nextInt(alpha.length)] })
    }

    private fun IntArray.toKey() = joinToString(",")

    // ── Serialization ─────────────────────────────────────────────────────────────

    fun serialize(): String = JSONObject().apply {
        put("version", 2)
        put("difficulty", difficulty.name)
        put("ringCount", ringCount)
        put("seed", seed)
        put("moves", moves)
        put("solved", solved)
        put("rewardClaimed", rewardClaimed)
        put("hintUsed", hintUsed)
        put("rotations", JSONArray(rotations.toTypedArray()))
        put("initialRotations", JSONArray(initialRotations.toTypedArray()))
        put("rotationLinks", JSONArray(rotationLinks.toTypedArray()))
    }.toString()

    fun deserialize(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            if (obj.optInt("version") != 2) return false
            val diff = CirclesDifficulty.valueOf(obj.getString("difficulty"))
            val rc = obj.getInt("ringCount").takeIf { it in 3..12 } ?: return false
            difficulty = diff
            ringCount = rc
            rings = createRings(ringCount)
            seed = obj.optString("seed", "")
            moves = obj.optInt("moves", 0)
            solved = obj.optBoolean("solved", false)
            rewardClaimed = obj.optBoolean("rewardClaimed", false)
            hintUsed = obj.optBoolean("hintUsed", false)
            val rotsArr = obj.getJSONArray("rotations")
            val initArr = obj.getJSONArray("initialRotations")
            val linksArr = obj.getJSONArray("rotationLinks")
            if (rotsArr.length() < rc || initArr.length() < rc || linksArr.length() < rc) return false
            rotations = IntArray(rc) { i -> ((rotsArr.getInt(i) % SEGMENTS) + SEGMENTS) % SEGMENTS }
            initialRotations = IntArray(rc) { i -> ((initArr.getInt(i) % SEGMENTS) + SEGMENTS) % SEGMENTS }
            rotationLinks = IntArray(rc) { i -> linksArr.getInt(i) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
