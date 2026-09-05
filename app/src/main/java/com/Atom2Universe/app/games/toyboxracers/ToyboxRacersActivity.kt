package com.Atom2Universe.app.games.toyboxracers

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
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
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.toyboxracers.game.PlayMode
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import com.Atom2Universe.app.games.toyboxracers.track.RoomKind
import com.Atom2Universe.app.games.toyboxracers.track.CircuitKind
import com.Atom2Universe.app.games.toyboxracers.track.HousePlan
import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.game.RacePhase
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
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
    private var currentScene = SceneChoice()
    private lateinit var housePlan: HousePlan
    private lateinit var minimap: ToyboxMinimapView
    private var currentMode = PlayMode.EXPLORATION
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
        glView = ToyboxRacersGLView(this, renderer)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(184, 219, 240))
            // Indispensable pour maintenir GAZ tout en dirigeant avec un autre doigt.
            setMotionEventSplittingEnabled(true)
        }
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))
        addHud(root)
        addControls(root)
        minimap = ToyboxMinimapView(this)
        val compact = resources.displayMetrics.widthPixels / resources.displayMetrics.density < 640f
        root.addView(minimap, FrameLayout.LayoutParams(dp(if (compact) 112 else 152), dp(if (compact) 80 else 106)).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(20)
        })
        addRaceOverlay(root)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = showPause()
        })
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        enableImmersiveMode()
        if (paused) showPause()
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

        val back = makeButton("Ⅱ", 52, 0xB83B4055.toInt()).apply {
            textSize = 28f
            contentDescription = getString(R.string.toybox_pause)
            setOnClickListener { showPause() }
        }
        root.addView(back, FrameLayout.LayoutParams(dp(52), dp(52)).apply {
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
        modeButton = makeButton(getString(R.string.toybox_start_race), 112, 0xAA735D91.toInt()).apply {
            textSize = 11f
            setOnClickListener { switchMode() }
        }
        root.addView(modeButton, FrameLayout.LayoutParams(dp(112), dp(42)).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dp(18)
            topMargin = dp(122)
        })
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
        for ((index, button) in listOf(roomButton, circuitButton, houseButton).withIndex()) {
            root.addView(button, FrameLayout.LayoutParams(dp(if (index == 2) 96 else 120), dp(42)).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(16 + if (index < 2) index * 128 else 256)
                topMargin = dp(128)
            })
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

        val brake = makeButton("FREIN\nRECUL", controls.brake, 0xB8735D91.toInt()).apply {
            textSize = 12f
        }
        root.addView(brake, FrameLayout.LayoutParams(dp(controls.brake), dp(controls.brake)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(controls.accelerator + 34)
            bottomMargin = dp(26)
        })

        val accelerator = makeButton("GAZ", controls.accelerator, 0xB8E26F82.toInt()).apply {
            textSize = 13f
        }
        root.addView(accelerator, FrameLayout.LayoutParams(dp(controls.accelerator), dp(controls.accelerator)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(22)
            bottomMargin = dp(16)
        })

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
    private fun bindHoldButton(view: View, changed: (Boolean) -> Unit) {
        var activePointerId = MotionEvent.INVALID_POINTER_ID
        releaseControls += {
            activePointerId = MotionEvent.INVALID_POINTER_ID
            view.isPressed = false
            view.alpha = 0.82f
            changed(false)
        }
        view.setOnTouchListener { touchedView, event ->
            if (paused) return@setOnTouchListener true
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
        if (isFinishing || isDestroyed || state.mode != currentMode || state.scene != currentScene) return
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

    companion object {
        private const val PREFS_NAME = "toybox_racers_save"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_BEST_PREFIX = "best_time_"
        private const val KEY_HOUSE_SEED = "house_seed_v1"
        private const val KEY_ROOM_CIRCUIT = "house_circuit_v1_"
    }
}
