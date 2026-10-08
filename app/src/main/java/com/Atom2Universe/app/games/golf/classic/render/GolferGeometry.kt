package com.Atom2Universe.app.games.golf.classic.render

import kotlin.math.*

/** Original, texture-free character geometry: position / normal / colour / bone / material. */
internal object GolferGeometry {
    const val STRIDE=11
    private data class Colour(val r: Float, val g: Float, val b: Float) {
        fun shade(v: Float)=Colour(r*v,g*v,b*v)
    }
    private fun rgb(hex: Int)=Colour((hex shr 16 and 255)/255f,(hex shr 8 and 255)/255f,(hex and 255)/255f)
    private val ivory=rgb(0xF5F1E5)
    private val ink=rgb(0x243347)
    private val metal=rgb(0xB7C6CC)
    private data class Ring(val y: Float,val x: Float,val z: Float,val forward: Float=0f,val bust: Float=0f)

    fun build(look: GolferAppearance, putting: Boolean, wood: Boolean): FloatArray {
        val b=Builder(look.female)
        val skin=listOf(0xE8AD82,0xB97C55,0x754B38)[look.skin.coerceIn(0,2)].let(::rgb)
        val hair=rgb(if(look.skin==0) 0x493226 else 0x281F21)
        val outfit=look.outfit.coerceIn(0,2)
        val shirt=rgb(when(outfit) { 1->0xDB6854; 2->0xDFE0CE; else->0x277E83 })
        val trousers=rgb(when(outfit) { 1->0xDDCDA7; 2->0x344F50; else->0x263E59 })
        val trim=if(outfit==2) rgb(0xAD693A) else ivory
        val female=look.female
        val waist=if(female) .128f else .177f
        val chest=if(female) .180f else .225f
        val belt=if(female) .175f else .195f
        val hem=if(female) .157f else .177f
        val hip=if(female) .199f else .191f

        b.bone=GolferPose.HIPS
        // A tucked shirt and a tapered trouser waist overlap inside the belt, not on its surface.
        b.loft(listOf(Ring(-.12f,.095f,.10f),Ring(-.065f,hip,.134f),
            Ring(-.018f,hip,.137f),Ring(.005f,belt,.137f),Ring(.035f,belt,.137f)),trousers,
            bands=mapOf(3 to ink))
        b.ellipsoid(0f,.020f,.143f,.028f,.016f,.007f,metal,12,6,.7f)

        b.bone=GolferPose.CHEST
        // The bust is sculpted into the same continuous shirt surface, with a fitted waist.
        val torso=listOf(Ring(-.11f,hem,.102f),Ring(-.04f,hem,.108f),
            Ring(.035f,waist+.007f,if(female) .098f else .123f),
            Ring(.105f,waist,if(female) .095f else .120f),
            Ring(.170f,if(female) .149f else .201f,.120f,bust=if(female) .016f else 0f),
            Ring(.225f,chest,.136f,bust=if(female) .070f else 0f),
            Ring(.275f,chest,.137f,bust=if(female) .076f else 0f),
            Ring(.325f,chest,.130f,bust=if(female) .033f else 0f),
            Ring(.365f,chest*.94f,.118f,bust=if(female) .008f else 0f),
            Ring(.395f,chest*.72f,.092f),Ring(.416f,if(female) .061f else .075f,.065f))
        b.loft(torso,shirt,material=.05f)
        b.ellipsoid(0f,.425f,0f,if(female) .053f else .065f,.085f,.057f,skin)
        // Patches follow the actual curved garment instead of cutting across it as flat plates.
        if(outfit==2) {
            b.frontTriangle(torso,-.098f,.394f,0f,.274f,.098f,.394f,ink)
            b.frontTriangle(torso,-.068f,.394f,0f,.312f,.068f,.394f,ivory,.006f)
        } else {
            b.frontPatch(torso,-.014f,.014f,.277f,.383f,shirt.shade(.83f))
            for(i in 0..2) {
                val y=.358f-i*.029f
                b.ellipsoid(0f,y,b.front(torso,0f,y)+.007f,.005f,.005f,.003f,ivory,8,6)
            }
        }
        b.frontTriangle(torso,-.058f,.411f,-.093f,.363f,-.012f,.381f,trim,.008f)
        b.frontTriangle(torso,.058f,.411f,.012f,.381f,.093f,.363f,trim,.008f)
        val badgeX=if(female) -.081f else -.102f
        val badgeZ=b.front(torso,badgeX,.290f)
        b.ellipsoid(badgeX,.290f,badgeZ+.004f,.014f,.018f,.003f,trim,12,8)
        b.ellipsoid(badgeX,.290f,badgeZ+.008f,.006f,.009f,.002f,shirt,10,6)

        b.bone=GolferPose.HEAD
        // Sculpted jaw, cheekbones and cranium instead of a sphere with painted dots.
        b.loft(listOf(Ring(.030f,if(female) .061f else .074f,.062f,.015f),Ring(.065f,if(female) .085f else .098f,.094f,.021f),
            Ring(.123f,if(female) .127f else .136f,.116f,.008f),Ring(.203f,.139f,.123f),
            Ring(.275f,.124f,.112f,-.006f),Ring(.316f,.078f,.075f,-.009f),Ring(.331f,.002f,.002f)),skin)
        for(side in intArrayOf(-1,1)) {
            b.ellipsoid(side*.139f,.171f,-.003f,.027f,.044f,.023f,skin,16,10)
            b.ellipsoid(side*.153f,.171f,.010f,.011f,.025f,.011f,skin.shade(.82f),12,8)
            b.ellipsoid(side*.055f,.201f,.111f,.029f,.019f,.008f,skin.shade(.91f),16,8)
            b.ellipsoid(side*.055f,.203f,.116f,.022f,.012f,.005f,ivory,16,8)
            b.ellipsoid(side*.053f,.202f,.121f,.009f,.010f,.003f,rgb(0x40564C),12,8)
            b.ellipsoid(side*.053f,.202f,.124f,.0045f,.006f,.002f,ink,10,6)
            b.ellipsoid(side*.05f-.003f,.206f,.126f,.0025f,.0025f,.0015f,ivory,8,6)
            b.ellipsoid(side*.055f,.239f,.113f,.032f,.007f,.007f,hair,14,6)
            b.ellipsoid(side*.117f,.216f,-.020f,.025f,.048f,.065f,hair,16,10)
        }
        b.ellipsoid(0f,.177f,.124f,.020f,.041f,.024f,skin,16,10)
        b.ellipsoid(0f,.153f,.141f,.022f,.015f,.016f,skin,16,8)
        // Subtle lower lip and curved smile, readable without a raster face texture.
        b.ellipsoid(0f,.104f,.117f,.032f,.008f,.006f,rgb(if(look.skin==2) 0x875448 else 0xB87765),16,8)
        for(i in -3..3) b.ellipsoid(i*.010f,.115f+i*i*.0007f,.117f-abs(i)*.0015f,
            .007f,.0025f,.003f,skin.shade(.52f),8,4)
        b.ellipsoid(0f,.268f,-.014f,.140f,.071f,.126f,hair,24,12)
        if(female) {
            // Long face-framing locks and a separate swinging fall reaching the middle of the back.
            b.ellipsoid(-.10f,.239f,.068f,.039f,.056f,.042f,hair,16,10)
            for(side in intArrayOf(-1,1)) {
                b.ellipsoid(side*.127f,.09f,-.025f,.033f,.17f,.067f,hair,18,14)
                b.ellipsoid(side*.130f,-.065f,.005f,.025f,.09f,.040f,hair,16,10)
            }
            b.ellipsoid(0f,.16f,-.093f,.128f,.127f,.057f,hair,20,12)
            b.bone=GolferPose.HAIR
            b.loft(listOf(Ring(-.49f,.029f,.015f,-.047f),Ring(-.45f,.069f,.030f,-.066f),
                Ring(-.36f,.100f,.045f,-.077f),Ring(-.23f,.118f,.053f,-.065f),
                Ring(-.10f,.118f,.062f,-.037f),Ring(.025f,.098f,.063f)),hair)
            b.bone=GolferPose.HEAD
        }
        // Cap for the tour outfit, visor for summer, uncovered hair with the knitwear.
        if(outfit!=2) {
            if(outfit==0) b.loft(listOf(Ring(.26f,.151f,.138f),Ring(.284f,.150f,.137f),
                Ring(.307f,.147f,.130f),Ring(.356f,.117f,.104f),Ring(.38f,.002f,.002f)),ivory,
                bands=mapOf(0 to shirt))
            else b.loft(listOf(Ring(.26f,.151f,.138f),Ring(.284f,.15f,.137f)),ivory)
            b.ellipsoid(0f,.267f,.149f,.155f,.013f,.114f,ivory,24,8)
            if(outfit==0) b.ellipsoid(0f,.322f,.128f,.023f,.019f,.005f,shirt,12,8)
        }

        for(side in 0..1) {
            b.bone=GolferPose.UPPER_L+side
            // A single rounded sleeve rooted INSIDE the torso: no detached shoulder ball.
            val sleeve=if(outfit==2) .295f else .162f
            b.loft(listOf(Ring(-.055f,.022f,.026f),Ring(-.025f,.057f,.061f),
                Ring(.025f,.074f,.073f),Ring(.075f,.072f,.070f),
                Ring(sleeve-.018f,.061f,.061f),Ring(sleeve,.060f,.060f)),shirt,
                bands=mapOf(4 to trim))
            if(outfit!=2) b.loft(listOf(Ring(.147f,.053f,.053f),Ring(.23f,.052f,.048f),
                Ring(.30f,.044f,.044f)),skin)
            b.ellipsoid(0f,.30f,0f,.048f,.047f,.048f,if(outfit==2) shirt else skin)
            b.bone=GolferPose.FORE_L+side
            val foreColour=if(outfit==2) shirt else skin
            b.loft(listOf(Ring(-.008f,.043f,.043f),Ring(.07f,.052f,.046f),
                Ring(.18f,.039f,.036f),Ring(.248f,.034f,.032f),
                Ring(.269f,.032f,.031f),Ring(.29f,.030f,.030f)),foreColour,
                bands=buildMap {
                    if(outfit==2) put(3,trim)
                    if(side==0) put(4,ivory)
                })
            b.bone=GolferPose.HAND_L+side
            val hand=if(side==0) ivory else skin
            b.ellipsoid(0f,.042f,0f,.041f,.055f,.035f,hand)
            for(i in 0..3) b.ellipsoid(.016f,.018f+i*.017f,.018f,.032f,.009f,.028f,hand,10,6)
            b.ellipsoid(-.025f,.036f,.021f,.020f,.033f,.017f,hand,12,8)

            b.bone=GolferPose.THIGH_L+side
            val leg=.106f
            val legRings=mutableListOf(Ring(-.012f,.074f,.084f),Ring(.07f,leg,.108f),
                Ring(.21f,.088f,.088f),Ring(.315f,.075f,.078f),Ring(.345f,.073f,.076f))
            if(outfit!=1) legRings+=Ring(.405f,.061f,.065f)
            b.loft(legRings,trousers,bands=if(outfit==1) mapOf(3 to trousers.shade(.9f)) else emptyMap())
            if(outfit==1) b.loft(listOf(Ring(.325f,.063f,.067f),Ring(.405f,.062f,.066f)),skin)
            b.ellipsoid(0f,.405f,0f,.064f,.048f,.067f,if(outfit==1) skin else trousers)
            b.bone=GolferPose.SHIN_L+side
            val shinColour=if(outfit==1) skin else trousers
            b.loft(listOf(Ring(-.005f,.060f,.063f),Ring(.12f,.066f,.071f),
                Ring(.29f,.045f,.047f),Ring(.34f,.042f,.045f),Ring(.405f,.041f,.044f)),shinColour,
                bands=mapOf(3 to if(outfit==1) ivory else trousers.shade(.92f)))
            b.bone=GolferPose.FOOT_L+side
            b.ellipsoid(0f,.040f,.061f,.079f,.032f,.148f,ink,20,10)
            b.ellipsoid(0f,.062f,.062f,.081f,.027f,.147f,ivory,20,10)
            b.ellipsoid(0f,.097f,.051f,.075f,.061f,.136f,ivory,20,12)
            b.ellipsoid(0f,.109f,-.035f,.077f,.041f,.050f,shirt,16,10)
            for(i in 0..2) b.ellipsoid(0f,.151f-i*.007f,.041f+i*.025f,.045f,.005f,.005f,ink,12,6)
            for(sign in intArrayOf(-1,1)) b.ellipsoid(sign*.072f,.103f,.076f,.006f,.018f,.048f,shirt,12,6)
        }

        b.bone=GolferPose.CLUB
        b.loft(listOf(Ring(-.07f,.014f,.014f),Ring(.20f,.012f,.012f)),ink,material=.15f)
        for(i in 0..7) b.loft(listOf(Ring(-.055f+i*.031f,.0145f,.0145f),
            Ring(-.052f+i*.031f,.0145f,.0145f)),rgb(0x58727A))
        b.loft(listOf(Ring(.20f,.007f,.007f),Ring(1.035f,.0045f,.0045f)),metal,material=.85f)
        if(putting) {
            b.ellipsoid(0f,1.038f,0f,.10f,.027f,.034f,metal,16,8,.8f)
            b.ellipsoid(-.021f,1.035f,.023f,.006f,.014f,.014f,ink,10,6)
        } else if(wood) {
            b.ellipsoid(-.028f,1.023f,-.013f,.070f,.050f,.071f,ink,20,12,.8f)
            b.ellipsoid(-.074f,1.025f,-.014f,.012f,.034f,.053f,metal,14,8,.9f)
            b.ellipsoid(-.025f,.979f,-.016f,.035f,.006f,.036f,shirt,14,8,.65f)
        } else {
            b.ellipsoid(-.033f,1.03f,0f,.065f,.043f,.018f,metal,16,8,.85f)
            for(i in 0..3) b.ellipsoid(-.035f,1.010f+i*.012f,.019f,.041f,.0018f,.002f,ink,10,4)
        }
        return b.finish()
    }

    private class Builder(private val female: Boolean) {
        var bone=0
        private var data=FloatArray(65536)
        private var size=0
        private fun vertex(p: GVec,n: GVec,c: Colour,material: Float) {
            if(size+STRIDE>data.size) data=data.copyOf(data.size*2)
            val stretch=when(bone) {
                GolferPose.UPPER_L,GolferPose.UPPER_L+1 -> GolferPose.ARM_LENGTH/.30f
                GolferPose.FORE_L,GolferPose.FORE_L+1 -> GolferPose.ARM_LENGTH/.29f
                else -> 1f
            }
            val slim=if(!female) 1f else when(bone) {
                GolferPose.UPPER_L,GolferPose.UPPER_L+1,GolferPose.FORE_L,GolferPose.FORE_L+1 -> .79f
                GolferPose.HAND_L,GolferPose.HAND_L+1 -> .86f
                GolferPose.THIGH_L,GolferPose.THIGH_L+1 -> .90f
                GolferPose.SHIN_L,GolferPose.SHIN_L+1 -> .83f
                GolferPose.FOOT_L,GolferPose.FOOT_L+1 -> .91f
                GolferPose.HEAD -> .95f
                else -> 1f
            }
            val normal=GVec(n.x/slim,n.y/stretch,n.z/slim).unit()
            data[size++]=p.x*slim; data[size++]=p.y*stretch; data[size++]=p.z*slim
            data[size++]=normal.x; data[size++]=normal.y; data[size++]=normal.z
            data[size++]=c.r; data[size++]=c.g; data[size++]=c.b
            data[size++]=bone.toFloat(); data[size++]=material
        }
        fun triangle(a: GVec,b: GVec,c: GVec,colour: Colour) {
            val n=(b-a).cross(c-a).unit()
            vertex(a,n,colour,0f);vertex(b,n,colour,0f);vertex(c,n,colour,0f)
        }
        fun ellipsoid(x: Float,y: Float,z: Float,rx: Float,ry: Float,rz: Float,c: Colour,
                      slices: Int=18,rings: Int=10,material: Float=0f) {
            fun emit(i: Int,j: Int) {
                val a=i*2f*PI.toFloat()/slices; val t=-PI.toFloat()/2+j*PI.toFloat()/rings
                val nx=cos(a)*cos(t);val ny=sin(t);val nz=sin(a)*cos(t)
                vertex(GVec(x+nx*rx,y+ny*ry,z+nz*rz),GVec(nx/rx,ny/ry,nz/rz).unit(),c,material)
            }
            for(j in 0 until rings) for(i in 0 until slices) {
                emit(i,j);emit(i,j+1);emit(i+1,j+1)
                emit(i,j);emit(i+1,j+1);emit(i+1,j)
            }
        }
        private fun surface(r: Ring,a: Float): GVec {
            val front=max(0f,sin(a))
            val fullness=front*front*(.82f+.28f*exp(-((abs(cos(a))-.42f)/.28f).pow(2)))
            return GVec(r.x*cos(a),r.y,r.z*sin(a)+r.forward+r.bust*fullness)
        }
        /** Height of the front of the actual garment at a given x/y, including its shaped bust. */
        fun front(rings: List<Ring>,x: Float,y: Float): Float {
            val end=rings.indexOfFirst { it.y>=y }
            val j=if(end<0) rings.lastIndex else end.coerceAtLeast(1)
            val a=rings[j-1];val b=rings[j]
            val t=((y-a.y)/(b.y-a.y)).coerceIn(0f,1f)
            fun lerp(a: Float,b: Float)=a+(b-a)*t
            val r=Ring(y,lerp(a.x,b.x),lerp(a.z,b.z),lerp(a.forward,b.forward),lerp(a.bust,b.bust))
            return surface(r,acos((x/r.x).coerceIn(-1f,1f))).z
        }
        private fun frontVertex(rings: List<Ring>,x: Float,y: Float,c: Colour,lift: Float) {
            val e=.0005f
            val nx=front(rings,x-e,y)-front(rings,x+e,y)
            val ny=front(rings,x,y-e)-front(rings,x,y+e)
            vertex(GVec(x,y,front(rings,x,y)+lift),GVec(nx,ny,2f*e).unit(),c,0f)
        }
        fun frontTriangle(rings: List<Ring>,ax: Float,ay: Float,bx: Float,by: Float,
                          cx: Float,cy: Float,c: Colour,lift: Float=.004f) {
            val steps=10
            fun emit(i: Int,j: Int) {
                val u=i.toFloat()/steps;val v=j.toFloat()/steps
                frontVertex(rings,ax+(bx-ax)*u+(cx-ax)*v,ay+(by-ay)*u+(cy-ay)*v,c,lift)
            }
            for(i in 0 until steps) for(j in 0 until steps-i) {
                emit(i,j);emit(i+1,j);emit(i,j+1)
                if(i+j<steps-1) { emit(i+1,j);emit(i+1,j+1);emit(i,j+1) }
            }
        }
        fun frontPatch(rings: List<Ring>,left: Float,right: Float,bottom: Float,top: Float,c: Colour) {
            frontTriangle(rings,left,bottom,right,bottom,right,top,c)
            frontTriangle(rings,left,bottom,right,top,left,top,c)
        }
        fun loft(rings: List<Ring>,c: Colour,material: Float=0f,bands: Map<Int,Colour> = emptyMap()) {
            val slices=if(bone==GolferPose.CHEST || bone==GolferPose.HAIR) 32 else 24
            fun emit(i: Int,j: Int,colour: Colour) {
                val a=i*2f*PI.toFloat()/slices; val r=rings[j]
                val before=rings[max(0,j-1)];val after=rings[min(rings.lastIndex,j+1)]
                val tangent=surface(after,a)-surface(before,a)
                val around=(surface(r,a+.001f)-surface(r,a-.001f))*500f
                val shaded=if(bone==GolferPose.HAIR) colour.shade(1f+.055f*cos(a*9f+r.y*4f)) else colour
                vertex(surface(r,a),tangent.cross(around).unit(),shaded,material)
            }
            for(j in 0 until rings.lastIndex) for(i in 0 until slices) {
                val colour=bands[j]?:c
                emit(i,j,colour);emit(i,j+1,colour);emit(i+1,j+1,colour)
                emit(i,j,colour);emit(i+1,j+1,colour);emit(i+1,j,colour)
            }
            for(j in listOf(0,rings.lastIndex)) {
                val r=rings[j]; val n=GVec(0f,if(j==0) -1f else 1f,0f)
                for(i in 0 until slices) {
                    vertex(GVec(0f,r.y,r.forward),n,c,material)
                    for(k in intArrayOf(i,i+1)) {
                        val a=k*2f*PI.toFloat()/slices
                        vertex(surface(r,a),n,c,material)
                    }
                }
            }
        }
        fun finish()=data.copyOf(size)
    }
}
