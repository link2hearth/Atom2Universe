package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.ai.IntList
import com.Atom2Universe.app.games.caves.ai.LineOfSight
import com.Atom2Universe.app.games.caves.ai.NavGrid
import com.Atom2Universe.app.games.caves.ai.PathFinder
import com.Atom2Universe.app.games.caves.ai.PathFollower
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyState
import com.Atom2Universe.app.games.caves.node.MobDef
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.MapSource
import com.Atom2Universe.app.games.caves.world.isDecoration
import com.Atom2Universe.app.games.caves.world.isWater
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Le mode Assaut : une carte préparée, jouée au fusil (voir CAVE_WORLD_ASSAUT.md).
 *
 * Phase 2 : des manches contre des mannequins immobiles. Chaque manche place [AssaultMatch.targetsPerRound]
 * mannequins au hasard sur la carte ; il faut tous les abattre avant la fin du chrono. Toutes les
 * armes sont prêtées, les munitions de réserve sont illimitées, l'heure est figée à midi, et la
 * carte ne se creuse pas.
 *
 * Phase 3 : un mannequin **coureur** (bleu) teste la navigation. Il suit le joueur en contournant
 * les obstacles et s'arrête dès qu'il le voit à moins de [RUNNER_STOP_DISTANCE] blocs. Il ne compte
 * pas dans la manche ; abattu, il repart du coin opposé. [showPath] trace son chemin au sol.
 *
 * Mannequins et coureur sont de simples [Enemy] rangés dans la liste de l'EnemyManager : le renderer
 * les dessine et les balles les touchent comme des monstres. Mais l'EnemyManager n'est jamais mis à
 * jour ici : c'est ce mode qui décide de leurs mouvements.
 */
internal class AssaultMode(
    private val r: CaveRenderer,
    private val source: MapSource,
) : GameMode {

    val match = AssaultMatch()

    /** Nouvel état de la partie à afficher. Appelé sur le thread GL, une dizaine de fois par seconde au plus. */
    @Volatile var onStatus: ((AssaultMatch.Status) -> Unit)? = null

    /** Un mannequin vient de tomber d'un tir à la tête. Appelé sur le thread GL. */
    @Volatile var onHeadshotKill: (() -> Unit)? = null

    /** Tracer au sol le chemin du coureur (bouton 🧭). */
    @Volatile var showPath = false

    override val allowsWorldEdits: Boolean get() = false
    override val infiniteAmmo: Boolean get() = true
    override val fixedTimeOfDayMs: Long get() = NOON_MS
    override val headshotMultiplier: Float get() = HEADSHOT_MULTIPLIER

    private val targets get() = r.enemyManager.enemies

    // Dernier coup reçu par chaque mannequin (par id) : c'est lui qui dit s'il est tombé d'un tir à la tête.
    private val lastHitWasHead = HashMap<Int, Boolean>()
    private var nextTargetId = 1
    private val rng = Random.Default
    private var statusTimer = 0f

    // ── Navigation (construite dans onSurfaceCreated, une fois les blocs connus) ──
    private val solid = SolidGrid { x, y, z -> blocksMovement(source.map.blockAt(x, y, z)) }
    private var navGrid: NavGrid? = null
    private var pathFinder: PathFinder? = null
    private var follower: PathFollower? = null
    private var runner: Enemy? = null
    private val pathBuffer = IntList(128)
    private var repathTimer = 0f

    override fun spawnPoint(): FloatArray = source.spawnPoint()

    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        // Pas encore d'équipement de match : on prête toutes les armes à distance.
        r.giveWeaponTestKit()
        r.loadoutChangedCallback?.invoke()

        val map = source.map
        val grid = NavGrid.build(map.sizeX, map.sizeY, map.sizeZ, solid)
        navGrid = grid
        pathFinder = PathFinder(grid)
        follower = PathFollower(grid)
    }

    override fun onPlayerPlaced(x: Double, y: Double, z: Double) = Unit

    override fun onEnemyHit(enemy: Enemy, headshot: Boolean) {
        lastHitWasHead[enemy.id] = headshot
    }

    override fun update(dt: Float) {
        // Tombé hors de la carte : retour au point d'apparition.
        if (r.camera.playerY < source.originY - FALL_LIMIT) respawnPlayer()

        // L'EnemyManager ne tourne pas dans ce mode : on éteint nous-mêmes le flash des coups.
        for (t in targets) if (t.hitFlash > 0f) t.hitFlash -= dt

        updateRunner(dt)
        collectFallenTargets()

        var forceStatus = false
        when (match.update(dt)) {
            AssaultMatch.Event.ROUND_STARTED -> { startRound(); forceStatus = true }
            AssaultMatch.Event.ROUND_ENDED -> { clearTargets(); forceStatus = true }
            AssaultMatch.Event.NONE -> Unit
        }

        statusTimer += dt
        if (forceStatus || statusTimer >= STATUS_INTERVAL) {
            statusTimer = 0f
            onStatus?.invoke(match.status())
        }
    }

    override fun debugSegments(out: DoubleArray): Int {
        if (!showPath) return 0
        val grid = navGrid ?: return 0
        val f = follower ?: return 0
        if (f.arrived) return 0
        val max = out.size / 6
        var count = 0
        // Du coureur à sa prochaine case, puis de case en case jusqu'au bout.
        var fromX = f.x + source.originX; var fromY = f.y + source.originY + PATH_LIFT; var fromZ = f.z + source.originZ
        for (i in f.nextIndex until f.path.size) {
            if (count >= max) break
            val n = f.path[i]
            val toX = grid.nodeX[n] + 0.5 + source.originX
            val toY = grid.nodeY[n] + source.originY + PATH_LIFT
            val toZ = grid.nodeZ[n] + 0.5 + source.originZ
            val o = count * 6
            out[o] = fromX; out[o + 1] = fromY; out[o + 2] = fromZ
            out[o + 3] = toX; out[o + 4] = toY; out[o + 5] = toZ
            count++
            fromX = toX; fromY = toY; fromZ = toZ
        }
        return count
    }

    // ── Coureur ───────────────────────────────────────────────────────────────

    private fun updateRunner(dt: Float) {
        val grid = navGrid ?: return
        val finder = pathFinder ?: return
        val f = follower ?: return
        val bot = runner ?: return
        if (bot.hp <= 0) { resetRunner(); return }

        // Le joueur, en coordonnées de la carte (pieds et yeux).
        val camera = r.camera
        val px = camera.playerX - source.originX
        val pz = camera.playerZ - source.originZ
        val eyeY = camera.playerY - source.originY
        val feetY = eyeY - EYE_HEIGHT

        val dx = px - f.x; val dz = pz - f.z
        val distance = sqrt(dx * dx + dz * dz)
        val seesPlayer = distance <= RUNNER_STOP_DISTANCE &&
            LineOfSight.isClear(f.x, f.y + EYE_HEIGHT, f.z, px, eyeY, pz, solid)

        if (seesPlayer) {
            // Assez près et rien entre nous : il s'arrête et regarde le joueur.
            f.stop()
            bot.state = EnemyState.WANDER
            if (distance > 0.1) bot.yaw = Math.toDegrees(atan2(dx, dz)).toFloat()
        } else {
            repathTimer -= dt
            if (repathTimer <= 0f || f.arrived) {
                repathTimer = REPATH_INTERVAL
                val from = grid.nodeUnder(f.x, f.y, f.z)
                val to = grid.nodeUnder(px, feetY, pz)
                if (from >= 0 && to >= 0 && from != to && finder.findPath(from, to, pathBuffer)) f.follow(pathBuffer)
            }
            if (f.advance(dt, RUNNER_SPEED)) {
                bot.state = EnemyState.CHASE   // jambes et bras qui balancent
                bot.animTime += dt
                bot.yaw = f.yawDeg
            } else {
                bot.state = EnemyState.WANDER
            }
        }
        bot.x = f.x + source.originX
        bot.y = f.y + source.originY
        bot.z = f.z + source.originZ
    }

    /** (Re)place le coureur au dernier point d'apparition (coin opposé au joueur), PV au maximum. */
    private fun resetRunner() {
        val f = follower ?: return
        val spawn = source.spawnPoint(1)
        f.place(
            spawn[0].toDouble() - source.originX,
            spawn[1].toDouble() - EYE_HEIGHT - source.originY,
            spawn[2].toDouble() - source.originZ,
        )
        val bot = runner ?: Enemy(RUNNER_ID, RUNNER, 0.0, 0.0, 0.0).also {
            runner = it
            targets += it
        }
        if (bot !in targets) targets += bot
        bot.hp = bot.maxHp
        bot.hitFlash = 0f
        bot.state = EnemyState.WANDER
        bot.x = f.x + source.originX; bot.y = f.y + source.originY; bot.z = f.z + source.originZ
        lastHitWasHead.remove(bot.id)
        repathTimer = 0f
    }

    // ── Manches ───────────────────────────────────────────────────────────────

    private fun collectFallenTargets() {
        var i = targets.size - 1
        while (i >= 0) {
            val t = targets[i]
            if (t.hp <= 0 && t !== runner) {
                targets.removeAt(i)
                val headshot = lastHitWasHead.remove(t.id) == true
                if (headshot) onHeadshotKill?.invoke()
                if (match.onTargetDown(headshot) == AssaultMatch.Event.ROUND_ENDED) {
                    // Dernier mannequin : la manche est gagnée, le bilan s'affiche tout de suite.
                    clearTargets()
                    onStatus?.invoke(match.status())
                    return
                }
            }
            i--
        }
    }

    private fun startRound() {
        clearTargets()
        respawnPlayer()
        resetRunner()
        placeTargets()
    }

    /** Retire les mannequins de la manche ; le coureur reste. */
    private fun clearTargets() {
        val bot = runner
        targets.clear()
        lastHitWasHead.clear()
        if (bot != null) targets += bot
    }

    /**
     * Pose les mannequins au hasard : au niveau du sol de départ (ni sur un mur, ni dans un trou),
     * loin du point d'apparition, et pas collés les uns aux autres.
     */
    private fun placeTargets() {
        val spawn = source.spawnPoint()
        val spawnX = spawn[0].toDouble(); val spawnZ = spawn[2].toDouble()
        val floorY = (spawn[1] - EYE_HEIGHT).toInt()
        val map = source.map
        val spanX = map.sizeX - 2 * TARGET_MARGIN
        val spanZ = map.sizeZ - 2 * TARGET_MARGIN
        if (spanX <= 0 || spanZ <= 0) return

        var placed = 0
        var attempts = 0
        while (placed < match.targetsPerRound && attempts < MAX_PLACEMENT_ATTEMPTS) {
            attempts++
            val bx = source.originX + TARGET_MARGIN + rng.nextInt(spanX)
            val bz = source.originZ + TARGET_MARGIN + rng.nextInt(spanZ)
            if (source.skyTopY(bx, bz) != floorY) continue

            val x = bx + 0.5; val z = bz + 0.5
            if (distSq(x, z, spawnX, spawnZ) < MIN_DIST_FROM_SPAWN * MIN_DIST_FROM_SPAWN) continue
            if (targets.any { distSq(x, z, it.x, it.z) < MIN_TARGET_SPACING * MIN_TARGET_SPACING }) continue

            val target = Enemy(nextTargetId++, DUMMY, x, floorY.toDouble(), z)
            target.hp = target.maxHp
            // Face au point d'apparition, pour que le joueur voie la cible peinte sur le torse.
            target.yaw = Math.toDegrees(atan2(spawnX - x, spawnZ - z)).toFloat()
            targets += target
            placed++
        }
    }

    private fun respawnPlayer() {
        val spawn = source.spawnPoint()
        val camera = r.camera
        camera.playerX = spawn[0].toDouble()
        camera.playerY = spawn[1].toDouble()
        camera.playerZ = spawn[2].toDouble()
        // Regard tourné vers le centre de la carte.
        val centerX = source.originX + source.map.sizeX / 2.0
        val centerZ = source.originZ + source.map.sizeZ / 2.0
        camera.yaw = Math.toDegrees(atan2(centerX - camera.playerX, centerZ - camera.playerZ)).toFloat()
        camera.pitch = 0f
        r.physics.reset()
    }

    private fun distSq(ax: Double, az: Double, bx: Double, bz: Double): Double {
        val dx = ax - bx; val dz = az - bz
        return dx * dx + dz * dz
    }

    private companion object {
        /** Blocs de chute sous la carte avant d'être ramené. */
        const val FALL_LIMIT = 16

        /** Midi dans le cycle de CaveRenderer (6 h = 0 ms, 100 000 ms par heure de jour). */
        const val NOON_MS = 600_000L

        const val HEADSHOT_MULTIPLIER = 2f
        const val EYE_HEIGHT = 1.62f
        const val STATUS_INTERVAL = 0.1f

        const val TARGET_MARGIN = 4
        const val MIN_DIST_FROM_SPAWN = 20.0
        const val MIN_TARGET_SPACING = 5.0
        const val MAX_PLACEMENT_ATTEMPTS = 500

        const val RUNNER_ID = -1
        const val RUNNER_SPEED = 4f
        const val RUNNER_STOP_DISTANCE = 8.0
        const val REPATH_INTERVAL = 0.5f
        /** Hauteur du tracé au-dessus du sol, pour qu'il ne clignote pas dans l'herbe. */
        const val PATH_LIFT = 0.06

        /** Même règle que la physique du joueur : l'air, la déco et l'eau ne bloquent pas. */
        fun blocksMovement(block: Short) = block != AIR && !isDecoration(block) && !isWater(block)

        /**
         * Mannequin : 100 PV (3 à 5 balles de pistolet au corps, 2 ou 3 à la tête), aucune attaque,
         * aucun déplacement, insensible aux effets élémentaires (qui ne s'appliqueraient pas ici).
         */
        val DUMMY = MobDef(
            id = "assault_dummy", hpBase = 100, damageBase = 0, speed = 0f,
            attackRange = 0.0, detectRange = 0.0, eyeHeight = 1.7f, radius = 0.45f,
            spriteScale = 0.9f, hpScalePerLevel = 1.0, hpScaleCap = 1.0, damageScalePer3Lvl = 0,
            speedScalePerLevel = 0f, biomes = emptyList(), model = "dummy", spawnZoneMin = 0,
            spawnWeight = 0f, lootTable = "", behavior = "static", bossEligible = false, xpBase = 0,
            resistances = mapOf("bleed" to 0f, "poison" to 0f, "fire" to 0f, "ice" to 0f, "electric" to 0f),
        )

        /** Le coureur : même mannequin, en bleu, qui marche. */
        val RUNNER = DUMMY.copy(id = "assault_runner", model = "runner", speed = RUNNER_SPEED)
    }
}
