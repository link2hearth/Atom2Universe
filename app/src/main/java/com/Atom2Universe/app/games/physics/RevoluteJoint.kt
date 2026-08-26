package com.Atom2Universe.app.games.physics

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Une liaison pivot : deux corps sont cloués l'un à l'autre en un point, et ne
 * peuvent plus que tourner autour de ce point.
 *
 * C'est la pièce qui manquait au moteur. Le jeu d'équilibre s'en passait parce
 * que sa planche tourne autour de son propre centre (`lockPosition` suffisait) ;
 * un trébuchet, lui, a son pied **décalé** : bras long d'un côté, bras court de
 * l'autre. Il faut donc ancrer un point quelconque du bras.
 *
 * Pour clouer un corps au décor, il suffit de prendre comme second corps une
 * masse immobile (`lockPosition` + `lockRotation`) : le calcul est le même, ses
 * masses inverses valant zéro.
 *
 * La résolution suit le même principe que les contacts : à chaque passe du
 * solveur on corrige la vitesse relative au point d'ancrage, et l'impulsion est
 * conservée d'une image à l'autre pour que la liaison ne « mollisse » pas.
 */
class RevoluteJoint(val a: PhysBody, val b: PhysBody) {

    /** Point d'ancrage dans le repère de [a]. */
    var localAnchorAX = 0f
    var localAnchorAY = 0f

    /** Point d'ancrage dans le repère de [b]. */
    var localAnchorBX = 0f
    var localAnchorBY = 0f

    /**
     * Liaison active. La mettre à `false` décroche instantanément les deux corps :
     * c'est comme ça que la fronde du trébuchet lâche son projectile.
     */
    var enabled = true

    /**
     * Faut-il quand même tester la collision entre les deux corps reliés ? Non par
     * défaut : l'axe d'un trébuchet traverse le bras, les deux se chevauchent donc
     * forcément, et les laisser se repousser ferait vibrer la machine.
     */
    var collideConnected = false

    /**
     * Souplesse de la liaison. 0 = rigide. Une petite valeur (0,01 à 0,1) donne
     * un axe qui « travaille » un peu, utile pour amortir un choc violent.
     */
    var softness = 0f

    /** Force du rappel qui recolle les deux ancrages quand ils ont dérivé. */
    var biasFactor = 0.2f

    // Impulsion accumulée, conservée d'une image à l'autre.
    var impulseX = 0f
        private set
    var impulseY = 0f
        private set

    // Pré-calculs (matrice de masse effective 2x2, inversée)
    private var m00 = 0f
    private var m01 = 0f
    private var m10 = 0f
    private var m11 = 0f
    private var rax = 0f
    private var ray = 0f
    private var rbx = 0f
    private var rby = 0f
    private var biasX = 0f
    private var biasY = 0f
    private var solvable = false

    companion object {
        /**
         * Cloue [a] et [b] l'un à l'autre au point monde ([worldX], [worldY]),
         * en calculant les deux ancrages locaux à partir de la pose actuelle.
         */
        fun pin(a: PhysBody, b: PhysBody, worldX: Float, worldY: Float): RevoluteJoint {
            val j = RevoluteJoint(a, b)
            j.setWorldAnchor(worldX, worldY)
            return j
        }

        /**
         * Soude [a] et [b] : deux pivots en deux points distincts, et il ne reste
         * plus aucun degré de liberté — même la rotation relative est bloquée.
         *
         * C'est le moyen d'assembler une pièce en plusieurs morceaux alors que le
         * moteur ne connaît qu'une forme par corps : la cuiller du trébuchet, par
         * exemple, est un rebord soudé au bout du bras. Les deux points doivent
         * être bien écartés, sinon la soudure a du jeu en rotation.
         */
        fun weld(
            a: PhysBody, b: PhysBody,
            x1: Float, y1: Float,
            x2: Float, y2: Float
        ): Pair<RevoluteJoint, RevoluteJoint> = pin(a, b, x1, y1) to pin(a, b, x2, y2)
    }

    /** Recalcule les ancrages locaux d'après un point monde et la pose actuelle. */
    fun setWorldAnchor(worldX: Float, worldY: Float) {
        val ca = cos(a.angle)
        val sa = sin(a.angle)
        val dxa = worldX - a.x
        val dya = worldY - a.y
        localAnchorAX = dxa * ca + dya * sa
        localAnchorAY = -dxa * sa + dya * ca

        val cb = cos(b.angle)
        val sb = sin(b.angle)
        val dxb = worldX - b.x
        val dyb = worldY - b.y
        localAnchorBX = dxb * cb + dyb * sb
        localAnchorBY = -dxb * sb + dyb * cb
    }

    /** Position monde actuelle de l'ancrage porté par [a], dans [out] (2 flottants). */
    fun anchorWorld(out: FloatArray) {
        a.localToWorld(localAnchorAX, localAnchorAY, out)
    }

    /**
     * Intensité de l'impulsion encaissée au dernier pas, en kg·m/s. C'est ce que
     * la liaison a dû encaisser pour tenir : de quoi faire céder une machine trop
     * chargée, si on décide un jour que les pièces peuvent casser.
     */
    val reactionImpulse: Float
        get() = sqrt(impulseX * impulseX + impulseY * impulseY)

    /** Oublie l'impulsion mémorisée (après avoir téléporté un corps). */
    fun reset() {
        impulseX = 0f
        impulseY = 0f
    }

    internal fun preStep(invDt: Float) {
        solvable = false
        if (!enabled || !a.inWorld || !b.inWorld) return

        val ca = cos(a.angle)
        val sa = sin(a.angle)
        rax = localAnchorAX * ca - localAnchorAY * sa
        ray = localAnchorAX * sa + localAnchorAY * ca

        val cb = cos(b.angle)
        val sb = sin(b.angle)
        rbx = localAnchorBX * cb - localAnchorBY * sb
        rby = localAnchorBX * sb + localAnchorBY * cb

        // Masse effective vue au point d'ancrage, sur les deux axes à la fois.
        val invM = a.invMass + b.invMass
        val k00 = invM + a.invI * ray * ray + b.invI * rby * rby + softness
        val k01 = -a.invI * rax * ray - b.invI * rbx * rby
        val k11 = invM + a.invI * rax * rax + b.invI * rbx * rbx + softness

        val det = k00 * k11 - k01 * k01
        // Deux corps immobiles : rien à résoudre, et la matrice n'est pas inversible.
        if (det <= 1e-12f) return
        val invDet = 1f / det
        m00 = k11 * invDet
        m01 = -k01 * invDet
        m10 = -k01 * invDet
        m11 = k00 * invDet

        // Écart entre les deux ancrages : on le résorbe progressivement.
        val dpx = (b.x + rbx) - (a.x + rax)
        val dpy = (b.y + rby) - (a.y + ray)
        biasX = -biasFactor * invDt * dpx
        biasY = -biasFactor * invDt * dpy

        // Réapplication de l'impulsion de l'image précédente
        a.vx -= a.invMass * impulseX
        a.vy -= a.invMass * impulseY
        a.omega -= a.invI * (rax * impulseY - ray * impulseX)
        b.vx += b.invMass * impulseX
        b.vy += b.invMass * impulseY
        b.omega += b.invI * (rbx * impulseY - rby * impulseX)

        solvable = true
    }

    internal fun applyImpulse() {
        if (!solvable) return

        // Vitesse relative des deux points d'ancrage
        val dvx = (b.vx - b.omega * rby) - (a.vx - a.omega * ray)
        val dvy = (b.vy + b.omega * rbx) - (a.vy + a.omega * rax)

        val ex = biasX - dvx - softness * impulseX
        val ey = biasY - dvy - softness * impulseY
        val px = m00 * ex + m01 * ey
        val py = m10 * ex + m11 * ey

        a.vx -= a.invMass * px
        a.vy -= a.invMass * py
        a.omega -= a.invI * (rax * py - ray * px)
        b.vx += b.invMass * px
        b.vy += b.invMass * py
        b.omega += b.invI * (rbx * py - rby * px)

        impulseX += px
        impulseY += py
    }
}
