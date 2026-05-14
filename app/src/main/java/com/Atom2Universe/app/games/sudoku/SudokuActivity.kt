package com.Atom2Universe.app.games.sudoku

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.Atom2Universe.app.LocaleHelper
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.games.sudoku.data.SudokuDatabase
import com.Atom2Universe.app.games.sudoku.data.SudokuSaveEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.Atom2Universe.app.util.enableImmersiveMode

class SudokuActivity : AppCompatActivity(), SudokuGridView.OnCellSelectedListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var gridView: SudokuGridView
    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private lateinit var difficultySpinner: Spinner
    private lateinit var newGameButton: Button
    private lateinit var checkButton: Button
    private lateinit var clearButton: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button

    private val numberButtons = mutableListOf<Button>()

    // ── Undo / Redo ───────────────────────────────────────────────────────────
    private data class SudokuAction(val row: Int, val col: Int, val oldValue: Int, val newValue: Int)
    private val undoStack = ArrayDeque<SudokuAction>()
    private val redoStack = ArrayDeque<SudokuAction>()

    private var currentDifficulty = SudokuDifficulty.MEDIUM
    private var elapsedTimeMs: Long = 0
    private var startTimeMs: Long = 0
    private var isTimerRunning = false
    private var isPuzzleSolved = false

    // Selected number for "number first" mode (0 = no number selected)
    private var selectedNumber: Int = 0

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                elapsedTimeMs = System.currentTimeMillis() - startTimeMs
                updateTimerDisplay()
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val database by lazy { SudokuDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_sudoku)

        initViews()
        setupListeners()
        loadSavedGame()
    }

    private fun initViews() {
        gridView = findViewById(R.id.sudoku_grid)
        timerText = findViewById(R.id.timer_text)
        statusText = findViewById(R.id.status_text)
        difficultySpinner = findViewById(R.id.difficulty_spinner)
        newGameButton = findViewById(R.id.new_game_button)
        checkButton = findViewById(R.id.check_button)
        clearButton = findViewById(R.id.pad_clear)
        undoButton = findViewById(R.id.pad_undo)
        redoButton = findViewById(R.id.pad_redo)

        // Number pad buttons
        numberButtons.add(findViewById(R.id.pad_1))
        numberButtons.add(findViewById(R.id.pad_2))
        numberButtons.add(findViewById(R.id.pad_3))
        numberButtons.add(findViewById(R.id.pad_4))
        numberButtons.add(findViewById(R.id.pad_5))
        numberButtons.add(findViewById(R.id.pad_6))
        numberButtons.add(findViewById(R.id.pad_7))
        numberButtons.add(findViewById(R.id.pad_8))
        numberButtons.add(findViewById(R.id.pad_9))

        gridView.listener = this

        setupDifficultySpinner()
    }

    private fun setupDifficultySpinner() {
        val difficulties = SudokuDifficulty.entries.toTypedArray()
        val labels = difficulties.map { getString(it.labelResId) }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        difficultySpinner.adapter = adapter
        difficultySpinner.setSelection(difficulties.indexOf(currentDifficulty))

        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentDifficulty = difficulties[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        newGameButton.setOnClickListener { onNewGameClicked() }

        checkButton.setOnClickListener { onCheckClicked() }

        clearButton.setOnClickListener { onClearClicked() }

        undoButton.setOnClickListener { onUndoClicked() }
        redoButton.setOnClickListener { onRedoClicked() }

        // Number pad
        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener { onNumberClicked(index + 1) }
        }
    }

    private fun loadSavedGame() {
        lifecycleScope.launch {
            val save = withContext(Dispatchers.IO) {
                database.sudokuDao().getSave()
            }

            if (save != null && !save.isSolved) {
                showResumeDialog(save)
            } else {
                showNewGameDialog()
            }
        }
    }

    private fun showResumeDialog(save: SudokuSaveEntity) {
        AlertDialog.Builder(this)
            .setTitle(R.string.sudoku_dialog_confirm_new_title)
            .setMessage(R.string.sudoku_dialog_confirm_new_message)
            .setPositiveButton(R.string.sudoku_resume_game) { _, _ ->
                restoreGame(save)
            }
            .setNegativeButton(R.string.sudoku_start_new) { _, _ ->
                showNewGameDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun restoreGame(save: SudokuSaveEntity) {
        currentDifficulty = save.toDifficulty()
        difficultySpinner.setSelection(SudokuDifficulty.entries.indexOf(currentDifficulty))

        gridView.setBoard(save.toBoard())
        elapsedTimeMs = save.elapsedTimeMs
        isPuzzleSolved = false
        clearHistory()

        updateDifficultySettings()
        updateValidation()
        startTimer()

        setStatus(getString(R.string.sudoku_status_no_error), isError = false)
    }

    private fun showNewGameDialog() {
        val difficulties = SudokuDifficulty.entries.toTypedArray()
        val labels = difficulties.map { getString(it.labelResId) }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.sudoku_dialog_new_game_title)
            .setItems(labels) { _, which ->
                currentDifficulty = difficulties[which]
                difficultySpinner.setSelection(which)
                startNewGame()
            }
            .setCancelable(false)
            .show()
    }

    private fun onNewGameClicked() {
        if (gridView.getBoard().cells.any { row -> row.any { it != 0 } }) {
            AlertDialog.Builder(this)
                .setTitle(R.string.sudoku_dialog_confirm_new_title)
                .setMessage(R.string.sudoku_dialog_confirm_new_message)
                .setPositiveButton(R.string.confirm) { _, _ -> showNewGameDialog() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            showNewGameDialog()
        }
    }

    private fun startNewGame() {
        setStatus(getString(R.string.sudoku_status_generating), isError = false)
        statusText.visibility = View.VISIBLE

        lifecycleScope.launch {
            val board = withContext(Dispatchers.Default) {
                SudokuGame.generatePuzzle(currentDifficulty)
            }

            gridView.setBoard(board)
            elapsedTimeMs = 0
            isPuzzleSolved = false
            clearSelectedNumber()
            clearHistory()

            updateDifficultySettings()
            startTimer()

            val clues = board.cells.sumOf { row -> row.count { it != 0 } }
            val difficultyLabel = getString(currentDifficulty.labelResId)
            setStatus(
                getString(R.string.sudoku_status_generated, difficultyLabel, clues),
                isError = false
            )

            saveGame()
        }
    }

    private fun updateDifficultySettings() {
        val showErrors = currentDifficulty.showErrorsRealtime
        val showConflicts = currentDifficulty.showConflictsRealtime

        gridView.setShowMistakes(showErrors)
        gridView.setShowConflicts(showConflicts)

        // Hide check button in easy mode (errors shown automatically)
        checkButton.visibility = if (currentDifficulty == SudokuDifficulty.EASY) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun startTimer() {
        startTimeMs = System.currentTimeMillis() - elapsedTimeMs
        isTimerRunning = true
        timerHandler.post(timerRunnable)
    }

    private fun stopTimer() {
        isTimerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun updateTimerDisplay() {
        val seconds = (elapsedTimeMs / 1000) % 60
        val minutes = (elapsedTimeMs / 1000) / 60
        timerText.text = String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCellSelected(row: Int, col: Int) {
        if (isPuzzleSolved) return

        // If a number is pre-selected, place it in the clicked cell
        if (selectedNumber > 0) {
            val board = gridView.getBoard()
            if (!board.isFixed(row, col)) {
                val oldValue = board.getValue(row, col)
                if (oldValue != selectedNumber) {
                    gridView.setValueAtSelected(selectedNumber)
                    recordAction(row, col, oldValue, selectedNumber)
                }
                updateValidation()
                checkForCompletion()
                saveGame()
                // Keep the number selected for "painting" multiple cells
            }
        }

        updateValidation()
    }

    private fun onNumberClicked(number: Int) {
        if (isPuzzleSolved) return

        if (gridView.hasSelection()) {
            // Mode: Cell selected first -> place number and deselect
            val row = gridView.getSelectedRow()
            val col = gridView.getSelectedCol()
            val oldValue = gridView.getBoard().getValue(row, col)
            if (oldValue != number) {
                gridView.setValueAtSelected(number)
                recordAction(row, col, oldValue, number)
            }
            gridView.clearSelection()
            clearSelectedNumber()
            updateValidation()
            checkForCompletion()
            saveGame()
        } else {
            // Mode: No cell selected -> toggle number selection for "painting" mode
            if (selectedNumber == number) {
                // Deselect if same number clicked again
                clearSelectedNumber()
            } else {
                selectNumber(number)
            }
        }
    }

    private fun selectNumber(number: Int) {
        selectedNumber = number
        updateNumberButtonsHighlight()
    }

    private fun clearSelectedNumber() {
        selectedNumber = 0
        updateNumberButtonsHighlight()
    }

    private fun updateNumberButtonsHighlight() {
        numberButtons.forEachIndexed { index, button ->
            val buttonNumber = index + 1
            if (buttonNumber == selectedNumber) {
                // Highlight selected number
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.sudoku_pad_button_selected))
            } else {
                // Reset to default
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.sudoku_pad_button_background))
            }
        }
    }

    private fun onClearClicked() {
        if (isPuzzleSolved) return

        // Clear selected number if any
        if (selectedNumber > 0) {
            clearSelectedNumber()
        }

        // Clear cell value if a cell is selected
        if (gridView.hasSelection()) {
            val row = gridView.getSelectedRow()
            val col = gridView.getSelectedCol()
            val oldValue = gridView.getBoard().getValue(row, col)
            if (oldValue != 0) {
                gridView.setValueAtSelected(0)
                recordAction(row, col, oldValue, 0)
            }
            gridView.clearSelection()
            updateValidation()
            saveGame()
        }
    }

    private fun onCheckClicked() {
        if (isPuzzleSolved) return

        val board = gridView.getBoard()
        val mistakes = SudokuGame.findMistakes(board)
        val conflicts = SudokuGame.findConflicts(board)

        gridView.setMistakes(mistakes)
        gridView.setConflicts(conflicts)
        gridView.setShowMistakes(true)
        gridView.setShowConflicts(true)

        val errorCount = mistakes.size + conflicts.size
        if (errorCount > 0) {
            setStatus(getString(R.string.sudoku_status_errors, errorCount), isError = true)
        } else {
            setStatus(getString(R.string.sudoku_status_no_error), isError = false)
        }

        // Reset after delay for non-easy modes
        if (currentDifficulty != SudokuDifficulty.EASY) {
            timerHandler.postDelayed({
                gridView.setShowMistakes(false)
                gridView.setShowConflicts(false)
                gridView.refresh()
            }, 2000)
        }
    }

    private fun updateValidation() {
        val board = gridView.getBoard()
        val mistakes = SudokuGame.findMistakes(board)
        val conflicts = SudokuGame.findConflicts(board)

        gridView.setMistakes(mistakes)
        gridView.setConflicts(conflicts)

        if (currentDifficulty.showErrorsRealtime) {
            val errorCount = mistakes.size
            if (errorCount > 0) {
                setStatus(getString(R.string.sudoku_status_mistakes, errorCount), isError = true)
            } else {
                setStatus(getString(R.string.sudoku_status_no_error), isError = false)
            }
        }
    }

    private fun checkForCompletion() {
        val board = gridView.getBoard()

        if (board.isSolved()) {
            isPuzzleSolved = true
            stopTimer()
            setStatus(getString(R.string.sudoku_status_solved), isError = false)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.sudoku_status_ok))

            // Clear save on completion
            lifecycleScope.launch(Dispatchers.IO) {
                database.sudokuDao().deleteSave()
            }

            if (currentDifficulty == SudokuDifficulty.HARD) {
                NeutrinoRepository(this).addPending(1)
                Toast.makeText(this, R.string.clicker_neutrino_awarded, Toast.LENGTH_SHORT).show()
            }

            Toast.makeText(this, R.string.sudoku_status_solved, Toast.LENGTH_LONG).show()
        }
    }

    private fun setStatus(message: String, isError: Boolean) {
        statusText.text = message
        statusText.visibility = View.VISIBLE
        statusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.sudoku_status_error else R.color.startup_text_secondary
            )
        )
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────

    private fun recordAction(row: Int, col: Int, oldValue: Int, newValue: Int) {
        undoStack.addLast(SudokuAction(row, col, oldValue, newValue))
        redoStack.clear()
        updateUndoRedoButtons()
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoButtons()
    }

    private fun onUndoClicked() {
        if (isPuzzleSolved || undoStack.isEmpty()) return
        val action = undoStack.removeLast()
        gridView.setValueAt(action.row, action.col, action.oldValue)
        gridView.clearSelection()
        redoStack.addLast(action)
        updateUndoRedoButtons()
        updateValidation()
        saveGame()
    }

    private fun onRedoClicked() {
        if (isPuzzleSolved || redoStack.isEmpty()) return
        val action = redoStack.removeLast()
        gridView.setValueAt(action.row, action.col, action.newValue)
        gridView.clearSelection()
        undoStack.addLast(action)
        updateUndoRedoButtons()
        updateValidation()
        saveGame()
    }

    private fun updateUndoRedoButtons() {
        undoButton.isEnabled = undoStack.isNotEmpty()
        undoButton.alpha = if (undoStack.isNotEmpty()) 1f else 0.35f
        redoButton.isEnabled = redoStack.isNotEmpty()
        redoButton.alpha = if (redoStack.isNotEmpty()) 1f else 0.35f
    }

    private fun saveGame() {
        if (isPuzzleSolved) return

        lifecycleScope.launch(Dispatchers.IO) {
            val save = SudokuSaveEntity.fromBoard(
                board = gridView.getBoard(),
                difficulty = currentDifficulty,
                elapsedTimeMs = elapsedTimeMs,
                isSolved = false
            )
            database.sudokuDao().saveSave(save)
        }
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        if (!isPuzzleSolved && gridView.getBoard().cells.any { row -> row.any { it != 0 } }) {
            startTimer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
    }
}
