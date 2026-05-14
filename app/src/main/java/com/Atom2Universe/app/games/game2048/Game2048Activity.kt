package com.Atom2Universe.app.games.game2048

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.util.enableImmersiveMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Activité 2048 Quantum.
 * Portée depuis scripts/arcade/quantum-2048.js
 *
 * - Grilles 3×3 à 6×6
 * - Objectif configurable (ex : atteindre 256 en 4×4)
 * - Compteur d'univers parallèles (incrémenté à chaque victoire ou défaite)
 * - Mode Quantique 🐱 : objectifs impures + coups joker (fusion de n'importe quelle paire)
 */
class Game2048Activity : AppCompatActivity(), Game2048View.SwipeListener {

    private val GRID_SIZES = listOf(3, 4, 5, 6)

    // Objectifs classiques par taille
    private val TARGET_POOLS = mapOf(
        3 to listOf(64, 128, 256),
        4 to listOf(256, 1024, 2048),
        5 to listOf(512, 2048, 4096),
        6 to listOf(1024, 4096, 8192)
    )

    // Objectifs impures par taille : Facile / Normal / Difficile
    private val QUANTUM_TARGET_POOLS = mapOf(
        4 to listOf(192, 448, 960),
        5 to listOf(384, 896, 3840),
        6 to listOf(1536, 3584, 7680)
    )

    // Nombre de jokers par objectif quantique
    private val QUANTUM_JOKERS = mapOf(
        192 to 3,  384 to 3,  1536 to 3,   // Facile
        448 to 6,  896 to 6,  3584 to 6,   // Normal
        960 to 4,  3840 to 4, 7680 to 4    // Difficile
    )

    private lateinit var boardView: Game2048View
    private lateinit var scoreText: TextView
    private lateinit var bestText: TextView
    private lateinit var universesText: TextView
    private lateinit var statusText: TextView
    private lateinit var sizeSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var newButton: Button
    private lateinit var quantumSwitch: SwitchCompat
    private lateinit var jokerButton: Button

    private val game = Game2048Logic(4)
    private var bestScore = 0
    private var parallelUniverses = 0
    private var ignoreSpinnerChange = false
    private var quantumMode = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_game2048)

        initViews()
        loadSave()
    }

    private fun initViews() {
        boardView = findViewById(R.id.game2048_view)
        scoreText = findViewById(R.id.game2048_score)
        bestText = findViewById(R.id.game2048_best)
        universesText = findViewById(R.id.game2048_universes)
        statusText = findViewById(R.id.game2048_status)
        sizeSpinner = findViewById(R.id.game2048_size_spinner)
        targetSpinner = findViewById(R.id.game2048_target_spinner)
        newButton = findViewById(R.id.game2048_new_button)
        quantumSwitch = findViewById(R.id.game2048_quantum_switch)
        jokerButton = findViewById(R.id.game2048_joker_button)

        boardView.swipeListener = this
        boardView.game = game

        setupSizeSpinner()

        quantumSwitch.setOnCheckedChangeListener { _, checked ->
            quantumMode = checked
            // Passer en 4×4 minimum si on active le mode quantique sur une 3×3
            if (quantumMode && game.size == 3) {
                ignoreSpinnerChange = true
                sizeSpinner.setSelection(GRID_SIZES.indexOf(4))
                ignoreSpinnerChange = false
            }
            val size = if (quantumMode && game.size == 3) 4 else game.size
            updateTargetSpinner(size)
            jokerButton.visibility = if (quantumMode) View.VISIBLE else View.GONE
            game.nextMoveIsJoker = false
            refreshJokerButton()
        }

        jokerButton.setOnClickListener {
            if (game.jokers <= 0) return@setOnClickListener
            game.nextMoveIsJoker = !game.nextMoveIsJoker
            refreshJokerButton()
        }

        newButton.setOnClickListener { confirmNewGame() }
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
    }

    private fun setupSizeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            GRID_SIZES.map { "${it}×${it}" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sizeSpinner.adapter = adapter
        sizeSpinner.setSelection(GRID_SIZES.indexOf(game.size).coerceAtLeast(0))

        sizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (ignoreSpinnerChange) return
                val newSize = GRID_SIZES[pos]
                // Mode quantique impossible sur 3×3 → désactiver
                if (quantumMode && newSize == 3) {
                    ignoreSpinnerChange = true
                    quantumSwitch.isChecked = false
                    quantumMode = false
                    jokerButton.visibility = View.GONE
                    ignoreSpinnerChange = false
                }
                if (newSize != game.size) {
                    updateTargetSpinner(newSize)
                    val pool = currentPool(newSize)
                    startNewGame(newSize, pool.last(), jokers = currentJokers(pool.last()))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun currentPool(size: Int): List<Int> =
        if (quantumMode) QUANTUM_TARGET_POOLS[size] ?: listOf(192)
        else TARGET_POOLS[size] ?: listOf(256)

    private fun currentJokers(qTarget: Int): Int =
        if (quantumMode) QUANTUM_JOKERS[qTarget] ?: 3 else 0

    private fun updateTargetSpinner(size: Int) {
        val pool = currentPool(size)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            pool.map { it.toString() })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        ignoreSpinnerChange = true
        targetSpinner.adapter = adapter
        val currentTarget = if (quantumMode) game.quantumTarget else game.target
        val targetIdx = pool.indexOf(currentTarget).coerceAtLeast(0)
        targetSpinner.setSelection(targetIdx)
        ignoreSpinnerChange = false

        targetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (ignoreSpinnerChange) return
                val newTarget = pool[pos]
                val effectiveCurrent = if (quantumMode) game.quantumTarget else game.target
                if (newTarget != effectiveCurrent) {
                    startNewGame(game.size, newTarget, jokers = currentJokers(newTarget))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun confirmNewGame() {
        if (game.moves > 0 && !game.gameOver) {
            AlertDialog.Builder(this)
                .setTitle(R.string.game2048_confirm_title)
                .setMessage(R.string.game2048_confirm_message)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    val qTarget = if (quantumMode) game.quantumTarget else 0
                    startNewGame(game.size, game.target, qTarget, currentJokers(qTarget))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            val qTarget = if (quantumMode) game.quantumTarget else 0
            startNewGame(game.size, game.target, qTarget, currentJokers(qTarget))
        }
    }

    private fun startNewGame(
        size: Int,
        target: Int,
        qTarget: Int = if (quantumMode) game.quantumTarget else 0,
        jokers: Int = 0
    ) {
        ignoreSpinnerChange = true
        val sizeIdx = GRID_SIZES.indexOf(size).coerceAtLeast(0)
        sizeSpinner.setSelection(sizeIdx)
        updateTargetSpinnerSelection(size, target)
        ignoreSpinnerChange = false

        if (quantumMode) {
            game.newGame(size, target, true, target, jokers)
        } else {
            game.newGame(size, target)
        }

        boardView.game = game
        boardView.refresh()
        updateDisplays()
        refreshJokerButton()
        statusText.text = getString(R.string.game2048_status_play)
        saveCurrent()
    }

    private fun updateTargetSpinnerSelection(size: Int, target: Int) {
        val pool = currentPool(size)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            pool.map { it.toString() })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        targetSpinner.adapter = adapter
        val idx = pool.indexOf(target).coerceAtLeast(0)
        targetSpinner.setSelection(idx)

        targetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (ignoreSpinnerChange) return
                val newTarget = pool[pos]
                val effectiveCurrent = if (quantumMode) game.quantumTarget else game.target
                if (newTarget != effectiveCurrent) {
                    startNewGame(game.size, newTarget, jokers = currentJokers(newTarget))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── Affichage du bouton joker ─────────────────────────────────────────────

    private fun refreshJokerButton() {
        if (!quantumMode) {
            jokerButton.visibility = View.GONE
            return
        }
        jokerButton.visibility = View.VISIBLE
        if (game.nextMoveIsJoker) {
            jokerButton.text = getString(R.string.game2048_joker_active)
            jokerButton.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#7C3AED"))
        } else {
            jokerButton.text = "🐱 ×${game.jokers}"
            jokerButton.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1E293B"))
        }
    }

    // === SwipeListener ===

    override fun onSwipe(direction: Game2048Logic.Direction) {
        if (game.gameOver) return

        val moved = game.move(direction)
        if (!moved) return

        if (game.score > bestScore) {
            bestScore = game.score
            saveBestScore()
        }

        boardView.refresh()
        updateDisplays()
        refreshJokerButton()
        saveCurrent()

        when {
            game.hasWon && !game.gameOver -> onWin()
            game.gameOver -> onGameOver()
        }
    }

    private fun onWin() {
        parallelUniverses++
        saveUniverses()
        updateDisplays()
        val winTarget = if (quantumMode) game.quantumTarget else game.target
        statusText.text = getString(R.string.game2048_status_win, winTarget)

        NeutrinoRepository(this).addPending(5)

        boardView.postDelayed({
            val qTarget = if (quantumMode) game.quantumTarget else 0
            startNewGame(game.size, game.target, qTarget, currentJokers(qTarget))
        }, 1200)
    }

    private fun onGameOver() {
        parallelUniverses++
        saveUniverses()
        updateDisplays()
        statusText.text = getString(R.string.game2048_status_over, game.bestTile)

        boardView.postDelayed({
            val qTarget = if (quantumMode) game.quantumTarget else 0
            startNewGame(game.size, game.target, qTarget, currentJokers(qTarget))
        }, 1500)
    }

    private fun updateDisplays() {
        scoreText.text = game.score.toString()
        bestText.text = bestScore.toString()
        universesText.text = parallelUniverses.toString()
    }

    // === Sauvegarde ===

    private fun loadSave() {
        val prefs = getSharedPreferences("game2048_save", MODE_PRIVATE)
        bestScore = prefs.getInt("best_score", 0)
        parallelUniverses = prefs.getInt("parallel_universes", 0)

        val stateJson = prefs.getString("current_state", null)
        var restored = false
        if (stateJson != null) {
            try {
                val data = jsonToMap(JSONObject(stateJson))
                restored = game.deserialize(data)
            } catch (e: Exception) {
                restored = false
            }
        }

        if (!restored) {
            game.newGame(4, 256)
        }

        // Restaurer le mode quantique si la partie sauvegardée l'avait
        quantumMode = game.quantumMode
        ignoreSpinnerChange = true
        quantumSwitch.isChecked = quantumMode
        val sizeIdx = GRID_SIZES.indexOf(game.size).coerceAtLeast(0)
        sizeSpinner.setSelection(sizeIdx)
        val effectiveTarget = if (quantumMode) game.quantumTarget else game.target
        updateTargetSpinnerSelection(game.size, effectiveTarget)
        jokerButton.visibility = if (quantumMode) View.VISIBLE else View.GONE
        ignoreSpinnerChange = false

        boardView.game = game
        boardView.refresh()
        updateDisplays()
        refreshJokerButton()

        if (game.gameOver) {
            statusText.text = getString(R.string.game2048_status_over, game.bestTile)
        } else if (game.hasWon) {
            val winTarget = if (quantumMode) game.quantumTarget else game.target
            statusText.text = getString(R.string.game2048_status_win, winTarget)
        } else {
            statusText.text = getString(R.string.game2048_status_play)
        }
    }

    private fun saveCurrent() {
        if (game.moves == 0) return
        try {
            val json = mapToJson(game.serialize())
            getSharedPreferences("game2048_save", MODE_PRIVATE).edit()
                .putString("current_state", json.toString())
                .apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun saveBestScore() {
        getSharedPreferences("game2048_save", MODE_PRIVATE).edit()
            .putInt("best_score", bestScore)
            .apply()
    }

    private fun saveUniverses() {
        getSharedPreferences("game2048_save", MODE_PRIVATE).edit()
            .putInt("parallel_universes", parallelUniverses)
            .apply()
    }

    private fun mapToJson(map: Map<String, Any>): JSONObject {
        val obj = JSONObject()
        for ((key, value) in map) {
            when (value) {
                is List<*> -> {
                    val arr = JSONArray()
                    value.forEach { arr.put(it) }
                    obj.put(key, arr)
                }
                else -> obj.put(key, value)
            }
        }
        return obj
    }

    private fun jsonToMap(obj: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for (key in obj.keys()) {
            when (val v = obj.get(key)) {
                is JSONArray -> {
                    val list = mutableListOf<Any>()
                    for (i in 0 until v.length()) list.add(v.get(i))
                    map[key] = list
                }
                else -> map[key] = v
            }
        }
        return map
    }

    override fun onPause() {
        super.onPause()
        saveCurrent()
        saveBestScore()
        saveUniverses()
    }
}
