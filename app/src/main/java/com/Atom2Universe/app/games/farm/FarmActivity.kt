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
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.applySystemBarsVisibility

class FarmActivity : ThemedActivity() {
    private lateinit var state: FarmState
    private lateinit var sprites: FarmSprites
    private lateinit var root: FrameLayout
    private lateinit var fieldPanel: LinearLayout
    private lateinit var fieldView: FieldArcadeView
    private lateinit var fieldInfo: TextView
    private lateinit var fuelTrack: FrameLayout
    private lateinit var fuelFill: View
    private val fuelDrawable = GradientDrawable().apply { setColor(Color.rgb(126, 187, 90)); cornerRadius = 999f }
    private lateinit var world: FarmWorldView
    private lateinit var toolbar: LinearLayout
    private lateinit var balance: TextView
    private lateinit var selection: FarmArtView
    private lateinit var produceIcon: FarmArtView
    private lateinit var wateringIcon: FarmArtView
    private lateinit var manureIcon: FarmArtView
    private lateinit var regionIcon: FarmArtView
    private lateinit var status: TextView
    private var bubble: LinearLayout? = null
    private val livestockUi = mutableListOf<() -> Unit>()
    private lateinit var seedGroup: LinearLayout
    private var bubbleFeedback: TextView? = null
    private val purchases = mutableListOf<Pair<Button, Long>>()
    private val stockLabels = mutableListOf<Pair<TextView, FarmCrop>>()
    private val produceSellSelection = mutableMapOf<Pair<FarmCrop, FarmCropQuality>, Int>()
    private var stepRepeat: Runnable? = null
    private var stepRepeatDelay = 260L
    private var stepRepeatStarted = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideStatus = Runnable { status.visibility = View.GONE }
    private val ink = Color.rgb(76, 73, 48)
    private val cream = Color.rgb(255, 248, 225)
    private val sage = Color.rgb(220, 232, 195)
    private val border = Color.rgb(183, 164, 119)
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
        world.onBushBonus = { gained -> message(getString(R.string.farm_bush_bonus, money(gained))); refresh() }
        root.addView(world, FrameLayout.LayoutParams(-1, -1))
        fieldPanel = column().apply { visibility = View.GONE; setBackgroundColor(sage) }
        fieldInfo = text("", 14, true).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(4)) }
        fieldPanel.addView(fieldInfo)
        fuelTrack = FrameLayout(this).apply { background = rounded(Color.rgb(210, 199, 165), 8, border) }
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
            background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(Color.rgb(255, 238, 196), 14, Color.rgb(224, 167, 63)), null)
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
        wateringIcon = icon(FarmArtView.Kind.WATER, R.string.farm_watering_mode) { toggleWatering() }
        regionIcon = icon(FarmArtView.Kind.MAP, R.string.farm_regions) { chooseRegion() }
        toolbar.addView(regionIcon, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(toolbar, FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP).apply {
            setMargins(dp(10), dp(8), dp(10), 0)
        })
        root.addView(wateringIcon, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.LEFT).apply {
            setMargins(dp(16), dp(76), 0, 0)
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
        setContentView(root)
        // Hide system bars only for this game, retaining safe space for camera cutouts.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom)
            insets
        }
        applySystemBarsVisibility(false, false)
        ViewCompat.requestApplyInsets(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (bubble != null) closeBubble() else finish() }
        })
        refresh()
        val regionName = savedInstanceState?.getString("farm_region")
        FarmRegion.entries.firstOrNull { it.name == regionName && state.regionUnlocked(it) }?.let { region ->
            world.post { world.switchRegion(region); refresh() }
        }
    }

    /** The milestone still to reach, or null when the region is open. */
    private fun regionLock(region: FarmRegion): String? = when {
        state.regionUnlocked(region) -> null
        region == FarmRegion.FIELDS ->
            getString(R.string.farm_region_locked_fields, FarmState.FIELDS_HARVESTS, state.harvests)
        else -> getString(R.string.farm_region_locked_livestock, FarmState.LIVESTOCK_HARVESTS,
            FarmState.LIVESTOCK_FIELD_CYCLES, state.harvests, state.largeFields.cycles)
    }
    private fun goToRegion(region: FarmRegion) {
        val locked = regionLock(region)
        if (locked != null) { message(locked); return }
        closeBubble(); world.switchRegion(region); fieldView.resetMotion(); refresh()
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
        super.onSaveInstanceState(outState)
    }

    private fun rounded(color: Int, radius: Int = 14, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(2), it) }
    }
    private fun text(value: String, size: Int = 14, bold: Boolean = false) = TextView(this).apply {
        this.text = value; textSize = size.toFloat(); setTextColor(ink)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun icon(kind: FarmArtView.Kind, label: Int, action: () -> Unit) = FarmArtView(this, kind, sprites).apply {
        contentDescription = getString(label); tooltipText = getString(label)
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(sage, 16), null)
        setOnClickListener { action() }
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 13f; isAllCaps = false; setTextColor(ink)
        minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(sage, 12), null)
        setOnClickListener { action() }
    }
    private fun preview(crop: FarmCrop) = FarmArtView(this, FarmArtView.Kind.CROP, sprites).apply {
        this.crop = crop; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = rounded(Color.rgb(235, 239, 205), 16)
    }
    private fun duration(seconds: Int): String {
        val minutes = (seconds.coerceAtLeast(0) + 59) / 60
        return getString(R.string.farm_duration, minutes / 60, minutes % 60)
    }

    /** An in-scene card, never a full-screen dialog or dimmed modal window. */
    private fun showBubble(title: String, parcel: Int? = null, content: (LinearLayout) -> Unit) {
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
        val maxHeight = minOf((root.height * .64f).toInt(), root.height - top - dp(16)).coerceAtLeast(1)
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
                    val sell = button(getString(R.string.farm_sell_adult, money(kind.sale))) {
                        matching().firstOrNull()?.let { state.sellAnimal(it.id) }
                        refresh()
                    }
                    column.addView(sell)
                    livestockUi.add { sell.isEnabled = matching().isNotEmpty() }
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
        if (world.region == FarmRegion.FIELDS) { fieldShop(); return }
        if (world.region == FarmRegion.LIVESTOCK) { livestockShop(); return }
        showBubble(getString(R.string.farm_shop_title)) { body ->
            val tabs = LinearLayout(this)
            // The orchard used to have a tab of its own, holding nothing but a notice that fruit
            // trees were being reworked. It is gone until they are back. A parcel can still be set
            // to orchard use from its own sign - this was only ever the shop tab.
            listOf(R.string.farm_use_crops, R.string.farm_bonuses).forEachIndexed { i, label ->
                tabs.addView(button(getString(label)) { shop(i == 1) }.apply {
                    alpha = if (bonuses == (i == 1)) 1f else .65f
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), dp(6), dp(2), dp(10)) })
            }
            body.addView(tabs)
            if (bonuses) {
                body.addView(text(getString(R.string.farm_watering_bonus_title), 17, true))
                body.addView(text(getString(R.string.farm_watering_bonus_body)).apply { setPadding(0, dp(6), 0, dp(12)) })
                body.addView(text(getString(when (state.wateringLevel) {
                    0 -> R.string.farm_watering_level_cell
                    1 -> R.string.farm_watering_level_row
                    else -> R.string.farm_watering_level_parcel
                }), 13).apply { setPadding(0, 0, 0, dp(10)) })
                if (state.wateringLevel < 2) {
                    val nextLevel = state.wateringLevel + 1
                    val cost = state.wateringUpgradeCost(nextLevel)
                    val label = if (nextLevel == 1) R.string.farm_watering_upgrade_row else R.string.farm_watering_upgrade_parcel
                    val buy = button(getString(label, money(cost))) {
                        message(getString(if (state.upgradeWatering()) R.string.farm_watering_upgraded else R.string.farm_no_coins))
                        refresh()
                    }
                    purchases.add(buy to cost)
                    body.addView(buy, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
                }

                body.addView(text(getString(R.string.farm_parcel_bonus_title), 17, true).apply { setPadding(0, dp(6), 0, 0) })
                body.addView(text(getString(R.string.farm_parcel_bonus_future)).apply { setPadding(0, dp(6), 0, dp(12)) })
                body.addView(text(getString(when (state.harvestLevel) {
                    0 -> R.string.farm_harvest_level_cell
                    1 -> R.string.farm_harvest_level_row
                    else -> R.string.farm_harvest_level_parcel
                }), 13).apply { setPadding(0, 0, 0, dp(10)) })
                if (state.harvestLevel < 2) {
                    val nextLevel = state.harvestLevel + 1
                    val cost = state.harvestUpgradeCost(nextLevel)
                    val label = if (nextLevel == 1) R.string.farm_harvest_upgrade_row else R.string.farm_harvest_upgrade_parcel
                    val buy = button(getString(label, money(cost))) {
                        message(getString(if (state.upgradeHarvest()) R.string.farm_harvest_upgraded else R.string.farm_no_coins))
                        refresh()
                    }
                    purchases.add(buy to cost)
                    body.addView(buy, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
                }

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
            } else FarmCrop.ladder.forEach { crop ->
                body.addView(seedRow(crop), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }
    /** One shop line. Locked rungs stay visible: seeing the next seed is what a parcel really sells. */
    private fun seedRow(crop: FarmCrop): LinearLayout {
        val open = state.cropUnlocked(crop)
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = rounded(Color.rgb(247, 238, 211), 16)
            setPadding(dp(6), dp(8), dp(8), dp(8)); alpha = if (open) 1f else .55f
        }
        row.addView(preview(crop), LinearLayout.LayoutParams(dp(66), dp(80)))
        val details = column().apply { setPadding(dp(10), 0, 0, 0) }
        details.addView(text(getString(crop.label), 16, true))
        details.addView(text(getString(R.string.farm_shop_details, duration(crop.seconds), money(crop.sale), perHour(crop)), 12))
        if (!open) {
            details.addView(text(getString(R.string.farm_crop_locked, crop.rank), 13, true))
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            return row
        }
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
                message(getString(if (state.buy(crop, quantity)) R.string.farm_bought else R.string.farm_no_coins))
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
        if (world.region == FarmRegion.LIVESTOCK) { livestockShop(); return }
        showBubble(getString(R.string.farm_inventory)) { body ->
            val available = FarmCrop.entries.filter { state.seeds[it.ordinal] > 0 }
            if (available.isEmpty()) body.addView(text(getString(R.string.farm_inventory_empty)).apply { setPadding(dp(8), dp(12), dp(8), dp(12)) })
            available.forEach { crop ->
                val current = crop == state.selected
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = if (current) rounded(Color.rgb(255, 238, 196), 14, Color.rgb(224, 167, 63)) else rounded(sage, 14)
                    isFocusable = true
                    setOnClickListener { state.selected = crop; state.save(); closeBubble(); refresh() }
                }
                row.addView(preview(crop), LinearLayout.LayoutParams(dp(60), dp(60)))
                val details = column().apply { setPadding(dp(10), 0, 0, 0) }
                details.addView(text(getString(crop.label), 16, current))
                details.addView(text(getString(R.string.farm_stock_count, state.seeds[crop.ordinal]), 12))
                if (current) details.addView(text(getString(R.string.farm_currently_selected), 12, true).apply {
                    setTextColor(Color.rgb(150, 108, 30))
                })
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
            body.addView(button(getString(R.string.farm_shop)) { shop() })
        }
    }
    private fun produceInventory() {
        showBubble(getString(R.string.farm_produce_inventory)) { body ->
            val crops = FarmCrop.entries.filter { state.cropProduceTotal(it) > 0 }
            if (crops.isEmpty()) {
                body.addView(text(getString(R.string.farm_produce_empty)).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                })
                return@showBubble
            }
            val sellAll = button(getString(R.string.farm_sell_all_produce)) { confirmSellAllProduce() }
            body.addView(sellAll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            crops.forEach { crop ->
                val group = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(Color.rgb(247, 238, 211), 16, border)
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
                    setTextColor(Color.rgb(116, 78, 48))
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
    private fun qualityTint(quality: FarmCropQuality) = when (quality) {
        FarmCropQuality.COMMON -> Color.rgb(232, 239, 215)
        FarmCropQuality.RARE -> Color.rgb(218, 238, 250)
        FarmCropQuality.EPIC -> Color.rgb(237, 222, 248)
        FarmCropQuality.LEGENDARY -> Color.rgb(255, 238, 196)
    }
    private fun qualityTextColor(quality: FarmCropQuality) = when (quality) {
        FarmCropQuality.COMMON -> Color.rgb(67, 91, 47)
        FarmCropQuality.RARE -> Color.rgb(32, 93, 142)
        FarmCropQuality.EPIC -> Color.rgb(91, 49, 132)
        FarmCropQuality.LEGENDARY -> Color.rgb(139, 83, 20)
    }
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
                        if (state.unlock(index)) { closeBubble(); message(getString(R.string.farm_unlocked)) }
                        else message(getString(R.string.farm_no_coins))
                        refresh()
                    }
                    purchases.add(buy to price)
                    body.addView(buy, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                }
            } else {
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
                state.plant(index, now) -> getString(
                    if (p.rich) R.string.farm_planted_rich else R.string.farm_planted_water,
                    getString(state.selected.label))
                else -> getString(R.string.farm_no_seeds)
            }
            p.progress(now) >= 1f -> {
                val result = state.harvestMany(state.harvestTargets(index), now)
                if (result.count > 1) getString(R.string.farm_harvested_many, result.count)
                else getString(R.string.farm_harvested_one, harvestStackLabel(result.stacks.firstOrNull()))
            }
            state.water(index, now) -> getString(R.string.farm_water_started, duration(p.remaining(now)))
            else -> getString(R.string.farm_wait_long, duration(p.remaining(now)))
        }
        message(result); refresh()
    }
    private fun debrisCleared(index: Int) {
        message(getString(R.string.farm_cleaned)); refresh()
    }
    private fun toggleWatering() {
        if (!state.hasPlantsNeedingWater()) return
        world.wateringMode = !world.wateringMode
        updateWateringIcon()
        message(getString(if (world.wateringMode) R.string.farm_watering_on else R.string.farm_watering_off))
    }
    private fun watered(count: Int) {
        message(getString(if (count < 0) R.string.farm_watering_nothing else R.string.farm_watering_done, count.coerceAtLeast(0)))
        if (!state.hasPlantsNeedingWater()) world.wateringMode = false
        refresh()
    }
    private fun updateWateringIcon() {
        wateringIcon.background = RippleDrawable(ColorStateList.valueOf(0x337D9966),
            rounded(if (world.wateringMode) Color.rgb(190, 225, 235) else cream, 18, border), null)
        wateringIcon.elevation = dp(if (world.wateringMode) 9 else 6).toFloat()
    }
    private fun harvested(index: Int, result: FarmHarvestResult) {
        message(if (result.count > 1) getString(R.string.farm_harvested_many, result.count)
            else getString(R.string.farm_harvested_one, harvestStackLabel(result.stacks.firstOrNull())))
        refresh()
    }
    private fun harvestStackLabel(stack: FarmHarvestStack?): String = stack?.let {
        getString(R.string.farm_harvest_stack_label, getString(it.crop.label), getString(it.quality.label))
    } ?: getString(R.string.farm_produce_inventory)
    private fun removePlant(index: Int) {
        if (state.plots[index].crop == null) return
        showBubble(getString(R.string.farm_clear), FarmLayout.parcelOf(index)) { body ->
            body.addView(text(getString(R.string.farm_clear_confirm)).apply { setPadding(0, dp(10), 0, dp(12)) })
            body.addView(button(getString(R.string.farm_clear)) { state.clear(index); closeBubble(); refresh() })
        }
    }
    private fun refresh() {
        state.advanceLivestock()
        state.advanceFields()
        fieldPanel.visibility = if (world.region == FarmRegion.FIELDS) View.VISIBLE else View.GONE
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
        regionIcon.alert = state.fieldsNeedAttention()
        val wateringAvailable = world.region == FarmRegion.HOME && state.hasPlantsNeedingWater()
        if (!wateringAvailable) world.wateringMode = false
        wateringIcon.visibility = if (wateringAvailable) View.VISIBLE else View.GONE
        updateWateringIcon()
        // Hidden until the pens exist: an icon for a system you have never seen is just a puzzle.
        manureIcon.visibility = if (seedGroup.visibility == View.VISIBLE && state.livestockUnlocked())
            View.VISIBLE else View.GONE
        manureIcon.stock = manureServings()
        manureIcon.contentDescription = getString(R.string.farm_manure_plantings, manureServings(),
            getString(state.selected.label), state.selected.manureCost)
        livestockUi.forEach { it() }
        balance.text = money(state.coins)
        balance.contentDescription = getString(R.string.farm_balance, money(state.coins), state.harvests)
        val inStock = state.seeds[state.selected.ordinal]
        // Nothing left of this seed means nothing to plant, so the little crop beside the bag goes
        // away entirely rather than sitting there reading "0 in stock". The bag stays: it is what
        // opens the picker, and it is where you go to get more.
        selection.visibility = if (inStock > 0) View.VISIBLE else View.GONE
        selection.crop = state.selected; selection.stock = inStock
        selection.contentDescription = getString(R.string.farm_seed_stock, getString(state.selected.label), inStock)
        produceIcon.crop = FarmCrop.entries.firstOrNull { state.cropProduceTotal(it) > 0 } ?: state.selected
        produceIcon.stock = state.produceCount().takeIf { it > 0 }
        produceIcon.contentDescription = getString(R.string.farm_produce_stock, state.produceCount())
        purchases.forEach { (button, price) -> button.isEnabled = state.coins >= price; button.alpha = if (button.isEnabled) 1f else .45f }
        stockLabels.forEach { (label, crop) -> label.text = getString(R.string.farm_stock_count, state.seeds[crop.ordinal]) }
        world.invalidate()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBarsVisibility(false, false)
    }
    override fun onResume() { super.onResume(); applySystemBarsVisibility(false, false); handler.post(tick) }
    override fun onPause() {
        handler.removeCallbacks(tick); handler.removeCallbacks(hideStatus)
        fieldView.stop(); closeBubble(); state.save(); super.onPause()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    /** Six-digit prices are unreadable run together; the grouping follows the app language. */
    private fun money(value: Long): String = java.text.NumberFormat.getIntegerInstance().format(value)
    private fun money(value: Int): String = money(value.toLong())
    private fun perHour(crop: FarmCrop): String = String.format("%.1f", crop.coinsPerHour)
}
