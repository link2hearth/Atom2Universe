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
    val hearingRange: Double = 80.0,
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
    /**
     * Portée à laquelle un homme **en réserve** accepte le duel. Au-delà, il ne reste pas planté à
     * regarder : il se déplace dans son secteur pour prendre une position de tir. Sans secteur
     * (escouade engagée), seule la portée de l'arme compte.
     */
    val reserveEngageRange: Double = 32.0,
    /** Rayon (en blocs) où il cherche un abri pour recharger. */
    val coverRadius: Int = 10,
    val coverHoldSeconds: Float = 1.2f,
    val coverCooldownSeconds: Float = 5f,
    val repositionSeconds: Float = 3f,
    /** Retard de son estimation de ta course : il tire où tu allais, pas où tu vas exactement. */
    val velocityLagSeconds: Float = 0.35f,
    /** Écart supplémentaire tant qu'il se déplace lui-même : tirer en marchant coûte cher. */
    val aimMoveSelfPenaltyDeg: Float = 0.8f,
    /** Rafales courtes puis pause, plutôt qu'un tir régulier de métronome. */
    val burstMin: Int = 3,
    val burstMax: Int = 5,
    val burstPauseMin: Float = 0.5f,
    val burstPauseMax: Float = 1.1f,
    /** Durée pendant laquelle une balle qui l'a frôlé le perturbe. */
    val suppressionSeconds: Float = 1.5f,
    val suppressionAimPenaltyDeg: Float = 1.2f,
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
    private var unblockDelay = UNBLOCK_FIRST_DELAY
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
    /** Depuis combien de temps il voit le joueur sans avoir de ligne de tir dégagée. */
    private var blockedLineFor = 0f
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

    /** Touché ou frôlé il y a peu : l'escouade n'a plus de raison de rester discrète. */
    val shaken: Boolean get() = recentHitLeft > 0f || suppressedLeft > 0f

    // ── Consignes de l'escouade (voir SquadCommand) ──
    /** Case à rejoindre sur ordre radio tant qu'il ne voit rien lui-même ; -1 le laisse libre. */
    private var orderedNode = -1
    /** Un poste de regroupement se tient : le bruit d'une fusillade voisine ne l'en fait pas partir. */
    private var orderStrict = false
    private var leashX = 0.0
    private var leashZ = 0.0
    /** Rayon du secteur tenu ; infini = libre de ses mouvements. */
    private var leashRadius = Double.POSITIVE_INFINITY
    private var radioX = 0.0
    private var radioZ = 0.0
    private var hasRadio = false
    private var lookPhase = 0f

    // ── Mémoire ──
    var knowsPlayer = false; private set
    var lastKnownX = 0.0; private set
    var lastKnownEyeY = 0.0; private set
    var lastKnownZ = 0.0; private set
    private var memoryAge = 0f
    private var searchNode = -1
    private var searchRefreshLeft = 0f
    private var pendingRoute: RouteQueue.Request? = null

    /** Il attend un trajet de la file partagée : tant que oui, il ne bouge pas. */
    val waitingForRoute: Boolean get() = pendingRoute != null

    fun cancelRoute() {
        pendingRoute?.cancelled = true
        pendingRoute = null
    }

    /**
     * Ordre radio : la case où se rendre tant qu'il ne voit rien lui-même. -1 le laisse libre.
     *
     * Avec [strict], l'ordre passe avant ce qu'il a entendu : c'est ce qui tient une escouade
     * groupée au point de regroupement au lieu de l'éparpiller au premier coup de feu lointain.
     * Ce qu'il **voit** reste toujours prioritaire : un ordre ne l'empêche pas de se défendre.
     */
    fun order(node: Int, strict: Boolean = false) {
        orderStrict = strict
        if (node == orderedNode) return
        orderedNode = node
        searchNode = -1
        searchRefreshLeft = 0f
    }

    /**
     * Position approximative du joueur annoncée à la radio. Elle ne lui apprend rien sur ce qu'il
     * voit : elle lui dit seulement de quel côté regarder quand il est en poste.
     */
    fun radioContact(x: Double, z: Double) {
        radioX = x; radioZ = z; hasRadio = true
    }

    /**
     * Secteur à tenir. Hors contact direct, il n'en sort pas ; et il ne tire pas sur ce qu'il
     * aperçoit bien au-delà (un homme en réserve n'ouvre pas le feu à travers tout le quartier).
     * Un rayon infini le libère : c'est l'état de l'escouade qui monte à l'assaut.
     */
    fun leashTo(x: Double, z: Double, radius: Double) {
        leashX = x; leashZ = z; leashRadius = radius
    }

    // ── Vue et visée ──
    var seesPlayer = false; private set
    /** Vient de repérer le joueur à cette image (pour une alerte sonore). */
    var justSpotted = false; private set
    var aimErrorDeg = 0f; private set
    private var reactionLeft = 0f
    // Estimation retardée de la course du joueur : il ne lit jamais sa vitesse réelle pour viser.
    private var trackedVelX = 0.0
    private var trackedVelZ = 0.0
    private var suppressedLeft = 0f
    private var shotsInBurst = 0
    private var burstTarget = tuning.burstMin

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
        repositionLeft = tuning.repositionSeconds; reloading = false; blockedLineFor = 0f
        healthFraction = 1f; aimErrorDeg = tuning.aimErrorStartDeg; reactionLeft = 0f
        trackedVelX = 0.0; trackedVelZ = 0.0; suppressedLeft = 0f
        shotsInBurst = 0; burstTarget = tuning.burstMin
        orderedNode = -1; orderStrict = false; hasRadio = false; lookPhase = 0f
        leashRadius = Double.POSITIVE_INFINITY
    }

    /**
     * Un bruit perçu en (x, eyeY, z), audible jusqu'à [range] blocs : il retient l'endroit et se
     * tourne vers lui. La portée dépend du bruit : un coup de feu s'entend de très loin, des pas
     * seulement de tout près (voir `AssaultMode.emitFootsteps`).
     */
    fun hearNoise(x: Double, eyeY: Double, z: Double, range: Double) {
        val dx = x - follower.x; val dz = z - follower.z
        val dy = eyeY - EYE_HEIGHT - follower.y
        if (dx * dx + dy * dy + dz * dz > range * range) return
        remember(x, eyeY, z)
        if (!seesPlayer) yawDeg = yawTo(x, z)
    }

    /** Un coup de feu du joueur part de (x, eyeY, z) : s'il est à portée d'oreille, il sait où aller voir. */
    fun hearShot(x: Double, eyeY: Double, z: Double) = hearNoise(x, eyeY, z, tuning.hearingRange)

    /** Il vient d'être touché par une balle tirée de (x, eyeY, z) : il se retourne vers le tireur. */
    fun onDamaged(fromX: Double, fromEyeY: Double, fromZ: Double) {
        remember(fromX, fromEyeY, fromZ)
        recentHitLeft = 2f
        if (!seesPlayer) yawDeg = yawTo(fromX, fromZ)
    }

    /**
     * Une balle du joueur vient de le frôler. Il se fait tout petit : sa visée se dégrade un
     * instant, et s'il est déjà blessé, cela suffit à le décider à plonger à couvert.
     */
    fun onNearMiss() {
        suppressedLeft = tuning.suppressionSeconds
        aimErrorDeg = maxOf(aimErrorDeg, tuning.suppressionAimPenaltyDeg)
    }

    /**
     * Met à jour son idée de la course du joueur, avec du retard (moyenne glissante). C'est cette
     * estimation, et non la vitesse réelle, qui sert à anticiper : changer brusquement de
     * direction le prend donc à contre-pied.
     */
    private fun trackPlayerRun(dt: Float, p: PlayerSnapshot) {
        val k = (dt / (tuning.velocityLagSeconds + dt)).toDouble()
        trackedVelX += (p.velX - trackedVelX) * k
        trackedVelZ += (p.velZ - trackedVelZ) * k
    }

    fun update(dt: Float, player: PlayerSnapshot, shots: ShotSink) {
        justSpotted = false
        searchRefreshLeft = (searchRefreshLeft - dt).coerceAtLeast(0f)
        if (clearance != null && !follower.arrived) {
            if (isMoving) { blockedFor = 0f; unblockDelay = UNBLOCK_FIRST_DELAY }
            else blockedFor += dt
            if (blockedFor > unblockDelay) {
                blockedFor = 0f
                // Recul progressif : deux soldats bloqués l'un contre l'autre relançaient chacun
                // un contournement toutes les 0,7 s, et noyaient la file pour tout le monde. Mais
                // un contournement complet vers le MÊME but, redemandé par les DEUX soldats qui se
                // gênent, peut reproduire le même blocage indéfiniment (chacun route autour de la
                // position actuelle de l'autre, qui a déjà bougé le temps que le chemin arrive) :
                // une fois l'escalade épuisée, on tente un simple pas de côté, sans se soucier du
                // but, pour casser la symétrie au lieu de la reproduire.
                if (unblockDelay >= UNBLOCK_MAX_DELAY && sidestep()) {
                    // Le pas de côté a pris la main ; l'état qui suit redemandera un vrai chemin
                    // vers son but une fois arrivé, comme à toute fin de trajet.
                } else {
                    val goal = follower.path[follower.path.size - 1]
                    pathTo(goal, avoidBodies = true)
                }
                unblockDelay = (unblockDelay * 2f).coerceAtMost(UNBLOCK_MAX_DELAY)
            }
        }
        recentHitLeft = (recentHitLeft - dt).coerceAtLeast(0f)
        suppressedLeft = (suppressedLeft - dt).coerceAtLeast(0f)
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
                // Il vient de le repérer : il ignore encore à quelle vitesse il court.
                trackedVelX = 0.0; trackedVelZ = 0.0
            }
            trackPlayerRun(dt, player)
        } else if (knowsPlayer) {
            memoryAge += dt
            if (memoryAge > tuning.memorySeconds) {
                knowsPlayer = false
                searchNode = -1
            }
        }

        // 2. Voir n'est pas pouvoir tirer : en réserve, un contact trop lointain le met en
        // recherche (il va prendre une position dans son secteur) au lieu de le figer en
        // contemplation. C'est ici que ça se décide, jamais au milieu d'une action.
        val engageable = seesPlayer && canEngage(player)
        val previous = state
        val decision = when {
            reloading -> State.RELOAD
            previous == State.COVER && coverHoldLeft > 0f -> State.COVER
            // Un ordre radio vaut une raison de se déplacer, même sans rien savoir du joueur.
            else -> SoldierDecision.choose(engageable, knowsPlayer || orderedNode >= 0,
                healthFraction, recentHitLeft > 0f || suppressedLeft > 0f, coverCooldown <= 0f)
        }
        state = decision
        if (state != previous) cancelRoute()
        if (state == State.COVER && previous != State.COVER) {
            coverCooldown = tuning.coverCooldownSeconds
            if (routeToCover()) {
                coverHoldLeft = tuning.coverHoldSeconds *
                    (1f + (1f - healthFraction.coerceIn(0f, 1f)) * LOW_HEALTH_COVER_EXTRA)
                coverTravelLeft = 4f
            } else {
                state = if (engageable) State.ENGAGE else State.SEARCH
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
        val selfMove = if (isMoving) tuning.aimMoveSelfPenaltyDeg else 0f
        val suppression = if (suppressedLeft > 0f) tuning.suppressionAimPenaltyDeg else 0f
        val targetError = (tuning.aimErrorMinDeg + lateral.toFloat() * tuning.aimMovePenaltyDeg +
            selfMove + suppression)
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
                p.x, p.eyeY - AIM_BELOW_EYE, p.z, world)) {
            // Ses yeux passent mais pas son canon (rebord, embrasure, angle de mur). Rester planté
            // là à le regarder est le pire de tout : il se décale pour dégager sa ligne de tir.
            blockedLineFor += dt
            if (blockedLineFor >= BLOCKED_LINE_SECONDS && follower.arrived) {
                blockedLineFor = 0f
                reposition()
            }
            return
        }
        blockedLineFor = 0f

        fire(p, shots)
        ammo--
        shotsInBurst++
        fireCooldown = if (shotsInBurst >= burstTarget) {
            // Fin de rafale : une pause, qui laisse au joueur une fenêtre pour riposter ou fuir.
            shotsInBurst = 0
            burstTarget = tuning.burstMin + rng.nextInt((tuning.burstMax - tuning.burstMin + 1).coerceAtLeast(1))
            tuning.burstPauseMin + rng.nextFloat() * (tuning.burstPauseMax - tuning.burstPauseMin)
        } else tuning.fireInterval
        if (ammo == 0) startReload()
    }

    private fun fire(p: PlayerSnapshot, shots: ShotSink) {
        val ox = follower.x; val oy = follower.y + EYE_HEIGHT - 0.15; val oz = follower.z
        // Anticipation du temps de vol de la balle, d'après l'estimation retardée de la course
        // (voir [trackPlayerRun]) : une course régulière est donc bien devancée, mais un
        // changement de direction le prend à contre-pied et la balle passe derrière.
        val distance = sqrt((p.x - ox) * (p.x - ox) + (p.z - oz) * (p.z - oz))
        val lead = (distance / tuning.bulletSpeed).coerceAtMost(1.5)
        var dx = p.x + trackedVelX * lead - ox
        var dy = (p.eyeY - AIM_BELOW_EYE) - oy
        var dz = p.z + trackedVelZ * lead - oz
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

    /**
     * Dernier recours contre un blocage qui persiste malgré l'escalade normale : un pas vers
     * n'importe quelle case voisine libre, sans se soucier du but. Casse la symétrie entre deux
     * soldats qui se redirigent l'un vers l'autre en boucle, sans repasser par un A* complet ni
     * par la file partagée — juste les liaisons déjà connues de la case courante.
     */
    private fun sidestep(): Boolean {
        val bodyClearance = clearance ?: return false
        val from = grid.nodeUnder(x, y, z)
        if (from < 0) return false
        for (e in grid.edgeStart[from] until grid.edgeStart[from + 1]) {
            val n = grid.edgeTarget[e]
            val nx = grid.nodeX[n] + 0.5; val ny = grid.nodeY[n].toDouble(); val nz = grid.nodeZ[n] + 0.5
            if (!bodyClearance.isFree(nx, ny, nz)) continue
            path.clear(); path.add(from); path.add(n)
            follower.follow(path)
            searchNode = -1   // l'état qui suit redemandera un vrai chemin en arrivant
            return true
        }
        return false
    }

    /** Petit déplacement latéral, sur le même sol, en conservant une ligne de tir. */
    private fun reposition() {
        cancelRoute()
        val from = grid.nodeUnder(x, y, z)
        if (from < 0) return
        val angle = Math.toRadians(yawDeg.toDouble())
        val side = if (rng.nextBoolean()) 1 else -1
        // Distance tirée à chaque saut : un rythme et une portée toujours identiques se
        // synchronisaient visiblement entre soldats voisins.
        val hop = REPOSITION_HOP_MIN + rng.nextDouble() * (REPOSITION_HOP_MAX - REPOSITION_HOP_MIN)
        for (sign in intArrayOf(side, -side)) {
            val nx = floor(x + cos(angle) * sign * hop).toInt()
            val nz = floor(z - sin(angle) * sign * hop).toInt()
            val n = grid.nodeAt(nx, grid.nodeY[from], nz)
            if (n < 0 || !clearsToLastKnown(n)) continue
            if (finder.findPath(from, n, path, clearance, maxCost = 4.5f) && path.size in 2..5) {
                follower.follow(path)
                return
            }
        }
        // Aucun des deux pas latéraux, au même niveau, ne dégage la ligne de tir : sur un
        // escalier, c'est la marche du dessus ou du dessous qui la dégage, jamais essayée
        // jusque-là puisque les deux candidats ci-dessus restent au Y de départ (`grid.nodeY
        // [from]`). Les liaisons réelles de la grille connaissent déjà les marches (`NavGrid`) :
        // on les prend telles quelles au lieu de recalculer des coordonnées qui ratent
        // systématiquement le changement de niveau — la tête qui dépasse sans jamais finir de
        // monter, ou sans redescendre se mettre à couvert.
        for (e in grid.edgeStart[from] until grid.edgeStart[from + 1]) {
            val n = grid.edgeTarget[e]
            if (grid.nodeY[n] == grid.nodeY[from] || !clearsToLastKnown(n)) continue
            path.clear(); path.add(from); path.add(n)
            follower.follow(path)
            return
        }
    }

    private fun clearsToLastKnown(n: Int): Boolean = LineOfSight.isClear(
        grid.nodeX[n] + 0.5, grid.nodeY[n] + EYE_HEIGHT - 0.15, grid.nodeZ[n] + 0.5,
        lastKnownX, lastKnownEyeY - AIM_BELOW_EYE, lastKnownZ, world)

    private fun actSearch(dt: Float) {
        // Ce qu'il a perçu lui-même l'emporte sur son poste, sauf sous un ordre strict : on ne
        // disperse pas une escouade en cours de regroupement. Sa destination reste bornée au
        // secteur qu'on lui a confié (rayon infini pour l'escouade engagée : elle va partout).
        val target = if (knowsPlayer && !(orderStrict && orderedNode >= 0))
            leashed(grid.nodeUnder(lastKnownX, lastKnownEyeY - EYE_HEIGHT, lastKnownZ))
        else orderedNode
        if (target != searchNode && worthRepathing(target) && searchRefreshLeft <= 0f &&
            pendingRoute == null) {
            searchRefreshLeft = .4f + rng.nextFloat() * .2f
            searchNode = target
            if (target >= 0) pathTo(target)
        }
        if (!follower.arrived) {
            isMoving = follower.advance(dt, tuning.runSpeed)
            yawDeg = follower.yawDeg
        } else if (hasRadio && !knowsPlayer) {
            // En poste, sans rien avoir vu : il balaie le secteur annoncé à la radio, plutôt que
            // de tourner sur lui-même. C'est là qu'on voit une garnison prévenue.
            lookPhase += dt * LOOK_SWEEP_PER_SEC
            yawDeg = normalizeDeg(yawTo(radioX, radioZ) +
                sin(lookPhase.toDouble()).toFloat() * LOOK_SWEEP_DEG)
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
        if (hasRadio) {
            // En réserve, il ne bouge pas, mais la radio lui a dit de quel côté ça se passe :
            // il surveille cette direction au lieu de contempler un mur au hasard.
            lookPhase += dt * LOOK_SWEEP_PER_SEC
            yawDeg = normalizeDeg(yawTo(radioX, radioZ) +
                sin(lookPhase.toDouble()).toFloat() * LOOK_SWEEP_DEG)
        }
        if (patrolWait > 0f) return
        // Une ronde coûte une recherche de chemin. Soixante hommes qui en demandent une toutes
        // les deux secondes, c'est trente recherches par seconde sur un graphe de dizaines de
        // milliers de cases — et personne pour les regarder marcher. Loin de ce que la radio
        // annonce, ils prennent leur temps ; c'est invisible et c'est dix fois moins cher.
        val far = hasRadio && !seesPlayer &&
            distSq2D(radioX, radioZ, follower.x, follower.z) > FAR_PATROL_SQ
        patrolWait = if (far) FAR_PATROL_WAIT + rng.nextFloat() * FAR_PATROL_WAIT
            else 1f + rng.nextFloat() * 1.5f
        // Prochaine destination : une case au hasard. **Tirée dans le secteur** quand il en tient
        // un : tirer dans toute la carte puis refuser ce qui en sort ne tombe pratiquement jamais
        // dedans (une centaine de cases sur des dizaines de milliers), et le soldat ne bouge plus.
        val minimum = if (leashRadius.isInfinite()) PATROL_MIN_SQ else SECTOR_PATROL_MIN_SQ
        repeat(PATROL_PICK_ATTEMPTS) {
            val n = if (leashRadius.isInfinite()) rng.nextInt(grid.nodeCount) else randomSectorNode()
            if (n < 0) return@repeat
            // Une patrouille locale ne doit pas viser une pièce quatre étages plus haut.
            if (abs(grid.nodeY[n] - follower.y) > 2.0) return@repeat
            if (!withinLeash(grid.nodeX[n] + 0.5, grid.nodeZ[n] + 0.5)) return@repeat
            val dx = grid.nodeX[n] + 0.5 - follower.x; val dz = grid.nodeZ[n] + 0.5 - follower.z
            val distSq = dx * dx + dz * dz
            if (distSq in minimum..PATROL_MAX_SQ) {
                // Le plafond de coût est ce qui borne une recherche **qui échoue** : un point tiré
                // dans le secteur tombe souvent derrière une cloison, et sans plafond serré A*
                // fouille tout l'étage avant d'abandonner. C'est le vrai prix d'une ronde.
                pathTo(n, maxCost = if (leashRadius.isInfinite()) 60f
                    else (leashRadius * SECTOR_PATH_SLACK).toFloat())
                if (!follower.arrived || pendingRoute != null) return
            }
        }
    }

    // ── Outils ────────────────────────────────────────────────────────────────

    /**
     * Le nouvel objectif vaut-il un recalcul ? Une cible qui glisse de deux blocs parce que le
     * joueur a fait un pas ne justifie pas un A* : à soixante soldats, cette agitation seule
     * sature la file et fige tout le monde.
     */
    private fun worthRepathing(target: Int): Boolean {
        if (target < 0 || searchNode < 0) return true
        val dx = grid.nodeX[target] - grid.nodeX[searchNode]
        val dy = grid.nodeY[target] - grid.nodeY[searchNode]
        val dz = grid.nodeZ[target] - grid.nodeZ[searchNode]
        return dx * dx + dz * dz + dy * dy * 4 > REPATH_MIN_SHIFT_SQ
    }

    /** Une case au hasard dans le secteur tenu, ou -1 si le tirage tombe dans un mur. */
    private fun randomSectorNode(): Int {
        val angle = rng.nextDouble() * 2.0 * Math.PI
        val radius = leashRadius * sqrt(rng.nextDouble())   // tirage uniforme sur le disque
        return grid.nodeUnder(leashX + cos(angle) * radius, follower.y, leashZ + sin(angle) * radius)
    }

    private fun withinLeash(tx: Double, tz: Double): Boolean {
        if (leashRadius.isInfinite()) return true
        val dx = tx - leashX; val dz = tz - leashZ
        return dx * dx + dz * dz <= leashRadius * leashRadius
    }

    /**
     * Peut-il ouvrir le feu sur ce qu'il voit ? En réserve, il ne prend que les duels de son
     * voisinage : un homme en poste ne canarde pas à travers tout le quartier. Une fois son
     * escouade engagée, la laisse saute et seule la portée de son arme compte.
     */
    private fun canEngage(p: PlayerSnapshot): Boolean {
        if (leashRadius.isInfinite()) return true
        val dx = p.x - follower.x; val dz = p.z - follower.z
        val r = tuning.reserveEngageRange
        return dx * dx + dz * dz <= r * r
    }

    /**
     * Ramène une destination dans le secteur tenu : il avance vers le joueur jusqu'au bord de sa
     * zone, et s'arrête là. Sans secteur, la destination est rendue telle quelle.
     */
    private fun leashed(node: Int): Int {
        if (node < 0 || leashRadius.isInfinite()) return node
        val tx = grid.nodeX[node] + 0.5; val tz = grid.nodeZ[node] + 0.5
        if (withinLeash(tx, tz)) return node
        val dx = tx - leashX; val dz = tz - leashZ
        val d = sqrt(dx * dx + dz * dz)
        if (d < 1e-6) return node
        return grid.nodeUnder(leashX + dx / d * leashRadius, follower.y, leashZ + dz / d * leashRadius)
    }

    private fun pathTo(goal: Int, maxCost: Float = Float.POSITIVE_INFINITY, avoidBodies: Boolean = false) {
        val from = grid.nodeUnder(follower.x, follower.y, follower.z)
        if (routes != null && from >= 0) {
            if (pendingRoute != null) return
            // On ne le fige pas pendant le calcul : il poursuit son trajet en cours et bascule à
            // l'arrivée du nouveau. Sinon une escouade qui remet son plan à jour en marchant
            // s'arrête net toutes les quelques secondes, et arrive en ordre dispersé.
            if (follower.arrived) follower.stop()
            // La navigation statique suffit normalement ; la collision réelle reste contrôlée
            // à chaque pas. En cas de blocage, contourner les corps, sans interdire la cible
            // occupée par le joueur (sinon A* explorerait toute la tour pour la refuser).
            val routingClearance = if (avoidBodies && clearance != null) BodyClearance { x, y, z ->
                (floor(x).toInt() == grid.nodeX[goal] && floor(y).toInt() == grid.nodeY[goal] &&
                    floor(z).toInt() == grid.nodeZ[goal]) || clearance.isFree(x, y, z)
            } else null
            // L'escouade engagée (laisse infinie) passe devant : c'est elle qui doit traverser
            // la carte tout de suite. Sans cette priorité, ses trajets attendent derrière ceux de
            // toutes les réserves qui ont entendu un tir, et elle avance au ralenti — d'autant
            // plus que la manche avance et que la garnison entière est en alerte.
            pendingRoute = routes.request(from, goal, maxCost, routingClearance,
                    priority = leashRadius.isInfinite()) { result ->
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
     *
     * Entre deux abris à distance égale, celui qui l'écarte le plus de sa position actuelle **vu
     * du joueur** l'emporte : sans ce biais, il file toujours vers le recoin le plus proche, quitte
     * à rester quasiment sur place — on dirait qu'il se planque, pas qu'il change d'angle. Avec, le
     * même réflexe de repli devient un vrai déplacement de flanc.
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
        val bearingHere = atan2(x - lastKnownX, z - lastKnownZ)
        // Chaque candidat coûte une ligne de vue, soit une vingtaine de pas de voxels — et dans
        // un bâtiment, chaque pas sur un escalier ou une dalle déclenche un test de volume. Le
        // balayage complet, c'est deux mille lignes de vue pour un seul soldat qui recharge.
        var probes = COVER_PROBE_BUDGET
        scan@ for (nz in cz - r..cz + r) for (nx in cx - r..cx + r) {
            val distSq = (nx - cx) * (nx - cx) + (nz - cz) * (nz - cz)
            if (distSq > r * r || distSq >= coverDistances.last()) continue
            for (ny in maxOf(1, floor(y).toInt() - 2)..minOf(grid.sizeY - 1, floor(y).toInt() + 2)) {
                val n = grid.nodeAt(nx, ny, nz)
                if (n < 0) continue
                if (probes-- <= 0) break@scan
                val hidden = !LineOfSight.isClear(lastKnownX, lastKnownEyeY, lastKnownZ,
                    nx + 0.5, ny + EYE_HEIGHT, nz + 0.5, world)
                if (hidden) {
                    val bearingThere = atan2(nx + 0.5 - lastKnownX, nz + 0.5 - lastKnownZ)
                    val swing = abs(angleDiffRad(bearingThere, bearingHere)) / Math.PI
                    val distance = distSq.toDouble() + abs(ny - y) * 2.0 - swing * COVER_SWING_BONUS
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

    private fun distSq2D(ax: Double, az: Double, bx: Double, bz: Double): Double {
        val dx = ax - bx; val dz = az - bz
        return dx * dx + dz * dz
    }

    private fun yawTo(tx: Double, tz: Double): Float =
        Math.toDegrees(atan2(tx - follower.x, tz - follower.z)).toFloat()

    private companion object {
        const val EYE_HEIGHT = 1.62
        /** Il vise le haut du torse plutôt que les yeux. */
        const val AIM_BELOW_EYE = 0.35
        const val LOOK_AROUND_DEG_PER_SEC = 60f
        /** Balayage du regard autour de la direction annoncée à la radio. */
        const val LOOK_SWEEP_PER_SEC = 1.1f
        const val LOOK_SWEEP_DEG = 55f
        /** Temps passé à voir le joueur sans ligne de tir avant de changer de place. */
        const val BLOCKED_LINE_SECONDS = .45f
        /** Portée d'un saut latéral en combat : jamais deux fois la même distance. */
        const val REPOSITION_HOP_MIN = 1.5
        const val REPOSITION_HOP_MAX = 3.0
        /** Temps de planque supplémentaire, en proportion, quand il est au plus bas. */
        const val LOW_HEALTH_COVER_EXTRA = 1.5f
        const val PATROL_PICK_ATTEMPTS = 20
        const val PATROL_MIN_SQ = 8.0 * 8.0
        /** Dans un secteur de quelques blocs, une ronde de trois blocs est déjà un déplacement. */
        const val SECTOR_PATROL_MIN_SQ = 3.0 * 3.0
        const val PATROL_MAX_SQ = 30.0 * 30.0
        /** Déplacement minimal de l'objectif avant de refaire un trajet. */
        const val REPATH_MIN_SHIFT_SQ = 3 * 3
        const val UNBLOCK_FIRST_DELAY = .7f
        const val UNBLOCK_MAX_DELAY = 4f
        /** Marge de détour tolérée pour rejoindre un point de ronde dans son secteur. */
        const val SECTOR_PATH_SLACK = 1.8
        /** Au-delà de cette distance de ce qu'annonce la radio, les rondes s'espacent. */
        const val FAR_PATROL_SQ = 45.0 * 45.0
        const val FAR_PATROL_WAIT = 6f
        /** Lignes de vue au plus pour trouver un abri : au-delà, on prend ce qu'on a trouvé. */
        const val COVER_PROBE_BUDGET = 160
        /** Poids du changement d'angle dans le choix d'un abri : un plein demi-tour (180°) vaut
         * ce nombre de blocs-carrés de distance en moins, assez pour préférer un abri qui déplace
         * vraiment sans faire ignorer un recoin bien plus proche du même côté. */
        const val COVER_SWING_BONUS = 40.0

        /** Écart entre deux angles en radians, ramené à [-π, π]. */
        fun angleDiffRad(a: Double, b: Double): Double {
            var d = (a - b) % (2 * Math.PI)
            if (d > Math.PI) d -= 2 * Math.PI
            if (d < -Math.PI) d += 2 * Math.PI
            return d
        }

        fun normalizeDeg(a: Float): Float {
            var v = a % 360f
            if (v > 180f) v -= 360f
            if (v < -180f) v += 360f
            return v
        }

        fun angleDiff(a: Float, b: Float): Float = normalizeDeg(a - b)
    }
}
