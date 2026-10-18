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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class MemoryActivity : ThemedActivity() {

    companion object {
        private const val PREFS_NAME = "memory_save"
        private const val KEY_SAVE = "save_json"
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
    private lateinit var zoomButton: ImageButton
    private var boardZoomed = false
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
        zoomButton = findViewById(R.id.memory_zoom_button)
        boardZoomed = savedInstanceState?.getBoolean("board_zoomed") ?: false
        flipsText    = findViewById(R.id.memory_flips)
        timeText     = findViewById(R.id.memory_time)
        winOverlay   = findViewById(R.id.memory_win_overlay)
        winMessage   = findViewById(R.id.memory_win_message)
        loadingView  = findViewById(R.id.memory_loading)

        findViewById<ImageButton>(R.id.memory_back_button).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.memory_btn_again).setOnClickListener {
            startNewGame(game.difficulty)
        }
        zoomButton.setOnClickListener {
            boardZoomed = !boardZoomed
            resizeBoard()
        }
        boardScroll.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) resizeBoard()
        }

        buildDiffChips()

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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("board_zoomed", boardZoomed)
        super.onSaveInstanceState(outState)
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

    // ── Difficulty chips ──────────────────────────────────────────────────────────
    private val diffChipMap = mutableMapOf<MemoryDifficulty, MaterialButton>()

    private fun buildDiffChips() {
        val container = findViewById<LinearLayout>(R.id.memory_diff_row)
        val density = resources.displayMetrics.density
        for (diff in MemoryDifficulty.entries) {
            val chip = MaterialButton(
                this, null, android.R.attr.borderlessButtonStyle
            ).apply {
                text = getString(R.string.game_stat_grid, diff.cols, diff.rows)
                textSize = 12f
                minimumWidth = 0
                minWidth = 0
                setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
                insetTop = (4 * density).toInt()
                insetBottom = (4 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (48 * density).toInt()
                ).also { it.setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0) }
                setOnClickListener { startNewGame(diff) }
            }
            container.addView(chip)
            diffChipMap[diff] = chip
        }
    }

    private fun updateDiffChips() {
        val active = ColorStateList.valueOf(com.Atom2Universe.app.audio.AudioStyle.accent(this))
        val inactive = ColorStateList.valueOf(com.Atom2Universe.app.audio.AudioStyle.surface(this))
        for ((diff, chip) in diffChipMap) {
            chip.isSelected = diff == game.difficulty
            chip.backgroundTintList = if (diff == game.difficulty) active else inactive
            chip.setTextColor(if (diff == game.difficulty) com.Atom2Universe.app.audio.AudioStyle.onAccent(this)
                else com.Atom2Universe.app.audio.AudioStyle.primaryText(this))
        }
        val scroll = findViewById<HorizontalScrollView>(R.id.memory_diff_scroll)
        diffChipMap[game.difficulty]?.let { chip ->
            scroll.post {
                scroll.smoothScrollTo((chip.left + chip.width / 2 - scroll.width / 2).coerceAtLeast(0), 0)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        loadingView.visibility = if (loading) View.VISIBLE else View.GONE
        recyclerView.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        zoomButton.isEnabled = !loading
        diffChipMap.values.forEach { it.isEnabled = !loading }
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
            updateDiffChips()
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
            updateDiffChips()
            saveGame()
            if (game.isWon) showWinOverlay() else startTimer()
        }
    }

    private fun setupRecyclerView() {
        val cols = game.difficulty.cols
        recyclerView.layoutManager = object : GridLayoutManager(this, cols) {
            override fun canScrollVertically() = boardZoomed
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
        val cols = game.difficulty.cols
        val rows = game.difficulty.rows
        var slotW = totalW / cols
        var slotH = slotW * 12 / 8
        if (slotH * rows > totalH) {
            slotH = totalH / rows
            slotW = slotH * 8 / 12
        }
        if (boardZoomed) {
            // Keep images and touch targets readable on phones; pan to the other cards.
            slotW = maxOf(slotW, (64 * resources.displayMetrics.density).toInt())
            slotH = slotW * 12 / 8
        }
        val boardW = maxOf(totalW, slotW * cols)
        recyclerView.layoutParams = recyclerView.layoutParams.apply { width = boardW }
        val hPad = (boardW - slotW * cols) / 2
        val vPad = ((totalH - slotH * rows) / 2).coerceAtLeast(0)
        recyclerView.setPadding(hPad, vPad, hPad, vPad)
        currentAdapter.cardSize = slotH
        currentAdapter.notifyDataSetChanged()
        if (!boardZoomed) {
            boardScroll.scrollTo(0, 0)
            recyclerView.scrollToPosition(0)
        }
        zoomButton.isSelected = boardZoomed
        zoomButton.contentDescription = getString(
            if (boardZoomed) R.string.memory_zoom_out else R.string.memory_zoom_in
        )
        zoomButton.imageTintList = ColorStateList.valueOf(
            if (boardZoomed) com.Atom2Universe.app.audio.AudioStyle.accent(this)
            else com.Atom2Universe.app.audio.AudioStyle.primaryText(this)
        )
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

    private fun showWinOverlay() {
        winMessage.text = getString(R.string.memory_win_message, game.flips, formatTime(game.elapsedSeconds))
        winOverlay.visibility = View.VISIBLE
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
                assets.open(path).use { stream ->
                    val bytes = stream.readBytes()
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    var sample = 1
                    while (opts.outWidth / sample > CARD_TARGET_PX || opts.outHeight / sample > CARD_TARGET_PX) {
                        sample *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    bitmapCache[idx] = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)!!
                }
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
