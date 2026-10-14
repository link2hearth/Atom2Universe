package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import com.Atom2Universe.app.R

/** Écran de campagne à plein écran. Le village et sa sauvegarde restent ceux du joueur. */
internal class HamletAdventureDialog(private val context: Context, private val game: HamletGame,
    private val haptics: Boolean = false, private val assets: HamletAssets = HamletAssets(context),
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
    private fun region(stage: Int) = raw("hamlet_region_${a.region(stage)}")
    private fun item(id: String) = raw("hamlet_n_$id")
    private fun bg(color: Int, radius: Int = 14) = HamletUi.panel(context, color, radius, 0xFFD4C5A9.toInt())
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun text(parent: LinearLayout, value: String, title: Boolean = false, dark: Boolean = true): TextView = TextView(context).also {
        it.text = value; it.textSize = if (title) 17f else 13f
        it.setTextColor(if (dark) HamletUi.INK else HamletUi.PAPER); it.setPadding(dp(3), dp(5), dp(3), dp(6)); it.setLineSpacing(dp(2).toFloat(), 1f)
        if (title) it.setTypeface(null, Typeface.BOLD)
        parent.addView(it)
    }
    private fun button(value: String, enabled: Boolean = true, click: () -> Unit = {}) = HamletUi.button(context, value) {
        if (haptics && ::root.isInitialized) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        click()
    }.apply {
        isEnabled = enabled
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(3) }
    }
    private fun action(parent: LinearLayout, label: String, enabled: Boolean = true, operation: () -> Boolean) {
        parent.addView(button(label, enabled) { if (operation()) render() else toast(s("not_ready")) })
    }
    private fun toast(value: String) { Toast.makeText(context, value, Toast.LENGTH_SHORT).show() }
    private fun card(title: String): LinearLayout = column().also {
        it.background = bg(HamletUi.PAPER); it.setPadding(dp(13), dp(10), dp(13), dp(13)); it.elevation = dp(2).toFloat()
        rows.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }); text(it, title, true)
    }
    fun show(initialTab: Int = 0) {
        tab = initialTab
        dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        root = column().apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(HamletUi.FOREST, 0xFF102D2A.toInt()))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val title = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(TextView(context).apply { text = s("title"); textSize = 19f; setTextColor(HamletUi.GOLD); setTypeface(null, Typeface.BOLD) }, LinearLayout.LayoutParams(0, -2, 1f))
        fun headButton(value: String, action: () -> Unit): Button = button(value, click = action).also {
            it.textSize = 11f; it.setPadding(dp(7), dp(3), dp(7), dp(3))
            title.addView(it, LinearLayout.LayoutParams(-2, dp(48)).apply { marginStart = dp(4) })
        }
        headButton(s("village")) { dialog.dismiss() }
        headButton(s("help")) { AlertDialog.Builder(context).setTitle(s("title")).setMessage(s("help_text")).setPositiveButton(android.R.string.ok, null).show() }
        if (onExit != null) headButton(context.getString(R.string.hamlet_ui_exit)) { if (onExit.invoke()) dialog.dismiss() }.apply {
            contentDescription = context.getString(R.string.hamlet_ui_exit_description)
        }
        root.addView(title)
        stock = LinearLayout(context).apply { setPadding(0, dp(5), 0, dp(5)) }
        val glyphs = arrayOf("apple", "wood", "ore", "gear", "book", "gem", "star", "ticket")
        for (i in glyphs.indices) {
            val chip = TextView(context).apply {
                textSize = 12f; gravity = Gravity.CENTER_VERTICAL; setTextColor(HamletUi.PAPER)
                setPadding(dp(8), dp(6), dp(10), dp(6)); background = HamletUi.panel(context, 0xFF305347.toInt(), 9)
                setCompoundDrawablesWithIntrinsicBounds(HamletUi.icon(context, glyphs[i], 18), null, null, null); compoundDrawablePadding = dp(5)
                isSingleLine = true
            }
            resourceChips.add(chip)
            stock.addView(chip, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(5) })
        }
        root.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = true; addView(stock); contentDescription = context.getString(R.string.hamlet_ui_resources)
        }, LinearLayout.LayoutParams(-1, dp(45)))
        rows = column(); scroll = ScrollView(context).apply { addView(rows); isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val tabs = LinearLayout(context).apply { setPadding(0, dp(6), 0, 0) }
        val names = arrayOf("explore", "heroes", "teams", "craft", "kingdom", "challenges")
        val icons = arrayOf("map", "hat", "shield", "gear", "crate", "sword")
        for (i in names.indices) tabs.addView(HamletUi.navigation(context, s(names[i]), icons[i]) {
            if (haptics) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            a.abandonBattle(); tab = i; scroll.scrollTo(0, 0); render()
        }.also { tabButtons.add(it) }, LinearLayout.LayoutParams(0, dp(66), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        root.addView(tabs)
        dialog.setContentView(root, android.view.ViewGroup.LayoutParams(-1, -1))
        dialog.setOnDismissListener { a.abandonBattle(); assets.clear(); buildingArt.clear(); onClose() }
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
    private fun portrait(kind: String, id: Int, heightDp: Int = 94): View = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        private val destination = RectF()
        private val sprite = if (kind == "building") buildingArt.forDef(Hamlet.byId.getValue(HamletAdventure.FACILITIES[id])) else null
        override fun onDraw(c: Canvas) {
            val size = minOf(width.toFloat(), height.toFloat())
            if (sprite != null) {
                val scale = minOf(width.toFloat() / sprite.bmp.width, height.toFloat() / sprite.bmp.height) * .94f
                val w = sprite.bmp.width * scale; val h = sprite.bmp.height * scale
                destination.set((width - w) / 2, height - h, (width + w) / 2, height.toFloat())
                c.drawBitmap(sprite.bmp, null, destination, paint)
            } else {
                val time = if (effectsEnabled) android.os.SystemClock.uptimeMillis() / 1000f else 0f
                HamletCharacterArt.draw(c, kind == "hero", id, width / 2f, height.toFloat(), size, time = time)
                if (effectsEnabled && isAttachedToWindow && windowVisibility == View.VISIBLE && hasWindowFocus()) postInvalidateDelayed(140L)
            }
        }
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (hasWindowFocus) invalidate() }
    }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp)) }
    private fun explore() {
        val headline = card(s("campaign", selected + 1, region(selected), a.frontier + 1))
        text(headline, s("explore_help", a.power(), a.slotCount()))
        headline.addView(button(context.getString(R.string.hamlet_s_today)) { stewardship() })
        val gather = a.node(selected) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)
        val actions = LinearLayout(context)
        actions.addView(button(s(if (gather) "gather" else "battle"), selected <= a.frontier) { if (gather) gather(selected) else battle(BattleMode.CAMPAIGN, selected) }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button(s("prepare")) { tab = 2; scroll.scrollTo(0, 0); render() }, LinearLayout.LayoutParams(0, -2, 1f))
        headline.addView(actions)
        val nav = LinearLayout(context)
        nav.addView(button(s("previous"), page > 0) { page--; selected = page * 12; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(button(s("next"), page < a.frontier / 12 + 1) { page++; selected = page * 12; render() }, LinearLayout.LayoutParams(0, -2, 1f)); headline.addView(nav)
        if (selected != a.frontier) headline.addView(button(s("frontier_go")) { selected = a.frontier; page = selected / 12; render() })
        val map = HamletAdventureMapView(context, assets, page, a, { s("number", it) }) { stage -> selected = stage; render() }
        map.selected = selected; map.contentDescription = s("map_accessibility")
        headline.addView(map, LinearLayout.LayoutParams(-1, dp(180)))
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
        text(box, s("loot_preview", 1 + selected / 10, if (selected == a.frontier) 2 else 1))
        if (a.node(selected) !in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) {
            val foes = a.encounter(BattleMode.CAMPAIGN, selected)
            val field = BattleField.fromRegion(a.region(selected))
            text(box, raw("hamlet_combat_field_${field.name.lowercase()}"), true)
            text(box, raw("hamlet_combat_field_${field.name.lowercase()}_desc"))
            text(box, s("enemy_hint", foes.map { it.faction }.distinct().joinToString(" · ") { s("faction_${it.ordinal}") }))
            val line = LinearLayout(context)
            for (foe in foes) line.addView(portrait(if (foe.heroVisual) "hero" else "enemy", foe.sprite, 70), LinearLayout.LayoutParams(0, dp(70), 1f))
            box.addView(line)
        } else text(box, s("gather_help"))
        if (selected > a.frontier) text(box, s("route_locked", a.frontier + 1))
        if (a.canSweep(selected)) {
            text(box, s("sweep_help"))
            action(box, s("sweep_cost", a.sweepCost(selected)), game.sandbox || a[AdventureResource.FOOD] >= a.sweepCost(selected)) { a.sweep(selected) }
        }
        val unlock = card(s("next_unlocks"))
        text(unlock, s("unlock_roadmap"))
    }
    private fun gather(stage: Int) {
        rows.removeAllViews()
        text(rows, s("gather_help"), true, false)
        val view = HamletGatherView(context, assets, a.region(stage), haptics) {
            if (a.gather(stage)) { toast(s("gather_done")); selected = a.frontier; page = selected / 12; render() }
        }
        view.effectsEnabled = effectsEnabled
        view.contentDescription = s("gather_accessibility")
        view.effectsEnabled = effectsEnabled
        rows.addView(view, LinearLayout.LayoutParams(-1, dp(340)))
        rows.addView(button(s("gather_alternative")) {
            // Alternative accessible : chaque appui simule une cueillette, pas une victoire immédiate.
            val index = view.gathered
            if (index >= 8) return@button
            val points = arrayOf(0.22f to 0.6f, 0.64f to 0.52f, 0.42f to 0.74f, 0.8f to 0.76f, 0.32f to 0.44f, 0.6f to 0.82f, 0.82f to 0.46f, 0.18f to 0.82f)
            val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_UP, points[index].first * view.width, points[index].second * view.height, 0)
            view.onTouchEvent(event); event.recycle()
        })
        rows.addView(button(s("cancel_free")) { render() })
    }
    private fun heroes() {
        val recruit = card(s("recruitment_title"))
        text(recruit, raw("hamlet_hero_recruitment_brief"))
        recruit.addView(button(raw("hamlet_hero_recruitment_rules")) {
            AlertDialog.Builder(context).setTitle(s("recruitment_title"))
                .setMessage(s("recruitment_help")).setPositiveButton(android.R.string.ok, null).show()
        })
        text(recruit, s("recruitment_progress", a.recruitmentPulls, HamletAdventure.PITY_EPIC - a.pityEpic, HamletAdventure.PITY_LEGENDARY - a.pityLegendary, HamletAdventure.PITY_MYTHIC - a.pityMythic))
        text(recruit, s("inn_recruitment", a.recruitmentWork))
        val draws = LinearLayout(context)
        draws.addView(button(s("summon_one"), game.sandbox || a[AdventureResource.TICKETS] >= 1) { summon(1) }, LinearLayout.LayoutParams(0, -2, 1f))
        draws.addView(button(s("summon_ten"), game.sandbox || a[AdventureResource.TICKETS] >= 10) { summon(10) }, LinearLayout.LayoutParams(0, -2, 1f)); recruit.addView(draws)
        recruit.addView(button(raw("hamlet_shop_ui_entry")) { HamletShardShopDialog(context, game, ::render).show() })
        val owned = CheckBox(context).apply {
            text = s("owned_filter", a.heroes.count { it.owned }, a.heroes.size); isChecked = ownedOnly; setTextColor(HamletUi.INK)
            setOnCheckedChangeListener { _, checked -> ownedOnly = checked; render() }
        }
        recruit.addView(owned)
        val filters = LinearLayout(context)
        for (faction in -1..2) filters.addView(button(s(if (faction < 0) "all" else "faction_$faction")) { heroFilter = faction; render() }.apply {
            isSelected = faction == heroFilter
            background = HamletUi.control(context, selected = isSelected)
            setTextColor(if (isSelected) HamletUi.INK else HamletUi.PAPER)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        rows.addView(filters)
        val filtered = a.heroes.filter { (!ownedOnly || it.owned) && (heroFilter < 0 || it.def.faction.ordinal == heroFilter) }
            .sortedWith(compareByDescending<HeroState> { it.owned }.thenByDescending { it.def.rarity.ordinal }.thenBy { it.id })
        for (pair in filtered.chunked(2)) {
            val line = LinearLayout(context)
            for (h in pair) {
                val box = column().apply {
                    setPadding(dp(8), dp(8), dp(8), dp(8)); background = bg(if (h.owned) 0xFFF0E4CB.toInt() else 0xFFC6CFC5.toInt())
                }
                box.addView(portrait("hero", h.id, 116).apply {
                    background = HamletUi.panel(context, intArrayOf(0xFFD9E7D2.toInt(), 0xFFEAE1C4.toInt(), 0xFFE4D4C8.toInt())[h.def.faction.ordinal], 12)
                    contentDescription = heroName(h.id)
                })
                text(box, heroName(h.id), true)
                text(box, s("hero_card", s("faction_${h.def.faction.ordinal}"), s("role_${h.def.role.name.lowercase()}"), s("rarity_${h.def.rarity.ordinal}")))
                text(box, if (h.owned) s("hero_growth", a.effectiveLevel(h.id), h.stars, h.shards) else recruitmentRequirement(h))
                if (!h.owned) text(box, s("shards_progress", h.shards, a.shardUnlockCost(h.id)))
                box.addView(button(s("details")) { showHero(h.id) })
                line.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(8), dp(3), 0) })
            }
            if (pair.size == 1) line.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            rows.addView(line)
        }
    }
    private fun recruitmentRequirement(h: HeroState): String = if (h.id < 12 && h.def.rescue > 0 && h.def.rarity.ordinal <= HeroRarity.RARE.ordinal)
        s("rescue_at", h.def.rescue)
    else raw("hamlet_hero_target_requirement", a.recruitFrontier(h.id), a.recruitChapter(h.id))

    private fun summon(count: Int) {
        val results = a.summon(count)
        if (results.isEmpty()) { toast(s("not_ready")); return }
        render()
        val content = column().apply { setPadding(dp(12), dp(8), dp(12), dp(10)); setBackgroundColor(HamletUi.PAPER) }
        for (result in results) {
            val h = a.heroes[result.hero]
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(portrait("hero", h.id, 76), LinearLayout.LayoutParams(dp(76), dp(76)))
            val detail = column()
            text(detail, if (result.newHero) s("recruit_new", heroName(h.id), s("rarity_${h.def.rarity.ordinal}")) else s("recruit_duplicate", heroName(h.id), s("rarity_${h.def.rarity.ordinal}"), result.shards), true)
            if (result.guaranteed) text(detail, s("recruit_pity"))
            row.addView(detail, LinearLayout.LayoutParams(0, -2, 1f)); content.addView(row)
        }
        AlertDialog.Builder(context).setTitle(s("summon_results")).setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(android.R.string.ok, null).show()
    }
    private fun showHero(hero: Int) {
        val h = a.heroes[hero]
        val content = column().apply { setPadding(dp(14), dp(10), dp(14), dp(10)); setBackgroundColor(0xFFF3E8D1.toInt()) }
        content.addView(portrait("hero", hero, 142))
        text(content, s("hero_story_$hero"))
        text(content, s("hero_growth", a.effectiveLevel(hero), h.stars, h.shards), true)
        if (h.owned && a.effectiveLevel(hero) > h.level) text(content, raw("hamlet_progression_training_hero", h.level, a.effectiveLevel(hero)))
        val n = a.numbers(hero)
        text(content, s("hero_numbers", n.hp.toLong(), n.attack.toLong(), n.armor.toInt(), (n.crit * 100).toInt()))
        val sub = AlertDialog.Builder(context).setTitle(heroName(hero)).setView(ScrollView(context).apply { addView(content) }).setNegativeButton(android.R.string.cancel, null).create()
        fun refresh(operation: () -> Boolean) { if (operation()) { sub.dismiss(); render(); showHero(hero) } else toast(s("not_ready")) }
        if (!h.owned) {
            text(content, recruitmentRequirement(h))
            content.addView(button(raw("hamlet_hero_target_cost", a.recruitCost(hero)), a.canFocusRecruit(hero)) { refresh { a.focusRecruit(hero) } })
            content.addView(button(s("recruit_shards_cost", a.shardUnlockCost(hero)), a.canRecruitShards(hero)) { refresh { a.recruitShards(hero) } })
        }
        else {
            content.addView(button(raw("hamlet_armory_title")) { sub.dismiss(); HamletArmoryDialog(context, game, ::render).show(1, hero) })
            content.addView(button(raw("hamlet_shop_ui_entry")) { sub.dismiss(); HamletShardShopDialog(context, game, ::render).show(hero) })
            text(content, raw("hamlet_hero_personal_fragments", heroName(hero), h.shards))
            content.addView(button(raw("hamlet_hero_duplicate_cost", a.recruitCost(hero), HamletAdventure.DUPLICATE_SHARDS, heroName(hero)), a.canFocusRecruit(hero)) { refresh { a.focusRecruit(hero) } })
            content.addView(button(raw(if (a.shardFocusHero == hero) "hamlet_hero_focus_clear" else "hamlet_hero_focus", heroName(hero))) { refresh { a.selectShardFocus(if (a.shardFocusHero == hero) -1 else hero) } })
            text(content, raw("hamlet_hero_focus_help"))
            content.addView(button(s("level_cost", a.levelCost(hero)), game.sandbox || a[AdventureResource.FOOD] >= a.levelCost(hero)) { refresh { a.levelUp(hero) } })
            if (h.stars < HamletHeroes.STAR_CAP) {
                text(content, raw("hamlet_hero_star_preview", h.stars + 1, a.starGainPercent(hero)))
                content.addView(button(s("stars_cost", a.starCost(hero)), a.canPromote(hero)) { refresh { a.promote(hero) } })
            } else text(content, raw("hamlet_hero_stars_max", HamletHeroes.STAR_CAP))
            text(content, s("hero_skills"), true)
            for (skill in 0..3) content.addView(button(s("skill_cost", s("skill_${h.def.role.name.lowercase()}_$skill"), h.skills[skill], a.skillCost(hero, skill)), game.sandbox || a[AdventureResource.BOOKS] >= a.skillCost(hero, skill)) { refresh { a.studySkill(hero, skill) } })
            text(content, s("skills_help"))
            text(content, s("traits_title"), true)
            for (trait in 0..3) content.addView(button(s("trait_cost", s("trait_$trait"), h.traits[trait], 12L * (h.traits[trait] + 1)), h.stars >= 5 && (game.sandbox || h.shards >= 12L * (h.traits[trait] + 1))) { refresh { a.promoteTrait(hero, trait) } })
        }
        sub.show()
    }
    private fun teams() {
        val presets = LinearLayout(context)
        for (i in 0..2) presets.addView(button(s("team_preset", i + 1)) { a.selectTeam(i); render() }.apply {
            isSelected = i == a.selectedTeam; background = HamletUi.control(context, selected = isSelected)
            setTextColor(if (isSelected) HamletUi.INK else HamletUi.PAPER)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        rows.addView(presets)
        val box = card(s("team_title", a.selectedTeam + 1, a.power()))
        text(box, s("formation_help"))
        text(box, raw("hamlet_progression_training", a.trainingLevel()))
        action(box, s("auto_formation")) { a.autoFormation() }
        val tactics = LinearLayout(context)
        for (tactic in BattleTactic.entries) tactics.addView(button(raw("hamlet_combat_tactic_${tactic.name.lowercase()}")) { a.selectTactic(tactic); render() }.apply {
            isSelected = a.tactics[a.selectedTeam] == tactic; background = HamletUi.control(context, selected = isSelected)
            setTextColor(if (isSelected) HamletUi.INK else HamletUi.PAPER)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(2) })
        box.addView(tactics); text(box, raw("hamlet_combat_tactic_${a.tactics[a.selectedTeam].name.lowercase()}_desc"))
        for (range in listOf(0..1, 2..5)) {
            val line = LinearLayout(context)
            for (slot in range) {
                val hero = a.teams[a.selectedTeam][slot]
                val cell = column().apply { setPadding(dp(2), dp(3), dp(2), dp(3)) }
                if (hero >= 0) cell.addView(portrait("hero", hero, if (slot < 2) 105 else 80))
                val unlocked = slot < a.slotCount()
                cell.addView(button(if (!unlocked) s("slot_locked", if (slot == 4) 10 else 24) else if (hero < 0) s("empty_slot") else heroName(hero), unlocked) { chooseHero(slot) })
                line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
            box.addView(line)
        }
        for (f in HeroFaction.entries) text(box, s("faction_bonus", s("faction_${f.ordinal}"), (a.factionBonus(f) * 100).toInt()))
        text(box, s("counter_help"))
        val bonds = card(s("bonds"))
        for ((index, members) in HamletHeroes.bonds.withIndex()) text(bonds, s("bond_row", s("bond_$index"), members.joinToString(raw("hamlet_list_separator")) { heroName(it) }, if (index in a.activeBonds()) s("active") else s("inactive")))
        val support = card(s("support"))
        for (pet in listOf(true, false)) {
            val values = if (pet) a.pets else a.relics
            for (i in values.indices) {
                if (pet) support.addView(portrait("enemy", intArrayOf(0, 5, 7)[i], 70))
                val name = s(if (pet) "pet_$i" else "relic_$i")
                val equipped = (if (pet) a.teamPets else a.teamRelics)[a.selectedTeam] == i
                text(support, s("support_row", name, values[i]) + if (equipped) " · " + s("active") else "")
                text(support, s(if (pet) "pet_story_$i" else "relic_story_$i"))
                val line = LinearLayout(context)
                line.addView(button(s("equip"), values[i] > 0) { a.selectSupport(pet, i); render() }, LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(button(s("support_upgrade", (if (pet) 50L else 12L) * (values[i] + 1), s(if (pet) "resource_food" else "resource_crystal")), values[i] > 0) { if (a.upgradeSupport(pet, i)) render() else toast(s("not_ready")) }, LinearLayout.LayoutParams(0, -2, 1f))
                support.addView(line)
            }
        }
    }
    private fun chooseHero(slot: Int) {
        val owned = a.heroes.filter { it.owned }
        AlertDialog.Builder(context).setTitle(s("choose_hero", slot + 1))
            .setItems((owned.map { s("hero_choice", heroName(it.id), s("role_${it.def.role.name.lowercase()}"), a.effectiveLevel(it.id)) } + s("remove")).toTypedArray()) { _, which ->
                if (which == owned.size) a.remove(slot) else a.assign(owned[which].id, slot); render()
            }.show()
    }
    private fun craft() {
        val box = card(s("forge_title"))
        text(box, raw("hamlet_armory_help"))
        text(box, raw("hamlet_armory_inventory", game.armory.inventory.size, game.armory.inventory.count { game.armory.owner(it.id) != null }))
        text(box, raw("hamlet_armory_forge_rank", game.armory.forgeRank()))
        if (a.facility("adventureForge") == 0) box.addView(button(s("build_named", item("adventureForge"))) { build("adventureForge") })
        box.addView(button(raw("hamlet_armory_tab_0")) { HamletArmoryDialog(context, game, ::render).show() })
        box.addView(button(raw("hamlet_armory_tab_2")) { HamletArmoryDialog(context, game, ::render).show(2) })
        for (hero in a.teams[a.selectedTeam].take(a.slotCount()).filter { it >= 0 }) {
            val worn = card(heroName(hero))
            for (slot in 0..3) text(worn, raw("hamlet_progression_worn", s("gear_$slot"), game.armory.equipped(hero, slot)?.let {
                raw("hamlet_armory_piece", s("gear_$slot"), raw("hamlet_armory_set_${it.set.name.lowercase()}"), raw("hamlet_armory_rarity_${it.rarity.ordinal}"), it.level)
            } ?: raw("hamlet_armory_slot_empty")))
            game.armory.setCounts(hero).forEach { (set, count) -> text(worn, raw("hamlet_armory_set_count", raw("hamlet_armory_set_${set.name.lowercase()}"), count)) }
            worn.addView(button(raw("hamlet_armory_tab_1")) { HamletArmoryDialog(context, game, ::render).show(1, hero) })
        }
        box.addView(button(raw("hamlet_progression_legacy_gear")) { showLegacyGear = !showLegacyGear; render() })
        if (!showLegacyGear) return
        text(rows, s("forge_help"), dark = false)
        for (hero in a.teams[a.selectedTeam].filter { it >= 0 }) {
            val h = a.heroes[hero]; val row = card(heroName(hero)); row.addView(portrait("hero", hero, 75))
            for (slot in 0..3) {
                text(row, s("gear_row", s("gear_$slot"), raw("hamlet_armory_rarity_${h.gearTier[slot]}"), h.gear[slot]))
                val buttons = LinearLayout(context)
                buttons.addView(button(s("forge_cost", a.forgeCost(hero, slot), h.gear[slot]), a.facility("adventureForge") > 0 || game.sandbox) { if (a.forge(hero, slot)) render() else toast(s("not_ready")) }, LinearLayout.LayoutParams(0, -2, 1f))
                buttons.addView(button(s("refine_cost", (h.gearTier[slot] + 1) * 5, (h.gearTier[slot] + 1) * 10), h.gearTier[slot] < 3) { if (a.refine(hero, slot)) render() else toast(s("not_ready")) }, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(buttons)
            }
            text(row, s("mastery", h.gear.minOrNull() ?: 0, raw("hamlet_armory_rarity_${h.gearTier.minOrNull() ?: 0}")))
        }
    }
    private fun kingdom() {
        val duties = card(context.getString(R.string.hamlet_s_posts))
        text(duties, context.getString(R.string.hamlet_s_posts_help))
        duties.addView(button(context.getString(R.string.hamlet_s_assign)) { stewardship(1) })
        val facilities = card(s("facilities"))
        text(facilities, s("facilities_help"))
        for ((i, id) in HamletAdventure.FACILITIES.withIndex()) {
            val facility = card(item(id))
            val showcase = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            showcase.addView(portrait("building", i, 116).apply { contentDescription = item(id) }, LinearLayout.LayoutParams(dp(108), dp(116)))
            val description = column()
            text(description, s("facility_$i")); text(description, s("facility_level", a.facility(id)))
            showcase.addView(description, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
            facility.addView(showcase)
            val best = game.objs.values.filter { it.t == id && game.buildLeft(it) == 0L }.maxByOrNull { it.tier }
            if (best != null && game.canUpgrade(best)) action(facility, raw("hamlet_progression_upgrade_building", best.tier + 1, game.upgradeCost(best)), game.canUpgrade(best) && (game.sandbox || game.coins >= game.upgradeCost(best))) { game.upgrade(best) }
            facility.addView(button(s("build_cost", Hamlet.byId.getValue(id).cost, Hamlet.byId.getValue(id).lvl)) { build(id) })
        }
        val workers = card(s("workers")); text(workers, s("workers_help", a.workers.sum(), a.workerCapacity()))
        a.productionPerMinute().forEachIndexed { i, rate -> text(workers, raw("hamlet_progression_rate", s("resource_${AdventureResource.entries[i].name.lowercase()}"), rate * 60)) }
        for (i in a.workers.indices) {
            text(workers, s("worker_row", s("worker_$i"), a.workers[i]))
            val buttons = LinearLayout(context)
            buttons.addView(button(s("worker_minus")) { a.assignWorker(i, -1); render() }, LinearLayout.LayoutParams(0, -2, 1f))
            buttons.addView(button(s("worker_plus")) { if (!a.assignWorker(i, 1)) toast(s("not_ready")); render() }, LinearLayout.LayoutParams(0, -2, 1f)); workers.addView(buttons)
        }
        val tech = card(s("academy")); text(tech, s("academy_help"))
        for (i in a.tech.indices) action(tech, s("tech_cost", s("tech_$i"), a.tech[i], a.researchCost(i)), a.facility("adventureAcademy") > 0 || game.sandbox) { a.research(i) }
        val troops = card(s("troops")); text(troops, s("troops_help", a.marchCapacity()))
        text(troops, raw("hamlet_progression_militia_cap", a.maxTroopTier()))
        for (i in 0..2) {
            action(troops, s("troop_row", s("faction_$i"), a.troops[i]), a.facility("adventureYard") > 0 || game.sandbox) { a.trainTroops(i) }
            action(troops, s("troop_upgrade", a.troopTier[i], 20L * a.troopTier[i], 15L * a.troopTier[i]), (a.facility("adventureYard") > 0 || game.sandbox) && a.troopTier[i] < a.maxTroopTier()) { a.improveTroops(i) }
        }
        val builders = card(s("builders")); text(builders, s("builders_help", game.workSeconds))
        action(builders, s("builders_cost")) { a.supplyBuilders() }
        val workshop = card(raw("hamlet_progression_supply_workshop"))
        text(workshop, raw("hamlet_progression_supply_help"))
        action(workshop, raw("hamlet_progression_supply_ore"), game.sandbox || a.facility("adventureStore") > 0) { a.workshopSupplies(false) }
        action(workshop, raw("hamlet_progression_supply_books"), game.sandbox || a.facility("adventureAcademy") > 0) { a.workshopSupplies(true) }
    }
    private fun stewardship(initialTab: Int = 0) {
        HamletStewardshipDialog(context, game, onAdventure = { destination -> tab = destination; render() },
            onJournal = { destination -> HamletJourneyDialog(context, game, haptics).show(destination) }).show(initialTab)
    }
    private fun build(id: String) {
        val d = Hamlet.byId.getValue(id)
        if (!game.journey.unlocked(d)) { toast(context.getString(R.string.hamlet_unlock_level, d.lvl)); return }
        dialog.dismiss(); onBuild(d)
    }
    private fun challenges() {
        val unlocks = mapOf(BattleMode.TRIAL to 10, BattleMode.BOUNTY to 5, BattleMode.RAID to 20, BattleMode.ARENA to 12)
        for ((mode, gate) in unlocks) {
            val stage = when (mode) { BattleMode.TRIAL -> a.trial; BattleMode.BOUNTY -> a.bounty; BattleMode.RAID -> a.raid; else -> a.arena }
            val box = card(s("mode_${mode.name.lowercase()}"))
            text(box, s("mode_story_${mode.name.lowercase()}")); text(box, s("challenge_stage", stage + 1, gate))
            box.addView(button(s("battle"), a.frontier >= gate) { battle(mode, stage) })
            if (stage > 0) box.addView(button(s("farm_previous")) { battle(mode, stage - 1) })
        }
    }
    private fun battle(mode: BattleMode, stage: Int) {
        val before = a.bag.clone(); val coinsBefore = game.coins; val workBefore = game.workSeconds
        val battle = a.beginBattle(mode, stage) ?: run { toast(s("not_ready")); return }
        rows.removeAllViews(); scroll.scrollTo(0, 0)
        text(rows, s("combat_header", s("mode_${mode.name.lowercase()}"), stage + 1), true, false)
        val view = HamletBattleView(context, battle, assets, { key, args -> s(key, *args) }, haptics) { result ->
            if (a.settle(result)) {
                if (haptics) root.performHapticFeedback(if (result.outcome == BattleOutcome.VICTORY) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
                rows.removeAllViews()
                val box = card(s(if (result.outcome == BattleOutcome.VICTORY) "victory" else "defeat"))
                text(box, s("battle_result", result.time.toInt(), result.damage.toLong(), result.healing.toLong(), result.survivors()))
                text(box, raw("hamlet_combat_report_title"), true)
                for (report in result.report()) {
                    text(box, heroName(report.sprite), true)
                    text(box, raw("hamlet_combat_report_row", report.damage.toLong(), report.healing.toLong(), report.protection.toLong(), report.kills))
                }
                text(box, s(if (result.outcome == BattleOutcome.VICTORY) "victory_help" else "defeat_help"))
                if (result.outcome == BattleOutcome.VICTORY) {
                    text(box, s("battle_loot", a.bag[0] - before[0], a.bag[1] - before[1], a.bag[2] - before[2], a.bag[3] - before[3],
                        a.bag[4] - before[4], a.bag[5] - before[5], a.bag[6] - before[6], a.bag[7] - before[7], game.coins - coinsBefore, (game.workSeconds - workBefore).coerceAtLeast(0)))
                    box.addView(button(s("next_stage")) {
                        val next = when (mode) { BattleMode.CAMPAIGN -> a.frontier; BattleMode.TRIAL -> a.trial; BattleMode.BOUNTY -> a.bounty; BattleMode.RAID -> a.raid; BattleMode.ARENA -> a.arena }
                        selected = a.frontier; page = selected / 12
                        if (mode == BattleMode.CAMPAIGN && a.node(next) in listOf(AdventureNode.GATHER, AdventureNode.SHRINE)) gather(next) else this.battle(mode, next)
                    })
                }
                box.addView(button(s("continue")) { selected = a.frontier; page = selected / 12; render() })
                box.addView(button(s("prepare")) { tab = 2; render() })
                if (result.outcome == BattleOutcome.DEFEAT) {
                    text(box, raw(if (result.survivors() > 0) "hamlet_progression_defeat_timeout" else "hamlet_progression_defeat_survival"))
                    box.addView(button(raw("hamlet_armory_title")) { tab = 3; render() })
                    box.addView(button(s("heroes")) { tab = 1; render() })
                    box.addView(button(s("retry_free")) { this.battle(mode, stage) })
                }
                refreshStock()
            }
        }
        view.effectsEnabled = effectsEnabled
        view.contentDescription = s("combat_accessibility")
        view.effectsEnabled = effectsEnabled
        rows.addView(view, LinearLayout.LayoutParams(-1, maxOf(dp(280), context.resources.displayMetrics.heightPixels * 48 / 100)))
        val controls = LinearLayout(context)
        val auto = button(s(if (battle.auto) "auto_on" else "auto_off")) { battle.auto = !battle.auto; a.auto = battle.auto; game.listener?.onChanged() }
        auto.setOnClickListener { battle.auto = !battle.auto; a.auto = battle.auto; auto.text = s(if (battle.auto) "auto_on" else "auto_off"); game.listener?.onChanged() }
        controls.addView(auto, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button(s("speed", 1)).apply { setOnClickListener { view.speed = if (view.speed == 4) 1 else view.speed * 2; text = s("speed", view.speed) } }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button(s("pause")).apply { setOnClickListener { view.paused = !view.paused; text = s(if (view.paused) "resume" else "pause") } }, LinearLayout.LayoutParams(0, -2, 1f))
        rows.addView(controls)
        text(rows, s("manual_help"), dark = false)
        rows.addView(button(s("cancel_free")) { a.abandonBattle(); render() })
    }
}
