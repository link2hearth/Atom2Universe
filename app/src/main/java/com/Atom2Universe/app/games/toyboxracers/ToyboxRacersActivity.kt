package com.Atom2Universe.app.games.toyboxracers

import android.annotation.SuppressLint
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.game.RacePhase
import com.Atom2Universe.app.games.toyboxracers.game.RaceSession
import com.Atom2Universe.app.util.enableImmersiveMode
import java.util.Locale

/**
 * Activité interne du prototype P2. Elle est déclarée dans le manifeste mais ne
 * sera ajoutée au hub public qu'une fois la boucle de course suffisamment stable.
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
        renderer = ToyboxRacersRenderer(currentDifficulty) { state ->
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
        addRaceOverlay(root)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        enableImmersiveMode()
    }

    override fun onPause() {
        renderer.setSteering(0f)
        renderer.setAccelerating(false)
        renderer.setBraking(false)
        glView.onPause()
        super.onPause()
    }

    private fun addHud(root: FrameLayout) {
        val density = resources.displayMetrics.density
        val topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            background = roundedBackground(0xB83B4055.toInt(), 18f)
        }
        val title = TextView(this).apply {
            text = "TOYBOX RACERS · PROTOTYPE P3"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        hud = TextView(this).apply {
            text = "0 km/h   ·   1/6   ·   Tour 1/3   ·   00:00.0"
            setTextColor(0xFFFFE7A8.toInt())
            textSize = 18f
            typeface = Typeface.MONOSPACE
        }
        status = TextView(this).apply {
            text = "Montée, plateau et saut inclus"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        topPanel.addView(title)
        topPanel.addView(hud)
        topPanel.addView(status)
        root.addView(topPanel, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (16 * density).toInt()
            topMargin = (12 * density).toInt()
        })

        val back = makeButton("×", 52, 0xB83B4055.toInt()).apply {
            textSize = 28f
            setOnClickListener { finish() }
        }
        root.addView(back, FrameLayout.LayoutParams(dp(52), dp(52)).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dp(14)
            topMargin = dp(12)
        })

        val reset = makeButton("DÉPART", 92, 0xAA735D91.toInt()).apply {
            textSize = 11f
            setOnClickListener { renderer.requestReset() }
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
        view.setOnTouchListener { touchedView, event ->
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
        if (state.turboLevel > lastTurboLevel) vibrate(18L, 35 + state.turboLevel * 20)
        if (state.turboReleaseSerial != lastTurboReleaseSerial) vibrate(42L, 105)
        lastTurboLevel = state.turboLevel
        lastTurboReleaseSerial = state.turboReleaseSerial
        val minutes = state.elapsedSeconds.toInt() / 60
        val seconds = state.elapsedSeconds % 60f
        hud.text = String.format(
            Locale.ROOT,
            "%3d km/h   ·   %d/6   ·   Tour %d/%d   ·   %02d:%04.1f",
            state.speedKmh,
            state.position,
            state.lap,
            RaceSession.TOTAL_LAPS,
            minutes,
            seconds
        )
        countdown.visibility = if (state.racePhase == RacePhase.COUNTDOWN) View.VISIBLE else View.GONE
        if (state.racePhase == RacePhase.COUNTDOWN) {
            countdown.text = kotlin.math.ceil(state.countdownSeconds.coerceAtMost(3f))
                .toInt().coerceAtLeast(1).toString()
        }
        if (state.finishSerial != lastFinishSerial && state.racePhase == RacePhase.FINISHED) {
            lastFinishSerial = state.finishSerial
            showResult(state)
        }
        status.text = when {
            state.racePhase == RacePhase.COUNTDOWN -> "Prépare-toi — le départ est verrouillé"
            state.racePhase == RacePhase.FINISHED -> "Course terminée"
            state.wrongWay -> "MAUVAIS SENS — fais demi-tour"
            state.airborne -> "SAUT !  Prépare la réception"
            state.reversing -> "MARCHE ARRIÈRE — relâche FREIN pour repartir"
            state.turboBoosting -> "RUBAN TURBO !  Relance pastel"
            state.drifting -> "Ruban ${"●".repeat(state.turboLevel.coerceAtLeast(1))}${"○".repeat((3 - state.turboLevel).coerceAtLeast(0))}  ${(state.turboCharge * 100).toInt()} %"
            state.offRoad -> "Exploration libre — aucune obligation de revenir"
            else -> "Maintiens GAZ — tourne, puis redresse pour le Ruban Turbo"
        }
    }

    private fun showResult(state: ToyboxRacersRenderer.HudState) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val key = KEY_BEST_PREFIX + state.difficulty.name
        val previousBest = prefs.getFloat(key, 0f)
        val newBest = previousBest <= 0f || state.elapsedSeconds < previousBest
        val best = if (newBest) state.elapsedSeconds else previousBest
        if (newBest) prefs.edit().putFloat(key, state.elapsedSeconds).apply()
        resultText.text = buildString {
            append(positionLabel(state.finishPosition)).append(" place\n")
            append(formatTime(state.elapsedSeconds)).append(" · ").append(state.difficulty.label)
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
    }
}
