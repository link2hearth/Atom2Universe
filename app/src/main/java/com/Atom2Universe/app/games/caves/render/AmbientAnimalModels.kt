package com.Atom2Universe.app.games.caves.render

import com.Atom2Universe.app.games.caves.entity.AmbientWildlife.Kind

/** Same closed voxel parts as the farm animals. Dimensions in world blocks, +Z forward.
 * All coats are built once; wings and tails rotate around their anatomical attachment points. */
internal object AmbientAnimalModels {
    private const val DARK = 0x34323A
    private const val EYE = 0x171D25
    private const val IVORY = 0xEFE6CF

    private fun p(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int,
                  limb: Limb = Limb.NONE, side: Int = 0,
                  px: Float = 0f, py: Float = 0f, pz: Float = 0f, glow: Boolean = false) =
        MobPart(x,y,z,w,h,d,color,limb,side,pivotY=py,pivotX=px,pivotZ=pz,emissive=glow)

    private val models = Kind.entries.associateWith { kind ->
        Array(4) { coat -> when (kind) {
            Kind.BIRD -> bird(coat)
            Kind.BUTTERFLY -> butterfly(coat)
            Kind.BEE -> bee()
            Kind.FISH -> fish(coat)
            Kind.DRAGONFLY -> dragonfly(coat)
            Kind.FIREFLY -> firefly()
        } }
    }
    val maxParts = models.values.maxOf { coats -> coats.maxOf { it.size } }
    fun get(kind: Kind, coat: Int): List<MobPart> = models.getValue(kind)[coat.coerceIn(0,3)]

    private fun bird(coat: Int): List<MobPart> = buildList {
        val back = when (coat) { 0 -> 0x6E8491; 1 -> 0x92765D; 2 -> 0x686B77; else -> 0x8B8174 }
        val breast = if (coat == 1) 0xC99671 else 0xC9C5B5
        add(p(0f,0f,0f,.22f,.23f,.34f,back))
        add(p(0f,-.060f,.045f,.18f,.13f,.27f,breast))
        add(p(0f,.10f,.19f,.18f,.18f,.18f,back))
        add(p(0f,.065f,.255f,.13f,.09f,.09f,breast))
        add(p(0f,.075f,.315f,.065f,.05f,.075f,0xCDA561))
        add(p(0f,.070f,.36f,.035f,.03f,.025f,0xA9844C))
        for (side in listOf(-1,1)) {
            add(p(side*.092f,.12f,.235f,.018f,.035f,.03f,EYE))
            add(p(side*.095f,.13f,.244f,.018f,.012f,.01f,IVORY))
            // Thick shoulder and layered flight feathers, with a stepped trailing edge.
            add(p(side*.20f,.01f,-.015f,.22f,.065f,.23f,back,Limb.WING,side,px=side*.10f))
            add(p(side*.36f,0f,-.055f,.18f,.045f,.25f,back,Limb.WING,side,px=side*.10f))
            for (feather in 0..2) {
                add(p(side*(.46f+feather*.025f),-.01f,-.15f+feather*.065f,
                    .15f,.028f,.047f,if(feather==0) DARK else back,Limb.WING,side,px=side*.10f))
            }
            add(p(side*.044f,-.12f,-.075f,.024f,.045f,.085f,0xA8855F))
            add(p(side*.057f,-.018f,-.265f,.092f,.032f,.22f,back,Limb.TAIL,pz=-.15f))
        }
    }

    private fun butterfly(coat: Int): List<MobPart> = buildList {
        val color = when (coat) { 0 -> 0xD99343; 1 -> 0x69A8C6; 2 -> 0xB98BBF; else -> 0xD7C785 }
        add(p(0f,0f,0f,.035f,.044f,.17f,DARK))
        add(p(0f,.012f,.10f,.05f,.05f,.045f,DARK))
        for (side in listOf(-1,1)) {
            // Four separate lobes with a dark rim and inset pigment on both faces.
            for (front in listOf(false,true)) {
                val z=if(front) .05f else -.082f
                val w=if(front) .19f else .14f
                val d=if(front) .16f else .11f
                val x=side*(.025f+w/2)
                add(p(x,0f,z,w,.026f,d,DARK,Limb.WING,side,px=side*.02f))
                add(p(x,.002f,z,w-.03f,.031f,d-.025f,color,Limb.WING,side,px=side*.02f))
                add(p(x+side*.035f,.002f,z+.022f,.024f,.035f,.032f,IVORY,Limb.WING,side,px=side*.02f))
            }
            add(p(side*.022f,.045f,.128f,.012f,.063f,.013f,DARK))
            add(p(side*.027f,.077f,.132f,.020f,.018f,.02f,IVORY))
            for (leg in 0..2) add(p(side*.03f,-.030f,.04f-leg*.04f,.036f,.013f,.012f,DARK))
        }
    }

    private fun bee(): List<MobPart> = buildList {
        // Contiguous abdominal segments give real stripes on the top, underside and both sides.
        for (segment in 0..4) {
            val size=if(segment==0 || segment==4) .105f else .14f
            add(p(0f,0f,-.09f+segment*.042f,size,size*.85f,.044f,
                if(segment%2==0) 0xD5AC54 else 0x4F4436))
        }
        add(p(0f,.015f,.14f,.105f,.10f,.075f,0x554837))
        for (side in listOf(-1,1)) {
            add(p(side*.045f,.027f,.177f,.028f,.036f,.019f,EYE))
            add(p(side*.045f,.037f,.187f,.012f,.012f,.009f,IVORY))
            add(p(side*.029f,.085f,.155f,.012f,.06f,.013f,DARK))
            for (leg in 0..2) add(p(side*.075f,-.074f,.06f-leg*.06f,.055f,.018f,.016f,DARK))
            add(p(side*.13f,.047f,.035f,.17f,.018f,.075f,0xD4E1D9,Limb.WING,side,px=side*.055f,py=.04f))
            add(p(side*.105f,.043f,-.045f,.12f,.016f,.065f,0xB4CAC6,Limb.WING,side,px=side*.055f,py=.04f))
        }
    }

    private fun fish(coat: Int): List<MobPart> = buildList {
        val scales=when(coat) { 0 -> 0xB4936E; 1 -> 0x759F93; 2 -> 0xA8B6B5; else -> 0xB9A777 }
        val fin=if(coat==1) 0x527C76 else 0x7B8989
        add(p(0f,0f,0f,.15f,.23f,.28f,scales))
        add(p(0f,-.065f,.02f,.13f,.095f,.24f,0xD2CEB2))
        add(p(0f,.055f,-.005f,.12f,.14f,.32f,scales))
        add(p(0f,0f,.19f,.12f,.165f,.12f,scales))
        add(p(0f,-.024f,.257f,.058f,.055f,.026f,0xA69C8B))
        add(p(0f,0f,-.20f,.085f,.13f,.14f,scales,Limb.TAIL,pz=-.14f))
        add(p(0f,0f,-.30f,.045f,.10f,.10f,fin,Limb.TAIL,pz=-.14f))
        add(p(0f,0f,-.375f,.032f,.245f,.08f,fin,Limb.TAIL,pz=-.14f))
        add(p(0f,.145f,-.04f,.030f,.105f,.12f,fin))
        for (side in listOf(-1,1)) {
            add(p(side*.063f,.025f,.205f,.016f,.05f,.046f,0xD9CFA0))
            add(p(side*.073f,.025f,.213f,.012f,.025f,.025f,EYE))
            add(p(side*.079f,.037f,.223f,.009f,.012f,.01f,IVORY))
            add(p(side*.095f,-.053f,.055f,.065f,.025f,.11f,fin,Limb.WING,side,px=side*.06f))
            add(p(side*.077f,.005f,.113f,.009f,.11f,.017f,fin))
        }
    }

    private fun dragonfly(coat: Int): List<MobPart> = buildList {
        val color=if(coat%2==0) 0x599E9A else 0x728AAF
        add(p(0f,0f,.04f,.07f,.075f,.14f,color))
        for (segment in 0..4) {
            val w=.047f-segment*.005f
            add(p(0f,0f,-.06f-segment*.039f,w,w,.04f,if(segment%2==0) color else 0x466A70))
        }
        add(p(0f,.02f,.135f,.075f,.065f,.055f,color))
        for (side in listOf(-1,1)) {
            add(p(side*.035f,.032f,.15f,.040f,.044f,.035f,0x374B4D))
            for (z in listOf(.055f,-.035f)) {
                add(p(side*.16f,.033f,z,.26f,.014f,.055f,0xBDD2CB,Limb.WING,side,px=side*.025f,py=.03f))
                add(p(side*.18f,.035f,z,.23f,.017f,.008f,0x7DABA4,Limb.WING,side,px=side*.025f,py=.03f))
            }
            for (leg in 0..2) add(p(side*.038f,-.045f,.08f-leg*.04f,.045f,.012f,.012f,DARK))
        }
    }

    private fun firefly(): List<MobPart> = buildList {
        add(p(0f,0f,0f,.062f,.050f,.08f,0x655C3C))
        add(p(0f,.002f,.065f,.05f,.045f,.045f,DARK))
        add(p(0f,-.004f,-.065f,.057f,.047f,.055f,0xDDEBA0,glow=true))
        for (side in listOf(-1,1)) {
            add(p(side*.035f,.027f,-.004f,.035f,.017f,.088f,0x7B7451))
            add(p(side*.07f,.023f,0f,.08f,.012f,.037f,0xB3BA9A,Limb.WING,side,px=side*.022f))
        }
    }
}
