package com.Atom2Universe.app.zoomcanvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.SystemClock
import com.Atom2Universe.app.zoomcanvas.core.CacheWindow
import com.Atom2Universe.app.zoomcanvas.core.Entry
import com.Atom2Universe.app.zoomcanvas.core.Layer
import com.Atom2Universe.app.zoomcanvas.core.RenderList
import com.Atom2Universe.app.zoomcanvas.core.ZoomRenderer
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Le cache raster d'une couche trop dense pour être tracée trait par trait.
 *
 * Dézoomé sur un grand tableau, des dizaines de milliers de traits tiennent dans l'écran, et le
 * moteur graphique ne peut pas tous les tracer à chaque image. On les cuit donc une fois dans un
 * bitmap ([front]) qu'on se contente de déplacer et d'agrandir, et qu'on recuit **hors du fil de
 * l'écran** ([bake]) quand on s'est trop éloigné de ce qu'il montre : le geste reste fluide, l'image
 * rattrape. En attendant elle est un peu floue, ou laisse voir un bord vide.
 *
 * Il se tient à jour tout seul ([changed]) : un trait posé se trace dans le bitmap ; un trait
 * retiré, déplacé ou remonté fait recalculer la petite zone qu'il touchait (tout ce qui la croise,
 * dans l'ordre). Les changements arrivés pendant une cuisson sont rejoués sur son résultat.
 *
 * Tout se passe sur le fil de l'écran, sauf [bake]. Ce qu'on cuit vient d'un instantané de
 * l'index de la couche ([Layer.snapshot]), dont les entrées sont immuables.
 */
class LayerCache(
    private val layer: Layer,
    private val host: Host,
    private val maxPixels: Long,
) : Layer.Observer {

    /** Ce dont le cache a besoin de la vue qui le porte. */
    interface Host {
        val imageProvider: ((String) -> Bitmap?)?
        val typefaceProvider: ((String, Int) -> Typeface)?
        /** Redessiner (une cuisson vient de finir). */
        fun cacheReady()
        /** Exécuter sur le fil de l'écran. */
        fun runOnMain(r: Runnable)
    }

    private class Patch(val removed: Entry?, val added: Entry?, val append: Boolean)

    private var front: Bitmap? = null
    private var frontWin: CacheWindow? = null
    private var spare: Bitmap? = null
    private var baking = false
    private var stale = false
    @Volatile private var cancelled = false
    private var released = false
    /** Après une cuisson ratée (mémoire), on ne réessaie pas avant cette heure. */
    private var retryAt = 0L
    private val log = ArrayList<Patch>()

    // La dernière vue demandée : une cuisson qui finit relance la suivante depuis là.
    private var lastCx = 0.0
    private var lastCy = 0.0
    private var lastZ = 1.0
    private var lastW = 0.0
    private var lastH = 0.0

    var lastUsed = 0L

    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val transform = CacheWindow.Transform()

    // Pour les petites mises à jour, sur le fil de l'écran (la cuisson a les siens).
    private val builder = ZoomRenderer.Builder()
    private val runs = RenderList()
    private val painter = RunPainter().also { it.imageProvider = host.imageProvider; it.typefaceProvider = host.typefaceProvider }
    private val entries = ArrayList<Entry>()

    init {
        layer.observer = this
    }

    /**
     * Pose le cache sur [canvas] pour une caméra ([cx], [cy], [z]) dans une vue [viewW]×[viewH], et
     * lance une cuisson si ce qu'il montre ne suffit plus. Ne montre rien tant que la première
     * cuisson n'est pas finie.
     */
    fun draw(canvas: Canvas, cx: Double, cy: Double, z: Double, viewW: Double, viewH: Double, alpha: Int) {
        lastCx = cx; lastCy = cy; lastZ = z; lastW = viewW; lastH = viewH
        val bmp = front
        val win = frontWin
        if (bmp != null && win != null) {
            win.transformTo(cx, cy, z, viewW, viewH, transform)
            matrix.reset()
            matrix.postScale(transform.scale.toFloat(), transform.scale.toFloat())
            matrix.postTranslate(transform.tx.toFloat(), transform.ty.toFloat())
            paint.alpha = alpha
            canvas.drawBitmap(bmp, matrix, paint)
        }
        if (!baking && SystemClock.uptimeMillis() >= retryAt && needsBake(cx, cy, z, viewW, viewH)) startBake()
    }

    private fun needsBake(cx: Double, cy: Double, z: Double, viewW: Double, viewH: Double): Boolean {
        val win = frontWin ?: return true
        if (stale) return true
        if (!win.sharpEnough(z)) return true
        // Bien avant que le bord ne se voie : la moitié de la réserve de chaque côté.
        val reserve = 0.5 * (CacheWindow.MARGIN - 1) / 2 * min(viewW, viewH)
        return !win.covers(cx, cy, z, viewW, viewH, reserve, transform)
    }

    // ---- Cuisson -----------------------------------------------------------------------------

    private fun startBake() {
        if (released || lastW <= 0.0 || lastH <= 0.0) return
        baking = true
        stale = false
        log.clear()
        val win = CacheWindow.around(lastCx, lastCy, lastZ, lastW, lastH, maxPixels)
        val snap = layer.snapshot()
        val reuse = spare?.takeIf { !it.isRecycled && it.width == win.w && it.height == win.h }
        if (reuse == null) spare?.recycle()
        spare = null
        cancelled = false
        val provider = host.imageProvider
        val faces = host.typefaceProvider
        EXECUTOR.execute { bake(win, snap, reuse, provider, faces) }
    }

    /** Sur le fil de cuisson : trace tout ce que la fenêtre touche, par paquets (la mémoire reste bornée). */
    private fun bake(win: CacheWindow, snap: Layer.Snapshot, reuse: Bitmap?, images: ((String) -> Bitmap?)?, faces: ((String, Int) -> Typeface)?) {
        var bmp: Bitmap? = null
        try {
            bmp = reuse ?: Bitmap.createBitmap(win.w, win.h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(0)
            val canvas = Canvas(bmp)
            val builder = ZoomRenderer.Builder()
            val runs = RenderList()
            val painter = RunPainter().also { it.imageProvider = images; it.typefaceProvider = faces }
            val all = ArrayList<Entry>()
            val (x0, y0, x1, y1) = worldRect(win, 4.0)
            snap.query(x0, y0, x1, y1, all)
            var from = 0
            while (from < all.size) {
                if (cancelled) { host.runOnMain { finish(null, win) }; return }
                val to = min(all.size, from + BAKE_CHUNK)
                builder.renderEntries(all.subList(from, to), win.vx, win.vy, win.zoom, win.w.toDouble(), win.h.toDouble(), runs)
                painter.drawRuns(canvas, runs, 0, runs.runCount)
                from = to
            }
            val done = bmp
            bmp = null
            host.runOnMain { finish(done, win) }
        } catch (e: Throwable) {
            // Pas assez de mémoire, ou autre : pas de cache cette fois, on retentera au prochain besoin.
            bmp?.takeIf { it !== reuse }?.recycle()
            host.runOnMain { finish(null, win) }
        }
    }

    /** Le rectangle de la couche que la fenêtre montre, élargi de [padPx] pixels du cache. */
    private fun worldRect(win: CacheWindow, padPx: Double): DoubleArray {
        val hw = (win.w / 2.0 + padPx) / win.zoom
        val hh = (win.h / 2.0 + padPx) / win.zoom
        return doubleArrayOf(win.vx - hw, win.vy - hh, win.vx + hw, win.vy + hh)
    }

    /** La cuisson est finie ([bmp] null : abandonnée ou ratée). Sur le fil de l'écran. */
    private fun finish(bmp: Bitmap?, win: CacheWindow) {
        baking = false
        if (released) { bmp?.recycle(); return }
        if (bmp != null) {
            // Ce qui a changé pendant la cuisson n'est pas dans l'instantané : on le rejoue.
            for (p in log) applyPatch(bmp, win, p)
            log.clear()
            val old = front
            front = bmp
            frontWin = win
            spare?.recycle()
            spare = old
            host.cacheReady()
        } else if (!cancelled) {
            // Raté : on n'insiste pas tout de suite.
            retryAt = SystemClock.uptimeMillis() + RETRY_MS
            return
        }
        cancelled = false
        // La vue a peut-être déjà bougé plus loin que cette fenêtre : on enchaîne.
        if (needsBake(lastCx, lastCy, lastZ, lastW, lastH)) startBake()
    }

    // ---- Tenir à jour ------------------------------------------------------------------------

    override fun changed(removed: Entry?, added: Entry?) {
        // Posé tout devant ? Alors il suffit de le tracer par-dessus. Sinon il faudra recalculer sa zone.
        val append = removed == null && added != null && layer.entries().lastOrNull() === added
        val patch = Patch(removed, added, append)
        if (baking) log.add(patch)
        val bmp = front ?: return
        val win = frontWin ?: return
        applyPatch(bmp, win, patch)
    }

    override fun reset() {
        // Tout a changé : ce qu'on a cuit ne vaut plus, ni la cuisson en cours.
        stale = true
        if (baking) cancelled = true
        log.clear()
    }

    private fun applyPatch(bmp: Bitmap, win: CacheWindow, p: Patch) {
        if (p.append && p.added != null && !p.added.dead) {
            entries.clear()
            entries.add(p.added)
            paintEntries(bmp, win, null)
            return
        }
        // La zone touchée : l'ancien et le nouveau.
        var x0 = Double.POSITIVE_INFINITY; var y0 = Double.POSITIVE_INFINITY
        var x1 = Double.NEGATIVE_INFINITY; var y1 = Double.NEGATIVE_INFINITY
        for (e in arrayOf(p.removed, p.added)) {
            if (e == null) continue
            x0 = min(x0, e.x0); y0 = min(y0, e.y0); x1 = max(x1, e.x1); y1 = max(y1, e.y1)
        }
        if (x1 < x0) return
        val pad = 3.0 / win.zoom
        x0 -= pad; y0 -= pad; x1 += pad; y1 += pad
        if (layer.countIn(x0, y0, x1, y1) > MAX_PATCH_ENTRIES) {
            // Trop à recalculer sur le fil de l'écran : on recuira tout, au repos.
            stale = true
            return
        }
        layer.query(x0, y0, x1, y1, entries)
        paintEntries(bmp, win, doubleArrayOf(x0, y0, x1, y1))
    }

    /**
     * Trace [entries] dans [bmp] ; avec [region] (rectangle de la couche), on efface d'abord cette
     * zone et on n'y trace que ça (c'est la mise à jour d'une zone) ; sans, on trace par-dessus.
     */
    private fun paintEntries(bmp: Bitmap, win: CacheWindow, region: DoubleArray?) {
        val canvas = Canvas(bmp)
        if (region != null) {
            val l = floor((region[0] - win.vx) * win.zoom + win.w / 2.0).toInt().coerceIn(0, win.w)
            val t = floor((region[1] - win.vy) * win.zoom + win.h / 2.0).toInt().coerceIn(0, win.h)
            val r = ceil((region[2] - win.vx) * win.zoom + win.w / 2.0).toInt().coerceIn(0, win.w)
            val b = ceil((region[3] - win.vy) * win.zoom + win.h / 2.0).toInt().coerceIn(0, win.h)
            if (r <= l || b <= t) { entries.clear(); return }
            canvas.clipRect(l, t, r, b)
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        }
        builder.renderEntries(entries, win.vx, win.vy, win.zoom, win.w.toDouble(), win.h.toDouble(), runs)
        painter.drawRuns(canvas, runs, 0, runs.runCount)
        entries.clear()
        // Le moteur graphique doit relire les pixels de ce bitmap.
        bmp.prepareToDraw()
    }

    /** Une image du projet vient de se charger : celles qu'on a cuites avec un cadre gris sont à refaire. */
    fun invalidate() {
        stale = true
        if (baking) cancelled = true
    }

    fun release() {
        released = true
        cancelled = true
        layer.observer = null
        if (!baking) {
            front?.recycle(); spare?.recycle()
        }
        // En pleine cuisson : le bitmap en cours est recyclé à sa fin (voir [finish]) ; ceux-ci ne servent plus, on les lâche.
        front = null; spare = null; frontWin = null
    }

    private companion object {
        /** Une seule cuisson à la fois, au calme : elle ne doit pas gêner le fil de l'écran. */
        val EXECUTOR = Executors.newSingleThreadExecutor { r ->
            Thread(r, "zoom-layer-cache").apply { priority = Thread.NORM_PRIORITY - 2; isDaemon = true }
        }
        /** Éléments tracés d'un coup à la cuisson : assez pour aller vite, peu pour garder le tableau de passes petit. */
        const val BAKE_CHUNK = 2000
        /** Au-delà, recalculer une zone sur le fil de l'écran coûte trop : on recuit tout. */
        const val MAX_PATCH_ENTRIES = 2500
        const val RETRY_MS = 3000L
    }
}

/**
 * Les caches raster des couches d'une vue : un par couche dense, et peu à la fois (chacun est un
 * grand bitmap). Un cache qu'on n'a pas utilisé depuis un moment est libéré.
 */
class LayerCacheManager(private val host: LayerCache.Host) {
    private val caches = HashMap<Layer, LayerCache>()

    /** Pixels qu'un cache peut occuper : de quoi garder la mémoire raisonnable, quelle que soit la taille de l'écran. */
    private val maxPixels: Long = (Runtime.getRuntime().maxMemory() / 64).coerceIn(2_000_000L, 8_000_000L)

    fun cacheFor(layer: Layer, now: Long): LayerCache {
        val c = caches.getOrPut(layer) { LayerCache(layer, host, maxPixels) }
        c.lastUsed = now
        return c
    }

    /** Libère ceux qu'on n'utilise plus (appelé à chaque image). */
    fun trim(now: Long) {
        if (caches.isEmpty()) return
        val it = caches.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value.lastUsed > UNUSED_MS) { e.value.release(); it.remove() }
        }
    }

    fun invalidateAll() {
        for (c in caches.values) c.invalidate()
    }

    fun clear() {
        for (c in caches.values) c.release()
        caches.clear()
    }

    private companion object {
        const val UNUSED_MS = 4000L
    }
}
