package com.Atom2Universe.app.games.mercerie

import kotlin.random.Random

/** Ce qu'une pièce sait faire. */
enum class Kind {
    NORMAL,
    /** Bobine : vide sa ligne. */
    LINE_H,
    /** Bobine : vide sa colonne. */
    LINE_V,
    /** Pompon : explose autour de lui, deux fois. */
    BOMB,
    /** Bouton arc-en-ciel : emporte toute une couleur. */
    RAINBOW,
    /** Dé à coudre : ne s'aligne pas, il faut le faire descendre tout en bas. */
    THIMBLE
}

class Piece(val id: Int, var color: Int, var kind: Kind) {
    /** Épinglée : ne bouge pas tant qu'un alignement ou une explosion n'a pas ôté l'épingle. */
    var pinned = false
    /** Pompon qui a déjà explosé une fois : il explosera encore au prochain temps. */
    var armed = false
    var radius = 1
    internal var armedAt = -1
}

/** Une pièce qui disparaît. [toward] : la case du bonus qu'elle rejoint, sinon -1. */
class Gone(val piece: Piece, val cell: Int, val wave: Int, val toward: Int = -1, val collected: Boolean = false)

/** Une pièce bonus qui naît d'un alignement. */
class Born(val piece: Piece, val cell: Int, val wave: Int)

enum class FxType { LINE_H, LINE_V, BLAST, ZAP, UNPIN, KNOT, PATCH, TRANSFORM, BASKET }

/** Un effet à montrer. [arg] : la case d'arrivée d'un fil (ZAP), le rayon (BLAST) ou ce qu'il reste (KNOT, PATCH). */
class Fx(val type: FxType, val cell: Int, val wave: Int, val arg: Int = 0, val color: Int = -1)

class Pop(val cell: Int, val points: Int, val wave: Int)

/** Un pas du trajet d'une pièce qui tombe : à l'instant [tick], elle est sur [cell]. */
class Hop(val tick: Int, val cell: Int)

/** [path] commence par la case de départ ; si [spawned], la pièce arrive d'au-dessus de sa première case. */
class Fall(val piece: Piece, val path: List<Hop>, val spawned: Boolean)

sealed class Step
class ClearStep(
    val gone: List<Gone>, val born: List<Born>, val fx: List<Fx>, val pops: List<Pop>,
    val cascade: Int, val waves: Int
) : Step()
class FallStep(val falls: List<Fall>, val ticks: Int) : Step()
/** Le plateau est bloqué : les pièces changent de place. */
class ShuffleStep(val moved: List<Pair<Piece, Int>>) : Step()

/**
 * Le plateau et ses règles, sans rien d'Android.
 *
 * Un coup se joue avec [swap] ; l'écran appelle ensuite [advance] jusqu'à recevoir null, et montre
 * chaque [Step] avant de demander le suivant.
 */
class MercerieBoard(val level: MercerieLevel, seed: Long = System.nanoTime()) {
    val cols = level.cols
    val rows = level.rows
    val size = cols * rows
    private val rnd = Random(seed)
    private var nextId = 1

    val cell = BooleanArray(size)
    val piece = arrayOfNulls<Piece>(size)
    /** Nœud de laine : 0 à 3 couches, bouche la case. */
    val knot = IntArray(size)
    /** Écusson cousu sous la pièce : 0 à 2 couches. */
    val patch = IntArray(size)

    var score = 0; private set
    var movesLeft = level.moves; private set
    val collected = IntArray(COLORS)
    var patchesLeft = 0; private set
    var thimblesDone = 0; private set
    private var thimblesToCome = 0
    private var thimblesWaiting = 0
    var cascade = 0; private set
    /** Fin de niveau gagnée : les coups restants deviennent des bobines, puis tout part. */
    var bonus = false; private set
    private var bonusRounds = 0

    private var stepNo = 0
    private var needFall = false
    private var pendingCombo: IntArray? = null
    private var swapA = -1
    private var swapB = -1
    private var swapHorizontal = true

    init {
        for (r in 0 until rows) for (c in 0 until cols) {
            val i = r * cols + c
            when (level.map[r][c]) {
                ' ', '#' -> {}
                '.' -> cell[i] = true
                'p' -> { cell[i] = true; patch[i] = 1 }
                'P' -> { cell[i] = true; patch[i] = 2 }
                'k' -> { cell[i] = true; knot[i] = 1 }
                'K' -> { cell[i] = true; knot[i] = 2 }
                'M' -> { cell[i] = true; knot[i] = 3 }
                'q' -> { cell[i] = true; knot[i] = 1; patch[i] = 1 }
                'Q' -> { cell[i] = true; knot[i] = 2; patch[i] = 2 }
                'x', 'y' -> cell[i] = true
                else -> error("case inconnue '${level.map[r][c]}'")
            }
        }
        var tries = 0
        do { fill() } while (!hasMove() && ++tries < 50)
        for (r in 0 until rows) for (c in 0 until cols) {
            val ch = level.map[r][c]
            if (ch == 'x' || ch == 'y') {
                piece[r * cols + c]?.pinned = true
                if (ch == 'y') patch[r * cols + c] = 1
            }
        }
        patchesLeft = patch.count { it > 0 }
        val first = minOf(level.thimblesAtOnce, level.goal.thimbles)
        val tops = (0 until cols).map { topCell(it) }.filter { it >= 0 && knot[it] == 0 && piece[it]?.pinned == false }.shuffled(rnd)
        for (i in tops.take(first)) piece[i] = Piece(nextId++, -1, Kind.THIMBLE)
        thimblesToCome = level.goal.thimbles - minOf(first, tops.size)
    }

    fun idx(r: Int, c: Int) = r * cols + c
    fun rowOf(i: Int) = i / cols
    fun colOf(i: Int) = i % cols
    private fun inside(r: Int, c: Int) = r in 0 until rows && c in 0 until cols && cell[r * cols + c]
    private fun topCell(c: Int): Int { for (r in 0 until rows) if (cell[idx(r, c)]) return idx(r, c); return -1 }
    private fun bottomCell(c: Int): Int { for (r in rows - 1 downTo 0) if (cell[idx(r, c)]) return idx(r, c); return -1 }
    private fun cellAbove(i: Int): Int {
        val c = colOf(i)
        for (r in rowOf(i) - 1 downTo 0) if (cell[idx(r, c)]) return idx(r, c)
        return -1
    }

    /** La couleur d'une case pour les alignements, ou -1. */
    private fun matchColor(i: Int): Int {
        val p = piece[i] ?: return -1
        return if (p.kind == Kind.THIMBLE || p.kind == Kind.RAINBOW) -1 else p.color
    }

    private fun fill() {
        for (r in 0 until rows) for (c in 0 until cols) {
            val i = idx(r, c)
            piece[i] = null
            if (!cell[i] || knot[i] > 0) continue
            var color: Int
            do {
                color = rnd.nextInt(level.colors)
                val h = c >= 2 && matchColor(i - 1) == color && matchColor(i - 2) == color
                val v = r >= 2 && matchColor(i - cols) == color && matchColor(i - 2 * cols) == color
            } while (h || v)
            piece[i] = Piece(nextId++, color, Kind.NORMAL)
        }
    }

    // ───────────────────────────── Coups ─────────────────────────────

    private fun movable(i: Int): Boolean {
        val p = piece[i] ?: return false
        return cell[i] && knot[i] == 0 && !p.pinned
    }

    private fun adjacent(a: Int, b: Int): Boolean {
        if (a !in 0 until size || b !in 0 until size) return false
        val dr = rowOf(a) - rowOf(b)
        val dc = colOf(a) - colOf(b)
        return (dr == 0 && (dc == 1 || dc == -1)) || (dc == 0 && (dr == 1 || dr == -1))
    }

    private fun special(p: Piece) = p.kind == Kind.LINE_H || p.kind == Kind.LINE_V || p.kind == Kind.BOMB

    /** Vrai si échanger [a] et [b] est un coup permis. Ne change rien. */
    fun canSwap(a: Int, b: Int): Boolean {
        if (!adjacent(a, b) || !movable(a) || !movable(b)) return false
        val pa = piece[a]!!
        val pb = piece[b]!!
        if (pa.kind == Kind.THIMBLE && pb.kind == Kind.THIMBLE) return false
        if (pa.kind == Kind.RAINBOW || pb.kind == Kind.RAINBOW) {
            val other = if (pa.kind == Kind.RAINBOW) pb else pa
            return other.kind != Kind.THIMBLE
        }
        if (special(pa) && special(pb)) return true
        piece[a] = pb; piece[b] = pa
        val ok = inRun(a) || inRun(b)
        piece[a] = pa; piece[b] = pb
        return ok
    }

    private fun inRun(i: Int): Boolean {
        val color = matchColor(i)
        if (color < 0) return false
        val r = rowOf(i)
        val c = colOf(i)
        var n = 1
        var k = c - 1
        while (k >= 0 && inside(r, k) && matchColor(idx(r, k)) == color) { n++; k-- }
        k = c + 1
        while (k < cols && inside(r, k) && matchColor(idx(r, k)) == color) { n++; k++ }
        if (n >= 3) return true
        n = 1
        k = r - 1
        while (k >= 0 && inside(k, c) && matchColor(idx(k, c)) == color) { n++; k-- }
        k = r + 1
        while (k < rows && inside(k, c) && matchColor(idx(k, c)) == color) { n++; k++ }
        return n >= 3
    }

    /** Joue le coup s'il est permis. L'écran enchaîne ensuite avec [advance]. */
    fun swap(a: Int, b: Int): Boolean {
        if (movesLeft <= 0 || bonus || !canSwap(a, b)) return false
        val pa = piece[a]!!
        val pb = piece[b]!!
        piece[a] = pb; piece[b] = pa
        movesLeft--
        cascade = 0
        swapA = a; swapB = b
        swapHorizontal = rowOf(a) == rowOf(b)
        if (pa.kind == Kind.RAINBOW || pb.kind == Kind.RAINBOW || (special(pa) && special(pb))) pendingCombo = intArrayOf(a, b)
        return true
    }

    /** Un coup possible (deux cases voisines), ou null si le plateau est bloqué. */
    fun findMove(): IntArray? {
        for (i in 0 until size) {
            if (!cell[i]) continue
            if (colOf(i) + 1 < cols && canSwap(i, i + 1)) return intArrayOf(i, i + 1)
            if (rowOf(i) + 1 < rows && canSwap(i, i + cols)) return intArrayOf(i, i + cols)
        }
        return null
    }

    fun hasMove() = findMove() != null

    /** Tous les coups permis, pour les robots de réglage. */
    fun allMoves(): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (i in 0 until size) {
            if (!cell[i]) continue
            if (colOf(i) + 1 < cols && canSwap(i, i + 1)) out += intArrayOf(i, i + 1)
            if (rowOf(i) + 1 < rows && canSwap(i, i + cols)) out += intArrayOf(i, i + cols)
        }
        return out
    }

    // ───────────────────────────── Résolution ─────────────────────────────

    private val gone = ArrayList<Gone>()
    private val born = ArrayList<Born>()
    private val fx = ArrayList<Fx>()
    private val pops = ArrayList<Pop>()
    private val knotHit = HashSet<Int>()
    private val toward = HashMap<Int, Int>()
    private var maxWave = 0

    /** Le prochain temps à montrer, ou null quand le plateau est au repos. */
    fun advance(): Step? {
        if (needFall) {
            needFall = false
            fall()?.let { return it }
        }
        clear()?.let { needFall = true; return it }
        if (bonus && bonusRounds < 40) {
            val specials = (0 until size).filter { i -> piece[i]?.let { it.kind != Kind.NORMAL && it.kind != Kind.THIMBLE } == true }
            if (specials.isNotEmpty()) {
                bonusRounds++
                begin()
                cascade++
                for ((n, i) in specials.withIndex()) hit(i, n / 3)
                needFall = true
                return finish()
            }
        }
        if (!bonus && movesLeft > 0 && !hasMove()) return shuffle()
        return null
    }

    private fun begin() {
        stepNo++
        gone.clear(); born.clear(); fx.clear(); pops.clear(); knotHit.clear(); toward.clear()
        maxWave = 0
    }

    private fun finish(): ClearStep {
        patchesLeft = patch.count { it > 0 }
        val out = gone.map { g -> toward[g.cell]?.let { Gone(g.piece, g.cell, g.wave, it) } ?: g }
        return ClearStep(out, born.toList(), fx.toList(), pops.toList(), cascade, maxWave + 1)
    }

    /** Chaque cascade vaut plus que la précédente, jusqu'à six fois la mise. */
    private val gain: Int get() = cascade.coerceIn(1, 6)

    private fun points(cell: Int, amount: Int, wave: Int) {
        val total = amount * gain
        score += total
        pops += Pop(cell, total, wave)
    }

    /** Une copie indépendante du plateau, pour qu'un robot essaie un coup sans le jouer. */
    fun fork(seed: Long): MercerieBoard {
        val b = MercerieBoard(level, seed)
        for (i in 0 until size) {
            b.cell[i] = cell[i]; b.knot[i] = knot[i]; b.patch[i] = patch[i]
            b.piece[i] = piece[i]?.let { p ->
                Piece(p.id, p.color, p.kind).also { it.pinned = p.pinned; it.armed = p.armed; it.radius = p.radius; it.armedAt = p.armedAt }
            }
        }
        b.nextId = nextId
        b.score = score; b.movesLeft = movesLeft
        collected.copyInto(b.collected)
        b.patchesLeft = patchesLeft; b.thimblesDone = thimblesDone
        b.thimblesToCome = thimblesToCome; b.thimblesWaiting = thimblesWaiting
        b.cascade = cascade; b.bonus = bonus; b.bonusRounds = bonusRounds
        b.stepNo = stepNo; b.needFall = needFall
        return b
    }

    internal fun hasMatches() = findGroups().isNotEmpty()
    internal fun fallNow() = fall()
    /** Cases vides qu'une pièce venue d'en haut aurait dû remplir : zéro quand le plateau est au repos. */
    internal fun starvedCells() = (0 until size).count { open(it) && fedFromAbove(it) }

    /** Couches de nœuds encore en place. */
    val knotsLeft: Int get() = knot.sum()

    /** Pour les robots : de combien de rangées les dés à coudre sont descendus, au total. */
    val thimbleDepth: Int get() = (0 until size).sumOf { if (piece[it]?.kind == Kind.THIMBLE) rowOf(it) else 0 }

    private fun clear(): ClearStep? {
        val combo = pendingCombo
        pendingCombo = null
        if (combo != null) {
            begin()
            cascade = 1
            combo(combo[0], combo[1])
            swapA = -1; swapB = -1
            return finish()
        }
        val groups = findGroups()
        val armed = (0 until size).filter { piece[it]?.armed == true }
        val baskets = (0 until cols).map { bottomCell(it) }.filter { it >= 0 && piece[it]?.kind == Kind.THIMBLE }
        if (groups.isEmpty() && armed.isEmpty() && baskets.isEmpty()) {
            swapA = -1; swapB = -1
            return null
        }
        begin()
        if (groups.isNotEmpty() || armed.isNotEmpty()) cascade++
        for (i in baskets) {
            val p = piece[i]!!
            piece[i] = null
            gone += Gone(p, i, 0, collected = true)
            fx += Fx(FxType.BASKET, i, 0)
            thimblesDone++
            if (thimblesToCome > 0) { thimblesToCome--; thimblesWaiting++ }
            points(i, 500, 0)
        }
        for (i in armed) hit(i, 0)
        for (g in groups) resolveGroup(g)
        swapA = -1; swapB = -1
        return finish()
    }

    private class Group(val cells: IntArray, val longest: Int, val crossing: Int, val horizontal: Boolean, val color: Int)

    private fun findGroups(): List<Group> {
        val inH = IntArray(size)
        val inV = IntArray(size)
        for (r in 0 until rows) {
            var c = 0
            while (c < cols) {
                val color = if (cell[idx(r, c)]) matchColor(idx(r, c)) else -1
                var e = c + 1
                if (color >= 0) while (e < cols && cell[idx(r, e)] && matchColor(idx(r, e)) == color) e++
                if (color >= 0 && e - c >= 3) for (k in c until e) inH[idx(r, k)] = e - c
                c = e
            }
        }
        for (c in 0 until cols) {
            var r = 0
            while (r < rows) {
                val color = if (cell[idx(r, c)]) matchColor(idx(r, c)) else -1
                var e = r + 1
                if (color >= 0) while (e < rows && cell[idx(e, c)] && matchColor(idx(e, c)) == color) e++
                if (color >= 0 && e - r >= 3) for (k in r until e) inV[idx(k, c)] = e - r
                r = e
            }
        }
        val seen = BooleanArray(size)
        val out = ArrayList<Group>()
        val stack = ArrayDeque<Int>()
        for (start in 0 until size) {
            if (seen[start] || (inH[start] == 0 && inV[start] == 0)) continue
            val color = matchColor(start)
            val cells = ArrayList<Int>()
            var longest = 0
            var crossing = -1
            var anyH = false
            stack.addLast(start); seen[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                cells += i
                longest = maxOf(longest, inH[i], inV[i])
                if (inH[i] > 0) anyH = true
                if (inH[i] > 0 && inV[i] > 0 && crossing < 0) crossing = i
                val r = rowOf(i)
                val c = colOf(i)
                // On ne suit que les voisins du même alignement, pour ne pas souder deux groupes qui se touchent.
                if (inH[i] > 0) {
                    if (c > 0) { val n = i - 1; if (!seen[n] && inH[n] > 0 && matchColor(n) == color) { seen[n] = true; stack.addLast(n) } }
                    if (c + 1 < cols) { val n = i + 1; if (!seen[n] && inH[n] > 0 && matchColor(n) == color) { seen[n] = true; stack.addLast(n) } }
                }
                if (inV[i] > 0) {
                    if (r > 0) { val n = i - cols; if (!seen[n] && inV[n] > 0 && matchColor(n) == color) { seen[n] = true; stack.addLast(n) } }
                    if (r + 1 < rows) { val n = i + cols; if (!seen[n] && inV[n] > 0 && matchColor(n) == color) { seen[n] = true; stack.addLast(n) } }
                }
            }
            out += Group(cells.toIntArray(), longest, crossing, anyH, color)
        }
        return out
    }

    private fun resolveGroup(g: Group) {
        val kind = when {
            bonus -> null
            g.longest >= 5 -> Kind.RAINBOW
            g.crossing >= 0 -> Kind.BOMB
            g.longest == 4 -> {
                val bySwap = g.cells.any { it == swapA || it == swapB }
                if (bySwap) (if (swapHorizontal) Kind.LINE_H else Kind.LINE_V)
                else if (g.horizontal) Kind.LINE_V else Kind.LINE_H
            }
            else -> null
        }
        // Le bonus naît sur la case jouée, sinon au croisement, sinon au milieu ; jamais sur une pièce épinglée.
        var at = -1
        if (kind != null) {
            val free = g.cells.filter { piece[it]?.pinned == false }
            at = free.firstOrNull { it == swapB } ?: free.firstOrNull { it == swapA }
                ?: free.firstOrNull { it == g.crossing } ?: free.getOrNull(free.size / 2) ?: -1
        }
        if (at >= 0) for (i in g.cells) if (i != at) toward[i] = at
        for (i in g.cells) {
            hit(i, 0, matched = true)
            for (n in neighbours(i)) if (knot[n] > 0) damageKnot(n, 0)
        }
        val base = when {
            g.longest >= 5 -> 200
            g.crossing >= 0 -> 200
            g.longest == 4 -> 120
            else -> 60
        }
        points(if (at >= 0) at else g.cells[g.cells.size / 2], base, 0)
        if (kind != null && at >= 0 && piece[at] == null) {
            val p = Piece(nextId++, if (kind == Kind.RAINBOW) -1 else g.color, kind)
            piece[at] = p
            born += Born(p, at, 1)
            maxWave = maxOf(maxWave, 1)
        }
    }

    private fun neighbours(i: Int): List<Int> {
        val r = rowOf(i)
        val c = colOf(i)
        val out = ArrayList<Int>(4)
        if (inside(r - 1, c)) out += i - cols
        if (inside(r + 1, c)) out += i + cols
        if (inside(r, c - 1)) out += i - 1
        if (inside(r, c + 1)) out += i + 1
        return out
    }

    private fun damageKnot(i: Int, wave: Int) {
        if (knot[i] <= 0 || !knotHit.add(i)) return
        knot[i]--
        fx += Fx(FxType.KNOT, i, wave, knot[i])
        score += 30 * gain
        maxWave = maxOf(maxWave, wave)
    }

    private fun hitPatch(i: Int, wave: Int) {
        if (patch[i] <= 0) return
        patch[i]--
        fx += Fx(FxType.PATCH, i, wave, patch[i])
        score += 100 * gain
    }

    /** Touche une case : alignement ([matched]) ou explosion. Déclenche les bonus touchés. */
    private fun hit(i: Int, wave: Int, matched: Boolean = false) {
        if (i !in 0 until size || !cell[i]) return
        maxWave = maxOf(maxWave, wave)
        if (knot[i] > 0) { damageKnot(i, wave); return }
        val p = piece[i]
        if (p == null) { hitPatch(i, wave); return }
        if (p.kind == Kind.THIMBLE) { hitPatch(i, wave); return }
        if (p.pinned) {
            p.pinned = false
            fx += Fx(FxType.UNPIN, i, wave)
            score += 30 * gain
            return
        }
        if (p.kind == Kind.BOMB && !p.armed) {
            // Première explosion : le pompon reste en place et repartira au temps suivant.
            p.armed = true
            p.armedAt = stepNo
            hitPatch(i, wave)
            blast(i, p.radius, wave)
            return
        }
        if (p.kind == Kind.BOMB && p.armedAt == stepNo) return
        piece[i] = null
        gone += Gone(p, i, wave)
        hitPatch(i, wave)
        if (p.color >= 0) collected[p.color]++
        if (!matched) score += 40 * gain
        when (p.kind) {
            Kind.LINE_H -> lineH(i, wave)
            Kind.LINE_V -> lineV(i, wave)
            Kind.BOMB -> blast(i, p.radius, wave)
            Kind.RAINBOW -> {
                val colors = (0 until size).mapNotNull { piece[it]?.takeIf { q -> q.kind != Kind.THIMBLE && q.color >= 0 }?.color }
                if (colors.isNotEmpty()) zapColor(i, colors[rnd.nextInt(colors.size)], wave, null)
            }
            else -> {}
        }
    }

    private fun lineH(i: Int, wave: Int) {
        fx += Fx(FxType.LINE_H, i, wave, color = piece[i]?.color ?: -1)
        val r = rowOf(i)
        val c = colOf(i)
        for (k in 0 until cols) if (k != c) hit(idx(r, k), wave + 1 + kotlin.math.abs(k - c) / 2)
    }

    private fun lineV(i: Int, wave: Int) {
        fx += Fx(FxType.LINE_V, i, wave)
        val r = rowOf(i)
        val c = colOf(i)
        for (k in 0 until rows) if (k != r) hit(idx(k, c), wave + 1 + kotlin.math.abs(k - r) / 2)
    }

    private fun blast(i: Int, radius: Int, wave: Int) {
        fx += Fx(FxType.BLAST, i, wave, radius)
        val r = rowOf(i)
        val c = colOf(i)
        for (dr in -radius..radius) for (dc in -radius..radius) {
            if (dr == 0 && dc == 0) continue
            if (r + dr in 0 until rows && c + dc in 0 until cols) hit(idx(r + dr, c + dc), wave + 1)
        }
    }

    /** Le bouton arc-en-ciel tire un fil vers chaque pièce de [color] ; [into] les change d'abord en bonus. */
    private fun zapColor(from: Int, color: Int, wave: Int, into: Kind?) {
        val targets = (0 until size).filter { piece[it]?.let { p -> p.color == color && p.kind != Kind.THIMBLE } == true }
        for ((n, t) in targets.withIndex()) {
            val w = wave + 1 + n / 4
            fx += Fx(FxType.ZAP, from, w, t, color)
            val p = piece[t]!!
            if (into != null && p.kind == Kind.NORMAL) {
                p.kind = if (into == Kind.BOMB) Kind.BOMB else if (rnd.nextBoolean()) Kind.LINE_H else Kind.LINE_V
                fx += Fx(FxType.TRANSFORM, t, w)
            }
        }
        val last = wave + 2 + targets.size / 4
        for ((n, t) in targets.withIndex()) hit(t, if (into != null) last + n / 3 else wave + 2 + n / 4)
    }

    private fun remove(i: Int, wave: Int) {
        val p = piece[i] ?: return
        piece[i] = null
        gone += Gone(p, i, wave)
        hitPatch(i, wave)
        if (p.color >= 0) collected[p.color]++
    }

    private fun combo(a: Int, b: Int) {
        val pa = piece[a]!!
        val pb = piece[b]!!
        val rainbowA = pa.kind == Kind.RAINBOW
        val rainbowB = pb.kind == Kind.RAINBOW
        when {
            rainbowA && rainbowB -> {
                remove(a, 0); remove(b, 0)
                points(b, 1000, 0)
                for (i in 0 until size) if (cell[i]) hit(i, 1 + (kotlin.math.abs(rowOf(i) - rowOf(b)) + kotlin.math.abs(colOf(i) - colOf(b))) / 2)
            }
            rainbowA || rainbowB -> {
                val at = if (rainbowA) a else b
                val other = if (rainbowA) pb else pa
                val otherCell = if (rainbowA) b else a
                remove(at, 0)
                points(at, 200, 0)
                when (other.kind) {
                    Kind.LINE_H, Kind.LINE_V -> zapColor(at, other.color, 0, Kind.LINE_H)
                    Kind.BOMB -> zapColor(at, other.color, 0, Kind.BOMB)
                    else -> zapColor(at, other.color, 0, null)
                }
                if (piece[otherCell] === other) hit(otherCell, 1)
            }
            pa.kind == Kind.BOMB && pb.kind == Kind.BOMB -> {
                // Un seul gros pompon reste, armé pour sa seconde explosion.
                remove(a, 0)
                pb.radius = 2
                pb.armed = true
                pb.armedAt = stepNo
                hitPatch(b, 0)
                points(b, 300, 0)
                blast(b, 2, 0)
            }
            pa.kind == Kind.BOMB || pb.kind == Kind.BOMB -> {
                remove(a, 0); remove(b, 0)
                points(b, 250, 0)
                val r = rowOf(b)
                val c = colOf(b)
                for (d in -1..1) {
                    if (r + d in 0 until rows) { fx += Fx(FxType.LINE_H, idx(r + d, c), 0); for (k in 0 until cols) hit(idx(r + d, k), 1 + kotlin.math.abs(k - c) / 2) }
                    if (c + d in 0 until cols) { fx += Fx(FxType.LINE_V, idx(r, c + d), 0); for (k in 0 until rows) hit(idx(k, c + d), 1 + kotlin.math.abs(k - r) / 2) }
                }
            }
            else -> {
                remove(a, 0); remove(b, 0)
                points(b, 200, 0)
                lineH(b, 0)
                lineV(b, 0)
            }
        }
    }

    // ───────────────────────────── Chute ─────────────────────────────

    private fun fedFromAbove(i: Int): Boolean {
        var at = i
        while (true) {
            val above = cellAbove(at)
            if (above < 0) return true
            if (knot[above] > 0) return false
            val p = piece[above]
            if (p != null) return !p.pinned
            at = above
        }
    }

    private fun open(i: Int) = cell[i] && knot[i] == 0 && piece[i] == null

    private fun fall(): FallStep? {
        val paths = LinkedHashMap<Piece, ArrayList<Hop>>()
        val spawned = HashSet<Piece>()
        val moved = HashSet<Piece>()
        var tick = 0
        while (tick < 200) {
            tick++
            moved.clear()
            var any = false
            for (r in rows - 1 downTo 0) for (c in 0 until cols) {
                val i = idx(r, c)
                if (!open(i)) continue
                val above = cellAbove(i)
                if (above < 0) {
                    val p = spawn(c)
                    piece[i] = p
                    spawned += p
                    moved += p
                    paths[p] = arrayListOf(Hop(tick - 1, i), Hop(tick, i))
                    any = true
                    continue
                }
                val pa = piece[above]
                if (pa != null && !pa.pinned && knot[above] == 0) {
                    if (moved.add(pa)) { move(pa, above, i, tick, paths); any = true }
                    continue
                }
                if (pa == null && knot[above] == 0 && fedFromAbove(above)) continue
                // Rien ne viendra d'en haut : une pièce glisse en biais depuis une colonne voisine.
                val first = if ((tick + c) % 2 == 0) -1 else 1
                for (side in intArrayOf(first, -first)) {
                    val nc = c + side
                    if (r == 0 || nc !in 0 until cols) continue
                    val n = idx(r - 1, nc)
                    if (!cell[n] || knot[n] > 0) continue
                    val pn = piece[n] ?: continue
                    if (pn.pinned || pn in moved) continue
                    val under = idx(r, nc)
                    if (open(under)) continue
                    moved += pn
                    move(pn, n, i, tick, paths)
                    any = true
                    break
                }
            }
            if (!any) { tick--; break }
        }
        if (paths.isEmpty()) return null
        calm(spawned)
        return FallStep(paths.map { (p, hops) -> Fall(p, hops, p in spawned) }, tick)
    }

    /**
     * Les pièces neuves n'arrivent jamais déjà alignées : sans cela, à quatre ou cinq couleurs, un
     * seul coup déclenchait des cascades sans fin. Seules les pièces déjà en jeu peuvent encore
     * s'aligner en retombant, ce qui donne une ou deux cascades, pas plus.
     */
    private fun calm(spawned: Set<Piece>) {
        if (spawned.isEmpty()) return
        for (i in 0 until size) {
            val p = piece[i] ?: continue
            if (p.kind != Kind.NORMAL || p !in spawned || !inRun(i)) continue
            val first = rnd.nextInt(level.colors)
            for (k in 0 until level.colors) {
                p.color = (first + k) % level.colors
                if (!inRun(i)) break
            }
        }
    }

    private fun move(p: Piece, from: Int, to: Int, tick: Int, paths: MutableMap<Piece, ArrayList<Hop>>) {
        piece[from] = null
        piece[to] = p
        val hops = paths.getOrPut(p) { arrayListOf(Hop(tick - 1, from)) }
        hops += Hop(tick, to)
    }

    private fun spawn(col: Int): Piece {
        if (thimblesWaiting > 0 && rnd.nextInt(3) == 0 && (0 until rows).none { piece[idx(it, col)]?.kind == Kind.THIMBLE }) {
            thimblesWaiting--
            return Piece(nextId++, -1, Kind.THIMBLE)
        }
        return Piece(nextId++, rnd.nextInt(level.colors), Kind.NORMAL)
    }

    // ───────────────────────────── Mélange, fin ─────────────────────────────

    private fun shuffle(): ShuffleStep? {
        val cells = (0 until size).filter { piece[it]?.let { p -> !p.pinned && p.kind != Kind.THIMBLE } == true }
        if (cells.size < 3) { movesLeft = 0; return null }
        val pieces = cells.map { piece[it]!! }.toMutableList()
        var ok = false
        repeat(200) {
            if (ok) return@repeat
            pieces.shuffle(rnd)
            for ((n, i) in cells.withIndex()) piece[i] = pieces[n]
            ok = findGroups().isEmpty() && hasMove()
        }
        if (!ok) {
            // Mélange impossible avec ces pièces : on en retire de nouvelles.
            repeat(50) {
                if (ok) return@repeat
                for (i in cells) piece[i]!!.let { p -> if (p.kind == Kind.NORMAL) p.color = rnd.nextInt(level.colors) }
                ok = findGroups().isEmpty() && hasMove()
            }
        }
        // Plateau sans issue (presque que des obstacles) : la partie s'arrête là.
        if (!ok) movesLeft = 0
        return ShuffleStep(cells.map { piece[it]!! to it })
    }

    val goalMet: Boolean
        get() {
            val g = level.goal
            if (g.patches && patchesLeft > 0) return false
            if (thimblesDone < g.thimbles) return false
            for (c in 0 until COLORS) if (collected[c] < g.collect[c]) return false
            if (g.onlyScore && score < level.stars[0]) return false
            return true
        }

    /** Les niveaux « au score » se jouent jusqu'au dernier coup ; les autres s'arrêtent dès l'objectif atteint. */
    val finished: Boolean get() = if (level.goal.onlyScore) movesLeft <= 0 else goalMet || movesLeft <= 0

    val stars: Int get() = if (!goalMet) 0 else maxOf(1, level.stars.count { score >= it })

    /**
     * Fin gagnée avec des coups en réserve : chacun change une pièce en bobine. L'écran montre ce
     * temps, puis reprend [advance] : les bonus partent tous.
     */
    fun startBonus(): ClearStep? {
        if (bonus) return null
        bonus = true
        begin()
        val normals = (0 until size).filter { piece[it]?.let { p -> p.kind == Kind.NORMAL && !p.pinned } == true }.shuffled(rnd)
        val count = minOf(movesLeft, normals.size)
        for (n in 0 until count) {
            val i = normals[n]
            piece[i]!!.kind = if (rnd.nextBoolean()) Kind.LINE_H else Kind.LINE_V
            fx += Fx(FxType.TRANSFORM, i, n)
            score += 250
            pops += Pop(i, 250, n)
            maxWave = maxOf(maxWave, n)
        }
        movesLeft = 0
        cascade = 0
        return if (count == 0) null else finish()
    }

    companion object {
        const val COLORS = 6
    }
}
