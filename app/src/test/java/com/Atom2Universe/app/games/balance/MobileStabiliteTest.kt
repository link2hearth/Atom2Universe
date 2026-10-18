package com.Atom2Universe.app.games.balance

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Le mobile tient-il tout seul ? Un mobile juste doit pendre droit, immobile ; une erreur d'un
 * seul poids doit se voir ; et rien ne doit trembler. Les montages sont écrits à la main, poids
 * et crans compris : aucun tableau n'est généré ici.
 */
class MobileStabiliteTest {

    private fun crochet(masse: Int) = BoutCrochet().apply { objet = MobileObjet(masse) }

    private fun monter(racine: MobileTige): MobileMonde {
        MobileMiseEnPage.etendue(racine, racine.crochets().maxOf { it.objet?.rayon ?: 0.2f })
        return MobileMonde(racine, 6f)
    }

    private fun MobileMonde.simuler(secondes: Float) {
        repeat((secondes * 120f).toInt()) { step(1f / 120f) }
    }

    private fun MobileMonde.pentes(): List<Float> = racine.tiges().map { penteDeg(it) }

    private fun MobileMonde.texte() = pentes().joinToString { "%.2f°".format(it) }

    /**
     * Une chaîne à la Calder, juste :
     *  - bas : 1 kg au cran 2 contre 2 kg au cran 1 ;
     *  - milieu : 3 kg contre le bas (3 kg), crans égaux ;
     *  - haut : 4 kg au cran 3 contre le milieu (6 kg) au cran 2.
     */
    private fun chaineJuste(): MobileTige {
        val bas = MobileTige(2, 1, crochet(1), crochet(2))
        val milieu = MobileTige(1, 1, crochet(3), BoutTige(bas))
        return MobileTige(3, 2, crochet(4), BoutTige(milieu))
    }

    /** Un arbre complet de quatre objets, juste : (2 | 4 en 2:1) contre (3 | 3 en 1:1), 6 contre 6. */
    private fun arbreJuste(): MobileTige = MobileTige(
        1, 1,
        BoutTige(MobileTige(2, 1, crochet(2), crochet(4))),
        BoutTige(MobileTige(1, 1, crochet(3), crochet(3)))
    )

    @Test
    fun `les montages ecrits a la main sont justes`() {
        assertTrue(chaineJuste().equilibree())
        assertTrue(arbreJuste().equilibree())
    }

    @Test
    fun `un mobile juste se pose droit et ne bouge plus`() {
        for (racine in listOf(chaineJuste(), arbreJuste())) {
            val m = monter(racine)
            m.simuler(8f)
            assertTrue("tiges penchées : ${m.texte()}", m.pentes().all { abs(it) < 0.5f })
            // Pendant une seconde de plus, rien ne bouge d'un dixième de degré.
            val avant = m.pentes()
            var ecart = 0f
            repeat(120) {
                m.step(1f / 120f)
                ecart = maxOf(ecart, m.pentes().zip(avant).maxOf { (a, b) -> abs(a - b) })
            }
            assertTrue("ça tremble de %.3f°".format(ecart), ecart < 0.1f)
        }
    }

    @Test
    fun `un poids de travers se voit`() {
        // La chaîne juste, avec le 1 et le 2 du bas échangés : le bas penche franchement.
        val bas = MobileTige(2, 1, crochet(2), crochet(1))
        val milieu = MobileTige(1, 1, crochet(3), BoutTige(bas))
        val racine = MobileTige(3, 2, crochet(4), BoutTige(milieu))
        val m = monter(racine)
        m.simuler(8f)
        val penteBas = abs(m.penteDeg(bas))
        assertTrue("le bas faux ne penche que de %.1f°".format(penteBas), penteBas > 8f)
        // Et du bon côté : trop lourd à gauche, la gauche descend (angle positif, sens trigo).
        assertTrue(m.penteDeg(bas) > 0f)
    }

    @Test
    fun `une tige vide pend droite`() {
        val racine = MobileTige(3, 1, BoutCrochet(), BoutCrochet())
        val m = monter(racine)
        m.simuler(4f)
        assertTrue("tige vide penchée : ${m.texte()}", abs(m.penteDeg(racine)) < 0.5f)
    }

    @Test
    fun `un mobile tres faux penche sans se retourner ni s'emballer`() {
        // Tout le poids d'un côté, rien de l'autre.
        val bas = MobileTige(1, 2, crochet(9), BoutCrochet())
        val racine = MobileTige(1, 3, crochet(9), BoutTige(bas))
        val m = monter(racine)
        m.simuler(10f)
        // La butée tient : une tige qui se dresse emporte ses crochets hors de portée.
        for (p in m.pentes()) assertTrue("tige trop penchée : ${m.texte()}", abs(p) < 25f)
        val corps = m.corps(racine)
        assertTrue("la tige a fui", hypot(corps.x, corps.y - 5.4f) < 1.5f)
    }

    @Test
    fun `accrocher puis decrocher rend le mobile a son etat`() {
        val racine = MobileTige(2, 1, BoutCrochet(), BoutCrochet())
        val m = monter(racine)
        val (gauche, droite) = racine.crochets()
        m.accrocher(gauche, MobileObjet(2))
        m.accrocher(droite, MobileObjet(4))
        m.simuler(6f)
        assertTrue("juste mais penchée : ${m.texte()}", abs(m.penteDeg(racine)) < 0.5f)
        m.decrocher(droite)
        m.simuler(6f)
        // Penchée franchement, mais retenue par sa butée.
        assertTrue("2 kg seuls à gauche devraient la pencher : ${m.texte()}", m.penteDeg(racine) > 7f)
        m.decrocher(gauche)
        m.simuler(6f)
        assertTrue("vide, elle devrait se remettre droite : ${m.texte()}", abs(m.penteDeg(racine)) < 0.5f)
    }
}
