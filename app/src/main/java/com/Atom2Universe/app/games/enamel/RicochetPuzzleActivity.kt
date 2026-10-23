package com.Atom2Universe.app.games.enamel

import android.graphics.Typeface
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.science.SciencePalette
import com.Atom2Universe.app.games.bigger.BiggerSoundEngine
import com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import kotlin.random.Random

class RicochetPuzzleActivity : ThemedActivity() {
    private val store by lazy { RicochetPuzzleStore(this) }
    private val colors by lazy { SciencePalette(this) }
    private val sound = BiggerSoundEngine()
    private lateinit var board: RicochetPuzzleView
    private lateinit var hud: TextView
    private lateinit var seedButton: Button
    private var dialog: AlertDialog? = null
    private var reported = false
    private var readingRules = false
    private val game get() = board.game
    private fun px(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val model = RicochetPuzzleGame(Random.nextInt(RicochetPuzzleRules.MAX_SEED + 1))
        store.restore(model)
        reported = model.complete
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(colors.background)
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = px(56) }
        fun icon(image: Int, label: Int, action: () -> Unit) = ImageButton(this).apply {
            setImageResource(image); setColorFilter(colors.text); background = null
            setPadding(px(12), px(12), px(12), px(12)); contentDescription = getString(label)
            setOnClickListener { action() }
        }
        bar.addView(icon(R.drawable.ic_arrow_back, R.string.back) { finish() }, LinearLayout.LayoutParams(px(48), px(48)))
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title.addView(TextView(this).apply {
            setText(R.string.puzzle_title); textSize = 22f; setTextColor(colors.text); typeface = Typeface.DEFAULT_BOLD
        })
        hud = TextView(this).apply { textSize = 14f; setTextColor(colors.ink(colors.accent, colors.background)) }
        title.addView(hud)
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(icon(R.drawable.ic_info, R.string.kit_rules) { rules() }, LinearLayout.LayoutParams(px(48), px(48)))
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        seedButton = action(R.string.puzzle_choose) { choose() }
        root.addView(seedButton, LinearLayout.LayoutParams(-1, -2))
        board = RicochetPuzzleView(this, model).apply {
            sound = this@RicochetPuzzleActivity.sound
            onChanged = { changed() }
        }
        root.addView(board, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this)
        actions.addView(action(R.string.puzzle_replay) { replace { start(game.seed, game.hard) } }, LinearLayout.LayoutParams(0, -1, 1f))
        actions.addView(action(R.string.puzzle_new) { replace { start(Random.nextInt(RicochetPuzzleRules.MAX_SEED + 1), game.hard) } }, LinearLayout.LayoutParams(0, -1, 1f))
        actions.addView(action(R.string.puzzle_history) { history() }, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(actions)
        setContentView(root)
        store.save(game); updateHud()
    }

    private fun action(label: Int, click: () -> Unit) = Button(this).apply {
        setText(label); isAllCaps = false; textSize = 14f
        backgroundTintList = ColorStateList.valueOf(colors.accent)
        minimumWidth = 0; minimumHeight = px(48)
        setTextColor(colors.onAccent); setOnClickListener { click() }
    }

    private fun mode(hard: Boolean) = getString(if (hard) R.string.puzzle_hard else R.string.puzzle_easy)
    private fun bestText(seed: Int = game.seed, hard: Boolean = game.hard): String {
        val best = store.best(seed, hard)
        return if (best == 0) getString(R.string.puzzle_no_record) else getString(R.string.puzzle_record, best)
    }

    private fun updateHud() {
        hud.text = getString(if (game.complete) R.string.puzzle_finished_hud else R.string.puzzle_hud,
            game.shots, game.pairsLeft, bestText())
        seedButton.text = getString(R.string.puzzle_seed_mode, game.seed, mode(game.hard))
    }

    private fun changed() {
        val record = store.save(game)
        updateHud()
        if (game.complete && !reported) {
            reported = true
            show(ImmersiveAlertDialogBuilder(this).setTitle(if (record) R.string.puzzle_new_record else R.string.puzzle_complete)
                .setMessage(getString(R.string.puzzle_result, game.shots, game.seed, mode(game.hard), bestText()))
                .setPositiveButton(R.string.puzzle_replay) { _, _ -> start(game.seed, game.hard) }
                .setNegativeButton(R.string.puzzle_new) { _, _ -> start(Random.nextInt(RicochetPuzzleRules.MAX_SEED + 1), game.hard) }
                .setNeutralButton(android.R.string.ok, null).create())
        }
    }

    private fun start(seed: Int, hard: Boolean) {
        game.reset(seed, hard); reported = false; board.refresh()
    }

    private fun replace(action: () -> Unit) {
        if (game.shots == 0 || game.complete) { action(); return }
        show(ImmersiveAlertDialogBuilder(this).setTitle(R.string.puzzle_replace_title)
            .setMessage(R.string.puzzle_replace_hint).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.puzzle_play) { _, _ -> action() }.create())
    }

    private fun show(next: AlertDialog) {
        board.pause(); dialog = next
        next.setOnDismissListener {
            if (dialog === next) {
                dialog = null
                if (!readingRules && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) board.resume()
            }
        }
        next.show()
    }

    private fun choose() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(px(24), px(8), px(24), px(4))
        }
        form.addView(TextView(this).apply { setText(R.string.puzzle_seed_hint) })
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(); setText(game.seed.toString())
            contentDescription = getString(R.string.puzzle_seed_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(9))
        }
        form.addView(input)
        val modes = RadioGroup(this)
        val easy = RadioButton(this).apply { id = android.view.View.generateViewId(); setText(R.string.puzzle_easy_description) }
        val hard = RadioButton(this).apply { id = android.view.View.generateViewId(); setText(R.string.puzzle_hard_description) }
        modes.addView(easy); modes.addView(hard); modes.check(if (game.hard) hard.id else easy.id)
        form.addView(modes)
        form.addView(TextView(this).apply { setText(R.string.puzzle_choose_hint); textSize = 16f })
        val next = ImmersiveAlertDialogBuilder(this).setTitle(R.string.puzzle_choose).setView(form)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.puzzle_play, null).create()
        show(next)
        next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val seed = input.text.toString().trim().toIntOrNull()
            if (seed == null || seed !in 0..RicochetPuzzleRules.MAX_SEED) input.error = getString(R.string.puzzle_seed_error)
            else {
                val isHard = modes.checkedRadioButtonId == hard.id
                next.dismiss()
                replace { start(seed, isHard) }
            }
        }
    }

    private fun history() {
        val recent = store.recent()
        val labels = recent.map { (seed, hard) -> getString(R.string.puzzle_recent_item, seed, mode(hard), bestText(seed, hard)) }.toTypedArray()
        show(ImmersiveAlertDialogBuilder(this).setTitle(R.string.puzzle_history)
            .setItems(labels) { _, index ->
                val (seed, hard) = recent[index]
                replace { start(seed, hard) }
            }.setNegativeButton(android.R.string.cancel, null).create())
    }

    private fun rules() {
        readingRules = true
        board.pause()
        showGameRules(R.string.puzzle_title, R.string.puzzle_rules) {
            readingRules = false
            if (dialog == null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) board.resume()
        }
    }

    override fun onResume() {
        super.onResume(); sound.start()
        if (dialog == null && !readingRules) board.resume()
    }

    override fun onPause() {
        board.pause(); store.save(game); sound.stop()
        super.onPause()
    }
}
