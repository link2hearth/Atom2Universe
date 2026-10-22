package com.Atom2Universe.app.games.colorstack

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import com.google.android.material.button.MaterialButton

/** Les éprouvettes : le second jeu du dossier Color Stack. */
class LabSortActivity : ThemedActivity(), LabSortView.Listener {

    companion object {
        private const val PREFS_NAME = "lab_sort_save"
        private const val KEY_SAVE = "save_json"
        private const val KEY_VICTORY = "victory_recorded"
        private const val KEY_MYSTERY = "mystery"
        private const val KEY_FORMULAS = "formulas"
        private const val WIN_DELAY_MS = 1300L
        private const val ARMED_COLOR = 0xFF4ADE80.toInt()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var labView: LabSortView
    private lateinit var movesText: TextView
    private lateinit var difficultySpinner: Spinner
    private lateinit var chipMystery: MaterialButton
    private lateinit var chipFormulas: MaterialButton
    private lateinit var btnUndo: MaterialButton
    private lateinit var btnPipette: MaterialButton
    private lateinit var winOverlay: View
    private lateinit var winCard: View

    private val game = LabSortGame()
    private var mysteryOn = false
    private var formulasOn = true
    private var pipetteArmed = false
    private var victoryRecorded = false
    private var lastReward = 0
    private var knownCompleted = 0
    private var ignoreSpinnerChange = false

    private var accentColor = 0
    private var onAccentColor = 0
    private var textColor = 0
    private var padColor = 0
    private var defaultStroke: ColorStateList? = null
    private var defaultIconTint: ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_lab_sort)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        mysteryOn = prefs.getBoolean(KEY_MYSTERY, false)
        formulasOn = prefs.getBoolean(KEY_FORMULAS, true)

        labView = findViewById(R.id.lab_sort_view)
        movesText = findViewById(R.id.lab_sort_moves)
        difficultySpinner = findViewById(R.id.lab_sort_difficulty_spinner)
        chipMystery = findViewById(R.id.lab_sort_chip_mystery)
        chipFormulas = findViewById(R.id.lab_sort_chip_formulas)
        btnUndo = findViewById(R.id.lab_sort_btn_undo)
        btnPipette = findViewById(R.id.lab_sort_btn_pipette)
        winOverlay = findViewById(R.id.lab_sort_win)
        winCard = findViewById(R.id.lab_sort_win_card)

        val tv = TypedValue()
        theme.resolveAttribute(R.attr.a2uMusicAccent, tv, true)
        accentColor = tv.data
        onAccentColor = ContextCompat.getColor(this, R.color.audio_on_accent)
        textColor = ContextCompat.getColor(this, R.color.startup_text_primary)
        padColor = ContextCompat.getColor(this, R.color.sudoku_pad_button_background)
        defaultStroke = btnPipette.strokeColor
        defaultIconTint = btnPipette.iconTint

        findViewById<ImageButton>(R.id.lab_sort_back_button).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.lab_sort_info_button).setOnClickListener {
            showGameRules(R.string.lab_sort_title, R.string.lab_sort_rules)
        }
        findViewById<View>(R.id.lab_sort_btn_new).setOnClickListener { startNewGame(game.difficulty) }
        findViewById<View>(R.id.lab_sort_btn_restart).setOnClickListener { restartGame() }
        btnUndo.setOnClickListener { undoMove() }
        btnPipette.setOnClickListener { togglePipette() }
        chipMystery.setOnClickListener {
            mysteryOn = !mysteryOn
            prefs.edit { putBoolean(KEY_MYSTERY, mysteryOn) }
            startNewGame(game.difficulty)
        }
        chipFormulas.setOnClickListener {
            formulasOn = !formulasOn
            prefs.edit { putBoolean(KEY_FORMULAS, formulasOn) }
            labView.showFormulas = formulasOn
            updateUI()
        }
        findViewById<View>(R.id.lab_sort_win_close).setOnClickListener { hideWin() }
        findViewById<View>(R.id.lab_sort_win_new).setOnClickListener { startNewGame(game.difficulty) }

        labView.listener = this
        labView.showFormulas = formulasOn

        val restored = loadGame()
        if (restored) {
            mysteryOn = game.mystery
        } else {
            game.newGame(LabSortGame.Difficulty.EASY, mysteryOn)
        }
        knownCompleted = game.completedCount()
        labView.game = game

        setupSpinner()
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        saveGame()
    }

    private fun setupSpinner() {
        val labels = listOf(
            getString(R.string.color_stack_difficulty_easy),
            getString(R.string.color_stack_difficulty_medium),
            getString(R.string.color_stack_difficulty_hard)
        )
        val adapter = ArrayAdapter(this, R.layout.item_spinner_selected, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        ignoreSpinnerChange = true
        difficultySpinner.adapter = adapter
        difficultySpinner.setSelection(game.difficulty.ordinal, false)
        ignoreSpinnerChange = false
        difficultySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreSpinnerChange) return
                val diff = LabSortGame.Difficulty.entries[position]
                if (diff != game.difficulty) startNewGame(diff)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun startNewGame(diff: LabSortGame.Difficulty) {
        game.newGame(diff, mysteryOn)
        victoryRecorded = false
        resetBoardState()
        ignoreSpinnerChange = true
        difficultySpinner.setSelection(diff.ordinal, false)
        ignoreSpinnerChange = false
        saveGame()
    }

    private fun restartGame() {
        game.restart()
        victoryRecorded = false
        resetBoardState()
        saveGame()
    }

    private fun undoMove() {
        if (game.undo()) {
            resetBoardState()
            saveGame()
        }
    }

    /** Après tout changement qui ne vient pas d'un versement : on repart d'un plateau calme. */
    private fun resetBoardState() {
        disarmPipette()
        hideWin()
        knownCompleted = game.completedCount()
        labView.game = game
        updateUI()
    }

    private fun togglePipette() {
        if (game.pipetteCharges <= 0) return
        pipetteArmed = !pipetteArmed
        labView.pipetteArmed = pipetteArmed
        updateUI()
    }

    private fun disarmPipette() {
        pipetteArmed = false
        labView.pipetteArmed = false
    }

    // ── Écouteur de la vue ────────────────────────────────────────────────────────────────────

    override fun onPour(from: Int, to: Int): Int {
        val n = game.pour(from, to)
        if (n > 0) { updateUI(); saveGame() }
        return n
    }

    override fun onPipette(from: Int, to: Int): Boolean {
        val ok = game.pipette(from, to)
        disarmPipette()
        if (ok) { updateUI(); saveGame() }
        return ok
    }

    override fun onSettled() {
        val done = game.completedCount()
        if (game.solved) {
            labView.celebrate()
            rumble()
            lastReward = 0
            if (!victoryRecorded) {
                victoryRecorded = true
                lastReward = NeutrinoRewards.labSort(game.difficulty.ordinal)
                NeutrinoRepository(this).addBalance(lastReward)
                saveGame()
            }
            labView.postDelayed({ if (game.solved && !isFinishing) showWin() }, WIN_DELAY_MS)
        } else if (done > knownCompleted) {
            tick()
        }
        knownCompleted = done
    }

    // ── Victoire ──────────────────────────────────────────────────────────────────────────────

    private fun showWin() {
        findViewById<TextView>(R.id.lab_sort_win_moves).text = getString(R.string.color_stack_moves, game.moves)
        findViewById<TextView>(R.id.lab_sort_win_reward).apply {
            visibility = if (lastReward > 0) View.VISIBLE else View.GONE
            text = getString(R.string.lab_sort_win_reward, lastReward)
        }
        winCard.scaleX = 0.7f
        winCard.scaleY = 0.7f
        winOverlay.alpha = 0f
        winOverlay.visibility = View.VISIBLE
        winOverlay.animate().alpha(1f).setDuration(250).start()
        winCard.animate().scaleX(1f).scaleY(1f).setDuration(450)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    private fun hideWin() {
        winOverlay.animate().cancel()
        winOverlay.visibility = View.GONE
    }

    // ── Vibration : toujours facultative ──────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun vibrate(effect: VibrationEffect) {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        try {
            vibrator.vibrate(effect)
        } catch (_: SecurityException) {
            // Un profil qui refuse le vibreur ne doit jamais interrompre une partie.
        }
    }

    /** Un petit coup sec quand un tube est terminé. */
    private fun tick() = vibrate(VibrationEffect.createOneShot(25, 110))

    /** Un roulement qui monte, pour la victoire. */
    private fun rumble() = vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0, 40, 50, 40, 50, 60, 50, 90, 50, 240),
            intArrayOf(0, 90, 0, 120, 0, 160, 0, 200, 0, 255),
            -1
        )
    )

    // ── Affichage ─────────────────────────────────────────────────────────────────────────────

    private fun updateUI() {
        movesText.text = getString(R.string.color_stack_moves, game.moves)

        btnUndo.text = game.undoLeft.toString()
        btnUndo.alpha = if (game.history.isNotEmpty() && game.undoLeft > 0) 1f else 0.35f

        btnPipette.text = game.pipetteCharges.toString()
        btnPipette.alpha = if (game.pipetteCharges > 0 || pipetteArmed) 1f else 0.35f
        if (pipetteArmed) {
            val green = ColorStateList.valueOf(ARMED_COLOR)
            btnPipette.strokeColor = green
            btnPipette.iconTint = green
            btnPipette.setTextColor(ARMED_COLOR)
        } else {
            btnPipette.strokeColor = defaultStroke
            btnPipette.iconTint = defaultIconTint
            btnPipette.setTextColor(textColor)
        }

        styleChip(chipMystery, mysteryOn)
        styleChip(chipFormulas, formulasOn)
    }

    private fun styleChip(chip: MaterialButton, on: Boolean) {
        chip.backgroundTintList = ColorStateList.valueOf(if (on) accentColor else padColor)
        chip.setTextColor(if (on) onAccentColor else textColor)
    }

    // ── Sauvegarde ────────────────────────────────────────────────────────────────────────────

    private fun saveGame() {
        prefs.edit {
            putString(KEY_SAVE, game.serialize())
            putBoolean(KEY_VICTORY, victoryRecorded)
        }
    }

    private fun loadGame(): Boolean {
        val json = prefs.getString(KEY_SAVE, null) ?: return false
        if (!game.deserialize(json)) return false
        victoryRecorded = prefs.getBoolean(KEY_VICTORY, game.solved)
        return true
    }
}
