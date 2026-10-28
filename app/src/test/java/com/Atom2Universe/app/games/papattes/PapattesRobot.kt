package com.Atom2Universe.app.games.papattes

import kotlin.math.sin

/**
 * Un joueur automatique qui ne fait que ce que fait le doigt : appuyer et relâcher.
 *
 * - Marche : à chaque appui, il choisit où poser le pied (un pas ordinaire, sauf si ce pas
 *   tomberait dans l'eau) et relâche quand le pied libre, plus la petite course qu'il fait encore
 *   après le lâcher, atteint ce point.
 * - Saut : posé et calme, il mesure le prochain danger et charge juste assez pour atterrir après
 *   (ou juste avant, s'il est trop loin).
 * - Glisse : il pousse jusqu'à une bonne vitesse et tape juste avant un trou ou une marche.
 *
 * Il sert à vérifier qu'une démarche existe pour chaque paire de jambes et que les parcours se terminent.
 */
class PapattesRobot(
    private val game: PapattesGame,
    private val strideFactor: Float = .8f,
    private val settle: Float = .18f
) {
    private val hip = FloatArray(2)
    private val torso = FloatArray(2)
    private var releaseReach = 0f
    private var releaseVx = 0f
    /** Pour les bancs : (portée de la jambe au lâcher, vitesse du corps, point de chute réel). */
    var onLanded: ((Float, Float, Float) -> Unit)? = null
    private var held = 0f
    private var resting = 0f
    private var target = 0f
    /** Saut : durée d'appui voulue ; glisse : appui bref en cours. */
    private var holdFor = 0f
    private val legs = game.critter.legs
    private val stride get() = strideFactor * legs.length
    private val reach get() = 1.25f * legs.length

    private var measuring = false

    fun tick(dt: Float) {
        val w = game.walker
        if (game.phase != PapattesGame.Phase.WALKING) {
            if (w.pressed) game.release()
            held = 0f; resting = 0f
            return
        }
        when (legs.gait) {
            Gait.WALK -> walk(dt)
            Gait.HOP -> hop(dt)
            Gait.GLIDE -> glide(dt)
        }
    }

    private fun walk(dt: Float) {
        val w = game.walker
        // Mesure le point de chute réel de chaque pas, pour régler la prédiction.
        if (measuring && w.swingLanded && !w.pressed) {
            measuring = false
            w.swing?.let { onLanded?.invoke(releaseReach, releaseVx, it.foot()[0]) }
        }
        if (w.pressed) {
            held += dt
            val swing = w.swing ?: return
            w.hipPoint(hip)
            val reach = hip[0] + legs.length * sin(swing.thigh.angle)
            if (reach + CARRY >= target || held > 1.6f) {
                releaseReach = reach; releaseVx = w.body.vx
                game.release()
                measuring = true
                resting = 0f
            }
        } else {
            resting += dt
            if (resting > settle && (w.all.all { game.footGrounded(it) } || resting > .6f)) {
                val stanceX = (if (w.rearLeg() === w.front) w.back else w.front).foot()[0]
                target = plan(stanceX)
                game.press()
                held = 0f
            }
        }
    }

    private fun hop(dt: Float) {
        val w = game.walker
        if (w.pressed) {
            held += dt
            if (held >= holdFor) { game.release(); resting = 0f }
            return
        }
        resting += dt
        val calm = game.feetOnGround > 0 && kotlin.math.abs(w.body.vx) < .6f && kotlin.math.abs(w.body.vy) < .6f
        if (resting < HOP_SETTLE || !calm) return
        w.torsoCenter(torso)
        val x = torso[0]
        val full = legs.maxJump * if (game.critter.body.power == Power.BOUNCE) PapattesWalker.BOUNCE_JUMP * PapattesWalker.BOUNCE_JUMP else 1f
        var distance = full * HOP_COMFORT
        val hazard = hazardAhead(x + .15f, x + full + .5f)
        if (hazard != null) {
            val (start, end) = hazard
            distance = if (end + HOP_MARGIN - x <= full * .95f) end + HOP_MARGIN - x else (start - .2f - x).coerceAtLeast(.15f)
        }
        holdFor = (distance / full).coerceIn(.05f, 1f) * legs.chargeTime
        held = 0f
        game.press()
    }

    private fun glide(dt: Float) {
        val w = game.walker
        val v = w.body.vx
        w.torsoCenter(torso)
        // Dans la boue, on ne dépasse jamais 1,1 m/s : on vise un peu moins que ce que permet le sol.
        val cruise = minOf(GLIDE_CRUISE, .85f * legs.glideSpeed(game.course.segmentAt(torso[0])?.surface ?: Surface.GRASS))
        if (w.pressed) {
            held += dt
            if (holdFor > 0f && held >= holdFor) { game.release(); holdFor = 0f; resting = 0f; return }
            if (holdFor == 0f && v > cruise) game.release()
            return
        }
        resting += dt
        if (game.feetOnGround == 0) return
        // On saute quand le pied avant arrive au bord : le corps passe au-dessus pendant la petite course en l'air.
        val front = maxOf(w.front.foot()[0], w.back.foot()[0])
        val lookahead = .12f + v * .1f
        val hazard = hazardAhead(torso[0], front + 1.6f, rise = .07f)
        if (hazard != null && hazard.first - front < lookahead && v > minOf(.8f, cruise * .8f) && resting > .2f) {
            holdFor = .06f
            held = 0f
            game.press()
            return
        }
        if (v < cruise * .8f && (hazard == null || hazard.first - torso[0] > 1.2f)) {
            holdFor = 0f
            held = 0f
            game.press()
        }
    }

    /**
     * Le premier danger entre [from] et [to] : un endroit sans sol, ou une marche qui monte de plus
     * de 0,2 m. Rend (début, fin) du danger.
     */
    private fun hazardAhead(from: Float, to: Float, rise: Float = .2f): Pair<Float, Float>? {
        var x = from
        var last = game.groundAt(from)
        while (x < to) {
            val y = game.groundAt(x)
            if (!y.isFinite() || (last.isFinite() && y - last > rise)) {
                val start = x
                if (!y.isFinite()) while (x < from + 6f && !game.groundAt(x).isFinite()) x += .02f
                // Une fente entre deux planches, plus étroite qu'un pied, n'est pas un trou.
                if (y.isFinite() || x - start > NARROW) return start to x
                last = game.groundAt(x)
                continue
            }
            last = y
            x += .02f
        }
        return null
    }

    private fun solid(from: Float, to: Float) = (0..6).count { game.groundAt(from + (to - from) * it / 6).isFinite() } >= 6

    /** Où poser le pied libre, le pied d'appui étant en [stanceX]. */
    private fun plan(stanceX: Float): Float {
        val nominal = stanceX + stride
        if (solid(nominal - .1f, nominal + .15f)) return nominal
        // Le premier trou devant le pas ordinaire.
        var x = stanceX + .1f
        while (x < nominal + .2f && game.groundAt(x).isFinite()) x += .02f
        val holeStart = x
        while (x < stanceX + reach + 1f && !game.groundAt(x).isFinite()) x += .02f
        val far = x + .15f
        if (far - stanceX <= reach && solid(far - .1f, far + .15f)) return far
        return maxOf(stanceX + .15f, holeStart - .15f)
    }

    /** Joue l'étape jusqu'au drapeau ou jusqu'à [limit] secondes. */
    fun run(limit: Float, dt: Float = 1f / 60f): Boolean {
        var t = 0f
        while (t < limit && game.phase != PapattesGame.Phase.FINISHED) {
            tick(dt)
            game.update(dt)
            t += dt
        }
        return game.phase == PapattesGame.Phase.FINISHED
    }

    private companion object {
        /** Le pied file encore un peu après le lâcher. */
        const val CARRY = .12f
        const val HOP_SETTLE = .15f
        /** Bond ordinaire, en part de la portée maximale, et marge après un trou. */
        const val HOP_COMFORT = .6f
        const val HOP_MARGIN = .4f
        const val GLIDE_CRUISE = 1.3f
        const val NARROW = .12f
    }
}
