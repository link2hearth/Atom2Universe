package com.Atom2Universe.app.games.golf.classic.render

/** A crouching rabbit facing -Z, with an actual muzzle, haunches and separate ear pivots. */
internal class GolfRabbit(snowy:Boolean) {
    private val fur=if(snowy) C(.92f,.94f,.96f) else C(.73f,.65f,.54f)
    private val cream=C(.96f,.92f,.85f)
    val body=MeshBuilder().apply {
        ellipsoid(0f,.32f,.09f,.23f,.27f,.38f,fur)
        ellipsoid(0f,.35f,-.17f,.18f,.24f,.22f,cream)
        for(s in intArrayOf(-1,1)) {
            ellipsoid(s*.17f,.21f,.20f,.14f,.18f,.20f,fur)
            ellipsoid(s*.17f,.07f,.06f,.10f,.07f,.22f,fur)
            ellipsoid(s*.10f,.065f,-.30f,.07f,.06f,.13f,cream)
        }
        ellipsoid(0f,.56f,-.27f,.19f,.18f,.20f,fur)
        for(s in intArrayOf(-1,1)) {
            ellipsoid(s*.065f,.51f,-.43f,.075f,.055f,.075f,cream)
            ellipsoid(s*.16f,.60f,-.36f,.026f,.034f,.028f,C(.075f,.055f,.045f))
            ellipsoid(s*.17f,.612f,-.377f,.008f,.009f,.008f,C(1f,1f,1f))
            for(i in 0..2) beam(P(s*.07f,.51f-i*.012f,-.49f),P(s*(.22f+i*.015f),.53f-i*.025f,-.48f),.002f,cream,3)
        }
        ellipsoid(0f,.53f,-.492f,.026f,.017f,.016f,C(.64f,.36f,.36f))
        beam(P(0f,.515f,-.493f),P(0f,.49f,-.49f),.003f,C(.32f,.25f,.22f),4)
        ellipsoid(0f,.33f,.445f,.09f,.085f,.10f,cream)
    }.build()
    val ear=MeshBuilder().apply {
        ellipsoid(0f,.16f,0f,.05f,.20f,.035f,fur)
        ellipsoid(0f,.17f,-.029f,.025f,.15f,.012f,C(.83f,.55f,.55f))
    }.build()
    val meshes get()=listOf(body,ear)
}
