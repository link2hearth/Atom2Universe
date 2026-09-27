package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Color
import com.Atom2Universe.app.games.caves.world.CaveFurnitureModel

/** Furniture atlas recipes. The book spines share exact coordinates with the 3D shelf. */
internal object CaveFurnitureTextures {
    private val parts=listOf("wood","planks","books","barrel_side","barrel_end")
    fun register() {
        for(species in listOf("oak","fir")) for(part in parts) {
            val key="${species}_$part"
            BlockRegistry.registerGeneratedTexture("cave_furniture:$key") { size -> texture(key,size) }
        }
    }
    private fun texture(key: String,size: Int): Bitmap {
        val fir=key.startsWith("fir_")
        val part=key.substringAfter('_')
        val base=if(fir) 0x715741 else 0x947958
        val light=if(fir) 0x997C58 else 0xAF9166
        val dark=if(fir) 0x443C31 else 0x67583F
        val soft=if(fir) 0x806347 else 0xA0855E
        val pixels=IntArray(32*32) { Color.rgb(base shr 16,base shr 8 and 255,base and 255) }
        fun paint(c: Int,x: Int,y: Int,w: Int,h: Int) {
            val color=Color.rgb(c shr 16,c shr 8 and 255,c and 255)
            for(py in maxOf(0,y) until minOf(32,y+h)) for(px in maxOf(0,x) until minOf(32,x+w)) pixels[py*32+px]=color
        }
        fun wood(horizontal: Boolean) {
            for(i in 0 until 32 step 8) {
                if(horizontal) { paint(dark,0,i,32,1);paint(light,0,i+1,32,1);paint(soft,0,i+2,32,3) }
                else { paint(dark,i,0,1,32);paint(light,i+1,0,1,32);paint(soft,i+2,0,3,32) }
            }
            if(horizontal) {paint(dark,6,6,7,1);paint(soft,19,21,8,1)}
            else {paint(dark,6,6,1,9);paint(soft,20,19,1,8)}
        }
        when(part) {
            "wood" -> wood(false)
            "planks" -> wood(true)
            "books" -> {
                // Leftmost three pixels are reserved for the exposed page edges.
                paint(0xD4C9A5,0,0,3,32)
                for(y in 1..31 step 3) paint(0xAFA486,0,y,3,1)
                val covers=intArrayOf(0x8E554E,0x507D75,0x8F7B45,0x69768E,0x806581,0x657A54)
                for(book in CaveFurnitureModel.books) {
                    val x=book.x;val y=32-book.y-book.height
                    val color=covers[(book.color+if(fir) 2 else 0)%covers.size]
                    paint(color,x,y,book.width,book.height)
                    fun tint(delta: Int) = ((color shr 16 and 255)+delta).coerceIn(0,255).shl(16) or
                        ((color shr 8 and 255)+delta).coerceIn(0,255).shl(8) or ((color and 255)+delta).coerceIn(0,255)
                    paint(tint(-28),x+book.width-1,y,1,book.height)
                    paint(tint(22),x,y,1,book.height)
                    paint(0xC6AC73,x,y+2,book.width-1,1)
                    paint(0xC6AC73,x,y+book.height-2,book.width-1,1)
                    if(book.width>=3) paint(0xE0D1A7,x+1,y+book.height/2,book.width-2,2)
                }
            }
            "barrel_side" -> {
                wood(false)
                for(y in listOf(5,24)) {
                    paint(0x354248,0,y,32,3);paint(0x7D8987,0,y,32,1);paint(0x53656A,0,y+1,32,1)
                    for(x in listOf(4,20)) paint(0xC4BEA5,x,y+1,1,1)
                }
                // A dark wooden bung is set between the hoops.
                paint(dark,14,14,5,5);paint(light,15,14,3,1);paint(soft,15,15,3,3)
            }
            "barrel_end" -> {
                wood(true)
                // Rim-adjacent end grain; the centre remains readable as inset planks.
                for(i in listOf(5,6,25,26)) {paint(dark,5,i,22,1);paint(dark,i,5,1,22)}
                paint(light,7,7,18,1);paint(light,7,7,1,18)
                paint(dark,17,14,4,4);paint(soft,18,15,2,2)
            }
            else -> error("Unknown cave furniture texture: $key")
        }
        val source=Bitmap.createBitmap(pixels,32,32,Bitmap.Config.ARGB_8888)
        if(size==32) return source
        return Bitmap.createScaledBitmap(source,size,size,false).also { source.recycle() }
    }
}
