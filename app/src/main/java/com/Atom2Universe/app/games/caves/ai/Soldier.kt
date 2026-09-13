package com.Atom2Universe.app.games.caves.ai

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Réglages d'un soldat. Angles en degrés, distances en blocs, durées en secondes. */
internal data class SoldierTuning(
    val fovDegrees: Float = 110f,
    val sightRange: Double = 45.0,
    /** En dessous de cette distance, il sent le joueur même dans son dos. */
    val closeAwareness: Double = 2.5,
    val hearingRange: Double = 35.0,
    /** Sans rien voir ni entendre pendant ce temps, il abandonne la traque. */
    val memorySeconds: Float = 8f,
    val reactionMin: Float = 0.35f,
    val reactionMax: Float = 0.6f,
    val aimErrorStartDeg: Float = 7f,
    val aimErrorMinDeg: Float = 1.2f,
    val aimErrorMaxDeg: Float = 10f,
    /** De combien la visée se resserre chaque seconde où il garde le joueur en vue. */
    val aimSettleDegPerSec: Float = 4f,
    /** De combien elle se dérègle chaque seconde, par bloc/s de course du joueur en travers. */
    val aimMovePenaltyDeg: Float = 1.2f,
    val fireInterval: Float = 0.45f,
    val magazineSize: Int = 10,
    val reloadSeconds: Float = 2.2f,
    val patrolSpeed: Float = 2.5f,
    val runSpeed: Float = 4.2f,
    /** Rayon (en blocs) où il cherche un abri pour recharger. */
    val coverRadius: Int = 10,
)

/** Le joueur tel que les soldats le perçoivent à cette image. Coordonnées locales à la carte, réutilisé d'une image à l'autre. */
internal class PlayerSnapshot {
    var x = 0.0
    var eyeY = 0.0
    var z = 0.0
    var velX = 0.0
    var velZ = 0.0
    var alive = true
}

internal fun interface ShotSink {
    /** Un tir part de (x, y, z) dans la direction normalisée (dx, dy, dz). */
    fun fire(x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double)
}

/**
 * Le cerveau d'un soldat du mode Assaut. Kotlin pur : il reçoit ce qui l'entoure (grille, blocs,
 * joueur) et renvoie ses mouvements et ses tirs, ce qui permet de le tester seul (SoldierTest).
 *
 * **Il ne triche pas** : il ne connaît la position du joueur que s'il le voit (champ de vision +
 * ligne de vue), l'entend tirer, ou se fait toucher. Il retient alors la *dernière position connue*
 * et l'oublie au bout de [SoldierTuning.memorySeconds] sans nouvelle information : on peut le semer.
 *
 * À chaque image, il choisit un état, dans cet ordre de priorité :
 * - [State.RELOAD] : chargeur vide, il court vers un abri et recharge ;
 * - [State.ENGAGE] : il voit le joueur, il s'arrête et tire, après un temps de réaction ;
 * - [State.SEARCH] : il l'a perdu de vue, il va vérifier la dernière position connue et regarde autour ;
 * - [State.PATROL] : il ne sait rien, il se promène d'un point à l'autre.
 *
 * Sa visée est volontairement humaine : écart de départ, qui se resserre tant qu'il garde la
 * cible en vue et se dérègle si le joueur court en travers.
 */
internal class Soldier(
    private val grid: NavGrid,
    private val world: SolidGrid,
    private val finder: PathFinder,
    private val rng: Random,
    val tuning: SoldierTuning = SoldierTuning(),
) {
    enum class State { PATROL, ENGAGE, SEARCH, RELOAD }

    /** Déplacement le long de la grille (lecture seule à l'extérieur : position, chemin pour le debug). */
    val follower = PathFollower(grid)
    private val path = IntList(128)

    var state = State.PATROL; private set
    val x: Double get() = follower.x
    val y: Double get() = follower.y
    val z: Double get() = follower.z

    /** Direction du regard en degrés (0 = +Z), même convention que la caméra. */
    var yawDeg = 0f; private set
    var isMoving = false; private set

    // ── Mémoire ──
    var knowsPlayer = false; private set
    var lastKnownX = 0.0; private set
    var lastKnownEyeY = 0.0; private set
    var lastKnownZ = 0.0; private set
    private var memoryAge = 0f
    private var searchNode = -1

    // ── Vue et visée ──
    var seesPlayer = false; private set
    /** Vient de repérer le joueur à cette image (pour une alerte sonore). */
    var justSpotted = false; private set
    var aimErrorDeg = 0f; private set
    private var reactionLeft = 0f

    // ── Arme ──
    var ammo = tuning.magazineSize; private set
    private var reloadLeft = 0f
    private var fireCooldown = 0f

    // ── Patrouille ──
    private var patrolWait = 0f

    fun place(x: Double, y: Double, z: Double) {
        follower.place(x, y, z)
        state = State.PATROL
        knowsPlayer = false; seesPlayer = false; justSpotted = false
        memoryAge = 0f; searchNode = -1
        ammo = tuning.magazineSize; reloadLeft = 0f; fireCooldown = 0f
        patrolWait = 0f
    }

    /** Un coup de feu du joueur part de (x, eyeY, z) : s'il est à portée d'oreille, il sait où aller voir. */
    fun hearShot(x: Double, eyeY: Double, z: Double) {
        val dx = x - follower.x; val dz = z - follower.z
        if (dx * dx + dz * dz > tuning.hearingRange * tuning.hearingRange) return
        remember(x, eyeY, z)
    }

    /** Il vient d'être touché par une balle tirée de (x, eyeY, z) : il se retourne vers le tireur. */
    fun onDamaged(fromX: Double, fromEyeY: Double, fromZ: Double) {
        remember(fromX, fromEyeY, fromZ)
        if (!seesPlayer) yawDeg = yawTo(fromX, fromZ)
    }

    fun update(dt: Float, player: PlayerSnapshot, shots: ShotSink) {
        justSpotted = false
        if (fireCooldown > 0f) fireCooldown = (fireCooldown - dt).coerceAtLeast(0f)

        // 1. Perception et mémoire.
        val sawBefore = seesPlayer
        seesPlayer = player.alive && canSee(player)
        if (seesPlayer) {
            remember(player.x, player.eyeY, player.z)
            if (!sawBefore) {
                justSpotted = true
                reactionLeft = tuning.reactionMin + rng.nextFloat() * (tuning.reactionMax - tuning.reactionMin)
                aimErrorDeg = tuning.aimErrorStartDeg
            }
        } else if (knowsPlayer) {
            memoryAge += dt
            if (memoryAge > tuning.memorySeconds) {
                knowsPlayer = false
                searchNode = -1
            }
        }

        // 2. Le rechargement se termine où qu'il en soit.
        if (reloadLeft > 0f) {
            reloadLeft -= dt
            if (reloadLeft <= 0f) {
                reloadLeft = 0f
                ammo = tuning.magazineSize
            }
        }

        // 3. Décision, puis action.
        state = when {
            reloadLeft > 0f -> State.RELOAD
            seesPlayer -> State.ENGAGE
            knowsPlayer -> State.SEARCH
            else -> State.PATROL
        }
        isMoving = false
        when (state) {
            State.RELOAD -> actReload(dt)
            State.ENGAGE -> actEngage(dt, player, shots)
            State.SEARCH -> actSearch(dt)
            State.PATROL -> actPatrol(dt)
        }
    }

    // ── Perception ────────────────────────────────────────────────────────────

    private fun canSee(p: PlayerSnapshot): Boolean {
        val dx = p.x - follower.x; val dz = p.z - follower.z
        val distSq = dx * dx + dz * dz
        if (distSq > tuning.sightRange * tuning.sightRange) return false
        // Déjà en train de le suivre des yeux, ou collé à lui : inutile qu'il soit dans le champ de vision.
        if (!seesPlayer && distSq > tuning.closeAwareness * tuning.closeAwareness) {
            if (abs(angleDiff(yawTo(p.x, p.z), yawDeg)) > tuning.fovDegrees / 2f) return false
        }
        return LineOfSight.isClear(follower.x, follower.y + EYE_HEIGHT, follower.z, p.x, p.eyeY, p.z, world)
    }

    private fun remember(x: Double, eyeY: Double, z: Double) {
        knowsPlayer = true
        lastKnownX = x; lastKnownEyeY = eyeY; lastKnownZ = z
        memoryAge = 0f
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun actEngage(dt: Float, p: PlayerSnapshot, shots: ShotSink) {
        follower.stop()
        searchNode = -1   // en le perdant de vue, il repartira vers la position la plus récente
        yawDeg = yawTo(p.x, p.z)

        // La visée se resserre tant qu'il garde le joueur en vue ; une course en travers la dérègle.
        val dx = p.x - follower.x; val dz = p.z - follower.z
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-6)
        val ux = dx / dist; val uz = dz / dist
        val along = p.velX * ux + p.velZ * uz
        val lateral = sqrt((p.velX - along * ux).let { it * it } + (p.velZ - along * uz).let { it * it })
        aimErrorDeg = (aimErrorDeg - tuning.aimSettleDegPerSec * dt + (lateral * tuning.aimMovePenaltyDeg * dt).toFloat())
            .coerceIn(tuning.aimErrorMinDeg, tuning.aimErrorMaxDeg)

        if (reactionLeft > 0f) { reactionLeft -= dt; return }
        if (fireCooldown > 0f) return
        if (ammo <= 0) { startReload(); return }

        fire(p, shots)
        ammo--
        fireCooldown = tuning.fireInterval
        if (ammo == 0) startReload()
    }

    private fun fire(p: PlayerSnapshot, shots: ShotSink) {
        val ox = follower.x; val oy = follower.y + EYE_HEIGHT - 0.15; val oz = follower.z
        var dx = p.x - ox; var dy = (p.eyeY - AIM_BELOW_EYE) - oy; var dz = p.z - oz
        var len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-6) return
        dx /= len; dy /= len; dz /= len

        // Écart aléatoire : on tourne un peu la direction à l'horizontale et à la verticale.
        val err = Math.toRadians(aimErrorDeg.toDouble())
        val yawOffset = (rng.nextDouble() * 2 - 1) * err
        val pitchOffset = (rng.nextDouble() * 2 - 1) * err
        val c = cos(yawOffset); val s = sin(yawOffset)
        val rx = dx * c + dz * s
        val rz = -dx * s + dz * c
        dx = rx; dz = rz
        dy += pitchOffset
        len = sqrt(dx * dx + dy * dy + dz * dz)
        shots.fire(ox, oy, oz, dx / len, dy / len, dz / len)
    }

    private fun startReload() {
        reloadLeft = tuning.reloadSeconds
        state = State.RELOAD
        val cover = findCover()
        if (cover >= 0) pathTo(cover) else follower.stop()
    }

    private fun actReload(dt: Float) {
        if (!follower.arrived) {
            isMoving = follower.advance(dt, tuning.runSpeed)
            yawDeg = follower.yawDeg
        } else if (knowsPlayer) {
            yawDeg = yawTo(lastKnownX, lastKnownZ)
        }
    }

    private fun actSearch(dt: Float) {
        val target = grid.nodeUnder(lastKnownX, lastKnownEyeY - EYE_HEIGHT, lastKnownZ)
        if (target != searchNode) {
            searchNode = target
            if (target >= 0) pathTo(target)
        }
        if (!follower.arrived) {
            isMoving = follower.advance(dt, tuning.runSpeed)
            yawDeg = follower.yawDeg
        } else {
            // Arrivé là où il l'a perdu : il regarde autour de lui en attendant d'oublier.
            yawDeg = normalizeDeg(yawDeg + LOOK_AROUND_DEG_PER_SEC * dt)
        }
    }

    private fun actPatrol(dt: Float) {
        if (!follower.arrived) {
            isMoving = follower.advance(dt, tuning.patrolSpeed)
            yawDeg = follower.yawDeg
            return
        }
        patrolWait -= dt
        if (patrolWait > 0f) return
        patrolWait = 1f + rng.nextFloat() * 1.5f
        // Prochaine destination : une case au hasard, ni trop près ni trop loin.
        repeat(PATROL_PICK_ATTEMPTS) {
            val n = rng.nextInt(grid.nodeCount)
            val dx = grid.nodeX[n] + 0.5 - follower.x; val dz = grid.nodeZ[n] + 0.5 - follower.z
            val distSq = dx * dx + dz * dz
            if (distSq in PATROL_MIN_SQ..PATROL_MAX_SQ) {
                pathTo(n)
                if (!follower.arrived) return
            }
        }
    }

    // ── Outils ────────────────────────────────────────────────────────────────

    private fun pathTo(goal: Int) {
        val from = grid.nodeUnder(follower.x, follower.y, follower.z)
        if (from >= 0 && finder.findPath(from, goal, path)) follower.follow(path) else follower.stop()
    }

    /**
     * La case la plus proche (dans [SoldierTuning.coverRadius]) où le joueur, depuis sa dernière
     * position connue, ne verrait pas la tête du soldat debout. -1 s'il n'y en a pas.
     */
    private fun findCover(): Int {
        if (!knowsPlayer) return -1
        val cx = floor(follower.x).toInt(); val cz = floor(follower.z).toInt()
        val r = tuning.coverRadius
        var best = -1
        var bestDistSq = Int.MAX_VALUE
        for (nz in cz - r..cz + r) for (nx in cx - r..cx + r) {
            val distSq = (nx - cx) * (nx - cx) + (nz - cz) * (nz - cz)
            if (distSq > r * r || distSq >= bestDistSq) continue
            for (ny in 0 until grid.sizeY) {
                val n = grid.nodeAt(nx, ny, nz)
                if (n < 0) continue
                val hidden = !LineOfSight.isClear(lastKnownX, lastKnownEyeY, lastKnownZ,
                    nx + 0.5, ny + EYE_HEIGHT, nz + 0.5, world)
                if (hidden) { best = n; bestDistSq = distSq; break }
            }
        }
        return best
    }

    private fun yawTo(tx: Double, tz: Double): Float =
        Math.toDegrees(atan2(tx - follower.x, tz - follower.z)).toFloat()

    private companion object {
        const val EYE_HEIGHT = 1.62
        /** Il vise le haut du torse plutôt que les yeux. */
        const val AIM_BELOW_EYE = 0.35
        const val LOOK_AROUND_DEG_PER_SEC = 60f
        const val PATROL_PICK_ATTEMPTS = 20
        const val PATROL_MIN_SQ = 8.0 * 8.0
        const val PATROL_MAX_SQ = 30.0 * 30.0

        fun normalizeDeg(a: Float): Float {
            var v = a % 360f
            if (v > 180f) v -= 360f
            if (v < -180f) v += 360f
            return v
        }

        fun angleDiff(a: Float, b: Float): Float = normalizeDeg(a - b)
    }
}
