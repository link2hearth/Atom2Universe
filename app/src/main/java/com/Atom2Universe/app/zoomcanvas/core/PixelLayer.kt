package com.Atom2Universe.app.zoomcanvas.core

import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Ce qu'une écriture de pixels emporte : les tuiles dont le contenu a changé (une copie de leurs
 * pixels, qu'on peut donc écrire sur un autre fil) et celles qui sont devenues vides.
 */
class PixelDelta(val upserts: List<Pair<Long, IntArray>>, val deletes: LongArray) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletes.isEmpty()

    companion object {
        val EMPTY = PixelDelta(emptyList(), LongArray(0))
    }
}

/**
 * La couche de pixels du « Canvas » : une grille de cases d'une unité, infinie dans tous les sens,
 * rangée par tuiles de [SIZE] × [SIZE] cases (seules celles qui ont de la couleur existent). Une case
 * vaut 0 (transparente) ou une couleur ARGB. C'est un bloc à part du dessin vectoriel : la gomme et
 * les traits ne s'y touchent pas.
 *
 * Les cases sont des `Int` : la grille ne va pas plus loin que ±[LIMIT] cases, ce qui laisse de la
 * marge (une case fait au moins 1/100 de pixel d'écran).
 */
class PixelLayer {

    class Tile(val tx: Int, val ty: Int) {
        val px = IntArray(SIZE * SIZE)
        /** Cases colorées dans cette tuile : à zéro, la tuile disparaît. */
        var count = 0
            internal set
        /** Change à chaque modification (et à la création) : le rendu s'en sert pour garder ses images à jour. */
        var version = 0
            internal set
    }

    private val tiles = HashMap<Long, Tile>()
    private val dirty = HashSet<Long>()
    private val removed = HashSet<Long>()
    private var clock = 0

    val tileCount: Int get() = tiles.size
    val isEmpty: Boolean get() = tiles.isEmpty()
    val allTiles: Collection<Tile> get() = tiles.values
    val hasUnsaved: Boolean get() = dirty.isNotEmpty() || removed.isNotEmpty()

    fun tile(tx: Int, ty: Int): Tile? = tiles[tileKey(tx, ty)]

    fun get(x: Int, y: Int): Int = tiles[tileKey(x shr SHIFT, y shr SHIFT)]?.px?.get(index(x, y)) ?: 0

    /** Peint la case ([x], [y]) de [color] (0 ou un alpha nul : la vide). Faux si rien n'a changé. */
    fun set(x: Int, y: Int, color: Int): Boolean {
        if (x < -LIMIT || x > LIMIT || y < -LIMIT || y > LIMIT) return false
        val c = if (color ushr 24 == 0) 0 else color
        val key = tileKey(x shr SHIFT, y shr SHIFT)
        var t = tiles[key]
        val i = index(x, y)
        val old = t?.px?.get(i) ?: 0
        if (old == c) return false
        if (t == null) {
            t = Tile(x shr SHIFT, y shr SHIFT)
            tiles[key] = t
        }
        t.px[i] = c
        if (old == 0) t.count++ else if (c == 0) t.count--
        t.version = ++clock
        if (t.count == 0) {
            tiles.remove(key)
            dirty.remove(key)
            removed.add(key)
        } else {
            dirty.add(key)
            removed.remove(key)
        }
        return true
    }

    /** La boîte des cases colorées (x0, y0, x1, y1 : coins inclus), ou null s'il n'y en a pas. */
    fun bounds(): IntArray? {
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = Int.MIN_VALUE
        var y1 = Int.MIN_VALUE
        for (t in tiles.values) {
            for (j in 0 until SIZE) for (i in 0 until SIZE) {
                if (t.px[j * SIZE + i] == 0) continue
                val x = (t.tx shl SHIFT) + i
                val y = (t.ty shl SHIFT) + j
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
        }
        return if (x0 == Int.MAX_VALUE) null else intArrayOf(x0, y0, x1, y1)
    }

    /** Prend ce qui a changé depuis le dernier appel, pour l'écrire (copies : les tuiles continuent de vivre). */
    fun drain(): PixelDelta {
        if (!hasUnsaved) return PixelDelta.EMPTY
        val up = ArrayList<Pair<Long, IntArray>>(dirty.size)
        for (k in dirty) tiles[k]?.let { up.add(k to it.px.copyOf()) }
        val del = removed.toLongArray()
        dirty.clear()
        removed.clear()
        return PixelDelta(up, del)
    }

    /** L'écriture de [d] a échoué : ses tuiles repartent (celles qui ont encore changé depuis sont déjà notées). */
    fun requeue(d: PixelDelta) {
        for ((k, _) in d.upserts) if (tiles.containsKey(k)) { dirty.add(k); removed.remove(k) }
        for (k in d.deletes) if (!tiles.containsKey(k)) { removed.add(k); dirty.remove(k) }
    }

    /** Remplace tout (lecture d'un projet) : ce qu'on vient de lire est déjà écrit. */
    fun load(list: List<Pair<Long, IntArray>>) {
        tiles.clear()
        dirty.clear()
        removed.clear()
        for ((k, px) in list) {
            if (px.size != SIZE * SIZE) continue
            val t = Tile(tileX(k), tileY(k))
            System.arraycopy(px, 0, t.px, 0, px.size)
            t.count = px.count { it != 0 }
            if (t.count == 0) continue
            t.version = ++clock
            tiles[k] = t
        }
    }

    companion object {
        const val SHIFT = 6
        const val SIZE = 1 shl SHIFT
        /** Les cases vont de −LIMIT à +LIMIT. */
        const val LIMIT = 1 shl 28

        private fun index(x: Int, y: Int) = ((y and (SIZE - 1)) shl SHIFT) or (x and (SIZE - 1))

        fun tileKey(tx: Int, ty: Int): Long = (tx.toLong() shl 32) or (ty.toLong() and 0xFFFFFFFFL)
        fun tileX(key: Long): Int = (key shr 32).toInt()
        fun tileY(key: Long): Int = key.toInt()

        /** Une case ([x], [y]) sur 64 bits : sert de clé à l'historique. */
        fun cellKey(x: Int, y: Int): Long = tileKey(x, y)
        fun cellX(key: Long): Int = tileX(key)
        fun cellY(key: Long): Int = tileY(key)

        /** Les pixels d'une tuile vers les octets d'une ligne de la base (compressés : une tuile presque vide pèse quelques dizaines d'octets). */
        fun encode(px: IntArray): ByteArray {
            val raw = ByteBuffer.allocate(px.size * 4).also { it.asIntBuffer().put(px) }.array()
            val d = Deflater(Deflater.BEST_SPEED)
            d.setInput(raw)
            d.finish()
            val out = java.io.ByteArrayOutputStream(1024)
            val buf = ByteArray(4096)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
            return out.toByteArray()
        }

        /** Les octets d'une ligne de la base vers les pixels d'une tuile. Null s'ils sont abîmés (une tuile perdue, pas le projet). */
        fun decode(data: ByteArray): IntArray? = try {
            val inf = Inflater()
            inf.setInput(data)
            val raw = ByteArray(SIZE * SIZE * 4)
            var n = 0
            while (n < raw.size && !inf.finished()) {
                val r = inf.inflate(raw, n, raw.size - n)
                if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break
                n += r
            }
            inf.end()
            if (n != raw.size) null else IntArray(SIZE * SIZE).also { ByteBuffer.wrap(raw).asIntBuffer().get(it) }
        } catch (e: Exception) {
            null
        }
    }
}
