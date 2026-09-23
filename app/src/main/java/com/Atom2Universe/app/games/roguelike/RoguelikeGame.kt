package com.Atom2Universe.app.games.roguelike

import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

// ─── Tuiles ────────────────────────────────────────────────────────────────────
enum class TileType { WALL, FLOOR, STAIRS_DOWN }

// ─── Position ──────────────────────────────────────────────────────────────────
data class Pos(val x: Int, val y: Int) {
    fun chebyshev(other: Pos) = max(abs(x - other.x), abs(y - other.y))
}

// ─── Objets au sol ─────────────────────────────────────────────────────────────
enum class ItemType(val spriteRow: Int, val spriteCol: Int) {
    GOLD  (9, 15),
    /** L'icône réelle est celle de la relique posée ([Item.relic]). */
    RELIC (113, 6),
}

data class Item(val type: ItemType, val pos: Pos, val relic: Relic? = null)

// ─── Monstres sur la carte ─────────────────────────────────────────────────────
enum class PackState { IDLE, CHASING }

/** Un monstre visible sur la carte = un groupe de 1 à 3 ennemis en combat. */
class MonsterPack(val types: List<MonsterType>, var pos: Pos) {
    val home = pos
    internal var patrol: List<Pos> = emptyList()
    internal var patrolIndex = 1
    var state = PackState.IDLE
    var lostTurns = 0
    var alive = true
}

// ─── Niveau ────────────────────────────────────────────────────────────────────
class DungeonLevel(val w: Int, val h: Int, val floor: Int) {
    val tiles    = Array(h) { Array(w) { TileType.WALL } }
    val packs    = mutableListOf<MonsterPack>()
    val items    = mutableListOf<Item>()
    val visible  = Array(h) { BooleanArray(w) }
    val explored = Array(h) { BooleanArray(w) }
    var theme    = DungeonTheme.forFloor(floor)
        internal set
    internal var format = DungeonFormat.MICRO
    internal var targetPacks = 3
    internal val districts = Array(h) { IntArray(w) }
    internal var sites: List<MapSite> = emptyList()
    internal var quietCells: Set<Pos> = emptySet()
    internal var campDistances = Array(h) { IntArray(w) { -1 } }
    var mausoleums: List<Pos> = emptyList()
    /** La forge de l'étage, s'il y en a une ([DungeonForge]). */
    var forge: Pos? = null
        internal set
    internal var passages: Map<Pos, MapPassage> = emptyMap()
    internal var waterways: Map<Pos, MapWaterway> = emptyMap()
    internal var scenery: Map<Pos, MapScenery> = emptyMap()
    val themes = Array(h) { Array(w) { theme } }
    fun themeAt(x: Int, y: Int): DungeonTheme = if (inBounds(x, y)) themes[y][x] else theme
    fun backdropAt(pos: Pos) = if (scenery[pos]?.kind == SceneryKind.CHAPEL) DungeonBackdrop.CRYPT else themeAt(pos.x, pos.y).backdropAt(pos.x, pos.y)
    var start    = Pos(1, 1)

    fun inBounds(x: Int, y: Int)  = x in 0 until w && y in 0 until h
    fun walkable(x: Int, y: Int)  = inBounds(x, y) && tiles[y][x] != TileType.WALL

    /**
     * Un pas en diagonale demande au moins un côté ouvert : on ne se faufile pas entre
     * deux coins de mur (sinon le labyrinthe fuit par ses angles).
     */
    fun canStep(from: Pos, dx: Int, dy: Int): Boolean {
        if (!walkable(from.x + dx, from.y + dy)) return false
        return dx == 0 || dy == 0 || walkable(from.x + dx, from.y) || walkable(from.x, from.y + dy)
    }
    fun packAt(x: Int, y: Int)    = packs.find { it.alive && it.pos.x == x && it.pos.y == y }
}


// ─── Journal ───────────────────────────────────────────────────────────────────
/** Clé de ressource + arguments (nombres ou enums Labeled) — résolue en texte uniquement à l'affichage. */
data class LogEntry(@StringRes val keyRes: Int, val args: List<Any> = emptyList())

/** Ce qu'on affiche après une mort : où on est tombé, combien d'or est perdu. */
data class DeathReport(val floor: Int, val goldLost: Int,
    val checkpointFloor: Int = RoguelikeGame.checkpointFloor(floor))

// ─── Moteur de la carte ────────────────────────────────────────────────────────
/**
 * L'exploration : on se déplace, les monstres patrouillent et nous poursuivent s'ils
 * nous voient. Un contact ouvre un [Combat]. On ne peut se reposer que si personne ne
 * nous poursuit. Voir DONJON.md.
 */
class RoguelikeGame(
    val hero: Hero = Hero.starter(),
    startFloor: Int = 1,
    private val rng: Random = Random,
    levelSeed: Long = Random.nextLong(),
    /**
     * Un checkpoint automatique tous les N étages (11, 21, 31… pour N = 10) : la mort ramène au
     * dernier atteint si le joueur le choisit. 0 reste disponible pour les simulations.
     */
    private val checkpointEvery: Int = CHECKPOINT_INTERVAL,
    /**
     * La mort ramène à ce nombre d'étages **avant** le checkpoint (sans descendre sous l'étage [CHECKPOINT]) : on refait
     * un peu de chemin, donc on farme un peu. 0 : au checkpoint même. Sert aux simulations, en attendant que le jeu laisse
     * farmer n'importe quelle tranche de 25 étages déjà terminée (voir DONJON.md).
     */
    private val deathRetreat: Int = 0,
) {
    var onCombatStart:  (() -> Unit)?             = null
    var onFloorChanged: ((floor: Int) -> Unit)?   = null

    companion object {
        const val FOV_RADIUS     = 8
        /** Distance à laquelle un monstre nous repère (en vue directe). */
        const val SIGHT          = 6
        /** Tours sans nous voir avant qu'un poursuivant abandonne. */
        const val CHASE_MEMORY   = 5
        /** Un poursuivant à cette distance à la fin d'un combat enchaîne directement. */
        const val CHAIN_DISTANCE = 2
        const val DEATH_GOLD_LOSS = 0.30f
        const val CHECKPOINT     = 1
        const val CHECKPOINT_INTERVAL = 10

        fun checkpointFloor(floor: Int, interval: Int = CHECKPOINT_INTERVAL): Int =
            if (interval <= 0) CHECKPOINT else CHECKPOINT + (floor.coerceAtLeast(CHECKPOINT) - CHECKPOINT) / interval * interval
        /**
         * Les reliques ne se trouvent **qu'en explorant** (décidé le 18/09/2026) : jamais sur
         * un monstre. Une relique attend au bout du cul-de-sac le plus éloigné du départ, sur
         * [RELIC_CHANCE] des étages — c'est ce qui donne envie de fouiller la carte. Elle est
         * tirée parmi celles qu'on n'a pas encore : la Boule de feu n'est pas forcément la
         * première. Rare, mais on garde tout en mourant et les étages se refont : on finit par
         * tout trouver.
         *
         * La toute première est garantie à l'étage [FIRST_RELIC_FLOOR], pour découvrir les sorts.
         */
        const val RELIC_CHANCE = 0.15f
        const val FIRST_RELIC_FLOOR = 2

        /**
         * Sur les premiers étages, au moins un équipement par étage : si la première victoire
         * de l'étage ne lâche rien, elle donne une pièce ordinaire (jamais une pièce de set).
         */
        const val GUARANTEED_GEAR_FLOORS = 5

        fun inventoryFromJson(j: JSONObject): RoguelikeGame {
            val hero = Hero().apply {
                gold    = j.getInt("gold")
                deepestFloor = j.optInt("deepestFloor", j.getInt("floor"))
                val eq  = j.getJSONObject("equipped")
                for (slotName in eq.keys())
                    equipped[EquipSlot.valueOf(slotName)] = SaveManager.equipFromJson(eq.getJSONObject(slotName))
                val bagJson = j.getJSONArray("bag")
                for (i in 0 until bagJson.length()) bag += SaveManager.equipFromJson(bagJson.getJSONObject(i))
                nextLootId = j.getLong("nextLootId")
                floor = j.getInt("floor")
                hp = HitPointBalance.restore(j.getInt("hp"), j.optDouble("hpMultiplier", 1.0), floor).coerceIn(1, maxHp)
                specialCooldown = j.optInt("specialCooldown", 0)
                val relicsJson = j.optJSONArray("relics")
                if (relicsJson == null) {
                    // Sauvegarde d'avant les reliques : on y avait toujours la Boule de feu
                    addRelic(Relic.FIREBALL)
                } else {
                    for (i in 0 until relicsJson.length())
                        Relic.fromSavedName(relicsJson.getString(i))?.let { relics += it }
                    j.optJSONObject("relicCooldowns")?.let { cds ->
                        for (name in cds.keys()) relics.firstOrNull { it.name == name }?.let { relicCooldowns[it] = cds.getInt(name) }
                    }
                    val slotsJson = j.optJSONArray("relicSlots")
                    for (i in 0 until minOf(slotsJson?.length() ?: 0, Hero.RELIC_SLOTS)) {
                        val name = slotsJson!!.optString(i, "")
                        relicSlots[i] = relics.firstOrNull { it.name == name }
                    }
                    j.optJSONArray("resonances")?.let { arr ->
                        for (i in 0 until arr.length())
                            runCatching { Resonance.valueOf(arr.getString(i)) }.getOrNull()?.let { knownResonances += it }
                    }
                    j.optJSONArray("reactions")?.let { arr ->
                        for (i in 0 until arr.length())
                            runCatching { Reaction.valueOf(arr.getString(i)) }.getOrNull()?.let { knownReactions += it }
                    }
                    j.optJSONArray("affinities")?.let { arr -> for (i in 0 until arr.length()) knownAffinities += arr.getString(i) }
                    j.optJSONArray("sets")?.let { arr -> for (i in 0 until arr.length()) knownSets += arr.getInt(i) }
                    // Une paire portée avant que les résonances existent : on la connaît déjà
                    discoverResonances()
                }
            }
            return RoguelikeGame(hero, j.getInt("floor"), levelSeed = j.optLong("levelSeed", Random.nextLong())).apply {
                heroSpritePath = j.getString("heroSprite")
                checkpoint = j.optInt("checkpoint", checkpoint).coerceIn(CHECKPOINT, floor)
                val pendingDeath = j.optJSONObject("deathReport")
                if (pendingDeath != null) {
                    val deathFloor = pendingDeath.getInt("floor").coerceAtLeast(CHECKPOINT)
                    deathReport = DeathReport(deathFloor, pendingDeath.getInt("goldLost"),
                        pendingDeath.optInt("checkpointFloor", checkpointFloor(deathFloor)).coerceIn(CHECKPOINT, deathFloor))
                } else if (j.getInt("hp") <= 0) {
                    // A defeat saved before dismissing the combat screen still requires a choice.
                    die()
                }
                log.clear()
                addLog(R.string.roguelike_log_resume, floor)
            }
        }

        fun fromJson(j: JSONObject): RoguelikeGame = inventoryFromJson(j).apply {
            restoreMapState(j)
        }
    }

    var floor = startFloor
        private set
    var levelSeed: Long = levelSeed
        private set
    var regenerationCount = 0
        private set

    init { hero.floor = floor }
    /** L'étage où la mort ramène. */
    var checkpoint = checkpointFloor(startFloor, checkpointEvery)
        private set
    /** Une forge sur cet étage : tirée à l'arrivée ([DungeonForge.rollPresence]), gardée si on régénère l'étage. */
    var forgeOnFloor = false
        private set
    var level: DungeonLevel = generateLevel(floor, levelSeed)
        private set
    var playerPos: Pos = level.start
        private set

    val log = ArrayDeque<LogEntry>()
    var heroSpritePath: String = "Assets/sprites/Dungeon/Heros/paperdoll_example_%02d.png"
        .format(Random.nextInt(1, 30))

    var combat: Combat? = null
        private set
    private var combatPack: MonsterPack? = null

    /** Équipements gagnés au dernier combat, proposés un par un. */
    val pendingLoot = ArrayDeque<Equipment>()
    val pendingEquipDrop get() = pendingLoot.firstOrNull()

    private var stairsArrivalPending = false

    var stairsOpen = false
    /** Le joueur se tient sur la forge et sa fenêtre est ouverte. */
    var forgeOpen = false
        private set
    /** Un équipement est déjà tombé sur cet étage (voir [GUARANTEED_GEAR_FLOORS]). */
    private var gearDroppedThisFloor = false
        private set
    var deathReport: DeathReport? = null
        private set

    init {
        computeFov()
        addLog(R.string.roguelike_log_descend_start)
    }

    // ── État ────────────────────────────────────────────────────────────────────

    /** Rien d'ouvert par-dessus la carte : on peut bouger. */
    val isExploring get() = combat == null && pendingLoot.isEmpty() && !stairsOpen && !forgeOpen && deathReport == null

    val isChased get() = level.packs.any { it.alive && it.state == PackState.CHASING }

    /** Le repos soigne et recharge les reliques : utile tant que l'un des deux n'est pas plein. */
    fun onCampTile() = playerPos == level.start
    fun canRest() = isExploring && onCampTile() && !isChased && (hero.hp < hero.maxHp || hero.relicsRecharging || hero.specialCooldown > 0)

    fun onStairsTile() = level.tiles[playerPos.y][playerPos.x] == TileType.STAIRS_DOWN

    // ── Actions sur la carte ────────────────────────────────────────────────────

    fun tryMove(dx: Int, dy: Int) {
        if (!isExploring || (dx == 0 && dy == 0)) return
        val nx = playerPos.x + dx; val ny = playerPos.y + dy
        val pack = level.packAt(nx, ny)
        if (pack != null) { startCombat(pack, ambush = false, encounterPos = pack.pos); return }
        if (!level.canStep(playerPos, dx, dy)) return
        playerPos = Pos(nx, ny)
        stairsArrivalPending = onStairsTile()
        hero.walkRelics()
        recoverAtCamp()
        pickup()
        endMapTurn()
        openStairsOnArrival()
        if (isExploring && playerPos == level.forge) forgeOpen = true
    }

    /** Arrival heals immediately, without spending an extra map turn. */
    private fun recoverAtCamp() {
        if (!onCampTile()) return
        if (hero.hp == hero.maxHp && !hero.relicsRecharging && hero.specialCooldown == 0) return
        hero.healFull()
        hero.relicCooldowns.clear()
        hero.specialCooldown = 0
        addLog(R.string.roguelike_log_camp_rest, hero.hp, hero.maxHp)
    }

    /** Full recovery is possible only on the starting fire, with no active pursuit. */
    fun rest(): Boolean {
        if (!canRest()) return false
        recoverAtCamp()
        endMapTurn(resting = true)
        return true
    }
    /** Sur l'escalier : on demande avant de descendre (on peut vouloir finir l'étage). */
    fun openStairs() {
        if (isExploring && onStairsTile()) stairsOpen = true
    }

    private fun openStairsOnArrival() {
        if (stairsArrivalPending && isExploring) {
            stairsArrivalPending = false
            openStairs()
        }
    }

    fun closeStairs() { stairsOpen = false; stairsArrivalPending = false }

    // ── Forge ───────────────────────────────────────────────────────────────────

    val forgePrice get() = DungeonForge.price(floor)

    fun closeForge() { forgeOpen = false }

    /** Outil de test : une forge sur cet étage, sur une case libre à côté du héros. */
    internal fun placeForgeNearHero(): Boolean {
        val taken = level.items.map { it.pos }.toSet()
        val spot = (1..3).asSequence().flatMap { r -> (-r..r).asSequence().flatMap { dy -> (-r..r).asSequence().map { dx -> Pos(playerPos.x + dx, playerPos.y + dy) } } }
            .firstOrNull { level.inBounds(it.x, it.y) && level.tiles[it.y][it.x] == TileType.FLOOR && it != playerPos &&
                it != level.start && it !in taken && it !in level.scenery && level.packAt(it.x, it.y) == null } ?: return false
        forgeOnFloor = true
        level.forge = spot
        level.explored[spot.y][spot.x] = true
        return true
    }

    /**
     * Reforge [item] (porté ou dans le sac) au niveau de l'étage : il est remplacé à sa place par le
     * nouveau tirage. Renvoie l'objet forgé, ou null si la forge n'est pas ouverte ou l'or manque.
     */
    fun reforge(item: Equipment): Equipment? {
        if (!forgeOpen || combat != null) return null
        val price = forgePrice
        if (hero.gold < price) return null
        val worn = hero.equipped[item.slot] === item
        val bagIndex = if (worn) -1 else hero.bag.indexOfFirst { it === item }
        if (!worn && bagIndex < 0) return null
        val forged = LootSystem.reforge(item, floor, hero.nextLootId++, rng)
        hero.gold -= price
        if (worn) {
            hero.equipped[item.slot] = forged
            hero.hp = hero.hp.coerceAtMost(hero.maxHp)
        } else hero.bag[bagIndex] = forged
        forged.isotopeSet?.let { hero.knownSets += it.z }
        addLog(R.string.roguelike_log_forged, forged, price)
        return forged
    }

    fun descend() {
        if (!stairsOpen) return
        stairsOpen = false
        changeFloor(floor + 1)
        addLog(R.string.roguelike_log_floor_descend, floor)
    }

    /** Fin de combat : on équipe l'objet proposé, l'ancien part au sac. */
    fun equipPendingDrop() {
        val equip = pendingLoot.removeFirstOrNull() ?: return
        hero.equip(equip)
        addLog(R.string.roguelike_log_equip, equip.slot, equip)
        if (pendingLoot.isEmpty()) chainIfChased()
    }

    /** Fin de combat : l'objet va au sac. Rien ne se perd. */
    fun stashPendingDrop() {
        val equip = pendingLoot.removeFirstOrNull() ?: return
        hero.bag += equip
        if (pendingLoot.isEmpty()) chainIfChased()
    }

    // ── Sac ─────────────────────────────────────────────────────────────────────

    fun equipFromBag(item: Equipment) {
        if (!isExploring || !hero.bag.remove(item)) return
        hero.equip(item)
        addLog(R.string.roguelike_log_equip, item.slot, item)
    }

    /** Remet un objet porté dans le sac, uniquement hors combat. */
    fun unequip(item: Equipment): Boolean {
        if (!isExploring || hero.equipped[item.slot] != item) return false
        hero.equipped.remove(item.slot)
        hero.bag += item
        hero.hp = hero.hp.coerceAtMost(hero.maxHp)
        return true
    }

    fun sell(item: Equipment) {
        if (!isExploring || !hero.bag.remove(item)) return
        val price = LootSystem.sellPrice(item)
        hero.gold += price
        addLog(R.string.roguelike_log_sold, item, price)
    }

    /** Sell a confirmed selection in one pass; reject a stale selection without selling part of it. */
    fun sellAll(items: Set<Equipment>): Boolean {
        if (!isExploring || items.isEmpty()) return false
        val owned = hero.bag.filter { it in items && it !in hero.equipped.values }
        if (owned.size != items.size) return false
        val price = owned.sumOf { LootSystem.sellPrice(it).toLong() }
        val balance = hero.gold.toLong() + price
        if (price < 0 || balance > Int.MAX_VALUE) return false
        hero.bag.removeAll { it in items }
        hero.gold = balance.toInt()
        addLog(R.string.roguelike_log_sold_many, owned.size, price)
        return true
    }

    // ── Reliques ────────────────────────────────────────────────────────────────

    /** Porter ou ranger une relique, seulement hors combat. */
    fun toggleRelic(relic: Relic): Hero.RelicToggle? {
        if (!isExploring) return null
        return hero.toggleRelic(relic).also { logDiscoveredResonances() }
    }

    /** Une paire portée pour la première fois : on l'annonce, elle rejoint le carnet. */
    private fun logDiscoveredResonances() {
        for (r in hero.discoverResonances()) addLog(R.string.roguelike_log_resonance_found, r, Resonance.BONUS, r.attribute)
    }

    fun restartAfterDeath(atCheckpoint: Boolean) {
        val report = deathReport ?: return
        deathReport = null
        changeFloor(if (atCheckpoint) report.checkpointFloor else report.floor)
    }

    /** Default choice retained for simulation callers. */
    fun dismissDeath() = restartAfterDeath(atCheckpoint = true)

    fun returnToCheckpoint() {
        if (!isExploring || !onCampTile()) return
        // Déjà sur l'étage du checkpoint : ce n'est pas une nouvelle arrivée, la chance de forge ne se relance pas
        // (sinon ce menu, répété au feu de camp, ferait apparaître une forge sans rien jouer)
        changeFloor(checkpoint, keepForgeRoll = checkpoint == floor)
        addLog(R.string.roguelike_log_camp_checkpoint, checkpoint)
    }

    /** Regenerates this floor from the next random state, preserving the hero and checkpoint. */
    fun regenerateCurrentFloor() {
        if (!isExploring || !onCampTile()) return
        levelSeed = rng.nextLong()
        regenerationCount++
        level = generateLevel(floor, levelSeed)
        playerPos = level.start
        stairsArrivalPending = false
        computeFov()
        recoverAtCamp()
        addLog(R.string.roguelike_log_camp_regenerate, floor)
    }

    // ── Combat ──────────────────────────────────────────────────────────────────

    private fun startCombat(pack: MonsterPack, ambush: Boolean, encounterPos: Pos = playerPos) {
        combatPack = pack
        combat = Combat(hero, floor, Encounters.build(pack.types, floor), ambush, rng, visualSeed = pack.home.x * 73856093 xor pack.home.y * 19349663, backdrop = level.backdropAt(encounterPos))
        onCombatStart?.invoke()
    }

    /** Appelé par l'écran de combat une fois la victoire ou la défaite affichée. */
    fun finishCombat() {
        val c = combat ?: return
        // On range l'état du combat qui s'achève AVANT de regarder s'il en démarre un autre :
        // sinon, l'enchaînement pose le nouveau groupe dans combatPack et la remise à zéro
        // qui suivait l'effaçait aussitôt. Le groupe enchaîné n'était alors jamais marqué
        // mort en fin de combat — il restait sur la carte, collé au joueur et toujours en
        // chasse, donc il réenchaînait sans fin.
        val beaten = combatPack
        combat = null
        combatPack = null
        when (c.phase) {
            CombatPhase.VICTORY -> {
                beaten?.alive = false
                // Reliques et Spécial sont prêts pour le combat suivant, pour tout le monde.
                hero.relicCooldowns.clear()
                hero.specialCooldown = 0
                val r = c.rewards!!
                hero.gold += r.gold
                pendingLoot.addAll(r.equipment)
                if (floor <= GUARANTEED_GEAR_FLOORS && !gearDroppedThisFloor && r.equipment.isEmpty())
                    pendingLoot += LootSystem.generateNormal(floor, hero.nextLootId++, rng)
                if (pendingLoot.isNotEmpty()) gearDroppedThisFloor = true
                r.equipment.forEach { e -> e.isotopeSet?.let { hero.knownSets += it.z } }
                addLog(R.string.roguelike_log_victory, r.gold)
                if (pendingLoot.isEmpty()) chainIfChased()
            }
            CombatPhase.DEFEAT -> die()
            else -> {}
        }
    }

    /** Un poursuivant tout proche nous saute dessus sans nous laisser souffler. */
    private fun chainIfChased() {
        val next = level.packs
            .filter { it.alive && it.state == PackState.CHASING && it.pos.chebyshev(playerPos) <= CHAIN_DISTANCE }
            .minByOrNull { it.pos.chebyshev(playerPos) }
        if (next == null) {
            // Recover only after the entire chain and its loot have been resolved.
            hero.healFull()
            openStairsOnArrival()
            return
        }
        addLog(R.string.roguelike_log_chain)
        startCombat(next, ambush = false)
    }

    private fun die() {
        val lost = (hero.gold * DEATH_GOLD_LOSS).roundToInt()
        hero.gold -= lost
        deathReport = DeathReport(floor, lost, (checkpoint - deathRetreat).coerceIn(CHECKPOINT, floor))
        hero.healFull()
        hero.relicCooldowns.clear()
        hero.specialCooldown = 0
        log.clear()
        addLog(R.string.roguelike_log_player_death)
    }

    // ── Tour des monstres sur la carte ──────────────────────────────────────────

    /**
     * Un monstre qui nous rejoint frappe en premier seulement s'il nous surprend : on ne
     * le voyait pas avant ce tour, ou on se reposait. Sinon, c'est nous qui ouvrons.
     */
    private fun endMapTurn(resting: Boolean = false) {
        val seenBefore = level.packs.filter { it.alive && level.visible[it.pos.y][it.pos.x] }.toSet()
        computeFov()
        for (pack in level.packs) {
            if (!pack.alive || combat != null) continue
            val sees = pack.pos.chebyshev(playerPos) <= SIGHT && level.visible[pack.pos.y][pack.pos.x]
            if (sees) {
                if (pack.state == PackState.IDLE) addLog(R.string.roguelike_log_spotted, pack.types.first())
                pack.state = PackState.CHASING; pack.lostTurns = 0
            } else if (pack.state == PackState.CHASING && ++pack.lostTurns > CHASE_MEMORY) {
                pack.state = PackState.IDLE
                addLog(R.string.roguelike_log_lost_track, pack.types.first())
            }

            when (pack.state) {
                PackState.CHASING -> {
                    if (pack.pos.chebyshev(playerPos) > 1) stepToward(pack, playerPos)
                    if (pack.pos.chebyshev(playerPos) <= 1) startCombat(pack, ambush = resting || pack !in seenBefore)
                }
                PackState.IDLE -> wander(pack)
            }
        }
    }

    private fun stepToward(pack: MonsterPack, target: Pos) {
        val next = bfsFirstStep(pack.pos, target) ?: return
        if (level.packAt(next.x, next.y) == null && next != playerPos) pack.pos = next
    }

    private fun wander(pack: MonsterPack) {
        if (MonsterType.PIRATE_CAPTAIN in pack.types) return
        if (rng.nextFloat() > 0.3f) return
        fun calm(p: Pos) = p in level.quietCells || level.campDistances[p.y][p.x] in 0..7
        if (pack.patrol.size > 1) {
            if (pack.pos == pack.patrol[pack.patrolIndex]) pack.patrolIndex = (pack.patrolIndex + 1) % pack.patrol.size
            val next = bfsFirstStep(pack.pos, pack.patrol[pack.patrolIndex], avoidQuiet = true,
                roamingTypes = pack.types) ?: return
            if (!calm(next) && level.packAt(next.x,next.y) == null && next != playerPos) pack.pos = next
            return
        }
        val dx = rng.nextInt(-1, 2); val dy = rng.nextInt(-1, 2)
        val n = Pos(pack.pos.x + dx, pack.pos.y + dy)
        if (level.canStep(pack.pos, dx, dy) && !calm(n) && level.packAt(n.x, n.y) == null && n != playerPos &&
            n.chebyshev(pack.home) <= 4 && DungeonBestiary.canWander(pack.types, level.themeAt(n.x, n.y), level.backdropAt(n)))
            pack.pos = n
    }
    /** Premier pas du plus court chemin (8 directions), limité pour rester léger. */
    private fun bfsFirstStep(from: Pos, to: Pos, avoidQuiet: Boolean = false,
        roamingTypes: List<MonsterType>? = null): Pos? {
        val prev = HashMap<Pos, Pos>()
        val queue = ArrayDeque<Pos>()
        queue.add(from); prev[from] = from
        while (queue.isNotEmpty() && prev.size < 600) {
            val c = queue.removeFirst()
            if (c == to) {
                var n = c
                while (prev[n] != from) n = prev[n]!!
                return n
            }
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val n = Pos(c.x + dx, c.y + dy)
                if (n in prev || !level.canStep(c, dx, dy)) continue
                if (roamingTypes != null && !DungeonBestiary.canWander(roamingTypes, level.themeAt(n.x, n.y), level.backdropAt(n))) continue
                if (avoidQuiet && (n in level.quietCells || level.campDistances[n.y][n.x] in 0..7)) continue
                prev[n] = c; queue.add(n)
            }
        }
        return null
    }

    private fun pickup() {
        val here = level.items.filter { it.pos == playerPos }
        for (item in here) when (item.type) {
            ItemType.GOLD -> {
                val gain = (rng.nextInt(3, 9) * (1f + 0.1f * (floor - 1)) * hero.goldMult).roundToInt()
                hero.gold += gain
                level.items.remove(item)
                addLog(R.string.roguelike_log_gold_pickup, gain)
            }
            ItemType.RELIC -> {
                val relic = item.relic ?: continue
                level.items.remove(item)
                val worn = hero.addRelic(relic)
                addLog(if (worn) R.string.roguelike_log_relic_found_equipped else R.string.roguelike_log_relic_found_bag, relic)
                logDiscoveredResonances()
            }
        }
    }

    private fun changeFloor(newFloor: Int, keepForgeRoll: Boolean = false) {
        floor = newFloor
        checkpoint = maxOf(checkpoint, checkpointFloor(floor, checkpointEvery))
        hero.floor = floor
        levelSeed = rng.nextLong()
        regenerationCount = 0
        gearDroppedThisFloor = false
        if (!keepForgeRoll) forgeOnFloor = DungeonForge.rollPresence(floor, rng)
        forgeOpen = false
        level = generateLevel(floor, levelSeed)
        playerPos = level.start
        stairsArrivalPending = false
        computeFov()
        if (hero.reachFloor(floor)) addLog(R.string.roguelike_log_relic_slot, hero.unlockedRelicSlots, Hero.RELIC_SLOTS)
        recoverAtCamp()
        onFloorChanged?.invoke(floor)
    }

    // ── Champ de vision ─────────────────────────────────────────────────────────

    fun computeFov() {
        val lv = level
        for (y in 0 until lv.h) lv.visible[y].fill(false)
        val px = playerPos.x; val py = playerPos.y
        for (ty in maxOf(0, py - FOV_RADIUS)..minOf(lv.h - 1, py + FOV_RADIUS))
            for (tx in maxOf(0, px - FOV_RADIUS)..minOf(lv.w - 1, px + FOV_RADIUS)) {
                if (los(px, py, tx, ty, lv)) { lv.visible[ty][tx] = true; lv.explored[ty][tx] = true }
            }
    }

    private fun los(x0: Int, y0: Int, x1: Int, y1: Int, lv: DungeonLevel): Boolean {
        val dx = x1 - x0; val dy = y1 - y0
        val steps = max(abs(dx), abs(dy))
        if (steps == 0) return true
        for (i in 1 until steps) {
            val x = (x0 + dx * i.toFloat() / steps).roundToInt()
            val y = (y0 + dy * i.toFloat() / steps).roundToInt()
            if (lv.tiles[y][x] == TileType.WALL && Pos(x,y) !in lv.waterways) return false
        }
        return true
    }

    // ── Génération ──────────────────────────────────────────────────────────────

    private fun generateLevel(floor: Int, seed: Long): DungeonLevel {
        val levelRng = Random(seed)
        val prepared = DungeonLevelFactory.create(floor, levelRng)
        val lv = prepared.level
        val layout = prepared.layout
        val w = lv.w; val h = lv.h
        val dist = DungeonPaths.distances(lv, lv.start)
        val farCells = (0 until h).flatMap { y -> (0 until w).map { x -> Pos(x,y) } }
            .filter { lv.tiles[it.y][it.x] == TileType.FLOOR && dist[it.y][it.x] >= 8 }
        val captainSite = DungeonBestiary.captainSite(lv, prepared.population.spawns)
        for (spawn in prepared.population.spawns) {
            val types = if (spawn.pos == captainSite) Encounters.captain(floor)
                else Encounters.roll(floor, levelRng, lv.themeAt(spawn.pos.x, spawn.pos.y), lv.backdropAt(spawn.pos))
            lv.packs += MonsterPack(types, spawn.pos).apply {
                patrol = spawn.patrol.takeIf { route -> route.all {
                    DungeonBestiary.canWander(types, lv.themeAt(it.x, it.y), lv.backdropAt(it))
                } } ?: emptyList()
            }
        }
        val quietLoot = prepared.population.sites.filter { it.kind == MapSiteKind.QUIET }.map { it.pos }
        // Parfois une relique, au bout du cul-de-sac le plus éloigné du départ
        val firstRelic = hero.relics.isEmpty() && floor >= FIRST_RELIC_FLOOR
        if (firstRelic || (floor >= FIRST_RELIC_FLOOR && levelRng.nextFloat() < RELIC_CHANCE)) {
            val relic = Relic.entries.filter { it !in hero.relics }.randomOrNull(levelRng)
            val spot = (quietLoot + layout.deadEnds).filter { lv.tiles[it.y][it.x] == TileType.FLOOR && it != lv.start }
                .maxByOrNull { dist[it.y][it.x] }
                ?: farCells.maxByOrNull { dist[it.y][it.x] }
            if (relic != null && spot != null) lv.items += Item(ItemType.RELIC, spot, relic)
        }

        // L'or récompense l'exploration : d'abord au bout des culs-de-sac
        val relicSpots = lv.items.map { it.pos }.toSet()
        val spots = (quietLoot.shuffled(levelRng) + layout.deadEnds.shuffled(levelRng) + layout.rooms.shuffled(levelRng).map { it.randomInner(levelRng) })
            .filter { lv.tiles[it.y][it.x] == TileType.FLOOR && it != lv.start && it !in relicSpots }
            .distinct()
        val goldCount = 3 + lv.targetPacks / 4 + levelRng.nextInt(3)
        spots.take(goldCount).forEach { lv.items += Item(ItemType.GOLD, it) }

        // La forge : dans une salle, loin du feu de camp, jamais sur un objet ni sur un décor
        if (forgeOnFloor) {
            val taken = lv.items.map { it.pos }.toSet()
            lv.forge = (layout.rooms.shuffled(levelRng).map { it.randomInner(levelRng) } + farCells.shuffled(levelRng))
                .firstOrNull { lv.tiles[it.y][it.x] == TileType.FLOOR && it.chebyshev(lv.start) > 2 && it !in taken &&
                    it !in lv.scenery && it !in lv.passages && it !in lv.waterways }
        }

        return lv
    }

    fun addLog(@StringRes keyRes: Int, vararg args: Any) {
        if (log.size >= 6) log.removeFirst()
        log.addLast(LogEntry(keyRes, args.toList()))
    }

    // ── Sauvegarde ──────────────────────────────────────────────────────────────

    fun inventoryToJson(): JSONObject = JSONObject().apply {
        put("floor",      floor)
        put("checkpoint", checkpoint)
        deathReport?.let { report ->
            put("deathReport", JSONObject().apply {
                put("floor", report.floor)
                put("goldLost", report.goldLost)
                put("checkpointFloor", report.checkpointFloor)
            })
        }
        put("hp",         hero.hp)
        put("hpMultiplier", HitPointBalance.playerMultiplier(hero.floor))
        put("gold",       hero.gold)
        put("deepestFloor", hero.deepestFloor)
        put("heroSprite", heroSpritePath)
        put("equipped", JSONObject().also { eq ->
            for ((slot, equip) in hero.equipped) eq.put(slot.name, SaveManager.equipToJson(equip))
        })
        put("bag", org.json.JSONArray().also { arr -> hero.bag.forEach { arr.put(SaveManager.equipToJson(it)) } })
        put("nextLootId", hero.nextLootId)
        put("specialCooldown", hero.specialCooldown)
        put("relics", org.json.JSONArray().also { arr -> hero.relics.forEach { arr.put(it.name) } })
        put("relicSlots", org.json.JSONArray().also { arr -> hero.relicSlots.forEach { arr.put(it?.name ?: "") } })
        put("relicCooldowns", JSONObject().also { o -> hero.relicCooldowns.forEach { (r, cd) -> o.put(r.name, cd) } })
        put("resonances", org.json.JSONArray().also { arr -> hero.knownResonances.forEach { arr.put(it.name) } })
        put("reactions", org.json.JSONArray().also { arr -> hero.knownReactions.forEach { arr.put(it.name) } })
        put("affinities", org.json.JSONArray().also { arr -> hero.knownAffinities.forEach { arr.put(it) } })
        put("sets", org.json.JSONArray().also { arr -> hero.knownSets.forEach { arr.put(it) } })
    }

    fun mapStateToJson(): JSONObject = JSONObject().apply {
        put("floor", floor)
        put("levelSeed", levelSeed)
        put("regenerationCount", regenerationCount)
        put("player", posToJson(playerPos))
        put("stairsOpen", stairsOpen)
        put("stairsArrivalPending", stairsArrivalPending)
        put("gearDroppedThisFloor", gearDroppedThisFloor)
        put("forge", forgeOnFloor)
        put("forgeOpen", forgeOpen)
        put("deathReport", deathReport?.let { report -> JSONObject().apply {
            put("floor", report.floor)
            put("goldLost", report.goldLost)
            put("checkpointFloor", report.checkpointFloor)
        } })
        put("explored", JSONArray().also { rows ->
            for (y in 0 until level.h) rows.put(String(CharArray(level.w) { x -> if (level.explored[y][x]) '1' else '0' }))
        })
        put("packs", JSONArray().also { arr ->
            level.packs.forEach { pack ->
                arr.put(JSONObject().apply {
                    put("types", JSONArray().also { types -> pack.types.forEach { types.put(it.name) } })
                    put("home", posToJson(pack.home))
                    put("pos", posToJson(pack.pos))
                    put("alive", pack.alive)
                    put("state", pack.state.name)
                    put("lostTurns", pack.lostTurns)
                    put("patrolIndex", pack.patrolIndex)
                })
            }
        })
        put("items", JSONArray().also { arr ->
            level.items.forEach { item ->
                arr.put(JSONObject().apply {
                    put("type", item.type.name)
                    put("pos", posToJson(item.pos))
                    item.relic?.let { put("relic", it.name) }
                })
            }
        })
        val stairs = findStairs()
        if (stairs != null) put("stairs", posToJson(stairs))
    }

    fun toJson(): JSONObject = inventoryToJson().apply {
        val state = mapStateToJson()
        for (key in state.keys()) put(key, state.get(key))
    }

    fun restoreFromSavedState(j: JSONObject) = restoreMapState(j)

    private fun restoreMapState(j: JSONObject) {
        floor = j.optInt("floor", floor)
        hero.floor = floor
        checkpoint = j.optInt("checkpoint", checkpoint).coerceIn(CHECKPOINT, floor)
        levelSeed = j.optLong("levelSeed", levelSeed)
        regenerationCount = j.optInt("regenerationCount", 0).coerceAtLeast(0)
        forgeOnFloor = j.optBoolean("forge", false)
        level = generateLevel(floor, levelSeed)
        j.optJSONArray("explored")?.let { rows ->
            for (y in 0 until minOf(rows.length(), level.h)) {
                val row = rows.optString(y, "")
                for (x in 0 until minOf(row.length, level.w)) level.explored[y][x] = row[x] == '1'
            }
        }
        j.optJSONArray("packs")?.let { arr ->
            val generated = level.packs.associateBy { "${it.home.x}:${it.home.y}:${it.types.joinToString(",")}" }
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val types = p.getJSONArray("types").let { typesJson ->
                    (0 until typesJson.length()).mapNotNull { index ->
                        runCatching { MonsterType.valueOf(typesJson.getString(index)) }.getOrNull()
                    }
                }
                val home = posFromJson(p.getJSONObject("home"))
                val pack = generated["${home.x}:${home.y}:${types.joinToString(",")}"] ?: continue
                pack.pos = posFromJson(p.getJSONObject("pos"))
                pack.alive = p.optBoolean("alive", true)
                pack.state = runCatching { PackState.valueOf(p.optString("state", PackState.IDLE.name)) }.getOrDefault(PackState.IDLE)
                pack.lostTurns = p.optInt("lostTurns", 0)
                pack.patrolIndex = p.optInt("patrolIndex", pack.patrolIndex).coerceAtLeast(0)
            }
        }
        j.optJSONArray("items")?.let { arr ->
            level.items.clear()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val type = runCatching { ItemType.valueOf(item.getString("type")) }.getOrNull() ?: continue
                val relic = item.optString("relic", "").takeIf { it.isNotEmpty() }?.let {
                    runCatching { Relic.valueOf(it) }.getOrNull()
                }
                level.items += Item(type, posFromJson(item.getJSONObject("pos")), relic)
            }
        }
        playerPos = j.optJSONObject("player")?.let(::posFromJson)?.takeIf { level.walkable(it.x, it.y) } ?: level.start
        stairsOpen = j.optBoolean("stairsOpen", false)
        stairsArrivalPending = j.optBoolean("stairsArrivalPending", false)
        gearDroppedThisFloor = j.optBoolean("gearDroppedThisFloor", false)
        forgeOpen = j.optBoolean("forgeOpen", false) && playerPos == level.forge
        deathReport = j.optJSONObject("deathReport")?.let { report ->
            DeathReport(report.getInt("floor"), report.getInt("goldLost"),
                report.optInt("checkpointFloor", checkpointFloor(report.getInt("floor"))))
        }
        combat = null
        combatPack = null
        computeFov()
    }

    private fun findStairs(): Pos? {
        for (y in 0 until level.h) for (x in 0 until level.w) {
            if (level.tiles[y][x] == TileType.STAIRS_DOWN) return Pos(x, y)
        }
        return null
    }

    private fun posToJson(pos: Pos) = JSONObject().apply {
        put("x", pos.x)
        put("y", pos.y)
    }

    private fun posFromJson(j: JSONObject) = Pos(j.getInt("x"), j.getInt("y"))
}
