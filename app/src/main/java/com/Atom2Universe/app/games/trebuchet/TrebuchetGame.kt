package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import com.Atom2Universe.app.games.physics.RevoluteJoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Dimensions du terrain et catalogue de pièces, en mètres (repère physique,
 * Y vers le haut, origine au sol sous le pivot).
 *
 * Le catalogue est **fixe et toujours entièrement disponible** : c'est au joueur
 * de choisir la bonne combinaison selon le terrain, pas au niveau de lui imposer
 * un stock. Les réglages continus (hauteur de lâcher, position du boulet) sont
 * volontairement bornés serré : s'ils couvraient toute la plage, une seule
 * machine suffirait à résoudre tous les niveaux et les crans ne serviraient plus.
 */
object TrebuchetRules {

    /** La machine tire vers les x positifs : le boulet passe par-dessus le pivot. */
    const val FIRING_LINE = 3f

    const val GROUND_LEFT = -14f
    const val GROUND_RIGHT = 260f

    /**
     * Longueurs de bras proposées, en mètres.
     *
     * Une machine de jet vit de son élan : la vitesse de sortie vaut oméga fois la
     * longueur du bras long, et l'énergie disponible vaut le poids du contrepoids
     * fois la hauteur dont il descend, elle-même proportionnelle au bras court.
     * Tout allonger fait donc grandir la portée deux fois — c'est pour ça que les
     * vraies machines sont énormes.
     */
    val BEAM_LENGTHS = floatArrayOf(5f, 8f, 12f)

    /** Un bras plus long est plus lourd : c'est ce qui crée l'optimum de rapport. */
    const val BEAM_DENSITY = 1.2f
    const val BEAM_HALF_THICKNESS = 0.07f

    /**
     * Hauteurs de pied, donc du pivot. Il faut de quoi laisser le bras court
     * descendre jusqu'à l'arrêtoir sans toucher le sol.
     */
    val FOOT_HEIGHTS = floatArrayOf(3f, 4.5f, 6f)

    /**
     * Crans du pied le long du bras, exprimés en rapport bras long / bras court.
     *
     * C'est **le** réglage qui commande la portée, bien plus que le contrepoids.
     * Quand celui-ci devient lourd, la vitesse de sortie tend vers une limite qui
     * ne dépend plus du tout de sa masse :
     *
     *     v = racine(2·g·sin(angle d'arrêt)) × bras long / racine(bras court)
     *
     * Autrement dit, doubler le contrepoids ne sert presque à rien — son énergie
     * part dans sa propre inertie — alors que raccourcir le bras court fait
     * grimper la vitesse. C'est pour ça que les vrais trébuchets ont un
     * contrepoids énorme collé tout près de l'axe.
     */
    val LEVER_RATIOS = floatArrayOf(2f, 4f, 6f, 9f)

    /** Longueur minimale du bras court : en deçà, la machine n'est plus crédible. */
    const val MIN_SHORT_ARM = 0.9f

    /** Contrepoids : léger, moyen, lourd. */
    val COUNTERWEIGHTS = floatArrayOf(200f, 400f, 700f)

    /**
     * Hauteur de lâcher du contrepoids, au-dessus du bras court.
     *
     * Lâcher de haut, c'est de l'élan en plus — exactement le principe de la
     * bascule de cirque, où l'acrobate saute sur la planche au lieu de s'y poser.
     * Et l'élan marche : au banc d'essai, un lâcher de 2,5 m fait sortir le boulet
     * à 31 m/s au lieu de 20.
     *
     * Mais une bascule de cirque envoie son acrobate **droit en l'air**, et c'est
     * bien ce que fait un choc : le bras saute à pleine vitesse en un ou deux
     * degrés de rotation, et le boulet part perpendiculairement au bras — mesuré à
     * 81°. Récupérer cet élan vers l'avant demanderait une cuiller capable de
     * retenir le boulet pendant le choc puis de le rendre au bon moment, ce qu'un
     * creux rigide ne sait pas faire (voir [CUP_CURVES_DEG]). D'ici la fronde, la
     * plage reste donc courte : ce réglage déclenche le tir, il ne le charge pas.
     */
    const val DROP_MIN = 0.3f
    const val DROP_MAX = 2.5f

    /**
     * Le boulet. Le rapport entre sa masse et celle du contrepoids est ce qui
     * règle la portée : à énergie donnée, la vitesse de sortie varie comme la
     * racine de ce rapport. C'est le réglage le plus efficace pour décider de la
     * taille du terrain.
     */
    const val BALL_RADIUS = 0.16f
    const val BALL_MASS = 6f

    /**
     * La cuiller. Les deux butées ne se ressemblent pas du tout, parce qu'elles ne
     * servent pas à la même chose :
     *
     *  - **côté pivot**, un simple rebord, très bas. Il ne sert qu'au tout début,
     *    quand le bras s'incline et que le boulet roule vers le pivot sous son
     *    propre poids. Le faire haut ne servait à rien et enfermait le boulet.
     *  - **côté pointe**, la vraie butée : un montant **toujours d'équerre** avec
     *    le bras, dont seule la moitié haute se recourbe. Dès que le bras fouette,
     *    la force centrifuge chasse le boulet vers la pointe et c'est ce montant
     *    qui le retient et l'emmène ; la courbure de son extrémité décide de la
     *    façon dont il s'échappe, donc de l'angle du tir.
     *
     * Enfermer le boulet entre deux hautes parois, comme au premier essai,
     * l'empêchait tout simplement de sortir.
     */
    const val CUP_HALF_WIDTH = 0.07f

    /**
     * Jeu laissé au boulet entre les deux rebords. Sans lui l'écart vaut très
     * exactement son diamètre : le boulet est monté serré, le moindre tremblement
     * du solveur l'enfonce des deux côtés à la fois, et il finit par jaillir de la
     * cuiller à une vitesse absurde toujours identique.
     */
    const val CUP_CLEARANCE = 0.025f

    /**
     * Courbure de la pointe de la butée avant, en degrés — le réglage de visée.
     *
     * Seule la pointe du montant se recourbe, vers le pivot. Plus elle crochète,
     * plus le boulet est retenu longtemps et part tendu ; droite, il s'échappe tôt
     * et monte presque à la verticale.
     *
     * L'échelle est courte, et pas par prudence : mesures à l'appui, une cuiller
     * rigide est **bistable**. En dessous d'une vingtaine de degrés elle ne retient
     * rien et le boulet part presque à la verticale ; au-dessus de quarante elle
     * l'emprisonne et il ne part plus du tout, faisant le tour avec le bras. Entre
     * les deux, la transition est brutale et il n'existe aucune valeur qui donne un
     * beau 45°. C'est la limite de fond de ce type de machine, et c'est exactement
     * pour ça qu'un vrai trébuchet emploie une fronde : une poche au bout d'une
     * corde sépare le fait de retenir du fait de lâcher, ce qu'un creux rigide ne
     * sait pas faire.
     */
    val CUP_CURVES_DEG = intArrayOf(0, 15, 30, 45)

    /** Position du contrepoids sur le bras court, en fraction de sa longueur. */
    const val SEAT_POSITION = 0.75f
    /** Le rebord côté pivot : juste de quoi bloquer le roulement du début. */
    const val CUP_BACK_HALF_HEIGHT = BALL_RADIUS * 0.55f

    /**
     * La butée avant, en deux morceaux.
     *
     * Le montant est d'équerre avec le bras, et son coude tombe **à hauteur du
     * centre du boulet** : c'est ce qui permet à la pointe de venir se refermer
     * par-dessus, et donc de retenir le boulet quand le contrepoids frappe fort.
     * Un coude placé plus haut que le boulet ne le couvrait pas : la pointe
     * passait à vingt-cinq centimètres au-dessus de lui sans rien retenir.
     */
    const val CUP_FRONT_BASE_HALF_HEIGHT = BALL_RADIUS * 0.62f
    const val CUP_FRONT_TIP_HALF_HEIGHT = BALL_RADIUS
    /**
     * Masse des pièces de la cuiller, du même ordre que celle du bras qui les
     * porte : une butée très légère encaisse mal la poussée d'un boulet lancé.
     */
    const val CUP_MASS = 2f

    /**
     * L'arrêtoir : le tampon que le bras court vient percuter en fin de course.
     *
     * Sans lui la machine ne lâche jamais rien. Une cuiller fermée retient son
     * boulet tant que la force centrifuge dépasse la pesanteur — le même effet
     * qu'un seau d'eau qu'on fait tourner à bout de bras : au banc d'essai, le
     * boulet faisait un tour complet accroché au bras avant d'être renvoyé en
     * arrière. C'est l'arrêt brutal du bras qui libère le boulet, et l'angle où
     * le bras s'arrête décide de l'angle de tir. Tous les mangonneaux ont cette
     * pièce, et c'est elle qui deviendra le réglage d'angle de largage.
     *
     * C'est un **réglage du joueur** : c'est lui qui décide de l'angle du tir. Un
     * arrêt précoce lâche le boulet haut et court, un arrêt tardif le lâche tendu.
     * Et il faut l'accorder au crochet : un crochet refermé retient le boulet
     * au-delà de l'arrêt, donc il faut arrêter le bras plus tôt pour compenser.
     *
     * Un arrêt tardif demande un pied haut, sinon le tampon passerait sous terre —
     * on retombe alors sur le cran praticable le plus proche.
     */
    val STOP_ANGLES_DEG = intArrayOf(-30, -40, -50, -60)
    const val STOP_RADIUS = 0.09f
    const val STOP_MIN_DISTANCE = 0.18f
    const val STOP_CLEARANCE = 0.08f

    /** Le boulet doit rester sur le bras long, sans chevaucher le pivot. */
    const val BALL_MIN_FROM_PIVOT = 1f

    const val GRAVITY = 9.81f

    /**
     * Demi-côté du contrepoids. La masse suit la surface, donc le côté suit la
     * racine : c'est ce qui évite qu'un contrepoids lourd devienne monstrueux.
     */
    fun counterweightHalfSize(mass: Float): Float = 0.021f * kotlin.math.sqrt(mass)
}

/**
 * Les choix du joueur. Tout ce qui définit la machine tient ici, ce qui permet
 * de rejouer un tir à l'identique — et, plus tard, de faire simuler au générateur
 * de niveaux une machine tirée au sort.
 */
class MachineConfig {
    var beamIndex = 1
    var footIndex = 1
    var ratioIndex = 2
    var weightIndex = 1
    var cupCurveIndex = 1
    var stopIndex = 2

    /** Hauteur de lâcher au-dessus du bras court. */
    var dropHeight = 0.7f

    /** Distance du boulet au pivot, sur le bras long. */
    var ballDistance = 1.5f

    val beamLength: Float get() = TrebuchetRules.BEAM_LENGTHS[beamIndex]
    val footHeight: Float get() = TrebuchetRules.FOOT_HEIGHTS[footIndex]
    /**
     * Rapport de bras effectivement réalisable.
     *
     * Un cran très marqué sur un bras court donnerait un bras court ridicule —
     * cinquante centimètres pour porter sept cents kilos. Le contrepoids y pèse
     * alors plus de cent fois le boulet à bout portant du pivot, et la simulation
     * n'a plus rien de crédible : mesuré à 147 m/s en sortie de cuiller. On borne
     * donc le cran à ce que la longueur du bras permet.
     */
    val leverRatio: Float
        get() = minOf(
            TrebuchetRules.LEVER_RATIOS[ratioIndex],
            beamLength / TrebuchetRules.MIN_SHORT_ARM - 1f
        )
    val counterweightMass: Float get() = TrebuchetRules.COUNTERWEIGHTS[weightIndex]
    val cupCurveDeg: Float get() = TrebuchetRules.CUP_CURVES_DEG[cupCurveIndex].toFloat()

    /** Angle auquel on veut arrêter le bras : le réglage de visée de la machine. */
    val wantedStopDeg: Float get() = TrebuchetRules.STOP_ANGLES_DEG[stopIndex].toFloat()

    /** Écart du rebord arrière au centre du boulet : il reste d'équerre. */
    val cupBackGap: Float
        get() = TrebuchetRules.BALL_RADIUS + TrebuchetRules.CUP_HALF_WIDTH +
            TrebuchetRules.CUP_CLEARANCE

    /**
     * Écart de la butée avant. Elle recule à mesure que sa pointe se referme :
     * sinon le crochet, qui part du centre du boulet, viendrait le raboter dès la
     * construction — et un boulet coincé, le solveur l'expédie à 300 m/s.
     */
    val cupFrontGap: Float
        get() = cupBackGap + 2f * TrebuchetRules.CUP_FRONT_TIP_HALF_HEIGHT *
            sin(Math.toRadians(cupCurveDeg.toDouble()).toFloat()) * 0.5f
    val beamMass: Float get() = beamLength * TrebuchetRules.BEAM_DENSITY

    /**
     * Demi-côté du contrepoids, **borné par le bras court qui le porte**.
     *
     * Sans cette limite, 700 kg font 1,10 m de large alors qu'un bras court de
     * rapport 9 sur un bras de 5 m n'en mesure que 0,55 : le contrepoids déborde de
     * partout, se plante dans l'arrêtoir et dans le sol, et le solveur finit par
     * expédier le boulet à 1 200 m/s. Une machine dont le contrepoids est plus
     * large que son bras n'est pas une machine.
     */
    val counterweightHalf: Float
        get() = minOf(
            TrebuchetRules.counterweightHalfSize(counterweightMass),
            0.4f * shortArm
        )

    /** Longueur du bras court, du pivot à son extrémité. */
    val shortArm: Float get() = beamLength / (1f + leverRatio)

    /** Longueur du bras long, celui qui porte le boulet. */
    val longArm: Float get() = beamLength - shortArm

    /**
     * Énergie stockée par le contrepoids, en joules : c'est tout ce que la
     * machine aura pour lancer. m·g·h, la hauteur étant celle du lâcher.
     */
    val storedEnergy: Float
        get() = counterweightMass * TrebuchetRules.GRAVITY * dropHeight

    fun clamp() {
        beamIndex = beamIndex.coerceIn(0, TrebuchetRules.BEAM_LENGTHS.size - 1)
        footIndex = footIndex.coerceIn(0, TrebuchetRules.FOOT_HEIGHTS.size - 1)
        ratioIndex = ratioIndex.coerceIn(0, TrebuchetRules.LEVER_RATIOS.size - 1)
        weightIndex = weightIndex.coerceIn(0, TrebuchetRules.COUNTERWEIGHTS.size - 1)
        cupCurveIndex = cupCurveIndex.coerceIn(0, TrebuchetRules.CUP_CURVES_DEG.size - 1)
        stopIndex = stopIndex.coerceIn(0, TrebuchetRules.STOP_ANGLES_DEG.size - 1)
        dropHeight = dropHeight.coerceIn(TrebuchetRules.DROP_MIN, TrebuchetRules.DROP_MAX)
        // Il faut laisser la place de la butée avant entre le boulet et la pointe.
        val room = longArm - cupFrontGap - TrebuchetRules.CUP_HALF_WIDTH
        ballDistance = ballDistance.coerceIn(
            TrebuchetRules.BALL_MIN_FROM_PIVOT,
            maxOf(TrebuchetRules.BALL_MIN_FROM_PIVOT, room)
        )
    }

    fun copyFrom(o: MachineConfig) {
        beamIndex = o.beamIndex
        footIndex = o.footIndex
        ratioIndex = o.ratioIndex
        weightIndex = o.weightIndex
        cupCurveIndex = o.cupCurveIndex
        stopIndex = o.stopIndex
        dropHeight = o.dropHeight
        ballDistance = o.ballDistance
    }
}

/**
 * Le jeu du trébuchet : on construit une machine de jet, on lâche le contrepoids,
 * et la portée est la conséquence de la géométrie choisie. On ne vise jamais.
 *
 * Le déroulé d'un tir :
 *  - **BUILD** — le bras est tenu par un cliquet (rotation bloquée), le boulet
 *    repose dessus, le contrepoids est suspendu au-dessus du bras court.
 *  - **DROP** — le contrepoids est lâché et tombe. Le cliquet tient toujours :
 *    sans lui, le bras s'affaisserait du côté du boulet avant même le choc, et
 *    le boulet roulerait par terre.
 *  - **FLIGHT** — le choc libère le cliquet, le bras fouette, le boulet part.
 *  - **RESULT** — tout s'est immobilisé, on mesure la portée.
 */
class TrebuchetGame {

    enum class Phase { BUILD, DROP, FLIGHT, RESULT }

    private companion object {
        // Catégories de collision. L'arrêtoir ne doit toucher que le bras, et le
        // boulet cesse de voir la machine dès qu'il l'a quittée : sinon il retombe
        // dessus et se fait broyer entre le contrepoids et le bras.
        const val CAT_GROUND = 1
        const val CAT_BEAM = 2
        const val CAT_WEIGHT = 4
        const val CAT_STOPPER = 8
        const val CAT_BALL = 16
        const val MACHINE = CAT_BEAM or CAT_WEIGHT or CAT_STOPPER
    }

    val world = PhysWorld().apply {
        // Plus de passes que le réglage d'origine : le boulet est coincé entre le
        // bras, la butée et son crochet, et sous plusieurs centaines de kilos une
        // machine lancée. Un solveur trop pressé laisse de l'interpénétration
        // résiduelle dans ce coin-là, et finit par éjecter le boulet.
        iterations = 22
    }
    val config = MachineConfig()

    var phase = Phase.BUILD
        private set

    lateinit var ground: PhysBody
        private set
    lateinit var post: PhysBody
        private set
    lateinit var beam: PhysBody
        private set
    lateinit var ball: PhysBody
        private set
    lateinit var counterweight: PhysBody
        private set

    /** Le tampon que le bras vient percuter : c'est lui qui libère le boulet. */
    lateinit var stopper: PhysBody
        private set

    /** Angle auquel le bras est arrêté, en degrés (négatif = bras long en l'air). */
    var stopAngleDeg = -45f
        private set

    /**
     * Élévation réelle du tir, en degrés, **mesurée** au moment où le boulet quitte
     * la cuiller.
     *
     * Rien ne la décide à l'avance : elle sort de la forme de la cuiller, de la
     * vitesse du bras et du moment où la géométrie cesse de retenir le boulet.
     */
    var launchAngleDeg = 0f
        private set

    private lateinit var pivot: RevoluteJoint
    /** Position du creux de la cuiller dans le repère du bras, pour voir partir le boulet. */
    private var seatLocalX = 0f
    private var seatLocalY = 0f

    /**
     * L'attache du contrepoids, créée au moment où il arrive sur le bras.
     *
     * Un contrepoids simplement posé glisse dès que le bras dépasse l'angle de
     * frottement — 39° — et tombe par terre : la machine perd ce qui l'entraîne et
     * le bras se contente d'osciller. Au banc d'essai, la moitié des machines
     * n'atteignaient même pas l'arrêtoir. Le retenir entre deux parois soudées ne
     * marche pas non plus : 90 kg lâchés entre deux planches de 1,4 kg, c'est un
     * rapport de masses que ce genre de solveur ne sait pas tenir, et la machine
     * partait à 400 m/s.
     *
     * On fait donc ce que font les vraies machines : le bras **attrape** le
     * contrepoids au moment du choc, et ne le lâche plus. Une seule liaison, entre
     * deux corps lourds tous les deux, et le contrepoids reste libre de pivoter.
     */
    private var cwHinge: RevoluteJoint? = null

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

    var shotCount = 0
        private set
    var bestDistance = 0f
        private set

    private var elapsed = 0f
    private var trailTimer = 0f
    private var ballFree = false
    private val seatProbe = FloatArray(2)

    init {
        build()
    }

    // ── Construction de la machine ────────────────────────────────────────────

    /** Position du pivot dans le monde. */
    val pivotX: Float get() = 0f
    val pivotY: Float get() = config.footHeight

    /** (Re)monte entièrement la machine d'après [config], et repasse en pose. */
    fun build() {
        config.clamp()
        world.clear()
        cwHinge = null
        trail.clear()
        elapsed = 0f
        trailTimer = 0f
        ballFree = false
        phase = Phase.BUILD

        val half = (TrebuchetRules.GROUND_RIGHT - TrebuchetRules.GROUND_LEFT) / 2f
        ground = PhysBody(half, 1f, 0f).apply {
            x = TrebuchetRules.GROUND_LEFT + half
            y = -1f
            lockPosition = true
            lockRotation = true
            friction = 0.75f
            category = CAT_GROUND
            refreshMass()
        }
        world.add(ground)

        // Le pied : un corps immobile qui sert d'axe. Le fût est purement décoratif,
        // c'est ce petit corps-ci qui tient le bras.
        post = PhysBody(0.07f, 0.07f, 0f).apply {
            x = pivotX
            y = pivotY
            lockPosition = true
            lockRotation = true
            refreshMass()
        }
        world.add(post)

        // Le bras et ses deux butées : **une seule pièce rigide**.
        //
        // C'est le point qui a tout changé. Tant que les butées étaient des corps
        // séparés tenus par une soudure approchée, le boulet coincé entre elles et
        // le fond faisait exploser le solveur : l'effort devait traverser une
        // liaison molle avant d'atteindre le bras, et le boulet ressortait à
        // 250 m/s. Assemblées en un seul corps, elles n'ont plus aucun jeu, et le
        // boulet peut rester libre — c'est la géométrie qui le retient puis le
        // lâche, plus une règle écrite à la main.
        val plankCenterX = pivotX + (config.shortArm - config.longArm) / 2f
        val top = TrebuchetRules.BEAM_HALF_THICKNESS
        val seatX = -config.ballDistance - (config.shortArm - config.longArm) / 2f
        val backGap = config.cupBackGap
        val frontGap = config.cupFrontGap
        val curve = -Math.toRadians(config.cupCurveDeg.toDouble()).toFloat()
        val kneeY = top + 2f * TrebuchetRules.CUP_FRONT_BASE_HALF_HEIGHT

        beam = PhysBody.compound(config.beamMass + 3f * TrebuchetRules.CUP_MASS) {
            // la planche
            box(config.beamLength / 2f, TrebuchetRules.BEAM_HALF_THICKNESS, 0f, 0f)
            // le rebord côté pivot, tout bas
            box(
                TrebuchetRules.CUP_HALF_WIDTH, TrebuchetRules.CUP_BACK_HALF_HEIGHT,
                seatX + backGap, top + TrebuchetRules.CUP_BACK_HALF_HEIGHT
            )
            // le montant d'équerre de la butée avant
            box(
                TrebuchetRules.CUP_HALF_WIDTH, TrebuchetRules.CUP_FRONT_BASE_HALF_HEIGHT,
                seatX - frontGap, top + TrebuchetRules.CUP_FRONT_BASE_HALF_HEIGHT
            )
            // et sa pointe recourbée, qui part du coude
            box(
                TrebuchetRules.CUP_HALF_WIDTH, TrebuchetRules.CUP_FRONT_TIP_HALF_HEIGHT,
                seatX - frontGap + TrebuchetRules.CUP_FRONT_TIP_HALF_HEIGHT * sin(curve).let { -it },
                kneeY + TrebuchetRules.CUP_FRONT_TIP_HALF_HEIGHT * cos(curve),
                curve
            )
        }.apply {
            friction = 0.75f
            category = CAT_BEAM
            // Le corps est repéré par son centre de masse : on le place de façon que
            // la planche, elle, tombe exactement où on la veut.
            x = plankCenterX - localOffsetX(0)
            y = pivotY - localOffsetY(0)
            // Le cliquet : pendant la pose, le bras ne bouge pas du tout.
            lockPosition = true
            lockRotation = true
            refreshMass()
        }
        world.add(beam)

        pivot = RevoluteJoint.pin(beam, post, pivotX, pivotY)
        world.addJoint(pivot)

        ball = PhysBody.circle(TrebuchetRules.BALL_RADIUS, TrebuchetRules.BALL_MASS).apply {
            friction = 0.7f
            restitution = 0.18f
            category = CAT_BALL
        }
        world.add(ball)
        placeBall()

        val cwHalf = config.counterweightHalf
        counterweight = PhysBody(cwHalf, cwHalf, config.counterweightMass).apply {
            friction = 0.8f
            category = CAT_WEIGHT
            // Un contrepoids lourd est plus large qu'un bras court, et vient donc
            // occuper la place de l'arrêtoir : les faire se percuter n'aurait aucun
            // sens, l'un est le tampon du bras et l'autre pend dessous.
            collidesWith = CAT_GROUND or CAT_BEAM or CAT_BALL
            // Tenu en l'air par le joueur : le moteur l'ignore jusqu'au lâcher.
            inWorld = false
        }
        world.add(counterweight)
        placeCounterweight()

        stopper = PhysBody.circle(TrebuchetRules.STOP_RADIUS, 0f).apply {
            category = CAT_STOPPER
            // Le tampon n'existe que pour le bras.
            collidesWith = CAT_BEAM
            lockPosition = true
            lockRotation = true
            friction = 0.9f
            refreshMass()
        }
        world.add(stopper)
        placeStopper()
    }

    /**
     * Installe l'arrêtoir sous le bras court, à l'endroit exact où celui-ci le
     * touchera à l'angle voulu.
     *
     * On essaie les inclinaisons de la plus tardive à la plus précoce et on garde
     * la première qui laisse le tampon au-dessus du sol : avec un pied bas et un
     * bras court très long, l'arrêt tardif passerait sous terre.
     */
    private fun placeStopper() {
        val ht = TrebuchetRules.BEAM_HALF_THICKNESS
        val rs = TrebuchetRules.STOP_RADIUS

        // On part du cran demandé, puis on remonte vers les arrêts plus précoces
        // si le tampon ne tient pas au-dessus du sol.
        for (i in config.stopIndex downTo 0) {
            val deg = TrebuchetRules.STOP_ANGLES_DEG[i]
            val a = Math.toRadians(deg.toDouble()).toFloat()
            val ux = cos(a); val uy = sin(a)
            // Normale du bras à cet angle : sa direction « vers le haut » locale.
            val nx = -sin(a); val ny = cos(a)

            // Distance maximale le long du bras qui garde le tampon au-dessus du sol.
            val dMax = (pivotY - TrebuchetRules.STOP_CLEARANCE - rs - (ht + rs) * ny) / -uy
            val d = minOf(0.8f * config.shortArm, dMax)
            if (d < TrebuchetRules.STOP_MIN_DISTANCE) continue

            stopAngleDeg = deg.toFloat()
            stopper.x = pivotX + d * ux - (ht + rs) * nx
            stopper.y = pivotY + d * uy - (ht + rs) * ny
            world.forgetContacts(stopper)
            return
        }
        // Aucun cran ne tient : on pose le tampon au plus près du pivot.
        val deg = TrebuchetRules.STOP_ANGLES_DEG.first()
        val a = Math.toRadians(deg.toDouble()).toFloat()
        stopAngleDeg = deg.toFloat()
        val d = TrebuchetRules.STOP_MIN_DISTANCE
        stopper.x = pivotX + d * cos(a) - (ht + rs) * -sin(a)
        stopper.y = pivotY + d * sin(a) - (ht + rs) * cos(a)
        world.forgetContacts(stopper)
    }

    private fun placeBall() {
        ball.x = pivotX - config.ballDistance
        ball.y = pivotY + TrebuchetRules.BEAM_HALF_THICKNESS + TrebuchetRules.BALL_RADIUS
        ball.vx = 0f; ball.vy = 0f; ball.omega = 0f; ball.angle = 0f
        world.forgetContacts(ball)
        // Le bras est horizontal à la pose : le creux se repère par simple écart.
        seatLocalX = ball.x - beam.x
        seatLocalY = ball.y - beam.y
    }

    /** Position actuelle du creux de la cuiller, qui suit le bras. */
    private fun seatWorld(out: FloatArray) = beam.localToWorld(seatLocalX, seatLocalY, out)

    /** Abscisse où le contrepoids se pose sur le bras court. */
    private fun seatCenterX(): Float =
        pivotX + maxOf(0.22f, config.shortArm * TrebuchetRules.SEAT_POSITION)

    /** Suspend le contrepoids au-dessus de l'extrémité du bras court. */
    private fun placeCounterweight() {
        val half = config.counterweightHalf
        // Juste au-dessus du berceau, pour qu'il tombe dedans.
        counterweight.x = seatCenterX()
        counterweight.y = pivotY + TrebuchetRules.BEAM_HALF_THICKNESS + half + config.dropHeight
        counterweight.vx = 0f; counterweight.vy = 0f; counterweight.omega = 0f
        counterweight.angle = 0f
        world.forgetContacts(counterweight)
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

    fun cycleFoot(delta: Int) = changeSetting {
        config.footIndex = wrap(config.footIndex + delta, TrebuchetRules.FOOT_HEIGHTS.size)
    }

    fun cycleRatio(delta: Int) = changeSetting {
        config.ratioIndex = wrap(config.ratioIndex + delta, TrebuchetRules.LEVER_RATIOS.size)
    }

    fun cycleWeight(delta: Int) = changeSetting {
        config.weightIndex = wrap(config.weightIndex + delta, TrebuchetRules.COUNTERWEIGHTS.size)
    }

    /** Courbure de la butée avant : c'est elle qui retient le boulet pendant le choc. */
    fun cycleCupCurve(delta: Int) = changeSetting {
        config.cupCurveIndex = wrap(config.cupCurveIndex + delta, TrebuchetRules.CUP_CURVES_DEG.size)
    }

    /** Angle d'arrêt du bras : le réglage de visée de la machine. */
    fun cycleStop(delta: Int) = changeSetting {
        config.stopIndex = wrap(config.stopIndex + delta, TrebuchetRules.STOP_ANGLES_DEG.size)
    }

    /** Repart d'une machine neuve, fantôme compris. */
    fun reset() {
        config.copyFrom(MachineConfig())
        build()
        ghost = null
    }

    private inline fun changeSetting(apply: () -> Unit) {
        apply()
        // Changer de bras peut rendre la position du boulet impossible : on la
        // ramène dans le domaine valide plutôt que de refuser le réglage.
        rebuild()
    }

    private fun wrap(v: Int, size: Int) = ((v % size) + size) % size

    /** Déplace le boulet le long du bras long (phase de pose uniquement). */
    fun setBallDistance(d: Float) {
        if (phase != Phase.BUILD) return
        config.ballDistance = d
        config.clamp()
        // Les butées font partie du bras : déplacer le boulet, c'est refabriquer
        // la pièce entière, sinon le boulet glisse en laissant sa cuiller derrière.
        build()
    }

    /** Règle la hauteur de lâcher du contrepoids (phase de pose uniquement). */
    fun setDropHeight(h: Float) {
        if (phase != Phase.BUILD) return
        config.dropHeight = h
        config.clamp()
        placeCounterweight()
    }

    // ── Le tir ───────────────────────────────────────────────────────────────

    /** Lâche le contrepoids : le reste n'est plus que de la physique. */
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
        counterweight.inWorld = true
        world.clearImpacts()
        phase = Phase.DROP
    }

    /** Libère le cliquet : le bras est désormais libre de tourner sur son axe. */
    private fun releaseCatch() {
        beam.lockPosition = false
        beam.lockRotation = false
        beam.refreshMass()
        // Les impulsions mémorisées ont été calculées contre un bras infiniment
        // lourd : les réappliquer à un bras devenu mobile ferait n'importe quoi.
        world.forgetContacts(beam)
        pivot.reset()

        // Le bras attrape le contrepoids à l'endroit exact où il arrive : à partir
        // de là ils ne font plus qu'un, et toute la descente sert à lancer.
        cwHinge = RevoluteJoint.pin(counterweight, beam, counterweight.x, counterweight.y).also {
            world.addJoint(it)
        }

        phase = Phase.FLIGHT
    }

    fun step(dt: Float) {
        if (phase == Phase.BUILD || phase == Phase.RESULT) return

        world.stepFrame(dt)
        elapsed += dt

        if (phase == Phase.DROP) {
            // Le cliquet lâche juste AVANT que le contrepoids ne touche le bras,
            // et non au moment du choc. Si le contrepoids percute un bras encore
            // figé, il s'y enfonce ; en libérant le bras l'image d'après, la
            // correction d'interpénétration lui rend d'un coup une énergie qui ne
            // vient de nulle part. Au banc d'essai, tous les tirs ressortaient à
            // la même vitesse absurde de 21,6 m/s, quel que soit le contrepoids.
            val half = config.counterweightHalf
            val gap = (counterweight.y - half) - (pivotY + TrebuchetRules.BEAM_HALF_THICKNESS)
            val closing = -counterweight.vy * dt * 2f
            // Sécurité : si le contrepoids rate le bras, on libère quand même.
            if (gap <= maxOf(0.02f, closing) || elapsed > 2.5f) releaseCatch()
            return
        }

        val speed = hypot(ball.vx, ball.vy)
        if (speed > peakSpeed) peakSpeed = speed
        if (ball.y > peakHeight) peakHeight = ball.y
        // Vitesse de sortie : celle qu'a le boulet quand il quitte le bras, donc
        // la dernière mesurée tant qu'il monte encore au contact de la machine.
        if (!ballFree) {
            launchSpeed = speed
            // Le boulet est parti quand il s'est éloigné du creux qui le portait.
            // Rien ne le décide : on ne fait que constater.
            seatWorld(seatProbe)
            if (hypot(ball.x - seatProbe[0], ball.y - seatProbe[1]) > 0.7f) {
                ballFree = true
                // Le boulet a quitté la machine : il ne la revoit plus. Sans ça il
                // lui retombe dessus et se fait écraser entre le contrepoids et le
                // bras, ce qui l'expédiait à plusieurs milliers de mètres par seconde.
                ball.collidesWith = ball.collidesWith and MACHINE.inv()
                world.forgetContacts(ball)
                launchAngleDeg = Math.toDegrees(
                    kotlin.math.atan2(ball.vy.toDouble(), ball.vx.toDouble())
                ).toFloat()
            }
        }

        trailTimer += dt
        if (trailTimer > 0.02f && trail.size < 4000) {
            trailTimer = 0f
            trail.add(ball.x)
            trail.add(ball.y)
        }

        val landed = world.isAtRest() || elapsed > 14f || ball.x > TrebuchetRules.GROUND_RIGHT - 2f
        if (landed) finishShot()
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
     * machine au hasard dans le catalogue, on regarde où elle envoie le boulet,
     * et on pose la cible là. Le niveau est faisable par construction.
     */
    fun simulateShot(dt: Float = 1f / 120f): Float {
        release()
        var guard = 0
        while (phase != Phase.RESULT && guard < 4000) {
            step(dt)
            guard++
        }
        if (phase != Phase.RESULT) finishShot()
        return shotDistance
    }
}
