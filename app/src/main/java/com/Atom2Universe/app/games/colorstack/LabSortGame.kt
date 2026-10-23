package com.Atom2Universe.app.games.colorstack

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Les liquides des éprouvettes : de vraies solutions de laboratoire, avec leur vraie couleur.
 * La liste est triée du plus distinct au plus ressemblant, pour que les petites parties
 * piochent d'abord des couleurs bien séparées.
 */
object LabLiquids {
    class Liquid(val hex: Int, val formula: String)

    val ALL = listOf(
        Liquid(0xFF3D8BFD.toInt(), "CuSO₄"),        // sulfate de cuivre : bleu
        Liquid(0xFFFF8A1F.toInt(), "K₂Cr₂O₇"),      // dichromate de potassium : orange
        Liquid(0xFF34C759.toInt(), "NiCl₂"),        // chlorure de nickel : vert
        Liquid(0xFFE5282E.toInt(), "FeSCN²⁺"),      // thiocyanate de fer : rouge sang
        Liquid(0xFF9B4DFF.toInt(), "KMnO₄"),        // permanganate de potassium : violet
        Liquid(0xFFFFD60A.toInt(), "K₂CrO₄"),       // chromate de potassium : jaune
        Liquid(0xFF17C3C3.toInt(), "CuCl₂"),        // chlorure de cuivre : turquoise
        Liquid(0xFFFF6FA5.toInt(), "CoCl₂"),        // chlorure de cobalt : rose
        Liquid(0xFF8A5A2B.toInt(), "I₂"),           // iode : brun
        Liquid(0xFF2431B8.toInt(), "CoCl₄²⁻"),      // complexe du cobalt : bleu nuit
        Liquid(0xFFC8E64A.toInt(), "Cl₂"),          // eau de chlore : vert-jaune
        Liquid(0xFF0F7A4A.toInt(), "K₂MnO₄"),       // manganate de potassium : vert sombre
        Liquid(0xFFE2199A.toInt(), "C₂₀H₁₄O₄"),     // phénolphtaléine en milieu basique : magenta
        Liquid(0xFFAEB7C2.toInt(), "Hg")            // mercure : gris argent
    )
}

/**
 * Éprouvettes de 4 niveaux : on verse tout le groupe du dessus sur la même couleur ou dans un tube
 * vide. Le mélange de départ est fabriqué en rejouant la partie à l'envers avec les seuls coups
 * qui sont l'inverse d'un versement permis : chaque niveau est donc résoluble par construction,
 * sans solveur ni validation.
 */
class LabSortGame(private val rng: Random = Random.Default) {

    enum class Difficulty { EASY, MEDIUM, HARD }

    class Config(
        val colors: Int,
        val empties: Int,
        /** Nombre de liquides parmi lesquels on pioche (les plus distincts d'abord). */
        val poolSize: Int
    )

    /** Un niveau de liquide ; `revealed` faux = caché dans le mode mystère. */
    class Cell(val colorId: Int, var revealed: Boolean)

    class Move(val from: Int, val to: Int, val count: Int, val pipette: Boolean)

    companion object {
        const val CAPACITY = 4
        const val UNDO_LIMIT = 10
        const val PIPETTE_START = 1
        const val PIPETTE_MAX = 3

        val CONFIGS = mapOf(
            Difficulty.EASY to Config(colors = 9, empties = 2, poolSize = 11),
            Difficulty.MEDIUM to Config(colors = 12, empties = 2, poolSize = 14),
            Difficulty.HARD to Config(colors = 14, empties = 2, poolSize = 14)
        )

        private const val MAX_ATTEMPTS = 200
    }

    var tubes: MutableList<ArrayList<Cell>> = mutableListOf()
        private set
    private var initialTubes: List<List<Cell>> = emptyList()
    val history = mutableListOf<Move>()

    var difficulty = Difficulty.EASY
        private set
    var mystery = false
        private set
    var moves = 0
        private set
    var undoLeft = UNDO_LIMIT
        private set
    var pipetteCharges = PIPETTE_START
        private set
    var solved = false
        private set

    private var bestCompleted = 0

    /** Trace du mélange (source, cible, quantité) à l'envers ; sert aux tests. */
    internal var generationTrail: List<IntArray> = emptyList()
        private set

    val tubeCount get() = tubes.size

    fun newGame(diff: Difficulty, mysteryMode: Boolean) {
        difficulty = diff
        mystery = mysteryMode
        val cfg = CONFIGS.getValue(diff)
        val ids = (0 until cfg.poolSize).shuffled(rng).take(cfg.colors)
        val colors = generate(cfg, ids)
        tubes = colors.map { col -> ArrayList(col.map { Cell(it, !mysteryMode) }) }.toMutableList()
        revealTops()
        initialTubes = snapshot()
        arrangeStartingTubes()
        resetCounters()
    }

    /** Place les tubes initialement vides à la fin, puis garde cet ordre pendant toute la partie. */
    private fun arrangeStartingTubes() {
        val order = initialTubes.indices.sortedBy { initialTubes[it].isEmpty() }
        val newIndices = IntArray(order.size)
        order.forEachIndexed { new, old -> newIndices[old] = new }
        tubes = order.map { tubes[it] }.toMutableList()
        initialTubes = order.map { initialTubes[it] }
        // La preuve de résolution utilise les mêmes indices que le plateau réordonné.
        generationTrail = generationTrail.map { m ->
            intArrayOf(newIndices[m[0]], newIndices[m[1]], m[2])
        }
    }

    fun restart() {
        tubes = initialTubes.map { col -> ArrayList(col.map { Cell(it.colorId, it.revealed) }) }.toMutableList()
        resetCounters()
    }

    private fun resetCounters() {
        history.clear()
        moves = 0
        undoLeft = UNDO_LIMIT
        pipetteCharges = PIPETTE_START
        bestCompleted = completedCount()
        solved = checkSolved()
    }

    private fun snapshot(): List<List<Cell>> =
        tubes.map { col -> col.map { Cell(it.colorId, it.revealed) } }

    private fun revealTops() {
        tubes.forEach { if (it.isNotEmpty()) it.last().revealed = true }
    }

    fun topGroupSize(i: Int): Int {
        val t = tubes.getOrNull(i) ?: return 0
        if (t.isEmpty()) return 0
        val c = t.last().colorId
        var n = 0
        for (k in t.indices.reversed()) if (t[k].colorId == c) n++ else break
        return n
    }

    fun canPour(from: Int, to: Int): Boolean {
        if (from == to || from !in tubes.indices || to !in tubes.indices) return false
        val src = tubes[from]
        val dst = tubes[to]
        if (src.isEmpty() || dst.size >= CAPACITY) return false
        return dst.isEmpty() || dst.last().colorId == src.last().colorId
    }

    /** Verse le groupe du dessus autant que la cible peut en prendre ; rend le nombre versé. */
    fun pour(from: Int, to: Int): Int {
        if (!canPour(from, to)) return 0
        val count = minOf(topGroupSize(from), CAPACITY - tubes[to].size)
        transfer(from, to, count)
        history.add(Move(from, to, count, pipette = false))
        afterMove()
        return count
    }

    fun canPipette(from: Int, to: Int): Boolean =
        pipetteCharges > 0 && from != to && from in tubes.indices && to in tubes.indices &&
            tubes[from].isNotEmpty() && tubes[to].size < CAPACITY

    /** La pipette déplace un seul niveau, quelle que soit la couleur d'arrivée. */
    fun pipette(from: Int, to: Int): Boolean {
        if (!canPipette(from, to)) return false
        pipetteCharges--
        transfer(from, to, 1)
        history.add(Move(from, to, 1, pipette = true))
        afterMove()
        return true
    }

    fun undo(): Boolean {
        if (history.isEmpty() || undoLeft <= 0) return false
        val m = history.removeAt(history.lastIndex)
        repeat(m.count) { tubes[m.from].add(tubes[m.to].removeAt(tubes[m.to].lastIndex)) }
        if (m.pipette) pipetteCharges++
        undoLeft--
        moves = (moves - 1).coerceAtLeast(0)
        solved = false
        return true
    }

    private fun transfer(from: Int, to: Int, count: Int) {
        repeat(count) {
            val cell = tubes[from].removeAt(tubes[from].lastIndex)
            cell.revealed = true
            tubes[to].add(cell)
        }
        if (tubes[from].isNotEmpty()) tubes[from].last().revealed = true
    }

    private fun afterMove() {
        moves++
        val done = completedCount()
        if (done > bestCompleted) {
            pipetteCharges = minOf(PIPETTE_MAX, pipetteCharges + (done - bestCompleted))
            bestCompleted = done
        }
        solved = checkSolved()
    }

    /** Tubes pleins d'une seule couleur, entièrement révélés. */
    fun completedCount() = tubes.count { isComplete(it) }

    fun isComplete(t: List<Cell>) =
        t.size == CAPACITY && t.all { it.revealed && it.colorId == t[0].colorId }

    private fun checkSolved() = tubes.all { it.isEmpty() || isComplete(it) }

    // ── Génération : le jeu à l'envers ─────────────────────────────────────────────────────────

    private fun generate(cfg: Config, ids: List<Int>): List<List<Int>> {
        var last: List<List<Int>> = emptyList()
        repeat(MAX_ATTEMPTS) {
            val tubesInt = ArrayList<ArrayList<Int>>()
            ids.forEach { id -> tubesInt.add(ArrayList(List(CAPACITY) { id })) }
            repeat(cfg.empties) { tubesInt.add(ArrayList()) }

            val trail = ArrayList<IntArray>()
            val minSteps = cfg.colors * 5
            val maxSteps = cfg.colors * 40
            for (step in 1..maxSteps) {
                val candidates = reverseCandidates(tubesInt, trail.lastOrNull())
                if (candidates.isEmpty()) break
                val m = pickTowardTidy(tubesInt, candidates)
                repeat(m[2]) { tubesInt[m[1]].add(tubesInt[m[0]].removeAt(tubesInt[m[0]].lastIndex)) }
                trail.add(m)
                if (step >= minSteps && isWellMixed(tubesInt, cfg)) {
                    generationTrail = trail
                    return tubesInt.map { it.toList() }
                }
            }
            last = tubesInt.map { it.toList() }
            generationTrail = trail
        }
        return last
    }

    /**
     * Les coups à l'envers : (tube d'où l'on retire, tube où l'on dépose, quantité). Retirer k niveaux
     * du dessus de B pour les poser sur A est l'inverse d'un versement permis de A vers B quand
     * - B, une fois allégé, est vide ou garde la même couleur au sommet (sinon B n'aurait pas pu recevoir),
     * - et le versement aurait bien déplacé exactement k niveaux : soit A n'avait pas d'autre niveau de
     *   cette couleur au sommet, soit B est plein (c'est la place qui a limité le versement).
     */
    private fun reverseCandidates(t: List<List<Int>>, previous: IntArray?): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (b in t.indices) {
            val tb = t[b]
            if (tb.isEmpty()) continue
            val c = tb.last()
            var g = 0
            for (k in tb.indices.reversed()) if (tb[k] == c) g++ else break
            for (a in t.indices) {
                if (a == b) continue
                val ta = t[a]
                // Ne pas défaire tout de suite le coup précédent.
                if (previous != null && previous[0] == a && previous[1] == b) continue
                val aTopIsC = ta.isNotEmpty() && ta.last() == c
                for (k in 1..g) {
                    if (k == g && g != tb.size) continue
                    if (ta.size + k > CAPACITY) break
                    if (aTopIsC && tb.size != CAPACITY) continue
                    out.add(intArrayOf(b, a, k))
                }
            }
        }
        return out
    }

    /**
     * Un départ de partie n'a que des tubes pleins ou vides. Un mélange au hasard s'en éloigne et n'y
     * revient presque jamais : on favorise donc les coups qui rapprochent de cet état (tube plein ou
     * vide), sans jamais interdire les autres.
     */
    private fun pickTowardTidy(t: List<List<Int>>, candidates: List<IntArray>): IntArray {
        fun tidy(size: Int) = if (size == 0 || size == CAPACITY) 1 else 0
        val weights = DoubleArray(candidates.size) { i ->
            val m = candidates[i]
            val b = t[m[0]].size
            val a = t[m[1]].size
            val delta = tidy(b - m[2]) + tidy(a + m[2]) - tidy(b) - tidy(a)
            Math.pow(4.0, delta.toDouble())
        }
        var r = rng.nextDouble() * weights.sum()
        for (i in weights.indices) {
            r -= weights[i]
            if (r <= 0) return candidates[i]
        }
        return candidates.last()
    }

    private fun isWellMixed(t: List<List<Int>>, cfg: Config): Boolean {
        if (t.count { it.isEmpty() } != cfg.empties) return false
        var pairs = 0
        var same = 0
        for (col in t) {
            if (col.isEmpty()) continue
            if (col.all { it == col[0] }) return false
            for (i in 0 until col.size - 1) {
                pairs++
                if (col[i] == col[i + 1]) same++
            }
        }
        return same * 3 <= pairs
    }

    // ── Sauvegarde ─────────────────────────────────────────────────────────────────────────────

    fun serialize(): String = JSONObject().apply {
        put("difficulty", difficulty.name)
        put("mystery", mystery)
        put("moves", moves)
        put("undoLeft", undoLeft)
        put("pipette", pipetteCharges)
        put("bestCompleted", bestCompleted)
        put("tubes", encode(tubes))
        put("initial", encode(initialTubes))
    }.toString()

    private fun encode(src: List<List<Cell>>): JSONArray {
        val arr = JSONArray()
        src.forEach { t ->
            val ta = JSONArray()
            t.forEach { ta.put(it.colorId * 2 + if (it.revealed) 1 else 0) }
            arr.put(ta)
        }
        return arr
    }

    private fun decode(arr: JSONArray): List<ArrayList<Cell>> = List(arr.length()) { i ->
        val ta = arr.getJSONArray(i)
        ArrayList(List(ta.length()) { j ->
            val v = ta.getInt(j)
            Cell(v / 2, v % 2 == 1)
        })
    }

    fun deserialize(json: String): Boolean = try {
        val o = JSONObject(json)
        difficulty = Difficulty.valueOf(o.getString("difficulty"))
        mystery = o.getBoolean("mystery")
        tubes = decode(o.getJSONArray("tubes")).toMutableList()
        initialTubes = decode(o.getJSONArray("initial"))
        moves = o.getInt("moves")
        undoLeft = o.getInt("undoLeft")
        pipetteCharges = o.getInt("pipette")
        bestCompleted = o.getInt("bestCompleted")
        history.clear()
        generationTrail = emptyList()
        arrangeStartingTubes()
        solved = checkSolved()
        true
    } catch (e: Exception) {
        false
    }
}
