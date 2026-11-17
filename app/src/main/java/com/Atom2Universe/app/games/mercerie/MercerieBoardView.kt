package com.Atom2Universe.app.games.mercerie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le plateau à l'écran : il montre les temps du moteur l'un après l'autre (échange, disparitions,
 * chute) et ne rend la main au joueur qu'une fois tout retombé.
 *
 * Tout ce qui félicite le joueur passe par ici : confettis de feutrine à chaque pièce, ciseaux qui
 * coupent la ligne, pompon qui souffle sa laine, papillons qui volent, et les mots en lettres
 * cousues, de plus en plus grands et dorés à mesure que le coup rapporte.
 */
@SuppressLint("ViewConstructor")
class MercerieBoardView(
    context: Context, val board: MercerieBoard, private val sound: MercerieSound?, slow: Float = 1f
) : View(context), Coverable {

    private var realBase = SystemClock.uptimeMillis()
    private var clockBase = realBase

    /**
     * L'horloge du plateau : celle du système, ralentie de [slow] pour le tutoriel. Elle compte
     * depuis le dernier changement de vitesse, en Double : un grand nombre de millisecondes en
     * Float n'a plus qu'une précision de plusieurs dizaines de millisecondes, et le ralenti
     * avançait par à-coups.
     */
    private fun clock(): Long = clockBase + ((SystemClock.uptimeMillis() - realBase) / slow.toDouble()).toLong()

    /** Ralenti du tutoriel : 2 = tout se déroule deux fois moins vite. Change sans à-coup. */
    var slow: Float = slow
        set(value) {
            if (value == field) return
            clockBase = clock()
            realBase = SystemClock.uptimeMillis()
            field = value
        }

    interface Listener {
        /** Score, coups ou objectifs ont bougé. */
        fun onChanged()
        /** Objectif atteint : les coups restants vont partir en bobines. */
        fun onBonus()
        fun onFinished(won: Boolean)
        /** Un mot de félicitation s'affiche, du palier 1 (joli coup) au palier 5 (coup de légende). */
        fun onCheer(tier: Int) {}
        /** Une pièce utile à l'objectif [key] ([KEY_PATCH]…, ou sa couleur) quitte le plateau en ([x], [y]) de la fenêtre. */
        fun onCollect(key: Int, x: Float, y: Float, size: Float) {}
        /** Couture finale : un coup restant part du compteur dans [delay] ms et se pose en ([x], [y]) [flight] ms plus tard. */
        fun onShot(x: Float, y: Float, delay: Long, flight: Long) {}
        /** Un coup est allé jusqu'au bout : le plateau est au repos. */
        fun onSettled() {}
        /** Le joueur échange [a] et [b] : appelé juste avant que le coup ne se joue. */
        fun onSwap(a: Int, b: Int) {}
    }

    var listener: Listener? = null

    /** Montrer un coup possible (deux pièces qui battent) quand le joueur ne joue pas. */
    var hints = true
        set(value) { field = value; armHint(); invalidate() }

    /** Attente avant que le coup conseillé ne batte, en millisecondes. */
    var hintDelay = 6000L

    /** Le coup à conseiller, s'il faut autre chose que le meilleur coup ([MercerieBoard.bestMove]) ; null : le meilleur. */
    var hintPick: (() -> IntArray?)? = null

    /** Tutoriel : seul le coup [guide] est permis (aucun s'il est null). */
    var guided = false
        set(value) { field = value; armHint(); invalidate() }

    /** Tutoriel : le coup à jouer, deux cases ; ses pièces battent et une main de feutrine montre le geste. */
    var guide: IntArray? = null
        set(value) { field = value; armHint(); invalidate() }

    /**
     * Une fiche (pause, aide, fin de partie) est posée sur le plateau : ce qui bat au repos attend
     * qu'elle se referme. Un coup en train de se dérouler, lui, va jusqu'au bout.
     */
    override var covered = false
        set(value) { if (field != value) { field = value; invalidate() } }
    private val idleTick = Runnable { invalidate() }

    private class Sprite(val piece: Piece) {
        var x = 0f
        var y = 0f
        var scale = 1f
        var alpha = 1f
        var squash = 0f
        var kind = piece.kind
        var color = piece.color
        var pinned = piece.pinned
        var timer = piece.timer
        var timerPop = 0L
        var fromX = 0f
        var fromY = 0f
    }

    private enum class Look { CUT_H, CUT_V, PUFF, THREAD, WINGS, POOF, BORN, GROW, RING, TWINKLE, SPIN }

    private class Swap(val a: Int, val b: Int, val valid: Boolean, val start: Long)
    private class Effect(val look: Look, val cell: Int, val arg: Int, val color: Int, val extra: Int, val start: Long, val life: Long) {
        var lastTrail = 0L
    }
    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float, val size: Float, val color: Int,
        var angle: Float, val spin: Float, val shape: Path, val born: Long, val life: Int, val gravity: Float
    )
    private class Floater(val x: Float, val y: Float, val text: String, val start: Long, val color: Int, val big: Boolean)
    private class Cheer(val word: CheerWord, val start: Long, val life: Long)

    private val sprites = LinkedHashMap<Int, Sprite>()
    private val shownKnot = IntArray(board.size)
    private val shownDust = BooleanArray(board.size)
    private val shownPatch = IntArray(board.size)
    private val effects = ArrayList<Effect>()
    private val growing = HashMap<Int, Effect>()
    private val particles = ArrayList<Particle>()
    private val floaters = ArrayList<Floater>()
    private val fired = HashSet<Any>()
    private val rnd = Random(System.nanoTime())

    /** Les mots, du palier 1 au palier 5, et le nom de chaque combinaison. */
    private val words: Array<Array<String>> = arrayOf(
        resources.getStringArray(R.array.mercerie_cheers_1), resources.getStringArray(R.array.mercerie_cheers_2),
        resources.getStringArray(R.array.mercerie_cheers_3), resources.getStringArray(R.array.mercerie_cheers_4),
        resources.getStringArray(R.array.mercerie_cheers_5)
    )
    private val comboWords: Array<String> = resources.getStringArray(R.array.mercerie_combos)
    private val wordCache = object : LinkedHashMap<String, CheerWord>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CheerWord>?): Boolean {
            if (size > 10) { eldest?.value?.let { w -> if (cheer?.word !== w) w.recycle() }; return true }
            return false
        }
    }
    private var lastWord = ""

    private var cell = 0f
    private var originX = 0f
    private var originY = 0f
    private var art: MercerieSprites? = null
    private var backdrop: Bitmap? = null
    /** Les téléporteurs, posés par-dessus les pièces qui y plongent. */
    private var front: Bitmap? = null

    private var step: Step? = null
    private var stepStart = 0L
    private var waveMs = WAVE_MS
    private var swap: Swap? = null
    private var ended = false
    private var resolving = false
    private var selected = -1
    private var downCell = -1
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private var hint: IntArray? = null
    private var cheer: Cheer? = null
    private var shakeStart = 0L
    private var shakeLife = 0L
    private var shakeAmp = 0f
    private var flashStart = 0L
    private var flashLife = 0L
    private var flashColor = 0
    private var dimStart = 0L
    private var lastFrame = 0L
    private var lastPop = 0L
    private var nextTwinkle = 0L

    /** Ce que rapporte le coup en cours : sert à choisir le palier du mot. */
    private var moveGone = 0
    private var moveSpecials = 0
    private var moveTier = 0
    private var warned = false
    /** Pièces de chaque couleur déjà envoyées vers leur compteur. */
    private val sent = IntArray(MercerieBoard.COLORS)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = MercerieUi.bold; textAlign = Paint.Align.CENTER }
    private val dst = RectF()
    private val beatRect = RectF()
    private val path = Path()
    private val loc = IntArray(2)
    private val ray: Path = Path().apply {
        val half = (PI / RAYS).toFloat() * .55f
        moveTo(0f, 0f)
        lineTo(cos(-half), sin(-half))
        lineTo(cos(half), sin(half))
        close()
    }

    private val showHint = Runnable {
        if (!busy) { hint = hintPick?.invoke() ?: board.bestMove(); invalidate() }
    }

    private val busy: Boolean get() = swap != null || step != null || resolving || ended

    init { sync() }

    // ───────────────────────────── Mise en place ─────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = prepare(w, h)

    /** Le mode jour / nuit a changé en pleine partie : pièces, cases et tissu sont refaits. */
    fun restyle() {
        prepare(width, height)
        invalidate()
    }

    /**
     * Tutoriel : les pièces de [cells] viennent d'être changées en bonus sur le plateau ; elles
     * prennent leur nouvelle allure dans une gerbe d'étincelles.
     */
    fun reveal(cells: List<Int>) {
        val now = clock()
        for (c in cells) {
            val p = board.piece[c] ?: continue
            sprites[p.id]?.let { it.kind = p.kind; it.color = p.color }
            effects += Effect(Look.BORN, c, 0, p.color, 0, now, 560L)
            sparkles(c, 9)
        }
        sound?.special()
        armHint()
        invalidate()
    }

    private fun prepare(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val margin = MercerieUi.dp(context, 10f)
        // Une rangée de marge en haut : les pièces neuves arrivent de là.
        // Le tissu du plateau dépasse des cases : on lui garde sa place dans la largeur.
        cell = min((w - 2 * margin) / (board.cols + .36f), (h - 2 * margin) / (board.rows + .6f))
        originX = (w - cell * board.cols) / 2
        originY = (h - cell * board.rows) / 2 + cell * .2f
        art = MercerieSprites(context.assets, cell.toInt().coerceAtLeast(8))
        wordCache.values.forEach { if (cheer?.word !== it) it.recycle() }
        wordCache.clear()
        cheer = null
        backdrop?.recycle()
        backdrop = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            val pad = cell * .16f
            val unit = MercerieUi.unit(context)
            val left = originX - pad
            val top = originY - pad
            val right = originX + cell * board.cols + pad
            val bottom = originY + cell * board.rows + pad
            Felt.panel(c, left, top, right, bottom, cell * .34f, Felt.KRAFT, unit, threadColor = Felt.CREAM)
            linen(context)?.let { cloth ->
                // Le lin du plateau, un peu foncé pour que les cases claires s'en détachent.
                val weave = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                weave.shader = BitmapShader(cloth, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
                    val k = cell * 4f / cloth.width
                    setLocalMatrix(Matrix().apply { setScale(k, k) })
                }
                if (!Felt.dark) weave.colorFilter = LightingColorFilter(0xFFE4D0BC.toInt(), 0)
                c.drawRoundRect(left, top, right, bottom, cell * .34f, cell * .34f, weave)
                val inset = unit * .16f
                path.rewind()
                path.addRoundRect(left + inset, top + inset, right - inset, bottom - inset, cell * .28f, cell * .28f, Path.Direction.CW)
                Felt.stitches(c, path, Felt.CREAM, unit)
            }
            for (r in 0 until board.rows) for (col in 0 until board.cols) if (board.cell[board.idx(r, col)]) {
                c.drawBitmap(art!!.tile[(r + col) % 2], originX + col * cell, originY + r * cell, paint)
            }
        }
        front?.recycle()
        front = null
        val exits = (0 until board.size).filter { board.feed[it] >= 0 }
        if (exits.isNotEmpty()) front = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
            val c = Canvas(b)
            for ((k, exit) in exits.withIndex()) {
                val color = Felt.lighter(Felt.COLORS[(k * 2) % Felt.COLORS.size], .15f)
                val entry = board.feed[exit]
                buttonhole(c, cx(board.colOf(entry).toFloat()), originY + (board.rowOf(entry) + 1) * cell, color)
                buttonhole(c, cx(board.colOf(exit).toFloat()), originY + board.rowOf(exit) * cell, color)
            }
        }
    }

    /** Un téléporteur : une boutonnière cousue sur le bord de la case, d'où les pièces plongent ou ressortent. */
    private fun buttonhole(c: Canvas, x: Float, y: Float, color: Int) {
        val w = cell * .34f
        val h = cell * .085f
        val rim = cell * .075f
        path.rewind()
        path.addRoundRect(x - w - rim, y - h - rim, x + w + rim, y + h + rim, h + rim, h + rim, Path.Direction.CW)
        Felt.shape(c, path, color, cell * .7f, stitch = false, plush = false)
        fill.color = 0xFF2A1E24.toInt()
        c.drawRoundRect(x - w, y - h, x + w, y + h, h, h, fill)
        path.rewind()
        path.addRoundRect(x - w - rim * .5f, y - h - rim * .5f, x + w + rim * .5f, y + h + rim * .5f, h + rim * .5f, h + rim * .5f, Path.Direction.CW)
        Felt.stitches(c, path, Felt.CREAM, cell * .45f, .05f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        armHint()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(showHint)
        removeCallbacks(idleTick)
        super.onDetachedFromWindow()
    }

    private fun armHint() {
        removeCallbacks(showHint)
        hint = null
        // Tutoriel : le coup prévu bat tout de suite, sans attendre.
        if (guided) { if (!ended) hint = guide; return }
        if (!ended && hints && isAttachedToWindow) postDelayed(showHint, hintDelay)
    }

    /** Remet chaque image sur la case de sa pièce : l'état de repos entre deux temps. */
    private fun sync() {
        val alive = HashSet<Int>()
        for (i in 0 until board.size) {
            val p = board.piece[i] ?: continue
            alive += p.id
            val s = sprites.getOrPut(p.id) { Sprite(p) }
            s.x = board.colOf(i).toFloat()
            s.y = board.rowOf(i).toFloat()
            s.scale = 1f; s.alpha = 1f; s.squash = 0f
            s.kind = p.kind; s.color = p.color; s.pinned = p.pinned; s.timer = p.timer
        }
        sprites.keys.retainAll(alive)
        board.knot.copyInto(shownKnot)
        board.dust.copyInto(shownDust)
        board.patch.copyInto(shownPatch)
    }

    /** Fait apparaître un mot cousu au milieu du plateau ; [tier] 0 avertit, 1 à 5 félicitent de plus en plus fort. */
    fun showCheer(word: String, tier: Int) {
        if (cell <= 0f) return
        val key = "$tier|$word"
        val size = min(cell * (if (tier >= 5) 1.35f else 1.2f), board.cols * cell * .94f / (word.length * .6f).coerceAtLeast(1f))
        val baked = wordCache[key]?.takeIf { abs(it.size - size) < 1f } ?: CheerWord.bake(word, tier, size).also { wordCache[key] = it }
        val life = when (tier) { 0 -> 1500L; 1, 2 -> 1200L; 3, 4 -> 1450L; else -> 1800L }
        val now = clock()
        cheer = Cheer(baked, now, life)
        lastWord = word
        if (tier >= 2) {
            val x = width / 2f
            val y = cheerBaseline() - size * .35f
            confetti(x, y, 8 + tier * 7, -1, cell * (3f + tier * .6f), big = true)
        }
        if (tier >= 5) flash(0xFFFFF3C4.toInt(), 260L)
        if (tier > 0) { sound?.cheer(tier); listener?.onCheer(tier) } else sound?.warn()
        invalidate()
    }

    private fun pickWord(tier: Int): String {
        val list = words[(tier - 1).coerceIn(0, words.size - 1)]
        val choices = list.filter { it != lastWord }.ifEmpty { list.toList() }
        return choices[rnd.nextInt(choices.size)]
    }

    private fun cheerBaseline() = originY + cell * board.rows * .47f

    private fun cx(col: Float) = originX + (col + .5f) * cell
    private fun cy(row: Float) = originY + (row + .5f) * cell

    private fun cellAt(x: Float, y: Float): Int {
        if (cell <= 0f) return -1
        val c = ((x - originX) / cell).toInt()
        val r = ((y - originY) / cell).toInt()
        if (x < originX || y < originY || c >= board.cols || r >= board.rows) return -1
        val i = board.idx(r, c)
        return if (board.cell[i]) i else -1
    }

    /** Le centre d'une case dans la fenêtre, pour la couche de vol de l'écran. */
    private fun windowPoint(cellIndex: Int): FloatArray {
        getLocationInWindow(loc)
        return floatArrayOf(loc[0] + cx(board.colOf(cellIndex).toFloat()), loc[1] + cy(board.rowOf(cellIndex).toFloat()))
    }

    private fun collect(key: Int, cellIndex: Int) {
        val l = listener ?: return
        val p = windowPoint(cellIndex)
        l.onCollect(key, p[0], p[1], cell * .8f)
    }

    // ───────────────────────────── Doigt ─────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (busy) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                armHint()
                sound?.wake()
                downCell = cellAt(event.x, event.y)
                downX = event.x; downY = event.y
                dragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (downCell < 0 || dragged) return true
                val dx = event.x - downX
                val dy = event.y - downY
                if (maxOf(abs(dx), abs(dy)) < cell * .32f) return true
                dragged = true
                val r = board.rowOf(downCell) + (if (abs(dy) > abs(dx)) (if (dy > 0) 1 else -1) else 0)
                val c = board.colOf(downCell) + (if (abs(dx) >= abs(dy)) (if (dx > 0) 1 else -1) else 0)
                selected = -1
                if (r in 0 until board.rows && c in 0 until board.cols) trySwap(downCell, board.idx(r, c))
            }
            MotionEvent.ACTION_UP -> {
                if (dragged || downCell < 0) return true
                // Deux touches de suite sur des cases voisines valent un glissement.
                val previous = selected
                if (previous >= 0 && previous != downCell && neighbours(previous, downCell)) {
                    selected = -1
                    trySwap(previous, downCell)
                } else {
                    selected = if (previous == downCell || board.piece[downCell] == null) -1 else downCell
                    if (selected >= 0) sound?.tick()
                }
                invalidate()
            }
        }
        return true
    }

    private fun neighbours(a: Int, b: Int) =
        abs(board.rowOf(a) - board.rowOf(b)) + abs(board.colOf(a) - board.colOf(b)) == 1

    private fun trySwap(a: Int, b: Int) {
        if (b < 0 || !board.cell[b]) return
        // Tutoriel : un autre coup que celui prévu ne fait rien.
        if (guided) {
            val g = guide ?: return
            if (!((a == g[0] && b == g[1]) || (a == g[1] && b == g[0]))) return
        }
        val pa = board.piece[a]
        val pb = board.piece[b]
        if (pa == null || pb == null || pa.pinned || pb.pinned) return
        val valid = board.canSwap(a, b)
        swap = Swap(a, b, valid, clock())
        if (valid) sound?.swap() else sound?.bad()
        hint = null
        removeCallbacks(showHint)
        invalidate()
    }

    // ───────────────────────────── Déroulé ─────────────────────────────

    private fun next() {
        resolving = true
        val s = board.advance()
        if (s == null) { settled(); return }
        begin(s)
    }

    private fun begin(s: Step) {
        step = s
        stepStart = clock()
        waveMs = WAVE_MS
        fired.clear()
        when (s) {
            is ClearStep -> {
                for (b in s.born) sprites[b.piece.id] = Sprite(b.piece).also {
                    it.x = board.colOf(b.cell).toFloat(); it.y = board.rowOf(b.cell).toFloat(); it.scale = 0f; it.alpha = 0f
                }
                if (s.shots) {
                    // Couture finale : les coups partent du compteur et se posent l'un après l'autre.
                    waveMs = BONUS_WAVE_MS
                    stepStart += SHOT_MS
                    for (f in s.fx) if (f.type == FxType.TRANSFORM) {
                        val p = windowPoint(f.cell)
                        listener?.onShot(p[0], p[1], f.wave * BONUS_WAVE_MS, SHOT_MS)
                    }
                }
                if (!board.bonus && !s.endOfMove && !s.shots) judge(s)
            }
            is FallStep -> for (f in s.falls) if (f.spawned) sprites[f.piece.id] = Sprite(f.piece).also {
                val at = f.path[0].cell
                it.x = board.colOf(at).toFloat(); it.y = board.rowOf(at) - 1f; it.alpha = 0f
            }
            is ShuffleStep -> for ((p, _) in s.moved) sprites[p.id]?.let { it.fromX = it.x; it.fromY = it.y }
        }
        invalidate()
    }

    /**
     * Le palier du coup grandit avec ce qu'il emporte : pièces, bonus déclenchés, cascades. Un
     * nouveau mot paraît chaque fois qu'un palier est franchi ; une combinaison a son propre nom.
     */
    private fun judge(s: ClearStep) {
        moveGone += s.gone.count { it.leave == Leave.POP || it.leave == Leave.FLY }
        moveSpecials += s.gone.count { it.piece.kind != Kind.NORMAL && it.piece.kind != Kind.THIMBLE } + s.born.size
        val heat = moveGone + 3 * moveSpecials + 4 * (s.cascade - 1).coerceAtLeast(0)
        val tier = HEAT.count { heat >= it }
        val combo = s.combo
        if (combo != null && combo != Combo.RAINBOW_COLOR) {
            val strong = when (combo) {
                Combo.RAINBOW_LINE, Combo.RAINBOW_BOMB, Combo.RAINBOW_BUTTERFLY, Combo.DOUBLE_RAINBOW -> 5
                Combo.BIG_BOMB -> 4
                else -> 3
            }
            moveTier = maxOf(strong, tier)
            dimStart = clock()
            flash(Felt.CREAM, 180L)
            sound?.combo()
            showCheer(comboWords[combo.ordinal], moveTier)
            return
        }
        if (tier > moveTier) {
            moveTier = tier
            showCheer(pickWord(tier), tier)
        }
    }

    private fun settled() {
        resolving = false
        step = null
        sync()
        listener?.onChanged()
        if (board.finished && !ended) {
            if (board.goalMet && !board.bonus) {
                listener?.onBonus()
                resolving = true
                val s = board.startBonus()
                if (s != null) begin(s) else next()
                return
            }
            ended = true
            removeCallbacks(showHint)
            listener?.onFinished(board.goalMet)
            return
        }
        // Le compte à rebours de Candy : on prévient quand il ne reste que cinq coups.
        if (!warned && !board.bonus && board.movesLeft == 5 && board.level.moves >= 10) {
            warned = true
            showCheer(resources.getString(R.string.mercerie_moves_left), 0)
        }
        listener?.onSettled()
        armHint()
        invalidate()
    }

    private fun update(now: Long) {
        swap?.let { s ->
            val u = ((now - s.start) / SWAP_MS).coerceIn(0f, 1f)
            val e = ease(if (s.valid) u else if (u < .5f) u * 2 else (1 - u) * 2) * (if (s.valid) 1f else .45f)
            val pa = board.piece[s.a]
            val pb = board.piece[s.b]
            val ax = board.colOf(s.a).toFloat(); val ay = board.rowOf(s.a).toFloat()
            val bx = board.colOf(s.b).toFloat(); val by = board.rowOf(s.b).toFloat()
            pa?.let { sprites[it.id] }?.let { it.x = ax + (bx - ax) * e; it.y = ay + (by - ay) * e }
            pb?.let { sprites[it.id] }?.let { it.x = bx + (ax - bx) * e; it.y = by + (ay - by) * e }
            if (u >= 1f) {
                swap = null
                if (s.valid) listener?.onSwap(s.a, s.b)
                if (s.valid && board.swap(s.a, s.b)) {
                    moveGone = 0; moveSpecials = 0; moveTier = 0
                    sync()
                    listener?.onChanged()
                    next()
                } else {
                    sync()
                    armHint()
                }
            }
        }
        when (val s = step) {
            is ClearStep -> clearStep(s, now - stepStart, now)
            is FallStep -> fallStep(s, now - stepStart)
            is ShuffleStep -> {
                val u = ((now - stepStart) / 450f).coerceIn(0f, 1f)
                val e = ease(u)
                for ((p, at) in s.moved) sprites[p.id]?.let {
                    it.x = it.fromX + (board.colOf(at) - it.fromX) * e
                    it.y = it.fromY + (board.rowOf(at) - it.fromY) * e
                    it.scale = 1f - sin(u * PI.toFloat()) * .25f
                }
                if (u >= 1f) endStep()
            }
            null -> {}
        }
        val dt = if (lastFrame == 0L) 0f else (now - lastFrame).coerceAtMost(50L) / 1000f
        lastFrame = now
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (now - p.born > p.life) { it.remove(); continue }
            p.vy += p.gravity * dt
            p.vx *= 1f - min(1f, dt * 1.2f)
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
        }
        trails(now)
        val ei = effects.iterator()
        while (ei.hasNext()) {
            val e = ei.next()
            if (now - e.start > e.life) {
                ei.remove()
                if (e.look == Look.GROW) growing.remove(e.cell)
            }
        }
        if (floaters.isNotEmpty()) floaters.removeAll { now - it.start > 850 }
        cheer?.let { if (now - it.start > it.life) cheer = null }
        // Au repos, une pièce scintille de temps en temps.
        if (!busy && now >= nextTwinkle) {
            if (nextTwinkle != 0L) {
                val resting = sprites.values.filter { it.kind == Kind.NORMAL && it.alpha == 1f }
                if (resting.isNotEmpty()) {
                    val s = resting[rnd.nextInt(resting.size)]
                    effects += Effect(Look.TWINKLE, board.idx(s.y.toInt(), s.x.toInt()), 0, 0, 0, now, 560L)
                }
            }
            nextTwinkle = now + 1500L + rnd.nextInt(2200)
        }
    }

    /** Les ciseaux sèment des bouts de fil, les papillons des étincelles. */
    private fun trails(now: Long) {
        for (e in effects) {
            if (now - e.lastTrail < 26) continue
            when (e.look) {
                Look.CUT_H, Look.CUT_V -> {
                    val travel = ((now - e.start) / CUT_MS).coerceIn(0f, 1f)
                    if (travel >= 1f) continue
                    e.lastTrail = now
                    val horizontal = e.look == Look.CUT_H
                    cutEnds(e, travel)
                    val x = cx(board.colOf(e.cell).toFloat())
                    val y = cy(board.rowOf(e.cell).toFloat())
                    val color = if (e.color in 0..5) Felt.COLORS[e.color] else Felt.PINK
                    for (tip in 0..1) {
                        val end = ends[tip]
                        if (horizontal) speck(end, y, color) else speck(x, end, color)
                    }
                }
                Look.WINGS -> {
                    val u = ((now - e.start).toFloat() / e.life).coerceIn(0f, 1f)
                    if (u >= 1f) continue
                    e.lastTrail = now
                    val p = wingsAt(e, u)
                    particles += Particle(p[0], p[1], (rnd.nextFloat() - .5f) * cell, (rnd.nextFloat() - .5f) * cell,
                        cell * (.06f + rnd.nextFloat() * .05f), if (rnd.nextBoolean()) Felt.CREAM else Felt.GOLD,
                        rnd.nextFloat() * 90f, 200f, Confetti.sparkle, now, 360, 0f)
                }
                else -> {}
            }
        }
    }

    private fun speck(x: Float, y: Float, color: Int) {
        if (particles.size > MAX_PARTICLES) return
        val now = clock()
        particles += Particle(x, y, (rnd.nextFloat() - .5f) * cell * 3f, -cell * (1f + rnd.nextFloat() * 2f),
            cell * (.05f + rnd.nextFloat() * .05f), if (rnd.nextBoolean()) Felt.CREAM else color,
            rnd.nextFloat() * 360f, (rnd.nextFloat() - .5f) * 700f, Confetti.shapes[3], now, 420 + rnd.nextInt(200), cell * 12f)
    }

    private fun endStep() {
        step = null
        sync()
        listener?.onChanged()
        next()
    }

    private fun clearStep(s: ClearStep, t: Long, now: Long) {
        for (g in s.gone) {
            val t0 = g.wave * waveMs
            if (t < t0) continue
            val sp = sprites[g.piece.id] ?: continue
            if (fired.add(g)) leaving(g, sp, s, now)
            val u = ((t - t0) / POP_MS).coerceIn(0f, 1f)
            when (g.leave) {
                Leave.BASKET -> {
                    sp.y = board.rowOf(g.cell) + u * 1.1f
                    sp.alpha = 1f - u
                }
                Leave.FLY -> { sp.alpha = 0f; sprites.remove(g.piece.id); continue }
                Leave.EATEN -> {
                    sp.scale = 1f - u * .9f
                    sp.alpha = 1f - u
                }
                Leave.POP -> {
                    sp.scale = if (u < .25f) 1f + u else 1.25f * (1f - (u - .25f) / .75f)
                    sp.alpha = 1f - u * u
                    if (g.toward >= 0) {
                        val e = u * u
                        sp.x = board.colOf(g.cell) + (board.colOf(g.toward) - board.colOf(g.cell)) * e
                        sp.y = board.rowOf(g.cell) + (board.rowOf(g.toward) - board.rowOf(g.cell)) * e
                    }
                }
            }
            if (u >= 1f) sprites.remove(g.piece.id)
        }
        var bornDone = true
        for (b in s.born) {
            val sp = sprites[b.piece.id] ?: continue
            val t0 = b.wave * waveMs + 70
            if (t < t0) { bornDone = false; continue }
            if (fired.add(b)) {
                sound?.special()
                effects += Effect(Look.BORN, b.cell, 0, b.piece.color, 0, now, 560L)
                sparkles(b.cell, 7)
            }
            val u = ((t - t0) / 280f).coerceIn(0f, 1f)
            sp.alpha = 1f
            sp.scale = overshoot(u)
            if (u < 1f) bornDone = false
        }
        for (f in s.fx) if (t >= f.wave * waveMs && fired.add(f)) applyFx(f, now)
        for (p in s.pops) if (t >= p.wave * waveMs && fired.add(p)) {
            val color = if (p.color in 0..5) Felt.COLORS[p.color] else Felt.PINK
            floaters += Floater(cx(board.colOf(p.cell).toFloat()), cy(board.rowOf(p.cell).toFloat()), MercerieUi.number(p.points), now, color, p.points >= 500)
            listener?.onChanged()
        }
        val tail = if (s.endOfMove && s.fx.any { it.type == FxType.SPREAD }) 380 else 60
        if (bornDone && t >= s.waves * waveMs + POP_MS + tail) endStep()
    }

    /** Une pièce s'en va : confettis à sa couleur, et vol vers son compteur si l'objectif la demande. */
    private fun leaving(g: Gone, sp: Sprite, s: ClearStep, now: Long) {
        val goal = board.level.goal
        when (g.leave) {
            Leave.BASKET -> { sound?.basket(); sparkles(g.cell, 10); collect(KEY_THIMBLE, g.cell) }
            Leave.EATEN -> confetti(cx(board.colOf(g.cell).toFloat()), cy(board.rowOf(g.cell).toFloat()), 6, DUST_GREY, cell * 2f)
            Leave.POP, Leave.FLY -> {
                if (g.leave == Leave.POP) {
                    val color = if (sp.color >= 0) Felt.COLORS[sp.color.coerceIn(0, 5)] else Felt.CREAM
                    confetti(cx(board.colOf(g.cell).toFloat()), cy(board.rowOf(g.cell).toFloat()), 6, color, cell * 3.2f)
                    effects += Effect(Look.POOF, g.cell, 0, sp.color, 0, now, 280L)
                    if (sp.kind == Kind.RAINBOW) effects += Effect(Look.SPIN, g.cell, 0, -1, 0, now, 520L)
                    if (now - lastPop > 45) { lastPop = now; sound?.pop(s.cascade) }
                }
                val c = g.piece.color
                if (c in 0 until MercerieBoard.COLORS && goal.collect[c] > 0 && sent[c] < goal.collect[c]) {
                    sent[c]++
                    collect(c, g.cell)
                }
            }
        }
    }

    private fun spriteAt(cell: Int): Sprite? {
        val c = board.colOf(cell).toFloat()
        val r = board.rowOf(cell).toFloat()
        return sprites.values.firstOrNull { abs(it.x - c) < .01f && abs(it.y - r) < .01f }
    }

    private fun applyFx(f: Fx, now: Long) {
        val goal = board.level.goal
        when (f.type) {
            FxType.KNOT -> { shownKnot[f.cell] = f.arg; burst(f.cell, 0xFFD8C4AE.toInt(), 8); effects += Effect(Look.POOF, f.cell, 0, -1, 0, now, 300L) }
            FxType.DUST -> {
                shownKnot[f.cell] = 0
                shownDust[f.cell] = false
                confetti(cx(board.colOf(f.cell).toFloat()), cy(board.rowOf(f.cell).toFloat()), 10, DUST_GREY, cell * 3f)
                effects += Effect(Look.POOF, f.cell, 0, -1, 0, now, 320L)
                sound?.poof()
                if (goal.dust) collect(KEY_DUST, f.cell)
            }
            FxType.PATCH -> {
                shownPatch[f.cell] = f.arg
                burst(f.cell, 0xFFF8BECB.toInt(), 6)
                if (f.arg == 0 && goal.patches) collect(KEY_PATCH, f.cell)
            }
            FxType.UNPIN -> { spriteAt(f.cell)?.pinned = false; burst(f.cell, 0xFF8D97AD.toInt(), 5); sound?.tick() }
            FxType.TRANSFORM -> {
                spriteAt(f.cell)?.let { it.kind = it.piece.kind; it.color = it.piece.color }
                sparkles(f.cell, 6)
                effects += Effect(Look.BORN, f.cell, 0, -1, 0, now, 480L)
                sound?.special()
            }
            FxType.LINE_H, FxType.LINE_V -> {
                effects += Effect(if (f.type == FxType.LINE_H) Look.CUT_H else Look.CUT_V, f.cell, 0, f.color, 0, now, 560L)
                shake(110L, .05f)
                sound?.line()
            }
            FxType.BLAST -> {
                effects += Effect(Look.PUFF, f.cell, f.arg, f.color, 0, now, 600L)
                shake(220L, if (f.arg >= 2) .2f else .12f)
                confetti(cx(board.colOf(f.cell).toFloat()), cy(board.rowOf(f.cell).toFloat()), 10 + f.arg * 6,
                    if (f.color in 0..5) Felt.lighter(Felt.COLORS[f.color], .35f) else Felt.CREAM, cell * (5f + f.arg * 2f))
                sound?.blast()
            }
            FxType.ZAP -> { effects += Effect(Look.THREAD, f.cell, f.arg, f.color, 0, now, 430L); sound?.zap() }
            FxType.FLY -> {
                effects += Effect(Look.WINGS, f.cell, f.arg, f.color, f.extra, now, MercerieBoard.FLIGHT * waveMs)
                sound?.flutter()
            }
            FxType.SPREAD -> {
                shownKnot[f.cell] = 1
                shownDust[f.cell] = true
                Effect(Look.GROW, f.cell, f.arg, -1, 0, now, 420L).also { effects += it; growing[f.cell] = it }
                sound?.spread()
            }
            FxType.TICK -> {
                (board.piece[f.cell]?.let { sprites[it.id] } ?: spriteAt(f.cell))?.let { it.timer = f.arg; it.timerPop = now }
                sound?.tickTock(f.arg)
            }
            FxType.RING -> {
                effects += Effect(Look.RING, f.cell, 0, -1, 0, now, 1100L)
                shake(500L, .22f)
                flash(0xFFE8596A.toInt(), 420L)
                sound?.ring()
            }
            FxType.BASKET -> {}
        }
    }

    private fun fallStep(s: FallStep, t: Long) {
        val tt = t / TICK_MS
        for (f in s.falls) {
            val sp = sprites[f.piece.id] ?: continue
            val hops = f.path
            val last = hops[hops.size - 1]
            if (tt >= last.tick) {
                sp.x = board.colOf(last.cell).toFloat()
                sp.y = board.rowOf(last.cell).toFloat()
                sp.alpha = 1f
                sp.scale = 1f
                val ls = (t - last.tick * TICK_MS) / 170f
                sp.squash = if (ls in 0f..1f) sin(ls * PI.toFloat()) * .16f else 0f
                if (ls >= 0f && fired.add(f)) sound?.land()
                continue
            }
            if (tt <= hops[0].tick) continue
            var i = 0
            while (i < hops.size - 2 && hops[i + 1].tick <= tt) i++
            val a = hops[i]
            val b = hops[i + 1]
            // Une pièce qui attend son tour reste posée, puis fait son pas en un seul temps.
            val u = (tt - (b.tick - 1)).coerceIn(0f, 1f)
            if (board.feed[b.cell] == a.cell) {
                // Téléporteur : elle plonge dans la boutonnière du dessous et ressort par celle du dessus.
                if (u < .5f) {
                    sp.x = board.colOf(a.cell).toFloat()
                    sp.y = board.rowOf(a.cell) + u
                    sp.scale = 1f - u * 1.7f
                } else {
                    sp.x = board.colOf(b.cell).toFloat()
                    sp.y = board.rowOf(b.cell) - (1f - u)
                    sp.scale = .15f + (u - .5f) * 1.7f
                }
                sp.alpha = 1f
                continue
            }
            sp.scale = 1f
            val fromSky = f.spawned && i == 0
            val ay = board.rowOf(a.cell) - (if (fromSky) 1f else 0f)
            sp.x = board.colOf(a.cell) + (board.colOf(b.cell) - board.colOf(a.cell)) * u
            sp.y = ay + (board.rowOf(b.cell) - ay) * u
            sp.alpha = if (fromSky) u else 1f
        }
        if (t >= s.ticks * TICK_MS + 170) endStep()
    }

    // ───────────────────────────── Confettis et étincelles ─────────────────────────────

    /** Une gerbe de confettis de feutrine en ([x], [y]). [color] -1 : toutes les couleurs. */
    private fun confetti(x: Float, y: Float, count: Int, color: Int, speed: Float, big: Boolean = false) {
        val now = clock()
        repeat(count) {
            if (particles.size > MAX_PARTICLES) return
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val v = speed * (.45f + rnd.nextFloat() * .55f)
            val c = when {
                color == -1 -> if (rnd.nextInt(5) == 0) Felt.GOLD else Felt.COLORS[rnd.nextInt(Felt.COLORS.size)]
                rnd.nextInt(4) == 0 -> Felt.CREAM
                else -> color
            }
            particles += Particle(x, y, cos(a) * v, sin(a) * v - speed * .5f, cell * (if (big) .09f + rnd.nextFloat() * .07f else .055f + rnd.nextFloat() * .05f),
                c, rnd.nextFloat() * 360f, (rnd.nextFloat() - .5f) * 720f, Confetti.shapes[rnd.nextInt(Confetti.shapes.size)],
                now, (if (big) 900 else 480) + rnd.nextInt(300), cell * (if (big) 7f else 13f))
        }
    }

    private fun burst(cellIndex: Int, color: Int, count: Int) =
        confetti(cx(board.colOf(cellIndex).toFloat()), cy(board.rowOf(cellIndex).toFloat()), count, color, cell * 3f)

    private fun sparkles(cellIndex: Int, count: Int) {
        val now = clock()
        val x = cx(board.colOf(cellIndex).toFloat())
        val y = cy(board.rowOf(cellIndex).toFloat())
        repeat(count) {
            if (particles.size > MAX_PARTICLES) return
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val v = cell * (1.5f + rnd.nextFloat() * 2f)
            particles += Particle(x, y, cos(a) * v, sin(a) * v, cell * (.08f + rnd.nextFloat() * .06f),
                if (rnd.nextBoolean()) Felt.CREAM else Felt.GOLD, rnd.nextFloat() * 90f, 160f, Confetti.sparkle, now, 460 + rnd.nextInt(200), 0f)
        }
    }

    private fun shake(life: Long, amp: Float) {
        val now = clock()
        val left = shakeStart + shakeLife - now
        if (left > 0 && shakeAmp * left / shakeLife.coerceAtLeast(1L) > amp) return
        shakeStart = now; shakeLife = life; shakeAmp = amp
    }

    private fun flash(color: Int, life: Long) {
        flashStart = clock(); flashLife = life; flashColor = color
    }

    private fun ease(u: Float) = u * u * (3 - 2 * u)

    private fun overshoot(u: Float): Float {
        val s = 1.9f
        val v = u - 1
        return v * v * ((s + 1) * v + s) + 1
    }

    /** Les deux bouts d'une coupe ou la place d'un papillon : rangés ici, sans nouvel objet à chaque image. */
    private val ends = FloatArray(2)
    private val wing = FloatArray(2)

    /** Les deux bouts de la coupe d'une bobine, en pixels le long de la ligne, rangés dans [ends]. */
    private fun cutEnds(e: Effect, travel: Float) {
        val horizontal = e.look == Look.CUT_H
        val e2 = ease(travel)
        if (horizontal) {
            val x = cx(board.colOf(e.cell).toFloat())
            val lo = originX - cell * .2f
            val hi = originX + cell * board.cols + cell * .2f
            ends[0] = x - (x - lo) * e2; ends[1] = x + (hi - x) * e2
        } else {
            val y = cy(board.rowOf(e.cell).toFloat())
            val lo = originY - cell * .2f
            val hi = originY + cell * board.rows + cell * .2f
            ends[0] = y - (y - lo) * e2; ends[1] = y + (hi - y) * e2
        }
    }

    /** La position d'un papillon en vol, sur un arc qui monte avant de piquer sur sa cible ([wing], réutilisé). */
    private fun wingsAt(e: Effect, u: Float): FloatArray {
        val x0 = cx(board.colOf(e.cell).toFloat()); val y0 = cy(board.rowOf(e.cell).toFloat())
        val x1 = cx(board.colOf(e.arg).toFloat()); val y1 = cy(board.rowOf(e.arg).toFloat())
        val side = if ((e.cell + e.arg) % 2 == 0) 1f else -1f
        val mx = (x0 + x1) / 2 - (y1 - y0) * .3f * side
        val my = (y0 + y1) / 2 + (x1 - x0) * .3f * side - cell * 1.2f
        val k = ease(u)
        wing[0] = (1 - k) * (1 - k) * x0 + 2 * (1 - k) * k * mx + k * k * x1
        wing[1] = (1 - k) * (1 - k) * y0 + 2 * (1 - k) * k * my + k * k * y1
        return wing
    }

    // ───────────────────────────── Dessin ─────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val a = art ?: return
        val now = clock()
        update(now)
        canvas.save()
        val shaking = now - shakeStart < shakeLife
        if (shaking) {
            val k = shakeAmp * cell * (1f - (now - shakeStart).toFloat() / shakeLife)
            canvas.translate((rnd.nextFloat() - .5f) * k, (rnd.nextFloat() - .5f) * k)
        }
        backdrop?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        paint.alpha = 255
        for (i in 0 until board.size) if (shownPatch[i] > 0)
            canvas.drawBitmap(a.patch[shownPatch[i] - 1], originX + board.colOf(i) * cell, originY + board.rowOf(i) * cell, paint)
        for (i in 0 until board.size) if (shownKnot[i] > 0) {
            if (shownDust[i]) drawDust(canvas, a, i, now)
            else canvas.drawBitmap(a.knot[(shownKnot[i] - 1).coerceAtMost(2)], originX + board.colOf(i) * cell, originY + board.rowOf(i) * cell, paint)
        }
        if (selected >= 0) {
            val pulse = 1f + sin(now / 130f) * .04f
            val half = cell * .47f * pulse
            val x = cx(board.colOf(selected).toFloat())
            val y = cy(board.rowOf(selected).toFloat())
            path.rewind()
            path.addRoundRect(x - half, y - half, x + half, y + half, cell * .2f, cell * .2f, Path.Direction.CW)
            Felt.stitches(canvas, path, Felt.PINK, cell, .07f)
        }
        var lively = false
        for (pass in 0..1) for (s in sprites.values) {
            val moving = s.scale != 1f || s.alpha != 1f
            if ((pass == 1) != moving) continue
            if (s.alpha <= 0f || s.scale <= 0f) continue
            if (drawSprite(canvas, a, s, now)) lively = true
        }
        paint.alpha = 255
        front?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        if (guided && !busy) guide?.let { drawGuide(canvas, it, now) }
        val dimAge = now - dimStart
        if (dimAge in 0 until DIM_MS) {
            val k = if (dimAge < 120) dimAge / 120f else 1f - (dimAge - 120f) / (DIM_MS - 120f)
            fill.color = Felt.alpha(0xFF1E1420.toInt(), (k * 110).toInt().coerceIn(0, 255))
            canvas.drawRoundRect(originX - cell * .16f, originY - cell * .16f, originX + cell * board.cols + cell * .16f,
                originY + cell * board.rows + cell * .16f, cell * .34f, cell * .34f, fill)
        }
        drawEffects(canvas, a, now)
        for (p in particles) {
            val u = (now - p.born).toFloat() / p.life
            fill.color = p.color
            fill.alpha = ((if (u > .6f) (1f - u) / .4f else 1f) * 255).toInt().coerceIn(0, 255)
            Confetti.draw(canvas, fill, p.shape, p.x, p.y, p.size, p.angle)
        }
        for (f in floaters) {
            val u = (now - f.start) / 850f
            val pop = if (u < .18f) overshoot(u / .18f) else 1f
            text.textSize = cell * (if (f.big) .46f else .34f) * pop
            val y = f.y - cell * .75f * ease(u.coerceAtMost(1f))
            val alpha = ((1f - u * u) * 255).toInt().coerceIn(0, 255)
            outlined(canvas, f.text, f.x, y, f.color, alpha)
        }
        drawCheer(canvas, now)
        val flashAge = now - flashStart
        if (flashAge in 0 until flashLife) {
            fill.color = Felt.alpha(flashColor, ((1f - flashAge.toFloat() / flashLife) * 120).toInt().coerceIn(0, 255))
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        }
        canvas.restore()
        // Pleine cadence pendant que ça bouge ; au repos, ce qui bat doucement (coup conseillé, bonus,
        // minuteurs pressés, scintillement) se contente de 25 images par seconde, et sinon rien du tout
        // jusqu'au prochain scintillement. Sous une fiche, le repos ne redessine rien.
        // Un seul rappel retardé à la fois : un invalidate() extérieur (doigt, coup conseillé) ne
        // dédouble pas la chaîne, qui sinon battrait deux ou trois fois plus vite.
        removeCallbacks(idleTick)
        val calmFx = effects.all { it.look == Look.TWINKLE }
        if ((busy && !ended) || !calmFx || particles.isNotEmpty() || floaters.isNotEmpty() || cheer != null || shaking ||
            dimAge < DIM_MS || flashAge < flashLife) postInvalidateOnAnimation()
        else if (covered) return
        else if (selected >= 0 || hint != null || lively || effects.isNotEmpty()) postDelayed(idleTick, MercerieUi.idleDelay())
        else if (!ended) postDelayed(idleTick, (nextTwinkle - now).coerceAtLeast(MercerieUi.IDLE_FRAME_MS))
    }

    /**
     * Tutoriel : un fil cousu relie les deux cases du coup, et un bout de doigt en feutrine se pose
     * sur la première, glisse jusqu'à la seconde et s'efface, en boucle.
     */
    private fun drawGuide(canvas: Canvas, g: IntArray, now: Long) {
        val ax = cx(board.colOf(g[0]).toFloat())
        val ay = cy(board.rowOf(g[0]).toFloat())
        val bx = cx(board.colOf(g[1]).toFloat())
        val by = cy(board.rowOf(g[1]).toFloat())
        path.rewind()
        path.moveTo(ax, ay)
        path.lineTo(bx, by)
        Felt.stitches(canvas, path, Felt.alpha(Felt.PINK, 200), cell * .9f, .08f)
        val u = (now % GUIDE_MS) / GUIDE_MS.toFloat()
        val glide = ease(((u - .22f) / .5f).coerceIn(0f, 1f))
        val alpha = when {
            u < .14f -> u / .14f
            u > .82f -> (1f - u) / .18f
            else -> 1f
        }
        // Le doigt appuie un peu avant de glisser.
        val press = if (u < .22f) 1f - .12f * (u / .22f) else .88f
        val x = ax + (bx - ax) * glide + cell * .16f
        val y = ay + (by - ay) * glide + cell * .2f
        val r = cell * .27f * press
        fill.color = Felt.alpha(0xFF3A2A30.toInt(), (alpha * 60).toInt())
        canvas.drawCircle(x + cell * .03f, y + cell * .05f, r, fill)
        fill.color = Felt.alpha(Felt.CREAM, (alpha * 235).toInt())
        canvas.drawCircle(x, y, r, fill)
        line.color = Felt.alpha(Felt.PINK, (alpha * 255).toInt())
        line.strokeWidth = cell * .055f
        canvas.drawCircle(x, y, r, line)
        fill.color = Felt.alpha(Felt.lighter(Felt.PINK, .55f), (alpha * 235).toInt())
        canvas.drawCircle(x, y - r * .28f, r * .42f, fill)
    }

    /** Dessine une pièce ; vrai si elle bouge d'elle-même au repos (bonus, minuteur pressé). */
    private fun drawSprite(canvas: Canvas, a: MercerieSprites, s: Sprite, now: Long): Boolean {
        var lively = false
        var scale = s.scale
        if (s.piece.armed) scale *= 1f + sin(now / 45f) * .07f
        val h = hint
        if (h != null && !busy) {
            val i = board.idx(s.y.toInt().coerceIn(0, board.rows - 1), s.x.toInt().coerceIn(0, board.cols - 1))
            if (i == h[0] || i == h[1]) scale *= 1f + sin(now / 110f) * .07f
        }
        // Le souffle des pompons repousse un instant les pièces voisines. (Parcours par indice : un
        // itérateur par pièce et par image, c'était quatre-vingts objets jetés à chaque image.)
        var ox = 0f
        var oy = 0f
        for (k in 0 until effects.size) {
            val e = effects[k]
            if (e.look != Look.PUFF) continue
            val age = now - e.start
            if (age > 320) continue
            val dx = s.x - board.colOf(e.cell)
            val dy = s.y - board.rowOf(e.cell)
            val d = hypot(dx, dy)
            if (d < .3f || d > e.arg + 1.8f) continue
            val push = sin(age / 320f * PI.toFloat()) * .18f / d.coerceAtLeast(.8f)
            ox += dx / d * push; oy += dy / d * push
        }
        val w = cell * scale * (1f + s.squash)
        val hgt = cell * scale * (1f - s.squash)
        val x = cx(s.x + ox)
        val y = cy(s.y + oy) + cell * scale * s.squash / 2
        dst.set(x - w / 2, y - hgt / 2, x + w / 2, y + hgt / 2)
        paint.alpha = (s.alpha * 255).toInt().coerceIn(0, 255)
        val arrow = a.arrowOf(s.kind)
        when {
            arrow != null -> {
                // Bobine : les ciseaux battent doucement au-dessus de la pièce.
                lively = true
                canvas.drawBitmap(a.plain(s.color), null, dst, paint)
                val beat = 1f + sin(now / 170f) * .1f
                scaled(dst, beat, beat)
                canvas.drawBitmap(arrow, null, beatRect, paint)
            }
            s.kind == Kind.RAINBOW -> {
                // Le bouton arc-en-ciel tourne doucement sur lui-même : un tour en cinq secondes.
                lively = true
                canvas.save()
                canvas.rotate((now % RAINBOW_TURN_MS) * 360f / RAINBOW_TURN_MS, dst.centerX(), dst.centerY())
                canvas.drawBitmap(a.rainbow, null, dst, paint)
                canvas.restore()
            }
            s.kind == Kind.BOMB -> {
                // Le pompon gonfle et dégonfle à peine.
                lively = true
                val puff = 1f + sin(now / 260f) * .05f
                scaled(dst, puff, puff)
                canvas.drawBitmap(a.of(s.kind, s.color), null, beatRect, paint)
            }
            s.kind == Kind.BUTTERFLY -> {
                // Le papillon bat des ailes, la pièce posée dessus.
                lively = true
                val flap = 1f - (.5f + .5f * sin(now / 150f)) * .3f
                scaled(dst, flap, 1f)
                canvas.drawBitmap(a.wings[s.color.coerceIn(0, 5)], null, beatRect, paint)
                scaled(dst, a.bodyScale, a.bodyScale)
                beatRect.offset(0f, dst.height() * .02f)
                canvas.drawBitmap(a.plain(s.color), null, beatRect, paint)
            }
            else -> canvas.drawBitmap(a.of(s.kind, s.color), null, dst, paint)
        }
        if (s.pinned) canvas.drawBitmap(a.pin, null, dst, paint)
        if (s.timer > 0) {
            val hot = s.timer <= 3
            if (hot) lively = true
            val popAge = now - s.timerPop
            val pop = if (popAge in 0..300) 1f + sin(popAge / 300f * PI.toFloat()) * .35f else 1f
            val side = dst.width() * .52f * pop
            val bx = dst.right - dst.width() * .24f
            val by = dst.top + dst.height() * .26f
            canvas.save()
            if (hot) canvas.rotate(sin(now / 55f) * 9f, bx, by)
            beatRect.set(bx - side / 2, by - side / 2, bx + side / 2, by + side / 2)
            canvas.drawBitmap(a.clock[if (hot) 1 else 0], null, beatRect, paint)
            text.textSize = side * (if (s.timer >= 10) .34f else .42f)
            text.style = Paint.Style.FILL
            text.color = if (hot) 0xFFB3202E.toInt() else 0xFF3A2E36.toInt()
            text.alpha = paint.alpha
            canvas.drawText(timerText(s.timer), bx, by + side * .05f + text.textSize * .36f, text)
            canvas.restore()
        }
        return lively
    }

    /** Les nombres des minuteurs, écrits une fois : un minuteur pressé est redessiné à chaque image. */
    private val timerTexts = arrayOfNulls<String>(100)

    private fun timerText(n: Int): String =
        if (n >= 0 && n < timerTexts.size) timerTexts[n] ?: n.toString().also { timerTexts[n] = it } else n.toString()

    private fun scaled(src: RectF, kx: Float, ky: Float) {
        val hw = src.width() * kx / 2
        val hh = src.height() * ky / 2
        beatRect.set(src.centerX() - hw, src.centerY() - hh, src.centerX() + hw, src.centerY() + hh)
    }

    /** Un mouton de poussière ; celui qui vient de s'étendre roule depuis sa case de départ en gonflant. */
    private fun drawDust(canvas: Canvas, a: MercerieSprites, i: Int, now: Long) {
        var x = originX + board.colOf(i) * cell
        var y = originY + board.rowOf(i) * cell
        val grow = growing[i]
        if (grow != null) {
            val u = ((now - grow.start).toFloat() / grow.life).coerceIn(0f, 1f)
            val k = ease(min(1f, u * 1.4f))
            x = originX + (board.colOf(grow.arg) + (board.colOf(i) - board.colOf(grow.arg)) * k) * cell
            y = originY + (board.rowOf(grow.arg) + (board.rowOf(i) - board.rowOf(grow.arg)) * k) * cell
            val s = .35f + .65f * overshoot(u)
            dst.set(x + cell * (1 - s) / 2, y + cell * (1 - s) / 2, x + cell * (1 + s) / 2, y + cell * (1 + s) / 2)
            canvas.drawBitmap(a.dust, null, dst, paint)
            return
        }
        canvas.drawBitmap(a.dust, x, y, paint)
    }

    private fun outlined(canvas: Canvas, s: String, x: Float, y: Float, color: Int, alpha: Int) {
        text.style = Paint.Style.STROKE
        text.strokeJoin = Paint.Join.ROUND
        text.strokeWidth = text.textSize * .24f
        text.color = Felt.CREAM
        text.alpha = alpha
        canvas.drawText(s, x, y, text)
        text.style = Paint.Style.FILL
        text.color = color
        text.alpha = alpha
        canvas.drawText(s, x, y, text)
    }

    /**
     * Le mot cousu : les lettres sautent l'une après l'autre, dansent un peu, puis s'envolent. Aux
     * grands paliers, des rayons de feutrine tournent derrière ; au plus haut, deux couronnes.
     */
    private fun drawCheer(canvas: Canvas, now: Long) {
        val c = cheer ?: return
        val word = c.word
        val age = (now - c.start).toFloat()
        val size = word.size
        val baseline = cheerBaseline() + size * .35f
        val centerY = baseline - size * .35f
        val out = ((age - (c.life - 320f)) / 320f).coerceIn(0f, 1f)
        if (word.tier >= 3) {
            val grow = ease(min(1f, age / 220f))
            val reach = maxOf(word.width * .62f, size * 1.5f) * grow * (1f + out * .2f)
            val fade = 1f - out
            for (layer in 0 until (if (word.tier >= 5) 2 else 1)) {
                canvas.save()
                canvas.translate(width / 2f, centerY)
                canvas.rotate(age * (if (layer == 0) .035f else -.05f))
                canvas.scale(reach * (if (layer == 0) 1f else .7f), reach * (if (layer == 0) 1f else .7f))
                for (k in 0 until RAYS) {
                    fill.color = if (layer == 1) Felt.COLORS[k % Felt.COLORS.size] else if (k % 2 == 0) Felt.GOLD else Felt.CREAM
                    fill.alpha = ((if (layer == 0) 120 else 90) * fade).toInt().coerceIn(0, 255)
                    canvas.drawPath(ray, fill)
                    canvas.rotate(360f / RAYS)
                }
                canvas.restore()
            }
        }
        var x = width / 2f - word.width / 2
        val warnShake = if (word.tier == 0 && age < 450) sin(age / 28f) * size * .05f * (1f - age / 450f) else 0f
        paint.alpha = ((1f - out) * 255).toInt().coerceIn(0, 255)
        for (k in 0 until word.letters.size) {
            val bmp = word.letters[k]
            val adv = word.advances[k]
            if (bmp != null) {
                val appear = age - k * 38f
                if (appear > 0) {
                    val u = min(1f, appear / 240f)
                    var scale = overshoot(u) * (1f + out * .3f)
                    if (word.tier >= 4) scale *= 1f + sin(age / 90f + k) * .03f
                    val bob = sin(age / 140f + k * .7f) * size * .05f
                    val tilt = sin(age / 170f + k) * 5f + (if (k % 2 == 0) -3f else 3f) + (1f - u) * (if (k % 2 == 0) -25f else 25f)
                    canvas.save()
                    canvas.translate(x + adv / 2 + warnShake, baseline + bob - out * size * .6f)
                    canvas.rotate(tilt)
                    canvas.scale(scale, scale)
                    canvas.drawBitmap(bmp, -bmp.width / 2f, -size * CheerWord.BASE, paint)
                    canvas.restore()
                }
            }
            x += adv
        }
        paint.alpha = 255
    }

    private fun drawEffects(canvas: Canvas, a: MercerieSprites, now: Long) {
        for (e in effects) {
            val age = (now - e.start).toFloat()
            val u = (age / e.life).coerceIn(0f, 1f)
            val x = cx(board.colOf(e.cell).toFloat())
            val y = cy(board.rowOf(e.cell).toFloat())
            val tint = if (e.color in 0..5) Felt.COLORS[e.color] else Felt.PINK
            when (e.look) {
                Look.CUT_H, Look.CUT_V -> {
                    val horizontal = e.look == Look.CUT_H
                    val travel = (age / CUT_MS).coerceIn(0f, 1f)
                    cutEnds(e, travel)
                    val lo = ends[0]
                    val hi = ends[1]
                    val fade = if (travel < 1f) 1f else 1f - ((age - CUT_MS) / (e.life - CUT_MS)).coerceIn(0f, 1f)
                    val x0 = if (horizontal) lo else x
                    val x1 = if (horizontal) hi else x
                    val y0 = if (horizontal) y else lo
                    val y1 = if (horizontal) y else hi
                    line.color = Felt.alpha(Felt.CREAM, (fade * 230).toInt())
                    line.strokeWidth = cell * .46f * (1f - u * .6f)
                    canvas.drawLine(x0, y0, x1, y1, line)
                    path.rewind(); path.moveTo(x0, y0); path.lineTo(x1, y1)
                    Felt.stitches(canvas, path, Felt.alpha(tint, (fade * 255).toInt()), cell, .07f)
                    val scissors = a.arrowOf(if (horizontal) Kind.LINE_H else Kind.LINE_V) ?: continue
                    paint.alpha = (fade * 255).toInt().coerceIn(0, 255)
                    val snip = 1f + sin(age / 30f) * .08f
                    val side = cell * 2.1f * snip
                    for (tip in 0..1) {
                        val end = if (tip == 0) hi else lo
                        val flip = if (tip == 0) 1f else -1f
                        val sx = if (horizontal) end else x
                        val sy = if (horizontal) y else end
                        canvas.save()
                        canvas.translate(sx, sy)
                        if (horizontal) canvas.scale(flip, 1f) else canvas.scale(1f, flip)
                        dst.set(-side / 2, -side / 2, side / 2, side / 2)
                        canvas.drawBitmap(scissors, null, dst, paint)
                        canvas.restore()
                    }
                    paint.alpha = 255
                }
                Look.PUFF -> {
                    val e2 = 1f - (1f - u) * (1f - u)
                    if (age < 160) {
                        fill.color = Felt.alpha(Felt.CREAM, ((1f - age / 160f) * 210).toInt())
                        canvas.drawCircle(x, y, cell * (.7f + e.arg * .4f), fill)
                    }
                    val wool = Felt.lighter(tint, .45f)
                    val tufts = 12 + e.arg * 4
                    val dist = cell * (.35f + (e.arg + .7f) * e2)
                    val r = cell * (.2f + e.arg * .04f) * (1f - u * .7f)
                    fill.color = Felt.alpha(wool, ((1f - u) * 230).toInt())
                    for (k in 0 until tufts) {
                        val ang = k * 2 * PI.toFloat() / tufts + e.cell
                        canvas.drawCircle(x + cos(ang) * dist, y + sin(ang) * dist, r, fill)
                    }
                    line.color = Felt.alpha(Felt.CREAM, ((1f - u) * 230).toInt())
                    line.strokeWidth = cell * .26f * (1f - u)
                    canvas.drawCircle(x, y, cell * (.4f + (e.arg + .9f) * e2), line)
                }
                Look.THREAD -> {
                    val tx = cx(board.colOf(e.arg).toFloat())
                    val ty = cy(board.rowOf(e.arg).toFloat())
                    val reach = min(1f, age / 170f)
                    val side = if (e.arg % 2 == 0) 1f else -1f
                    val mx = (x + tx) / 2 - (ty - y) * .25f * side
                    val my = (y + ty) / 2 + (tx - x) * .25f * side
                    path.rewind()
                    path.moveTo(x, y)
                    var px = x
                    var py = y
                    var qx = x
                    var qy = y
                    for (k in 1..10) {
                        val v = reach * k / 10f
                        qx = px; qy = py
                        px = (1 - v) * (1 - v) * x + 2 * (1 - v) * v * mx + v * v * tx
                        py = (1 - v) * (1 - v) * y + 2 * (1 - v) * v * my + v * v * ty
                        path.lineTo(px, py)
                    }
                    val fade = if (age < 260) 1f else 1f - (age - 260) / (e.life - 260f)
                    line.color = Felt.alpha(Felt.lighter(tint, .5f), (fade * 150).toInt().coerceIn(0, 255))
                    line.strokeWidth = cell * .12f
                    canvas.drawPath(path, line)
                    Felt.stitches(canvas, path, Felt.alpha(tint, (fade * 255).toInt().coerceIn(0, 255)), cell, .08f)
                    if (reach < 1f) {
                        // L'aiguille au bout du fil.
                        val d = hypot(px - qx, py - qy).coerceAtLeast(.01f)
                        val nx = (px - qx) / d
                        val ny = (py - qy) / d
                        line.color = 0xFF9AA3B5.toInt()
                        line.strokeWidth = cell * .055f
                        canvas.drawLine(px - nx * cell * .3f, py - ny * cell * .3f, px + nx * cell * .06f, py + ny * cell * .06f, line)
                        line.color = 0xFF5B4034.toInt()
                        line.strokeWidth = cell * .02f
                        canvas.drawCircle(px - nx * cell * .25f, py - ny * cell * .25f, cell * .025f, line)
                    }
                }
                Look.SPIN -> {
                    val s = cell * (1f + u * .7f)
                    paint.alpha = ((1f - u) * 255).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.rotate(age * 1.3f, x, y)
                    dst.set(x - s / 2, y - s / 2, x + s / 2, y + s / 2)
                    canvas.drawBitmap(a.rainbow, null, dst, paint)
                    canvas.restore()
                    paint.alpha = 255
                }
                Look.WINGS -> {
                    if (u >= 1f) continue
                    val p = wingsAt(e, u)
                    val color = e.color.coerceIn(0, 5)
                    val flap = .3f + .7f * abs(cos(age / 36f))
                    val s = cell * 1.2f
                    val tx = cx(board.colOf(e.arg).toFloat())
                    canvas.save()
                    canvas.translate(p[0], p[1])
                    canvas.rotate(if (tx > x) 14f else if (tx < x) -14f else 0f)
                    dst.set(-s * flap / 2, -s / 2, s * flap / 2, s / 2)
                    canvas.drawBitmap(a.wings[color], null, dst, paint)
                    val b = s * a.bodyScale
                    dst.set(-b / 2, -b / 2 + s * .02f, b / 2, b / 2 + s * .02f)
                    canvas.drawBitmap(a.plain(color), null, dst, paint)
                    val carried = e.extra
                    if (carried == Kind.LINE_H.ordinal || carried == Kind.LINE_V.ordinal) {
                        a.arrowOf(Kind.entries[carried])?.let { sc -> dst.set(-s * .6f, -s * .6f, s * .6f, s * .6f); canvas.drawBitmap(sc, null, dst, paint) }
                    } else if (carried == Kind.BOMB.ordinal) {
                        dst.set(-s * .3f, s * .1f, s * .3f, s * .7f)
                        canvas.drawBitmap(a.of(Kind.BOMB, color), null, dst, paint)
                    }
                    canvas.restore()
                }
                Look.POOF -> {
                    line.color = Felt.alpha(if (e.color in 0..5) Felt.lighter(Felt.COLORS[e.color], .5f) else Felt.CREAM, ((1f - u) * 210).toInt())
                    line.strokeWidth = cell * .1f * (1f - u)
                    canvas.drawCircle(x, y, cell * (.22f + .42f * ease(u)), line)
                }
                Look.BORN -> {
                    fill.color = Felt.alpha(Felt.CREAM, ((1f - u) * 150).toInt())
                    canvas.drawCircle(x, y, cell * (.4f + .25f * u), fill)
                    fill.color = Felt.alpha(Felt.GOLD, ((1f - u) * 255).toInt())
                    val ring = cell * (.3f + .5f * ease(u))
                    for (k in 0 until 8) {
                        val ang = k * PI.toFloat() / 4 + u * 1.2f
                        Confetti.draw(canvas, fill, Confetti.sparkle, x + cos(ang) * ring, y + sin(ang) * ring, cell * .09f * (1f - u * .5f), u * 90f)
                    }
                }
                Look.RING -> {
                    val wobble = sin(age / 40f) * 22f * (1f - u)
                    val s = cell * (1.3f + sin(age / 70f) * .12f)
                    canvas.save()
                    canvas.rotate(wobble, x, y)
                    dst.set(x - s / 2, y - s / 2, x + s / 2, y + s / 2)
                    canvas.drawBitmap(a.clock[1], null, dst, paint)
                    canvas.restore()
                    for (k in 0 until 3) {
                        val v = ((age / 360f) + k / 3f) % 1f
                        line.color = Felt.alpha(0xFFE8596A.toInt(), ((1f - v) * 200 * (1f - u)).toInt().coerceIn(0, 255))
                        line.strokeWidth = cell * .08f
                        canvas.drawCircle(x, y, cell * (.5f + v * 1.1f), line)
                    }
                }
                Look.TWINKLE -> {
                    fill.color = Felt.alpha(0xFFFFFFFF.toInt(), (sin(u * PI.toFloat()) * 235).toInt().coerceIn(0, 255))
                    Confetti.draw(canvas, fill, Confetti.sparkle, x - cell * .2f, y - cell * .22f, cell * .17f * sin(u * PI.toFloat()), u * 90f)
                }
                Look.GROW -> {}
            }
        }
    }

    companion object {
        /** Le tissu du plateau (`plateau.webp`), chargé une fois par mode. */
        private val cloth = HashMap<Boolean, Bitmap>()

        private fun linen(context: Context): Bitmap? =
            cloth[Felt.dark] ?: Felt.image(context.assets, "plateau")?.also { cloth[Felt.dark] = it }

        /** Objectifs autres qu'une couleur, pour [Listener.onCollect]. */
        const val KEY_PATCH = 10
        const val KEY_THIMBLE = 11
        const val KEY_DUST = 12

        /** Ce qu'il faut emporter en un coup pour chaque palier de félicitations. */
        private val HEAT = intArrayOf(12, 20, 30, 44, 62)
        private const val RAYS = 14
        private const val MAX_PARTICLES = 340
        private const val DUST_GREY = 0xFFC4BDCE.toInt()
        private const val RAINBOW_TURN_MS = 5000L
        private const val SWAP_MS = 150f
        private const val WAVE_MS = 70L
        private const val BONUS_WAVE_MS = 120L
        private const val SHOT_MS = 420L
        private const val POP_MS = 230f
        private const val TICK_MS = 58f
        private const val CUT_MS = 300f
        private const val DIM_MS = 700L
        /** Un aller de la main du tutoriel, temps du plateau (donc ralenti). */
        private const val GUIDE_MS = 1100L
    }
}
