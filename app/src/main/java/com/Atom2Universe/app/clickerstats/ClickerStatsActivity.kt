package com.Atom2Universe.app.clickerstats

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.ClickerOfflineRepository
import com.Atom2Universe.app.crypto.clicker.ClickerRepository
import com.Atom2Universe.app.crypto.clicker.ClickerStatsRepository
import com.Atom2Universe.app.crypto.clicker.CritRepository
import com.Atom2Universe.app.crypto.clicker.ElementBonusEngine
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.GachaTicketRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.fusion.ElementCardRepository
import com.Atom2Universe.app.crypto.sync.ParticulesRecords
import com.Atom2Universe.app.crypto.sync.SharedGameStats
import com.Atom2Universe.app.crypto.sync.SyncedStat
import com.Atom2Universe.app.crypto.fusion.FusionStore
import com.Atom2Universe.app.periodic.PeriodicCollectionStore
import com.Atom2Universe.app.util.enableImmersiveMode
import android.content.Intent
import kotlinx.coroutines.launch
import java.text.NumberFormat
import androidx.core.content.edit

class ClickerStatsActivity : ThemedActivity() {

    private var recordsJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_clicker_stats)

        val stats = ClickerStatsRepository(this).load()

        val fmt = NumberFormat.getNumberInstance(resources.configuration.locales[0])

        findViewById<TextView>(R.id.stat_total_clicks_value).text =
            fmt.format(stats.totalClicks)

        findViewById<TextView>(R.id.stat_frenzy_clicks_value).text =
            fmt.format(stats.clicksDuringFrenzy)

        findViewById<TextView>(R.id.stat_apc_frenzies_value).text =
            fmt.format(stats.apcFrenzyCount)

        findViewById<TextView>(R.id.stat_aps_frenzies_value).text =
            fmt.format(stats.apsFrenzyCount)

        val cps = stats.maxCps.toInt()
        findViewById<TextView>(R.id.stat_max_cps_value).text =
            if (cps > 0) "$cps /s" else "—"

        findViewById<TextView>(R.id.stat_max_apc_clicks_value).text =
            if (stats.maxClicksPerApcFrenzy > 0) "${stats.maxClicksPerApcFrenzy}" else "—"

        findViewById<TextView>(R.id.stat_play_time_value).text =
            formatPlayTime(stats.totalPlayTimeMs)

        val elemBonuses = ElementBonusEngine.compute(PeriodicCollectionStore(this))
        findViewById<TextView>(R.id.stat_element_bonus_apc_value).text =
            formatElementBonus(elemBonuses.flatApc, elemBonuses.multApc)
        findViewById<TextView>(R.id.stat_element_bonus_aps_value).text =
            formatElementBonus(elemBonuses.flatAps, elemBonuses.multAps)

        bindProductionSplit(stats)
        setupLongPressDeletes()

        findViewById<ImageButton>(R.id.stats_back_btn).setOnClickListener { finish() }

        val statsRepository  = ClickerStatsRepository(this)
        val periodicStore    = PeriodicCollectionStore(this)
        val fusionStore      = FusionStore(this)
        val critRepo         = CritRepository(this)
        val clickerRepo      = ClickerRepository(this)
        val offlineRepo      = ClickerOfflineRepository(this)

        findViewById<ImageButton>(R.id.stats_reset_btn).setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_reset_options, null)
            val toggleStats    = dialogView.findViewById<SwitchCompat>(R.id.toggle_stats)
            val togglePeriodic = dialogView.findViewById<SwitchCompat>(R.id.toggle_periodic)
            val toggleClicker  = dialogView.findViewById<SwitchCompat>(R.id.toggle_clicker)
            val wordInput      = dialogView.findViewById<EditText>(R.id.reset_word_input)
            val cancelBtn      = dialogView.findViewById<Button>(R.id.reset_cancel_btn)
            val confirmBtn     = dialogView.findViewById<Button>(R.id.reset_confirm_btn)

            val dialog = AlertDialog.Builder(this)
                .setTitle(getString(R.string.stats_reset_confirm_title))
                .setMessage(getString(R.string.stats_reset_confirm_message))
                .setView(dialogView)
                .create()

            wordInput.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    confirmBtn.isEnabled = !s.isNullOrBlank()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            cancelBtn.setOnClickListener { dialog.dismiss() }

            confirmBtn.setOnClickListener {
                val word = wordInput.text.toString().trim()
                if (word == "quark") {
                    dialog.dismiss()
                    startActivity(Intent(this, DevCheatActivity::class.java))
                    return@setOnClickListener
                }
                if (word != "reset") return@setOnClickListener

                if (toggleStats.isChecked) {
                    statsRepository.reset()
                }
                if (togglePeriodic.isChecked) {
                    periodicStore.reset()
                    fusionStore.reset()
                    // Cartes de collection gagnées lors des fusions (prefs séparées de FusionStore).
                    ElementCardRepository(this).reset()
                    // Les niveaux crit sont achetés avec les quarks (FusionStore) : on les
                    // remet à zéro ici aussi pour qu'ils ne survivent pas à leur monnaie.
                    critRepo.reset()
                }
                if (toggleClicker.isChecked) {
                    getSharedPreferences("clicker_achievements", MODE_PRIVATE).edit { clear() }
                    lifecycleScope.launch(kotlinx.coroutines.NonCancellable) {
                        clickerRepo.reset()
                        GachaTicketRepository(this@ClickerStatsActivity).resetTickets()
                    }
                    NeutrinoRepository(this).reset()
                    offlineRepo.save(0L)
                    getSharedPreferences("chess_save",            MODE_PRIVATE).edit { clear() }
                    getSharedPreferences("game2048_save",         MODE_PRIVATE).edit { clear() }
                    getSharedPreferences("draughts_save",         MODE_PRIVATE).edit { clear() }
                    getSharedPreferences("color_stack_save",      MODE_PRIVATE).edit { clear() }
                    getSharedPreferences("the_line_widget_save",  MODE_PRIVATE).edit { clear() }
                    getSharedPreferences("memory_save",           MODE_PRIVATE).edit { clear() }
                    getSharedPreferences("pipetap_save",          MODE_PRIVATE).edit { clear() }
                }
                // Signaler au ViewModel (dans MainClickerActivity) que le reset a eu lieu,
                // afin qu'il vide aussi sa RAM avant que l'autosave ne réécrive dessus.
                getSharedPreferences("pending_reset_flags", MODE_PRIVATE).edit()
                    .putBoolean("reset_stats",   toggleStats.isChecked)
                    .putBoolean("reset_clicker", toggleClicker.isChecked)
                    .apply()
                dialog.dismiss()
                finish()
            }

            dialog.show()
        }
    }

    override fun onResume() {
        super.onResume()
        val container = findViewById<LinearLayout>(R.id.game_stats_sections)
        val sections = (0 until container.childCount).map { container.getChildAt(it) }.associateBy { it.tag }
        container.removeAllViews()
        com.Atom2Universe.app.games.GamesCatalog.orderedTiles(this).forEach { tile ->
            sections[tile.id]?.let { container.addView(it) }
        }
        bindGameStats()
    }

    private fun bindProductionSplit(stats: com.Atom2Universe.app.crypto.clicker.ClickerStats) {
        val apc = stats.lifetimeApcAtoms
        val aps = stats.lifetimeApsAtoms
        val total = apc.add(aps)

        val apcPct: Double
        val apsPct: Double
        if (total.isZero()) {
            apcPct = 0.0
            apsPct = 0.0
        } else {
            apcPct = apc.divide(total).toNumber() * 100.0
            apsPct = 100.0 - apcPct
        }

        val fmtPct = { v: Double -> if (total.isZero()) "—" else "${"%.1f".format(v)} %" }
        findViewById<TextView>(R.id.stat_production_apc_value).text = fmtPct(apcPct)
        findViewById<TextView>(R.id.stat_production_aps_value).text = fmtPct(apsPct)

        val bar = findViewById<LinearLayout>(R.id.production_bar)
        val barApc = bar.getChildAt(0)
        val barAps = bar.getChildAt(1)
        val wApc = if (total.isZero()) 1f else apcPct.toFloat().coerceIn(0f, 100f)
        val wAps = if (total.isZero()) 1f else apsPct.toFloat().coerceIn(0f, 100f)
        (barApc.layoutParams as LinearLayout.LayoutParams).weight = wApc
        (barAps.layoutParams as LinearLayout.LayoutParams).weight = wAps
        barApc.layoutParams = barApc.layoutParams
        barAps.layoutParams = barAps.layoutParams
    }


    private fun formatElementBonus(flat: Long, mult: Double): String {
        val parts = mutableListOf<String>()
        if (flat > 0) parts.add("+$flat flat")
        if (mult > 0.0) parts.add("×${"%.2f".format(1.0 + mult)}")
        return if (parts.isEmpty()) "—" else parts.joinToString("  ")
    }

    private fun formatPlayTime(ms: Long): String {
        if (ms <= 0L) return "—"
        val totalSec = ms / 1000L
        val hours    = totalSec / 3600
        val minutes  = (totalSec % 3600) / 60
        val secs     = totalSec % 60
        return when {
            hours > 0   -> "${hours}h ${minutes}min"
            minutes > 0 -> "${minutes}min ${secs}s"
            else        -> "${secs}s"
        }
    }

    private fun bindGameStats() {
        val gameStats = GameStatsRepository(this).load()
        val fmt = NumberFormat.getNumberInstance(resources.configuration.locales[0])

        fun TextView.setWins(won: Int, played: Int) {
            text = if (played > 0 || won > 0) getString(R.string.game_stat_ratio, fmt.format(won), fmt.format(played)) else getString(R.string.game_stat_empty)
        }

        // Compteurs : la somme de tous les appareils (cet appareil + ceux vus à la dernière sync).
        fun total(key: String) = SharedGameStats.total(this, "game_stats", key)

        findViewById<TextView>(R.id.stat_solitaire_won_value).setWins(total("solitaire_won"), total("solitaire_played"))
        findViewById<TextView>(R.id.stat_sudoku_won_value).setWins(total("sudoku_won"), total("sudoku_played"))
        findViewById<TextView>(R.id.stat_chess_won_value).setWins(total("chess_won"), total("chess_played"))
        findViewById<TextView>(R.id.stat_draughts_won_value).setWins(total("draughts_won"), total("draughts_played"))
        findViewById<TextView>(R.id.stat_2048_won_value).setWins(total("game2048_won"), total("game2048_played"))
        findViewById<TextView>(R.id.stat_blackjack_won_value).setWins(total("blackjack_won"), total("blackjack_played"))
        findViewById<TextView>(R.id.stat_colorstack_won_value)
            .setWins(total("colorstack_hard_won"), total("colorstack_hard_played"))
        findViewById<TextView>(R.id.stat_colorstack_time_value).text =
            if (gameStats.colorStackHardBestMs > 0) formatMs(gameStats.colorStackHardBestMs) else "—"
        val pipeTapWon = total("pipetap_hard_won")
        findViewById<TextView>(R.id.stat_pipetap_won_value).text =
            if (pipeTapWon > 0) fmt.format(pipeTapWon) else "—"
        findViewById<TextView>(R.id.stat_othello_won_value).setWins(total("othello_won"), total("othello_played"))

        // Records depuis SharedPreferences individuels
        val sw = getSharedPreferences("stars_war_save", MODE_PRIVATE)
        val swScore = sw.getInt("best_score", 0)
        val swWave  = sw.getInt("best_wave", 0)
        findViewById<TextView>(R.id.stat_starswar_score_value).text = if (swScore > 0) fmt.format(swScore) else "—"
        findViewById<TextView>(R.id.stat_starswar_wave_value).text  = if (swWave > 0) fmt.format(swWave) else "—"

        val rx = getSharedPreferences("reflex_save", MODE_PRIVATE)
        val rxBest = rx.getLong("best_score", 0L)
        findViewById<TextView>(R.id.stat_reflex_best_value).text = if (rxBest > 0) fmt.format(rxBest) else "—"

        val fc = getSharedPreferences("flappy_cat_save", MODE_PRIVATE)
        val fcScore = fc.getInt("best_score", 0)
        findViewById<TextView>(R.id.stat_flappy_score_value).text = if (fcScore > 0) fmt.format(fcScore) else "—"

        val g2048 = getSharedPreferences("game2048_save", MODE_PRIVATE)
        val best2048 = g2048.getInt("best_score", 0)
        findViewById<TextView>(R.id.stat_2048_best_value).text = if (best2048 > 0) fmt.format(best2048) else "—"

        val ws = getSharedPreferences("wave_surf_save", MODE_PRIVATE)
        val wsSpeed = ws.getInt("best_speed", 0)
        val wsAlt   = ws.getInt("best_altitude", 0)
        findViewById<TextView>(R.id.stat_wavesurf_speed_value).text =
            if (wsSpeed > 0) "${fmt.format(wsSpeed)} km/h" else "—"
        findViewById<TextView>(R.id.stat_wavesurf_altitude_value).text =
            if (wsAlt > 0) "${fmt.format(wsAlt)} m" else "—"

        val sv = getSharedPreferences("survivor_save", MODE_PRIVATE)
        val svTime  = sv.getFloat("best_time", 0f)
        val svKills = sv.getInt("best_kills", 0)
        val svTimeSec = svTime.toInt()
        findViewById<TextView>(R.id.stat_survivor_time_value).text =
            if (svTime > 0f) "%d:%02d".format(svTimeSec / 60, svTimeSec % 60) else "—"
        findViewById<TextView>(R.id.stat_survivor_kills_value).text =
            if (svKills > 0) fmt.format(svKills) else "—"

        val m3 = getSharedPreferences("match3_save", MODE_PRIVATE)
        val m3Score = m3.getInt("best_score", 0)
        findViewById<TextView>(R.id.stat_match3_best_value).text = if (m3Score > 0) fmt.format(m3Score) else "—"
        val m3TimeMs = m3.getLong("best_time_ms", 0L)
        findViewById<TextView>(R.id.stat_match3_best_time_value).text = if (m3TimeMs > 0L) {
            val secs = (m3TimeMs / 1000).toInt()
            "%d:%02d".format(secs / 60, secs % 60)
        } else "—"

        val hrBestMs = gameStats.hexRunnerBestMs
        findViewById<TextView>(R.id.stat_hexrunner_best_value).text = if (hrBestMs > 0L) {
            val secs = (hrBestMs / 1000).toInt()
            if (secs >= 60) "%d:%02d".format(secs / 60, secs % 60) else "$secs s"
        } else "—"

        val hp = getSharedPreferences("hot_potato_save", MODE_PRIVATE)
        val hpScore = hp.getInt("best_score", 0)
        findViewById<TextView>(R.id.stat_hotpotato_score_value).text = if (hpScore > 0) fmt.format(hpScore) else "—"

        val ob = getSharedPreferences("orbite_save", MODE_PRIVATE)
        val obBest = ob.getInt("best", 0)
        findViewById<TextView>(R.id.stat_orbite_score_value).text = if (obBest > 0) fmt.format(obBest) else "—"

        val cr = getSharedPreferences("cosmo_run_save", MODE_PRIVATE)
        val crBest = cr.getInt("best_score", 0)
        findViewById<TextView>(R.id.stat_cosmorun_score_value).text = if (crBest > 0) fmt.format(crBest) else "—"

        val nc = getSharedPreferences("nuclea_save", MODE_PRIVATE)
        val ncWave = nc.getInt("best_wave", 0)
        findViewById<TextView>(R.id.stat_nuclea_wave_value).text = if (ncWave > 0) fmt.format(ncWave) else "—"

        val tb = getSharedPreferences(com.Atom2Universe.app.games.trebuchet.TREBUCHET_PREFS, MODE_PRIVATE)
        val tbDistance = tb.getFloat("best_distance", 0f)
        findViewById<TextView>(R.id.stat_trebuchet_distance_value).text =
            if (tbDistance > 0f) "${fmt.format(tbDistance.toInt())} m" else "—"
        val tbSites = SharedGameStats.total(this, "trebuchet_game", "sites_destroyed")
        findViewById<TextView>(R.id.stat_trebuchet_sites_value).text = if (tbSites > 0) fmt.format(tbSites) else "—"

        val rg = getSharedPreferences("roguelike_save", MODE_PRIVATE)
        val rgFloor = rg.getInt("best_floor", 0)
        findViewById<TextView>(R.id.stat_roguelike_floor_value).text = if (rgFloor > 0) fmt.format(rgFloor) else "—"

        val elSolved = SharedGameStats.total(this, "escape_labyrinth_prefs", "solved")
        val elPerfect = SharedGameStats.total(this, "escape_labyrinth_prefs", "solved_perfect")
        findViewById<TextView>(R.id.stat_escape_solved_value).text = if (elSolved > 0) fmt.format(elSolved) else "—"
        findViewById<TextView>(R.id.stat_escape_perfect_value).text = if (elPerfect > 0) fmt.format(elPerfect) else "—"

        val sbSolved = SharedGameStats.total(this, "starbridges_save", "solved")
        findViewById<TextView>(R.id.stat_starbridges_solved_value).text = if (sbSolved > 0) fmt.format(sbSolved) else "—"

        bindAdditionalStats()
        bindMemoryStats()
        bindCirclesStats()

        // Particules : Room DB async
        recordsJob?.cancel()
        recordsJob = lifecycleScope.launch {
            val meta = com.Atom2Universe.app.games.particules.data.ParticulesDatabase
                .getInstance(applicationContext).metaDao().getMeta()
            val scoreView = findViewById<TextView>(R.id.stat_particules_score_value)
            val levelView = findViewById<TextView>(R.id.stat_particules_level_value)
            // Indépendants : chaque ligne peut être supprimée seule, l'autre doit rester.
            val score = meta?.highScore ?: 0L
            val level = meta?.highestLevel ?: 0
            scoreView.text = if (score > 0) fmt.format(score) else "—"
            levelView.text = if (level > 0) fmt.format(level) else "—"
            addExtraStat("particules", getString(R.string.game_stat_combo), number(meta?.bestCombo ?: 0),
                listOf(SyncedStat(ParticulesRecords.SOURCE, ParticulesRecords.KEY_BEST_COMBO)))
        }
    }

    /** Une ligne par difficulté : meilleur temps + meilleur nombre de coups. */
    private fun bindMemoryStats() {
        val prefs = getSharedPreferences("memory_save", MODE_PRIVATE)
        val container = findViewById<LinearLayout>(R.id.memory_diff_container)
        container.removeAllViews()
        for (diff in com.Atom2Universe.app.games.memory.MemoryDifficulty.entries) {
            val time = prefs.getInt("best_time_" + diff.name, 0)
            val moves = prefs.getInt("best_moves_" + diff.name, 0)

            val row = layoutInflater.inflate(R.layout.item_clicker_stat_row, container, false)
            row.findViewById<TextView>(R.id.row_label).text = getString(R.string.game_stat_grid, diff.cols, diff.rows)
            val parts = mutableListOf<String>()
            if (time > 0) parts.add("%d:%02d".format(time / 60, time % 60))
            if (moves > 0) parts.add(getString(R.string.stat_moves_format, moves))
            row.findViewById<TextView>(R.id.row_value).text = parts.joinToString(" · ").ifEmpty { getString(R.string.game_stat_empty) }
            onLongPressDelete(row, listOf(
                SyncedStat("memory_save", "best_time_" + diff.name),
                SyncedStat("memory_save", "best_moves_" + diff.name)
            ))
            container.addView(row)
        }
    }

    /** Une ligne par difficulté : puzzles résolus + meilleur nombre de coups. */
    private fun bindCirclesStats() {
        val prefs = getSharedPreferences("circles_save", MODE_PRIVATE)
        val container = findViewById<LinearLayout>(R.id.circles_diff_container)
        container.removeAllViews()
        for (diff in com.Atom2Universe.app.games.circles.CirclesDifficulty.entries) {
            val solved = SharedGameStats.total(this, "circles_save", "solved_" + diff.name)
            val moves = prefs.getInt("best_moves_" + diff.name, 0)

            val row = layoutInflater.inflate(R.layout.item_clicker_stat_row, container, false)
            row.findViewById<TextView>(R.id.row_label).text = difficultyLabel(diff.name)
            val parts = mutableListOf(getString(R.string.stat_solved_format, solved))
            if (moves > 0) parts.add(getString(R.string.stat_moves_format, moves))
            row.findViewById<TextView>(R.id.row_value).text = parts.joinToString(" · ").ifEmpty { getString(R.string.game_stat_empty) }
            onLongPressDelete(row, listOf(
                SyncedStat("circles_save", "solved_" + diff.name),
                SyncedStat("circles_save", "best_moves_" + diff.name)
            ))
            container.addView(row)
        }
    }

    // ── Suppression d'une statistique (appui long) ───────────────────────────

    /** Les lignes fixes de la page et ce que chacune supprime. */
    private fun setupLongPressDeletes() {
        val g = "game_stats"
        val rows = mapOf(
            // Compteurs : additionnés entre appareils
            R.id.stat_solitaire_won_value to listOf(SyncedStat(g, "solitaire_won"), SyncedStat(g, "solitaire_played")),
            R.id.stat_sudoku_won_value to listOf(SyncedStat(g, "sudoku_won"), SyncedStat(g, "sudoku_played")),
            R.id.stat_chess_won_value to listOf(SyncedStat(g, "chess_won"), SyncedStat(g, "chess_played")),
            R.id.stat_draughts_won_value to listOf(SyncedStat(g, "draughts_won"), SyncedStat(g, "draughts_played")),
            R.id.stat_2048_won_value to listOf(SyncedStat(g, "game2048_won"), SyncedStat(g, "game2048_played")),
            R.id.stat_blackjack_won_value to listOf(SyncedStat(g, "blackjack_won"), SyncedStat(g, "blackjack_played")),
            R.id.stat_colorstack_won_value to listOf(SyncedStat(g, "colorstack_hard_won"), SyncedStat(g, "colorstack_hard_played")),
            R.id.stat_pipetap_won_value to listOf(SyncedStat(g, "pipetap_hard_won")),
            R.id.stat_othello_won_value to listOf(SyncedStat(g, "othello_won"), SyncedStat(g, "othello_played")),
            R.id.stat_trebuchet_sites_value to listOf(SyncedStat("trebuchet_game", "sites_destroyed")),
            R.id.stat_escape_solved_value to listOf(SyncedStat("escape_labyrinth_prefs", "solved")),
            R.id.stat_escape_perfect_value to listOf(SyncedStat("escape_labyrinth_prefs", "solved_perfect")),
            R.id.stat_starbridges_solved_value to listOf(SyncedStat("starbridges_save", "solved")),
            // Records : le meilleur de tous les appareils
            R.id.stat_colorstack_time_value to listOf(SyncedStat(g, "colorstack_hard_best_ms")),
            R.id.stat_hexrunner_best_value to listOf(SyncedStat(g, "hexrunner_best_ms")),
            R.id.stat_starswar_score_value to listOf(SyncedStat("stars_war_save", "best_score")),
            R.id.stat_starswar_wave_value to listOf(SyncedStat("stars_war_save", "best_wave")),
            R.id.stat_reflex_best_value to listOf(SyncedStat("reflex_save", "best_score")),
            R.id.stat_flappy_score_value to listOf(SyncedStat("flappy_cat_save", "best_score")),
            R.id.stat_2048_best_value to listOf(SyncedStat("game2048_save", "best_score")),
            R.id.stat_wavesurf_speed_value to listOf(SyncedStat("wave_surf_save", "best_speed")),
            R.id.stat_wavesurf_altitude_value to listOf(SyncedStat("wave_surf_save", "best_altitude")),
            R.id.stat_survivor_time_value to listOf(SyncedStat("survivor_save", "best_time")),
            R.id.stat_survivor_kills_value to listOf(SyncedStat("survivor_save", "best_kills")),
            R.id.stat_match3_best_value to listOf(SyncedStat("match3_save", "best_score")),
            R.id.stat_match3_best_time_value to listOf(SyncedStat("match3_save", "best_time_ms")),
            R.id.stat_hotpotato_score_value to listOf(SyncedStat("hot_potato_save", "best_score")),
            R.id.stat_orbite_score_value to listOf(SyncedStat("orbite_save", "best")),
            R.id.stat_cosmorun_score_value to listOf(SyncedStat("cosmo_run_save", "best_score")),
            R.id.stat_nuclea_wave_value to listOf(SyncedStat("nuclea_save", "best_wave")),
            R.id.stat_trebuchet_distance_value to listOf(SyncedStat("trebuchet_game", "best_distance")),
            R.id.stat_roguelike_floor_value to listOf(SyncedStat("roguelike_save", "best_floor")),
            R.id.stat_particules_score_value to listOf(SyncedStat(ParticulesRecords.SOURCE, ParticulesRecords.KEY_HIGH_SCORE)),
            R.id.stat_particules_level_value to listOf(SyncedStat(ParticulesRecords.SOURCE, ParticulesRecords.KEY_HIGHEST_LEVEL))
        )
        rows.forEach { (valueId, stats) ->
            // Toute la ligne réagit, pas seulement le chiffre.
            val row = findViewById<TextView>(valueId).parent as View
            onLongPressDelete(row, stats)
        }
    }

    /**
     * Appui long : une bulle avec, pour les compteurs, le détail par appareil, puis
     * l'action de suppression. Celle-ci passe par une confirmation, car elle efface
     * aussi sur les autres appareils.
     */
    private fun onLongPressDelete(row: View, stats: List<SyncedStat>) {
        row.setOnLongClickListener {
            val popup = PopupMenu(this, row, Gravity.END)
            val counters = stats.filter { it.isCounter }
            val fmt = NumberFormat.getNumberInstance(resources.configuration.locales[0])

            SharedGameStats.breakdown(this, counters).forEach { (deviceName, values) ->
                val name = deviceName ?: getString(R.string.stat_device_this)
                val line = getString(R.string.stat_device_line, name, values.joinToString(" / ") { fmt.format(it) })
                popup.menu.add(line).isEnabled = false
            }

            val actionLabel = if (counters.isNotEmpty()) R.string.stat_reset_counter else R.string.stat_delete_record
            popup.menu.add(actionLabel).setOnMenuItemClickListener {
                confirmDelete(stats)
                true
            }
            popup.show()
            true
        }
    }

    private fun confirmDelete(stats: List<SyncedStat>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.stat_delete_confirm_title)
            .setMessage(R.string.stat_delete_confirm_message)
            .setNegativeButton(R.string.stat_delete_cancel, null)
            .setPositiveButton(R.string.stat_delete_confirm_ok) { _, _ ->
                // Suspendu : Particules efface ses records dans sa base Room.
                lifecycleScope.launch {
                    SharedGameStats.delete(this@ClickerStatsActivity, stats)
                    bindGameStats()
                }
            }
            .show()
    }


    private fun extraContainer(game: String): LinearLayout =
        findViewById<LinearLayout>(R.id.game_stats_sections).findViewWithTag("extra_$game")

    private fun number(value: Number): String = NumberFormat.getNumberInstance(resources.configuration.locales[0]).format(value)

    private fun difficultyLabel(name: String): String = getString(when (name) {
        "EASY" -> R.string.game_stat_easy
        "NORMAL" -> R.string.game_stat_normal
        "MEDIUM" -> R.string.game_stat_medium
        "HARD" -> R.string.game_stat_hard
        "EXPERT" -> R.string.game_stat_expert
        else -> R.string.game_stat_extreme
    })

    private fun addExtraStat(game: String, label: String, value: String, stats: List<SyncedStat> = emptyList()) {
        val container = extraContainer(game)
        val row = layoutInflater.inflate(R.layout.item_clicker_stat_row, container, false)
        row.findViewById<TextView>(R.id.row_label).text = label
        row.findViewById<TextView>(R.id.row_value).text = value
        if (stats.isNotEmpty()) onLongPressDelete(row, stats)
        container.addView(row)
    }

    private fun recordRow(game: String, label: String, prefs: String, key: String, fallback: String? = null) {
        val data = getSharedPreferences(prefs, MODE_PRIVATE).all
        val value = (data[key] as? Number) ?: (fallback?.let { data[it] } as? Number)
        val text = if (value == null || value.toDouble() <= 0) getString(R.string.game_stat_empty)
            else number(value)
        addExtraStat(game, label, text, listOfNotNull(SyncedStat(prefs, key), fallback?.let { SyncedStat(prefs, it) }))
    }

    private fun bindAdditionalStats() {
        val sections = findViewById<LinearLayout>(R.id.game_stats_sections)
        // Certaines tuiles n'ont pas encore de statistiques pertinentes à afficher.
        for (index in 0 until sections.childCount) {
            extraContainer(sections.getChildAt(index).tag as String).removeAllViews()
        }
        for (diff in listOf("EASY", "MEDIUM")) {
            val played = SharedGameStats.total(this, "game_stats", "colorstack_played_$diff")
            val won = SharedGameStats.total(this, "game_stats", "colorstack_won_$diff")
            addExtraStat("colorstack", getString(R.string.game_stat_wins_difficulty, difficultyLabel(diff)),
                getString(R.string.game_stat_ratio, number(won), number(played)),
                listOf(SyncedStat("game_stats", "colorstack_won_$diff"), SyncedStat("game_stats", "colorstack_played_$diff")))
        }
        recordRow("bigger", getString(R.string.game_stat_score), "bigger_save", "best_score")
        recordRow("link", getString(R.string.game_stat_score), "link_save", "best_score")
        recordRow("reflex", getString(R.string.game_stat_combo), "reflex_save", "best_combo")
        recordRow("match3", getString(R.string.forge_endless_title), "match3_forge", "best_endless")
        recordRow("match3", getString(R.string.forge_slag_endless_title), "match3_forge", "best_slag")
        for (rounds in listOf(3, 6, 12)) {
            recordRow("match3", getString(R.string.game_stat_expedition, rounds), "match3_forge",
                "best_expedition_$rounds", fallback = if (rounds == 6) "best_expedition" else null)
        }
        val tier = getSharedPreferences("bigger_save", MODE_PRIVATE).getInt("discovered_tier", -1)
        addExtraStat("bigger", getString(R.string.game_stat_tier), if (tier < 0) getString(R.string.game_stat_empty) else number(tier + 1))
        val hits = getSharedPreferences("trebuchet_game", MODE_PRIVATE).all.filterKeys { it.startsWith("best_hits") }
            .values.mapNotNull { (it as? Int)?.takeIf { n -> n > 0 } }.minOrNull()
        addExtraStat("trebuchet", getString(R.string.game_stat_hits), hits?.let { number(it) } ?: getString(R.string.game_stat_empty),
            listOf(SyncedStat("trebuchet_game", "best_hits", isPrefix = true)))
        val farm = com.Atom2Universe.app.games.farm.FarmState(getSharedPreferences("farm_v1", MODE_PRIVATE))
        addExtraStat("farm", getString(R.string.game_stat_harvests), number(farm.harvests))
        addExtraStat("farm", getString(R.string.game_stat_parcels), number(farm.parcels.count { it.unlocked }))
        addExtraStat("farm", getString(R.string.game_stat_coins), number(farm.coins))
        val line = getSharedPreferences("the_line_save", MODE_PRIVATE)
        for (mode in com.Atom2Universe.app.games.theline.TheLineMode.entries) {
            for (diff in com.Atom2Universe.app.games.theline.TheLineDifficulty.entries) {
                val slot = mode.ordinal * com.Atom2Universe.app.games.theline.TheLineDifficulty.entries.size + diff.ordinal
                val modeName = getString(if (mode.ordinal == 0) R.string.the_line_mode_single else R.string.the_line_mode_multi)
                addExtraStat("theline", getString(R.string.game_stat_mode_difficulty, modeName, difficultyLabel(diff.name)),
                    number((line.getInt("level_$slot", 1) - 1).coerceAtLeast(0)))
            }
        }
        com.Atom2Universe.app.games.minesweeper.MinesweeperDifficulty.entries.forEach { diff ->
            val time = com.Atom2Universe.app.games.minesweeper.MinesweeperPrefs(this).getBestTime(diff)
            addExtraStat("minesweeper", getString(R.string.game_stat_time_difficulty, difficultyLabel(diff.name)), if (time < 0) getString(R.string.game_stat_empty)
                else getString(R.string.game_stat_seconds, time.toDouble()), listOf(SyncedStat("minesweeper_prefs", "best_${diff.name}")))
        }
        com.Atom2Universe.app.games.balance.BalanceGame.Difficulty.entries.forEach { diff ->
            recordRow("balance", getString(R.string.game_stat_level_difficulty, difficultyLabel(diff.name)), "balance_game", "best_level_${diff.ordinal}")
        }
        val spins = SharedGameStats.total(this, "roulette_stats", "spins")
        val wins = SharedGameStats.total(this, "roulette_stats", "wins")
        addExtraStat("roulette", getString(R.string.game_stat_spins), number(spins), listOf(SyncedStat("roulette_stats", "spins")))
        addExtraStat("roulette", getString(R.string.game_stat_winning_spins), number(wins), listOf(SyncedStat("roulette_stats", "wins")))
        recordRow("roulette", getString(R.string.game_stat_payout), "roulette_stats", "best_payout")
    }

    private fun formatMs(ms: Long): String {
        return if (ms >= 1000) "${"%.1f".format(ms / 1000.0)}s" else "${ms}ms"
    }
}
