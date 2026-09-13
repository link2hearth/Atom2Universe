package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.node.MobDef
import com.Atom2Universe.app.games.caves.world.MapSource
import kotlin.math.atan2
import kotlin.random.Random

/**
 * Le mode Assaut : une carte préparée, jouée au fusil (voir CAVE_WORLD_ASSAUT.md).
 *
 * Phase 2 : des manches contre des mannequins immobiles. Chaque manche place [AssaultMatch.targetsPerRound]
 * mannequins au hasard sur la carte ; il faut tous les abattre avant la fin du chrono. Toutes les
 * armes sont prêtées, les munitions de réserve sont illimitées, l'heure est figée à midi, et la
 * carte ne se creuse pas.
 *
 * Les mannequins sont de simples [Enemy] rangés dans la liste de l'EnemyManager : le renderer les
 * dessine et les balles les touchent comme des monstres. Mais l'EnemyManager n'est jamais mis à
 * jour ici, donc ils ne bougent pas et n'attaquent pas.
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

    override fun spawnPoint(): FloatArray = source.spawnPoint()

    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        // Pas encore d'équipement de match : on prête toutes les armes à distance.
        r.giveWeaponTestKit()
        r.loadoutChangedCallback?.invoke()
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

    // ── Manches ───────────────────────────────────────────────────────────────

    private fun collectFallenTargets() {
        var i = targets.size - 1
        while (i >= 0) {
            val t = targets[i]
            if (t.hp <= 0) {
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
        placeTargets()
    }

    private fun clearTargets() {
        targets.clear()
        lastHitWasHead.clear()
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

        var attempts = 0
        while (targets.size < match.targetsPerRound && attempts < MAX_PLACEMENT_ATTEMPTS) {
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
    }
}
