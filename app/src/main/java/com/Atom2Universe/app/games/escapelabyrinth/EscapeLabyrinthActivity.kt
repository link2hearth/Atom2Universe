package com.Atom2Universe.app.games.escapelabyrinth

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class EscapeLabyrinthActivity : ThemedActivity() {

    // ── Views ────────────────────────────────────────────────────────────────

    private lateinit var mazeView: EscapeLabyrinthView
    private lateinit var tvStatus: TextView
    private lateinit var tvTurns: TextView
    private lateinit var tvOrbs: TextView
    private lateinit var tvDifficulty: TextView
    private lateinit var btnNew: Button
    private lateinit var btnEasy: Button
    private lateinit var btnMedium: Button
    private lateinit var btnHard: Button
    private lateinit var loadingOverlay: View

    // ── State ────────────────────────────────────────────────────────────────

    private var currentDifficulty = Difficulty.EASY
    private var currentLevel: Level? = null
    private var currentPlay: PlayState? = null
    private var isGenerating = false

    private val prefs by lazy { getSharedPreferences("escape_labyrinth_prefs", MODE_PRIVATE) }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_escape_labyrinth)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bindViews()
        setupListeners()
        loadPrefs()
        updateDifficultyButtons()
        startNewGame()
    }

    override fun onPause() {
        super.onPause()
        savePrefs()
    }

    // ── View binding ─────────────────────────────────────────────────────────

    private fun bindViews() {
        mazeView     = findViewById(R.id.escape_maze_view)
        tvStatus     = findViewById(R.id.escape_tv_status)
        tvTurns      = findViewById(R.id.escape_tv_turns)
        tvOrbs       = findViewById(R.id.escape_tv_orbs)
        tvDifficulty = findViewById(R.id.escape_tv_difficulty)
        btnNew       = findViewById(R.id.escape_btn_new)
        btnEasy      = findViewById(R.id.escape_btn_easy)
        btnMedium    = findViewById(R.id.escape_btn_medium)
        btnHard      = findViewById(R.id.escape_btn_hard)
        loadingOverlay = findViewById(R.id.escape_loading_overlay)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.escape_btn_back).setOnClickListener { finish() }
        btnNew.setOnClickListener { startNewGame() }

        btnEasy.setOnClickListener   { changeDifficulty(Difficulty.EASY) }
        btnMedium.setOnClickListener { changeDifficulty(Difficulty.MEDIUM) }
        btnHard.setOnClickListener   { changeDifficulty(Difficulty.HARD) }

        mazeView.onSwipe = { dr, dc -> attemptMove(dr, dc) }
        mazeView.onWait  = { attemptWait() }
    }

    // ── Prefs ────────────────────────────────────────────────────────────────

    private fun loadPrefs() {
        val diffName = prefs.getString("difficulty", Difficulty.EASY.name) ?: Difficulty.EASY.name
        currentDifficulty = runCatching { Difficulty.valueOf(diffName) }.getOrDefault(Difficulty.EASY)
    }

    private fun savePrefs() {
        prefs.edit().putString("difficulty", currentDifficulty.name).apply()
    }

    // ── Difficulty ───────────────────────────────────────────────────────────

    private fun changeDifficulty(diff: Difficulty) {
        if (diff == currentDifficulty) return
        currentDifficulty = diff
        updateDifficultyButtons()
        startNewGame()
    }

    private fun updateDifficultyButtons() {
        val sel = 0xFFFFFFFF.toInt(); val unsel = 0x55FFFFFF.toInt()
        btnEasy.setTextColor(if (currentDifficulty == Difficulty.EASY)   sel else unsel)
        btnMedium.setTextColor(if (currentDifficulty == Difficulty.MEDIUM) sel else unsel)
        btnHard.setTextColor(if (currentDifficulty == Difficulty.HARD)   sel else unsel)
        tvDifficulty.text = when (currentDifficulty) {
            Difficulty.EASY   -> getString(R.string.escape_diff_easy)
            Difficulty.MEDIUM -> getString(R.string.escape_diff_medium)
            Difficulty.HARD   -> getString(R.string.escape_diff_hard)
        }
    }

    // ── Game flow ────────────────────────────────────────────────────────────

    private fun startNewGame() {
        if (isGenerating) return
        isGenerating = true
        setInputEnabled(false)
        loadingOverlay.visibility = View.VISIBLE
        tvStatus.text = getString(R.string.escape_status_generating)

        val seed = Random.nextLong()
        val diff = currentDifficulty

        lifecycleScope.launch {
            val level = withContext(Dispatchers.Default) {
                EscapeLabyrinthGame.create(seed, diff)
            }
            isGenerating = false
            loadingOverlay.visibility = View.GONE
            if (level == null) {
                tvStatus.text = getString(R.string.escape_status_gen_error)
                return@launch
            }
            currentLevel = level
            currentPlay = EscapeLabyrinthGame.initialPlay(level)
            refreshView()
            setInputEnabled(true)
            tvStatus.text = getString(R.string.escape_status_ready)
        }
    }

    // ── Action handling ──────────────────────────────────────────────────────

    private fun attemptMove(dr: Int, dc: Int) {
        val level = currentLevel ?: return
        val play = currentPlay ?: return
        if (play.completed || play.caught) { startNewGame(); return }

        val result = EscapeLabyrinthGame.move(level, play, dr, dc)
        currentPlay = result.newState ?: play
        refreshView()
        handleOutcome(result.outcome, currentPlay!!)
    }

    private fun attemptWait() {
        val level = currentLevel ?: return
        val play = currentPlay ?: return
        if (play.completed || play.caught) { startNewGame(); return }

        currentPlay = EscapeLabyrinthGame.wait(level, play)
        refreshView()
        val p = currentPlay!!
        if (p.caught) handleOutcome(MoveOutcome.BLOCKED_BY_GUARD, p)
        else showTurnStatus(p)
    }

    private fun handleOutcome(outcome: MoveOutcome, play: PlayState) {
        when (outcome) {
            MoveOutcome.WIN -> {
                val lvl = currentLevel!!
                val perfect = play.turn <= lvl.solveTurns
                // EASY=2, MEDIUM=5, HARD=10 ; ×2 si parcours parfait
                NeutrinoRepository(this).addBalance(
                    NeutrinoRewards.escape(currentDifficulty.ordinal, perfect)
                )
                tvStatus.text = if (perfect)
                    getString(R.string.escape_status_win_perfect, play.turn)
                else
                    getString(R.string.escape_status_win, play.turn, lvl.solveTurns)
                setInputEnabled(false)
            }
            MoveOutcome.BLOCKED_BY_GUARD,
            MoveOutcome.IN_VISION -> {
                tvStatus.text = getString(R.string.escape_status_caught, play.turn)
                setInputEnabled(false)
            }
            MoveOutcome.MISSING_ORBS -> {
                val lvl = currentLevel!!
                val missing = lvl.bonuses.size - play.collectedIds.size
                tvStatus.text = getString(R.string.escape_status_missing_orbs, missing)
            }
            MoveOutcome.WALL -> tvStatus.text = getString(R.string.escape_status_wall)
            MoveOutcome.OK   -> showTurnStatus(play)
        }
    }

    private fun showTurnStatus(play: PlayState) {
        val lvl = currentLevel ?: return
        val collected = play.collectedIds.size
        val total = lvl.bonuses.size
        tvStatus.text = if (collected < total)
            getString(R.string.escape_status_playing, play.turn, collected, total)
        else
            getString(R.string.escape_status_reach_exit)
    }

    // ── View refresh ─────────────────────────────────────────────────────────

    private fun refreshView() {
        val lvl = currentLevel ?: return
        val play = currentPlay ?: return
        val vision = EscapeLabyrinthGame.guardVisionAt(lvl, play.guardPhase)
        mazeView.level = lvl
        mazeView.playState = play
        mazeView.guardVision = vision

        val collected = play.collectedIds.size
        val total = lvl.bonuses.size
        tvTurns.text = buildTurnSpan(play.turn, lvl.solveTurns)
        tvOrbs.text  = getString(R.string.escape_orbs_label, collected, total)
    }

    /** Affiche "Tour 12/52" avec le 12 en rouge si > optimal. */
    private fun buildTurnSpan(turn: Int, optimal: Int): SpannableStringBuilder {
        val prefix = getString(R.string.escape_turns_prefix)   // "Tour "
        val suffix = "/$optimal"
        val turnStr = turn.toString()
        val sb = SpannableStringBuilder(prefix).append(turnStr).append(suffix)
        if (turn > optimal) {
            val start = prefix.length
            sb.setSpan(ForegroundColorSpan(Color.parseColor("#FF5555")),
                start, start + turnStr.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun setInputEnabled(enabled: Boolean) {
        mazeView.isClickable = enabled
        mazeView.isFocusable = enabled
    }
}
