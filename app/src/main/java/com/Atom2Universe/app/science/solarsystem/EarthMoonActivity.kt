package com.Atom2Universe.app.science.solarsystem

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class EarthMoonActivity : ThemedActivity() {

    companion object {
        const val EXTRA_ELAPSED_DAYS = "extra_elapsed_days_j2000"
    }

    private lateinit var glView: EarthMoonGLView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var tvSpeed: TextView
    private lateinit var speedSlider: StepSpeedSlider
    private lateinit var btnModeClose: TextView
    private lateinit var btnModeReal: TextView
    private lateinit var cardInfo: CardView
    private lateinit var tvBodyName: TextView
    private lateinit var tvDate: TextView
    private lateinit var btnBodySelector: TextView

    // Grille d'infos — 4 lignes (label + valeur)
    private val infoLabels = arrayOfNulls<TextView>(4)
    private val infoValues = arrayOfNulls<TextView>(4)

    private val J2000 = LocalDate.of(2000, 1, 1)
    private val dateHandler = Handler(Looper.getMainLooper())
    private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val dateUpdater = object : Runnable {
        override fun run() {
            updateDateLabel()
            // Distance et phase de la Lune changent avec le temps : la carte restait figée
            // sur les valeurs du moment où on l'avait ouverte.
            if (cardInfo.visibility == View.VISIBLE && selectedBody == 1) showBodyCard(1)
            dateHandler.postDelayed(this, 500)
        }
    }

    private var selectedBody = 0  // 0=Terre, 1=Lune
    private var lastActiveStep = SolarSystemActivity.DEFAULT_STEP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        buildUI()
        setupListeners()

        val days = intent.getDoubleExtra(EXTRA_ELAPSED_DAYS,
            ChronoUnit.DAYS.between(J2000, LocalDate.now()).toDouble())
        glView.renderer.elapsedSimDays = days
    }

    override fun onResume() {
        super.onResume(); glView.onResume(); dateHandler.post(dateUpdater)
    }

    override fun onPause() {
        super.onPause(); glView.onPause(); dateHandler.removeCallbacks(dateUpdater)
    }

    /** Rend la date courante au système solaire, quel que soit le chemin de sortie. */
    override fun finish() {
        setResult(RESULT_OK, android.content.Intent().putExtra(EXTRA_ELAPSED_DAYS, glView.renderer.elapsedSimDays))
        super.finish()
    }

    // ─────────────────────────────────────────────────────────────
    private fun buildUI() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        glView = EarthMoonGLView(this)
        root.addView(glView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // ── Barre supérieure ─────────────────────────────────────
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(0xAA000000.toInt())
        }
        val btnBack = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back); setColorFilter(Color.WHITE); background = null
            contentDescription = getString(R.string.back)
            setOnClickListener { finish() }
        }
        tvDate = TextView(this).apply {
            textSize = 13f; setTextColor(0xFFDDEEFF.toInt()); gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(0x33FFFFFF)
            contentDescription = getString(R.string.solar_change_date)
            setOnClickListener { showDatePicker() }
        }
        // Bouton bascule vers la vue Système solaire — icône du mode courant (Terre-Lune)
        val btnSwitchSS = ImageButton(this).apply {
            setImageBitmap(loadAssetBitmap("Assets/sprites/Planet_Terre.png"))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            background = null
            setPadding(dp(3), dp(3), dp(3), dp(3))
            contentDescription = getString(R.string.solar_open_solar_system)
            setOnClickListener { finish() }
        }
        btnBodySelector = TextView(this).apply {
            textSize = 12f; setTextColor(0xFFFFDD88.toInt()); gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(0x33FFFFFF)
            contentDescription = getString(R.string.solar_choose_body)
            setOnClickListener { showBodyDropdown() }
        }
        topBar.addView(btnBack, LinearLayout.LayoutParams(dp(40), dp(40)))
        topBar.addView(tvDate, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(6); rightMargin = dp(6) })
        topBar.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))  // spacer flexible
        topBar.addView(btnSwitchSS, LinearLayout.LayoutParams(dp(36), dp(36))
            .apply { rightMargin = dp(6) })
        topBar.addView(btnBodySelector, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)))
        root.addView(topBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP })

        // ── Carte info ───────────────────────────────────────────
        cardInfo = CardView(this).apply {
            radius = dp(12).toFloat(); cardElevation = dp(8).toFloat()
            setCardBackgroundColor(0xCC101025.toInt()); visibility = View.GONE
        }
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val cardHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        tvBodyName = TextView(this).apply {
            textSize = 20f; setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnCloseCard = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close); setColorFilter(0xFFAAAAAA.toInt()); background = null
            contentDescription = getString(R.string.solar_close_card)
            setOnClickListener { cardInfo.visibility = View.GONE }
        }
        cardHeader.addView(tvBodyName)
        cardHeader.addView(btnCloseCard, LinearLayout.LayoutParams(dp(32), dp(32)))
        cardContent.addView(cardHeader)

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        repeat(4) { i ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(3), 0, dp(3))
            }
            infoLabels[i] = TextView(this).apply {
                textSize = 12f; setTextColor(0xFF888888.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoValues[i] = TextView(this).apply {
                textSize = 12f; setTextColor(0xFFDDEEFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            row.addView(infoLabels[i]); row.addView(infoValues[i]); grid.addView(row)
        }
        cardContent.addView(grid); cardInfo.addView(cardContent)
        root.addView(cardInfo, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM; bottomMargin = dp(130); leftMargin = dp(16); rightMargin = dp(16) })

        // ── Panneau bas ──────────────────────────────────────────
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(0xBB000000.toInt())
        }

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        btnModeClose = modeBtn(getString(R.string.solar_mode_close))
        btnModeReal  = modeBtn(getString(R.string.solar_mode_real))
        modeRow.addView(btnModeClose, LinearLayout.LayoutParams(0, dp(32), 1f).apply { rightMargin = dp(6) })
        modeRow.addView(btnModeReal,  LinearLayout.LayoutParams(0, dp(32), 1f))
        bottomPanel.addView(modeRow)

        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        btnPlayPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause); setColorFilter(Color.WHITE); background = null
            contentDescription = getString(R.string.solar_play_pause)
        }
        tvSpeed = TextView(this).apply {
            textSize = 11f; setTextColor(0xFFCCCCCC.toInt()); gravity = Gravity.END
            minWidth = dp(80)
        }
        speedSlider = StepSpeedSlider(this).apply {
            stepCount = SolarSystemActivity.SPEED_STEPS.size
            centerStep = SolarSystemActivity.PAUSE_STEP
            setStep(SolarSystemActivity.DEFAULT_STEP)
            contentDescription = getString(R.string.solar_speed_slider)
        }
        speedRow.addView(btnPlayPause, LinearLayout.LayoutParams(dp(40), dp(40)))
        speedRow.addView(speedSlider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { leftMargin = dp(8); rightMargin = dp(8) })
        speedRow.addView(tvSpeed, LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT))
        bottomPanel.addView(speedRow)
        root.addView(bottomPanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })

        setContentView(root)
        updateModeButtons()
        glView.renderer.speedDaysPerSec = SolarSystemActivity.SPEED_STEPS[SolarSystemActivity.DEFAULT_STEP]
        tvSpeed.text = SolarSystemActivity.stepLabel(this, SolarSystemActivity.DEFAULT_STEP)
        updateBodySelector()
    }

    // ─────────────────────────────────────────────────────────────
    private fun setupListeners() {
        btnPlayPause.setOnClickListener {
            if (speedSlider.step == SolarSystemActivity.PAUSE_STEP) {
                speedSlider.setStep(lastActiveStep)
                applyStep(lastActiveStep)
            } else {
                lastActiveStep = speedSlider.step
                speedSlider.setStep(SolarSystemActivity.PAUSE_STEP)
                applyStep(SolarSystemActivity.PAUSE_STEP)
            }
        }
        speedSlider.onStepChanged = { p ->
            if (p != SolarSystemActivity.PAUSE_STEP) lastActiveStep = p
            applyStep(p)
        }
        btnModeClose.setOnClickListener { glView.renderer.targetBlend = 0f; updateModeButtons() }
        btnModeReal.setOnClickListener  { glView.renderer.targetBlend = 1f; updateModeButtons() }

        glView.renderer.onBodyTapped = { idx -> runOnUiThread { showBodyCard(idx) } }
    }

    // ─────────────────────────────────────────────────────────────
    private fun updateModeButtons() {
        val on = 0xFF4466AA.toInt(); val off = 0x33FFFFFF
        btnModeClose.setBackgroundColor(if (glView.renderer.targetBlend < 0.5f) on else off)
        btnModeReal.setBackgroundColor( if (glView.renderer.targetBlend >= 0.5f) on else off)
    }

    private fun updateDateLabel() {
        val date = SolarSystemActivity.simDate(J2000, glView.renderer.elapsedSimDays)
        tvDate.text = date.format(dateFmt)
    }

    private fun updateBodySelector() {
        btnBodySelector.text = getString(R.string.solar_body_selector, bodyName(selectedBody))
    }

    private fun applyStep(p: Int) {
        val speed = SolarSystemActivity.SPEED_STEPS[p]
        glView.renderer.speedDaysPerSec = speed
        glView.renderer.paused = (speed == 0.0)
        btnPlayPause.setImageResource(
            if (speed == 0.0) android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause)
        tvSpeed.text = SolarSystemActivity.stepLabel(this, p)
    }

    private fun showDatePicker() {
        val cur = SolarSystemActivity.simDate(J2000, glView.renderer.elapsedSimDays)
        DatePickerDialog(this, { _, y, m, d ->
            glView.renderer.elapsedSimDays = ChronoUnit.DAYS.between(J2000, LocalDate.of(y, m + 1, d)).toDouble()
        }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show()
    }

    private fun showBodyDropdown() {
        val popup = PopupMenu(this, btnBodySelector)
        popup.menu.add(0, 0, 0, bodyName(0))
        popup.menu.add(0, 1, 1, bodyName(1))
        popup.setOnMenuItemClickListener { item -> focusOnBody(item.itemId); true }
        popup.show()
    }

    private fun focusOnBody(idx: Int) {
        selectedBody = idx
        glView.renderer.focusBody = idx
        glView.renderer.cameraDistance = glView.renderer.recommendedDistance(idx)
        updateBodySelector()
        showBodyCard(idx)
    }

    private fun showBodyCard(idx: Int) {
        selectedBody = idx
        val lunarPos = LunarCalculator.position(glView.renderer.elapsedSimDays)
        tvBodyName.text = bodyName(idx)
        if (idx == 0) {
            setRow(0, getString(R.string.solar_info_radius_label), getString(R.string.solar_info_km, 6_371.0))
            setRow(1, getString(R.string.solar_info_tilt), getString(R.string.solar_info_degrees, 23.44))
            // Rotation sidérale (23 h 56 min), comme pour les planètes ; 24 h est le jour solaire.
            setRow(2, getString(R.string.solar_info_rotation), getString(R.string.solar_info_rotation_hm, 23, 56))
            setRow(3, getString(R.string.solar_info_satellites), "1")
        } else {
            setRow(0, getString(R.string.solar_info_distance), getString(R.string.solar_info_km, lunarPos.distanceKm))
            setRow(1, getString(R.string.solar_info_phase), moonPhaseName(lunarPos))
            setRow(2, getString(R.string.solar_info_radius_label), getString(R.string.solar_info_km, 1_737.0))
            setRow(3, getString(R.string.solar_info_synodic), getString(R.string.solar_info_days_precise, 29.53))
        }
        cardInfo.visibility = View.VISIBLE
        updateBodySelector()
    }

    private fun setRow(i: Int, label: String, value: String) {
        infoLabels[i]?.text = label; infoValues[i]?.text = value
    }

    private fun moonPhaseName(pos: LunarCalculator.LunarPosition): String {
        val earthLong = OrbitalCalculator.orbitAngleDeg(SolarSystemData.planets[2],
            glView.renderer.elapsedSimDays).toDouble()
        val sunLong = (earthLong + 180.0) % 360.0
        val elong = ((pos.longitude - sunLong + 360.0) % 360.0).roundToInt()
        val name = getString(when {
            elong < 22  -> R.string.solar_phase_new
            elong < 68  -> R.string.solar_phase_waxing_crescent
            elong < 112 -> R.string.solar_phase_first_quarter
            elong < 158 -> R.string.solar_phase_waxing_gibbous
            elong < 202 -> R.string.solar_phase_full
            elong < 248 -> R.string.solar_phase_waning_gibbous
            elong < 292 -> R.string.solar_phase_last_quarter
            elong < 338 -> R.string.solar_phase_waning_crescent
            else        -> R.string.solar_phase_new
        })
        return getString(R.string.solar_phase_value, name, elong)
    }

    private fun bodyName(idx: Int): String =
        getString(if (idx == 1) R.string.solar_moon_name else R.string.solar_planet_earth)

    private fun modeBtn(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(0x33FFFFFF)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadAssetBitmap(path: String) =
        try { assets.open(path).use { BitmapFactory.decodeStream(it) } }
        catch (_: java.io.IOException) { null }
}
