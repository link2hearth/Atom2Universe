package com.Atom2Universe.app.games.motocross

import kotlin.math.*

/** Châssis rigide, deux suspensions à ressort amorti, propulsion arrière seulement. */
internal class MotocrossBike {
    class Wheel {
        var x = 0f; var y = 0f
        var compression = 0f
        var spin = 0f
        var grounded = false
        var surfaceX = 0f; var surfaceY = 0f
        var normalX = 0f; var normalY = 1f
    }
    val rear = Wheel()
    val front = Wheel()
    var x = 3f; private set
    var y = 1f; private set
    var vx = 0f; private set
    var vy = 0f; private set
    var angle = 0f; private set
    var angularVelocity = 0f; private set
    var lean = 0f; private set
    var crashed = false; private set
    var impact = 0f; private set
    val headLocalX: Float get() = .12f + lean * .30f
    val headLocalY: Float get() = 1.05f - impact * .08f
    private val route = MotocrossTrack.RouteState()
    fun activeLoop(track: MotocrossTrack): MotocrossRoad? = track.activeLoop(route)
    private val contact = FloatArray(4)
    private var forceX = 0f
    private var forceY = 0f
    private var torque = 0f

    fun reset(atX: Float, track: MotocrossTrack) {
        x = atX; y = track.height(x) + .84f
        vx = 0f; vy = 0f; angle = 0f; angularVelocity = 0f; lean = 0f
        crashed = false; impact = 0f
        route.clear()
        rear.compression = 0f; front.compression = 0f
        rear.spin = 0f; front.spin = 0f
        rear.grounded = false; front.grounded = false
        placeWheel(rear, -HALF_BASE)
        placeWheel(front, HALF_BASE)
    }

    fun step(dt: Float, throttle: Boolean, brake: Boolean, leanInput: Float, track: MotocrossTrack) {
        if (crashed) return
        track.updateUnderpasses(x, y, route)
        lean += (leanInput - lean) * (1f - exp(-10f * dt))
        impact *= exp(-9f * dt)
        forceX = -vx * .13f
        forceY = -MASS * GRAVITY - vy * .035f
        torque = 0f
        suspension(rear, -HALF_BASE, true, dt, throttle, brake, track)
        suspension(front, HALF_BASE, false, dt, throttle, brake, track)
        val airborne = !rear.grounded && !front.grounded
        if (airborne) {
            // "lean" est déjà lissé (constante ~0,1 s) : une pression brève ne
            // déclenche donc plus un salto instantané. Relâcher amortit la rotation.
            val damping = if (leanInput == 0f) 2.4f else .35f
            torque += -lean * AIR_TORQUE - angularVelocity * damping
        } else {
            // Au sol (jamais en chute libre), le pilote qui se penche déplace son
            // centre de masse : la gravité, appliquée à côté de l'appui des roues,
            // crée un vrai couple de wheelie/stoppie. Rien d'inventé : c'est la
            // suspension (deux ressorts indépendants) qui tient déjà l'équilibre ;
            // se pencher ne fait que déplacer le point où la gravité tire.
            torque += -MASS * GRAVITY * cos(angle) * lean * RIDER_SHIFT - angularVelocity * .15f
        }
        vx += forceX / MASS * dt
        vy += forceY / MASS * dt
        angularVelocity = (angularVelocity + torque / INERTIA * dt)
            .coerceIn(-MAX_ROTATION_SPEED, MAX_ROTATION_SPEED)
        x += vx * dt
        y += vy * dt
        angle += angularVelocity * dt
        angle = atan2(sin(angle), cos(angle))
        if (x < 1f) { x = 1f; vx = max(0f, vx) }
        placeWheel(rear, -HALF_BASE)
        placeWheel(front, HALF_BASE)
        // Collision du casque et du cadre, pas de mort anticipée sur un angle seul.
        val ca = cos(angle); val sa = sin(angle)
        val headX = x + ca * headLocalX - sa * headLocalY
        val headY = y + sa * headLocalX + ca * headLocalY
        track.updateUnderpasses(x, y, route)
        if (track.hitsBody(headX, headY, .20f, route) ||
            track.hitsBody(x, y, .13f, route) || y < -12f ||
            !x.isFinite() || !y.isFinite() || !angle.isFinite()) crashed = true
    }

    private fun placeWheel(wheel: Wheel, localX: Float) {
        val length = REST - wheel.compression
        wheel.x = x + cos(angle) * localX + sin(angle) * length
        wheel.y = y + sin(angle) * localX - cos(angle) * length
    }

    private fun suspension(
        wheel: Wheel, localX: Float, driven: Boolean, dt: Float,
        throttle: Boolean, brake: Boolean, track: MotocrossTrack
    ) {
        val ca = cos(angle); val sa = sin(angle)
        val restX = x + ca * localX + sa * REST
        val restY = y + sa * localX - ca * REST
        track.contact(restX, restY, x + ca * localX, y + sa * localX,
            -sa, ca, route, contact)
        val nx = contact[2]; val ny = contact[3]
        wheel.surfaceX = contact[0]; wheel.surfaceY = contact[1]
        wheel.normalX = nx; wheel.normalY = ny
        val penetration = RADIUS - ((restX - contact[0]) * nx + (restY - contact[1]) * ny)
        val alignment = -sa * nx + ca * ny
        wheel.grounded = penetration > 0f && alignment > .2f
        wheel.compression = if (wheel.grounded) (penetration / alignment).coerceIn(0f, TRAVEL) else 0f
        placeWheel(wheel, localX)
        val rx = wheel.x - x
        val ry = wheel.y - y
        val pvx = vx - angularVelocity * ry
        val pvy = vy + angularVelocity * rx
        val tangentSpeed = pvx * ny - pvy * nx
        if (wheel.grounded) {
            val normalSpeed = pvx * nx + pvy * ny
            val load = (SPRING * wheel.compression - DAMPER * normalSpeed).coerceIn(0f, 160f)
            val grip = load * 2.2f
            // Conserver du couple dans les longues montées : l'ancienne courbe
            // perdait déjà la moitié de sa force à seulement 7,5 m/s.
            val speedRatio = (max(0f, tangentSpeed) / MOTOR_SPEED).coerceIn(0f, 1f)
            var traction = if (driven && throttle && !brake) ENGINE_FORCE * (1f - speedRatio * speedRatio) else 0f
            if (brake) traction = (-tangentSpeed * MASS / (2f * dt)).coerceIn(-36f, 36f)
            traction = (traction - tangentSpeed * .08f).coerceIn(-grip, grip)
            val fx = nx * load + ny * traction
            val fy = ny * load - nx * traction
            forceX += fx; forceY += fy
            torque += rx * fy - ry * fx
            impact = max(impact, (load / 95f).coerceIn(0f, 1f))
            // Butée : correction séparée, puis suppression de la vitesse entrante.
            val excess = penetration - TRAVEL * alignment
            if (excess > 0f) {
                x += nx * excess * .65f
                y += ny * excess * .65f
                if (normalSpeed < 0f) {
                    val arm = rx * ny - ry * nx
                    val impulse = -normalSpeed / (1f / MASS + arm * arm / INERTIA)
                    vx += nx * impulse / MASS
                    vy += ny * impulse / MASS
                    angularVelocity += arm * impulse / INERTIA
                }
            }
        }
        val spinSpeed = if (wheel.grounded) tangentSpeed / RADIUS
            else if (driven && throttle && !brake) 24f else if (brake) 0f else vx / RADIUS
        wheel.spin = (wheel.spin - spinSpeed * dt) % (2f * PI.toFloat())
    }

    companion object {
        const val RADIUS = .32f
        const val HALF_BASE = .76f
        const val REST = .62f
        const val TRAVEL = .36f
        private const val MASS = 1.8f
        private const val INERTIA = .9f
        private const val GRAVITY = 12f
        private const val SPRING = 115f
        private const val DAMPER = 9f
        private const val RIDER_SHIFT = .4f
        private const val ENGINE_FORCE = 36f
        private const val MOTOR_SPEED = 28f
        private const val AIR_TORQUE = 15f
        private const val MAX_ROTATION_SPEED = 6f
    }
}
