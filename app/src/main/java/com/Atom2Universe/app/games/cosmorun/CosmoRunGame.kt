package com.Atom2Universe.app.games.cosmorun

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Simulation en mètres, indépendante du rendu et d'Android. Mutations sur le thread UI.
 *
 * Le monde est fait de boîtes : chaque objet occupe une voie, un intervalle en z (son bord avant [Entity.z]
 * et sa [Entity.length]) et un intervalle de hauteur. Le joueur est une boîte qui saute, glisse et
 * grimpe sur les dessus praticables (conteneurs, rampes). Une seule routine de collision continue.
 */
class CosmoRunGame(private val random: Random = Random.Default) {
    companion object {
        const val NUM_LANES = 5
        const val LANE_WIDTH = 1.6f
        const val MIN_SPEED = 17f
        const val MAX_SPEED = 28f
        /** Le monde se génère jusqu'à cette avance, en secondes de course (pas en mètres). */
        const val HORIZON_SECONDS = 3.2f
        const val GRAVITY = 34f
        const val JUMP_SPEED = 10.9f
        const val DIVE_SPEED = 26f
        const val SLIDE_TIME = .48f
        const val LANE_TIME = .11f
        const val COYOTE_TIME = .08f
        const val JUMP_BUFFER = .12f
        const val STAND_HEIGHT = 1.85f
        const val SLIDE_HEIGHT = .8f
        const val PLAYER_HALF = .24f
        const val CONTAINER_HEIGHT = 2.4f
        /** Dénivelé qu'on gravit sans sauter : une rampe, jamais un mur. */
        const val STEP = .35f
        private const val HALF_HAZARD = .68f
        /** Tolérance en faveur du joueur : on frôle sans être touché. */
        private const val FORGIVE = .12f
        /** Demi-largeur du sol sous les pieds : plus large qu'une demi-voie, sans trou entre deux toits voisins. */
        private const val SUPPORT_HALF = .85f
        const val WARDEN_TIME = 5f
        const val HIT_GRACE = .35f
        const val SHIELD_GRACE = .6f
        const val BOOST_GRACE = .5f
        /** Un météore commence à tomber 1,6 s avant le joueur et touche le sol 0,7 s avant lui. */
        const val METEOR_WARN = 1.6f
        const val METEOR_LAND = .7f
        const val STUMBLE_SLOW = 1f
        const val MAGNET_TIME = 8f
        const val BOOST_TIME = 6f
        const val CHAIN_IDLE = 2f
        const val SECTOR_LENGTH = 600
        val TIERS = intArrayOf(0, 10, 25, 50, 100)
        fun laneX(lane: Float) = (lane - 2f) * LANE_WIDTH
        fun baseSpeed(distance: Float) = min(MAX_SPEED, MIN_SPEED + distance * .0025f)
    }

    enum class EntityType(val depth: Float, val yMin: Float, val yMax: Float,
                          val hazard: Boolean = false, val solid: Boolean = false) {
        CONTAINER(0f, 0f, CONTAINER_HEIGHT, solid = true),
        RAMP(0f, 0f, CONTAINER_HEIGHT, solid = true),
        HURDLE(.45f, 0f, .9f, hazard = true),
        LASER(.3f, 1.15f, 2.6f, hazard = true),
        DRONE(.7f, 1.1f, 2.2f, hazard = true),
        /** Un rocher qui tombe : annoncé au sol, il n'existe qu'à l'impact. Se saute ou se contourne. */
        METEOR(.6f, 0f, .9f, hazard = true),
        ATOM(.4f, .7f, 1.1f), ATOM_HIGH(.4f, 1.9f, 2.3f),
        SHIELD(.5f, .7f, 1.3f), MAGNET(.5f, .7f, 1.3f), BOOST(.5f, .7f, 1.3f);
        val blocking get() = hazard || solid
        val overhead get() = this == LASER || this == DRONE
        val pickup get() = !blocking
    }
    enum class Event { ATOM, SHIELD, MAGNET, BOOST, SHIELD_BREAK, STUMBLE, CRASH, SMASH, SCRAPE, CLEAR,
        JUMP, LAND, SLIDE, LANE, METEOR, WARDEN_OFF, MISSION, RANK, RECORD, SECTOR, TIER }

    class Entity(val type: EntityType, val lane: Int, var z: Float, val length: Float,
                 val baseY: Float = 0f, val variant: Int = 0) {
        var x = laneX(lane.toFloat())
        var y = baseY + (type.yMin + type.yMax) / 2f
        var attracting = false
        var smashed = false
        var dead = false
        var avoided = false
        /** Météore : a-t-il touché le sol ? [drop] va de 1 (en haut du ciel) à 0 (posé). */
        var landed = false
        var drop = 1f
        var scored = false
        /** Nom du motif d'où vient l'objet (diagnostic). */
        var source = ""
        val zCenter get() = z + length / 2f
        val top get() = baseY + type.yMax
        val pickup get() = type.pickup
        /** Hauteur du dessus praticable à la distance [zAhead] du bord avant. */
        fun topAt(zAhead: Float): Float = when (type) {
            EntityType.RAMP -> baseY + CONTAINER_HEIGHT * (zAhead / length).coerceIn(0f, 1f)
            else -> top
        }
    }

    val entities = ArrayList<Entity>(200)
    var onEvent: ((Event, Int) -> Unit)? = null
    var laneTarget = 2; private set
    var laneF = 2f; private set
    /** Hauteur des pieds, et vitesse verticale. */
    var playerY = 0f; private set
    private var vy = 0f
    var grounded = true; private set
    var slideTime = 0f; private set
    val isSliding get() = slideTime > 0f
    val isJumping get() = !grounded
    /** Vers le haut du saut, 0 → 1 → 0 : pour animer le personnage. */
    val jumpHeight get() = if (grounded) 0f else playerY
    val playerHeight get() = if (isSliding) SLIDE_HEIGHT else STAND_HEIGHT
    /** Hauteur de la caméra au-dessus de son niveau de base : elle suit le joueur quand il monte. */
    var cameraLift = 0f; private set
    /** Hauteur de ce qui porte le joueur (sol ou toit) : pour l'ombre. */
    var supportY = 0f; private set
    var distance = 0f; private set
    var speed = MIN_SPEED; private set
    var atomsCollected = 0; private set
    var hazardsCleared = 0; private set
    var smashes = 0; private set
    var roofSeconds = 0f; private set
    var missionsThisRun = 0; private set
    var chain = 0; private set
    var maxChain = 0; private set
    var shield = false; private set
    var magnetTime = 0f; private set
    var boostTime = 0f; private set
    var wardenTime = 0f; private set
    var invincibleTime = 0f; private set
    var isRunning = false; private set
    var isGameOver = false; private set
    var bestScore = 0; private set
    var crashType = EntityType.CONTAINER; private set
    /** Motif du dernier obstacle qui nous a touchés (diagnostic et statistiques). */
    var lastHitSource = ""; private set
    val missions = CosmoRunMissions()
    /** Distance du meilleur record et de la dernière mort : la piste y dresse une borne. */
    var bestDistance = 0f
    var lastDeathDistance = 0f
    private var recordPassed = false
    val multiplier get() = TIERS.indexOfLast { chain >= it } + 1
    val sector get() = distance.toInt() / SECTOR_LENGTH
    val score get() = distance.toInt() + bonusScore
    val onRoof get() = grounded && playerY > STEP

    private val director = CosmoRunDirector(random)
    private var bonusScore = 0
    private var chainIdle = 0f
    private var slowTime = 0f
    private var coyote = 0f
    private var jumpBuffer = 0f
    private var diving = false
    private var stableLane = 2
    private var scrapeLock = 0f
    private var frontier = 0f
    var roofsEnabled = true

    fun initBestScore(saved: Int) { bestScore = maxOf(bestScore, saved) }
    fun restoreMissions(rank: Int, progress: FloatArray) = missions.restore(rank, progress)

    fun start(resetMissions: Boolean = true) {
        entities.clear()
        laneTarget = 2; laneF = 2f; stableLane = 2
        playerY = 0f; vy = 0f; grounded = true; slideTime = 0f; coyote = 0f; jumpBuffer = 0f; diving = false
        cameraLift = 0f
        distance = 0f; speed = MIN_SPEED; atomsCollected = 0; bonusScore = 0
        hazardsCleared = 0; smashes = 0; roofSeconds = 0f; missionsThisRun = 0; recordPassed = bestDistance <= 0f
        if (resetMissions) missions.startRun()
        chain = 0; maxChain = 0; chainIdle = 0f
        shield = false; magnetTime = 0f; boostTime = 0f; wardenTime = 0f; invincibleTime = 0f
        slowTime = 0f; scrapeLock = 0f
        director.reset(); director.roofsEnabled = roofsEnabled
        frontier = 30f
        isRunning = true; isGameOver = false
        spawnAhead()
    }

    fun enterHangar() {
        start(resetMissions = false)
        isRunning = false
        entities.clear()
    }

    fun moveLeft() = changeLane(-1)
    fun moveRight() = changeLane(1)
    private fun changeLane(direction: Int) {
        if (!isRunning) return
        val next = (laneTarget + direction).coerceIn(0, NUM_LANES - 1)
        if (next == laneTarget) return
        stableLane = laneTarget
        laneTarget = next
        onEvent?.invoke(Event.LANE, next)
    }

    fun jump() {
        if (!isRunning) return
        if (grounded || coyote > 0f) startJump() else jumpBuffer = JUMP_BUFFER
    }

    fun slide() {
        if (!isRunning) return
        if (grounded) {
            if (!isSliding) onEvent?.invoke(Event.SLIDE, laneTarget)
            slideTime = SLIDE_TIME
        } else {
            // Plongeon : chute rapide, puis glissade à l'atterrissage.
            vy = min(vy, -DIVE_SPEED); diving = true
        }
    }

    private fun startJump() {
        vy = JUMP_SPEED; grounded = false; slideTime = 0f; coyote = 0f; jumpBuffer = 0f
        onEvent?.invoke(Event.JUMP, laneTarget)
    }

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
        val base = baseSpeed(distance)
        val target = base * (if (boostTime > 0f) 1.35f else 1f) * (if (slowTime > 0f) .7f else 1f)
        speed += (target - speed) * min(1f, dt * 4f)
        val travelled = speed * dt
        distance += travelled
        completed(missions.report(CosmoRunMissions.Kind.DISTANCE, distance))
        if (!recordPassed && distance >= bestDistance) { recordPassed = true; onEvent?.invoke(Event.RECORD, 0) }
        if (sector != oldSector) onEvent?.invoke(Event.SECTOR, sector)

        magnetTime = max(0f, magnetTime - dt)
        val boosting = boostTime > 0f
        boostTime = max(0f, boostTime - dt)
        invincibleTime = max(0f, invincibleTime - dt)
        // La surrégime finit en douceur : la vitesse redescend pendant que les chocs restent sans effet.
        if (boosting && boostTime == 0f) invincibleTime = max(invincibleTime, BOOST_GRACE)
        slowTime = max(0f, slowTime - dt)
        scrapeLock = max(0f, scrapeLock - dt)
        if (wardenTime > 0f) {
            wardenTime = max(0f, wardenTime - dt)
            if (wardenTime == 0f) onEvent?.invoke(Event.WARDEN_OFF, 0)
        }
        laneF += (laneTarget - laneF).coerceIn(-dt / LANE_TIME, dt / LANE_TIME)
        vertical(dt)
        if (onRoof) { roofSeconds += dt; completed(missions.add(CosmoRunMissions.Kind.ROOF, dt)) }

        if (!moveEntities(travelled, dt)) return
        chainIdle += dt
        if (chainIdle >= CHAIN_IDLE && chain > 0) {
            val tier = TIERS.indexOfLast { chain >= it }
            chain = if (tier <= 0) 0 else TIERS[tier] - 1
            chainIdle = 0f
        }
        val lift = supportY * .55f + (playerY - supportY).coerceAtLeast(0f) * .35f
        cameraLift += (lift - cameraLift) * min(1f, dt * 5f)
        spawnAhead()
    }

    /** Sol sous le joueur : le plus haut dessus praticable qu'il a les pieds assez haut pour gravir. */
    private fun groundHeight(): Float {
        var ground = 0f
        val px = laneX(laneF)
        for (e in entities) {
            if (!e.type.solid || e.smashed) continue
            if (abs(px - e.x) >= SUPPORT_HALF) continue
            if (e.z > 0f || e.z + e.length < 0f) continue
            val top = e.topAt(-e.z)
            if (playerY >= top - STEP && top > ground) ground = top
        }
        return ground
    }

    private fun vertical(dt: Float) {
        jumpBuffer = max(0f, jumpBuffer - dt)
        val wasSliding = slideTime > 0f
        slideTime = max(0f, slideTime - dt)
        // Une glissade dure tant qu'un obstacle haut est au-dessus ; on n'en déclenche jamais une toute seule.
        if (wasSliding && slideTime <= 0f && grounded && underOverhead()) slideTime = .02f
        val ground = groundHeight()
        supportY = ground
        if (grounded) {
            if (ground < playerY - .02f) {
                // Le dessus s'arrête : on tombe, avec un court instant pour sauter quand même.
                grounded = false; vy = 0f; coyote = COYOTE_TIME
            } else playerY = ground
            if (grounded && jumpBuffer > 0f) startJump()
        }
        if (!grounded) {
            coyote = max(0f, coyote - dt)
            vy -= GRAVITY * dt
            playerY += vy * dt
            if (playerY <= ground && vy <= 0f) {
                playerY = ground; vy = 0f; grounded = true
                onEvent?.invoke(Event.LAND, if (diving) 1 else 0)
                if (diving) { diving = false; slideTime = SLIDE_TIME }
                if (jumpBuffer > 0f) startJump()
            }
        }
    }

    private fun underOverhead(): Boolean {
        val px = laneX(laneF)
        for (e in entities) {
            if (!e.type.overhead || e.smashed) continue
            if (abs(px - e.x) < HALF_HAZARD + PLAYER_HALF - FORGIVE && e.z < .3f && e.z + e.length > -.3f) return true
        }
        return false
    }

    /** Faux si la partie vient de se terminer. */
    private fun moveEntities(travelled: Float, dt: Float): Boolean {
        val px = laneX(laneF)
        val height = playerHeight
        val bodyTop = playerY + height
        var removeAny = false
        for (e in entities) {
            if (e.dead) continue
            val previousZ = e.z
            e.z -= travelled
            val length = e.length
            if (e.z + length < -12f) { e.dead = true; removeAny = true; continue }
            if (e.smashed) continue
            if (e.type == EntityType.METEOR && !e.landed) {
                val impact = speed * METEOR_LAND
                val start = speed * METEOR_WARN
                e.drop = ((e.z - impact) / (start - impact)).coerceIn(0f, 1f)
                if (e.z > impact) continue
                e.landed = true; e.drop = 0f
                onEvent?.invoke(Event.METEOR, e.lane)
            }
            if (e.pickup) {
                if (collectPickup(e, px, bodyTop, dt)) { e.dead = true; removeAny = true }
                continue
            }
            val overlapsZ = e.z < .3f && e.z + length > -.3f
            val overlapsX = abs(px - e.x) < HALF_HAZARD + PLAYER_HALF - FORGIVE
            if (overlapsZ && overlapsX) {
                val top = if (e.type.solid) e.topAt(.3f - e.z) else e.top
                val bottom = e.baseY + e.type.yMin
                val blocked = if (e.type.solid) playerY < top - STEP else playerY < top && bodyTop > bottom
                if (blocked) {
                    if (boostTime > 0f) { smash(e, bonus = true); continue }
                    if (e.type.solid) {
                        // De côté, en changeant de voie : on rebondit sans choc. De face (ou déjà dedans) : un choc, et
                        // le conteneur reste debout, qui repousse le joueur vers la voie libre voisine. Sous protection,
                        // on est repoussé de la même façon mais sans pénalité : jamais laissé à l'intérieur.
                        val sideways = previousZ < .3f && abs(laneF - laneTarget) > .001f
                        if (sideways) { if (scrapeLock <= 0f) bounce(e, px); continue }
                        if (invincibleTime > 0f || scrapeLock > 0f) { pushOut(e); continue }
                        if (!hitSolid(e)) return false
                        continue
                    }
                    if (invincibleTime > 0f) continue
                    if (!hit(e, smashIt = true)) return false
                    continue
                }
                if (e.type.hazard) e.avoided = true
            }
            // Un obstacle franchi de la voie où l'on court, sans l'avoir touché : point de flux.
            if (e.type.hazard && e.avoided && !e.scored && e.z + length < -.3f) {
                e.scored = true
                hazardsCleared++
                gainChain(1)
                bonusScore += 40 * multiplier
                completed(missions.add(CosmoRunMissions.Kind.CLEARS, 1f))
                onEvent?.invoke(Event.CLEAR, e.lane)
            }
        }
        if (removeAny) entities.removeAll { it.dead }
        return true
    }

    /** Renvoie le joueur du côté d'où il vient, jamais vers le conteneur qu'il frôle. */
    private fun bounce(e: Entity, px: Float) {
        laneTarget = (e.lane + if (px < e.x) -1 else 1).coerceIn(0, NUM_LANES - 1)
        stableLane = laneTarget
        scrapeLock = .3f
        chain = 0; chainIdle = 0f
        onEvent?.invoke(Event.SCRAPE, laneTarget)
    }

    /** Détruit un objet. Seule la surrégime « fracasse » pour de bon : ça compte pour les missions. */
    private fun smash(e: Entity, bonus: Boolean) {
        e.smashed = true; e.dead = true
        // Ce qui reposait sur le toit d'un conteneur détruit disparaît avec lui.
        for (o in entities) if (!o.dead && o !== e && o.lane == e.lane && o.baseY > 0f &&
            o.z < e.z + e.length && o.z + o.length > e.z) o.dead = true
        if (bonus) {
            smashes++
            completed(missions.add(CosmoRunMissions.Kind.SMASHES, 1f))
            bonusScore += 60 * multiplier; gainChain(1)
        }
        onEvent?.invoke(Event.SMASH, e.lane)
    }

    /** Choc de face contre un conteneur : la pénalité habituelle, puis on est repoussé vers une voie libre. */
    private fun hitSolid(e: Entity): Boolean {
        val free = freeNeighbour(e)
        if (free < 0) return hit(e, smashIt = true)     // pris entre deux murs : on traverse
        val alive = hit(e, smashIt = false)
        if (alive) { laneTarget = free; stableLane = free; scrapeLock = .45f }
        return alive
    }

    /** Repousse le joueur d'un conteneur sans lui faire payer : la protection ne doit jamais l'enfermer dedans. */
    private fun pushOut(e: Entity) {
        val free = freeNeighbour(e)
        if (free < 0) { smash(e, bonus = false); return }
        if (laneTarget != free) { laneTarget = free; stableLane = free }
        scrapeLock = max(scrapeLock, .3f)
    }

    private fun freeNeighbour(e: Entity): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (lane in intArrayOf(e.lane - 1, e.lane + 1)) {
            if (lane !in 0 until NUM_LANES) continue
            // Libre sur tout ce qu'on parcourt pendant le changement de voie et la protection qui suit.
            val reach = speed * (LANE_TIME + .45f) + 1f
            if (entities.any { !it.dead && it.type.solid && it.lane == lane && it.z < reach && it.z + it.length > -1.2f }) continue
            val d = abs(lane - laneF) + abs(lane - 2) * .01f
            if (d < bestDistance) { bestDistance = d; best = lane }
        }
        return best
    }

    /** Faux si ce choc termine la partie. */
    private fun hit(e: Entity, smashIt: Boolean): Boolean {
        if (smashIt) smash(e, bonus = false)
        lastHitSource = e.source
        when {
            shield -> {
                shield = false; invincibleTime = SHIELD_GRACE
                onEvent?.invoke(Event.SHIELD_BREAK, e.lane)
            }
            wardenTime > 0f -> {
                crashType = e.type; isRunning = false; isGameOver = true
                lastDeathDistance = distance
                bestScore = maxOf(bestScore, score)
                onEvent?.invoke(Event.CRASH, e.lane)
                return false
            }
            else -> {
                wardenTime = WARDEN_TIME; invincibleTime = HIT_GRACE; slowTime = STUMBLE_SLOW
                chain = 0; chainIdle = 0f
                onEvent?.invoke(Event.STUMBLE, e.lane)
            }
        }
        return true
    }

    private fun collectPickup(e: Entity, px: Float, bodyTop: Float, dt: Float): Boolean {
        val reach = if (boostTime > 0f) 2 else if (magnetTime > 0f) 1 else 0
        if (e.type == EntityType.ATOM && reach > 0 && e.z in 0f..13f && abs(px - e.x) <= reach * LANE_WIDTH + .6f) {
            e.attracting = true
        }
        if (e.attracting) {
            // L'aimant déplace réellement le modèle vers le torse ; le crédit attend son arrivée.
            val dx = px - e.x
            val dy = playerY + .9f - (if (isSliding) .4f else 0f) - e.y
            val dz = -e.z
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            val step = 38f * dt
            val fraction = if (length > 0f) (step / length).coerceAtMost(1f) else 1f
            e.x += dx * fraction; e.y += dy * fraction; e.z += dz * fraction
            if (length <= step + .15f) { collect(e); return true }
            return false
        }
        if (abs(e.zCenter) > .45f || abs(px - e.x) > .7f) return false
        if (e.y < playerY - .25f || e.y > bodyTop + .25f) return false
        collect(e)
        return true
    }

    private fun gainChain(amount: Int) {
        val tier = multiplier
        chain += amount; maxChain = max(maxChain, chain); chainIdle = 0f
        completed(missions.report(CosmoRunMissions.Kind.CHAIN, chain.toFloat()))
        if (multiplier > tier) onEvent?.invoke(Event.TIER, multiplier)
    }

    private fun collect(e: Entity) {
        when (e.type) {
            EntityType.ATOM, EntityType.ATOM_HIGH -> {
                gainChain(1)
                atomsCollected++; bonusScore += 25 * multiplier
                completed(missions.add(CosmoRunMissions.Kind.ATOMS, 1f))
                onEvent?.invoke(Event.ATOM, chain)
            }
            EntityType.SHIELD -> { shield = true; onEvent?.invoke(Event.SHIELD, e.lane) }
            EntityType.MAGNET -> { magnetTime = MAGNET_TIME; onEvent?.invoke(Event.MAGNET, e.lane) }
            EntityType.BOOST -> { boostTime = BOOST_TIME; onEvent?.invoke(Event.BOOST, e.lane) }
            else -> Unit
        }
    }

    /** Appelé après chaque avancée de mission : primes et événements pour celles qui viennent de finir. */
    private fun completed(count: Int) {
        if (count == 0) return
        missionsThisRun += count
        bonusScore += 500 * count
        onEvent?.invoke(Event.MISSION, missions.rank)
        if (missions.justRankedUp) { bonusScore += 1000; onEvent?.invoke(Event.RANK, missions.rank) }
    }

    private fun spawnAhead() {
        val horizon = baseSpeed(distance) * HORIZON_SECONDS
        while (frontier - distance < horizon) placeNext()
    }

    private fun placeNext() {
        val rowLength = baseSpeed(frontier) * CosmoRunPatterns.ROW_TIME
        val choice = director.next(frontier)
        frontier += choice.gapRows * rowLength
        val origin = frontier - distance
        for (cell in choice.pattern.cells) {
            val z = origin + cell.row * rowLength
            val baseY = if (cell.roofed) CONTAINER_HEIGHT else 0f
            val length = if (cell.type.solid) cell.length * rowLength else cell.type.depth
            entities.add(Entity(cell.type, cell.lane, z, length, baseY, random.nextInt(4)).also { it.source = choice.pattern.name })
        }
        frontier += choice.pattern.rows * rowLength
    }
}
