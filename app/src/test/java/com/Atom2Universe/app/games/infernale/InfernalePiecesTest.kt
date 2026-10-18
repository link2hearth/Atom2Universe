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
        val sans = Plateau()
        val billeSans = sans.lacher(0f, 1.5f)
        repeat(360) { sans.avancer(PAS) }
        val temoin = billeSans.x

        val avec = Plateau()
        avec.poser(Pieces.ventilateur(x = -1.4f, y = 0.4f, direction = 0f))
        val billeAvec = avec.lacher(0f, 1.5f)
        repeat(360) { avec.avancer(PAS) }
        val pousse = billeAvec.x

        assertTrue(
            "le ventilateur n'a pas pousse la bille : temoin $temoin, souffle $pousse",
            pousse > temoin + 0.5f
        )
    }

    @Test
    fun `le souffle ne touche pas ce qui est hors du jet`() {
        // Une zone qui deborderait rendrait le placement impossible a viser : le joueur
        // verrait un jet fin et subirait un effet large.
        val monde = Plateau()
        // Le jet part vers la droite a hauteur 0,4 m ; la bille est posee bien
        // au-dessus, hors de la demi-largeur.
        monde.poser(Pieces.ventilateur(x = -1.4f, y = 0.4f, direction = 0f))
        val bille = monde.lacher(0f, 2.5f)
        val temoin = Plateau()
        val billeTemoin = temoin.lacher(0f, 2.5f)
        repeat(60) { monde.avancer(PAS); temoin.avancer(PAS) }
        assertEquals(
            "le jet a agi sur une bille qui n'etait pas dedans",
            billeTemoin.x, bille.x, 1e-3f
        )
    }

    @Test
    fun `le souffle suit sa direction`() {
        // Meme piece, direction opposee : la bille doit partir dans l'autre sens. C'est
        // ce qui verifie que le reglage est bien branche sur quelque chose.
        val droite = Plateau()
        droite.poser(Pieces.ventilateur(x = -1.4f, y = 0.4f, direction = 0f))
        val billeDroite = droite.lacher(0f, 1.5f)
        val gauche = Plateau()
        gauche.poser(Pieces.ventilateur(x = 1.4f, y = 0.4f, direction = 180f))
        val billeGauche = gauche.lacher(0f, 1.5f)
        repeat(300) { droite.avancer(PAS); gauche.avancer(PAS) }
        assertTrue(
            "les deux directions donnent le meme resultat : ${billeDroite.x} et ${billeGauche.x}",
            billeDroite.x > billeGauche.x + 1f
        )
    }

    @Test
    fun `un ventilateur plus puissant pousse plus loin`() {
        fun essai(force: Float): Float {
            val monde = Plateau()
            monde.poser(Pose(TypePiece.VENTILATEUR, x = -1.4f, y = 0.4f, reglage = 0f, force = force).creer())
            val bille = monde.lacher(0f, 1.5f)
            repeat(300) { monde.avancer(PAS) }
            return bille.x
        }
        val faible = essai(3f)
        val fort = essai(20f)
        assertTrue("la puissance ne change rien : $faible contre $fort", fort > faible + 1f)
    }

    @Test
    fun `la portee suit la puissance`() {
        val faible = Pose(TypePiece.VENTILATEUR, 0f, 1f, force = 3f).creer().souffle!!
        val fort = Pose(TypePiece.VENTILATEUR, 0f, 1f, force = 30f).creer().souffle!!
        assertTrue(fort.portee > faible.portee)
        assertEquals(Pieces.SOUFFLE_PORTEE, Pose(TypePiece.VENTILATEUR, 0f, 1f).creer().souffle!!.portee, 1e-3f)
    }

    // ── Le tambour ───────────────────────────────────────────────────────────

    @Test
    fun `le tambour renvoie la bille plus haut qu un tremplin`() {
        // La raison d'etre du tambour tient dans cette comparaison. Un tremplin rend les
        // deux tiers de ce qu'on lui donne — c'est mesure dans sa documentation — parce
        // qu'un ressort perd au solveur. La restitution de contact, elle, ne passe par
        // aucun ressort.
        val monde = Plateau()
        monde.poser(Pieces.tambour(x = 0f, y = 0.3f))
        val b = monde.lacher(0f, 2.3f)
        var sommet = 0f
        repeat(600) {
            monde.avancer(PAS)
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
        monde.lacher(x = godet.x, y = Pieces.poulieGodetHaut(bas) + 0.25f)
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

    // ── Le tapis et la bille du joueur ───────────────────────────────────────

    @Test
    fun `le tapis entraine ce qui roule dessus`() {
        // Un tapis ne « donne » pas une vitesse : il demande aux deux surfaces une vitesse
        // relative, que le solveur fournit dans la limite du frottement. Ce qui se mesure,
        // c'est donc que la bille parte — et qu'elle parte du bon cote.
        fun essai(miroir: Boolean): Float {
            val monde = Plateau()
            monde.poser(Pose(TypePiece.TAPIS, x = 0f, y = 0.5f, taille = 3f, miroir = miroir).creer())
            val bille = monde.lacher(x = -0.6f, y = 0.85f)
            repeat(180) { monde.avancer(PAS) }
            return bille.x
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
        val reference = Plateau().lacher(0f, 1f)
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
            val bille = monde.lacher(x = -1.2f, y = 1.7f)
            repeat(210) { monde.avancer(PAS) }
            return bille.x
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
        val bille = monde.lacher(x = -1.2f, y = 1.1f)
        repeat(60) { monde.avancer(PAS) }
        val surLaBande = bille.y
        var sommet = surLaBande
        repeat(240) {
            monde.avancer(PAS)
            if (bille.y > sommet) sommet = bille.y
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
        // la meme machine, vue dans un miroir, marche toujours.
        val droite = Pose(TypePiece.POULIE, x = 0f, y = 0.2f)
        val gauche = droite.copy(miroir = true)
        assertTrue("le godet n'est pas passe a droite",
            droite.creer().corps[1].x < 0f && gauche.creer().corps[1].x > 0f)
        assertTrue("le contrepoids n'est pas passe a gauche",
            droite.creer().corps[2].x > 0f && gauche.creer().corps[2].x < 0f)

        val monde = Plateau()
        val poulie = monde.poser(gauche.creer())
        val contrepoids = poulie.corps[2]
        val depart = contrepoids.y
        monde.lacher(x = Pieces.POULIE_ECART, y = Pieces.poulieGodetHaut(0.2f) + 0.25f)
        monde.derouler(10f)
        assertTrue("la poulie retournee ne fait plus monter son contrepoids",
            contrepoids.y - depart > Pieces.POULIE_COURSE * 0.8f)
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
                .creer().let { piece ->
                    piece.corps.map { listOf(it.x, it.y, it.angle, it.surfaceSpeed) } +
                        listOf(listOf(piece.tireAuDepart, piece.continu))
                }
            val change = pose(false) != pose(true)
            assertEquals("le type $type annonce mal son cote", type.miroitable, change)
        }
    }

    // ── Le ballon ────────────────────────────────────────────────────────────

    @Test
    fun `un ballon monte avec son panier`() {
        val monde = Plateau()
        val piece = monde.poser(Pose(TypePiece.BALLON, x = 0f, y = 0f).creer())
        val panier = piece.corps[0]
        val depart = panier.y
        monde.derouler(3f)
        assertTrue("le panier n'a pas decolle : ${panier.y - depart} m", panier.y > depart + 1f)
    }

    @Test
    fun `un ballon trop petit ne leve pas son panier`() {
        val monde = Plateau()
        val piece = monde.poser(Pose(TypePiece.BALLON, x = 0f, y = 0f, taille = 0.2f).creer())
        val panier = piece.corps[0]
        monde.derouler(3f)
        assertTrue("un tout petit ballon a leve le panier : ${panier.y}", panier.y < 0.5f)
    }

    @Test
    fun `un gros ballon leve une bille tombee dans son panier`() {
        val monde = Plateau()
        val piece = monde.poser(Pose(TypePiece.BALLON, x = 0f, y = 0f, taille = 0.7f).creer())
        val panier = piece.corps[0]
        // La bille tombe de cote, dans le panier, depuis juste au-dessus.
        val bille = monde.lacher(0f, 0.6f)
        monde.derouler(2f)
        assertTrue("la bille n'est pas restee dans le panier : x=${bille.x}, y=${bille.y}",
            kotlin.math.abs(bille.x - panier.x) < 0.2f)
        assertTrue("le ballon n'a pas emporte la bille : ${bille.y}", bille.y > 1.5f)
    }

    @Test
    fun `un ventilateur deporte un ballon`() {
        val monde = Plateau()
        val piece = monde.poser(Pose(TypePiece.BALLON, x = 0f, y = 2f).creer())
        val enveloppe = piece.corps[1]
        monde.poser(Pose(TypePiece.VENTILATEUR, x = -1.2f, y = enveloppe.y, reglage = 0f).creer())
        monde.derouler(1.5f)
        assertTrue("le souffle n'a pas deporte le ballon : ${enveloppe.x}", enveloppe.x > 0.8f)
    }

    // ── Le pic ───────────────────────────────────────────────────────────────

    @Test
    fun `un pic creve le ballon et detruit son panier`() {
        val monde = Plateau()
        val ballon = monde.poser(Pose(TypePiece.BALLON, x = 0f, y = 0f).creer())
        monde.poser(Pose(TypePiece.PIC, x = 0f, y = 3.2f, reglage = 270f).creer())
        val panier = ballon.corps[0]
        val enveloppe = ballon.corps[1]
        monde.derouler(4f, jusqua = { ballon.creve })
        assertTrue("le ballon n'a pas creve", ballon.creve)
        assertTrue("l'enveloppe est restee dans le monde", enveloppe !in monde.monde.bodies)
        assertTrue("le panier est reste dans le monde", panier !in monde.monde.bodies)
    }

    @Test
    fun `la bille du panier retombe avec lui`() {
        val monde = Plateau()
        val ballon = monde.poser(Pose(TypePiece.BALLON, x = 0f, y = 0f, taille = 0.7f).creer())
        monde.poser(Pose(TypePiece.PIC, x = 0f, y = 4.8f, reglage = 270f).creer())
        val bille = monde.lacher(0f, 0.6f)
        monde.derouler(6f, jusqua = { ballon.creve })
        assertTrue("le ballon n'a pas creve", ballon.creve)
        assertTrue("la bille n'etait pas montee : ${bille.y}", bille.y > 1.5f)
        monde.derouler(4f)
        assertTrue("la bille n'est pas retombee : ${bille.y}", bille.y < 0.5f)
    }

    @Test
    fun `un pic ne creve rien d autre`() {
        val monde = Plateau()
        monde.poser(Pose(TypePiece.PIC, x = 0f, y = 0.18f, reglage = 90f).creer())
        val bille = monde.lacher(0.3f, 1f)
        monde.derouler(2f)
        assertTrue("la bille a disparu du monde", bille in monde.monde.bodies)
    }

    // ── L'aimant ─────────────────────────────────────────────────────────────

    private fun essaiAimant(force: Float, repousse: Boolean): Float {
        val monde = Plateau()
        monde.poser(Pose(TypePiece.AIMANT, x = 1f, y = 0.9f, force = force, miroir = repousse).creer())
        val bille = monde.lacher(0f, 0.9f)
        monde.derouler(1.5f)
        return bille.x
    }

    @Test
    fun `un aimant attire une bille et un aimant retourne la repousse`() {
        val attire = essaiAimant(30f, repousse = false)
        val repousse = essaiAimant(30f, repousse = true)
        assertTrue("l'aimant n'a pas attire la bille : x = $attire", attire > 0.5f)
        assertTrue("l'aimant retourne n'a pas repousse la bille : x = $repousse", repousse < -0.2f)
    }

    @Test
    fun `plus l aimant est fort plus il tire`() {
        val faible = essaiAimant(6f, repousse = false)
        val fort = essaiAimant(40f, repousse = false)
        assertTrue("la force ne change rien : $faible contre $fort", fort > faible + 0.3f)
    }

    @Test
    fun `un aimant ne sent que le fer`() {
        val monde = Plateau()
        monde.poser(Pose(TypePiece.AIMANT, x = 0.5f, y = 0.6f, force = 40f).creer())
        val domino = monde.poser(Pose(TypePiece.DOMINO, x = 0f, y = 0f).creer())
        val avant = domino.principal.x
        monde.derouler(2f)
        assertEquals("l'aimant a tire un domino d'ivoire", avant, domino.principal.x, 0.02f)
    }

    // ── Le canon ─────────────────────────────────────────────────────────────

    @Test
    fun `un canon retourne tire des le lancement`() {
        val monde = Plateau()
        val piece = monde.poser(Pose(TypePiece.CANON, x = -2f, y = 1f, reglage = 40f, miroir = true).creer())
        val bille = piece.corps[1]
        val depart = bille.x
        monde.derouler(1f)
        assertEquals("la bille est partie sans lancement", depart, bille.x, 0.05f)

        piece.demarrer()
        monde.derouler(1.5f)
        assertTrue("la bille n'est pas partie : ${bille.x}", bille.x > depart + 2f)
    }

    @Test
    fun `un canon avec bouton ne tire pas au lancement`() {
        val monde = Plateau()
        val piece = monde.poser(Pose(TypePiece.CANON, x = -2f, y = 1f, reglage = 40f).creer())
        val bille = piece.corps[1]
        val depart = bille.x
        piece.demarrer()
        monde.derouler(1f)
        assertTrue("le canon a tire sans qu'on touche son bouton", kotlin.math.abs(bille.x - depart) < 0.1f)
    }

    @Test
    fun `une bille qui tombe sur le bouton fait tirer le canon`() {
        val monde = Plateau()
        val piece = monde.poser(Pose(TypePiece.CANON, x = 0f, y = 1.5f, reglage = 0f).creer())
        val tire = piece.corps[1]
        val depart = tire.x
        // Le bouton est derriere la culasse : on y laisse tomber une autre bille.
        monde.lacher(-Pieces.GACHETTE_DISTANCE, 2.4f)
        monde.derouler(1.5f)
        assertTrue("le canon n'a pas tire : ${piece.parti}", piece.parti)
        assertTrue("la bille chargee n'est pas partie : ${tire.x}", tire.x > depart + 1.5f)
    }

    @Test
    fun `un canon plus puissant tire plus loin`() {
        fun portee(force: Float): Float {
            val monde = Plateau()
            val piece = monde.poser(
                Pose(TypePiece.CANON, x = -4f, y = 1f, reglage = 35f, force = force, miroir = true).creer()
            )
            val bille = piece.corps[1]
            piece.demarrer()
            monde.derouler(1.2f)
            return bille.x
        }
        val faible = portee(8f)
        val fort = portee(30f)
        assertTrue("la puissance ne change rien : $faible contre $fort", fort > faible + 3f)
    }

    // ── La plaque de pression ────────────────────────────────────────────────

    @Test
    fun `une bille sur la plaque fait tirer le canon relie`() {
        val monde = Plateau()
        val plaque = monde.poser(Pose(TypePiece.PLAQUE, x = -3f, y = 0.03f).creer())
        val canon = monde.poser(Pose(TypePiece.CANON, x = 0f, y = 1.5f, reglage = 0f).creer())
        monde.liens.add(Lien(0, 1))
        val tire = canon.corps[1]
        val depart = tire.x
        monde.derouler(0.5f)
        assertTrue("le canon a tire sans qu'on touche la plaque", !canon.parti)

        monde.lacher(-3f, 1f)
        monde.derouler(1.5f)
        assertTrue("la plaque ne s'est pas allumee", plaque.actif)
        assertTrue("le canon relie n'a pas tire", canon.parti)
        assertTrue("la bille chargee n'est pas partie : ${tire.x}", tire.x > depart + 1.5f)
    }

    @Test
    fun `une plaque non reliee ne fait rien tirer`() {
        val monde = Plateau()
        monde.poser(Pose(TypePiece.PLAQUE, x = -3f, y = 0.03f).creer())
        val canon = monde.poser(Pose(TypePiece.CANON, x = 0f, y = 1.5f, reglage = 0f).creer())
        monde.lacher(-3f, 1f)
        monde.derouler(1.5f)
        assertTrue("le canon a tire sans lien", !canon.parti)
    }

    @Test
    fun `la plaque se desenfonce quand l objet part`() {
        val monde = Plateau()
        val plaque = monde.poser(Pose(TypePiece.PLAQUE, x = 0f, y = 0.03f).creer())
        val bille = monde.lacher(0f, 0.4f)
        monde.derouler(0.6f)
        assertTrue("la plaque n'est pas enfoncee", plaque.contacts > 0)
        bille.x = 2f
        bille.y = 0.5f
        bille.wake()
        monde.derouler(0.5f)
        assertEquals("la plaque est restee enfoncee", 0, plaque.contacts)
    }

    @Test
    fun `un ventilateur relie a une plaque ne souffle qu apres elle`() {
        fun essai(plaqueTouchee: Boolean): Float {
            val monde = Plateau()
            monde.poser(Pose(TypePiece.PLAQUE, x = -3f, y = 0.03f).creer())
            monde.poser(Pose(TypePiece.VENTILATEUR, x = -1.4f, y = 0.4f, reglage = 0f).creer())
            monde.liens.add(Lien(0, 1))
            val bille = monde.lacher(0f, 1.2f)
            if (plaqueTouchee) monde.lacher(-3f, 0.4f)
            monde.derouler(2f)
            return bille.x
        }
        val sans = essai(false)
        val avec = essai(true)
        assertTrue("le ventilateur a souffle sans plaque : $sans", sans < 0.5f)
        assertTrue("le ventilateur n'a pas souffle apres la plaque : $avec", avec > 1.5f)
    }

    @Test
    fun `un ventilateur libre souffle toujours`() {
        val monde = Plateau()
        monde.poser(Pose(TypePiece.VENTILATEUR, x = -1.4f, y = 0.4f, reglage = 0f).creer())
        val bille = monde.lacher(0f, 1.2f)
        monde.derouler(2f)
        assertTrue("le ventilateur libre ne souffle pas : ${bille.x}", bille.x > 1.5f)
    }

    @Test
    fun `un interrupteur bascule a chaque appui`() {
        val monde = Plateau()
        val plaque = monde.poser(Pose(TypePiece.PLAQUE, x = 0f, y = 0.03f).creer())
        val bille = monde.lacher(0f, 0.4f)
        monde.derouler(1f)
        assertTrue("l'interrupteur ne s'est pas allume", plaque.actif)

        // La bille reste dessus : rien ne bascule tant qu'elle n'est pas repartie.
        monde.derouler(1f)
        assertTrue("l'interrupteur a clignote avec la bille posee dessus", plaque.actif)

        // On la retire, puis on la repose : deuxieme appui, il s'eteint.
        bille.x = 3f; bille.y = 0.4f; bille.wake()
        monde.derouler(1f)
        assertTrue("l'interrupteur s'est eteint tout seul", plaque.actif)
        bille.x = 0f; bille.y = 0.4f; bille.vx = 0f; bille.vy = 0f; bille.wake()
        monde.derouler(1f)
        assertTrue("le deuxieme appui n'a pas eteint l'interrupteur", !plaque.actif)
    }

    @Test
    fun `un bouton continu s eteint des qu on le quitte`() {
        val monde = Plateau()
        val plaque = monde.poser(Pose(TypePiece.PLAQUE, x = 0f, y = 0.03f, miroir = true).creer())
        val bille = monde.lacher(0f, 0.4f)
        monde.derouler(1f)
        assertTrue("le bouton n'est pas actif sous la bille", plaque.actif)
        bille.x = 3f; bille.y = 0.4f; bille.wake()
        monde.derouler(1f)
        assertTrue("le bouton est reste actif sans rien dessus", !plaque.actif)
    }

    @Test
    fun `un ventilateur relie a un bouton continu s arrete quand la bille part`() {
        val monde = Plateau()
        monde.poser(Pose(TypePiece.PLAQUE, x = -3f, y = 0.03f, miroir = true).creer())
        val ventilo = monde.poser(Pose(TypePiece.VENTILATEUR, x = -1.4f, y = 0.4f, reglage = 0f).creer())
        monde.liens.add(Lien(0, 1))
        val appui = monde.lacher(-3f, 0.4f)
        monde.derouler(1f)
        assertTrue("le ventilateur n'a pas demarre", !ventilo.eteint)
        appui.x = -5f; appui.y = 0.4f; appui.wake()
        monde.derouler(1f)
        assertTrue("le ventilateur est reste en marche", ventilo.eteint)
    }

    // ── Le pendule ───────────────────────────────────────────────────────────

    @Test
    fun `le pendule se balance et garde sa longueur`() {
        val monde = Plateau()
        val piece = monde.poser(Pose(TypePiece.PENDULE, x = 0f, y = 3f, reglage = 60f, taille = 1.5f).creer())
        val boulet = piece.corps[1]
        var minX = boulet.x
        var maxX = boulet.x
        repeat(240) {
            monde.avancer(PAS)
            minX = minOf(minX, boulet.x)
            maxX = maxOf(maxX, boulet.x)
            val distance = kotlin.math.hypot(boulet.x - 0f, boulet.y - 3f)
            assertEquals("la barre s'est etiree", 1.5f, distance, 0.05f)
        }
        assertTrue("le pendule n'a pas traverse : de $minX a $maxX", minX < -0.5f && maxX > 1f)
    }

    @Test
    fun `le pendule renverse un domino`() {
        val monde = Plateau()
        monde.poser(Pose(TypePiece.PENDULE, x = 0f, y = 1.8f, reglage = -70f, taille = 1.6f).creer())
        val domino = monde.poser(Pose(TypePiece.DOMINO, x = 0.9f, y = 0f).creer())
        monde.derouler(3f)
        assertTrue("le domino est reste debout : angle ${domino.principal.angle}",
            kotlin.math.abs(domino.principal.angle) > 0.5f || domino.principal.x > 1.1f)
    }

    // ── La bascule ───────────────────────────────────────────────────────────

    @Test
    fun `la bascule tourne librement sans butee`() {
        val monde = Plateau()
        val bascule = monde.poser(Pieces.bascule(x = 0f, bas = 0f))
        val planche = bascule.mobiles.single()
        monde.lacher(0.5f, 2f, masse = 6f)
        monde.derouler(3f)
        assertTrue("la planche n'a pas bascule : ${planche.angle} rad", kotlin.math.abs(planche.angle) > 0.2f)
        assertEquals("la bascule ne doit avoir qu'un pied scelle", 1, bascule.scelles.size)
    }

    @Test
    fun `une bascule decalee penche du cote long`() {
        val monde = Plateau()
        val bascule = monde.poser(Pose(TypePiece.BASCULE, x = 0f, y = 0f, taille = 1.6f, taille2 = 0.5f).creer())
        val planche = bascule.mobiles.single()
        monde.derouler(2f)
        // Le centre est decale a droite : le cote droit est plus lourd, donc il descend (angle negatif).
        assertTrue("le levier decale n'a pas penche : ${planche.angle}", planche.angle < -0.1f)
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
