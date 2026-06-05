package com.Atom2Universe.app.games.roguelike

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

// ─── Tile ──────────────────────────────────────────────────────────────────────
enum class TileType { WALL, FLOOR, STAIRS_DOWN }

// ─── Position ──────────────────────────────────────────────────────────────────
data class Pos(val x: Int, val y: Int) {
    fun chebyshev(other: Pos) = max(abs(x - other.x), abs(y - other.y))
    fun manhattan(other: Pos) = abs(x - other.x) + abs(y - other.y)
}

// ─── Items (consommables) ───────────────────────────────────────────────────────
enum class ItemType(
    val symbol: Char, val label: String, val colorArgb: Int,
    val healAmount: Int,
    val spriteRow: Int, val spriteCol: Int
) {
    FOOD_SMALL ('f', "Pain",   0xFFFFCC80.toInt(), 10, 32, 0),
    FOOD_MEDIUM('m', "Viande", 0xFFEF9A9A.toInt(), 25, 30, 0),
    FOOD_LARGE ('s', "Ragoût", 0xFFFF7043.toInt(), 40, 29, 0),
    GOLD       ('$', "Or",     0xFFFFD600.toInt(),  0,  9, 15),
}

data class Item(val type: ItemType, var pos: Pos)

// ─── Monstres ───────────────────────────────────────────────────────────────────
enum class MonsterType(
    val symbol: Char, val label: String, val colorArgb: Int,
    val baseDef: Int, val goldReward: Int, val minFloor: Int,
    val hpMult: Float, val atkMult: Float
) {
    RAT     ('r', "Rat",       0xFF8D6E63.toInt(), 0,  2, 1, 0.50f, 0.50f),
    GOBLIN  ('g', "Gobelin",   0xFF66BB6A.toInt(), 1,  5, 1, 0.75f, 0.75f),
    SKELETON('S', "Squelette", 0xFFECEFF1.toInt(), 2,  8, 2, 1.00f, 1.00f),
    ORC     ('O', "Orc",       0xFF4CAF50.toInt(), 3, 12, 3, 1.40f, 1.20f),
    DEMON   ('D', "Démon",     0xFFEF5350.toInt(), 4, 18, 4, 2.00f, 1.60f),
}

class Monster(
    val type: MonsterType,
    var pos: Pos,
    val scaledHp: Int,
    val scaledAtk: Int,
) {
    var hp     = scaledHp
    val maxHp  = scaledHp
    var isAlive = true
    var awake   = false
}

// ─── Joueur ─────────────────────────────────────────────────────────────────────
class Player(startPos: Pos) {
    var pos    = startPos
    var hp     = 100; var maxHp = 100
    var baseAtk = 5;  var baseDef = 2
    var gold   = 0;   var floor = 1
    val inventory = mutableListOf<Item>()

    val equipped = mutableMapOf<EquipSlot, Equipment>()

    var barrier         = 0
    var barrierUnlocked = false
    val maxBarrier      get() = if (barrierUnlocked) totalMaxHp / 5 else 0
    var barrierStep     = 0

    private fun equipSum(type: StatType) =
        equipped.values.flatMap { it.stats }
            .filter { it.type == type }
            .sumOf { it.value.toDouble() }.toFloat()

    val atk           get() = baseAtk + floor + equipSum(StatType.ATK).toInt()
    val def           get() = baseDef + equipSum(StatType.DEF).toInt()
    val totalMaxHp    get() = maxHp + equipSum(StatType.MAX_HP).toInt()
    val critChance    get() = equipSum(StatType.CRIT_CHANCE).coerceAtMost(0.75f)
    val critDmgMult   get() = 1.5f + equipSum(StatType.CRIT_DMG)
    val evasionChance get() = equipSum(StatType.EVASION).coerceAtMost(0.75f)
    val blockChance   get() = equipSum(StatType.BLOCK).coerceAtMost(0.75f)
}

// ─── Shop ───────────────────────────────────────────────────────────────────────
enum class ShopItem(val cost: Int) {
    POTION(20),
    ATK_UP(25),
    BARRIER(20)
}

// ─── Niveau de donjon ──────────────────────────────────────────────────────────
class DungeonLevel(val w: Int, val h: Int, val floor: Int) {
    val tiles      = Array(h) { Array(w) { TileType.WALL } }
    val monsters   = mutableListOf<Monster>()
    val items      = mutableListOf<Item>()
    val equipDrops = mutableListOf<Pair<Equipment, Pos>>()
    val visible    = Array(h) { BooleanArray(w) }
    val explored   = Array(h) { BooleanArray(w) }
    val theme      = DungeonTheme.ALL.random()

    fun inBounds(x: Int, y: Int)    = x in 0 until w && y in 0 until h
    fun walkable(x: Int, y: Int)    = inBounds(x, y) && tiles[y][x] != TileType.WALL
    fun monsterAt(x: Int, y: Int)   = monsters.find { it.isAlive && it.pos.x == x && it.pos.y == y }
    fun itemAt(x: Int, y: Int)      = items.find { it.pos.x == x && it.pos.y == y }
    fun equipDropAt(x: Int, y: Int) = equipDrops.find { it.second.x == x && it.second.y == y }
    fun hasAliveEnemies()           = monsters.any { it.isAlive }
}

private data class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun center()      = Pos(x + w / 2, y + h / 2)
    fun overlaps(o: Room) = x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y
    fun randomInner() = Pos(x + 1 + Random.nextInt(maxOf(1, w - 2)), y + 1 + Random.nextInt(maxOf(1, h - 2)))
}

enum class GamePhase { PLAYING, GAME_OVER }

// ─── Moteur principal ──────────────────────────────────────────────────────────
class RoguelikeGame {

    companion object {
        const val MAP_W               = 40
        const val MAP_H               = 25
        const val FOV_RADIUS          = 8
        const val MAX_INV             = 5
        const val BARRIER_REGEN_STEPS = 8

        // soin moyen pondéré (50%×10 + 35%×25 + 15%×40 ≈ 20)
        private const val AVG_FOOD_HEAL = 20f

        fun fromJson(j: JSONObject): RoguelikeGame {
            val game = RoguelikeGame()
            val p    = game.player

            p.floor           = j.getInt("floor")
            p.hp              = j.getInt("hp")
            p.maxHp           = j.getInt("maxHp")
            p.baseAtk         = j.getInt("baseAtk")
            p.baseDef         = j.getInt("baseDef")
            p.gold            = j.getInt("gold")
            p.barrierUnlocked = j.getBoolean("barrierUnlocked")
            p.barrier         = j.getInt("barrier")
            game.heroSpritePath = j.getString("heroSprite")

            val inv = j.getJSONArray("inventory")
            for (i in 0 until inv.length()) {
                val name = inv.getString(i)
                val type = try {
                    ItemType.valueOf(name)
                } catch (_: Exception) {
                    // compatibilité anciens saves : HEALTH_POTION → FOOD_MEDIUM
                    if (name == "HEALTH_POTION") ItemType.FOOD_MEDIUM else continue
                }
                p.inventory.add(Item(type, p.pos))
            }

            val eq = j.getJSONObject("equipped")
            for (slotName in eq.keys())
                p.equipped[EquipSlot.valueOf(slotName)] = SaveManager.equipFromJson(eq.getJSONObject(slotName))

            game.level = game.generateLevel(p.floor)
            p.pos = game.firstFloor(game.level)
            p.hp  = p.hp.coerceAtMost(p.totalMaxHp)
            game.computeFov()
            game.log.clear()
            game.log.addLast("Aventure reprise — Étage ${p.floor}.")
            return game
        }
    }

    // player créé en premier pour que generateLevel puisse utiliser ses stats
    var player: Player      = Player(Pos(0, 0))
    var level:  DungeonLevel = generateLevel(1)
    var phase:  GamePhase    = GamePhase.PLAYING
    val log = ArrayDeque<String>()
    var heroSpritePath: String = "Assets/sprites/Dungeon/Heros/paperdoll_example_%02d.png"
        .format(Random.nextInt(1, 30))

    var pendingEquipDrop: Equipment? = null

    var shopOpen = false
    val shopBought = mutableSetOf<ShopItem>()

    init {
        player.pos = firstFloor(level)
        computeFov()
        addLog("Tu descends dans le donjon…")
    }

    // ── Actions publiques ───────────────────────────────────────────────────────

    fun tryMove(dx: Int, dy: Int) {
        if (phase != GamePhase.PLAYING || shopOpen || pendingEquipDrop != null) return
        val nx = player.pos.x + dx
        val ny = player.pos.y + dy
        val m  = level.monsterAt(nx, ny)
        when {
            m != null              -> meleeMonster(m)
            level.walkable(nx, ny) -> { player.pos = Pos(nx, ny); checkPickup(); checkBarrierRegen() }
            else                   -> return
        }
        endTurn()
    }

    fun openShop() {
        if (onStairsTile()) shopOpen = true
    }

    fun buyShopItem(item: ShopItem): Boolean {
        if (item in shopBought || player.gold < item.cost) return false
        player.gold -= item.cost
        shopBought.add(item)
        when (item) {
            ShopItem.POTION  -> { player.maxHp += 10; player.hp = player.totalMaxHp; addLog("HP max +10 ! Soins complets !") }
            ShopItem.ATK_UP  -> { player.baseAtk++; addLog("Cristal de force. ATK +1 !") }
            ShopItem.BARRIER -> { player.barrierUnlocked = true; player.barrier = player.totalMaxHp / 5; addLog("Barrière débloquée !") }
        }
        return true
    }

    fun closeShopAndDescend() {
        shopOpen = false
        shopBought.clear()
        tryDescend()
    }

    fun tryDescend(): Boolean {
        if (level.tiles[player.pos.y][player.pos.x] != TileType.STAIRS_DOWN) return false
        player.floor++
        player.barrierStep = 0
        level  = generateLevel(player.floor)
        player.pos = firstFloor(level)
        computeFov()
        addLog("Étage ${player.floor} — les ténèbres s'approfondissent…")
        return true
    }

    fun useItem(index: Int) {
        if (phase != GamePhase.PLAYING) return
        val item = player.inventory.getOrNull(index) ?: return
        if (item.type.healAmount > 0) {
            val gain = item.type.healAmount.coerceAtMost(player.totalMaxHp - player.hp)
            player.hp = min(player.totalMaxHp, player.hp + item.type.healAmount)
            player.inventory.removeAt(index)
            addLog("${item.type.label} mangé. +$gain HP.")
        }
        endTurn()
    }

    fun equipPendingDrop() {
        val equip = pendingEquipDrop ?: return
        player.equipped[equip.slot] = equip
        player.hp = player.hp.coerceAtMost(player.totalMaxHp)
        pendingEquipDrop = null
        addLog("${equip.slot.label} équipé : ${equip.label} !")
    }

    fun ignorePendingDrop() {
        pendingEquipDrop = null
        addLog("Objet laissé au sol.")
    }

    fun onStairsTile() =
        level.tiles.getOrNull(player.pos.y)?.getOrNull(player.pos.x) == TileType.STAIRS_DOWN

    // ── Combat ──────────────────────────────────────────────────────────────────

    private fun meleeMonster(m: Monster) {
        val isCrit = Random.nextFloat() < player.critChance
        val base   = max(1, player.atk - m.type.baseDef + Random.nextInt(-1, 2))
        val dmg    = if (isCrit) (base * player.critDmgMult).toInt() else base
        m.hp -= dmg
        if (m.hp <= 0) {
            m.isAlive = false
            val gld = Random.nextInt(1, m.type.goldReward / 2 + 3)
            player.gold += gld
            if (isCrit)
                addLog("CRITIQUE ! ${m.type.label} -$dmg. +$gld or.")
            else
                addLog("${m.type.label} -$dmg. +$gld or.")
            maybeDrop(m)
        } else {
            val suffix = if (isCrit) " (CRIT)" else ""
            addLog("${m.type.label} -$dmg$suffix. (${m.hp}/${m.maxHp})")
        }
    }

    private fun meleePlayer(m: Monster) {
        if (Random.nextFloat() < player.evasionChance) {
            addLog("${m.type.label} rate ! (Esquive)")
            return
        }
        var dmg = max(1, m.scaledAtk - player.def + Random.nextInt(-1, 2))
        if (Random.nextFloat() < player.blockChance) {
            dmg = max(1, dmg / 2)
            addLog("${m.type.label} bloqué → -$dmg HP !")
        }
        if (player.barrier > 0) {
            val absorbed = min(player.barrier, dmg)
            player.barrier -= absorbed
            dmg -= absorbed
            if (dmg <= 0) {
                addLog("Barrière absorbe l'attaque. (${player.barrier}/${player.maxBarrier})")
                return
            }
        }
        player.hp -= dmg
        addLog("${m.type.label} frappe -$dmg ! HP ${player.hp}/${player.totalMaxHp}")
        if (player.hp <= 0) { player.hp = 0; phase = GamePhase.GAME_OVER; addLog("Tu es mort !") }
    }

    private fun checkBarrierRegen() {
        if (!player.barrierUnlocked || player.barrier >= player.maxBarrier || !level.hasAliveEnemies()) return
        player.barrierStep++
        if (player.barrierStep >= BARRIER_REGEN_STEPS) {
            player.barrierStep = 0
            player.barrier = min(player.maxBarrier, player.barrier + 1)
        }
    }

    private fun endTurn() {
        computeFov()
        if (phase == GamePhase.PLAYING) moveMonsters()
    }

    private fun moveMonsters() {
        for (m in level.monsters) {
            if (!m.isAlive || phase != GamePhase.PLAYING) continue
            if (!m.awake && level.visible[m.pos.y][m.pos.x]) m.awake = true
            if (!m.awake) continue
            if (m.pos.chebyshev(player.pos) <= 1) { meleePlayer(m); continue }
            val step = stepToward(m.pos, player.pos) ?: continue
            if (level.walkable(step.x, step.y) && level.monsterAt(step.x, step.y) == null)
                m.pos = step
        }
    }

    private fun stepToward(from: Pos, to: Pos): Pos? {
        val sx = (to.x - from.x).sign; val sy = (to.y - from.y).sign
        if (sx != 0 && sy != 0) { val p = Pos(from.x + sx, from.y + sy); if (level.walkable(p.x, p.y)) return p }
        if (sx != 0)             { val p = Pos(from.x + sx, from.y);      if (level.walkable(p.x, p.y)) return p }
        if (sy != 0)             { val p = Pos(from.x, from.y + sy);      if (level.walkable(p.x, p.y)) return p }
        return null
    }

    private fun checkPickup() {
        val px = player.pos.x; val py = player.pos.y

        val here = level.items.filter { it.pos.x == px && it.pos.y == py }
        for (it in here) {
            when {
                it.type == ItemType.GOLD -> {
                    val gain = Random.nextInt(3, 12); player.gold += gain
                    level.items.remove(it); addLog("+$gain or !")
                }
                it.type.healAmount > 0 -> {
                    if (player.inventory.size < MAX_INV) {
                        player.inventory.add(it); level.items.remove(it)
                        addLog("${it.type.label} ramassé.")
                    } else addLog("Inventaire plein !")
                }
            }
        }

        val drop = level.equipDropAt(px, py)
        if (drop != null && pendingEquipDrop == null) {
            level.equipDrops.remove(drop)
            pendingEquipDrop = drop.first
        }
    }

    private fun maybeDrop(m: Monster) {
        // 8% chance de lâcher de la nourriture en mourant
        if (Random.nextFloat() < 0.08f) {
            val foodType = when (Random.nextFloat()) {
                in 0f..0.5f  -> ItemType.FOOD_SMALL
                in 0.5f..0.85f -> ItemType.FOOD_MEDIUM
                else           -> ItemType.FOOD_LARGE
            }
            level.items.add(Item(foodType, m.pos))
            return
        }
        val equip = LootSystem.tryDrop(player.floor) ?: return
        level.equipDrops.add(equip to m.pos)
    }

    // ── Calcul stats mobs ───────────────────────────────────────────────────────

    private fun computeMobStats(type: MonsterType): Pair<Int, Int> {
        val p = player
        // HP : joueur tue en ~5 coups
        val effectiveDmg = max(1, p.atk - type.baseDef).toFloat()
        val scaledHp = (5f * effectiveDmg * type.hpMult).roundToInt().coerceAtLeast(3)

        // ATK brute : mob inflige totalMaxHp/20 de dégâts nets après esquive/blocage
        val netDmgPerHit = p.totalMaxHp / 20f
        val evadeMult    = (1f - p.evasionChance).coerceAtLeast(0.25f)
        val blockMult    = (1f - p.blockChance * 0.5f).coerceAtLeast(0.5f)
        val grossAtk     = netDmgPerHit / (evadeMult * blockMult) + p.def
        val scaledAtk    = (grossAtk * type.atkMult).roundToInt().coerceAtLeast(1)

        return scaledHp to scaledAtk
    }

    // ── FOV ─────────────────────────────────────────────────────────────────────

    fun computeFov() {
        val lv = level
        for (y in 0 until lv.h) lv.visible[y].fill(false)
        val px = player.pos.x; val py = player.pos.y
        for (ty in maxOf(0, py - FOV_RADIUS)..minOf(lv.h - 1, py + FOV_RADIUS))
            for (tx in maxOf(0, px - FOV_RADIUS)..minOf(lv.w - 1, px + FOV_RADIUS)) {
                if (max(abs(tx - px), abs(ty - py)) > FOV_RADIUS) continue
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
            if (lv.tiles[y][x] == TileType.WALL) return false
        }
        return true
    }

    // ── Génération de niveau ────────────────────────────────────────────────────

    private fun generateLevel(floor: Int): DungeonLevel {
        val lv    = DungeonLevel(MAP_W, MAP_H, floor)
        val rooms = mutableListOf<Room>()

        repeat(80) {
            val rw = Random.nextInt(5, 13); val rh = Random.nextInt(4, 9)
            val rx = Random.nextInt(1, MAP_W - rw - 1); val ry = Random.nextInt(1, MAP_H - rh - 1)
            val room = Room(rx, ry, rw, rh)
            if (rooms.none { it.overlaps(room) }) {
                rooms.add(room)
                for (cy in ry until ry + rh) for (cx in rx until rx + rw) lv.tiles[cy][cx] = TileType.FLOOR
            }
        }

        if (rooms.isEmpty()) {
            rooms.add(Room(3, 3, 15, 10))
            for (cy in 3..12) for (cx in 3..17) lv.tiles[cy][cx] = TileType.FLOOR
        }

        val shuffled = rooms.shuffled()
        for (i in 0 until shuffled.size - 1) carveCorridor(lv, shuffled[i].center(), shuffled[i + 1].center())

        val stairPos = shuffled.last().randomInner()
        lv.tiles[stairPos.y][stairPos.x] = TileType.STAIRS_DOWN

        // Monstres : stats scalées selon les stats actuelles du joueur
        val eligible = MonsterType.values().filter { it.minFloor <= floor }
        val mobCount = 6 + floor * 4
        repeat(mobCount) {
            val room = shuffled.drop(1).randomOrNull() ?: shuffled.first()
            val pos  = room.randomInner()
            if (lv.tiles[pos.y][pos.x] == TileType.FLOOR && lv.monsterAt(pos.x, pos.y) == null) {
                val type = eligible.random()
                val (sHp, sAtk) = computeMobStats(type)
                lv.monsters.add(Monster(type, pos, sHp, sAtk))
            }
        }

        // Food : quantité calculée pour couvrir 80% des dégâts de l'étage
        val avgMobAtk = if (lv.monsters.isNotEmpty())
            lv.monsters.map { it.scaledAtk }.average().toFloat()
        else 5f
        val totalExpectedDmg = avgMobAtk * lv.monsters.size * 0.8f
        val foodCount = (totalExpectedDmg / AVG_FOOD_HEAL).roundToInt().coerceIn(3, 25)

        repeat(foodCount) {
            val room = shuffled.randomOrNull() ?: return@repeat
            val pos  = room.randomInner()
            if (lv.tiles[pos.y][pos.x] == TileType.FLOOR && lv.itemAt(pos.x, pos.y) == null) {
                val roll = Random.nextFloat()
                val foodType = when {
                    roll < 0.50f -> ItemType.FOOD_SMALL
                    roll < 0.85f -> ItemType.FOOD_MEDIUM
                    else         -> ItemType.FOOD_LARGE
                }
                lv.items.add(Item(foodType, pos))
            }
        }

        // Or au sol
        val goldCount = 3 + Random.nextInt(4)
        repeat(goldCount) {
            val room = shuffled.randomOrNull() ?: return@repeat
            val pos  = room.randomInner()
            if (lv.tiles[pos.y][pos.x] == TileType.FLOOR && lv.itemAt(pos.x, pos.y) == null)
                lv.items.add(Item(ItemType.GOLD, pos))
        }

        return lv
    }

    private fun firstFloor(lv: DungeonLevel): Pos {
        for (y in 0 until lv.h) for (x in 0 until lv.w)
            if (lv.tiles[y][x] == TileType.FLOOR) return Pos(x, y)
        return Pos(1, 1)
    }

    private fun carveCorridor(lv: DungeonLevel, a: Pos, b: Pos) {
        var cx = a.x; var cy = a.y
        if (Random.nextBoolean()) {
            while (cx != b.x) { lv.tiles[cy][cx] = TileType.FLOOR; cx += (b.x - cx).sign }
            while (cy != b.y) { lv.tiles[cy][cx] = TileType.FLOOR; cy += (b.y - cy).sign }
        } else {
            while (cy != b.y) { lv.tiles[cy][cx] = TileType.FLOOR; cy += (b.y - cy).sign }
            while (cx != b.x) { lv.tiles[cy][cx] = TileType.FLOOR; cx += (b.x - cx).sign }
        }
        lv.tiles[cy][cx] = TileType.FLOOR
    }

    private fun addLog(msg: String) {
        if (log.size >= 6) log.removeFirst()
        log.addLast(msg)
    }

    // ── Sérialisation ────────────────────────────────────────────────────────────

    fun toJson(): JSONObject {
        val p = player
        return JSONObject().apply {
            put("floor",           p.floor)
            put("hp",              p.hp)
            put("maxHp",           p.maxHp)
            put("baseAtk",         p.baseAtk)
            put("baseDef",         p.baseDef)
            put("gold",            p.gold)
            put("barrierUnlocked", p.barrierUnlocked)
            put("barrier",         p.barrier)
            put("heroSprite",      heroSpritePath)
            put("inventory", JSONArray().also { arr ->
                for (item in p.inventory) arr.put(item.type.name)
            })
            put("equipped", JSONObject().also { eq ->
                for ((slot, equip) in p.equipped) eq.put(slot.name, SaveManager.equipToJson(equip))
            })
        }
    }
}
