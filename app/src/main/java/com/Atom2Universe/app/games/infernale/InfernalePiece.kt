package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.DistanceJoint
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
    CONTREPOIDS,

    /** Bille posee par le joueur, en plus de celle du tableau. */
    BILLE,

    /** Bande d'un tapis roulant. */
    TAPIS,

    /** Applique d'une torche. */
    TORCHE,

    /** Boulet du pendule : une grosse boule de fonte. */
    BOULET,

    /** Enveloppe d'un ballon de baudruche. */
    BALLON,

    /** Panier suspendu sous un ballon. */
    PANIER,

    /** Aimant : un fer a cheval qui attire (ou repousse) le metal. */
    AIMANT,

    /** Pic acere : il creve les ballons. */
    PIC,

    /** Bouton de tir d'un canon : une zone que le moindre objet mobile declenche. */
    GACHETTE,

    /** Plaque de pression : le socle, peint avec son bouton qui s'enfonce. */
    PLAQUE,

    /** Zone de detection invisible : elle se voit par ce qu'elle declenche, pas par son dessin. */
    ZONE
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
 * Une attraction : la zone d'un aimant, qui tire le **metal** vers lui.
 *
 * Comme le souffle et la portance, c'est une force posee avant chaque pas. Elle decroit avec
 * la distance, en carre : un aimant attrape fort de pres et a peine au bord de sa portee,
 * donc sa position compte. [force] est signee : negative, l'aimant **repousse**.
 *
 * Seul ce qui est en fer reagit — billes, boulets, contrepoids. Un domino d'ivoire ou une
 * planche de bois ne sentent rien, ce qui fait de l'aimant une piece selective, et pas un
 * second ventilateur.
 */
class Attraction(
    val x: Float,
    val y: Float,
    /** Force au contact, en newtons. Negative : repousse. */
    val force: Float,
    /** Rayon d'action, en metres. */
    val portee: Float
) {
    fun appliquer(corps: PhysBody) {
        if (corps.immovable || !estMetal(corps)) return
        val dx = x - corps.x
        val dy = y - corps.y
        val d = kotlin.math.hypot(dx, dy)
        if (d > portee) return
        val proche = maxOf(d, 0.2f)
        val reste = 1f - proche / portee
        if (reste <= 0f) return
        val f = force * reste * reste
        corps.applyForce(f * dx / proche, f * dy / proche)
    }

    private fun estMetal(corps: PhysBody): Boolean = when (corps.tag as? Element) {
        Element.BILLE, Element.BOULET, Element.CONTREPOIDS -> true
        else -> false
    }
}

/** Une vitesse donnee a un corps au moment du lancement : c'est ce qui fait tirer un canon. */
class Lancement(val corps: PhysBody, val vx: Float, val vy: Float)

/**
 * Une portance : ce qui fait monter un ballon.
 *
 * Comme le souffle, c'est une **force** posee avant chaque pas, pas une vitesse imposee : un
 * ballon charge monte lentement, un ballon vide s'envole, et un ventilateur le pousse bien
 * plus loin qu'une bille. S'y ajoute une **trainee** — l'air freine ce qui le traverse —
 * sans laquelle un ballon leger accelererait sans fin sous un souffle de huit newtons.
 */
class Portance(
    val corps: PhysBody,
    /** Poussee vers le haut, en newtons. */
    val levage: Float,
    /** Coefficient de trainee, en newtons par metre par seconde. */
    val trainee: Float
) {
    fun appliquer() {
        if (!corps.inWorld) return
        corps.applyForce(-trainee * corps.vx, levage - trainee * corps.vy)
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
    POULIE(Ancrage.MIXTE),

    /**
     * Une bille de plus, libre.
     *
     * ## Pourquoi c'est une pièce et pas un décor
     *
     * Tout le reste du jeu transmet un mouvement que la bille du tableau apporte. Poser une
     * **seconde** bille, c'est apporter une deuxième source : deux kilos de plus, immobiles
     * jusqu'à ce que quelque chose les libère, et qui repartent alors dans une direction que
     * la première n'aurait pas pu prendre. C'est ce qui permet la machine en deux temps —
     * la première bille remplit le godet d'une poulie, le contrepoids qui monte fait tomber
     * le domino qui retenait la seconde, et celle-ci part sur une pente que la première
     * avait déjà dépassée.
     *
     * Elle est en tout point la bille du tableau : même rayon, même masse, même faible
     * rebond. Une bille de joueur qui se comporterait autrement serait un piège.
     */
    BILLE(Ancrage.LIBRE),

    /**
     * Tapis roulant : une bande scellée qui entraîne ce qui roule dessus.
     *
     * C'est la seule pièce qui transporte **à plat**, sans perdre d'altitude. Tout le reste
     * échange de la hauteur contre du mouvement ; un tapis en fabrique, ce qui est
     * exactement ce qu'il faut quand la bille est arrivée en bas et qu'il reste deux mètres
     * à franchir.
     */
    TAPIS(Ancrage.SCELLE),

    /**
     * Ballon de baudruche et son panier : la portance fait monter, la corde retient le
     * panier. Un ventilateur le pousse, une bille lachee dans le panier le fait redescendre.
     */
    BALLON(Ancrage.LIBRE),

    /** Pendule : un pivot scelle, une barre, un lourd boulet qui se balance. */
    PENDULE(Ancrage.MIXTE),

    /**
     * Aimant : attire les billes et le fer, de plus en plus fort en s'approchant. Retourne
     * (⇄), il **repousse**. Sa force se regle d'une poignee.
     */
    AIMANT(Ancrage.SCELLE),

    /**
     * Canon : un tube scelle et une bille chargee. Il tire quand **quelque chose touche son bouton
     * rouge**, a l'arriere — une bille qui roule dessus, un domino qui tombe, un ballon qui
     * redescend. Retourne (⇄), il n'a plus de bouton et tire des le lancement.
     */
    CANON(Ancrage.MIXTE),

    /**
     * Pic : une pointe scellee, qui creve tout ballon qui la touche. Le ballon disparait, sa
     * portance avec lui, et le panier retombe — avec ce qu'il portait.
     */
    PIC(Ancrage.SCELLE),

    /**
     * Plaque de pression : un socle plat, scelle, et un bouton que tout objet mobile enfonce.
     * Reliee a un ou plusieurs canons, elle les fait tirer — c'est le seul declencheur a
     * distance du jeu.
     */
    PLAQUE(Ancrage.SCELLE),

    /**
     * Une torche, pour y voir.
     *
     * ## Pourquoi c'est une piece et pas du decor
     *
     * Il y en avait trois, semees par le generateur sur les parois. C'etait joli et c'etait
     * une decision prise a la place du joueur : elles tombaient ou elles voulaient, parfois
     * derriere la machine, et personne ne pouvait ni les bouger ni s'en passer. Un terrain
     * d'experimentation n'a pas a decider de son propre eclairage.
     *
     * Elle a un corps — une petite applique scellee — parce qu'un objet qu'on voit doit
     * exister : une torche traversee par la bille serait un mensonge de plus. Elle est assez
     * menue pour ne gener personne, et assez solide pour servir de minuscule rebord a qui
     * voudra.
     */
    TORCHE(Ancrage.SCELLE);

    /**
     * La piece a-t-elle un cote que rien d'autre ne regle ? Alors le bouton miroir la
     * retourne.
     *
     * La liste s'est raccourcie le jour ou les poignees sont arrivees. Une rampe et un
     * ventilateur s'orientent maintenant en tirant sur un bout, donc « retourner » n'y veut
     * plus rien dire — l'angle dit tout. Ne restent que les trois pieces dont le cote n'est
     * pas un angle : le sens de marche d'un tapis, le cote de la charniere d'un tremplin, et
     * lequel des deux plateaux d'une poulie porte le godet.
     */
    val miroitable: Boolean
        get() = this == TAPIS || this == TREMPLIN || this == POULIE || this == AIMANT || this == CANON ||
            this == PLAQUE
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
    val souffle: Souffle? = null,
    /** Ce qui la fait monter, quand elle en a. Seul le ballon en a. */
    val portances: List<Portance> = emptyList(),
    /** La zone d'attraction de la piece, quand elle en a une. Seul l'aimant en a une. */
    val attraction: Attraction? = null,
    /** Les corps a lancer quand le canon tire. Seul le canon en a. */
    val lancements: List<Lancement> = emptyList(),
    /** La zone de tir du canon, ou `null` s'il tire des le lancement. */
    val declencheur: PhysBody? = null,
    /** Vrai pour un canon qui tire des que la machine part, sans bouton. */
    val tireAuDepart: Boolean = false,
    /**
     * Pour une plaque : bouton **continu** (actif tant qu'on appuie) plutot qu'interrupteur
     * (chaque appui bascule l'etat).
     */
    val continu: Boolean = false
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

    /** Vrai une fois le ballon creve : son enveloppe et son panier ont quitte le monde. */
    var creve = false

    /**
     * Les eclats a dessiner : un par morceau detruit, `[x, y, genre]` avec le genre 0 pour
     * l'enveloppe et 1 pour le panier. [eclatAge] compte les secondes depuis l'eclatement.
     */
    val eclats = ArrayList<FloatArray>()
    var eclatAge = -1f

    /** Pour une plaque : est-elle allumee en ce moment ? Les pieces liees la lisent. */
    var actif = false

    /** Pour un interrupteur : le moment du dernier basculement, pour ignorer les rebonds. */
    var dernierBascule = -10f

    /**
     * Vrai pour un ventilateur commande par une plaque tant qu'aucune de ses plaques n'est
     * allumee : il est a l'arret. Remis a jour a chaque image par le plateau.
     */
    var eteint = false

    /** Combien d'objets touchent en ce moment la zone de detection de la piece (plaque, canon). */
    var contacts = 0

    /** Vrai une fois que le canon a tire : il ne tire qu'une fois par lancement. */
    var parti = false
        private set

    /** Declenche la piece, une seule fois : un canon tire, une plaque s'enfonce. */
    fun tirer() {
        if (parti) return
        parti = true
        for (l in lancements) {
            l.corps.wake()
            l.corps.vx = l.vx
            l.corps.vy = l.vy
        }
    }

    /** Appele quand la machine part : un canon sans bouton tire tout de suite. */
    fun demarrer() {
        if (tireAuDepart) tirer()
    }

    /** Les cordes et barres de la piece : le dessin les trace entre leurs ancrages. */
    val tringles: List<DistanceJoint> get() = liaisons.filterIsInstance<DistanceJoint>()

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
    const val BILLE_RAYON = 0.11f
    const val TAPIS_LONGUEUR = 1.3f
    const val POULIE_HAUTEUR = 1.8f
    const val BALLON_RAYON = 0.35f
    const val PENDULE_LONGUEUR = 1.2f
    const val PENDULE_ANGLE = 45f
    const val AIMANT_COTE = 0.34f
    const val PIC_DEMI_LARGEUR = 0.05f
    const val PIC_DEMI_HAUTEUR = 0.18f
    const val CANON_DEMI_LONGUEUR = 0.35f
    const val GACHETTE_RAYON = 0.1f
    const val PLAQUE_LARGEUR = 0.5f
    const val PLAQUE_DEMI_HAUTEUR = 0.03f
    const val GACHETTE_DISTANCE = 0.52f

    /**
     * Force reglable par defaut selon le type : un aimant doit pouvoir soulever une bille
     * (vingt newtons), un canon tire a dix metres par seconde, un ventilateur reste doux.
     */
    fun forceParDefaut(type: TypePiece): Float = when (type) {
        TypePiece.AIMANT -> 25f
        TypePiece.CANON -> 20f
        else -> SOUFFLE_POUSSEE
    }

    /** Vitesse de sortie d'un canon pour une force reglee : un demi-metre par seconde par newton. */
    fun canonVitesse(force: Float): Float = force * 0.5f

    /** Portee d'un aimant : plus fort, il porte plus loin, en racine. */
    fun aimantPortee(force: Float): Float =
        (1.2f * kotlin.math.sqrt(force / 10f)).coerceIn(0.8f, 4f)

    /** Rayons extremes d'un ballon : en dessous de 0,33 m il ne sait plus lever son panier. */
    const val BALLON_RAYON_MIN = 0.33f
    const val BALLON_RAYON_MAX = 0.7f

    /** Hauteur des bords du panier. */
    const val PANIER_HAUT = 0.2f

    /** Longueur de corde entre le bas du ballon et le haut du panier. */
    const val BALLON_CORDE = 0.9f


    /** Hauteur du centre d'un ballon pose sur un panier dont le bas est a [bas]. */
    fun ballonCentre(bas: Float, rayon: Float): Float = bas + PANIER_HAUT + BALLON_CORDE + rayon

    // ── Le ventilateur ───────────────────────────────────────────────────────

    /** Longueur du jet, en mètres. */
    const val SOUFFLE_PORTEE = 2.2f

    /** Demi-largeur du jet. */
    const val SOUFFLE_DEMI_LARGEUR = 0.32f

    /**
     * Poussée à la bouche, en newtons.
     *
     * Valeur par defaut, reglable de [SOUFFLE_POUSSEE_MIN] a [SOUFFLE_POUSSEE_MAX]. Elle est
     * **volontairement plus faible que le poids de la bille** (deux kilos, donc presque vingt
     * newtons). Un ventilateur qui pourrait la soulever ferait de chaque
     * tableau un problème de vol stationnaire, où la position exacte de la bille dans le
     * jet déciderait de tout : injouable et impossible à viser. À huit newtons il dévie,
     * il ralentit, il pousse une bille qui roule et il renverse un domino — ce qui est
     * une panoplie de verbes largement suffisante.
     */
    const val SOUFFLE_POUSSEE = 8f

    /** Poussee extremes que le joueur peut regler, en newtons. */
    const val SOUFFLE_POUSSEE_MIN = 1f
    const val SOUFFLE_POUSSEE_MAX = 40f

    /**
     * Longueur du jet pour une poussee donnee : un ventilateur plus puissant porte plus loin,
     * mais en racine — doubler la force ne double pas la portee.
     */
    fun porteePour(poussee: Float): Float =
        (SOUFFLE_PORTEE * kotlin.math.sqrt(poussee / SOUFFLE_POUSSEE)).coerceIn(0.9f, 5f)

    // ── La poulie ────────────────────────────────────────────────────────────

    /**
     * Ecart horizontal entre le centre de la piece et chacun des deux corps pendus.
     *
     * Le godet est a gauche, le contrepoids a droite, et le bouton que vise le generateur
     * se pose exactement a l'aplomb du second.
     */
    const val POULIE_ECART = 0.5f

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
    fun bascule(
        x: Float,
        bas: Float,
        longueur: Float = BASCULE_LONGUEUR,
        decalage: Float = 0f,
        hauteurPied: Float = 0.3f
    ): Piece {
        val sommet = bas + hauteurPied
        val pied = scelle(PhysBody(0.07f, hauteurPied / 2f, 0f).apply {
            this.x = x
            this.y = bas + hauteurPied / 2f
            friction = FROTTEMENT
        }.marquer(Element.BATI))
        // [decalage] : de combien le centre de la planche est decale du pied, en metres. A zero
        // la planche est equilibree ; decalee, le cote long est plus lourd et penche — c'est un
        // levier, et c'est le joueur qui place le pivot. Aucune butee : la planche tourne
        // librement, et seul le sol (ou son propre pied) l'arrete.
        val planche = PhysBody(longueur / 2f, 0.04f, 1.2f).apply {
            this.x = x + decalage
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
        poussee: Float = SOUFFLE_POUSSEE,
        portee: Float = porteePour(poussee)
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
        pente: Float = 0f,
        rebond: Float = 0.92f
    ): Piece {
        val peau = PhysBody(largeur / 2f, 0.05f, 0f).apply {
            this.x = x
            this.y = y
            angle = -Math.toRadians(pente.toDouble()).toFloat()
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
    fun poulie(
        x: Float,
        bas: Float,
        hauteur: Float = POULIE_HAUTEUR,
        sens: Float = 1f
    ): Piece {
        // Le miroir echange les deux plateaux : le godet passe a droite, le contrepoids a
        // gauche. Rien d'autre ne change, le mat restant au milieu.
        val cote = if (sens < 0f) -1f else 1f
        val reaX = x
        val reaY = bas + hauteur

        val mat = scelle(PhysBody(0.06f, hauteur / 2f, 0f).apply {
            this.x = x
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
        }.parPremiereForme(x - cote * POULIE_ECART, godetCentre - GODET_DEMI_HAUTEUR).apply {
            friction = FROTTEMENT
            restitution = 0.02f
        }.marquer(Element.GODET)

        val contrepoidsBas = poulieContrepoidsBas(bas)
        val contrepoids = PhysBody(0.1f, CONTREPOIDS_DEMI_HAUTEUR, POULIE_MASSE_CONTREPOIDS).apply {
            this.x = x + cote * POULIE_ECART
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
        }.parPremiereForme(x + cote * POULIE_ECART - 0.13f, basTablette).apply {
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

    /**
     * Une bille de plus, posee par le joueur. [bas] est le point ou elle touche.
     *
     * Rigoureusement identique a celle du tableau — meme rayon, meme masse, meme faible
     * rebond. Une bille de joueur qui se comporterait autrement serait un piege, et le
     * joueur passerait son temps a se demander laquelle il regarde.
     */
    fun bille(x: Float, bas: Float, rayon: Float = BILLE_RAYON, masse: Float = 2f): Piece {
        val boule = PhysBody.circle(rayon, masse).apply {
            this.x = x
            this.y = bas + rayon
            friction = 0.25f
            restitution = 0.1f
        }.marquer(Element.BILLE)
        return Piece(TypePiece.BILLE, listOf(boule), emptyList())
    }

    /**
     * Tapis roulant : une bande scellee qui entraine ce qui roule dessus, dans le sens
     * [sens].
     *
     * C'est la seule piece qui transporte **a plat**. Tout le reste du jeu echange de la
     * hauteur contre du mouvement ; un tapis en fabrique, ce qui est exactement ce qu'il
     * faut quand la bille est arrivee en bas et qu'il reste deux metres a franchir.
     *
     * Le moteur savait deja le faire : `PhysBody.surfaceSpeed` demande aux deux surfaces
     * en contact une vitesse relative le long de la tangente, et le solveur la fournit
     * dans la limite de `mu x impulsion normale` — donc dans la limite de ce que le poids
     * pose dessus autorise. Un tapis ne peut pas entrainer ce qui ne le touche pas, et
     * n'entraine que mollement ce qui l'effleure : c'est ce que fait un vrai tapis.
     *
     * D'ou le frottement eleve de la bande. Les frottements se combinent en racine du
     * produit, et une bille a 0,25 sur une bande a 0,25 ne serait entrainee qu'au quart de
     * son poids : le tapis patinerait.
     *
     * ## Un tapis ne remonte pas une bille, et ce n'est pas un defaut
     *
     * Mesure : une bande inclinee a dix degres, qui tourne vers le haut a 2,4 m/s, laisse
     * la bille **redescendre**. A vingt degres elle repart franchement en arriere. Et sur
     * une bande horizontale a 2,4 m/s, la bille ne depasse jamais 1 m/s.
     *
     * La raison est jolie et n'a rien a voir avec le frottement. Le tapis impose une vitesse
     * au **point de contact**, pas au centre. L'impulsion tangentielle change la vitesse du
     * centre de `J/m` et la rotation de `J·r/I` ; pour un disque plein, `r²/I = 2/m`, donc
     * le point de contact accelere **trois fois plus vite** que le centre. Le tapis a
     * rattrape sa vitesse relative alors que la bille n'a pris que le tiers du chemin : le
     * reste est parti en rotation. Sur une pente, ce tiers ne suffit pas contre la pesanteur.
     *
     * C'est exactement ce que fait un vrai tapis : il transporte des caisses, pas des billes.
     * L'inclinaison reste utile — une bande descendante lance bien plus fort qu'une planche,
     * et une bande inclinee devie — mais elle ne fait pas gagner de hauteur. Pour remonter,
     * il y a la poulie.
     */
    fun tapis(
        x: Float,
        y: Float,
        longueur: Float = TAPIS_LONGUEUR,
        pente: Float = 0f,
        sens: Float = 1f,
        vitesse: Float = 2.4f
    ): Piece {
        // La bande entraine le long de son propre x, donc l'inclinaison suffit a en faire
        // un tapis qui monte : le moteur applique la vitesse de surface dans le repere du
        // corps, et un corps incline entraine en pente. Rien de plus a ecrire.
        val bande = PhysBody(longueur / 2f, 0.07f, 0f).apply {
            this.x = x
            this.y = y
            angle = -Math.toRadians(pente.toDouble()).toFloat()
            friction = 1.4f
            restitution = 0f
            surfaceSpeed = if (sens < 0f) -vitesse else vitesse
        }.marquer(Element.TAPIS)
        return Piece(TypePiece.TAPIS, listOf(scelle(bande)), emptyList())
    }

    /**
     * Ballon de baudruche : une enveloppe legere qui monte, un panier ouvert qui pend dessous,
     * et deux cordes qui les tiennent. [bas] est le dessous du panier.
     *
     * ## La portance est une force, et le panier est ouvert
     *
     * Le ballon monte parce qu'une force l'y pousse ([Portance]), pas parce qu'on lui impose
     * une vitesse : charge, il monte lentement ; un ventilateur le deporte ; une bille lachee
     * dans le panier le fait redescendre, et un gros ballon la remonte. Le panier est un godet
     * large de trente-quatre centimetres, assez pour une bille.
     *
     * Les cordes sont de vraies cordes : elles retiennent et ne poussent jamais, donc un panier
     * pose au sol laisse le ballon se balancer au-dessus sans rien casser. Elles forment un V
     * depuis le bas du ballon, pour que le panier reste droit.
     */
    fun ballon(x: Float, bas: Float, rayon: Float = BALLON_RAYON): Piece {
        val demiLargeur = 0.17f
        val panier = PhysBody.compound(0.3f) {
            box(demiLargeur, 0.02f, 0f, 0f)
            box(0.02f, PANIER_HAUT / 2f, -demiLargeur, PANIER_HAUT / 2f - 0.02f)
            box(0.02f, PANIER_HAUT / 2f, demiLargeur, PANIER_HAUT / 2f - 0.02f)
        }.parPremiereForme(x, bas + 0.02f).apply {
            friction = FROTTEMENT
            restitution = 0.05f
        }.marquer(Element.PANIER)

        val centre = ballonCentre(bas, rayon)
        val enveloppe = PhysBody.circle(rayon, 0.06f).apply {
            this.x = x
            this.y = centre
            friction = 0.3f
            restitution = 0.4f
        }.marquer(Element.BALLON)

        val basBallon = centre - rayon
        val bordHaut = bas + PANIER_HAUT
        val cordes = listOf(-demiLargeur, demiLargeur).map { dx ->
            DistanceJoint.between(
                enveloppe, x, basBallon,
                panier, x + dx, bordHaut,
                rope = true
            )
        }
        val facteur = rayon / BALLON_RAYON
        val levage = 5f * facteur * facteur * facteur
        val trainee = 0.8f * facteur * facteur
        return Piece(
            TypePiece.BALLON, listOf(panier, enveloppe), cordes,
            portances = listOf(Portance(enveloppe, levage, trainee))
        )
    }

    /**
     * Pendule : un pivot scelle en ([x], [y]), une barre de [longueur], et un boulet de six
     * kilos au bout. [angle] est l'ecart de depart en degres : 0 pend droit, positif envoie le
     * boulet a droite.
     *
     * Le boulet part sans vitesse : on le pose en l'air, on lance, il tombe et balaie ce qui
     * est sur sa route. C'est la seule piece qui frappe **de cote avec de l'elan**, ce que ni
     * une bille ni un domino ne savent faire.
     *
     * La barre est rigide (une liaison de distance, pas une corde) : une corde molle ferait
     * s'affaisser le pendule des qu'il est sous le pivot.
     */
    fun pendule(x: Float, y: Float, longueur: Float = PENDULE_LONGUEUR, angle: Float = PENDULE_ANGLE): Piece {
        val pivot = scelle(PhysBody(0.06f, 0.06f, 0f).apply {
            this.x = x
            this.y = y
            friction = FROTTEMENT
        }.marquer(Element.BATI))
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val bx = x + sin(rad) * longueur
        val by = y - cos(rad) * longueur
        val boulet = PhysBody.circle(0.17f, 6f).apply {
            this.x = bx
            this.y = by
            friction = 0.3f
            restitution = 0.2f
        }.marquer(Element.BOULET)
        val barre = DistanceJoint.between(pivot, x, y, boulet, bx, by, rope = false)
        return Piece(TypePiece.PENDULE, listOf(pivot, boulet), listOf(barre))
    }

    /**
     * Aimant en fer a cheval, centre en ([x], [y]). [force] est la traction au contact, en
     * newtons ; [repousse] inverse le signe. Le corps est scelle : l'aimant ne bouge pas, il
     * agit a distance — voir [Attraction].
     *
     * Retourne, il tourne d'un demi-tour : c'est ce qui fait lire la polarite sur le dessin,
     * et ce qui distingue deux aimants au meme endroit sans rien ecrire.
     */
    fun aimant(x: Float, y: Float, force: Float, repousse: Boolean = false): Piece {
        val corps = PhysBody(AIMANT_COTE / 2f, AIMANT_COTE / 2f, 0f).apply {
            this.x = x
            this.y = y
            angle = if (repousse) Math.PI.toFloat() else 0f
            friction = 0.4f
            restitution = 0.1f
        }.marquer(Element.AIMANT)
        val signee = if (repousse) -force else force
        return Piece(
            TypePiece.AIMANT, listOf(scelle(corps)), emptyList(),
            attraction = Attraction(x, y, signee, aimantPortee(force))
        )
    }

    /**
     * Canon : un tube ouvert (deux plaques et une culasse), scelle, et une bille chargee dedans.
     * ([x], [y]) est le centre du tube, [direction] en degres comme un ventilateur, et la
     * bille part a [vitesse] metres par seconde **au lancement** ([Piece.lancer]).
     *
     * Le tube est un U couche : la bille repose sur la plaque basse et ne peut pas reculer. Elle
     * sort donc dans l'axe, et son poids joue des qu'elle a quitte le tube — c'est ce qui fait
     * une vraie parabole, et c'est pourquoi la vitesse seule regle la portee.
     */
    fun canon(
        x: Float,
        y: Float,
        direction: Float = 0f,
        vitesse: Float = canonVitesse(20f),
        auDepart: Boolean = false
    ): Piece {
        val rad = Math.toRadians(direction.toDouble()).toFloat()
        val c = cos(rad)
        val sn = sin(rad)
        val plaque = 0.12f
        val tube = PhysBody.compound(0f) {
            box(CANON_DEMI_LONGUEUR, 0.02f, 0f, plaque)
            box(CANON_DEMI_LONGUEUR, 0.02f, 0f, -plaque)
            box(0.02f, plaque + 0.02f, -CANON_DEMI_LONGUEUR, 0f)
        }
        // L'origine du croquis n'est pas celle du corps : on retrouve ou elle est dans le
        // repere du corps pour que le centre du tube tombe bien en (x, y) apres rotation.
        val ox = tube.localOffsetX(0)
        val oy = tube.localOffsetY(0) - plaque
        tube.angle = rad
        tube.x = x - (c * ox - sn * oy)
        tube.y = y - (sn * ox + c * oy)
        tube.friction = 0.3f
        tube.restitution = 0.1f
        tube.marquer(Element.BATI)

        val rayon = 0.09f
        val charge = PhysBody.circle(rayon, 1.2f).apply {
            this.x = x - c * 0.18f
            this.y = y - sn * 0.18f
            friction = 0.25f
            restitution = 0.1f
        }.marquer(Element.BILLE)
        val lancement = Lancement(charge, c * vitesse, sn * vitesse)

        // Le bouton de tir : une zone ronde juste derriere la culasse, hors de portee de la
        // bille chargee (qui est a l'interieur). Tout ce qui bouge et l'effleure declenche.
        val gachette = if (auDepart) null else PhysBody.circle(GACHETTE_RAYON, 0f).apply {
            this.x = x - c * GACHETTE_DISTANCE
            this.y = y - sn * GACHETTE_DISTANCE
            isSensor = true
            collidesWith = Plateau.MOBILE
        }.marquer(Element.GACHETTE)
        val corps = listOfNotNull(scelle(tube), charge, gachette?.let { scelle(it) })
        return Piece(
            TypePiece.CANON, corps, emptyList(),
            lancements = listOf(lancement),
            declencheur = gachette,
            tireAuDepart = auDepart
        )
    }

    /**
     * Pic : une pointe de [direction] degres (comme un ventilateur : 0 vers la droite, 90 vers
     * le haut), centree en ([x], [y]). Etroit exprès : une bille ne doit pas pouvoir se percher
     * sur sa pointe plus qu'un instant, et un ballon doit la sentir du premier contact.
     */
    fun pic(x: Float, y: Float, direction: Float = 90f): Piece {
        val corps = PhysBody(PIC_DEMI_LARGEUR, PIC_DEMI_HAUTEUR, 0f).apply {
            this.x = x
            this.y = y
            // Le corps est haut dans son propre repere, pointe vers +y : tourner de
            // (direction - 90) degres l'oriente.
            angle = Math.toRadians((direction - 90f).toDouble()).toFloat()
            friction = 0.3f
            restitution = 0.1f
        }.marquer(Element.PIC)
        return Piece(TypePiece.PIC, listOf(scelle(corps)), emptyList())
    }

    /**
     * Plaque de pression centree en ([x], [y]) : un socle de [largeur] et, juste dessus, une
     * zone de detection qui sent tout objet mobile. Par defaut un **interrupteur** : chaque
     * appui bascule son etat, et un voyant dit s'il est allume. [continu] en fait un bouton :
     * allume tant qu'on appuie dessus, eteint des qu'on la quitte. Elle n'agit que par ses **liens** : voir
     * [Plateau.liens]. Seule, elle n'est qu'un bouton qui s'enfonce.
     *
     * La zone est un capteur, pas un corps : une bille y roule sans etre arretee, et le socle
     * scelle ne bouge pas. Elle est un peu plus etroite que le socle pour qu'un objet pose de
     * travers sur le bord ne declenche rien.
     */
    fun plaque(x: Float, y: Float, largeur: Float = PLAQUE_LARGEUR, continu: Boolean = false): Piece {
        val socle = scelle(PhysBody(largeur / 2f, PLAQUE_DEMI_HAUTEUR, 0f).apply {
            this.x = x
            this.y = y
            friction = 0.6f
            restitution = 0.05f
        }.marquer(Element.PLAQUE))
        val zone = scelle(PhysBody(largeur / 2f - 0.03f, 0.04f, 0f).apply {
            this.x = x
            this.y = y + PLAQUE_DEMI_HAUTEUR + 0.04f
            isSensor = true
            collidesWith = Plateau.MOBILE
        }.marquer(Element.ZONE))
        return Piece(TypePiece.PLAQUE, listOf(socle, zone), emptyList(), declencheur = zone, continu = continu)
    }

    /** Une torche murale. [y] est le bas de son applique. */
    fun torche(x: Float, y: Float): Piece {
        val applique = PhysBody(0.05f, 0.13f, 0f).apply {
            this.x = x
            this.y = y + 0.13f
            friction = FROTTEMENT
            restitution = 0.05f
        }.marquer(Element.TORCHE)
        return Piece(TypePiece.TORCHE, listOf(scelle(applique)), emptyList())
    }
}
