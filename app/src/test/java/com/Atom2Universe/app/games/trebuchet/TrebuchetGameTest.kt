package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Vérifie la machine de jet : elle doit porter comme un vrai trébuchet, réagir
 * lisiblement à chaque réglage du catalogue, et ne jamais partir en vrille
 * numérique.
 */
class TrebuchetGameTest {

    private fun machine(
        beam: Int = 1, post: Int = 1, ratio: Int = 1, weight: Int = 1,
        hang: Int = 1, pin: Int = 2, sling: Float = 0.6f
    ) = TrebuchetGame().apply {
        config.beamIndex = beam
        config.postIndex = post
        config.ratioIndex = ratio
        config.weightIndex = weight
        config.hangIndex = hang
        config.pinIndex = pin
        config.slingRatio = sling
        build()
    }

    @Test
    fun `la machine par defaut envoie le boulet loin devant`() {
        // La machine que le joueur trouve en arrivant, sans avoir rien réglé.
        val g = TrebuchetGame()
        val d = g.simulateShot()

        assertTrue("le boulet ne part pas devant : $d m", d > 60f)
        assertTrue("le boulet ne quitte pas la fronde : ${g.launchSpeed} m/s", g.launchSpeed > 25f)
        assertTrue("le crochet n'a pas lâché", g.ballFree)
        assertTrue("le boulet ne monte pas : ${g.peakHeight} m", g.peakHeight > g.pivotY)
    }

    @Test
    fun `une machine plus grande porte plus loin`() {
        // C'est ce qui donne son sens au catalogue : le bras long fait la vitesse de
        // pointe, et la fronde la double. Le banc, sur la meilleure combinaison de
        // chaque taille : 143 m à 8 m de bras, 174 m à 12 m, 214 m à 18 m.
        val small = machine(beam = 0, post = 2, ratio = 3, weight = 2, hang = 2, pin = 2, sling = 0.85f)
        val big = machine(beam = 2, post = 2, ratio = 3, weight = 2, hang = 2, pin = 2, sling = 0.85f)
        val ds = small.simulateShot()
        val db = big.simulateShot()

        println("bras 8 m → %.0f m (%.0f m/s) | bras 18 m → %.0f m (%.0f m/s)".format(
            ds, small.launchSpeed, db, big.launchSpeed))
        assertTrue("la grande machine ne porte pas plus loin : $ds m contre $db m", db > ds * 1.25f)
    }

    @Test
    fun `la fronde double la vitesse de la pointe`() {
        // C'est toute la raison d'être de la fronde : sans elle, le boulet sortirait à
        // la vitesse de la pointe du bras. Avec elle, il sort à peu près au double,
        // et c'est ce facteur deux qui quadruple la portée.
        val g = machine(sling = 0.85f, pin = 2)
        g.simulateShot()

        println("pointe %.1f m/s → boulet %.1f m/s (×%.2f)".format(
            g.launchTipSpeed, g.launchSpeed, g.launchSpeed / g.launchTipSpeed))
        assertTrue(
            "la fronde n'accélère rien : pointe %.1f m/s, boulet %.1f m/s".format(
                g.launchTipSpeed, g.launchSpeed
            ),
            g.launchSpeed > g.launchTipSpeed * 1.5f
        )
    }

    @Test
    fun `le crochet commande langle de tir`() {
        // Crochet redressé : la fronde le rencontre tôt, le boulet est encore bas et
        // file devant — tir tendu. Crochet couché : il retient jusqu'au bout du fouet,
        // le boulet passe par-dessus la pointe et part haut. C'est le seul réglage de
        // visée de la machine, et il doit être monotone, sinon le joueur ne peut rien
        // en faire.
        val angles = TrebuchetRules.PIN_ANGLES_DEG.indices.map { pin ->
            machine(pin = pin, sling = 0.7f).also { it.simulateShot() }.launchAngleDeg
        }
        println("crochets ${TrebuchetRules.PIN_ANGLES_DEG.toList()} → " +
            angles.joinToString { "%.0f°".format(it) })

        val flat = angles.last()      // crochet le plus redressé
        val steep = angles.first()    // crochet le plus couché
        assertTrue(
            "le crochet ne redresse pas le tir : %.0f° contre %.0f°".format(flat, steep),
            steep > flat + 15f
        )
        for (i in 1 until angles.size) {
            assertTrue(
                "le crochet n'est pas monotone : ${angles.map { it.toInt() }}",
                angles[i] <= angles[i - 1] + 6f
            )
        }
    }

    @Test
    fun `un contrepoids plus lourd porte plus loin`() {
        val light = machine(weight = 0).also { it.simulateShot() }.shotDistance
        val heavy = machine(weight = 2).also { it.simulateShot() }.shotDistance

        assertTrue(
            "le contrepoids ne change rien : $light m contre $heavy m",
            heavy > light * 1.25f
        )
    }

    @Test
    fun `la detente tient la machine bandee`() {
        val g = machine()
        // Bandée, la machine ne bouge pas : le bras est retenu, le contrepoids pend.
        repeat(60) { g.step(1f / 120f) }
        assertTrue("le bras part tout seul : ${g.beam.angle}", g.phase == TrebuchetGame.Phase.BUILD)

        val cocked = g.beam.angle
        g.release()
        repeat(24) { g.step(1f / 120f) }
        assertTrue("le bras ne démarre pas", g.beam.angle > cocked + 0.01f)
        assertTrue("le contrepoids ne tombe pas : ${g.counterweight.vy}", g.counterweight.vy < -0.2f)
    }

    @Test
    fun `le boulet part quand la fronde passe le crochet, pas avant`() {
        // Le largage n'est pas décrété : c'est la géométrie qui le produit. Ce test
        // vérifie que l'instant constaté est bien celui où la fronde croise le crochet.
        val g = machine(pin = 1)
        g.release()
        var freedAt = Float.NaN
        repeat(3000) {
            val wasFree = g.ballFree
            g.step(1f / 240f)
            if (!wasFree && g.ballFree && freedAt.isNaN()) freedAt = g.slingAngle
        }
        assertTrue("le crochet n'a jamais lâché", !freedAt.isNaN())
        assertTrue(
            "le largage n'est pas au passage du crochet : %.2f rad pour un seuil de %.2f"
                .format(freedAt, g.releaseAngle),
            abs(freedAt - g.releaseAngle) < 0.2f
        )
    }

    @Test
    fun `table croisee du crochet et de la fronde`() {
        // Les deux réglages fins de la machine. Cette table dit s'ils restent lisibles
        // pour le joueur ou s'ils se brouillent l'un l'autre.
        println("--- bras 12 m, levier 4:1, pied 0,70, 3000 kg, chape moyenne ---")
        for (pin in TrebuchetRules.PIN_ANGLES_DEG.indices) {
            val line = StringBuilder("  crochet %3d° :".format(TrebuchetRules.PIN_ANGLES_DEG[pin]))
            for (s in listOf(0.3f, 0.45f, 0.6f, 0.75f, 0.9f)) {
                val g = machine(pin = pin, sling = s)
                val d = g.simulateShot()
                line.append(
                    "  fronde %.2f → %4.0f m (%4.1f m/s, %3.0f°, %2.0f %%)".format(
                        s, d, g.launchSpeed, g.launchAngleDeg, g.efficiency * 100f
                    )
                )
            }
            println(line)
        }
    }

    @Test
    fun `table de la chape du contrepoids`() {
        // La chape accorde le pendule du contrepoids sur la rotation du bras.
        println("--- chape × levier, bras 12 m, crochet 90°, fronde 0,6, 3000 kg ---")
        for (h in TrebuchetRules.HANG_RATIOS.indices) {
            val line = StringBuilder("  chape %.1f× :".format(TrebuchetRules.HANG_RATIOS[h]))
            for (r in TrebuchetRules.LEVER_RATIOS.indices) {
                val g = machine(hang = h, ratio = r)
                val d = g.simulateShot()
                line.append(
                    "  levier %.0f:1 → %4.0f m (%2.0f %%)".format(
                        TrebuchetRules.LEVER_RATIOS[r], d, g.efficiency * 100f
                    )
                )
            }
            println(line)
        }
    }

    /**
     * Le garde-fou le plus important du jeu.
     *
     * Toute la mise au point de la machine précédente avait consisté à traquer des
     * tirs qui partaient à 250, puis 400 m/s — jamais de la vraie physique, toujours
     * un corps coincé quelque part et éjecté par le solveur. Aucune combinaison du
     * catalogue ne doit pouvoir produire ça, et la borne n'est pas arbitraire : c'est
     * celle que l'énergie stockée autorise.
     */
    @Test
    fun `aucune combinaison du catalogue ne fait exploser la simulation`() {
        var forward = 0
        var total = 0
        var worstOverspeed = 0f
        var worstWhere = ""
        val all = ArrayList<Pair<Float, String>>()

        for (b in TrebuchetRules.BEAM_LENGTHS.indices) {
            for (p in TrebuchetRules.POST_RATIOS.indices) {
                for (r in TrebuchetRules.LEVER_RATIOS.indices) {
                    for (w in TrebuchetRules.COUNTERWEIGHTS.indices) {
                        for (h in TrebuchetRules.HANG_RATIOS.indices) {
                            for (pin in TrebuchetRules.PIN_ANGLES_DEG.indices) {
                                val g = machine(
                                    beam = b, post = p, ratio = r, weight = w,
                                    hang = h, pin = pin
                                )
                                // Plafond honnête : toute l'énergie stockée passée au
                                // boulet, et rien d'autre. Un tir au-delà est de
                                // l'énergie que personne n'a fournie.
                                val ceiling = kotlin.math.sqrt(
                                    2f * g.config.storedEnergy / TrebuchetRules.BALL_MASS
                                )
                                val d = g.simulateShot()
                                total++
                                if (d > 0f) forward++

                                val label = "bras=%.0f pied=%.2f levier=%.0f poids=%.0f chape=%.1f crochet=%d".format(
                                    TrebuchetRules.BEAM_LENGTHS[b],
                                    TrebuchetRules.POST_RATIOS[p],
                                    TrebuchetRules.LEVER_RATIOS[r],
                                    TrebuchetRules.COUNTERWEIGHTS[w],
                                    TrebuchetRules.HANG_RATIOS[h],
                                    TrebuchetRules.PIN_ANGLES_DEG[pin]
                                ) + " | %.0f m/s sous %.0f° (%.0f %%)".format(
                                    g.launchSpeed, g.launchAngleDeg, g.efficiency * 100f
                                )
                                all += d to label
                                val over = g.peakSpeed / ceiling
                                if (over > worstOverspeed) {
                                    worstOverspeed = over
                                    worstWhere = label
                                }
                                assertTrue(
                                    "le tir ne se termine pas",
                                    g.phase == TrebuchetGame.Phase.RESULT
                                )
                            }
                        }
                    }
                }
            }
        }

        all.sortByDescending { it.first }
        println("=== 8 meilleures ===")
        all.take(8).forEach { println("  %5.0f m  %s".format(it.first, it.second)) }
        println("=== 4 pires ===")
        all.takeLast(4).forEach { println("  %5.0f m  %s".format(it.first, it.second)) }
        println(
            "=== %d combinaisons : médiane %.0f m, %d vers l'avant ===".format(
                total, all[total / 2].first, forward
            )
        )
        println(
            "=== pire dépassement du plafond d'énergie : %.2f× (%s) ===".format(
                worstOverspeed, worstWhere
            )
        )

        assertTrue(
            "un tir dépasse l'énergie disponible de %.2f× : %s".format(worstOverspeed, worstWhere),
            worstOverspeed < 1f
        )
        assertTrue(
            "seules $forward machines sur $total tirent vers l'avant",
            forward > total * 4 / 5
        )
        assertTrue(
            "la meilleure machine du catalogue ne porte qu'à %.0f m".format(all.first().first),
            all.first().first > 150f
        )
    }
}
