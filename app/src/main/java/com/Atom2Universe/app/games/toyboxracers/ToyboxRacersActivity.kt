package com.Atom2Universe.app.games.toyboxracers

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import com.Atom2Universe.app.util.enableImmersiveMode
import java.util.Locale

/**
 * Activité interne du prototype P1. Elle est déclarée dans le manifeste mais ne
 * sera ajoutée au hub public qu'une fois la boucle de course suffisamment stable.
 */
class ToyboxRacersActivity : ThemedActivity() {
    private lateinit var glView: ToyboxRacersGLView
    private lateinit var renderer: ToyboxRacersRenderer
    private lateinit var hud: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        renderer = ToyboxRacersRenderer { state ->
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
            text = "TOYBOX RACERS · PROTOTYPE P1"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        hud = TextView(this).apply {
            text = "0 km/h   ·   Exploration   ·   00:00.0"
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
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addControls(root: FrameLayout) {
        val steering = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val left = makeButton("◀", 78, 0xA84B617A.toInt())
        val right = makeButton("▶", 78, 0xA84B617A.toInt())
        steering.addView(left, LinearLayout.LayoutParams(dp(78), dp(78)).apply { rightMargin = dp(10) })
        steering.addView(right, LinearLayout.LayoutParams(dp(78), dp(78)))
        root.addView(steering, FrameLayout.LayoutParams(-2, dp(78)).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(20)
            bottomMargin = dp(20)
        })

        val brake = makeButton("FREIN\nDÉRAPAGE", 88, 0xB8735D91.toInt()).apply {
            textSize = 12f
        }
        root.addView(brake, FrameLayout.LayoutParams(dp(88), dp(88)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(142)
            bottomMargin = dp(26)
        })

        val accelerator = makeButton("GAZ", 108, 0xB8E26F82.toInt()).apply {
            textSize = 13f
        }
        root.addView(accelerator, FrameLayout.LayoutParams(dp(108), dp(108)).apply {
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
        val minutes = state.elapsedSeconds.toInt() / 60
        val seconds = state.elapsedSeconds % 60f
        hud.text = String.format(
            Locale.ROOT,
            "%3d km/h   ·   Libre   ·   %02d:%04.1f",
            state.speedKmh,
            minutes,
            seconds
        )
        status.text = when {
            state.airborne -> "SAUT !  Prépare la réception"
            state.offRoad -> "Exploration libre — aucune obligation de revenir"
            else -> "Maintiens GAZ — freine en tournant pour déraper"
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
}
