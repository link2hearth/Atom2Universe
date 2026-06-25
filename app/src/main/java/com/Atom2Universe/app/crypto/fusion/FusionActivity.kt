package com.Atom2Universe.app.crypto.fusion

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.gacha.atomicNumbersOf
import com.Atom2Universe.app.periodic.PeriodicCollectionStore
import com.Atom2Universe.app.periodic.getPeriodicElements
import com.Atom2Universe.app.periodic.localizedName
import com.Atom2Universe.app.util.enableImmersiveMode
import kotlin.math.roundToInt

class FusionActivity : ThemedActivity() {

    private lateinit var fusionStore: FusionStore
    private lateinit var collectionStore: PeriodicCollectionStore
    private lateinit var cardRepository: ElementCardRepository

    private lateinit var recipesContainer: LinearLayout
    private lateinit var animOverlay: View
    private lateinit var flashView: View
    private lateinit var splatView: FusionSplatView
    private lateinit var resultLayout: View
    private lateinit var resultTitle: TextView
    private lateinit var resultDetail: TextView
    private lateinit var tapContinue: TextView
    private lateinit var confettiView: FusionConfettiView
    private lateinit var bonusApcText: TextView
    private lateinit var bonusApsText: TextView
    private lateinit var nextBonusText: TextView
    private lateinit var cardOverlay: FrameLayout
    private lateinit var cardImageView: ImageView
    private lateinit var cardTapHint: TextView
    private lateinit var btnMute: TextView

    private val soundEngine = FusionSoundEngine()
    private var isAnimating = false
    private val handler = Handler(Looper.getMainLooper())

    private var rainbowAnimator: ValueAnimator? = null
    private val rainbowMatrix = Matrix()
    private val rainbowColors = intArrayOf(
        0xFFFF0000.toInt(), 0xFFFF8800.toInt(), 0xFFFFFF00.toInt(),
        0xFF00EE00.toInt(), 0xFF00DDFF.toInt(), 0xFF0077FF.toInt(),
        0xFF9900FF.toInt(), 0xFFFF0000.toInt()
    )

    private val elementsByNumber by lazy { getPeriodicElements().associateBy { it.atomicNumber } }

    private val prefs by lazy { getSharedPreferences("fusion_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fusion)
        enableImmersiveMode()

        fusionStore     = FusionStore(this)
        collectionStore = PeriodicCollectionStore(this)
        cardRepository  = ElementCardRepository(this)

        recipesContainer = findViewById(R.id.fusion_recipes_container)
        animOverlay      = findViewById(R.id.fusion_anim_overlay)
        flashView        = findViewById(R.id.fusion_flash_view)
        splatView        = findViewById(R.id.fusion_splat_view)
        resultLayout     = findViewById(R.id.fusion_result_layout)
        resultTitle      = findViewById(R.id.fusion_result_title)
        resultDetail     = findViewById(R.id.fusion_result_detail)
        tapContinue      = findViewById(R.id.fusion_tap_continue)
        confettiView     = findViewById(R.id.fusion_confetti)
        bonusApcText     = findViewById(R.id.fusion_bonus_apc)
        bonusApsText     = findViewById(R.id.fusion_bonus_aps)
        nextBonusText    = findViewById(R.id.fusion_next_bonus)
        cardOverlay      = findViewById(R.id.fusion_card_overlay)
        cardImageView    = findViewById(R.id.fusion_card_image)
        cardTapHint      = findViewById(R.id.fusion_card_tap_hint)
        btnMute          = findViewById(R.id.fusion_btn_mute)

        findViewById<View>(R.id.fusion_back).setOnClickListener { finish() }
        animOverlay.setOnClickListener { if (!isAnimating) closeOverlay() }

        soundEngine.start()
        soundEngine.muted = prefs.getBoolean("sound_muted", false)
        updateMuteButton()

        btnMute.setOnClickListener {
            soundEngine.muted = !soundEngine.muted
            prefs.edit().putBoolean("sound_muted", soundEngine.muted).apply()
            updateMuteButton()
        }

        buildRecipeTiles()
        refreshBonusDisplay()
    }

    override fun onResume() {
        super.onResume()
        refreshAllTiles()
        refreshBonusDisplay()
    }

    // ── Mute ──────────────────────────────────────────────────────────────────

    private fun updateMuteButton() {
        btnMute.text = if (soundEngine.muted) "🔇" else "🔊"
    }

    // ── Bonus display ─────────────────────────────────────────────────────────

    private fun refreshBonusDisplay() {
        val apc = Math.round(fusionStore.getBonusMultApc() * 100).toInt()
        val aps = Math.round(fusionStore.getBonusMultAps() * 100).toInt()
        bonusApcText.text = "+${apc}% APC"
        bonusApsText.text = "+${aps}% APS"
        nextBonusText.text = if (fusionStore.nextBonusIsAps())
            getString(R.string.fusion_next_bonus_aps)
        else
            getString(R.string.fusion_next_bonus_apc)
    }

    // ── Tiles ─────────────────────────────────────────────────────────────────

    private fun buildRecipeTiles() {
        recipesContainer.removeAllViews()
        for (recipe in FusionRecipe.values()) {
            val tile = layoutInflater.inflate(R.layout.fusion_recipe_tile, recipesContainer, false)
            setupTile(tile, recipe)
            recipesContainer.addView(tile)
        }
    }

    private fun setupTile(tile: View, recipe: FusionRecipe) {
        tile.tag = recipe.id
        tile.findViewById<TextView>(R.id.tile_name).text = getString(recipe.nameRes)
        tile.findViewById<TextView>(R.id.tile_science).text = getString(recipe.scienceRes)
        tile.findViewById<TextView>(R.id.tile_game_info).text = buildGameInfoText(recipe)
        refreshTileState(tile, recipe)
        tile.findViewById<Button>(R.id.tile_fuse_btn).setOnClickListener {
            if (!isAnimating) attemptFusion(recipe)
        }
        tile.findViewById<Button>(R.id.tile_fuse_all_btn).setOnClickListener {
            if (!isAnimating) fuseAll(recipe)
        }
    }

    private fun buildGameInfoText(recipe: FusionRecipe): String {
        val inputs = recipe.inputs.joinToString(" + ") { "${it.count} ${elementSymbol(it.atomicNumber)}" }
        val output = when (val out = recipe.output) {
            is FusionOutput.Element      -> elementSymbol(out.atomicNumber)
            is FusionOutput.RandomRarity -> getString(R.string.fusion_game_info_random_output, getString(out.rarity.nameRes))
        }
        val rate = "${(recipe.baseRate * 100).roundToInt()}%"
        val bonus = getString(R.string.fusion_game_info_bonus)
        return "$inputs → $output  •  $rate  •  $bonus"
    }

    private fun elementSymbol(atomicNumber: Int): String = when (atomicNumber) {
        1 -> "H"; 2 -> "He"; 6 -> "C"; 8 -> "O"; 10 -> "Ne"; 16 -> "S"; 26 -> "Fe"
        else -> "#$atomicNumber"
    }

    private fun refreshTileState(tile: View, recipe: FusionRecipe) {
        val wins  = fusionStore.getWins(recipe)
        val tries = fusionStore.getTries(recipe)

        // Haut-droite : inventaire possédé / nécessaire
        val inventoryText = recipe.inputs.joinToString("  ") { input ->
            val have = collectionStore.getCopyCount(input.atomicNumber)
            "${elementSymbol(input.atomicNumber)} $have/${input.count}"
        }
        tile.findViewById<TextView>(R.id.tile_stats).text = inventoryText

        val parentDone = recipe.unlockParentId?.let { pid ->
            FusionRecipe.byId(pid)?.let { fusionStore.getWins(it) > 0 } ?: false
        } ?: true

        val hasEnough = recipe.inputs.all { input ->
            collectionStore.getCopyCount(input.atomicNumber) >= input.count
        }

        val fuseBtn    = tile.findViewById<Button>(R.id.tile_fuse_btn)
        val fuseAllBtn = tile.findViewById<Button>(R.id.tile_fuse_all_btn)
        val lockedText = tile.findViewById<TextView>(R.id.tile_locked)

        if (!parentDone) {
            fuseBtn.isEnabled = false;    fuseBtn.alpha = 0.25f
            fuseAllBtn.isEnabled = false; fuseAllBtn.alpha = 0.25f
            lockedText.visibility = View.VISIBLE
            lockedText.text = getString(R.string.fusion_locked)
        } else {
            fuseBtn.isEnabled = hasEnough
            fuseBtn.alpha = if (hasEnough) 1f else 0.35f
            fuseAllBtn.isEnabled = hasEnough
            fuseAllBtn.alpha = if (hasEnough) 1f else 0.35f
            // Bas-gauche : compteur victoires / essais quand débloqué
            lockedText.visibility = View.VISIBLE
            lockedText.text = getString(R.string.fusion_stats, wins, tries)
        }
    }

    private fun refreshAllTiles() {
        for (i in 0 until recipesContainer.childCount) {
            val tile     = recipesContainer.getChildAt(i)
            val recipeId = tile.tag as? String ?: continue
            val recipe   = FusionRecipe.byId(recipeId) ?: continue
            refreshTileState(tile, recipe)
        }
    }

    // ── Fusion individuelle ───────────────────────────────────────────────────

    private fun attemptFusion(recipe: FusionRecipe) {
        if (!recipe.inputs.all { collectionStore.getCopyCount(it.atomicNumber) >= it.count }) {
            Toast.makeText(this, R.string.fusion_not_enough_elements, Toast.LENGTH_SHORT).show()
            return
        }

        for (input in recipe.inputs) repeat(input.count) { collectionStore.consumeCopy(input.atomicNumber) }

        val success = Math.random() < recipe.baseRate

        val resolvedAtomicNumber: Int? = if (success) {
            val atomicNum = when (val out = recipe.output) {
                is FusionOutput.Element     -> out.atomicNumber
                is FusionOutput.RandomRarity -> {
                    val pool = atomicNumbersOf(out.rarity).filter { it !in out.exclude }
                    pool.random()
                }
            }
            collectionStore.addCopyFromFusion(atomicNum)
            atomicNum
        } else null

        val droppedCard: ElementCard? = if (success) {
            cardRepository.rollDrop(recipe.id)?.also { cardRepository.markObtained(it.atomicNumber) }
        } else null

        fusionStore.recordAttempt(recipe, success)
        startFusionAnimation(recipe, success, resolvedAtomicNumber, droppedCard)
    }

    // ── Tout fusionner (palier 1) ─────────────────────────────────────────────

    private fun fuseAll(recipe: FusionRecipe) {
        // Pour chaque input, nombre de fusions possibles = stock / count
        val maxFusions = recipe.inputs.minOf { input ->
            collectionStore.getCopyCount(input.atomicNumber) / input.count
        }

        if (maxFusions <= 0) {
            Toast.makeText(this, R.string.fusion_not_enough_elements, Toast.LENGTH_SHORT).show()
            return
        }

        var wins   = 0
        var losses = 0
        repeat(maxFusions) {
            for (input in recipe.inputs) repeat(input.count) { collectionStore.consumeCopy(input.atomicNumber) }
            val success = Math.random() < recipe.baseRate
            if (success) {
                val atomicOut = when (val out = recipe.output) {
                    is FusionOutput.Element      -> out.atomicNumber
                    is FusionOutput.RandomRarity -> atomicNumbersOf(out.rarity)
                        .filter { it !in out.exclude }.random()
                }
                collectionStore.addCopyFromFusion(atomicOut)
                wins++
            } else {
                losses++
            }
            fusionStore.recordAttempt(recipe, success)
        }

        startBatchAnimation(wins, losses)
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private fun startFusionAnimation(
        recipe: FusionRecipe, success: Boolean,
        resolvedAtomicNumber: Int? = null, droppedCard: ElementCard? = null
    ) {
        isAnimating = true
        splatView.hide()
        resultLayout.visibility = View.GONE
        confettiView.visibility = View.GONE
        confettiView.stop()
        animOverlay.visibility = View.VISIBLE
        animOverlay.alpha = 0f
        animOverlay.animate().alpha(1f).setDuration(150).withEndAction {
            runCountdown {
                if (droppedCard != null)
                    showCardOverlay(droppedCard) { showResult(recipe, success, resolvedAtomicNumber, droppedCard) }
                else
                    showResult(recipe, success, resolvedAtomicNumber, droppedCard)
            }
        }.start()
    }

    private fun startBatchAnimation(wins: Int, losses: Int) {
        isAnimating = true
        splatView.hide()
        resultLayout.visibility = View.GONE
        confettiView.visibility = View.GONE
        confettiView.stop()
        animOverlay.visibility = View.VISIBLE
        animOverlay.alpha = 0f
        animOverlay.animate().alpha(1f).setDuration(150).withEndAction {
            runCountdown { showBatchResult(wins, losses) }
        }.start()
    }

    private fun runCountdown(onDone: () -> Unit) {
        val dm      = resources.displayMetrics
        val minDim  = minOf(dm.widthPixels, dm.heightPixels).toFloat()
        val baseSeed = (System.currentTimeMillis() and 0xFFFFF).toInt()

        data class SplatStep(val color: Int, val radius: Float, val seed: Int)
        val steps = listOf(
            SplatStep(Color.parseColor("#FFFF44"), minDim * 0.15f, baseSeed),
            SplatStep(Color.parseColor("#FF8800"), minDim * 0.30f, baseSeed + 1),
            SplatStep(Color.parseColor("#FF2200"), minDim * 0.50f, baseSeed + 2)
        )

        fun runStep(index: Int) {
            if (index >= steps.size) {
                splatView.hide()
                handler.postDelayed({ onDone() }, 250L)
                return
            }
            val step = steps[index]
            flashView.setBackgroundColor(step.color)
            ValueAnimator.ofObject(ArgbEvaluator(), step.color, Color.parseColor("#08000000")).apply {
                duration = 380L
                addUpdateListener { flashView.setBackgroundColor(it.animatedValue as Int) }
                start()
            }
            soundEngine.playCountdownStep(index)
            splatView.splat(step.color, step.radius, step.seed) { runStep(index + 1) }
        }

        runStep(0)
    }

    private fun showCardOverlay(card: ElementCard, onDismiss: () -> Unit) {
        try {
            val bmp = BitmapFactory.decodeStream(assets.open(card.file))
            cardImageView.setImageBitmap(bmp)
        } catch (_: Exception) {
            onDismiss()
            return
        }
        soundEngine.playSpecialCard()

        val maxTilt = 18f
        cardImageView.cameraDistance = resources.displayMetrics.density * 8000f
        cardImageView.alpha = 0f
        cardImageView.scaleX = 0.8f
        cardImageView.scaleY = 0.8f
        cardTapHint.alpha = 0f

        cardOverlay.visibility = View.VISIBLE
        cardImageView.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420)
            .setInterpolator(OvershootInterpolator(1.2f))
            .withEndAction { cardTapHint.animate().alpha(1f).setDuration(400).start() }
            .start()

        cardImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val cx = v.width / 2f; val cy = v.height / 2f
                    v.rotationY = ((event.x - cx) / cx) * maxTilt
                    v.rotationX = -((event.y - cy) / cy) * maxTilt
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().rotationX(0f).rotationY(0f)
                        .setDuration(300).setInterpolator(OvershootInterpolator(1.5f)).start()
            }
            true
        }

        cardOverlay.setOnClickListener {
            cardOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                cardOverlay.visibility = View.GONE
                cardOverlay.alpha = 1f
                cardImageView.rotationX = 0f
                cardImageView.rotationY = 0f
                cardImageView.setOnTouchListener(null)
                cardOverlay.setOnClickListener(null)
                onDismiss()
            }.start()
        }
    }

    private fun showResult(
        recipe: FusionRecipe, success: Boolean,
        resolvedAtomicNumber: Int? = null, droppedCard: ElementCard? = null
    ) {
        resultLayout.visibility = View.VISIBLE
        resultTitle.alpha = 0f; resultDetail.alpha = 0f; tapContinue.alpha = 0f

        if (success) {
            if (droppedCard == null) soundEngine.playWin()
            resultTitle.text = getString(R.string.fusion_success)
            startRainbowText(resultTitle)

            val elem = resolvedAtomicNumber?.let { elementsByNumber[it] }
            val elementLine = if (elem != null)
                getString(R.string.fusion_result_got_element, elem.symbol, elem.localizedName(this))
            else ""
            val cardLine = if (droppedCard != null) {
                val cardElem = elementsByNumber[droppedCard.atomicNumber]
                val cardName = cardElem?.localizedName(this) ?: "#${droppedCard.atomicNumber}"
                "\n✨ " + getString(R.string.fusion_card_dropped, cardName)
            } else ""
            resultDetail.text = elementLine + cardLine
            resultDetail.setTextColor(Color.WHITE)

            confettiView.visibility = View.VISIBLE
            confettiView.launch()
        } else {
            soundEngine.playFail()
            rainbowAnimator?.cancel()
            resultTitle.text = getString(R.string.fusion_fail)
            resultTitle.setTextColor(Color.WHITE)
            resultTitle.paint.shader = null
            resultDetail.text = getString(R.string.fusion_result_consumed)
            resultDetail.setTextColor(Color.parseColor("#AAFFFFFF"))
        }

        animateResultIn()
    }

    private fun showBatchResult(wins: Int, losses: Int) {
        resultLayout.visibility = View.VISIBLE
        resultTitle.alpha = 0f; resultDetail.alpha = 0f; tapContinue.alpha = 0f

        if (wins > 0) {
            soundEngine.playWin()
            resultTitle.text = getString(R.string.fusion_success)
            startRainbowText(resultTitle)
            confettiView.visibility = View.VISIBLE
            confettiView.launch()
        } else {
            soundEngine.playFail()
            rainbowAnimator?.cancel()
            resultTitle.text = getString(R.string.fusion_fail)
            resultTitle.setTextColor(Color.WHITE)
            resultTitle.paint.shader = null
        }

        resultDetail.text = getString(R.string.fusion_batch_result, wins, losses)
        resultDetail.setTextColor(Color.WHITE)

        animateResultIn()
    }

    private fun animateResultIn() {
        resultTitle.animate().alpha(1f).setDuration(400L).start()
        resultDetail.animate().alpha(1f).setDuration(400L).setStartDelay(200L).start()
        tapContinue.animate().alpha(1f).setDuration(400L).setStartDelay(800L).withEndAction {
            isAnimating = false
            refreshAllTiles()
            refreshBonusDisplay()
        }.start()
    }

    // ── Rainbow titre ─────────────────────────────────────────────────────────

    private fun startRainbowText(tv: TextView) {
        rainbowAnimator?.cancel()
        tv.paint.shader = null
        tv.post {
            val w = tv.width.toFloat().coerceAtLeast(300f)
            val shader = LinearGradient(0f, 0f, w, 0f, rainbowColors, null, Shader.TileMode.MIRROR)
            tv.paint.shader = shader
            tv.invalidate()
            rainbowAnimator = ValueAnimator.ofFloat(0f, w).apply {
                duration = 1200L
                repeatCount = ValueAnimator.INFINITE
                repeatMode  = ValueAnimator.RESTART
                addUpdateListener { anim ->
                    rainbowMatrix.setTranslate(anim.animatedValue as Float, 0f)
                    shader.setLocalMatrix(rainbowMatrix)
                    tv.invalidate()
                }
                start()
            }
        }
    }

    // ── Fermeture overlay ─────────────────────────────────────────────────────

    private fun closeOverlay() {
        rainbowAnimator?.cancel()
        rainbowAnimator = null
        resultTitle.paint.shader = null
        splatView.hide()
        confettiView.stop()
        animOverlay.animate().alpha(0f).setDuration(250L).withEndAction {
            animOverlay.visibility = View.GONE
            confettiView.visibility = View.GONE
            resultLayout.visibility = View.GONE
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        rainbowAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
        confettiView.stop()
        soundEngine.stop()
    }
}
