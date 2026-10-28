package com.Atom2Universe.app.games.papattes

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import com.Atom2Universe.app.games.physics.RevoluteJoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * La bestiole dans le moteur physique : un corps (tête comprise), deux cuisses, deux tibias.
 *
 * Toutes les articulations sont des servos : le moteur de chaque pivot est piloté vers un angle
 * cible avec un couple borné. Le jeu ne choisit que les angles ; la pesanteur, l'adhérence et
 * l'élan décident du reste. Le corps reçoit un couple de redressement (la bestiole garde la tête
 * haute), coupé dès qu'elle tombe.
 *
 * Angles relatifs : une cuisse à +θ part vers l'avant (vers +x), un genou à −θ plie vers l'arrière.
 *
 * La démarche dépend des jambes ([Gait]) : marche (balancier libre), saut (accroupi puis bond) ou
 * glisse (pieds sans adhérence ; la poussée et le freinage viennent du jeu, voir [PapattesGame]).
 */
class PapattesWalker(private val world: PhysWorld, val critter: Critter, startX: Float, groundY: Float) {

    inner class Leg(val thigh: PhysBody, val shin: PhysBody, val hip: RevoluteJoint, val knee: RevoluteJoint) {
        var hipTarget = 0f
        var kneeTarget = 0f
        val hipAngle get() = thigh.angle - body.angle
        val kneeAngle get() = shin.angle - thigh.angle
        private val out = FloatArray(2)
        /** Point d'appui sous le pied. */
        fun foot(): FloatArray { shin.localToWorld(shinFootX, shinFootY - legs.footRadius, out); return out }
        fun kneePoint(): FloatArray { shin.localToWorld(shinTopX, shinTopY, out); return out }
    }

    val legs = critter.legs
    /** Ce que touchent le corps et les jambes : le sol, et l'eau pour un corps qui flotte. */
    private val solidMask = if (critter.body.power == Power.FLOAT) CAT_GROUND or CAT_WATER else CAT_GROUND
    val body: PhysBody
    val front: Leg
    val back: Leg
    val all: List<Leg>

    /** Doigt posé : la jambe libre avance et la jambe d'appui pousse. */
    var pressed = false; private set
    var swing: Leg? = null; private set
    var fallen = false; private set
    /** Le jeu dit ici combien de pieds touchent le sol, avant chaque [drive]. */
    var feetDown = 2
    /** Durée de l'appui en cours : charge du bond, ou appui bref de la glisse. */
    var held = 0f; private set
    /** Charge du bond, de 0 à 1. */
    val charge: Float get() = if (legs.gait == Gait.HOP && pressed) (held / legs.chargeTime).coerceIn(0f, 1f) else 0f
    private var kickPhase = 0f
    /** Un bond ou un saut vient de partir : le jeu le lit (et le remet à faux) pour ses effets. */
    var jumped = false

    /** Corps : rayon du disque du torse et de la tête, et position des points d'accroche. */
    val bodyRadius = PapattesPose.bodyRadius(critter.body)
    val headRadius = .15f
    /** Le cou et la hanche, dans le repère du corps. */
    val neckX: Float
    val neckY: Float
    val hipX: Float
    val hipY: Float
    private var shinTopX = 0f
    private var shinTopY = 0f
    private var shinFootX = 0f
    private var shinFootY = 0f

    init {
        val spread = START_SPREAD
        val hipHeight = groundY + legs.length * cos(spread) + legs.footRadius + .01f
        val hipDrop = bodyRadius * .55f
        val torso = PhysBody.compound(critter.body.mass) {
            circle(bodyRadius, 0f, 0f)
            circle(headRadius, bodyRadius * .1f, bodyRadius + headRadius)
        }
        // Le corps composé est recentré sur son centre de masse : on retrouve où sont tombés le torse et la tête.
        val torsoX = torso.localOffsetX(0)
        val torsoY = torso.localOffsetY(0)
        neckX = torso.localOffsetX(1)
        neckY = torso.localOffsetY(1) - headRadius * .6f
        hipX = torsoX
        hipY = torsoY - hipDrop
        body = torso.apply {
            x = startX - hipX
            y = hipHeight - hipY
            category = CAT_BODY
            collidesWith = solidMask
            friction = .6f
            linearDamping = .02f
            angularDamping = .5f
        }
        world.add(body)
        front = buildLeg(startX, hipHeight, spread)
        back = buildLeg(startX, hipHeight, -spread)
        all = listOf(front, back)
        for (leg in all) { leg.hipTarget = leg.hipAngle; leg.kneeTarget = 0f }
    }

    private fun buildLeg(hx: Float, hy: Float, angle: Float): Leg {
        val s = sin(angle)
        val c = cos(angle)
        val thigh = PhysBody(.045f, legs.thigh / 2, legs.mass * .55f).apply {
            x = hx + s * legs.thigh / 2
            y = hy - c * legs.thigh / 2
            this.angle = angle
        }
        val kx = hx + s * legs.thigh
        val ky = hy - c * legs.thigh
        val shin = PhysBody.compound(legs.mass * .45f) {
            box(.035f, legs.shin / 2, 0f, 0f)
            circle(legs.footRadius, 0f, -legs.shin / 2)
        }
        shinTopX = shin.localOffsetX(0)
        shinTopY = shin.localOffsetY(0) + legs.shin / 2
        shinFootX = shin.localOffsetX(1)
        shinFootY = shin.localOffsetY(1)
        shin.angle = angle
        shin.x = kx - (shinTopX * c - shinTopY * s)
        shin.y = ky - (shinTopX * s + shinTopY * c)
        for (part in listOf(thigh, shin)) part.apply {
            category = CAT_LEG
            collidesWith = solidMask
            friction = legs.friction
            restitution = 0f
            linearDamping = .02f
            angularDamping = .5f
            world.add(this)
        }
        val hip = RevoluteJoint.pin(body, thigh, hx, hy)
        val knee = RevoluteJoint.pin(thigh, shin, kx, ky)
        world.addJoint(hip)
        world.addJoint(knee)
        return Leg(thigh, shin, hip, knee)
    }

    /** Le pied le plus en retrait : c'est lui qui part au prochain appui. */
    fun rearLeg(): Leg = if (front.foot()[0] <= back.foot()[0]) front else back

    fun press() {
        if (fallen || pressed) return
        pressed = true
        held = 0f
        if (legs.gait != Gait.WALK) return
        val swingLeg = rearLeg()
        swing = swingLeg
        swingLanded = false
        val stance = if (swingLeg === front) back else front
        swingLeg.hipTarget = swingLeg.hipAngle
        // Si le corps est encore en retrait du pied d'appui, la poussée commence par l'y ramener :
        // sinon lever le pied arrière le ferait tomber à la renverse.
        stance.hipTarget = minOf(stance.hipAngle, -swingLeg.hipAngle)
    }

    /** Hauteur des hanches au-dessus de la moyenne des deux pieds : trop basse, c'est le grand écart. */
    fun hipClearance(): Float {
        body.localToWorld(hipX, hipY, splitTmp)
        return splitTmp[1] - (front.foot()[1] + back.foot()[1]) / 2
    }
    private val splitTmp = FloatArray(2)

    fun release() {
        if (!pressed) return
        pressed = false
        when (legs.gait) {
            Gait.HOP -> { if (feetDown > 0 && held >= MIN_CHARGE) hop((held / legs.chargeTime).coerceIn(0f, 1f)); return }
            Gait.GLIDE -> { if (feetDown > 0 && held < TAP) ollie(); return }
            Gait.WALK -> Unit
        }
        // Le pied qui vient de partir reste où il est et redescend jusqu'au sol ; une fois posé, la
        // jambe d'appui finit de recentrer le corps (voir [drive]).
        val swingLeg = swing ?: return
        swingLeg.hipTarget = swingLeg.hipAngle.coerceIn(-STANCE_MAX, STANCE_MAX)
        for (leg in all) leg.kneeTarget = 0f
    }

    /** Applique les servos et le redressement ; à appeler avant chaque pas du monde. */
    fun drive(dt: Float) {
        if (fallen) return
        if (pressed) held += dt
        when (legs.gait) {
            Gait.WALK -> walkPose(dt)
            Gait.HOP -> hopPose()
            Gait.GLIDE -> glidePose(dt)
        }
        val torqueScale = if (pressed || legs.gait != Gait.WALK) 1f else HOLD_TORQUE
        for (leg in all) {
            servo(leg.hip, leg.hipAngle, leg.hipTarget, HIP_SPEED, legs.hipTorque * torqueScale)
            servo(leg.knee, leg.kneeAngle, leg.kneeTarget, KNEE_SPEED, legs.kneeTorque)
        }
        // Le bras de levier de la pesanteur grandit avec les jambes : le redressement aussi.
        val scale = body.mass * legs.length / REFERENCE_LENGTH
        val upright = -(UPRIGHT_KP * body.angle + UPRIGHT_KD * body.omega) * scale
        body.applyTorque(upright.coerceIn(-UPRIGHT_MAX * scale, UPRIGHT_MAX * scale))
    }

    private fun walkPose(dt: Float) {
        val swingLeg = swing
        if (swingLeg != null) {
            val stance = if (swingLeg === front) back else front
            if (pressed) {
                swingLeg.kneeTarget = -legs.kneeBend
                // Trop tenu, la jambe libre continue de monter : le coup de pied en l'air, puis la chute en avant.
                swingLeg.hipTarget = (swingLeg.hipTarget + legs.swingRate * dt).coerceAtMost(SWING_MAX)
                stance.kneeTarget = 0f
                stance.hipTarget = (stance.hipTarget - legs.pushRate * dt).coerceAtLeast(-STANCE_MAX)
            } else if (swingLanded) {
                // Une fois le pied posé, le compas se partage également entre les deux jambes : le corps
                // glisse au milieu des deux pieds.
                val half = (swingLeg.hipAngle - stance.hipAngle) / 2
                swingLeg.hipTarget = approach(swingLeg.hipTarget, half, legs.pushRate * dt)
                stance.hipTarget = approach(stance.hipTarget, -half, legs.pushRate * dt)
            }
        }
    }

    /**
     * Saut : accroupi tant qu'on tient (la cuisse avance de θ, le genou plie de 2θ : le pied reste
     * sous la hanche), jambes tendues vers l'avant en l'air pour se recevoir, « A » serré au repos.
     */
    private fun hopPose() {
        val theta = charge * legs.kneeBend * .5f
        when {
            pressed -> {
                front.hipTarget = HOP_SPREAD + theta
                back.hipTarget = -HOP_SPREAD + theta
                for (leg in all) leg.kneeTarget = -2f * theta
            }
            feetDown == 0 -> { front.hipTarget = .35f; back.hipTarget = .05f; front.kneeTarget = -.25f; back.kneeTarget = -.35f }
            else -> { front.hipTarget = HOP_SPREAD; back.hipTarget = -HOP_SPREAD; for (leg in all) leg.kneeTarget = 0f }
        }
    }

    /** Glisse : un « A » serré, genoux souples ; tant qu'on pousse, les jambes donnent tour à tour des coups vers l'arrière. */
    private fun glidePose(dt: Float) {
        val kick = if (pressed && feetDown > 0) {
            kickPhase += dt * KICK_RATE
            maxOf(0f, sin(kickPhase)) * .45f
        } else 0f
        val frontKicks = sin(kickPhase * .5f) > 0f
        front.hipTarget = GLIDE_SPREAD - if (frontKicks) kick else 0f
        back.hipTarget = -GLIDE_SPREAD - if (frontKicks) 0f else kick
        for (leg in all) leg.kneeTarget = -legs.kneeBend
    }

    /** Le bond : toute la bestiole part d'un coup, plus vite si elle a longtemps chargé. */
    private fun hop(charge: Float) {
        val boost = if (critter.body.power == Power.BOUNCE) BOUNCE_JUMP else 1f
        val v = legs.jumpSpeed * kotlin.math.sqrt(charge) * boost
        val a = Math.toRadians(legs.jumpAngle.toDouble()).toFloat()
        launch(v * cos(a), v * sin(a))
        for (leg in all) leg.kneeTarget = 0f
        jumped = true
    }

    /** Le petit saut de la glisse : on garde son élan, on décolle. */
    private fun ollie() {
        kick(0f, OLLIE_SPEED)
        jumped = true
    }

    /** Relance après un atterrissage (ressorts) : un bond proportionnel à la vitesse d'arrivée. */
    fun rebound(impact: Float) {
        if (fallen || pressed || legs.rebound <= 0f || impact < REBOUND_MIN) return
        val v = impact * legs.rebound
        val a = Math.toRadians(legs.jumpAngle.toDouble()).toFloat()
        kick(v * cos(a) * .5f, v * sin(a))
    }

    /** Toutes les pièces prennent la vitesse ([vx], [vy]). */
    fun launch(vx: Float, vy: Float) {
        for (part in parts) { part.vx = vx; part.vy = vy; part.wake() }
    }

    /** Ajoute ([dvx], [dvy]) à la vitesse de toutes les pièces. */
    fun kick(dvx: Float, dvy: Float) {
        for (part in parts) { part.vx += dvx; part.vy += dvy; part.wake() }
    }

    /** Masse totale : corps, tête et jambes. */
    val totalMass: Float get() = body.mass + 2 * legs.mass

    /** Le jeu dit ici si le pied qui vient de partir a retouché le sol. */
    var swingLanded = false

    private fun approach(value: Float, target: Float, step: Float) =
        if (value < target) minOf(target, value + step) else maxOf(target, value - step)

    private fun servo(joint: RevoluteJoint, angle: Float, target: Float, speed: Float, torque: Float) {
        joint.motorEnabled = true
        joint.motorSpeed = ((target - angle) * SERVO_GAIN).coerceIn(-speed, speed)
        joint.maxMotorTorque = torque
    }

    /** La bestiole s'effondre : plus de servos ni de redressement, elle tombe comme une poupée de chiffon. */
    fun collapse() {
        if (fallen) return
        fallen = true
        pressed = false
        for (leg in all) for (j in listOf(leg.hip, leg.knee)) {
            j.motorSpeed = 0f
            j.maxMotorTorque = 1.5f
        }
    }

    /** Position monde de la tête, dans [out]. */
    fun headCenter(out: FloatArray) = body.localToWorld(neckX, neckY + headRadius * .6f, out)
    fun hipPoint(out: FloatArray) = body.localToWorld(hipX, hipY, out)
    fun torsoCenter(out: FloatArray) = body.localToWorld(hipX, hipY + bodyRadius * .55f, out)

    fun remove() {
        world.removeOwned(this)
    }

    val parts: List<PhysBody> by lazy { listOf(body, front.thigh, front.shin, back.thigh, back.shin) }

    init {
        body.owner = this
        for (leg in all) { leg.thigh.owner = this; leg.shin.owner = this }
    }

    companion object {
        const val CAT_GROUND = 1
        const val CAT_BODY = 2
        const val CAT_LEG = 4
        /** L'eau portante, que seuls les corps qui flottent touchent. */
        const val CAT_WATER = 8
        const val START_SPREAD = .24f
        /** La jambe d'appui ne pousse pas au-delà ; la jambe libre, elle, peut monter jusqu'à [SWING_MAX]. */
        const val STANCE_MAX = 1.05f
        const val SWING_MAX = 1.8f
        const val HIP_SPEED = 7f
        const val KNEE_SPEED = 12f
        const val SERVO_GAIN = 14f
        /**
         * Part du couple des hanches gardée quand le doigt est levé : un « A » trop ouvert ne tient
         * plus et glisse en grand écart.
         */
        const val HOLD_TORQUE = .4f
        /**
         * Redressement par kilogramme de corps. Il doit l'emporter nettement sur le couple des hanches :
         * sinon la jambe d'appui fait basculer le corps en arrière au lieu de le pousser en avant.
         * Mais il reste plafonné : assez pour enjamber, pas assez pour tenir une bestiole trop penchée.
         * C'est ce plafond qui fait tomber celui qui garde le doigt posé trop longtemps.
         */
        const val UPRIGHT_KP = 120f
        const val UPRIGHT_KD = 7f
        const val UPRIGHT_MAX = 12f
        /** Longueur de jambe (échasses) pour laquelle les réglages de redressement valent tels quels. */
        const val REFERENCE_LENGTH = 1.1f
        /** Saut : en dessous de cette durée d'appui, rien ne part. */
        const val MIN_CHARGE = .08f
        const val HOP_SPREAD = .14f
        const val BOUNCE_JUMP = 1.12f
        const val REBOUND_MIN = 1.6f
        /** Glisse : un appui plus bref que [TAP] fait sauter au lâcher. */
        const val TAP = .2f
        const val OLLIE_SPEED = 3.4f
        const val GLIDE_SPREAD = .16f
        const val KICK_RATE = 9f
    }
}
