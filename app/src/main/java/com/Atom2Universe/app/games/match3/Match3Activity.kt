package com.Atom2Universe.app.games.match3

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.applySystemBarsVisibility
import com.Atom2Universe.app.util.enableImmersiveMode
import org.json.JSONObject
import org.json.JSONArray

class Match3Activity : ThemedActivity() {
    private val prefs by lazy { getSharedPreferences("match3_forge", MODE_PRIVATE) }
    private var forge: ForgeView? = null
    private var screen = "menu"
    private lateinit var status: TextView
    private lateinit var objective: TextView
    private lateinit var objectiveArt: View
    private lateinit var hint: TextView
    private lateinit var advance: Button
    private lateinit var metrics: TextView
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            if (forge == null) return
            updateMetrics()
            timerHandler.postDelayed(this, 1000L)
        }
    }
    private val sensor by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // Gravity is relative to the current fullscreen window, in either
                // portrait or landscape. The sensor never rotates the interface.
                @Suppress("DEPRECATION")
                val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.rotation else windowManager.defaultDisplay.rotation
                val angle = (orientation + when (rotation) {
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> 0
                }) % 360
                val direction = when {
                    angle < 45 || angle >= 315 -> 0
                    angle < 135 -> 2
                    angle < 225 -> 3
                    else -> 1
                }
                forge?.setGravity(direction)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        applySystemBarsVisibility(showStatusBar = false, showNavBar = false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (screen == "menu") finish() else showMenu() }
        })
        val saved = savedInstanceState?.getString("forge")
        if (saved != null) showForge(readGame(saved) ?: newGame()) else showMenu()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun newGame(endless: Boolean = false, rounds: Int = 6, slagEndless: Boolean = false) = ForgeGame(cols = if (isLandscape()) 10 else 8,
        rows = if (isLandscape()) 8 else 10, endless = endless || slagEndless, landscapeLayout = isLandscape(), rounds = rounds, slagEndless = slagEndless)
    private fun expeditionBest(rounds: Int) = prefs.getLong("best_expedition_$rounds",
        if (rounds == 6) prefs.getLong("best_expedition", 0L) else 0L)
    private fun panel(color: Int = 0xff192b33.toInt()) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(16).toFloat(); setStroke(dp(1), 0xff405057.toInt())
    }
    private fun label(text: String, size: Float, color: Int = 0xffe7eff0.toInt()) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); setPadding(0, dp(5), 0, dp(5))
    }
    private fun button(text: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 15f
        setTextColor(if (primary) 0xff1e2527.toInt() else 0xffecf2f2.toInt())
        background = panel(if (primary) 0xffedb56b.toInt() else 0xff243840.toInt())
        minHeight = dp(48); setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { action() }
    }
    private fun LinearLayout.addFull(view: View, height: Int = -2) {
        addView(view, LinearLayout.LayoutParams(-1, height).apply { topMargin = dp(8) })
    }
    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xff10212a.toInt(), 0xff10191d.toInt(), 0xff352820.toInt()))
    }
    private fun store() { forge?.let { prefs.edit().putString(when {
        it.game.slagEndless -> "slag_run"; it.game.endless -> "endless_run"; else -> "run"
    }, it.stableSave()).apply() } }
    private fun leave() { timerHandler.removeCallbacks(timerTick); store(); forge?.pause(); forge = null; sensor.disable() }
    private fun readGame(value: String): ForgeGame? = runCatching {
        val json = JSONObject(value)
        var cols = json.optInt("cols", 8); var rows = json.optInt("rows", 10)
        require(cols in 6..11 && rows in 6..11)
        if (cols != rows && (cols > rows) != isLandscape()) {
            // Transpose rather than restart: every match, slag cell and core is preserved.
            val cells = json.getJSONArray("cells"); val slag = json.getJSONArray("slag")
            val newCells = JSONArray(); val newSlag = JSONArray()
            val edges = json.optJSONArray("coreEdges")
            val newEdges = JSONArray()
            val directions = intArrayOf(2, 3, 0, 1)
            for (r in 0 until cols) for (c in 0 until rows) {
                newCells.put(cells.getInt(c * cols + r))
                newSlag.put(slag.getBoolean(c * cols + r))
                val edge = edges?.optInt(c * cols + r, -1) ?: -1
                newEdges.put(if (edge in 0..3) directions[edge] else -1)
            }
            if (edges != null) json.put("coreEdges", newEdges)
            for (key in listOf("slagLayers", "slagRules")) {
                val old = json.optJSONArray(key) ?: continue
                val transposed = JSONArray()
                for (r in 0 until cols) for (c in 0 until rows) transposed.put(old.optInt(c * cols + r, 0))
                json.put(key, transposed)
            }
            json.put("gravity", directions[json.getInt("gravity")])
            json.put("deliveryEdge", directions[json.optInt("deliveryEdge", 0)])
            json.put("cells", newCells); json.put("slag", newSlag)
            val oldCols = cols; cols = rows; rows = oldCols
        }
        json.put("cols", cols); json.put("rows", rows)
        ForgeGame(json.getInt("seed"), cols, rows, json.optBoolean("endless", false), isLandscape(),
            slagEndless = json.optBoolean("slagEndless", false)).apply { restore(json) }
    }.getOrNull()

    private fun showMenu() {
        leave(); screen = "menu"
        val outer = root(); setContentView(outer)
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(button(getString(R.string.forge_back)) { finish() })
        head.addView(label(getString(R.string.match3_title), 26f).apply {
            setTypeface(typeface, Typeface.BOLD); gravity = Gravity.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        outer.addFull(head)
        val wide = isLandscape()
        val body = LinearLayout(this).apply { orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        outer.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        if (wide) {
            val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(18), 0) }
            hero.addFull(label(getString(R.string.forge_subtitle), 22f, 0xffedb56b.toInt()))
            hero.addView(object : View(this) {
                private val artwork = Match3HubTileDrawable(this@Match3Activity)
                override fun onDraw(canvas: Canvas) { artwork.setBounds(0, 0, width, height); artwork.draw(canvas) }
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            hero.addFull(label(getString(R.string.forge_menu_tagline), 15f, 0xffb9c9cc.toInt()))
            body.addView(hero, LinearLayout.LayoutParams(0, -1, .85f))
        }
        val scroll = ScrollView(this)
        val choices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(choices)
        body.addView(scroll, if (wide) LinearLayout.LayoutParams(0, -1, 1.15f) else LinearLayout.LayoutParams(-1, -1))
        if (!wide) choices.addFull(label(getString(R.string.forge_menu_tagline), 15f, 0xffb9c9cc.toInt()))
        fun modeCard(endless: Boolean, slagMode: Boolean = false) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = panel()
                setPadding(dp(16), dp(12), dp(16), dp(14))
            }
            card.addView(label(getString(if (slagMode) R.string.forge_slag_endless_title else if (endless) R.string.forge_endless_title else R.string.forge_expedition_title), 23f,
                if (endless) 0xff78e0d8.toInt() else 0xffedb56b.toInt()).apply { setTypeface(typeface, Typeface.BOLD) })
            card.addView(label(getString(if (slagMode) R.string.forge_slag_endless_desc else if (endless) R.string.forge_endless_desc else R.string.forge_expedition_desc), 14f, 0xffbdced1.toInt()))
            val key = if (slagMode) "slag_run" else if (endless) "endless_run" else "run"
            val saved = prefs.getString(key, null)?.let { readGame(it) }
            val resumable = saved != null && !saved.expeditionComplete
            if (saved != null && resumable) {
                val resumeText = if (endless) getString(R.string.forge_resume_round, saved.stage + 1)
                    else getString(R.string.forge_resume_expedition, saved.stage + 1, saved.roundLimit)
                card.addFull(button(resumeText, true) { showForge(saved) })
            }
            if (endless) {
                card.addFull(button(getString(R.string.forge_new_run), !resumable) { showForge(newGame(true, slagEndless = slagMode)) })
                val best = prefs.getLong(if (slagMode) "best_slag" else "best_endless", 0L)
                if (best > 0) card.addView(label(getString(R.string.forge_best_score, best), 13f, 0xffbdced1.toInt()))
            } else {
                val formats = LinearLayout(this)
                for (rounds in listOf(3, 6, 12)) {
                    val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    column.addFull(button(getString(R.string.forge_rounds_button, rounds), !resumable) {
                        showForge(newGame(rounds = rounds))
                    }.apply { textSize = 14f; minimumWidth = 0; setPadding(dp(4), dp(8), dp(4), dp(8)) })
                    column.addFull(label(getString(R.string.forge_best_score, expeditionBest(rounds)), 12f, 0xffbdced1.toInt()).apply {
                        gravity = Gravity.CENTER
                    })
                    formats.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply { if (rounds != 12) marginEnd = dp(6) })
                }
                card.addFull(formats)
            }
            choices.addFull(card)
        }
        modeCard(false); modeCard(true); modeCard(true, true)
        val overheat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = panel()
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }
        overheat.addView(label(getString(R.string.forge_arcade_short), 23f, 0xffff9565.toInt()).apply {
            setTypeface(typeface, Typeface.BOLD)
        })
        overheat.addView(label(getString(R.string.forge_overheat_desc), 14f, 0xffbdced1.toInt()))
        overheat.addFull(button(getString(R.string.forge_overheat_start), true) { showArcade() })
        val arcadeBest = getSharedPreferences("match3_save", MODE_PRIVATE).getInt("best_score", 0)
        if (arcadeBest > 0) overheat.addView(label(getString(R.string.forge_best_score, arcadeBest.toLong()), 13f, 0xffbdced1.toInt()))
        choices.addFull(overheat)
        val links = LinearLayout(this)
        links.addView(button(getString(R.string.forge_rules)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.forge_rules)
                .setMessage(getString(R.string.forge_rules_body, getString(R.string.forge_how), getString(R.string.forge_bonuses_help)))
                .setPositiveButton(android.R.string.ok, null).show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        choices.addFull(links)
    }

    private fun showForge(game: ForgeGame) {
        leave(); screen = "forge"
        val box = root(); setContentView(box)
        val wide = isLandscape()
        val information = if (wide) LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } else box
        if (wide) {
            box.orientation = LinearLayout.HORIZONTAL
            val scroll = ScrollView(this).apply { addView(information) }
            box.addView(scroll, LinearLayout.LayoutParams(dp(220), -1))
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(button(getString(R.string.forge_menu)) { showMenu() })
        status = label("", 14f, 0xffedb56b.toInt()).apply { gravity = Gravity.END }
        if (wide) {
            information.addFull(head)
            information.addFull(status.apply { gravity = Gravity.START })
        } else {
            head.addView(status, LinearLayout.LayoutParams(0, -2, 1f)); information.addFull(head)
        }
        metrics = label("", 14f, 0xff78e0d8.toInt())
        val gravityIndicator = GravityIndicatorView(this)
        val metricsRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        metricsRow.addView(metrics, LinearLayout.LayoutParams(0, -2, 1f))
        metricsRow.addView(gravityIndicator, LinearLayout.LayoutParams(dp(36), dp(32)))
        information.addFull(metricsRow)
        objective = label("", 18f).apply { setTypeface(typeface, Typeface.BOLD) }
        val objectiveRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        objectiveArt = object : View(this) {
            private val art = ForgeArt()
            override fun onDraw(canvas: Canvas) {
                if (game.dualRecipe) {
                    art.piece(canvas, game.material, 0f, 0f, width * .65f)
                    art.piece(canvas, game.secondaryMaterial, width * .35f, height * .35f, width * .65f)
                } else art.piece(canvas, if (game.kind == 0) game.material else if (game.kind == 2) ForgeGame.CORE else 1,
                    0f, 0f, minOf(width, height).toFloat(), deliveryEdge = game.activeDeliveryEdges.singleOrNull() ?: -1)
            }
        }
        objectiveRow.addView(objectiveArt, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
        objectiveRow.addView(objective, LinearLayout.LayoutParams(0, -2, 1f))
        information.addFull(objectiveRow)
        hint = label("", 13f, 0xffb9c9cc.toInt()).apply {
            visibility = if (game.slagEndless) View.GONE else View.VISIBLE
        }; information.addFull(hint)
        val board = ForgeView(this, game); forge = board
        gravityIndicator.direction = board.gravityDirection
        board.onGravityChanged = { gravityIndicator.direction = it }
        box.addView(board, if (wide) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1f))
        advance = button(getString(R.string.forge_next), true) {
            when {
                !game.won -> game.startStage(game.stage)
                game.endless || game.stage < game.roundLimit - 1 -> game.startStage(game.stage + 1)
                else -> { prefs.edit().remove("run").apply(); forge?.pause(); forge = null; showMenu(); return@button }
            }
            board.refresh(); store()
        }
        information.addFull(advance)
        board.onChanged = { updateForge() }
        board.onInputFeedback = {
            if (!game.slagEndless) hint.setText(it)
        }
        board.onFinished = {
            val key = if (game.slagEndless) "best_slag" else if (game.endless) "best_endless" else "best_expedition_${game.roundLimit}"
            val best = if (game.endless) prefs.getLong(key, 0L) else expeditionBest(game.roundLimit)
            if ((game.endless || game.expeditionComplete || game.moves == 0) && game.score > best) prefs.edit().putLong(key, game.score).apply()
            store(); updateForge(); objective.announceForAccessibility(objective.text)
        }
        board.onReshuffled = { Toast.makeText(this, R.string.forge_reshuffled, Toast.LENGTH_SHORT).show() }
        updateForge(); board.resume(); if (sensor.canDetectOrientation()) sensor.enable()
        timerHandler.removeCallbacks(timerTick); timerHandler.post(timerTick)
    }

    private fun updateMetrics() {
        val board = forge ?: return
        val seconds = board.elapsedTimeMs() / 1000L
        val time = getString(R.string.forge_clock_format, seconds / 60L, seconds % 60L)
        metrics.text = getString(R.string.forge_metrics, board.game.score, time)
    }

    private fun updateForge() {
        val board = forge ?: return; val game = board.game
        objectiveArt.invalidate()
        status.text = if (game.endless) getString(R.string.forge_endless_status, game.stage + 1, game.moves, game.cols, game.rows)
            else getString(R.string.forge_round_status, game.stage + 1, game.roundLimit, game.moves)
        updateMetrics()
        val materials = resources.getStringArray(R.array.forge_materials)
        objective.text = when {
            game.won -> getString(if (game.expeditionComplete) R.string.forge_complete else R.string.forge_success)
            game.moves <= 0 -> getString(R.string.forge_failed)
            game.dualRecipe -> getString(R.string.forge_dual_alloy, materials[game.material], minOf(game.collected, game.target), game.target,
                materials[game.secondaryMaterial], minOf(game.secondaryCollected, game.target))
            game.kind == 0 -> getString(R.string.forge_alloy, materials[game.material], minOf(game.collected, game.target), game.target)
            game.kind == 1 -> getString(R.string.forge_slag, game.collected, game.target)
            else -> getString(R.string.forge_delivery_count, game.collected, game.target)
        }
        hint.text = getString(when {
            board.busy -> R.string.forge_resolving
            game.won -> R.string.forge_success_hint
            game.moves <= 0 -> R.string.forge_retry_hint
            game.dualRecipe -> R.string.forge_dual_hint
            game.kind == 0 -> R.string.forge_alloy_hint
            game.kind == 1 -> R.string.forge_slag_hint
            else -> R.string.forge_delivery_hint
        })
        advance.visibility = if (!board.busy && (game.won || game.moves == 0)) View.VISIBLE else View.GONE
        advance.text = getString(if (!game.won) R.string.forge_retry else if (game.expeditionComplete) R.string.forge_finish else R.string.forge_next)
    }

    private fun showArcade() {
        leave(); screen = "arcade"
        setContentView(R.layout.activity_match3)
        val board = findViewById<Match3View>(R.id.match3_view)
        val score = findViewById<TextView>(R.id.match3_score)
        val gravityIndicator = findViewById<GravityIndicatorView>(R.id.match3_gravity)
        gravityIndicator.direction = board.gravityDirection
        board.onGravityChanged = { gravityIndicator.direction = it }
        val timer = findViewById<ProgressBar>(R.id.match3_timer)
        score.text = getString(R.string.match3_score, 0)
        board.onScoreChanged = { score.text = getString(R.string.match3_score, it) }
        board.onTimerChanged = { value, max ->
            timer.progress = ((value / max).coerceIn(0f, 1f) * 1000).toInt()
            timer.progressTintList = ColorStateList.valueOf(0xffedb56b.toInt())
        }
        findViewById<View>(R.id.match3_btn_back).setOnClickListener { showMenu() }
        findViewById<View>(R.id.match3_btn_new).setOnClickListener { board.resetGame() }
    }

    override fun onPause() { timerHandler.removeCallbacks(timerTick); store(); forge?.pause(); sensor.disable(); super.onPause() }
    override fun onResume() {
        super.onResume(); forge?.resume(); if (forge != null && sensor.canDetectOrientation()) sensor.enable()
        timerHandler.removeCallbacks(timerTick); if (forge != null) timerHandler.post(timerTick)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        forge?.let { outState.putString("forge", it.stableSave()) }
        super.onSaveInstanceState(outState)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarsVisibility(showStatusBar = false, showNavBar = false)
    }
}
