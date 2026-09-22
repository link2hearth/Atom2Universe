package com.Atom2Universe.app.games.roguelike

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var tvFloorLevel: TextView
    private lateinit var btnInventory: Button
    private lateinit var healthBar:    ProgressBar
    private lateinit var inventory:    InventoryPanel
    private lateinit var lexicon:      LexiconPanel

    private var game = RoguelikeGame()
    private val saveHandler = Handler(Looper.getMainLooper())
    private val deferredSave = Runnable { SaveManager.saveState(this, game) }

    private val sfx   by lazy { RoguelikeSoundEngine(lifecycleScope) }
    private val music by lazy { DungeonProceduralMusic(lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roguelike)
        enableImmersiveMode()

        gameView     = findViewById(R.id.roguelike_view)
        combatView   = findViewById(R.id.roguelike_combat_view)
        btnBack      = findViewById(R.id.roguelike_btn_back)
        tvFloorLevel = findViewById(R.id.roguelike_tv_floorlevel)
        btnInventory = findViewById(R.id.roguelike_btn_inventory)
        healthBar    = findViewById(R.id.roguelike_health_bar)

        lexicon      = LexiconPanel(findViewById(R.id.roguelike_lexicon)) { game }
        inventory    = InventoryPanel(findViewById(R.id.roguelike_inventory), lexicon,
            onChanged = { saveNow(); refresh() },
            onClosed = { saveNow() })

        btnBack.setOnClickListener {
            when {
                lexicon.isOpen -> lexicon.back()
                inventory.isOpen -> inventory.back()
                else -> finish()
            }
        }
        btnInventory.setOnClickListener {
            if (game.isExploring && !lexicon.isOpen) {
                if (inventory.isOpen) {
                    inventory.back()
                } else {
                    saveNow()
                    inventory.show(game)
                }
            }
        }
        // Retour : ferme d'abord le lexique, puis l'inventaire, s'ils sont ouverts
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    lexicon.isOpen   -> lexicon.back()
                    inventory.isOpen -> inventory.back()
                    else             -> finish()
                }
            }
        })

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
        AlertDialog.Builder(this, R.style.Theme_Dungeon_Dialog)
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
        saveNow()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        sfx.start()
        music.start(game.floor)
    }

    override fun onDestroy() {
        super.onDestroy()
        saveHandler.removeCallbacks(deferredSave)
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

        g.onFloorChanged = { floor -> music.onFloorChanged(floor); saveBestFloorIfBetter(floor); saveNow() }
        g.onCombatStart  = { saveNow(); showCombat() }

        gameView.onMove          = { dx, dy -> g.tryMove(dx, dy); scheduleStateSave(); refresh() }
        gameView.onCloseStairs   = { g.closeStairs(); scheduleStateSave(); refresh() }
        gameView.onDescend       = { sfx.onDescend(); g.descend(); saveNow(); refresh() }
        gameView.onEquipItem     = { g.equipPendingDrop(); saveNow(); refresh() }
        gameView.onStashDrop     = { g.stashPendingDrop(); saveNow(); refresh() }

        gameView.onRestartAfterDeath = { atCheckpoint ->
            g.restartAfterDeath(atCheckpoint)
            saveNow()
            refresh()
        }
        gameView.onCampfireHeld = {
            AlertDialog.Builder(this)
                .setTitle(R.string.roguelike_camp_menu_title)
                .setItems(arrayOf(getString(R.string.roguelike_camp_menu_checkpoint, g.checkpoint),
                    getString(R.string.roguelike_camp_menu_regenerate, g.floor))) { _, choice ->
                    if (choice == 0) g.returnToCheckpoint() else g.regenerateCurrentFloor()
                    saveNow()
                    refresh()
                }.show()
        }

        combatView.onStrike    = { crit -> sfx.onPlayerAttack(crit) }
        combatView.onEnemyDied = { sfx.onMonsterDied() }
        combatView.onHeroHit   = { sfx.onPlayerHit() }
        combatView.onParry     = { perfect -> sfx.onParry(perfect) }
        combatView.onFinished  = {
            g.finishCombat()
            saveNow()
            // Un poursuivant tout proche a pu relancer un combat pendant finishCombat
            if (g.combat == null) combatView.visibility = View.GONE
            refresh()
        }

        combatView.visibility = View.GONE
        inventory.hide()
        lexicon.hide()
        refresh()
    }

    private fun scheduleStateSave() {
        saveHandler.removeCallbacks(deferredSave)
        saveHandler.postDelayed(deferredSave, 1_000L)
    }

    private fun saveNow() {
        saveHandler.removeCallbacks(deferredSave)
        SaveManager.saveImmediate(this, game)
    }

    private fun showCombat() {
        val c = game.combat ?: return
        combatView.visibility = View.VISIBLE
        combatView.start(c, game.heroSpritePath)
    }

    private fun refresh() {
        gameView.invalidate()
        val h = game.hero
        tvFloorLevel.text = getString(R.string.roguelike_banner_floor,
            getString(R.string.roguelike_region_floor, game.floor,
                getString(game.level.themeAt(game.playerPos.x, game.playerPos.y).label)),
            getString(R.string.roguelike_map_format, getString(game.level.format.label), game.level.w, game.level.h))
        btnInventory.text = getString(R.string.roguelike_banner_health,
            h.archetype?.let { getString(it.labelRes) } ?: getString(R.string.inv_no_class),
            DungeonNumbers.format(this, h.hp), DungeonNumbers.format(this, h.maxHp))
        btnInventory.isEnabled = game.isExploring
        healthBar.progress = (h.hp.toDouble() / h.maxHp.coerceAtLeast(1) * 1000).toInt().coerceIn(0, 1000)
    }
}
