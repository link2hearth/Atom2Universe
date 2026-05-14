package com.Atom2Universe.app.games.reflex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Jeu Réflexes : tapez les cercles le plus vite possible avant qu'ils débordent.
 * Logique portée depuis scripts/arcade/reflex.js
 */
class ReflexActivity : AppCompatActivity() {

    private data class ModeConfig(
        val maxActiveTargets: Int,
        val initialIntervalMs: Long,
        val intervalDecreaseMs: Long,
        val earlySpawnCount: Int,
        val minIntervalMs: Long
    )

    private val TARGET_SIZE_DP = 68f
    private val EASY_HIT_SCALE = 2f

    private val EASY_CONFIG = ModeConfig(
        maxActiveTargets = 6,
        initialIntervalMs = 1150,
        intervalDecreaseMs = 8,
        earlySpawnCount = 6,
        minIntervalMs = 260
    )
    private val HARD_CONFIG = ModeConfig(
        maxActiveTargets = 5,
        initialIntervalMs = 1000,
        intervalDecreaseMs = 10,
        earlySpawnCount = 5,
        minIntervalMs = 220
    )

    private lateinit var playfield: FrameLayout
    private lateinit var scoreText: TextView
    private lateinit var bestEasyText: TextView
    private lateinit var bestHardText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var easyButton: Button
    private lateinit var hardButton: Button

    private var score = 0
    private var bestEasy = 0
    private var bestHard = 0
    private var isRunning = false
    private var spawnCount = 0
    private var isHardMode = false

    private val handler = Handler(Looper.getMainLooper())
    private var spawnRunnable: Runnable? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_reflex)

        initViews()
        loadBestScores()
        updateDisplays()
    }

    private fun initViews() {
        playfield = findViewById(R.id.reflex_playfield)
        scoreText = findViewById(R.id.reflex_score)
        bestEasyText = findViewById(R.id.reflex_best_easy)
        bestHardText = findViewById(R.id.reflex_best_hard)
        statusText = findViewById(R.id.reflex_status)
        startButton = findViewById(R.id.reflex_start_button)
        easyButton = findViewById(R.id.reflex_mode_easy)
        hardButton = findViewById(R.id.reflex_mode_hard)

        startButton.setOnClickListener { startGame() }
        easyButton.setOnClickListener { setMode(false) }
        hardButton.setOnClickListener { setMode(true) }
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        setMode(false)
    }

    private fun setMode(hard: Boolean) {
        isHardMode = hard
        easyButton.alpha = if (!hard) 1f else 0.5f
        hardButton.alpha = if (hard) 1f else 0.5f
    }

    private fun getModeConfig() = if (isHardMode) HARD_CONFIG else EASY_CONFIG

    private fun computeNextInterval(): Long {
        val config = getModeConfig()
        if (spawnCount < config.earlySpawnCount) {
            return config.initialIntervalMs
        }
        val extra = (spawnCount - config.earlySpawnCount + 1).toLong()
        val reduced = config.initialIntervalMs - config.intervalDecreaseMs * extra
        return max(config.minIntervalMs, reduced)
    }

    private fun startGame() {
        stopSpawning()
        score = 0
        spawnCount = 0
        isRunning = true
        clearTargets()
        updateDisplays()
        statusText.text = getString(R.string.reflex_status_go)
        scheduleNextSpawn()
    }

    private fun stopSpawning() {
        isRunning = false
        spawnRunnable?.let { handler.removeCallbacks(it) }
        spawnRunnable = null
    }

    private fun clearTargets() {
        playfield.removeAllViews()
    }

    private fun gameOver() {
        stopSpawning()
        clearTargets()

        val currentBest = if (isHardMode) bestHard else bestEasy
        if (score > currentBest) {
            if (isHardMode) bestHard = score else bestEasy = score
            saveBestScores()
            statusText.text = getString(R.string.reflex_status_new_record, score)
        } else {
            statusText.text = getString(R.string.reflex_status_game_over, score)
        }
        updateDisplays()
    }

    private fun scheduleNextSpawn() {
        if (!isRunning) return
        val delay = computeNextInterval()
        val r = Runnable { spawnTarget() }
        spawnRunnable = r
        handler.postDelayed(r, delay)
    }

    private fun spawnTarget() {
        if (!isRunning) return

        spawnCount++
        val config = getModeConfig()
        val density = resources.displayMetrics.density
        val targetSizePx = (TARGET_SIZE_DP * density).toInt()
        val hitScale = if (!isHardMode) EASY_HIT_SCALE else 1f
        val hitSizePx = (targetSizePx * hitScale).toInt()

        val areaWidth = playfield.width
        val areaHeight = playfield.height
        if (areaWidth <= 0 || areaHeight <= 0) {
            scheduleNextSpawn()
            return
        }

        val maxX = max(0, areaWidth - hitSizePx)
        val maxY = max(0, areaHeight - hitSizePx)
        val x = (Math.random() * maxX).toFloat()
        val y = (Math.random() * maxY).toFloat()

        val circle = createCircleView(targetSizePx, hitSizePx)
        val createdAt = System.currentTimeMillis()

        circle.x = x
        circle.y = y
        circle.tag = "active"

        circle.setOnClickListener {
            if (!isRunning || circle.tag != "active") return@setOnClickListener
            circle.tag = "resolved"
            val delayMs = System.currentTimeMillis() - createdAt
            val points = max(0, (1000 - delayMs * 0.5).roundToInt())
            score += points
            playfield.removeView(circle)
            updateDisplays()
            statusText.text = getString(R.string.reflex_status_hit, points, score)
        }

        playfield.addView(circle, FrameLayout.LayoutParams(hitSizePx, hitSizePx))

        if (playfield.childCount > config.maxActiveTargets) {
            gameOver()
            return
        }

        scheduleNextSpawn()
    }

    private fun createCircleView(targetSizePx: Int, hitSizePx: Int): View {
        val bitmap = Bitmap.createBitmap(hitSizePx, hitSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = hitSizePx / 2f
        val cy = hitSizePx / 2f
        val radius = targetSizePx / 2f

        // Halo extérieur semi-transparent
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55FF4444")
        }
        canvas.drawCircle(cx, cy, radius * 1.35f, haloPaint)

        // Cercle principal rouge
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E53935")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, circlePaint)

        // Contour
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7043")
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.12f
        }
        canvas.drawCircle(cx, cy, radius - strokePaint.strokeWidth / 2f, strokePaint)

        // Reflet lumineux
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88FFFFFF")
        }
        canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.28f, radius * 0.32f, highlightPaint)

        val view = View(this)
        @Suppress("DEPRECATION")
        view.background = BitmapDrawable(resources, bitmap)
        return view
    }

    private fun updateDisplays() {
        scoreText.text = score.toString()
        bestEasyText.text = if (bestEasy > 0) bestEasy.toString() else "—"
        bestHardText.text = if (bestHard > 0) bestHard.toString() else "—"
    }

    private fun loadBestScores() {
        val prefs = getSharedPreferences("reflex_save", MODE_PRIVATE)
        bestEasy = prefs.getInt("best_easy", 0)
        bestHard = prefs.getInt("best_hard", 0)
    }

    private fun saveBestScores() {
        getSharedPreferences("reflex_save", MODE_PRIVATE).edit()
            .putInt("best_easy", bestEasy)
            .putInt("best_hard", bestHard)
            .apply()
    }

    override fun onPause() {
        super.onPause()
        if (isRunning) {
            stopSpawning()
            clearTargets()
            statusText.text = getString(R.string.reflex_status_ready)
            updateDisplays()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
