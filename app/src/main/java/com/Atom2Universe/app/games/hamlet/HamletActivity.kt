package com.Atom2Universe.app.games.hamlet

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight
import com.Atom2Universe.app.util.SystemBarsManager

/**
 * Le village garde tout l'écran. Une bannière commune, en bas, mène aux autres modules (aventure,
 * héros, journal, voisins) qui prennent sa place ; les fenêtres secondaires sont des fiches parchemin.
 */
class HamletActivity : ThemedActivity(), HamletViewHost, HamletListener, HamletNav {
    /** The whole game reads a little larger, clearly larger on tablets. */
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(HamletUi.larger(newBase))
    internal lateinit var game: HamletGame
    internal lateinit var village: HamletView
    private lateinit var top: HamletTopBar
    private lateinit var dock: HamletDock
    private lateinit var stage: FrameLayout
    private lateinit var villagePage: FrameLayout
    private lateinit var tracker: TextView
    private lateinit var recenter: View
    private lateinit var fabs: View
    private lateinit var collectFab: View
    private lateinit var collectBadge: TextView
    private lateinit var undoFab: View
    private lateinit var panel: LinearLayout
    private lateinit var selectedLabel: TextView
    private lateinit var progress: HamletBar
    private lateinit var objectActions: LinearLayout
    private lateinit var photoTools: View
    private var module = HamletModule.VILLAGE
    private var adventure: HamletAdventurePage? = null
    private var journal: HamletJournalPage? = null
    private var neighbors: HamletNeighborsPage? = null
    private var selected: Obj? = null
    private var actionSignature = ""
    private var questReady: Boolean? = null
    private var resumed = false
    private var creative = false
    private var unreadableSave = false
    private lateinit var worldFiles: HamletWorldFiles
    private val prefs by lazy { getSharedPreferences("hamlet_ui", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val delayedSave = Runnable { save() }
    private val refreshSoon = Runnable { refresh() }
    private val loop = object : Runnable {
        override fun run() {
            if (!resumed) return
            game.tick(); refresh(); page()?.tick(); handler.postDelayed(this, 1_000L)
        }
    }
    override val haptics get() = village.haptics
    override val effects get() = village.renderer.effectsEnabled

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        worldFiles = HamletWorldFiles(this)
        worldFiles.restoreState(savedInstanceState)
        supportActionBar?.hide()
        game = HamletGame()
        creative = prefs.getBoolean("creative", false)
        if (!HamletSave.read(filesDir, game, creative)) {
            unreadableSave = HamletSave.file(filesDir, creative).exists()
            game.startNew(creative)
        }
        village = HamletView(this, game, HamletArt()).also { it.host = this }
        village.haptics = prefs.getBoolean("haptics", true)
        village.renderer.nightEnabled = prefs.getBoolean("night", true)
        village.renderer.effectsEnabled = prefs.getBoolean("effects", true)
        HamletMotion.enabled = village.renderer.effectsEnabled
        game.listener = this
        val root = column().apply { setBackgroundColor(HamletUi.DEEP) }
        top = HamletTopBar(this, onMenu = { tick(); showMenu() }, onExit = { exitGame() })
        root.addView(top)
        stage = FrameLayout(this)
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        villagePage = FrameLayout(this)
        villagePage.addView(village, FrameLayout.LayoutParams(-1, -1))
        buildVillageOverlays()
        stage.addView(villagePage, FrameLayout.LayoutParams(-1, -1))
        dock = HamletDock(this) { target -> tick(); open(target) }
        root.addView(dock)
        photoTools = LinearLayout(this).apply {
            gravity = Gravity.CENTER; visibility = View.GONE; setPadding(dp(8), dp(6), dp(8), dp(8))
            addView(button(R.string.hamlet_photo_light, "sun") { showLighting() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
            addView(button(R.string.hamlet_photo_capture, "camera", Weight.MAIN) { worldFiles.capture() }, LinearLayout.LayoutParams(0, -2, 1.3f).apply { marginEnd = dp(4) })
            addView(button(R.string.hamlet_photo_return, "cross", Weight.QUIET, onDark = true) { setPhotoMode(false) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(photoTools)
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    village.photoMode -> setPhotoMode(false)
                    module != HamletModule.VILLAGE -> if (page()?.back() != true) open(HamletModule.VILLAGE)
                    village.mode !is HamletMode.Browse -> village.browse()
                    selected != null -> onTapEmpty()
                    else -> exitGame()
                }
            }
        })
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(SystemBarsManager.visibleTypes(this) or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        top.module(HamletModule.VILLAGE, getString(HamletModule.VILLAGE.label))
        village.post {
            village.home()
            if (unreadableSave) {
                val sheet = HamletSheet(this, getString(R.string.hamlet_realms_import))
                sheet.body.addView(TextView(this).apply { setText(R.string.hamlet_save_recovery); textSize = HamletUi.TEXT; setTextColor(HamletUi.INK) })
                sheet.dialog.setCancelable(false)
                sheet.footer(button(R.string.hamlet_ui_exit, "door", Weight.QUIET) { sheet.dismiss(); finish() } to 1f,
                    button(R.string.hamlet_realms_import, "crate", Weight.MAIN) { sheet.dismiss(); worldFiles.importWorld() } to 1.4f)
                sheet.show()
            } else if (!prefs.getBoolean("introduced", false)) { prefs.edit().putBoolean("introduced", true).apply(); showHelp() }
        }
        refresh()
    }

    /** Over the map: the goal at the top left, the recentre button at the top right, the big round buttons above the panel. */
    private fun buildVillageOverlays() {
        tracker = TextView(this).apply {
            textSize = 13f; setTextColor(HamletUi.INK); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(10), dp(7), dp(12), dp(7)); elevation = dp(4).toFloat(); gravity = Gravity.CENTER_VERTICAL
            setCompoundDrawablesRelativeWithIntrinsicBounds(HamletUi.icon(this@HamletActivity, "star", 20), null, null, null)
            compoundDrawablePadding = dp(7)
            setOnClickListener { tick(); showQuest() }
            HamletMotion.press(this)
        }
        villagePage.addView(tracker, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(10), dp(70), 0) })
        recenter = HamletUi.round(this, "map", getString(R.string.hamlet_ui_center), 46) { tick(); village.home() }
        villagePage.addView(recenter, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(10), dp(10), 0) })
        val bottom = column()
        val row = LinearLayout(this).apply { gravity = Gravity.BOTTOM; setPadding(dp(10), 0, dp(10), dp(6)) }
        val collect = FrameLayout(this)
        collect.addView(fab("sack", R.string.hamlet_ui_collect, 56, false) {
            val n = game.collectAll()
            if (n > 0 && village.haptics) village.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }, FrameLayout.LayoutParams(-2, -2))
        collectBadge = TextView(this).apply {
            textSize = 11f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            minWidth = dp(20); setPadding(dp(5), 0, dp(5), 0); elevation = dp(8).toFloat()
            background = GradientDrawable().apply { setColor(HamletUi.ALERT); cornerRadius = dp(10).toFloat(); setStroke(dp(2), 0xFFFFFFFF.toInt()) }
        }
        collect.addView(collectBadge, FrameLayout.LayoutParams(-2, dp(20), Gravity.TOP or Gravity.END))
        collectFab = collect
        row.addView(collect)
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        undoFab = fab("undo", R.string.hamlet_tools_undo, 46, false) { village.undoBuild() }
        row.addView(undoFab, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
        row.addView(fab("hammer", R.string.hamlet_build, 66, true) { HamletCatalogDialog(this).show() })
        fabs = row
        bottom.addView(row)
        panel = column().apply {
            setPadding(dp(14), dp(9), dp(14), dp(8)); elevation = dp(6).toFloat()
            background = HamletUi.panel(this@HamletActivity, HamletUi.PAPER, 16, 0xFFD7C7A5.toInt())
        }
        selectedLabel = TextView(this).apply { textSize = 13.5f; setTextColor(HamletUi.INK); setLineSpacing(dp(2).toFloat(), 1f) }
        panel.addView(selectedLabel)
        progress = HamletBar(this)
        panel.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(5) })
        objectActions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        panel.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(objectActions) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        bottom.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(8), 0, dp(8), dp(8)) })
        villagePage.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }
    /** A round map button with its name under it. */
    private fun fab(glyph: String, label: Int, size: Int, gold: Boolean, click: () -> Unit) = column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(HamletUi.round(this@HamletActivity, glyph, getString(label), size, gold) { tick(); click() }, LinearLayout.LayoutParams(dp(size), dp(size)))
        addView(TextView(this@HamletActivity).apply {
            setText(label); textSize = 12f; setTextColor(HamletUi.PAPER); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0xE6000000.toInt())
        })
    }

    override fun onPause() {
        resumed = false; village.stop(); page()?.hide(); handler.removeCallbacks(loop); handler.removeCallbacks(delayedSave)
        save(); super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        worldFiles.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume(); resumed = true
        if (::village.isInitialized) { if (module == HamletModule.VILLAGE) village.start() else page()?.show(-1) }
        handler.removeCallbacks(loop); handler.post(loop)
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); worldFiles.close(); village.stop(); village.art.clear()
        for (p in listOfNotNull(adventure, journal, neighbors)) p.close()
        game.listener = null; super.onDestroy()
    }

    // ------------------------------------------------------------------ modules

    private fun page(m: HamletModule = module): HamletPage? = when (m) {
        HamletModule.VILLAGE -> null
        HamletModule.ADVENTURE, HamletModule.HEROES -> adventure
        HamletModule.JOURNAL -> journal
        HamletModule.NEIGHBORS -> neighbors
    }
    private fun create(m: HamletModule): HamletPage = page(m) ?: when (m) {
        HamletModule.JOURNAL -> HamletJournalPage(this, game, this, ::showQuest).also { journal = it }
        HamletModule.NEIGHBORS -> HamletNeighborsPage(this, game, this).also { neighbors = it }
        else -> HamletAdventurePage(this, game, this).also { adventure = it }
    }
    private fun adventurous(m: HamletModule) = m == HamletModule.ADVENTURE || m == HamletModule.HEROES

    override fun open(module: HamletModule, page: Int) {
        if (village.photoMode) return
        val previous = this.module
        if (previous == HamletModule.VILLAGE && module == HamletModule.VILLAGE && page < 0) { village.home(); return }
        val sameView = previous == module || (adventurous(previous) && adventurous(module))
        if (!sameView) {
            if (previous == HamletModule.VILLAGE) { village.stop(); selected = null; village.browse() }
            else page(previous)?.let { it.hide(); it.view.visibility = View.GONE }
        }
        this.module = module
        dock.select(module); top.module(module, getString(module.label))
        if (module == HamletModule.VILLAGE) {
            villagePage.visibility = View.VISIBLE
            if (resumed) village.start()
            actionSignature = ""; refresh()
            if (!sameView) HamletMotion.enter(villagePage)
        } else {
            villagePage.visibility = View.GONE
            val target = create(module)
            if (target.view.parent == null) stage.addView(target.view, FrameLayout.LayoutParams(-1, -1))
            target.view.visibility = View.VISIBLE
            val at = when {
                page >= 0 -> page
                adventurous(module) -> adventure!!.first(module == HamletModule.HEROES, again = previous == module)
                else -> -1
            }
            target.show(at)
            if (!sameView) HamletMotion.enter(target.view)
        }
        refreshBadges()
    }
    override fun build(def: Def) { open(HamletModule.VILLAGE); selected = null; village.startPlacing(def) }
    override fun supplies(chips: List<HamletChip>) = top.supplies(chips)
    override fun changed() = onChanged()

    private fun villageChips(): List<HamletChip> {
        val values = longArrayOf(game.coins.toLong(), game.gems.toLong(), game.population().toLong(), game.happiness().toLong())
        val glyphs = arrayOf("coin", "gem", "hat", "flower")
        val descriptions = intArrayOf(R.string.hamlet_ui_coins, R.string.hamlet_ui_gems, R.string.hamlet_ui_people, R.string.hamlet_ui_happiness)
        return values.indices.map { i ->
            HamletChip(glyphs[i], values[i], getString(if (i == 3) R.string.hamlet_ui_percent else R.string.hamlet_ui_number, values[i]), getString(descriptions[i], values[i]))
        }
    }
    private fun rewardReady() = game.currentQuest()?.let { game.questDone(it) } == true || game.journey.canClaim() ||
        game.stewardship.hasRewards() || (game.journey.expedition >= 0 && game.journey.expeditionLeft() == 0L)
    private fun refreshBadges() {
        dock.badge(HamletModule.VILLAGE, if (module != HamletModule.VILLAGE) game.collectableCount() else 0)
        dock.badge(HamletModule.HEROES, if (game.adventure[AdventureResource.TICKETS] >= 1) -1 else 0)
        dock.badge(HamletModule.JOURNAL, if (rewardReady()) -1 else 0)
        dock.badge(HamletModule.NEIGHBORS, game.neighbors.readyCount())
        top.menuBadge(game.giftReady() || (0..2).any { game.dailyClaimed and (1 shl it) == 0 && game.dailyProgress(it) >= HamletLife.dailyTargets[it] })
    }

    // ------------------------------------------------------------------ village

    override fun onTapObject(o: Obj) {
        selected = o
        if (village.mode is HamletMode.Browse) game.collectReady(o)
        refresh()
    }
    override fun onTapVillager(v: Villager) {
        val roles = intArrayOf(R.string.hamlet_role_resident, R.string.hamlet_role_farmer, R.string.hamlet_role_artisan, R.string.hamlet_role_guard, R.string.hamlet_role_shepherd)
        val sheet = HamletSheet(this, getString(R.string.hamlet_resident_name, Hamlet.visitors[v.nameIndex], getString(roles[v.role])))
        sheet.body.addView(text(getString(R.string.hamlet_resident_story, game.happiness())))
        sheet.footer(button(R.string.hamlet_daily, "sun") { sheet.dismiss(); showDaily() } to 1f)
        sheet.show()
    }
    override fun onTapEmpty() { selected = null; refresh() }
    override fun onModeChanged() { actionSignature = ""; refresh() }
    override fun onReward(o: Obj?, coins: Int, xp: Int, gems: Int) {
        village.reward(o, coins, xp, gems)
        if (village.haptics) village.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    override fun onLevelUp(level: Int, gems: Int) {
        toast(getString(R.string.hamlet_level_up, level, gems))
        if (village.haptics) village.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    override fun onMessage(code: String, arg: String) {
        val id = resources.getIdentifier("hamlet_msg_$code", "string", packageName)
        if (id != 0) toast(if (arg.isEmpty()) getString(id) else getString(id, if (arg in Hamlet.byId) itemName(arg) else arg))
    }
    override fun onChanged() {
        handler.removeCallbacks(delayedSave); handler.postDelayed(delayedSave, 600)
        handler.removeCallbacks(refreshSoon); handler.post(refreshSoon)
    }
    private fun refresh() {
        if (!::top.isInitialized) return
        val realmName = game.realms.name(game.realms.current).ifBlank { getString(R.string.hamlet_realms_default_name, game.realms.current) }
        top.subtitle(getString(R.string.hamlet_ui_realm_level, realmName, if (creative) getString(R.string.hamlet_ui_creative) else getString(R.string.hamlet_ui_level, game.level())))
        refreshBadges()
        if (module != HamletModule.VILLAGE) return
        top.supplies(villageChips())
        val browsing = village.mode is HamletMode.Browse
        val o = selected?.takeIf { game.objs[it.id] === it }
        selected = o; village.renderer.selected = if (browsing) o else null
        val canCollect = game.canCollectAll(); val ready = game.collectableCount()
        collectFab.visibility = if (canCollect && ready > 0) View.VISIBLE else View.GONE
        collectBadge.text = getString(R.string.hamlet_ui_number, ready)
        undoFab.visibility = if (game.buildHistory.canUndo()) View.VISIBLE else View.GONE
        fabs.visibility = if (browsing) View.VISIBLE else View.GONE
        recenter.visibility = if (browsing) View.VISIBLE else View.GONE
        val quest = game.currentQuest()
        tracker.visibility = if (browsing && o == null && quest != null) View.VISIBLE else View.GONE
        if (quest != null) {
            val done = game.questDone(quest)
            tracker.text = getString(R.string.hamlet_ui_goal_line, stringByName("hamlet_q${game.quest}_d"), game.questProgress(quest).coerceAtMost(quest.n.toLong()), quest.n)
            if (questReady != done) {
                questReady = done
                tracker.background = HamletUi.panel(this, if (done) HamletUi.GOLD else 0xF2F6EDD9.toInt(), 14, if (done) 0xFFFFE3A0.toInt() else 0xFFD7C7A5.toInt())
                tracker.setTypeface(null, if (done) Typeface.BOLD else Typeface.NORMAL)
                if (done) HamletMotion.pop(tracker, 1.12f)
            }
        }
        val placing = village.mode is HamletMode.Place; val brushing = village.mode is HamletMode.Brush
        val status = o?.let { game.status(it) }; val left = o?.let { remaining(it) } ?: 0L
        panel.visibility = if (!browsing || o != null) View.VISIBLE else View.GONE
        selectedLabel.text = when {
            placing -> getString(if (village.renderer.ghostOk) R.string.hamlet_placing else R.string.hamlet_invalid_placement)
            brushing -> getString(R.string.hamlet_brushing)
            village.mode is HamletMode.Bulldoze -> getString(R.string.hamlet_tools_bulldoze_hint)
            village.mode is HamletMode.Eyedropper -> getString(R.string.hamlet_tools_pick_hint)
            village.mode is HamletMode.Redesign -> getString(R.string.hamlet_tools_move_hint)
            village.mode is HamletMode.Fill -> getString(R.string.hamlet_tools_fill_hint)
            o == null -> ""
            else -> objectLine(o, status!!, left)
        }
        selectedLabel.visibility = if (selectedLabel.text.isEmpty()) View.GONE else View.VISIBLE
        progress.visibility = if (left > 0 && !placing && !brushing) View.VISIBLE else View.GONE
        if (o != null && left > 0) {
            val total = when (status) {
                Status.BUILDING -> o.def.build * 1000L
                Status.GROWING -> game.cropOf(o)!!.time * 1000L
                Status.ORDER_BUSY -> o.def.job * 1000L
                Status.PRODUCING -> HamletLife.produce.getValue(o.t).seconds * 1000L
                else -> o.def.period * 1000L
            }
            progress.set(total - left, total.coerceAtLeast(1))
        }
        val sig = listOf(village.mode::class, o?.id, status, o?.crop, o?.tier, o?.tended, o?.let { game.hurryCost(it) }, game.workSeconds > 0, village.renderer.ghostOk, game.buildHistory.canUndo(), game.quest).joinToString()
        if (sig == actionSignature) return
        actionSignature = sig; objectActions.removeAllViews()
        if (placing) {
            addAction(R.string.hamlet_rotate, "undo") { village.rotateGhost() }
            addAction(R.string.hamlet_confirm, "tick", Weight.MAIN) {
                if (village.confirmPlacement() && village.haptics) village.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }.isEnabled = village.renderer.ghostOk
            addAction(R.string.hamlet_cancel, "cross", Weight.QUIET) { village.browse() }
        } else if (!browsing) {
            addAction(R.string.hamlet_cancel, "cross") { village.browse() }
            if (game.buildHistory.canUndo()) addAction(R.string.hamlet_tools_undo, "undo", Weight.QUIET) { village.undoBuild() }
        } else if (o != null) objectActionButtons(o)
    }
    /** The object's name in bold, its state, and the time left. */
    private fun objectLine(o: Obj, status: Status, left: Long): CharSequence {
        val name = itemName(o.t)
        val line = getString(R.string.hamlet_object_line, name, o.tier, stringByName("hamlet_status_${status.name.lowercase()}")) +
            if (left > 0) getString(R.string.hamlet_remaining, duration(left)) else ""
        return HamletUi.tint(line, name to HamletUi.INK)
    }
    private fun remaining(o: Obj) = when (game.status(o)) {
        Status.BUILDING -> game.buildLeft(o)
        Status.GROWING -> game.cropLeft(o)
        Status.ORDER_BUSY -> game.jobLeft(o)
        Status.PRODUCING -> game.productLeft(o)
        Status.NONE -> if (o.def.cat == Cat.HOME) game.taxLeft(o) else 0L
        else -> 0L
    }
    private fun addAction(label: Int, glyph: String, weight: Weight = Weight.NORMAL, click: () -> Unit) = button(label, glyph, weight, click = click).also {
        objectActions.addView(it, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6); topMargin = dp(2) })
    }
    private fun objectActionButtons(o: Obj) {
        when (game.status(o)) {
            Status.RIPE, Status.WITHERED -> addAction(R.string.hamlet_harvest, "sack", Weight.MAIN) { game.harvest(o) }
            Status.TAX -> addAction(R.string.hamlet_collect, "coin", Weight.MAIN) { game.collectTax(o) }
            Status.ORDER_NEW -> { addAction(R.string.hamlet_accept, "scroll", Weight.MAIN) { game.acceptOrder(o) }; addAction(R.string.hamlet_decline, "cross", Weight.QUIET) { game.declineOrder(o) } }
            Status.ORDER_DONE -> addAction(R.string.hamlet_deliver, "crate", Weight.MAIN) { game.deliver(o) }
            Status.GIFT -> addAction(R.string.hamlet_gift, "gift", Weight.MAIN) { game.openGift() }
            Status.FEED -> addAction(R.string.hamlet_feed, "apple", Weight.MAIN) {
                confirm(getString(R.string.hamlet_feed), getString(R.string.hamlet_feed_confirm, HamletLife.produce.getValue(o.t).feed), "apple") { game.feed(o) }
            }
            Status.PRODUCT -> addAction(R.string.hamlet_products, "crate", Weight.MAIN) { game.collectProduct(o) }
            else -> Unit
        }
        if (o.t in HamletAdventure.FACILITIES && game.buildLeft(o) == 0L) {
            addAction(R.string.hamlet_s_facility_open, "sword") {
                when (o.t) {
                    "adventureInn" -> open(HamletModule.HEROES, HamletAdventurePage.HEROES)
                    "adventureForge" -> open(HamletModule.HEROES, HamletAdventurePage.FORGE)
                    "adventureGate" -> open(HamletModule.ADVENTURE, HamletAdventurePage.EXPLORE)
                    else -> open(HamletModule.ADVENTURE, HamletAdventurePage.KINGDOM)
                }
            }
        }
        if (o.def.cat == Cat.FIELD && o.crop == null && game.buildLeft(o) == 0L) addAction(R.string.hamlet_plant, "leaf", Weight.MAIN) { showCropMenu(o) }
        if (o.crop != null && !o.tended && game.cropStage(o) < 2) addAction(R.string.hamlet_tend, "drop") {
            confirm(getString(R.string.hamlet_tend), getString(R.string.hamlet_tend_confirm, game.tendCost(o)), "drop") { game.tend(o) }
        }
        if (game.workLeft(o) > 0) addAction(R.string.hamlet_work_apply, "hammer") {
            if (!game.applyWork(o)) open(HamletModule.JOURNAL, HamletJournalPage.WORK)
        }
        if (game.hurryCost(o) > 0) addAction(R.string.hamlet_hurry, "hourglass") {
            confirm(getString(R.string.hamlet_hurry), getString(R.string.hamlet_hurry_confirm, game.hurryCost(o)), "hourglass") { game.hurry(o) }
        }
        if (game.canUpgrade(o)) addAction(R.string.hamlet_upgrade, "star") {
            confirm(getString(R.string.hamlet_upgrade), getString(R.string.hamlet_upgrade_confirm, o.tier + 1, game.upgradeCost(o)), "star") { game.upgrade(o) }
        }
        addAction(R.string.hamlet_info, "book", Weight.QUIET) { showObjectInfo(o) }
        if (o.def.cat != Cat.HALL) {
            addAction(R.string.hamlet_move, "move", Weight.QUIET) { village.startPlacing(o.def, o) }
            addAction(R.string.hamlet_tools_duplicate, "brush", Weight.QUIET) { selected = null; village.duplicate(o) }
            addAction(R.string.hamlet_sell, "coin", Weight.QUIET) {
                confirm(getString(R.string.hamlet_sell), getString(R.string.hamlet_sell_confirm, itemName(o.t), game.sellValue(o)), "coin") { game.sell(o).also { if (it) selected = null } }
            }
        }
    }
    private fun showCropMenu(field: Obj) {
        var all = prefs.getBoolean("plantAll", false)
        val crops = Hamlet.crops.filter { it.lvl <= game.ulvl() && it.magic == field.def.magic }
        val sheet = HamletSheet(this, getString(R.string.hamlet_plant), tall = crops.size > 6)
        val toggle = HamletUi.row(this, getString(R.string.hamlet_plant_all), all, if (all) "tick" else "leaf") {}
        fun paint() { HamletUi.glyph(toggle, if (all) "tick" else "leaf"); toggle.setTypeface(null, if (all) Typeface.BOLD else Typeface.NORMAL)
            HamletUi.face(toggle, HamletUi.rowFace(this, all)) }
        toggle.setOnClickListener { all = !all; prefs.edit().putBoolean("plantAll", all).apply(); paint() }
        sheet.top.addView(toggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        for (crop in crops) {
            val name = itemName("c_${crop.id}")
            val label = HamletUi.tint(getString(R.string.hamlet_crop_choice, name, crop.cost, crop.earn, duration(crop.time * 1000L)), name to HamletUi.INK)
            sheet.body.addView(HamletUi.row(this, label, glyph = "leaf") {
                sheet.dismiss()
                if (all) game.plantAll(crop.id) else game.plant(field, crop.id)
            }.apply { isEnabled = game.sandbox || game.coins >= crop.cost }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        sheet.show()
    }
    private fun showObjectInfo(o: Obj) {
        val d = o.def
        var message = getString(R.string.hamlet_footprint, o.w, o.d, d.lvl, d.pop)
        message += when {
            o.t in HamletLife.produce -> HamletLife.produce.getValue(o.t).let { p ->
                getString(R.string.hamlet_producer_info, game.yieldCoins(o, p.coins), duration(p.seconds * 1000L), p.feed) +
                    getString(if (p.feed > 0) R.string.hamlet_ranch_help else R.string.hamlet_orchard_help)
            }
            d.cat == Cat.HOME -> getString(R.string.hamlet_home_info, game.yieldCoins(o, d.tax), duration(d.period * 1000L), game.charmBonus(o))
            d.cat == Cat.SHOP -> getString(R.string.hamlet_shop_info, stringByName("hamlet_n_p_${o.t}"), game.yieldCoins(o, d.pay), duration(d.job * 1000L), game.charmBonus(o))
            d.cat == Cat.FIELD -> getString(R.string.hamlet_field_help)
            else -> getString(R.string.hamlet_charm_info, d.charm)
        }
        if (d.shore) message += getString(R.string.hamlet_shore_help)
        if (game.stewardship.villageBonus(d.cat) > 0) message += getString(R.string.hamlet_s_village_bonus, game.stewardship.villageBonus(d.cat))
        HamletSheet.message(this, itemName(o.t), message)
    }

    // ------------------------------------------------------------------ menu and small windows

    /** Rare things, in one place behind the gear: a grid of pictures. */
    private fun showMenu() {
        val sheet = HamletSheet(this, getString(R.string.hamlet_village), tall = true)
        val canHall = game.nextHall()?.let { game.ulvl() >= it.lvl && game.journey.canUpgradeHall() } == true
        val daily = (0..2).any { game.dailyClaimed and (1 shl it) == 0 && game.dailyProgress(it) >= HamletLife.dailyTargets[it] }
        class Entry(val label: Int, val glyph: String, val enabled: Boolean = true, val calls: Boolean = false, val run: () -> Unit)
        val entries = listOf(
            Entry(R.string.hamlet_daily, "sun", calls = daily) { showDaily() },
            Entry(R.string.hamlet_gift, "gift", game.giftReady(), calls = game.giftReady()) { game.openGift() },
            Entry(R.string.hamlet_land, "map", game.canExpand()) { expandLand() },
            Entry(R.string.hamlet_hall, "castle", canHall) { upgradeHall() },
            Entry(R.string.hamlet_overview, "book") { showOverview() },
            Entry(R.string.hamlet_tools_title, "brush") { HamletCatalogDialog(this).show(tools = true) },
            Entry(R.string.hamlet_realms_title, "ship") { HamletWorldsDialog(this, game, onChanged = ::refreshWorld, onSave = { save() }).show() },
            Entry(R.string.hamlet_chronicle_title, "scroll") { HamletChronicleDialog(this, game).show() },
            Entry(R.string.hamlet_photo_title, "camera") { setPhotoMode(true) },
            Entry(R.string.hamlet_realms_export, "crate") { worldFiles.exportWorld() },
            Entry(R.string.hamlet_realms_import, "sack") { worldFiles.importWorld() },
            Entry(R.string.hamlet_worlds, "star") { showWorlds() },
            Entry(R.string.hamlet_settings, "gear") { showSettings() },
            Entry(R.string.hamlet_help, "lantern") { showHelp() },
        )
        val columns = if (resources.configuration.screenWidthDp >= 600) 4 else 3
        for (group in entries.chunked(columns)) {
            val row = LinearLayout(this)
            for (e in group) {
                val tile = HamletUi.tab(this, getString(e.label), e.glyph) { sheet.dismiss(); e.run() }.apply {
                    maxLines = 2; textSize = 12.5f; minHeight = dp(78); minimumHeight = dp(78)
                    HamletUi.face(this, HamletUi.rowFace(this@HamletActivity, e.calls))
                    setTextColor(android.content.res.ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(0xFF9AA09A.toInt(), HamletUi.INK)))
                    setCompoundDrawablesWithIntrinsicBounds(null, HamletUi.icon(this@HamletActivity, e.glyph, 30), null, null)
                    isEnabled = e.enabled; alpha = if (e.enabled) 1f else .6f
                }
                row.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            repeat(columns - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            sheet.body.addView(row)
        }
        sheet.show()
    }
    private fun showOverview() {
        val q = game.currentQuest()
        val text = getString(R.string.hamlet_overview_detail, game.population(), game.happiness(), game.objs.values.count { it.def.cat == Cat.HOME },
            game.objs.values.count { it.def.cat == Cat.SHOP }, game.objs.values.count { it.def.cat == Cat.RANCH }, game.realmSize(),
            game.stats["harvested"] ?: 0, game.stats["delivered"] ?: 0, game.stats["products"] ?: 0, game.readyCount()) +
            if (q != null) getString(R.string.hamlet_next_goal, stringByName("hamlet_q${game.quest}_d"), game.questProgress(q), q.n) else ""
        HamletSheet.message(this, getString(R.string.hamlet_overview), text)
    }
    private fun showDaily() {
        game.ensureDaily()
        val labels = intArrayOf(R.string.hamlet_daily_harvest, R.string.hamlet_daily_products, R.string.hamlet_daily_orders)
        val glyphs = arrayOf("sack", "milk", "crate")
        val sheet = HamletSheet(this, getString(R.string.hamlet_daily), getString(R.string.hamlet_daily_help, duration(Hamlet.DAY_MS - game.now() % Hamlet.DAY_MS)))
        fun fill() {
            sheet.body.removeAllViews()
            for (i in labels.indices) {
                val claimed = game.dailyClaimed and (1 shl i) != 0
                val target = HamletLife.dailyTargets[i]
                val done = game.dailyProgress(i).coerceAtMost(target.toLong())
                val box = column().apply { background = HamletUi.card(this@HamletActivity, 0xFFFFF8E9.toInt()); setPadding(dp(12), dp(8), dp(12), dp(10)) }
                val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                head.addView(ImageView(this).apply { setImageDrawable(HamletUi.icon(this@HamletActivity, glyphs[i], 26)) }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(8) })
                head.addView(TextView(this).apply {
                    text = getString(R.string.hamlet_ui_goal_line, getString(labels[i]), done, target); textSize = HamletUi.TEXT; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                head.addView(TextView(this).apply {
                    text = getString(R.string.hamlet_ui_number, HamletLife.dailyCoins[i]); textSize = HamletUi.TEXT; setTextColor(0xFFB27A12.toInt()); setTypeface(null, Typeface.BOLD)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(HamletUi.icon(this@HamletActivity, "coin", 18), null, null, null); compoundDrawablePadding = dp(4)
                })
                box.addView(head)
                box.addView(HamletBar(this).apply { set(done, target.toLong()) }, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(6); bottomMargin = dp(4) })
                if (claimed) box.addView(TextView(this).apply { setText(R.string.hamlet_ui_claimed); setTextColor(HamletUi.GOOD); textSize = HamletUi.SMALL; setTypeface(null, Typeface.BOLD) })
                else if (done >= target) box.addView(button(R.string.hamlet_claim, "coin", Weight.MAIN) { if (game.claimDaily(i)) fill() })
                sheet.body.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        fill(); sheet.show()
    }
    internal fun showQuest() {
        val q = game.currentQuest() ?: return
        val title = stringByName("hamlet_q${game.quest}_t")
        val sheet = HamletSheet(this, title)
        val done = game.questDone(q)
        sheet.body.addView(text(stringByName("hamlet_q${game.quest}_d")))
        val progressValue = game.questProgress(q).coerceAtMost(q.n.toLong())
        sheet.body.addView(HamletBar(this).apply { set(progressValue, q.n.toLong()) }, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(8) })
        sheet.body.addView(text(getString(R.string.hamlet_ui_progress, progressValue, q.n)).apply { textSize = HamletUi.SMALL; setTextColor(HamletUi.MUTED) })
        val reward = getString(R.string.hamlet_ui_reward_pair, q.coins, q.gems)
        sheet.body.addView(text(HamletUi.tint(getString(R.string.hamlet_ui_reward_line, reward), reward to 0xFFB27A12.toInt())))
        if (done) sheet.footer(button(R.string.hamlet_claim, "gift", Weight.MAIN) { sheet.dismiss(); game.claimQuest() } to 1f)
        else sheet.footer(button(R.string.hamlet_ui_goal_go, "map") { sheet.dismiss(); guideToQuest(q) } to 1f)
        sheet.show()
    }
    private fun expandLand() {
        if (!game.canExpand()) return
        confirm(getString(R.string.hamlet_land), getString(R.string.hamlet_land_confirm, game.expandCost()), "map") { game.expandLand() }
    }
    private fun upgradeHall() {
        val next = game.nextHall() ?: return
        if (game.ulvl() < next.lvl || !game.journey.canUpgradeHall()) return
        confirm(getString(R.string.hamlet_hall), getString(R.string.hamlet_hall_confirm, game.hall + 1, next.cost, next.realm), "castle") { game.upgradeHall() }
    }
    private fun showHelp() { HamletSheet.message(this, getString(R.string.hamlet_help), getString(R.string.hamlet_help_text)) }
    private fun showSettings() {
        val sheet = HamletSheet(this, getString(R.string.hamlet_settings))
        val labels = intArrayOf(R.string.hamlet_haptics, R.string.hamlet_night, R.string.hamlet_effects)
        val keys = arrayOf("haptics", "night", "effects")
        fun value(i: Int) = when (i) { 0 -> village.haptics; 1 -> village.renderer.nightEnabled; else -> village.renderer.effectsEnabled }
        fun fill() {
            sheet.body.removeAllViews()
            for (i in labels.indices) sheet.body.addView(HamletUi.row(this, getString(labels[i]), value(i), if (value(i)) "tick" else "cross") {
                val checked = !value(i)
                prefs.edit().putBoolean(keys[i], checked).apply()
                when (i) {
                    0 -> village.haptics = checked
                    1 -> village.renderer.nightEnabled = checked
                    else -> { village.renderer.effectsEnabled = checked; HamletMotion.enabled = checked }
                }
                fill()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        }
        fill(); sheet.show()
    }
    /** A question whose answer button runs [action]; an impossible action simply does nothing. */
    private fun confirm(title: String, message: String, glyph: String, action: () -> Boolean) {
        HamletSheet.confirm(this, title, message, glyph = glyph) { action() }
    }
    private fun showWorlds() {
        HamletSheet.confirm(this, getString(R.string.hamlet_worlds), getString(R.string.hamlet_worlds_help),
            getString(if (creative) R.string.hamlet_progression_world else R.string.hamlet_creative_world), "star") { switchWorld() }
    }
    private fun guideToQuest(q: Quest) {
        val target = when (q.key) {
            "planted" -> game.objs.values.firstOrNull { it.def.cat == Cat.FIELD && it.crop == null }
            "harvested" -> game.objs.values.firstOrNull { game.status(it) == Status.RIPE }
                ?: game.objs.values.firstOrNull { it.def.cat == Cat.FIELD }
            "taxes" -> game.objs.values.firstOrNull { game.status(it) == Status.TAX }
                ?: game.objs.values.firstOrNull { it.def.cat == Cat.HOME }
            "accepted", "delivered" -> game.objs.values.firstOrNull { it.def.cat == Cat.SHOP && game.status(it) in setOf(Status.ORDER_NEW, Status.ORDER_DONE) }
            else -> null
        }
        if (target != null) {
            open(HamletModule.VILLAGE); village.browse(); village.renderer.centerOn(target.x + target.w / 2f, target.y + target.d / 2f)
            onTapObject(target); return
        }
        when {
            q.key.startsWith("built_") -> Hamlet.byId[q.key.removePrefix("built_")]?.let { build(it) }
            q.key == "castle" || q.key == "hall" -> upgradeHall()
            q.key == "expanded" -> expandLand()
            q.key == "gifts" -> game.openGift()
            q.key == "work_used" -> open(HamletModule.JOURNAL, HamletJournalPage.WORK)
            else -> HamletCatalogDialog(this).show()
        }
    }
    private fun setPhotoMode(enabled: Boolean) {
        if (enabled && module != HamletModule.VILLAGE) open(HamletModule.VILLAGE)
        selected = null; village.browse(); village.photoMode = enabled; village.renderer.photoMode = enabled
        val chrome = if (enabled) View.GONE else View.VISIBLE
        top.visibility = chrome; dock.visibility = chrome
        for (i in 1 until villagePage.childCount) villagePage.getChildAt(i).visibility = chrome
        photoTools.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) village.renderer.daylightOverride = null
        actionSignature = ""; questReady = null; refresh()
    }
    private fun showLighting() {
        val labels = intArrayOf(R.string.hamlet_photo_auto, R.string.hamlet_photo_dawn, R.string.hamlet_photo_day, R.string.hamlet_photo_dusk, R.string.hamlet_photo_night)
        val values = arrayOf(null, .65f, 1f, .4f, .08f)
        HamletSheet.pick(this, getString(R.string.hamlet_photo_light), labels.map { getString(it) }, values.indexOf(village.renderer.daylightOverride),
            listOf("clock", "sun", "sun", "lantern", "star")) { i -> village.renderer.daylightOverride = values[i] }
    }
    private fun refreshWorld() {
        selected = null; village.browse(); village.art.clear(); village.renderer.villagers.clear(); village.renderer.floaters.clear(); village.home(); actionSignature = ""; refresh()
    }
    internal fun applyImportedWorld(prepared: HamletTransfer.Prepared) {
        handler.removeCallbacks(delayedSave)
        if (!unreadableSave && !save()) return
        val previous = HamletSave.toJson(game)
        game.listener = null
        val ok = HamletTransfer.apply(game, prepared, acceptWarnings = true)
        // Import targets the current slot, so its play mode remains explicit and predictable.
        game.sandbox = creative
        if (!ok || !HamletSave.write(filesDir, game, creative)) {
            HamletSave.fromJson(game, previous); game.listener = this
            toast(getString(R.string.hamlet_realms_file_error)); return
        }
        unreadableSave = false; game.listener = this; refreshWorld(); toast(getString(R.string.hamlet_realms_import_ok))
    }
    private fun switchWorld() {
        handler.removeCallbacks(delayedSave)
        if (!save()) return
        val target = !creative
        game.listener = null
        val loaded = HamletSave.read(filesDir, game, target)
        if (!loaded && HamletSave.file(filesDir, target).exists()) {
            game.listener = this; toast(getString(R.string.hamlet_save_failed)); return
        }
        if (!loaded) game.startNew(target)
        creative = target; prefs.edit().putBoolean("creative", creative).apply()
        game.listener = this; selected = null; village.browse(); village.art.clear()
        village.renderer.villagers.clear(); village.renderer.floaters.clear(); village.home()
        save(); refresh()
    }
    private fun save(): Boolean {
        if (unreadableSave) return false
        val ok = !::game.isInitialized || HamletSave.write(filesDir, game, creative)
        if (!ok) toast(getString(R.string.hamlet_save_failed))
        return ok
    }
    /** Explicit Exit and Android Back share the same successful-save requirement. */
    internal fun exitGame(): Boolean {
        handler.removeCallbacks(delayedSave)
        if (unreadableSave) { finish(); return true }
        if (!save()) return false
        resumed = false; village.stop(); page()?.hide(); handler.removeCallbacks(loop)
        finish()
        return true
    }
    internal fun itemName(id: String) = stringByName(if (id == "hall") "hamlet_n_hall${game.hall}" else "hamlet_n_$id")
    internal fun stringByName(name: String): String {
        val id = resources.getIdentifier(name, "string", packageName)
        return if (id == 0) getString(R.string.hamlet_title) else getString(id)
    }
    internal fun duration(ms: Long): String {
        if (ms < 60_000) return getString(R.string.hamlet_duration_seconds, (ms.coerceAtLeast(0) + 999) / 1000)
        val m = (ms.coerceAtLeast(0) + 59_999) / 60_000
        return if (m >= 60) getString(R.string.hamlet_duration_hours, m / 60, m % 60) else getString(R.string.hamlet_duration_minutes, m)
    }
    internal fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun tick() { if (::village.isInitialized && village.haptics) village.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    private fun button(label: Int, glyph: String? = null, weight: Weight = Weight.NORMAL, onDark: Boolean = false, click: () -> Unit) =
        HamletUi.button(this, getString(label), weight, onDark, glyph) { tick(); click() }
    private fun text(value: CharSequence) = TextView(this).apply {
        text = value; textSize = HamletUi.TEXT; setTextColor(HamletUi.INK); setLineSpacing(dp(3).toFloat(), 1f); setPadding(0, dp(4), 0, dp(4))
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    internal fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
