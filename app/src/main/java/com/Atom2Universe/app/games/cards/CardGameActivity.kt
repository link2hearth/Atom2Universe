package com.Atom2Universe.app.games.cards

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.games.kit.KitPalette
import com.Atom2Universe.app.util.ImmersiveAlertDialogBuilder
import com.Atom2Universe.app.util.showGameRules
import com.Atom2Universe.app.util.enableImmersiveMode
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * L'écran commun des jeux de cartes contre l'ordinateur : le titre, les trois niveaux en pastilles,
 * la table, des boutons propres au jeu, et « Nouvelle partie ».
 *
 * Chaque jeu fournit son moteur ([createEngine]), ce qu'on en voit ([buildScene]) et ce que fait un
 * toucher sur une carte ([onHumanCard]). Tout le reste est ici : la boucle des tours (les adversaires
 * réfléchissent hors du fil d'interface, avec un petit délai pour qu'on les suive), les trajets des
 * cartes, la sauvegarde, les stats, la récompense en neutrinos, la fête de victoire.
 *
 * **Sauvegarde** : la graine du mélange et la liste des coups suffisent (voir [CardEngine]) ; à la
 * reprise on rejoue la partie à vide, sans trajets.
 *
 * Tout est lu dans le thème. Rien n'explique les règles à l'écran : elles sont derrière le « i ».
 */
abstract class CardGameActivity : ThemedActivity() {

    /** Un bouton propre au jeu, posé sous la table. */
    class Control(val label: String, val enabled: Boolean = true, val primary: Boolean = false, val action: () -> Unit)

    protected abstract val gameKey: String
    protected abstract val titleRes: Int
    protected abstract val rulesRes: Int

    protected abstract fun createEngine(seed: Long): CardEngine

    /** Ce qu'on voit à cet instant : voir [TableScene]. */
    protected abstract fun buildScene(): TableScene

    /** Le joueur touche une carte de sa main (le jeu décide : soulever, jouer, rien). */
    protected abstract fun onHumanCard(card: PlayingCard)

    /** Le joueur a glissé une carte de sa main vers la table : la jouer, si le jeu le permet. */
    protected open fun onHumanDrop(card: PlayingCard) = onHumanCard(card)

    /** Le joueur touche la pioche. */
    protected open fun onHumanStock() {}

    /** Le joueur touche la défausse. */
    protected open fun onHumanDiscard() {}

    protected open fun rulesText(): CharSequence = getString(rulesRes)

    /** Les boutons du moment (choix de couleur, passer des cartes…). */
    protected open fun controls(): List<Control> = emptyList()

    /** Un petit texte à côté du résultat (la manche, les points…), ou null. */
    protected open fun statusText(): String? = null

    /** Un coup vient d'être joué (le jeu efface ses sélections). */
    protected open fun onMovePlayed() {}

    /** Le joueur a-t-il gagné ? Par défaut : il figure parmi les vainqueurs. */
    protected open fun humanWon(): Boolean = 0 in engine.winners()

    /** Le chiffre à comparer au record de ce jeu, une fois la partie finie, ou null. */
    protected open fun recordValue(): Int? = null
    protected open val lowerRecordIsBetter: Boolean = false

    protected lateinit var engine: CardEngine
        private set
    protected lateinit var palette: KitPalette
        private set
    protected lateinit var table: CardTableView
        private set

    protected var level = CardLevel.MEDIUM
        private set

    /** Les cartes que le joueur a soulevées. Vidée après chaque coup. */
    protected val lifted = LinkedHashSet<PlayingCard>()

    private var seed = 0L
    private val moves = ArrayList<Int>()
    private var finished = false
    private var recorded = false
    /** Le joueur a joué au moins un coup : abandonner la partie compte alors une défaite. */
    private var started = false
    private var generation = 0
    private var aiJob: Job? = null
    /** Entre onResume et onPause : hors de là, les adversaires ne jouent pas. */
    private var active = false

    private lateinit var statusView: TextView
    private lateinit var controlsRow: LinearLayout
    /** Les boutons affichés : on ne refait la rangée que s'ils changent. */
    private var shownControls: List<Control> = emptyList()
    private val levelChips = ArrayList<TextView>()

    private val prefs by lazy { getSharedPreferences("$PREFS_PREFIX$gameKey", MODE_PRIVATE) }

    protected val dp: Float get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = KitPalette.from(this)
        table = CardTableView(this).also {
            it.palette = palette
            it.onCardTap = { card -> if (canAct()) onHumanCard(card) }
            it.onCardDrop = { card -> if (canAct()) onHumanDrop(card) }
            it.onStockTap = { if (canAct()) onHumanStock() }
            it.onDiscardTap = { if (canAct()) onHumanDiscard() }
            it.onIdle = { drive() }
        }
        setContentView(buildScreen())
        enableImmersiveMode()
        if (!restore()) newGame(prefs.getInt(KEY_LEVEL, CardLevel.MEDIUM).coerceIn(0, CardLevel.COUNT - 1))
    }

    override fun onResume() {
        super.onResume()
        active = true
        drive()
    }

    override fun onPause() {
        active = false
        aiJob?.cancel()
        table.skipAnimations()
        save()
        super.onPause()
    }

    // ── Partie ──────────────────────────────────────────────────────────────────────────────

    protected fun newGame(newLevel: Int = level) {
        aiJob?.cancel()
        generation++
        table.skipAnimations()
        // Abandonner une partie commencée compte une défaite (sans neutrinos).
        if (::engine.isInitialized && !finished && !recorded && started) {
            recorded = true
            record(won = false)
        }
        level = newLevel
        seed = Random.nextLong()
        moves.clear()
        lifted.clear()
        finished = false
        recorded = false
        started = false
        engine = createEngine(seed)
        engine.drainEvents()
        table.painter.newBack()
        updateChips()
        table.show(buildScene())
        refresh()
        save()
        drive()
    }

    /** Le joueur peut-il agir : c'est son tour, et plus rien n'est en route sur la table. */
    protected fun canAct(): Boolean = !finished && engine.current == HUMAN && !table.busy

    /** Le joueur joue [move], s'il est permis. */
    protected fun submit(move: Int) {
        if (!canAct() || !engine.isLegal(move)) return
        applyMove(move)
    }

    private fun applyMove(move: Int) {
        val mover = engine.current
        val scoresBefore = engine.scores
        engine.play(move)
        moves.add(move)
        if (mover == HUMAN) started = true
        lifted.clear()
        onMovePlayed()
        val events = engine.drainEvents()
        table.present(buildScene(), events)
        refresh(rebuildScene = false)
        // Chaque coup du joueur est gardé tout de suite (l'appli peut être tuée) ; ceux des adversaires
        // partent avec lui, à la fin d'une manche ou à la pause.
        if (mover == HUMAN || engine.isOver || !engine.scores.contentEquals(scoresBefore)) save()
        drive()
    }

    /**
     * Fait avancer la partie : le prochain adversaire joue, ou on attend le joueur, ou c'est fini.
     * Appelée après chaque coup et à la fin des trajets : un seul adversaire réfléchit à la fois, et
     * jamais pendant qu'une carte voyage.
     */
    private fun drive() {
        if (!::engine.isInitialized || !active || finished || table.busy) return
        if (engine.isOver) { endGame(); return }
        if (engine.current == HUMAN) { refresh(); return }
        if (aiJob?.isActive == true) return
        val snapshot = engine.copy()
        val startedAt = SystemClock.uptimeMillis()
        val lvl = level
        val gen = generation
        aiJob = lifecycleScope.launch {
            val move = withContext(Dispatchers.Default) { snapshot.aiMove(lvl, Random(startedAt xor seed)) }
            val wait = AI_MIN_MS - (SystemClock.uptimeMillis() - startedAt)
            if (wait > 0) delay(wait)
            if (aiJob !== coroutineContext[Job] || gen != generation || finished || !active) return@launch
            // Ce robot a fini : son coup peut relancer le suivant tout de suite (une annonce ou une
            // passe, sans carte qui voyage, ne repasse pas par la fin des trajets).
            aiJob = null
            applyMove(move)
        }
    }

    private fun endGame() {
        finished = true
        val won = humanWon()
        if (!recorded) {
            recorded = true
            record(won)
        }
        refresh()
        if (won) table.celebrate()
        save()
    }

    // ── Stats et récompense ─────────────────────────────────────────────────────────────────

    private fun record(won: Boolean) {
        val reward = if (won) NeutrinoRewards.cards(gameKey, level) else 0
        if (reward > 0) NeutrinoRepository(this).addBalance(reward)
        val value = recordValue()
        prefs.edit {
            putInt(KEY_PLAYED, prefs.getInt(KEY_PLAYED, 0) + 1)
            if (won) {
                putInt(KEY_WON, prefs.getInt(KEY_WON, 0) + 1)
                if (level + 1 > prefs.getInt(KEY_TOP, 0)) putInt(KEY_TOP, level + 1)
            }
            if (value != null) {
                // Rangé à partir de 1 : la synchro ignore les zéros.
                val stored = value + 1
                val old = prefs.getInt(KEY_BEST, 0)
                val better = old == 0 || (if (lowerRecordIsBetter) stored < old else stored > old)
                if (better) putInt(KEY_BEST, stored)
            }
        }
    }

    // ── Sauvegarde ──────────────────────────────────────────────────────────────────────────

    private fun save() {
        if (!::engine.isInitialized) return
        prefs.edit {
            putLong(KEY_SEED, seed)
            putInt(KEY_LEVEL, level)
            putString(KEY_MOVES, moves.joinToString(","))
            putBoolean(KEY_RECORDED, recorded)
            putBoolean(KEY_STARTED, started)
            putBoolean(KEY_SAVED, true)
        }
    }

    private fun restore(): Boolean {
        if (!prefs.getBoolean(KEY_SAVED, false)) return false
        val savedSeed = prefs.getLong(KEY_SEED, 0L)
        val savedMoves = prefs.getString(KEY_MOVES, "").orEmpty().split(",").filter { it.isNotEmpty() }
        return try {
            val e = createEngine(savedSeed)
            for (m in savedMoves) e.play(m.toInt())
            e.drainEvents()
            engine = e
            seed = savedSeed
            level = prefs.getInt(KEY_LEVEL, CardLevel.MEDIUM).coerceIn(0, CardLevel.COUNT - 1)
            moves.clear()
            moves.addAll(savedMoves.map { it.toInt() })
            recorded = prefs.getBoolean(KEY_RECORDED, false)
            started = prefs.getBoolean(KEY_STARTED, false)
            finished = engine.isOver
            table.painter.newBack()
            updateChips()
            table.show(buildScene())
            // Une partie finie pendant le dernier trajet puis quittée : elle compte, sans la fête.
            if (finished && !recorded) {
                recorded = true
                record(humanWon())
                save()
            }
            refresh()
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── Affichage ───────────────────────────────────────────────────────────────────────────

    /** Remet à jour tout ce qui entoure la table ; la table elle-même seulement si [rebuildScene]. */
    protected fun refresh(rebuildScene: Boolean = true) {
        if (!::engine.isInitialized || !::controlsRow.isInitialized) return
        if (rebuildScene) table.update(buildScene())
        updateStatus()
        updateControls()
    }

    private fun updateStatus() {
        val parts = ArrayList<String>()
        if (finished) parts.add(getString(if (humanWon()) R.string.cards_result_won else R.string.cards_result_lost))
        statusText()?.let { parts.add(it) }
        statusView.text = parts.joinToString(getString(R.string.kit_separator))
        statusView.setTextColor(if (finished) palette.ink else palette.secondary)
    }

    private fun updateControls() {
        val wanted = ArrayList<Control>()
        if (!finished && engine.current == HUMAN && !table.busy) wanted.addAll(controls())
        wanted.add(Control(getString(R.string.kit_new), primary = finished) { askNewGame() })
        val old = shownControls
        val same = old.size == wanted.size && controlsRow.childCount == wanted.size && wanted.indices.all {
            old[it].label == wanted[it].label && old[it].enabled == wanted[it].enabled && old[it].primary == wanted[it].primary
        }
        if (same) {
            // Les mêmes boutons : seule leur action change (elle capture l'état du moment).
            for (i in wanted.indices) {
                val c = wanted[i]
                controlsRow.getChildAt(i).setOnClickListener { c.action() }
            }
        } else {
            controlsRow.removeAllViews()
            for (c in wanted) controlsRow.addView(button(c.label, c.primary, c.enabled, c.action), buttonParams())
        }
        shownControls = wanted
    }

    private fun askNewGame() {
        if (finished || moves.isEmpty()) { newGame(); return }
        ImmersiveAlertDialogBuilder(this)
            .setMessage(R.string.cards_new_confirm)
            .setPositiveButton(R.string.ok) { _, _ -> newGame() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun buttonParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginStart = (3 * dp).toInt()
        marginEnd = (3 * dp).toInt()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppearanceStyle.screen(this@CardGameActivity) ?: GradientDrawable().apply {
                setColor(palette.background)
            }
            setTag(R.id.app_effects_scene, true)
        }

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

        // Les trois niveaux.
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * dp).toInt(), (2 * dp).toInt(), (12 * dp).toInt(), (2 * dp).toInt())
        }
        val labels = intArrayOf(R.string.kit_easy, R.string.kit_medium, R.string.kit_hard)
        for (i in 0 until CardLevel.COUNT) {
            val chip = TextView(this).apply {
                setText(labels[i])
                textSize = 13f
                gravity = Gravity.CENTER
                minWidth = (56 * dp).toInt()
                setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
                setOnClickListener { chooseLevel(i) }
            }
            levelChips.add(chip)
            chips.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = (6 * dp).toInt() })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })

        statusView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(palette.secondary)
            setPadding(0, (6 * dp).toInt(), 0, (2 * dp).toInt())
        }
        root.addView(statusView)

        table.setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
        root.addView(table, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
        }
        root.addView(controlsRow)
        return root
    }

    private fun chooseLevel(newLevel: Int) {
        if (newLevel == level) return
        if (finished || moves.isEmpty()) { newGame(newLevel); return }
        ImmersiveAlertDialogBuilder(this)
            .setMessage(R.string.cards_new_confirm)
            .setPositiveButton(R.string.ok) { _, _ -> newGame(newLevel) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRules() {
        showGameRules(titleRes, rulesText())
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

    /** Bouton aux couleurs du thème : plein pour l'action principale, en relief sinon. */
    private fun button(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            insetTop = 0
            insetBottom = 0
            minHeight = (48 * dp).toInt()
            setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            cornerRadius = AppearanceStyle.corner(this@CardGameActivity, 14f).toInt()
            val bg = if (primary) palette.accent else palette.raised
            val fg = if (primary) palette.onAccent else palette.text
            backgroundTintList = ColorStateList.valueOf(bg)
            setTextColor(fg)
            strokeColor = ColorStateList.valueOf(if (primary) palette.accent else palette.outline)
            strokeWidth = if (primary) 0 else dp.toInt().coerceAtLeast(1)
            elevation = 0f
            stateListAnimator = null
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            setOnClickListener { onClick() }
        }

    private fun updateChips() {
        levelChips.forEachIndexed { i, chip ->
            val selected = i == level
            chip.background = GradientDrawable().apply {
                cornerRadius = AppearanceStyle.corner(this@CardGameActivity, 16f)
                setColor(if (selected) palette.accent else palette.raised)
                setStroke(dp.toInt().coerceAtLeast(1), if (selected) palette.accent else palette.outline)
            }
            chip.setTextColor(if (selected) palette.onAccent else palette.text)
        }
    }

    // ── Aides pour les jeux ─────────────────────────────────────────────────────────────────

    /** Le nom du siège, selon le nombre de joueurs (toi en bas, puis dans le sens des aiguilles d'une montre). */
    protected fun seatName(seat: Int, seats: Int): String = getString(when {
        seat == 0 -> R.string.cards_seat_you
        seats == 2 -> R.string.cards_seat_north
        seats == 3 -> if (seat == 1) R.string.cards_seat_west else R.string.cards_seat_east
        else -> when (seat) { 1 -> R.string.cards_seat_west; 2 -> R.string.cards_seat_north; else -> R.string.cards_seat_east }
    })

    /** La carte qu'un coup fait jouer, ou null (piocher, passer, choisir une couleur…). */
    protected open fun cardOfMove(move: Int): PlayingCard? = if (move in 0..51) PlayingCard(move) else null

    /** Les cartes de la main que le joueur peut jouer maintenant, pour griser les autres ; null hors de son tour. */
    protected fun playableCards(): Set<PlayingCard>? {
        if (finished || engine.current != HUMAN) return null
        return engine.legalMoves().toList().mapNotNull { cardOfMove(it) }.toSet()
    }

    companion object {
        const val HUMAN = 0
        private const val AI_MIN_MS = 650L
        private const val KEY_SEED = "seed"
        private const val KEY_LEVEL = "level"
        private const val KEY_MOVES = "moves"
        private const val KEY_RECORDED = "recorded"
        private const val KEY_STARTED = "started"
        private const val KEY_SAVED = "saved"
        // Lues aussi par la page des stats de jeux (ClickerStatsActivity) et synchronisées sur le
        // Drive (SharedGameStats, GamesSyncFile) : parties jouées et gagnées, plus haut niveau
        // battu (numéroté à partir de 1, car la synchro ignore les zéros), meilleur résultat.
        const val PREFS_PREFIX = "cards_"
        const val KEY_PLAYED = "played"
        const val KEY_WON = "won"
        const val KEY_TOP = "top_level"
        const val KEY_BEST = "best"
    }
}
