package com.Atom2Universe.app.games.caves.render

import com.Atom2Universe.app.games.caves.node.MobDef

/** Small, unarmed silhouettes with work clothes; moving workshop parts share the voxel renderer. */
internal object FrontierModels {
    fun definition(id: String, behavior: String, scale: Float = .9f) = MobDef(
        id=id, hpBase=20, damageBase=0, speed=.7f, attackRange=0.0, detectRange=0.0,
        eyeHeight=1.5f, radius=.3f, spriteScale=scale, hpScalePerLevel=1.0, hpScaleCap=1.0,
        damageScalePer3Lvl=0, speedScalePerLevel=0f, biomes=emptyList(), model=id,
        spawnZoneMin=0, spawnWeight=0f, lootTable="", behavior=behavior, bossEligible=false, xpBase=0)

    val all: Map<String,MobModel> by lazy {
        (0..2).associate { "settler_$it" to resident(it) } + ("workshop_rotor" to rotor())
    }
    private fun resident(role: Int): MobModel {
        val parts=mutableListOf<MobPart>()
        val cloth=intArrayOf(0xFF819A73.toInt(),0xFF758C9C.toInt(),0xFF9D81A7.toInt())[role]
        val skin=0xFFD3AE8F.toInt();val dark=0xFF4A535D.toInt();val leather=0xFF876C51.toInt()
        fun box(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,c:Int,limb:Limb=Limb.NONE,side:Int=0,pivot:Float=y+h/2) {
            parts+=MobPart(x,y,z,w,h,d,c,limb,side,pivot)
        }
        for(side in listOf(-1,1)) {
            box(side*2.5f,5f,0f,4f,10f,4f,dark,Limb.LEG,side,10f)
            box(side*2.5f,1f,.8f,4.2f,2f,5.2f,leather,Limb.LEG,side,10f)
            box(side*6f,16f,0f,3f,9f,3f,cloth,Limb.ARM,side,20.5f)
            box(side*6f,11f,0f,3f,2f,3f,skin,Limb.ARM,side,20.5f)
        }
        box(0f,15.5f,0f,9f,11f,5f,cloth)
        box(0f,14f,2.7f,6f,8f,.6f,leather)
        box(0f,24.5f,0f,7f,7f,7f,skin,Limb.LOOK,0,21f)
        for(side in listOf(-1,1)) box(side*1.7f,25f,3.6f,1f,1f,.3f,dark,Limb.LOOK,0,21f)
        box(0f,28f,0f,if(role==0) 11f else 7.5f,1.5f,if(role==0) 10f else 7.5f,if(role==0) 0xFFD6B878.toInt() else cloth,Limb.LOOK,0,21f)
        if(role==2) box(0f,19f,2.9f,1.8f,2.4f,.6f,0xFFB4EDD8.toInt())
        return MobModel(parts,30f,gait=6f,stride=.35f,breath=.08f)
    }
    private fun rotor(): MobModel {
        val parts=mutableListOf<MobPart>()
        for(i in 0..3) parts+=MobPart(0f,15f,0f,3f,25f,1.2f,0xFFC4AA76.toInt(),
            Limb.ROTOR,pivotY=15f,baseTiltDeg=i*45f,pivotZ=0f,pivotX=0f)
        parts+=MobPart(0f,15f,.8f,5f,5f,2f,0xFF637C83.toInt())
        return MobModel(parts,30f,breath=0f)
    }
}
