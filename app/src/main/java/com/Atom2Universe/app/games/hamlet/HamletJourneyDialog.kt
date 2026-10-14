package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R

/** Carnet du village : une seule fenêtre, onglets horizontaux et objectifs lisibles avant déblocage. */
internal class HamletJourneyDialog(context: Context, game: HamletGame, haptics: Boolean = false, onQuest: (() -> Unit)? = null) {
    constructor(activity: HamletActivity) : this(activity, activity.game, activity.village.haptics, activity::showQuest)
    private val a = JournalContext(context, game, haptics, onQuest)
    private val g get() = a.game
    private val j get() = g.journey
    private lateinit var dialog: AlertDialog
    private lateinit var rows: LinearLayout
    private lateinit var scroll: ScrollView
    private val timer = Handler(Looper.getMainLooper())
    private var tab = 0
    private var countdown: TextView? = null
    private val tabButtons = ArrayList<Button>()
    private lateinit var tabScroll: HorizontalScrollView
    private val pulse = object : Runnable {
        override fun run() {
            if (!dialog.isShowing) return
            countdown?.text = a.getString(R.string.hamlet_expedition_running, name("region", j.expedition), a.duration(j.expeditionLeft()))
            timer.postDelayed(this, 1000)
        }
    }

    fun show(initialTab: Int = 0) {
        tab = initialTab
        val body = column().apply { setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8)); setBackgroundColor(0xFFF4ECD9.toInt()) }
        val tabs = LinearLayout(a)
        val labels = intArrayOf(R.string.hamlet_chapters, R.string.hamlet_expeditions, R.string.hamlet_companions, R.string.hamlet_research, R.string.hamlet_work)
        for (i in labels.indices) tabs.addView(Button(a).apply {
            text = a.getString(labels[i]); isAllCaps = false; textSize = 12f
            setOnClickListener { tab = i; scroll.scrollTo(0, 0); render() }
        }.also { tabButtons.add(it) })
        tabScroll = HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        body.addView(tabScroll)
        rows = column()
        scroll = ScrollView(a).apply { addView(rows) }
        body.addView(scroll, LinearLayout.LayoutParams(-1, minOf(a.dp(390), a.resources.displayMetrics.heightPixels * 55 / 100)))
        dialog = AlertDialog.Builder(a).setTitle(R.string.hamlet_journey).setView(body)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnDismissListener { timer.removeCallbacksAndMessages(null) }
        dialog.show(); render(); timer.post(pulse)
    }
    private fun render() {
        countdown = null; rows.removeAllViews()
        tabButtons.forEachIndexed { i, button ->
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(if (i == tab) 0xFF486D55.toInt() else 0xFFDFD6BF.toInt())
            button.setTextColor(if (i == tab) 0xFFF8F0DB.toInt() else 0xFF344C40.toInt())
            button.isSelected = i == tab
        }
        tabScroll.post { tabScroll.scrollTo(tabButtons[tab].left - a.dp(8), 0) }
        text(rows, a.getString(R.string.hamlet_journey_stock, j.chapter, HamletJourney.chapters.size, j.supplies, a.duration(g.workSeconds * 1000L)))
        when (tab) { 0 -> chapters(); 1 -> expeditions(); 2 -> companions(); 3 -> research(); 4 -> workshop() }
    }
    private fun name(type: String, index: Int) = a.stringByName("hamlet_${type}_$index")
    private fun column() = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private fun card(title: String, completed: Boolean = false): LinearLayout = column().also {
        it.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))
        it.background = a.rounded(if (completed) 0xFFE0E9CF.toInt() else 0xFFECE0C9.toInt(), 14)
        rows.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(10) })
        text(it, title, true)
    }
    private fun text(parent: LinearLayout, label: String, bold: Boolean = false): TextView = TextView(a).also {
        it.text = label; it.textSize = if (bold) 16f else 13f; it.setTextColor(0xFF344C40.toInt())
        it.setPadding(0, a.dp(4), 0, a.dp(4))
        if (bold) it.setTypeface(null, android.graphics.Typeface.BOLD)
        parent.addView(it)
    }
    private fun action(parent: LinearLayout, label: String, enabled: Boolean = true, perform: () -> Boolean) {
        parent.addView(Button(a).apply {
            text = label; isAllCaps = false; isEnabled = enabled; textSize = 13f
            setOnClickListener {
                val y = scroll.scrollY
                if (perform()) {
                    if (a.haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    render(); scroll.post { scroll.scrollTo(0, y) }
                } else a.toast(a.getString(R.string.hamlet_action_failed))
            }
        })
    }
    private fun goalLabel(goal: HamletJourney.Goal): String = when {
        goal.key.startsWith("built_") -> a.getString(R.string.hamlet_goal_build, a.itemName(goal.key.removePrefix("built_")))
        goal.key.startsWith("region_") -> a.getString(R.string.hamlet_goal_explore, name("region", goal.key.removePrefix("region_").toInt()))
        else -> a.stringByName("hamlet_goal_${goal.key}")
    }
    private fun chapters() {
        text(rows, a.getString(R.string.hamlet_chapter_help))
        action(rows, a.getString(R.string.hamlet_quest)) { a.showQuest(); true }
        // La prochaine étape apparaît en premier ; les suivantes montrent ce qui reste à découvrir.
        val order = (j.chapter until HamletJourney.chapters.size).toList() + (0 until j.chapter).toList()
        for (i in order) {
            val c = HamletJourney.chapters[i]
            val done = i < j.chapter
            val box = card(a.getString(R.string.hamlet_chapter_title, i + 1, name("chapter", i)), done)
            text(box, a.stringByName("hamlet_chapter_story_$i"))
            val unlocks = HamletJourney.buildings.filterValues { it == i + 1 }.keys.map { a.itemName(it) }.toMutableList()
            HamletJourney.regions.forEachIndexed { r, def -> if (def.chapter == i + 1) unlocks.add(name("region", r)) }
            if (i % 2 == 0) unlocks.add(name("companion", i / 2))
            if (i + 1 == HamletGame.COLLECT_ALL_CHAPTER) unlocks.add(a.getString(R.string.hamlet_ui_collect))
            text(box, a.getString(R.string.hamlet_chapter_unlocks, unlocks.joinToString(a.getString(R.string.hamlet_list_separator))))
            if (done) text(box, a.getString(R.string.hamlet_daily_claimed))
            else {
                text(box, a.getString(R.string.hamlet_chapter_requirements, c.level, c.hall))
                for (goal in c.goals) text(box, a.getString(R.string.hamlet_goal_progress, goalLabel(goal), j.progress(goal).coerceAtMost(goal.target.toLong()), goal.target))
                text(box, a.getString(R.string.hamlet_chapter_reward, c.coins, c.supplies))
                if (i == j.chapter) action(box, a.getString(R.string.hamlet_claim), j.canClaim()) { j.claim() }
            }
        }
    }
    private fun expeditions() {
        text(rows, a.getString(R.string.hamlet_expedition_help, j.power()))
        text(rows, a.getString(R.string.hamlet_s_expedition_link, j.scoutingPower(), j.scoutingSpeed()))
        text(rows, a.getString(R.string.hamlet_expedition_invitation_help))
        if (j.expedition >= 0) {
            val box = card(name("region", j.expedition))
            countdown = text(box, a.getString(R.string.hamlet_expedition_running, name("region", j.expedition), a.duration(j.expeditionLeft())))
            action(box, a.getString(R.string.hamlet_expedition_collect)) {
                if (j.expeditionLeft() > 0) { a.toast(a.duration(j.expeditionLeft())); false } else j.collectExpedition()
            }
            action(box, a.getString(R.string.hamlet_work_apply), g.workSeconds > 0 && j.expeditionLeft() > 0) { g.workOnExpedition() }
            action(box, a.getString(R.string.hamlet_work)) { tab = 4; true }
        }
        for (i in HamletJourney.regions.indices) {
            val r = HamletJourney.regions[i]
            val box = card(name("region", i), j.explored[i] > 0)
            text(box, a.stringByName("hamlet_region_story_$i"))
            text(box, a.getString(R.string.hamlet_region_detail, r.chapter, r.power, a.duration(j.expeditionSeconds(i) * 1000L), r.coins, r.supplies, j.explored[i]))
            text(box, a.getString(R.string.hamlet_s_expedition_loot, j.expeditionFood(i), j.expeditionOre(i), j.expeditionBooks(i), j.expeditionTickets(i)))
            action(box, a.getString(R.string.hamlet_expedition_start), j.canExplore(i)) { j.startExpedition(i) }
        }
    }
    private fun companions() {
        text(rows, a.getString(R.string.hamlet_companion_help, j.power()))
        for (i in j.companions.indices) {
            val rank = j.companions[i]
            val box = card(name("companion", i), rank > 0)
            box.addView(Portrait(a, i), LinearLayout.LayoutParams(-1, a.dp(76)))
            text(box, a.stringByName("hamlet_companion_story_$i"))
            text(box, a.getString(R.string.hamlet_companion_rank, rank, i * 2 + 1))
            val slot = j.party.indexOf(i)
            if (slot >= 0) text(box, a.getString(R.string.hamlet_companion_assigned, slot + 1))
            if (rank > 0 || g.sandbox) {
                action(box, a.getString(R.string.hamlet_companion_assign)) {
                    AlertDialog.Builder(a).setTitle(R.string.hamlet_companion_assign)
                        .setItems(j.party.mapIndexed { s, hero -> a.getString(R.string.hamlet_party_slot, s + 1, if (hero < 0) a.getString(R.string.hamlet_party_empty) else name("companion", hero)) }.toTypedArray()) { _, s -> j.assign(i, s); render() }.show()
                    true
                }
                if (rank in 1..4) action(box, a.getString(R.string.hamlet_training_cost, j.trainCoins(i), j.trainSupplies(i), rank * 2 + 1), j.canTrain(i)) { j.train(i) }
            }
        }
    }
    private fun research() {
        text(rows, a.getString(R.string.hamlet_research_help))
        for (i in j.research.indices) {
            val box = card(name("research", i))
            text(box, a.stringByName("hamlet_research_story_$i"))
            for (rank in 0..4) text(box, a.getString(R.string.hamlet_research_row, if (rank < j.research[i]) a.getString(R.string.hamlet_mark_done) else a.getString(R.string.hamlet_mark_future), name("tech", i * 5 + rank), 2 + rank * 2))
            if (j.research[i] < 5) action(box, a.getString(R.string.hamlet_research_cost, j.research[i] + 1, j.researchCoins(i), j.researchSupplies(i)), j.canResearch(i)) { j.study(i) }
        }
    }
    private fun workshop() {
        text(rows, a.getString(R.string.hamlet_work_intro))
        val box = card(a.getString(R.string.hamlet_work))
        g.beginWork()
        val scene = WorkScene(a) { g.workStep }
        box.addView(scene, LinearLayout.LayoutParams(-1, a.dp(96)))
        val prompt = text(box, a.stringByName("hamlet_work_task_${g.workTool}"), true)
        val meter = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 12; progress = g.workStep }
        box.addView(meter)
        val count = text(box, a.getString(R.string.hamlet_work_progress, g.workStep))
        val stock = text(box, a.getString(R.string.hamlet_work_bank, a.duration(g.workSeconds * 1000L)))
        val tools = LinearLayout(a)
        box.addView(tools)
        for (tool in 0..2) tools.addView(Button(a).apply {
            text = a.stringByName("hamlet_work_tool_$tool"); isAllCaps = false; textSize = 12f
            setOnClickListener {
                if (g.workSeconds >= 7200) { a.toast(a.getString(R.string.hamlet_work_full)); return@setOnClickListener }
                if (g.answerWork(tool)) {
                    if (a.haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    prompt.text = a.stringByName("hamlet_work_task_${g.workTool}")
                    meter.progress = g.workStep
                    scene.invalidate()
                    count.text = a.getString(R.string.hamlet_work_progress, g.workStep)
                    stock.text = a.getString(R.string.hamlet_work_bank, a.duration(g.workSeconds * 1000L))
                    animate().cancel(); alpha = 0.55f; animate().alpha(1f).setDuration(180).start()
                    if (g.workStep == 0) a.toast(a.getString(R.string.hamlet_work_reward))
                } else a.toast(a.getString(R.string.hamlet_work_retry))
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val construction = g.objs.values.filter { g.buildLeft(it) > 0 }
        for (o in construction) action(rows, a.getString(R.string.hamlet_work_target, a.itemName(o.t), a.duration(g.buildLeft(o)))) { g.applyWork(o) }
        if (j.expedition >= 0) action(rows, a.getString(R.string.hamlet_work_target, name("region", j.expedition), a.duration(j.expeditionLeft()))) { g.workOnExpedition() }
        action(rows, a.getString(R.string.hamlet_work_rules)) {
            AlertDialog.Builder(a).setTitle(R.string.hamlet_work).setMessage(R.string.hamlet_work_help)
                .setPositiveButton(android.R.string.ok, null).show()
            true
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

    /** Petits portraits entièrement dessinés, avec la couleur et l'outil du métier. */
    private class Portrait(a: Context, private val hero: Int) : View(a) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            c.save(); c.translate(width / 2f, height / 2f); c.scale(height / 80f, height / 80f)
            fun color(value: Int) { p.color = value or 0xFF000000.toInt() }
            val colors = intArrayOf(0x678F53, 0xB17855, 0x547F9B, 0x9676A9, 0xB75F79, 0xC6A052)
            color(0xD6CFB6); c.drawCircle(0f, 0f, 37f, p)
            color(colors[hero]); c.drawRoundRect(-21f, 12f, 21f, 42f, 12f, 12f, p)
            color(if (hero % 2 == 0) 0xE5B98E else 0xBF906B); c.drawOval(-17f, -25f, 17f, 17f, p)
            color(0x514333); c.drawCircle(-6f, -3f, 2f, p); c.drawCircle(6f, -3f, 2f, p)
            color(colors[hero]); c.drawRoundRect(-22f, -25f, 22f, -16f, 4f, 4f, p)
            c.drawRoundRect(-15f, -35f, 15f, -20f, 6f, 6f, p)
            HamletGlyphs.draw(c, arrayOf("flower", "wood", "map", "potion", "cloth", "star")[hero], 24f, 20f, 20f)
            c.restore()
        }
    }

    /** Le carnet n'a pas besoin du cycle de vie de l'activité pour se dessiner. */
    private class JournalContext(context: Context, val game: HamletGame, val haptics: Boolean, private val onQuest: (() -> Unit)?) : ContextWrapper(context) {
        fun showQuest() {
            if (onQuest != null) { onQuest.invoke(); return }
            val q = game.currentQuest() ?: run { toast(getString(R.string.hamlet_quests_done)); return }
            val title = stringByName("hamlet_q${game.quest}_t")
            val builder = AlertDialog.Builder(this).setTitle(title)
                .setMessage(getString(R.string.hamlet_quest_detail, title, stringByName("hamlet_q${game.quest}_d"), game.questProgress(q), q.n, q.coins, q.gems))
                .setNegativeButton(android.R.string.cancel, null)
            if (game.questDone(q)) builder.setPositiveButton(R.string.hamlet_claim) { _, _ -> game.claimQuest() }
            else builder.setPositiveButton(android.R.string.ok, null)
            builder.show()
        }
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
        fun stringByName(name: String): String {
            val id = resources.getIdentifier(name, "string", packageName)
            return getString(if (id == 0) R.string.hamlet_title else id)
        }
        fun itemName(id: String) = stringByName(if (id == "hall") "hamlet_n_hall${game.hall}" else "hamlet_n_$id")
        fun duration(ms: Long): String {
            if (ms < 60_000) return getString(R.string.hamlet_duration_seconds, (ms.coerceAtLeast(0) + 999) / 1000)
            val m = (ms.coerceAtLeast(0) + 59_999) / 60_000
            return if (m >= 60) getString(R.string.hamlet_duration_hours, m / 60, m % 60) else getString(R.string.hamlet_duration_minutes, m)
        }
        fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    }
}
