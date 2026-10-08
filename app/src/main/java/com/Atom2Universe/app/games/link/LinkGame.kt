package com.Atom2Universe.app.games.link

import kotlin.random.Random

/**
 * Intrication (ancien « Link ») : les règles, sans rien d'Android.
 *
 * Chaque case de la grille est un atome excité ; son **énergie** (0 à 4) dit combien de
 * fois il doit encore être touché. Le niveau donne une **main de pièces** (dominos,
 * trominos, tétrominos) : poser une pièce sur des atomes leur retire un niveau
 * d'énergie chacun. Deux atomes **intriqués** partagent leur sort : toucher l'un fait
 * aussi descendre l'autre, où qu'il soit sur la grille.
 *
 * On ne pose jamais une pièce qui ferait descendre un atome sous zéro, jumeau compris.
 * La partie est gagnée quand toutes les pièces sont posées et tous les atomes au repos.
 *
 * Le générateur fabrique les énergies en posant lui-même les pièces sur une grille au
 * repos : une solution existe toujours, et toute autre façon de tout poser gagne aussi.
 *
 * Une **partie** compte [FLOORS] étages de plus en plus chargés ; le dernier est le boss,
 * avec toutes les formes et un maximum de paires. Chaque étage rapporte des points
 * selon sa taille, moins ce qu'ont coûté les annulations : le score récompense celui
 * qui réfléchit avant de poser.
 */
class LinkGame {

    /** Une pièce, en cases relatives, ramenée en haut à gauche. */
    class Shape(val id: Int, cells: List<Pair<Int, Int>>) {
        val cells: List<Pair<Int, Int>> = normalize(cells)
        val size get() = cells.size
        /** Toutes les orientations (rotations et miroirs), sans doublon. */
        val variants: List<List<Pair<Int, Int>>> = run {
            val seen = LinkedHashMap<String, List<Pair<Int, Int>>>()
            var cur = this.cells
            repeat(4) {
                cur = normalize(cur.map { (x, y) -> -y to x })
                seen.getOrPut(key(cur)) { cur }
                val mirror = normalize(cur.map { (x, y) -> -x to y })
                seen.getOrPut(key(mirror)) { mirror }
            }
            seen.values.toList()
        }
        private val keys = variants.map { key(it) }.toSet()
        fun matches(normalized: List<Pair<Int, Int>>) = key(normalized) in keys
    }

    /** Une pièce posée : la pièce de la main, et les cases touchées (indices y × largeur + x). */
    class Placement(val handIndex: Int, val cells: List<Int>)

    enum class Result { PLACED, NO_MATCH, EXHAUSTED }

    companion object {
        const val MAX_ENERGY = 4
        const val FLOORS = 15
        /** Ce que coûte une annulation, et un étage recommencé, en part des points de l'étage. */
        const val UNDO_COST = 0.10f
        const val RESTART_COST = 0.25f
        /** Bonus d'un étage fini sans rien annuler ni recommencer. */
        const val PERFECT_BONUS = 0.25f
        /** Un étage fini rapporte toujours au moins cette part. */
        const val FLOOR_MIN_SHARE = 0.2f

        val SHAPES = listOf(
            Shape(0, listOf(0 to 0, 1 to 0)),                         // domino
            Shape(1, listOf(0 to 0, 1 to 0, 2 to 0)),                 // ligne de 3
            Shape(2, listOf(0 to 0, 1 to 0, 0 to 1)),                 // coin
            Shape(3, listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)),         // carré
            Shape(4, listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)),         // ligne de 4
            Shape(5, listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2)),         // L
            Shape(6, listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1)),         // T
            Shape(7, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1))          // zigzag
        )

        fun normalize(cells: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val mx = cells.minOf { it.first }; val my = cells.minOf { it.second }
            return cells.map { (x, y) -> (x - mx) to (y - my) }.sortedWith(compareBy({ it.second }, { it.first }))
        }

        private fun key(cells: List<Pair<Int, Int>>) = cells.joinToString(";") { "${it.first},${it.second}" }

        /**
         * Ce que demande l'étage [floor] (1 à [FLOORS]). La grille grandit par paliers ;
         * les formes et les paires arrivent une à une, pour qu'on apprenne chaque règle
         * avant la suivante.
         */
        fun paramsFor(floor: Int): Params {
            val f = floor.coerceIn(1, FLOORS)
            if (f == FLOORS) return Params(size = 7, pieces = 20, pairs = 7, maxEnergy = MAX_ENERGY,
                shapePool = SHAPES.indices.toList(), everyShape = true)
            val size = when {
                f <= 3 -> 4
                f <= 7 -> 5
                f <= 11 -> 6
                else -> 7
            }
            val pool = when {
                f <= 4 -> listOf(0, 1, 2)
                f <= 7 -> listOf(0, 1, 2, 3, 4)
                else -> SHAPES.indices.toList()
            }
            val pairs = intArrayOf(0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 4, 4, 5, 5)[f - 1]
            // Assez de pièces pour que la grille soit pleine, puis de plus en plus chargée :
            // c'est la superposition des pièces qui fait le casse-tête.
            val density = (1.0f + 0.045f * (f - 1)).coerceAtMost(1.6f)
            val averageSize = pool.map { SHAPES[it].size }.average().toFloat()
            return Params(
                size = size,
                pieces = kotlin.math.ceil(size * size * density / averageSize).toInt().coerceAtMost(18),
                pairs = pairs,
                maxEnergy = if (f < 4) 3 else MAX_ENERGY,
                shapePool = pool
            )
        }

        /** Les points d'un étage : sa taille, moins ce qu'ont coûté les annulations. */
        fun floorScore(pieces: Int, floor: Int, undos: Int, restarts: Int): Int {
            val base = pieces * 10 * floor
            val kept = (1f - UNDO_COST * undos - RESTART_COST * restarts).coerceAtLeast(FLOOR_MIN_SHARE)
            val perfect = undos == 0 && restarts == 0
            return (base * kept).toInt() + if (perfect) (base * PERFECT_BONUS).toInt() else 0
        }
    }

    class Params(
        val size: Int, val pieces: Int, val pairs: Int, val maxEnergy: Int, val shapePool: List<Int>,
        /** Le boss : chaque forme au moins une fois dans la main. */
        val everyShape: Boolean = false
    )

    /** L'étage en cours, 1 à [FLOORS]. */
    var floor = 1
    var width = 0
        private set
    var height = 0
        private set
    /** Énergies au début du niveau. */
    var initial = IntArray(0)
        private set
    var energy = IntArray(0)
        private set
    /** Paires intriquées, en indices de cases : [a0, b0, a1, b1, …]. */
    var pairCells = IntArray(0)
        private set
    /** Jumeau de chaque case, -1 si elle n'est pas intriquée. */
    private var partner = IntArray(0)
    /** La main : un indice de [SHAPES] par pièce. */
    var hand = IntArray(0)
        private set
    val placements = mutableListOf<Placement>()
    /** Les points de l'étage en cours ont été comptés. */
    var floorScored = false
        private set
    /** Annulations et recommencements de l'étage en cours : c'est ce qui coûte des points. */
    var undos = 0
        private set
    var restarts = 0
        private set
    /** Points de chaque étage fini, -1 tant qu'il ne l'est pas. */
    val floorScores = IntArray(FLOORS) { -1 }
    /** Étages finis sans rien annuler ni recommencer. */
    val perfectFloors = BooleanArray(FLOORS)
    /** Les poses du générateur (non sauvegardées) : une solution, pour les tests. */
    var solution: List<List<Int>> = emptyList()
        private set

    val pairCount get() = pairCells.size / 2
    fun partnerOf(cell: Int) = partner.getOrElse(cell) { -1 }
    fun isUsed(handIndex: Int) = placements.any { it.handIndex == handIndex }
    val isVictory get() = placements.size == hand.size && energy.all { it == 0 }
    val isBoss get() = floor == FLOORS
    val totalScore get() = floorScores.filter { it > 0 }.sum()
    val isRunOver get() = floorScores[FLOORS - 1] >= 0

    // ── Partie ────────────────────────────────────────────────────────────────────
    fun newRun(random: Random = Random) {
        floor = 1
        floorScores.fill(-1)
        perfectFloors.fill(false)
        generate(random)
    }

    /** Compte les points de l'étage qu'on vient de finir ; rend -1 s'ils l'étaient déjà. */
    fun scoreFloor(): Int {
        if (floorScored || !isVictory) return -1
        floorScored = true
        val points = floorScore(hand.size, floor, undos, restarts)
        floorScores[floor - 1] = points
        perfectFloors[floor - 1] = undos == 0 && restarts == 0
        return points
    }

    fun nextFloor(random: Random = Random) {
        if (floor >= FLOORS) return
        floor++
        generate(random)
    }

    // ── Génération ────────────────────────────────────────────────────────────────
    fun generate(random: Random = Random) {
        val p = paramsFor(floor)
        repeat(400) {
            if (tryGenerate(p, random)) return
        }
        // Filet de sécurité : sans paire, une grille se remplit toujours.
        tryGenerate(Params(p.size, p.pieces, 0, p.maxEnergy, p.shapePool, p.everyShape), random)
    }

    private fun tryGenerate(p: Params, random: Random): Boolean {
        val n = p.size * p.size
        val e = IntArray(n)
        val cells = (0 until n).shuffled(random)
        val pairs = IntArray(p.pairs * 2) { cells[it] }
        val part = IntArray(n) { -1 }
        for (k in 0 until p.pairs) { part[pairs[k * 2]] = pairs[k * 2 + 1]; part[pairs[k * 2 + 1]] = pairs[k * 2] }

        val shapes = IntArray(p.pieces) { i ->
            if (p.everyShape && i < p.shapePool.size) p.shapePool[i] else p.shapePool[random.nextInt(p.shapePool.size)]
        }
        // Les grosses pièces d'abord : elles ont besoin de place.
        val order = shapes.sortedByDescending { SHAPES[it].size }
        val touchedPairs = HashSet<Int>()
        val poses = mutableListOf<List<Int>>()
        for (s in order) {
            var best: List<Int>? = null
            var bestScore = -1
            repeat(40) {
                val v = SHAPES[s].variants[random.nextInt(SHAPES[s].variants.size)]
                val w = v.maxOf { it.first } + 1; val h = v.maxOf { it.second } + 1
                if (w > p.size || h > p.size) return@repeat
                val ox = random.nextInt(p.size - w + 1); val oy = random.nextInt(p.size - h + 1)
                val placed = v.map { (x, y) -> (oy + y) * p.size + ox + x }
                val hit = affected(placed, part)
                if (hit.any { e[it] >= p.maxEnergy }) return@repeat
                // On préfère couvrir des atomes encore au repos, pour que la grille soit pleine.
                val score = hit.count { e[it] == 0 } * 4 + random.nextInt(4)
                if (score > bestScore) { bestScore = score; best = placed }
            }
            val chosen = best ?: return false
            poses.add(chosen)
            for (c in affected(chosen, part)) e[c]++
            for (c in chosen) if (part[c] >= 0) touchedPairs.add(minOf(c, part[c]))
        }
        // Une paire que rien ne touche n'apprend rien au joueur : on recommence.
        if (touchedPairs.size < p.pairs) return false
        if (e.count { it == 0 } > n / 4) return false

        width = p.size; height = p.size
        initial = e; energy = e.copyOf()
        pairCells = pairs; partner = part
        hand = shapes.sortedBy { SHAPES[it].size * 10 + it }.toIntArray()
        placements.clear()
        floorScored = false
        undos = 0; restarts = 0
        solution = poses
        return true
    }

    /** Les cases qu'une pose touche : ses cases, plus le jumeau de chacune s'il n'est pas déjà dedans. */
    private fun affected(cells: List<Int>, part: IntArray): List<Int> {
        val out = cells.toMutableList()
        for (c in cells) {
            val t = part[c]
            if (t >= 0 && t !in cells && t !in out) out.add(t)
        }
        return out
    }

    fun affected(cells: List<Int>): List<Int> = affected(cells, partner)

    // ── Jeu ───────────────────────────────────────────────────────────────────────
    /** La pièce de la main, encore libre, dont [cells] a la forme ; -1 si aucune. */
    fun matchingPiece(cells: List<Int>): Int {
        if (cells.isEmpty() || cells.toSet().size != cells.size) return -1
        val shape = normalize(cells.map { (it % width) to (it / width) })
        for (i in hand.indices) {
            if (!isUsed(i) && SHAPES[hand[i]].size == cells.size && SHAPES[hand[i]].matches(shape)) return i
        }
        return -1
    }

    /**
     * Pose une pièce sur [cells]. NO_MATCH : aucune pièce libre n'a cette forme.
     * EXHAUSTED : un des atomes touchés (jumeaux compris) est déjà au repos.
     */
    fun place(cells: List<Int>): Result {
        val piece = matchingPiece(cells)
        if (piece < 0) return Result.NO_MATCH
        val hit = affected(cells)
        if (hit.any { energy[it] <= 0 }) return Result.EXHAUSTED
        for (c in hit) energy[c]--
        placements.add(Placement(piece, cells.toList()))
        return Result.PLACED
    }

    fun undo(): Placement? {
        if (floorScored) return null
        val last = placements.removeLastOrNull() ?: return null
        for (c in affected(last.cells)) energy[c]++
        undos++
        return last
    }

    fun restart() {
        if (floorScored || placements.isEmpty()) return
        placements.clear()
        energy = initial.copyOf()
        restarts++
    }

    // ── Sauvegarde ────────────────────────────────────────────────────────────────
    /**
     * Une ligne : `2;étage;taille;énergies;paires;main;poses;compté;annulations;recommencements;scores;parfaits`.
     * Les énergies sont un chiffre par case ; les poses, `pièce:cases` séparées par des
     * barres ; les parfaits, un 0 ou un 1 par étage.
     */
    fun serialize(): String {
        if (width == 0) return ""
        return buildString {
            append("2;").append(floor).append(';').append(width)
            append(';').append(initial.joinToString(""))
            append(';').append(pairCells.joinToString(","))
            append(';').append(hand.joinToString(","))
            append(';').append(placements.joinToString("/") { "${it.handIndex}:${it.cells.joinToString(",")}" })
            append(';').append(if (floorScored) 1 else 0)
            append(';').append(undos).append(';').append(restarts)
            append(';').append(floorScores.joinToString(","))
            append(';').append(perfectFloors.joinToString("") { if (it) "1" else "0" })
        }
    }

    fun deserialize(line: String): Boolean {
        val f = line.split(';')
        if (f.size != 12 || f[0] != "2") return false
        return try {
            fun ints(s: String) = if (s.isEmpty()) IntArray(0) else s.split(',').map { it.toInt() }.toIntArray()
            val size = f[2].toInt()
            val init = IntArray(f[3].length) { f[3][it] - '0' }
            if (init.size != size * size) return false
            val scores = ints(f[10])
            if (scores.size != FLOORS || f[11].length != FLOORS) return false
            val pairs = ints(f[4])
            val part = IntArray(size * size) { -1 }
            for (k in 0 until pairs.size / 2) { part[pairs[k * 2]] = pairs[k * 2 + 1]; part[pairs[k * 2 + 1]] = pairs[k * 2] }
            floor = f[1].toInt().coerceIn(1, FLOORS)
            width = size; height = size
            initial = init; energy = init.copyOf()
            pairCells = pairs; partner = part
            hand = ints(f[5])
            placements.clear()
            if (f[6].isNotEmpty()) for (p in f[6].split('/')) {
                val (piece, cells) = p.split(':')
                val list = ints(cells).toList()
                for (c in affected(list)) energy[c]--
                placements.add(Placement(piece.toInt(), list))
            }
            floorScored = f[7] == "1"
            undos = f[8].toInt(); restarts = f[9].toInt()
            scores.copyInto(floorScores)
            for (i in 0 until FLOORS) perfectFloors[i] = f[11][i] == '1'
            energy.all { it >= 0 }
        } catch (_: RuntimeException) {
            false
        }
    }
}
