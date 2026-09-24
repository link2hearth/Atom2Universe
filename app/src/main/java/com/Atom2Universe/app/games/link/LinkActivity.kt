package com.Atom2Universe.app.games.link

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * Intrication (ancien « Link ») : une partie de [LinkGame.FLOORS] étages. À chaque étage,
 * poser toutes les pièces de la main sur des atomes excités jusqu'à les ramener tous au
 * repos, sachant que deux atomes intriqués descendent ensemble. Le dernier étage est le
 * boss.
 *
 * Les points tombent à chaque étage fini ; les neutrinos, une fois, à la fin de la
 * partie, selon le score. La partie en cours est sauvegardée à chaque pose.
 */
class LinkActivity : ThemedActivity(), LinkBoardView.Listener {

    companion object {
        private const val PREFS = "link_save"
        private const val KEY_SAVE = "save"
        /** Le record, synchronisé avec les autres records de jeux. */
        const val KEY_BEST = "best_score"
    }

    private val game = LinkGame()

    private lateinit var boardView: LinkBoardView
    private lateinit var handView: LinkHandView
    private lateinit var floorText: TextView
    private lateinit var scoreText: TextView
    private lateinit var overlay: View
    private lateinit var overlayTitle: TextView
    private lateinit var overlayText: TextView
    private lateinit var breakdown: GridLayout
    private lateinit var overlayButton: MaterialButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRestart: ImageButton

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_link_game)
        enableImmersiveMode()

        boardView     = findViewById(R.id.link_board_view)
        handView      = findViewById(R.id.link_hand_view)
        floorText     = findViewById(R.id.link_level)
        scoreText     = findViewById(R.id.link_score)
        overlay       = findViewById(R.id.link_victory_overlay)
        overlayTitle  = findViewById(R.id.link_victory_title)
        overlayText   = findViewById(R.id.link_victory_moves)
        breakdown     = findViewById(R.id.link_run_breakdown)
        overlayButton = findViewById(R.id.link_victory_new_btn)
        btnUndo       = findViewById(R.id.link_btn_undo)
        btnRestart    = findViewById(R.id.link_btn_restart)

        boardView.game = game
        boardView.listener = this
        handView.game = game

        findViewById<ImageButton>(R.id.link_back_button).setOnClickListener { finish() }
        btnUndo.setOnClickListener { undo() }
        btnRestart.setOnClickListener { showRestartMenu() }
        overlayButton.setOnClickListener { if (game.isRunOver) newRun() else nextFloor() }

        val restored = prefs.getString(KEY_SAVE, null)?.let { game.deserialize(it) } == true
        if (!restored) game.newRun()
        refreshAll()
        when {
            game.isRunOver -> showRunOver(newBest = false, neutrinos = NeutrinoRewards.link(game.totalScore))
            game.floorScored -> showFloorDone()
        }
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    private fun refreshAll() {
        boardView.notifyBoardChanged()
        handView.highlighted = -1
        handView.refresh()
        floorText.text = if (game.isBoss) getString(R.string.link_floor_boss)
        else getString(R.string.link_floor, game.floor, LinkGame.FLOORS)
        floorText.setTextColor(if (game.isBoss) 0xFFFF7043.toInt() else 0xFF4FC3F7.toInt())
        updateScore()
        updateButtons()
    }

    private fun updateScore() {
        scoreText.text = getString(R.string.link_score, game.totalScore)
    }

    private fun updateButtons() {
        val playing = !game.floorScored
        btnUndo.isEnabled = playing && game.placements.isNotEmpty()
        btnUndo.alpha = if (btnUndo.isEnabled) 1f else 0.35f
    }

    // ── Commandes ─────────────────────────────────────────────────────────────────
    private fun undo() {
        val last = game.undo() ?: return
        boardView.flashUndo(last.cells)
        handView.invalidate()
        updateButtons()
        save()
    }

    /** Recommencer l'étage (ça coûte des points) ou abandonner toute la partie. */
    private fun showRestartMenu() {
        val menu = PopupMenu(this, btnRestart)
        val canRestartFloor = !game.floorScored && game.placements.isNotEmpty()
        if (canRestartFloor) menu.menu.add(0, 1, 0, R.string.link_restart_floor)
        menu.menu.add(0, 2, 1, R.string.link_new_run)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { game.restart(); refreshAll(); save() }
                2 -> confirmNewRun()
            }
            true
        }
        menu.show()
    }

    private fun confirmNewRun() {
        // Une partie finie ou à peine commencée n'a rien à perdre : pas de question.
        if (game.isRunOver || (game.floor == 1 && game.placements.isEmpty())) { newRun(); return }
        AlertDialog.Builder(this)
            .setMessage(R.string.link_new_run_confirm)
            .setPositiveButton(R.string.link_new_run) { _, _ -> newRun() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun newRun() {
        overlay.visibility = View.GONE
        game.newRun()
        refreshAll()
        save()
    }

    private fun nextFloor() {
        overlay.visibility = View.GONE
        game.nextFloor()
        refreshAll()
        save()
    }

    // ── LinkBoardView.Listener ────────────────────────────────────────────────────
    override fun onSelectionChanged(matchingPiece: Int) {
        handView.highlighted = matchingPiece
    }

    override fun onPlaced() {
        handView.invalidate()
        updateButtons()
        save()
    }

    override fun onSolved() {
        game.scoreFloor()
        updateScore()
        updateButtons()
        if (game.isRunOver) finishRun()
        save()
    }

    override fun onVictoryShown() {
        if (game.isRunOver) showRunOver(newBest = lastRunWasBest, neutrinos = NeutrinoRewards.link(game.totalScore))
        else showFloorDone()
    }

    // ── Fin d'étage, fin de partie ────────────────────────────────────────────────
    private var lastRunWasBest = false

    /** Le dernier étage vient de tomber : neutrinos et record, une seule fois. */
    private fun finishRun() {
        val score = game.totalScore
        NeutrinoRepository(this).addBalance(NeutrinoRewards.link(score))
        val best = prefs.getInt(KEY_BEST, 0)
        lastRunWasBest = score > best
        if (lastRunWasBest) prefs.edit { putInt(KEY_BEST, score) }
    }

    private fun showFloorDone() {
        val i = game.floor - 1
        val points = game.floorScores[i].coerceAtLeast(0)
        overlayTitle.setText(R.string.link_victory_title)
        overlayText.text = getString(
            if (game.perfectFloors[i]) R.string.link_floor_perfect else R.string.link_floor_points, points
        )
        breakdown.visibility = View.GONE
        overlayButton.setText(R.string.link_next_floor)
        overlay.visibility = View.VISIBLE
    }

    private fun showRunOver(newBest: Boolean, neutrinos: Int) {
        val score = game.totalScore
        val perfect = game.perfectFloors.count { it }
        overlayTitle.setText(R.string.link_run_over)
        val head = if (newBest) getString(R.string.link_run_new_best, score)
        else getString(R.string.link_run_summary, score, prefs.getInt(KEY_BEST, score))
        val tail = resources.getQuantityString(R.plurals.link_run_perfect, perfect, perfect, LinkGame.FLOORS, neutrinos)
        overlayText.text = "$head\n$tail"
        fillBreakdown()
        overlayButton.setText(R.string.link_new_run)
        overlay.visibility = View.VISIBLE
    }

    /** Les points de chaque étage ; les parfaits en or. */
    private fun fillBreakdown() {
        breakdown.removeAllViews()
        val pad = (6 * resources.displayMetrics.density).toInt()
        for (i in 0 until LinkGame.FLOORS) {
            breakdown.addView(TextView(this).apply {
                text = getString(R.string.link_floor_cell, i + 1, game.floorScores[i].coerceAtLeast(0))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(pad, pad / 2, pad, pad / 2)
                setTextColor(if (game.perfectFloors[i]) 0xFFFFD54F.toInt() else 0xFFB8C0F0.toInt())
            })
        }
        breakdown.visibility = View.VISIBLE
    }

    private fun save() {
        prefs.edit { putString(KEY_SAVE, game.serialize()) }
    }
}
