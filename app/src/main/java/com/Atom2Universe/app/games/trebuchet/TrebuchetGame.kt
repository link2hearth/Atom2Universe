package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import com.Atom2Universe.app.games.physics.RevoluteJoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

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

    const val GROUND_LEFT = -10f
    const val GROUND_RIGHT = 120f

    /** Longueurs de bras proposées, en mètres. */
    val BEAM_LENGTHS = floatArrayOf(2.4f, 3.2f, 4.2f)

    /** Un bras plus long est plus lourd : c'est ce qui crée l'optimum de rapport. */
    const val BEAM_DENSITY = 2.4f
    const val BEAM_HALF_THICKNESS = 0.06f

    /** Hauteurs de pied, donc du pivot. */
    val FOOT_HEIGHTS = floatArrayOf(1.1f, 1.7f, 2.4f)

    /** Crans du pied le long du bras, exprimés en rapport bras long / bras court. */
    val LEVER_RATIOS = floatArrayOf(1.5f, 2f, 3f, 4f)

    /** Contrepoids : léger, moyen, lourd. */
    val COUNTERWEIGHTS = floatArrayOf(25f, 50f, 90f)

    /** Hauteur de lâcher du contrepoids, au-dessus du bras court. */
    const val DROP_MIN = 0.3f
    const val DROP_MAX = 1.3f

    const val BALL_RADIUS = 0.13f
    const val BALL_MASS = 4f

    /**
     * La cuiller : deux rebords soudés au bras, qui enserrent le boulet.
     *
     * Un bras plat ne lance rien, et il en faut bien **deux**, parce que le boulet
     * est poussé successivement dans les deux sens :
     *  - au tout début, le bras s'incline et le boulet roule vers le pivot sous
     *    son propre poids — c'est le rebord arrière qui le retient ;
     *  - dès que le bras fouette, la force centrifuge (oméga²·r) l'emporte et
     *    chasse le boulet vers la pointe — c'est la butée avant qui le retient et
     *    qui l'emmène par-dessus, jusqu'au largage.
     *
     * Avec le seul rebord arrière, le banc d'essai renvoyait encore la moitié des
     * tirs derrière la machine. C'est pour ça qu'un mangonneau a une vraie cuiller
     * creuse, et pas une simple planche.
     */
    const val CUP_HALF_WIDTH = 0.04f

    /**
     * Jeu laissé au boulet entre les deux rebords. Sans lui l'écart vaut très
     * exactement son diamètre : le boulet est monté serré, le moindre tremblement
     * du solveur l'enfonce des deux côtés à la fois, et il finit par jaillir de la
     * cuiller à une vitesse absurde toujours identique.
     */
    const val CUP_CLEARANCE = 0.025f

    /**
     * Inclinaison de la cuiller vers le pivot, en degrés. C'est le réglage de
     * visée de la machine : une cuiller droite lâche le boulet perpendiculairement
     * au bras, une cuiller penchée en arrière le retient plus longtemps et le
     * relâche plus tendu. À énergie égale, ça change la portée du tout au tout.
     */
    val CUP_TILTS_DEG = intArrayOf(0, 12, 24, 36)

    /** Position du contrepoids sur le bras court, en fraction de sa longueur. */
    const val SEAT_POSITION = 0.75f
    /**
     * Hauteur des rebords. Ils doivent dépasser le boulet, sinon il sort de la
     * cuiller dès que le bras s'incline : une machine lente ne tourne pas assez
     * vite pour que la force centrifuge le plaque au fond, et il retombe avant
     * même d'avoir pris de la vitesse. Une cuiller creuse le retient jusqu'à
     * l'arrêtoir, et c'est l'arrêt du bras qui décide du départ.
     */
    const val CUP_HALF_HEIGHT = 0.2f
    const val CUP_MASS = 0.9f

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
     * On cherche l'arrêt le plus incliné qui tienne encore au-dessus du sol :
     * un pied plus haut autorise un arrêt plus tardif, donc un tir plus tendu.
     */
    val STOP_ANGLES_DEG = intArrayOf(-55, -50, -45, -40, -35, -30, -25, -20)
    const val STOP_RADIUS = 0.09f
    const val STOP_MIN_DISTANCE = 0.18f
    const val STOP_CLEARANCE = 0.08f

    /** Le boulet doit rester sur le bras long, sans chevaucher le pivot. */
    const val BALL_MIN_FROM_PIVOT = 0.45f

    const val GRAVITY = 9.81f

    /** Demi-côté du contrepoids, calculé pour que sa taille suive sa masse. */
    fun counterweightHalfSize(mass: Float): Float = 0.08f + 0.0022f * mass
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
    var cupTiltIndex = 1

    /** Hauteur de lâcher au-dessus du bras court. */
    var dropHeight = 0.7f

    /** Distance du boulet au pivot, sur le bras long. */
    var ballDistance = 1.5f

    val beamLength: Float get() = TrebuchetRules.BEAM_LENGTHS[beamIndex]
    val footHeight: Float get() = TrebuchetRules.FOOT_HEIGHTS[footIndex]
    val leverRatio: Float get() = TrebuchetRules.LEVER_RATIOS[ratioIndex]
    val counterweightMass: Float get() = TrebuchetRules.COUNTERWEIGHTS[weightIndex]
    val cupTiltDeg: Float get() = TrebuchetRules.CUP_TILTS_DEG[cupTiltIndex].toFloat()

    /**
     * Demi-écart entre les deux rebords de la cuiller.
     *
     * Il grandit avec l'inclinaison : une paroi penchée avance vers le boulet à
     * mesure qu'on monte, et à 36° elle finissait par le traverser dès la
     * construction — le solveur éjectait alors le boulet à 90 m/s.
     */
    val cupHalfGap: Float
        get() = TrebuchetRules.BALL_RADIUS + TrebuchetRules.CUP_HALF_WIDTH +
            TrebuchetRules.CUP_CLEARANCE +
            TrebuchetRules.BALL_RADIUS * tan(Math.toRadians(cupTiltDeg.toDouble()).toFloat())
    val beamMass: Float get() = beamLength * TrebuchetRules.BEAM_DENSITY

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
        cupTiltIndex = cupTiltIndex.coerceIn(0, TrebuchetRules.CUP_TILTS_DEG.size - 1)
        dropHeight = dropHeight.coerceIn(TrebuchetRules.DROP_MIN, TrebuchetRules.DROP_MAX)
        // Il faut laisser la place de la butée avant entre le boulet et la pointe.
        val room = longArm - cupHalfGap - TrebuchetRules.CUP_HALF_WIDTH
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
        cupTiltIndex = o.cupTiltIndex
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

    val world = PhysWorld()
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

    /** Rebord côté pivot : retient le boulet pendant que le bras s'incline. */
    lateinit var cupBack: PhysBody
        private set

    /** Butée au bout du bras : retient le boulet pendant toute l'ascension. */
    lateinit var cupFront: PhysBody
        private set

    /** Le tampon que le bras vient percuter : c'est lui qui libère le boulet. */
    lateinit var stopper: PhysBody
        private set

    /** Angle auquel le bras est arrêté, en degrés (négatif = bras long en l'air). */
    var stopAngleDeg = -45f
        private set

    private lateinit var pivot: RevoluteJoint
    private val cupJoints = ArrayList<RevoluteJoint>()

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
        cupJoints.clear()
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

        // Le bras : il déborde du pivot du côté long (vers les x négatifs).
        val beamCenter = pivotX + (config.shortArm - config.longArm) / 2f
        beam = PhysBody(config.beamLength / 2f, TrebuchetRules.BEAM_HALF_THICKNESS, config.beamMass).apply {
            x = beamCenter
            y = pivotY
            friction = 0.75f
            // Le cliquet : pendant la pose, le bras ne bouge pas du tout.
            lockPosition = true
            lockRotation = true
            refreshMass()
        }
        world.add(beam)

        pivot = RevoluteJoint.pin(beam, post, pivotX, pivotY)
        world.addJoint(pivot)

        cupBack = cradleWall(
            TrebuchetRules.CUP_HALF_WIDTH, TrebuchetRules.CUP_HALF_HEIGHT, TrebuchetRules.CUP_MASS
        )
        cupFront = cradleWall(
            TrebuchetRules.CUP_HALF_WIDTH, TrebuchetRules.CUP_HALF_HEIGHT, TrebuchetRules.CUP_MASS
        )
        world.add(cupBack)
        world.add(cupFront)

        ball = PhysBody.circle(TrebuchetRules.BALL_RADIUS, TrebuchetRules.BALL_MASS).apply {
            friction = 0.7f
            restitution = 0.18f
        }
        world.add(ball)
        placeBall()
        weldCup()

        val cwHalf = TrebuchetRules.counterweightHalfSize(config.counterweightMass)
        counterweight = PhysBody(cwHalf, cwHalf, config.counterweightMass).apply {
            friction = 0.8f
            // Tenu en l'air par le joueur : le moteur l'ignore jusqu'au lâcher.
            inWorld = false
        }
        world.add(counterweight)
        placeCounterweight()

        stopper = PhysBody.circle(TrebuchetRules.STOP_RADIUS, 0f).apply {
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
        for (deg in TrebuchetRules.STOP_ANGLES_DEG) {
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
        // Aucun angle ne tient : on pose le tampon au plus près du pivot.
        val a = Math.toRadians(TrebuchetRules.STOP_ANGLES_DEG.last().toDouble()).toFloat()
        stopAngleDeg = TrebuchetRules.STOP_ANGLES_DEG.last().toFloat()
        val d = TrebuchetRules.STOP_MIN_DISTANCE
        stopper.x = pivotX + d * cos(a) - (ht + rs) * -sin(a)
        stopper.y = pivotY + d * sin(a) - (ht + rs) * cos(a)
        world.forgetContacts(stopper)
    }

    /** Repose le boulet sur le bras long, à la distance choisie. */
    private fun placeBall() {
        ball.x = pivotX - config.ballDistance
        ball.y = pivotY + TrebuchetRules.BEAM_HALF_THICKNESS + TrebuchetRules.BALL_RADIUS
        ball.vx = 0f; ball.vy = 0f; ball.omega = 0f; ball.angle = 0f
        world.forgetContacts(ball)
    }

    private fun cradleWall(halfW: Float, halfH: Float, mass: Float) =
        PhysBody(halfW, halfH, mass).apply { friction = 0.85f }

    /**
     * Pose un berceau sur le dessus du bras : deux parois soudées, centrées sur
     * [centerX] et écartées de 2·[halfGap], penchées de [tiltDeg] vers le pivot.
     */
    private fun placeCradle(
        back: PhysBody,
        front: PhysBody,
        centerX: Float,
        halfGap: Float,
        tiltDeg: Float,
        joints: MutableList<RevoluteJoint>
    ) {
        val top = pivotY + TrebuchetRules.BEAM_HALF_THICKNESS
        // Le pivot est du côté des x positifs : pencher vers lui, c'est tourner
        // les parois dans le sens horaire, donc d'un angle négatif.
        val a = -Math.toRadians(tiltDeg.toDouble()).toFloat()
        placeCradleWall(back, centerX + halfGap, top, a, joints)
        placeCradleWall(front, centerX - halfGap, top, a, joints)
    }

    private fun placeCradleWall(
        wall: PhysBody,
        baseX: Float,
        baseY: Float,
        a: Float,
        joints: MutableList<RevoluteJoint>
    ) {
        val ux = cos(a); val uy = sin(a)      // axe « largeur » de la paroi
        val nx = -sin(a); val ny = cos(a)     // axe « hauteur » de la paroi
        wall.angle = a
        wall.x = baseX + wall.halfH * nx
        wall.y = baseY + wall.halfH * ny
        wall.vx = 0f; wall.vy = 0f; wall.omega = 0f
        world.forgetContacts(wall)
        // Deux points de soudure écartés : un seul laisserait la paroi pivoter.
        val (j1, j2) = RevoluteJoint.weld(
            wall, beam,
            baseX - wall.halfW * ux, baseY - wall.halfW * uy,
            baseX + wall.halfW * ux, baseY + wall.halfW * uy
        )
        world.addJoint(j1)
        world.addJoint(j2)
        joints.add(j1)
        joints.add(j2)
    }

    /**
     * Installe la cuiller autour du boulet et la soude au bras.
     *
     * Elle suit le boulet : régler la position du boulet, c'est en réalité choisir
     * où l'on installe la cuiller le long du bras.
     */
    private fun weldCup() {
        for (j in cupJoints) world.removeJoint(j)
        cupJoints.clear()
        placeCradle(cupBack, cupFront, ball.x, config.cupHalfGap, config.cupTiltDeg, cupJoints)
    }

    /** Abscisse où le contrepoids se pose sur le bras court. */
    private fun seatCenterX(): Float =
        pivotX + maxOf(0.22f, config.shortArm * TrebuchetRules.SEAT_POSITION)

    /** Suspend le contrepoids au-dessus de l'extrémité du bras court. */
    private fun placeCounterweight() {
        val half = TrebuchetRules.counterweightHalfSize(config.counterweightMass)
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

    /** Inclinaison de la cuiller : le réglage de visée de la machine. */
    fun cycleCupTilt(delta: Int) = changeSetting {
        config.cupTiltIndex = wrap(config.cupTiltIndex + delta, TrebuchetRules.CUP_TILTS_DEG.size)
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
        placeBall()
        weldCup()
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
        world.forgetContacts(cupBack)
        world.forgetContacts(cupFront)
        pivot.reset()
        for (j in cupJoints) j.reset()

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
            val half = TrebuchetRules.counterweightHalfSize(config.counterweightMass)
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
            if (ball.y > pivotY + 0.6f || ball.x > pivotX + 0.5f) ballFree = true
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
