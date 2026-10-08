package com.Atom2Universe.app.games.caves.render

import kotlin.math.*

/** Dôme continu à base aplatie : sommets locaux XYZ + éclairage, construits une seule fois. */
internal object SlimeGeometry {
    val vertices: FloatArray by lazy {
        val out=ArrayList<Float>()
        fun vertex(ring:Int, sector:Int) {
            val latitude=ring*PI/16
            val angle=sector*PI/8
            val radius=8.5*cos(latitude)
            out.add((radius*sin(angle)).toFloat())
            out.add((13*sin(latitude)).toFloat())
            out.add((radius*cos(angle)).toFloat())
            out.add((.68+.24*sin(latitude)+.08*cos(angle)*cos(latitude)).toFloat())
        }
        for(ring in 0 until 8) for(sector in 0 until 16) {
            vertex(ring,sector); vertex(ring,sector+1); vertex(ring+1,sector+1)
            vertex(ring,sector); vertex(ring+1,sector+1); vertex(ring+1,sector)
        }
        for(sector in 0 until 16) {
            out.addAll(listOf(0f,0f,0f,.5f))
            vertex(0,sector+1); vertex(0,sector)
        }
        out.toFloatArray()
    }
}
