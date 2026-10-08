package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfLie
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.*

/** The ground (with the hole cut out), vegetation and buildings are baked into immutable batches. */
internal class ClassicLandscape(private val hole: ClassicHole) {
    private val palette=ClassicPalette(hole)
    // Local canopy queries bake soft tree shade into the terrain colours, with no shadow-map pass.
    private val shadeTrees by lazy {
        val cells=HashMap<Pair<Int,Int>,MutableList<com.Atom2Universe.app.games.golf.classic.core.GolfTree>>()
        for(tree in hole.trees) {
            val r=tree.radius*1.6f
            for(z in floor((tree.z-r)/16f).toInt()..floor((tree.z+r*2f)/16f).toInt())
                for(x in floor((tree.x-r)/16f).toInt()..floor((tree.x+r*2f)/16f).toInt())
                    cells.getOrPut(x to z){ArrayList()}.add(tree)
        }
        cells
    }
    private fun treeShade(x:Float,z:Float):Float {
        var shade=1f
        shadeTrees[floor(x/16f).toInt() to floor(z/16f).toInt()]?.forEach { tree ->
            val r=tree.radius
            val canopy=((x-tree.x-r*.60f)/(r*1.18f)).pow(2)+((z-tree.z-r*.64f)/(r*.95f)).pow(2)
            val root=((x-tree.x)/(r*.28f)).pow(2)+((z-tree.z)/(r*.28f)).pow(2)
            shade*=1f-.18f*exp(-canopy*1.7f)-.13f*exp(-root*1.8f)
        }
        return shade.coerceAtLeast(.63f)
    }
    /**
     * Runs [rows] rows of work on the processor's cores, in bands of a few rows, and returns
     * each band's result in row order. Meshing a hole on one core took about four seconds.
     */
    private fun <T> inBands(rows:Int,work:(Int,Int)->T):List<T> {
        val starts=(0 until rows step 4).toList()
        val threads=Runtime.getRuntime().availableProcessors().coerceIn(1,6)
        if(threads==1) return starts.map { work(it,min(rows,it+4)) }
        val pool=Executors.newFixedThreadPool(threads)
        try { return pool.invokeAll(starts.map { s -> Callable { work(s,min(rows,s+4)) } }).map { it.get() } }
        finally { pool.shutdown() }
    }

    val millX = hole.width * -.35f
    val millZ = hole.length * .63f
    val millY = hole.heightAt(millX,millZ) + 7.5f

    fun terrain(): GroundMesh {
        val b = GroundBuilder()
        val step = 2.5f
        val nx = ceil((hole.width + 80f) / step).toInt()
        val nz = ceil((hole.length + 110f) / step).toInt()
        // One contour/hazard evaluation supplies both colour and shader weights at each vertex.
        // Base colours only: mowing, tufts and grain are drawn per pixel by GroundShader.
        fun sampleAt(x:Float,z:Float): GroundSample {
            val archipelago=hole.islands.isNotEmpty()
            val shore=if(archipelago)hole.islandSignedDistance(x,z) else Float.NEGATIVE_INFINITY
            val fair=if(archipelago)shore+ClassicHole.ISLAND_ROUGH_WIDTH else hole.fairwaySignedDistance(x,z)
            val greenDistance=hole.greenSignedDistance(x,z)
            val tee=abs(x)<4.5f && abs(z)<5.5f && greenDistance>0f
            var colour=palette.rough
            val fairBlend=((1.15f-fair)/2.3f).coerceIn(0f,1f)
            var green=((.40f-greenDistance)/.8f).coerceIn(0f,1f)
            var fairway=if(tee)1f else fairBlend
            var sand=0f; var water=0f
            colour=colour.mix(palette.fairway,fairBlend)
            if(greenDistance<2.2f) {
                colour=colour.mix(palette.fringe,((2.2f-greenDistance)/1.1f).coerceIn(0f,1f))
                colour=colour.mix(palette.green,((.40f-greenDistance)/.8f).coerceIn(0f,1f))
            }
            if(tee) colour=palette.fairway
            if(archipelago) {
                water=((shore+.2f)/.6f).coerceIn(0f,1f)
                colour=colour.mix(palette.water,((shore+1.4f)/1.4f).coerceIn(0f,1f))
            }
            for(hazard in hole.hazards) {
                val d=hazard.signedDistance(x,z)
                if(d<1.4f && greenDistance>0f) {
                    if(hazard.lie==GolfLie.BUNKER) {
                        colour=colour.mix(C(.54f,.57f,.25f),((1.4f-d)/1.4f).coerceIn(0f,1f))
                        colour=colour.mix(palette.sand,((.2f-d)/.7f).coerceIn(0f,1f))
                        sand=max(sand,((.2f-d)/.7f).coerceIn(0f,1f))
                    } else {
                        colour=colour.mix(C(.50f,.62f,.36f),((1.4f-d)/1.4f).coerceIn(0f,1f))
                        colour=colour.mix(palette.water,((.2f-d)/.6f).coerceIn(0f,1f))
                        water=max(water,((.2f-d)/.6f).coerceIn(0f,1f))
                    }
                }
            }
            val hazards=min(1f,sand+water)
            green*=1f-hazards
            fairway=min(fairway*(1f-hazards),1f-hazards-green).coerceAtLeast(0f)
            val dx=if(shore>1f)0f else (hole.heightAt(x+.4f,z)-hole.heightAt(x-.4f,z))/.8f
            val dz=if(shore>1f)0f else (hole.heightAt(x,z+.4f)-hole.heightAt(x,z-.4f))/.8f
            val light=(.77f+.23f*((dx*.35f+.86f+dz*.36f)/sqrt(1f+dx*dx+dz*dz)).coerceIn(0f,1f))*treeShade(x,z)
            return GroundSample(colour.shade(light),Turf(fairway,green,sand,water),fair,greenDistance,light,1f)
        }
        // Shared lattice vertices are evaluated once, including expensive slope / contour samples.
        // Bands on different threads may both fill a vertex they share: the same value, written
        // whole (a float, or an immutable sample), so the race only costs a duplicate evaluation.
        val latticeWidth=nx*3+1
        val cachedHeight=FloatArray(latticeWidth*(nz*3+1)) { Float.NaN }
        val cachedSample=arrayOfNulls<GroundSample>(cachedHeight.size)
        fun index(x:Float,z:Float)=(((z+40f)*3f/step).roundToInt().coerceIn(0,nz*3))*latticeWidth+
            ((x+(hole.width+80f)/2)*3f/step).roundToInt().coerceIn(0,nx*3)
        fun point(x:Float,z:Float):P {
            val i=index(x,z)
            if(cachedHeight[i].isNaN()) cachedHeight[i]=hole.heightAt(x,z)
            return P(x,cachedHeight[i],z)
        }
        fun sample(x:Float,z:Float):GroundSample {
            val i=index(x,z)
            return cachedSample[i] ?: sampleAt(x,z).also { cachedSample[i]=it }
        }
        // Fine cells that touch the hole are left out and replaced by one patch with the cup cut out.
        val cx=hole.cup.x; val cz=hole.cup.z
        val reach=hole.cupRadius+.004f
        /** A band of rows, meshed on its own thread: its triangles and the cells it left for the cup patch. */
        class Band {
            val mesh=GroundBuilder(1 shl 14)
            var cupLeft=Float.MAX_VALUE; var cupRight=-Float.MAX_VALUE
            var cupBack=Float.MAX_VALUE; var cupFront=-Float.MAX_VALUE
            fun cell(x:Float,z:Float,size:Float) {
                if(size<step && x<cx+reach && x+size>cx-reach && z<cz+reach && z+size>cz-reach) {
                    cupLeft=min(cupLeft,x); cupRight=max(cupRight,x+size)
                    cupBack=min(cupBack,z); cupFront=max(cupFront,z+size)
                    return
                }
                mesh.triangle(point(x,z),point(x+size,z),point(x+size,z+size),sample(x,z),sample(x+size,z),sample(x+size,z+size))
                mesh.triangle(point(x,z),point(x+size,z+size),point(x,z+size),sample(x,z),sample(x+size,z+size),sample(x,z+size))
            }
        }
        val refined=BooleanArray(nx*nz)
        inBands(nz) { iz0,iz1 ->
            for (iz in iz0 until iz1) for (ix in 0 until nx) {
                val x = -(hole.width+80f)/2 + ix*step; val z = -40f+iz*step
                // The whole green is finely meshed: a real-size ball and the cup sit on its true surface.
                val nearGreen=hole.greenSignedDistance(x+step*.5f,z+step*.5f)<step*2f
                val fairDistance=hole.fairwaySignedDistance(x+step*.5f,z+step*.5f)
                refined[iz*nx+ix]=nearGreen || fairDistance in -step..(ClassicHole.SEMI_ROUGH_WIDTH+step) ||
                    (hole.islands.isNotEmpty() && abs(hole.islandSignedDistance(x+step*.5f,z+step*.5f))<step*2f) ||
                    hole.hazards.any { abs(it.signedDistance(x+step*.5f,z+step*.5f))<step*2f }
            }
        }
        fun fine(ix:Int,iz:Int)=ix in 0 until nx && iz in 0 until nz && refined[iz*nx+ix]
        val bands=inBands(nz) { iz0,iz1 -> Band().apply {
            for (iz in iz0 until iz1) for (ix in 0 until nx) {
                val x = -(hole.width+80f)/2 + ix*step; val z = -40f+iz*step
                if(fine(ix,iz)) {
                    for(j in 0..2) for(i in 0..2) cell(x+i*step/3,z+j*step/3,step/3)
                } else if(fine(ix-1,iz)||fine(ix+1,iz)||fine(ix,iz-1)||fine(ix,iz+1)) {
                    // Match each fine neighbour's edge vertices. A two-triangle coarse cell would
                    // leave hairline sky cracks wherever the true slope curves between its corners.
                    val edge=ArrayList<P>(12)
                    fun side(ax:Float,az:Float,bx:Float,bz:Float,subdivide:Boolean) {
                        val pieces=if(subdivide)3 else 1
                        for(i in 0 until pieces) edge+=point(ax+(bx-ax)*i/pieces,az+(bz-az)*i/pieces)
                    }
                    side(x,z,x+step,z,fine(ix,iz-1))
                    side(x+step,z,x+step,z+step,fine(ix+1,iz))
                    side(x+step,z+step,x,z+step,fine(ix,iz+1))
                    side(x,z+step,x,z,fine(ix-1,iz))
                    val mx=x+step*.5f;val mz=z+step*.5f
                    val centre=P(mx,hole.heightAt(mx,mz),mz)
                    val middle=sampleAt(mx,mz)
                    for(i in edge.indices) {
                        val a=edge[i];val c=edge[(i+1)%edge.size]
                        mesh.triangle(centre,a,c,middle,sample(a.x,a.z),sample(c.x,c.z))
                    }
                } else cell(x,z,step)
            }
        } }
        bands.forEach { b.append(it.mesh) }
        val cupLeft=bands.minOf { it.cupLeft }; val cupRight=bands.maxOf { it.cupRight }
        val cupBack=bands.minOf { it.cupBack }; val cupFront=bands.maxOf { it.cupFront }
        if(cupLeft<cupRight) cupPatch(b,cupLeft,cupBack,cupRight,cupFront,::sampleAt)
        // A single shared lattice surrounds the course, including the four apron corners.
        // At the inner seam its edge has exactly the subdivisions of the playable mesh.
        val backdrop=ClassicBackdrop(hole)
        val distantPoints=HashMap<Pair<Float,Float>,P>()
        val distantSamples=HashMap<Pair<Float,Float>,GroundSample>()
        fun distantPoint(x:Float,z:Float)=distantPoints.getOrPut(x to z) { P(x,backdrop.heightAt(x,z),z) }
        fun distantSample(x:Float,z:Float):GroundSample = distantSamples.getOrPut(x to z) {
            if(backdrop.outward(x,z)==0f) return@getOrPut sampleAt(x,z)
            if(hole.islands.isNotEmpty()) return@getOrPut GroundSample(palette.water,Turf(water=1f))
            val dx=(backdrop.heightAt(x+.4f,z)-backdrop.heightAt(x-.4f,z))/.8f
            val dz=(backdrop.heightAt(x,z+.4f)-backdrop.heightAt(x,z-.4f))/.8f
            val light=.77f+.23f*((dx*.35f+.86f+dz*.36f)/sqrt(1f+dx*dx+dz*dz)).coerceIn(0f,1f)
            val variation=.5f+.5f*sin(x*.019f+sin(z*.013f)*2f)*cos(z*.021f)
            val colour=palette.rough.mix(if(hole.snowy) C(.62f,.69f,.77f) else if(hole.highlands) C(.57f,.48f,.31f) else C(.36f,.49f,.25f),variation*.55f)
            GroundSample(colour.shade(light),Turf.ROUGH,light=light)
        }
        for(j in 0 until backdrop.zs.size-1) for(i in 0 until backdrop.xs.size-1) {
            val x=backdrop.xs[i]; val xx=backdrop.xs[i+1]
            val z=backdrop.zs[j]; val zz=backdrop.zs[j+1]
            if(x>=backdrop.left && xx<=backdrop.right && z>=backdrop.front && zz<=backdrop.back) continue
            val edge=ArrayList<P>(8)
            fun side(ax:Float,az:Float,bx:Float,bz:Float,seam:Boolean) {
                if(!seam) { edge+=distantPoint(ax,az); return }
                // Only this edge needs the 2.5 m (or refined) playable lattice. Its other
                // three edges keep the coarse lattice shared by neighbouring apron cells.
                val horizontal=az==bz
                val cells=(max(abs(bx-ax),abs(bz-az))/step).roundToInt()
                for(cell in 0 until cells) {
                    val sx=ax+(bx-ax)*cell/cells; val sz=az+(bz-az)*cell/cells
                    val ex=ax+(bx-ax)*(cell+1)/cells; val ez=az+(bz-az)*(cell+1)/cells
                    val ix=if(horizontal) ((min(sx,ex)-backdrop.left)/step).roundToInt()
                        else if(ax==backdrop.left)0 else nx-1
                    val iz=if(!horizontal) ((min(sz,ez)-backdrop.front)/step).roundToInt()
                        else if(az==backdrop.front)0 else nz-1
                    val pieces=if(fine(ix,iz))3 else 1
                    for(k in 0 until pieces) edge+=distantPoint(sx+(ex-sx)*k/pieces,sz+(ez-sz)*k/pieces)
                }
            }
            val withinX=x>=backdrop.left && xx<=backdrop.right
            val withinZ=z>=backdrop.front && zz<=backdrop.back
            side(x,z,xx,z,z==backdrop.back && withinX)
            side(xx,z,xx,zz,xx==backdrop.left && withinZ)
            side(xx,zz,x,zz,zz==backdrop.front && withinX)
            side(x,zz,x,z,x==backdrop.right && withinZ)
            if(edge.size==4) {
                val samples=edge.map { distantSample(it.x,it.z) }
                b.triangle(edge[0],edge[1],edge[2],samples[0],samples[1],samples[2])
                b.triangle(edge[0],edge[2],edge[3],samples[0],samples[2],samples[3])
            } else {
                val centre=distantPoint((x+xx)*.5f,(z+zz)*.5f)
                val middle=distantSample(centre.x,centre.z)
                for(k in edge.indices) {
                    val a=edge[k]; val c=edge[(k+1)%edge.size]
                    b.triangle(centre,a,c,middle,distantSample(a.x,a.z),distantSample(c.x,c.z))
                }
            }
        }
        // A winding sandy cart path follows the right edge without crossing the playing line.
        val gravel=Turf(sand=.55f)
        var z=-15f
        while(hole.islands.isEmpty() && z<hole.length+40f) {
            val next=z+1.5f
            val x=pathX(z); val xx=pathX(next)
            // Never paint a dry-looking path on a water penalty area.
            if(hole.lieAt(x,z)==GolfLie.WATER || hole.lieAt(xx,next)==GolfLie.WATER) { z=next; continue }
            b.quad(P(x-1.3f,hole.heightAt(x-1.3f,z)+.18f,z),P(x+1.3f,hole.heightAt(x+1.3f,z)+.18f,z),
                P(xx+1.3f,hole.heightAt(xx+1.3f,next)+.18f,next),P(xx-1.3f,hole.heightAt(xx-1.3f,next)+.18f,next),C(.77f,.72f,.52f),gravel)
            z=next
        }
        return b.build()
    }

    /**
     * Turf of the cells around the hole, from the rectangle edge in to the lip: a fan of quads
     * between the rectangle (its corners included, so neighbours meet it exactly) and the circle.
     */
    private fun cupPatch(b:GroundBuilder,x0:Float,z0:Float,x1:Float,z1:Float,sampleAt:(Float,Float)->GroundSample) {
        val cx=hole.cup.x; val cz=hole.cup.z
        val two=2f*PI.toFloat()
        fun wrap(a:Float)=((a%two)+two)%two
        val angles=(List(64) { it*two/64f }+listOf(P(x0,0f,z0),P(x1,0f,z0),P(x1,0f,z1),P(x0,0f,z1)).map { wrap(atan2(it.z-cz,it.x-cx)) }).sorted()
        fun edge(a:Float):P {
            val dx=cos(a); val dz=sin(a)
            var t=Float.MAX_VALUE
            if(dx>1e-6f) t=min(t,(x1-cx)/dx) else if(dx< -1e-6f) t=min(t,(x0-cx)/dx)
            if(dz>1e-6f) t=min(t,(z1-cz)/dz) else if(dz< -1e-6f) t=min(t,(z0-cz)/dz)
            val x=(cx+dx*t).coerceIn(x0,x1); val z=(cz+dz*t).coerceIn(z0,z1)
            return P(x,hole.heightAt(x,z),z)
        }
        fun lip(a:Float):P { val x=cx+cos(a)*hole.cupRadius; val z=cz+sin(a)*hole.cupRadius; return P(x,hole.heightAt(x,z),z) }
        for(i in angles.indices) {
            val a=angles[i]; val c=angles[(i+1)%angles.size]
            if(abs(c-a)<1e-6f) continue
            val p=lip(a); val q=edge(a); val r=edge(c); val s=lip(c)
            val sp=sampleAt(p.x,p.z); val sr=sampleAt(r.x,r.z)
            b.triangle(p,q,r,sp,sampleAt(q.x,q.z),sr)
            b.triangle(p,r,s,sp,sr,sampleAt(s.x,s.z))
        }
    }

    /**
     * The hole itself: a 2.5 cm band of soil under the lip, then the white liner, darker with depth,
     * and the bottom. A real-size ball can drop into it and be seen inside.
     */
    private fun cup(b:MeshBuilder) {
        val cx=hole.cup.x; val cz=hole.cup.z
        val floor=hole.cup.y-ClassicHole.CUP_DEPTH
        val radius=hole.cupRadius
        val soil=C(.34f,.26f,.17f); val liner=C(.86f,.86f,.82f); val deep=C(.36f,.36f,.34f)
        val sides=40
        for(i in 0 until sides) {
            val a=i*2f*PI.toFloat()/sides; val c=(i+1)*2f*PI.toFloat()/sides
            val ax=cx+cos(a)*radius; val az=cz+sin(a)*radius
            val bx=cx+cos(c)*radius; val bz=cz+sin(c)*radius
            val ay=hole.heightAt(ax,az); val by=hole.heightAt(bx,bz)
            b.colouredQuad(P(ax,ay,az),P(bx,by,bz),P(bx,by-.025f,bz),P(ax,ay-.025f,az),soil,soil,soil.shade(.7f),soil.shade(.7f))
            b.colouredQuad(P(ax,ay-.025f,az),P(bx,by-.025f,bz),P(bx,floor,bz),P(ax,floor,az),liner,liner,deep,deep)
        }
        b.disc(cx,floor,cz,radius,radius,C(.22f,.22f,.20f),sides)
        // A thin white collar on the turf makes the lip readable from across the green.
        val collar=C(.97f,.97f,.93f)
        for(i in 0 until sides) {
            val a=i*2f*PI.toFloat()/sides; val c=(i+1)*2f*PI.toFloat()/sides
            fun at(angle:Float,r:Float):P { val x=cx+cos(angle)*r; val z=cz+sin(angle)*r; return P(x,hole.heightAt(x,z)+.002f,z) }
            b.quad(at(a,radius+.004f),at(c,radius+.004f),at(c,radius+.02f),at(a,radius+.02f),collar,false)
        }
    }

    private fun pathX(z: Float) = hole.pathX(z)

    fun scenery(): List<ClassicMesh> {
        val b=MeshBuilder()
        val homeX=-hole.width*.34f; val homeZ=10f
        if(hole.islands.isEmpty()) {
            if(hole.highlands) highlandLodge(b,homeX,homeZ) else {
                cottage(b,homeX,homeZ); mill(b)
            }
            cart(b,pathX(35f)+2.5f,35f)
            cart(b,pathX(hole.length*.76f)+2.5f,hole.length*.76f)
            golfBag(b,hole.tee.x+4f,hole.tee.z-1f)
            // White stakes make the playable boundary legible before a costly out-of-bounds shot.
            val stake=C(.97f,.96f,.88f)
            for(side in listOf(-1f,1f)) for(z in -20..(hole.length+55f).toInt() step 32) {
                val x=side*hole.width*.5f
                b.cone(x,hole.heightAt(x,z.toFloat()),z.toFloat(),.11f,1.35f,stake,5,.11f)
            }
            for(z in listOf(-20f,hole.length+55f)) for(x in (-hole.width*.5f).toInt()..(hole.width*.5f).toInt() step 32)
                b.cone(x.toFloat(),hole.heightAt(x.toFloat(),z),z,.11f,1.35f,stake,5,.11f)
        }
        if(hole.islands.isNotEmpty()) golfBag(b,hole.tee.x+4f,hole.tee.z-1f)
        // Tee markers and the real hole; the flagstick is drawn live.
        for(side in listOf(-1f,1f)) {
            val x=hole.tee.x+side*2.2f
            b.sphere(x,hole.heightAt(x,hole.tee.z)+.12f,hole.tee.z,.13f,C(.92f,.91f,.82f),8,4)
        }
        cup(b)
        return ClassicDecor(hole).build()+b.build()
    }

    private fun bush(b: MeshBuilder,x: Float,z: Float) {
        val y=hole.heightAt(x,z)
        b.organic(x,y+.65f,z,1.15f,.83f,1.05f,C(.26f,.44f,.23f),x+z,9,4)
        for(i in 0..4) {
            val a=i*1.25f
            b.sphere(x+cos(a)*.8f,y+1.05f,z+sin(a)*.65f,.16f,
                if(i%2==0) C(1f,.68f,.68f) else C(1f,.92f,.45f),5,3)
        }
    }

    private fun highlandLodge(b:MeshBuilder,x:Float,z:Float) {
        val y=hole.heightAt(x,z)
        b.box(x,y,z,8f,3.4f,6f,C(.49f,.48f,.43f))
        val roof=if(hole.snowy) C(.87f,.91f,.95f) else C(.23f,.28f,.30f)
        b.quad(P(x-4.6f,y+3.4f,z-3.6f),P(x+4.6f,y+3.4f,z-3.6f),P(x+4.6f,y+5.4f,z),P(x-4.6f,y+5.4f,z),roof)
        b.quad(P(x-4.6f,y+5.4f,z),P(x+4.6f,y+5.4f,z),P(x+4.6f,y+3.4f,z+3.6f),P(x-4.6f,y+3.4f,z+3.6f),roof)
        b.box(x,y,z-3.05f,1.5f,2.4f,.15f,C(.27f,.22f,.18f))
        for(side in listOf(-1f,1f)) b.box(x+side*2.6f,y+1.5f,z-3.1f,1.2f,1.1f,.15f,C(.75f,.58f,.32f))
        b.box(x+2.6f,y+3.4f,z+1f,.8f,2.7f,.8f,C(.43f,.43f,.40f))
        // A ruined watchtower beyond the boundary replaces the pastoral windmill.
        val tx=millX; val tz=millZ; val ty=hole.heightAt(tx,tz)
        b.cone(tx,ty,tz,2.8f,8.5f,C(.43f,.45f,.44f),8,2.5f)
        for(i in 0..7) {
            val a=i*PI.toFloat()/4f
            b.box(tx+cos(a)*2.3f,ty+8.5f,tz+sin(a)*2.3f,.85f,1.1f,.85f,C(.48f,.49f,.46f))
        }
    }

    private fun cottage(b: MeshBuilder,x: Float,z: Float) {
        val y=hole.heightAt(x,z)
        b.box(x,y,z,9f,4.7f,7f,C(.96f,.86f,.66f))
        val roof=C(.66f,.27f,.19f)
        b.quad(P(x-5.2f,y+4.6f,z-4.2f),P(x+5.2f,y+4.6f,z-4.2f),P(x+5.2f,y+7.1f,z),P(x-5.2f,y+7.1f,z),roof)
        b.quad(P(x-5.2f,y+7.1f,z),P(x+5.2f,y+7.1f,z),P(x+5.2f,y+4.6f,z+4.2f),P(x-5.2f,y+4.6f,z+4.2f),roof)
        b.tri(P(x-4.5f,y+4.7f,z-3.5f),P(x-4.5f,y+7f,z),P(x-4.5f,y+4.7f,z+3.5f),C(.89f,.78f,.59f))
        b.tri(P(x+4.5f,y+4.7f,z+3.5f),P(x+4.5f,y+7f,z),P(x+4.5f,y+4.7f,z-3.5f),C(.89f,.78f,.59f))
        b.box(x,y-.10f,z,9.4f,.40f,7.4f,C(.56f,.53f,.44f))
        b.box(x,y+.10f,z-4.0f,2.6f,.18f,1.1f,C(.68f,.65f,.54f))
        // Eaves, roof courses and window frames give depth without a texture atlas.
        for(i in 1..6) for(side in intArrayOf(-1,1)) {
            val zz=z+side*i*.58f;val yy=y+7.1f-i*.58f*2.5f/4.2f
            b.beam(P(x-5.15f,yy+.025f,zz),P(x+5.15f,yy+.025f,zz),.028f,C(.49f,.24f,.17f),4)
        }
        for(side in intArrayOf(-1,1))b.box(x,y+4.49f,z+side*3.6f,9.9f,.15f,.15f,C(.39f,.30f,.22f))
        b.box(x,y,z-3.55f,1.7f,2.8f,.1f,C(.27f,.39f,.39f))
        for(side in listOf(-1f,1f)) {
            b.box(x+side*2.8f,y+1.9f,z-3.58f,1.4f,1.45f,.15f,C(.39f,.71f,.79f))
            val wx=x+side*2.8f
            for(edge in intArrayOf(-1,1))b.box(wx+edge*.75f,y+1.82f,z-3.69f,.12f,1.62f,.12f,C(.93f,.88f,.72f))
            for(h in listOf(1.82f,2.6f,3.35f))b.box(wx,y+h,z-3.69f,1.6f,.08f,.12f,C(.93f,.88f,.72f))
            b.box(wx,y+1.85f,z-3.70f,.075f,1.6f,.10f,C(.93f,.88f,.72f))
            b.box(x+side*2.8f,y+1.78f,z-3.8f,1.8f,.25f,.5f,C(.53f,.29f,.15f))
            bush(b,x+side*5.7f,z-2.5f)
        }
        b.box(x+2.7f,y+5.1f,z+1f,1f,3f,1f,C(.68f,.49f,.36f))
        b.box(x+2.7f,y+8.05f,z+1f,1.25f,.16f,1.25f,C(.44f,.37f,.31f))
    }

    private fun mill(b: MeshBuilder) {
        val y=hole.heightAt(millX,millZ)
        b.cone(millX,y,millZ,2.5f,9f,C(.90f,.86f,.70f),8,1.7f)
        b.cone(millX,y+9f,millZ,2.3f,2.5f,C(.52f,.29f,.22f),8)
        b.box(millX,y,millZ-2.35f,1.2f,2.2f,.2f,C(.37f,.29f,.20f))
        for(h in listOf(3.6f,6.2f)) {
            b.box(millX,y+h,millZ-2.08f,.85f,1.15f,.20f,C(.32f,.43f,.43f))
            b.box(millX,y+h+.53f,millZ-2.21f,.88f,.06f,.06f,C(.89f,.85f,.72f))
        }
    }

    private fun cart(b: MeshBuilder,x: Float,z: Float) {
        val y=hole.heightAt(x,z)
        b.box(x,y+.5f,z,1.6f,.65f,2.7f,C(.97f,.91f,.74f))
        b.box(x,y+1f,z+.45f,1.25f,.2f,.8f,C(.24f,.36f,.30f))
        b.box(x,y+1.2f,z+.75f,1.25f,.6f,.15f,C(.24f,.36f,.30f))
        for(side in listOf(-1f,1f)) {
            b.box(x+side*.65f,y+1f,z-.8f,.06f,1.1f,.06f,C(.37f,.41f,.37f))
            b.box(x+side*.65f,y+1f,z+.8f,.06f,1.1f,.06f,C(.37f,.41f,.37f))
            for(front in listOf(-1f,1f)) b.sphere(x+side*.82f,y+.38f,z+front*.84f,.35f,C(.16f,.20f,.20f),7,4)
        }
        b.box(x,y+2.1f,z,1.8f,.15f,2.45f,C(.96f,.87f,.59f))
        b.box(x,y+.70f,z-1.40f,1.40f,.09f,.16f,C(.23f,.29f,.28f))
        for(side in intArrayOf(-1,1)) {
            b.box(x+side*.50f,y+.87f,z-1.363f,.25f,.14f,.04f,C(.98f,.96f,.78f))
            b.box(x+side*.48f,y+.79f,z+1.363f,.20f,.12f,.04f,C(.73f,.23f,.18f))
        }
        b.beam(P(x-.42f,y+.85f,z-.51f),P(x-.42f,y+1.39f,z-.25f),.037f,C(.23f,.29f,.28f),5)
        b.beam(P(x-.64f,y+1.40f,z-.25f),P(x-.20f,y+1.40f,z-.25f),.030f,C(.23f,.29f,.28f),5)
    }

    private fun golfBag(b: MeshBuilder,x:Float,z:Float) {
        val y=hole.heightAt(x,z)
        b.cone(x,y+.2f,z,.34f,1.15f,C(.85f,.29f,.18f),8,.4f)
        for(i in 0..4) {
            val dx=(i-2)*.12f
            b.cone(x+dx,y+1f,z,.023f,.85f,C(.77f,.83f,.81f),4,.023f)
            b.box(x+dx+.045f,y+1.8f,z,.17f,.09f,.09f,C(.25f,.31f,.31f))
        }
        b.sphere(x-.4f,y+.2f,z,.2f,C(.15f,.19f,.17f),6,4)
        b.sphere(x+.4f,y+.2f,z,.2f,C(.15f,.19f,.17f),6,4)
    }

    fun blades(): ClassicMesh {
        val b=MeshBuilder()
        for(i in 0..3) {
            val a=i*PI.toFloat()/2
            fun p(r:Float,w:Float)=P(cos(a)*r-sin(a)*w,sin(a)*r+cos(a)*w,0f)
            b.quad(p(.3f,-.09f),p(5.2f,-.09f),p(5.2f,.09f),p(.3f,.09f),C(.39f,.31f,.21f))
            b.quad(p(2.1f,.1f),p(5.1f,.1f),p(5.1f,.84f),p(2.1f,.84f),C(.96f,.93f,.78f))
        }
        b.sphere(0f,0f,0f,.29f,C(.44f,.34f,.22f),8,4)
        return b.build()
    }
}
