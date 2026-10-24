package com.Atom2Universe.app.games.atomblocks

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.followImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.Executors

/** An untimed block puzzle: every mode, hint and second chance is available offline. */
class AtomBlocksActivity : ThemedActivity() {
    private val store by lazy { AtomBlocksStore(this) }
    private val palette by lazy { SciencePalette(this) }
    private val game = AtomBlocksEngine()
    private lateinit var audio: AtomBlocksAudio
    private lateinit var root: LinearLayout
    private lateinit var board: AtomBlocksBoardView
    private lateinit var boardFrame: FrameLayout
    private lateinit var score: TextView
    private lateinit var best: TextView
    private lateinit var chain: TextView
    private lateinit var goalText: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var undo: MaterialButton
    private lateinit var hint: MaterialButton
    private var resultPanel: View? = null
    private var playing = false
    private var resumed = false
    private var sessionLoaded = false
    private val handler = Handler(Looper.getMainLooper())
    private val hintWorker = Executors.newSingleThreadExecutor()
    private var positionVersion = 0L
    private var hintPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = AtomBlocksAudio(this).apply {
            soundEnabled = store.prefs.getBoolean("sound", true)
            musicEnabled = store.prefs.getBoolean("music", true)
        }
        val last = store.lastKey()
        sessionLoaded = last != null && store.load(last, game)
        if (savedInstanceState?.getBoolean("playing") == true && sessionLoaded) showGame() else showMenu()
        enableImmersiveMode()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (playing) showMenu() else finish() }
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(text: String, size: Float = 14f, secondary: Boolean = false, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size
        setTextColor(if (secondary) palette.secondary else palette.text)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private fun button(text: String, primary: Boolean = false, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text; isAllCaps = false; textSize = 14f
        minHeight = dp(48); minimumHeight = dp(48)
        insetTop = dp(3); insetBottom = dp(3)
        cornerRadius = AppearanceStyle.corner(this@AtomBlocksActivity, 14f).toInt()
        backgroundTintList = ColorStateList.valueOf(if (primary) palette.accent else palette.raised)
        setTextColor(if (primary) palette.onAccent else palette.text)
        strokeColor = ColorStateList.valueOf(palette.outline); strokeWidth = dp(1)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { action() }
    }
    private fun icon(drawable: Int, description: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(drawable); setColorFilter(palette.text)
        val attr = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, attr, true)
        setBackgroundResource(attr.resourceId)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        contentDescription = getString(description); setOnClickListener { action() }
    }
    private fun LinearLayout.addSpaced(view: View, gap: Int = 8) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap) })
    }
    private fun makeRoot(title: String): LinearLayout {
        root = column().apply { background = AppearanceStyle.screen(this@AtomBlocksActivity) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
        header.addView(icon(R.drawable.ic_app_back, R.string.back) { if (playing) showMenu() else finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(label(title, 20f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(icon(R.drawable.ic_info, R.string.kit_rules) { showGameRules(R.string.ab_title, R.string.ab_rules) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(icon(android.R.drawable.ic_menu_preferences, R.string.ab_settings) { showSettings() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        setContentView(root)
        return root
    }

    private fun showMenu() {
        invalidateHint()
        handler.removeCallbacksAndMessages(null)
        if (playing) save()
        playing = false
        audio.pause()
        makeRoot(getString(R.string.ab_title))
        val scroll = ScrollView(this)
        val content = column().apply { setPadding(dp(20), dp(4), dp(20), dp(24)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(View(this).apply {
            background = AtomBlocksHubTileDrawable(this@AtomBlocksActivity)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, dp(160)))
        content.addSpaced(label(getString(R.string.ab_tagline), 24f, bold = true), 16)
        content.addSpaced(label(getString(R.string.ab_welcome), secondary = true))
        if (sessionLoaded) content.addSpaced(button(getString(R.string.ab_resume, sessionTitle()), true) { showGame() }, 14)
        content.addSpaced(modeCard(R.string.ab_classic, R.string.ab_classic_desc) { openMode(AtomBlocksEngine.Mode.CLASSIC) }, 16)
        content.addSpaced(modeCard(R.string.ab_adventure, R.string.ab_adventure_desc) { chooseLevel() })
        content.addSpaced(modeCard(R.string.ab_zen, R.string.ab_zen_desc) { openMode(AtomBlocksEngine.Mode.ZEN) })
        content.addSpaced(modeCard(R.string.ab_daily, R.string.ab_daily_desc) { openDaily() })
        content.addSpaced(label(getString(R.string.ab_free), 12f, secondary = true).apply { gravity = Gravity.CENTER }, 16)
    }

    private fun modeCard(title: Int, desc: Int, action: () -> Unit): View = column().apply {
        background = palette.shape(palette.surface)
        setPadding(dp(16), dp(10), dp(16), dp(12))
        addView(button(getString(title), action = action), LinearLayout.LayoutParams(-1, -2))
        addSpaced(label(getString(desc), 13f, secondary = true), 3)
    }

    private fun openMode(mode: AtomBlocksEngine.Mode, level: Int = 1, seed: Long = System.nanoTime()) {
        if (playing) save()
        val key = store.key(mode, level, seed)
        if (!store.load(key, game)) game.newGame(mode, level, seed)
        sessionLoaded = true
        save()
        showGame()
    }

    private fun chooseLevel() {
        val entries = Array(AtomBlocksEngine.ADVENTURE_LEVELS) { i ->
            getString(if (store.completed(i + 1)) R.string.ab_level_complete else R.string.ab_level, i + 1)
        }
        ImmersiveAlertDialogBuilder(this).setTitle(R.string.ab_choose_level)
            .setItems(entries) { _, index -> openMode(AtomBlocksEngine.Mode.ADVENTURE, index + 1, 1000L + index) }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun openDaily() {
        val days = List(7) { LocalDate.now().minusDays(it.toLong()) }
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(resources.configuration.locales[0])
        val entries = (days.map { it.format(format) } + getString(R.string.ab_daily_archive)).toTypedArray()
        ImmersiveAlertDialogBuilder(this).setTitle(R.string.ab_choose_daily)
            .setItems(entries) { _, index ->
                if (index < days.size) openMode(AtomBlocksEngine.Mode.DAILY, seed = days[index].toEpochDay())
                else {
                    val today = LocalDate.now()
                    android.app.DatePickerDialog(this, { _, year, month, day ->
                        openMode(AtomBlocksEngine.Mode.DAILY, seed = LocalDate.of(year, month + 1, day).toEpochDay())
                    }, today.year, today.monthValue - 1, today.dayOfMonth).apply {
                        followImmersiveMode(); show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun sessionTitle(): String = when (game.mode) {
        AtomBlocksEngine.Mode.CLASSIC -> getString(R.string.ab_classic)
        AtomBlocksEngine.Mode.ZEN -> getString(R.string.ab_zen)
        AtomBlocksEngine.Mode.ADVENTURE -> getString(R.string.ab_level, game.level)
        AtomBlocksEngine.Mode.DAILY -> getString(R.string.ab_daily_date, LocalDate.ofEpochDay(game.seed).format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(resources.configuration.locales[0])))
    }

    private fun showGame() {
        invalidateHint()
        handler.removeCallbacksAndMessages(null)
        audio.pause()
        playing = true
        resultPanel = null
        makeRoot(sessionTitle())
        board = AtomBlocksBoardView(this).apply {
            game = this@AtomBlocksActivity.game
            effectsEnabled = store.prefs.getBoolean("effects", true)
            onSelect = { audio.select() }
            onPlace = { slot, row, col -> place(slot, row, col) }
        }
        boardFrame = FrameLayout(this).apply { addView(board, FrameLayout.LayoutParams(-1, -1)) }
        val stats = LinearLayout(this)
        score = stat(stats, R.string.ab_score)
        best = stat(stats, R.string.ab_best)
        chain = stat(stats, R.string.ab_combo)
        goalText = label("", 13f, secondary = true).apply { setPadding(dp(12), dp(4), dp(12), dp(4)) }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(palette.accent)
            progressBackgroundTintList = ColorStateList.valueOf(palette.raised)
        }
        status = label(getString(R.string.ab_instruction), 12f, secondary = true).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), dp(4), dp(12), dp(4))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val controls = LinearLayout(this).apply { setPadding(dp(6), 0, dp(6), dp(6)) }
        undo = button(getString(R.string.ab_undo)) { undoMove() }
        hint = button(getString(R.string.ab_hint)) { requestHint() }
        val more = button(getString(R.string.ab_more)) { showActions() }
        listOf(undo, hint, more).forEach { controls.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(3); marginEnd = dp(3) }) }

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            controls.removeAllViews()
            val body = LinearLayout(this)
            val sidebar = column().apply { setPadding(dp(8), dp(4), dp(8), dp(8)) }
            sidebar.addView(stats); sidebar.addView(goalText)
            sidebar.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
            sidebar.addSpaced(status)
            sidebar.addSpaced(undo); sidebar.addSpaced(hint); sidebar.addSpaced(more)
            val scroll = ScrollView(this).apply { addView(sidebar) }
            body.addView(scroll, LinearLayout.LayoutParams(dp(235), -1))
            body.addView(boardFrame, LinearLayout.LayoutParams(0, -1, 1f))
            root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            root.addView(stats); root.addView(goalText)
            root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)).apply { marginStart = dp(14); marginEnd = dp(14) })
            root.addView(boardFrame, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(status); root.addView(controls)
        }
        updateHud()
        if (resumed) audio.resume()
        if (game.won || game.blocked) showResult()
    }

    private fun stat(row: LinearLayout, title: Int): TextView {
        val block = column().apply { gravity = Gravity.CENTER; setPadding(dp(4), dp(5), dp(4), dp(5)) }
        block.addView(label(getString(title), 11f, secondary = true))
        val value = label("", 20f, bold = true)
        block.addView(value)
        row.addView(block, LinearLayout.LayoutParams(0, -2, 1f))
        return value
    }

    private fun updateHud() {
        score.text = getString(R.string.ab_number, game.score)
        best.text = getString(R.string.ab_number, maxOf(store.best(game.mode), game.score))
        chain.text = getString(R.string.ab_multiplier, game.combo)
        undo.isEnabled = game.canUndo
        hint.isEnabled = !hintPending && !game.won && !game.blocked
        val goal = game.goal
        if (goal == null) {
            goalText.text = getString(R.string.ab_endless_progress, game.lines, game.moves)
            progress.visibility = View.GONE
        } else {
            val goals = mutableListOf<String>()
            val fractions = mutableListOf<Float>()
            if (goal.score > 0) {
                goals += getString(R.string.ab_score_goal, game.score, goal.score)
                fractions += (game.score.toFloat() / goal.score).coerceAtMost(1f)
            }
            if (goal.lines > 0) {
                goals += getString(R.string.ab_lines_goal, game.lines, goal.lines)
                fractions += (game.lines.toFloat() / goal.lines).coerceAtMost(1f)
            }
            if (goal.gems > 0) {
                goals += getString(R.string.ab_gems_goal, game.collectedGems, goal.gems)
                fractions += (game.collectedGems.toFloat() / goal.gems).coerceAtMost(1f)
            }
            goalText.text = goals.joinToString(getString(R.string.ab_separator))
            progress.visibility = View.VISIBLE
            progress.progress = (fractions.average() * 1000).toInt()
            progress.contentDescription = goalText.text
        }
    }

    private fun place(slot: Int, row: Int, col: Int) {
        if (resultPanel != null) return
        val result = game.place(slot, row, col) ?: return
        invalidateHint()
        board.showMove(result)
        if (store.prefs.getBoolean("haptics", true)) board.performHapticFeedback(
            if (result.lines > 0) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
        if (result.lines > 0) audio.clear(result.lines, result.combo, result.allClear) else audio.place()
        status.text = when {
            result.allClear -> getString(R.string.ab_all_clear)
            result.lines > 0 -> getString(R.string.ab_clear_status, result.lines, result.scoreGained, result.combo)
            else -> getString(R.string.ab_instruction)
        }
        if (game.blocked && game.mode == AtomBlocksEngine.Mode.ZEN) {
            game.rescue(rememberUndo = false); board.refresh(); audio.rescue(); status.setText(R.string.ab_zen_rescue)
        }
        save(); updateHud()
        if (game.won || game.blocked) {
            board.celebrate(game.won)
            if (game.won) audio.win() else audio.lose()
            handler.postDelayed({ if (playing && resumed && (game.won || game.blocked)) showResult() }, 850L)
        }
    }

    private fun undoMove() {
        handler.removeCallbacksAndMessages(null)
        if (!game.undo()) return
        invalidateHint()
        removeResult(); board.game = game; audio.undo()
        status.setText(R.string.ab_undone)
        save(); updateHud()
        if (game.won || game.blocked) showResult()
    }

    private fun rescue() {
        if (!game.rescue()) return
        invalidateHint()
        handler.removeCallbacksAndMessages(null)
        removeResult(); board.game = game; audio.rescue()
        status.setText(R.string.ab_rescued)
        save(); updateHud()
    }

    private fun showActions() {
        val actions = arrayOf(getString(R.string.ab_rescue), getString(R.string.ab_restart), getString(R.string.ab_menu))
        ImmersiveAlertDialogBuilder(this).setTitle(R.string.ab_more).setItems(actions) { _, index ->
            when (index) { 0 -> rescue(); 1 -> confirmRestart(); else -> showMenu() }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    /** Solve a detached position off the UI thread; never apply an obsolete suggestion. */
    private fun requestHint() {
        if (hintPending || game.won || game.blocked) return
        val version = positionVersion
        val snapshot = game.snapshot().copy(history = emptyList())
        hintPending = true
        hint.isEnabled = false
        status.setText(R.string.ab_hint_searching)
        hintWorker.execute {
            val solver = AtomBlocksEngine()
            val move = if (solver.restore(snapshot)) solver.hint() else null
            handler.post {
                if (!isDestroyed && playing && resumed && positionVersion == version) {
                    hintPending = false
                    board.showHint(move)
                    status.setText(if (move == null) R.string.ab_no_hint else R.string.ab_hint_status)
                    updateHud()
                }
            }
        }
    }

    private fun invalidateHint() { positionVersion++; hintPending = false }

    private fun confirmRestart() {
        ImmersiveAlertDialogBuilder(this).setTitle(R.string.ab_restart)
            .setMessage(R.string.ab_restart_message)
            .setPositiveButton(R.string.ab_restart) { _, _ -> restart() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun restart() {
        val seed = if (game.mode == AtomBlocksEngine.Mode.DAILY || game.mode == AtomBlocksEngine.Mode.ADVENTURE) game.seed else System.nanoTime()
        game.newGame(game.mode, game.level, seed); save(); showGame()
    }

    private fun removeResult() {
        resultPanel?.let { boardFrame.removeView(it) }; resultPanel = null
        board.isEnabled = true
        board.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun showResult() {
        if (resultPanel != null) return
        board.clearSelection()
        board.isEnabled = false
        board.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val card = column().apply {
            background = palette.shape(palette.surface, 22f)
            elevation = AppearanceStyle.elevation(this@AtomBlocksActivity, 8f)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        card.addView(label(getString(if (game.won) R.string.ab_win_title else R.string.ab_loss_title), 24f, bold = true))
        card.addSpaced(label(getString(if (game.won) R.string.ab_win_message else R.string.ab_loss_message), secondary = true), 6)
        card.addSpaced(label(getString(R.string.ab_result_score, game.score, game.lines), 16f, bold = true))
        if (game.won) {
            if (game.mode == AtomBlocksEngine.Mode.ADVENTURE && game.level < AtomBlocksEngine.ADVENTURE_LEVELS) {
                card.addSpaced(button(getString(R.string.ab_next), true) { openMode(AtomBlocksEngine.Mode.ADVENTURE, game.level + 1, 1000L + game.level) })
            }
            card.addSpaced(button(getString(R.string.ab_replay)) { restart() })
        } else {
            card.addSpaced(button(getString(R.string.ab_rescue), true) { rescue() })
            if (game.canUndo) card.addSpaced(button(getString(R.string.ab_undo)) { undoMove() })
            card.addSpaced(button(getString(R.string.ab_new_game)) { restart() })
        }
        card.addSpaced(button(getString(R.string.ab_menu)) { showMenu() })
        val scroll = ScrollView(this).apply { isFillViewport = false; addView(card) }
        val holder = FrameLayout(this).apply {
            isClickable = true
            addView(scroll, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        }
        boardFrame.addView(holder, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(10), dp(12), dp(10), dp(12)) })
        resultPanel = holder
        card.announceForAccessibility(getString(if (game.won) R.string.ab_win_title else R.string.ab_loss_title))
    }

    private fun showSettings() {
        val content = column().apply { setPadding(dp(24), dp(4), dp(24), dp(8)) }
        fun toggle(title: Int, key: String, default: Boolean, update: (Boolean) -> Unit) {
            content.addView(SwitchCompat(this).apply {
                setText(title); setTextColor(palette.text); minHeight = dp(52)
                isChecked = store.prefs.getBoolean(key, default)
                setOnCheckedChangeListener { _, checked -> store.prefs.edit().putBoolean(key, checked).apply(); update(checked) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        toggle(R.string.ab_sound, "sound", true) { audio.soundEnabled = it }
        toggle(R.string.ab_music, "music", true) { audio.musicEnabled = it }
        toggle(R.string.ab_effects, "effects", true) { if (::board.isInitialized) board.effectsEnabled = it }
        toggle(R.string.ab_haptics, "haptics", true) { }
        ImmersiveAlertDialogBuilder(this).setTitle(R.string.ab_settings).setView(content)
            .setPositiveButton(R.string.ab_close, null).show()
    }

    private fun save() { if (sessionLoaded) store.save(game) }
    override fun onResume() {
        super.onResume(); resumed = true
        if (playing) {
            audio.resume(); updateHud()
            if (game.won || game.blocked) showResult() else status.setText(R.string.ab_instruction)
        }
    }
    override fun onPause() {
        resumed = false; invalidateHint(); handler.removeCallbacksAndMessages(null)
        if (::board.isInitialized) board.clearSelection()
        save(); audio.pause(); super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("playing", playing); save(); super.onSaveInstanceState(outState) }
    override fun onDestroy() { hintWorker.shutdownNow(); handler.removeCallbacksAndMessages(null); audio.release(); super.onDestroy() }
}
