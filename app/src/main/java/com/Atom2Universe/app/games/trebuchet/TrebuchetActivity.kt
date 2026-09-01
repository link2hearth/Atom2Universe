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
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineConfig
import com.Atom2Universe.app.games.trebuchet.gears.GearEditorBubble
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineLibrary
import com.Atom2Universe.app.games.trebuchet.gears.GearMachinePreset
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineRules
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineView
import com.Atom2Universe.app.games.trebuchet.gears.GearLinkKind
import com.Atom2Universe.app.games.trebuchet.gears.GearWheelMaterial
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Le trébuchet : on construit une machine de jet, on décroche la détente, et la
 * portée est la conséquence de la géométrie choisie. On ne vise jamais — on règle
 * la fronde et le crochet, et la physique fait le reste.
 */
class TrebuchetActivity : ThemedActivity(), TrebuchetView.Listener, GearMachineView.Listener {

    private companion object {
        const val PREFS_NAME = "trebuchet_game"
        const val KEY_BEST = "best_distance"
        const val KEY_SEED = "level_seed"
        const val KEY_STYLE = "target_style"
        const val KEY_MACHINES = "machines"
        const val KEY_GEAR_MACHINES = "gear_machines"
        const val KEY_GHOSTS = "ghost_limit"
        const val KEY_SOUND = "sound_enabled"

        // Les entrées du menu des machines. Les machines enregistrées prennent les
        // numéros suivants, dans l'ordre où elles s'affichent.
        const val ID_GROUP_TREBUCHET = 10
        const val ID_GROUP_GEARS = 20
        const val ID_TREBUCHET_DEFAULT = 100
        const val ID_TREBUCHET_DELETE = 101
        const val ID_GEAR_DEFAULT = 200
        const val ID_GEAR_DELETE = 201
        const val ID_SAVE_AS = 300
        const val ID_FIRST_PRESET = 1_000
        const val ID_FIRST_GEAR_PRESET = 2_000

        const val ID_GEAR_ADD = 100
        const val ID_GEAR_LAYER_DOWN = 110
        const val ID_GEAR_LAYER_UP = 111
        const val ID_GEAR_FLYWHEEL = 113
        const val ID_GEAR_LAUNCHER = 115
        const val ID_GEAR_ANGLE_DOWN = 116
        const val ID_GEAR_ANGLE_UP = 117
        const val ID_GEAR_STOP = 118
        const val ID_BELT_OPEN = 119
        const val ID_BELT_CROSSED = 120
        const val ID_CHAIN_CCW = 121
        const val ID_CHAIN_CW = 122
        const val ID_LINK_REMOVE = 123
        const val ID_LINK_CANCEL = 124

        /**
         * Les entrées du menu des pièces, dans l'ordre où elles s'affichent sur la
         * machine : le pied, la poutre qui pose dessus, le contrepoids qui pend, puis
         * le bout du bras — le crochet et la fronde.
         */
        val PARTS = listOf(
            TrebuchetView.Part.POST to R.string.trebuchet_part_post,
            TrebuchetView.Part.BEAM to R.string.trebuchet_part_beam,
            TrebuchetView.Part.WEIGHT to R.string.trebuchet_part_weight,
            TrebuchetView.Part.PIN to R.string.trebuchet_part_pin,
            TrebuchetView.Part.SLING to R.string.trebuchet_part_sling
        )

        // Les entrées du menu des réglages.
        const val ID_ARCADE = 0
        const val ID_REALISTE = 1
        const val ID_CLEAN = 2
        const val ID_GHOSTS = 3
        const val ID_SOUND = 4
        const val ID_FIRST_GHOST_CHOICE = 20
    }

    private lateinit var gameView: TrebuchetView
    private lateinit var gearView: GearMachineView
    private lateinit var statusText: TextView
    private lateinit var specsText: TextView
    private lateinit var bestText: TextView
    private lateinit var fireButton: TextView
    private lateinit var wheels: TrebuchetWheelBubble
    private lateinit var gearEditor: GearEditorBubble
    private lateinit var infoTitle: TextView
    private lateinit var infoTip: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var machinesButton: TextView
    private lateinit var partsButton: TextView

    /** Les machines mises de côté par le joueur, la plus récente en tête. */
    private var machines = emptyList<MachinePreset>()
    private var gearMachines = emptyList<GearMachinePreset>()

    private enum class MachineMode { TREBUCHET, GEARS }
    private var machineMode = MachineMode.TREBUCHET

    private var best = 0f

    /** La graine du site en cours. Tout le niveau tient dedans. */
    private var levelSeed = 1L

    /** Vrai tant qu'un site se fabrique en fond. */
    private var loading = false

    /** Le numéro de la dernière fabrication demandée : voir [loadLevel]. */
    private var loadToken = 0

    /** Le nom sous lequel on a chargé ou enregistré pour la dernière fois. */
    private var lastMachineName = ""
    private var lastGearMachineName = ""

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
        gearView = findViewById(R.id.trebuchet_gear_view)
        statusText = findViewById(R.id.trebuchet_status)
        specsText = findViewById(R.id.trebuchet_specs)
        bestText = findViewById(R.id.trebuchet_best)
        fireButton = findViewById(R.id.trebuchet_btn_fire)
        infoTitle = findViewById(R.id.trebuchet_info_title)
        infoTip = findViewById(R.id.trebuchet_info_tip)
        wheels = findViewById(R.id.trebuchet_wheels)
        gearEditor = findViewById(R.id.trebuchet_gear_editor)

        gameView.game.ghostLimit = prefs.getInt(KEY_GHOSTS, TrebuchetRules.GHOST_HISTORY)
        gameView.soundEnabled = prefs.getBoolean(KEY_SOUND, true)
        gameView.listener = this
        gearView.listener = this
        // Les roulettes règlent la même machine que le doigt, et préviennent quand
        // elles tournent : les deux moyens restent en phase sans se connaître.
        wheels.game = gameView.game
        wheels.onValueChanged = { gameView.playHammerTap(); updateUi() }
        gearEditor.gearView = gearView
        gearEditor.onEdited = { updateUi() }

        findViewById<ImageButton>(R.id.trebuchet_btn_back).setOnClickListener { finish() }
        machines = MachineLibrary.decode(prefs.getString(KEY_MACHINES, "") ?: "")
        gearMachines = GearMachineLibrary.decode(prefs.getString(KEY_GEAR_MACHINES, "") ?: "")
        machinesButton = findViewById(R.id.trebuchet_btn_machines)
        machinesButton.setOnClickListener { showMachinesMenu() }
        // Appui long : on passe au site suivant sans l'avoir rasé. Indispensable pour
        // essayer, et sans doute à remplacer par un vrai bouton quand le mode aura
        // trouvé sa forme.
        machinesButton.setOnLongClickListener {
            if (machineMode == MachineMode.TREBUCHET) nextLevel()
            true
        }
        partsButton = findViewById(R.id.trebuchet_btn_parts)
        partsButton.setOnClickListener { showPartsMenu() }
        fireButton.setOnClickListener { onFireButton() }
        findViewById<ImageButton>(R.id.trebuchet_btn_settings)
            .setOnClickListener { showSettingsMenu(it) }

        loadLevel(levelSeed)
    }

    override fun onResume() {
        super.onResume()
        if (machineMode == MachineMode.TREBUCHET) gameView.resume() else gearView.resume()
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        gearView.pause()
    }

    /**
     * Charge le site de cette graine et rebande la machine devant.
     *
     * **Le site se bâtit sur un fil de fond.** [TargetGenerator.generate] tire le plan,
     * dessine la maçonnerie et la tasse dans un monde physique jetable : trois
     * millisecondes en moyenne et seize au pire sur ordinateur, donc jusqu'à un dixième
     * de seconde sur un téléphone. Fait ici même, ce travail bloquait deux fils d'un
     * coup — celui de l'interface, et celui de la simulation, puisqu'il tenait le verrou
     * du jeu pendant tout ce temps. L'écran se figeait à chaque changement de site.
     *
     * Le fil de fond ne touche à rien du jeu en cours : il ne fait que fabriquer un
     * [TargetLevel], objet inerte, dans son propre monde. Seule la **pose** revient sur
     * le fil de l'interface, et elle est immédiate.
     */
    private fun loadLevel(seed: Long) {
        levelSeed = seed
        prefs.edit { putLong(KEY_SEED, seed) }
        gameView.clearSelection()
        // Le jeton écarte les sites périmés : un joueur qui enchaîne les appuis longs
        // lance plusieurs fabrications, et seule la dernière demandée doit se poser.
        val token = ++loadToken
        loading = true
        updateUi()
        lifecycleScope.launch {
            val level = withContext(Dispatchers.Default) { TargetGenerator.generate(seed) }
            if (token != loadToken) return@launch
            loading = false
            synchronized(gameView.game) { gameView.game.applyLevel(level) }
            gameView.syncPhase()
            updateUi()
        }
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

        menu.add(0, ID_SOUND, 4, getString(R.string.trebuchet_sound)).apply {
            isCheckable = true
            isChecked = gameView.soundEnabled
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_ARCADE -> setStyle(TargetStyle.ARCADE)
                ID_REALISTE -> setStyle(TargetStyle.REALISTE)
                ID_CLEAN -> {
                    synchronized(gameView.game) { gameView.game.clearGhosts() }
                    toast(getString(R.string.trebuchet_clean_done))
                }
                ID_SOUND -> setSoundEnabled(!gameView.soundEnabled)
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

    /** Coupe ou rétablit les bruitages, et s'en souvient pour la prochaine partie. */
    private fun setSoundEnabled(on: Boolean) {
        gameView.soundEnabled = on
        prefs.edit { putBoolean(KEY_SOUND, on) }
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
        // Le tempérament se pose **avant** la fabrication, qui le lit pour dimensionner
        // ses pierres. Le site d'avant tourne donc quelques dizaines de millisecondes
        // avec les constantes du nouveau — sans conséquence, puisqu'il est sur le point
        // d'être remplacé et que personne ne tire pendant qu'il choisit dans un menu.
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
        val trebuchets = menu.addSubMenu(
            0, ID_GROUP_TREBUCHET, 0, getString(R.string.trebuchet_machine_group_trebuchet)
        )
        trebuchets.add(0, ID_TREBUCHET_DEFAULT, 0, getString(R.string.trebuchet_machine_default))
        for ((i, m) in machines.withIndex()) {
            trebuchets.add(0, ID_FIRST_PRESET + i, i + 1, m.name)
        }
        if (machines.isNotEmpty()) {
            trebuchets.add(0, ID_TREBUCHET_DELETE, machines.size + 1, getString(R.string.trebuchet_machine_delete))
        }

        val gears = menu.addSubMenu(
            0, ID_GROUP_GEARS, 1, getString(R.string.trebuchet_machine_group_gears)
        )
        gears.add(0, ID_GEAR_DEFAULT, 0, getString(R.string.trebuchet_gear_default))
        for ((i, m) in gearMachines.withIndex()) {
            gears.add(0, ID_FIRST_GEAR_PRESET + i, i + 1, m.name)
        }
        if (gearMachines.isNotEmpty()) {
            gears.add(0, ID_GEAR_DELETE, gearMachines.size + 1, getString(R.string.trebuchet_machine_delete))
        }
        menu.add(0, ID_SAVE_AS, 2, getString(R.string.trebuchet_machine_save_as))
        popup.setOnMenuItemClickListener { item ->
            when (val id = item.itemId) {
                ID_TREBUCHET_DEFAULT -> {
                    // On repart de zéro : le champ « enregistrer sous » aussi, sinon on
                    // proposerait d'écraser une machine dont il ne reste rien.
                    lastMachineName = ""
                    switchMachineMode(MachineMode.TREBUCHET)
                    loadMachine(MachineConfig(), getString(R.string.trebuchet_machine_default))
                }
                ID_GEAR_DEFAULT -> {
                    lastGearMachineName = ""
                    switchMachineMode(MachineMode.GEARS)
                    loadGearMachine(GearMachineConfig(), getString(R.string.trebuchet_gear_default))
                }
                ID_SAVE_AS -> askMachineName()
                ID_TREBUCHET_DELETE -> showDeleteMenu(MachineMode.TREBUCHET)
                ID_GEAR_DELETE -> showDeleteMenu(MachineMode.GEARS)
                in ID_FIRST_PRESET until ID_FIRST_GEAR_PRESET -> {
                    machines.getOrNull(id - ID_FIRST_PRESET)?.let {
                        lastMachineName = it.name
                        switchMachineMode(MachineMode.TREBUCHET)
                        loadMachine(it.config, it.name)
                    }
                }
                else -> gearMachines.getOrNull(id - ID_FIRST_GEAR_PRESET)?.let {
                    lastGearMachineName = it.name
                    switchMachineMode(MachineMode.GEARS)
                    loadGearMachine(it.config, it.name)
                }
            }
            true
        }
        popup.show()
    }

    /**
     * Le menu des pièces : la même sélection qu'au doigt, mais par le nom.
     *
     * Toucher une pièce reste le geste normal, et il ne va nulle part. Seulement il
     * demande de voir la pièce assez gros pour la viser : dézoomé sur le site, la
     * machine tient dans un ongle, et régler quoi que ce soit obligeait à revenir
     * dessus, régler, puis reculer de nouveau pour voir où le coup tombe.
     *
     * La liste ne fait donc **que** désigner la pièce — ni cadrage, ni verrou. Rien n'est
     * coché, parce qu'il n'y a rien à retenir : c'est une sélection, pas un mode. Un appui
     * hors de la bulle la repose, exactement comme une pièce prise au doigt.
     */
    private fun showPartsMenu() {
        if (machineMode == MachineMode.GEARS) {
            showGearPartsMenu()
            return
        }
        val popup = PopupMenu(this, partsButton)
        for ((i, p) in PARTS.withIndex()) popup.menu.add(0, i, i, getString(p.second))
        popup.setOnMenuItemClickListener { item ->
            PARTS.getOrNull(item.itemId)?.let { (part, _) ->
                gameView.selectPart(part)
                // La sélection sort la bulle à roulettes et le bandeau de la pièce : c'est
                // updateUi qui les tient, et rien ne les rafraîchit tout seul ici puisque
                // la vue n'a pas reçu de doigt.
                updateUi()
            }
            true
        }
        popup.show()
    }

    private fun showGearPartsMenu() {
        val popup = PopupMenu(this, partsButton)
        popup.menu.add(0, ID_GEAR_ADD, 0, getString(R.string.trebuchet_gear_add))
        popup.menu.add(0, ID_GEAR_FLYWHEEL, 1, getString(R.string.trebuchet_gear_add_flywheel))
        val selected = gearView.selectedWheel()
        if (selected == null) {
            popup.menu.add(0, ID_GEAR_LAYER_DOWN, 2, getString(R.string.trebuchet_gear_layer_down))
            popup.menu.add(0, ID_GEAR_LAYER_UP, 3, getString(R.string.trebuchet_gear_layer_up))
        } else {
            popup.menu.add(0, ID_GEAR_LAUNCHER, 8, getString(R.string.trebuchet_gear_attach_launcher))
            val transmission = popup.menu.addSubMenu(
                0, 900, 9, getString(R.string.trebuchet_gear_transmission)
            )
            transmission.add(0, ID_BELT_OPEN, 0, getString(R.string.trebuchet_gear_belt_open))
            transmission.add(0, ID_BELT_CROSSED, 1, getString(R.string.trebuchet_gear_belt_crossed))
            transmission.add(0, ID_CHAIN_CCW, 2, getString(R.string.trebuchet_gear_chain_ccw))
            transmission.add(0, ID_CHAIN_CW, 3, getString(R.string.trebuchet_gear_chain_cw))
            transmission.add(0, ID_LINK_REMOVE, 4, getString(R.string.trebuchet_gear_link_remove))
        }
        if (gearView.pendingLink != null) popup.menu.add(
            0, ID_LINK_CANCEL, 11, getString(R.string.trebuchet_gear_link_cancel)
        )
        if (gearView.game.config.launcherWheelId != null) {
            popup.menu.add(0, ID_GEAR_ANGLE_DOWN, 10, getString(R.string.trebuchet_gear_angle_down))
            popup.menu.add(0, ID_GEAR_ANGLE_UP, 11, getString(R.string.trebuchet_gear_angle_up))
        }
        popup.menu.add(0, ID_GEAR_STOP, 12, getString(R.string.trebuchet_gear_stop))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_GEAR_ADD -> gearView.armPlacement(GearMachineRules.SIZES[1])
                ID_GEAR_FLYWHEEL -> gearView.armFlywheel()
                ID_GEAR_LAYER_DOWN -> {
                    if (gearView.selectedWheel() != null) gearView.changeSelectedLayer(-1)
                    else gearView.setCurrentLayer(-1)
                }
                ID_GEAR_LAYER_UP -> {
                    if (gearView.selectedWheel() != null) gearView.changeSelectedLayer(1)
                    else gearView.setCurrentLayer(1)
                }
                ID_GEAR_LAUNCHER -> gearView.attachLauncher()
                ID_GEAR_ANGLE_DOWN -> gearView.adjustLauncherAngle(-5f)
                ID_GEAR_ANGLE_UP -> gearView.adjustLauncherAngle(5f)
                ID_GEAR_STOP -> gearView.stopAll()
                ID_BELT_OPEN -> gearView.armLink(GearLinkKind.BELT_OPEN)
                ID_BELT_CROSSED -> gearView.armLink(GearLinkKind.BELT_CROSSED)
                ID_CHAIN_CCW -> gearView.armLink(GearLinkKind.CHAIN_FREEWHEEL, 1)
                ID_CHAIN_CW -> gearView.armLink(GearLinkKind.CHAIN_FREEWHEEL, -1)
                ID_LINK_REMOVE -> gearView.removeSelectedLinks()
                ID_LINK_CANCEL -> gearView.cancelLink()
            }
            updateUi()
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

    private fun loadGearMachine(cfg: GearMachineConfig, name: String) {
        gearView.loadConfig(cfg)
        updateUi()
        toast(getString(R.string.trebuchet_machine_loaded, name))
    }

    private fun switchMachineMode(mode: MachineMode) {
        if (machineMode == mode) return
        machineMode = mode
        if (mode == MachineMode.TREBUCHET) {
            gearView.pause()
            gearView.visibility = View.GONE
            gameView.visibility = View.VISIBLE
            gameView.resume()
        } else {
            gameView.pause()
            gameView.clearSelection()
            gameView.visibility = View.GONE
            gearView.visibility = View.VISIBLE
            gearView.resume()
        }
        updateUi()
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
            setText(if (machineMode == MachineMode.TREBUCHET) lastMachineName else lastGearMachineName)
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
        if (machineMode == MachineMode.TREBUCHET) {
            machines = MachineLibrary.put(machines, MachinePreset(name, gameView.game.config))
            prefs.edit { putString(KEY_MACHINES, MachineLibrary.encode(machines)) }
            lastMachineName = name
        } else {
            gearMachines = GearMachineLibrary.put(gearMachines, GearMachinePreset(name, gearView.snapshot()))
            prefs.edit { putString(KEY_GEAR_MACHINES, GearMachineLibrary.encode(gearMachines)) }
            lastGearMachineName = name
        }
        toast(getString(R.string.trebuchet_machine_saved, name))
    }

    /** Le second menu : celui dont on ne sort rien, on y jette. */
    private fun showDeleteMenu(mode: MachineMode) {
        val popup = PopupMenu(this, machinesButton)
        val names = if (mode == MachineMode.TREBUCHET) machines.map { it.name } else gearMachines.map { it.name }
        for ((i, name) in names.withIndex()) popup.menu.add(0, i, i, name)
        popup.setOnMenuItemClickListener { item ->
            names.getOrNull(item.itemId)?.let { name ->
                if (mode == MachineMode.TREBUCHET) {
                    machines = MachineLibrary.remove(machines, name)
                    prefs.edit { putString(KEY_MACHINES, MachineLibrary.encode(machines)) }
                } else {
                    gearMachines = GearMachineLibrary.remove(gearMachines, name)
                    prefs.edit { putString(KEY_GEAR_MACHINES, GearMachineLibrary.encode(gearMachines)) }
                }
                toast(getString(R.string.trebuchet_machine_deleted, name))
            }
            true
        }
        popup.show()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    /**
     * Le bouton principal, et les trois choses qu'il fait — une par phase.
     *
     * Il se changeait en « site suivant » dès que le site était rasé, ce qui faisait de
     * la victoire une porte qui se referme : le joueur voulait souvent retirer un coup
     * dans les ruines pour finir le travail, ou simplement regarder. Un site rasé reste
     * donc un site où l'on tire, et on n'en change que sur demande — appui long sur le
     * bouton des machines.
     *
     * **Pendant le vol, il termine le tir au lieu de l'annuler.** Il rebandait la
     * machine sur-le-champ, ce qui était la seule action disponible et jetait tout ce
     * que le tir avait montré : la traînée disparaissait, et avec elle la seule trace de
     * l'essai qu'on venait de faire. Il clôt maintenant le tir comme s'il s'était
     * terminé — le fantôme est gardé, arrêté à l'endroit exact où le boulet en était,
     * les dégâts restent acquis — et la caméra s'arrête là où le joueur regardait. Pour
     * rebander, il suffit d'appuyer une seconde fois, ou de toucher une pièce.
     */
    private fun onFireButton() {
        if (machineMode == MachineMode.GEARS) {
            if (gearView.game.config.launcherWheelId == null) {
                gearView.stopAll()
            } else if (!gearView.launchProjectile()) {
                toast(getString(R.string.trebuchet_gear_not_enough_energy))
            }
            updateUi()
            return
        }
        val game = gameView.game
        gameView.clearSelection()
        when (game.phase) {
            TrebuchetGame.Phase.BUILD -> {
                synchronized(game) { game.release() }
                gameView.syncPhase()
            }
            TrebuchetGame.Phase.FLIGHT -> {
                synchronized(game) { game.stopShot() }
                // La caméra se fige avant que la vue n'ait vu le changement de phase :
                // sans ça, elle reculerait d'elle-même pour montrer l'arc du tir qu'on
                // vient justement d'interrompre.
                gameView.freezeCamera()
                gameView.syncPhase()
            }
            TrebuchetGame.Phase.RESULT -> {
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
        if (machineMode == MachineMode.TREBUCHET) updateUi()
    }

    override fun onGearMachineChanged() {
        if (machineMode == MachineMode.GEARS) updateUi()
    }

    override fun onGearSelectionChanged() {
        if (machineMode == MachineMode.GEARS) updateUi()
    }

    // ── Affichage ─────────────────────────────────────────────────────────────

    private fun updateUi() {
        if (machineMode == MachineMode.GEARS) {
            updateGearUi()
            return
        }
        gearEditor.visibility = View.GONE
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

        fireButton.text = getString(
            when (game.phase) {
                TrebuchetGame.Phase.BUILD -> R.string.trebuchet_btn_release
                // En vol, le bouton clôt le tir sans rien perdre : voir [onFireButton].
                TrebuchetGame.Phase.FLIGHT -> R.string.trebuchet_btn_stop
                TrebuchetGame.Phase.RESULT -> R.string.trebuchet_btn_adjust
            }
        )
        // On ne décroche pas la détente sur un site qui n'est pas encore posé : le tir
        // partirait sur la cible précédente, et il serait remplacé une image plus tard.
        fireButton.isEnabled = !loading
        // Le fond du bouton ne dit pas tout seul qu'il est éteint : on le fait pâlir,
        // sinon un bouton qui ne répond pas passe pour un bouton cassé.
        fireButton.alpha = if (loading) 0.45f else 1f

        statusText.visibility =
            if (gameView.selected == TrebuchetView.Part.NONE) View.VISIBLE else View.GONE
        // Le site en cours de fabrication passe avant tout le reste, victoire comprise :
        // c'est la seule chose que le joueur attend, et le site rasé qu'annoncerait la
        // ligne suivante est celui d'avant.
        if (loading) {
            statusText.visibility = View.VISIBLE
            statusText.setText(R.string.trebuchet_status_loading)
            return
        }
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
                // Un tir arrêté en plein vol n'a pas de portée, et il ne faut surtout pas
                // le confondre avec un tir parti en arrière : l'un est un choix, l'autre
                // est une machine mal réglée.
                if (game.shotStopped) {
                    getString(R.string.trebuchet_status_stopped)
                } else if (game.shotDistance <= 0f) {
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

    private fun updateGearUi() {
        val selected = gearView.selectedWheel()
        wheels.visibility = View.GONE
        gearEditor.showForSelection()
        specsText.text = getString(
            R.string.trebuchet_gear_specs,
            gearView.game.gears.size,
            gearView.game.meshes.size + gearView.game.transmissions.size,
            gearView.currentLayer,
            gearView.game.rotationalEnergy() / 1000f,
            gearView.game.slippingCount()
        )
        bestText.setText(R.string.trebuchet_gear_title)
        fireButton.setText(
            if (gearView.game.config.launcherWheelId != null) R.string.trebuchet_gear_launch
            else R.string.trebuchet_gear_stop
        )
        fireButton.isEnabled = true
        fireButton.alpha = 1f
        statusText.visibility = if (selected == null) View.VISIBLE else View.GONE
        statusText.text = gearView.pendingLink?.let {
            getString(
                if (it.kind == GearLinkKind.SHAFT_CLUTCH) R.string.trebuchet_gear_shaft_pick_second
                else R.string.trebuchet_gear_link_pick_second
            )
        } ?: gearView.placementTeeth?.let {
            if (gearView.placementKind == com.Atom2Universe.app.games.trebuchet.gears.GearWheelKind.FLYWHEEL) {
                getString(R.string.trebuchet_gear_place_flywheel, gearView.currentLayer)
            } else {
                getString(R.string.trebuchet_gear_place, it, gearView.currentLayer)
            }
        } ?: gearView.game.projectile?.let {
            getString(
                R.string.trebuchet_gear_shot_status,
                gearView.game.lastLaunchSpeed,
                gearView.game.lastLaunchEnergy / 1000f,
                it.body.x - it.startX,
                it.peakY - it.startY
            )
        } ?: getString(R.string.trebuchet_gear_status)
        if (selected != null) {
            val state = gearView.game.gears.firstOrNull { it.wheel.id == selected.id }
            infoTitle.visibility = View.VISIBLE
            infoTip.visibility = View.VISIBLE
            infoTitle.text = getString(
                R.string.trebuchet_gear_selected,
                if (selected.kind == com.Atom2Universe.app.games.trebuchet.gears.GearWheelKind.FLYWHEEL)
                    getString(R.string.trebuchet_gear_flywheel) else getString(R.string.trebuchet_gear_wheel, selected.teeth),
                gearMaterialLabel(selected.material),
                state?.body?.mass ?: 0f,
                selected.layer,
                if (state != null) 0.5f * state.body.inertia * state.body.omega * state.body.omega / 1000f else 0f
            )
            infoTip.setText(
                if (gearView.layoutConflictIds.isNotEmpty())
                    R.string.trebuchet_gear_layout_conflict
                else if (gearView.pendingLink?.kind == GearLinkKind.SHAFT_CLUTCH)
                    R.string.trebuchet_gear_shaft_pending_tip
                else if (gearView.pendingLink != null) R.string.trebuchet_gear_link_pending_tip
                else if (gearView.game.config.launcherWheelId == selected.id) R.string.trebuchet_gear_launcher_tip
                else R.string.trebuchet_gear_tip
            )
        } else {
            infoTitle.visibility = View.GONE
            infoTip.visibility = View.GONE
        }
    }

    private fun gearMaterialLabel(material: GearWheelMaterial): String = getString(
        when (material) {
            GearWheelMaterial.WOOD -> R.string.trebuchet_gear_material_wood
            GearWheelMaterial.ALUMINUM -> R.string.trebuchet_gear_material_aluminum
            GearWheelMaterial.STEEL -> R.string.trebuchet_gear_material_steel
            GearWheelMaterial.TITANIUM -> R.string.trebuchet_gear_material_titanium
        }
    )

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
