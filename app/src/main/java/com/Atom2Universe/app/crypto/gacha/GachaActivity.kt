package com.Atom2Universe.app.crypto.gacha

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.AstronomyCalculator
import com.Atom2Universe.app.crypto.EarthMoonCanvasView
import com.Atom2Universe.app.crypto.bigbang.BigBangActivity
import com.Atom2Universe.app.crypto.clicker.BigBangRepository
import com.Atom2Universe.app.crypto.clicker.ElementTokenRepository
import com.Atom2Universe.app.crypto.clicker.GachaTicketRepository
import com.Atom2Universe.app.periodic.PeriodicCollectionStore
import com.Atom2Universe.app.periodic.PeriodicTableActivity
import com.Atom2Universe.app.periodic.PeriodicElement
import com.Atom2Universe.app.periodic.localizedName
import com.Atom2Universe.app.util.applySystemBarsVisibility
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GachaActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "gacha_prefs"
        const val KEY_DRAW_MULTIPLIER = "draw_multiplier"

        /** Multiplicateurs de tirage proposés, dans l'ordre de cycle du bouton. */
        private val MULTIPLIER_CYCLE = listOf(1, 10, 100)

        /**
         * À partir de ce nombre de tirages cumulés, l'animation multi-tirage ne joue plus
         * que l'effet de l'élément le plus rare (au lieu d'un effet par rareté).
         */
        private const val RAREST_ONLY_ANIMATION_THRESHOLD = 200
    }

    private data class MultiDrawResult(
        val element: PeriodicElement,
        val rarity: GachaRarity,
        val isFirst: Boolean,
        val totalCopies: Int
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var root: FrameLayout
    private lateinit var sunBtn: FrameLayout
    private lateinit var sunImage: ImageView
    private lateinit var sunGlow: View
    private lateinit var particleView: GachaParticleView
    private lateinit var earthMoonView: EarthMoonCanvasView
    private lateinit var resultCard: LinearLayout
    private lateinit var resultNumber: TextView
    private lateinit var resultSymbol: TextView
    private lateinit var resultName: TextView
    private lateinit var resultMass: TextView
    private lateinit var resultRarity: TextView
    private lateinit var resultCategory: TextView
    private lateinit var resultCopies: TextView

    private lateinit var collectionStore: PeriodicCollectionStore
    private lateinit var ticketRepository: GachaTicketRepository
    private lateinit var elementTokenRepo: ElementTokenRepository
    private lateinit var bigBangRepo: BigBangRepository
    private lateinit var ticketsDisplay: TextView
    private lateinit var bigBangBtn: TextView

    private var drawMultiplier = 1
    private lateinit var multiBtn: TextView
    private lateinit var multiResultOverlay: FrameLayout
    private lateinit var multiResultContainer: LinearLayout
    private val miniCardAnimators = mutableListOf<ValueAnimator>()

    private var isAnimating = false
    private var isFirstDiscovery = false
    private val handler = Handler(Looper.getMainLooper())
    private var glowPulseAnimator: ValueAnimator? = null
    private var discoveryAnimator: ValueAnimator? = null

    private val dismissGesture by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isAnimating && (multiResultOverlay.visibility == View.VISIBLE || resultCard.visibility == View.VISIBLE)) {
                    resetToIdle()
                    return true
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (multiResultOverlay.visibility == View.VISIBLE || resultCard.visibility == View.VISIBLE) {
            dismissGesture.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarsVisibility(showStatusBar = false, showNavBar = false)
        setContentView(R.layout.activity_gacha)

        root          = findViewById(R.id.gacha_root)
        sunBtn        = findViewById(R.id.gacha_sun_btn)
        sunImage      = findViewById(R.id.gacha_sun_image)
        sunGlow       = findViewById(R.id.gacha_sun_glow)
        particleView  = findViewById(R.id.gacha_particles)
        earthMoonView = findViewById(R.id.gacha_earth_moon)
        resultCard    = findViewById(R.id.gacha_result_card)
        resultNumber  = findViewById(R.id.gacha_result_number)
        resultSymbol  = findViewById(R.id.gacha_result_symbol)
        resultName    = findViewById(R.id.gacha_result_name)
        resultMass    = findViewById(R.id.gacha_result_mass)
        resultRarity  = findViewById(R.id.gacha_result_rarity)
        resultCategory = findViewById(R.id.gacha_result_category)
        resultCopies = findViewById(R.id.gacha_result_copies)
        ticketsDisplay = findViewById(R.id.gacha_tickets)
        multiBtn = findViewById(R.id.gacha_multi_btn)
        multiResultOverlay = findViewById(R.id.gacha_multi_result_overlay)
        multiResultContainer = findViewById(R.id.gacha_multi_result_container)

        collectionStore  = PeriodicCollectionStore(this)
        ticketRepository = GachaTicketRepository(this)
        elementTokenRepo = ElementTokenRepository(this)
        bigBangRepo      = BigBangRepository(this)

        loadAndDisplayTickets()

        bigBangBtn = findViewById(R.id.gacha_big_bang_btn)
        bigBangBtn.setOnClickListener {
            startActivity(Intent(this, BigBangActivity::class.java))
        }

        findViewById<ImageButton>(R.id.gacha_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.gacha_open_periodic).setOnClickListener {
            val intent = Intent(this, PeriodicTableActivity::class.java).apply {
                putExtra(PeriodicTableActivity.EXTRA_SOURCE, PeriodicTableActivity.SOURCE_GACHA)
            }
            startActivity(intent)
        }

        loadSunGif()
        configureEarthMoon()
        startGlowPulse()
        startSunRotation()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        drawMultiplier = prefs.getInt(KEY_DRAW_MULTIPLIER, 1)
        if (drawMultiplier !in MULTIPLIER_CYCLE) drawMultiplier = 1
        multiBtn.text = "×$drawMultiplier"

        multiBtn.setOnClickListener {
            val nextIdx = (MULTIPLIER_CYCLE.indexOf(drawMultiplier) + 1) % MULTIPLIER_CYCLE.size
            drawMultiplier = MULTIPLIER_CYCLE[nextIdx]
            multiBtn.text = "×$drawMultiplier"
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_DRAW_MULTIPLIER, drawMultiplier).apply()
        }
        multiResultOverlay.setOnClickListener { if (!isAnimating) resetToIdle() }

        sunBtn.setOnClickListener {
            when {
                multiResultOverlay.visibility == View.VISIBLE && !isAnimating -> resetToIdle()
                resultCard.visibility == View.VISIBLE -> resetToIdle()
                !isAnimating -> if (drawMultiplier > 1) startMultiGachaDraw(drawMultiplier) else startGachaDraw()
            }
        }
        resultCard.setOnClickListener { if (!isAnimating) resetToIdle() }
        root.setOnClickListener { if (!isAnimating && resultCard.visibility == View.VISIBLE) resetToIdle() }
        root.setOnTouchListener { _, event ->
            if (isAnimating && !isFirstDiscovery) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> particleView.setHoldSpeedBoost(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        particleView.setHoldSpeedBoost(false)
                    }
                }
            }
            false
        }
    }

    private fun loadSunGif() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(assets, "Assets/Image/Sun.gif")
                val drawable = ImageDecoder.decodeDrawable(source)
                sunImage.setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable) drawable.start()
            } else {
                val stream = assets.open("Assets/Image/Sun.gif")
                sunImage.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(stream, null))
                stream.close()
            }
        } catch (e: Exception) {
            // Fallback : cercle solaire simple via tint
            sunImage.setBackgroundResource(R.drawable.gacha_glow_ring)
            sunImage.setColorFilter(Color.parseColor("#FF8800"))
        }
    }

    private fun configureEarthMoon() {
        earthMoonView.cameraView = EarthMoonCanvasView.CameraView.FROM_SUN
        earthMoonView.showMoon = true
        earthMoonView.showClouds = true
        earthMoonView.drawBackground = false
        earthMoonView.moonDistanceMultiplier = 1.1f
        val snapshot = AstronomyCalculator.compute(System.currentTimeMillis())
        earthMoonView.updateSnapshot(snapshot)
    }

    private fun startGlowPulse() {
        glowPulseAnimator = ValueAnimator.ofFloat(0.2f, 0.7f).apply {
            duration = 1800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { sunGlow.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun startSunRotation() {
        ObjectAnimator.ofFloat(sunImage, "rotation", 0f, 360f).apply {
            duration = 60_000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = null
            start()
        }
    }

    private fun startGachaDraw() {
        lifecycleScope.launch {
            val ticketState = ticketRepository.awardTickets(System.currentTimeMillis())
            if (ticketState.totalTickets <= 0) {
                Toast.makeText(this@GachaActivity, R.string.gacha_no_tickets, Toast.LENGTH_SHORT).show()
                return@launch
            }

            isAnimating = true

            val launchDraw: () -> Unit = {
                val (element, rarity) = rollGacha()
                val isFirst = !collectionStore.hasEverObtained(element.atomicNumber)
                val totalCopies = collectionStore.addCopy(element.atomicNumber)
                elementTokenRepo.addTokens(1)
                isFirstDiscovery = isFirst

                // Cacher soleil et Terre/Lune dès le début de l'animation
                sunBtn.animate().alpha(0f).setDuration(300).start()
                earthMoonView.animate().alpha(0f).setDuration(300).start()
                glowPulseAnimator?.cancel()

                // Démarrer les particules
                particleView.post {
                    val categoryColor = getCategoryColor(element.category)
                    particleView.playEffect(rarity, categoryColor) {
                        particleView.setHoldSpeedBoost(false)
                        showResultCard(element, rarity, totalCopies, isFirst)
                    }
                }
            }

            // Consommer le ticket et mettre à jour l'affichage
            ticketRepository.consumeTicket()
            loadAndDisplayTickets()

            // Effet de pression sur le soleil
            sunBtn.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(120)
                .withEndAction {
                    sunBtn.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(150)
                        .withEndAction {
                            sunBtn.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                            launchDraw()
                        }
                        .start()
                }
                .start()
        }
    }

    private fun startMultiGachaDraw(count: Int) {
        lifecycleScope.launch {
            val ticketState = ticketRepository.awardTickets(System.currentTimeMillis())
            if (ticketState.totalTickets < count) {
                Toast.makeText(
                    this@GachaActivity,
                    getString(R.string.gacha_not_enough_tickets_multi, count),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            isAnimating = true

            ticketRepository.consumeTickets(count)
            loadAndDisplayTickets()

            // Roll all results
            val results = (1..count).map {
                val (element, rarity) = rollGacha()
                val isFirst = !collectionStore.hasEverObtained(element.atomicNumber)
                val totalCopies = collectionStore.addCopy(element.atomicNumber)
                elementTokenRepo.addTokens(1)
                MultiDrawResult(element, rarity, isFirst, totalCopies)
            }

            // Unique rarities in ascending order (commun → irréel)
            val uniqueRarities = results.map { it.rarity }.distinct().sortedBy { it.ordinal }

            // Sun squeeze then fade out
            suspendCancellableCoroutine<Unit> { cont ->
                sunBtn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(120)
                    .withEndAction {
                        sunBtn.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150)
                            .withEndAction {
                                sunBtn.animate().scaleX(1f).scaleY(1f).setDuration(100)
                                    .withEndAction {
                                        sunBtn.animate().alpha(0f).setDuration(300).start()
                                        earthMoonView.animate().alpha(0f).setDuration(300).start()
                                        glowPulseAnimator?.cancel()
                                        cont.resume(Unit)
                                    }.start()
                            }.start()
                    }.start()
            }

            // Au-delà du seuil de tirages cumulés, ne jouer que l'animation de l'élément
            // le plus rare (ordinal le plus haut). Sinon : une animation par rareté.
            // Le total est dérivé directement de la collection (copies cumulées hors
            // fusion), donc fiable rétroactivement sans compteur séparé.
            val raritiesToAnimate = if (collectionStore.getTotalGachaDraws() >= RAREST_ONLY_ANIMATION_THRESHOLD) {
                listOfNotNull(uniqueRarities.lastOrNull())
            } else {
                uniqueRarities
            }

            // One animation per rarity, lowest first
            for (rarity in raritiesToAnimate) {
                val rep = results.first { it.rarity == rarity }
                val categoryColor = getCategoryColor(rep.element.category)
                suspendCancellableCoroutine<Unit> { cont ->
                    particleView.post {
                        particleView.playEffect(rarity, categoryColor) {
                            particleView.setHoldSpeedBoost(false)
                            cont.resume(Unit)
                        }
                    }
                }
            }

            showMultiResultScreen(results)
        }
    }

    private fun showMultiResultScreen(results: List<MultiDrawResult>) {
        sunGlow.animate().alpha(0f).setDuration(300).start()

        // Group by element, sort ascending by rarity (commun first)
        val grouped = results.groupBy { it.element }
        val sorted = grouped.entries.sortedBy { (_, items) -> items.first().rarity.ordinal }

        multiResultContainer.removeAllViews()

        var row: LinearLayout? = null
        sorted.forEachIndexed { index, (element, items) ->
            if (index % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                multiResultContainer.addView(row)
            }

            val count = items.size
            val rarity = items.first().rarity
            val isFirst = items.any { it.isFirst }
            row!!.addView(inflateMiniCard(element, rarity, count, isFirst))

            // Odd last item: fill empty slot with spacer
            if (index == sorted.size - 1 && sorted.size % 2 != 0) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                }
                row!!.addView(spacer)
            }
        }

        multiResultOverlay.visibility = View.VISIBLE
        multiResultOverlay.alpha = 0f
        multiResultOverlay.animate().alpha(1f).setDuration(500).start()

        isAnimating = false
    }

    private fun inflateMiniCard(element: PeriodicElement, rarity: GachaRarity, count: Int, isFirst: Boolean): View {
        val card = layoutInflater.inflate(R.layout.gacha_mini_card, multiResultContainer, false)

        card.findViewById<TextView>(R.id.mini_number).text = "#${element.atomicNumber}"
        card.findViewById<TextView>(R.id.mini_symbol).apply {
            text = element.symbol
            setTextColor(rarity.color)
        }
        card.findViewById<TextView>(R.id.mini_name).text = element.localizedName(this)
        card.findViewById<TextView>(R.id.mini_rarity).apply {
            text = getString(rarity.nameRes).uppercase()
            setTextColor(rarity.color)
        }
        if (count > 1) {
            card.findViewById<TextView>(R.id.mini_count).apply {
                text = "×$count"
                visibility = View.VISIBLE
            }
        }

        if (isFirst) {
            val rainbow = RainbowBorderDrawable()
            card.background = rainbow
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                interpolator = null
                addUpdateListener {
                    rainbow.phase = it.animatedValue as Float
                    card.invalidate()
                }
                start()
            }.also { miniCardAnimators.add(it) }
        } else {
            card.background = GradientStrokeDrawable(rarity.color)
        }

        return card
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayTickets()
        updateBigBangButtonVisibility()
    }

    private fun updateBigBangButtonVisibility() {
        if (bigBangRepo.isUnlocked()) {
            bigBangBtn.visibility = View.VISIBLE
            return
        }
        if (elementTokenRepo.getBalance() >= BigBangRepository.UNLOCK_THRESHOLD) {
            bigBangRepo.markUnlocked()
            bigBangBtn.visibility = View.VISIBLE
        }
    }

    private fun loadAndDisplayTickets() {
        lifecycleScope.launch {
            val ticketState = ticketRepository.awardTickets(System.currentTimeMillis())
            ticketsDisplay.text = ticketState.totalTickets.toString()
        }
    }

    private fun showResultCard(element: PeriodicElement, rarity: GachaRarity, totalCopies: Int, isFirst: Boolean = false) {
        // Cacher le glow résiduel
        sunGlow.animate().alpha(0f).setDuration(300).start()

        // Remplir la card
        resultNumber.text   = "#${element.atomicNumber}"
        resultSymbol.text   = element.symbol
        resultName.text     = element.localizedName(this)
        resultMass.text     = element.atomicMass.toString()
        resultRarity.text   = getString(rarity.nameRes).uppercase()
        resultCategory.text = categoryLabel(element.category)
        resultCopies.text   = getString(R.string.gacha_result_copies_count, totalCopies)

        // Couleur de rareté
        resultSymbol.setTextColor(rarity.color)
        resultRarity.setTextColor(rarity.color)

        // Fond de la card : arc-en-ciel animé si premier tirage, sinon bordure de rareté
        if (isFirst) {
            val rainbowDrawable = RainbowBorderDrawable()
            resultCard.background = rainbowDrawable
            discoveryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                interpolator = null
                addUpdateListener {
                    rainbowDrawable.phase = it.animatedValue as Float
                    resultCard.invalidate()
                }
                start()
            }
        } else {
            resultCard.background = GradientStrokeDrawable(rarity.color)
        }

        // Apparition de la card avec overshoot
        resultCard.visibility = View.VISIBLE
        resultCard.scaleX = 0.5f
        resultCard.scaleY = 0.5f
        resultCard.alpha  = 0f
        resultCard.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()

        isAnimating = false
        isFirstDiscovery = false
    }

    private fun resetToIdle() {
        discoveryAnimator?.cancel()
        discoveryAnimator = null
        miniCardAnimators.forEach { it.cancel() }
        miniCardAnimators.clear()

        if (multiResultOverlay.visibility == View.VISIBLE) {
            multiResultOverlay.animate().alpha(0f).setDuration(300).withEndAction {
                multiResultOverlay.visibility = View.INVISIBLE
                multiResultContainer.removeAllViews()
            }.start()
        }

        resultCard.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(300).withEndAction {
            resultCard.visibility = View.INVISIBLE
            resultCard.setBackgroundResource(R.drawable.gacha_card_bg)
            particleView.stop()

            sunGlow.setBackgroundResource(R.drawable.gacha_glow_ring)
            sunBtn.animate().alpha(1f).setDuration(300).start()
            earthMoonView.animate().alpha(1f).setDuration(300).start()
            startGlowPulse()
        }.start()
    }

    private fun categoryLabel(category: String): String = when (category) {
        "nonmetal"             -> "Non-métal"
        "noble-gas"            -> "Gaz noble"
        "alkali-metal"         -> "Métal alcalin"
        "alkaline-earth-metal" -> "Métal alcalino-terreux"
        "metalloid"            -> "Métalloïde"
        "halogen"              -> "Halogène"
        "transition-metal"     -> "Métal de transition"
        "post-transition-metal"-> "Métal pauvre"
        "lanthanide"           -> "Lanthanide"
        "actinide"             -> "Actinide"
        else                   -> category
    }

    private fun getCategoryColor(category: String): Int = when (category) {
        "alkali-metal" -> getColor(R.color.category_alkali_metal)
        "alkaline-earth-metal" -> getColor(R.color.category_alkaline_earth_metal)
        "transition-metal" -> getColor(R.color.category_transition_metal)
        "post-transition-metal" -> getColor(R.color.category_post_transition_metal)
        "metalloid" -> getColor(R.color.category_metalloid)
        "nonmetal" -> getColor(R.color.category_nonmetal)
        "halogen" -> getColor(R.color.category_halogen)
        "noble-gas" -> getColor(R.color.category_noble_gas)
        "lanthanide" -> getColor(R.color.category_lanthanide)
        "actinide" -> getColor(R.color.category_actinide)
        else -> getColor(R.color.category_default)
    }

    override fun onDestroy() {
        super.onDestroy()
        glowPulseAnimator?.cancel()
        discoveryAnimator?.cancel()
        miniCardAnimators.forEach { it.cancel() }
        particleView.stop()
    }

    // Arc-en-ciel qui défile le long du bord de la card via PathMeasure
    private class RainbowBorderDrawable : android.graphics.drawable.Drawable() {

        var phase = 0f  // avance de 0 à 1 en boucle, anime la position des couleurs

        private val segmentPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = android.graphics.Paint.Cap.BUTT
        }
        private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = 0xCC0D1226.toInt()
        }

        private val borderPath = android.graphics.Path()
        private val pathMeasure = android.graphics.PathMeasure()
        private val pos = FloatArray(2)
        private val cornerRadius = 40f

        // Palette arc-en-ciel : rouge → orange → jaune → vert → cyan → bleu → violet → retour rouge
        private val rainbow = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFF8800.toInt(), 0xFFFFFF00.toInt(),
            0xFF00FF00.toInt(), 0xFF00FFFF.toInt(), 0xFF0088FF.toInt(),
            0xFF8800FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()
        )

        private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
            val r = (Color.red(c1)   + (Color.red(c2)   - Color.red(c1))   * t).toInt()
            val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t).toInt()
            val b = (Color.blue(c1)  + (Color.blue(c2)  - Color.blue(c1))  * t).toInt()
            return Color.argb(255, r, g, b)
        }

        private fun colorAt(t: Float): Int {
            val n = rainbow.size - 1
            val scaled = (t % 1f) * n
            val idx = scaled.toInt().coerceIn(0, n - 1)
            return lerpColor(rainbow[idx], rainbow[idx + 1], scaled - idx)
        }

        override fun draw(canvas: android.graphics.Canvas) {
            val b = bounds
            val sw = segmentPaint.strokeWidth / 2f
            val rect = android.graphics.RectF(b.left + sw, b.top + sw, b.right - sw, b.bottom - sw)

            // Fond de la card
            borderPath.reset()
            borderPath.addRoundRect(rect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
            canvas.drawPath(borderPath, bgPaint)

            // Mesurer le périmètre réel
            pathMeasure.setPath(borderPath, false)
            val total = pathMeasure.length
            if (total <= 0f) return

            // Décomposer en 120 segments et colorer chacun selon sa position + phase
            val segments = 120
            val segLen = total / segments

            for (i in 0 until segments) {
                val t = (i.toFloat() / segments + phase) % 1f
                segmentPaint.color = colorAt(t)

                val d0 = i * segLen
                val d1 = (d0 + segLen).coerceAtMost(total - 0.1f)

                pathMeasure.getPosTan(d0, pos, null)
                val x0 = pos[0]; val y0 = pos[1]
                pathMeasure.getPosTan(d1, pos, null)
                val x1 = pos[0]; val y1 = pos[1]

                canvas.drawLine(x0, y0, x1, y1, segmentPaint)
            }
        }

        override fun setAlpha(a: Int) { segmentPaint.alpha = a; bgPaint.alpha = a }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { segmentPaint.colorFilter = cf }
        @Deprecated("Deprecated in Java", ReplaceWith("android.graphics.PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    // Drawable simple avec bordure colorée pour la card résultat
    private class GradientStrokeDrawable(private val rarityColor: Int) : android.graphics.drawable.Drawable() {
        private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC0D1226.toInt()
            style = android.graphics.Paint.Style.FILL
        }
        private val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val cornerRadius = 40f

        override fun draw(canvas: android.graphics.Canvas) {
            val bounds = bounds
            val rect = android.graphics.RectF(bounds)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
            // Gradient border coloré
            strokePaint.shader = android.graphics.LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(rarityColor, Color.WHITE, rarityColor),
                null, android.graphics.Shader.TileMode.CLAMP
            )
            strokePaint.alpha = 180
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
        }

        override fun setAlpha(alpha: Int) { fillPaint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { fillPaint.colorFilter = cf }
        @Deprecated("Deprecated in Java", ReplaceWith("android.graphics.PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}
