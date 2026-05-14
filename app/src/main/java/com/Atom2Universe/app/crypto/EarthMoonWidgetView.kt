package com.Atom2Universe.app.crypto

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.google.android.material.card.MaterialCardView
import kotlin.math.hypot

class EarthMoonWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cardView: MaterialCardView
    private val canvasView: EarthMoonCanvasView
    private val viewLabel: TextView
    private val prevBtn: TextView
    private val nextBtn: TextView
    private val locationPrevBtn: TextView
    private val locationNextBtn: TextView

    private val minScale = 0.6f
    private val maxScale = 3.0f
    private var currentScale = 1f

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastDragX = 0f
    private var lastDragY = 0f
    private var isDragging = false
    private var isTwoFingerGesture = false
    private var skipNextDragFrame = false

    private val views = EarthMoonCanvasView.CameraView.values()
    private var currentViewIndex = 0
    private var fixedLocationName: String = ""
    private var currentLocationIndex: Int = 0

    // Seuil en pixels pour distinguer tap vs drag
    private val tapThresholdPx = 12f * context.resources.displayMetrics.density

    // Handler pour masquer les boutons après inactivité
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { setButtonsVisible(false) }
    private val HIDE_DELAY_MS = 2500L

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                if (next != currentScale) {
                    currentScale = next
                    scaleX = currentScale
                    scaleY = currentScale
                }
                return true
            }
        })

    init {
        LayoutInflater.from(context).inflate(R.layout.view_earth_moon_widget, this, true)
        isClickable = true
        isFocusable = true

        cardView          = findViewById(R.id.earth_moon_card)
        canvasView        = findViewById(R.id.earth_moon_canvas)
        viewLabel         = findViewById(R.id.earth_moon_view_label)
        prevBtn           = findViewById(R.id.earth_moon_view_prev)
        nextBtn           = findViewById(R.id.earth_moon_view_next)
        locationPrevBtn   = findViewById(R.id.earth_moon_location_prev)
        locationNextBtn   = findViewById(R.id.earth_moon_location_next)

        // Boutons cachés par défaut
        setButtonsVisible(false)

        prevBtn.setOnClickListener {
            cycleView(-1)
            showButtonsTemporarily()
        }
        nextBtn.setOnClickListener {
            cycleView(+1)
            showButtonsTemporarily()
        }

        locationPrevBtn.setOnClickListener {
            cycleLocation(-1)
            showButtonsTemporarily()
        }
        locationNextBtn.setOnClickListener {
            cycleLocation(+1)
            showButtonsTemporarily()
        }

        // Le multiplicateur par défaut (1.3f) place la lune hors du widget car
        // 0.42f * 1.3f = 0.546 > 0.5 (demi-largeur). On ramène à 0.82f.
        canvasView.moonDistanceMultiplier = 1.07f

        updateViewLabel()

        setOnTouchListener { _, event -> handleTouch(event) }
    }

    fun updateSnapshot(snap: AstronomyCalculator.AstroSnapshot) {
        canvasView.updateSnapshot(snap)
    }

    fun setEarthOnlyMode(earthOnly: Boolean) {
        canvasView.showMoon = !earthOnly
    }

    fun setShowClouds(showClouds: Boolean) {
        canvasView.showClouds = showClouds
    }

    fun setShowTerminator(show: Boolean) {
        canvasView.showTerminator = show
    }

    fun setFixedLocation(latDeg: Double, lonDeg: Double, name: String) {
        canvasView.fixedLatDeg = latDeg
        canvasView.fixedLonDeg = lonDeg
        fixedLocationName = name
        currentLocationIndex = EarthMoonCanvasView.LOCATION_PRESETS.indexOfFirst { it.name == name }.coerceAtLeast(0)
        updateViewLabel()
    }

    private fun cycleLocation(direction: Int) {
        val presets = EarthMoonCanvasView.LOCATION_PRESETS
        currentLocationIndex = ((currentLocationIndex + direction) + presets.size) % presets.size
        val preset = presets[currentLocationIndex]
        canvasView.fixedLatDeg = preset.latDeg
        canvasView.fixedLonDeg = preset.lonDeg
        fixedLocationName = preset.name
        MainClickerPreferences.setEarthFixedLocationIndex(context, currentLocationIndex)
        updateViewLabel()
    }

    private fun cycleView(direction: Int) {
        currentViewIndex = ((currentViewIndex + direction) + views.size) % views.size
        canvasView.cameraView = views[currentViewIndex]
        updateViewLabel()
    }

    private fun updateViewLabel() {
        val view = views[currentViewIndex]
        viewLabel.text = if (view == EarthMoonCanvasView.CameraView.FIXED_LOCATION && fixedLocationName.isNotEmpty()) {
            fixedLocationName
        } else {
            view.label
        }
    }

    private fun showButtonsTemporarily() {
        setButtonsVisible(true)
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY_MS)
    }

    private fun setButtonsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.INVISIBLE
        prevBtn.visibility = v
        nextBtn.visibility = v
        val isFixedLocation = views[currentViewIndex] == EarthMoonCanvasView.CameraView.FIXED_LOCATION
        val locV = if (visible && isFixedLocation) View.VISIBLE else View.INVISIBLE
        locationPrevBtn.visibility = locV
        locationNextBtn.visibility = locV
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront()
                downRawX = event.rawX
                downRawY = event.rawY
                lastDragX = event.rawX
                lastDragY = event.rawY
                isDragging = false
                isTwoFingerGesture = false
                skipNextDragFrame = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isTwoFingerGesture = true
                isDragging = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Quand un doigt se lève après un pinch, on ignore le premier
                // ACTION_MOVE à 1 doigt pour éviter le saut de position.
                skipNextDragFrame = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (skipNextDragFrame) {
                        // Recaler le point de référence sans bouger le widget
                        lastDragX = event.rawX
                        lastDragY = event.rawY
                        downRawX = event.rawX
                        downRawY = event.rawY
                        isDragging = false
                        skipNextDragFrame = false
                    } else {
                        val moved = hypot(event.rawX - downRawX, event.rawY - downRawY)
                        if (isDragging || moved > tapThresholdPx) {
                            isDragging = true
                            translationX += event.rawX - lastDragX
                            translationY += event.rawY - lastDragY
                        }
                        lastDragX = event.rawX
                        lastDragY = event.rawY
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && !isTwoFingerGesture) {
                    showButtonsTemporarily()
                }
                isDragging = false
                isTwoFingerGesture = false
                skipNextDragFrame = false
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isTwoFingerGesture = false
                skipNextDragFrame = false
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        hideHandler.removeCallbacks(hideRunnable)
        super.onDetachedFromWindow()
    }
}
