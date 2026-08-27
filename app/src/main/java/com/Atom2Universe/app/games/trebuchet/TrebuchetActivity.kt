package com.Atom2Universe.app.games.trebuchet

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.edit
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.util.enableImmersiveMode

/**
 * Le trébuchet : on construit une machine de jet, on décroche la détente, et la
 * portée est la conséquence de la géométrie choisie. On ne vise jamais — on règle
 * la fronde et le crochet, et la physique fait le reste.
 */
class TrebuchetActivity : ThemedActivity(), TrebuchetView.Listener {

    private companion object {
        const val PREFS_NAME = "trebuchet_game"
        const val KEY_BEST = "best_distance"
    }

    private lateinit var gameView: TrebuchetView
    private lateinit var statusText: TextView
    private lateinit var specsText: TextView
    private lateinit var bestText: TextView
    private lateinit var fireButton: TextView
    private lateinit var pickBeam: TextView
    private lateinit var pickPost: TextView
    private lateinit var pickRatio: TextView
    private lateinit var pickWeight: TextView
    private lateinit var pickHang: TextView
    private lateinit var pickPin: TextView
    private lateinit var prefs: SharedPreferences

    private var best = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trebuchet)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        best = prefs.getFloat(KEY_BEST, 0f)

        gameView = findViewById(R.id.trebuchet_view)
        statusText = findViewById(R.id.trebuchet_status)
        specsText = findViewById(R.id.trebuchet_specs)
        bestText = findViewById(R.id.trebuchet_best)
        fireButton = findViewById(R.id.trebuchet_btn_fire)
        pickBeam = findViewById(R.id.trebuchet_pick_beam)
        pickPost = findViewById(R.id.trebuchet_pick_post)
        pickRatio = findViewById(R.id.trebuchet_pick_ratio)
        pickWeight = findViewById(R.id.trebuchet_pick_weight)
        pickHang = findViewById(R.id.trebuchet_pick_hang)
        pickPin = findViewById(R.id.trebuchet_pick_pin)

        gameView.listener = this

        findViewById<ImageButton>(R.id.trebuchet_btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.trebuchet_btn_reset).setOnClickListener { resetMachine() }
        fireButton.setOnClickListener { onFireButton() }

        // Appui court : valeur suivante. Appui long : valeur précédente.
        bindPick(pickBeam) { d -> gameView.game.cycleBeam(d) }
        bindPick(pickPost) { d -> gameView.game.cyclePost(d) }
        bindPick(pickRatio) { d -> gameView.game.cycleRatio(d) }
        bindPick(pickWeight) { d -> gameView.game.cycleWeight(d) }
        bindPick(pickHang) { d -> gameView.game.cycleHang(d) }
        bindPick(pickPin) { d -> gameView.game.cyclePin(d) }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    private fun bindPick(view: TextView, change: (Int) -> Unit) {
        view.setOnClickListener { applySetting(change, +1) }
        view.setOnLongClickListener {
            applySetting(change, -1)
            true
        }
    }

    /** Un réglage ne se touche qu'en phase de pose : sinon on remet la machine à zéro. */
    private fun applySetting(change: (Int) -> Unit, delta: Int) {
        val game = gameView.game
        synchronized(game) {
            if (game.phase != TrebuchetGame.Phase.BUILD) game.rebuild()
            change(delta)
        }
        gameView.syncPhase()
        updateUi()
    }

    private fun resetMachine() {
        synchronized(gameView.game) { gameView.game.reset() }
        gameView.syncPhase()
        updateUi()
    }

    private fun onFireButton() {
        val game = gameView.game
        when (game.phase) {
            TrebuchetGame.Phase.BUILD -> {
                synchronized(game) { game.release() }
                gameView.syncPhase()
            }
            // Pendant le vol aussi : dès qu'on voit que c'est raté, on reprend la
            // main sans attendre que tout soit retombé.
            else -> {
                synchronized(game) { game.rebuild() }
                gameView.syncPhase()
            }
        }
        updateUi()
    }

    // ── Retours de la vue ─────────────────────────────────────────────────────

    override fun onShotFinished() {
        val game = gameView.game
        if (game.shotDistance > best) {
            best = game.shotDistance
            prefs.edit { putFloat(KEY_BEST, best) }
        }
        updateUi()
    }

    override fun onMachineChanged() {
        updateUi()
    }

    // ── Affichage ─────────────────────────────────────────────────────────────

    private fun updateUi() {
        val game = gameView.game
        val cfg = game.config

        pickBeam.text = getString(R.string.trebuchet_beam, fmt(cfg.beamLength))
        pickPost.text = getString(R.string.trebuchet_post, fmt(cfg.pivotHeight))
        pickRatio.text = getString(R.string.trebuchet_ratio, cfg.leverRatio.toInt())
        pickWeight.text = getString(R.string.trebuchet_weight, cfg.counterweightMass.toInt())
        pickHang.text = getString(R.string.trebuchet_hang, fmt(cfg.hangLength))
        pickPin.text = getString(R.string.trebuchet_pin, cfg.pinAngleDeg.toInt())

        specsText.text = getString(
            R.string.trebuchet_specs,
            cfg.cockAngleDeg.toInt(),
            (cfg.storedEnergy / 1000f).toInt(),
            fmt(cfg.slingLength)
        )
        bestText.text = if (best > 0f) getString(R.string.trebuchet_best, fmt(best)) else ""

        val building = game.phase == TrebuchetGame.Phase.BUILD
        fireButton.text = getString(
            if (building) R.string.trebuchet_btn_release else R.string.trebuchet_btn_adjust
        )

        statusText.text = when (game.phase) {
            TrebuchetGame.Phase.BUILD -> getString(R.string.trebuchet_status_build)
            TrebuchetGame.Phase.RESULT ->
                if (game.shotDistance <= 0f) {
                    getString(R.string.trebuchet_status_backwards)
                } else {
                    getString(
                        R.string.trebuchet_status_result,
                        fmt(game.shotDistance),
                        fmt(game.launchSpeed),
                        game.launchAngleDeg.toInt(),
                        fmt(game.peakHeight),
                        (game.efficiency * 100f).toInt()
                    )
                }
            else -> getString(R.string.trebuchet_status_flight)
        }
        statusText.visibility = View.VISIBLE
    }

    private fun fmt(v: Float): String = String.format("%.1f", v)
}
