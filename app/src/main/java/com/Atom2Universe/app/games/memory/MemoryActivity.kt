package com.Atom2Universe.app.games.memory

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.PopupWindow
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.Atom2Universe.app.ThemedActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.showGameRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class MemoryActivity : ThemedActivity() {

    companion object {
        private const val PREFS_NAME = "memory_save"
        private const val KEY_SAVE = "save_json"
        private const val KEY_POP = "pop_cards"
        private const val ASSETS_CARTES = "Assets/Cartes"
        private const val FLIP_BACK_DELAY_MS = 900L
        private const val CARD_TARGET_PX = 280
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var flipsText: TextView
    private lateinit var timeText: TextView
    private lateinit var winOverlay: FrameLayout
    private lateinit var winMessage: TextView
    private lateinit var loadingView: View
    private lateinit var prefs: SharedPreferences
    private lateinit var boardScroll: HorizontalScrollView
    private lateinit var sizeButton: MaterialButton
    private lateinit var popLayer: FrameLayout
    private lateinit var zoomButton: ImageButton
    private var isLoading = false

    private val game = MemoryGame()
    private val handler = Handler(Looper.getMainLooper())
    private val bitmapCache = mutableMapOf<Int, Bitmap>()
    private var adapter: MemoryAdapter? = null

    private var firstFlippedPos: Int? = null
    private var isChecking = false

    private var timerStartMs = 0L
    private var timerRunning = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!timerRunning) return
            game.elapsedSeconds = (System.currentTimeMillis() - timerStartMs) / 1000
            updateStats()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_memory)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        recyclerView = findViewById(R.id.memory_recycler)
        boardScroll = findViewById(R.id.memory_board_scroll)
        popLayer = findViewById(R.id.memory_pop_layer)
        zoomButton = findViewById(R.id.memory_zoom_button)
        // Loupe : allume ou éteint l'agrandissement des cartes retournées (mémorisé).
        zoomButton.setOnClickListener {
            prefs.edit { putBoolean(KEY_POP, !prefs.getBoolean(KEY_POP, true)) }
            updateZoomButton()
        }
        updateZoomButton()
        sizeButton = findViewById(R.id.memory_size_button)
        flipsText    = findViewById(R.id.memory_flips)
        timeText     = findViewById(R.id.memory_time)
        winOverlay   = findViewById(R.id.memory_win_overlay)
        winMessage   = findViewById(R.id.memory_win_message)
        loadingView  = findViewById(R.id.memory_loading)

        findViewById<ImageButton>(R.id.memory_back_button).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.memory_btn_again).setOnClickListener {
            startNewGame(game.difficulty)
        }
        findViewById<ImageButton>(R.id.memory_info_button).setOnClickListener {
            showGameRules(R.string.memory_title, R.string.memory_rules)
        }
        boardScroll.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) resizeBoard()
        }

        sizeButton.setOnClickListener { showSizeBubble() }

        val restored = prefs.getString(KEY_SAVE, null)?.let { game.deserialize(it) } == true
        if (restored) {
            initFromSavedState()
        } else {
            startNewGame(MemoryDifficulty.EASY)
        }
    }

    override fun onPause() {
        super.onPause()
        stopTimer()
        saveGame()
    }

    override fun onResume() {
        super.onResume()
        if (!game.isWon && !isLoading) startTimer()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
        bitmapCache.clear()
        super.onDestroy()
    }

    // ── Taille : un seul bouton, la bulle propose les autres ─────────────────────
    private fun cardCountLabel(diff: MemoryDifficulty) = getString(R.string.memory_card_count, diff.pairCount * 2)

    private fun updateSizeButton() {
        sizeButton.text = cardCountLabel(game.difficulty)
    }

    /** Bulle sous le bouton : chaque taille en nombre de cartes, la taille en cours surlignée. */
    private fun showSizeBubble() {
        if (isLoading) return
        val density = resources.displayMetrics.density
        fun px(v: Int) = (v * density).toInt()
        val accent = com.Atom2Universe.app.audio.AudioStyle.accent(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(6), px(6), px(6), px(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(com.Atom2Universe.app.audio.AudioStyle.surface(this@MemoryActivity))
                cornerRadius = px(16).toFloat()
            }
        }
        // Sans prise de focus, la bulle laisse l'écran en plein écran ; une touche ailleurs la ferme.
        val popup = PopupWindow(list, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true
            elevation = px(10).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        for (diff in MemoryDifficulty.entries) {
            val current = diff == game.difficulty
            list.addView(TextView(this).apply {
                text = cardCountLabel(diff); textSize = 17f
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(px(18), 0, px(28), 0)
                setTextColor(if (current) com.Atom2Universe.app.audio.AudioStyle.onAccent(this@MemoryActivity)
                    else com.Atom2Universe.app.audio.AudioStyle.primaryText(this@MemoryActivity))
                if (current) background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(accent); cornerRadius = px(12).toFloat()
                }
                setOnClickListener { popup.dismiss(); if (!current) startNewGame(diff) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(48)))
        }
        popup.showAsDropDown(sizeButton, 0, px(4))
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        loadingView.visibility = if (loading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        sizeButton.isEnabled = !loading
    }

    // ── Game lifecycle ────────────────────────────────────────────────────────────
    private fun startNewGame(diff: MemoryDifficulty) {
        if (isLoading) return
        setLoading(true)
        stopTimer()
        handler.removeCallbacksAndMessages(null)
        winOverlay.visibility = View.GONE
        firstFlippedPos = null
        isChecking = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { pickFolderAndFiles(diff) }
            val (folder, files) = result
            game.newGame(diff, folder, files)
            loadBitmapsForGame()
            setLoading(false)
            setupRecyclerView()
            updateStats()
            updateSizeButton()
            saveGame()
            startTimer()
        }
    }

    private fun initFromSavedState() {
        firstFlippedPos = null
        isChecking = false
        setLoading(true)
        lifecycleScope.launch {
            loadBitmapsForGame()
            setLoading(false)
            setupRecyclerView()
            updateStats()
            updateSizeButton()
            saveGame()
            if (game.isWon) showWinOverlay() else startTimer()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = object : GridLayoutManager(this, game.difficulty.cols) {
            override fun canScrollVertically() = false
            override fun canScrollHorizontally() = false
        }
        val newAdapter = MemoryAdapter(game.cards, bitmapCache,
            if (game.usesSymbols) game.imageFiles else null) { pos -> onCardClicked(pos) }
        adapter = newAdapter
        recyclerView.adapter = newAdapter
        boardScroll.scrollTo(0, 0)
        recyclerView.post { resizeBoard() }
    }

    private fun resizeBoard() {
        if (isLoading) return
        val currentAdapter = adapter ?: return
        val totalW = boardScroll.width
        val totalH = boardScroll.height
        if (totalW == 0 || totalH == 0) return
        val (cols, rows) = bestArrangement(game.cards.size, totalW, totalH)
        (recyclerView.layoutManager as? GridLayoutManager)?.spanCount = cols
        val (slotW, slotH) = slotFor(cols, rows, totalW, totalH)
        val boardW = maxOf(totalW, slotW * cols)
        recyclerView.layoutParams = recyclerView.layoutParams.apply { width = boardW }
        val hPad = (boardW - slotW * cols) / 2
        val vPad = ((totalH - slotH * rows) / 2).coerceAtLeast(0)
        recyclerView.setPadding(hPad, vPad, hPad, vPad)
        currentAdapter.cardSize = slotH
        currentAdapter.notifyDataSetChanged()
        boardScroll.scrollTo(0, 0)
        recyclerView.scrollToPosition(0)
    }

    /**
     * Case d'une carte pour une grille donnée. La carte garde exactement le format des images
     * (2 : 3, 600 × 900) : jamais déformée ni recadrée ; la case ajoute la marge de 3 dp autour.
     */
    private fun slotFor(cols: Int, rows: Int, totalW: Int, totalH: Int): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        val margin = (8 * density).toInt()
        val pad = (6 * density).toInt()
        val byWidth = (totalW - 2 * margin) / cols - pad
        val byHeight = ((totalH - 2 * margin) / rows - pad) * 2 / 3
        val cardW = minOf(byWidth, byHeight).coerceAtLeast(1)
        return (cardW + pad) to (cardW * 3 / 2 + pad)
    }

    /**
     * Colonnes et rangées qui donnent les plus grandes cartes pour cet écran. La dernière rangée
     * peut rester incomplète : les cases vides sont en bas à droite.
     */
    private fun bestArrangement(count: Int, totalW: Int, totalH: Int): Pair<Int, Int> {
        var best = game.difficulty.cols to game.difficulty.rows
        var bestArea = -1L
        for (cols in 1..count) {
            val rows = (count + cols - 1) / cols
            if (cols * rows - count >= cols) continue
            val (w, h) = slotFor(cols, rows, totalW, totalH)
            val area = w.toLong() * h
            if (area > bestArea) { bestArea = area; best = cols to rows }
        }
        return best
    }

    // ── Card click logic ──────────────────────────────────────────────────────────
    private fun onCardClicked(position: Int) {
        if (isChecking || isLoading) return
        val card = game.cards[position]
        if (card.state != CardState.FACE_DOWN) return
        val first = firstFlippedPos
        if (first == position) return

        card.state = CardState.FACE_UP
        adapter?.flipCard(position)
        popCard(position, card)

        if (first == null) {
            firstFlippedPos = position
        } else {
            firstFlippedPos = null
            game.flips++
            updateStats()
            val cardA = game.cards[first]
            if (cardA.imageIndex == card.imageIndex) {
                cardA.state = CardState.MATCHED
                card.state = CardState.MATCHED
                game.matchedPairs++
                adapter?.notifyItemChanged(first)
                adapter?.notifyItemChanged(position)
                if (game.isWon) {
                    stopTimer()
                    claimWinReward()
                    saveGame()
                    handler.postDelayed({ showWinOverlay() }, 350L)
                }
            } else {
                isChecking = true
                handler.postDelayed({
                    cardA.state = CardState.FACE_DOWN
                    card.state = CardState.FACE_DOWN
                    adapter?.flipCard(first)
                    adapter?.flipCard(position)
                    isChecking = false
                }, FLIP_BACK_DELAY_MS)
            }
        }
    }

    /**
     * La carte qu'on vient de retourner grossit un instant au-dessus du plateau, puis revient à
     * sa place. C'est une copie posée sur une couche qui ne prend aucune touche : la carte voisine
     * reste cliquable pendant l'animation.
     */
    private fun updateZoomButton() {
        val on = prefs.getBoolean(KEY_POP, true)
        zoomButton.isSelected = on
        zoomButton.imageTintList = ColorStateList.valueOf(
            if (on) com.Atom2Universe.app.audio.AudioStyle.accent(this)
            else com.Atom2Universe.app.audio.AudioStyle.primaryText(this)
        )
    }

    private fun popCard(position: Int, card: MemoryCard) {
        if (!prefs.getBoolean(KEY_POP, true)) return
        val holder = recyclerView.findViewHolderForAdapterPosition(position) ?: return
        val front = holder.itemView.findViewById<View>(R.id.memory_card_front)
        val w = front.width; val h = front.height
        if (w <= 0 || h <= 0 || popLayer.width <= 0) return
        // Pas trop gros : au plus 2,6 fois, la moitié de la hauteur et le tiers de la largeur.
        val scale = minOf(2.6f, popLayer.height * 0.5f / h, popLayer.width * 0.34f / w)
        if (scale < 1.2f) return
        val at = IntArray(2); front.getLocationInWindow(at)
        val layerAt = IntArray(2); popLayer.getLocationInWindow(layerAt)
        val left = (at[0] - layerAt[0]).toFloat(); val top = (at[1] - layerAt[1]).toFloat()
        val ghost = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val symbol = if (game.usesSymbols) game.imageFiles.getOrNull(card.imageIndex) else null
            if (symbol != null) setImageDrawable(MemorySymbolDrawable(symbol,
                com.Atom2Universe.app.games.kit.KitPalette.from(this@MemoryActivity)))
            else setImageBitmap(bitmapCache[card.imageIndex] ?: return)
            isClickable = false; isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            outlineProvider = android.view.ViewOutlineProvider.BOUNDS
            elevation = 12 * resources.displayMetrics.density
            x = left; y = top; pivotX = w / 2f; pivotY = h / 2f
            alpha = 0f
        }
        popLayer.addView(ghost, FrameLayout.LayoutParams(w, h))
        // Le centre se décale juste assez pour que la carte agrandie reste dans le plateau.
        val halfW = w * scale / 2f; val halfH = h * scale / 2f
        val cx = (left + w / 2f).coerceIn(halfW, popLayer.width - halfW)
        val cy = (top + h / 2f).coerceIn(halfH, popLayer.height - halfH)
        val dx = cx - (left + w / 2f); val dy = cy - (top + h / 2f)
        ghost.animate().setStartDelay(130).setDuration(170)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .alpha(1f).scaleX(scale).scaleY(scale).translationXBy(dx).translationYBy(dy)
            .withEndAction {
                ghost.animate().setStartDelay(420).setDuration(170)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .scaleX(1f).scaleY(1f).translationXBy(-dx).translationYBy(-dy)
                    .withEndAction { popLayer.removeView(ghost) }
                    .start()
            }.start()
    }

    private fun showWinOverlay() {
        winMessage.text = getString(R.string.memory_win_message, game.flips, formatTime(game.elapsedSeconds))
        winOverlay.visibility = View.VISIBLE
    }

    /** Paye une seule fois, au moment de la victoire ; le panneau ne fait qu'afficher. */
    private fun claimWinReward() {
        if (game.rewardClaimed) return
        game.rewardClaimed = true
        // Récompense définie dans la source commune, pour toutes les tailles.
        NeutrinoRepository(this).addBalance(NeutrinoRewards.memory(game.difficulty.ordinal))
        saveRecordIfBetter()
    }

    /** Meilleur temps et meilleur nombre de coups, par difficulté (0 = jamais résolu à cette difficulté). */
    private fun saveRecordIfBetter() {
        val diff = game.difficulty
        val timeKey = "best_time_" + diff.name
        val movesKey = "best_moves_" + diff.name
        val bestTime = prefs.getInt(timeKey, 0)
        val bestMoves = prefs.getInt(movesKey, 0)
        prefs.edit {
            if (bestTime == 0 || game.elapsedSeconds < bestTime) putInt(timeKey, game.elapsedSeconds.toInt())
            if (bestMoves == 0 || game.flips < bestMoves) putInt(movesKey, game.flips)
        }
    }

    // ── Bitmap loading ────────────────────────────────────────────────────────────
    private suspend fun loadBitmapsForGame() = withContext(Dispatchers.IO) {
        bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
        bitmapCache.clear()
        if (game.usesSymbols) return@withContext
        val folder = game.imageFolder
        val files = game.imageFiles
        val uniqueIndices = game.cards.map { it.imageIndex }.toSet()
        for (idx in uniqueIndices) {
            val filename = files.getOrNull(idx) ?: continue
            runCatching {
                val path = com.Atom2Universe.app.games.cards.CardBacks.resolveLegacyPath(
                    this@MemoryActivity, "$ASSETS_CARTES/$folder/$filename"
                )
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / sample > CARD_TARGET_PX || opts.outHeight / sample > CARD_TARGET_PX) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                bitmapCache[idx] = requireNotNull(assets.open(path).use { BitmapFactory.decodeStream(it, null, decodeOpts) })
            }
        }
        if (game.cards.any { it.imageIndex !in bitmapCache }) {
            bitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
            bitmapCache.clear()
            game.useSymbols()
        }
    }

    private fun pickFolderAndFiles(diff: MemoryDifficulty): Pair<String, List<String>> {
        val allFolders = runCatching { assets.list(ASSETS_CARTES)?.toList() }.getOrNull().orEmpty()
        val validFolders = allFolders.filter { folder ->
            val count = assets.list("$ASSETS_CARTES/$folder")
                ?.count { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) }
                ?: 0
            count >= diff.pairCount
        }
        if (validFolders.isEmpty()) return MemorySymbols.FOLDER to MemorySymbols.forDifficulty(diff)
        val chosen = validFolders.random()
        val files = assets.list("$ASSETS_CARTES/$chosen")!!
            .filter { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) }
            .sorted()
        return chosen to files
    }

    // ── Timer ─────────────────────────────────────────────────────────────────────
    private fun startTimer() {
        if (timerRunning) return
        timerStartMs = System.currentTimeMillis() - game.elapsedSeconds * 1000
        timerRunning = true
        handler.post(timerRunnable)
    }

    private fun stopTimer() {
        timerRunning = false
        handler.removeCallbacks(timerRunnable)
        if (timerStartMs > 0) {
            game.elapsedSeconds = (System.currentTimeMillis() - timerStartMs) / 1000
        }
    }

    private fun formatTime(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return getString(R.string.memory_time_format, s / 60, s % 60)
    }

    private fun updateStats() {
        flipsText.text = getString(R.string.memory_flips, game.flips)
        timeText.text  = formatTime(game.elapsedSeconds)
    }

    private fun saveGame() {
        prefs.edit { putString(KEY_SAVE, game.serialize()) }
    }
}
