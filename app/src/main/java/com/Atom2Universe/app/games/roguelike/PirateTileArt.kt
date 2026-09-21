package com.Atom2Universe.app.games.roguelike

import kotlin.math.*

/** 48px maritime tiles using the deck/cabin palette and props of DungeonPirateBackdrop. */
internal class PirateTileArt {
    companion object { const val SIZE = 48 }
    private var pixels = IntArray(SIZE * SIZE)
    private val ink = 0xFF34434C.toInt()
    private val wood = 0xFF967950.toInt()
    private val pale = 0xFFC0AC83.toInt()
    private val dark = 0xFF705441.toInt()
    private val iron = 0xFF4A5B5E.toInt()
    private val metal = 0xFF81999C.toInt()
    private val gold = 0xFFCEB581.toInt()
    private val cream = 0xFFE1D4AF.toInt()
    private val rose = 0xFFAC6773.toInt()
    private fun hash(x:Int,y:Int,s:Int=0):Int {var n=x*374761393+y*668265263+s*1442695041;n=(n xor(n ushr 13))*1274126177;return(n xor(n ushr 16)) and Int.MAX_VALUE}
    private fun dot(x:Int,y:Int,c:Int){if(x in 0..47&&y in 0..47)pixels[y*48+x]=c}
    private fun rect(x:Int,y:Int,w:Int,h:Int,c:Int){for(yy in y.coerceAtLeast(0) until(y+h).coerceAtMost(48))for(xx in x.coerceAtLeast(0) until(x+w).coerceAtMost(48))dot(xx,yy,c)}
    private fun oval(x:Int,y:Int,rx:Int,ry:Int,c:Int){for(yy in -ry..ry)for(xx in -rx..rx)if(xx*xx*ry*ry+yy*yy*rx*rx<=rx*rx*ry*ry)dot(x+xx,y+yy,c)}
    private fun line(x0:Int,y0:Int,x1:Int,y1:Int,c:Int){var x=x0;var y=y0;val dx=abs(x1-x0);val dy=-abs(y1-y0);val sx=if(x0<x1)1 else -1;val sy=if(y0<y1)1 else -1;var e=dx+dy
        while(true){dot(x,y,c);if(x==x1&&y==y1)break;val e2=2*e;if(e2>=dy){e+=dy;x+=sx};if(e2<=dx){e+=dx;y+=sy}}}

    fun render(tx:Int,ty:Int,wall:Boolean,stairs:Boolean,cabin:Boolean,neighbours:Int):IntArray {
        pixels=IntArray(SIZE*SIZE)
        val seed=hash(tx,ty)
        floor(tx,ty,cabin,cabin && !wall,neighbours)
        if(wall && !cabin && neighbours and 240!=0) railing(neighbours)
        else if(wall){
            oval(25,38,19,5,if(cabin)0xFF4D393B.toInt() else dark)
            if(cabin)when(seed%6){0->desk();1->window();2->books();3->chest();4->bottles();else->chair()}
            else when(seed%6){0->barrels();1->crate();2->cannon();3->mast();4->sail();else->capstan()}
        }else if(stairs)hatch(cabin)
        else when(seed%13){
            0->rope(24,28,9)
            1-> {rect(9,15,29,20,dark);rect(10,16,27,17,ink);for(x in 12..35 step 4)rect(x,17,2,15,wood);rect(10,23,27,2,pale)}
            2-> {line(12,31,32,21,if(cabin)dark else pale);line(14,33,34,23,dark)}
            else->Unit
        }
        return pixels
    }
    private fun floor(tx:Int,ty:Int,cabin:Boolean,rug:Boolean,mask:Int){
        rect(0,0,48,48,if(cabin)0xFF4D393B.toInt() else 0xFF574638.toInt())
        for(row in 0..3)for(col in -1..1){
            val x=col*32+row%2*16;val y=row*12
            val seed=hash(tx*2+col,ty*4+row)
            val tone=if(cabin)when(seed%3){0->0xFF85634C.toInt();1->0xFF735746.toInt();else->0xFF7D5D4C.toInt()}
                else when(seed%3){0->wood;1->0xFFA88A5D.toInt();else->0xFF9F8157.toInt()}
            rect(x+1,y+1,30,10,tone);rect(x+2,y+1,28,1,if(cabin)wood else pale)
            line(x+5,y+5,x+20,y+5,if(cabin)0xFF89624A.toInt() else 0xFFB19667.toInt())
            dot(x+3,y+8,dark);dot(x+28,y+3,dark)
            if(seed%5==0){oval(x+22,y+7,3,1,dark);dot(x+22,y+7,tone)}
        }
        if(rug){
            val left=if(mask and 8!=0)5 else 0;val top=if(mask and 1!=0)5 else 0
            val right=if(mask and 2!=0)43 else 48;val bottom=if(mask and 4!=0)43 else 48
            rect(left,top,right-left,bottom-top,0xFF976673.toInt())
            if(mask and 8!=0)rect(left+1,top,1,bottom-top,gold)
            if(mask and 2!=0)rect(right-2,top,1,bottom-top,gold)
            if(mask and 1!=0)rect(left,top+1,right-left,1,gold)
            if(mask and 4!=0)rect(left,bottom-2,right-left,1,gold)
            for(y in 12..47 step 24)for(x in 12..47 step 24){
                line(x-4,y,x,y+4,rose);line(x,y+4,x+4,y,rose)
                line(x-4,y,x,y-4,rose);line(x,y-4,x+4,y,rose);dot(x,y,pale)
            }
        }
    }    private fun barrel(x:Int,y:Int){
        oval(x+9,y+13,9,13,dark);rect(x+1,y+3,16,21,wood)
        for(i in 0..3)rect(x+3+i*4,y+3,1,21,pale)
        oval(x+9,y+3,8,3,pale);oval(x+9,y+3,6,2,wood)
        rect(x+1,y+8,17,3,iron);rect(x+1,y+20,17,3,iron)
        dot(x+3,y+8,metal);dot(x+15,y+20,metal)
    }
    private fun barrels(){barrel(4,12);barrel(25,8);rope(23,37,5)}
    private fun crate(){
        rect(5,10,33,29,dark);rect(7,12,28,24,wood)
        for(x in 10..34 step 7)rect(x,13,1,22,dark)
        for(i in 0..2){line(8+i,13,33,34-i,pale);line(8+i,34,33,13+i,pale)}
        rect(5,10,33,3,pale);rect(5,36,33,3,iron)
        for(x in intArrayOf(7,34)){dot(x,11,iron);dot(x,37,metal)}
        rect(29,7,11,3,cream);rect(32,8,4,1,wood)
    }
    private fun cannon(){
        rect(9,28,32,8,0xFF9C7154.toInt());rect(12,27,25,2,pale)
        for(x in intArrayOf(13,33)){oval(x,36,5,5,ink);oval(x,36,2,2,metal);dot(x,36,gold)}
        rect(17,12,16,19,iron);rect(19,10,12,21,metal)
        rect(19,10,3,20,0xFF9AABAA.toInt());rect(28,13,3,17,iron)
        oval(25,11,9,4,iron);oval(25,10,8,3,metal);oval(25,10,5,2,ink)
        rect(15,26,20,3,ink);rect(22,29,6,3,iron)
        oval(39,27,3,3,ink);dot(38,26,metal)
    }
    private fun rope(x:Int,y:Int,r:Int){
        for(i in r downTo 3 step 3){oval(x,y,i,(i*.6).roundToInt(),pale);oval(x,y,i-1,(i*.6).roundToInt()-1,dark)}
        line(x+r,y,x+r+4,y+7,pale);dot(x+2,y-2,cream)
    }
    private fun mast(){
        oval(24,34,12,6,iron);rect(20,3,9,33,dark);rect(21,3,3,31,pale)
        for(y in intArrayOf(12,15,28,31)){rect(19,y,11,2,gold);dot(20,y,cream)}
        line(21,8,4,34,pale);line(28,8,43,33,pale)
        for(i in 0..3){val y=15+i*5;line(17-i*3,y,30+i*3,y,pale)}
        rope(35,38,6)
    }
    private fun sail(){
        rect(6,7,36,3,dark);rect(7,7,33,1,pale)
        for(x in 9..39){val drop=24+(sin((x-9)/30.0*PI)*5).toInt();rect(x,10,1,drop-10,if(x%7==0)0xFFB9AB86.toInt() else cream)}
        rect(21,27,5,12,dark);rect(22,28,2,10,pale)
        line(9,10,4,39,pale);line(39,10,43,39,pale)
        rect(26,30,12,8,ink);rect(30,32,4,3,cream);dot(30,33,ink);dot(33,33,ink)
    }
    private fun capstan(){
        oval(24,34,15,6,iron);oval(24,31,13,5,wood);rect(19,19,10,13,dark)
        oval(24,19,10,4,pale);oval(24,19,6,2,wood)
        rect(5,18,38,3,dark);rect(6,18,36,1,pale)
        line(14,10,34,28,pale);line(14,28,34,10,pale);oval(24,19,3,2,iron)
        rope(11,37,5)
    }
    private fun desk(){
        rect(8,16,32,20,dark);rect(10,32,4,9,dark);rect(34,32,4,9,dark)
        rect(6,14,36,17,wood);rect(6,14,36,2,pale)
        rect(10,17,21,11,0xFFE0C99B.toInt());rect(10,17,2,11,cream)
        rect(16,20,6,3,0xFF899975.toInt());rect(19,23,5,2,0xFF899975.toInt())
        line(25,19,28,25,0xFF9F7156.toInt());line(25,25,28,19,0xFF9F7156.toInt())
        oval(35,23,4,3,gold);dot(35,22,metal);rect(32,17,6,1,cream)
    }
    private fun window(){
        rect(4,4,40,35,0xFF62463F.toInt());rect(8,7,32,28,gold)
        rect(10,9,28,23,0xFF80A9B6.toInt());rect(10,21,28,11,0xFF397489.toInt())
        rect(12,24,9,1,0xFF84B7BC.toInt());rect(26,28,10,1,metal)
        rect(23,9,2,23,gold);rect(10,19,28,2,gold)
        for(x in intArrayOf(5,37)){rect(x,8,6,28,0xFF723F51.toInt());rect(x+1,9,2,25,rose);rect(x,26,6,2,gold)}
        rect(4,5,40,2,pale);rect(7,35,34,3,pale)
    }
    private fun books(){
        rect(6,7,36,32,0xFF4D393B.toInt());rect(7,7,2,31,pale);rect(39,7,2,31,wood)
        for(row in 0..1){val y=10+row*14
            for(i in 0..5){val c=when(i%3){0->0xFF618780.toInt();1->0xFFB88E61.toInt();else->0xFF9A6066.toInt()};rect(11+i*4,y+i%2,3,9-i%2,c);dot(12+i*4,y+3,gold)}
            rect(8,y+10,32,2,pale)
        }
    }
    private fun chest(){
        rect(8,23,32,14,dark);oval(24,22,16,8,dark);rect(9,18,30,10,wood)
        rect(9,18,30,2,pale);rect(9,29,30,2,ink)
        for(x in intArrayOf(13,32)){rect(x,17,3,20,gold);dot(x+1,34,cream)}
        rect(22,27,5,7,gold);dot(24,30,ink);rect(10,37,4,2,ink);rect(34,37,4,2,ink)
    }
    private fun bottles(){
        rect(7,19,35,19,dark);rect(6,18,37,3,pale)
        for(i in 0..2){val x=10+i*10;rect(x+2,7,3,5,gold);rect(x,11,7,10,0xFF4C8575.toInt());rect(x+1,11,1,6,0xFFAFBF90.toInt());rect(x+1,16,5,3,cream)}
        rect(10,25,29,1,wood);rect(10,31,29,1,wood);dot(24,28,gold)
    }
    private fun chair(){
        rect(14,9,22,23,dark);rect(16,11,18,16,rose);rect(19,12,12,12,0xFF8E5665.toInt())
        rect(12,28,27,6,wood);rect(15,28,21,4,rose)
        rect(14,34,4,7,dark);rect(33,34,4,7,dark)
        for(x in intArrayOf(12,36)){rect(x,21,3,10,pale);dot(x,20,gold)}
        rect(14,8,22,2,gold);dot(24,18,gold)
    }
    private fun railing(mask:Int){
        // The four outer map edges show sea beyond the ship's continuous gunwale.
        rect(0,0,48,48,0xFF438996.toInt())
        for(i in 0..6)rect(i*7%42,5+i*6,6,1,0xFF6BAAB0.toInt())
        fun horizontal(y:Int){rect(0,y,48,5,dark);rect(0,y,48,2,pale);for(x in 4..47 step 12){rect(x,y-4,4,13,wood);rect(x,y-4,1,12,pale)}}
        fun vertical(x:Int){rect(x,0,5,48,dark);rect(x,0,2,48,pale);for(y in 4..47 step 12){rect(x-4,y,13,4,wood);rect(x-4,y,12,1,pale)}}
        if(mask and 16!=0)horizontal(37)
        if(mask and 64!=0)horizontal(6)
        if(mask and 128!=0)vertical(37)
        if(mask and 32!=0)vertical(6)
    }
    private fun hatch(cabin:Boolean){
        rect(5,6,38,37,ink);rect(3,4,42,4,if(cabin)gold else pale)
        rect(4,8,3,35,dark);rect(41,8,3,35,dark)
        for(i in 0..5){val x=9+i*2;val y=11+i*5;rect(x,y,30-i*4,3,wood);rect(x,y,30-i*4,1,pale)}
        line(19,39,24,43,gold);line(24,43,29,39,gold)
    }
}
