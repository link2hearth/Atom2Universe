package com.Atom2Universe.app.games.toyboxracers.render

import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import com.Atom2Universe.app.games.toyboxracers.models.DecorShape
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3

/** Regroupe plusieurs objets en un mesh statique : aucun draw call par accessoire. */
internal object DecorMeshFactory {
    fun build(placements: List<DecorPlacement>): ColoredMesh {
        val builder = MeshBuilder()
        placements.forEach { add(builder, it) }
        return builder.build()
    }

    fun add(builder: MeshBuilder, placement: DecorPlacement) {
        builder.placed(placement) {
            for (p in placement.model.parts) {
                val c = floatArrayOf(((p.color shr 16) and 255) / 255f,
                    ((p.color shr 8) and 255) / 255f, (p.color and 255) / 255f, 1f)
                when (p.shape) {
                    DecorShape.BOX -> builder.box(p.x, p.y, p.z, p.width, p.height, p.depth, c)
                    DecorShape.OVAL -> builder.lowPolyEllipsoid(p.x, p.y, p.z,
                        p.width / 2, p.height / 2, p.depth / 2, 7, 12, c)
                    DecorShape.CYLINDER_Y -> builder.cylinderY(p.x, p.y, p.z, p.height, p.width / 2, 12, c)
                    DecorShape.CYLINDER_X -> builder.cylinderX(p.x, p.y, p.z, p.width, p.height / 2, 12, c)
                    DecorShape.CONE_Y -> builder.coneY(p.x, p.y - p.height / 2, p.z, p.height, p.width / 2, 12, c)
                    DecorShape.GABLE_ROOF -> {
                        val y = p.y - p.height / 2
                        val a = Vec3(p.x - p.width / 2, y, p.z - p.depth / 2)
                        val b = Vec3(p.x + p.width / 2, y, p.z - p.depth / 2)
                        val d = Vec3(p.x - p.width / 2, y, p.z + p.depth / 2)
                        val e = Vec3(p.x + p.width / 2, y, p.z + p.depth / 2)
                        val topBack = Vec3(p.x, y + p.height, p.z - p.depth / 2)
                        val topFront = Vec3(p.x, y + p.height, p.z + p.depth / 2)
                        builder.triangle(a, topBack, b, c)
                        builder.triangle(d, e, topFront, c)
                        builder.quad(a, d, topFront, topBack, c)
                        builder.quad(b, topBack, topFront, e, c)
                        builder.quad(a, b, e, d, c)
                    }
                }
            }
        }
    }
}
