package com.Atom2Universe.app.games.papattes

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Une étape en cours : le monde physique, le parcours et la bestiole.
 *
 * La simulation avance à pas fixe ([STEP]) quelle que soit la cadence de l'écran : à 30 ou à
 * 120 images par seconde, le même geste donne la même démarche.
 *
 * Le sol n'est pas seulement le parcours fixe : des planches cèdent, des bascules penchent, les
 * chapeaux de champignon portent, et l'eau porte les corps qui flottent. [groundAt] réunit tout ça.
 */
class PapattesGame(val stage: Int, val critter: Critter) {
    enum class Phase { WALKING, FALLEN, FINISHED }
    enum class Event { STEP, CHECKPOINT, FALL, SPLASH, RESPAWN, FINISH, JUMP, LAND, BOUNCE, CRUMBLE, GOLD, SAVED }

    val course = PapattesCourse(stage, critter.legs)
    val world = PhysWorld().apply {
        linearDamping = .02f
        angularDamping = .3f
    }
    var walker: PapattesWalker; private set
    var phase = Phase.WALKING; private set
    var falls = 0; private set
    /** Durée de marche, chutes comprises. */
    var elapsed = 0f; private set
    var checkpointReached = false; private set
    /** Abscisse la plus avancée atteinte par le torse. */
    var progressX = course.startX; private set
    val events = ArrayList<Event>()

    val goldTaken = BooleanArray(course.goldSeeds.size)
    val goldCount: Int get() = goldTaken.count { it }
    /** La dernière graine d'or ramassée, pour ses étincelles. */
    var lastGold = -1; private set

    /** Planches : temps depuis le premier pied posé (−1 = jamais), et temps depuis qu'elles ont cédé (−1 = en place). */
    val plankTimer = FloatArray(course.segments.size) { -1f }
    val plankFall = FloatArray(course.segments.size) { -1f }
    private val plankBodies = arrayOfNulls<PhysBody>(course.segments.size)
    val seesawBodies: List<PhysBody>
    private var accumulator = 0f
    private var respawnTimer = 0f
    private var bounceCooldown = 0f
    private var savedCooldown = 0f
    private val grounded = BooleanArray(2)
    private var feetDown = 2
    private var fallingSpeed = 0f
    private val tmp = FloatArray(2)

    init {
        for ((i, s) in course.segments.withIndex()) {
            val body = groundBody(s)
            world.add(body)
            if (s.floater == Floater.PLANK) plankBodies[i] = body
        }
        seesawBodies = course.seesaws.map { buildSeesaw(it) }
        for (m in course.mushrooms) {
            world.add(PhysBody.circle(m.radius, 0f).apply { x = m.x; y = m.y; ground(.9f) })
            val stem = (m.y - m.groundY) / 2
            world.add(PhysBody(.07f, stem, 0f).apply { x = m.x; y = m.groundY + stem; ground(.9f) })
        }
        if (critter.body.power == Power.FLOAT) for (w in course.waters) {
            // Le plancher invisible de l'eau, que seuls les corps qui flottent touchent.
            world.add(PhysBody((w.x1 - w.x0) / 2 + .02f, GROUND_DEPTH, 0f).apply {
                x = (w.x0 + w.x1) / 2
                y = w.level + PapattesCourse.FLOAT_LIFT - GROUND_DEPTH
                friction = Surface.WATER.friction
                category = PapattesWalker.CAT_WATER
            })
        }
        walker = spawn(course.startX)
        for (i in 0..1) grounded[i] = true
    }

    private fun PhysBody.ground(friction: Float) {
        this.friction = friction
        restitution = 0f
        category = PapattesWalker.CAT_GROUND
    }

    private fun groundBody(s: Segment): PhysBody {
        val length = hypot(s.x1 - s.x0, s.y1 - s.y0)
        val angle = atan2(s.y1 - s.y0, s.x1 - s.x0)
        val half = if (s.floating) LOG_DEPTH else GROUND_DEPTH
        // Le dessus du bloc épouse le segment ; les blocs voisins se chevauchent un peu pour que les pieds ne butent pas.
        return PhysBody(length / 2 + if (s.floater == Floater.PLANK) 0f else .02f, half, 0f).apply {
            this.angle = angle
            x = (s.x0 + s.x1) / 2 + sin(angle) * half
            y = (s.y0 + s.y1) / 2 - cos(angle) * half
            ground(s.surface.friction)
            tag = s
        }
    }

    /**
     * Une bascule est une planche immobile pour le moteur, que le jeu fait pencher lui-même : vers
     * le côté où se trouve le corps de la bestiole, à vitesse fixe. Libre sur son pivot, elle restait
     * dressée côté arrivée sous les longues jambes (qui chevauchent le pivot), et la bestiole
     * tombait en descendant la marche.
     */
    private fun buildSeesaw(s: Seesaw): PhysBody {
        val plank = PhysBody(s.halfLength, PapattesCourse.SEESAW_THICKNESS / 2, 0f).apply { ground(Surface.WOOD.friction) }
        placeSeesaw(plank, s)
        world.add(plank)
        return plank
    }

    private fun placeSeesaw(plank: PhysBody, s: Seesaw) {
        plank.x = s.pivotX; plank.y = s.pivotY; plank.angle = s.restAngle; plank.omega = 0f
    }

    private fun tiltSeesaws() {
        walker.torsoCenter(tmp)
        for ((i, s) in course.seesaws.withIndex()) {
            val plank = seesawBodies[i]
            val target = if (tmp[0] < s.pivotX) s.restAngle else -s.restAngle
            val step = (target - plank.angle).coerceIn(-SEESAW_RATE * STEP, SEESAW_RATE * STEP)
            plank.angle += step
            // Le moteur ne déplace pas un corps immobile, mais lit sa vitesse dans les contacts.
            plank.omega = step / STEP
        }
    }

    private fun spawn(x: Float): PapattesWalker {
        // Le sol sous les deux pieds de départ : on se pose sur le plus haut.
        var ground = course.surfaceAt(x)
        for (dx in listOf(-.4f, .4f)) ground = maxOf(ground, course.surfaceAt(x + dx))
        return PapattesWalker(world, critter, x, ground)
    }

    fun press() { if (phase == Phase.WALKING) walker.press() }
    fun release() { walker.release() }

    fun update(dt: Float) {
        events.clear()
        if (phase == Phase.FINISHED) return
        accumulator = (accumulator + dt).coerceAtMost(STEP * MAX_STEPS_PER_FRAME)
        while (accumulator >= STEP) {
            accumulator -= STEP
            step()
        }
    }

    private fun step() {
        elapsed += STEP
        bounceCooldown -= STEP
        savedCooldown -= STEP
        for (i in plankFall.indices) if (plankFall[i] >= 0f) plankFall[i] += STEP
        if (phase == Phase.FALLEN) {
            respawnTimer -= STEP
            if (respawnTimer <= 0f) return respawn()
        }
        walker.feetDown = feetDown
        walker.drive(STEP)
        if (phase == Phase.WALKING) applyForces()
        tiltSeesaws()
        world.stepFrame(STEP)
        if (walker.jumped) { walker.jumped = false; events += Event.JUMP }
        crumble()
        if (phase != Phase.WALKING) return

        walker.torsoCenter(tmp)
        val cx = tmp[0]
        val cy = tmp[1]
        progressX = maxOf(progressX, cx)
        val r = walker.bodyRadius
        if (cy - r < groundAt(cx) + TOUCH || headDown()) {
            if (!save()) return fall(Event.FALL)
        }
        if (walker.hipClearance() < SPLITS * critter.legs.length) return fall(Event.FALL)
        if (submerged(cx, cy - r)) return fall(Event.SPLASH)
        var down = 0
        for ((i, leg) in walker.all.withIndex()) {
            val foot = leg.foot()
            if (submerged(foot[0], foot[1])) return fall(Event.SPLASH)
            val touching = foot[1] - groundAt(foot[0]) < FOOT_CONTACT
            if (touching && !grounded[i]) events += Event.STEP
            grounded[i] = touching
            if (touching) { down++; touchPlank(foot[0]) }
            if (touching && !walker.pressed && leg === walker.swing) walker.swingLanded = true
        }
        if (down == 0) fallingSpeed = maxOf(fallingSpeed, -walker.body.vy)
        else if (feetDown == 0) {
            events += Event.LAND
            // Les jambes de saut amortissent la réception : sans ça, un long bond finit sur le nez.
            if (critter.legs.gait == Gait.HOP) walker.kick(-walker.body.vx * LANDING_DAMPING, 0f)
            walker.rebound(fallingSpeed)
            fallingSpeed = 0f
        }
        feetDown = down
        mushroomBounce()
        collect(cx)
        if (!checkpointReached && course.checkpointX > 0f && cx >= course.checkpointX) {
            checkpointReached = true
            events += Event.CHECKPOINT
        }
        if (cx >= course.finishX) {
            phase = Phase.FINISHED
            walker.release()
            events += Event.FINISH
        }
    }

    /** Vent, poussée de la glisse, vol plané : les forces que le jeu ajoute à la physique. */
    private fun applyForces() {
        val body = walker.body
        val mass = walker.totalMass
        course.windAt(body.x)?.let { wind ->
            val factor = when {
                critter.body.power == Power.ANCHOR -> ANCHOR_WIND
                critter.body.weight == Weight.LIGHT -> LIGHT_WIND
                else -> 1f
            }
            body.applyForce(wind.at(elapsed) * mass * factor, 0f)
        }
        // La poussée de la glisse et le vol plané sont répartis sur toutes les pièces selon leur masse :
        // appliqués au seul torse, ils faisaient basculer la bestiole en avant.
        val legs = critter.legs
        if (legs.gait == Gait.GLIDE && feetDown > 0) {
            val vmax = legs.glideSpeed(surfaceUnderFeet())
            val v = body.vx
            accelerate(if (walker.pressed) GLIDE_PUSH * (1f - v / vmax) else -GLIDE_ROLL * v / vmax, 0f)
        }
        if (critter.body.power == Power.GLIDE && feetDown == 0 && body.vy < -GLIDE_FALL) {
            accelerate(0f, (GRAVITY + (-body.vy - GLIDE_FALL) * GLIDE_BRAKE).coerceAtMost(GRAVITY * 1.6f))
        }
    }

    /** La même accélération pour toutes les pièces : la bestiole avance sans tourner. */
    private fun accelerate(ax: Float, ay: Float) {
        for (part in walker.parts) part.applyForce(ax * part.mass, ay * part.mass)
    }

    private fun surfaceUnderFeet(): Surface {
        val x = (walker.front.foot()[0] + walker.back.foot()[0]) / 2
        val seg = segmentUnder(x)
        if (seg != null) return seg.surface
        return if (critter.body.power == Power.FLOAT && course.waterAt(x) != null) Surface.WATER else Surface.WOOD
    }

    /** Rebondit : un choc du corps relance la bestiole au lieu de la faire tomber, une fois de temps en temps. */
    private fun save(): Boolean {
        if (critter.body.power != Power.BOUNCE || savedCooldown > 0f) return false
        savedCooldown = SAVE_COOLDOWN
        walker.kick(0f, SAVE_SPEED)
        walker.body.omega *= .2f
        events += Event.SAVED
        return true
    }

    private fun touchPlank(x: Float) {
        val seg = segmentUnder(x) ?: return
        if (seg.floater != Floater.PLANK) return
        val i = course.segments.indexOf(seg)
        if (plankTimer[i] < 0f) plankTimer[i] = 0f
    }

    private fun crumble() {
        for (i in plankTimer.indices) {
            if (plankTimer[i] < 0f || plankFall[i] >= 0f) continue
            plankTimer[i] += STEP
            if (plankTimer[i] >= CRUMBLE_DELAY) {
                plankBodies[i]?.let { world.remove(it) }
                plankFall[i] = 0f
                events += Event.CRUMBLE
            }
        }
    }

    private fun mushroomBounce() {
        if (bounceCooldown > 0f) return
        for (m in course.mushrooms) for (leg in walker.all) {
            // Le moindre contact avec le champignon, chapeau ou pied, renvoie la bestiole en l'air.
            val foot = leg.foot()
            val margin = critter.legs.footRadius + .08f
            if (foot[0] > m.x - m.radius - margin && foot[0] < m.x + m.radius + margin && foot[1] < m.y + m.radius + margin) {
                val boost = if (critter.body.power == Power.BOUNCE) BOUNCE_MUSHROOM else 1f
                walker.launch(maxOf(walker.body.vx, MUSHROOM_VX), MUSHROOM_VY * boost)
                bounceCooldown = .6f
                events += Event.BOUNCE
                return
            }
        }
    }

    private fun collect(cx: Float) {
        val points = ArrayList<FloatArray>(6)
        walker.torsoCenter(tmp); points += tmp.copyOf()
        walker.headCenter(tmp); points += tmp.copyOf()
        for (leg in walker.all) { points += leg.foot().copyOf(); points += leg.kneePoint().copyOf() }
        for ((i, g) in course.goldSeeds.withIndex()) {
            if (goldTaken[i]) continue
            if (points.any { hypot(it[0] - g.x, it[1] - g.y) < GOLD_PICK }) {
                goldTaken[i] = true
                lastGold = i
                events += Event.GOLD
            }
        }
    }

    private fun respawn() {
        walker.remove()
        for (i in plankTimer.indices) {
            plankTimer[i] = -1f
            if (plankFall[i] >= 0f) { plankBodies[i]?.let { world.add(it) }; plankFall[i] = -1f }
        }
        for ((i, s) in course.seesaws.withIndex()) placeSeesaw(seesawBodies[i], s)
        walker = spawn(if (checkpointReached) course.checkpointX else course.startX)
        phase = Phase.WALKING
        accumulator = 0f
        feetDown = 2
        fallingSpeed = 0f
        for (i in 0..1) grounded[i] = true
        events += Event.RESPAWN
    }

    private fun headDown(): Boolean {
        walker.headCenter(tmp)
        return tmp[1] - walker.headRadius < groundAt(tmp[0]) + TOUCH
    }

    private fun submerged(x: Float, y: Float): Boolean {
        val water = course.waterAt(x) ?: return false
        val depth = if (critter.body.power == Power.FLOAT) FLOAT_DEPTH else 0f
        return y < water.level - depth && groundAt(x) < y
    }

    private fun fall(event: Event) {
        phase = Phase.FALLEN
        falls++
        walker.collapse()
        respawnTimer = RESPAWN_DELAY
        events += event
    }

    /** Le segment fixe (planches tombées exclues) le plus haut sous [x]. */
    private fun segmentUnder(x: Float): Segment? {
        var best: Segment? = null
        var top = Float.NEGATIVE_INFINITY
        for ((i, s) in course.segments.withIndex()) {
            if (x < s.x0 || x > s.x1 || plankFall[i] >= 0f) continue
            val y = s.yAt(x)
            if (y > top) { top = y; best = s }
        }
        return best
    }

    /**
     * Le dessus le plus haut à l'abscisse [x] : sol fixe et planches encore en place, bascules,
     * chapeaux de champignon, et l'eau pour un corps qui flotte ; −∞ s'il n'y a rien.
     */
    fun groundAt(x: Float): Float {
        var best = segmentUnder(x)?.yAt(x) ?: Float.NEGATIVE_INFINITY
        for ((i, s) in course.seesaws.withIndex()) {
            val p = seesawBodies[i]
            val c = cos(p.angle)
            val sn = sin(p.angle)
            val half = PapattesCourse.SEESAW_THICKNESS / 2
            val u = (x - p.x + half * sn) / c
            if (u >= -s.halfLength && u <= s.halfLength) best = maxOf(best, p.y + u * sn + half * c)
        }
        for (m in course.mushrooms) {
            val dx = x - m.x
            if (dx >= -m.radius && dx <= m.radius) best = maxOf(best, m.y + sqrt(m.radius * m.radius - dx * dx))
        }
        if (critter.body.power == Power.FLOAT) course.waterAt(x)?.let { best = maxOf(best, it.level + PapattesCourse.FLOAT_LIFT) }
        return best
    }

    /** Le pied au sol, pour l'aide de départ et le robot des tests. */
    fun footGrounded(leg: PapattesWalker.Leg): Boolean {
        val foot = leg.foot()
        return foot[1] - groundAt(foot[0]) < FOOT_CONTACT
    }

    /** Pieds au sol au dernier pas. */
    val feetOnGround: Int get() = feetDown

    /** Avancement de 0 (départ) à 1 (drapeau). */
    val progress: Float get() = ((progressX - course.startX) / (course.finishX - course.startX)).coerceIn(0f, 1f)

    /** Les étoiles gagnées, une fois l'étape finie : arrivée, toutes les graines d'or, aucune chute. */
    val stars: Int get() = if (phase != Phase.FINISHED) 0 else
        STAR_FINISH or (if (goldCount == goldTaken.size) STAR_GOLD else 0) or (if (falls == 0) STAR_CLEAN else 0)

    companion object {
        const val STEP = 1f / 120f
        const val MAX_STEPS_PER_FRAME = 8
        const val GROUND_DEPTH = .6f
        const val LOG_DEPTH = .35f
        const val TOUCH = .03f
        const val FOOT_CONTACT = .035f
        const val RESPAWN_DELAY = 1.3f
        /** Hanches plus basses que cette part de la longueur des jambes : grand écart, chute. */
        const val SPLITS = .55f
        const val GRAVITY = 9.81f
        /** Une planche cède ce temps-là après le premier pied posé. */
        const val CRUMBLE_DELAY = 1.6f
        /** Part de la vitesse horizontale amortie à la réception d'un bond. */
        const val LANDING_DAMPING = .5f
        /** Vitesse de bascule des planches, en rad/s. */
        const val SEESAW_RATE = 1.1f
        const val MUSHROOM_VY = 5.6f
        const val MUSHROOM_VX = 1.8f
        const val BOUNCE_MUSHROOM = 1.12f
        /** Part du vent ressentie par un corps qui s'ancre, et par un corps léger. */
        const val ANCHOR_WIND = .25f
        const val LIGHT_WIND = 1.25f
        /** Glisse : accélération en poussant (m/s²) et freinage en roue libre à pleine vitesse. */
        const val GLIDE_PUSH = 4f
        const val GLIDE_ROLL = .9f
        /** Vol plané : vitesse de chute au-delà de laquelle le corps freine, et la fermeté du freinage. */
        const val GLIDE_FALL = 1.1f
        const val GLIDE_BRAKE = 6f
        /** Rebondit : élan rendu et délai entre deux sauvetages. */
        const val SAVE_SPEED = 3.6f
        const val SAVE_COOLDOWN = 1.5f
        /** Un corps qui flotte ne coule qu'enfoncé de plus que ça sous la surface. */
        const val FLOAT_DEPTH = .35f
        const val GOLD_PICK = .32f
        const val STAR_FINISH = 1
        const val STAR_GOLD = 2
        const val STAR_CLEAN = 4
    }
}
