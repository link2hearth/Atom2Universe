package com.Atom2Universe.app.science.pendulum

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ToggleButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class DoublePendulumActivity : ThemedActivity() {

    // Corps célestes : (nom ressource string, gravité m/s²)
    // L'index 3 = Terre est la valeur par défaut
    private val gravityPresets by lazy {
        listOf(
            getString(R.string.pendulum_gravity_pluto)    to 0.62,
            getString(R.string.pendulum_gravity_moon)     to 1.62,
            getString(R.string.pendulum_gravity_mars)     to 3.72,
            getString(R.string.pendulum_gravity_earth)    to 9.81,
            getString(R.string.pendulum_gravity_saturn)   to 10.44,
            getString(R.string.pendulum_gravity_uranus)   to 8.87,
            getString(R.string.pendulum_gravity_neptune)  to 11.15,
            getString(R.string.pendulum_gravity_jupiter)  to 24.79,
            getString(R.string.pendulum_gravity_sun)      to 274.0
        )
    }

    private lateinit var pendulumView: DoublePendulumView
    private lateinit var playPauseBtn: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastFrameMs = 0L
    private val frameMs = 16L // ~60 fps

    private val stepRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val now = System.currentTimeMillis()
            val dt = if (lastFrameMs == 0L) 0.016 else (now - lastFrameMs) / 1000.0
            lastFrameMs = now
            pendulumView.step(dt.coerceAtMost(0.05))
            handler.postDelayed(this, frameMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(buildUI())
        startSim()
    }

    private fun buildUI(): FrameLayout {
        val dp = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF0D0D1A.toInt())
        }

        pendulumView = DoublePendulumView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(pendulumView)

        root.addView(buildTopBar(dp), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.TOP })

        root.addView(buildBottomBar(dp), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.gravity = Gravity.BOTTOM })

        return root
    }

    private fun buildTopBar(dp: Float): LinearLayout {
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
            setText(R.string.pendulum_title)
            textSize = 16f
            setTextColor(0xFFCCCCFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((12 * dp).toInt(), 0, 0, 0)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        playPauseBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF7B8CDE.toInt())
            setOnClickListener { togglePlay() }
        }
        bar.addView(playPauseBtn, LinearLayout.LayoutParams((40 * dp).toInt(), (36 * dp).toInt()))

        val resetBtn = TextView(this).apply {
            setText(R.string.pendulum_reset)
            textSize = 13f
            setTextColor(0xFFCCCCFF.toInt())
            setBackgroundColor(0xFF2A2A4A.toInt())
            gravity = Gravity.CENTER
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { pendulumView.reset() }
        }
        bar.addView(resetBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.marginStart = (8 * dp).toInt() })

        return bar
    }

    private fun buildBottomBar(dp: Float): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt())
            setBackgroundColor(0xCC0D0D1A.toInt())
        }

        // ── Toggles traînées ────────────────────────────────────────────
        val trailRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * dp).toInt() }
        }

        val trailLabel = TextView(this).apply {
            setText(R.string.pendulum_trails)
            textSize = 11f
            setTextColor(0xFF8888AA.toInt())
            setPadding(0, 0, (10 * dp).toInt(), 0)
        }
        trailRow.addView(trailLabel)

        fun trailToggle(labelRes: Int, initial: Boolean, onToggle: (Boolean) -> Unit): ToggleButton {
            return ToggleButton(this).apply {
                textOn = getString(labelRes)
                textOff = getString(labelRes)
                text = getString(labelRes)
                isChecked = initial
                textSize = 11f
                setTextColor(0xFFCCCCFF.toInt())
                setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
                setBackgroundColor(if (initial) 0xFF2A2A5A.toInt() else 0xFF1A1A3A.toInt())
                setOnCheckedChangeListener { _, checked ->
                    setBackgroundColor(if (checked) 0xFF2A2A5A.toInt() else 0xFF1A1A3A.toInt())
                    onToggle(checked)
                }
            }
        }

        trailRow.addView(trailToggle(R.string.pendulum_trail_pivot2, pendulumView.showTrailPivot2) {
            pendulumView.showTrailPivot2 = it
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.marginEnd = (6 * dp).toInt() })

        trailRow.addView(trailToggle(R.string.pendulum_trail_tip, pendulumView.showTrailTip) {
            pendulumView.showTrailTip = it
        })

        bar.addView(trailRow)

        // ── Curseurs ────────────────────────────────────────────────────
        bar.addView(buildSeekRow(dp,
            labelRes = R.string.pendulum_count,
            min = 1, max = 12, initial = 8,
            format = { it.toString() }
        ) { v ->
            pendulumView.pendulumCount = v
            pendulumView.reset(v)
        })

        bar.addView(buildSeekRow(dp,
            labelRes = R.string.pendulum_arm_length,
            min = 5, max = 20, initial = 10,
            format = { "×${"%.1f".format(it / 10f)}" }
        ) { v ->
            pendulumView.armLength = v / 10.0
            pendulumView.reset()
        })

        bar.addView(buildSeekRow(dp,
            labelRes = R.string.pendulum_speed,
            min = 1, max = 13, initial = 10,
            format = { "×${"%.1f".format(it / 10f)}" }
        ) { v -> pendulumView.simSpeed = v / 10.0 })

        bar.addView(buildSeekRow(dp,
            labelRes = R.string.pendulum_damping,
            min = 0, max = 20, initial = 0,
            format = { if (it == 0) getString(R.string.pendulum_off) else "${"%.2f".format(it / 100f)}" }
        ) { v -> pendulumView.damping = v / 100.0 })

        bar.addView(buildSeekRow(dp,
            labelRes = R.string.pendulum_trail_length,
            min = 0, max = 10, initial = 6,
            format = { if (it == 0) getString(R.string.pendulum_off) else "${it * 100}" }
        ) { v -> pendulumView.trailLength = v * 100 })

        bar.addView(buildGravityRow(dp))

        return bar
    }

    private fun buildGravityRow(dp: Float): LinearLayout {
        val earthIndex = 3
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (4 * dp).toInt() }
        }

        val label = TextView(this).apply {
            setText(R.string.pendulum_gravity)
            textSize = 11f
            setTextColor(0xFF8888AA.toInt())
            minWidth = (90 * dp).toInt()
        }
        row.addView(label)

        val valueText = TextView(this).apply {
            val (name, g) = gravityPresets[earthIndex]
            text = "$name (${"%.2f".format(g)} m/s²)"
            textSize = 11f
            setTextColor(0xFFCCCCFF.toInt())
            minWidth = (110 * dp).toInt()
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, (8 * dp).toInt(), 0)
        }

        val seek = SeekBar(this).apply {
            max = gravityPresets.size - 1
            progress = earthIndex
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val (name, g) = gravityPresets[progress]
                    valueText.text = "$name (${"%.2f".format(g)} m/s²)"
                    pendulumView.gravity = g
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        row.addView(seek)
        row.addView(valueText)
        return row
    }

    private fun buildSeekRow(
        dp: Float,
        labelRes: Int,
        min: Int, max: Int, initial: Int,
        format: (Int) -> String,
        onChanged: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (4 * dp).toInt() }
        }

        val label = TextView(this).apply {
            setText(labelRes)
            textSize = 11f
            setTextColor(0xFF8888AA.toInt())
            minWidth = (90 * dp).toInt()
        }
        row.addView(label)

        val valueText = TextView(this).apply {
            text = format(initial)
            textSize = 11f
            setTextColor(0xFFCCCCFF.toInt())
            minWidth = (52 * dp).toInt()
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, (8 * dp).toInt(), 0)
        }

        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val v = progress + min
                    valueText.text = format(v)
                    onChanged(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        row.addView(seek)
        row.addView(valueText)
        return row
    }

    private fun togglePlay() {
        if (isRunning) stopSim() else startSim()
    }

    private fun startSim() {
        isRunning = true
        lastFrameMs = 0L
        playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
        handler.post(stepRunnable)
    }

    private fun stopSim() {
        isRunning = false
        playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
        handler.removeCallbacks(stepRunnable)
    }

    override fun onPause() { super.onPause(); stopSim() }
    override fun onResume() { super.onResume(); startSim() }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(stepRunnable) }
}
