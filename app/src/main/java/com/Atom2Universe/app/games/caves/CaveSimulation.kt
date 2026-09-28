package com.Atom2Universe.app.games.caves

import android.content.Context
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyManager
import com.Atom2Universe.app.games.caves.entity.FrontierResidents
import com.Atom2Universe.app.games.caves.entity.PassiveAnimals
import com.Atom2Universe.app.games.caves.entity.Projectile
import com.Atom2Universe.app.games.caves.entity.ProjectileKind
import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.CombatNode
import com.Atom2Universe.app.games.caves.render.MobModels
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.PartialBlockModel
import com.Atom2Universe.app.games.caves.world.StairConnections
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
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

    // ── Projectiles ───────────────────────────────────────────────────────────

    /**
     * Ce que le vol des projectiles laisse au reste du jeu : les effets à l'écran, les sons, les
     * règles du mode et le butin. Le renderer le branche ; la simulation ne fait que l'appeler.
     */
    interface ProjectileRules {
        /** Multiplicateur de dégâts d'un tir à la tête (voir GameMode.headshotMultiplier). */
        val headshotMultiplier: Float
        /** Les animaux qu'un tir peut blesser (aucun en Assaut, ni quand le joueur est mort). */
        val huntableAnimals: List<Enemy>
        /** Un projectile frappe quelque chose en (x, y, z) : des éclats. */
        fun impact(x: Double, y: Double, z: Double)
        /** Une balle claque contre un mur en (x, y, z). */
        fun bulletImpact(x: Double, y: Double, z: Double)
        /** Une balle ennemie touche [player]. */
        fun playerShot(player: CavePlayer, p: Projectile)
        /** Un tir touche [enemy] (déjà blessé), à la tête si [headshot]. */
        fun enemyHit(enemy: Enemy, headshot: Boolean)
        /** Un tir touche un animal qu'on peut chasser. */
        fun animalHit(animal: Enemy, damage: Int)
        /** Une munition vient de se planter, ou d'être ramassée par l'un des [collectors]. */
        fun ammoChanged(collectors: Set<CavePlayer>)
    }
    var projectileRules: ProjectileRules? = null

    private val decorSource get() = worldSource as? MapSource

    /** Le bloc en (x, y, z), ou de l'air tant que son chunk n'est pas encore construit. */
    private fun loadedBlockAt(x: Int, y: Int, z: Int): Short {
        val cx = Math.floorDiv(x, CHUNK_SIZE); val cy = Math.floorDiv(y, CHUNK_SIZE); val cz = Math.floorDiv(z, CHUNK_SIZE)
        val chunk = world.getChunk(cx, cy, cz) ?: return AIR
        if (!chunk.generated) return AIR
        return chunk.blockAt(x - cx * CHUNK_SIZE, y - cy * CHUNK_SIZE, z - cz * CHUNK_SIZE)
    }

    /** Un projectile en (x, y, z) est-il dans quelque chose de dur ? Tient compte des demi-blocs et du décor. */
    fun projectileSolid(x: Double, y: Double, z: Double): Boolean {
        if (decorSource?.decorHitsSegment(x, y, z, x, y, z) == true) return true
        val bx = floor(x).toInt(); val by = floor(y).toInt(); val bz = floor(z).toInt()
        val block = loadedBlockAt(bx, by, bz)
        if (block == AIR || BlockRegistry.isDecoration(block) || BlockRegistry.isWater(block)) return false
        val def = BlockRegistry.get(block) ?: return true
        if (!def.partial) return true
        val stairs = StairConnections.maskAt(bx, by, bz, ::loadedBlockAt, world::metaAt)
        return PartialBlockModel.boxes(def, world.metaAt(bx, by, bz), stairs).any {
            x - bx >= it.x && x - bx <= it.x + it.width &&
                y - by >= it.y && y - by <= it.y + it.height &&
                z - bz >= it.z && z - bz <= it.z + it.depth
        }
    }

    /** Le joueur dont la balle traverse le corps, ou null. Cylindre qui va des pieds au sommet du crâne. */
    private fun playerHitBy(p: Projectile): CavePlayer? = players.firstOrNull { who ->
        if (who.mode != PlayerMode.WALK) return@firstOrNull false
        val dx = p.x - who.x; val dz = p.z - who.z
        if (dx * dx + dz * dz > PLAYER_HIT_RADIUS * PLAYER_HIT_RADIUS) return@firstOrNull false
        p.y >= who.y - 1.62 && p.y <= who.y + who.physics.heightAbove
    }

    /** Le joueur qui peut ramasser cette munition plantée (assez près, rien entre les deux), ou null. */
    private fun recoveredBy(p: Projectile): CavePlayer? = players.firstOrNull { who ->
        val dx=p.x-who.x; val dy=p.y-(who.y-.5); val dz=p.z-who.z
        if (dx*dx+dy*dy+dz*dz > 2.2*2.2) return@firstOrNull false
        val steps=ceil(sqrt(dx*dx+dy*dy+dz*dz)/.15).toInt().coerceAtLeast(1)
        (1..steps).none { i ->
            val t=i.toDouble()/steps
            projectileSolid(who.x+dx*t,who.y-.5+dy*t,who.z+dz*t)
        }
    }

    /** Fait voler les projectiles pendant [dt] secondes : murs, joueurs, monstres, animaux. */
    fun tickProjectiles(dt: Float) {
        val rules = projectileRules ?: return
        var ammoChanged = false
        val collectors = HashSet<CavePlayer>(1)
        val iter=projectiles.iterator()
        while(iter.hasNext()) {
            val p=iter.next()
            p.age+=dt
            if(p.stuck) {
                val who = if(p.age>.4f && p.ammoId != null) recoveredBy(p) else null
                if(who != null) {
                    who.inventory[p.ammoId!!]=(who.inventory[p.ammoId] ?: 0)+1
                    collectors += who; ammoChanged=true; iter.remove()
                }
                continue
            }
            // Sous-pas de 15 cm : même une balle rapide ne saute pas une paroi voxel.
            val steps=p.substeps(dt)
            val step=dt/steps
            for(i in 0 until steps) {
                val ox=p.x; val oy=p.y; val oz=p.z
                p.advance(step)
                if(p.kind != ProjectileKind.LEGACY && (projectileSolid(p.x,p.y,p.z) ||
                    decorSource?.decorHitsSegment(ox,oy,oz,p.x,p.y,p.z) == true)) {
                    rules.impact(p.x,p.y,p.z)
                    if (p.kind == ProjectileKind.BULLET || p.kind == ProjectileKind.PELLET) rules.bulletImpact(p.x,p.y,p.z)
                    if(p.ammoId != null && (p.kind==ProjectileKind.ARROW || p.kind==ProjectileKind.BOLT)) {
                        p.x=ox;p.y=oy;p.z=oz;p.stuck=true;ammoChanged=true
                    } else { iter.remove() }
                    break
                }
                if (p.fromEnemy) {
                    // Balle de soldat : elle ne touche que les joueurs (pas de tir ami entre soldats).
                    val shot = playerHitBy(p)
                    if (shot != null) {
                        rules.impact(p.x,p.y,p.z)
                        rules.playerShot(shot, p)
                        iter.remove();break
                    }
                    if(p.travelDist>p.maxRange) { iter.remove();break }
                    continue
                }
                val hit=enemyManager.enemies.find { e ->
                    val radius=e.def.radius.toDouble()+.5
                    e.hp>0 && (p.x-e.x).pow(2)+(p.z-e.z).pow(2)<radius*radius &&
                        p.y>=e.y-.25 && p.y<=e.y+MobModels.bodyHeightWorld(e.def.model,e.baseScale)+.25
                } ?: rules.huntableAnimals.find { a ->
                    val radius=a.def.radius * (if(a.young) .72 else 1.0)
                    a.hp>0 && (p.x-a.x).pow(2)+(p.z-a.z).pow(2)<radius*radius &&
                        p.y>=a.y && p.y<=a.y+passiveAnimals.height(a)
                }
                if(hit!=null) {
                    if(p.kind!=ProjectileKind.LEGACY) rules.impact(p.x,p.y,p.z)
                    if(hit.def.behavior=="passive") {
                        rules.animalHit(hit,p.damage)
                        iter.remove();break
                    }
                    // Tête : le haut du corps, au-dessus de MobModels.HEAD_START. Le mode décide ce
                    // qu'elle vaut (la survie ne change rien, l'Assaut double les dégâts).
                    val headshot = p.y >= hit.y + MobModels.bodyHeightWorld(hit.def.model, hit.baseScale) * MobModels.HEAD_START
                    val damage = if (headshot) (p.damage * rules.headshotMultiplier).roundToInt() else p.damage
                    if(p.isPlayerWeapon) applyWeaponHit(hit,damage,p.stats,Random.Default,p.owner)
                    else enemyManager.damageEnemy(hit,damage)
                    rules.enemyHit(hit, headshot)
                    iter.remove();break
                }
                if(p.travelDist>p.maxRange) { iter.remove();break }
            }
        }
        // Limite mémoire pour les munitions plantées dans une session très longue.
        var excess=projectiles.count { it.stuck }-256
        if(excess>0) projectiles.removeAll { it.stuck && excess-- > 0 }
        if(ammoChanged) rules.ammoChanged(collectors)
    }

    /**
     * Un coup d'arme touche [enemy] : critique, vol de vie pour [owner], exécution et effets
     * (saignement, poison, feu, gel, électricité), selon les [stats] de l'arme.
     */
    fun applyWeaponHit(enemy: Enemy, baseDamage: Int, stats: Map<String, Int>, rng: Random,
                       owner: com.Atom2Universe.app.games.caves.entity.EnemyTarget?) {
        var dmg = baseDamage

        // Coup critique — multiplie le coup ET les DoTs
        val critChance = stats["crit_chance"] ?: 0
        val isCrit = critChance > 0 && rng.nextInt(100) < critChance
        val critMult = if (isCrit) 2.0f + (stats["crit_dmg"] ?: 0) / 100f else 1.0f
        if (isCrit) dmg = (dmg * critMult).toInt()

        CombatNode.damageEnemy(enemy, dmg)
        enemyManager.knockbackFromPlayer(enemy)

        // Vol de vie (sur les dégâts du coup, post-crit) : soigne celui qui a tiré.
        val lifeSteal = stats["life_steal"] ?: 0
        if (lifeSteal > 0) {
            val heal = (dmg * lifeSteal / 100f).toInt().coerceAtLeast(1)
            (owner?.node ?: enemyManager.player)?.applyHeal(heal)
        }

        // Exécution (ennemi < 20% HP)
        val execute = stats["execute"] ?: 0
        if (execute > 0 && enemy.hp > 0) {
            val threshold = (enemy.maxHp * 0.20f).toInt()
            if (enemy.hp <= threshold && rng.nextInt(100) < execute) {
                enemy.hp = 0
                return
            }
        }

        // Résistances élémentaires du mob : multiplient à la fois la chance de proc
        // et l'ampleur de l'effet (dégâts ou durée). 0 = immunisé, >1 = vulnérable.
        val res = enemy.def.resistances

        // Saignement : chaque proc ajoute à une jauge (façon Dark Souls) ; pleine (100),
        // elle explose en un gros pourcentage des PV max puis retombe à zéro. Elle
        // redescend seule si le mob n'est pas retouché (voir EnemyManager).
        val bleedRes = res["bleed"] ?: 1f
        val bleedChance = ((stats["bleed_chance"] ?: 0) * bleedRes).toInt()
        if (bleedRes > 0f && bleedChance > 0 && rng.nextInt(100) < bleedChance) {
            enemy.bleedBuildup = (enemy.bleedBuildup + 25f * bleedRes).coerceAtMost(100f)
            enemy.bleedDecayGrace = 2.5f
        }

        // Poison : dégâts sur la durée, chance et ampleur réduites si le mob y résiste
        val poisonRes = res["poison"] ?: 1f
        val poisonChance = ((stats["poison_chance"] ?: 0) * poisonRes).toInt()
        if (poisonRes > 0f && poisonChance > 0 && rng.nextInt(100) < poisonChance) {
            enemy.poisonDamage = (baseDamage * 0.10f * critMult * poisonRes).toInt().coerceAtLeast(1)
            enemy.poisonTimer = 4f
            enemy.poisonTickTimer = 0.8f
        }

        // Feu : dégâts rapides sur la durée, inefficace contre les mobs résistants au feu
        val fireRes = res["fire"] ?: 1f
        val fireChance = ((stats["fire_chance"] ?: 0) * fireRes).toInt()
        if (fireRes > 0f && fireChance > 0 && rng.nextInt(100) < fireChance) {
            enemy.fireDamage = (baseDamage * 0.20f * critMult * fireRes).toInt().coerceAtLeast(1)
            enemy.fireTimer = 2f
            enemy.fireTickTimer = 0.3f
        }

        // Gel : immobilisation totale, durée modulée par la résistance/vulnérabilité au froid
        val freezeRes = res["ice"] ?: 1f
        val freezeChance = ((stats["freeze_chance"] ?: 0) * freezeRes).toInt()
        if (freezeRes > 0f && freezeChance > 0 && rng.nextInt(100) < freezeChance) {
            enemy.freezeTimer = 1.5f * freezeRes
        }

        // Électrique : le mob "bugue" et attaque ses propres alliés un instant
        val electricRes = res["electric"] ?: 1f
        val electricChance = ((stats["electric_chance"] ?: 0) * electricRes).toInt()
        if (electricRes > 0f && electricChance > 0 && rng.nextInt(100) < electricChance) {
            enemy.confusionTimer = 1.5f * electricRes
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
        /** Rayon du corps d'un joueur pour les balles. */
        private const val PLAYER_HIT_RADIUS = 0.4

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
