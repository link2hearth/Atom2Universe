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

    private val profiles = 30
    /**
     * Réglages de mesure par variables d'environnement, sans toucher au code :
     * SIM_MAX_FLOOR=100 pour aller au bout, SIM_RELICS=NONE (aucun sort) ou
     * SIM_RELICS=FIREBALL,VENOM pour n'autoriser que certaines reliques.
     * Gradle ne relance pas un test déjà passé : ajouter cleanTestDebugUnitTest.
     */
    private val simRelics = System.getenv("SIM_RELICS")?.split(",")
    private val maxFloor = System.getenv("SIM_MAX_FLOOR")?.toInt() ?: 40
    private val maxMapTurns = 250_000

    class FloorStat {
        var entries = 0; var deaths = 0; var fights = 0; var chains = 0; var ambushes = 0
        var dmgPct = 0.0; var potionsUsed = 0; var turnsInFight = 0
        var groupSizes = 0; var mapTurns = 0
        /** Combats arrêtés à [MAX_FIGHT_TURNS] tours : un combat qui ne finit pas est un bug. */
        var stuck = 0
    }

    companion object {
        /** Un vrai combat dure une dizaine de tours ; au-delà de ça, quelque chose tourne en rond. */
        const val MAX_FIGHT_TURNS = 300
    }

    class Report(val skill: Skill) {
        val floors = sortedMapOf<Int, FloorStat>()
        val bestFloors = mutableListOf<Int>()
        val deathsBeforeFloor = mutableMapOf<Int, MutableList<Int>>()   // étage -> morts cumulées avant d'y arriver
        val gearOnArrival = mutableMapOf<Int, MutableList<Int>>()       // étage -> note totale de l'équipement porté
        var restNoises = 0
        var timeouts = 0
        /** D'où viennent les morts : PV au début du combat fatal, embuscade, enchaînement, taille du groupe. */
        var deathsFullHp = 0; var deathsAmbush = 0; var deathsChained = 0
        val deathsByGroup = IntArray(4)
        var nextFightChained = false
        fun f(n: Int) = floors.getOrPut(n) { FloorStat() }
    }

    @Test
    fun simulate() {
        val out = StringBuilder()
        for (skill in Skill.entries) {
            val r = Report(skill)
            restNoisesCounter = 0
            repeat(profiles) { i -> playProfile(skill, r, Random(i * 7919L + skill.ordinal)) }
            r.restNoises = restNoisesCounter
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

    /**
     * Chaque relique seule, à équipement égal, contre « aucun sort » : c'est ce qui dit si
     * le budget tient (toutes devraient aider à peu près autant). Joueur « correct ».
     * On compte les victoires sur trois combats enchaînés.
     *
     * Une ligne par relique, une colonne par étage. Les reliques tournent **en parallèle**
     * (une par cœur) : chacune a ses propres dés, rien n'est partagé. Réglages :
     *  - SIM_RELICS=SUNDER,METEOR : ne mesurer que celles-là (« aucun sort » est toujours là) ;
     *  - SIM_SERIES=1000 : séries de 3 combats par case (±1,5 point à 1000, ±0,9 à 3000) ;
     *  - SIM_FLOORS=8,15,30,60 : les étages.
     * Un combat qui dépasse [MAX_FIGHT_TURNS] tours est arrêté, compté perdu et signalé.
     */
    @Test
    fun relicsAtFixedGear() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 1000
        val floors = System.getenv("SIM_FLOORS")?.split(",")?.map { it.trim().toInt() } ?: listOf(8, 15, 30, 60)
        val configs = listOf<Relic?>(null) + Relic.entries.filter { simRelics == null || it.name in simRelics }
        val start = System.currentTimeMillis()
        val rows = configs.parallelStream().map { relic ->
            val stuck = FloorStat()
            val cells = floors.map { floor ->
                val rng = Random(floor * 131L)
                var win3 = 0
                repeat(series) {
                    val hero = geared(floor, rng)
                    relic?.let { hero.addRelic(it) }
                    var ok = true
                    repeat(3) { if (ok) ok = soloFight(hero, floor, Skill.CORRECT, rng, stuck) }
                    if (ok) win3++
                }
                String.format("%5.1f %%", 100.0 * win3 / series)
            }
            String.format("%-17s", relic?.name ?: "aucun sort") + cells.joinToString("") { String.format("%10s", it) } +
                if (stuck.stuck > 0) "   ⚠ ${stuck.stuck} combats bloqués" else ""
        }.collect(java.util.stream.Collectors.toList())
        val out = StringBuilder("══════ Reliques seules à équipement égal ($series séries de 3 combats par case, joueur correct) ══════\n")
        out.appendLine(String.format("%-17s", "Étage") + floors.joinToString("") { String.format("%10d", it) })
        rows.forEach { out.appendLine(it) }
        out.appendLine("(victoires sur 3 combats enchaînés ; ${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-relics.txt").writeText(out.toString())
        println(out)
    }

    /**
     * Les combos rendent-ils le jeu trop facile ? Pour chaque paire de reliques qui peut
     * combiner (une résonance, ou une préparation suivie de sa réaction), la même paire jouée
     * deux fois, à équipement égal : au hasard (le bot lance ce qui est prêt), puis par le bot
     * **joueur de combos** (voir [comboChoice]). L'écart, c'est ce que rapporte le savoir.
     * Mêmes réglages que [relicsAtFixedGear] (SIM_SERIES, SIM_FLOORS).
     */
    @Test
    fun combosAtFixedGear() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 1000
        val floors = System.getenv("SIM_FLOORS")?.split(",")?.map { it.trim().toInt() } ?: listOf(15, 30, 60)
        val pairs = (Resonance.entries.map { it.a to it.b } +
            Relic.entries.flatMap { a -> Relic.entries.filter { b -> a != b && prepares(a, b) }.map { b -> a to b } })
            .distinctBy { setOf(it.first, it.second) }
        fun win3(relics: List<Relic>, floor: Int, combos: Boolean): Double {
            val rng = Random(floor * 131L)
            var wins = 0
            val stuck = FloorStat()
            repeat(series) {
                val hero = geared(floor, rng)
                relics.forEach { hero.addRelic(it) }
                var ok = true
                repeat(3) { if (ok) ok = soloFight(hero, floor, Skill.CORRECT, rng, stuck, combos) }
                if (ok) wins++
            }
            return 100.0 * wins / series
        }
        val start = System.currentTimeMillis()
        val base = floors.map { win3(emptyList(), it, false) }
        val rows = pairs.parallelStream().map { (a, b) ->
            val cells = floors.map { f ->
                val naive = win3(listOf(a, b), f, combos = false)
                val smart = win3(listOf(a, b), f, combos = true)
                String.format("%5.1f → %5.1f", naive, smart)
            }
            val res = Resonance.active(listOf(a, b)).firstOrNull()?.name ?: ""
            String.format("%-34s", "${a.name} + ${b.name}") + cells.joinToString("") { String.format("%18s", it) } + "  $res"
        }.collect(java.util.stream.Collectors.toList())
        val out = StringBuilder("══════ Combos à équipement égal ($series séries de 3 combats, joueur correct) : au hasard → en combo ══════\n")
        out.appendLine(String.format("%-34s", "Étage") + floors.joinToString("") { String.format("%18d", it) })
        out.appendLine(String.format("%-34s", "aucun sort") + base.joinToString("") { String.format("%18s", String.format("%5.1f", it)) })
        rows.forEach { out.appendLine(it) }
        out.appendLine("(victoires sur 3 combats enchaînés ; ${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-combos.txt").writeText(out.toString())
        println(out)
    }

    // ── Le bot joueur de combos : ce que sait un joueur qui connaît le jeu ──────

    /** [r] lancé sur [e] déclencherait une réaction (ou son bonus contre un figé). */
    private fun triggers(r: Relic, e: Enemy): Boolean {
        if (e.type.affinity(r.element) == Affinity.IMMUNE) return false
        if (r.effect == RelicEffect.CRYSTALLIZE && e.frozen) return true
        return when (r.element) {
            Element.FIRE      -> e.poisonTurns > 0 || e.frozen || e.soakedTurns > 0 || e.bleedTurns > 0
            Element.LIGHTNING -> e.soakedTurns > 0
            Element.PHYSICAL  -> r.hits && e.frozen
            Element.HOLY      -> e.poisonTurns > 0
            else -> false
        }
    }

    /** [setup] pose l'état que [payoff] vient ensuite déclencher (ou une résonance les lie). */
    private fun prepares(setup: Relic, payoff: Relic): Boolean = when (setup.effect) {
        RelicEffect.SOAK -> payoff.element == Element.LIGHTNING || payoff.effect == RelicEffect.FREEZE
        RelicEffect.POISON, RelicEffect.ACID, RelicEffect.ENCHANT_POISON ->
            payoff.hits && (payoff.element == Element.FIRE || payoff.element == Element.HOLY)
        RelicEffect.FREEZE -> (payoff.element == Element.PHYSICAL && payoff.hits) || payoff.effect == RelicEffect.CRYSTALLIZE
        RelicEffect.BLEED, RelicEffect.BLEED_ON_CRIT -> payoff.element == Element.FIRE
        RelicEffect.MARK -> payoff == Relic.FAN_OF_KNIVES
        RelicEffect.WARCRY -> payoff == Relic.SUNDER
        RelicEffect.SMOKE -> payoff == Relic.CHARM
        else -> false
    }

    /**
     * Le choix du joueur de combos, ou null s'il n'a rien de mieux que le jeu au hasard :
     *  1. une relique prête qui **déclenche** une réaction sur un ennemi : on la lance sur lui ;
     *  2. sinon, une relique prête qui **prépare** l'autre relique portée, si celle-ci sera
     *     prête au tour suivant : on pose l'état d'abord ;
     *  3. une relique qui déclenche mais n'a rien à déclencher attend sa préparation, si
     *     celle-ci est prête.
     * [target] : celui qui va frapper le plus tôt (pour les contrôles).
     */
    private fun comboChoice(c: Combat, target: Int): Pair<Relic?, Int>? {
        val worn = c.hero.relicSlots.filterNotNull()
        val ready = worn.filter { c.canCast(it) }
        val alive = c.aliveIndices()
        for (r in ready) alive.firstOrNull { triggers(r, c.enemies[it]) }?.let { return r to it }
        for (s in ready) {
            val payoff = worn.firstOrNull { it != s && prepares(s, it) } ?: continue
            if (c.hero.relicCooldown(payoff) > 1) continue
            val aim = if (s.effect == RelicEffect.FREEZE || s.effect == RelicEffect.MARK) target
                else alive.maxBy { c.enemies[it].hp }
            if (c.enemies[aim].type.affinity(s.element) == Affinity.IMMUNE) continue
            return s to aim
        }
        // Une relique de déclenchement ne part pas à vide si sa préparation est prête : on attaque
        val waiting = ready.firstOrNull { p -> ready.any { s -> s != p && prepares(s, p) } }
        return if (waiting != null) null to target else null
    }

    private fun geared(floor: Int, rng: Random): Hero {
        val hero = Hero.starter()
        repeat(15) {
            val e = LootSystem.generate(floor, 0, rng)
            val cur = hero.equipped[e.slot]
            if (cur == null || score(e) > score(cur)) hero.equipped[e.slot] = e
        }
        hero.potions = 2
        hero.healFull()
        return hero
    }

    private fun soloFight(hero: Hero, floor: Int, skill: Skill, rng: Random, fs: FloorStat = FloorStat(), combos: Boolean = false): Boolean {
        val c = Combat(hero, floor, Encounters.build(Encounters.roll(floor, rng), floor), ambush = false, rng = rng)
        playCombat(c, skill, fs, rng, combos)
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
                if (reached.add(g.floor)) {
                    r.deathsBeforeFloor.getOrPut(g.floor) { mutableListOf() } += deaths
                    r.gearOnArrival.getOrPut(g.floor) { mutableListOf() } += g.hero.equipped.values.sumOf { LootSystem.rating(it) }
                }
                best = maxOf(best, g.floor)
            }
            when {
                g.combat != null -> if (fight(g, skill, r, rng)) deaths++
                g.deathReport != null -> g.dismissDeath()
                g.pendingEquipDrop != null -> {
                    val e = g.pendingEquipDrop!!
                    val cur = g.hero.equipped[e.slot]
                    if (cur == null || score(e) > score(cur)) g.equipPendingDrop() else g.stashPendingDrop()
                }
                g.merchantOpen -> {
                    while (g.hero.potions < 3 && g.buyPotion()) {}
                    g.descend()
                }
                else -> { mapStep(g); r.f(g.floor).mapTurns++ }
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
        val ambush = c.phase == CombatPhase.ENEMY_TURN
        val chained = r.nextFightChained
        playCombat(c, skill, fs, rng)
        val died = c.phase == CombatPhase.DEFEAT
        if (died) {
            if (hpStart >= c.hero.maxHp * 0.9f) r.deathsFullHp++
            if (ambush) r.deathsAmbush++
            if (chained) r.deathsChained++
            r.deathsByGroup[c.enemies.size]++
        }
        fs.dmgPct += (hpStart - if (died) 0 else c.hero.hp).coerceAtLeast(0).toDouble() / c.hero.maxHp
        if (died) fs.deaths++
        val floorBefore = g.floor
        g.finishCombat()
        r.nextFightChained = !died && g.combat != null
        if (r.nextFightChained) r.f(floorBefore).chains++
        return died
    }

    private fun playCombat(c: Combat, skill: Skill, fs: FloorStat, rng: Random, combos: Boolean = false) {
        var turns = 0
        // Un sort qui ne frappe pas (bouclier, soin, charme…) n'est jamais lancé deux tours de
        // suite : avec une SAG haute, sa recharge tombe à 1 et le bot ne frappait plus jamais
        var lastWasSupport = false
        while (c.phase == CombatPhase.PLAYER_TURN || c.phase == CombatPhase.ENEMY_TURN) {
            if (turns > MAX_FIGHT_TURNS) { fs.stuck++; break }
            if (c.phase == CombatPhase.PLAYER_TURN) {
                turns++
                val alive = c.aliveIndices()
                // Cible : le plus proche d'attaquer, puis le plus faible
                val target = alive.minWith(compareBy<Int>({ c.enemies[it].countdown }, { c.enemies[it].hp }))
                val incoming = alive.filter { c.enemies[it].countdown <= 1 }.sumOf { c.enemies[it].damage }
                // Glace et foudre visent celui qui va frapper, comme la fracture, la marque, le charme
                // et l'aveuglement
                // (elles préparent ses propres coups) ; feu et poison, le plus solide.
                // Le bot connaît les affinités (un joueur les apprend en mourant) : il ne lance
                // jamais un sort sur un monstre qui y est immunisé ou déjà enragé.
                fun aimAt(r: Relic) =
                    if (r.element == Element.ICE || r.element == Element.LIGHTNING ||
                        r.effect in setOf(RelicEffect.FRACTURE, RelicEffect.MARK, RelicEffect.CHARM, RelicEffect.BLIND)) target
                    else alive.maxBy { c.enemies[it].hp }
                val ready = c.hero.relicSlots.filterNotNull().firstOrNull {
                    val e = c.enemies[aimAt(it)]
                    c.canCast(it) && (simRelics == null || it.name in simRelics) && !(lastWasSupport && !it.hits) &&
                        e.type.affinity(it.element) != Affinity.IMMUNE &&
                        !(e.enraged && (it.element == Element.ICE || it.element == Element.LIGHTNING))
                }
                // Le « Spécial » : le voleur achève une cible exposée, le guerrier se met en garde
                // devant un gros coup, le mage lève ses doubles dès qu'il n'en a plus
                val exposed = alive.filter { c.isExposed(c.enemies[it]) }.minByOrNull { c.enemies[it].hp }
                val special = if (!c.canUseSpecial()) null else when (c.hero.archetype) {
                    Archetype.ROGUE   -> exposed?.let { { c.deadlyStrike(it, strike(skill, rng)); Unit } }
                    // Garde et doubles ne frappent pas : jamais juste après un autre tour sans frapper
                    Archetype.WARRIOR -> if (incoming > c.hero.maxHp * 0.2f && !lastWasSupport) ({ c.guard(); lastWasSupport = true }) else null
                    Archetype.MAGE    -> if (c.mirrorImages == 0 && !lastWasSupport) ({ c.mirrorImage(); lastWasSupport = true }) else null
                    null -> null
                }
                lastWasSupport = false
                val combo = if (combos) comboChoice(c, target) else null
                when {
                    c.canDrinkPotion() && c.hero.hp <= incoming * 1.3f + c.hero.maxHp * 0.1f -> { c.drinkPotion(); fs.potionsUsed++ }
                    special != null -> special()
                    combo != null -> {
                        val r = combo.first
                        if (r == null) c.attack(combo.second, strike(skill, rng))
                        else { c.castRelic(r, combo.second, strike(skill, rng)); lastWasSupport = !r.hits }
                    }
                    ready != null -> { c.castRelic(ready, aimAt(ready), strike(skill, rng)); lastWasSupport = !ready.hits }
                    else -> c.attack(target, strike(skill, rng))
                }
            } else {
                val (_, attackers) = c.startEnemyTurn()
                for (a in attackers) {
                    if (c.phase != CombatPhase.ENEMY_TURN) break
                    c.resolveStrike(a, parry(skill, rng, guarding = c.guarding))
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

    /** En garde, la fenêtre de parade double : on compte les chances de réussite ×1,6 (bornées). */
    private fun parry(s: Skill, rng: Random, guarding: Boolean = false): Timing {
        val k = if (guarding) 1.6f else 1f
        val perfect = (s.parryPerfect * k).coerceAtMost(0.9f)
        val good = (s.parryGood * k).coerceAtMost(0.95f - perfect)
        val x = rng.nextFloat()
        return when { x < perfect -> Timing.PERFECT; x < perfect + good -> Timing.GOOD; else -> Timing.MISS }
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
        if (g.canRest() && hero.hp < hero.maxHp * 0.9f) {
            val packsBefore = lv.packs.size
            g.rest()
            if (g.level === lv && lv.packs.size > packsBefore) restNoisesCounter++
            return
        }

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

    private var restNoisesCounter = 0

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

    private fun score(e: Equipment) = LootSystem.rating(e)

    // ── Rapport ─────────────────────────────────────────────────────────────────

    private fun format(r: Report): String = buildString {
        appendLine("══════ ${r.skill} ($profiles profils, max étage $maxFloor, $maxMapTurns tours de carte) ══════")
        val b = r.bestFloors.sorted()
        appendLine("Meilleur étage : médiane ${b[b.size / 2]}, min ${b.first()}, max ${b.last()}   blocages : ${r.timeouts}")
        val totalDeaths = r.floors.values.sumOf { it.deaths }.coerceAtLeast(1)
        appendLine("Morts : $totalDeaths dont ${100 * r.deathsFullHp / totalDeaths} % en commençant le combat à plus de 90 % des PV, " +
            "${100 * r.deathsAmbush / totalDeaths} % en embuscade, ${100 * r.deathsChained / totalDeaths} % en combat enchaîné ; " +
            "par taille de groupe : 1 → ${100 * r.deathsByGroup[1] / totalDeaths} %, 2 → ${100 * r.deathsByGroup[2] / totalDeaths} %, 3 → ${100 * r.deathsByGroup[3] / totalDeaths} %")
        appendLine("Monstres attirés par le repos : ${r.restNoises}")
        appendLine("Arrivée à l'étage : profils arrivés | morts cumulées (médiane) | note totale de l'équipement porté (médiane)")
        for ((fl, l) in r.deathsBeforeFloor.toSortedMap()) {
            if (fl % 5 != 0 && fl != 1) continue
            val d = l.sorted(); val gear = r.gearOnArrival.getValue(fl).sorted()
            appendLine(String.format("  %2d | %2d | %4d | %5d", fl, d.size, d[d.size / 2], gear[gear.size / 2]))
        }
        appendLine("Ét. | passages | tours de carte/passage | combats/passage | combats | ennemis/combat | embuscades | enchaînés | dégâts/combat (% PV max) | tours/combat | potions/combat | morts")
        for ((fl, f) in r.floors) {
            val n = f.fights.coerceAtLeast(1).toDouble()
            appendLine(String.format("%3d | %5d | %5.0f | %4.1f | %6d | %4.2f | %4.0f%% | %4.0f%% | %5.1f%% | %4.1f | %5.2f | %d",
                fl, f.entries, f.mapTurns / f.entries.coerceAtLeast(1).toDouble(), f.fights / f.entries.coerceAtLeast(1).toDouble(), f.fights, f.groupSizes / n, 100 * f.ambushes / n, 100 * f.chains / n,
                100 * f.dmgPct / n, f.turnsInFight / n, f.potionsUsed / n, f.deaths))
        }
        appendLine()
    }
}
