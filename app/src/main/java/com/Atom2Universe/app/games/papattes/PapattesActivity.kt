package com.Atom2Universe.app.games.papattes

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import com.google.android.material.button.MaterialButton
import kotlin.random.Random

class PapattesActivity : ThemedActivity() {
    private enum class Screen { HOME, PLAY, PAUSE, END, LAB, HATCH }
    private var screen = Screen.HOME

    private val prefs by lazy { getSharedPreferences("papattes_save", MODE_PRIVATE) }
    private lateinit var progress: PapattesProgress
    private var stage = 1
    private val sprites by lazy { PapattesSprites.get(this) }
    /** Les couleurs du thème de l'appli : fond, surfaces, texte et accent. */
    private lateinit var palette: KitPalette

    private lateinit var scene: PapattesView
    private lateinit var chrome: LinearLayout
    private lateinit var titleLabel: TextView
    private lateinit var seedChip: TextView
    private lateinit var pauseButton: MaterialButton
    private lateinit var overlay: ScrollView
    private lateinit var card: LinearLayout
    private lateinit var hero: PapattesPortraitView
    private lateinit var heading: TextView
    private lateinit var stageRow: LinearLayout
    private lateinit var stageLabel: TextView
    private lateinit var result: TextView
    private lateinit var wildLabel: TextView
    private lateinit var primary: MaterialButton
    private lateinit var secondary: MaterialButton
    private lateinit var tertiary: MaterialButton
    private lateinit var lab: LinearLayout
    private lateinit var labScroll: ScrollView
    private lateinit var labCounts: TextView
    private lateinit var penGrid: GridLayout
    private lateinit var filterBox: LinearLayout
    private lateinit var eggIcon: ImageView
    private lateinit var eggLabel: TextView
    private lateinit var eggButton: MaterialButton
    private lateinit var selectionLabel: TextView
    private lateinit var hatchLayer: FrameLayout
    private lateinit var hatchView: PapattesHatchView
    private lateinit var hatchName: TextView
    private lateinit var hatchButton: MaterialButton
    /** Filtres du carnet : on ne montre que les bestioles qui ont ces pièces. */
    private var filterHead: Head? = null
    private var filterBody: Body? = null
    private var filterLegs: Legs? = null
    private lateinit var walkWith: MaterialButton
    private lateinit var breed: MaterialButton
    private lateinit var release: MaterialButton
    private val selected = ArrayList<Int>()
    /** Vignettes du carnet, décodées à la première demande. */
    private val thumbnails = HashMap<String, android.graphics.Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = KitPalette.from(this)
        progress = PapattesProgress.decode(prefs.getString(KEY_PROGRESS, null))
        stage = prefs.getInt(KEY_STAGE, 1).coerceIn(1, progress.cleared + 1)
        buildUi()
        enableImmersiveMode()
        scene.onFinished = { finishStage() }
        scene.onChanged = { updateSeeds() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    Screen.PLAY -> show(Screen.PAUSE)
                    Screen.PAUSE -> resume()
                    Screen.LAB, Screen.END -> show(Screen.HOME)
                    Screen.HATCH -> show(Screen.LAB)
                    Screen.HOME -> finish()
                }
            }
        })
        show(Screen.HOME)
    }

    // ── Interface ───────────────────────────────────────────────────────────────────────────────

    private fun buildUi() {
        val root = FrameLayout(this)
        scene = PapattesView(this)
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))

        chrome = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(palette.withAlpha(palette.background, .94f), palette.withAlpha(palette.background, 0f)))
        }
        chrome.addView(iconButton(R.drawable.ic_app_back, R.string.papattes_back, palette.text) { onBackPressedDispatcher.onBackPressed() },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        titleLabel = label("", 18f).apply {
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(4), 0, dp(4), 0)
        }
        chrome.addView(titleLabel, LinearLayout.LayoutParams(0, -2, 1f))
        seedChip = label("", 14f).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(12), dp(4))
            background = shape(palette.raised, 18f, palette.outline)
            setCompoundDrawables(icon(sprites.seed, 20), null, null, null)
            compoundDrawablePadding = dp(6)
        }
        chrome.addView(seedChip, LinearLayout.LayoutParams(-2, dp(38)))
        pauseButton = button(R.string.papattes_pause) { show(Screen.PAUSE) }
        chrome.addView(pauseButton, LinearLayout.LayoutParams(-2, dp(48)).apply { leftMargin = dp(6) })
        chrome.addView(iconButton(R.drawable.ic_info, R.string.kit_rules, palette.accent) { showRules() },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(2) })
        chrome.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scene.setViewportTop(chrome.bottom) }

        overlay = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(64), 0, dp(12))
            setBackgroundColor(palette.withAlpha(palette.background, .55f))
            isClickable = true
        }
        val frame = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), dp(12)) }
        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(18))
            background = shape(palette.surface, 24f, palette.outline)
            elevation = AppearanceStyle.elevation(this@PapattesActivity, 8f)
        }
        hero = PapattesPortraitView(this).apply {
            background = shape(palette.blend(palette.surface, palette.accent, if (palette.isLight) .10f else .16f), 20f, null)
        }
        card.addView(hero, LinearLayout.LayoutParams(-1, dp(180)))
        heading = label("", 26f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, 0)
        }
        card.addView(heading, LinearLayout.LayoutParams(-1, -2))
        wildLabel = label("", 15f, palette.ink).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(wildLabel, LinearLayout.LayoutParams(-1, -2))
        stageRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        stageRow.addView(roundButton(R.drawable.ic_chevron_left, R.string.papattes_previous_stage) { pickStage(stage - 1) },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        stageLabel = label("", 16f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        stageRow.addView(stageLabel, LinearLayout.LayoutParams(0, -2, 1f))
        stageRow.addView(roundButton(R.drawable.ic_chevron_right, R.string.papattes_next_stage) { pickStage(stage + 1) },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        card.addView(stageRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(6) })
        result = label("", 15f).apply {
            gravity = Gravity.CENTER
            background = shape(palette.raised, 16f, palette.outline)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        card.addView(result, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(6) })
        primary = button(R.string.papattes_play, primary = true) { onPrimary() }.apply {
            textSize = 18f
            minimumHeight = dp(58)
        }
        secondary = button(R.string.papattes_lab) { onSecondary() }
        tertiary = button(R.string.papattes_menu) { onTertiary() }
        for (b in listOf(primary, secondary, tertiary)) card.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        frame.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        overlay.addView(frame, FrameLayout.LayoutParams(-1, -2))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        lab = buildLab()
        root.addView(lab, FrameLayout.LayoutParams(-1, -1))
        hatchLayer = buildHatch()
        root.addView(hatchLayer, FrameLayout.LayoutParams(-1, -1))
        // La barre du haut (retour) reste au-dessus du menu et du Labo.
        root.addView(chrome, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)
    }

    /**
     * Le Labo : l'œuf, le carnet qui trie et l'enclos défilent ; la bestiole choisie et les
     * actions restent en bas, toujours sous le doigt.
     */
    private fun buildLab(): LinearLayout {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
            setPadding(0, dp(64), 0, 0)
            isClickable = true
        }
        labScroll = ScrollView(this).apply {
            clipToPadding = false
            setPadding(dp(14), dp(4), dp(14), dp(16))
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labCounts = label("", 13f, palette.secondary).apply { gravity = Gravity.CENTER }
        column.addView(labCounts, LinearLayout.LayoutParams(-1, -2))
        // L'œuf mystère : à acheter, en couveuse, ou prêt à éclore.
        val eggRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = shape(palette.surface, 18f, palette.outline)
        }
        eggIcon = ImageView(this).apply { setImageBitmap(sprites.egg.bitmap); scaleType = ImageView.ScaleType.FIT_CENTER }
        eggRow.addView(eggIcon, LinearLayout.LayoutParams(dp(36), dp(44)))
        eggLabel = label("", 15f).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(dp(10), 0, dp(6), 0) }
        eggRow.addView(eggLabel, LinearLayout.LayoutParams(0, -2, 1f))
        eggButton = button(R.string.papattes_hatch, primary = true) { onEggButton() }
        eggRow.addView(eggButton, LinearLayout.LayoutParams(-2, -2))
        column.addView(eggRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        column.addView(label(R.string.papattes_book, 17f).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(14), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        filterBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(filterBox, LinearLayout.LayoutParams(-1, -2))
        penGrid = GridLayout(this)
        column.addView(penGrid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        labScroll.addView(column, FrameLayout.LayoutParams(-1, -2))
        page.addView(labScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = shape(palette.surface, 0f, palette.outline)
            elevation = AppearanceStyle.elevation(this@PapattesActivity, 6f)
        }
        selectionLabel = label("", 13f).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(1).toFloat(), 1f)
            setPadding(0, 0, 0, dp(4))
        }
        bar.addView(selectionLabel, LinearLayout.LayoutParams(-1, -2))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        walkWith = button(R.string.papattes_walk_with) { walkWithSelected() }
        breed = button(R.string.papattes_lab, primary = true) { breedSelected() }
        release = button(R.string.papattes_release) { releaseSelected() }
        for (b in listOf(walkWith, breed, release)) actions.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = dp(3); rightMargin = dp(3)
        })
        bar.addView(actions, LinearLayout.LayoutParams(-1, -2))
        page.addView(bar, LinearLayout.LayoutParams(-1, -2))
        return page
    }

    /** L'écran d'éclosion : l'œuf au milieu, puis le nom de la bestiole et le retour au Labo. */
    private fun buildHatch(): FrameLayout {
        val layer = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(palette.background, palette.blend(palette.background, palette.accent, if (palette.isLight) .12f else .18f)))
            isClickable = true
        }
        hatchView = PapattesHatchView(this)
        layer.addView(hatchView, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(64) })
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(8), dp(24), dp(24))
        }
        hatchName = label("", 20f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        bottom.addView(hatchName, LinearLayout.LayoutParams(-1, -2))
        hatchButton = button(R.string.papattes_to_lab, primary = true) { show(Screen.LAB); celebrateNewest() }.apply { textSize = 17f }
        bottom.addView(hatchButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        layer.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        hatchView.onDone = {
            val critter = progress.hatch()
            if (critter != null) {
                save()
                clearFilters()
                selected.clear()
                selected += progress.pen.lastIndex
                hatchName.text = fullName(critter)
                hatchName.visibility = View.VISIBLE
                hatchButton.visibility = View.VISIBLE
                updateSeeds()
            }
        }
        return layer
    }

    // ── Écrans ──────────────────────────────────────────────────────────────────────────────────

    private fun show(next: Screen) {
        screen = next
        val playing = next == Screen.PLAY
        scene.setRunning(playing)
        if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        overlay.visibility = if (next == Screen.HOME || next == Screen.PAUSE || next == Screen.END) View.VISIBLE else View.GONE
        lab.visibility = if (next == Screen.LAB) View.VISIBLE else View.GONE
        hatchLayer.visibility = if (next == Screen.HATCH) View.VISIBLE else View.GONE
        pauseButton.visibility = if (playing) View.VISIBLE else View.GONE
        titleLabel.text = when (next) {
            Screen.PLAY, Screen.PAUSE -> getString(R.string.papattes_stage, stage)
            Screen.LAB -> getString(R.string.papattes_lab)
            Screen.HATCH -> getString(R.string.papattes_hatch_title)
            else -> ""
        }
        updateSeeds()
        hero.animated = next == Screen.HOME
        when (next) {
            Screen.HOME -> {
                scene.prepare(stage, progress.activeCritter)
                hero.critter = progress.activeCritter
                hero.visibility = View.VISIBLE
                heading.setText(R.string.papattes_title)
                wildLabel.visibility = View.GONE
                stageRow.visibility = View.VISIBLE
                updateStageLabel()
                result.visibility = View.GONE
                primary.setText(R.string.papattes_play)
                secondary.setText(R.string.papattes_lab)
                tertiary.setText(R.string.papattes_back)
            }
            Screen.PAUSE -> {
                hero.visibility = View.GONE
                heading.setText(R.string.papattes_paused)
                wildLabel.visibility = View.GONE
                stageRow.visibility = View.GONE
                result.visibility = View.GONE
                primary.setText(R.string.papattes_resume)
                secondary.setText(R.string.papattes_restart)
                tertiary.setText(R.string.papattes_menu)
            }
            Screen.END -> {
                heading.setText(R.string.papattes_finished)
                stageRow.visibility = View.GONE
                result.visibility = View.VISIBLE
                primary.setText(R.string.papattes_next_stage)
                secondary.setText(R.string.papattes_lab)
                tertiary.setText(R.string.papattes_menu)
            }
            Screen.LAB -> refreshLab()
            Screen.PLAY, Screen.HATCH -> Unit
        }
        if (overlay.visibility == View.VISIBLE) {
            overlay.scrollTo(0, 0)
            card.alpha = 0f
            card.translationY = dp(14).toFloat()
            card.animate().alpha(1f).translationY(0f).setDuration(220).start()
        }
    }

    private fun showRules() {
        if (screen == Screen.PLAY) show(Screen.PAUSE)
        showGameRules(R.string.papattes_title, R.string.papattes_rules)
    }

    private fun onPrimary() = when (screen) {
        Screen.HOME, Screen.END -> startStage(stage)
        Screen.PAUSE -> resume()
        else -> Unit
    }

    private fun onSecondary() = when (screen) {
        Screen.PAUSE -> startStage(stage)
        else -> show(Screen.LAB)
    }

    private fun onTertiary() = if (screen == Screen.HOME) finish() else show(Screen.HOME)

    private fun startStage(number: Int) {
        stage = number.coerceIn(1, progress.cleared + 1)
        prefs.edit().putInt(KEY_STAGE, stage).apply()
        scene.prepare(stage, progress.activeCritter)
        show(Screen.PLAY)
    }

    private fun resume() = show(Screen.PLAY)

    private fun pickStage(number: Int) {
        val next = number.coerceIn(1, progress.cleared + 1)
        if (next == stage) return
        stage = next
        prefs.edit().putInt(KEY_STAGE, stage).apply()
        updateStageLabel()
        scene.prepare(stage, progress.activeCritter)
    }

    private fun updateStageLabel() {
        val biome = when (PapattesCourse(stage).biome) {
            Biome.MEADOW -> R.string.papattes_biome_meadow
            Biome.MARSH -> R.string.papattes_biome_marsh
            Biome.ICE -> R.string.papattes_biome_ice
        }
        stageLabel.text = getString(R.string.papattes_stage_line, getString(R.string.papattes_stage, stage), getString(biome)) +
            "\n" + starLine(progress.starsOf(stage))
    }

    /** Les trois étoiles d'une étape : arrivée, toutes les graines d'or, aucune chute ; pleines ou vides. */
    private fun starLine(bits: Int): String = listOf(PapattesGame.STAR_FINISH, PapattesGame.STAR_GOLD, PapattesGame.STAR_CLEAN)
        .joinToString(" ") { getString(if (bits and it != 0) R.string.papattes_star_full else R.string.papattes_star_empty) }

    private fun finishStage() {
        val game = scene.game ?: return
        val reward = progress.clearStage(stage, game.stars, game.goldCount)
        val neutrinos = NeutrinoRewards.papattes(stage, reward.firstTime)
        if (neutrinos > 0) NeutrinoRepository(this).addBalance(neutrinos)
        // L'étape suivante devient celle du menu : « C'est parti » ne relance pas celle qu'on vient de finir.
        stage = (stage + 1).coerceIn(1, progress.cleared + 1)
        prefs.edit().putInt(KEY_STAGE, stage).apply()
        save()
        val seconds = game.elapsed.toInt()
        result.text = starLine(game.stars) + "\n" +
            getString(R.string.papattes_result, game.falls, seconds / 60, seconds % 60) + "\n" +
            getString(R.string.papattes_gold, reward.gold, game.course.goldSeeds.size) + "\n" +
            getString(R.string.papattes_reward, reward.seeds, neutrinos)
        hero.critter = progress.activeCritter
        hero.visibility = View.VISIBLE
        wildLabel.setText(R.string.papattes_egg_ready)
        wildLabel.visibility = if (reward.eggReady) View.VISIBLE else View.GONE
        show(Screen.END)
    }

    /** Hors jeu, la réserve de graines ; en marche, les graines d'or ramassées sur l'étape. */
    private fun updateSeeds() {
        val game = scene.game
        seedChip.text = if (screen == Screen.PLAY && game != null)
            getString(R.string.papattes_gold_short, game.goldCount, game.course.goldSeeds.size)
        else getString(R.string.papattes_count, progress.seeds)
    }

    private fun save() {
        prefs.edit().putString(KEY_PROGRESS, progress.encode()).apply()
    }

    // ── Labo ────────────────────────────────────────────────────────────────────────────────────

    private fun refreshLab() {
        updateSeeds()
        labCounts.text = getString(R.string.papattes_book_counts, progress.discoveredCount, Parts.COUNT,
            progress.seenCount, Critter.COUNT) + "\n" + getString(R.string.papattes_pen, progress.pen.size, PapattesProgress.PEN_SIZE)
        refreshEgg()
        refreshFilters()
        val shown = progress.pen.indices.filter { matches(progress.pen[it]) }
        selected.removeAll { it !in shown }
        penGrid.removeAllViews()
        // Petites tuiles : trois par rangée sur téléphone, davantage sur tablette.
        val metrics = resources.displayMetrics
        val columns = (metrics.widthPixels / metrics.density / CARD_DP).toInt().coerceIn(3, 6)
        penGrid.columnCount = columns
        val cell = (metrics.widthPixels - dp(28)) / columns - dp(6)
        for (i in shown) penGrid.addView(critterCard(i, progress.pen[i], cell), GridLayout.LayoutParams().apply {
            width = cell
            setMargins(dp(3), dp(3), dp(3), dp(3))
        })
        updateLabButtons()
    }

    private fun matches(c: Critter) = (filterHead == null || c.head == filterHead) &&
        (filterBody == null || c.body == filterBody) && (filterLegs == null || c.legs == filterLegs)

    private fun clearFilters() { filterHead = null; filterBody = null; filterLegs = null }

    /**
     * Le carnet, en trois rangées qui défilent : têtes, corps, jambes. Seules les pièces connues y
     * figurent ; toucher une pièce ne garde dans l'enclos que les bestioles qui l'ont, la retoucher
     * enlève le filtre.
     */
    private fun refreshFilters() {
        filterBox.removeAllViews()
        fun row(title: Int, total: Int, entries: List<Triple<String, Boolean, () -> Unit>>) {
            filterBox.addView(label(getString(R.string.papattes_book_row, getString(title), entries.size, total), 12f, palette.secondary).apply {
                setPadding(dp(2), dp(8), 0, dp(3))
            }, LinearLayout.LayoutParams(-1, -2))
            val strip = LinearLayout(this)
            for ((name, chosen, action) in entries) {
                strip.addView(ImageView(this).apply {
                    setImageBitmap(thumbnails.getOrPut(name) { sprites.thumbnail(name) })
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    background = if (chosen) shape(palette.cellHighlight, 12f, palette.accent, 2)
                        else shape(palette.raised, 12f, palette.outline)
                    setOnClickListener { action(); refreshLab() }
                }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(6) })
            }
            filterBox.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(strip)
            }, LinearLayout.LayoutParams(-1, -2))
        }
        row(R.string.papattes_heads, Head.entries.size, Head.entries.filter { progress.isDiscovered(Parts.index(it)) }.map { h ->
            Triple("head_${h.id}", filterHead == h) { filterHead = if (filterHead == h) null else h }
        })
        row(R.string.papattes_bodies, Body.entries.size, Body.entries.filter { progress.isDiscovered(Parts.index(it)) }.map { b ->
            Triple("body_${b.id}", filterBody == b) { filterBody = if (filterBody == b) null else b }
        })
        row(R.string.papattes_legs, Legs.entries.size, Legs.entries.filter { progress.isDiscovered(Parts.index(it)) }.map { l ->
            Triple("shin_${l.id}", filterLegs == l) { filterLegs = if (filterLegs == l) null else l }
        })
    }

    /** Le panneau de l'œuf mystère, selon où il en est. */
    private fun refreshEgg() {
        val egg = progress.egg
        eggIcon.alpha = if (egg == null) .55f else 1f
        when {
            egg == null -> {
                eggLabel.setText(R.string.papattes_egg_mystery)
                eggButton.text = getString(R.string.papattes_egg_buy, PapattesProgress.EGG_COST)
                eggButton.visibility = View.VISIBLE
                eggButton.isEnabled = progress.canBuyEgg
            }
            !progress.eggReady -> {
                eggLabel.setText(R.string.papattes_egg_incubating)
                eggButton.visibility = View.GONE
            }
            else -> {
                eggLabel.setText(if (progress.penFull) R.string.papattes_pen_full else R.string.papattes_egg_ready_short)
                eggButton.setText(R.string.papattes_hatch)
                eggButton.visibility = View.VISIBLE
                eggButton.isEnabled = progress.canHatch
            }
        }
        eggButton.alpha = if (eggButton.isEnabled) 1f else .45f
        // L'œuf prêt se dandine pour attirer l'œil.
        eggIcon.animate().cancel()
        eggIcon.rotation = 0f
        if (egg != null && progress.eggReady) eggIcon.animate().rotation(10f).setDuration(260).withEndAction {
            eggIcon.animate().rotation(-10f).setDuration(260).withEndAction { eggIcon.animate().rotation(0f).setDuration(200).start() }.start()
        }.start()
    }

    private fun onEggButton() {
        val egg = progress.egg
        when {
            egg == null -> {
                if (!progress.buyEgg(Random.Default)) return
                save()
                refreshLab()
            }
            progress.canHatch -> {
                hatchView.critter = egg
                hatchName.visibility = View.INVISIBLE
                hatchButton.visibility = View.INVISIBLE
                show(Screen.HATCH)
            }
        }
    }

    /** Une petite tuile d'enclos : la bestiole, ses jambes, et son pouvoir s'il en a un. */
    private fun critterCard(index: Int, critter: Critter, cell: Int): View {
        val active = index == progress.active
        val chosen = index in selected
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(6))
            background = when {
                chosen -> shape(palette.cellHighlight, 14f, palette.accent, 3)
                active -> shape(palette.surface, 14f, palette.ink, 2)
                else -> shape(palette.surface, 14f, palette.outline)
            }
            addView(PapattesPortraitView(this@PapattesActivity).apply { this.critter = critter },
                LinearLayout.LayoutParams(-1, (cell * .9f).toInt()))
            addView(label(critter.legs.nameRes, 11f).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2))
            if (critter.body.power != Power.NONE) addView(label(critter.body.power.nameRes, 10f, palette.ink).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, -2))
            setOnClickListener { toggle(index) }
        }
    }

    private fun toggle(index: Int) {
        if (index in selected) selected.remove(index) else {
            selected += index
            if (selected.size > 2) selected.removeAt(0)
        }
        refreshLab()
    }

    private fun fullName(c: Critter) = getString(R.string.papattes_critter_name, getString(c.head.nameRes),
        getString(c.body.nameRes), getString(c.legs.nameRes))

    /** Ce que fait une bestiole : démarche et trait des jambes, puis pouvoir (ou poids) du corps. */
    private fun details(c: Critter) = getString(R.string.papattes_card,
        getString(R.string.papattes_card, getString(c.legs.gait.nameRes), getString(c.legs.traitRes)),
        getString(if (c.body.power == Power.NONE) c.body.weight.nameRes else c.body.power.nameRes))

    private fun updateLabButtons() {
        val picked = selected.map { progress.pen[it] }
        selectionLabel.visibility = if (picked.isEmpty()) View.GONE else View.VISIBLE
        selectionLabel.text = when (picked.size) {
            1 -> fullName(picked[0]) + "\n" + details(picked[0])
            else -> picked.joinToString("\n") { fullName(it) }
        }
        val one = selected.size == 1
        walkWith.isEnabled = one && selected[0] != progress.active
        release.isEnabled = one && progress.pen.size > 1
        breed.text = getString(R.string.papattes_breed, PapattesProgress.BREED_COST)
        breed.isEnabled = selected.size == 2 && progress.canBreed(selected[0], selected[1])
        for (b in listOf(walkWith, release, breed)) b.alpha = if (b.isEnabled) 1f else .45f
    }

    private fun walkWithSelected() {
        val index = selected.singleOrNull() ?: return
        progress.select(index)
        selected.clear()
        save()
        refreshLab()
    }

    private fun releaseSelected() {
        val index = selected.singleOrNull() ?: return
        if (!progress.release(index)) return
        selected.clear()
        save()
        refreshLab()
    }

    private fun breedSelected() {
        if (selected.size != 2) return
        progress.breed(selected[0], selected[1], Random.Default) ?: return
        save()
        clearFilters()
        selected.clear()
        selected += progress.pen.lastIndex
        refreshLab()
        celebrateNewest()
    }

    /** La dernière venue de l'enclos saute un peu, avec son mot. */
    private fun celebrateNewest() {
        val newest = penGrid.getChildAt(penGrid.childCount - 1) as? LinearLayout ?: return
        newest.addView(label(R.string.papattes_hatched, 11f, palette.ink).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }, 0, LinearLayout.LayoutParams(-1, -2))
        newest.scaleX = .6f; newest.scaleY = .6f
        newest.animate().scaleX(1f).scaleY(1f).setDuration(380).start()
        labScroll.post { labScroll.smoothScrollTo(0, penGrid.top + newest.top) }
    }

    // ── Petits outils ───────────────────────────────────────────────────────────────────────────

    private fun shape(fill: Int, radius: Float, stroke: Int?, strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = AppearanceStyle.corner(this@PapattesActivity, radius)
        if (stroke != null) setStroke(dp(strokeDp), stroke)
    }

    private fun icon(sprite: Sprite, size: Int) = BitmapDrawable(resources, sprite.bitmap).apply {
        val h = dp(size)
        setBounds(0, 0, h * sprite.bitmap.width / sprite.bitmap.height, h)
    }

    private fun label(text: String, size: Float, color: Int = palette.text) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
    }

    private fun label(text: Int, size: Float, color: Int = palette.text) = label(getString(text), size, color)

    /** Bouton plein à l'accent du thème ([primary]) ou en relief avec contour. */
    private fun button(text: Int, primary: Boolean = false, action: () -> Unit) = MaterialButton(this).apply {
        setText(text); isAllCaps = false; minimumHeight = dp(48)
        cornerRadius = AppearanceStyle.corner(this@PapattesActivity, 18f).toInt()
        backgroundTintList = ColorStateList.valueOf(if (primary) palette.accent else palette.raised)
        setTextColor(if (primary) palette.onAccent else palette.text)
        strokeWidth = if (primary) 0 else dp(1)
        strokeColor = ColorStateList.valueOf(palette.outline)
        rippleColor = ColorStateList.valueOf(palette.withAlpha(if (primary) palette.onAccent else palette.accent, .2f))
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        insetTop = dp(4); insetBottom = dp(4)
        setOnClickListener { action() }
    }

    /** Icône seule de la barre du haut (retour, règles), sans fond, comme dans les autres jeux. */
    private fun iconButton(iconRes: Int, description: Int, tint: Int, action: () -> Unit) = ImageButton(this).apply {
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        setBackgroundResource(ripple.resourceId)
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(tint)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = getString(description)
        setOnClickListener { action() }
    }

    private fun roundButton(iconRes: Int, description: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(palette.text)
        background = GradientDrawable().apply {
            shape = AppearanceStyle.controlShape(this@PapattesActivity)
            if (shape != GradientDrawable.OVAL) cornerRadius = AppearanceStyle.corner(this@PapattesActivity, 14f)
            setColor(palette.raised)
            setStroke(dp(1), palette.outline)
        }
        contentDescription = getString(description)
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    override fun onPause() {
        if (::scene.isInitialized && screen == Screen.PLAY) show(Screen.PAUSE)
        if (::progress.isInitialized) save()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::scene.isInitialized && screen == Screen.PLAY) show(Screen.PAUSE)
    }

    override fun onDestroy() {
        if (::scene.isInitialized) scene.setRunning(false)
        super.onDestroy()
    }

    private companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_STAGE = "stage"
        /** Largeur visée d'une tuile d'enclos, en dp. */
        const val CARD_DP = 118f
    }
}
