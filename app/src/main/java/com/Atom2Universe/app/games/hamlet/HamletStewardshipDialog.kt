package com.Atom2Universe.app.games.hamlet

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import com.Atom2Universe.app.R

/** Point de retour commun : récolter, préparer un voyage, puis choisir une activité. */
internal class HamletStewardshipDialog(private val context: Context, private val g: HamletGame,
    private val onAdventure: (Int) -> Unit = {}, private val onJournal: (Int) -> Unit = {}) {
    private val state get() = g.stewardship
    private lateinit var dialog: AlertDialog
    private lateinit var rows: LinearLayout
    private lateinit var scroll: ScrollView
    private var tab = 0
    private var readiness: TextView? = null
    private var collectAction: Button? = null
    private var expeditionAction: Button? = null
    private val timer = Handler(Looper.getMainLooper())
    private val pulse = object : Runnable {
        override fun run() {
            if (!dialog.isShowing) return
            updateReadiness(); timer.postDelayed(this, 1000)
        }
    }
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun s(id: Int, vararg args: Any) = if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    private fun heroName(hero: Int): String = context.getString(context.resources.getIdentifier("hamlet_a_hero_$hero", "string", context.packageName))
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun text(parent: LinearLayout, value: String, title: Boolean = false) = TextView(context).also {
        it.text = value; it.textSize = if (title) 16f else 13f; it.setTextColor(HamletUi.INK)
        it.setPadding(0, dp(5), 0, dp(5)); it.setLineSpacing(dp(2).toFloat(), 1f)
        if (title) it.setTypeface(null, android.graphics.Typeface.BOLD)
        parent.addView(it)
    }
    private fun card(title: String) = column().also {
        it.background = HamletUi.panel(context, 0xFFEAE2CE.toInt(), 12)
        it.setPadding(dp(12), dp(9), dp(12), dp(10))
        rows.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }); text(it, title, true)
    }
    private fun button(parent: LinearLayout, value: String, enabled: Boolean = true, click: () -> Unit): Button = HamletUi.button(context, value, click = click).also {
        it.isEnabled = enabled; parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
    }
    fun show(initialTab: Int = 0) {
        tab = initialTab.coerceIn(0, 1); g.tick()
        val body = column().apply { setPadding(dp(12), dp(4), dp(12), dp(8)); setBackgroundColor(HamletUi.PAPER) }
        val tabs = LinearLayout(context)
        val controls = ArrayList<Button>()
        for (i in 0..1) {
            controls.add(HamletUi.button(context, s(if (i == 0) R.string.hamlet_s_today else R.string.hamlet_s_posts)) {
                tab = i; render(preserveScroll = false)
                controls.forEachIndexed { index, b -> b.isSelected = index == tab; b.background = HamletUi.control(context, selected = index == tab) }
            }.also { tabs.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) }) })
        }
        body.addView(tabs); rows = column(); scroll = ScrollView(context).apply { addView(rows) }
        body.addView(scroll, LinearLayout.LayoutParams(-1, minOf(dp(440), context.resources.displayMetrics.heightPixels * 65 / 100)))
        dialog = AlertDialog.Builder(context).setTitle(R.string.hamlet_s_title).setView(body)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnDismissListener { timer.removeCallbacksAndMessages(null) }
        dialog.show()
        controls.forEachIndexed { i, b -> b.isSelected = i == tab; b.background = HamletUi.control(context, selected = i == tab) }
        render(); timer.post(pulse)
    }
    private fun render(preserveScroll: Boolean = true) {
        val y = if (preserveScroll) scroll.scrollY else 0
        state.ensureDay(); rows.removeAllViews(); readiness = null; collectAction = null; expeditionAction = null
        if (tab == 0) today() else posts()
        scroll.post { scroll.scrollTo(0, y) }
    }
    private fun updateReadiness() {
        readiness?.text = s(R.string.hamlet_s_ready, g.readyCount(), g.adventure.heroes.count { it.owned }, g.adventure.heroes.size)
        collectAction?.isEnabled = g.canCollectAll() && g.collectableCount() > 0
        expeditionAction?.apply {
            isEnabled = g.journey.expedition >= 0 && g.journey.expeditionLeft() == 0L
            text = if (isEnabled) s(R.string.hamlet_expedition_collect) else s(R.string.hamlet_s_trip_wait, duration(g.journey.expeditionLeft()))
        }
    }
    private fun duration(ms: Long): String {
        val m = (ms.coerceAtLeast(0) + 59_999) / 60_000
        return if (m >= 60) s(R.string.hamlet_duration_hours, m / 60, m % 60) else s(R.string.hamlet_duration_minutes, m)
    }
    private fun today() {
        val overview = card(s(R.string.hamlet_s_return))
        readiness = text(overview, "")
        if (g.canCollectAll()) collectAction = button(overview, s(R.string.hamlet_ui_collect), g.collectableCount() > 0) {
            Toast.makeText(context, s(R.string.hamlet_round_result, g.collectAll()), Toast.LENGTH_SHORT).show(); render()
        } else text(overview, s(R.string.hamlet_collect_direct_hint, HamletGame.COLLECT_ALL_CHAPTER))
        if (g.journey.expedition >= 0) {
            expeditionAction = button(overview, "", g.journey.expeditionLeft() == 0L) { g.journey.collectExpedition(); render() }
        } else button(overview, s(R.string.hamlet_s_plan_trip)) { dialog.dismiss(); onJournal(1) }
        text(overview, s(R.string.hamlet_s_cycle))
        updateReadiness()
        val labels = intArrayOf(R.string.hamlet_s_task_village, R.string.hamlet_s_task_adventure, R.string.hamlet_s_task_growth)
        val help = intArrayOf(R.string.hamlet_s_task_village_help, R.string.hamlet_s_task_adventure_help, R.string.hamlet_s_task_growth_help)
        text(rows, s(R.string.hamlet_s_daily_help, duration(Hamlet.DAY_MS - g.now() % Hamlet.DAY_MS)))
        for (i in labels.indices) {
            val box = card(s(labels[i])); text(box, s(help[i]))
            val progress = state.progress(i).coerceAtMost(HamletStewardship.TARGETS[i].toLong())
            text(box, s(R.string.hamlet_s_task_progress, progress, HamletStewardship.TARGETS[i], HamletStewardship.TICKETS[i]))
            box.addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = HamletStewardship.TARGETS[i]; this.progress = progress.toInt() })
            val claimed = state.claimed and (1 shl i) != 0
            button(box, s(if (claimed) R.string.hamlet_daily_claimed else R.string.hamlet_claim), state.canClaim(i)) { state.claim(i); render() }
            if (!claimed && !state.canClaim(i)) button(box, s(R.string.hamlet_s_go)) {
                dialog.dismiss(); if (i > 0) onAdventure(if (i == 1) 0 else 1)
            }
        }
        val chest = card(s(R.string.hamlet_s_chest)); text(chest, s(R.string.hamlet_s_chest_help))
        button(chest, s(if (state.chestClaimed) R.string.hamlet_daily_claimed else R.string.hamlet_claim), state.canClaimChest()) { state.claimChest(); render() }
        button(rows, s(R.string.hamlet_s_chapters)) { dialog.dismiss(); onJournal(0) }
    }
    private fun posts() {
        text(rows, s(R.string.hamlet_s_posts_help))
        val titles = intArrayOf(R.string.hamlet_s_post_0, R.string.hamlet_s_post_1, R.string.hamlet_s_post_2)
        val help = intArrayOf(R.string.hamlet_s_post_help_0, R.string.hamlet_s_post_help_1, R.string.hamlet_s_post_help_2)
        for (post in titles.indices) {
            val hero = state.posts[post]; val box = card(s(titles[post]))
            text(box, s(help[post]))
            text(box, if (hero < 0) s(R.string.hamlet_s_unassigned) else s(R.string.hamlet_s_assignment, heroName(hero), state.bonus(post)))
            button(box, s(R.string.hamlet_s_assign)) {
                val owned = g.adventure.heroes.filter { it.owned }.sortedByDescending { state.heroBonus(post, it.id) }
                val choices = owned.map { s(R.string.hamlet_s_choice, heroName(it.id), state.heroBonus(post, it.id)) } + s(R.string.hamlet_s_unassign)
                AlertDialog.Builder(context).setTitle(titles[post]).setItems(choices.toTypedArray()) { _, i ->
                    state.assign(post, if (i == owned.size) -1 else owned[i].id); render()
                }.setNegativeButton(android.R.string.cancel, null).show()
            }
        }
        button(rows, s(R.string.hamlet_ui_adventure)) { dialog.dismiss(); onAdventure(1) }
    }
}
