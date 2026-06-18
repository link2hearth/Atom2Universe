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
    private var lastPx = 0.0
    private var lastPz = 0.0

    // ── Tick principal ────────────────────────────────────────────────────────

    fun update(dt: Float, px: Double, py: Double, pz: Double) {
        if (isCreative) { enemies.clear(); return }
        lastPx = px; lastPz = pz
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

        // Recul infligé par le joueur — déplacement amorti, même si étourdi.
        applyMobKnockback(e, dt)

        // Étourdissement
        if (e.shockTimer > 0f) { e.shockTimer -= dt; return }


        val dx = px - e.x; val dy = py - e.y; val dz = pz - e.z
        val dist3d = sqrt(dx * dx + dy * dy + dz * dz)
        val dist    = sqrt(dx * dx + dz * dz)   // XZ pour l'orientation et le déplacement

        // Distance de garde souhaitée au centre du joueur : tient compte du rayon
        // du mob pour que son corps n'entre pas dans la caméra en vue FPS.
        val keep = keepDist(e)

        val prevState = e.state
        e.state = when (e.state) {
            EnemyState.WANDER -> if (dist3d < e.def.detectRange) EnemyState.CHASE else EnemyState.WANDER
            EnemyState.CHASE  -> when {
                dist3d <= keep + ATTACK_REACH    -> EnemyState.ATTACK
                dist3d > e.def.detectRange * 2.0 -> { e.alertPlayed = false; EnemyState.WANDER }
                else                              -> EnemyState.CHASE
            }
            EnemyState.ATTACK -> if (dist3d > keep + 1.5) EnemyState.CHASE else EnemyState.ATTACK
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
            EnemyState.CHASE -> if (dist > keep) {
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
            } else {
                // À distance de garde : reste sur place, fait juste face au joueur.
                e.stuckTimer = 0f
                if (dist > 0.1) e.yaw = atan2((dx / dist).toFloat(), (dz / dist).toFloat()) * (180f / PI.toFloat())
            }
            EnemyState.ATTACK -> {
                e.stuckTimer = 0f
                if (dist > 0.1)
                    e.yaw = atan2((px - e.x).toFloat(), (pz - e.z).toFloat()) * (180f / PI.toFloat())
                // Maintien de la distance : si le joueur s'avance dans le mob, il recule.
                if (dist in 0.1..(keep - 0.4)) {
                    move(e, (-dx / dist) * spd * 0.8, (-dz / dist) * spd * 0.8)
                }
            }
        }

        // Attaque : découplée de l'état de déplacement. Dès que le mob est à portée,
        // le cooldown tourne et il frappe — la séparation entre mobs ne l'empêche plus.
        e.attackCooldown -= dt
        if (dist3d <= keep + ATTACK_REACH && e.attackCooldown <= 0f && playerInvTimer <= 0f) {
            e.attackCooldown = ATTACK_CD
            playerInvTimer = 0.5f
            val bus = eventBus
            val p   = player
            if (p != null && bus != null) {
                // Direction du recul : de l'ennemi vers le joueur, à l'horizontale.
                val kdx = (px - e.x); val kdz = (pz - e.z)
                val klen = sqrt(kdx * kdx + kdz * kdz).coerceAtLeast(0.001)
                CombatNode.enemyAttacksPlayer(e, p, bus, (kdx / klen).toFloat(), (kdz / klen).toFloat())
                val thornsDmg = thornsProvider?.invoke() ?: 0
                if (thornsDmg > 0) {
                    e.hp = (e.hp - thornsDmg).coerceAtLeast(0)
                    e.hitFlash = 0.15f
                }
            }
        }

        // Séparation : les mobs s'évitent entre eux pour ne pas s'empiler.
        applySeparation(e, dt)

        e.velY = (e.velY - GRAVITY * dt).coerceAtLeast(MAX_FALL.toDouble())
        val newY = e.y + e.velY * dt
        val ground = solidGroundBelow(e.x, e.z, newY + 1.0)
        if (ground != null && newY < ground) {
            e.y = ground; e.velY = 0.0; e.onGround = true
        } else {
            e.y = newY; e.onGround = ground != null && newY <= ground + 0.1
        }
    }

    /** Distance XZ à laquelle le mob se tient du centre du joueur (corps hors caméra). */
    private fun keepDist(e: Enemy): Double =
        (e.def.radius.toDouble() + PLAYER_STANDOFF).coerceAtLeast(e.def.attackRange)

    /** Donne une impulsion de recul à [e], à l'opposé du joueur (réduite pour les boss). */
    fun knockbackFromPlayer(e: Enemy, strength: Double = MOB_KNOCKBACK) {
        val dx = e.x - lastPx; val dz = e.z - lastPz
        val len = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
        val s = if (e.isBoss) strength * 0.4 else strength
        e.knockX = dx / len * s
        e.knockZ = dz / len * s
    }

    /** Applique le recul amorti de [e] (avec collision via [move]). */
    private fun applyMobKnockback(e: Enemy, dt: Float) {
        if (e.knockX == 0.0 && e.knockZ == 0.0) return
        move(e, e.knockX * dt, e.knockZ * dt)
        val damp = (KNOCK_DAMP * dt).coerceAtMost(1.0)
        e.knockX -= e.knockX * damp
        e.knockZ -= e.knockZ * damp
        if (abs(e.knockX) < 0.1 && abs(e.knockZ) < 0.1) { e.knockX = 0.0; e.knockZ = 0.0 }
    }

    /** Repousse [e] des autres mobs trop proches pour éviter l'empilement. */
    private fun applySeparation(e: Enemy, dt: Float) {
        var sx = 0.0; var sz = 0.0
        for (o in enemies) {
            if (o === e || o.hp <= 0) continue
            val dx = e.x - o.x; val dz = e.z - o.z
            val d2 = dx * dx + dz * dz
            val want = e.def.radius.toDouble() + o.def.radius.toDouble() + SEP_GAP
            if (d2 in 1e-6..(want * want)) {
                val d = sqrt(d2)
                val push = (want - d) / want
                sx += dx / d * push; sz += dz / d * push
            }
        }
        if (sx != 0.0 || sz != 0.0) {
            val spd = e.scaledSpeed.toDouble() * dt * SEP_STRENGTH
            move(e, sx * spd, sz * spd)
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

    fun damageEnemy(e: Enemy, dmg: Int) {
        CombatNode.damageEnemy(e, dmg)
        knockbackFromPlayer(e)
        if (e.state == EnemyState.WANDER) e.state = EnemyState.CHASE
        eventBus?.publish(com.Atom2Universe.app.games.caves.node.GameEvent.MobHit(e.isBoss))
    }

    fun healPlayer(amount: Int) {
        player?.applyHeal(amount)
    }

    fun computeLevel(blockX: Double, blockY: Double, blockZ: Double): Int =
        spawnManager.computeLevel(blockX, blockY, blockZ)

    companion object {
        const val ATTACK_CD      = 1.5f
        const val GRAVITY        = 20f
        const val MAX_FALL       = -20f
        const val STEP_UP_VEL    = 7.0
        const val DESPAWN_CHUNKS = 6
        const val ATTACK_REACH    = 0.6    // portée d'attaque au-delà de la distance de garde
        const val PLAYER_STANDOFF = 1.5    // marge XZ au-delà du rayon du mob (corps hors caméra)
        const val SEP_GAP         = 0.7    // espace désiré entre les surfaces de deux mobs
        const val SEP_STRENGTH    = 0.9    // intensité de la force de séparation
        const val MOB_KNOCKBACK   = 5.0    // vitesse initiale du recul d'un mob touché
        const val KNOCK_DAMP      = 8.0    // amortissement du recul (par seconde)
    }

    // RNG local pour l'IA de wandering (indépendant du spawn)
    private val rng = Random(seed xor 0x1A2B3C4D5E6F7A8BL)
}
