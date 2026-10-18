package com.Atom2Universe.app.games.balance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.science.SciencePalette
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Affichage et pilotage du mobile : le décor dans les couleurs du thème de l'appli, les objets
 * dans celles de Calder (rouge, bleu, jaune, et l'encre du thème).
 *
 * La simulation tourne sur son propre fil à pas fixe. L'interface ne touche **jamais** à
 * l'état du jeu : elle note les gestes dans une file, que le fil de jeu vide au début de
 * chaque image.
 */
class MobileView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    interface Listener {
        /** Le mobile vient d'être achevé. */
        fun onWon()
    }

    val game = MobileGame()
    var listener: Listener? = null
    var sfx: MobileSfx? = null

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastNanos = 0L
    private var accumulator = 0f
    private var clock = 0f
    private var victoireDepuis = -1f
    private var dernierDt = 0f

    private var scale = 100f
    private var originX = 0f
    private var originY = 0f
    private var camKey: Any? = null
    private var viewW = 0
    private var viewH = 0

    private val dp = resources.displayMetrics.density

    /** Gestes en attente : [type, x, y] en pixels. 0 = appui, 1 = glissement, 2 = relâchement. */
    private val gestures = ConcurrentLinkedQueue<FloatArray>()

    // Le décor suit le thème de l'appli : son fond d'écran (teinté par l'accent en haut), son
    // encre (la couleur du texte : claire sur fond sombre, foncée sur fond clair) et son accent,
    // qui allume les nœuds justes. Seuls les objets gardent les couleurs de Calder.
    private val couleurs = SciencePalette(ctx)
    private val fond = AppearanceStyle.screen(ctx)
    private val paper = couleurs.background
    private val ink = couleurs.text
    private val inkSoft = couleurs.secondary
    private val ok = couleurs.accent
    private val poutre = couleurs.raised
    private val palette = intArrayOf(
        "#D63A2F".toColorInt(),
        "#2F5DA8".toColorInt(),
        "#F2B705".toColorInt(),
        // Le « noir » de Calder est l'encre du thème : noir sur fond clair, blanc sur fond sombre.
        ink
    )
    /** Les points d'un objet : clairs sur le rouge et le bleu, foncés sur le jaune, fond sur l'encre. */
    private val points = intArrayOf(
        "#FBF6EA".toColorInt(),
        "#FBF6EA".toColorInt(),
        "#211C19".toColorInt(),
        paper
    )

    private fun encre(alpha: Int) = ColorUtils.setAlphaComponent(ink, alpha)

    private val pBg = Paint().apply { color = paper }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ink
    }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private var hardwareCanvas = true

    private fun lockFrame(): Canvas? {
        if (hardwareCanvas) {
            try {
                return holder.lockHardwareCanvas()
            } catch (_: Throwable) {
                hardwareCanvas = false
            }
        }
        return holder.lockCanvas()
    }

    init {
        holder.addCallback(this)
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) = resume()

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        viewW = w
        viewH = h
        camKey = null
        fond?.setBounds(0, 0, w, h)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = pause()

    fun pause() {
        running = false
        thread?.join(1500)
        thread = null
    }

    fun resume() {
        if (running) return
        running = true
        lastNanos = System.nanoTime()
        accumulator = 0f
        thread = Thread(this, "MobilePhysics").also { it.start() }
    }

    /** Après un changement de tableau piloté par l'activité. */
    fun syncLevel() {
        camKey = null
        victoireDepuis = -1f
        gestures.clear()
        appuiEnAttente = false
        noteCascade = -1
        gain = null
    }

    /** Le gain de la victoire, affiché au-dessus du mobile ; posé par l'activité. */
    @Volatile var gain: String? = null

    // ── Boucle ────────────────────────────────────────────────────────────────

    private val sons = ArrayList<MobileGame.Evenement>()

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            val frameDt = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            lastNanos = now

            var justWon = false
            synchronized(game) {
                fitCamera()
                drainGestures()
                accumulator += frameDt
                var guard = 0
                while (accumulator >= FIXED_DT && guard < 24) {
                    game.step(FIXED_DT)
                    accumulator -= FIXED_DT
                    guard++
                }
                if (guard == 24) accumulator = 0f
                clock += frameDt
                dernierDt = frameDt
                for (e in game.evenements) {
                    if (e is MobileGame.Evenement.Victoire) {
                        justWon = true
                        victoireDepuis = clock
                    }
                    sons += e
                }
                game.evenements.clear()
            }
            val accroche = sons.any { it is MobileGame.Evenement.Accroche }
            jouerSons()
            if (accroche) post { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            if (justWon) post { listener?.onWon() }

            val canvas = lockFrame()
            if (canvas == null) {
                Thread.sleep(16)
                continue
            }
            try {
                synchronized(game) { drawFrame(canvas) }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    /** Prochain étage de la cascade à sonner, ou -1 hors victoire. */
    private var noteCascade = -1

    private fun jouerSons() {
        val s = sfx
        if (s != null) {
            for (e in sons) when (e) {
                is MobileGame.Evenement.Accroche -> s.accroche(e.objet.masse)
                is MobileGame.Evenement.Prise -> s.prise()
                MobileGame.Evenement.Equilibre -> s.juste()
                MobileGame.Evenement.Victoire -> noteCascade = 0
            }
        }
        sons.clear()
        // La cascade sonne avec les nœuds qui s'allument : une cloche par étage, de plus en plus
        // aiguë en montant, et l'accord quand le nœud du plafond s'allume à son tour.
        if (noteCascade >= 0 && victoireDepuis >= 0f) {
            while (noteCascade <= profondeurMax && clock - victoireDepuis >= noteCascade * CASCADE) {
                if (noteCascade == profondeurMax) s?.victoire() else s?.cascade(noteCascade)
                noteCascade++
            }
            if (noteCascade > profondeurMax) noteCascade = -1
        }
    }

    private companion object {
        const val FIXED_DT = 1f / 120f

        /** Épaisseur de la poutre du plafond, en dp. */
        const val HAUT_PLAFOND = 10f

        /** Ce que l'étagère prend en hauteur en plus de ses objets : son trait, et de l'air au-dessus. */
        const val ESPACE_ETAGERE = 56f

        /** Un crochet est visé à cette distance de l'anneau de l'objet tenu (dp). */
        const val PORTEE = 48f

        /** Hauteur de l'anneau de l'objet tenu au-dessus du doigt (dp). */
        const val LEVEE = 28f

        /** Le doigt doit glisser de tant (dp) avant de prendre : en deçà, c'est un tapotement. */
        const val GLISSE = 8f

        /** Délai entre deux nœuds dans la cascade de la victoire, du bas vers le haut (s). */
        const val CASCADE = 0.14f

        /** Durée de l'onde d'un nœud qui s'allume (s). */
        const val ONDE = 0.7f
    }

    // ── Caméra ────────────────────────────────────────────────────────────────

    private fun fitCamera() {
        if (viewW == 0 || viewH == 0) return
        if (camKey === game.monde) return
        camKey = game.monde
        compterProfondeurs()
        val b = game.bornes
        val w = b[1] - b[0]
        val h = b[3] - b[2]
        val marge = 14f * dp
        val largeur = viewW - 2 * marge
        // Le mobile prend toute la largeur qu'il peut ; l'étagère, en bas, rapetisse ses objets
        // plutôt que de le faire rapetisser, lui. Sa hauteur dépend donc de l'échelle : deux
        // passes suffisent à s'accorder.
        val pleineEtagere = largeur / game.largeurEtagere
        val bandeau = { s: Float -> 2f * game.rayonMax * min(s, pleineEtagere) + ESPACE_ETAGERE * dp }
        val haut = HAUT_PLAFOND * dp
        scale = largeur / w
        repeat(2) { scale = min(largeur / w, (viewH - haut - marge - bandeau(scale)) / h) }
        originX = viewW / 2f - (b[0] + b[1]) / 2f * scale
        // Le plafond en haut de l'écran, l'étagère en bas : la place qui reste va entre les deux,
        // là où le mobile se balance.
        originY = haut + MobileGame.PLAFOND * scale
        val reduction = min(1f, pleineEtagere / scale)
        val basEtagere = viewH - marge - 8f * dp
        game.poserEtagere(wy(basEtagere - game.rayonMax * reduction * scale), wx(viewW / 2f), reduction)
    }

    private fun sx(x: Float) = originX + x * scale
    private fun sy(y: Float) = originY - y * scale
    private fun wx(px: Float) = (px - originX) / scale
    private fun wy(py: Float) = (originY - py) / scale

    // ── Gestes ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> gestures.add(floatArrayOf(0f, e.x, e.y))
            MotionEvent.ACTION_MOVE -> gestures.add(floatArrayOf(1f, e.x, e.y))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> gestures.add(floatArrayOf(2f, e.x, e.y))
        }
        return true
    }


    /** Où le doigt s'est posé (pixels), tant qu'il n'a pas encore bougé assez pour prendre. */
    private var appuiX = 0f
    private var appuiY = 0f
    private var appuiEnAttente = false

    /**
     * Appelé sur le fil de jeu seulement : c'est lui qui touche à l'état.
     *
     * On ne prend un objet qu'une fois que le doigt a **glissé** : un simple tapotement ne fait
     * rien. Prendre dès l'appui décrochait l'objet touché, et selon sa taille il revenait à son
     * crochet ou filait à l'étagère — la même touche faisait deux choses.
     */
    private fun drainGestures() {
        while (true) {
            val g = gestures.poll() ?: break
            val portee = PORTEE * dp / scale
            when (g[0].toInt()) {
                0 -> {
                    appuiX = g[1]
                    appuiY = g[2]
                    appuiEnAttente = true
                }
                1 -> {
                    if (appuiEnAttente && kotlin.math.hypot(g[1] - appuiX, g[2] - appuiY) > GLISSE * dp) {
                        appuiEnAttente = false
                        game.prendre(wx(appuiX), wy(appuiY), 14f * dp / scale)
                    }
                    // L'anneau juste au-dessus du bout du doigt : c'est lui qui vise, et le
                    // doigt ne le cache pas. Le disque pend dessous.
                    game.deplacer(wx(g[1]), wy(g[2]) + LEVEE * dp / scale, portee)
                }
                else -> {
                    appuiEnAttente = false
                    game.lacher()
                }
            }
        }
    }

    // ── Dessin ────────────────────────────────────────────────────────────────

    private val a = FloatArray(2)
    private val b = FloatArray(2)

    private fun drawFrame(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pBg)
        fond?.draw(c)
        if (viewW == 0) return
        val monde = game.monde
        if (vuPour !== game.casesObjets) {
            vu.clear()
            vuPour = game.casesObjets
        }

        dessinerPlafond(c)
        dessinerEtagere(c)

        // Fils des tiges (plafond → nœud, bout de tige → nœud).
        pLine.color = ink
        pLine.strokeWidth = max(1.4f * dp, 0.012f * scale)
        for (fil in monde.filsTiges) {
            fil.anchorAWorld(a)
            fil.anchorBWorld(b)
            c.drawLine(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), pLine)
        }

        for (t in monde.tiges) dessinerTige(c, t)

        for (cr in monde.crochets) dessinerCrochet(c, cr)

        // L'objet en main, par-dessus tout.
        game.enMain?.let { o ->
            noter(o, game.mainX, game.mainY)
            val cible = game.cible
            val ax = sx(game.mainX)
            val ay = sy(game.mainY + o.rayon)
            if (cible != null) {
                // Aimanté : l'objet est dessiné là où il pendra, un seul disque à lire. Au doigt
                // ne reste que son anneau, relié à lui par le fil qu'il aura.
                val corps = monde.corps(cible)
                if (cible.objet != null && corps != null) {
                    b[0] = corps.x
                    b[1] = corps.y
                } else {
                    monde.crochet(cible, b)
                    b[1] -= o.rayon
                }
                pLine.color = encre(120)
                pLine.strokeWidth = max(1.4f * dp, 0.012f * scale)
                c.drawLine(ax, ay, sx(b[0]), sy(b[1] + o.rayon), pLine)
                dessinerObjet(c, o, b[0], b[1], couleur(o), 0f, alpha = 235)
                val cx = sx(b[0])
                val cy = sy(b[1] + o.rayon)
                dessinerAnneau(c, cx, cy)
                // L'anneau du doigt seulement s'il est à l'écart : posé sur l'autre, il le brouille.
                if (kotlin.math.hypot(ax - cx, ay - cy) > 14f * dp) dessinerAnneau(c, ax, ay)
            } else {
                pFill.style = Paint.Style.FILL
                // Une ombre reste sombre, même sur un fond sombre.
                pFill.color = Color.argb(60, 0, 0, 0)
                c.drawCircle(sx(game.mainX) + 6f * dp, sy(game.mainY) + 10f * dp, o.rayon * scale, pFill)
                dessinerObjet(c, o, game.mainX, game.mainY, couleur(o), 0f)
                dessinerAnneau(c, ax, ay)
            }
        }
        dessinerGain(c)
    }

    /** L'anneau d'un objet tenu, là où se nouera son fil : c'est lui qu'on amène au crochet. */
    private fun dessinerAnneau(c: Canvas, x: Float, y: Float) {
        val r = 5.5f * dp
        pFill.style = Paint.Style.FILL
        pFill.color = paper
        c.drawCircle(x, y, r, pFill)
        pLine.color = ink
        pLine.strokeWidth = 2.2f * dp
        c.drawCircle(x, y, r, pLine)
    }

    /**
     * La couleur suit le poids : d'un tableau à l'autre, le jaune à trois points reste le jaune à
     * trois points, et deux jumeaux se ressemblent.
     */
    private fun couleur(o: MobileObjet): Int = palette[(o.masse - 1).mod(palette.size)]

    private fun dessinerPlafond(c: Canvas) {
        val y = sy(MobileGame.PLAFOND)
        pFill.style = Paint.Style.FILL
        pFill.color = poutre
        c.drawRect(0f, 0f, width.toFloat(), y, pFill)
        // Le bord de la poutre à l'encre : le fil du mobile part de là.
        pLine.color = ink
        pLine.strokeWidth = 2f * dp
        c.drawLine(0f, y, width.toFloat(), y, pLine)
    }

    private fun dessinerEtagere(c: Canvas) {
        val cases = game.casesEtagere
        if (cases.isEmpty()) return
        val red = game.echelleEtagere
        val y = sy(cases[1] - game.rayonMax * red) + 6f * dp
        pLine.color = inkSoft
        pLine.strokeWidth = 2f * dp
        val gauche = sx(cases[0] - game.casesObjets[0].rayon * red) - 14f * dp
        val n = game.casesObjets.size
        val droite = sx(cases[2 * (n - 1)] + game.casesObjets[n - 1].rayon * red) + 14f * dp
        c.drawLine(gauche, y, droite, y, pLine)

        // Les traces des cases vides d'abord, les objets ensuite : un objet qui rentre passe
        // par-dessus les cases qu'il survole.
        for ((i, o) in game.casesObjets.withIndex()) {
            if (game.etagere.any { it === o } && arrive(o, cases[2 * i], cases[2 * i + 1])) continue
            pLine.color = encre(70)
            pLine.strokeWidth = 1.5f * dp
            c.drawCircle(sx(cases[2 * i]), sy(cases[2 * i + 1]), o.rayon * red * scale, pLine)
        }
        for ((i, o) in game.casesObjets.withIndex()) {
            if (game.etagere.none { it === o }) continue
            val p = vu.getOrPut(o) { floatArrayOf(cases[2 * i], cases[2 * i + 1], red) }
            // Un objet rendu à l'étagère y retourne en glissant, il ne s'y téléporte pas.
            val k = min(1f, dernierDt * 14f)
            p[0] += (cases[2 * i] - p[0]) * k
            p[1] += (cases[2 * i + 1] - p[1]) * k
            p[2] += (red - p[2]) * k
            dessinerObjet(c, o, p[0], p[1], couleur(o), 0f, p[2])
        }
    }

    /** Vrai si l'objet est déjà posé dans sa case (à un millimètre près). */
    private fun arrive(o: MobileObjet, x: Float, y: Float): Boolean {
        val p = vu[o] ?: return true
        return kotlin.math.abs(p[0] - x) < 0.001f && kotlin.math.abs(p[1] - y) < 0.001f
    }

    /**
     * Dernière position affichée de chaque objet (x, y en mètres, puis sa taille relative) :
     * d'où il part quand il rentre.
     */
    private val vu = java.util.IdentityHashMap<MobileObjet, FloatArray>()
    private var vuPour: List<MobileObjet>? = null

    private fun noter(o: MobileObjet, x: Float, y: Float) {
        val p = vu.getOrPut(o) { FloatArray(3) }
        p[0] = x
        p[1] = y
        p[2] = 1f
    }

    private fun dessinerTige(c: Canvas, t: MobileTige) {
        val corps = game.monde.corps(t)
        val epaisseur = max(3.5f * dp, 0.045f * scale)
        val juste = game.estJuste(t)

        // L'étrier : deux brins du nœud à la tige.
        val pied = min(0.09f, 0.4f * t.unite)
        corps.localToWorld(0f, MobileRegles.ETRIER, a)
        val nx = sx(a[0])
        val ny = sy(a[1])
        pLine.color = ink
        pLine.strokeWidth = max(1.4f * dp, 0.012f * scale)
        corps.localToWorld(-pied, 0f, b)
        c.drawLine(nx, ny, sx(b[0]), sy(b[1]), pLine)
        corps.localToWorld(pied, 0f, b)
        c.drawLine(nx, ny, sx(b[0]), sy(b[1]), pLine)

        // La tige, en autant de maillons qu'elle a de crans : un bras se compte d'un coup d'œil,
        // depuis le nœud, qui tombe toujours entre deux maillons. Les bouts restent pleins, là
        // où pendent les fils.
        pLine.strokeWidth = epaisseur
        val jeu = (max(3f * dp, 0.35f * epaisseur) + epaisseur) * 0.5f / scale
        for (k in -t.brasGauche until t.brasDroit) {
            val x0 = k * t.unite + if (k == -t.brasGauche) 0f else jeu
            val x1 = (k + 1) * t.unite - if (k + 1 == t.brasDroit) 0f else jeu
            corps.localToWorld(x0, 0f, a)
            corps.localToWorld(x1, 0f, b)
            c.drawLine(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), pLine)
        }

        // Le nœud : un anneau, vert quand tout ce qui pend dessous est juste.
        corps.localToWorld(0f, MobileRegles.ETRIER, a)
        val r = max(5f * dp, 0.045f * scale)
        pFill.style = Paint.Style.FILL
        pFill.color = if (juste) ok else paper
        c.drawCircle(sx(a[0]), sy(a[1]), r, pFill)
        pLine.color = ink
        pLine.strokeWidth = max(1.6f * dp, 0.014f * scale)
        c.drawCircle(sx(a[0]), sy(a[1]), r, pLine)

        // La victoire monte du bas vers le haut : chaque nœud s'allume à son tour, la tige la plus
        // basse d'abord, le nœud du plafond en dernier.
        if (game.gagne && victoireDepuis >= 0f) {
            val p = profondeurs[t] ?: 0
            val t1 = clock - victoireDepuis - (profondeurMax - p) * CASCADE
            if (t1 in 0f..ONDE) {
                val k = t1 / ONDE
                pFill.color = ColorUtils.setAlphaComponent(ok, (170 * (1f - k)).toInt())
                c.drawCircle(sx(a[0]), sy(a[1]), r * (1f + 4f * k), pFill)
                pFill.color = ok
                c.drawCircle(sx(a[0]), sy(a[1]), r * (1f + 0.6f * sin(Math.PI.toFloat() * k)), pFill)
            }
        }
    }

    /** Profondeur de chaque tige sous le plafond (0 pour la première), pour la cascade. */
    private val profondeurs = java.util.IdentityHashMap<MobileTige, Int>()
    private var profondeurMax = 0

    private fun compterProfondeurs() {
        profondeurs.clear()
        profondeurMax = 0
        fun visiter(t: MobileTige, p: Int) {
            profondeurs[t] = p
            profondeurMax = max(profondeurMax, p)
            (t.gauche as? BoutTige)?.let { visiter(it.tige, p + 1) }
            (t.droite as? BoutTige)?.let { visiter(it.tige, p + 1) }
        }
        visiter(game.monde.racine, 0)
    }

    /** Le gain monte au-dessus du mobile et s'efface, une fois la cascade arrivée en haut. */
    private fun dessinerGain(c: Canvas) {
        val texte = gain ?: return
        if (!game.gagne || victoireDepuis < 0f) return
        val t = clock - victoireDepuis - profondeurMax * CASCADE - 0.2f
        if (t < 0f || t > 2.2f) return
        val k = t / 2.2f
        val alpha = (255 * min(1f, 3f * (1f - k))).toInt()
        pTexte.textSize = 40f * dp
        // Dans l'espace libre entre le bas du mobile et l'étagère, au milieu de l'écran.
        val bas = sy(game.bornes[2])
        val etagere = sy(game.casesEtagere[1] + game.rayonMax * game.echelleEtagere)
        val x = viewW / 2f
        val y = (bas + etagere) / 2f + 14f * dp - 30f * dp * k
        // Un liseré couleur papier d'abord : le fil du plafond passe derrière le chiffre.
        pTexte.style = Paint.Style.STROKE
        pTexte.strokeWidth = 6f * dp
        pTexte.color = paper
        pTexte.alpha = alpha
        c.drawText(texte, x, y, pTexte)
        pTexte.style = Paint.Style.FILL
        pTexte.color = ColorUtils.setAlphaComponent(ok, alpha)
        c.drawText(texte, x, y, pTexte)
    }

    private val pTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun dessinerCrochet(c: Canvas, cr: BoutCrochet) {
        val monde = game.monde
        monde.bout(cr, a)
        monde.crochet(cr, b)
        val visee = game.enMain != null && game.cible === cr
        pLine.color = ink
        pLine.strokeWidth = max(1.4f * dp, 0.012f * scale)
        c.drawLine(sx(a[0]), sy(a[1]), sx(b[0]), sy(b[1]), pLine)

        val o = cr.objet
        val corps = monde.corps(cr)
        if (o != null && corps != null) {
            noter(o, corps.x, corps.y)
            dessinerObjet(c, o, corps.x, corps.y, couleur(o), corps.angle)
            if (visee) {
                pLine.color = encre(160)
                pLine.strokeWidth = 2.5f * dp
                c.drawCircle(sx(corps.x), sy(corps.y), o.rayon * scale + 7f * dp, pLine)
            }
            return
        }

        // Un crochet vide : un petit « J » au bout du fil.
        val hx = sx(b[0])
        val hy = sy(b[1])
        val rc = max(6f * dp, 0.05f * scale)
        path.reset()
        path.moveTo(hx, hy - rc * 0.2f)
        path.lineTo(hx, hy + rc)
        path.arcTo(hx - rc * 1.2f, hy, hx, hy + rc * 2f, 0f, 180f, false)
        pLine.strokeWidth = max(2f * dp, 0.016f * scale)
        // Visé, il n'a pas besoin de halo : l'objet en main est déjà dessiné accroché dessus.
        c.drawPath(path, pLine)
    }

    /** Un objet : un disque de couleur, et ses points — autant que son poids. */
    private fun dessinerObjet(
        c: Canvas, o: MobileObjet, x: Float, y: Float, couleur: Int, angle: Float,
        taille: Float = 1f, alpha: Int = 255
    ) {
        val cx = sx(x)
        val cy = sy(y)
        val r = o.rayon * taille * scale
        pFill.style = Paint.Style.FILL
        pFill.color = couleur
        pFill.alpha = alpha
        c.drawCircle(cx, cy, r, pFill)

        pFill.color = points[palette.indexOf(couleur).coerceAtLeast(0)]
        pFill.alpha = alpha
        val pas = r * 0.42f
        val rp = max(1.8f * dp, r * 0.12f)
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        for ((gx, gy) in POINTS[o.masse.coerceIn(1, 9)]) {
            val lx = gx * pas
            val ly = gy * pas
            // Les points tournent avec l'objet ; l'écran a son Y vers le bas.
            val px = cx + lx * cos - ly * sin
            val py = cy - (lx * sin + ly * cos)
            c.drawCircle(px, py, rp, pFill)
        }
    }
}

/** La disposition des points de 1 à 9, sur une grille de 3 × 3 (comme un dé, puis complétée). */
private val POINTS: Array<List<Pair<Int, Int>>> = arrayOf(
    emptyList(),
    listOf(0 to 0),
    listOf(-1 to 1, 1 to -1),
    listOf(-1 to 1, 0 to 0, 1 to -1),
    listOf(-1 to 1, 1 to 1, -1 to -1, 1 to -1),
    listOf(-1 to 1, 1 to 1, 0 to 0, -1 to -1, 1 to -1),
    listOf(-1 to 1, -1 to 0, -1 to -1, 1 to 1, 1 to 0, 1 to -1),
    listOf(-1 to 1, -1 to 0, -1 to -1, 0 to 0, 1 to 1, 1 to 0, 1 to -1),
    listOf(-1 to 1, 0 to 1, 1 to 1, -1 to 0, 1 to 0, -1 to -1, 0 to -1, 1 to -1),
    listOf(-1 to 1, 0 to 1, 1 to 1, -1 to 0, 0 to 0, 1 to 0, -1 to -1, 0 to -1, 1 to -1)
)
