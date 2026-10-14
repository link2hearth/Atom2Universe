package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight

/** Écran de campagne à plein écran. Le village et sa sauvegarde restent ceux du joueur.
 * Gros boutons dorés pour ce qu'on fait sans cesse, boutons discrets pour le reste ;
 * les règles se lisent derrière un « ? », pas sur l'écran. */
internal class HamletAdventureDialog(private val context: Context, private val game: HamletGame,
    private val haptics: Boolean = false,
    private val onBuild: (Def) -> Unit = {}, private val onClose: () -> Unit = {},
    private val onExit: (() -> Boolean)? = null, private val effectsEnabled: Boolean = true) {
    private val a get() = game.adventure
    private lateinit var dialog: Dialog
    private lateinit var root: LinearLayout
    private lateinit var stock: LinearLayout
    private val resourceChips = ArrayList<TextView>()
    private val buildingArt = HamletArt()
    private lateinit var rows: LinearLayout
    private lateinit var scroll: ScrollView
    private var tab = 0
    private var heroFilter = -1
    private var ownedOnly = false
    private var showLegacyGear = false
    private var page = a.frontier / 12
    private var selected = a.frontier
    private val tabButtons = ArrayList<Button>()
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun raw(key: String, vararg args: Any): String {
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        val resolved = if (id == 0) R.string.hamlet_title else id
        return if (args.isEmpty()) context.getString(resolved) else context.getString(resolved, *args)
    }
    private fun s(key: String, vararg args: Any) = raw("hamlet_a_$key", *args)
    private fun heroName(hero: Int) = s("hero_$hero")
    private fun rarity(hero: Int) = HamletHeroes.defs[hero].rarity
    /** A hero's name in the colour of their rarity. */
    private fun named(hero: Int, value: String = heroName(hero), onDark: Boolean = false) =
        HamletUi.tint(value, heroName(hero) to HamletUi.rarity(rarity(hero), onDark))
    private fun factionName(f: HeroFaction) = s("faction_${f.ordinal}")
    private fun region(stage: Int) = raw("hamlet_region_${a.region(stage)}")
    private fun item(id: String) = raw("hamlet_n_$id")
    private fun bg(color: Int, radius: Int = 14) = HamletUi.panel(context, color, radius, 0xFFD4C5A9.toInt())
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun line() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun text(parent: LinearLayout, value: CharSequence, title: Boolean = false, dark: Boolean = true,
                     small: Boolean = false, color: Int = 0): TextView = TextView(context).also {
        it.text = value; it.textSize = if (title) HamletUi.TITLE else if (small) HamletUi.SMALL else HamletUi.TEXT
        it.setTextColor(if (color != 0) color else if (!dark) HamletUi.PAPER else if (small) HamletUi.MUTED else HamletUi.INK)
        it.setPadding(dp(3), dp(4), dp(3), dp(5)); it.setLineSpacing(dp(2).toFloat(), 1f)
        if (title) it.setTypeface(null, Typeface.BOLD)
        parent.addView(it)
    }
    private fun button(value: CharSequence, enabled: Boolean = true, weight: Weight = Weight.NORMAL, onDark: Boolean = false,
                       click: () -> Unit = {}) = HamletUi.button(context, value, weight, onDark) {
        if (haptics && ::root.isInitialized) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        click()
    }.apply {
        isEnabled = enabled
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(3) }
    }
    /** Buttons side by side, sharing the width by their weights. */
    private fun side(parent: LinearLayout, vararg items: Pair<Button, Float>) {
        val row = line()
        for ((b, w) in items) row.addView(b, LinearLayout.LayoutParams(0, -2, w).apply { marginStart = dp(2); marginEnd = dp(2); topMargin = dp(3); bottomMargin = dp(3) })
        parent.addView(row)
    }
    /** Toggles in rows of [perRow] on narrow screens, all on one row on wide ones. */
    private fun choices(parent: LinearLayout, items: List<Button>, perRow: Int = 2) {
        val wide = context.resources.configuration.screenWidthDp >= 600
        for (group in items.chunked(if (wide) items.size else perRow)) side(parent, *group.map { it to 1f }.toTypedArray())
    }
    /** A title on the dark background between cards, with its "?". */
    private fun section(title: String, help: String) {
        val head = line().apply { setPadding(0, dp(12), 0, 0) }
        head.addView(TextView(context).apply { text = title; textSize = HamletUi.TITLE; setTextColor(HamletUi.GOLD); setTypeface(null, Typeface.BOLD); setPadding(dp(3), 0, dp(3), 0) },
            LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(info(title, help, onDark = true), LinearLayout.LayoutParams(dp(44), -2))
        rows.addView(head)
    }
    /** An action that redraws the screen when it worked; an impossible one does nothing. */
    private fun action(parent: LinearLayout, label: CharSequence, enabled: Boolean = true, weight: Weight = Weight.NORMAL, operation: () -> Boolean) {
        parent.addView(button(label, enabled, weight) { if (operation()) render() })
    }
    private fun info(title: CharSequence, message: CharSequence, onDark: Boolean = false) = HamletUi.info(context, title, message, onDark)
    private fun toast(value: String) { Toast.makeText(context, value, Toast.LENGTH_SHORT).show() }
    /** A parchment card; [help] adds a "?" beside its title. */
    private fun card(title: CharSequence, help: CharSequence? = null, parent: LinearLayout = rows): LinearLayout = column().also {
        it.background = bg(HamletUi.PAPER); it.setPadding(dp(13), dp(8), dp(13), dp(12)); it.elevation = dp(2).toFloat()
        parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val head = line()
        head.addView(TextView(context).apply {
            text = title; textSize = HamletUi.TITLE; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD)
            setPadding(dp(3), dp(4), dp(3), dp(4))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (help != null) head.addView(info(title, help), LinearLayout.LayoutParams(dp(44), -2))
        it.addView(head)
    }
    fun show(initialTab: Int = 0) {
        tab = initialTab
        dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        root = column().apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(HamletUi.FOREST, 0xFF102D2A.toInt()))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val title = line()
        title.addView(TextView(context).apply { text = s("title"); textSize = HamletUi.HEAD; setTextColor(HamletUi.GOLD); setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(info(s("title"), s("help_text"), onDark = true), LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(4) })
        if (onExit != null) title.addView(button(context.getString(R.string.hamlet_ui_exit), weight = Weight.QUIET, onDark = true) { if (onExit.invoke()) dialog.dismiss() }.apply {
            contentDescription = context.getString(R.string.hamlet_ui_exit_description)
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        title.addView(button(s("village")) { dialog.dismiss() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
        root.addView(title)
        stock = LinearLayout(context).apply { setPadding(0, dp(5), 0, dp(5)) }
        val glyphs = arrayOf("apple", "wood", "ore", "gear", "book", "gem", "star", "ticket")
        for (i in glyphs.indices) {
            val chip = TextView(context).apply {
                textSize = 13f; gravity = Gravity.CENTER_VERTICAL; setTextColor(HamletUi.PAPER)
                setPadding(dp(8), dp(6), dp(10), dp(6)); background = HamletUi.panel(context, 0xFF305347.toInt(), 9)
                setCompoundDrawablesWithIntrinsicBounds(HamletUi.icon(context, glyphs[i], 18), null, null, null); compoundDrawablePadding = dp(5)
                isSingleLine = true
            }
            resourceChips.add(chip)
            stock.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(5) })
        }
        root.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false; addView(stock); contentDescription = context.getString(R.string.hamlet_ui_resources)
        }, LinearLayout.LayoutParams(-1, -2))
        rows = column(); scroll = ScrollView(context).apply { addView(rows); isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val tabs = LinearLayout(context).apply { setPadding(0, dp(6), 0, 0) }
        val names = arrayOf("explore", "heroes", "teams", "craft", "kingdom", "challenges")
        val icons = arrayOf("map", "hat", "shield", "gear", "crate", "sword")
        for (i in names.indices) tabs.addView(HamletUi.navigation(context, s(names[i]), icons[i]) {
            if (haptics) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            a.abandonBattle(); tab = i; scroll.scrollTo(0, 0); render()
        }.also { it.minHeight = dp(66); it.minimumHeight = dp(66); tabButtons.add(it) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        root.addView(tabs)
        dialog.setContentView(root, android.view.ViewGroup.LayoutParams(-1, -1))
        dialog.setOnDismissListener { a.abandonBattle(); buildingArt.clear(); onClose() }
        dialog.show(); dialog.window?.apply { setLayout(-1, -1); setBackgroundDrawableResource(android.R.color.transparent); addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            v.setPadding(dp(10) + bars.left, dp(10) + bars.top, dp(10) + bars.right, dp(8) + bars.bottom); insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root); render()
    }
    private fun render() {
        a.advanceProduction(); rows.removeAllViews()
        refreshStock()
        tabButtons.forEachIndexed { i, b ->
            b.background = HamletUi.control(context, selected = i == tab, quiet = i != tab)
            b.setTextColor(if (i == tab) HamletUi.INK else HamletUi.PAPER); b.isSelected = i == tab
        }
        when (tab) { 0 -> explore(); 1 -> heroes(); 2 -> teams(); 3 -> craft(); 4 -> kingdom(); 5 -> challenges() }
    }
    private fun refreshStock() {
        AdventureResource.entries.forEachIndexed { i, resource ->
            resourceChips[i].text = context.getString(R.string.hamlet_ui_number, a[resource])
            val description = context.getString(R.string.hamlet_ui_resource_amount, s("resource_${resource.name.lowercase()}"), a[resource])
            resourceChips[i].contentDescription = description; resourceChips[i].tooltipText = description
        }
    }
    /** Rarity colour on the card's edge, so a hero's rank reads before its name. */
    private fun rarityCard(owned: Boolean, rarity: HeroRarity): android.graphics.drawable.Drawable {
        val shape = GradientDrawable().apply {
            setColor(if (owned) 0xFFF0E4CB.toInt() else 0xFFD3D8CF.toInt()); cornerRadius = dp(14).toFloat()
            setStroke(dp(1 + (rarity.ordinal + 1) / 2), HamletHeroFx.main(rarity))
        }
        return RippleDrawable(ColorStateList.valueOf(0x22293D37), shape, null)
    }
    /** Framed hero portraits stand on their rarity backdrop, with room under the feet for the rune circle. */
    private fun portrait(kind: String, id: Int, heightDp: Int = 94, framed: Boolean = false): View = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private val destination = RectF()
        private val sprite = if (kind == "building") buildingArt.forDef(Hamlet.byId.getValue(HamletAdventure.FACILITIES[id])) else null
        private val rarity = if (framed && kind == "hero") HamletHeroes.defs[id].rarity else null
        private val backdrop = rarity?.let { HeroBackdrop(it, resources.displayMetrics.density) }
        override fun onDraw(c: Canvas) {
            val size = minOf(width.toFloat(), height.toFloat())
            if (sprite != null) {
                val scale = minOf(width.toFloat() / sprite.bmp.width, height.toFloat() / sprite.bmp.height) * .94f
                val w = sprite.bmp.width * scale; val h = sprite.bmp.height * scale
                destination.set((width - w) / 2, height - h, (width + w) / 2, height.toFloat())
                c.drawBitmap(sprite.bmp, null, destination, paint)
            } else {
                val time = if (effectsEnabled) android.os.SystemClock.uptimeMillis() / 1000f else 0f
                if (backdrop != null) {
                    backdrop.draw(c, width.toFloat(), height.toFloat(), time)
                    val inner = size * .86f
                    HamletCharacterArt.draw(c, true, id, width / 2f, height - inner * .07f, inner, time = time)
                } else HamletCharacterArt.draw(c, kind == "hero", id, width / 2f, height.toFloat(), size, time = time)
                val delay = if ((rarity?.ordinal ?: 0) >= HeroRarity.EPIC.ordinal) 90L else 140L
                if (effectsEnabled && isAttachedToWindow && windowVisibility == View.VISIBLE && hasWindowFocus()) postInvalidateDelayed(delay)
            }
        }
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (hasWindowFocus) invalidate() }
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp)) }

    private fun explore() {
        val headline = card(s("campaign", selected + 1, region(selected)))
        text(headline, s("explore_help", a.power(), a.slotCount()), small = true)
        val gather = a.node(selected) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)
        side(headline, button(s(if (gather) "gather" else "battle"), selected <= a.frontier, Weight.MAIN) {
            if (gather) gather(selected) else battle(BattleMode.CAMPAIGN, selected)
        } to 1.5f, button(s("prepare")) { tab = 2; scroll.scrollTo(0, 0); render() } to 1f,
            button(context.getString(R.string.hamlet_s_today)) { stewardship() }.apply { maxLines = 1 } to 1.35f)
        val map = HamletAdventureMapView(context, page, a, { s("number", it) }) { stage -> selected = stage; render() }
        map.selected = selected; map.effectsEnabled = effectsEnabled; map.contentDescription = s("map_accessibility")
        headline.addView(map, LinearLayout.LayoutParams(-1, dp((context.resources.configuration.screenWidthDp * 3 / 10).coerceIn(190, 300))).apply { topMargin = dp(4) })
        val nav = mutableListOf(button(s("previous"), page > 0, Weight.QUIET) { page--; selected = page * 12; render() } to 1f,
            button(s("next"), page < a.frontier / 12 + 1, Weight.QUIET) { page++; selected = page * 12; render() } to 1f)
        if (selected != a.frontier) nav.add(button(s("frontier_go"), weight = Weight.QUIET) { selected = a.frontier; page = selected / 12; render() } to 1f)
        side(headline, *nav.toTypedArray())
        val picker = Spinner(context)
        val stages = (page * 12 until page * 12 + 12).toList()
        picker.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, stages.map { s("stage_choice", it + 1, s("node_${a.node(it).name.lowercase()}")) })
        picker.setSelection(stages.indexOf(selected).coerceAtLeast(0), false)
        picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            var first = true
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { if (first) { first = false; return }; selected = stages[position]; render() }
        }
        headline.addView(picker)
        val box = card(s("stage_choice", selected + 1, s("node_${a.node(selected).name.lowercase()}")))
        text(box, s("loot_preview", 1 + selected / 10), small = true)
        if (selected == a.frontier) text(box, s("loot_first", 2), color = HamletUi.GOOD)
        if (selected > a.frontier) text(box, s("route_locked", a.frontier + 1), color = HamletUi.BAD)
        if (a.node(selected) !in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) {
            val foes = a.encounter(BattleMode.CAMPAIGN, selected)
            val field = BattleField.fromRegion(a.region(selected))
            val fieldName = raw("hamlet_combat_field_${field.name.lowercase()}")
            val fieldLine = line()
            fieldLine.addView(TextView(context).apply {
                text = fieldName; textSize = HamletUi.TEXT; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD); setPadding(dp(3), 0, dp(3), 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            fieldLine.addView(info(fieldName, raw("hamlet_combat_field_${field.name.lowercase()}_desc")), LinearLayout.LayoutParams(dp(44), -2))
            box.addView(fieldLine)
            val factions = foes.map { it.faction }.distinct()
            val names = factions.joinToString(raw("hamlet_list_separator")) { factionName(it) }
            text(box, HamletUi.tint(s("enemy_hint", names), *factions.map { factionName(it) to HamletUi.faction(it) }.toTypedArray()))
            val portraits = LinearLayout(context)
            for (foe in foes) portraits.addView(portrait(if (foe.heroVisual) "hero" else "enemy",
                if (foe.heroVisual) foe.sprite else HamletEnemies.visual(foe.sprite, a.region(selected)), 76), LinearLayout.LayoutParams(0, dp(76), 1f))
            box.addView(portraits)
        }
        if (a.canSweep(selected)) {
            val sweep = line()
            sweep.addView(button(s("sweep_cost", a.sweepCost(selected)), game.sandbox || a[AdventureResource.FOOD] >= a.sweepCost(selected)) {
                if (a.sweep(selected)) render()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            sweep.addView(info(s("sweep_cost", a.sweepCost(selected)), s("sweep_help")), LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(4) })
            box.addView(sweep)
        }
        rows.addView(button(s("next_unlocks"), weight = Weight.QUIET, onDark = true) {
            AlertDialog.Builder(context).setTitle(s("next_unlocks")).setMessage(s("unlock_roadmap")).setPositiveButton(android.R.string.ok, null).show()
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
    }
    private fun gather(stage: Int) {
        rows.removeAllViews()
        text(rows, s("gather_help"), true, false)
        val view = HamletGatherView(context, a.region(stage), haptics) {
            if (a.gather(stage)) { toast(s("gather_done")); selected = a.frontier; page = selected / 12; render() }
        }
        view.effectsEnabled = effectsEnabled
        view.contentDescription = s("gather_accessibility")
        rows.addView(view, LinearLayout.LayoutParams(-1, dp(340)))
        side(rows, button(s("gather_alternative")) {
            // Alternative accessible : chaque appui simule une cueillette, pas une victoire immédiate.
            val index = view.gathered
            if (index >= 8) return@button
            val points = arrayOf(0.22f to 0.6f, 0.64f to 0.52f, 0.42f to 0.74f, 0.8f to 0.76f, 0.32f to 0.44f, 0.6f to 0.82f, 0.82f to 0.46f, 0.18f to 0.82f)
            val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_UP, points[index].first * view.width, points[index].second * view.height, 0)
            view.onTouchEvent(event); event.recycle()
        } to 2f, button(s("cancel_free"), weight = Weight.QUIET, onDark = true) { render() } to 1f)
    }

    private fun heroes() {
        val recruit = card(s("recruitment_title"), s("recruitment_help") + "\n\n" + s("inn_recruitment", a.recruitmentWork))
        val pity = listOf(HeroRarity.EPIC to HamletAdventure.PITY_EPIC - a.pityEpic,
            HeroRarity.LEGENDARY to HamletAdventure.PITY_LEGENDARY - a.pityLegendary, HeroRarity.MYTHIC to HamletAdventure.PITY_MYTHIC - a.pityMythic)
        val parts = pity.map { (r, n) -> s("pity_in", s("rarity_${r.ordinal}"), n) }
        val list = parts.joinToString(context.getString(R.string.hamlet_ui_dot))
        text(recruit, HamletUi.tint(s("pity_line", list), *pity.map { (r, _) -> s("rarity_${r.ordinal}") to HamletUi.rarity(r) }.toTypedArray()), small = true)
        side(recruit, button(s("summon_one"), game.sandbox || a[AdventureResource.TICKETS] >= 1, Weight.MAIN) { summon(1) } to 1f,
            button(s("summon_ten"), game.sandbox || a[AdventureResource.TICKETS] >= 10, Weight.MAIN) { summon(10) } to 1f)
        val tools = line()
        tools.addView(CheckBox(context).apply {
            text = s("owned_filter", a.heroes.count { it.owned }, a.heroes.size); isChecked = ownedOnly; setTextColor(HamletUi.INK)
            textSize = HamletUi.SMALL; buttonTintList = ColorStateList.valueOf(0xFF376250.toInt())
            setOnCheckedChangeListener { _, checked -> ownedOnly = checked; render() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        tools.addView(button(raw("hamlet_shop_ui_entry"), weight = Weight.QUIET) { HamletShardShopDialog(context, game, ::render).show() },
            LinearLayout.LayoutParams(-2, -2))
        recruit.addView(tools)
        val filters = (-1..2).map { faction ->
            HamletUi.choice(button(if (faction < 0) s("all") else factionName(HeroFaction.entries[faction])) { heroFilter = faction; render() }, faction == heroFilter) to 1f
        }
        side(rows, *filters.toTypedArray())
        val filtered = a.heroes.filter { (!ownedOnly || it.owned) && (heroFilter < 0 || it.def.faction.ordinal == heroFilter) }
            .sortedWith(compareByDescending<HeroState> { it.owned }.thenByDescending { it.def.rarity.ordinal }.thenBy { it.id })
        val columns = (context.resources.configuration.screenWidthDp / 220).coerceIn(2, 5)
        for (group in filtered.chunked(columns)) {
            val row = LinearLayout(context)
            for (h in group) {
                val r = h.def.rarity
                val box = column().apply {
                    setPadding(dp(8), dp(8), dp(8), dp(8)); background = rarityCard(h.owned, r)
                    isClickable = true; isFocusable = true; contentDescription = heroName(h.id)
                    setOnClickListener { showHero(h.id) }
                }
                box.addView(portrait("hero", h.id, 120, framed = true))
                text(box, named(h.id), true)
                val rarityName = s("rarity_${r.ordinal}")
                text(box, rarityName, small = true, color = HamletUi.rarity(r))
                val faction = factionName(h.def.faction)
                text(box, HamletUi.tint(s("hero_kind", faction, s("role_${h.def.role.name.lowercase()}")), faction to HamletUi.faction(h.def.faction)), small = true)
                if (h.owned) text(box, s("hero_level", a.effectiveLevel(h.id), h.stars))
                else {
                    text(box, recruitmentRequirement(h), small = true)
                    if (h.shards > 0) text(box, s("shards_progress", h.shards, a.shardUnlockCost(h.id)), small = true)
                }
                row.addView(box, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(8), dp(3), 0) })
            }
            repeat(columns - group.size) { row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
            rows.addView(row)
        }
    }
    private fun recruitmentRequirement(h: HeroState): String = if (h.id < 12 && h.def.rescue > 0 && h.def.rarity.ordinal <= HeroRarity.RARE.ordinal)
        s("rescue_short", h.def.rescue)
    else raw("hamlet_hero_target_short", a.recruitFrontier(h.id), a.recruitChapter(h.id))

    private fun summon(count: Int) {
        val results = a.summon(count)
        if (results.isEmpty()) return
        render()
        val content = column().apply { setPadding(dp(12), dp(8), dp(12), dp(10)); setBackgroundColor(HamletUi.PAPER) }
        for (result in results) {
            val h = a.heroes[result.hero]
            val row = line()
            row.addView(portrait("hero", h.id, 80, framed = true), LinearLayout.LayoutParams(dp(80), dp(80)))
            val detail = column()
            val rarityName = s("rarity_${h.def.rarity.ordinal}")
            val value = if (result.newHero) s("recruit_new", heroName(h.id), rarityName) else s("recruit_duplicate", heroName(h.id), rarityName, result.shards)
            val color = HamletUi.rarity(h.def.rarity)
            text(detail, HamletUi.tint(value, heroName(h.id) to color, rarityName to color), title = result.newHero)
            if (result.guaranteed) text(detail, s("recruit_pity"), small = true, color = color)
            row.addView(detail, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) }); content.addView(row)
        }
        AlertDialog.Builder(context).setTitle(s("summon_results")).setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(android.R.string.ok, null).show()
    }
    private fun showHero(hero: Int) {
        val h = a.heroes[hero]
        val r = rarity(hero)
        val content = column().apply { setPadding(dp(14), dp(10), dp(14), dp(10)); setBackgroundColor(0xFFF3E8D1.toInt()) }
        content.addView(portrait("hero", hero, 150, framed = true))
        val rarityName = s("rarity_${r.ordinal}")
        val faction = factionName(h.def.faction)
        text(content, HamletUi.tint(rarityName + context.getString(R.string.hamlet_ui_dot) + s("hero_kind", faction, s("role_${h.def.role.name.lowercase()}")),
            rarityName to HamletUi.rarity(r), faction to HamletUi.faction(h.def.faction)))
        text(content, s("hero_story_$hero"), small = true)
        val sub = AlertDialog.Builder(context).setTitle(named(hero)).setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel, null).create()
        fun refresh(operation: () -> Boolean) { if (operation()) { sub.dismiss(); render(); showHero(hero) } }
        if (!h.owned) {
            text(content, recruitmentRequirement(h), true)
            text(content, s("shards_progress", h.shards, a.shardUnlockCost(hero)), small = true)
            content.addView(button(raw("hamlet_hero_target_cost", a.recruitCost(hero)), a.canFocusRecruit(hero), Weight.MAIN) { refresh { a.focusRecruit(hero) } })
            content.addView(button(s("recruit_shards_cost", a.shardUnlockCost(hero)), a.canRecruitShards(hero)) { refresh { a.recruitShards(hero) } })
            sub.show(); return
        }
        text(content, s("hero_growth", a.effectiveLevel(hero), h.stars, h.shards), true)
        val n = a.numbers(hero)
        text(content, s("hero_numbers", n.hp.toLong(), n.attack.toLong(), n.armor.toInt(), (n.crit * 100).toInt()), small = true)
        content.addView(button(s("level_cost", a.levelCost(hero)), game.sandbox || a[AdventureResource.FOOD] >= a.levelCost(hero), Weight.MAIN) { refresh { a.levelUp(hero) } })
        if (h.stars < HamletHeroes.STAR_CAP) {
            content.addView(button(s("stars_cost", a.starCost(hero)), a.canPromote(hero)) { refresh { a.promote(hero) } })
            text(content, raw("hamlet_hero_star_preview", h.stars + 1, a.starGainPercent(hero)), small = true)
        } else text(content, raw("hamlet_hero_stars_max", HamletHeroes.STAR_CAP), small = true, color = HamletUi.GOOD)
        val skills = line()
        skills.addView(TextView(context).apply { text = s("hero_skills"); textSize = HamletUi.TITLE; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        skills.addView(info(s("hero_skills"), s("skills_help")), LinearLayout.LayoutParams(dp(44), -2)); content.addView(skills)
        for (skill in 0..3) content.addView(button(s("skill_cost", s("skill_${h.def.role.name.lowercase()}_$skill"), h.skills[skill], a.skillCost(hero, skill)),
            game.sandbox || a[AdventureResource.BOOKS] >= a.skillCost(hero, skill)) { refresh { a.studySkill(hero, skill) } })
        text(content, s("traits_title"), true)
        for (trait in 0..3) content.addView(button(s("trait_cost", s("trait_$trait"), h.traits[trait], 12L * (h.traits[trait] + 1)),
            h.stars >= 5 && (game.sandbox || h.shards >= 12L * (h.traits[trait] + 1))) { refresh { a.promoteTrait(hero, trait) } })
        side(content, button(raw("hamlet_armory_title")) { sub.dismiss(); HamletArmoryDialog(context, game, ::render).show(1, hero) } to 1f,
            button(raw("hamlet_shop_ui_entry"), weight = Weight.QUIET) { sub.dismiss(); HamletShardShopDialog(context, game, ::render).show(hero) } to 1f)
        content.addView(button(raw("hamlet_hero_duplicate_cost", a.recruitCost(hero), HamletAdventure.DUPLICATE_SHARDS, heroName(hero)), a.canFocusRecruit(hero), Weight.QUIET) { refresh { a.focusRecruit(hero) } })
        val focus = line()
        focus.addView(button(raw(if (a.shardFocusHero == hero) "hamlet_hero_focus_clear" else "hamlet_hero_focus", heroName(hero)), weight = Weight.QUIET) {
            refresh { a.selectShardFocus(if (a.shardFocusHero == hero) -1 else hero) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        focus.addView(info(raw("hamlet_hero_focus", heroName(hero)), raw("hamlet_hero_focus_help")), LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(4) })
        content.addView(focus)
        sub.show()
    }

    private fun teams() {
        side(rows, *(0..2).map { i -> HamletUi.choice(button(s("team_preset", i + 1)) { a.selectTeam(i); render() }, i == a.selectedTeam) to 1f }.toTypedArray())
        val box = card(s("team_title", a.selectedTeam + 1, a.power()), s("formation_help"))
        text(box, s("training_level", a.trainingLevel()), small = true)
        val slots = column()
        for (range in listOf(0..1, 2..5)) {
            val row = LinearLayout(context)
            for (slot in range) {
                val hero = a.teams[a.selectedTeam][slot]
                val unlocked = slot < a.slotCount()
                val cell = column().apply {
                    setPadding(dp(4), dp(4), dp(4), dp(6)); gravity = Gravity.CENTER_HORIZONTAL
                    background = if (hero >= 0) rarityCard(true, rarity(hero)) else HamletUi.panel(context, if (unlocked) 0x22376250 else 0x14000000, 12, 0xFFCBBB97.toInt())
                    if (unlocked) { isClickable = true; isFocusable = true; setOnClickListener { chooseHero(slot) } }
                }
                if (hero >= 0) cell.addView(portrait("hero", hero, if (slot < 2) 110 else 84, framed = true))
                text(cell, if (!unlocked) s("slot_locked", if (slot == 4) 10 else 24) else if (hero < 0) s("empty_slot") else named(hero),
                    small = hero < 0, color = if (!unlocked) HamletUi.MUTED else 0).gravity = Gravity.CENTER
                row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
            }
            slots.addView(row)
        }
        box.addView(slots)
        action(box, s("auto_formation")) { a.autoFormation() }
        val tactic = a.tactics[a.selectedTeam]
        val tacticName = raw("hamlet_combat_tactic_${tactic.name.lowercase()}")
        val tacticHead = line()
        tacticHead.addView(TextView(context).apply { text = raw("hamlet_combat_tactic_title"); textSize = HamletUi.TEXT; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD); setPadding(dp(3), dp(6), 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        tacticHead.addView(info(tacticName, raw("hamlet_combat_tactic_${tactic.name.lowercase()}_desc")), LinearLayout.LayoutParams(dp(44), -2))
        box.addView(tacticHead)
        choices(box, BattleTactic.entries.map { t ->
            HamletUi.choice(button(raw("hamlet_combat_tactic_${t.name.lowercase()}")) { a.selectTactic(t); render() }, t == tactic)
        })
        val bonuses = HeroFaction.entries.map { f -> s("faction_bonus_short", factionName(f), (a.factionBonus(f) * 100).toInt()) to HamletUi.faction(f) }
        text(box, HamletUi.joined(context, bonuses))
        val chain = listOf(HeroFaction.GROVE, HeroFaction.FORGE, HeroFaction.DAWN, HeroFaction.GROVE)
        text(box, HamletUi.tint(chain.joinToString(context.getString(R.string.hamlet_ui_arrow)) { factionName(it) },
            *chain.map { factionName(it) to HamletUi.faction(it) }.toTypedArray()), small = true)
        val bonds = card(s("bonds"))
        val active = a.activeBonds()
        for ((index, members) in HamletHeroes.bonds.withIndex()) {
            val on = index in active
            text(bonds, s("bond_$index"), color = if (on) HamletUi.GOOD else HamletUi.MUTED).setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            text(bonds, members.joinToString(raw("hamlet_list_separator")) { heroName(it) }, small = true).setPadding(dp(14), 0, dp(3), dp(6))
        }
        val support = card(s("support"))
        for (pet in listOf(true, false)) {
            val values = if (pet) a.pets else a.relics
            for (i in values.indices) {
                val name = s(if (pet) "pet_$i" else "relic_$i")
                val equipped = (if (pet) a.teamPets else a.teamRelics)[a.selectedTeam] == i
                val row = line()
                if (pet) row.addView(portrait("enemy", HamletEnemies.pet(i), 64), LinearLayout.LayoutParams(dp(64), dp(64)))
                val label = column()
                text(label, s("support_row", name, values[i]), true, color = if (equipped) HamletUi.GOOD else 0)
                if (equipped) text(label, s("active"), small = true, color = HamletUi.GOOD)
                row.addView(label, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
                row.addView(info(name, s(if (pet) "pet_story_$i" else "relic_story_$i")), LinearLayout.LayoutParams(dp(44), -2))
                support.addView(row)
                side(support, button(s("equip"), values[i] > 0 && !equipped) { a.selectSupport(pet, i); render() } to 1f,
                    button(s("support_upgrade", (if (pet) 50L else 12L) * (values[i] + 1), s(if (pet) "resource_food" else "resource_crystal")), values[i] > 0, Weight.QUIET) {
                        if (a.upgradeSupport(pet, i)) render()
                    } to 1f)
            }
        }
    }
    private fun chooseHero(slot: Int) {
        val owned = a.heroes.filter { it.owned }.sortedWith(compareByDescending<HeroState> { it.def.rarity.ordinal }.thenByDescending { a.effectiveLevel(it.id) })
        val labels = owned.map { named(it.id, s("hero_choice", heroName(it.id), s("role_${it.def.role.name.lowercase()}"), a.effectiveLevel(it.id))) } + s("remove")
        AlertDialog.Builder(context).setTitle(s("choose_hero", slot + 1))
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == owned.size) a.remove(slot) else a.assign(owned[which].id, slot); render()
            }.show()
    }

    private fun craft() {
        val box = card(s("forge_title"), raw("hamlet_armory_help"))
        text(box, raw("hamlet_armory_inventory", game.armory.inventory.size, game.armory.inventory.count { game.armory.owner(it.id) != null }), small = true)
        text(box, raw("hamlet_armory_forge_rank", game.armory.forgeRank()), small = true)
        if (a.facility("adventureForge") == 0) box.addView(button(s("build_named", item("adventureForge")), weight = Weight.MAIN) { build("adventureForge") })
        side(box, button(raw("hamlet_armory_tab_0")) { HamletArmoryDialog(context, game, ::render).show() } to 1f,
            button(raw("hamlet_armory_tab_2")) { HamletArmoryDialog(context, game, ::render).show(2) } to 1f)
        for (hero in a.teams[a.selectedTeam].take(a.slotCount()).filter { it >= 0 }) {
            val worn = card(named(hero))
            if ((0..3).all { game.armory.equipped(hero, it) == null }) text(worn, raw("hamlet_armory_slot_empty"), small = true)
            else for (slot in 0..3) {
                val piece = game.armory.equipped(hero, slot)
                val slotName = s("gear_$slot")
                if (piece == null) { text(worn, raw("hamlet_progression_worn", slotName, raw("hamlet_armory_slot_empty")), small = true); continue }
                val rarityName = raw("hamlet_armory_rarity_${piece.rarity.ordinal}")
                val value = raw("hamlet_progression_worn", slotName, raw("hamlet_armory_piece", slotName, raw("hamlet_armory_set_${piece.set.name.lowercase()}"), rarityName, piece.level))
                text(worn, HamletUi.tint(value, slotName to HamletUi.INK, rarityName to HamletUi.gear(piece.rarity.ordinal)))
            }
            game.armory.setCounts(hero).forEach { (set, count) -> text(worn, raw("hamlet_armory_set_count", raw("hamlet_armory_set_${set.name.lowercase()}"), count), small = true, color = HamletUi.GOOD) }
            worn.addView(button(raw("hamlet_armory_tab_1")) { HamletArmoryDialog(context, game, ::render).show(1, hero) })
        }
        rows.addView(button(raw("hamlet_progression_legacy_gear"), weight = Weight.QUIET, onDark = true) { showLegacyGear = !showLegacyGear; render() }
            .apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
        if (!showLegacyGear) return
        for (hero in a.teams[a.selectedTeam].filter { it >= 0 }) {
            val h = a.heroes[hero]; val row = card(named(hero), if (hero == a.teams[a.selectedTeam].first { it >= 0 }) s("forge_help") else null)
            row.addView(portrait("hero", hero, 80, framed = true))
            for (slot in 0..3) {
                val rarityName = raw("hamlet_armory_rarity_${h.gearTier[slot]}")
                text(row, HamletUi.tint(s("gear_row", s("gear_$slot"), rarityName, h.gear[slot]), rarityName to HamletUi.gear(h.gearTier[slot])))
                side(row, button(s("forge_cost", a.forgeCost(hero, slot), h.gear[slot]), a.facility("adventureForge") > 0 || game.sandbox) { if (a.forge(hero, slot)) render() } to 1f,
                    button(s("refine_cost", (h.gearTier[slot] + 1) * 5, (h.gearTier[slot] + 1) * 10), h.gearTier[slot] < 3, Weight.QUIET) { if (a.refine(hero, slot)) render() } to 1f)
            }
            val worst = h.gearTier.minOrNull() ?: 0
            val worstName = raw("hamlet_armory_rarity_$worst")
            text(row, HamletUi.tint(s("mastery", h.gear.minOrNull() ?: 0, worstName), worstName to HamletUi.gear(worst)), small = true)
        }
    }

    private fun kingdom() {
        val duties = card(context.getString(R.string.hamlet_s_posts), context.getString(R.string.hamlet_s_posts_help))
        duties.addView(button(context.getString(R.string.hamlet_s_assign)) { stewardship(1) })
        section(s("facilities"), s("facilities_help"))
        for ((i, id) in HamletAdventure.FACILITIES.withIndex()) {
            val facility = card(item(id), s("facility_$i"))
            val showcase = line()
            showcase.addView(portrait("building", i, 96).apply { contentDescription = item(id) }, LinearLayout.LayoutParams(dp(96), dp(96)))
            val detail = column()
            text(detail, s("facility_level", a.facility(id)), small = true)
            val best = game.objs.values.filter { it.t == id && game.buildLeft(it) == 0L }.maxByOrNull { it.tier }
            if (best != null && game.canUpgrade(best)) action(detail, raw("hamlet_progression_upgrade_building", best.tier + 1, game.upgradeCost(best)),
                game.canUpgrade(best) && (game.sandbox || game.coins >= game.upgradeCost(best))) { game.upgrade(best) }
            detail.addView(button(s("build_cost", Hamlet.byId.getValue(id).cost, Hamlet.byId.getValue(id).lvl), weight = if (a.facility(id) == 0) Weight.NORMAL else Weight.QUIET) { build(id) })
            showcase.addView(detail, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
            facility.addView(showcase)
        }
        val workers = card(s("workers"), s("workers_help", a.workers.sum(), a.workerCapacity()))
        text(workers, s("workers_count", a.workers.sum(), a.workerCapacity()))
        a.productionPerMinute().forEachIndexed { i, rate -> text(workers, raw("hamlet_progression_rate", s("resource_${AdventureResource.entries[i].name.lowercase()}"), rate * 60), small = true) }
        for (i in a.workers.indices) {
            val row = line()
            row.addView(TextView(context).apply { text = s("worker_row", s("worker_$i"), a.workers[i]); textSize = HamletUi.TEXT; setTextColor(HamletUi.INK) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button(s("worker_minus"), a.workers[i] > 0) { a.assignWorker(i, -1); render() }, LinearLayout.LayoutParams(dp(54), -2))
            row.addView(button(s("worker_plus"), a.workers.sum() < a.workerCapacity()) { if (a.assignWorker(i, 1)) render() }, LinearLayout.LayoutParams(dp(54), -2).apply { marginStart = dp(6) })
            workers.addView(row)
        }
        val tech = card(s("academy"), s("academy_help"))
        for (i in a.tech.indices) action(tech, s("tech_cost", s("tech_$i"), a.tech[i], a.researchCost(i)), a.facility("adventureAcademy") > 0 || game.sandbox) { a.research(i) }
        val troops = card(s("troops"), s("troops_help", a.marchCapacity()) + "\n\n" + raw("hamlet_progression_militia_cap", a.maxTroopTier()))
        text(troops, s("militia_rank", a.maxTroopTier()), small = true)
        for (i in 0..2) {
            action(troops, s("troop_row", factionName(HeroFaction.entries[i]), a.troops[i]), a.facility("adventureYard") > 0 || game.sandbox) { a.trainTroops(i) }
            action(troops, s("troop_upgrade", a.troopTier[i], 20L * a.troopTier[i], 15L * a.troopTier[i]),
                (a.facility("adventureYard") > 0 || game.sandbox) && a.troopTier[i] < a.maxTroopTier(), Weight.QUIET) { a.improveTroops(i) }
        }
        val builders = card(s("builders"), s("builders_help", game.workSeconds))
        text(builders, s("builders_reserve", game.workSeconds), small = true)
        action(builders, s("builders_cost")) { a.supplyBuilders() }
        val workshop = card(raw("hamlet_progression_supply_workshop"), raw("hamlet_progression_supply_help"))
        action(workshop, raw("hamlet_progression_supply_ore"), game.sandbox || a.facility("adventureStore") > 0) { a.workshopSupplies(false) }
        action(workshop, raw("hamlet_progression_supply_books"), game.sandbox || a.facility("adventureAcademy") > 0) { a.workshopSupplies(true) }
    }
    private fun stewardship(initialTab: Int = 0) {
        HamletStewardshipDialog(context, game, onAdventure = { destination -> tab = destination; render() },
            onJournal = { destination -> HamletJourneyDialog(context, game, haptics).show(destination) }).show(initialTab)
    }
    private fun build(id: String) {
        val d = Hamlet.byId.getValue(id)
        if (!game.journey.unlocked(d)) return
        dialog.dismiss(); onBuild(d)
    }
    private fun challenges() {
        val unlocks = mapOf(BattleMode.TRIAL to 10, BattleMode.BOUNTY to 5, BattleMode.RAID to 20, BattleMode.ARENA to 12)
        for ((mode, gate) in unlocks) {
            val stage = when (mode) { BattleMode.TRIAL -> a.trial; BattleMode.BOUNTY -> a.bounty; BattleMode.RAID -> a.raid; else -> a.arena }
            val box = card(s("mode_${mode.name.lowercase()}"), s("mode_story_${mode.name.lowercase()}"))
            val open = a.frontier >= gate
            text(box, s("challenge_stage", stage + 1, gate), small = true, color = if (open) 0 else HamletUi.BAD)
            box.addView(button(s("battle"), open, Weight.MAIN) { battle(mode, stage) })
            if (stage > 0) box.addView(button(s("farm_previous"), open, Weight.QUIET) { battle(mode, stage - 1) })
        }
    }
    private fun battle(mode: BattleMode, stage: Int) {
        val before = a.bag.clone(); val coinsBefore = game.coins; val workBefore = game.workSeconds
        val battle = a.beginBattle(mode, stage) ?: return
        rows.removeAllViews(); scroll.scrollTo(0, 0)
        text(rows, s("combat_header", s("mode_${mode.name.lowercase()}"), stage + 1), true, false)
        val view = HamletBattleView(context, battle, { key, args -> s(key, *args) }, haptics) { result ->
            if (a.settle(result)) {
                if (haptics) root.performHapticFeedback(if (result.outcome == BattleOutcome.VICTORY) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
                rows.removeAllViews()
                val won = result.outcome == BattleOutcome.VICTORY
                val box = card(s(if (won) "victory" else "defeat"))
                (((box.getChildAt(0) as LinearLayout).getChildAt(0)) as TextView).apply {
                    setTextColor(if (won) 0xFFB27A12.toInt() else HamletUi.BAD); textSize = HamletUi.HEAD
                }
                text(box, s("battle_result", result.time.toInt(), result.damage.toLong(), result.healing.toLong(), result.survivors()), small = true)
                if (won) {
                    val gains = ArrayList<Pair<String, Int>>()
                    for (i in AdventureResource.entries.indices) {
                        val gained = a.bag[i] - before[i]
                        if (gained > 0) gains.add(s("gain", gained, s("resource_${AdventureResource.entries[i].name.lowercase()}")) to HamletUi.GOOD)
                    }
                    if (game.coins > coinsBefore) gains.add(s("gain", game.coins - coinsBefore, s("coins")) to 0xFFB27A12.toInt())
                    val work = (game.workSeconds - workBefore).coerceAtLeast(0)
                    if (work > 0) gains.add(s("gain", work, s("work")) to HamletUi.GOOD)
                    if (gains.isNotEmpty()) text(box, HamletUi.joined(context, gains))
                    box.addView(button(s("next_stage"), weight = Weight.MAIN) {
                        val next = when (mode) { BattleMode.CAMPAIGN -> a.frontier; BattleMode.TRIAL -> a.trial; BattleMode.BOUNTY -> a.bounty; BattleMode.RAID -> a.raid; BattleMode.ARENA -> a.arena }
                        selected = a.frontier; page = selected / 12
                        if (mode == BattleMode.CAMPAIGN && a.node(next) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) gather(next) else this.battle(mode, next)
                    })
                    side(box, button(s("continue")) { selected = a.frontier; page = selected / 12; render() } to 1f,
                        button(s("prepare"), weight = Weight.QUIET) { tab = 2; render() } to 1f)
                } else {
                    val timeout = result.survivors() > 0
                    val reason = line()
                    reason.addView(TextView(context).apply { text = s(if (timeout) "defeat_time" else "defeat_fallen"); textSize = HamletUi.TEXT; setTextColor(HamletUi.BAD); setPadding(dp(3), 0, dp(3), 0) }, LinearLayout.LayoutParams(0, -2, 1f))
                    reason.addView(info(s("defeat"), raw(if (timeout) "hamlet_progression_defeat_timeout" else "hamlet_progression_defeat_survival")), LinearLayout.LayoutParams(dp(44), -2))
                    box.addView(reason)
                    box.addView(button(s("retry_free"), weight = Weight.MAIN) { this.battle(mode, stage) })
                    side(box, button(s("continue")) { selected = a.frontier; page = selected / 12; render() } to 1f)
                    side(box, button(s("prepare"), weight = Weight.QUIET) { tab = 2; render() } to 1f,
                        button(raw("hamlet_armory_title"), weight = Weight.QUIET) { tab = 3; render() } to 1f,
                        button(s("heroes"), weight = Weight.QUIET) { tab = 1; render() } to 1f)
                }
                text(box, raw("hamlet_combat_report_title"), true)
                for (report in result.report()) {
                    text(box, named(report.sprite)).setTypeface(null, Typeface.BOLD)
                    text(box, raw("hamlet_combat_report_row", report.damage.toLong(), report.healing.toLong(), report.protection.toLong(), report.kills), small = true)
                }
                refreshStock()
            }
        }
        view.effectsEnabled = effectsEnabled
        view.contentDescription = s("combat_accessibility")
        rows.addView(view, LinearLayout.LayoutParams(-1, maxOf(dp(300), context.resources.displayMetrics.heightPixels * 52 / 100)))
        val auto = button(s(if (battle.auto) "auto_on" else "auto_off"))
        auto.setOnClickListener { battle.auto = !battle.auto; a.auto = battle.auto; auto.text = s(if (battle.auto) "auto_on" else "auto_off"); game.listener?.onChanged() }
        val speed = button(s("speed", 1)).apply { setOnClickListener { view.speed = if (view.speed == 4) 1 else view.speed * 2; text = s("speed", view.speed) } }
        val pause = button(s("pause")).apply { setOnClickListener { view.paused = !view.paused; text = s(if (view.paused) "resume" else "pause") } }
        for (b in listOf(auto, speed, pause)) { b.minHeight = dp(54); b.minimumHeight = dp(54); b.textSize = 15f }
        val controls = line()
        for (b in listOf(auto, speed, pause)) controls.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        controls.addView(info(s("combat_header", s("mode_${mode.name.lowercase()}"), stage + 1), s("manual_help"), onDark = true), LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(2) })
        rows.addView(controls)
        rows.addView(button(s("cancel_free"), weight = Weight.QUIET, onDark = true) { a.abandonBattle(); render() })
    }
}
