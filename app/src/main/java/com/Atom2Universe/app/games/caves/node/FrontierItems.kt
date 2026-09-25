package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Stable IDs below the dynamic weapon range. */
internal object FrontierItems {
    const val CHEST: Short = 9800
    const val CACHE: Short = 9801
    const val MILL: Short = 9802
    const val WATERWHEEL: Short = 9803
    const val SHAFT: Short = 9804
    const val COOKER: Short = 9805
    const val COMPOSTER: Short = 9806
    const val HOPPER: Short = 9807
    const val FLOUR: Short = 9810
    const val BREAD: Short = 9811
    const val SALAD: Short = 9812
    const val STEW: Short = 9813
    const val BAKED_POTATO: Short = 9814
    const val BERRY_TART: Short = 9815
    const val RATATOUILLE: Short = 9816
    const val TRAVEL_RATION: Short = 9817
    const val COMPOST: Short = 9818
    const val MORTAR: Short = 9819
    const val GEAR: Short = 9820

    const val PRESS: Short = 9840
    const val CRUSHER: Short = 9841
    const val LOOM: Short = 9842
    const val KILN: Short = 9843
    const val VAT: Short = 9844
    const val SHEARS: Short = 9846
    const val TROUGH: Short = 9847
    const val TOKEN: Short = 9848
    const val WOOL: Short = 9850
    const val MILK: Short = 9851
    const val EGG: Short = 9852
    const val TRUFFLE: Short = 9853
    const val CHEESE: Short = 9854
    const val CLOTH: Short = 9855
    const val PLATE: Short = 9856
    const val IRON_DUST: Short = 9857
    const val COPPER_DUST: Short = 9858
    const val STEEL: Short = 9859
    const val GEODE: Short = 9860
    const val LAMP: Short = 9861
    const val OMELETTE: Short = 9862
    const val PANCAKE: Short = 9863
    const val CREAM_SOUP: Short = 9864
    const val HEARTH: Short = 9870
    const val CHARM: Short = 9871
    const val MARKET_BELL: Short = 9872

    fun isContainer(id: Short) = id in CHEST..COMPOSTER && id != WATERWHEEL && id != SHAFT || id in PRESS..VAT || id==TROUGH || id==ExpeditionItems.FORGE
    fun healing(id: Short): Int = when (id) {
        BREAD -> 7; SALAD -> 6; STEW -> 14; BAKED_POTATO -> 6
        BERRY_TART -> 12; RATATOUILLE -> 16; TRAVEL_RATION -> 10
        CHEESE -> 9; OMELETTE -> 16; PANCAKE -> 18; CREAM_SOUP -> 24
        else -> ExpeditionItems.healing(id)
    }
    fun isGardenItem(id: Short) = id in FLOUR..MORTAR || id in WOOL..CHEESE || id in OMELETTE..CREAM_SOUP || id == SHEARS || id == CHARM

    fun toolIndex(id: Short?): Int = when(id?.toInt()) {
        in 9830..9838 -> id!!.toInt()-9830
        in 9880..9882 -> id!!.toInt()-9880+9
        else -> -1
    }
    /** Tools speed up the matching material; no arbitrary lock on hand gathering. */
    fun miningSpeed(tool: Short?, target: BlockDef?): Float {
        val n = toolIndex(tool)
        if (n !in 0..11 || target == null) return 1f
        val matches = when (n % 3) {
            0 -> target.creativeTab in setOf("stone", "ores") || target.harvestCategory in setOf("ore", "fractured_stone")
            1 -> "logs" in target.tags || "planks" in target.tags || target.creativeTab == "wood"
            else -> "soil" in target.tags || target.name in setOf("sand", "redsand", "gravel", "clay", "snow")
        }
        return if (matches) floatArrayOf(1.8f, 3.2f, 5f, 6.5f)[n / 3] else 1f
    }

    fun registerTextures() {
        for (id in 9800..9882) for (face in listOf("top", "side")) {
            BlockRegistry.registerGeneratedTexture("frontier:$id:$face") { size -> texture(id, face, size) }
        }
    }
    private fun texture(id: Int, face: String, size: Int): Bitmap {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint().apply { isAntiAlias = false }
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Long) {
            p.color = color.toInt(); c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), p)
        }
        if (toolIndex(id.toShort())>=0) {
            rect(15, 9, 3, 21, 0xFFB48A56)
            val color = longArrayOf(0xFFBD935B, 0xFF909C96, 0xFFD4DED7, 0xFF83BCC3)[toolIndex(id.toShort())/3]
            when (toolIndex(id.toShort())%3) {
                0 -> { rect(5,5,22,4,color); rect(4,8,4,5,color); rect(24,8,4,5,color) }
                1 -> { rect(7,4,11,12,color); rect(5,7,4,8,color) }
                2 -> { rect(10,3,13,10,color); rect(12,13,9,3,color) }
            }
        } else if (id >= 9840) {
            val device=id in 9840..9844 || id==9847 || id in listOf(9861,9870,9872)
            val tint=when(id) { 9850,9851,9852,9855 -> 0xFFE8E0C7; 9856,9858,9848 -> 0xFFCE9975; 9860,9861,9870,9871 -> 0xFF8EDCC9; else -> 0xFF8DABB1 }
            if(device) {
                rect(0,0,32,32,0xFF536D70); rect(2,2,28,28,0xFF79938B)
                rect(3,23,26,6,0xFF394F58); rect(5,5,22,16,0xFF344C54)
                if(id==9870 || id==9861) { rect(11,7,10,14,tint); rect(14,4,4,22,0xFFD6F7DC) }
                else if(id==9872) { rect(11,5,10,14,0xFFE8BB72); rect(8,17,16,5,0xFFE8BB72); rect(14,23,4,3,0xFFCC9364) }
                else { rect(8,7,16,3,tint); rect(14,8,4,11,tint); rect(8,18,16,3,tint) }
            } else {
                rect(7,9,18,16,tint);rect(10,6,12,22,tint);rect(11,10,5,5,0xFFF0EACD)
                if(id==9851) { rect(6,11,20,3,0xFF718E97);rect(8,24,16,4,0xFF718E97) }
                if(id==9846) { rect(14,3,3,25,0xFFD7E4DB);rect(6,22,7,7,0xFF876552);rect(20,22,7,7,0xFF876552) }
                if(id==9871) { rect(13,0,6,7,0xFFAC8965);rect(13,27,6,3,0xFFAC8965) }
            }
        } else if (id >= 9810) {
            val color = when(id) { 9812 -> 0xFF87B853; 9818 -> 0xFF765339; 9820 -> 0xFFC58C54; else -> 0xFFE3B568 }
            rect(6,10,20,15,color); rect(9,7,14,3,color); rect(7,24,18,3,0xFF795636)
            for (i in 0..3) rect(9+i*4,12+(i%2)*4,2,3,0xFFFFE3A1)
            if(id in 9813..9816) { rect(4,18,24,4,0xFF698B8D); rect(7,22,18,6,0xFF4B676B) }
            if(id == 9820) { rect(13,12,6,10,0xFF493D32); rect(11,15,10,4,0xFF493D32) }
        } else {
            val stone = id == 9802 || id == 9805 || id == 9807
            rect(0,0,32,32,if(stone) 0xFF768582 else 0xFF967348)
            for (y in 0..31) for (x in 0..31) if ((x*17+y*31+x*y)%19 < 5)
                rect(x,y,1,1,if(stone) 0xFF84928C else 0xFFA78353)
            for(y in 0..3) rect(0,y*8,32,1,0xFF4A5146)
            rect(2,0,3,32,0xFF455C5C); rect(27,0,3,32,0xFF455C5C)
            if(id == 9800 || id == 9801) {
                rect(0,10,32,2,0xFF433E30)
                rect(13,8,6,9,if(id==9801) 0xFFB8D6B2 else 0xFFE4BC68)
                rect(15,12,2,3,0xFF544C37)
            } else if(id == 9806 || id == 9807 || face == "top" && id == 9805) {
                rect(6,5,20,22,0xFF364238); rect(9,8,14,16,0xFF554B32)
            } else {
                p.color=0xFFD1B885.toInt(); c.drawCircle(16f,16f,11f,p)
                p.color=0xFF4B625E.toInt(); c.drawCircle(16f,16f,8f,p)
                rect(14,4,4,24,0xFFB89A67); rect(4,14,24,4,0xFFB89A67)
                rect(13,13,6,6,0xFFE6D3A3)
            }
        }
        return if(size == 32) b else Bitmap.createScaledBitmap(b,size,size,false).also { b.recycle() }
    }
}
