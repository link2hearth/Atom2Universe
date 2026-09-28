package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.render.MobModels
import kotlin.math.abs

/** WAILA selects the nearest living body before the first blocking surface. */
internal object EnemyFocus {
    data class Mob(val id: Int,val model: String,val level: Int,val hp: Int,val maxHp: Int,val isBoss: Boolean)

    fun mob(enemies: List<Enemy>,sx: Double,sy: Double,sz: Double,dx: Double,dy: Double,dz: Double,
             limit: Double): Mob? {
        var nearest=limit
        var selected: Enemy?=null
        for(e in enemies) {
            if(e.hp<=0 || e.exhibitPose!=null) continue
            val radius=e.collisionRadius
            val height=MobModels.bodyHeightWorld(e.def.model,e.baseScale)-e.crouchDrop
            val origin=doubleArrayOf(sx,sy,sz);val direction=doubleArrayOf(dx,dy,dz)
            val low=doubleArrayOf(e.x-radius,e.y,e.z-radius)
            val high=doubleArrayOf(e.x+radius,e.y+height,e.z+radius)
            var enter=0.0;var leave=nearest
            for(axis in 0..2) {
                if(abs(direction[axis])<1e-8) {
                    if(origin[axis]<low[axis] || origin[axis]>high[axis]) leave=-1.0
                } else {
                    val a=(low[axis]-origin[axis])/direction[axis]
                    val b=(high[axis]-origin[axis])/direction[axis]
                    enter=maxOf(enter,minOf(a,b));leave=minOf(leave,maxOf(a,b))
                }
            }
            if(enter<=leave && enter<nearest) { nearest=enter;selected=e }
        }
        return selected?.let { Mob(it.id,it.def.model,it.level,it.hp,it.maxHp,it.isBoss) }
    }
}
