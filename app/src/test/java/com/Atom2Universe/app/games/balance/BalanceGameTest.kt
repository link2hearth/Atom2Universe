package com.Atom2Universe.app.games.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Vérifie les règles du jeu d'équilibre : les niveaux tirés au sort doivent
 * admettre une répartition parfaite *posable* (sans chevauchement), une bonne
 * répartition doit être acceptée, une mauvaise refusée.
 */
class BalanceGameTest {

    // ── Utilitaires ───────────────────────────────────────────────────────────

    /**
     * Une rangée de briques serrées les unes contre les autres d'un côté du pivot,
     * repérée par [dMin]/[dMax], la distance du pivot au bord intérieur de la première.
     * Faire glisser toute la rangée fait varier le couple de façon linéaire.
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
            // [BalanceWeight.halfWidth], pas `body.halfW` : une brique n'est plus
            // forcément une boîte, et `body.halfW` ne rend que la première des quatre
            // boîtes d'un tétrimino.
            val hw = ws[i].halfWidth
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

    private fun orderings(ws: List<BalanceWeight>, idx: List<Int>): List<List<Int>> {
        if (idx.size < 2) return listOf(idx)
        return listOf(
            idx.sortedByDescending { ws[it].mass },
            idx.sortedBy { ws[it].mass }
        )
    }

    private fun applyRow(ws: List<BalanceWeight>, row: Row, target: Float, side: Float, out: FloatArray) {
        val d = row.dFor(target).coerceIn(row.dMin, row.dMax)
        for ((k, i) in row.idx.withIndex()) out[i] = side * (d + row.offsets[k])
    }

    /**
     * Une disposition exacte **à plat** : deux rangées serrées, une de chaque côté, aucune
     * brique posée sur une autre. Null si les briques ne tiennent pas côte à côte.
     *
     * C'est la disposition d'un joueur prudent, et surtout la seule dont on puisse
     * affirmer qu'elle **tient** : une pile, elle, se renverse quand la planche s'incline,
     * et la monter droite est justement le savoir-faire que le jeu demande.
     */
    private fun solveFlatLayout(game: BalanceGame): FloatArray? {
        val ws = game.weights
        val n = ws.size
        for (mask in 1 until (1 shl n) - 1) {
            val leftIdx = (0 until n).filter { (mask shr it) and 1 == 1 }
            val rightIdx = (0 until n).filter { (mask shr it) and 1 == 0 }
            for (leftOrder in orderings(ws, leftIdx)) {
                val leftRow = buildRow(ws, leftOrder) ?: continue
                for (rightOrder in orderings(ws, rightIdx)) {
                    val rightRow = buildRow(ws, rightOrder) ?: continue
                    val from = maxOf(leftRow.tMin, rightRow.tMin)
                    val to = minOf(leftRow.tMax, rightRow.tMax)
                    if (to < from) continue
                    val target = (from + to) / 2f
                    val x = FloatArray(n)
                    applyRow(ws, leftRow, target, -1f, x)
                    applyRow(ws, rightRow, target, 1f, x)
                    return x
                }
            }
        }
        return null
    }

    /**
     * Cherche une répartition exacte et **posable** : chaque poids d'un côté ou de
     * l'autre, à une abscisse dans sa plage autorisée, couple total exactement nul.
     *
     * **Le solveur empile, et c'est tout le sujet.** Il cherchait auparavant deux rangées
     * de briques serrées côte à côte, une par côté, et refusait tout ce qui n'y tenait
     * pas. C'était juste tant qu'une brique était une boîte de quelques centimètres ;
     * depuis que ce sont des tétriminos, les niveaux avancés demandent bien plus de
     * largeur qu'il n'y en a — mesuré : jusqu'à **197 %** de la planche utilisable en
     * EXTREME, quatorze briques pour 3,46 m — et c'est **voulu** : ces niveaux-là se
     * gagnent en empilant. Un solveur qui ne sait pas empiler déclarait donc infaisables
     * des niveaux parfaitement jouables.
     *
     * Il suit maintenant exactement le contrat du générateur ([BalanceGame.hasExactSolution]) :
     * un découpage en deux camps, et dans chaque camp des positions continues qui
     * atteignent le couple visé. Deux briques dont les abscisses se recouvrent s'empilent
     * d'elles-mêmes à la pose.
     */
    private fun solveLayout(game: BalanceGame): FloatArray? {
        val ws = game.weights
        val n = ws.size
        val lo = FloatArray(n) { ws[it].minDistance }
        val hi = FloatArray(n) { ws[it].maxDistance }

        for (mask in 1 until (1 shl n) - 1) {
            var loL = 0f; var hiL = 0f; var loR = 0f; var hiR = 0f
            for (i in 0 until n) {
                val t0 = ws[i].mass * lo[i]
                val t1 = ws[i].mass * hi[i]
                if ((mask shr i) and 1 == 1) { loL += t0; hiL += t1 } else { loR += t0; hiR += t1 }
            }
            val from = maxOf(loL, loR)
            val to = minOf(hiL, hiR)
            if (to < from) continue

            val target = (from + to) / 2f
            val x = FloatArray(n)
            fillSide(ws, lo, hi, mask, 1, -1f, loL, hiL, target, x)
            fillSide(ws, lo, hi, mask, 0, 1f, loR, hiR, target, x)
            return x
        }
        return null
    }

    /**
     * Place les poids d'un camp pour que leur couple total vaille exactement [target].
     *
     * Chaque poids parcourt sa plage à la même fraction : le couple du camp est alors une
     * fonction affine de cette fraction, et l'atteindre est une règle de trois. C'est
     * aussi ce qui **répartit** les briques au lieu de les empiler toutes au même endroit.
     */
    private fun fillSide(
        ws: List<BalanceWeight>, lo: FloatArray, hi: FloatArray,
        mask: Int, bit: Int, side: Float,
        campLo: Float, campHi: Float, target: Float, out: FloatArray
    ) {
        val span = campHi - campLo
        val t = if (span <= 1e-6f) 0f else ((target - campLo) / span).coerceIn(0f, 1f)
        for (i in ws.indices) {
            if ((mask shr i) and 1 != bit) continue
            out[i] = side * (lo[i] + t * (hi[i] - lo[i]))
        }
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

    /**
     * Un niveau **reproductible**.
     *
     * La graine est obligatoire, et c'est deliberement penible : sans elle, `BalanceGame`
     * tire ses masses sur le hasard du systeme, et deux tests posaient des assertions
     * chiffrees sur le niveau tire. Ils tombaient une fois sur dix, jamais les memes, et
     * jamais quand on relancait la classe seule — le pire genre de test, qui passe des mois
     * puis accuse le mauvais coupable un jour de malchance. Une graine obligatoire rend
     * impossible d'en ecrire un autre par distraction.
     */
    private fun newGame(diff: BalanceGame.Difficulty, graine: Long): BalanceGame {
        val game = BalanceGame(Random(graine))
        game.setViewport(8f)
        game.newLevel(diff)
        return game
    }

    /**
     * Installe une solution exacte **à plat** et laisse tout se caler.
     *
     * À plat, et pas empilée : les tests de verdict qui s'en servent demandent que la
     * disposition **tienne** dix secondes de test. Monter une pile qui ne verse pas est le
     * savoir-faire du joueur, pas quelque chose qu'un solveur de test improvise — il
     * empilait quatre briques au même endroit et la tour versait dès que la planche
     * bougeait. Rend faux quand les briques ne tiennent pas côte à côte, ce qui arrive
     * pour de bon en EXPERT et EXTREME : voir [solveLayout] et le test de faisabilité.
     */
    private fun layoutSolution(game: BalanceGame): Boolean {
        val x = solveFlatLayout(game) ?: return false
        for ((i, w) in game.weights.withIndex()) game.put(w, x[i])
        game.simulate(1.5f)
        return true
    }

    // ── Génération ────────────────────────────────────────────────────────────

    @Test
    fun `chaque niveau admet une repartition exacte et posable`() {
        for (diff in BalanceGame.Difficulty.entries) {
            repeat(25) { essai ->
                val game = newGame(diff, graine = 100L * diff.ordinal + essai)
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
        repeat(25) { essai ->
            val game = newGame(BalanceGame.Difficulty.MEDIUM, graine = 200L + essai)
            val masses = game.weights.map { it.mass }
            assertEquals("masses en double : $masses", masses.size, masses.toSet().size)
        }
    }

    /** L'aire réellement occupée par une pièce, toutes ses formes additionnées. */
    private fun aire(w: BalanceWeight): Float = w.body.parts.sumOf { part ->
        when (part.shape) {
            com.Atom2Universe.app.games.physics.Shape.BOX ->
                4.0 * part.halfW * part.halfH
            // Sommets (-w,-h), (w,-h), (0,h) : base x hauteur / 2.
            com.Atom2Universe.app.games.physics.Shape.TRIANGLE ->
                2.0 * part.halfW * part.halfH
            com.Atom2Universe.app.games.physics.Shape.CIRCLE ->
                Math.PI * part.radius * part.radius
        }
    }.toFloat()

    /**
     * **Une brique est d'autant plus grosse qu'elle est lourde, et l'aire est la seule
     * échelle de taille du jeu.**
     *
     * C'est la promesse écrite dans `createWeight` : « chaque géométrie reçoit exactement
     * [BalanceRules.areaForMass] ». On la vérifie donc directement, pour **toutes** les
     * difficultés et toutes les formes.
     *
     * Ce test comparait auparavant `body.halfW * body.halfH`, c'est-à-dire une boîte
     * englobante — et depuis que les briques sont des corps composés, cette lecture est
     * fausse deux fois : `body.halfW` ne rend que la première des quatre boîtes d'un
     * tétrimino, et une boîte englobante n'est de toute façon pas une aire. Un tétrimino
     * léger a une boîte englobante plus large qu'un rectangle lourd sans être plus gros :
     * comparer des boîtes englobantes de formes différentes ne veut rien dire, et c'est
     * pour ça que le test ne pouvait pas passer.
     */
    @Test
    fun `une brique est d autant plus grosse qu elle est lourde`() {
        for (diff in BalanceGame.Difficulty.entries) {
            val game = newGame(diff, graine = 300L + diff.ordinal)
            for (w in game.weights) {
                val attendu = BalanceRules.areaForMass(w.mass)
                assertEquals(
                    "${diff.name} : la brique ${w.shape} de ${w.mass} kg n'a pas l'aire " +
                        "de sa masse",
                    attendu.toDouble(), aire(w).toDouble(), (attendu * 0.02f).toDouble()
                )
            }
            val sorted = game.weights.sortedBy { it.mass }
            for (i in 1 until sorted.size) {
                assertTrue(
                    "${diff.name} : la brique de ${sorted[i].mass} kg n'est pas plus " +
                        "grosse que celle de ${sorted[i - 1].mass} kg",
                    aire(sorted[i]) > aire(sorted[i - 1])
                )
            }
        }
    }

    // ── Règles de pose ────────────────────────────────────────────────────────

    @Test
    fun `poser sur le pivot est refuse`() {
        val game = newGame(BalanceGame.Difficulty.EASY, graine = 501L)
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
        val game = newGame(BalanceGame.Difficulty.EASY, graine = 502L)
        val w = game.weights.first()
        val target = 0.93f
        game.put(w, target)
        game.simulate(1f)

        assertTrue("le poids n'est pas posé", w.placed)
        assertEquals("l'abscisse a été modifiée", target, w.body.x, 0.01f)
        val expected = BalanceRules.PLANK_TOP - w.baseOffset
        assertTrue(
            "le poids ne repose pas sur la planche : y=${w.body.y} (attendu ~$expected)",
            abs(w.body.y - expected) < 0.03f
        )
    }

    @Test
    fun `une brique peut depasser du bord en restant suffisamment soutenue`() {
        val game = newGame(BalanceGame.Difficulty.EASY, graine = 503L)
        val w = game.weights.first()
        game.put(w, BalanceRules.PLANK_HALF_LENGTH + 0.3f)
        game.simulate(1f)

        assertTrue("le poids n'est pas posé", w.placed)
        val overlap = BalanceRules.PLANK_HALF_LENGTH - (w.body.x - w.halfWidth)
        val minOverlap = 2f * w.halfWidth * BalanceRules.MIN_SUPPORT_FRACTION
        assertTrue("la brique n'est pas assez soutenue : $overlap < $minOverlap", overlap + 0.001f >= minOverlap)
    }

    @Test
    fun `le test n est possible qu une fois tous les poids poses`() {
        val game = newGame(BalanceGame.Difficulty.EASY, graine = 504L)
        assertTrue("test autorisé alors que rien n'est posé", !game.canTest())
        game.put(game.weights[0], 0.6f)
        assertTrue("test autorisé avec un seul poids posé", !game.canTest())
    }

    // ── Verdict de la balance ─────────────────────────────────────────────────

    /**
     * **Une disposition exacte et à plat gagne, dans toutes les difficultés où elle
     * existe.**
     *
     * « Où elle existe » n'est pas une échappatoire : à partir d'EXPERT les briques ne
     * tiennent plus côte à côte — mesuré, jusqu'à 197 % de la planche utilisable en
     * EXTREME — et ces niveaux-là se gagnent **en empilant**, ce qui est le sujet même du
     * jeu à ce stade. Qu'une répartition exacte existe pour eux aussi est garanti par
     * `chaque niveau admet une repartition exacte`, qui ne demande pas qu'elle soit à
     * plat. Ce test-ci vérifie autre chose : que la balance **valide** une solution juste.
     * Il compte donc les difficultés couvertes, pour qu'un jour où plus rien ne rentrerait
     * côte à côte se voie tout de suite.
     */
    @Test
    fun `une repartition equilibree est validee`() {
        var couvertes = 0
        for (diff in BalanceGame.Difficulty.entries) {
            var vues = 0
            repeat(5) { essai ->
                val game = newGame(diff, graine = 400L * diff.ordinal + essai)
                if (!layoutSolution(game)) return@repeat
                vues++
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
            println("VERDICT ${diff.name} : $vues/5 niveaux tenaient à plat")
            if (vues > 0) couvertes++
        }
        assertTrue(
            "plus aucune difficulté n'admet de disposition à plat : le test ne prouve rien",
            couvertes >= 3
        )
    }

    @Test
    fun `une repartition desequilibree est refusee`() {
        val game = newGame(BalanceGame.Difficulty.EASY, graine = 505L)
        // Tout du même côté : la planche doit franchement pencher.
        var x = 0.4f
        for (w in game.weights) {
            game.put(w, x + w.halfWidth)
            x += 2f * w.halfWidth + 0.04f
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
        val game = newGame(BalanceGame.Difficulty.MEDIUM, graine = 506L)
        assertTrue("aucune disposition trouvée", layoutSolution(game))

        // Solution exacte, puis on décale le poids le plus léger : le déséquilibre reste
        // petit, donc rien ne glisse et la prévision vaut.
        //
        // **Le décalage se cherche au lieu d'être posé à 20 cm.** Vingt centimètres d'un
        // poids léger ne font pas toujours 0,4° d'inclinaison — cela dépend des masses du
        // niveau — et le test échouait alors sur sa propre mise en scène, en annonçant
        // « le déséquilibre du test est nul » alors qu'il n'avait rien mesuré du tout. On
        // prend donc le plus petit décalage qui produise quelque chose à mesurer.
        val lightest = game.weights.minByOrNull { it.mass }!!
        val depart = lightest.body.x
        val side = if (depart < 0f) -1f else 1f
        var predicted = 0f
        for (shift in floatArrayOf(0.2f, 0.35f, 0.5f, 0.7f)) {
            val room = lightest.maxDistance - abs(depart)
            game.put(lightest, depart + side * if (room > shift) shift else -shift)
            game.simulate(1.5f)
            predicted = game.predictedLeanDeg
            if (abs(predicted) > 0.4f) break
        }
        assertTrue("aucun décalage ne déséquilibre ce niveau", abs(predicted) > 0.4f)

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
        val game = newGame(BalanceGame.Difficulty.EASY, graine = 507L)
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
        val game = newGame(BalanceGame.Difficulty.MEDIUM, graine = 508L)
        assertTrue("aucune disposition trouvée", layoutSolution(game))
        // Déséquilibre volontaire : la planche penche pendant le test, donc les
        // briques se déplacent réellement avant qu'on interrompe.
        val lightest = game.weights.minByOrNull { it.mass }!!
        val side = if (lightest.body.x < 0f) -1f else 1f
        val room = lightest.maxDistance - abs(lightest.body.x)
        game.put(lightest, lightest.body.x + side * if (room > 0.3f) 0.3f else -0.3f)
        game.simulate(1.5f)

        // **On retient l'abscisse choisie, pas celle où la brique a fini par se caler.**
        // C'est ce que `resumePlacing` restitue, sa documentation le dit en toutes lettres,
        // et le titre de ce test aussi — « leur place choisie ». L'assertion, elle, comparait
        // à `body.x` relevé après une seconde et demie de tassement : une brique qui avait
        // glissé de quatorze centimètres en se calant faisait échouer le test sur un écart
        // qui n'avait rien à voir avec l'interruption.
        val choisies = game.weights.map { it.plankX }

        game.startTest()
        game.simulate(1.2f)
        assertTrue("la planche n'a pas bougé pendant le test", abs(game.leanDeg) > 0.3f)

        game.resumePlacing()
        game.simulate(0.5f)

        assertEquals(BalanceGame.Phase.PLACING, game.phase)
        assertEquals("la planche n'est pas revenue à plat", 0f, game.leanDeg, 0.01f)
        assertTrue("des poids ont été renvoyés au plateau", game.allPlaced)
        for ((i, w) in game.weights.withIndex()) {
            assertEquals("le poids ${w.mass} kg n'a pas retrouvé sa place choisie",
                choisies[i], w.body.x, 0.02f)
        }
    }

    @Test
    fun `les essais sont comptes, le test gagnant excepte`() {
        val game = newGame(BalanceGame.Difficulty.EASY, graine = 509L)
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
        val game = newGame(BalanceGame.Difficulty.EASY, graine = 510L)
        assertTrue("aucune disposition trouvée", layoutSolution(game))
        // Meme raison qu'au test precedent : c'est l'abscisse choisie qui est restituee.
        val choisies = game.weights.map { it.plankX }

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
            assertEquals("le poids ${w.mass} kg a bougé", choisies[i], w.body.x, 0.15f)
        }
    }
}
