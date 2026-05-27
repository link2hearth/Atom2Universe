package com.Atom2Universe.app.games.solitaire

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.io.IOException

/**
 * Custom View for rendering the Solitaire game board.
 */
class SolitaireView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnGameActionListener {
        fun onStockClicked()
        fun onCardClicked(pileType: PileType, pileIndex: Int, cardIndex: Int)
        fun onPileClicked(pileType: PileType, pileIndex: Int)
        fun onCardDoubleTapped(card: Card, pileType: PileType, pileIndex: Int)
        fun onCardDragged(
            sourcePileType: PileType,
            sourcePileIndex: Int,
            sourceCardIndex: Int,
            targetPileType: PileType,
            targetPileIndex: Int
        ): Boolean
    }

    var listener: OnGameActionListener? = null
    var game: SolitaireGame? = null
        set(value) {
            field = value
            invalidate()
        }

    // Card dimensions (calculated based on view size)
    private var cardWidth = 0f
    private var cardHeight = 0f
    private var cardSpacing = 0f
    private var tableauOffset = 0f // Vertical offset between cards in tableau
    private var tableauFaceDownOffset = 0f

    // Layout positions
    private var topRowY = 0f
    private var tableauY = 0f

    // Card back image
    private var cardBackBitmap: Bitmap? = null
    private var scaledCardBackBitmap: Bitmap? = null

    // Sources de fonds de cartes : (dossier, préfixe, premier index, dernier index)
    private data class CardBackSource(val folder: String, val prefix: String, val first: Int, val last: Int) {
        fun randomPath(): String {
            val n = (first..last).random()
            return "$folder/$prefix ($n).jpg"
        }
    }
    private val cardBackSources = listOf(
        CardBackSource("Assets/Cartes/bonus",  "images",    1, 32),
        CardBackSource("Assets/Cartes/bonus1", "imagesbis", 1, 32),
        CardBackSource("Assets/Cartes/bonus2", "bonus2",    0, 24),
        CardBackSource("Assets/Cartes/bonus3", "bonus2",    1, 32),
    )

    // Paints
    private val cardBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        style = Paint.Style.FILL
    }

    private val cardFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL
    }

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val cardSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val emptyPilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }

    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }

    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val cardBackPattern = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D47A1")
    }

    // Double tap detection
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private val doubleTapTimeout = 300L
    private val doubleTapSlop = 50f

    // Drag and drop state
    private var isDragging = false
    private var dragCards: List<Card> = emptyList()
    private var dragSourcePileType: PileType? = null
    private var dragSourcePileIndex = 0
    private var dragSourceCardIndex = 0
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var currentDragX = 0f
    private var currentDragY = 0f
    private val dragThreshold = 20f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var hasMoved = false

    // Victory animation
    private data class AnimatedCard(
        val card: Card,
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val spawnTime: Long
    )

    private var isVictoryAnimating = false
    private val animatedCards = mutableListOf<AnimatedCard>()
    private var nextCardToAnimate = 0
    private var lastAnimFrameTime = 0L
    private var lastCardSpawnTime = 0L
    private val gravity = 0.8f
    private val bounceFactor = 0.7f
    private val animHandler = Handler(Looper.getMainLooper())

    // Animation timing constants
    private val cardSpawnInterval = 300L   // New card every 300ms
    private val cardLifespan = 4500L       // Each card lives 4.5 seconds
    private val maxAnimatedCards = 15      // Max 15 cards on screen

    private val animRunnable = object : Runnable {
        override fun run() {
            if (!isVictoryAnimating) return

            val currentTime = System.currentTimeMillis()
            val deltaTime = if (lastAnimFrameTime == 0L) 16f else (currentTime - lastAnimFrameTime).toFloat()
            lastAnimFrameTime = currentTime

            // Add new card every 300ms if under limit and cards remain
            val g = game
            if (g != null && nextCardToAnimate < 52 && animatedCards.size < maxAnimatedCards) {
                if (currentTime - lastCardSpawnTime >= cardSpawnInterval) {
                    addNextAnimatedCard(g, currentTime)
                    lastCardSpawnTime = currentTime
                }
            }

            // Update physics for all cards
            val iterator = animatedCards.iterator()
            while (iterator.hasNext()) {
                val ac = iterator.next()

                // Remove cards that exceeded their lifespan
                if (currentTime - ac.spawnTime >= cardLifespan) {
                    iterator.remove()
                    continue
                }

                // Apply gravity
                ac.vy += gravity

                // Update position
                ac.x += ac.vx * (deltaTime / 16f)
                ac.y += ac.vy * (deltaTime / 16f)

                // Bounce off bottom
                if (ac.y + cardHeight > height) {
                    ac.y = height - cardHeight
                    ac.vy = -ac.vy * bounceFactor
                    ac.vx *= 0.95f
                }

                // Bounce off sides
                if (ac.x < 0) {
                    ac.x = 0f
                    ac.vx = -ac.vx * bounceFactor
                } else if (ac.x + cardWidth > width) {
                    ac.x = width - cardWidth
                    ac.vx = -ac.vx * bounceFactor
                }
            }

            invalidate()

            // Continue if still animating
            if (animatedCards.isNotEmpty() || nextCardToAnimate < 52) {
                animHandler.postDelayed(this, 16) // ~60fps
            } else {
                isVictoryAnimating = false
            }
        }
    }

    private fun addNextAnimatedCard(g: SolitaireGame, currentTime: Long) {
        // Find next card from foundations
        for (i in 0 until 4) {
            val foundation = g.foundations[i]
            if (foundation.isNotEmpty()) {
                val card = foundation.removeAt(foundation.lastIndex)
                val startX = getColumnX(3 + i)
                val startY = topRowY

                // Random velocity - mostly horizontal with slight upward
                val vx = (Math.random() * 16 - 8).toFloat() // -8 to 8
                val vy = (Math.random() * -4 - 2).toFloat() // -6 to -2 (upward)

                animatedCards.add(AnimatedCard(card, startX, startY, vx, vy, currentTime))
                nextCardToAnimate++
                return
            }
        }
    }

    fun startVictoryAnimation() {
        isVictoryAnimating = true
        animatedCards.clear()
        nextCardToAnimate = 0
        lastAnimFrameTime = 0L
        lastCardSpawnTime = 0L
        animHandler.post(animRunnable)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions(w, h)
        scaleCardBack()
    }

    private fun calculateDimensions(width: Int, height: Int) {
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        val isLandscape = width > height

        if (isLandscape) {
            // Landscape: calculate card size based on height constraint
            // Need to fit: top row (1 card height) + small gap + tableau (~13 cards max visible)
            val maxTableauCards = 13
            val estimatedTableauOffsetRatio = 0.15f // Smaller offset in landscape
            val minGap = 4f // Minimal gap between elements

            // Total height needed: cardHeight + gap + cardHeight + (maxCards-1) * offset
            val heightRatio = 2f + (maxTableauCards - 1) * estimatedTableauOffsetRatio
            cardHeight = (availableHeight - minGap * 2) / heightRatio
            cardWidth = cardHeight / 1.4f

            // Check if cards are too wide for 7 columns, adjust if needed
            val maxCardWidth = (availableWidth - 8f * 4f) / 7f // 4f minimum spacing
            if (cardWidth > maxCardWidth) {
                cardWidth = maxCardWidth
                cardHeight = cardWidth * 1.4f
            }

            cardSpacing = (availableWidth - cardWidth * 7) / 8f

            // Minimal margins in landscape - cards close to header and to each other
            topRowY = paddingTop + minGap
            tableauY = topRowY + cardHeight + minGap

            // Smaller offsets in landscape to fit more cards
            tableauOffset = cardHeight * 0.15f
            tableauFaceDownOffset = cardHeight * 0.08f
        } else {
            // Portrait: calculate card size based on width (original behavior)
            cardSpacing = availableWidth * 0.015f
            cardWidth = (availableWidth - cardSpacing * 8) / 7f
            cardHeight = cardWidth * 1.4f

            topRowY = paddingTop + cardSpacing
            tableauY = topRowY + cardHeight + cardSpacing * 2

            tableauOffset = cardHeight * 0.22f
            tableauFaceDownOffset = cardHeight * 0.12f
        }

        // Update text sizes
        val textSize = cardWidth * 0.28f
        redTextPaint.textSize = textSize
        blackTextPaint.textSize = textSize
        symbolPaint.textSize = cardWidth * 0.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return

        drawStock(canvas, g)
        drawWaste(canvas, g)
        drawFoundations(canvas, g)
        drawTableau(canvas, g)

        // Draw dragged cards on top
        if (isDragging && dragCards.isNotEmpty()) {
            drawDraggedCards(canvas)
        }

        // Draw victory animation cards
        if (isVictoryAnimating) {
            for (ac in animatedCards) {
                drawCard(canvas, ac.card, ac.x, ac.y, isSelected = false)
            }
        }
    }

    private fun drawDraggedCards(canvas: Canvas) {
        val x = currentDragX - dragOffsetX
        var y = currentDragY - dragOffsetY

        for (card in dragCards) {
            drawCard(canvas, card, x, y, isSelected = true)
            y += tableauOffset
        }
    }

    private fun getColumnX(column: Int): Float {
        return paddingLeft + cardSpacing + column * (cardWidth + cardSpacing)
    }

    private fun drawStock(canvas: Canvas, game: SolitaireGame) {
        val x = getColumnX(0)
        val y = topRowY

        if (game.stock.isEmpty()) {
            // Draw empty pile indicator (recycle icon area)
            drawEmptyPile(canvas, x, y)
        } else {
            // Draw card back
            drawCardBack(canvas, x, y, false)
        }
    }

    private fun drawWaste(canvas: Canvas, game: SolitaireGame) {
        val x = getColumnX(1)
        val y = topRowY

        if (game.waste.isEmpty()) {
            drawEmptyPile(canvas, x, y)
        } else {
            // Skip if this card is being dragged
            val card = game.waste.last()
            if (isDragging && dragCards.contains(card)) {
                // Show the card underneath if any, otherwise empty pile
                if (game.waste.size > 1) {
                    val cardBelow = game.waste[game.waste.lastIndex - 1]
                    drawCard(canvas, cardBelow, x, y, false)
                } else {
                    drawEmptyPile(canvas, x, y)
                }
            } else {
                val isSelected = game.isCardSelected(card)
                drawCard(canvas, card, x, y, isSelected)
            }
        }
    }

    private fun drawFoundations(canvas: Canvas, game: SolitaireGame) {
        for (i in 0 until 4) {
            val x = getColumnX(3 + i)
            val y = topRowY
            val foundation = game.foundations[i]

            if (foundation.isEmpty()) {
                drawEmptyPile(canvas, x, y, getSuitSymbol(i))
            } else {
                val card = foundation.last()
                // Skip if this card is being dragged
                if (isDragging && dragCards.contains(card)) {
                    // Show the card underneath if any, otherwise empty pile with suit
                    if (foundation.size > 1) {
                        val cardBelow = foundation[foundation.lastIndex - 1]
                        drawCard(canvas, cardBelow, x, y, false)
                    } else {
                        drawEmptyPile(canvas, x, y, getSuitSymbol(i))
                    }
                } else {
                    val isSelected = game.isCardSelected(card)
                    drawCard(canvas, card, x, y, isSelected)
                }
            }
        }
    }

    private fun getSuitSymbol(index: Int): String {
        return when (index) {
            0 -> "♥"
            1 -> "♦"
            2 -> "♣"
            3 -> "♠"
            else -> ""
        }
    }

    private fun drawTableau(canvas: Canvas, game: SolitaireGame) {
        for (col in 0 until 7) {
            val x = getColumnX(col)
            val column = game.tableau[col]

            // Check if we're dragging from this column
            val isDraggingFromThisColumn = isDragging &&
                dragSourcePileType == PileType.TABLEAU &&
                dragSourcePileIndex == col

            // Calculate how many cards to show (exclude dragged cards)
            val cardsToShow = if (isDraggingFromThisColumn) {
                dragSourceCardIndex
            } else {
                column.size
            }

            if (cardsToShow == 0 && column.isEmpty()) {
                drawEmptyPile(canvas, x, tableauY)
            } else if (cardsToShow == 0) {
                // All cards are being dragged, show empty pile
                drawEmptyPile(canvas, x, tableauY)
            } else {
                var y = tableauY
                for (index in 0 until minOf(cardsToShow, column.size)) {
                    val card = column[index]
                    val isSelected = game.isCardSelected(card)
                    if (card.faceUp) {
                        drawCard(canvas, card, x, y, isSelected)
                    } else {
                        drawCardBack(canvas, x, y, isSelected)
                    }
                    y += if (card.faceUp) tableauOffset else tableauFaceDownOffset
                }
            }
        }
    }

    private fun drawEmptyPile(canvas: Canvas, x: Float, y: Float, symbol: String = "") {
        val rect = RectF(x, y, x + cardWidth, y + cardHeight)
        val radius = cardWidth * 0.08f
        canvas.drawRoundRect(rect, radius, radius, emptyPilePaint)

        if (symbol.isNotEmpty()) {
            symbolPaint.color = Color.parseColor("#444444")
            val textY = y + cardHeight / 2 - (symbolPaint.descent() + symbolPaint.ascent()) / 2
            canvas.drawText(symbol, x + cardWidth / 2, textY, symbolPaint)
        }
    }

    private fun drawCardBack(canvas: Canvas, x: Float, y: Float, isSelected: Boolean) {
        val rect = RectF(x, y, x + cardWidth, y + cardHeight)
        val radius = cardWidth * 0.08f

        val bitmap = scaledCardBackBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            // Draw bitmap with rounded corners
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(rect, radius, radius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawBitmap(bitmap, x, y, null)
            canvas.restore()
        } else {
            // Fallback: painted background
            canvas.drawRoundRect(rect, radius, radius, cardBackPaint)
            val patternRect = RectF(x + 4, y + 4, x + cardWidth - 4, y + cardHeight - 4)
            canvas.drawRoundRect(patternRect, radius, radius, cardBackPattern)
        }

        // Border
        canvas.drawRoundRect(rect, radius, radius, cardBorderPaint)

        if (isSelected) {
            canvas.drawRoundRect(rect, radius, radius, cardSelectedPaint)
        }
    }

    private fun drawCard(canvas: Canvas, card: Card, x: Float, y: Float, isSelected: Boolean) {
        val rect = RectF(x, y, x + cardWidth, y + cardHeight)
        val radius = cardWidth * 0.08f

        // Card face
        canvas.drawRoundRect(rect, radius, radius, cardFacePaint)

        // Border
        canvas.drawRoundRect(rect, radius, radius, cardBorderPaint)

        // Text paint based on color
        val textPaint = if (card.color == CardColor.RED) redTextPaint else blackTextPaint
        symbolPaint.color = textPaint.color

        // Draw rank in top-left corner only
        val rankText = card.rank.label
        val textX = x + cardWidth * 0.1f
        val textY = y + cardWidth * 0.32f
        canvas.drawText(rankText, textX, textY, textPaint)

        // Draw large suit symbol in center only
        val suitText = card.suit.symbol
        val centerX = x + cardWidth / 2
        val centerY = y + cardHeight / 2 - (symbolPaint.descent() + symbolPaint.ascent()) / 2
        canvas.drawText(suitText, centerX, centerY, symbolPaint)

        if (isSelected) {
            canvas.drawRoundRect(rect, radius, radius, cardSelectedPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                hasMoved = false

                val currentTime = System.currentTimeMillis()
                val isDoubleTap = currentTime - lastTapTime < doubleTapTimeout &&
                        Math.abs(x - lastTapX) < doubleTapSlop &&
                        Math.abs(y - lastTapY) < doubleTapSlop

                lastTapTime = currentTime
                lastTapX = x
                lastTapY = y

                if (isDoubleTap) {
                    handleDoubleTap(x, y)
                    return true
                }

                // Check if we can start dragging a card
                tryStartDrag(x, y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    currentDragX = x
                    currentDragY = y
                    invalidate()
                } else if (!hasMoved) {
                    val dx = Math.abs(x - touchDownX)
                    val dy = Math.abs(y - touchDownY)
                    if (dx > dragThreshold || dy > dragThreshold) {
                        hasMoved = true
                        if (dragCards.isNotEmpty()) {
                            isDragging = true
                            currentDragX = x
                            currentDragY = y
                            invalidate()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    handleDrop(x, y)
                    isDragging = false
                    dragCards = emptyList()
                    invalidate()
                } else if (!hasMoved) {
                    // It was a tap, not a drag
                    handleTap(touchDownX, touchDownY)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun tryStartDrag(x: Float, y: Float) {
        val g = game ?: return
        dragCards = emptyList()
        dragSourcePileType = null

        // Check waste pile
        if (y >= topRowY && y <= topRowY + cardHeight) {
            val wasteX = getColumnX(1)
            if (x >= wasteX && x <= wasteX + cardWidth && g.waste.isNotEmpty()) {
                dragCards = listOf(g.waste.last())
                dragSourcePileType = PileType.WASTE
                dragSourcePileIndex = 0
                dragSourceCardIndex = g.waste.lastIndex
                dragOffsetX = x - wasteX
                dragOffsetY = y - topRowY
                return
            }

            // Check foundations (can drag top card)
            for (i in 0 until 4) {
                val fx = getColumnX(3 + i)
                if (x >= fx && x <= fx + cardWidth && g.foundations[i].isNotEmpty()) {
                    dragCards = listOf(g.foundations[i].last())
                    dragSourcePileType = PileType.FOUNDATION
                    dragSourcePileIndex = i
                    dragSourceCardIndex = g.foundations[i].lastIndex
                    dragOffsetX = x - fx
                    dragOffsetY = y - topRowY
                    return
                }
            }
        }

        // Check tableau
        if (y >= tableauY) {
            for (col in 0 until 7) {
                val colX = getColumnX(col)
                if (x >= colX && x <= colX + cardWidth) {
                    val column = g.tableau[col]
                    if (column.isNotEmpty()) {
                        val cardIndex = findCardInTableau(y, column)
                        if (cardIndex >= 0 && cardIndex < column.size) {
                            val card = column[cardIndex]
                            if (card.faceUp) {
                                // Can drag this card and all cards below it
                                dragCards = column.subList(cardIndex, column.size).toList()
                                dragSourcePileType = PileType.TABLEAU
                                dragSourcePileIndex = col
                                dragSourceCardIndex = cardIndex
                                dragOffsetX = x - colX
                                dragOffsetY = y - getTableauCardY(col, cardIndex)
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getTableauCardY(col: Int, cardIndex: Int): Float {
        val g = game ?: return tableauY
        var y = tableauY
        for (i in 0 until cardIndex) {
            val card = g.tableau[col].getOrNull(i) ?: break
            y += if (card.faceUp) tableauOffset else tableauFaceDownOffset
        }
        return y
    }

    private fun handleDoubleTap(x: Float, y: Float) {
        val g = game ?: return

        // Check waste
        if (y >= topRowY && y <= topRowY + cardHeight) {
            val wasteX = getColumnX(1)
            if (x >= wasteX && x <= wasteX + cardWidth && g.waste.isNotEmpty()) {
                listener?.onCardDoubleTapped(g.waste.last(), PileType.WASTE, 0)
                return
            }
        }

        // Check tableau
        if (y >= tableauY) {
            for (col in 0 until 7) {
                val colX = getColumnX(col)
                if (x >= colX && x <= colX + cardWidth) {
                    val column = g.tableau[col]
                    if (column.isNotEmpty()) {
                        val cardIndex = findCardInTableau(y, column)
                        if (cardIndex == column.lastIndex && column[cardIndex].faceUp) {
                            listener?.onCardDoubleTapped(column[cardIndex], PileType.TABLEAU, col)
                        }
                    }
                    return
                }
            }
        }
    }

    private fun handleTap(x: Float, y: Float) {
        val g = game ?: return

        // Check stock
        if (y >= topRowY && y <= topRowY + cardHeight) {
            val stockX = getColumnX(0)
            if (x >= stockX && x <= stockX + cardWidth) {
                listener?.onStockClicked()
                return
            }

            // Check waste
            val wasteX = getColumnX(1)
            if (x >= wasteX && x <= wasteX + cardWidth && g.waste.isNotEmpty()) {
                listener?.onCardClicked(PileType.WASTE, 0, g.waste.lastIndex)
                return
            }

            // Check foundations
            for (i in 0 until 4) {
                val fx = getColumnX(3 + i)
                if (x >= fx && x <= fx + cardWidth) {
                    val foundation = g.foundations[i]
                    if (foundation.isNotEmpty()) {
                        listener?.onCardClicked(PileType.FOUNDATION, i, foundation.lastIndex)
                    } else {
                        listener?.onPileClicked(PileType.FOUNDATION, i)
                    }
                    return
                }
            }
        }

        // Check tableau
        if (y >= tableauY) {
            for (col in 0 until 7) {
                val colX = getColumnX(col)
                if (x >= colX && x <= colX + cardWidth) {
                    val column = g.tableau[col]
                    if (column.isEmpty()) {
                        listener?.onPileClicked(PileType.TABLEAU, col)
                    } else {
                        val cardIndex = findCardInTableau(y, column)
                        if (cardIndex >= 0 && cardIndex < column.size) {
                            listener?.onCardClicked(PileType.TABLEAU, col, cardIndex)
                        }
                    }
                    return
                }
            }
        }
    }

    private fun handleDrop(x: Float, y: Float) {
        val sourcePileType = dragSourcePileType ?: return
        game ?: return

        // Find drop target
        // Check foundations
        if (y >= topRowY && y <= topRowY + cardHeight) {
            for (i in 0 until 4) {
                val fx = getColumnX(3 + i)
                if (x >= fx && x <= fx + cardWidth) {
                    val success = listener?.onCardDragged(
                        sourcePileType,
                        dragSourcePileIndex,
                        dragSourceCardIndex,
                        PileType.FOUNDATION,
                        i
                    ) ?: false
                    if (success) return
                }
            }
        }

        // Check tableau
        if (y >= tableauY) {
            for (col in 0 until 7) {
                val colX = getColumnX(col)
                if (x >= colX && x <= colX + cardWidth) {
                    val success = listener?.onCardDragged(
                        sourcePileType,
                        dragSourcePileIndex,
                        dragSourceCardIndex,
                        PileType.TABLEAU,
                        col
                    ) ?: false
                    if (success) return
                }
            }
        }
    }

    private fun findCardInTableau(touchY: Float, column: List<Card>): Int {
        var y = tableauY
        for ((index, card) in column.withIndex()) {
            val nextY = y + if (index == column.lastIndex) {
                cardHeight
            } else {
                if (card.faceUp) tableauOffset else tableauFaceDownOffset
            }

            if (touchY >= y && touchY < nextY) {
                return index
            }
            y += if (card.faceUp) tableauOffset else tableauFaceDownOffset
        }
        // If touch is below last card, return last card
        return column.lastIndex
    }

    fun refresh() {
        invalidate()
    }

    /**
     * Loads a new random card back image for a new game.
     */
    fun loadNewCardBack() {
        loadRandomCardBack()
        scaleCardBack()
        invalidate()
    }

    private fun loadRandomCardBack() {
        val source = cardBackSources.random()
        val path = source.randomPath()
        try {
            context.assets.open(path).use { inputStream ->
                cardBackBitmap = BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            // Fallback: cardBackBitmap stays null, we'll use painted background
            cardBackBitmap = null
        }
    }

    private fun scaleCardBack() {
        val original = cardBackBitmap ?: return
        if (cardWidth <= 0 || cardHeight <= 0) return

        // Recycle old scaled bitmap if exists
        scaledCardBackBitmap?.recycle()

        // Scale to card dimensions
        scaledCardBackBitmap = Bitmap.createScaledBitmap(
            original,
            cardWidth.toInt(),
            cardHeight.toInt(),
            true
        )
    }
}
