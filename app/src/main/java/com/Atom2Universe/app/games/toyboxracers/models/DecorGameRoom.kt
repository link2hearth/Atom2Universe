package com.Atom2Universe.app.games.toyboxracers.models

import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.BLUE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.CREAM
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.DARK
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.GOLD
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.METAL
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.ROSE
import com.Atom2Universe.app.games.toyboxracers.models.DecorPalette.WOOD
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.lathe
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.mesh
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.quad
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.rod
import com.Atom2Universe.app.games.toyboxracers.models.DecorSculpt.v
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Empty tables and a separate, single-colour ball for arranging a scene in the editor. */
internal object DecorGameRoom {
    private const val FELT = 0x649F8C
    private const val CUSHION = 0x467E70
    private fun model(id: String, build: DecorBuilder.() -> Unit) =
        DecorModel("kawaii.$id", DecorRoom.LIVING_ROOM, DecorBuilder().apply(build).parts.toList())

    /** Convex footprint, counter-clockwise seen from above; top may slope along Z. */
    private fun DecorBuilder.slab(points: List<Pair<Float, Float>>, bottom: (Float) -> Float,
                                  top: (Float) -> Float, color: Int) = mesh(color) {
        val upper = points.map { (x,z) -> v(x,top(z),z) }
        val lower = points.map { (x,z) -> v(x,bottom(z),z) }
        for(i in 1 until points.lastIndex) {
            addAll(listOf(upper[0],upper[i],upper[i+1],lower[0],lower[i+1],lower[i]))
        }
        for(i in points.indices) {
            val j=(i+1)%points.size
            quad(upper[i],lower[i],lower[j],upper[j])
        }
    }
    private fun rectangle(left: Float, right: Float, back: Float, front: Float) =
        listOf(left to back,left to front,right to front,right to back)

    private fun DecorBuilder.table(pockets: Boolean) {
        for(x in floatArrayOf(-3.5f,3.5f)) for(z in floatArrayOf(-6.4f,6.4f)) {
            box(x,2.7f,z,1.1f,5.4f,1.1f,WOOD)
            box(x,.2f,z,1.25f,.4f,1.25f,DARK)
            box(x,4.6f,z,1.35f,.45f,1.35f,GOLD)
        }
        for(z in floatArrayOf(-6.4f,6.4f)) box(0f,2.2f,z,7f,.55f,.65f,WOOD)
        box(0f,2.2f,0f,.65f,.55f,12.8f,WOOD)

        if(!pockets) {
            box(0f,6f,0f,10f,1.8f,18f,WOOD)
            box(0f,6.96f,0f,8f,.12f,16f,FELT)
            for(s in floatArrayOf(-1f,1f)) {
                box(s*4.6f,7.1f,0f,1.2f,.4f,18f,WOOD)
                box(s*4.03f,7.14f,0f,.46f,.36f,16.2f,CUSHION)
                box(0f,7.1f,s*8.6f,8f,.4f,1.2f,WOOD)
                box(0f,7.14f,s*8.03f,8f,.36f,.46f,CUSHION)
            }
        } else {
            // The bed is cut around six circular mouths, rather than covered with black discs.
            box(0f,6.45f,0f,6.7f,.9f,16f,WOOD)
            box(0f,6.96f,0f,6.7f,.12f,16f,FELT)
            val breaks = (listOf(-8f,-7.35f,-.65f,.65f,7.35f,8f) +
                (0..8).flatMap { i -> listOf(-8f+i*.65f/8,-.65f+i*1.3f/8,7.35f+i*.65f/8) }).distinct().sorted()
            fun edge(z: Float): Float {
                val distance = minOf(abs(z+8f),abs(z),abs(z-8f))
                return if(distance < .65f) 4f-sqrt((.65f*.65f-distance*distance).coerceAtLeast(0f)) else 4f
            }
            for(s in floatArrayOf(-1f,1f)) {
                for(i in 0 until breaks.lastIndex) {
                    val z0=breaks[i]; val z1=breaks[i+1]
                    val outline=listOf(3.35f to z0,3.35f to z1,edge(z1) to z1,edge(z0) to z0)
                        .map { (x,z) -> s*x to z }.let { if(s<0) it.reversed() else it }
                    slab(outline,{6f},{6.9f},WOOD)
                    slab(outline,{6.9f},{7.02f},FELT)
                }
                for(z in floatArrayOf(-4f,4f)) {
                    box(s*4.65f,6.1f,z,1.1f,1.8f,6.3f,WOOD)
                    box(s*4.62f,7.1f,z,1.16f,.4f,6.3f,WOOD)
                    box(s*4.02f,7.14f,z,.45f,.36f,6.3f,CUSHION)
                }
                box(0f,6.1f,s*8.6f,6.5f,1.8f,1.1f,WOOD)
                box(0f,7.1f,s*8.6f,6.5f,.4f,1.15f,WOOD)
                box(0f,7.14f,s*8.03f,6.5f,.36f,.45f,CUSHION)
                for(z in floatArrayOf(-8f,0f,8f)) {
                    // Open rim and deep cup: dark interior remains visibly below the cloth.
                    lathe(s*4f,z,listOf(0f to 5.5f,.48f to 5.5f,.79f to 6.9f,
                        .79f to 7.29f,.65f to 7.29f,.65f to 6.9f,.39f to 5.7f,0f to 5.7f),DARK,segments=20)
                }
            }
        }
        for(s in floatArrayOf(-1f,1f)) {
            for(z in floatArrayOf(-6f,-4f,-2f,2f,4f,6f))
                cylinder(s*4.65f,7.335f,z,.08f,.025f,CREAM,false)
            for(x in floatArrayOf(-2f,0f,2f)) cylinder(x,7.335f,s*8.6f,.08f,.025f,CREAM,false)
        }
    }

    val all by lazy { listOf(
        model("pool_table") { table(true) },
        model("carom_table") { table(false) },
        model("billiard_ball") {
            // No baked highlight or number: one palette entry recolours the entire ball.
            lathe(0f,0f,(0..16).map { i ->
                val angle=i*PI.toFloat()/16
                (.48f*sin(angle).coerceAtLeast(0f)) to (.48f-.48f*cos(angle))
            },CREAM,segments=24)
        },
        model("pinball_cabinet") {
            fun deck(z: Float) = 6.8f-z*.14f
            for(s in floatArrayOf(-1f,1f)) for(z in floatArrayOf(-4.8f,4.8f)) {
                rod(v(s*3.15f,.18f,z+.25f),v(s*2.8f,deck(z)-.9f,z),.16f,METAL)
                cylinder(s*3.15f,.12f,z+.25f,.35f,.24f,DARK)
            }
            // Split solid strips keep the collision surface close to the visible incline.
            for(i in 0 until 32) {
                val back=-6f+i*12f/32; val front=-6f+(i+1)*12f/32
                slab(rectangle(-3.3f,3.3f,back,front),{4.8f},{deck(it)},BLUE)
                slab(rectangle(-2.93f,2.93f,back,front),{deck(it)},{deck(it)+.09f},0xB5D9DF)
            }
            for(s in floatArrayOf(-1f,1f)) {
                slab(rectangle(s*3.1f-.18f,s*3.1f+.18f,-6f,6f),{deck(it)},{deck(it)+.24f},METAL)
                // Flipper button on the cabinet side; purely decorative.
                rod(v(s*3.3f,5.35f,4.45f),v(s*3.47f,5.35f,4.45f),.19f,ROSE,false)
            }
            slab(rectangle(-3.3f,3.3f,5.65f,6.12f),{deck(it)},{deck(it)+.26f},CREAM)
            box(0f,5.12f,6.08f,6.6f,.64f,.16f,BLUE)
            box(-.75f,5.2f,6.19f,.85f,.48f,.1f,DARK,false)
            rod(v(2.3f,5.25f,6.12f),v(2.3f,5.25f,6.65f),.1f,METAL,false)
            oval(2.3f,5.25f,6.7f,.4f,.4f,.2f,ROSE,false)
            box(0f,7.25f,-6.3f,5.8f,1.4f,.9f,BLUE)
            box(0f,9.65f,-6.45f,7.5f,4.2f,1.35f,BLUE)
            box(0f,9.7f,-5.74f,6.8f,3.5f,.12f,CREAM,false)
            box(0f,10.1f,-5.64f,6.15f,2.1f,.08f,ROSE,false)
            box(0f,8.75f,-5.64f,4.8f,.48f,.08f,DARK,false)
            for(x in floatArrayOf(-2.8f,2.8f)) for(i in 0..2)
                box(x,8.57f+i*.16f,-5.63f,.45f,.05f,.06f,METAL,false)
        }
    ) }
    val names = mapOf(
        "kawaii.pool_table" to R.string.toybox_prop_pool_table,
        "kawaii.carom_table" to R.string.toybox_prop_carom_table,
        "kawaii.billiard_ball" to R.string.toybox_prop_billiard_ball,
        "kawaii.pinball_cabinet" to R.string.toybox_prop_pinball_cabinet
    )
}
