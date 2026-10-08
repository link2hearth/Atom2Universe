package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * **L'avance rapide donne-t-elle vraiment l'élan qu'elle promet ?**
 *
 * C'est la seule question qui compte pour cette commande : le joueur règle une durée,
 * appuie, et doit obtenir *exactement* la machine qu'il aurait eue en attendant. Une
 * avance rapide qui tricherait — en sautant du temps, en posant une vitesse au lieu de
 * la faire monter — se verrait au premier tir et ne se démontrerait jamais à l'œil.
 *
 * Rien de tout ça n'est visible dans l'application : deux machines chargées
 * différemment se ressemblent trait pour trait, seule la portée diffère.
 */
class GearChargeTest {

    private val fixed = 1f / 120f

    /** L'énergie cinétique de rotation stockée dans tout le mécanisme, en joules. */
    private fun elan(game: GearMachineGame): Float {
        var total = 0f
        for (gear in game.gears) {
            val b = gear.body
            total += 0.5f * b.inertia * b.omega * b.omega
        }
        return total
    }

    /** Charge la machine de bout en bout, comme le fait la vue image par image. */
    private fun charge(game: GearMachineGame, seconds: Float): Float {
        assertTrue("la charge n'a pas démarré", game.startCharge(seconds))
        var joue = 0f
        var garde = 0
        while (game.charging && garde++ < 100_000) {
            joue += game.advanceCharge(fixed, 2_000)
        }
        return joue
    }

    /**
     * **Avancer vite, c'est attendre.**
     *
     * Le test qui vaut tous les autres. Deux machines identiques : l'une passe par
     * l'avance rapide, l'autre est simulée pas à pas au rythme normal pendant la même
     * durée. Elles doivent finir dans le **même** état, au bruit de virgule près.
     *
     * Si un jour quelqu'un décide d'« optimiser » l'avance rapide en agrandissant son
     * pas de temps, c'est ce test qui le dira : un solveur à pas variable ne donne pas
     * le même régime, et la machine sortirait de sa charge avec une énergie inventée.
     */
    @Test
    fun `l avance rapide donne exactement le meme etat que d attendre`() {
        val duree = 20f

        val rapide = GearMachineGame()
        val joue = charge(rapide, duree)

        val patient = GearMachineGame()
        var reste = duree
        while (reste > 0f) {
            val h = minOf(fixed, reste)
            patient.step(h)
            reste -= h
        }

        val a = elan(rapide)
        val b = elan(patient)
        println(
            "CHARGE ${duree.toInt()} s : avance rapide ${"%.1f".format(a)} J, " +
                "attente ${"%.1f".format(b)} J, temps joué ${"%.3f".format(joue)} s"
        )
        assertEquals("l'avance rapide n'a pas joué la durée demandée", duree, joue, 1e-3f)
        assertTrue("la machine n'a pris aucun élan", a > 1f)
        assertEquals(
            "l'avance rapide diverge de l'attente : ${"%.2f".format(a)} contre ${"%.2f".format(b)} J",
            b, a, b * 0.001f + 0.01f
        )

        // Et roue par roue, pas seulement en somme : deux erreurs opposées se
        // compenseraient dans un total.
        for (r in rapide.gears) {
            val p = patient.gears.first { it.wheel.id == r.wheel.id }
            assertEquals(
                "la roue ${r.wheel.id} ne tourne pas au même régime",
                p.body.omega, r.body.omega, abs(p.body.omega) * 0.002f + 1e-3f
            )
        }
    }

    /**
     * **Charger plus longtemps donne plus d'élan — jusqu'au régime libre du moteur.**
     *
     * C'est la promesse du réglage de durée, et elle a une fin. Mesuré sur la machine
     * par défaut, en joules stockés dans tout le mécanisme :
     *
     * ```
     *   5 s :     704 J
     *  15 s :   6 302 J      (+  560 J/s)
     *  45 s :  55 858 J      (+1 652 J/s)
     * 120 s : 132 928 J      (+1 028 J/s)
     * 300 s : 132 928 J      (+    0 J/s)
     * ```
     *
     * Le début accélère — l'énergie va comme le **carré** du régime, donc à couple
     * constant elle grimpe de plus en plus vite. Puis tout s'arrête net, et ce n'est pas
     * le frottement : c'est la **vitesse libre** du moteur, `RotaryDriveJoint.targetOmega`.
     * Une roue à aubes ne tourne pas plus vite que l'eau qui la pousse ; arrivée là, son
     * couple tombe à zéro et la machine ne gagne plus rien.
     *
     * **C'est la limite que le joueur doit connaître** : passé le palier, régler une
     * durée plus longue ne sert à rien, et il faut changer de moteur ou de rapport de
     * transmission. Le chiffre dépend de la machine — celle par défaut plafonne vers
     * deux minutes.
     */
    @Test
    fun `l elan croit avec la duree jusqu au regime libre du moteur`() {
        val mesures = listOf(5f, 15f, 45f, 120f, 300f).map { d ->
            d to elan(GearMachineGame().also { charge(it, d) })
        }
        println(
            "CHARGE élan : " + mesures.joinToString(", ") {
                "${it.first.toInt()} s → ${"%.0f".format(it.second)} J"
            }
        )
        for ((avant, apres) in mesures.zipWithNext()) {
            assertTrue(
                "charger ${apres.first} s ne donne pas plus que ${avant.first} s",
                apres.second >= avant.second - 1f
            )
        }

        // Les gains **par seconde**, seule façon de comparer des tranches inégales.
        val vitesses = mesures.zipWithNext().map { (avant, apres) ->
            (apres.second - avant.second) / (apres.first - avant.first)
        }
        println("CHARGE gain : " + vitesses.joinToString(", ") { "${"%.0f".format(it)} J/s" })
        assertTrue(
            "le gain ne ralentit jamais : le moteur n'a pas de vitesse libre",
            vitesses.last() < vitesses.max()
        )
        // Le palier est franc : au-delà, charger plus longtemps ne rapporte plus rien.
        assertEquals(
            "le palier n'en est pas un : la machine gagne encore après le régime libre",
            mesures[3].second, mesures[4].second, mesures[3].second * 0.02f
        )
    }

    /**
     * **Aucune énergie n'est créée en avançant vite.**
     *
     * Le garde-fou du moteur physique, applique a la charge : ce que la machine stocke
     * ne peut pas dépasser le travail que ses moteurs ont fourni pendant ce temps-là.
     * `installedPower() × durée` est ce travail, et le rendement réel est bien
     * inférieur — il y a la denture, les paliers, et le patinage des embrayages.
     */
    @Test
    fun `la charge ne cree pas d energie`() {
        val duree = 30f
        val game = GearMachineGame()
        val travail = game.installedPower() * duree
        charge(game, duree)
        val stocke = elan(game)
        val rendement = if (travail > 0f) stocke / travail else 0f
        println(
            "CHARGE ${duree.toInt()} s : ${"%.0f".format(stocke)} J stockés pour " +
                "${"%.0f".format(travail)} J fournis — rendement ${"%.1f".format(rendement * 100f)} %"
        )
        assertTrue("la machine n'a aucun moteur", travail > 0f)
        assertTrue(
            "la charge a créé de l'énergie : ${"%.0f".format(stocke)} J pour " +
                "${"%.0f".format(travail)} J fournis",
            stocke <= travail
        )
    }

    /**
     * La durée demandée est **bornée**, et ce qu'on joue est ce qu'on a demandé.
     *
     * Le bouton d'avance rapide vivra bientôt dans sa propre bulle, avec son réglage de
     * durée ; ce test fixe le contrat que cette bulle devra respecter.
     */
    @Test
    fun `la duree de charge est bornee et respectee`() {
        val game = GearMachineGame()
        assertTrue(game.startCharge(GearMachineRules.MAX_CHARGE * 10f))
        assertEquals(
            "une durée démesurée n'est pas ramenée à la borne",
            GearMachineRules.MAX_CHARGE, game.chargeTotal, 1e-3f
        )
        game.cancelCharge()

        assertTrue(game.startCharge(0f))
        assertEquals(
            "une durée nulle n'est pas ramenée à la borne",
            GearMachineRules.MIN_CHARGE, game.chargeTotal, 1e-3f
        )
        game.cancelCharge()

        // Interrompre une charge la laisse là où elle en est : l'élan déjà pris reste
        // acquis, c'est tout l'intérêt du bouton d'arrêt.
        val autre = GearMachineGame()
        autre.startCharge(60f)
        autre.advanceCharge(fixed, 600)
        val acquis = elan(autre)
        autre.cancelCharge()
        assertTrue("interrompre la charge a effacé l'élan", elan(autre) >= acquis - 1e-3f)
        assertTrue("la charge continue après avoir été interrompue", !autre.charging)
    }
}
