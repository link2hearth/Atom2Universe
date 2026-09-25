package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Equipment has stable IDs; forged firearms still use the existing instance registry. */
internal object ExpeditionItems {
    const val FORGE: Short = 9890
    const val ANVIL: Short = 9891
    const val ROD: Short = 9892
    const val BAIT: Short = 9893
    const val RIVER_FISH: Short = 9894
    const val CAVE_FISH: Short = 9895
    const val DEEP_FISH: Short = 9896
    const val GRILLED_FISH: Short = 9897
    const val FISH_STEW: Short = 9898
    const val FISH_OIL: Short = 9899
    const val WOOD_SWORD: Short = 9900
    const val STONE_SPEAR: Short = 9901
    const val IRON_SWORD: Short = 9902
    const val IRON_SPEAR: Short = 9903
    const val STEEL_SWORD: Short = 9904
    const val STEEL_HAMMER: Short = 9905
    const val PADDED_ARMOR: Short = 9910
    const val IRON_ARMOR: Short = 9911
    const val STEEL_ARMOR: Short = 9912
    const val SHIELD: Short = 9913
    const val BARREL: Short = 9920
    const val RECEIVER: Short = 9921
    const val MAGAZINE: Short = 9922
    const val CASE: Short = 9923
    const val POWDER: Short = 9924
    const val STEEL_PLATE: Short = 9925
    const val RIVETS: Short = 9926
    const val BLANK: Short = 9927

    data class Melee(val type: String, val damage: Int, val reach: Double,
        val arc: Double, val recovery: Float, val targets: Int)
    val melee = mapOf(
        WOOD_SWORD to Melee("sword", 6, 2.8, .78, .52f, 2),
        STONE_SPEAR to Melee("spear", 8, 3.9, .96, .80f, 1),
        IRON_SWORD to Melee("sword", 13, 3.0, .78, .50f, 2),
        IRON_SPEAR to Melee("spear", 16, 4.2, .96, .78f, 1),
        STEEL_SWORD to Melee("sword", 22, 3.1, .75, .48f, 2),
        STEEL_HAMMER to Melee("hammer", 34, 2.9, .68, 1.05f, 3))
    fun armor(id: Short?) = when(id) { PADDED_ARMOR -> .18f; IRON_ARMOR -> .34f; STEEL_ARMOR -> .48f; else -> 0f }
    fun isEquipment(id: Short) = id in melee || id in PADDED_ARMOR..SHIELD
    fun isGardenItem(id: Short) = id in ROD..FISH_OIL
    fun healing(id: Short) = when(id) { GRILLED_FISH -> 12; FISH_STEW -> 22; else -> 0 }

    fun registerTextures() {
        for(id in 9890..9927) for(face in listOf("top", "side")) {
            BlockRegistry.registerGeneratedTexture("expedition:$id:$face") { size ->
                val b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
                val c=Canvas(b); val p=Paint()
                fun rect(x: Int,y: Int,w: Int,h: Int,color: Long) {
                    p.color=color.toInt(); c.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),p)
                }
                val metal=if(id==9900) 0xFFB28A50 else if(id==9901) 0xFF888F91 else if(id>=9904) 0xFF83CBD1 else 0xFFD0D5D3
                when(id.toShort()) {
                    FORGE, ANVIL -> {
                        rect(0,0,32,32,0xFF414B55); rect(2,2,28,4,0xFF7F939A)
                        if(id==FORGE.toInt()) { rect(5,13,22,15,0xFF211F29);rect(7,23,18,4,0xFFFFB14A);rect(10,19,12,5,0xFFD55432) }
                        else { rect(3,11,26,6,0xFFA7BAC1);rect(12,17,8,9,0xFF738B99);rect(7,26,18,4,0xFFB3C3C5) }
                    }
                    ROD -> { rect(19,2,3,25,0xFFC39154);rect(7,3,14,1,0xFFE4DCC5);rect(7,4,1,19,0xFFD1DCC7);rect(5,22,5,5,0xFFE96843) }
                    in RIVER_FISH..DEEP_FISH, GRILLED_FISH -> {
                        val color=when(id.toShort()) { CAVE_FISH -> 0xFF9ABAE9; DEEP_FISH -> 0xFFDFBA68; GRILLED_FISH -> 0xFFD4864D; else -> 0xFF70C1AA }
                        rect(7,10,20,11,color);rect(3,7,5,17,color);rect(15,7,8,3,color);rect(24,12,2,2,0xFF142B35)
                    }
                    in WOOD_SWORD..STEEL_HAMMER -> {
                        rect(14,21,4,10,0xFF986A41)
                        if(id==STEEL_HAMMER.toInt()) {rect(5,3,22,12,metal);rect(14,12,4,10,0xFF986A41)}
                        else if(melee[id.toShort()]?.type=="spear") {rect(15,7,2,22,0xFFBB915B);rect(12,2,8,8,metal)}
                        else {rect(13,2,6,20,metal);rect(8,21,16,3,0xFFE3C16F)}
                    }
                    in PADDED_ARMOR..STEEL_ARMOR -> { val color=if(id==9910) 0xFFB58D62 else if(id==9911) 0xFFABB5B8 else 0xFF73BCC9
                        rect(8,7,16,22,color);rect(2,7,6,10,color);rect(24,7,6,10,color);rect(12,5,8,5,0xFF35404C);rect(15,11,2,17,0xFF667B80) }
                    SHIELD -> {rect(4,4,24,18,0xFF8A644B);rect(7,22,18,5,0xFF8A644B);rect(12,27,8,3,0xFFB9C7C4);rect(14,4,4,22,0xFFB9C7C4)}
                    else -> {rect(6,9,20,15,0xFF88ADB3);rect(8,7,16,3,0xFFD4DDCB);rect(9,12,14,3,0xFF4B6571);rect(8+id%10,18,5,4,0xFFE8BA64)}
                }
                Bitmap.createScaledBitmap(b,size,size,false).also { if(it!==b) b.recycle() }
            }
        }
    }
}
