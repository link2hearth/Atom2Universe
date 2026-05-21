package com.Atom2Universe.app.games.hexrunner

import kotlin.random.Random

class HexRunnerGame {
    companion object {
        const val NUM_FACES = 6
        const val NUM_RINGS = 12
        const val ROTATION_MS = 180L
        const val BASE_SPEED = 0.0008f // rings per millisecond (~1.25 s/ring at start)
        private const val MAX_STREAK = 3 // max consecutive rings a face stays solid
    }

    class Ring {
        val solid = BooleanArray(NUM_FACES)
    }

    var playerFaceIndex = 0; private set
    var rotationFrom = 0; private set
    var rotationProgress = 1f; private set

    private val rings = ArrayDeque<Ring>()
    var scrollProgress = 0f; private set
    val ringCount get() = rings.size

    var score = 0L; private set
    var isGameOver = false; private set
    var isRunning = false; private set
    var bestScore = 0L; private set

    // Per-face consecutive-solid streak counter — used to prevent same pattern sticking too long
    private val faceStreaks = IntArray(NUM_FACES)

    /** Snapshot of rings that were successfully crossed — consumed by HexRunnerView for the fly-out animation. */
    class PassedRingSnapshot(val solid: BooleanArray)
    val justPassedRings = ArrayDeque<PassedRingSnapshot>(4)

    fun getRing(i: Int): Ring = rings[i]

    fun initBestScore(saved: Long) {
        if (saved > bestScore) bestScore = saved
    }

    /** True when the player is currently on a gap face that will kill them on the next ring pass. */
    val isDanger: Boolean get() = rings.isNotEmpty() && !rings[0].solid[playerFaceIndex]

    private fun speed() = BASE_SPEED * (1f + score / 90_000f)

    fun start() {
        score = 0L
        scrollProgress = 0f
        rotationProgress = 1f
        playerFaceIndex = 0
        rotationFrom = 0
        isGameOver = false
        isRunning = true
        faceStreaks.fill(0)
        justPassedRings.clear()
        rings.clear()
        repeat(NUM_RINGS) { i ->
            val r = Ring()
            if (i < 6) r.solid.fill(true) else fillRandom(r, grace = true)
            rings.addLast(r)
        }
    }

    fun update(deltaMs: Long) {
        if (!isRunning || isGameOver) return
        score += deltaMs
        scrollProgress += speed() * deltaMs

        while (scrollProgress >= 1f) {
            scrollProgress -= 1f
            val front = rings.removeFirst()
            if (!front.solid[playerFaceIndex]) {
                isGameOver = true
                isRunning = false
                if (score > bestScore) bestScore = score
                return
            }
            // Expose the crossed ring for the fly-out animation (max 3 buffered)
            justPassedRings.addLast(PassedRingSnapshot(front.solid.copyOf()))
            if (justPassedRings.size > 3) justPassedRings.removeFirst()
            val next = Ring()
            fillRandom(next, grace = false)
            // Guarantee at least one reachable face (within 1 rotation) is solid
            val adj = setOf(
                playerFaceIndex,
                (playerFaceIndex + 1) % NUM_FACES,
                (playerFaceIndex + NUM_FACES - 1) % NUM_FACES
            )
            if (adj.none { next.solid[it] }) next.solid[adj.random()] = true
            rings.addLast(next)
        }

        if (rotationProgress < 1f) {
            rotationProgress = (rotationProgress + deltaMs.toFloat() / ROTATION_MS).coerceAtMost(1f)
        }
    }

    private fun fillRandom(ring: Ring, grace: Boolean) {
        ring.solid.fill(false)

        val minSolid = when {
            grace || score < 12_000L -> 4
            score < 35_000L -> 3
            else -> 2
        }

        // Faces that have been solid too many times in a row are temporarily blocked
        val blocked: Set<Int> = if (grace) emptySet()
            else (0 until NUM_FACES).filter { faceStreaks[it] >= MAX_STREAK }.toSet()

        // Prefer faces that are "due" for a change (lower streak = higher weight)
        val candidates = (0 until NUM_FACES).sortedBy { faceStreaks[it] }.toMutableList()
        // Remove blocked faces but keep them as fallback if we can't reach minSolid otherwise
        val preferred = candidates.filter { it !in blocked }
        val fallback  = candidates.filter { it in blocked }

        val count = Random.nextInt(minSolid, NUM_FACES)

        // Fill from preferred first, then fallback only if needed
        val pool = (preferred.shuffled() + fallback).take(NUM_FACES)
        pool.take(count).forEach { ring.solid[it] = true }

        // Update streaks
        for (f in 0 until NUM_FACES) {
            faceStreaks[f] = if (ring.solid[f]) faceStreaks[f] + 1 else 0
        }
    }

    fun rotateLeft() {
        if (rotationProgress < 1f) return
        rotationFrom = playerFaceIndex
        playerFaceIndex = (playerFaceIndex + 1) % NUM_FACES
        rotationProgress = 0f
    }

    fun rotateRight() {
        if (rotationProgress < 1f) return
        rotationFrom = playerFaceIndex
        playerFaceIndex = (playerFaceIndex + NUM_FACES - 1) % NUM_FACES
        rotationProgress = 0f
    }
}
