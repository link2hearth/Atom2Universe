package com.Atom2Universe.app.games.mercerie

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
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
import com.Atom2Universe.app.util.FitFrame
import com.Atom2Universe.app.util.enableImmersiveMode
import com.Atom2Universe.app.util.updateSystemBarsVisibility

/**
 * Bobine & Boutons : un jeu d'alignements en feutrine. Trois écrans dans la même activité :
 * l'accueil, la carte des niveaux et la partie ; les fiches (niveau, pause, fin, aide) se posent
 * par-dessus. Chaque écran a sa mise en page debout et sa mise en page couchée.
 */
class MercerieActivity : ThemedActivity() {
    private enum class Screen { HOME, MAP, GAME, TUTORIAL }

    private lateinit var root: FrameLayout
    private var screen = Screen.HOME
    private var card: View? = null
    /** Ce qui s'anime au repos sur l'écran en cours (mascotte, titre, plateau, carte) : se calme sous une fiche. */
    private val lively = ArrayList<Coverable>()
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
    private val chipViews = HashMap<Int, View>()
    /** Pièces en vol vers chaque compteur : le nombre affiché ne baisse qu'à leur arrivée. */
    private val inFlight = HashMap<Int, Int>()
    /** Pendant la couture finale, le compteur de coups descend au rythme des coups qui partent. */
    private var shownMoves = -1
    private var movesBox: View? = null
    private var flights: FlightLayer? = null
    /** La médaille du défi pendant la partie : son image et les coups du défi qui restent. */
    private var parIcon: ImageView? = null
    private var parText: TextView? = null
    private var parGold = true

    /** Le tutoriel : ses tableaux, celui qu'on joue, le coup attendu, et l'écran où revenir après. */
    private var lessons: List<Lesson> = emptyList()
    private var lessonAt = 0
    private var lessonStep = 0
    private var tutorView: MercerieBoardView? = null
    private var tutorText: TextView? = null
    private var tutorButtons: View? = null
    private var tutorFrom = Screen.HOME
    /** La démonstration est finie : le plateau est libre, à toi de refaire la même chose. */
    private var practicing = false
    /** L'exercice est réussi (ou il n'y en a pas) : jeu libre. */
    private var practiceDone = false
    /** Le coup en train de se jouer refait l'exercice : bravo quand il retombe. */
    private var practiceHit = false

    /** Un objectif à l'écran : une image et un nombre. [key] : sa couleur, ou KEY_PATCH… du plateau. */
    private class Chip(val icon: Bitmap, val target: Int, val key: Int, val left: (MercerieBoard) -> Int)

    private val landscape: Boolean get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        updateSystemBarsVisibility()
        Felt.theme(progress.dark)
        sound.muted = progress.muted
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

    /** La bascule jour / nuit de la barre du haut : tout le jeu change de feutrine, même en pleine partie. */
    private fun setDark(dark: Boolean) {
        progress.dark = dark
        Felt.theme(dark)
        iconCache = null
        starIconCache = null
        medalCache.clear()
        timerIconCache = null
        portalIconCache = null
        root.background = FabricDrawable(this)
        if (card != null) return
        when (screen) {
            Screen.HOME -> showHome()
            Screen.MAP -> showMap()
            Screen.GAME -> boardView?.let { it.restyle(); layoutGame(it) }
            Screen.TUTORIAL -> tutorView?.let { it.restyle(); layoutTutorial() }
        }
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
            Screen.TUTORIAL -> layoutTutorial()
        }
        if (open != null) {
            card = open
            root.addView(open, FrameLayout.LayoutParams(-1, -1))
            cover(true)
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
            screen == Screen.TUTORIAL -> leaveTutorial()
            else -> finish()
        }
    }

    private fun setScreen(next: Screen, content: View) {
        screen = next
        card = null
        root.removeAllViews()
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        lively.clear()
        collectLively(content)
        // La couche de vol passe au-dessus de tout, fiches comprises, sans jamais prendre le doigt.
        val layer = flights ?: FlightLayer(this).also { flights = it; it.translationZ = 1000f }
        layer.clear()
        root.addView(layer, FrameLayout.LayoutParams(-1, -1))
    }

    private fun collectLively(view: View) {
        if (view is Coverable) lively += view
        if (view is ViewGroup) for (i in 0 until view.childCount) collectLively(view.getChildAt(i))
    }

    /** Une fiche se pose ([on]) ou s'en va : l'écran dessous cesse ou reprend de s'animer au repos. */
    private fun cover(on: Boolean) {
        for (v in lively) v.covered = on
    }

    private fun leaveGame() {
        boardView = null
        mascot = null
        chipTexts.clear()
        chipViews.clear()
        inFlight.clear()
        movesBox = null
        shownMoves = -1
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

    /**
     * Barre du haut : retour à gauche, suivi du titre ; à droite, de gauche à droite, la bascule
     * jour / nuit, le haut-parleur et l'aide. Le titre prend la place entre les deux et rapetisse
     * sur un téléphone étroit plutôt que de passer sous les boutons de droite.
     */
    private fun topBar(title: String?, onBack: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12f), dp(10f), dp(12f), 0)
        addView(roundButton(R.string.mercerie_sym_back, onBack))
        val label = MercerieUi.label(context, 22f).apply {
            text = title.orEmpty()
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(dp(11f), textSize.toInt(), 1, TypedValue.COMPLEX_UNIT_PX)
        }
        addView(label, LinearLayout.LayoutParams(0, dp(52f), 1f).apply { leftMargin = dp(14f); rightMargin = dp(8f) })
        addView(rightButtons())
    }

    /** Bascule jour / nuit, haut-parleur et aide, côte à côte. */
    private fun rightButtons(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(darkSwitch())
        addView(speaker(), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6f) })
        addView(roundButton(R.string.mercerie_sym_help) { showHelp() }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8f) })
    }

    /** Le petit haut-parleur rond, à gauche du « ? » : il coupe ou rend les sons. */
    private fun speaker(): View {
        val icon = SpeakerDrawable(this, !progress.muted)
        return FeltButton(this, "", Felt.PAPER, round = true).apply {
            foreground = icon
            foregroundGravity = Gravity.CENTER
            contentDescription = getString(R.string.mercerie_sound)
            setOnClickListener {
                val on = progress.muted
                progress.muted = !on
                sound.muted = !on
                icon.on = on
                invalidate()
                if (on) sound.tick()
            }
        }
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
        val level = MercerieUi.label(this, 16f).apply { text = levelTitle(next, progress.easy) }
        val levels = button(R.string.mercerie_levels, Felt.MINT) { showMap() }

        // Sur un petit écran, la mascotte rapetisse (puis la guirlande) ; s'il manque encore de la
        // place, l'accueil entier est réduit d'un bloc : rien ne passe sous le bord de l'écran.
        val fit = FitFrame(this)
        if (landscape) {
            // Couché : le titre et Pique à gauche, les boutons à droite.
            val left = column().apply { gravity = Gravity.CENTER }
            left.add(title, width = -1)
            left.add(pique, width = dp(190f), height = dp(190f))
            fit.elastic(pique, dp(90f), square = true)
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
            frame.addView(topBar(null) { finish() }, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
            fit.addView(frame, ViewGroup.LayoutParams(-1, -1))
            setScreen(Screen.HOME, fit)
            return
        }
        val content = column()
        content.add(topBar(null) { finish() }, width = -1)
        val bunting = BuntingView(this)
        content.add(bunting, width = -1, height = dp(70f))
        content.add(spacer(), height = 0, weight = .6f)
        content.add(title, width = -1)
        content.add(pique, width = dp(210f), height = dp(210f), top = 4f)
        content.add(spacer(), height = 0, weight = .5f)
        content.add(play)
        content.add(level, top = 10f)
        content.add(spacer(), height = 0, weight = .7f)
        content.add(totals())
        content.add(levels, top = 14f)
        content.add(spacer(), height = dp(10f), weight = 1f)
        fit.elastic(pique, dp(100f), square = true)
        fit.elastic(bunting, dp(36f))
        fit.addView(content, ViewGroup.LayoutParams(-1, -1))
        setScreen(Screen.HOME, fit)
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
            out += Chip(icons.of(Kind.NORMAL, c), g.collect[c], c) { b -> (g.collect[c] - b.collected[c]).coerceAtLeast(0) }
        if (g.patches) out += Chip(icons.patch[0], MercerieBoard(l, 1L).patchesLeft, MercerieBoardView.KEY_PATCH) { it.patchesLeft }
        if (g.dust) out += Chip(icons.dust, MercerieBoard(l, 1L).dustLeft, MercerieBoardView.KEY_DUST) { it.dustLeft }
        if (g.thimbles > 0) out += Chip(icons.thimble, g.thimbles, MercerieBoardView.KEY_THIMBLE) { b -> g.thimbles - b.thimblesDone }
        if (g.onlyScore) out += Chip(starIcon, l.stars[0], -1) { b -> (l.stars[0] - b.score).coerceAtLeast(0) }
        return out
    }

    private var starIconCache: Bitmap? = null
    private val medalCache = HashMap<Boolean, Bitmap>()

    /** La médaille du défi : un disque de feutrine dorée et son ruban ; grise une fois le défi manqué. */
    private fun medal(gold: Boolean): Bitmap = medalCache.getOrPut(gold) {
        val s = dp(44f).toFloat()
        Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888).also {
            val c = android.graphics.Canvas(it)
            val left = android.graphics.Path().apply { moveTo(s * .26f, 0f); lineTo(s * .44f, 0f); lineTo(s * .54f, s * .5f); lineTo(s * .38f, s * .52f); close() }
            val right = android.graphics.Path().apply { moveTo(s * .56f, 0f); lineTo(s * .74f, 0f); lineTo(s * .62f, s * .52f); lineTo(s * .46f, s * .5f); close() }
            Felt.shape(c, left, if (gold) Felt.PINK else Felt.GREY, s * .5f, stitch = false)
            Felt.shape(c, right, if (gold) Felt.COLORS[1] else Felt.GREY, s * .5f, stitch = false)
            val disc = android.graphics.Path().apply { addCircle(s * .5f, s * .63f, s * .32f, android.graphics.Path.Direction.CW) }
            val face = if (gold) Felt.GOLD else Felt.GREY
            Felt.shape(c, disc, face, s * .64f, threadColor = Felt.lighter(face, .6f))
            c.save()
            c.translate(s * .5f, s * .64f)
            Felt.shape(c, Felt.starPath(s * .13f), Felt.lighter(face, .35f), s * .3f, stitch = false, shadow = false)
            c.restore()
        }
    }
    private var timerIconCache: Bitmap? = null
    private var portalIconCache: Bitmap? = null

    /** Une pièce à minuteur pour l'aide : le réveil posé sur un cœur. */
    private val timerIcon: Bitmap get() = timerIconCache ?: icons.of(Kind.NORMAL, 0).copy(Bitmap.Config.ARGB_8888, true).also {
        val c = android.graphics.Canvas(it)
        val s = it.width * .52f
        c.drawBitmap(icons.clock[0], null, android.graphics.RectF(it.width - s, 0f, it.width.toFloat(), s), null)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = MercerieUi.bold; textAlign = android.graphics.Paint.Align.CENTER; textSize = s * .42f; color = 0xFF3A2E36.toInt()
        }
        c.drawText("5", it.width - s / 2, s * .55f + p.textSize * .36f, p)
        timerIconCache = it
    }

    /** Une boutonnière pour l'aide. */
    private val portalIcon: Bitmap get() = portalIconCache ?: Bitmap.createBitmap(dp(44f), dp(44f), Bitmap.Config.ARGB_8888).also {
        val c = android.graphics.Canvas(it)
        val w = it.width * .36f
        val h = it.height * .1f
        val path = android.graphics.Path().apply {
            addRoundRect(it.width / 2f - w - h, it.height / 2f - h * 2, it.width / 2f + w + h, it.height / 2f + h * 2, h * 2, h * 2, android.graphics.Path.Direction.CW)
        }
        Felt.shape(c, path, Felt.lighter(Felt.COLORS[4], .15f), it.width * .7f, stitch = false, plush = false)
        val slit = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A1E24.toInt() }
        c.drawRoundRect(it.width / 2f - w, it.height / 2f - h, it.width / 2f + w, it.height / 2f + h, h, h, slit)
        portalIconCache = it
    }
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
            // Une seule ligne : « 2 500 » ne doit jamais se couper en « 2 50 » + « 0 ».
            count.isSingleLine = true
            if (live) chipTexts += chip to count
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8f), 0, dp(8f), 0)
                addView(ImageView(context).apply { setImageBitmap(chip.icon) }, LinearLayout.LayoutParams(dp(if (live) 40f else 48f), dp(if (live) 40f else 48f)))
                addView(count, LinearLayout.LayoutParams(-2, -2))
            }
            if (live) chipViews[chip.key] = box
            addView(box)
        }
    }

    private fun chipText(chip: Chip, board: MercerieBoard?): String {
        val left = if (board == null) chip.target else (chip.left(board) + (inFlight[chip.key] ?: 0)).coerceAtMost(chip.target)
        return if (left <= 0) getString(R.string.mercerie_sym_done) else MercerieUi.number(left)
    }

    // ───────────────────────────── Fiches ─────────────────────────────

    private fun closeCard() {
        card?.let { root.removeView(it) }
        card = null
        cover(false)
    }

    /**
     * Pose une fiche au milieu de l'écran, sur un voile. [dismissable] : toucher à côté la referme.
     * Trop haute pour l'écran (téléphone couché), elle est réduite d'un bloc plutôt que coupée ;
     * [fits] à faux pour une fiche qui fait déjà défiler son contenu (l'aide).
     */
    private fun showCard(dismissable: Boolean, fits: Boolean = true, build: CardColumn.() -> Unit) {
        closeCard()
        val panel = CardColumn(this, dp(300f), dp(12f)).apply {
            background = FeltDrawable(context, Felt.PAPER, 34f, threadColor = Felt.PINK)
            setPadding(dp(30f), dp(26f), dp(30f), dp(30f))
            isClickable = true
            build()
        }
        val box = FitFrame(this, shrinks = fits).apply { addView(panel, ViewGroup.LayoutParams(-2, -2)) }
        val veil = FrameLayout(this).apply {
            setBackgroundColor(Felt.VEIL)
            isClickable = true
            if (dismissable) setOnClickListener { closeCard() }
            addView(box, FrameLayout.LayoutParams(-1, -1).apply {
                leftMargin = dp(18f); rightMargin = dp(18f); topMargin = dp(10f); bottomMargin = dp(10f)
            })
        }
        card = veil
        root.addView(veil, FrameLayout.LayoutParams(-1, -1))
        cover(true)
        box.scaleX = .8f; box.scaleY = .8f; box.alpha = 0f
        box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
    }

    /** Une rangée de boutons ; trop large pour la fiche (téléphone étroit), ils passent l'un sous l'autre. */
    private fun CardColumn.buttons(vararg items: View) {
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for (b in items) line.addView(b, LinearLayout.LayoutParams(-2, -2))
        row(line)
        add(line, top = 20f)
    }

    /** Un bloc large de fiche : 300 de large au plus, moins sur un téléphone étroit. */
    private fun CardColumn.addWide(view: View, height: Int = -2, top: Float = 0f, weight: Float = 0f) {
        add(view, width = dp(300f), height = height, top = top, weight = weight)
        wide(view)
    }

    /**
     * Un étage sans fin n'est pas toujours prêt : le robot doit d'abord le régler. Pique sautille
     * sur une fiche pendant ce temps, puis [then] reçoit le niveau, si le joueur l'attend encore.
     */
    private fun withLevel(number: Int, then: (MercerieLevel) -> Unit) {
        workshop.peek(number)?.let { awaited = 0; then(it); return }
        awaited = number
        showCard(dismissable = true) {
            add(MercerieUi.label(context, 26f, Felt.PINK).apply { text = levelTitle(number, progress.easy) })
            add(MascotView(context, Mascot.random()).apply { mood = 1 }, width = dp(130f), height = dp(130f), top = 6f)
        }
        val shown = card
        workshop.request(number) { level ->
            if (awaited == number && card === shown) { awaited = 0; then(level) }
        }
    }

    /** « Niveau 36 », ou « 36 Facile » en mode facile. */
    private fun levelTitle(number: Int, easy: Boolean) =
        getString(if (easy) R.string.mercerie_level_easy else R.string.mercerie_level, number)

    private fun showLevelCard(number: Int) = withLevel(number) { l ->
        showCard(dismissable = true) {
            add(MercerieUi.label(context, 26f, Felt.PINK).apply { text = levelTitle(number, l.easy) })
            // Fini seulement en facile : ses étoiles en vert menthe, et « facile » dessous.
            val onlyEasy = progress.onlyEasy(number)
            add(StarsView(context, if (onlyEasy) progress.easyStars(number) else progress.stars(number), animated = false, easy = onlyEasy),
                width = dp(170f), height = dp(60f), top = 6f)
            if (onlyEasy) add(easyLabel(15f))
            add(goalRow(l, null, live = false), top = 8f)
            add(MercerieUi.label(context, 16f).apply { text = resources.getQuantityString(R.plurals.mercerie_moves, l.moves, l.moves) }, top = 10f)
            if (l.par > 0) add(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(context).apply { setImageBitmap(medal(true)) }, LinearLayout.LayoutParams(dp(36f), dp(36f)))
                addView(MercerieUi.label(context, 16f, Felt.darker(Felt.GOLD, if (Felt.dark) 1f else .7f)).apply {
                    text = resources.getQuantityString(R.plurals.mercerie_moves, l.par, l.par)
                    setPadding(dp(6f), 0, 0, 0)
                })
            }, top = 6f)
            val best = progress.best(number, l.easy)
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

    /** Le mot « facile », vert menthe : sous les étoiles d'un niveau fini en mode facile. */
    private fun easyLabel(size: Float): View =
        MercerieUi.label(this, size, if (Felt.dark) Felt.lighter(Felt.MINT, .55f) else Felt.darker(Felt.MINT, .6f)).apply {
            text = getString(R.string.mercerie_easy_tag)
        }

    /** Une bascule oui / non de l'aide : [label] à gauche, le bouton à droite. */
    private fun toggleRow(label: Int, get: () -> Boolean, set: (Boolean) -> Unit): View {
        val toggle = FeltButton(this, "")
        fun paint() {
            val on = get()
            toggle.text = getString(if (on) R.string.mercerie_on else R.string.mercerie_off)
            toggle.background = FeltDrawable(this, if (on) Felt.MINT else Felt.GREY, 28f)
        }
        toggle.setOnClickListener {
            sound.tick()
            set(!get())
            paint()
        }
        paint()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(MercerieUi.label(context, 16f).apply {
                text = getString(label)
                gravity = Gravity.START
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(toggle, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12f) })
        }
    }

    /** Le coup possible qui bat doucement quand on ne joue pas. */
    private fun hintToggle(): View = toggleRow(R.string.mercerie_hint, { progress.hints }) {
        progress.hints = it
        boardView?.hints = it
    }

    /**
     * Le mode facile. En pleine partie, le niveau repart aussitôt dans le mode choisi ; ailleurs,
     * l'aide se refait sans ce que le mode facile n'a pas (papillons, minuteurs, boutonnières, défi).
     */
    private fun easyToggle(): View = toggleRow(R.string.mercerie_easy, { progress.easy }) {
        progress.easy = it
        val playing = if (screen == Screen.GAME) boardView?.board?.level?.number else null
        if (playing != null) startLevel(playing)
        else {
            // L'accueil montre le titre du niveau à jouer : il change avec le mode.
            if (screen == Screen.HOME) showHome() else workshop.warm(progress.next)
            showHelp()
        }
    }

    private fun showHelp() {
        val easy = progress.easy
        val rows = listOfNotNull(
            icons.of(Kind.LINE_H, 0) to R.string.mercerie_help_line,
            if (easy) null else icons.of(Kind.BUTTERFLY, 3) to R.string.mercerie_help_butterfly,
            icons.of(Kind.BOMB, 1) to R.string.mercerie_help_bomb,
            icons.rainbow to R.string.mercerie_help_rainbow,
            icons.patch[0] to R.string.mercerie_help_patch,
            icons.knot[0] to R.string.mercerie_help_knot,
            icons.dust to if (easy) R.string.mercerie_help_dust_easy else R.string.mercerie_help_dust,
            icons.pin to R.string.mercerie_help_pin,
            icons.thimble to R.string.mercerie_help_thimble,
            if (easy) null else timerIcon to R.string.mercerie_help_timer,
            if (easy) null else portalIcon to R.string.mercerie_help_portal,
            if (easy) null else medal(true) to R.string.mercerie_help_par
        )
        showCard(dismissable = true, fits = false) {
            add(MercerieUi.label(context, 24f, Felt.PINK).apply { text = getString(R.string.mercerie_help_title) })
            add(button(R.string.mercerie_tutorial, Felt.MINT) { startTutorial() }, top = 10f)
            addWide(easyToggle(), top = 10f)
            addWide(hintToggle(), top = 6f)
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
            addWide(scroll, height = 0, top = 10f, weight = 1f)
            buttons(button(R.string.close) { closeCard() })
        }
    }

    // ───────────────────────────── Tutoriel ─────────────────────────────

    /**
     * Le tutoriel, ouvert depuis le « ? » : des tableaux préparés où le joueur glisse lui-même les
     * pièces, mais seulement le coup prévu, montré par une main de feutrine ; tout se déroule au
     * ralenti pour qu'on voie chaque bonus naître, partir et en déclencher d'autres. Une partie en
     * cours attend derrière, intacte.
     */
    private fun startTutorial() {
        closeCard()
        if (screen != Screen.TUTORIAL) tutorFrom = screen
        lessons = MercerieLessons.forMode(progress.easy)
        openLesson(0)
    }

    private fun openLesson(k: Int) {
        lessonAt = k
        lessonStep = 0
        practicing = false
        practiceDone = false
        practiceHit = false
        val lesson = lessons[k]
        val view = MercerieBoardView(this, lesson.board(progress.easy), sound, slow = TUTOR_SLOW)
        view.hints = false
        view.guided = true
        view.guide = lesson.cells(0)
        view.listener = object : MercerieBoardView.Listener {
            override fun onChanged() {}
            override fun onBonus() {}
            override fun onFinished(won: Boolean) {}
            override fun onSwap(a: Int, b: Int) {
                if (tutorView === view && practicing && !practiceDone && lesson.practiced(view.board, a, b)) practiceHit = true
            }
            override fun onSettled() {
                if (tutorView !== view) return
                if (!practicing) nextLessonStep()
                else if (practiceHit && !practiceDone) {
                    practiceDone = true
                    view.hintPick = null
                    sound.win(3)
                    showLessonState()
                }
            }
        }
        tutorView = view
        layoutTutorial()
    }

    /** Le coup prévu est retombé : le suivant, ou la fin de la démonstration. */
    private fun nextLessonStep() {
        val view = tutorView ?: return
        val lesson = lessons[lessonAt]
        lessonStep++
        if (lessonStep < lesson.moves.size) view.guide = lesson.cells(lessonStep) else startPractice(view, lesson)
        showLessonState()
    }

    /**
     * La démonstration est finie : le plateau devient libre, à vitesse normale. Le joueur refait la
     * même chose seul (le bonus [Lesson.practice], ou les deux bonus [Lesson.encore] qui apparaissent) ;
     * s'il cherche, le coup conseillé lui montre l'exercice au bout de quelques secondes.
     */
    private fun startPractice(view: MercerieBoardView, lesson: Lesson) {
        practicing = true
        practiceDone = !lesson.hasPractice
        practiceHit = false
        val board = view.board
        view.guide = null
        view.guided = false
        view.slow = 1f
        view.hintDelay = PRACTICE_HINT_MS
        view.hintPick = if (lesson.hasPractice) ({ lesson.practiceMove(board) }) else null
        view.hints = true
        if (lesson.encore.isNotEmpty()) view.reveal(lesson.placeEncore(board))
    }

    private fun showLessonState() {
        val lesson = lessons.getOrNull(lessonAt) ?: return
        tutorText?.text = getString(when {
            !practicing -> lesson.moves[lessonStep].text
            practiceDone -> if (lesson.hasPractice) R.string.mercerie_tuto_well_done else R.string.mercerie_tuto_free
            lesson.encore.isNotEmpty() -> R.string.mercerie_tuto_your_turn_combo
            else -> R.string.mercerie_tuto_your_turn
        })
        tutorButtons?.visibility = if (practicing) View.VISIBLE else View.INVISIBLE
    }

    private fun leaveTutorial() {
        tutorView = null
        tutorText = null
        tutorButtons = null
        val game = boardView
        when {
            // Le jour ou la nuit a pu changer pendant le tutoriel : le plateau de la partie se refait.
            tutorFrom == Screen.GAME && game != null -> { game.restyle(); layoutGame(game) }
            tutorFrom == Screen.MAP -> showMap()
            else -> showHome()
        }
    }

    /** Debout : consigne au-dessus du plateau, boutons dessous. Couché : consigne et boutons à gauche. */
    private fun layoutTutorial() {
        val view = tutorView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        val lesson = lessons[lessonAt]
        val wide = landscape
        val caption = column().apply {
            background = FeltDrawable(context, Felt.PAPER, 30f, threadColor = Felt.KRAFT)
            setPadding(dp(18f), dp(12f), dp(18f), dp(16f))
        }
        caption.add(MercerieUi.label(this, 14f, Felt.SOFT).apply {
            text = getString(R.string.mercerie_tuto_count, lessonAt + 1, lessons.size)
        })
        caption.add(MercerieUi.label(this, 22f, Felt.PINK).apply { text = getString(lesson.title) }, top = 2f)
        val instruction = MercerieUi.label(this, 17f).apply { minLines = 2 }
        caption.add(instruction, width = -1, top = 6f)
        tutorText = instruction
        val last = lessonAt == lessons.size - 1
        val again = button(R.string.mercerie_retry, Felt.MINT) { openLesson(lessonAt) }
        val next = button(if (last) R.string.mercerie_tuto_end else R.string.mercerie_next) {
            if (last) leaveTutorial() else openLesson(lessonAt + 1)
        }
        // Rejouer à gauche, la suite à droite.
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(again)
            addView(next, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14f) })
        }
        tutorButtons = row
        val content: View
        if (wide) {
            val side = column()
            val corner = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(roundButton(R.string.mercerie_sym_back) { leaveTutorial() })
                addView(spacer(), LinearLayout.LayoutParams(0, 0, 1f))
                addView(rightButtons())
            }
            side.add(corner, width = -1)
            side.add(MercerieUi.label(this, 20f).apply { text = getString(R.string.mercerie_tutorial) }, top = 8f)
            side.add(caption, width = -1, top = 10f)
            side.add(spacer(), height = 0, weight = 1f)
            side.add(row)
            side.add(spacer(), height = dp(8f))
            val sideFit = FitFrame(this).apply { addView(side, ViewGroup.LayoutParams(-1, -1)) }
            content = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(sideFit, LinearLayout.LayoutParams(dp(300f), -1).apply {
                    leftMargin = dp(12f); topMargin = dp(10f); bottomMargin = dp(10f)
                })
                addView(view, LinearLayout.LayoutParams(0, -1, 1f))
            }
        } else {
            val column = column()
            column.add(topBar(getString(R.string.mercerie_tutorial)) { leaveTutorial() }, width = -1)
            column.addView(caption, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f); leftMargin = dp(12f); rightMargin = dp(12f) })
            column.add(view, width = -1, height = 0, weight = 1f)
            column.add(row)
            column.add(spacer(), height = dp(14f))
            content = column
        }
        setScreen(Screen.TUTORIAL, content)
        showLessonState()
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
            override fun onBonus() {
                setMood(1)
                shownMoves = view.board.movesLeft
                if (view.board.beatsPar) view.showCheer(getString(R.string.mercerie_challenge), 5)
                else view.showCheer(getString(R.string.mercerie_bonus), 4)
            }
            override fun onFinished(won: Boolean) = finishLevel(won)
            override fun onCheer(tier: Int) {
                if (tier < 3 || mood != 0) return
                mascot?.mood = 1
                root.postDelayed({ if (mood == 0) mascot?.mood = 0 }, 1300L)
            }
            override fun onCollect(key: Int, x: Float, y: Float, size: Float) = collectFlight(view, key, x, y, size)
            override fun onShot(x: Float, y: Float, delay: Long, flight: Long) = bonusShot(view, x, y, delay, flight)
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
        val title = levelTitle(l.number, l.easy)
        parIcon = null
        parText = null
        val parBox = if (l.par <= 0) null else column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(8f), 0, dp(4f), 0)
            val icon = ImageView(context).apply { setImageBitmap(medal(true)) }
            parGold = true
            parIcon = icon
            parText = MercerieUi.label(context, if (wide) 18f else 16f, Felt.darker(Felt.GOLD, if (Felt.dark) 1f else .7f)).apply { isSingleLine = true }
            addView(icon, LinearLayout.LayoutParams(dp(if (wide) 44f else 38f), dp(if (wide) 44f else 38f)))
            addView(parText)
        }

        val content: View
        if (wide) {
            val side = column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                background = FeltDrawable(context, Felt.PAPER, 30f, threadColor = Felt.KRAFT)
                setPadding(dp(14f), dp(14f), dp(14f), dp(18f))
            }
            // La colonne est étroite : retour seul en haut, les trois boutons de droite en dessous.
            val corner = FrameLayout(this).apply {
                addView(roundButton(R.string.mercerie_sym_back) { showPause() }, FrameLayout.LayoutParams(-2, -2, Gravity.START))
            }
            side.add(corner, width = -1)
            side.add(rightButtons(), top = 8f)
            side.add(spacer(), height = 0, weight = .6f)
            side.add(MercerieUi.label(this, 22f, Felt.PINK).apply { text = title })
            side.add(spacer(), height = 0, weight = .6f)
            side.add(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(movesBox)
                parBox?.let { addView(it) }
            })
            side.add(spacer(), height = 0, weight = 1f)
            side.add(goals)
            side.add(spacer(), height = 0, weight = 1f)
            side.add(scoreBox)
            side.add(spacer(), height = 0, weight = .6f)
            // Téléphone couché, peu haut : la colonne est réduite d'un bloc plutôt que rognée en bas.
            val sideFit = FitFrame(this).apply { addView(side, ViewGroup.LayoutParams(-1, -1)) }
            content = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(sideFit, LinearLayout.LayoutParams(dp(244f), -1).apply {
                    leftMargin = dp(10f); topMargin = dp(10f); bottomMargin = dp(10f)
                })
                addView(view, LinearLayout.LayoutParams(0, -1, 1f))
            }
        } else {
            val column = column()
            column.add(topBar(title) { showPause() }, width = -1)
            val hud = column().apply {
                background = FeltDrawable(context, Felt.PAPER, 30f, threadColor = Felt.KRAFT)
                setPadding(dp(14f), dp(10f), dp(16f), dp(14f))
            }
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(movesBox)
                parBox?.let { addView(it) }
            }
            // Téléphone étroit : les objectifs n'ont plus la place entre les coups et le score, ils
            // passent sur une seconde ligne plutôt que d'être rognés.
            val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val need = listOfNotNull(movesBox, parBox, goals, scoreBox).sumOf { it.measure(free, free); it.measuredWidth }
            val room = (resources.configuration.screenWidthDp * resources.displayMetrics.density).toInt() -
                dp(24f) - hud.paddingLeft - hud.paddingRight
            if (need <= room) {
                line.addView(goals, LinearLayout.LayoutParams(0, -2, 1f))
                line.addView(scoreBox)
                hud.add(line, width = -1)
            } else {
                line.addView(spacer(), LinearLayout.LayoutParams(0, 0, 1f))
                line.addView(scoreBox)
                hud.add(line, width = -1)
                hud.add(goals, top = 6f)
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
        this.movesBox = movesBox
        inFlight.clear()
        refreshHud()
    }

    /** Une pièce utile vole du plateau jusqu'à son compteur, qui ne baisse qu'à son arrivée. */
    private fun collectFlight(view: MercerieBoardView, key: Int, x: Float, y: Float, size: Float) {
        val target = chipViews[key] ?: return
        val chip = chipTexts.firstOrNull { it.first.key == key }?.first ?: return
        val layer = flights ?: return
        inFlight[key] = (inFlight[key] ?: 0) + 1
        refreshHud()
        layer.fly(chip.icon, x, y, target, size, dp(40f).toFloat(), life = 520L + (inFlight[key] ?: 0).coerceAtMost(8) * 25L,
            trail = if (key in 0..5) Felt.COLORS[key] else Felt.GOLD) {
            if (boardView !== view) return@fly
            inFlight[key] = ((inFlight[key] ?: 1) - 1).coerceAtLeast(0)
            sound.collect()
            target.animate().cancel()
            target.scaleX = 1.3f; target.scaleY = 1.3f
            target.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
            refreshHud()
        }
    }

    /** Couture finale : chaque coup restant part du compteur, en étoile, vers la pièce qu'il change en bobine. */
    private fun bonusShot(view: MercerieBoardView, x: Float, y: Float, delay: Long, flight: Long) {
        val layer = flights ?: return
        root.postDelayed({
            val box = movesBox ?: return@postDelayed
            if (boardView !== view) return@postDelayed
            if (shownMoves > 0) shownMoves--
            refreshHud()
            box.animate().cancel()
            box.scaleX = 1.12f; box.scaleY = 1.12f
            box.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            val loc = IntArray(2)
            box.getLocationInWindow(loc)
            layer.flyTo(starIcon, loc[0] + box.width / 2f, loc[1] + box.height / 2f, x, y, dp(34f).toFloat(), dp(26f).toFloat(),
                life = flight, trail = Felt.GOLD)
        }, delay)
    }

    /**
     * Ne réécrit un compteur que s'il a changé : le plateau rappelle [refreshHud] à chaque pièce qui
     * part, et un texte réécrit, même identique, refait la mise en page de l'écran à l'image suivante.
     */
    private fun TextView.show(value: String) {
        if (!TextUtils.equals(text, value)) text = value
    }

    private fun refreshHud() {
        val board = boardView?.board ?: return
        movesText?.show(MercerieUi.number(if (board.bonus && shownMoves >= 0) shownMoves else board.movesLeft))
        scoreText?.show(MercerieUi.number(board.score))
        starBar?.score = board.score
        for ((chip, view) in chipTexts) view.show(chipText(chip, board))
        parText?.let { label ->
            // Coups du défi qui restent ; tenu, une coche ; manqué, la médaille grise sans nombre.
            val left = board.level.par - board.movesUsed
            val gold = board.beatsPar || (!board.bonus && !board.finished && left >= 0)
            label.show(when {
                board.beatsPar -> getString(R.string.mercerie_sym_done)
                gold -> MercerieUi.number(left)
                else -> ""
            })
            if (gold != parGold) {
                parGold = gold
                parIcon?.setImageBitmap(medal(gold))
            }
        }
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
                    add(MercerieUi.label(context, 26f, Felt.PINK).apply {
                        text = getString(if (board.timedOut) R.string.mercerie_lose_timer else R.string.mercerie_lose)
                    })
                    add(goalRow(l, board, live = false), top = 12f)
                    buttons(button(R.string.mercerie_retry) { startLevel(l.number) })
                    buttons(button(R.string.mercerie_quit, Felt.PAPER) { showHome() })
                }
            }, 500L)
            return
        }
        val stars = board.stars
        val record = progress.record(l.number, board.score, stars, l.easy)
        val neutrinos = NeutrinoRewards.mercerie(l.number, record.firstTime, l.easy)
        if (neutrinos > 0) NeutrinoRepository(this).addBalance(neutrinos)
        sound.win(stars)
        root.postDelayed({
            if (screen != Screen.GAME || boardView?.board !== board) return@postDelayed
            flights?.confetti(stars)
            val titles = resources.getStringArray(when (stars) { 3 -> R.array.mercerie_win_3; 2 -> R.array.mercerie_win_2; else -> R.array.mercerie_win_1 })
            showCard(dismissable = false) {
                addWide(FeltTitleView(context, titles.random(), 40f))
                add(StarsView(context, stars, easy = l.easy), width = dp(210f), height = dp(78f), top = 2f)
                if (l.easy) add(easyLabel(15f))
                // Le score se compte sous les yeux du joueur.
                val total = MercerieUi.label(context, 30f).apply { text = MercerieUi.number(0) }
                add(total, top = 4f)
                if (board.challenge > 0) add(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(context).apply { setImageBitmap(medal(true)) }, LinearLayout.LayoutParams(dp(40f), dp(40f)))
                    addView(MercerieUi.label(context, 17f, Felt.darker(Felt.GOLD, if (Felt.dark) 1f else .7f)).apply {
                        text = getString(R.string.mercerie_challenge)
                        setPadding(dp(6f), 0, 0, 0)
                    })
                }, top = 4f)
                android.animation.ValueAnimator.ofInt(0, board.score).apply {
                    duration = 900L
                    startDelay = 250L
                    addUpdateListener { total.text = MercerieUi.number(it.animatedValue as Int) }
                    start()
                }
                add(MercerieUi.label(context, 15f, Felt.SOFT).apply {
                    text = if (record.newBest) getString(R.string.mercerie_new_best)
                    else getString(R.string.mercerie_best, MercerieUi.number(progress.best(l.number, l.easy)))
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

    private companion object {
        /** La démonstration du tutoriel se déroule deux fois moins vite qu'une partie. */
        const val TUTOR_SLOW = 2f
        /** Après la démonstration, le coup à refaire bat au bout de ce temps sans jouer. */
        const val PRACTICE_HINT_MS = 4000L
    }
}
