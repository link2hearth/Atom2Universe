package com.Atom2Universe.app.games.roguelike

import androidx.annotation.StringRes
import com.Atom2Universe.app.R
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

        fun fromJson(j: JSONObject): RoguelikeGame {
            val hero = Hero().apply {
                gold    = j.getInt("gold")
                deepestFloor = j.optInt("deepestFloor", j.getInt("floor"))
                val eq  = j.getJSONObject("equipped")
                for (slotName in eq.keys())
                    equipped[EquipSlot.valueOf(slotName)] = SaveManager.equipFromJson(eq.getJSONObject(slotName))
                val bagJson = j.getJSONArray("bag")
                for (i in 0 until bagJson.length()) bag += SaveManager.equipFromJson(bagJson.getJSONObject(i))
                nextLootId = j.getLong("nextLootId")
                hp = j.getInt("hp").coerceIn(1, maxHp)
                specialCooldown = j.optInt("specialCooldown", 0)
                val relicsJson = j.optJSONArray("relics")
                if (relicsJson == null) {
                    // Sauvegarde d'avant les reliques : on y avait toujours la Boule de feu
                    addRelic(Relic.FIREBALL)
                } else {
                    for (i in 0 until relicsJson.length())
                        runCatching { Relic.valueOf(relicsJson.getString(i)) }.getOrNull()?.let { relics += it }
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
            return RoguelikeGame(hero, j.getInt("floor")).apply {
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
    }

    var floor = startFloor
        private set

    init { hero.floor = floor }
    /** L'étage où la mort ramène. */
    var checkpoint = checkpointFloor(startFloor, checkpointEvery)
        private set
    var level: DungeonLevel = generateLevel(floor)
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

    var stairsOpen = false
        private set
    var deathReport: DeathReport? = null
        private set

    init {
        computeFov()
        addLog(R.string.roguelike_log_descend_start)
    }

    // ── État ────────────────────────────────────────────────────────────────────

    /** Rien d'ouvert par-dessus la carte : on peut bouger. */
    val isExploring get() = combat == null && pendingLoot.isEmpty() && !stairsOpen && deathReport == null

    val isChased get() = level.packs.any { it.alive && it.state == PackState.CHASING }

    /** Le repos soigne et recharge les reliques : utile tant que l'un des deux n'est pas plein. */
    fun onCampTile() = playerPos == level.start
    fun canRest() = isExploring && onCampTile() && !isChased && (hero.hp < hero.maxHp || hero.relicsRecharging || hero.specialCooldown > 0)

    fun onStairsTile() = level.tiles[playerPos.y][playerPos.x] == TileType.STAIRS_DOWN

    // ── Actions sur la carte ────────────────────────────────────────────────────

    fun tryMove(dx: Int, dy: Int) {
        if (!isExploring) return
        val nx = playerPos.x + dx; val ny = playerPos.y + dy
        val pack = level.packAt(nx, ny)
        if (pack != null) { startCombat(pack, ambush = false, encounterPos = pack.pos); return }
        if (!level.canStep(playerPos, dx, dy)) return
        playerPos = Pos(nx, ny)
        hero.walkRelics()
        pickup()
        endMapTurn()
    }

    /** Full recovery is possible only on the starting fire, with no active pursuit. */
    fun rest(): Boolean {
        if (!canRest()) return false
        hero.healFull()
        hero.relicCooldowns.clear()
        hero.specialCooldown = 0
        addLog(R.string.roguelike_log_camp_rest, hero.hp, hero.maxHp)
        endMapTurn(resting = true)
        return true
    }
    /** Sur l'escalier : on demande avant de descendre (on peut vouloir finir l'étage). */
    fun openStairs() {
        if (isExploring && onStairsTile()) stairsOpen = true
    }

    fun closeStairs() { stairsOpen = false }

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
            .minByOrNull { it.pos.chebyshev(playerPos) } ?: return
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
        if (rng.nextFloat() > 0.3f) return
        fun calm(p: Pos) = p in level.quietCells || level.campDistances[p.y][p.x] in 0..7
        if (pack.patrol.size > 1) {
            if (pack.pos == pack.patrol[pack.patrolIndex]) pack.patrolIndex = (pack.patrolIndex + 1) % pack.patrol.size
            val next = bfsFirstStep(pack.pos, pack.patrol[pack.patrolIndex], avoidQuiet = true) ?: return
            if (!calm(next) && level.packAt(next.x,next.y) == null && next != playerPos) pack.pos = next
            return
        }
        val dx = rng.nextInt(-1, 2); val dy = rng.nextInt(-1, 2)
        val n = Pos(pack.pos.x + dx, pack.pos.y + dy)
        if (level.canStep(pack.pos, dx, dy) && !calm(n) && level.packAt(n.x, n.y) == null && n != playerPos && n.chebyshev(pack.home) <= 4)
            pack.pos = n
    }
    /** Premier pas du plus court chemin (8 directions), limité pour rester léger. */
    private fun bfsFirstStep(from: Pos, to: Pos, avoidQuiet: Boolean = false): Pos? {
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

    private fun changeFloor(newFloor: Int) {
        floor = newFloor
        checkpoint = maxOf(checkpoint, checkpointFloor(floor, checkpointEvery))
        hero.floor = floor
        level = generateLevel(floor)
        playerPos = level.start
        computeFov()
        if (hero.reachFloor(floor)) addLog(R.string.roguelike_log_relic_slot, hero.unlockedRelicSlots, Hero.RELIC_SLOTS)
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

    private fun generateLevel(floor: Int): DungeonLevel {
        val prepared = DungeonLevelFactory.create(floor, rng)
        val lv = prepared.level
        val layout = prepared.layout
        val w = lv.w; val h = lv.h
        val dist = DungeonPaths.distances(lv, lv.start)
        val farCells = (0 until h).flatMap { y -> (0 until w).map { x -> Pos(x,y) } }
            .filter { lv.tiles[it.y][it.x] == TileType.FLOOR && dist[it.y][it.x] >= 8 }
        for (spawn in prepared.population.spawns) {
            lv.packs += MonsterPack(Encounters.roll(floor, rng), spawn.pos).apply { patrol = spawn.patrol }
        }
        val quietLoot = prepared.population.sites.filter { it.kind == MapSiteKind.QUIET }.map { it.pos }
        // Parfois une relique, au bout du cul-de-sac le plus éloigné du départ
        val firstRelic = hero.relics.isEmpty() && floor >= FIRST_RELIC_FLOOR
        if (firstRelic || (floor >= FIRST_RELIC_FLOOR && rng.nextFloat() < RELIC_CHANCE)) {
            val relic = Relic.entries.filter { it !in hero.relics }.randomOrNull(rng)
            val spot = (quietLoot + layout.deadEnds).filter { lv.tiles[it.y][it.x] == TileType.FLOOR && it != lv.start }
                .maxByOrNull { dist[it.y][it.x] }
                ?: farCells.maxByOrNull { dist[it.y][it.x] }
            if (relic != null && spot != null) lv.items += Item(ItemType.RELIC, spot, relic)
        }

        // L'or récompense l'exploration : d'abord au bout des culs-de-sac
        val relicSpots = lv.items.map { it.pos }.toSet()
        val spots = (quietLoot.shuffled(rng) + layout.deadEnds.shuffled(rng) + layout.rooms.shuffled(rng).map { it.randomInner(rng) })
            .filter { lv.tiles[it.y][it.x] == TileType.FLOOR && it != lv.start && it !in relicSpots }
            .distinct()
        val goldCount = 3 + lv.targetPacks / 4 + rng.nextInt(3)
        spots.take(goldCount).forEach { lv.items += Item(ItemType.GOLD, it) }

        return lv
    }

    fun addLog(@StringRes keyRes: Int, vararg args: Any) {
        if (log.size >= 6) log.removeFirst()
        log.addLast(LogEntry(keyRes, args.toList()))
    }

    // ── Sauvegarde ──────────────────────────────────────────────────────────────

    /** Le niveau n'est pas sauvegardé : il est régénéré à la reprise. */
    fun toJson(): JSONObject = JSONObject().apply {
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
}
