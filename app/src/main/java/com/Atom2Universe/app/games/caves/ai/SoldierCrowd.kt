package com.Atom2Universe.app.games.caves.ai

import kotlin.math.floor

/** Voisinage 3D des corps : les collisions consultent les cellules proches, pas toute l'armée. */
internal class SoldierCrowd(sizeX: Int, sizeY: Int, sizeZ: Int) {
    private val width = (sizeX + 3) / 4
    private val height = (sizeY + 3) / 4
    private val depth = (sizeZ + 3) / 4
    private class Body(val id: Int, var x: Double, var y: Double, var z: Double, var cell: Int)
    // Cellules et corps rangés sans HashMap<Int, …> : au-delà de 127, chaque numéro de soldat
    // passé en clé était un Integer alloué (dès la troisième manche).
    private val cells = arrayOfNulls<ArrayList<Body>>(width * height * depth)
    /** Corps par numéro de soldat (les numéros commencent à 1 et ne font que croître). */
    private var bodies = arrayOfNulls<Body>(64)

    fun clear() { cells.fill(null); bodies.fill(null) }

    private fun bodyOf(id: Int): Body? = if (id >= 0 && id < bodies.size) bodies[id] else null

    fun remove(id: Int) {
        val body = bodyOf(id) ?: return
        bodies[id] = null
        val bucket = cells[body.cell] ?: return
        bucket.remove(body)
        if (bucket.isEmpty()) cells[body.cell] = null
    }

    fun move(id: Int, x: Double, y: Double, z: Double) {
        val cell = index(cell(x), cell(y), cell(z))
        if (cell < 0) { remove(id); return }
        val old = bodyOf(id)
        if (old?.cell == cell) { old.x = x; old.y = y; old.z = z; return }
        remove(id)
        if (id < 0) return
        if (id >= bodies.size) bodies = bodies.copyOf(maxOf(id + 1, bodies.size * 2))
        val body = Body(id, x, y, z, cell)
        bodies[id] = body
        (cells[cell] ?: ArrayList<Body>(4).also { cells[cell] = it }).add(body)
    }

    fun overlaps(x: Double, y: Double, z: Double, except: Int = -1,
                 height: Double = SoldierCollision.HEIGHT): Boolean {
        for (cx in cell(x - .6)..cell(x + .6))
            for (cy in cell(y - SoldierCollision.HEIGHT)..cell(y + height))
                for (cz in cell(z - .6)..cell(z + .6)) {
                    val key = index(cx, cy, cz)
                    if (key < 0) continue
                    val bucket = cells[key] ?: continue
                    for (i in bucket.indices) {
                        val b = bucket[i]
                        if (b.id == except) continue
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
