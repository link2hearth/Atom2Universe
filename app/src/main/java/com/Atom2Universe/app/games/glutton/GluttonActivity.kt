package com.Atom2Universe.app.games.glutton

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Family
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Prop
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Shape
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.ceil

class GluttonActivity : ThemedActivity() {
    private enum class Screen { HOME, PLAY, PAUSE, END }
    private var screen = Screen.HOME
    private lateinit var scene: GluttonView
    private lateinit var hud: LinearLayout
    private lateinit var footer: FrameLayout
    private lateinit var overlay: ScrollView
    private lateinit var card: LinearLayout
    private lateinit var heading: TextView
    private lateinit var subtitle: TextView
    private lateinit var result: TextView
    private lateinit var bestLabel: TextView
    private lateinit var score: TextView
    private lateinit var timer: TextView
    private lateinit var combo: TextView
    private lateinit var banner: TextView
    private lateinit var growth: ProgressBar
    private lateinit var charge: ProgressBar
    private lateinit var target: View
    private lateinit var sizeLabel: TextView
    private lateinit var objective: TextView
    private lateinit var treasureLabel: TextView
    private lateinit var chapters: LinearLayout
    private val chapterButtons = ArrayList<MaterialButton>(3)
    private lateinit var hero: View
    private lateinit var primary: MaterialButton
    private lateinit var secondary: MaterialButton
    private lateinit var sameRoom: MaterialButton
    private lateinit var help: MaterialButton
    private lateinit var burst: MaterialButton
    private lateinit var preferences: LinearLayout
    private val prefs by lazy { getSharedPreferences("glutton_save", MODE_PRIVATE) }
    private val names = HashMap<String, String>()
    private var best = 0
    private var hasRun = false
    private var rewardPaid = 0
    private var showingRules = false
    private var displayedScore = -1
    private var displayedSeconds = -1
    private var displayedMultiplier = -1
    private var displayedScene = -1
    private var displayedTreasures = -1
    private var burstReady: Boolean? = null
    private var selectedZone = 0
    private val hideBanner = Runnable { if (::banner.isInitialized) banner.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        best = prefs.getInt("best", 0)
        buildUi(); enableImmersiveMode()
        scene.haptics = prefs.getBoolean("haptics", true)
        scene.sound = prefs.getBoolean("sound", true)
        scene.nameOf = { name(it) }
        scene.onHudChanged = { updateHud() }
        scene.onMilestone = { metres -> flash(getString(R.string.glutton_milestone, sizeText(metres))) }
        scene.onStreak = { family -> STREAKS[family]?.let { flash(getString(it)) } }
        scene.onSceneCleared = { stars ->
            saveProgress()
            flash(getString(R.string.glutton_scene_cleared, getString(ZONES[scene.game.sceneIndex]), "★".repeat(stars) + "☆".repeat(3 - stars)), 2400)
        }
        scene.onSceneChanged = { displayedScene = -1; displayedSeconds = -1; updateHud(); flash(getString(ZONES[scene.game.sceneIndex]), 1800) }
        scene.onFinished = { finishRun() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    Screen.PLAY -> showMenu(Screen.PAUSE)
                    Screen.PAUSE -> resumeGame()
                    else -> leave()
                }
            }
        })
        showMenu(Screen.HOME)
    }

    fun name(prop: Prop): String = names.getOrPut(prop.id) {
        val id = resources.getIdentifier("glutton_item_${prop.id}", "string", packageName)
        if (id == 0) prop.id else getString(id)
    }
    private fun sizeText(metres: Float) = if (metres < 1f) getString(R.string.glutton_size_cm, metres * 100f) else getString(R.string.glutton_size_m, metres)
    private fun flash(text: CharSequence, duration: Long = 1600) {
        banner.text = text; banner.visibility = View.VISIBLE
        banner.removeCallbacks(hideBanner); banner.postDelayed(hideBanner, duration)
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        scene = GluttonView(this)
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))
        hud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
            background = panel(0xF5FFF5E7.toInt(), 0x00FFFFFF, 0)
            elevation = dp(3).toFloat()
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        score = label(R.string.glutton_title, 27f).apply { typeface = Typeface.DEFAULT_BOLD }
        row.addView(score, LinearLayout.LayoutParams(0, dp(48), 1f))
        combo = label(R.string.glutton_title, 18f).apply { setTextColor(0xFF389B85.toInt()); gravity = Gravity.CENTER }
        row.addView(combo, LinearLayout.LayoutParams(dp(48), dp(48)))
        timer = label(R.string.glutton_title, 20f).apply { gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD }
        row.addView(timer, LinearLayout.LayoutParams(dp(72), dp(48)))
        row.addView(button(R.string.glutton_pause) { showMenu(Screen.PAUSE) }.apply { textSize = 12f },
            LinearLayout.LayoutParams(dp(76), dp(48)))
        hud.addView(row)
        val progressRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sizeLabel = label(R.string.glutton_title, 14f).apply { typeface = Typeface.DEFAULT_BOLD }
        progressRow.addView(sizeLabel, LinearLayout.LayoutParams(dp(83), dp(30)))
        growth = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = ColorStateList.valueOf(GluttonArt.MINT)
            progressBackgroundTintList = ColorStateList.valueOf(0xFFE2D8D0.toInt())
        }
        progressRow.addView(growth, LinearLayout.LayoutParams(0, dp(10), 1f))
        target = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                scene.art.item(canvas, scene.game.goal.prop, width / 2f, height / 2f, dp(13).toFloat(), -8f)
            }
        }
        progressRow.addView(target, LinearLayout.LayoutParams(dp(38), dp(30)))
        hud.addView(progressRow)
        val missionRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        objective = label(R.string.glutton_title, 13f).apply { typeface = Typeface.DEFAULT_BOLD }
        missionRow.addView(objective, LinearLayout.LayoutParams(0, dp(25), 1f))
        treasureLabel = label(R.string.glutton_title, 13f).apply { gravity = Gravity.END; setTextColor(0xFFAC803D.toInt()) }
        missionRow.addView(treasureLabel, LinearLayout.LayoutParams(dp(68), dp(25)))
        hud.addView(missionRow)
        root.addView(hud, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        footer = FrameLayout(this).apply { setPadding(dp(18), dp(8), dp(18), dp(12)) }
        val power = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        burst = button(R.string.glutton_burst) { scene.suction() }.apply {
            textSize = 15f; cornerRadius = dp(28)
            contentDescription = getString(R.string.glutton_burst_accessibility)
        }
        power.addView(burst, LinearLayout.LayoutParams(dp(124), dp(56)))
        charge = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = ColorStateList.valueOf(0xFF2E9D92.toInt())
            progressBackgroundTintList = ColorStateList.valueOf(0xFFD6CBC2.toInt())
        }
        power.addView(charge, LinearLayout.LayoutParams(dp(100), dp(6)).apply { gravity = Gravity.CENTER; topMargin = dp(2) })
        footer.addView(power, FrameLayout.LayoutParams(-2, -2, Gravity.END))
        root.addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        banner = label(R.string.glutton_title, 21f).apply {
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = panel(0xF5FFF5E7.toInt(), GluttonArt.GOLD, 20)
            setPadding(dp(18), dp(8), dp(18), dp(8)); visibility = View.GONE
        }
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(135) })
        overlay = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(0xCDEADCD8.toInt()); isClickable = true }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        card = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val bounded = MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(widthMeasureSpec), dp(390)), MeasureSpec.EXACTLY)
                super.onMeasure(bounded, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(16), dp(22), dp(18))
            background = panel(0xFFFFF7EA.toInt(), 0xFFE5C4AA.toInt(), 30); elevation = dp(5).toFloat()
        }
        hero = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                val scale = minOf(width / 280f, height / 156f)
                canvas.save(); canvas.translate(width / 2f, height / 2f); canvas.scale(scale, scale)
                scene.art.shape(canvas, Shape.DONUT, -87f, 24f, 24f, -15f)
                scene.art.shape(canvas, Shape.SOFA, 92f, 25f, 36f, 12f)
                scene.art.shape(canvas, Shape.CAT, -95f, -32f, 18f, 10f)
                scene.art.shape(canvas, Shape.HOUSE, 96f, -36f, 20f, -6f)
                scene.art.body(canvas, 0f, 1f, 57f, .6f, hungry = screen == Screen.END)
                scene.art.shape(canvas, Shape.COOKIE, -34f, 36f, 13f, 20f)
                scene.art.shape(canvas, Shape.CHAIR, 36f, -30f, 14f, -20f)
                canvas.restore()
            }
        }
        card.addView(hero, LinearLayout.LayoutParams(-1, dp(148)))
        heading = label(R.string.glutton_title, 29f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        card.addView(heading, LinearLayout.LayoutParams(-1, -2))
        subtitle = label(R.string.glutton_intro, 15f).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(12)); setLineSpacing(dp(2).toFloat(), 1f)
        }
        card.addView(subtitle)
        result = label(R.string.glutton_title, 18f).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(12)) }
        card.addView(result)
        bestLabel = label(R.string.glutton_title, 13f).apply { setTextColor(0xFF8C7483.toInt()); setPadding(0, 0, 0, dp(8)) }
        card.addView(bestLabel)
        chapters = LinearLayout(this).apply { gravity = Gravity.CENTER }
        for (zone in 0..2) {
            val chapter = button(ZONES[zone]) { selectedZone = zone; updateChapters() }.apply {
                textSize = 12f; setPadding(dp(4), 0, dp(4), 0)
            }
            chapterButtons += chapter
            chapters.addView(chapter, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        card.addView(chapters, LinearLayout.LayoutParams(-1, -2))
        primary = button(R.string.glutton_play) { if (screen == Screen.PAUSE) resumeGame() else startGame(zone = selectedZone) }.apply {
            backgroundTintList = ColorStateList.valueOf(GluttonArt.MINT)
        }
        card.addView(primary, LinearLayout.LayoutParams(-1, dp(56)))
        sameRoom = button(R.string.glutton_same) { startGame(scene.game.seed, scene.game.sceneIndex) }
        card.addView(sameRoom, LinearLayout.LayoutParams(-1, dp(48)))
        secondary = button(R.string.glutton_again) { startGame(zone = scene.game.sceneIndex) }
        card.addView(secondary, LinearLayout.LayoutParams(-1, dp(48)))
        help = button(R.string.glutton_help) {
            showingRules = !showingRules
            subtitle.setText(if (showingRules) R.string.glutton_rules else R.string.glutton_intro)
        }
        card.addView(help, LinearLayout.LayoutParams(-1, dp(48)))
        preferences = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        preferences.addView(SwitchMaterial(this).apply {
            setText(R.string.glutton_haptics); setTextColor(GluttonArt.INK)
            isChecked = prefs.getBoolean("haptics", true); minHeight = dp(48)
            setOnCheckedChangeListener { _, checked ->
                scene.haptics = checked; prefs.edit().putBoolean("haptics", checked).apply()
            }
        }, LinearLayout.LayoutParams(-1, -2))
        preferences.addView(SwitchMaterial(this).apply {
            setText(R.string.glutton_sound); setTextColor(GluttonArt.INK)
            isChecked = prefs.getBoolean("sound", true); minHeight = dp(48)
            setOnCheckedChangeListener { _, checked ->
                scene.sound = checked; prefs.edit().putBoolean("sound", checked).apply()
            }
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(preferences, LinearLayout.LayoutParams(-1, -2))
        card.addView(button(R.string.glutton_exit) { leave() }, LinearLayout.LayoutParams(-1, dp(48)))
        center.addView(card, LinearLayout.LayoutParams(-1, -2))
        overlay.addView(center)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        hud.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }
        footer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewport() }
    }

    private fun updateViewport() { scene.setViewportInsets(hud.height, footer.height) }

    private fun startGame(seed: Long = kotlin.random.Random.nextLong(), zone: Int = 0) {
        settle(); hasRun = true; rewardPaid = 0
        displayedScore = -1; displayedSeconds = -1; displayedScene = -1; displayedMultiplier = -1; displayedTreasures = -1; burstReady = null
        banner.removeCallbacks(hideBanner); banner.visibility = View.GONE
        screen = Screen.PLAY; overlay.visibility = View.GONE; hud.visibility = View.VISIBLE; footer.visibility = View.VISIBLE
        scene.start(seed, zone); keepAwake(true)
    }

    private fun resumeGame() {
        if (scene.game.finished) return
        screen = Screen.PLAY; overlay.visibility = View.GONE; hud.visibility = View.VISIBLE; footer.visibility = View.VISIBLE
        scene.setRunning(true); keepAwake(true)
    }

    private fun showMenu(next: Screen) {
        if (next == Screen.PAUSE && scene.game.finished) { finishRun(); return }
        screen = next; showingRules = false
        scene.setRunning(false); keepAwake(false)
        banner.removeCallbacks(hideBanner); banner.visibility = View.GONE
        hud.visibility = View.INVISIBLE; footer.visibility = View.INVISIBLE; overlay.visibility = View.VISIBLE
        overlay.scrollTo(0, 0)
        hero.visibility = if (next == Screen.PAUSE) View.GONE else View.VISIBLE
        hero.invalidate()
        heading.setText(when (next) {
            Screen.PAUSE -> R.string.glutton_paused
            Screen.END -> R.string.glutton_end
            else -> R.string.glutton_title
        })
        subtitle.setText(R.string.glutton_intro)
        subtitle.visibility = if (next == Screen.END) View.GONE else View.VISIBLE
        primary.setText(when (next) {
            Screen.PAUSE -> R.string.glutton_resume
            Screen.END -> R.string.glutton_again
            else -> R.string.glutton_play
        })
        secondary.visibility = if (next == Screen.PAUSE) View.VISIBLE else View.GONE
        sameRoom.visibility = if (next == Screen.END) View.VISIBLE else View.GONE
        result.visibility = if (next == Screen.END) View.VISIBLE else View.GONE
        help.visibility = if (next == Screen.END) View.GONE else View.VISIBLE
        chapters.visibility = if (next == Screen.HOME || next == Screen.END) View.VISIBLE else View.GONE
        updateChapters()
    }

    private fun updateHud() {
        val game = scene.game
        if (game.score != displayedScore) {
            displayedScore = game.score; score.text = getString(R.string.glutton_score, game.score)
        }
        val seconds = ceil(game.remaining.toDouble()).toInt()
        if (seconds != displayedSeconds) {
            displayedSeconds = seconds; timer.text = getString(R.string.glutton_timer, seconds / 60, seconds % 60)
            timer.setTextColor(if (seconds <= 10) 0xFFBD556C.toInt() else GluttonArt.INK)
        }
        if (game.multiplier != displayedMultiplier) {
            displayedMultiplier = game.multiplier
            combo.text = getString(R.string.glutton_combo, game.multiplier)
            combo.visibility = if (game.multiplier > 1) View.VISIBLE else View.INVISIBLE
        }
        growth.progress = (game.progress * 1000).toInt()
        sizeLabel.text = sizeText(game.diameterMetres)
        if (game.sceneIndex != displayedScene) {
            displayedScene = game.sceneIndex; target.invalidate()
            objective.text = getString(R.string.glutton_goal, name(game.goal.prop), sizeText(game.targetMetres))
            growth.contentDescription = getString(R.string.glutton_growth_accessibility, name(game.goal.prop), sizeText(game.targetMetres))
        }
        if (game.treasures != displayedTreasures) {
            displayedTreasures = game.treasures; treasureLabel.text = getString(R.string.glutton_treasure_counter, game.treasures)
        }
        charge.progress = (game.charge * 1000).toInt()
        val ready = game.charge >= .999f && game.burst <= 0f
        if (ready != burstReady) {
            burstReady = ready
            burst.setText(if (ready) R.string.glutton_burst else R.string.glutton_burst_wait)
            burst.backgroundTintList = ColorStateList.valueOf(if (ready) GluttonArt.MINT else 0xFFD5DECF.toInt())
        }
    }

    private fun settle(): Int {
        if (!hasRun) return 0
        best = maxOf(best, scene.game.score); prefs.edit().putInt("best", best).apply()
        val reward = NeutrinoRewards.glutton(scene.game.score)
        val difference = reward - rewardPaid
        if (difference > 0) { NeutrinoRepository(this).addBalance(difference); rewardPaid = reward }
        return reward
    }

    private fun finishRun() {
        val reward = settle(); saveProgress(); selectedZone = scene.game.sceneIndex; showMenu(Screen.END)
        heading.setText(if (scene.game.won) R.string.glutton_victory else R.string.glutton_time_up)
        result.text = getString(R.string.glutton_expedition_result, scene.game.score, sizeText(scene.game.diameterMetres),
            scene.game.eaten, scene.game.medals.sum(), reward)
    }

    private fun saveProgress() {
        val editor = prefs.edit()
        var unlocked = prefs.getInt("unlocked", 0)
        for (zone in 0..2) if (scene.game.medals[zone] > 0) {
            unlocked = maxOf(unlocked, (zone + 1).coerceAtMost(2))
            editor.putInt("stars_$zone", maxOf(prefs.getInt("stars_$zone", 0), scene.game.medals[zone]))
            editor.putFloat("time_$zone", minOf(prefs.getFloat("time_$zone", Float.MAX_VALUE), scene.game.completionTimes[zone]))
        }
        editor.putInt("unlocked", unlocked).apply()
    }

    private fun updateChapters() {
        val unlocked = prefs.getInt("unlocked", 0).coerceIn(0, 2)
        selectedZone = selectedZone.coerceIn(0, unlocked)
        for ((zone, button) in chapterButtons.withIndex()) {
            button.isEnabled = zone <= unlocked
            button.text = if (zone <= unlocked) getString(ZONES[zone]) else getString(R.string.glutton_locked_chapter, getString(ZONES[zone]))
            button.backgroundTintList = ColorStateList.valueOf(if (zone == selectedZone) GluttonArt.MINT else 0xFFE5E6D9.toInt())
        }
        bestLabel.text = getString(R.string.glutton_chapter_record, best, prefs.getInt("stars_$selectedZone", 0))
        val time = prefs.getFloat("time_$selectedZone", Float.MAX_VALUE)
        if (time < Float.MAX_VALUE) bestLabel.text = getString(R.string.glutton_best_time, bestLabel.text, time.toInt())
    }

    private fun leave() { settle(); finish() }
    private fun keepAwake(value: Boolean) {
        if (value) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        if (::scene.isInitialized) {
            if (screen == Screen.PLAY) showMenu(Screen.PAUSE)
            scene.setRunning(false)
            settle()
        }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::scene.isInitialized && screen == Screen.PLAY) showMenu(Screen.PAUSE)
    }

    override fun onDestroy() {
        if (::scene.isInitialized) { scene.setRunning(false); banner.removeCallbacks(hideBanner) }
        super.onDestroy()
    }

    private fun label(text: Int, size: Float) = TextView(this).apply { setText(text); textSize = size; setTextColor(GluttonArt.INK) }
    private fun panel(color: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }
    private fun button(text: Int, action: () -> Unit) = MaterialButton(this).apply {
        setText(text); isAllCaps = false; textSize = 14f; minimumHeight = dp(48); cornerRadius = dp(18)
        backgroundTintList = ColorStateList.valueOf(0xFFDCE8D9.toInt()); setTextColor(GluttonArt.INK)
        rippleColor = ColorStateList.valueOf(0x44FFFFFF); insetTop = dp(3); insetBottom = dp(3)
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    companion object {
        val ZONES = intArrayOf(R.string.glutton_zone_room, R.string.glutton_zone_garden, R.string.glutton_zone_city)
        val STREAKS = mapOf(
            Family.FOOD to R.string.glutton_streak_food, Family.TOY to R.string.glutton_streak_toy,
            Family.HOME to R.string.glutton_streak_home, Family.PLANT to R.string.glutton_streak_plant,
            Family.ANIMAL to R.string.glutton_streak_animal, Family.GARDEN to R.string.glutton_streak_garden,
            Family.VEHICLE to R.string.glutton_streak_vehicle, Family.STREET to R.string.glutton_streak_street,
            Family.BUILDING to R.string.glutton_streak_building)
    }
}
