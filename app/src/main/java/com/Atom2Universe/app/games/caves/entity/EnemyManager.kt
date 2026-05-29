package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.world.*
import kotlin.math.*
import kotlin.random.Random

internal class EnemyManager(private val world: World) {

    val enemies = ArrayList<Enemy>(32)

    var playerHp = 20
    val playerMaxHp = 20
    var playerHpCallback: ((hp: Int, maxHp: Int) -> Unit)? = null

    /** Position du spawn d'origine (en blocs). Fixé une seule fois dans CaveRenderer. */
    var worldSpawnX: Double = 0.0
    var worldSpawnZ: Double = 0.0

    private var nextId = 0
    private var spawnTimer = 5f
    private var playerInvTimer = 0f

    fun update(dt: Float, px: Double, py: Double, pz: Double) {
        playerInvTimer = (playerInvTimer - dt).coerceAtLeast(0f)

        spawnTimer -= dt
        if (spawnTimer <= 0f && enemies.size < MAX_ENEMIES) {
            spawnTimer = SPAWN_INTERVAL
            trySpawn(px, py, pz)
        }

        enemies.removeAll { it.hp <= 0 }
        for (e in enemies) updateEnemy(e, dt, px, py, pz)
    }

    private fun updateEnemy(e: Enemy, dt: Float, px: Double, py: Double, pz: Double) {
        if (e.hitFlash > 0f) e.hitFlash -= dt

        val dx = px - e.x
        val dz = pz - e.z
        val dist = sqrt(dx * dx + dz * dz)

        e.state = when (e.state) {
            EnemyState.WANDER -> if (dist < e.type.detectRange) EnemyState.CHASE else EnemyState.WANDER
            EnemyState.CHASE  -> when {
                dist < e.type.attackRange      -> EnemyState.ATTACK
                dist > e.type.detectRange * 1.5 -> EnemyState.WANDER
                else                            -> EnemyState.CHASE
            }
            EnemyState.ATTACK -> if (dist > e.type.attackRange * 1.5) EnemyState.CHASE else EnemyState.ATTACK
        }

        val spd = e.scaledSpeed.toDouble() * dt
        when (e.state) {
            EnemyState.WANDER -> {
                e.wanderTimer -= dt
                if (e.wanderTimer <= 0f) {
                    val a = Random.nextFloat() * 2 * PI.toFloat()
                    e.wanderDirX = sin(a); e.wanderDirZ = cos(a)
                    e.wanderTimer = Random.nextFloat() * 3f + 2f
                }
                move(e, e.wanderDirX * spd * 0.4, e.wanderDirZ * spd * 0.4)
                if (e.wanderDirX != 0f || e.wanderDirZ != 0f)
                    e.yaw = atan2(-e.wanderDirX, -e.wanderDirZ) * (180f / PI.toFloat())
            }
            EnemyState.CHASE -> if (dist > 0.1) {
                val nx = dx / dist; val nz = dz / dist
                move(e, nx * spd, nz * spd)
                e.yaw = atan2(-nx.toFloat(), -nz.toFloat()) * (180f / PI.toFloat())
            }
            EnemyState.ATTACK -> {
                if (dist > 0.1)
                    e.yaw = atan2(-(px - e.x).toFloat(), -(pz - e.z).toFloat()) * (180f / PI.toFloat())
                e.attackCooldown -= dt
                if (e.attackCooldown <= 0f && playerInvTimer <= 0f) {
                    e.attackCooldown = ATTACK_CD
                    playerHp = (playerHp - e.scaledDamage).coerceAtLeast(0)
                    playerInvTimer = 0.5f
                    playerHpCallback?.invoke(playerHp, playerMaxHp)
                }
            }
        }

        if (e.type.flies) {
            val targetVY = ((py - 0.5 - e.y) * 3.0).coerceIn(-4.0, 4.0)
            e.velY += (targetVY - e.velY) * (dt * 5.0)
            val ny = e.y + e.velY * dt
            if (!collides(e.x, ny, e.z, e)) e.y = ny
        } else {
            e.velY = (e.velY - 22.0 * dt).coerceAtLeast(-30.0)
            val ny = e.y + e.velY * dt
            if (!collides(e.x, ny, e.z, e)) {
                e.y = ny
                e.onGround = false
            } else {
                if (e.velY < 0) {
                    e.onGround = true
                    if (!collides(e.x, e.y + 1.05, e.z, e)) e.y += 0.15
                }
                e.velY = 0.0
            }
        }
    }

    private fun move(e: Enemy, dx: Double, dz: Double) {
        if (!collides(e.x + dx, e.y, e.z, e)) e.x += dx
        if (!collides(e.x, e.y, e.z + dz, e)) e.z += dz
    }

    private fun collides(ex: Double, ey: Double, ez: Double, e: Enemy): Boolean {
        val r = e.type.radius.toDouble()
        val h = e.type.eyeHeight.toDouble()
        val x0 = Math.floor(ex - r).toInt();  val x1 = Math.floor(ex + r - 0.01).toInt()
        val y0 = Math.floor(ey - h - 0.1).toInt(); val y1 = Math.floor(ey).toInt()
        val z0 = Math.floor(ez - r).toInt();  val z1 = Math.floor(ez + r - 0.01).toInt()
        for (bz in z0..z1) for (by in y0..y1) for (bx in x0..x1) {
            val b = blockAt(bx, by, bz)
            if (b != AIR && !isDecoration(b)) return true
        }
        return false
    }

    private fun blockAt(bx: Int, by: Int, bz: Int): Byte {
        val cx = Math.floorDiv(bx, CHUNK_SIZE)
        val cy = Math.floorDiv(by, CHUNK_SIZE)
        val cz = Math.floorDiv(bz, CHUNK_SIZE)
        val ch = world.getChunk(cx, cy, cz) ?: return AIR
        if (!ch.generated) return AIR
        return ch.blockAt(bx - cx * CHUNK_SIZE, by - cy * CHUNK_SIZE, bz - cz * CHUNK_SIZE)
    }

    fun hitByLaser(
        ox: Double, oy: Double, oz: Double,
        ddx: Float, ddy: Float, ddz: Float,
        maxDist: Float
    ): Enemy? {
        var closest: Enemy? = null
        var closestT = maxDist.toDouble()
        for (e in enemies) {
            if (e.hp <= 0) continue
            val r = e.type.radius.toDouble()
            val t = rayAABB(
                ox, oy, oz, ddx, ddy, ddz,
                e.x - r, e.y - e.type.eyeHeight, e.z - r,
                e.x + r, e.y + 0.1, e.z + r
            ) ?: continue
            if (t < closestT) { closestT = t; closest = e }
        }
        return closest
    }

    fun damageEnemy(e: Enemy, dmg: Int) {
        e.hp = (e.hp - dmg).coerceAtLeast(0)
        e.hitFlash = 0.18f
        if (e.state == EnemyState.WANDER) e.state = EnemyState.CHASE
    }

    fun healPlayer(amount: Int) {
        playerHp = (playerHp + amount).coerceAtMost(playerMaxHp)
        playerHpCallback?.invoke(playerHp, playerMaxHp)
    }

    private fun trySpawn(px: Double, py: Double, pz: Double) {
        repeat(12) attempt@{
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val dist = 10.0 + Random.nextFloat() * 10.0
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            var sy = py
            for (dy in -5..5) {
                val ty = py + dy
                val bBelow = blockAt(Math.floor(sx).toInt(), Math.floor(ty - 1).toInt(), Math.floor(sz).toInt())
                val bAt    = blockAt(Math.floor(sx).toInt(), Math.floor(ty).toInt(),     Math.floor(sz).toInt())
                val bAbove = blockAt(Math.floor(sx).toInt(), Math.floor(ty + 1).toInt(), Math.floor(sz).toInt())
                if (bBelow != AIR && !isDecoration(bBelow) && bAt == AIR && bAbove == AIR) {
                    sy = ty; break
                }
            }
            val type = EnemyType.values()[Random.nextInt(EnemyType.values().size)]
            val e = Enemy(nextId++, type, sx, sy, sz)
            e.level = computeLevel(sx, sz)
            e.hp = e.maxHp
            if (!collides(sx, sy, sz, e)) {
                enemies.add(e)
                return
            }
        }
    }

    /** Niveau = floor(distance_en_chunks / 10) + 1, sans plafond. */
    fun computeLevel(blockX: Double, blockZ: Double): Int {
        val dX = (blockX - worldSpawnX) / CHUNK_SIZE
        val dZ = (blockZ - worldSpawnZ) / CHUNK_SIZE
        val distChunks = sqrt(dX * dX + dZ * dZ)
        return (distChunks / 10.0).toInt() + 1
    }

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
            tMin = maxOf(tMin, minOf(t1, t2))
            tMax = minOf(tMax, maxOf(t1, t2))
            return tMax >= tMin
        }
        if (!axis(ox, ddx, minX, maxX)) return null
        if (!axis(oy, ddy, minY, maxY)) return null
        if (!axis(oz, ddz, minZ, maxZ)) return null
        return if (tMax < 0.0) null else tMin
    }

    companion object {
        const val MAX_ENEMIES = 16
        const val SPAWN_INTERVAL = 10f
        const val ATTACK_CD = 1.5f
    }
}
