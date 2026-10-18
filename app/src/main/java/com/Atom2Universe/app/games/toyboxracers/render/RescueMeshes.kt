package com.Atom2Universe.app.games.toyboxracers.render

/** Drone jouet de dépannage, construit avec les mêmes volumes que les véhicules. */
internal object RescueMeshes {
    private val mint = floatArrayOf(.34f, .89f, .77f, 1f)
    private val cream = floatArrayOf(1f, .96f, .82f, 1f)
    private val dark = floatArrayOf(.10f, .16f, .23f, 1f)
    private val amber = floatArrayOf(1f, .70f, .18f, 1f)

    fun drone(): ColoredMesh {
        val b = MeshBuilder()
        b.lowPolyEllipsoid(0f, 0f, 0f, .46f, .23f, .33f, 6, 16, mint)
        b.lowPolyEllipsoid(0f, -.17f, 0f, .30f, .12f, .23f, 4, 12, amber)
        b.lowPolyEllipsoid(0f, .15f, -.035f, .30f, .16f, .23f, 4, 12, cream)
        for (x in floatArrayOf(-.15f, .15f)) {
            b.box(x, .015f, .298f, .135f, .115f, .05f, dark)
            b.box(x, .027f, .330f, .045f, .063f, .018f, cream)
        }
        b.box(0f, -.08f, .325f, .105f, .027f, .025f, dark)
        for (x in floatArrayOf(-.55f, .55f)) for (z in floatArrayOf(-.28f, .28f)) {
            b.box(x * .75f, .07f, z, .48f, .045f, .055f, dark)
            b.cylinderY(x, .09f, z, .08f, .085f, 10, amber)
        }
        b.cylinderY(0f, .34f, -.03f, .19f, .025f, 8, dark)
        b.lowPolyEllipsoid(0f, .44f, -.03f, .055f, .055f, .055f, 4, 8, amber)
        return b.build()
    }

    fun propeller(): ColoredMesh {
        val b = MeshBuilder()
        b.box(0f, 0f, 0f, .45f, .016f, .055f, cream)
        b.box(0f, 0f, 0f, .055f, .016f, .45f, mint)
        return b.build()
    }

    /** Longueur unitaire ; le rendu l'étire entre le drone et le crochet. */
    fun cable(): ColoredMesh {
        val b = MeshBuilder()
        b.cylinderY(0f, 0f, 0f, 1f, .018f, 8, dark)
        return b.build()
    }

    fun hook(): ColoredMesh {
        val b = MeshBuilder()
        b.box(0f, 0f, 0f, .24f, .04f, .04f, amber)
        for (x in floatArrayOf(-.10f, .10f)) b.box(x, .065f, 0f, .04f, .14f, .04f, amber)
        return b.build()
    }
}
