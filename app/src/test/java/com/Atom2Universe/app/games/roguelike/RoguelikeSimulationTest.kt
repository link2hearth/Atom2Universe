package com.Atom2Universe.app.games.roguelike

import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Banc de mesure du Donjon : des bots jouent de vraies parties (carte + combats) avec le
 * moteur du jeu, et on regarde jusqu'où ils vont. Aucune assertion.
 *
 * Les bots ne trichent pas : ils ne connaissent que les cases explorées et les monstres
 * visibles. Ils jouent tous pareil sur la carte (explorer, se reposer hors poursuite,
 * acheter des potions) ; seule leur précision au timing change :
 *  - NOVICE  : rate presque toutes ses parades et ses frappes ;
 *  - CORRECT : réussit environ une parade sur deux ;
 *  - EXPERT  : pare presque tout, souvent parfaitement.
 */
class RoguelikeSimulationTest {

    enum class Skill(val parryGood: Float, val parryPerfect: Float, val strikeGood: Float, val strikePerfect: Float) {
        NOVICE (0.15f, 0.03f, 0.20f, 0.05f),
        CORRECT(0.40f, 0.15f, 0.40f, 0.15f),
        EXPERT (0.45f, 0.45f, 0.45f, 0.40f),
    }

    private val profiles = 60
    private val maxFloor = 15
    private val maxMapTurns = 40_000

    class FloorStat {
        var entries = 0; var deaths = 0; var fights = 0; var chains = 0; var ambushes = 0
        var dmgPct = 0.0; var potionsUsed = 0; var turnsInFight = 0
        var groupSizes = 0
    }

    class Report(val skill: Skill) {
        val floors = sortedMapOf<Int, FloorStat>()
        val bestFloors = mutableListOf<Int>()
        val deathsBeforeFloor = mutableMapOf<Int, MutableList<Int>>()   // étage -> morts cumulées avant d'y arriver
        var timeouts = 0
        fun f(n: Int) = floors.getOrPut(n) { FloorStat() }
    }

    @Test
    fun simulate() {
        val out = StringBuilder()
        for (skill in Skill.entries) {
            val r = Report(skill)
            repeat(profiles) { i -> playProfile(skill, r, Random(i * 7919L + skill.ordinal)) }
            out.append(format(r))
        }
        File("build/roguelike-sim.txt").writeText(out.toString())
        println(out)
    }

    /**
     * Même équipement pour tous (le meilleur de 15 objets de l'étage), PV pleins :
     * seul le skill change. On mesure un combat isolé, puis deux combats enchaînés.
     */
    @Test
    fun skillAtFixedGear() {
        val out = StringBuilder("══════ Skill à équipement égal (2000 combats par case) ══════\n")
        out.appendLine("Ét. | skill   | victoire seul | dégâts seul (% PV) | victoire 2 enchaînés | victoire 3 enchaînés")
        for (floor in listOf(1, 3, 5, 8, 12)) {
            for (skill in Skill.entries) {
                val rng = Random(floor * 31L)
                var win1 = 0; var dmg1 = 0.0; var win2 = 0; var win3 = 0
                repeat(2000) {
                    val hero = geared(floor, rng)
                    val hp0 = hero.maxHp
                    val ok1 = soloFight(hero, floor, skill, rng)
                    if (ok1) { win1++; dmg1 += (hp0 - hero.hp).toDouble() / hp0 }
                    val ok2 = ok1 && soloFight(hero, floor, skill, rng)
                    if (ok2) win2++
                    if (ok2 && soloFight(hero, floor, skill, rng)) win3++
                }
                out.appendLine(String.format("%3d | %-7s | %5.1f%% | %5.1f%% | %5.1f%% | %5.1f%%",
                    floor, skill, win1 / 20.0, 100 * dmg1 / win1.coerceAtLeast(1), win2 / 20.0, win3 / 20.0))
            }
        }
        File("build/roguelike-skill.txt").writeText(out.toString())
        println(out)
    }

    private fun geared(floor: Int, rng: Random): Hero {
        val hero = Hero()
        repeat(15) {
            val e = LootSystem.generate(floor, rng)
            val cur = hero.equipped[e.slot]
            if (cur == null || score(e) > score(cur)) hero.equipped[e.slot] = e
        }
        hero.potions = 2
        hero.healFull()
        return hero
    }

    private fun soloFight(hero: Hero, floor: Int, skill: Skill, rng: Random): Boolean {
        val c = Combat(hero, floor, Encounters.build(Encounters.roll(floor, rng), floor), ambush = false, rng = rng)
        val fs = FloorStat()
        playCombat(c, skill, fs, rng)
        return c.phase == CombatPhase.VICTORY
    }

    // ── Un profil de joueur : il rejoue après chaque mort, avec son équipement ──

    private fun playProfile(skill: Skill, r: Report, rng: Random) {
        val g = RoguelikeGame(rng = rng)
        var best = 1
        var deaths = 0
        var turns = 0
        var lastFloor = 0
        val reached = mutableSetOf<Int>()

        while (g.floor <= maxFloor && turns++ < maxMapTurns) {
            if (g.floor != lastFloor) {
                lastFloor = g.floor
                r.f(g.floor).entries++
                if (reached.add(g.floor)) r.deathsBeforeFloor.getOrPut(g.floor) { mutableListOf() } += deaths
                best = maxOf(best, g.floor)
            }
            when {
                g.combat != null -> if (fight(g, skill, r, rng)) deaths++
                g.deathReport != null -> g.dismissDeath()
                g.pendingEquipDrop != null -> {
                    val e = g.pendingEquipDrop!!
                    val cur = g.hero.equipped[e.slot]
                    if (cur == null || score(e) > score(cur)) g.equipPendingDrop() else g.ignorePendingDrop()
                }
                g.merchantOpen -> {
                    while (g.hero.potions < 3 && g.buyPotion()) {}
                    g.descend()
                }
                else -> mapStep(g)
            }
        }
        if (turns >= maxMapTurns) r.timeouts++
        r.bestFloors += best
    }

    /** Joue un combat entier. Renvoie true si le héros est mort. */
    private fun fight(g: RoguelikeGame, skill: Skill, r: Report, rng: Random): Boolean {
        val c = g.combat!!
        val fs = r.f(g.floor)
        fs.fights++
        fs.groupSizes += c.enemies.size
        if (c.phase == CombatPhase.ENEMY_TURN) fs.ambushes++
        val hpStart = c.hero.hp
        playCombat(c, skill, fs, rng)
        val died = c.phase == CombatPhase.DEFEAT
        fs.dmgPct += (hpStart - if (died) 0 else c.hero.hp).coerceAtLeast(0).toDouble() / c.hero.maxHp
        if (died) fs.deaths++
        val floorBefore = g.floor
        g.finishCombat()
        if (!died && g.combat != null) r.f(floorBefore).chains++
        return died
    }

    private fun playCombat(c: Combat, skill: Skill, fs: FloorStat, rng: Random) {
        var turns = 0
        while (c.phase == CombatPhase.PLAYER_TURN || c.phase == CombatPhase.ENEMY_TURN) {
            if (c.phase == CombatPhase.PLAYER_TURN) {
                turns++
                val alive = c.aliveIndices()
                // Cible : le plus proche d'attaquer, puis le plus faible
                val target = alive.minWith(compareBy<Int>({ c.enemies[it].countdown }, { c.enemies[it].hp }))
                val incoming = alive.filter { c.enemies[it].countdown <= 1 }.sumOf { c.enemies[it].damage }
                when {
                    c.canDrinkPotion() && c.hero.hp <= incoming * 1.3f + c.hero.maxHp * 0.1f -> { c.drinkPotion(); fs.potionsUsed++ }
                    c.canCast(Relic.FIREBALL) -> c.castRelic(Relic.FIREBALL, alive.maxBy { c.enemies[it].hp }, strike(skill, rng))
                    else -> c.attack(target, strike(skill, rng))
                }
            } else {
                val (_, attackers) = c.startEnemyTurn()
                for (a in attackers) {
                    if (c.phase != CombatPhase.ENEMY_TURN) break
                    c.resolveStrike(a, parry(skill, rng))
                }
                c.endEnemyTurn()
            }
        }
        fs.turnsInFight += turns
    }

    private fun strike(s: Skill, rng: Random): Timing {
        val x = rng.nextFloat()
        return when { x < s.strikePerfect -> Timing.PERFECT; x < s.strikePerfect + s.strikeGood -> Timing.GOOD; else -> Timing.MISS }
    }

    private fun parry(s: Skill, rng: Random): Timing {
        val x = rng.nextFloat()
        return when { x < s.parryPerfect -> Timing.PERFECT; x < s.parryPerfect + s.parryGood -> Timing.GOOD; else -> Timing.MISS }
    }

    // ── Carte : ce qu'un joueur raisonnable ferait avec ce qu'il voit ───────────

    private fun mapStep(g: RoguelikeGame) {
        val lv = g.level
        val hero = g.hero
        val visiblePacks = lv.packs.filter { it.alive && lv.visible[it.pos.y][it.pos.x] }

        // Poursuivi : on prend l'initiative plutôt que de se faire surprendre
        if (g.isChased) {
            val chaser = visiblePacks.filter { it.state == PackState.CHASING }.minByOrNull { it.pos.chebyshev(g.playerPos) }
            if (chaser != null && moveToward(g, listOf(chaser.pos), avoidPacks = false)) return
        }
        // Souffler dès qu'on peut
        if (g.canRest() && hero.hp < hero.maxHp * 0.9f) { g.rest(); return }

        // Explorer ce qui ne l'est pas, puis l'escalier
        val front = frontier(g)
        if (g.onStairsTile() && front.isEmpty()) { g.openMerchant(); return }
        val goals = front.ifEmpty { stairsKnown(g) }
        if (goals.isEmpty()) { g.openMerchant(); return }
        if (!moveToward(g, goals, avoidPacks = true) && !moveToward(g, goals, avoidPacks = false)) {
            // Rien d'atteignable : on file à l'escalier s'il est connu
            val stairs = stairsKnown(g)
            if (g.onStairsTile()) g.openMerchant() else if (stairs.isNotEmpty()) moveToward(g, stairs, avoidPacks = false)
        }
    }

    private fun stairsKnown(g: RoguelikeGame): List<Pos> {
        val lv = g.level
        for (y in 0 until lv.h) for (x in 0 until lv.w)
            if (lv.explored[y][x] && lv.tiles[y][x] == TileType.STAIRS_DOWN) return listOf(Pos(x, y))
        return emptyList()
    }

    /** Cases explorées praticables qui touchent de l'inconnu. */
    private fun frontier(g: RoguelikeGame): List<Pos> {
        val lv = g.level
        val out = mutableListOf<Pos>()
        for (y in 0 until lv.h) for (x in 0 until lv.w) {
            if (!lv.explored[y][x] || lv.tiles[y][x] == TileType.WALL) continue
            var touches = false
            for (dy in -1..1) for (dx in -1..1) if (lv.inBounds(x + dx, y + dy) && !lv.explored[y + dy][x + dx]) touches = true
            if (touches && Pos(x, y) != g.playerPos) out += Pos(x, y)
        }
        return out
    }

    private fun moveToward(g: RoguelikeGame, goals: List<Pos>, avoidPacks: Boolean): Boolean {
        val lv = g.level; val start = g.playerPos
        val goalSet = goals.toHashSet()
        val danger = if (avoidPacks) lv.packs.filter { it.alive && lv.visible[it.pos.y][it.pos.x] }.map { it.pos } else emptyList()
        val prev = HashMap<Pos, Pos>()
        val q = ArrayDeque<Pos>(); q.add(start); prev[start] = start
        while (q.isNotEmpty()) {
            val c = q.removeFirst()
            if (c in goalSet && c != start) {
                var n = c
                while (prev[n] != start) n = prev[n]!!
                g.tryMove(n.x - start.x, n.y - start.y)
                return true
            }
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val n = Pos(c.x + dx, c.y + dy)
                if (n in prev || !lv.canStep(c, dx, dy) || !lv.explored[n.y][n.x]) continue
                if (n !in goalSet && danger.any { it.chebyshev(n) <= 1 }) continue
                prev[n] = c; q.add(n)
            }
        }
        return false
    }

    private fun score(e: Equipment) = e.stats.sumOf {
        when (it.type) {
            StatType.WEAPON_DMG -> 3.0 * it.value; StatType.ARMOR -> 1.5 * it.value; StatType.MAX_HP -> 0.5 * it.value
            StatType.STR -> 1.5 * it.value; StatType.CON -> 2.0 * it.value; StatType.DEX -> 1.2 * it.value
            StatType.INT -> 0.8 * it.value; StatType.WIS -> 0.3 * it.value; StatType.CHA -> 0.2 * it.value
            StatType.SPELL_DMG -> 8.0 * it.value
        }
    }

    // ── Rapport ─────────────────────────────────────────────────────────────────

    private fun format(r: Report): String = buildString {
        appendLine("══════ ${r.skill} ($profiles profils, max étage $maxFloor, $maxMapTurns tours de carte) ══════")
        val b = r.bestFloors.sorted()
        appendLine("Meilleur étage : médiane ${b[b.size / 2]}, min ${b.first()}, max ${b.last()}   blocages : ${r.timeouts}")
        appendLine("Morts avant d'atteindre l'étage (médiane, profils arrivés) : " +
            r.deathsBeforeFloor.toSortedMap().entries.joinToString { (fl, l) -> val s = l.sorted(); "$fl:${s[s.size / 2]} (${s.size})" })
        appendLine("Ét. | passages | combats | ennemis/combat | embuscades | enchaînés | dégâts/combat (% PV max) | tours/combat | potions/combat | morts")
        for ((fl, f) in r.floors) {
            val n = f.fights.coerceAtLeast(1).toDouble()
            appendLine(String.format("%3d | %5d | %6d | %4.2f | %4.0f%% | %4.0f%% | %5.1f%% | %4.1f | %5.2f | %d",
                fl, f.entries, f.fights, f.groupSizes / n, 100 * f.ambushes / n, 100 * f.chains / n,
                100 * f.dmgPct / n, f.turnsInFight / n, f.potionsUsed / n, f.deaths))
        }
        appendLine()
    }
}
