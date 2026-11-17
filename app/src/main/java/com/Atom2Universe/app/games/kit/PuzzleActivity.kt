package com.Atom2Universe.app.games.kit

import android.content.res.ColorStateList
import android.content.res.Configuration
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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.FitRow
import com.Atom2Universe.app.util.Landscape
import com.Atom2Universe.app.util.SidePanel
import com.Atom2Universe.app.util.showGameRules
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlin.random.Random

/**
 * L'écran commun des jeux du kit (puzzles de la collection de Simon Tatham et jeux solo
 * d'OpenSpiel) : une barre de titre, les préréglages en un seul bouton déroulant, le plateau,
 * d'éventuelles commandes propres au jeu, puis annuler / rétablir / recommencer / nouvelle partie
 * en icônes sans texte.
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
    // Les actions : des icônes discrètes dans la bande du haut.
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var restartButton: ImageButton
    private lateinit var newButton: ImageButton
    private var hintButton: ImageButton? = null
    private var presetSpinner: Spinner? = null

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
        hintButton?.let { showHintLook(it, demo = false) }
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
            hintButton?.let { showHintLook(it, demo = true); setEnabled(it, true) }
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
        // Couché, la bulle est étroite : une information par ligne plutôt qu'une phrase coupée n'importe où.
        val text = parts.joinToString(if (landscapeLayout) "\n" else getString(R.string.kit_separator))
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

    // Les vues de l'écran, créées une fois : la rotation ne fait que les redisposer.
    private lateinit var topBar: LinearLayout
    /** La bande du haut, réduite d'un bloc si un écran étroit ne peut pas tout montrer. */
    private lateinit var topBand: FitRow
    private lateinit var boardFrame: FrameLayout
    private var controlsView: View? = null
    private var arrangedLandscape: Boolean? = null

    private fun buildScreen(): View {
        // Les actions, en icônes discrètes.
        undoButton = iconButton(R.drawable.ic_px_undo, R.string.kit_undo, palette.text) { stopDemo(); undo() }
        redoButton = iconButton(R.drawable.ic_px_redo, R.string.kit_redo, palette.text) { stopDemo(); redo() }
        restartButton = iconButton(R.drawable.ic_restart, R.string.kit_restart, palette.text) { stopDemo(); restart() }
        newButton = iconButton(R.drawable.ic_refresh, R.string.kit_new, palette.text) {
            if (generation?.isActive != true) newGame()
        }
        if (hasHints) {
            hintButton = iconButton(R.drawable.ic_hint, R.string.kit_demo_stop, palette.text) { useHint() }.also {
                showHintLook(it, demo = false)
            }
        }

        // Bande du haut : retour, nom du jeu, actions, règles.
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(iconButton(R.drawable.ic_app_back, R.string.back, palette.text) { finish() },
            LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        topBar.addView(TextView(this).apply {
            setText(titleRes)
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(palette.text)
            setPadding((8 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        for (b in listOfNotNull(undoButton, redoButton, restartButton, hintButton, newButton)) {
            b.setPadding((9 * dp).toInt(), (9 * dp).toInt(), (9 * dp).toInt(), (9 * dp).toInt())
            b.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            topBar.addView(b, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
        }
        topBar.addView(iconButton(R.drawable.ic_info, R.string.kit_rules, palette.accent) { showRules() },
            LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))
        topBand = FitRow(this).apply {
            // Le titre garde sa largeur : faute de place, toute la bande rapetisse plutôt que de le couper.
            flexDp = 220f
            addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Préréglages : un seul bouton qui déroule les autres.
        if (presets.size > 1) presetSpinner = buildPresetSpinner()

        statusView = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(palette.secondary)
        }

        // Plateau, avec la roue de génération par-dessus.
        boardFrame = FrameLayout(this)
        boardFrame.addView(board, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))
        progress = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(palette.accent)
            visibility = View.GONE
        }
        boardFrame.addView(progress, FrameLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt(), Gravity.CENTER))

        controlsView = createControls()
        return arrange()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::boardFrame.isInitialized && arrangedLandscape != Landscape.isLandscape(this)) {
            setContentView(arrange())
            updateStatus()
        }
    }

    /**
     * Dispose les vues selon l'orientation. La bande du haut porte toujours les actions en icônes.
     * Debout : préréglage et chrono sur une ligne, plateau, commandes du jeu. Couché : le plateau
     * sur toute la place restante, et une petite bulle à côté (préréglage, chrono, commandes du jeu).
     */
    private fun arrange(): View {
        val landscape = Landscape.isLandscape(this)
        arrangedLandscape = landscape
        landscapeLayout = landscape
        listOfNotNull(topBand, statusView, boardFrame, controlsView, presetSpinner).forEach { Landscape.detach(it) }
        val screen = AppearanceStyle.screen(this@PuzzleActivity) ?: GradientDrawable().apply {
            setColor(palette.background)
        }
        return if (landscape) arrangeLandscape(screen) else arrangePortrait(screen)
    }

    /** Debout : la bande du haut ; préréglage et chrono sur une ligne ; plateau, commandes du jeu. */
    private fun arrangePortrait(screen: android.graphics.drawable.Drawable): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = screen
            setTag(R.id.app_effects_scene, true)
        }
        topBar.setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        root.addView(topBand, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (2 * dp).toInt(), (16 * dp).toInt(), (2 * dp).toInt())
        }
        presetSpinner?.let {
            line.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (40 * dp).toInt()))
        }
        statusView.textSize = 14f
        statusView.maxLines = 2
        statusView.gravity = if (presetSpinner != null) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER
        statusView.setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        line.addView(statusView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (12 * dp).toInt()
        })
        root.addView(line)
        board.setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        root.addView(boardFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        onArranged(false)
        controlsView?.let { controls ->
            root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            })
        }
        return root
    }

    /**
     * Couché : la bande du haut avec les actions, le plateau sur toute la place restante, et une
     * petite bulle à côté (préréglage, chrono, commandes du jeu), qu'on glisse à gauche ou à droite.
     * Un jeu à commandes (pavé de chiffres…) a une bulle plus large.
     */
    private fun arrangeLandscape(screen: android.graphics.drawable.Drawable): View {
        val panel = sidePanel ?: SidePanel(this).also {
            sidePanel = it
        }
        val col = panel.content
        col.removeAllViews()
        topBar.setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), 0)
        presetSpinner?.let {
            col.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (40 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
        }
        statusView.textSize = 17f
        statusView.maxLines = 4
        statusView.gravity = Gravity.CENTER
        statusView.setPadding(0, (6 * dp).toInt(), 0, (8 * dp).toInt())
        col.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        val width = bubbleWidth()
        panelContentWidth = width - (12 * dp).toInt()
        onArranged(true)
        controlsView?.let { controls ->
            col.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), (4 * dp).toInt())
            })
        }
        board.setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        return panel.compose(boardFrame, screen, topBand, width)
    }

    /** La largeur utile de la bulle couchée, en pixels (marges intérieures ôtées) : les commandes s'y règlent. */
    protected var panelContentWidth = 0
        private set

    /**
     * Range les touches d'un pavé (chiffres, gomme…) dans [box]. Debout, une seule rangée sur toute
     * la largeur. Couché, des touches carrées serrées, en rangées équilibrées de [maxPerRow] au plus
     * (4 + 3 plutôt que 6 + 1), à la taille que permet la bulle : le pavé ne déborde plus.
     */
    protected fun layoutKeys(box: LinearLayout, keys: List<View>, portraitHeightDp: Int = 46, maxPerRow: Int = 6) {
        keys.forEach { Landscape.detach(it) }
        box.removeAllViews()
        if (keys.isEmpty()) return
        val gap = (2 * dp).toInt()
        if (!landscapeLayout) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            keys.forEach {
                row.addView(it, LinearLayout.LayoutParams(0, (portraitHeightDp * dp).toInt(), 1f).apply {
                    marginStart = gap; marginEnd = gap
                })
            }
            return
        }
        val rows = (keys.size + maxPerRow - 1) / maxPerRow
        val perRow = (keys.size + rows - 1) / rows
        val room = panelContentWidth.takeIf { it > 0 } ?: (280 * dp).toInt()
        // 48 dp au plus : sur un téléphone couché, deux rangées de touches et les actions tiennent sans défiler.
        val size = (room / perRow - 2 * gap).coerceIn((40 * dp).toInt(), (48 * dp).toInt())
        val flow = FlexboxLayout(this).apply {
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.CENTER
        }
        keys.forEach { flow.addView(it, FlexboxLayout.LayoutParams(size, size).apply { setMargins(gap, gap, gap, gap) }) }
        box.addView(flow, LinearLayout.LayoutParams(perRow * (size + 2 * gap), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
    }

    /**
     * La largeur de la bulle couchée. Sans commandes propres au jeu, elle reste étroite (entre 128
     * et 200 dp) et le plateau prend tout le reste. Avec (pavé de chiffres…), elle prend ce que le
     * plateau carré laisse libre, entre 240 et 320 dp. Le plateau n'est pas centré de force : une
     * marge vide en face de la bulle le rétrécissait.
     */
    private fun bubbleWidth(): Int {
        if (controlsView != null) {
            // La vraie taille de l'écran : la configuration retranche les barres système, même
            // cachées, et la bulle débordait alors sur la place du plateau.
            val (w, h) = screenDp()
            val boardSide = h - 56f - 16f
            return ((w - boardSide - 32f).coerceIn(240f, 320f) * dp).toInt()
        }
        val c = resources.configuration
        val side = (c.screenWidthDp - (c.screenHeightDp - 56)) / 2 - 24
        return (side.coerceIn(128, 200) * dp).toInt()
    }

    /** Largeur et hauteur de tout l'écran, en dp. */
    private fun screenDp(): Pair<Float, Float> {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val b = windowManager.currentWindowMetrics.bounds
            return b.width() / dp to b.height() / dp
        }
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(m)
        return m.widthPixels / dp to m.heightPixels / dp
    }

    private var sidePanel: SidePanel? = null

    /** Un bouton d'action réduit à son icône ; son nom reste lu par l'accessibilité et en appui long. */
    protected fun iconAction(icon: Int, description: String, primary: Boolean, onClick: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = null
            setIconResource(icon)
            iconSize = (22 * dp).toInt()
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            cornerRadius = AppearanceStyle.corner(this@PuzzleActivity, 14f).toInt()
            val bg = if (primary) palette.accent else palette.raised
            val fg = if (primary) palette.onAccent else palette.text
            backgroundTintList = ColorStateList.valueOf(bg)
            iconTint = ColorStateList.valueOf(fg)
            strokeColor = ColorStateList.valueOf(if (primary) palette.accent else palette.outline)
            strokeWidth = if (primary) 0 else dp.toInt().coerceAtLeast(1)
            elevation = 0f
            stateListAnimator = null
            contentDescription = description
            tooltipText = description
            setOnClickListener { onClick() }
        }

    /** L'astuce : une ampoule (avec son prix au toucher long), un carré d'arrêt pendant la démonstration. */
    private fun showHintLook(button: ImageButton, demo: Boolean) {
        button.setImageResource(if (demo) R.drawable.ic_stop else R.drawable.ic_hint)
        val description = if (demo) getString(R.string.kit_demo_stop)
            else getString(R.string.kit_hint, NeutrinoRewards.HINT_COST)
        button.contentDescription = description
        button.tooltipText = description
    }

    /** Les préréglages : un seul bouton qui montre le choix et déroule les autres sous le doigt. */
    private fun buildPresetSpinner(): Spinner =
        KitDropdown.create(this, palette, presets.map { getString(it.labelRes, *it.args) }, { preset }) { position ->
            if (generation?.isActive == true) updateChips() else newGame(position)
        }

    /** Vrai quand l'écran est disposé couché (bulle de côté). */
    protected var landscapeLayout = false
        private set

    /** Appelé à chaque disposition, avant que les commandes du jeu soient posées. */
    protected open fun onArranged(landscape: Boolean) {}

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
        presetSpinner?.let { if (it.selectedItemPosition != preset) it.setSelection(preset, false) }
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
        // Partie finie : la nouvelle partie passe à l'accent.
        newButton.imageTintList = ColorStateList.valueOf(if (solved || lost) palette.accent else palette.text)
    }

    private fun setEnabled(button: View, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.35f
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
