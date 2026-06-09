package com.Atom2Universe.app.crypto

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.google.android.material.card.MaterialCardView
import com.Atom2Universe.app.games.sudoku.CellPosition
import com.Atom2Universe.app.games.sudoku.SudokuBoard
import com.Atom2Universe.app.games.sudoku.SudokuDifficulty
import com.Atom2Universe.app.games.sudoku.SudokuGame
import com.Atom2Universe.app.games.sudoku.SudokuGridView
import com.Atom2Universe.app.games.sudoku.data.SudokuDatabase
import com.Atom2Universe.app.games.sudoku.data.SudokuSaveEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * Widget affichant une grille de sudoku interactive.
 *
 * Gestes :
 *  - Glisser le header      → déplacer le widget
 *  - Pincer (2 doigts)      → zoom/dézoom
 *  - Taper une cellule      → sélectionner
 *  - Taper un chiffre 1–9   → placer le chiffre dans la cellule sélectionnée
 *  - ✕ (Annuler)            → effacer la cellule sélectionnée
 *  - ✓ (Valider)            → ouvrir SudokuActivity pour continuer
 *  - Tap sur le header      → ouvrir SudokuActivity
 */
class SudokuWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val sudokuGridView: SudokuGridView
    private lateinit var cardView: MaterialCardView
    private lateinit var headerArea: LinearLayout
    private lateinit var digitRow: SudokuDigitRowView
    private lateinit var difficultyOverlay: FrameLayout
    private lateinit var resultOverlay: FrameLayout
    private lateinit var resultText: TextView
    private val baseCardColor = Color.parseColor("#0F172A")
    private var currentDifficulty = SudokuDifficulty.MEDIUM
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Undo / Notes ──────────────────────────────────────────────────────────
    private data class SudokuAction(val row: Int, val col: Int, val oldValue: Int, val newValue: Int)
    private val undoStack = ArrayDeque<SudokuAction>()
    private lateinit var btnUndo: TextView
    private lateinit var btnNotes: TextView
    private var notesMode = false

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
        LayoutInflater.from(context).inflate(R.layout.view_sudoku_widget, this, true)

        cardView = findViewById(R.id.sudoku_widget_card)
        sudokuGridView = findViewById(R.id.sudoku_widget_grid)
        difficultyOverlay = findViewById(R.id.sudoku_difficulty_overlay)
        resultOverlay = findViewById(R.id.sudoku_result_overlay)
        resultText = findViewById(R.id.sudoku_result_text)
        headerArea = findViewById(R.id.sudoku_header_area)
        digitRow = findViewById(R.id.sudoku_digit_row)

        resultOverlay.setOnClickListener {
            resultOverlay.visibility = GONE
        }

        btnUndo = findViewById(R.id.sudoku_btn_undo)
        btnNotes = findViewById(R.id.sudoku_btn_notes)
        btnNotes.setText(R.string.sudoku_widget_note_icon)
        btnNotes.setTextColor(Color.parseColor("#A78BFA"))
        btnNotes.contentDescription = context.getString(R.string.sudoku_notes)

        // Rangée de chiffres 1–9
        digitRow.onDigitTapped = { digit ->
            placeDigit(digit)
        }

        // Bouton Annuler (✕) → effacer la cellule sélectionnée
        findViewById<TextView>(R.id.sudoku_btn_cancel).setOnClickListener {
            placeDigit(0)
        }

        // Bouton Valider (✓) → désélectionner la cellule courante
        findViewById<TextView>(R.id.sudoku_btn_validate).setOnClickListener {
            sudokuGridView.clearSelection()
        }

        // Bouton Vérifier (?) → surligner les erreurs selon la difficulté
        findViewById<TextView>(R.id.sudoku_btn_verify).setOnClickListener {
            verifyBoard()
        }

        // Bouton Undo (↩) → annuler le dernier coup
        btnUndo.setOnClickListener { undo() }

        // Bouton Notes : les chiffres posent/retirent des annotations.
        btnNotes.setOnClickListener { toggleNotesMode() }

        // Bouton reset → afficher l'overlay de sélection de difficulté
        findViewById<TextView>(R.id.sudoku_btn_reset).setOnClickListener {
            difficultyOverlay.visibility = VISIBLE
        }

        // Boutons de l'overlay
        findViewById<TextView>(R.id.sudoku_diff_easy).setOnClickListener {
            startNewGame(SudokuDifficulty.EASY)
        }
        findViewById<TextView>(R.id.sudoku_diff_medium).setOnClickListener {
            startNewGame(SudokuDifficulty.MEDIUM)
        }
        findViewById<TextView>(R.id.sudoku_diff_hard).setOnClickListener {
            startNewGame(SudokuDifficulty.HARD)
        }
        findViewById<TextView>(R.id.sudoku_diff_cancel).setOnClickListener {
            difficultyOverlay.visibility = GONE
        }

        // Header : drag depuis les boutons ou la zone vide
        // Les boutons couvrent 100 % du header → il faut leur transmettre la logique de drag.
        // On retourne true sur ACTION_UP uniquement quand on glisse, pour empêcher
        // le click du bouton de se déclencher après un drag.
        val headerArea = findViewById<LinearLayout>(R.id.sudoku_header_area)
        val dragListener = OnTouchListener { _, event ->
            handleHeaderTouch(event)
            event.action == MotionEvent.ACTION_UP && isDragging
        }
        headerArea.setOnTouchListener(dragListener)
        listOf(
            R.id.sudoku_btn_cancel, R.id.sudoku_btn_validate, R.id.sudoku_btn_verify,
            R.id.sudoku_btn_undo, R.id.sudoku_btn_notes, R.id.sudoku_btn_reset
        ).forEach { id -> findViewById<View>(id).setOnTouchListener(dragListener) }
        updateActionButtons()
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

    // ── Nouvelle partie avec difficulté choisie ───────────────────────────────
    private fun startNewGame(difficulty: SudokuDifficulty) {
        difficultyOverlay.visibility = GONE
        resultOverlay.visibility = GONE
        currentDifficulty = difficulty
        undoStack.clear()
        notesMode = false
        updateActionButtons()
        GameStatsRepository(context).recordSudokuStarted()

        ioScope.launch {
            val board = SudokuGame.generatePuzzle(difficulty)
            withContext(Dispatchers.Main) {
                sudokuGridView.setBoard(board)
                sudokuGridView.clearSelection()
            }
            // Sauvegarder la nouvelle partie en base
            runCatching {
                val save = SudokuSaveEntity.fromBoard(
                    board = board,
                    difficulty = difficulty,
                    elapsedTimeMs = 0L,
                    isSolved = false
                )
                SudokuDatabase.getInstance(context).sudokuDao().saveSave(save)
            }
        }
    }

    // ── Vérification des erreurs selon la difficulté ─────────────────────────
    private fun verifyBoard() {
        val board = sudokuGridView.getBoard()
        val incorrectCells = mutableSetOf<CellPosition>()

        for (row in 0 until SudokuBoard.SIZE) {
            for (col in 0 until SudokuBoard.SIZE) {
                if (!board.isFixed(row, col) && board.getValue(row, col) != 0 && !board.isCorrect(row, col)) {
                    incorrectCells.add(CellPosition(row, col))
                }
            }
        }

        if (incorrectCells.isEmpty()) return

        val cellsToHighlight: Set<CellPosition> = when (currentDifficulty) {
            SudokuDifficulty.EASY -> incorrectCells
            else -> {
                // Toutes les cases remplies par le joueur (non préremplies) se surlignent
                val allUserCells = mutableSetOf<CellPosition>()
                for (row in 0 until SudokuBoard.SIZE) {
                    for (col in 0 until SudokuBoard.SIZE) {
                        if (!board.isFixed(row, col) && board.getValue(row, col) != 0) {
                            allUserCells.add(CellPosition(row, col))
                        }
                    }
                }
                allUserCells
            }
        }

        sudokuGridView.setMistakes(cellsToHighlight)
        sudokuGridView.setShowMistakes(true)
    }

    // ── Placement de chiffre + sauvegarde immédiate ───────────────────────────
    private fun placeDigit(digit: Int) {
        if (!sudokuGridView.hasSelection()) return
        val row = sudokuGridView.getSelectedRow()
        val col = sudokuGridView.getSelectedCol()
        if (notesMode && digit in 1..9) {
            if (sudokuGridView.toggleNoteAtSelected(digit)) {
                persistBoard()
            }
            return
        }

        val oldValue = sudokuGridView.getBoard().getValue(row, col)
        if (oldValue == digit) return  // Aucun changement, rien à enregistrer
        // Effacer le surlignage des erreurs dès qu'on joue un coup
        sudokuGridView.setShowMistakes(false)
        sudokuGridView.setValueAtSelected(digit)
        undoStack.addLast(SudokuAction(row, col, oldValue, digit))
        updateActionButtons()
        persistBoard()
        checkBoardCompletion()
    }

    private fun checkBoardCompletion() {
        val board = sudokuGridView.getBoard()
        if (!board.isComplete()) return
        if (board.isSolved()) {
            resultText.text = context.getString(R.string.sudoku_result_success)
            resultText.setTextColor(Color.parseColor("#22C55E"))
            GameStatsRepository(context).recordSudokuWon()
            val reward = when (currentDifficulty) {
                SudokuDifficulty.EASY   -> 5
                SudokuDifficulty.MEDIUM -> 10
                SudokuDifficulty.HARD   -> 20
            }
            NeutrinoRepository(context).addBalance(reward)
        } else {
            resultText.text = context.getString(R.string.sudoku_result_failure)
            resultText.setTextColor(Color.parseColor("#EF4444"))
        }
        resultOverlay.visibility = VISIBLE
    }

    // ── Undo / Notes ──────────────────────────────────────────────────────────
    private fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeLast()
        sudokuGridView.setShowMistakes(false)
        sudokuGridView.setValueAt(action.row, action.col, action.oldValue)
        updateActionButtons()
        persistBoard()
    }

    private fun toggleNotesMode() {
        notesMode = !notesMode
        updateActionButtons()
    }

    private fun updateActionButtons() {
        btnUndo.alpha = if (undoStack.isEmpty()) 0.3f else 1f
        btnNotes.alpha = if (notesMode) 1f else 0.55f
    }

    private fun persistBoard() {
        val board = sudokuGridView.getBoard()
        val notes = sudokuGridView.getNotesMasks()
        val difficulty = currentDifficulty
        ioScope.launch {
            runCatching {
                val save = SudokuSaveEntity.fromBoard(
                    board = board,
                    difficulty = difficulty,
                    elapsedTimeMs = 0L,
                    isSolved = false,
                    notes = notes
                )
                SudokuDatabase.getInstance(context).sudokuDao().saveSave(save)
            }
        }
    }

    private fun openSudokuActivity() {
        val intent = Intent(context, com.Atom2Universe.app.games.sudoku.SudokuActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
    }

    // ── Opacité fond (carte + header + rangée chiffres + fonds de cases) ─────
    fun applyBackgroundOpacity(percent: Int) {
        val alpha = (percent.coerceIn(0, 100) / 100f * 255f).toInt()
        cardView.setCardBackgroundColor(ColorUtils.setAlphaComponent(baseCardColor, alpha))
        val viewAlpha = percent.coerceIn(0, 100) / 100f
        headerArea.alpha = viewAlpha
        digitRow.alpha = viewAlpha
        sudokuGridView.setBackgroundAlpha(percent)
    }

    // ── Opacité chiffres dans la grille uniquement ────────────────────────────
    fun applyNumbersOpacity(percent: Int) {
        sudokuGridView.setNumbersAlpha(percent)
    }

    // ── Chargement depuis Room DB ─────────────────────────────────────────────
    fun reload(scope: CoroutineScope) {
        scope.launch {
            val save = runCatching {
                SudokuDatabase.getInstance(context).sudokuDao().getSave()
            }.getOrNull()
            val board = save?.toBoard() ?: SudokuBoard.empty()
            if (save != null) currentDifficulty = save.toDifficulty()
            withContext(Dispatchers.Main) {
                sudokuGridView.setBoard(board)
                if (save != null) {
                    sudokuGridView.setNotesMasks(save.toNotes())
                }
                undoStack.clear()
                notesMode = false
                updateActionButtons()
            }
        }
    }

    override fun onDetachedFromWindow() {
        ioScope.coroutineContext[Job]?.cancel()
        super.onDetachedFromWindow()
    }
}
