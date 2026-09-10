package com.Atom2Universe.app.games.farm

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
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
    private lateinit var world: FarmWorldView
    private lateinit var toolbar: LinearLayout
    private lateinit var balance: TextView
    private lateinit var selection: FarmArtView
    private lateinit var wateringIcon: FarmArtView
    private lateinit var status: TextView
    private var bubble: LinearLayout? = null
    private var bubbleFeedback: TextView? = null
    private val purchases = mutableListOf<Pair<Button, Int>>()
    private val stockLabels = mutableListOf<Pair<TextView, FarmCrop>>()
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
        state = FarmState(getSharedPreferences("farm_v1", MODE_PRIVATE))
        sprites = FarmSprites(this)
        root = FrameLayout(this)
        world = FarmWorldView(this, state, ::interact, ::parcelMenu, ::removePlant, ::debrisCleared, ::watered, ::harvested)
        world.dismissBubble = { if (bubble != null) { closeBubble(); true } else false }
        world.onRegionTap = { message(getString(world.region.description)) }
        root.addView(world, FrameLayout.LayoutParams(-1, -1))
        toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(cream, 22, border)
            elevation = dp(6).toFloat()
        }
        toolbar.addView(icon(FarmArtView.Kind.BACK, R.string.farm_back) { if (bubble != null) closeBubble() else finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val purse = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        purse.addView(FarmArtView(this, FarmArtView.Kind.COIN), LinearLayout.LayoutParams(dp(28), dp(36)))
        balance = text("", 16, true).apply { setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
        purse.addView(balance, LinearLayout.LayoutParams(0, -2, 1f))
        // Hidden dev entry point: long-press the balance, never a visible button reachable in normal play.
        purse.setOnLongClickListener { cheatMenu(); true }
        toolbar.addView(purse, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(icon(FarmArtView.Kind.SHOP, R.string.farm_shop) { shop() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        // A small sub-group: the seed bag (opens the picker) beside a plain crop icon for whatever is
        // currently selected - gold border there, unlike the action icons, since it shows a state.
        val seedGroup = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = rounded(sage, 16)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        seedGroup.addView(icon(FarmArtView.Kind.SEEDS, R.string.farm_inventory) { inventory() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        selection = icon(FarmArtView.Kind.CROP, R.string.farm_inventory) { inventory() }.apply {
            background = RippleDrawable(ColorStateList.valueOf(0x337D9966), rounded(Color.rgb(255, 238, 196), 14, Color.rgb(224, 167, 63)), null)
        }
        seedGroup.addView(selection, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(2) })
        toolbar.addView(seedGroup, LinearLayout.LayoutParams(dp(92), dp(48)).apply { leftMargin = dp(2) })
        wateringIcon = icon(FarmArtView.Kind.WATER, R.string.farm_watering_mode) { toggleWatering() }
        toolbar.addView(wateringIcon, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(icon(FarmArtView.Kind.MAP, R.string.farm_regions) { chooseRegion() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(toolbar, FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP).apply {
            setMargins(dp(10), dp(8), dp(10), 0)
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
        FarmRegion.entries.firstOrNull { it.name == regionName }?.let { region ->
            world.post { world.switchRegion(region) }
        }
    }

    private fun chooseRegion() {
        showBubble(getString(R.string.farm_regions)) { body ->
            FarmRegion.entries.forEach { region ->
                val row = column().apply {
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded(if (region == world.region) sage else cream, 14, border)
                    isFocusable = true
                    setOnClickListener {
                        closeBubble(); world.switchRegion(region)
                        message(getString(region.description))
                    }
                }
                row.addView(text(getString(region.label), 17, true))
                row.addView(text(getString(region.description), 13))
                body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }

    private fun cheatMenu() {
        showBubble(getString(R.string.farm_dev_title)) { body ->
            body.addView(text(getString(R.string.farm_dev_coins), 15, true).apply { setPadding(0, 0, 0, dp(6)) })
            val coinsRow = LinearLayout(this)
            for (amount in listOf(100, 1000)) coinsRow.addView(button(getString(R.string.farm_dev_add_coins, amount)) {
                state.cheatAddCoins(amount.toLong()); message(getString(R.string.farm_dev_done)); refresh()
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
            body.addView(button(getString(R.string.farm_dev_seed_for_testing)) {
                state.cheatSeedForTesting(); closeBubble(); world.focusParcel(0); message(getString(R.string.farm_dev_seeded))
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(14) })

            body.addView(text(getString(R.string.farm_dev_reset), 15, true).apply { setPadding(0, 0, 0, dp(6)) })
            body.addView(button(getString(R.string.farm_dev_reset_confirm)) {
                state.cheatReset(); closeBubble(); world.focusParcel(0); message(getString(R.string.farm_dev_reset_done))
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
        bubble?.let { root.removeView(it) }
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
    private fun shop(trees: Boolean = false, bonuses: Boolean = false) {
        showBubble(getString(R.string.farm_shop_title)) { body ->
            val tabs = LinearLayout(this)
            listOf(R.string.farm_use_crops, R.string.farm_use_orchard, R.string.farm_bonuses).forEachIndexed { i, label ->
                tabs.addView(button(getString(label)) { shop(i == 1, i == 2) }.apply {
                    alpha = if ((bonuses && i == 2) || (!bonuses && i == if (trees) 1 else 0)) 1f else .65f
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
                    val buy = button(getString(label, cost)) {
                        message(getString(if (state.upgradeWatering()) R.string.farm_watering_upgraded else R.string.farm_no_coins))
                        refresh()
                    }
                    purchases.add(buy to cost.toInt())
                    body.addView(buy, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
                }
                body.addView(text(getString(R.string.farm_parcel_bonus_title), 17, true).apply { setPadding(0, dp(6), 0, 0) })
                body.addView(text(getString(R.string.farm_parcel_bonus_future)).apply { setPadding(0, dp(6), 0, dp(16)) })
            } else FarmCrop.entries.filter { it.tree == trees }.forEach { crop ->
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL; background = rounded(Color.rgb(247, 238, 211), 16)
                    setPadding(dp(6), dp(8), dp(8), dp(8))
                }
                row.addView(preview(crop), LinearLayout.LayoutParams(dp(66), dp(80)))
                val details = column().apply { setPadding(dp(10), 0, 0, 0) }
                details.addView(text(getString(crop.label), 16, true))
                details.addView(text(getString(R.string.farm_shop_details, duration(crop.seconds), crop.sale), 12))
                if (crop.tree) details.addView(text(getString(R.string.farm_shop_regrowth), 12))
                val count = text("", 12)
                stockLabels.add(count to crop); details.addView(count)
                val buy = LinearLayout(this)
                for (quantity in listOf(1, 6)) {
                    val price = quantity * crop.cost
                    val action = button(getString(R.string.farm_buy_short, quantity, price)) {
                        message(getString(if (state.buy(crop, quantity)) R.string.farm_bought else R.string.farm_no_coins))
                        refresh()
                    }
                    purchases.add(action to price)
                    buy.addView(action, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, dp(6), dp(4), 0) })
                }
                details.addView(buy)
                row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
    }
    private fun inventory() {
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
    private fun parcelMenu(index: Int) {
        val land = state.parcels[index]
        showBubble(getString(R.string.farm_parcel_label, index + 1, getString(land.use.label)), index) { body ->
            body.addView(text(getString(R.string.farm_parcel_size, FarmLayout.lands[index].columns,
                FarmLayout.lands[index].rows, FarmLayout.lands[index].capacity)).apply { setPadding(0, dp(8), 0, dp(12)) })
            if (!land.unlocked) {
                body.addView(text(getString(R.string.farm_unlock_new_body)))
                val price = state.unlockCost(index)
                val buy = button(getString(R.string.farm_locked_price, price)) {
                    if (state.unlock(index)) { closeBubble(); message(getString(R.string.farm_unlocked)) }
                    else message(getString(R.string.farm_no_coins))
                    refresh()
                }
                purchases.add(buy to price)
                body.addView(buy, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            } else {
                for (use in FarmLandUse.entries) body.addView(button(getString(use.label)) {
                    if (state.changeUse(index, use)) { closeBubble(); message(getString(R.string.farm_use_changed)) }
                    else message(getString(R.string.farm_empty_required))
                    refresh()
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                body.addView(button(getString(R.string.farm_manage_plants)) { plantList(index) })
                body.addView(text(getString(R.string.farm_livestock_future), 12).apply { setPadding(0, dp(12), 0, 0) })
            }
        }
    }
    private fun plantList(parcel: Int) {
        showBubble(getString(R.string.farm_manage_plants), parcel) { body ->
            val now = System.currentTimeMillis()
            FarmLayout.cells(parcel).forEach { i ->
                val p = state.plots[i]
                val description = when {
                    p.debris != 0 -> getString(R.string.farm_debris)
                    p.crop == null -> getString(R.string.farm_empty)
                    else -> getString(p.crop!!.label) + " · " + when {
                        !p.watered -> getString(R.string.farm_needs_water)
                        p.progress(now) >= 1f -> getString(R.string.farm_ready)
                        else -> duration(p.remaining(now))
                    }
                }
                body.addView(button(getString(R.string.farm_cell_info, FarmLayout.localCell(i) + 1, description)) {
                    closeBubble(); interact(i)
                }.apply { setOnLongClickListener { if (p.crop == null) false else { removePlant(i); true } } },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
        }
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
                state.plant(index, now) -> getString(R.string.farm_planted_water, getString(state.selected.label))
                else -> getString(R.string.farm_no_seeds)
            }
            p.progress(now) >= 1f -> getString(R.string.farm_earned, state.harvest(index, now))
            state.water(index, now) -> getString(R.string.farm_water_started, duration(p.remaining(now)))
            else -> getString(R.string.farm_wait_long, duration(p.remaining(now)))
        }
        message(result); refresh()
    }
    private fun debrisCleared(index: Int) {
        message(getString(R.string.farm_cleaned)); refresh()
    }
    private fun toggleWatering() {
        world.wateringMode = !world.wateringMode
        wateringIcon.background = RippleDrawable(ColorStateList.valueOf(0x337D9966),
            rounded(if (world.wateringMode) Color.rgb(190, 225, 235) else sage, 16, if (world.wateringMode) border else null), null)
        message(getString(if (world.wateringMode) R.string.farm_watering_on else R.string.farm_watering_off))
    }
    private fun watered(count: Int) {
        message(getString(if (count < 0) R.string.farm_watering_nothing else R.string.farm_watering_done, count.coerceAtLeast(0)))
        refresh()
    }
    private fun harvested(index: Int, amount: Int) {
        message(getString(R.string.farm_earned, amount)); refresh()
    }
    private fun removePlant(index: Int) {
        if (state.plots[index].crop == null) return
        showBubble(getString(R.string.farm_clear), FarmLayout.parcelOf(index)) { body ->
            body.addView(text(getString(R.string.farm_clear_confirm)).apply { setPadding(0, dp(10), 0, dp(12)) })
            body.addView(button(getString(R.string.farm_clear)) { state.clear(index); closeBubble(); refresh() })
        }
    }
    private fun refresh() {
        balance.text = state.coins.toString()
        balance.contentDescription = getString(R.string.farm_balance, state.coins, state.harvests)
        selection.crop = state.selected; selection.stock = state.seeds[state.selected.ordinal]
        selection.contentDescription = getString(R.string.farm_seed_stock, getString(state.selected.label), state.seeds[state.selected.ordinal])
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
        closeBubble(); state.save(); super.onPause()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
