package com.Atom2Universe.app.games.match3

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import java.io.IOException
import kotlin.math.*
import kotlin.random.Random

class Match3View @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val COLS = 10   // côté court (largeur en portrait)
        private const val ROWS = 16   // côté long  (hauteur en portrait, direction de chute)
        private const val GEM_COUNT = 5
        private const val MATCH_MIN = 3

        private const val DRAG_SCALE_MAX = 1.18f
        private const val DRAG_SCALE_MS  = 130f
        private const val SWAP_ANIM_MS   = 200L
        private const val POP_ANIM_MS    = 320L
        private const val PARTICLE_LIFE_MS = 500f
        private const val PARTICLE_COUNT   = 8
        private const val FALL_DELAY_MS    = 80L

        private const val TIMER_START    = 6f    // secondes de départ
        private const val TIMER_PER_MATCH = 1f   // secondes gagnées par match
        private const val TIMER_MIN_MAX  = 1f    // plancher du timerMax
        private const val COLOR_CYCLE_MS = 20_000L // fenêtre diversité couleurs
    }

    // gridCols/gridRows sont fixes — la grille ne change jamais
    private val gridCols = COLS
    private val gridRows = ROWS

    // ── Assets ────────────────────────────────────────────────────────────────
    private val gemAssetPaths = arrayOf(
        "Assets/sprites/Argent.png",
        "Assets/sprites/Bronze.png",
        "Assets/sprites/Cuivre.png",
        "Assets/sprites/Diamant.png",
        "Assets/sprites/Or.png"
    )
    private val gemFallbackColors = intArrayOf(
        Color.parseColor("#ADBECA"),
        Color.parseColor("#C77E36"),
        Color.parseColor("#B87333"),
        Color.parseColor("#82D9FF"),
        Color.parseColor("#E6C838")
    )
    private val gemBitmaps    = arrayOfNulls<Bitmap>(GEM_COUNT)
    private val scaledBitmaps = arrayOfNulls<Bitmap>(GEM_COUNT)

    // ── Grille ────────────────────────────────────────────────────────────────
    private val grid    = Array(gridRows) { IntArray(gridCols) }
    private val cleared = Array(gridRows) { BooleanArray(gridCols) }

    // ── Layout ────────────────────────────────────────────────────────────────
    private var tileSize = 0f
    private var gridLeft = 0f
    private var gridTop  = 0f

    // ── Son ───────────────────────────────────────────────────────────────────
    private val soundEngine = Match3SoundEngine()
    private var comboCount  = 0

    // ── État ──────────────────────────────────────────────────────────────────
    private var isProcessing = false
    var score = 0; private set
    var onScoreChanged: ((Int) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    // ── Drag ──────────────────────────────────────────────────────────────────
    private var dragRow = -1
    private var dragCol = -1
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var swipeTriggered = false
    private var dragPressTime = 0L

    // ── Animation swap ────────────────────────────────────────────────────────
    private data class SwapAnim(val r1: Int, val c1: Int, val r2: Int, val c2: Int)
    private var swapAnim: SwapAnim? = null
    private var swapProgress = 0f
    private var swapAnimator: ValueAnimator? = null

    // ── Animation pop ─────────────────────────────────────────────────────────
    private data class PopCell(
        val r: Int, val c: Int, val gemType: Int,
        val cx: Float, val cy: Float, val startTime: Long
    )
    private val popCells = mutableListOf<PopCell>()

    // ── Particules ────────────────────────────────────────────────────────────
    private data class Particle(
        val startX: Float, val startY: Float,
        val vx: Float, val vy: Float,
        val color: Int, val radius: Float, val startTime: Long
    )
    private val particles = mutableListOf<Particle>()

    // ── Peintures ─────────────────────────────────────────────────────────────
    private val paintBg      = Paint().apply { color = Color.parseColor("#0F0F1E") }
    private val paintCell    = Paint().apply { color = Color.parseColor("#1A1A2E") }
    private val paintRing    = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val paintGem      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintFallback = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintParticle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintOverlay  = Paint().apply { color = Color.argb(190, 10, 10, 25) }
    private val paintGameOver = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    // ── Timer ─────────────────────────────────────────────────────────────────
    private var timerMax   = TIMER_START
    private var timerValue = TIMER_START
    private var timerLastTick = 0L
    private var gameStartUptimeMs = 0L
    private var gameElapsedMs = 0L
    private var neutrinosEarned = 0
    private var gameOver = false

    // Cycle de 20 sec : toutes les couleurs doivent être matchées, sinon timerMax -1
    private val matchedColorsInCycle = mutableSetOf<Int>()
    private var cycleStartTime = 0L

    var onTimerChanged: ((value: Float, max: Float) -> Unit)? = null
    var onGameOver: (() -> Unit)? = null

    // ── Direction de gravité ──────────────────────────────────────────────────
    // Lue depuis l'orientation physique du device, indépendamment du verrouillage
    // de l'affichage. La grille est toujours en portrait, mais les gemmes tombent
    // vers le bas physique réel.
    private enum class GravDir { DOWN, UP, RIGHT, LEFT }
    private var gravDir = GravDir.DOWN

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            // Quand le display est en reverse portrait (ROTATION_180), LEFT/RIGHT sont visuellement
            // inversés — les gemmes doivent tomber dans la direction opposée.
            val flipped = getDisplayRotation() == Surface.ROTATION_180
            gravDir = when {
                orientation < 45 || orientation >= 315 -> GravDir.DOWN
                orientation < 135                      -> if (flipped) GravDir.LEFT else GravDir.RIGHT
                orientation < 225                      -> GravDir.DOWN
                else                                   -> if (flipped) GravDir.RIGHT else GravDir.LEFT
            }
        }
    }

    private fun getDisplayRotation(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                    ?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    }

    init {
        loadBitmaps()
        initGrid()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (orientationListener.canDetectOrientation()) orientationListener.enable()
        soundEngine.start()
    }

    // ── Chargement ────────────────────────────────────────────────────────────
    private fun loadBitmaps() {
        for (i in gemAssetPaths.indices) {
            try {
                context.assets.open(gemAssetPaths[i]).use { stream ->
                    gemBitmaps[i] = BitmapFactory.decodeStream(stream)
                }
            } catch (_: IOException) {}
        }
    }

    private fun rescaleBitmaps() {
        val size = (tileSize * 0.88f).toInt()
        if (size <= 0) return
        for (i in gemBitmaps.indices) {
            val src = gemBitmaps[i] ?: continue
            scaledBitmaps[i] = Bitmap.createScaledBitmap(src, size, size, true)
        }
    }

    private fun initGrid() {
        for (r in 0 until gridRows) for (c in 0 until gridCols) grid[r][c] = randomGemAvoidingMatch(r, c)
    }

    private fun randomGemAvoidingMatch(row: Int, col: Int): Int {
        val forbidden = mutableSetOf<Int>()
        if (col >= 2 && grid[row][col - 1] == grid[row][col - 2]) forbidden.add(grid[row][col - 1])
        if (row >= 2 && grid[row - 1][col] == grid[row - 2][col]) forbidden.add(grid[row - 1][col])
        val available = (0 until GEM_COUNT).filter { it !in forbidden }
        return if (available.isEmpty()) Random.nextInt(GEM_COUNT) else available.random()
    }

    // ── Taille ────────────────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        tileSize = minOf(w.toFloat() / gridCols, h.toFloat() / gridRows)
        gridLeft = (w - tileSize * gridCols) / 2f
        gridTop  = (h - tileSize * gridRows) / 2f
        paintRing.strokeWidth = tileSize * 0.07f
        rescaleBitmaps()
    }

    // ── Dessin ────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()

        // ── Tick timer ────────────────────────────────────────────────────────
        if (!gameOver) {
            if (timerLastTick > 0L) {
                val delta = (now - timerLastTick) / 1000f
                timerValue = (timerValue - delta).coerceAtLeast(0f)
                if (timerValue <= 0f) triggerGameOver()
                onTimerChanged?.invoke(timerValue, timerMax)
                // Pénalité diversité couleurs : toutes les 20 sec sans tout matcher → timerMax -1
                if (cycleStartTime > 0L && now - cycleStartTime >= COLOR_CYCLE_MS) {
                    if (matchedColorsInCycle.size < GEM_COUNT) {
                        timerMax = (timerMax - TIMER_MIN_MAX).coerceAtLeast(TIMER_MIN_MAX)
                        timerValue = timerValue.coerceAtMost(timerMax)
                    }
                    matchedColorsInCycle.clear()
                    cycleStartTime = now
                }
            }
            timerLastTick = now
            if (cycleStartTime == 0L) { cycleStartTime = now; gameStartUptimeMs = now }
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

        val margin  = tileSize * 0.04f
        val radius  = tileSize * 0.18f
        val gemPad  = tileSize * 0.06f

        val anim    = swapAnim
        val swapSet = if (anim != null) setOf(anim.r1 to anim.c1, anim.r2 to anim.c2) else emptySet()
        val popSet  = popCells.map { it.r to it.c }.toSet()

        // Gemmes normales
        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                if ((r to c) in swapSet || (r to c) in popSet || cleared[r][c]) continue
                val isHeld = r == dragRow && c == dragCol
                val scale = if (isHeld) {
                    val t = ((now - dragPressTime).toFloat() / DRAG_SCALE_MS).coerceIn(0f, 1f)
                    1f + (DRAG_SCALE_MAX - 1f) * t
                } else 1f
                drawGem(canvas, grid[r][c], tileX(c), tileY(r), scale, margin, radius, gemPad, 255, isHeld)
            }
        }

        // Gemmes en cours de swap (positions interpolées)
        if (anim != null) {
            val t  = smoothStep(swapProgress)
            val x1 = tileX(anim.c1); val y1 = tileY(anim.r1)
            val x2 = tileX(anim.c2); val y2 = tileY(anim.r2)
            drawGem(canvas, grid[anim.r1][anim.c1], lerp(x1, x2, t), lerp(y1, y2, t), 1f, margin, radius, gemPad, 255, false)
            drawGem(canvas, grid[anim.r2][anim.c2], lerp(x2, x1, t), lerp(y2, y1, t), 1f, margin, radius, gemPad, 255, false)
        }

        // Animations pop
        var hasAnim = false
        val popIter = popCells.iterator()
        while (popIter.hasNext()) {
            val pop = popIter.next()
            val t = ((now - pop.startTime).toFloat() / POP_ANIM_MS).coerceIn(0f, 1f)
            if (t >= 1f) { popIter.remove(); continue }
            hasAnim = true
            val scale = if (t < 0.35f) lerp(1f, 1.28f, t / 0.35f) else lerp(1.28f, 0f, (t - 0.35f) / 0.65f)
            val alpha = ((1f - t) * 255).toInt().coerceIn(0, 255)
            drawGem(canvas, pop.gemType, tileX(pop.c), tileY(pop.r), scale, margin, radius, gemPad, alpha, false)
        }

        // Particules
        val partIter = particles.iterator()
        while (partIter.hasNext()) {
            val p = partIter.next()
            val elapsed = (now - p.startTime).toFloat()
            if (elapsed < 0f) { hasAnim = true; continue }
            val t = elapsed / PARTICLE_LIFE_MS
            if (t >= 1f) { partIter.remove(); continue }
            hasAnim = true
            paintParticle.color  = p.color
            paintParticle.alpha  = ((1f - t) * 230).toInt().coerceIn(0, 255)
            val grav = 0.00014f * elapsed * elapsed
            val px = p.startX + p.vx * elapsed + when (gravDir) {
                GravDir.RIGHT -> grav; GravDir.LEFT -> -grav; else -> 0f
            }
            val py = p.startY + p.vy * elapsed + when (gravDir) {
                GravDir.DOWN -> grav; GravDir.UP -> -grav; else -> 0f
            }
            canvas.drawCircle(px, py, p.radius * (1f - t * 0.5f), paintParticle)
        }

        // Overlay game over
        if (gameOver) drawGameOverOverlay(canvas)

        if (hasAnim || (!gameOver && timerValue > 0f)) postInvalidateOnAnimation()

        if (dragRow != -1 && !swipeTriggered && (now - dragPressTime) < DRAG_SCALE_MS.toLong()) {
            postInvalidateOnAnimation()
        }
    }

    private fun tileX(col: Int) = gridLeft + col * tileSize
    private fun tileY(row: Int) = gridTop  + row * tileSize
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun smoothStep(t: Float) = t * t * (3f - 2f * t)

    private fun drawGem(
        canvas: Canvas, gemType: Int,
        x: Float, y: Float, scale: Float,
        margin: Float, radius: Float, gemPad: Float,
        alpha: Int, showRing: Boolean
    ) {
        val cx = x + tileSize / 2f
        val cy = y + tileSize / 2f
        canvas.drawRoundRect(x + margin, y + margin, x + tileSize - margin, y + tileSize - margin,
            radius, radius, paintCell)

        val half  = tileSize * scale / 2f
        val gx    = cx - half;  val gy    = cy - half
        val gSize = tileSize * scale
        val pad   = gemPad * scale

        val bmp = scaledBitmaps[gemType]
        if (bmp != null) {
            paintGem.alpha = alpha
            canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height),
                RectF(gx + pad, gy + pad, gx + gSize - pad, gy + gSize - pad), paintGem)
        } else {
            paintFallback.color = gemFallbackColors[gemType]
            paintFallback.alpha = alpha
            canvas.drawRoundRect(gx + pad, gy + pad, gx + gSize - pad, gy + gSize - pad,
                radius * scale, radius * scale, paintFallback)
        }

        if (showRing && scale > 1.01f) {
            paintRing.alpha = ((scale - 1f) / (DRAG_SCALE_MAX - 1f) * 255).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(gx + margin * 0.3f, gy + margin * 0.3f,
                gx + gSize - margin * 0.3f, gy + gSize - margin * 0.3f,
                radius * scale, radius * scale, paintRing)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    private val swipeThreshold get() = tileSize * 0.3f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isProcessing) return true
                val col = ((event.x - gridLeft) / tileSize).toInt()
                val row = ((event.y - gridTop)  / tileSize).toInt()
                if (row !in 0 until gridRows || col !in 0 until gridCols) return true
                dragRow = row; dragCol = col
                dragStartX = event.x; dragStartY = event.y
                swipeTriggered = false
                dragPressTime = SystemClock.uptimeMillis()
                postInvalidateOnAnimation()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isProcessing || dragRow == -1 || swipeTriggered) return true
                val dx = event.x - dragStartX; val dy = event.y - dragStartY
                if (abs(dx) < swipeThreshold && abs(dy) < swipeThreshold) return true
                val targetRow: Int; val targetCol: Int
                if (abs(dx) >= abs(dy)) {
                    targetRow = dragRow; targetCol = dragCol + if (dx > 0) 1 else -1
                } else {
                    targetRow = dragRow + if (dy > 0) 1 else -1; targetCol = dragCol
                }
                if (targetRow in 0 until gridRows && targetCol in 0 until gridCols) {
                    swipeTriggered = true
                    val fromRow = dragRow; val fromCol = dragCol
                    dragRow = -1; dragCol = -1
                    isProcessing = true
                    startSwap(fromRow, fromCol, targetRow, targetCol)
                } else {
                    dragRow = -1; dragCol = -1
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragRow = -1; dragCol = -1; swipeTriggered = false
                invalidate()
            }
        }
        return true
    }

    // ── Swap ──────────────────────────────────────────────────────────────────
    private fun startSwap(r1: Int, c1: Int, r2: Int, c2: Int) {
        comboCount = 0
        swapAnimator?.cancel()
        swapAnim = SwapAnim(r1, c1, r2, c2)
        swapProgress = 0f
        swapAnimator = buildSwapAnimator {
            swap(r1, c1, r2, c2)
            val matches = findMatches()
            if (matches.isEmpty()) {
                swapAnim = SwapAnim(r1, c1, r2, c2)
                swapProgress = 0f
                swapAnimator = buildSwapAnimator {
                    swap(r1, c1, r2, c2)
                    swapAnim = null
                    isProcessing = false
                    invalidate()
                }
            } else {
                swapAnim = null
                processMatches(matches)
            }
            invalidate()
        }
    }

    private fun buildSwapAnimator(onEnd: () -> Unit): ValueAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SWAP_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { swapProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }

    // ── Logique ───────────────────────────────────────────────────────────────
    private fun swap(r1: Int, c1: Int, r2: Int, c2: Int) {
        val tmp = grid[r1][c1]; grid[r1][c1] = grid[r2][c2]; grid[r2][c2] = tmp
    }

    private fun findMatches(): Set<Pair<Int, Int>> {
        val matched = mutableSetOf<Pair<Int, Int>>()
        for (r in 0 until gridRows) {
            var c = 0
            while (c < gridCols) {
                var len = 1
                while (c + len < gridCols && grid[r][c + len] == grid[r][c]) len++
                if (len >= MATCH_MIN) for (k in 0 until len) matched.add(r to c + k)
                c += len
            }
        }
        for (c in 0 until gridCols) {
            var r = 0
            while (r < gridRows) {
                var len = 1
                while (r + len < gridRows && grid[r + len][c] == grid[r][c]) len++
                if (len >= MATCH_MIN) for (k in 0 until len) matched.add(r + k to c)
                r += len
            }
        }
        return matched
    }

    private fun processMatches(matches: Set<Pair<Int, Int>>) {
        // Note pentatonique basée sur la gemme majoritaire du match
        val dominant = matches.groupBy { (r, c) -> grid[r][c] }.maxBy { it.value.size }.key
        soundEngine.playMatch(dominant, comboCount)
        comboCount++

        score += matches.size * 10
        onScoreChanged?.invoke(score)
        // +1 sec par match, plafonné au timerMax courant
        timerValue = (timerValue + TIMER_PER_MATCH).coerceAtMost(timerMax)
        // Enregistrer les couleurs matchées pour le cycle diversité
        for ((r, c) in matches) matchedColorsInCycle.add(grid[r][c])
        val now = SystemClock.uptimeMillis()
        for ((r, c) in matches) {
            cleared[r][c] = true
            val cx = tileX(c) + tileSize / 2f
            val cy = tileY(r) + tileSize / 2f
            popCells.add(PopCell(r, c, grid[r][c], cx, cy, now))
            spawnParticles(cx, cy, gemFallbackColors[grid[r][c]], now)
        }
        postInvalidateOnAnimation()
        handler.postDelayed({ applyGravity() }, POP_ANIM_MS + 40L)
    }

    private fun spawnParticles(cx: Float, cy: Float, color: Int, now: Long) {
        val speed      = tileSize * 0.0030f
        val baseRadius = tileSize * 0.075f
        repeat(PARTICLE_COUNT) { i ->
            val angle = (i.toFloat() / PARTICLE_COUNT) * 2f * PI.toFloat() + Random.nextFloat() * 0.7f
            val spd   = speed * (0.4f + Random.nextFloat() * 1.2f)
            particles.add(Particle(
                startX = cx, startY = cy,
                vx = cos(angle) * spd, vy = sin(angle) * spd,
                color = color,
                radius = baseRadius * (0.4f + Random.nextFloat() * 0.7f),
                startTime = now + Random.nextLong(60L)
            ))
        }
    }

    private fun applyGravity() {
        when (gravDir) {
            GravDir.DOWN -> {
                // Chute vers row ROWS-1 ; nouvelles gemmes apparaissent en haut
                for (c in 0 until gridCols) {
                    val kept = mutableListOf<Int>()
                    for (r in gridRows - 1 downTo 0) if (!cleared[r][c]) kept.add(grid[r][c])
                    repeat(gridRows - kept.size) { kept.add(Random.nextInt(GEM_COUNT)) }
                    kept.reverse()
                    for (r in 0 until gridRows) { grid[r][c] = kept[r]; cleared[r][c] = false }
                }
            }
            GravDir.UP -> {
                // Chute vers row 0 ; nouvelles gemmes apparaissent en bas
                for (c in 0 until gridCols) {
                    val kept = mutableListOf<Int>()
                    for (r in 0 until gridRows) if (!cleared[r][c]) kept.add(grid[r][c])
                    while (kept.size < gridRows) kept.add(Random.nextInt(GEM_COUNT))
                    for (r in 0 until gridRows) { grid[r][c] = kept[r]; cleared[r][c] = false }
                }
            }
            GravDir.RIGHT -> {
                // Chute vers col COLS-1 ; nouvelles gemmes apparaissent à gauche
                for (r in 0 until gridRows) {
                    val kept = mutableListOf<Int>()
                    for (c in gridCols - 1 downTo 0) if (!cleared[r][c]) kept.add(grid[r][c])
                    repeat(gridCols - kept.size) { kept.add(Random.nextInt(GEM_COUNT)) }
                    kept.reverse()
                    for (c in 0 until gridCols) { grid[r][c] = kept[c]; cleared[r][c] = false }
                }
            }
            GravDir.LEFT -> {
                // Chute vers col 0 ; nouvelles gemmes apparaissent à droite
                for (r in 0 until gridRows) {
                    val kept = mutableListOf<Int>()
                    for (c in 0 until gridCols) if (!cleared[r][c]) kept.add(grid[r][c])
                    while (kept.size < gridCols) kept.add(Random.nextInt(GEM_COUNT))
                    for (c in 0 until gridCols) { grid[r][c] = kept[c]; cleared[r][c] = false }
                }
            }
        }
        invalidate()
        handler.postDelayed({ checkCascade() }, FALL_DELAY_MS)
    }

    private fun checkCascade() {
        val matches = findMatches()
        if (matches.isNotEmpty()) processMatches(matches) else { comboCount = 0; isProcessing = false }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────
    fun resetGame() {
        handler.removeCallbacksAndMessages(null)
        swapAnimator?.cancel(); swapAnimator = null; swapAnim = null
        popCells.clear(); particles.clear()
        isProcessing = false
        dragRow = -1; dragCol = -1; swipeTriggered = false
        score = 0
        comboCount = 0
        timerMax   = TIMER_START
        timerValue = TIMER_START
        timerLastTick = 0L
        gameStartUptimeMs = 0L
        gameElapsedMs = 0L
        neutrinosEarned = 0
        cycleStartTime = 0L
        matchedColorsInCycle.clear()
        gameOver = false
        for (r in 0 until gridRows) cleared[r].fill(false)
        initGrid()
        onScoreChanged?.invoke(0)
        invalidate()
    }

    private fun triggerGameOver() {
        if (gameOver) return
        gameOver = true
        isProcessing = true
        timerValue = 0f
        handler.removeCallbacksAndMessages(null)
        val prefs = context.getSharedPreferences("match3_save", Context.MODE_PRIVATE)
        gameElapsedMs = if (gameStartUptimeMs > 0L) SystemClock.uptimeMillis() - gameStartUptimeMs else 0L
        neutrinosEarned = (gameElapsedMs / 15_000L).toInt()
        if (neutrinosEarned > 0) NeutrinoRepository(context).addBalance(neutrinosEarned)
        val elapsedMs = gameElapsedMs
        prefs.edit().apply {
            if (score > prefs.getInt("best_score", 0)) putInt("best_score", score)
            if (elapsedMs > prefs.getLong("best_time_ms", 0L)) putLong("best_time_ms", elapsedMs)
            apply()
        }
        onGameOver?.invoke()
        invalidate()
    }

    private fun drawGameOverOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintOverlay)
        paintGameOver.textSize = tileSize * 1.1f
        canvas.drawText(context.getString(R.string.match3_game_over),
            width / 2f, height / 2f - tileSize * 0.3f, paintGameOver)
        paintGameOver.textSize = tileSize * 0.65f
        canvas.drawText(context.getString(R.string.match3_score, score),
            width / 2f, height / 2f + tileSize * 0.9f, paintGameOver)
        if (gameElapsedMs > 0L) {
            val secs = (gameElapsedMs / 1000).toInt()
            val timeStr = "%d:%02d".format(secs / 60, secs % 60)
            canvas.drawText(context.getString(R.string.match3_time, timeStr),
                width / 2f, height / 2f + tileSize * 1.7f, paintGameOver)
        }
        if (neutrinosEarned > 0) {
            canvas.drawText(context.getString(R.string.match3_neutrinos, neutrinosEarned),
                width / 2f, height / 2f + tileSize * 2.6f, paintGameOver)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        swapAnimator?.cancel()
        orientationListener.disable()
        soundEngine.stop()
    }
}
