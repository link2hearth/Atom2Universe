package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.world.*
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.render.Camera
import kotlin.math.*
import kotlin.random.Random

/** Decorative wildlife, bounded and local. Never enters combat, farming inventories or saves. */
internal class AmbientWildlife(private val world: World, seed: Long) {
    companion object {
        const val MAX_CREATURES = 28
        const val VISIBLE_RADIUS = 72f
        private const val STEP_SECONDS = .05
    }
    enum class Kind { BUTTERFLY, BEE, BIRD, FIREFLY, FISH, DRAGONFLY }
    enum class Activity { TRAVEL, FORAGE, REST }
    data class Flower(val x: Int, val y: Int, val z: Int, val block: Short)
    class Creature(val kind: Kind, val homeX: Double, val homeY: Double, val homeZ: Double,
                   val phase: Float, val coat: Int) {
        var x = homeX; var y = homeY; var z = homeZ
        var yaw = 0f; var age = 0f; var alpha = 0f
        var previousX = x; var previousY = y; var previousZ = z
        var previousYaw = yaw; var previousAlpha = alpha
        var retiring = false
        var activity = Activity.TRAVEL
        var targetX = x; var targetY = y; var targetZ = z
        var targetTime = 0f; var restTime = 0f; var blockedTime = 0f
        var flower: Flower? = null
        var lastFlower: Flower? = null
        var activityBlend = 0f; var previousActivityBlend = 0f

        fun rememberPose() {
            previousX = x; previousY = y; previousZ = z
            previousYaw = yaw; previousAlpha = alpha
            previousActivityBlend = activityBlend
        }
    }
    val creatures = ArrayList<Creature>(MAX_CREATURES)
    var animationTime = 0f; private set
    private val random = Random(seed xor 0x5a174bL)
    private var spawnTimer = 0f
    private var step = 0.0
    /** Interpolate only between two collision-checked poses; never extrapolate through blocks. */
    val renderFraction get() = (step / STEP_SECONDS).toFloat().coerceIn(0f, 1f)
    private var birdCooldown = 12f

    fun clear() { creatures.clear(); step = 0.0; spawnTimer = 0f }

    fun update(dt: Float, camera: Camera, daylight: Float, rain: Float) {
        // Keep the fractional remainder at every frame rate. Bound catch-up after a stall.
        val elapsed = dt.coerceIn(0f, .1f)
        animationTime += elapsed
        birdCooldown = (birdCooldown - elapsed).coerceAtLeast(0f)
        step += elapsed.toDouble()
        while (step >= STEP_SECONDS) {
            step -= STEP_SECONDS
            simulate(STEP_SECONDS.toFloat(), camera, daylight, rain)
        }
    }

    private fun simulate(tick: Float, camera: Camera, daylight: Float, rain: Float) {
        val px=camera.playerX; val py=camera.playerY; val pz=camera.playerZ
        var goalBudget = 2 // Terrain searches are spread over frames, including after a time change.
        val iterator = creatures.iterator()
        while (iterator.hasNext()) {
            val a = iterator.next()
            a.rememberPose()
            a.age += tick
            val night = daylight < .45f
            val wrongTime = (a.kind == Kind.FIREFLY) != night && a.kind != Kind.FISH
            val playerDistance = hypot(a.x-px,a.z-pz)
            if ((a.age > 180f && playerDistance > 48) || wrongTime || (rain > .45f && a.kind != Kind.FISH)) a.retiring = true
            a.alpha = (a.alpha + tick * if (a.retiring) -.5f else .35f).coerceIn(0f, 1f)
            if ((a.retiring && a.alpha == 0f) || playerDistance > VISIBLE_RADIUS+4 || abs(a.y - py) > 40) {
                iterator.remove(); continue
            }
            if (!free(a.kind, a.x, a.y, a.z)) {
                iterator.remove() // Water drained, new wall, or unloaded chunk.
                continue
            }
            a.targetTime -= tick
            val startled = a.kind != Kind.FISH && playerDistance < 2.0 && abs(a.y-py) < 2.0
            val flower = a.flower
            if (flower != null && block(flower.x,flower.y,flower.z) != flower.block) {
                a.flower = null; a.activity = Activity.TRAVEL; a.targetTime = 0f
            }
            if (startled && a.activity != Activity.TRAVEL) {
                a.lastFlower = a.flower; a.flower = null
                a.activity = Activity.TRAVEL; a.targetTime = 0f
            }
            if (a.activity != Activity.TRAVEL) {
                a.restTime -= tick
                if (a.restTime <= 0f) {
                    a.lastFlower = a.flower; a.flower = null
                    a.activity = Activity.TRAVEL; a.targetTime = 0f
                }
            }
            if (a.activity == Activity.TRAVEL && a.targetTime <= 0f && goalBudget > 0) {
                goalBudget--
                chooseDestination(a, allowFlowers = !startled)
            }
            val toGoal = sqrt((a.targetX-a.x).pow(2)+(a.targetY-a.y).pow(2)+(a.targetZ-a.z).pow(2))
            if (a.activity == Activity.TRAVEL && a.targetTime > 0f && toGoal < .30) {
                if (a.flower != null) {
                    a.activity = Activity.FORAGE; a.restTime = 3f+random.nextFloat()*5f
                } else if (a.kind == Kind.BIRD || a.kind == Kind.FISH) {
                    a.targetTime = 0f
                } else {
                    a.activity = Activity.REST; a.restTime = .6f+random.nextFloat()*1.5f
                }
            }
            a.activityBlend += ((if (a.activity == Activity.FORAGE) 1f else 0f)-a.activityBlend)*tick*4f
            val moving = a.activity == Activity.TRAVEL
            val bob = if (moving) .12 else .025
            var targetX = a.targetX
            val targetY = a.targetY + sin(a.age*2.0+a.phase)*bob
            var targetZ = a.targetZ
            // Repulsion fades to zero at its boundary instead of jumping by 0.7 blocks.
            val distance = hypot(a.x - px, a.z - pz)
            if (a.kind != Kind.FISH && distance in .001..2.0) {
                val horizontal = (1.0-distance/2.0).let { it*it*(3.0-2.0*it) }
                val vertical = (1.0-abs(a.y-py)/2.0).coerceIn(0.0,1.0)
                val push = horizontal * vertical * 1.2
                targetX += (a.x-px)/distance*push; targetZ += (a.z-pz)/distance*push
            }
            // Destinations change; position never jumps to them. Keep interpolation and swept collision.
            val dx=targetX-a.x; val dy=targetY-a.y; val dz=targetZ-a.z
            val travel=sqrt(dx*dx+dy*dy+dz*dz)
            val maxSpeed=when(a.kind) { Kind.BIRD -> 3.2; Kind.FISH -> 1.4; Kind.BEE -> 1.2; else -> 1.6 }
            val follow=minOf(1.0-exp(-tick.toDouble()*4.0), maxSpeed*tick/travel.coerceAtLeast(.000001))
            val nx=a.x+dx*follow; val ny=a.y+dy*follow; val nz=a.z+dz*follow
            if (canMove(a, nx, ny, nz)) {
                if (hypot(nx-a.x,nz-a.z) > .0001) {
                    val targetYaw = atan2((nx - a.x).toFloat(), (nz - a.z).toFloat())
                    val turn = atan2(sin(targetYaw-a.yaw), cos(targetYaw-a.yaw))
                    a.yaw += turn.coerceIn(-tick*4f, tick*4f)
                }
                a.x = nx; a.y = ny; a.z = nz
                a.blockedTime = 0f
            } else {
                a.blockedTime += tick
                // Gain a little height to clear vegetation; solid walls still require a new route.
                val rise = a.y + maxSpeed*tick*.5
                if (a.kind != Kind.FISH && moving && a.blockedTime < 1f && canMove(a,a.x,rise,a.z)) a.y = rise
                if (a.blockedTime > .6f) {
                    a.lastFlower = a.flower; a.flower = null
                    a.activity = Activity.TRAVEL; a.targetTime = 0f; a.blockedTime = 0f
                }
            }
        }
        spawnTimer -= tick
        if (spawnTimer > 0f || creatures.size >= MAX_CREATURES) return
        spawnTimer = .8f
        repeat(2) { populate(camera, daylight, rain) }
    }

    private fun isFlower(b: Short) = b.toInt() in 7040..7045 || b.toInt() == 7048 || b.toInt() == 7054

    /** Actual loaded ground, including edits. Never request terrain generation for ambient life. */
    private fun groundAt(x: Int, z: Int, nearY: Double): Int? {
        val surface = world.surfaceTopY(x,z)
        if (abs(surface-nearY) > 24) return null
        for (y in surface+8 downTo surface-5) {
            val b = block(x,y,z) ?: return null
            if (b != AIR && !isDecoration(b) && !isLeaf(b)) return y
        }
        return null
    }

    private fun chooseDestination(a: Creature, allowFlowers: Boolean) {
        if (allowFlowers && (a.kind == Kind.BEE || a.kind == Kind.BUTTERFLY)) {
            var best: Flower? = null
            var bestDistance = Double.MAX_VALUE
            // Search the surrounding plants, not the original spawn point. Each visit advances the route.
            repeat(24) {
                val angle = random.nextDouble()*PI*2
                val distance = 3.0+random.nextDouble()*12
                val x = floor(a.x+sin(angle)*distance).toInt()
                val z = floor(a.z+cos(angle)*distance).toInt()
                val y = groundAt(x,z,a.y)?.plus(1) ?: return@repeat
                val b = block(x,y,z) ?: return@repeat
                if (!isFlower(b)) return@repeat
                val flower = Flower(x,y,z,b)
                if (flower == a.lastFlower || creatures.any { it !== a && it.flower == flower }) return@repeat
                val height = y+maxOf(1.28,BlockRegistry.getSpriteHeight(b).toDouble()+.30)
                val dist = hypot(x+.5-a.x,z+.5-a.z)
                if (dist < bestDistance && free(a.kind,x+.5,height,z+.5) && weatherRoof(world,x,z,height) <= height) {
                    best = flower; bestDistance = dist
                }
            }
            best?.let { flower ->
                a.flower = flower
                a.targetX = flower.x+.5; a.targetZ = flower.z+.5
                a.targetY = flower.y+maxOf(1.28,BlockRegistry.getSpriteHeight(flower.block).toDouble()+.30)
                a.targetTime = 30f
                return
            }
        }
        a.flower = null
        repeat(12) {
            val angle = a.yaw.toDouble()+random.nextDouble(-1.8,1.8)
            val distance = when(a.kind) {
                Kind.BIRD -> random.nextDouble(14.0,28.0)
                Kind.FISH -> random.nextDouble(4.0,10.0)
                Kind.FIREFLY -> random.nextDouble(4.0,10.0)
                else -> random.nextDouble(7.0,17.0)
            }
            val x=a.x+sin(angle)*distance; val z=a.z+cos(angle)*distance
            val bx=floor(x).toInt(); val bz=floor(z).toInt()
            val y = if(a.kind == Kind.FISH) {
                a.y+random.nextDouble(-.4,.4)
            } else {
                val ground = groundAt(bx,bz,a.y) ?: return@repeat
                val floorBlock = block(bx,ground,bz) ?: return@repeat
                if (a.kind == Kind.DRAGONFLY && !isWater(floorBlock)) return@repeat
                val roof=weatherRoof(world,bx,bz,a.y)
                if (a.kind == Kind.BIRD) maxOf(ground+7.0,roof+1.0)+random.nextDouble()*3
                else {
                    val height=ground+1.7+random.nextDouble()*1.2
                    if (roof > height) return@repeat
                    height
                }
            }
            if (free(a.kind,x,y,z)) {
                a.targetX=x; a.targetY=y; a.targetZ=z
                a.targetTime=30f
                return
            }
        }
        // No valid destination yet (small pond, streaming edge): wait briefly and retry elsewhere.
        a.targetX=a.x; a.targetY=a.y; a.targetZ=a.z
        a.activity=Activity.REST; a.restTime=1f+random.nextFloat()
        a.targetTime=0f
    }

    private fun populate(camera: Camera, daylight: Float, rain: Float) {
        val px=camera.playerX; val py=camera.playerY; val pz=camera.playerZ
        if (creatures.size >= MAX_CREATURES) return
        val angle = random.nextDouble() * PI * 2
        val distance = 34 + random.nextDouble() * 16
        val x = floor(px + sin(angle) * distance).toInt()
        val z = floor(pz + cos(angle) * distance).toInt()
        val surface = world.surfaceTopY(x, z)
        if (abs(surface - py) > 22) return
        // Actual edited ground, including plants and leaves, takes precedence over the generator.
        var ground = surface - 1
        for (y in surface + 8 downTo surface - 5) {
            val b = block(x, y, z) ?: return
            if (b != AIR && !isDecoration(b) && !isLeaf(b)) { ground = y; break }
        }
        val floorBlock = block(x, ground, z) ?: return
        val aquatic = isWater(floorBlock)
        val climate = world.vegetationClimateAt(x, z)
        val green = floorBlock == GRASS || floorBlock == MOSS || floorBlock == FOREST_FLOOR
        val kind = when {
            aquatic && random.nextInt(3) != 0 -> Kind.FISH
            rain > .35f || climate == 4 -> return
            daylight < .45f && (green || aquatic) -> Kind.FIREFLY
            daylight < .45f -> return
            aquatic -> Kind.DRAGONFLY
            !green -> return
            else -> when (random.nextInt(20)) {
                0 -> Kind.BIRD
                in 1..6 -> Kind.BEE
                else -> Kind.BUTTERFLY
            }
        }
        if (kind == Kind.BIRD && (birdCooldown > 0f || creatures.count { it.kind == Kind.BIRD } >= 2)) return
        val y = ground + when (kind) { Kind.FISH -> -.35; Kind.BIRD -> 7.5; else -> 1.8 }
        if (kind != Kind.FISH && weatherRoof(world, x, z, y) > y) return
        val group = if (kind == Kind.FISH) 3 else 1
        repeat(group) { i ->
            val nx = x + .5 + i * .7
            // Entire spawn is behind the actual eye plane, including in third-person view.
            val behind = (nx-camera.x)*camera.aimX+(y-camera.y)*camera.aimY+(z+.5-camera.z)*camera.aimZ < -2.0
            if (creatures.size < MAX_CREATURES && behind && free(kind, nx, y, z + .5)) {
                creatures += Creature(kind, nx, y, z + .5, random.nextFloat() * 6.28f, random.nextInt(4)).apply {
                    yaw = atan2((px-nx).toFloat(),(pz-z-.5).toFloat()) + random.nextFloat()-.5f
                    previousYaw = yaw
                }
                if (kind == Kind.BIRD) birdCooldown = 40f + random.nextFloat()*40f
            }
        }
    }

    private fun block(x: Int, y: Int, z: Int): Short? {
        val c = world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16))
            ?.takeIf { it.generated } ?: return null
        return c.blockAt(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16))
    }

    private fun free(kind: Kind, x: Double, y: Double, z: Double): Boolean {
        val r = when (kind) { Kind.BIRD -> .65; Kind.FISH -> .45; Kind.DRAGONFLY -> .33; else -> .28 }
        val h = if (kind == Kind.BIRD) .4 else .25
        for (bx in floor(x-r).toInt()..floor(x+r).toInt())
            for (by in floor(y-h).toInt()..floor(y+h).toInt())
                for (bz in floor(z-r).toInt()..floor(z+r).toInt()) {
                    val b = block(bx, by, bz) ?: return false
                    if (kind == Kind.FISH) {
                        // Only full source water: no fish in thin flowing films or waterfalls.
                        if (b != WATER) return false
                    } else if (b != AIR) return false
                }
        return true
    }

    private fun canMove(a: Creature, x: Double, y: Double, z: Double): Boolean {
        // Also test the path: fish must not jump across a dry bank into another pond.
        val steps = ceil(maxOf(abs(x-a.x), abs(y-a.y), abs(z-a.z))/.25).toInt().coerceAtLeast(1)
        for (i in 1..steps) {
            val t = i.toDouble()/steps
            if (!free(a.kind, a.x+(x-a.x)*t, a.y+(y-a.y)*t, a.z+(z-a.z)*t)) return false
        }
        return true
    }
}
