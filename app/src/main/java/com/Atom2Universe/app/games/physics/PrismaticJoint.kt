package com.Atom2Universe.app.games.physics

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Glissière : [b] peut coulisser le long d'un axe porté par [a], sans mouvement
 * transversal ni rotation relative.
 *
 * C'est la liaison d'un piston, d'une culasse mobile, d'une presse ou d'un chariot.
 * Elle accepte des butées de course et un moteur linéaire limité en force. Toutes les
 * longueurs sont en mètres ; une course de 0,02 m ou de 2 000 m suit le même calcul.
 */
class PrismaticJoint(a: PhysBody, b: PhysBody) : Joint(a, b) {
    var localAnchorAX = 0f
    var localAnchorAY = 0f
    var localAnchorBX = 0f
    var localAnchorBY = 0f

    /** Axe de translation dans le repère de A. Il est normalisé à chaque pas. */
    var localAxisAX = 1f
    var localAxisAY = 0f

    var limitsEnabled = false
    var lowerTranslation = 0f
    var upperTranslation = 1f

    var motorEnabled = false
    var motorSpeed = 0f
    var maxMotorForce = 0f

    var translation = 0f
        private set
    var motorForce = 0f
        private set
    var atLowerLimit = false
        private set
    var atUpperLimit = false
        private set

    private var referenceAngle = b.angle - a.angle
    private var axisX = 1f
    private var axisY = 0f
    private var perpX = 0f
    private var perpY = 1f
    private var rax = 0f
    private var ray = 0f
    private var rbx = 0f
    private var rby = 0f
    private var transverseError = 0f
    private var angleError = 0f
    private var jaPerp = 0f
    private var jbPerp = 0f
    private var jaAxis = 0f
    private var jbAxis = 0f
    private var massPerp = 0f
    private var massAxis = 0f
    private var massAngle = 0f
    private var transverseImpulse = 0f
    private var angleImpulse = 0f
    private var motorImpulse = 0f
    private var limitImpulse = 0f
    private var posTransverseImpulse = 0f
    private var posAngleImpulse = 0f
    private var posLimitImpulse = 0f
    private var maxMotorImpulse = 0f
    private var posScale = 0f
    private var lastInvDt = 0f
    private var solvable = false

    /** Reprend l'angle relatif actuel comme orientation nominale de la glissière. */
    fun captureReferenceAngle() {
        referenceAngle = b.angle - a.angle
        reset()
    }

    fun setWorldAnchorsAndAxis(ax: Float, ay: Float, bx: Float, by: Float, worldAxisX: Float, worldAxisY: Float) {
        val axisLength = hypot(worldAxisX, worldAxisY)
        require(axisLength > 1e-6f) { "l'axe de glissière ne peut pas être nul" }

        val ca = cos(a.angle); val sa = sin(a.angle)
        val daX = ax - a.x; val daY = ay - a.y
        localAnchorAX = daX * ca + daY * sa
        localAnchorAY = -daX * sa + daY * ca
        localAxisAX = (worldAxisX * ca + worldAxisY * sa) / axisLength
        localAxisAY = (-worldAxisX * sa + worldAxisY * ca) / axisLength

        val cb = cos(b.angle); val sb = sin(b.angle)
        val dbX = bx - b.x; val dbY = by - b.y
        localAnchorBX = dbX * cb + dbY * sb
        localAnchorBY = -dbX * sb + dbY * cb
        captureReferenceAngle()
    }

    override fun reset() {
        transverseImpulse = 0f
        angleImpulse = 0f
        motorImpulse = 0f
        limitImpulse = 0f
        posTransverseImpulse = 0f
        posAngleImpulse = 0f
        posLimitImpulse = 0f
        motorForce = 0f
    }

    override fun preStep(invDt: Float) {
        solvable = false
        motorForce = 0f
        posTransverseImpulse = 0f
        posAngleImpulse = 0f
        posLimitImpulse = 0f
        if (!enabled || !a.inWorld || !b.inWorld) return

        val ca = cos(a.angle); val sa = sin(a.angle)
        rax = localAnchorAX * ca - localAnchorAY * sa
        ray = localAnchorAX * sa + localAnchorAY * ca
        val rawAxisX = localAxisAX * ca - localAxisAY * sa
        val rawAxisY = localAxisAX * sa + localAxisAY * ca
        val axisLength = hypot(rawAxisX, rawAxisY)
        if (axisLength < 1e-6f) return
        axisX = rawAxisX / axisLength
        axisY = rawAxisY / axisLength
        perpX = -axisY
        perpY = axisX

        val cb = cos(b.angle); val sb = sin(b.angle)
        rbx = localAnchorBX * cb - localAnchorBY * sb
        rby = localAnchorBX * sb + localAnchorBY * cb
        val dx = (b.x + rbx) - (a.x + rax)
        val dy = (b.y + rby) - (a.y + ray)
        translation = dx * axisX + dy * axisY
        transverseError = dx * perpX + dy * perpY
        angleError = (b.angle - a.angle) - referenceAngle

        // Jacobiennes incluant la rotation de l'axe porté par A.
        jaPerp = -(rax * perpY - ray * perpX) - translation
        jbPerp = rbx * perpY - rby * perpX
        jaAxis = -(rax * axisY - ray * axisX) + transverseError
        jbAxis = rbx * axisY - rby * axisX

        val kPerp = a.invMass + b.invMass + a.invI * jaPerp * jaPerp + b.invI * jbPerp * jbPerp
        val kAxis = a.invMass + b.invMass + a.invI * jaAxis * jaAxis + b.invI * jbAxis * jbAxis
        val kAngle = a.invI + b.invI
        massPerp = if (kPerp > 1e-12f) 1f / kPerp else 0f
        massAxis = if (kAxis > 1e-12f) 1f / kAxis else 0f
        massAngle = if (kAngle > 1e-12f) 1f / kAngle else 0f
        if (massPerp == 0f && massAxis == 0f && massAngle == 0f) return

        atLowerLimit = limitsEnabled && translation <= lowerTranslation
        atUpperLimit = limitsEnabled && translation >= upperTranslation
        if (!atLowerLimit && !atUpperLimit) limitImpulse = 0f
        maxMotorImpulse = maxMotorForce.coerceAtLeast(0f) / invDt
        if (!motorEnabled) motorImpulse = 0f
        posScale = 0.35f * invDt
        lastInvDt = invDt

        applyRealPerp(transverseImpulse)
        a.omega -= a.invI * angleImpulse
        b.omega += b.invI * angleImpulse
        applyRealAxis(motorImpulse + limitImpulse)
        solvable = true
    }

    override fun applyImpulse() {
        if (!solvable) return

        var cDot = relativeVelocityX() * perpX + relativeVelocityY() * perpY - a.omega * translation
        var dP = -massPerp * cDot
        transverseImpulse += dP
        applyRealPerp(dP)

        cDot = b.omega - a.omega
        dP = -massAngle * cDot
        angleImpulse += dP
        a.omega -= a.invI * dP
        b.omega += b.invI * dP

        val axialSpeed = relativeVelocityX() * axisX + relativeVelocityY() * axisY +
            a.omega * transverseError
        if (motorEnabled && maxMotorImpulse > 0f) {
            dP = massAxis * (motorSpeed - axialSpeed)
            val old = motorImpulse
            motorImpulse = (old + dP).coerceIn(-maxMotorImpulse, maxMotorImpulse)
            dP = motorImpulse - old
            applyRealAxis(dP)
            motorForce = motorImpulse * lastInvDt
        }

        if (atLowerLimit && axialSpeed < 0f) {
            dP = -massAxis * axialSpeed
            val old = limitImpulse
            limitImpulse = maxOf(old + dP, 0f)
            applyRealAxis(limitImpulse - old)
        } else if (atUpperLimit && axialSpeed > 0f) {
            dP = -massAxis * axialSpeed
            val old = limitImpulse
            limitImpulse = minOf(old + dP, 0f)
            applyRealAxis(limitImpulse - old)
        }
    }

    override fun applyPositionImpulse() {
        if (!solvable) return

        var cDot = relativePseudoX() * perpX + relativePseudoY() * perpY - a.pomega * translation
        var dP = -massPerp * (transverseError * posScale + cDot)
        posTransverseImpulse += dP
        applyPseudoPerp(dP)

        cDot = b.pomega - a.pomega
        dP = -massAngle * (angleError * posScale + cDot)
        posAngleImpulse += dP
        a.pomega -= a.invI * dP
        b.pomega += b.invI * dP

        val limitError = when {
            limitsEnabled && translation < lowerTranslation -> translation - lowerTranslation
            limitsEnabled && translation > upperTranslation -> translation - upperTranslation
            else -> 0f
        }
        if (limitError != 0f) {
            cDot = relativePseudoX() * axisX + relativePseudoY() * axisY +
                a.pomega * transverseError
            dP = -massAxis * (limitError * posScale + cDot)
            val old = posLimitImpulse
            posLimitImpulse = if (limitError < 0f) maxOf(old + dP, 0f) else minOf(old + dP, 0f)
            applyPseudoAxis(posLimitImpulse - old)
        }
    }

    private fun relativeVelocityX() = (b.vx - b.omega * rby) - (a.vx - a.omega * ray)
    private fun relativeVelocityY() = (b.vy + b.omega * rbx) - (a.vy + a.omega * rax)
    private fun relativePseudoX() = (b.pvx - b.pomega * rby) - (a.pvx - a.pomega * ray)
    private fun relativePseudoY() = (b.pvy + b.pomega * rbx) - (a.pvy + a.pomega * rax)

    private fun applyRealPerp(p: Float) {
        a.vx -= a.invMass * perpX * p; a.vy -= a.invMass * perpY * p
        b.vx += b.invMass * perpX * p; b.vy += b.invMass * perpY * p
        a.omega += a.invI * jaPerp * p; b.omega += b.invI * jbPerp * p
    }

    private fun applyRealAxis(p: Float) {
        a.vx -= a.invMass * axisX * p; a.vy -= a.invMass * axisY * p
        b.vx += b.invMass * axisX * p; b.vy += b.invMass * axisY * p
        a.omega += a.invI * jaAxis * p; b.omega += b.invI * jbAxis * p
    }

    private fun applyPseudoPerp(p: Float) {
        a.pvx -= a.invMass * perpX * p; a.pvy -= a.invMass * perpY * p
        b.pvx += b.invMass * perpX * p; b.pvy += b.invMass * perpY * p
        a.pomega += a.invI * jaPerp * p; b.pomega += b.invI * jbPerp * p
    }

    private fun applyPseudoAxis(p: Float) {
        a.pvx -= a.invMass * axisX * p; a.pvy -= a.invMass * axisY * p
        b.pvx += b.invMass * axisX * p; b.pvy += b.invMass * axisY * p
        a.pomega += a.invI * jaAxis * p; b.pomega += b.invI * jbAxis * p
    }
}
