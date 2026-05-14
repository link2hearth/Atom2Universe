package com.Atom2Universe.app.games.blackjack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.random.Random

class BlackjackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var game: BlackjackGame? = null

    var isThinking: Boolean = false
        set(v) {
            field = v
            if (v) startAnimating()
        }

    var widgetMode: Boolean = false

    // ── Paints ──────────────────────────────────────────────────────────────────
    private val bgPaint = Paint()

    private val tableArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#388E3C")
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        alpha = 80
    }

    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFDE7") }
    private val cardShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }

    private val cardRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C62828")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    private val cardBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A2E")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    private val cardBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1A237E") }
    private val cardBackPatternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3949AB")
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
    }

    // Dos de carte : image aléatoire parmi les assets du solitaire
    private var cardBackBitmap: Bitmap? = null
    private var scaledCardBackBitmap: Bitmap? = null
    private var scaledBackW = 0f
    private var scaledBackH = 0f

    private fun ensureCardBackBitmap(cardW: Float, cardH: Float) {
        if (cardBackBitmap == null) {
            val sources = listOf(
                Triple("Assets/Cartes/bonus",  "images",    1..32),
                Triple("Assets/Cartes/bonus1", "imagesbis", 1..32),
                Triple("Assets/Cartes/bonus2", "bonus2",    0..24),
                Triple("Assets/Cartes/bonus3", "bonus2",    1..32),
            )
            val (folder, prefix, range) = sources.random()
            val path = "$folder/$prefix (${range.random()}).jpg"
            try {
                cardBackBitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { }
        }
        val src = cardBackBitmap ?: return
        val w = cardW.roundToInt().coerceAtLeast(1)
        val h = cardH.roundToInt().coerceAtLeast(1)
        if (w != scaledBackW.roundToInt() || h != scaledBackH.roundToInt()) {
            scaledCardBackBitmap?.recycle()
            scaledCardBackBitmap = Bitmap.createScaledBitmap(src, w, h, true)
            scaledBackW = cardW
            scaledBackH = cardH
        }
    }
    private val cardEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val betPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        textAlign = Paint.Align.CENTER
    }
    private val bustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF5350")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66BB6A")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val losePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF5350")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val pushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4AE")
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.parseColor("#FFD700")
    }
    private val humanHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 255, 215, 0)
    }
    private val thinkingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD700") }
    private val bubbleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8A000")
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#1A1A1A")
    }

    // ── Manga bubbles ────────────────────────────────────────────────────────────
    data class MangaBubble(
        val text: String,
        val playerIndex: Int,   // -1 = dealer
        val startMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 2400L,
        val offsetX: Float = 0f
    ) {
        val alpha: Float
            get() {
                val age = (System.currentTimeMillis() - startMs).toFloat() / durationMs
                return when {
                    age >= 1f -> 0f
                    age < 0.12f -> age / 0.12f
                    age > 0.75f -> (1f - age) / 0.25f
                    else -> 1f
                }
            }
        val isExpired: Boolean get() = System.currentTimeMillis() - startMs > durationMs + 100L
    }

    private val bubbles = mutableListOf<MangaBubble>()

    // ── Animation ticker ─────────────────────────────────────────────────────────
    private var animRunning = false
    private val animTick = object : Runnable {
        override fun run() {
            bubbles.removeAll { it.isExpired }
            invalidate()
            if (isThinking || bubbles.isNotEmpty()) postDelayed(this, 16L)
            else animRunning = false
        }
    }

    fun startAnimating() {
        if (!animRunning) {
            animRunning = true
            post(animTick)
        }
    }

    fun addBubble(text: String, playerIndex: Int) {
        val offsetX = Random.nextFloat() * dp(24f) - dp(12f)
        bubbles.add(MangaBubble(text, playerIndex, offsetX = offsetX))
        startAnimating()
    }

    fun refresh() { invalidate() }

    // ── Canvas helpers ────────────────────────────────────────────────────────────
    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    private fun cardSize(): Pair<Float, Float> {
        val g = game ?: return Pair(dp(44f), dp(62f))
        val numPlayers = g.players.size.coerceAtLeast(1)
        val available = width.toFloat() - dp(24f)
        val maxPerPlayer = available / numPlayers
        val cardW = (maxPerPlayer * 0.75f).coerceIn(dp(28f), dp(52f))
        return Pair(cardW, cardW * 1.42f)
    }

    private fun playerAreaCenterX(idx: Int, numPlayers: Int): Float {
        val available = width.toFloat() - dp(8f)
        val areaW = available / numPlayers
        return dp(4f) + areaW * idx + areaW / 2f
    }

    // ── Drawing ───────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        drawBackground(canvas)
        drawDealerArea(canvas, g)
        drawPlayerAreas(canvas, g)
        drawBubbles(canvas, g)
        if (isThinking) drawThinkingDots(canvas, g)
    }

    private fun drawBackground(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = maxOf(width, height) * 0.85f
        bgPaint.shader = RadialGradient(
            cx, cy * 1.3f, radius,
            intArrayOf(Color.parseColor("#2E7D32"), Color.parseColor("#0B2E10")),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Decorative oval table arcs
        val oval1 = RectF(dp(16f), dp(8f), width - dp(16f), height - dp(8f))
        canvas.drawOval(oval1, tableArcPaint)
        tableArcPaint.alpha = 40
        val oval2 = RectF(dp(24f), dp(14f), width - dp(24f), height - dp(14f))
        canvas.drawOval(oval2, tableArcPaint)
        tableArcPaint.alpha = 80
    }

    private fun drawDealerArea(canvas: Canvas, g: BlackjackGame) {
        val dHand = g.dealer.currentHand ?: return
        if (dHand.cards.isEmpty()) return
        val (cardW, cardH) = cardSize()
        val cx = width / 2f
        val cardBottom = cardH + dp(18f)

        drawCardStack(canvas, dHand.cards, cx, cardBottom, cardW, cardH)

        // Value / label
        val v = dHand.displayValue
        val valStr = when {
            dHand.cards.all { it.faceUp } && dHand.isBust -> "BUST"
            dHand.cards.all { it.faceUp } && dHand.isBlackjack -> "BJ ♠"
            dHand.cards.all { it.faceUp } -> v.toString()
            else -> dHand.cards.firstOrNull { it.faceUp }?.let { "${it.rankLabel}${it.suitSymbol}" } ?: "?"
        }
        subtitlePaint.textSize = dp(12f)
        canvas.drawText(valStr, cx, cardBottom + dp(16f), subtitlePaint)

        // Dealer label
        namePaint.textSize = dp(11f)
        namePaint.color = Color.parseColor("#B0BEC5")
        canvas.drawText("DEALER", cx, cardBottom + dp(28f), namePaint)
        namePaint.color = Color.WHITE
    }

    private fun drawPlayerAreas(canvas: Canvas, g: BlackjackGame) {
        val (cardW, cardH) = cardSize()
        val numPlayers = g.players.size.coerceAtLeast(1)
        val available = width.toFloat() - dp(8f)
        val areaW = available / numPlayers
        val cardBottom = height - dp(68f)

        for ((idx, player) in g.players.withIndex()) {
            val hand = player.currentHand ?: continue
            val cx = playerAreaCenterX(idx, numPlayers)
            val isActive = g.phase == GamePhase.PLAYER_TURNS && idx == g.activePlayerIndex

            // Human player background highlight
            if (player.isHuman) {
                val areaX = dp(4f) + areaW * idx
                val rect = RectF(areaX + dp(2f), cardBottom - cardH - dp(6f),
                    areaX + areaW - dp(2f), cardBottom + dp(2f))
                canvas.drawRoundRect(rect, dp(8f), dp(8f), humanHighlightPaint)
            }

            // Active player golden glow
            if (isActive) {
                val alpha = highlightAlpha()
                highlightPaint.alpha = alpha
                highlightPaint.strokeWidth = dp(2.5f)
                val areaX = dp(4f) + areaW * idx
                val rect = RectF(areaX + dp(1f), cardBottom - cardH - dp(7f),
                    areaX + areaW - dp(1f), cardBottom + dp(3f))
                canvas.drawRoundRect(rect, dp(10f), dp(10f), highlightPaint)
                highlightPaint.alpha = 255
            }

            // Draw hands (may have multiple after split)
            for ((hIdx, h) in player.hands.withIndex()) {
                val handCx = if (player.hands.size == 1) cx
                else cx + (hIdx - (player.hands.size - 1) / 2f) * (areaW * 0.38f)

                drawCardStack(canvas, h.cards, handCx, cardBottom, cardW, cardH)

                // Hand value
                val handVal = h.value
                val valStr = when (h.actionState) {
                    ActionState.BUST -> "BUST"
                    ActionState.BLACKJACK -> "BJ ♠"
                    else -> if (h.cards.isNotEmpty()) handVal.toString() else ""
                }
                val vPaint = when (h.actionState) {
                    ActionState.BUST -> bustPaint
                    ActionState.BLACKJACK -> winPaint
                    else -> valuePaint
                }
                vPaint.textSize = dp(12f)
                canvas.drawText(valStr, handCx, cardBottom + dp(15f), vPaint)

                // Bet chips display (masqué en mode widget)
                if (h.bet > 0 && !widgetMode) {
                    betPaint.textSize = dp(10f)
                    canvas.drawText("⚛${h.bet}", handCx, cardBottom + dp(27f), betPaint)
                }

                // Payout result
                h.payoutResult?.let { res ->
                    val (rStr, rPaint) = when (res) {
                        PayoutResult.WIN -> "+${h.bet} ⚛" to winPaint
                        PayoutResult.WIN_BJ -> "+${(h.bet * 1.5f).toInt()} ⚛" to winPaint
                        PayoutResult.LOSE -> "-${h.bet} ⚛" to losePaint
                        PayoutResult.PUSH -> "=" to pushPaint
                    }
                    rPaint.textSize = dp(13f)
                    canvas.drawText(rStr, handCx, cardBottom + dp(40f), rPaint)
                }
            }

            // Player name label (masqué en mode widget)
            if (!widgetMode) {
                val nameLabel = player.name
                namePaint.textSize = dp(11f)
                namePaint.color = if (player.isHuman) Color.parseColor("#FFD700") else Color.WHITE
                namePaint.typeface = if (player.isHuman) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                canvas.drawText(nameLabel, cx, height - dp(50f), namePaint)
                namePaint.color = Color.WHITE
                namePaint.typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun highlightAlpha(): Int {
        val t = System.currentTimeMillis() % 1000L
        return (sin(t * Math.PI * 2 / 1000.0) * 80 + 175).toInt().coerceIn(95, 255)
    }

    private fun drawCardStack(
        canvas: Canvas,
        cards: List<BlackjackCard>,
        centerX: Float,
        baseline: Float,
        cardW: Float,
        cardH: Float
    ) {
        if (cards.isEmpty()) {
            val x = centerX - cardW / 2f
            val rect = RectF(x, baseline - cardH, x + cardW, baseline)
            canvas.drawRoundRect(rect, dp(4f), dp(4f), cardEmptyPaint)
            return
        }
        val overlap = when {
            cards.size <= 2 -> dp(2f)
            cards.size <= 4 -> cardW * 0.22f
            else -> cardW * 0.38f
        }
        val totalW = cardW + (cards.size - 1) * (cardW - overlap)
        var x = centerX - totalW / 2f
        for (card in cards) {
            drawCard(canvas, card, x, baseline - cardH, cardW, cardH)
            x += cardW - overlap
        }
    }

    private fun drawCard(canvas: Canvas, card: BlackjackCard, x: Float, y: Float, w: Float, h: Float) {
        val rx = dp(4f)
        val rect = RectF(x, y, x + w, y + h)

        // Shadow
        val sRect = RectF(x + dp(1.5f), y + dp(1.5f), x + w + dp(1.5f), y + h + dp(1.5f))
        canvas.drawRoundRect(sRect, rx, rx, cardShadowPaint)

        if (!card.faceUp) {
            ensureCardBackBitmap(w, h)
            val bmp = scaledCardBackBitmap
            canvas.save()
            val clipPath = Path().apply { addRoundRect(rect, rx, rx, Path.Direction.CW) }
            canvas.clipPath(clipPath)
            if (bmp != null && !bmp.isRecycled) {
                canvas.drawBitmap(bmp, x, y, null)
            } else {
                // Fallback dessiné si l'image n'est pas disponible
                canvas.drawRoundRect(rect, rx, rx, cardBackPaint)
                val step = w * 0.28f
                val m = dp(3f)
                var px = x + m
                while (px <= x + w - m) { canvas.drawLine(px, y + m, px, y + h - m, cardBackPatternPaint); px += step }
                var py = y + m
                while (py <= y + h - m) { canvas.drawLine(x + m, py, x + w - m, py, cardBackPatternPaint); py += step }
            }
            canvas.restore()
            // Bordure
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E0E0E0"); style = Paint.Style.STROKE; strokeWidth = dp(0.5f)
            }
            canvas.drawRoundRect(rect, rx, rx, borderPaint)
            return
        }

        // Card face
        canvas.drawRoundRect(rect, rx, rx, cardBgPaint)

        // Thin border
        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = dp(0.5f)
        }
        canvas.drawRoundRect(rect, rx, rx, borderP)

        val textPaint = if (card.isRed) cardRedPaint else cardBlackPaint
        val smallSz = h * 0.195f
        val bigSz = h * 0.36f
        val margin = dp(3f)

        // Top-left rank
        textPaint.textSize = smallSz
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(card.rankLabel, x + margin, y + smallSz + margin, textPaint)
        // Top-left suit (smaller)
        textPaint.textSize = smallSz * 0.82f
        canvas.drawText(card.suitSymbol, x + margin + dp(1f),
            y + smallSz + margin + smallSz * 0.82f, textPaint)

        // Center large suit
        textPaint.textSize = bigSz
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(card.suitSymbol, x + w / 2f, y + h * 0.62f, textPaint)

        // Bottom-right (rotated 180°)
        canvas.save()
        canvas.rotate(180f, x + w / 2f, y + h / 2f)
        textPaint.textSize = smallSz
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(card.rankLabel, x + margin, y + smallSz + margin, textPaint)
        textPaint.textSize = smallSz * 0.82f
        canvas.drawText(card.suitSymbol, x + margin + dp(1f),
            y + smallSz + margin + smallSz * 0.82f, textPaint)
        canvas.restore()
    }

    private fun drawBubbles(canvas: Canvas, g: BlackjackGame) {
        val (_, cardH) = cardSize()
        val numPlayers = g.players.size.coerceAtLeast(1)
        val cardBottom = height - dp(68f)

        for (bubble in bubbles) {
            val alpha = bubble.alpha
            if (alpha < 0.01f) continue
            val a = (alpha * 255).toInt()

            val centerX: Float
            val bottomY: Float
            if (bubble.playerIndex < 0) {
                centerX = width / 2f + bubble.offsetX
                bottomY = cardH + dp(18f) + dp(44f)
            } else {
                centerX = playerAreaCenterX(bubble.playerIndex, numPlayers) + bubble.offsetX
                bottomY = cardBottom - cardH - dp(18f)
            }

            val bW = dp(56f)
            val bH = dp(26f)
            val bRect = RectF(centerX - bW / 2f, bottomY - bH, centerX + bW / 2f, bottomY)

            // Bubble bg
            bubbleBgPaint.color = Color.argb(a, 255, 238, 40)
            canvas.drawRoundRect(bRect, dp(8f), dp(8f), bubbleBgPaint)

            // Tail
            val tailPath = Path().apply {
                moveTo(centerX - dp(5f), bottomY)
                lineTo(centerX + dp(5f), bottomY)
                lineTo(centerX, bottomY + dp(9f))
                close()
            }
            canvas.drawPath(tailPath, bubbleBgPaint)

            // Border
            bubbleBorderPaint.alpha = a
            canvas.drawRoundRect(bRect, dp(8f), dp(8f), bubbleBorderPaint)

            // Text
            bubbleTextPaint.textSize = dp(14f)
            bubbleTextPaint.color = Color.argb(a, 25, 25, 25)
            canvas.drawText(bubble.text, centerX, bottomY - dp(7f), bubbleTextPaint)
        }
    }

    private fun drawThinkingDots(canvas: Canvas, g: BlackjackGame) {
        val numPlayers = g.players.size.coerceAtLeast(1)
        val (_, cardH) = cardSize()
        val cardBottom = height - dp(68f)
        val activeIdx = g.activePlayerIndex
        if (activeIdx < 0 || activeIdx >= numPlayers) return

        val cx = playerAreaCenterX(activeIdx, numPlayers)
        val dotY = cardBottom - cardH - dp(10f)
        val t = System.currentTimeMillis()

        for (i in 0..2) {
            val phase = ((t + i * 250L) % 750L).toFloat() / 750f
            val scale = (sin(phase * Math.PI * 2).toFloat() + 1f) / 2f
            val radius = dp(3.5f) * (0.5f + scale * 0.5f)
            val dotX = cx + (i - 1) * dp(11f)
            thinkingPaint.alpha = (scale * 180 + 75).toInt()
            canvas.drawCircle(dotX, dotY, radius, thinkingPaint)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(animTick)
        super.onDetachedFromWindow()
    }
}
