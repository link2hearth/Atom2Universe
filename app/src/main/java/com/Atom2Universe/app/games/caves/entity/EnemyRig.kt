package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.render.MobModels
import kotlin.math.*

/** Shared model sockets keep the released projectile at the visible weapon/mouth. */
internal object EnemyRig {
    const val BOW_X = 6f
    const val BOW_Y = 22.4f
    const val BOW_Z = 11f
    data class Point(val x: Double,val y: Double,val z: Double)
    fun muzzle(e: Enemy): Point {
        val scale=e.baseScale.toDouble()
        val voxel=scale*2/MobModels.REF_VOX
        val yaw=Math.toRadians(e.windupYaw.toDouble())
        val side=if(e.def.model=="skeleton") BOW_X*voxel-.035*scale else 0.0
        val height=when(e.def.model) {
            "skeleton" -> BOW_Y*voxel
            "spider" -> MobModels.bodyHeightWorld(e.def.model,e.baseScale)*.5
            else -> MobModels.bodyHeightWorld(e.def.model,e.baseScale)*.72
        }
        val forward=if(e.def.model=="skeleton") BOW_Z*voxel else e.collisionRadius+.12
        return Point(e.x+side*cos(yaw)+forward*sin(yaw),e.y+height,e.z-side*sin(yaw)+forward*cos(yaw))
    }

    fun touching(e: Enemy, target: EnemyTarget): Boolean {
        if(e.hp<=0 || e.freezeTimer>0f || e.staggerTimer>0f || e.retreat>0f || e.confusionTimer>0f || !target.node.isAlive) return false
        val feet=target.y-1.62
        val height=MobModels.bodyHeightWorld(e.def.model,e.baseScale)
        return feet<e.y+height && target.y+.1-target.eyeDrop>e.y &&
            hypot(target.x-e.x,target.z-e.z)<e.collisionRadius+.3
    }
}
