package com.Atom2Universe.app.clickerstats

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.MainClickerPreferences
import com.Atom2Universe.app.crypto.clicker.ClickerOfflineRepository
import com.Atom2Universe.app.crypto.clicker.ClickerRepository
import com.Atom2Universe.app.crypto.clicker.ClickerStatsRepository
import com.Atom2Universe.app.crypto.clicker.ElementBonusEngine
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.GachaTicketRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.periodic.PeriodicCollectionStore
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ClickerStatsActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_clicker_stats)

        val stats = ClickerStatsRepository(this).load()

        val fmt = NumberFormat.getNumberInstance(Locale.FRENCH)

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
        bindGameStats()

        findViewById<ImageButton>(R.id.stats_back_btn).setOnClickListener { finish() }

        val statsRepository  = ClickerStatsRepository(this)
        val periodicStore    = PeriodicCollectionStore(this)
        val clickerRepo      = ClickerRepository(this)
        val offlineRepo      = ClickerOfflineRepository(this)

        findViewById<ImageButton>(R.id.stats_reset_btn).setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_reset_options, null)
            val toggleStats    = dialogView.findViewById<SwitchCompat>(R.id.toggle_stats)
            val togglePeriodic = dialogView.findViewById<SwitchCompat>(R.id.toggle_periodic)
            val toggleClicker  = dialogView.findViewById<SwitchCompat>(R.id.toggle_clicker)
            val toggleRecords  = dialogView.findViewById<SwitchCompat>(R.id.toggle_records)
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
                if (word == "nsfw") {
                    dialog.dismiss()
                    val enabled = MainClickerPreferences.toggleNsfwMode(this)
                    val msgRes = if (enabled) R.string.settings_hidden_mode_on else R.string.settings_hidden_mode_off
                    Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (word != "reset") return@setOnClickListener

                if (toggleStats.isChecked) {
                    statsRepository.reset()
                    GameStatsRepository(this).reset()
                    getSharedPreferences("clicker_achievements", MODE_PRIVATE).edit().clear().apply()
                }
                if (togglePeriodic.isChecked) periodicStore.reset()
                if (toggleClicker.isChecked) {
                    lifecycleScope.launch(kotlinx.coroutines.NonCancellable) {
                        clickerRepo.reset()
                        GachaTicketRepository(this@ClickerStatsActivity).resetTickets()
                    }
                    NeutrinoRepository(this).reset()
                    offlineRepo.save(0L)
                    getSharedPreferences("chess_save",            MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("game2048_save",         MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("draughts_save",         MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("color_stack_save",      MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("the_line_widget_save",  MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("memory_save",           MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("pipetap_save",          MODE_PRIVATE).edit().clear().apply()
                }
                if (toggleRecords.isChecked) {
                    getSharedPreferences("stars_war_save",   MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("reflex_save",      MODE_PRIVATE).edit().clear().apply()
                    getSharedPreferences("flappy_cat_save",  MODE_PRIVATE).edit().clear().apply()
                    // Best score 2048 seulement (la partie en cours est gérée par toggleClicker)
                    getSharedPreferences("game2048_save",    MODE_PRIVATE).edit().remove("best_score").apply()
                    // Particules : zéro les records en conservant la progression shop
                    lifecycleScope.launch(kotlinx.coroutines.NonCancellable) {
                        val db = com.Atom2Universe.app.games.particules.data.ParticulesDatabase
                            .getInstance(applicationContext)
                        val meta = db.metaDao().getMeta()
                        if (meta != null) db.metaDao().upsert(meta.copy(highScore = 0L, highestLevel = 0))
                    }
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


    private fun formatElementBonus(flat: Int, mult: Double): String {
        val parts = mutableListOf<String>()
        if (flat > 0) parts.add("+$flat flat")
        if (mult > 0.0) parts.add("+${"%.2f".format(mult * 100)}%")
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
        val fmt = NumberFormat.getNumberInstance(Locale.FRENCH)

        fun TextView.setWins(won: Int, played: Int) {
            text = if (played > 0) "${fmt.format(won)} / ${fmt.format(played)}" else "—"
        }

        findViewById<TextView>(R.id.stat_solitaire_won_value).setWins(gameStats.solitaireWon, gameStats.solitairePlayed)
        findViewById<TextView>(R.id.stat_sudoku_won_value).setWins(gameStats.sudokuWon, gameStats.sudokuPlayed)
        findViewById<TextView>(R.id.stat_chess_won_value).setWins(gameStats.chessWon, gameStats.chessPlayed)
        findViewById<TextView>(R.id.stat_draughts_won_value).setWins(gameStats.draughtsWon, gameStats.draughtsPlayed)
        findViewById<TextView>(R.id.stat_2048_won_value).setWins(gameStats.game2048Won, gameStats.game2048Played)
        findViewById<TextView>(R.id.stat_blackjack_won_value).setWins(gameStats.blackjackWon, gameStats.blackjackPlayed)
        findViewById<TextView>(R.id.stat_colorstack_won_value)
            .setWins(gameStats.colorStackHardWon, gameStats.colorStackHardPlayed)
        findViewById<TextView>(R.id.stat_colorstack_time_value).text =
            if (gameStats.colorStackHardBestMs > 0) formatMs(gameStats.colorStackHardBestMs) else "—"
        findViewById<TextView>(R.id.stat_pipetap_won_value).text =
            if (gameStats.pipeTapHardWon > 0) fmt.format(gameStats.pipeTapHardWon) else "—"

        // Records depuis SharedPreferences individuels
        val sw = getSharedPreferences("stars_war_save", MODE_PRIVATE)
        val swScore = sw.getInt("best_score", 0)
        val swWave  = sw.getInt("best_wave", 0)
        findViewById<TextView>(R.id.stat_starswar_score_value).text = if (swScore > 0) fmt.format(swScore) else "—"
        findViewById<TextView>(R.id.stat_starswar_wave_value).text  = if (swWave > 0) fmt.format(swWave) else "—"

        val rx = getSharedPreferences("reflex_save", MODE_PRIVATE)
        val rxEasy = rx.getInt("best_easy", 0)
        val rxHard = rx.getInt("best_hard", 0)
        findViewById<TextView>(R.id.stat_reflex_easy_value).text = if (rxEasy > 0) formatMs(rxEasy.toLong()) else "—"
        findViewById<TextView>(R.id.stat_reflex_hard_value).text = if (rxHard > 0) formatMs(rxHard.toLong()) else "—"

        val fc = getSharedPreferences("flappy_cat_save", MODE_PRIVATE)
        val fcScore = fc.getInt("best_score", 0)
        findViewById<TextView>(R.id.stat_flappy_score_value).text = if (fcScore > 0) fmt.format(fcScore) else "—"

        val g2048 = getSharedPreferences("game2048_save", MODE_PRIVATE)
        val best2048 = g2048.getInt("best_score", 0)
        findViewById<TextView>(R.id.stat_2048_best_value).text = if (best2048 > 0) fmt.format(best2048) else "—"

        // Particules : Room DB async
        lifecycleScope.launch {
            val meta = com.Atom2Universe.app.games.particules.data.ParticulesDatabase
                .getInstance(applicationContext).metaDao().getMeta()
            val scoreView = findViewById<TextView>(R.id.stat_particules_score_value)
            val levelView = findViewById<TextView>(R.id.stat_particules_level_value)
            if (meta != null && meta.highScore > 0) {
                scoreView.text = fmt.format(meta.highScore)
                levelView.text = fmt.format(meta.highestLevel)
            } else {
                scoreView.text = "—"
                levelView.text = "—"
            }
        }
    }

    private fun formatMs(ms: Long): String {
        return if (ms >= 1000) "${"%.1f".format(ms / 1000.0)}s" else "${ms}ms"
    }
}
