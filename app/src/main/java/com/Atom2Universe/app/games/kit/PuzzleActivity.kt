package com.Atom2Universe.app.games.kit

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.showGameRules
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlin.random.Random

/**
 * L'écran commun des jeux du kit (puzzles de la collection de Simon Tatham et jeux solo
 * d'OpenSpiel) : une barre de titre, les préréglages en pastilles, le plateau, d'éventuelles
 * commandes propres au jeu, puis annuler / rétablir / recommencer / nouvelle partie.
 *
 * Chaque jeu ne fournit que ce qui lui est propre : générer une partie, l'encoder pour la
 * sauvegarde, dire si elle est résolue, et sa vue. L'historique est une simple pile d'états :
 * un coup ne modifie jamais l'état affiché, il en produit un nouveau.
 *
 * Tout est lu dans le thème (fond d'écran, panneaux, arrondis, couleur d'accent, clair ou sombre).
 * Rien n'explique les règles à l'écran : elles sont derrière le bouton « i ».
 */
abstract class PuzzleActivity<S : Any> : ThemedActivity() {

    /**
     * Une difficulté ou une taille proposée en pastille. Le libellé peut prendre des arguments
     * (« 7×7 » s'écrit `Preset(R.string.kit_size, 7, 7)`). Sa récompense en neutrinos n'est pas
     * ici : elle vit avec toutes les autres dans `NeutrinoRewards.kit`.
     */
    class Preset(val labelRes: Int, vararg val args: Any)

    protected abstract val gameKey: String
    protected abstract val titleRes: Int
    protected abstract val rulesRes: Int
    protected abstract val presets: List<Preset>
    protected open val defaultPreset: Int = 0

    /** Vrai si le jeu a un chrono (les jeux de score, eux, comptent autrement). */
    protected open val timed: Boolean = true

    protected abstract fun createBoard(): PuzzleView<S>

    /** Fabrique une partie neuve ; appelé hors du fil d'interface. */
    protected abstract fun generate(preset: Int, random: Random): S
    protected abstract fun encode(state: S): String
    protected abstract fun decode(text: String): S?
    protected abstract fun isSolved(state: S): Boolean

    /** Vrai si la partie est perdue (plus rien à jouer) : elle s'arrête sans récompense. */
    protected open fun isLost(state: S): Boolean = false

    /**
     * Vrai si une défaite montre la solution (Boîte noire, Mastermind) : la partie compte alors
     * comme aidée, pour qu'annuler puis recopier la solution ne rapporte rien.
     */
    protected open val revealsOnLoss: Boolean = false

    /** Ce qui s'affiche à côté du chrono (mines restantes, coups…), ou null. */
    protected open fun statusText(state: S): String? = null

    /** Commandes propres au jeu, posées sous le plateau (pavé de chiffres, outil…). */
    protected open fun createControls(): View? = null

    /** Appelé quand l'état affiché change (coup, annulation, nouvelle partie). */
    protected open fun onStateShown(state: S) {}

    /** Récompense en neutrinos d'une partie gagnée, lue dans la source unique des montants. */
    protected open fun reward(preset: Int, state: S): Int = NeutrinoRewards.kit(gameKey, preset)

    /**
     * L'astuce payante : l'état avec un pas de plus vers la solution (une case juste de plus, une
     * erreur corrigée…), ou toute la solution pour les jeux où un indice ne voudrait rien dire.
     * null : rien à montrer, et rien n'est payé. Un jeu qui en propose met [hasHints] à vrai.
     * Une partie aidée ne rapporte plus rien : ni neutrinos, ni meilleur temps, ni compteur.
     */
    protected open fun hint(state: S): S? = null
    protected open val hasHints: Boolean = false

    /**
     * Une astuce « démonstration » : toute la suite de coups jusqu'à la solution, jouée sous les
     * yeux du joueur, lentement, pour qu'il voie comment on s'y prend. Si elle est fournie, elle
     * remplace [hint]. [onDemoStep] dit au jeu où en est la lecture (null : terminée ou arrêtée).
     */
    protected open fun demo(state: S): List<S>? = null
    /** Calculée hors interface ; les éventuelles données visuelles sont publiées ensuite. */
    protected class Demonstration<S>(val steps: List<S>, val onReady: () -> Unit = {})
    protected open fun prepareDemo(state: S): Demonstration<S>? = demo(state)?.let { Demonstration(it) }
    protected open val demoStepMs: Long = 350L
    /** Vrai si la démonstration sait repartir d'une partie perdue (en revenant en arrière). */
    protected open val demoWhenLost: Boolean = false
    protected open fun onDemoStep(step: Int?) {}

    /** Appelé une seule fois, à la première victoire d'une partie (débloquer un niveau…). */
    protected open fun onSolved(state: S) {}

    /** Sous quelle clé ranger le meilleur temps : par préréglage, ou plus fin (par niveau). */
    protected open fun bestKey(preset: Int, state: S): String = "$KEY_BEST$preset"

    protected lateinit var palette: KitPalette
        private set
    protected lateinit var board: PuzzleView<S>
        private set

    protected var preset = 0
        private set

    private val history = ArrayList<S>()
    private var cursor = -1
    private var solved = false
    private var lost = false
    private var rewarded = false
    /** Vrai dès qu'une astuce a servi dans cette partie. */
    private var assisted = false
    private var elapsedMs = 0L
    private var runningSince = 0L
    private var generation: Job? = null
    private var hintCalculation: Job? = null
    private var stateStatus: String? = null
    private var resumed = false

    protected val current: S? get() = history.getOrNull(cursor)

    private lateinit var statusView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var undoButton: MaterialButton
    private lateinit var redoButton: MaterialButton
    private lateinit var restartButton: MaterialButton
    private var hintButton: MaterialButton? = null
    private val presetChips = ArrayList<TextView>()

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!timed || runningSince == 0L) return
            updateStatus()
            handler.postDelayed(this, 1000L)
        }
    }

    private val prefs by lazy { getSharedPreferences("$PREFS_PREFIX$gameKey", MODE_PRIVATE) }

    protected val dp: Float get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = KitPalette.from(this)
        board = createBoard().also {
            it.palette = palette
            it.onMove = { next -> play(next) }
        }
        setContentView(buildScreen())
        // Les libellés des pastilles, dans la langue du moment : la page des stats de jeux les
        // relit pour nommer la plus grande taille réussie.
        prefs.edit { presets.forEachIndexed { i, p -> putString("$KEY_LABEL$i", getString(p.labelRes, *p.args)) } }
        enableImmersiveMode()
        if (!restore()) newGame(prefs.getInt(KEY_PRESET, defaultPreset).coerceIn(0, presets.size - 1))
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!solved && !lost && current != null && generation?.isActive != true) startClock()
    }

    override fun onPause() {
        resumed = false
        cancelHint()
        stopDemo()
        stopClock()
        handler.removeCallbacks(ticker)
        save()
        super.onPause()
    }

    // ── Partie ──────────────────────────────────────────────────────────────────────────────

    protected fun newGame(presetIndex: Int = preset) {
        cancelHint()
        stopDemo()
        generation?.cancel()
        stopClock()
        preset = presetIndex
        updateChips()
        board.inputEnabled = false
        progress.visibility = View.VISIBLE
        board.alpha = 0.25f
        val seed = Random.nextLong()
        generation = lifecycleScope.launch {
            val state = runInterruptible(Dispatchers.Default) { generate(presetIndex, Random(seed)) }
            generation = null
            progress.visibility = View.GONE
            board.alpha = 1f
            history.clear()
            history.add(state)
            cursor = 0
            solved = false
            lost = false
            rewarded = false
            assisted = false
            elapsedMs = 0L
            show(state)
            startClock()
            save()
        }
    }

    /** Vrai pendant qu'une démonstration se joue : les commandes du jeu n'y touchent pas. */
    protected val demoRunning: Boolean get() = demoSteps != null

    /** Joue un coup : l'état suivant remplace tout ce qui avait été annulé. */
    protected fun play(next: S) {
        if (demoSteps != null) return
        push(next)
    }

    private fun push(next: S) {
        if (solved || lost || cursor < 0 || generation?.isActive == true) return
        cancelHint()
        while (history.size > cursor + 1) history.removeAt(history.size - 1)
        history.add(next)
        if (history.size > MAX_HISTORY) history.removeAt(1)
        cursor = history.size - 1
        show(next)
        checkEnd(next)
    }

    /** Remplace l'état affiché sans l'empiler (animations, effets qui ne s'annulent pas seuls). */
    protected fun replaceCurrent(next: S) {
        if (cursor < 0) return
        cancelHint()
        history[cursor] = next
        show(next)
        checkEnd(next)
    }

    private fun undo() {
        if (generation?.isActive == true) return
        cancelHint()
        if (cursor <= 0) return
        cursor--
        reopen()
    }

    private fun redo() {
        if (generation?.isActive == true) return
        cancelHint()
        if (cursor >= history.size - 1) return
        cursor++
        reopen()
        current?.let { checkEnd(it) }
    }

    /**
     * L'astuce : payée seulement si elle montre quelque chose. Elle entre dans l'historique comme
     * un coup (on peut l'annuler, mais elle reste payée) et la partie ne rapporte plus rien.
     */
    private var demoSteps: List<S>? = null
    private var demoIndex = 0
    private val demoRunner = object : Runnable {
        override fun run() {
            val steps = demoSteps ?: return
            if (demoIndex >= steps.size || solved || lost) { stopDemo(); return }
            onDemoStep(demoIndex)
            push(steps[demoIndex++])
            if (demoSteps != null) handler.postDelayed(this, demoStepMs)
        }
    }

    /** Arrête une démonstration en cours (bouton Arrêter, autre bouton, pause, fin). */
    private fun stopDemo() {
        if (demoSteps == null) return
        demoSteps = null
        handler.removeCallbacks(demoRunner)
        onDemoStep(null)
        hintButton?.text = getString(R.string.kit_hint, NeutrinoRewards.HINT_COST)
        board.inputEnabled = !solved && !lost
        updateButtons()
    }

    private fun useHint() {
        if (demoSteps != null) { stopDemo(); return }
        val s = current ?: return
        if (solved || (lost && !demoWhenLost) || generation?.isActive == true || hintCalculation != null) return
        board.inputEnabled = false
        progress.visibility = View.VISIBLE
        hintButton?.let { setEnabled(it, false) }
        hintCalculation = lifecycleScope.launch {
            try {
                val (demonstration, next) = runInterruptible(Dispatchers.Default) {
                    val plan = prepareDemo(s)
                    plan to if (plan == null) hint(s) else null
                }
                // Un autre bouton peut avoir changé la partie pendant la recherche.
                if (current !== s || generation?.isActive == true) return@launch
                hintCalculation = null
                applyHint(demonstration, next)
            } finally {
                if (hintCalculation === coroutineContext[Job] || hintCalculation == null) {
                    hintCalculation = null
                    progress.visibility = if (generation?.isActive == true) View.VISIBLE else View.GONE
                    board.inputEnabled = !solved && !lost && demoSteps == null && generation?.isActive != true
                    updateButtons()
                }
            }
        }
    }

    private fun cancelHint() {
        val job = hintCalculation ?: return
        hintCalculation = null
        job.cancel()
        progress.visibility = if (generation?.isActive == true) View.VISIBLE else View.GONE
        board.inputEnabled = !solved && !lost && demoSteps == null && generation?.isActive != true
        updateButtons()
    }

    private fun applyHint(demonstration: Demonstration<S>?, next: S?) {
        val steps = demonstration?.steps
        if (steps != null) {
            if (steps.isEmpty() || !NeutrinoRepository(this).subtractBalance(NeutrinoRewards.HINT_COST)) return
            // Une partie perdue (plus aucun coup) se rouvre : la démonstration commence par revenir en arrière.
            if (lost) reopen()
            assisted = true
            demonstration.onReady()
            board.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            demoSteps = steps
            demoIndex = 0
            board.inputEnabled = false
            hintButton?.setText(R.string.kit_demo_stop)
            hintButton?.let { setEnabled(it, true) }
            handler.post(demoRunner)
            return
        }
        if (lost) return
        if (next == null) return
        if (!NeutrinoRepository(this).subtractBalance(NeutrinoRewards.HINT_COST)) return
        assisted = true
        board.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        play(next)
    }

    private fun restart() {
        if (generation?.isActive == true) return
        cancelHint()
        val first = history.firstOrNull() ?: return
        if (cursor == 0 && !solved && !lost) return
        reopen()
        play(first)
    }

    /** Revenir sur une partie finie la rouvre ; la récompense, elle, reste acquise. */
    private fun reopen() {
        if (solved || lost) {
            solved = false
            lost = false
            startClock()
        }
        board.inputEnabled = true
        current?.let { show(it) }
    }

    private fun show(state: S) {
        board.state = state
        board.inputEnabled = !solved && !lost && demoSteps == null
        onStateShown(state)
        stateStatus = statusText(state)
        updateButtons()
        updateStatus()
    }

    private fun checkEnd(state: S) {
        when {
            isSolved(state) -> {
                solved = true
                stopClock()
                board.inputEnabled = false
                board.celebrate()
                if (!rewarded && assisted) {
                    // Aidée : elle débloque ce qu'elle débloque (l'image suivante…) mais ne paie rien.
                    rewarded = true
                    onSolved(state)
                }
                if (!rewarded) {
                    rewarded = true
                    val amount = reward(preset, state)
                    if (amount > 0) NeutrinoRepository(this).addBalance(amount)
                    onSolved(state)
                    prefs.edit {
                        putInt(KEY_WON, prefs.getInt(KEY_WON, 0) + 1)
                        if (preset + 1 > prefs.getInt(KEY_TOP, 0)) putInt(KEY_TOP, preset + 1)
                    }
                    if (timed) {
                        val key = bestKey(preset, state)
                        val best = prefs.getLong(key, 0L)
                        if (best == 0L || elapsedMs < best) prefs.edit { putLong(key, elapsedMs) }
                    }
                }
                save()
            }
            isLost(state) -> {
                lost = true
                if (revealsOnLoss) assisted = true
                stopClock()
                board.inputEnabled = false
                save()
            }
        }
        updateStatus()
        updateButtons()
    }

    // ── Chrono ──────────────────────────────────────────────────────────────────────────────

    private fun startClock() {
        if (runningSince != 0L || !resumed) return
        runningSince = SystemClock.elapsedRealtime()
        if (timed) { handler.removeCallbacks(ticker); handler.post(ticker) }
    }

    private fun stopClock() {
        handler.removeCallbacks(ticker)
        if (runningSince != 0L) {
            elapsedMs += SystemClock.elapsedRealtime() - runningSince
            runningSince = 0L
        }
    }

    private fun elapsed(): Long =
        elapsedMs + if (runningSince != 0L) SystemClock.elapsedRealtime() - runningSince else 0L

    protected fun formatTime(ms: Long): String {
        val s = ms / 1000
        return getString(R.string.kit_time, s / 60, s % 60)
    }

    private fun updateStatus() {
        if (!::statusView.isInitialized) return
        val state = current
        val parts = ArrayList<String>()
        if (state != null) {
            when {
                solved && timed -> parts.add(getString(R.string.kit_solved_in, formatTime(elapsed())))
                solved -> parts.add(getString(R.string.kit_solved))
                lost -> parts.add(getString(R.string.kit_lost))
                timed -> parts.add(formatTime(elapsed()))
            }
            stateStatus?.let { parts.add(it) }
            if (timed && solved) {
                val best = prefs.getLong(bestKey(preset, state), 0L)
                if (best > 0L) parts.add(getString(R.string.kit_best, formatTime(best)))
            }
        }
        val text = parts.joinToString(getString(R.string.kit_separator))
        if (statusView.text.toString() != text) statusView.text = text
        statusView.setTextColor(if (solved) palette.ink else palette.secondary)
    }

    // ── Sauvegarde ──────────────────────────────────────────────────────────────────────────

    private fun save() {
        if (generation?.isActive == true) return
        val state = current ?: return
        val first = history.firstOrNull() ?: return
        prefs.edit {
            putInt(KEY_PRESET, preset)
            putString(KEY_INITIAL, encode(first))
            putString(KEY_CURRENT, encode(state))
            putLong(KEY_ELAPSED, elapsed())
            putBoolean(KEY_SOLVED, solved)
            putBoolean(KEY_LOST, lost)
            putBoolean(KEY_REWARDED, rewarded)
            putBoolean(KEY_ASSISTED, assisted)
        }
    }

    private fun restore(): Boolean {
        val initialText = prefs.getString(KEY_INITIAL, null) ?: return false
        val currentText = prefs.getString(KEY_CURRENT, null) ?: return false
        val first = runCatching { decode(initialText) }.getOrNull() ?: return false
        val state = runCatching { decode(currentText) }.getOrNull() ?: return false
        // Lisible mais qui ne colle plus au jeu (image retirée…) : on repart d'une partie neuve.
        if (runCatching { isSolved(first); isSolved(state); statusText(state) }.isFailure) return false
        preset = prefs.getInt(KEY_PRESET, defaultPreset).coerceIn(0, presets.size - 1)
        history.clear()
        history.add(first)
        if (currentText != initialText) history.add(state)
        cursor = history.size - 1
        elapsedMs = prefs.getLong(KEY_ELAPSED, 0L)
        solved = prefs.getBoolean(KEY_SOLVED, false)
        lost = prefs.getBoolean(KEY_LOST, false)
        rewarded = prefs.getBoolean(KEY_REWARDED, false)
        assisted = prefs.getBoolean(KEY_ASSISTED, false)
        updateChips()
        if (runCatching { show(state) }.isFailure) {
            board.state = null
            history.clear()
            cursor = -1
            solved = false
            lost = false
            return false
        }
        if (solved) board.inputEnabled = false
        return true
    }

    // ── Écran ───────────────────────────────────────────────────────────────────────────────

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppearanceStyle.screen(this@PuzzleActivity) ?: GradientDrawable().apply {
                setColor(palette.background)
            }
            setTag(R.id.app_effects_scene, true)
        }

        // Barre de titre : retour, nom du jeu, règles.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        }
        top.addView(iconButton(R.drawable.ic_app_back, R.string.back, palette.text) { finish() },
            LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        top.addView(TextView(this).apply {
            setText(titleRes)
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(palette.text)
            setPadding((8 * dp).toInt(), 0, 0, 0)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconButton(R.drawable.ic_info, R.string.kit_rules, palette.accent) { showRules() },
            LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        root.addView(top)

        // Préréglages.
        if (presets.size > 1) {
            val chips = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((12 * dp).toInt(), (2 * dp).toInt(), (12 * dp).toInt(), (2 * dp).toInt())
            }
            presets.forEachIndexed { index, p ->
                val chip = TextView(this).apply {
                    text = getString(p.labelRes, *p.args)
                    textSize = 13f
                    gravity = Gravity.CENTER
                    minWidth = (56 * dp).toInt()
                    setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
                    setOnClickListener { if (generation?.isActive != true) newGame(index) }
                }
                presetChips.add(chip)
                chips.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = (6 * dp).toInt() })
            }
            root.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(chips)
            })
        }

        statusView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(palette.secondary)
            setPadding(0, (6 * dp).toInt(), 0, (2 * dp).toInt())
        }
        root.addView(statusView)

        // Plateau, avec la roue de génération par-dessus.
        val boardFrame = FrameLayout(this)
        board.setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        boardFrame.addView(board, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        progress = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(palette.accent)
            visibility = View.GONE
        }
        boardFrame.addView(progress, FrameLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt(), Gravity.CENTER))
        root.addView(boardFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        createControls()?.let { controls ->
            root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), 0)
            })
        }

        // Actions.
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
        }
        undoButton = actionButton(R.string.kit_undo, R.drawable.ic_px_undo, primary = false) { stopDemo(); undo() }
        redoButton = actionButton(R.string.kit_redo, R.drawable.ic_px_redo, primary = false) { stopDemo(); redo() }
        restartButton = actionButton(R.string.kit_restart, R.drawable.ic_restart, primary = false) { stopDemo(); restart() }
        val newButton = actionButton(R.string.kit_new, R.drawable.ic_refresh, primary = true) {
            if (generation?.isActive != true) newGame()
        }
        if (hasHints) {
            hintButton = actionButton(R.string.kit_hint, R.drawable.ic_hint, primary = false) { useHint() }.apply {
                text = getString(R.string.kit_hint, NeutrinoRewards.HINT_COST)
            }
        }
        for (b in listOfNotNull(undoButton, redoButton, restartButton, hintButton, newButton)) {
            actions.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (3 * dp).toInt()
                marginEnd = (3 * dp).toInt()
            })
        }
        root.addView(actions)
        return root
    }

    private fun showRules() {
        showGameRules(titleRes, rulesRes)
    }

    private fun iconButton(icon: Int, description: Int, tint: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tint)
        val ripple = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        setBackgroundResource(ripple.resourceId)
        contentDescription = getString(description)
        tooltipText = contentDescription
        setOnClickListener { onClick() }
    }

    /** Bouton d'action aux couleurs du thème : plein pour l'action principale, en relief sinon. */
    protected fun actionButton(text: Int, icon: Int, primary: Boolean, onClick: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            setText(text)
            isAllCaps = false
            textSize = 12f
            setIconResource(icon)
            iconSize = (18 * dp).toInt()
            iconGravity = MaterialButton.ICON_GRAVITY_TOP
            iconPadding = (2 * dp).toInt()
            insetTop = 0
            insetBottom = 0
            minHeight = (52 * dp).toInt()
            setPadding((4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt())
            cornerRadius = AppearanceStyle.corner(this@PuzzleActivity, 14f).toInt()
            val bg = if (primary) palette.accent else palette.raised
            val fg = if (primary) palette.onAccent else palette.text
            backgroundTintList = ColorStateList.valueOf(bg)
            setTextColor(fg)
            iconTint = ColorStateList.valueOf(fg)
            strokeColor = ColorStateList.valueOf(if (primary) palette.accent else palette.outline)
            strokeWidth = if (primary) 0 else dp.toInt().coerceAtLeast(1)
            elevation = 0f
            stateListAnimator = null
            setOnClickListener { onClick() }
        }

    /** Une pastille de choix (préréglage, outil) : pleine quand elle est choisie. */
    protected fun styleChip(chip: TextView, selected: Boolean) {
        chip.background = GradientDrawable().apply {
            cornerRadius = AppearanceStyle.corner(this@PuzzleActivity, 16f)
            setColor(if (selected) palette.accent else palette.raised)
            setStroke(dp.toInt().coerceAtLeast(1), if (selected) palette.accent else palette.outline)
        }
        chip.setTextColor(if (selected) palette.onAccent else palette.text)
    }

    private fun updateChips() {
        presetChips.forEachIndexed { i, chip -> styleChip(chip, i == preset) }
    }

    private fun updateButtons() {
        if (!::undoButton.isInitialized) return
        val generating = generation?.isActive == true
        setEnabled(undoButton, !generating && cursor > 0)
        setEnabled(redoButton, !generating && cursor < history.size - 1)
        setEnabled(restartButton, !generating && (cursor > 0 || solved || lost))
        hintButton?.let {
            setEnabled(it, !generating && hintCalculation == null && (demoSteps != null || (!solved && (!lost || demoWhenLost) && NeutrinoRepository(this).getBalance() >= NeutrinoRewards.HINT_COST)))
        }
    }

    private fun setEnabled(button: MaterialButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.4f
    }

    companion object {
        private const val MAX_HISTORY = 500
        private const val KEY_PRESET = "preset"
        private const val KEY_INITIAL = "initial"
        private const val KEY_CURRENT = "current"
        private const val KEY_ELAPSED = "elapsed"
        private const val KEY_SOLVED = "solved"
        private const val KEY_LOST = "lost"
        private const val KEY_REWARDED = "rewarded"
        private const val KEY_ASSISTED = "assisted"
        const val KEY_BEST = "best_"
        // Lues aussi par la page des stats de jeux (ClickerStatsActivity) et synchronisées sur
        // le Drive (SharedGameStats, GamesSyncFile) : grilles résolues, plus grande pastille
        // réussie (numérotée à partir de 1, car la sync ignore les zéros), libellé de chaque
        // pastille (lui ne voyage pas : chaque appareil le refait à l'ouverture du jeu).
        const val KEY_WON = "won"
        const val KEY_TOP = "top_size"
        const val KEY_LABEL = "label_"
        const val PREFS_PREFIX = "kit_"
    }
}
