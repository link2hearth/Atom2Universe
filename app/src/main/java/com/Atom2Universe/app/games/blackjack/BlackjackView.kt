package com.Atom2Universe.app.games.blackjack

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.cards.CardPainter
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.PlayingCard
import com.Atom2Universe.app.games.kit.KitPalette
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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

    /** Le mode widget (cadre sombre fixe) garde un tapis sombre ; sinon tout vient du thème. */
    var widgetMode: Boolean = false
        set(value) {
            field = value
            palette = if (value) KitPalette.artwork(0xFF66BB6A.toInt()) else KitPalette.from(context)
        }

    /** Les couleurs viennent du thème, comme dans les autres jeux : rien n'est figé ici. */
    var palette: KitPalette = KitPalette.from(context)
        set(value) { field = value; painter.palette = value; applyPalette(); invalidate() }

    /** Le dessin des cartes, commun à tous les jeux de cartes (faces, dos choisis dans les options). */
    private val painter = CardPainter(context).also { it.palette = palette }

    // ── Paints ──────────────────────────────────────────────────────────────────
    private val tableArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val cardEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val betPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val losePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val pushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val humanHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thinkingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    init { applyPalette() }

    private fun applyPalette() {
        tableArcPaint.color = palette.text
        cardEmptyPaint.color = palette.withAlpha(palette.text, 0.28f)
        namePaint.color = palette.text
        subtitlePaint.color = palette.secondary
        valuePaint.color = palette.text
        betPaint.color = palette.ink
        bustPaint.color = palette.error
        winPaint.color = palette.success
        losePaint.color = palette.error
        pushPaint.color = palette.tertiary
        highlightPaint.color = palette.accent
        humanHighlightPaint.color = palette.withAlpha(palette.accent, 0.12f)
        thinkingPaint.color = palette.accent
        bubbleBorderPaint.color = palette.accentDim
        bubbleTextPaint.color = palette.onAccent
    }

    private val bustLabel = context.getString(R.string.blackjack_bust)
    private val naturalLabel = context.getString(R.string.blackjack_natural)
    private val dealerLabel = context.getString(R.string.blackjack_dealer)

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

    // ── Card size functions ───────────────────────────────────────────────────────

    /** Widget mode : tous les joueurs partagent la largeur. */
    private fun cardSize(): Pair<Float, Float> {
        val g = game ?: return Pair(dp(44f), dp(62f))
        val numPlayers = g.players.size.coerceAtLeast(1)
        val available = width.toFloat() - dp(24f)
        val maxPerPlayer = available / numPlayers
        val cardW = (maxPerPlayer * 0.75f).coerceIn(dp(28f), dp(52f))
        return Pair(cardW, cardW * 1.42f)
    }

    /** Plein écran : cartes du croupier. */
    private fun dealerCardSizeFull(): Pair<Float, Float> {
        val cardW = (width * 0.19f).coerceIn(dp(44f), dp(72f))
        return Pair(cardW, cardW * 1.42f)
    }

    /** Plein écran : cartes des IA (partagent la largeur entre elles). */
    private fun aiCardSizeFull(): Pair<Float, Float> {
        val g = game ?: return Pair(dp(36f), dp(50f))
        val numAI = g.aiPlayers.size.coerceAtLeast(1)
        val available = width.toFloat() - dp(16f)
        val cardW = (available / numAI * 0.60f).coerceIn(dp(26f), dp(46f))
        return Pair(cardW, cardW * 1.42f)
    }

    /** Plein écran : cartes du joueur humain — grand format, indépendant des IA. */
    private fun humanCardSizeFull(): Pair<Float, Float> {
        val numHands = (game?.humanPlayer?.hands?.size ?: 1).coerceAtLeast(1)
        val cardW = when (numHands) {
            1 -> (width * 0.24f).coerceIn(dp(58f), dp(92f))
            2 -> (width * 0.20f).coerceIn(dp(46f), dp(78f))
            else -> (width * 0.17f).coerceIn(dp(36f), dp(64f))
        }
        return Pair(cardW, cardW * 1.42f)
    }

    // ── Layout helpers ────────────────────────────────────────────────────────────
    private fun playerAreaCenterX(idx: Int, numPlayers: Int): Float {
        val available = width.toFloat() - dp(8f)
        val areaW = available / numPlayers
        return dp(4f) + areaW * idx + areaW / 2f
    }

    private fun dealerBaselineY(): Float {
        val (_, dH) = dealerCardSizeFull()
        return dH + dp(20f)
    }

    private fun aiBaselineY(): Float = height * 0.60f

    private fun humanBaselineY(): Float = height - dp(72f)

    // ── Drawing ───────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        drawBackground(canvas)
        drawDealerArea(canvas, g)
        drawPlayerAreas(canvas, g)
        drawBubbles(canvas, g)
        if (isThinking) drawThinkingDots(canvas, g)
    }

    /** Pas de tapis vert : l'écran garde le fond du thème, et deux ovales discrets dessinent la table. */
    private fun drawBackground(canvas: Canvas) {
        tableArcPaint.alpha = 36
        canvas.drawOval(RectF(dp(16f), dp(8f), width - dp(16f), height - dp(8f)), tableArcPaint)
        tableArcPaint.alpha = 18
        canvas.drawOval(RectF(dp(24f), dp(14f), width - dp(24f), height - dp(14f)), tableArcPaint)
    }

    private fun drawDealerArea(canvas: Canvas, g: BlackjackGame) {
        val dHand = g.dealer.currentHand ?: return
        if (dHand.cards.isEmpty()) return

        val (cardW, cardH) = if (widgetMode) cardSize() else dealerCardSizeFull()
        val cx = width / 2f
        val cardBottom = if (widgetMode) cardH + dp(18f) else dealerBaselineY()

        drawCardStack(canvas, dHand.cards, cx, cardBottom, cardW, cardH)

        // Value / label
        val v = dHand.displayValue
        val valStr = when {
            dHand.cards.all { it.faceUp } && dHand.isBust -> bustLabel
            dHand.cards.all { it.faceUp } && dHand.isBlackjack -> naturalLabel
            dHand.cards.all { it.faceUp } -> v.toString()
            else -> dHand.cards.firstOrNull { it.faceUp }?.let { "${it.rankLabel}${it.suitSymbol}" } ?: "?"
        }
        val labelSize = if (widgetMode) dp(12f) else dp(14f)
        subtitlePaint.textSize = labelSize
        canvas.drawText(valStr, cx, cardBottom + dp(16f), subtitlePaint)

        namePaint.textSize = if (widgetMode) dp(11f) else dp(13f)
        namePaint.color = palette.secondary
        canvas.drawText(dealerLabel, cx, cardBottom + dp(30f), namePaint)
        namePaint.color = palette.text
    }

    private fun drawPlayerAreas(canvas: Canvas, g: BlackjackGame) {
        if (widgetMode) {
            drawPlayerAreasCompact(canvas, g)
        } else {
            drawHumanZone(canvas, g)
            if (g.aiPlayers.isNotEmpty()) drawAIZone(canvas, g)
        }
    }

    // ── Widget / compact mode : tous les joueurs sur une ligne ────────────────────
    private fun drawPlayerAreasCompact(canvas: Canvas, g: BlackjackGame) {
        val (cardW, cardH) = cardSize()
        val numPlayers = g.players.size.coerceAtLeast(1)
        val available = width.toFloat() - dp(8f)
        val areaW = available / numPlayers
        val cardBottom = height - dp(68f)

        for ((idx, player) in g.players.withIndex()) {
            player.currentHand ?: continue
            val cx = playerAreaCenterX(idx, numPlayers)
            val isActive = g.phase == GamePhase.PLAYER_TURNS && idx == g.activePlayerIndex

            if (player.isHuman) {
                val areaX = dp(4f) + areaW * idx
                val rect = RectF(areaX + dp(2f), cardBottom - cardH - dp(6f),
                    areaX + areaW - dp(2f), cardBottom + dp(2f))
                canvas.drawRoundRect(rect, dp(8f), dp(8f), humanHighlightPaint)
            }

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

            for ((hIdx, h) in player.hands.withIndex()) {
                val handCx = if (player.hands.size == 1) cx
                else cx + (hIdx - (player.hands.size - 1) / 2f) * (areaW * 0.38f)

                drawCardStack(canvas, h.cards, handCx, cardBottom, cardW, cardH)

                val handVal = h.value
                val valStr = when (h.actionState) {
                    ActionState.BUST -> bustLabel
                    ActionState.BLACKJACK -> naturalLabel
                    else -> if (h.cards.isNotEmpty()) handVal.toString() else ""
                }
                val vPaint = when (h.actionState) {
                    ActionState.BUST -> bustPaint
                    ActionState.BLACKJACK -> winPaint
                    else -> valuePaint
                }
                vPaint.textSize = dp(12f)
                canvas.drawText(valStr, handCx, cardBottom + dp(15f), vPaint)

                if (h.bet > 0 && !widgetMode) {
                    betPaint.textSize = dp(10f)
                    canvas.drawText("⚛${h.bet}", handCx, cardBottom + dp(27f), betPaint)
                }

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

            if (!widgetMode) {
                namePaint.textSize = dp(11f)
                namePaint.color = if (player.isHuman) palette.ink else palette.text
                namePaint.typeface = if (player.isHuman) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                canvas.drawText(player.name, cx, height - dp(50f), namePaint)
                namePaint.color = palette.text
                namePaint.typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    // ── Plein écran : joueur humain (zone du bas) ─────────────────────────────────
    private fun drawHumanZone(canvas: Canvas, g: BlackjackGame) {
        val (cardW, cardH) = humanCardSizeFull()
        val cardBottom = humanBaselineY()
        val cx = width / 2f
        val humanPlayer = g.humanPlayer
        val isHumanActive = g.phase == GamePhase.PLAYER_TURNS && g.activePlayer?.isHuman == true

        // Fond highlight
        val hLeftEdge = (cx - cardW * 1.3f).coerceAtLeast(dp(4f))
        val hRightEdge = (cx + cardW * 1.3f).coerceAtMost(width - dp(4f))
        val hRect = RectF(hLeftEdge, cardBottom - cardH - dp(8f), hRightEdge, cardBottom + dp(4f))
        canvas.drawRoundRect(hRect, dp(12f), dp(12f), humanHighlightPaint)

        if (isHumanActive) {
            val alpha = highlightAlpha()
            highlightPaint.alpha = alpha
            highlightPaint.strokeWidth = dp(3f)
            val gRect = RectF(hRect.left - dp(2f), hRect.top - dp(2f), hRect.right + dp(2f), hRect.bottom + dp(2f))
            canvas.drawRoundRect(gRect, dp(14f), dp(14f), highlightPaint)
            highlightPaint.alpha = 255
        }

        for ((hIdx, hand) in humanPlayer.hands.withIndex()) {
            val handCx = if (humanPlayer.hands.size == 1) cx
            else {
                val spacing = cardW * 1.15f
                cx + (hIdx - (humanPlayer.hands.size - 1) / 2f) * spacing
            }

            drawCardStack(canvas, hand.cards, handCx, cardBottom, cardW, cardH)

            val valStr = when (hand.actionState) {
                ActionState.BUST -> bustLabel
                ActionState.BLACKJACK -> naturalLabel
                else -> if (hand.cards.isNotEmpty()) hand.value.toString() else ""
            }
            val vPaint = when (hand.actionState) {
                ActionState.BUST -> bustPaint
                ActionState.BLACKJACK -> winPaint
                else -> valuePaint
            }
            vPaint.textSize = dp(14f)
            canvas.drawText(valStr, handCx, cardBottom - cardH - dp(6f), vPaint)

            if (hand.bet > 0) {
                betPaint.textSize = dp(12f)
                canvas.drawText("⚛${hand.bet}", handCx, cardBottom + dp(33f), betPaint)
            }

            hand.payoutResult?.let { res ->
                val (rStr, rPaint) = when (res) {
                    PayoutResult.WIN -> "+${hand.bet} ⚛" to winPaint
                    PayoutResult.WIN_BJ -> "+${(hand.bet * 1.5f).toInt()} ⚛" to winPaint
                    PayoutResult.LOSE -> "-${hand.bet} ⚛" to losePaint
                    PayoutResult.PUSH -> "=" to pushPaint
                }
                rPaint.textSize = dp(14f)
                canvas.drawText(rStr, handCx, cardBottom + dp(49f), rPaint)
            }
        }

        namePaint.textSize = dp(12f)
        namePaint.color = palette.ink
        namePaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(humanPlayer.name, cx, height - dp(48f), namePaint)
        namePaint.color = palette.text
        namePaint.typeface = Typeface.DEFAULT_BOLD
    }

    // ── Plein écran : joueurs IA (zone du milieu) ─────────────────────────────────
    private fun drawAIZone(canvas: Canvas, g: BlackjackGame) {
        val (cardW, cardH) = aiCardSizeFull()
        val cardBottom = aiBaselineY()
        val numAI = g.aiPlayers.size
        val areaW = (width.toFloat() - dp(8f)) / numAI

        for ((idx, ai) in g.aiPlayers.withIndex()) {
            if (ai.hands.isEmpty()) continue
            val cx = playerAreaCenterX(idx, numAI)
            val isActive = g.phase == GamePhase.PLAYER_TURNS && idx == g.activePlayerIndex

            if (isActive) {
                val alpha = highlightAlpha()
                highlightPaint.alpha = alpha
                highlightPaint.strokeWidth = dp(2f)
                val areaX = dp(4f) + areaW * idx
                val rect = RectF(areaX + dp(2f), cardBottom - cardH - dp(6f),
                    areaX + areaW - dp(2f), cardBottom + dp(2f))
                canvas.drawRoundRect(rect, dp(8f), dp(8f), highlightPaint)
                highlightPaint.alpha = 255
            }

            for ((hIdx, hand) in ai.hands.withIndex()) {
                val handCx = if (ai.hands.size == 1) cx
                else cx + (hIdx - (ai.hands.size - 1) / 2f) * (areaW * 0.38f)

                drawCardStack(canvas, hand.cards, handCx, cardBottom, cardW, cardH)

                val valStr = when (hand.actionState) {
                    ActionState.BUST -> bustLabel
                    ActionState.BLACKJACK -> naturalLabel
                    else -> if (hand.cards.isNotEmpty()) hand.value.toString() else ""
                }
                val vPaint = when (hand.actionState) {
                    ActionState.BUST -> bustPaint
                    ActionState.BLACKJACK -> winPaint
                    else -> valuePaint
                }
                vPaint.textSize = dp(11f)
                canvas.drawText(valStr, handCx, cardBottom + dp(14f), vPaint)

                if (hand.bet > 0) {
                    betPaint.textSize = dp(10f)
                    canvas.drawText("⚛${hand.bet}", handCx, cardBottom + dp(26f), betPaint)
                }

                hand.payoutResult?.let { res ->
                    val (rStr, rPaint) = when (res) {
                        PayoutResult.WIN -> "+${hand.bet} ⚛" to winPaint
                        PayoutResult.WIN_BJ -> "+${(hand.bet * 1.5f).toInt()} ⚛" to winPaint
                        PayoutResult.LOSE -> "-${hand.bet} ⚛" to losePaint
                        PayoutResult.PUSH -> "=" to pushPaint
                    }
                    rPaint.textSize = dp(11f)
                    canvas.drawText(rStr, handCx, cardBottom + dp(38f), rPaint)
                }
            }

            namePaint.textSize = dp(10f)
            namePaint.color = palette.text
            namePaint.typeface = Typeface.DEFAULT
            canvas.drawText(ai.name, cx, cardBottom + dp(50f), namePaint)
            namePaint.color = palette.text
            namePaint.typeface = Typeface.DEFAULT_BOLD
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
        val rect = RectF(x, y, x + w, y + h)
        if (!card.faceUp) painter.drawBack(canvas, rect) else painter.drawFace(canvas, playingCard(card), rect)
    }

    private fun playingCard(card: BlackjackCard): PlayingCard = PlayingCard.of(
        when (card.suit) {
            0 -> CardSuit.SPADES
            1 -> CardSuit.HEARTS
            2 -> CardSuit.DIAMONDS
            else -> CardSuit.CLUBS
        }, card.rank)

    // ── Bulles manga ──────────────────────────────────────────────────────────────
    private fun drawBubbles(canvas: Canvas, g: BlackjackGame) {
        for (bubble in bubbles) {
            val alpha = bubble.alpha
            if (alpha < 0.01f) continue
            val a = (alpha * 255).toInt()

            val centerX: Float
            val bottomY: Float
            if (widgetMode) {
                // Mode widget : positions compactes
                val (_, cardH) = cardSize()
                val numPlayers = g.players.size.coerceAtLeast(1)
                val cardBottom = height - dp(68f)
                if (bubble.playerIndex < 0) {
                    centerX = width / 2f + bubble.offsetX
                    bottomY = cardH + dp(18f) + dp(44f)
                } else {
                    centerX = playerAreaCenterX(bubble.playerIndex, numPlayers) + bubble.offsetX
                    bottomY = cardBottom - cardH - dp(18f)
                }
            } else {
                // Mode plein écran : positions par zone
                val numAI = g.aiPlayers.size
                when {
                    bubble.playerIndex < 0 -> {
                        // Croupier
                        val (_, dCardH) = dealerCardSizeFull()
                        centerX = width / 2f + bubble.offsetX
                        bottomY = dealerBaselineY() + dCardH * 0.1f + dp(44f)
                    }
                    bubble.playerIndex < numAI -> {
                        // IA
                        val (_, aiCardH) = aiCardSizeFull()
                        centerX = playerAreaCenterX(bubble.playerIndex, numAI) + bubble.offsetX
                        bottomY = aiBaselineY() - aiCardH - dp(12f)
                    }
                    else -> {
                        // Humain
                        val (_, hCardH) = humanCardSizeFull()
                        centerX = width / 2f + bubble.offsetX
                        bottomY = humanBaselineY() - hCardH - dp(12f)
                    }
                }
            }

            val bW = dp(56f)
            val bH = dp(26f)
            val bRect = RectF(centerX - bW / 2f, bottomY - bH, centerX + bW / 2f, bottomY)

            bubbleBgPaint.color = palette.withAlpha(palette.accent, alpha)
            canvas.drawRoundRect(bRect, dp(8f), dp(8f), bubbleBgPaint)

            val tailPath = Path().apply {
                moveTo(centerX - dp(5f), bottomY)
                lineTo(centerX + dp(5f), bottomY)
                lineTo(centerX, bottomY + dp(9f))
                close()
            }
            canvas.drawPath(tailPath, bubbleBgPaint)

            bubbleBorderPaint.color = palette.withAlpha(palette.accentDim, alpha)
            canvas.drawRoundRect(bRect, dp(8f), dp(8f), bubbleBorderPaint)

            bubbleTextPaint.textSize = dp(14f)
            bubbleTextPaint.color = palette.withAlpha(palette.onAccent, alpha)
            canvas.drawText(bubble.text, centerX, bottomY - dp(7f), bubbleTextPaint)
        }
    }

    private fun drawThinkingDots(canvas: Canvas, g: BlackjackGame) {
        val activeIdx = g.activePlayerIndex
        val numAI = g.aiPlayers.size

        val cx: Float
        val dotY: Float
        if (widgetMode) {
            val numPlayers = g.players.size.coerceAtLeast(1)
            val (_, cardH) = cardSize()
            if (activeIdx < 0 || activeIdx >= numPlayers) return
            cx = playerAreaCenterX(activeIdx, numPlayers)
            dotY = height - dp(68f) - cardH - dp(10f)
        } else {
            if (activeIdx < numAI) {
                // IA active
                if (activeIdx < 0 || numAI == 0) return
                val (_, aiCardH) = aiCardSizeFull()
                cx = playerAreaCenterX(activeIdx, numAI)
                dotY = aiBaselineY() - aiCardH - dp(10f)
            } else {
                // Humain actif
                val (_, hCardH) = humanCardSizeFull()
                cx = width / 2f
                dotY = humanBaselineY() - hCardH - dp(10f)
            }
        }

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
