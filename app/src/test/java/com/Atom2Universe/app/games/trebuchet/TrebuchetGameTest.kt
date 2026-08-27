package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Vérifie la machine de jet : elle doit porter comme un vrai trébuchet, réagir
 * lisiblement à chaque réglage, et ne jamais partir en vrille numérique.
 *
 * Les réglages sont continus depuis que le joueur les prend en main directement sur
 * la machine. Les balayages ci-dessous ne sont donc plus « toutes les combinaisons
 * du catalogue » mais une grille tendue d'un bout à l'autre de chaque plage, bornes
 * comprises : c'est là que les machines absurdes se trouvent, et c'est justement
 * d'elles qu'on veut la garantie.
 */
class TrebuchetGameTest {

    /**
     * Une machine décrite comme le joueur la voit. Le pied et la chape se donnent en
     * proportion — un pied de huit mètres ne veut pas dire la même chose sur une
     * poutre de huit et sur une poutre de vingt.
     */
    private fun machine(
        beam: Float = 12f,
        postRatio: Float = 0.70f,
        lever: Float = 4f,
        weight: Float = 3000f,
        hangRatio: Float = 1f,
        pin: Float = 45f,
        sling: Float = 0.6f
    ) = TrebuchetGame().apply {
        config.beamLength = beam
        config.leverRatio = lever
        config.pivotHeight = postRatio * beam
        config.counterweightMass = weight
        config.hangLength = hangRatio * (beam / (1f + lever))
        config.pinAngleDeg = pin
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
        // Le bras long fait la vitesse de pointe, et la fronde la double.
        val small = machine(beam = 8f, postRatio = 0.85f, lever = 6f, weight = 6000f,
            hangRatio = 1.6f, sling = 0.85f)
        val big = machine(beam = 18f, postRatio = 0.85f, lever = 6f, weight = 6000f,
            hangRatio = 1.6f, sling = 0.85f)
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
        val g = machine(sling = 0.85f)
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
        // Crochet couché : il retient jusqu'au bout du fouet, le boulet passe
        // par-dessus la pointe et part devant, tendu. Crochet redressé : la fronde le
        // rencontre tôt, le boulet grimpe encore derrière la machine et part de plus
        // en plus haut. C'est le seul réglage de visée de la machine, et il doit être
        // monotone, sinon le joueur ne peut rien en faire.
        val hooks = listOf(15f, 30f, 45f, 60f, 75f)
        val angles = hooks.map { machine(pin = it, sling = 0.7f).also { m -> m.simulateShot() }.launchAngleDeg }
        println("crochets $hooks → " + angles.joinToString { "%.0f°".format(it) })

        val flat = angles.first()
        val steep = angles.last()
        assertTrue(
            "le crochet ne redresse pas le tir : %.0f° contre %.0f°".format(flat, steep),
            steep > flat + 15f
        )
        for (i in 1 until angles.size) {
            assertTrue(
                "le crochet n'est pas monotone : ${angles.map { it.toInt() }}",
                angles[i] >= angles[i - 1] - 6f
            )
        }
    }

    @Test
    fun `un contrepoids plus lourd porte plus loin`() {
        val light = machine(weight = 1500f).also { it.simulateShot() }.shotDistance
        val heavy = machine(weight = 6000f).also { it.simulateShot() }.shotDistance

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
        // Le contrepoids, levé côté cible, retombe : le bras tourne en sens horaire
        // et son angle décroît.
        assertTrue("le bras ne démarre pas", g.beam.angle < cocked - 0.01f)
        assertTrue("le contrepoids ne tombe pas : ${g.counterweight.vy}", g.counterweight.vy < -0.2f)
    }

    @Test
    fun `le boulet part quand la fronde passe le crochet, pas avant`() {
        // Le largage n'est pas décrété : c'est la géométrie qui le produit. Ce test
        // vérifie que l'instant constaté est bien celui où la fronde croise le crochet.
        val g = machine(pin = 30f)
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
    fun `le domaine reste habitable aux bornes`() {
        // Les réglages continus laissent le joueur pousser chaque bouton à fond. La
        // machine doit alors se ramener toute seule dans le possible : une chape plus
        // longue que la place sous le bras court poserait le contrepoids par terre.
        val g = TrebuchetGame()
        g.config.beamLength = 999f
        g.config.leverRatio = 0.1f
        g.config.counterweightMass = -50f
        g.config.hangLength = 500f
        g.config.pinAngleDeg = 400f
        g.config.slingRatio = 9f
        g.build()

        val c = g.config
        val room = c.pivotHeight - c.shortArm - c.counterweightHalf - TrebuchetRules.CW_CLEARANCE
        assertTrue("la poutre sort de sa plage : ${c.beamLength}", c.beamLength <= TrebuchetRules.BEAM_MAX)
        assertTrue("le levier sort de sa plage : ${c.leverRatio}", c.leverRatio >= TrebuchetRules.LEVER_MIN)
        assertTrue("le contrepoids est négatif : ${c.counterweightMass}", c.counterweightMass >= TrebuchetRules.CW_MIN)
        assertTrue("le crochet sort de sa plage : ${c.pinAngleDeg}", c.pinAngleDeg <= TrebuchetRules.PIN_MAX_DEG)
        assertTrue(
            "la chape laboure le sol : %.2f m pour %.2f m de place".format(c.hangLength, room),
            c.hangLength <= maxOf(room, TrebuchetRules.HANG_MIN) + 0.001f
        )
        assertTrue("le tir ne se termine pas", g.simulateShot() > -1000f)
    }

    @Test
    fun `la previsualisation montre le depart et pas la chute`() {
        // Le cône affiché au joueur sort de cette trace : elle doit commencer au
        // largage — pas au sol, sous la machine — et s'arrêter à la distance demandée.
        val g = TrebuchetGame()
        val path = g.simulateStart(50f)

        assertTrue("la prévisualisation est vide", path.size >= 8)
        val startX = path[0]
        val endX = path[path.size - 2]
        val startY = path[1]
        println("départ prévisualisé : (%.1f, %.1f) → (%.1f, %.1f) en %d points".format(
            startX, startY, endX, path[path.size - 1], path.size / 2))
        assertTrue("la trace ne part pas du largage : %.1f m de haut".format(startY), startY > 1f)
        assertTrue("la trace ne va pas devant : %.1f m".format(endX), endX > startX)
        assertTrue("la trace dépasse ce qu'on demande : %.1f m".format(endX), endX < 70f)
    }

    @Test
    fun `table croisee du crochet et de la fronde`() {
        // Les deux réglages fins de la machine. Cette table dit s'ils restent lisibles
        // pour le joueur ou s'ils se brouillent l'un l'autre.
        println("--- bras 12 m, levier 4:1, pied 0,70, 3000 kg, chape moyenne ---")
        for (pin in listOf(15f, 30f, 45f, 60f, 75f)) {
            val line = StringBuilder("  crochet %3.0f° :".format(pin))
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
        println("--- chape × levier, bras 12 m, crochet 45°, fronde 0,6, 3000 kg ---")
        for (h in listOf(0.5f, 1f, 1.6f, 2.4f)) {
            val line = StringBuilder("  chape %.1f× :".format(h))
            for (r in listOf(3f, 4f, 5f, 6f)) {
                val g = machine(hangRatio = h, lever = r)
                val d = g.simulateShot()
                line.append(
                    "  levier %.0f:1 → %4.0f m (%2.0f %%)".format(r, d, g.efficiency * 100f)
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
     * un corps coincé quelque part et éjecté par le solveur. Aucun réglage accessible
     * au joueur ne doit pouvoir produire ça, et la borne n'est pas arbitraire : c'est
     * celle que l'énergie stockée autorise.
     *
     * Depuis que les réglages sont continus, le balayage tient les bornes de chaque
     * plage plutôt que des crans : ce sont les extrêmes qui cassent les solveurs, pas
     * les valeurs raisonnables du milieu.
     */
    @Test
    fun `aucun reglage accessible ne fait exploser la simulation`() {
        var forward = 0
        var total = 0
        var worstOverspeed = 0f
        var worstWhere = ""
        val all = ArrayList<Pair<Float, String>>()

        for (beam in listOf(6f, 12f, 20f)) {
            for (post in listOf(0.35f, 0.7f, 1f)) {
                for (lever in listOf(2f, 4f, 6f, 8f)) {
                    for (weight in listOf(300f, 3000f, 12000f)) {
                        for (hang in listOf(0.3f, 1.2f, 2.5f)) {
                            for (pin in listOf(5f, 25f, 45f, 65f, 85f)) {
                                // La fronde suit le crochet : c'est le couplage mesuré
                                // au banc (un crochet redressé veut une fronde longue),
                                // et c'est l'accord que le joueur fait au doigt.
                                val g = machine(
                                    beam = beam, postRatio = post, lever = lever,
                                    weight = weight, hangRatio = hang, pin = pin,
                                    sling = 0.55f + 0.004f * pin
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

                                val label = ("bras=%.0f pied=%.2f levier=%.0f poids=%.0f " +
                                    "chape=%.1f crochet=%.0f").format(
                                    beam, post, lever, weight, hang, pin
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
            "=== %d réglages : médiane %.0f m, %d vers l'avant ===".format(
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
            forward > total / 2
        )
        assertTrue(
            "la meilleure machine ne porte qu'à %.0f m".format(all.first().first),
            all.first().first > 150f
        )
    }
}
