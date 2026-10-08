package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * L'accroche : ce qui tient une pièce, et ce qui ne la tient pas.
 *
 * Trois cas, et le troisième est celui qui compte — une bascule dont **le pied est collé
 * mais pas la planche**. Si ce cas-là marche, le modèle est bon ; s'il ne marche pas,
 * aucune quantité de pièces supplémentaires ne sauvera le jeu.
 */
class InfernaleAncrageTest {

    private fun plateau() = Plateau()

    @Test
    fun `une piece scellee ne tombe jamais, meme en l air`() {
        // C'est tout l'intérêt d'une rampe : elle tient sans pied, en plein vide.
        val p = plateau()
        val rampe = p.poser(Pieces.rampe(x = 0f, y = 3f, pente = 25f))
        val depart = rampe.principal.y
        p.derouler(5f)
        assertEquals("la rampe scellée est tombée", depart, rampe.principal.y, 0f)
        assertEquals("une pièce scellée n'a que des corps scellés",
            rampe.corps.size, rampe.scelles.size)
        assertTrue("une pièce scellée ne doit rien avoir de mobile", rampe.mobiles.isEmpty())
    }

    @Test
    fun `une piece libre tombe et se renverse`() {
        val p = plateau()
        // Posé en l'air : il doit tomber sur le sol.
        val domino = p.poser(Pieces.domino(x = 0f, bas = 2f))
        p.derouler(3f)
        assertTrue("le domino libre n'est pas tombé", domino.principal.y < 1f)
        assertTrue("une pièce libre ne doit rien avoir de scellé", domino.scelles.isEmpty())
    }

    @Test
    fun `une bascule garde son pied et laisse partir sa planche`() {
        // Le cas qui justifie le modèle. Le pied ne bouge pas d'un micron ; la planche,
        // chargée d'un côté, tourne autour de lui.
        val p = plateau()
        val bascule = p.poser(Pieces.bascule(x = 0f, bas = 0f))
        val pied = bascule.scelles.single()
        val planche = bascule.mobiles.single()
        val piedDepart = pied.x to pied.y
        val plancheDepart = planche.angle

        // On laisse tomber un poids sur l'extrémité droite.
        val poids = p.poserBille(x = 0.45f, y = 1.4f, masse = 3f)
        p.derouler(3f)

        assertEquals("le pied de la bascule a bougé en x", piedDepart.first, pied.x, 0f)
        assertEquals("le pied de la bascule a bougé en y", piedDepart.second, pied.y, 0f)
        assertTrue("la planche n'a pas basculé : ${planche.angle} rad",
            abs(planche.angle - plancheDepart) > 0.15f)
        assertTrue("la planche s'est détachée de son pied",
            abs(planche.x - pied.x) < 0.2f)
        assertTrue("le poids n'est pas descendu", poids.y < 1.4f)
    }

    @Test
    fun `une bascule au repos reste droite`() {
        // Sans charge, la planche doit tenir à l'horizontale et ne pas glisser
        // doucement d'un côté : un pivot bien posé est un pivot centré.
        val p = plateau()
        val bascule = p.poser(Pieces.bascule(x = 0f, bas = 0f))
        val planche = bascule.mobiles.single()
        p.derouler(5f)
        assertTrue("la bascule à vide part toute seule : ${planche.angle} rad",
            abs(planche.angle) < 0.05f)
    }

    @Test
    fun `un tremplin garde son socle et renvoie ce qui tombe dessus`() {
        val p = plateau()
        val tremplin = p.poser(Pieces.tremplin(x = 0f, bas = 0f))
        assertEquals("un tremplin a deux points scellés", 2, tremplin.scelles.size)
        assertEquals("un tremplin n'a qu'un volet mobile", 1, tremplin.mobiles.size)

        val bille = p.poserBille(x = 0.2f, y = 1.5f)
        var plusHaut = 0f
        var t = 0f
        while (t < 4f) {
            p.avancer(1f / 120f)
            t += 1f / 120f
            // On ne mesure la remontée qu'après le premier contact.
            if (bille.vy > 0f && bille.y > plusHaut) plusHaut = bille.y
        }
        // Mesuré à 61 % du lâcher avec les cotes par défaut ; on garde de la marge.
        assertTrue("le tremplin ne renvoie plus assez : plus haut = $plusHaut", plusHaut > 0.7f)
    }

    @Test
    fun `retirer une piece ne touche pas aux autres`() {
        // Le geste qu'on va faire cent fois dans l'éditeur : reprendre une pièce.
        val p = plateau()
        val gardee = p.poser(Pieces.bloc(x = -1f, y = 0.2f))
        val bascule = p.poser(Pieces.bascule(x = 1f, bas = 0f))
        val reprise = p.poser(Pieces.domino(x = 0f, bas = 0f))
        val avant = p.monde.bodies.size

        p.retirer(reprise)

        assertEquals("le domino n'a pas été retiré", avant - 1, p.monde.bodies.size)
        assertTrue("le bloc gardé a disparu", gardee.principal in p.monde.bodies)
        assertTrue("la bascule a perdu un corps",
            bascule.corps.all { it in p.monde.bodies })
        assertTrue("le pivot de la bascule a été emporté", p.monde.joints.isNotEmpty())
        // Et le monde continue de tourner sans elle.
        p.derouler(2f)
    }

    @Test
    fun `le bouton ne se declenche pas tout seul`() {
        val p = plateau()
        val bouton = p.poserBouton(x = 0f, bas = 0f)
        p.derouler(6f)
        assertFalse("le bouton s'est declenche sans rien", bouton.declenche)
    }

    @Test
    fun `le bouton ignore le decor et les pieces scellees`() {
        // Le piege de la zone de detection : posee a meme le sol, elle le touche. Et une
        // rampe scellee peut tres bien la traverser sans que ca veuille rien dire.
        val p = plateau()
        val bouton = p.poserBouton(x = 0f, bas = 0f)
        p.poser(Pieces.bloc(x = 0f, y = 0.1f))
        p.poser(Pieces.rampe(x = 0f, y = 0.15f, longueur = 1f))
        p.derouler(4f)
        assertFalse("le decor a declenche le bouton", bouton.declenche)
    }

    @Test
    fun `le bouton se declenche des que quelque chose entre, et le reste`() {
        val p = plateau()
        val bouton = p.poserBouton(x = 0f, bas = 0f)
        val bille = p.poserBille(x = 0f, y = 1.6f)
        p.derouler(5f)
        assertTrue("la bille tombee dedans n'a pas declenche le bouton", bouton.declenche)
        assertSame("le bouton ne dit pas qui l'a declenche", bille, bouton.declencheur)

        // Le verrou : la bille peut repartir, le bouton reste gagne.
        p.derouler(3f)
        assertTrue("le bouton s'est degagne", bouton.declenche)
    }

    @Test
    fun `un domino qui tombe dans la zone suffit`() {
        // Le cas qui a coute trois versions d'un bouton a ressort : un domino couche ne
        // pese que deux newtons et demi, bien trop peu pour enfoncer quoi que ce soit.
        // Une zone, elle, n'a besoin d'aucune force.
        val p = plateau()
        val bouton = p.poserBouton(x = 0.45f, bas = 0f)
        // On le penche au-dela de son point d'equilibre : un domino bascule quand son
        // centre passe au-dela de son arete, soit ici 10 degres. Lui donner une vitesse
        // de rotation ne suffit pas — pose a plat, il se contente de vaciller et se
        // remet droit, ce qu'on a verifie a ses depens.
        val domino = p.poser(Pieces.domino(x = 0f, bas = 0f))
        domino.principal.angle = -0.30f
        p.derouler(5f)
        assertTrue("le domino tombe n'a pas atteint la zone", bouton.declenche)
    }

    @Test
    fun `le bouton se rearme`() {
        val p = plateau()
        val bouton = p.poserBouton(x = 0f, bas = 0f)
        p.poserBille(x = 0f, y = 1.2f)
        p.derouler(4f)
        assertTrue(bouton.declenche)
        bouton.rearmer()
        assertFalse("le rearmement n'a pas remis le bouton a zero", bouton.declenche)
        assertNull("le rearmement a garde l'ancien declencheur", bouton.declencheur)
    }
}
