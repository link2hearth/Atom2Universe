package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.world.World
import kotlin.math.*
import kotlin.random.Random

internal class PassiveMobManager(private val world: World, seed: Long = 0L) {

    val mobs = ArrayList<PassiveMob>(16)

    var worldSpawnX: Double = 0.0
    var worldSpawnZ: Double = 0.0

    private var nextId = 0
    private val rng = Random(seed xor 0x13F7A2B4C8D9E501L)

    fun update(dt: Float, px: Double, py: Double, pz: Double) {
        for (mob in mobs) {
            if (mob.hp <= 0) continue
            mob.animTime += dt
            updateMob(mob, dt, px, py, pz)
        }
        mobs.removeAll { it.hp <= 0 }

        if (mobs.size < MAX_COWS) trySpawnCow(px, py, pz)
    }

    private fun updateMob(mob: PassiveMob, dt: Float, px: Double, py: Double, pz: Double) {
        val dx = px - mob.x
        val dz = pz - mob.z
        val distToPlayer = sqrt(dx * dx + dz * dz)

        mob.state = when {
            distToPlayer < FLEE_RANGE -> PassiveMobState.FLEE
            mob.state == PassiveMobState.FLEE && distToPlayer > FLEE_RANGE * 2.0 -> PassiveMobState.IDLE
            else -> mob.state
        }

        val spd = mob.type.speed.toDouble() * dt

        when (mob.state) {
            PassiveMobState.IDLE -> {
                mob.idleTimer -= dt
                if (mob.idleTimer <= 0f) {
                    val a = rng.nextFloat() * 2 * PI.toFloat()
                    mob.wanderDirX = sin(a)
                    mob.wanderDirZ = cos(a)
                    mob.yaw = atan2(mob.wanderDirX, mob.wanderDirZ) * (180f / PI.toFloat())
                    mob.wanderTimer = 2f + rng.nextFloat() * 4f
                    mob.state = PassiveMobState.WANDER
                }
            }
            PassiveMobState.WANDER -> {
                mob.x += mob.wanderDirX * spd * 0.5
                mob.z += mob.wanderDirZ * spd * 0.5
                mob.wanderTimer -= dt
                if (mob.wanderTimer <= 0f) {
                    mob.idleTimer = 2f + rng.nextFloat() * 3f
                    mob.state = PassiveMobState.IDLE
                }
            }
            PassiveMobState.FLEE -> {
                if (distToPlayer > 0.1) {
                    val nx = -dx / distToPlayer
                    val nz = -dz / distToPlayer
                    mob.x += nx * spd * 1.8
                    mob.z += nz * spd * 1.8
                    mob.yaw = atan2(nx.toFloat(), nz.toFloat()) * (180f / PI.toFloat())
                }
            }
        }

        // Gravité + snap au sol
        mob.velY = (mob.velY - GRAVITY * dt).coerceAtLeast(MAX_FALL.toDouble())
        val newY = mob.y + mob.velY * dt
        val ground = world.groundBelow(mob.x, mob.z, newY + 1.0)
        if (ground != null && newY < ground) {
            mob.y = ground
            mob.velY = 0.0
        } else {
            mob.y = newY
        }
    }

    private fun trySpawnCow(px: Double, py: Double, pz: Double) {
        repeat(12) {
            val angle = rng.nextFloat() * 2 * PI.toFloat()
            val dist = 16.0 + rng.nextFloat() * 12.0
            val sx = px + cos(angle) * dist
            val sz = pz + sin(angle) * dist
            val dSpawn = sqrt((sx - worldSpawnX).pow(2) + (sz - worldSpawnZ).pow(2))
            if (dSpawn < 20.0) return@repeat
            val mob = PassiveMob(nextId++, PassiveMobType.COW, sx, py + rng.nextFloat() * 4f - 2f, sz)
            mob.yaw = rng.nextFloat() * 360f
            mobs.add(mob)
            return
        }
    }

    companion object {
        const val MAX_COWS  = 5
        const val FLEE_RANGE = 4.0
        const val GRAVITY   = 20f
        const val MAX_FALL  = -20f
    }
}
