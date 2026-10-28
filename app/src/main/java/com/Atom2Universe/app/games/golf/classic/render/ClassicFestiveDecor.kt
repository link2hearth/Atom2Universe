package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfDecorTheme
import com.Atom2Universe.app.games.golf.classic.core.GolfLie
import kotlin.math.*

/** Sparse villages baked once. Only wings, spirits and tiny lights move each frame. */
internal class ClassicFestiveDecor(private val hole:ClassicHole) {
    data class Site(val origin:P,val yaw:Float)
    val sites:List<Site> = if(hole.decorTheme==GolfDecorTheme.GARDEN || hole.islands.isNotEmpty()) emptyList()
        else (if(hole.par==3) listOf(12f,hole.length*.48f,hole.length-12f)
            else listOf(12f,hole.length*.27f,hole.length*.52f,hole.length*.76f,hole.length-12f))
            .mapIndexedNotNull { i,z -> findSite(z,if((hole.number+i)%2==0)1f else -1f) }

    private fun findSite(z:Float,preferred:Float):Site? {
        for(side in listOf(preferred,-preferred)) for(distance in 0..16) {
            val x=hole.fairwayCenter(z)+side*(hole.fairwayWidth(z)*.5f+18f+distance*5f)
            if(abs(x)>hole.width*.5f-10f) continue
            val y=hole.heightAt(x,z)
            // Check the whole village footprint, including alternate fairways and shoreline edges.
            if(listOf(-8f,0f,8f).all { dx -> listOf(-8f,0f,8f).all { dz ->
                hole.lieAt(x+dx,z+dz)==GolfLie.ROUGH && hole.fairwaySignedDistance(x+dx,z+dz)>4f &&
                    hole.greenSignedDistance(x+dx,z+dz)>5f &&
                    hole.hazards.none { it.signedDistance(x+dx,z+dz)<3f } &&
                    abs(hole.heightAt(x+dx,z+dz)-y)<.9f
            } } && hole.trees.none { hypot(x-it.x,z-it.z)<it.radius+9f })
                return Site(P(x,y,z),atan2(-side,0f))
        }
        return null
    }

    private val gold=C(1f,.78f,.30f)
    private val wood=C(.40f,.26f,.17f)
    private val pink=C(.94f,.51f,.69f)
    private val mint=C(.46f,.73f,.60f)
    private val white=C(.94f,.97f,1f)
    private val village by lazy { MeshBuilder().apply {
        when(hole.decorTheme) {
            GolfDecorTheme.FAIRY -> fairyVillage(this)
            GolfDecorTheme.HALLOWEEN -> halloweenVillage(this)
            GolfDecorTheme.CHRISTMAS -> christmasVillage(this)
            else -> Unit
        }
    } }
    private val dwelling by lazy {
        val colours = when(hole.decorTheme) {
            GolfDecorTheme.HALLOWEEN -> C(.52f,.41f,.32f) to C(.28f,.22f,.31f)
            GolfDecorTheme.CHRISTMAS -> C(.60f,.35f,.25f) to white
            else -> C(.88f,.77f,.60f) to C(.52f,.35f,.66f)
        }
        MeshBuilder().apply {
            append(GolfBuildings.house(colours.first,colours.second),P(0f,0f,-4.4f))
        }
    }
    // Rigid architecture on a deep plinth; garden ornaments alone follow the crossfall.
    val scenery by lazy { sites.map { MeshBuilder().apply {
        append(dwelling,it.origin,it.yaw)
        append(village,it.origin,it.yaw,groundHeight=hole::heightAt)
    }.build() } }
    private val light by lazy { MeshBuilder().apply { ellipsoid(0f,0f,0f,.06f,.06f,.06f,gold) }.build() }
    private val spirit by lazy { MeshBuilder().apply {
        if(hole.decorTheme==GolfDecorTheme.FAIRY) {
            ellipsoid(0f,.45f,0f,.095f,.20f,.08f,mint)
            cone(0f,.16f,0f,.23f,.33f,pink,16,.07f)
            ellipsoid(0f,.75f,0f,.115f,.13f,.11f,C(.98f,.83f,.68f))
            ellipsoid(0f,.81f,.025f,.12f,.105f,.10f,C(.78f,.54f,.23f))
            for(s in intArrayOf(-1,1)) {
                ellipsoid(s*.045f,.77f,-.099f,.012f,.015f,.012f,C(.15f,.23f,.22f))
                beam(P(s*.07f,.58f,0f),P(s*.22f,.43f,-.025f),.024f,C(.98f,.83f,.68f),8)
            }
            beam(P(.22f,.43f,-.025f),P(.28f,.75f,-.025f),.009f,gold,6)
            ellipsoid(.28f,.79f,-.025f,.035f,.035f,.035f,gold)
        } else {
            ellipsoid(0f,.85f,0f,.37f,.47f,.30f,white)
            // A scalloped cloth skirt, with both sides closed, rather than a stack of balls.
            for(i in 0..23) {
                fun point(k:Int):P { val a=k*2f*PI.toFloat()/24
                    return P(cos(a)*.44f,.28f+.09f*sin(a*6f),sin(a)*.34f) }
                val a=i*2f*PI.toFloat()/24; val aa=(i+1)*2f*PI.toFloat()/24
                quad(point(i),point(i+1),P(cos(aa)*.29f,.85f,sin(aa)*.24f),P(cos(a)*.29f,.85f,sin(a)*.24f),white)
            }
            for(s in intArrayOf(-1,1)) ellipsoid(s*.12f,1.02f,-.27f,.052f,.085f,.024f,C(.12f,.15f,.24f))
            ellipsoid(0f,.82f,-.30f,.047f,.06f,.018f,C(.12f,.15f,.24f))
        }
    }.build() }
    private val wingShape by lazy { MeshBuilder().apply {
        ellipsoid(.18f,.10f,0f,.24f,.13f,.018f,C(.76f,.72f,.95f))
        ellipsoid(.12f,-.10f,0f,.15f,.11f,.017f,pink)
        beam(P(0f,0f,0f),P(.32f,.16f,0f),.006f,white,4)
    } }
    private val wing by lazy { wingShape.build() }
    private val leftWing by lazy { MeshBuilder().apply { append(wingShape,P(0f,0f,0f),mirrorX=-1f) }.build() }
    val animatedMeshes get()=if(sites.isEmpty()) emptyList() else when(hole.decorTheme) {
        GolfDecorTheme.FAIRY -> listOf(light,spirit,wing,leftWing)
        GolfDecorTheme.HALLOWEEN -> listOf(light,spirit)
        else -> listOf(light)
    }

    /** Receives each ornament's mesh, ground position, heading and scale; plain floats, nothing allocated per frame. */
    fun interface Ornament { fun draw(mesh:ClassicMesh,x:Float,y:Float,z:Float,yaw:Float,scale:Float) }

    /** Local ornaments transformed with the same pose as their static village; none for a village off screen. */
    fun animate(time:Float,eyeX:Float,eyeZ:Float,viewProjection:FloatArray,draw:Ornament) {
        val villages=scenery
        for(index in sites.indices) {
            val site=sites[index]
            if(hypot(site.origin.x-eyeX,site.origin.z-eyeZ)>180f) continue
            // The ornaments stay within a couple of metres of their village's own bounds.
            if(!villages[index].visible(viewProjection,2f)) continue
            val t=time+index*2.1f
            when(hole.decorTheme) {
                GolfDecorTheme.FAIRY -> {
                    val x=-2.6f+sin(t*.65f)*.7f; val y=1.4f+sin(t*1.3f)*.18f
                    show(draw,site,spirit,x,y,1.6f,PI.toFloat()+sin(t*.4f)*.3f)
                    repeat(2) { k -> val s=k*2-1
                        show(draw,site,if(s==1)wing else leftWing,x,y+.56f,1.53f,PI.toFloat()+s*(1.1f+sin(t*12f)*.6f)) }
                    repeat(6) { i -> show(draw,site,light,sin(t*.4f+i*2f)*3.4f,1.2f+sin(t+i)*.5f,cos(t*.5f+i)*2.5f,scale=.5f+.25f*sin(t*2f+i)) }
                }
                GolfDecorTheme.HALLOWEEN -> {
                    show(draw,site,spirit,-3.2f+sin(t*.3f)*.5f,1.4f+sin(t*1.4f)*.25f,1.4f,PI.toFloat()+sin(t*.5f)*.25f)
                    repeat(3) { i -> show(draw,site,light,2.8f+i*.85f,.65f,1.65f,scale=1.1f+.18f*sin(t*7f+i*2f)) }
                }
                GolfDecorTheme.CHRISTMAS -> repeat(14) { i ->
                    val a=i*2.4f; val h=.8f+i*.23f; val r=(4.3f-h)*.43f
                    show(draw,site,light,-2.3f+cos(a)*r,h,sin(a)*r,scale=.65f+.25f*sin(t*2f+i))
                }
                else -> Unit
            }
        }
    }

    private fun show(draw:Ornament,site:Site,mesh:ClassicMesh,px:Float,py:Float,pz:Float,angle:Float=0f,scale:Float=1f) {
        val cs=cos(site.yaw); val sn=sin(site.yaw)
        val x=site.origin.x+px*cs+pz*sn; val z=site.origin.z-px*sn+pz*cs
        draw.draw(mesh,x,hole.heightAt(x,z)+py,z,site.yaw+angle,scale)
    }

    private fun fairyVillage(b:MeshBuilder) = with(b) {
        // Toybox's polka-dot mushrooms become garden sculptures around the little dwelling.
        for(i in 0..2) append(GolfToyboxModels.mushroom,P(-3.3f+i*.85f,0f,.4f),scale=.30f+i*.08f)
        for(i in 0..8) {
            val x=-3.7f+i*.9f; val z=2.8f+sin(i*1.7f)*.6f
            beam(P(x,0f,z),P(x,.55f,z),.025f,mint,6)
            for(j in 0..5) { val a=j*PI.toFloat()/3
                ellipsoid(x+cos(a)*.16f,.56f,z+sin(a)*.16f,.13f,.05f,.13f,if(i%2==0)pink else C(.72f,.61f,.91f)) }
            ellipsoid(x,.59f,z,.065f,.045f,.065f,gold)
        }
        // Wooden arch with climbing blossoms, visible from the fairway.
        for(s in intArrayOf(-1,1)) beam(P(s*1.1f,0f,2.4f),P(s*1.1f,2.6f,2.4f),.085f,wood,8)
        beam(P(-1.1f,2.6f,2.4f),P(1.1f,2.6f,2.4f),.08f,wood,8)
        repeat(9) { i -> ellipsoid(-1.15f+i*.28f,2.62f+.07f*sin(i*2f),2.4f,.12f,.10f,.12f,pink) }
    }

    private fun halloweenVillage(b:MeshBuilder) = with(b) {
        for(i in 0..2) pumpkin(this,2.8f+i*.85f,1.2f,.48f-i*.07f)
        // A bare tree with tapering branches and crooked tips.
        beam(P(-3.7f,0f,-1f),P(-3.5f,3.6f,-.8f),.22f,wood,10,.07f)
        for(s in intArrayOf(-1,1)) {
            beam(P(-3.6f,2f,-.9f),P(-3.6f+s*.85f,3f,-.8f),.09f,wood,8,.035f)
            beam(P(-3.6f+s*.85f,3f,-.8f),P(-3.6f+s*1.05f,3.7f,-.5f),.035f,wood,6,.009f)
        }
        cone(-2.7f,0f,2.3f,.35f,.32f,C(.38f,.32f,.39f),16,.32f)
        cone(-2.7f,.32f,2.3f,.34f,.86f,C(.43f,.37f,.44f),16,.20f)
        // Lantern casing: bright panes are recessed behind a dark frame.
        box(2.7f,0f,-1.2f,.10f,2.5f,.10f,wood)
        box(2.7f,1.9f,-1.2f,.48f,.52f,.48f,gold)
        for(s in intArrayOf(-1,1)) for(t in intArrayOf(-1,1)) box(2.7f+s*.23f,1.86f,-1.2f+t*.23f,.035f,.61f,.035f,wood)
        cone(2.7f,2.45f,-1.2f,.37f,.25f,C(.22f,.21f,.26f),8)
    }

    private fun pumpkin(b:MeshBuilder,x:Float,z:Float,r:Float) = with(b) {
        repeat(10) { i -> val a=i*2f*PI.toFloat()/10
            ellipsoid(x+cos(a)*r*.46f,r*.85f,z+sin(a)*r*.46f,r*.58f,r*.82f,r*.58f,C(.92f,.34f+.02f*(i%3),.055f)) }
        beam(P(x,r*1.5f,z),P(x+.055f,r*1.94f,z-.035f),r*.09f,wood,8,r*.06f)
        for(s in intArrayOf(-1,1)) tri(P(x+s*r*.36f,r*1.13f,z+r*.94f),P(x+s*r*.12f,r*1.03f,z+r*.99f),P(x+s*r*.24f,r*.81f,z+r*.99f),gold,false)
        quad(P(x-r*.32f,r*.59f,z+r*.95f),P(x+r*.32f,r*.59f,z+r*.95f),P(x+r*.23f,r*.40f,z+r*.93f),P(x-r*.23f,r*.40f,z+r*.93f),gold,false)
    }

    private fun christmasVillage(b:MeshBuilder) = with(b) {
        append(GolfToyboxModels.snowman,P(3.4f,0f,1.0f),scale=.34f)
        cone(-2.3f,0f,0f,.20f,.9f,wood,10,.17f)
        repeat(4) { i -> cone(-2.3f,.4f+i*.83f,0f,1.5f-i*.30f,1.55f,C(.12f,.34f,.24f),20) }
        for(i in 0..22) {
            val h=.75f+(i%10)*.31f; val a=i*2.4f; val r=(4.3f-h)*.42f
            ellipsoid(-2.3f+cos(a)*r,h,sin(a)*r,.09f,.09f,.09f,if(i%2==0)C(.83f,.16f,.16f) else gold)
        }
        // Five-point star on the fir; double sided so the landmark reads from either side.
        for(i in 0..9) {
            fun p(k:Int):P { val a=k*PI.toFloat()/5; val r=if(k%2==0).33f else .15f
                return P(-2.3f+sin(a)*r,4.45f+cos(a)*r,0f) }
            tri(P(-2.3f,4.45f,0f),p(i),p(i+1),gold,false)
        }
        for(i in 0..3) {
            val x=-3.6f+i*.68f; val z=1.5f; val h=.40f+(i%2)*.16f
            val c=if(i%2==0)C(.79f,.16f,.22f) else C(.18f,.47f,.37f)
            box(x,0f,z,.56f,h,.56f,c)
            box(x-.045f,.005f,z,.09f,h+.02f,.58f,gold)
            box(x,h,z,.58f,.025f,.09f,gold)
            for(s in intArrayOf(-1,1)) ellipsoid(x+s*.09f,h+.08f,z,.10f,.065f,.055f,gold)
        }
        for(i in 0..12) {
            val x=-3.65f+i*.61f; val y=3.03f-.17f*sin(i*PI.toFloat()/12)
            if(i>0) beam(P(x-.61f,y+.015f,-1.38f),P(x,y,-1.38f),.012f,wood,4)
            ellipsoid(x,y,-1.37f,.042f,.06f,.042f,if(i%2==0)gold else C(.91f,.24f,.23f))
        }
    }
}
