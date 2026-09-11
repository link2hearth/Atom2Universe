package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.Joint
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import com.Atom2Universe.app.games.physics.PrismaticJoint
import com.Atom2Universe.app.games.physics.PulleyJoint
import com.Atom2Universe.app.games.physics.RevoluteJoint
import com.Atom2Universe.app.games.physics.SpringJoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ce qui tient une pièce en place — et c'est la question qui structure tout le jeu.
 *
 * Une pièce n'est pas un corps, c'est un **petit assemblage**. Le moteur ne connaît que
 * des corps et des liaisons ; ce qui distingue une rampe d'un domino, ce n'est pas leur
 * forme, c'est ce qui les retient.
 */
enum class Ancrage {
    /**
     * Scellée au fond : rien ne la déplace, jamais. Elle peut tenir en l'air sans
     * support, ce qui est précisément l'intérêt d'une rampe.
     *
     * Dans le moteur : un corps de masse nulle avec `lockPosition` et `lockRotation`.
     */
    SCELLE,

    /**
     * Libre : elle tombe, elle roule, elle se renverse. Un domino n'est intéressant que
     * parce qu'il peut tomber, donc il ne tient que par le sol sur lequel on le pose.
     */
    LIBRE,

    /**
     * **Un pied scellé, et le reste qui bouge autour.** C'est la bascule : le pied est
     * collé au fond, la planche ne l'est pas, et c'est de cet écart que vient tout le
     * mécanisme.
     *
     * Dans le moteur : deux corps, l'un immobile, l'autre libre, tenus ensemble par une
     * liaison pivot. Rien d'autre à inventer — clouer un corps au décor, c'est
     * exactement le prendre en pivot sur une masse immobile.
     */
    MIXTE
}

/**
 * Ce qu'un corps représente, pour que le dessin sache quoi peindre dessus.
 *
 * Le moteur ne voit que des boîtes et des disques : une planche de bascule et un volet
 * de tremplin sont exactement la même chose pour lui. Le dessin, lui, doit les
 * distinguer, sans quoi tout le tableau ressemble à un tas de rectangles gris — ce qui
 * était précisément le défaut de la première version.
 *
 * L'étiquette voyage sur `PhysBody.tag`, donc elle survit au passage par le moteur et
 * ne coûte rien : aucune table à tenir à jour, aucune recherche à faire au moment de
 * peindre.
 */
enum class Element {
    /** Planche de bois : rampe, bascule, volet de tremplin. */
    PLANCHE,

    /** Disque rebondissant. */
    PLOT,

    /** Bloc de pierre. */
    BLOC,

    /** Domino dressé. */
    DOMINO,

    /** Montant, pied, socle : ce qui tient le reste. */
    BATI,

    /** Carter du ventilateur, avec ses pales dessinées. */
    VENTILATEUR,

    /** Peau du tambour. */
    PEAU,

    /** Godet de la poulie. */
    GODET,

    /** Contrepoids de la poulie. */
    CONTREPOIDS
}

/**
 * Un souffle : une zone rectangulaire qui pousse ce qui la traverse.
 *
 * ## Une force, pas une vitesse
 *
 * Un ventilateur ne « donne » pas une vitesse : il pousse, et ce qui est léger part plus
 * vite que ce qui est lourd. Une force constante rend exactement cela, et le moteur
 * n'avait besoin d'apprendre aucun mot nouveau — [PhysBody.applyForce] existait déjà, et
 * une force posée de l'extérieur réveille son corps.
 *
 * La poussée décroît le long du jet : un ventilateur souffle fort devant lui et à peine
 * au bout de sa portée. C'est ce qui rend le placement intéressant, sans quoi la distance
 * entre le ventilateur et la bille ne changerait rien.
 */
class Souffle(
    /** Bouche du ventilateur, en mètres. */
    val x: Float,
    val y: Float,
    /** Direction du jet, en radians. 0 souffle vers la droite, π/2 vers le haut. */
    val direction: Float,
    /** Longueur du jet. */
    val portee: Float,
    /** Demi-largeur du jet. */
    val demiLargeur: Float,
    /** Poussée à la bouche, en newtons. */
    val poussee: Float
) {
    private val dx = cos(direction)
    private val dy = sin(direction)

    /** Le long du jet, de 0 à [portee], ou −1 si le point est hors du souffle. */
    fun avancement(px: Float, py: Float): Float {
        val ex = px - x
        val ey = py - y
        val le = ex * dx + ey * dy
        if (le < 0f || le > portee) return -1f
        val travers = -ex * dy + ey * dx
        if (travers < -demiLargeur || travers > demiLargeur) return -1f
        return le
    }

    /**
     * Pousse [corps] s'il est dans le jet.
     *
     * Les corps scellés sont ignorés — non pas parce que la force les dérangerait (ils
     * ont une masse infinie), mais parce qu'une force posée sur un corps le **réveille**,
     * et qu'un ventilateur soufflant sur son propre carter empêcherait tout le tableau de
     * s'endormir.
     */
    fun appliquer(corps: PhysBody) {
        if (corps.immovable) return
        val le = avancement(corps.x, corps.y)
        if (le < 0f) return
        val part = 1f - 0.65f * (le / portee)
        val f = poussee * part
        corps.applyForce(f * dx, f * dy)
    }
}

/**
 * Les pièces que le joueur peut poser.
 *
 * Neuf entrées, et c'est un plafond assumé : la difficulté d'une machine infernale ne
 * vient pas du nombre de pièces disponibles mais du nombre qu'on vous en donne. Ce qui
 * compte est que chacune fasse **quelque chose qu'aucune autre ne fait** — guider,
 * renvoyer, barrer, propager, basculer, relancer, souffler, rebondir haut, et changer une
 * chute en montée. Une dixième qui ferait comme une autre en plus joli serait du bruit.
 */
enum class TypePiece(val ancrage: Ancrage) {
    /** Planche inclinée, scellée : elle guide la bille. */
    RAMPE(Ancrage.SCELLE),

    /** Plot rond et rebondissant, scellé : la bille repart ailleurs. */
    PLOT(Ancrage.SCELLE),

    /** Mur ou butée, scellé : il barre ou renvoie. */
    BLOC(Ancrage.SCELLE),

    /** Tombe quand on le pousse, et pousse le suivant. */
    DOMINO(Ancrage.LIBRE),

    /** Pied scellé, planche libre : ce qui tombe d'un côté soulève l'autre. */
    BASCULE(Ancrage.MIXTE),

    /** Socle scellé, volet sur ressort : ce qui tombe dessus repart en l'air. */
    TREMPLIN(Ancrage.MIXTE),

    /** Souffle un jet d'air : il pousse la bille là où la pente ne la mène pas. */
    VENTILATEUR(Ancrage.SCELLE),

    /** Peau tendue : ce qui tombe dessus repart presque aussi haut. */
    TAMBOUR(Ancrage.SCELLE),

    /** Godet et contrepoids sur une corde : la chute d'un côté devient montée de l'autre. */
    POULIE(Ancrage.MIXTE)
}

/**
 * Une pièce posée sur le tableau : ses corps, ses liaisons, et de quoi la reprendre.
 *
 * Les corps portent tous cette pièce comme propriétaire, ce qui rend le retrait exact :
 * on retire **ses** corps, jamais on ne vide le monde — un tableau contient d'autres
 * pièces, la bille et le bouton, qui n'ont pas à souffrir d'un déplacement.
 */
class Piece internal constructor(
    val type: TypePiece,
    val corps: List<PhysBody>,
    val liaisons: List<Joint>,
    /** Le jet d'air de la pièce, quand elle en a un. Seul le ventilateur en a un. */
    val souffle: Souffle? = null
) {
    init {
        for (c in corps) {
            c.owner = this
            // Ce qui bouge se signale : une zone de detection ne veut voir ni le sol, ni
            // les murs, ni le pied scelle d'une bascule. Marquer la categorie ici plutot
            // que dans chaque fabrique garantit qu'aucune piece ne l'oubliera.
            if (!c.immovable) c.category = Plateau.MOBILE
        }
    }

    val ancrage: Ancrage get() = type.ancrage

    /** Les corps collés au fond. Vide pour un domino, tout pour une rampe. */
    val scelles: List<PhysBody> get() = corps.filter { it.immovable }

    /** Les corps qui bougent. Vide pour une rampe, tout pour un domino. */
    val mobiles: List<PhysBody> get() = corps.filter { !it.immovable }

    /** Le corps principal, celui qu'on désigne quand on parle de la pièce. */
    val principal: PhysBody get() = corps.first()

    /** La poulie de la pièce, quand elle en a une : le dessin y accroche sa corde. */
    val poulie: PulleyJoint? get() = liaisons.firstOrNull { it is PulleyJoint } as? PulleyJoint

    fun poser(monde: PhysWorld) {
        for (c in corps) monde.add(c)
        for (l in liaisons) monde.addJoint(l)
    }

    /** Reprend la pièce sans toucher au reste du tableau. */
    fun retirer(monde: PhysWorld) {
        monde.removeOwned(this)
    }
}

/**
 * L'atelier : une fabrique par pièce, en mètres et en kilogrammes comme tout le moteur.
 *
 * Les cotes sont ici et nulle part ailleurs, pour qu'essayer autre chose tienne en une
 * ligne à changer.
 */
object Pieces {

    /** Frottement commun : du bois sur du bois, ça accroche. */
    private const val FROTTEMENT = 0.6f

    // Les cotes par defaut, nommees pour qu'une [Pose] puisse s'y referer au lieu de
    // les recopier — deux copies d'une meme cote finissent toujours par diverger.
    const val RAMPE_LONGUEUR = 1.5f
    const val PLOT_RAYON = 0.15f
    const val BLOC_COTE = 0.4f
    const val DOMINO_HAUTEUR = 0.44f
    const val BASCULE_LONGUEUR = 1.2f
    const val TREMPLIN_LONGUEUR = 0.7f
    const val VENTILATEUR_COTE = 0.34f
    const val TAMBOUR_LARGEUR = 0.8f
    const val POULIE_HAUTEUR = 1.8f

    // ── Le ventilateur ───────────────────────────────────────────────────────

    /** Longueur du jet, en mètres. */
    const val SOUFFLE_PORTEE = 2.2f

    /** Demi-largeur du jet. */
    const val SOUFFLE_DEMI_LARGEUR = 0.32f

    /**
     * Poussée à la bouche, en newtons.
     *
     * Elle est **volontairement plus faible que le poids de la bille** (deux kilos, donc
     * presque vingt newtons). Un ventilateur qui pourrait la soulever ferait de chaque
     * tableau un problème de vol stationnaire, où la position exacte de la bille dans le
     * jet déciderait de tout : injouable et impossible à viser. À huit newtons il dévie,
     * il ralentit, il pousse une bille qui roule et il renverse un domino — ce qui est
     * une panoplie de verbes largement suffisante.
     */
    const val SOUFFLE_POUSSEE = 8f

    // ── La poulie ────────────────────────────────────────────────────────────

    /**
     * Ecart horizontal entre le centre de la piece et chacun des deux corps pendus.
     *
     * Le godet est a gauche, le contrepoids a droite, et le bouton que vise le generateur
     * se pose exactement a l'aplomb du second.
     */
    const val POULIE_ECART = 0.3f

    /** Course verticale du contrepoids, bornee par ses deux tablettes. */
    const val POULIE_COURSE = 0.7f

    /** Distance entre le rea et la margelle du godet quand celui-ci est en haut. */
    private const val POULIE_RETRAIT = 0.55f

    /** Demi-hauteur du godet : la margelle est a cette distance de son centre. */
    private const val GODET_DEMI_HAUTEUR = 0.12f

    /** Demi-largeur du godet. Large, pour qu'une bille lachee un peu de travers y tombe. */
    private const val GODET_DEMI_LARGEUR = 0.26f

    /** Demi-hauteur du contrepoids. */
    private const val CONTREPOIDS_DEMI_HAUTEUR = 0.14f

    /** Hauteur du centre du contrepoids au repos, au-dessus du pied du mat. */
    private const val CONTREPOIDS_REPOS = 0.24f

    /** Ecart entre le centre de la piece et le mat. */
    private const val POULIE_MAT = 0.62f

    /** Masse du godet vide. Il doit etre **plus leger** que le contrepoids. */
    private const val POULIE_MASSE_GODET = 0.4f

    /** Masse du contrepoids : plus lourd a vide, depasse des que la bille entre. */
    private const val POULIE_MASSE_CONTREPOIDS = 0.9f

    /**
     * Hauteur de la margelle du godet au repos, pour un mat dont le pied est a [bas].
     *
     * Publique parce que le generateur en a besoin : pour faire tomber la bille dans le
     * godet, il doit savoir ou il est **avant** de fabriquer la piece. La calculer deux
     * fois, ici et la-bas, c'est signer le bug du jour ou l'une des deux changera.
     */
    fun poulieGodetHaut(bas: Float, hauteur: Float = POULIE_HAUTEUR): Float =
        bas + hauteur - POULIE_RETRAIT

    /** Hauteur du contrepoids au repos, pose sur sa tablette basse. */
    fun poulieContrepoidsBas(bas: Float): Float = bas + CONTREPOIDS_REPOS

    /** Hauteur atteinte par le contrepoids quand le godet est descendu a fond. */
    fun poulieContrepoidsHaut(bas: Float): Float = poulieContrepoidsBas(bas) + POULIE_COURSE

    private fun scelle(corps: PhysBody): PhysBody = corps.apply {
        lockPosition = true
        lockRotation = true
        refreshMass()
    }

    private fun PhysBody.marquer(element: Element): PhysBody = apply { tag = element }

    /**
     * Place un corps composé de façon que sa **première forme** tombe en ([px], [py]).
     *
     * `PhysBody.compound` recentre les formes sur le centre de masse de l'ensemble : le
     * point d'origine du croquis n'est donc plus celui du corps, et poser `x`/`y`
     * directement décale toute la pièce d'une distance qui dépend des cotes. Le godet de
     * la poulie se retrouvait dix centimètres trop haut et sa corde partait de travers.
     * `localOffsetX`/`localOffsetY` disent exactement où une forme a atterri, ce qui
     * ramène le placement à une soustraction.
     */
    private fun PhysBody.parPremiereForme(px: Float, py: Float): PhysBody = apply {
        x = px - localOffsetX(0)
        y = py - localOffsetY(0)
    }

    /**
     * Planche inclinée scellée. [pente] en degrés, **positive = descend vers la droite**,
     * ce qui est le sens où on lit une pente.
     *
     * L'angle du moteur, lui, tourne dans l'autre sens : les y montent, donc un angle
     * positif lève le bout droit. D'où le signe moins, une fois ici plutôt qu'à chaque
     * appel — sinon chaque rampe posée dans le jeu devrait le refaire, et l'une d'elles
     * l'oublierait.
     */
    fun rampe(x: Float, y: Float, pente: Float = 20f, longueur: Float = RAMPE_LONGUEUR): Piece {
        val planche = PhysBody(longueur / 2f, 0.04f, 0f).apply {
            this.x = x
            this.y = y
            angle = -Math.toRadians(pente.toDouble()).toFloat()
            friction = 0.35f
            restitution = 0.05f
        }.marquer(Element.PLANCHE)
        return Piece(TypePiece.RAMPE, listOf(scelle(planche)), emptyList())
    }

    /** Plot rond et rebondissant, scellé au fond. */
    fun plot(x: Float, y: Float, rayon: Float = PLOT_RAYON): Piece {
        val disque = PhysBody.circle(rayon, 0f).apply {
            this.x = x
            this.y = y
            friction = 0.2f
            restitution = 0.75f
        }.marquer(Element.PLOT)
        return Piece(TypePiece.PLOT, listOf(scelle(disque)), emptyList())
    }

    /** Mur scellé. */
    fun bloc(x: Float, y: Float, largeur: Float = BLOC_COTE, hauteur: Float = BLOC_COTE): Piece {
        val mur = PhysBody(largeur / 2f, hauteur / 2f, 0f).apply {
            this.x = x
            this.y = y
            friction = FROTTEMENT
            restitution = 0.05f
        }.marquer(Element.BLOC)
        return Piece(TypePiece.BLOC, listOf(scelle(mur)), emptyList())
    }

    /**
     * Domino libre. [y] est le **bas** de la pièce : on pose un domino sur un sol, on ne
     * calcule pas son centre.
     *
     * Il est volontairement gros. Les petits corps qui se touchent sont le pire cas du
     * solveur, et une rangée de trente dominos fins coûte plus cher qu'un château —
     * huit gros dominos font le même effet pour une fraction du prix.
     */
    fun domino(x: Float, bas: Float, hauteur: Float = DOMINO_HAUTEUR, epaisseur: Float = 0.08f): Piece {
        val piece = PhysBody(epaisseur / 2f, hauteur / 2f, 0.5f).apply {
            this.x = x
            this.y = bas + hauteur / 2f
            friction = FROTTEMENT
            restitution = 0f
        }.marquer(Element.DOMINO)
        return Piece(TypePiece.DOMINO, listOf(piece), emptyList())
    }

    /**
     * Bascule : **le pied est collé, la planche ne l'est pas.**
     *
     * C'est l'exemple qui justifie [Ancrage.MIXTE] à lui seul. Le pied est un corps
     * immobile ; la planche est un corps libre qui lui est cloué en un point par un
     * pivot. Ce qui tombe d'un côté fait monter l'autre, et rien dans le moteur n'a eu
     * besoin d'apprendre le mot « bascule ».
     *
     * [bas] est le bas du pied.
     */
    fun bascule(x: Float, bas: Float, longueur: Float = BASCULE_LONGUEUR, hauteurPied: Float = 0.3f): Piece {
        val pied = scelle(PhysBody(0.07f, hauteurPied / 2f, 0f).apply {
            this.x = x
            this.y = bas + hauteurPied / 2f
            friction = FROTTEMENT
        }.marquer(Element.BATI))
        val sommet = bas + hauteurPied
        val planche = PhysBody(longueur / 2f, 0.04f, 1.2f).apply {
            this.x = x
            this.y = sommet + 0.04f
            friction = FROTTEMENT
            restitution = 0.05f
            // La planche et son pied se chevauchent au pivot : ils ne doivent pas se
            // repousser. C'est ce que fait déjà `collideConnected = false` par défaut.
        }.marquer(Element.PLANCHE)
        val pivot = RevoluteJoint.pin(pied, planche, x, sommet)
        return Piece(TypePiece.BASCULE, listOf(pied, planche), listOf(pivot))
    }

    /**
     * Tremplin : socle scellé, volet articulé, et un ressort qui le remonte.
     *
     * [sens] dit de quel côté est la charnière : `+1` la met à gauche, donc le volet se
     * charge par la droite, et c'est ce qu'il faut pour une bille qui arrive de la
     * gauche. `−1` fait le miroir. Sans ce paramètre, la moitié des tableaux tirés au
     * hasard demandaient un tremplin monté à l'envers, et la bille se contentait de
     * grimper sur le socle.
     *
     * ## Le réglage qui compte est [hauteur], pas [raideur]
     *
     * C'est contre-intuitif, et c'est mesuré. Une bille lâchée de 1,5 m repart à :
     *
     * | hauteur de charnière | 300 N/m | 900 N/m | 2500 N/m |
     * |---|---|---|---|
     * | 18 cm | 23 % | 37 % | 52 % |
     * | 35 cm | 14 % | **61 %** | 63 % |
     * | 50 cm | 7 % | 65 % | 62 % |
     *
     * Deux choses se lisent là-dedans. D'abord **la course fait plus que la raideur** :
     * à raideur égale, monter la charnière de 18 à 35 cm fait passer de 37 % à 61 %.
     * Ensuite, un ressort trop mou pour la hauteur **talonne** — il touche le sol avant
     * d'avoir rien stocké, et le tremplin devient une planche (7 %).
     *
     * La raison tient au moteur : la perte d'un ressort vaut à peu près `(π/2)·ω·h`, avec
     * `ω = √(k/m)` et `h` le sous-pas. Un ressort raide et court a un `ω` énorme, donc
     * perd énormément ; un ressort doux et long a le même travail à fournir avec un `ω`
     * bien plus faible. C'est aussi ce que fait un vrai trampoline : il est **grand**.
     *
     * Au-delà d'environ 65 % ça plafonne, et c'est le plafond du solveur, pas du réglage.
     * Un tremplin rend donc les deux tiers de ce qu'on lui donne : c'est net à l'œil et
     * ça suffit largement à relancer une bille.
     */
    fun tremplin(
        x: Float,
        bas: Float,
        longueur: Float = TREMPLIN_LONGUEUR,
        sens: Float = 1f,
        raideur: Float = 900f,
        masseVolet: Float = 0.3f,
        amortissement: Float = 1f,
        hauteur: Float = 0.35f
    ): Piece {
        val cote = if (sens < 0f) -1f else 1f
        val charniereX = x - cote * longueur / 2f
        val ressortX = x + cote * longueur / 2f
        val socle = scelle(PhysBody(0.08f, hauteur / 2f, 0f).apply {
            this.x = charniereX
            this.y = bas + hauteur / 2f
            friction = FROTTEMENT
        }.marquer(Element.BATI))
        val charniere = bas + hauteur
        val volet = PhysBody(longueur / 2f, 0.035f, masseVolet).apply {
            this.x = x
            this.y = charniere
            friction = 0.3f
            restitution = 0.1f
        }.marquer(Element.PLANCHE)
        val pivot = RevoluteJoint.pin(socle, volet, charniereX, charniere)

        // Le pied du ressort est un second corps scellé, sous l'extrémité libre : c'est
        // lui qui donne au ressort un point d'appui qui ne bouge pas.
        val appui = scelle(PhysBody(0.05f, 0.02f, 0f).apply {
            this.x = ressortX
            this.y = bas
        }.marquer(Element.BATI))
        val ressort = SpringJoint.between(
            appui, ressortX, bas,
            volet, ressortX, charniere,
            stiffness = raideur,
            damping = amortissement
        )
        return Piece(TypePiece.TREMPLIN, listOf(socle, appui, volet), listOf(pivot, ressort))
    }

    /**
     * Ventilateur : un carter scellé et un jet d'air devant lui.
     *
     * [direction] est en degrés, dans le sens trigonométrique : 0 souffle vers la droite,
     * 90 vers le haut, 180 vers la gauche. [x] et [y] sont le **centre du carter** ; le
     * jet part de sa face avant.
     *
     * Aucune pale n'est simulée, et c'est délibéré. Un rotor en contact avec la bille
     * serait un corps rapide et fin — le pire cas du solveur — pour un résultat que
     * personne ne verrait, puisque ce qui compte est le vent, pas les pales. Elles sont
     * dessinées, elles tournent à l'écran, et elles ne coûtent rien.
     */
    fun ventilateur(
        x: Float,
        y: Float,
        direction: Float = 0f,
        cote: Float = VENTILATEUR_COTE,
        portee: Float = SOUFFLE_PORTEE,
        poussee: Float = SOUFFLE_POUSSEE
    ): Piece {
        val rad = Math.toRadians(direction.toDouble()).toFloat()
        val carter = PhysBody(cote / 2f, cote / 2f, 0f).apply {
            this.x = x
            this.y = y
            angle = rad
            friction = 0.4f
            restitution = 0.1f
        }.marquer(Element.VENTILATEUR)
        val bouche = cote / 2f + 0.02f
        val souffle = Souffle(
            x = x + cos(rad) * bouche,
            y = y + sin(rad) * bouche,
            direction = rad,
            portee = portee,
            demiLargeur = SOUFFLE_DEMI_LARGEUR,
            poussee = poussee
        )
        return Piece(TypePiece.VENTILATEUR, listOf(scelle(carter)), emptyList(), souffle)
    }

    /**
     * Tambour : une peau tendue, scellée, qui renvoie presque tout ce qu'elle reçoit.
     *
     * ## Pourquoi un corps scellé et pas un ressort
     *
     * Le tremplin est déjà un ressort, et sa documentation dit ce qu'il en coûte : les
     * deux tiers de l'énergie rendus dans le meilleur des cas, et un réglage à trouver
     * entre talonnage et perte. Un tambour qui refait la même chose en plus large ne
     * serait qu'un tremplin mal réglé.
     *
     * La restitution de contact, elle, ne passe par aucun ressort : elle est bornée par
     * construction (elle ne peut pas rendre plus que la vitesse d'arrivée) et elle coûte
     * un contact. À 0,92 la bille repart à quatre-vingt-cinq pour cent de sa hauteur,
     * franchement au-dessus de ce qu'un tremplin sait faire, sans un seul réglage.
     *
     * Les deux pièces ne font donc pas doublon : le tremplin **renvoie de côté** ce qui
     * roule dessus, le tambour **renvoie en haut** ce qui tombe dessus.
     */
    fun tambour(
        x: Float,
        y: Float,
        largeur: Float = TAMBOUR_LARGEUR,
        rebond: Float = 0.92f
    ): Piece {
        val peau = PhysBody(largeur / 2f, 0.05f, 0f).apply {
            this.x = x
            this.y = y
            friction = 0.12f
            restitution = rebond
        }.marquer(Element.PEAU)
        return Piece(TypePiece.TAMBOUR, listOf(scelle(peau)), emptyList())
    }

    /**
     * Poulie : un godet d'un côté, un contrepoids de l'autre, une corde par-dessus un réa.
     *
     * **C'est la seule pièce qui fait monter quelque chose.** Tout le reste du jeu
     * descend : la pesanteur guide la bille, les dominos tombent, la bascule renvoie de
     * côté. Ici, la bille qui tombe dans le godet fait s'élever le contrepoids de
     * [POULIE_COURSE] mètres — et ce qui est en haut peut enfin être atteint.
     *
     * ## Pourquoi un mât seul, et rien du tout au-dessus du godet
     *
     * La première version avait un beau portique : deux montants, une traverse, un réa à
     * chaque bout. Et le générateur ne l'a **jamais** retenue une seule fois sur vingt
     * niveaux. La raison saute aux yeux une fois qu'on la cherche au bon endroit : pour
     * tomber dans un godet suspendu, une bille doit traverser la hauteur d'où pend la
     * corde — donc taper la traverse. Aucun réglage ne rattrape ça ; c'est la forme qui
     * était fausse.
     *
     * D'où le mât unique, planté **du côté du contrepoids**, et un seul réa à son sommet :
     * la corde descend en diagonale vers le godet, et au-dessus du godet il n'y a
     * strictement rien. C'est aussi, accessoirement, à quoi ressemble une vraie grue.
     *
     * Les deux brins obliques font que le godet descend un peu plus que le contrepoids ne
     * monte — un brin oblique s'allonge moins vite que la chute qui le tire. C'est
     * physiquement exact et sans conséquence : ce sont les tablettes qui bornent la course
     * du contrepoids, donc la hauteur qu'il atteint reste celle qu'annonce
     * [poulieContrepoidsHaut].
     *
     * ## Trois liaisons, et pas une de trop
     *
     * Deux glissières verticales empêchent le godet et le contrepoids de tanguer ; la
     * poulie tient la somme des deux brins. Sans les rails, une bille tombant de travers
     * dans le godet le fait pivoter et tout part en vrille ; avec eux, la pièce ne sait
     * faire qu'une chose, ce qui est exactement ce qu'on demande à une pièce de casse-tête.
     *
     * Au repos le contrepoids (0,9 kg) l'emporte sur le godet vide (0,4 kg) : il repose
     * sur sa tablette basse et la machine attend. La bille pèse deux kilos : elle renverse
     * le rapport dès qu'elle entre.
     *
     * [bas] est le pied du mât.
     */
    fun poulie(x: Float, bas: Float, hauteur: Float = POULIE_HAUTEUR): Piece {
        val matX = x + POULIE_MAT
        val reaX = matX
        val reaY = bas + hauteur

        val mat = scelle(PhysBody(0.06f, hauteur / 2f, 0f).apply {
            this.x = matX
            this.y = bas + hauteur / 2f
            friction = FROTTEMENT
        }.marquer(Element.BATI))

        // Le repère du godet est sa **margelle** — le bord par-dessus lequel la bille
        // tombe dedans. C'est la seule cote dont le générateur ait besoin, et la seule
        // que le joueur regarde.
        val godetCentre = poulieGodetHaut(bas, hauteur) - GODET_DEMI_HAUTEUR
        val godet = PhysBody.compound(POULIE_MASSE_GODET) {
            box(GODET_DEMI_LARGEUR, 0.03f, 0f, -GODET_DEMI_HAUTEUR)
            box(0.03f, GODET_DEMI_HAUTEUR, -GODET_DEMI_LARGEUR, 0f)
            box(0.03f, GODET_DEMI_HAUTEUR, GODET_DEMI_LARGEUR, 0f)
        }.parPremiereForme(x - POULIE_ECART, godetCentre - GODET_DEMI_HAUTEUR).apply {
            friction = FROTTEMENT
            restitution = 0.02f
        }.marquer(Element.GODET)

        val contrepoidsBas = poulieContrepoidsBas(bas)
        val contrepoids = PhysBody(0.1f, CONTREPOIDS_DEMI_HAUTEUR, POULIE_MASSE_CONTREPOIDS).apply {
            this.x = x + POULIE_ECART
            this.y = contrepoidsBas
            friction = FROTTEMENT
            restitution = 0.05f
        }.marquer(Element.CONTREPOIDS)

        // ## Les tablettes, et pourquoi ce ne sont pas des butées de glissière
        //
        // La course se bornait avec `limitsEnabled` sur les deux glissières, ce qui est la
        // façon évidente de faire — et qui **fuyait**. Mesure : le godet descendait de cinq
        // millimètres par seconde, indéfiniment, alors que la machine était censée attendre.
        // La contrainte de poulie seule, elle, tient au micron près, et une corde ordinaire
        // aussi : c'était donc la butée de glissière qui cédait sous une charge permanente.
        //
        // Le remède est de reposer sur ce que ce moteur sait faire de mieux : **un contact**.
        // Un château de pierres tient sans bouger dans le trébuchet ; deux tablettes
        // tiendront bien un contrepoids. Elles sont un corps à part et non une forme du mât,
        // parce qu'une glissière désactive la collision entre les deux corps qu'elle relie —
        // des tablettes portées par le mât se laisseraient traverser sans un mot.
        //
        // Deux tablettes par niveau plutôt qu'une seule pleine : deux points d'appui écartés
        // transmettent un couple et empêchent le contrepoids de s'incliner, et l'espace
        // laissé au milieu est celui où passe la corde.
        val basTablette = contrepoidsBas - CONTREPOIDS_DEMI_HAUTEUR - 0.035f
        val hautTablette = contrepoidsBas + POULIE_COURSE + CONTREPOIDS_DEMI_HAUTEUR + 0.035f
        val butees = scelle(PhysBody.compound(0f) {
            box(0.05f, 0.035f, -0.13f, 0f)
            box(0.05f, 0.035f, 0.13f, 0f)
            box(0.05f, 0.035f, -0.13f, hautTablette - basTablette)
            box(0.05f, 0.035f, 0.13f, hautTablette - basTablette)
        }.parPremiereForme(x + POULIE_ECART - 0.13f, basTablette).apply {
            friction = FROTTEMENT
            restitution = 0f
        }.marquer(Element.BATI))

        // Les rails ne bornent rien : ils empêchent seulement le godet et le contrepoids de
        // tanguer et de tourner. Les butées de course restent posées, très larges, comme
        // garde-fou si un contact venait à être manqué.
        val railGodet = PrismaticJoint(mat, godet).apply {
            setWorldAnchorsAndAxis(godet.x, godet.y, godet.x, godet.y, 0f, 1f)
            limitsEnabled = true
            lowerTranslation = -(POULIE_COURSE + 0.6f)
            upperTranslation = 0.4f
        }
        val railMasse = PrismaticJoint(mat, contrepoids).apply {
            setWorldAnchorsAndAxis(contrepoids.x, contrepoids.y, contrepoids.x, contrepoids.y, 0f, 1f)
            limitsEnabled = true
            lowerTranslation = -0.4f
            upperTranslation = POULIE_COURSE + 0.4f
        }
        // Un seul réa, donc les deux points de renvoi sont confondus : la corde fait un V
        // depuis le sommet du mât.
        val corde = PulleyJoint.over(
            groundAX = reaX, groundAY = reaY,
            groundBX = reaX, groundBY = reaY,
            a = godet, ax = godet.x, ay = godet.y,
            b = contrepoids, bx = contrepoids.x, by = contrepoids.y
        )

        return Piece(
            TypePiece.POULIE,
            listOf(mat, godet, contrepoids, butees),
            listOf(railGodet, railMasse, corde)
        )
    }
}
