package com.Atom2Universe.app.games.match3

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
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
    private fun newGame() = if (isLandscape()) ForgeGame(cols = 10, rows = 8) else ForgeGame()
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
    private fun store() { forge?.let { prefs.edit().putString("run", it.stableSave()).apply() } }
    private fun leave() { store(); forge?.pause(); forge = null; sensor.disable() }
    private fun readGame(value: String): ForgeGame? = runCatching {
        val json = JSONObject(value)
        var cols = json.optInt("cols", 8); var rows = json.optInt("rows", 10)
        require((cols == 8 && rows == 10) || (cols == 10 && rows == 8))
        if ((cols > rows) != isLandscape()) {
            // Transpose rather than restart: every match, slag cell and core is preserved.
            val cells = json.getJSONArray("cells"); val slag = json.getJSONArray("slag")
            val newCells = JSONArray(); val newSlag = JSONArray()
            for (r in 0 until cols) for (c in 0 until rows) {
                newCells.put(cells.getInt(c * cols + r))
                newSlag.put(slag.getBoolean(c * cols + r))
            }
            val directions = intArrayOf(2, 3, 0, 1)
            json.put("gravity", directions[json.getInt("gravity")])
            json.put("deliveryEdge", directions[json.optInt("deliveryEdge", 0)])
            json.put("cells", newCells); json.put("slag", newSlag)
            val oldCols = cols; cols = rows; rows = oldCols
        }
        ForgeGame(json.getInt("seed"), cols, rows).apply { restore(json) }
    }.getOrNull()

    private fun showMenu() {
        leave(); screen = "menu"
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val box = root(); scroll.addView(box); setContentView(scroll)
        box.addFull(button(getString(R.string.forge_back)) { finish() })
        box.addFull(label(getString(R.string.forge_eyebrow), 12f, 0xffedb56b.toInt()))
        box.addFull(label(getString(R.string.match3_title), 34f).apply { setTypeface(typeface, Typeface.BOLD) })
        box.addFull(label(getString(R.string.forge_subtitle), 20f, 0xffedb56b.toInt()))
        box.addFull(object : View(this) {
            private val art = ForgeArt()
            private val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                val x = width / 2f; val y = height / 2f
                p.color = 0xff253b43.toInt(); c.drawCircle(x, y, height * 0.46f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(2).toFloat(); p.color = 0xffc18c51.toInt()
                c.drawCircle(x, y, height * 0.43f, p); c.drawCircle(x, y, height * 0.34f, p); p.style = Paint.Style.FILL
                val size = height * 0.46f
                art.piece(c, 5, x - size / 2, y - size / 2, size)
                art.piece(c, 2, x - size * 1.65f, y - size * 0.35f, size * 0.8f)
                art.piece(c, 4, x + size * 0.85f, y - size * 0.35f, size * 0.8f)
            }
        }, dp(160))
        box.addFull(label(getString(R.string.forge_pitch), 16f))
        if (prefs.getString("run", null)?.let { readGame(it) } != null) {
            box.addFull(button(getString(R.string.forge_continue), true) { showForge(readGame(prefs.getString("run", "")!!) ?: newGame()) })
        }
        box.addFull(button(getString(R.string.forge_start), true) { showForge(newGame()) })
        box.addFull(button(getString(R.string.forge_arcade)) { showArcade() })
        box.addFull(label(getString(R.string.forge_how), 14f, 0xffb9c9cc.toInt()))
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
        objective = label("", 18f).apply { setTypeface(typeface, Typeface.BOLD) }
        val objectiveRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        objectiveArt = object : View(this) {
            private val art = ForgeArt()
            override fun onDraw(canvas: Canvas) {
                art.piece(canvas, if (game.kind == 0) game.material else if (game.kind == 2) ForgeGame.CORE else 1, 0f, 0f, minOf(width, height).toFloat())
            }
        }
        objectiveRow.addView(objectiveArt, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
        objectiveRow.addView(objective, LinearLayout.LayoutParams(0, -2, 1f))
        information.addFull(objectiveRow)
        hint = label("", 13f, 0xffb9c9cc.toInt()); information.addFull(hint)
        val board = ForgeView(this, game); forge = board
        box.addView(board, if (wide) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1f))
        advance = button(getString(R.string.forge_next), true) {
            when {
                !game.won -> game.startStage(game.stage)
                game.stage < 5 -> game.startStage(game.stage + 1)
                else -> { prefs.edit().remove("run").apply(); forge?.pause(); forge = null; showMenu(); return@button }
            }
            board.refresh(); store()
        }
        information.addFull(advance)
        board.onChanged = { updateForge() }
        board.onInputFeedback = { hint.setText(it) }
        board.onFinished = { store(); updateForge(); objective.announceForAccessibility(objective.text) }
        board.onReshuffled = { Toast.makeText(this, R.string.forge_reshuffled, Toast.LENGTH_SHORT).show() }
        updateForge(); board.resume(); if (sensor.canDetectOrientation()) sensor.enable()
    }

    private fun updateForge() {
        val board = forge ?: return; val game = board.game
        objectiveArt.invalidate()
        status.text = getString(R.string.forge_status, game.stage + 1, game.moves)
        val materials = resources.getStringArray(R.array.forge_materials)
        objective.text = when {
            game.won -> getString(if (game.stage == 5) R.string.forge_complete else R.string.forge_success)
            game.moves <= 0 -> getString(R.string.forge_failed)
            game.kind == 0 -> getString(R.string.forge_alloy, materials[game.material], minOf(game.collected, game.target), game.target)
            game.kind == 1 -> getString(R.string.forge_slag, game.collected, game.target)
            else -> getString(R.string.forge_delivery)
        }
        hint.text = getString(when {
            board.busy -> R.string.forge_resolving
            game.won -> R.string.forge_success_hint
            game.moves <= 0 -> R.string.forge_retry_hint
            game.kind == 0 -> R.string.forge_alloy_hint
            game.kind == 1 -> R.string.forge_slag_hint
            else -> R.string.forge_delivery_hint
        })
        advance.visibility = if (!board.busy && (game.won || game.moves == 0)) View.VISIBLE else View.GONE
        advance.text = getString(if (!game.won) R.string.forge_retry else if (game.stage == 5) R.string.forge_finish else R.string.forge_next)
    }

    private fun showArcade() {
        leave(); screen = "arcade"
        setContentView(R.layout.activity_match3)
        val board = findViewById<Match3View>(R.id.match3_view)
        val score = findViewById<TextView>(R.id.match3_score)
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

    override fun onPause() { store(); forge?.pause(); sensor.disable(); super.onPause() }
    override fun onResume() { super.onResume(); forge?.resume(); if (forge != null && sensor.canDetectOrientation()) sensor.enable() }
    override fun onSaveInstanceState(outState: Bundle) {
        forge?.let { outState.putString("forge", it.stableSave()) }
        super.onSaveInstanceState(outState)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarsVisibility(showStatusBar = false, showNavBar = false)
    }
}
