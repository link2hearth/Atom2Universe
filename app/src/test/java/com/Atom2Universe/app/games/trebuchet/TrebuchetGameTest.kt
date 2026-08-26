package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la machine de jet : elle doit lancer vers l'avant, réagir aux réglages
 * du catalogue, et surtout ne jamais partir en vrille numérique.
 */
class TrebuchetGameTest {

    private fun machine(
        beam: Int = 1, foot: Int = 1, ratio: Int = 2, weight: Int = 2,
        tilt: Int = 2, stop: Int = 2,
        drop: Float = TrebuchetRules.DROP_MAX, ballAtTip: Boolean = true
    ) = TrebuchetGame().apply {
        config.beamIndex = beam
        config.footIndex = foot
        config.ratioIndex = ratio
        config.weightIndex = weight
        config.cupCurveIndex = tilt
        config.stopIndex = stop
        config.dropHeight = drop
        if (ballAtTip) config.ballDistance = 99f     // ramené au bout du bras
        build()
    }

    @Test
    fun `la machine par defaut envoie le boulet devant la ligne de tir`() {
        val g = machine()
        val d = g.simulateShot()

        assertTrue("le boulet ne franchit pas la ligne de tir : $d m", d > 5f)
        assertTrue("le boulet ne quitte pas la cuiller : ${g.launchSpeed} m/s", g.launchSpeed > 3f)
        assertTrue("le boulet ne monte pas : ${g.peakHeight} m", g.peakHeight > g.pivotY)
    }

    @Test
    fun `raccourcir le bras court augmente la vitesse de sortie`() {
        // C'est le rapport de bras qui commande la portée, pas la masse. Quand le
        // contrepoids devient lourd, la vitesse tend vers une limite qui ne dépend
        // plus du tout de lui — son énergie part dans sa propre inertie :
        //
        //     v = racine(2·g·sin(angle d'arrêt)) × bras long / racine(bras court)
        //
        // Doubler le contrepoids ne change donc presque rien, alors que raccourcir
        // le bras court fait monter la vitesse. C'est mesuré, pas supposé.
        // Sur le grand bras, où la machine a de quoi exprimer la différence.
        val gentle = machine(beam = 2, ratio = 0, stop = 3).also { it.simulateShot() }.launchSpeed
        val steep = machine(beam = 2, ratio = 2, stop = 3).also { it.simulateShot() }.launchSpeed

        assertTrue(
            "le rapport de bras ne change rien : $gentle m/s contre $steep m/s",
            steep > gentle * 1.15f
        )
    }

    @Test
    fun `le lacher du contrepoids declenche sans desarconner le boulet`() {
        // La hauteur de lâcher n'est pas une réserve d'énergie, c'est une gâchette.
        // Lâché de trop haut, le contrepoids frappe le bras au lieu de le pousser :
        // le bras saute à pleine vitesse en deux degrés et le boulet est éjecté de
        // sa cuiller comme une balle posée sur une planche qu'on cogne par dessous.
        // Sur toute la plage autorisée, le tir doit rester un vrai tir.
        for (drop in listOf(TrebuchetRules.DROP_MIN, TrebuchetRules.DROP_MAX)) {
            val g = machine(drop = drop)
            val d = g.simulateShot()
            assertTrue("à $drop m de lâcher, le tir ne porte que $d m", d > 10f)
            assertTrue(
                "à $drop m de lâcher, le boulet part sous ${g.launchAngleDeg}° : il est " +
                    "désarçonné avant que le bras ait tourné",
                g.launchAngleDeg < 60f
            )
        }
    }

    @Test
    fun `le cliquet tient le bras jusqu au choc du contrepoids`() {
        val g = machine()
        g.release()
        // Un dixième de seconde après le lâcher, le contrepoids tombe encore et le
        // bras n'a pas bougé d'un cheveu : sans ce cliquet, le bras s'affaisserait
        // du côté du boulet et celui-ci roulerait par terre avant le tir.
        repeat(12) { g.step(1f / 120f) }
        assertTrue("le bras part avant le choc : ${g.beam.angle}", kotlin.math.abs(g.beam.angle) < 0.01f)
        assertTrue("le contrepoids ne tombe pas", g.counterweight.vy < -0.5f)
    }

    @Test
    fun `larretoir arrete bien le bras`() {
        val g = machine()
        g.simulateShot()
        // Le bras doit finir contre son tampon, à l'angle prévu, et pas avoir fait
        // un tour complet : c'est cet arrêt qui libère le boulet.
        val stopped = Math.toRadians(g.stopAngleDeg.toDouble()).toFloat()
        assertTrue(
            "le bras ne s'arrête pas à l'arrêtoir : ${g.beam.angle} rad pour un arrêt prévu à $stopped",
            g.beam.angle > stopped - 0.35f
        )
    }

    @Test
    fun `table croisee du crochet et de larretoir`() {
        // Les deux réglages agissent tous les deux sur le moment du largage : cette
        // table dit s'ils restent lisibles pour le joueur ou s'ils se brouillent.
        for (drop in listOf(TrebuchetRules.DROP_MIN, TrebuchetRules.DROP_MAX)) {
            println("--- lâcher de $drop m, bras 8 m, rapport 6, 700 kg ---")
            for (t in TrebuchetRules.CUP_CURVES_DEG.indices) {
                val line = StringBuilder("  crochet %2d° :".format(TrebuchetRules.CUP_CURVES_DEG[t]))
                for (st in TrebuchetRules.STOP_ANGLES_DEG.indices) {
                    val g = machine(beam = 1, ratio = 2, weight = 2, tilt = t, stop = st, drop = drop)
                    val d = g.simulateShot()
                    line.append("   arrêt %3d° → %6.1f m (%4.1f m/s, %3.0f°)".format(
                        TrebuchetRules.STOP_ANGLES_DEG[st], d, g.launchSpeed, g.launchAngleDeg))
                }
                println(line)
            }
        }
    }

    /**
     * Le garde-fou le plus important du jeu.
     *
     * Toute la mise au point de la machine a consisté à traquer des tirs qui
     * partaient à 20, puis 400 m/s — jamais de la vraie physique, toujours un
     * corps coincé quelque part et éjecté par le solveur. Aucune combinaison du
     * catalogue ne doit pouvoir produire ça.
     */
    @Test
    fun `aucune combinaison du catalogue ne fait exploser la simulation`() {
        var forward = 0
        var total = 0
        var best = 0f
        var worstSpeed = 0f
        var worstSpeedWhere = ""
        val all = ArrayList<Pair<Float, String>>()

        for (b in TrebuchetRules.BEAM_LENGTHS.indices) {
            for (r in TrebuchetRules.LEVER_RATIOS.indices) {
                for (w in TrebuchetRules.COUNTERWEIGHTS.indices) {
                    for (st in TrebuchetRules.STOP_ANGLES_DEG.indices) {
                        val g = machine(beam = b, ratio = r, weight = w, stop = st)
                        val d = g.simulateShot()
                        total++
                        if (d > 0f) forward++
                        if (d > best) best = d
                        all += d to ("bras=${TrebuchetRules.BEAM_LENGTHS[b]} " +
                            "rapport=${TrebuchetRules.LEVER_RATIOS[r]} " +
                            "poids=${TrebuchetRules.COUNTERWEIGHTS[w]} " +
                            "arrêt=${TrebuchetRules.STOP_ANGLES_DEG[st]} " +
                            "| sortie %.1f m/s sous %.0f° (arrêt %.0f°)".format(
                                g.launchSpeed, g.launchAngleDeg, g.stopAngleDeg))

                        if (g.peakSpeed > worstSpeed) {
                            worstSpeed = g.peakSpeed
                            worstSpeedWhere = all.last().second
                        }
                        assertTrue("le tir ne se termine pas", g.phase == TrebuchetGame.Phase.RESULT)
                    }
                }
            }
        }

        all.sortByDescending { it.first }
        println("=== 8 meilleures ===")
        all.take(8).forEach { println("  %.1f m  %s".format(it.first, it.second)) }
        println("=== 4 pires ===")
        all.takeLast(4).forEach { println("  %.1f m  %s".format(it.first, it.second)) }
        println("=== %d combinaisons : médiane %.1f m, %d vers l'avant ===".format(
            total, all[total / 2].first, forward))

        println("=== vitesse maximale rencontrée : %.1f m/s (%s) ===".format(worstSpeed, worstSpeedWhere))

        assertTrue(
            "vitesse aberrante : %.1f m/s pour %s".format(worstSpeed, worstSpeedWhere),
            worstSpeed < 60f
        )
        assertTrue(
            "portée aberrante : %.1f m pour %s".format(all.last().first, all.last().second),
            all.last().first > -40f
        )
        assertTrue("la meilleure machine du catalogue ne porte qu'à $best m", best > 28f)
        assertTrue(
            "seules $forward machines sur $total tirent vers l'avant",
            forward > total * 9 / 10
        )
    }
}
