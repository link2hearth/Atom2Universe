package com.Atom2Universe.app.science.solarsystem

import android.app.DatePickerDialog
import android.graphics.Color
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
import androidx.cardview.widget.CardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.pow

class SolarSystemActivity : ThemedActivity() {

    private lateinit var glView: SolarSystemGLView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var tvSpeed: TextView
    private lateinit var seekSpeed: SeekBar
    private lateinit var btnModeClose: TextView
    private lateinit var btnModeLog: TextView
    private lateinit var btnModeReal: TextView
    private lateinit var cardPlanetInfo: CardView
    private lateinit var tvPlanetName: TextView
    private lateinit var tvPlanetRadius: TextView
    private lateinit var tvPlanetOrbit: TextView
    private lateinit var tvPlanetPeriod: TextView
    private lateinit var tvPlanetMoons: TextView
    private lateinit var tvPlanetTilt: TextView
    private lateinit var tvFocusLabel: TextView
    private lateinit var tvDate: TextView

    private val refDate: LocalDate = LocalDate.now()  // elapsedSimDays=0 = aujourd'hui
    private val dateHandler = Handler(Looper.getMainLooper())
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val dateUpdater = object : Runnable {
        override fun run() {
            updateDateLabel()
            dateHandler.postDelayed(this, 500)
        }
    }

    private var selectedPlanetIdx = -2  // -2=rien, -1=Soleil, 0..7=planète
    private var currentMode = ProportionMode.CLOSE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        buildUI()
        setupListeners()
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // GL View
        glView = SolarSystemGLView(this)
        root.addView(glView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // ── Top bar ──────────────────────────────────────────────
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(0xAA000000.toInt())
        }
        val btnBack = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(Color.WHITE)
            background = null
            setOnClickListener { finish() }
        }
        val tvTitle = TextView(this).apply {
            text = getString(R.string.solar_system_title)
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        // Label focus (ex. "⊙ Earth") avec clic pour retour Soleil
        tvFocusLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFDD88.toInt())
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setBackgroundColor(0x99000000.toInt())
            visibility = View.GONE
            setOnClickListener { resetFocus() }
        }

        topBar.addView(btnBack, LinearLayout.LayoutParams(dp(40), dp(40)))
        topBar.addView(tvTitle)
        topBar.addView(tvFocusLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)
        ).apply { gravity = Gravity.CENTER_VERTICAL })

        val topParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        root.addView(topBar, topParams)

        // ── Carte info planète ────────────────────────────────────
        cardPlanetInfo = CardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(0xCC101025.toInt())
            visibility = View.GONE
        }
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val cardHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tvPlanetName = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCloseCard = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(0xFFAAAAAA.toInt())
            background = null
            setOnClickListener { cardPlanetInfo.visibility = View.GONE }
        }
        cardHeader.addView(tvPlanetName)
        cardHeader.addView(btnCloseCard, LinearLayout.LayoutParams(dp(32), dp(32)))
        cardContent.addView(cardHeader)

        // Infos tabulaires
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        tvPlanetRadius = addInfoRow(grid, getString(R.string.solar_info_radius), "")
        tvPlanetOrbit  = addInfoRow(grid, getString(R.string.solar_info_orbit), "")
        tvPlanetPeriod = addInfoRow(grid, getString(R.string.solar_info_period), "")
        tvPlanetMoons  = addInfoRow(grid, getString(R.string.solar_info_moons), "")
        tvPlanetTilt   = addInfoRow(grid, getString(R.string.solar_info_tilt), "")
        cardContent.addView(grid)
        cardPlanetInfo.addView(cardContent)

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(130)
            leftMargin = dp(16); rightMargin = dp(16)
        }
        root.addView(cardPlanetInfo, cardParams)

        // ── Panneau bas ───────────────────────────────────────────
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(0xBB000000.toInt())
        }

        // Chip date — tappable, ouvre le DatePicker
        tvDate = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFDDEEFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setBackgroundColor(0x33FFFFFF)
            setOnClickListener { showDatePicker() }
        }
        bottomPanel.addView(tvDate, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Sélecteur de mode
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnModeClose = modeChip(getString(R.string.solar_mode_close), true)
        btnModeLog   = modeChip(getString(R.string.solar_mode_log), false)
        btnModeReal  = modeChip(getString(R.string.solar_mode_real), false)
        modeRow.addView(btnModeClose, LinearLayout.LayoutParams(0, dp(30), 1f).apply { rightMargin = dp(6) })
        modeRow.addView(btnModeLog,   LinearLayout.LayoutParams(0, dp(30), 1f).apply { rightMargin = dp(6) })
        modeRow.addView(btnModeReal,  LinearLayout.LayoutParams(0, dp(30), 1f))
        bottomPanel.addView(modeRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Contrôles du temps
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        btnPlayPause = ImageButton(this).apply {
            setImageResource(R.drawable.ic_pause)
            setColorFilter(Color.WHITE)
            background = null
        }
        seekSpeed = SeekBar(this).apply {
            max = 100; progress = 35   // ≈ 1 j/s au démarrage
        }
        tvSpeed = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            minWidth = dp(80)
        }
        timeRow.addView(btnPlayPause, LinearLayout.LayoutParams(dp(40), dp(40)))
        timeRow.addView(seekSpeed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(8); rightMargin = dp(8)
        })
        timeRow.addView(tvSpeed, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        bottomPanel.addView(timeRow)

        val bottomParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }
        root.addView(bottomPanel, bottomParams)

        setContentView(root)
        glView.renderer.speedDaysPerSec = progressToSpeed(seekSpeed.progress)
        updateSpeedLabel(seekSpeed.progress)
        updateModeButtons()
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener {
            glView.renderer.paused = !glView.renderer.paused
            btnPlayPause.setImageResource(
                if (glView.renderer.paused) R.drawable.ic_play else R.drawable.ic_pause
            )
        }

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                glView.renderer.speedDaysPerSec = progressToSpeed(p)
                updateSpeedLabel(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnModeClose.setOnClickListener { setMode(ProportionMode.CLOSE) }
        btnModeLog.setOnClickListener   { setMode(ProportionMode.COMPRESSED) }
        btnModeReal.setOnClickListener  { setMode(ProportionMode.REALISTIC) }

        glView.renderer.onPlanetTapped = { idx ->
            runOnUiThread { showPlanetInfo(idx) }
        }

        glView.renderer.onPlanetLongPressed = { idx ->
            runOnUiThread { updateFocusLabel(idx) }
        }
    }

    private fun resetFocus() {
        glView.renderer.focusPlanetIdx = -1
        glView.renderer.panX = 0f; glView.renderer.panY = 0f
        glView.renderer.cameraDistance = glView.renderer.recommendedDistance(-1)
        updateFocusLabel(-1)
    }

    private fun updateFocusLabel(idx: Int) {
        if (idx < 0) {
            tvFocusLabel.visibility = View.GONE
        } else {
            val name = SolarSystemData.planets[idx].name
            tvFocusLabel.text = "⊙ $name  ×"
            tvFocusLabel.visibility = View.VISIBLE
        }
    }

    private fun setMode(mode: ProportionMode) {
        currentMode = mode
        glView.renderer.targetModeBlend = when (mode) {
            ProportionMode.CLOSE       -> 0f
            ProportionMode.COMPRESSED  -> 1f
            ProportionMode.REALISTIC   -> 2f
        }
        // Ajuster la distance caméra par défaut
        val targetDist = when (mode) {
            ProportionMode.CLOSE       -> 25f
            ProportionMode.COMPRESSED  -> 20f
            ProportionMode.REALISTIC   -> 220f
        }
        glView.renderer.cameraDistance = targetDist
        updateModeButtons()
    }

    private fun showPlanetInfo(idx: Int) {
        if (idx == -1) {
            // Soleil
            tvPlanetName.text = getString(R.string.solar_sun_name)
            tvPlanetRadius.text = "696 000 km"
            tvPlanetOrbit.text = "—"
            tvPlanetPeriod.text = "25.4 j (équateur)"
            tvPlanetMoons.text = "—"
            tvPlanetTilt.text = "7.25°"
        } else if (idx in 0..7) {
            val p = SolarSystemData.planets[idx]
            tvPlanetName.text = p.name
            tvPlanetRadius.text = getString(R.string.solar_info_km,
                (p.radiusKm * 2).toDouble())
            tvPlanetOrbit.text = getString(R.string.solar_info_au, p.orbitRadiusAU.toDouble())
            val days = p.orbitalPeriodDays
            tvPlanetPeriod.text = if (days >= 365.25)
                getString(R.string.solar_info_years, (days / 365.25).toDouble())
            else
                getString(R.string.solar_info_days, days.toDouble())
            tvPlanetMoons.text = p.knownMoons.toString()
            tvPlanetTilt.text = "${p.axialTiltDeg}°"
        }
        cardPlanetInfo.visibility = View.VISIBLE
        selectedPlanetIdx = idx
    }

    private fun currentSimDate(): LocalDate =
        refDate.plusDays(glView.renderer.elapsedSimDays.toLong())

    private fun updateDateLabel() {
        tvDate.text = "📅  ${currentSimDate().format(dateFmt)}"
    }

    private fun showDatePicker() {
        val d = currentSimDate()
        DatePickerDialog(this, { _, year, month, day ->
            val picked = LocalDate.of(year, month + 1, day)
            glView.renderer.elapsedSimDays = ChronoUnit.DAYS.between(refDate, picked).toDouble()
            updateDateLabel()
        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    // Plage : p=0 → 1 h/s  |  p=100 → 1 an/s  (log scale)
    private fun progressToSpeed(p: Int): Double {
        val logMin = Math.log10(1.0 / 24.0)   // 1 heure = 1 seconde
        val logMax = Math.log10(365.25)        // 1 an    = 1 seconde
        return Math.pow(10.0, logMin + (logMax - logMin) * p / 100.0)
    }

    private fun updateSpeedLabel(progress: Int) {
        val daysPerSec = progressToSpeed(progress)
        tvSpeed.text = when {
            daysPerSec < 1.0 -> getString(R.string.solar_speed_hours, daysPerSec * 24.0)
            daysPerSec < 365.25 -> getString(R.string.solar_speed_days, daysPerSec)
            else -> getString(R.string.solar_speed_years, daysPerSec / 365.25)
        }
    }

    private fun updateModeButtons() {
        val activeColor  = 0xFF1565C0.toInt()
        val inactiveColor = 0xFF333333.toInt()
        btnModeClose.setBackgroundColor(if (currentMode == ProportionMode.CLOSE)      activeColor else inactiveColor)
        btnModeLog.setBackgroundColor(  if (currentMode == ProportionMode.COMPRESSED) activeColor else inactiveColor)
        btnModeReal.setBackgroundColor( if (currentMode == ProportionMode.REALISTIC)  activeColor else inactiveColor)
    }

    private fun modeChip(text: String, selected: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(4), dp(4), dp(4))
        setBackgroundColor(if (selected) 0xFF1565C0.toInt() else 0xFF333333.toInt())
    }

    private fun addInfoRow(parent: LinearLayout, label: String, value: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val tvLabel = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvValue = TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(tvLabel); row.addView(tvValue)
        parent.addView(row)
        return tvValue
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        glView.onResume()
        dateHandler.post(dateUpdater)
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        dateHandler.removeCallbacks(dateUpdater)
    }
}
