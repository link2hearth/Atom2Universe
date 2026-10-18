package com.Atom2Universe.app.games.reflex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Règles de Réflexes : jugement des appuis, vies, et indépendance à la cadence d'images. */
class ReflexGameTest {

    private fun newGame(seed: Long = 42L) = ReflexGame(seed).apply {
        setField(0f, 0f, 1080f, 1800f, 2.75f)
        start()
    }

    /** Avance jusqu'à la naissance d'une particule et la renvoie. */
    private fun ReflexGame.nextParticle(): Particle {
        repeat(600) {
            particles.firstOrNull { it.alive }?.let { return it }
            update(1f / 60f)
        }
        error("aucune particule n'est apparue")
    }

    @Test
    fun tapOnTheRingIsPerfect() {
        val g = newGame()
        val p = g.nextParticle()
        g.update(p.hitAt - g.time)
        g.tap(p.x, p.y)
        assertEquals(1, g.perfects)
        assertEquals(1, g.combo)
        assertEquals(HitGrade.PERFECT.basePoints.toLong(), g.score)
        assertEquals(ReflexGame.START_LIVES, g.lives)
    }

    @Test
    fun tooEarlyBreaksTheCombo() {
        val g = newGame()
        val p1 = g.nextParticle()
        g.update(p1.hitAt - g.time)
        g.tap(p1.x, p1.y)
        assertEquals(1, g.combo)

        val p2 = g.nextParticle()
        g.tap(p2.x, p2.y)   // tout juste né : bien trop tôt
        assertEquals(0, g.combo)
        assertEquals(ReflexGame.START_LIVES, g.lives)
    }

    @Test
    fun theFingerTimeCountsNotTheFrameTime() {
        val g = newGame()
        val p = g.nextParticle()
        // L'image arrive 50 ms après le « bon moment », mais le doigt, lui, était à l'heure.
        g.update(p.hitAt - g.time + 0.05f)
        g.tap(p.x, p.y, secondsAgo = 0.05f)
        assertEquals(1, g.perfects)
    }

    @Test
    fun missesCostLivesUntilGameOver() {
        val g = newGame()
        var guard = 0
        while (g.phase == ReflexPhase.PLAYING && guard++ < 60 * 120) g.update(1f / 60f)
        assertEquals(ReflexPhase.GAME_OVER, g.phase)
        assertEquals(0, g.lives)
        assertEquals(ReflexGame.START_LIVES, g.misses)
    }

    @Test
    fun touchingAntimatterCostsALife() {
        val g = newGame()
        val anti = Particle(ParticleKind.ANTI, 500f, 900f, 100f, g.time, g.time + 1f)
        g.particles += anti
        g.tap(500f, 900f)
        assertEquals(ReflexGame.START_LIVES - 1, g.lives)
        assertTrue(!anti.alive)
    }

    @Test
    fun chainMustBeTappedInOrder() {
        val g = newGame()
        val first = Particle(ParticleKind.CHAIN, 200f, 900f, 60f, g.time, g.time + 1f, chainId = 99, chainIndex = 1)
        val second = Particle(ParticleKind.CHAIN, 700f, 900f, 60f, g.time, g.time + 1.4f, chainId = 99, chainIndex = 2)
        g.particles += first
        g.particles += second
        g.tap(700f, 900f)
        assertTrue("le 2 ne part pas avant le 1", second.alive)
        g.update(1f - g.time)
        g.tap(200f, 900f)
        assertTrue(!first.alive)
        assertEquals(ReflexGame.START_LIVES, g.lives)
    }

    @Test
    fun sameGameAt60And120Fps() {
        // Sans aucun appui, la partie doit durer le même temps quelle que soit la cadence.
        fun duration(fps: Int): Float {
            val g = newGame(7L)
            var guard = 0
            while (g.phase == ReflexPhase.PLAYING && guard++ < fps * 120) g.update(1f / fps)
            return g.time
        }
        val d60 = duration(60)
        val d120 = duration(120)
        assertTrue("60 Hz : $d60 s, 120 Hz : $d120 s", abs(d60 - d120) < 0.1f)
    }
}
