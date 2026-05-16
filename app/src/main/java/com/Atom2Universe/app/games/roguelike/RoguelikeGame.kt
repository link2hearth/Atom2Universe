package com.Atom2Universe.app.games.roguelike

import kotlin.math.*
import kotlin.random.Random

// ─── Tile ──────────────────────────────────────────────────────────────────────
enum class TileType { WALL, FLOOR, STAIRS_DOWN }

// ─── Position ──────────────────────────────────────────────────────────────────
data class Pos(val x: Int, val y: Int) {
    fun chebyshev(other: Pos) = max(abs(x - other.x), abs(y - other.y))
    fun manhattan(other: Pos) = abs(x - other.x) + abs(y - other.y)
}

// ─── Items ──────────────────────────────────────────────────────────────────────
enum class ItemType(val symbol: Char, val label: String, val colorArgb: Int) {
    HEALTH_POTION('♥', "Health Potion", 0xFFE53935.toInt()),
    SWORD('/', "Sword",               0xFFB0BEC5.toInt()),
    SHIELD(')', "Shield",             0xFF78909C.toInt()),
    GOLD('$', "Gold",                 0xFFFFD600.toInt())
}

data class Item(val type: ItemType, var pos: Pos)

// ─── Monsters ───────────────────────────────────────────────────────────────────
enum class MonsterType(
    val symbol: Char,
    val label: String,
    val colorArgb: Int,
    val baseHp: Int,
    val baseAtk: Int,
    val baseDef: Int,
    val goldReward: Int,
    val minFloor: Int
) {
    RAT     ('r', "Rat",     0xFF8D6E63.toInt(),  6,  2, 0,  2, 1),
    GOBLIN  ('g', "Goblin",  0xFF66BB6A.toInt(), 12,  4, 1,  5, 1),
    SKELETON('S', "Skeleton",0xFFECEFF1.toInt(), 16,  5, 2,  8, 2),
    ORC     ('O', "Orc",     0xFF4CAF50.toInt(), 22,  7, 3, 12, 3),
    DEMON   ('D', "Demon",   0xFFEF5350.toInt(), 30, 10, 4, 18, 4)
}

class Monster(
    val type: MonsterType,
    var pos: Pos,
    var hp: Int = type.baseHp
) {
    val maxHp: Int = type.baseHp
    var isAlive = true
    var awake = false
}

// ─── Player ─────────────────────────────────────────────────────────────────────
class Player(startPos: Pos) {
    var pos = startPos
    var hp = 30; var maxHp = 30
    var baseAtk = 5; var baseDef = 2
    var gold = 0; var floor = 1
    val inventory = mutableListOf<Item>()
    var equippedWeapon: Item? = null
    var equippedShield: Item? = null

    // Barrière : absorbe les dégâts avant les HP, débloquée via le shop
    var barrier = 0
    var barrierUnlocked = false
    val maxBarrier get() = if (barrierUnlocked) maxHp / 5 else 0
    var barrierStep = 0

    val atk get() = baseAtk + floor + if (equippedWeapon?.type == ItemType.SWORD) 3 else 0
    val def get() = baseDef + if (equippedShield?.type == ItemType.SHIELD) 2 else 0
}

// ─── Shop ───────────────────────────────────────────────────────────────────────
enum class ShopItem(val cost: Int) {
    POTION(20),
    ATK_UP(25),
    BARRIER(20)
}

// ─── Dungeon level ──────────────────────────────────────────────────────────────
class DungeonLevel(val w: Int, val h: Int, val floor: Int) {
    val tiles    = Array(h) { Array(w) { TileType.WALL } }
    val monsters = mutableListOf<Monster>()
    val items    = mutableListOf<Item>()
    val visible  = Array(h) { BooleanArray(w) }
    val explored = Array(h) { BooleanArray(w) }
    val theme    = DungeonTheme.ALL.random()

    fun inBounds(x: Int, y: Int) = x in 0 until w && y in 0 until h
    fun walkable(x: Int, y: Int) = inBounds(x, y) && tiles[y][x] != TileType.WALL
    fun monsterAt(x: Int, y: Int) = monsters.find { it.isAlive && it.pos.x == x && it.pos.y == y }
    fun itemAt(x: Int, y: Int)    = items.find { it.pos.x == x && it.pos.y == y }
    fun hasAliveEnemies()         = monsters.any { it.isAlive }
}

// ─── Rect helper ───────────────────────────────────────────────────────────────
private data class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun center() = Pos(x + w / 2, y + h / 2)
    fun overlaps(o: Room) = x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y
    fun randomInner() = Pos(x + 1 + Random.nextInt(maxOf(1, w - 2)), y + 1 + Random.nextInt(maxOf(1, h - 2)))
}

// ─── Game phases ────────────────────────────────────────────────────────────────
enum class GamePhase { PLAYING, GAME_OVER, VICTORY }

// ─── Main game engine ────────────────────────────────────────────────────────────
class RoguelikeGame {

    companion object {
        const val MAP_W = 40
        const val MAP_H = 25
        const val MAX_FLOORS = 5
        const val FOV_RADIUS = 8
        const val MAX_INV = 5
        const val BARRIER_REGEN_STEPS = 8  // pas nécessaires pour +1 barrière
    }

    var level: DungeonLevel = generateLevel(1)
    var player: Player = Player(firstFloor(level))
    var phase: GamePhase = GamePhase.PLAYING
    val log = ArrayDeque<String>()
    val heroSpritePath: String = "Assets/sprites/Dungeon/Heros/paperdoll_example_%02d.png"
        .format(Random.nextInt(1, 30))

    // ── Shop state ──────────────────────────────────────────────────────────────
    var shopOpen = false
    val shopBought = mutableSetOf<ShopItem>()

    init { computeFov(); addLog("You descend into the dungeon…") }

    // ── Public actions ──────────────────────────────────────────────────────────

    fun tryMove(dx: Int, dy: Int) {
        if (phase != GamePhase.PLAYING || shopOpen) return
        val nx = player.pos.x + dx
        val ny = player.pos.y + dy
        val m = level.monsterAt(nx, ny)
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
            ShopItem.POTION -> {
                player.maxHp += 10
                player.hp = player.maxHp
                addLog("Max HP +10! Soins complets!")
            }
            ShopItem.ATK_UP -> {
                player.baseAtk++
                addLog("Power crystal. ATK +1!")
            }
            ShopItem.BARRIER -> {
                player.barrierUnlocked = true
                player.barrier = player.maxHp / 5
                addLog("Barrier unlocked!")
            }
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
        if (player.floor >= MAX_FLOORS) {
            phase = GamePhase.VICTORY
            addLog("You escaped the dungeon! Victory!")
            return true
        }
        player.floor++
        player.barrierStep = 0
        level = generateLevel(player.floor)
        player.pos = firstFloor(level)
        computeFov()
        addLog("Floor ${player.floor} — deeper darkness…")
        return true
    }

    fun useItem(index: Int) {
        if (phase != GamePhase.PLAYING) return
        val item = player.inventory.getOrNull(index) ?: return
        when (item.type) {
            ItemType.HEALTH_POTION -> {
                val gain = player.maxHp - player.hp
                player.hp = player.maxHp
                player.inventory.removeAt(index)
                addLog("Potion bue. +$gain HP. Soins complets!")
            }
            ItemType.SWORD -> {
                player.equippedWeapon = item
                player.inventory.removeAt(index)
                addLog("Sword equipped (+3 ATK).")
            }
            ItemType.SHIELD -> {
                player.equippedShield = item
                player.inventory.removeAt(index)
                addLog("Shield equipped (+2 DEF).")
            }
            ItemType.GOLD -> { /* auto-collected */ }
        }
        endTurn()
    }

    fun onStairsTile() = level.tiles.getOrNull(player.pos.y)?.getOrNull(player.pos.x) == TileType.STAIRS_DOWN

    // ── Private ─────────────────────────────────────────────────────────────────

    private fun meleeMonster(m: Monster) {
        val dmg = max(1, player.atk - m.type.baseDef + Random.nextInt(-1, 2))
        m.hp -= dmg
        if (m.hp <= 0) {
            m.isAlive = false
            val gld = Random.nextInt(1, m.type.goldReward / 2 + 3)
            player.gold += gld
            addLog("${m.type.label} slain! +$gld gold.")
            maybeDrop(m)
        } else {
            addLog("Hit ${m.type.label} for $dmg. (${m.hp}/${m.maxHp})")
        }
    }

    private fun meleePlayer(m: Monster) {
        var dmg = max(1, m.type.baseAtk - player.def + Random.nextInt(-1, 2))
        if (player.barrier > 0) {
            val absorbed = min(player.barrier, dmg)
            player.barrier -= absorbed
            dmg -= absorbed
            if (dmg <= 0) {
                addLog("Barrier absorbed ${m.type.label}'s hit! (${player.barrier}/${player.maxBarrier})")
                return
            }
        }
        player.hp -= dmg
        addLog("${m.type.label} hits for $dmg! HP ${player.hp}/${player.maxHp}")
        if (player.hp <= 0) { player.hp = 0; phase = GamePhase.GAME_OVER; addLog("You died!") }
    }

    private fun checkBarrierRegen() {
        if (!player.barrierUnlocked) return
        if (player.barrier >= player.maxBarrier) return
        if (!level.hasAliveEnemies()) return
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
        val sx = (to.x - from.x).sign
        val sy = (to.y - from.y).sign
        val candidates = buildList {
            if (sx != 0 && sy != 0) add(Pos(from.x + sx, from.y + sy))
            if (sx != 0) add(Pos(from.x + sx, from.y))
            if (sy != 0) add(Pos(from.x, from.y + sy))
        }
        return candidates.firstOrNull { level.walkable(it.x, it.y) }
    }

    private fun checkPickup() {
        val here = level.items.filter { it.pos.x == player.pos.x && it.pos.y == player.pos.y }
        if (here.isEmpty()) return
        for (it in here) {
            if (it.type == ItemType.GOLD) {
                val gain = Random.nextInt(3, 12)
                player.gold += gain
                level.items.remove(it)
                addLog("+$gain gold!")
            } else if (player.inventory.size < MAX_INV) {
                player.inventory.add(it)
                level.items.remove(it)
                addLog("Ramassé : ${it.type.label}.")
            } else {
                addLog("Inventaire plein !")
            }
        }
    }

    private fun maybeDrop(m: Monster) {
        if (Random.nextFloat() > 0.45f) return
        val pool = when {
            m.type.minFloor >= 3 -> listOf(ItemType.SWORD, ItemType.SHIELD, ItemType.HEALTH_POTION, ItemType.GOLD)
            else                 -> listOf(ItemType.HEALTH_POTION, ItemType.GOLD, ItemType.GOLD)
        }
        level.items.add(Item(pool.random(), m.pos))
    }

    // ── FOV ────────────────────────────────────────────────────────────────────

    fun computeFov() {
        val lv = level
        for (y in 0 until lv.h) lv.visible[y].fill(false)
        val px = player.pos.x; val py = player.pos.y
        for (ty in maxOf(0, py - FOV_RADIUS)..minOf(lv.h - 1, py + FOV_RADIUS)) {
            for (tx in maxOf(0, px - FOV_RADIUS)..minOf(lv.w - 1, px + FOV_RADIUS)) {
                if (Pos(tx, ty).chebyshev(player.pos) > FOV_RADIUS) continue
                if (los(px, py, tx, ty, lv)) {
                    lv.visible[ty][tx] = true
                    lv.explored[ty][tx] = true
                }
            }
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

    // ── Level generation ────────────────────────────────────────────────────────

    private fun generateLevel(floor: Int): DungeonLevel {
        val lv = DungeonLevel(MAP_W, MAP_H, floor)
        val rooms = mutableListOf<Room>()

        repeat(80) {
            val rw = Random.nextInt(5, 13)
            val rh = Random.nextInt(4, 9)
            val rx = Random.nextInt(1, MAP_W - rw - 1)
            val ry = Random.nextInt(1, MAP_H - rh - 1)
            val room = Room(rx, ry, rw, rh)
            if (rooms.none { it.overlaps(room) }) {
                rooms.add(room)
                for (cy in ry until ry + rh)
                    for (cx in rx until rx + rw)
                        lv.tiles[cy][cx] = TileType.FLOOR
            }
        }

        if (rooms.isEmpty()) {
            rooms.add(Room(3, 3, 15, 10))
            for (cy in 3..12) for (cx in 3..17) lv.tiles[cy][cx] = TileType.FLOOR
        }

        val shuffled = rooms.shuffled()
        for (i in 0 until shuffled.size - 1)
            carveCorridor(lv, shuffled[i].center(), shuffled[i + 1].center())

        val stairPos = shuffled.last().randomInner()
        lv.tiles[stairPos.y][stairPos.x] = TileType.STAIRS_DOWN

        val eligible = MonsterType.values().filter { it.minFloor <= floor }
        repeat(6 + floor * 4) {
            val room = shuffled.drop(1).randomOrNull() ?: shuffled.first()
            val pos = room.randomInner()
            if (lv.tiles[pos.y][pos.x] == TileType.FLOOR && lv.monsterAt(pos.x, pos.y) == null)
                lv.monsters.add(Monster(eligible.random(), pos))
        }

        val itemCount = (4 + Random.nextInt(5)) + maxOf(0, (floor - 3) * 2)
        val potionProb = minOf(0.8f, 0.3f + floor * 0.1f)
        repeat(itemCount) {
            val room = shuffled.randomOrNull() ?: return@repeat
            val pos = room.randomInner()
            if (lv.tiles[pos.y][pos.x] == TileType.FLOOR && lv.itemAt(pos.x, pos.y) == null) {
                val type = if (Random.nextFloat() < potionProb) ItemType.HEALTH_POTION else ItemType.GOLD
                lv.items.add(Item(type, pos))
            }
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
}
