package com.Atom2Universe.app.games.roguelike

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode
import androidx.core.content.edit

class RoguelikeActivity : ThemedActivity() {

    private lateinit var gameView:     RoguelikeView
    private lateinit var combatView:   CombatView
    private lateinit var btnBack:      ImageButton
    private lateinit var tvGold:       TextView
    private lateinit var tvFloorLevel: TextView

    private var game = RoguelikeGame()

    private val sfx   by lazy { RoguelikeSoundEngine(lifecycleScope) }
    private val music by lazy { DungeonProceduralMusic(lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roguelike)
        enableImmersiveMode()

        gameView     = findViewById(R.id.roguelike_view)
        combatView   = findViewById(R.id.roguelike_combat_view)
        btnBack      = findViewById(R.id.roguelike_btn_back)
        tvGold       = findViewById(R.id.roguelike_tv_gold)
        tvFloorLevel = findViewById(R.id.roguelike_tv_floorlevel)

        btnBack.setOnClickListener { finish() }

        // sfx et music démarrés dans onResume uniquement (évite le double init EAS)
        if (SaveManager.hasSave(this)) {
            showContinueDialog()
        } else {
            attachGame(RoguelikeGame())
        }
    }

    // ── Dialog continuer / nouvelle partie ──────────────────────────────────────

    private fun showContinueDialog() {
        val summary = SaveManager.saveSummary(this) ?: getString(R.string.roguelike_save_in_progress_fallback)
        AlertDialog.Builder(this, R.style.Theme_A2U_Dialog)
            .setTitle(R.string.roguelike_title)
            .setMessage(getString(R.string.roguelike_resume_dialog_message, summary))
            .setCancelable(false)
            .setPositiveButton(R.string.roguelike_resume_dialog_continue) { _, _ ->
                val saved = SaveManager.load(this)
                attachGame(saved ?: RoguelikeGame())
            }
            .setNegativeButton(R.string.roguelike_resume_dialog_new_game) { _, _ ->
                SaveManager.clear(this)
                attachGame(RoguelikeGame())
            }
            .show()
    }

    // ── Cycle de vie — sauvegarde auto ───────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        music.stop()
        sfx.stop()
        // La mort ne remet pas à zéro : on sauvegarde toujours (équipement, or, étage)
        SaveManager.save(this, game)
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        sfx.start()
        music.start(game.floor)
    }

    override fun onDestroy() {
        super.onDestroy()
        music.stop()
        sfx.stop()
    }

    // ── Attache un game ──────────────────────────────────────────────────────────

    /** Étage le plus profond jamais atteint, pour les stats jeux. */
    private fun saveBestFloorIfBetter(floor: Int) {
        val prefs = getSharedPreferences("roguelike_save", MODE_PRIVATE)
        if (floor > prefs.getInt("best_floor", 0)) {
            prefs.edit { putInt("best_floor", floor) }
        }
    }

    private fun attachGame(g: RoguelikeGame) {
        game          = g
        gameView.game = g

        g.onFloorChanged = { floor -> music.onFloorChanged(floor); saveBestFloorIfBetter(floor) }
        g.onCombatStart  = { showCombat() }

        gameView.onMove          = { dx, dy -> g.tryMove(dx, dy); refresh() }
        gameView.onRest          = { g.rest(); refresh() }
        gameView.onOpenMerchant  = { g.openMerchant(); refresh() }
        gameView.onBuyPotion     = { g.buyPotion(); refresh() }
        gameView.onCloseMerchant = { g.closeMerchant(); refresh() }
        gameView.onDescend       = { sfx.onDescend(); g.descend(); refresh() }
        gameView.onEquipItem     = { g.equipPendingDrop(); refresh() }
        gameView.onIgnoreDrop    = { g.ignorePendingDrop(); refresh() }
        gameView.onDismissDeath  = { g.dismissDeath(); refresh() }

        combatView.onStrike    = { crit -> sfx.onPlayerAttack(crit) }
        combatView.onEnemyDied = { sfx.onMonsterDied() }
        combatView.onHeroHit   = { sfx.onPlayerHit() }
        combatView.onParry     = { perfect -> sfx.onParry(perfect) }
        combatView.onFinished  = {
            g.finishCombat()
            // Un poursuivant tout proche a pu relancer un combat pendant finishCombat
            if (g.combat == null) combatView.visibility = View.GONE
            refresh()
        }

        combatView.visibility = View.GONE
        refresh()
    }

    private fun showCombat() {
        val c = game.combat ?: return
        combatView.visibility = View.VISIBLE
        combatView.start(c, game.heroSpritePath)
    }

    private fun refresh() {
        gameView.invalidate()
        val h = game.hero
        tvGold.text       = getString(R.string.roguelike_hud_gold, h.gold)
        tvFloorLevel.text = getString(R.string.roguelike_hud_floor, game.floor)
    }
}
