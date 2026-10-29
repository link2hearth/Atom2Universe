package com.Atom2Universe.app.games.hexrunner

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import kotlin.math.*

class HexRunnerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    /** Faux dès qu'une surface a refusé le canevas matériel : voir [lockFrame]. */
    private var hardwareCanvas = true

    /**
     * Attrape l'image à venir — **sur le processeur graphique**.
     *
     * C'était [SurfaceHolder.lockCanvas], donc un canevas logiciel : le processeur
     * calculait et écrivait lui-même les quatre millions et demi de pixels de l'écran,
     * à chaque image. Mesuré à la tablette sur le jeu Particules, qui souffrait du même
     * mal, cela coûtait un cœur entier et un ampère pendant que la puce graphique
     * restait à deux pour cent ; le basculement a ramené le jeu à trente pour cent d'un
     * cœur et la puce de 53 à 36 degrés. Remplir des surfaces est précisément ce que la
     * carte graphique fait pour rien.
     *
     * [SurfaceHolder.lockHardwareCanvas] ne demande qu'une chose : **tout redessiner à
     * chaque image**, puisque le contenu de l'image précédente n'est pas conservé — ce
     * que cette vue fait déjà, son rendu commençant par repeindre l'écran entier.
     *
     * Le repli logiciel n'est pas de la prudence de principe : une surface peut refuser
     * le canevas matériel, et le jeu doit alors continuer comme avant plutôt que de
     * s'arrêter. Un refus vaut pour toujours, on ne le redemande pas soixante fois par
     * seconde ; une toile nulle, en revanche, veut seulement dire que la surface n'est
     * pas prête, et c'est l'appelant qui patiente.
     */
    private fun lockFrame(): Canvas? {
        if (hardwareCanvas) {
            try {
                return holder.lockHardwareCanvas()
            } catch (_: Throwable) {
                hardwareCanvas = false
            }
        }
        return holder.lockCanvas()
    }

    val game = HexRunnerGame()

    @Volatile private var running = false
    @Volatile private var pendingStart = false
    @Volatile private var pendingNumFaces = 6

    /** Vrai pendant la lecture des règles ou après une sortie de l'appli : la partie est figée. */
    @Volatile var held = false
        set(value) { field = value; wake() }
    private var thread: Thread? = null

    // Le fil de jeu attend ce signal quand rien ne bouge (accueil, fin, partie figée).
    private val wakeSignal = Semaphore(0)
    // Gestes notés par le fil UI et appliqués par le fil de jeu (vrai = vers la gauche).
    private val pendingTurns = ConcurrentLinkedQueue<Boolean>()
    @Volatile private var playing = false
    private var keptAwake = false
    // Neutrinos déjà versés pour la partie en cours (protégé par synchronized(game)).
    private var paidReward = 0
    private val vertexCos = FloatArray(16)
    private val vertexSin = FloatArray(16)
    private var hudSeconds = -1L
    private var hudText = ""

    private val outgoingRings = ArrayDeque<HexRunnerGame.PassedRingSnapshot>(7)

    private var lastSwipeX = 0f

    var onGameOver: (() -> Unit)? = null
    private var gameOverFired = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path  = Path()
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val colBg         = Color.parseColor("#070710")
    private val hsvBuf        = FloatArray(3)
    @Volatile private var colorTime = 0f
    private val colCenter     = Color.parseColor("#0D1A28")
    private val colPlayer     = Color.WHITE
    private val colPlayerGlow = Color.parseColor("#5500E5C0")
    private val colScore      = Color.parseColor("#AAEEDD")

    init { holder.addCallback(this) }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "hex-runner").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) { wake() }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        wake()
        thread?.join(2000)
    }

    override fun run() {
        var lastNanos = System.nanoTime()
        var carryNanos = 0L
        var gameOver = false
        while (running) {
            val now   = System.nanoTime()
            // Les fractions de milliseconde sont reportées à l'image suivante.
            val spentNanos = now - lastNanos + carryNanos
            lastNanos = now
            var dtMs = spentNanos / 1_000_000L
            if (dtMs > 50L) { dtMs = 50L; carryNanos = 0L } else carryNanos = spentNanos - dtMs * 1_000_000L

            synchronized(game) {
                if (pendingStart) {
                    pendingStart = false
                    gameOverFired = false
                    outgoingRings.clear()
                    game.numFaces = pendingNumFaces
                    game.start()
                    paidReward = 0
                }
                while (true) {
                    val left = pendingTurns.poll() ?: break
                    if (game.isRunning && !held) { if (left) game.rotateLeft() else game.rotateRight() }
                }
                if (game.isRunning && !held) {
                    game.update(dtMs)
                    colorTime = (colorTime + dtMs * 0.018f) % 360f
                    if (game.isGameOver && !gameOverFired) {
                        gameOverFired = true
                        gameOver = true
                    }
                }
            }
            if (gameOver) { gameOver = false; onGameOver?.invoke() }

            while (game.justPassedRings.isNotEmpty()) {
                outgoingRings.addFirst(game.justPassedRings.removeFirst())
                if (outgoingRings.size > 6) outgoingRings.removeLast()
            }

            val canvas = lockFrame()
            if (canvas != null) try { drawGame(canvas) } finally { holder.unlockCanvasAndPost(canvas) }
            updateKeepAwake()

            if (idle()) {
                // Scène figée : le fil dort jusqu'au prochain geste, départ ou retour.
                waitForWake()
                lastNanos = System.nanoTime(); carryNanos = 0L
            } else {
                val frameMs = (System.nanoTime() - now) / 1_000_000L
                if (frameMs < 16L) Thread.sleep(16L - frameMs)
            }
        }
        if (keptAwake) { keptAwake = false; post { keepScreenOn = false } }
    }

    private fun idle() = !pendingStart && pendingTurns.isEmpty() && (!game.isRunning || held)

    private fun waitForWake() {
        wakeSignal.drainPermits()
        if (running && idle()) try { wakeSignal.acquire() } catch (_: InterruptedException) {}
    }

    private fun wake() {
        if (wakeSignal.availablePermits() == 0) wakeSignal.release()
    }

    /** L'écran reste allumé pendant une partie en cours seulement. */
    private fun updateKeepAwake() {
        playing = game.isRunning
        val awake = game.isRunning && !held
        if (awake != keptAwake) { keptAwake = awake; post { keepScreenOn = awake } }
    }

    fun startGame(numFaces: Int) {
        pendingNumFaces = numFaces
        pendingStart = true
        held = false
    }

    /** Neutrinos du temps joué pas encore versés : à la fin de partie et en quittant. */
    fun collectUnpaidReward(): Int = synchronized(game) {
        val owed = NeutrinoRewards.perTime(game.score) - paidReward
        if (owed > 0) { paidReward += owed; owed } else 0
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastSwipeX = e.x
                // Une partie figée par une sortie de l'appli reprend d'un toucher.
                if (held && playing) held = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!playing || held) return true
                val dx = e.x - lastSwipeX
                if (abs(dx) > 50f) {
                    pendingTurns.add(dx < 0)
                    lastSwipeX = e.x
                    wake()
                }
            }
        }
        return true
    }

    private fun drawGame(canvas: Canvas) {
        canvas.drawColor(colBg)
        val N = game.ringCount
        if (N == 0) return

        val cx     = width  / 2f
        val cy     = height / 2f
        val pR     = minOf(cx, cy) * 0.88f
        val minR   = pR * 0.05f
        val angle  = computeHexAngle()
        // Sommets du polygone : mêmes angles pour tous les anneaux de l'image.
        val faceDeg = 360.0 / game.numFaces
        for (v in 0..game.numFaces) {
            val a = rad((v + 1) * faceDeg + angle)
            vertexCos[v] = cos(a); vertexSin[v] = sin(a)
        }

        paint.style = Paint.Style.FILL
        paint.color = colCenter
        canvas.drawCircle(cx, cy, minR * 2.5f, paint)

        for (i in (N - 1) downTo 0) {
            val fInner = ringFrac(i, N)
            val fOuter = if (i == 0) 1f + game.scrollProgress / N else ringFrac(i - 1, N)
            val rInner = perspR(fInner, pR, minR)
            val rOuter = perspR(fOuter, pR, minR)
            if (rInner >= rOuter) continue

            val ring      = game.getRing(i)
            val proximity = fInner.coerceIn(0f, 1f)

            hsvBuf[0] = (colorTime + fInner * 360f) % 360f
            hsvBuf[1] = 0.58f
            hsvBuf[2] = 0.90f
            val ringRgb = Color.HSVToColor(hsvBuf)
            val rR = Color.red(ringRgb); val rG = Color.green(ringRgb); val rB = Color.blue(ringRgb)

            for (face in 0 until game.numFaces) {
                buildTrap(face, rInner, rOuter, cx, cy)
                paint.style = Paint.Style.FILL
                val isGapFace = !ring.solid[face]
                paint.color = Color.argb(lerp(40f, 230f, proximity).toInt(), rR, rG, rB)
                canvas.drawPath(path, paint)
                paint.style       = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = Color.argb(lerp(5f, 90f, proximity).toInt(), rR / 2, rG / 2, rB / 2)
                canvas.drawPath(path, paint)
                if (isGapFace) {
                    val rBandOuter = rInner + (rOuter - rInner) * 0.22f
                    buildTrap(face, rInner, rBandOuter, cx, cy)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(lerp(80f, 240f, proximity).toInt(), 210, 30, 30)
                    canvas.drawPath(path, paint)
                }
            }
        }

        // Anneaux sortants : continuent de défiler au-delà de pR jusqu'en dehors de l'écran
        val maxR = sqrt(cx * cx + cy * cy)
        for (k in outgoingRings.indices) {
            val fInner = (game.scrollProgress + N + k) / N
            val fOuter = (game.scrollProgress + N + k + 1) / N
            val rInner = perspR(fInner, pR, minR)
            val rOuter = perspR(fOuter, pR, minR)
            if (rInner > maxR) break
            val snapshot = outgoingRings[k]
            hsvBuf[0] = (colorTime + fInner * 360f) % 360f
            hsvBuf[1] = 0.58f
            hsvBuf[2] = 0.90f
            val ringRgb = Color.HSVToColor(hsvBuf)
            val rR = Color.red(ringRgb); val rG = Color.green(ringRgb); val rB = Color.blue(ringRgb)
            for (face in 0 until game.numFaces) {
                buildTrap(face, rInner, rOuter, cx, cy)
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(230, rR, rG, rB)
                canvas.drawPath(path, paint)
                paint.style       = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = Color.argb(90, rR / 2, rG / 2, rB / 2)
                canvas.drawPath(path, paint)
                if (!snapshot.solid[face]) {
                    val rBandOuter = rInner + (rOuter - rInner) * 0.22f
                    buildTrap(face, rInner, rBandOuter, cx, cy)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(240, 210, 30, 30)
                    canvas.drawPath(path, paint)
                }
            }
        }

        drawPlayer(canvas, cx, cy, pR, angle)
        drawHUD(canvas, cx)
    }

    private fun drawPlayer(canvas: Canvas, cx: Float, cy: Float, pR: Float, hexAngleDeg: Double) {
        val faceDeg    = 360.0 / game.numFaces
        val faceMidRad = rad((game.playerFaceIndex + 1.5) * faceDeg + hexAngleDeg)
        val r  = pR * 0.91f
        val px = cx + r * cos(faceMidRad)
        val py = cy + r * sin(faceMidRad)

        val dx  = cx - px;  val dy  = cy - py
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx  =  dx / len;  val ny  =  dy / len
        val tx  = -ny;        val ty  =  nx

        val s = pR * 0.055f

        paint.style = Paint.Style.FILL
        paint.color = colPlayerGlow
        path.reset()
        path.moveTo(px + nx*s*1.7f,              py + ny*s*1.7f)
        path.lineTo(px - nx*s*0.9f + tx*s*1.4f, py - ny*s*0.9f + ty*s*1.4f)
        path.lineTo(px - nx*s*0.9f - tx*s*1.4f, py - ny*s*0.9f - ty*s*1.4f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = colPlayer
        path.reset()
        path.moveTo(px + nx*s,                   py + ny*s)
        path.lineTo(px - nx*s*0.6f + tx*s*0.9f, py - ny*s*0.6f + ty*s*0.9f)
        path.lineTo(px - nx*s*0.6f - tx*s*0.9f, py - ny*s*0.6f - ty*s*0.9f)
        path.close()
        canvas.drawPath(path, paint)
    }

    /** Trapèze de la face [faceIdx] ; sommets calculés une fois par image dans [drawGame]. */
    private fun buildTrap(
        faceIdx: Int, rInner: Float, rOuter: Float,
        cx: Float, cy: Float
    ) {
        val c1 = vertexCos[faceIdx]; val s1 = vertexSin[faceIdx]
        val c2 = vertexCos[faceIdx + 1]; val s2 = vertexSin[faceIdx + 1]
        path.reset()
        path.moveTo(cx + rInner * c1, cy + rInner * s1)
        path.lineTo(cx + rInner * c2, cy + rInner * s2)
        path.lineTo(cx + rOuter * c2, cy + rOuter * s2)
        path.lineTo(cx + rOuter * c1, cy + rOuter * s1)
        path.close()
    }

    private fun drawHUD(canvas: Canvas, cx: Float) {
        if (!game.isRunning && !game.isGameOver) return
        scorePaint.textSize = width * 0.06f
        scorePaint.color    = colScore
        val secs = game.score / 1000
        if (secs != hudSeconds) { hudSeconds = secs; hudText = formatTime(context, secs) }
        canvas.drawText(hudText, cx, width * 0.10f, scorePaint)
    }

    private fun ringFrac(i: Int, N: Int): Float =
        (game.scrollProgress + N - 1 - i).toFloat() / N

    private fun perspR(f: Float, pR: Float, minR: Float): Float {
        val t = f.coerceAtLeast(0f)
        return minR + (pR - minR) * t * t * t
    }

    private fun computeHexAngle(): Double {
        val faceDeg = 360.0 / game.numFaces
        val target = -game.playerFaceIndex * faceDeg
        val from   = -game.rotationFrom   * faceDeg
        var delta  = target - from
        while (delta >  180.0) delta -= 360.0
        while (delta < -180.0) delta += 360.0
        return from + delta * easeInOut(game.rotationProgress)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun rad(deg: Double) = Math.toRadians(deg).toFloat()

    private fun easeInOut(t: Float): Double {
        val c = t.toDouble()
        return if (c < 0.5) 2.0 * c * c else -1.0 + (4.0 - 2.0 * c) * c
    }

    companion object {
        /** Durée affichée « 1:05 » ou « 42 s », partagée avec l'écran de fin. */
        fun formatTime(context: Context, secs: Long): String {
            val m = secs / 60; val s = secs % 60
            return if (m > 0) context.getString(R.string.hex_runner_time_minutes, m, s)
            else context.getString(R.string.hex_runner_time_seconds, s)
        }
    }
}
