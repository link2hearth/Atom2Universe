package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.PI

/** Oversized, explorable props. Every solid is checked against the complete road envelope. */
internal object DestinationWorlds {
    fun boxes(room: RoomKind): List<RoomBox> = buildList {
        val theme = RoomThemes.theme(room)
        // Architecture sits beyond the race area; it shares geometry with collision.
        for (x in -96..96 step 32) {
            when (room) {
                RoomKind.MINE -> for (z in floatArrayOf(-73f,73f)) {
                    add(RoomBox(x.toFloat(),16f,z,12f,32f,4f,0x5F5550))
                    add(RoomBox(x.toFloat(),31f,z,28f,2f,5f,0x8E7357))
                }
                RoomKind.LABORATORY -> for (z in floatArrayOf(-74f,74f)) {
                    add(RoomBox(x.toFloat(),17f,z,1.2f,30f,1f,0x668997))
                    add(RoomBox(x.toFloat()+12f,23f,z,20f,12f,1f,0xB5ECED))
                    add(RoomBox(x.toFloat()+12f,29.5f,z,20f,.6f,1.2f,0x68DCCA))
                }
                RoomKind.RESTAURANT -> for (z in floatArrayOf(-74f,74f)) {
                    add(RoomBox(x.toFloat(),7f,z,30f,14f,1f,0x703D3F))
                    add(RoomBox(x.toFloat(),15f,z,31f,1f,1.4f,0xE8B477))
                    add(RoomBox(x.toFloat(),24f,z,20f,12f,1f,0x96624B))
                    add(RoomBox(x.toFloat(),24f,z,17f,9f,1.3f,0xECC686))
                }
                RoomKind.NEON_CITY -> for (z in floatArrayOf(-74f,74f)) {
                    add(RoomBox(x.toFloat(),16f,z,30f,32f,2f,theme.wall))
                    for (y in 6..28 step 7) for (dx in -10..10 step 5)
                        add(RoomBox(x+dx.toFloat(),y.toFloat(),z*.985f,2.4f,3.8f,.3f,
                            if ((x+dx+y)%3==0) 0x61C8DD else 0xE6AE78))
                }
                else -> Unit
            }
        }
    }

    fun decorations(scene: SceneChoice): List<DecorPlacement> {
        val candidates = buildList {
            fun prop(id: String, x: Float, z: Float, scale: Float = 1f, yaw: Float = 0f, y: Float = 0f) {
                add(DecorPlacement(DecorCatalog[id],x,y,z,scale=scale,yawDegrees=yaw))
            }
            when (scene.room) {
                RoomKind.MINE -> {
                    prop("mine.ore_cart",-56f,0f,1.1f,90f)
                    prop("mine.cable_drum",54f,2f,1.1f)
                    prop("mine.crystals",0f,-18f,1.3f)
                    prop("mine.crystals",50f,65f,1.2f)
                    prop("mine.crystals",-64f,65f,.85f)
                    prop("mine.ore_cart",-30f,-64f,.85f,90f)
                    for (x in floatArrayOf(-86f,0f,84f)) {
                        prop("mine.rock_wall",x,-69f,.7f)
                        prop("mine.lantern",x,63f,.9f)
                    }
                    prop("mine.crystals",109f,-8f,.8f)
                }
                RoomKind.LABORATORY -> {
                    prop("laboratory.microscope",-60f,0f,1.1f,90f)
                    prop("laboratory.coil",55f,0f,1.05f)
                    prop("laboratory.flask",-6f,21f,1.25f)
                    prop("laboratory.test_tubes",-35f,-63f,1.1f)
                    prop("laboratory.robot_arm",48f,-63f,.8f)
                    prop("laboratory.flask",-54f,63f,.8f)
                    prop("laboratory.test_tubes",28f,63f,1.05f,180f)
                    prop("laboratory.flask",108f,10f,.75f)
                }
                RoomKind.RESTAURANT -> {
                    prop("restaurant.burger",0f,0f,1.4f)
                    prop("restaurant.coffee",50f,10f,1.2f)
                    prop("restaurant.cutlery",-45f,0f,1.1f,90f)
                    prop("restaurant.booth",-45f,-65f,.9f)
                    prop("restaurant.counter",40f,-65f,.95f)
                    prop("restaurant.booth",-50f,64f,.85f,180f)
                    prop("restaurant.booth",40f,64f,.85f,180f)
                    prop("restaurant.coffee",109f,7f,.7f)
                }
                RoomKind.NEON_CITY -> {
                    for ((i,x) in floatArrayOf(-78f,-27f,27f,78f).withIndex()) {
                        // Skyline beyond the street facades, not a building squeezed into the road verge.
                        prop("neon.tower",x,-92f,if(i%2==0) 1f else .8f)
                        prop("neon.tower",x,92f,if(i%2==0) .8f else 1f,180f)
                    }
                    prop("neon.billboard",0f,-63f,.85f)
                    prop("neon.taxi",-50f,0f,1.2f,90f)
                    prop("neon.kiosk",50f,0f,1.1f)
                    for (x in floatArrayOf(-80f,0f,80f)) {
                        prop("neon.streetlamp",x,56f,.9f,180f)
                        prop("neon.streetlamp",x,-56f,.9f)
                    }
                    prop("neon.hydrant",108f,10f,1.1f)
                }
                else -> Unit
            }
        }
        if (!scene.circuit.usesSculptedLayout) {
            // Unsupported old saves must remain drivable even before scene normalization.
            // Older splines can fill the room, so do not place unverified solid props there.
            return emptyList()
        }
        val count = SculptedCircuits.sampleCount(scene.circuit)
        val road = (0 until count).map { i ->
            val f=i.toFloat()/count
            SculptedCircuits.point(scene.circuit,f) to (SculptedCircuits.width(scene.circuit,f)*.5f+1.8f)
        }
        val accepted = ArrayList<DecorPlacement>()
        val pillars = SculptedCircuits.pillars(scene.circuit)
        val architecture = boxes(scene.room)
        fun addClear(prop: DecorPlacement) {
            val solids = prop.solids
            val blocked = solids.any { box ->
                road.any { (p,r) ->
                    p.x+r >= box.left && p.x-r <= box.right && p.z+r >= box.back && p.z-r <= box.front &&
                        box.top > p.y-PrototypeTrack.ROAD_THICKNESS && box.bottom < p.y+2.6f
                } || pillars.any { overlap(box,it) } || architecture.any { overlap(box,it) } ||
                    accepted.any { other -> other.solids.any { overlap(box,it) } }
            }
            if (!blocked) accepted += prop
        }
        // Memorable passages straddle the road, with separate legs and a real open underside.
        val portalId = when(scene.room) {
            RoomKind.MINE -> "mine.timber_gate"
            RoomKind.LABORATORY -> "laboratory.workbench"
            RoomKind.RESTAURANT -> "restaurant.table"
            else -> null
        }
        if (portalId != null && scene.circuit.isDestinationCircuit) {
            val anchors = when(scene.room) {
                RoomKind.MINE -> listOf(-50f to -42f, -72f to 42f)
                RoomKind.LABORATORY -> listOf(0f to -42f, 60f to 42f)
                else -> listOf(-40f to 42f, 0f to -42f)
            }
            for ((x,z) in anchors) {
                val fraction=SculptedCircuits.layout(scene.circuit).loop.fractionNear(x,z)
                val p=SculptedCircuits.point(scene.circuit,fraction)
                val next=SculptedCircuits.point(scene.circuit,fraction+.002f)
                val yaw=atan2(next.x-p.x,next.z-p.z)*180f/PI.toFloat()
                addClear(DecorPlacement(DecorCatalog[portalId],p.x,0f,p.z,yawDegrees=yaw))
            }
        }
        candidates.forEach { prop ->
            // Large objects live inside the room. This also prevents a window/trim from
            // cutting through them; the safety check still rejects an obstructed placement.
            val nearWall = abs(prop.z) >= 60f && prop.model.id != "neon.tower"
            val halfDepth=prop.model.bounds.depth*prop.scale*.5f
            val inset=if(nearWall) (71f-halfDepth).coerceAtMost(abs(prop.z)) else abs(prop.z)
            addClear(if(nearWall) prop.copy(z=if(prop.z<0f) -inset else inset) else prop)
        }
        return accepted
    }

    private fun overlap(a: RoomBox,b: RoomBox) = a.left < b.right && a.right > b.left &&
        a.back < b.front && a.front > b.back && a.bottom < b.top && a.top > b.bottom
}
