package com.Atom2Universe.app.games.roguelike

import android.os.Bundle
import android.widget.*
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode

class RoguelikeActivity : ThemedActivity() {

    private lateinit var gameView: RoguelikeView
    private lateinit var btnBack: ImageButton
    private lateinit var tvGold: TextView
    private lateinit var tvFloorLevel: TextView

    private var game = RoguelikeGame()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_roguelike)
        enableImmersiveMode()

        gameView     = findViewById(R.id.roguelike_view)
        btnBack      = findViewById(R.id.roguelike_btn_back)
        tvGold       = findViewById(R.id.roguelike_tv_gold)
        tvFloorLevel = findViewById(R.id.roguelike_tv_floorlevel)

        attachGame(game)
        btnBack.setOnClickListener { finish() }
    }

    private fun attachGame(g: RoguelikeGame) {
        game = g
        gameView.game = g

        gameView.onMove = { dx, dy ->
            g.tryMove(dx, dy)
            refresh()
        }
        gameView.onUseItem = {
            if (g.phase == GamePhase.PLAYING && g.player.inventory.isNotEmpty()) {
                g.useItem(0)
                refresh()
            }
        }
        gameView.onDescend = {
            if (g.phase == GamePhase.PLAYING && g.onStairsTile()) {
                if (g.player.floor >= RoguelikeGame.MAX_FLOORS) {
                    NeutrinoRepository(this).addBalance(10)
                    g.tryDescend()   // déclenche VICTORY directement
                } else {
                    g.openShop()
                }
                refresh()
            }
        }
        gameView.onBuyShopItem = { item ->
            g.buyShopItem(item)
            refresh()
        }
        gameView.onConfirmDescend = {
            g.closeShopAndDescend()
            refresh()
        }

        gameView.setOnTouchListener { _, event ->
            val consumed = gameView.onTouchEvent(event)
            if (consumed) refresh()
            if (g.phase != GamePhase.PLAYING && !g.shopOpen
                && event.action == android.view.MotionEvent.ACTION_UP) {
                restartGame()
            }
            consumed
        }

        refresh()
    }

    private fun restartGame() {
        attachGame(RoguelikeGame())
    }

    private fun refresh() {
        gameView.invalidate()
        val p = game.player
        tvGold.text = "${p.gold} g"
        tvFloorLevel.text = "Floor ${p.floor} / ${RoguelikeGame.MAX_FLOORS}"
    }
}
