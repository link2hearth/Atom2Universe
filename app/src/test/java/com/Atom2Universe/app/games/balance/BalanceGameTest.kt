package com.Atom2Universe.app.games.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Vérifie les règles du jeu d'équilibre : les niveaux tirés au sort doivent
 * admettre une répartition parfaite *posable* (sans chevauchement), une bonne
 * répartition doit être acceptée, une mauvaise refusée.
 */
class BalanceGameTest {

    // ── Utilitaires ───────────────────────────────────────────────────────────

    /**
     * Une rangée de briques serrées les unes contre les autres d'un côté du
     * pivot, repérée par [d], la distance du pivot au bord intérieur de la
     * première brique. Faire glisser toute la rangée fait varier le couple de
     * façon linéaire : couple = masseTotale × d + [base].
     */
    private class Row(val idx: List<Int>, val offsets: FloatArray, val base: Float, val mass: Float) {
        var dMin = 0f
        var dMax = 0f
        val tMin get() = mass * dMin + base
        val tMax get() = mass * dMax + base
        fun dFor(t: Float) = (t - base) / mass
    }

    /** Construit la rangée correspondant à un ordre donné, ou null si elle ne tient pas. */
    private fun buildRow(ws: List<BalanceWeight>, order: List<Int>): Row? {
        val gap = 0.02f
        val offsets = FloatArray(order.size)
        var cursor = 0f
        var base = 0f
        var mass = 0f
        for ((k, i) in order.withIndex()) {
            val hw = ws[i].body.halfW
            offsets[k] = cursor + hw
            cursor = offsets[k] + hw + gap
            base += ws[i].mass * offsets[k]
            mass += ws[i].mass
        }
        val outerExtent = cursor - gap
        val row = Row(order, offsets, base, mass)
        row.dMin = BalanceRules.DEAD_HALF
        row.dMax = BalanceRules.PLANK_HALF_LENGTH - outerExtent
        return if (row.dMax < row.dMin) null else row
    }

    /**
     * Cherche une disposition exacte et réalisable : les poids répartis en deux
     * rangées serrées, une de chaque côté, avec un couple total exactement nul.
     * C'est la façon dont un joueur s'y prend, et ça garantit qu'aucune brique
     * n'en chevauche une autre.
     */
    private fun solveLayout(game: BalanceGame): FloatArray? {
        val ws = game.weights
        val n = ws.size

        for (mask in 1 until (1 shl n) - 1) {
            val leftIdx = (0 until n).filter { (mask shr it) and 1 == 1 }
            val rightIdx = (0 until n).filter { (mask shr it) and 1 == 0 }
            // Deux ordres par rangée : lourds à l'intérieur, ou lourds à l'extérieur.
            for (leftOrder in orderings(ws, leftIdx)) {
                val leftRow = buildRow(ws, leftOrder) ?: continue
                for (rightOrder in orderings(ws, rightIdx)) {
                    val rightRow = buildRow(ws, rightOrder) ?: continue
                    val from = maxOf(leftRow.tMin, rightRow.tMin)
                    val to = minOf(leftRow.tMax, rightRow.tMax)
                    if (to < from) continue

                    val target = (from + to) / 2f
                    val x = FloatArray(n)
                    apply(ws, leftRow, target, -1f, x)
                    apply(ws, rightRow, target, 1f, x)
                    return x
                }
            }
        }
        return null
    }

    private fun orderings(ws: List<BalanceWeight>, idx: List<Int>): List<List<Int>> {
        if (idx.size < 2) return listOf(idx)
        return listOf(
            idx.sortedByDescending { ws[it].mass },
            idx.sortedBy { ws[it].mass }
        )
    }

    private fun apply(ws: List<BalanceWeight>, row: Row, target: Float, side: Float, out: FloatArray) {
        val d = row.dFor(target).coerceIn(row.dMin, row.dMax)
        for ((k, i) in row.idx.withIndex()) out[i] = side * (d + row.offsets[k])
    }

    /** Pose un poids à une abscisse en passant par le vrai glisser-déposer. */
    private fun BalanceGame.put(w: BalanceWeight, x: Float) {
        beginDrag(w, w.body.x, w.body.y)
        dragTo(x, BalanceRules.PLANK_TOP + 0.45f)
        endDrag()
    }

    private fun BalanceGame.simulate(seconds: Float) {
        val dt = 1f / 120f
        repeat((seconds / dt).toInt()) { step(dt) }
    }

    private fun newGame(diff: BalanceGame.Difficulty): BalanceGame {
        val game = BalanceGame()
        game.setViewport(8f)
        game.newLevel(diff)
        return game
    }

    /** Installe la solution exacte trouvée par [solveLayout] et laisse tout se caler. */
    private fun layoutSolution(game: BalanceGame): Boolean {
        val x = solveLayout(game) ?: return false
        for ((i, w) in game.weights.withIndex()) game.put(w, x[i])
        game.simulate(1.5f)
        return true
    }

    // ── Génération ────────────────────────────────────────────────────────────

    @Test
    fun `chaque niveau admet une repartition exacte et posable`() {
        for (diff in BalanceGame.Difficulty.entries) {
            repeat(25) {
                val game = newGame(diff)
                assertEquals(diff.weightCount, game.weights.size)
                assertNotNull(
                    "niveau sans disposition réalisable : ${game.weights.map { w -> w.mass }}",
                    solveLayout(game)
                )
            }
        }
    }

    @Test
    fun `les masses d un niveau sont toutes differentes`() {
        repeat(25) {
            val game = newGame(BalanceGame.Difficulty.MEDIUM)
            val masses = game.weights.map { it.mass }
            assertEquals("masses en double : $masses", masses.size, masses.toSet().size)
        }
    }

    @Test
    fun `une brique est d autant plus grosse qu elle est lourde`() {
        val game = newGame(BalanceGame.Difficulty.HARD)
        val sorted = game.weights.sortedBy { it.mass }
        for (i in 1 until sorted.size) {
            val small = sorted[i - 1]
            val big = sorted[i]
            assertTrue(
                "la brique de ${big.mass} kg n'est pas plus grosse que celle de ${small.mass} kg",
                big.body.halfW * big.body.halfH > small.body.halfW * small.body.halfH
            )
        }
    }

    // ── Règles de pose ────────────────────────────────────────────────────────

    @Test
    fun `poser sur le pivot est refuse`() {
        val game = newGame(BalanceGame.Difficulty.EASY)
        val w = game.weights.first()
        game.beginDrag(w, w.body.x, w.body.y)
        game.dragTo(0f, BalanceRules.PLANK_TOP + 0.45f)
        val placed = game.endDrag()

        assertTrue("la pose sur le pivot a été acceptée", !placed)
        assertEquals(BalanceGame.Notice.DEAD_ZONE, game.notice)
        assertTrue("le poids n'est pas revenu au plateau", !w.placed)
    }

    @Test
    fun `on pose librement, sans aimantation`() {
        val game = newGame(BalanceGame.Difficulty.EASY)
        val w = game.weights.first()
        val target = 0.93f
        game.put(w, target)
        game.simulate(1f)

        assertTrue("le poids n'est pas posé", w.placed)
        assertEquals("l'abscisse a été modifiée", target, w.body.x, 0.01f)
        val expected = BalanceRules.PLANK_TOP + w.body.halfH
        assertTrue(
            "le poids ne repose pas sur la planche : y=${w.body.y} (attendu ~$expected)",
            abs(w.body.y - expected) < 0.03f
        )
    }

    @Test
    fun `une brique peut depasser du bord en restant suffisamment soutenue`() {
        val game = newGame(BalanceGame.Difficulty.EASY)
        val w = game.weights.first()
        game.put(w, BalanceRules.PLANK_HALF_LENGTH + 0.3f)
        game.simulate(1f)

        assertTrue("le poids n'est pas posé", w.placed)
        val overlap = BalanceRules.PLANK_HALF_LENGTH - (w.body.x - w.body.halfW)
        val minOverlap = 2f * w.body.halfW * BalanceRules.MIN_SUPPORT_FRACTION
        assertTrue("la brique n'est pas assez soutenue : $overlap < $minOverlap", overlap + 0.001f >= minOverlap)
    }

    @Test
    fun `le test n est possible qu une fois tous les poids poses`() {
        val game = newGame(BalanceGame.Difficulty.EASY)
        assertTrue("test autorisé alors que rien n'est posé", !game.canTest())
        game.put(game.weights[0], 0.6f)
        assertTrue("test autorisé avec un seul poids posé", !game.canTest())
    }

    // ── Verdict de la balance ─────────────────────────────────────────────────

    @Test
    fun `une repartition equilibree est validee`() {
        for (diff in BalanceGame.Difficulty.entries) {
            repeat(5) {
                val game = newGame(diff)
                assertTrue("aucune disposition trouvée", layoutSolution(game))
                assertTrue("tous les poids devraient être posés", game.canTest())
                // Tolérance : les briques bougent d'un cheveu en se calant.
                assertEquals(0f, game.residualTorque, 0.08f)

                game.startTest()
                game.simulate(10f)
                assertEquals(
                    "solution exacte refusée (${diff.name}, inclinaison ${game.verdictLeanDeg}°, " +
                        "motif ${game.notice})",
                    BalanceGame.Phase.WON, game.phase
                )
            }
        }
    }

    @Test
    fun `une repartition desequilibree est refusee`() {
        val game = newGame(BalanceGame.Difficulty.EASY)
        // Tout du même côté : la planche doit franchement pencher.
        var x = 0.4f
        for (w in game.weights) {
            game.put(w, x + w.body.halfW)
            x += 2f * w.body.halfW + 0.04f
        }
        game.simulate(1.5f)
        game.startTest()
        game.simulate(10f)

        assertEquals(BalanceGame.Phase.LOST, game.phase)
        assertTrue(
            "la planche n'a pas penché : ${game.verdictLeanDeg}°",
            abs(game.verdictLeanDeg) > game.difficulty.toleranceDeg
        )
    }

    @Test
    fun `l inclinaison prevue correspond a l inclinaison mesuree`() {
        val game = newGame(BalanceGame.Difficulty.MEDIUM)
        assertTrue("aucune disposition trouvée", layoutSolution(game))
        // Solution exacte, puis on décale légèrement le poids le plus léger :
        // le déséquilibre reste petit, donc rien ne glisse et la prévision vaut.
        val lightest = game.weights.minByOrNull { it.mass }!!
        val side = if (lightest.body.x < 0f) -1f else 1f
        val room = lightest.maxDistance - abs(lightest.body.x)
        val shift = if (room > 0.2f) 0.2f else -0.2f
        game.put(lightest, lightest.body.x + side * shift)
        game.simulate(1.5f)

        val predicted = game.predictedLeanDeg
        assertTrue("le déséquilibre du test est nul", abs(predicted) > 0.4f)

        game.startTest()
        game.simulate(12f)

        // On compare à l'inclinaison figée au verdict : après coup le levier
        // continue de vivre (rebonds, glissements).
        assertTrue(
            "prévision $predicted° contre mesure ${game.verdictLeanDeg}°",
            abs(predicted - game.verdictLeanDeg) < 1.5f
        )
    }

    @Test
    fun `le levier ne s effondre pas apres le verdict`() {
        val game = newGame(BalanceGame.Difficulty.EASY)
        assertTrue("aucune disposition trouvée", layoutSolution(game))
        game.startTest()
        game.simulate(10f)
        assertEquals(BalanceGame.Phase.WON, game.phase)

        // Encore dix secondes après la victoire : la planche doit tenir.
        game.simulate(10f)
        assertTrue(
            "le levier est parti au sol après la victoire : ${game.leanDeg}°",
            abs(game.leanDeg) < game.difficulty.toleranceDeg + 1f
        )
    }

    @Test
    fun `interrompre un test en cours ramene les poids a leur place choisie`() {
        val game = newGame(BalanceGame.Difficulty.MEDIUM)
        assertTrue("aucune disposition trouvée", layoutSolution(game))
        // Déséquilibre volontaire : la planche penche pendant le test, donc les
        // briques se déplacent réellement avant qu'on interrompe.
        val lightest = game.weights.minByOrNull { it.mass }!!
        val side = if (lightest.body.x < 0f) -1f else 1f
        val room = lightest.maxDistance - abs(lightest.body.x)
        game.put(lightest, lightest.body.x + side * if (room > 0.3f) 0.3f else -0.3f)
        game.simulate(1.5f)
        val before = game.weights.map { it.body.x }

        game.startTest()
        game.simulate(1.2f)
        assertTrue("la planche n'a pas bougé pendant le test", abs(game.leanDeg) > 0.3f)

        game.resumePlacing()
        game.simulate(0.5f)

        assertEquals(BalanceGame.Phase.PLACING, game.phase)
        assertEquals("la planche n'est pas revenue à plat", 0f, game.leanDeg, 0.01f)
        assertTrue("des poids ont été renvoyés au plateau", game.allPlaced)
        for ((i, w) in game.weights.withIndex()) {
            assertEquals("le poids ${w.mass} kg n'a pas retrouvé sa place", before[i], w.body.x, 0.02f)
        }
    }

    @Test
    fun `les essais sont comptes, le test gagnant excepte`() {
        val game = newGame(BalanceGame.Difficulty.EASY)
        assertTrue("aucune disposition trouvée", layoutSolution(game))
        assertEquals(0, game.testAttempts)
        assertEquals(0, game.failedAttempts)

        // Un test lancé puis interrompu compte comme un essai raté.
        game.startTest()
        game.simulate(0.5f)
        game.resumePlacing()
        assertEquals(1, game.testAttempts)
        assertEquals(1, game.failedAttempts)

        game.startTest()
        game.simulate(10f)
        assertEquals(BalanceGame.Phase.WON, game.phase)
        assertEquals(2, game.testAttempts)
        assertEquals("le test gagnant ne doit pas être décompté", 1, game.failedAttempts)
    }

    @Test
    fun `ajuster garde les poids en place et remet la planche a plat`() {
        val game = newGame(BalanceGame.Difficulty.EASY)
        assertTrue("aucune disposition trouvée", layoutSolution(game))
        val before = game.weights.map { it.body.x }

        game.startTest()
        game.simulate(10f)
        assertTrue("aucun verdict rendu", game.phase != BalanceGame.Phase.PLACING)

        // C'est le bouton « Ajuster » après un essai : on reprend la pose sans
        // renvoyer les briques au plateau.
        game.resumePlacing()
        game.simulate(1f)

        assertEquals(BalanceGame.Phase.PLACING, game.phase)
        assertEquals("la planche n'est pas revenue à plat", 0f, game.leanDeg, 0.01f)
        assertTrue("des poids ont été renvoyés au plateau", game.allPlaced)
        for ((i, w) in game.weights.withIndex()) {
            assertEquals("le poids ${w.mass} kg a bougé", before[i], w.body.x, 0.15f)
        }
    }
}
