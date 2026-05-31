package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.world.*
import kotlin.math.*
import kotlin.random.Random

internal class EnemyManager(private val world: World, seed: Long = 0L) {

    val enemies = ArrayList<Enemy>(32)

    var playerHp = 20
    var playerMaxHp = 20
    var playerHpCallback: ((hp: Int, maxHp: Int) -> Unit)? = null

    var playerShieldMax     = 0
    var playerShieldCurrent = 0
    var shieldRechargeTimer = 0f
    var shieldCallback: ((current: Int, max: Int) -> Unit)? = null

    var worldSpawnX: Double = 0.0
    var worldSpawnZ: Double = 0.0

    private var nextId = 0
    private val rng = Random(seed xor 0x5A5A5A5A5A5A5A5AL)

    val familyPool: List<String> = ALL_FAMILIES.shuffled(Random(seed)).take(POOL_SIZE)

    val wardStoneZones: MutableList<Pair<Double, Double>> = mutableListOf()

    var bossRewardCallback: (() -> Unit)? = null
    private var bossEnemyId    = -1
    private var bossRewardGiven = false

    var isCreative = false

    private var spawnCooldown = SPAWN_INTERVAL
    private var playerInvTimer = 0f

    fun update(dt: Float, px: Double, py: Double, pz: Double) {
        if (isCreative) { enemies.clear(); return }
        playerInvTimer = (playerInvTimer - dt).coerceAtLeast(0f)

        // Recharge bouclier
        if (playerShieldMax > 0 && playerShieldCurrent < playerShieldMax) {
            shieldRechargeTimer -= dt
            if (shieldRechargeTimer <= 0f) {
                playerShieldCurrent = playerShieldMax
                shieldCallback?.invoke(playerShieldCurrent, playerShieldMax)
            }
        }

        for (e in enemies) if (e.hp > 0) e.animTime += dt

        // Despawn ennemis trop loin (> DESPAWN_CHUNKS) ou morts
        val despawnDist2 = (DESPAWN_CHUNKS * CHUNK_SIZE).toDouble().let { it * it }
        enemies.removeAll { e ->
            if (e.hp <= 0) {
                if (e.id == bossEnemyId && !bossRewardGiven) {
                    bossRewardGiven = true; bossEnemyId = -1
                    bossRewardCallback?.invoke()
                }
                return@removeAll true
            }
            val dx = e.x - px; val dz = e.z - pz
            dx * dx + dz * dz > despawnDist2
        }

        // ── Spawn ambiant organique ───────────────────────────────────────────
        val nearbyCount = enemies.count { e ->
            val dx = e.x - px; val dz = e.z - pz
            dx * dx + dz * dz <= (SPAWN_MAX_CHUNKS * CHUNK_SIZE).toDouble().let { it * it }
        }
        if (!isInSafeZone(px, pz) && nearbyCount < MAX_ENEMIES_NEARBY) {
            spawnCooldown -= dt
            if (spawnCooldown <= 0f) {
                spawnCooldown = SPAWN_INTERVAL + rng.nextFloat() * SPAWN_JITTER
                tryAmbientSpawn(px, py, pz)
            }
        }

        for (e in enemies) updateEnemy(e, dt, px, py, pz)
    }

    private fun tryAmbientSpawn(px: Double, py: Double, pz: Double) {
        val spawnBoss = bossEnemyId == -1 && rng.nextFloat() < BOSS_CHANCE
        val minDist = SPAWN_MIN_CHUNKS * CHUNK_SIZE.toDouble()
        val maxDist = SPAWN_MAX_CHUNKS * CHUNK_SIZE.toDouble()
        repeat(20) {
            val angle = rng.nextDouble() * 2 * PI
            val dist  = minDist + rng.nextDouble() * (maxDist - minDist)
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            if (!canSpawnAt(sx, sz)) return@repeat
            val sy = findSpawnGround(sx, sz, py) ?: return@repeat
            val familyIdx = rng.nextInt(POOL_SIZE)
            val e = Enemy(nextId++, EnemyType.values()[rng.nextInt(EnemyType.values().size)], sx, sy, sz)
            e.spriteFamily = familyPool[familyIdx]
            e.familyRow    = familyIdx
            e.level        = computeLevel(sx, sz).coerceAtLeast(1)
            e.isBoss       = spawnBoss
            e.hp           = e.maxHp
            e.state        = EnemyState.CHASE
            if (spawnBoss) { bossEnemyId = e.id; bossRewardGiven = false }
            enemies.add(e)
            return
        }
    }

    // Trouve le premier sol solide non-eau sous (sx, sz) avec 2 blocs d'air au-dessus.
    private fun findSpawnGround(sx: Double, sz: Double, nearY: Double): Double? {
        val bx = Math.floor(sx).toInt()
        val bz = Math.floor(sz).toInt()
        val startY = (nearY + 8.0).toInt()
        for (by in startY downTo startY - 40) {
            val b = world.blockAt(bx, by, bz)
            if (b == AIR || isWater(b) || isDecoration(b)) continue
            // Sol solide trouvé : vérifier 2 blocs d'air libres au-dessus
            val a1 = world.blockAt(bx, by + 1, bz)
            val a2 = world.blockAt(bx, by + 2, bz)
            if (a1 == AIR && a2 == AIR) return (by + 1).toDouble()
        }
        return null
    }

    private fun isInSafeZone(px: Double, pz: Double): Boolean {
        if (distFromSpawnChunks(px, pz) < SAFE_ZONE_CHUNKS) return true
        return wardStoneZones.any { (wx, wz) ->
            val dX = (px - wx) / CHUNK_SIZE; val dZ = (pz - wz) / CHUNK_SIZE
            sqrt(dX * dX + dZ * dZ) < WARD_SAFE_RADIUS
        }
    }

    private fun canSpawnAt(sx: Double, sz: Double): Boolean {
        if (distFromSpawnChunks(sx, sz) < SAFE_ZONE_CHUNKS) return false
        if (wardStoneZones.any { (wx, wz) ->
            val dX = (sx - wx) / CHUNK_SIZE; val dZ = (sz - wz) / CHUNK_SIZE
            sqrt(dX * dX + dZ * dZ) < WARD_SAFE_RADIUS
        }) return false
        return true
    }

    private fun distFromSpawnChunks(x: Double, z: Double): Double {
        val dX = (x - worldSpawnX) / CHUNK_SIZE; val dZ = (z - worldSpawnZ) / CHUNK_SIZE
        return sqrt(dX * dX + dZ * dZ)
    }

    private fun updateEnemy(e: Enemy, dt: Float, px: Double, py: Double, pz: Double) {
        if (e.hitFlash > 0f) e.hitFlash -= dt

        val dx = px - e.x; val dz = pz - e.z
        val dist = sqrt(dx * dx + dz * dz)

        e.state = when (e.state) {
            EnemyState.WANDER -> if (dist < e.type.detectRange) EnemyState.CHASE else EnemyState.WANDER
            EnemyState.CHASE  -> when {
                dist < e.type.attackRange       -> EnemyState.ATTACK
                dist > e.type.detectRange * 2.0 -> EnemyState.WANDER
                else                             -> EnemyState.CHASE
            }
            EnemyState.ATTACK -> if (dist > e.type.attackRange * 1.5) EnemyState.CHASE else EnemyState.ATTACK
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

                // Si bloqué depuis trop longtemps, appliquer une perturbation perpendiculaire
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
                    val dmg      = e.scaledDamage
                    val shAbsorb = minOf(playerShieldCurrent, dmg)
                    playerShieldCurrent -= shAbsorb
                    val hpDmg = dmg - shAbsorb
                    if (hpDmg > 0) playerHp = (playerHp - hpDmg).coerceAtLeast(0)
                    playerInvTimer = 0.5f
                    if (shAbsorb > 0 || dmg > 0) {
                        shieldRechargeTimer = SHIELD_RECHARGE_DELAY
                        shieldCallback?.invoke(playerShieldCurrent, playerShieldMax)
                    }
                    playerHpCallback?.invoke(playerHp, playerMaxHp)
                }
            }
        }

        // ── Gravité (sol = bloc solide non-eau) ──────────────────────────────
        if (e.type.flies) {
            val groundY = solidGroundBelow(e.x, e.z, e.y + 2.0) ?: (e.y - 8.0)
            val hoverTarget = groundY + BAT_HOVER_HEIGHT
            val targetVY = ((hoverTarget - e.y) * 4.0).coerceIn(-8.0, 8.0)
            e.velY += (targetVY - e.velY) * (dt * 4.0)
            e.y += e.velY * dt
        } else {
            e.velY = (e.velY - GRAVITY * dt).coerceAtLeast(MAX_FALL.toDouble())
            val newY = e.y + e.velY * dt
            val ground = solidGroundBelow(e.x, e.z, newY + 1.0)
            if (ground != null && newY < ground) {
                e.y = ground; e.velY = 0.0; e.onGround = true
            } else {
                e.y = newY; e.onGround = ground != null && newY <= ground + 0.1
            }
        }
    }

    // Déplacement horizontal avec collision + step-up d'un bloc.
    private fun move(e: Enemy, dx: Double, dz: Double) {
        val r = e.type.radius.toDouble()
        // +0.002 évite qu'une valeur comme 64.9999 (au lieu de 65.0) traite le bloc sol comme footY
        val footY = Math.floor(e.y + 0.002).toInt()
        val headY = footY + 1

        // Axe X
        if (dx != 0.0) {
            val tx = Math.floor(e.x + dx + if (dx > 0) r else -r).toInt()
            val zMin = Math.floor(e.z - r + 0.05).toInt()
            val zMax = Math.floor(e.z + r - 0.05).toInt()
            val freeX = (zMin..zMax).all { bz -> isFreeForMob(tx, footY, bz) && isFreeForMob(tx, headY, bz) }
            val stepX = !freeX && e.onGround &&
                (zMin..zMax).all { bz -> isFreeForMob(tx, footY + 1, bz) && isFreeForMob(tx, headY + 1, bz) }
            when {
                freeX  -> e.x += dx
                stepX  -> { e.velY = STEP_UP_VEL; e.x += dx }
            }
        }

        // Axe Z
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

    // Un bloc est libre pour un mob s'il est air, eau ou décoration.
    private fun isFreeForMob(bx: Int, by: Int, bz: Int): Boolean {
        val b = world.blockAt(bx, by, bz)
        return b == AIR || isWater(b) || isDecoration(b)
    }

    // Sol solide = premier bloc ni air, ni eau, ni décoration.
    private fun solidGroundBelow(wx: Double, wz: Double, fromY: Double): Double? {
        val bx = Math.floor(wx).toInt(); val bz = Math.floor(wz).toInt()
        val startY = Math.floor(fromY).toInt()
        for (by in startY downTo startY - 24) {
            val b = world.blockAt(bx, by, bz)
            if (b != AIR && !isWater(b) && !isDecoration(b)) return (by + 1).toDouble()
        }
        return null
    }

    fun hitByLaser(
        ox: Double, oy: Double, oz: Double,
        ddx: Float, ddy: Float, ddz: Float,
        maxDist: Float
    ): Enemy? {
        var closest: Enemy? = null; var closestT = maxDist.toDouble()
        for (e in enemies) {
            if (e.hp <= 0) continue
            val r = e.type.radius.toDouble()
            val t = rayAABB(ox, oy, oz, ddx, ddy, ddz,
                e.x - r, e.y - e.type.eyeHeight, e.z - r,
                e.x + r, e.y + 0.1, e.z + r) ?: continue
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

    fun computeLevel(blockX: Double, blockZ: Double): Int {
        val distChunks = distFromSpawnChunks(blockX, blockZ)
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
            tMin = maxOf(tMin, minOf(t1, t2)); tMax = minOf(tMax, maxOf(t1, t2))
            return tMax >= tMin
        }
        if (!axis(ox, ddx, minX, maxX)) return null
        if (!axis(oy, ddy, minY, maxY)) return null
        if (!axis(oz, ddz, minZ, maxZ)) return null
        return if (tMax < 0.0) null else tMin
    }

    companion object {
        const val POOL_SIZE             = 32
        const val ATTACK_CD             = 1.5f
        const val SAFE_ZONE_CHUNKS      = 5.0
        const val WARD_SAFE_RADIUS      = 5.0
        const val SHIELD_RECHARGE_DELAY = 10f
        const val GRAVITY               = 20f
        const val MAX_FALL              = -20f
        const val STEP_UP_VEL           = 7.0   // vitesse verticale initiale pour monter une marche
        const val BAT_HOVER_HEIGHT      = 0.4f  // hauteur au-dessus du sol (chauves-souris)

        const val SPAWN_INTERVAL       = 5f    // secondes entre spawns
        const val SPAWN_JITTER         = 3f    // variation aléatoire de l'intervalle
        const val MAX_ENEMIES_NEARBY   = 10    // plafond dans le rayon de spawn
        const val SPAWN_MIN_CHUNKS     = 2     // distance min (chunks)
        const val SPAWN_MAX_CHUNKS     = 4     // distance max (chunks)
        const val DESPAWN_CHUNKS       = 6     // distance de despawn (chunks)
        const val BOSS_CHANCE          = 0.01f

        val ALL_FAMILIES = listOf(
            "amg1", "amg2", "amg3", "amg4",
            "avt1", "avt2", "avt3", "avt4",
            "bmg1", "bmg2", "bmg3", "bmg4",
            "chr1", "dvl1",
            "ftr1", "ftr2", "ftr3", "ftr4",
            "gsd1", "isd1", "jli1", "kin1",
            "knt1", "knt2", "knt3", "knt4",
            "man1", "man2", "man3", "man4",
            "mnt1", "mnt2", "mnt3", "mnt4",
            "mnv1", "mnv2", "mnv3", "mnv4",
            "mst1", "mst2", "mst3", "mst4",
            "nja1", "nja2", "nja3", "nja4",
            "npc1", "npc2", "npc3", "npc4", "npc5", "npc6", "npc7", "npc8", "npc9",
            "pdn1", "pdn2", "pdn3", "pdn4",
            "scr1", "scr2", "scr3", "scr4",
            "skl1",
            "smr1", "smr2", "smr3", "smr4",
            "spd1", "syb1",
            "thf1", "thf2", "thf3", "thf4",
            "trk1",
            "wmg1", "wmg2", "wmg3", "wmg4",
            "wmn1", "wmn2", "wmn3",
            "wnv1", "wnv2", "wnv3", "wnv4",
            "ybo1", "ygr1", "zph1"
        )
    }
}
