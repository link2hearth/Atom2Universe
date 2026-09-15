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
    val sightRange: Double = 65.0,
    /** Après un bruit ou un impact, peut identifier le tireur au bout de l'arène, sans voir à travers les murs. */
    val alertedSightRange: Double = 160.0,
    /** En dessous de cette distance, il sent le joueur même dans son dos. */
    val closeAwareness: Double = 2.5,
    val hearingRange: Double = 160.0,
    /** Sans rien voir ni entendre pendant ce temps, il abandonne la traque. */
    val memorySeconds: Float = 8f,
    val reactionMin: Float = 0.35f,
    val reactionMax: Float = 0.6f,
    val aimErrorStartDeg: Float = 2f,
    val aimErrorMinDeg: Float = 0.18f,
    val aimErrorMaxDeg: Float = 3f,
    /** De combien la visée se resserre chaque seconde où il garde le joueur en vue. */
    val aimSettleDegPerSec: Float = 3f,
    /** Écart supplémentaire par bloc/s de course en travers, sans accumulation dans le temps. */
    val aimMovePenaltyDeg: Float = 0.04f,
    val bulletSpeed: Float = 120f,
    val bulletRange: Float = 160f,
    val fireInterval: Float = 0.45f,
    val magazineSize: Int = 10,
    val reloadSeconds: Float = 2.2f,
    val patrolSpeed: Float = 2.5f,
    val runSpeed: Float = 4.2f,
    /** Rayon (en blocs) où il cherche un abri pour recharger. */
    val coverRadius: Int = 10,
    val coverHoldSeconds: Float = 1.2f,
    val coverCooldownSeconds: Float = 5f,
    val repositionSeconds: Float = 3f,
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
 * Rechargement et repli sont menés à leur terme ; les autres actions sont choisies par scores :
 * - [State.RELOAD] : chargeur vide, il court vers un abri et recharge ;
 * - [State.COVER] : blessé sous le feu, il rejoint un abri et attend avant de ressortir ;
 * - [State.ENGAGE] : il voit le joueur, tire après réaction et change parfois de position ;
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
    private val clearance: BodyClearance? = null,
    private val routes: RouteQueue? = null,
) {
    enum class State { PATROL, ENGAGE, SEARCH, COVER, RELOAD }

    /** Déplacement le long de la grille (lecture seule à l'extérieur : position, chemin pour le debug). */
    val follower = PathFollower(grid, clearance)
    private var blockedFor = 0f
    val reloadProgress: Float get() = if (reloading && follower.arrived)
        (1f - reloadLeft / tuning.reloadSeconds).coerceIn(0f, 1f) else 0f
    private val path = IntList(128)
    private val coverCandidates = IntArray(8)
    private val coverDistances = DoubleArray(8)
    private var recentHitLeft = 0f
    private var coverCooldown = 0f
    private var coverHoldLeft = 0f
    private var coverTravelLeft = 0f
    private var repositionLeft = tuning.repositionSeconds
    private var reloading = false

    /** Santé propre du soldat, fournie par le combat ; aucune information sur le joueur. */
    var healthFraction = 1f

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
    private var searchRefreshLeft = 0f
    private var pendingRoute: RouteQueue.Request? = null

    fun cancelRoute() {
        pendingRoute?.cancelled = true
        pendingRoute = null
    }

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
        cancelRoute()
        follower.place(x, y, z)
        state = State.PATROL
        knowsPlayer = false; seesPlayer = false; justSpotted = false
        memoryAge = 0f; searchNode = -1; searchRefreshLeft = 0f
        ammo = tuning.magazineSize; reloadLeft = 0f; fireCooldown = 0f
        patrolWait = 0f
        recentHitLeft = 0f; coverCooldown = 0f; coverHoldLeft = 0f; coverTravelLeft = 0f
        repositionLeft = tuning.repositionSeconds; reloading = false
        healthFraction = 1f; aimErrorDeg = tuning.aimErrorStartDeg; reactionLeft = 0f
    }

    /** Un coup de feu du joueur part de (x, eyeY, z) : s'il est à portée d'oreille, il sait où aller voir. */
    fun hearShot(x: Double, eyeY: Double, z: Double) {
        val dx = x - follower.x; val dz = z - follower.z
        val dy = eyeY - EYE_HEIGHT - follower.y
        if (dx * dx + dy * dy + dz * dz > tuning.hearingRange * tuning.hearingRange) return
        remember(x, eyeY, z)
        if (!seesPlayer) yawDeg = yawTo(x, z)
    }

    /** Il vient d'être touché par une balle tirée de (x, eyeY, z) : il se retourne vers le tireur. */
    fun onDamaged(fromX: Double, fromEyeY: Double, fromZ: Double) {
        remember(fromX, fromEyeY, fromZ)
        recentHitLeft = 2f
        if (!seesPlayer) yawDeg = yawTo(fromX, fromZ)
    }

    fun update(dt: Float, player: PlayerSnapshot, shots: ShotSink) {
        justSpotted = false
        searchRefreshLeft = (searchRefreshLeft - dt).coerceAtLeast(0f)
        if (clearance != null && !follower.arrived) {
            blockedFor = if (isMoving) 0f else blockedFor + dt
            if (blockedFor > .7f) {
                val goal = follower.path[follower.path.size - 1]
                pathTo(goal, avoidBodies = true)
                blockedFor = 0f
            }
        }
        recentHitLeft = (recentHitLeft - dt).coerceAtLeast(0f)
        coverCooldown = (coverCooldown - dt).coerceAtLeast(0f)
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

        // 2. Une action engagée dure jusqu'à son terme : pas d'oscillation à chaque image.
        val previous = state
        val decision = when {
            reloading -> State.RELOAD
            previous == State.COVER && coverHoldLeft > 0f -> State.COVER
            else -> SoldierDecision.choose(seesPlayer, knowsPlayer, healthFraction,
                recentHitLeft > 0f, coverCooldown <= 0f)
        }
        state = decision
        if (state != previous) cancelRoute()
        if (state == State.COVER && previous != State.COVER) {
            coverCooldown = tuning.coverCooldownSeconds
            if (routeToCover()) {
                coverHoldLeft = tuning.coverHoldSeconds
                coverTravelLeft = 4f
            } else {
                state = if (seesPlayer) State.ENGAGE else State.SEARCH
            }
        }
        if (state == State.ENGAGE && previous != State.ENGAGE) {
            follower.stop()
            repositionLeft = tuning.repositionSeconds
        }
        isMoving = false
        when (state) {
            State.RELOAD -> actReload(dt)
            State.COVER -> actCover(dt)
            State.ENGAGE -> actEngage(dt, player, shots)
            State.SEARCH -> actSearch(dt)
            State.PATROL -> actPatrol(dt)
        }
    }

    // ── Perception ────────────────────────────────────────────────────────────

    private fun canSee(p: PlayerSnapshot): Boolean {
        val dx = p.x - follower.x; val dz = p.z - follower.z
        val distSq = dx * dx + dz * dz
        val range = if (knowsPlayer) tuning.alertedSightRange else tuning.sightRange
        if (distSq > range * range) return false
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
        searchNode = -1   // en le perdant de vue, il repartira vers la position la plus récente
        val inWeaponRange = (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z) <=
            tuning.bulletRange * tuning.bulletRange * .81
        repositionLeft -= dt
        if (inWeaponRange && follower.arrived && repositionLeft <= 0f) {
            repositionLeft = tuning.repositionSeconds + rng.nextFloat()
            reposition()
        }
        if (inWeaponRange && !follower.arrived) isMoving = follower.advance(dt, tuning.patrolSpeed)
        yawDeg = yawTo(p.x, p.z)

        // La visée se resserre tant qu'il garde le joueur en vue ; une course en travers la dérègle.
        val dx = p.x - follower.x; val dz = p.z - follower.z
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-6)
        val ux = dx / dist; val uz = dz / dist
        val along = p.velX * ux + p.velZ * uz
        val lateral = sqrt((p.velX - along * ux).let { it * it } + (p.velZ - along * uz).let { it * it })
        val targetError = (tuning.aimErrorMinDeg + lateral.toFloat() * tuning.aimMovePenaltyDeg)
            .coerceIn(tuning.aimErrorMinDeg, tuning.aimErrorMaxDeg)
        val correction = tuning.aimSettleDegPerSec * dt
        aimErrorDeg += (targetError - aimErrorDeg).coerceIn(-correction, correction)

        if (dist > tuning.bulletRange * .9) {
            val goal = grid.nodeUnder(lastKnownX - ux * 2, lastKnownEyeY - EYE_HEIGHT,
                lastKnownZ - uz * 2)
            if (follower.arrived && goal >= 0) pathTo(goal)
            if (!follower.arrived) isMoving = follower.advance(dt, tuning.runSpeed)
            return
        }
        if (reactionLeft > 0f) { reactionLeft -= dt; return }
        if (fireCooldown > 0f) return
        if (ammo <= 0) { startReload(); return }

        // La position a pu changer depuis la perception : aucun tir à travers un angle de mur.
        if (!LineOfSight.isClear(x, y + EYE_HEIGHT - 0.15, z,
                p.x, p.eyeY - AIM_BELOW_EYE, p.z, world)) return

        fire(p, shots)
        ammo--
        fireCooldown = tuning.fireInterval
        if (ammo == 0) startReload()
    }

    private fun fire(p: PlayerSnapshot, shots: ShotSink) {
        val ox = follower.x; val oy = follower.y + EYE_HEIGHT - 0.15; val oz = follower.z
        // Anticipation partielle de la course visible, limitée à 1,5 s : un changement de
        // direction après le départ de la balle permet toujours de l'esquiver.
        val distance = sqrt((p.x - ox) * (p.x - ox) + (p.z - oz) * (p.z - oz))
        val lead = (distance / tuning.bulletSpeed).coerceAtMost(1.5) * 0.85
        var dx = p.x + p.velX * lead - ox
        var dy = (p.eyeY - AIM_BELOW_EYE) - oy
        var dz = p.z + p.velZ * lead - oz
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
        reloading = true
        reloadLeft = tuning.reloadSeconds
        state = State.RELOAD
        coverTravelLeft = 4f
        routeToCover()
        searchNode = -1
    }

    private fun actReload(dt: Float) {
        if (!follower.arrived && coverTravelLeft > 0f) {
            coverTravelLeft -= dt
            isMoving = follower.advance(dt, tuning.runSpeed)
            yawDeg = follower.yawDeg
            return
        }
        follower.stop()
        if (knowsPlayer) yawDeg = yawTo(lastKnownX, lastKnownZ)
        // Le compte à rebours commence une fois arrivé ; faute d'abri, recharge sur place.
        reloadLeft = (reloadLeft - dt).coerceAtLeast(0f)
        if (reloadLeft <= 0f) {
            ammo = tuning.magazineSize
            reloading = false
        }
    }

    private fun actCover(dt: Float) {
        searchNode = -1
        if (!follower.arrived && coverTravelLeft > 0f) {
            coverTravelLeft -= dt
            isMoving = follower.advance(dt, tuning.runSpeed)
            yawDeg = follower.yawDeg
            return
        }
        follower.stop()
        if (knowsPlayer) yawDeg = yawTo(lastKnownX, lastKnownZ)
        coverHoldLeft = (coverHoldLeft - dt).coerceAtLeast(0f)
        if (coverHoldLeft <= 0f) coverCooldown = tuning.coverCooldownSeconds
    }

    /** Petit déplacement latéral, sur le même sol, en conservant une ligne de tir. */
    private fun reposition() {
        cancelRoute()
        val from = grid.nodeUnder(x, y, z)
        if (from < 0) return
        val angle = Math.toRadians(yawDeg.toDouble())
        val side = if (rng.nextBoolean()) 1 else -1
        for (sign in intArrayOf(side, -side)) {
            val nx = floor(x + cos(angle) * sign * 2).toInt()
            val nz = floor(z - sin(angle) * sign * 2).toInt()
            val n = grid.nodeAt(nx, grid.nodeY[from], nz)
            if (n < 0 || !LineOfSight.isClear(nx + 0.5, y + EYE_HEIGHT - 0.15, nz + 0.5,
                    lastKnownX, lastKnownEyeY - AIM_BELOW_EYE, lastKnownZ, world)) continue
            if (finder.findPath(from, n, path, clearance, maxCost = 4.5f) && path.size in 2..4) {
                follower.follow(path)
                return
            }
        }
    }

    private fun actSearch(dt: Float) {
        val target = grid.nodeUnder(lastKnownX, lastKnownEyeY - EYE_HEIGHT, lastKnownZ)
        if (target != searchNode && searchRefreshLeft <= 0f && pendingRoute == null) {
            searchRefreshLeft = .4f + rng.nextFloat() * .2f
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
            // Une patrouille locale ne doit pas viser une pièce quatre étages plus haut.
            if (abs(grid.nodeY[n] - follower.y) > 2.0) return@repeat
            val dx = grid.nodeX[n] + 0.5 - follower.x; val dz = grid.nodeZ[n] + 0.5 - follower.z
            val distSq = dx * dx + dz * dz
            if (distSq in PATROL_MIN_SQ..PATROL_MAX_SQ) {
                pathTo(n, maxCost = 60f)
                if (!follower.arrived || pendingRoute != null) return
            }
        }
    }

    // ── Outils ────────────────────────────────────────────────────────────────

    private fun pathTo(goal: Int, maxCost: Float = Float.POSITIVE_INFINITY, avoidBodies: Boolean = false) {
        val from = grid.nodeUnder(follower.x, follower.y, follower.z)
        if (routes != null && from >= 0) {
            if (pendingRoute != null) return
            follower.stop()
            // La navigation statique suffit normalement ; la collision réelle reste contrôlée
            // à chaque pas. En cas de blocage, contourner les corps, sans interdire la cible
            // occupée par le joueur (sinon A* explorerait toute la tour pour la refuser).
            val routingClearance = if (avoidBodies && clearance != null) BodyClearance { x, y, z ->
                (floor(x).toInt() == grid.nodeX[goal] && floor(y).toInt() == grid.nodeY[goal] &&
                    floor(z).toInt() == grid.nodeZ[goal]) || clearance.isFree(x, y, z)
            } else null
            pendingRoute = routes.request(from, goal, maxCost, routingClearance) { result ->
                pendingRoute = null
                if (!result.isEmpty()) follower.follow(result) else {
                    searchNode = -1
                    searchRefreshLeft = 1f
                }
            }
            return
        }
        if (from >= 0 && finder.findPath(from, goal, path, clearance, maxCost)) follower.follow(path) else follower.stop()
    }

    /**
     * Rejoint un des abris proches accessibles, caché depuis la dernière position connue.
     * Renvoie faux si aucun des candidats retenus n'a de chemin raisonnablement court.
     */
    private fun routeToCover(): Boolean {
        cancelRoute()
        follower.stop()
        searchNode = -1
        if (!knowsPlayer) return false
        val from = grid.nodeUnder(x, y, z)
        if (from < 0) return false
        coverCandidates.fill(-1)
        coverDistances.fill(Double.POSITIVE_INFINITY)
        val cx = floor(follower.x).toInt(); val cz = floor(follower.z).toInt()
        val r = tuning.coverRadius
        for (nz in cz - r..cz + r) for (nx in cx - r..cx + r) {
            val distSq = (nx - cx) * (nx - cx) + (nz - cz) * (nz - cz)
            if (distSq > r * r || distSq >= coverDistances.last()) continue
            for (ny in maxOf(1, floor(y).toInt() - 2)..minOf(grid.sizeY - 1, floor(y).toInt() + 2)) {
                val n = grid.nodeAt(nx, ny, nz)
                if (n < 0) continue
                val hidden = !LineOfSight.isClear(lastKnownX, lastKnownEyeY, lastKnownZ,
                    nx + 0.5, ny + EYE_HEIGHT, nz + 0.5, world)
                if (hidden) {
                    val distance = distSq.toDouble() + abs(ny - y) * 2.0
                    var slot = coverCandidates.lastIndex
                    if (distance >= coverDistances[slot]) continue
                    while (slot > 0 && distance < coverDistances[slot - 1]) {
                        coverCandidates[slot] = coverCandidates[slot - 1]
                        coverDistances[slot] = coverDistances[slot - 1]
                        slot--
                    }
                    coverCandidates[slot] = n; coverDistances[slot] = distance
                }
            }
        }
        // Huit candidats au maximum : un abri proche à vol d'oiseau peut être inaccessible.
        for (n in coverCandidates) {
            if (n >= 0 && finder.findPath(from, n, path, clearance,
                    maxCost = tuning.coverRadius * 4.8f) && path.size <= tuning.coverRadius * 3 + 1) {
                follower.follow(path)
                return true
            }
        }
        return false
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
