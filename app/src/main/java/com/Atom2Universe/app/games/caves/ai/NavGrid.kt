package com.Atom2Universe.app.games.caves.ai

import kotlin.math.floor

/**
 * Où un bot peut se tenir sur une carte voxel finie, et comment il passe d'une case à l'autre.
 *
 * Une **case** est une position de pieds (x, y, z) : un bloc plein dessous, deux blocs libres
 * (pieds et tête). Chaque case est reliée aux 8 cases voisines quand on peut y aller :
 * - à plat : coût 1, ou √2 en diagonale ;
 * - une marche d'un bloc vers le haut, s'il reste un 3e bloc libre au-dessus du départ (la tête
 *   passe pendant la montée) ;
 * - une descente de 1 à [MAX_DROP] blocs, à condition de pouvoir tomber tout droit.
 * Les diagonales restent à plat et ne coupent pas les coins : les deux cases de côté doivent être
 * libres, sinon le bot raserait l'arête d'un mur.
 *
 * On note aussi la **couverture** de chaque case : un bloc plein à côté, sur 2 blocs de haut
 * (couverture totale, debout) ou sur 1 seul (muret : couverture à moitié).
 *
 * Construite une fois au chargement de la carte, en lecture seule ensuite. Coordonnées locales.
 */
internal class NavGrid private constructor(
    val sizeX: Int, val sizeY: Int, val sizeZ: Int,
    private val nodeIndex: IntArray,
    val nodeX: IntArray, val nodeY: IntArray, val nodeZ: IntArray,
    /** Liaisons de la case n : indices [edgeStart] (n) inclus à [edgeStart] (n + 1) exclu. */
    val edgeStart: IntArray,
    val edgeTarget: IntArray,
    val edgeCost: FloatArray,
    private val coverMask: ByteArray,
) {
    val nodeCount: Int get() = nodeX.size

    /** Case dont les pieds sont dans le bloc (x, y, z), ou -1. */
    fun nodeAt(x: Int, y: Int, z: Int): Int =
        if (x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ) nodeIndex[x + sizeX * (z + sizeZ * y)] else -1

    /** Case sous des pieds posés en (x, y, z), en tolérant d'être un peu en l'air (saut, chute). */
    fun nodeUnder(x: Double, y: Double, z: Double): Int {
        val bx = floor(x).toInt(); val bz = floor(z).toInt()
        val by = floor(y + 0.01).toInt()
        for (dy in 0..MAX_DROP) {
            val n = nodeAt(bx, by - dy, bz)
            if (n >= 0) return n
        }
        return -1
    }

    /** Un mur d'au moins 2 blocs protège la case [node] du côté [dir] ([POS_X], [NEG_X], [POS_Z], [NEG_Z]). */
    fun hasFullCover(node: Int, dir: Int): Boolean = (coverMask[node].toInt() shr dir) and 1 == 1

    /** Un muret d'un seul bloc protège la case [node] du côté [dir] : à couvert accroupi seulement. */
    fun hasHalfCover(node: Int, dir: Int): Boolean = (coverMask[node].toInt() shr (4 + dir)) and 1 == 1

    companion object {
        const val MAX_DROP = 3

        const val POS_X = 0
        const val NEG_X = 1
        const val POS_Z = 2
        const val NEG_Z = 3

        private val DIR_X = intArrayOf(1, -1, 0, 0)
        private val DIR_Z = intArrayOf(0, 0, 1, -1)

        private const val DIAGONAL_COST = 1.4142135f
        // Monter ou descendre coûte un peu plus : à longueur égale, le bot préfère le plat.
        private const val STEP_UP_COST = 1.5f
        private const val DROP_COST_PER_BLOCK = 0.2f

        fun build(sizeX: Int, sizeY: Int, sizeZ: Int, world: SolidGrid): NavGrid {
            fun solid(x: Int, y: Int, z: Int) =
                x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ && world.isSolid(x, y, z)
            fun free(x: Int, y: Int, z: Int) = !solid(x, y, z)

            // 1. Les cases.
            val nodeIndex = IntArray(sizeX * sizeY * sizeZ) { -1 }
            val xs = ArrayList<Int>(); val ys = ArrayList<Int>(); val zs = ArrayList<Int>()
            for (y in 1 until sizeY) for (z in 0 until sizeZ) for (x in 0 until sizeX) {
                if (solid(x, y - 1, z) && free(x, y, z) && free(x, y + 1, z)) {
                    nodeIndex[x + sizeX * (z + sizeZ * y)] = xs.size
                    xs += x; ys += y; zs += z
                }
            }
            fun node(x: Int, y: Int, z: Int) =
                if (x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ) nodeIndex[x + sizeX * (z + sizeZ * y)] else -1

            // 2. Les liaisons et la couverture.
            val count = xs.size
            val edgeStart = IntArray(count + 1)
            val targets = ArrayList<Int>(count * 8)
            val costs = ArrayList<Float>(count * 8)
            val cover = ByteArray(count)
            for (n in 0 until count) {
                edgeStart[n] = targets.size
                val x = xs[n]; val y = ys[n]; val z = zs[n]
                var mask = 0

                for (d in 0..3) {
                    val nx = x + DIR_X[d]; val nz = z + DIR_Z[d]
                    val flat = node(nx, y, nz)
                    val up = node(nx, y + 1, nz)
                    when {
                        flat >= 0 -> { targets += flat; costs += 1f }
                        up >= 0 && free(x, y + 2, z) -> { targets += up; costs += STEP_UP_COST }
                        free(nx, y, nz) && free(nx, y + 1, nz) -> {
                            // Rien sous la case voisine : on tombe tout droit jusqu'au premier sol.
                            var drop = 1
                            while (drop <= MAX_DROP) {
                                val landing = node(nx, y - drop, nz)
                                if (landing >= 0) {
                                    targets += landing; costs += 1f + drop * DROP_COST_PER_BLOCK
                                    break
                                }
                                if (solid(nx, y - drop, nz)) break
                                drop++
                            }
                        }
                    }
                    if (solid(nx, y, nz)) mask = mask or (if (solid(nx, y + 1, nz)) 1 shl d else 1 shl (4 + d))
                }

                for (sx in intArrayOf(-1, 1)) for (sz in intArrayOf(-1, 1)) {
                    val diagonal = node(x + sx, y, z + sz)
                    if (diagonal >= 0 &&
                        free(x + sx, y, z) && free(x + sx, y + 1, z) &&
                        free(x, y, z + sz) && free(x, y + 1, z + sz)) {
                        targets += diagonal; costs += DIAGONAL_COST
                    }
                }
                cover[n] = mask.toByte()
            }
            edgeStart[count] = targets.size

            return NavGrid(
                sizeX, sizeY, sizeZ, nodeIndex,
                xs.toIntArray(), ys.toIntArray(), zs.toIntArray(),
                edgeStart, targets.toIntArray(), costs.toFloatArray(), cover,
            )
        }
    }
}
