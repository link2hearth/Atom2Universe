package com.Atom2Universe.app.games.roguelike

import org.junit.Test
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

/** Un équipement complet : un objet par emplacement (certains peuvent manquer). */
private typealias Loadout = Map<EquipSlot, Equipment>

/**
 * Banc de mesure du Donjon : des bots jouent de vraies parties (carte + combats) avec le
 * moteur du jeu, et on regarde jusqu'où ils vont. Aucune assertion.
 *
 * Les bots ne trichent pas : ils ne connaissent que les cases explorées et les monstres
 * visibles. Ils jouent tous pareil sur la carte (explorer, se reposer hors poursuite,
 * descendre) ; seule leur précision au timing change. Recalibrés le 18/09/2026 par
 * le propriétaire (les anciens niveaux étaient bien trop maladroits : l'ancien « expert »,
 * c'était sa mère de 70 ans) :
 *  - NOVICE  : parfait 45 %, bon 45 %, raté 10 % (l'ancien expert) ;
 *  - CORRECT : parfait 70 %, bon 28 %, raté 2 % ;
 *  - EXPERT  : parfait 95 %, bon 5 %, jamais raté.
 *
 * Ils connaissent aussi le grimoire (voir [chooseRelics] et [comboChoice]) : ils changent de
 * reliques selon l'étage et jouent les combos, comme le fera un vrai joueur. SIM_SMART=0 pour
 * revenir au bot d'avant (les deux premières reliques trouvées, lancées dès qu'elles sont prêtes).
 */
class RoguelikeSimulationTest {

    enum class Skill(val parryGood: Float, val parryPerfect: Float, val strikeGood: Float, val strikePerfect: Float) {
        NOVICE (0.45f, 0.45f, 0.45f, 0.45f),
        CORRECT(0.28f, 0.70f, 0.28f, 0.70f),
        EXPERT (0.05f, 0.95f, 0.05f, 0.95f),
    }

    /**
     * Deux façons de jouer le même donjon (voir [playerStyles]) :
     *  - CASUAL : il équipe ce qui a la meilleure note, sans regarder les sets ni les archétypes, garde
     *    les deux premières reliques trouvées et les lance dès qu'elles sont prêtes, sans combos ;
     *  - OPTIMIZER : il garde tout, choisit son archétype et ses pièces de set en essayant des équipements
     *    complets au combat (arme, main gauche de classe), choisit ses reliques et joue les combos ;
     *  - LEGACY : le bot de [simulate] (équipement au meilleur score, reliques selon SIM_SMART).
     */
    enum class Style { LEGACY, CASUAL, OPTIMIZER }

    private val profiles = 30
    /**
     * Réglages de mesure par variables d'environnement, sans toucher au code :
     * SIM_MAX_FLOOR=100 pour aller au bout, SIM_RELICS=NONE (aucun sort) ou
     * SIM_RELICS=FIREBALL,VENOM pour n'autoriser que certaines reliques.
     * Gradle ne relance pas un test déjà passé : ajouter cleanTestDebugUnitTest.
     */
    private val simRelics = System.getenv("SIM_RELICS")?.split(",")
    private val maxFloor = System.getenv("SIM_MAX_FLOOR")?.toInt() ?: 40
    /** SIM_CHECKPOINT=10 : un checkpoint automatique tous les 10 étages (0 : toujours l'étage 1, comme le jeu). */
    /** Règle de référence des bots : SIM_CHECKPOINT=10 et SIM_RETREAT=20 (la mort ramène 20 étages avant le checkpoint : le bot farme un peu, sans repartir de l'étage 1). */
    private val deathRetreat = System.getenv("SIM_RETREAT")?.toInt() ?: 0
    private val checkpointEvery = System.getenv("SIM_CHECKPOINT")?.toInt() ?: 0
    /** Le bot qui connaît le grimoire (reliques choisies, combos). SIM_SMART=0 : l'ancien bot. */
    private val smart = System.getenv("SIM_SMART") != "0"
    /** Archétypes écartés des essais d'équipement pour les bancs de contre-mesure (ex. MAGE). */
    private val avoidedArchetypes = System.getenv("SIM_AVOID_ARCHETYPES")
        ?.split(",")?.map { Archetype.valueOf(it.trim()) }?.toSet() ?: emptySet()
    /** Variante du banc : mesure le rôle des Spéciaux dans le choix spontané de l'équipement. */
    private val simUseSpecial = System.getenv("SIM_USE_SPECIAL") != "0"
    /** Séries de 3 combats d'essai par paire de reliques, quand le bot choisit les siennes. */
    private val trialSeries = 12
    /** Le budget de tours de carte d'un profil : SIM_MAP_TURNS pour une partie longue. */
    /** Par défaut, SIM_MAX_SECONDS est un délai global ; SIM_TIME_PER_PROFILE=1 le démarre à l'entrée de chaque bot. */
    private val maxSeconds = System.getenv("SIM_MAX_SECONDS")?.toLong()
    private val deadline = System.currentTimeMillis() + (maxSeconds?.times(1000) ?: Long.MAX_VALUE / 4)
    private val timePerProfile = System.getenv("SIM_TIME_PER_PROFILE") == "1"
    private val maxMapTurns = System.getenv("SIM_MAP_TURNS")?.toInt() ?: 250_000

    /** Le stock de l'optimiseur : objets possédés, équipements candidats, poids d'armure dont il a les trois pièces, pièces de set. */
    class StockStat {
        var decisions = 0; var pool = 0; var candidates = 0; var completeWeights = 0; var setPieces = 0; var setCandidates = 0
    }

    /** Ce qui s'est passé dans un combat : sorts, combos, Spécial (et Spécial amélioré par un set spécial). */
    class CombatStat {
        var casts = 0; var combos = 0; var specials = 0; var boostedSpecials = 0
        val specialBy = mutableMapOf<String, Int>()
        val castBy = mutableMapOf<Relic, Int>()
        fun cast(r: Relic) { castBy.merge(r, 1, Int::plus) }
        /** Ce qui frappe le héros : coups des monstres, et comment chacun s'est terminé. */
        var enemyAttacks = 0; var dodged = 0; var perks = 0; var imageHits = 0; var landed = 0
        var damageTaken = 0L; var puppetAbsorbed = 0L; var barrierAbsorbed = 0L
        /** Ce que le héros inflige : par sorte d'action (« attaque », « relique », « Spécial »), et pendant les tours ennemis. */
        val actionsBy = mutableMapOf<String, Int>()
        val damageBy = mutableMapOf<String, Long>()
        var thorns = 0L; var counters = 0L; var enemyTurnDamage = 0L
    }

    /** Un groupe de combats : combien, combien de morts, PV perdus (somme des parts de PV max). */
    class Agg { var fights = 0; var deaths = 0; var hpLost = 0.0 }

    class FloorStat {
        var entries = 0; var deaths = 0; var fights = 0; var chains = 0; var ambushes = 0
        var dmgPct = 0.0; var turnsInFight = 0
        var groupSizes = 0; var mapTurns = 0
        /** Combats arrêtés à [MAX_FIGHT_TURNS] tours : un combat qui ne finit pas est un bug. */
        var stuck = 0
        /** L'économie : pièces d'équipement tombées, leur valeur de revente, l'or gagné (sol + combats) et perdu aux morts. */
        var drops = 0; var dropValue = 0L; var goldEarned = 0L; var goldLost = 0L
    }

    companion object {
        /** Garde-fou des combats bloqués, adapté au multiplicateur de PV du jeu. */
        const val MAX_FIGHT_TURNS = 1500
        /**
         * Combats enchaînés sans repos dans le banc des reliques. 3 au début ; 5 depuis que les
         * bots sont adroits (18/09/2026) : à 3, « aucun sort » gagnait 99 % et le banc ne
         * départageait plus rien. Les PV perdus par combat, eux, ne saturent pas.
         */
        const val CHAIN = 5
        /** Séries de 3 combats d'essai par équipement candidat, pour l'optimiseur. */
        const val GEAR_SERIES = 16
        /** Une victoire d'essai pèse plus que n'importe quelle durée de combat (le score d'un essai est victoires × ce poids − tours joués). */
        const val TRIAL_WIN_WEIGHT = 100_000
        /** Au-delà de ce nombre de morts, un profil tourne en rond : on note ce qu'il porte. */
        const val DEATH_LOOP = 60
        /** Tours de carte sur un même étage au-delà desquels le bot est jugé coincé. */
        const val STUCK_TURNS = 20_000
        /** Pas récents regardés pour voir si le bot piétine (16 pas sur 3 cases au plus). */
        const val STUCK_WINDOW = 16
        /** Échecs de suite dans la même zone de 10 étages après lesquels le casual change de reliques au hasard. */
        const val CASUAL_FAILS = 3
    }

    class Report(val skill: Skill) {
        val floors = sortedMapOf<Int, FloorStat>()
        val bestFloors = mutableListOf<Int>()
        val deathsBeforeFloor = mutableMapOf<Int, MutableList<Int>>()   // étage -> morts cumulées avant d'y arriver
        val gearOnArrival = mutableMapOf<Int, MutableList<Int>>()       // étage -> note totale de l'équipement porté
        var restNoises = 0
        var mapResets = 0
        var timeouts = 0
        /** D'où viennent les morts : PV au début du combat fatal, embuscade, enchaînement, taille du groupe. */
        var deathsFullHp = 0; var deathsAmbush = 0; var deathsChained = 0
        val deathsByGroup = IntArray(4)
        var nextFightChained = false
        /** Ce que le bot porte en fin de partie, et la part de ses combats joués avec le bonus d'un set. */
        val archetypesAtEnd = mutableListOf<String>()
        var setBonusAtEnd = 0
        var setPiecesAtEnd = 0
        val setDrops = mutableMapOf<Archetype, Int>()
        val firstCompleteSetFloor = mutableMapOf<Archetype, Int>()
        var fightsTotal = 0; var fightsWithSet = 0
        val combatRows = mutableListOf<String>()
        /** Les changements d'équipement décidés par l'optimiseur, et ceux qui ont changé d'archétype. */
        var regears = 0; var archetypeSwitches = 0
        var profileNo = 0
        /** Ce que l'optimiseur avait à choisir à chaque décision, par tranche de 25 étages (voir [StockStat]). */
        val stock = sortedMapOf<Int, StockStat>()
        /** Un profil qui meurt en boucle (plus de [DEATH_LOOP] morts) : ce qu'il portait, pour comprendre pourquoi. */
        val deathLoops = mutableListOf<String>()
        val timeoutInfo = mutableListOf<String>()
        /** Les combats rangés par usage (« combos|oui », « set|non »…) et par tranche de 250 étages, pour comparer avec et sans. */
        val agg = sortedMapOf<String, Agg>()
        /** Utilisations du Spécial, « ARCHÉTYPE|amélioré » ou « ARCHÉTYPE|normal ». */
        val specialUses = sortedMapOf<String, Int>()
        var comboSteps = 0; var relicCasts = 0
        /** Morts par tranche de 50 étages, et passages (arrivées) sur ces tranches : là où les bots ont le plus « farmé ». */
        val deathsByBand = sortedMapOf<Int, Int>()
        val entriesByBand = sortedMapOf<Int, Int>()
        val fightsByBand = sortedMapOf<Int, Int>()
        var wallClockCut = 0
        /** Par relique portée au début d'un combat : combats, morts, PV perdus ; et ses lancers. */
        val relicAgg = sortedMapOf<String, Agg>()
        val relicCastsBy = sortedMapOf<String, Int>()
        /** Par archétype (« aucun » sans majorité de poids), avec « +set » quand le bonus de set est actif. */
        val archAgg = sortedMapOf<String, Agg>()
        /** Le casual qui change de reliques au hasard après des échecs répétés. */
        var relicReshuffles = 0
        /** L'optimiseur qui revoit ses reliques : combien de fois il les a réexaminées, et combien de fois il en a changé. */
        var relicChecks = 0
        var relicSwitches = 0
        /** Les reliques portées à la fin de chaque profil, pour voir ce que le bot a choisi. */
        val finalRelics = mutableListOf<String>()
        /** Toutes les reliques distinctes découvertes pendant chaque profil, même celles restées dans le sac. */
        val discoveredRelics = mutableListOf<String>()
        /** Étage -> or en poche + valeur de revente du sac, à la première arrivée (la richesse si tout était revendu). */
        val wealthOnArrival = mutableMapOf<Int, MutableList<Long>>()
        fun f(n: Int) = floors.getOrPut(n) { FloorStat() }
    }

    @Test
    fun simulate() {
        // SIM_SETS=0 : les sets spéciaux ne tombent pas (la mesure « d'avant les sets »)
        val noSets = System.getenv("SIM_SETS") == "0"
        val savedShare = IsotopeSets.dropShare
        if (noSets) IsotopeSets.dropShare = 0f
        try { simulateAll() } finally { IsotopeSets.dropShare = savedShare }
    }

    private fun simulateAll() {
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
     * Deux façons de jouer, avec et sans sets spéciaux : le joueur qui équipe le meilleur score sans rien regarder
     * d'autre (CASUAL, joueur « correct ») contre celui qui optimise tout (OPTIMIZER : archétype, sets, arme, main
     * gauche, reliques, combos), d'abord aussi « correct » pour isoler ce que vaut l'équipement, puis « expert ».
     * Chaque partie recommence à l'étage 1 à chaque mort, comme le jeu. Réglages : SIM_MAX_FLOOR (80 par défaut),
     * SIM_PROFILES (20), SIM_STYLES=CASUAL:CORRECT,OPTIMIZER:EXPERT (remplace la liste des joueurs).
     * Écrit build/roguelike-styles.txt.
     */
    @Test
    fun playerStyles() {
        val profiles = System.getenv("SIM_PROFILES")?.toInt() ?: 20
        val floorCap = System.getenv("SIM_MAX_FLOOR")?.toInt() ?: 80
        val players = System.getenv("SIM_STYLES")?.split(",")?.map { it.split(":").let { (a, b) -> Style.valueOf(a) to Skill.valueOf(b) } }
            ?: listOf(Style.CASUAL to Skill.CORRECT, Style.OPTIMIZER to Skill.CORRECT, Style.OPTIMIZER to Skill.EXPERT)
        val start = System.currentTimeMillis()
        val saved = IsotopeSets.dropShare
        val out = StringBuilder("══════ Deux façons de jouer, avec et sans sets ($profiles parties chacun, jusqu'à l'étage $floorCap) ══════\n\n")
        try {
            for (withSets in listOf(true, false)) {
                IsotopeSets.dropShare = if (withSets) IsotopeSets.DROP_SHARE else 0f
                val reports = players.parallelStream().map { (style, skill) ->
                    val r = Report(skill)
                    repeat(profiles) { i -> playProfile(skill, r, Random(i * 7919L + skill.ordinal + 13), style, floorCap) }
                    Triple(style, skill, r)
                }.collect(java.util.stream.Collectors.toList())
                out.appendLine("── ${if (withSets) "AVEC les sets" else "SANS les sets"} ──")
                out.appendLine(String.format("%-10s %-7s | étage : médiane · min · max | morts pour l'étage %d : médiane · max (arrivés) | combats avec bonus de set | archétypes en fin de partie", "joueur", "skill", floorCap))
                for ((style, skill, r) in reports) {
                    val b = r.bestFloors.sorted()
                    val d = r.deathsBeforeFloor[floorCap]?.sorted()
                    val deaths = if (d == null) "aucun n'y arrive" else "${d[d.size / 2]} · ${d.last()} (${d.size}/$profiles)"
                    val kinds = r.archetypesAtEnd.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                        .joinToString(" ") { "${it.key}×${it.value}" }
                    out.appendLine(String.format("%-10s %-7s | %3d · %3d · %3d | %-28s | %4.1f %% | %s  (optimiseur : %d rééquipements, %d changements d'archétype)",
                        style, skill, b[b.size / 2], b.first(), b.last(), deaths, 100.0 * r.fightsWithSet / r.fightsTotal.coerceAtLeast(1), kinds, r.regears, r.archetypeSwitches))
                }
                out.appendLine()
                for ((style, skill, r) in reports) out.append(format(r, style, floorCap, profiles))
            }
        } finally { IsotopeSets.dropShare = saved }
        out.appendLine("(${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-styles${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
        println(out)
    }

    /** La spécialisation guide le bot sans lui interdire les autres reliques ni les autres sets. */
    private fun preferredStat(wanted: Archetype): StatType = when (wanted) {
        Archetype.BARBARIAN -> StatType.STR
        Archetype.WARRIOR -> StatType.CON
        Archetype.ROGUE -> StatType.DEX
        Archetype.VAGABOND -> StatType.END
        Archetype.MAGE -> StatType.INT
        Archetype.NECROMANCER -> StatType.WIS
    }

    /**
     * Le même donjon avec chaque archétype préféré : pour chacun des cinq, 4 bots × (casual, optimiseur) × (novice, correct, expert), jusqu'à
     * l'étage 1000, checkpoint tous les 10 étages et retour de SIM_RETREAT étages. Compare les morts pour arriver au bout et la solidité de chacun.
     * SIM_PROFILES=4, SIM_MAX_FLOOR=1000, SIM_MAX_SECONDS, SIM_WANTED=WARRIOR,ROGUE,... Écrit build/roguelike-preferred.txt.
     */
    @Test
    fun preferredArchetypes() {
        val perCell = System.getenv("SIM_PROFILES")?.toInt() ?: 4
        val floorCap = System.getenv("SIM_MAX_FLOOR")?.toInt() ?: 1000
        val wantedList = System.getenv("SIM_WANTED")?.split(",")?.map { Archetype.valueOf(it) } ?: Archetype.entries.toList()
        val players = System.getenv("SIM_STYLES")?.split(",")?.map { it.split(":").let { (a, b) -> Style.valueOf(a) to Skill.valueOf(b) } }
            ?: listOf(Style.CASUAL to Skill.NOVICE, Style.CASUAL to Skill.CORRECT, Style.CASUAL to Skill.EXPERT,
                Style.OPTIMIZER to Skill.NOVICE, Style.OPTIMIZER to Skill.CORRECT, Style.OPTIMIZER to Skill.EXPERT)
        val start = System.currentTimeMillis()
        File("build/roguelike-progress.txt").writeText("")
        data class Task(val wanted: Archetype, val style: Style, val skill: Skill, val i: Int)
        // SIM_SKIP=VAGABOND:NOVICE,... : des cases qu'on ne joue pas (un profil qui meurt des milliers de fois retient tout le monde)
        val skipped = System.getenv("SIM_SKIP")?.split(",")?.toSet() ?: emptySet()
        val tasks = wantedList.flatMap { w -> players.filter { (_, sk) -> "${w.name}:${sk.name}" !in skipped }.flatMap { (st, sk) -> (0 until perCell).map { Task(w, st, sk, it) } } }
        val results = tasks.parallelStream().map { t ->
            val r = Report(t.skill)
            playProfile(t.skill, r, Random(t.i * 7919L + t.skill.ordinal + 301), t.style, floorCap,
                label = "${t.wanted} ${t.style} ${t.skill} bot ${t.i}", wanted = t.wanted)
            t to r
        }.collect(java.util.stream.Collectors.toList())

        val out = StringBuilder("══════ Un archétype préféré : ${perCell} bots par case, jusqu'à l'étage $floorCap ══════\n\n")
        out.appendLine("Morts pour arriver à l'étage $floorCap (les $perCell bots de la case ; « x » : n'y est pas arrivé)")
        out.appendLine(String.format("%-12s", "") + players.joinToString("") { String.format("%-24s", "${it.first.name.take(3)} ${it.second.name.take(4)}") })
        for (w in wantedList) {
            val cells = players.map { (st, sk) ->
                val rs = results.filter { it.first.wanted == w && it.first.style == st && it.first.skill == sk }.map { it.second }
                if (rs.isEmpty()) "-" else rs.joinToString(" ") { r -> if (r.bestFloors.single() >= floorCap) r.floors.values.sumOf { it.deaths }.toString() else "x" }
            }
            out.appendLine(String.format("%-12s", w.name) + cells.joinToString("") { String.format("%-24s", it) })
        }
        out.appendLine()
        out.appendLine("Médiane des morts pour arriver au bout, par archétype : casual · optimiseur")
        for (w in wantedList) {
            fun med(style: Style): String {
                val v = results.filter { it.first.wanted == w && it.first.style == style }.map { it.second }
                    .map { r -> if (r.bestFloors.single() >= floorCap) r.floors.values.sumOf { it.deaths } else Int.MAX_VALUE }.sorted()
                if (v.isEmpty()) return "-"
                val m = v[v.size / 2]
                return if (m == Int.MAX_VALUE) "x" else m.toString()
            }
            out.appendLine(String.format("  %-12s %8s · %8s", w.name, med(Style.CASUAL), med(Style.OPTIMIZER)))
        }
        out.appendLine()
        out.appendLine("Solidité de chaque archétype préféré (tous les bots de la ligne) : combats · joué avec l'archétype actif · avec le bonus de set · morts % · PV perdus %")
        for (w in wantedList) {
            val rs = results.filter { it.first.wanted == w }.map { it.second }
            val all = rs.flatMap { it.archAgg.entries }
            val fights = all.sumOf { it.value.fights }.coerceAtLeast(1)
            val active = all.filter { it.key.startsWith(w.name) }
            val withSet = all.filter { it.key == "${w.name}+set" }.sumOf { it.value.fights }
            val deaths = all.sumOf { it.value.deaths }; val lost = all.sumOf { it.value.hpLost }
            out.appendLine(String.format("  %-12s %8d · %5.1f %% · %5.1f %% · %5.2f %% · %5.1f %%", w.name, fights,
                100.0 * active.sumOf { it.value.fights } / fights, 100.0 * withSet / fights, 100.0 * deaths / fights, 100.0 * lost / fights))
        }
        out.appendLine()
        out.appendLine("Solidité par façon de jouer (morts % · PV perdus %) :")
        for (w in wantedList) {
            val cells = players.map { (st, sk) ->
                val a = results.filter { it.first.wanted == w && it.first.style == st && it.first.skill == sk }.flatMap { it.second.archAgg.values }
                val f = a.sumOf { it.fights }.coerceAtLeast(1)
                String.format("%5.2f %% · %5.1f %%", 100.0 * a.sumOf { it.deaths } / f, 100.0 * a.sumOf { it.hpLost } / f)
            }
            out.appendLine(String.format("%-12s", w.name) + cells.joinToString("") { String.format("%-24s", it) })
        }
        for (casual in listOf(true, false)) {
            val group = results.filter { (it.first.style == Style.CASUAL) == casual }.map { it.second }
            out.appendLine("\nReliques, ${if (casual) "casual" else "optimiseur"} (tous archétypes imposés) :")
            out.appendLine(relicTable(group))
            out.appendLine("  Changements de reliques : ${group.sumOf { it.relicSwitches }} sur ${group.sumOf { it.relicChecks }} révisions")
        }
        out.appendLine("\n(${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-preferred${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
        println(out)
    }

    /**
     * Sept profils comparables : libre, puis un par archétype. Tous testent les reliques
     * disponibles et leurs combos. Les six spécialisés préfèrent leur set et leurs reliques,
     * mais peuvent choisir autre chose si les essais en combat le justifient.
     * SIM_MAX_FLOOR, SIM_PROFILES, SIM_SKILLS, SIM_STYLES (CASUAL,OPTIMIZER), SIM_MAX_SECONDS et SIM_OUT règlent le banc.
     */
    @Test
    fun balancedProfiles() {
        val perCell = System.getenv("SIM_PROFILES")?.toInt() ?: 2
        val floorCap = System.getenv("SIM_MAX_FLOOR")?.toInt() ?: 500
        val skills = System.getenv("SIM_SKILLS")?.split(",")?.map { Skill.valueOf(it) }
            ?: listOf(Skill.CORRECT, Skill.EXPERT)
        val styles = System.getenv("SIM_STYLES")?.split(",")?.map { Style.valueOf(it) }
            ?: listOf(Style.OPTIMIZER)
        val wantedList: List<Archetype?> = System.getenv("SIM_WANTED")?.split(",")?.map {
            if (it == "LIBRE") null else Archetype.valueOf(it)
        } ?: listOf(null) + Archetype.entries
        data class Task(val wanted: Archetype?, val style: Style, val skill: Skill, val seed: Int)
        val tasks = wantedList.flatMap { wanted -> styles.flatMap { style -> skills.flatMap { skill ->
            (0 until perCell).map { Task(wanted, style, skill, it) }
        } } }
        val start = System.currentTimeMillis()
        File("build/roguelike-progress.txt").writeText("")
        val results = tasks.parallelStream().map { t ->
            val report = Report(t.skill)
            playProfile(t.skill, report, Random(t.seed * 7919L + t.skill.ordinal + 301),
                t.style, floorCap, label = "${t.wanted ?: "LIBRE"} ${t.style} ${t.skill} bot ${t.seed}", wanted = t.wanted)
            t to report
        }.collect(java.util.stream.Collectors.toList())
        val out = StringBuilder("══════ Profils ${wantedList.joinToString { it?.name ?: "LIBRE" }}, étage $floorCap, $perCell bots par type/style/niveau (${tasks.size} parcours) ══════\n")
        out.appendLine("Chaque profil peut changer de classe et porter toutes les reliques (Sablier exclu). Le spécialisé préfère son set et ses reliques. Spécial : ${if (simUseSpecial) "activé" else "désactivé"}. Limite de temps : ${if (timePerProfile) "par bot dès son démarrage" else "commune au banc"}.")
        out.appendLine("Profil / style / niveau · meilleur étage · morts pour atteindre $floorCap (x : non atteint) · combats · morts/combats · PV perdus/combats · archétype préféré actif · set préféré actif · reliques du type portées pour 100 combats")
        out.appendLine("Paramètres : sets permanents 8 % ; checkpoint=$checkpointEvery ; recul=$deathRetreat ; budget carte=$maxMapTurns ; secondes=$maxSeconds ; mêmes graines entre profils.")
        for (wanted in wantedList) for (style in styles) for (skill in skills) {
            val group = results.filter { it.first.wanted == wanted && it.first.style == style && it.first.skill == skill }.map { it.second }
            val fights = group.sumOf { it.fightsTotal }.coerceAtLeast(1)
            val a = group.flatMap { it.archAgg.entries }
            val deaths = a.sumOf { it.value.deaths }
            val lost = a.sumOf { it.value.hpLost }
            val active = a.filter { wanted != null && it.key.startsWith(wanted.name) }.sumOf { it.value.fights }
            val set = a.filter { wanted != null && it.key == "${wanted.name}+set" }.sumOf { it.value.fights }
            val ownRelics = group.flatMap { it.relicAgg.entries }.filter { entry ->
                wanted != null && Relic.valueOf(entry.key).attribute == preferredStat(wanted)
            }.sumOf { it.value.fights }
            val best = group.joinToString("/") { r -> if (r.fightsTotal == 0) "attente" else r.bestFloors.single().toString() }
            val deathCells = group.joinToString("/") { r ->
                if (r.fightsTotal == 0) "attente"
                else if (r.bestFloors.single() >= floorCap) r.floors.values.sumOf { it.deaths }.toString() else "x"
            }
            out.appendLine(String.format("%-13s %-9s %-7s · %-11s · %-17s · %8d · %5.2f %% · %5.1f %% · %5.1f %% · %5.1f %% · %5.1f",
                wanted?.name ?: "LIBRE", style, skill, best, deathCells, fights, 100.0 * deaths / fights,
                100.0 * lost / fights, 100.0 * active / fights, 100.0 * set / fights, 100.0 * ownRelics / fights))
        }
        for (wanted in wantedList) for (style in styles) {
            val group = results.filter { it.first.wanted == wanted && it.first.style == style }.map { it.second }
            out.appendLine("\n████ ${wanted ?: "LIBRE"} $style : archétypes réellement portés ████")
            out.appendLine(usageTable(group.map { it.archAgg }, group.sumOf { it.fightsTotal }))
            out.appendLine("Reliques portées, lancers et résultats :")
            out.appendLine("Collecte : pièces trouvées / bots avec les trois emplacements / étage de première collection complète (pas forcément portée)")
            for (a in Archetype.entries) {
                val completed = group.mapNotNull { it.firstCompleteSetFloor[a] }
                out.appendLine("  $a : ${group.sumOf { it.setDrops[a] ?: 0 }} / ${completed.size}/${group.size} / ${completed.joinToString("/").ifEmpty { "—" }}")
            }
            out.appendLine(relicTable(group))
            out.appendLine("Spéciaux utilisés : nombre · par combat dans cet archétype · part améliorée par le set")
            for (a in Archetype.entries) {
                val byKey = group.flatMap { it.specialUses.entries }.filter { it.key.startsWith("${a.name}|") }
                val uses = byKey.sumOf { it.value }
                val boosted = byKey.filter { it.key.endsWith("amélioré") }.sumOf { it.value }
                val archetypeFights = group.flatMap { it.archAgg.entries }.filter { it.key.startsWith(a.name) }.sumOf { it.value.fights }
                out.appendLine(String.format("  %-12s %7d · %5.2f · %5.1f %%", a.name, uses,
                    uses.toDouble() / archetypeFights.coerceAtLeast(1), 100.0 * boosted / uses.coerceAtLeast(1)))
            }
            out.appendLine("Changements de reliques : ${group.sumOf { it.relicSwitches }} / ${group.sumOf { it.relicChecks }} révisions ; combos joués : ${group.sumOf { it.comboSteps }} ; arrêts sur temps : ${group.sumOf { it.wallClockCut }}")
        }
        out.appendLine("\nDurée : ${(System.currentTimeMillis() - start) / 1000} s")
        out.appendLine("\nDétail par bot : meilleur étage, morts totales, morts aux étages 1 / 2 / 3 / 4 / 5, morts pleins PV, embuscades, enchaînements, reliques distinctes découvertes, limites atteintes")
        val botCsv = StringBuilder("wanted,style,skill,seed,best_floor,deaths,deaths_floor1,deaths_floor2,deaths_floor3,deaths_floor4,deaths_floor5,deaths_full_hp,deaths_ambush,deaths_chained,discovered_relic_count,discovered_relics,time_cut,map_cut,map_resets\n")
        val fightCsv = StringBuilder("wanted,style,skill,seed,floor,class,enemies,bosses,outcome,player_actions,hp_start,max_hp_start,ambush,chained,armor_pieces,weapon,weapon_power,relic_count,relics,enemy_types,enemy_hp_left,enemy_max_hp,life_steal,life_stolen\n")
        for ((t, r) in results) {
            val early = (1..5).map { r.floors[it]?.deaths ?: 0 }
            val deaths = r.floors.values.sumOf { it.deaths }
            val discovered = r.discoveredRelics.single()
            val discoveredCount = if (discovered.isBlank()) 0 else discovered.split("+").size
            out.appendLine("${t.wanted ?: "LIBRE"} ${t.style} ${t.skill} graine ${t.seed} : ${r.bestFloors.single()} ; $deaths ; ${early.joinToString("/")} ; ${r.deathsFullHp} ; ${r.deathsAmbush} ; ${r.deathsChained} ; reliques=$discoveredCount ; temps=${r.wallClockCut}, carte=${r.timeouts}")
            botCsv.appendLine((listOf(t.wanted ?: "LIBRE", t.style, t.skill, t.seed, r.bestFloors.single(), deaths) + early +
                listOf(r.deathsFullHp, r.deathsAmbush, r.deathsChained, discoveredCount, discovered, r.wallClockCut, r.timeouts, r.mapResets)).joinToString(","))
            out.appendLine("Régénérations au feu : ${r.mapResets}")
            r.timeoutInfo.forEach { out.appendLine(it) }
            for (row in r.combatRows) fightCsv.appendLine("${t.wanted ?: "LIBRE"},${t.style},${t.skill},${t.seed},$row")
        }
        out.appendLine("\nMorts par étage (tous bots) :")
        results.flatMap { it.second.floors.entries }.groupBy { it.key }.toSortedMap().forEach { (floor, rows) ->
            val deaths = rows.sumOf { it.value.deaths }
            if (deaths > 0) out.appendLine("Étage $floor : $deaths morts / ${rows.sumOf { it.value.fights }} combats")
        }
        val suffix = System.getenv("SIM_OUT") ?: ""
        File("build/roguelike-bots$suffix.csv").writeText(botCsv.toString())
        File("build/roguelike-combats$suffix.csv").writeText(fightCsv.toString())
        File("build/roguelike-balanced${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
        println(out)
    }

    /**
     * La longue partie : quelques bots de chaque façon de jouer descendent jusqu'à l'étage 1000 avec les vraies règles (pas de checkpoint :
     * la mort ramène à l'étage 1, c'est ce qui les fait « farmer » les étages bas). On regarde combien de morts pour arriver, où ils meurent,
     * combien de passages par tranche d'étages, et si les combos, les sets et les Spéciaux (améliorés ou non) servent.
     * SIM_PROFILES=4 (par joueur), SIM_MAX_FLOOR=1000, SIM_MAP_TURNS (budget de tours de carte, 20 000 000 ici), SIM_MAX_SECONDS
     * (arrêt au bout de ce temps, ce qui compte comme « pas arrivé »), SIM_STYLES. Écrit build/roguelike-long.txt.
     */
    @Test
    fun longRun() {
        val perStyle = System.getenv("SIM_PROFILES")?.toInt() ?: 4
        val floorCap = System.getenv("SIM_MAX_FLOOR")?.toInt() ?: 1000
        val players = System.getenv("SIM_STYLES")?.split(",")?.map { it.split(":").let { (a, b) -> Style.valueOf(a) to Skill.valueOf(b) } }
            ?: listOf(Style.CASUAL to Skill.CORRECT, Style.OPTIMIZER to Skill.CORRECT, Style.OPTIMIZER to Skill.EXPERT)
        val start = System.currentTimeMillis()
        File("build/roguelike-progress.txt").writeText("")
        val tasks = players.flatMap { (style, skill) -> (0 until perStyle).map { Triple(style, skill, it) } }
        val results = tasks.parallelStream().map { (style, skill, i) ->
            val r = Report(skill)
            playProfile(skill, r, Random(i * 7919L + skill.ordinal + 101), style, floorCap, label = "$style $skill bot $i")
            Triple(style, skill, r)
        }.collect(java.util.stream.Collectors.toList())
        val out = StringBuilder("══════ Longue partie : ${perStyle} bots par joueur, jusqu'à l'étage $floorCap, sans checkpoint ══════\n\n")
        for ((style, skill) in players.distinct()) {
            out.append(formatLong(style, skill, results.filter { it.first == style && it.second == skill }.map { it.third }, floorCap))
        }
        val all = results.map { it.third }
        out.appendLine("████████ TOUS LES BOTS ENSEMBLE ████████")
        out.appendLine("Archétypes : part des combats · combats · morts % · PV perdus %")
        out.appendLine(usageTable(all.map { it.archAgg }, all.sumOf { it.fightsTotal }))
        for (casual in listOf(true, false)) {
            val group = results.filter { (it.first == Style.CASUAL) == casual }.map { it.third }
            out.appendLine("Reliques, ${if (casual) "casual (au hasard, jamais optimisé)" else "optimiseur (choisies par essais)"} :")
            out.appendLine(relicTable(group))
            out.appendLine("  Changements de reliques : ${group.sumOf { it.relicSwitches }} sur ${group.sumOf { it.relicChecks }} révisions (${group.sumOf { it.fightsTotal }} combats)")
        }
        out.appendLine("(${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-long${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
        println(out)
    }

    /**
     * L'économie de l'or, pour fixer le prix de la forge : par tranche d'étages, combien de pièces d'équipement
     * tombent par passage sur un étage, ce qu'elles valent à la revente, et l'or gagné (sol + combats).
     * Les bots ne revendent pas (l'optimiseur a besoin de son sac) : la revente est comptée comme si tout le butin
     * était vendu, ce qui la surestime un peu (les pièces portées ne se vendent pas).
     * Se lance avec la règle de référence SIM_CHECKPOINT=10 SIM_RETREAT=20. SIM_PROFILES=4 (par joueur),
     * SIM_MAX_FLOOR=200, SIM_BAND=10, SIM_STYLES. Écrit build/roguelike-gold.txt.
     */
    @Test
    fun goldEconomy() {
        val perStyle = System.getenv("SIM_PROFILES")?.toInt() ?: 4
        val floorCap = System.getenv("SIM_MAX_FLOOR")?.toInt() ?: 200
        val band = System.getenv("SIM_BAND")?.toInt() ?: 10
        val players = System.getenv("SIM_STYLES")?.split(",")?.map { it.split(":").let { (a, b) -> Style.valueOf(a) to Skill.valueOf(b) } }
            ?: listOf(Style.CASUAL to Skill.CORRECT, Style.OPTIMIZER to Skill.CORRECT)
        val start = System.currentTimeMillis()
        File("build/roguelike-progress.txt").writeText("")
        val tasks = players.flatMap { (style, skill) -> (0 until perStyle).map { Triple(style, skill, it) } }
        val results = tasks.parallelStream().map { (style, skill, i) ->
            val r = Report(skill)
            playProfile(skill, r, Random(i * 7919L + skill.ordinal + 101), style, floorCap, label = "$style $skill bot $i")
            Triple(style, skill, r)
        }.collect(java.util.stream.Collectors.toList())

        fun table(title: String, reports: List<Report>): String = buildString {
            appendLine("── $title : ${reports.size} bots, meilleurs étages ${reports.flatMap { it.bestFloors }.sorted()} ──")
            appendLine(String.format("%-9s %8s %7s %9s %9s %9s %9s %8s %11s %8s",
                "étages", "passages", "pièces", "revente", "or gagné", "total", "or perdu", "puiss.", "richesse", "total/P"))
            appendLine("%-9s %8s %7s %9s %9s %9s %9s %8s %11s %8s".format("", "", "/pass.", "/pass.", "/pass.", "/pass.", "/pass.", "moy.", "à l'arrivée", ""))
            for (lo in 1..floorCap step band) {
                val hi = lo + band - 1
                val fs = reports.flatMap { r -> r.floors.filterKeys { it in lo..hi }.values }
                val visits = fs.sumOf { it.entries }
                if (visits == 0) continue
                val drops = fs.sumOf { it.drops }.toDouble() / visits
                val value = fs.sumOf { it.dropValue }.toDouble() / visits
                val earned = fs.sumOf { it.goldEarned }.toDouble() / visits
                val lost = fs.sumOf { it.goldLost }.toDouble() / visits
                val power = LootSystem.powerCenter((lo + hi) / 2)
                val wealth = reports.flatMap { it.wealthOnArrival[lo] ?: emptyList() }.sorted().let { if (it.isEmpty()) "—" else it[it.size / 2].toString() }
                appendLine(String.format("%-9s %8d %7.2f %9.0f %9.0f %9.0f %9.0f %8.1f %11s %8.1f",
                    "$lo-$hi", visits, drops, value, earned, value + earned, lost, power, wealth, (value + earned) / power))
            }
            appendLine()
        }
        val out = StringBuilder("══════ Économie de l'or : $perStyle bots par joueur, jusqu'à l'étage $floorCap, checkpoint $checkpointEvery, retour $deathRetreat ══════\n")
        out.appendLine("Par passage sur un étage (une arrivée, retours après une mort compris). « revente » = valeur de revente de toutes les pièces tombées ;")
        out.appendLine("« richesse » = or en poche + revente du sac à la première arrivée au premier étage de la tranche (médiane) ; « total/P » = total par passage ÷ puissance moyenne des objets.\n")
        for ((style, skill) in players.distinct()) out.append(table("$style $skill", results.filter { it.first == style && it.second == skill }.map { it.third }))
        out.append(table("TOUS", results.map { it.third }))
        out.appendLine("(${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-gold${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
        println(out)
    }

    private fun formatLong(style: Style, skill: Skill, reports: List<Report>, floorCap: Int): String = buildString {
        appendLine("████████ $style $skill ████████")
        for ((i, r) in reports.withIndex()) {
            val deaths = r.floors.values.sumOf { it.deaths }
            appendLine(String.format("bot %d : meilleur étage %4d | %5d morts | %s%s | %s", i, r.bestFloors.single(), deaths,
                r.archetypesAtEnd.single(), if (r.wallClockCut > 0) " | ARRÊTÉ par le temps" else if (r.timeouts > 0) " | budget de tours épuisé" else "",
                r.finalRelics.single()))
        }
        // Morts cumulées à la première arrivée sur chaque palier
        appendLine("Morts cumulées à la première arrivée sur l'étage (par bot ; « - » : jamais arrivé) :")
        for (m in generateSequence(100) { it + 100 }.takeWhile { it <= floorCap }) {
            val d = reports.map { r -> r.deathsBeforeFloor[m]?.firstOrNull()?.toString() ?: "-" }
            appendLine(String.format("  étage %4d : %s", m, d.joinToString("  ") { String.format("%5s", it) }))
        }
        // Où les bots passent leur temps et meurent
        appendLine("Par tranche de 50 étages, cumul des bots : passages (arrivées) · combats · morts · morts pour 100 combats")
        val bands = (reports.flatMap { it.entriesByBand.keys } + reports.flatMap { it.deathsByBand.keys }).toSortedSet()
        val top = bands.sortedByDescending { b -> reports.sumOf { it.deathsByBand[b] ?: 0 } }.take(5).toSet()
        for (b in bands) {
            val e = reports.sumOf { it.entriesByBand[b] ?: 0 }; val f = reports.sumOf { it.fightsByBand[b] ?: 0 }; val d = reports.sumOf { it.deathsByBand[b] ?: 0 }
            appendLine(String.format("  %4d–%4d : %5d passages · %6d combats · %4d morts · %5.2f%s", b * 50, b * 50 + 49, e, f, d, 100.0 * d / f.coerceAtLeast(1), if (b in top && d > 0) "   ◄ plus meurtrier" else ""))
        }
        // Avec ou sans : combos, Spécial, Spécial amélioré, bonus de set (combats groupés par tranche de 250 étages)
        appendLine("Avec ou sans (combats · morts % · PV perdus % par combat), par tranche de 250 étages ; « oui » contre « non » dans la même tranche :")
        val keys = reports.flatMap { it.agg.keys }.map { it.substringBeforeLast("|z").substringBeforeLast("|") }.distinct().sorted()
        val zones = reports.flatMap { it.agg.keys }.map { it.substringAfterLast("|z").toInt() }.distinct().sorted()
        for (k in keys) {
            for (z in zones) {
                fun cell(v: String): String {
                    val a = reports.mapNotNull { it.agg["$k|$v|z$z"] }
                    val fights = a.sumOf { it.fights }
                    return if (fights == 0) "          –          " else String.format("%7d · %5.2f%% · %5.1f%%", fights, 100.0 * a.sumOf { it.deaths } / fights, 100.0 * a.sumOf { it.hpLost } / fights)
                }
                val yes = cell("oui"); val no = cell("non")
                if (yes.trim() == "–" && no.trim() == "–") continue
                appendLine(String.format("  %-16s étages %4d–%4d | oui %s | non %s", k, z * 250, z * 250 + 249, yes, no))
            }
        }
        appendLine("Archétypes portés (par combat) : part des combats · combats · morts % · PV perdus %")
        appendLine(usageTable(reports.map { it.archAgg }, reports.sumOf { it.fightsTotal }))
        if (reports.any { it.relicReshuffles > 0 }) appendLine("Changements de reliques au hasard après échecs répétés : ${reports.sumOf { it.relicReshuffles }} (${reports.joinToString(" / ") { it.relicReshuffles.toString() }} par bot)")
        val uses = reports.flatMap { it.specialUses.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.toSortedMap()
        appendLine("Spéciaux utilisés (total des bots) : " + uses.entries.joinToString("  ") { "${it.key} ×${it.value}" })
        val fights = reports.sumOf { it.fightsTotal }.coerceAtLeast(1)
        appendLine(String.format("Par combat : %.2f sorts lancés, %.2f pas de combo ; combats avec le bonus d'un set : %.1f %%", reports.sumOf { it.relicCasts } / fights.toDouble(),
            reports.sumOf { it.comboSteps } / fights.toDouble(), 100.0 * reports.sumOf { it.fightsWithSet } / fights))
        appendLine()
    }

    /** Un tableau « nom · part des combats · combats · morts % · PV perdus % » sur plusieurs Agg, trié par combats. */
    private fun usageTable(maps: List<Map<String, Agg>>, totalFights: Int): String {
        val names = maps.flatMap { it.keys }.distinct()
        return names.map { n ->
            val a = maps.mapNotNull { it[n] }
            Triple(n, a.sumOf { it.fights }, a)
        }.sortedByDescending { it.second }.joinToString("\n") { (n, fights, a) ->
            String.format("  %-22s %5.1f %% · %8d · %5.2f %% · %5.1f %%", n, 100.0 * fights / totalFights.coerceAtLeast(1), fights,
                100.0 * a.sumOf { it.deaths } / fights.coerceAtLeast(1), 100.0 * a.sumOf { it.hpLost } / fights.coerceAtLeast(1))
        }
    }

    /** Les reliques : portées (part des combats), lancers par combat où elle est portée, morts, PV perdus. */
    private fun relicTable(reports: List<Report>): String {
        val totalFights = reports.sumOf { it.fightsTotal }.coerceAtLeast(1)
        val names = reports.flatMap { it.relicAgg.keys }.distinct()
        return names.map { n ->
            val a = reports.mapNotNull { it.relicAgg[n] }
            val worn = a.sumOf { it.fights }
            Triple(n, worn, a)
        }.sortedByDescending { it.second }.joinToString("\n") { (n, worn, a) ->
            val casts = reports.sumOf { it.relicCastsBy[n] ?: 0 }
            String.format("  %-18s portée %5.1f %% des combats · %5.2f lancers/combat · morts %5.2f %% · PV perdus %5.1f %%", n,
                100.0 * worn / totalFights, casts.toDouble() / worn.coerceAtLeast(1),
                100.0 * a.sumOf { it.deaths } / worn.coerceAtLeast(1), 100.0 * a.sumOf { it.hpLost } / worn.coerceAtLeast(1))
        }
    }

    private fun <T> r0(reports: List<Report>, f: (Report) -> Set<T>): Set<T> = reports.flatMap { f(it) }.toSet()

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


    // ── Les sets spéciaux, à équipement égal ───────────────────────────────────

    /** Ce que porte le héros d'essai sur le casque, l'armure et les bottes. */
    private enum class SetGear(val label: String) {
        /** L'armure du butin comme le bot la trouve : des poids mélangés, aucune pièce de set. */
        CLASSIC("classique"),
        /** Le butin ordinaire, mais du poids de l'archétype du set : la comparaison juste. */
        SAME_ARCHETYPE("même poids"),
        /** Les pièces du set, sans leur bonus : ce que valent leurs seules stats. */
        SET_NO_BONUS("set sans bonus"),
        /** Les pièces du set, avec le bonus des trois pièces. */
        SET("set"),
    }

    /** Le meilleur de [tries] tirages de butin ordinaire pour cet emplacement (et ce poids, s'il est donné). */
    private fun classicPiece(floor: Int, slot: EquipSlot, weight: ArmorWeight?, rng: Random, tries: Int = 3): Equipment =
        (1..tries).map {
            var e: Equipment
            do e = LootSystem.generate(floor, 0, rng) while (e.slot != slot || e.isotopeZ != null || (weight != null && e.weight != weight))
            e
        }.maxBy { score(it) }

    /**
     * Le héros d'essai n° [i] : mêmes dés d'équipement pour toutes les configurations (arme, main
     * gauche, bijoux), seule l'armure change. Un joueur qui farme une tranche a le temps d'attendre
     * de bonnes pièces : on prend la meilleure de 3 par emplacement, pour le set comme pour le butin.
     */
    private fun setGearedHero(floor: Int, i: Int, gear: SetGear, set: IsotopeSet): Hero {
        val hero = geared(floor, Random(floor * 7919L + i))
        val rng = Random(floor * 104729L + i)
        // Une arme qui ne va pas à l'archétype du set le pénaliserait sans que le set y soit pour rien :
        // mêmes dés, mais l'arme du tirage est refaite jusqu'à ce qu'elle lui aille
        if (hero.equipped[EquipSlot.WEAPON]?.let { set.archetype.accepts(it.base) } == false) {
            var weapon: Equipment
            do weapon = LootSystem.generate(floor, 0, rng) while (weapon.slot != EquipSlot.WEAPON || weapon.isotopeZ != null || !set.archetype.accepts(weapon.base))
            hero.equipped[EquipSlot.WEAPON] = weapon
        }
        // Main gauche de classe identique dans les quatre variantes : sinon Ravage ne peut pas ouvrir sa brèche.
        val offhandRng = Random(floor * 65537L + i)
        hero.equipped[EquipSlot.OFFHAND] = (1..3).map {
            var item: Equipment
            do item = LootSystem.generate(floor, 0, offhandRng) while (item.base != set.archetype.offhand)
            item
        }.maxBy { score(it) }
        val armor: Map<EquipSlot, Equipment> = when (gear) {
            SetGear.CLASSIC -> emptyMap()
            SetGear.SAME_ARCHETYPE -> IsotopeSets.SLOTS.associateWith { classicPiece(floor, it, set.archetype.weight, rng) }
            SetGear.SET_NO_BONUS, SetGear.SET -> IsotopeSets.BASES.associate { base ->
                val best = (1..3).map { LootSystem.createSetPiece(set, base, 0, rng, floor) }.maxBy { score(it) }
                best.slot to if (gear == SetGear.SET) best else best.copy(isotopeZ = null)
            }
        }
        hero.equipped.putAll(armor)
        hero.healFull()
        return hero
    }

    /** Durée des victoires par taille de groupe ; les morts et blocages restent séparés. */
    @Test
    fun combatPaceByGroup() {
        val samples = System.getenv("SIM_SERIES")?.toInt() ?: 300
        val builds = System.getenv("SIM_BUILDS")?.toInt() ?: 6
        require(samples > 0 && builds > 0)
        val skill = System.getenv("SIM_SKILL")?.let(Skill::valueOf) ?: Skill.CORRECT
        val suffix = System.getenv("SIM_OUT") ?: ""
        val csv = StringBuilder("class,floor,enemies,boss,samples,wins,deaths,censored,mean_win_actions,median_win_actions,p90_win_actions,mean_enemy_hp\n")
        val out = StringBuilder("Durée des combats : $samples essais/cellule, $builds équipements/classe/étage, $skill\n")
        out.appendLine("Un tour = une action du joueur (attaque, relique ou Spécial), soutien inclus. Moyennes sur victoires seulement ; morts et blocages séparés.")
        out.appendLine("Set complet du tier supérieur, arme compatible, main gauche de classe, reliques de classe choisies par le bot. Emplacements ouverts à cet étage seulement.")
        out.appendLine("PV pleins et recharges prêtes à chaque combat ; aucune embuscade. Bestiaire Donjon, mêmes espèces et graines entre classes. Groupe de 3 = un chef boss + deux accompagnants selon Encounters.build.")
        val saved = IsotopeSets.dropShare
        IsotopeSets.dropShare = 0f
        try {
            for (floor in setFloors()) for (set in simSets()) {
                val templates = List(builds) { i ->
                    setGearedHero(floor, i, SetGear.SET, set).apply {
                        deepestFloor = floor
                        relicSlots.fill(null)
                        relics.clear()
                        (Relic.entries.filter { it.attribute == preferredStat(set.archetype) && it != Relic.HOURGLASS })
                            .distinct().forEach { addRelic(it) }
                        chooseRelics(this, floor, skill, set.archetype)
                        healFull()
                    }
                }
                out.appendLine("\n${set.archetype}, étage $floor : " + templates.joinToString(" ; ") { h ->
                    h.relicSlots.filterNotNull().joinToString("+") { it.name }
                })
                for (count in 1..3) {
                    val wins = mutableListOf<Int>()
                    var deaths = 0; var censored = 0; var enemyHp = 0L
                    repeat(samples) { i ->
                        val template = templates[i % builds]
                        val hero = trialCopy(template, template.relicSlots.filterNotNull())
                        check(hero.archetype == set.archetype && hero.setArchetype == set.archetype)
                        val family = Encounters.roll(floor, Random(floor * 524287L + i)).first()
                        val enemies = Encounters.build(List(count) { family }, floor)
                        check(enemies.count { it.isBoss } == if (count == 3) 1 else 0)
                        enemyHp += enemies.sumOf { it.maxHp.toLong() }
                        val stat = FloorStat()
                        val combat = Combat(hero, floor, enemies, ambush = false, rng = Random(floor * 65537L + i))
                        playCombat(combat, skill, stat, Random(floor * 8191L + i), combos = true)
                        when {
                            combat.phase == CombatPhase.VICTORY -> wins += stat.turnsInFight
                            hero.hp <= 0 -> deaths++
                            else -> censored++
                        }
                    }
                    val sorted = wins.sorted()
                    fun percentile(fraction: Double) = if (sorted.isEmpty()) Double.NaN else
                        sorted[(kotlin.math.ceil(sorted.size * fraction).toInt() - 1).coerceAtLeast(0)].toDouble()
                    fun number(value: Double) = if (value.isNaN()) "NA" else String.format(java.util.Locale.US, "%.2f", value)
                    val mean = number(wins.map { it.toDouble() }.average())
                    val median = number(percentile(.5))
                    val p90 = number(percentile(.9))
                    csv.appendLine("${set.archetype},$floor,$count,${count == 3},$samples,${wins.size},$deaths,$censored,$mean,$median,$p90,${number(enemyHp.toDouble() / samples)}")
                    out.appendLine("  $count ennemi(s)${if (count == 3) " dont boss" else ""} : $mean tours moyens ; médiane $median ; p90 $p90 ; victoires ${wins.size}/$samples ; morts $deaths ; blocages $censored")
                }
            }
        } finally { IsotopeSets.dropShare = saved }
        File("build/roguelike-pace$suffix.csv").writeText(csv.toString())
        File("build/roguelike-pace$suffix.txt").writeText(out.toString())
        println(out)
    }

    private fun armorRating(hero: Hero) = IsotopeSets.SLOTS.sumOf { slot -> hero.equipped[slot]?.let { LootSystem.rating(it) } ?: 0 }

    /** Victoires sur [chain] combats enchaînés, PV perdus par combat (en %), note de l'armure. */
    private class SetCell(val wins: Double, val lost: Double, val rating: Double)

    private fun measureSetCell(floor: Int, gear: SetGear, set: IsotopeSet, series: Int, chain: Int, relic: Relic?, skill: Skill): SetCell {
        val stuck = FloorStat()
        var wins = 0; var lost = 0.0; var fights = 0; var rating = 0.0
        repeat(series) { i ->
            val hero = setGearedHero(floor, i, gear, set)
            relic?.let { hero.addRelic(it) }
            rating += armorRating(hero)
            val rng = Random(floor * 31L + i)
            var ok = true
            repeat(chain) {
                if (!ok) return@repeat
                val before = hero.hp
                ok = soloFight(hero, floor, skill, rng, stuck)
                lost += (before - if (ok) hero.hp else 0).coerceAtLeast(0).toDouble() / hero.maxHp
                fights++
            }
            if (ok) wins++
        }
        return SetCell(100.0 * wins / series, 100.0 * lost / fights.coerceAtLeast(1), rating / series)
    }

    /**
     * Une, deux ou trois pièces du set, le reste de l'armure étant du butin du même poids : que vaut
     * une pièce seule ? Le bonus n'existe qu'à trois. Colonnes : 0, 1, 2, 3 pièces (0 = même poids).
     * L'emplacement des pièces de set tourne d'un essai à l'autre pour ne favoriser aucune case.
     * SIM_SERIES=600, SIM_SKILL=NOVICE.
     */
    @Test
    fun isotopeSetsByPieceCount() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 600
        val chain = System.getenv("SIM_CHAIN")?.toInt() ?: 3
        val skill = System.getenv("SIM_SKILL")?.let { Skill.valueOf(it) } ?: Skill.CORRECT
        val saved = IsotopeSets.dropShare
        IsotopeSets.dropShare = 0f
        try {
            val out = StringBuilder("══════ Sets spéciaux : 0 à 3 pièces ($series séries de $chain combats, joueur $skill) ══════\n")
            out.appendLine("victoires · PV perdus par combat, le reste de l'armure étant du butin du même poids\n")
            for (set in simSets()) {
                out.appendLine("── SET ${set.archetype} (permanent, tier supérieur) ──")
                out.appendLine(String.format("%5s", "Étage") + (0..3).joinToString("") { String.format("%20s", "$it pièce(s)") })
                val rows = setFloors().parallelStream().map { floor ->
                    val cells = (0..3).map { pieces ->
                        val stuck = FloorStat()
                        var wins = 0; var lost = 0.0; var fights = 0
                        repeat(series) { i ->
                            val hero = setGearedHero(floor, i, SetGear.SAME_ARCHETYPE, set)
                            val rng = Random(floor * 104729L + i)
                            val slots = IsotopeSets.SLOTS
                            val chosen = (0 until pieces).map { slots[(it + i) % slots.size] }.toSet()
                            for (slot in slots) {
                                hero.equipped[slot] = if (slot in chosen) {
                                    val base = IsotopeSets.BASES[slots.indexOf(slot)]
                                    (1..3).map { LootSystem.createSetPiece(set, base, 0, rng, floor) }.maxBy { score(it) }
                                } else classicPiece(floor, slot, set.archetype.weight, rng)
                            }
                            hero.healFull()
                            val fightRng = Random(floor * 31L + i)
                            var ok = true
                            repeat(chain) {
                                if (!ok) return@repeat
                                val before = hero.hp
                                ok = soloFight(hero, floor, skill, fightRng, stuck)
                                lost += (before - if (ok) hero.hp else 0).coerceAtLeast(0).toDouble() / hero.maxHp
                                fights++
                            }
                            if (ok) wins++
                        }
                        String.format("%5.1f%% · %4.1f%%", 100.0 * wins / series, 100.0 * lost / fights.coerceAtLeast(1))
                    }
                    String.format("%5d", floor) + cells.joinToString("") { String.format("%20s", it) }
                }.collect(java.util.stream.Collectors.toList())
                rows.forEach { out.appendLine(it) }
                out.appendLine()
            }
            File("build/roguelike-sets-pieces.txt").writeText(out.toString())
            println(out)
        } finally { IsotopeSets.dropShare = saved }
    }

    /**
     * Ce qui fait la force de chaque archétype, à butin égal et sans set : pour chacun, l'armure de son poids (la meilleure de 3
     * par emplacement), avec (1) une épée commune, sans main gauche et **sans Spécial** : que valent les seuls passifs du poids
     * (CA, vitesse, armure) ; (2) la même chose **avec le Spécial** ; (3-4) son équipement complet : arme qui lui va,
     * sa main gauche, sans puis avec le Spécial.
     * Cellule : victoires sur 3 combats enchaînés · PV perdus · tours par combat. SIM_SERIES=500, SIM_SKILL=CORRECT, SIM_RELIC=MAGIC_MISSILE
     * (défaut : aucune relique), SIM_FLOORS=10,50,150,400,800. Écrit build/roguelike-archetype-strength.txt.
     */
    @Test
    fun archetypeStrength() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 500
        val skill = System.getenv("SIM_SKILL")?.let { Skill.valueOf(it) } ?: Skill.CORRECT
        val relic = System.getenv("SIM_RELIC")?.let { Relic.valueOf(it) }
        val floors = System.getenv("SIM_FLOORS")?.split(",")?.map { it.trim().toInt() } ?: listOf(10, 50, 150, 400, 800)
        val saved = IsotopeSets.dropShare
        IsotopeSets.dropShare = 0f
        try {
            val out = StringBuilder("══════ Force des archétypes à butin égal ($series séries de 3 combats, joueur $skill, relique ${relic ?: "aucune"}) ══════\n")
            out.appendLine("cellule : victoires · PV perdus par combat · tours par combat ; colonnes : épée seule sans Spécial | + Spécial | équipement complet sans Spécial | + Spécial\n")
            fun best(floor: Int, rng: Random, tries: Int, ok: (Equipment) -> Boolean): Equipment =
                (1..tries).map { var e: Equipment; do e = LootSystem.generate(floor, 0, rng) while (!ok(e) || e.isotopeZ != null); e }.maxBy { score(it) }
            for (floor in floors) {
                out.appendLine("── étage $floor ──")
                val rows = Archetype.entries.parallelStream().map { a ->
                    val cells = (0..3).map { variant ->
                        var wins = 0; var lost = 0.0; var fights = 0; var turns = 0
                        repeat(series) { i ->
                            val hero = geared(floor, Random(floor * 7919L + i))
                            val rng = Random(floor * 104729L + i)
                            for (slot in IsotopeSets.SLOTS) hero.equipped[slot] = classicPiece(floor, slot, a.weight, rng)
                            if (variant < 2) {
                                hero.equipped[EquipSlot.WEAPON] = best(floor, rng, 3) { it.base == ItemBase.SWORD }
                                hero.equipped.remove(EquipSlot.OFFHAND)
                            } else {
                                hero.equipped[EquipSlot.WEAPON] = best(floor, rng, 3) { it.slot == EquipSlot.WEAPON && a.accepts(it.base) }
                                hero.equipped[EquipSlot.OFFHAND] = best(floor, rng, 3) { it.base == a.offhand }
                            }
                            relic?.let { hero.addRelic(it) }
                            hero.healFull()
                            val fightRng = Random(floor * 31L + i)
                            var ok = true
                            repeat(3) {
                                if (!ok) return@repeat
                                val before = hero.hp
                                val stat = FloorStat()
                                ok = soloFight(hero, floor, skill, fightRng, stat, useSpecial = variant % 2 == 1)
                                turns += stat.turnsInFight
                                lost += (before - if (ok) hero.hp else 0).coerceAtLeast(0).toDouble() / hero.maxHp
                                fights++
                            }
                            if (ok) wins++
                        }
                        String.format("%5.1f%% · %5.1f%% · %4.1ft", 100.0 * wins / series,
                            100.0 * lost / fights.coerceAtLeast(1), turns.toDouble() / fights.coerceAtLeast(1))
                    }
                    String.format("%-12s", a.name) + cells.joinToString(" | ") { String.format("%25s", it) }
                }.collect(java.util.stream.Collectors.toList())
                rows.forEach { out.appendLine(it) }
                out.appendLine()
            }
            File("build/roguelike-archetype-strength${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
            println(out)
        } finally { IsotopeSets.dropShare = saved }
    }

    /**
     * D'où vient la force d'un archétype, à butin égal, sans set, Spécial joué : armure de son poids, puis quatre combinaisons
     * arme (épée commune ou arme qui lui va) × main gauche (aucune ou la sienne). Cellule : victoires · PV perdus par combat.
     * SIM_SERIES=500, SIM_SKILL=CORRECT, SIM_FLOORS=50,150,400. Écrit build/roguelike-archetype-synergy.txt.
     */
    @Test
    fun archetypeSynergy() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 500
        val skill = System.getenv("SIM_SKILL")?.let { Skill.valueOf(it) } ?: Skill.CORRECT
        val floors = System.getenv("SIM_FLOORS")?.split(",")?.map { it.trim().toInt() } ?: listOf(50, 150, 400)
        val saved = IsotopeSets.dropShare
        IsotopeSets.dropShare = 0f
        try {
            val out = StringBuilder("══════ Synergies par archétype ($series séries de 3 combats, joueur $skill, sans relique) ══════\n")
            out.appendLine("colonnes : épée+rien | arme à lui+rien | épée+sa main gauche | arme à lui+sa main gauche ; cellule : victoires · PV perdus\n")
            fun best(floor: Int, rng: Random, ok: (Equipment) -> Boolean): Equipment =
                (1..3).map { var e: Equipment; do e = LootSystem.generate(floor, 0, rng) while (!ok(e) || e.isotopeZ != null); e }.maxBy { score(it) }
            for (floor in floors) {
                out.appendLine("── étage $floor ──")
                val rows = Archetype.entries.parallelStream().map { a ->
                    val cells = (0..3).map { variant ->
                        val ownWeapon = variant == 1 || variant == 3
                        val ownOffhand = variant >= 2
                        var wins = 0; var lost = 0.0; var fights = 0; var hit = 0.0; var dex = 0.0
                        repeat(series) { i ->
                            val hero = geared(floor, Random(floor * 7919L + i))
                            val rng = Random(floor * 104729L + i)
                            for (slot in IsotopeSets.SLOTS) hero.equipped[slot] = classicPiece(floor, slot, a.weight, rng)
                            hero.equipped[EquipSlot.WEAPON] =
                                if (ownWeapon) best(floor, rng) { it.slot == EquipSlot.WEAPON && a.accepts(it.base) && it.base != ItemBase.SWORD }
                                else best(floor, rng) { it.base == ItemBase.SWORD }
                            if (ownOffhand) hero.equipped[EquipSlot.OFFHAND] = best(floor, rng) { it.base == a.offhand } else hero.equipped.remove(EquipSlot.OFFHAND)
                            simRelics?.filter { it != "NONE" }?.forEach { hero.addRelic(Relic.valueOf(it)) }
                            hero.healFull()
                            hit += 1f - hero.dodgeChance(floor)
                            dex += hero.attribute(StatType.DEX)
                            val fightRng = Random(floor * 31L + i)
                            var ok = true
                            repeat(3) {
                                if (!ok) return@repeat
                                val before = hero.hp
                                ok = soloFight(hero, floor, skill, fightRng)
                                lost += (before - if (ok) hero.hp else 0).coerceAtLeast(0).toDouble() / hero.maxHp
                                fights++
                            }
                            if (ok) wins++
                        }
                        String.format("%5.1f%% · %5.1f%% · touché %3.0f%% · DEX %3.0f", 100.0 * wins / series, 100.0 * lost / fights.coerceAtLeast(1), 100.0 * hit / series, dex / series)
                    }
                    String.format("%-12s", a.name) + cells.joinToString(" | ") { String.format("%38s", it) }
                }.collect(java.util.stream.Collectors.toList())
                rows.forEach { out.appendLine(it) }
                out.appendLine()
            }
            File("build/roguelike-archetype-synergy.txt").writeText(out.toString())
            println(out)
        } finally { IsotopeSets.dropShare = saved }
    }

    /**
     * La note (celle qui trie le sac et que le bot casual suit) d'une pièce d'armure tirée au hasard, selon son poids, à puissance égale :
     * si un poids note plus haut, un joueur qui équipe « le meilleur » finit dans ce poids. SIM_SERIES=3000. build/roguelike-rating-by-weight.txt.
     */
    @Test
    fun ratingByWeight() {
        val n = System.getenv("SIM_SERIES")?.toInt() ?: 3000
        val out = StringBuilder("══════ Note moyenne d'une pièce d'armure par poids, à puissance égale ($n pièces par case, rareté rare) ══════\n")
        out.appendLine(String.format("%-8s", "étage") + ArmorWeight.entries.joinToString("") { String.format("%13s", it.name) })
        for (floor in listOf(10, 50, 150, 400, 800)) {
            val power = LootSystem.powerCenter(floor).roundToInt()
            val avg = ArmorWeight.entries.map { w ->
                val rng = Random(floor * 17L + w.ordinal)
                (1..n).map { LootSystem.rating(LootSystem.create(IsotopeSets.BASES.random(rng), power, Rarity.EPIC, 0, rng, forcedWeight = w)) }.average()
            }
            out.appendLine(String.format("%-8d", floor) + avg.joinToString("") { String.format("%13.0f", it) })
        }
        File("build/roguelike-rating-by-weight.txt").writeText(out.toString())
        println(out)
    }

    /**
     * Le guerrier paraît plus faible que le voleur et le mage à butin égal : est-ce le bouclier ? Pour chaque
     * archétype, l'armure du même poids, avec la main gauche telle que tirée, puis avec le meilleur bouclier de
     * 3 tirages, puis sans bouclier ni orbe. Sans set. SIM_SERIES=600, SIM_SKILL=CORRECT.
     */
    @Test
    fun archetypesAndShield() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 600
        val skill = System.getenv("SIM_SKILL")?.let { Skill.valueOf(it) } ?: Skill.CORRECT
        val relic = System.getenv("SIM_RELIC")?.let { Relic.valueOf(it) }   // sans relique, l'orbe ne sert à rien
        val saved = IsotopeSets.dropShare
        IsotopeSets.dropShare = 0f
        try {
            val out = StringBuilder("══════ Archétypes et main gauche (victoires · tours par combat ; ${series} séries de 3 combats, joueur $skill, relique ${relic ?: "aucune"}) ══════\n")
            for (a in Archetype.entries) {
                out.appendLine("── ${a.name} ──")
                out.appendLine(String.format("%5s%22s%22s%22s%22s%22s%14s", "Étage", "tirée", "meilleur bouclier", "meilleure orbe", "SA main gauche", "sans", "% boucliers"))
                for (floor in listOf(5, 13, 25, 50)) {
                    fun run(mode: Int): Triple<Double, Double, Double> {
                        val stuck = FloorStat()
                        var wins = 0; var lost = 0.0; var fights = 0; var shields = 0
                        repeat(series) { i ->
                            val hero = geared(floor, Random(floor * 7919L + i))
                            val rng = Random(floor * 104729L + i)
                            for (slot in IsotopeSets.SLOTS) hero.equipped[slot] = classicPiece(floor, slot, a.weight, rng)
                            if (hero.equipped[EquipSlot.OFFHAND]?.base == ItemBase.SHIELD) shields++
                            when (mode) {
                                1, 3, 4 -> {
                                    val wanted = when (mode) { 1 -> ItemBase.SHIELD; 3 -> ItemBase.ORB; else -> a.offhand }
                                    var best: Equipment? = null
                                    repeat(3) {
                                        var e: Equipment
                                        do e = LootSystem.generate(floor, 0, rng) while (e.base != wanted || e.isotopeZ != null)
                                        if (best == null || score(e) > score(best!!)) best = e
                                    }
                                    hero.equipped[EquipSlot.OFFHAND] = best!!
                                }
                                2 -> hero.equipped.remove(EquipSlot.OFFHAND)
                            }
                            relic?.let { hero.addRelic(it) }
                            hero.healFull()
                            val fightRng = Random(floor * 31L + i)
                            var ok = true
                            repeat(3) {
                                if (!ok) return@repeat
                                val before = hero.hp
                                ok = soloFight(hero, floor, skill, fightRng, stuck)
                                lost += (before - if (ok) hero.hp else 0).coerceAtLeast(0).toDouble() / hero.maxHp
                                fights++
                            }
                            if (ok) wins++
                        }
                        return Triple(100.0 * wins / series, 100.0 * shields / series, stuck.turnsInFight.toDouble() / fights.coerceAtLeast(1))
                    }
                    val tirée = run(0); val bouclier = run(1); val orbe = run(3); val sienne = run(4); val sans = run(2)
                    out.appendLine(String.format("%5d", floor) + listOf(tirée, bouclier, orbe, sienne, sans).joinToString("") { String.format("%22s", String.format("%5.1f%% · %4.1f t", it.first, it.third)) } + String.format("%13.0f%%", tirée.second))
                }
                out.appendLine()
            }
            File("build/roguelike-archetypes.txt").writeText(out.toString())
            println(out)
        } finally { IsotopeSets.dropShare = saved }
    }

    /**
     * L'équilibre des armes : pour chaque archétype (armure de son poids, sa main gauche, une relique), chaque type
     * d'arme (la meilleure de 3 tirages de ce type), à trois étages. Un * marque une arme qui ne va pas à l'archétype
     * (malus). Victoires · tours par combat. SIM_RELIC=FIREBALL, SIM_SKILL=NOVICE, SIM_SERIES=600.
     */
    @Test
    fun weaponsByArchetype() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 600
        val skill = System.getenv("SIM_SKILL")?.let { Skill.valueOf(it) } ?: Skill.CORRECT
        val relic = System.getenv("SIM_RELIC")?.let { Relic.valueOf(it) } ?: Relic.FIREBALL
        // Toutes les armes du jeu : une arme ajoutée plus tard entre d'elle-même dans le banc
        val weapons = ItemBase.entries.filter { it.slot == EquipSlot.WEAPON }
        val saved = IsotopeSets.dropShare
        IsotopeSets.dropShare = 0f
        try {
            val out = StringBuilder("══════ Armes par archétype (victoires · tours ; $series séries de 3 combats, joueur $skill, relique $relic) ══════\n")
            for (a in Archetype.entries) {
                out.appendLine("── ${a.name} (armes : ${(listOf(ItemBase.SWORD) + a.weapons).joinToString(" ") { it.name }}) ──")
                out.appendLine(String.format("%5s", "Étage") + weapons.joinToString("") { String.format("%18s", it.name + if (a.accepts(it)) "" else "*") })
                val rows = listOf(13, 25, 50).parallelStream().map { floor ->
                    val cells = weapons.map { wb ->
                        val stuck = FloorStat()
                        var wins = 0; var fights = 0
                        repeat(series) { i ->
                            val hero = geared(floor, Random(floor * 7919L + i))
                            val rng = Random(floor * 104729L + i)
                            for (slot in IsotopeSets.SLOTS) hero.equipped[slot] = classicPiece(floor, slot, a.weight, rng)
                            var best: Equipment? = null
                            repeat(3) {
                                var e: Equipment
                                do e = LootSystem.generate(floor, 0, rng) while (e.base != wb || e.isotopeZ != null)
                                if (best == null || score(e) > score(best!!)) best = e
                            }
                            hero.equipped[EquipSlot.WEAPON] = best!!
                            var offhand: Equipment? = null
                            repeat(3) {
                                var e: Equipment
                                do e = LootSystem.generate(floor, 0, rng) while (e.base != a.offhand || e.isotopeZ != null)
                                if (offhand == null || score(e) > score(offhand!!)) offhand = e
                            }
                            hero.equipped[EquipSlot.OFFHAND] = offhand!!
                            hero.addRelic(relic)
                            hero.healFull()
                            val fightRng = Random(floor * 31L + i)
                            var ok = true
                            repeat(3) {
                                if (!ok) return@repeat
                                ok = soloFight(hero, floor, skill, fightRng, stuck)
                                fights++
                            }
                            if (ok) wins++
                        }
                        String.format("%5.1f%% · %4.1f t", 100.0 * wins / series, stuck.turnsInFight.toDouble() / fights.coerceAtLeast(1))
                    }
                    String.format("%5d", floor) + cells.joinToString("") { String.format("%18s", it) }
                }.collect(java.util.stream.Collectors.toList())
                rows.forEach { out.appendLine(it) }
                out.appendLine()
            }
            File("build/roguelike-weapons.txt").writeText(out.toString())
            println(out)
        } finally { IsotopeSets.dropShare = saved }
    }

    /** SIM_SETS_ONLY=WARRIOR,BARBARIAN ; les six classes par défaut, sans doublon. */
    private fun simSets(): List<IsotopeSet> {
        val only = System.getenv("SIM_SETS_ONLY")?.split(",")?.map { Archetype.valueOf(it.trim()) }
        return IsotopeSets.PERMANENT.filter { only == null || it.archetype in only }
    }

    /** Mêmes profondeurs pour toutes les classes ; aucune ancienne tranche isotope. */
    private fun setFloors() = System.getenv("SIM_FLOORS")?.split(",")?.map { it.trim().toInt().also { floor ->
        require(floor >= 1) { "SIM_FLOORS : les étages doivent être positifs" }
    } } ?: listOf(1, 25, 100, 250, 500)
    /**
     * Ce que valent les sets : pour chacun, aux mêmes étages, le même héros
     * avec l'armure du butin classique, du butin du même poids, les pièces du set sans bonus, puis
     * avec le bonus. Sans relique (le bot joue son Spécial, le bonus s'y applique), joueur « correct ».
     * SIM_SERIES=600, SIM_SKILL=EXPERT, SIM_CHAIN=3.
     */
    @Test
    fun isotopeSetsAtFixedGear() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 600
        val chain = System.getenv("SIM_CHAIN")?.toInt() ?: 3
        val skill = System.getenv("SIM_SKILL")?.let { Skill.valueOf(it) } ?: Skill.CORRECT
        val start = System.currentTimeMillis()
        val saved = IsotopeSets.dropShare
        IsotopeSets.dropShare = 0f   // le butin « ordinaire » ne doit contenir aucune pièce de set
        try {
            val out = StringBuilder("══════ Sets spéciaux à équipement égal ($series séries de $chain combats, joueur $skill) ══════\n")
            out.appendLine("victoires · PV perdus par combat · note de l'armure ; la comparaison juste est « même poids » contre « set »\n")
            for (set in simSets()) {
                out.appendLine("── SET ${set.archetype} (permanent, tier supérieur) ──")
                out.appendLine(String.format("%5s", "Étage") + SetGear.entries.joinToString("") { String.format("%26s", it.label) })
                val floors = setFloors()
                val rows = floors.parallelStream().map { floor ->
                    val cells = SetGear.entries.map { measureSetCell(floor, it, set, series, chain, null, skill) }
                    String.format("%5d", floor) + cells.joinToString("") {
                        String.format("%26s", String.format("%5.1f%% · %4.1f%% · %5.0f", it.wins, it.lost, it.rating))
                    }
                }.collect(java.util.stream.Collectors.toList())
                rows.forEach { out.appendLine(it) }
                out.appendLine()
            }
            out.appendLine("(${(System.currentTimeMillis() - start) / 1000} s)")
            File("build/roguelike-sets.txt").writeText(out.toString())
            println(out)
        } finally { IsotopeSets.dropShare = saved }
    }

    /**
     * Les sets avec toutes sortes de reliques : chaque relique seule (et « aucun sort »), aux mêmes étages pour chaque classe, l'armure du même poids contre le set. Une cellule : victoires de l'un → de l'autre.
     * SIM_SERIES=300, SIM_SETS_ONLY=WARRIOR,BARBARIAN et SIM_FLOORS=25,100,500.
     */
    @Test
    fun isotopeSetsWithRelics() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 300
        val chain = System.getenv("SIM_CHAIN")?.toInt() ?: 3
        val configs = listOf<Relic?>(null) + Relic.entries.filter { simRelics == null || it.name in simRelics }
        val start = System.currentTimeMillis()
        val saved = IsotopeSets.dropShare
        IsotopeSets.dropShare = 0f
        try {
            val out = StringBuilder("══════ Sets spéciaux avec une relique ($series séries de $chain combats, joueur correct) ══════\n")
            out.appendLine("cellule : victoires « même poids » → « set », en %\n")
            for (set in simSets()) {
                val floors = setFloors()
                out.appendLine("── SET ${set.archetype} (permanent, tier supérieur) ──")
                out.appendLine(String.format("%-17s", "Étage") + floors.joinToString("") { String.format("%18d", it) })
                val rows = configs.parallelStream().map { relic ->
                    val cells = floors.map { floor ->
                        val base = measureSetCell(floor, SetGear.SAME_ARCHETYPE, set, series, chain, relic, Skill.CORRECT)
                        val with = measureSetCell(floor, SetGear.SET, set, series, chain, relic, Skill.CORRECT)
                        String.format("%5.1f → %5.1f", base.wins, with.wins)
                    }
                    String.format("%-17s", relic?.name ?: "aucun sort") + cells.joinToString("") { String.format("%18s", it) }
                }.collect(java.util.stream.Collectors.toList())
                rows.forEach { out.appendLine(it) }
                out.appendLine()
            }
            out.appendLine("(${(System.currentTimeMillis() - start) / 1000} s)")
            File("build/roguelike-sets-relics.txt").writeText(out.toString())
            println(out)
        } finally { IsotopeSets.dropShare = saved }
    }

    /**
     * Chaque relique seule, à équipement égal, contre « aucun sort : c'est ce qui dit si
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
        val chain = System.getenv("SIM_CHAIN")?.toInt() ?: CHAIN
        val configs = listOf<Relic?>(null) + Relic.entries.filter { simRelics == null || it.name in simRelics }
        // SIM_BASE_RELICS=MAGIC_MISSILE,FREEZING_RAIN : un build de base porté par tous ; chaque relique s'y ajoute (ce qu'elle vaut **dans un build**)
        val baseRelics = System.getenv("SIM_BASE_RELICS")?.split(",")?.map { Relic.valueOf(it) } ?: emptyList()
        val start = System.currentTimeMillis()
        val rows = configs.parallelStream().map { relic ->
            val stuck = FloorStat()
            val cells = floors.map { floor ->
                val rng = Random(floor * 131L)
                var wins = 0
                var lost = 0.0; var fights = 0
                repeat(series) {
                    val hero = geared(floor, rng)
                    baseRelics.forEach { hero.addRelic(it) }
                    relic?.let { hero.addRelic(it) }
                    var ok = true
                    repeat(chain) {
                        if (!ok) return@repeat
                        val before = hero.hp
                        ok = soloFight(hero, floor, Skill.CORRECT, rng, stuck)
                        lost += (before - if (ok) hero.hp else 0).coerceAtLeast(0).toDouble() / hero.maxHp
                        fights++
                    }
                    if (ok) wins++
                }
                String.format("%5.1f %% · %4.1f %%", 100.0 * wins / series, 100.0 * lost / fights.coerceAtLeast(1))
            }
            String.format("%-17s", relic?.name ?: "aucun sort") + cells.joinToString("") { String.format("%18s", it) } +
                if (stuck.stuck > 0) "   ⚠ ${stuck.stuck} combats bloqués" else ""
        }.collect(java.util.stream.Collectors.toList())
        val out = StringBuilder("══════ Reliques seules à équipement égal ($series séries de $chain combats par case, joueur correct) ══════\n")
        out.appendLine(String.format("%-17s", "Étage") + floors.joinToString("") { String.format("%18d", it) })
        rows.forEach { out.appendLine(it) }
        out.appendLine("(victoires sur $chain combats enchaînés · PV perdus par combat, en % des PV max ; ${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-relics${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
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
        // SIM_CHAIN=1 : un combat seul à PV pleins, comme en jeu (le héros récupère après chaque victoire isolée)
        val chain = System.getenv("SIM_CHAIN")?.toInt() ?: 3
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
                repeat(chain) { if (ok) ok = soloFight(hero, floor, Skill.CORRECT, rng, stuck, combos) }
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
        val out = StringBuilder("══════ Combos à équipement égal ($series séries de $chain combats, joueur correct) : au hasard → en combo ══════\n")
        out.appendLine(String.format("%-34s", "Étage") + floors.joinToString("") { String.format("%18d", it) })
        out.appendLine(String.format("%-34s", "aucun sort") + base.joinToString("") { String.format("%18s", String.format("%5.1f", it)) })
        rows.forEach { out.appendLine(it) }
        out.appendLine("(victoires sur $chain combats enchaînés ; ${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-combos.txt").writeText(out.toString())
        println(out)
    }

    /** Ce qu'un bot du banc [relicBuildsByGroup] a vécu : son build, et par taille de groupe (1 à 3) ses combats. */
    private class BuildRun(val relics: List<Relic>, val archetype: Archetype?) {
        val fights = IntArray(4); val deaths = IntArray(4); val stuck = IntArray(4); val winTurns = LongArray(4)
        val casts = mutableMapOf<Relic, Int>()
    }

    /**
     * Les builds de reliques à l'étage 100 : chaque bot tire un équipement de l'étage 95 (le meilleur de
     * 15 objets, au hasard), reçoit **toutes** les reliques et choisit ses 3 (les emplacements ouverts à
     * l'étage 100) par [chooseRelics], essais à PV pleins. Puis il enchaîne des combats contre 1, 2 et 3
     * ennemis (3 = un boss et deux accompagnants, comme en jeu), soigné et recharges prêtes avant chacun.
     * Un combat qui atteint la limite de tours est arrêté et compté à part (bloqué).
     *
     * On y lit les morts par groupe, les reliques les plus portées, et surtout celles qui sont portées
     * quand le bot meurt. Réglages : SIM_HEROES=64 (bots), SIM_FIGHTS=100 (combats par bot et par
     * taille de groupe), SIM_GEAR_FLOOR=95, SIM_FLOOR=100, SIM_TURN_LIMIT=500, SIM_SKILL=CORRECT,
     * SIM_TRIAL_SERIES=36 (combats d'essai par combinaison). Écrit build/roguelike-builds.txt.
     */
    @Test
    fun relicBuildsByGroup() {
        val heroes = System.getenv("SIM_HEROES")?.toInt() ?: 64
        val fightsPerGroup = System.getenv("SIM_FIGHTS")?.toInt() ?: 100
        val gearFloor = System.getenv("SIM_GEAR_FLOOR")?.toInt() ?: 95
        val floor = System.getenv("SIM_FLOOR")?.toInt() ?: 100
        val turnLimit = System.getenv("SIM_TURN_LIMIT")?.toInt() ?: 500
        val skill = System.getenv("SIM_SKILL")?.let(Skill::valueOf) ?: Skill.CORRECT
        val trialSeries = System.getenv("SIM_TRIAL_SERIES")?.toInt() ?: 36
        val start = System.currentTimeMillis()
        val runs = (0 until heroes).toList().parallelStream().map { i ->
            val rng = Random(gearFloor * 7919L + i)
            val template = geared(gearFloor, rng).apply {
                this.floor = floor
                deepestFloor = floor
                relicSlots.fill(null)
                relics.clear()
                Relic.entries.filter { it != Relic.HOURGLASS }.forEach { relics += it }
                chooseRelics(this, floor, skill, trialChain = 1, series = trialSeries)
            }
            val worn = template.relicSlots.filterNotNull()
            val run = BuildRun(worn, template.archetype)
            for (count in 1..3) repeat(fightsPerGroup) { k ->
                val hero = trialCopy(template, worn)
                val fightRng = Random(i * 1_000_003L + count * 10_007L + k)
                val family = Encounters.roll(floor, fightRng).first()
                val combat = Combat(hero, floor, Encounters.build(List(count) { family }, floor), ambush = false, rng = fightRng)
                val stat = FloorStat()
                val cs = CombatStat()
                playCombat(combat, skill, stat, fightRng, combos = true, cs = cs, maxTurns = turnLimit)
                run.fights[count]++
                when {
                    combat.phase == CombatPhase.VICTORY -> run.winTurns[count] += stat.turnsInFight.toLong()
                    hero.hp <= 0 -> run.deaths[count]++
                    else -> run.stuck[count]++
                }
                cs.castBy.forEach { (r, n) -> run.casts.merge(r, n, Int::plus) }
            }
            run
        }.collect(java.util.stream.Collectors.toList())

        fun pct(a: Int, b: Int) = if (b == 0) "   —  " else String.format("%5.1f%%", 100.0 * a / b)
        val out = StringBuilder("══════ Builds de reliques à l'étage $floor ($heroes bots, équipement de l'étage $gearFloor, $skill) ══════\n")
        out.appendLine("$fightsPerGroup combats par bot et par taille de groupe, soin complet et recharges prêtes avant chacun ; limite $turnLimit tours ; ${runs.first().relics.size} reliques portées.")
        out.appendLine("Choix des reliques : chaque relique seule, puis les combinaisons des 8 meilleures, $trialSeries combats d'essai chacune (groupes au hasard).")

        out.appendLine("\n── Morts par groupe ──")
        out.appendLine("Ennemis | combats | morts | bloqués | victoires | tours par victoire")
        for (count in 1..3) {
            val f = runs.sumOf { it.fights[count] }; val d = runs.sumOf { it.deaths[count] }; val s = runs.sumOf { it.stuck[count] }
            val w = f - d - s
            out.appendLine(String.format("%7s | %7d | %5d (%s) | %4d | %s | %5.1f", if (count == 3) "3 (boss)" else "$count",
                f, d, pct(d, f), s, pct(w, f), runs.sumOf { it.winTurns[count] }.toDouble() / w.coerceAtLeast(1)))
        }
        val botsWithDeath = (1..3).map { c -> runs.count { it.deaths[c] > 0 } }
        out.appendLine("Bots morts au moins une fois : 1 ennemi ${botsWithDeath[0]}/$heroes · 2 ennemis ${botsWithDeath[1]}/$heroes · 3 ennemis ${botsWithDeath[2]}/$heroes")

        val totalDeaths = IntArray(4) { c -> runs.sumOf { it.deaths[c] } }
        val allDeaths = totalDeaths.sum()
        out.appendLine("\n── Reliques : portées, lancées, et présentes aux morts ──")
        out.appendLine("« part des morts » : parmi toutes les morts du groupe, celles où la relique était équipée. « taux » : morts / combats quand elle est portée.")
        out.appendLine(String.format("%-16s | %5s | %9s | %-24s | %-24s | %-24s | %s", "Relique", "bots", "lancers/c",
            "1 ennemi : part · taux", "2 ennemis : part · taux", "3 ennemis : part · taux", "toutes morts"))
        val relicRows = Relic.entries.filter { it != Relic.HOURGLASS }.map { r -> r to runs.filter { r in it.relics } }
            .sortedWith(compareByDescending<Pair<Relic, List<BuildRun>>> { (_, with) -> with.sumOf { it.deaths.sum() } }.thenByDescending { it.second.size })
        for ((r, with) in relicRows) {
            val fights = with.sumOf { it.fights.sum() }
            val cells = (1..3).map { c ->
                val d = with.sumOf { it.deaths[c] }
                "${pct(d, totalDeaths[c])} · ${pct(d, with.sumOf { it.fights[c] })}"
            }
            out.appendLine(String.format("%-16s | %2d/%-2d | %9s | %-24s | %-24s | %-24s | %s", r.name, with.size, heroes,
                if (fights == 0) "—" else String.format("%.2f", runs.sumOf { it.casts[r] ?: 0 }.toDouble() / fights),
                cells[0], cells[1], cells[2], pct(with.sumOf { it.deaths.sum() }, allDeaths)))
        }

        out.appendLine("\n── Builds (trio de reliques) : du plus meurtrier au plus sûr ──")
        out.appendLine("Build | bots | archétypes | morts 1 · 2 · 3 ennemis (taux) | bloqués")
        runs.groupBy { it.relics.map { r -> r.name }.sorted().joinToString(" + ") }
            .entries.sortedByDescending { (_, l) -> l.sumOf { it.deaths.sum() }.toDouble() / l.sumOf { it.fights.sum() } }
            .forEach { (build, l) ->
                val arch = l.groupingBy { it.archetype?.name ?: "aucun" }.eachCount().entries.joinToString(",") { "${it.key}×${it.value}" }
                out.appendLine("$build | ${l.size} | $arch | " + (1..3).joinToString(" · ") { c ->
                    pct(l.sumOf { it.deaths[c] }, l.sumOf { it.fights[c] }) } + " | ${l.sumOf { it.stuck.sum() }}")
            }

        out.appendLine("\n── Par archétype du bot (majorité de poids d'armure) ──")
        runs.groupBy { it.archetype?.name ?: "aucun" }.toSortedMap().forEach { (a, l) ->
            out.appendLine(String.format("%-12s | %2d bots | morts %s", a, l.size, (1..3).joinToString(" · ") { c ->
                pct(l.sumOf { it.deaths[c] }, l.sumOf { it.fights[c] }) }))
        }
        out.appendLine("\n(${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-builds${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
        println(out)
    }

    /** Une rareté d'équipement du banc [archetypesByRarity] : les trois du butin, et le set de classe. */
    private enum class GearTier(val label: String, val rarity: Rarity, val set: Boolean = false) {
        NORMAL("normal", Rarity.COMMON), MAGIC("magique", Rarity.RARE), RARE("rare", Rarity.EPIC), SET("set", Rarity.EPIC, set = true)
    }

    /**
     * L'équipement complet d'une classe, **mêmes dés pour toutes** : pour le bot [i], chaque emplacement a sa
     * graine (puissance de l'étage [gearFloor], affixes), quelle que soit la classe ou la rareté. Seuls changent
     * le poids de l'armure (celui de la classe, qui en donne la caractéristique), l'arme et la main gauche de
     * la classe, et la rareté. Le set : des pièces rares à la même puissance, avec le bonus des trois pièces.
     */
    private fun classGear(a: Archetype, tier: GearTier, i: Int, gearFloor: Int): Map<EquipSlot, Equipment> {
        val bases = mapOf(
            EquipSlot.WEAPON to a.weapons.first(), EquipSlot.OFFHAND to a.offhand,
            EquipSlot.HELMET to ItemBase.HELMET, EquipSlot.CHEST to ItemBase.ARMOR, EquipSlot.BOOTS to ItemBase.BOOTS,
            EquipSlot.AMULET to ItemBase.AMULET, EquipSlot.RING to ItemBase.RING,
        )
        return bases.entries.associate { (slot, base) ->
            val rng = Random(gearFloor * 1_000_003L + i * 101L + slot.ordinal)
            val power = LootSystem.rollPowerForFloor(gearFloor, rng)
            val item = LootSystem.create(base, power, tier.rarity, 0, rng, forcedWeight = if (base in ArmorWeight.WEIGHTED) a.weight else null)
            slot to if (tier.set && slot in IsotopeSets.SLOTS) item.copy(isotopeZ = IsotopeSets.forArchetype(a).index) else item
        }
    }

    /**
     * Les classes à équipement égal, et ce que vaut la rareté : chaque classe porte un équipement complet de sa
     * classe ([classGear], mêmes dés pour toutes) en normal, magique, rare, puis en set. À l'étage 100, elle
     * enchaîne des combats contre 1, 2 et 3 ennemis (soin complet et recharges prêtes avant chacun, mêmes
     * graines de combat pour toutes), sans relique puis avec les 3 reliques qu'elle choisit parmi toutes.
     * Réglages : SIM_HEROES=40 (tirages d'équipement), SIM_FIGHTS=100 (par tirage et par taille de groupe),
     * SIM_GEAR_FLOOR=95, SIM_FLOOR=100, SIM_TURN_LIMIT=500, SIM_SKILL=CORRECT, SIM_TRIAL_SERIES=36,
     * SIM_PERK=1 (chance d'atout imposée à tous ; celle du jeu sinon), SIM_MONSTER_DMG=1.1 (coups des monstres).
     * Écrit build/roguelike-rarity.txt.
     */
    @Test
    fun archetypesByRarity() {
        val perk = System.getenv("SIM_PERK")?.toFloat()
        val monsterDamage = System.getenv("SIM_MONSTER_DMG")?.toFloat() ?: Dodge.damageMult
        val savedDamage = Dodge.damageMult
        Hero.classPerkOverride = perk
        Dodge.damageMult = monsterDamage
        try { archetypesByRarityRun(perk, monsterDamage) } finally {
            Hero.classPerkOverride = null
            Dodge.damageMult = savedDamage
        }
    }

    private fun archetypesByRarityRun(perk: Float?, monsterDamage: Float) {
        val heroes = System.getenv("SIM_HEROES")?.toInt() ?: 40
        val fightsPerGroup = System.getenv("SIM_FIGHTS")?.toInt() ?: 100
        val gearFloor = System.getenv("SIM_GEAR_FLOOR")?.toInt() ?: 95
        val floor = System.getenv("SIM_FLOOR")?.toInt() ?: 100
        val turnLimit = System.getenv("SIM_TURN_LIMIT")?.toInt() ?: 500
        val skill = System.getenv("SIM_SKILL")?.let(Skill::valueOf) ?: Skill.CORRECT
        val trialSeries = System.getenv("SIM_TRIAL_SERIES")?.toInt() ?: 36
        val start = System.currentTimeMillis()

        class Cell(val a: Archetype, val tier: GearTier) {
            val deaths = Array(2) { IntArray(4) }; val stuck = Array(2) { IntArray(4) }; val fights = Array(2) { IntArray(4) }
            val winTurns = Array(2) { LongArray(4) }
            var hp = 0.0; var armor = 0.0; var dodge = 0.0; var weapon = 0.0; var mainStat = 0.0; var crit = 0.0; var speed = 0.0; var perkChance = 0.0
            val relics = mutableMapOf<Relic, Int>()
        }
        val jobs = Archetype.entries.flatMap { a -> GearTier.entries.map { a to it } }
        val cells = jobs.parallelStream().map { (a, tier) ->
            val cell = Cell(a, tier)
            for (i in 0 until heroes) {
                val template = heroWithAllSlots().apply {
                    this.floor = floor
                    deepestFloor = floor
                    equipped.putAll(classGear(a, tier, i, gearFloor))
                    healFull()
                }
                check(template.archetype == a && (!tier.set || template.setArchetype == a))
                cell.hp += template.maxHp; cell.armor += template.armor; cell.dodge += template.dodgeChance(floor)
                cell.weapon += (template.weaponMin + template.weaponMax) / 2.0
                cell.mainStat += template.attribute(preferredStat(a)); cell.crit += template.critChance(floor); cell.speed += template.speed
                cell.perkChance += template.classPerkChance
                val chosen = template.apply {
                    relicSlots.fill(null)
                    relics.clear()
                    Relic.entries.filter { it != Relic.HOURGLASS }.forEach { relics += it }
                    chooseRelics(this, floor, skill, trialChain = 1, series = trialSeries)
                }.relicSlots.filterNotNull()
                chosen.forEach { cell.relics.merge(it, 1, Int::plus) }
                for ((v, worn) in listOf(emptyList<Relic>(), chosen).withIndex()) for (count in 1..3) repeat(fightsPerGroup) { k ->
                    val hero = trialCopy(template, worn)
                    val fightRng = Random(i * 1_000_003L + count * 10_007L + k)
                    val family = Encounters.roll(floor, fightRng).first()
                    val combat = Combat(hero, floor, Encounters.build(List(count) { family }, floor), ambush = false, rng = fightRng)
                    val stat = FloorStat()
                    playCombat(combat, skill, stat, fightRng, combos = true, maxTurns = turnLimit)
                    cell.fights[v][count]++
                    when {
                        combat.phase == CombatPhase.VICTORY -> cell.winTurns[v][count] += stat.turnsInFight.toLong()
                        hero.hp <= 0 -> cell.deaths[v][count]++
                        else -> cell.stuck[v][count]++
                    }
                }
            }
            cell
        }.collect(java.util.stream.Collectors.toList())

        fun pct(x: Int, n: Int) = String.format("%5.1f%%", 100.0 * x / n.coerceAtLeast(1))
        val out = StringBuilder("══════ Classes à équipement égal, par rareté (étage $floor, équipement de l'étage $gearFloor, $skill) ══════\n")
        out.appendLine("$heroes tirages d'équipement par classe (mêmes dés pour toutes), $fightsPerGroup combats par tirage et par taille de groupe ; soin complet avant chaque combat ; limite $turnLimit tours.")
        out.appendLine("Normal : 0 affixe · magique : 1-2 · rare : 3-4 · set : rare + bonus des 3 pièces (même puissance que le rare).")
        out.appendLine("Atout de classe sur parade parfaite : ${perk?.let { "imposé à ${(it * 100).roundToInt()} %" } ?: "règle du jeu (${(Hero.BASE_CLASS_PERK * 100).roundToInt()} % + affixes, plafond ${(Hero.MAX_CLASS_PERK * 100).roundToInt()} %)"} ; coups des monstres ×$monsterDamage.")

        out.appendLine("\n── Stats moyennes du héros ──")
        out.appendLine(String.format("%-12s %-8s | %6s | %6s | %7s | %6s | %s | %6s | %5s | %5s", "Classe", "Rareté", "PV max", "Armure", "Esquive", "Arme", "Carac. de classe", "Crit.", "Vit.", "Atout"))
        for (c in cells) {
            val n = heroes.toDouble()
            out.appendLine(String.format("%-12s %-8s | %6.0f | %6.0f | %6.1f%% | %6.0f | %4s %5.1f       | %5.1f%% | %5.2f | %4.0f%%", c.a, c.tier.label,
                c.hp / n, c.armor / n, 100 * c.dodge / n, c.weapon / n, preferredStat(c.a), c.mainStat / n, 100 * c.crit / n, c.speed / n, 100 * c.perkChance / n))
        }

        for ((v, title) in listOf("sans relique", "avec 3 reliques choisies par le bot").withIndex()) {
            out.appendLine("\n── Morts, $title : 1 · 2 · 3 ennemis (boss) — tours par victoire contre 3 ──")
            out.appendLine(String.format("%-12s | %-26s | %-26s | %-26s | %-26s", "Classe", *GearTier.entries.map { it.label }.toTypedArray()))
            for (a in Archetype.entries) {
                val row = GearTier.entries.map { t ->
                    val c = cells.first { it.a == a && it.tier == t }
                    val w3 = c.fights[v][3] - c.deaths[v][3] - c.stuck[v][3]
                    (1..3).joinToString(" ") { pct(c.deaths[v][it], c.fights[v][it]).trim() } +
                        String.format(" · %.0f t", c.winTurns[v][3].toDouble() / w3.coerceAtLeast(1)) +
                        (c.stuck[v].sum().takeIf { it > 0 }?.let { " ⚠$it" } ?: "")
                }
                out.appendLine(String.format("%-12s | %-26s | %-26s | %-26s | %-26s", a, *row.toTypedArray()))
            }
        }

        out.appendLine("\n── Reliques choisies (sur $heroes tirages), par classe, toutes raretés confondues ──")
        for (a in Archetype.entries) {
            val counts = mutableMapOf<Relic, Int>()
            cells.filter { it.a == a }.forEach { c -> c.relics.forEach { (r, n) -> counts.merge(r, n, Int::plus) } }
            out.appendLine(String.format("%-12s ", a) + counts.entries.sortedByDescending { it.value }.take(6).joinToString("  ") { "${it.key.name} ×${it.value}" })
        }
        out.appendLine("\n(${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-rarity${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
        println(out)
    }

    /**
     * Ce qui tue une classe : mêmes équipements que [archetypesByRarity] (mêmes dés, étage 100, équipement
     * de l'étage 95), contre 3 ennemis (un boss et deux accompagnants), soin complet avant chaque combat.
     * Pour chaque classe, en normal et en rare, sans relique puis avec les 3 qu'elle choisit : les coups
     * reçus (esquivés, évités par l'atout, pris par un double, encaissés), ce que coûte un coup encaissé,
     * et d'où viennent les dégâts infligés (attaque, relique, Spécial, pendant les tours ennemis).
     * Réglages : SIM_HEROES=30, SIM_FIGHTS=100, SIM_ENEMIES=3, SIM_SKILL=CORRECT. Écrit build/roguelike-diagnosis.txt.
     */
    @Test
    fun classDiagnosis() {
        val heroes = System.getenv("SIM_HEROES")?.toInt() ?: 30
        val fights = System.getenv("SIM_FIGHTS")?.toInt() ?: 100
        val count = System.getenv("SIM_ENEMIES")?.toInt() ?: 3
        val skill = System.getenv("SIM_SKILL")?.let(Skill::valueOf) ?: Skill.CORRECT
        val gearFloor = 95; val floor = 100
        val start = System.currentTimeMillis()

        class Row(val a: Archetype, val tier: GearTier, val withRelics: Boolean) {
            val cs = CombatStat(); var fights = 0; var deaths = 0; var turns = 0L
            var maxHpSum = 0L; var enemyHpSum = 0L
        }
        val jobs = Archetype.entries.flatMap { a -> listOf(GearTier.NORMAL, GearTier.RARE).flatMap { t -> listOf(false, true).map { Triple(a, t, it) } } }
        val rows = jobs.parallelStream().map { (a, tier, withRelics) ->
            val row = Row(a, tier, withRelics)
            for (i in 0 until heroes) {
                val template = heroWithAllSlots().apply {
                    this.floor = floor
                    deepestFloor = floor
                    equipped.putAll(classGear(a, tier, i, gearFloor))
                    relicSlots.fill(null)
                    relics.clear()
                    healFull()
                }
                val worn = if (!withRelics) emptyList() else {
                    Relic.entries.filter { it != Relic.HOURGLASS }.forEach { template.relics += it }
                    chooseRelics(template, floor, skill, trialChain = 1, series = 36)
                    template.relicSlots.filterNotNull()
                }
                repeat(fights) { k ->
                    val hero = trialCopy(template, worn)
                    val rng = Random(i * 1_000_003L + count * 10_007L + k)
                    val family = Encounters.roll(floor, rng).first()
                    val enemies = Encounters.build(List(count) { family }, floor)
                    row.enemyHpSum += enemies.sumOf { it.maxHp.toLong() }
                    row.maxHpSum += hero.maxHp
                    val combat = Combat(hero, floor, enemies, ambush = false, rng = rng)
                    val stat = FloorStat()
                    playCombat(combat, skill, stat, rng, combos = true, cs = row.cs, maxTurns = 500)
                    row.fights++
                    row.turns += stat.turnsInFight
                    if (hero.hp <= 0) row.deaths++
                }
            }
            row
        }.collect(java.util.stream.Collectors.toList())

        fun pct(x: Double) = String.format("%5.1f%%", 100 * x)
        val out = StringBuilder("══════ Ce qui tue chaque classe (étage $floor, équipement de l'étage $gearFloor, $count ennemis, $skill) ══════\n")
        out.appendLine("$heroes tirages × $fights combats par ligne ; soin complet avant chaque combat. Coups reçus : ce que les monstres ont lancé sur le héros.")
        out.appendLine("« coups pour mourir » = PV max / dégâts d'un coup encaissé. Dégâts infligés : par action du héros, en % des PV du groupe ennemi.")
        for (withRelics in listOf(false, true)) {
            out.appendLine("\n── ${if (withRelics) "Avec 3 reliques choisies" else "Sans relique"} ──")
            out.appendLine(String.format("%-12s %-6s | %6s | %5s | %5s | %6s | %6s | %6s | %6s | %7s | %6s | %7s | %s",
                "Classe", "Rareté", "Morts", "Tours", "Coups", "Esquiv", "Atout", "Double", "Touché", "Coup/PV", "Pr mour", "Dég/act", "Part des dégâts : attaque · relique · Spécial · tour ennemi (renvoi, riposte, poisons)"))
            for (r in rows.filter { it.withRelics == withRelics }) {
                val cs = r.cs; val n = r.fights.toDouble()
                val att = cs.enemyAttacks.coerceAtLeast(1).toDouble()
                val hitCost = cs.damageTaken.toDouble() / cs.landed.coerceAtLeast(1) / (r.maxHpSum / n)
                val dealt = cs.damageBy.values.sum() + cs.enemyTurnDamage
                val actions = cs.actionsBy.values.sum().coerceAtLeast(1)
                fun share(x: Long) = pct(x.toDouble() / dealt.coerceAtLeast(1))
                out.appendLine(String.format("%-12s %-6s | %6s | %5.1f | %5.1f | %6s | %6s | %6s | %6s | %7s | %7.1f | %7s | %s · %s · %s · %s",
                    r.a, r.tier.label, pct(r.deaths / n), r.turns / n, cs.enemyAttacks / n,
                    pct(cs.dodged / att), pct(cs.perks / att), pct(cs.imageHits / att), pct(cs.landed / att),
                    pct(hitCost), 1 / hitCost, pct(cs.damageBy.values.sum().toDouble() / actions / (r.enemyHpSum / n)),
                    share(cs.damageBy["attaque"] ?: 0), share(cs.damageBy["relique"] ?: 0), share(cs.damageBy["Spécial"] ?: 0), share(cs.enemyTurnDamage)))
            }
        }
        out.appendLine("\n(${(System.currentTimeMillis() - start) / 1000} s)")
        File("build/roguelike-diagnosis${System.getenv("SIM_OUT") ?: ""}.txt").writeText(out.toString())
        println(out)
    }

    // ── Le bot joueur de combos : ce que sait un joueur qui connaît le jeu ──────

    /** [r] lancé sur [e] déclencherait une réaction (ou son bonus contre un figé). */
    private fun triggers(r: Relic, e: Enemy): Boolean {
        if (e.type.affinity(r.element) == Affinity.IMMUNE) return false
        // Un sort sur soi (Sablier) ne touche pas l'ennemi : il ne déclenche rien
        if (r.target == RelicTarget.SELF) return false
        if (r.effect == RelicEffect.CRYSTALLIZE && e.frozen) return true
        val states = buildSet {
            if (e.burnTurns > 0) add("burning"); if (e.frozen) add("frozen"); if (e.paralyzedTurns > 0) add("paralyzed")
            if (e.poisonTurns > 0) add("poisoned"); if (e.fracturedTurns > 0 || e.blindedTurns > 0 || e.marked) add("exposed")
        }
        return states.any { reactsTo(r.element, it) }
    }

    /** Une réaction existe-t-elle entre un coup de cet élément et cet état ? (voir [Reaction]) */
    private fun reactsTo(el: Element, state: String): Boolean = when (el) {
        Element.FIRE -> state in setOf("frozen", "poisoned", "exposed", "paralyzed")
        Element.ICE -> state in setOf("burning", "paralyzed", "poisoned", "exposed")
        Element.LIGHTNING -> state in setOf("burning", "poisoned", "frozen", "exposed")
        Element.POISON -> state in setOf("burning", "frozen", "paralyzed", "exposed")
        Element.PHYSICAL, Element.HOLY -> state in setOf("frozen", "paralyzed", "poisoned", "burning")
    }

    /** [setup] pose l'état que [payoff] vient ensuite déclencher (ou une résonance les lie). */
    private fun prepares(setup: Relic, payoff: Relic): Boolean {
        if (!payoff.hits) return false
        val states = when (setup.effect) {
            RelicEffect.BURN -> setOf("burning")
            RelicEffect.FREEZE -> setOf("frozen")
            RelicEffect.PARALYZE -> setOf("paralyzed")
            RelicEffect.POISON, RelicEffect.ENCHANT_POISON -> setOf("poisoned")
            RelicEffect.ACID -> setOf("poisoned", "exposed")
            RelicEffect.FRACTURE, RelicEffect.MARK, RelicEffect.BLIND -> setOf("exposed")
            else -> emptySet()
        }
        return states.any { reactsTo(payoff.element, it) } || (setup.effect == RelicEffect.FREEZE && payoff.effect == RelicEffect.CRYSTALLIZE)
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
        // Un ennemi fragile (la Rigidité) : l'attaque normale suivante fait double, on la lui donne
        alive.firstOrNull { c.enemies[it].fragile }?.let { return null to it }
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

    /**
     * Le bot choisit ses reliques comme un joueur qui connaît le jeu : il essaie chaque
     * combinaison possible (autant de reliques que d'emplacements ouverts) parmi celles qu'il
     * a trouvées, sur quelques combats d'essai (à son étage, avec son équipement, en jouant les
     * combos), et garde celle qui gagne le plus. Les mêmes dés pour toutes, pour que seules les
     * reliques les départagent. Au-delà de 8 reliques, il garde les meilleures seules et deux
     * partenaires de réaction, pour ne pas essayer des centaines de combinaisons.
     */
    private fun chooseRelics(hero: Hero, floor: Int, skill: Skill, wanted: Archetype? = null,
                             trialChain: Int = 3, series: Int = trialSeries) {
        // Le Sablier change le gameplay (la fenêtre de parade), pas la simulation : les bots ne le portent pas
        val owned = hero.relics.filter { it != Relic.HOURGLASS }
        val slots = hero.unlockedRelicSlots
        if (owned.size <= slots) {
            hero.relicSlots.fill(null)
            owned.forEachIndexed { i, r -> hero.relicSlots[i] = r }
            hero.hp = hero.hp.coerceAtMost(hero.maxHp)
            return
        }
        // Les victoires d'abord, et à égalité (l'étage 1 les donne toutes) le combat le plus court : sans ça, le bot
        // gardait trois sorts de soutien qui ne frappent jamais et perdait 50 PV contre chaque gobelin
        fun trial(relics: List<Relic>): Int {
            val rng = Random(floor * 977L)
            val stat = FloorStat()
            var wins = 0
            repeat(series) {
                val h = trialCopy(hero, relics)
                var ok = true
                repeat(trialChain) { if (ok) ok = soloFight(h, floor, skill, rng, stat, combos = true) }
                if (ok) wins++
            }
            return wins * TRIAL_WIN_WEIGHT - stat.turnsInFight
        }
        // Toutes les reliques sont essayées seules. On réserve ensuite deux places aux partenaires
        // des six meilleures : un sort moyen seul peut être indispensable au meilleur combo.
        // Un score par relique, calculé une fois : sortedByDescending { trial(...) } relançait les essais à chaque
        // comparaison du tri (~300 essais au lieu de ~31), et l'optimiseur y passait l'essentiel de son temps
        val solo = owned.associateWith { trial(listOf(it)) }
        val ranked = owned.sortedByDescending { solo.getValue(it) }
        val shortlist = if (owned.size <= 8) owned else {
            val own = if (wanted == null) emptyList() else ranked.filter { it.attribute == preferredStat(wanted) }.take(2)
            val core = (ranked.take(if (wanted == null) 6 else 4) + own).distinct()
            val partners = ranked.filter { candidate -> candidate !in core &&
                core.any { prepares(it, candidate) || prepares(candidate, it) } }
            (core + partners + ranked).distinct().take(8)
        }
        fun combinations(from: List<Relic>, k: Int): List<List<Relic>> =
            if (k == 0) listOf(emptyList())
            else from.indices.flatMap { i -> combinations(from.drop(i + 1), k - 1).map { listOf(from[i]) + it } }
        val best = combinations(shortlist, slots).maxBy { relics ->
            trial(relics) + if (wanted == null) 0 else
                relics.count { it.attribute == preferredStat(wanted) } * (TRIAL_WIN_WEIGHT / 4)
        }
        hero.relicSlots.fill(null)
        best.forEachIndexed { i, r -> hero.relicSlots[i] = r }
        hero.knownResonances += hero.resonances
        hero.hp = hero.hp.coerceAtMost(hero.maxHp)
    }

    /** Un héros d'essai : même équipement, les reliques [relics], PV pleins, recharges prêtes. */
    private fun trialCopy(src: Hero, relics: List<Relic>): Hero = Hero().apply {
        floor = src.floor
        deepestFloor = src.deepestFloor
        equipped.putAll(src.equipped)
        relics.forEach { addRelic(it) }
        healFull()
    }

    // ── L'optimiseur : l'équipement complet, essayé au combat ────────────────────

    /**
     * Les équipements qu'un joueur qui sait s'y prendre envisage, dans tout ce qu'il possède (porté + sac) : pour
     * chaque archétype, l'armure de son poids (avec, s'il en a les trois, uniquement des pièces de set de cet archétype),
     * la meilleure arme qui lui va, sa main gauche de classe si elle existe, et le meilleur bijou de chaque sorte.
     * Il faut au moins [Hero.ARCHETYPE_PIECES] pièces du poids pour compter comme cet archétype.
     */
    private fun candidateLoadouts(hero: Hero, avoided: Set<Archetype> = emptySet()): List<Loadout> {
        val pool = hero.equipped.values + hero.bag
        fun best(slot: EquipSlot, a: Archetype?, filter: (Equipment) -> Boolean = { true }) =
            pool.filter { it.slot == slot && filter(it) }.maxByOrNull { LootSystem.rating(it, a) }
        val out = mutableListOf<Loadout>()
        if (hero.archetype !in avoided) out += hero.equipped.toMap()
        for (a in Archetype.entries) {
            if (a in avoided) continue
            val variants = listOf<(Equipment) -> Boolean>({ true }, { it.isotopeSet?.archetype == a })
            for (armorFilter in variants) {
                val loadout = mutableMapOf<EquipSlot, Equipment>()
                for (slot in IsotopeSets.SLOTS) {
                    best(slot, a) { it.weight == a.weight && armorFilter(it) }?.let { loadout[slot] = it }
                }
                if (loadout.size < IsotopeSets.SLOTS.size && armorFilter !== variants[0]) continue
                if (loadout.size < Hero.ARCHETYPE_PIECES) continue
                // Un emplacement d'armure sans pièce du bon poids : la meilleure pièce, quel qu'en soit le poids
                for (slot in IsotopeSets.SLOTS) if (slot !in loadout) best(slot, a)?.let { loadout[slot] = it }
                best(EquipSlot.WEAPON, a)?.let { loadout[EquipSlot.WEAPON] = it }
                (best(EquipSlot.OFFHAND, a) { it.base == a.offhand } ?: best(EquipSlot.OFFHAND, a))?.let { loadout[EquipSlot.OFFHAND] = it }
                best(EquipSlot.AMULET, a)?.let { loadout[EquipSlot.AMULET] = it }
                best(EquipSlot.RING, a)?.let { loadout[EquipSlot.RING] = it }
                out += loadout
            }
        }
        return out.distinct()
    }

    /**
     * Essaie chaque équipement candidat sur quelques séries de 3 combats à l'étage courant (mêmes dés pour tous,
     * avec ses reliques et ses combos), garde celui qui gagne le plus (à égalité, la meilleure note), l'enfile, puis
     * refait le choix des reliques : un autre archétype change ce que valent les sorts.
     */
    private fun optimizeGear(hero: Hero, floor: Int, skill: Skill, r: Report, wanted: Archetype? = null, avoided: Set<Archetype> = emptySet()) {
        val candidates = candidateLoadouts(hero, avoided)
        r.stock.getOrPut((floor - 1) / 25 * 25 + 1) { StockStat() }.also { st ->
            val pool = hero.equipped.values + hero.bag
            st.decisions++
            st.pool += pool.size
            st.candidates += candidates.size - 1
            st.completeWeights += ArmorWeight.entries.count { w -> IsotopeSets.SLOTS.all { slot -> pool.any { it.slot == slot && it.weight == w } } }
            st.setPieces += pool.count { it.isotopeZ != null }
            st.setCandidates += candidates.count { c -> c.values.count { it.isotopeZ != null } == IsotopeSets.SLOTS.size }
        }
        if (candidates.size <= 1) return
        val relics = hero.relicSlots.filterNotNull()
        fun trial(loadout: Loadout): Int {
            val rng = Random(floor * 1237L)
            val stat = FloorStat()
            var wins = 0
            repeat(GEAR_SERIES) {
                val h = Hero().apply {
                    this.floor = hero.floor
                    deepestFloor = hero.deepestFloor
                    equipped.putAll(loadout)
                    relics.forEach { addRelic(it) }
                    healFull()
                }
                var ok = true
                repeat(3) { if (ok) ok = soloFight(h, floor, skill, rng, stat, combos = true) }
                if (ok) wins++
            }
            return wins * TRIAL_WIN_WEIGHT - stat.turnsInFight
        }
        fun rating(loadout: Loadout, a: Archetype?) = loadout.values.sumOf { LootSystem.rating(it, a) }
        fun preference(loadout: Loadout): Int {
            if (wanted == null) return 0
            val armor = IsotopeSets.SLOTS.mapNotNull { loadout[it] }
            val matching = armor.count { it.weight == wanted.weight }
            val fullSet = armor.size == IsotopeSets.SLOTS.size && armor.all { it.isotopeSet?.archetype == wanted }
            return (if (matching >= Hero.ARCHETYPE_PIECES) TRIAL_WIN_WEIGHT / 4 else 0) +
                (if (fullSet) TRIAL_WIN_WEIGHT / 2 else 0)
        }
        val current = hero.archetype
        val scores = candidates.associateWith { trial(it) }
        val best = candidates.maxWith(compareBy<Loadout>({ scores.getValue(it) + preference(it) }, { rating(it, current) }))
        // Il ne change que pour mieux : plus de victoires, ou à victoires égales des combats nettement plus courts (15 %), ou 2 victoires de plus sur 16.
        // Sans cette marge, chaque essai bruité en désignait un autre et il changeait d'archétype à chaque étage.
        val now = scores.getValue(candidates.first())
        val wins = { score: Int -> Math.floorDiv(score + TRIAL_WIN_WEIGHT / 2, TRIAL_WIN_WEIGHT) }
        val turns = { score: Int -> wins(score) * TRIAL_WIN_WEIGHT - score }
        val better = wins(scores.getValue(best)) >= wins(now) + 2 ||
            (wins(scores.getValue(best)) == wins(now) && turns(scores.getValue(best)) < turns(now) * 0.85)
        if ((!better && scores.getValue(best) + preference(best) <= now + preference(candidates.first())) || best == hero.equipped.toMap()) return
        for ((slot, item) in best) {
            if (hero.equipped[slot] == item) continue
            hero.bag.remove(item)
            hero.equip(item)
        }
        r.regears++
        if (hero.archetype != current) r.archetypeSwitches++
        chooseRelics(hero, floor, skill, wanted)
    }

    private fun geared(floor: Int, rng: Random): Hero {
        // Les bancs essaient des paires de reliques : tous les emplacements ouverts
        val hero = heroWithAllSlots()
        hero.floor = floor
        fun offer(e: Equipment) {
            val cur = hero.equipped[e.slot]
            if (cur == null || score(e) > score(cur)) hero.equipped[e.slot] = e
        }
        repeat(15) { offer(LootSystem.generate(floor, 0, rng)) }
        // Toujours une arme de l'étage : sans elle, le héros d'essai se battait à l'épée de bois
        // (15 tirages sans arme arrivent), et le banc mesurait des combats de 300 tours
        if (hero.equipped[EquipSlot.WEAPON]?.power == 1 && floor > 1) {
            var weapon = LootSystem.generate(floor, 0, rng)
            while (weapon.slot != EquipSlot.WEAPON) weapon = LootSystem.generate(floor, 0, rng)
            offer(weapon)
        }
        hero.healFull()
        return hero
    }

    private fun soloFight(hero: Hero, floor: Int, skill: Skill, rng: Random, fs: FloorStat = FloorStat(), combos: Boolean = false, useSpecial: Boolean = simUseSpecial): Boolean {
        val c = Combat(hero, floor, Encounters.build(Encounters.roll(floor, rng), floor), ambush = false, rng = rng)
        playCombat(c, skill, fs, rng, combos, useSpecial = useSpecial)
        return c.phase == CombatPhase.VICTORY
    }

    // ── Un profil de joueur : il rejoue après chaque mort, avec son équipement ──

    private fun playProfile(skill: Skill, r: Report, rng: Random, style: Style = Style.LEGACY, maxFloor: Int = this.maxFloor, label: String = "", wanted: Archetype? = null) {
        val smart = when (style) { Style.LEGACY -> this.smart; Style.CASUAL -> false; Style.OPTIMIZER -> true }
        val profileNo = r.profileNo++
        val trace = System.getenv("SIM_TRACE_PROFILE")?.toInt() == profileNo
        val g = RoguelikeGame(rng = rng, levelSeed = rng.nextLong(), checkpointEvery = checkpointEvery, deathRetreat = deathRetreat)
        var lastGearFloor = 0
        var newSetPiece = false
        /** L'étage de la dernière mort, tant que l'optimiseur n'a pas essayé d'y répondre : c'est là qu'il doit s'essayer, pas à l'étage 1. */
        var wallFloor = 0
        var stuckReported = false
        var visitFloor = 0; var visitTurns = 0
        var failZone = -1; var failStreak = 0
        val mem = MapMemory()
        val started = System.currentTimeMillis()
        val profileDeadline = if (timePerProfile) started + (maxSeconds?.times(1000) ?: Long.MAX_VALUE / 4) else deadline
        var lastProgress = started
        // Où le profil passe ses tours et où il meurt : de quoi comprendre un blocage
        val turnsByFloor = mutableMapOf<Int, Int>()
        val deathsByFloor = mutableMapOf<Int, Int>()
        var best = 1
        var deaths = 0
        var turns = 0
        var lastFloor = 0
        val reached = mutableSetOf<Int>()
        var relicsSeen = 0
        var zoneSeen = 0
        val collectedSetSlots = mutableMapOf<Archetype, MutableSet<EquipSlot>>()

        while (g.floor <= maxFloor && turns++ < maxMapTurns) {
            if (r.floors.values.any { it.stuck > 0 }) break
            if (System.currentTimeMillis() > profileDeadline) { r.wallClockCut++; break }
            if (label.isNotEmpty() && turns % 2000 == 0 && System.currentTimeMillis() - lastProgress > 30_000) {
                lastProgress = System.currentTimeMillis()
                progress("$label : ${(lastProgress - started) / 1000} s, étage ${g.floor} (meilleur $best), $deaths morts, $turns tours de carte")
            }
            turnsByFloor.merge(g.floor, 1, Int::plus)
            // Les tours d'une même visite : un bot qui revient cent fois sur un étage après des morts n'est pas coincé
            if (g.floor != visitFloor) { visitFloor = g.floor; visitTurns = 0 }
            visitTurns++
            // Plus de 20 000 tours de carte d'affilée sur un même étage, sans combat : on décrit l'état exact, une seule fois par profil
            if (!stuckReported && g.combat == null && visitTurns == STUCK_TURNS) {
                stuckReported = true
                if (label.isNotEmpty()) progress("$label : BLOQUÉ à l'étage ${g.floor} (plus de $STUCK_TURNS tours de carte sur cet étage)")
                val lvl = g.level
                r.timeoutInfo += "  BLOQUÉ profil n°$profileNo étage ${g.floor} : position ${g.playerPos}, PV ${g.hero.hp}/${g.hero.maxHp}, poursuivi ${g.isChased}, " +
                    "peut se reposer ${g.canRest()}, sur l'escalier ${g.onStairsTile()}, escalier ouvert ${g.stairsOpen}, cases à explorer ${frontier(g).size}, " +
                    "escaliers connus ${stairsKnown(g).size}, butin en attente ${g.pendingLoot.size}, monstres vus " +
                    lvl.packs.filter { it.alive && lvl.visible[it.pos.y][it.pos.x] }.joinToString(",") { "${it.types.first()}:${it.state}@${it.pos}" }
            }
            // Une relique trouvée, ou une nouvelle zone de 10 étages : le bot revoit ses deux reliques
            val zone = (g.floor - 1) / 10
            if (smart && g.combat == null && (g.hero.relics.size != relicsSeen || zone != zoneSeen)) {
                relicsSeen = g.hero.relics.size
                zoneSeen = zone
                val before = g.hero.relicSlots.filterNotNull().toSet()
                chooseRelics(g.hero, g.floor, skill, wanted)
                if (before.isNotEmpty()) { r.relicChecks++; if (g.hero.relicSlots.filterNotNull().toSet() != before) r.relicSwitches++ }
            }
            // L'optimiseur refait son équipement complet tous les 10 étages, dès qu'une pièce de set est tombée (au moins 3 étages après le dernier essai), et après chaque mort, à l'étage où il est mort
            if (style == Style.OPTIMIZER && g.combat == null && g.pendingEquipDrop == null && g.deathReport == null &&
                (wallFloor > 0 || g.floor - lastGearFloor >= 10 || (newSetPiece && g.floor - lastGearFloor >= 3))) {
                lastGearFloor = g.floor
                newSetPiece = false
                optimizeGear(g.hero, maxOf(g.floor, wallFloor), skill, r, wanted, avoidedArchetypes)
                wallFloor = 0
            }
            if (g.floor != lastFloor) {
                lastFloor = g.floor
                r.f(g.floor).entries++
                r.entriesByBand.merge(g.floor / 50, 1, Int::plus)
                if (reached.add(g.floor)) {
                    r.deathsBeforeFloor.getOrPut(g.floor) { mutableListOf() } += deaths
                    r.gearOnArrival.getOrPut(g.floor) { mutableListOf() } += g.hero.equipped.values.sumOf { LootSystem.rating(it) }
                    r.wealthOnArrival.getOrPut(g.floor) { mutableListOf() } += g.hero.gold + g.hero.bag.sumOf { LootSystem.sellPrice(it).toLong() }
                }
                best = maxOf(best, g.floor)
            }
            // L'or gagné ou perdu pendant ce pas, compté à l'étage où il a eu lieu
            val goldBefore = g.hero.gold
            val goldFloor = g.floor
            when {
                g.combat != null -> {
                    val cc = g.combat!!
                    val floorFought = g.floor
                    val heroBefore = "PV ${cc.hero.hp}/${cc.hero.maxHp}"
                    if (trace && deaths >= DEATH_LOOP - 3 && deaths < DEATH_LOOP + 3) println("TRACE étage $floorFought $heroBefore contre " + cc.enemies.joinToString(", ") { "${it.type} ${it.hp}PV" })
                    if (fight(g, skill, r, rng, smart)) {
                    deaths++
                    wallFloor = floorFought
                    deathsByFloor.merge(floorFought, 1, Int::plus)
                    // Le casual ne cherche pas à optimiser, mais après des échecs répétés dans la même zone il essaie
                    // d'autres reliques, au hasard parmi celles qu'il a trouvées (il finit par toutes les avoir essayées)
                    if (style == Style.CASUAL) {
                        val zoneFailed = floorFought / 10
                        if (zoneFailed == failZone) failStreak++ else { failZone = zoneFailed; failStreak = 1 }
                        if (failStreak >= CASUAL_FAILS) {
                            failStreak = 0
                            r.relicReshuffles++
                            val pick = g.hero.relics.filter { it != Relic.HOURGLASS }.shuffled(rng).take(g.hero.unlockedRelicSlots)
                            g.hero.relicSlots.fill(null)
                            pick.forEachIndexed { i, relic -> g.hero.relicSlots[i] = relic }
                        }
                    }
                    if (deaths in DEATH_LOOP + 1..DEATH_LOOP + 3) r.deathLoops += "  mort n°$deaths à l'étage $floorFought ($heroBefore, embuscade ${cc.ambush}) contre " +
                        cc.enemies.joinToString(", ") { "${it.type} ${it.hp} PV ${it.damage} dégâts" }
                    if (deaths == DEATH_LOOP) r.deathLoops += "profil n°$profileNo, étage ${g.floor}, ${g.hero.archetype}, PV ${g.hero.maxHp}, arme ${g.hero.equipped[EquipSlot.WEAPON]?.base} " +
                        "P${g.hero.equipped[EquipSlot.WEAPON]?.power}, armure " + IsotopeSets.SLOTS.joinToString("/") { slot -> g.hero.equipped[slot]?.let { "${it.weight}:P${it.power}" } ?: "rien" } +
                        ", dégâts ${g.hero.weaponMin}-${g.hero.weaponMax}, vitesse ${g.hero.speed}, CA ${g.hero.armor}, PV ${g.hero.hp}/${g.hero.maxHp}" +
                        ", main gauche ${g.hero.equipped[EquipSlot.OFFHAND]?.base}, reliques ${g.hero.relicSlots.filterNotNull().joinToString("+")}"
                    }
                }
                g.deathReport != null -> g.dismissDeath()
                g.pendingEquipDrop != null -> {
                    val e = g.pendingEquipDrop!!
                    r.f(g.floor).drops++
                    r.f(g.floor).dropValue += LootSystem.sellPrice(e)
                    if (e.isotopeZ != null) newSetPiece = true
                    e.isotopeSet?.archetype?.let { a ->
                        r.setDrops.merge(a, 1, Int::plus)
                        val slots = collectedSetSlots.getOrPut(a) { mutableSetOf() }
                        slots += e.slot
                        if (slots.containsAll(IsotopeSets.SLOTS)) r.firstCompleteSetFloor.putIfAbsent(a, g.floor)
                    }
                    val cur = g.hero.equipped[e.slot]
                    // Une consigne d'évitement doit aussi s'appliquer au ramassage immédiat :
                    // sinon deux pièces tombées entre deux essais d'équipement peuvent former
                    // brièvement l'archétype écarté.
                    fun createsAvoidedArchetype(item: Equipment): Boolean {
                        if (item.slot !in IsotopeSets.SLOTS) return false
                        return avoidedArchetypes.any { avoided ->
                            IsotopeSets.SLOTS.count { slot ->
                                (if (slot == item.slot) item else g.hero.equipped[slot])?.weight == avoided.weight
                            } >= Hero.ARCHETYPE_PIECES
                        }
                    }
                    // Le bot connaît sa classe : une arme qui n'est pas la sienne note moins bien
                    val archetype = wanted ?: g.hero.archetype
                    // Une pièce étrangère peut dépanner ; une pièce du set visé reçoit un léger bonus.
                    fun dropScore(item: Equipment): Int {
                        val rating = LootSystem.rating(item, archetype)
                        if (wanted == null) return rating
                        return rating +
                            (if (item.weight == wanted.weight) rating / 5 else 0) +
                            (if (item.isotopeSet?.archetype == wanted) rating / 4 else 0)
                    }
                    if (!createsAvoidedArchetype(e) && (cur == null || dropScore(e) > dropScore(cur))) g.equipPendingDrop()
                    else g.stashPendingDrop()
                }
                g.stairsOpen -> g.descend()
                // Les bots ne se servent pas de la forge : ils la referment
                g.forgeOpen -> g.closeForge()
                else -> { mapStep(g, mem); r.f(g.floor).mapTurns++ }
            }
            val goldDelta = g.hero.gold - goldBefore
            if (goldDelta > 0) r.f(goldFloor).goldEarned += goldDelta else r.f(goldFloor).goldLost -= goldDelta
        }
        if (turns >= maxMapTurns) {
            r.timeouts++
            val top = turnsByFloor.entries.sortedByDescending { it.value }.take(3).joinToString(", ") { "étage ${it.key} : ${it.value} tours de carte, ${deathsByFloor[it.key] ?: 0} morts" }
            r.timeoutInfo += "profil n°$profileNo, budget épuisé à l'étage ${g.floor} (meilleur $best, $deaths morts, ${g.hero.archetype}, PV ${g.hero.hp}/${g.hero.maxHp}) ; là où il a passé son temps : $top"
        }
        r.mapResets += mem.resets
        r.timeoutInfo += mem.resetEvents
        r.bestFloors += best
        r.archetypesAtEnd += (g.hero.archetype?.name ?: "aucun") + if (g.hero.setArchetype != null) "+set" else ""
        if (g.hero.setArchetype != null) r.setBonusAtEnd++
        r.setPiecesAtEnd += g.hero.equipped.values.count { it.isotopeZ != null }
        r.finalRelics += g.hero.relicSlots.filterNotNull().sortedBy { it.ordinal }.joinToString(" + ") { it.name }
        r.discoveredRelics += g.hero.relics.sortedBy { it.ordinal }.joinToString("+") { it.name }
    }

    /** Joue un combat entier. Renvoie true si le héros est mort. */
    private fun fight(g: RoguelikeGame, skill: Skill, r: Report, rng: Random, smart: Boolean = this.smart): Boolean {
        val c = g.combat!!
        val fs = r.f(g.floor)
        fs.fights++
        fs.groupSizes += c.enemies.size
        if (c.ambush) fs.ambushes++
        val hpStart = c.hero.hp
        val maxHpStart = c.hero.maxHp
        val turnsStart = fs.turnsInFight
        val armorCount = IsotopeSets.SLOTS.count { c.hero.equipped[it] != null }
        val weapon = c.hero.equipped[EquipSlot.WEAPON]
        val ambush = c.ambush
        val chained = r.nextFightChained
        r.fightsTotal++
        val setBefore = c.hero.setArchetype != null
        val worn = c.hero.relicSlots.filterNotNull()
        val archName = (c.hero.archetype?.name ?: "aucun") + if (setBefore) "+set" else ""
        if (setBefore) r.fightsWithSet++
        val cs = CombatStat()
        val floorFought = g.floor
        playCombat(c, skill, fs, rng, combos = smart, cs = cs, useSpecial = simUseSpecial)
        val died = c.phase == CombatPhase.DEFEAT
        r.combatRows += listOf(floorFought, archName, c.enemies.size, c.enemies.count { it.isBoss },
            c.phase.name, fs.turnsInFight - turnsStart, hpStart, maxHpStart, ambush, chained,
            armorCount, weapon?.base?.name ?: "NONE", weapon?.power ?: 0, worn.size,
            worn.joinToString("+") { it.name }, c.enemies.joinToString("+") { it.type.name },
            c.enemies.sumOf { it.hp }, c.enemies.sumOf { it.maxHp }, c.hero.lifeSteal, c.lifeStolen).joinToString(",")
        if (died) {
            if (hpStart >= c.hero.maxHp * 0.9f) r.deathsFullHp++
            if (ambush) r.deathsAmbush++
            if (chained) r.deathsChained++
            r.deathsByGroup[c.enemies.size]++
        }
        val lost = (hpStart - if (died) 0 else c.hero.hp).coerceAtLeast(0).toDouble() / c.hero.maxHp
        val zone = floorFought / 250
        fun tally(key: String) = r.agg.getOrPut("$key|z$zone") { Agg() }.also { it.fights++; if (died) it.deaths++; it.hpLost += lost }
        tally("combos|" + if (cs.combos > 0) "oui" else "non")
        tally("special|" + if (cs.specials > 0) "oui" else "non")
        tally("set|" + if (setBefore) "oui" else "non")
        if (cs.specials > 0) tally("specialAmélioré|" + if (cs.boostedSpecials > 0) "oui" else "non")
        cs.specialBy.forEach { (k, n) -> r.specialUses.merge(k, n, Int::plus) }
        r.comboSteps += cs.combos; r.relicCasts += cs.casts
        for (relic in worn) r.relicAgg.getOrPut(relic.name) { Agg() }.also { it.fights++; if (died) it.deaths++; it.hpLost += lost }
        cs.castBy.forEach { (relic, n) -> r.relicCastsBy.merge(relic.name, n, Int::plus) }
        r.archAgg.getOrPut(archName) { Agg() }.also { it.fights++; if (died) it.deaths++; it.hpLost += lost }
        r.fightsByBand.merge(floorFought / 50, 1, Int::plus)
        if (died) r.deathsByBand.merge(floorFought / 50, 1, Int::plus)
        fs.dmgPct += lost
        if (died) fs.deaths++
        val floorBefore = g.floor
        if (c.phase != CombatPhase.VICTORY && c.phase != CombatPhase.DEFEAT) return false
        g.finishCombat()
        r.nextFightChained = !died && g.combat != null
        if (r.nextFightChained) r.f(floorBefore).chains++
        return died
    }

    private fun playCombat(c: Combat, skill: Skill, fs: FloorStat, rng: Random, combos: Boolean = false, cs: CombatStat? = null, useSpecial: Boolean = true,
                           maxTurns: Int = MAX_FIGHT_TURNS) {
        var turns = 0
        // Un sort qui ne frappe pas (bouclier, charme…) n'est jamais lancé deux tours de
        // suite : avec une SAG haute, sa recharge tombe à 1 et le bot ne frappait plus jamais
        var lastWasSupport = false
        while (c.phase == CombatPhase.PLAYER_TURN || c.phase == CombatPhase.ENEMY_TURN) {
            if (turns >= maxTurns) { fs.stuck++; break }
            if (c.phase == CombatPhase.PLAYER_TURN) {
                turns++
                val alive = c.aliveIndices()
                // Cible : le plus proche d'attaquer, puis le plus faible
                val target = alive.minWith(compareBy<Int>({ c.roundsUntilTurn(it) }, { c.enemies[it].hp }))
                val incoming = alive.filter { c.roundsUntilTurn(it) <= 1 }.sumOf { c.enemies[it].damage }
                // Glace et foudre visent celui qui va frapper, comme la fracture, la marque, le charme
                // et l'aveuglement
                // (elles préparent ses propres coups) ; feu et poison, le plus solide.
                // Le bot connaît les affinités (un joueur les apprend en mourant) : il ne lance
                // jamais un sort sur un monstre qui y est immunisé ou déjà enragé.
                fun aimAt(r: Relic) =
                    if (r.element == Element.ICE || r.element == Element.LIGHTNING ||
                        r.effect in setOf(RelicEffect.FRACTURE, RelicEffect.MARK, RelicEffect.CHARM, RelicEffect.BLIND, RelicEffect.SLOW)) target
                    else alive.maxBy { c.enemies[it].hp }
                val ready = c.hero.relicSlots.filterNotNull().firstOrNull {
                    val e = c.enemies[aimAt(it)]
                    c.canCast(it) && (simRelics == null || it.name in simRelics) && !(lastWasSupport && !it.hits) &&
                        // On ne réenduit pas une arme encore enduite : ce serait un tour perdu
                        !(it.effect == RelicEffect.ENCHANT_POISON && c.poisonedBlades > 0) &&
                        e.type.affinity(it.element) != Affinity.IMMUNE &&
                        !(e.enraged && (it.element == Element.ICE || it.element == Element.LIGHTNING || it.effect == RelicEffect.SLOW))
                }
                // Le « Spécial » : le voleur achève une cible exposée, le guerrier se met en garde
                // devant un gros coup, le mage lève ses doubles dès qu'il n'en a plus
                val exposed = alive.filter { c.isExposed(c.enemies[it]) }.minByOrNull { c.enemies[it].hp }
                val special = if (!useSpecial || !c.canUseSpecial()) null else when (c.hero.archetype) {
                    Archetype.ROGUE   -> exposed?.let { { c.deadlyStrike(it, strike(skill, rng, c.hero)); Unit } }
                    // Garde et doubles ne frappent pas : jamais juste après un autre tour sans frapper
                    Archetype.WARRIOR -> if (incoming > c.hero.maxHp * 0.15f && !lastWasSupport) ({ c.guard(); lastWasSupport = true }) else null
                    Archetype.MAGE    -> if (c.mirrorImages == 0 && !lastWasSupport) ({ c.mirrorImage(); lastWasSupport = true }) else null
                    // Deux coups d'arme : il enchaîne dès qu'il peut. Le nécromancien invoque ses pantins, puis rappelle les tombés.
                    Archetype.BARBARIAN -> ({ c.smash(target, strike(skill, rng, c.hero)); Unit })
                    Archetype.VAGABOND -> ({ c.chain(target, strike(skill, rng, c.hero), strike(skill, rng, c.hero)); Unit })
                    Archetype.NECROMANCER -> if ((c.puppetHp.isEmpty() || c.puppetHp.any { it <= 0 }) && !lastWasSupport) ({ c.recallPuppets(); lastWasSupport = true }) else null
                    null -> null
                }
                lastWasSupport = false
                val combo = if (combos) comboChoice(c, target) else null
                val enemyHpBefore = c.enemies.sumOf { it.hp.toLong() }
                val kind = when { special != null -> "Spécial"; combo?.first != null || (combo == null && ready != null) -> "relique"; else -> "attaque" }
                when {
                    special != null -> {
                        cs?.let {
                            val a = c.hero.archetype
                            val boosted = a != null && c.hero.specialBoosted(a)
                            it.specials++
                            if (boosted) it.boostedSpecials++
                            it.specialBy.merge("$a|${if (boosted) "amélioré" else "normal"}", 1, Int::plus)
                        }
                        special()
                    }
                    combo != null -> {
                        cs?.let { it.combos++ }
                        val r = combo.first
                        if (r == null) c.attack(combo.second, strike(skill, rng, c.hero))
                        else { cs?.cast(r); c.castRelic(r, combo.second, strike(skill, rng, c.hero)); lastWasSupport = !r.hits }
                    }
                    ready != null -> { cs?.let { it.casts++; it.cast(ready) }; c.castRelic(ready, aimAt(ready), strike(skill, rng, c.hero)); lastWasSupport = !ready.hits }
                    else -> c.attack(target, strike(skill, rng, c.hero))
                }
                cs?.let {
                    it.actionsBy.merge(kind, 1, Int::plus)
                    it.damageBy.merge(kind, enemyHpBefore - c.enemies.sumOf { e -> e.hp.toLong() }, Long::plus)
                }
            } else {
                val enemyHpBefore = c.enemies.sumOf { it.hp.toLong() }
                val (_, attackers) = c.startEnemyTurn()
                for (a in attackers) {
                    if (c.phase != CombatPhase.ENEMY_TURN) break
                    val s = c.resolveStrike(a, parry(skill, rng, c.hero, guarding = c.guarding, slowed = c.hourglassStrikes > 0))
                    cs?.let {
                        if (s.charmed || s.bledOut) return@let
                        it.enemyAttacks++
                        when {
                            s.imageHit -> it.imageHits++
                            s.blocked || s.dodged -> it.perks++
                            s.missed -> it.dodged++
                            else -> it.landed++
                        }
                        it.damageTaken += s.damage
                        it.puppetAbsorbed += s.puppetAbsorbed
                        it.barrierAbsorbed += s.absorbed
                        it.thorns += s.thorns
                        it.counters += s.counter?.damage ?: 0
                    }
                }
                c.endEnemyTurn()
                cs?.let { it.enemyTurnDamage += enemyHpBefore - c.enemies.sumOf { e -> e.hp.toLong() } }
            }
        }
        fs.turnsInFight += turns
    }

    /** Erreurs temporelles calibrées sur les anciennes fenêtres ; les nouvelles règles jugent le geste. */
    private fun gestureError(perfect: Float, good: Float, oldPerfectMs: Float, oldGoodMs: Float,
                             oldHalfDuration: Float, rng: Random): Float {
        val roll = rng.nextFloat()
        val (low, high) = when {
            roll < perfect -> 0f to oldPerfectMs
            roll < perfect + good -> oldPerfectMs to oldGoodMs
            else -> oldGoodMs to oldHalfDuration
        }
        return low + rng.nextFloat() * (high - low)
    }

    private fun strike(s: Skill, rng: Random, hero: Hero): Timing {
        val error = gestureError(s.strikePerfect, s.strikeGood, 1350f * .045f,
            1350f * .13f, 675f, rng)
        val duration = CombatTiming.strikeMs(hero)
        return when {
            error <= duration * CombatTiming.strikePerfect(hero) -> Timing.PERFECT
            error <= duration * CombatTiming.strikeGood(hero) -> Timing.GOOD
            else -> Timing.MISS
        }
    }

    private fun parry(s: Skill, rng: Random, hero: Hero, guarding: Boolean = false, slowed: Boolean = false): Timing {
        // Même règle temporelle que CombatView : garde et Sablier multiplient les fenêtres.
        val scale = (if (guarding) 2f else 1f) * (if (slowed) Relic.HOURGLASS_SLOW else 1f)
        // La préparation plus courte laisse moins de temps au joueur simulé pour réagir.
        val error = gestureError(s.parryPerfect, s.parryGood, 70f, 160f, 475f, rng) *
            950f / CombatTiming.WINDUP_MS
        return when {
            error <= CombatTiming.parryPerfect(hero) * scale -> Timing.PERFECT
            error <= CombatTiming.parryGood(hero) * scale -> Timing.GOOD
            else -> Timing.MISS
        }
    }

    // ── Carte : ce qu'un joueur raisonnable ferait avec ce qu'il voit ───────────

    /** Ce que le bot retient de ses derniers pas sur la carte : de quoi voir qu'il piétine. */
    /** Une ligne de suivi, écrite tout de suite (build/roguelike-progress.txt) : les longues parties ne rendent leur rapport qu'à la fin. */
    private fun progress(line: String) {
        synchronized(RoguelikeSimulationTest::class.java) { File("build/roguelike-progress.txt").appendText("$line\n") }
    }

    private class MapMemory(val stalledLimit: Int = 2000) {
        var seed: Long? = null
        var explored = -1
        var defeated = -1
        var stalled = 0
        var returningToCamp = false
        var campWaitSeconds = 0
        var resets = 0
        val resetEvents = mutableListOf<String>()
        val route = ArrayDeque<Pos>()
        val recent = ArrayDeque<Pos>()
        /** Pas restants pendant lesquels il ne contourne plus les monstres (il va au combat). */
        var forceFight = 0
    }

    private fun mapStep(g: RoguelikeGame, mem: MapMemory = MapMemory()) {
        val lv = g.level
        val hero = g.hero
        val visiblePacks = lv.packs.filter { it.alive && lv.visible[it.pos.y][it.pos.x] }
        // Observe progress before any chase/healing early return.
        val explored = lv.explored.sumOf { row -> row.count { it } }
        val defeated = lv.packs.count { !it.alive }
        if (mem.seed != g.levelSeed) {
            mem.seed = g.levelSeed
            mem.explored = -1
            mem.defeated = -1
            mem.stalled = 0
            mem.returningToCamp = false
            mem.campWaitSeconds = 0
            mem.recent.clear()
            mem.route.clear()
            mem.forceFight = 0
        }
        mem.route.addLast(g.playerPos)
        if (mem.route.size > 32) mem.route.removeFirst()
        if (explored != mem.explored || defeated != mem.defeated) mem.stalled = 0
        else mem.stalled++
        mem.explored = explored
        mem.defeated = defeated
        if (!mem.returningToCamp && mem.stalled >= mem.stalledLimit) {
            mem.returningToCamp = true
            mem.resetEvents += "Retour au feu : étage ${g.floor}, graine ${g.levelSeed}, position ${g.playerPos}, PV ${hero.hp}/${hero.maxHp}, poursuivi ${g.isChased}, frontière ${frontier(g).size}, escaliers connus ${stairsKnown(g)}, dernières positions ${mem.route.joinToString()}"
        }
        if (mem.returningToCamp) {
            if (g.onCampTile()) {
                // The UI delay is wall time, not five movement turns. Advance a virtual
                // second per bot decision, without moving monsters or re-running fights.
                if (++mem.campWaitSeconds >= 5) {
                    val oldSeed = g.levelSeed
                    g.regenerateCurrentFloor()
                    if (g.levelSeed != oldSeed) {
                        mem.resets++
                        mem.resetEvents += "Étage ${g.floor} régénéré au feu après 5 secondes simulées : $oldSeed -> ${g.levelSeed}"
                    }
                }
                return
            }
            mem.campWaitSeconds = 0
            // Use the real path and fight blockers; never teleport to the fire.
            if (moveToward(g, listOf(lv.start), avoidPacks = false)) return
        }

        // Poursuivi : on prend l'initiative plutôt que de se faire surprendre
        if (g.isChased) {
            val chaser = visiblePacks.filter { it.state == PackState.CHASING }.minByOrNull { it.pos.chebyshev(g.playerPos) }
            if (chaser != null && moveToward(g, listOf(chaser.pos), avoidPacks = false)) return
        }
        // Return to the starting fire for recovery between fights.
        if (!g.isChased && hero.hp < hero.maxHp * 0.9f && !g.onCampTile() &&
            moveToward(g, listOf(lv.start), avoidPacks = true)) return
        if (g.canRest() && hero.hp < hero.maxHp * 0.9f) {
            val packsBefore = lv.packs.size
            g.rest()
            if (g.level === lv && lv.packs.size > packsBefore) restNoisesCounter++
            return
        }

        // Piétiner : un monstre inactif n'est visible que de certaines cases, donc le chemin qui l'évite change à chaque
        // pas et le bot fait des allers-retours pour toujours (9 profils sur 20 y ont brûlé leur budget). Un joueur irait le battre.
        mem.recent.addLast(g.playerPos)
        if (mem.recent.size > STUCK_WINDOW) mem.recent.removeFirst()
        if (mem.forceFight > 0) mem.forceFight--
        else if (mem.recent.size == STUCK_WINDOW && mem.recent.toSet().size <= 3) mem.forceFight = 40

        // Explorer ce qui ne l'est pas, puis l'escalier
        val front = frontier(g)
        if (g.onStairsTile() && front.isEmpty()) { g.openStairs(); return }
        val goals = front.ifEmpty { stairsKnown(g) }
        if (goals.isEmpty()) { g.openStairs(); return }
        val moved = if (mem.forceFight > 0) moveToward(g, goals, avoidPacks = false) else moveToward(g, goals, avoidPacks = true) || moveToward(g, goals, avoidPacks = false)
        if (!moved) {
            // Rien d'atteignable : on file à l'escalier s'il est connu
            val stairs = stairsKnown(g)
            if (g.onStairsTile()) g.openStairs() else if (stairs.isNotEmpty()) moveToward(g, stairs, avoidPacks = false)
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

    private fun format(r: Report, style: Style = Style.LEGACY, maxFloor: Int = this.maxFloor, profiles: Int = this.profiles): String = buildString {
        val smart = when (style) { Style.LEGACY -> this@RoguelikeSimulationTest.smart; Style.CASUAL -> false; Style.OPTIMIZER -> true }
        val cp = if (checkpointEvery > 0) ", checkpoint tous les $checkpointEvery étages" else ""
        val who = if (style == Style.LEGACY) "${r.skill}" else "$style ${r.skill}"
        appendLine("══════ $who ($profiles profils, max étage $maxFloor, $maxMapTurns tours de carte$cp) ══════")
        val b = r.bestFloors.sorted()
        appendLine("Meilleur étage : médiane ${b[b.size / 2]}, min ${b.first()}, max ${b.last()}   blocages : ${r.timeouts}")
        // Combien de morts pour aller au bout, chez ceux qui y sont arrivés
        r.deathsBeforeFloor[maxFloor]?.sorted()?.let { d ->
            appendLine("Arrivés à l'étage $maxFloor : ${d.size} profils sur $profiles ; morts pour y arriver : médiane ${d[d.size / 2]}, min ${d.first()}, max ${d.last()}")
        } ?: appendLine("Arrivés à l'étage $maxFloor : aucun")
        val totalDeaths = r.floors.values.sumOf { it.deaths }.coerceAtLeast(1)
        appendLine("Morts : $totalDeaths dont ${100 * r.deathsFullHp / totalDeaths} % en commençant le combat à plus de 90 % des PV, " +
            "${100 * r.deathsAmbush / totalDeaths} % en embuscade, ${100 * r.deathsChained / totalDeaths} % en combat enchaîné ; " +
            "par taille de groupe : 1 → ${100 * r.deathsByGroup[1] / totalDeaths} %, 2 → ${100 * r.deathsByGroup[2] / totalDeaths} %, 3 → ${100 * r.deathsByGroup[3] / totalDeaths} %")
        appendLine("Monstres attirés par le repos : ${r.restNoises}")
        if (r.stock.isNotEmpty()) {
            appendLine("Ce que l'optimiseur avait à choisir (moyenne par décision, par tranche d'étages) : objets · équipements candidats · poids d'armure complets (sur ${ArmorWeight.entries.size}) · pièces de set · équipements 100 % set")
            for ((band, st) in r.stock) {
                val n = st.decisions.toDouble()
                appendLine(String.format("  %3d–%3d | %3d décisions | %5.1f | %4.1f | %3.1f | %3.1f | %3.1f", band, band + 24, st.decisions,
                    st.pool / n, st.candidates / n, st.completeWeights / n, st.setPieces / n, st.setCandidates / n))
            }
        }
        r.timeoutInfo.forEach { appendLine("Budget épuisé : $it") }
        r.deathLoops.forEach { appendLine("Profil qui meurt en boucle (${DEATH_LOOP} morts) : $it") }
        if (smart) appendLine("Reliques portées en fin de partie : " +
            r.finalRelics.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6).joinToString("  ") { "${it.key} ×${it.value}" })
        appendLine("Arrivée à l'étage : profils arrivés | morts cumulées (médiane) | note totale de l'équipement porté (médiane)")
        for ((fl, l) in r.deathsBeforeFloor.toSortedMap()) {
            if (fl % 5 != 0 && fl != 1) continue
            val d = l.sorted(); val gear = r.gearOnArrival.getValue(fl).sorted()
            appendLine(String.format("  %2d | %2d | %4d | %5d", fl, d.size, d[d.size / 2], gear[gear.size / 2]))
        }
        appendLine("Ét. | passages | tours de carte/passage | combats/passage | combats | ennemis/combat | embuscades | enchaînés | dégâts/combat (% PV max) | tours/combat | morts")
        for ((fl, f) in r.floors) {
            val n = f.fights.coerceAtLeast(1).toDouble()
            appendLine(String.format("%3d | %5d | %5.0f | %4.1f | %6d | %4.2f | %4.0f%% | %4.0f%% | %5.1f%% | %4.1f | %d",
                fl, f.entries, f.mapTurns / f.entries.coerceAtLeast(1).toDouble(), f.fights / f.entries.coerceAtLeast(1).toDouble(), f.fights, f.groupSizes / n, 100 * f.ambushes / n, 100 * f.chains / n,
                100 * f.dmgPct / n, f.turnsInFight / n, f.deaths))
        }
        appendLine()
    }
    @Test fun stalledBotWalksToCampAndRegeneratesAfterVirtualWait() {
        val g = RoguelikeGame(rng = Random(7), levelSeed = 7L)
        g.level.packs.forEach { it.alive = false }
        val start = g.playerPos
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            g.tryMove(dx, dy)
            if (g.playerPos != start) break
        }
        org.junit.Assert.assertTrue(g.playerPos != start)
        val seed = g.levelSeed
        val mem = MapMemory(stalledLimit = 1).apply {
            this.seed = seed
            explored = g.level.explored.sumOf { row -> row.count { it } }
            defeated = g.level.packs.count { !it.alive }
        }
        mapStep(g, mem)
        org.junit.Assert.assertTrue(g.onCampTile())
        org.junit.Assert.assertEquals(seed, g.levelSeed)
        repeat(4) { mapStep(g, mem) }
        org.junit.Assert.assertEquals(seed, g.levelSeed)
        mapStep(g, mem)
        org.junit.Assert.assertNotEquals(seed, g.levelSeed)
        org.junit.Assert.assertEquals(1, g.floor)
        org.junit.Assert.assertEquals(1, mem.resets)
        org.junit.Assert.assertTrue(mem.resetEvents.size >= 2)
        mapStep(g, mem)
        org.junit.Assert.assertFalse(mem.returningToCamp)
    }
}
