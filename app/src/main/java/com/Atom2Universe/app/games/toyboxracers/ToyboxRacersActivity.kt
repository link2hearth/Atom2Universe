package com.Atom2Universe.app.games.toyboxracers

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.text.InputType
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.R
import com.Atom2Universe.app.SimpleColorPickerDialog
import com.Atom2Universe.app.games.toyboxracers.editor.EditorTouchLayer
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxDecor
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolume
import com.Atom2Universe.app.games.toyboxracers.game.PlayMode
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import com.Atom2Universe.app.games.toyboxracers.track.RoomKind
import com.Atom2Universe.app.games.toyboxracers.track.CircuitKind
import com.Atom2Universe.app.games.toyboxracers.track.HousePlan
import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.game.RacePhase
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolumeKind
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorld
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorldStore
import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorModel
import com.Atom2Universe.app.games.toyboxracers.models.DecorRoom
import com.Atom2Universe.app.games.toyboxracers.models.DecorShape
import com.Atom2Universe.app.util.enableImmersiveMode
import java.util.Locale

/**
 * Exploration libre et courses facultatives, accessibles depuis le hub des jeux.
 */
class ToyboxRacersActivity : ThemedActivity() {
    private lateinit var glView: ToyboxRacersGLView
    private lateinit var renderer: ToyboxRacersRenderer
    private lateinit var hud: TextView
    private lateinit var status: TextView
    private lateinit var countdown: TextView
    private lateinit var resultPanel: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var difficultyButton: Button
    private lateinit var modeButton: Button
    private lateinit var roomButton: Button
    private lateinit var circuitButton: Button
    private lateinit var editorButton: Button
    private lateinit var pauseButton: Button
    private lateinit var editorPanel: LinearLayout
    private lateinit var editorToolsPanel: LinearLayout
    private lateinit var editorPositionPanel: LinearLayout
    private lateinit var editorCameraPanel: LinearLayout
    private lateinit var editorInfo: TextView
    private lateinit var editorKindButton: Button
    private lateinit var editorGridButton: Button
    private lateinit var editorGroupButton: Button
    private lateinit var editorSolidButton: Button
    private lateinit var editorColorButton: Button
    private lateinit var editorPlaceButton: Button
    private lateinit var editorWidthPicker: NumberPicker
    private lateinit var editorHeightPicker: NumberPicker
    private lateinit var editorDepthPicker: NumberPicker
    private lateinit var editorTouchLayer: EditorTouchLayer
    private val raceHudViews = mutableListOf<View>()
    private val raceControlViews = mutableListOf<View>()
    private var currentScene = SceneChoice()
    private lateinit var housePlan: HousePlan
    private lateinit var minimap: ToyboxMinimapView
    private lateinit var worldStore: ToyboxWorldStore
    private var editorWorld = ToyboxWorld()
    private var currentMode = PlayMode.EXPLORATION
    private var editorActive = false
    private var editorKind = ToyboxVolumeKind.FLOOR
    private var editorWidth = 20f
    private var editorHeight = 0.6f
    private var editorDepth = 20f
    private var editorQuarterTurns = 0
    private var editorFloorY = 0f
    private var editorSolid = true
    private var editorColor = editorKind.color
    private var selectedVolumeId: Long? = null
    private var selectedDecorId: Long? = null
    private var selectedGroupIds: Set<Long> = emptySet()
    private var editorDraftDecorModelId: String? = null
    private var editorDecorQuarterTurns = 0
    private var editorDecorScale = 1f
    private var editorGroupMode = EditorGroupMode.OFF
    private var editorDraftActive = false
    private var editorGridIndex = 0
    private var editorForward = 0f
    private var editorStrafe = 0f
    private var editorPitch = 0f
    private var editorYaw = 0f
    private var syncingEditorPickers = false
    private var paused = false
    private var pauseDialog: AlertDialog? = null
    private val releaseControls = mutableListOf<() -> Unit>()
    private var currentDifficulty = RaceDifficulty.ARCADE
    private var lastTurboLevel = 0
    private var lastTurboReleaseSerial = 0
    private var lastFinishSerial = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        worldStore = ToyboxWorldStore(this)
        editorWorld = worldStore.load()
        currentDifficulty = RaceDifficulty.entries.getOrElse(
            prefs.getInt(KEY_DIFFICULTY, RaceDifficulty.ARCADE.ordinal)
        ) { RaceDifficulty.ARCADE }
        val seed = prefs.getLong(KEY_HOUSE_SEED, 2026L)
        housePlan = HousePlan.generate(seed)
        val savedRoom = RoomKind.entries.find { it.name == prefs.getString("room", null) } ?: RoomKind.BEDROOM
        currentScene = sceneForRoom(savedRoom)
        // Migration de la sélection avant l'arrivée du plan de maison.
        if (!prefs.contains(KEY_HOUSE_SEED)) {
            CircuitKind.entries.find { it.name == prefs.getString("circuit", null) }?.let {
                currentScene = SceneChoice(savedRoom, it)
            }
            prefs.edit().putLong(KEY_HOUSE_SEED, seed)
                .putString(KEY_ROOM_CIRCUIT + savedRoom.name, currentScene.circuit.name).apply()
        }
        renderer = ToyboxRacersRenderer(currentDifficulty, currentScene) { state ->
            runOnUiThread { updateHud(state) }
        }
        renderer.setEditorWorld(editorWorld)
        pushEditorPreview()
        glView = ToyboxRacersGLView(this, renderer)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(184, 219, 240))
            // Indispensable pour maintenir GAZ tout en dirigeant avec un autre doigt.
            setMotionEventSplittingEnabled(true)
        }
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))
        addHud(root)
        addControls(root)
        addEditorOverlay(root)
        minimap = ToyboxMinimapView(this)
        val compact = resources.displayMetrics.widthPixels / resources.displayMetrics.density < 640f
        root.addView(minimap, FrameLayout.LayoutParams(dp(if (compact) 112 else 152), dp(if (compact) 80 else 106)).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(20)
        })
        addRaceOverlay(root)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editorActive) toggleEditorMode() else showPause()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        enableImmersiveMode()
        if (editorActive) {
            paused = false
            renderer.setPaused(false)
            renderer.setEditorActive(true)
            pushEditorPreview()
        }
        if (paused && !editorActive) showPause()
    }

    override fun onPause() {
        pauseGame()
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        pauseDialog?.dismiss()
        pauseDialog = null
        super.onDestroy()
    }

    private fun pauseGame() {
        paused = true
        releaseControls.forEach { it() }
        renderer.setPaused(true)
    }

    private fun resumeGame() {
        paused = false
        renderer.setPaused(false)
        pauseDialog?.dismiss()
        pauseDialog = null
        enableImmersiveMode()
    }

    private fun showPause() {
        if (isFinishing || isDestroyed || pauseDialog?.isShowing == true) return
        pauseGame()
        if (editorActive) {
            showEditorPause()
            return
        }
        pauseDialog = dialogBuilder()
            .setTitle(R.string.toybox_pause)
            .setItems(arrayOf(
                getString(R.string.toybox_resume),
                getString(R.string.toybox_restart),
                getString(if (currentMode == PlayMode.EXPLORATION) R.string.toybox_start_race else R.string.toybox_free),
                getString(R.string.toybox_quit)
            )) { _, which ->
                when (which) {
                    0 -> resumeGame()
                    1 -> {
                        resultPanel.visibility = View.GONE
                        renderer.requestReset()
                        resumeGame()
                    }
                    2 -> {
                        switchMode()
                        resumeGame()
                    }
                    3 -> finish()
                }
            }
            .setOnCancelListener { resumeGame() }
            .show()
    }

    private fun showEditorPause() {
        pauseDialog = dialogBuilder()
            .setTitle("Pause creation")
            .setItems(arrayOf(
                "Reprendre",
                "Save actual",
                "Load",
                "Load creations",
                "Load niveau du jeu",
                "Quitter le mode edition",
                getString(R.string.toybox_quit)
            )) { _, which ->
                when (which) {
                    0 -> resumeGame()
                    1 -> saveCurrentCreation()
                    2 -> showEditorLoadMenu()
                    3 -> showCreationLoader()
                    4 -> showBuiltInLevelLoader()
                    5 -> {
                        resumeGame()
                        toggleEditorMode()
                    }
                    6 -> finish()
                }
            }
            .setOnCancelListener { resumeGame() }
            .show()
    }

    private fun showEditorLoadMenu() {
        pauseDialog = null
        pauseDialog = dialogBuilder()
            .setTitle("Load")
            .setItems(arrayOf("Load creations", "Load niveau du jeu")) { _, which ->
                when (which) {
                    0 -> showCreationLoader()
                    1 -> showBuiltInLevelLoader()
                }
            }
            .setNegativeButton("Retour") { _, _ -> showEditorPause() }
            .setOnCancelListener { resumeGame() }
            .show()
    }

    private fun saveCurrentCreation() {
        val file = worldStore.saveCreation(editorWorld)
        Toast.makeText(this, "Creation sauvee: ${file.name}", Toast.LENGTH_SHORT).show()
        resumeGame()
    }

    private fun showCreationLoader() {
        val creations = worldStore.listCreations()
        if (creations.isEmpty()) {
            Toast.makeText(this, "Aucune creation sauvegardee", Toast.LENGTH_SHORT).show()
            resumeGame()
            return
        }
        pauseDialog = null
        pauseDialog = dialogBuilder()
            .setTitle("Load creations")
            .setItems(creations.map { it.nameWithoutExtension }.toTypedArray()) { _, which ->
                val world = worldStore.loadCreation(creations[which])
                if (world == null) {
                    Toast.makeText(this, "Creation illisible", Toast.LENGTH_SHORT).show()
                    resumeGame()
                } else {
                    loadEditorWorld(world, saveAsActual = true)
                    Toast.makeText(this, "Creation chargee", Toast.LENGTH_SHORT).show()
                    resumeGame()
                }
            }
            .setNegativeButton("Retour") { _, _ -> showEditorPause() }
            .setOnCancelListener { resumeGame() }
            .show()
    }

    private fun showBuiltInLevelLoader() {
        val levels = ToyboxWorld.builtInWorlds()
        pauseDialog = null
        pauseDialog = dialogBuilder()
            .setTitle("Load niveau du jeu")
            .setItems(levels.map { it.name }.toTypedArray()) { _, which ->
                loadEditorWorld(levels[which], saveAsActual = true)
                Toast.makeText(this, "${levels[which].name} charge", Toast.LENGTH_SHORT).show()
                resumeGame()
            }
            .setNegativeButton("Retour") { _, _ -> showEditorPause() }
            .setOnCancelListener { resumeGame() }
            .show()
    }

    private fun loadEditorWorld(world: ToyboxWorld, saveAsActual: Boolean) {
        editorWorld = world
        selectedVolumeId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftDecorModelId = null
        editorQuarterTurns = 0
        if (saveAsActual) worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
        pushEditorPreview()
    }

    private fun switchMode() {
        releaseControls.forEach { it() }
        currentMode = if (currentMode == PlayMode.EXPLORATION) PlayMode.RACE else PlayMode.EXPLORATION
        resultPanel.visibility = View.GONE
        modeButton.setText(if (currentMode == PlayMode.EXPLORATION) R.string.toybox_start_race else R.string.toybox_free)
        renderer.setMode(currentMode)
    }

    private fun addHud(root: FrameLayout) {
        val density = resources.displayMetrics.density
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            background = roundedBackground(0xB83B4055.toInt(), 18f)
        }
        val title = TextView(this).apply {
            text = getString(R.string.toybox_racers_title)
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        hud = TextView(this).apply {
            text = getString(R.string.toybox_free_hud, 0, "00:00.0")
            setTextColor(0xFFFFE7A8.toInt())
            textSize = 16f
            typeface = Typeface.MONOSPACE
        }
        status = TextView(this).apply {
            text = getString(R.string.toybox_explore_hint)
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        topPanel.addView(title)
        topPanel.addView(hud)
        topPanel.addView(status)
        root.addView(topPanel, FrameLayout.LayoutParams(resources.displayMetrics.widthPixels - dp(204), -2).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (16 * density).toInt()
            topMargin = (12 * density).toInt()
        })
        raceHudViews += topPanel

        pauseButton = makeButton("Ⅱ", 52, 0xB83B4055.toInt()).apply {
            textSize = 28f
            contentDescription = getString(R.string.toybox_pause)
            setOnClickListener { showPause() }
        }
        root.addView(pauseButton, FrameLayout.LayoutParams(dp(52), dp(52)).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dp(14)
            topMargin = dp(12)
        })

        val reset = makeButton("DÉPART", 92, 0xAA735D91.toInt()).apply {
            textSize = 11f
            setOnClickListener {
                releaseControls.forEach { it() }
                resultPanel.visibility = View.GONE
                renderer.requestReset()
            }
        }
        root.addView(reset, FrameLayout.LayoutParams(dp(92), dp(44)).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dp(76)
            topMargin = dp(16)
        })
        raceHudViews += reset

        difficultyButton = makeButton(currentDifficulty.label, 92, 0xAA4B617A.toInt()).apply {
            textSize = 11f
            setOnClickListener {
                currentDifficulty = RaceDifficulty.entries[(currentDifficulty.ordinal + 1) % RaceDifficulty.entries.size]
                text = currentDifficulty.label
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putInt(KEY_DIFFICULTY, currentDifficulty.ordinal)
                    .apply()
                resultPanel.visibility = View.GONE
                renderer.setDifficulty(currentDifficulty)
            }
        }
        root.addView(difficultyButton, FrameLayout.LayoutParams(dp(104), dp(42)).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dp(18)
            topMargin = dp(72)
        })
        raceHudViews += difficultyButton
        modeButton = makeButton(getString(R.string.toybox_start_race), 112, 0xAA735D91.toInt()).apply {
            textSize = 11f
            setOnClickListener { switchMode() }
        }
        root.addView(modeButton, FrameLayout.LayoutParams(dp(112), dp(42)).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dp(18)
            topMargin = dp(122)
        })
        raceHudViews += modeButton
        roomButton = makeButton(roomLabel(), 120, 0xAA4B617A.toInt()).apply {
            textSize = 12f
            contentDescription = getString(R.string.toybox_change_room)
            setOnClickListener {
                showRoomPicker()
            }
        }
        circuitButton = makeButton(circuitLabel(), 120, 0xAA735D91.toInt()).apply {
            textSize = 12f
            contentDescription = getString(R.string.toybox_change_circuit)
            setOnClickListener {
                showCircuitPicker()
            }
        }
        val houseButton = makeButton(getString(R.string.toybox_house_mode), 96, 0xAA4B8F6E.toInt()).apply {
            textSize = 12f
            setOnClickListener { enterHouseMode() }
        }
        editorButton = makeButton("BUILD 3D", 96, 0xAA735D91.toInt()).apply {
            textSize = 12f
            setOnClickListener { toggleEditorMode() }
        }
        for ((index, button) in listOf(roomButton, circuitButton, houseButton, editorButton).withIndex()) {
            root.addView(button, FrameLayout.LayoutParams(dp(if (index >= 2) 96 else 120), dp(42)).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(16 + if (index < 2) index * 128 else 256 + (index - 2) * 104)
                topMargin = dp(128)
            })
            raceHudViews += button
        }
    }

    private fun roomLabel(kind: RoomKind = currentScene.room) = getString(when (kind) {
        RoomKind.BEDROOM -> R.string.toybox_room_bedroom
        RoomKind.KITCHEN -> R.string.toybox_room_kitchen
        RoomKind.LIVING_ROOM -> R.string.toybox_room_living
        RoomKind.DINING_ROOM -> R.string.toybox_room_dining
        RoomKind.OFFICE -> R.string.toybox_room_office
        RoomKind.BATHROOM -> R.string.toybox_room_bathroom
        RoomKind.LAUNDRY -> R.string.toybox_room_laundry
        RoomKind.GARAGE -> R.string.toybox_room_garage
    })

    private fun sceneForRoom(kind: RoomKind): SceneChoice {
        val saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ROOM_CIRCUIT + kind.name, null)
        val circuit = CircuitKind.entries.find { it.name == saved } ?: housePlan.room(kind).circuit
        return SceneChoice(kind, circuit)
    }

    private fun showRoomPicker() {
        if (isFinishing || isDestroyed || pauseDialog?.isShowing == true) return
        pauseGame()
        val rooms = housePlan.rooms.map { it.kind }
        pauseDialog = dialogBuilder()
            .setTitle(getString(R.string.toybox_change_room) + " · " + housePlan.seed)
            .setSingleChoiceItems(rooms.map { roomLabel(it) + " · " + circuitLabel(sceneForRoom(it).circuit) }
                .toTypedArray(), rooms.indexOf(currentScene.room)) { _, which ->
                if (rooms[which] != currentScene.room) changeScene(sceneForRoom(rooms[which]))
                resumeGame()
            }
            .setNeutralButton(R.string.toybox_house_seed) { _, _ ->
                // Le dialogue de sélection est fermé avant d'ouvrir la saisie.
                pauseDialog = null
                roomButton.post { if (!isFinishing && !isDestroyed) showSeedPicker() }
            }
            .setOnCancelListener { resumeGame() }
            .show()
    }

    private fun showSeedPicker() {
        pauseGame()
        val builder = dialogBuilder()
        val input = EditText(builder.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(housePlan.seed.toString())
            selectAll()
            contentDescription = getString(R.string.toybox_house_seed)
        }
        val dialog = builder
            .setTitle(R.string.toybox_house_seed)
            .setMessage(R.string.toybox_house_seed_help)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> resumeGame() }
            .setOnCancelListener { resumeGame() }
            .create()
        pauseDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val seed = input.text.toString().trim().toLongOrNull()
                if (seed == null) {
                    input.error = getString(R.string.toybox_house_seed_invalid)
                    return@setOnClickListener
                }
                housePlan = HousePlan.generate(seed)
                val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putLong(KEY_HOUSE_SEED, seed)
                RoomKind.entries.forEach { editor.remove(KEY_ROOM_CIRCUIT + it.name) }
                editor.apply()
                changeScene(housePlan.room(currentScene.room).scene)
                resumeGame()
            }
        }
        dialog.show()
    }
    private fun circuitLabel(kind: CircuitKind = currentScene.circuit) = getString(when (kind) {
        CircuitKind.FIGURE_EIGHT -> R.string.toybox_circuit_eight
        CircuitKind.SLALOM -> R.string.toybox_circuit_slalom
        CircuitKind.ROLLING_HILLS -> R.string.toybox_circuit_hills
        CircuitKind.DOUBLE_BUMPS -> R.string.toybox_circuit_doubles
        CircuitKind.HIGH_GARDEN -> R.string.toybox_circuit_viaduct
        CircuitKind.SWITCHBACKS -> R.string.toybox_circuit_switchbacks
        CircuitKind.JUMP_PARADE -> R.string.toybox_circuit_jumps
        CircuitKind.RIBBON_RALLY -> R.string.toybox_circuit_rally
        CircuitKind.FURNITURE_TRAIL -> R.string.toybox_circuit_furniture
        CircuitKind.WORKSHOP_EXPEDITION -> R.string.toybox_circuit_expedition
        CircuitKind.HOUSE_GROUND_FLOOR -> R.string.toybox_house_mode
    })

    private fun showCircuitPicker() {
        if (isFinishing || isDestroyed || pauseDialog?.isShowing == true) return
        pauseGame()
        val pickable = CircuitKind.entries.filterNot { it.usesHouseLayout }
        pauseDialog = dialogBuilder()
            .setTitle(getString(R.string.toybox_change_circuit) + " · " + roomLabel())
            .setSingleChoiceItems(pickable.map { circuitLabel(it) }.toTypedArray(),
                pickable.indexOf(currentScene.circuit)) { _, which ->
                val choice = pickable[which]
                if (choice != currentScene.circuit) changeScene(currentScene.copy(circuit = choice))
                resumeGame()
            }
            .setOnCancelListener { resumeGame() }
            .show()
    }

    /** Scène dédiée : contrairement aux 8 pièces indépendantes, la maison
     * couvre plusieurs pièces à la fois et n'a pas de RoomKind qui la représente
     * seule, donc elle contourne le sélecteur pièce/circuit habituel. */
    private fun enterHouseMode() {
        if (currentScene.circuit == CircuitKind.HOUSE_GROUND_FLOOR) return
        releaseControls.forEach { it() }
        currentScene = SceneChoice(RoomKind.BEDROOM, CircuitKind.HOUSE_GROUND_FLOOR)
        roomButton.text = roomLabel()
        circuitButton.text = circuitLabel()
        resultPanel.visibility = View.GONE
        lastFinishSerial = 0
        lastTurboLevel = 0
        lastTurboReleaseSerial = 0
        renderer.setScene(currentScene)
    }

    private fun dialogBuilder() = AlertDialog.Builder(this, R.style.Theme_Toybox_Dialog)

    private fun changeScene(scene: SceneChoice) {
        releaseControls.forEach { it() }
        currentScene = scene
        roomButton.text = roomLabel()
        circuitButton.text = circuitLabel()
        resultPanel.visibility = View.GONE
        lastFinishSerial = 0
        lastTurboLevel = 0
        lastTurboReleaseSerial = 0
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString("room", scene.room.name).putString("circuit", scene.circuit.name)
            .putString(KEY_ROOM_CIRCUIT + scene.room.name, scene.circuit.name).apply()
        renderer.setScene(scene)
    }

    private fun addRaceOverlay(root: FrameLayout) {
        countdown = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 64f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(8f, 0f, 3f, 0xCC3B4055.toInt())
        }
        root.addView(countdown, FrameLayout.LayoutParams(dp(180), dp(110)).apply {
            gravity = Gravity.CENTER
        })

        resultPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(22), dp(28), dp(22))
            background = roundedBackground(0xED3B4055.toInt(), 22f)
            visibility = View.GONE
        }
        resultText = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
        }
        val replay = makeButton("REJOUER", 118, 0xCCE26F82.toInt()).apply {
            textSize = 14f
            setOnClickListener {
                resultPanel.visibility = View.GONE
                renderer.requestReset()
            }
        }
        resultPanel.addView(resultText, LinearLayout.LayoutParams(dp(300), -2).apply {
            bottomMargin = dp(16)
        })
        resultPanel.addView(replay, LinearLayout.LayoutParams(dp(150), dp(52)))
        root.addView(resultPanel, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addControls(root: FrameLayout) {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val controls = ControlDimensions.forWidth(widthDp)
        val steering = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val left = makeButton("◀", controls.steering, 0xA84B617A.toInt())
        val right = makeButton("▶", controls.steering, 0xA84B617A.toInt())
        steering.addView(left, LinearLayout.LayoutParams(dp(controls.steering), dp(controls.steering)).apply { rightMargin = dp(controls.gap) })
        steering.addView(right, LinearLayout.LayoutParams(dp(controls.steering), dp(controls.steering)))
        root.addView(steering, FrameLayout.LayoutParams(-2, dp(controls.steering)).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(20)
            bottomMargin = dp(20)
        })
        raceControlViews += steering

        val brake = makeButton("FREIN\nRECUL", controls.brake, 0xB8735D91.toInt()).apply {
            textSize = 12f
        }
        root.addView(brake, FrameLayout.LayoutParams(dp(controls.brake), dp(controls.brake)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(controls.accelerator + 34)
            bottomMargin = dp(26)
        })
        raceControlViews += brake

        val accelerator = makeButton("GAZ", controls.accelerator, 0xB8E26F82.toInt()).apply {
            textSize = 13f
        }
        root.addView(accelerator, FrameLayout.LayoutParams(dp(controls.accelerator), dp(controls.accelerator)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(22)
            bottomMargin = dp(16)
        })
        raceControlViews += accelerator

        bindHoldButton(left) { pressed ->
            renderer.setSteering(if (pressed) 1f else if (right.isPressed) -1f else 0f)
        }
        bindHoldButton(right) { pressed ->
            renderer.setSteering(if (pressed) -1f else if (left.isPressed) 1f else 0f)
        }
        bindHoldButton(brake, renderer::setBraking)
        bindHoldButton(accelerator, renderer::setAccelerating)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addEditorOverlay(root: FrameLayout) {
        editorTouchLayer = EditorTouchLayer(this).apply {
            visibility = View.GONE
            onMoveAxesChanged = { strafe, forward ->
                editorStrafe = -strafe
                editorForward = forward
                pushEditorInput()
            }
            onLookAxesChanged = { yaw, pitch ->
                editorYaw = -yaw
                editorPitch = pitch
                pushEditorInput()
            }
            onObjectLongPress = { x, y ->
                renderer.pickDecor(x, y, editorWorld.decorations)?.let {
                    selectEditorDecor(it)
                    return@let
                } ?: renderer.pickVolume(x, y, editorWorld.volumes)?.let {
                    when (editorGroupMode) {
                        EditorGroupMode.OFF -> selectEditorVolume(it)
                        EditorGroupMode.SAME_KIND -> selectConnectedGroup(it, sameKindOnly = true)
                        EditorGroupMode.CONNECTED -> selectConnectedGroup(it, sameKindOnly = false)
                    }
                }
            }
        }
        root.addView(editorTouchLayer, FrameLayout.LayoutParams(-1, -1))

        fun makeBubble() = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = roundedBackground(0xD83B4055.toInt(), 18f)
            visibility = View.GONE
        }

        fun row(parent: LinearLayout, vararg views: View, heightDp: Int = 42, bottomDp: Int = 6) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            views.forEachIndexed { index, view ->
                line.addView(view, LinearLayout.LayoutParams(0, dp(heightDp), 1f).apply {
                    if (index < views.lastIndex) rightMargin = dp(6)
                })
            }
            parent.addView(line, LinearLayout.LayoutParams(-1, dp(heightDp)).apply { bottomMargin = dp(bottomDp) })
        }

        fun label(text: String, size: Float = 12f, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = size
            alpha = 0.9f
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        fun grab(text: String, target: View) = label("≡  $text", 15f, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(4))
            setOnTouchListener(panelDragTouchListener(target))
        }

        fun action(label: String, color: Int = 0xAA4B617A.toInt(), action: () -> Unit) =
            makeEditorButton(label, color).apply { setOnClickListener { action() } }

        editorToolsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(0xD83B4055.toInt(), 18f)
            visibility = View.GONE
        }
        val toolsContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        editorKindButton = makeEditorButton("+", 0xAA4B8F6E.toInt()).apply {
            textSize = 24f
            setOnClickListener { showAddItemFamilies() }
        }
        editorGridButton = makeEditorButton("", 0xAA735D91.toInt()).apply {
            setOnClickListener {
                editorGridIndex = (editorGridIndex + 1) % EDITOR_GRIDS.size
                pushEditorPreview()
            }
        }
        editorGroupButton = makeEditorButton("Groupe", 0xAA4B617A.toInt()).apply {
            setOnClickListener {
                editorGroupMode = editorGroupMode.next()
                if (editorGroupMode == EditorGroupMode.OFF) selectedGroupIds = emptySet()
                pushEditorPreview()
            }
        }
        listOf(editorKindButton, editorGridButton, editorGroupButton).forEachIndexed { index, view ->
            toolsContent.addView(view, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                if (index < 2) rightMargin = dp(6)
            })
        }
        editorToolsPanel.orientation = LinearLayout.VERTICAL
        editorToolsPanel.addView(grab("Outils", editorToolsPanel), LinearLayout.LayoutParams(-1, dp(28)))
        editorToolsPanel.addView(toolsContent, LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(editorToolsPanel, FrameLayout.LayoutParams(dp(286), -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(14)
        })

        editorCameraPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBackground(0xD83B4055.toInt(), 18f)
            visibility = View.GONE
        }
        val cameraUp = action("Cam +") { renderer.moveEditorCameraHeight(gridSize()) }
        val cameraDown = action("Cam -") { renderer.moveEditorCameraHeight(-gridSize()) }
        bindRepeatingEditorAction(cameraUp) { renderer.moveEditorCameraHeight(gridSize()) }
        bindRepeatingEditorAction(cameraDown) { renderer.moveEditorCameraHeight(-gridSize()) }
        editorCameraPanel.addView(cameraUp, LinearLayout.LayoutParams(dp(78), dp(42)).apply { bottomMargin = dp(6) })
        editorCameraPanel.addView(cameraDown, LinearLayout.LayoutParams(dp(78), dp(42)))
        root.addView(editorCameraPanel, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(16)
            bottomMargin = dp(24)
        })

        editorPanel = makeBubble()
        editorPanel.addView(grab("Bloc", editorPanel), LinearLayout.LayoutParams(-1, dp(28)).apply {
            bottomMargin = dp(6)
        })
        editorInfo = label("", 12f).apply {
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(4))
        }
        editorPanel.addView(editorInfo, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        editorSolidButton = makeEditorButton("", 0xAA735D91.toInt()).apply {
            setOnClickListener {
                editorSolid = !editorSolid
                updateSelectedVolume { it.copy(solid = editorSolid) }
                pushEditorPreview()
            }
        }
        editorColorButton = makeEditorButton("", 0xAA4B617A.toInt()).apply {
            setOnClickListener { showEditorColorPicker() }
        }
        row(editorPanel, editorSolidButton, editorColorButton)
        val dimensions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        editorPanel.addView(dimensions, LinearLayout.LayoutParams(-1, dp(116)).apply { bottomMargin = dp(8) })
        fun dimensionPicker(title: String, initial: Float, changed: (Float) -> Unit): NumberPicker {
            val picker = NumberPicker(this).apply {
                minValue = EDITOR_DIMENSION_MIN_TICKS
                maxValue = dimensionMaxTick()
                wrapSelectorWheel = false
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                setFormatter { formatEditorNumber(it * gridSize()) }
                value = dimensionToTick(initial)
                setOnValueChangedListener { _, _, newValue ->
                    if (!syncingEditorPickers) changed(newValue * gridSize())
                }
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(label(title, 11f, bold = true), LinearLayout.LayoutParams(-1, -2))
                addView(picker, LinearLayout.LayoutParams(-1, dp(88)))
            }
            dimensions.addView(box, LinearLayout.LayoutParams(0, -1, 1f))
            return picker
        }
        editorWidthPicker = dimensionPicker("Largeur", editorWidth) { setEditorDimension(width = it) }
        editorHeightPicker = dimensionPicker("Hauteur", editorHeight) { setEditorDimension(height = it) }
        editorDepthPicker = dimensionPicker("Profondeur", editorDepth) { setEditorDimension(depth = it) }
        editorPlaceButton = makeEditorButton("Poser", 0xAA4B8F6E.toInt()).apply {
            setOnClickListener { placeEditorVolume() }
        }
        row(
            editorPanel,
            editorPlaceButton,
            action("Effacer", 0xAA9A4B4B.toInt()) { deleteEditorVolume() }
        )

        root.addView(editorPanel, FrameLayout.LayoutParams(dp(286), -2).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dp(14)
            topMargin = dp(70)
        })

        editorPositionPanel = makeBubble()
        editorPositionPanel.addView(grab("Position", editorPositionPanel), LinearLayout.LayoutParams(-1, dp(28)).apply {
            bottomMargin = dp(8)
        })
        val moveUp = action("↑") { moveEditorObject(0f, 1f) }
        val moveLeft = action("←") { moveEditorObject(1f, 0f) }
        val moveRight = action("→") { moveEditorObject(-1f, 0f) }
        val moveDown = action("↓") { moveEditorObject(0f, -1f) }
        val moveHigher = action("Y +") { moveEditorFloor(gridSize()) }
        val moveLower = action("Y -") { moveEditorFloor(-gridSize()) }
        val rotateLeft = action("↺ 90") { rotateEditorObject(-1) }
        val rotateRight = action("↻ 90") { rotateEditorObject(1) }
        bindRepeatingEditorAction(moveUp) { moveEditorObject(0f, 1f) }
        bindRepeatingEditorAction(moveLeft) { moveEditorObject(1f, 0f) }
        bindRepeatingEditorAction(moveRight) { moveEditorObject(-1f, 0f) }
        bindRepeatingEditorAction(moveDown) { moveEditorObject(0f, -1f) }
        bindRepeatingEditorAction(moveHigher) { moveEditorFloor(gridSize()) }
        bindRepeatingEditorAction(moveLower) { moveEditorFloor(-gridSize()) }
        row(
            editorPositionPanel,
            label(""),
            moveUp,
            label(""),
            bottomDp = 2
        )
        row(
            editorPositionPanel,
            moveLeft,
            label(""),
            moveRight,
            bottomDp = 2
        )
        row(
            editorPositionPanel,
            label(""),
            moveDown,
            label(""),
            bottomDp = 0
        )
        row(editorPositionPanel, moveHigher, moveLower)
        row(editorPositionPanel, rotateLeft, rotateRight, bottomDp = 0)
        root.addView(editorPositionPanel, FrameLayout.LayoutParams(dp(206), -2).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(18)
            bottomMargin = dp(22)
        })
        pauseButton.bringToFront()
        pushEditorPreview()
    }

    private fun toggleEditorMode() {
        editorActive = !editorActive
        releaseControls.forEach { it() }
        resultPanel.visibility = View.GONE
        raceHudViews.forEach { it.visibility = if (editorActive) View.GONE else View.VISIBLE }
        raceControlViews.forEach { it.visibility = if (editorActive) View.GONE else View.VISIBLE }
        editorPanel.visibility = if (editorActive) View.VISIBLE else View.GONE
        editorToolsPanel.visibility = if (editorActive) View.VISIBLE else View.GONE
        editorPositionPanel.visibility = if (editorActive) View.VISIBLE else View.GONE
        editorCameraPanel.visibility = if (editorActive) View.VISIBLE else View.GONE
        editorTouchLayer.visibility = if (editorActive) View.VISIBLE else View.GONE
        minimap.visibility = if (editorActive) View.GONE else View.VISIBLE
        renderer.setEditorActive(editorActive)
        pushEditorInput()
        pushEditorPreview()
        pauseButton.bringToFront()
        if (editorActive) {
            status.text = "Mode construction : déplace la caméra, règle le bloc, puis POSER"
        }
    }

    private fun applyEditorItemPreset(item: EditorItemPreset) {
        selectedVolumeId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = true
        editorDraftDecorModelId = null
        editorKind = item.kind
        editorWidth = item.width
        editorHeight = item.height
        editorDepth = item.depth
        editorQuarterTurns = 0
        editorFloorY = maxOf(editorFloorY, item.minimumFloorY)
        editorSolid = item.kind.solidByDefault
        editorColor = item.kind.color
        renderer.resetEditorPreviewAnchor()
        pushEditorPreview()
    }

    private fun applyEditorDecorModel(model: DecorModel) {
        selectedVolumeId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftDecorModelId = model.id
        editorQuarterTurns = 0
        editorDecorQuarterTurns = 0
        editorDecorScale = 1f
        editorFloorY = maxOf(editorFloorY, 0f)
        renderer.resetEditorPreviewAnchor()
        pushEditorPreview()
    }

    private fun resizeEditor(widthDelta: Float, heightDelta: Float, depthDelta: Float) {
        if (selectedVolumeId == null && !editorDraftActive) return
        editorWidth = (editorWidth + widthDelta).coerceAtLeast(gridSize())
        editorHeight = (editorHeight + heightDelta).coerceAtLeast(gridSize())
        editorDepth = (editorDepth + depthDelta).coerceAtLeast(gridSize())
        updateSelectedVolume { it.copy(width = editorWidth, height = editorHeight, depth = editorDepth) }
        pushEditorPreview()
    }

    private fun setEditorDimension(width: Float? = null, height: Float? = null, depth: Float? = null) {
        if (selectedVolumeId == null && !editorDraftActive) return
        editorWidth = (width ?: editorWidth).coerceAtLeast(gridSize())
        editorHeight = (height ?: editorHeight).coerceAtLeast(gridSize())
        editorDepth = (depth ?: editorDepth).coerceAtLeast(gridSize())
        updateSelectedVolume { it.copy(width = editorWidth, height = editorHeight, depth = editorDepth) }
        pushEditorPreview()
    }

    private fun showAddItemFamilies() {
        val families = EDITOR_ITEM_FAMILIES
        val labels = families.map { it.name } + "Modeles 3D"
        dialogBuilder()
            .setTitle("Ajouter un objet")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < families.size) showAddItems(families[which]) else showDecorModelFamilies()
            }
            .show()
    }

    private fun showAddItems(family: EditorItemFamily) {
        dialogBuilder()
            .setTitle(family.name)
            .setItems(family.items.map { it.name }.toTypedArray()) { _, which ->
                applyEditorItemPreset(family.items[which])
            }
            .setNegativeButton("Retour") { _, _ -> showAddItemFamilies() }
            .show()
    }

    private fun showDecorModelFamilies() {
        val families = decorModelFamilies()
        dialogBuilder()
            .setTitle("Modeles 3D")
            .setItems(families.map { "${it.name} (${it.items.size})" }.toTypedArray()) { _, which ->
                showDecorModelList(families[which])
            }
            .setNegativeButton("Retour") { _, _ -> showAddItemFamilies() }
            .show()
    }

    private fun showDecorModelList(family: DecorModelFamily) {
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        scroll.addView(list)
        val dialog = dialogBuilder()
            .setTitle(family.name)
            .setView(scroll)
            .setNegativeButton("Retour") { _, _ -> showDecorModelFamilies() }
            .create()
        family.items.forEach { model ->
            list.addView(decorModelRow(model, dialog), LinearLayout.LayoutParams(-1, dp(74)).apply {
                bottomMargin = dp(7)
            })
        }
        dialog.show()
    }

    private fun decorModelRow(model: DecorModel, dialog: AlertDialog): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(10), dp(6))
            background = roundedBackground(0xAA4B617A.toInt(), 14f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                applyEditorDecorModel(model)
            }
        }
        row.addView(DecorModelThumbnailView(this, model), LinearLayout.LayoutParams(dp(62), dp(62)).apply {
            rightMargin = dp(10)
        })
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        textBox.addView(TextView(this).apply {
            text = decorName(model.id)
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        textBox.addView(TextView(this).apply {
            text = "${decorRoomLabel(model.room)}  |  ${model.parts.size} pieces"
            setTextColor(0xFFFFE7A8.toInt())
            textSize = 11f
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, -1, 1f))
        return row
    }

    private fun moveEditorFloor(delta: Float) {
        if (selectedDecorId != null || editorDraftDecorModelId != null) {
            editorFloorY += delta
            updateSelectedDecor { it.copy(y = editorFloorY) }
            pushEditorPreview()
            return
        }
        if (selectedVolumeId == null && !editorDraftActive) return
        editorFloorY += delta
        updateSelectedVolume { volume ->
            val centerY = if (volume.kind == ToyboxVolumeKind.FLOOR) editorFloorY - volume.height * 0.5f
                else editorFloorY + volume.height * 0.5f
            volume.copy(y = centerY)
        }
        pushEditorPreview()
    }

    private fun placeEditorVolume() {
        if (selectedVolumeId != null) {
            clearEditorSelection()
            return
        }
        if (selectedDecorId != null) {
            clearEditorSelection()
            return
        }
        if (selectedGroupIds.isNotEmpty()) {
            clearEditorSelection()
            return
        }
        editorDraftDecorModelId?.let { modelId ->
            val anchor = renderer.makePreviewVolume(System.nanoTime())
            val decor = ToyboxDecor(
                id = System.nanoTime(),
                modelId = modelId,
                x = anchor.x,
                y = editorFloorY,
                z = anchor.z,
                quarterTurns = editorDecorQuarterTurns,
                scale = editorDecorScale
            )
            editorWorld = editorWorld.copy(decorations = editorWorld.decorations + decor)
            editorDraftDecorModelId = null
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }
        if (!editorDraftActive) return
        val volume = renderer.makePreviewVolume(System.nanoTime())
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes + volume)
        editorDraftActive = false
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
        pushEditorPreview()
    }

    private fun deleteEditorVolume() {
        if (selectedGroupIds.isNotEmpty()) {
            editorWorld = editorWorld.copy(volumes = editorWorld.volumes.filterNot { it.id in selectedGroupIds })
            selectedGroupIds = emptySet()
            selectedVolumeId = null
            selectedDecorId = null
            editorDraftActive = false
            editorDraftDecorModelId = null
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }
        val selectedDecor = selectedDecorId
        if (selectedDecor != null) {
            editorWorld = editorWorld.copy(decorations = editorWorld.decorations.filterNot { it.id == selectedDecor })
            selectedDecorId = null
            editorDraftDecorModelId = null
            editorDraftActive = false
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }
        val selected = selectedVolumeId
        if (selected == null) return
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes.filterNot { it.id == selected })
        selectedVolumeId = null
        editorDraftActive = false
        editorDraftDecorModelId = null
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
        pushEditorPreview()
    }

    private fun pushEditorInput() {
        renderer.setEditorInput(editorStrafe, editorForward, editorYaw, editorPitch)
    }

    private fun pushEditorPreview() {
        if (!::editorKindButton.isInitialized) return
        val selected = selectedVolume()
        val selectedDecor = selectedDecor()
        val draftDecorModel = editorDraftDecorModelId?.let { runCatching { DecorCatalog[it] }.getOrNull() }
        val hasDraft = selected == null && selectedDecor == null && editorDraftActive
        val hasDecorDraft = selected == null && selectedDecor == null && draftDecorModel != null
        val hasGroup = selectedGroupIds.isNotEmpty()
        val canEditBlock = selected != null || hasDraft
        val canEditDecor = selectedDecor != null || hasDecorDraft
        editorPanel.visibility = if (editorActive && (canEditBlock || canEditDecor) && !hasGroup) View.VISIBLE else View.GONE
        editorPositionPanel.visibility = if (editorActive && (canEditBlock || canEditDecor || hasGroup)) View.VISIBLE else View.GONE
        editorToolsPanel.visibility = if (editorActive) View.VISIBLE else View.GONE
        editorCameraPanel.visibility = if (editorActive) View.VISIBLE else View.GONE
        editorKindButton.text = "+"
        editorGridButton.text = "Grille ${gridLabel()}"
        editorGroupButton.text = when {
            hasGroup -> "Groupe ${selectedGroupIds.size}"
            editorGroupMode == EditorGroupMode.SAME_KIND -> "Groupe type"
            editorGroupMode == EditorGroupMode.CONNECTED -> "Prefab"
            else -> "Groupe"
        }
        editorGroupButton.alpha = if (editorGroupMode == EditorGroupMode.OFF) 0.82f else 1f
        editorSolidButton.text = if (editorSolid) "Solide" else "Décor"
        editorPlaceButton.text = when {
            selected != null -> "Valider"
            selectedDecor != null -> "Valider"
            hasGroup -> "Valider"
            hasDecorDraft -> "Poser"
            hasDraft -> "Poser"
            else -> "Rien"
        }
        listOf(editorSolidButton, editorColorButton, editorPlaceButton).forEach {
            it.isEnabled = canEditBlock || it === editorPlaceButton
            it.alpha = if (it.isEnabled && (canEditBlock || it === editorPlaceButton)) 0.82f else 0.38f
        }
        if (::editorWidthPicker.isInitialized) {
            syncingEditorPickers = true
            val maxTick = dimensionMaxTick()
            listOf(editorWidthPicker, editorHeightPicker, editorDepthPicker).forEach { picker ->
                picker.maxValue = maxTick
            }
            editorWidthPicker.value = dimensionToTick(editorWidth)
            editorHeightPicker.value = dimensionToTick(editorHeight)
            editorDepthPicker.value = dimensionToTick(editorDepth)
            listOf(editorWidthPicker, editorHeightPicker, editorDepthPicker).forEach { picker ->
                picker.setFormatter { formatEditorNumber(it * gridSize()) }
                picker.invalidate()
            }
            syncingEditorPickers = false
        }
        editorColorButton.text = ""
        editorColorButton.background = roundedBackground(editorColor, 16f)
        editorInfo.text = buildString {
            append(when {
                selected != null -> "Bloc selectionne #${selected.id.toString().takeLast(4)}"
                selectedDecor != null -> "Modele 3D: ${decorName(selectedDecor.modelId)}"
                hasGroup -> "Groupe selectionne (${selectedGroupIds.size} blocs)"
                hasDecorDraft -> "Nouveau modele 3D: ${draftDecorModel?.let { decorName(it.id) } ?: "inconnu"}"
                hasDraft -> "Nouveau bloc"
                else -> "Aucun bloc selectionne"
            })
            append("\n")
            if (canEditDecor) {
                val turns = selectedDecor?.quarterTurns ?: editorDecorQuarterTurns
                val scale = selectedDecor?.scale ?: editorDecorScale
                append("Type: modele 3D  ").append(gridLabel())
                append("  Tour ").append(((turns % 4) + 4) % 4 * 90).append(" deg")
                append("  Echelle ").append(formatEditorNumber(scale))
            } else {
                append("Type: ").append(editorKind.label).append("  ").append(gridLabel())
                append("  L ").append(formatEditorNumber(editorWidth))
                append("  P ").append(formatEditorNumber(editorDepth))
                append("  H ").append(formatEditorNumber(editorHeight))
                append("  Tour ").append(((editorQuarterTurns % 4) + 4) % 4 * 90).append(" deg")
            }
            append("  Y ").append(formatEditorNumber(editorFloorY))
            append("\n")
            append(if (canEditDecor) "modele procedural 3D" else if (editorSolid) "solide" else "decor seulement")
            append("  ").append(editorWorld.volumes.size + editorWorld.decorations.size).append(" objets")
            if (selected == null && selectedDecor == null) {
                append(if (hasDraft || hasDecorDraft) "  |  Poser pour creer" else "  |  + pour ajouter")
            }
        }
        renderer.setEditorSelection(selected)
        renderer.setEditorPreviewVisible(!hasGroup && !canEditDecor && (selected != null || hasDraft))
        renderer.setEditorPreview(editorKind, editorWidth, editorHeight, editorDepth, gridSize(), editorFloorY, editorSolid, editorColor, editorQuarterTurns)
        renderer.setEditorDecorPreview(if (hasDecorDraft) {
            val anchor = renderer.makePreviewVolume(-2L)
            ToyboxDecor(
                id = -2L,
                modelId = draftDecorModel!!.id,
                x = anchor.x,
                y = editorFloorY,
                z = anchor.z,
                quarterTurns = editorDecorQuarterTurns,
                scale = editorDecorScale
            )
        } else null)
    }

    private fun selectConnectedGroup(startId: Long, sameKindOnly: Boolean) {
        val start = editorWorld.volumes.firstOrNull { it.id == startId } ?: return
        val selected = LinkedHashSet<Long>()
        val queue = ArrayDeque<ToyboxVolume>()
        selected += start.id
        queue += start
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            editorWorld.volumes.forEach { candidate ->
                if (candidate.id in selected) return@forEach
                if ((!sameKindOnly || candidate.kind == start.kind) && current.touches(candidate)) {
                    selected += candidate.id
                    queue += candidate
                }
            }
        }
        selectedVolumeId = null
        selectedDecorId = null
        editorDraftActive = false
        editorDraftDecorModelId = null
        selectedGroupIds = selected
        editorKind = start.kind
        editorSolid = start.solid
        editorColor = start.color
        editorFloorY = if (start.kind == ToyboxVolumeKind.FLOOR) start.y + start.height * 0.5f
            else start.y - start.height * 0.5f
        pushEditorPreview()
    }

    private fun selectEditorVolume(id: Long) {
        val volume = editorWorld.volumes.firstOrNull { it.id == id } ?: return
        selectedVolumeId = id
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftDecorModelId = null
        editorKind = volume.kind
        editorWidth = volume.width
        editorHeight = volume.height
        editorDepth = volume.depth
        editorQuarterTurns = volume.quarterTurns
        editorSolid = volume.solid
        editorColor = volume.color
        editorFloorY = if (volume.kind == ToyboxVolumeKind.FLOOR) volume.y + volume.height * 0.5f
            else volume.y - volume.height * 0.5f
        pushEditorPreview()
    }

    private fun selectEditorDecor(id: Long) {
        val decor = editorWorld.decorations.firstOrNull { it.id == id } ?: return
        selectedVolumeId = null
        selectedDecorId = id
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftDecorModelId = null
        editorFloorY = decor.y
        editorDecorQuarterTurns = decor.quarterTurns
        editorDecorScale = decor.scale
        pushEditorPreview()
    }

    private fun ToyboxVolume.touches(other: ToyboxVolume): Boolean {
        val epsilon = 0.06f
        val xTouch = kotlin.math.abs(right - other.left) <= epsilon || kotlin.math.abs(left - other.right) <= epsilon
        val zTouch = kotlin.math.abs(front - other.back) <= epsilon || kotlin.math.abs(back - other.front) <= epsilon
        val xOverlap = right >= other.left - epsilon && left <= other.right + epsilon
        val zOverlap = front >= other.back - epsilon && back <= other.front + epsilon
        val yOverlap = top() >= other.bottom() - epsilon && bottom() <= other.top() + epsilon
        return yOverlap && ((xTouch && zOverlap) || (zTouch && xOverlap))
    }

    private fun ToyboxVolume.bottom() = y - height * 0.5f

    private fun ToyboxVolume.top() = y + height * 0.5f

    private fun moveSelectedGroup(dx: Float, dz: Float) {
        val group = selectedGroupIds
        if (group.isEmpty()) return
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes.map { volume ->
            if (volume.id in group) volume.copy(
                x = snapEditor(volume.x + dx),
                z = snapEditor(volume.z + dz)
            ) else volume
        })
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
    }

    private fun rotateSelectedGroup(deltaTurns: Int) {
        val group = selectedGroupIds
        if (group.isEmpty()) return
        val volumes = editorWorld.volumes.filter { it.id in group }
        if (volumes.isEmpty()) return
        val centerX = volumes.map { it.x }.average().toFloat()
        val centerZ = volumes.map { it.z }.average().toFloat()
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes.map { volume ->
            if (volume.id !in group) volume else {
                val dx = volume.x - centerX
                val dz = volume.z - centerZ
                volume.rotateQuarter(deltaTurns).copy(
                    x = snapEditor(centerX + dz * deltaTurns),
                    z = snapEditor(centerZ - dx * deltaTurns)
                )
            }
        })
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
    }

    private fun moveEditorObject(strafe: Float, forward: Float) {
        if (!editorActive) return
        if (selectedVolumeId == null && selectedDecorId == null && selectedGroupIds.isEmpty() && !editorDraftActive && editorDraftDecorModelId == null) return
        val delta = renderer.editorNudgeDelta(strafe, forward, gridSize())
        val selected = selectedVolumeId
        if (selectedGroupIds.isNotEmpty()) {
            moveSelectedGroup(delta.x, delta.z)
        } else if (selectedDecorId != null) {
            updateSelectedDecor { decor ->
                decor.copy(
                    x = snapEditor(decor.x + delta.x),
                    z = snapEditor(decor.z + delta.z)
                )
            }
        } else if (selected == null) {
            renderer.moveEditorPreview(delta.x, delta.z)
        } else {
            updateSelectedVolume { volume ->
                volume.copy(
                    x = snapEditor(volume.x + delta.x),
                    z = snapEditor(volume.z + delta.z)
                )
            }
        }
        pushEditorPreview()
    }

    private fun rotateEditorObject(deltaTurns: Int) {
        if (selectedGroupIds.isNotEmpty()) {
            rotateSelectedGroup(deltaTurns)
            pushEditorPreview()
            return
        }
        if (selectedDecorId != null) {
            updateSelectedDecor { decor ->
                decor.rotateQuarter(deltaTurns).also { editorDecorQuarterTurns = it.quarterTurns }
            }
            pushEditorPreview()
            return
        }
        if (editorDraftDecorModelId != null) {
            editorDecorQuarterTurns = ((editorDecorQuarterTurns + deltaTurns) % 4 + 4) % 4
            pushEditorPreview()
            return
        }
        if (selectedVolumeId == null && !editorDraftActive) return
        if (selectedVolumeId == null) editorQuarterTurns = ((editorQuarterTurns + deltaTurns) % 4 + 4) % 4
        updateSelectedVolume { volume ->
            volume.rotateQuarter(deltaTurns).also {
                editorWidth = it.width
                editorDepth = it.depth
                editorQuarterTurns = it.quarterTurns
            }
        }
        pushEditorPreview()
    }

    private fun snapEditor(value: Float): Float {
        val grid = gridSize()
        return kotlin.math.round(value / grid) * grid
    }

    private fun clearEditorSelection() {
        selectedVolumeId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftDecorModelId = null
        editorQuarterTurns = 0
        pushEditorPreview()
    }

    private fun selectedVolume() = selectedVolumeId?.let { id -> editorWorld.volumes.firstOrNull { it.id == id } }

    private fun selectedDecor() = selectedDecorId?.let { id -> editorWorld.decorations.firstOrNull { it.id == id } }

    private fun updateSelectedVolume(change: (ToyboxVolume) -> ToyboxVolume) {
        val selected = selectedVolumeId ?: return
        var changed = false
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes.map { volume ->
            if (volume.id == selected) {
                changed = true
                change(volume)
            } else volume
        })
        if (changed) {
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
        }
    }

    private fun updateSelectedDecor(change: (ToyboxDecor) -> ToyboxDecor) {
        val selected = selectedDecorId ?: return
        var changed = false
        editorWorld = editorWorld.copy(decorations = editorWorld.decorations.map { decor ->
            if (decor.id == selected) {
                changed = true
                change(decor)
            } else decor
        })
        if (changed) {
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
        }
    }

    private fun showEditorColorPicker() {
        SimpleColorPickerDialog(
            this,
            currentColorHex = String.format(Locale.ROOT, "#%06X", editorColor and 0xFFFFFF),
            showTextMode = false
        ) { colorHex, _ ->
            editorColor = Color.parseColor(colorHex)
            updateSelectedVolume { it.copy(color = editorColor) }
            pushEditorPreview()
        }.show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindEditorHold(view: View, changed: (Boolean) -> Unit) {
        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    touchedView.isPressed = true
                    touchedView.alpha = 1f
                    changed(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                    touchedView.isPressed = false
                    touchedView.alpha = 0.82f
                    changed(false)
                }
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindRepeatingEditorAction(view: View, action: () -> Unit) {
        var activePointerId = MotionEvent.INVALID_POINTER_ID
        val repeater = object : Runnable {
            override fun run() {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID || !editorActive) return
                action()
                view.postDelayed(this, EDITOR_REPEAT_INTERVAL_MS)
            }
        }
        fun release(touchedView: View) {
            activePointerId = MotionEvent.INVALID_POINTER_ID
            touchedView.removeCallbacks(repeater)
            touchedView.isPressed = false
            touchedView.alpha = 0.82f
        }
        view.setOnTouchListener { touchedView, event ->
            if (!editorActive) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(event.actionIndex)
                    touchedView.isPressed = true
                    touchedView.alpha = 1f
                    action()
                    touchedView.removeCallbacks(repeater)
                    touchedView.postDelayed(repeater, EDITOR_REPEAT_INITIAL_DELAY_MS)
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.getPointerId(event.actionIndex) == activePointerId) release(touchedView)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release(touchedView)
            }
            true
        }
    }

    private fun makeEditorButton(label: String, color: Int = 0xAA4B617A.toInt()): Button =
        makeButton(label, 72, color).apply { textSize = 12f }

    private fun gridSize() = EDITOR_GRIDS[editorGridIndex].first

    private fun gridLabel() = EDITOR_GRIDS[editorGridIndex].second

    private fun dimensionStep() = maxOf(gridSize(), 0.25f)

    private fun formatEditorNumber(value: Float): String =
        if (kotlin.math.abs(value - value.toInt()) < 0.001f) value.toInt().toString()
        else String.format(Locale.ROOT, "%.2f", value)

    private fun decorModelFamilies(): List<DecorModelFamily> =
        DECOR_ROOM_ORDER.mapNotNull { room ->
            DecorCatalog.forRoom(room).takeIf { it.isNotEmpty() }?.let { DecorModelFamily(decorRoomLabel(room), it) }
        }

    private fun decorRoomLabel(room: DecorRoom): String = when (room) {
        DecorRoom.KITCHEN -> "Cuisine"
        DecorRoom.LIVING_ROOM -> "Salon"
        DecorRoom.GARAGE -> "Garage"
        DecorRoom.OFFICE -> "Bureau"
        DecorRoom.BATHROOM -> "Salle de bain"
        DecorRoom.OUTDOOR -> "Exterieur"
        DecorRoom.BEDROOM -> "Chambre"
    }

    private fun decorName(modelId: String): String =
        modelId.substringAfter('.')
            .replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

    @SuppressLint("ClickableViewAccessibility")
    private fun panelDragTouchListener(target: View): View.OnTouchListener {
        var lastRawX = 0f
        var lastRawY = 0f
        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val parent = target.parent as? View ?: return@OnTouchListener true
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    val minX = -target.left.toFloat()
                    val maxX = (parent.width - target.right).toFloat()
                    val minY = -target.top.toFloat()
                    val maxY = (parent.height - target.bottom).toFloat()
                    target.translationX = (target.translationX + dx).coerceIn(minX, maxX)
                    target.translationY = (target.translationY + dy).coerceIn(minY, maxY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun dimensionToTick(value: Float): Int =
        kotlin.math.round(value / gridSize())
            .toInt()
            .coerceIn(EDITOR_DIMENSION_MIN_TICKS, dimensionMaxTick())

    private fun dimensionMaxTick(): Int =
        kotlin.math.round(EDITOR_DIMENSION_MAX_SIZE / gridSize())
            .toInt()
            .coerceAtLeast(EDITOR_DIMENSION_MIN_TICKS)

    @SuppressLint("ClickableViewAccessibility")
    private fun bindHoldButton(view: View, changed: (Boolean) -> Unit) {
        var activePointerId = MotionEvent.INVALID_POINTER_ID
        releaseControls += {
            activePointerId = MotionEvent.INVALID_POINTER_ID
            view.isPressed = false
            view.alpha = 0.82f
            changed(false)
        }
        view.setOnTouchListener { touchedView, event ->
            if (paused || editorActive) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(event.actionIndex)
                    touchedView.isPressed = true
                    touchedView.alpha = 1f
                    changed(true)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
                        activePointerId = event.getPointerId(event.actionIndex)
                        touchedView.isPressed = true
                        touchedView.alpha = 1f
                        changed(true)
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val liftedPointerId = event.getPointerId(event.actionIndex)
                    // Lever le doigt de direction ne doit jamais couper GAZ,
                    // et inversement. Seul le doigt propriétaire libère le bouton.
                    if (liftedPointerId == activePointerId) {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        touchedView.isPressed = false
                        touchedView.alpha = 0.82f
                        changed(false)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    touchedView.isPressed = false
                    touchedView.alpha = 0.82f
                    changed(false)
                }
            }
            true
        }
    }

    private fun updateHud(state: ToyboxRacersRenderer.HudState) {
        if (isFinishing || isDestroyed || editorActive || state.mode != currentMode || state.scene != currentScene) return
        minimap.update(state)
        if (state.turboLevel > lastTurboLevel) vibrate(18L, 35 + state.turboLevel * 20)
        if (state.turboReleaseSerial != lastTurboReleaseSerial) vibrate(42L, 105)
        lastTurboLevel = state.turboLevel
        lastTurboReleaseSerial = state.turboReleaseSerial
        val minutes = state.elapsedSeconds.toInt() / 60
        val seconds = state.elapsedSeconds % 60f
        hud.text = if (state.mode == PlayMode.EXPLORATION) getString(R.string.toybox_free_hud, state.speedKmh, formatTime(state.elapsedSeconds)) else String.format(
            Locale.ROOT,
            "%3d km/h   ·   %d/6   ·   Tour %d/%d   ·   %02d:%04.1f",
            state.speedKmh,
            state.position,
            state.lap,
            RaceSession.TOTAL_LAPS,
            minutes,
            seconds
        )
        val racing = state.mode == PlayMode.RACE
        difficultyButton.isEnabled = !racing || state.racePhase == RacePhase.FINISHED
        difficultyButton.alpha = if (difficultyButton.isEnabled) 0.82f else 0.4f
        countdown.visibility = if (racing && state.racePhase == RacePhase.COUNTDOWN) View.VISIBLE else View.GONE
        if (racing && state.racePhase == RacePhase.COUNTDOWN) {
            countdown.text = kotlin.math.ceil(state.countdownSeconds.coerceAtMost(3f))
                .toInt().coerceAtLeast(1).toString()
        }
        if (racing && state.finishSerial != lastFinishSerial && state.racePhase == RacePhase.FINISHED) {
            lastFinishSerial = state.finishSerial
            showResult(state)
        }
        status.text = when {
            racing && state.racePhase == RacePhase.COUNTDOWN -> "Prépare-toi — le départ est verrouillé"
            racing && state.racePhase == RacePhase.FINISHED -> "Course terminée"
            racing && state.wrongWay -> "MAUVAIS SENS — fais demi-tour"
            state.airborne -> "SAUT !  Prépare la réception"
            state.reversing -> "MARCHE ARRIÈRE — relâche FREIN pour repartir"
            state.turboBoosting -> "RUBAN TURBO !  Relance pastel"
            state.drifting -> "Ruban ${"●".repeat(state.turboLevel.coerceAtLeast(1))}${"○".repeat((3 - state.turboLevel).coerceAtLeast(0))}  ${(state.turboCharge * 100).toInt()} %"
            state.offRoad -> getString(if (racing) R.string.toybox_return_track else R.string.toybox_explore_hint)
            state.scene.circuit == CircuitKind.SLALOM -> getString(R.string.toybox_slalom_hint)
            else -> "Maintiens GAZ — tourne, puis redresse pour le Ruban Turbo"
        }
    }

    private fun showResult(state: ToyboxRacersRenderer.HudState) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val key = KEY_BEST_PREFIX + state.scene.room.name + "_" + state.scene.circuit.name + "_" + state.difficulty.name
        val legacyBest = if (state.scene == SceneChoice()) prefs.getFloat(KEY_BEST_PREFIX + state.difficulty.name, 0f) else 0f
        val previousBest = prefs.getFloat(key, legacyBest)
        val newBest = previousBest <= 0f || state.elapsedSeconds < previousBest
        val best = if (newBest) state.elapsedSeconds else previousBest
        if (newBest) prefs.edit().putFloat(key, state.elapsedSeconds).apply()
        resultText.text = buildString {
            append(positionLabel(state.finishPosition)).append(" place\n")
            append(formatTime(state.elapsedSeconds)).append(" · ").append(state.difficulty.label)
            append("\n").append(roomLabel()).append(" · ").append(circuitLabel())
            append("\nMeilleur : ").append(formatTime(best))
            if (newBest) append("  ★ NOUVEAU RECORD")
        }
        resultPanel.visibility = View.VISIBLE
    }

    private fun positionLabel(position: Int) = if (position == 1) "1re" else "${position}e"

    private fun formatTime(seconds: Float): String = String.format(
        Locale.ROOT,
        "%02d:%04.1f",
        seconds.toInt() / 60,
        seconds % 60f
    )

    @Suppress("DEPRECATION")
    private fun vibrate(durationMs: Long, amplitude: Int) {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (vibrator.hasVibrator()) {
            try {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } catch (_: SecurityException) {
                // Le retour haptique reste facultatif : un profil qui refuse le
                // vibreur ne doit jamais interrompre une partie.
            }
        }
    }

    private fun makeButton(label: String, sizeDp: Int, color: Int): Button = Button(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = if (sizeDp >= 70) 24f else 16f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        includeFontPadding = false
        alpha = 0.82f
        background = roundedBackground(color, sizeDp * 0.34f)
        stateListAnimator = null
        setPadding(dp(4), dp(2), dp(4), dp(2))
    }

    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
        setStroke(dp(2), 0x66FFFFFF)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    internal data class ControlDimensions(
        val steering: Int,
        val brake: Int,
        val accelerator: Int,
        val gap: Int
    ) {
        companion object {
            /** Trois tailles gardent les commandes accessibles du petit au grand écran. */
            fun forWidth(widthDp: Float): ControlDimensions = when {
                widthDp < 640f -> ControlDimensions(70, 80, 96, 8)
                widthDp < 840f -> ControlDimensions(78, 88, 108, 10)
                else -> ControlDimensions(88, 98, 118, 12)
            }
        }
    }

    private data class EditorItemFamily(
        val name: String,
        val items: List<EditorItemPreset>
    )

    private data class DecorModelFamily(
        val name: String,
        val items: List<DecorModel>
    )

    private data class EditorItemPreset(
        val name: String,
        val kind: ToyboxVolumeKind,
        val width: Float,
        val height: Float,
        val depth: Float,
        val minimumFloorY: Float = 0f
    )

    private enum class EditorGroupMode {
        OFF,
        SAME_KIND,
        CONNECTED;

        fun next() = when (this) {
            OFF -> SAME_KIND
            SAME_KIND -> CONNECTED
            CONNECTED -> OFF
        }
    }

    companion object {
        private const val PREFS_NAME = "toybox_racers_save"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_BEST_PREFIX = "best_time_"
        private const val KEY_HOUSE_SEED = "house_seed_v1"
        private const val KEY_ROOM_CIRCUIT = "house_circuit_v1_"
        private val EDITOR_GRIDS = arrayOf(
            1f to "10cm",
            0.1f to "1cm",
            0.01f to "0.1cm"
        )
        private const val EDITOR_DIMENSION_MIN_TICKS = 1
        private const val EDITOR_DIMENSION_MAX_SIZE = 100f
        private const val EDITOR_REPEAT_INITIAL_DELAY_MS = 260L
        private const val EDITOR_REPEAT_INTERVAL_MS = 82L
        private val DECOR_ROOM_ORDER = listOf(
            DecorRoom.GARAGE,
            DecorRoom.KITCHEN,
            DecorRoom.LIVING_ROOM,
            DecorRoom.BEDROOM,
            DecorRoom.OFFICE,
            DecorRoom.BATHROOM,
            DecorRoom.OUTDOOR
        )
        private val EDITOR_ITEM_FAMILIES = listOf(
            EditorItemFamily(
                "Construction",
                listOf(
                    EditorItemPreset("Sol", ToyboxVolumeKind.FLOOR, 24f, 0.6f, 24f),
                    EditorItemPreset("Mur", ToyboxVolumeKind.WALL, 18f, 8f, 1f),
                    EditorItemPreset("Plafond", ToyboxVolumeKind.FLOOR, 24f, 0.5f, 24f, minimumFloorY = 8f),
                    EditorItemPreset("Rambarde", ToyboxVolumeKind.RAIL, 14f, 3.2f, 0.8f)
                )
            ),
            EditorItemFamily(
                "Ouvertures",
                listOf(
                    EditorItemPreset("Porte", ToyboxVolumeKind.DOOR, 5f, 7f, 0.8f),
                    EditorItemPreset("Fenetre", ToyboxVolumeKind.WINDOW, 6f, 4f, 0.5f, minimumFloorY = 3f)
                )
            ),
            EditorItemFamily(
                "Circulation",
                listOf(
                    EditorItemPreset("Planche / rampe", ToyboxVolumeKind.RAMP, 8f, 4f, 18f),
                    EditorItemPreset("Escalier", ToyboxVolumeKind.STAIR, 8f, 4f, 14f),
                    EditorItemPreset("Conduit large", ToyboxVolumeKind.DUCT, 12f, 5f, 22f)
                )
            )
        )
    }
}

private class DecorModelThumbnailView(
    context: android.content.Context,
    private val model: DecorModel
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = 10f * resources.displayMetrics.density
        paint.style = Paint.Style.FILL
        paint.color = 0x6630364A
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)

        val bounds = model.bounds
        val spanX = maxOf(1f, bounds.width)
        val spanZ = maxOf(1f, bounds.depth)
        val spanY = maxOf(1f, bounds.height)
        val visualWidth = (spanX + spanZ) * 0.72f
        val visualHeight = (spanX + spanZ) * 0.34f + spanY * 0.72f
        val scale = minOf(width * 0.72f / visualWidth, height * 0.72f / visualHeight).coerceAtLeast(0.1f)
        val centerX = width * 0.5f
        val centerY = height * 0.66f + spanY * scale * 0.22f

        model.parts
            .sortedWith(compareBy({ it.x + it.z }, { it.y }))
            .forEach { part ->
                val sx = centerX + (part.x - part.z) * 0.72f * scale
                val sy = centerY + (part.x + part.z) * 0.34f * scale - part.y * 0.72f * scale
                val w = maxOf(3f, (part.width + part.depth) * 0.34f * scale)
                val h = maxOf(3f, part.height * 0.72f * scale)
                paint.color = 0xFF000000.toInt() or (part.color and 0x00FFFFFF)
                paint.alpha = if (part.solid) 255 else 170
                when (part.shape) {
                    DecorShape.OVAL, DecorShape.CYLINDER_Y, DecorShape.CYLINDER_X -> {
                        rect.set(sx - w * 0.5f, sy - h * 0.5f, sx + w * 0.5f, sy + h * 0.5f)
                        canvas.drawOval(rect, paint)
                    }
                    DecorShape.CONE_Y, DecorShape.GABLE_ROOF -> {
                        val path = android.graphics.Path().apply {
                            moveTo(sx, sy - h * 0.65f)
                            lineTo(sx + w * 0.55f, sy + h * 0.45f)
                            lineTo(sx - w * 0.55f, sy + h * 0.45f)
                            close()
                        }
                        canvas.drawPath(path, paint)
                    }
                    else -> {
                        rect.set(sx - w * 0.5f, sy - h * 0.5f, sx + w * 0.5f, sy + h * 0.5f)
                        canvas.drawRoundRect(rect, 3f, 3f, paint)
                    }
                }
                paint.alpha = 255
            }
    }
}
