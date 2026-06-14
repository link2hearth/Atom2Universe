package com.Atom2Universe.app.games.memory

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

class MemoryActivity : AppCompatActivity() {

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
        flipsText    = findViewById(R.id.memory_flips)
        timeText     = findViewById(R.id.memory_time)
        winOverlay   = findViewById(R.id.memory_win_overlay)
        winMessage   = findViewById(R.id.memory_win_message)
        loadingView  = findViewById(R.id.memory_loading)

        findViewById<ImageButton>(R.id.memory_back_button).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.memory_btn_again).setOnClickListener {
            confirmNewGame(game.difficulty)
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

    override fun onResume() {
        super.onResume()
        if (!game.isWon) startTimer()
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
        val dp6 = (6 * resources.displayMetrics.density).toInt()
        for (diff in MemoryDifficulty.entries) {
            val chip = MaterialButton(
                this, null, android.R.attr.borderlessButtonStyle
            ).apply {
                text = diff.label
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(dp6 / 2, 0, dp6 / 2, 0) }
                setOnClickListener { confirmNewGame(diff) }
            }
            container.addView(chip)
            diffChipMap[diff] = chip
        }
    }

    private fun confirmNewGame(diff: MemoryDifficulty) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.memory_confirm_message, diff.label))
            .setPositiveButton(R.string.memory_btn_again) { _, _ -> startNewGame(diff) }
            .setNegativeButton(R.string.memory_btn_back, null)
            .show()
    }

    private fun updateDiffChips() {
        val active   = ColorStateList.valueOf(Color.parseColor("#8B5CF6"))
        val inactive = ColorStateList.valueOf(Color.parseColor("#1F2937"))
        for ((diff, chip) in diffChipMap) {
            chip.backgroundTintList = if (diff == game.difficulty) active else inactive
        }
    }

    // ── Game lifecycle ────────────────────────────────────────────────────────────
    private fun startNewGame(diff: MemoryDifficulty) {
        stopTimer()
        handler.removeCallbacksAndMessages(null)
        winOverlay.visibility = View.GONE
        firstFlippedPos = null
        isChecking = false

        lifecycleScope.launch {
            loadingView.visibility = View.VISIBLE
            val result = withContext(Dispatchers.IO) { pickFolderAndFiles(diff) }
            val (folder, files) = result
            if (folder == null) {
                loadingView.visibility = View.GONE
                return@launch
            }
            game.newGame(diff, folder, files)
            loadBitmapsForGame()
            loadingView.visibility = View.GONE
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
        lifecycleScope.launch {
            loadingView.visibility = View.VISIBLE
            loadBitmapsForGame()
            loadingView.visibility = View.GONE
            setupRecyclerView()
            updateStats()
            updateDiffChips()
            if (game.isWon) showWinOverlay()
        }
    }

    private fun setupRecyclerView() {
        val cols = game.difficulty.cols
        val rows = game.difficulty.rows
        recyclerView.layoutManager = object : GridLayoutManager(this, cols) {
            override fun canScrollVertically() = false
            override fun canScrollHorizontally() = false
        }
        val newAdapter = MemoryAdapter(game.cards, bitmapCache) { pos -> onCardClicked(pos) }
        adapter = newAdapter
        recyclerView.adapter = newAdapter
        recyclerView.post {
            val totalW = recyclerView.width
            val totalH = recyclerView.height
            if (totalW == 0 || totalH == 0) return@post

            // Fit by width, then check if height overflows
            var slotW = totalW / cols
            var slotH = slotW * 12 / 8
            if (slotH * rows > totalH) {
                // Height is the limiting dimension — fit by height instead
                slotH = totalH / rows
                slotW = slotH * 8 / 12
            }

            // Center the grid with symmetric padding
            val hPad = (totalW - slotW * cols) / 2
            val vPad = (totalH - slotH * rows) / 2
            recyclerView.setPadding(hPad, vPad, hPad, vPad)

            newAdapter.cardSize = slotH
            newAdapter.notifyDataSetChanged()
        }
    }

    // ── Card click logic ──────────────────────────────────────────────────────────
    private fun onCardClicked(position: Int) {
        if (isChecking) return
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
        // EASY=+1, NORMAL=+2, MEDIUM=+3, PRO=+4, HARD=+5
        NeutrinoRepository(this).addBalance(NeutrinoRewards.memory(game.difficulty.ordinal))
    }

    // ── Bitmap loading ────────────────────────────────────────────────────────────
    private suspend fun loadBitmapsForGame() = withContext(Dispatchers.IO) {
        bitmapCache.clear()
        val folder = game.imageFolder
        val files = game.imageFiles
        val uniqueIndices = game.cards.map { it.imageIndex }.toSet()
        for (idx in uniqueIndices) {
            val filename = files.getOrNull(idx) ?: continue
            runCatching {
                assets.open("$ASSETS_CARTES/$folder/$filename").use { stream ->
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
    }

    private fun pickFolderAndFiles(diff: MemoryDifficulty): Pair<String?, List<String>> {
        val allFolders = assets.list(ASSETS_CARTES)?.toList() ?: return null to emptyList()
        val validFolders = allFolders.filter { folder ->
            val count = assets.list("$ASSETS_CARTES/$folder")
                ?.count { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) }
                ?: 0
            count >= diff.pairCount
        }
        if (validFolders.isEmpty()) return null to emptyList()
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
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun updateStats() {
        flipsText.text = getString(R.string.memory_flips, game.flips)
        timeText.text  = formatTime(game.elapsedSeconds)
    }

    private fun saveGame() {
        prefs.edit { putString(KEY_SAVE, game.serialize()) }
    }
}
