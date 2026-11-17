package com.Atom2Universe.app.games.othello

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Le plateau de Reversi : cadre de bois et tapis de feutre (dessinés une fois par taille), pions
 * en relief qui tombent en place puis se retournent en vague depuis le coup joué, chacun le long
 * de la ligne qui l'a pris.
 *
 * L'écran appelle [sync] après chaque changement de la partie : la vue compare la partie à ce
 * qu'elle montre et anime la différence (un pion posé, des pions retournés) ; une nouvelle partie
 * ou une reprise s'affichent d'un coup. Les coups possibles apparaissent une fois les pions
 * retournés : un petit point de la couleur du joueur au trait.
 */
class OthelloBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnCellClickListener {
        fun onCellClick(row: Int, col: Int)
    }

    var cellClickListener: OnCellClickListener? = null

    /** Reçoit le compte des pions tels qu'on les voit : il suit les retournements. */
    var onCountsShown: ((black: Int, white: Int) -> Unit)? = null

    /** Les coups possibles du joueur au trait ; masqués pendant que l'IA réfléchit. */
    var showValidMoves = true
        set(value) {
            if (value && !field) hintsFrom = max(SystemClock.uptimeMillis(), animEnd)
            field = value
            if (!value) pressed = -1
            postInvalidateOnAnimation()
        }

    /** La case du dernier coup (rangée × 8 + colonne), ou -1. */
    var lastMove = -1
        set(value) {
            field = value
            invalidate()
        }

    private var game: OthelloGame? = null
    private val n = OthelloGame.BOARD_SIZE
    private val shown = IntArray(n * n)
    private val before = IntArray(n * n)
    private val startAt = LongArray(n * n)
    private val kind = IntArray(n * n)
    private val axis = FloatArray(n * n)
    private var animEnd = 0L
    private var hintsFrom = 0L
    private var pressed = -1
    private var countedBlack = -1
    private var countedWhite = -1

    private val dp = resources.displayMetrics.density
    private var base: Bitmap? = null
    private var frame = 0f
    private var cell = 0f
    private var radius = 0f
    private var blackSprite: Bitmap? = null
    private var whiteSprite: Bitmap? = null
    private var shadowSprite: Bitmap? = null

    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, OthelloArt.corner(view.width.toFloat()))
            }
        }
        elevation = 10 * dp
    }

    /** Montre [game] tel quel, sans animation. */
    fun bind(game: OthelloGame) {
        this.game = game
        sync(animate = false)
    }

    /** Ce qui reste à voir des animations en cours, en millisecondes. */
    fun animationRemainingMs(): Long = max(0L, animEnd - SystemClock.uptimeMillis())

    /**
     * Met la vue à jour d'après la partie. Avec [animate], un seul pion posé depuis la dernière
     * fois : il tombe en place et les pions pris se retournent, les plus proches d'abord.
     */
    fun sync(animate: Boolean) {
        val g = game ?: return
        val now = SystemClock.uptimeMillis()
        var placed = -1
        var placedCount = 0
        var emptied = false
        for (i in 0 until n * n) {
            val v = g.board[i / n][i % n]
            if (shown[i] == OthelloGame.EMPTY && v != OthelloGame.EMPTY) { placed = i; placedCount++ }
            else if (shown[i] != OthelloGame.EMPTY && v == OthelloGame.EMPTY) emptied = true
        }
        if (!animate || placedCount != 1 || emptied) {
            for (i in 0 until n * n) {
                shown[i] = g.board[i / n][i % n]
                kind[i] = NONE
            }
            animEnd = now
            if (lastMove >= 0 && shown[lastMove] == OthelloGame.EMPTY) lastMove = -1
        } else {
            lastMove = placed
            val pr = placed / n
            val pc = placed % n
            for (i in 0 until n * n) {
                val v = g.board[i / n][i % n]
                if (i == placed) {
                    before[i] = OthelloGame.EMPTY
                    shown[i] = v
                    kind[i] = PLACE
                    startAt[i] = now
                    continue
                }
                if (v == shown[i]) continue
                before[i] = colorAt(i, now)
                shown[i] = v
                val dr = i / n - pr
                val dc = i % n - pc
                val steps = max(abs(dr), abs(dc))
                kind[i] = FLIP
                startAt[i] = now + FLIP_DELAY_MS + (steps - 1) * FLIP_STEP_MS
                axis[i] = Math.toDegrees(atan2(dr.toDouble(), dc.toDouble())).toFloat()
            }
            animEnd = now
            for (i in 0 until n * n) when (kind[i]) {
                PLACE -> animEnd = max(animEnd, startAt[i] + PLACE_MS)
                FLIP -> animEnd = max(animEnd, startAt[i] + FLIP_MS)
            }
        }
        hintsFrom = max(now, animEnd)
        pressed = -1
        reportCounts(now)
        postInvalidateOnAnimation()
    }

    /** La couleur qu'on voit en [i] à l'instant [now] (un pion retourné change à mi-course). */
    private fun colorAt(i: Int, now: Long): Int =
        if (kind[i] == FLIP && now < startAt[i] + FLIP_MS / 2) before[i] else shown[i]

    private fun reportCounts(now: Long) {
        var black = 0
        var white = 0
        for (i in 0 until n * n) when (colorAt(i, now)) {
            OthelloGame.BLACK -> black++
            OthelloGame.WHITE -> white++
        }
        if (black != countedBlack || white != countedWhite) {
            countedBlack = black
            countedWhite = white
            onCountsShown?.invoke(black, white)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val wFree = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
        val hFree = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
        val size = when {
            wFree && hFree -> (320 * dp).toInt()
            wFree -> h
            hFree -> w
            else -> min(w, h)
        }
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val size = min(w, h)
        frame = OthelloArt.frame(size.toFloat())
        cell = (size - 2 * frame) / n
        radius = cell * OthelloArt.DISC
        base = OthelloArt.board(size, dp)
        blackSprite = OthelloArt.discSprite(radius, OthelloGame.BLACK)
        whiteSprite = OthelloArt.discSprite(radius, OthelloGame.WHITE)
        shadowSprite = OthelloArt.shadowSprite(radius)
        invalidateOutline()
    }

    private fun centre(index: Int) = frame + (index + 0.5f) * cell

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val board = base ?: return
        canvas.drawBitmap(board, 0f, 0f, null)
        val now = SystemClock.uptimeMillis()
        var busy = false
        for (i in 0 until n * n) {
            if (kind[i] == PLACE && now >= startAt[i] + PLACE_MS) kind[i] = NONE
            if (kind[i] == FLIP && now >= startAt[i] + FLIP_MS) kind[i] = NONE
            if (kind[i] != NONE) busy = true
        }
        // Les ombres d'abord : un pion soulevé projette la sienne sous ses voisins.
        for (pass in 0..1) for (i in 0 until n * n) {
            if (shown[i] != OthelloGame.EMPTY) drawCell(canvas, i, now, shadow = pass == 0)
        }

        // Le dernier coup : un petit point sur le pion.
        val last = lastMove
        if (last >= 0 && shown[last] != OthelloGame.EMPTY && kind[last] == NONE) {
            val cx = centre(last % n)
            val cy = centre(last / n) - radius * OthelloArt.THICKNESS / 2f
            dotPaint.color = 0x66000000
            canvas.drawCircle(cx, cy + radius * 0.02f, radius * 0.15f, dotPaint)
            dotPaint.color = LAST_MOVE_COLOR
            canvas.drawCircle(cx, cy, radius * 0.13f, dotPaint)
        }

        // Les coups possibles, une fois les pions retournés.
        val hintsOn = showValidMoves && !g.gameOver && now >= hintsFrom
        if (showValidMoves && !g.gameOver && now < hintsFrom + HINT_FADE_MS) busy = true
        if (hintsOn) {
            val fade = ((now - hintsFrom).toFloat() / HINT_FADE_MS).coerceIn(0f, 1f)
            val blackToPlay = g.currentPlayer == OthelloGame.BLACK
            dotPaint.color = if (blackToPlay) Color.argb((105 * fade).toInt(), 0, 0, 0)
            else Color.argb((140 * fade).toInt(), 255, 255, 255)
            for ((pos, _) in g.validMoves) {
                canvas.drawCircle(centre(pos.second), centre(pos.first), cell * 0.1f, dotPaint)
            }
            // Le doigt posé sur une case jouable : le pion en transparence.
            val p = pressed
            if (p >= 0 && shown[p] == OthelloGame.EMPTY) {
                drawPiece(canvas, centre(p % n), centre(p / n), g.currentPlayer, shadow = false, alpha = 120)
            }
        }

        reportCounts(now)
        if (busy) postInvalidateOnAnimation()
    }

    private fun drawCell(canvas: Canvas, i: Int, now: Long, shadow: Boolean) {
        val cx = centre(i % n)
        val cy = centre(i / n)
        val color = shown[i]
        when (kind[i]) {
            PLACE -> {
                val t = ((now - startAt[i]).toFloat() / PLACE_MS).coerceIn(0f, 1f)
                val e = 1f - (1f - t) * (1f - t) * (1f - t)
                drawPiece(canvas, cx, cy, color, shadow, scale = 1.3f - 0.3f * e, lift = 1f - e,
                    alpha = (255 * min(1f, t * 2.5f)).toInt())
            }
            FLIP -> {
                val t = (now - startAt[i]).toFloat() / FLIP_MS
                if (t <= 0f) {
                    drawPiece(canvas, cx, cy, before[i], shadow)
                } else {
                    val lift = sin(PI.toFloat() * t)
                    val squeeze = max(0.05f, abs(cos(PI.toFloat() * t)))
                    drawPiece(canvas, cx, cy, if (t < 0.5f) before[i] else color, shadow,
                        scale = 1f + 0.14f * lift, lift = lift, squeeze = squeeze, axisDeg = axis[i])
                }
            }
            else -> drawPiece(canvas, cx, cy, color, shadow)
        }
    }

    /**
     * Un pion (ou son ombre) : [scale] l'agrandit, [lift] le soulève (l'ombre s'écarte et pâlit),
     * [squeeze] l'écrase le long de la direction [axisDeg] pendant qu'il se retourne.
     */
    private fun drawPiece(
        canvas: Canvas, cx: Float, cy: Float, color: Int, shadow: Boolean,
        scale: Float = 1f, lift: Float = 0f, squeeze: Float = 1f, axisDeg: Float = 0f, alpha: Int = 255
    ) {
        val sprite = (if (shadow) shadowSprite else if (color == OthelloGame.BLACK) blackSprite else whiteSprite) ?: return
        val half = sprite.width / 2f
        canvas.save()
        if (shadow) {
            val drop = radius * (0.1f + 0.32f * lift)
            canvas.translate(cx + drop * 0.35f, cy + drop)
            val k = scale * (1f + 0.12f * lift)
            canvas.scale(k, k)
            spritePaint.alpha = (alpha * (1f - 0.45f * lift)).toInt().coerceIn(0, 255)
        } else {
            canvas.translate(cx, cy - radius * 0.12f * lift)
            canvas.scale(scale, scale)
            spritePaint.alpha = alpha.coerceIn(0, 255)
        }
        if (squeeze < 1f) {
            canvas.rotate(axisDeg)
            canvas.scale(squeeze, 1f)
            canvas.rotate(-axisDeg)
        }
        canvas.drawBitmap(sprite, -half, -half, spritePaint)
        canvas.restore()
    }

    private fun cellAt(x: Float, y: Float): Int {
        if (cell <= 0f) return 0
        val row = ((y - frame) / cell).toInt().coerceIn(0, n - 1)
        val col = ((x - frame) / cell).toInt().coerceIn(0, n - 1)
        return row * n + col
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return true
        val i = cellAt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val playable = showValidMoves && !g.gameOver &&
                    SystemClock.uptimeMillis() >= hintsFrom && g.validMoves.containsKey(Pair(i / n, i % n))
                val p = if (playable) i else -1
                if (p != pressed) { pressed = p; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                if (pressed != -1) { pressed = -1; invalidate() }
                if (!g.gameOver) {
                    performClick()
                    cellClickListener?.onCellClick(i / n, i % n)
                }
            }
            MotionEvent.ACTION_CANCEL -> if (pressed != -1) { pressed = -1; invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val NONE = 0
        const val PLACE = 1
        const val FLIP = 2
        const val PLACE_MS = 220L
        const val FLIP_MS = 320L
        const val FLIP_DELAY_MS = 110L
        const val FLIP_STEP_MS = 70L
        const val HINT_FADE_MS = 200L
        const val LAST_MOVE_COLOR = 0xFFE2553F.toInt()
    }
}
