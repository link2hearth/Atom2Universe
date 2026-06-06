package com.Atom2Universe.app.games.gameoflife

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class GameOfLifeActivity : ThemedActivity() {

    private lateinit var gameView: GameOfLifeView
    private lateinit var playPauseBtn: ImageButton
    private lateinit var generationText: TextView
    private lateinit var aliveText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var generation = 0
    private var intervalMs = 100L

    private val stepRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                gameView.step()
                generation++
                updateStats()
                handler.postDelayed(this, intervalMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()

        val root = buildUI()
        setContentView(root)

        gameView.onCellCountChanged = { alive ->
            aliveText.text = getString(R.string.gol_cells_alive, alive)
        }
        gameView.randomize()
        updateStats()
    }

    private fun buildUI(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF0D0D1A.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        gameView = GameOfLifeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(gameView)

        val topBar = buildTopBar()
        root.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.TOP })

        val bottomBar = buildBottomBar()
        root.addView(bottomBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM })

        return root
    }

    private fun buildTopBar(): LinearLayout {
        val dp = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(0xCC0D0D1A.toInt())
        }

        val backBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            setOnClickListener { finish() }
        }
        bar.addView(backBtn, LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()))

        val title = TextView(this).apply {
            setText(R.string.gol_title)
            textSize = 16f
            setTextColor(0xFFCCCCFF.toInt())
            android.graphics.Typeface.DEFAULT_BOLD.also { typeface = it }
            setPadding((12 * dp).toInt(), 0, 0, 0)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        generationText = TextView(this).apply {
            text = getString(R.string.gol_generation, 0)
            textSize = 11f
            setTextColor(0xFF8888AA.toInt())
            gravity = Gravity.END
        }
        bar.addView(generationText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        aliveText = TextView(this).apply {
            text = getString(R.string.gol_cells_alive, 0)
            textSize = 11f
            setTextColor(0xFF8888AA.toInt())
            gravity = Gravity.END
            setPadding((8 * dp).toInt(), 0, 0, 0)
        }
        bar.addView(aliveText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return bar
    }

    private fun buildBottomBar(): LinearLayout {
        val dp = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt())
            setBackgroundColor(0xCC0D0D1A.toInt())
        }

        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * dp).toInt() }
        }
        val speedLabel = TextView(this).apply {
            setText(R.string.gol_speed)
            textSize = 11f
            setTextColor(0xFF8888AA.toInt())
            setPadding(0, 0, (8 * dp).toInt(), 0)
        }
        speedRow.addView(speedLabel)
        val seekBar = SeekBar(this).apply {
            max = 9
            progress = 7
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    intervalMs = when (progress) {
                        0 -> 1000L; 1 -> 500L; 2 -> 300L; 3 -> 200L; 4 -> 150L
                        5 -> 100L; 6 -> 60L; 7 -> 30L; 8 -> 16L; else -> 8L
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        speedRow.addView(seekBar)
        bar.addView(speedRow)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        playPauseBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(0xFF2A2A4A.toInt())
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF7B8CDE.toInt())
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { togglePlay() }
        }
        btnRow.addView(playPauseBtn, LinearLayout.LayoutParams((52 * dp).toInt(), (44 * dp).toInt()).also {
            it.marginEnd = (8 * dp).toInt()
        })

        fun actionBtn(labelRes: Int, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                setText(labelRes)
                textSize = 13f
                setTextColor(0xFFCCCCFF.toInt())
                gravity = Gravity.CENTER
                setBackgroundColor(0xFF2A2A4A.toInt())
                setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
                setOnClickListener { onClick() }
            }
        }

        val clearBtn = actionBtn(R.string.gol_clear) {
            stopSim()
            generation = 0
            gameView.clear()
            updateStats()
        }
        btnRow.addView(clearBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.marginEnd = (8 * dp).toInt() })

        val randomBtn = actionBtn(R.string.gol_random) {
            stopSim()
            generation = 0
            gameView.randomize()
            updateStats()
        }
        btnRow.addView(randomBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.marginEnd = (8 * dp).toInt() })

        val presetsBtn = actionBtn(R.string.gol_preset_label) { showPresetsDialog() }
        btnRow.addView(presetsBtn)

        bar.addView(btnRow)

        val hint = TextView(this).apply {
            setText(R.string.gol_zoom_hint)
            textSize = 9f
            setTextColor(0xFF444466.toInt())
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (6 * dp).toInt()
            layoutParams = lp
        }
        bar.addView(hint)

        return bar
    }

    private fun togglePlay() {
        if (isRunning) stopSim() else startSim()
    }

    private fun startSim() {
        isRunning = true
        playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
        handler.post(stepRunnable)
    }

    private fun stopSim() {
        isRunning = false
        playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
        handler.removeCallbacks(stepRunnable)
    }

    private fun updateStats() {
        generationText.text = getString(R.string.gol_generation, generation)
    }

    private fun showPresetsDialog() {
        val wasRunning = isRunning
        stopSim()

        val presets = listOf(
            getString(R.string.gol_preset_glider) to GLIDER,
            getString(R.string.gol_preset_pulsar) to PULSAR,
            getString(R.string.gol_preset_gosper) to GOSPER_GUN,
            getString(R.string.gol_preset_lwss) to LWSS
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.gol_preset_label)
            .setItems(presets.map { it.first }.toTypedArray()) { _, i ->
                generation = 0
                gameView.clear()
                gameView.placePattern(presets[i].second)
                updateStats()
                if (wasRunning) startSim()
            }
            .setOnCancelListener { if (wasRunning) startSim() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(stepRunnable)
    }

    companion object {
        val GLIDER = arrayOf(
            intArrayOf(0, 1, 0),
            intArrayOf(0, 0, 1),
            intArrayOf(1, 1, 1)
        )

        val PULSAR = arrayOf(
            intArrayOf(0,0,1,1,1,0,0,0,1,1,1,0,0),
            intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0),
            intArrayOf(1,0,0,0,0,1,0,1,0,0,0,0,1),
            intArrayOf(1,0,0,0,0,1,0,1,0,0,0,0,1),
            intArrayOf(1,0,0,0,0,1,0,1,0,0,0,0,1),
            intArrayOf(0,0,1,1,1,0,0,0,1,1,1,0,0),
            intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0),
            intArrayOf(0,0,1,1,1,0,0,0,1,1,1,0,0),
            intArrayOf(1,0,0,0,0,1,0,1,0,0,0,0,1),
            intArrayOf(1,0,0,0,0,1,0,1,0,0,0,0,1),
            intArrayOf(1,0,0,0,0,1,0,1,0,0,0,0,1),
            intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0),
            intArrayOf(0,0,1,1,1,0,0,0,1,1,1,0,0)
        )

        val GOSPER_GUN = arrayOf(
            intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,1,1),
            intArrayOf(0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,1,1),
            intArrayOf(1,1,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
            intArrayOf(1,1,0,0,0,0,0,0,0,0,1,0,0,0,1,0,1,1,0,0,0,0,1,0,1,0,0,0,0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0),
            intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
        )

        val LWSS = arrayOf(
            intArrayOf(0,1,0,0,1),
            intArrayOf(1,0,0,0,0),
            intArrayOf(1,0,0,0,1),
            intArrayOf(1,1,1,1,0)
        )
    }
}
