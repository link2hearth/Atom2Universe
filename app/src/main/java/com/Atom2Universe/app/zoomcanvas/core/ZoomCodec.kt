package com.Atom2Universe.app.zoomcanvas.core

import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Un élément d'une couche ⇄ les octets rangés dans sa ligne de la base. Le type est rangé à part
 * (colonne `type`), l'identifiant aussi : le blob ne garde que le contenu.
 *
 * Les points d'un trait sont écrits tels quels, en `Float` : c'est ce que la mémoire garde, et ce
 * qui suffit (voir [Stroke]). Les formes et les textes sont rangés par numéro d'ordre de leurs
 * énumérations ([ShapeKind], [ShapeFill]) : ne jamais réordonner ces énumérations.
 */
object ZoomCodec {
    const val STROKE = 0
    const val IMAGE = 1
    const val SHAPE = 2
    const val TEXT = 3

    fun typeOf(item: LayerItem): Int = when (item) {
        is Stroke -> STROKE
        is ImageItem -> IMAGE
        is ShapeItem -> SHAPE
        is TextItem -> TEXT
        is StrokeBox -> STROKE
    }

    fun encode(item: LayerItem): ByteArray = when (item) {
        is Stroke -> encodeStroke(item)
        is StrokeBox -> encodeStroke(item.stroke)
        is ImageItem -> {
            val key = item.key.toByteArray(Charsets.UTF_8)
            buffer(4 + key.size + 8 + 32).apply {
                putInt(key.size); put(key)
                putInt(item.pxW); putInt(item.pxH)
                putDouble(item.x); putDouble(item.y); putDouble(item.w); putDouble(item.h)
            }.array()
        }
        is ShapeItem -> buffer(1 + 1 + 4 + 4 + 1 + 8 + 32).apply {
            put(item.kind.ordinal.toByte())
            put(((if (item.flipX) 1 else 0) or (if (item.flipY) 2 else 0)).toByte())
            putInt(item.strokeColor); putInt(item.fillColor)
            put(item.fill.ordinal.toByte())
            putDouble(item.width)
            putDouble(item.x); putDouble(item.y); putDouble(item.w); putDouble(item.h)
        }.array()
        is TextItem -> {
            val text = item.text.toByteArray(Charsets.UTF_8)
            val font = item.font.toByteArray(Charsets.UTF_8)
            buffer(4 + text.size + 32 + 8 + 4 + 1 + 4 + font.size).apply {
                putInt(text.size); put(text)
                putDouble(item.x); putDouble(item.y); putDouble(item.w); putDouble(item.h)
                putDouble(item.fontSize)
                putInt(item.color)
                put(item.style.toByte())
                putInt(font.size); put(font)
            }.array()
        }
    }

    private fun encodeStroke(s: Stroke): ByteArray {
        val n = s.pts.size
        val b = buffer(4 + 8 + 1 + 16 + 4 + 4 * n)
        b.putInt(s.color); b.putDouble(s.width); b.put(s.kind.toByte())
        b.putDouble(s.x); b.putDouble(s.y)
        b.putInt(n)
        b.asFloatBuffer().put(s.pts)
        return b.array()
    }

    private fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    /** L'élément [id] de type [type] relu depuis [data], ou null si les octets sont abîmés (on n'invente rien). */
    fun decode(type: Int, id: Long, data: ByteArray): LayerItem? = try {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        when (type) {
            STROKE -> {
                val color = b.int
                val width = b.double
                val kind = b.get().toInt()
                val x = b.double
                val y = b.double
                val n = b.int
                if (n < 0 || n % 2 != 0 || b.remaining() < 4 * n) null else {
                    val pts = FloatArray(n)
                    b.asFloatBuffer().get(pts)
                    Stroke(id, x, y, pts, color, width, kind)
                }
            }
            IMAGE -> {
                val key = string(b)
                val pw = b.int
                val ph = b.int
                val item = ImageItem(id, key, pw, ph, b.double, b.double, b.double, b.double)
                // Le nom sert à retrouver un fichier du projet : jamais un chemin.
                if (pw <= 0 || ph <= 0 || key.isEmpty() || key.contains('/') || key.contains("..")) null else item
            }
            SHAPE -> {
                val kind = ShapeKind.values().getOrNull(b.get().toInt())
                val flips = b.get().toInt()
                val stroke = b.int
                val fillColor = b.int
                val fill = ShapeFill.values().getOrNull(b.get().toInt())
                val width = b.double
                val x = b.double
                val y = b.double
                val w = b.double
                val h = b.double
                if (kind == null || fill == null) null
                else ShapeItem(id, kind, x, y, w, h, flips and 1 != 0, flips and 2 != 0, stroke, fillColor, fill, width)
            }
            TEXT -> {
                val text = string(b)
                val x = b.double
                val y = b.double
                val w = b.double
                val h = b.double
                val size = b.double
                val color = b.int
                val style = b.get().toInt()
                val font = string(b)
                TextItem(id, text, x, y, w, h, size, color, style, font)
            }
            else -> null
        }
    } catch (e: RuntimeException) {
        // Octets tronqués (BufferUnderflowException) ou longueur absurde : l'élément est perdu, pas la couche.
        null
    }

    private fun string(b: ByteBuffer): String {
        val len = b.int
        if (len < 0 || len > b.remaining()) throw IllegalArgumentException("bad length $len")
        val bytes = ByteArray(len)
        b.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
