package com.Atom2Universe.app.games.caves.world

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Un bloc d'une carte, en coordonnées locales (0 ≤ x < sizeX, etc.). */
internal data class MapPoint(val x: Int, val y: Int, val z: Int)

/**
 * Une carte du mode Assaut : un volume de blocs construit à l'avance, et les points
 * d'apparition des deux camps (voir CAVE_WORLD_ASSAUT.md).
 *
 * Fichier `.a2map` : un en-tête, les points d'apparition, puis les blocs couche par couche
 * (y, puis z, puis x) rangés en « suites ». Une suite = un bloc + son orientation + le nombre de
 * fois qu'il se répète d'affilée. Une arène est faite de grands aplats (air, sol, murs), donc de
 * très peu de suites ; le tout passe ensuite dans GZIP.
 */
internal class A2Map(
    val name: String,
    val sizeX: Int, val sizeY: Int, val sizeZ: Int,
    val blocks: ShortArray,
    val meta: ByteArray,
    val spawnsA: List<MapPoint>,
    val spawnsB: List<MapPoint>,
) {
    val volume: Int

    init {
        require(sizeX > 0 && sizeY > 0 && sizeZ > 0) { "Taille de carte invalide" }
        require(sizeX.toLong() * sizeY * sizeZ <= MAX_VOLUME) { "Carte trop grande" }
        volume = sizeX * sizeY * sizeZ
        require(blocks.size == volume && meta.size == volume) { "Tableaux de taille incohérente" }
    }

    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ

    /** Même ordre que le fichier : couche (y), puis rangée (z), puis colonne (x). */
    fun index(x: Int, y: Int, z: Int): Int = x + sizeX * (z + sizeZ * y)

    fun blockAt(x: Int, y: Int, z: Int): Short = if (contains(x, y, z)) blocks[index(x, y, z)] else AIR

    fun metaAt(x: Int, y: Int, z: Int): Byte = if (contains(x, y, z)) meta[index(x, y, z)] else 0

    /** Écrit la carte dans [out], sans le fermer (il appartient à l'appelant). */
    fun write(out: OutputStream) {
        val gzip = GZIPOutputStream(out)
        val data = DataOutputStream(gzip)
        data.writeInt(MAGIC)
        data.writeInt(VERSION)
        data.writeUTF(name)
        data.writeInt(sizeX); data.writeInt(sizeY); data.writeInt(sizeZ)
        writePoints(data, spawnsA)
        writePoints(data, spawnsB)

        var i = 0
        while (i < volume) {
            val block = blocks[i]
            val m = meta[i]
            var run = 1
            while (i + run < volume && blocks[i + run] == block && meta[i + run] == m) run++
            data.writeShort(block.toInt())
            data.writeByte(m.toInt())
            writeVarInt(data, run)
            i += run
        }
        data.flush()
        gzip.finish()
    }

    companion object {
        const val EXTENSION = "a2map"

        /** 4 millions de blocs, soit par exemple 256 × 60 × 256 : bien plus qu'une arène. */
        const val MAX_VOLUME = 4_000_000

        private const val MAGIC = 0x41324D50   // "A2MP"
        private const val VERSION = 1
        private const val MAX_SPAWNS = 64

        fun read(input: InputStream): A2Map {
            val data = DataInputStream(GZIPInputStream(input))
            if (data.readInt() != MAGIC) throw IOException("Pas une carte Atom2Universe")
            val version = data.readInt()
            if (version != VERSION) throw IOException("Version de carte inconnue : $version")
            val name = data.readUTF()
            val sx = data.readInt(); val sy = data.readInt(); val sz = data.readInt()
            if (sx <= 0 || sy <= 0 || sz <= 0 || sx.toLong() * sy * sz > MAX_VOLUME)
                throw IOException("Taille de carte invalide : $sx × $sy × $sz")
            val spawnsA = readPoints(data, sx, sy, sz)
            val spawnsB = readPoints(data, sx, sy, sz)

            val volume = sx * sy * sz
            val blocks = ShortArray(volume)
            val meta = ByteArray(volume)
            var i = 0
            while (i < volume) {
                val block = data.readShort()
                val m = data.readByte()
                val run = readVarInt(data)
                if (run <= 0 || run > volume - i) throw IOException("Carte corrompue")
                blocks.fill(block, i, i + run)
                meta.fill(m, i, i + run)
                i += run
            }
            return A2Map(name, sx, sy, sz, blocks, meta, spawnsA, spawnsB)
        }

        /**
         * Fabrique une carte à partir d'une zone du monde. [blockAt] et [metaAt] reçoivent des
         * coordonnées locales. Les balises [SPAWN_MARKER_A] / [SPAWN_MARKER_B] ne sont pas gardées
         * comme blocs : elles deviennent des points d'apparition, et il reste de l'air à leur place.
         */
        fun capture(
            name: String,
            sizeX: Int, sizeY: Int, sizeZ: Int,
            blockAt: (x: Int, y: Int, z: Int) -> Short,
            metaAt: (x: Int, y: Int, z: Int) -> Byte,
        ): A2Map {
            require(sizeX > 0 && sizeY > 0 && sizeZ > 0) { "Taille de carte invalide" }
            require(sizeX.toLong() * sizeY * sizeZ <= MAX_VOLUME) { "Carte trop grande" }
            val volume = sizeX * sizeY * sizeZ
            val blocks = ShortArray(volume)
            val meta = ByteArray(volume)
            val spawnsA = ArrayList<MapPoint>()
            val spawnsB = ArrayList<MapPoint>()
            for (y in 0 until sizeY) for (z in 0 until sizeZ) for (x in 0 until sizeX) {
                when (val block = blockAt(x, y, z)) {
                    SPAWN_MARKER_A -> spawnsA += MapPoint(x, y, z)
                    SPAWN_MARKER_B -> spawnsB += MapPoint(x, y, z)
                    else -> {
                        val i = x + sizeX * (z + sizeZ * y)
                        blocks[i] = block
                        meta[i] = metaAt(x, y, z)
                    }
                }
            }
            return A2Map(name, sizeX, sizeY, sizeZ, blocks, meta,
                spawnsA.take(MAX_SPAWNS), spawnsB.take(MAX_SPAWNS))
        }

        private fun writePoints(out: DataOutputStream, points: List<MapPoint>) {
            out.writeInt(points.size)
            for (p in points) { out.writeInt(p.x); out.writeInt(p.y); out.writeInt(p.z) }
        }

        private fun readPoints(input: DataInputStream, sx: Int, sy: Int, sz: Int): List<MapPoint> {
            val count = input.readInt()
            if (count !in 0..MAX_SPAWNS) throw IOException("Carte corrompue")
            return List(count) {
                val p = MapPoint(input.readInt(), input.readInt(), input.readInt())
                if (p.x !in 0 until sx || p.y !in 0 until sy || p.z !in 0 until sz)
                    throw IOException("Point d'apparition hors de la carte")
                p
            }
        }

        /** Entier sur 1 à 5 octets : 7 bits utiles par octet, le 8e dit « il en reste ». */
        private fun writeVarInt(out: DataOutputStream, value: Int) {
            var v = value
            while (v and 0x7F.inv() != 0) {
                out.writeByte((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            out.writeByte(v)
        }

        private fun readVarInt(input: DataInputStream): Int {
            var result = 0
            var shift = 0
            while (true) {
                val byte = input.readUnsignedByte()
                result = result or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
                if (shift > 28) throw IOException("Carte corrompue")
            }
        }
    }
}
