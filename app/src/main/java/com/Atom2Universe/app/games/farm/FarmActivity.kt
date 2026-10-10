package com.Atom2Universe.app.games.farm

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.launch

class FarmActivity : ThemedActivity() {
    private lateinit var state: FarmState
    private lateinit var sprites: FarmSprites
    private lateinit var root: FrameLayout
    private lateinit var fieldPanel: LinearLayout
    private lateinit var livestockPanel: LinearLayout
    private lateinit var habitat: LivestockHabitatView
    private lateinit var habitatTitle: TextView
    private lateinit var habitatCount: TextView
    private lateinit var habitatPrevious: Button
    private lateinit var habitatNext: Button
    private lateinit var habitatManage: Button
    private lateinit var habitatCollect: Button
    private lateinit var habitatGreet: Button
    private lateinit var fieldView: FieldArcadeView
    private lateinit var fieldInfo: TextView
    private lateinit var fuelTrack: FrameLayout
    private lateinit var fuelFill: View
    private val fuelDrawable = GradientDrawable().apply { setColor(Color.rgb(126, 187, 90)); cornerRadius = 999f }
    private lateinit var world: FarmWorldView
    private lateinit var actionEffects: FarmActionEffectsView
    private var actionOrigin: PointF? = null
    private var shownCoins: Long? = null
    private lateinit var toolbar: LinearLayout
    private lateinit var balance: TextView
    private lateinit var selection: FarmArtView
    private lateinit var produceIcon: FarmArtView
    private lateinit var manureIcon: FarmArtView
    private lateinit var regionIcon: FarmArtView
    private lateinit var villageEntry: LinearLayout
    private lateinit var villageSummary: TextView
    private val villageUi = mutableListOf<() -> Unit>()
    private lateinit var visitDock: LinearLayout
    private lateinit var visitSummaryText: TextView
    private val visitUi = mutableListOf<() -> Unit>()
    private val workshopUi = mutableListOf<() -> Unit>()
    private var workshopCategory = FarmRecipeCategory.GARDEN
    private var favoriteRecipesOnly = false
    private val greenhouseUi = mutableListOf<() -> Unit>()
    private lateinit var status: TextView
    private var bubble: LinearLayout? = null
    private val livestockUi = mutableListOf<() -> Unit>()
    private lateinit var seedGroup: LinearLayout
    private var bubbleFeedback: TextView? = null
    private val purchases = mutableListOf<Pair<Button, Long>>()
    private val stockLabels = mutableListOf<Pair<TextView, FarmCrop>>()
    private val produceSellSelection = mutableMapOf<Pair<FarmCrop, FarmCropQuality>, Int>()
    /** True once the cloud farm has replaced this one on disk - see [rebuildOnCloudFarm]. */
    private var farmSuperseded = false
    private var stepRepeat: Runnable? = null
    private var stepRepeatDelay = 260L
    private var stepRepeatStarted = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideStatus = Runnable { status.visibility = View.GONE }
    private val palette by lazy { com.Atom2Universe.app.games.kit.KitPalette.from(this) }
    private val ink get() = palette.text
    private val cream get() = palette.surface
    private val sage get() = palette.raised
    private val border get() = palette.outline
    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        state = FarmState(getSharedPreferences(FarmState.PREFS, MODE_PRIVATE))
        sprites = FarmSprites(this)
        root = FrameLayout(this)
        world = FarmWorldView(this, state, ::interact, ::parcelMenu, ::removePlant, ::debrisCleared, ::watered, ::harvested)
        world.dismissBubble = { if (bubble != null) { closeBubble(); true } else false }
        world.onLivestockPen = ::livestockPen
        world.onRegionTap = { message(getString(world.region.description)) }
        world.onGreenhouseTap = { showGreenhouse(it) }
        world.onVisitorTap = { showEncounters() }
        world.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) rememberActionOrigin(view, event.x, event.y)
            false
        }
        world.onProjectTap = { showProjects(it) }
        world.onDecorationTap = { showDecorations(it) }
        world.onWorkshopTap = { showWorkshop() }
        world.onBushBonus = { gained -> message(getString(R.string.farm_bush_bonus, money(gained))); refresh() }
        root.addView(world, FrameLayout.LayoutParams(-1, -1))
        fieldPanel = column().apply { visibility = View.GONE; setBackgroundColor(sage) }
        fieldInfo = text("", 14, true).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(4)) }
        fieldPanel.addView(fieldInfo)
        fuelTrack = FrameLayout(this).apply { background = rounded(palette.raised, 8, border) }
        fuelFill = View(this).apply { background = fuelDrawable; pivotX = 0f }
        fuelTrack.addView(fuelFill, FrameLayout.LayoutParams(-1, dp(10)))
        fieldPanel.addView(fuelTrack, LinearLayout.LayoutParams(-1, dp(10)).apply {
            leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(6)
        })
        fieldView = FieldArcadeView(this, state) { refresh() }
        fieldView.dismissBubble = { if (bubble != null) { closeBubble(); true } else false }
        fieldPanel.addView(fieldView, LinearLayout.LayoutParams(-1, 0, 1f))
        fieldPanel.addView(text(getString(R.string.farm_field_steer), 12).apply { gravity = Gravity.CENTER; setPadding(dp(10), dp(4), dp(10), dp(4)) })
        root.addView(fieldPanel, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(74) })
        livestockPanel = column().apply { visibility = View.GONE; setBackgroundColor(cream) }
        habitat = LivestockHabitatView(this, sprites, state.livestock).apply {
            onOpen = { livestockPen(it, true) }
            onAnimal = { showAnimal(it) }
            dismissBubble = { if (bubble != null) { closeBubble(); true } else false }
            onPageChanged = { refreshHabitat() }
        }
        val habitatNavigation = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(2), dp(8), dp(2)) }
        habitatPrevious = button(getString(R.string.farm_page_previous_symbol)) {
            LivestockKind.entries.getOrNull(habitat.selected.ordinal - 1)?.let { habitat.select(it) }
        }.apply { contentDescription = getString(R.string.farm_page_previous) }
        habitatNext = button(getString(R.string.farm_page_next_symbol)) {
            LivestockKind.entries.getOrNull(habitat.selected.ordinal + 1)?.let { habitat.select(it) }
        }.apply { contentDescription = getString(R.string.farm_page_next) }
        habitatTitle = text("", 18, true).apply { gravity = Gravity.CENTER; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        habitatNavigation.addView(habitatPrevious, LinearLayout.LayoutParams(dp(48), dp(48)))
        habitatNavigation.addView(habitatTitle, LinearLayout.LayoutParams(0, -2, 1f))
        habitatNavigation.addView(habitatNext, LinearLayout.LayoutParams(dp(48), dp(48)))
        livestockPanel.addView(habitatNavigation)
        habitatCount = text("", 13).apply { gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), dp(6)) }
        livestockPanel.addView(habitatCount)
        livestockPanel.addView(habitat, LinearLayout.LayoutParams(-1, 0, 1f))
        val animalActions = LinearLayout(this).apply { setPadding(dp(12), dp(4), dp(12), 0) }
        habitatCollect = button("") { collectAnimals(habitat.selected) }
        habitatGreet = button(getString(R.string.farm_companion_greet)) { greetAnimals(habitat.selected) }
        animalActions.addView(habitatCollect, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        animalActions.addView(habitatGreet, LinearLayout.LayoutParams(0, -2, 1f))
        livestockPanel.addView(animalActions)
        habitatManage = button(getString(R.string.farm_herd_open)) { livestockPen(habitat.selected, true) }
        livestockPanel.addView(habitatManage, LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(dp(12), dp(5), dp(12), 0) })
        livestockPanel.addView(text(getString(R.string.farm_page_swipe), 12).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(4), dp(8), dp(6)) })
        root.addView(livestockPanel, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(74) })
        toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(cream, 22, border)
            elevation = dp(6).toFloat()
        }
        toolbar.addView(icon(FarmArtView.Kind.BACK, R.string.farm_back) { if (bubble != null) closeBubble() else finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val purse = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        purse.addView(icon(FarmArtView.Kind.SHOP, R.string.farm_shop) { shop() }, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            rightMargin = dp(4)
        })
        purse.addView(FarmArtView(this, FarmArtView.Kind.COIN), LinearLayout.LayoutParams(dp(28), dp(36)))
        balance = text("", 16, true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
        purse.addView(balance, LinearLayout.LayoutParams(0, -2, 1f))
        // Hidden dev entry point: long-press the balance, never a visible button reachable in normal play.
        purse.setOnLongClickListener { cheatMenu(); true }
        toolbar.addView(purse, LinearLayout.LayoutParams(0, -2, 1f))
        // A small sub-group: the seed bag (opens the picker) beside a plain crop icon for whatever is
        // currently selected - gold border there, unlike the action icons, since it shows a state.
        seedGroup = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = rounded(sage, 16)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        seedGroup.addView(icon(FarmArtView.Kind.SEEDS, R.string.farm_inventory) { inventory() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        selection = icon(FarmArtView.Kind.CROP, R.string.farm_inventory) { inventory() }.apply {
            background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(palette.raised, 14, palette.accent), null)
        }
        seedGroup.addView(selection, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(2) })
        // Wrapped rather than a fixed 92dp: the selected-seed icon beside the bag disappears once
        // that seed runs out, and a fixed width would leave half a sage pill sitting empty.
        toolbar.addView(seedGroup, LinearLayout.LayoutParams(-2, dp(48)).apply { leftMargin = dp(2) })
        produceIcon = icon(FarmArtView.Kind.CRATE, R.string.farm_produce_inventory) { produceInventory() }
        toolbar.addView(produceIcon, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(2) })
        world.harvestTarget = {
            val icon = IntArray(2); val view = IntArray(2)
            produceIcon.getLocationOnScreen(icon); world.getLocationOnScreen(view)
            PointF(icon[0] - view[0] + produceIcon.width / 2f, icon[1] - view[1] + produceIcon.height / 2f)
        }
        // Between the seed bag and the watering can: what goes in the ground, then what starts it.
        manureIcon = icon(FarmArtView.Kind.MANURE, R.string.farm_manure_title) { manurePit() }
        toolbar.addView(manureIcon, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(2) })
        regionIcon = icon(FarmArtView.Kind.MAP, R.string.farm_regions) { chooseRegion() }
        toolbar.addView(regionIcon, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(toolbar, FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP).apply {
            setMargins(dp(10), dp(8), dp(10), 0)
        })
        visitDock = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val villageLink = visitDockEntry(FarmArtView.Kind.VILLAGE, R.string.farm_village_title) { showVillage() }
        villageEntry = villageLink.first; villageSummary = villageLink.second
        visitDock.addView(villageEntry, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        val visitLink = visitDockEntry(FarmArtView.Kind.WATER, R.string.farm_visit_title) { showVisit() }
        visitSummaryText = visitLink.second
        visitDock.addView(visitLink.first, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
        root.addView(visitDock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(12))
        })
        status = text("", 13).apply {
            background = rounded(cream, 16, border)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            elevation = dp(5).toFloat(); visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(20), dp(76), dp(20), 0)
        })
        actionEffects = FarmActionEffectsView(this)
        root.addView(actionEffects, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        enableImmersiveMode()
        ViewCompat.requestApplyInsets(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (bubble != null) closeBubble() else finish() }
        })
        refresh()
        val restoredHabitat = savedInstanceState?.getInt("farm_livestock_page", 0) ?: 0
        habitat.select(LivestockKind.entries[restoredHabitat.coerceIn(0, LivestockKind.entries.lastIndex)], false)
        val regionName = savedInstanceState?.getString("farm_region")
        FarmRegion.entries.firstOrNull { it.name == regionName && state.regionUnlocked(it) }?.let { region ->
            world.post { world.switchRegion(region); refresh() }
        }
        if (pendingRestoreNotice) { pendingRestoreNotice = false; message(getString(R.string.farm_sync_restored)) }
        syncOnOpen()
    }

    /**
     * Asks the cloud whether another device has played. Drive never announces anything on its own,
     * so opening the game is the moment we go and look - and the only moment the answer matters.
     *
     * The save is read here, synchronously, before the download starts: it is the farm the player
     * is about to see. Handing it to the sync tells apart a farm nobody touched from one played on
     * while the answer was travelling.
     */
    private fun syncOnOpen() {
        val openedWith = getSharedPreferences(FarmState.PREFS, MODE_PRIVATE)
            .getString(FarmState.KEY_STATE, null)
        lifecycleScope.launch {
            when (val result = FarmSyncManager.onFarmOpened(this@FarmActivity, openedWith)) {
                FarmSyncManager.OpenResult.Idle -> Unit
                // The farm on disk is no longer the one the views were built from: rebuild rather
                // than patch, since the world, the fields and the pens all hold the old state.
                FarmSyncManager.OpenResult.Applied -> rebuildOnCloudFarm()
                is FarmSyncManager.OpenResult.Conflict -> chooseFarm(result)
            }
        }
    }

    /**
     * Two farms, both real. No rule can merge them - coins spent here and animals bought there do
     * not add up - so the player picks, with the numbers that tell the two apart in front of them.
     *
     * Closing the card without choosing keeps this device farm: nothing is lost, and the question
     * comes back at the next opening, since neither side has moved on.
     */
    private fun chooseFarm(conflict: FarmSyncManager.OpenResult.Conflict) {
        showBubble(getString(R.string.farm_sync_title)) { body ->
            body.addView(text(getString(R.string.farm_sync_body), 13).apply { setPadding(0, 0, 0, dp(10)) })
            body.addView(farmCard(getString(R.string.farm_sync_this_device), conflict.local) {
                FarmSyncManager.keepLocal(this, conflict.remote)
                closeBubble(); message(getString(R.string.farm_sync_kept_local))
            })
            val other = conflict.remote.deviceName.ifBlank { getString(R.string.farm_sync_other_device) }
            body.addView(farmCard(other, conflict.remote) {
                closeBubble()
                lifecycleScope.launch {
                    FarmSyncManager.keepRemote(this@FarmActivity, conflict.remote)
                    rebuildOnCloudFarm()
                }
            })
        }
    }

    /**
     * The cloud farm is on disk: rebuild the screen on top of it.
     *
     * Everything this instance still holds describes the farm that was just discarded, and saving
     * any of it would undo the choice - then publish the discarded farm at the next closing and
     * carry the mistake to the other device. So the object in memory is declared superseded, and
     * from here on this instance writes nothing, neither to disk nor to the cloud.
     */
    private fun rebuildOnCloudFarm() {
        farmSuperseded = true
        pendingRestoreNotice = true
        recreate()
    }

    /** One farm to choose from: whose it is, what it holds, and when it was left. */
    private fun farmCard(title: String, farm: FarmSyncFile, choose: () -> Unit): View {
        val card = column().apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(cream, 14, border)
            isFocusable = true
            setOnClickListener { choose() }
        }
        card.addView(text(title, 17, true))
        card.addView(text(getString(R.string.farm_sync_summary, money(farm.coins), farm.harvests), 14))
        if (farm.savedAt > 0) card.addView(text(android.text.format.DateUtils.getRelativeTimeSpanString(
            farm.savedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString(), 12))
        return card.also { it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) } }
    }

    /** The milestone still to reach, or null when the region is open. */
    private fun regionLock(region: FarmRegion): String? = when {
        state.regionUnlocked(region) -> null
        region == FarmRegion.FIELDS ->
            getString(R.string.farm_region_locked_fields, FarmState.FIELDS_HARVESTS, state.harvests)
        else -> getString(R.string.farm_visit_livestock_locked, FarmState.LIVESTOCK_HARVESTS,
            state.harvests)
    }
    private fun goToRegion(region: FarmRegion) {
        val locked = regionLock(region)
        if (locked != null) { message(locked); return }
        closeBubble(); world.switchRegion(region); fieldView.resetMotion(); refresh()
        if (region == FarmRegion.GREENHOUSE) { showGreenhouse(); return }
        if (region != FarmRegion.FIELDS) message(getString(region.description))
    }

    private fun chooseRegion() {
        showBubble(getString(R.string.farm_regions)) { body ->
            FarmRegion.entries.forEach { region ->
                val locked = regionLock(region)
                val row = column().apply {
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded(if (region == world.region) sage else cream, 14, border)
                    isFocusable = true
                    alpha = if (locked == null) 1f else .6f
                    setOnClickListener { goToRegion(region) }
                }
                val ready = region == FarmRegion.FIELDS && state.fieldsNeedAttention()
                row.addView(text(getString(region.label) + if (ready) " 🚜" else "", 17, true))
                row.addView(text(getString(region.description), 13))
                if (locked != null) row.addView(text("🔒 " + locked, 13, true))
                body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }

    /** How many plantings of the selected seed the pit can still enrich. */
    private fun manureServings(): Int {
        val cost = state.selected.manureCost
        return if (cost <= 0) 0 else (state.livestock.manure / cost).toInt()
    }
    private fun manurePit() {
        state.advanceLivestock()
        showBubble(getString(R.string.farm_manure_title)) { body ->
            val pit = text("", 17, true)
            body.addView(pit)
            val servings = text("", 15)
            body.addView(servings.apply { setPadding(0, dp(4), 0, dp(10)) })
            livestockUi.add {
                pit.text = getString(R.string.farm_manure_stock, money(state.livestock.manure),
                    money(state.livestock.manureCapacity()), money(state.livestock.manurePerDay()))
                servings.text = getString(R.string.farm_manure_plantings, manureServings(),
                    getString(state.selected.label), state.selected.manureCost)
            }
            body.addView(text(getString(R.string.farm_manure_body), 13))
        }
    }

    private fun cheatMenu() {
        showBubble(getString(R.string.farm_dev_title)) { body ->
            body.addView(text(getString(R.string.farm_dev_coins), 15, true).apply { setPadding(0, 0, 0, dp(6)) })
            val coinsRow = LinearLayout(this)
            // Amounts that match the rebalanced scale: a parcel, an upgrade, the whole ladder.
            for (amount in listOf(1_000L, 100_000L, 10_000_000L)) coinsRow.addView(
                button(getString(R.string.farm_dev_add_coins, money(amount))) {
                    state.cheatAddCoins(amount); message(getString(R.string.farm_dev_done)); refresh()
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) })
            body.addView(coinsRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

            body.addView(text(getString(R.string.farm_dev_time), 15, true).apply { setPadding(0, 0, 0, dp(6)) })
            val timeRow = LinearLayout(this)
            for ((label, hours) in listOf(R.string.farm_dev_skip_1h to 1L, R.string.farm_dev_skip_24h to 24L))
                timeRow.addView(button(getString(label)) {
                    state.cheatSkipTime(hours * 3600_000L); message(getString(R.string.farm_dev_done)); refresh()
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(4), 0) })
            body.addView(timeRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
            body.addView(button(getString(R.string.farm_dev_complete_growth)) {
                state.cheatCompleteGrowth(); message(getString(R.string.farm_dev_done)); refresh()
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(14) })

            body.addView(text(getString(R.string.farm_dev_testing), 15, true).apply { setPadding(0, 0, 0, dp(6)) })
            body.addView(button(getString(R.string.farm_dev_harvest_all)) {
                val result = state.harvestMany(state.plots.indices.toList(), System.currentTimeMillis())
                message(getString(R.string.farm_harvested_many, result.count)); refresh()
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(4) })
            body.addView(button(getString(R.string.farm_dev_water_all)) {
                state.cheatWaterAll(); message(getString(R.string.farm_dev_done)); refresh()
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(14) })

            body.addView(text(getString(R.string.farm_dev_reset), 15, true).apply { setPadding(0, 0, 0, dp(6)) })
            body.addView(button(getString(R.string.farm_dev_reset_confirm)) {
                state.cheatReset(); closeBubble()
                // The fields and the pens are locked again: standing in one of them would be a dead end.
                world.switchRegion(FarmRegion.HOME); fieldView.resetMotion(); world.focusParcel(0)
                refresh(); message(getString(R.string.farm_dev_reset_done))
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("farm_region", world.region.name)
        outState.putInt("farm_livestock_page", habitat.selected.ordinal)
        super.onSaveInstanceState(outState)
    }

    private fun rounded(color: Int, radius: Int = 14, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = com.Atom2Universe.app.AppearanceStyle.corner(this@FarmActivity, radius.toFloat()); stroke?.let { setStroke(dp(2), it) }
    }
    private fun text(value: String, size: Int = 14, bold: Boolean = false) = TextView(this).apply {
        this.text = value; textSize = size.toFloat(); setTextColor(ink)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun visitDockEntry(kind: FarmArtView.Kind, label: Int, action: () -> Unit): Pair<LinearLayout, TextView> {
        val entry = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(60)
            background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(cream, 18, border), null)
            elevation = dp(6).toFloat(); isFocusable = true; contentDescription = getString(label)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener { action() }
        }
        entry.addView(FarmArtView(this, kind).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(32), dp(40)))
        val details = column().apply { setPadding(dp(6), 0, 0, 0) }
        details.addView(text(getString(label), 14, true))
        val summary = text("", 11).apply { maxLines = 2 }
        details.addView(summary)
        entry.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        return entry to summary
    }
    private fun icon(kind: FarmArtView.Kind, label: Int, action: () -> Unit) = FarmArtView(this, kind, sprites).apply {
        contentDescription = getString(label); tooltipText = getString(label)
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(sage, 16), null)
        setOnClickListener {
            rememberActionOrigin(this)
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                animate().cancel(); scaleX = .96f; scaleY = .96f
                animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            action()
        }
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false; setTextColor(ink)
        minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(sage, 12), null)
        setOnClickListener {
            rememberActionOrigin(this)
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                animate().cancel(); scaleX = .96f; scaleY = .96f
                animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            action()
        }
    }
    private fun rememberActionOrigin(view: View, x: Float = view.width / 2f, y: Float = view.height / 2f) {
        val at = IntArray(2); val rootAt = IntArray(2)
        view.getLocationInWindow(at); root.getLocationInWindow(rootAt)
        actionOrigin = PointF(at[0] - rootAt[0] + x, at[1] - rootAt[1] + y)
    }
    private fun showAction(kind: FarmActionEffectsView.Kind, count: Int = 1, flower: FarmFlower? = null, caption: String? = null) {
        if (count <= 0 || !::actionEffects.isInitialized) return
        val point = actionOrigin ?: PointF(root.width / 2f, root.height * .55f)
        actionEffects.elevation = dp(30).toFloat(); actionEffects.bringToFront()
        actionEffects.emit(kind, point.x, point.y, caption ?: getString(R.string.farm_action_count, count), flower)
    }
    private fun preview(crop: FarmCrop) = FarmArtView(this, FarmArtView.Kind.CROP, sprites).apply {
        this.crop = crop; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = rounded(palette.surface, 16)
    }
    private fun duration(seconds: Int): String {
        if (seconds < 60) return getString(R.string.farm_duration_seconds, seconds.coerceAtLeast(0))
        val minutes = (seconds.coerceAtLeast(0) + 59) / 60
        return getString(R.string.farm_duration, minutes / 60, minutes % 60)
    }

    /** An in-scene card, never a full-screen dialog or dimmed modal window. */
    private fun showBubble(title: String, parcel: Int? = null, heightFraction: Float = .64f, content: (LinearLayout) -> Unit) {
        closeBubble()
        status.visibility = View.GONE
        val card = column().apply {
            background = rounded(cream, 22, border); elevation = dp(12).toFloat()
            setPadding(dp(12), dp(8), dp(12), dp(12))
            isClickable = true
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text(title, 18, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(icon(FarmArtView.Kind.CLOSE, R.string.farm_close) { closeBubble() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        card.addView(header)
        bubbleFeedback = text("", 13).apply {
            visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        card.addView(bubbleFeedback)
        val body = column()
        content(body)
        val scroll = ScrollView(this).apply { isFillViewport = false; addView(body); isVerticalScrollBarEnabled = false }
        card.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        bubble = card
        val availableWidth = (root.width - dp(24)).coerceAtLeast(1)
        val cardWidth = minOf(dp(380), availableWidth)
        val top = toolbar.bottom + dp(10)
        val maxHeight = minOf((root.height * heightFraction).toInt(), root.height - top - dp(16)).coerceAtLeast(1)
        card.measure(View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
        val cardHeight = minOf(card.measuredHeight + dp(32), maxHeight)
        // Bound the scroll body explicitly, so long shops remain inside their floating card.
        scroll.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        val point = parcel?.let { world.parcelScreenPosition(it) }
        val x = ((point?.x?.toInt() ?: (root.width - cardWidth / 2 - dp(12))) - cardWidth / 2)
            .coerceIn(dp(12), maxOf(dp(12), root.width - cardWidth - dp(12)))
        val y = (point?.y?.toInt() ?: top).coerceIn(top, maxOf(top, root.height - cardHeight - dp(12)))
        root.addView(card, FrameLayout.LayoutParams(cardWidth, cardHeight, Gravity.TOP or Gravity.LEFT).apply {
            leftMargin = x; topMargin = y
        })
        ViewCompat.setAccessibilityPaneTitle(card, title)
        refresh()
    }
    private fun closeBubble() {
        stopStepRepeat()
        bubble?.let { root.removeView(it) }
        livestockUi.clear()
        villageUi.clear()
        visitUi.clear()
        workshopUi.clear()
        greenhouseUi.clear()
        bubble = null; bubbleFeedback = null; purchases.clear(); stockLabels.clear()
    }
    private fun message(value: String) {
        if (bubbleFeedback != null) {
            bubbleFeedback?.text = value; bubbleFeedback?.visibility = View.VISIBLE
        } else {
            status.text = value; status.visibility = View.VISIBLE
            handler.removeCallbacks(hideStatus); handler.postDelayed(hideStatus, 4200)
        }
    }
    private fun addEncounterLink(body: LinearLayout) {
        val meeting = state.encounters.pending
        if (meeting != null) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(FarmVisitorPreview(this, meeting.visitor), LinearLayout.LayoutParams(dp(64), dp(64)))
            row.addView(button(getString(R.string.farm_encounter_open, getString(meeting.visitor.label))) { showEncounters() },
                LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        } else body.addView(button(getString(R.string.farm_encounter_journal)) { showEncounters() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }
    private fun showEncounters() {
        state.advanceEncounters()
        showBubble(getString(R.string.farm_encounter_title)) { body ->
            body.addView(text(getString(R.string.farm_encounter_intro), 13).apply { setPadding(0, dp(6), 0, dp(10)) })
            val meeting = state.encounters.pending
            if (meeting != null) {
                body.addView(FarmVisitorPreview(this, meeting.visitor), LinearLayout.LayoutParams(-1, dp(125)))
                body.addView(text(getString(meeting.visitor.label), 18, true))
                body.addView(text(getString(meeting.visitor.stories[meeting.story]), 15).apply { setPadding(0, dp(8), 0, dp(12)) })
                body.addView(text(getString(R.string.farm_encounter_gift, meeting.seeds, getString(meeting.crop.label), money(meeting.coins)), 14, true))
                val greet = button(getString(R.string.farm_encounter_receive)) { receiveVisitor(meeting.id, true) }
                body.addView(greet, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
                visitUi += { greet.isEnabled = state.seeds[meeting.crop.ordinal] <= 9999 - meeting.seeds }
                body.addView(button(getString(R.string.farm_encounter_receive_coins, money(meeting.coinAlternative))) {
                    receiveVisitor(meeting.id, false)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                body.addView(button(getString(R.string.farm_encounter_visit)) {
                    goToRegion(FarmRegion.HOME); world.focusVisitor(); closeBubble()
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            } else body.addView(text(getString(R.string.farm_encounter_rest), 14).apply { setPadding(0, dp(8), 0, dp(12)) })
            body.addView(text(getString(R.string.farm_encounter_journal), 17, true).apply { setPadding(0, dp(12), 0, dp(6)) })
            for (visitor in FarmVisitor.entries) {
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(FarmVisitorPreview(this, visitor), LinearLayout.LayoutParams(dp(54), dp(54)))
                val visits = state.encounters.count(visitor)
                row.addView(text(getString(R.string.farm_encounter_history, getString(visitor.label), visits), 13),
                    LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(row)
            }
            body.addView(button(getString(R.string.farm_visit_title)) { showVisit() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
    }
    private fun receiveVisitor(id: Long, withSeeds: Boolean) {
        val reward = state.greetVisitor(id, withSeeds)
        showEncounters()
        if (reward == null) { message(getString(R.string.farm_encounter_unavailable)); return }
        showAction(FarmActionEffectsView.Kind.HEART)
        if (reward.seeds > 0) showAction(FarmActionEffectsView.Kind.SEEDS, reward.seeds)
        message(getString(R.string.farm_encounter_thanks, getString(reward.visitor.label)))
    }

    private fun showVisit() {
        showBubble(getString(R.string.farm_visit_title)) { body ->
            body.addView(text(getString(R.string.farm_visit_intro), 13).apply {
                setPadding(0, dp(6), 0, dp(10))
            })
            addEncounterLink(body)
            if (state.welcomePlantings > 0 || state.plots.any { it.welcome && it.crop == FarmCrop.RADISH }) {
                body.addView(text(getString(R.string.farm_visit_welcome,
                    FarmState.WELCOME_SEEDS, FarmState.WELCOME_GROWTH_SECONDS), 14, true).apply {
                    setPadding(0, 0, 0, dp(12))
                })
            }
            addVisitActions(body, null)
            addWorkshopLink(body)
            addOrchardLink(body)
            addGreenhouseLink(body)
            if (state.livestock.unlocked > 0) addAnimalCare(body, null)
            addProjectLink(body)
            body.addView(button(getString(R.string.farm_village_title)) { showVillage() },
                LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
            val rescue = button(getString(R.string.farm_visit_seed_help, FarmState.SEED_HELP_COUNT)) {
                if (state.claimSeedHelp()) {
                    message(getString(R.string.farm_visit_seed_help_done, FarmState.SEED_HELP_COUNT)); refresh()
                }
            }
            body.addView(rescue, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
            visitUi += { rescue.visibility = if (state.canClaimSeedHelp()) View.VISIBLE else View.GONE }
            visitUi.forEach { it() }
        }
    }

    /** The same small set of gestures works on one parcel and on the entire owned farm. */
    private fun addVisitActions(body: LinearLayout, parcel: Int?) {
        val summary = text("", 13).apply { setPadding(0, 0, 0, dp(10)) }
        body.addView(summary)
        val harvest = button("") {
            world.cancelMiniGames()
            val result = state.harvestMany(state.visitCells(parcel), System.currentTimeMillis())
            showAction(FarmActionEffectsView.Kind.BASKET, result.count)
            message(getString(R.string.farm_harvested_many, result.count)); refresh()
        }
        val plant = button("") {
            world.cancelMiniGames()
            val crop = state.selected
            val count = state.plantAll(crop, System.currentTimeMillis(), parcel)
            message(plantedMessage(crop, count)); refresh()
        }
        val water = button("") {
            world.cancelMiniGames()
            val watered = state.waterMany(state.visitCells(parcel), System.currentTimeMillis())
            showAction(FarmActionEffectsView.Kind.WATER, watered.size)
            world.showWaterBursts(watered)
            message(getString(R.string.farm_watering_done, watered.size)); refresh()
        }
        val clean = button("") {
            world.cancelMiniGames()
            val count = state.cleanForVisit(parcel)
            showAction(FarmActionEffectsView.Kind.BUILD, count)
            message(getString(R.string.farm_visit_cleaned, count)); refresh()
        }
        for (action in listOf(clean, harvest, plant, water)) body.addView(action,
            LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        val choose = button(getString(R.string.farm_visit_choose_seeds)) { inventory() }
        body.addView(choose, LinearLayout.LayoutParams(-1, dp(48)))
        visitUi += {
            val stateNow = state.visitSummary(parcel)
            summary.text = getString(R.string.farm_visit_summary, stateNow.ready, stateNow.thirsty,
                stateNow.empty, stateNow.growing) + (stateNow.nextHarvestSeconds?.let {
                getString(R.string.farm_visit_next_harvest, duration(it))
            } ?: "")
            val planting = state.plantAllCount(state.selected, parcel)
            harvest.text = getString(R.string.farm_visit_harvest, stateNow.ready)
            plant.text = getString(R.string.farm_visit_plant, getString(state.selected.label), planting)
            water.text = getString(R.string.farm_visit_water, stateNow.thirsty)
            clean.text = getString(R.string.farm_visit_clean, stateNow.debris)
            for ((action, enabled) in listOf(harvest to (stateNow.ready > 0), plant to (planting > 0),
                    water to (stateNow.thirsty > 0), clean to (stateNow.debris > 0))) {
                action.isEnabled = enabled; action.alpha = if (enabled) 1f else .45f
            }
            clean.visibility = if (stateNow.debris > 0) View.VISIBLE else View.GONE
            harvest.visibility = if (stateNow.ready > 0) View.VISIBLE else View.GONE
            plant.visibility = if (stateNow.empty > 0) View.VISIBLE else View.GONE
            water.visibility = if (stateNow.thirsty > 0) View.VISIBLE else View.GONE
        }
        visitUi.forEach { it() }
    }

    private fun showVillage() {
        showBubble(getString(R.string.farm_village_title)) { body ->
            body.addView(text(getString(R.string.farm_village_intro), 13).apply {
                setPadding(dp(2), dp(4), dp(2), dp(8))
            })
            body.addView(text(getString(R.string.farm_village_deliveries, state.village.deliveries), 13, true))
            body.addView(text(getString(R.string.farm_village_quality, FarmVillageOrder.BONUS_PERCENT), 12).apply {
                setPadding(dp(2), dp(6), dp(2), dp(12))
            })
            addProjectLink(body)
            state.village.orders.forEach { order ->
                body.addView(villageOrderCard(order), LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(12)
                })
            }
            addWorkshopLink(body)
        }
    }

    private fun villageOrderCard(order: FarmVillageOrder): View {
        val person = order.villager
        val card = column().apply {
            background = rounded(palette.surface, 16, border)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(FarmArtView(this, FarmArtView.Kind.VILLAGER).apply {
            villager = person; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val identity = column().apply { setPadding(dp(10), 0, 0, 0) }
        identity.addView(text(getString(person.label), 17, true))
        identity.addView(text(getString(person.role), 12))
        header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(header)
        val friendship = state.village.friendship(person)
        val relation = when {
            friendship >= 15 -> R.string.farm_village_relation_friend
            friendship >= 8 -> R.string.farm_village_relation_trusted
            friendship >= 3 -> R.string.farm_village_relation_neighbour
            else -> R.string.farm_village_relation_new
        }
        card.addView(text(getString(relation), 13, true).apply {
            setTextColor(palette.success); setPadding(0, dp(8), 0, 0)
        })
        val next = when { friendship < 3 -> 3; friendship < 8 -> 8; friendship < 15 -> 15; else -> null }
        val relationProgress = if (next == null) getString(R.string.farm_village_relation_full, friendship)
            else getString(R.string.farm_village_relation_progress, friendship, next,
                getString(when (next) {
                    3 -> R.string.farm_village_relation_neighbour
                    8 -> R.string.farm_village_relation_trusted
                    else -> R.string.farm_village_relation_friend
                }))
        card.addView(text(relationProgress, 12))
        val gift = FarmGift.entries.first { it.villager == person }
        if (!state.projects.owns(gift) && friendship >= FarmGift.FRIENDSHIP_NEEDED) {
            card.addView(button(getString(R.string.farm_gift_offer, getString(person.label), getString(gift.label))) {
                val claimed = state.claimGift(gift)
                if (claimed) showAction(FarmActionEffectsView.Kind.GIFT)
                showDecorations()
                if (claimed) message(getString(R.string.farm_gift_received, getString(gift.label)))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        card.addView(text(if (order.preparation == null) getString(person.requests[order.story])
            else getString(R.string.farm_workshop_village_story, getString(order.preparation.label)), 14).apply {
            setPadding(0, dp(10), 0, dp(6))
        })
        order.items.forEach { item ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(preview(item.crop), LinearLayout.LayoutParams(dp(46), dp(52)))
            val details = column().apply { setPadding(dp(8), dp(4), 0, dp(4)) }
            val amount = text("", 13, true)
            details.addView(amount)
            val prepare = button(getString(if (item.crop.tree) R.string.farm_orchard_title else R.string.farm_village_prepare)) { prepareOrderCrop(item.crop) }
            details.addView(prepare, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) })
            villageUi += {
                val stock = state.cropProduceTotal(item.crop)
                amount.text = getString(R.string.farm_village_item, getString(item.crop.label), stock, item.count)
                amount.setTextColor(if (stock >= item.count) palette.success else ink)
                prepare.visibility = if (stock >= item.count) View.GONE else View.VISIBLE
            }
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
        }
        val reward = text("", 13, true).apply { setPadding(0, dp(8), 0, dp(8)) }
        order.preparation?.let { recipe ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(preparationPreview(recipe), LinearLayout.LayoutParams(dp(48), dp(56)))
            val details = column().apply { setPadding(dp(8), 0, 0, 0) }
            val count = text("", 13, true); details.addView(count)
            details.addView(button(getString(R.string.farm_workshop_prepare)) { showWorkshop(recipe) })
            villageUi += { count.text = getString(R.string.farm_village_item, getString(recipe.label), state.workshop.count(recipe), 1) }
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(row)
        }
        card.addView(reward)
        val deliver = button(getString(R.string.farm_village_deliver)) {
            val project = state.projects.active
            val result = state.deliverOrder(order.id)
            showVillage()
            if (result != null) {
                showAction(FarmActionEffectsView.Kind.HEART)
                if (project != null) showAction(FarmActionEffectsView.Kind.BUILD, caption = getString(R.string.farm_action_help))
            }
            if (result == null) message(getString(R.string.farm_village_missing))
            else message(getString(R.string.farm_village_delivered, getString(result.villager.label),
                getString(result.villager.thanks), money(result.coins)) + (project?.let {
                    getString(if (state.projects.complete(it)) R.string.farm_project_finished_feedback
                        else R.string.farm_project_help_feedback, getString(it.label))
                } ?: ""))
        }
        card.addView(deliver, LinearLayout.LayoutParams(-1, dp(48)))
        card.addView(button(getString(R.string.farm_village_replace)) {
            if (state.replaceOrder(order.id)) {
                showVillage(); message(getString(R.string.farm_village_replaced))
            }
        }.apply { background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(cream, 12), null) },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        villageUi += {
            val actual = state.orderReward(order.id)
            deliver.isEnabled = actual != null; deliver.alpha = if (actual != null) 1f else .45f
            reward.text = if (actual == null) getString(R.string.farm_village_reward_min, money(order.minimumReward))
                else getString(R.string.farm_village_reward_ready, money(actual))
        }
        villageUi.forEach { it() }
        return card
    }

    private fun preparationPreview(recipe: FarmRecipe) = FarmArtView(this, FarmArtView.Kind.PREPARATION).apply {
        this.recipe = recipe; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun addWorkshopLink(body: LinearLayout) {
        val summary = text("", 13, true).apply { setPadding(0, dp(10), 0, dp(4)) }; body.addView(summary)
        body.addView(button(getString(R.string.farm_workshop_title)) { showWorkshop() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        body.addView(button(getString(R.string.farm_cookbook_title)) { showCookbook() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val collect = button(getString(R.string.farm_workshop_collect_short)) {
            val count = state.collectPreparations()
            showAction(FarmActionEffectsView.Kind.BASKET, count)
            message(getString(if (count > 0) R.string.farm_workshop_collected else R.string.farm_workshop_collect_none, count))
            refresh()
        }
        body.addView(collect, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        workshopUi += {
            summary.text = getString(R.string.farm_workshop_summary,
                state.workshop.readyCount(System.currentTimeMillis()), state.workshop.jobs.size, FarmWorkshopState.SLOTS)
            collect.visibility = if (state.workshop.readyCount(System.currentTimeMillis()) > 0) View.VISIBLE else View.GONE
        }
    }
    private fun showWorkshop(first: FarmRecipe? = null) {
        if (first != null) { workshopCategory = first.category; favoriteRecipesOnly = false }
        if (state.discoverRecipes()) state.save()
        showBubble(getString(R.string.farm_workshop_title)) { body ->
            body.addView(text(getString(R.string.farm_workshop_intro, FarmWorkshopState.SLOTS), 13).apply {
                setPadding(0, dp(6), 0, dp(10))
            })
            if (!state.workshop.introUsed) body.addView(text(getString(R.string.farm_workshop_intro_fast), 14, true))
            val jobs = state.workshop.jobs
            body.addView(text(getString(R.string.farm_workshop_free, FarmWorkshopState.SLOTS - jobs.size), 14, true))
            for ((index, job) in jobs.withIndex()) {
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(preparationPreview(job.recipe), LinearLayout.LayoutParams(dp(52), dp(58)))
                val timing = text("", 13, true)
                row.addView(timing, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                body.addView(row)
                workshopUi += {
                    val now = System.currentTimeMillis()
                    timing.text = getString(R.string.farm_workshop_job, index + 1, getString(job.recipe.label),
                        if (job.readyAt <= now) getString(R.string.farm_workshop_ready)
                        else duration(((job.readyAt - now + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
                }
            }
            val collect = button("") {
                val count = state.collectPreparations(); showWorkshop(first)
                showAction(FarmActionEffectsView.Kind.BASKET, count)
                message(getString(if (count > 0) R.string.farm_workshop_collected else R.string.farm_workshop_collect_none, count))
            }
            body.addView(collect, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(10) })
            workshopUi += {
                val ready = state.workshop.readyCount(System.currentTimeMillis())
                collect.text = getString(R.string.farm_workshop_collect, ready)
                collect.isEnabled = ready > 0
            }
            body.addView(text(getString(R.string.farm_workshop_catalogue, FarmRecipe.entries.size), 14, true))
            body.addView(button(getString(R.string.farm_cookbook_title)) { showCookbook(first) })
            body.addView(button(getString(if (favoriteRecipesOnly) R.string.farm_cookbook_show_all else R.string.farm_cookbook_show_favorites)) {
                favoriteRecipesOnly = !favoriteRecipesOnly; showWorkshop()
            })
            for (category in FarmRecipeCategory.entries) {
                body.addView(button(getString(category.label)) {
                    workshopCategory = category; showWorkshop()
                }.apply { isEnabled = category != workshopCategory },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
            }
            if (state.livestock.unlocked > 0 || state.livestock.meatCount() > 0) {
                body.addView(button(getString(R.string.farm_butcher_title)) { showButcher() },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
            val recipes = FarmRecipe.entries.filter { it.category == workshopCategory && (!favoriteRecipesOnly || state.workshop.favorite(it)) }
            if (recipes.isEmpty()) body.addView(text(getString(R.string.farm_cookbook_no_favorites), 13))
            for (recipe in recipes.sortedWith(compareBy<FarmRecipe> { if (it == first) 0 else 1 }
                .thenBy { !state.canPrepare(it) }.thenBy { !state.workshop.favorite(it) }.thenBy { !state.workshop.knows(it) })) {
                body.addView(recipeCard(recipe), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            body.addView(button(getString(R.string.farm_produce_inventory)) { produceInventory() })
            body.addView(button(getString(R.string.farm_village_title)) { showVillage() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            body.addView(button(getString(R.string.farm_workshop_visit)) {
                closeBubble(); goToRegion(FarmRegion.HOME); world.focusWorkshop()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }
    private fun recipeCard(recipe: FarmRecipe, inBook: Boolean = false): View {
        val card = column().apply {
            background = rounded(palette.surface, 16, border)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(preparationPreview(recipe), LinearLayout.LayoutParams(dp(66), dp(70)))
        val identity = column().apply { setPadding(dp(8), 0, 0, 0) }
        identity.addView(text(getString(recipe.label), 17, true))
        identity.addView(text(getString(R.string.farm_workshop_recipe_value, money(recipe.sale.toLong()),
            money(recipe.ingredientValue.toLong())), 12))
        val timing = text("", 12); identity.addView(timing)
        workshopUi += { timing.text = getString(R.string.farm_workshop_recipe_time,
            duration(if (state.workshop.introUsed) recipe.minutes * 60 else 60)) }
        header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(header)
        card.addView(text(getString(R.string.farm_workshop_common), 12).apply { setPadding(0, dp(6), 0, dp(6)) })
        for (ingredient in recipe.crops) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(preview(ingredient.crop), LinearLayout.LayoutParams(dp(42), dp(48)))
            val count = text("", 13)
            row.addView(count, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) }); card.addView(row)
            workshopUi += { count.text = getString(R.string.farm_workshop_ingredient,
                getString(ingredient.crop.label), state.produce[ingredient.crop.ordinal][FarmCropQuality.COMMON.ordinal], ingredient.count) }
            if (state.cropUnlocked(ingredient.crop)) card.addView(button(getString(
                if (ingredient.crop.tree) R.string.farm_orchard_title else R.string.farm_village_prepare)) {
                prepareOrderCrop(ingredient.crop)
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        }
        for (ingredient in recipe.products) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(FarmArtView(this, FarmArtView.Kind.ANIMAL_PRODUCT).apply {
                product = ingredient.product; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(42), dp(48)))
            val count = text("", 13)
            row.addView(count, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) }); card.addView(row)
            workshopUi += { count.text = getString(R.string.farm_workshop_ingredient,
                getString(ingredient.product.label), state.livestock.stock(ingredient.product), ingredient.count) }
        }
        for (ingredient in recipe.flowers) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(FarmFlowerPreview(this, ingredient.flower), LinearLayout.LayoutParams(dp(42), dp(48)))
            val count = text("", 13)
            row.addView(count, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) }); card.addView(row)
            workshopUi += { count.text = getString(R.string.farm_workshop_ingredient,
                getString(ingredient.flower.label), state.greenhouse.count(ingredient.flower), ingredient.count) }
        }
        for (ingredient in recipe.meats) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(meatPreview(ingredient.meat), LinearLayout.LayoutParams(dp(42), dp(48)))
            val count = text("", 13)
            row.addView(count, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) }); card.addView(row)
            workshopUi += { count.text = getString(R.string.farm_workshop_ingredient,
                getString(ingredient.meat.label), state.livestock.stock(ingredient.meat), ingredient.count) }
        }
        for (ingredient in recipe.preparations) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(preparationPreview(ingredient.recipe), LinearLayout.LayoutParams(dp(42), dp(48)))
            val count = text("", 13)
            row.addView(count, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) }); card.addView(row)
            workshopUi += { count.text = getString(R.string.farm_workshop_ingredient,
                getString(ingredient.recipe.label), state.workshop.count(ingredient.recipe), ingredient.count) }
            card.addView(button(getString(R.string.farm_kitchen_make_ingredient, getString(ingredient.recipe.label))) { showWorkshop(ingredient.recipe) })
        }
        if (recipe.meats.isNotEmpty()) card.addView(button(getString(R.string.farm_butcher_title)) { showButcher() })
        if (recipe.flowers.isNotEmpty()) card.addView(button(getString(R.string.farm_greenhouse_title)) { showGreenhouse() })
        if (recipe.products.isNotEmpty() && state.livestockUnlocked()) card.addView(button(getString(R.string.farm_companion_visit)) {
            closeBubble(); goToRegion(FarmRegion.LIVESTOCK)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        if (!state.workshop.knows(recipe)) card.addView(text(getString(R.string.farm_workshop_locked), 12, true))
        if (recipe.dishesNeeded > state.workshop.distinctDishes) card.addView(text(getString(R.string.farm_cookbook_unlock,
            state.workshop.distinctDishes, recipe.dishesNeeded), 13, true))
        if (recipe.food && state.workshop.knows(recipe)) {
            card.addView(text(getString(R.string.farm_cookbook_history, state.workshop.cooked(recipe), state.workshop.practiced(recipe)), 12))
            card.addView(button(getString(if (state.workshop.favorite(recipe)) R.string.farm_cookbook_unfavorite else R.string.farm_cookbook_favorite)) {
                state.toggleFavoriteRecipe(recipe)
                if (inBook) showCookbook(recipe) else showWorkshop(recipe)
            })
        }
        val stock = text("", 12, true).apply { setPadding(0, dp(6), 0, dp(6)) }; card.addView(stock)
        workshopUi += { stock.text = getString(R.string.farm_workshop_recipe_stock, state.workshop.count(recipe)) }
        val revision = state.workshop.revision
        val start = button(getString(if (recipe.food) R.string.farm_kitchen_start else R.string.farm_workshop_start)) {
            if (recipe.food) showKitchen(recipe, revision) else launchRecipe(recipe, revision, false)
        }
        card.addView(start)
        workshopUi += { start.isEnabled = state.canPrepare(recipe); start.alpha = if (start.isEnabled) 1f else .45f }
        if (recipe.food) {
            val quick = button(getString(R.string.farm_kitchen_direct)) { launchRecipe(recipe, revision, false) }
            card.addView(quick, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            workshopUi += { quick.isEnabled = state.canPrepare(recipe) }
        }
        return card
    }

    private fun launchRecipe(recipe: FarmRecipe, revision: Long, handsOn: Boolean) {
        val ok = state.startPreparation(recipe, revision, handsOn = handsOn)
        showWorkshop(recipe)
        if (ok) showAction(FarmActionEffectsView.Kind.COOK, caption = getString(R.string.farm_action_preparation))
        message(getString(if (ok) R.string.farm_workshop_started else R.string.farm_workshop_unavailable, getString(recipe.label)))
    }

    private fun showKitchen(recipe: FarmRecipe, revision: Long) {
        if (!state.canPrepare(recipe) || state.workshop.revision != revision) {
            message(getString(R.string.farm_workshop_unavailable, getString(recipe.label))); return
        }
        val session = FarmCookingSession(recipe)
        showBubble(getString(R.string.farm_kitchen_title, getString(recipe.label)), heightFraction = .92f) { body ->
            body.addView(text(getString(R.string.farm_kitchen_intro), 12))
            val instruction = text("", 15, true); body.addView(instruction)
            val help = text("", 13).apply { setPadding(0, dp(4), 0, dp(6)) }; body.addView(help)
            val surface = FarmCookingView(this, session)
            val boardHeight = minOf(dp(200), ((root.height - toolbar.bottom - dp(100)) * .4f).toInt()).coerceAtLeast(dp(100))
            body.addView(surface, LinearLayout.LayoutParams(-1, boardHeight))
            val assist = button(getString(R.string.farm_kitchen_assist)) { surface.assistGesture() }
            body.addView(assist)
            val finish = button(getString(R.string.farm_kitchen_finish)) {
                if (session.complete) launchRecipe(recipe, revision, true)
            }
            body.addView(finish, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            body.addView(button(getString(R.string.farm_kitchen_direct)) { launchRecipe(recipe, revision, false) })
            body.addView(button(getString(R.string.farm_cancel)) { showWorkshop(recipe) })
            val update = {
                instruction.text = if (session.complete) getString(R.string.farm_kitchen_done) else getString(
                    R.string.farm_kitchen_step, session.index + 1, session.steps.size, getString(session.step!!.label))
                help.text = if (session.complete) getString(R.string.farm_kitchen_bonus) else getString(session.step!!.help)
                session.layerLabel?.let { help.text = getString(R.string.farm_kitchen_assemble_next, getString(it)) }
                finish.isEnabled = session.complete && state.canPrepare(recipe) && state.workshop.revision == revision
                assist.visibility = if (session.complete) View.GONE else View.VISIBLE
            }
            surface.onProgress = update; workshopUi += update; update()
        }
    }

    private fun showCookbook(first: FarmRecipe? = null) {
        if (state.discoverRecipes()) state.save()
        val pages = state.workshop.recipes.filter { it.food }
        val page = pages.indexOf(first).coerceAtLeast(0)
        showBubble(getString(R.string.farm_cookbook_title)) { body ->
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(FarmArtView(this, FarmArtView.Kind.BOOK).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(70), dp(72)))
            header.addView(text(getString(R.string.farm_cookbook_summary, pages.size, FarmRecipe.entries.count { it.food },
                state.workshop.distinctDishes, state.workshop.totalDishes), 14, true), LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(header)
            body.addView(text(getString(R.string.farm_cookbook_intro), 13).apply { setPadding(0, dp(8), 0, dp(8)) })
            val upcoming = FarmRecipe.entries.filter { !state.workshop.knows(it) && it.dishesNeeded > 0 }.minByOrNull { it.dishesNeeded }
            if (upcoming != null) {
                body.addView(text(getString(R.string.farm_cookbook_next, getString(upcoming.label), upcoming.dishesNeeded), 13, true))
                body.addView(text(getString(R.string.farm_cookbook_unlock, minOf(state.workshop.distinctDishes, upcoming.dishesNeeded), upcoming.dishesNeeded), 12))
            }
            pages.getOrNull(page)?.let { recipe ->
                body.addView(text(getString(R.string.farm_cookbook_page, page + 1, pages.size), 14, true).apply { setPadding(0, dp(10), 0, dp(6)) })
                body.addView(recipeCard(recipe, inBook = true))
                val navigation = LinearLayout(this)
                navigation.addView(button(getString(R.string.farm_cookbook_previous)) {
                    showCookbook(pages[(page + pages.size - 1) % pages.size])
                }.apply { isEnabled = pages.size > 1 }, LinearLayout.LayoutParams(0, -2, 1f))
                navigation.addView(button(getString(R.string.farm_cookbook_next_page)) {
                    showCookbook(pages[(page + 1) % pages.size])
                }.apply { isEnabled = pages.size > 1 }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
                body.addView(navigation, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
            body.addView(button(getString(R.string.farm_workshop_title)) { showWorkshop(first) })
        }
    }

    private fun projectSummary(): String {
        val project = state.projects.active
        return when {
            project != null -> getString(R.string.farm_project_summary, getString(project.label),
                state.projects.progress(project), project.helpNeeded)
            FarmProject.entries.all { state.projects.complete(it) } -> getString(R.string.farm_projects_all_done)
            else -> getString(R.string.farm_project_reserve, state.projects.reserve)
        }
    }

    private fun addProjectLink(body: LinearLayout) {
        body.addView(text(projectSummary(), 13, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        body.addView(button(getString(R.string.farm_projects_title)) { showProjects() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun showProjects(first: FarmProject? = null) {
        showBubble(getString(R.string.farm_projects_title)) { body ->
            body.addView(text(getString(R.string.farm_projects_intro), 13).apply { setPadding(0, dp(6), 0, dp(10)) })
            body.addView(text(projectSummary(), 14, true))
            val ordered = FarmProject.entries.sortedBy { if (it == first) 0 else 1 }
            for (project in ordered) {
                val card = column().apply {
                    background = rounded(palette.surface, 16, border)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                }
                val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                header.addView(FarmProjectPreview(this, state.projects, project), LinearLayout.LayoutParams(dp(96), dp(76)))
                val identity = column().apply { setPadding(dp(10), 0, 0, 0) }
                identity.addView(text(getString(project.label), 17, true))
                identity.addView(text(if (state.projects.complete(project)) getString(R.string.farm_project_complete)
                    else getString(R.string.farm_project_progress, state.projects.progress(project), project.helpNeeded), 13))
                header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(header)
                card.addView(text(getString(project.description), 13).apply { setPadding(0, dp(8), 0, dp(8)) })
                if (!state.projects.complete(project)) {
                    val progress = state.projects.progress(project)
                    val stage = project.stage(progress)
                    val phase = getString(when (stage) {
                        0 -> R.string.farm_project_stage_site
                        1 -> R.string.farm_project_stage_started
                        else -> R.string.farm_project_stage_advanced
                    })
                    card.addView(text(getString(R.string.farm_project_next_stage, phase, project.nextStageAt(progress) - progress), 12))
                    card.addView(button(getString(if (state.projects.active == project) R.string.farm_project_selected
                        else R.string.farm_project_select)) {
                        state.selectProject(project); showProjects(project); world.invalidate()
                    }.apply { isEnabled = state.projects.active != project },
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                }
                card.addView(button(getString(R.string.farm_project_visit)) {
                    closeBubble(); goToRegion(FarmRegion.HOME); world.focusProject(project)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                card.addView(button(getString(R.string.farm_project_decorate)) { showDecorations(project) },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            }
            body.addView(button(getString(R.string.farm_gifts_title)) { showDecorations() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            body.addView(button(getString(R.string.farm_village_title)) { showVillage() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    private fun showDecorations(slot: FarmProject? = null) {
        showBubble(if (slot == null) getString(R.string.farm_gifts_title)
            else getString(R.string.farm_gift_slot_title, getString(slot.label))) { body ->
            body.addView(text(getString(R.string.farm_gifts_intro, FarmGift.FRIENDSHIP_NEEDED), 13).apply {
                setPadding(0, dp(6), 0, dp(10))
            })
            if (slot != null) {
                val current = state.projects.decoration(slot)
                body.addView(text(if (current == null) getString(R.string.farm_gift_slot_empty)
                    else getString(R.string.farm_gift_slot_current, getString(current.label)), 14, true))
                body.addView(text(getString(R.string.farm_gift_place_desc), 12))
                if (current != null) body.addView(button(getString(R.string.farm_gift_store)) {
                    state.storeGift(slot); showDecorations(slot); world.invalidate()
                    message(getString(R.string.farm_gift_stored_done))
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
            for (gift in FarmGift.entries) {
                val card = column().apply {
                    background = rounded(palette.surface, 16, border)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(FarmProjectPreview(this, state.projects, gift = gift, flower = state.greenhouse.displayFlower), LinearLayout.LayoutParams(dp(76), dp(66)))
                val identity = column().apply { setPadding(dp(8), 0, 0, 0) }
                identity.addView(text(getString(gift.label), 16, true))
                identity.addView(text(getString(R.string.farm_gift_from, getString(gift.villager.label)), 12))
                row.addView(identity, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(row)
                if (!state.projects.owns(gift)) {
                    val friendship = state.village.friendship(gift.villager)
                    if (friendship >= FarmGift.FRIENDSHIP_NEEDED) card.addView(button(getString(R.string.farm_gift_claim)) {
                        val claimed = state.claimGift(gift); showDecorations(slot)
                        if (claimed) showAction(FarmActionEffectsView.Kind.GIFT)
                        if (claimed) message(getString(R.string.farm_gift_received, getString(gift.label)))
                    }, LinearLayout.LayoutParams(-1, -2))
                    else card.addView(text(getString(R.string.farm_gift_until, friendship, FarmGift.FRIENDSHIP_NEEDED), 13))
                } else {
                    val location = state.projects.location(gift)
                    card.addView(text(if (location == null) getString(R.string.farm_gift_stored)
                        else getString(R.string.farm_gift_at, getString(location.label)), 12))
                    if (slot == null) card.addView(button(getString(R.string.farm_gift_choose_place)) {
                        chooseGiftPlace(gift)
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                    else card.addView(button(getString(if (location == slot) R.string.farm_gift_already_here
                        else R.string.farm_gift_place_here)) {
                        if (state.placeGift(gift, slot)) {
                            showDecorations(slot); world.invalidate()
                            showAction(FarmActionEffectsView.Kind.GIFT, caption = "")
                            message(getString(R.string.farm_gift_placed, getString(gift.label)))
                        }
                    }.apply { isEnabled = location != slot }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                }
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            }
            if (slot != null) body.addView(button(getString(R.string.farm_project_visit)) {
                closeBubble(); goToRegion(FarmRegion.HOME); world.focusProject(slot)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addGreenhouseLink(body)
            body.addView(button(getString(R.string.farm_projects_title)) { showProjects(slot) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            body.addView(button(getString(R.string.farm_village_title)) { showVillage() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    private fun chooseGiftPlace(gift: FarmGift) {
        showBubble(getString(gift.label)) { body ->
            body.addView(text(getString(R.string.farm_gift_place_desc), 13).apply { setPadding(0, dp(6), 0, dp(10)) })
            for (slot in FarmProject.entries) {
                val current = state.projects.decoration(slot)
                body.addView(FarmProjectPreview(this, state.projects, slot), LinearLayout.LayoutParams(-1, dp(90)))
                body.addView(text(if (current == null) getString(R.string.farm_gift_slot_empty)
                    else getString(R.string.farm_gift_slot_current, getString(current.label)), 12))
                body.addView(button(getString(R.string.farm_gift_place_at, getString(slot.label))) {
                    val moved = state.placeGift(gift, slot); showDecorations(slot); world.invalidate()
                    if (moved) showAction(FarmActionEffectsView.Kind.GIFT, caption = "")
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }
    }

    private fun addGreenhouseLink(body: LinearLayout) {
        val summary = text("", 12).apply { setPadding(0, dp(8), 0, dp(4)) }; body.addView(summary)
        greenhouseUi += { summary.text = getString(R.string.farm_greenhouse_summary,
            state.greenhouse.readyCount(System.currentTimeMillis()), state.greenhouse.jobs.size, FarmGreenhouseState.SLOTS) }
        body.addView(button(getString(R.string.farm_greenhouse_title)) { showGreenhouse() },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val collect = button(getString(R.string.farm_greenhouse_collect_short)) {
            val count = state.collectFlowers()
            showAction(FarmActionEffectsView.Kind.FLOWERS, count)
            message(getString(if (count > 0) R.string.farm_greenhouse_collected else R.string.farm_greenhouse_collect_none, count)); refresh()
        }
        body.addView(collect)
        greenhouseUi += { collect.visibility = if (state.greenhouse.readyCount(System.currentTimeMillis()) > 0) View.VISIBLE else View.GONE }
    }

    private fun showGreenhouse(slot: Int? = null, journal: Boolean = false) {
        showBubble(getString(if (journal) R.string.farm_greenhouse_journal else R.string.farm_greenhouse_title)) { body ->
            body.addView(text(getString(R.string.farm_greenhouse_intro), 13).apply { setPadding(0, dp(6), 0, dp(10)) })
            if (!state.greenhouse.introUsed) body.addView(text(getString(R.string.farm_greenhouse_welcome), 14, true))
            val tabs = LinearLayout(this)
            for ((index, label) in listOf(R.string.farm_greenhouse_planters, R.string.farm_greenhouse_journal).withIndex()) {
                tabs.addView(button(getString(label)) { showGreenhouse(slot, index == 1) },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), dp(8), dp(2), dp(10)) })
            }
            body.addView(tabs)
            val collect = button("") {
                val count = state.collectFlowers(); showGreenhouse(slot, journal)
                showAction(FarmActionEffectsView.Kind.FLOWERS, count)
                message(getString(if (count > 0) R.string.farm_greenhouse_collected else R.string.farm_greenhouse_collect_none, count))
            }
            body.addView(collect, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            greenhouseUi += {
                val ready = state.greenhouse.readyCount(System.currentTimeMillis())
                collect.text = getString(R.string.farm_greenhouse_collect, ready)
                collect.isEnabled = ready > 0; collect.alpha = if (collect.isEnabled) 1f else .45f
            }
            if (!journal) {
                for (index in (0 until FarmGreenhouseState.SLOTS).sortedBy { if (it == slot) 0 else 1 }) {
                    val job = state.greenhouse.at(index)
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
                    if (job != null) row.addView(FarmFlowerPreview(this, job.flower), LinearLayout.LayoutParams(dp(46), dp(58)))
                    val description = text("", 13)
                    row.addView(description, LinearLayout.LayoutParams(0, -2, 1f)); body.addView(row)
                    greenhouseUi += {
                        description.text = if (job == null) getString(R.string.farm_greenhouse_empty, index + 1)
                            else getString(R.string.farm_greenhouse_job, index + 1, getString(job.flower.label),
                                if (job.readyAt <= System.currentTimeMillis()) getString(R.string.farm_ready)
                                else duration(((job.readyAt - System.currentTimeMillis()).coerceAtLeast(0) + 999).div(1000).toInt()))
                    }
                    if (job == null) body.addView(button(getString(R.string.farm_greenhouse_choose)) { showGreenhouse(index, true) })
                }
                body.addView(button(getString(R.string.farm_greenhouse_journal)) { showGreenhouse(null, true) },
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            } else {
                body.addView(text(getString(R.string.farm_greenhouse_collection, state.greenhouse.collection.size, FarmFlower.entries.size), 15, true))
                if (slot != null) body.addView(text(getString(R.string.farm_greenhouse_target, slot + 1), 13))
                for (flower in FarmFlower.entries) body.addView(flowerCard(flower, slot),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            }
            body.addView(text(getString(R.string.farm_greenhouse_decor_hint), 12).apply { setPadding(0, dp(10), 0, dp(8)) })
            body.addView(button(getString(R.string.farm_greenhouse_visit)) {
                goToRegion(FarmRegion.GREENHOUSE); closeBubble()
            })
            body.addView(button(getString(R.string.farm_workshop_title)) { showWorkshop(FarmRecipe.GARDEN_BOUQUET) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            body.addView(button(getString(R.string.farm_produce_inventory)) { produceInventory() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    private fun flowerCard(flower: FarmFlower, slot: Int?): View {
        val card = column().apply {
            background = rounded(palette.surface, 16, border); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val known = state.greenhouse.knows(flower)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(FarmFlowerPreview(this, flower, !known), LinearLayout.LayoutParams(dp(52), dp(70)))
        val details = column().apply { setPadding(dp(8), 0, 0, 0) }
        details.addView(text(getString(flower.label), 16, true))
        details.addView(text(getString(if (known) R.string.farm_greenhouse_known else R.string.farm_greenhouse_unknown), 12))
        val stock = text("", 12); details.addView(stock)
        greenhouseUi += { stock.text = getString(R.string.farm_product_stock, getString(flower.label), state.greenhouse.count(flower)) }
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(row)
        val revision = state.greenhouse.revision
        if (known) {
            card.addView(text(getString(R.string.farm_greenhouse_grow_time,
                duration(if (state.greenhouse.introUsed) flower.hours * 3600 else 60), money(flower.sale)), 12))
            val grow = button(getString(R.string.farm_greenhouse_grow)) {
                val ok = state.startFlower(flower, false, revision, slot = slot)
                showGreenhouse(); message(getString(if (ok) R.string.farm_greenhouse_started else R.string.farm_greenhouse_unavailable))
                if (ok) showAction(FarmActionEffectsView.Kind.SEEDS, caption = getString(R.string.farm_action_planting))
            }
            card.addView(grow)
            greenhouseUi += { grow.isEnabled = state.greenhouse.canStart(flower, false, slot); grow.alpha = if (grow.isEnabled) 1f else .45f }
            card.addView(button(getString(if (state.greenhouse.displayFlower == flower) R.string.farm_greenhouse_displayed else R.string.farm_greenhouse_display)) {
                state.displayFlower(flower); showGreenhouse(slot, true); world.invalidate()
                showAction(FarmActionEffectsView.Kind.FLOWERS, flower = flower, caption = "")
            }.apply { isEnabled = state.greenhouse.displayFlower != flower }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        if (!flower.base) {
            card.addView(text(getString(R.string.farm_greenhouse_cross_hint), 12).apply { setPadding(0, dp(8), 0, dp(6)) })
            for (parent in flower.parents) {
                val amount = text("", 12); card.addView(amount)
                greenhouseUi += { amount.text = getString(R.string.farm_workshop_ingredient, getString(parent.label), state.greenhouse.count(parent), 1) }
            }
            val cross = button(getString(R.string.farm_greenhouse_cross)) {
                val ok = state.startFlower(flower, true, revision, slot = slot)
                showGreenhouse(); message(getString(if (ok) R.string.farm_greenhouse_started else R.string.farm_greenhouse_unavailable))
                if (ok) showAction(FarmActionEffectsView.Kind.FLOWERS, flower = flower, caption = getString(R.string.farm_action_cross))
            }
            card.addView(cross, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            greenhouseUi += { cross.isEnabled = state.greenhouse.canStart(flower, true, slot); cross.alpha = if (cross.isEnabled) 1f else .45f }
        }
        return card
    }

    private fun confirmFlowerSale(flower: FarmFlower, count: Int) {
        showBubble(getString(R.string.farm_product_sell_title)) { body ->
            body.addView(text(getString(R.string.farm_product_sell_confirm, count, getString(flower.label), money(count.toLong() * flower.sale)), 15, true))
            body.addView(text(getString(R.string.farm_greenhouse_sell_hint), 13))
            body.addView(button(getString(R.string.farm_confirm)) {
                val value = state.sellFlower(flower, count); produceInventory()
                message(getString(R.string.farm_produce_sold, money(value)))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            body.addView(button(getString(R.string.farm_cancel)) { produceInventory() })
        }
    }

    private fun plantedMessage(crop: FarmCrop, count: Int): String {
        showAction(FarmActionEffectsView.Kind.SEEDS, count)
        return getString(if (crop.tree) R.string.farm_orchard_planted else R.string.farm_planted_all, count, getString(crop.label))
    }

    private fun addOrchardLink(body: LinearLayout) {
        body.addView(button(getString(R.string.farm_orchard_title)) { showOrchard() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
    }

    private fun showOrchard() {
        showBubble(getString(R.string.farm_orchard_title)) { body ->
            body.addView(text(getString(R.string.farm_tree_cycle), 13).apply { setPadding(0, dp(6), 0, dp(10)) })
            if (!state.orchardIntroUsed) body.addView(text(getString(R.string.farm_orchard_welcome), 14, true))
            body.addView(text(getString(R.string.farm_orchard_conversion_hint), 13))
            val orchard = state.parcels.indices.filter { state.parcels[it].unlocked && state.parcels[it].use == FarmLandUse.ORCHARD }
            val collect = button("") {
                world.cancelMiniGames()
                val result = state.harvestMany(state.orchardCells(), System.currentTimeMillis())
                showAction(FarmActionEffectsView.Kind.BASKET, result.count)
                message(if (result.count == 0) getString(R.string.farm_orchard_storage_full)
                    else getString(R.string.farm_harvested_many, result.count)); refresh()
            }
            body.addView(collect, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
            visitUi += {
                val count = orchard.sumOf { state.visitSummary(it).ready }
                collect.text = getString(R.string.farm_orchard_collect, count)
                collect.isEnabled = count > 0; collect.alpha = if (collect.isEnabled) 1f else .45f
            }
            for (index in state.parcels.indices.filter { state.parcels[it].unlocked }) {
                val label = getString(R.string.farm_orchard_parcel, index + 1, getString(state.parcels[index].use.label))
                body.addView(button(label) {
                    closeBubble(); goToRegion(FarmRegion.HOME); world.focusParcel(index); parcelMenu(index)
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
                if (index in orchard) {
                    val status = text("", 12).apply { setPadding(0, 0, 0, dp(8)) }; body.addView(status)
                    visitUi += { status.text = parcelStatusLines(index).joinToString("\n") }
                }
            }
            for (crop in FarmCrop.trees) {
                if (!state.cropUnlocked(crop)) {
                    body.addView(text(getString(R.string.farm_orchard_locked, getString(crop.label), crop.orchardParcelRequirement), 13))
                    continue
                }
                body.addView(seedRow(crop), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
                val plant = button("") {
                    world.cancelMiniGames()
                    val planted = state.plantAll(crop, System.currentTimeMillis())
                    message(plantedMessage(crop, planted)); refresh()
                }
                body.addView(plant, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(10) })
                visitUi += {
                    val count = state.plantAllCount(crop)
                    plant.text = getString(R.string.farm_plant_all, count); plant.isEnabled = count > 0
                    plant.alpha = if (plant.isEnabled) 1f else .45f
                }
            }
            addWorkshopLink(body)
            body.addView(button(getString(R.string.farm_village_title)) { showVillage() })
            visitUi.forEach { it() }
        }
    }

    private fun prepareOrderCrop(crop: FarmCrop) {
        if (crop.tree) { showOrchard(); return }
        showBubble(getString(R.string.farm_village_seed_title, getString(crop.label))) { body ->
            body.addView(text(getString(R.string.farm_village_seed_hint), 13).apply {
                setPadding(0, dp(6), 0, dp(12))
            })
            body.addView(seedRow(crop))
            body.addView(button(getString(R.string.farm_village_plant)) {
                state.selected = crop; state.save()
                closeBubble(); goToRegion(FarmRegion.HOME)
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
            body.addView(button(getString(R.string.farm_village_back)) { showVillage() },
                LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        }
    }

    private fun livestockShop() {
        state.advanceLivestock()
        showBubble(getString(R.string.farm_animal_shop)) { body ->
            body.addView(text(getString(R.string.farm_manure_title), 17, true))
            val pit = text("", 15, true)
            body.addView(pit)
            livestockUi.add {
                pit.text = getString(R.string.farm_manure_stock, money(state.livestock.manure),
                    money(state.livestock.manureCapacity()), money(state.livestock.manurePerDay()))
            }
            body.addView(text(getString(R.string.farm_manure_body), 13).apply { setPadding(0, dp(4), 0, dp(12)) })
            body.addView(text(getString(R.string.farm_breeding_rules), 13))
            if (state.livestock.firstEggAvailable) body.addView(text(getString(R.string.farm_product_first_egg), 13, true).apply {
                setPadding(0, dp(8), 0, dp(8))
            })
            for (kind in LivestockKind.entries) {
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(LivestockIcon(this, sprites, kind.female + "_1"), LinearLayout.LayoutParams(dp(70), dp(70)))
                val details = column()
                details.addView(text(getString(kind.label), 17, true))
                details.addView(button(getString(R.string.farm_herd_open)) { livestockPen(kind, true) })
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(row)
                if (!state.livestock.available(kind)) {
                    body.addView(button(getString(R.string.farm_locked_price, money(kind.landPrice))) { livestockPen(kind, false) })
                } else {
                    for (male in listOf(false, true)) {
                        val action = button(getString(R.string.farm_animal_buy, getString(if (male) R.string.farm_male else R.string.farm_female), money(kind.price))) {
                            val ok = state.buyAnimal(kind, male)
                            if (ok) showAction(FarmActionEffectsView.Kind.HEART, caption = "")
                            message(getString(if (ok) R.string.farm_animal_bought else R.string.farm_animal_unavailable)); refresh()
                        }
                        body.addView(action, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
                        livestockUi.add { action.isEnabled = state.coins >= kind.price && state.livestock.count(kind) < LivestockState.CAPACITY }
                    }
                }
            }
        }
    }
    private fun livestockPen(kind: LivestockKind, details: Boolean) {
        habitat.select(kind, false)
        state.advanceLivestock()
        if (!state.livestock.available(kind)) {
            showBubble(getString(kind.label)) { body ->
                body.addView(LivestockIcon(this, sprites, kind.shelter), LinearLayout.LayoutParams(-1, dp(120)))
                body.addView(text(getString(R.string.farm_animal_land_info)))
                if (kind.ordinal != state.livestock.unlocked) {
                    body.addView(text(getString(R.string.farm_animal_order, getString(LivestockKind.entries[kind.ordinal - 1].label))))
                } else if (!state.livestockRequirementMet(kind)) {
                    body.addView(text(requirementText(kind), 14, true))
                } else {
                    val action = button(getString(R.string.farm_locked_price, money(kind.landPrice))) {
                        if (state.unlockLivestock(kind)) { livestockPen(kind, true); message(getString(R.string.farm_unlocked)) }
                        else message(getString(R.string.farm_no_coins))
                        refresh()
                    }
                    body.addView(action)
                    livestockUi.add { action.isEnabled = state.coins >= kind.landPrice }
                }
            }
            return
        }
        showBubble(getString(kind.label)) { body ->
            val summary = text("", 15, true); body.addView(summary)
            livestockUi.add { summary.text = getString(R.string.farm_herd_count, state.livestock.count(kind), LivestockState.CAPACITY) }
            addAnimalCare(body, kind)
            for (group in 0..2) {
                val id = (when (group) { 0 -> kind.female; 1 -> kind.male; else -> kind.young }) + "_1"
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(LivestockIcon(this, sprites, id), LinearLayout.LayoutParams(dp(70), dp(70)))
                val column = column()
                val count = text("", 15, true); column.addView(count)
                fun matching() = state.livestock.animals.filter { it.kind == kind && if (group == 2) !it.adult else it.adult && it.male == (group == 1) }
                livestockUi.add { count.text = getString(R.string.farm_animal_group, getString(when(group) {
                    0 -> R.string.farm_females; 1 -> R.string.farm_males; else -> R.string.farm_young
                }), matching().size) }
                if (group < 2) {
                    column.addView(button(getString(R.string.farm_companion_meet)) { showAnimalRoster(kind, group) })
                }
                row.addView(column, LinearLayout.LayoutParams(0, -2, 1f)); body.addView(row)
            }
            val timing = text("", 13); body.addView(timing)
            livestockUi.add {
                val herd = state.livestock.animals.filter { it.kind == kind }
                val now = System.currentTimeMillis()
                val birth = herd.filter { it.birthAt > 0 }.minOfOrNull { it.birthAt }
                val growth = herd.filter { !it.adult }.minOfOrNull { it.adultAt }
                timing.text = (if (state.livestock.count(kind) >= LivestockState.CAPACITY) getString(R.string.farm_herd_full)
                    else if (birth == null) getString(R.string.farm_pair_needed)
                    else getString(R.string.farm_next_birth, duration(((birth - now).coerceAtLeast(0) / 1000).toInt()))) +
                    (growth?.let { "\n" + getString(R.string.farm_next_adult, duration(((it - now).coerceAtLeast(0) / 1000).toInt())) } ?: "")
            }
            val ration = button("") { state.feedYoung(kind); refresh() }
            body.addView(text(getString(R.string.farm_field_feed_help)))
            body.addView(ration)
            livestockUi.add {
                val now = System.currentTimeMillis()
                val young = state.livestock.animals.filter { it.kind == kind && !it.adult && it.boostUntil <= now }
                val cost = young.size * (kind.ordinal + 1) * 5L
                ration.text = getString(R.string.farm_field_feed, cost, state.largeFields.grain)
                ration.isEnabled = young.isNotEmpty() && state.largeFields.grain >= cost
            }
            body.addView(button(getString(R.string.farm_animal_shop)) { livestockShop() })
            body.addView(text(getString(R.string.farm_breeding_rules), 12))
        }
    }

    private fun animalName(animal: FarmAnimal): String {
        if (animal.name.isNotBlank()) return animal.name
        val names = resources.getStringArray(R.array.farm_companion_names)
        return names[((animal.id - 1) % names.size).toInt()]
    }
    private fun affectionText(animal: FarmAnimal): String = getString(R.string.farm_companion_relation,
        getString(when {
            animal.affection >= 12 -> R.string.farm_companion_friend
            animal.affection >= 6 -> R.string.farm_companion_familiar
            else -> R.string.farm_companion_curious
        }), animal.affection)

    private fun collectAnimals(kind: LivestockKind?) {
        val count = state.collectAnimalProducts(kind)
        showAction(FarmActionEffectsView.Kind.BASKET, count)
        message(getString(if (count > 0) R.string.farm_product_collected else R.string.farm_product_collect_none, count))
        refresh()
    }
    private fun greetAnimals(kind: LivestockKind) {
        val greeted = state.greetAnimals(kind)
        showAction(FarmActionEffectsView.Kind.HEART, greeted.size)
        habitat.greet(state.livestock.animals.filter { it.kind == kind }.map { it.id })
        message(getString(R.string.farm_companion_greeted, greeted.size)); refresh()
    }
    private fun addAnimalCare(body: LinearLayout, kind: LivestockKind?) {
        body.addView(button(getString(R.string.farm_butcher_title)) { showButcher(kind) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        body.addView(text(getString(R.string.farm_product_title), 17, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        body.addView(text(getString(R.string.farm_companion_care), 12))
        val summary = text("", 13).apply { setPadding(0, dp(6), 0, dp(6)) }; body.addView(summary)
        val collect = button("") { collectAnimals(kind) }; body.addView(collect)
        livestockUi += {
            val now = System.currentTimeMillis()
            val ready = state.livestock.readyProducts(kind, now)
            collect.text = getString(R.string.farm_product_collect, ready)
            collect.isEnabled = ready > 0
            summary.text = when {
                ready > 0 -> getString(R.string.farm_product_waiting, ready)
                state.livestock.nextProductAt(kind) != null -> getString(R.string.farm_product_next,
                    duration(((state.livestock.nextProductAt(kind)!! - now).coerceAtLeast(0) / 1000).toInt()))
                else -> getString(R.string.farm_product_no_producer)
            }
        }
        body.addView(text(getString(R.string.farm_product_rules), 12).apply { setPadding(0, dp(6), 0, dp(6)) })
        if (kind != null) {
            body.addView(button(getString(R.string.farm_companion_greet)) { greetAnimals(kind) })
            body.addView(button(getString(R.string.farm_companion_roster)) { showAnimalRoster(kind) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        } else body.addView(button(getString(R.string.farm_companion_visit)) {
            closeBubble(); goToRegion(FarmRegion.LIVESTOCK)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        body.addView(button(getString(R.string.farm_produce_inventory)) { produceInventory() },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(10) })
    }

    private fun showAnimalRoster(kind: LivestockKind, group: Int? = null) {
        state.advanceLivestock()
        showBubble(getString(R.string.farm_companion_roster_title, getString(kind.label))) { body ->
            body.addView(text(getString(R.string.farm_companion_roster_help), 13))
            val animals = state.livestock.animals.filter { it.kind == kind && when (group) {
                0 -> it.adult && !it.male; 1 -> it.adult && it.male; 2 -> !it.adult; else -> true
            } }
            if (animals.isEmpty()) body.addView(text(getString(R.string.farm_companion_empty), 14))
            for (animal in animals) {
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(LivestockIcon(this, sprites, animal.sprite).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(66), dp(66)))
                val card = column().apply { setPadding(dp(6), dp(8), 0, dp(8)) }
                card.addView(text(affectionText(animal), 12))
                card.addView(button(animalName(animal)) { showAnimal(animal.id) })
                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f)); body.addView(row)
            }
            body.addView(button(getString(R.string.farm_herd_open)) { livestockPen(kind, true) })
        }
    }

    private fun showAnimal(id: Long) {
        state.advanceLivestock()
        val animal = state.livestock.find(id) ?: run { message(getString(R.string.farm_companion_gone)); return }
        showBubble(animalName(animal)) { body ->
            body.addView(LivestockIcon(this, sprites, animal.sprite).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(104)))
            body.addView(text(getString(R.string.farm_companion_identity, getString(animal.kind.label),
                getString(if (!animal.adult) R.string.farm_young else if (animal.male) R.string.farm_male else R.string.farm_female)), 14, true))
            val relation = text("", 14, true); body.addView(relation)
            livestockUi += { relation.text = affectionText(animal) }
            body.addView(text(getString(R.string.farm_companion_pet_rules), 12).apply { setPadding(0, dp(6), 0, dp(6)) })
            body.addView(button(getString(R.string.farm_companion_pet)) {
                val changed = state.petAnimal(id)
                showAction(FarmActionEffectsView.Kind.HEART, caption = "")
                habitat.greet(listOf(id)); refresh()
                message(getString(if (changed) R.string.farm_companion_pet_done else R.string.farm_companion_pet_again, animalName(animal)))
            })
            val product = text("", 13).apply { setPadding(0, dp(10), 0, dp(10)) }; body.addView(product)
            livestockUi += {
                val now = System.currentTimeMillis()
                product.text = animal.product?.let {
                    getString(R.string.farm_companion_production, getString(it.label), it.cycleHours) +
                        if (animal.productAt in 0..now) getString(R.string.farm_companion_production_ready)
                        else getString(R.string.farm_companion_production_next,
                            duration(((animal.productAt - now).coerceAtLeast(0) / 1000).toInt()))
                } ?: getString(R.string.farm_companion_no_production)
            }
            body.addView(button(getString(R.string.farm_companion_rename)) { renameAnimal(id) })
            if (animal.adult) body.addView(button(getString(R.string.farm_companion_sell)) { confirmAnimalSale(id) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            if (animal.adult) body.addView(button(getString(R.string.farm_butcher_transform)) { confirmButcher(id) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            body.addView(button(getString(R.string.farm_companion_roster)) { showAnimalRoster(animal.kind) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    private fun renameAnimal(id: Long) {
        val animal = state.livestock.find(id) ?: return
        showBubble(getString(R.string.farm_companion_rename)) { body ->
            body.addView(text(getString(R.string.farm_companion_name_help, LivestockState.NAME_LENGTH), 13))
            val input = EditText(this).apply {
                setText(animal.name); hint = animalName(animal)
                contentDescription = getString(R.string.farm_companion_name_label)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                filters = arrayOf(android.text.InputFilter.LengthFilter(LivestockState.NAME_LENGTH))
                setTextColor(ink); setHintTextColor(border); isSingleLine = true
            }
            body.addView(input, LinearLayout.LayoutParams(-1, -2))
            body.addView(button(getString(R.string.farm_companion_name_save)) {
                state.renameAnimal(id, input.text.toString()); showAnimal(id)
            })
            body.addView(button(getString(R.string.farm_cancel)) { showAnimal(id) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    private fun meatPreview(meat: FarmMeat) = FarmArtView(this, FarmArtView.Kind.MEAT).apply {
        this.meat = meat; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun showButcher(kind: LivestockKind? = null) {
        state.advanceLivestock()
        showBubble(getString(R.string.farm_butcher_title)) { body ->
            body.addView(text(getString(R.string.farm_butcher_intro), 13).apply { setPadding(0, dp(6), 0, dp(10)) })
            for (meat in FarmMeat.entries.filter { kind == null || it.kind == kind }) {
                if (!state.livestock.available(meat.kind) && state.livestock.stock(meat) == 0) continue
                val card = column().apply {
                    background = rounded(palette.surface, 16, border)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(meatPreview(meat), LinearLayout.LayoutParams(dp(62), dp(66)))
                val details = column().apply { setPadding(dp(8), 0, 0, 0) }
                details.addView(text(getString(R.string.farm_product_stock, getString(meat.label), state.livestock.stock(meat)), 15, true))
                details.addView(text(getString(R.string.farm_butcher_yield, meat.portions, money(meat.sale.toLong() * meat.portions)), 12))
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(row)
                if (state.livestock.stock(meat) > 0) card.addView(button(getString(R.string.farm_product_sell_stock,
                    money(state.livestock.stock(meat).toLong() * meat.sale))) { confirmMeatSale(meat, state.livestock.stock(meat)) })
                val recipe = FarmRecipe.entries.first { it.meats.any { ingredient -> ingredient.meat == meat } }
                card.addView(button(getString(R.string.farm_butcher_cook, getString(recipe.label))) { showWorkshop(recipe) })
                val adults = state.livestock.animals.filter { it.kind == meat.kind && it.adult }
                if (adults.isEmpty()) card.addView(text(getString(R.string.farm_butcher_no_adult), 12))
                if (kind == null && adults.isNotEmpty()) card.addView(button(getString(R.string.farm_butcher_adults, adults.size)) {
                    showButcher(meat.kind)
                })
                for (animal in if (kind == null) emptyList() else adults) {
                    val animalRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                    animalRow.addView(LivestockIcon(this, sprites, animal.sprite).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(dp(48), dp(54)))
                    animalRow.addView(button(getString(R.string.farm_butcher_choose, animalName(animal))) { confirmButcher(animal.id) },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
                    card.addView(animalRow)
                }
                body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
            if (state.livestock.unlocked == 0) body.addView(text(getString(R.string.farm_butcher_locked), 13))
            if (kind != null) body.addView(button(getString(R.string.farm_butcher_all_pens)) { showButcher() })
            body.addView(button(getString(R.string.farm_produce_inventory)) { produceInventory() })
        }
    }

    private fun confirmButcher(id: Long) {
        val animal = state.livestock.find(id)?.takeIf { it.adult } ?: return
        val meat = FarmMeat.forKind(animal.kind)
        showBubble(getString(R.string.farm_butcher_transform)) { body ->
            body.addView(LivestockIcon(this, sprites, animal.sprite).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(96)))
            body.addView(text(getString(R.string.farm_butcher_confirm, animalName(animal), meat.portions, getString(meat.label)), 15, true))
            body.addView(meatPreview(meat), LinearLayout.LayoutParams(-1, dp(72)))
            body.addView(text(getString(R.string.farm_butcher_confirm_help), 13).apply { setPadding(0, dp(8), 0, dp(10)) })
            if (animal.male && state.livestock.animals.count { it.kind == animal.kind && it.adult && it.male } == 1) {
                body.addView(text(getString(R.string.farm_butcher_last_male), 13, true).apply { setPadding(0, 0, 0, dp(10)) })
            }
            if (!animal.male && state.livestock.animals.count { it.kind == animal.kind && it.adult && !it.male } == 1) {
                body.addView(text(getString(R.string.farm_butcher_last_female), 13, true).apply { setPadding(0, 0, 0, dp(10)) })
            }
            // Cancellation comes first; only the explicitly named second button removes the animal.
            body.addView(button(getString(R.string.farm_cancel)) { showAnimal(id) })
            body.addView(button(getString(R.string.farm_butcher_confirm_yes, animalName(animal))) {
                val count = state.butcherAnimal(id)
                showButcher(animal.kind)
                if (count > 0) showAction(FarmActionEffectsView.Kind.PANTRY, count)
                message(getString(if (count > 0) R.string.farm_butcher_done else R.string.farm_butcher_failed,
                    count, getString(meat.label)))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    private fun confirmMeatSale(meat: FarmMeat, quantity: Int) {
        val count = minOf(quantity, state.livestock.stock(meat))
        showBubble(getString(R.string.farm_product_sell_title)) { body ->
            body.addView(meatPreview(meat), LinearLayout.LayoutParams(-1, dp(72)))
            body.addView(text(getString(R.string.farm_product_sell_confirm, count, getString(meat.label),
                money(count.toLong() * meat.sale)), 15, true))
            body.addView(button(getString(R.string.farm_confirm)) {
                val value = state.sellMeat(meat, count)
                produceInventory(); message(getString(R.string.farm_produce_sold, money(value)))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            body.addView(button(getString(R.string.farm_cancel)) { produceInventory() })
        }
    }

    private fun confirmAnimalSale(id: Long) {
        val animal = state.livestock.find(id) ?: return
        showBubble(getString(R.string.farm_companion_sell)) { body ->
            body.addView(text(getString(R.string.farm_companion_sell_confirm, animalName(animal), money(animal.kind.sale)), 15, true))
            body.addView(text(getString(R.string.farm_companion_sell_help), 12).apply { setPadding(0, dp(10), 0, dp(10)) })
            body.addView(button(getString(R.string.farm_companion_sell_yes)) {
                val value = state.sellAnimal(id)
                showAnimalRoster(animal.kind)
                message(getString(if (value > 0) R.string.farm_companion_sold else R.string.farm_companion_sale_failed,
                    animalName(animal)))
            })
            body.addView(button(getString(R.string.farm_cancel)) { showAnimal(id) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    /** What a pen still asks for: harvests, and a working herd of the animal before it. */
    private fun requirementText(kind: LivestockKind): String {
        val previous = LivestockKind.entries.getOrNull(kind.ordinal - 1)
            ?: return getString(R.string.farm_animal_requirement_first, kind.harvestsNeeded)
        return getString(R.string.farm_animal_requirement, kind.harvestsNeeded,
            FarmState.LIVESTOCK_HERD_NEEDED, getString(previous.label))
    }

    private fun fieldShop() {
        fieldView.stop()
        showBubble(getString(R.string.farm_field_silo)) { body ->
            val stock = text("", 18, true)
            body.addView(stock)
            livestockUi.add { stock.text = getString(R.string.farm_field_stock, state.largeFields.grain) }
            val sell = button("") {
                val gained = state.sellGrain()
                if (gained > 0) message(getString(R.string.farm_grain_sold, money(gained)))
                refresh()
            }
            body.addView(sell)
            livestockUi.add {
                sell.text = getString(R.string.farm_field_sell, money(state.largeFields.grain * state.grainPrice()))
                sell.isEnabled = state.largeFields.grain > 0
            }

            body.addView(text(getString(R.string.farm_silo_title), 17, true).apply { setPadding(0, dp(10), 0, 0) })
            body.addView(text(getString(R.string.farm_silo_body), 13).apply { setPadding(0, dp(4), 0, dp(8)) })
            val siloLevel = text("", 13)
            body.addView(siloLevel)
            livestockUi.add { siloLevel.text = getString(R.string.farm_silo_level, money(state.grainPrice())) }
            if (state.largeFields.silo < 3) {
                val cost = state.siloUpgradeCost(state.largeFields.silo + 1)
                val upgrade = button(getString(R.string.farm_silo_upgrade, money(cost))) {
                    message(getString(if (state.upgradeSilo()) R.string.farm_silo_upgraded else R.string.farm_no_coins))
                    closeBubble(); refresh()
                }
                body.addView(upgrade, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                livestockUi.add { upgrade.isEnabled = state.coins >= cost }
            }
            body.addView(text(getString(R.string.farm_field_rules)))
            state.largeFields.fields.forEach { f ->
                val open = f.index < state.largeFields.unlocked
                val label = if (open) getString(R.string.farm_field_number, f.index + 1)
                    else getString(R.string.farm_field_buy, f.index + 1, money(f.price))
                val action = button(label) {
                    if (open) { state.largeFields.selected = f.index; state.save() }
                    else if (!state.unlockField(f.index)) return@button
                    fieldView.resetMotion(); closeBubble(); refresh()
                }
                body.addView(action)
                livestockUi.add { action.isEnabled = open || (f.index == state.largeFields.unlocked && state.coins >= f.price) }
            }
        }
    }
    private fun shop(bonuses: Boolean = false) {
        if (world.region == FarmRegion.GREENHOUSE) { showGreenhouse(); return }
        if (world.region == FarmRegion.FIELDS) { fieldShop(); return }
        if (world.region == FarmRegion.LIVESTOCK) { livestockShop(); return }
        showBubble(getString(R.string.farm_shop_title)) { body ->
            val tabs = LinearLayout(this)
            listOf(R.string.farm_use_crops, R.string.farm_bonuses).forEachIndexed { i, label ->
                tabs.addView(button(getString(label)) { shop(i == 1) }.apply {
                    alpha = if (bonuses == (i == 1)) 1f else .65f
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), dp(6), dp(2), dp(10)) })
            }
            body.addView(tabs)
            addOrchardLink(body)
            if (bonuses) {
                body.addView(text(getString(R.string.farm_visit_tools_title), 17, true))
                body.addView(text(getString(R.string.farm_visit_tools_body), 13).apply {
                    setPadding(0, dp(6), 0, dp(12))
                })

                // Shown as "lucky seed": it is a chance, rolled when the plant is watered, of a
                // better harvest quality. The state field and these string keys still say
                // fertilizer - fertilizerLevel is written into the save file and cannot be renamed.
                body.addView(text(getString(R.string.farm_fertilizer_title), 17, true).apply { setPadding(0, dp(6), 0, 0) })
                body.addView(text(getString(R.string.farm_fertilizer_body)).apply { setPadding(0, dp(6), 0, dp(12)) })
                body.addView(text(getString(R.string.farm_fertilizer_level, state.criticalChance()), 13)
                    .apply { setPadding(0, 0, 0, dp(10)) })
                if (state.fertilizerLevel < 2) {
                    val cost = state.fertilizerUpgradeCost(state.fertilizerLevel + 1)
                    val buy = button(getString(R.string.farm_fertilizer_upgrade, money(cost))) {
                        message(getString(if (state.upgradeFertilizer()) R.string.farm_fertilizer_upgraded else R.string.farm_no_coins))
                        refresh()
                    }
                    purchases.add(buy to cost)
                    body.addView(buy, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
                }

                body.addView(text(getString(R.string.farm_aim_title), 17, true).apply { setPadding(0, dp(6), 0, 0) })
                body.addView(text(getString(R.string.farm_aim_body)).apply { setPadding(0, dp(6), 0, dp(12)) })
                body.addView(text(if (state.aimLevel == 0) getString(R.string.farm_aim_level_none)
                    else getString(R.string.farm_aim_level, state.aimBonusPercent()), 13)
                    .apply { setPadding(0, 0, 0, dp(10)) })
                if (state.aimLevel < 2) {
                    val cost = state.aimUpgradeCost(state.aimLevel + 1)
                    val buy = button(getString(R.string.farm_aim_upgrade, money(cost))) {
                        message(getString(if (state.upgradeAim()) R.string.farm_aim_upgraded else R.string.farm_no_coins))
                        refresh()
                    }
                    purchases.add(buy to cost)
                    body.addView(buy, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
                }
            } else FarmCrop.ladder.filter { state.cropUnlocked(it) }.sortedWith(dearestFirst).forEach { crop ->
                body.addView(seedRow(crop), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }
    /** The shop and the seed bag both list the dearest seed first, so the newest crop sits on top. */
    private val dearestFirst = compareByDescending<FarmCrop> { it.cost }.thenByDescending { it.rank }

    /** One shop line. Only unlocked seeds reach the shop; the next one is announced on its parcel sign. */
    private fun seedRow(crop: FarmCrop): LinearLayout {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = rounded(palette.surface, 16)
            setPadding(dp(6), dp(8), dp(8), dp(8))
        }
        row.addView(preview(crop), LinearLayout.LayoutParams(dp(66), dp(80)))
        val details = column().apply { setPadding(dp(10), 0, 0, 0) }
        details.addView(text(getString(crop.label), 16, true))
        details.addView(text(if (crop.tree) getString(R.string.farm_orchard_details, crop.fruitCount, money(crop.sale),
            duration(crop.seconds), duration(crop.regrowthSeconds)) else
            getString(R.string.farm_shop_details, duration(crop.seconds), money(crop.sale), perHour(crop)), 12))
        val count = text("", 12)
        stockLabels.add(count to crop); details.addView(count)
        val buy = LinearLayout(this)
        // A third button buys exactly what the empty cells need - clicking x1 sixty times is not play.
        val fill = state.emptyCells(crop.tree).coerceAtMost(FarmState.MAX_SEED_BATCH)
        val offers = listOf(1, 6) + if (fill > 6) listOf(fill) else emptyList()
        offers.forEachIndexed { index, quantity ->
            val price = quantity.toLong() * crop.cost
            val label = if (index > 1) getString(R.string.farm_buy_fill, quantity, money(price))
                else getString(R.string.farm_buy_short, quantity, money(price))
            val action = button(label) {
                val bought = state.buy(crop, quantity)
                if (bought) showAction(FarmActionEffectsView.Kind.SEEDS, quantity)
                message(getString(if (bought) R.string.farm_bought else R.string.farm_no_coins))
                refresh()
            }
            purchases.add(action to price)
            buy.addView(action, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, dp(6), dp(4), 0) })
        }
        details.addView(buy)
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun inventory() {
        if (world.region == FarmRegion.GREENHOUSE) { showGreenhouse(); return }
        if (world.region == FarmRegion.LIVESTOCK) { livestockShop(); return }
        showBubble(getString(R.string.farm_inventory)) { body ->
            val available = FarmCrop.entries.filter { state.seeds[it.ordinal] > 0 }.sortedWith(dearestFirst)
            if (available.isEmpty()) body.addView(text(getString(R.string.farm_inventory_empty)).apply { setPadding(dp(8), dp(12), dp(8), dp(12)) })
            available.forEach { crop ->
                val current = crop == state.selected
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = if (current) rounded(palette.raised, 14, palette.accent) else rounded(sage, 14)
                    isFocusable = true
                    setOnClickListener { state.selected = crop; state.save(); closeBubble(); refresh() }
                }
                row.addView(preview(crop), LinearLayout.LayoutParams(dp(60), dp(60)))
                val details = column().apply { setPadding(dp(10), 0, 0, 0) }
                details.addView(text(getString(crop.label), 16, current))
                details.addView(text(getString(R.string.farm_stock_count, state.seeds[crop.ordinal]), 12))
                if (current) details.addView(text(getString(R.string.farm_currently_selected), 12, true).apply {
                    setTextColor(palette.accent)
                })
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                // Fills every free cell of the farm with this seed, parcel 1 first. The count is what
                // will really go in: the smaller of the bag and the free, cleared cells.
                val count = state.plantAllCount(crop)
                row.addView(button(getString(R.string.farm_plant_all, count)) {
                    val planted = state.plantAll(crop, System.currentTimeMillis())
                    closeBubble()
                    message(if (planted > 0) plantedMessage(crop, planted)
                        else getString(R.string.farm_plant_all_none))
                    refresh()
                }.apply {
                    isEnabled = count > 0; alpha = if (count > 0) 1f else .45f
                }, LinearLayout.LayoutParams(-2, dp(48)).apply { leftMargin = dp(6) })
                body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
            body.addView(button(getString(R.string.farm_shop)) { shop() })
        }
    }
    private fun produceInventory() {
        showBubble(getString(R.string.farm_produce_inventory)) { body ->
            val crops = FarmCrop.entries.filter { state.cropProduceTotal(it) > 0 }
            val animalProducts = FarmAnimalProduct.entries.filter { state.livestock.stock(it) > 0 }
            val preparations = FarmRecipe.entries.filter { state.workshop.count(it) > 0 }
            val flowers = FarmFlower.entries.filter { state.greenhouse.count(it) > 0 }
            val meats = FarmMeat.entries.filter { state.livestock.stock(it) > 0 }
            addWorkshopLink(body)
            addGreenhouseLink(body)
            if (state.livestock.unlocked > 0 || meats.isNotEmpty()) body.addView(button(getString(R.string.farm_butcher_title)) { showButcher() })
            if (crops.isEmpty() && animalProducts.isEmpty() && preparations.isEmpty() && flowers.isEmpty() && meats.isEmpty()) {
                body.addView(text(getString(R.string.farm_produce_empty)).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                })
                return@showBubble
            }
            val sellAll = button(getString(R.string.farm_sell_all_produce)) { confirmSellAllProduce() }
            body.addView(button(getString(R.string.farm_village_title)) { showVillage() },
                LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
            body.addView(sellAll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            for (meat in meats) {
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(meatPreview(meat), LinearLayout.LayoutParams(dp(56), dp(62)))
                val details = column().apply { setPadding(dp(8), 0, 0, dp(10)) }
                val count = state.livestock.stock(meat)
                details.addView(text(getString(R.string.farm_product_stock, getString(meat.label), count), 14, true))
                details.addView(button(getString(R.string.farm_product_sell_one, money(meat.sale.toLong()))) { confirmMeatSale(meat, 1) })
                details.addView(button(getString(R.string.farm_product_sell_stock, money(count.toLong() * meat.sale))) { confirmMeatSale(meat, count) })
                details.addView(button(getString(R.string.farm_workshop_title)) {
                    showWorkshop(FarmRecipe.entries.first { it.meats.any { ingredient -> ingredient.meat == meat } })
                })
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f)); body.addView(row)
            }
            for (flower in flowers) {
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(FarmFlowerPreview(this, flower), LinearLayout.LayoutParams(dp(56), dp(66)))
                val details = column().apply { setPadding(dp(8), dp(6), 0, dp(10)) }
                val count = state.greenhouse.count(flower)
                details.addView(text(getString(R.string.farm_product_stock, getString(flower.label), count), 14, true))
                details.addView(button(getString(R.string.farm_product_sell_stock, money(count.toLong() * flower.sale))) {
                    confirmFlowerSale(flower, count)
                })
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f)); body.addView(row)
            }
            for (recipe in preparations) {
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(preparationPreview(recipe), LinearLayout.LayoutParams(dp(56), dp(66)))
                val details = column().apply { setPadding(dp(8), dp(6), 0, dp(10)) }
                val count = state.workshop.count(recipe)
                details.addView(text(getString(R.string.farm_product_stock, getString(recipe.label), count), 14, true))
                if (state.requestedPreparation(recipe) > 0) details.addView(text(getString(R.string.farm_workshop_requested), 12, true))
                details.addView(button(getString(R.string.farm_product_sell_one, money(recipe.sale.toLong()))) {
                    confirmPreparationSale(recipe, 1)
                })
                details.addView(button(getString(R.string.farm_product_sell_stock, money(count.toLong() * recipe.sale))) {
                    confirmPreparationSale(recipe, count)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f)); body.addView(row)
            }
            if (animalProducts.isNotEmpty()) {
                body.addView(text(getString(R.string.farm_product_inventory_help), 12).apply { setPadding(0, 0, 0, dp(8)) })
                for (product in animalProducts) {
                    val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
                    row.addView(FarmArtView(this, FarmArtView.Kind.ANIMAL_PRODUCT).apply {
                        this.product = product; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(dp(56), dp(62)))
                    val details = column().apply { setPadding(dp(8), 0, 0, 0) }
                    val count = state.livestock.stock(product)
                    details.addView(text(getString(R.string.farm_product_stock, getString(product.label), count), 14, true))
                    details.addView(button(getString(R.string.farm_product_sell_one, money(product.sale.toLong()))) {
                        confirmProductSale(product, 1)
                    })
                    details.addView(button(getString(R.string.farm_product_sell_stock, money(count.toLong() * product.sale))) {
                        confirmProductSale(product, count)
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                    row.addView(details, LinearLayout.LayoutParams(0, -2, 1f)); body.addView(row)
                }
            }
            crops.forEach { crop ->
                val group = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(palette.surface, 16, border)
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                }
                val cropBadge = column().apply {
                    gravity = Gravity.CENTER
                    setPadding(0, 0, dp(6), 0)
                }
                cropBadge.addView(preview(crop), LinearLayout.LayoutParams(dp(46), dp(48)))
                cropBadge.addView(text(getString(crop.label), 11, true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(dp(58), -2))
                cropBadge.addView(text("×${state.cropProduceTotal(crop)}", 12, true).apply {
                    gravity = Gravity.CENTER
                    setTextColor(palette.secondary)
                }, LinearLayout.LayoutParams(dp(58), -2))
                if (state.requestedProduce(crop) > 0) cropBadge.addView(
                    text(getString(R.string.farm_village_stock_hint, state.requestedProduce(crop)), 11).apply {
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(58), -2))
                group.addView(cropBadge, LinearLayout.LayoutParams(dp(64), -1))
                val rows = column()
                FarmCropQuality.entries.forEach { quality ->
                    val count = state.produce[crop.ordinal][quality.ordinal]
                    if (count > 0) rows.addView(qualitySellRow(crop, quality, count),
                        LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(3) })
                }
                group.addView(rows, LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(group, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }
    private fun confirmPreparationSale(recipe: FarmRecipe, quantity: Int) {
        val count = minOf(quantity, state.workshop.count(recipe))
        showBubble(getString(R.string.farm_confirm_sale_title)) { body ->
            body.addView(text(getString(R.string.farm_product_sell_confirm, count, getString(recipe.label),
                money(count.toLong() * recipe.sale)), 15, true))
            if (state.requestedPreparation(recipe) > 0) {
                body.addView(text(getString(R.string.farm_workshop_requested), 13))
                body.addView(button(getString(R.string.farm_village_title)) { showVillage() })
            }
            body.addView(button(getString(R.string.farm_confirm)) {
                val value = state.sellPreparation(recipe, count)
                produceInventory(); message(getString(R.string.farm_produce_sold, money(value)))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            body.addView(button(getString(R.string.farm_cancel)) { produceInventory() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }
    private fun confirmProductSale(product: FarmAnimalProduct, quantity: Int) {
        val count = minOf(quantity, state.livestock.stock(product))
        showBubble(getString(R.string.farm_product_sell_title)) { body ->
            body.addView(text(getString(R.string.farm_product_sell_confirm, count, getString(product.label),
                money(count.toLong() * product.sale)), 15, true))
            body.addView(button(getString(R.string.farm_confirm)) {
                val value = state.sellAnimalProduct(product, count)
                produceInventory(); message(getString(R.string.farm_produce_sold, money(value)))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            body.addView(button(getString(R.string.farm_cancel)) { produceInventory() },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }
    private fun qualitySellRow(crop: FarmCrop, quality: FarmCropQuality, count: Int) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(qualityTint(quality), 12, qualityColor(quality))
        setPadding(dp(7), dp(3), dp(5), dp(3))
        val key = crop to quality
        var selected = produceSellSelection[key]?.coerceIn(1, count) ?: 1
        produceSellSelection[key] = selected
        addView(qualityChip(quality, count), LinearLayout.LayoutParams(0, dp(32), 1f))
        val selectedText = text(selected.toString(), 15, true).apply {
            gravity = Gravity.CENTER
            setTextColor(ink)
        }
        lateinit var sellButton: Button
        fun update(delta: Int) {
            val next = (selected + delta).coerceIn(1, count)
            if (next == selected) return
            selected = next
            produceSellSelection[key] = selected
            selectedText.text = selected.toString()
            sellButton.text = getString(R.string.farm_sell_selected_produce,
                money(selected.toLong() * crop.sale * quality.multiplier))
        }
        addView(stepButton("−") { update(-1) }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { leftMargin = dp(4) })
        addView(selectedText, LinearLayout.LayoutParams(dp(32), dp(36)))
        sellButton = button(getString(R.string.farm_sell_selected_produce, money(selected.toLong() * crop.sale * quality.multiplier))) {
            confirmProduceSale(crop, quality, selected)
        }
        addView(sellButton, LinearLayout.LayoutParams(dp(98), dp(40)).apply { leftMargin = dp(5); rightMargin = dp(5) })
        addView(stepButton("+") { update(1) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        contentDescription = getString(R.string.farm_quality_count, getString(quality.label), count)
    }
    private fun confirmProduceSale(crop: FarmCrop, quality: FarmCropQuality, quantity: Int) {
        val amount = quantity.toLong() * crop.sale * quality.multiplier
        showBubble(getString(R.string.farm_confirm_sale_title)) { body ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(qualityTint(quality), 16, qualityColor(quality))
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            row.addView(preview(crop), LinearLayout.LayoutParams(dp(58), dp(62)))
            val details = column().apply { setPadding(dp(10), 0, 0, 0) }
            details.addView(text(getString(R.string.farm_confirm_sale_body,
                quantity, getString(crop.label), getString(quality.label)), 16, true).apply {
                setTextColor(qualityTextColor(quality))
            })
            details.addView(text(getString(R.string.farm_confirm_sale_value, money(amount)), 13).apply {
                setTextColor(ink)
            })
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            actions.addView(button(getString(R.string.farm_cancel)) { produceInventory() },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
            actions.addView(button(getString(R.string.farm_confirm)) {
                val gained = state.sellProduce(crop, quality, quantity)
                val left = state.produce[crop.ordinal][quality.ordinal]
                val key = crop to quality
                if (left <= 0) produceSellSelection.remove(key) else produceSellSelection[key] = quantity.coerceAtMost(left)
                message(getString(R.string.farm_produce_sold, money(gained)))
                refresh(); produceInventory()
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
            body.addView(actions)
        }
    }
    private fun confirmSellAllProduce() {
        val amount = state.produceValue()
        showBubble(getString(R.string.farm_confirm_sale_title)) { body ->
            body.addView(text(getString(R.string.farm_confirm_sale_all_body, state.produceCount()), 16, true).apply {
                setTextColor(ink); setPadding(0, 0, 0, dp(6))
            })
            body.addView(text(getString(R.string.farm_confirm_sale_value, money(amount)), 13).apply {
                setTextColor(ink); setPadding(0, 0, 0, dp(12))
            })
            if (state.village.orders.any { order -> order.items.any { state.cropProduceTotal(it.crop) > 0 } ||
                    order.preparation?.let { state.workshop.count(it) > 0 } == true }) {
                body.addView(text(getString(R.string.farm_village_sale_hint), 13).apply {
                    setPadding(0, 0, 0, dp(8))
                })
                body.addView(button(getString(R.string.farm_village_title)) { showVillage() },
                    LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })
            }
            val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            actions.addView(button(getString(R.string.farm_cancel)) { produceInventory() },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
            actions.addView(button(getString(R.string.farm_confirm)) {
                val gained = state.sellProduce()
                produceSellSelection.clear()
                message(getString(R.string.farm_produce_sold, money(gained)))
                refresh(); produceInventory()
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
            body.addView(actions)
        }
    }
    private fun stepButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(ink); isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(cream, 12, border), null)
        setOnClickListener { action() }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    startStepRepeat(action)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    val repeated = stepRepeatStarted
                    stopStepRepeat()
                    if (!repeated && event.actionMasked == MotionEvent.ACTION_UP) action()
                    true
                }
                else -> true
            }
        }
    }
    private fun startStepRepeat(action: () -> Unit) {
        stopStepRepeat()
        stepRepeatDelay = 260L
        stepRepeatStarted = false
        val repeat = object : Runnable {
            override fun run() {
                stepRepeatStarted = true
                action()
                stepRepeatDelay = (stepRepeatDelay * .78f).toLong().coerceAtLeast(42L)
                handler.postDelayed(this, stepRepeatDelay)
            }
        }
        stepRepeat = repeat
        handler.postDelayed(repeat, 360L)
    }
    private fun stopStepRepeat() {
        stepRepeat?.let { handler.removeCallbacks(it) }
        stepRepeat = null
    }
    private fun qualityChip(quality: FarmCropQuality, count: Int) = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        addView(text("★", 22, true).apply {
            gravity = Gravity.CENTER
            setTextColor(qualityColor(quality))
        }, LinearLayout.LayoutParams(dp(30), dp(30)))
        contentDescription = getString(R.string.farm_quality_count, getString(quality.label), count)
    }
    private fun qualityColor(quality: FarmCropQuality) = when (quality) {
        FarmCropQuality.COMMON -> Color.rgb(112, 137, 80)
        FarmCropQuality.RARE -> Color.rgb(53, 132, 194)
        FarmCropQuality.EPIC -> Color.rgb(139, 73, 184)
        FarmCropQuality.LEGENDARY -> Color.rgb(218, 137, 25)
    }
    private fun qualityTint(quality: FarmCropQuality) = palette.blend(palette.surface, qualityColor(quality), if (palette.isLight) 0.12f else 0.18f)
    private fun qualityTextColor(quality: FarmCropQuality) = com.Atom2Universe.app.games.kit.KitPalette.ensureContrast(qualityColor(quality), qualityTint(quality), 4.5)
    private fun parcelMenu(index: Int) {
        val land = state.parcels[index]
        showBubble(getString(R.string.farm_parcel_label, index + 1), index) { body ->
            body.addView(text(getString(R.string.farm_parcel_size, FarmLayout.lands[index].columns,
                FarmLayout.lands[index].rows, FarmLayout.lands[index].capacity)).apply { setPadding(0, dp(8), 0, dp(12)) })
            if (!land.unlocked) {
                body.addView(text(getString(R.string.farm_unlock_new_body)))
                // The seed is the real purchase; say so before the price.
                FarmCrop.ladder.firstOrNull { it.rank == index + 1 }?.let { seed ->
                    body.addView(text(getString(R.string.farm_unlock_seed, getString(seed.label)), 16, true)
                        .apply { setPadding(0, dp(10), 0, 0) })
                    body.addView(text(getString(R.string.farm_shop_details, duration(seed.seconds),
                        money(seed.sale), perHour(seed)), 13))
                }
                if (!state.unlockAvailable(index)) {
                    body.addView(text(getString(R.string.farm_unlock_order, index))
                        .apply { setPadding(0, dp(12), 0, 0) })
                } else {
                    val price = state.unlockCost(index).toLong()
                    val buy = button(getString(R.string.farm_locked_price, money(price))) {
                        if (state.unlock(index)) {
                            closeBubble(); message(getString(R.string.farm_unlocked))
                            showAction(FarmActionEffectsView.Kind.BUILD, caption = "")
                        }
                        else message(getString(R.string.farm_no_coins))
                        refresh()
                    }
                    purchases.add(buy to price)
                    body.addView(buy, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                }
            } else {
                body.addView(text(getString(land.use.label), 16, true))
                if (land.use == FarmLandUse.ORCHARD) {
                    body.addView(text(getString(R.string.farm_tree_cycle), 13))
                    body.addView(button(getString(R.string.farm_orchard_title)) { showOrchard() })
                }
                addVisitActions(body, index)
                val targetUse = if (land.use == FarmLandUse.CROPS) FarmLandUse.ORCHARD else FarmLandUse.CROPS
                body.addView(text(getString(R.string.farm_orchard_conversion_hint), 12).apply { setPadding(0, dp(8), 0, dp(6)) })
                val convert = button(getString(R.string.farm_orchard_convert, getString(targetUse.label))) {
                    if (state.changeUse(index, targetUse)) { parcelMenu(index); refresh() }
                }
                body.addView(convert)
                visitUi += { convert.isEnabled = state.canChangeUse(index, targetUse); convert.alpha = if (convert.isEnabled) 1f else .45f }
                val careful = button(getString(R.string.farm_visit_careful, FarmState.CAREFUL_WATERING_BONUS)) {
                    closeBubble()
                    if (world.startCarefulWatering(index)) message(getString(R.string.farm_visit_careful_hint))
                    refresh()
                }
                body.addView(careful, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
                visitUi += {
                    careful.isEnabled = state.visitSummary(index).thirsty > 0
                    careful.alpha = if (careful.isEnabled) 1f else .45f
                    careful.visibility = if (careful.isEnabled) View.VISIBLE else View.GONE
                }
                parcelStatusLines(index).forEach { line ->
                    body.addView(text(line, 14).apply { setPadding(0, 0, 0, dp(4)) })
                }
            }
        }
    }
    /** Grouped, at-a-glance status of a parcel's spaces: e.g. "3 empty spaces", "5 Radish · Growing". */
    private fun parcelStatusLines(parcel: Int): List<String> {
        val now = System.currentTimeMillis()
        var empty = 0; var debris = 0
        val byCropState = linkedMapOf<Pair<FarmCrop, Int>, Int>()
        FarmLayout.cells(parcel).forEach { i ->
            val p = state.plots[i]
            when {
                p.debris != 0 -> debris++
                p.crop == null -> empty++
                else -> {
                    val stateIndex = when { !p.watered -> 0; p.progress(now) >= 1f -> 2; else -> 1 }
                    val key = p.crop!! to stateIndex
                    byCropState[key] = (byCropState[key] ?: 0) + 1
                }
            }
        }
        val lines = mutableListOf<String>()
        if (empty > 0) lines += getString(R.string.farm_parcel_status_empty, empty)
        if (debris > 0) lines += getString(R.string.farm_parcel_status_debris, debris)
        byCropState.forEach { (key, count) ->
            val (crop, stateIndex) = key
            val stateLabel = getString(when (stateIndex) {
                0 -> R.string.farm_needs_water; 2 -> R.string.farm_ready; else -> R.string.farm_field_grow
            })
            lines += getString(R.string.farm_parcel_status_line, count, getString(crop.label), stateLabel)
        }
        return lines
    }
    private fun interact(index: Int) {
        val p = state.plots[index]
        val parcel = FarmLayout.parcelOf(index)
        val now = System.currentTimeMillis()
        val result = when {
            !state.parcels[parcel].unlocked -> { parcelMenu(parcel); return }
            p.debris != 0 -> { state.clean(index); getString(R.string.farm_cleaned) }
            p.crop == null -> when {
                state.selected.tree != (state.parcels[parcel].use == FarmLandUse.ORCHARD) -> getString(R.string.farm_wrong_use)
                state.seeds[state.selected.ordinal] == 0 -> { inventory(); return }
                state.plant(index, now) -> {
                    showAction(FarmActionEffectsView.Kind.SEEDS)
                    getString(if (p.crop?.tree == true) R.string.farm_orchard_planted_one else if (p.rich) R.string.farm_planted_rich else R.string.farm_planted_water,
                        getString(state.selected.label))
                }
                else -> getString(R.string.farm_no_seeds)
            }
            p.progress(now) >= 1f -> {
                val result = state.harvestMany(state.harvestTargets(index), now)
                showAction(FarmActionEffectsView.Kind.BASKET, result.count)
                if (result.count > 1) getString(R.string.farm_harvested_many, result.count)
                else getString(R.string.farm_harvested_one, harvestStackLabel(result.stacks.firstOrNull()))
            }
            // A thirsty plant is handled by the map's instant parcel watering.
            // Name and quality first: "growing" goes without saying for a plant that is not ripe yet.
            else -> getString(R.string.farm_plant_growing, getString(p.crop!!.label),
                getString(p.quality().label), duration(p.remaining(now)))
        }
        message(result); refresh()
    }
    private fun debrisCleared(index: Int) {
        showAction(FarmActionEffectsView.Kind.BUILD, caption = "")
        message(getString(R.string.farm_cleaned)); refresh()
    }
    private fun watered(count: Int) {
        showAction(FarmActionEffectsView.Kind.WATER, count)
        message(getString(R.string.farm_watering_done, count))
        refresh()
    }
    private fun harvested(index: Int, result: FarmHarvestResult) {
        showAction(FarmActionEffectsView.Kind.BASKET, result.count)
        message(if (result.count == 0 && state.plots[index].crop?.tree == true) getString(R.string.farm_orchard_storage_full)
            else if (result.count > 1) getString(R.string.farm_harvested_many, result.count)
            else getString(R.string.farm_harvested_one, harvestStackLabel(result.stacks.firstOrNull())))
        refresh()
    }
    private fun harvestStackLabel(stack: FarmHarvestStack?): String = stack?.let {
        getString(R.string.farm_harvest_stack_label, getString(it.crop.label), getString(it.quality.label))
    } ?: getString(R.string.farm_produce_inventory)
    private fun removePlant(index: Int) {
        if (state.plots[index].crop == null) return
        showBubble(getString(R.string.farm_clear), FarmLayout.parcelOf(index)) { body ->
            body.addView(text(getString(if (state.plots[index].crop?.tree == true) R.string.farm_orchard_remove_confirm
                else R.string.farm_clear_confirm)).apply { setPadding(0, dp(10), 0, dp(12)) })
            body.addView(button(getString(R.string.farm_clear)) { state.clear(index); closeBubble(); refresh() })
        }
    }
    private fun refreshHabitat() {
        val kind = habitat.selected
        habitatTitle.text = getString(R.string.farm_page_title, getString(kind.label), kind.ordinal + 1, LivestockKind.entries.size)
        habitatCount.text = if (state.livestock.available(kind)) getString(R.string.farm_herd_count, state.livestock.count(kind), LivestockState.CAPACITY)
            else getString(R.string.farm_locked_price, money(kind.landPrice))
        habitatPrevious.isEnabled = kind.ordinal > 0
        habitatNext.isEnabled = kind.ordinal < LivestockKind.entries.lastIndex
        habitatManage.text = getString(if (state.livestock.available(kind)) R.string.farm_herd_open else R.string.farm_habitat_unlock)
        val ready = state.livestock.readyProducts(kind)
        habitatCollect.text = getString(R.string.farm_product_collect, ready)
        habitatCollect.isEnabled = ready > 0
        habitatGreet.isEnabled = state.livestock.count(kind) > 0
    }
    private fun refresh() {
        state.advanceLivestock()
        state.advanceFields()
        state.advanceEncounters()
        fieldPanel.visibility = if (world.region == FarmRegion.FIELDS) View.VISIBLE else View.GONE
        livestockPanel.visibility = if (world.region == FarmRegion.LIVESTOCK) View.VISIBLE else View.GONE
        world.visibility = if (world.region == FarmRegion.LIVESTOCK) View.INVISIBLE else View.VISIBLE
        refreshHabitat()
        if (world.region != FarmRegion.FIELDS) fieldView.stop()
        val f = state.largeFields.fields[state.largeFields.selected]
        val phaseLabel = listOf(R.string.farm_field_plough, R.string.farm_field_seed, R.string.farm_field_grow, R.string.farm_field_harvest)[f.phase]
        fieldInfo.text = getString(R.string.farm_field_status, f.index + 1, getString(phaseLabel), f.coverage) + when {
            f.phase == 2 -> " · " + duration(((f.readyAt - System.currentTimeMillis()).coerceAtLeast(0) / 1000).toInt())
            !f.paid -> " · " + getString(R.string.farm_field_start, f.seedCost)
            else -> ""
        }
        fuelTrack.visibility = if (f.phase == 2) View.GONE else View.VISIBLE
        fuelFill.scaleX = f.fuel.coerceIn(0f, 1f)
        fuelDrawable.setColor(when { f.fuel > .5f -> Color.rgb(126, 187, 90); f.fuel > .2f -> Color.rgb(224, 167, 63); else -> Color.rgb(196, 64, 58) })
        fieldView.invalidate()
        seedGroup.visibility = if (world.region == FarmRegion.HOME) View.VISIBLE else View.GONE
        produceIcon.visibility = seedGroup.visibility
        regionIcon.alert = state.fieldsNeedAttention() || state.greenhouse.readyCount(System.currentTimeMillis()) > 0
        visitDock.visibility = if (world.region == FarmRegion.HOME) View.VISIBLE else View.GONE
        val readyOrders = state.readyOrders()
        val readyGifts = FarmGift.entries.count { !state.projects.owns(it) &&
            state.village.friendship(it.villager) >= FarmGift.FRIENDSHIP_NEEDED }
        villageSummary.text = when {
            readyOrders > 0 -> getString(R.string.farm_village_ready, readyOrders)
            readyGifts > 0 -> getString(R.string.farm_gift_waiting_count, readyGifts)
            else -> getString(R.string.farm_village_waiting)
        }
        villageEntry.contentDescription = getString(R.string.farm_village_person,
            getString(R.string.farm_village_title), villageSummary.text)
        val visit = state.visitSummary()
        visitSummaryText.text = when {
            visit.ready > 0 -> getString(R.string.farm_visit_ready, visit.ready)
            state.encounters.pending != null -> getString(R.string.farm_encounter_waiting)
            state.livestock.readyProducts() > 0 -> getString(R.string.farm_product_collect, state.livestock.readyProducts())
            state.workshop.readyCount(System.currentTimeMillis()) > 0 -> getString(R.string.farm_workshop_collect, state.workshop.readyCount(System.currentTimeMillis()))
            state.greenhouse.readyCount(System.currentTimeMillis()) > 0 -> getString(R.string.farm_greenhouse_collect, state.greenhouse.readyCount(System.currentTimeMillis()))
            visit.thirsty > 0 -> getString(R.string.farm_visit_thirsty, visit.thirsty)
            visit.growing > 0 -> getString(R.string.farm_visit_growing)
            else -> getString(R.string.farm_visit_prepare)
        }
        // Hidden until the pens exist: an icon for a system you have never seen is just a puzzle.
        manureIcon.visibility = if (seedGroup.visibility == View.VISIBLE && state.livestockUnlocked())
            View.VISIBLE else View.GONE
        manureIcon.stock = manureServings()
        manureIcon.contentDescription = getString(R.string.farm_manure_plantings, manureServings(),
            getString(state.selected.label), state.selected.manureCost)
        livestockUi.forEach { it() }
        villageUi.forEach { it() }
        visitUi.forEach { it() }
        workshopUi.forEach { it() }
        greenhouseUi.forEach { it() }
        val previousCoins = shownCoins
        shownCoins = state.coins
        if (previousCoins != null && state.coins > previousCoins) showAction(FarmActionEffectsView.Kind.COINS,
            caption = getString(R.string.farm_action_coins, money(state.coins - previousCoins)))
        balance.text = money(state.coins)
        balance.contentDescription = getString(R.string.farm_balance, money(state.coins), state.harvests)
        val inStock = state.seeds[state.selected.ordinal]
        // Nothing left of this seed means nothing to plant, so the little crop beside the bag goes
        // away entirely rather than sitting there reading "0 in stock". The bag stays: it is what
        // opens the picker, and it is where you go to get more.
        selection.visibility = if (inStock > 0) View.VISIBLE else View.GONE
        selection.crop = state.selected; selection.stock = inStock
        selection.contentDescription = getString(R.string.farm_seed_stock, getString(state.selected.label), inStock)
        produceIcon.crop = FarmCrop.entries.firstOrNull { state.cropProduceTotal(it) > 0 }
            ?: state.selected.takeIf { state.livestock.productCount() == 0 && state.workshop.totalStock() == 0 }
        produceIcon.stock = state.produceCount().takeIf { it > 0 }
        produceIcon.contentDescription = getString(R.string.farm_produce_stock, state.produceCount())
        purchases.forEach { (button, price) -> button.isEnabled = state.coins >= price; button.alpha = if (button.isEnabled) 1f else .45f }
        stockLabels.forEach { (label, crop) -> label.text = getString(R.string.farm_stock_count, state.seeds[crop.ordinal]) }
        world.invalidate()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }
    override fun onResume() { super.onResume(); enableImmersiveMode(); handler.post(tick) }
    override fun onPause() {
        handler.removeCallbacks(tick); handler.removeCallbacks(hideStatus)
        fieldView.stop(); closeBubble()
        if (!farmSuperseded) state.save()
        super.onPause()
    }
    /** The session is over: onPause has already saved, so what is on disk is what goes up. */
    override fun onStop() {
        if (!farmSuperseded) FarmSyncManager.onFarmClosed(this)
        super.onStop()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    /** Six-digit prices are unreadable run together; the grouping follows the app language. */
    private fun money(value: Long): String = java.text.NumberFormat.getIntegerInstance().format(value)
    private fun money(value: Int): String = money(value.toLong())
    private fun perHour(crop: FarmCrop): String = String.format("%.1f", crop.coinsPerHour)

    companion object {
        /** Survives the [recreate] that installing a cloud farm forces, so the notice is not lost. */
        private var pendingRestoreNotice = false
    }
}
