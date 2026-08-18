package com.Atom2Universe.app.games.cosmorun

import kotlin.random.Random

/**
 * Logique pure du runner 3 voies (aucune dépendance Android, testable seule).
 *
 * Repère : l'axe Z est la distance (en "mètres") devant le joueur. Les entités
 * apparaissent au loin ([SPAWN_Z]) et défilent vers 0, le plan du joueur.
 * Le joueur ne bouge que latéralement (voie 0/1/2) et peut sauter.
 */
class CosmoRunGame {

    companion object {
        const val NUM_LANES = 3
        const val SPAWN_Z = 70f              // distance d'apparition des entités
        const val BASE_SPEED = 14f           // m/s au départ
        const val MAX_SPEED = 34f            // m/s plafond
        const val SPEED_PER_METER = 0.010f   // gain de vitesse par mètre parcouru
        const val LANE_SWITCH_DURATION = 0.10f // s pour changer de voie
        const val JUMP_DURATION = 0.62f      // s durée totale d'un saut
        // Fenêtre du saut (fraction 0..1) pendant laquelle on survole une barrière
        const val JUMP_CLEAR_MIN = 0.18f
        const val JUMP_CLEAR_MAX = 0.82f
        const val HIT_DEPTH = 1.4f           // demi-épaisseur de collision sur Z (m)
        const val ATOM_SCORE = 25
        const val ATOM_VARIANTS = 4          // nombre de sprites d'atomes différents
    }

    enum class EntityType { ASTEROID, BARRIER, ATOM }

    /** Une entité sur la piste. [lane] est ignoré pour les barrières (toute la largeur). */
    class Entity(val type: EntityType, val lane: Int, var z: Float, val variant: Int)

    val entities = ArrayList<Entity>()

    // ── État du joueur ─────────────────────────────────────────────────────────
    var laneTarget = 1; private set          // voie visée (0..2)
    var laneF = 1f; private set              // position latérale continue (0..2)
    var jumpProgress = -1f; private set      // -1 = au sol, sinon 0..1
    val isJumping get() = jumpProgress in 0f..1f

    // ── État de la partie ──────────────────────────────────────────────────────
    var distance = 0f; private set           // mètres parcourus
    var speed = BASE_SPEED; private set
    var atomsCollected = 0; private set
    var isRunning = false; private set
    var isGameOver = false; private set
    var bestScore = 0; private set

    val score: Int get() = distance.toInt() + atomsCollected * ATOM_SCORE

    // ── Génération ─────────────────────────────────────────────────────────────
    private var safeLane = 1                 // voie garantie libre (dérive de ±1 max par rangée)
    private var nextRowAt = 0f               // distance à laquelle spawner la prochaine rangée

    fun initBestScore(saved: Int) {
        if (saved > bestScore) bestScore = saved
    }

    fun start() {
        entities.clear()
        laneTarget = 1; laneF = 1f
        jumpProgress = -1f
        distance = 0f; speed = BASE_SPEED
        atomsCollected = 0
        safeLane = 1
        nextRowAt = 18f                      // petite zone tranquille au départ
        isGameOver = false
        isRunning = true
    }

    fun moveLeft()  { if (isRunning && laneTarget > 0) laneTarget-- }
    fun moveRight() { if (isRunning && laneTarget < NUM_LANES - 1) laneTarget++ }

    fun jump() {
        if (isRunning && !isJumping) jumpProgress = 0f
    }

    fun update(dt: Float) {
        if (!isRunning) return

        // Vitesse et distance
        speed = (BASE_SPEED + distance * SPEED_PER_METER).coerceAtMost(MAX_SPEED)
        distance += speed * dt

        // Glissement latéral vers la voie visée
        val laneDelta = dt / LANE_SWITCH_DURATION
        laneF = when {
            laneF < laneTarget -> (laneF + laneDelta).coerceAtMost(laneTarget.toFloat())
            laneF > laneTarget -> (laneF - laneDelta).coerceAtLeast(laneTarget.toFloat())
            else -> laneF
        }

        // Saut
        if (isJumping) {
            jumpProgress += dt / JUMP_DURATION
            if (jumpProgress >= 1f) jumpProgress = -1f
        }

        // Défilement des entités + collisions
        val step = speed * dt
        val it = entities.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.z -= step
            if (e.z < -4f) { it.remove(); continue }
            if (e.z > HIT_DEPTH || e.z < -HIT_DEPTH) continue
            when (e.type) {
                EntityType.ATOM -> if (onLane(e.lane) && !isJumping) {
                    atomsCollected++; it.remove()
                }
                EntityType.ASTEROID -> if (onLane(e.lane)) gameOver()
                EntityType.BARRIER -> if (!clearsBarrier()) gameOver()
            }
            if (isGameOver) return
        }

        // Nouvelle rangée d'obstacles quand on a assez avancé
        if (distance >= nextRowAt) spawnRow()
    }

    private fun onLane(lane: Int) = kotlin.math.abs(laneF - lane) < 0.5f

    private fun clearsBarrier() =
        isJumping && jumpProgress in JUMP_CLEAR_MIN..JUMP_CLEAR_MAX

    private fun gameOver() {
        isRunning = false
        isGameOver = true
        if (score > bestScore) bestScore = score
    }

    // ── Rangées d'obstacles ────────────────────────────────────────────────────
    //
    // Invariant d'équité : [safeLane] est toujours franchissable et ne dérive
    // que d'une voie à la fois, avec un espacement des rangées proportionnel à
    // la vitesse — le joueur a donc toujours le temps de rejoindre la voie sûre.

    private fun spawnRow() {
        // La voie sûre dérive de ±1
        safeLane = (safeLane + Random.nextInt(-1, 2)).coerceIn(0, NUM_LANES - 1)

        val r = Random.nextFloat()
        val hardness = (distance / 900f).coerceIn(0f, 1f) // monte en difficulté sur ~900 m
        when {
            // Barrière à sauter sur toute la largeur (dès ~150 m)
            distance > 150f && r < 0.16f + 0.08f * hardness -> {
                entities.add(Entity(EntityType.BARRIER, 0, SPAWN_Z, 0))
                // Petite ligne d'atomes juste derrière pour récompenser le saut
                if (Random.nextFloat() < 0.5f) atomLine(safeLane, SPAWN_Z + 6f, 3)
            }
            // Deux astéroïdes : seule la voie sûre passe
            r < 0.34f + 0.22f * hardness -> {
                for (lane in 0 until NUM_LANES) {
                    if (lane != safeLane) entities.add(asteroid(lane))
                }
            }
            // Un seul astéroïde sur une voie non sûre
            r < 0.78f -> {
                val lanes = (0 until NUM_LANES).filter { it != safeLane }
                entities.add(asteroid(lanes.random()))
                if (Random.nextFloat() < 0.35f) atomLine(safeLane, SPAWN_Z, 3)
            }
            // Rangée bonus : ligne d'atomes, aucun obstacle
            else -> atomLine(safeLane, SPAWN_Z, 5)
        }

        // Espacement : ~1 seconde de course au minimum, un peu aléatoire
        val gap = (speed * (0.95f + Random.nextFloat() * 0.55f)).coerceAtLeast(13f)
        nextRowAt = distance + gap
    }

    private fun asteroid(lane: Int) =
        Entity(EntityType.ASTEROID, lane, SPAWN_Z, Random.nextInt(3))

    private fun atomLine(lane: Int, startZ: Float, count: Int) {
        val variant = Random.nextInt(ATOM_VARIANTS)
        for (i in 0 until count) {
            entities.add(Entity(EntityType.ATOM, lane, startZ + i * 3.2f, variant))
        }
    }
}
