package com.Atom2Universe.app.games.caves.world

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Maillage d'un chunk solide, tassé pour la carte graphique : 24 octets par sommet au lieu de 48,
 * et 4 sommets par face (reliés par des indices) au lieu de 6. Soit environ 2,7 fois moins de
 * mémoire graphique par chunk — c'est ce qui bornait la distance de simulation.
 *
 * Disposition d'un sommet ([STRIDE] octets, tout aligné sur son propre type) :
 * - 0  : position x, y, z en entiers signés ×[POS_SCALE] (précision 1/256 de bloc), + 2 octets vides ;
 * - 8  : u, v en entiers ×[UV_SCALE], puis face × 4096 + couche de texture, sans signe ;
 * - 14 : lumière du ciel, 15 : lumière des torches, un octet chacune (0..255 = 0..1) ;
 * - 16 : teinte de climat r, g, b et son masque, en demi-flottants.
 *
 * Les indices sont sur 2 octets tant que le chunk a moins de 65 536 sommets, sur 4 au-delà.
 */
internal class PackedMesh(
    val vertices: ByteArray,
    val vertexCount: Int,
    val indices: ByteArray,
    val indexCount: Int,
    val wideIndices: Boolean,
) {
    val byteSize: Int get() = vertices.size + indices.size

    companion object {
        const val STRIDE = 24
        const val POS_SCALE = 256f
        const val UV_SCALE = 1024f
        val EMPTY = PackedMesh(ByteArray(0), 0, ByteArray(0), 0, false)

        /**
         * Tasse des sommets « larges » (12 flottants, 6 par face : v0 v1 v2 v0 v2 v3). Une face qui
         * suit ce motif garde 4 sommets ; une qui ne le suit pas garde ses 6, rien n'est perdu.
         */
        fun pack(wide: FloatArray, floatsPerVertex: Int): PackedMesh {
            val vertexTotal = wide.size / floatsPerVertex
            if (vertexTotal == 0) return EMPTY
            val faces = vertexTotal / 6
            fun same(a: Int, b: Int): Boolean {
                val ia = a * floatsPerVertex; val ib = b * floatsPerVertex
                for (k in 0 until floatsPerVertex) if (wide[ia + k] != wide[ib + k]) return false
                return true
            }
            // Premier passage : compter, pour choisir la taille des indices et des tampons.
            var unique = 0
            for (f in 0 until faces) {
                val b = f * 6
                unique += if (same(b, b + 3) && same(b + 2, b + 4)) 4 else 6
            }
            val extra = vertexTotal - faces * 6
            unique += extra
            val indexCount = faces * 6 + extra
            val wideIndices = unique > 65535
            val vbb = ByteBuffer.allocate(unique * STRIDE).order(ByteOrder.nativeOrder())
            val ibb = ByteBuffer.allocate(indexCount * if (wideIndices) 4 else 2).order(ByteOrder.nativeOrder())
            var next = 0
            fun index(i: Int) { if (wideIndices) ibb.putInt(i) else ibb.putShort(i.toShort()) }
            fun emit(v: Int): Int {
                val s = v * floatsPerVertex
                vbb.putShort(Math.round(wide[s] * POS_SCALE).toShort())
                vbb.putShort(Math.round(wide[s + 1] * POS_SCALE).toShort())
                vbb.putShort(Math.round(wide[s + 2] * POS_SCALE).toShort())
                vbb.putShort(0)
                vbb.putShort(Math.round(wide[s + 3] * UV_SCALE).coerceIn(0, 65535).toShort())
                vbb.putShort(Math.round(wide[s + 4] * UV_SCALE).coerceIn(0, 65535).toShort())
                vbb.putShort(Math.round(wide[s + 5]).coerceIn(0, 65535).toShort())
                vbb.put(Math.round(wide[s + 6].coerceIn(0f, 1f) * 255f).toByte())
                vbb.put(Math.round(wide[s + 11].coerceIn(0f, 1f) * 255f).toByte())
                for (k in 7..10) vbb.putShort(toHalf(wide[s + k]))
                return next++
            }
            for (f in 0 until faces) {
                val b = f * 6
                if (same(b, b + 3) && same(b + 2, b + 4)) {
                    val i0 = emit(b); val i1 = emit(b + 1); val i2 = emit(b + 2); val i3 = emit(b + 5)
                    index(i0); index(i1); index(i2); index(i0); index(i2); index(i3)
                } else {
                    for (k in 0 until 6) index(emit(b + k))
                }
            }
            for (v in faces * 6 until vertexTotal) index(emit(v))
            return PackedMesh(vbb.array(), unique, ibb.array(), indexCount, wideIndices)
        }

        /** Flottant 32 bits → demi-flottant IEEE (arrondi au plus proche), sans dépendre d'Android. */
        fun toHalf(value: Float): Short {
            val bits = java.lang.Float.floatToIntBits(value)
            val sign = (bits ushr 16) and 0x8000
            var exp = ((bits ushr 23) and 0xFF) - 127 + 15
            var mant = bits and 0x7FFFFF
            if (exp <= 0) {
                if (exp < -10) return sign.toShort()
                mant = (mant or 0x800000) shr (1 - exp)
                return (sign or ((mant + 0x1000) shr 13)).toShort()
            }
            if (exp >= 31) return (sign or 0x7C00).toShort()
            val rounded = mant + 0x1000
            if (rounded and 0x800000 != 0) { mant = 0; exp++ } else mant = rounded
            if (exp >= 31) return (sign or 0x7C00).toShort()
            return (sign or (exp shl 10) or (mant shr 13)).toShort()
        }
    }
}
