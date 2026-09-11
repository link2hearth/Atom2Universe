package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Les trois pieces ajoutees — ventilateur, tambour, poulie — et ce qu'elles promettent.
 *
 * Chacune existe parce qu'elle fait **quelque chose qu'aucune autre ne fait**. Ce fichier
 * mesure precisement ce quelque chose : si le ventilateur ne deplace pas ce que la
 * pesanteur seule n'aurait pas deplace, il ne sert a rien ; si la poulie ne fait pas
 * monter son contrepoids quand la bille tombe dans le godet, elle n'est qu'un decor cher.
 */
class InfernalePiecesTest {

    private val PAS = 1f / 120f

    // ── Le ventilateur ───────────────────────────────────────────────────────

    @Test
    fun `le souffle deplace la bille lateralement`() {
        // Le controle est le meme monde sans ventilateur : la bille tombe et roule un
        // peu. La mesure n'a de sens que comme **difference** entre les deux.
        val sans = Plateau().apply { poserBille(0f, 1.5f) }
        repeat(360) { sans.avancer(PAS) }
        val temoin = sans.bille!!.x

        val avec = Plateau().apply {
            poser(Pieces.ventilateur(x = -1.4f, y = 0.4f, direction = 0f))
            poserBille(0f, 1.5f)
        }
        repeat(360) { avec.avancer(PAS) }
        val pousse = avec.bille!!.x

        assertTrue(
            "le ventilateur n'a pas pousse la bille : temoin $temoin, souffle $pousse",
            pousse > temoin + 0.5f
        )
    }

    @Test
    fun `le souffle ne touche pas ce qui est hors du jet`() {
        // Une zone qui deborderait rendrait le placement impossible a viser : le joueur
        // verrait un jet fin et subirait un effet large.
        val monde = Plateau().apply {
            // Le jet part vers la droite a hauteur 0,4 m ; la bille est posee bien
            // au-dessus, hors de la demi-largeur.
            poser(Pieces.ventilateur(x = -1.4f, y = 0.4f, direction = 0f))
            poserBille(0f, 2.5f)
        }
        val temoin = Plateau().apply { poserBille(0f, 2.5f) }
        repeat(60) { monde.avancer(PAS); temoin.avancer(PAS) }
        assertEquals(
            "le jet a agi sur une bille qui n'etait pas dedans",
            temoin.bille!!.x, monde.bille!!.x, 1e-3f
        )
    }

    @Test
    fun `le souffle suit sa direction`() {
        // Meme piece, direction opposee : la bille doit partir dans l'autre sens. C'est
        // ce qui verifie que le reglage est bien branche sur quelque chose.
        val droite = Plateau().apply {
            poser(Pieces.ventilateur(x = -1.4f, y = 0.4f, direction = 0f))
            poserBille(0f, 1.5f)
        }
        val gauche = Plateau().apply {
            poser(Pieces.ventilateur(x = 1.4f, y = 0.4f, direction = 180f))
            poserBille(0f, 1.5f)
        }
        repeat(300) { droite.avancer(PAS); gauche.avancer(PAS) }
        assertTrue(
            "les deux directions donnent le meme resultat : ${droite.bille!!.x} et ${gauche.bille!!.x}",
            droite.bille!!.x > gauche.bille!!.x + 1f
        )
    }

    // ── Le tambour ───────────────────────────────────────────────────────────

    @Test
    fun `le tambour renvoie la bille plus haut qu un tremplin`() {
        // La raison d'etre du tambour tient dans cette comparaison. Un tremplin rend les
        // deux tiers de ce qu'on lui donne — c'est mesure dans sa documentation — parce
        // qu'un ressort perd au solveur. La restitution de contact, elle, ne passe par
        // aucun ressort.
        val monde = Plateau().apply {
            poser(Pieces.tambour(x = 0f, y = 0.3f))
            poserBille(0f, 2.3f)
        }
        var sommet = 0f
        repeat(600) {
            monde.avancer(PAS)
            val b = monde.bille!!
            // On ne mesure qu'apres le premier rebond : avant, le sommet est le point de
            // lacher, ce qui ne dit rien du tambour.
            if (b.vy < 0f && b.y > sommet && it > 120) sommet = b.y
        }
        val chute = 2.3f - 0.35f
        val remonte = sommet - 0.35f
        assertTrue(
            "le tambour n'a rendu que ${(remonte / chute * 100).toInt()} % de la hauteur",
            remonte > chute * 0.7f
        )
    }

    // ── La poulie ────────────────────────────────────────────────────────────

    @Test
    fun `la poulie attend sans bouger`() {
        // Au repos le contrepoids l'emporte : il est en bas, le godet en haut, et la
        // machine attend. Une poulie qui se declencherait toute seule casserait chaque
        // tableau qui l'emploie.
        //
        // Ce qu'on mesure est **l'absence de derive**, pas l'absence de mouvement. Le
        // godet s'affaisse de deux centimetres et demi dans la premiere seconde : c'est
        // l'erreur permanente de la contrainte sous charge, comme une corde qui s'etire,
        // et elle se voit a peine. Ce qui serait grave, c'est qu'elle continue — un
        // affaissement qui ne s'arrete pas finit par vider le godet dans le sol.
        val monde = Plateau().apply { poser(Pieces.poulie(x = 0f, bas = 0.2f)) }
        val piece = monde.pieces.first()
        val godet = piece.corps[1]
        val depart = godet.y

        repeat(240) { monde.avancer(PAS) }
        val apresDeuxSecondes = godet.y
        repeat(480) { monde.avancer(PAS) }

        assertEquals("le godet continue de descendre", apresDeuxSecondes, godet.y, 0.005f)
        assertEquals("le godet s'est affaisse bien plus que l'etirement attendu",
            depart, godet.y, 0.05f)
    }

    @Test
    fun `la bille dans le godet fait monter le contrepoids`() {
        // **La promesse de la piece, et la seule chose qu'aucune autre ne sait faire :
        // transformer une chute en montee.**
        val bas = 0.2f
        val monde = Plateau().apply { poser(Pieces.poulie(x = 0f, bas = bas)) }
        val piece = monde.pieces.first()
        val godet = piece.corps[1]
        val contrepoids = piece.corps[2]
        val hautDepart = contrepoids.y

        // La bille tombe juste au-dessus de la margelle du godet.
        monde.poserBille(x = godet.x, y = Pieces.poulieGodetHaut(bas) + 0.25f)
        repeat(900) { monde.avancer(PAS) }

        val montee = contrepoids.y - hautDepart
        assertTrue(
            "le contrepoids n'est monte que de $montee m",
            montee > Pieces.POULIE_COURSE * 0.8f
        )
        // Le godet descend **plus** que le contrepoids ne monte, et c'est correct : les
        // deux brins sont obliques, et un brin oblique s'allonge moins vite que la chute
        // qui le tire. Ce qui compte est qu'il descende, et d'au moins autant.
        val descente = Pieces.poulieGodetHaut(bas) - 0.12f - godet.y
        assertTrue(
            "le godet n'est descendu que de $descente m pour une montee de $montee m",
            descente >= montee - 0.02f
        )
        assertEquals(
            "le contrepoids n'a pas atteint la hauteur annoncee par la fabrique",
            Pieces.poulieContrepoidsHaut(bas), contrepoids.y, 0.08f
        )
    }

    @Test
    fun `le contrepoids qui monte declenche le bouton`() {
        // La chaine complete telle que le generateur la construit : c'est ce montage-la
        // qui produit les tableaux dont le bouton est en l'air.
        val bas = 0.2f
        val monde = Plateau()
        monde.poser(Pieces.poulie(x = 0f, bas = bas))
        monde.poserBouton(
            x = Pieces.POULIE_ECART,
            bas = Pieces.poulieContrepoidsHaut(bas) - 0.02f
        )
        monde.poserBille(x = -Pieces.POULIE_ECART, y = Pieces.poulieGodetHaut(bas) + 0.25f)
        monde.derouler(10f)
        assertTrue("la poulie n'a pas declenche le bouton", monde.gagne)
    }

    // ── Toutes ───────────────────────────────────────────────────────────────

    @Test
    fun `chaque type de piece porte une etiquette de dessin`() {
        // Sans etiquette, la vue retombe sur son dessin generique : la piece marche mais
        // ressemble a un caillou gris, et personne ne comprend ce qu'elle fait.
        for (type in TypePiece.entries) {
            val piece = Pose(type, x = 0f, y = 1.2f, reglage = 15f).creer()
            for (corps in piece.corps) {
                assertTrue(
                    "le type $type a un corps sans etiquette de dessin",
                    corps.tag is Element
                )
            }
        }
    }

    @Test
    fun `seul le ventilateur souffle`() {
        for (type in TypePiece.entries) {
            val piece = Pose(type, x = 0f, y = 1.2f).creer()
            val attendu = type == TypePiece.VENTILATEUR
            assertEquals("le souffle du type $type", attendu, piece.souffle != null)
        }
    }
}
