package com.Atom2Universe.app.games.caves.render

/** Bestiaire voxel : matériaux mats et désaturés, détails liés aux articulations. */
internal object EnemyModels {
    private val ink=0xFF383641.toInt()
    private val bone=0xFFE6DDC7.toInt()
    private val leather=0xFF887066.toInt()
    private val steel=0xFF9CA8B1.toInt()
    private val glow=0xFFB4E2D7.toInt()
    val all: Map<String, MobModel> by lazy {
        mapOf("zombie" to zombie(), "skeleton" to skeleton(), "ogre" to ogre(),
            "dwarf" to dwarf(), "goblin" to goblin(), "troll" to troll(), "golem" to golem(),
            "wraith" to wraith(), "spider" to spider(), "imp" to imp(), "mummy" to mummy(),
            "slime" to slime(), "soldier" to soldier())
    }

    private class Builder {
        val parts=mutableListOf<MobPart>()
        fun box(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,c:Int,
                limb:Limb=Limb.NONE,side:Int=0,py:Float=y+h/2,pz:Float=z,px:Float=x,
                tilt:Float=0f,lit:Boolean=false) {
            parts += MobPart(x,y,z,w,h,d,c,limb,side,py,tilt,lit,pz,px)
        }
        fun legs(x:Float,height:Float,width:Float,c:Int) {
            for (side in listOf(-1,1)) {
                box(side*x,height/2,0f,width,height,width,c,Limb.LEG,side,py=height,pz=0f)
                box(side*x,1.2f,.6f,width+.4f,2.4f,width+1.3f,leather,Limb.LEG,side,py=height,pz=0f)
            }
        }
        fun arms(x:Float,shoulder:Float,length:Float,width:Float,c:Int,tilt:Float=0f) {
            for (side in listOf(-1,1)) {
                box(side*x,shoulder-length/2,0f,width,length,width,c,Limb.ARM,side,py=shoulder,pz=0f,tilt=tilt)
                box(side*x,shoulder-length+.8f,.3f,width+.5f,2.6f,width+.6f,c,Limb.ARM,side,py=shoulder,pz=0f,tilt=tilt)
            }
        }
        fun head(y:Float,w:Float,h:Float,d:Float,c:Int,eyeColor:Int=ink,z:Float=0f) {
            val pivot=y-h/2
            box(0f,y,z,w,h,d,c,Limb.LOOK,py=pivot,pz=z,px=0f)
            for (side in listOf(-1,1))
                box(side*w*.24f,y+.4f,z+d/2+.18f,1.4f,1.3f,.5f,eyeColor,
                    Limb.LOOK,py=pivot,pz=z,px=0f,lit=eyeColor==glow)
        }
        /** Même hauteur de référence que le modèle historique : sélection et barres de vie stables. */
        fun build(height:Float,gait:Float=8f,stride:Float=.42f,breath:Float=.18f,
                  floats:Boolean=false,squash:Boolean=false):MobModel {
            require(parts.size<=48) { "Enemy model exceeds the geometry budget" }
            val scale=height/parts.maxOf { it.cy+it.h/2 }
            val result=parts.map { p ->
                MobPart(p.cx,p.cy*scale,p.cz,p.w,p.h*scale,p.d,p.color,p.limb,p.side,
                    p.pivotY*scale,p.baseTiltDeg,p.emissive,p.pivotZ,p.pivotX)
            }
            return MobModel(result,height,squash=squash,floats=floats,gait=gait,stride=stride,breath=breath)
        }
    }

    private fun zombie()=Builder().apply {
        val skin=0xFF9CAE92.toInt(); val shirt=0xFF889AAB.toInt()
        legs(3f,12f,4f,0xFF78778A.toInt())
        box(0f,17f,0f,10f,11f,6f,shirt)
        box(0f,12.4f,0f,10.3f,1.5f,6.3f,leather)
        arms(7f,22f,11f,3f,skin,-52f)
        head(26.5f,8f,8f,8f,skin)
        box(0f,30f,-.6f,8.3f,1f,7f,0xFF677566.toInt(),Limb.LOOK,py=22.5f,pz=0f,px=0f)
        box(-2f,25f,4.3f,2.3f,1f,.5f,0xFFB5BC9C.toInt(),Limb.LOOK,py=22.5f,pz=0f,px=0f)
        box(1.2f,24.1f,4.3f,3.2f,.5f,.5f,ink,Limb.LOOK,py=22.5f,pz=0f,px=0f)
        box(-2.8f,16f,3.2f,2.3f,3.5f,.5f,0xFFB9ADA4.toInt())
        box(2.3f,19f,3.2f,2f,.7f,.5f,leather)
    }.build(30.5f,gait=5.6f,stride=.32f,breath=.3f)

    private fun skeleton()=Builder().apply {
        legs(2.5f,12f,2.2f,bone)
        box(0f,16.5f,-1f,2f,11f,2f,bone)
        box(0f,12f,0f,7f,2.5f,4f,bone)
        for (y in listOf(15f,18f,21f)) for (side in listOf(-1,1))
            box(side*2.5f,y,.4f,4f,1.3f,4.2f,bone)
        arms(6f,22f,11f,2f,bone)
        head(26f,7f,7f,7f,bone)
        box(0f,23f,1f,5.5f,1.3f,5f,bone,Limb.LOOK,py=22.5f,pz=0f,px=0f)
        box(0f,25f,3.7f,1f,1.4f,.5f,ink,Limb.LOOK,py=22.5f,pz=0f,px=0f)
        for(x in listOf(-1.5f,0f,1.5f))
            box(x,23f,3.6f,.5f,.8f,.4f,leather,Limb.LOOK,py=22.5f,pz=0f,px=0f)
    }.build(30f,gait=8.8f,stride=.45f,breath=.07f)

    private fun ogre()=Builder().apply {
        val skin=0xFFB3BA91.toInt()
        legs(5f,14f,6f,skin)
        box(0f,22f,0f,18f,14f,13f,skin)
        box(0f,21f,6.2f,12f,10f,2f,0xFFC9C6A1.toInt())
        box(0f,14f,0f,17f,5f,12f,leather)
        box(0f,15f,6.3f,3f,2f,1f,steel)
        arms(11f,30f,16f,5f,skin)
        head(32f,9f,8f,9f,skin)
        for(side in listOf(-1,1)) {
            box(side*2.8f,29.7f,5f,1.6f,3f,1.6f,bone,Limb.LOOK,py=28f,pz=0f,px=0f)
            box(side*11f,19f,0f,5.5f,2f,5.5f,leather,Limb.ARM,side,py=30f,pz=0f)
        }
        box(0f,31f,5f,3f,2f,2.5f,skin,Limb.LOOK,py=28f,pz=0f,px=0f)
    }.build(36f,gait=4.7f,stride=.28f,breath=.38f)

    private fun dwarf()=Builder().apply {
        val skin=0xFFD5B39B.toInt(); val tunic=0xFF9485A5.toInt(); val beard=0xFFB48B6B.toInt()
        legs(3.5f,8f,5f,leather)
        box(0f,13f,0f,12f,10f,8f,tunic)
        box(0f,9f,0f,12.5f,2f,8.5f,leather)
        box(0f,9f,4.5f,3f,2.3f,1f,bone)
        arms(8f,18f,10f,4f,skin)
        head(21.5f,8f,7f,8f,skin)
        box(0f,26f,0f,9f,4f,9f,steel,Limb.LOOK,py=18f,pz=0f,px=0f)
        box(0f,28.5f,0f,2f,3f,7f,0xFFB4BDC1.toInt(),Limb.LOOK,py=18f,pz=0f,px=0f)
        for(side in listOf(-1,1))
            box(side*2.4f,18f,4f,3.8f,8f,3f,beard,Limb.LOOK,py=18f,pz=0f,px=0f)
        box(0f,20f,5f,3f,2f,2f,skin,Limb.LOOK,py=18f,pz=0f,px=0f)
        box(8f,9f,3f,1.7f,10f,1.7f,leather,Limb.ARM,1,py=18f,pz=0f)
        box(8f,13f,3f,7f,4f,4f,steel,Limb.ARM,1,py=18f,pz=0f)
    }.build(31f,gait=7f,stride=.36f)

    private fun goblin()=Builder().apply {
        val skin=0xFFA5BE94.toInt()
        legs(2.5f,10f,3f,skin)
        box(0f,13f,1f,8f,7f,5f,0xFFAD927C.toInt())
        arms(5.5f,17f,9f,2.5f,skin,-15f)
        head(20f,7f,7f,7f,skin,z=1.5f)
        for(side in listOf(-1,1)) {
            box(side*5f,21f,1f,4f,2.5f,2f,skin,Limb.LOOK,py=16.5f,pz=1.5f,px=0f)
            box(side*6.8f,21.5f,1f,1.5f,1.4f,1.5f,skin,Limb.LOOK,py=16.5f,pz=1.5f,px=0f)
        }
        box(0f,19f,5.7f,2f,2.6f,2.3f,skin,Limb.LOOK,py=16.5f,pz=1.5f,px=0f)
        box(-2.2f,12f,4f,3f,4f,2f,leather)
        box(5.5f,8f,2f,1.6f,5f,2f,steel,Limb.ARM,1,py=17f,pz=0f,tilt=-15f)
    }.build(24f,gait=11f,stride=.6f,breath=.2f)

    private fun troll()=Builder().apply {
        val skin=0xFF98AC98.toInt(); val moss=0xFFB3BE8C.toInt()
        legs(4f,16f,5f,skin)
        box(0f,26f,0f,13f,16f,9f,skin)
        arms(8.5f,33f,20f,4f,skin)
        head(37f,8f,7f,8f,skin,glow)
        box(0f,36f,4.8f,3f,4f,3f,skin,Limb.LOOK,py=33.5f,pz=0f,px=0f)
        box(-4f,32f,-1f,8f,4f,10f,moss)
        box(2f,23f,4.6f,6f,7f,1.3f,moss)
        for(side in listOf(-1,1)) {
            box(side*8.5f,17f,0f,4.4f,3f,4.4f,moss,Limb.ARM,side,py=33f,pz=0f)
            box(side*2.6f,34f,4.4f,1.2f,2f,1.5f,bone,Limb.LOOK,py=33.5f,pz=0f,px=0f)
        }
    }.build(40.5f,gait=5.2f,stride=.35f,breath=.3f)

    private fun golem()=Builder().apply {
        val stone=0xFFAAB4B9.toInt(); val dark=0xFF839199.toInt()
        legs(5f,12f,7f,dark)
        box(0f,22f,0f,18f,18f,12f,stone)
        arms(12f,30f,18f,6f,stone)
        head(36f,11f,9f,11f,dark,glow)
        for(side in listOf(-1,1)) {
            box(side*12f,28f,0f,7f,5f,7f,dark,Limb.ARM,side,py=30f,pz=0f)
            box(side*5f,8f,3.7f,5f,4f,1f,stone,Limb.LEG,side,py=12f,pz=0f)
        }
        box(0f,24f,6.2f,5f,6f,.7f,dark)
        box(0f,24f,6.7f,1f,4f,.4f,glow,lit=true)
        box(0f,24f,6.7f,3f,1f,.4f,glow,lit=true)
        box(-5f,17f,6.2f,1f,7f,.5f,dark)
        box(-3.5f,20f,6.2f,4f,1f,.5f,dark)
    }.build(40.5f,gait=4f,stride=.23f,breath=.05f)

    private fun wraith()=Builder().apply {
        val robe=0xFF8A82A9.toInt(); val dark=0xFF655F80.toInt()
        box(0f,18f,0f,11f,16f,9f,robe)
        for(side in listOf(-1,1)) {
            box(side*4f,6f,0f,5f,12f,10f,dark,Limb.CLOTH,side,py=12f,pz=0f)
            box(side*7f,16f,0f,3f,12f,4f,robe,Limb.ARM,side,py=22f,pz=0f,tilt=-25f)
            box(side*7f,10f,2f,2f,3f,3f,glow,Limb.ARM,side,py=22f,pz=0f,tilt=-25f)
        }
        box(0f,27f,0f,9f,10f,9f,dark,Limb.LOOK,py=22f,pz=0f,px=0f)
        box(0f,27f,4.6f,6f,6f,.5f,ink,Limb.LOOK,py=22f,pz=0f,px=0f)
        for(side in listOf(-1,1))
            box(side*1.8f,27.7f,5f,1.5f,1f,.4f,glow,Limb.LOOK,py=22f,pz=0f,px=0f,lit=true)
        box(0f,19f,4.7f,2f,3f,.7f,bone)
    }.build(32f,gait=5f,stride=.12f,breath=.2f,floats=true)

    private fun spider()=Builder().apply {
        val body=0xFF968A9C.toInt(); val legs=0xFF706679.toInt()
        box(0f,4.5f,-5f,11f,8f,11f,body)
        box(0f,4f,3f,8f,6f,8f,legs)
        box(0f,8.2f,-5f,5f,1f,6f,0xFFB5A6B8.toInt())
        for(side in listOf(-1,1)) {
            for(i in 0..3) {
                val z=5f-i*3.2f
                val phase=if(i%2==0) side else -side
                box(side*5.8f,4.5f,z,5f,1.5f,1.6f,legs,Limb.CRAWL,phase,py=5f,pz=z,px=side*3.5f)
                box(side*8f,2.7f,z,1.6f,4.5f,1.6f,body,Limb.CRAWL,phase,py=5f,pz=z,px=side*3.5f)
            }
            box(side*2.1f,5.5f,7.2f,1.5f,1.5f,.7f,0xFFE9B8AD.toInt(),lit=true)
            box(side*3.2f,4.7f,7.2f,.8f,.8f,.6f,0xFFE9B8AD.toInt(),lit=true)
            box(side*1.5f,2.5f,7f,1f,2.5f,2f,bone)
        }
    }.build(9f,gait=13f,stride=.2f,breath=.08f)

    private fun imp()=Builder().apply {
        val skin=0xFFCC9790.toInt(); val wing=0xFFAB8796.toInt()
        legs(2f,7f,2.5f,skin)
        box(0f,9.5f,0f,6f,6f,4f,skin)
        arms(4.5f,13f,7f,2f,skin,-20f)
        head(15f,6f,6f,6f,skin)
        for(side in listOf(-1,1)) {
            box(side*2f,18f,0f,1.4f,3f,1.4f,bone,Limb.LOOK,py=12f,pz=0f,px=0f)
            box(side*4.5f,11f,-2.5f,4f,5f,1f,wing,Limb.WING,side,py=13f,px=side*2.5f)
            box(side*6.5f,11.5f,-2.5f,1f,3f,1f,bone,Limb.WING,side,py=13f,px=side*2.5f)
        }
        box(0f,7f,-4f,1.5f,1.5f,5f,skin,Limb.TAIL,py=8f,pz=-1.5f)
        box(0f,7f,-7f,3f,2f,2f,wing,Limb.TAIL,py=8f,pz=-1.5f)
    }.build(18.5f,gait=12f,stride=.55f,breath=.2f)

    private fun mummy()=Builder().apply {
        val linen=0xFFE1D4B8.toInt(); val seam=0xFFBEAF99.toInt()
        legs(3f,12f,4f,linen)
        box(0f,17f,0f,10f,11f,6f,linen)
        arms(7f,22f,12f,3f,linen,-62f)
        head(26.5f,8f,8f,8f,linen)
        for(y in listOf(13f,16f,19f,21f)) box(0f,y,3.2f,10.2f,.7f,.5f,seam)
        for(y in listOf(24f,29f)) box(0f,y,4.2f,8.2f,.7f,.5f,seam,Limb.LOOK,py=22.5f,pz=0f,px=0f)
        for(side in listOf(-1,1)) {
            box(side*7f,17f,1.6f,3.2f,1f,.5f,seam,Limb.ARM,side,py=22f,pz=0f,tilt=-62f)
            box(side*3f,6f,2.2f,4.2f,.8f,.5f,seam,Limb.LEG,side,py=12f,pz=0f)
        }
        box(-4f,10f,3.7f,2f,7f,.5f,linen,Limb.CLOTH,-1,py=13.5f)
    }.build(30.5f,gait=5f,stride=.25f,breath=.12f)

    private fun slime()=Builder().apply {
        val jelly=0xFFA3C6AB.toInt()
        // Enveloppe de référence : le renderer dessine ici le dôme de SlimeGeometry.
        box(0f,6.5f,0f,17f,13f,17f,jelly)
        for(side in listOf(-1,1)) {
            box(side*2.6f,6f,7.4f,1.4f,2f,.7f,ink)
            box(side*2.6f-.2f,6.5f,7.8f,.4f,.5f,.2f,bone)
        }
        box(0f,3.7f,8.2f,2f,.5f,.4f,ink)
        box(-3f,9f,5.6f,1f,1f,.3f,0xFFD9E8CF.toInt())
    }.build(13f,gait=5f,breath=0f,squash=true)

    private fun soldier()=Builder().apply {
        val uniform=0xFF99A38D.toInt(); val vest=0xFF758778.toInt(); val skin=0xFFD4B39C.toInt()
        legs(2.5f,12f,3.8f,uniform)
        box(0f,17f,0f,10f,11f,6f,uniform)
        box(0f,17.5f,0f,10.6f,8f,6.6f,vest)
        for(side in listOf(-1,1)) {
            box(side*2.7f,17f,3.7f,3f,3.5f,1.2f,leather)
            box(side*6.5f,16.5f,0f,3f,11f,3f,uniform,Limb.ARM,side,py=22f,pz=0f,tilt=if(side<0) -70f else -80f)
        }
        head(26.5f,8f,8f,8f,skin)
        box(0f,30.3f,0f,8.8f,2.8f,8.8f,vest,Limb.LOOK,py=22.5f,pz=0f,px=0f)
        box(0f,29.3f,4f,9.2f,1f,2f,vest,Limb.LOOK,py=22.5f,pz=0f,px=0f)
        box(-4.2f,26.5f,0f,1f,3f,3f,ink,Limb.LOOK,py=22.5f,pz=0f,px=0f)
        box(0f,18f,-4f,7f,8f,3f,leather)
        box(1.5f,19.5f,9f,2f,2.6f,13f,ink,Limb.WEAPON)
        box(1.5f,20.9f,10f,1f,1f,4f,steel,Limb.WEAPON)
        box(1.5f,19.5f,17f,2.2f,2.2f,3f,0xFFFFD785.toInt(),Limb.MUZZLE_FLASH,lit=true)
    }.build(31.7f,gait=8f,stride=.4f,breath=.12f)
}
