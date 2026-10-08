package com.Atom2Universe.app.games.caves.world

import kotlin.math.floor
import kotlin.math.hypot

/** Lecture du flux déjà simulé : aucune recherche de chemin pendant la physique. */
internal object WaterCurrent {
    private val dx = intArrayOf(1, -1, 0, 0)
    private val dz = intArrayOf(0, 0, 1, -1)

    /** Remplit un tampon réutilisable : vitesse X/Y/Z en m/s, puis immersion (0 ou 1). */
    fun sample(world: World, x: Double, y: Double, z: Double, out: DoubleArray) {
        out.fill(0.0)
        if (!MeshBuilder.isPointInWater(world, x, y, z)) return
        out[3] = 1.0
        val wx = floor(x).toInt(); val wy = floor(y).toInt(); val wz = floor(z).toInt()
        val block = world.blockAt(wx, wy, wz)
        val level = world.waterFlowLevelKnown(block, wx, wy, wz)
        var fx = 0.0; var fz = 0.0
        for (i in 0..3) {
            val nx = wx + dx[i]; val nz = wz + dz[i]
            val neighbor = world.blockAt(nx, wy, nz)
            // Les cellules sèches n'attirent pas les corps : attendre le flux réel.
            if (!isWater(neighbor)) continue
            val slope = world.waterFlowLevelKnown(neighbor, nx, wy, nz) - level
            fx += dx[i] * slope.toDouble()
            fz += dz[i] * slope.toDouble()
        }
        val length = hypot(fx, fz)
        if (length > 0.0) {
            out[0] = fx / length * 1.25
            out[2] = fz / length * 1.25
        }
        if (block == WATER_FLOW && level == 0 && isWater(world.blockAt(wx, wy + 1, wz)))
            out[1] = -1.2
    }
}
