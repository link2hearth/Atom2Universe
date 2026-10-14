package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
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

/** Le village garde tout l'écran ; les catalogues et la gestion s'ouvrent à la demande. */
class HamletActivity : ThemedActivity(), HamletViewHost, HamletListener {
    /** The whole game reads a little larger, clearly larger on tablets. */
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(HamletUi.larger(newBase))
    internal lateinit var game: HamletGame
    internal lateinit var village: HamletView
    private lateinit var stats: TextView
    private val resourceChips = ArrayList<TextView>()
    private lateinit var selectedLabel: TextView
    private lateinit var objectActions: LinearLayout
    private lateinit var actionScroll: View
    private lateinit var progress: ProgressBar
    private lateinit var questButton: Button
    private lateinit var collectButton: Button
    private var selected: Obj? = null
    private var actionSignature = ""
    private var resumed = false
    private var creative = false
    private var unreadableSave = false
    private var adventureOpen = false
    private var journeyReady: Boolean? = null
    private val chrome = ArrayList<View>()
    private lateinit var photoTools: View
    private lateinit var worldFiles: HamletWorldFiles
    private val prefs by lazy { getSharedPreferences("hamlet_ui", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val delayedSave = Runnable { save() }
    private val refreshSoon = Runnable { refresh() }
    private val loop = object : Runnable {
        override fun run() {
            if (!resumed) return
            game.tick(); refresh(); handler.postDelayed(this, 1_000L)
        }
    }

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
        game.listener = this
        val root = column().apply { setBackgroundColor(HamletUi.FOREST) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(5), dp(10), dp(3)) }
        val heading = column()
        heading.addView(TextView(this).apply { setText(R.string.hamlet_title); textSize = 20f; setTextColor(HamletUi.PAPER); setTypeface(null, android.graphics.Typeface.BOLD) })
        stats = TextView(this).apply { textSize = 11f; setTextColor(0xFFD0DACA.toInt()) }
        heading.addView(stats)
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button(R.string.hamlet_home) { village.home() }.apply {
            text = ""; contentDescription = getString(R.string.hamlet_ui_center)
            setCompoundDrawablesWithIntrinsicBounds(HamletUi.icon(this@HamletActivity, "map", 23), null, null, null)
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(6) })
        header.addView(button(R.string.hamlet_ui_exit) { exitGame() }.apply {
            contentDescription = getString(R.string.hamlet_ui_exit_description)
            background = HamletUi.control(this@HamletActivity, quiet = true)
        }, LinearLayout.LayoutParams(-2, dp(48)))
        root.addView(header)
        val supplies = LinearLayout(this).apply { setPadding(dp(10), dp(3), dp(10), dp(7)) }
        for (glyph in arrayOf("coin", "gem", "hat", "flower")) {
            val chip = TextView(this).apply {
                textSize = 12f; gravity = Gravity.CENTER_VERTICAL; setTextColor(HamletUi.PAPER)
                setPadding(dp(6), dp(5), dp(6), dp(5)); background = rounded(0xFF305347.toInt(), 9)
                setCompoundDrawablesWithIntrinsicBounds(HamletUi.icon(this@HamletActivity, glyph, 16), null, null, null)
                compoundDrawablePadding = dp(4); isSingleLine = true
                setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }
            resourceChips.add(chip)
            supplies.addView(chip, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) })
        }
        root.addView(supplies); chrome.add(supplies)
        root.addView(village, LinearLayout.LayoutParams(-1, 0, 1f))
        val panel = column().apply { setPadding(dp(14), dp(9), dp(14), dp(8)); background = HamletUi.panel(this@HamletActivity, HamletUi.PAPER, 16, 0xFFD7C7A5.toInt()) }
        selectedLabel = TextView(this).apply { textSize = 13f; setTextColor(HamletUi.INK); setLineSpacing(dp(2).toFloat(), 1f) }
        panel.addView(selectedLabel)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(0xFF668D51.toInt())
        }
        panel.addView(progress, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(5) })
        objectActions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actionScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(objectActions) }
        panel.addView(actionScroll, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(panel); chrome.add(panel)
        val tools = LinearLayout(this).apply { setPadding(dp(6), dp(5), dp(6), dp(6)) }
        fun tool(b: Button) { tools.addView(b, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(2); marginEnd = dp(2) }) }
        fun nav(label: Int, glyph: String, action: () -> Unit) = HamletUi.navigation(this, getString(label), glyph) {
            if (village.haptics) village.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            action()
        }
        tool(nav(R.string.hamlet_ui_build, "crate") { HamletCatalogDialog(this).show() })
        collectButton = nav(R.string.hamlet_ui_collect, "sack") { toast(getString(R.string.hamlet_round_result, game.collectAll())) }; tool(collectButton)
        tool(nav(R.string.hamlet_ui_adventure, "sword") { showAdventure() })
        questButton = nav(R.string.hamlet_ui_journal, "book") { showStewardship() }; tool(questButton)
        tool(nav(R.string.hamlet_ui_manage, "gear") { showVillage() })
        root.addView(tools); chrome.add(tools)
        photoTools = LinearLayout(this).apply {
            gravity = Gravity.CENTER; visibility = View.GONE
            addView(button(R.string.hamlet_photo_light) { showLighting() })
            addView(button(R.string.hamlet_photo_capture) { worldFiles.capture() })
            addView(button(R.string.hamlet_photo_return) { setPhotoMode(false) })
        }
        root.addView(photoTools); setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (village.photoMode) setPhotoMode(false)
                else if (village.mode !is HamletMode.Browse) village.browse()
                else if (selected != null) onTapEmpty()
                else exitGame()
            }
        })
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        village.post {
            village.home()
            if (unreadableSave) AlertDialog.Builder(this).setTitle(R.string.hamlet_realms_import)
                .setMessage(R.string.hamlet_save_recovery).setCancelable(false)
                .setPositiveButton(R.string.hamlet_realms_import) { _, _ -> worldFiles.importWorld() }
                .setNegativeButton(R.string.hamlet_ui_exit) { _, _ -> finish() }.show()
            else if (!prefs.getBoolean("introduced", false)) { prefs.edit().putBoolean("introduced", true).apply(); showHelp() }
        }
        refresh()
    }
    override fun onPause() {
        resumed = false; village.stop(); handler.removeCallbacks(loop); handler.removeCallbacks(delayedSave)
        save(); super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        worldFiles.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume(); resumed = true
        if (::village.isInitialized && !adventureOpen) village.start()
        handler.removeCallbacks(loop); handler.post(loop)
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); worldFiles.close(); village.stop(); village.art.clear(); game.listener = null; super.onDestroy()
    }
    override fun onTapObject(o: Obj) {
        selected = o
        if (village.mode is HamletMode.Browse) game.collectReady(o)
        refresh()
    }
    override fun onTapVillager(v: Villager) {
        val roles = intArrayOf(R.string.hamlet_role_resident, R.string.hamlet_role_farmer, R.string.hamlet_role_artisan, R.string.hamlet_role_guard, R.string.hamlet_role_shepherd)
        AlertDialog.Builder(this).setTitle(getString(R.string.hamlet_resident_name, Hamlet.visitors[v.nameIndex], getString(roles[v.role])))
            .setMessage(getString(R.string.hamlet_resident_story, game.happiness()))
            .setPositiveButton(R.string.hamlet_daily) { _, _ -> showDaily() }
            .setNegativeButton(android.R.string.cancel, null).show()
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
        if (!::stats.isInitialized) return
        collectButton.visibility = if (game.canCollectAll()) View.VISIBLE else View.GONE
        collectButton.isEnabled = game.collectableCount() > 0
        val realmName = game.realms.name(game.realms.current).ifBlank { getString(R.string.hamlet_realms_default_name, game.realms.current) }
        stats.text = getString(R.string.hamlet_ui_realm_level, realmName, if (creative) getString(R.string.hamlet_ui_creative) else getString(R.string.hamlet_ui_level, game.level()))
        val values = longArrayOf(game.coins.toLong(), game.gems.toLong(), game.population().toLong(), game.happiness().toLong())
        val descriptions = intArrayOf(R.string.hamlet_ui_coins, R.string.hamlet_ui_gems, R.string.hamlet_ui_people, R.string.hamlet_ui_happiness)
        resourceChips.forEachIndexed { i, chip ->
            chip.text = getString(if (i == 3) R.string.hamlet_ui_percent else R.string.hamlet_ui_number, values[i])
            chip.contentDescription = getString(descriptions[i], values[i])
            chip.tooltipText = chip.contentDescription
        }
        val rewardReady = game.currentQuest()?.let { game.questDone(it) } == true || game.journey.canClaim() || game.stewardship.hasRewards() || (game.journey.expedition >= 0 && game.journey.expeditionLeft() == 0L)
        if (journeyReady != rewardReady) {
            journeyReady = rewardReady
            questButton.text = getString(if (rewardReady) R.string.hamlet_ui_ready else R.string.hamlet_ui_journal)
            questButton.background = HamletUi.control(this, selected = rewardReady, quiet = !rewardReady)
            questButton.setTextColor(if (rewardReady) HamletUi.INK else HamletUi.PAPER)
        }
        val o = selected?.takeIf { game.objs[it.id] === it }
        selected = o; village.renderer.selected = if (village.mode is HamletMode.Browse) o else null
        val placing = village.mode is HamletMode.Place; val brushing = village.mode is HamletMode.Brush
        val status = o?.let { game.status(it) }; val left = o?.let { remaining(it) } ?: 0L
        selectedLabel.text = when {
            placing -> getString(if (village.renderer.ghostOk) R.string.hamlet_placing else R.string.hamlet_invalid_placement)
            brushing -> getString(R.string.hamlet_brushing)
            village.mode is HamletMode.Bulldoze -> getString(R.string.hamlet_tools_bulldoze_hint)
            village.mode is HamletMode.Eyedropper -> getString(R.string.hamlet_tools_pick_hint)
            village.mode is HamletMode.Redesign -> getString(R.string.hamlet_tools_move_hint)
            village.mode is HamletMode.Fill -> getString(R.string.hamlet_tools_fill_hint)
            o == null -> game.currentQuest()?.let { getString(R.string.hamlet_ui_next_goal, stringByName("hamlet_q${game.quest}_d"), game.questProgress(it).coerceAtMost(it.n.toLong()), it.n) } ?: getString(R.string.hamlet_hint)
            else -> getString(R.string.hamlet_object_line, itemName(o.t), o.tier, stringByName("hamlet_status_${status!!.name.lowercase()}")) +
                if (left > 0) getString(R.string.hamlet_remaining, duration(left)) else ""
        }
        progress.visibility = if (left > 0 && !placing && !brushing) View.VISIBLE else View.GONE
        if (o != null && left > 0) {
            val total = when (status) {
                Status.BUILDING -> o.def.build * 1000L
                Status.GROWING -> game.cropOf(o)!!.time * 1000L
                Status.ORDER_BUSY -> o.def.job * 1000L
                Status.PRODUCING -> HamletLife.produce.getValue(o.t).seconds * 1000L
                else -> o.def.period * 1000L
            }
            progress.progress = ((1 - left.toDouble() / total.coerceAtLeast(1)) * 1000).toInt().coerceIn(0, 1000)
        }
        val sig = listOf(village.mode::class, o?.id, status, o?.crop, o?.tier, o?.tended, o?.let { game.hurryCost(it) }, game.workSeconds > 0, village.renderer.ghostOk, game.buildHistory.canUndo(), game.quest).joinToString()
        if (sig == actionSignature) return
        actionSignature = sig; objectActions.removeAllViews()
        if (placing) {
            addAction(R.string.hamlet_rotate) { village.rotateGhost() }
            addAction(R.string.hamlet_confirm) {
                if (village.confirmPlacement() && village.haptics) village.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }.isEnabled = village.renderer.ghostOk
            addAction(R.string.hamlet_cancel) { village.browse() }
        } else if (village.mode !is HamletMode.Browse) {
            addAction(R.string.hamlet_cancel) { village.browse() }
            if (game.buildHistory.canUndo()) addAction(R.string.hamlet_tools_undo) { village.undoBuild() }
        }
        else if (o != null) objectActionButtons(o)
        else {
            addAction(R.string.hamlet_ui_goal) { showQuest() }
            addAction(R.string.hamlet_work) { HamletJourneyDialog(this).show(4) }
            addAction(R.string.hamlet_tools_title) { showBuilderTools() }
            if (game.buildHistory.canUndo()) addAction(R.string.hamlet_tools_undo) { village.undoBuild() }
        }
        actionScroll.visibility = if (objectActions.childCount > 0) View.VISIBLE else View.GONE
    }
    private fun remaining(o: Obj) = when (game.status(o)) {
        Status.BUILDING -> game.buildLeft(o)
        Status.GROWING -> game.cropLeft(o)
        Status.ORDER_BUSY -> game.jobLeft(o)
        Status.PRODUCING -> game.productLeft(o)
        Status.NONE -> if (o.def.cat == Cat.HOME) game.taxLeft(o) else 0L
        else -> 0L
    }
    private fun addAction(label: Int, click: () -> Unit) = button(label, click).also {
        objectActions.addView(it, LinearLayout.LayoutParams(-2, dp(46)).apply { marginEnd = dp(6); topMargin = dp(2) })
    }
    private fun objectActionButtons(o: Obj) {
        if (o.t in HamletAdventure.FACILITIES && game.buildLeft(o) == 0L) {
            addAction(R.string.hamlet_s_facility_open) { showAdventure(when (o.t) {
                "adventureInn" -> 1
                "adventureForge" -> 3
                "adventureGate" -> 0
                else -> 4
            }) }
        }
        when (game.status(o)) {
            Status.RIPE, Status.WITHERED -> addAction(R.string.hamlet_harvest) { game.harvest(o) }
            Status.TAX -> addAction(R.string.hamlet_collect) { game.collectTax(o) }
            Status.ORDER_NEW -> { addAction(R.string.hamlet_accept) { game.acceptOrder(o) }; addAction(R.string.hamlet_decline) { game.declineOrder(o) } }
            Status.ORDER_DONE -> addAction(R.string.hamlet_deliver) { game.deliver(o) }
            Status.GIFT -> addAction(R.string.hamlet_gift) { game.openGift() }
            Status.FEED -> addAction(R.string.hamlet_feed) { confirm(getString(R.string.hamlet_feed_confirm, HamletLife.produce.getValue(o.t).feed)) { game.feed(o) } }
            Status.PRODUCT -> addAction(R.string.hamlet_products) { game.collectProduct(o) }
            else -> Unit
        }
        if (o.def.cat == Cat.FIELD && o.crop == null && game.buildLeft(o) == 0L) addAction(R.string.hamlet_plant) { showCropMenu(o) }
        if (o.crop != null && !o.tended && game.cropStage(o) < 2) addAction(R.string.hamlet_tend) {
            confirm(getString(R.string.hamlet_tend_confirm, game.tendCost(o))) { game.tend(o) }
        }
        if (game.workLeft(o) > 0) addAction(R.string.hamlet_work_apply) {
            if (!game.applyWork(o)) HamletJourneyDialog(this).show(4)
        }
        if (game.hurryCost(o) > 0) addAction(R.string.hamlet_hurry) { confirm(getString(R.string.hamlet_hurry_confirm, game.hurryCost(o))) { game.hurry(o) } }
        addAction(R.string.hamlet_info) { showObjectInfo(o) }
        if (game.canUpgrade(o)) addAction(R.string.hamlet_upgrade) { confirm(getString(R.string.hamlet_upgrade_confirm, o.tier + 1, game.upgradeCost(o))) { game.upgrade(o) } }
        if (o.def.cat != Cat.HALL) {
            addAction(R.string.hamlet_tools_duplicate) { selected = null; village.duplicate(o) }
            addAction(R.string.hamlet_move) { village.startPlacing(o.def, o) }
            addAction(R.string.hamlet_sell) { confirm(getString(R.string.hamlet_sell_confirm, itemName(o.t), game.sellValue(o))) { game.sell(o).also { if (it) selected = null } } }
        }
    }
    private fun showCropMenu(field: Obj) {
        val all = CheckBox(this).apply { setText(R.string.hamlet_plant_all); isChecked = prefs.getBoolean("plantAll", false) }
        val crops = Hamlet.crops.filter { it.lvl <= game.ulvl() && it.magic == field.def.magic }
        val labels = crops.map { getString(R.string.hamlet_crop_choice, itemName("c_${it.id}"), it.cost, it.earn, duration(it.time * 1000L)) }.toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.hamlet_plant).setView(all).setItems(labels) { _, which ->
            prefs.edit().putBoolean("plantAll", all.isChecked).apply()
            val n = if (all.isChecked) game.plantAll(crops[which].id) else if (game.plant(field, crops[which].id)) 1 else 0
            toast(getString(R.string.hamlet_planted_count, n))
        }.setNegativeButton(android.R.string.cancel, null).show()
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
        AlertDialog.Builder(this).setTitle(itemName(o.t)).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
    }
    private fun showVillage() {
        val actions = listOf<Pair<Int, () -> Unit>>(
            R.string.hamlet_s_today to { showStewardship() },
            R.string.hamlet_overview to ::showOverview,
            R.string.hamlet_realms_title to { HamletWorldsDialog(this, game, onChanged = ::refreshWorld, onSave = { save() }).show() },
            R.string.hamlet_ui_neighbors to ::showNeighbors,
            R.string.hamlet_chronicle_title to { HamletChronicleDialog(this, game).show() },
            R.string.hamlet_tools_title to ::showBuilderTools,
            R.string.hamlet_land to ::expandLand, R.string.hamlet_hall to ::upgradeHall,
            R.string.hamlet_daily to ::showDaily, R.string.hamlet_gift to ::claimGift,
            R.string.hamlet_photo_title to { setPhotoMode(true) },
            R.string.hamlet_realms_export to { worldFiles.exportWorld() },
            R.string.hamlet_realms_import to { worldFiles.importWorld() },
            R.string.hamlet_worlds to ::showWorlds,
            R.string.hamlet_settings to ::showSettings, R.string.hamlet_help to ::showHelp,
        )
        AlertDialog.Builder(this).setTitle(R.string.hamlet_village).setItems(actions.map { getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }.show()
    }
    private fun showOverview() {
        val q = game.currentQuest()
        val text = getString(R.string.hamlet_overview_detail, game.population(), game.happiness(), game.objs.values.count { it.def.cat == Cat.HOME },
            game.objs.values.count { it.def.cat == Cat.SHOP }, game.objs.values.count { it.def.cat == Cat.RANCH }, game.realmSize(),
            game.stats["harvested"] ?: 0, game.stats["delivered"] ?: 0, game.stats["products"] ?: 0, game.readyCount()) +
            if (q != null) getString(R.string.hamlet_next_goal, stringByName("hamlet_q${game.quest}_d"), game.questProgress(q), q.n) else ""
        AlertDialog.Builder(this).setTitle(R.string.hamlet_overview).setMessage(text).setPositiveButton(android.R.string.ok, null).show()
    }
    private fun showDaily() {
        game.ensureDaily()
        val labels = intArrayOf(R.string.hamlet_daily_harvest, R.string.hamlet_daily_products, R.string.hamlet_daily_orders)
        val list = column().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        list.addView(TextView(this).apply { text = getString(R.string.hamlet_daily_help, duration(Hamlet.DAY_MS - game.now() % Hamlet.DAY_MS)) })
        val dialog = AlertDialog.Builder(this).setTitle(R.string.hamlet_daily).setView(list).setNegativeButton(android.R.string.cancel, null).create()
        for (i in labels.indices) {
            val claimed = game.dailyClaimed and (1 shl i) != 0
            list.addView(Button(this).apply {
                isAllCaps = false
                text = getString(R.string.hamlet_daily_row, getString(labels[i]), game.dailyProgress(i).coerceAtMost(HamletLife.dailyTargets[i].toLong()), HamletLife.dailyTargets[i], HamletLife.dailyCoins[i]) +
                    if (claimed) getString(R.string.hamlet_daily_claimed) else ""
                isEnabled = !claimed && game.dailyProgress(i) >= HamletLife.dailyTargets[i]
                setOnClickListener { if (game.claimDaily(i)) { dialog.dismiss(); showDaily() } }
            })
        }
        dialog.show()
    }
    private fun claimGift() { if (!game.openGift()) toast(getString(R.string.hamlet_gift_wait, duration(Hamlet.DAY_MS - (game.now() - game.giftT)))) }
    internal fun showQuest() {
        val q = game.currentQuest() ?: run { toast(getString(R.string.hamlet_quests_done)); return }
        val title = stringByName("hamlet_q${game.quest}_t")
        val dialog = AlertDialog.Builder(this).setTitle(title).setMessage(getString(R.string.hamlet_quest_detail, title, stringByName("hamlet_q${game.quest}_d"), game.questProgress(q), q.n, q.coins, q.gems))
            .setNegativeButton(android.R.string.cancel, null)
        if (game.questDone(q)) dialog.setPositiveButton(R.string.hamlet_claim) { _, _ -> game.claimQuest() }
        else dialog.setPositiveButton(R.string.hamlet_ui_goal_go) { _, _ -> guideToQuest(q) }
        dialog.show()
    }
    private fun expandLand() {
        if (!game.canExpand()) { toast(getString(R.string.hamlet_land_max)); return }
        confirm(getString(R.string.hamlet_land_confirm, game.expandCost())) { game.expandLand() }
    }
    private fun upgradeHall() {
        val next = game.nextHall() ?: run { toast(getString(R.string.hamlet_hall_max)); return }
        if (game.ulvl() < next.lvl) { toast(getString(R.string.hamlet_unlock_level, next.lvl)); return }
        if (!game.journey.canUpgradeHall()) { toast(getString(R.string.hamlet_chapter_gate, game.journey.hallChapter(game.hall + 1))); return }
        confirm(getString(R.string.hamlet_hall_confirm, game.hall + 1, next.cost, next.realm)) { game.upgradeHall() }
    }
    private fun showHelp() { AlertDialog.Builder(this).setTitle(R.string.hamlet_help).setMessage(R.string.hamlet_help_text).setPositiveButton(android.R.string.ok, null).show() }
    private fun showSettings() {
        val labels = intArrayOf(R.string.hamlet_haptics, R.string.hamlet_night, R.string.hamlet_effects)
        val keys = arrayOf("haptics", "night", "effects")
        AlertDialog.Builder(this).setTitle(R.string.hamlet_settings)
            .setMultiChoiceItems(labels.map { getString(it) }.toTypedArray(), booleanArrayOf(village.haptics, village.renderer.nightEnabled, village.renderer.effectsEnabled)) { _, i, checked ->
                prefs.edit().putBoolean(keys[i], checked).apply()
                when (i) { 0 -> village.haptics = checked; 1 -> village.renderer.nightEnabled = checked; 2 -> village.renderer.effectsEnabled = checked }
            }.setPositiveButton(android.R.string.ok, null).show()
    }
    private fun confirm(message: String, action: () -> Boolean) {
        AlertDialog.Builder(this).setMessage(message).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> if (!action()) toast(getString(R.string.hamlet_action_failed)) }.show()
    }
    private fun showWorlds() {
        AlertDialog.Builder(this).setTitle(R.string.hamlet_worlds).setMessage(R.string.hamlet_worlds_help)
            .setPositiveButton(if (creative) R.string.hamlet_progression_world else R.string.hamlet_creative_world) { _, _ -> switchWorld() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun showNeighbors() {
        adventureOpen = true; village.stop()
        HamletNeighborsDialog(this, game, haptics = village.haptics, effectsEnabled = village.renderer.effectsEnabled,
            onClose = { adventureOpen = false; if (resumed && !isFinishing) village.start(); refresh(); save() }).show()
    }
    private fun showStewardship() {
        HamletStewardshipDialog(this, game, onAdventure = ::showAdventure,
            onJournal = { tab -> HamletJourneyDialog(this).show(tab) }).show()
    }
    private fun showAdventure(initialTab: Int = 0) {
        adventureOpen = true; village.stop()
        HamletAdventureDialog(this, game, village.haptics, onBuild = { d -> selected = null; village.startPlacing(d) },
            onClose = { adventureOpen = false; if (resumed && !isFinishing) village.start(); refresh(); save() },
            onExit = { exitGame() }, effectsEnabled = village.renderer.effectsEnabled).show(initialTab)
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
            village.browse(); village.renderer.centerOn(target.x + target.w / 2f, target.y + target.d / 2f)
            onTapObject(target); return
        }
        when {
            q.key.startsWith("built_") -> Hamlet.byId[q.key.removePrefix("built_")]?.let { selected = null; village.startPlacing(it) }
            q.key == "castle" || q.key == "hall" -> upgradeHall()
            q.key == "expanded" -> expandLand()
            q.key == "gifts" -> claimGift()
            q.key == "work_used" -> HamletJourneyDialog(this).show(4)
            else -> HamletCatalogDialog(this).show()
        }
    }
    private fun showBuilderTools() {
        val actions = listOf<Pair<Int, () -> Unit>>(
            R.string.hamlet_tools_undo to { village.undoBuild() },
            R.string.hamlet_tools_pick to { selected = null; village.startEyedropper() },
            R.string.hamlet_tools_move to { selected = null; village.startRedesign() },
            R.string.hamlet_tools_bulldoze to { selected = null; village.startBulldozer() },
            R.string.hamlet_tools_recent to ::showRecent,
            R.string.hamlet_tools_water to { confirm(getString(R.string.hamlet_tools_water_confirm)) { game.floodRealm(true); true } },
            R.string.hamlet_tools_drain to { confirm(getString(R.string.hamlet_tools_drain_confirm)) { game.floodRealm(false); true } },
        )
        AlertDialog.Builder(this).setTitle(R.string.hamlet_tools_title).setItems(actions.map { getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }.show()
    }
    private fun showRecent() {
        val recent = game.buildHistory.recent
        if (recent.isEmpty()) { toast(getString(R.string.hamlet_tools_recent_empty)); return }
        AlertDialog.Builder(this).setTitle(R.string.hamlet_tools_recent).setItems(recent.map(::itemName).toTypedArray()) { _, i -> selected = null; village.selectRecent(i) }.show()
    }
    private fun setPhotoMode(enabled: Boolean) {
        selected = null; village.browse(); village.photoMode = enabled; village.renderer.photoMode = enabled
        chrome.forEach { it.visibility = if (enabled) View.GONE else View.VISIBLE }
        photoTools.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) village.renderer.daylightOverride = null
        actionSignature = ""; refresh()
    }
    private fun showLighting() {
        val labels = intArrayOf(R.string.hamlet_photo_auto, R.string.hamlet_photo_dawn, R.string.hamlet_photo_day, R.string.hamlet_photo_dusk, R.string.hamlet_photo_night)
        AlertDialog.Builder(this).setTitle(R.string.hamlet_photo_light).setItems(labels.map { getString(it) }.toTypedArray()) { _, i ->
            village.renderer.daylightOverride = arrayOf(null, .65f, 1f, .4f, .08f)[i]
        }.show()
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
        resumed = false; village.stop(); handler.removeCallbacks(loop)
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
    private fun button(label: Int, click: () -> Unit) = HamletUi.button(this, getString(label)) {
        if (::village.isInitialized && village.haptics) village.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        click()
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    internal fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
