package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import com.Atom2Universe.app.games.caves.node.MineralItems.Form

/** Pixel silhouettes, shared across tiers to keep the texture array bounded. */
internal object MineralArt {
    fun textureKey(metal: Int, form: Form, tier: Int) = "mineral:$metal:${form.ordinal}:$tier"
    fun register() {
        for (metal in 0..10) for (form in Form.entries) for (tier in 1..if(form == Form.ORE) 3 else 1) {
            BlockRegistry.registerGeneratedTexture(textureKey(metal,form,tier)) { size -> texture(metal,form,tier,size) }
        }
    }
    fun texture(metal: Int, form: Form, tier: Int, size: Int): Bitmap {
        val color = MineralItems.metals.getOrNull(metal)?.color ?: if(metal == 9) 0x8EAEBE else 0xDF9251
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        fun shade(c: Int, light: Float, alpha: Int=255): Int = (alpha shl 24) or
            (((((c shr 16) and 255)*light).toInt().coerceIn(0,255)) shl 16) or
            (((((c shr 8) and 255)*light).toInt().coerceIn(0,255)) shl 8) or
            (((c and 255)*light).toInt().coerceIn(0,255))
        if (form == Form.ORE) {
            val pixels=IntArray(1024)
            val centers=arrayOf(7 to 8,23 to 7,17 to 20,5 to 26,27 to 27)
            for(y in 0..31) for(x in 0..31) {
                val noise=((x*73428767 xor y*912931) ushr 5) and 15
                var inside=false;var edge=false
                for((cx,cy) in centers) {
                    val dx=x-cx;val dy=y-cy;val ax=abs(dx);val ay=abs(dy)
                    val hit=when(metal) {
                        0 -> ax+ay<=4 && !(dx< -1 && dy>1)
                        1 -> abs(dx*2+dy+(y/3%2))<=1 && ay<6
                        2 -> dx*dx+dy*dy<=12
                        3 -> ax<=3 && ay<=3 && (ax+ay<5 || dx==dy)
                        4 -> abs(dx+dy)<=1 && ay<7
                        5 -> ax<=3 && ay<=3 && (ax>=2 || ay>=2)
                        6 -> abs(ay-ax)<=1 && ay<=4
                        7 -> ax<=5 && ay<=2 && dx+dy<5
                        8 -> ax+ay<=4 && (ax<=1 || ay<=1 || ax==ay)
                        else -> (abs(dx+(dy/3))<=1 && ay<6) || (dy in 0..3 && dx==dy)
                    }
                    if(hit) { inside=true;edge=dx+dy<0;break }
                }
                val fissure=if((x+2*y)%17==0) .78f else 1f
                // Alpha 254 encodes only the mineral inclusion for the low-emission shader.
                pixels[y*32+x]=if(inside) shade(color,if(edge) 1.22f else .78f+(tier-1)*.06f,254)
                    else shade(0x454950,(.88f+noise*.012f)*fissure)
            }
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
                Form.RAW -> { rect(7,8,17,17,dark);rect(10,6,12,16,mid);rect(7,13,18,8,mid);rect(11,8,7,4,bright) }
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
