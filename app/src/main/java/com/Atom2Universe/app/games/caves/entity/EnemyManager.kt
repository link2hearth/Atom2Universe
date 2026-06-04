package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.node.CombatNode
import com.Atom2Universe.app.games.caves.node.EventBus
import com.Atom2Universe.app.games.caves.node.PlayerNode
import com.Atom2Universe.app.games.caves.world.*
import kotlin.math.*
import kotlin.random.Random

internal class EnemyManager(private val world: World, seed: Long = 0L) {

    val enemies = ArrayList<Enemy>(32)

    val wardStoneZones: MutableList<Pair<Double, Double>> = mutableListOf()

    val spawnManager = SpawnManager(
        world          = world,
        worldSeed      = seed,
        enemies        = enemies,
        wardStoneZones = wardStoneZones,
        seed           = seed
    )

    var player: PlayerNode? = null

    var eventBus: EventBus? = null
        set(v) { field = v; spawnManager.eventBus = v }

    var worldSpawnX: Double
        get() = spawnManager.worldSpawnX
        set(v) { spawnManager.worldSpawnX = v }
    var worldSpawnY: Double
        get() = spawnManager.worldSpawnY
        set(v) { spawnManager.worldSpawnY = v }
    var worldSpawnZ: Double
        get() = spawnManager.worldSpawnZ
        set(v) { spawnManager.worldSpawnZ = v }

    // Rétro-compatibilité : les callbacks UI routent vers PlayerNode
    var playerHpCallback: ((hp: Int, maxHp: Int) -> Unit)?
        get() = player?.onHpChanged
        set(v) { player?.onHpChanged = v }
    var shieldCallback: ((current: Int, max: Int) -> Unit)?
        get() = player?.onShieldChanged
        set(v) { player?.onShieldChanged = v }

    var isCreative = false

    // Fournit les dégâts thorns de l'arme équipée (0 si pas d'épines)
    var thornsProvider: (() -> Int)? = null

    private var playerInvTimer = 0f

    // ── Tick principal ────────────────────────────────────────────────────────

    fun update(dt: Float, px: Double, py: Double, pz: Double) {
        if (isCreative) { enemies.clear(); return }
        playerInvTimer = (playerInvTimer - dt).coerceAtLeast(0f)

        player?.tickShield(dt)

        for (e in enemies) if (e.hp > 0) e.animTime += dt

        val despawnDist2 = (DESPAWN_CHUNKS * CHUNK_SIZE).toDouble().let { it * it }
        enemies.removeAll { e ->
            if (e.hp <= 0) {
                spawnManager.onEnemyDied(e)
                return@removeAll true
            }
            val dx = e.x - px; val dz = e.z - pz
            dx * dx + dz * dz > despawnDist2
        }

        spawnManager.update(dt, px, py, pz)

        for (e in enemies) updateEnemy(e, dt, px, py, pz)
    }

    // ── IA ennemis ────────────────────────────────────────────────────────────

    private fun updateEnemy(e: Enemy, dt: Float, px: Double, py: Double, pz: Double) {
        if (e.hitFlash > 0f) e.hitFlash -= dt

        // Saignement (tick toutes les 0.5s, 3s)
        if (e.bleedTimer > 0f) {
            e.bleedTimer -= dt; e.bleedTickTimer -= dt
            if (e.bleedTickTimer <= 0f) {
                e.bleedTickTimer = 0.5f
                e.hp = (e.hp - e.bleedDamage).coerceAtLeast(0); e.hitFlash = 0.12f
            }
            if (e.bleedTimer <= 0f) { e.bleedTimer = 0f; e.bleedDamage = 0; e.bleedTickTimer = 0f }
        }

        // Poison (tick toutes les 0.8s, 4s — dégâts moindres mais plus durables)
        if (e.poisonTimer > 0f) {
            e.poisonTimer -= dt; e.poisonTickTimer -= dt
            if (e.poisonTickTimer <= 0f) {
                e.poisonTickTimer = 0.8f
                e.hp = (e.hp - e.poisonDamage).coerceAtLeast(0); e.hitFlash = 0.10f
            }
            if (e.poisonTimer <= 0f) { e.poisonTimer = 0f; e.poisonDamage = 0; e.poisonTickTimer = 0f }
        }

        // Feu (tick toutes les 0.3s, 2s — dégâts rapides et élevés)
        if (e.fireTimer > 0f) {
            e.fireTimer -= dt; e.fireTickTimer -= dt
            if (e.fireTickTimer <= 0f) {
                e.fireTickTimer = 0.3f
                e.hp = (e.hp - e.fireDamage).coerceAtLeast(0); e.hitFlash = 0.18f
            }
            if (e.fireTimer <= 0f) { e.fireTimer = 0f; e.fireDamage = 0; e.fireTickTimer = 0f }
        }

        // Étourdissement
        if (e.shockTimer > 0f) { e.shockTimer -= dt; return }


        val dx = px - e.x; val dy = py - e.y; val dz = pz - e.z
        val dist3d = sqrt(dx * dx + dy * dy + dz * dz)
        val dist    = sqrt(dx * dx + dz * dz)   // XZ pour l'orientation et le déplacement

        val prevState = e.state
        e.state = when (e.state) {
            EnemyState.WANDER -> if (dist3d < e.def.detectRange) EnemyState.CHASE else EnemyState.WANDER
            EnemyState.CHASE  -> when {
                dist3d < e.def.attackRange       -> EnemyState.ATTACK
                dist3d > e.def.detectRange * 2.0 -> { e.alertPlayed = false; EnemyState.WANDER }
                else                              -> EnemyState.CHASE
            }
            EnemyState.ATTACK -> if (dist3d > e.def.attackRange * 1.5) EnemyState.CHASE else EnemyState.ATTACK
        }
        if (prevState == EnemyState.WANDER && e.state == EnemyState.CHASE && !e.alertPlayed) {
            e.alertPlayed = true
            eventBus?.publish(com.Atom2Universe.app.games.caves.node.GameEvent.MobNearby(e.isBoss))
        }

        val spd = e.scaledSpeed.toDouble() * dt
        when (e.state) {
            EnemyState.WANDER -> {
                e.stuckTimer = 0f
                e.wanderTimer -= dt
                if (e.wanderTimer <= 0f) {
                    val a = rng.nextFloat() * 2 * PI.toFloat()
                    e.wanderDirX = sin(a); e.wanderDirZ = cos(a)
                    e.wanderTimer = rng.nextFloat() * 3f + 2f
                }
                move(e, e.wanderDirX * spd * 0.4, e.wanderDirZ * spd * 0.4)
                if (e.wanderDirX != 0f || e.wanderDirZ != 0f)
                    e.yaw = atan2(e.wanderDirX, e.wanderDirZ) * (180f / PI.toFloat())
            }
            EnemyState.CHASE -> if (dist > 0.1) {
                var nx = (dx / dist).toFloat()
                var nz = (dz / dist).toFloat()
                if (e.stuckTimer > 1.2f) {
                    val sign = if ((e.stuckTimer * 3).toInt() % 2 == 0) 1f else -1f
                    val px2 = -nz * sign * 0.7f; val pz2 = nx * sign * 0.7f
                    nx = (nx + px2); nz = (nz + pz2)
                    val len = sqrt(nx * nx + nz * nz).coerceAtLeast(0.001f)
                    nx /= len; nz /= len
                }
                val prevX = e.x; val prevZ = e.z
                move(e, nx * spd, nz * spd)
                if (e.x == prevX && e.z == prevZ) e.stuckTimer += dt else e.stuckTimer = 0f
                e.yaw = atan2(nx, nz) * (180f / PI.toFloat())
            }
            EnemyState.ATTACK -> {
                e.stuckTimer = 0f
                if (dist > 0.1)
                    e.yaw = atan2((px - e.x).toFloat(), (pz - e.z).toFloat()) * (180f / PI.toFloat())
                e.attackCooldown -= dt
                if (e.attackCooldown <= 0f && playerInvTimer <= 0f) {
                    e.attackCooldown = ATTACK_CD
                    playerInvTimer = 0.5f
                    val bus = eventBus
                    val p   = player
                    if (p != null && bus != null) {
                        CombatNode.enemyAttacksPlayer(e, p, bus)
                        val thornsDmg = thornsProvider?.invoke() ?: 0
                        if (thornsDmg > 0) {
                            e.hp = (e.hp - thornsDmg).coerceAtLeast(0)
                            e.hitFlash = 0.15f
                        }
                    }
                }
            }
        }

        e.velY = (e.velY - GRAVITY * dt).coerceAtLeast(MAX_FALL.toDouble())
        val newY = e.y + e.velY * dt
        val ground = solidGroundBelow(e.x, e.z, newY + 1.0)
        if (ground != null && newY < ground) {
            e.y = ground; e.velY = 0.0; e.onGround = true
        } else {
            e.y = newY; e.onGround = ground != null && newY <= ground + 0.1
        }
    }

    // ── Déplacement + collision ───────────────────────────────────────────────

    private fun move(e: Enemy, dx: Double, dz: Double) {
        val r = e.def.radius.toDouble()
        val footY = Math.floor(e.y + 0.002).toInt()
        val headY = footY + 1

        if (dx != 0.0) {
            val tx = Math.floor(e.x + dx + if (dx > 0) r else -r).toInt()
            val zMin = Math.floor(e.z - r + 0.05).toInt()
            val zMax = Math.floor(e.z + r - 0.05).toInt()
            val freeX = (zMin..zMax).all { bz -> isFreeForMob(tx, footY, bz) && isFreeForMob(tx, headY, bz) }
            val stepX = !freeX && e.onGround &&
                (zMin..zMax).all { bz -> isFreeForMob(tx, footY + 1, bz) && isFreeForMob(tx, headY + 1, bz) }
            when {
                freeX -> e.x += dx
                stepX -> { e.velY = STEP_UP_VEL; e.x += dx }
            }
        }

        if (dz != 0.0) {
            val tz = Math.floor(e.z + dz + if (dz > 0) r else -r).toInt()
            val xMin = Math.floor(e.x - r + 0.05).toInt()
            val xMax = Math.floor(e.x + r - 0.05).toInt()
            val fy2 = Math.floor(e.y + 0.002).toInt(); val hy2 = fy2 + 1
            val freeZ = (xMin..xMax).all { bx -> isFreeForMob(bx, fy2, tz) && isFreeForMob(bx, hy2, tz) }
            val stepZ = !freeZ && e.onGround &&
                (xMin..xMax).all { bx -> isFreeForMob(bx, fy2 + 1, tz) && isFreeForMob(bx, hy2 + 1, tz) }
            when {
                freeZ -> e.z += dz
                stepZ -> { e.velY = STEP_UP_VEL; e.z += dz }
            }
        }
    }

    private fun isFreeForMob(bx: Int, by: Int, bz: Int): Boolean {
        val b = world.blockAt(bx, by, bz)
        return b == AIR || isWater(b) || isDecoration(b)
    }

    private fun solidGroundBelow(wx: Double, wz: Double, fromY: Double): Double? {
        val bx = Math.floor(wx).toInt(); val bz = Math.floor(wz).toInt()
        val startY = Math.floor(fromY).toInt()
        for (by in startY downTo startY - 24) {
            val b = world.blockAt(bx, by, bz)
            if (b != AIR && !isWater(b) && !isDecoration(b)) return (by + 1).toDouble()
        }
        return null
    }

    // ── API publique ──────────────────────────────────────────────────────────

    fun hitByLaser(
        ox: Double, oy: Double, oz: Double,
        ddx: Float, ddy: Float, ddz: Float,
        maxDist: Float
    ): Enemy? {
        var closest: Enemy? = null; var closestT = maxDist.toDouble()
        for (e in enemies) {
            if (e.hp <= 0) continue
            val t = rayAABB(ox, oy, oz, ddx, ddy, ddz,
                e.x - 0.5, e.y - 0.5, e.z - 0.5,
                e.x + 0.5, e.y + 0.5, e.z + 0.5) ?: continue
            if (t < closestT) { closestT = t; closest = e }
        }
        return closest
    }

    fun damageEnemy(e: Enemy, dmg: Int) {
        CombatNode.damageEnemy(e, dmg)
        if (e.state == EnemyState.WANDER) e.state = EnemyState.CHASE
        eventBus?.publish(com.Atom2Universe.app.games.caves.node.GameEvent.MobHit(e.isBoss))
    }

    fun healPlayer(amount: Int) {
        player?.applyHeal(amount)
    }

    fun computeLevel(blockX: Double, blockY: Double, blockZ: Double): Int =
        spawnManager.computeLevel(blockX, blockY, blockZ)

    private fun rayAABB(
        ox: Double, oy: Double, oz: Double,
        ddx: Float, ddy: Float, ddz: Float,
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double
    ): Double? {
        var tMin = 0.0; var tMax = Double.MAX_VALUE
        fun axis(o: Double, d: Float, lo: Double, hi: Double): Boolean {
            if (d == 0f) return o in lo..hi
            val t1 = (lo - o) / d; val t2 = (hi - o) / d
            tMin = maxOf(tMin, minOf(t1, t2)); tMax = minOf(tMax, maxOf(t1, t2))
            return tMax >= tMin
        }
        if (!axis(ox, ddx, minX, maxX)) return null
        if (!axis(oy, ddy, minY, maxY)) return null
        if (!axis(oz, ddz, minZ, maxZ)) return null
        return if (tMax < 0.0) null else tMin
    }

    companion object {
        const val ATTACK_CD      = 1.5f
        const val GRAVITY        = 20f
        const val MAX_FALL       = -20f
        const val STEP_UP_VEL    = 7.0
        const val DESPAWN_CHUNKS = 6
    }

    // RNG local pour l'IA de wandering (indépendant du spawn)
    private val rng = Random(seed xor 0x1A2B3C4D5E6F7A8BL)
}
