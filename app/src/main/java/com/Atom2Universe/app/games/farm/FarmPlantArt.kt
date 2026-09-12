package com.Atom2Universe.app.games.farm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import kotlin.math.*

/** Native port of the three validated My Farm HTML studies. No WebView or asset PNG. */
object FarmPlantArt {
    private val supported = setOf(FarmCrop.RADISH, FarmCrop.CARROT, FarmCrop.LETTUCE,
        FarmCrop.POTATO, FarmCrop.CAULIFLOWER, FarmCrop.ZUCCHINI, FarmCrop.PEPPER, FarmCrop.EGGPLANT)
    fun supports(crop: FarmCrop) = crop in supported
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private data class Key(val crop: FarmCrop, val variant: Int, val growth: Int)
    // Bound the cache by bytes: planting times differ, so growth snapshots cannot grow forever.
    private val cache = object : LruCache<Key, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
    }
    fun draw(canvas: Canvas, crop: FarmCrop, variant: Int, growth: Float, target: RectF,
             windTime: Float? = null) {
        if (target.width() <= 0f || target.height() <= 0f) return
        val key = Key(crop, Math.floorMod(variant, 4), (growth.coerceIn(0f, 1f) * 256).roundToInt())
        val bitmap = cache[key] ?: run {
            // Lettuce leaves extend below the root line; retain that transparent padding.
            val small = Bitmap.createBitmap(80, 88, Bitmap.Config.ARGB_8888)
            PlantPainter(Canvas(small), key.variant, key.growth / 256.0).draw(crop)
            val enlarged = Bitmap.createScaledBitmap(small, 240, 264, false)
            small.recycle()
            cache.put(key, enlarged)
            enlarged
        }
        val artHeight = when (crop) {
            FarmCrop.POTATO -> 64f
            FarmCrop.PEPPER, FarmCrop.EGGPLANT -> 62f
            FarmCrop.LETTUCE -> 38f
            else -> 46f
        }
        val scale = min(target.width() / 72f, target.height() / artHeight)
        val bottomPadding = when (crop) {
            FarmCrop.LETTUCE -> 14f
            FarmCrop.ZUCCHINI -> 10f
            else -> 5f
        }
        val base = target.bottom - bottomPadding * scale
        canvas.save()
        canvas.translate(target.centerX(), base)
        if (windTime != null) {
            val gust = .5 + .5 * cos(2 * PI * (target.centerX() / 2.0 - 16 * windTime) / 320)
            val bend = 1.1 * gust * gust * growth.coerceIn(0f, 1f)
            canvas.skew((-bend / artHeight).toFloat(), 0f)
        }
        // Art pixels remain large; only the transform samples the 3x image for gentle movement.
        paint.isFilterBitmap = windTime != null
        canvas.drawBitmap(bitmap, null, RectF(-40f * scale, -72f * scale, 40f * scale, 16f * scale), paint)
        canvas.restore()
    }
}

/** Integer raster primitives match the HTML's scanline polygons instead of drawing vectors at zoom. */
private class PlantPainter(private val canvas: Canvas, variant: Int, private val g: Double) {
    private val paint = Paint().apply { isAntiAlias = false }
    private val s = 100 + variant * 17
    private val colors = HashMap<String, Int>()
    private val pal = arrayOf(
        arrayOf("#21573d", "#398949", "#69b654", "#b7df87"),
        arrayOf("#205c47", "#3c9460", "#79bf76", "#c5e8a0"),
        arrayOf("#305e35", "#4c9444", "#88bd55", "#d0e695"),
        arrayOf("#20553f", "#318551", "#68b36a", "#b4df98")
    )[variant]
    private val x = 40.0
    private val y = 72.0
    private fun color(c: String) = colors.getOrPut(c) { Color.parseColor(c) }
    private fun hash(k: Int, seed: Int = s): Double {
        var n = (seed + 13) * (k + 713) xor 0x68bc21eb
        n = (n xor (n ushr 16)) * 0x45d9f3b
        return ((n xor (n ushr 16)).toLong() and 0xffffffffL) / 4294967296.0
    }
    private fun smooth(a: Double, b: Double): Double {
        val p = ((g - a) / (b - a)).coerceIn(0.0, 1.0)
        return p * p * (3 - 2 * p)
    }
    private fun rect(x: Double, y: Double, w: Double, h: Double, c: String) {
        if (w <= 0 || h <= 0) return
        val left = floor(x + .5).toFloat(); val top = floor(y + .5).toFloat()
        paint.color = color(c)
        canvas.drawRect(left, top, left + max(1.0, floor(w + .5)).toFloat(),
            top + max(1.0, floor(h + .5)).toFloat(), paint)
    }
    private fun poly(points: List<Pair<Double, Double>>, c: String) {
        for (row in ceil(points.minOf { it.second }).toInt()..floor(points.maxOf { it.second }).toInt()) {
            val hits = ArrayList<Double>()
            points.indices.forEach { i ->
                val a = points[i]; val b = points[(i + 1) % points.size]
                if ((a.second <= row && b.second > row) || (b.second <= row && a.second > row))
                    hits.add(a.first + (row - a.second) * (b.first - a.first) / (b.second - a.second))
            }
            hits.sort()
            for (i in 0 until hits.size - 1 step 2)
                rect(ceil(hits[i]), row.toDouble(), max(1.0, floor(hits[i + 1]) - ceil(hits[i])), 1.0, c)
        }
    }
    private fun line(x: Double, y: Double, xx: Double, yy: Double, w: Double, c: String) {
        val n = ceil(max(abs(xx - x), abs(yy - y))).toInt()
        for (i in 0..n) rect(x + (xx - x) * i / max(1, n), y + (yy - y) * i / max(1, n), w, w, c)
    }
    private fun oval(x: Double, y: Double, rx: Double, ry: Double, c: String) {
        if (rx < .4 || ry < .4) return
        for (j in -floor(ry).toInt()..floor(ry).toInt()) {
            val w = sqrt(max(0.0, 1 - j * j / (ry * ry))) * rx
            rect(x - w, y + j, w * 2, 1.0, c)
        }
    }
    private fun mix(a: String, b: String, t: Double): String {
        val aa = color(a); val bb = color(b)
        val r = (Color.red(aa) * (1 - t) + Color.red(bb) * t).roundToInt()
        val gg = (Color.green(aa) * (1 - t) + Color.green(bb) * t).roundToInt()
        val bl = (Color.blue(aa) * (1 - t) + Color.blue(bb) * t).roundToInt()
        return "#%02x%02x%02x".format(r, gg, bl)
    }
    private fun leaf(x: Double, y: Double, dx: Double, dy: Double, width: Double) {
        val len = hypot(dx, dy); if (len < 1) return
        val nx = -dy / len * width; val ny = dx / len * width
        fun p(u: Double, v: Double) = (x + dx * u + nx * v) to (y + dy * u + ny * v)
        poly(listOf(p(0.0,0.0),p(.25,-.65),p(.38,-.52),p(.49,-1.0),p(.62,-.65),p(.76,-.72),
            p(1.0,0.0),p(.74,.65),p(.62,.52),p(.45,.9),p(.3,.5)),pal[0])
        poly(listOf(p(.1,0.0),p(.3,-.4),p(.5,-.75),p(.74,-.4),p(.95,0.0),p(.55,.37),p(.3,.32)),pal[1])
        poly(listOf(p(.15,0.0),p(.52,-.55),p(.85,-.2),p(.6,0.0)),pal[2])
        val a=p(.08,0.0);val b=p(.88,0.0);line(a.first,a.second,b.first,b.second,1.0,pal[2])
    }
    fun draw(crop: FarmCrop) {
        if (g < .055) { oval(x,y,7.0,2.0,"#5c4128");rect(x-1,y-1,3.0,1.0,"#e1ba79") }
        val a=smooth(.025,.19)
        if(a>0){line(x,y,x,y-9*a,1.0,pal[0]);leaf(x,y-9*a+1,-7*a,-3*a,2*a);leaf(x,y-9*a,6*a,-4*a,2*a)}
        when(crop){
            FarmCrop.RADISH -> radish()
            FarmCrop.CARROT -> carrot()
            FarmCrop.LETTUCE -> lettuce()
            FarmCrop.POTATO -> potato()
            FarmCrop.CAULIFLOWER -> cauliflower()
            FarmCrop.ZUCCHINI -> zucchini()
            FarmCrop.PEPPER -> fruitPlant(false)
            FarmCrop.EGGPLANT -> fruitPlant(true)
            else -> Unit
        }
    }
    private fun radish(){
        val a=smooth(.35,.95);val w=(6+hash(41)*3)*a;val h=(6+hash(42)*3)*a
        if(a>0){
            oval(x,y-2,w,h,"#983e58");oval(x-.7,y-3,w*.86,h*.82,arrayOf("#e45f85","#ee7093","#d95d8b","#ed7c91")[s%4])
            oval(x-w*.32,y-5,w*.42,h*.45,"#ffafbf");rect(x-w*.42,y-5,2*a,1.0,"#ffd3d7")
            oval(x,y+2,w*.58,2*a,"#fff0d4");rect(x-2,y+3,4.0,2.0,"#a47b55")
        }
        for(i in 0..4){val b=smooth(.06+i*.035,.45+i*.075);if(b==0.0)continue
            val angle=-2.65+i*.53+(hash(i+71)-.5)*.14;val length=(23+hash(i+81)*9)*b
            val dx=cos(angle)*length;val dy=sin(angle)*length
            line(x,y-5,x+dx*.48,y-5+dy*.48,1.0,pal[1]);leaf(x+dx*.24,y-5+dy*.24,dx*.8,dy*.8,(5+hash(i+91)*2)*b)
        }
    }
    private fun carrot(){
        val crown=smooth(.4,.98);val w=(6+hash(3)*2)*crown
        if(w>.5){oval(x,y-1,w,4*crown,"#ac6636");oval(x-.5,y-2,w*.85,4*crown,arrayOf("#f6a34e","#ef9850","#f4ae58","#ed9147")[s%4]);oval(x-2,y-3,w*.4,2*crown,"#ffd18a");rect(x+w*.3,y-1,2.0,1.0,"#ce773c")}
        for(i in 0..6){val a=smooth(.07+i*.025,.48+i*.044);if(a==0.0)continue
            val angle=-2.55+i*.31+(hash(10+i)-.5)*.12;val length=(22+hash(20+i)*13)*a
            val dx=cos(angle)*length;val dy=sin(angle)*length
            line(x,y-3,x+dx,y-3+dy,1.0,pal[1])
            for(j in 1..6){val u=j/7.0;val lx=x+dx*u;val ly=y-3+dy*u;val size=(1-u*.5)*a
                leaf(lx,ly,-(6+hash(j+i)*3)*size,-6*size,3*size);leaf(lx,ly,8*size,-5*size,2.8*size)}
            leaf(x+dx*.9,y-3+dy*.9,dx*.13,dy*.17,2*a)
        }
    }
    private fun lettuce(){
        val size=.88+hash(8)*.2
        for(ring in 0..2){
            val leaves=(0 until 7-ring).map{it to (it/(7-ring).toDouble()*PI*2+ring*.7+hash(2)*.4)}.sortedBy{sin(it.second)}
            for((i,angle) in leaves){val a=smooth(.13+ring*.19+i*.008,.51+ring*.23);if(a==0.0)continue
                val radius=(if(ring==0)17 else if(ring==1)11 else 6)*size*a
                val cx=x+cos(angle)*radius*.65;val cy=y-4-ring*4*a+sin(angle)*radius*.32
                val rx=radius*(.67+hash(40+i)*.1);val ry=radius*(.49+hash(60+i)*.1)
                val pts=(0..19).map{val t=it/20.0*PI*2;val r=if(it%2==1).9 else 1.05;(cx+cos(t)*rx*r) to (cy+sin(t)*ry*r)}
                poly(pts,pal[0]);poly(pts.map{(X,Y)->(cx+(X-cx)*.87) to (cy+(Y-cy)*.85-1)},pal[1])
                poly(pts.map{(X,Y)->(cx-1+(X-cx)*.68) to (cy-2+(Y-cy)*.66)},pal[2])
                line(x+(cx-x)*.45,y+(cy-y)*.45,cx,cy-ry*.45,1.0,pal[3])
                for(j in 0..2)rect(cx-rx*.4+j*rx*.35,cy-ry*.5+sin((j*2+i).toDouble())*2*a,2*a,1.0,pal[3])
            }
        }
    }
    private fun potato(){
        for(branch in 0..2){val b=smooth(.1+branch*.04,.58+branch*.06);if(b==0.0)continue
            val h=(32+hash(branch+130)*12)*b;val dx=(branch-1)*(13+hash(branch+140)*4)*b
            line(x,y,x+dx,y-h,2.0,pal[0]);line(x,y,x+dx,y-h,1.0,pal[2])
            for(k in 0..3){val u=.28+k*.2;val a=smooth(.15+k*.05+branch*.025,.49+k*.06)
                for(side in listOf(-1,1))leaf(x+dx*u,y-h*u,side*(9+hash(branch*10+k+150)*5)*a,-(4+k)*a,4.5*a)}
            leaf(x+dx,y-h,2*b,-10*b,4*b)
            val bloom=smooth(.64+branch*.035,.86+branch*.02)
            if(bloom>0)for(i in 0..1){val bx=x+dx+(if(i==1)4 else -3);val by=y-h-5-i*3
                line(x+dx,y-h,bx,by,1.0,pal[1]);val petal=if(s%2==1)"#e1c7ee" else "#fff2d4";val shade=if(s%2==1)"#b797cd" else "#d4c4a0"
                for(k in 0..4){val t=k/5.0*PI*2;oval(bx+cos(t)*2*bloom,by+sin(t)*2*bloom,2*bloom,1.7*bloom,shade);oval(bx+cos(t)*2*bloom,by+sin(t)*2*bloom-1,1.5*bloom,1.3*bloom,petal)}
                rect(bx,by,2.0,2.0,"#ebc25e");rect(bx,by,1.0,1.0,"#ffdf7e")}
        }
    }
    private fun cabbageLeaves(front:Boolean){
        val angles=if(front)listOf(-.38,-2.78)else listOf(-2.55,-2.13,-1.55,-1.02,-.52)
        angles.forEachIndexed{i,angle->val a=smooth(.08+i*.035,.55+i*.047);val length=(if(front)25.0 else 29+hash(210+i)*6)*a
            leaf(x,y-3,cos(angle)*length,sin(angle)*length,(if(front)8.0 else 8.5)*a)}
    }
    private fun cauliflower(){
        cabbageLeaves(false);val a=smooth(.4,.98);val size=(14+hash(240)*3)*a
        if(a>0){oval(x,y-9*a,size+1,size*.67,"#819065")
            val clusters=listOf(Triple(-.48,-.14,.55),Triple(.45,-.2,.56),Triple(0.0,-.6,.57),Triple(-.35,.2,.52),Triple(.38,.21,.51),Triple(0.0,-.03,.6))
            clusters.forEachIndexed{i,(dx,dy,r)->val cx=x+dx*size;val cy=y-9*a+dy*size;val radius=r*size
                oval(cx,cy+1,radius,radius*.71,"#b2ac80");oval(cx-.5,cy-1,radius*.93,radius*.68,"#d8d2a4")
                for(j in 0..9){val angle=hash(301+j,s+i*13)*PI*2;val d=sqrt(hash(321+j,s+i*13))*radius*.7
                    val px=cx+cos(angle)*d;val py=cy-1+sin(angle)*d*.58
                    oval(px,py,radius*.27+1,radius*.2+.5,"#f0eac6");rect(px-1,py-1,1.0,1.0,"#fff7dc")}}
        };cabbageLeaves(true)
    }
    private fun zucchini(){
        for(i in 0..5){val a=smooth(.07+i*.035,.5+i*.05);if(a==0.0)continue
            val angle=-2.8+i*.48;val length=(26+hash(i+51)*8)*a;val dx=cos(angle)*length;val dy=sin(angle)*length
            line(x,y-3,x+dx*.65,y+dy*.65,2*a,pal[1]);leaf(x+dx*.25,y+dy*.25,dx*.8,dy*.8,10*a)}
        for(i in 0..1){val a=smooth(.49+i*.06,.97);val side=if(i==1)1 else -1;val bx=x+side*5;val by=y-7-i*3
            if(g>.39+i*.04&&a<.15){oval(bx+side*6,by,3.0,2.0,"#ffc76c");rect(bx+side*6,by-3,2.0,5.0,"#ffe59a")}
            if(a<=0)continue
            val len=(19+hash(i+71)*6)*a;val r=(4+hash(i+81))*a
            fun p(u:Double,v:Double)=(bx+side*u*len) to (by+u*5+v*r)
            poly(listOf(p(0.0,-.6),p(.2,-1.0),p(.8,-1.0),p(1.0,-.5),p(1.0,.5),p(.8,1.0),p(.15,1.0),p(0.0,.5)),"#28583c")
            poly(listOf(p(.08,-.5),p(.25,-.8),p(.83,-.7),p(.95,-.2),p(.85,.6),p(.15,.5)),arrayOf("#468653","#53965c","#3a7e4b","#5a9860")[s%4])
            val aa=p(.17,-.4);val bb=p(.82,-.4);line(aa.first,aa.second,bb.first,bb.second,1.0,"#b0cc77")
            val cc=p(.13,.25);val dd=p(.8,.3);line(cc.first,cc.second,dd.first,dd.second,1.0,"#80b367");rect(bx-1,by-2,3.0,3.0,pal[1])
        }
    }
    private fun fruitPlant(egg:Boolean){
        val a=smooth(.1,.6);if(a==0.0)return
        val h=(39+hash(31)*10)*a;val lean=(hash(32)-.5)*6
        line(x,y,x+lean,y-h,2.0,pal[0]);line(x,y,x+lean,y-h,1.0,pal[2])
        val sites=ArrayList<Triple<Double,Double,Int>>()
        for(i in 0..5){val b=smooth(.12+i*.045,.43+i*.055);if(b==0.0)continue
            val side=if(i%2==1)1 else -1;val u=.26+i*.12;val bx=x+lean*u;val by=y-h*u;val dx=side*(12+hash(i+91)*5)*b;val dy=-6*b
            line(bx,by,bx+dx,by+dy,1.0,pal[1]);leaf(bx+dx*.55,by+dy*.55,side*(if(egg)14 else 11)*b,-9*b,(if(egg)7 else 5)*b)
            if(i<3)sites.add(Triple(bx+dx*.85,by+dy+2,i))}
        leaf(x+lean,y-h,5*a,-11*a,5*a)
        for((fx,fy,i)in sites){val b=smooth(.53+i*.055,.9+i*.025);val bloom=smooth(.39+i*.04,.49+i*.04)
            if(bloom==0.0)continue
            if(b<.12){oval(fx,fy,3*bloom,2*bloom,if(egg)"#d2afe7" else "#fff0cb");rect(fx-1,fy-1,2.0,2.0,"#f4d578");continue}
            val ripe=smooth(.73,.98);val w=(if(egg)5 else 6)*b*(.9+hash(i+141)*.2);val fh=(if(egg)14 else 10)*b*(.85+hash(i+151)*.25)
            val c=(if(egg)arrayOf("#775294","#865ba3","#68448a","#925ba1")else arrayOf("#e76955","#edb64e","#ef9356","#de635b"))[s%4]
            if(egg){oval(fx+1,fy+fh*.55,w,fh*.65,"#443252");oval(fx,fy+fh*.5,w*.82,fh*.58,mix("#82a365",c,ripe))
                oval(fx-w*.25,fy+fh*.35,w*.34,fh*.36,mix("#b9cf8b","#ba91cb",ripe));line(fx-w*.32,fy+fh*.14,fx-w*.32,fy+fh*.4,1.0,"#e2c5e3")
            }else for(j in -1..1){val cx=fx+j*w*.55;val cy=fy+fh*.5+abs(j)*b
                oval(cx,cy,w*.6,fh*.65,mix("#376c40","#9d4b3e",ripe));oval(cx-.5,cy-1,w*.48,fh*.55,mix("#6bad58",c,ripe))
                if(j<1)line(cx-1,cy-fh*.27,cx-1,cy+fh*.13,1.0,mix("#b2d583","#ffda94",ripe))}
            leaf(fx,fy,-4*b,-2*b,2*b);leaf(fx,fy,4*b,-2*b,2*b);rect(fx,fy-3*b,1.0,4*b,pal[0])
        }
    }
}
