package com.Atom2Universe.app.games.cosmorun

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Simulation en mètres, indépendante du rendu et d'Android. Mutations sur le thread UI. */
class CosmoRunGame(private val random: Random = Random.Default) {
    companion object {
        const val NUM_LANES = 5
        const val LANE_WIDTH = 1.6f
        const val SPAWN_Z = 108f
        const val BASE_SPEED = 16f
        const val MAX_SPEED = 29f
        const val JUMP_DURATION = .94f
        const val SLIDE_DURATION = .86f
        fun laneX(lane: Float) = (lane - 2f) * LANE_WIDTH
    }

    enum class EntityType { CARGO, HURDLE, LASER, GAP, DRONE, ATOM, SHIELD, MAGNET, BOOST }
    enum class Contract { DISTANCE, ATOMS, SECTORS }
    enum class Event { ATOM, SHIELD, MAGNET, BOOST, SHIELD_BREAK, CONTRACT, SECTOR, CRASH, CLEAR }
    class Entity(val type: EntityType, val lane: Int, var z: Float, val variant: Int = 0, val row: Int = -1) {
        var resolved = false
        var x = laneX(lane.toFloat())
        var y = 1f
        var attracting = false
        val collectible get() = type == EntityType.ATOM || type == EntityType.SHIELD ||
            type == EntityType.MAGNET || type == EntityType.BOOST
    }

    val entities = ArrayList<Entity>(160)
    var onEvent: ((Event, Int) -> Unit)? = null
    var laneTarget = 2; private set
    var laneF = 2f; private set
    var jumpProgress = -1f; private set
    var slideTime = 0f; private set
    val isJumping get() = jumpProgress >= 0f
    val isSliding get() = slideTime > 0f
    val jumpHeight get() = if (isJumping) sin(jumpProgress * Math.PI).toFloat() * 2.05f else 0f
    var distance = 0f; private set
    var speed = BASE_SPEED; private set
    var atomsCollected = 0; private set
    var obstaclesCleared = 0; private set
    var contractsThisRun = 0; private set
    var chain = 0; private set
    var maxChain = 0; private set
    var shield = false; private set
    var magnetTime = 0f; private set
    var boostTime = 0f; private set
    var invincibleTime = 0f; private set
    var isRunning = false; private set
    var isGameOver = false; private set
    var bestScore = 0; private set
    var crashType = EntityType.CARGO; private set
    var contractLevel = 0; private set
    var contractProgress = 0f; private set
    val contract get() = Contract.entries[contractLevel % Contract.entries.size]
    val contractTarget: Int get() = when (contract) {
        Contract.DISTANCE -> 400 + min(contractLevel / 3, 12) * 100
        Contract.ATOMS -> 30 + min(contractLevel / 3, 12) * 5
        Contract.SECTORS -> 12 + min(contractLevel / 3, 12) * 2
    }
    val multiplier get() = (1 + chain / 8).coerceAtMost(5)
    val sector get() = distance.toInt() / 600
    val score get() = distance.toInt() + bonusScore

    private var bonusScore = 0
    private var chainTime = 0f
    private var safeLane = 2
    private var nextRowAt = 0f
    private var rowId = 0
    private var lastClearedRow = -1

    fun initBestScore(saved: Int) { bestScore = maxOf(bestScore, saved) }
    fun restoreCareer(level: Int, progress: Float) {
        contractLevel = level.coerceIn(0, 100_000)
        contractProgress = if (progress.isFinite()) progress.coerceIn(0f, contractTarget - .001f) else 0f
    }

    fun start() {
        entities.clear()
        laneTarget = 2; laneF = 2f; jumpProgress = -1f; slideTime = 0f
        distance = 0f; speed = BASE_SPEED; atomsCollected = 0; bonusScore = 0
        obstaclesCleared = 0; contractsThisRun = 0; chain = 0; maxChain = 0; chainTime = 0f
        shield = true; magnetTime = 0f; boostTime = 0f; invincibleTime = 0f
        safeLane = 2; rowId = 0; lastClearedRow = -1; nextRowAt = 0f
        isRunning = true; isGameOver = false
        for (i in 0..9) entities.add(Entity(EntityType.ATOM, 2, 12f + i * 4f))
    }

    fun enterHangar() {
        start()
        isRunning = false
        entities.clear()
    }

    fun moveLeft() { if (isRunning) laneTarget = (laneTarget - 1).coerceAtLeast(0) }
    fun moveRight() { if (isRunning) laneTarget = (laneTarget + 1).coerceAtMost(NUM_LANES - 1) }
    fun jump() { if (isRunning && !isJumping) { slideTime = 0f; jumpProgress = 0f } }
    fun slide() { if (isRunning) { jumpProgress = -1f; slideTime = SLIDE_DURATION } }

    fun update(dt: Float) {
        if (!isRunning || !dt.isFinite() || dt <= 0f) return
        // Sous-pas bornés : même un appel lent ne traverse pas un obstacle sans collision.
        var remaining = dt.coerceAtMost(.25f)
        while (remaining > 0f && isRunning) {
            val step = min(remaining, 1f / 120f)
            tick(step)
            remaining -= step
        }
    }

    private fun tick(dt: Float) {
        val oldSector = sector
        speed = (BASE_SPEED + distance * .006f).coerceAtMost(MAX_SPEED)
        val travelled = speed * dt
        distance += travelled
        advanceContract(Contract.DISTANCE, travelled)
        if (sector != oldSector) onEvent?.invoke(Event.SECTOR, laneTarget)
        laneF += (laneTarget - laneF).coerceIn(-dt / .13f, dt / .13f)
        if (isJumping) {
            jumpProgress += dt / JUMP_DURATION
            if (jumpProgress >= 1f) jumpProgress = -1f
        }
        slideTime = (slideTime - dt).coerceAtLeast(0f)
        magnetTime = (magnetTime - dt).coerceAtLeast(0f)
        boostTime = (boostTime - dt).coerceAtLeast(0f)
        invincibleTime = (invincibleTime - dt).coerceAtLeast(0f)
        chainTime -= dt
        if (chainTime <= 0f) chain = 0

        var crossedRow = -1
        val iterator = entities.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            val previousZ = e.z
            e.z -= travelled
            if (e.z < -9f) { iterator.remove(); continue }
            if (e.resolved) continue
            val onLane = abs(laneF - e.lane) < .48f
            if (e.collectible) {
                if (e.type == EntityType.ATOM && (magnetTime > 0f || boostTime > 0f) && e.z in 0f..13f) {
                    e.attracting = true
                }
                // L'aimant déplace réellement le modèle vers le torse. Le crédit et les
                // particules attendent son arrivée, même si le bonus expire pendant le trajet.
                val reachedPlayer = if (e.attracting) {
                    val dx = laneX(laneF) - e.x
                    val dy = .9f + jumpHeight - (if (isSliding) .4f else 0f) - e.y
                    val dz = -e.z
                    val length = sqrt(dx * dx + dy * dy + dz * dz)
                    val step = 38f * dt
                    val fraction = if (length > 0f) (step / length).coerceAtMost(1f) else 1f
                    e.x += dx * fraction; e.y += dy * fraction; e.z += dz * fraction
                    length <= step + .15f
                } else {
                    // Même plan Z que le personnage ; test de franchissement pour ne pas
                    // manquer un ramassage lorsque la vitesse ou la durée d'image augmente.
                    onLane && previousZ >= 0f && e.z <= 0f
                }
                if (reachedPlayer) {
                    collect(e); iterator.remove()
                }
            } else if (previousZ > .65f && e.z <= .65f) {
                e.resolved = true
                val avoids = when (e.type) {
                    EntityType.HURDLE, EntityType.GAP -> jumpHeight > .85f
                    EntityType.LASER, EntityType.DRONE -> isSliding
                    else -> false
                }
                if (onLane && !avoids && boostTime <= 0f && invincibleTime <= 0f) {
                    if (shield) {
                        shield = false; invincibleTime = 1.5f; chain = 0
                        onEvent?.invoke(Event.SHIELD_BREAK, e.lane)
                    } else {
                        crashType = e.type; isRunning = false; isGameOver = true
                        bestScore = maxOf(bestScore, score)
                        onEvent?.invoke(Event.CRASH, e.lane)
                        return
                    }
                } else if (onLane && avoids) {
                    bonusScore += 40 * multiplier
                    onEvent?.invoke(Event.CLEAR, e.lane)
                }
                crossedRow = maxOf(crossedRow, e.row)
            }
        }
        // Valider après toutes les collisions de la rangée, jamais avant un choc fatal.
        if (crossedRow > lastClearedRow) {
            lastClearedRow = crossedRow
            obstaclesCleared++
            advanceContract(Contract.SECTORS, 1f)
        }
        if (distance >= nextRowAt) spawnRow()
    }

    private fun collect(e: Entity) {
        when (e.type) {
            EntityType.ATOM -> {
                chain++; maxChain = maxOf(maxChain, chain); chainTime = 2.8f
                atomsCollected++; bonusScore += 25 * multiplier
                advanceContract(Contract.ATOMS, 1f)
                onEvent?.invoke(Event.ATOM, e.lane)
            }
            EntityType.SHIELD -> { shield = true; onEvent?.invoke(Event.SHIELD, e.lane) }
            EntityType.MAGNET -> { magnetTime = 9f; onEvent?.invoke(Event.MAGNET, e.lane) }
            EntityType.BOOST -> { boostTime = 6f; onEvent?.invoke(Event.BOOST, e.lane) }
            else -> Unit
        }
    }

    private fun advanceContract(kind: Contract, amount: Float) {
        if (contract != kind) return
        contractProgress += amount
        if (contractProgress >= contractTarget) {
            contractProgress = 0f; contractLevel++; contractsThisRun++
            bonusScore += 1000
            onEvent?.invoke(Event.CONTRACT, laneTarget)
        }
    }

    private fun spawnRow() {
        // Voie entièrement libre, décalée d'une voie maximum. Au moins 1,35 s entre rangées,
        // même au plafond de vitesse ; les lignes d'atomes ne débordent pas sur la suivante.
        safeLane = (safeLane + random.nextInt(-1, 2)).coerceIn(0, NUM_LANES - 1)
        val available = (0 until NUM_LANES).filter { it != safeLane }.shuffled(random)
        val count = when { distance < 160f -> 2; distance < 650f -> 3; else -> 4 }
        val pattern = rowId % 7
        for (lane in available.take(count)) {
            val type = when {
                rowId == 0 -> EntityType.CARGO
                pattern == 1 -> EntityType.HURDLE
                pattern == 2 -> EntityType.LASER
                pattern == 3 && distance > 300f -> EntityType.GAP
                pattern == 4 && distance > 450f -> EntityType.DRONE
                pattern == 5 -> if (lane % 2 == 0) EntityType.HURDLE else EntityType.LASER
                else -> EntityType.CARGO
            }
            entities.add(Entity(type, lane, SPAWN_Z, random.nextInt(4), rowId))
        }
        for (i in 0..4) entities.add(Entity(EntityType.ATOM, safeLane, SPAWN_Z - 10f + i * 3f))
        if (rowId % 4 == 2) {
            val bonus = when ((rowId / 4) % 3) {
                0 -> EntityType.MAGNET
                1 -> EntityType.SHIELD
                else -> EntityType.BOOST
            }
            entities.add(Entity(bonus, safeLane, SPAWN_Z + 7f))
        }
        rowId++
        nextRowAt = distance + maxOf(30f, MAX_SPEED * (1.35f + random.nextFloat() * .3f))
    }
}
