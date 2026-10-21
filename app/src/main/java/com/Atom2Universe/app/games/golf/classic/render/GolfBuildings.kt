package com.Atom2Universe.app.games.golf.classic.render

/** Metre-scale architecture baked into the scenery VBO, without image textures. Front is +Z. */
internal object GolfBuildings {
    private val stone = C(.52f,.50f,.44f)
    private val timber = C(.28f,.21f,.16f)
    private val trim = C(.92f,.88f,.75f)
    private val glass = C(.32f,.53f,.59f)

    fun house(wall: C, roof: C, lodge: Boolean = false): MeshBuilder = MeshBuilder().apply {
        val w = if(lodge) 14f else 7.6f
        val d = if(lodge) 8.4f else 5.6f
        val eave = if(lodge) 6.2f else 3.1f
        val ridge = eave + if(lodge) 2.6f else 2.1f
        val front = d*.5f
        box(0f,-1.0f,0f,w+.5f,1.22f,d+.5f,stone)
        box(0f,.22f,0f,w,eave-.22f,d,wall)
        box(0f,.22f,0f,w+.12f,.24f,d+.12f,stone.shade(.82f))
        roof(this,w,d,eave,ridge,roof,wall)
        // Corner dressings and a continuous fascia keep the silhouette readable at distance.
        for(s in intArrayOf(-1,1)) for(t in intArrayOf(-1,1))
            box(s*(w*.5f-.13f),.45f,t*(front-.08f),.28f,eave-.5f,.22f,trim)
        for(s in intArrayOf(-1,1)) box(0f,eave-.16f,s*(front+.06f),w+.30f,.18f,.18f,timber)
        if(lodge) box(0f,3.12f,0f,w+.15f,.22f,d+.15f,stone)
        // The 2.2 m door is deliberately human-sized, independently of the building width.
        box(0f,.25f,front+.07f,1.34f,2.28f,.14f,trim)
        box(0f,.28f,front+.16f,1.08f,2.15f,.08f,timber)
        box(0f,1.35f,front+.21f,.72f,.72f,.025f,glass)
        box(.36f,1.20f,front+.25f,.07f,.07f,.08f,C(.86f,.68f,.30f))
        box(0f,-.03f,front+1.0f,3.0f,.18f,1.8f,stone)
        box(0f,.15f,front+.65f,2.4f,.14f,1.1f,trim)
        // Shallow entrance canopy with two posts, rather than an oversized roof over a tiny door.
        for(s in intArrayOf(-1,1)) box(s*1.05f,.25f,front+1.23f,.14f,2.35f,.14f,timber)
        quad(P(-1.45f,2.57f,front+1.65f),P(1.45f,2.57f,front+1.65f),
            P(1.45f,2.90f,front-.05f),P(-1.45f,2.90f,front-.05f),roof)
        box(0f,2.48f,front+1.58f,2.9f,.12f,.14f,timber)
        val columns = if(lodge) listOf(-5.1f,-2.65f,2.65f,5.1f) else listOf(-2.35f,2.35f)
        for(x in columns) for(back in listOf(false,true))
            window(this,x,.95f,if(back)-front else front,1.12f,1.35f,back)
        if(lodge) for(x in listOf(-5.1f,-2.55f,0f,2.55f,5.1f)) for(back in listOf(false,true))
            window(this,x,3.9f,if(back)-front else front,1.12f,1.40f,back)
        // Windows on the gable ends use rotated local facade pieces.
        for(side in intArrayOf(-1,1)) {
            val end = MeshBuilder()
            window(end,0f,1.0f,0f,1.10f,1.25f,false)
            if(lodge) window(end,0f,4.0f,0f,1.10f,1.25f,false)
            append(end,P(side*w*.5f,0f,0f),side*kotlin.math.PI.toFloat()*.5f)
        }
        val chimneyX=w*.28f; val chimneyZ=-d*.12f
        box(chimneyX,eave+.2f,chimneyZ,.85f,ridge-eave+.9f,.85f,stone.shade(.86f))
        box(chimneyX,ridge+1.1f,chimneyZ,1.05f,.18f,1.05f,trim)
        box(chimneyX,ridge+1.28f,chimneyZ,.68f,.08f,.68f,timber)
    }

    private fun window(b:MeshBuilder,x:Float,y:Float,z:Float,w:Float,h:Float,back:Boolean) = with(b) {
        val s=if(back)-1f else 1f
        box(x,y-.10f,z+s*.055f,w+.24f,h+.20f,.13f,timber)
        box(x,y,z+s*.135f,w,h,.045f,glass)
        for(edge in intArrayOf(-1,1)) {
            box(x+edge*(w*.5f+.035f),y-.02f,z+s*.18f,.09f,h+.04f,.08f,trim)
            // Closed-width shutters sit beside, never across, the glass.
            box(x+edge*(w*.5f+.29f),y,z+s*.10f,.35f,h,.09f,C(.31f,.40f,.33f))
        }
        for(level in listOf(0f,h*.52f,h)) box(x,y+level,z+s*.18f,w+.12f,.07f,.08f,trim)
        box(x,y,z+s*.18f,.06f,h,.08f,trim)
        box(x,y-.13f,z+s*.20f,w+.42f,.13f,.34f,stone)
        // A restrained pale reflection gives the panes some depth without emission or textures.
        box(x-w*.27f,y+h*.58f,z+s*.165f,w*.32f,h*.30f,.015f,C(.61f,.72f,.73f))
    }

    private fun roof(b:MeshBuilder,w:Float,d:Float,eave:Float,ridge:Float,c:C,wall:C) = with(b) {
        val half=w*.5f+.45f; val run=d*.5f+.45f
        val wallEave=eave+(ridge-eave)*.45f/run
        for(s in intArrayOf(-1,1)) {
            quad(P(-half,eave,s*run),P(half,eave,s*run),P(half,ridge,0f),P(-half,ridge,0f),c)
            quad(P(s*w*.5f,eave,-d*.5f),P(s*w*.5f,eave,d*.5f),
                P(s*w*.5f,wallEave,d*.5f),P(s*w*.5f,wallEave,-d*.5f),wall)
            tri(P(s*w*.5f,wallEave,-d*.5f),P(s*w*.5f,wallEave,d*.5f),P(s*w*.5f,ridge,0f),wall)
            beam(P(s*half,eave,-run),P(s*half,ridge,0f),.085f,trim,4)
            beam(P(s*half,ridge,0f),P(s*half,eave,run),.085f,trim,4)
            // Broad courses rather than thousands of individual tiles.
            for(i in 1..7) {
                val z=run*i/8f; val y=ridge-(ridge-eave)*i/8f
                beam(P(-half,y+.015f,s*z),P(half,y+.015f,s*z),.025f,c.shade(.79f),4)
            }
        }
        beam(P(-half,ridge+.025f,0f),P(half,ridge+.025f,0f),.13f,c.shade(.85f),6)
    }
}
