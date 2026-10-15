package com.Atom2Universe.app.games.hamlet

import android.content.Context
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.hamlet.HamletUi.Weight

/**
 * Le module Journal : la journée (collectes, tâches, fête), les chapitres, les expéditions, les
 * légendes, les recherches, les coups de main et les métiers des héros. Une page à onglets,
 * les explications derrière un « ? ».
 */
internal class HamletJournalPage(private val context: Context, private val g: HamletGame, private val nav: HamletNav,
                                 private val onQuest: () -> Unit) : HamletPage {
    companion object {
        const val TODAY = 0
        const val CHAPTERS = 1
        const val EXPEDITIONS = 2
        const val LEGENDS = 3
        const val RESEARCH = 4
        const val WORK = 5
        const val POSTS = 6
        private val LABELS = intArrayOf(R.string.hamlet_s_today, R.string.hamlet_chapters, R.string.hamlet_expeditions, R.string.hamlet_legends,
            R.string.hamlet_research, R.string.hamlet_work, R.string.hamlet_s_posts)
        private val GLYPHS = arrayOf("sun", "scroll", "ship", "star", "book", "hammer", "hat")
    }
    private val j get() = g.journey
    private val state get() = g.stewardship
    private val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    override val view: View get() = root
    private val tabs = LinearLayout(context)
    private val tabScroll = HorizontalScrollView(context)
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context)
    private var tab = TODAY
    private var countdown: TextView? = null
    private var readiness: TextView? = null
    private var collectAction: Button? = null
    private var expeditionAction: Button? = null
    private var shown: List<Any>? = null
    private val vigils = HashMap<Int, TextView>()
    private fun dp(v: Int) = HamletUi.dp(context, v)
    private fun s(id: Int, vararg args: Any) = if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    private fun byName(name: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return context.getString(if (id == 0) R.string.hamlet_title else id)
    }
    private fun name(type: String, index: Int) = byName("hamlet_${type}_$index")
    private fun itemName(id: String) = byName(if (id == "hall") "hamlet_n_hall${g.hall}" else "hamlet_n_$id")
    private fun heroName(hero: Int) = byName("hamlet_a_hero_$hero")
    private fun duration(ms: Long): String {
        if (ms < 60_000) return s(R.string.hamlet_duration_seconds, (ms.coerceAtLeast(0) + 999) / 1000)
        val m = (ms.coerceAtLeast(0) + 59_999) / 60_000
        return if (m >= 60) s(R.string.hamlet_duration_hours, m / 60, m % 60) else s(R.string.hamlet_duration_minutes, m)
    }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun line() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun text(parent: LinearLayout, value: CharSequence, title: Boolean = false, small: Boolean = false, color: Int = 0): TextView = TextView(context).also {
        it.text = value; it.textSize = if (title) HamletUi.TITLE else if (small) HamletUi.SMALL else HamletUi.TEXT
        it.setTextColor(if (color != 0) color else if (small) HamletUi.MUTED else HamletUi.INK)
        it.setPadding(dp(3), dp(4), dp(3), dp(4)); it.setLineSpacing(dp(2).toFloat(), 1f)
        if (title) it.setTypeface(null, Typeface.BOLD)
        parent.addView(it)
    }
    private fun button(parent: LinearLayout, label: CharSequence, enabled: Boolean = true, weight: Weight = Weight.NORMAL, glyph: String? = null,
                       click: () -> Unit): Button = HamletUi.button(context, label, weight, glyph = glyph) {
        if (nav.haptics) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        click()
    }.also { it.isEnabled = enabled; parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }) }
    /** An action that redraws the page when it worked; an impossible one does nothing. */
    private fun action(parent: LinearLayout, label: CharSequence, enabled: Boolean = true, weight: Weight = Weight.NORMAL, glyph: String? = null, perform: () -> Boolean) =
        button(parent, label, enabled, weight, glyph) { if (perform()) { nav.changed(); render() } }
    /** A parchment card; [help] adds a "?" beside its title, [done] greens it. */
    private fun card(title: CharSequence, help: CharSequence? = null, glyph: String? = null, done: Boolean = false): LinearLayout = column().also {
        it.background = HamletUi.card(context, if (done) 0xFFE3ECD3.toInt() else HamletUi.PAPER)
        it.setPadding(dp(13), dp(8), dp(13), dp(12)); it.elevation = dp(2).toFloat()
        rows.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val head = line()
        head.addView(TextView(context).apply {
            text = title; textSize = HamletUi.TITLE; setTextColor(HamletUi.INK); setTypeface(null, Typeface.BOLD); setPadding(dp(3), dp(4), dp(3), dp(4))
            if (glyph != null) { setCompoundDrawablesRelativeWithIntrinsicBounds(HamletUi.icon(context, glyph, 22), null, null, null); compoundDrawablePadding = dp(7) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (done) head.addView(ImageView(context).apply { setImageDrawable(HamletUi.icon(context, "tick", 22)) }, LinearLayout.LayoutParams(dp(30), dp(30)))
        if (help != null) head.addView(HamletUi.info(context, title, help), LinearLayout.LayoutParams(dp(44), -2))
        it.addView(head)
    }
    /** A title on the dark background, with its "?". */
    private fun section(title: String, help: String?) {
        val head = line().apply { setPadding(0, dp(12), 0, 0) }
        head.addView(TextView(context).apply { text = title; textSize = HamletUi.TITLE; setTextColor(HamletUi.GOLD); setTypeface(null, Typeface.BOLD); setPadding(dp(3), 0, dp(3), 0) },
            LinearLayout.LayoutParams(0, -2, 1f))
        if (help != null) head.addView(HamletUi.info(context, title, help, onDark = true), LinearLayout.LayoutParams(dp(44), -2))
        rows.addView(head)
    }
    /** A bar with "done / target" beside it. */
    private fun progress(parent: LinearLayout, done: Long, target: Long, label: CharSequence? = null) {
        val row = line().apply { setPadding(dp(3), dp(2), dp(3), dp(2)) }
        if (label != null) row.addView(TextView(context).apply { text = label; textSize = HamletUi.SMALL; setTextColor(HamletUi.INK) }, LinearLayout.LayoutParams(0, -2, 1.2f).apply { marginEnd = dp(8) })
        row.addView(HamletBar(context).apply { set(done, target) }, LinearLayout.LayoutParams(0, dp(9), 1f))
        row.addView(TextView(context).apply {
            text = s(R.string.hamlet_ui_progress, done.coerceAtMost(target), target); textSize = HamletUi.SMALL
            setTextColor(if (done >= target) HamletUi.GOOD else HamletUi.MUTED); setTypeface(null, Typeface.BOLD); setPadding(dp(8), 0, 0, 0)
        })
        parent.addView(row)
    }

    init {
        tabs.setPadding(dp(8), dp(6), dp(8), dp(6))
        for (i in LABELS.indices) tabs.addView(HamletUi.button(context, s(LABELS[i]), glyph = GLYPHS[i]) {
            if (nav.haptics) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (i != tab) { tab = i; scroll.scrollTo(0, 0); render() }
        }.apply { maxLines = 1 }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(5) })
        tabScroll.apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        tabScroll.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(HamletUi.FOREST, 0xFF1A3631.toInt()))
        root.addView(tabScroll, LinearLayout.LayoutParams(-1, -2))
        rows.setPadding(dp(10), 0, dp(10), dp(16))
        scroll.addView(rows); scroll.isFillViewport = true
        scroll.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF1A3631.toInt(), HamletUi.DEEP))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    override fun show(page: Int) {
        if (page >= 0 && page != tab) { tab = page.coerceIn(TODAY, POSTS); scroll.scrollTo(0, 0) }
        g.tick(); render(preserve = page < 0)
    }
    override fun hide() = Unit
    override fun tick() {
        supplies(); tabDots()
        for ((legend, label) in vigils) label.text = s(R.string.hamlet_legend_burning, duration(g.legends.timeLeft(legend)))
        countdown?.text = s(R.string.hamlet_expedition_running, name("region", j.expedition), duration(j.expeditionLeft()))
        readiness?.text = s(R.string.hamlet_s_ready, g.readyCount(), g.adventure.heroes.count { it.owned }, g.adventure.heroes.size)
        collectAction?.isEnabled = g.canCollectAll() && g.collectableCount() > 0
        expeditionAction?.apply {
            val back = j.expedition >= 0 && j.expeditionLeft() == 0L
            if (back != isEnabled) render()
            else if (!back) text = s(R.string.hamlet_s_trip_wait, duration(j.expeditionLeft()))
        }
        if (snapshot() != shown) render()
    }
    /** What may change on its own while the page is open: claimable tasks and chapters. */
    private fun snapshot(): List<Any> = listOf(tab, state.claimed, (0..2).map { state.canClaim(it) }, state.canClaimChest(), j.chapter, j.canClaim(), j.expedition, HamletLegends.defs.indices.map { g.legends.canFinish(it) })
    /** Les pastilles des onglets : ce qui attend, ou vient de se terminer. */
    private fun tabDots() {
        val waiting = booleanArrayOf(
            state.hasRewards(),
            g.currentQuest()?.let { g.questDone(it) } == true || j.canClaim(),
            j.expedition >= 0 && j.expeditionLeft() == 0L,
            g.legends.attention())
        for (i in 0 until tabs.childCount) HamletUi.waiting(tabs.getChildAt(i) as Button, waiting.getOrElse(i) { false })
    }
    private fun supplies() {
        nav.supplies(listOf(
            HamletChip("crate", j.supplies.toLong(), s(R.string.hamlet_ui_number, j.supplies.toLong()), s(R.string.hamlet_ui_supplies, j.supplies.toLong())),
            HamletChip("hourglass", g.workSeconds.toLong(), duration(g.workSeconds * 1000L), s(R.string.hamlet_ui_work_bank, duration(g.workSeconds * 1000L))),
        ))
    }
    private fun render(preserve: Boolean = true) {
        val y = if (preserve) scroll.scrollY else 0
        state.ensureDay(); rows.removeAllViews(); vigils.clear(); countdown = null; readiness = null; collectAction = null; expeditionAction = null
        for (i in 0 until tabs.childCount) HamletUi.choice(tabs.getChildAt(i) as Button, i == tab)
        tabScroll.post { tabScroll.smoothScrollTo((tabs.getChildAt(tab).left - dp(40)).coerceAtLeast(0), 0) }
        supplies(); tabDots()
        when (tab) { TODAY -> today(); CHAPTERS -> chapters(); EXPEDITIONS -> expeditions(); LEGENDS -> legends(); RESEARCH -> research(); WORK -> workshop(); else -> posts() }
        shown = snapshot()
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun today() {
        val overview = card(s(R.string.hamlet_s_return), s(R.string.hamlet_s_cycle), "house")
        readiness = text(overview, s(R.string.hamlet_s_ready, g.readyCount(), g.adventure.heroes.count { it.owned }, g.adventure.heroes.size))
        if (g.canCollectAll()) collectAction = button(overview, s(R.string.hamlet_ui_collect), g.collectableCount() > 0, Weight.MAIN, "sack") {
            g.collectAll(); render()
        } else text(overview, s(R.string.hamlet_collect_direct_hint, HamletGame.COLLECT_ALL_CHAPTER), small = true)
        if (j.expedition >= 0) {
            val back = j.expeditionLeft() == 0L
            expeditionAction = button(overview, if (back) s(R.string.hamlet_expedition_collect) else s(R.string.hamlet_s_trip_wait, duration(j.expeditionLeft())),
                back, if (back) Weight.MAIN else Weight.NORMAL, "ship") { if (j.collectExpedition()) { nav.changed(); render() } }
        } else button(overview, s(R.string.hamlet_s_plan_trip), glyph = "ship") { tab = EXPEDITIONS; scroll.scrollTo(0, 0); render() }
        section(s(R.string.hamlet_daily_tasks), s(R.string.hamlet_s_daily_help, duration(Hamlet.DAY_MS - g.now() % Hamlet.DAY_MS)))
        val labels = intArrayOf(R.string.hamlet_s_task_village, R.string.hamlet_s_task_adventure, R.string.hamlet_s_task_growth)
        val help = intArrayOf(R.string.hamlet_s_task_village_help, R.string.hamlet_s_task_adventure_help, R.string.hamlet_s_task_growth_help)
        val glyphs = arrayOf("house", "sword", "star")
        for (i in labels.indices) {
            val claimed = state.claimed and (1 shl i) != 0
            val box = card(s(labels[i]), s(help[i]), glyphs[i], claimed)
            progress(box, state.progress(i), HamletStewardship.TARGETS[i].toLong())
            val reward = context.resources.getQuantityString(R.plurals.hamlet_ui_tickets, HamletStewardship.TICKETS[i].toInt(), HamletStewardship.TICKETS[i])
            text(box, HamletUi.tint(s(R.string.hamlet_ui_reward_line, reward), reward to 0xFFB27A12.toInt()), small = true)
            when {
                claimed -> Unit
                state.canClaim(i) -> action(box, s(R.string.hamlet_claim), weight = Weight.MAIN, glyph = "ticket") { state.claim(i) }
                else -> button(box, s(R.string.hamlet_s_go), weight = Weight.QUIET, glyph = glyphs[i]) {
                    when (i) {
                        0 -> nav.open(HamletModule.VILLAGE)
                        1 -> nav.open(HamletModule.ADVENTURE, HamletAdventurePage.EXPLORE)
                        else -> nav.open(HamletModule.HEROES, HamletAdventurePage.HEROES)
                    }
                }
            }
        }
        val chest = card(s(R.string.hamlet_s_chest), s(R.string.hamlet_s_chest_help), "chest", state.chestClaimed)
        progress(chest, (0..2).count { state.claimed and (1 shl it) != 0 }.toLong(), 3L)
        if (!state.chestClaimed) action(chest, s(R.string.hamlet_claim), state.canClaimChest(), Weight.MAIN, "chest") { state.claimChest() }
    }
    private fun goalLabel(goal: HamletJourney.Goal): String = when {
        goal.key.startsWith("built_") -> s(R.string.hamlet_goal_build, itemName(goal.key.removePrefix("built_")))
        goal.key.startsWith("region_") -> s(R.string.hamlet_goal_explore, name("region", goal.key.removePrefix("region_").toInt()))
        else -> byName("hamlet_goal_${goal.key}")
    }
    private fun chapters() {
        section(s(R.string.hamlet_chapters), s(R.string.hamlet_chapter_help))
        if (g.currentQuest() != null) button(rows, s(R.string.hamlet_quest), glyph = "star") { onQuest() }
        // La prochaine étape apparaît en premier ; les suivantes montrent ce qui reste à découvrir.
        val order = (j.chapter until HamletJourney.chapters.size).toList() + (0 until j.chapter).toList()
        for (i in order) {
            val c = HamletJourney.chapters[i]
            val done = i < j.chapter
            val box = card(s(R.string.hamlet_chapter_title, i + 1, name("chapter", i)), byName("hamlet_chapter_story_$i"), "scroll", done)
            val unlocks = HamletJourney.buildings.filterValues { it == i + 1 }.keys.map { itemName(it) }.toMutableList()
            HamletJourney.regions.forEachIndexed { r, def -> if (def.chapter == i + 1) unlocks.add(name("region", r)) }
            if (i % 2 == 1) unlocks.add(s(R.string.hamlet_chapter_call))
            if (i + 1 == HamletGame.COLLECT_ALL_CHAPTER) unlocks.add(s(R.string.hamlet_ui_collect))
            text(box, s(R.string.hamlet_chapter_unlocks, unlocks.joinToString(s(R.string.hamlet_list_separator))), small = true)
            if (done) continue
            text(box, s(R.string.hamlet_chapter_requirements, c.level, c.hall), small = true, color = if (i == j.chapter) 0 else HamletUi.MUTED)
            if (i != j.chapter) continue
            for (goal in c.goals) progress(box, j.progress(goal), goal.target.toLong(), goalLabel(goal))
            val reward = s(R.string.hamlet_ui_reward_chapter, c.coins, c.supplies)
            text(box, HamletUi.tint(s(R.string.hamlet_ui_reward_line, reward), reward to 0xFFB27A12.toInt()), small = true)
            action(box, s(R.string.hamlet_claim), j.canClaim(), Weight.MAIN, "gift") { j.claim() }
        }
    }
    private fun expeditions() {
        section(s(R.string.hamlet_expeditions), s(R.string.hamlet_expedition_help, j.power()) + "\n\n" +
            s(R.string.hamlet_s_expedition_link, j.scoutingPower(), j.scoutingSpeed()) + "\n\n" + s(R.string.hamlet_expedition_invitation_help))
        if (j.expedition >= 0) {
            val box = card(name("region", j.expedition), glyph = "ship")
            val back = j.expeditionLeft() == 0L
            if (back) action(box, s(R.string.hamlet_expedition_collect), weight = Weight.MAIN, glyph = "gift") { j.collectExpedition() }
            else {
                countdown = text(box, s(R.string.hamlet_expedition_running, name("region", j.expedition), duration(j.expeditionLeft())))
                action(box, s(R.string.hamlet_work_apply), g.workSeconds > 0, glyph = "hammer") { g.workOnExpedition() }
            }
        }
        for (i in HamletJourney.regions.indices) {
            val r = HamletJourney.regions[i]
            val box = card(name("region", i), byName("hamlet_region_story_$i"), "map", j.explored[i] > 0)
            text(box, s(R.string.hamlet_region_detail, r.chapter, r.power, duration(j.expeditionSeconds(i) * 1000L), r.coins, r.supplies, j.explored[i]), small = true)
            text(box, s(R.string.hamlet_s_expedition_loot, j.expeditionFood(i), j.expeditionOre(i), j.expeditionBooks(i), j.expeditionCrystal(i), j.expeditionTickets(i)), small = true)
            if (j.expedition < 0) action(box, s(R.string.hamlet_expedition_start), j.canExplore(i), Weight.MAIN, "ship") { j.startExpedition(i) }
        }
    }
    /** Les légendes : une rumeur, puis trois veillées au cercle de pierres. Aucune ne sort d'un appel. */
    private fun legends() {
        section(s(R.string.hamlet_legends), s(R.string.hamlet_legends_help))
        val legends = g.legends
        var any = false
        for (i in HamletLegends.defs.indices) {
            val def = HamletLegends.defs[i]
            if (legends.owned(i)) {
                any = true
                val box = card(heroName(def.hero), byName("hamlet_a_hero_story_${def.hero}"), "star", done = true)
                text(box, s(R.string.hamlet_legend_done), small = true, color = HamletUi.GOOD)
                continue
            }
            if (!legends.whispered(i)) continue
            any = true
            val box = card(byName("hamlet_legend_rumor_$i"), byName("hamlet_legend_rumor_story_$i"), "star")
            box.addView(Silhouette(context, def.hero), LinearLayout.LayoutParams(-1, dp(110)))
            if (!legends.opened(i)) {
                text(box, s(if (def.requires.isEmpty()) R.string.hamlet_legend_soon else R.string.hamlet_legend_gather), small = true)
                continue
            }
            val phase = legends.phase(i) ?: continue
            text(box, s(R.string.hamlet_legend_night, legends.nights[i] + 1, def.phases.size), true)
            if (legends.running(i)) {
                vigils[i] = text(box, s(R.string.hamlet_legend_burning, duration(legends.timeLeft(i))))
                if (legends.canFinish(i)) action(box, s(R.string.hamlet_legend_dawn), weight = Weight.MAIN, glyph = "star") { finishVigil(i) }
                else action(box, s(R.string.hamlet_work_apply), g.workSeconds > 0, glyph = "hammer") { legends.applyWork(i) }
                continue
            }
            need(box, itemName(HamletLegends.CIRCLE), legends.circleBuilt())
            need(box, s(R.string.hamlet_need_coins, phase.coins), g.sandbox || g.coins >= phase.coins)
            need(box, s(R.string.hamlet_need_crystal, phase.crystal), g.sandbox || g.adventure[AdventureResource.CRYSTAL] >= phase.crystal)
            for (n in phase.needs) need(box, s(R.string.hamlet_need_crop, itemName("c_${n.crop}"), g.stock(n.crop), n.units), g.sandbox || g.stock(n.crop) >= n.units)
            text(box, s(R.string.hamlet_legend_hours, phase.hours), small = true)
            action(box, s(R.string.hamlet_legend_light), legends.canBegin(i), Weight.MAIN, "star") { legends.begin(i) }
        }
        if (!any) text(rows, s(R.string.hamlet_legends_none), small = true, color = HamletUi.MUTED_LIGHT)
    }
    /** Une condition de la veillée : verte quand elle est remplie, rouge sinon. */
    private fun need(parent: LinearLayout, label: String, met: Boolean) {
        text(parent, label, small = true, color = if (met) HamletUi.GOOD else HamletUi.BAD)
    }
    /** La dernière nuit révèle le héros en plein écran ; les autres nuits redessinent simplement la page. */
    private fun finishVigil(legend: Int): Boolean {
        val step = g.legends.finish(legend) ?: return false
        val recruit = step.recruit
        if (recruit != null) {
            nav.changed(); render()
            HamletHeroReveal.sequence(context, listOf(recruit.hero to true), nav.effects, nav.haptics) { nav.changed(); render() }
            return false
        }
        return true
    }

    private fun research() {
        section(s(R.string.hamlet_research), s(R.string.hamlet_research_help))
        for (i in j.research.indices) {
            val box = card(name("research", i), byName("hamlet_research_story_$i"), "book", j.research[i] >= 5)
            for (rank in 0..4) {
                val done = rank < j.research[i]
                text(box, s(R.string.hamlet_research_row, s(if (done) R.string.hamlet_mark_done else R.string.hamlet_mark_future), name("tech", i * 5 + rank), 2 + rank * 2),
                    small = !done, color = if (done) HamletUi.GOOD else 0)
            }
            if (j.research[i] < 5) action(box, s(R.string.hamlet_research_cost, j.research[i] + 1, j.researchCoins(i), j.researchSupplies(i)), j.canResearch(i), glyph = "book") { j.study(i) }
        }
    }
    private fun workshop() {
        section(s(R.string.hamlet_work), s(R.string.hamlet_work_intro) + "\n\n" + s(R.string.hamlet_work_help) + "\n\n" + s(R.string.hamlet_industry_work))
        val box = card(s(R.string.hamlet_work), glyph = "hammer")
        g.beginWork()
        val scene = WorkScene(context) { g.workStep }
        box.addView(scene, LinearLayout.LayoutParams(-1, dp(110)))
        val prompt = text(box, byName("hamlet_work_task_${g.workTool}"), true).apply { gravity = Gravity.CENTER }
        val meter = HamletBar(context).apply { set(g.workStep.toLong(), 12L) }
        box.addView(meter, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(4); bottomMargin = dp(4) })
        val count = text(box, s(R.string.hamlet_work_progress, g.workStep), small = true)
        val tools = line()
        box.addView(tools)
        val glyphs = arrayOf("hammer", "brush", "wood")
        for (tool in 0..2) tools.addView(HamletUi.button(context, byName("hamlet_work_tool_$tool"), Weight.MAIN, glyph = glyphs[tool]) {
            if (g.answerWork(tool)) {
                if (nav.haptics) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                prompt.text = byName("hamlet_work_task_${g.workTool}")
                meter.set(g.workStep.toLong(), 12L)
                scene.invalidate()
                count.text = s(R.string.hamlet_work_progress, g.workStep)
                HamletMotion.pop(scene, 1.06f)
                if (g.workStep == 0) { HamletMotion.pop(prompt); nav.changed(); supplies() }
            } else HamletMotion.pop(prompt, .92f)
        }.apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        val construction = g.objs.values.filter { g.buildLeft(it) > 0 }
        if (construction.isNotEmpty() || j.expedition >= 0) section(s(R.string.hamlet_ui_help_targets), null)
        val targets = column().apply { background = HamletUi.card(context); setPadding(dp(10), dp(6), dp(10), dp(10)) }
        for (o in construction) action(targets, s(R.string.hamlet_work_target, itemName(o.t), duration(g.buildLeft(o))), g.workSeconds > 0, glyph = "house") { g.applyWork(o) }
        if (j.expedition >= 0) action(targets, s(R.string.hamlet_work_target, name("region", j.expedition), duration(j.expeditionLeft())), g.workSeconds > 0, glyph = "ship") { g.workOnExpedition() }
        if (targets.childCount > 0) rows.addView(targets, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    private fun posts() {
        section(s(R.string.hamlet_s_posts), s(R.string.hamlet_s_posts_help))
        val titles = intArrayOf(R.string.hamlet_s_post_0, R.string.hamlet_s_post_1, R.string.hamlet_s_post_2)
        val help = intArrayOf(R.string.hamlet_s_post_help_0, R.string.hamlet_s_post_help_1, R.string.hamlet_s_post_help_2)
        val glyphs = arrayOf("leaf", "anvil", "house")
        for (post in titles.indices) {
            val hero = state.posts[post]; val box = card(s(titles[post]), s(help[post]), glyphs[post])
            if (hero < 0) text(box, s(R.string.hamlet_s_unassigned), small = true)
            else {
                val line = s(R.string.hamlet_s_assignment, heroName(hero), state.bonus(post))
                text(box, HamletUi.tint(line, heroName(hero) to HamletUi.rarity(HamletHeroes.defs[hero].rarity)))
            }
            button(box, s(R.string.hamlet_s_assign), glyph = "hat") {
                val owned = g.adventure.heroes.filter { it.owned }.sortedByDescending { state.heroBonus(post, it.id) }
                val labels: List<CharSequence> = owned.map {
                    HamletUi.tint(s(R.string.hamlet_s_choice, heroName(it.id), state.heroBonus(post, it.id)), heroName(it.id) to HamletUi.rarity(it.def.rarity))
                } + s(R.string.hamlet_s_unassign)
                HamletSheet.pick(context, s(titles[post]), labels, owned.indexOfFirst { it.id == hero }, owned.map { "hat" } + "cross") { i ->
                    state.assign(post, if (i == owned.size) -1 else owned[i].id); nav.changed(); render()
                }
            }
        }
    }

    /** Le petit chantier se monte avec les gestes : socle, poteaux, murs, puis charpente. */
    private class WorkScene(a: Context, val step: () -> Int) : View(a) {
        override fun onDraw(c: Canvas) {
            c.save()
            val scale = height / 142f
            c.translate(width / 2f, height * 0.52f); c.scale(scale, scale)
            val p = Iso(c, 0f, 0f)
            p.top(-0.3f, -0.3f, 2.6f, 2.6f, 0f, 0xC6BA92)
            p.box(0.05f, 0.05f, 0f, 1.95f, 1.95f, 0.16f, 0xB8ADA0)
            val s = step()
            for (i in 0 until minOf(s + 1, 4)) {
                val x = if (i % 2 == 0) 0.1f else 1.7f
                val y = if (i < 2) 0.1f else 1.7f
                p.box(x, y, 0.16f, x + 0.16f, y + 0.16f, 1.4f, 0x9B7548)
            }
            if (s >= 4) p.box(0.1f, 0.1f, 0.16f, 1.85f, 0.25f, 1.3f, 0xE4D6B6)
            if (s >= 6) p.box(0.1f, 0.1f, 0.16f, 0.25f, 1.85f, 1.3f, 0xE4D6B6)
            if (s >= 8) {
                p.line(0.1f, 0.1f, 1.4f, 1f, 0.1f, 2f, 0x9B7548, 3f)
                p.line(1f, 0.1f, 2f, 1.85f, 0.1f, 1.4f, 0x9B7548, 3f)
                p.line(1f, 0.1f, 2f, 1f, 1.85f, 2f, 0x9B7548, 3f)
            }
            if (s >= 10) p.face(0x768F6A, 0.1f, 0.1f, 1.4f, 1f, 0.1f, 2f, 1f, 1.85f, 2f, 0.1f, 1.85f, 1.4f)
            HamletGlyphs.draw(c, "log", -64f, 24f, 26f)
            HamletGlyphs.draw(c, "crate", 65f, 25f, 24f)
            c.restore()
        }
    }

    /** Un héros qu'on ne connaît pas encore : sa silhouette noire sur un halo de braises, jamais ses couleurs. */
    private class Silhouette(a: Context, private val hero: Int) : View(a) {
        private val shade = Paint().apply { colorFilter = LightingColorFilter(0x00000000, 0x00120E0A) }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val size = minOf(width, height).toFloat()
            glow.shader = RadialGradient(width / 2f, height * .55f, size * .75f, 0x66EAC779, 0x00EAC779, Shader.TileMode.CLAMP)
            c.drawCircle(width / 2f, height * .55f, size * .75f, glow)
            c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), shade)
            HamletCharacterArt.draw(c, true, hero, width / 2f, height.toFloat(), size)
            c.restore()
        }
    }
}
