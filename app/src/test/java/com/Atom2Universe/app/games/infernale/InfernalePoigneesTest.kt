package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Les poignees : tirer un bout de planche, l'autre restant ou il est.
 *
 * C'est de la geometrie pure — une pose et un point du monde entrent, une pose sort — donc
 * ca se teste sans ecran, et c'est bien la raison pour laquelle ce calcul ne vit pas dans
 * la vue. Une poignee qui deplacerait le bout qu'on voulait garder ne se verrait a l'oeil
 * que comme « l'interface est bizarre », ce qui ne se diagnostique pas.
 */
class InfernalePoigneesTest {

    /** Les deux bouts d'une planche, tels que la poignee les designe. */
    private fun bouts(pose: Pose): Pair<Poignee, Poignee> {
        val liste = Poignees.pour(pose)
        return liste.first { it.prise == Prise.BOUT_DEBUT } to
            liste.first { it.prise == Prise.BOUT_FIN }
    }

    @Test
    fun `tirer un bout laisse l autre exactement ou il etait`() {
        // **La promesse de la poignee, et tout le reste en decoule.** On tient une
        // extremite et on tire l'autre : si le point tenu bouge, le geste ne veut plus rien
        // dire et il faut corriger apres coup, ce qui etait tout le defaut des curseurs.
        val pose = Pose(TypePiece.RAMPE, x = 0f, y = 2f, reglage = 20f, taille = 1.5f)
        val (debut, fin) = bouts(pose)

        val tiree = Poignees.tirer(pose, Prise.BOUT_FIN, 2.2f, 0.9f)
        val (debutApres, _) = bouts(tiree)
        assertEquals("le bout fixe a bouge en x", debut.x, debutApres.x, 1e-3f)
        assertEquals("le bout fixe a bouge en y", debut.y, debutApres.y, 1e-3f)

        // Et l'inverse : c'est l'autre bout qui doit tenir.
        val autre = Poignees.tirer(pose, Prise.BOUT_DEBUT, -2.2f, 3.1f)
        val (_, finApres) = bouts(autre)
        assertEquals("le bout fixe a bouge en x", fin.x, finApres.x, 1e-3f)
        assertEquals("le bout fixe a bouge en y", fin.y, finApres.y, 1e-3f)
    }

    @Test
    fun `le bout tire arrive sous le doigt`() {
        val pose = Pose(TypePiece.RAMPE, x = 0f, y = 2f, reglage = 20f, taille = 1.5f)
        val tiree = Poignees.tirer(pose, Prise.BOUT_FIN, 1.4f, 1.2f)
        val (_, fin) = bouts(tiree)
        assertEquals("le bout tire n'a pas suivi le doigt en x", 1.4f, fin.x, 0.02f)
        assertEquals("le bout tire n'a pas suivi le doigt en y", 1.2f, fin.y, 0.02f)
    }

    @Test
    fun `tirer regle longueur et angle du meme geste`() {
        // C'est la raison d'etre de la poignee : on pense « d'ici a la », pas « vingt-deux
        // degres sur un metre cinquante ».
        val pose = Pose(TypePiece.RAMPE, x = 0f, y = 2f, reglage = 0f, taille = 1f)
        val (debut, _) = bouts(pose)
        val tiree = Poignees.tirer(pose, Prise.BOUT_FIN, debut.x + 2f, debut.y - 2f)

        assertEquals("la longueur ne suit pas la distance",
            hypot(2f, 2f), Poignees.longueur(tiree), 0.02f)
        // Une planche qui descend de 45 degres vers la droite : la pente est positive.
        assertEquals("la pente ne suit pas la direction", 45f, tiree.reglage, 1.5f)
    }

    @Test
    fun `la longueur reste dans des bornes praticables`() {
        val pose = Pose(TypePiece.RAMPE, x = 0f, y = 2f, reglage = 0f, taille = 1.5f)
        val (debut, _) = bouts(pose)
        val minuscule = Poignees.tirer(pose, Prise.BOUT_FIN, debut.x + 0.01f, debut.y)
        val geante = Poignees.tirer(pose, Prise.BOUT_FIN, debut.x + 40f, debut.y)
        assertEquals("pas de plancher sur la longueur",
            Poignees.LONGUEUR_MIN, Poignees.longueur(minuscule), 1e-3f)
        assertEquals("pas de plafond sur la longueur",
            Poignees.LONGUEUR_MAX, Poignees.longueur(geante), 1e-3f)
    }

    @Test
    fun `le doigt pose sur le point fixe ne fait pas disparaitre la planche`() {
        // Le seul cas degenere : plus aucune direction. On garde celle qu'on avait plutot
        // que de produire une planche de longueur nulle et d'angle indefini.
        val pose = Pose(TypePiece.RAMPE, x = 0f, y = 2f, reglage = 20f, taille = 1.5f)
        val (debut, _) = bouts(pose)
        val tiree = Poignees.tirer(pose, Prise.BOUT_FIN, debut.x, debut.y)
        assertTrue("la planche s'est evaporee", Poignees.longueur(tiree) >= Poignees.LONGUEUR_MIN)
        assertTrue("la pente est devenue absurde", abs(tiree.reglage) <= 90f)
        assertTrue("la piece est partie a l'infini", abs(tiree.x) < 10f && abs(tiree.y) < 10f)
    }

    @Test
    fun `le tremplin ne se regle que par son bout libre`() {
        // Sa charniere appartient a la piece : lui proposer un angle serait mentir, puisque
        // le volet le reprendrait au premier pas de simulation.
        val pose = Pose(TypePiece.TREMPLIN, x = 0f, y = 0f, taille = 0.7f)
        val poignees = Poignees.pour(pose)
        assertEquals("le tremplin devrait n'avoir qu'une poignee", 1, poignees.size)

        val charniere = pose.x - pose.taille / 2f
        val tiree = Poignees.tirer(pose, Prise.BOUT_FIN, 1.6f, 0.35f)
        assertEquals("la charniere a bouge",
            charniere, tiree.x - Poignees.longueur(tiree) / 2f, 1e-3f)
        assertTrue("le volet ne s'est pas allonge", Poignees.longueur(tiree) > pose.taille)
    }

    @Test
    fun `le coin d un bloc regle ses deux cotes separement`() {
        // C'est ce qui transforme le bloc en piece a tout faire : un mur haut et fin, une
        // plateforme large et plate, tout cela d'un seul geste.
        val pose = Pose(TypePiece.BLOC, x = 0f, y = 1f)
        val tiree = Poignees.tirer(pose, Prise.COIN, 0.9f, 1.15f)
        assertEquals("la largeur ne suit pas", 1.8f, tiree.taille, 1e-3f)
        assertEquals("la hauteur ne suit pas", 0.3f, tiree.taille2, 1e-3f)
    }

    @Test
    fun `le jet d un ventilateur se vise au doigt`() {
        val pose = Pose(TypePiece.VENTILATEUR, x = 0f, y = 1f, reglage = 0f)
        val versLeHaut = Poignees.tirer(pose, Prise.JET, 0f, 2f)
        assertEquals("le jet ne s'est pas tourne vers le haut", 90f, versLeHaut.reglage, 1e-3f)
        val versLaGauche = Poignees.tirer(pose, Prise.JET, -2f, 1f)
        assertEquals("le jet ne s'est pas tourne vers la gauche", 180f, abs(versLaGauche.reglage), 1e-3f)
    }

    @Test
    fun `chaque piece reglable rend des poignees, les autres non`() {
        // Le garde-fou contre l'oubli : ajouter une piece sans lui donner de poignee la
        // rendrait figee sans que rien ne le signale.
        val sansPoignee = setOf(TypePiece.TORCHE)
        for (type in TypePiece.entries) {
            val poignees = Poignees.pour(Pose(type, x = 0f, y = 1f, reglage = 15f))
            assertEquals(
                "le type $type n'annonce pas les bonnes poignees",
                type !in sansPoignee,
                poignees.isNotEmpty()
            )
        }
    }

    @Test
    fun `une poignee tiree donne une piece que la partie accepte`() {
        // La geometrie a beau etre juste, elle ne sert a rien si la partie refuse le
        // resultat. On passe donc par les vrais gestes : poser, puis regler.
        val p = Partie(
            Tableau(graine = 0L, billeX = -4f, billeY = 4.5f, boutonX = 3f, boutonBas = 0f)
        )
        val depart = Pose(TypePiece.RAMPE, x = 0f, y = 2f, reglage = 10f, taille = 1.5f)
        assertEquals(Refus.OK, p.poser(depart))

        val tiree = Poignees.tirer(p.placees()[0], Prise.BOUT_FIN, 1.6f, 1.2f)
        assertEquals("la partie refuse une planche pourtant reglee au doigt",
            Refus.OK, p.deplacer(0, tiree))
        assertEquals("le reglage n'a pas ete garde", tiree, p.placees()[0])
    }
}
