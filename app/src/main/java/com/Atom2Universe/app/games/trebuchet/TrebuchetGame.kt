package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.DistanceJoint
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import com.Atom2Universe.app.games.physics.RevoluteJoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dimensions du terrain et catalogue de pièces, en mètres (repère physique,
 * Y vers le haut, origine au sol sous le pivot, tir vers les X croissants).
 *
 * La machine est orientée **comme sur les gravures** : bandée, la pointe du bras
 * plonge vers l'**arrière**, la fronde est couchée au sol **sous** la machine, et
 * le contrepoids est levé du côté de la **cible**. Au tir, le boulet est traîné
 * sous le bâti, monte en fouettant côté arrière, passe **par-dessus la pointe**
 * et part vers l'avant — c'est le tour complet du fouet, pas un lancer à plat.
 *
 * La machine est un **vrai trébuchet**, c'est-à-dire les trois pièces que les
 * ingénieurs médiévaux ont fini par trouver, et dont aucune n'est décorative :
 *
 *  1. un **bras** sur un pivot haut, très inégal : bras court côté contrepoids,
 *     bras long côté projectile ;
 *  2. un **contrepoids pendulaire**, suspendu à l'extrémité du bras court par une
 *     chape et non boulonné dessus. Pendu, il tombe presque à la verticale au lieu
 *     de décrire un arc, et rend donc presque toute sa hauteur ;
 *  3. une **fronde**, une corde entre la pointe du bras et le boulet, dont une
 *     extrémité est passée sur un **crochet de largage**. La fronde double le bras
 *     au dernier moment — le boulet sort environ deux fois plus vite que la pointe —
 *     et c'est l'inclinaison du crochet qui décide de l'instant du largage, donc de
 *     l'angle de tir.
 *
 * Ce que la machine faisait avant : un contrepoids lâché de haut qui percutait le
 * bras, un boulet posé dans une cuiller rigide, et un tampon pour arrêter le bras.
 * Mesuré au banc d'essai, ça sortait à 14 m/s pour 21 m de portée. Le choc
 * gaspillait, la cuiller rigide était bistable, et l'arrêt brutal du bras, qui
 * tenait lieu de largage, jetait le reste. Puis la fronde est arrivée, mais montée
 * en miroir : la machine larguait tôt, dans la montée du fouet, et plafonnait à
 * 220 m. Remise dans le sens des gravures, le même banc donne plus de 400 m à la
 * plus petite machine et plus de 500 m à la plus grande, au-delà de 100 m/s.
 *
 * Tous les réglages sont **continus et toujours entièrement disponibles** : le
 * joueur les prend en main directement sur la machine, et aucun niveau ne lui
 * impose un stock. Chacun n'est borné que par ce que la mécanique supporte.
 */
object TrebuchetRules {

    /** La portée se mesure depuis le pied de la machine. */
    const val FIRING_LINE = 0f

    /**
     * Le terrain. À gauche, de quoi voir retomber un tir parti en arrière, ce qui
     * arrive vite. À droite, le kilomètre : depuis que les réglages sont continus,
     * le joueur peut construire des monstres de vingt mètres de bras chargés de
     * douze tonnes, et le banc les voyait buter contre le bord du monde au lieu de
     * retomber. La meilleure machine du balayage tombe à 691 m : le terrain lui
     * laisse trois cents mètres de marge, de quoi encaisser mieux qu'elle.
     */
    const val GROUND_LEFT = -260f
    const val GROUND_RIGHT = 1000f

    /**
     * Longueur de bras permise, en mètres.
     *
     * Une machine de jet vit de son élan : la vitesse de la pointe vaut oméga fois
     * le bras long, et la fronde double encore ça. Mais un bras long est aussi un
     * bras lourd, qui emporte lui-même une bonne part de l'énergie du contrepoids :
     * c'est ce qui crée l'optimum, et c'est pour ça que les vraies machines sont
     * énormes mais pas infinies.
     */
    const val BEAM_MIN = 6f
    const val BEAM_MAX = 50f

    /**
     * Masse du bras par mètre : une poutre de chêne d'une vingtaine de centimètres.
     *
     * Elle compte plus qu'on ne croit. Le bras finit sa course dressé, son centre de
     * masse tout en haut, et cette hauteur-là est de l'énergie que le contrepoids a
     * payée sans que le boulet en voie la couleur. Il tourne aussi, et sa rotation
     * emporte autant que celle du boulet quand il est trop lourd.
     */
    const val BEAM_DENSITY = 8f
    const val BEAM_HALF_THICKNESS = 0.09f

    /**
     * Hauteur du pivot. Elle se règle en mètres, mais ses bornes s'expriment en
     * fraction du bras : un pied de huit mètres est haut pour une poutre de huit
     * mètres et ridicule pour une poutre de vingt.
     *
     * Elle ne sert pas qu'à faire joli : c'est elle qui décide de **l'angle
     * d'armement**. Un pivot haut laisse le bras long descendre plus près de la
     * verticale, donc le contrepoids monte plus haut, donc la machine stocke plus
     * d'énergie et le bras a plus de course pour la rendre.
     */
    const val POST_MIN_RATIO = 0.35f
    const val POST_MAX_RATIO = 1f

    /**
     * Position de l'axe le long du bras, en rapport bras long / bras court.
     *
     * C'est le réglage qui échange la force contre la vitesse. Bras court long : le
     * contrepoids descend de haut, beaucoup d'énergie, mais le bras tourne
     * lentement. Bras court ramassé : peu d'énergie, mais un fouet très rapide.
     */
    const val LEVER_MIN = 2f
    const val LEVER_MAX = 8f

    /**
     * Contrepoids permis, en kilogrammes — des tonnes, comme les vraies machines.
     *
     * Le rapport qui compte est celui du contrepoids au boulet : les trébuchets
     * historiques tournaient entre cent et quatre cents fois. En dessous, le boulet
     * est trop lourd pour la machine, il freine le bras au lieu de se laisser
     * emporter, et la moitié de l'énergie reste dans la charpente.
     */
    const val CW_MIN = 300f
    const val CW_MAX = 50000f

    /**
     * Longueur de la chape qui suspend le contrepoids. Elle se règle en mètres, et
     * son maximum s'exprime en fraction du bras court — au-delà, le contrepoids
     * balance dans le vide au lieu de tomber.
     *
     * C'est le réglage de **synchronisation** de la machine. Le contrepoids pendu
     * est un pendule : il a son propre temps de balancement. Trop courte, la chape
     * le colle au bras et il se comporte comme s'il y était boulonné — on perd tout
     * le gain du pendulaire. Trop longue, il balance en retard et le bras est déjà
     * reparti quand il pousse enfin. Entre les deux, il tombe presque droit et rend
     * tout ce qu'il a.
     */
    const val HANG_MIN = 0.15f
    const val HANG_MAX_RATIO = 2.5f

    /**
     * Inclinaison du crochet de largage sur l'axe du bras, en degrés — **la visée**.
     *
     * La fronde a deux brins. L'un est noué à la pointe du bras et ne la quitte
     * jamais ; l'autre finit par une boucle simplement posée sur un ergot recourbé,
     * le crochet. Tant que la fronde tire dans l'axe du crochet, la boucle y reste ;
     * dès qu'elle passe au-delà, la boucle glisse et le boulet part.
     *
     * L'angle se mesure depuis l'axe du bras, du côté où la fronde traîne. Bandée,
     * la machine a sa fronde loin derrière elle — entre 90 et 145° selon la
     * géométrie — et le fouet la rabat vers l'axe du bras, qu'elle finit par
     * prolonger au sommet du tour. Elle croise donc le crochet en chemin, et le
     * sens est celui-ci, mesuré au banc et non supposé :
     *
     *  - **crochet couché** (petit angle) : il retient la boucle presque jusqu'au
     *    bout du fouet. Le boulet passe par-dessus la pointe à pleine vitesse —
     *    au-delà de quatre-vingt-dix mètres par seconde — et part vers l'avant,
     *    d'autant plus tendu que le crochet est couché. C'est le coup de trébuchet
     *    des gravures.
     *  - **crochet redressé** (grand angle) : la fronde le rencontre **tôt**, alors
     *    que le boulet grimpe encore derrière la machine. Le tir part de plus en
     *    plus haut, puis carrément en arrière, dans les pieds des servants.
     *
     * C'est exactement le réglage que les constructeurs de trébuchets passent leurs
     * journées à limer, et il n'y a rien d'arbitraire dedans : le moment du largage
     * est une **conséquence géométrique** de l'angle de la fronde, mesuré à chaque
     * pas. Les bornes non plus ne sont pas choisies au jugé : elles encadrent
     * largement la plage où le banc d'essai trouve les tirs utiles, laquelle dépend
     * beaucoup de la longueur de la fronde — une fronde courte veut un crochet
     * couché. C'est ce qui lie les deux réglages fins de la machine, et c'est
     * pourquoi ils se règlent tous les deux au doigt, l'un après l'autre.
     */
    const val PIN_MIN_DEG = 5f
    const val PIN_MAX_DEG = 85f

    /**
     * Ce que la fronde doit au minimum balayer avant que la boucle puisse glisser.
     *
     * Un crochet plus redressé que la fronde bandée ne la retiendrait pas du tout :
     * la boucle serait déjà du mauvais côté au moment de bander la machine. On le
     * couche alors juste assez pour qu'il tienne, plutôt que de larguer le boulet
     * dans les pieds du joueur.
     */
    const val MIN_PIN_SWEEP_DEG = 10f

    /**
     * Longueur de la fronde, en fraction du bras long. Réglage continu : on fait
     * glisser le boulet sur le sol pour l'allonger ou la raccourcir.
     *
     * Une fronde longue donne plus de rayon donc plus de vitesse, mais met plus de
     * temps à faire son tour : passé un certain point, le bras a fini sa course
     * avant que la fronde ait fini la sienne, et le tir part n'importe comment.
     */
    const val SLING_MIN_RATIO = 0.25f
    const val SLING_MAX_RATIO = 0.95f

    /**
     * Le boulet de référence : une belle pierre de taille.
     *
     * Ce sont les chiffres de [Projectile.BOULET], recopiés ici parce que toute la
     * documentation de la machine et tous les bancs d'essai s'y réfèrent. Le tir, lui,
     * lit le projectile choisi et non ces constantes.
     */
    const val BALL_RADIUS = 0.16f
    const val BALL_MASS = 12f

    /**
     * Traînée du boulet, en kg/m : la moitié de ρ·Cx·S pour une sphère de ce rayon.
     * À 70 m/s elle vaut à peu près le poids du boulet — un trébuchet qui porte
     * loin est une machine dont l'air décide autant que la géométrie.
     */
    const val BALL_DRAG = 0.023f

    /**
     * Nombre de tirs gardés en mémoire par défaut, le plus récent compris.
     *
     * Dix pour commencer, et pas tous : au-delà, la nappe de traits devient une
     * bouillie où le tir qu'on vient de faire ne se distingue plus, et c'est lui qui
     * compte. Mais c'est un jugement, pas une mesure — le coût, lui, n'entre pas en
     * ligne de compte, une trajectoire pesant deux mille flottants et la vue ne
     * dessinant que ce qui tient à l'écran. Le joueur peut donc en décider autrement,
     * voir [GHOST_MAX].
     */
    const val GHOST_HISTORY = 10

    /**
     * Le plus grand nombre de tirs qu'on puisse garder à l'écran.
     *
     * Le joueur le règle au curseur, de zéro à cinquante, un par un — il y avait cinq
     * paliers figés (10, 20, 30, 40, 50), ce qui interdisait « juste les trois derniers »,
     * qui est pourtant le réglage le plus utile quand on cherche un écart de dix mètres.
     */
    const val GHOST_MAX = 50

    /** Garde au sol de la pointe du bras quand la machine est bandée. */
    const val TIP_CLEARANCE = 0.35f

    /** Garde au sol du contrepoids au plus bas de sa course. */
    const val CW_CLEARANCE = 0.2f

    /** Bornes de l'angle d'armement : au-delà la machine n'a plus de sens. */
    const val MIN_COCK_DEG = 15f
    const val MAX_COCK_DEG = 70f

    const val GRAVITY = 9.81f

    /**
     * Demi-côté de la caisse du contrepoids : de la pierre entassée, à peu près deux
     * tonnes et demie au mètre cube. La masse suit la surface, donc le côté suit la
     * racine — c'est ce qui évite qu'un contrepoids lourd devienne monstrueux.
     */
    fun counterweightHalfSize(mass: Float): Float = 0.0105f * sqrt(mass)
}

/**
 * Les choix du joueur. Tout ce qui définit la machine tient ici, ce qui permet de
 * rejouer un tir à l'identique — et, plus tard, de faire simuler au générateur de
 * niveaux une machine tirée au sort.
 */
class MachineConfig {

    /**
     * Toutes les valeurs de départ décrivent la même machine qu'avant : une poutre
     * de douze mètres, un pied de huit et demi, un levier de quatre, trois tonnes
     * pendues à deux mètres quarante et un crochet à quarante-cinq degrés. Elle
     * tire une belle cloche par-dessus la pointe : ça marche, et tout se laisse
     * nettement améliorer.
     */

    /** Longueur totale de la poutre, en mètres. */
    var beamLength = 12f

    /** Hauteur de l'axe au-dessus du sol, en mètres. */
    var pivotHeight = 8.4f

    /** Rapport bras long / bras court : où l'axe est planté dans la poutre. */
    var leverRatio = 4f

    /** Masse du contrepoids, en kilogrammes. */
    var counterweightMass = 3000f

    /** Longueur de la chape qui pend sous le bras court, en mètres. */
    var hangLength = 2.4f

    /** Inclinaison du crochet de largage sur l'axe du bras, en degrés. */
    var pinAngleDeg = 45f

    /** Longueur de la fronde, en fraction du bras long. */
    var slingRatio = 0.65f

    /**
     * Ce qu'on a mis dans la fronde.
     *
     * C'est un réglage comme les autres, et il est ici pour la même raison que les
     * autres : un tir se rejoue à l'identique à partir de cette classe seule. Il est
     * aussi le seul qui change la **masse lancée**, donc l'accord de toute la machine.
     */
    var projectile = Projectile.BOULET

    /**
     * Combien de bâtons de poudre dans la bombe.
     *
     * Sans effet sur les autres projectiles, qui n'ont pas de charge. Sur la bombe, il
     * agit sur **les deux bouts à la fois** : plus de poudre fait un souffle plus large
     * et plus dur, et une bombe plus lourde, donc un tir plus court. C'est le seul
     * réglage du jeu dont on ne peut pas dire s'il faut le monter ou le descendre sans
     * savoir à quelle distance on tire.
     */
    var bombSticks = Projectile.DEFAULT_STICKS

    /** Le poids du boulet, en kilogrammes. Sans effet sur les autres projectiles. */
    var ballMass = Projectile.DEFAULT_BALL_MASS

    /**
     * La masse réellement lancée.
     *
     * **Tout ce qui a besoin de savoir ce que la machine soulève passe par ici, et par
     * rien d'autre.** Deux projectiles sur trois ont une masse qui n'est plus dans la
     * table — le boulet parce qu'on le pèse, la bombe parce qu'on la charge — et un
     * seul endroit du code qui l'aurait oublié suffirait à faire diverger la balistique
     * de ce que le joueur voit à l'écran.
     */
    val shotMass: Float
        get() = when {
            projectile.explosive -> projectile.massFor(bombSticks)
            projectile.weighable -> ballMass
            else -> projectile.mass
        }

    /** Le rayon qui va avec cette masse-là : un projectile lourd est un projectile gros. */
    val shotRadius: Float get() = projectile.radiusFor(shotMass)

    /** La traînée qui va avec ce rayon-là. */
    val shotDrag: Float get() = projectile.dragFor(shotRadius)

    /** Longueur du bras court, du pivot à la chape du contrepoids. */
    val shortArm: Float get() = beamLength / (1f + leverRatio)

    /** Longueur du bras long, celui qui porte la fronde. */
    val longArm: Float get() = beamLength - shortArm

    val beamMass: Float get() = beamLength * TrebuchetRules.BEAM_DENSITY

    val counterweightHalf: Float
        get() = TrebuchetRules.counterweightHalfSize(counterweightMass)

    /**
     * Angle d'armement : de combien le bras long plonge sous l'horizontale quand la
     * machine est bandée.
     *
     * Il ne se règle pas directement, il **se déduit** : on descend la pointe aussi
     * bas que le pied le permet sans qu'elle touche terre. Un pied haut donne donc
     * un armement plus plongeant, et c'est ce qui rend le choix du pied intéressant.
     */
    val cockAngleDeg: Float
        get() {
            val reach = (pivotHeight - TrebuchetRules.TIP_CLEARANCE) / longArm
            val deg = Math.toDegrees(asin(reach.coerceIn(0f, 1f).toDouble())).toFloat()
            return deg.coerceIn(TrebuchetRules.MIN_COCK_DEG, TrebuchetRules.MAX_COCK_DEG)
        }

    /** Longueur de la fronde, en mètres. */
    val slingLength: Float get() = slingRatio * longArm

    /**
     * Énergie stockée, en joules : le poids du contrepoids fois la hauteur dont il
     * descend entre la position bandée et le bras court à la verticale.
     *
     * Le calcul est purement géométrique — la chape n'y change rien, elle descend
     * d'autant que son point d'accrochage.
     */
    val storedEnergy: Float
        get() {
            val sink = shortArm * (1f + sin(Math.toRadians(cockAngleDeg.toDouble())).toFloat())
            return counterweightMass * TrebuchetRules.GRAVITY * sink
        }

    /**
     * Ramène la machine dans le domaine du possible. L'ordre compte : la chape se
     * mesure à la place qui reste sous le bras court, et cette place dépend de tout
     * le reste. Un réglage borné n'est pas un réglage refusé — le doigt continue de
     * glisser, la machine s'arrête simplement là où elle tiendrait debout.
     */
    fun clamp() {
        beamLength = beamLength.coerceIn(TrebuchetRules.BEAM_MIN, TrebuchetRules.BEAM_MAX)
        leverRatio = leverRatio.coerceIn(TrebuchetRules.LEVER_MIN, TrebuchetRules.LEVER_MAX)
        counterweightMass =
            counterweightMass.coerceIn(TrebuchetRules.CW_MIN, TrebuchetRules.CW_MAX)
        pinAngleDeg =
            pinAngleDeg.coerceIn(TrebuchetRules.PIN_MIN_DEG, TrebuchetRules.PIN_MAX_DEG)
        pivotHeight = pivotHeight.coerceIn(
            TrebuchetRules.POST_MIN_RATIO * beamLength,
            TrebuchetRules.POST_MAX_RATIO * beamLength
        )
        // Un contrepoids qui laboure le sol au bas de sa course n'est pas un
        // contrepoids, et c'est le genre de situation dont aucun solveur ne se remet.
        val room = pivotHeight - shortArm - counterweightHalf - TrebuchetRules.CW_CLEARANCE
        val longest = minOf(TrebuchetRules.HANG_MAX_RATIO * shortArm, room)
        hangLength = hangLength.coerceIn(
            TrebuchetRules.HANG_MIN, maxOf(TrebuchetRules.HANG_MIN, longest)
        )
        slingRatio = slingRatio.coerceIn(
            TrebuchetRules.SLING_MIN_RATIO, TrebuchetRules.SLING_MAX_RATIO
        )
        bombSticks = bombSticks.coerceIn(Projectile.MIN_STICKS, Projectile.MAX_STICKS)
        ballMass = ballMass.coerceIn(Projectile.MIN_BALL_MASS, Projectile.MAX_BALL_MASS)
    }

    fun copyFrom(o: MachineConfig) {
        beamLength = o.beamLength
        pivotHeight = o.pivotHeight
        leverRatio = o.leverRatio
        counterweightMass = o.counterweightMass
        hangLength = o.hangLength
        pinAngleDeg = o.pinAngleDeg
        slingRatio = o.slingRatio
        projectile = o.projectile
        bombSticks = o.bombSticks
        ballMass = o.ballMass
    }

    /**
     * L'empreinte des réglages : deux machines qui tirent pareil ont la même.
     *
     * Elle sert à l'aperçu du départ, que la vue ne recalcule que si elle a changé —
     * rejouer un début de tir coûte quelques centaines de pas de solveur, et personne ne
     * veut les payer soixante fois par seconde pour une machine qui n'a pas bougé.
     *
     * **Elle vit ici et pas dans la vue, et c'est le fruit d'une bêtise commise deux
     * fois.** La vue en tenait sa propre liste, recopiée à la main ; le jour où le poids
     * du boulet et la charge de la bombe sont devenus réglables, cette liste-là ne les a
     * pas appris, et l'aperçu montrait tranquillement la trajectoire du projectile
     * précédent. Le commentaire qui expliquait ce piège était juste au-dessus de la
     * ligne fautive. Une empreinte des réglages appartient aux réglages : ajouter un
     * champ ici sans l'ajouter là est encore possible, mais ça se voit, et un test le
     * dit.
     *
     * On hache **la masse lancée** et non les deux boutons qui la décident : c'est elle
     * qui change le vol, elle capte les deux d'un coup, et elle captera de la même façon
     * ce qui viendra après.
     */
    fun signature(): Int {
        var h = beamLength.toRawBits()
        h = h * 31 + pivotHeight.toRawBits()
        h = h * 31 + leverRatio.toRawBits()
        h = h * 31 + counterweightMass.toRawBits()
        h = h * 31 + hangLength.toRawBits()
        h = h * 31 + pinAngleDeg.toRawBits()
        h = h * 31 + slingRatio.toRawBits()
        // Le projectile ne change pas que la masse : il se sépare en vol, il explose, il
        // traverse. Son identité compte donc pour elle-même.
        h = h * 31 + projectile.ordinal
        h = h * 31 + shotMass.toRawBits()
        return h
    }
}

/**
 * Le jeu du trébuchet : on construit une machine de jet, on décroche la détente,
 * et la portée est la conséquence de la géométrie choisie. On ne vise jamais.
 *
 * Le déroulé d'un tir :
 *  - **BUILD** — la machine est bandée : le bras long plonge vers l'arrière,
 *    retenu par la détente ; le contrepoids pend en l'air du côté de la cible ;
 *    la fronde est étalée au sol sous la machine, avec le boulet dedans.
 *  - **FLIGHT** — la détente lâche. Le contrepoids tombe, le bras fouette, la
 *    fronde traîne le boulet sous le bâti puis le fait tourner par-dessus la
 *    pointe, et la boucle quitte le crochet quand la géométrie le veut.
 *  - **RESULT** — le boulet est retombé, on mesure.
 *
 * Aucune pièce de la machine n'entre en collision avec une autre : tout se joue par
 * liaisons — un pivot, une chape, une corde. C'est ce qui rend cette machine
 * incomparablement plus solide que la précédente, où un boulet coincé entre trois
 * planches finissait éjecté à des centaines de mètres par seconde.
 */
class TrebuchetGame(
    /**
     * Le monde et le site, **fournis de l'extérieur** quand on veut les partager.
     *
     * Les valeurs par défaut donnent à la machine son monde à elle : c'est ce dont un
     * test a besoin, et c'est ce qu'était le jeu jusqu'ici. L'activité, elle, passe le
     * monde et le site communs aux deux machines — c'est ce qui fait que changer de
     * machine ne change plus de village.
     */
    val world: PhysWorld = PhysWorld().apply {
        // Trois liaisons et un contact au sol : le monde est bien plus simple qu'avant,
        // et n'a plus besoin de vingt-deux passes pour tenir.
        iterations = 16
        // Une cible est faite de dizaines de pierres qui ne bougent pas. Ce n'est pas ce
        // drapeau qui les rend gratuites — [TargetField.trySleep] les **retire** du
        // monde — mais il endort aussi les gravats et la machine après le tir.
        //
        // Mesuré sur un site de vingt-cinq pierres : 0,032 ms par pas de physique, soit
        // **3,8 ms de processeur par seconde de jeu**, 0,4 % d'un cœur. C'est un confort,
        // pas une nécessité. Le chiffre est donné par seconde et non par image : la
        // physique avance par pas fixes de 1/120 s dans un accumulateur, donc elle fait le
        // même travail que l'affichage tourne à soixante ou à cent vingt.
        sleepEnabled = true
    },
    val site: ShotSite = ShotSite(world)
) {

    enum class Phase { BUILD, FLIGHT, RESULT }

    private companion object {
        // Les catégories vivent désormais dans [TrebuchetCategory] : les cibles en ont
        // besoin elles aussi, et deux jeux de bits qui doivent s'accorder sans se voir
        // finissent toujours par se contredire.
        const val CAT_GROUND = TrebuchetCategory.GROUND
        const val CAT_BEAM = TrebuchetCategory.BEAM
        const val CAT_WEIGHT = TrebuchetCategory.WEIGHT
        const val CAT_BALL = TrebuchetCategory.BALL

        /** Durée maximale d'un tir, en secondes : une parabole de 400 m dure 12 s. */
        const val SHOT_TIMEOUT = 30f

        /**
         * Grâce accordée à l'écroulement une fois le boulet posé, en secondes.
         *
         * Comptée depuis l'atterrissage et non depuis le largage : un tir en cloche
         * passe déjà de longues secondes en l'air, et faire partir ce délai du
         * largage aurait laissé un château presque plus de temps pour tomber qu'un
         * tir tendu au même endroit.
         */
        const val COLLAPSE_GRACE = 10f

        /**
         * Combien de temps la construction doit être **restée** immobile avant
         * qu'on la croie vraiment posée, en secondes.
         *
         * Une pierre qui vient de rompre lègue ses éclats à la vitesse qu'elle
         * avait — souvent proche de zéro, si c'est la charge qui l'a lentement
         * écrasée plutôt qu'un choc. Un seul relevé au repos, juste après la
         * rupture, croirait donc l'écroulement fini avant que la gravité n'ait eu
         * une image pour agir. Voir [TargetRules.ARM_CALM], la même idée à
         * l'armement.
         */
        const val SETTLE_CALM = TargetRules.ARM_CALM

        /**
         * Sécurité : passé ça, la fronde a dépassé l'axe du bras de l'autre côté
         * sans que le crochet ait lâché. Ça ne devrait pas arriver — mais un boulet
         * qui fait le tour du bras indéfiniment vaut mieux largué que gardé.
         */
        const val RUNAWAY_SLING = 0.6f

        /**
         * Écart au crochet, en radians, en deçà duquel l'image se découpe en
         * sous-pas pour chercher le largage.
         *
         * Un peu plus d'un demi-tour de sécurité : la fronde balaie jusqu'à quarante
         * radians par seconde à cet instant-là, soit deux tiers de radian dans une
         * image de soixantième. La fenêtre doit rester plus large que ce qu'une image
         * peut franchir, sinon on la survole sans la voir.
         */
        const val RELEASE_WINDOW = 1.0f

        /**
         * En combien de tranches l'image se découpe dans cette fenêtre.
         *
         * **C'est le réglage qui rend la machine lisible.** Le crochet lâche quand la
         * fronde croise son axe, et cette rencontre était cherchée une fois par image :
         * à quarante radians par seconde, la fronde pouvait dépasser le crochet de
         * trente-huit degrés avant qu'on ne s'en aperçoive, et l'écart dépendait de la
         * façon dont les images tombaient. Deux machines réglées à un cran l'une de
         * l'autre partaient donc dans des directions sans rapport — pas parce que la
         * mécanique est chaotique, mais parce qu'on la regardait trop rarement.
         *
         * Seize tranches ramènent l'erreur sous trois degrés, et ne coûtent rien : la
         * fenêtre ne couvre que les deux ou trois dernières images du fouet, sur les
         * cent cinquante que dure un armement.
         */
        const val RELEASE_SUBSTEPS = 16

        /** Écart donné au dernier éclat d'un paquet qui se défait, en m/s. */
        const val SHARD_SPREAD = 2f

        /**
         * Choc, en joules, à partir duquel une bombe se déclenche.
         *
         * Volontairement bas : une bombe qui effleure un toit de chaume doit partir
         * aussi. Cinquante joules, c'est un boulet de douze kilos à trois mètres par
         * seconde — bien en dessous de tout ce qui ressemble à un impact, et bien
         * au-dessus du frottement d'un corps qui roule.
         */
        const val BLAST_TRIGGER = 50f
    }

    val config = MachineConfig()

    /**
     * La cible : la construction à raser, et tout ce qui lui arrive.
     *
     * Elle vit dans le même monde que la machine, à des centaines de mètres de là. Les
     * catégories de collision font que les deux ne se rencontrent jamais autrement que
     * par le boulet.
     */
    /**
     * La partie : le site, le relief, le vent, les compteurs, le bouquet final.
     *
     * C'est [ShotSite], la **même** pièce que l'atelier d'engrenages — tout ce qu'une
     * partie possède sauf la machine. Les accesseurs qui suivent sont pour la vue et
     * l'activité, qui lisent `game.effects`, `game.targets`, `game.terrain`… depuis
     * toujours.
     */
    val effects: TrebuchetEffects get() = site.effects

    /**
     * Prévient qu'une bombe vient d'exploser, pour qui voudrait en faire un bruit —
     * la simulation reste du Kotlin pur, sans dépendance à l'audio Android.
     */
    var onExplosion: ((Float, Float, Float) -> Unit)? = null

    /**
     * Hauteur de ciel visible à l'écran, en mètres. La vue la pose à chaque image.
     *
     * C'est la seule chose que la simulation sait de l'affichage, et elle ne sert qu'à
     * une chose : dire au feu d'artifice jusqu'où monter. Un écran couché montre cent
     * mètres de ciel, le même écran debout en montre huit cents — des fusées réglées
     * pour l'un se tassent dans le bas de l'autre.
     */
    var skyTop: Float
        get() = site.skyTop
        set(value) { site.skyTop = value }

    val targets: TargetField get() = site.targets

    /**
     * Le vent du moment. Il vient du niveau, donc de sa graine.
     *
     * Le poser ici le pose partout : le monde s'en sert pour la traînée du boulet, les
     * effets pour emporter la fumée et les feux. Rien d'autre n'a besoin de le
     * connaître.
     */
    val wind: Wind get() = site.wind

    /** Le niveau en cours, ou nul en bac à sable (record de portée, sans cible). */
    val level: TargetLevel? get() = site.level

    var phase = Phase.BUILD
        private set

    /**
     * Le relief du niveau en cours : plat en bac à sable, dessiné par la graine sinon.
     *
     * Il n'appartient pas au monde physique — c'est une **description**, et c'est elle
     * qui fait autorité. Le monde en tire ses corps immobiles à chaque remontage de la
     * machine, la vue en tire sa ligne d'horizon, et le jeu lui demande où est le sol
     * quand il veut savoir si le boulet a touché. Un seul profil, trois lecteurs, et
     * aucun risque qu'ils racontent trois histoires différentes.
     */
    val terrain: Terrain get() = site.terrain

    /** Les corps immobiles qui portent le monde. Refaits à chaque remontage. */
    private val groundBodies = ArrayList<PhysBody>(12)

    /**
     * Le sol sous le pied de la machine. Le tablier est toujours plat, et à zéro : la
     * machine se règle sur un terrain qu'elle connaît, quel que soit le relief devant.
     */
    lateinit var ground: PhysBody
        private set
    lateinit var beam: PhysBody
        private set
    lateinit var ball: PhysBody
        private set
    lateinit var counterweight: PhysBody
        private set

    private lateinit var post: PhysBody
    private lateinit var pivot: RevoluteJoint
    private lateinit var hanger: RevoluteJoint

    /** La fronde : une corde entre la pointe du bras et le boulet. */
    lateinit var sling: DistanceJoint
        private set

    /**
     * Angle de la fronde dans le repère du bras, déroulé pour ne pas sauter à ±π,
     * en radians.
     *
     * Zéro veut dire « la fronde part droit devant la pointe, dans l'axe du bras ».
     * Bandée, la machine a sa fronde très en arrière — entre +90 et +145° — et le
     * fouet la rabat vers zéro. C'est cette descente que le crochet surveille.
     */
    var slingAngle = 0f
        private set

    /** Angle de la fronde au moment où la machine a été bandée. */
    var slingStartAngle = 0f
        private set

    private var prevRawSlingAngle = 0f

    /** Vitesse de balayage de la fronde, en radians par seconde. Sert à la guetter. */
    private var slingRate = 0f

    /** Vrai dès que la boucle a quitté le crochet. */
    var ballFree = false
        private set

    /**
     * Les **autres** morceaux d'un projectile qui s'est séparé en vol.
     *
     * Le premier éclat garde la place de [ball] : c'est lui qui porte la trace, la
     * caméra et la mesure de portée, et tout le reste du jeu continue de ne connaître
     * qu'un boulet. Ceux-ci sont les quatre autres, et la vue les dessine pareil.
     */
    val shards = ArrayList<PhysBody>(8)

    /** Vrai quand le paquet s'est déjà défait : on ne se sépare qu'une fois. */
    private var split = false

    /** Vrai quand la bombe a déjà soufflé : on n'explose qu'une fois. */
    private var blown = false

    /**
     * Vrai quand le projectile n'existe plus : la bombe qui vient de souffler.
     *
     * Elle **cessait** de nuire sans cesser d'exister — un caillou inerte qui roulait
     * jusqu'au bout du tir. C'était faux à regarder, et coûteux : la caméra suit le
     * projectile pendant le vol, elle restait donc accrochée à un débris sans force qui
     * dévalait le terrain, pendant que le château qu'on venait de souffler sortait de
     * l'écran par le côté.
     *
     * Une bombe qui a rendu sa charge disparaît donc. Ce qu'il fallait garder d'elle est
     * relevé au moment du souffle — la portée et l'heure de l'impact, voir
     * [explodeOnImpact] — et le corps est retiré du monde. La vue ne dessine plus rien,
     * et le tir se termine sur l'écroulement, qui est ce qu'on est venu voir.
     */
    var ballGone = false
        private set

    /**
     * [elapsed] au moment où le boulet a touché, ou -1 tant qu'il n'a pas encore
     * atterri. C'est de là, et pas du largage, que se compte [COLLAPSE_GRACE].
     */
    private var landedAt = -1f

    /**
     * Depuis combien de temps la construction est vue immobile, d'affilée. Remis à
     * zéro dès qu'une pierre bouge encore, pour que [COLLAPSE_GRACE] ne se laisse
     * pas tromper par un relevé au repos qui ne dure qu'une image.
     */
    private var settleCalm = 0f

    /**
     * Où commence le vol libre dans [trail], ou -1 tant que la boucle n'a pas quitté
     * le crochet. Ce qui précède est la course du boulet traîné au sol : joli, mais
     * ce n'est pas la trajectoire.
     */
    var launchTrailIndex = -1
        private set

    /**
     * Élévation réelle du tir, en degrés, **mesurée** à l'instant du largage.
     * Rien ne la décide à l'avance : elle sort de la vitesse du bras, de celle de
     * la fronde, et du moment où le crochet cesse de retenir la boucle.
     */
    var launchAngleDeg = 0f
        private set

    /**
     * La trace du tir en cours et la pile des tirs passés.
     *
     * C'est [ShotTrail], la **même** pièce que l'atelier d'engrenages : une trajectoire
     * n'appartient pas à la machine qui l'a lancée. Les deux jeux en avaient chacun leur
     * copie, et elles avaient déjà divergé — voir la mesure dans [ShotTrail].
     *
     * Les accesseurs ci-dessous ne sont là que pour la vue, qui lit `game.trail`,
     * `game.trailCount`, `game.ghosts` et `game.ghostStamp` depuis toujours.
     */
    val shotTrail: ShotTrail get() = site.trail

    /**
     * Trajectoire du tir en cours, en couples (x, y), à lire jusqu'à [trailCount].
     *
     * Un tableau de flottants et non une liste : une liste de `Float` emballe chaque
     * nombre dans un objet, et la trace d'un tir en compte plusieurs centaines, relus
     * à chaque image par la vue. C'est du travail pour le ramasse-miettes pendant le
     * vol, c'est-à-dire au pire moment.
     */
    val trail: FloatArray get() = shotTrail.points

    /** Nombre de flottants utiles dans [trail] (deux par point). */
    val trailCount: Int get() = shotTrail.count

    /**
     * Change à chaque fois que la pile des fantômes change, et jamais autrement.
     *
     * La vue peint les fantômes dans un calque qu'elle garde d'une image à l'autre ; il
     * lui faut donc un moyen de savoir si la pile a bougé. Comparer les tableaux
     * eux-mêmes voudrait dire relire dix fois trois mille nombres à chaque image, soit
     * exactement le travail qu'on cherche à éviter. Un compteur suffit.
     */
    val ghostStamp: Int get() = shotTrail.stamp

    /** Combien de tirs on garde. Le joueur le règle dans le menu des réglages. */
    var ghostLimit: Int
        get() = shotTrail.limit
        set(value) { shotTrail.limit = value }

    /**
     * Les trajectoires des tirs précédents, **du plus récent au plus ancien**.
     *
     * Un seul fantôme disait de combien on avait manqué ; une pile en dit bien plus. On
     * y lit la **série** : trois tirs qui se resserrent sur la cible, ou trois tirs qui
     * s'éloignent parce qu'on tourne le mauvais bouton. C'est la mémoire du joueur, et
     * elle vaut mieux que la sienne.
     *
     * Les traces sont gardées telles quelles, en coordonnées monde : elles ne coûtent
     * qu'un millier de flottants chacune, la vue les découpe déjà à ce qui tient à
     * l'écran, et rien n'oblige à les redessiner quand la caméra ne bouge pas.
     */
    val ghosts: List<FloatArray> get() = shotTrail.ghosts

    /** La trajectoire du dernier tir, ou nulle si personne n'a encore tiré. */
    val ghost: FloatArray? get() = shotTrail.ghosts.firstOrNull()

    // Mesures du tir
    var shotDistance = 0f
        private set

    /**
     * Vrai quand le dernier tir a été **arrêté à la main en plein vol**.
     *
     * Il ne s'agit pas d'un tir raté mais d'un tir qu'on n'a pas laissé finir : le
     * joueur a vu où ça allait et a repris la main. Ce qui a été fait est gardé — la
     * traînée jusqu'au point d'arrêt, les dégâts déjà infligés — mais il n'y a **pas de
     * portée** : le boulet n'a pas touché terre, et inscrire l'endroit où il volait
     * encore comme un record serait un mensonge.
     */
    var shotStopped = false
        private set
    var peakHeight = 0f
        private set
    var peakSpeed = 0f
        private set
    var launchSpeed = 0f
        private set

    /**
     * Vitesse de la pointe du bras à l'instant du largage, en m/s.
     *
     * C'est la référence qui donne son sens à [launchSpeed] : sans fronde, le boulet
     * sortirait à cette vitesse-là. Le rapport des deux est le gain du fouet : vers
     * deux pour un largage précoce, et jusqu'à quatre et plus quand la fronde fait
     * son tour complet par-dessus la pointe — le bras a alors fini de ralentir et
     * presque tout son élan est passé dans la corde.
     */
    var launchTipSpeed = 0f
        private set

    /**
     * Rendement du tir : l'énergie emportée par le boulet au largage, rapportée à
     * celle que le contrepoids a réellement dépensée jusque-là.
     *
     * C'est le chiffre qui dit au joueur si sa machine est bien accordée, et c'est
     * celui que les constructeurs de trébuchets cherchent à faire monter. Un tir
     * largué trop tôt, pendant la montée du fouet, reste vers 15 % : le gros de
     * l'énergie fait tourner la charpente. Un tir qui prend le fouet complet, la
     * fronde passant par-dessus la pointe, monte à 40–65 % — et c'est bien la
     * fourchette des vrais trébuchets, dont la fronde pendulaire est précisément
     * ce qui récupère l'énergie de la charpente au dernier moment.
     */
    var efficiency = 0f
        private set

    var shotCount: Int
        get() = site.shotCount
        private set(value) { site.shotCount = value }

    /**
     * Les tirs qui ont **touché**, c'est-à-dire fait perdre de la vie à au moins une
     * pierre. C'est le seul compteur dont on puisse faire une note.
     *
     * Compter les tirs *lancés* punirait le réglage, c'est-à-dire le jeu lui-même : au
     * trébuchet on ajuste sa machine sur plusieurs coups avant d'être sûr de toucher où
     * l'on vise, et ces coups-là doivent rester **gratuits**. Compter les tirs qui
     * portent, en revanche, mesure exactement ce qu'on veut mesurer : est-ce que le
     * joueur a visé juste une fois qu'il savait viser.
     */
    var hitCount: Int
        get() = site.hitCount
        private set(value) { site.hitCount = value }
    var bestDistance = 0f
        private set

    private var elapsed = 0f
    private var cwStartY = 0f
    private val probe = FloatArray(2)

    init {
        attach()
    }

    /**
     * Branche la machine sur le site : à partir de là, c'est elle qui se remonte quand le
     * site change, et c'est elle qui est dans le monde.
     *
     * Le trébuchet ne tamponne rien : il n'a pas d'étages de collision. Il **efface**
     * quand même le tampon de l'atelier, sans quoi une machine chassant l'autre laisserait
     * le sien en place.
     */
    fun attach() {
        site.remount = { build() }
        site.stampPieces = {}
        build()
    }

    /**
     * Retire la machine du monde en y laissant le site intact.
     *
     * C'est l'autre moitié du geste : le village, ses gravats et leurs dégâts restent
     * exactement là où ils sont pendant que l'autre machine prend la place.
     */
    fun detach() {
        world.removeOwned(this)
        groundBodies.clear()
    }

    // ── Géométrie de la machine ───────────────────────────────────────────────

    /** Position du pivot dans le monde. */
    val pivotX: Float get() = 0f
    val pivotY: Float get() = config.pivotHeight

    /**
     * Décalage du pivot dans le repère du bras.
     *
     * Le bras est une planche homogène, donc son centre de masse est son milieu
     * géométrique ; le pivot, lui, est très décentré. Le repère local du bras a son
     * axe X dirigé **vers la pointe**.
     */
    private val pivotLocalX: Float get() = -(config.longArm - config.shortArm) / 2f

    /** Position monde de la pointe du bras, dans [out]. */
    fun tipWorld(out: FloatArray) = beam.localToWorld(config.beamLength / 2f, 0f, out)

    /** Position monde de l'extrémité du bras court, dans [out]. */
    fun buttWorld(out: FloatArray) = beam.localToWorld(-config.beamLength / 2f, 0f, out)

    /**
     * Position monde de la pointe du crochet, dans [out]. Purement décorative, mais
     * elle montre au joueur ce que règle l'inclinaison du crochet.
     */
    fun pinWorld(out: FloatArray) {
        // On dessine le crochet **tel qu'il lâchera vraiment**, couchage compris :
        // sinon le joueur verrait un ergot qui ne correspond pas au tir qu'il obtient.
        val a = releaseAngle
        val len = 0.2f + 0.03f * config.longArm
        beam.localToWorld(config.beamLength / 2f + len * cos(a), len * sin(a), out)
    }

    /**
     * Met un corps dans le monde **au nom de cette machine**, pour pouvoir le lui retirer
     * plus tard sans toucher au reste. Voir [PhysBody.owner].
     */
    private fun own(b: PhysBody) {
        b.owner = this
        world.add(b)
    }

    /** (Re)monte entièrement la machine d'après [config], et la rebande. */
    fun build() {
        config.clamp()
        // **On ne vide plus le monde, on retire ce qui est à nous.** Vider marchait tant
        // qu'un monde ne portait qu'une machine et son site — au prix de remettre le site
        // dedans juste après, ce qui a déjà coûté un bug de pierres comptées deux fois.
        // Le site, lui, ne bouge plus : ni retiré, ni remis.
        world.removeOwned(this)
        shotTrail.begin()
        elapsed = 0f
        ballFree = false
        efficiency = 0f
        shotStopped = false
        phase = Phase.BUILD
        shards.clear()
        split = false
        blown = false
        ballGone = false
        landedAt = -1f
        settleCalm = 0f
        targets.forgetPiercers()
        targets.resetHitFlag()

        // **Plus de `reattach`.** Le site n'a jamais quitté le monde : le remontage ne
        // retire que les corps de la machine. Le remettre reviendrait à l'ajouter une
        // seconde fois — le piège que [PhysWorld.add] garde en mémoire.
        // Le boulet qui vient de la toucher, lui, n'a pas survécu à ce vidage : c'est
        // le moment de rendormir la cible si elle est prête, plutôt que d'attendre la
        // prochaine image de vol pour s'en apercevoir — cette image-là est celle du
        // bras qui prend de la vitesse, pas le bon moment pour découvrir que plus rien
        // n'approche.
        targets.trySleep()

        // **Le vent ne se repose plus ici.** Il fallait le refaire tant que `build`
        // commençait par vider le monde ; depuis qu'il ne retire que les corps de la
        // machine, le vent du monde n'est plus effacé. Le laisser aurait fait un
        // **deuxième** endroit où le vent du monde se décide — exactement le motif qui a
        // fait diverger les deux jeux. Un seul : [ShotSite.applyWind].

        // Le relief, en corps immobiles. Une dalle par palier, une boîte tournée par
        // talus, rien du tout pour une falaise — et sur un terrain plat, un seul corps,
        // exactement comme avant que le relief n'existe.
        //
        // C'est [TrebuchetGround] qui le pose, et c'est la **même** pièce qui sert à
        // l'atelier d'engrenages : le sol appartient au site, pas à la machine. Le
        // relief d'un niveau couvre déjà GROUND_LEFT..GROUND_RIGHT, donc le prolongement
        // ne change rien ici — c'est exactement le sol d'avant. C'est du côté de
        // l'atelier, dont le monde est vingt fois plus large, qu'il rembourse.
        groundBodies.clear()
        groundBodies += TrebuchetGround.lay(
            world, terrain, TrebuchetRules.GROUND_LEFT, TrebuchetRules.GROUND_RIGHT,
            friction = 0.55f
        ) { it.owner = this }
        ground = groundBodies.first()

        // Le pied : un corps immobile qui sert d'axe. Le bâti dessiné en dessous est
        // décoratif, c'est ce petit corps-ci qui tient le bras.
        post = PhysBody(0.08f, 0.08f, 0f).apply {
            x = pivotX
            y = pivotY
            lockPosition = true
            lockRotation = true
            category = CAT_BEAM
            collidesWith = 0
            refreshMass()
        }
        own(post)

        // Le bras : une simple planche. Plus aucune cuiller ni butée — tout ce que
        // cette géométrie tentait de faire, la fronde le fait mieux.
        //
        // Bandé, il pointe vers l'arrière **et** vers le bas : π le retourne côté
        // arrière, l'angle d'armement le fait plonger. Le contrepoids se retrouve
        // levé du côté de la cible, comme sur les gravures, et le tir passera
        // par-dessus la pointe.
        val cock = (PI + Math.toRadians(config.cockAngleDeg.toDouble())).toFloat()
        beam = PhysBody(
            config.beamLength / 2f, TrebuchetRules.BEAM_HALF_THICKNESS, config.beamMass
        ).apply {
            angle = cock
            category = CAT_BEAM
            // Le bras ne touche rien : son axe lui suffit, et un contact contre le
            // sol ou le contrepoids ne ferait que gêner.
            collidesWith = 0
            // La détente : bandée, la machine ne bouge pas d'un cheveu.
            lockPosition = true
            lockRotation = true
        }
        placeBeam()
        own(beam)

        pivot = RevoluteJoint.pin(beam, post, pivotX, pivotY)
        world.addJoint(pivot)

        // Le contrepoids, pendu à l'extrémité du bras court par une chape.
        //
        // C'est le point qui change tout par rapport à la machine précédente, où le
        // contrepoids était lâché de haut sur le bras. Un contrepoids boulonné décrit
        // un arc et finit sa course en poussant de travers ; pendu, il tombe presque
        // droit et rend presque toute sa hauteur.
        counterweight = PhysBody(
            config.counterweightHalf, config.counterweightHalf, config.counterweightMass
        ).apply {
            friction = 0.6f
            category = CAT_WEIGHT
            collidesWith = CAT_GROUND
        }
        placeCounterweight()
        own(counterweight)

        // La chape est ancrée **au-dessus** du contrepoids, à la longueur voulue :
        // une liaison pivot accepte n'importe quel point du repère du corps, même
        // hors de sa matière, et c'est exactement ce qu'est une élingue.
        hanger = RevoluteJoint(counterweight, beam).also {
            it.localAnchorAX = 0f
            it.localAnchorAY = config.hangLength
            it.localAnchorBX = -config.beamLength / 2f
            it.localAnchorBY = 0f
            world.addJoint(it)
        }

        ball = PhysBody.circle(config.shotRadius, config.shotMass).apply {
            // Un vrai trébuchet fait rouler son boulet dans une auge lisse : le
            // traîner sur la terre battue mangerait une partie de la course.
            friction = 0.2f
            restitution = 0.1f
            dragFactor = config.shotDrag
            // **Pas d'amortissement : la traînée suffit, et c'est la seule qui soit
            // une loi.** `dragFactor` est la vraie poussée de l'air, ½ρCdAv², qui
            // fait retomber un tir plus raide qu'il n'est monté. `linearDamping`, lui,
            // est une décroissance exponentielle appliquée à tout — un filet de
            // sécurité numérique, pas de la physique. Un projectile subissait les
            // deux : mesuré, la seconde lui coûtait **vingt-sept pour cent de portée**
            // en vol pur. Voir [PhysBody.linearDamping].
            linearDamping = 0f
            category = CAT_BALL
            // Le boulet ne connaît que le sol : la fronde le relie au bras, il n'a
            // aucune raison de venir cogner la machine.
            collidesWith = CAT_GROUND
        }
        own(ball)
        placeBall()

        tipWorld(probe)
        sling = DistanceJoint.between(
            beam, probe[0], probe[1],
            ball, ball.x, ball.y,
            rope = true
        ).also {
            it.length = config.slingLength
            world.addJoint(it)
        }

        slingStartAngle = measureSlingAngle()
        slingAngle = slingStartAngle
        prevRawSlingAngle = slingStartAngle

        // **La machine bandée s'endort.**
        //
        // Depuis que le monde continue de tourner entre les tirs ([idleStep]), une machine
        // armée y serait intégrée comme le reste : la poutre est bloquée par sa détente,
        // mais le contrepoids pend librement à sa chape et se mettrait à osciller tout
        // seul pendant qu'on regarde le village s'écrouler. Endormie, elle est exactement
        // là où on l'a posée, et [release] la réveille — il le faisait déjà, pour une
        // machine restée immobile assez longtemps pour que le moteur l'endorme.
        for (b in world.bodies) if (b.owner === this) b.sleep()
    }

    /**
     * Angle auquel le crochet lâchera réellement la boucle, en radians (positif).
     *
     * C'est l'inclinaison choisie, mais couchée si besoin : un crochet plus redressé
     * que la fronde bandée ne retiendrait rien du tout.
     */
    val releaseAngle: Float
        get() = minOf(
            Math.toRadians(config.pinAngleDeg.toDouble()).toFloat(),
            slingStartAngle - Math.toRadians(TrebuchetRules.MIN_PIN_SWEEP_DEG.toDouble()).toFloat()
        )

    /** Repose le bras à son angle d'armement, pivot au bon endroit. */
    private fun placeBeam() {
        val c = cos(beam.angle)
        val s = sin(beam.angle)
        beam.x = pivotX - pivotLocalX * c
        beam.y = pivotY - pivotLocalX * s
        beam.vx = 0f; beam.vy = 0f; beam.omega = 0f
        beam.refreshMass()
        world.forgetContacts(beam)
    }

    /** Suspend le contrepoids sous l'extrémité du bras court, chape à la verticale. */
    private fun placeCounterweight() {
        buttWorld(probe)
        counterweight.x = probe[0]
        counterweight.y = probe[1] - config.hangLength
        counterweight.angle = 0f
        counterweight.vx = 0f; counterweight.vy = 0f; counterweight.omega = 0f
        world.forgetContacts(counterweight)
    }

    /**
     * Pose le boulet au sol, sous la machine, à l'endroit exact où la fronde est
     * juste tendue — comme dans l'auge des vraies machines, qui court de la pointe
     * bandée vers le bâti. La fronde étant une corde, un peu de mou ne gênerait
     * pas, mais un tir commence mieux sans à-coup.
     */
    private fun placeBall() {
        tipWorld(probe)
        val dy = probe[1] - config.shotRadius
        val l = config.slingLength
        val dx = sqrt(maxOf(l * l - dy * dy, 0.01f))
        ball.x = probe[0] + dx
        ball.y = config.shotRadius
        ball.angle = 0f
        ball.vx = 0f; ball.vy = 0f; ball.omega = 0f
        ball.collidesWith = CAT_GROUND
        world.forgetContacts(ball)
    }

    /**
     * Charge le niveau de la graine donnée, et rebande la machine devant.
     *
     * Tout le niveau tient dans cet entier : la sorte de site, sa taille, ses matériaux
     * et sa distance. Le rejouer, c'est rappeler la même graine.
     */
    fun loadLevel(seed: Long) {
        applyLevel(TargetGenerator.generate(seed))
    }

    /**
     * Pose un niveau **déjà fabriqué**, et rebande la machine devant.
     *
     * La fabrication et la pose sont séparées parce qu'elles n'ont ni le même coût ni le
     * même fil. [TargetGenerator.generate] bâtit le site et le tasse dans un monde
     * jetable — trois millisecondes en moyenne, seize au pire, davantage sur un
     * téléphone — sans toucher à quoi que ce soit du jeu en cours ; c'est du travail de
     * fond. La pose, elle, remplace le monde sous les pieds du joueur : elle est
     * immédiate, et elle doit se faire d'un bloc pendant que la simulation est à l'arrêt.
     */
    fun applyLevel(lvl: TargetLevel) = site.load(lvl)

    /**
     * Repart en bac à sable : plus de cible, terrain plat, et pas de vent — on ne mesure
     * que la portée, et une mesure ne se fait pas dans le courant d'air.
     */
    fun clearLevel() = site.load(null)

    /** Pose le vent sur le monde et sur les effets. Voir [ShotSite.applyWind]. */
    fun applyWind(w: Wind) = site.applyWind(w)


    /**
     * Rejoue la pose avec les réglages actuels, en gardant l'historique des tirs.
     *
     * C'est [build] qui remonte la machine, et il ne touche pas aux fantômes : régler sa
     * poutre entre deux tirs ne doit pas effacer ce qu'on vient d'apprendre.
     */
    fun rebuild() {
        build()
    }

    // ── Réglages ─────────────────────────────────────────────────────────────

    /** Longueur de la poutre, en mètres. */
    fun setBeamLength(metres: Float) = editSetting { config.beamLength = metres }

    /** Hauteur de l'axe au-dessus du sol, en mètres : elle décide de l'armement. */
    fun setPivotHeight(metres: Float) = editSetting { config.pivotHeight = metres }

    /** Position de l'axe dans la poutre : la force contre la vitesse. */
    fun setLeverRatio(ratio: Float) = editSetting { config.leverRatio = ratio }

    /** Masse du contrepoids, en kilogrammes. */
    fun setCounterweightMass(kg: Float) = editSetting { config.counterweightMass = kg }

    /** Longueur de la chape : c'est le réglage de synchronisation du contrepoids. */
    fun setHangLength(metres: Float) = editSetting { config.hangLength = metres }

    /** Inclinaison du crochet de largage : le réglage de visée de la machine. */
    fun setPinAngle(deg: Float) = editSetting { config.pinAngleDeg = deg }

    /** Repart d'une machine neuve, fantôme compris. */
    fun reset() = loadConfig(MachineConfig())

    /**
     * Prend une machine toute faite : celle d'origine, ou une que le joueur a mise de
     * côté.
     *
     * **Les fantômes restent**, et c'est le joueur qui a tranché. On les effaçait au
     * motif qu'une trace ne dit plus rien dès que la machine n'est plus celle qui l'a
     * faite. C'est vrai sur le papier et faux en jouant : ce qu'on regarde, ce n'est
     * pas « quel réglage a fait ce trait », c'est « où tombaient mes boulets tout à
     * l'heure ». Un joueur qui essaie trois machines sur la même cible veut justement
     * voir les trois nappes ensemble. Ils ne s'en vont donc que sur commande, par
     * [clearGhosts].
     */
    fun loadConfig(c: MachineConfig) {
        config.copyFrom(c)
        build()
    }

    /** Efface la mémoire des tirs — la trace vivante comprise. Voir [ShotTrail.clearGhosts]. */
    fun clearGhosts() = shotTrail.clearGhosts()

    /** Règle la longueur de la fronde, en mètres. */
    fun setSlingLength(metres: Float) = editSetting {
        config.slingRatio = metres / config.longArm
    }

    /**
     * Change ce qu'il y a dans la fronde, et rebande la machine dessus.
     *
     * La machine est **remontée**, pas seulement rechargée : le boulet est un corps du
     * moteur, avec sa masse et son rayon, et on ne change pas la masse d'un corps déjà
     * pris dans une liaison sans que la fronde s'en aperçoive.
     */
    fun setProjectile(kind: Projectile) = editSetting { config.projectile = kind }

    /** Combien de bâtons de poudre on met dans la bombe. */
    fun setBombSticks(n: Int) = editSetting {
        config.bombSticks = n.coerceIn(Projectile.MIN_STICKS, Projectile.MAX_STICKS)
    }

    /** Le poids du boulet, en kilogrammes. */
    fun setBallMass(kg: Float) = editSetting {
        config.ballMass = kg.coerceIn(Projectile.MIN_BALL_MASS, Projectile.MAX_BALL_MASS)
    }

    /**
     * Pose une valeur et rebande la machine dessus. Toucher un réglage pendant un
     * tir le reprend donc à zéro : c'est ce que le joueur veut dire en attrapant
     * une pièce, et le fantôme du tir précédent, lui, reste affiché.
     */
    private inline fun editSetting(apply: () -> Unit) {
        apply()
        config.clamp()
        rebuild()
    }

    // ── Le tir ───────────────────────────────────────────────────────────────

    /** Décroche la détente : à partir de là, tout n'est plus que de la physique. */
    fun release() {
        if (phase != Phase.BUILD) return
        // La machine vient peut-être de passer plusieurs secondes à l'arrêt : on la
        // réveille avant de la lâcher, sinon la détente ne déclencherait rien.
        world.wakeAll()
        // Le tir s'ouvre ici : le champ relève l'état de chaque pierre, qui décidera de
        // ce que le boulet a le droit de traverser.
        targets.beginShot()
        // La trace s'ouvre **vide** : celle du trébuchet commence par le balancement
        // du bras, il n'y a donc pas de point de départ à poser comme au canon.
        shotTrail.begin()
        elapsed = 0f
        ballFree = false
        shotDistance = 0f
        peakHeight = ball.y
        peakSpeed = 0f
        launchSpeed = 0f
        launchTipSpeed = 0f
        launchAngleDeg = 0f
        efficiency = 0f
        launchTrailIndex = -1
        cwStartY = counterweight.y
        split = false
        blown = false
        ballGone = false
        landedAt = -1f
        settleCalm = 0f
        targets.forgetPiercers()
        targets.resetHitFlag()

        // Le bras n'est plus tenu que par son axe.
        beam.lockPosition = false
        beam.lockRotation = false
        beam.refreshMass()
        pivot.reset()
        hanger.reset()
        sling.reset()

        slingStartAngle = measureSlingAngle()
        slingAngle = slingStartAngle
        prevRawSlingAngle = slingStartAngle
        slingRate = 0f
        world.clearImpacts()
        phase = Phase.FLIGHT
    }

    /**
     * Angle brut de la fronde dans le repère du bras, dans (-π, π].
     *
     * Zéro veut dire « la fronde part droit devant la pointe, dans l'axe du bras » ;
     * π veut dire « elle revient vers le pivot ». C'est cette valeur, déroulée, que
     * le crochet surveille.
     */
    private fun measureSlingAngle(): Float {
        tipWorld(probe)
        val dx = ball.x - probe[0]
        val dy = ball.y - probe[1]
        val c = cos(beam.angle)
        val s = sin(beam.angle)
        return atan2(-dx * s + dy * c, dx * c + dy * s)
    }

    fun step(dt: Float) {
        if (phase != Phase.FLIGHT) {
            idleStep(dt)
            return
        }

        // L'élan d'avant le choc, gardé pour la traversée : une fois le pas simulé, il
        // est perdu, et c'est justement lui qu'on veut rendre au projectile qui casse.
        aimPierce()

        stepUntilRelease(dt)
        // Le choc du projectile se lit **avant** la cible : c'est elle qui remet les
        // compteurs d'impact à zéro, une fois les dégâts appliqués.
        val hit = ballFree && ball.impactAccum > BLAST_TRIGGER
        targets.update(dt)
        elapsed += dt

        // Le site vient d'encaisser (`targets.update` ci-dessus) : la traversée sait
        // donc ce que le boulet a cassé, et peut lui rendre le reste de son élan.
        pierce.apply(world, targets)
        if (!blown) explodeOnImpact(hit)
        if (!split) splitInFlight()

        val speed = hypot(ball.vx, ball.vy)
        if (speed > peakSpeed) peakSpeed = speed
        if (ball.y > peakHeight) peakHeight = ball.y
        if (!ballFree) launchSpeed = speed

        // Une bombe partie en fumée n'écrit plus : sans ça, la trace empilerait des
        // centaines de points au même endroit pendant que la construction s'écroule, et
        // le fantôme du tir garderait ce pâté-là.
        shotTrail.sample(dt, ball.x, ball.y, record = !ballGone)

        // Le tir se mesure au **point d'impact**, comme une portée d'artillerie : ce
        // qui compte est là où le boulet frappe, pas où il finit de rouler. Un boulet
        // qui touche à quarante mètres par seconde rebondit et roule encore cent
        // mètres, ce qui n'apprend rien au joueur sur sa machine.
        //
        // On n'attend pas non plus que la machine s'immobilise : le bras balance au
        // bout de sa chape pendant de longues secondes après le tir.
        // « Touché » se lit par rapport au sol **de cet endroit-là**. Avec un relief,
        // un boulet qui se pose sur un plateau à quinze mètres n'atteindra jamais
        // l'altitude zéro, et le tir ne se terminerait pas.
        // Une bombe qui a soufflé a fini son voyage, où qu'elle l'ait fini : elle a pu
        // partir contre un mur à dix mètres du sol, et attendre qu'elle « touche terre »
        // serait attendre un corps qui n'existe plus. Son atterrissage est relevé au
        // moment du souffle, voir [explodeOnImpact].
        val landed = ballFree &&
            (ballGone ||
                (ball.y <= terrain.heightAt(ball.x) + ball.boundingRadius + 0.03f &&
                    ball.vy <= 0f))
        if (landed && shotDistance == 0f) shotDistance = ball.x - TrebuchetRules.FIRING_LINE
        if (landed && landedAt < 0f) landedAt = elapsed

        // Le tir est fini quand le boulet a touché **et** que la cible a fini de
        // s'écrouler : un château met plusieurs secondes à s'effondrer, et rendre la
        // main avant reviendrait à cacher au joueur le résultat de son coup.
        //
        // « Fini » se lit sur une **immobilité tenue**, pas sur un relevé isolé : une
        // pierre qui vient de rompre part parfois à vitesse quasi nulle, portée par la
        // charge plutôt que par un choc, et le tour de veille suivant la verrait « au
        // repos » une image entière avant que la gravité n'ait eu le temps d'agir. Sans
        // ce délai tenu, ce relevé-là suffisait à clore le tir en pleine chute — la
        // construction se figeait à mi-écroulement, debout sur un boulet qui, lui, avait
        // bel et bien fini sa course.
        if (level != null) {
            settleCalm = if (landed && targets.piecesAtRest()) settleCalm + dt else 0f
        }
        val settled = landed && (level == null || settleCalm >= SETTLE_CALM)
        // Le délai avant abandon se compte depuis l'atterrissage, pas depuis le
        // largage : un tir en cloche passe déjà de longues secondes en l'air, et le
        // faire courir depuis le départ aurait laissé un château presque plus de
        // temps pour tomber qu'un tir tendu posé au même endroit.
        val stuck = landed && landedAt >= 0f && elapsed - landedAt > COLLAPSE_GRACE
        if (settled || stuck || elapsed > SHOT_TIMEOUT ||
            ball.x > TrebuchetRules.GROUND_RIGHT - 4f
        ) {
            finishShot()
        }
    }

    // ── Ce que le projectile fait de sa vie ──────────────────────────────────

    /**
     * La traversée : un projectile ne paie que ce qu'il a détruit.
     *
     * C'est [ShotPierce], la **même** pièce que l'atelier d'engrenages. Le calcul y est
     * décrit en entier ; ici il ne reste qu'à dire **qui** a le droit de traverser cette
     * image-ci, ce qui est la seule chose que la machine sache et pas la pièce partagée.
     */
    private val pierce = ShotPierce()

    /**
     * Qui peut traverser cette image : le boulet s'il est libre, et ses éclats s'il
     * s'est fendu en vol.
     *
     * La liste se remplit **avant** le pas, et c'est elle que la traversée relit après :
     * un projectile qui n'y était pas au moment où on a relevé son élan n'a pas d'élan à
     * qui rendre quoi que ce soit. Une bombe partie en fumée n'y est plus non plus —
     * [ball] désigne alors un corps retiré du monde.
     */
    private fun aimPierce() {
        pierce.bodies.clear()
        if (ballFree && !ballGone) {
            pierce.bodies.add(ball)
            pierce.bodies.addAll(shards)
        }
        pierce.remember()
    }

    /**
     * La bombe : elle rend tout d'un coup, là où elle touche.
     *
     * Le déclencheur est l'énergie que le moteur vient de dissiper dans le corps —
     * autrement dit un vrai choc, et pas un frôlement. Le seuil est bas : une bombe
     * qui touche du chaume doit exploser aussi.
     *
     * **Et elle disparaît.** Elle s'éteignait, autrefois : le corps restait dans le monde,
     * inerte, incapable de toucher autre chose que le sol, et il finissait sa course en
     * roulant. La raison invoquée était la mesure — le tir se mesure au point d'impact, et
     * un projectile évaporé ne dit plus où il a frappé. Elle ne tenait pas : le point
     * d'impact, c'est **ici**, à l'instant du souffle, et rien n'empêche de le relever
     * avant de retirer le corps. Ce qui restait était une bombe qui avait déjà tout donné,
     * qui n'avait aucune raison d'exister, et qui gardait la caméra accrochée à elle
     * pendant que le château soufflé s'écroulait hors du cadre.
     */
    private fun explodeOnImpact(hit: Boolean) {
        if (!ballFree) return
        val kind = config.projectile
        if (!kind.explosive) return
        // Une bombe qui n'a rien cogné d'assez dur part quand même **en touchant le
        // sol** — et le sol, depuis qu'il y a du relief, n'est pas à zéro. Comparée à
        // une altitude absolue, cette condition-ci ne se réalisait jamais sur un site
        // perché : la bombe roulait sur son plateau jusqu'à la fin du tir sans exploser.
        // C'est la même faute que celle de la détection d'atterrissage, au même endroit
        // du raisonnement.
        if (!hit && ball.y > terrain.heightAt(ball.x) + config.shotRadius + 0.03f) return
        blown = true
        val r = kind.blastRadiusFor(config.bombSticks)
        targets.blast(ball.x, ball.y, kind.blastEnergyFor(config.bombSticks), r)
        effects.explosion(ball.x, ball.y, r)
        onExplosion?.invoke(ball.x, ball.y, r)

        // Le tir se relève **avant** que la bombe ne s'en aille : c'est le dernier
        // instant où elle sait où elle a frappé, et c'est cela, la portée.
        if (shotDistance == 0f) shotDistance = ball.x - TrebuchetRules.FIRING_LINE
        if (landedAt < 0f) landedAt = elapsed

        // Le corps quitte le monde. On l'arrête aussi net qu'on le retire : la caméra
        // vise devant le projectile, d'autant plus loin qu'il va vite, et une vitesse
        // restée dans un corps que plus personne n'intègre décalerait le cadrage de
        // plusieurs mètres pour toujours.
        ball.vx = 0f
        ball.vy = 0f
        ball.omega = 0f
        world.remove(ball)
        // Le champ de cibles garde une liste de projectiles à qui il crédite ce qu'ils
        // cassent ; il saute ceux qui ne sont plus du monde, à condition qu'on le dise.
        ball.inWorld = false
        ballGone = true
    }

    /**
     * Le paquet se défait, une fois passé le sommet de la cloche.
     *
     * Le repère est la **hauteur**, pas le temps : une machine bien accordée envoie son
     * projectile à quarante mètres, une machine molle à dix, et un délai en secondes
     * ferait s'ouvrir le paquet au ras du sol dans un cas et bien trop haut dans
     * l'autre. À trente pour cent de la descente, les éclats ont la place de s'écarter
     * sans avoir le temps de se disperser.
     *
     * Ils partent en éventail **autour de la trajectoire**, pas au hasard : l'écart est
     * perpendiculaire à la vitesse, symétrique, et proportionnel à rien du tout — deux
     * mètres par seconde suffisent à couvrir un front de maisons depuis vingt mètres de
     * haut.
     */
    private fun splitInFlight() {
        if (!ballFree || blown) return
        val kind = config.projectile
        if (kind.shards <= 1) return
        if (ball.vy > 0f) return
        val floor = ball.y - config.shotRadius
        if (floor > peakHeight * (1f - kind.splitFraction)) return

        split = true
        val v = hypot(ball.vx, ball.vy)
        if (v < 1f) return
        // Perpendiculaire à la trajectoire, normée.
        val px = -ball.vy / v
        val py = ball.vx / v
        val m = kind.shardMass()
        val r = config.shotRadius / sqrt(kind.shards.toFloat())

        val x0 = ball.x
        val y0 = ball.y
        val vx0 = ball.vx
        val vy0 = ball.vy
        world.remove(ball)
        targets.forgetPiercers()
        shards.clear()

        for (k in 0 until kind.shards) {
            // Étalés symétriquement : -2, -1, 0, +1, +2 pour cinq éclats.
            val rank = k - (kind.shards - 1) / 2f
            val spread = SHARD_SPREAD * rank
            val b = PhysBody.circle(r, m).apply {
                // On les écarte aussi dans l'espace, sinon ils naissent les uns dans
                // les autres et se repoussent violemment à la première image.
                x = x0 + px * rank * 3f * r
                y = y0 + py * rank * 3f * r
                vx = vx0 + px * spread
                vy = vy0 + py * spread
                friction = 0.2f
                restitution = 0.1f
                dragFactor = kind.dragFor(r)
                // Les éclats volent sous la même loi que le boulet dont ils sortent.
                linearDamping = 0f
                category = TrebuchetCategory.BALL
                collidesWith = TrebuchetCategory.projectileMask()
            }
            own(b)
            targets.trackPiercer(b)
            if (k == 0) ball = b else shards.add(b)
        }
    }

    /**
     * Simule l'image, en la découpant quand le largage approche.
     *
     * Tout le reste du vol se joue à l'image entière : le moteur découpe déjà de
     * lui-même ce qu'il faut pour qu'un boulet rapide ne traverse rien. Ce qu'il ne
     * peut pas deviner, c'est qu'on **guette un instant** — celui où la fronde croise
     * le crochet — et qu'un instant guetté une fois par image est un instant manqué de
     * peu, toujours, et jamais de la même quantité.
     */
    private fun stepUntilRelease(dt: Float) {
        if (ballFree) {
            world.stepFrame(dt)
            return
        }
        // La fenêtre tient compte de la vitesse de balayage mesurée à l'image
        // précédente : une fronde très rapide mérite qu'on la guette de plus loin.
        val marge = slingAngle - releaseAngle
        val fenetre = maxOf(RELEASE_WINDOW, 2f * abs(slingRate) * dt)
        if (marge > fenetre) {
            world.stepFrame(dt)
            updateSlingAngle(dt)
            checkRelease()
            return
        }
        val h = dt / RELEASE_SUBSTEPS
        for (i in 0 until RELEASE_SUBSTEPS) {
            world.stepFrame(h)
            updateSlingAngle(h)
            checkRelease()
            if (ballFree) {
                // Le reste de l'image se joue normalement : le boulet est parti, il n'y
                // a plus rien à guetter.
                val reste = dt - (i + 1) * h
                if (reste > 1e-5f) world.stepFrame(reste)
                return
            }
        }
    }

    /**
     * Le monde continue de tourner **entre les tirs**.
     *
     * Il s'arrêtait net : `step` rendait la main dès qu'on n'était plus en vol, et une
     * tour prise en pleine chute se figeait en l'air jusqu'au tir suivant. C'était le seul
     * endroit du jeu où la gravité s'arrêtait, et ça se voyait — l'atelier, lui, n'a jamais
     * eu cette porte et son village finit de tomber.
     *
     * **Et ça ne coûte rien.** Mesuré sur un site de vingt-cinq pierres, **par seconde de
     * jeu** — la bonne unité, puisque la physique avance par pas fixes de 1/120 s dans un
     * accumulateur et fait donc le même travail que l'affichage tourne à soixante ou à
     * cent vingt :
     *
     * ```
     * vol + effondrement, boulet rapide   1,4 ms de processeur par seconde
     * monde qui tourne après le tir       2,3 ms par seconde   (0,23 % d'un cœur)
     * ```
     *
     * Ce qui coûte cher dans ce jeu n'a jamais été le château, c'est le **sous-pas** qu'un
     * boulet rapide impose — sept millisecondes pour une seule image mesurées autrefois en
     * vol, parce que chaque pierre est résolue à chacun des trente-deux sous-pas. Boulet
     * posé, plus de sous-pas, et le château redevient gratuit.
     *
     * La machine, elle, ne bouge pas : elle est endormie à l'armement ([build]) et
     * [release] la réveille. Sans ça, un contrepoids bandé dériverait doucement pendant
     * qu'on regarde le village s'écrouler.
     */
    private fun idleStep(dt: Float) {
        world.stepFrame(dt)
        // Le site s'arme, encaisse et se rendort tout seul : sans cet appel, un château de
        // quatre-vingts pierres resterait éveillé pour rien.
        targets.update(dt)
        // Un site rasé mérite son bouquet même si le tir s'est terminé avant que la
        // dernière pierre ne tombe.
        celebrate()
    }

    /**
     * Fait vivre les effets, et **seulement** eux.
     *
     * À appeler à chaque image, quelle que soit la phase : un feu d'artifice se tire
     * après le tir, quand la machine est retombée au repos et que [step] ne fait plus
     * rien. Les lier au pas de simulation aurait figé le bouquet en plein ciel.
     */
    fun stepEffects(dt: Float) {
        effects.update(dt)
    }

    /** Le crochet lâche-t-il, à cet instant précis ? */
    private fun checkRelease() {
        if (slingAngle <= releaseAngle || slingAngle <= -RUNAWAY_SLING) letGo()
    }

    /**
     * Suit l'angle de la fronde en le déroulant, pour qu'il ne saute pas à ±π, et
     * mesure au passage sa vitesse de balayage.
     */
    private fun updateSlingAngle(dt: Float) {
        val raw = measureSlingAngle()
        var d = raw - prevRawSlingAngle
        val twoPi = 2f * PI.toFloat()
        while (d > PI) d -= twoPi
        while (d < -PI) d += twoPi
        prevRawSlingAngle = raw
        slingAngle += d
        if (dt > 1e-6f) slingRate = d / dt
    }

    /** La boucle quitte le crochet : le boulet n'appartient plus à la machine. */
    private fun letGo() {
        ballFree = true
        // Tout ce qui est déjà dans la trace est la course au sol : le vol commence ici.
        launchTrailIndex = trailCount
        sling.enabled = false
        // Le boulet n'appartient plus à la machine : c'est **maintenant** qu'il devient
        // capable de toucher une cible. Le rendre solide plus tôt reviendrait à le
        // laisser cogner sa propre charpente pendant qu'il est traîné sous le bâti.
        ball.collidesWith = TrebuchetCategory.projectileMask()
        world.forgetContacts(ball)
        // À partir d'ici, la cible saura ce que ce corps-là casse lui-même.
        targets.trackPiercer(ball)
        launchSpeed = hypot(ball.vx, ball.vy)
        launchAngleDeg = Math.toDegrees(atan2(ball.vy.toDouble(), ball.vx.toDouble())).toFloat()
        // La pointe du bras décrit un cercle autour du pivot : sa vitesse est le
        // produit de la rotation du bras par la longueur du bras long.
        launchTipSpeed = abs(beam.omega) * config.longArm

        // Rendement : ce que le boulet emporte, sur ce que le contrepoids a dépensé
        // pour le lui donner.
        val spent = config.counterweightMass * TrebuchetRules.GRAVITY * (cwStartY - counterweight.y)
        efficiency = if (spent > 1f) {
            0.5f * ball.mass * launchSpeed * launchSpeed / spent
        } else {
            0f
        }
    }

    /**
     * Arrête le tir en cours sur-le-champ, et garde ce qu'il a fait.
     *
     * C'est le geste du joueur qui a compris avant la fin : le boulet part trop court,
     * la construction est déjà par terre, ou il a simplement vu ce qu'il voulait voir.
     * Rien n'est effacé — la traînée devient un fantôme arrêté au point où le boulet en
     * était, et les dégâts restent puisqu'ils appartiennent aux pierres, pas au tir.
     *
     * Un boulet qui n'a pas encore touché terre ne laisse en revanche aucune portée :
     * voir [shotStopped].
     */
    fun stopShot() {
        if (phase != Phase.FLIGHT) return
        shotStopped = landedAt < 0f
        finishShot()
    }

    private fun finishShot() {
        phase = Phase.RESULT
        celebrate()
        // La portée est celle du **point d'impact**, mesurée à l'atterrissage : après,
        // le boulet roule, et où il finit sa course n'apprend rien.
        if (!shotStopped) {
            if (shotDistance == 0f) shotDistance = ball.x - TrebuchetRules.FIRING_LINE
            if (shotDistance > bestDistance) bestDistance = shotDistance
        }
        shotCount++
        // « Touché » se lit sur la cible elle-même : le drapeau se lève dès qu'un choc a
        // entamé une pierre, et il est remis à zéro à l'ouverture du tir.
        if (targets.tookDamage) hitCount++
        // Un dernier point **là où le boulet est vraiment**, puis la trace devient le
        // fantôme le plus récent. Sans ce point, un tir coupé en plein vol laissait un
        // trait qui s'arrête en l'air : mesuré à 0,25 m à trente mètres par seconde,
        // et ça monte avec la vitesse. L'atelier le posait, pas le trébuchet.
        shotTrail.archive(ball.x, ball.y)
    }

    /**
     * Tire le feu d'artifice de la victoire. Le bouquet part du bord droit de la machine
     * jusqu'au bout des décombres : voir [ShotSite.celebrate].
     */
    private fun celebrate() = site.celebrate(TrebuchetRules.FIRING_LINE)


    // ── Simulation sans affichage ────────────────────────────────────────────

    /**
     * Joue un tir complet le plus vite possible et rend la portée obtenue.
     *
     * Sert aux tests, et servira surtout au générateur de niveaux : on tire une
     * machine au hasard dans le catalogue, on regarde où elle envoie le boulet, et on
     * pose la cible là. Le niveau est faisable par construction.
     */
    fun simulateShot(dt: Float = 1f / 120f): Float {
        release()
        var guard = 0
        val maxSteps = (SHOT_TIMEOUT / dt).toInt() + 10
        while (phase != Phase.RESULT && guard < maxSteps) {
            step(dt)
            guard++
        }
        if (phase != Phase.RESULT) finishShot()
        return shotDistance
    }

    /**
     * Joue le **départ** d'un tir et rend la trace du boulet sur ses [metres]
     * premiers mètres de vol libre.
     *
     * C'est la prévisualisation : elle ne dit pas où le boulet tombe — ça, il faut
     * tirer pour le savoir — mais elle dit par où il part, ce qui est justement ce
     * que le joueur ne peut pas deviner en regardant sa machine à l'arrêt.
     *
     * On s'arrête vite : quatre secondes de simulation suffisent pour le fouet et
     * cinquante mètres de vol. Une machine qui ne largue pas dans ce temps-là ne
     * largue pas du tout, et rend une trace vide plutôt qu'un mensonge.
     */
    fun simulateStart(metres: Float, dt: Float = 1f / 120f): FloatArray {
        release()
        var guard = 0
        val maxSteps = (4f / dt).toInt()
        while (phase == Phase.FLIGHT && guard < maxSteps &&
            ball.x - TrebuchetRules.FIRING_LINE < metres
        ) {
            step(dt)
            guard++
        }
        return startTrace()
    }

    /**
     * La trace du boulet depuis le largage, vide tant que la boucle n'a pas quitté le
     * crochet. Ce qui précède est la course du boulet traîné au sol sous la machine :
     * c'est joli, mais ce n'est pas la trajectoire, et le montrer laisserait croire
     * que le tir part de là.
     */
    fun startTrace(): FloatArray {
        val from = launchTrailIndex
        if (from < 0 || trailCount - from < 6) return FloatArray(0)
        return shotTrail.points.copyOfRange(from, trailCount)
    }
}
