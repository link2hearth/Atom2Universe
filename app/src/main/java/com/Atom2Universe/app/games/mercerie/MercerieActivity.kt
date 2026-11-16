package com.Atom2Universe.app.games.mercerie

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.Atom2Universe.app.crypto.clicker.NeutrinoRepository
import com.Atom2Universe.app.crypto.clicker.NeutrinoRewards
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.updateSystemBarsVisibility

/**
 * Bobine & Boutons : un jeu d'alignements en feutrine. Trois écrans dans la même activité :
 * l'accueil, la carte des niveaux et la partie ; les fiches (niveau, pause, fin, aide) se posent
 * par-dessus. Chaque écran a sa mise en page debout et sa mise en page couchée.
 */
class MercerieActivity : ThemedActivity() {
    private enum class Screen { HOME, MAP, GAME }

    private lateinit var root: FrameLayout
    private var screen = Screen.HOME
    private var card: View? = null
    private val progress by lazy { MercerieProgress(this) }
    private val workshop by lazy { MercerieWorkshop(this) }
    /** L'étage que l'atelier est en train de préparer pour le joueur, ou 0. */
    private var awaited = 0
    private val sound = MercerieSound()
    private var iconCache: MercerieSprites? = null
    /** Les petites images des objectifs et de l'aide, refaites quand le mode change. */
    private val icons: MercerieSprites get() = iconCache ?: MercerieSprites(assets, dp(44f)).also { iconCache = it }

    private var boardView: MercerieBoardView? = null
    private var movesText: TextView? = null
    private var scoreText: TextView? = null
    private var starBar: StarBarView? = null
    private var mascot: MascotView? = null
    private var mood = 0
    private val chipTexts = ArrayList<Pair<Chip, TextView>>()

    /** Un objectif à l'écran : une image et un nombre. */
    private class Chip(val icon: Bitmap, val target: Int, val left: (MercerieBoard) -> Int)

    private val landscape: Boolean get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        updateSystemBarsVisibility()
        Felt.theme(progress.dark)
        root = FrameLayout(this).apply { background = FabricDrawable(this@MercerieActivity) }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        showHome()
    }

    override fun onResume() { super.onResume(); sound.start() }
    override fun onPause() { sound.stop(); super.onPause() }

    /** La palette est commune à tout le processus : la tuile du hub se dessine en clair. */
    override fun onDestroy() { Felt.theme(false); super.onDestroy() }

    /** La bascule de l'accueil : tout le jeu change de feutrine, tout de suite. */
    private fun setDark(dark: Boolean) {
        progress.dark = dark
        Felt.theme(dark)
        iconCache = null
        starIconCache = null
        root.background = FabricDrawable(this)
        if (screen == Screen.HOME && card == null) showHome()
    }

    /** L'écran a tourné : même écran, même partie, même fiche, dans l'autre mise en page. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val open = card
        open?.let { root.removeView(it) }
        when (screen) {
            Screen.HOME -> showHome()
            Screen.MAP -> showMap()
            Screen.GAME -> boardView?.let { layoutGame(it) }
        }
        if (open != null) {
            card = open
            root.addView(open, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun dp(value: Float) = MercerieUi.dp(this, value).toInt()

    private fun back() {
        when {
            // Fin de partie : le bouton retour fait comme « Quitter ».
            card != null && screen == Screen.GAME && boardView?.board?.finished == true -> showHome()
            card != null -> closeCard()
            screen == Screen.GAME -> showPause()
            screen == Screen.MAP -> showHome()
            else -> finish()
        }
    }

    private fun setScreen(next: Screen, content: View) {
        screen = next
        card = null
        root.removeAllViews()
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    private fun leaveGame() {
        boardView = null
        mascot = null
        chipTexts.clear()
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun LinearLayout.add(view: View, width: Int = -2, height: Int = -2, top: Float = 0f, weight: Float = 0f) {
        addView(view, LinearLayout.LayoutParams(width, height, weight).apply { topMargin = dp(top) })
    }

    private fun spacer() = View(this)

    private fun button(text: Int, color: Int = Felt.PINK, big: Boolean = false, action: () -> Unit) =
        FeltButton(this, getString(text), color, big).apply { setOnClickListener { sound.tick(); action() } }

    private fun roundButton(text: Int, action: () -> Unit) =
        FeltButton(this, getString(text), Felt.PAPER, round = true).apply { setOnClickListener { sound.tick(); action() } }

    /** Barre du haut : retour à gauche, aide à droite, titre au milieu ; [extra] se range à gauche de l'aide. */
    private fun topBar(title: String?, extra: View? = null, onBack: () -> Unit): View = FrameLayout(this).apply {
        setPadding(dp(12f), dp(10f), dp(12f), 0)
        addView(roundButton(R.string.mercerie_sym_back, onBack), FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))
        if (title != null) addView(MercerieUi.label(context, 22f).apply { text = title }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        addView(roundButton(R.string.mercerie_sym_help) { showHelp() }, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        if (extra != null) addView(extra, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(66f) })
    }

    private fun darkSwitch(): View = FeltSwitch(this, progress.dark) { sound.tick(); setDark(it) }.apply {
        contentDescription = getString(R.string.mercerie_dark_mode)
    }

    /** Étiquette : le total des étoiles et des points. */
    private fun totals(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = FeltDrawable(context, Felt.PAPER, 22f, threadColor = Felt.KRAFT)
        setPadding(dp(20f), dp(10f), dp(20f), dp(14f))
        addView(MercerieUi.label(context, 17f, Felt.darker(Felt.GOLD, .8f)).apply {
            text = getString(R.string.mercerie_stars_total, progress.totalStars)
        })
        addView(MercerieUi.label(context, 17f).apply {
            text = getString(R.string.mercerie_points, MercerieUi.number(progress.totalScore))
            setPadding(dp(18f), 0, 0, 0)
        })
    }

    // ───────────────────────────── Accueil ─────────────────────────────

    private fun showHome() {
        leaveGame()
        val next = progress.next
        workshop.warm(next)
        val pique = MascotView(this, Mascot.random())
        pique.setOnClickListener { pique.mood = 1; sound.special(); pique.postDelayed({ pique.mood = 0 }, 1200L) }
        val title = FeltTitleView(this, getString(R.string.mercerie_logo))
        val play = button(R.string.mercerie_play, big = true) { startLevel(next) }
        val level = MercerieUi.label(this, 16f).apply { text = getString(R.string.mercerie_level, next) }
        val levels = button(R.string.mercerie_levels, Felt.MINT) { showMap() }

        if (landscape) {
            // Couché : le titre et Pique à gauche, les boutons à droite.
            val left = column().apply { gravity = Gravity.CENTER }
            left.add(title, width = -1)
            left.add(pique, width = dp(190f), height = dp(190f))
            val right = column().apply { gravity = Gravity.CENTER }
            right.add(play)
            right.add(level, top = 10f)
            right.add(totals(), top = 26f)
            right.add(levels, top = 14f)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(24f), dp(56f), dp(24f), dp(8f))
                addView(left, LinearLayout.LayoutParams(0, -1, 1.15f))
                addView(right, LinearLayout.LayoutParams(0, -1, 1f))
            }
            val frame = FrameLayout(this)
            frame.addView(BuntingView(this), FrameLayout.LayoutParams(-1, dp(56f), Gravity.TOP))
            frame.addView(row, FrameLayout.LayoutParams(-1, -1))
            frame.addView(topBar(null, darkSwitch()) { finish() }, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            setScreen(Screen.HOME, frame)
            return
        }
        val content = column()
        content.add(topBar(null, darkSwitch()) { finish() }, width = -1)
        content.add(BuntingView(this), width = -1, height = dp(70f))
        content.add(spacer(), height = 0, weight = .6f)
        content.add(title, width = -1)
        content.add(pique, width = dp(210f), height = dp(210f), top = 4f)
        content.add(spacer(), height = 0, weight = .5f)
        content.add(play)
        content.add(level, top = 10f)
        content.add(spacer(), height = 0, weight = .7f)
        content.add(totals())
        content.add(levels, top = 14f)
        content.add(spacer(), height = 0, weight = 1f)
        setScreen(Screen.HOME, content)
    }

    // ───────────────────────────── Carte ─────────────────────────────

    private fun showMap() {
        leaveGame()
        workshop.warm(progress.next)
        val content = column()
        content.add(topBar(getString(R.string.mercerie_levels)) { showHome() }, width = -1)
        content.add(totals(), top = if (landscape) 0f else 10f)
        val map = MercerieMapView(this, progress) { showLevelCard(it) }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            addView(map, ViewGroup.LayoutParams(-1, -2))
        }
        content.add(scroll, width = -1, height = 0, top = 8f, weight = 1f)
        setScreen(Screen.MAP, content)
        // La carte s'ouvre sur l'étage en cours, au milieu de l'écran.
        scroll.post { scroll.scrollTo(0, (map.levelY(progress.next) - scroll.height / 2).toInt().coerceAtLeast(0)) }
    }

    // ───────────────────────────── Objectifs ─────────────────────────────

    private fun chips(l: MercerieLevel): List<Chip> {
        val out = ArrayList<Chip>()
        val g = l.goal
        for (c in 0 until MercerieBoard.COLORS) if (g.collect[c] > 0)
            out += Chip(icons.of(Kind.NORMAL, c), g.collect[c]) { b -> (g.collect[c] - b.collected[c]).coerceAtLeast(0) }
        if (g.patches) out += Chip(icons.patch[0], MercerieBoard(l, 1L).patchesLeft) { it.patchesLeft }
        if (g.thimbles > 0) out += Chip(icons.thimble, g.thimbles) { b -> g.thimbles - b.thimblesDone }
        if (g.onlyScore) out += Chip(starIcon, l.stars[0]) { b -> (l.stars[0] - b.score).coerceAtLeast(0) }
        return out
    }

    private var starIconCache: Bitmap? = null
    private val starIcon: Bitmap get() = starIconCache ?: Bitmap.createBitmap(dp(44f), dp(44f), Bitmap.Config.ARGB_8888).also {
        Felt.star(android.graphics.Canvas(it), it.width / 2f, it.height / 2f, it.width * .36f, true)
        starIconCache = it
    }

    /** Une rangée d'objectifs. Avec [live], les nombres suivront la partie. */
    private fun goalRow(l: MercerieLevel, board: MercerieBoard?, live: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        for (chip in chips(l)) {
            val count = MercerieUi.label(context, if (live) 16f else 18f)
            count.text = chipText(chip, board)
            if (live) chipTexts += chip to count
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8f), 0, dp(8f), 0)
                addView(ImageView(context).apply { setImageBitmap(chip.icon) }, LinearLayout.LayoutParams(dp(if (live) 40f else 48f), dp(if (live) 40f else 48f)))
                addView(count)
            })
        }
    }

    private fun chipText(chip: Chip, board: MercerieBoard?): String {
        val left = if (board == null) chip.target else chip.left(board)
        return if (left <= 0) getString(R.string.mercerie_sym_done) else MercerieUi.number(left)
    }

    // ───────────────────────────── Fiches ─────────────────────────────

    private fun closeCard() {
        card?.let { root.removeView(it) }
        card = null
    }

    /** Pose une fiche au milieu de l'écran, sur un voile. [dismissable] : toucher à côté la referme. */
    private fun showCard(dismissable: Boolean, build: LinearLayout.() -> Unit) {
        closeCard()
        val panel = column().apply {
            background = FeltDrawable(context, Felt.PAPER, 34f, threadColor = Felt.PINK)
            setPadding(dp(30f), dp(26f), dp(30f), dp(30f))
            isClickable = true
            build()
        }
        val veil = FrameLayout(this).apply {
            setBackgroundColor(Felt.VEIL)
            isClickable = true
            if (dismissable) setOnClickListener { closeCard() }
            addView(panel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply {
                leftMargin = dp(18f); rightMargin = dp(18f); topMargin = dp(10f); bottomMargin = dp(10f)
            })
        }
        card = veil
        root.addView(veil, FrameLayout.LayoutParams(-1, -1))
        panel.scaleX = .8f; panel.scaleY = .8f; panel.alpha = 0f
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
    }

    private fun LinearLayout.buttons(vararg items: View) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for ((n, b) in items.withIndex()) row.addView(b, LinearLayout.LayoutParams(-2, -2).apply { if (n > 0) leftMargin = dp(12f) })
        add(row, top = 20f)
    }

    /**
     * Un étage sans fin n'est pas toujours prêt : le robot doit d'abord le régler. Pique sautille
     * sur une fiche pendant ce temps, puis [then] reçoit le niveau, si le joueur l'attend encore.
     */
    private fun withLevel(number: Int, then: (MercerieLevel) -> Unit) {
        workshop.peek(number)?.let { awaited = 0; then(it); return }
        awaited = number
        showCard(dismissable = true) {
            add(MercerieUi.label(context, 26f, Felt.PINK).apply { text = getString(R.string.mercerie_level, number) })
            add(MascotView(context, Mascot.random()).apply { mood = 1 }, width = dp(130f), height = dp(130f), top = 6f)
        }
        val shown = card
        workshop.request(number) { level ->
            if (awaited == number && card === shown) { awaited = 0; then(level) }
        }
    }

    private fun showLevelCard(number: Int) = withLevel(number) { l ->
        showCard(dismissable = true) {
            add(MercerieUi.label(context, 26f, Felt.PINK).apply { text = getString(R.string.mercerie_level, number) })
            add(StarsView(context, progress.stars(number), animated = false), width = dp(170f), height = dp(60f), top = 6f)
            add(goalRow(l, null, live = false), top = 8f)
            add(MercerieUi.label(context, 16f).apply { text = resources.getQuantityString(R.plurals.mercerie_moves, l.moves, l.moves) }, top = 10f)
            val best = progress.best(number)
            if (best > 0) add(MercerieUi.label(context, 15f, Felt.SOFT).apply {
                text = getString(R.string.mercerie_best, MercerieUi.number(best))
            }, top = 6f)
            buttons(button(R.string.mercerie_play, big = true) { play(l) })
        }
    }

    private fun showPause() {
        val number = boardView?.board?.level?.number ?: return
        showCard(dismissable = true) {
            add(MercerieUi.label(context, 26f, Felt.PINK).apply { text = getString(R.string.mercerie_pause) })
            buttons(button(R.string.mercerie_resume) { closeCard() })
            buttons(
                button(R.string.mercerie_restart, Felt.MINT) { startLevel(number) },
                button(R.string.mercerie_levels, Felt.MINT) { showMap() }
            )
        }
    }

    /** Bascule oui / non : le coup possible qui bat doucement quand on ne joue pas. */
    private fun hintToggle(): View {
        val toggle = FeltButton(this, "")
        fun paint() {
            val on = progress.hints
            toggle.text = getString(if (on) R.string.mercerie_on else R.string.mercerie_off)
            toggle.background = FeltDrawable(this, if (on) Felt.MINT else Felt.GREY, 28f)
        }
        toggle.setOnClickListener {
            sound.tick()
            progress.hints = !progress.hints
            boardView?.hints = progress.hints
            paint()
        }
        paint()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(MercerieUi.label(context, 16f).apply {
                text = getString(R.string.mercerie_hint)
                gravity = Gravity.START
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(toggle, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12f) })
        }
    }

    private fun showHelp() {
        val rows = listOf(
            icons.of(Kind.LINE_H, 0) to R.string.mercerie_help_line,
            icons.of(Kind.BOMB, 1) to R.string.mercerie_help_bomb,
            icons.rainbow to R.string.mercerie_help_rainbow,
            icons.patch[0] to R.string.mercerie_help_patch,
            icons.knot[0] to R.string.mercerie_help_knot,
            icons.pin to R.string.mercerie_help_pin,
            icons.thimble to R.string.mercerie_help_thimble
        )
        showCard(dismissable = true) {
            add(MercerieUi.label(context, 24f, Felt.PINK).apply { text = getString(R.string.mercerie_help_title) })
            add(hintToggle(), width = dp(300f), top = 10f)
            val list = column().apply { gravity = Gravity.START }
            for ((icon, textRes) in rows) list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6f), 0, dp(6f))
                addView(ImageView(context).apply { setImageBitmap(icon) }, LinearLayout.LayoutParams(dp(44f), dp(44f)))
                addView(MercerieUi.label(context, 15f).apply {
                    text = getString(textRes)
                    gravity = Gravity.START
                    setPadding(dp(12f), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, -2, 1f))
            })
            list.addView(MercerieUi.label(context, 15f).apply {
                text = getString(R.string.mercerie_help_combo)
                gravity = Gravity.START
                setPadding(0, dp(8f), 0, 0)
            })
            val scroll = ScrollView(context).apply { addView(list) }
            // La liste prend la place qui reste : en paysage, elle défile dans la fiche.
            add(scroll, width = dp(300f), height = 0, top = 10f, weight = 1f)
            buttons(button(R.string.close) { closeCard() })
        }
    }

    // ───────────────────────────── Partie ─────────────────────────────

    private fun startLevel(number: Int) = withLevel(number) { play(it) }

    private fun play(l: MercerieLevel) {
        // L'étage d'après se prépare pendant que celui-ci se joue.
        workshop.warm(l.number + 1)
        val view = MercerieBoardView(this, MercerieBoard(l), sound)
        view.hints = progress.hints
        mood = 0
        view.listener = object : MercerieBoardView.Listener {
            override fun onChanged() = refreshHud()
            override fun onBonus() { setMood(1); view.showCheer(getString(R.string.mercerie_bonus)) }
            override fun onFinished(won: Boolean) = finishLevel(won)
        }
        layoutGame(view)
    }

    private fun setMood(value: Int) {
        mood = value
        mascot?.mood = value
    }

    /**
     * Range la partie à l'écran autour du plateau [view]. Debout : compteurs en bandeau au-dessus,
     * Pique en dessous. Couché : compteurs dans une colonne à gauche, le plateau prend toute la
     * hauteur. Le plateau lui-même n'est pas recréé : une rotation ne coupe pas la partie.
     */
    private fun layoutGame(view: MercerieBoardView) {
        (view.parent as? ViewGroup)?.removeView(view)
        val board = view.board
        val l = board.level
        chipTexts.clear()
        mascot = null
        val wide = landscape

        val moves = MercerieUi.label(this, if (wide) 34f else 28f, Felt.CREAM)
        val movesBox = column().apply {
            gravity = Gravity.CENTER
            background = FeltDrawable(context, Felt.PINK, 30f)
            setPadding(dp(14f), dp(8f), dp(14f), dp(12f))
            minimumWidth = dp(if (wide) 96f else 74f)
            addView(moves)
            addView(MercerieUi.label(context, if (wide) 13f else 11f, Felt.CREAM).apply { text = getString(R.string.mercerie_moves_label) })
        }
        val score = MercerieUi.label(this, if (wide) 24f else 20f)
        val bar = StarBarView(this, l.stars)
        val scoreBox = column().apply {
            gravity = Gravity.CENTER
            addView(score)
            addView(bar, LinearLayout.LayoutParams(dp(if (wide) 150f else 118f), dp(if (wide) 36f else 30f)).apply { topMargin = dp(4f) })
        }
        val goals = goalRow(l, board, live = true)
        val title = getString(R.string.mercerie_level, l.number)

        val content: View
        if (wide) {
            val side = column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                background = FeltDrawable(context, Felt.PAPER, 30f, threadColor = Felt.KRAFT)
                setPadding(dp(14f), dp(14f), dp(14f), dp(18f))
            }
            val corner = FrameLayout(this).apply {
                addView(roundButton(R.string.mercerie_sym_back) { showPause() }, FrameLayout.LayoutParams(-2, -2, Gravity.START))
                addView(roundButton(R.string.mercerie_sym_help) { showHelp() }, FrameLayout.LayoutParams(-2, -2, Gravity.END))
            }
            side.add(corner, width = -1)
            side.add(spacer(), height = 0, weight = .6f)
            side.add(MercerieUi.label(this, 22f, Felt.PINK).apply { text = title })
            side.add(spacer(), height = 0, weight = .6f)
            side.add(movesBox)
            side.add(spacer(), height = 0, weight = 1f)
            side.add(goals)
            side.add(spacer(), height = 0, weight = 1f)
            side.add(scoreBox)
            side.add(spacer(), height = 0, weight = .6f)
            content = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(side, LinearLayout.LayoutParams(dp(232f), -1).apply {
                    leftMargin = dp(10f); topMargin = dp(10f); bottomMargin = dp(10f)
                })
                addView(view, LinearLayout.LayoutParams(0, -1, 1f))
            }
        } else {
            val column = column()
            column.add(topBar(title) { showPause() }, width = -1)
            val hud = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = FeltDrawable(context, Felt.PAPER, 30f, threadColor = Felt.KRAFT)
                setPadding(dp(14f), dp(10f), dp(16f), dp(14f))
                addView(movesBox)
                addView(goals, LinearLayout.LayoutParams(0, -2, 1f))
                addView(scoreBox)
            }
            column.addView(hud, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f); leftMargin = dp(12f); rightMargin = dp(12f) })
            column.add(view, width = -1, height = 0, weight = 1f)
            val pique = MascotView(this, Mascot.of(l))
            pique.mood = mood
            column.add(pique, width = dp(92f), height = dp(92f))
            column.add(spacer(), height = dp(6f))
            mascot = pique
            content = column
        }
        setScreen(Screen.GAME, content)
        boardView = view
        movesText = moves; scoreText = score; starBar = bar
        refreshHud()
    }

    private fun refreshHud() {
        val board = boardView?.board ?: return
        movesText?.text = MercerieUi.number(board.movesLeft)
        scoreText?.text = MercerieUi.number(board.score)
        starBar?.score = board.score
        for ((chip, view) in chipTexts) view.text = chipText(chip, board)
    }

    private fun finishLevel(won: Boolean) {
        val board = boardView?.board ?: return
        val l = board.level
        setMood(if (won) 1 else -1)
        if (!won) {
            sound.lose()
            root.postDelayed({
                if (screen != Screen.GAME || boardView?.board !== board) return@postDelayed
                showCard(dismissable = false) {
                    add(MercerieUi.label(context, 26f, Felt.PINK).apply { text = getString(R.string.mercerie_lose) })
                    add(goalRow(l, board, live = false), top = 12f)
                    buttons(button(R.string.mercerie_retry) { startLevel(l.number) })
                    buttons(button(R.string.mercerie_quit, Felt.PAPER) { showHome() })
                }
            }, 500L)
            return
        }
        val stars = board.stars
        val record = progress.record(l.number, board.score, stars)
        val neutrinos = NeutrinoRewards.mercerie(l.number, record.firstTime)
        if (neutrinos > 0) NeutrinoRepository(this).addBalance(neutrinos)
        sound.win(stars)
        root.postDelayed({
            if (screen != Screen.GAME || boardView?.board !== board) return@postDelayed
            showCard(dismissable = false) {
                add(MercerieUi.label(context, 28f, Felt.PINK).apply { text = getString(R.string.mercerie_win) })
                add(StarsView(context, stars), width = dp(210f), height = dp(78f), top = 6f)
                add(MercerieUi.label(context, 30f).apply { text = MercerieUi.number(board.score) }, top = 4f)
                add(MercerieUi.label(context, 15f, Felt.SOFT).apply {
                    text = if (record.newBest) getString(R.string.mercerie_new_best)
                    else getString(R.string.mercerie_best, MercerieUi.number(progress.best(l.number)))
                }, top = 4f)
                if (neutrinos > 0) add(MercerieUi.label(context, 15f, if (Felt.dark) Felt.lighter(Felt.COLORS[1], .45f) else Felt.darker(Felt.COLORS[1], .8f)).apply {
                    text = getString(R.string.mercerie_neutrinos, neutrinos)
                }, top = 6f)
                // Rejouer à gauche, la suite à droite.
                buttons(
                    button(R.string.mercerie_retry, Felt.MINT) { startLevel(l.number) },
                    button(R.string.mercerie_next) { startLevel(l.number + 1) }
                )
                buttons(button(R.string.mercerie_quit, Felt.PAPER) { showHome() })
            }
        }, 600L)
    }
}
