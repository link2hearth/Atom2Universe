package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.ai.NavGrid
import com.Atom2Universe.app.games.caves.ai.PathFinder
import com.Atom2Universe.app.games.caves.ai.PlayerSnapshot
import com.Atom2Universe.app.games.caves.ai.ShotSink
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import com.Atom2Universe.app.games.caves.ai.Soldier
import com.Atom2Universe.app.games.caves.ai.SoldierTuning
import com.Atom2Universe.app.games.caves.ai.BodyClearance
import com.Atom2Universe.app.games.caves.ai.SoldierCollision
import com.Atom2Universe.app.games.caves.entity.RangedProfile
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyState
import com.Atom2Universe.app.games.caves.entity.Projectile
import com.Atom2Universe.app.games.caves.entity.ProjectileKind
import com.Atom2Universe.app.games.caves.entity.WeaponColor
import com.Atom2Universe.app.games.caves.entity.WeaponDef
import com.Atom2Universe.app.games.caves.entity.WeaponVariant
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.node.MobDef
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.MapSource
import com.Atom2Universe.app.games.caves.world.isDecoration
import com.Atom2Universe.app.games.caves.world.isWater
import kotlin.math.atan2
import kotlin.random.Random

/**
 * Le mode Assaut : une carte préparée, jouée au fusil (voir CAVE_WORLD_ASSAUT.md).
 *
 * Chaque manche oppose le joueur à [SOLDIERS_PER_ROUND] soldats, qui apparaissent de l'autre côté
 * de la carte. Il faut tous les éliminer avant la fin du chrono ; mourir perd la manche. Toutes les
 * armes sont prêtées, les munitions de réserve sont illimitées, l'heure est figée à midi, et la
 * carte ne se creuse pas.
 *
 * Les soldats pensent avec [Soldier] (Kotlin pur, testé seul). Leur corps est un simple [Enemy]
 * rangé dans la liste de l'EnemyManager : le renderer le dessine et les balles du joueur le
 * touchent. L'EnemyManager n'est jamais mis à jour ici : c'est ce mode qui place les corps là où
 * les cerveaux ont décidé d'aller.
 */
internal class AssaultMode(
    private val r: CaveRenderer,
    private val source: MapSource,
) : GameMode {

    val match = AssaultMatch(targetsPerRound = SOLDIERS_PER_ROUND, roundSeconds = ROUND_SECONDS)

    /** Nouvel état de la partie à afficher. Appelé sur le thread GL, une dizaine de fois par seconde au plus. */
    @Volatile var onStatus: ((AssaultMatch.Status) -> Unit)? = null

    /** Un soldat vient de tomber d'un tir à la tête. Appelé sur le thread GL. */
    @Volatile var onHeadshotKill: (() -> Unit)? = null

    /** Tracer au sol le chemin des soldats (bouton 🧭). */
    @Volatile var showPath = false

    override val allowsWorldEdits: Boolean get() = false
    override val infiniteAmmo: Boolean get() = true
    override val fixedTimeOfDayMs: Long get() = NOON_MS
    override val headshotMultiplier: Float get() = HEADSHOT_MULTIPLIER

    /** Un soldat : son corps (dessiné, touché par les balles) et son cerveau. */
    private class Trooper(val body: Enemy, val brain: Soldier, val damage: Int)

    private val units = ArrayList<Trooper>(SOLDIERS_PER_ROUND)
    private val bodies get() = r.enemyManager.enemies

    // Dernier coup reçu par chaque soldat (par id) : c'est lui qui dit s'il est tombé d'un tir à la tête.
    private val lastHitWasHead = HashMap<Int, Boolean>()
    private var nextSoldierId = 1
    private val rng = Random.Default
    private var statusTimer = 0f

    // ── Navigation (construite dans onSurfaceCreated, une fois les blocs connus) ──
    private val solid = object : SolidGrid {
        override fun isSolid(x: Int, y: Int, z: Int) = blocksMovement(source.map.blockAt(x, y, z))

        override fun blocksSight(x: Int, y: Int, z: Int, x0: Double, y0: Double, z0: Double,
                                 dx: Double, dy: Double, dz: Double): Boolean {
            if (!isSolid(x, y, z)) return false
            val def = com.Atom2Universe.app.games.caves.node.BlockRegistry.get(source.map.blockAt(x, y, z))
                ?: return true
            if (!def.stairs && !def.slab && def.blockHeight >= 1f) return true
            return com.Atom2Universe.app.games.caves.world.PartialBlockModel.intersect(
                source.map.metaAt(x, y, z), x0 - x, y0 - y, z0 - z, dx, dy, dz, 1.0,
                def.slab, def.blockHeight,
                com.Atom2Universe.app.games.caves.world.StairConnections.maskAt(
                    x, y, z, source.map::blockAt, source.map::metaAt)) != null
        }
    }
    private var navGrid: NavGrid? = null
    private var pathFinder: PathFinder? = null

    // ── Le joueur vu par les soldats (coordonnées de la carte) ──
    private val player = PlayerSnapshot()
    private var prevPlayerX = Double.NaN
    private var prevPlayerZ = Double.NaN

    // Balles des soldats : même aspect que les balles du joueur, dégâts fixes.
    private val bulletLook = WeaponDef(WeaponColor.WHITE, WeaponVariant.SQUARE)
    private val soldierTuning = SoldierTuning()
    private var firingBody: Enemy? = null
    private var firingUnit: Trooper? = null
    private val shotSink = ShotSink { x, y, z, dx, dy, dz ->
        firingBody?.shotRecoil = .16f
        val unit = firingUnit ?: error("Tir sans soldat actif")
        r.projectiles.add(Projectile(
            x + source.originX, y + source.originY, z + source.originZ, dx, dy, dz,
            unit.brain.tuning.bulletSpeed, unit.damage, bulletLook,
            kind = ProjectileKind.BULLET, maxRange = unit.brain.tuning.bulletRange, fromEnemy = true,
        ))
        r.eventBus.publish(GameEvent.EnemyFired)
    }

    override fun spawnPoint(): FloatArray = source.spawnPoint()

    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        r.physics.dynamicCollision = { x, feetY, z, height ->
            units.any { it.body.hp > 0 && SoldierCollision.overlaps(
                it.brain.x, it.brain.y, it.brain.z,
                x - source.originX, feetY - source.originY, z - source.originZ, height) }
        }
        // Pas encore d'équipement de match : on prête toutes les armes à distance.
        r.giveWeaponTestKit()
        r.loadoutChangedCallback?.invoke()
        r.playerNode.setMaxHp(PLAYER_MAX_HP, PLAYER_MAX_HP)

        val map = source.map
        val grid = NavGrid.build(map.sizeX, map.sizeY, map.sizeZ, solid)
        navGrid = grid
        pathFinder = PathFinder(grid)
    }

    override fun onPlayerPlaced(x: Double, y: Double, z: Double) = kotlin.Unit

    override fun onEnemyHit(enemy: Enemy, headshot: Boolean) {
        lastHitWasHead[enemy.id] = headshot
        r.eventBus.publish(GameEvent.MobHit(false))
        // Touché : il sait d'où vient le tir, même sans avoir vu le tireur.
        val unit = units.firstOrNull { it.body === enemy } ?: return
        unit.brain.healthFraction = enemy.hp.toFloat() / enemy.maxHp.coerceAtLeast(1)
        refreshPlayerPosition()
        unit.brain.onDamaged(player.x, player.eyeY, player.z)
    }

    override fun onPlayerShot(damage: Int, dirX: Double, dirZ: Double) {
        if (match.phase != AssaultMatch.Phase.PLAYING || r.playerNode.hp <= 0) return
        r.playerNode.applyDamage(damage)
        r.eventBus.publish(GameEvent.PlayerHit(damage, dirX.toFloat(), dirZ.toFloat()))
    }

    override fun onPlayerFired() {
        refreshPlayerPosition()
        for (u in units) u.brain.hearShot(player.x, player.eyeY, player.z)
    }

    override fun update(dt: Float) {
        // Tombé hors de la carte : retour au point d'apparition.
        if (r.camera.playerY < source.originY - FALL_LIMIT) respawnPlayer()

        // L'EnemyManager ne tourne pas dans ce mode : on éteint nous-mêmes le flash des coups.
        for (b in bodies) if (b.hitFlash > 0f) b.hitFlash -= dt

        updatePlayerSnapshot(dt)

        var forceStatus = false
        if (match.phase == AssaultMatch.Phase.PLAYING && r.playerNode.hp <= 0 &&
            match.onPlayerDied() == AssaultMatch.Event.ROUND_ENDED) {
            clearSoldiers()
            forceStatus = true
        }

        updateSoldiers(dt)
        if (collectFallenSoldiers()) forceStatus = true

        when (match.update(dt)) {
            AssaultMatch.Event.ROUND_STARTED -> { startRound(); forceStatus = true }
            AssaultMatch.Event.ROUND_ENDED -> { clearSoldiers(); forceStatus = true }
            AssaultMatch.Event.NONE -> kotlin.Unit
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
        val max = out.size / 6
        var count = 0
        for (u in units) {
            val f = u.brain.follower
            if (f.arrived) continue
            // Du soldat à sa prochaine case, puis de case en case jusqu'au bout.
            var fromX = f.x + source.originX; var fromY = f.y + source.originY + PATH_LIFT; var fromZ = f.z + source.originZ
            for (i in f.nextIndex until f.path.size) {
                if (count >= max) return count
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
        }
        return count
    }

    // ── Soldats ───────────────────────────────────────────────────────────────

    private fun updateSoldiers(dt: Float) {
        for (u in units) {
            // Les impacts sont traités avant ce passage : un soldat abattu ne doit plus tirer.
            if (u.body.hp <= 0) continue
            val brain = u.brain
            brain.healthFraction = u.body.hp.toFloat() / u.body.maxHp.coerceAtLeast(1)
            u.body.shotRecoil = (u.body.shotRecoil - dt).coerceAtLeast(0f)
            firingBody = u.body
            firingUnit = u
            brain.update(dt, player, shotSink)
            firingBody = null
            firingUnit = null
            if (brain.justSpotted) r.eventBus.publish(GameEvent.MobNearby(false))

            val body = u.body
            body.weaponReload = brain.reloadProgress
            body.x = brain.x + source.originX
            body.y = brain.y + source.originY
            body.z = brain.z + source.originZ
            body.yaw = brain.yawDeg
            body.resting = !brain.isMoving
            if (brain.isMoving) {
                body.state = EnemyState.CHASE   // jambes et bras qui balancent
                body.animTime += dt
            } else {
                body.state = EnemyState.WANDER
            }
        }
    }

    /** Retire les soldats abattus. Renvoie vrai si l'affichage de la partie doit être rafraîchi. */
    private fun collectFallenSoldiers(): Boolean {
        var changed = false
        var i = units.size - 1
        while (i >= 0) {
            val body = units[i].body
            if (body.hp <= 0) {
                units.removeAt(i)
                bodies.remove(body)
                changed = true
                val headshot = lastHitWasHead.remove(body.id) == true
                if (headshot) onHeadshotKill?.invoke()
                // Pas MobDied : lui déclenche le butin de la survie, qui ne connaît pas les soldats.
                r.eventBus.publish(GameEvent.SoldierDown)
                if (match.onTargetDown(headshot) == AssaultMatch.Event.ROUND_ENDED) {
                    clearSoldiers()
                    return true
                }
            }
            i--
        }
        return changed
    }

    private fun startRound() {
        clearSoldiers()
        respawnPlayer()
        r.playerNode.setMaxHp(PLAYER_MAX_HP, PLAYER_MAX_HP)
        spawnSoldiers()
    }

    private fun clearSoldiers() {
        units.clear()
        bodies.clear()
        lastHitWasHead.clear()
        // Les balles encore en vol ne doivent pas toucher le joueur pendant la pause.
        r.projectiles.removeAll { it.fromEnemy }
    }

    /**
     * Fait apparaître les soldats près du point d'apparition adverse (coin opposé), bien loin du
     * joueur et un peu espacés entre eux.
     */
    private fun spawnSoldiers() {
        val grid = navGrid ?: return
        val finder = pathFinder ?: return
        if (grid.nodeCount == 0) return
        val playerSpawn = source.spawnPoint(0)
        val enemySpawn = source.spawnPoint(1)
        val px = playerSpawn[0].toDouble() - source.originX; val pz = playerSpawn[2].toDouble() - source.originZ
        val ex = enemySpawn[0].toDouble() - source.originX; val ez = enemySpawn[2].toDouble() - source.originZ

        // Sur la carte intégrée, déployer au sol dans la cour est, jamais sur les toits.
        val deployment = if (source.map.name == com.Atom2Universe.app.games.caves.world.BuiltinMaps.ARENA_ID &&
            source.map.sizeX == com.Atom2Universe.app.games.caves.world.BuiltinMaps.ARENA_SIZE &&
            source.map.sizeZ == com.Atom2Universe.app.games.caves.world.BuiltinMaps.ARENA_DEPTH &&
            source.map.spawnsB.isNotEmpty()) {
            (0 until grid.nodeCount).filter { n ->
                grid.nodeY[n] == source.map.spawnsB.first().y &&
                    grid.nodeX[n] >= source.map.sizeX - 23 &&
                    kotlin.math.abs(grid.nodeZ[n] + 0.5 - ez) <= 13 &&
                    distSq(grid.nodeX[n] + 0.5, grid.nodeZ[n] + 0.5, ex, ez) <=
                    ENEMY_SPAWN_RADIUS * ENEMY_SPAWN_RADIUS
            }
        } else null
        if (deployment != null && deployment.isEmpty()) return
        var attempts = 0
        while (units.size < SOLDIERS_PER_ROUND && attempts < MAX_SPAWN_ATTEMPTS) {
            attempts++
            val n = deployment?.let { it[rng.nextInt(it.size)] } ?: rng.nextInt(grid.nodeCount)
            val x = grid.nodeX[n] + 0.5; val z = grid.nodeZ[n] + 0.5
            // D'abord autour du camp adverse ; si ça ne suffit pas, n'importe où loin du joueur.
            val nearEnemySpawn = attempts < MAX_SPAWN_ATTEMPTS / 2
            if (nearEnemySpawn && distSq(x, z, ex, ez) > ENEMY_SPAWN_RADIUS * ENEMY_SPAWN_RADIUS) continue
            if (distSq(x, z, px, pz) < MIN_DIST_FROM_PLAYER * MIN_DIST_FROM_PLAYER) continue
            if (units.any { distSq(x, z, it.brain.x, it.brain.z) < MIN_SOLDIER_SPACING * MIN_SOLDIER_SPACING }) continue

            if (units.any { SoldierCollision.overlaps(x, grid.nodeY[n].toDouble(), z,
                    it.brain.x, it.brain.y, it.brain.z) }) continue
            val weaponType = arrayOf("gun", "smg", "lever_rifle")[units.size]
            val profile = RangedProfile.all.getValue(weaponType)
            val tuning = soldierTuning.copy(bulletSpeed = profile.speed, bulletRange = profile.range,
                fireInterval = profile.interval, magazineSize = profile.magazine, reloadSeconds = profile.reload)
            lateinit var brain: Soldier
            val clearance = BodyClearance { bx, by, bz ->
                SoldierCollision.clearsWorld(solid, bx, by, bz) &&
                    units.none { it.brain !== brain && it.body.hp > 0 &&
                        SoldierCollision.overlaps(bx, by, bz, it.brain.x, it.brain.y, it.brain.z) } &&
                    !(r.playerNode.hp > 0 && SoldierCollision.overlaps(bx, by, bz,
                        r.camera.playerX - source.originX, r.camera.playerY - source.originY - 1.62,
                        r.camera.playerZ - source.originZ,
                        (r.camera.eyeY - r.camera.playerY + 1.8).coerceAtLeast(.5)))
            }
            brain = Soldier(grid, solid, finder, rng, tuning, clearance)
            brain.place(x, grid.nodeY[n].toDouble(), z)
            val body = Enemy(nextSoldierId++, SOLDIER, x + source.originX, grid.nodeY[n].toDouble() + source.originY, z + source.originZ)
            body.hp = body.maxHp
            body.heldWeaponType = weaponType
            // Cadences identiques au joueur ; dégâts ajustés pour le solo, surtout la SMG.
            val damage = when (weaponType) { "smg" -> 3; "lever_rifle" -> 16; else -> 8 }
            units += Trooper(body, brain, damage)
            bodies += body
        }
    }

    // ── Joueur ────────────────────────────────────────────────────────────────

    private fun refreshPlayerPosition() {
        val camera = r.camera
        player.x = camera.playerX - source.originX
        player.eyeY = camera.eyeY - source.originY
        player.z = camera.playerZ - source.originZ
        player.alive = r.playerNode.hp > 0
    }

    private fun updatePlayerSnapshot(dt: Float) {
        refreshPlayerPosition()
        // Vitesse à l'horizontale, pour que les soldats visent moins bien un joueur qui court.
        if (!prevPlayerX.isNaN() && dt > 0f) {
            val vx = (player.x - prevPlayerX) / dt
            val vz = (player.z - prevPlayerZ) / dt
            // Une téléportation (réapparition) n'est pas une course.
            val teleported = vx * vx + vz * vz > MAX_PLAYER_SPEED * MAX_PLAYER_SPEED
            player.velX = if (teleported) 0.0 else vx
            player.velZ = if (teleported) 0.0 else vz
        }
        prevPlayerX = player.x
        prevPlayerZ = player.z
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

    companion object {
        /** Blocs de chute sous la carte avant d'être ramené. */
        const val FALL_LIMIT = 16

        /** Midi dans le cycle de CaveRenderer (6 h = 0 ms, 100 000 ms par heure de jour). */
        const val NOON_MS = 600_000L

        const val SOLDIERS_PER_ROUND = 3
        const val ROUND_SECONDS = 180f
        const val PLAYER_MAX_HP = 100
        const val HEADSHOT_MULTIPLIER = 2f
        const val STATUS_INTERVAL = 0.1f

        /** Dégâts d'une balle de soldat : une douzaine suffisent à abattre le joueur. */
        const val SOLDIER_DAMAGE = 8

        const val ENEMY_SPAWN_RADIUS = 15.0
        const val MIN_DIST_FROM_PLAYER = 40.0
        const val MIN_SOLDIER_SPACING = 3.0
        const val MAX_SPAWN_ATTEMPTS = 2000
        const val MAX_PLAYER_SPEED = 30.0

        /** Hauteur du tracé au-dessus du sol, pour qu'il ne clignote pas dans l'herbe. */
        const val PATH_LIFT = 0.06

        /** Même règle que la physique du joueur : l'air, la déco et l'eau ne bloquent pas. */
        fun blocksMovement(block: Short) = block != AIR && !isDecoration(block) && !isWater(block)

        /** Soldat : 100 PV (3 à 5 balles de pistolet au corps, 2 ou 3 à la tête), insensible aux effets élémentaires. */
        val SOLDIER = MobDef(
            id = "assault_soldier", hpBase = 100, damageBase = SOLDIER_DAMAGE, speed = 4.2f,
            attackRange = 45.0, detectRange = 45.0, eyeHeight = 1.62f, radius = 0.45f,
            spriteScale = 0.9f, hpScalePerLevel = 1.0, hpScaleCap = 1.0, damageScalePer3Lvl = 0,
            speedScalePerLevel = 0f, biomes = emptyList(), model = "soldier", spawnZoneMin = 0,
            spawnWeight = 0f, lootTable = "", behavior = "soldier", bossEligible = false, xpBase = 0,
            resistances = mapOf("bleed" to 0f, "poison" to 0f, "fire" to 0f, "ice" to 0f, "electric" to 0f),
        )
    }
}
