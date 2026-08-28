package com.Atom2Universe.app.games.trebuchet

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        const val KEY_MACHINES = "machines"
        const val KEY_GHOSTS = "ghost_limit"

        // Les entrées du menu des machines. Les machines enregistrées prennent les
        // numéros suivants, dans l'ordre où elles s'affichent.
        const val ID_DEFAULT = 0
        const val ID_SAVE_AS = 1
        const val ID_DELETE = 2
        const val ID_FIRST_PRESET = 10

        // Les entrées du menu des réglages.
        const val ID_ARCADE = 0
        const val ID_REALISTE = 1
        const val ID_CLEAN = 2
        const val ID_GHOSTS = 3
        const val ID_FIRST_GHOST_CHOICE = 20
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
    private lateinit var machinesButton: TextView

    /** Les machines mises de côté par le joueur, la plus récente en tête. */
    private var machines = emptyList<MachinePreset>()

    private var best = 0f

    /** La graine du site en cours. Tout le niveau tient dedans. */
    private var levelSeed = 1L

    /** Le nom sous lequel on a chargé ou enregistré pour la dernière fois. */
    private var lastMachineName = ""

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

        gameView.game.ghostLimit = prefs.getInt(KEY_GHOSTS, TrebuchetRules.GHOST_HISTORY)
        gameView.listener = this
        // Les roulettes règlent la même machine que le doigt, et préviennent quand
        // elles tournent : les deux moyens restent en phase sans se connaître.
        wheels.game = gameView.game
        wheels.onValueChanged = { updateUi() }

        findViewById<ImageButton>(R.id.trebuchet_btn_back).setOnClickListener { finish() }
        machines = MachineLibrary.decode(prefs.getString(KEY_MACHINES, "") ?: "")
        machinesButton = findViewById(R.id.trebuchet_btn_machines)
        machinesButton.setOnClickListener { showMachinesMenu() }
        // Appui long : on passe au site suivant sans l'avoir rasé. Indispensable pour
        // essayer, et sans doute à remplacer par un vrai bouton quand le mode aura
        // trouvé sa forme.
        machinesButton.setOnLongClickListener { nextLevel(); true }
        fireButton.setOnClickListener { onFireButton() }
        findViewById<ImageButton>(R.id.trebuchet_btn_settings)
            .setOnClickListener { showSettingsMenu(it) }

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

    /**
     * Les réglages du jeu — par opposition aux réglages de la machine, qui se font sur
     * la machine elle-même.
     *
     * Il n'y en a que deux, et c'est bien ainsi : le tempérament des constructions, et
     * l'effacement de la mémoire des tirs. Le second est ici précisément **parce qu'on
     * ne s'en sert jamais** — c'est un ménage, pas un geste de jeu, et un ménage n'a
     * rien à faire sur la barre du bas à côté du bouton de tir.
     */
    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        menu.add(1, ID_ARCADE, 0, getString(R.string.trebuchet_style_arcade))
        menu.add(1, ID_REALISTE, 1, getString(R.string.trebuchet_style_realistic))
        // Une case cochée dit lequel des deux est en cours ; deux entrées valent mieux
        // qu'un interrupteur qui n'annonce pas ce qu'il va faire.
        menu.setGroupCheckable(1, true, true)
        menu.findItem(
            if (TargetRules.style == TargetStyle.ARCADE) ID_ARCADE else ID_REALISTE
        ).isChecked = true

        // La profondeur de mémoire, dans son propre tiroir : c'est un réglage qu'on
        // pose une fois et qu'on ne rouvre plus.
        val sous = menu.addSubMenu(
            0, ID_GHOSTS, 2,
            getString(R.string.trebuchet_ghost_limit, gameView.game.ghostLimit)
        )
        for ((i, n) in TrebuchetRules.GHOST_CHOICES.withIndex()) {
            sous.add(2, ID_FIRST_GHOST_CHOICE + i, i, n.toString())
        }
        sous.setGroupCheckable(2, true, true)
        val choisi = TrebuchetRules.GHOST_CHOICES.indexOf(gameView.game.ghostLimit)
        if (choisi >= 0) sous.findItem(ID_FIRST_GHOST_CHOICE + choisi).isChecked = true

        val fantomes = gameView.game.ghosts.size
        menu.add(0, ID_CLEAN, 3, getString(R.string.trebuchet_clean_ghosts, fantomes))
            .isEnabled = fantomes > 0

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_ARCADE -> setStyle(TargetStyle.ARCADE)
                ID_REALISTE -> setStyle(TargetStyle.REALISTE)
                ID_CLEAN -> {
                    synchronized(gameView.game) { gameView.game.clearGhosts() }
                    toast(getString(R.string.trebuchet_clean_done))
                }
                else -> {
                    val i = item.itemId - ID_FIRST_GHOST_CHOICE
                    TrebuchetRules.GHOST_CHOICES.getOrNull(i)?.let { setGhostLimit(it) }
                }
            }
            true
        }
        popup.show()
    }

    /** Combien de tirs le joueur veut garder à l'écran. */
    private fun setGhostLimit(n: Int) {
        synchronized(gameView.game) { gameView.game.ghostLimit = n }
        prefs.edit { putInt(KEY_GHOSTS, n) }
        toast(getString(R.string.trebuchet_ghost_limit, n))
    }

    /**
     * Change le tempérament et refait le site avec.
     *
     * Le site se refait, et il n'y a pas moyen de faire autrement : la masse et la
     * solidité d'une pierre sont fixées à sa naissance. On ne les change pas sous les
     * pieds du joueur.
     */
    private fun setStyle(style: TargetStyle) {
        if (style == TargetRules.style) return
        TargetRules.style = style
        prefs.edit { putString(KEY_STYLE, style.name) }
        loadLevel(levelSeed)
    }

    /** Le nom du tempérament, tel qu'il s'affiche. */
    private fun styleLabel(): String =
        if (TargetRules.style == TargetStyle.ARCADE) "ARCADE" else "RÉALISTE"

    // ── L'atelier ─────────────────────────────────────────────────────────────

    /**
     * Le menu des machines : celle d'origine, celles qu'on a mises de côté, et de quoi
     * ranger celle qu'on tient.
     *
     * Il a remplacé un bouton « Réinitialiser », lequel ne savait faire qu'une chose et
     * la faisait brutalement : effacer une demi-heure de réglages. Le même bouton rend
     * maintenant ce geste-là **et** son contraire — remettre la main sur une machine
     * qu'on avait trouvée bonne.
     */
    private fun showMachinesMenu() {
        val popup = PopupMenu(this, machinesButton)
        val menu = popup.menu
        menu.add(0, ID_DEFAULT, 0, getString(R.string.trebuchet_machine_default))
        for ((i, m) in machines.withIndex()) menu.add(0, ID_FIRST_PRESET + i, i + 1, m.name)
        menu.add(0, ID_SAVE_AS, machines.size + 1, getString(R.string.trebuchet_machine_save_as))
        if (machines.isNotEmpty()) {
            menu.add(0, ID_DELETE, machines.size + 2, getString(R.string.trebuchet_machine_delete))
        }
        popup.setOnMenuItemClickListener { item ->
            when (val id = item.itemId) {
                ID_DEFAULT -> {
                    // On repart de zéro : le champ « enregistrer sous » aussi, sinon on
                    // proposerait d'écraser une machine dont il ne reste rien.
                    lastMachineName = ""
                    loadMachine(MachineConfig(), getString(R.string.trebuchet_machine_default))
                }
                ID_SAVE_AS -> askMachineName()
                ID_DELETE -> showDeleteMenu()
                else -> machines.getOrNull(id - ID_FIRST_PRESET)?.let {
                    // Charger une machine, c'est reprendre son nom : la prochaine
                    // sauvegarde proposera de la mettre à jour, qui est ce qu'on veut
                    // faire après l'avoir sortie pour la retoucher.
                    lastMachineName = it.name
                    loadMachine(it.config, it.name)
                }
            }
            true
        }
        popup.show()
    }

    /** Pose une machine sur le terrain et le dit, parce que ça ne se voit pas toujours. */
    private fun loadMachine(cfg: MachineConfig, name: String) {
        gameView.clearSelection()
        synchronized(gameView.game) { gameView.game.loadConfig(cfg) }
        gameView.syncPhase()
        updateUi()
        toast(getString(R.string.trebuchet_machine_loaded, name))
    }

    /**
     * Demande un nom et range la machine du moment.
     *
     * Le champ arrive **prérempli** avec le nom de la dernière machine chargée : neuf
     * fois sur dix, on enregistre une machine qu'on vient d'améliorer, et réécrire son
     * nom au clavier à chaque fois serait une punition. Reprendre le même nom remplace,
     * ce qui est très exactement ce qu'on voulait faire.
     */
    private fun askMachineName() {
        val field = EditText(this).apply {
            setText(lastMachineName)
            setSelection(text.length)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.trebuchet_machine_name)
            .setView(field)
            .setPositiveButton(R.string.trebuchet_machine_save) { _, _ ->
                saveMachine(field.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveMachine(rawName: String) {
        val name = MachinePreset.clean(rawName)
        if (name.isEmpty()) {
            toast(getString(R.string.trebuchet_machine_noname))
            return
        }
        machines = MachineLibrary.put(machines, MachinePreset(name, gameView.game.config))
        prefs.edit { putString(KEY_MACHINES, MachineLibrary.encode(machines)) }
        lastMachineName = name
        toast(getString(R.string.trebuchet_machine_saved, name))
    }

    /** Le second menu : celui dont on ne sort rien, on y jette. */
    private fun showDeleteMenu() {
        val popup = PopupMenu(this, machinesButton)
        for ((i, m) in machines.withIndex()) popup.menu.add(0, i, i, m.name)
        popup.setOnMenuItemClickListener { item ->
            machines.getOrNull(item.itemId)?.let { m ->
                machines = MachineLibrary.remove(machines, m.name)
                prefs.edit { putString(KEY_MACHINES, MachineLibrary.encode(machines)) }
                toast(getString(R.string.trebuchet_machine_deleted, m.name))
            }
            true
        }
        popup.show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    /**
     * Le bouton de tir tire, et rien d'autre.
     *
     * Il se changeait en « site suivant » dès que le site était rasé, ce qui faisait de
     * la victoire une porte qui se referme : le joueur voulait souvent retirer un coup
     * dans les ruines pour finir le travail, ou simplement regarder. Un site rasé reste
     * donc un site où l'on tire, et on n'en change que sur demande — appui long sur le
     * bouton des machines.
     */
    private fun onFireButton() {
        val game = gameView.game
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
            fmt2(cfg.slingLength)
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
                (game.targets.score * 100f).toInt(),
                (game.targets.winRatio * 100f).toInt(),
                game.shotCount
            ) + " · " + styleLabel()
            best > 0f -> getString(R.string.trebuchet_best, fmt(best))
            else -> ""
        }

        val building = game.phase == TrebuchetGame.Phase.BUILD
        fireButton.text = getString(
            if (building) R.string.trebuchet_btn_release else R.string.trebuchet_btn_adjust
        )

        statusText.visibility =
            if (gameView.selected == TrebuchetView.Part.NONE) View.VISIBLE else View.GONE
        // La victoire passe devant tout le reste, et elle dit quoi faire ensuite : rien
        // ne se déclenche tout seul, et un joueur qui ne sait pas comment continuer est
        // un joueur bloqué sur un écran de fête.
        if (game.level != null && game.targets.cleared) {
            statusText.text = getString(
                R.string.trebuchet_win,
                game.shotCount,
                getString(R.string.trebuchet_btn_machines)
            )
            return
        }
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
                    fmt2(cfg.slingLength), getString(shotLabel(cfg.projectile))
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

    /** La fronde s'affiche au centimètre : c'est à ce grain-là qu'elle se règle. */
    private fun fmt2(v: Float): String = String.format("%.2f", v)

    /** Le nom du projectile chargé, tel qu'il s'affiche dans le bandeau. */
    private fun shotLabel(kind: Projectile): Int = when (kind) {
        Projectile.BOULET -> R.string.trebuchet_shot_ball
        Projectile.FRAGMENTATION -> R.string.trebuchet_shot_cluster
        Projectile.BOMBE -> R.string.trebuchet_shot_bomb
    }
}
