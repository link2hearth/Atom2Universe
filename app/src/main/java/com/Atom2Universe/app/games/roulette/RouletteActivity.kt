package com.Atom2Universe.app.games.roulette

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode
import java.nio.ByteBuffer

class RouletteActivity : ThemedActivity() {

    private val game = RouletteGame()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var neutrinoRepo: NeutrinoRepository

    private lateinit var balanceText: TextView
    private lateinit var statusText: TextView
    private lateinit var spinButton: TextView
    private lateinit var lastWinText: TextView
    private lateinit var cellViews: Array<Array<ImageView>>
    private lateinit var betButtons: List<TextView>
    private lateinit var winOverlay: FrameLayout
    private lateinit var winCard: LinearLayout
    private lateinit var winAmountText: TextView
    private lateinit var confettiView: ConfettiView

    private var balance = 0
    private var selectedBet = 1
    private var spinning = false
    private var bitmapsReady = false
    private val betLevels = listOf(1, 2, 5, 10, 25)

    private val bitmapCache = mutableMapOf<RouletteSymbol, Bitmap>()
    private var jokerGifBytes: ByteArray? = null

    private val SPIN_DURATION_MS = 2000L
    private val COLUMN_DELAY_MS  = 800L
    private val SHUFFLE_INTERVAL = 80L
    private val DRUM_ROLL_MS     = 110L

    private val spinRunners     = arrayOfNulls<Runnable>(3)
    private val stopTimeouts    = arrayOfNulls<Runnable>(3)
    private val columnAnimators = arrayOfNulls<ValueAnimator>(3)

    private val colorHighBorder = Color.parseColor("#FFD700")
    private val colorNormBorder = Color.parseColor("#3949AB")
    private val colorHighBg     = Color.parseColor("#2A2200")
    private val colorNormBg     = Color.parseColor("#1A1A2E")
    private val colorJoker      = Color.parseColor("#FFD700")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_roulette)

        neutrinoRepo = NeutrinoRepository(this)
        balance = neutrinoRepo.getBalance()

        bindViews()
        setupListeners()
        refreshBalance()
        updateBetButtons()
        showStatus(getString(R.string.roulette_status_idle))

        // Chargement des bitmaps en arrière-plan
        Thread {
            RouletteSymbol.entries.filter { !it.isJoker }.forEach { getBitmap(it) }
            jokerGifBytes = try { assets.open("Assets/Image/RainbowStar.gif").readBytes() } catch (e: Exception) { null }
            runOnUiThread {
                bitmapsReady = true
                for (r in 0..2) for (c in 0..2) applyCellSymbol(r, c, RouletteSymbol.BLACKHOLE, false)
                updateBetButtons()
            }
        }.start()
    }

    // ── Bind ─────────────────────────────────────────────────────────────────

    private fun bindViews() {
        balanceText   = findViewById(R.id.roulette_balance)
        statusText    = findViewById(R.id.roulette_status)
        spinButton    = findViewById(R.id.roulette_spin_btn)
        lastWinText   = findViewById(R.id.roulette_last_win)
        winOverlay    = findViewById(R.id.roulette_win_overlay)
        winCard       = findViewById(R.id.roulette_win_card)
        winAmountText = findViewById(R.id.roulette_win_amount)
        confettiView  = findViewById(R.id.roulette_confetti)

        val ids = arrayOf(
            arrayOf(R.id.roulette_cell_00, R.id.roulette_cell_01, R.id.roulette_cell_02),
            arrayOf(R.id.roulette_cell_10, R.id.roulette_cell_11, R.id.roulette_cell_12),
            arrayOf(R.id.roulette_cell_20, R.id.roulette_cell_21, R.id.roulette_cell_22)
        )
        cellViews = Array(3) { r -> Array(3) { c -> findViewById(ids[r][c]) } }
        for (r in 0..2) for (c in 0..2) initCell(cellViews[r][c])

        betButtons = listOf(
            R.id.roulette_bet_1, R.id.roulette_bet_2, R.id.roulette_bet_5,
            R.id.roulette_bet_10, R.id.roulette_bet_25
        ).map { findViewById(it) }
    }

    private fun initCell(iv: ImageView) {
        val bg = GradientDrawable()
        bg.cornerRadius = dpF(12f)
        bg.setColor(colorNormBg)
        bg.setStroke(dpI(1f), colorNormBorder)
        iv.background = bg
        iv.outlineProvider = ViewOutlineProvider.BACKGROUND
        iv.clipToOutline = true
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.roulette_back).setOnClickListener { finish() }
        spinButton.setOnClickListener { if (!spinning && bitmapsReady) onSpinClicked() }
        winOverlay.setOnClickListener { dismissWinOverlay() }

        betButtons.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                if (!spinning) { selectedBet = betLevels[i]; updateBetButtons() }
            }
        }
    }

    // ── Mise ─────────────────────────────────────────────────────────────────

    private fun updateBetButtons() {
        betButtons.forEachIndexed { i, btn ->
            val level = betLevels[i]
            val isSelected = level == selectedBet
            val canAfford  = level <= balance
            btn.isEnabled = !spinning && bitmapsReady && canAfford
            val bg = btn.background as? GradientDrawable ?: GradientDrawable().also { btn.background = it }
            bg.cornerRadius = dpF(10f)
            bg.setStroke(dpI(2f), if (isSelected) colorHighBorder else Color.parseColor("#5C6BC0"))
            bg.setColor(if (isSelected) Color.parseColor("#1A1A4A") else Color.parseColor("#0D0D2E"))
            btn.setTextColor(when {
                !canAfford -> Color.parseColor("#555588")
                isSelected -> colorJoker
                else       -> Color.parseColor("#E0E0E0")
            })
        }
        val canSpin = !spinning && bitmapsReady && balance >= selectedBet
        spinButton.isEnabled = canSpin
        spinButton.alpha = if (canSpin) 1f else 0.5f
    }

    // ── Spin ──────────────────────────────────────────────────────────────────

    private fun onSpinClicked() {
        if (balance < selectedBet) { showStatus(getString(R.string.roulette_status_no_funds)); return }
        balance -= selectedBet
        neutrinoRepo.setBalance(balance)
        refreshBalance()

        spinning = true
        updateBetButtons()
        clearHighlights()
        lastWinText.text = ""
        showStatus(getString(R.string.roulette_status_spinning))

        game.spin()
        for (col in 0..2) startColumnSpin(col)
        for (col in 0..2) {
            val delay = SPIN_DURATION_MS + col * COLUMN_DELAY_MS
            val r = Runnable { stopColumn(col) }
            stopTimeouts[col] = r
            handler.postDelayed(r, delay)
        }
    }

    private fun startColumnSpin(col: Int) {
        val runner = object : Runnable {
            override fun run() {
                if (spinning) {
                    for (row in 0..2) applySymbolToView(cellViews[row][col], RouletteSymbol.random())
                    handler.postDelayed(this, SHUFFLE_INTERVAL)
                }
            }
        }
        spinRunners[col] = runner
        handler.post(runner)

        val anim = ValueAnimator.ofFloat(1f, 0.06f, 1f).apply {
            duration = DRUM_ROLL_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val s = va.animatedValue as Float
                for (row in 0..2) cellViews[row][col].scaleY = s
            }
        }
        columnAnimators[col] = anim
        anim.start()
    }

    private fun stopColumn(col: Int) {
        handler.removeCallbacks(spinRunners[col] ?: return)
        spinRunners[col] = null
        columnAnimators[col]?.cancel()
        columnAnimators[col] = null

        for (row in 0..2) {
            cellViews[row][col].scaleY = 1f
            applySymbolToView(cellViews[row][col], game.grid[row][col])
            applyCellBorder(cellViews[row][col], false, isBlackhole = game.grid[row][col].isBlackhole)
        }

        // Rebond d'arrêt
        ValueAnimator.ofFloat(0.82f, 1.10f, 0.96f, 1.02f, 1f).apply {
            duration = 320L
            interpolator = OvershootInterpolator(1.8f)
            addUpdateListener { va ->
                val s = va.animatedValue as Float
                for (row in 0..2) cellViews[row][col].scaleY = s
            }
            start()
        }

        if (col == 2) handler.postDelayed({ finalize() }, 450L)
    }

    // ── Résultat ──────────────────────────────────────────────────────────────

    private fun finalize() {
        val (wins, totalMult) = game.evaluate()
        val winCells = wins.flatMap { it.cells }.toSet()
        for (r in 0..2) for (c in 0..2) {
            applyCellSymbol(r, c, game.grid[r][c], Pair(r, c) in winCells)
        }

        showStatus(buildResultStatus(wins, totalMult))

        if (totalMult > 0) {
            val gain = selectedBet * totalMult
            balance += gain
            neutrinoRepo.setBalance(balance)
            refreshBalance()
            lastWinText.text = getString(R.string.roulette_last_win, selectedBet, totalMult, gain)
            handler.postDelayed({ showWinOverlay(gain, totalMult, wins.size) }, 400L)
        } else {
            spinning = false
            updateBetButtons()
        }
    }

    // ── Overlay victoire ──────────────────────────────────────────────────────

    private fun showWinOverlay(gain: Int, multiplier: Int, winCount: Int) {
        winAmountText.text = "+$gain ⚛\n×$multiplier"
        confettiView.visibility = View.VISIBLE
        confettiView.launch(multiplier, winCount)

        winOverlay.alpha = 0f
        winOverlay.visibility = View.VISIBLE
        winOverlay.animate().alpha(1f).setDuration(350L).start()

        winCard.scaleX = 0.3f
        winCard.scaleY = 0.3f
        winCard.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(500L)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()
    }

    private fun dismissWinOverlay() {
        confettiView.stop()
        confettiView.visibility = View.GONE
        winOverlay.animate().alpha(0f).setDuration(200L).withEndAction {
            winOverlay.visibility = View.GONE
        }.start()
        spinning = false
        updateBetButtons()
    }

    private fun buildResultStatus(wins: List<RouletteWin>, totalMult: Int): String {
        if (wins.isEmpty()) return getString(R.string.roulette_status_lose)
        if (wins.size == 1) {
            val w = wins[0]
            return when (w.type) {
                WinType.JOKER_LINE -> getString(R.string.roulette_win_joker_line, w.multiplier)
                WinType.SYMBOL_MATCH -> {
                    val label = w.cells.firstOrNull { (r, c) -> !game.grid[r][c].isJoker }
                        ?.let { (r, c) -> game.grid[r][c].displayName } ?: ""
                    getString(R.string.roulette_win_symbol, label, w.multiplier)
                }
                WinType.JOKER_WILD -> {
                    val label = w.cells.firstOrNull { (r, c) -> !game.grid[r][c].isJoker }
                        ?.let { (r, c) -> game.grid[r][c].displayName } ?: ""
                    getString(R.string.roulette_win_joker_wild, label, w.multiplier)
                }
            }
        }
        return getString(R.string.roulette_win_multiple, totalMult)
    }

    // ── Bitmap ────────────────────────────────────────────────────────────────

    private fun getBitmap(symbol: RouletteSymbol): Bitmap =
        bitmapCache.getOrPut(symbol) { loadBitmap(symbol) }

    private fun loadBitmap(symbol: RouletteSymbol): Bitmap {
        val path = symbol.assetPath ?: return createFallbackBitmap()
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            opts.inSampleSize = computeSampleSize(opts, 512, 512)
            opts.inJustDecodeBounds = false
            assets.open(path).use { BitmapFactory.decodeStream(it, null, opts)!! }
        } catch (e: Exception) {
            createFallbackBitmap()
        }
    }

    private fun computeSampleSize(opts: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        var size = 1
        val h = opts.outHeight; val w = opts.outWidth
        if (h > reqH || w > reqW) {
            while ((h / (size * 2) >= reqH) && (w / (size * 2) >= reqW)) size *= 2
        }
        return size
    }

    private fun createFallbackBitmap(): Bitmap =
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.DKGRAY) }

    // ── Cellules ─────────────────────────────────────────────────────────────

    private fun applyCellSymbol(row: Int, col: Int, symbol: RouletteSymbol, highlight: Boolean) {
        applySymbolToView(cellViews[row][col], symbol)
        applyCellBorder(cellViews[row][col], highlight, isBlackhole = symbol.isBlackhole)
    }

    private fun applySymbolToView(iv: ImageView, symbol: RouletteSymbol) {
        val isBlackhole = symbol.isBlackhole
        // Saturne et trou noir sont plus larges que hauts : padding généreux
        val isWide = isBlackhole || symbol == RouletteSymbol.SATURN
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        val pad = when {
            symbol.isJoker -> 0
            isWide         -> dpI(10f)
            else           -> dpI(8f)
        }
        iv.setPadding(pad, pad, pad, pad)
        if (symbol.isJoker) applyJokerGif(iv) else iv.setImageBitmap(getBitmap(symbol))
        val bg = iv.background as? GradientDrawable
        if (bg != null) bg.setColor(if (isBlackhole) Color.BLACK else colorNormBg)
    }

    private fun applyJokerGif(iv: ImageView) {
        val bytes = jokerGifBytes
        if (bytes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                val drawable = ImageDecoder.decodeDrawable(source)
                iv.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
                return
            } catch (_: Exception) {}
        }
        // Fallback API 26-27 : première frame statique
        bytes?.let { iv.setImageBitmap(BitmapFactory.decodeByteArray(it, 0, it.size)) }
            ?: iv.setImageBitmap(createFallbackBitmap())
    }

    private fun applyCellBorder(iv: ImageView, highlight: Boolean, isBlackhole: Boolean = false) {
        val bg = iv.background as? GradientDrawable ?: GradientDrawable().also {
            it.cornerRadius = dpF(12f); iv.background = it
            iv.outlineProvider = ViewOutlineProvider.BACKGROUND; iv.clipToOutline = true
        }
        if (highlight) {
            bg.setColor(if (isBlackhole) Color.BLACK else colorHighBg)
            bg.setStroke(dpI(3f), colorHighBorder)
        } else {
            bg.setColor(if (isBlackhole) Color.BLACK else colorNormBg)
            bg.setStroke(dpI(1f), if (isBlackhole) Color.BLACK else colorNormBorder)
        }
    }

    private fun clearHighlights() {
        for (r in 0..2) for (c in 0..2) applyCellBorder(cellViews[r][c], false)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun refreshBalance() { balanceText.text = getString(R.string.roulette_balance, balance) }
    private fun showStatus(msg: String) { statusText.text = msg }
    private fun dpF(v: Float) = v * resources.displayMetrics.density
    private fun dpI(v: Float) = dpF(v).toInt()

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        neutrinoRepo.setBalance(balance)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
        bitmapCache.clear()
    }
}
