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

    /** Ce qui s'est passé dans un combat : sorts, combos, Spécial (et Spécial amélioré par un set d'isotope). */
    class CombatStat {
        var casts = 0; var combos = 0; var specials = 0; var boostedSpecials = 0
        val specialBy = mutableMapOf<String, Int>()
        val castBy = mutableMapOf<Relic, Int>()
        fun cast(r: Relic) { castBy.merge(r, 1, Int::plus) }
    }

    /** Un groupe de combats : combien, combien de morts, PV perdus (somme des parts de PV max). */
    class Agg { var fights = 0; var deaths = 0; var hpLost = 0.0 }

    class FloorStat {
        var entries = 0; var deaths = 0; var fights = 0; var chains = 0; var ambushes = 0
        var dmgPct = 0.0; var turnsInFight = 0
        var groupSizes = 0; var mapTurns = 0
        /** Combats arrêtés à [MAX_FIGHT_TURNS] tours : un combat qui ne finit pas est un bug. */
        var stuck = 0
    }

    companion object {
        /** Un vrai combat dure une dizaine de tours ; au-delà de ça, quelque chose tourne en rond. */
        const val MAX_FIGHT_TURNS = 300
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
        var timeouts = 0
        /** D'où viennent les morts : PV au début du combat fatal, embuscade, enchaînement, taille du groupe. */
        var deathsFullHp = 0; var deathsAmbush = 0; var deathsChained = 0
        val deathsByGroup = IntArray(4)
        var nextFightChained = false
        /** Ce que le bot porte en fin de partie, et la part de ses combats joués avec le bonus d'un set. */
        val archetypesAtEnd = mutableListOf<String>()
        var setBonusAtEnd = 0
        var setPiecesAtEnd = 0
        var fightsTotal = 0; var fightsWithSet = 0
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
        /** Par archétype (« aucun » sans majorité de poids), avec « +set » quand le bonus d'isotope est actif. */
        val archAgg = sortedMapOf<String, Agg>()
        /** Le casual qui change de reliques au hasard après des échecs répétés. */
        var relicReshuffles = 0
        /** L'optimiseur qui revoit ses reliques : combien de fois il les a réexaminées, et combien de fois il en a changé. */
        var relicChecks = 0
        var relicSwitches = 0
        /** Les reliques portées à la fin de chaque profil, pour voir ce que le bot a choisi. */
        val finalRelics = mutableListOf<String>()
        fun f(n: Int) = floors.getOrPut(n) { FloorStat() }
    }

    @Test
    fun simulate() {
        // SIM_SETS=0 : les sets d'isotope ne tombent pas (la mesure « d'avant les sets »)
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
     * Deux façons de jouer, avec et sans sets d'isotope : le joueur qui équipe le meilleur score sans rien regarder
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
     * Six profils comparables : libre, puis un par archétype. Tous testent les reliques
     * disponibles et leurs combos. Les cinq spécialisés préfèrent leur set et leurs reliques,
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
        for (wanted in wantedList) for (style in styles) for (skill in skills) {
            val group = results.filter { it.first.wanted == wanted && it.first.style == style && it.first.skill == skill }.map { it.second }
            val fights = group.sumOf { it.fightsTotal }.coerceAtLeast(1)
            val a = group.flatMap { it.archAgg.entries }
            val deaths = a.sumOf { it.value.deaths }
            val lost = a.sumOf { it.value.hpLost }
            val active = a.filter { wanted != null && it.key.startsWith(wanted.name) }.sumOf { it.value.fights }
            val set = a.filter { wanted != null && it.key == "${wanted.name}+set" }.sumOf { it.value.fights }
            val ownRelics = group.flatMap { it.relicAgg.entries }.filter { entry ->
                wanted != null && Relic.valueOf(entry.key).attribute == preferredStat(wanted) && entry.key != Relic.HEAL.name
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


    // ── Les sets d'isotope, à équipement égal ───────────────────────────────────

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
        val armor: Map<EquipSlot, Equipment> = when (gear) {
            SetGear.CLASSIC -> emptyMap()
            SetGear.SAME_ARCHETYPE -> IsotopeSets.SLOTS.associateWith { classicPiece(floor, it, set.archetype.weight, rng) }
            SetGear.SET_NO_BONUS, SetGear.SET -> IsotopeSets.BASES.associate { base ->
                val best = (1..3).map { LootSystem.createSetPiece(set, base, 0, rng) }.maxBy { score(it) }
                best.slot to if (gear == SetGear.SET) best else best.copy(isotopeZ = null)
            }
        }
        hero.equipped.putAll(armor)
        hero.healFull()
        return hero
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
            val out = StringBuilder("══════ Sets d'isotope : 0 à 3 pièces ($series séries de $chain combats, joueur $skill) ══════\n")
            out.appendLine("victoires · PV perdus par combat, le reste de l'armure étant du butin du même poids\n")
            for (set in simSets()) {
                out.appendLine("── ${set.symbol()}-${set.mass} (${set.archetype}) ──")
                out.appendLine(String.format("%5s", "Étage") + (0..3).joinToString("") { String.format("%20s", "$it pièce(s)") })
                val rows = setFloors(set).parallelStream().map { floor ->
                    val cells = (0..3).map { pieces ->
                        val stuck = FloorStat()
                        var wins = 0; var lost = 0.0; var fights = 0
                        repeat(series) { i ->
                            val hero = geared(floor, Random(floor * 7919L + i))
                            val rng = Random(floor * 104729L + i)
                            val slots = IsotopeSets.SLOTS
                            val chosen = (0 until pieces).map { slots[(it + i) % slots.size] }.toSet()
                            for (slot in slots) {
                                hero.equipped[slot] = if (slot in chosen) {
                                    val base = IsotopeSets.BASES[slots.indexOf(slot)]
                                    (1..3).map { LootSystem.createSetPiece(set, base, 0, rng) }.maxBy { score(it) }
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
                            hit += ArmorClass.hitChance(hero.armorClass, ArmorClass.monsterAttack(floor))
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
                (1..n).map { LootSystem.rating(LootSystem.create(IsotopeSets.BASES.random(rng), power, Rarity.RARE, 0, rng, forcedWeight = w)) }.average()
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
        val weapons = listOf(ItemBase.SWORD, ItemBase.AXE, ItemBase.DAGGER, ItemBase.MACE, ItemBase.STAFF, ItemBase.SCEPTER)
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

    /**
     * Les sets qu'un banc mesure : SIM_SETS_ONLY=1,2,3 (numéros atomiques) ou, par défaut, les cinq premiers, un par
     * archétype. Les 113 sets, ce serait des heures pour rien : le bonus ne dépend que de l'archétype.
     */
    private fun simSets(only: List<Int>? = System.getenv("SIM_SETS_ONLY")?.split(",")?.map { it.trim().toInt() }) =
        IsotopeSets.ALL.filter { if (only != null) it.z in only else it.z <= Archetype.entries.size }

    /** Les étages qu'on regarde pour un set : sa tranche (début, milieu, fin), puis ce qui suit, quand ses pièces vieillissent. */
    private fun setFloors(set: IsotopeSet) = listOf(set.firstFloor, (set.firstFloor + set.lastFloor) / 2, set.lastFloor,
        set.lastFloor + 10, set.lastFloor + 25)

    /**
     * Ce que valent les sets : pour chacun, aux étages de sa tranche puis au-delà, le même héros
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
            val out = StringBuilder("══════ Sets d'isotope à équipement égal ($series séries de $chain combats, joueur $skill) ══════\n")
            out.appendLine("victoires · PV perdus par combat · note de l'armure ; la comparaison juste est « même poids » contre « set »\n")
            for (set in simSets()) {
                out.appendLine("── ${set.symbol()}-${set.mass} (${set.archetype}, étages ${set.firstFloor}–${set.lastFloor}) ──")
                out.appendLine(String.format("%5s", "Étage") + SetGear.entries.joinToString("") { String.format("%26s", it.label) })
                val floors = setFloors(set)
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
     * Les sets avec toutes sortes de reliques : chaque relique seule (et « aucun sort »), à trois étages de
     * la tranche du set, l'armure du même poids contre le set. Une cellule : victoires de l'un → de l'autre.
     * SIM_SERIES=300, SIM_SETS_ONLY=1,2,3 (les numéros atomiques des sets à mesurer).
     */
    @Test
    fun isotopeSetsWithRelics() {
        val series = System.getenv("SIM_SERIES")?.toInt() ?: 300
        val chain = System.getenv("SIM_CHAIN")?.toInt() ?: 3
        val only = System.getenv("SIM_SETS_ONLY")?.split(",")?.map { it.trim().toInt() }
        val configs = listOf<Relic?>(null) + Relic.entries.filter { simRelics == null || it.name in simRelics }
        val start = System.currentTimeMillis()
        val saved = IsotopeSets.dropShare
        IsotopeSets.dropShare = 0f
        try {
            val out = StringBuilder("══════ Sets d'isotope avec une relique ($series séries de $chain combats, joueur correct) ══════\n")
            out.appendLine("cellule : victoires « même poids » → « set », en %\n")
            for (set in simSets(only)) {
                val floors = listOf(set.firstFloor, (set.firstFloor + set.lastFloor) / 2, set.lastFloor)
                out.appendLine("── ${set.symbol()}-${set.mass} (${set.archetype}) ──")
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
        // Un sort sur soi (Soin, Sablier) ne touche pas l'ennemi : il ne déclenche rien
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
    private fun chooseRelics(hero: Hero, floor: Int, skill: Skill, wanted: Archetype? = null) {
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
            repeat(trialSeries) {
                val h = trialCopy(hero, relics)
                var ok = true
                repeat(3) { if (ok) ok = soloFight(h, floor, skill, rng, stat, combos = true) }
                if (ok) wins++
            }
            return wins * TRIAL_WIN_WEIGHT - stat.turnsInFight
        }
        // Toutes les reliques sont essayées seules. On réserve ensuite deux places aux partenaires
        // des six meilleures : un sort moyen seul peut être indispensable au meilleur combo.
        val ranked = owned.sortedByDescending { trial(listOf(it)) }
        val shortlist = if (owned.size <= 8) owned else {
            val own = if (wanted == null) emptyList() else ranked.filter { it.attribute == preferredStat(wanted) && it != Relic.HEAL }.take(2)
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
                relics.count { it.attribute == preferredStat(wanted) && it != Relic.HEAL } * (TRIAL_WIN_WEIGHT / 4)
        }
        hero.relicSlots.fill(null)
        best.forEachIndexed { i, r -> hero.relicSlots[i] = r }
        hero.knownResonances += hero.resonances
        hero.hp = hero.hp.coerceAtMost(hero.maxHp)
    }

    /** Un héros d'essai : même équipement, les reliques [relics], PV pleins, recharges prêtes. */
    private fun trialCopy(src: Hero, relics: List<Relic>): Hero = Hero().apply {
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
    private fun candidateLoadouts(hero: Hero): List<Loadout> {
        val pool = hero.equipped.values + hero.bag
        fun best(slot: EquipSlot, a: Archetype?, filter: (Equipment) -> Boolean = { true }) =
            pool.filter { it.slot == slot && filter(it) }.maxByOrNull { LootSystem.rating(it, a) }
        val out = mutableListOf<Loadout>(hero.equipped.toMap())
        for (a in Archetype.entries) {
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
    private fun optimizeGear(hero: Hero, floor: Int, skill: Skill, r: Report, wanted: Archetype? = null) {
        val candidates = candidateLoadouts(hero)
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
        val g = RoguelikeGame(rng = rng, checkpointEvery = checkpointEvery, deathRetreat = deathRetreat)
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

        while (g.floor <= maxFloor && turns++ < maxMapTurns) {
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
                optimizeGear(g.hero, maxOf(g.floor, wallFloor), skill, r, wanted)
                wallFloor = 0
            }
            if (g.floor != lastFloor) {
                lastFloor = g.floor
                r.f(g.floor).entries++
                r.entriesByBand.merge(g.floor / 50, 1, Int::plus)
                if (reached.add(g.floor)) {
                    r.deathsBeforeFloor.getOrPut(g.floor) { mutableListOf() } += deaths
                    r.gearOnArrival.getOrPut(g.floor) { mutableListOf() } += g.hero.equipped.values.sumOf { LootSystem.rating(it) }
                }
                best = maxOf(best, g.floor)
            }
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
                    if (e.isotopeZ != null) newSetPiece = true
                    val cur = g.hero.equipped[e.slot]
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
                    if (cur == null || dropScore(e) > dropScore(cur)) g.equipPendingDrop() else g.stashPendingDrop()
                }
                g.stairsOpen -> g.descend()
                else -> { mapStep(g, mem); r.f(g.floor).mapTurns++ }
            }
        }
        if (turns >= maxMapTurns) {
            r.timeouts++
            val top = turnsByFloor.entries.sortedByDescending { it.value }.take(3).joinToString(", ") { "étage ${it.key} : ${it.value} tours de carte, ${deathsByFloor[it.key] ?: 0} morts" }
            r.timeoutInfo += "profil n°$profileNo, budget épuisé à l'étage ${g.floor} (meilleur $best, $deaths morts, ${g.hero.archetype}, PV ${g.hero.hp}/${g.hero.maxHp}) ; là où il a passé son temps : $top"
        }
        r.bestFloors += best
        r.archetypesAtEnd += (g.hero.archetype?.name ?: "aucun") + if (g.hero.setArchetype != null) "+set" else ""
        if (g.hero.setArchetype != null) r.setBonusAtEnd++
        r.setPiecesAtEnd += g.hero.equipped.values.count { it.isotopeZ != null }
        r.finalRelics += g.hero.relicSlots.filterNotNull().sortedBy { it.ordinal }.joinToString(" + ") { it.name }
    }

    /** Joue un combat entier. Renvoie true si le héros est mort. */
    private fun fight(g: RoguelikeGame, skill: Skill, r: Report, rng: Random, smart: Boolean = this.smart): Boolean {
        val c = g.combat!!
        val fs = r.f(g.floor)
        fs.fights++
        fs.groupSizes += c.enemies.size
        if (c.ambush) fs.ambushes++
        val hpStart = c.hero.hp
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
        g.finishCombat()
        r.nextFightChained = !died && g.combat != null
        if (r.nextFightChained) r.f(floorBefore).chains++
        return died
    }

    private fun playCombat(c: Combat, skill: Skill, fs: FloorStat, rng: Random, combos: Boolean = false, cs: CombatStat? = null, useSpecial: Boolean = true) {
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
                // Le Soin se garde pour quand il le faut : la même règle que l'ancienne potion
                val healNow = Relic.HEAL in c.hero.relicSlots && c.canCast(Relic.HEAL) &&
                    (simRelics == null || Relic.HEAL.name in simRelics) &&
                    c.hero.hp < c.hero.maxHp && !lastWasSupport &&
                    c.hero.hp <= incoming * 1.3f + c.hero.maxHp * 0.1f
                val ready = c.hero.relicSlots.filterNotNull().firstOrNull {
                    val e = c.enemies[aimAt(it)]
                    it != Relic.HEAL && c.canCast(it) && (simRelics == null || it.name in simRelics) && !(lastWasSupport && !it.hits) &&
                        e.type.affinity(it.element) != Affinity.IMMUNE &&
                        !(e.enraged && (it.element == Element.ICE || it.element == Element.LIGHTNING || it.effect == RelicEffect.SLOW))
                }
                // Le « Spécial » : le voleur achève une cible exposée, le guerrier se met en garde
                // devant un gros coup, le mage lève ses doubles dès qu'il n'en a plus
                val exposed = alive.filter { c.isExposed(c.enemies[it]) }.minByOrNull { c.enemies[it].hp }
                val special = if (!useSpecial || !c.canUseSpecial()) null else when (c.hero.archetype) {
                    Archetype.ROGUE   -> exposed?.let { { c.deadlyStrike(it, strike(skill, rng, c.hero)); Unit } }
                    // Garde et doubles ne frappent pas : jamais juste après un autre tour sans frapper
                    Archetype.WARRIOR -> if (incoming > c.hero.maxHp * 0.2f && !lastWasSupport) ({ c.guard(); lastWasSupport = true }) else null
                    Archetype.MAGE    -> if (c.mirrorImages == 0 && !lastWasSupport) ({ c.mirrorImage(); lastWasSupport = true }) else null
                    // Deux coups d'arme : il enchaîne dès qu'il peut. Le nécromancien invoque ses pantins, puis rappelle les tombés.
                    Archetype.BARBARIAN -> ({ c.smash(target, strike(skill, rng, c.hero)); Unit })
                    Archetype.VAGABOND -> ({ c.chain(target, strike(skill, rng, c.hero), strike(skill, rng, c.hero)); Unit })
                    Archetype.NECROMANCER -> if ((c.puppetHp.isEmpty() || c.puppetHp.any { it <= 0 }) && !lastWasSupport) ({ c.recallPuppets(); lastWasSupport = true }) else null
                    null -> null
                }
                lastWasSupport = false
                val combo = if (combos) comboChoice(c, target) else null
                when {
                    healNow -> { cs?.cast(Relic.HEAL); c.castRelic(Relic.HEAL, target, Timing.MISS); lastWasSupport = true }
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
            } else {
                val (_, attackers) = c.startEnemyTurn()
                for (a in attackers) {
                    if (c.phase != CombatPhase.ENEMY_TURN) break
                    c.resolveStrike(a, parry(skill, rng, c.hero, guarding = c.guarding, slowed = c.hourglassStrikes > 0))
                }
                c.endEnemyTurn()
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

    private class MapMemory {
        val recent = ArrayDeque<Pos>()
        /** Pas restants pendant lesquels il ne contourne plus les monstres (il va au combat). */
        var forceFight = 0
    }

    private fun mapStep(g: RoguelikeGame, mem: MapMemory = MapMemory()) {
        val lv = g.level
        val hero = g.hero
        val visiblePacks = lv.packs.filter { it.alive && lv.visible[it.pos.y][it.pos.x] }

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
}
