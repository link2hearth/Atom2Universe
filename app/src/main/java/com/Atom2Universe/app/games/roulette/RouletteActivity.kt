package com.Atom2Universe.app.games.roulette

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode

class RouletteActivity : ThemedActivity() {

    private val game = RouletteGame()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var neutrinoRepo: NeutrinoRepository

    // Views
    private lateinit var balanceText: TextView
    private lateinit var statusText: TextView
    private lateinit var spinButton: TextView
    private lateinit var lastWinText: TextView
    private lateinit var cellViews: Array<Array<TextView>>   // [row][col]
    private lateinit var betButtons: List<TextView>

    // State
    private var balance = 0
    private var selectedBet = 1
    private var spinning = false
    private val betLevels = listOf(1, 2, 5, 10, 25)

    // Animation: colonnes s'arrêtent dans l'ordre
    private val SPIN_DURATION_MS = 2200L
    private val COLUMN_DELAY_MS  = 900L
    private val SHUFFLE_INTERVAL = 80L

    private val spinRunners = arrayOfNulls<Runnable>(3)
    private val stopTimeouts = arrayOfNulls<Runnable>(3)

    // Couleurs
    private val colorRed    = Color.parseColor("#FF5252")
    private val colorBlack  = Color.parseColor("#E0E0E0")
    private val colorJoker  = Color.parseColor("#FFD700")
    private val colorVoid   = Color.parseColor("#616161")
    private val colorHighBg = Color.parseColor("#2A2200")
    private val colorNormBg = Color.parseColor("#1A1A2E")
    private val colorHighBorder = Color.parseColor("#FFD700")
    private val colorNormBorder = Color.parseColor("#3949AB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_roulette)

        neutrinoRepo = NeutrinoRepository(this)
        balance = neutrinoRepo.getWidgetBalance()

        bindViews()
        setupListeners()
        refreshBalance()
        updateBetButtons()
        showStatus(getString(R.string.roulette_status_idle))
    }

    private fun bindViews() {
        balanceText  = findViewById(R.id.roulette_balance)
        statusText   = findViewById(R.id.roulette_status)
        spinButton   = findViewById(R.id.roulette_spin_btn)
        lastWinText  = findViewById(R.id.roulette_last_win)

        // Grille 3×3
        val ids = arrayOf(
            arrayOf(R.id.roulette_cell_00, R.id.roulette_cell_01, R.id.roulette_cell_02),
            arrayOf(R.id.roulette_cell_10, R.id.roulette_cell_11, R.id.roulette_cell_12),
            arrayOf(R.id.roulette_cell_20, R.id.roulette_cell_21, R.id.roulette_cell_22)
        )
        cellViews = Array(3) { r -> Array(3) { c -> findViewById(ids[r][c]) } }
        for (r in 0..2) for (c in 0..2) applySymbol(r, c, RouletteSymbol.VOID, false)

        // Boutons de mise
        betButtons = listOf(
            R.id.roulette_bet_1, R.id.roulette_bet_2, R.id.roulette_bet_5,
            R.id.roulette_bet_10, R.id.roulette_bet_25
        ).map { findViewById(it) }
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.roulette_back).setOnClickListener { finish() }
        spinButton.setOnClickListener { if (!spinning) onSpinClicked() }

        betButtons.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                if (!spinning) {
                    selectedBet = betLevels[i]
                    updateBetButtons()
                }
            }
        }
    }

    // ── Mise ─────────────────────────────────────────────────────────────────────
    private fun updateBetButtons() {
        betButtons.forEachIndexed { i, btn ->
            val level = betLevels[i]
            val isSelected = level == selectedBet
            val canAfford  = level <= balance
            btn.isEnabled = !spinning && canAfford
            val bg = btn.background as? GradientDrawable ?: GradientDrawable().also { btn.background = it }
            bg.cornerRadius = dpF(10f)
            bg.setStroke(dpI(2f), if (isSelected) colorHighBorder else Color.parseColor("#5C6BC0"))
            bg.setColor(if (isSelected) Color.parseColor("#1A1A4A") else Color.parseColor("#0D0D2E"))
            btn.setTextColor(when {
                !canAfford -> Color.parseColor("#555588")
                isSelected -> colorJoker
                else       -> colorBlack
            })
        }
        spinButton.isEnabled = !spinning && balance >= selectedBet
        spinButton.alpha = if (!spinning && balance >= selectedBet) 1f else 0.5f
    }

    // ── Spin ──────────────────────────────────────────────────────────────────────
    private fun onSpinClicked() {
        if (balance < selectedBet) {
            showStatus(getString(R.string.roulette_status_no_funds))
            return
        }
        // Débiter la mise
        balance -= selectedBet
        neutrinoRepo.addDebit(selectedBet)
        neutrinoRepo.setWidgetBalance(balance)
        refreshBalance()

        spinning = true
        updateBetButtons()
        clearHighlights()
        lastWinText.text = ""
        showStatus(getString(R.string.roulette_status_spinning))

        game.spin()   // résultat final déjà tiré

        // Lancer les rotations de colonne
        for (col in 0..2) startColumnSpin(col)

        // Arrêter les colonnes une par une
        for (col in 0..2) {
            val delay = SPIN_DURATION_MS + col * COLUMN_DELAY_MS
            val r = Runnable { stopColumn(col) }
            stopTimeouts[col] = r
            handler.postDelayed(r, delay)
        }
    }

    private fun startColumnSpin(col: Int) {
        val r = object : Runnable {
            override fun run() {
                if (spinning) {
                    for (row in 0..2) applySymbol(row, col, RouletteSymbol.random(), false)
                    handler.postDelayed(this, SHUFFLE_INTERVAL)
                }
            }
        }
        spinRunners[col] = r
        handler.post(r)
    }

    private fun stopColumn(col: Int) {
        handler.removeCallbacks(spinRunners[col] ?: return)
        spinRunners[col] = null
        // Afficher le résultat final de cette colonne
        for (row in 0..2) applySymbol(row, col, game.grid[row][col], false)

        if (col == 2) handler.postDelayed({ finalize() }, 300L)
    }

    private fun finalize() {
        val (wins, totalMult) = game.evaluate()

        // Appliquer le highlight sur les cellules gagnantes
        val winCells = wins.flatMap { it.cells }.toSet()
        for (r in 0..2) for (c in 0..2) {
            applySymbol(r, c, game.grid[r][c], Pair(r, c) in winCells)
        }

        // Créditer les gains
        if (totalMult > 0) {
            val gain = selectedBet * totalMult
            balance += gain
            neutrinoRepo.addPending(gain)
            neutrinoRepo.setWidgetBalance(balance)
            refreshBalance()
            lastWinText.text = getString(R.string.roulette_last_win, selectedBet, totalMult, gain)
        }

        showStatus(buildResultStatus(wins, totalMult))
        spinning = false
        updateBetButtons()
    }

    private fun buildResultStatus(wins: List<RouletteWin>, totalMult: Int): String {
        if (wins.isEmpty()) return getString(R.string.roulette_status_lose)
        if (wins.size == 1) {
            val w = wins[0]
            return when (w.type) {
                WinType.JOKER_ROW      -> getString(R.string.roulette_win_joker, w.multiplier)
                WinType.SUIT_DIAGONAL  -> getString(R.string.roulette_win_suit_diag, symbolLabel(w, game), w.multiplier)
                WinType.COLOR_DIAGONAL -> getString(R.string.roulette_win_color_diag, colorLabel(w, game), w.multiplier)
                WinType.SUIT_LINE      -> getString(R.string.roulette_win_suit_line, symbolLabel(w, game), w.multiplier)
                WinType.COLOR_LINE     -> getString(R.string.roulette_win_color_line, colorLabel(w, game), w.multiplier)
            }
        }
        return getString(R.string.roulette_win_multiple, totalMult)
    }

    private fun symbolLabel(win: RouletteWin, g: RouletteGame): String {
        val cell = win.cells.firstOrNull() ?: return ""
        return g.grid[cell.first][cell.second].label
    }

    private fun colorLabel(win: RouletteWin, g: RouletteGame): String {
        val cell = win.cells.firstOrNull { (r, c) -> !g.grid[r][c].isJoker } ?: return ""
        return when (g.grid[cell.first][cell.second].colorType) {
            RouletteSymbol.ColorType.RED   -> getString(R.string.roulette_color_red)
            RouletteSymbol.ColorType.BLACK -> getString(R.string.roulette_color_black)
            else -> ""
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────────
    private fun applySymbol(row: Int, col: Int, symbol: RouletteSymbol, highlight: Boolean) {
        val tv = cellViews[row][col]
        tv.text = symbol.label
        tv.setTextColor(when (symbol.colorType) {
            RouletteSymbol.ColorType.RED   -> colorRed
            RouletteSymbol.ColorType.BLACK -> colorBlack
            RouletteSymbol.ColorType.JOKER -> colorJoker
            RouletteSymbol.ColorType.VOID  -> colorVoid
        })
        val bg = tv.background as? GradientDrawable ?: GradientDrawable().also { tv.background = it }
        bg.cornerRadius = dpF(10f)
        if (highlight) {
            bg.setColor(colorHighBg)
            bg.setStroke(dpI(3f), colorHighBorder)
        } else {
            bg.setColor(colorNormBg)
            bg.setStroke(dpI(1f), colorNormBorder)
        }
    }

    private fun clearHighlights() {
        for (r in 0..2) for (c in 0..2) applySymbol(r, c, game.grid[r][c], false)
    }

    private fun refreshBalance() {
        balanceText.text = getString(R.string.roulette_balance, balance)
    }

    private fun showStatus(msg: String) {
        statusText.text = msg
    }

    private fun dpF(v: Float) = v * resources.displayMetrics.density
    private fun dpI(v: Float) = dpF(v).toInt()

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        neutrinoRepo.setWidgetBalance(balance)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
