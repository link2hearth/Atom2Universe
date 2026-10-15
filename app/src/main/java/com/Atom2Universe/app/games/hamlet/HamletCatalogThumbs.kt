package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Les vignettes de la boutique de construction. Peindre un bâtiment coûte de quelques millisecondes à plusieurs
 * dizaines, et le catalogue en compte plus de 650 : les peindre à l'ouverture figeait l'écran. Un seul fil
 * d'arrière-plan les prépare à la demande (la dernière demandée d'abord, c'est ce qui est à l'écran) et la vue se
 * redessine dès qu'une est prête. Les vignettes sont déjà à la bonne taille et gardées d'une ouverture à l'autre.
 */
internal class HamletCatalogThumbs(cacheBytes: Int = CACHE_BYTES) {
    private class Job(val key: String, val def: Def, val w: Int, val h: Int, val needed: () -> Boolean)

    private val cache = object : LruCache<String, Bitmap>(cacheBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val lock = Any()
    private val queue = ArrayDeque<Job>()
    private val asked = HashSet<String>()
    private var running = false
    private var refreshPending = false
    @Volatile private var closed = false
    private val main = Handler(Looper.getMainLooper())
    /** Le pinceau du fil d'arrière-plan : lui seul y touche, il n'est pas partagé avec le village. */
    private val art = HamletArt()
    private val pool = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hamlet-thumbs").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }

    /** Appelé sur le fil principal quand de nouvelles vignettes sont prêtes. */
    var onReady: (() -> Unit)? = null

    fun peek(key: String): Bitmap? = cache.get(key)

    /** Demande une vignette ; [needed] dit, au moment de la peindre, si la vue l'attend encore. Sans effet si déjà demandée. */
    fun request(def: Def, w: Int, h: Int, key: String, needed: () -> Boolean) {
        if (closed || w <= 8 || h <= 8) return
        synchronized(lock) {
            if (!asked.add(key)) return
            queue.addFirst(Job(key, def, w, h, needed))
            if (!running) { running = true; pool.execute(::drain) }
        }
    }

    /** Abandonne ce qui n'a pas encore été peint (la fenêtre se ferme) ; les vignettes prêtes restent. */
    fun cancel() = synchronized(lock) {
        for (job in queue) asked.remove(job.key)
        queue.clear()
    }

    fun close() {
        closed = true
        onReady = null
        synchronized(lock) { queue.clear(); asked.clear() }
        pool.shutdownNow()
        cache.evictAll()
    }

    private fun drain() {
        while (true) {
            val job = synchronized(lock) { queue.pollFirst() ?: run { running = false; null } } ?: return
            if (closed) return
            if (!job.needed()) { synchronized(lock) { asked.remove(job.key) }; continue }
            // Une vignette qui échoue reste « demandée » : la vue ne la réclame pas à chaque image.
            val bmp = try { paint(job) } catch (e: Throwable) { null } ?: continue
            cache.put(job.key, bmp)
            synchronized(lock) { asked.remove(job.key) }
            announce()
        }
    }

    private fun announce() {
        synchronized(lock) { if (refreshPending) return; refreshPending = true }
        main.post {
            synchronized(lock) { refreshPending = false }
            if (!closed) onReady?.invoke()
        }
    }

    private fun paint(job: Job): Bitmap {
        val d = job.def
        if (d.isPath) {
            val bmp = Bitmap.createBitmap(job.w, job.h, Bitmap.Config.ARGB_8888)
            Iso(Canvas(bmp), job.w / 2f, job.h / 2f).top(0f, 0f, 0.7f, 0.7f, 0f, d.look.a)
            return bmp
        }
        val sprite = art.forDef(d)
        val scale = min((job.w - 8f) / sprite.bmp.width, (job.h - 8f) / sprite.bmp.height)
        val tw = max(1, (sprite.bmp.width * scale).roundToInt())
        val th = max(1, (sprite.bmp.height * scale).roundToInt())
        val thumb = Bitmap.createScaledBitmap(sprite.bmp, tw, th, true)
        art.clear()
        return thumb
    }

    companion object {
        private const val CACHE_BYTES = 14 * 1024 * 1024
        fun key(def: Def, w: Int, h: Int) = "${def.id}|$w|$h"
    }
}
