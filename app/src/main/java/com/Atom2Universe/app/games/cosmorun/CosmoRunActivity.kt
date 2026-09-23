package com.Atom2Universe.app.games.cosmorun

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

class CosmoRunActivity : ThemedActivity() {
    private lateinit var root: FrameLayout
    private lateinit var runView: CosmoRunView
    private lateinit var hud: LinearLayout
    private lateinit var bottom: LinearLayout
    private lateinit var overlay: FrameLayout
    private lateinit var scoreText: TextView
    private lateinit var sectorText: TextView
    private lateinit var bonusText: TextView
    private lateinit var missionText: TextView
    private lateinit var banner: TextView
    private lateinit var missionBar: ProgressBar
    private val prefs by lazy { getSharedPreferences("cosmo_run_save", MODE_PRIVATE) }
    private var panel = Panel.HANGAR
    private var settled = true
    private var lastReward = 0
    private var selectedSuit = 0
    private var bannerUntil = 0L
    private val game get() = runView.game
    private enum class Panel { HANGAR, RUN, PAUSE, RESULTS }
    private val white = 0xFFE9F6FF.toInt()
    private val muted = 0xFFAABFD2.toInt()
    private val mint = 0xFF54E5E0.toInt()
    private val ink = 0xFF091124.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cosmo_run)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = findViewById(R.id.cosmo_root)
        runView = findViewById(R.id.cosmo_run_view)
        game.initBestScore(prefs.getInt("best_score", 0))
        game.restoreCareer(prefs.getInt("contract_level", 0), prefs.getFloat("contract_progress", 0f))
        selectedSuit = prefs.getInt("suit", 0).coerceIn(0, unlockedSuits() - 1)
        runView.renderer.suit = selectedSuit
        runView.feedbackEnabled = prefs.getBoolean("haptics", true)
        buildInterface()
        runView.onHud = { updateHud() }
        runView.onEvent = { event ->
            val text = when (event) {
                CosmoRunGame.Event.SHIELD -> R.string.cosmo_shield_ready
                CosmoRunGame.Event.MAGNET -> R.string.cosmo_magnet_ready
                CosmoRunGame.Event.BOOST -> R.string.cosmo_boost_ready
                CosmoRunGame.Event.SHIELD_BREAK -> R.string.cosmo_shield_broken
                CosmoRunGame.Event.CONTRACT -> R.string.cosmo_contract_complete
                CosmoRunGame.Event.SECTOR -> sectorName()
                CosmoRunGame.Event.CLEAR -> R.string.cosmo_clean_move
                else -> 0
            }
            if (text != 0) {
                // Un petit franchissement ne masque pas le message de contrat ou de bonus.
                if (event != CosmoRunGame.Event.CLEAR || android.os.SystemClock.uptimeMillis() >= bannerUntil) {
                    banner.text = getString(text)
                    bannerUntil = android.os.SystemClock.uptimeMillis() + if (event == CosmoRunGame.Event.CLEAR) 700L else 2400L
                }
            }
            if (event == CosmoRunGame.Event.CONTRACT) saveCareer()
        }
        runView.onGameOver = { settleRun(); showPanel(Panel.RESULTS) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (panel) {
                    Panel.RUN -> showPanel(Panel.PAUSE)
                    Panel.PAUSE -> resumeRun()
                    Panel.RESULTS -> showPanel(Panel.HANGAR)
                    Panel.HANGAR -> finish()
                }
            }
        })
        showPanel(Panel.HANGAR)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(22).toFloat(); setStroke(dp(1), stroke)
    }
    private fun label(text: String, size: Float, color: Int = white, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun button(text: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 15f
        minHeight = dp(48); minimumHeight = dp(48)
        setTextColor(if (primary) ink else white)
        background = rounded(if (primary) mint else 0xFF23354B.toInt())
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { action() }
    }
    private fun LinearLayout.addSpaced(view: View, margin: Int = 10) {
        addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(margin) })
    }

    private fun buildInterface() {
        hud = column().apply {
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xED091124.toInt(), Color.TRANSPARENT))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val scoreColumn = column()
        scoreText = label("", 25f, white, true)
        sectorText = label("", 11f, mint, true)
        scoreColumn.addView(sectorText); scoreColumn.addView(scoreText)
        top.addView(scoreColumn, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(button(getString(R.string.cosmo_pause)) { showPanel(Panel.PAUSE) }, LinearLayout.LayoutParams(-2, dp(48)))
        hud.addView(top)
        bonusText = label("", 12f, muted)
        hud.addView(bonusText)
        banner = label("", 14f, mint, true).apply { gravity = Gravity.CENTER; minHeight = dp(32) }
        hud.addView(banner)
        root.addView(hud, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        bottom = column().apply {
            setPadding(dp(16), dp(12), dp(16), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xF5091124.toInt(), Color.TRANSPARENT))
        }
        missionText = label("", 12f, white, true)
        bottom.addView(missionText)
        missionBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(mint)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF34445A.toInt())
        }
        bottom.addView(missionBar, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(6); bottomMargin = dp(10) })
        val controls = LinearLayout(this)
        val controlLabels = intArrayOf(R.string.cosmo_left, R.string.cosmo_jump, R.string.cosmo_slide, R.string.cosmo_right)
        controlLabels.forEachIndexed { index, id ->
            controls.addView(button(getString(id)) {
                if (panel == Panel.RUN) when (index) { 0 -> game.moveLeft(); 1 -> game.jump(); 2 -> game.slide(); else -> game.moveRight() }
            }.apply { textSize = 12f; setPadding(0, 0, 0, 0) },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (index != 0) leftMargin = dp(6) })
        }
        bottom.addView(controls)
        bottom.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            runView.renderer.bottomInset = bottom.height.toFloat()
        }
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        overlay = FrameLayout(this).apply { isClickable = true; isFocusable = true; setBackgroundColor(0x45050D1C) }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
            hud.setPadding(dp(18) + safe.left, dp(12) + safe.top, dp(18) + safe.right, dp(12))
            bottom.setPadding(dp(16) + safe.left, dp(12), dp(16) + safe.right, dp(10) + safe.bottom)
            overlay.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }

    private fun contractText(): String {
        val res = when (game.contract) {
            CosmoRunGame.Contract.DISTANCE -> R.string.cosmo_contract_distance
            CosmoRunGame.Contract.ATOMS -> R.string.cosmo_contract_atoms
            CosmoRunGame.Contract.SECTORS -> R.string.cosmo_contract_rows
        }
        return getString(res, game.contractProgress.toInt(), game.contractTarget)
    }
    private fun sectorName() = when (game.sector % 3) {
        1 -> R.string.cosmo_sector_crystal
        2 -> R.string.cosmo_sector_nebula
        else -> R.string.cosmo_sector_orbit
    }
    private fun updateHud() {
        if (panel != Panel.RUN) return
        scoreText.text = getString(R.string.cosmo_hud_score, game.score, game.multiplier)
        sectorText.text = getString(R.string.cosmo_sector_label, game.sector + 1, getString(sectorName()), game.distance.toInt())
        val status = when {
            game.boostTime > 0 -> getString(R.string.cosmo_boost_timer, kotlin.math.ceil(game.boostTime).toInt())
            game.magnetTime > 0 -> getString(R.string.cosmo_magnet_timer, kotlin.math.ceil(game.magnetTime).toInt())
            game.shield -> getString(R.string.cosmo_shield_ready)
            else -> getString(R.string.cosmo_no_shield)
        }
        bonusText.text = getString(R.string.cosmo_hud_stats, game.atomsCollected, status)
        missionText.text = getString(R.string.cosmo_contract_hud, game.contractLevel + 1, contractText())
        missionBar.progress = (game.contractProgress / game.contractTarget * 1000f).toInt()
        if (runView.resumeCountdown > 0f) banner.text = getString(R.string.cosmo_resume_countdown, kotlin.math.ceil(runView.resumeCountdown).toInt())
        else if (android.os.SystemClock.uptimeMillis() > bannerUntil) banner.text =
            if (game.distance < 90f) getString(R.string.cosmo_swipe_hint) else ""
    }

    private fun showPanel(next: Panel) {
        if (next == Panel.HANGAR) game.enterHangar()
        panel = next
        runView.setPaused(next != Panel.RUN && next != Panel.HANGAR)
        hud.visibility = if (next == Panel.RUN) View.VISIBLE else View.GONE
        bottom.visibility = hud.visibility
        overlay.visibility = if (next == Panel.RUN) View.GONE else View.VISIBLE
        if (next == Panel.RUN) { updateHud(); return }
        overlay.removeAllViews()
        val card = column().apply {
            background = rounded(0xF20D1B30.toInt(), 0xFF36506A.toInt())
            setPadding(dp(24), dp(22), dp(24), dp(22))
        }
        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = MeasureSpec.getSize(heightMeasureSpec)
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((available * .84f).toInt(), MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false; clipToPadding = false; addView(card) }
        val width = minOf(resources.displayMetrics.widthPixels - dp(32), dp(490))
        overlay.addView(scroll, FrameLayout.LayoutParams(width, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(18); topMargin = dp(18)
        })
        when (next) {
            Panel.HANGAR -> {
                card.addView(label(getString(R.string.cosmo_kicker), 11f, mint, true))
                card.addSpaced(label(getString(R.string.cosmo_run_title), 38f, white, true), 3)
                card.addSpaced(label(getString(R.string.cosmo_run_subtitle), 14f, muted), 2)
                card.addSpaced(label(getString(R.string.cosmo_career, game.contractLevel, game.bestScore), 12f, mint), 14)
                card.addSpaced(label(getString(R.string.cosmo_contract_hud, game.contractLevel + 1, contractText()), 15f, white, true), 10)
                card.addSpaced(label(getString(R.string.cosmo_contract_persistent), 12f, muted), 3)
                card.addSpaced(button(getString(R.string.cosmo_run_btn_start), true) { startRun() }, 18)
                card.addSpaced(button(getString(R.string.cosmo_suit_button, suitName())) { showSuits() })
                val row = LinearLayout(this)
                row.addView(button(getString(R.string.cosmo_guide)) { showGuide() }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(button(getString(R.string.cosmo_run_btn_back)) { finish() }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                card.addSpaced(row)
            }
            Panel.PAUSE -> {
                card.addView(label(getString(R.string.cosmo_pause_title), 30f, white, true))
                card.addSpaced(label(contractText(), 15f, mint))
                card.addSpaced(button(getString(R.string.cosmo_resume), true) { resumeRun() }, 20)
                card.addSpaced(button(getString(R.string.cosmo_guide)) { showGuide() })
                card.addSpaced(button(getString(R.string.cosmo_finish_run)) { settleRun(); showPanel(Panel.RESULTS) })
            }
            Panel.RESULTS -> {
                card.addView(label(getString(R.string.cosmo_run_game_over), 12f, mint, true))
                card.addSpaced(label(getString(R.string.cosmo_run_score_label, game.score), 32f, white, true), 6)
                val reason = if (!game.isGameOver) R.string.cosmo_run_banked else when (game.crashType) {
                    CosmoRunGame.EntityType.CARGO -> R.string.cosmo_crash_cargo
                    CosmoRunGame.EntityType.HURDLE -> R.string.cosmo_crash_hurdle
                    CosmoRunGame.EntityType.GAP -> R.string.cosmo_crash_gap
                    else -> R.string.cosmo_crash_laser
                }
                card.addSpaced(label(getString(reason), 14f, muted))
                card.addSpaced(label(getString(R.string.cosmo_run_stats_label, game.distance.toInt(), game.atomsCollected), 17f, white, true), 16)
                card.addSpaced(label(getString(R.string.cosmo_results_details, game.maxChain, game.contractsThisRun, lastReward), 14f, mint), 6)
                card.addSpaced(label(getString(R.string.cosmo_run_best_label, game.bestScore), 13f, muted), 6)
                card.addSpaced(label(contractText(), 14f, white))
                card.addSpaced(button(getString(R.string.cosmo_run_btn_restart), true) { startRun() }, 20)
                card.addSpaced(button(getString(R.string.cosmo_hangar)) { showPanel(Panel.HANGAR) })
            }
            else -> Unit
        }
    }

    private fun startRun() {
        settled = false; lastReward = 0; bannerUntil = 0L
        runView.startGame(); showPanel(Panel.RUN)
    }
    private fun resumeRun() { showPanel(Panel.RUN); runView.requestFocus() }
    private fun saveCareer() {
        prefs.edit {
            putInt("contract_level", game.contractLevel); putFloat("contract_progress", game.contractProgress)
            putInt("suit", selectedSuit); putInt("best_score", game.bestScore)
        }
    }
    private fun settleRun() {
        if (settled) return
        settled = true
        game.initBestScore(game.score)
        lastReward = NeutrinoRewards.perDistance(game.distance)
        if (lastReward > 0) NeutrinoRepository(this).addBalance(lastReward)
        saveCareer()
    }
    private fun unlockedSuits() = when { game.contractLevel >= 9 -> 3; game.contractLevel >= 3 -> 2; else -> 1 }
    private fun suitName(index: Int = selectedSuit) = getString(when (index) {
        1 -> R.string.cosmo_suit_solar
        2 -> R.string.cosmo_suit_void
        else -> R.string.cosmo_suit_orbit
    })
    private fun showSuits() {
        val names = Array(3) { i ->
            if (i < unlockedSuits()) suitName(i)
            else getString(R.string.cosmo_suit_locked, suitName(i), if (i == 1) 3 else 9)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.cosmo_suits_title)
            .setSingleChoiceItems(names, selectedSuit) { dialog, which ->
                if (which < unlockedSuits()) {
                    selectedSuit = which; runView.renderer.suit = which; saveCareer()
                    runView.invalidate(); dialog.dismiss(); showPanel(Panel.HANGAR)
                } else {
                    (dialog as androidx.appcompat.app.AlertDialog).listView.setItemChecked(selectedSuit, true)
                    Toast.makeText(this, names[which], Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(R.string.cosmo_close, null).show()
    }
    private fun showGuide() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.cosmo_guide)
            .setMessage(R.string.cosmo_run_instructions)
            .setPositiveButton(R.string.cosmo_close, null)
            .setNeutralButton(if (runView.feedbackEnabled) R.string.cosmo_haptics_off else R.string.cosmo_haptics_on) { _, _ ->
                runView.feedbackEnabled = !runView.feedbackEnabled
                prefs.edit { putBoolean("haptics", runView.feedbackEnabled) }
            }.show()
    }
    override fun onResume() { super.onResume(); runView.resume() }
    override fun onPause() {
        if (panel == Panel.RUN) showPanel(Panel.PAUSE)
        saveCareer(); runView.pause(); super.onPause()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (panel == Panel.RUN) showPanel(Panel.PAUSE) else showPanel(panel)
    }
}
