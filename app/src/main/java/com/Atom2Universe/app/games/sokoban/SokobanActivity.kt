package com.Atom2Universe.app.games.sokoban

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.util.ImmersiveBottomSheetDialog
import com.Atom2Universe.app.util.Landscape
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deux façons de jouer, choisies par un seul bouton déroulant :
 * - **Niveaux** : la progression, une centaine de niveaux fabriqués sur PC (`SokobanForgeBench`),
 *   de plus en plus riches ; chaque niveau résolu ouvre les suivants.
 * - **Infini** (quatre difficultés) : un niveau aléatoire après l'autre, fabriqué sur la tablette ;
 *   le suivant se prépare pendant qu'on joue et reste gardé si l'on quitte.
 */
class SokobanActivity : ThemedActivity() {

    companion object {
        private const val PREFS = "sokoban_prefs"
        private const val KEY_BEST = "best_"
        private const val KEY_MODE = "mode"
        private const val KEY_LEVEL = "level"
        private const val KEY_LEVEL_MOVES = "lvl_moves_"
        private const val KEY_LEVEL_PUSHES = "lvl_pushes_"
        private const val KEY_NEXT = "next_"
        private const val AUTO_NEXT_DELAY_MS = 2200L
        /** Niveaux pas encore résolus qu'on peut ouvrir d'avance, pour ne jamais rester bloqué. */
        private const val LOOKAHEAD = 3
    }

    private lateinit var board: SokobanBoardView
    private lateinit var tvMessage: TextView
    private lateinit var tvBest: TextView
    private lateinit var tvMoves: TextView
    private lateinit var tvPushes: TextView
    private lateinit var loadingView: View
    private lateinit var modeSpinner: Spinner
    private lateinit var levelButton: TextView
    private lateinit var btnUndo: View
    private lateinit var btnReset: View
    private lateinit var btnNew: View

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private lateinit var levels: List<SokobanLevel>

    /** 0 = les niveaux ; 1 à 4 = l'infini, difficulté `mode - 1`. */
    private var mode = 0
    private var levelNumber = 1
    private val infinite get() = mode > 0
    private val infiniteDifficulty get() = SokobanDifficulty.entries[(mode - 1).coerceAtLeast(0)]

    private var generationJob: Job? = null
    private var prepareJob: Job? = null
    private var prepareFor: SokobanDifficulty? = null
    private var rewarded = false
    private var generating = false

    private val game = SokobanGame()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAutoNext: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sokoban)
        Landscape.bindSidePanel(this)
        enableImmersiveMode()

        board       = findViewById(R.id.sokoban_board)
        tvMessage   = findViewById(R.id.sokoban_message)
        tvBest      = findViewById(R.id.sokoban_best)
        tvMoves     = findViewById(R.id.sokoban_moves)
        tvPushes    = findViewById(R.id.sokoban_pushes)
        loadingView = findViewById(R.id.sokoban_loading)
        modeSpinner = findViewById(R.id.sokoban_difficulty_spinner)
        levelButton = findViewById(R.id.sokoban_level_button)
        btnUndo     = findViewById(R.id.sokoban_btn_undo)
        btnReset    = findViewById(R.id.sokoban_btn_reset)
        btnNew      = findViewById(R.id.sokoban_btn_new)

        levels = SokobanLevels.all(this)
        mode = prefs.getInt(KEY_MODE, 0).coerceIn(0, SokobanDifficulty.entries.size)
        levelNumber = prefs.getInt(KEY_LEVEL, firstUnsolved()).coerceIn(1, levels.size)
        if (!playable(levelNumber)) levelNumber = firstUnsolved()

        findViewById<ImageButton>(R.id.sokoban_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.sokoban_info_button).setOnClickListener {
            showGameRules(R.string.sokoban_title, R.string.sokoban_rules)
        }
        btnNew.setOnClickListener {
            if (!infinite && !playable(levelNumber + 1)) return@setOnClickListener
            cancelAutoNext(); next()
        }
        btnReset.setOnClickListener {
            if (generating) return@setOnClickListener
            cancelAutoNext(); game.reset(); board.loadGame(game); onBoardChanged()
            showMessage(null)
        }
        btnUndo.setOnClickListener {
            if (!generating && game.undo()) {
                cancelAutoNext()
                board.loadGame(game)
                onBoardChanged()
                showMessage(null)
            }
        }
        levelButton.setOnClickListener { showLevelPicker() }
        // Debout, le nom du dépôt partage sa ligne avec les boutons : coupé, il s'efface.
        findViewById<TextView?>(R.id.sokoban_identity_label)?.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val cut = ((v as TextView).layout?.getEllipsisCount(0) ?: 0) > 0
            v.visibility = if (cut) View.INVISIBLE else View.VISIBLE
        }

        findViewById<MaterialButton>(R.id.sokoban_up).setOnClickListener { board.move(SokobanDir.UP) }
        findViewById<MaterialButton>(R.id.sokoban_down).setOnClickListener { board.move(SokobanDir.DOWN) }
        findViewById<MaterialButton>(R.id.sokoban_left).setOnClickListener { board.move(SokobanDir.LEFT) }
        findViewById<MaterialButton>(R.id.sokoban_right).setOnClickListener { board.move(SokobanDir.RIGHT) }

        board.onChanged = { onBoardChanged() }
        board.onSolved = { onSolved() }

        buildModeSpinner()
        startMode()
    }

    /** Couché : le dépôt au milieu, réglages et flèches de part et d'autre ; le niveau ne change pas. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Landscape.swapLayout(this, R.layout.activity_sokoban)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Le message de fin de niveau, par-dessus le dépôt ; rien pendant la partie. */
    private fun showMessage(text: String?) {
        tvMessage.text = text
        tvMessage.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** Une icône inactive s'estompe (une icône seule ne change pas d'allure quand on la désactive). */
    private fun setActive(view: View, active: Boolean) {
        view.isEnabled = active
        view.alpha = if (active) 1f else 0.35f
    }

    // ── Mode : niveaux ou infini ─────────────────────────────────────────────

    /** Un seul bouton qui montre le mode choisi et déroule les autres sous le doigt. */
    private fun buildModeSpinner() {
        val labels = listOf(getString(R.string.sokoban_mode_levels)) + SokobanDifficulty.entries.map { diff ->
            getString(R.string.sokoban_mode_infinite, getString(when (diff) {
                SokobanDifficulty.EASY   -> R.string.sokoban_diff_easy
                SokobanDifficulty.MEDIUM -> R.string.sokoban_diff_medium
                SokobanDifficulty.HARD   -> R.string.sokoban_diff_hard
                SokobanDifficulty.EXPERT -> R.string.sokoban_diff_expert
            }))
        }
        val adapter = ArrayAdapter(this, R.layout.item_spinner_selected, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        modeSpinner.adapter = adapter
        modeSpinner.setSelection(mode, false)
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos == mode) return
                mode = pos
                prefs.edit { putInt(KEY_MODE, mode) }
                startMode()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun startMode() {
        levelButton.visibility = if (infinite) View.GONE else View.VISIBLE
        if (infinite) {
            game.difficulty = infiniteDifficulty
            startRandom(infiniteDifficulty)
        } else {
            prepareJob?.cancel(); prepareFor = null
            loadLevel(levelNumber)
        }
    }

    private fun next() {
        if (infinite) startRandom(infiniteDifficulty)
        else if (levelNumber < levels.size) loadLevel(levelNumber + 1)
    }

    private fun showBoard() {
        generating = false
        board.isEnabled = true
        setActive(btnReset, true)
        loadingView.visibility = View.GONE
        board.visibility = View.VISIBLE
    }

    private fun showLoading() {
        generating = true
        board.isEnabled = false
        setActive(btnUndo, false)
        setActive(btnReset, false)
        showMessage(null)
        // Rien de l'ancien niveau pendant que le suivant se fabrique.
        findViewById<TextView>(R.id.sokoban_delivery).text = ""
        findViewById<TextView>(R.id.sokoban_target).text = ""
        loadingView.visibility = View.VISIBLE
        board.visibility = View.INVISIBLE
    }

    // ── Les niveaux ──────────────────────────────────────────────────────────

    private fun solvedMoves(n: Int) = prefs.getInt(KEY_LEVEL_MOVES + n, 0)
    private fun solvedPushes(n: Int) = prefs.getInt(KEY_LEVEL_PUSHES + n, 0)
    private fun isSolved(n: Int) = solvedMoves(n) > 0

    private fun lastSolved(): Int = (levels.size downTo 1).firstOrNull { isSolved(it) } ?: 0

    private fun firstUnsolved(): Int = (1..levels.size).firstOrNull { !isSolved(it) } ?: 1

    /** Un niveau s'ouvre s'il est résolu ou s'il suit de près le plus loin des niveaux résolus. */
    private fun playable(n: Int) = n in 1..levels.size && (isSolved(n) || n <= lastSolved() + LOOKAHEAD)

    private fun loadLevel(n: Int) {
        cancelAutoNext()
        generationJob?.cancel()
        levelNumber = n
        prefs.edit { putInt(KEY_LEVEL, n) }
        rewarded = false
        showBoard()
        showMessage(null)
        game.load(levels[n - 1].puzzle)
        board.loadGame(game)
        levelButton.text = getString(R.string.sokoban_level, n)
        onBoardChanged()
        updateBest()
    }

    // ── L'infini ─────────────────────────────────────────────────────────────

    /** Le niveau aléatoire déjà prêt pour cette difficulté (gardé d'une partie à l'autre). */
    private fun takeStored(diff: SokobanDifficulty): SokobanPuzzle? {
        val text = prefs.getString(KEY_NEXT + diff.name, null) ?: return null
        prefs.edit { remove(KEY_NEXT + diff.name) }
        return runCatching { SokobanXsb.parse(text).firstOrNull()?.first }.getOrNull()
    }

    /** Prépare en arrière-plan le prochain niveau aléatoire, pour que « suivant » soit immédiat. */
    private fun prepare(diff: SokobanDifficulty) {
        if (prefs.contains(KEY_NEXT + diff.name)) return
        if (prepareFor == diff && prepareJob?.isActive == true) return
        prepareJob?.cancel()
        prepareFor = diff
        prepareJob = lifecycleScope.launch {
            val puzzle = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                SokobanGenerator.generate(diff) { context.ensureActive() }
            } ?: return@launch
            prefs.edit { putString(KEY_NEXT + diff.name, SokobanXsb.format(puzzle, listOf("pushes" to puzzle.optimalPushes))) }
        }
    }

    private fun startRandom(diff: SokobanDifficulty) {
        cancelAutoNext()
        generationJob?.cancel()
        if (prepareFor != diff) { prepareJob?.cancel(); prepareFor = null }
        val stored = takeStored(diff)
        if (stored != null) {
            loadRandom(stored)
            prepare(diff)
            return
        }
        showLoading()
        generationJob = lifecycleScope.launch {
            // Un niveau est peut-être déjà en route : on l'attend plutôt que d'en lancer un autre.
            if (prepareFor == diff) prepareJob?.join()
            val puzzle = takeStored(diff) ?: withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                SokobanGenerator.generate(diff) { context.ensureActive() }
            }
            if (puzzle == null) {
                showBoard()
                onBoardChanged()
                showMessage(getString(R.string.sokoban_error))
                return@launch
            }
            loadRandom(puzzle)
            prepare(diff)
        }
    }

    private fun loadRandom(puzzle: SokobanPuzzle) {
        showBoard()
        showMessage(null)
        rewarded = false
        game.load(puzzle)
        board.loadGame(game)
        onBoardChanged()
        updateBest()
    }

    // ── Partie ───────────────────────────────────────────────────────────────

    private fun onBoardChanged() {
        tvMoves.text = getString(R.string.sokoban_moves, game.moves)
        tvPushes.text = getString(R.string.sokoban_pushes, game.pushes)
        setActive(btnUndo, game.canUndo && !generating)
        setActive(btnNew, infinite || playable(levelNumber + 1))
        val puzzle = game.puzzle
        findViewById<TextView>(R.id.sokoban_delivery).text = if (puzzle == null) "" else
            getString(R.string.sokoban_delivery, game.boxes.count { it in puzzle.goals }, puzzle.goals.size)
        findViewById<TextView>(R.id.sokoban_target).text = if (puzzle == null) "" else
            getString(R.string.sokoban_target, puzzle.optimalPushes)
    }

    private fun onSolved() {
        val puzzle = game.puzzle ?: return
        val perfect = game.pushes <= puzzle.optimalPushes
        val best: Int
        if (infinite) {
            val diff = infiniteDifficulty
            best = saveBest(KEY_BEST + diff.name, game.moves)
            if (!rewarded) NeutrinoRepository(this).addBalance(NeutrinoRewards.sokoban(diff.ordinal))
        } else {
            val n = levelNumber
            val firstTime = !isSolved(n)
            best = saveBest(KEY_LEVEL_MOVES + n, game.moves)
            val pushes = solvedPushes(n)
            if (pushes == 0 || game.pushes < pushes) prefs.edit { putInt(KEY_LEVEL_PUSHES + n, game.pushes) }
            if (firstTime && !rewarded) {
                NeutrinoRepository(this).addBalance(NeutrinoRewards.sokobanLevel(levels[n - 1].chapter))
            }
        }
        rewarded = true
        updateBest()
        onBoardChanged()
        showMessage(getString(if (perfect) R.string.sokoban_solved_perfect else R.string.sokoban_solved, game.moves, best))

        if (infinite || levelNumber < levels.size) {
            val r = Runnable { pendingAutoNext = null; next() }
            pendingAutoNext = r
            handler.postDelayed(r, AUTO_NEXT_DELAY_MS)
        }
    }

    private fun saveBest(key: String, moves: Int): Int {
        val current = prefs.getInt(key, 0)
        val best = if (current == 0 || moves < current) moves else current
        if (best != current) prefs.edit { putInt(key, best) }
        return best
    }

    private fun updateBest() {
        val key = if (infinite) KEY_BEST + infiniteDifficulty.name else KEY_LEVEL_MOVES + levelNumber
        val current = prefs.getInt(key, 0)
        tvBest.text = if (current > 0) getString(R.string.sokoban_best, current) else ""
    }

    private fun cancelAutoNext() {
        pendingAutoNext?.let { handler.removeCallbacks(it) }
        pendingAutoNext = null
    }

    // ── Choix du niveau ──────────────────────────────────────────────────────

    private fun dp(value: Float) = (value * resources.displayMetrics.density + .5f).toInt()

    /**
     * Les niveaux par chapitre, en tuiles : pleine = résolu avec le moins de poussées possible,
     * teintée = résolu, estompée = pas encore ouverte. La tuile du niveau en cours est cerclée.
     */
    private fun showLevelPicker() {
        val palette = KitPalette.from(this)
        val sheet = ImmersiveBottomSheetDialog(this)
        val wide = resources.configuration.screenWidthDp >= 600
        val tile = dp(if (wide) 62f else 50f)
        val gap = dp(if (wide) 6f else 4f)
        val usable = minOf(resources.displayMetrics.widthPixels, dp(760f)) - dp(40f)
        val columns = (usable / (tile + 2 * gap)).coerceIn(5, 10)
        val corner = AppearanceStyle.corner(this, 12f)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(18f), dp(20f), dp(28f))
            setBackgroundColor(palette.surface)
        }
        root.addView(TextView(this).apply {
            setText(R.string.sokoban_mode_levels)
            textSize = if (wide) 24f else 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.text)
        })
        var currentTile: View? = null
        for ((chapter, group) in levels.groupBy { it.chapter }.toSortedMap()) {
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(gap, dp(16f), gap, dp(6f))
            }
            header.addView(TextView(this).apply {
                text = getString(R.string.sokoban_chapter, chapter)
                textSize = if (wide) 17f else 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.secondary)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(TextView(this).apply {
                text = getString(R.string.sokoban_chapter_count, group.count { isSolved(it.number) }, group.size)
                textSize = if (wide) 16f else 14f
                setTextColor(palette.secondary)
            })
            // Le titre du chapitre prend la largeur de sa grille, et le tout se centre.
            val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            block.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            val grid = GridLayout(this).apply { columnCount = columns }
            for (level in group) {
                val n = level.number
                val solved = isSolved(n)
                val perfect = solved && solvedPushes(n) <= level.puzzle.optimalPushes
                val open = playable(n)
                val view = TextView(this).apply {
                    text = n.toString()
                    gravity = Gravity.CENTER
                    textSize = if (wide) 19f else 16f
                    typeface = if (n == levelNumber) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextColor(if (perfect) palette.onAccent else palette.text)
                    background = GradientDrawable().apply {
                        cornerRadius = corner
                        setColor(when {
                            perfect -> palette.accent
                            solved -> palette.blend(palette.raised, palette.accent, .32f)
                            else -> palette.raised
                        })
                        if (n == levelNumber) setStroke(dp(2.5f), palette.text)
                        else setStroke(dp(1f), palette.outline)
                    }
                    alpha = if (open) 1f else .32f
                    if (open) setOnClickListener {
                        sheet.dismiss()
                        if (n != levelNumber || game.puzzle !== level.puzzle) loadLevel(n)
                    }
                }
                grid.addView(view, GridLayout.LayoutParams().apply {
                    width = tile; height = tile
                    setMargins(gap, gap, gap, gap)
                })
                if (n == levelNumber) currentTile = view
            }
            block.addView(grid)
            root.addView(block, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        val scroll = NestedScrollView(this).apply {
            setBackgroundColor(palette.surface)
            addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        sheet.setContentView(scroll)
        sheet.setOnShowListener {
            sheet.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                BottomSheetBehavior.from(it).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
            currentTile?.let { tileView ->
                scroll.post {
                    val grid = tileView.parent as View
                    val block = grid.parent as View
                    scroll.scrollTo(0, (block.top + grid.top + tileView.top - scroll.height / 3).coerceAtLeast(0))
                }
            }
        }
        sheet.show()
    }
}
