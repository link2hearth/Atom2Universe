package com.Atom2Universe.app.crypto.clicker

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R

class ClickerBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // ── Bannière principale ───────────────────────────────────────────────────
    private val atomsText: TextView
    private val atomsContainer: LinearLayout
    private val apcText: TextView
    private val apsText: TextView
    private val rabbitView: RabbitSpriteView
    private val turtleView: TurtleSpriteView
    private val shopButton: Button
    private val gachaButton: Button
    private val fusionButton: Button

    // ── Sides
    private val mainRow: LinearLayout
    private val leftSide: LinearLayout
    private val rightSide: LinearLayout
    private var isVerticalMode = false

    // ── Panneau frénésies ─────────────────────────────────────────────────────
    private val frenzySubrow: View
    private val frenzyApcBox: View
    private val frenzyApcMult: TextView
    private val frenzyApcTimer: TextView
    private val frenzyApcClicks: TextView
    private val frenzyApsBox: View
    private val frenzyApsMult: TextView

    private var cachedDrawMultiplier: Int = 1

    var onShopClick: (() -> Unit)? = null
    var onGachaClick: (() -> Unit)? = null
    var onFusionClick: (() -> Unit)? = null
    var onAtomsClick: (() -> Unit)? = null
    var onAtomsLongClick: (() -> Unit)? = null

    // ── Arc-en-ciel pastel défilant sur le compteur d'atomes ─────────────────
    private var rainbowAnimator: ValueAnimator? = null
    private var rainbowShader: LinearGradient? = null
    private val rainbowMatrix = Matrix()

    // Largeur d'un cycle arc-en-ciel complet (rayons resserrés)
    private val stripeWidthDp = 128f

    // Couleurs pastel : saturation ~38 %, valeur ~98 %
    private val rainbowColors = intArrayOf(
        Color.HSVToColor(floatArrayOf(  0f, 0.38f, 0.98f)),   // rose
        Color.HSVToColor(floatArrayOf( 40f, 0.38f, 0.98f)),   // pêche
        Color.HSVToColor(floatArrayOf( 80f, 0.38f, 0.98f)),   // jaune-vert
        Color.HSVToColor(floatArrayOf(140f, 0.38f, 0.98f)),   // vert
        Color.HSVToColor(floatArrayOf(195f, 0.38f, 0.98f)),   // cyan
        Color.HSVToColor(floatArrayOf(250f, 0.38f, 0.98f)),   // bleu
        Color.HSVToColor(floatArrayOf(300f, 0.38f, 0.98f)),   // violet
        Color.HSVToColor(floatArrayOf(360f, 0.38f, 0.98f)),   // rose (bouclage)
    )

    private fun startRainbow() {
        rainbowAnimator?.cancel()
        val stripeWidthPx = stripeWidthDp * resources.displayMetrics.density
        val shader = LinearGradient(
            0f, 0f, stripeWidthPx, 0f,
            rainbowColors, null,
            Shader.TileMode.REPEAT
        ).also { rainbowShader = it }
        atomsText.paint.shader = shader

        rainbowAnimator = ValueAnimator.ofFloat(0f, stripeWidthPx).apply {
            duration = 12_000L         // défilement lent
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                rainbowMatrix.setTranslate(anim.animatedValue as Float, 0f)
                shader.setLocalMatrix(rainbowMatrix)
                atomsText.invalidate()
            }
            start()
        }
    }

    private fun stopRainbow() {
        rainbowAnimator?.cancel()
        rainbowAnimator = null
        rainbowShader = null
        atomsText.paint.shader = null
        atomsText.invalidate()
    }

    // ── Dégradé chaud (rouge/orange) défilant sur l'APC ─────────────────────
    private var apcGlowAnimator: ValueAnimator? = null
    private var apcGlowShader: LinearGradient? = null
    private val apcGlowMatrix = Matrix()

    private val apcGlowColors = intArrayOf(
        Color.HSVToColor(floatArrayOf(  0f, 0.00f, 1.00f)),  // blanc
        Color.HSVToColor(floatArrayOf( 45f, 0.35f, 1.00f)),  // jaune doux
        Color.HSVToColor(floatArrayOf( 25f, 0.65f, 0.98f)),  // orange
        Color.HSVToColor(floatArrayOf( 10f, 0.75f, 0.97f)),  // rouge-orange
        Color.HSVToColor(floatArrayOf(  0f, 0.72f, 0.96f)),  // rouge
        Color.HSVToColor(floatArrayOf( 15f, 0.60f, 0.98f)),  // orange-rouge
        Color.HSVToColor(floatArrayOf( 35f, 0.40f, 1.00f)),  // jaune-orangé
        Color.HSVToColor(floatArrayOf(  0f, 0.00f, 1.00f)),  // blanc (bouclage)
    )

    private fun startApcGlow() {
        apcGlowAnimator?.cancel()
        val stripeWidthPx = stripeWidthDp * resources.displayMetrics.density
        val shader = LinearGradient(
            0f, 0f, stripeWidthPx, 0f,
            apcGlowColors, null,
            Shader.TileMode.REPEAT
        ).also { apcGlowShader = it }
        apcText.paint.shader = shader

        apcGlowAnimator = ValueAnimator.ofFloat(0f, stripeWidthPx).apply {
            duration = 40_000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                apcGlowMatrix.setTranslate(anim.animatedValue as Float, 0f)
                shader.setLocalMatrix(apcGlowMatrix)
                apcText.invalidate()
            }
            start()
        }
    }

    private fun stopApcGlow() {
        apcGlowAnimator?.cancel()
        apcGlowAnimator = null
        apcGlowShader = null
        apcText.paint.shader = null
        apcText.invalidate()
    }

    // ── Dégradé froid (blanc/bleu/violet) défilant sur l'APS ────────────────
    private var apsGlowAnimator: ValueAnimator? = null
    private var apsGlowShader: LinearGradient? = null
    private val apsGlowMatrix = Matrix()

    private val apsGlowColors = intArrayOf(
        Color.HSVToColor(floatArrayOf(  0f, 0.00f, 1.00f)),  // blanc
        Color.HSVToColor(floatArrayOf(195f, 0.25f, 0.98f)),  // bleu ciel clair
        Color.HSVToColor(floatArrayOf(220f, 0.55f, 0.95f)),  // bleu
        Color.HSVToColor(floatArrayOf(250f, 0.50f, 0.92f)),  // bleu-indigo
        Color.HSVToColor(floatArrayOf(275f, 0.45f, 0.95f)),  // violet
        Color.HSVToColor(floatArrayOf(250f, 0.30f, 0.98f)),  // lavande
        Color.HSVToColor(floatArrayOf(195f, 0.15f, 1.00f)),  // bleuté très clair
        Color.HSVToColor(floatArrayOf(  0f, 0.00f, 1.00f)),  // blanc (bouclage)
    )

    private fun startApsGlow() {
        apsGlowAnimator?.cancel()
        val stripeWidthPx = stripeWidthDp * resources.displayMetrics.density
        val shader = LinearGradient(
            0f, 0f, stripeWidthPx, 0f,
            apsGlowColors, null,
            Shader.TileMode.REPEAT
        ).also { apsGlowShader = it }
        apsText.paint.shader = shader

        apsGlowAnimator = ValueAnimator.ofFloat(0f, stripeWidthPx).apply {
            duration = 40_000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                apsGlowMatrix.setTranslate(anim.animatedValue as Float, 0f)
                shader.setLocalMatrix(apsGlowMatrix)
                apsText.invalidate()
            }
            start()
        }
    }

    private fun stopApsGlow() {
        apsGlowAnimator?.cancel()
        apsGlowAnimator = null
        apsGlowShader = null
        apsText.paint.shader = null
        apsText.invalidate()
    }

    // ── Shop affordability shake ──────────────────────────────────────────────
    private var shopAffordable = false
    private val shakeRunnable = object : Runnable {
        override fun run() {
            if (!shopAffordable) return
            ObjectAnimator.ofFloat(shopButton, "translationX", 0f, 4f, -4f, 4f, -4f, 0f).apply {
                duration = 350
                start()
            }
            handler.postDelayed(this, 3_500L)
        }
    }

    fun setShopAffordable(affordable: Boolean) {
        if (affordable == shopAffordable) return
        shopAffordable = affordable
        shopButton.text = if (affordable) "$+" else "$"
        if (affordable) {
            handler.post(shakeRunnable)
        } else {
            handler.removeCallbacks(shakeRunnable)
            shopButton.translationX = 0f
        }
    }

    // État de la frénésie APC (pour la période de grâce post-expiry)
    private var currentFrenzy     = FrenzyUiState()
    private var apcPreviouslyActive = false
    private var apcGraceEndMs     = 0L   // timestamp de fin du fade-out (0 = pas de grâce)
    private var lastApcMult       = 1
    private var lastApcClicks     = 0

    // Position tortue (frénésie APS)
    private var lastTurtleTargetTx = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val refreshTick = object : Runnable {
        override fun run() {
            val nowMs = System.currentTimeMillis()
            refreshFrenzyDisplay(nowMs)
            scheduleRefreshIfNeeded(nowMs)
        }
    }

    // ── Constantes de mise à l'échelle ────────────────────────────────────────
    private val defaultHeightDp    = 56
    private val baseAtomsSp        = 18f
    private val baseApcApsSp       = 14f
    private val basePaddingDp      = 10f
    private val baseSpriteHeightDp = 20f
    private val baseFrenzySp       = 11f

    init {
        orientation = VERTICAL
        inflate(context, R.layout.view_clicker_banner, this)

        atomsText      = findViewById(R.id.clicker_atoms)
        atomsContainer = findViewById(R.id.clicker_atoms_container)
        apcText    = findViewById(R.id.clicker_apc)
        apsText    = findViewById(R.id.clicker_aps)
        rabbitView = findViewById(R.id.clicker_rabbit)
        turtleView = findViewById(R.id.clicker_turtle)
        shopButton = findViewById(R.id.clicker_shop_btn)
        shopButton.setOnClickListener { onShopClick?.invoke() }
        gachaButton = findViewById(R.id.clicker_gacha_btn)
        gachaButton.setOnClickListener { onGachaClick?.invoke() }
        fusionButton = findViewById(R.id.clicker_fusion_btn)
        fusionButton.setOnClickListener { onFusionClick?.invoke() }
        atomsContainer.setOnClickListener { onAtomsClick?.invoke() }
        atomsContainer.setOnLongClickListener { onAtomsLongClick?.invoke(); true }

        mainRow  = findViewById(R.id.clicker_main_row)
        leftSide = findViewById(R.id.clicker_left_side)
        rightSide = findViewById(R.id.clicker_right_side)

        frenzySubrow   = findViewById(R.id.frenzy_subrow)
        frenzyApcBox   = findViewById(R.id.frenzy_apc_box)
        frenzyApcMult  = findViewById(R.id.frenzy_apc_mult)
        frenzyApcTimer = findViewById(R.id.frenzy_apc_timer)
        frenzyApcClicks = findViewById(R.id.frenzy_apc_clicks)
        frenzyApsBox   = findViewById(R.id.frenzy_aps_box)
        frenzyApsMult  = findViewById(R.id.frenzy_aps_mult)

        rabbitView.onProgressUpdate = { progress ->
            rabbitView.translationX = if (isVerticalMode && width > 0) progress * (width / 2f) else 0f
        }
    }

    // ── Bannière principale ───────────────────────────────────────────────────

    fun update(state: ClickerGameState) {
        atomsText.text = state.atoms.toString()
        apcText.text   = state.perClick.toString()
        apsText.text   = state.perSecond.toString()
        val threshold = if (cachedDrawMultiplier == 10) 10 else 1
        gachaButton.text = if (state.gachaTickets >= threshold) "☀+" else "☀"
        fusionButton.text = if (state.isFusionAvailable) "⚛+" else "⚛"
    }

    fun refreshDrawMultiplier() {
        cachedDrawMultiplier = context.getSharedPreferences(
            com.Atom2Universe.app.crypto.gacha.GachaActivity.PREFS_NAME, Context.MODE_PRIVATE
        ).getInt(com.Atom2Universe.app.crypto.gacha.GachaActivity.KEY_DRAW_MULTIPLIER, 1)
    }

    fun onClickRegistered() = rabbitView.registerClick()

    fun onCritHit() {
        apcText.animate().cancel()
        apcText.scaleX = 1f
        apcText.scaleY = 1f
        apcText.animate()
            .scaleX(1.55f).scaleY(1.55f)
            .setDuration(80)
            .setInterpolator(android.view.animation.OvershootInterpolator(3f))
            .withEndAction {
                apcText.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }.start()
    }

    fun applyOpacity(percent: Int) { alpha = percent / 100f }

    fun applySize(heightDp: Int) {
        val density = resources.displayMetrics.density
        val scale   = heightDp / defaultHeightDp.toFloat()

        atomsText.textSize = baseAtomsSp  * scale
        apcText.textSize   = baseApcApsSp * scale
        apsText.textSize   = baseApcApsSp * scale

        val spritePx = (baseSpriteHeightDp * scale * density).toInt()
        rabbitView.layoutParams = (rabbitView.layoutParams as LayoutParams).also {
            it.height = spritePx; it.width = spritePx
        }
        turtleView.layoutParams = (turtleView.layoutParams as LayoutParams).also {
            it.height = spritePx; it.width = spritePx * 3
        }

        setPadding(0, 0, 0, 0)

        val fSp = baseFrenzySp * scale
        frenzyApcMult.textSize   = fSp
        frenzyApcTimer.textSize  = fSp
        frenzyApcClicks.textSize = fSp
        frenzyApsMult.textSize   = fSp

        val needsVertical = heightDp >= 90
        isVerticalMode = needsVertical
        val orientation = if (needsVertical) VERTICAL else HORIZONTAL
        leftSide.orientation = orientation
        rightSide.orientation = orientation

        // En mode vertical : APS (compteur) en haut, tortue en dessous
        // En mode horizontal : tortue à gauche, APS à droite (ordre XML d'origine)
        rightSide.removeView(turtleView)
        rightSide.removeView(apsText)
        if (needsVertical) {
            rightSide.addView(apsText)
            rightSide.addView(turtleView)
        } else {
            rightSide.addView(turtleView)
            rightSide.addView(apsText)
        }

        // Ajuster la gravity
        if (orientation == VERTICAL) {
            leftSide.gravity = android.view.Gravity.START or android.view.Gravity.TOP
            rightSide.gravity = android.view.Gravity.END or android.view.Gravity.TOP
        } else {
            leftSide.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            rightSide.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        }

        val clip = !needsVertical
        for (v in listOf(mainRow, leftSide, rightSide)) {
            v.clipChildren  = clip
            v.clipToPadding = clip
        }
        if (!needsVertical) {
            // Réinitialiser la position de la tortue en mode 1 ligne
            turtleView.animate().cancel()
            turtleView.translationX = 0f
            lastTurtleTargetTx = 0f
        }

        requestLayout()
    }

    // ── Frénésies ─────────────────────────────────────────────────────────────

    fun bindFrenzy(state: FrenzyUiState) {
        currentFrenzy = state
        handler.removeCallbacks(refreshTick)
        val nowMs = System.currentTimeMillis()
        refreshFrenzyDisplay(nowMs)
        scheduleRefreshIfNeeded(nowMs)
    }

    private fun scheduleRefreshIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        val s         = currentFrenzy
        val apcActive = s.apcEffectExpiries.any { it > nowMs }
        val apsActive = s.apsEffectExpiries.any { it > nowMs }
        val inGrace   = apcGraceEndMs > 0L && nowMs < apcGraceEndMs

        if (apcActive || apsActive || inGrace) {
            // Rafraîchissement rapide pendant le fade-out pour un alpha fluide
            handler.postDelayed(refreshTick, if (inGrace) 200L else 1000L)
        }
    }

    private fun refreshFrenzyDisplay(nowMs: Long = System.currentTimeMillis()) {
        val s     = currentFrenzy

        val apcFx    = s.apcEffectExpiries.filter { it > nowMs }
        val apsFx    = s.apsEffectExpiries.filter { it > nowMs }
        val apcActive = apcFx.isNotEmpty()

        // ── Détecter la fin de la frénésie APC → démarrer la période de grâce ──
        if (!apcActive && apcPreviouslyActive && apcGraceEndMs == 0L) {
            apcGraceEndMs = nowMs + 5_000L
        }
        apcPreviouslyActive = apcActive

        val inGrace = !apcActive && apcGraceEndMs > 0L && nowMs < apcGraceEndMs

        // ── Boîte APC ─────────────────────────────────────────────────────────
        when {
            apcActive -> {
                lastApcMult   = 1 shl apcFx.size          // 2^stacks
                lastApcClicks = s.apcClickCount
                apcGraceEndMs = 0L

                val secsLeft = (apcFx.max() - nowMs) / 1000f
                frenzyApcMult.text    = "×${lastApcMult}"
                frenzyApcTimer.text   = "  ${secsLeft.toInt()}s"
                frenzyApcTimer.visibility = VISIBLE
                frenzyApcClicks.text  = "  ${lastApcClicks}"
                frenzyApcBox.alpha    = 1f
                frenzyApcBox.visibility = VISIBLE
            }
            inGrace -> {
                // Fade de alpha 1→0 sur 5 secondes après expiry
                val remaining = (apcGraceEndMs - nowMs).coerceAtLeast(0L)
                val alpha     = (remaining / 5_000f).coerceIn(0f, 1f)
                frenzyApcMult.text    = "×${lastApcMult}"
                frenzyApcTimer.visibility = GONE
                frenzyApcClicks.text  = "  ${lastApcClicks}"
                frenzyApcBox.alpha    = alpha
                frenzyApcBox.visibility = VISIBLE
            }
            else -> {
                apcGraceEndMs = 0L
                frenzyApcBox.alpha    = 1f   // reset pour le prochain usage
                frenzyApcBox.visibility = INVISIBLE
            }
        }

        // ── Boîte APS ─────────────────────────────────────────────────────────
        if (apsFx.isNotEmpty()) {
            val mult = 1 shl apsFx.size
            frenzyApsMult.text      = "×${mult}"
            frenzyApsBox.visibility = VISIBLE
        } else {
            frenzyApsBox.visibility = INVISIBLE
        }

        updateTurtlePosition(apsFx.size)

        // La sous-ligne est toujours visible (contient les boutons Gacha/Shop)
    }

    // ── Tortue (frénésie APS) ─────────────────────────────────────────────────
    // apsFxCount = nombre de stacks actifs → mult = 2^stacks (x2, x4, x8, x16+)
    // Courbe quadratique : steps = 0..5 → fraction = (steps/5)² → translation vers la gauche
    // Max à x32 : -(width/6)
    private fun updateTurtlePosition(apsFxCount: Int) {
        if (!isVerticalMode || width == 0) return
        val steps = apsFxCount.coerceIn(0, 5).toFloat()   // 0=aucune, 1=×2, 2=×4, 3=×8, 4=×16, 5=×32+
        val fraction = (steps / 5f) * (steps / 5f)        // 0, 0.04, 0.16, 0.36, 0.64, 1.0
        val targetTx = -fraction * (width / 6f) * 0.8f
        if (targetTx == lastTurtleTargetTx) return
        lastTurtleTargetTx = targetTx
        turtleView.animate()
            .translationX(targetTx)
            .setDuration(700L)
            .start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startRainbow()
        startApcGlow()
        startApsGlow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(refreshTick)
        handler.removeCallbacks(shakeRunnable)
        stopRainbow()
        stopApcGlow()
        stopApsGlow()
    }
}
