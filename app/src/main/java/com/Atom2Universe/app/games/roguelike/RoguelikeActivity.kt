package com.Atom2Universe.app.games.roguelike

import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

class RoguelikeActivity : ThemedActivity() {

    private lateinit var gameView:     RoguelikeView
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
        val summary = SaveManager.saveSummary(this) ?: "partie en cours"
        AlertDialog.Builder(this, R.style.Theme_A2U_Dialog)
            .setTitle("Donjon")
            .setMessage("Une aventure est en cours :\n$summary\n\nQue veux-tu faire ?")
            .setCancelable(false)
            .setPositiveButton("Continuer") { _, _ ->
                val saved = SaveManager.load(this)
                attachGame(saved ?: RoguelikeGame())
            }
            .setNegativeButton("Nouvelle partie") { _, _ ->
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
        if (game.phase == GamePhase.PLAYING) SaveManager.save(this, game)
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        sfx.start()
        music.start(game.player.floor)
    }

    override fun onDestroy() {
        super.onDestroy()
        music.stop()
        sfx.stop()
    }

    // ── Attache / détache un game ────────────────────────────────────────────────

    private fun attachGame(g: RoguelikeGame) {
        game          = g
        gameView.game = g

        g.onPlayerAttack = { isCrit -> sfx.onPlayerAttack(isCrit) }
        g.onPlayerHit    = { sfx.onPlayerHit() }
        g.onMonsterDied  = { sfx.onMonsterDied() }
        g.onDescend      = { sfx.onDescend() }
        g.onFloorChanged = { floor -> music.onFloorChanged(floor) }

        gameView.onMove = { dx, dy ->
            g.tryMove(dx, dy); refresh()
        }
        gameView.onUseItem = {
            if (g.phase == GamePhase.PLAYING && g.player.inventory.isNotEmpty()) {
                g.useItem(0); refresh()
            }
        }
        gameView.onDescend = {
            if (g.phase == GamePhase.PLAYING && g.onStairsTile()) {
                g.openShop(); refresh()
            }
        }
        gameView.onBuyShopItem    = { item -> g.buyShopItem(item); refresh() }
        gameView.onConfirmDescend = { g.closeShopAndDescend(); refresh() }
        gameView.onEquipItem      = { g.equipPendingDrop(); refresh() }
        gameView.onIgnoreDrop     = { g.ignorePendingDrop(); refresh() }

        gameView.setOnTouchListener { _, event ->
            val consumed = gameView.onTouchEvent(event)
            if (consumed) refresh()
            // Redémarre sur tap après game over
            if (g.phase == GamePhase.GAME_OVER
                && !g.shopOpen && g.pendingEquipDrop == null
                && event.action == android.view.MotionEvent.ACTION_UP) {
                restartGame()
            }
            consumed
        }

        refresh()
    }

    private fun restartGame() {
        SaveManager.clear(this)
        attachGame(RoguelikeGame())
    }

    private fun refresh() {
        gameView.invalidate()
        val p = game.player
        tvGold.text       = "${p.gold} or"
        tvFloorLevel.text = "Étage ${p.floor}"
    }
}
