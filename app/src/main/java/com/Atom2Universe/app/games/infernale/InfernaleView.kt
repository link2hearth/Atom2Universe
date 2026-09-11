package com.Atom2Universe.app.games.infernale

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.Shape

/**
 * La vue du tableau : le monde vu de cote, et le doigt qui pose les pieces.
 *
 * ## Pourquoi on dessine les corps et pas les jolies vignettes
 *
 * La bibliotheque `art/` sait dessiner cent vingt-six pieces, et elles sont belles. Mais
 * ce sont des **icones** : des silhouettes centrees dans un carre, qui ne suivent pas les
 * cotes reelles d'une rampe de deux metres ni l'angle d'un domino en train de tomber.
 * Les montrer sur le tableau mentirait sur ce que fait la physique, et un casse-tete ou
 * l'image ne correspond pas au calcul est injouable.
 *
 * Ici on dessine donc les corps eux-memes, tels que le moteur les voit. Les vignettes
 * gardent leur emploi la ou elles sont justes : dans la reserve, en bas, ou une piece
 * n'est encore qu'un choix.
 */
class InfernaleView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : SurfaceView(ctx, attrs), SurfaceHolder.Callback, Runnable {

    interface Listener {
        fun surVictoire()
        fun surChangement()
    }

    var listener: Listener? = null

    /** La partie en cours. Toute lecture depuis le fil de rendu passe par [verrou]. */
    private var partie: Partie? = null
    private val verrou = Object()

    /** Le type que le doigt s'apprete a poser, ou `null` s'il ne pose rien. */
    var typeChoisi: TypePiece? = null
        set(value) {
            field = value
            apercu = null
        }

    /** Ou la piece choisie irait, et si elle y a le droit. */
    private var apercu: Pose? = null
    private var apercuRefus = Refus.OK

    private var fil: Thread? = null
    @Volatile private var tourne = false
    private var dernier = 0L
    private var reste = 0f
    private var victoireAnnoncee = false

    private val fond = Paint().apply { color = 0xFF0B1020.toInt() }
    private val sol = Paint().apply { color = 0xFF2A3550.toInt() }
    private val scelleP = Paint().apply { color = 0xFF8FA6C8.toInt(); isAntiAlias = true }
    private val libreP = Paint().apply { color = 0xFFE0B07A.toInt(); isAntiAlias = true }
    private val contour = Paint().apply {
        color = 0xFF121A2E.toInt(); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val billeP = Paint().apply { color = 0xFFF2F6FF.toInt(); isAntiAlias = true }
    private val zoneP = Paint().apply { color = 0x3355E08A; isAntiAlias = true }
    private val zoneGagneP = Paint().apply { color = 0xAA55E08A.toInt(); isAntiAlias = true }
    private val apercuOk = Paint().apply { color = 0x8855E08A.toInt(); isAntiAlias = true }
    private val apercuNon = Paint().apply { color = 0x88E05555.toInt(); isAntiAlias = true }

    private val coins = FloatArray(8)
    private val centre = FloatArray(2)
    private val trace = Path()

    private var echelle = 40f
    private var origineX = 0f
    private var basY = 0f

    init {
        holder.addCallback(this)
        // Voir la liste de controle des vues de jeu : canevas materiel, un seul fil,
        // et un plafond sur le pas de temps.
        setWillNotDraw(true)
    }

    fun jouer(nouvelle: Partie) {
        synchronized(verrou) {
            partie = nouvelle
            victoireAnnoncee = false
            apercu = null
        }
        // **Recadrer ici aussi**, et pas seulement quand la surface change de taille.
        // Le tableau est tire sur un fil a part, donc il arrive presque toujours apres
        // que la surface a pris ses mesures : sans ce rappel, le cadrage garde son
        // echelle par defaut et le monde s'affiche a la mauvaise taille jusqu'a ce que
        // l'ecran tourne.
        if (largeurVue > 0 && hauteurVue > 0) cadrer(largeurVue, hauteurVue)
    }

    fun partieCourante(): Partie? = synchronized(verrou) { partie }

    /** Execute [action] sur la partie sans risquer que le fil de rendu la lise entre-temps. */
    fun <T> surPartie(action: (Partie) -> T): T? = synchronized(verrou) {
        partie?.let(action)
    }

    // ── Boucle ───────────────────────────────────────────────────────────────

    override fun surfaceCreated(h: SurfaceHolder) = reprendre()
    override fun surfaceDestroyed(h: SurfaceHolder) = suspendre()
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
        largeurVue = w
        hauteurVue = ht
        cadrer(w, ht)
    }

    private var largeurVue = 0
    private var hauteurVue = 0

    fun reprendre() {
        if (tourne) return
        tourne = true
        dernier = System.nanoTime()
        reste = 0f
        fil = Thread(this, "InfernalePhysique").also { it.start() }
    }

    fun suspendre() {
        tourne = false
        fil?.join(500)
        fil = null
    }

    override fun run() {
        while (tourne) {
            val maintenant = System.nanoTime()
            // Le plafond est ce qui empeche une image en retard — un changement
            // d'application, un ramasse-miettes — de faire avancer la machine d'un
            // demi-seconde d'un coup et de tout traverser.
            val ecoule = ((maintenant - dernier) / 1_000_000_000f).coerceAtMost(0.1f)
            dernier = maintenant

            var gagneMaintenant = false
            synchronized(verrou) {
                val p = partie
                if (p != null && p.lancee) {
                    reste += ecoule
                    while (reste >= PAS) {
                        p.avancer(PAS)
                        reste -= PAS
                    }
                    if (p.gagne && !victoireAnnoncee) {
                        victoireAnnoncee = true
                        gagneMaintenant = true
                    }
                }
            }
            if (gagneMaintenant) post { listener?.surVictoire() }

            val canevas = verrouillerCanevas()
            if (canevas == null) {
                Thread.sleep(16)
                continue
            }
            try {
                synchronized(verrou) { peindre(canevas) }
            } finally {
                holder.unlockCanvasAndPost(canevas)
            }
            Thread.sleep(2)
        }
    }

    private fun verrouillerCanevas(): Canvas? = try {
        // Canevas materiel : c'est lui qui evite que Skia rastérise les formes au
        // processeur, et la difference se compte en dizaines d'images par seconde.
        if (holder.surface?.isValid == true) holder.lockHardwareCanvas() else null
    } catch (_: IllegalStateException) {
        null
    }

    // ── Cadrage ──────────────────────────────────────────────────────────────

    private fun cadrer(w: Int, h: Int) {
        val p = synchronized(verrou) { partie } ?: return
        if (w <= 0 || h <= 0) return
        val largeurMonde = p.plateau.largeur * 0.72f
        echelle = w / largeurMonde
        origineX = w / 2f
        basY = h * 0.88f
    }

    private fun ex(x: Float) = origineX + x * echelle
    private fun ey(y: Float) = basY - y * echelle

    // ── Peinture ─────────────────────────────────────────────────────────────

    private fun peindre(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fond)
        val p = partie ?: return
        c.drawRect(0f, ey(0f), width.toFloat(), height.toFloat(), sol)

        p.plateau.bouton?.let { b ->
            val d = if (b.declenche) zoneGagneP else zoneP
            corpsRect(b.zone, 0, coins)
            dessinerCoins(c, coins, d)
        }

        for (piece in p.plateau.pieces) {
            for (i in piece.corps.indices) {
                val corps = piece.corps[i]
                dessinerCorps(c, corps, if (corps.immovable) scelleP else libreP)
            }
        }

        p.plateau.bille?.let { b ->
            b.partWorld(0, centre)
            c.drawCircle(ex(centre[0]), ey(centre[1]), b.parts[0].radius * echelle, billeP)
        }

        apercu?.let { pose ->
            val fantome = pose.creer()
            val teinte = if (apercuRefus == Refus.OK) apercuOk else apercuNon
            for (corps in fantome.corps) dessinerCorps(c, corps, teinte)
        }
    }

    private fun dessinerCorps(c: Canvas, corps: PhysBody, peinture: Paint) {
        for (i in corps.parts.indices) {
            if (corps.parts[i].shape == Shape.CIRCLE) {
                corps.partWorld(i, centre)
                val r = corps.parts[i].radius * echelle
                c.drawCircle(ex(centre[0]), ey(centre[1]), r, peinture)
                c.drawCircle(ex(centre[0]), ey(centre[1]), r, contour)
            } else {
                corpsRect(corps, i, coins)
                dessinerCoins(c, coins, peinture)
            }
        }
    }

    private fun corpsRect(corps: PhysBody, part: Int, out: FloatArray) {
        corps.partCorners(part, out)
    }

    private fun dessinerCoins(c: Canvas, pts: FloatArray, peinture: Paint) {
        trace.rewind()
        trace.moveTo(ex(pts[0]), ey(pts[1]))
        trace.lineTo(ex(pts[2]), ey(pts[3]))
        trace.lineTo(ex(pts[4]), ey(pts[5]))
        trace.lineTo(ex(pts[6]), ey(pts[7]))
        trace.close()
        c.drawPath(trace, peinture)
        c.drawPath(trace, contour)
    }

    // ── Doigt ────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val mx = (event.x - origineX) / echelle
        val my = (basY - event.y) / echelle

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val type = typeChoisi ?: return true
                synchronized(verrou) {
                    val p = partie ?: return true
                    val pose = poseA(type, mx, my)
                    apercu = pose
                    apercuRefus = p.verifier(pose)
                }
            }

            MotionEvent.ACTION_UP -> {
                val type = typeChoisi
                synchronized(verrou) {
                    val p = partie ?: return true
                    if (type == null) {
                        // Sans piece choisie, un appui reprend ce qu'on touche.
                        val index = pieceSous(p, mx, my)
                        if (index >= 0) p.reprendre(index)
                    } else {
                        p.poser(poseA(type, mx, my))
                        if (p.stock(type) <= 0) typeChoisi = null
                    }
                    apercu = null
                }
                post { listener?.surChangement() }
            }

            MotionEvent.ACTION_CANCEL -> synchronized(verrou) { apercu = null }
        }
        return true
    }

    /**
     * Ou tombe une piece quand le doigt est la.
     *
     * Les pieces libres et mixtes se posent **par leur base** : on pose un domino sur un
     * sol, on ne vise pas son centre. Les pieces scellees, elles, se posent par leur
     * centre, puisqu'elles flottent ou l'on veut.
     */
    private fun poseA(type: TypePiece, x: Float, y: Float): Pose = when (type) {
        TypePiece.RAMPE -> Pose(type, x, y, reglage = penteRampe)
        TypePiece.PLOT, TypePiece.BLOC -> Pose(type, x, y)
        else -> Pose(type, x, y.coerceAtLeast(0f))
    }

    /** Pente appliquee aux rampes posees. Reglee par l'activite. */
    var penteRampe = 20f

    private fun pieceSous(p: Partie, x: Float, y: Float): Int {
        val placees = p.placees()
        for (i in placees.indices.reversed()) {
            val piece = p.plateau.pieces.getOrNull(i) ?: continue
            for (corps in piece.corps) {
                corps.updateAabb()
                if (x >= corps.aabbMinX && x <= corps.aabbMaxX &&
                    y >= corps.aabbMinY && y <= corps.aabbMaxY
                ) return i
            }
        }
        return -1
    }

    private companion object {
        /** Pas de simulation, fixe : c'est ce qui rend une partie reproductible. */
        const val PAS = 1f / 120f
    }
}
