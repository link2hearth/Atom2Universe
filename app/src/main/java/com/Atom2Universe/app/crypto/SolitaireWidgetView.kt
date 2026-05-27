package com.Atom2Universe.app.crypto

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.google.android.material.card.MaterialCardView
import com.Atom2Universe.app.games.solitaire.Card
import com.Atom2Universe.app.games.solitaire.PileType
import com.Atom2Universe.app.games.solitaire.SolitaireGame
import com.Atom2Universe.app.games.solitaire.SolitaireView
import com.Atom2Universe.app.games.solitaire.data.SolitaireDatabase
import com.Atom2Universe.app.games.solitaire.data.SolitaireSaveEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * Widget affichant une partie de Solitaire (Klondike) interactive.
 *
 * Gestes :
 *  - Glisser le header    → déplacer le widget
 *  - Pincer (2 doigts)    → zoom/dézoom
 *  - Interagir avec le jeu → mêmes actions que SolitaireActivity
 *  - Tap sur le header    → ouvre SolitaireActivity
 */
class SolitaireWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    SolitaireView.OnGameActionListener {

    private lateinit var cardView: MaterialCardView
    private lateinit var solitaireView: SolitaireView
    private lateinit var resultOverlay: FrameLayout
    private lateinit var resultText: TextView
    private lateinit var resetOverlay: FrameLayout
    private val baseCardColor = Color.parseColor("#0F172A")

    private val game = SolitaireGame()
    private var moves = 0
    private var isGameWon = false

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Zoom ──────────────────────────────────────────────────────────────────
    private val minScale = 0.4f
    private val maxScale = 3.0f
    private var currentScale = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                if (next != currentScale) {
                    currentScale = next
                    scaleX = currentScale
                    scaleY = currentScale
                }
                return true
            }
        })

    // ── Drag du widget (header uniquement) ────────────────────────────────────
    private var dragLastX = 0f
    private var dragLastY = 0f
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var isDragging = false
    private var skipNextDragFrame = false
    private val tapThresholdPx = 12f * context.resources.displayMetrics.density

    init {
        LayoutInflater.from(context).inflate(R.layout.view_solitaire_widget, this, true)

        cardView = findViewById(R.id.solitaire_widget_card)
        solitaireView = findViewById(R.id.solitaire_widget_view)
        resultOverlay = findViewById(R.id.solitaire_result_overlay)
        resultText = findViewById(R.id.solitaire_result_text)
        resetOverlay = findViewById(R.id.solitaire_reset_overlay)

        solitaireView.game = game
        solitaireView.listener = this

        // Clic sur l'overlay résultat → le fermer
        resultOverlay.setOnClickListener {
            resultOverlay.visibility = GONE
        }

        // Bouton reset → afficher l'overlay de confirmation
        // Pas de touch listener → pas de conflit avec le drag du header
        findViewById<TextView>(R.id.solitaire_btn_new_game).setOnClickListener {
            resultOverlay.visibility = GONE
            resetOverlay.visibility = VISIBLE
        }

        // Overlay reset : confirmer
        findViewById<TextView>(R.id.solitaire_reset_confirm).setOnClickListener {
            resetOverlay.visibility = GONE
            startNewGame()
        }

        // Overlay reset : annuler
        findViewById<TextView>(R.id.solitaire_reset_cancel).setOnClickListener {
            resetOverlay.visibility = GONE
        }

        // Header : drag uniquement (pas d'ouverture d'activité au tap)
        val headerArea = findViewById<FrameLayout>(R.id.solitaire_header_area)
        headerArea.setOnTouchListener { _, event -> handleHeaderTouch(event) }
    }

    // ── Pinch-zoom : intercepter les gestes 2 doigts avant les enfants ────────
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) {
            scaleDetector.onTouchEvent(ev)
            return true
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        return true
    }

    // ── Drag depuis le header ─────────────────────────────────────────────────
    private fun handleHeaderTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront()
                dragDownX = event.rawX
                dragDownY = event.rawY
                dragLastX = event.rawX
                dragLastY = event.rawY
                isDragging = false
                skipNextDragFrame = false
            }
            MotionEvent.ACTION_POINTER_UP -> skipNextDragFrame = true
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (skipNextDragFrame) {
                        dragLastX = event.rawX
                        dragLastY = event.rawY
                        dragDownX = event.rawX
                        dragDownY = event.rawY
                        isDragging = false
                        skipNextDragFrame = false
                    } else {
                        val moved = hypot(event.rawX - dragDownX, event.rawY - dragDownY)
                        if (isDragging || moved > tapThresholdPx) {
                            isDragging = true
                            translationX += event.rawX - dragLastX
                            translationY += event.rawY - dragLastY
                        }
                        dragLastX = event.rawX
                        dragLastY = event.rawY
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                skipNextDragFrame = false
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                skipNextDragFrame = false
            }
        }
        return true
    }

    // ── Nouvelle partie ───────────────────────────────────────────────────────
    private fun startNewGame() {
        resultOverlay.visibility = GONE
        resetOverlay.visibility = GONE
        isGameWon = false
        moves = 0
        game.newGame()
        solitaireView.loadNewCardBack()
        solitaireView.refresh()
        persistGame()
        GameStatsRepository(context).recordSolitaireStarted()
    }

    // ── OnGameActionListener ──────────────────────────────────────────────────

    override fun onStockClicked() {
        if (isGameWon) return
        game.drawFromStock()
        moves++
        solitaireView.refresh()
        persistGame()
    }

    override fun onCardClicked(pileType: PileType, pileIndex: Int, cardIndex: Int) {
        if (isGameWon) return

        val pile = game.getPile(pileType, pileIndex) ?: return
        if (cardIndex < 0 || cardIndex >= pile.size) return

        if (game.selectedCards.isNotEmpty()) {
            if (pileType == PileType.TABLEAU) {
                if (game.moveToTableau(pileIndex)) {
                    moves++
                    solitaireView.refresh()
                    checkWin()
                    persistGame()
                    return
                }
            }
            if (pileType == PileType.FOUNDATION) {
                if (game.moveToFoundation(pileIndex)) {
                    moves++
                    solitaireView.refresh()
                    checkWin()
                    persistGame()
                    return
                }
            }
            game.clearSelection()
        }

        game.selectCard(pileType, pileIndex, cardIndex)
        solitaireView.refresh()
    }

    override fun onPileClicked(pileType: PileType, pileIndex: Int) {
        if (isGameWon) return

        if (game.selectedCards.isNotEmpty()) {
            when (pileType) {
                PileType.TABLEAU -> {
                    if (game.moveToTableau(pileIndex)) {
                        moves++
                        solitaireView.refresh()
                        checkWin()
                        persistGame()
                        return
                    }
                }
                PileType.FOUNDATION -> {
                    if (game.moveToFoundation(pileIndex)) {
                        moves++
                        solitaireView.refresh()
                        checkWin()
                        persistGame()
                        return
                    }
                }
                else -> {}
            }
        }

        game.clearSelection()
        solitaireView.refresh()
    }

    override fun onCardDoubleTapped(card: Card, pileType: PileType, pileIndex: Int) {
        if (isGameWon) return
        if (game.autoMoveToFoundation(card, pileType, pileIndex)) {
            moves++
            solitaireView.refresh()
            checkWin()
            persistGame()
        }
    }

    override fun onCardDragged(
        sourcePileType: PileType,
        sourcePileIndex: Int,
        sourceCardIndex: Int,
        targetPileType: PileType,
        targetPileIndex: Int
    ): Boolean {
        if (isGameWon) return false

        game.clearSelection()
        if (!game.selectCard(sourcePileType, sourcePileIndex, sourceCardIndex)) return false

        val success = when (targetPileType) {
            PileType.TABLEAU -> game.moveToTableau(targetPileIndex)
            PileType.FOUNDATION -> game.moveToFoundation(targetPileIndex)
            else -> false
        }

        if (success) {
            moves++
            solitaireView.refresh()
            checkWin()
            persistGame()
        } else {
            game.clearSelection()
        }

        return success
    }

    // ── Vérification victoire ─────────────────────────────────────────────────
    private fun checkWin() {
        if (game.isGameWon()) {
            isGameWon = true
            resultText.text = context.getString(R.string.solitaire_status_won_short)
            resultText.setTextColor(Color.parseColor("#22C55E"))
            resultOverlay.visibility = VISIBLE
            solitaireView.startVictoryAnimation()
            NeutrinoRepository(context).addBalance(5)
            GameStatsRepository(context).recordSolitaireWon()
            ioScope.launch {
                runCatching { SolitaireDatabase.getInstance(context).solitaireDao().deleteSave() }
            }
        }
    }

    // ── Persistance ──────────────────────────────────────────────────────────
    private fun persistGame() {
        if (isGameWon) return
        val capturedMoves = moves
        ioScope.launch {
            runCatching {
                val save = SolitaireSaveEntity.fromGame(game, capturedMoves, 0L)
                SolitaireDatabase.getInstance(context).solitaireDao().saveSave(save)
            }
        }
    }

    // ── Chargement depuis Room DB ─────────────────────────────────────────────
    fun reload(scope: CoroutineScope) {
        resultOverlay.visibility = GONE
        resetOverlay.visibility = GONE
        scope.launch {
            val save = runCatching {
                SolitaireDatabase.getInstance(context).solitaireDao().getSave()
            }.getOrNull()

            withContext(Dispatchers.Main) {
                if (save != null && !save.isWon) {
                    save.restoreGame(game)
                    moves = save.moves
                    isGameWon = false
                    solitaireView.loadNewCardBack()  // charge un fond de carte aléatoire
                    solitaireView.game = game
                    solitaireView.refresh()
                } else {
                    // Pas de sauvegarde → nouvelle partie et on la persiste
                    game.newGame()
                    moves = 0
                    isGameWon = false
                    solitaireView.loadNewCardBack()
                    solitaireView.game = game
                    solitaireView.refresh()
                    persistGame()
                }
            }
        }
    }

    // ── Opacité fond (carte) ──────────────────────────────────────────────────
    fun applyBackgroundOpacity(percent: Int) {
        val alpha = (percent.coerceIn(0, 100) / 100f * 255f).toInt()
        cardView.setCardBackgroundColor(ColorUtils.setAlphaComponent(baseCardColor, alpha))
    }

    override fun onDetachedFromWindow() {
        ioScope.coroutineContext[Job]?.cancel()
        super.onDetachedFromWindow()
    }
}
