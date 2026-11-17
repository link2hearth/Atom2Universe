package com.Atom2Universe.app.games.mercerie

import kotlin.math.abs
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
    THIMBLE,
    /** Papillon (carré de quatre) : s'envole droit sur ce qui gêne le plus et le défait. */
    BUTTERFLY
}

class Piece(val id: Int, var color: Int, var kind: Kind) {
    /** Épinglée : ne bouge pas tant qu'un alignement ou une explosion n'a pas ôté l'épingle. */
    var pinned = false
    /** Pompon qui a déjà explosé une fois : il explosera encore au prochain temps. */
    var armed = false
    var radius = 1
    internal var armedAt = -1
    /** Minuteur : coups qu'il reste pour aligner la pièce ; 0 pour une pièce ordinaire. */
    var timer = 0
    /** Minuteur tombé pendant le coup en cours : il ne compte qu'à partir du coup suivant. */
    internal var fresh = false
}

/** Comment une pièce quitte le plateau. */
enum class Leave {
    /** Elle éclate. */
    POP,
    /** Un dé à coudre arrivé tout en bas : il part dans le panier. */
    BASKET,
    /** Un papillon s'envole : l'écran le montre en vol jusqu'à sa cible. */
    FLY,
    /** Un mouton de poussière l'avale en s'étendant. */
    EATEN
}

/** Une pièce qui disparaît. [toward] : la case du bonus qu'elle rejoint, sinon -1. */
class Gone(val piece: Piece, val cell: Int, val wave: Int, val toward: Int = -1, val leave: Leave = Leave.POP)

/** Une pièce bonus qui naît d'un alignement. */
class Born(val piece: Piece, val cell: Int, val wave: Int)

enum class FxType {
    LINE_H, LINE_V, BLAST, ZAP, UNPIN, KNOT, PATCH, TRANSFORM, BASKET,
    /** Un papillon part de [Fx.cell] vers [Fx.arg] ; il arrive [MercerieBoard.FLIGHT] temps plus tard. */
    FLY,
    /** Un mouton de poussière est chassé. */
    DUST,
    /** Un mouton gagne la case [Fx.cell], venu de [Fx.arg]. */
    SPREAD,
    /** Un minuteur baisse : [Fx.arg] est ce qu'il lui reste. */
    TICK,
    /** Un minuteur arrive à zéro : la partie est perdue. */
    RING
}

/**
 * Un effet à montrer. [arg] : la case d'arrivée (ZAP, FLY), la case de départ (SPREAD), le rayon
 * (BLAST) ou ce qu'il reste (KNOT, PATCH, TICK). [extra] : ce que porte un papillon (FLY), ou -1.
 */
class Fx(val type: FxType, val cell: Int, val wave: Int, val arg: Int = 0, val color: Int = -1, val extra: Int = -1)

class Pop(val cell: Int, val points: Int, val wave: Int, val color: Int = -1)

/** Deux bonus échangés ensemble : l'écran nomme la combinaison. */
enum class Combo { CROSS, LINE_BOMB, BIG_BOMB, RAINBOW_COLOR, RAINBOW_LINE, RAINBOW_BOMB, RAINBOW_BUTTERFLY, DOUBLE_RAINBOW, BUTTERFLIES, BUTTERFLY_LINE, BUTTERFLY_BOMB }

/** Un pas du trajet d'une pièce qui tombe : à l'instant [tick], elle est sur [cell]. */
class Hop(val tick: Int, val cell: Int)

/** [path] commence par la case de départ ; si [spawned], la pièce arrive d'au-dessus de sa première case. */
class Fall(val piece: Piece, val path: List<Hop>, val spawned: Boolean)

sealed class Step
class ClearStep(
    val gone: List<Gone>, val born: List<Born>, val fx: List<Fx>, val pops: List<Pop>,
    val cascade: Int, val waves: Int,
    val combo: Combo? = null,
    /** Début de la couture finale : chaque coup restant part du compteur vers une pièce. */
    val shots: Boolean = false,
    /** Fin d'un coup : les minuteurs baissent, un mouton s'étend. */
    val endOfMove: Boolean = false
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
    /** Nœud de laine : 0 à 3 couches, bouche la case. Un mouton de poussière est un nœud d'une couche. */
    val knot = IntArray(size)
    /** Mouton de poussière : il gagne une case voisine à chaque coup où l'on n'en chasse aucun. */
    val dust = BooleanArray(size)
    /** Écusson cousu sous la pièce : 0 à 2 couches. */
    val patch = IntArray(size)
    /** Téléporteurs : la case d'où vient la pièce qui tombe sur celle-ci, ou -1. */
    val feed = IntArray(size) { -1 }
    /** Entrée d'un téléporteur : ce qui tombe de cette case passe par le téléporteur. */
    val entrance = BooleanArray(size)

    var score = 0; private set
    var movesLeft = level.moves; private set
    val collected = IntArray(COLORS)
    var patchesLeft = 0; private set
    var thimblesDone = 0; private set
    private var thimblesToCome = 0
    private var thimblesWaiting = 0
    private var timersToCome = 0
    var cascade = 0; private set
    /** Fin de niveau gagnée : les coups restants deviennent des bobines, puis tout part. */
    var bonus = false; private set
    private var bonusRounds = 0
    /** Un minuteur est arrivé à zéro : partie perdue. */
    var timedOut = false; private set
    /** Le bonus du défi, gagné en atteignant l'objectif en autant de coups que le robot : 0 sinon. */
    var challenge = 0; private set

    /** Coups joués jusqu'ici. */
    val movesUsed: Int get() = level.moves - movesLeft

    /** Le défi est tenu : objectif atteint sans dépasser les coups du robot. Vrai aussi pendant la couture finale. */
    val beatsPar: Boolean get() = challenge > 0 || (!bonus && goalMet && level.par > 0 && movesUsed <= level.par)

    private var stepNo = 0
    private var needFall = false
    private var pendingCombo: IntArray? = null
    private var swapA = -1
    private var swapB = -1
    private var swapHorizontal = true
    /** Un coup est en cours : à la fin, les minuteurs baissent et un mouton peut s'étendre. */
    private var moveOpen = false
    private var dustCleared = false
    /** Les carrés de quatre s'alignent (papillons) ; pas en mode facile. */
    private val squares = !level.easy

    init {
        for (r in 0 until rows) for (c in 0 until cols) {
            val i = r * cols + c
            when (level.map[r][c]) {
                ' ', '#' -> {}
                '.', 't' -> cell[i] = true
                'p' -> { cell[i] = true; patch[i] = 1 }
                'P' -> { cell[i] = true; patch[i] = 2 }
                'k' -> { cell[i] = true; knot[i] = 1 }
                'K' -> { cell[i] = true; knot[i] = 2 }
                'M' -> { cell[i] = true; knot[i] = 3 }
                'q' -> { cell[i] = true; knot[i] = 1; patch[i] = 1 }
                'Q' -> { cell[i] = true; knot[i] = 2; patch[i] = 2 }
                'd' -> { cell[i] = true; knot[i] = 1; dust[i] = true }
                'e' -> { cell[i] = true; knot[i] = 1; dust[i] = true; patch[i] = 1 }
                'x', 'y' -> cell[i] = true
                else -> error("case inconnue '${level.map[r][c]}'")
            }
        }
        for (p in level.portals) {
            val a = idx(p.r1, p.c1)
            val b = idx(p.r2, p.c2)
            require(cell[a] && cell[b]) { "téléporteur hors du plateau" }
            entrance[a] = true
            feed[b] = a
        }
        var tries = 0
        do { fill() } while (!hasMove() && ++tries < 50)
        var placed = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            val ch = level.map[r][c]
            val p = piece[r * cols + c] ?: continue
            if (ch == 'x' || ch == 'y') {
                p.pinned = true
                if (ch == 'y') patch[r * cols + c] = 1
            }
            if (ch == 't' && level.timer > 0) { p.timer = level.timer; placed++ }
        }
        timersToCome = (level.timers - placed).coerceAtLeast(0)
        patchesLeft = patch.count { it > 0 }
        val first = minOf(level.thimblesAtOnce, level.goal.thimbles)
        val tops = (0 until cols).map { topCell(it) }.filter { it >= 0 && knot[it] == 0 && piece[it]?.pinned == false && piece[it]?.timer == 0 }.shuffled(rnd)
        for (i in tops.take(first)) piece[i] = Piece(nextId++, -1, Kind.THIMBLE)
        thimblesToCome = level.goal.thimbles - minOf(first, tops.size)
    }

    fun idx(r: Int, c: Int) = r * cols + c
    fun rowOf(i: Int) = i / cols
    fun colOf(i: Int) = i % cols
    private fun inside(r: Int, c: Int) = r in 0 until rows && c in 0 until cols && cell[r * cols + c]
    private fun topCell(c: Int): Int { for (r in 0 until rows) if (cell[idx(r, c)]) return idx(r, c); return -1 }
    private fun bottomCell(c: Int): Int { for (r in rows - 1 downTo 0) if (cell[idx(r, c)]) return idx(r, c); return -1 }

    /**
     * La case d'où descend ce qui tombe sur [i] : la sortie d'un téléporteur est nourrie par son
     * entrée ; sinon la case au-dessus, même par-dessus un trou. Sous une entrée de téléporteur, rien
     * ne descend : les pièces y arrivent d'en haut, comme sur la première rangée.
     */
    private fun cellAbove(i: Int): Int {
        if (feed[i] >= 0) return feed[i]
        val c = colOf(i)
        for (r in rowOf(i) - 1 downTo 0) {
            val j = idx(r, c)
            if (cell[j]) return if (entrance[j]) -1 else j
        }
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
                val sq = squares && r >= 1 && c >= 1 && matchColor(i - 1) == color && matchColor(i - cols) == color && matchColor(i - cols - 1) == color
            } while (h || v || sq)
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

    private fun special(p: Piece) = p.kind == Kind.LINE_H || p.kind == Kind.LINE_V || p.kind == Kind.BOMB || p.kind == Kind.BUTTERFLY

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
        val ok = lined(a) || lined(b)
        piece[a] = pa; piece[b] = pb
        return ok
    }

    /** Vrai si [i] s'aligne : trois de suite, ou un carré de quatre hors du mode facile. */
    private fun lined(i: Int) = inRun(i) || (squares && inSquare(i))

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

    /** Vrai si [i] fait partie d'un carré de quatre pièces de la même couleur. */
    private fun inSquare(i: Int): Boolean {
        val color = matchColor(i)
        if (color < 0) return false
        val r = rowOf(i)
        val c = colOf(i)
        for (dr in -1..0) for (dc in -1..0) {
            val r0 = r + dr
            val c0 = c + dc
            if (r0 < 0 || c0 < 0 || r0 + 1 >= rows || c0 + 1 >= cols) continue
            val a = idx(r0, c0)
            if (matchColor(a) == color && matchColor(a + 1) == color && matchColor(a + cols) == color && matchColor(a + cols + 1) == color) return true
        }
        return false
    }

    /** Joue le coup s'il est permis. L'écran enchaîne ensuite avec [advance]. */
    fun swap(a: Int, b: Int): Boolean {
        if (movesLeft <= 0 || bonus || timedOut || !canSwap(a, b)) return false
        val pa = piece[a]!!
        val pb = piece[b]!!
        piece[a] = pb; piece[b] = pa
        movesLeft--
        cascade = 0
        swapA = a; swapB = b
        swapHorizontal = rowOf(a) == rowOf(b)
        moveOpen = true
        dustCleared = false
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

    /**
     * Le coup que montre l'aide, pour apprendre les bonus par l'exemple : d'abord deux bonus
     * échangés ensemble (l'arc-en-ciel en tête), puis un coup qui fabrique un bonus (arc-en-ciel,
     * pompon, ciseaux, papillon), puis un coup qui déclenche un bonus déjà là, et seulement à
     * défaut un simple alignement de trois. Null si le plateau est bloqué.
     */
    fun bestMove(): IntArray? {
        var best: IntArray? = null
        var bestValue = -1
        for (m in allMoves()) {
            val v = moveValue(m[0], m[1])
            if (v > bestValue) { bestValue = v; best = m }
        }
        return best
    }

    /**
     * Le bonus que fabrique l'échange de [a] et [b] : arc-en-ciel, pompon, papillon, ou LINE_H pour
     * des ciseaux, quel que soit leur sens. Null pour un simple alignement. Ne change rien.
     */
    fun bonusMadeBy(a: Int, b: Int): Kind? {
        val pa = piece[a] ?: return null
        val pb = piece[b] ?: return null
        piece[a] = pb; piece[b] = pa
        var best: Kind? = null
        var rank = 0
        for (g in findGroups()) {
            val (kind, r) = when {
                g.longest >= 5 -> Kind.RAINBOW to 4
                g.crossing >= 0 -> Kind.BOMB to 3
                g.longest == 4 -> Kind.LINE_H to 2
                g.square -> Kind.BUTTERFLY to 1
                else -> null to 0
            }
            if (r > rank) { rank = r; best = kind }
        }
        piece[a] = pa; piece[b] = pb
        return best
    }

    private fun moveValue(a: Int, b: Int): Int {
        val pa = piece[a]!!
        val pb = piece[b]!!
        val rainbowA = pa.kind == Kind.RAINBOW
        val rainbowB = pb.kind == Kind.RAINBOW
        if (rainbowA && rainbowB) return 100
        if (rainbowA || rainbowB) return if (special(if (rainbowA) pb else pa)) 90 else 60
        if (special(pa) && special(pb)) return 80
        piece[a] = pb; piece[b] = pa
        var v = 0
        // Au repos, le plateau n'a aucun alignement : ceux qu'on trouve viennent de cet échange.
        for (g in findGroups()) {
            val made = when {
                g.longest >= 5 -> 50
                g.crossing >= 0 -> 45
                g.longest == 4 -> 40
                g.square -> 35
                else -> 0
            }
            val fires = if (g.cells.any { piece[it]?.let { p -> special(p) } == true }) 30 else 0
            v = maxOf(v, made, fires, 1)
        }
        piece[a] = pa; piece[b] = pb
        return v
    }

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
    /** Cases déjà visées par un papillon pendant ce temps : chacun file vers une cible différente. */
    private val targeted = HashSet<Int>()
    private var maxWave = 0
    private var combo: Combo? = null

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
        if (moveOpen) {
            moveOpen = false
            endOfMove()?.let { return it }
        }
        if (!bonus && !timedOut && movesLeft > 0 && !hasMove()) return shuffle()
        return null
    }

    private fun begin() {
        stepNo++
        gone.clear(); born.clear(); fx.clear(); pops.clear(); knotHit.clear(); toward.clear(); targeted.clear()
        maxWave = 0
        combo = null
    }

    private fun finish(shots: Boolean = false, endOfMove: Boolean = false): ClearStep {
        patchesLeft = patch.count { it > 0 }
        val out = gone.map { g -> toward[g.cell]?.let { Gone(g.piece, g.cell, g.wave, it, g.leave) } ?: g }
        return ClearStep(out, born.toList(), fx.toList(), pops.toList(), cascade, maxWave + 1, combo, shots, endOfMove)
    }

    /** Chaque cascade vaut plus que la précédente, jusqu'à six fois la mise. */
    private val gain: Int get() = cascade.coerceIn(1, 6)

    private fun points(cell: Int, amount: Int, wave: Int, color: Int = -1) {
        val total = amount * gain
        score += total
        pops += Pop(cell, total, wave, color)
    }

    /** Une copie indépendante du plateau, pour qu'un robot essaie un coup sans le jouer. */
    fun fork(seed: Long): MercerieBoard {
        val b = MercerieBoard(level, seed)
        for (i in 0 until size) {
            b.cell[i] = cell[i]; b.knot[i] = knot[i]; b.patch[i] = patch[i]; b.dust[i] = dust[i]
            b.piece[i] = piece[i]?.let { p ->
                Piece(p.id, p.color, p.kind).also {
                    it.pinned = p.pinned; it.armed = p.armed; it.radius = p.radius; it.armedAt = p.armedAt
                    it.timer = p.timer; it.fresh = p.fresh
                }
            }
        }
        b.nextId = nextId
        b.score = score; b.movesLeft = movesLeft
        collected.copyInto(b.collected)
        b.patchesLeft = patchesLeft; b.thimblesDone = thimblesDone
        b.thimblesToCome = thimblesToCome; b.thimblesWaiting = thimblesWaiting
        b.timersToCome = timersToCome; b.timedOut = timedOut; b.challenge = challenge
        b.cascade = cascade; b.bonus = bonus; b.bonusRounds = bonusRounds
        b.stepNo = stepNo; b.needFall = needFall
        b.moveOpen = moveOpen; b.dustCleared = dustCleared
        return b
    }

    internal fun hasMatches() = findGroups().isNotEmpty()
    internal fun fallNow() = fall()
    /** Cases vides qu'une pièce venue d'en haut aurait dû remplir : zéro quand le plateau est au repos. */
    internal fun starvedCells() = (0 until size).count { open(it) && fedFromAbove(it) }

    /** Couches de nœuds encore en place (moutons compris). */
    val knotsLeft: Int get() = knot.sum()

    /** Moutons de poussière encore sur le plateau. */
    val dustLeft: Int get() = dust.count { it }

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
        val baskets = (0 until cols).map { bottomCell(it) }.filter { it >= 0 && !entrance[it] && piece[it]?.kind == Kind.THIMBLE }
        if (groups.isEmpty() && armed.isEmpty() && baskets.isEmpty()) {
            swapA = -1; swapB = -1
            return null
        }
        begin()
        if (groups.isNotEmpty() || armed.isNotEmpty()) cascade++
        for (i in baskets) {
            val p = piece[i]!!
            piece[i] = null
            gone += Gone(p, i, 0, leave = Leave.BASKET)
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

    private class Group(val cells: IntArray, val longest: Int, val crossing: Int, val horizontal: Boolean, val color: Int, val square: Boolean)

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
        // Les carrés de quatre, repérés par leur coin en haut à gauche.
        val square = BooleanArray(size)
        val inSq = BooleanArray(size)
        if (squares) for (r in 0 until rows - 1) for (c in 0 until cols - 1) {
            val a = idx(r, c)
            val color = matchColor(a)
            if (color < 0) continue
            if (matchColor(a + 1) == color && matchColor(a + cols) == color && matchColor(a + cols + 1) == color) {
                square[a] = true
                inSq[a] = true; inSq[a + 1] = true; inSq[a + cols] = true; inSq[a + cols + 1] = true
            }
        }
        val seen = BooleanArray(size)
        val out = ArrayList<Group>()
        val stack = ArrayDeque<Int>()
        fun push(n: Int) { if (!seen[n]) { seen[n] = true; stack.addLast(n) } }
        for (start in 0 until size) {
            if (seen[start] || (inH[start] == 0 && inV[start] == 0 && !inSq[start])) continue
            val color = matchColor(start)
            val cells = ArrayList<Int>()
            var longest = 0
            var crossing = -1
            var anyH = false
            var anySq = false
            stack.addLast(start); seen[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                cells += i
                longest = maxOf(longest, inH[i], inV[i])
                if (inH[i] > 0) anyH = true
                if (inSq[i]) anySq = true
                if (inH[i] > 0 && inV[i] > 0 && crossing < 0) crossing = i
                val r = rowOf(i)
                val c = colOf(i)
                // On ne suit que les voisins du même alignement, pour ne pas souder deux groupes qui se touchent.
                if (inH[i] > 0) {
                    if (c > 0) { val n = i - 1; if (inH[n] > 0 && matchColor(n) == color) push(n) }
                    if (c + 1 < cols) { val n = i + 1; if (inH[n] > 0 && matchColor(n) == color) push(n) }
                }
                if (inV[i] > 0) {
                    if (r > 0) { val n = i - cols; if (inV[n] > 0 && matchColor(n) == color) push(n) }
                    if (r + 1 < rows) { val n = i + cols; if (inV[n] > 0 && matchColor(n) == color) push(n) }
                }
                if (inSq[i]) for (dr in -1..0) for (dc in -1..0) {
                    val r0 = r + dr
                    val c0 = c + dc
                    if (r0 < 0 || c0 < 0) continue
                    val a = idx(r0, c0)
                    if (!square[a]) continue
                    push(a); push(a + 1); push(a + cols); push(a + cols + 1)
                }
            }
            out += Group(cells.toIntArray(), longest, crossing, anyH, color, anySq)
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
            g.square -> Kind.BUTTERFLY
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
            g.square -> 120
            else -> 60
        }
        points(if (at >= 0) at else g.cells[g.cells.size / 2], base, 0, g.color)
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
        if (dust[i] && knot[i] == 0) {
            dust[i] = false
            dustCleared = true
            fx += Fx(FxType.DUST, i, wave)
            score += 60 * gain
        } else {
            fx += Fx(FxType.KNOT, i, wave, knot[i])
            score += 30 * gain
        }
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
        gone += Gone(p, i, wave, leave = if (p.kind == Kind.BUTTERFLY) Leave.FLY else Leave.POP)
        hitPatch(i, wave)
        if (p.color >= 0) collected[p.color]++
        if (!matched) score += 40 * gain
        if (p.timer > 0) points(i, 150, wave, p.color)
        when (p.kind) {
            Kind.LINE_H -> lineH(i, wave, p.color)
            Kind.LINE_V -> lineV(i, wave, p.color)
            Kind.BOMB -> blast(i, p.radius, wave)
            Kind.RAINBOW -> {
                val colors = (0 until size).mapNotNull { piece[it]?.takeIf { q -> q.kind != Kind.THIMBLE && q.color >= 0 }?.color }
                if (colors.isNotEmpty()) zapColor(i, colors[rnd.nextInt(colors.size)], wave, null)
            }
            Kind.BUTTERFLY -> launch(i, wave, p.color, null)
            else -> {}
        }
    }

    private fun lineH(i: Int, wave: Int, color: Int = -1) {
        fx += Fx(FxType.LINE_H, i, wave, color = color)
        val r = rowOf(i)
        val c = colOf(i)
        for (k in 0 until cols) if (k != c) hit(idx(r, k), wave + 1 + abs(k - c) / 2)
    }

    private fun lineV(i: Int, wave: Int, color: Int = -1) {
        fx += Fx(FxType.LINE_V, i, wave, color = color)
        val r = rowOf(i)
        val c = colOf(i)
        for (k in 0 until rows) if (k != r) hit(idx(k, c), wave + 1 + abs(k - r) / 2)
    }

    private fun blast(i: Int, radius: Int, wave: Int) {
        fx += Fx(FxType.BLAST, i, wave, radius, piece[i]?.color ?: -1)
        val r = rowOf(i)
        val c = colOf(i)
        for (dr in -radius..radius) for (dc in -radius..radius) {
            if (dr == 0 && dc == 0) continue
            if (r + dr in 0 until rows && c + dc in 0 until cols) hit(idx(r + dr, c + dc), wave + 1)
        }
    }

    /**
     * La cible d'un papillon : ce qui gêne le plus. D'abord un minuteur pressé, puis ce que demande
     * l'objectif (écusson, mouton, pièce sous un dé à coudre, couleur à récolter), puis les nœuds et
     * les épingles ; à défaut, une pièce au hasard.
     */
    private fun pickTarget(from: Int): Int {
        val g = level.goal
        var best = -1
        var bestScore = 0.0
        for (i in 0 until size) {
            if (!cell[i] || i == from || i in targeted) continue
            val p = piece[i]
            var s = when {
                dust[i] -> if (g.dust) 70.0 else 52.0
                knot[i] > 0 -> 30.0 + (if (g.patches && patch[i] > 0) 26.0 else 0.0)
                p == null -> if (g.patches && patch[i] > 0) 50.0 else 0.0
                p.timer > 0 -> 96.0 - p.timer * 5.0
                p.kind == Kind.THIMBLE -> 0.0
                g.patches && patch[i] > 0 -> 55.0 + patch[i] * 5.0
                p.pinned -> 36.0
                p.kind == Kind.NORMAL && p.color >= 0 && collected[p.color] < g.collect[p.color] -> 24.0
                p.kind == Kind.NORMAL -> 2.0
                else -> 0.0
            }
            if (g.thimbles > 0 && p != null && p.kind != Kind.THIMBLE) {
                val above = cellAbove(i)
                if (above >= 0 && piece[above]?.kind == Kind.THIMBLE) s = maxOf(s, 46.0)
            }
            if (s <= 0.0) continue
            s += rnd.nextDouble() * 4.0
            if (s > bestScore) { bestScore = s; best = i }
        }
        return best
    }

    /**
     * Un papillon s'envole de [from] : il touche sa cible et les quatre cases autour. S'il porte un
     * bonus ([carry]), le bonus part de la cible.
     */
    private fun launch(from: Int, wave: Int, color: Int, carry: Kind?) {
        val target = pickTarget(from)
        if (target < 0) return
        targeted += target
        val land = wave + FLIGHT
        fx += Fx(FxType.FLY, from, wave, target, color, carry?.ordinal ?: -1)
        maxWave = maxOf(maxWave, land)
        when (carry) {
            Kind.LINE_H -> lineH(target, land, color)
            Kind.LINE_V -> lineV(target, land, color)
            Kind.BOMB -> blast(target, 1, land)
            else -> {}
        }
        hit(target, land)
        for (n in neighbours(target)) hit(n, land + 1)
    }

    /** Le bouton arc-en-ciel tire un fil vers chaque pièce de [color] ; [into] les change d'abord en bonus. */
    private fun zapColor(from: Int, color: Int, wave: Int, into: Kind?) {
        val targets = (0 until size).filter { piece[it]?.let { p -> p.color == color && p.kind != Kind.THIMBLE } == true }
        for ((n, t) in targets.withIndex()) {
            val w = wave + 1 + n / 4
            fx += Fx(FxType.ZAP, from, w, t, color)
            val p = piece[t]!!
            if (into != null && p.kind == Kind.NORMAL) {
                p.kind = when (into) {
                    Kind.BOMB -> Kind.BOMB
                    Kind.BUTTERFLY -> Kind.BUTTERFLY
                    else -> if (rnd.nextBoolean()) Kind.LINE_H else Kind.LINE_V
                }
                fx += Fx(FxType.TRANSFORM, t, w)
            }
        }
        val last = wave + 2 + targets.size / 4
        for ((n, t) in targets.withIndex()) hit(t, if (into != null) last + n / 3 else wave + 2 + n / 4)
    }

    private fun remove(i: Int, wave: Int, leave: Leave = Leave.POP) {
        val p = piece[i] ?: return
        piece[i] = null
        gone += Gone(p, i, wave, leave = leave)
        hitPatch(i, wave)
        if (p.color >= 0) collected[p.color]++
    }

    private fun combo(a: Int, b: Int) {
        val pa = piece[a]!!
        val pb = piece[b]!!
        val rainbowA = pa.kind == Kind.RAINBOW
        val rainbowB = pb.kind == Kind.RAINBOW
        val wingsA = pa.kind == Kind.BUTTERFLY
        val wingsB = pb.kind == Kind.BUTTERFLY
        when {
            rainbowA && rainbowB -> {
                combo = Combo.DOUBLE_RAINBOW
                remove(a, 0); remove(b, 0)
                points(b, 1000, 0)
                for (i in 0 until size) if (cell[i]) hit(i, 1 + (abs(rowOf(i) - rowOf(b)) + abs(colOf(i) - colOf(b))) / 2)
            }
            rainbowA || rainbowB -> {
                val at = if (rainbowA) a else b
                val other = if (rainbowA) pb else pa
                val otherCell = if (rainbowA) b else a
                remove(at, 0)
                points(at, 200, 0)
                when (other.kind) {
                    Kind.LINE_H, Kind.LINE_V -> { combo = Combo.RAINBOW_LINE; zapColor(at, other.color, 0, Kind.LINE_H) }
                    Kind.BOMB -> { combo = Combo.RAINBOW_BOMB; zapColor(at, other.color, 0, Kind.BOMB) }
                    Kind.BUTTERFLY -> { combo = Combo.RAINBOW_BUTTERFLY; zapColor(at, other.color, 0, Kind.BUTTERFLY) }
                    else -> { combo = Combo.RAINBOW_COLOR; zapColor(at, other.color, 0, null) }
                }
                if (piece[otherCell] === other) hit(otherCell, 1)
            }
            wingsA && wingsB -> {
                // Deux papillons : trois s'envolent.
                combo = Combo.BUTTERFLIES
                remove(a, 0, Leave.FLY); remove(b, 0, Leave.FLY)
                points(b, 300, 0, pb.color)
                launch(b, 0, pb.color, null)
                launch(b, 0, pa.color, null)
                launch(b, 1, pb.color, null)
            }
            wingsA || wingsB -> {
                // Le papillon emporte l'autre bonus et le déclenche sur sa cible.
                val wings = if (wingsA) pa else pb
                val other = if (wingsA) pb else pa
                combo = if (other.kind == Kind.BOMB) Combo.BUTTERFLY_BOMB else Combo.BUTTERFLY_LINE
                remove(a, 0, if (wingsA) Leave.FLY else Leave.POP)
                remove(b, 0, if (wingsB) Leave.FLY else Leave.POP)
                points(b, 250, 0, wings.color)
                launch(b, 0, wings.color, other.kind)
            }
            pa.kind == Kind.BOMB && pb.kind == Kind.BOMB -> {
                // Un seul gros pompon reste, armé pour sa seconde explosion.
                combo = Combo.BIG_BOMB
                remove(a, 0)
                pb.radius = 2
                pb.armed = true
                pb.armedAt = stepNo
                hitPatch(b, 0)
                points(b, 300, 0)
                blast(b, 2, 0)
            }
            pa.kind == Kind.BOMB || pb.kind == Kind.BOMB -> {
                combo = Combo.LINE_BOMB
                remove(a, 0); remove(b, 0)
                points(b, 250, 0)
                val r = rowOf(b)
                val c = colOf(b)
                for (d in -1..1) {
                    if (r + d in 0 until rows) { fx += Fx(FxType.LINE_H, idx(r + d, c), 0); for (k in 0 until cols) hit(idx(r + d, k), 1 + abs(k - c) / 2) }
                    if (c + d in 0 until cols) { fx += Fx(FxType.LINE_V, idx(r, c + d), 0); for (k in 0 until rows) hit(idx(k, c + d), 1 + abs(k - r) / 2) }
                }
            }
            else -> {
                combo = Combo.CROSS
                remove(a, 0); remove(b, 0)
                points(b, 200, 0)
                lineH(b, 0, pb.color)
                lineV(b, 0, pb.color)
            }
        }
    }

    // ───────────────────────────── Fin d'un coup ─────────────────────────────

    /**
     * Le coup est retombé : chaque minuteur baisse d'un cran (celui qui atteint zéro fait perdre la
     * partie), puis, si aucun mouton n'a été chassé pendant ce coup, un mouton gagne une case voisine
     * en avalant la pièce qui s'y trouvait. En mode facile, les moutons restent où ils sont.
     */
    private fun endOfMove(): ClearStep? {
        if (bonus || finished) return null
        begin()
        cascade = 0
        var any = false
        for (i in 0 until size) {
            val p = piece[i] ?: continue
            if (p.timer <= 0) continue
            if (p.fresh) { p.fresh = false; continue }
            p.timer--
            any = true
            fx += Fx(FxType.TICK, i, 0, p.timer)
            if (p.timer == 0) {
                timedOut = true
                fx += Fx(FxType.RING, i, 1)
                maxWave = maxOf(maxWave, 1)
            }
        }
        if (!level.easy && !dustCleared && !timedOut && spread()) any = true
        dustCleared = false
        return if (any) finish(endOfMove = true) else null
    }

    private fun spread(): Boolean {
        val options = ArrayList<IntArray>()
        for (s in 0 until size) if (dust[s]) for (n in neighbours(s)) {
            val p = piece[n] ?: continue
            if (knot[n] == 0 && p.kind == Kind.NORMAL && !p.pinned && p.timer == 0) options += intArrayOf(s, n)
        }
        if (options.isEmpty()) return false
        val (from, to) = options[rnd.nextInt(options.size)].let { it[0] to it[1] }
        val p = piece[to]!!
        piece[to] = null
        gone += Gone(p, to, 0, leave = Leave.EATEN)
        knot[to] = 1
        dust[to] = true
        fx += Fx(FxType.SPREAD, to, 0, from)
        return true
    }

    // ───────────────────────────── Chute ─────────────────────────────

    private fun fedFromAbove(i: Int): Boolean {
        var at = i
        repeat(size) {
            val above = cellAbove(at)
            if (above < 0) return true
            if (knot[above] > 0) return false
            val p = piece[above]
            if (p != null) return !p.pinned
            at = above
        }
        return false
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
                    if (!cell[n] || knot[n] > 0 || entrance[n]) continue
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
            if (p.kind != Kind.NORMAL || p !in spawned || !lined(i)) continue
            val first = rnd.nextInt(level.colors)
            for (k in 0 until level.colors) {
                p.color = (first + k) % level.colors
                if (!lined(i)) break
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
        val p = Piece(nextId++, rnd.nextInt(level.colors), Kind.NORMAL)
        if (timersToCome > 0 && !bonus && level.timer > 0 && rnd.nextInt(4) == 0 && piece.count { (it?.timer ?: 0) > 0 } < level.timersAtOnce) {
            timersToCome--
            p.timer = level.timer
            p.fresh = true
        }
        return p
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
            if (timedOut) return false
            val g = level.goal
            if (g.patches && patchesLeft > 0) return false
            if (g.dust && dust.any { it }) return false
            if (thimblesDone < g.thimbles) return false
            for (c in 0 until COLORS) if (collected[c] < g.collect[c]) return false
            if (g.onlyScore && score < level.stars[0]) return false
            return true
        }

    /** Les niveaux « au score » se jouent jusqu'au dernier coup ; les autres s'arrêtent dès l'objectif atteint. */
    val finished: Boolean get() = timedOut || if (level.goal.onlyScore) movesLeft <= 0 else goalMet || movesLeft <= 0

    val stars: Int get() = if (!goalMet) 0 else maxOf(1, level.stars.count { score >= it })

    /**
     * Fin gagnée avec des coups en réserve : chacun change une pièce en bobine. L'écran montre ce
     * temps, puis reprend [advance] : les bonus partent tous. Si le défi est tenu, le score reçoit
     * d'abord de quoi atteindre la troisième étoile.
     */
    fun startBonus(): ClearStep? {
        if (bonus) return null
        val par = beatsPar
        bonus = true
        moveOpen = false
        begin()
        if (par) {
            challenge = level.stars[2]
            score += challenge
            val middle = (0 until size).minByOrNull { i -> if (cell[i]) abs(rowOf(i) * 2 - rows + 1) + abs(colOf(i) * 2 - cols + 1) else Int.MAX_VALUE } ?: 0
            pops += Pop(middle, challenge, 0)
        }
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
        return if (count == 0 && challenge == 0) null else finish(shots = true)
    }

    companion object {
        const val COLORS = 6
        /** Temps de vol d'un papillon jusqu'à sa cible. */
        const val FLIGHT = 5
    }
}
