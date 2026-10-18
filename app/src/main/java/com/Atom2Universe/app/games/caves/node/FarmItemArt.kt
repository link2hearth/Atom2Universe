package com.Atom2Universe.app.games.caves.node

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.Atom2Universe.app.games.farm.FarmCrop

/** Standalone harvest silhouettes, also printed on seed packets; no plant bitmap is reused. */
internal object FarmItemArt {
    fun texture(crop: FarmCrop, seeds: Boolean, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint().apply { isAntiAlias = false }
        fun color(hex: Int) { p.color = 0xFF000000.toInt() or hex }
        fun rect(x: Float, y: Float, w: Float, h: Float, hex: Int) {
            color(hex); c.drawRect(x, y, x+w, y+h, p)
        }
        fun oval(x: Float, y: Float, w: Float, h: Float, hex: Int) {
            color(hex); c.drawOval(x, y, x+w, y+h, p)
        }
        fun line(x: Float, y: Float, xx: Float, yy: Float, width: Float, hex: Int) {
            color(hex); p.strokeWidth = width; c.drawLine(x,y,xx,yy,p)
        }
        fun leaves(x: Float, y: Float) {
            line(x,y,x-8,y-9,3f,0x3A8247); line(x,y,x,y-12,3f,0x68AB50)
            line(x,y,x+8,y-8,3f,0x8DC764)
        }
        if (seeds) {
            rect(7f,3f,34f,42f,0x765038); rect(9f,5f,30f,38f,0xD6B77B)
            rect(9f,5f,30f,6f,0xF4DFA7); rect(11f,37f,26f,4f,0xA18050)
            c.save(); c.translate(10f,11f); c.scale(.58f,.58f)
        }
        when (crop) {
            FarmCrop.WHEAT -> {
                for (x in listOf(15f,24f,33f)) {
                    line(x,43f,x,10f,2f,0xAC7D32)
                    for (y in listOf(10f,16f,22f,28f)) {
                        oval(x-6,y,6f,9f,0xD99F37); oval(x,y-2,6f,9f,0xF3CD62)
                    }
                }
            }
            FarmCrop.CORN -> {
                oval(14f,5f,22f,35f,0xB88024)
                for (y in 0..5) for (x in 0..2) rect(17f+x*5,8f+y*5,4f,4f,if ((x+y)%2==0) 0xF4CE4E else 0xECAF36)
                line(14f,22f,24f,43f,8f,0x46834C); line(38f,20f,27f,43f,7f,0x88B65B)
            }
            FarmCrop.TOMATO -> {
                oval(6f,13f,36f,29f,0xA53732); oval(8f,13f,31f,25f,0xE75842)
                oval(12f,17f,9f,6f,0xFFAC79); leaves(24f,16f)
            }
            FarmCrop.LETTUCE -> {
                for (i in 0..3) {
                    oval(5f+i*3,15f-i*2,34f-i*5,28f-i*3,if(i%2==0) 0x43884C else 0xA3D776)
                }
                line(24f,40f,24f,20f,2f,0xD7EDAC)
            }
            FarmCrop.CARROT -> {
                leaves(26f,13f); line(24f,18f,17f,40f,9f,0xD2772C)
                line(26f,17f,19f,36f,7f,0xF3A441); line(17f,37f,14f,44f,3f,0xF3A441)
                line(19f,25f,25f,27f,2f,0xAB5A26)
            }
            FarmCrop.POTATO -> {
                oval(7f,15f,26f,26f,0xAC814D); oval(21f,6f,22f,29f,0xD2AC70)
                for (v in listOf(13f to 24f,23f to 32f,30f to 13f,35f to 24f)) rect(v.first,v.second,3f,2f,0x805C39)
            }
            FarmCrop.ONION -> {
                oval(9f,18f,30f,25f,0xAE783E); oval(12f,18f,24f,22f,0xE1B778)
                line(24f,25f,26f,5f,4f,0x76984C); line(19f,24f,17f,38f,2f,0xF2D29C)
                line(29f,25f,31f,38f,2f,0x9E6735); line(23f,41f,20f,46f,1f,0xC2A572)
            }
            FarmCrop.STRAWBERRY -> {
                oval(9f,13f,30f,21f,0xD74648); oval(15f,24f,19f,17f,0xE85952)
                leaves(24f,15f)
                for (y in 0..2) for(x in 0..2) rect(17f+x*6,21f+y*6,2f,2f,0xFFE0A0)
            }
            FarmCrop.RADISH -> {
                leaves(25f,17f); oval(12f,16f,25f,23f,0xDE5479)
                oval(15f,31f,18f,9f,0xF2DFCC); line(24f,38f,20f,45f,2f,0xECDCC6)
            }
            FarmCrop.PEPPER -> {
                for (x in listOf(10f,18f,26f)) oval(x,15f,14f,27f,if(x==18f) 0xF07745 else 0xCB4D35)
                line(24f,18f,25f,6f,4f,0x568149); line(25f,6f,32f,7f,3f,0x74A753)
            }
            FarmCrop.EGGPLANT -> {
                oval(10f,16f,25f,28f,0x543466); oval(14f,17f,16f,24f,0x88559D)
                oval(17f,21f,4f,13f,0xC595CB); leaves(25f,18f)
            }
            FarmCrop.BROCCOLI, FarmCrop.CAULIFLOWER -> {
                line(24f,39f,24f,19f,9f,0x79A66B)
                val green = crop == FarmCrop.BROCCOLI
                for (v in listOf(5f to 14f,17f to 6f,28f to 13f,15f to 18f)) {
                    oval(v.first,v.second,17f,18f,if(green) 0x347449 else 0xB7B48D)
                    oval(v.first+2,v.second,13f,13f,if(green) 0x71AE65 else 0xF0E5BF)
                }
                if (!green) { line(6f,29f,20f,43f,6f,0x52804D); line(42f,29f,28f,43f,6f,0x78A663) }
            }
            FarmCrop.PEAS -> {
                oval(9f,5f,25f,39f,0x397951); oval(14f,9f,16f,30f,0xABC969)
                for (y in listOf(11f,20f,29f)) oval(17f,y,10f,10f,0x6AA74C)
            }
            FarmCrop.CHILI -> {
                line(29f,12f,25f,29f,10f,0xCE3F38); line(25f,29f,14f,39f,7f,0xE9553E)
                line(14f,39f,6f,40f,3f,0xE9553E); line(29f,13f,31f,5f,4f,0x548744)
                line(27f,16f,25f,24f,2f,0xFF9670)
            }
            FarmCrop.LEEK -> {
                line(20f,42f,25f,22f,10f,0xDFE2BB)
                for (x in listOf(13f,24f,36f)) line(25f,23f,x,4f,6f,if(x==24f) 0x7AB17B else 0x377553)
            }
            FarmCrop.RASPBERRY, FarmCrop.BLUEBERRY -> {
                val raspberry = crop == FarmCrop.RASPBERRY
                for (y in 0..2) for(x in 0..(2-y)) {
                    val xx=9f+x*12+y*5; val yy=13f+y*10
                    oval(xx,yy,14f,14f,if(raspberry) 0xA93D63 else 0x3F4D83)
                    oval(xx+2,yy+1,9f,8f,if(raspberry) 0xE77C97 else 0x8297C4)
                }
                leaves(25f,13f)
            }
            FarmCrop.PINEAPPLE -> {
                oval(12f,17f,25f,29f,0xBA843C); oval(14f,18f,20f,25f,0xE8B44E)
                for(y in 0..3) for(x in 0..2) rect(17f+x*5,22f+y*5,3f,3f,0xA37534)
                leaves(25f,20f); line(25f,19f,24f,2f,4f,0x478252)
            }
            else -> error("Unsupported farm harvest: $crop")
        }
        if (seeds) c.restore()
        if (size == 48) return bitmap
        return Bitmap.createScaledBitmap(bitmap,size,size,false).also { bitmap.recycle() }
    }
}
