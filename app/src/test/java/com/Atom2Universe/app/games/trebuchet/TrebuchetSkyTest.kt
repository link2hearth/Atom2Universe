package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le ciel : l'heure, les astres, les couleurs, et l'éclipse.
 *
 * Tout se mesure sans écran, parce que [SkyState] ne connaît pas Android — il rend des
 * fractions, des hauteurs et des entiers ARGB, et la vue se contente de les peindre.
 * C'est ce qui permet de vérifier qu'une pleine lune se lève au coucher du soleil, ce
 * qu'aucun coup d'œil à l'application ne dirait avant d'avoir attendu dix minutes.
 */
class TrebuchetSkyTest {

    /** Minuit UTC le 1er janvier 2026, comme point de départ lisible. */
    private val minuit = 1767225600000L
    private val heure = 3_600_000L

    private fun ciel(t: Long) = SkyState().apply { update(t) }

    @Test
    fun `le soleil monte le matin et se couche le soir`() {
        val aube = ciel(minuit + 6 * heure)
        val midi = ciel(minuit + 12 * heure)
        val crepuscule = ciel(minuit + 18 * heure)
        val nuit = ciel(minuit)

        assertEquals("à six heures le soleil devrait être à l'horizon", 0f, aube.sunAltitude, 0.02f)
        assertEquals("à midi il devrait être au zénith", 1f, midi.sunAltitude, 0.02f)
        assertEquals("à dix-huit heures, de nouveau à l'horizon", 0f, crepuscule.sunAltitude, 0.02f)
        assertTrue("à minuit il devrait être sous terre", nuit.sunAltitude < -0.9f)

        // Et il traverse le ciel d'est en ouest sans jamais se téléporter.
        assertTrue("le soleil ne va pas vers l'ouest", midi.sunX > aube.sunX)
        assertTrue("le soleil dépasse le bord", crepuscule.sunX <= 1f)
    }

    @Test
    fun `il fait clair le jour et sombre la nuit`() {
        assertTrue("il ne fait pas jour à midi", ciel(minuit + 12 * heure).light > 0.95f)
        assertTrue("il ne fait pas nuit à minuit", ciel(minuit).light < 0.4f)
        // Le crépuscule n'est ni l'un ni l'autre : c'est tout l'intérêt.
        val entreDeux = ciel(minuit + 19 * heure).light
        assertTrue("le crépuscule est un interrupteur : $entreDeux", entreDeux in 0.02f..0.9f)
    }

    @Test
    fun `les etoiles ne sortent que quand le soleil est parti`() {
        assertEquals("des étoiles à midi", 0f, ciel(minuit + 12 * heure).starAlpha, 1e-3f)
        assertTrue("pas d'étoiles à minuit", ciel(minuit).starAlpha > 0.9f)
    }

    /**
     * **Une pleine lune se lève quand le soleil se couche.**
     *
     * C'est le détail qu'on ne sait pas nommer mais qu'on sent : une lune qui montrerait
     * un croissant en plein milieu de la nuit, ou un disque plein à midi, aurait l'air
     * fausse sans qu'on puisse dire pourquoi. La position sort de l'élongation, donc des
     * éphémérides, donc elle est juste par construction — ce test vérifie qu'on n'a pas
     * inversé le signe en chemin.
     */
    @Test
    fun `la lune est au bon endroit du ciel pour la phase qu elle montre`() {
        // On balaie un mois lunaire à la recherche d'une pleine lune et d'une nouvelle.
        var pleine = -1L
        var nouvelle = -1L
        for (j in 0 until 30) {
            val t = minuit + j * 24L * heure
            val p = ciel(t).moonPhase
            if (p > 0.99f && pleine < 0) pleine = t
            if (p < 0.01f && nouvelle < 0) nouvelle = t
        }
        assertTrue("aucune pleine lune en un mois", pleine > 0)
        assertTrue("aucune nouvelle lune en un mois", nouvelle > 0)

        // Pleine lune : à minuit elle est haute, à midi elle est couchée.
        val minuitPleine = ciel(pleine - pleine % (24L * heure) + 0 * heure)
        val midiPleine = ciel(pleine - pleine % (24L * heure) + 12 * heure)
        println(
            "LUNE pleine : phase ${"%.2f".format(minuitPleine.moonPhase)}, " +
                "hauteur à minuit ${"%.2f".format(minuitPleine.moonAltitude)}, " +
                "à midi ${"%.2f".format(midiPleine.moonAltitude)}"
        )
        assertTrue(
            "une pleine lune devrait être haute à minuit",
            minuitPleine.moonAltitude > midiPleine.moonAltitude
        )

        // Nouvelle lune : elle suit le soleil, donc elle est au ciel en plein jour.
        val midiNouvelle = ciel(nouvelle - nouvelle % (24L * heure) + 12 * heure)
        println(
            "LUNE nouvelle : phase ${"%.2f".format(midiNouvelle.moonPhase)}, " +
                "hauteur à midi ${"%.2f".format(midiNouvelle.moonAltitude)}"
        )
        assertTrue(
            "une nouvelle lune devrait accompagner le soleil",
            midiNouvelle.moonAltitude > 0.5f
        )
    }

    @Test
    fun `la phase parcourt tout le cycle en un mois lunaire`() {
        var mini = 1f
        var maxi = 0f
        for (j in 0 until 30) {
            val p = ciel(minuit + j * 24L * heure).moonPhase
            if (p < mini) mini = p
            if (p > maxi) maxi = p
        }
        println("LUNE phase de ${"%.2f".format(mini)} à ${"%.2f".format(maxi)} sur un mois")
        assertTrue("la lune ne devient jamais nouvelle : $mini", mini < 0.05f)
        assertTrue("la lune ne devient jamais pleine : $maxi", maxi > 0.95f)
    }

    /**
     * Le crépuscule passe par **l'orange et le violet**, et pas par un gris.
     *
     * C'est toute la raison d'avoir cinq paliers de couleur au lieu de deux : entre une
     * nuit bleue et un jour bleu, une interpolation directe ne donne rien. On vérifie
     * donc que l'horizon devient franchement plus rouge que bleu au moment du coucher,
     * ce qu'aucune interpolation nuit↔jour ne ferait.
     */
    @Test
    fun `le couchant rougit l horizon`() {
        fun rouge(c: Int) = (c shr 16) and 0xFF
        fun bleu(c: Int) = c and 0xFF

        val midi = ciel(minuit + 12 * heure)
        val couchant = ciel(minuit + 18 * heure)
        val nuit = ciel(minuit)

        println(
            "CIEL horizon midi=${"%06X".format(midi.horizon and 0xFFFFFF)} " +
                "couchant=${"%06X".format(couchant.horizon and 0xFFFFFF)} " +
                "nuit=${"%06X".format(nuit.horizon and 0xFFFFFF)}"
        )
        assertTrue("un midi bleu devrait l'être", bleu(midi.horizon) > rouge(midi.horizon))
        assertTrue("un couchant devrait rougir", rouge(couchant.horizon) > bleu(couchant.horizon) + 40)
        assertTrue("une nuit devrait rester bleue", bleu(nuit.horizon) > rouge(nuit.horizon))
    }

    /**
     * **Une éclipse finit par arriver, et pas tous les mois.**
     *
     * Les deux moitiés comptent autant : un ciel où le Soleil s'éteint à chaque nouvelle
     * lune serait faux et lassant, un ciel où ça n'arrive jamais ne mériterait pas le
     * code. On balaie donc trois années de jeu et on demande que ce soit rare — mais
     * réel.
     */
    @Test
    fun `une eclipse arrive parfois et jamais tous les mois`() {
        var jours = 0
        var eclipses = 0
        var pire = 0f
        val pas = heure / 2
        var t = minuit
        var dansUne = false
        while (t < minuit + 3L * 365 * 24 * heure) {
            val e = ciel(t).eclipse
            if (e > pire) pire = e
            if (e > 0f && !dansUne) {
                eclipses++
                dansUne = true
            } else if (e == 0f) {
                dansUne = false
            }
            t += pas
            jours++
        }
        println(
            "ÉCLIPSES $eclipses en trois ans, couverture maximale ${"%.2f".format(pire)}"
        )
        assertTrue("aucune éclipse en trois ans : le code ne sert à rien", eclipses >= 2)
        assertTrue("une éclipse par mois : ce n'est plus une éclipse", eclipses <= 20)
        assertTrue("aucune éclipse n'est franche : $pire", pire > 0.4f)
    }

    @Test
    fun `une eclipse assombrit sans faire la nuit`() {
        // On cherche le moment le plus couvert, puis on regarde ce qu'il fait.
        var meilleur = minuit
        var pire = 0f
        var t = minuit
        while (t < minuit + 3L * 365 * 24 * heure) {
            val c = ciel(t)
            // Une éclipse ne se voit que si le soleil est levé.
            if (c.eclipse > pire && c.sunAltitude > 0.3f) {
                pire = c.eclipse
                meilleur = t
            }
            t += heure / 2
        }
        val pendant = ciel(meilleur)
        // Le jour suivant, à la même heure : même soleil, plus d'éclipse. Comparer à
        // douze heures d'écart comparerait à la nuit, ce qui ne dit rien.
        val avant = ciel(meilleur + 24 * heure)
        println(
            "ÉCLIPSE couverture ${"%.2f".format(pendant.eclipse)}, " +
                "clarté ${"%.2f".format(pendant.light)} contre ${"%.2f".format(avant.light)} un jour ordinaire"
        )
        assertTrue("aucune éclipse de jour trouvée", pire > 0.3f)
        assertTrue("l'éclipse n'assombrit rien", pendant.light < avant.light)
        assertTrue("l'éclipse fait la nuit noire", pendant.light > 0.05f)
    }

    @Test
    fun `l horloge court soixante-douze fois plus vite que la vraie`() {
        val h = SkyClock(minuit)
        h.advance(SkyClock.DAY_SECONDS)
        val ecoule = h.instant - minuit
        assertTrue(
            "un jour de jeu ne fait pas vingt minutes réelles : $ecoule ms",
            kotlin.math.abs(ecoule - 24L * heure) < 60_000L
        )

        val avant = h.instant
        h.scrub(-3f)
        assertEquals("le balayage ne recule pas", -3 * heure, h.instant - avant)
    }
}
