package com.Atom2Universe.app.games.caves

import android.content.Context
import com.Atom2Universe.app.games.caves.entity.EnemyManager
import com.Atom2Universe.app.games.caves.entity.FrontierResidents
import com.Atom2Universe.app.games.caves.entity.PassiveAnimals
import com.Atom2Universe.app.games.caves.entity.Projectile
import com.Atom2Universe.app.games.caves.node.EventBus
import com.Atom2Universe.app.games.caves.node.LootNode
import com.Atom2Universe.app.games.caves.world.CHUNK_SIZE
import com.Atom2Universe.app.games.caves.world.CaveWorldChunkStorage
import com.Atom2Universe.app.games.caves.world.Farming
import com.Atom2Universe.app.games.caves.world.FrontierLife
import com.Atom2Universe.app.games.caves.world.FrontierWorkshops
import com.Atom2Universe.app.games.caves.world.MapSource
import com.Atom2Universe.app.games.caves.world.ShowcaseMap
import com.Atom2Universe.app.games.caves.world.World
import com.Atom2Universe.app.games.caves.world.WorldSource

/**
 * La vie du monde de Cave World, sans rien dessiner.
 *
 * Tout ce qui vit ici continue d'exister quel que soit le joueur qui regarde : les blocs, l'eau,
 * les feuilles qui tombent, l'heure, la ferme, les ateliers, les animaux, les habitants et les
 * monstres. [CaveRenderer] ne fait que lire cet état pour le dessiner.
 *
 * C'est la première marche vers le multijoueur : un jour, un seul appareil (l'hôte) fera avancer
 * cette simulation, et les autres ne feront que l'afficher. Rien ici ne doit donc toucher à
 * OpenGL, à la caméra ou à l'interface ; ce dont la simulation a besoin du reste du jeu passe
 * par les crochets ci-dessous, que le renderer branche.
 *
 * Toutes les méthodes sont appelées sur le thread GL, comme avant.
 */
internal class CaveSimulation(
    context: Context,
    val worldSeed: Long,
    worldId: String?,
    savedState: CaveRenderer.SavedState?,
    val worldSource: WorldSource?,
    simulationDistance: Int,
) {
    val storage = worldId?.let {
        CaveWorldChunkStorage(java.io.File(context.filesDir, "cave_worlds/$it"))
    }
    val world = World(seed = worldSeed, storage = storage, source = worldSource)
        .apply { setSimulationDistance(simulationDistance) }

    // ── Crochets branchés par le renderer ─────────────────────────────────────

    /** Un bloc a changé tout seul (ferme, atelier) : son maillage doit être refait. */
    var blockChanged: (x: Int, y: Int, z: Int) -> Unit = { _, _, _ -> }
    /** Le chunk de cette clé est-il déjà affiché ? Sa lumière n'est fiable qu'à partir de là. */
    var chunkShown: (key: Long) -> Boolean = { true }
    /** Toutes les 10 s de jeu : le moment d'écrire une sauvegarde de secours. */
    var checkpointDue: () -> Unit = {}

    // ── Les joueurs ───────────────────────────────────────────────────────────

    /**
     * Les joueurs présents dans ce monde. Un seul pour l'instant (celui de l'appareil), mais
     * rien ici ne doit supposer qu'il est seul : c'est la liste que le multijoueur remplirait.
     */
    val players = ArrayList<CavePlayer>(1)

    // ── Ce qui vit dans le monde ──────────────────────────────────────────────

    val eventBus = EventBus()
    val lootNode = LootNode(eventBus)
    val enemyManager = EnemyManager(world, worldSeed).apply {
        spawnManager.restoreDefeatedSiteBosses(savedState?.defeatedSiteBosses.orEmpty())
    }
    val passiveAnimals = PassiveAnimals(world, worldSeed).apply { restore(savedState?.passiveAnimals ?: "[]") }
    val frontierLife = FrontierLife(savedState?.frontierLife ?: "{}")
    val residents by lazy { FrontierResidents(world, frontierLife.residents) }

    /** La carte vitrine du mode Assaut montre les pièces mécaniques en marche. */
    val exhibition get() = (worldSource as? MapSource)?.isShowcase == true
    val workshops by lazy { FrontierWorkshops(world, worldSeed).also {
        it.restore(savedState?.workshops ?: "{}")
        it.exhibition = exhibition
        (worldSource as? MapSource)?.takeIf { s -> s.isShowcase }?.let { s ->
            for (mill in ShowcaseMap.windmills())
                it.addWindmill(FrontierWorkshops.Pos(mill.x + s.originX, mill.y + s.originY, mill.z + s.originZ), mill.axis, mill.sails)
        }
    } }
    /** Flèches, balles, cailloux et venin en vol, ou munitions plantées dans un mur. */
    val projectiles = ArrayList<Projectile>(64)
    val farming by lazy { Farming(world, { x, y, z -> blockChanged(x, y, z) }, ::ecologicalLight) }

    // ── Heure du jour ─────────────────────────────────────────────────────────

    /** Temps de jeu écoulé en ms (ne progresse pas en pause). Un jour complet dure 1 800 000 ms. */
    var gameTimeMs: Long = 0L

    /** Position dans la journée : 0 = minuit, 0,25 = 6 h, 0,5 = midi, 0,75 = 18 h. */
    fun dayFraction(): Float {
        val gf = (gameTimeMs % 1_800_000L) / 1_800_000f
        return if (gf < 0.6667f) {
            0.25f + gf * 0.75f
        } else {
            (0.75f + (gf - 0.6667f) * 1.5f) % 1f
        }
    }

    /** Lumière du jour en ce moment, de 0,08 (nuit) à 1 (plein jour). */
    fun daylight(): Float = ambientFor(dayFraction())

    // ── Avancer d'une image ───────────────────────────────────────────────────

    private var waterTickAccum = 0f
    private var leafTickAccum = 0f
    private var gravityTickAccum = 0f
    private var checkpointTimer = 0f

    /**
     * Fait vivre les blocs et le temps pendant [dt] secondes (déjà plafonné) ; [rawDt] est
     * l'intervalle réel, qui sert à la ferme et aux ateliers. N'est jamais appelé en pause.
     *
     * [leafDrop] reçoit les feuilles qui tombent d'un arbre coupé (null = elles disparaissent).
     */
    fun tickWorld(dt: Float, rawDt: Float, allowsWorldEdits: Boolean, timeFlows: Boolean,
                  leafDrop: ((Short) -> Unit)?) {
        // Heure figée par le mode (Assaut : midi) ; sinon le jour et la nuit tournent.
        if (allowsWorldEdits && rawDt < 1f) {
            frontierLife.advance((rawDt * 1000f).toLong())
            farming.advance((rawDt * 1000f).toLong())
            workshops.sunUp = daylight() > .6f
            workshops.advance(rawDt)
            for (p in workshops.takeChanged()) blockChanged(p.x, p.y, p.z)
            workshops.feedAnimals(passiveAnimals, rawDt)
            checkpointTimer += rawDt
            if (checkpointTimer >= 10f) { checkpointTimer = 0f; checkpointDue() }
        }
        if (timeFlows) gameTimeMs += (dt * 1_000f).toLong()

        waterTickAccum += dt
        if (waterTickAccum >= 0.1f) {
            waterTickAccum %= 0.1f
            world.tickWater()
        }
        leafTickAccum += dt
        if (leafTickAccum >= 0.25f) {
            leafTickAccum %= 0.25f
            if (allowsWorldEdits) world.tickLeaves { leafDrop?.invoke(it) }
        }
        gravityTickAccum += dt
        if (gravityTickAccum >= 0.1f) {
            gravityTickAccum = 0f
            world.tickFalling(64)
        }
    }

    /**
     * Fait vivre les animaux, les habitants et les machines autour des [players] (les animaux
     * suivent la nourriture qu'un joueur tient en main). Seul ce qui est à moins de
     * [simulationChunks] chunks reste actif. N'est jamais appelé en pause.
     *
     * Ces systèmes ne savent encore suivre qu'un joueur : ils suivent le premier de la liste.
     */
    fun tickLiving(dt: Float, simulationChunks: Int) {
        val player = players.firstOrNull() ?: return
        val px = player.x; val py = player.y; val pz = player.z
        val held = player.heldItem
        val radius = simulationChunks * CHUNK_SIZE.toDouble()
        if (worldSource == null) {
            passiveAnimals.simulationRadius = radius; residents.simulationRadius = radius
            enemyManager.despawnChunks = simulationChunks
            workshops.simulationRadius = radius
            passiveAnimals.update(dt, px, py, pz, held)
            residents.update(dt, px, py, pz, daylight() < .4f)
            workshops.animate(dt, px, py, pz) { x, y, z -> ecologicalLight(x, y, z) / 15f }
        } else if (exhibition) {
            workshops.simulationRadius = radius
            workshops.animate(dt, px, py, pz) { x, y, z -> ecologicalLight(x, y, z) / 15f }
        }
    }

    // ── Lumière vue par les êtres vivants ─────────────────────────────────────

    /** Attend que la lumière d'un chunk caché soit posée avant d'y faire apparaître des monstres. */
    fun spawnLight(x: Int, y: Int, z: Int): Int {
        val c = world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16))
        return if (c?.ecologyLightReady == true) ecologicalLight(x, y, z) else 15
    }

    /** Lumière d'un bloc (0 à 15) : le ciel selon l'heure, ou une torche, la plus forte des deux. */
    fun ecologicalLight(x: Int, y: Int, z: Int): Int {
        val chunk = world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16)) ?: return 15
        if (!chunkShown(world.chunkKey(chunk.cx, chunk.cy, chunk.cz))) return 15
        val sky = chunk.skyAt(Math.floorMod(x, 16), Math.floorMod(y, 16), Math.floorMod(z, 16))
        val index = Math.floorMod(x, 16) + Math.floorMod(y, 16) * 16 + Math.floorMod(z, 16) * 256
        val blockLight = (chunk.light[index].toInt() ushr 4) and 15
        return maxOf((sky * daylight()).toInt(), blockLight).coerceIn(0, 15)
    }

    companion object {
        private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

        /** Lumière ambiante au moment [t] de la journée (voir [dayFraction]). */
        fun ambientFor(t: Float): Float = when {
            t < 0.208f -> 0.08f
            t < 0.313f -> lerpF(0.08f, 1.00f, (t - 0.208f) / 0.105f)
            t < 0.708f -> 1.00f
            t < 0.833f -> lerpF(1.00f, 0.08f, (t - 0.708f) / 0.125f)
            else       -> 0.08f
        }
    }
}
