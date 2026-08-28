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
        const val KEY_SEED = "level_seed"
        const val KEY_STYLE = "target_style"
    }

    private lateinit var gameView: TrebuchetView
    private lateinit var statusText: TextView
    private lateinit var specsText: TextView
    private lateinit var bestText: TextView
    private lateinit var fireButton: TextView
    private lateinit var wheels: TrebuchetWheelBubble
    private lateinit var infoTitle: TextView
    private lateinit var infoTip: TextView
    private lateinit var prefs: SharedPreferences

    private var best = 0f

    /** La graine du site en cours. Tout le niveau tient dedans. */
    private var levelSeed = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trebuchet)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        best = prefs.getFloat(KEY_BEST, 0f)
        levelSeed = prefs.getLong(KEY_SEED, 1L)
        TargetRules.style = runCatching {
            TargetStyle.valueOf(prefs.getString(KEY_STYLE, null) ?: TargetStyle.ARCADE.name)
        }.getOrDefault(TargetStyle.ARCADE)

        gameView = findViewById(R.id.trebuchet_view)
        statusText = findViewById(R.id.trebuchet_status)
        specsText = findViewById(R.id.trebuchet_specs)
        bestText = findViewById(R.id.trebuchet_best)
        fireButton = findViewById(R.id.trebuchet_btn_fire)
        infoTitle = findViewById(R.id.trebuchet_info_title)
        infoTip = findViewById(R.id.trebuchet_info_tip)
        wheels = findViewById(R.id.trebuchet_wheels)

        gameView.listener = this
        // Les roulettes règlent la même machine que le doigt, et préviennent quand
        // elles tournent : les deux moyens restent en phase sans se connaître.
        wheels.game = gameView.game
        wheels.onValueChanged = { updateUi() }

        findViewById<ImageButton>(R.id.trebuchet_btn_back).setOnClickListener { finish() }
        val reset = findViewById<TextView>(R.id.trebuchet_btn_reset)
        reset.setOnClickListener { resetMachine() }
        // Appui long : on passe au site suivant sans l'avoir rasé. Indispensable pour
        // essayer, et sans doute à remplacer par un vrai bouton quand le mode aura
        // trouvé sa forme.
        reset.setOnLongClickListener { nextLevel(); true }
        fireButton.setOnClickListener { onFireButton() }
        // Le bandeau du site sert aussi d'interrupteur entre arcade et réaliste. Le
        // site se refait au passage : la masse et la solidité d'une pierre sont fixées
        // à sa naissance, on ne les change pas sous les pieds du joueur.
        bestText.setOnClickListener { toggleStyle() }

        loadLevel(levelSeed)
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
    }

    /** Charge le site de cette graine et rebande la machine devant. */
    private fun loadLevel(seed: Long) {
        levelSeed = seed
        prefs.edit { putLong(KEY_SEED, seed) }
        gameView.clearSelection()
        synchronized(gameView.game) { gameView.game.loadLevel(seed) }
        gameView.syncPhase()
        updateUi()
    }

    private fun nextLevel() = loadLevel(levelSeed + 1L)

    /** Passe d'un tempérament à l'autre et refait le site avec. */
    private fun toggleStyle() {
        TargetRules.style = when (TargetRules.style) {
            TargetStyle.ARCADE -> TargetStyle.REALISTE
            TargetStyle.REALISTE -> TargetStyle.ARCADE
        }
        prefs.edit { putString(KEY_STYLE, TargetRules.style.name) }
        loadLevel(levelSeed)
    }

    /** Le nom du tempérament, tel qu'il s'affiche. */
    private fun styleLabel(): String =
        if (TargetRules.style == TargetStyle.ARCADE) "ARCADE" else "RÉALISTE"

    private fun resetMachine() {
        gameView.clearSelection()
        synchronized(gameView.game) { gameView.game.reset() }
        gameView.syncPhase()
        updateUi()
    }

    private fun onFireButton() {
        val game = gameView.game
        // Site rasé : le bouton ne sert plus qu'à passer au suivant.
        if (game.level != null && game.targets.cleared) {
            nextLevel()
            return
        }
        gameView.clearSelection()
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

        updateInfoBand(cfg)
        wheels.showFor(gameView.selected)

        specsText.text = getString(
            R.string.trebuchet_specs,
            cfg.cockAngleDeg.toInt(),
            (cfg.storedEnergy / 1000f).toInt(),
            fmt(cfg.slingLength)
        )
        // La ligne du haut dit où on en est du site ; en bac à sable, elle garde le
        // record de portée.
        val lvl = game.level
        bestText.text = when {
            lvl != null && game.targets.cleared -> getString(
                R.string.trebuchet_level_cleared,
                TargetGenerator.label(lvl), game.shotCount
            ) + " · " + styleLabel()
            lvl != null -> getString(
                R.string.trebuchet_level,
                TargetGenerator.label(lvl),
                (game.targets.progress * 100f).toInt(),
                game.shotCount
            ) + " · " + styleLabel()
            best > 0f -> getString(R.string.trebuchet_best, fmt(best))
            else -> ""
        }

        val building = game.phase == TrebuchetGame.Phase.BUILD
        val cleared = game.level != null && game.targets.cleared
        fireButton.text = getString(
            when {
                cleared -> R.string.trebuchet_btn_next
                building -> R.string.trebuchet_btn_release
                else -> R.string.trebuchet_btn_adjust
            }
        )

        statusText.visibility =
            if (gameView.selected == TrebuchetView.Part.NONE) View.VISIBLE else View.GONE
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
    }

    /**
     * Le bandeau de la pièce tenue en main : son nom, ses valeurs vives, et une
     * phrase sur ce qu'elle échange contre quoi. Une seule phrase — c'est un jeu, pas
     * un manuel, et le joueur a la machine sous les yeux pour le reste.
     */
    private fun updateInfoBand(cfg: MachineConfig) {
        val selected = gameView.selected
        val visible = selected != TrebuchetView.Part.NONE
        infoTitle.visibility = if (visible) View.VISIBLE else View.GONE
        infoTip.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return

        infoTitle.text = when (selected) {
            TrebuchetView.Part.BEAM ->
                getString(R.string.trebuchet_sel_beam, fmt(cfg.beamLength), fmt(cfg.leverRatio))
            TrebuchetView.Part.POST -> getString(R.string.trebuchet_sel_post, fmt(cfg.pivotHeight))
            TrebuchetView.Part.WEIGHT -> getString(
                R.string.trebuchet_sel_weight,
                cfg.counterweightMass.toInt(), fmt(cfg.hangLength)
            )
            TrebuchetView.Part.PIN ->
                getString(R.string.trebuchet_sel_pin, cfg.pinAngleDeg.toInt())
            TrebuchetView.Part.SLING ->
                getString(
                    R.string.trebuchet_sel_sling_shot,
                    fmt(cfg.slingLength), getString(shotLabel(cfg.projectile))
                )
            TrebuchetView.Part.NONE -> ""
        }
        infoTip.setText(
            when (selected) {
                TrebuchetView.Part.BEAM -> R.string.trebuchet_tip_beam
                TrebuchetView.Part.POST -> R.string.trebuchet_tip_post
                TrebuchetView.Part.WEIGHT -> R.string.trebuchet_tip_weight
                TrebuchetView.Part.PIN -> R.string.trebuchet_tip_pin
                TrebuchetView.Part.SLING -> R.string.trebuchet_tip_sling
                TrebuchetView.Part.NONE -> R.string.trebuchet_status_build
            }
        )
    }

    private fun fmt(v: Float): String = String.format("%.1f", v)

    /** Le nom du projectile chargé, tel qu'il s'affiche dans le bandeau. */
    private fun shotLabel(kind: Projectile): Int = when (kind) {
        Projectile.BOULET -> R.string.trebuchet_shot_ball
        Projectile.LOURD -> R.string.trebuchet_shot_heavy
        Projectile.FRAGMENTATION -> R.string.trebuchet_shot_cluster
        Projectile.BOMBE -> R.string.trebuchet_shot_bomb
    }
}
