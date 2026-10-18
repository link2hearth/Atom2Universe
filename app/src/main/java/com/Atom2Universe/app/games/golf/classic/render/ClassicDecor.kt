package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfLie
import com.Atom2Universe.app.games.golf.classic.core.GolfDecorTheme
import kotlin.math.*
import kotlin.random.Random

/** Small original models, assembled once into spatial batches; no images or model downloads. */
internal class ClassicDecor(private val hole:ClassicHole) {
    private val backdrop=ClassicBackdrop(hole)
    private val wood=C(.36f,.25f,.16f)
    private val timber=C(.61f,.43f,.25f)
    private val iron=C(.16f,.22f,.21f)
    private val cream=C(.91f,.87f,.72f)
    private val chunks=linkedMapOf<Pair<Int,Int>,MeshBuilder>()
    private fun at(x:Float,z:Float,build:(MeshBuilder)->Unit) {
        build(chunks.getOrPut(floor(x/48f).toInt() to floor(z/48f).toInt()){MeshBuilder()})
    }
    private fun dry(x:Float,z:Float,margin:Float=2f)=hole.lieAt(x,z)==GolfLie.ROUGH &&
        hole.greenSignedDistance(x,z)>margin+2f && hole.fairwaySignedDistance(x,z)>margin &&
        hole.hazards.none{it.signedDistance(x,z)<margin} && hypot(x-hole.tee.x,z-hole.tee.z)>7f

    fun build():List<ClassicMesh> {
        if(hole.islands.isNotEmpty()) return emptyList()
        val random=Random(hole.number*9719)
        hole.trees.forEachIndexed { i,t ->
            at(t.x,t.z) { b ->
                tree(b,t.x,t.z,t.radius,t.kind,i.toFloat())
                if(i%4==0) {
                    val x=t.x+t.radius*.85f;val z=t.z+1.8f
                    if(dry(x,z)) shrub(b,x,z,.9f,i%3)
                }
            }
        }
        // Cheap distant silhouettes: the near trees carry branches and clustered crowns.
        repeat(42) { i ->
            val side=if(i%2==0)-1f else 1f
            val x=side*(hole.width*.55f+random.nextFloat()*65f)
            val z=random.nextFloat()*(hole.length+110f)-40f
            if(hole.lieAt(x,z)!=GolfLie.WATER) at(x,z){tree(it,x,z,3.7f+random.nextFloat()*2.7f,i%3,i+51f,true)}
        }
        // Uneven woodland islands frame every viewing direction, with open meadow between them.
        val woodland=Random(hole.number*3571+83)
        repeat(36) { cluster ->
            val depth=75f+woodland.nextFloat()*210f
            val x:Float
            val z:Float
            when(cluster%4) {
                0 -> { x=backdrop.left-depth; z=backdrop.front+woodland.nextFloat()*(backdrop.back-backdrop.front) }
                1 -> { x=backdrop.right+depth; z=backdrop.front+woodland.nextFloat()*(backdrop.back-backdrop.front) }
                2 -> { x=backdrop.left+woodland.nextFloat()*(backdrop.right-backdrop.left); z=backdrop.front-depth }
                else -> { x=backdrop.left+woodland.nextFloat()*(backdrop.right-backdrop.left); z=backdrop.back+depth }
            }
            repeat(4+cluster%4) { index ->
                val angle=woodland.nextFloat()*2f*PI.toFloat()
                val spread=sqrt(woodland.nextFloat())*23f
                val tx=x+cos(angle)*spread; val tz=z+sin(angle)*spread
                val radius=3.5f+woodland.nextFloat()*3.2f
                at(tx,tz) { tree(it,tx,tz,radius,(cluster+index)%3,cluster*7f+index+103f,true) }
            }
        }
        // Quiet clusters along the walking path, away from the playable corridor.
        for(i in 0..6) {
            val z=12f+i*(hole.length-15f)/6f;val x=hole.pathX(z)+3.9f
            if(dry(x,z)) at(x,z) { b ->
                when(i%3) {
                    0 -> { bench(b,x,z);bin(b,x+2.2f,z+.3f);if(!hole.snowy)flowers(b,x-1.7f,z+.9f,1.2f,i) }
                    1 -> { rocks(b,x,z,1.0f+i*.08f);shrub(b,x+1.5f,z+1f,1.2f,i%3) }
                    else -> { planter(b,x,z);sign(b,x,z+2.2f) }
                }
            }
        }
        val teeX=hole.pathX(1f)+3.5f
        if(dry(teeX,1f)) at(teeX,1f){sign(it,teeX,1f);bench(it,teeX,4f)}
        val shelterZ=hole.length*.51f;val shelterX=hole.pathX(shelterZ)+8f
        if(dry(shelterX,shelterZ,5f)) at(shelterX,shelterZ){shelter(it,shelterX,shelterZ)}
        // Split-rail fences frame the estate; their terrain-following posts stay outside play.
        for(side in intArrayOf(-1,1)) {
            val x=side*(hole.width*.5f+4f)
            for(i in 0..4) {
                val z=hole.length*.16f+i*4.5f
                if(hole.lieAt(x,z)!=GolfLie.WATER) at(x,z){fence(it,x,z,x,z+4.5f)}
            }
        }
        // Reeds and rocks follow both banks of winding lakes, including their inner coves.
        hole.hazards.filter{it.lie==GolfLie.WATER}.forEachIndexed { index,h ->
            repeat(26) { i ->
                val a=i*2f*PI.toFloat()/26f
                var radius=1f
                var x=h.x;var z=h.z
                val course=h.watercourse
                if(course!=null) {
                    val samples=course.samples
                    val k=1+(i/2)*(samples.size-3)/12
                    val p=samples[k];val before=samples[k-1];val after=samples[k+1]
                    val length=hypot(after.x-before.x,after.z-before.z)
                    val side=if(i%2==0)1f else -1f
                    val dx=(after.z-before.z)/length*side;val dz=(before.x-after.x)/length*side
                    val limit=max(h.rx,h.rz)*3f
                    while(radius<limit && course.signedDistance(p.x+dx*radius,p.z+dz*radius)<.8f) radius+=.8f
                    x=h.x+p.x+dx*radius;z=h.z+p.z+dz*radius
                } else repeat(14) {
                    x=h.x+cos(a)*h.rx*radius;z=h.z+sin(a)*h.rz*radius
                    if(h.signedDistance(x,z)<.5f)radius+=.045f
                }
                if(dry(x,z,.25f)&&abs(x-hole.pathX(z))>2.5f) at(x,z) { b ->
                    reeds(b,x,z,i+index*31)
                    if(i%6==0)rocks(b,x+.8f,z+.5f,.65f)
                }
            }
        }
        // Meadow accents form patches rather than a regular grid of identical bushes.
        repeat(46) { i ->
            val z=random.nextFloat()*(hole.length+20f)-8f
            val side=if(i%2==0)-1f else 1f
            val x=hole.fairwayCenter(z)+side*(hole.fairwayWidth(z)*.5f+6f+random.nextFloat()*16f)
            if(dry(x,z,4f)&&abs(x-hole.pathX(z))>3f) at(x,z) { b ->
                if(hole.snowy || hole.highlands && i%3==0) rocks(b,x,z,1.1f+random.nextFloat()*.9f)
                else if(i%3==0)flowers(b,x,z,1.7f,i) else shrub(b,x,z,.45f+random.nextFloat()*.65f,i%3)
            }
        }
        return chunks.values.map{it.build()}
    }

    private fun tree(b:MeshBuilder,x:Float,z:Float,r:Float,kind:Int,seed:Float,distant:Boolean=false) {
        val y=if(distant && backdrop.outward(x,z)>0f) backdrop.surfaceHeight(x,z) else hole.heightAt(x,z)
        if(!distant) {
            b.cone(x,y,z,r*.072f,r*1.4f,wood,7,r*.035f)
            repeat(4) { i ->
                val a=i*1.57f+seed
                b.beam(P(x,y+r*(.8f+i*.12f),z),P(x+cos(a)*r*.52f,y+r*1.6f,z+sin(a)*r*.52f),r*.04f,wood,5,r*.016f)
            }
        } else b.cone(x,y,z,r*.06f,r*1.4f,wood,5,r*.025f)
        when(if(hole.snowy)1 else kind) {
            1 -> {
                // Irregular tiered conifer, with skirt shadows and warm tips.
                val levels=if(distant)3 else 5
                repeat(levels) { level ->
                    val t=level.toFloat()/levels
                    val cy=y+r*(.62f+t*1.48f);val radius=r*(1f-t*.75f)
                    val count=if(distant)7 else 10
                    val shade=if(hole.snowy && level%2==0) C(.86f,.90f,.94f)
                        else C(.18f+t*.12f,.34f+t*.14f,.24f+t*.06f)
                    for(i in 0 until count) {
                        val a=i*2f*PI.toFloat()/count;val aa=(i+1)*2f*PI.toFloat()/count
                        fun skirt(v:Float)=P(x+cos(v)*radius*(1f+.13f*sin(v*3f+seed+level)),
                            cy+sin(v*4f+seed)*r*.045f,z+sin(v)*radius*(1f+.1f*cos(v*5f+seed)))
                        val p=skirt(a);val q=skirt(aa);val tip=P(x+r*.06f*sin(seed),cy+r*.92f,z)
                        b.tri(p,q,tip,shade)
                        b.tri(P(x,cy+r*.10f,z),q,p,shade.shade(.70f))
                    }
                }
            }
            2 -> {
                val leaf=if(hole.highlands)C(.64f,.43f,.20f) else C(.42f,.55f,.24f)
                b.organic(x,y+r*1.70f,z,r*.64f,r*1.24f,r*.62f,leaf,seed,8,5)
                if(!distant) repeat(4) { i ->
                    val a=i*2.4f+seed
                    b.organic(x+cos(a)*r*.38f,y+r*(1.15f+i*.20f),z+sin(a)*r*.40f,
                        r*.47f,r*.63f,r*.43f,leaf.shade(.92f+i*.035f),seed+i,7,3)
                }
            }
            else -> {
                val leaf=if(hole.decorTheme==GolfDecorTheme.FAIRY && seed.toInt()%4==0) {
                    if(seed.toInt()%8==0)C(.90f,.65f,.76f) else C(.74f,.65f,.87f)
                } else if(hole.highlands) {
                    if(seed.toInt()%3==0)C(.66f,.39f,.18f) else C(.53f,.47f,.23f)
                } else if(seed.toInt()%4==0)C(.38f,.54f,.22f) else C(.24f,.45f,.24f)
                b.organic(x,y+r*1.73f,z,r*.86f,r*.83f,r*.86f,leaf,seed,9,4)
                val lobes=if(distant)2 else 6
                repeat(lobes) { i ->
                    val a=i*2.39996f+seed
                    b.organic(x+cos(a)*r*.61f,y+r*(1.30f+(i%3)*.23f),z+sin(a)*r*.59f,
                        r*.57f,r*(.52f+(i%2)*.11f),r*.55f,leaf.shade(.92f+(i%3)*.07f),seed+i,7,3)
                }
            }
        }
    }

    private fun shrub(b:MeshBuilder,x:Float,z:Float,r:Float,kind:Int) {
        val y=hole.heightAt(x,z)
        val c=when(kind){0->C(.28f,.43f,.21f);1->C(.38f,.49f,.26f);else->C(.24f,.40f,.32f)}
        repeat(3){i -> b.organic(x+(i-1)*r*.45f,y+r*(.36f+(i%2)*.22f),z+sin(i*2f)*r*.2f,
            r*.56f,r*.46f,r*.5f,c.shade(.93f+i*.05f),x+z+i,7,3)}
    }

    private fun rocks(b:MeshBuilder,x:Float,z:Float,size:Float) {
        val stone=if(hole.highlands)C(.46f,.49f,.50f) else C(.49f,.49f,.42f)
        repeat(3){i ->
            val xx=x+cos(i*2.4f)*size*.6f;val zz=z+sin(i*2.4f)*size*.5f
            val r=size*(.65f-i*.14f);val y=hole.heightAt(xx,zz)
            b.organic(xx,y+r*.30f,zz,r,r*.55f,r*.68f,stone.shade(.88f+i*.07f),x+z+i,7,3,.19f)
        }
    }

    private fun flowers(b:MeshBuilder,x:Float,z:Float,r:Float,seed:Int) {
        repeat(13) { i ->
            val a=i*2.39996f+seed;val reach=r*sqrt((i+.5f)/13f)
            val xx=x+cos(a)*reach;val zz=z+sin(a)*reach;val y=hole.heightAt(xx,zz)
            val h=.20f+(i%4)*.055f
            b.beam(P(xx,y,zz),P(xx+.035f,y+h,zz),.008f,C(.30f,.43f,.17f),3,.005f)
            val colour=when(seed%3){0->C(.93f,.79f,.40f);1->C(.75f,.65f,.86f);else->C(.94f,.91f,.79f)}
            b.disc(xx+.035f,y+h,zz,.065f,.065f,colour,5)
            b.disc(xx+.035f,y+h+.002f,zz,.019f,.019f,C(.72f,.48f,.18f),5)
        }
    }

    private fun reeds(b:MeshBuilder,x:Float,z:Float,seed:Int) {
        val random=Random(seed*193+hole.number)
        repeat(9) { i ->
            val xx=x+(random.nextFloat()-.5f)*1.1f;val zz=z+(random.nextFloat()-.5f)*1.1f
            if(hole.lieAt(xx,zz)==GolfLie.WATER)return@repeat
            val y=hole.heightAt(xx,zz);val h=.65f+random.nextFloat()*.65f
            val tip=P(xx+.13f,y+h,zz-.08f)
            b.beam(P(xx,y,zz),tip,.012f,C(.43f,.47f,.21f),3,.005f)
            b.tri(P(xx-.045f,y+.08f,zz),P(xx+.045f,y+.08f,zz),P(xx-.27f,y+h*.85f,zz+.11f),C(.40f,.49f,.23f))
            if(i%3==0)b.beam(P(tip.x,tip.y-.20f,tip.z),tip,.038f,C(.39f,.29f,.15f),5,.027f)
        }
    }

    private fun bench(b:MeshBuilder,x:Float,z:Float) {
        val y=hole.heightAt(x,z)
        for(side in intArrayOf(-1,1)) {
            b.box(x+side*.78f,y,z,.075f,.82f,.10f,iron)
            b.box(x+side*.78f,y,z+.47f,.075f,.46f,.10f,iron)
            b.beam(P(x+side*.78f,y+.42f,z-.03f),P(x+side*.78f,y+.42f,z+.54f),.037f,iron,4)
        }
        repeat(4){i->b.box(x,y+.45f,z+.06f+i*.13f,2.05f,.055f,.11f,timber.shade(.90f+i*.025f))}
        repeat(3){i->b.box(x,y+.59f+i*.12f,z,2.05f,.10f,.055f,timber)}
    }

    private fun bin(b:MeshBuilder,x:Float,z:Float) {
        val y=hole.heightAt(x,z)
        b.cone(x,y,z,.25f,.78f,iron,8,.29f)
        b.cone(x,y+.78f,z,.32f,.07f,wood,8,.28f)
        b.disc(x,y+.853f,z,.20f,.20f,C(.08f,.11f,.10f),10)
        repeat(8){i->val a=i*PI.toFloat()/4;b.box(x+cos(a)*.26f,y+.10f,z+sin(a)*.26f,.033f,.62f,.033f,timber)}
    }

    private fun sign(b:MeshBuilder,x:Float,z:Float) {
        val y=hole.heightAt(x,z)
        for(side in intArrayOf(-1,1))b.box(x+side*.52f,y,z,.10f,1.40f,.11f,wood)
        b.box(x,y+.80f,z,1.34f,.80f,.15f,wood)
        b.box(x,y+.87f,z-.083f,1.18f,.64f,.023f,cream)
        // A tiny routed-hole pictogram is geometry too; there is no illegible baked-in text.
        b.beam(P(x-.29f,y+.99f,z-.102f),P(x+.20f,y+1.34f,z-.102f),.045f,C(.31f,.49f,.28f),5)
        b.beam(P(x+.20f,y+1.27f,z-.112f),P(x+.20f,y+1.44f,z-.112f),.008f,iron,3)
        b.tri(P(x+.20f,y+1.44f,z-.115f),P(x+.37f,y+1.39f,z-.115f),P(x+.20f,y+1.34f,z-.115f),C(.79f,.29f,.20f))
    }

    private fun planter(b:MeshBuilder,x:Float,z:Float) {
        val y=hole.heightAt(x,z)
        b.box(x,y,z,1.6f,.34f,.85f,wood)
        repeat(5){i->b.box(x-.64f+i*.32f,y+.03f,z-.435f,.26f,.28f,.022f,timber)}
        b.box(x,y+.34f,z,1.40f,.04f,.67f,C(.25f,.22f,.14f))
        shrub(b,x,z,.9f,2)
    }

    private fun shelter(b:MeshBuilder,x:Float,z:Float) {
        val y=hole.heightAt(x,z)
        b.box(x,y-.08f,z,5.1f,.25f,4.1f,C(.58f,.56f,.46f))
        for(s in intArrayOf(-1,1))for(t in intArrayOf(-1,1))b.box(x+s*2.1f,y,z+t*1.6f,.17f,2.65f,.17f,wood)
        val roof=C(.29f,.38f,.36f)
        b.quad(P(x-2.7f,y+2.6f,z-2.2f),P(x+2.7f,y+2.6f,z-2.2f),P(x+2.7f,y+3.65f,z),P(x-2.7f,y+3.65f,z),roof)
        b.quad(P(x-2.7f,y+3.65f,z),P(x+2.7f,y+3.65f,z),P(x+2.7f,y+2.6f,z+2.2f),P(x-2.7f,y+2.6f,z+2.2f),roof)
        for(s in intArrayOf(-1,1))b.beam(P(x-2.2f,y+2.6f,z+s*1.6f),P(x+2.2f,y+2.6f,z+s*1.6f),.10f,timber,4)
        bench(b,x,z-.8f)
    }

    private fun fence(b:MeshBuilder,x:Float,z:Float,xx:Float,zz:Float) {
        val y=hole.heightAt(x,z);val yy=hole.heightAt(xx,zz)
        b.box(x,y,z,.14f,1.15f,.14f,timber)
        b.box(xx,yy,zz,.14f,1.15f,.14f,timber)
        for(h in listOf(.42f,.88f))b.beam(P(x,y+h,z),P(xx,yy+h,zz),.063f,timber,4)
    }
}
