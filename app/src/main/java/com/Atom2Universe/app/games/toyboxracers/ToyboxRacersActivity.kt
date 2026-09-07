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
import com.Atom2Universe.app.games.toyboxracers.editor.ActiveWorldKind
import com.Atom2Universe.app.games.toyboxracers.editor.EditorTouchLayer
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxDecor
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxTrackSection
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolume
import com.Atom2Universe.app.games.toyboxracers.game.PlayMode
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import com.Atom2Universe.app.games.toyboxracers.track.RoomKind
import com.Atom2Universe.app.games.toyboxracers.track.CircuitKind
import com.Atom2Universe.app.games.toyboxracers.track.HouseGeometry
import com.Atom2Universe.app.games.toyboxracers.track.HousePlan
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.RaceLayouts
import com.Atom2Universe.app.games.toyboxracers.track.RoomBox
import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.game.RacePhase
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxRotationAxis
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxVolumeKind
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorld
import com.Atom2Universe.app.games.toyboxracers.editor.ToyboxWorldStore
import com.Atom2Universe.app.games.toyboxracers.menu.ToyboxWorldMenu
import com.Atom2Universe.app.games.toyboxracers.menu.ToyboxWorldMenuHost
import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorModel
import com.Atom2Universe.app.games.toyboxracers.models.DecorRoom
import com.Atom2Universe.app.games.toyboxracers.models.DecorShape
import com.Atom2Universe.app.util.enableImmersiveMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exploration libre et courses facultatives, accessibles depuis le hub des jeux.
 */
class ToyboxRacersActivity : ThemedActivity() {
    private data class EditorUndoState(
        val world: ToyboxWorld,
        val selectedVolumeId: Long?,
        val selectedTrackId: Long?,
        val selectedDecorId: Long?,
        val selectedGroupIds: Set<Long>,
        val draftActive: Boolean,
        val draftTrackActive: Boolean,
        val draftDecorModelId: String?,
        val kind: ToyboxVolumeKind,
        val width: Float,
        val height: Float,
        val depth: Float,
        val quarterTurns: Int,
        val yawDegrees: Float,
        val pitchDegrees: Float,
        val rollDegrees: Float,
        val floorY: Float,
        val solid: Boolean,
        val color: Int,
        val decorQuarterTurns: Int,
        val decorYawDegrees: Float,
        val decorScale: Float,
        val groupMode: EditorGroupMode,
        val rotationAxis: ToyboxRotationAxis,
        val rotationStepIndex: Int,
        val gridIndex: Int
    ) {
        fun toJson() = JSONObject()
            .put("world", world.toJson())
            .put("selectedVolumeId", selectedVolumeId)
            .put("selectedTrackId", selectedTrackId)
            .put("selectedDecorId", selectedDecorId)
            .put("selectedGroupIds", JSONArray().apply { selectedGroupIds.forEach { put(it) } })
            .put("draftActive", draftActive)
            .put("draftTrackActive", draftTrackActive)
            .put("draftDecorModelId", draftDecorModelId)
            .put("kind", kind.name)
            .put("width", width.toDouble())
            .put("height", height.toDouble())
            .put("depth", depth.toDouble())
            .put("quarterTurns", quarterTurns)
            .put("yawDegrees", yawDegrees.toDouble())
            .put("pitchDegrees", pitchDegrees.toDouble())
            .put("rollDegrees", rollDegrees.toDouble())
            .put("floorY", floorY.toDouble())
            .put("solid", solid)
            .put("color", color)
            .put("decorQuarterTurns", decorQuarterTurns)
            .put("decorYawDegrees", decorYawDegrees.toDouble())
            .put("decorScale", decorScale.toDouble())
            .put("groupMode", groupMode.name)
            .put("rotationAxis", rotationAxis.name)
            .put("rotationStepIndex", rotationStepIndex)
            .put("gridIndex", gridIndex)

        companion object {
            fun fromJson(json: JSONObject): EditorUndoState {
                val selectedGroupJson = json.optJSONArray("selectedGroupIds") ?: JSONArray()
                return EditorUndoState(
                    world = ToyboxWorld.fromJson(json.getJSONObject("world")),
                    selectedVolumeId = if (json.isNull("selectedVolumeId")) null else json.optLong("selectedVolumeId"),
                    selectedTrackId = if (json.isNull("selectedTrackId")) null else json.optLong("selectedTrackId"),
                    selectedDecorId = if (json.isNull("selectedDecorId")) null else json.optLong("selectedDecorId"),
                    selectedGroupIds = List(selectedGroupJson.length()) { selectedGroupJson.optLong(it) }.toSet(),
                    draftActive = json.optBoolean("draftActive", false),
                    draftTrackActive = json.optBoolean("draftTrackActive", false),
                    draftDecorModelId = json.optString("draftDecorModelId").takeIf { !json.isNull("draftDecorModelId") && it.isNotBlank() },
                    kind = ToyboxVolumeKind.entries.find { it.name == json.optString("kind") } ?: ToyboxVolumeKind.FLOOR,
                    width = json.optDouble("width", 20.0).toFloat(),
                    height = json.optDouble("height", 0.6).toFloat(),
                    depth = json.optDouble("depth", 20.0).toFloat(),
                    quarterTurns = json.optInt("quarterTurns", 0),
                    yawDegrees = json.optDouble("yawDegrees", 0.0).toFloat(),
                    pitchDegrees = json.optDouble("pitchDegrees", 0.0).toFloat(),
                    rollDegrees = json.optDouble("rollDegrees", 0.0).toFloat(),
                    floorY = json.optDouble("floorY", 0.0).toFloat(),
                    solid = json.optBoolean("solid", true),
                    color = json.optInt("color", ToyboxVolumeKind.FLOOR.color),
                    decorQuarterTurns = json.optInt("decorQuarterTurns", 0),
                    decorYawDegrees = json.optDouble("decorYawDegrees", 0.0).toFloat(),
                    decorScale = json.optDouble("decorScale", 1.0).toFloat(),
                    groupMode = EditorGroupMode.entries.find { it.name == json.optString("groupMode") } ?: EditorGroupMode.OFF,
                    rotationAxis = ToyboxRotationAxis.entries.find { it.name == json.optString("rotationAxis") } ?: ToyboxRotationAxis.YAW,
                    rotationStepIndex = json.optInt("rotationStepIndex", 1),
                    gridIndex = json.optInt("gridIndex", 0)
                )
            }
        }
    }

    private lateinit var glView: ToyboxRacersGLView
    private lateinit var renderer: ToyboxRacersRenderer
    private lateinit var hud: TextView
    private lateinit var status: TextView
    private lateinit var countdown: TextView
    private lateinit var resultPanel: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var difficultyButton: Button
    private lateinit var modeButton: Button
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
    private lateinit var editorDuplicateButton: Button
    private lateinit var editorRotateLeftButton: Button
    private lateinit var editorRotateRightButton: Button
    private lateinit var editorRotationStepButton: Button
    private lateinit var editorRotationAxisButton: Button
    private lateinit var editorUndoButton: Button
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
    private lateinit var worldMenu: ToyboxWorldMenu
    private var editorWorld = ToyboxWorld()
    private var currentCreationFile: File? = null
    private var currentWorldKind = ActiveWorldKind.CUSTOM
    private var currentMode = PlayMode.EXPLORATION
    private var editorActive = false
    private var trackEndpoint = -1
    private var trackDragUndo: EditorUndoState? = null
    private var trackDragOrigin: Pair<Float, Float>? = null
    private var trackDragSection: ToyboxTrackSection? = null
    private var trackTapFraction = 0.5f
    private var editorKind = ToyboxVolumeKind.FLOOR
    private var editorWidth = 20f
    private var editorHeight = 0.6f
    private var editorDepth = 20f
    private var editorQuarterTurns = 0
    private var editorYawDegrees = 0f
    private var editorPitchDegrees = 0f
    private var editorRollDegrees = 0f
    private var editorFloorY = 0f
    private var editorSolid = true
    private var editorColor = editorKind.color
    private var selectedVolumeId: Long? = null
    private var selectedTrackId: Long? = null
    private var selectedDecorId: Long? = null
    private var selectedGroupIds: Set<Long> = emptySet()
    private var editorDraftDecorModelId: String? = null
    private var editorDecorQuarterTurns = 0
    private var editorDecorYawDegrees = 0f
    private var editorDecorScale = 1f
    private var editorGroupMode = EditorGroupMode.OFF
    private var editorRotationAxis = ToyboxRotationAxis.YAW
    private var editorRotationStepIndex = 1
    private var editorDraftActive = false
    private var editorDraftTrackActive = false
    private var editorGridIndex = 0
    private var editorForward = 0f
    private var editorStrafe = 0f
    private var editorPitch = 0f
    private var editorYaw = 0f
    private var syncingEditorPickers = false
    private var paused = false
    private val releaseControls = mutableListOf<() -> Unit>()
    private val editorUndoStack = ArrayDeque<EditorUndoState>()
    private var currentDifficulty = RaceDifficulty.ARCADE
    private var lastTurboLevel = 0
    private var lastTurboReleaseSerial = 0
    private var lastFinishSerial = 0
    private var suppressEditorUndo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        worldStore = ToyboxWorldStore(this)
        worldMenu = ToyboxWorldMenu(this, worldMenuHost)
        editorWorld = worldStore.load()
        currentCreationFile = prefs.getString(KEY_CURRENT_CREATION_FILE, null)?.let { worldStore.creationFileNamed(it) }
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
        renderer.setActiveWorldKind(currentWorldKind)
        loadEditorUndoHistory()
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
        updateRaceControlsVisibility()
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showPause()
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
        worldMenu.dismiss()
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
        worldMenu.dismiss()
        enableImmersiveMode()
    }

    private fun showPause() {
        if (isFinishing || isDestroyed || worldMenu.isShowing()) return
        pauseGame()
        worldMenu.showPause()
    }

    /** Bascule d'un monde à l'autre : toujours une copie en mémoire, jamais
     * une écriture du fichier source (créations comme mondes intégrés). */
    private fun loadCustomWorld(world: ToyboxWorld, sourceFile: File?) {
        editorWorld = world
        editorUndoStack.clear()
        currentCreationFile = sourceFile
        loadEditorUndoHistory()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_CURRENT_CREATION_FILE, sourceFile?.name)
            .apply()
        selectedVolumeId = null
        selectedTrackId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftTrackActive = false
        editorDraftDecorModelId = null
        editorQuarterTurns = 0
        editorYawDegrees = 0f
        editorPitchDegrees = 0f
        editorRollDegrees = 0f
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
        currentWorldKind = ActiveWorldKind.CUSTOM
        renderer.setActiveWorldKind(currentWorldKind)
        currentMode = PlayMode.EXPLORATION
        renderer.setMode(currentMode)
        renderer.requestReset()
        pushEditorPreview()
        updateRaceControlsVisibility()
        resumeGame()
    }

    private fun loadLegacyScene(scene: SceneChoice) {
        currentWorldKind = ActiveWorldKind.LEGACY
        renderer.setActiveWorldKind(currentWorldKind)
        if (editorActive) toggleEditorMode()
        changeScene(scene)
        updateRaceControlsVisibility()
        resumeGame()
    }

    private fun saveCurrentCreation() {
        val file = currentCreationFile
        if (file != null && worldStore.overwriteCreation(editorWorld, file)) {
            Toast.makeText(this, getString(R.string.toybox_menu_creation_saved, file.nameWithoutExtension), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCurrentCreationAsNew(name: String) {
        editorWorld = editorWorld.copy(name = name)
        val file = worldStore.saveCreation(editorWorld, name)
        currentCreationFile = file
        saveEditorUndoHistory()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_CURRENT_CREATION_FILE, file.name)
            .apply()
    }

    /** Traduit l'état de l'activité vers ce dont [ToyboxWorldMenu] a besoin,
     * sans lui exposer les champs privés directement. */
    private val worldMenuHost = object : ToyboxWorldMenuHost {
        override val worldStore get() = this@ToyboxRacersActivity.worldStore
        override fun dialogBuilder() = this@ToyboxRacersActivity.dialogBuilder()
        override fun activeWorldKind() = currentWorldKind
        override fun isEditorActive() = editorActive
        override fun currentScene() = this@ToyboxRacersActivity.currentScene
        override fun housePlan() = this@ToyboxRacersActivity.housePlan
        override fun currentCreationFile() = currentCreationFile
        override fun currentEditorWorldName() = editorWorld.name
        override fun roomLabel(kind: RoomKind) = this@ToyboxRacersActivity.roomLabel(kind)
        override fun circuitLabel(kind: CircuitKind) = this@ToyboxRacersActivity.circuitLabel(kind)
        override fun sceneForRoom(kind: RoomKind) = this@ToyboxRacersActivity.sceneForRoom(kind)
        override fun resumeGame() = this@ToyboxRacersActivity.resumeGame()
        override fun restartRace() {
            resultPanel.visibility = View.GONE
            renderer.requestReset()
            resumeGame()
        }
        override fun quitGame() = finish()
        override fun setEditing(editing: Boolean) {
            if (editing != editorActive) toggleEditorMode()
            resumeGame()
        }
        override fun loadCustomWorld(world: ToyboxWorld, sourceFile: File?) =
            this@ToyboxRacersActivity.loadCustomWorld(world, sourceFile)
        override fun loadLegacyScene(scene: SceneChoice) = this@ToyboxRacersActivity.loadLegacyScene(scene)
        override fun editLegacySceneAsNewCreation() = this@ToyboxRacersActivity.editLegacySceneAsNewCreation()
        override fun setHousePlan(plan: HousePlan) {
            housePlan = plan
            val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putLong(KEY_HOUSE_SEED, plan.seed)
            RoomKind.entries.forEach { editor.remove(KEY_ROOM_CIRCUIT + it.name) }
            editor.apply()
            loadLegacyScene(housePlan.room(currentScene.room).scene)
        }
        override fun saveCurrentCreation() = this@ToyboxRacersActivity.saveCurrentCreation()
        override fun saveCurrentCreationAsNew(name: String) = this@ToyboxRacersActivity.saveCurrentCreationAsNew(name)
    }

    private fun editLegacySceneAsNewCreation() {
        val name = legacyCopyName(currentScene)
        editorWorld = legacySceneToWorld(currentScene, name)
        editorUndoStack.clear()
        currentCreationFile = worldStore.saveCreation(editorWorld, name)
        saveEditorUndoHistory()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_CURRENT_CREATION_FILE, currentCreationFile?.name)
            .apply()
        selectedVolumeId = null
        selectedTrackId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftTrackActive = false
        editorDraftDecorModelId = null
        editorQuarterTurns = 0
        editorYawDegrees = 0f
        editorPitchDegrees = 0f
        editorRollDegrees = 0f
        currentWorldKind = ActiveWorldKind.CUSTOM
        renderer.setActiveWorldKind(currentWorldKind)
        currentMode = PlayMode.EXPLORATION
        renderer.setMode(currentMode)
        renderer.setEditorWorld(editorWorld)
        renderer.requestReset()
        if (!editorActive) toggleEditorMode()
        pushEditorPreview()
        updateRaceControlsVisibility()
        resumeGame()
        Toast.makeText(this, "Copie creee : $name", Toast.LENGTH_SHORT).show()
    }

    private fun legacyCopyName(scene: SceneChoice): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date())
        return "${circuitLabel(scene.circuit)} - $stamp"
    }

    private fun legacySceneToWorld(scene: SceneChoice, name: String): ToyboxWorld {
        var nextId = 100_000L
        fun RoomBox.toVisualVolume(kind: ToyboxVolumeKind, solid: Boolean = false) = ToyboxVolume(
            id = nextId++,
            kind = kind,
            x = x,
            y = y,
            z = z,
            width = width,
            height = height,
            depth = depth,
            solid = if (kind == ToyboxVolumeKind.FLOOR) true else solid,
            color = 0xFF000000.toInt() or (color and 0x00FFFFFF)
        )
        val floor = ToyboxVolume(nextId++, ToyboxVolumeKind.FLOOR, 0f, -0.4f, 0f, 220f, 0.8f, 150f)
        val houseFloors = if (scene.circuit.usesHouseLayout) {
            HouseGeometry.floorBoxes().map { it.toVisualVolume(ToyboxVolumeKind.FLOOR, solid = true) }
        } else {
            emptyList()
        }
        val solidBoxes = RaceLayouts.solids(scene).toHashSet()
        val visualVolumes = RaceLayouts.boxes(scene).map { box ->
            val kind = when {
                box.height <= 1.2f -> ToyboxVolumeKind.FLOOR
                box.width <= 1.2f || box.depth <= 1.2f -> ToyboxVolumeKind.WALL
                else -> ToyboxVolumeKind.FURNITURE
            }
            box.toVisualVolume(kind, solid = box in solidBoxes || kind == ToyboxVolumeKind.FLOOR)
        }
        val decorations = RaceLayouts.decorations(scene).mapIndexed { index, placement ->
            ToyboxDecor(
                id = 200_000L + index,
                modelId = placement.model.id,
                x = placement.x,
                y = placement.y,
                z = placement.z,
                quarterTurns = placement.quarterTurns,
                scale = placement.scale,
                yawDegrees = placement.yawDegrees
            )
        }
        return ToyboxWorld(
            name = name,
            volumes = listOf(floor) + houseFloors + visualVolumes,
            trackSections = legacySceneTrackSections(scene),
            decorations = decorations
        )
    }

    private fun legacySceneTrackSections(scene: SceneChoice): List<ToyboxTrackSection> {
        val track = PrototypeTrack(scene = scene)
        val samples = track.allSamples()
        if (samples.size < 2) return emptyList()
        val step = 1
        val sections = ArrayList<ToyboxTrackSection>()
        var id = 300_000L
        var index = 0
        while (index < samples.size) {
            val nextIndex = (index + step) % samples.size
            val a = samples[index]
            val b = samples[nextIndex]
            val dx = b.position.x - a.position.x
            val dz = b.position.z - a.position.z
            val length = kotlin.math.hypot(dx, dz).coerceAtLeast(0.25f)
            val bDistance = if (nextIndex <= index) b.distance + track.length else b.distance
            val midpointDistance = (a.distance + bDistance) * 0.5f
            if (track.hasDeck(track.sampleAt(midpointDistance))) {
                val yaw = kotlin.math.atan2(dx, dz) * 180f / kotlin.math.PI.toFloat()
                sections += ToyboxTrackSection(
                    id = id++,
                    x = (a.position.x + b.position.x) * 0.5f,
                    y = a.position.y,
                    z = (a.position.z + b.position.z) * 0.5f,
                    yawDegrees = ((yaw % 360f) + 360f) % 360f,
                    length = length,
                    width = a.roadWidth,
                    endWidth = b.roadWidth,
                    startYawOffset = kotlin.math.atan2(-a.right.z, a.right.x) * 180f / kotlin.math.PI.toFloat() - yaw,
                    endYawOffset = kotlin.math.atan2(-b.right.z, b.right.x) * 180f / kotlin.math.PI.toFloat() - yaw,
                    endY = b.position.y,
                    color = 0xFF6F7B91.toInt()
                )
            }
            index += step
        }
        return sections
    }

    private fun updateRaceControlsVisibility() {
        val legacy = currentWorldKind == ActiveWorldKind.LEGACY
        difficultyButton.visibility = if (legacy) View.VISIBLE else View.GONE
        modeButton.visibility = if (legacy) View.VISIBLE else View.GONE
        minimap.visibility = if (legacy && !editorActive) View.VISIBLE else View.GONE
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

    private fun dialogBuilder() = AlertDialog.Builder(this, R.style.Theme_Toybox_Dialog)

    private fun changeScene(scene: SceneChoice) {
        releaseControls.forEach { it() }
        currentScene = scene
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
            handles = { trackHandles() }
            onTap = { x, y ->
                renderer.pickTrackSection(x, y, editorWorld.trackSections)?.let { id ->
                    selectEditorTrack(id)
                    selectedTrack()?.let { section ->
                        renderer.editorPointOnPlane(x, y, (section.y + section.endY) * 0.5f)?.let {
                            trackTapFraction = ((section.localAlong(it.x, it.z) + section.halfLength) / section.length).coerceIn(0.1f, 0.9f)
                        }
                    }
                    val points = trackHandles()
                    trackEndpoint = points.indices.minByOrNull { i ->
                        kotlin.math.hypot(points[i].first - x, points[i].second - y)
                    } ?: -1
                    selectedHandle = trackEndpoint
                    pushEditorPreview()
                }
            }
            onHandleDown = { x, y ->
                val points = trackHandles()
                val index = points.indices.minByOrNull { i -> kotlin.math.hypot(points[i].first - x, points[i].second - y) }
                val hit = index != null && kotlin.math.hypot(points[index].first - x, points[index].second - y) < dp(28)
                if (hit) {
                    trackEndpoint = index!!
                    selectedHandle = index
                    trackDragOrigin = null
                    trackDragUndo = captureEditorUndoState()
                    trackDragSection = selectedTrack()
                    val section = trackDragSection!!
                    renderer.editorPointOnPlane(x, y, if (index == 0) section.y else section.endY)?.let {
                        trackDragOrigin = it.x to it.z
                    }
                }
                hit
            }
            onHandleMove = { x, y -> dragTrackEndpoint(x, y) }
            onHandleEnd = { cancelled -> finishTrackDrag(cancelled) }
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
                renderer.pickVolume(x, y, editorWorld.volumes)?.let {
                    when (editorGroupMode) {
                        EditorGroupMode.OFF -> selectEditorVolume(it)
                        EditorGroupMode.SAME_KIND -> selectConnectedGroup(it, sameKindOnly = true)
                        EditorGroupMode.CONNECTED -> selectConnectedGroup(it, sameKindOnly = false)
                    }
                } ?: renderer.pickTrackSection(x, y, editorWorld.trackSections)?.let {
                    selectEditorTrack(it)
                } ?: renderer.pickDecor(x, y, editorWorld.decorations)?.let {
                    selectEditorDecor(it)
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
        editorUndoButton = makeEditorButton("Annuler", 0xAA4B617A.toInt()).apply {
            setOnClickListener { undoEditorAction() }
        }
        val toolButtons = listOf(editorKindButton, editorGridButton, editorGroupButton, editorUndoButton)
        toolButtons.forEachIndexed { index, view ->
            toolsContent.addView(view, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                if (index < toolButtons.lastIndex) rightMargin = dp(6)
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
                if (selectedVolume()?.kind == ToyboxVolumeKind.FLOOR) {
                    editorSolid = true
                    pushEditorPreview()
                    return@setOnClickListener
                }
                val undoState = captureEditorUndoState()
                editorSolid = !editorSolid
                updateSelectedVolume(undoState) { it.copy(solid = editorSolid) }
                pushEditorPreview()
            }
        }
        editorColorButton = makeEditorButton("", 0xAA4B617A.toInt()).apply {
            setOnClickListener { showEditorColorPicker() }
        }
        row(editorPanel, editorSolidButton, editorColorButton)
        row(editorPanel,
            action(getString(R.string.toybox_track_extend)) { extendSelectedTrack() },
            action(getString(R.string.toybox_track_point)) {
                if (selectedTrack() != null) {
                    trackEndpoint = (trackEndpoint + 1) % 2
                    editorTouchLayer.selectedHandle = trackEndpoint
                    pushEditorPreview()
                }
            },
            action(getString(R.string.toybox_track_split)) { splitSelectedTrack() })
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
        editorDuplicateButton = makeEditorButton("Dupliquer", 0xAA4B617A.toInt()).apply {
            setOnClickListener { duplicateEditorSelection() }
        }
        row(
            editorPanel,
            editorPlaceButton,
            editorDuplicateButton,
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
        editorRotateLeftButton = action("") { rotateEditorObject(-rotationStep()) }
        editorRotateRightButton = action("") { rotateEditorObject(rotationStep()) }
        editorRotationAxisButton = action("") {
            editorRotationAxis = editorRotationAxis.next()
            pushEditorPreview()
        }
        editorRotationStepButton = action("") {
            editorRotationStepIndex = (editorRotationStepIndex + 1) % EDITOR_ROTATION_STEPS.size
            pushEditorPreview()
        }
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
        row(editorPositionPanel, editorRotateLeftButton, editorRotationAxisButton, editorRotateRightButton)
        row(editorPositionPanel, editorRotationStepButton, bottomDp = 0)
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
        renderer.setEditorActive(editorActive)
        updateRaceControlsVisibility()
        pushEditorInput()
        pushEditorPreview()
        pauseButton.bringToFront()
        if (editorActive) {
            status.text = "Mode construction : déplace la caméra, règle le bloc, puis POSER"
        }
    }

    private fun applyEditorItemPreset(item: EditorItemPreset) {
        selectedVolumeId = null
        selectedTrackId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = true
        editorDraftTrackActive = false
        editorDraftDecorModelId = null
        editorKind = item.kind
        editorWidth = item.width
        editorHeight = item.height
        editorDepth = item.depth
        editorQuarterTurns = 0
        editorYawDegrees = 0f
        editorPitchDegrees = item.pitchDegrees
        editorRollDegrees = item.rollDegrees
        editorFloorY = maxOf(editorFloorY, item.minimumFloorY)
        editorSolid = item.kind.solidByDefault
        editorColor = item.kind.color
        renderer.resetEditorPreviewAnchor()
        pushEditorPreview()
    }

    private fun applyEditorDecorModel(model: DecorModel) {
        selectedVolumeId = null
        selectedTrackId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftTrackActive = false
        editorDraftDecorModelId = model.id
        editorQuarterTurns = 0
        editorYawDegrees = 0f
        editorPitchDegrees = 0f
        editorRollDegrees = 0f
        editorDecorQuarterTurns = 0
        editorDecorYawDegrees = 0f
        editorDecorScale = 1f
        editorFloorY = maxOf(editorFloorY, 0f)
        renderer.resetEditorPreviewAnchor()
        pushEditorPreview()
    }

    private fun applyEditorTrackSectionPreset() {
        selectedVolumeId = null
        selectedTrackId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftTrackActive = true
        editorDraftDecorModelId = null
        editorWidth = 9f
        editorHeight = 0f
        editorDepth = 28f
        editorYawDegrees = 0f
        editorPitchDegrees = 0f
        editorRollDegrees = 0f
        editorFloorY = maxOf(editorFloorY, 0f)
        editorSolid = true
        editorColor = 0xFF6F7B91.toInt()
        renderer.resetEditorPreviewAnchor()
        pushEditorPreview()
    }

    private fun resizeEditor(widthDelta: Float, heightDelta: Float, depthDelta: Float) {
        if (selectedVolumeId == null && !editorDraftActive) return
        val undoState = captureEditorUndoState()
        editorWidth = (editorWidth + widthDelta).coerceAtLeast(gridSize())
        editorHeight = (editorHeight + heightDelta).coerceAtLeast(gridSize())
        editorDepth = (editorDepth + depthDelta).coerceAtLeast(gridSize())
        updateSelectedVolume(undoState) { it.copy(width = editorWidth, height = editorHeight, depth = editorDepth) }
        pushEditorPreview()
    }

    private fun setEditorDimension(width: Float? = null, height: Float? = null, depth: Float? = null) {
        if (selectedTrackId != null || editorDraftTrackActive) {
            val undoState = captureEditorUndoState()
            editorWidth = (width ?: editorWidth).coerceAtLeast(0.05f)
            editorHeight = height ?: editorHeight
            editorDepth = (depth ?: editorDepth).coerceAtLeast(0.05f)
            updateSelectedTrack(undoState) {
                it.resize(width = editorWidth, length = editorDepth).withEndY(editorFloorY + editorHeight)
            }
            pushEditorPreview()
            return
        }
        if (selectedVolumeId == null && !editorDraftActive) return
        val undoState = captureEditorUndoState()
        editorWidth = (width ?: editorWidth).coerceAtLeast(gridSize())
        editorHeight = (height ?: editorHeight).coerceAtLeast(gridSize())
        editorDepth = (depth ?: editorDepth).coerceAtLeast(gridSize())
        updateSelectedVolume(undoState) { it.copy(width = editorWidth, height = editorHeight, depth = editorDepth) }
        pushEditorPreview()
    }

    private fun showAddItemFamilies() {
        val families = EDITOR_ITEM_FAMILIES
        val labels = families.map { it.name } + listOf("Piste", "Modeles 3D")
        dialogBuilder()
            .setTitle("Ajouter un objet")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < families.size -> showAddItems(families[which])
                    which == families.size -> applyEditorTrackSectionPreset()
                    else -> showDecorModelFamilies()
                }
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
        selectedTrack()?.let { section ->
            if (trackEndpoint >= 0) {
                val state = captureEditorUndoState()
                moveTrackEndpoint(section, trackEndpoint == 1,
                    if (trackEndpoint == 0) section.startX else section.finishX,
                    (if (trackEndpoint == 0) section.y else section.endY) + delta,
                    if (trackEndpoint == 0) section.startZ else section.finishZ)
                rememberEditorUndo(state)
                worldStore.save(editorWorld)
                syncTrackFields()
                return
            }
        }
        if (selectedTrackId != null || editorDraftTrackActive) {
            val undoState = captureEditorUndoState()
            editorFloorY += delta
            updateSelectedTrack(undoState) { section ->
                section.withStartY(editorFloorY).withEndY(editorFloorY + editorHeight)
            }
            pushEditorPreview()
            return
        }
        if (selectedDecorId != null || editorDraftDecorModelId != null) {
            val undoState = captureEditorUndoState()
            editorFloorY += delta
            updateSelectedDecor(undoState) { it.copy(y = editorFloorY) }
            pushEditorPreview()
            return
        }
        if (selectedVolumeId == null && !editorDraftActive) return
        val undoState = captureEditorUndoState()
        editorFloorY += delta
        updateSelectedVolume(undoState) { volume ->
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
        if (selectedTrackId != null) {
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
            rememberEditorUndo()
            val anchor = renderer.makePreviewVolume(System.nanoTime())
            val decor = ToyboxDecor(
                id = System.nanoTime(),
                modelId = modelId,
                x = anchor.x,
                y = editorFloorY,
                z = anchor.z,
                quarterTurns = editorDecorQuarterTurns,
                scale = editorDecorScale,
                yawDegrees = editorDecorYawDegrees
            )
            editorWorld = editorWorld.copy(decorations = editorWorld.decorations + decor)
            editorDraftDecorModelId = null
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }
        if (editorDraftTrackActive) {
            rememberEditorUndo()
            val anchor = renderer.makePreviewVolume(System.nanoTime())
            val raw = ToyboxTrackSection(
                id = System.nanoTime(),
                x = anchor.x,
                y = editorFloorY,
                z = anchor.z,
                yawDegrees = editorYawDegrees,
                length = editorDepth,
                width = editorWidth,
                endY = editorFloorY + editorHeight,
                bankDegrees = editorRollDegrees,
                color = editorColor
            )
            val section = raw.snappedTo(editorWorld.trackSections, TRACK_SNAP_DISTANCE)
            editorWorld = editorWorld.copy(trackSections = editorWorld.trackSections + section)
            editorDraftTrackActive = false
            selectedTrackId = section.id
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }
        if (!editorDraftActive) return
        rememberEditorUndo()
        val volume = renderer.makePreviewVolume(System.nanoTime())
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes + volume)
        editorDraftActive = false
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
        pushEditorPreview()
    }

    private fun deleteEditorVolume() {
        if (selectedGroupIds.isNotEmpty()) {
            rememberEditorUndo()
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
            rememberEditorUndo()
            editorWorld = editorWorld.copy(decorations = editorWorld.decorations.filterNot { it.id == selectedDecor })
            selectedDecorId = null
            editorDraftDecorModelId = null
            editorDraftActive = false
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }
        val selectedTrack = selectedTrackId
        if (selectedTrack != null) {
            rememberEditorUndo()
            editorWorld = editorWorld.copy(trackSections = editorWorld.trackSections.filterNot { it.id == selectedTrack })
            selectedTrackId = null
            editorDraftTrackActive = false
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }
        val selected = selectedVolumeId
        if (selected == null) return
        rememberEditorUndo()
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes.filterNot { it.id == selected })
        selectedVolumeId = null
        editorDraftActive = false
        editorDraftDecorModelId = null
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
        pushEditorPreview()
    }

    private fun duplicateEditorSelection() {
        val offset = gridSize()
        if (selectedGroupIds.isNotEmpty()) {
            val duplicated = editorWorld.volumes
                .filter { it.id in selectedGroupIds }
                .mapIndexed { index, volume ->
                    volume.copy(
                        id = System.nanoTime() + index,
                        x = snapEditor(volume.x + offset),
                        z = snapEditor(volume.z + offset)
                    )
            }
            if (duplicated.isEmpty()) return
            rememberEditorUndo()
            editorWorld = editorWorld.copy(volumes = editorWorld.volumes + duplicated)
            selectedGroupIds = duplicated.map { it.id }.toSet()
            selectedVolumeId = null
            selectedDecorId = null
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }

        selectedVolume()?.let { volume ->
            val copy = volume.copy(
                id = System.nanoTime(),
                x = snapEditor(volume.x + offset),
                z = snapEditor(volume.z + offset)
            )
            rememberEditorUndo()
            editorWorld = editorWorld.copy(volumes = editorWorld.volumes + copy)
            selectEditorVolume(copy.id)
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }

        selectedDecor()?.let { decor ->
            val copy = decor.copy(
                id = System.nanoTime(),
                x = snapEditor(decor.x + offset),
                z = snapEditor(decor.z + offset)
            )
            rememberEditorUndo()
            editorWorld = editorWorld.copy(decorations = editorWorld.decorations + copy)
            selectEditorDecor(copy.id)
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
            return
        }

        selectedTrack()?.let { section ->
            val copy = section.copy(
                id = System.nanoTime(),
                x = snapEditor(section.x + offset),
                z = snapEditor(section.z + offset)
            )
            rememberEditorUndo()
            editorWorld = editorWorld.copy(trackSections = editorWorld.trackSections + copy)
            selectEditorTrack(copy.id)
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            pushEditorPreview()
        }
    }

    private fun rememberEditorUndo(state: EditorUndoState = captureEditorUndoState()) {
        if (suppressEditorUndo || editorUndoStack.lastOrNull() == state) return
        if (editorUndoStack.size >= EDITOR_UNDO_LIMIT) editorUndoStack.removeFirst()
        editorUndoStack.addLast(state)
        saveEditorUndoHistory()
        updateEditorUndoButton()
    }

    private fun captureEditorUndoState() = EditorUndoState(
        world = editorWorld,
        selectedVolumeId = selectedVolumeId,
        selectedTrackId = selectedTrackId,
        selectedDecorId = selectedDecorId,
        selectedGroupIds = selectedGroupIds.toSet(),
        draftActive = editorDraftActive,
        draftTrackActive = editorDraftTrackActive,
        draftDecorModelId = editorDraftDecorModelId,
        kind = editorKind,
        width = editorWidth,
        height = editorHeight,
        depth = editorDepth,
        quarterTurns = editorQuarterTurns,
        yawDegrees = editorYawDegrees,
        pitchDegrees = editorPitchDegrees,
        rollDegrees = editorRollDegrees,
        floorY = editorFloorY,
        solid = editorSolid,
        color = editorColor,
        decorQuarterTurns = editorDecorQuarterTurns,
        decorYawDegrees = editorDecorYawDegrees,
        decorScale = editorDecorScale,
        groupMode = editorGroupMode,
        rotationAxis = editorRotationAxis,
        rotationStepIndex = editorRotationStepIndex,
        gridIndex = editorGridIndex
    )

    private fun undoEditorAction() {
        val state = editorUndoStack.removeLastOrNull() ?: return
        suppressEditorUndo = true
        try {
            restoreEditorUndoState(state)
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            saveEditorUndoHistory()
            updateEditorUndoButton()
            pushEditorPreview()
            Toast.makeText(this, "Action annulee", Toast.LENGTH_SHORT).show()
        } finally {
            suppressEditorUndo = false
        }
    }

    private fun restoreEditorUndoState(state: EditorUndoState) {
        editorWorld = state.world
        selectedVolumeId = state.selectedVolumeId
        selectedTrackId = state.selectedTrackId
        selectedDecorId = state.selectedDecorId
        selectedGroupIds = state.selectedGroupIds
        editorDraftActive = state.draftActive
        editorDraftTrackActive = state.draftTrackActive
        editorDraftDecorModelId = state.draftDecorModelId
        editorKind = state.kind
        editorWidth = state.width
        editorHeight = state.height
        editorDepth = state.depth
        editorQuarterTurns = state.quarterTurns
        editorYawDegrees = state.yawDegrees
        editorPitchDegrees = state.pitchDegrees
        editorRollDegrees = state.rollDegrees
        editorFloorY = state.floorY
        editorSolid = state.solid
        editorColor = state.color
        editorDecorQuarterTurns = state.decorQuarterTurns
        editorDecorYawDegrees = state.decorYawDegrees
        editorDecorScale = state.decorScale
        editorGroupMode = state.groupMode
        editorRotationAxis = state.rotationAxis
        editorRotationStepIndex = state.rotationStepIndex.coerceIn(0, EDITOR_ROTATION_STEPS.lastIndex)
        editorGridIndex = state.gridIndex.coerceIn(0, EDITOR_GRIDS.lastIndex)
    }

    private fun updateEditorUndoButton() {
        if (!::editorUndoButton.isInitialized) return
        val canUndo = editorUndoStack.isNotEmpty()
        editorUndoButton.isEnabled = canUndo
        editorUndoButton.alpha = if (canUndo) 0.82f else 0.38f
    }

    private fun saveEditorUndoHistory() {
        val json = JSONArray().apply { editorUndoStack.forEach { put(it.toJson()) } }
        worldStore.saveUndoHistory(currentCreationFile, json)
    }

    private fun loadEditorUndoHistory() {
        editorUndoStack.clear()
        val json = worldStore.loadUndoHistory(currentCreationFile) ?: run {
            updateEditorUndoButton()
            return
        }
        val start = (json.length() - EDITOR_UNDO_LIMIT).coerceAtLeast(0)
        for (index in start until json.length()) {
            runCatching { EditorUndoState.fromJson(json.getJSONObject(index)) }
                .getOrNull()
                ?.let { editorUndoStack.addLast(it) }
        }
        updateEditorUndoButton()
    }

    private fun pushEditorInput() {
        renderer.setEditorInput(editorStrafe, editorForward, editorYaw, editorPitch)
    }

    private fun pushEditorPreview() {
        if (!::editorKindButton.isInitialized) return
        val selected = selectedVolume()
        val selectedTrack = selectedTrack()
        val selectedDecor = selectedDecor()
        val draftDecorModel = editorDraftDecorModelId?.let { runCatching { DecorCatalog[it] }.getOrNull() }
        val hasTrackDraft = selected == null && selectedTrack == null && selectedDecor == null && editorDraftTrackActive
        val hasDraft = selected == null && selectedTrack == null && selectedDecor == null && editorDraftActive
        val hasDecorDraft = selected == null && selectedTrack == null && selectedDecor == null && draftDecorModel != null
        val hasGroup = selectedGroupIds.isNotEmpty()
        val canEditBlock = selected != null || hasDraft
        val canEditTrack = selectedTrack != null || hasTrackDraft
        val canEditDecor = selectedDecor != null || hasDecorDraft
        editorPanel.visibility = if (editorActive && (canEditBlock || canEditTrack || canEditDecor) && !hasGroup) View.VISIBLE else View.GONE
        editorPositionPanel.visibility = if (editorActive && (canEditBlock || canEditTrack || canEditDecor || hasGroup)) View.VISIBLE else View.GONE
        editorToolsPanel.visibility = if (editorActive) View.VISIBLE else View.GONE
        editorCameraPanel.visibility = if (editorActive) View.VISIBLE else View.GONE
        editorKindButton.text = "+"
        editorGridButton.text = "Grille ${gridLabel()}"
        updateEditorUndoButton()
        editorGroupButton.text = when {
            hasGroup -> "Groupe ${selectedGroupIds.size}"
            editorGroupMode == EditorGroupMode.SAME_KIND -> "Groupe type"
            editorGroupMode == EditorGroupMode.CONNECTED -> "Prefab"
            else -> "Groupe"
        }
        if (::editorRotationAxisButton.isInitialized) {
            editorRotationAxisButton.text = editorRotationAxis.label
            editorRotationAxisButton.isEnabled = !canEditDecor
            editorRotationAxisButton.alpha = if (canEditDecor) 0.38f else 0.82f
        }
        if (::editorRotationStepButton.isInitialized) {
            editorRotationStepButton.text = "Rotation ${formatEditorNumber(rotationStep())} deg"
            editorRotateLeftButton.text = "↺ ${formatEditorNumber(rotationStep())}"
            editorRotateRightButton.text = "↻ ${formatEditorNumber(rotationStep())}"
        }
        editorGroupButton.alpha = if (editorGroupMode == EditorGroupMode.OFF) 0.82f else 1f
        val selectedFloor = selected?.kind == ToyboxVolumeKind.FLOOR
        editorSolidButton.text = when {
            selectedFloor -> "Sol"
            editorSolid -> "Solide"
            else -> "Décor"
        }
        editorPlaceButton.text = when {
            selected != null -> "Valider"
            selectedTrack != null -> "Valider"
            selectedDecor != null -> "Valider"
            hasGroup -> "Valider"
            hasTrackDraft -> "Poser"
            hasDecorDraft -> "Poser"
            hasDraft -> "Poser"
            else -> "Rien"
        }
        val canDuplicate = selected != null || selectedTrack != null || selectedDecor != null || hasGroup
        editorSolidButton.isEnabled = canEditBlock && !selectedFloor
        editorSolidButton.alpha = if (editorSolidButton.isEnabled) 0.82f else 0.38f
        editorColorButton.isEnabled = canEditBlock || canEditTrack
        editorColorButton.alpha = if (editorColorButton.isEnabled) 0.82f else 0.38f
        editorPlaceButton.isEnabled = canEditBlock || canEditTrack || canEditDecor || hasGroup
        editorPlaceButton.alpha = if (editorPlaceButton.isEnabled) 0.82f else 0.38f
        if (::editorDuplicateButton.isInitialized) {
            editorDuplicateButton.isEnabled = canDuplicate
            editorDuplicateButton.alpha = if (canDuplicate) 0.82f else 0.38f
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
                selectedTrack != null -> "Section piste #${selectedTrack.id.toString().takeLast(4)}"
                selectedDecor != null -> "Modele 3D: ${decorName(selectedDecor.modelId)}"
                hasGroup -> "Groupe selectionne (${selectedGroupIds.size} blocs)"
                hasTrackDraft -> "Nouvelle section de piste"
                hasDecorDraft -> "Nouveau modele 3D: ${draftDecorModel?.let { decorName(it.id) } ?: "inconnu"}"
                hasDraft -> "Nouveau bloc"
                else -> "Aucun bloc selectionne"
            })
            append("\n")
            if (canEditTrack) {
                append(getString(R.string.toybox_track_hint)).append("\n")
                if (selectedTrack != null && trackEndpoint >= 0) {
                    append(getString(R.string.toybox_track_point)).append(" ").append(trackEndpoint + 1)
                    append(" · Y ").append(formatEditorNumber(if (trackEndpoint == 0) selectedTrack.y else selectedTrack.endY)).append("\n")
                }
                append("Type: piste  ").append(gridLabel())
                append("  Largeur ").append(formatEditorNumber(editorWidth))
                append("  Longueur ").append(formatEditorNumber(editorDepth))
                append("  Deniv ").append(formatEditorNumber(editorHeight))
                append("  Banking ").append(formatEditorNumber(editorRollDegrees)).append(" deg")
            } else if (canEditDecor) {
                val yaw = selectedDecor?.yawDegrees ?: editorDecorYawDegrees
                val scale = selectedDecor?.scale ?: editorDecorScale
                append("Type: modele 3D  ").append(gridLabel())
                append("  Angle ").append(formatEditorNumber(yaw)).append(" deg")
                append("  Echelle ").append(formatEditorNumber(scale))
            } else {
                append("Type: ").append(editorKind.label).append("  ").append(gridLabel())
                append("  L ").append(formatEditorNumber(editorWidth))
                append("  P ").append(formatEditorNumber(editorDepth))
                append("  H ").append(formatEditorNumber(editorHeight))
                append("  Angle ").append(formatEditorNumber(editorYawDegrees)).append(" deg")
                append("  Incl ").append(formatEditorNumber(editorPitchDegrees)).append("/")
                    .append(formatEditorNumber(editorRollDegrees)).append(" deg")
            }
            append("  Y ").append(formatEditorNumber(editorFloorY))
            append("\n")
            append(when {
                canEditTrack -> "surface conduite"
                canEditDecor -> "modele procedural 3D"
                editorSolid -> "solide"
                else -> "decor seulement"
            })
            append("  ").append(editorWorld.volumes.size + editorWorld.decorations.size).append(" objets")
            append("  ").append(editorWorld.trackSections.size).append(" pistes")
            if (selected == null && selectedTrack == null && selectedDecor == null) {
                append(if (hasDraft || hasTrackDraft || hasDecorDraft) "  |  Poser pour creer" else "  |  + pour ajouter")
            }
        }
        renderer.setEditorSelection(selected)
        renderer.setEditorPreviewVisible(!hasGroup && !canEditDecor && !canEditTrack && (selected != null || hasDraft))
        renderer.setEditorPreview(
            editorKind,
            editorWidth,
            editorHeight,
            editorDepth,
            gridSize(),
            editorFloorY,
            editorSolid,
            editorColor,
            editorQuarterTurns,
            editorYawDegrees,
            editorPitchDegrees,
            editorRollDegrees,
            editorRotationAxis
        )
        renderer.setEditorTrackPreview(if (canEditTrack) {
            selectedTrack ?: run {
                val anchor = renderer.makePreviewVolume(-3L)
                ToyboxTrackSection(
                    id = -3L,
                    x = anchor.x,
                    y = editorFloorY,
                    z = anchor.z,
                    yawDegrees = editorYawDegrees,
                    length = editorDepth,
                    width = editorWidth,
                    endY = editorFloorY + editorHeight,
                    bankDegrees = editorRollDegrees,
                    color = editorColor
                ).snappedTo(editorWorld.trackSections, TRACK_SNAP_DISTANCE)
            }
        } else null)
        renderer.setEditorDecorPreview(if (hasDecorDraft) {
            val anchor = renderer.makePreviewVolume(-2L)
            ToyboxDecor(
                id = -2L,
                modelId = draftDecorModel!!.id,
                x = anchor.x,
                y = editorFloorY,
                z = anchor.z,
                quarterTurns = editorDecorQuarterTurns,
                scale = editorDecorScale,
                yawDegrees = editorDecorYawDegrees
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
        editorSolid = if (start.kind == ToyboxVolumeKind.FLOOR) true else start.solid
        editorColor = start.color
        editorFloorY = if (start.kind == ToyboxVolumeKind.FLOOR) start.y + start.height * 0.5f
            else start.y - start.height * 0.5f
        pushEditorPreview()
    }

    private fun selectEditorVolume(id: Long) {
        val volume = editorWorld.volumes.firstOrNull { it.id == id } ?: return
        selectedVolumeId = id
        selectedTrackId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftTrackActive = false
        editorDraftDecorModelId = null
        editorKind = volume.kind
        editorWidth = volume.width
        editorHeight = volume.height
        editorDepth = volume.depth
        editorQuarterTurns = volume.quarterTurns
        editorYawDegrees = volume.yawDegrees
        editorPitchDegrees = volume.pitchDegrees
        editorRollDegrees = volume.rollDegrees
        editorSolid = if (volume.kind == ToyboxVolumeKind.FLOOR) true else volume.solid
        editorColor = volume.color
        editorFloorY = if (volume.kind == ToyboxVolumeKind.FLOOR) volume.y + volume.height * 0.5f
            else volume.y - volume.height * 0.5f
        pushEditorPreview()
    }

    private fun selectEditorTrack(id: Long) {
        trackTapFraction = 0.5f
        trackEndpoint = -1
        editorTouchLayer.selectedHandle = -1
        val section = editorWorld.trackSections.firstOrNull { it.id == id } ?: return
        selectedVolumeId = null
        selectedTrackId = id
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftTrackActive = false
        editorDraftDecorModelId = null
        editorWidth = section.width
        editorHeight = section.endY - section.y
        editorDepth = section.length
        editorYawDegrees = section.yawDegrees
        editorPitchDegrees = 0f
        editorRollDegrees = section.bankDegrees
        editorFloorY = section.y
        editorSolid = true
        editorColor = section.color
        pushEditorPreview()
    }

    private fun selectEditorDecor(id: Long) {
        val decor = editorWorld.decorations.firstOrNull { it.id == id } ?: return
        selectedVolumeId = null
        selectedTrackId = null
        selectedDecorId = id
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftTrackActive = false
        editorDraftDecorModelId = null
        editorFloorY = decor.y
        editorDecorQuarterTurns = decor.quarterTurns
        editorDecorYawDegrees = decor.yawDegrees
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
        rememberEditorUndo()
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes.map { volume ->
            if (volume.id in group) volume.copy(
                x = snapEditor(volume.x + dx),
                z = snapEditor(volume.z + dz)
            ) else volume
        })
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
    }

    private fun rotateSelectedGroup(deltaDegrees: Float) {
        val group = selectedGroupIds
        if (group.isEmpty()) return
        val volumes = editorWorld.volumes.filter { it.id in group }
        if (volumes.isEmpty()) return
        rememberEditorUndo()
        if (editorRotationAxis != ToyboxRotationAxis.YAW) {
            editorWorld = editorWorld.copy(volumes = editorWorld.volumes.map { volume ->
                if (volume.id !in group) volume else rotateVolumeOnSelectedAxis(volume, deltaDegrees)
            })
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
            return
        }
        val centerX = volumes.map { it.x }.average().toFloat()
        val centerZ = volumes.map { it.z }.average().toFloat()
        val radians = deltaDegrees * kotlin.math.PI.toFloat() / 180f
        val c = kotlin.math.cos(radians)
        val s = kotlin.math.sin(radians)
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes.map { volume ->
            if (volume.id !in group) volume else {
                val dx = volume.x - centerX
                val dz = volume.z - centerZ
                volume.rotateYaw(deltaDegrees).copy(
                    x = snapEditor(centerX + dx * c + dz * s),
                    z = snapEditor(centerZ - dx * s + dz * c)
                )
            }
        })
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
    }

    private fun moveEditorObject(strafe: Float, forward: Float) {
        if (!editorActive) return
        if (selectedVolumeId == null && selectedTrackId == null && selectedDecorId == null &&
            selectedGroupIds.isEmpty() && !editorDraftActive && !editorDraftTrackActive &&
            editorDraftDecorModelId == null) return
        val delta = renderer.editorNudgeDelta(strafe, forward, gridSize())
        val selected = selectedVolumeId
        if (selectedGroupIds.isNotEmpty()) {
            moveSelectedGroup(delta.x, delta.z)
        } else if (selectedTrackId != null) {
            updateSelectedTrack { section ->
                section.moveTo(snapEditor(section.x + delta.x), snapEditor(section.z + delta.z))
            }
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

    private fun rotateEditorObject(deltaDegrees: Float) {
        if (selectedGroupIds.isNotEmpty()) {
            rotateSelectedGroup(deltaDegrees)
            pushEditorPreview()
            return
        }
        if (selectedTrackId != null) {
            updateSelectedTrack { section ->
                when (editorRotationAxis) {
                    ToyboxRotationAxis.YAW -> section.rotateYaw(deltaDegrees).also {
                        editorYawDegrees = it.yawDegrees
                    }
                    ToyboxRotationAxis.PITCH -> section.withEndY(section.endY + deltaDegrees * 0.12f).also {
                        editorHeight = it.endY - it.y
                    }
                    ToyboxRotationAxis.ROLL -> section.copy(
                        bankDegrees = (section.bankDegrees + deltaDegrees).coerceIn(-38f, 38f)
                    ).also {
                        editorRollDegrees = it.bankDegrees
                    }
                }
            }
            pushEditorPreview()
            return
        }
        if (editorDraftTrackActive) {
            when (editorRotationAxis) {
                ToyboxRotationAxis.YAW -> editorYawDegrees = ((editorYawDegrees + deltaDegrees) % 360f + 360f) % 360f
                ToyboxRotationAxis.PITCH -> editorHeight += deltaDegrees * 0.12f
                ToyboxRotationAxis.ROLL -> editorRollDegrees = (editorRollDegrees + deltaDegrees).coerceIn(-38f, 38f)
            }
            pushEditorPreview()
            return
        }
        if (selectedDecorId != null) {
            updateSelectedDecor { decor ->
                decor.rotateYaw(deltaDegrees).also {
                    editorDecorQuarterTurns = it.quarterTurns
                    editorDecorYawDegrees = it.yawDegrees
                }
            }
            pushEditorPreview()
            return
        }
        if (editorDraftDecorModelId != null) {
            val normalized = ((editorDecorYawDegrees + deltaDegrees) % 360f + 360f) % 360f
            editorDecorYawDegrees = normalized
            editorDecorQuarterTurns = ((normalized / 90f).toInt() % 4 + 4) % 4
            pushEditorPreview()
            return
        }
        if (selectedVolumeId == null && !editorDraftActive) return
        if (selectedVolumeId == null) {
            when (editorRotationAxis) {
                ToyboxRotationAxis.YAW -> {
                    editorYawDegrees = ((editorYawDegrees + deltaDegrees) % 360f + 360f) % 360f
                    editorQuarterTurns = ((editorYawDegrees / 90f).toInt() % 4 + 4) % 4
                }
                ToyboxRotationAxis.PITCH -> editorPitchDegrees += deltaDegrees
                ToyboxRotationAxis.ROLL -> editorRollDegrees += deltaDegrees
            }
        }
        updateSelectedVolume { volume ->
            rotateVolumeOnSelectedAxis(volume, deltaDegrees).also {
                editorWidth = it.width
                editorDepth = it.depth
                editorQuarterTurns = it.quarterTurns
                editorYawDegrees = it.yawDegrees
                editorPitchDegrees = it.pitchDegrees
                editorRollDegrees = it.rollDegrees
            }
        }
        pushEditorPreview()
    }

    private fun rotateVolumeOnSelectedAxis(volume: ToyboxVolume, deltaDegrees: Float): ToyboxVolume =
        when (editorRotationAxis) {
            ToyboxRotationAxis.YAW -> volume.rotateYaw(deltaDegrees)
            ToyboxRotationAxis.PITCH -> volume.rotatePitch(deltaDegrees)
            ToyboxRotationAxis.ROLL -> volume.rotateRoll(deltaDegrees)
        }

    private fun snapEditor(value: Float): Float {
        val grid = gridSize()
        return kotlin.math.round(value / grid) * grid
    }

    private fun rotationStep(): Float = EDITOR_ROTATION_STEPS[editorRotationStepIndex]

    private fun clearEditorSelection() {
        selectedVolumeId = null
        selectedTrackId = null
        selectedDecorId = null
        selectedGroupIds = emptySet()
        editorDraftActive = false
        editorDraftTrackActive = false
        editorDraftDecorModelId = null
        editorQuarterTurns = 0
        editorYawDegrees = 0f
        editorPitchDegrees = 0f
        editorRollDegrees = 0f
        pushEditorPreview()
    }

    private fun selectedVolume() = selectedVolumeId?.let { id -> editorWorld.volumes.firstOrNull { it.id == id } }

    private fun selectedTrack() = selectedTrackId?.let { id -> editorWorld.trackSections.firstOrNull { it.id == id } }

    private fun trackHandles(): List<Pair<Float, Float>> {
        val section = selectedTrack() ?: return emptyList()
        val start = renderer.projectEditorPoint(section.startX, section.y, section.startZ) ?: return emptyList()
        val end = renderer.projectEditorPoint(section.finishX, section.endY, section.finishZ) ?: return emptyList()
        return listOf(start, end)
    }

    private fun syncTrackFields() {
        selectedTrack()?.let {
            editorFloorY = it.y
            editorHeight = it.endY - it.y
            editorDepth = it.length
            editorYawDegrees = it.yawDegrees
        }
        renderer.setEditorWorld(editorWorld)
        pushEditorPreview()
    }

    /** Move shared endpoints together, using the original gesture snapshot to avoid drift. */
    private fun moveTrackEndpoint(section: ToyboxTrackSection, finish: Boolean, x: Float, y: Float, z: Float) {
        val ox = if (finish) section.finishX else section.startX
        val oy = if (finish) section.endY else section.y
        val oz = if (finish) section.finishZ else section.startZ
        fun connected(px: Float, py: Float, pz: Float) =
            kotlin.math.hypot(px - ox, pz - oz) < 0.025f && kotlin.math.abs(py - oy) < 0.025f
        editorWorld = editorWorld.copy(trackSections = editorWorld.trackSections.map { other ->
            when {
                other.id == section.id -> other.withEndpoint(finish, x, y, z)
                connected(other.startX, other.y, other.startZ) -> other.withEndpoint(false, x, y, z)
                connected(other.finishX, other.endY, other.finishZ) -> other.withEndpoint(true, x, y, z)
                else -> other
            }
        })
    }

    private fun dragTrackEndpoint(x: Float, y: Float) {
        val section = trackDragSection ?: return
        val origin = trackDragOrigin ?: return
        val state = trackDragUndo ?: return
        val finish = trackEndpoint == 1
        val altitude = if (finish) section.endY else section.y
        val point = renderer.editorPointOnPlane(x, y, altitude) ?: return
        editorWorld = state.world
        moveTrackEndpoint(section, finish,
            snapEditor((if (finish) section.finishX else section.startX) + point.x - origin.first),
            altitude,
            snapEditor((if (finish) section.finishZ else section.startZ) + point.z - origin.second))
        syncTrackFields()
    }

    private fun finishTrackDrag(cancelled: Boolean) {
        val state = trackDragUndo ?: return
        if (cancelled) editorWorld = state.world
        else if (editorWorld != state.world) {
            rememberEditorUndo(state)
            worldStore.save(editorWorld)
        }
        trackDragUndo = null
        trackDragOrigin = null
        trackDragSection = null
        syncTrackFields()
    }

    private fun extendSelectedTrack() {
        val source = selectedTrack() ?: return
        rememberEditorUndo()
        val length = 20f
        val section = ToyboxTrackSection(System.nanoTime(),
            source.finishX + source.forwardX * length * 0.5f, source.endY,
            source.finishZ + source.forwardZ * length * 0.5f,
            source.yawDegrees, length, source.endWidth, color = source.color)
        editorWorld = editorWorld.copy(trackSections = editorWorld.trackSections + section)
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
        selectEditorTrack(section.id)
        trackEndpoint = 1
        editorTouchLayer.selectedHandle = 1
        pushEditorPreview()
    }

    private fun splitSelectedTrack() {
        val section = selectedTrack() ?: return
        if (section.length < 0.5f) return
        val t = trackTapFraction
        val x = section.startX + (section.finishX - section.startX) * t
        val y = section.y + (section.endY - section.y) * t
        val z = section.startZ + (section.finishZ - section.startZ) * t
        val backLeft = section.corner(-1f, -1f)
        val backRight = section.corner(-1f, 1f)
        val frontLeft = section.corner(1f, -1f)
        val frontRight = section.corner(1f, 1f)
        val rx = (backRight.x - backLeft.x) * (1f - t) + (frontRight.x - frontLeft.x) * t
        val rz = (backRight.z - backLeft.z) * (1f - t) + (frontRight.z - frontLeft.z) * t
        val width = kotlin.math.hypot(rx, rz)
        val offset = kotlin.math.atan2(-rz, rx) * 180f / kotlin.math.PI.toFloat() - section.yawDegrees
        val first = section.withEndpoint(true, x, y, z).copy(endWidth = width, endYawOffset = offset)
        val second = section.withEndpoint(false, x, y, z).copy(id = System.nanoTime(), width = width, startYawOffset = offset)
        rememberEditorUndo()
        editorWorld = editorWorld.copy(trackSections = editorWorld.trackSections.flatMap {
            if (it.id == section.id) listOf(first, second) else listOf(it)
        })
        worldStore.save(editorWorld)
        renderer.setEditorWorld(editorWorld)
        selectEditorTrack(first.id)
        trackEndpoint = 1
        editorTouchLayer.selectedHandle = 1
        pushEditorPreview()
    }

    private fun selectedDecor() = selectedDecorId?.let { id -> editorWorld.decorations.firstOrNull { it.id == id } }

    private fun updateSelectedVolume(
        undoState: EditorUndoState = captureEditorUndoState(),
        change: (ToyboxVolume) -> ToyboxVolume
    ) {
        val selected = selectedVolumeId ?: return
        var changed = false
        editorWorld = editorWorld.copy(volumes = editorWorld.volumes.map { volume ->
            if (volume.id == selected) {
                changed = true
                change(volume).let { changedVolume ->
                    if (changedVolume.kind == ToyboxVolumeKind.FLOOR) changedVolume.copy(solid = true) else changedVolume
                }
            } else volume
        })
        if (changed) {
            rememberEditorUndo(undoState)
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
        }
    }

    private fun updateSelectedDecor(
        undoState: EditorUndoState = captureEditorUndoState(),
        change: (ToyboxDecor) -> ToyboxDecor
    ) {
        val selected = selectedDecorId ?: return
        var changed = false
        editorWorld = editorWorld.copy(decorations = editorWorld.decorations.map { decor ->
            if (decor.id == selected) {
                changed = true
                change(decor)
            } else decor
        })
        if (changed) {
            rememberEditorUndo(undoState)
            worldStore.save(editorWorld)
            renderer.setEditorWorld(editorWorld)
        }
    }

    private fun updateSelectedTrack(
        undoState: EditorUndoState = captureEditorUndoState(),
        change: (ToyboxTrackSection) -> ToyboxTrackSection
    ) {
        val selected = selectedTrackId ?: return
        var changed = false
        editorWorld = editorWorld.copy(trackSections = editorWorld.trackSections.map { section ->
            if (section.id == selected) {
                changed = true
                change(section)
            } else section
        })
        if (changed) {
            rememberEditorUndo(undoState)
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
            val undoState = captureEditorUndoState()
            editorColor = Color.parseColor(colorHex)
            if (selectedTrackId != null) {
                updateSelectedTrack(undoState) { it.copy(color = editorColor) }
            } else {
                updateSelectedVolume(undoState) { it.copy(color = editorColor) }
            }
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
        val minimumFloorY: Float = 0f,
        val pitchDegrees: Float = 0f,
        val rollDegrees: Float = 0f
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
        private const val KEY_CURRENT_CREATION_FILE = "current_creation_file"
        private val EDITOR_GRIDS = arrayOf(
            1f to "10cm",
            0.1f to "1cm",
            0.01f to "0.1cm"
        )
        private const val EDITOR_DIMENSION_MIN_TICKS = 1
        private const val EDITOR_DIMENSION_MAX_SIZE = 100f
        private const val EDITOR_UNDO_LIMIT = 20
        private const val EDITOR_REPEAT_INITIAL_DELAY_MS = 260L
        private const val EDITOR_REPEAT_INTERVAL_MS = 82L
        private const val TRACK_SNAP_DISTANCE = 3.0f
        private val EDITOR_ROTATION_STEPS = floatArrayOf(1f, 15f, 90f)
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
                    EditorItemPreset("Pan de toit", ToyboxVolumeKind.WALL, 28f, 0.55f, 12f, minimumFloorY = 6f, pitchDegrees = -35f),
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
