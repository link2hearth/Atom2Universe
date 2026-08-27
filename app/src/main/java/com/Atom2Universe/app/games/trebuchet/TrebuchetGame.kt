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
 * Y vers le haut, origine au sol sous le pivot).
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
 * tenait lieu de largage, jetait le reste. Le même banc donne aujourd'hui 48 m/s et
 * 143 m pour la plus petite machine, 66 m/s et 214 m pour la plus grande.
 *
 * Le catalogue reste **fixe et toujours entièrement disponible** : c'est au joueur
 * de choisir la bonne combinaison, pas au niveau de lui imposer un stock.
 */
object TrebuchetRules {

    /** La portée se mesure depuis le pied de la machine. */
    const val FIRING_LINE = 0f

    /** De quoi voir retomber un tir parti en arrière, ce qui arrive vite. */
    const val GROUND_LEFT = -140f
    const val GROUND_RIGHT = 420f

    /**
     * Longueurs de bras proposées, en mètres.
     *
     * Une machine de jet vit de son élan : la vitesse de la pointe vaut oméga fois
     * le bras long, et la fronde double encore ça. Mais un bras long est aussi un
     * bras lourd, qui emporte lui-même une bonne part de l'énergie du contrepoids :
     * c'est ce qui crée l'optimum, et c'est pour ça que les vraies machines sont
     * énormes mais pas infinies.
     */
    val BEAM_LENGTHS = floatArrayOf(8f, 12f, 18f)

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
     * Hauteur du pivot, en fraction de la longueur totale du bras.
     *
     * Elle ne sert pas qu'à faire joli : c'est elle qui décide de **l'angle
     * d'armement**. Un pivot haut laisse le bras long descendre plus près de la
     * verticale, donc le contrepoids monte plus haut, donc la machine stocke plus
     * d'énergie et le bras a plus de course pour la rendre.
     */
    val POST_RATIOS = floatArrayOf(0.55f, 0.70f, 0.85f)

    /**
     * Crans du pivot le long du bras, exprimés en rapport bras long / bras court.
     *
     * C'est le réglage qui échange la force contre la vitesse. Bras court long : le
     * contrepoids descend de haut, beaucoup d'énergie, mais le bras tourne
     * lentement. Bras court ramassé : peu d'énergie, mais un fouet très rapide.
     */
    val LEVER_RATIOS = floatArrayOf(3f, 4f, 5f, 6f)

    /**
     * Contrepoids proposés, en kilogrammes — des tonnes, comme les vraies machines.
     *
     * Le rapport qui compte est celui du contrepoids au boulet : les trébuchets
     * historiques tournaient entre cent et quatre cents fois. En dessous, le boulet
     * est trop lourd pour la machine, il freine le bras au lieu de se laisser
     * emporter, et la moitié de l'énergie reste dans la charpente.
     */
    val COUNTERWEIGHTS = floatArrayOf(1500f, 3000f, 6000f)

    /**
     * Longueur de la chape qui suspend le contrepoids, en fraction du bras court.
     *
     * C'est le réglage de **synchronisation** de la machine. Le contrepoids pendu
     * est un pendule : il a son propre temps de balancement. Trop courte, la chape
     * le colle au bras et il se comporte comme s'il y était boulonné — on perd tout
     * le gain du pendulaire. Trop longue, il balance en retard et le bras est déjà
     * reparti quand il pousse enfin. Entre les deux, il tombe presque droit et rend
     * tout ce qu'il a.
     */
    val HANG_RATIOS = floatArrayOf(0.5f, 1f, 1.6f)

    /**
     * Inclinaison du crochet de largage sur l'axe du bras, en degrés — **la visée**.
     *
     * La fronde a deux brins. L'un est noué à la pointe du bras et ne la quitte
     * jamais ; l'autre finit par une boucle simplement posée sur un ergot recourbé,
     * le crochet. Tant que la fronde tire dans l'axe du crochet, la boucle y reste ;
     * dès qu'elle passe au-delà, la boucle glisse et le boulet part.
     *
     * L'angle se mesure depuis l'axe du bras, du côté où la fronde traîne. Bandée,
     * la machine a sa fronde loin en arrière — entre 90 et 145° selon la géométrie —
     * et le fouet la ramène vers l'axe du bras. Elle croise donc le crochet en
     * chemin, et le sens est celui-ci, mesuré au banc et non supposé :
     *
     *  - **crochet redressé** (grand angle) : la fronde le rencontre **tôt**, alors
     *    que le boulet est encore bas et file vers l'avant. Tir tendu et long.
     *  - **crochet couché** (petit angle) : il retient la boucle jusqu'au bout du
     *    fouet. Le boulet y gagne beaucoup de vitesse — jusqu'à soixante mètres par
     *    seconde — mais il a le temps de passer par-dessus la pointe, et il part de
     *    plus en plus haut, puis carrément en arrière.
     *
     * C'est exactement le réglage que les constructeurs de trébuchets passent leurs
     * journées à limer, et il n'y a rien d'arbitraire dedans : le moment du largage
     * est une **conséquence géométrique** de l'angle de la fronde, mesuré à chaque
     * pas. Les valeurs de la liste ne sont pas choisies au jugé non plus : elles
     * couvrent la plage où le banc d'essai trouve les tirs utiles, laquelle dépend
     * beaucoup de la longueur de la fronde — une fronde longue veut un crochet
     * redressé. C'est ce qui lie les deux réglages fins de la machine.
     */
    val PIN_ANGLES_DEG = intArrayOf(60, 75, 90, 105, 120)

    /**
     * Ce que la fronde doit au minimum balayer avant que la boucle puisse glisser.
     *
     * Un crochet plus redressé que la fronde ne la retiendrait pas du tout : la
     * boucle serait déjà du mauvais côté au moment de bander la machine. On le
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

    /** Le boulet : une belle pierre de taille. */
    const val BALL_RADIUS = 0.16f
    const val BALL_MASS = 12f

    /**
     * Traînée du boulet, en kg/m : la moitié de ρ·Cx·S pour une sphère de ce rayon.
     * À 70 m/s elle vaut à peu près le poids du boulet — un trébuchet qui porte
     * loin est une machine dont l'air décide autant que la géométrie.
     */
    const val BALL_DRAG = 0.023f

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
    var beamIndex = 1
    var postIndex = 1
    var ratioIndex = 1
    var weightIndex = 1
    var hangIndex = 1
    var pinIndex = 2

    /** Longueur de la fronde, en fraction du bras long. */
    var slingRatio = 0.6f

    val beamLength: Float get() = TrebuchetRules.BEAM_LENGTHS[beamIndex]
    val leverRatio: Float get() = TrebuchetRules.LEVER_RATIOS[ratioIndex]
    val counterweightMass: Float get() = TrebuchetRules.COUNTERWEIGHTS[weightIndex]
    val pinAngleDeg: Float get() = TrebuchetRules.PIN_ANGLES_DEG[pinIndex].toFloat()

    /** Longueur du bras court, du pivot à la chape du contrepoids. */
    val shortArm: Float get() = beamLength / (1f + leverRatio)

    /** Longueur du bras long, celui qui porte la fronde. */
    val longArm: Float get() = beamLength - shortArm

    /** Hauteur du pivot au-dessus du sol. */
    val pivotHeight: Float get() = TrebuchetRules.POST_RATIOS[postIndex] * beamLength

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

    /**
     * Longueur de la chape, **bornée par la place disponible sous le bras court** :
     * un contrepoids qui laboure le sol au bas de sa course n'est pas un
     * contrepoids, et c'est le genre de situation dont aucun solveur ne se remet.
     */
    val hangLength: Float
        get() {
            val wanted = TrebuchetRules.HANG_RATIOS[hangIndex] * shortArm
            val room = pivotHeight - shortArm - counterweightHalf - TrebuchetRules.CW_CLEARANCE
            return wanted.coerceAtMost(maxOf(room, 0.15f)).coerceAtLeast(0.15f)
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

    fun clamp() {
        beamIndex = beamIndex.coerceIn(0, TrebuchetRules.BEAM_LENGTHS.size - 1)
        postIndex = postIndex.coerceIn(0, TrebuchetRules.POST_RATIOS.size - 1)
        ratioIndex = ratioIndex.coerceIn(0, TrebuchetRules.LEVER_RATIOS.size - 1)
        weightIndex = weightIndex.coerceIn(0, TrebuchetRules.COUNTERWEIGHTS.size - 1)
        hangIndex = hangIndex.coerceIn(0, TrebuchetRules.HANG_RATIOS.size - 1)
        pinIndex = pinIndex.coerceIn(0, TrebuchetRules.PIN_ANGLES_DEG.size - 1)
        slingRatio = slingRatio.coerceIn(
            TrebuchetRules.SLING_MIN_RATIO, TrebuchetRules.SLING_MAX_RATIO
        )
    }

    fun copyFrom(o: MachineConfig) {
        beamIndex = o.beamIndex
        postIndex = o.postIndex
        ratioIndex = o.ratioIndex
        weightIndex = o.weightIndex
        hangIndex = o.hangIndex
        pinIndex = o.pinIndex
        slingRatio = o.slingRatio
    }
}

/**
 * Le jeu du trébuchet : on construit une machine de jet, on décroche la détente,
 * et la portée est la conséquence de la géométrie choisie. On ne vise jamais.
 *
 * Le déroulé d'un tir :
 *  - **BUILD** — la machine est bandée : le bras long plonge vers l'avant, retenu
 *    par la détente ; le contrepoids pend en l'air derrière ; la fronde est étalée
 *    au sol avec le boulet dedans.
 *  - **FLIGHT** — la détente lâche. Le contrepoids tombe, le bras fouette, la
 *    fronde traîne le boulet au sol puis le fait tourner, et la boucle quitte le
 *    crochet quand la géométrie le veut.
 *  - **RESULT** — le boulet est retombé, on mesure.
 *
 * Aucune pièce de la machine n'entre en collision avec une autre : tout se joue par
 * liaisons — un pivot, une chape, une corde. C'est ce qui rend cette machine
 * incomparablement plus solide que la précédente, où un boulet coincé entre trois
 * planches finissait éjecté à des centaines de mètres par seconde.
 */
class TrebuchetGame {

    enum class Phase { BUILD, FLIGHT, RESULT }

    private companion object {
        const val CAT_GROUND = 1
        const val CAT_BEAM = 2
        const val CAT_WEIGHT = 4
        const val CAT_BALL = 8

        /** Durée maximale d'un tir, en secondes : une parabole de 400 m dure 12 s. */
        const val SHOT_TIMEOUT = 30f

        /**
         * Sécurité : passé ça, la fronde est repartie loin devant la pointe sans que
         * le crochet ait lâché. Ça ne devrait pas arriver — mais un boulet qui fait
         * le tour du bras indéfiniment vaut mieux largué que gardé.
         */
        const val RUNAWAY_SLING = 0.6f
    }

    val world = PhysWorld().apply {
        // Trois liaisons et un contact au sol : le monde est bien plus simple
        // qu'avant, et n'a plus besoin de vingt-deux passes pour tenir.
        iterations = 16
    }
    val config = MachineConfig()

    var phase = Phase.BUILD
        private set

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
     * Bandée, la machine a sa fronde très en arrière — entre -90 et -145° — et le
     * fouet la ramène vers zéro. C'est cette remontée que le crochet surveille.
     */
    var slingAngle = 0f
        private set

    /** Angle de la fronde au moment où la machine a été bandée. */
    var slingStartAngle = 0f
        private set

    private var prevRawSlingAngle = 0f

    /** Vrai dès que la boucle a quitté le crochet. */
    var ballFree = false
        private set

    /**
     * Élévation réelle du tir, en degrés, **mesurée** à l'instant du largage.
     * Rien ne la décide à l'avance : elle sort de la vitesse du bras, de celle de
     * la fronde, et du moment où le crochet cesse de retenir la boucle.
     */
    var launchAngleDeg = 0f
        private set

    /** Trajectoire du tir en cours, en couples (x, y). */
    val trail = ArrayList<Float>()

    /** Trajectoire du tir précédent : le fantôme qui sert à corriger. */
    var ghost: FloatArray? = null
        private set

    // Mesures du tir
    var shotDistance = 0f
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
     * sortirait à cette vitesse-là. Le rapport des deux est le gain du fouet, et il
     * tourne autour de deux — d'où quatre fois la portée.
     */
    var launchTipSpeed = 0f
        private set

    /**
     * Rendement du tir : l'énergie emportée par le boulet au largage, rapportée à
     * celle que le contrepoids a réellement dépensée jusque-là.
     *
     * C'est le chiffre qui dit au joueur si sa machine est bien accordée, et c'est
     * celui que les constructeurs de trébuchets cherchent à faire monter. Il paraît
     * bas, et il l'est : quand le contrepoids pèse cinq cents fois le boulet, le
     * gros de l'énergie sert à faire tourner la charpente et à remonter le bras, pas
     * à lancer la pierre. Le catalogue va de 3 % pour une machine mal accordée à
     * 16 % pour la meilleure, et c'est l'ordre de grandeur des vraies machines de ce
     * gabarit.
     */
    var efficiency = 0f
        private set

    var shotCount = 0
        private set
    var bestDistance = 0f
        private set

    private var elapsed = 0f
    private var trailTimer = 0f
    private var cwStartY = 0f
    private val probe = FloatArray(2)

    init {
        build()
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
        val a = -releaseAngle
        val len = 0.2f + 0.03f * config.longArm
        beam.localToWorld(config.beamLength / 2f + len * cos(a), -len * sin(a), out)
    }

    /** (Re)monte entièrement la machine d'après [config], et la rebande. */
    fun build() {
        config.clamp()
        world.clear()
        trail.clear()
        elapsed = 0f
        trailTimer = 0f
        ballFree = false
        efficiency = 0f
        phase = Phase.BUILD

        val half = (TrebuchetRules.GROUND_RIGHT - TrebuchetRules.GROUND_LEFT) / 2f
        ground = PhysBody(half, 1f, 0f).apply {
            x = TrebuchetRules.GROUND_LEFT + half
            y = -1f
            lockPosition = true
            lockRotation = true
            friction = 0.55f
            category = CAT_GROUND
            refreshMass()
        }
        world.add(ground)

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
        world.add(post)

        // Le bras : une simple planche. Plus aucune cuiller ni butée — tout ce que
        // cette géométrie tentait de faire, la fronde le fait mieux.
        val cock = -Math.toRadians(config.cockAngleDeg.toDouble()).toFloat()
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
        world.add(beam)

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
        world.add(counterweight)

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

        ball = PhysBody.circle(TrebuchetRules.BALL_RADIUS, TrebuchetRules.BALL_MASS).apply {
            // Un vrai trébuchet fait rouler son boulet dans une auge lisse : le
            // traîner sur la terre battue mangerait une partie de la course.
            friction = 0.2f
            restitution = 0.1f
            dragFactor = TrebuchetRules.BALL_DRAG
            category = CAT_BALL
            // Le boulet ne connaît que le sol : la fronde le relie au bras, il n'a
            // aucune raison de venir cogner la machine.
            collidesWith = CAT_GROUND
        }
        world.add(ball)
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
    }

    /**
     * Angle auquel le crochet lâchera réellement la boucle, en radians (négatif).
     *
     * C'est l'inclinaison choisie, mais couchée si besoin : un crochet plus redressé
     * que la fronde ne retiendrait rien du tout.
     */
    val releaseAngle: Float
        get() = maxOf(
            -Math.toRadians(config.pinAngleDeg.toDouble()).toFloat(),
            slingStartAngle + Math.toRadians(TrebuchetRules.MIN_PIN_SWEEP_DEG.toDouble()).toFloat()
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
     * Pose le boulet au sol, derrière la pointe, à l'endroit exact où la fronde est
     * juste tendue. La fronde étant une corde, un peu de mou ne gênerait pas, mais un
     * tir commence mieux sans à-coup.
     */
    private fun placeBall() {
        tipWorld(probe)
        val dy = probe[1] - TrebuchetRules.BALL_RADIUS
        val l = config.slingLength
        val dx = sqrt(maxOf(l * l - dy * dy, 0.01f))
        ball.x = probe[0] - dx
        ball.y = TrebuchetRules.BALL_RADIUS
        ball.angle = 0f
        ball.vx = 0f; ball.vy = 0f; ball.omega = 0f
        ball.collidesWith = CAT_GROUND
        world.forgetContacts(ball)
    }

    /** Rejoue la pose avec les réglages actuels, en gardant le fantôme du tir précédent. */
    fun rebuild() {
        val keep = ghost
        build()
        ghost = keep
    }

    // ── Réglages ─────────────────────────────────────────────────────────────

    /** Fait défiler un réglage du catalogue ; [delta] vaut +1 ou -1. */
    fun cycleBeam(delta: Int) = changeSetting {
        config.beamIndex = wrap(config.beamIndex + delta, TrebuchetRules.BEAM_LENGTHS.size)
    }

    fun cyclePost(delta: Int) = changeSetting {
        config.postIndex = wrap(config.postIndex + delta, TrebuchetRules.POST_RATIOS.size)
    }

    fun cycleRatio(delta: Int) = changeSetting {
        config.ratioIndex = wrap(config.ratioIndex + delta, TrebuchetRules.LEVER_RATIOS.size)
    }

    fun cycleWeight(delta: Int) = changeSetting {
        config.weightIndex = wrap(config.weightIndex + delta, TrebuchetRules.COUNTERWEIGHTS.size)
    }

    /** Longueur de la chape : c'est le réglage de synchronisation du contrepoids. */
    fun cycleHang(delta: Int) = changeSetting {
        config.hangIndex = wrap(config.hangIndex + delta, TrebuchetRules.HANG_RATIOS.size)
    }

    /** Inclinaison du crochet de largage : le réglage de visée de la machine. */
    fun cyclePin(delta: Int) = changeSetting {
        config.pinIndex = wrap(config.pinIndex + delta, TrebuchetRules.PIN_ANGLES_DEG.size)
    }

    /** Repart d'une machine neuve, fantôme compris. */
    fun reset() {
        config.copyFrom(MachineConfig())
        build()
        ghost = null
    }

    private inline fun changeSetting(apply: () -> Unit) {
        apply()
        rebuild()
    }

    private fun wrap(v: Int, size: Int) = ((v % size) + size) % size

    /** Règle la longueur de la fronde, en mètres (phase de pose uniquement). */
    fun setSlingLength(metres: Float) {
        if (phase != Phase.BUILD) return
        config.slingRatio = metres / config.longArm
        config.clamp()
        build()
    }

    // ── Le tir ───────────────────────────────────────────────────────────────

    /** Décroche la détente : à partir de là, tout n'est plus que de la physique. */
    fun release() {
        if (phase != Phase.BUILD) return
        trail.clear()
        elapsed = 0f
        trailTimer = 0f
        ballFree = false
        shotDistance = 0f
        peakHeight = ball.y
        peakSpeed = 0f
        launchSpeed = 0f
        launchTipSpeed = 0f
        launchAngleDeg = 0f
        efficiency = 0f
        cwStartY = counterweight.y

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
        if (phase != Phase.FLIGHT) return

        world.stepFrame(dt)
        elapsed += dt

        val speed = hypot(ball.vx, ball.vy)
        if (speed > peakSpeed) peakSpeed = speed
        if (ball.y > peakHeight) peakHeight = ball.y

        if (!ballFree) {
            launchSpeed = speed
            updateSlingAngle()
            if (slingAngle >= releaseAngle || slingAngle >= RUNAWAY_SLING) letGo()
        }

        trailTimer += dt
        if (trailTimer > 0.02f && trail.size < 6000) {
            trailTimer = 0f
            trail.add(ball.x)
            trail.add(ball.y)
        }

        // Le tir se mesure au **point d'impact**, comme une portée d'artillerie : ce
        // qui compte est là où le boulet frappe, pas où il finit de rouler. Un boulet
        // qui touche à quarante mètres par seconde rebondit et roule encore cent
        // mètres, ce qui n'apprend rien au joueur sur sa machine.
        //
        // On n'attend pas non plus que la machine s'immobilise : le bras balance au
        // bout de sa chape pendant de longues secondes après le tir.
        val landed = ballFree &&
            ball.y <= TrebuchetRules.BALL_RADIUS + 0.03f &&
            ball.vy <= 0f
        if (landed || elapsed > SHOT_TIMEOUT || ball.x > TrebuchetRules.GROUND_RIGHT - 4f) {
            finishShot()
        }
    }

    /** Suit l'angle de la fronde en le déroulant, pour qu'il ne saute pas à ±π. */
    private fun updateSlingAngle() {
        val raw = measureSlingAngle()
        var d = raw - prevRawSlingAngle
        val twoPi = 2f * PI.toFloat()
        while (d > PI) d -= twoPi
        while (d < -PI) d += twoPi
        prevRawSlingAngle = raw
        slingAngle += d
    }

    /** La boucle quitte le crochet : le boulet n'appartient plus à la machine. */
    private fun letGo() {
        ballFree = true
        sling.enabled = false
        world.forgetContacts(ball)
        launchSpeed = hypot(ball.vx, ball.vy)
        launchAngleDeg = Math.toDegrees(atan2(ball.vy.toDouble(), ball.vx.toDouble())).toFloat()
        // La pointe du bras décrit un cercle autour du pivot : sa vitesse est le
        // produit de la rotation du bras par la longueur du bras long.
        launchTipSpeed = abs(beam.omega) * config.longArm

        // Rendement : ce que le boulet emporte, sur ce que le contrepoids a dépensé
        // pour le lui donner.
        val spent = config.counterweightMass * TrebuchetRules.GRAVITY * (cwStartY - counterweight.y)
        efficiency = if (spent > 1f) {
            0.5f * TrebuchetRules.BALL_MASS * launchSpeed * launchSpeed / spent
        } else {
            0f
        }
    }

    private fun finishShot() {
        phase = Phase.RESULT
        shotDistance = ball.x - TrebuchetRules.FIRING_LINE
        shotCount++
        if (shotDistance > bestDistance) bestDistance = shotDistance
        ghost = trail.toFloatArray()
    }

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
}
