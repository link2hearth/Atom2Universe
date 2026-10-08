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

    // ── Le tapis et la bille du joueur ───────────────────────────────────────

    @Test
    fun `le tapis entraine ce qui roule dessus`() {
        // Un tapis ne « donne » pas une vitesse : il demande aux deux surfaces une vitesse
        // relative, que le solveur fournit dans la limite du frottement. Ce qui se mesure,
        // c'est donc que la bille parte — et qu'elle parte du bon cote.
        fun essai(miroir: Boolean): Float {
            val monde = Plateau()
            monde.poser(Pose(TypePiece.TAPIS, x = 0f, y = 0.5f, taille = 3f, miroir = miroir).creer())
            monde.poserBille(x = -0.6f, y = 0.85f)
            repeat(180) { monde.avancer(PAS) }
            return monde.bille!!.x
        }
        val droite = essai(miroir = false)
        val gauche = essai(miroir = true)
        assertTrue("le tapis n'entraine pas vers la droite : $droite", droite > 0.2f)
        assertTrue("le miroir n'inverse pas le tapis : $gauche", gauche < -1f)
    }

    @Test
    fun `la bille du joueur est en tout point celle du tableau`() {
        // Une bille de joueur qui se comporterait autrement serait un piege : le joueur
        // passerait son temps a se demander laquelle il regarde.
        val piece = Pose(TypePiece.BILLE, x = 0f, y = 1f).creer()
        val posee = piece.principal
        val reference = Plateau().poserBille(0f, 1f)
        assertEquals("le rayon differe", reference.radius, posee.radius, 1e-4f)
        assertEquals("la masse differe", reference.mass, posee.mass, 1e-4f)
        assertEquals("le rebond differe", reference.restitution, posee.restitution, 1e-4f)
        assertTrue("la bille du joueur est scellee", !posee.immovable)
        assertEquals("la bille ne repose pas sur le point vise", 1f, posee.y - posee.radius, 1e-4f)
    }

    @Test
    fun `un tapis descendant lance plus fort qu une planche`() {
        // A quoi sert l'inclinaison, puisqu'elle ne fait pas remonter : a **lancer**. Une
        // bande descendante ajoute sa vitesse a celle de la chute, ce qu'aucune planche ne
        // sait faire.
        fun essai(tapis: Boolean): Float {
            val monde = Plateau()
            val pose = if (tapis) {
                Pose(TypePiece.TAPIS, x = 0f, y = 1f, reglage = 20f, taille = 3f)
            } else {
                Pose(TypePiece.RAMPE, x = 0f, y = 1f, reglage = 20f, taille = 3f)
            }
            monde.poser(pose.creer())
            monde.poserBille(x = -1.2f, y = 1.7f)
            repeat(210) { monde.avancer(PAS) }
            return monde.bille!!.x
        }
        val avec = essai(tapis = true)
        val sans = essai(tapis = false)
        assertTrue("le tapis ne lance pas plus loin qu'une planche : $avec contre $sans",
            avec > sans + 0.5f)
    }

    @Test
    fun `un tapis ne remonte pas une bille`() {
        // **Ce test consigne une limite, pas un objectif.** Un tapis impose une vitesse au
        // point de contact ; pour une bille, les deux tiers de cette vitesse partent en
        // rotation, et le tiers restant ne suffit pas contre la pesanteur. C'est ce que fait
        // un vrai tapis, qui transporte des caisses et pas des billes. Le jour ou quelqu'un
        // « corrigera » ca en montant le frottement, ce test dira que le probleme est
        // ailleurs — et la documentation de `Pieces.tapis` dira ou.
        val monde = Plateau()
        monde.poser(Pose(TypePiece.TAPIS, x = 0f, y = 1f, reglage = -15f, taille = 3f).creer())
        monde.poserBille(x = -1.2f, y = 1.1f)
        repeat(60) { monde.avancer(PAS) }
        val surLaBande = monde.bille!!.y
        var sommet = surLaBande
        repeat(240) {
            monde.avancer(PAS)
            if (monde.bille!!.y > sommet) sommet = monde.bille!!.y
        }
        assertTrue(
            "la bille est montee de ${sommet - surLaBande} m : la limite documentee a bouge",
            sommet < surLaBande + 0.1f
        )
    }

    // ── Le miroir ────────────────────────────────────────────────────────────

    @Test
    fun `le miroir inverse le sens de marche du tapis`() {
        // Le tapis est la seule piece dont le cote ne soit **pas** un angle : une bande
        // horizontale peut entrainer dans les deux sens. C'est exactement pour ce cas-la
        // que le miroir existe encore, la rampe et le ventilateur ayant leur angle.
        val tapis = Pose(TypePiece.TAPIS, x = 0f, y = 1f)
        assertEquals(
            "le miroir n'inverse pas la bande",
            -tapis.creer().principal.surfaceSpeed,
            tapis.copy(miroir = true).creer().principal.surfaceSpeed,
            1e-4f
        )
        assertEquals(
            "le miroir a aussi touche a l'inclinaison",
            tapis.creer().principal.angle,
            tapis.copy(miroir = true).creer().principal.angle,
            1e-4f
        )
    }

    @Test
    fun `le miroir echange les deux plateaux de la poulie`() {
        // Le godet a gauche et le contrepoids a droite, ou l'inverse. C'est la seule chose
        // que le miroir doive faire sur cette piece, et elle ne doit rien casser d'autre :
        // la meme machine, vue dans un miroir, gagne toujours.
        val droite = Pose(TypePiece.POULIE, x = 0f, y = 0.2f)
        val gauche = droite.copy(miroir = true)
        assertTrue("le godet n'est pas passe a droite",
            droite.creer().corps[1].x < 0f && gauche.creer().corps[1].x > 0f)
        assertTrue("le contrepoids n'est pas passe a gauche",
            droite.creer().corps[2].x > 0f && gauche.creer().corps[2].x < 0f)

        val monde = Plateau()
        monde.poser(gauche.creer())
        monde.poserBouton(x = -Pieces.POULIE_ECART, bas = Pieces.poulieContrepoidsHaut(0.2f) - 0.02f)
        monde.poserBille(x = Pieces.POULIE_ECART, y = Pieces.poulieGodetHaut(0.2f) + 0.25f)
        monde.derouler(10f)
        assertTrue("la poulie retournee ne fonctionne plus", monde.gagne)
    }

    @Test
    fun `seules les pieces qui ont un cote sont miroitables`() {
        // Proposer le bouton sur une bascule ou un tambour mentirait sur ce qu'il fait :
        // les retourner ne change rien.
        // On compare **tous** les corps, pas seulement le principal. Le mat d'une poulie
        // est au milieu : il ne bouge pas au retournement, et regarder lui seul concluait
        // que la poulie n'avait pas de cote.
        for (type in TypePiece.entries) {
            fun pose(miroir: Boolean) = Pose(type, x = 0f, y = 1.2f, reglage = 20f, miroir = miroir)
                .creer().corps.map { listOf(it.x, it.y, it.angle, it.surfaceSpeed) }
            val change = pose(false) != pose(true)
            assertEquals("le type $type annonce mal son cote", type.miroitable, change)
        }
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
