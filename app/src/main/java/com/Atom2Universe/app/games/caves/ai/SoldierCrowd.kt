package com.Atom2Universe.app.games.caves.ai

import kotlin.math.floor

/** Voisinage 3D des corps : les collisions consultent les cellules proches, pas toute l'armée. */
internal class SoldierCrowd(sizeX: Int, sizeY: Int, sizeZ: Int) {
    private val width = (sizeX + 3) / 4
    private val height = (sizeY + 3) / 4
    private val depth = (sizeZ + 3) / 4
    private class Body(var x: Double, var y: Double, var z: Double, var cell: Int)
    private val cells = arrayOfNulls<MutableSet<Int>>(width * height * depth)
    private val bodies = HashMap<Int, Body>()

    fun clear() { cells.fill(null); bodies.clear() }

    fun remove(id: Int) {
        val body = bodies.remove(id) ?: return
        val bucket = cells[body.cell] ?: return
        bucket.remove(id)
        if (bucket.isEmpty()) cells[body.cell] = null
    }

    fun move(id: Int, x: Double, y: Double, z: Double) {
        val cell = index(cell(x), cell(y), cell(z))
        if (cell < 0) { remove(id); return }
        val old = bodies[id]
        if (old?.cell == cell) { old.x = x; old.y = y; old.z = z; return }
        remove(id)
        bodies[id] = Body(x, y, z, cell)
        (cells[cell] ?: HashSet<Int>().also { cells[cell] = it }).add(id)
    }

    fun overlaps(x: Double, y: Double, z: Double, except: Int = -1,
                 height: Double = SoldierCollision.HEIGHT): Boolean {
        for (cx in cell(x - .6)..cell(x + .6))
            for (cy in cell(y - SoldierCollision.HEIGHT)..cell(y + height))
                for (cz in cell(z - .6)..cell(z + .6)) {
                    val key = index(cx, cy, cz)
                    if (key < 0) continue
                    val bucket = cells[key] ?: continue
                    for (id in bucket) {
                        if (id == except) continue
                        val b = bodies[id] ?: continue
                        if (SoldierCollision.overlaps(b.x, b.y, b.z, x, y, z, height)) return true
                    }
                }
        return false
    }

    private fun cell(value: Double) = floor(value / 4.0).toInt()
    private fun index(x: Int, y: Int, z: Int): Int =
        if (x in 0 until width && y in 0 until height && z in 0 until depth)
            x + width * (z + depth * y) else -1
}
