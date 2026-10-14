package com.Atom2Universe.app.games.hamlet

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
import android.widget.*
import com.Atom2Universe.app.R
import kotlin.math.min
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight

/**
 * Les modules Aventure (carte, défis, royaume) et Héros (héros, équipes, forge) : une seule page à
 * onglets, ouverte par la bannière commune. Gros boutons dorés pour ce qu'on fait sans cesse, boutons
 * discrets pour le reste ; les règles se lisent derrière un « ? », pas sur l'écran.
 */
internal class HamletAdventurePage(private val context: Context, private val game: HamletGame, private val nav: HamletNav) : HamletPage {
    companion object {
        const val EXPLORE = 0
        const val HEROES = 1
        const val TEAMS = 2
        const val FORGE = 3
        const val KINGDOM = 4
        const val CHALLENGES = 5
        private val ADVENTURE_TABS = intArrayOf(EXPLORE, CHALLENGES, KINGDOM)
        private val HERO_TABS = intArrayOf(HEROES, TEAMS, FORGE)
        private val NAMES = arrayOf("explore", "heroes", "teams", "craft", "kingdom", "challenges")
        private val GLYPHS = arrayOf("map", "hat", "shield", "anvil", "castle", "trophy")
        fun module(tab: Int) = if (tab in HERO_TABS) HamletModule.HEROES else HamletModule.ADVENTURE
    }
    private val a get() = game.adventure
    private val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    override val view: View get() = root
    private val strip = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val buildingArt = HamletArt()
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context)
    private var tab = EXPLORE
    private var lastAdventure = EXPLORE
    private var lastHeroes = HEROES
    private var group = -1
    /** A fight, a gathering or a result is on screen instead of the tab. */
    private var away = false
    private var fighting = false
    private var heroFilter = -1
    private var page = a.frontier / 12
    private var selected = a.frontier
    private val haptics get() = nav.haptics
    private val effectsEnabled get() = nav.effects
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
                       glyph: String? = null, click: () -> Unit = {}) = HamletUi.button(context, value, weight, onDark, glyph) {
        if (haptics) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
    private fun action(parent: LinearLayout, label: CharSequence, enabled: Boolean = true, weight: Weight = Weight.NORMAL, glyph: String? = null, operation: () -> Boolean) {
        parent.addView(button(label, enabled, weight, glyph = glyph) { if (operation()) render() })
    }
    private fun info(title: CharSequence, message: CharSequence, onDark: Boolean = false) = HamletUi.info(context, title, message, onDark)
    private fun toast(value: String) { Toast.makeText(context, value, Toast.LENGTH_SHORT).show() }
    /** A parchment card; [help] adds a "?" beside its title. */
    private fun card(title: CharSequence, help: CharSequence? = null, parent: LinearLayout = rows, glyph: String? = null): LinearLayout = column().also {
        it.background = HamletUi.card(context); it.setPadding(dp(13), dp(8), dp(13), dp(12)); it.elevation = dp(2).toFloat()
        parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val head = line()
        head.addView(TextView(context).apply {
            text = title; textSize = HamletUi.TITLE; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD)
            setPadding(dp(3), dp(4), dp(3), dp(4))
            if (glyph != null) { setCompoundDrawablesRelativeWithIntrinsicBounds(HamletUi.icon(context, glyph, 22), null, null, null); compoundDrawablePadding = dp(7) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (help != null) head.addView(info(title, help), LinearLayout.LayoutParams(dp(44), -2))
        it.addView(head)
    }

    init {
        strip.setPadding(dp(8), dp(6), dp(8), dp(6))
        strip.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(HamletUi.FOREST, 0xFF1A3631.toInt()))
        root.addView(strip, LinearLayout.LayoutParams(-1, -2))
        rows.setPadding(dp(10), 0, dp(10), dp(16))
        scroll.addView(rows); scroll.isFillViewport = true
        scroll.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF1A3631.toInt(), HamletUi.DEEP))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    /** The tab to open from the banner: the group's first on a second tap, else the last one used there. */
    fun first(heroes: Boolean, again: Boolean) = when {
        again -> if (heroes) HEROES else EXPLORE
        heroes -> lastHeroes
        else -> lastAdventure
    }
    override fun show(page: Int) {
        if (page < 0) { if (!away) render(); supplies(); return }
        if (page == tab && fighting) { supplies(); return }
        if (fighting || away) a.abandonBattle()
        tab = page; scroll.scrollTo(0, 0); render()
    }
    override fun hide() = Unit
    override fun tick() { supplies() }
    override fun back(): Boolean {
        if (!away) return false
        a.abandonBattle(); render(); return true
    }
    override fun close() { a.abandonBattle(); buildingArt.clear() }
    /** Another tab, followed by the banner. */
    private fun go(target: Int) = nav.open(module(target), target)

    /** L'atelier s'ouvre avec la route : au début, seuls les héros et l'équipe comptent. */
    private fun heroTabs() = if (game.sandbox || a.frontier >= HamletAdventure.FOURTH_SLOT || a.facility("adventureForge") > 0) HERO_TABS else intArrayOf(HEROES, TEAMS)

    private fun strip() {
        val heroes = tab in HERO_TABS
        val tabs = if (heroes) heroTabs() else ADVENTURE_TABS
        val wanted = (if (heroes) 1 else 0) + 2 * tabs.size
        if (group == wanted && strip.childCount > 0) {
            for (i in 0 until strip.childCount - 1) (strip.getChildAt(i) as Button).let { b -> HamletUi.choice(b, b.tag == tab) }
            return
        }
        group = wanted; strip.removeAllViews()
        for (t in tabs) {
            strip.addView(HamletUi.button(context, s(NAMES[t]), glyph = GLYPHS[t]) {
                if (haptics) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (t != tab || away) go(t)
            }.apply { tag = t; maxLines = 1; HamletUi.choice(this, t == tab) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        }
        strip.addView(info(s(if (heroes) "heroes" else "title"), s(if (heroes) "recruitment_help" else "help_text"), onDark = true), LinearLayout.LayoutParams(dp(44), -2))
    }
    private fun supplies() {
        a.advanceProduction()
        nav.supplies(emptyList())
    }
    private fun render() {
        a.advanceProduction(); rows.removeAllViews(); away = false; fighting = false
        if (tab in HERO_TABS) lastHeroes = tab else lastAdventure = tab
        strip(); supplies()
        when (tab) { EXPLORE -> explore(); HEROES -> heroes(); TEAMS -> teams(); FORGE -> craft(); KINGDOM -> kingdom(); else -> challenges() }
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
                if (effectsEnabled && isAttachedToWindow && isShown && hasWindowFocus()) postInvalidateDelayed(delay)
            }
        }
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (hasWindowFocus) invalidate() }
        override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); if (isVisible) invalidate() }
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp)) }

    private fun explore() {
        val headline = card(s("campaign", selected + 1, region(selected)), glyph = "map")
        text(headline, s("explore_help", a.power(), a.slotCount()), small = true)
        val gather = a.node(selected) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)
        side(headline, button(s(if (gather) "gather" else "battle"), selected <= a.frontier, Weight.MAIN, glyph = if (gather) "flower" else "sword") {
            if (gather) gather(selected) else battle(BattleMode.CAMPAIGN, selected)
        } to 1.6f, button(s("prepare"), glyph = "shield") { go(TEAMS) } to 1f)
        val map = HamletAdventureMapView(context, page, a, { s("number", it) }) { stage -> selected = stage; render() }
        map.selected = selected; map.effectsEnabled = effectsEnabled; map.contentDescription = s("map_accessibility")
        headline.addView(map, LinearLayout.LayoutParams(-1, dp((context.resources.configuration.screenWidthDp * 3 / 10).coerceIn(190, 300))).apply { topMargin = dp(4) })
        val nav = mutableListOf(button(s("previous"), page > 0, Weight.QUIET) { page--; selected = page * 12; render() } to 1f,
            button(s("next"), page < a.frontier / 12 + 1, Weight.QUIET) { page++; selected = page * 12; render() } to 1f)
        if (selected != a.frontier) nav.add(button(s("frontier_go"), weight = Weight.QUIET) { selected = a.frontier; page = selected / 12; render() } to 1f)
        side(headline, *nav.toTypedArray())
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
            sweep.addView(button(s("sweep_cost", a.sweepCost(selected)), game.sandbox || a[AdventureResource.FOOD] >= a.sweepCost(selected), glyph = "hourglass") {
                if (a.sweep(selected)) render()
            }, LinearLayout.LayoutParams(0, -2, 1f))
            sweep.addView(info(s("sweep_cost", a.sweepCost(selected)), s("sweep_help")), LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(4) })
            box.addView(sweep)
        }
        rows.addView(button(s("next_unlocks"), weight = Weight.QUIET, onDark = true) {
            HamletSheet.message(context, s("next_unlocks"), s("unlock_roadmap"))
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
    }
    private fun gather(stage: Int) {
        rows.removeAllViews(); away = true
        text(rows, s("gather_help"), true, false)
        val view = HamletGatherView(context, a.region(stage), haptics) {
            if (a.gather(stage)) { toast(s("gather_done")); selected = a.frontier; page = selected / 12; render() }
        }
        view.effectsEnabled = effectsEnabled
        view.contentDescription = s("gather_accessibility")
        rows.addView(view, LinearLayout.LayoutParams(-1, dp(340)))
        side(rows, button(s("gather_alternative"), glyph = "flower") {
            // Alternative accessible : chaque appui simule une cueillette, pas une victoire immédiate.
            val index = view.gathered
            if (index >= 8) return@button
            val points = arrayOf(0.22f to 0.6f, 0.64f to 0.52f, 0.42f to 0.74f, 0.8f to 0.76f, 0.32f to 0.44f, 0.6f to 0.82f, 0.82f to 0.46f, 0.18f to 0.82f)
            val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_UP, points[index].first * view.width, points[index].second * view.height, 0)
            view.onTouchEvent(event); event.recycle()
        } to 2f, button(s("cancel_free"), weight = Weight.QUIET, onDark = true) { render() } to 1f)
    }

    private fun heroes() {
        val tickets = a[AdventureResource.TICKETS]
        val call = card(s("call_title"), s("recruitment_help") + "\n\n" + s("inn_recruitment", a.recruitmentWork), glyph = "ticket")
        text(call, s("tickets_line", tickets), true)
        if (a.scriptedLeft() == 0) {
            val epic = s("rarity_${HeroRarity.EPIC.ordinal}")
            text(call, HamletUi.tint(s("pity_line", s("pity_in", epic, a.epicIn())), epic to HamletUi.rarity(HeroRarity.EPIC)), small = true)
        }
        side(call, button(s("summon_one"), game.sandbox || tickets >= 1, Weight.MAIN, glyph = "ticket") { summon(1) } to 1f,
            button(s("summon_ten"), game.sandbox || (tickets >= 10 && a.scriptedLeft() == 0), Weight.MAIN, glyph = "ticket") { summon(10) } to 1f)
        val owned = a.heroes.filter { it.owned }
        if (owned.isEmpty()) return
        side(rows, button(raw("hamlet_shop_ui_entry"), weight = Weight.QUIET, glyph = "gem", onDark = true) { HamletShardShopDialog(context, game, ::render).show() } to 1f)
        section(s("collection_title", owned.size), s("collection_help"))
        if (owned.size >= 4) {
            val filters = (-1..2).map { faction ->
                HamletUi.choice(button(if (faction < 0) s("all") else factionName(HeroFaction.entries[faction])) { heroFilter = faction; render() }, faction == heroFilter) to 1f
            }
            side(rows, *filters.toTypedArray())
        }
        val shown = owned.filter { heroFilter < 0 || it.def.faction.ordinal == heroFilter }
            .sortedWith(compareByDescending<HeroState> { it.def.rarity.ordinal }.thenBy { it.id })
        val columns = (context.resources.configuration.screenWidthDp / 220).coerceIn(2, 5)
        val cells = ArrayList<View>()
        for (h in shown) {
            val r = h.def.rarity
            val box = column().apply {
                setPadding(dp(8), dp(8), dp(8), dp(8)); background = rarityCard(true, r)
                isClickable = true; isFocusable = true; contentDescription = heroName(h.id)
                setOnClickListener { showHero(h.id) }
                HamletMotion.press(this)
            }
            box.addView(portrait("hero", h.id, 120, framed = true))
            text(box, named(h.id), true)
            text(box, s("rarity_${r.ordinal}"), small = true, color = HamletUi.rarity(r))
            val faction = factionName(h.def.faction)
            text(box, HamletUi.tint(s("hero_kind", faction, s("role_${h.def.role.name.lowercase()}")), faction to HamletUi.faction(h.def.faction)), small = true)
            text(box, s("hero_level", a.effectiveLevel(h.id), h.stars))
            cells.add(box)
        }
        // Les héros pas encore connus restent des cartes fermées : rien de leur nom ni de leur dessin. Une légende annoncée
        // par la rumeur s'y glisse en or et mène au récit de sa veillée.
        if (heroFilter < 0) {
            for (i in HamletLegends.defs.indices) if (game.legends.whispered(i)) cells.add(mystery(legend = i))
            val hidden = a.heroes.count { !it.owned && it.def.rarity.ordinal <= HeroRarity.EPIC.ordinal }
            repeat(min(hidden, columns)) { cells.add(mystery(legend = -1)) }
        }
        for (group in cells.chunked(columns)) {
            val row = LinearLayout(context)
            for (cell in group) row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(8), dp(3), 0) })
            repeat(columns - group.size) { row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)) }
            rows.addView(row)
        }
    }
    /** Une carte fermée : un point d'interrogation, jamais le dessin du héros. Une légende annoncée y est dorée. */
    private fun mystery(legend: Int): View = column().apply {
        gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(8), dp(8), dp(8), dp(8)); minimumHeight = dp(190)
        val gold = legend >= 0
        background = GradientDrawable().apply {
            setColor(if (gold) 0xFF2A2412.toInt() else 0xFF1B2D2A.toInt()); cornerRadius = dp(14).toFloat()
            setStroke(dp(if (gold) 3 else 2), if (gold) HamletHeroFx.main(HeroRarity.LEGENDARY) else 0xFF3F5A54.toInt())
        }
        addView(TextView(context).apply {
            text = context.getString(R.string.hamlet_a_unknown); textSize = 44f; gravity = Gravity.CENTER
            setTextColor(if (gold) HamletHeroFx.main(HeroRarity.LEGENDARY) else 0xFF6B8680.toInt()); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(130)))
        if (gold) {
            text(this, s("rumor_card"), small = true, color = HamletHeroFx.light(HeroRarity.LEGENDARY)).gravity = Gravity.CENTER
            isClickable = true; isFocusable = true
            setOnClickListener { nav.open(HamletModule.JOURNAL, HamletJournalPage.LEGENDS) }
            HamletMotion.press(this)
        }
    }

    private fun summon(count: Int) {
        val results = a.summon(count)
        if (results.isEmpty()) return
        render()
        val fresh = results.filter { it.newHero }.map { it.hero to false }
        val duplicates = results.filter { !it.newHero }
        fun summary() {
            if (duplicates.isEmpty()) return
            val sheet = HamletSheet(context, s("summon_results"), tall = duplicates.size > 3)
            duplicates.forEachIndexed { index, result ->
                val h = a.heroes[result.hero]
                val row = line()
                row.addView(portrait("hero", h.id, 80, framed = true), LinearLayout.LayoutParams(dp(80), dp(80)))
                val detail = column()
                val rarityName = s("rarity_${h.def.rarity.ordinal}")
                val color = HamletUi.rarity(h.def.rarity)
                text(detail, HamletUi.tint(s("recruit_duplicate", heroName(h.id), rarityName, result.shards), heroName(h.id) to color, rarityName to color))
                if (result.guaranteed) text(detail, s("recruit_pity"), small = true, color = color)
                row.addView(detail, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
                sheet.body.addView(row)
                HamletMotion.enter(row, 80L * index)
            }
            sheet.show()
        }
        HamletHeroReveal.sequence(context, fresh, effectsEnabled, haptics) { render(); nav.changed(); summary() }
    }
    private fun showHero(hero: Int) {
        val h = a.heroes[hero]
        if (!h.owned) return
        val r = rarity(hero)
        val sheet = HamletSheet(context, named(hero, onDark = true), tall = true)
        val content = sheet.body
        content.addView(portrait("hero", hero, 150, framed = true))
        val rarityName = s("rarity_${r.ordinal}")
        val faction = factionName(h.def.faction)
        text(content, HamletUi.tint(rarityName + context.getString(R.string.hamlet_ui_dot) + s("hero_kind", faction, s("role_${h.def.role.name.lowercase()}")),
            rarityName to HamletUi.rarity(r), faction to HamletUi.faction(h.def.faction)))
        text(content, s("hero_story_$hero"), small = true)
        fun refresh(operation: () -> Boolean) { if (operation()) { sheet.dismiss(); render(); showHero(hero) } }
        text(content, s("hero_growth", a.effectiveLevel(hero), h.stars, h.shards), true)
        val n = a.numbers(hero)
        text(content, s("hero_numbers", n.hp.toLong(), n.attack.toLong(), n.armor.toInt(), (n.crit * 100).toInt()), small = true)
        if (a.atLevelCap(hero)) content.addView(button(s("level_max", a.levelCap(hero)), false, Weight.MAIN, glyph = "apple"))
        else content.addView(button(s("level_cost", a.levelCost(hero)), game.sandbox || a[AdventureResource.FOOD] >= a.levelCost(hero), Weight.MAIN, glyph = "apple") { refresh { a.levelUp(hero) } })
        if (h.stars < HamletHeroes.STAR_CAP) {
            if (h.stars >= a.starLimit()) {
                val needed = (1..Hamlet.halls.size).first { HamletHeroes.starLimit(it) > h.stars }
                val locked = line()
                locked.addView(button(s("stars_hall", needed), false, glyph = "castle"), LinearLayout.LayoutParams(0, -2, 1f))
                locked.addView(info(s("stars_hall", needed), s("stars_hall_help")), LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(4) })
                content.addView(locked)
            } else content.addView(button(s("stars_cost", a.starCost(hero)), a.canPromote(hero), glyph = "star") { refresh { a.promote(hero) } })
            text(content, raw("hamlet_hero_star_preview", h.stars + 1, a.starGainPercent(hero)), small = true)
        } else text(content, raw("hamlet_hero_stars_max", HamletHeroes.STAR_CAP), small = true, color = HamletUi.GOOD)
        if (a.skillsOpen()) {
            val skills = line()
            skills.addView(TextView(context).apply { text = s("hero_skills"); textSize = HamletUi.TITLE; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
            skills.addView(info(s("hero_skills"), s("skills_help")), LinearLayout.LayoutParams(dp(44), -2)); content.addView(skills)
            for (skill in 0..3) {
                val name = s("skill_${h.def.role.name.lowercase()}_$skill")
                if (h.skills[skill] >= a.skillCap(hero)) content.addView(button(s("skill_max", name, h.skills[skill]), false, glyph = "book"))
                else content.addView(button(s("skill_cost", name, h.skills[skill], a.skillCost(hero, skill)),
                    game.sandbox || a[AdventureResource.BOOKS] >= a.skillCost(hero, skill), glyph = "book") { refresh { a.studySkill(hero, skill) } })
            }
        }
        if (a.traitCap(hero) > 0) {
            text(content, s("traits_title"), true)
            for (trait in 0..3) content.addView(button(s("trait_cost", s("trait_$trait"), h.traits[trait], 12L * (h.traits[trait] + 1)),
                h.traits[trait] < a.traitCap(hero) && (game.sandbox || h.shards >= 12L * (h.traits[trait] + 1)), glyph = "gem") { refresh { a.promoteTrait(hero, trait) } })
        }
        side(content, button(raw("hamlet_armory_title"), glyph = "anvil") { sheet.dismiss(); HamletArmoryDialog(context, game, ::render).show(1, hero) } to 1f,
            button(raw("hamlet_shop_ui_entry"), weight = Weight.QUIET, glyph = "gem") { sheet.dismiss(); HamletShardShopDialog(context, game, ::render).show(hero) } to 1f)
        val focus = line()
        focus.addView(button(raw(if (a.shardFocusHero == hero) "hamlet_hero_focus_clear" else "hamlet_hero_focus", heroName(hero)), weight = Weight.QUIET) {
            refresh { a.selectShardFocus(if (a.shardFocusHero == hero) -1 else hero) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        focus.addView(info(raw("hamlet_hero_focus", heroName(hero)), raw("hamlet_hero_focus_help")), LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(4) })
        content.addView(focus)
        sheet.show()
    }

    private fun teams() {
        side(rows, *(0..2).map { i -> HamletUi.choice(button(s("team_preset", i + 1)) { a.selectTeam(i); render() }, i == a.selectedTeam) to 1f }.toTypedArray())
        val box = card(s("team_title", a.selectedTeam + 1, a.power()), s("formation_help"), glyph = "shield")
        text(box, s("training_level", a.trainingLevel()), small = true)
        val slots = column()
        for (range in listOf(0..1, 2..4)) {
            val row = LinearLayout(context)
            for (slot in range) {
                val hero = a.teams[a.selectedTeam][slot]
                val unlocked = slot < a.slotCount()
                val cell = column().apply {
                    setPadding(dp(4), dp(4), dp(4), dp(6)); gravity = Gravity.CENTER_HORIZONTAL
                    background = if (hero >= 0) rarityCard(true, rarity(hero)) else HamletUi.panel(context, if (unlocked) 0x22376250 else 0x14000000, 12, 0xFFCBBB97.toInt())
                    if (unlocked) { isClickable = true; isFocusable = true; setOnClickListener { chooseHero(slot) }; HamletMotion.press(this) }
                }
                if (hero >= 0) cell.addView(portrait("hero", hero, if (slot < 2) 110 else 84, framed = true))
                text(cell, if (!unlocked) s("slot_locked", if (slot == 3) HamletAdventure.FOURTH_SLOT else HamletAdventure.FIFTH_SLOT) else if (hero < 0) s("empty_slot") else named(hero),
                    small = hero < 0, color = if (!unlocked) HamletUi.MUTED else 0).gravity = Gravity.CENTER
                row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
            }
            slots.addView(row)
        }
        box.addView(slots)
        action(box, s("auto_formation"), glyph = "star") { a.autoFormation() }
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
        val bonds = card(s("bonds"), glyph = "people")
        val active = a.activeBonds()
        for ((index, members) in HamletHeroes.bonds.withIndex()) {
            val on = index in active
            text(bonds, s("bond_$index"), color = if (on) HamletUi.GOOD else HamletUi.MUTED).setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            text(bonds, members.joinToString(raw("hamlet_list_separator")) { heroName(it) }, small = true).setPadding(dp(14), 0, dp(3), dp(6))
        }
        val support = card(s("support"), glyph = "paw")
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
        val labels: List<CharSequence> = owned.map { named(it.id, s("hero_choice", heroName(it.id), s("role_${it.def.role.name.lowercase()}"), a.effectiveLevel(it.id))) } + s("remove")
        val current = owned.indexOfFirst { it.id == a.teams[a.selectedTeam][slot] }
        HamletSheet.pick(context, s("choose_hero", slot + 1), labels, current, owned.map { "hat" } + "cross") { which ->
            if (which == owned.size) a.remove(slot) else a.assign(owned[which].id, slot); render()
        }
    }

    private fun craft() {
        val box = card(s("forge_title"), raw("hamlet_armory_help"), glyph = "anvil")
        text(box, raw("hamlet_armory_inventory", game.armory.inventory.size, game.armory.inventory.count { game.armory.owner(it.id) != null }), small = true)
        text(box, raw("hamlet_armory_forge_rank", game.armory.forgeRank()), small = true)
        if (a.facility("adventureForge") == 0) box.addView(button(s("build_named", item("adventureForge")), weight = Weight.MAIN, glyph = "hammer") { build("adventureForge") })
        side(box, button(raw("hamlet_armory_tab_0"), glyph = "anvil") { HamletArmoryDialog(context, game, ::render).show() } to 1f,
            button(raw("hamlet_armory_tab_2"), glyph = "crate") { HamletArmoryDialog(context, game, ::render).show(2) } to 1f)
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
            worn.addView(button(raw("hamlet_armory_tab_1"), glyph = "shield") { HamletArmoryDialog(context, game, ::render).show(1, hero) })
        }
    }

    private fun kingdom() {
        val duties = card(context.getString(R.string.hamlet_s_posts), context.getString(R.string.hamlet_s_posts_help), glyph = "people")
        duties.addView(button(context.getString(R.string.hamlet_s_assign), glyph = "hat") { nav.open(HamletModule.JOURNAL, HamletJournalPage.POSTS) })
        section(s("facilities"), s("facilities_help"))
        for ((i, id) in HamletAdventure.FACILITIES.withIndex()) {
            val facility = card(item(id), s("facility_$i"))
            val showcase = line()
            showcase.addView(portrait("building", i, 96).apply { contentDescription = item(id) }, LinearLayout.LayoutParams(dp(96), dp(96)))
            val detail = column()
            text(detail, s("facility_level", a.facility(id)), small = true)
            val best = game.objs.values.filter { it.t == id && game.buildLeft(it) == 0L }.maxByOrNull { it.tier }
            if (best != null && game.canUpgrade(best)) action(detail, raw("hamlet_progression_upgrade_building", best.tier + 1, game.upgradeCost(best)),
                game.canUpgrade(best) && (game.sandbox || game.coins >= game.upgradeCost(best)), glyph = "star") { game.upgrade(best) }
            detail.addView(button(s("build_cost", Hamlet.byId.getValue(id).cost, Hamlet.byId.getValue(id).lvl),
                weight = if (a.facility(id) == 0) Weight.NORMAL else Weight.QUIET, glyph = "hammer") { build(id) })
            showcase.addView(detail, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
            facility.addView(showcase)
        }
        val workers = card(s("workers"), s("workers_help", a.workers.sum(), a.workerCapacity()), glyph = "people")
        text(workers, s("workers_count", a.workers.sum(), a.workerCapacity()))
        a.productionPerMinute().forEachIndexed { i, rate -> text(workers, raw("hamlet_progression_rate", s("resource_${AdventureResource.entries[i].name.lowercase()}"), rate * 60), small = true) }
        for (i in a.workers.indices) {
            val row = line()
            row.addView(TextView(context).apply { text = s("worker_row", s("worker_$i"), a.workers[i]); textSize = HamletUi.TEXT; setTextColor(HamletUi.INK) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button(s("worker_minus"), a.workers[i] > 0) { a.assignWorker(i, -1); render() }, LinearLayout.LayoutParams(dp(54), -2))
            row.addView(button(s("worker_plus"), a.workers.sum() < a.workerCapacity()) { if (a.assignWorker(i, 1)) render() }, LinearLayout.LayoutParams(dp(54), -2).apply { marginStart = dp(6) })
            workers.addView(row)
        }
        val tech = card(s("academy"), s("academy_help"), glyph = "book")
        for (i in a.tech.indices) action(tech, s("tech_cost", s("tech_$i"), a.tech[i], a.researchCost(i)), a.facility("adventureAcademy") > 0 || game.sandbox) { a.research(i) }
        val troops = card(s("troops"), s("troops_help", a.marchCapacity()) + "\n\n" + raw("hamlet_progression_militia_cap", a.maxTroopTier()), glyph = "lance")
        text(troops, s("militia_rank", a.maxTroopTier()), small = true)
        for (i in 0..2) {
            action(troops, s("troop_row", factionName(HeroFaction.entries[i]), a.troops[i]), a.facility("adventureYard") > 0 || game.sandbox) { a.trainTroops(i) }
            action(troops, s("troop_upgrade", a.troopTier[i], 20L * a.troopTier[i], 15L * a.troopTier[i]),
                (a.facility("adventureYard") > 0 || game.sandbox) && a.troopTier[i] < a.maxTroopTier(), Weight.QUIET) { a.improveTroops(i) }
        }
        val builders = card(s("builders"), s("builders_help", game.workSeconds), glyph = "hammer")
        text(builders, s("builders_reserve", game.workSeconds), small = true)
        action(builders, s("builders_cost")) { a.supplyBuilders() }
        val workshop = card(raw("hamlet_progression_supply_workshop"), raw("hamlet_progression_supply_help"), glyph = "crate")
        action(workshop, raw("hamlet_progression_supply_ore"), game.sandbox || a.facility("adventureStore") > 0, glyph = "ore") { a.workshopSupplies(false) }
        action(workshop, raw("hamlet_progression_supply_books"), game.sandbox || a.facility("adventureAcademy") > 0, glyph = "book") { a.workshopSupplies(true) }
    }
    private fun build(id: String) {
        val d = Hamlet.byId.getValue(id)
        if (!game.journey.unlocked(d)) return
        nav.build(d)
    }
    private fun challenges() {
        val unlocks = mapOf(BattleMode.TRIAL to 10, BattleMode.BOUNTY to 5, BattleMode.RAID to 20, BattleMode.ARENA to 12)
        val glyphs = mapOf(BattleMode.TRIAL to "hourglass", BattleMode.BOUNTY to "coins", BattleMode.RAID to "castle", BattleMode.ARENA to "trophy")
        for ((mode, gate) in unlocks) {
            val stage = when (mode) { BattleMode.TRIAL -> a.trial; BattleMode.BOUNTY -> a.bounty; BattleMode.RAID -> a.raid; else -> a.arena }
            val box = card(s("mode_${mode.name.lowercase()}"), s("mode_story_${mode.name.lowercase()}"), glyph = glyphs[mode])
            val open = a.frontier >= gate
            text(box, s("challenge_stage", stage + 1, gate), small = true, color = if (open) 0 else HamletUi.BAD)
            box.addView(button(s("battle"), open, Weight.MAIN, glyph = "sword") { battle(mode, stage) })
            if (stage > 0) box.addView(button(s("farm_previous"), open, Weight.QUIET) { battle(mode, stage - 1) })
        }
    }
    private fun battle(mode: BattleMode, stage: Int) {
        val before = a.bag.clone(); val coinsBefore = game.coins; val workBefore = game.workSeconds
        val battle = a.beginBattle(mode, stage) ?: return
        rows.removeAllViews(); scroll.scrollTo(0, 0); away = true; fighting = true
        text(rows, s("combat_header", s("mode_${mode.name.lowercase()}"), stage + 1), true, false)
        val view = HamletBattleView(context, battle, { key, args -> s(key, *args) }, haptics) { result ->
            fighting = false
            if (a.settle(result)) {
                if (haptics) root.performHapticFeedback(if (result.outcome == BattleOutcome.VICTORY) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
                rows.removeAllViews()
                val won = result.outcome == BattleOutcome.VICTORY
                val box = card(s(if (won) "victory" else "defeat"), glyph = if (won) "trophy" else "shield")
                (((box.getChildAt(0) as LinearLayout).getChildAt(0)) as TextView).apply {
                    setTextColor(if (won) 0xFFB27A12.toInt() else HamletUi.BAD); textSize = HamletUi.HEAD
                }
                HamletMotion.enter(box)
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
                    if (a.lastFind) HamletMotion.pop(text(box, s("find"), true, color = 0xFFB27A12.toInt()), 1.2f)
                    else if (a.lastFirst) text(box, s("first_clear"), small = true, color = HamletUi.GOOD)
                    if (gains.isNotEmpty()) text(box, HamletUi.joined(context, gains))
                    box.addView(button(s("next_stage"), weight = Weight.MAIN, glyph = "sword") {
                        val next = when (mode) { BattleMode.CAMPAIGN -> a.frontier; BattleMode.TRIAL -> a.trial; BattleMode.BOUNTY -> a.bounty; BattleMode.RAID -> a.raid; BattleMode.ARENA -> a.arena }
                        selected = a.frontier; page = selected / 12
                        if (mode == BattleMode.CAMPAIGN && a.node(next) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) gather(next) else this.battle(mode, next)
                    })
                    side(box, button(s("continue"), glyph = "map") { selected = a.frontier; page = selected / 12; render() } to 1f,
                        button(s("prepare"), weight = Weight.QUIET) { go(TEAMS) } to 1f)
                } else {
                    val timeout = result.survivors() > 0
                    val reason = line()
                    reason.addView(TextView(context).apply { text = s(if (timeout) "defeat_time" else "defeat_fallen"); textSize = HamletUi.TEXT; setTextColor(HamletUi.BAD); setPadding(dp(3), 0, dp(3), 0) }, LinearLayout.LayoutParams(0, -2, 1f))
                    reason.addView(info(s("defeat"), raw(if (timeout) "hamlet_progression_defeat_timeout" else "hamlet_progression_defeat_survival")), LinearLayout.LayoutParams(dp(44), -2))
                    box.addView(reason)
                    box.addView(button(s("retry_free"), weight = Weight.MAIN, glyph = "sword") { this.battle(mode, stage) })
                    side(box, button(s("continue"), glyph = "map") { selected = a.frontier; page = selected / 12; render() } to 1f)
                    side(box, button(s("prepare"), weight = Weight.QUIET) { go(TEAMS) } to 1f,
                        button(raw("hamlet_armory_title"), weight = Weight.QUIET) { go(FORGE) } to 1f,
                        button(s("heroes"), weight = Weight.QUIET) { go(HEROES) } to 1f)
                }
                text(box, raw("hamlet_combat_report_title"), true)
                for (report in result.report()) {
                    text(box, named(report.sprite)).setTypeface(null, Typeface.BOLD)
                    text(box, raw("hamlet_combat_report_row", report.damage.toLong(), report.healing.toLong(), report.protection.toLong(), report.kills), small = true)
                }
                supplies(); nav.changed()
            }
        }
        view.effectsEnabled = effectsEnabled
        view.contentDescription = s("combat_accessibility")
        rows.addView(view, LinearLayout.LayoutParams(-1, maxOf(dp(300), context.resources.displayMetrics.heightPixels * 48 / 100)))
        val auto = button(s(if (battle.auto) "auto_on" else "auto_off"), glyph = "gear")
        auto.setOnClickListener { battle.auto = !battle.auto; a.auto = battle.auto; auto.text = s(if (battle.auto) "auto_on" else "auto_off"); HamletUi.choice(auto, battle.auto); nav.changed() }
        HamletUi.choice(auto, battle.auto)
        val speed = button(s("speed", 1), glyph = "hourglass").apply { setOnClickListener { view.speed = if (view.speed == 4) 1 else view.speed * 2; text = s("speed", view.speed); HamletMotion.pop(this, 1.08f) } }
        val pause = button(s("pause"), glyph = "clock").apply { setOnClickListener { view.paused = !view.paused; text = s(if (view.paused) "resume" else "pause"); HamletUi.choice(this, view.paused) } }
        for (b in listOf(auto, speed, pause)) { b.minHeight = dp(54); b.minimumHeight = dp(54); b.textSize = 15f }
        val controls = line()
        for (b in listOf(auto, speed, pause)) controls.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        controls.addView(info(s("combat_header", s("mode_${mode.name.lowercase()}"), stage + 1), s("manual_help"), onDark = true), LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(2) })
        rows.addView(controls)
        rows.addView(button(s("cancel_free"), weight = Weight.QUIET, onDark = true) { a.abandonBattle(); render() })
    }
}
