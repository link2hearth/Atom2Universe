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
    const val COGWHEEL: Short = 9808
    const val LARGE_COGWHEEL: Short = 9809
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
    const val GEARBOX: Short = 9821
    const val CRANK: Short = 9822
    const val LARGE_WATERWHEEL: Short = 9823
    const val WINDMILL: Short = 9824
    const val SAIL: Short = 9825
    /** Blows on a forge beside it: the faster it turns, the hotter the fire. */
    const val BELLOWS: Short = 9826
    /** Empty wooden brick mould; filled with clay it becomes [MOLD_WET]. */
    const val BRICK_MOLD: Short = 9827
    /** Clay in its mould, set down to dry in the sun; a day of sun turns it into [MOLD_DRY]. */
    const val MOLD_WET: Short = 9828
    const val MOLD_DRY: Short = 9829
    /** Sits on a forge and melts metal with its heat; turned by a gearbox, it tips and pours. */
    const val CRUCIBLE: Short = 9865
    /** Takes what a crucible pours and gives the chosen piece, hot. */
    const val CAST_MOLD: Short = 9866
    const val MOLTEN_COPPER: Short = 9867
    const val MOLTEN_IRON: Short = 9868
    const val MOLTEN_STEEL: Short = 9869
    const val MOLTEN_GOLD: Short = 9873
    const val MOLTEN_SILVER: Short = 9874
    /** Cast in a mould, then shaped by the press. */
    const val IRON_PLATE: Short = 9875
    /** Metal in fusion: it lives in crucibles and moulds only, never in a bag. */
    val MOLTEN = setOf(MOLTEN_COPPER, MOLTEN_IRON, MOLTEN_STEEL, MOLTEN_GOLD, MOLTEN_SILVER)
    /** What each molten metal sets into when it leaves its crucible or mould. */
    val SOLID = mapOf(MOLTEN_COPPER to 3115.toShort(), MOLTEN_IRON to 3114.toShort(), MOLTEN_STEEL to 9859.toShort(),
        MOLTEN_GOLD to 3116.toShort(), MOLTEN_SILVER to 3117.toShort())

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

    fun isContainer(id: Short) = id in CHEST..COMPOSTER && id != WATERWHEEL && id != SHAFT || id in PRESS..VAT || id==TROUGH || id==ExpeditionItems.FORGE || id==HOPPER || id==CRUCIBLE || id==CAST_MOLD
    fun healing(id: Short): Int = when (id) {
        BREAD -> 7; SALAD -> 6; STEW -> 14; BAKED_POTATO -> 6
        BERRY_TART -> 12; RATATOUILLE -> 16; TRAVEL_RATION -> 10
        CHEESE -> 9; OMELETTE -> 16; PANCAKE -> 18; CREAM_SOUP -> 24
        else -> maxOf(ExpeditionItems.healing(id), KitchenItems.healing(id))
    }
    fun isGardenItem(id: Short) = id in FLOUR..MORTAR || id in WOOL..CHEESE || id in OMELETTE..CREAM_SOUP || id == SHEARS || id == CHARM || KitchenItems.isItem(id)

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
        for (id in 9800..9882) for (face in if (id == 9805) listOf("top", "side", "front", "bottom") else listOf("top", "side")) {
            BlockRegistry.registerGeneratedTexture("frontier:$id:$face") { size -> texture(id, face, size) }
        }
    }
    /** Cooking stove: an iron range on a brick base; oven door with a window in front, two burners on top. */
    private fun cookerFace(face: String, rect: (Int, Int, Int, Int, Long) -> Unit, c: Canvas, p: Paint) {
        val iron = 0xFF3E4448; val trim = 0xFFB8894F; val brick = 0xFF8C4A34; val mortar = 0xFF5E3526
        fun bricks() {
            rect(0, 24, 32, 8, brick)
            rect(0, 27, 32, 1, mortar); rect(0, 31, 32, 1, mortar)
            for (x in intArrayOf(5, 15, 25)) rect(x, 24, 1, 3, mortar)
            for (x in intArrayOf(0, 10, 20, 30)) rect(x, 28, 1, 3, mortar)
        }
        when (face) {
            "front" -> {
                rect(0, 0, 32, 24, iron); rect(0, 0, 32, 2, trim); bricks()
                rect(4, 5, 24, 16, 0xFF2A2E31); rect(6, 7, 20, 12, 0xFF1A1C1E)       // oven door
                rect(8, 9, 16, 8, 0xFF3A2A20); rect(10, 13, 12, 4, 0xFFD55432); rect(12, 14, 8, 2, 0xFFFFB14A)
                rect(6, 3, 20, 2, 0xFFD6D0C0)                                       // handle
                for (x in intArrayOf(5, 25)) rect(x, 21, 2, 2, trim)                 // knobs
            }
            "top" -> {
                rect(0, 0, 32, 32, iron); rect(0, 0, 32, 2, trim); rect(0, 30, 32, 2, trim)
                for ((cx, cy) in listOf(10f to 11f, 22f to 21f)) {
                    p.color = 0xFF1A1C1E.toInt(); c.drawCircle(cx, cy, 7f, p)
                    p.color = 0xFF5A6066.toInt(); c.drawCircle(cx, cy, 5f, p)
                    p.color = 0xFF1A1C1E.toInt(); c.drawCircle(cx, cy, 3f, p)
                }
                rect(22, 4, 6, 6, 0xFF5A6066)                                        // flue
            }
            "side" -> {
                rect(0, 0, 32, 24, iron); rect(0, 0, 32, 2, trim); bricks()
                rect(4, 6, 24, 1, 0xFF4E5559); rect(4, 14, 24, 1, 0xFF4E5559)
                for (x in intArrayOf(6, 13, 20)) rect(x, 8, 4, 2, 0xFF6E767C)          // hooks
            }
            else -> { rect(0, 0, 32, 32, brick); for (y in 0..3) rect(0, y * 8, 32, 1, mortar) }
        }
    }

    /** Crucible, casting mould, molten metals and hot pieces. */
    private fun foundryItem(id: Int, face: String, rect: (Int, Int, Int, Int, Long) -> Unit, c: Canvas, p: Paint) {
        val glow = when (id) { 9867 -> 0xFFE8783A; 9868 -> 0xFFF2A23C; 9869 -> 0xFFFFE7A6; 9873 -> 0xFFFFD24A; 9874 -> 0xFFF4F1E8; else -> 0xFFE8683A }
        when (id) {
            9865 -> {
                // Crucible: a fired clay pot with its pivot pins.
                rect(0, 0, 32, 32, 0x00000000)
                rect(6, 6, 20, 22, 0xFF8C4A34); rect(8, 8, 16, 18, 0xFFA85B40)
                if (face == "top") { rect(8, 8, 16, 16, 0xFF2A1A14); rect(11, 11, 10, 10, 0xFFE8783A) }
                rect(2, 14, 4, 4, 0xFF5E656B); rect(26, 14, 4, 4, 0xFF5E656B); rect(6, 4, 20, 3, 0xFF6E3A28)
            }
            9866 -> {
                // Casting mould: a stone block with the shape hollowed in its top.
                rect(0, 0, 32, 32, 0xFF6F7A73); rect(2, 2, 28, 28, 0xFF84908A)
                if (face == "top") { rect(7, 11, 18, 10, 0xFF2E3336); rect(9, 13, 14, 6, 0xFF3E4448) }
                else { rect(0, 20, 32, 2, 0xFF4E5852) }
            }
            in 9867..9874 -> {
                // Molten metal: a glowing pool in a ladle.
                p.color = 0xFF3E4448.toInt(); c.drawCircle(16f, 18f, 12f, p)
                p.color = glow.toInt(); c.drawCircle(16f, 18f, 9f, p)
                p.color = 0xFFFFF4C8.toInt(); c.drawCircle(13f, 15f, 3f, p)
                rect(26, 6, 3, 12, 0xFF5E656B)
            }
            // Iron plate: a flat grey sheet with a bevelled edge.
            else -> { rect(4, 8, 24, 16, 0xFF4E5559); rect(5, 9, 22, 14, 0xFF8A9299); rect(6, 10, 12, 2, 0xFFC4CBD0); rect(5, 22, 22, 1, 0xFF5E656B) }
        }
    }

    private fun texture(id: Int, face: String, size: Int): Bitmap {
        val b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint().apply { isAntiAlias = false }
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Long) {
            p.color = color.toInt(); c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), p)
        }
        if (id == 9805) cookerFace(face, ::rect, c, p)
        else if (id in 9865..9875) foundryItem(id, face, ::rect, c, p)
        else if (id in listOf(9808, 9809, 9821, 9822, 9823, 9824, 9825, 9826, 9827, 9828, 9829)) {
            // Mechanical parts: wood, a darker hub and the mark of their role.
            rect(0,0,32,32,0xFF8E6A40); rect(2,2,28,28,0xFFB48A56)
            when (id) {
                9808, 9809 -> {
                    p.color=0xFF6E4E2C.toInt(); c.drawCircle(16f,16f,if(id==9809) 14f else 10f,p)
                    p.color=0xFFC9A26B.toInt(); c.drawCircle(16f,16f,if(id==9809) 11f else 7f,p)
                    val teeth=if(id==9809) 12 else 8; val r=if(id==9809) 14f else 10f
                    for(i in 0 until teeth) {
                        val a=i*2*Math.PI/teeth
                        rect((16+kotlin.math.cos(a)*r).toInt()-2,(16+kotlin.math.sin(a)*r).toInt()-2,4,4,0xFF6E4E2C)
                    }
                    rect(13,13,6,6,0xFF4A3520)
                }
                9823 -> {
                    // Large water wheel: rim, spokes and paddles.
                    p.color=0xFF6E4E2C.toInt(); c.drawCircle(16f,16f,14f,p)
                    p.color=0xFF8EB8C4.toInt(); c.drawCircle(16f,16f,11f,p)
                    rect(15,3,2,26,0xFF6E4E2C); rect(3,15,26,2,0xFF6E4E2C)
                    for(i in 0 until 8) { val a=i*Math.PI/4; rect((16+kotlin.math.cos(a)*13).toInt()-2,(16+kotlin.math.sin(a)*13).toInt()-2,5,5,0xFFC9A26B) }
                    rect(13,13,6,6,0xFF4A3520)
                }
                9824 -> {
                    // Windmill head: a round bearing plate with its hub.
                    p.color=0xFF6F7A73.toInt(); c.drawCircle(16f,16f,12f,p)
                    p.color=0xFF4E5852.toInt(); c.drawCircle(16f,16f,8f,p)
                    rect(12,12,8,8,0xFFC4A06A); rect(14,14,4,4,0xFF4A3520)
                }
                9825 -> {
                    // Sail: cloth stretched on a wooden frame, with a cross brace.
                    rect(0,0,32,32,0xFF6E4E2C); rect(3,3,26,26,0xFFE8E0C7)
                    rect(15,3,2,26,0xFFB48A56); rect(3,15,26,2,0xFFB48A56)
                }
                9826 -> {
                    // Bellows: two boards and the folded leather between them, a nozzle in front.
                    rect(0,0,32,32,0xFF8E6A40); rect(3,6,26,5,0xFFC4A06A); rect(3,21,26,5,0xFFC4A06A)
                    rect(5,11,22,10,0xFF7A5236); for(i in 0..2) rect(5,13+i*3,22,1,0xFF5A3A24)
                    rect(27,14,5,4,0xFF5E656B)
                }
                9827, 9828, 9829 -> if(face=="side" && id!=9827) {
                    // The mould's edge: planks with nail heads, the clay or bricks just showing above.
                    rect(0,0,32,32,0xFF8E6A40); rect(0,24,32,8,0xFF6E4E2C); for(x in intArrayOf(3,15,27)) rect(x,27,2,2,0xFF3A2A18)
                    rect(0,20,32,4,if(id==9828) 0xFF9C7B63 else 0xFFB5613F)
                } else {
                    // Brick mould: a wooden frame with four cells, empty, wet clay or dry bricks.
                    rect(0,0,32,32,0xFF6E4E2C)
                    val fill=when(id) { 9828 -> 0xFF9C7B63; 9829 -> 0xFFB5613F; else -> 0xFF4A3520 }
                    for(cx in 0..1) for(cy in 0..1) rect(3+cx*14,3+cy*14,12,12,fill)
                    if(id==9829) for(cx in 0..1) for(cy in 0..1) rect(4+cx*14,4+cy*14,10,2,0xFFD08A62)
                }
                9821 -> { rect(4,4,24,24,0xFF6F7A73); rect(7,7,18,18,0xFF4E5852); rect(13,2,6,28,0xFFC4A06A); rect(2,13,28,6,0xFFC4A06A) }
                else -> { rect(14,4,4,24,0xFFC4A06A); rect(14,4,12,4,0xFF6E4E2C); rect(22,4,4,10,0xFF4A3520) }
            }
        } else if (toolIndex(id.toShort())>=0) {
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
