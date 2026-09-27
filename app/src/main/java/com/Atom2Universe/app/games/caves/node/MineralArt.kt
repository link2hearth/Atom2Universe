package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.Atom2Universe.app.games.caves.node.MineralItems.Form

/** Pixel silhouettes, shared across tiers to keep the texture array bounded. */
internal object MineralArt {
    fun textureKey(metal: Int, form: Form, tier: Int) = "mineral:$metal:${form.ordinal}:$tier"
    fun oreVariantKey(base: String, variant: Int) = if (variant == 0) base else "$base:variant$variant"
    fun register() {
        for(metal in MineralItems.metals.indices) for(slot in ForgedEquipment.Slot.entries) {
            BlockRegistry.registerGeneratedTexture("armor:$metal:${slot.ordinal}") { size -> armor(metal,slot,size) }
        }
        for (metal in 0..16) for (form in Form.entries) for (tier in 1..if(form == Form.ORE) 3 else 1) {
            for (variant in 0 until if(form == Form.ORE) MineralOrePixels.VARIANTS else 1) {
                BlockRegistry.registerGeneratedTexture(oreVariantKey(textureKey(metal,form,tier),variant)) { size ->
                    texture(metal,form,tier,size,variant)
                }
            }
        }
    }
    private fun armor(metal: Int, slot: ForgedEquipment.Slot, size: Int): Bitmap {
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); val paint=Paint().apply { isAntiAlias=false }
        val color=MineralItems.metals[metal].color
        fun rect(x: Int,y: Int,w: Int,h: Int,light: Float) {
            paint.color=android.graphics.Color.rgb((((color shr 16) and 255)*light).toInt().coerceIn(0,255),
                (((color shr 8) and 255)*light).toInt().coerceIn(0,255),((color and 255)*light).toInt().coerceIn(0,255))
            canvas.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),paint)
        }
        when(slot) {
            ForgedEquipment.Slot.HELMET -> {
                rect(7,7,18,20,.45f);rect(10,4,12,5,.65f);rect(8,8,16,11,1f)
                rect(10,6,12,3,1.3f);rect(6,18,6,10,.8f);rect(20,18,6,10,.8f)
                rect(10,16,12,4,.28f);rect(15,14,2,11,1.25f)
            }
            ForgedEquipment.Slot.CHEST -> {
                rect(8,8,16,21,.5f);rect(2,7,7,10,.7f);rect(23,7,7,10,.7f)
                rect(9,9,14,17,1f);rect(12,7,8,4,.35f);rect(10,12,3,11,1.3f)
                rect(9,22,14,2,.7f);rect(9,27,14,2,1.2f);rect(3,8,5,2,1.3f);rect(24,8,5,2,1.3f)
            }
            ForgedEquipment.Slot.LEGS -> {
                rect(7,5,18,9,.55f);rect(8,7,16,5,1.1f)
                for(x in intArrayOf(8,18)) { rect(x,12,6,18,.5f);rect(x,12,4,16,1f);rect(x,20,5,4,1.25f) }
            }
            ForgedEquipment.Slot.BOOTS -> {
                for(x in intArrayOf(4,18)) { rect(x+2,6,8,21,.5f);rect(x+3,8,6,13,1f)
                    rect(x,22,10,6,.8f);rect(x,28,11,2,.4f);rect(x+3,9,2,10,1.3f);rect(x+1,23,7,2,1.25f) }
            }
        }
        return if(size==32) bitmap else Bitmap.createScaledBitmap(bitmap,size,size,false).also { bitmap.recycle() }
    }
    fun texture(metal: Int, form: Form, tier: Int, size: Int, variant: Int = 0): Bitmap {
        val color = MineralItems.metals.getOrNull(metal)?.color ?: when(metal) {
            9 -> 0x8EAEBE; 10 -> 0xDF9251; 11 -> 0x252A34
            12 -> 0xCC3349; 13 -> 0x50C878; 14 -> 0x88E8F0; 15 -> 0xAA3930; else -> 0xD5E8FA
        }
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        fun shade(c: Int, light: Float, alpha: Int=255): Int = (alpha shl 24) or
            (((((c shr 16) and 255)*light).toInt().coerceIn(0,255)) shl 16) or
            (((((c shr 8) and 255)*light).toInt().coerceIn(0,255)) shl 8) or
            (((c and 255)*light).toInt().coerceIn(0,255))
        if (form == Form.ORE) {
            val pixels=MineralOrePixels.pixels(metal,color,tier,variant)
            bitmap.setPixels(pixels,0,32,0,0,32,32)
        } else {
            val c=Canvas(bitmap);val p=Paint().apply { isAntiAlias=false }
            fun rect(x: Int,y: Int,w: Int,h: Int, tint: Int) { p.color=tint;c.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),p) }
            val dark=shade(color,.5f);val mid=shade(color,.85f);val bright=shade(color,1.3f)
            val wood=0xFF987044.toInt()
            when(form) {
                Form.INGOT -> { rect(5,12,22,12,dark);rect(7,9,18,12,mid);rect(9,9,14,3,bright) }
                Form.MOLTEN -> { rect(5,9,22,17,dark);rect(7,11,18,12,mid);rect(10,12,10,3,bright) }
                Form.DUST -> for(i in 0..19) rect(6+i*7%20,9+i*11%17,2,2,if(i%3==0) bright else mid)
                Form.RAW -> if(metal==16) {
                    for(y in 7..26) {
                        val half=if(y<=12) 6+(y-7) else (26-y)*11/14
                        rect(16-half,y,half*2+1,1,dark)
                        if(half>1) rect(17-half,y,half*2-1,1,if(y<12) bright else mid)
                        if(y>=12 && half>1) rect(16,y,1,1,bright)
                    }
                    rect(7,12,19,1,bright)
                } else { rect(7,8,17,17,dark);rect(10,6,12,16,mid);rect(7,13,18,8,mid);rect(11,8,7,4,bright) }
                Form.PLATE,Form.ARMOR_PART -> { rect(5,7,22,21,dark);rect(6,7,19,18,mid);rect(7,8,17,2,bright)
                    if(form==Form.ARMOR_PART) {rect(7,14,17,2,dark);rect(7,20,17,2,dark)} }
                Form.HEAD,Form.PICK -> { if(form==Form.PICK) rect(14,12,3,18,wood)
                    rect(5,7,22,4,dark);rect(6,6,19,3,bright);rect(4,10,4,5,mid);rect(24,9,4,6,mid) }
                Form.BLADE,Form.SWORD -> {rect(13,3,6,19,mid);rect(13,4,2,17,bright)
                    if(form==Form.SWORD) {rect(9,21,14,3,dark);rect(14,24,4,6,wood)} }
                Form.SPEAR -> {rect(15,10,3,21,wood);rect(12,5,8,8,mid);rect(14,2,4,12,bright)}
                Form.HAMMER -> {rect(14,12,4,19,wood);rect(5,5,23,10,mid);rect(6,5,21,2,bright);rect(5,13,23,3,dark)}
                Form.ARMOR -> {rect(8,7,16,22,mid);rect(2,7,6,10,dark);rect(24,7,6,10,dark);rect(12,5,8,5,0xFF303741.toInt());rect(10,11,2,14,bright)}
                else -> Unit
            }
        }
        return if(size==32) bitmap else Bitmap.createScaledBitmap(bitmap,size,size,false).also { bitmap.recycle() }
    }
}
