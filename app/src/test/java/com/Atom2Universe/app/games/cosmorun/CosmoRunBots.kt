package com.Atom2Universe.app.games.cosmorun

import com.Atom2Universe.app.games.cosmorun.CosmoRunGame.EntityType
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** Des joueurs simulés qui pilotent [CosmoRunGame] par ses seules entrées publiques. */
abstract class CosmoRunBot(protected val game: CosmoRunGame) {
    abstract fun step(dt: Float)
}

/** Ne fait rien : le plancher. */
class IdleBot(game: CosmoRunGame) : CosmoRunBot(game) {
    override fun step(dt: Float) = Unit
}

/** L'ancien exploit : suit les atomes de voie en voie, sans jamais sauter ni glisser. */
class AtomFollowerBot(game: CosmoRunGame) : CosmoRunBot(game) {
    override fun step(dt: Float) {
        val target = game.entities.filter { it.type == EntityType.ATOM && it.z > 1f }.minByOrNull { it.z }?.lane ?: return
        if (target < game.laneTarget) game.moveLeft() else if (target > game.laneTarget) game.moveRight()
    }
}

/**
 * Lit la piste et joue. [jitter] est l'écart-type (secondes) de son timing ; [miss] la part d'obstacles
 * dont il oublie de s'occuper. Avec (0, 0), c'est un joueur exact : s'il meurt, la piste est injuste.
 */
class ReaderBot(game: CosmoRunGame, private val random: Random, private val jitter: Float,
                private val miss: Float, private val greedy: Boolean = false,
                private val reaction: Float = 0f) : CosmoRunBot(game) {
    private class Plan(val offset: Float, val skip: Boolean) { var done = false }
    private val plans = IdentityHashMap<CosmoRunGame.Entity, Plan>()
    var roofSeconds = 0f
        private set

    private fun gauss() = (random.nextDouble() + random.nextDouble() + random.nextDouble() - 1.5).toFloat() * 2f

    private fun plan(e: CosmoRunGame.Entity) = plans.getOrPut(e) {
        Plan(gauss() * jitter, miss > 0f && random.nextFloat() < miss)
    }

    /** Temps avant que le bord avant d'un objet atteigne le joueur. */
    private fun eta(e: CosmoRunGame.Entity) = (e.z - .3f) / max(game.speed, 1f)

    override fun step(dt: Float) {
        clock += dt
        if (game.onRoof) roofSeconds += dt
        val lane = game.laneTarget
        // Sur un toit, le gourmand reste dans sa voie ; il ne replanifie qu'une fois retombé au sol.
        if (!(greedy && (game.onRoof || game.playerY > 1.6f))) laneChoice(dt)
        if (greedy && (game.onRoof || game.playerY > 1.6f)) crossRoofGap(lane)
        // Actes de saut / glissade pour les dangers de la voie où l'on court (niveau sol ou toit).
        val level = if (game.onRoof || game.playerY > 1.2f) CosmoRunGame.CONTAINER_HEIGHT else 0f
        for (e in game.entities) {
            if (e.dead || e.lane != lane || !e.type.hazard) continue
            if (abs(e.baseY - level) > .1f) continue
            val t = eta(e)
            if (t < -.1f || t > .6f) continue
            val p = plan(e)
            if (p.done || p.skip) continue
            when (e.type) {
                EntityType.HURDLE, EntityType.METEOR -> if (t <= .30f + p.offset) { game.jump(); p.done = true }
                else -> if (t <= .24f + p.offset) { game.slide(); p.done = true }
            }
        }
    }

    private var pendingDelay = -1f
    private var clock = 0f
    private var lastMove = -10f
    private val gapPlans = IdentityHashMap<CosmoRunGame.Entity, Plan>()

    /** Sur un toit qui finit juste avant un autre : sauter au bord pour passer de l'un à l'autre. */
    private fun crossRoofGap(lane: Int) {
        val current = game.entities.firstOrNull { !it.dead && it.lane == lane && it.type == EntityType.CONTAINER && it.z <= .3f && it.z + it.length > 0f } ?: return
        val end = current.z + current.length
        val next = game.entities.filter { !it.dead && it.lane == lane && it.type == EntityType.CONTAINER && it.z > end - .1f }
            .minByOrNull { it.z } ?: return
        if (next.z - end > game.speed * .5f) return                     // trop large : on tombera, tant pis
        val plan = gapPlans.getOrPut(current) { Plan(gauss() * jitter, miss > 0f && random.nextFloat() < miss) }
        if (plan.done || plan.skip) return
        if (end / game.speed <= .07f + plan.offset) { game.jump(); plan.done = true }
    }

    /**
     * Cherche, voie par voie et pas à pas sur 1,8 s, un chemin qui ne croise aucun conteneur, avec le
     * moins de changements de voie possible. C'est un lecteur exact de la piste, pas un solveur du jeu.
     */
    private fun laneChoice(dt: Float) {
        val start = game.laneTarget
        val steps = 45
        val slice = .04f
        val level = if (game.onRoof) CosmoRunGame.CONTAINER_HEIGHT else 0f
        val v = game.speed
        // occupied[l][k] : la voie l est prise par un conteneur (ou une rampe) à l'instant k * slice.
        val occupied = Array(CosmoRunGame.NUM_LANES) { BooleanArray(steps + 4) }
        for (e in game.entities) {
            if (e.dead || !e.type.solid) continue
            if (level > 0f && e.type == EntityType.CONTAINER) continue   // sur un toit, les conteneurs voisins ne gênent pas
            for (k in 0 until steps + 4) {
                val z = e.z - v * k * slice
                if (z < .55f && z + e.length > -.55f) occupied[e.lane][k] = true
            }
        }
        // Le gourmand vise la première rampe libre : sa voie (rampe et conteneur derrière) n'est plus un mur.
        var goal = -1
        if (greedy) {
            val ramp = game.entities.filter { !it.dead && it.type == EntityType.RAMP && it.z > 1f && it.z < v * 2.4f }
                .minByOrNull { it.z }
            if (ramp != null) {
                goal = ramp.lane
                for (e in game.entities) if (e.lane == goal && e.type.solid && e.z >= ramp.z - .1f) for (k in 0 until steps + 4) occupied[goal][k] = false
                for (e in game.entities) if (e.lane == goal && e.type.solid && e.z < ramp.z - .1f) for (k in 0 until steps + 4) {
                    val z = e.z - v * k * slice
                    if (z < .55f && z + e.length > -.55f) occupied[goal][k] = true
                }
            }
        }
        val inf = 100000
        val cost = Array(steps + 4) { IntArray(CosmoRunGame.NUM_LANES) { inf } }
        val first = Array(steps + 4) { IntArray(CosmoRunGame.NUM_LANES) }   // premier geste : -1, 0 (rester), +1
        if (!occupied[start][0]) cost[0][start] = 0 else return          // déjà touché : rien à planifier
        for (k in 0 until steps) for (l in 0 until CosmoRunGame.NUM_LANES) {
            val c = cost[k][l]
            if (c >= inf) continue
            if (!occupied[l][k + 1] && c < cost[k + 1][l]) { cost[k + 1][l] = c; first[k + 1][l] = if (k == 0) 0 else first[k][l] }
            for (d in intArrayOf(-1, 1)) {
                val l2 = l + d
                if (l2 !in 0 until CosmoRunGame.NUM_LANES) continue
                // Un changement de voie dure 3 pas : les deux voies doivent rester libres pendant ce temps.
                if ((1..3).any { occupied[l][minOf(k + it, steps + 3)] || occupied[l2][minOf(k + it, steps + 3)] }) continue
                val k2 = k + 3
                // Un changement coûte 100, un peu plus s'il est tardif : à nombre égal, on agit dès qu'on voit.
                val price = c + 100 + k
                if (k2 <= steps && price < cost[k2][l2]) { cost[k2][l2] = price; first[k2][l2] = if (k == 0) d else first[k][l] }
            }
        }
        var bestLane = -1
        var bestCost = inf
        if (goal >= 0 && cost[steps][goal] < inf) { bestLane = goal; bestCost = cost[steps][goal] }
        else for (l in 0 until CosmoRunGame.NUM_LANES) {
            val c = cost[steps][l]
            // À coût égal, la voie la plus proche du centre de ce qui vient : on garde de la marge des deux côtés.
            if (c < bestCost) { bestCost = c; bestLane = l }
        }
        if (bestLane < 0) return
        val move = first[steps][bestLane]
        if (move == 0) { pendingDelay = -1f; return }
        // Un joueur réel met un instant à réagir : le lecteur imparfait attend un peu avant de glisser.
        // Le temps de réaction ne se paie qu'au début d'une manœuvre : un geste enchaîné à la suite part vite.
        if (pendingDelay < 0f) pendingDelay = (if (clock - lastMove < .4f) 0f else reaction) + kotlin.math.abs(gauss()) * jitter * 2f
        pendingDelay -= dt
        if (pendingDelay > 0f) return
        pendingDelay = -1f
        lastMove = clock
        if (move > 0) game.moveRight() else game.moveLeft()
    }
}

/** Le résultat d'une partie simulée. */
class RunResult(val distance: Float, val seconds: Float, val cause: EntityType?, val stumbles: Int,
                val scrapes: Int, val atoms: Int, val maxChain: Int, val smashes: Int, val roofSeconds: Float,
                val hitSources: List<String> = emptyList(), val roofAtoms: Int = 0, val roofHits: Int = 0)

object CosmoRunSim {
    fun run(seed: Int, capMeters: Float = 6000f, roofs: Boolean = false, make: (CosmoRunGame, Random) -> CosmoRunBot): RunResult {
        val game = CosmoRunGame(Random(seed))
        game.roofsEnabled = roofs
        val bot = make(game, Random(seed * 31 + 7))
        var stumbles = 0
        var scrapes = 0
        val sources = ArrayList<String>()
        var roofAtoms = 0
        var roofHits = 0
        game.onEvent = { event, _ ->
            val up = game.onRoof || game.playerY > 1.5f
            if (event == CosmoRunGame.Event.ATOM && up) roofAtoms++
            if (event == CosmoRunGame.Event.STUMBLE && up) roofHits++
            if (event == CosmoRunGame.Event.STUMBLE || event == CosmoRunGame.Event.CRASH) sources.add(game.lastHitSource)
            if (event == CosmoRunGame.Event.STUMBLE) stumbles++
            if (event == CosmoRunGame.Event.SCRAPE) scrapes++
        }
        game.start()
        val dt = 1f / 60f
        var t = 0f
        while (game.isRunning && game.distance < capMeters && t < 1500f) {
            bot.step(dt)
            game.update(dt)
            t += dt
        }
        return RunResult(game.distance, t, if (game.isGameOver) game.crashType else null, stumbles, scrapes,
            game.atomsCollected, game.maxChain, game.smashes, game.roofSeconds, sources, roofAtoms, roofHits)
    }
}
