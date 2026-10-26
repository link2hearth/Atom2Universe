package com.Atom2Universe.app.games.mochi

import android.graphics.Canvas
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButton

class MochiActivity : ThemedActivity() {
    private enum class Screen { HOME, PLAY, PAUSE, UPGRADE, END }
    private var screen = Screen.HOME
    private lateinit var scene: MochiView
    private lateinit var overlay: ScrollView
    private lateinit var heading: TextView
    private lateinit var explanation: TextView
    private lateinit var result: TextView
    private lateinit var mastery: TextView
    private lateinit var hud: TextView
    private lateinit var heightChip: TextView
    private lateinit var comboBadge: TextView
    private lateinit var hudPanel: LinearLayout
    private lateinit var menuCard: LinearLayout
    private lateinit var loadout: TextView
    private lateinit var hint: TextView
    private lateinit var pauseButton: MaterialButton
    private lateinit var primary: MaterialButton
    private lateinit var secondary: MaterialButton
    private lateinit var help: MaterialButton
    private lateinit var mode: SwitchCompat
    private lateinit var modeCopy: TextView
    private lateinit var upgradeCards: LinearLayout
    private lateinit var hero: View
    private val ink = 0xFF264E49.toInt()
    private val muted = 0xFF638078.toInt()
    private var displayedScore = -1
    private val prefs by lazy { getSharedPreferences("mochi_save", MODE_PRIVATE) }
    private var selectedEasy = false
    private var best = 0
    private var bestAtStart = 0
    private var paid = false
    private var hasRun = false
    private var showingRules = false
    private data class Prowess(val key: String, val target: Int, val title: Int, val value: (MochiGame) -> Int)
    private val prowess = listOf(
        Prowess("chain3", 3, R.string.mochi_goal_chain) { it.bestCombo },
        Prowess("rest1", 1, R.string.mochi_goal_rests) { it.sanctuaries },
        Prowess("detour2", 2, R.string.mochi_goal_detours) { it.detours },
        Prowess("chain5", 5, R.string.mochi_goal_chain) { it.bestCombo },
        Prowess("rest3", 3, R.string.mochi_goal_rests) { it.sanctuaries },
        Prowess("perfect20", 20, R.string.mochi_goal_perfects) { it.perfects }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedEasy = prefs.getBoolean("easy_mode", false)
        best = bestFor(selectedEasy)
        buildUi()
        enableImmersiveMode()
        scene.onChanged = { updateHud() }
        scene.onRest = { showOverlay(Screen.UPGRADE) }
        scene.onFinished = {
            val newBest = scene.game.score > bestAtStart
            val reward = settle()
            showOverlay(Screen.END)
            explanation.setText(when (scene.game.end) {
                MochiGame.End.FALL -> R.string.mochi_end_fall
                MochiGame.End.THORN -> R.string.mochi_end_thorn
                MochiGame.End.BEE -> R.string.mochi_end_bee
            })
            result.text = getString(R.string.mochi_result, scene.game.score, scene.game.heightMetres,
                scene.game.caught, scene.game.treats, best, reward, scene.game.detours, scene.game.sanctuaries,
                scene.game.perfects, scene.game.bestCombo, scene.game.bankShots)
            if (newBest) heading.setText(R.string.mochi_new_record)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    Screen.PLAY, Screen.UPGRADE -> showOverlay(Screen.PAUSE)
                    Screen.PAUSE -> resumeGame()
                    else -> leaveGame()
                }
            }
        })
        updateHud()
        showOverlay(Screen.HOME)
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        scene = MochiView(this)
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))
        val chrome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xB51B3B40.toInt(), 0x001B3B40))
        }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            imageTintList = ColorStateList.valueOf(0xFFFFF3DC.toInt())
            background = panel(0xAA214A47.toInt(), 0x447FD5BA, 24)
            contentDescription = getString(R.string.mochi_back)
            setOnClickListener { if (screen == Screen.PLAY || screen == Screen.UPGRADE) showOverlay(Screen.PAUSE) else leaveGame() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        toolbar.addView(label(R.string.mochi_title, 20f).apply {
            gravity = Gravity.CENTER
            setTextColor(0xFFFFF4D8.toInt())
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xAA193C39.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        pauseButton = button(R.string.mochi_pause) { showOverlay(Screen.PAUSE) }.apply {
            backgroundTintList = ColorStateList.valueOf(0xD92C6158.toInt())
            setTextColor(0xFFFFF5DE.toInt())
            textSize = 12f
            minWidth = dp(64)
            setPadding(dp(8), 0, dp(8), 0)
        }
        toolbar.addView(pauseButton, LinearLayout.LayoutParams(-2, dp(48)))
        chrome.addView(toolbar, LinearLayout.LayoutParams(-1, dp(50)))
        hudPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val meters = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hud = label(R.string.mochi_title, 12f).apply {
            setPadding(dp(14), dp(7), dp(12), dp(7))
            background = panel(0xF7FFF2D4.toInt(), 0xFFFFDB85.toInt(), 20)
            gravity = Gravity.CENTER_VERTICAL
            val icon = BitmapDrawable(resources, MochiSprites.get(this@MochiActivity).starfruit)
            icon.setBounds(0, 0, dp(30), dp(30))
            setCompoundDrawables(icon, null, null, null)
            compoundDrawablePadding = dp(8)
        }
        meters.addView(hud, LinearLayout.LayoutParams(0, dp(65), 1f))
        heightChip = label(R.string.mochi_title, 11f).apply {
            setTextColor(0xFFEEFFE8.toInt())
            setPadding(dp(12), dp(7), dp(12), dp(7))
            gravity = Gravity.CENTER
            background = panel(0xE82A5B54.toInt(), 0x667FD5BA, 20)
        }
        meters.addView(heightChip, LinearLayout.LayoutParams(0, dp(65), .85f).apply { leftMargin = dp(8) })
        hudPanel.addView(meters)
        val detailRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        comboBadge = label(R.string.mochi_title, 12f).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = panel(0xF9F6B9CD.toInt(), 0x66FFFFFF, 12)
            typeface = Typeface.DEFAULT_BOLD
        }
        detailRow.addView(comboBadge, LinearLayout.LayoutParams(-2, -2))
        loadout = label(R.string.mochi_title, 10f).apply {
            setTextColor(0xFFF1FFEC.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(4), dp(7), dp(4))
            background = panel(0xD0244A47.toInt(), 0x448BC9A9, 12)
        }
        detailRow.addView(loadout, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        hudPanel.addView(detailRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        chrome.addView(hudPanel)
        root.addView(chrome, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        hint = label(R.string.mochi_hint, 12f).apply {
            gravity = Gravity.CENTER
            setTextColor(0xFFFFF4DB.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = panel(0xDF234D49.toInt(), 0x558DCAA9, 20)
        }
        root.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(18), 0, dp(18), dp(12))
        })
        // Reserve space from measured controls, including enlarged accessibility fonts.
        fun updateInsets() = scene.setViewportInsets(
            maxOf(dp(136), chrome.bottom),
            if (hint.visibility == View.VISIBLE) hint.height + dp(18) else dp(48))
        chrome.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateInsets() }
        hint.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateInsets() }

        overlay = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(62), 0, dp(12))
            setBackgroundColor(0x60203939)
            isClickable = true
        }
        val frame = FrameLayout(this).apply { setPadding(dp(20), dp(12), dp(20), dp(12)) }
        menuCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(12), dp(22), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFFF8E7.toInt(), 0xFFF4E9CE.toInt())).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(2), 0xFFFFDF99.toInt())
            }
            elevation = dp(12).toFloat()
        }
        hero = object : View(this) {
            private val art = MochiArt(this@MochiActivity)
            override fun onDraw(canvas: Canvas) {
                val time = (android.os.SystemClock.uptimeMillis() % 60000) / 1000f
                val unit = minOf(width / 270f, height / 170f)
                canvas.save()
                canvas.translate(width / 2f, height / 2f + 8 * unit)
                canvas.scale(unit, unit)
                for (i in 0..4) {
                    val a = time * .3f + i * 1.256f
                    art.sparkle(canvas, kotlin.math.cos(a) * 99, kotlin.math.sin(a) * 61,
                        3f + kotlin.math.sin(time * 2 + i), 0xFFE7B85D.toInt())
                }
                canvas.save(); canvas.scale(1.5f, .9f)
                art.flower(canvas, 0f, 40f, false, true, time, MochiGame.FlowerKind.SANCTUARY)
                canvas.restore()
                canvas.translate(0f, -4f + kotlin.math.sin(time * 2) * 3)
                canvas.scale(2.1f, 2.1f)
                art.body(canvas, 0f, 0f, 1f + kotlin.math.sin(time * 2) * .018f, -5f, time,
                    hurt = screen == Screen.END, shield = scene.game.shields > 0, hero = screen == Screen.HOME)
                canvas.restore()
                if (isShown && hasWindowFocus()) postInvalidateDelayed(33)
            }
        }
        menuCard.addView(hero, LinearLayout.LayoutParams(-1, dp(170)))
        heading = label(R.string.mochi_title, 30f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            letterSpacing = -.035f
        }
        explanation = label(R.string.mochi_intro, 14f).apply {
            gravity = Gravity.CENTER
            setTextColor(muted)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(8), 0, dp(12))
        }
        result = label(R.string.mochi_title, 16f).apply {
            gravity = Gravity.CENTER
            background = panel(0xFFE7EBD1.toInt(), 0xFFD2DDB6.toInt(), 16)
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }
        mastery = label(R.string.mochi_mastery_complete, 12f).apply {
            gravity = Gravity.CENTER
            setTextColor(ink)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = panel(0xFFF5E8C2.toInt(), 0xFFE4D194.toInt(), 14)
        }
        mode = SwitchCompat(this).apply {
            setText(R.string.mochi_easy_option)
            textSize = 13f
            setTextColor(ink)
            minHeight = dp(46)
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0xFF358F75.toInt(), 0xFF93A69A.toInt()))
            trackTintList = ColorStateList.valueOf(0xFFCCD8BC.toInt())
            isChecked = selectedEasy
            setOnCheckedChangeListener { _, checked ->
                selectedEasy = checked
                prefs.edit().putBoolean("easy_mode", checked).apply()
                updateModeCopy()
                updateMastery()
                if (!hasRun) { best = bestFor(checked); updateHud() }
            }
        }
        modeCopy = label(R.string.mochi_normal_explanation, 12f).apply {
            setTextColor(muted)
            setPadding(0, 0, 0, dp(12))
        }
        upgradeCards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        primary = button(R.string.mochi_play) {
            if (screen == Screen.PAUSE) resumeGame() else startGame()
        }.apply {
            backgroundTintList = ColorStateList.valueOf(0xFF2E977B.toInt())
            setTextColor(0xFFFFF8DD.toInt())
            strokeColor = ColorStateList.valueOf(0xFF72C9A5.toInt())
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(60)
            elevation = dp(6).toFloat()
        }
        secondary = button(R.string.mochi_same_route) {
            if (screen == Screen.END) startGame(scene.game.seed) else startGame()
        }
        help = button(R.string.mochi_help) {
            showingRules = !showingRules
            explanation.setText(if (showingRules) R.string.mochi_rules else
                if (screen == Screen.HOME) R.string.mochi_intro else R.string.mochi_pause_summary)
        }
        for (child in listOf(heading, explanation, result, mastery, mode, modeCopy, upgradeCards, primary,
            secondary, help, button(R.string.mochi_back) { leaveGame() })) {
            menuCard.addView(child, LinearLayout.LayoutParams(-1, -2))
        }
        frame.addView(menuCard, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        overlay.addView(frame, FrameLayout.LayoutParams(-1, -2))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun panel(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }

    private fun counterText(value: String, size: Int): CharSequence = SpannableString(value).apply {
        val end = value.indexOf('\n').let { if (it < 0) value.length else it }
        setSpan(AbsoluteSizeSpan(size, true), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(StyleSpan(Typeface.BOLD), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    private fun label(text: Int, size: Float) = TextView(this).apply {
        setText(text); textSize = size; setTextColor(ink)
    }

    private fun button(text: Int, action: () -> Unit) = MaterialButton(this).apply {
        setText(text); isAllCaps = false; minimumHeight = dp(48)
        cornerRadius = dp(18)
        backgroundTintList = ColorStateList.valueOf(0xFFE5EAD3.toInt())
        setTextColor(ink)
        rippleColor = ColorStateList.valueOf(0x4472C9A5)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(0xFFD2DAB9.toInt())
        textSize = 13f
        insetTop = dp(4); insetBottom = dp(4)
        setOnClickListener { action() }
    }

    private fun startGame(seed: Long = kotlin.random.Random.nextLong()) {
        settle()
        best = bestFor(selectedEasy)
        bestAtStart = best
        paid = false
        hasRun = true
        screen = Screen.PLAY
        hudPanel.visibility = View.VISIBLE
        overlay.visibility = View.GONE
        pauseButton.visibility = View.VISIBLE
        hint.visibility = View.VISIBLE
        scene.start(seed, selectedEasy)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun resumeGame() {
        if (scene.game.phase == MochiGame.Phase.CHOOSING) { showOverlay(Screen.UPGRADE); return }
        screen = Screen.PLAY
        hudPanel.visibility = View.VISIBLE
        overlay.visibility = View.GONE
        pauseButton.visibility = View.VISIBLE
        hint.visibility = View.VISIBLE
        scene.setDecorating(false)
        scene.setRunning(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showOverlay(next: Screen) {
        screen = next
        showingRules = false
        scene.setRunning(false)
        scene.setDecorating(next != Screen.PAUSE && hasWindowFocus())
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        overlay.visibility = View.VISIBLE
        overlay.scrollTo(0, 0)
        hudPanel.visibility = View.GONE
        menuCard.animate().cancel()
        menuCard.alpha = 0f
        menuCard.translationY = dp(14).toFloat()
        menuCard.animate().alpha(1f).translationY(0f).setDuration(220).start()
        pauseButton.visibility = View.GONE
        hint.visibility = View.GONE
        hero.visibility = if (next == Screen.PAUSE) View.GONE else View.VISIBLE
        hero.invalidate()
        heading.setText(when (next) {
            Screen.PAUSE -> R.string.mochi_paused
            Screen.UPGRADE -> R.string.mochi_rest_title
            Screen.END -> R.string.mochi_finished
            else -> R.string.mochi_title
        })
        explanation.setText(when (next) {
            Screen.HOME -> R.string.mochi_intro
            Screen.UPGRADE -> R.string.mochi_rest_description
            else -> R.string.mochi_pause_summary
        })
        result.visibility = if (next == Screen.END) View.VISIBLE else View.GONE
        mastery.visibility = if (next == Screen.HOME || next == Screen.END || next == Screen.PAUSE) View.VISIBLE else View.GONE
        updateMastery()
        primary.visibility = if (next == Screen.UPGRADE) View.GONE else View.VISIBLE
        primary.setText(when (next) {
            Screen.PAUSE -> R.string.mochi_resume
            Screen.END -> R.string.mochi_new_route
            else -> R.string.mochi_play
        })
        secondary.visibility = if (next == Screen.PAUSE || next == Screen.END) View.VISIBLE else View.GONE
        secondary.setText(if (next == Screen.END) R.string.mochi_same_route else R.string.mochi_new_route)
        mode.visibility = if (next == Screen.HOME || next == Screen.END) View.VISIBLE else View.GONE
        modeCopy.visibility = mode.visibility
        help.visibility = if (next == Screen.UPGRADE || next == Screen.END) View.GONE else View.VISIBLE
        updateModeCopy()
        upgradeCards.removeAllViews()
        upgradeCards.visibility = if (next == Screen.UPGRADE) View.VISIBLE else View.GONE
        if (next == Screen.UPGRADE) {
            explanation.text = getString(R.string.mochi_rest_description,
                scene.game.passiveSlotsUsed, MochiGame.MAX_PASSIVE_SLOTS, scene.game.stepsToRest)
            scene.game.offers.forEach { upgrade ->
                val title = when (upgrade) {
                    MochiGame.Upgrade.BUBBLE -> R.string.mochi_bubble_title
                    MochiGame.Upgrade.MAGNET -> R.string.mochi_magnet_title
                    MochiGame.Upgrade.GRIP -> R.string.mochi_grip_title
                    MochiGame.Upgrade.NECTAR -> R.string.mochi_nectar_title
                    MochiGame.Upgrade.HARVEST -> R.string.mochi_harvest_title
                }
                val description = when (upgrade) {
                    MochiGame.Upgrade.BUBBLE -> getString(R.string.mochi_bubble_description)
                    MochiGame.Upgrade.MAGNET -> getString(R.string.mochi_magnet_description, scene.game.magnet + 1)
                    MochiGame.Upgrade.GRIP -> getString(R.string.mochi_grip_description, scene.game.grip + 1)
                    MochiGame.Upgrade.NECTAR -> getString(R.string.mochi_nectar_description, (scene.game.nectar + 1) * 3)
                    MochiGame.Upgrade.HARVEST -> getString(R.string.mochi_harvest_description, MochiGame.HARVEST_POINTS)
                }
                upgradeCards.addView(button(title) {
                    if (scene.game.chooseUpgrade(upgrade)) { updateHud(); resumeGame() }
                }.apply {
                    text = getString(R.string.mochi_upgrade_card, getString(title), description)
                    val pack = MochiSprites.get(this@MochiActivity)
                    icon = BitmapDrawable(resources, when (upgrade) {
                        MochiGame.Upgrade.BUBBLE -> pack.faces[0]
                        MochiGame.Upgrade.MAGNET -> pack.peach
                        MochiGame.Upgrade.GRIP -> pack.leaves
                        MochiGame.Upgrade.NECTAR, MochiGame.Upgrade.HARVEST -> pack.starfruit
                    })
                    iconTint = null
                    iconSize = dp(43)
                    iconPadding = dp(10)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    textSize = 14f
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
    }

    private fun updateModeCopy() {
        modeCopy.setText(if (selectedEasy) R.string.mochi_easy_explanation else R.string.mochi_normal_explanation)
    }

    private fun updateHud() {
        val g = scene.game
        if (hasRun) best = maxOf(best, g.score)
        val easy = if (hasRun) g.easyMode else selectedEasy
        hud.text = counterText(getString(R.string.mochi_score_card, g.score, best), 26)
        heightChip.text = counterText(getString(R.string.mochi_height_card, g.heightMetres,
            getString(if (easy) R.string.mochi_mode_easy else R.string.mochi_mode_normal)), 22)
        comboBadge.text = if (g.combo > 0) getString(R.string.mochi_combo_badge, g.combo)
            else getString(R.string.mochi_combo_ready)
        comboBadge.visibility = View.VISIBLE
        if (g.score != displayedScore) {
            if (displayedScore >= 0 && g.score > displayedScore) {
                hud.animate().cancel(); hud.scaleX = 1.055f; hud.scaleY = 1.055f
                hud.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            displayedScore = g.score
        }
        loadout.visibility = View.VISIBLE
        loadout.text = if (g.shields + g.grip + g.magnet + g.nectar > 0)
            getString(R.string.mochi_loadout, g.shields, g.magnet, g.grip, g.nectar)
        else getString(R.string.mochi_loadout_empty, g.stepsToRest)
        hint.text = when {
            g.phase == MochiGame.Phase.ATTACHED && g.attachedFlower?.kind == MochiGame.FlowerKind.FRAGILE -> getString(R.string.mochi_hint_fragile)
            g.phase == MochiGame.Phase.ATTACHED && g.attachedFlower?.kind == MochiGame.FlowerKind.SPRING -> getString(R.string.mochi_hint_spring)
            g.phase == MochiGame.Phase.ATTACHED && g.attachedFlower?.kind == MochiGame.FlowerKind.DRIFT -> getString(R.string.mochi_hint_drift)
            g.shots < MochiGame.INTRO_SHOTS -> getString(R.string.mochi_hint_intro)
            g.walls.any { it.top > g.y && it.bottom < g.y + 300f } -> getString(R.string.mochi_hint_geometry)
            else -> getString(R.string.mochi_hint_climb, getString(when (g.biome) {
                1 -> R.string.mochi_biome_sunset
                2 -> R.string.mochi_biome_moon
                else -> R.string.mochi_biome_dawn
            }), g.stepsToRest)
        }
    }

    /** Les anciens records de la V1, guidée en permanence, restent dans le mode facile. */
    private fun bestFor(easy: Boolean) = prefs.getInt(if (easy) "best_easy" else "best_normal",
        if (easy) prefs.getInt("best", 0) else 0)

    private fun saveRecord() {
        if (hasRun) {
            val edit = prefs.edit().putInt(if (scene.game.easyMode) "best_easy" else "best_normal", best)
            val suffix = if (scene.game.easyMode) "easy" else "normal"
            prowess.forEach { goal ->
                val key = "mastery_${suffix}_${goal.key}"
                edit.putInt(key, maxOf(prefs.getInt(key, 0), goal.value(scene.game)))
            }
            edit.apply()
        }
    }

    private fun updateMastery() {
        val easy = if (screen == Screen.HOME) selectedEasy else if (hasRun) scene.game.easyMode else selectedEasy
        val suffix = if (easy) "easy" else "normal"
        fun progress(goal: Prowess): Int = maxOf(prefs.getInt("mastery_${suffix}_${goal.key}", 0),
            if (hasRun && scene.game.easyMode == easy) goal.value(scene.game) else 0)
        val completed = prowess.count { progress(it) >= it.target }
        val next = prowess.firstOrNull { progress(it) < it.target }
        val challenge = if (next == null) getString(R.string.mochi_mastery_complete)
            else getString(R.string.mochi_mastery_next, getString(next.title, next.target), progress(next), next.target)
        mastery.text = getString(R.string.mochi_mastery, completed, prowess.size,
            getString(if (easy) R.string.mochi_mode_easy else R.string.mochi_mode_normal), challenge)
    }

    private fun settle(): Int {
        if (!hasRun) return 0
        best = maxOf(best, scene.game.score)
        saveRecord()
        val reward = NeutrinoRewards.mochi(scene.game.score)
        if (!paid) {
            paid = true
            if (reward > 0) NeutrinoRepository(this).addBalance(reward)
        }
        return reward
    }

    private fun leaveGame() { settle(); finish() }

    override fun onPause() {
        if (::scene.isInitialized) {
            if (screen == Screen.PLAY) showOverlay(Screen.PAUSE)
            scene.setRunning(false)
            scene.setDecorating(false)
            saveRecord()
        }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::scene.isInitialized && screen == Screen.PLAY) showOverlay(Screen.PAUSE)
        if (::scene.isInitialized) {
            scene.setDecorating(hasFocus && screen != Screen.PLAY && screen != Screen.PAUSE)
            if (hasFocus) hero.invalidate()
        }
    }

    override fun onDestroy() {
        if (::scene.isInitialized) { scene.setDecorating(false); scene.setRunning(false) }
        super.onDestroy()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}
