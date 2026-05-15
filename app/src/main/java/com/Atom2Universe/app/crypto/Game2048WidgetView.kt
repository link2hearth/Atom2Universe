package com.Atom2Universe.app.crypto

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import android.widget.Toast
import com.Atom2Universe.app.R
import com.Atom2Universe.app.crypto.clicker.GameStatsRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.games.game2048.Game2048Logic
import com.Atom2Universe.app.games.game2048.Game2048View
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot

/**
 * Widget 2048 Quantique interactif pour la page Cryptos.
 *
 * Gestes :
 *  - Glisser le header    → déplacer le widget
 *  - Pincer (2 doigts)    → zoom/dézoom
 *  - Swiper sur le plateau→ déplacer les tuiles (haut/bas/gauche/droite)
 *  - ↺ (header)           → afficher l'overlay de sélection de difficulté
 *  - Tap sur l'objectif   → activer/désactiver le prochain coup joker 🐱
 *
 * Difficulté → taille aléatoire parmi 4-6 (seulement en mode quantique), objectif selon la taille :
 *  - Facile   : 4→128 · 5→512  · 6→1024  (classique) / 4→192   · 5→384  · 6→1536 (quantique)
 *  - Normal   : 4→1024· 5→2048 · 6→4096  (classique) / 4→448   · 5→896  · 6→3584 (quantique)
 *  - Difficile: 4→2048· 5→4096 · 6→8192  (classique) / 4→960   · 5→3840 · 6→7680 (quantique)
 *  - Aléatoire: taille + objectif librement piochés
 */
class Game2048WidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val game = Game2048Logic(4)

    private lateinit var cardView: MaterialCardView
    private lateinit var headerArea: LinearLayout
    private lateinit var headerTarget: TextView
    private lateinit var boardView: Game2048View
    private lateinit var difficultyOverlay: FrameLayout
    private lateinit var quantumToggle: SwitchCompat

    // ── Zoom (pinch 2 doigts) ─────────────────────────────────────────────────
    private val minScale = 0.4f
    private val maxScale = 3.0f
    private var currentScale = 1f

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

    // ── Drag du widget depuis le header ───────────────────────────────────────
    private var dragLastX = 0f
    private var dragLastY = 0f
    private var dragDownX = 0f
    private var dragDownY = 0f
    private var isDragging = false
    private var skipNextDragFrame = false
    private val tapThresholdPx = 12f * context.resources.displayMetrics.density

    init {
        LayoutInflater.from(context).inflate(R.layout.view_game2048_widget, this, true)

        cardView = findViewById(R.id.game2048_widget_card)
        headerArea = findViewById(R.id.game2048_header_area)
        headerTarget = findViewById(R.id.game2048_header_target)
        boardView = findViewById(R.id.game2048_widget_board)
        difficultyOverlay = findViewById(R.id.game2048_difficulty_overlay)
        quantumToggle = findViewById(R.id.game2048_quantum_toggle)

        // Connecter la vue au jeu
        boardView.game = game
        boardView.swipeListener = object : Game2048View.SwipeListener {
            override fun onSwipe(direction: Game2048Logic.Direction) {
                handleSwipe(direction)
            }
        }

        // Bouton ↺ → afficher/masquer l'overlay de difficulté
        findViewById<TextView>(R.id.game2048_btn_reset).setOnClickListener {
            difficultyOverlay.visibility =
                if (difficultyOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Boutons de l'overlay de difficulté
        findViewById<TextView>(R.id.game2048_diff_easy).setOnClickListener {
            val (s, t, q, jk) = pickForDifficulty(TARGETS_EASY, QUANTUM_EASY, JOKERS_EASY)
            startNewGame(s, t, q, jk, countStat = true)
        }
        findViewById<TextView>(R.id.game2048_diff_normal).setOnClickListener {
            val (s, t, q, jk) = pickForDifficulty(TARGETS_NORMAL, QUANTUM_NORMAL, JOKERS_NORMAL)
            startNewGame(s, t, q, jk, countStat = true)
        }
        findViewById<TextView>(R.id.game2048_diff_hard).setOnClickListener {
            val (s, t, q, jk) = pickForDifficulty(TARGETS_HARD, QUANTUM_HARD, JOKERS_HARD)
            startNewGame(s, t, q, jk, countStat = true)
        }
        findViewById<TextView>(R.id.game2048_diff_random).setOnClickListener {
            val (s, t, q, jk) = pickRandom()
            startNewGame(s, t, q, jk, countStat = true)
        }
        // Drag depuis le header — headerArea et le bouton reset
        val dragListener = View.OnTouchListener { _, event ->
            handleHeaderTouch(event)
            event.action == MotionEvent.ACTION_UP && isDragging
        }
        headerArea.setOnTouchListener(dragListener)
        findViewById<View>(R.id.game2048_btn_reset).setOnTouchListener(dragListener)

        // headerTarget : drag + tap joker (doit être géré ici pour ne pas bloquer le drag)
        headerTarget.setOnTouchListener { _, event ->
            val wasDragging = isDragging
            handleHeaderTouch(event)
            if (event.action == MotionEvent.ACTION_UP && !wasDragging) {
                // C'est un tap → activer/désactiver le joker quantique
                if (game.quantumMode && game.jokers > 0) {
                    game.nextMoveIsJoker = !game.nextMoveIsJoker
                    updateHeaderTarget()
                }
            }
            true  // Consommer l'événement (ne pas remonter à headerArea en double)
        }

        // Charger la partie sauvegardée
        reload()
    }

    // ── Interception du pinch-zoom (2 doigts) avant les enfants ──────────────
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) {
            scaleDetector.onTouchEvent(ev)
            return true
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        return true
    }

    // ── Drag depuis le header ─────────────────────────────────────────────────
    private fun handleHeaderTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bringToFront()
                dragDownX = event.rawX
                dragDownY = event.rawY
                dragLastX = event.rawX
                dragLastY = event.rawY
                isDragging = false
                skipNextDragFrame = false
            }
            MotionEvent.ACTION_POINTER_UP -> skipNextDragFrame = true
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (skipNextDragFrame) {
                        dragLastX = event.rawX
                        dragLastY = event.rawY
                        dragDownX = event.rawX
                        dragDownY = event.rawY
                        isDragging = false
                        skipNextDragFrame = false
                    } else {
                        val moved = hypot(event.rawX - dragDownX, event.rawY - dragDownY)
                        if (isDragging || moved > tapThresholdPx) {
                            isDragging = true
                            translationX += event.rawX - dragLastX
                            translationY += event.rawY - dragLastY
                        }
                        dragLastX = event.rawX
                        dragLastY = event.rawY
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                skipNextDragFrame = false
            }
        }
        return true
    }

    // ── Gestion du swipe sur le plateau ───────────────────────────────────────
    private fun handleSwipe(direction: Game2048Logic.Direction) {
        if (game.gameOver) return
        val moved = game.move(direction)
        if (!moved) return

        boardView.refresh()
        updateHeaderTarget()
        persistState()

        if (game.hasWon) {
            GameStatsRepository(context).recordGame2048Won()
            NeutrinoRepository(context).addBalance(5)
        }
        if (game.hasWon || game.gameOver) {
            boardView.postDelayed({
                val qTarget = if (game.quantumMode) game.quantumTarget else 0
                val jokers = if (game.quantumMode) QUANTUM_JOKERS[game.quantumTarget] ?: 0 else 0
                startNewGame(game.size, game.target, qTarget, jokers)
            }, 1200)
        }
    }

    // ── Nouvelle partie ───────────────────────────────────────────────────────
    private fun startNewGame(size: Int, target: Int, quantumTarget: Int = 0, jokers: Int = 0, countStat: Boolean = false) {
        difficultyOverlay.visibility = View.GONE
        val isQuantum = quantumTarget > 0
        game.newGame(size, target, isQuantum, quantumTarget, jokers)
        boardView.game = game
        boardView.refresh()
        updateHeaderTarget()
        persistState()
        if (countStat) GameStatsRepository(context).recordGame2048Started()
    }

    // ── Affichage de l'objectif dans le header ────────────────────────────────
    private fun updateHeaderTarget() {
        if (game.quantumMode) {
            val jokerIndicator = when {
                game.jokers <= 0 -> ""
                game.nextMoveIsJoker -> " ⚡"
                else -> " 🐱×${game.jokers}"
            }
            headerTarget.text = "${game.quantumTarget}$jokerIndicator"
            headerTarget.setTextColor(android.graphics.Color.parseColor("#A78BFA"))
        } else {
            headerTarget.text = game.target.toString()
            headerTarget.setTextColor(android.graphics.Color.parseColor("#EDC22E"))
        }
    }

    // ── Opacité globale (tout le widget, grille incluse) ──────────────────────
    fun applyBackgroundOpacity(percent: Int) {
        alpha = percent.coerceIn(0, 100) / 100f
    }

    // ── Persistance (même fichier que Game2048Activity) ───────────────────────
    private fun persistState() {
        if (game.moves == 0) return
        try {
            val json = mapToJson(game.serialize())
            context.getSharedPreferences("game2048_save", Context.MODE_PRIVATE).edit()
                .putString("current_state", json.toString())
                .apply()
        } catch (_: Exception) {}
    }

    /** Recharge la partie depuis les SharedPreferences (appelé par MainClickerActivity.onResume). */
    fun reload() {
        val prefs = context.getSharedPreferences("game2048_save", Context.MODE_PRIVATE)
        val stateJson = prefs.getString("current_state", null)
        var restored = false
        if (stateJson != null) {
            try {
                val data = jsonToMap(JSONObject(stateJson))
                restored = game.deserialize(data)
            } catch (_: Exception) {}
        }
        if (!restored) {
            game.newGame(4, 256)
        }
        // Synchroniser le toggle quantique de l'overlay avec l'état restauré
        quantumToggle.isChecked = game.quantumMode
        boardView.game = game
        boardView.refresh()
        updateHeaderTarget()
    }

    // ── Sélection de difficulté ───────────────────────────────────────────────

    /** Retourne (size, classicTarget, quantumTarget, jokers) pour la difficulté donnée. */
    private fun pickForDifficulty(
        classicTargets: Map<Int, Int>,
        quantumTargets: Map<Int, Int>,
        jokers: Int
    ): DifficultyResult {
        val isQuantum = quantumToggle.isChecked
        val sizes = if (isQuantum) listOf(4, 5, 6) else classicTargets.keys.toList()
        val size = sizes.random()
        return if (isQuantum) {
            val qTarget = quantumTargets[size] ?: 192
            DifficultyResult(size, 256, qTarget, jokers)
        } else {
            DifficultyResult(size, classicTargets[size] ?: 256, 0, 0)
        }
    }

    private fun pickRandom(): DifficultyResult {
        val isQuantum = quantumToggle.isChecked
        return if (isQuantum) {
            val size = listOf(4, 5, 6).random()
            val qTarget = ALL_QUANTUM_TARGETS.random()
            val jokers = QUANTUM_JOKERS[qTarget] ?: 3
            DifficultyResult(size, 256, qTarget, jokers)
        } else {
            val size = ALL_SIZES.random()
            val target = ALL_TARGETS.random()
            DifficultyResult(size, target, 0, 0)
        }
    }

    private data class DifficultyResult(
        val size: Int,
        val target: Int,
        val quantumTarget: Int,
        val jokers: Int
    )

    // ── Utilitaires JSON ──────────────────────────────────────────────────────
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

    companion object {
        // Objectifs classiques par taille de grille
        private val TARGETS_EASY   = mapOf(3 to 64,   4 to 256,  5 to 512,  6 to 1024)
        private val TARGETS_NORMAL = mapOf(3 to 128,  4 to 1024, 5 to 2048, 6 to 4096)
        private val TARGETS_HARD   = mapOf(3 to 256,  4 to 2048, 5 to 4096, 6 to 8192)

        // Objectifs impures par taille (mode quantique 🐱, 4×4 minimum)
        private val QUANTUM_EASY   = mapOf(4 to 192,  5 to 384,  6 to 1536)
        private val QUANTUM_NORMAL = mapOf(4 to 448,  5 to 896,  6 to 3584)
        private val QUANTUM_HARD   = mapOf(4 to 960,  5 to 3840, 6 to 7680)

        // Jokers par difficulté quantique
        private const val JOKERS_EASY   = 3
        private const val JOKERS_NORMAL = 6
        private const val JOKERS_HARD   = 4

        // Carte jokers par objectif (pour l'auto-renouvellement après victoire/défaite)
        val QUANTUM_JOKERS = mapOf(
            192 to 3,  384 to 3,  1536 to 3,
            448 to 6,  896 to 6,  3584 to 6,
            960 to 4,  3840 to 4, 7680 to 4
        )

        private val ALL_SIZES   = listOf(3, 4, 5, 6)
        private val ALL_TARGETS = listOf(64, 256, 512, 1024, 2048, 4096, 8192)
        private val ALL_QUANTUM_TARGETS = listOf(192, 384, 448, 896, 960, 1536, 3584, 3840, 7680)
    }
}
