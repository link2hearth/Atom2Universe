package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfLie
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.*

/** The ground (with the hole cut out), vegetation and buildings are baked into immutable batches. */
internal class ClassicLandscape(private val hole: ClassicHole) {
    private val palette=ClassicPalette(hole)
    val archipelago by lazy { if(hole.islands.isNotEmpty()) ArchipelagoScenery(hole) else null }
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
    val millY = hole.heightAt(millX,millZ) + 10.2f

    fun terrain(): GroundMesh {
        if(hole.mini!=null) return MiniLandscape(hole,this).terrain()
        val b = GroundBuilder()
        val step = 2.5f
        val nx = ceil((hole.width + 80f) / step).toInt()
        val nz = ceil((hole.length + 110f) / step).toInt()
        val left=-(hole.width+80f)/2f
        // Lighting needs the broad slope, not four new physics queries for every fine
        // vertex. Share a 2.5 m height lattice, including one neighbour beyond each edge.
        // Islands keep their cheap flat-sea shortcut; the cup keeps its precise normals.
        val coarseWidth=nx+3
        val coarseHeights=if(hole.islands.isEmpty()) FloatArray(coarseWidth*(nz+3)).also { heights ->
            inBands(nz+3) { start,end ->
                for(z in start until end) for(x in 0 until coarseWidth)
                    heights[z*coarseWidth+x]=hole.heightAt(left+(x-1)*step,-40f+(z-1)*step)
            }
        } else null
        val coarseLight=coarseHeights?.let { heights -> FloatArray((nx+1)*(nz+1)) { i ->
            val j=(i/(nx+1)+1)*coarseWidth+i%(nx+1)+1
            val dx=(heights[j+1]-heights[j-1])/(2f*step)
            val dz=(heights[j+coarseWidth]-heights[j-coarseWidth])/(2f*step)
            .77f+.23f*((dx*.35f+.86f+dz*.36f)/sqrt(1f+dx*dx+dz*dz)).coerceIn(0f,1f)
        } }
        fun slopeLight(x:Float,z:Float):Float {
            val gx=((x-left)/step).coerceIn(0f,nx.toFloat())
            val gz=((z+40f)/step).coerceIn(0f,nz.toFloat())
            val ix=gx.toInt().coerceAtMost(nx-1); val iz=gz.toInt().coerceAtMost(nz-1)
            val u=gx-ix; val v=gz-iz; val i=iz*(nx+1)+ix; val row=nx+1
            val lights=coarseLight!!
            return (lights[i]*(1f-u)+lights[i+1]*u)*(1f-v)+
                (lights[i+row]*(1f-u)+lights[i+row+1]*u)*v
        }
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
            var sand=0f; var water=0f; var lakeDistance=1000f
            colour=colour.mix(palette.fairway,fairBlend)
            if(greenDistance<2.2f) {
                colour=colour.mix(palette.fringe,((2.2f-greenDistance)/1.1f).coerceIn(0f,1f))
                colour=colour.mix(palette.green,((.40f-greenDistance)/.8f).coerceIn(0f,1f))
            }
            if(tee) colour=palette.fairway
            if(archipelago) {
                water=((shore+.2f)/.6f).coerceIn(0f,1f)
                val beach=((shore+4.6f)/3.8f).coerceIn(0f,1f).let { it*it*(3f-2f*it) }
                sand=beach*(1f-water)
                colour=colour.mix(C(.86f,.79f,.59f),sand)
                val sea=palette.waterAtDepth(shore)
                colour=colour.mix(sea,water)
            }
            for(hazard in hole.hazards) {
                val d=hazard.signedDistance(x,z)
                if(hazard.lie==GolfLie.WATER) lakeDistance=min(lakeDistance,d)
                if(d<1.4f && greenDistance>0f) {
                    if(hazard.lie==GolfLie.BUNKER) {
                        colour=colour.mix(C(.54f,.57f,.25f),((1.4f-d)/1.4f).coerceIn(0f,1f))
                        colour=colour.mix(palette.sand,((.2f-d)/.7f).coerceIn(0f,1f))
                        sand=max(sand,((.2f-d)/.7f).coerceIn(0f,1f))
                    } else {
                        colour=colour.mix(C(.50f,.62f,.36f),((1.4f-d)/1.4f).coerceIn(0f,1f))
                        colour=colour.mix(palette.waterAtDepth(-d),((.2f-d)/.6f).coerceIn(0f,1f))
                        water=max(water,((.2f-d)/.6f).coerceIn(0f,1f))
                    }
                }
            }
            val hazards=min(1f,sand+water)
            green*=1f-hazards
            fairway=min(fairway*(1f-hazards),1f-hazards-green).coerceAtLeast(0f)
            val slope=if(shore>1f) .9678f else if(coarseLight!=null && greenDistance>3f) slopeLight(x,z) else {
                val dx=(hole.heightAt(x+.4f,z)-hole.heightAt(x-.4f,z))/.8f
                val dz=(hole.heightAt(x,z+.4f)-hole.heightAt(x,z-.4f))/.8f
                .77f+.23f*((dx*.35f+.86f+dz*.36f)/sqrt(1f+dx*dx+dz*dz)).coerceIn(0f,1f)
            }
            val light=slope*treeShade(x,z)
            return GroundSample(colour.shade(light),Turf(fairway,green,sand,water),fair,greenDistance,light,1f,lakeDistance)
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
            if(cachedHeight[i].isNaN()) {
                val ix=i%latticeWidth; val iz=i/latticeWidth
                cachedHeight[i]=if(coarseHeights!=null && ix%3==0 && iz%3==0)
                    coarseHeights[(iz/3+1)*coarseWidth+ix/3+1] else hole.heightAt(x,z)
            }
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
            if(hole.islands.isNotEmpty()) {
                // Continue the actual shore distance across the apron: a default 1000 m value
                // made the shallow water abruptly turn blue along the rectangular mesh seam.
                val shore=hole.islandSignedDistance(x,z)
                return@getOrPut GroundSample(palette.waterAtDepth(shore),Turf(water=1f),
                    fairwayDistance=shore+ClassicHole.ISLAND_ROUGH_WIDTH)
            }
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
    internal fun cupPatch(b:GroundBuilder,x0:Float,z0:Float,x1:Float,z1:Float,sampleAt:(Float,Float)->GroundSample) {
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
    internal fun cup(b:MeshBuilder) {
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
        if(hole.mini!=null) return MiniLandscape(hole,this).scenery()
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
        return (archipelago?.scenery ?: ClassicDecor(hole).build())+b.build()
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
        b.append(GolfBuildings.house(C(.56f,.55f,.50f),
            if(hole.snowy) C(.87f,.91f,.95f) else C(.23f,.28f,.30f),lodge=true),P(x,y,z),PI.toFloat())
        // A substantial stone landmark with a plinth, masonry belts and an inset doorway.
        val tx=millX; val tz=millZ; val ty=hole.heightAt(tx,tz)
        val stone=C(.46f,.47f,.44f)
        b.cone(tx,ty-.6f,tz,4.3f,1.2f,stone.shade(.8f),12,4.1f)
        b.cone(tx,ty+.6f,tz,3.9f,12f,stone,12,3.5f)
        for(h in listOf(1f,4f,8f,12.3f)) {
            val r=3.9f-(h-.6f)/12f*.4f
            b.cone(tx,ty+h,tz,r+.15f,.24f,stone.shade(1.13f),12,r+.15f)
        }
        b.box(tx,ty+.1f,tz-3.88f,1.55f,2.6f,.12f,C(.20f,.24f,.23f))
        for(h in listOf(5.1f,9f)) b.box(tx,ty+h,tz-3.77f,.55f,1.35f,.20f,C(.19f,.23f,.23f))
        for(i in 0..11) {
            val a=i*PI.toFloat()/6f
            b.box(tx+cos(a)*3.35f,ty+12.6f,tz+sin(a)*3.35f,.9f,1.35f,.9f,stone)
        }
    }

    private fun cottage(b:MeshBuilder,x:Float,z:Float) {
        b.append(GolfBuildings.house(C(.94f,.85f,.68f),C(.60f,.28f,.20f),lodge=true),
            P(x,hole.heightAt(x,z),z),PI.toFloat())
        for(side in intArrayOf(-1,1)) bush(b,x+side*8.3f,z-3.4f)
    }

    private fun mill(b:MeshBuilder) {
        val x=millX; val z=millZ; val y=hole.heightAt(x,z)
        val plaster=C(.90f,.86f,.73f); val stone=C(.55f,.52f,.44f)
        b.cone(x,y-.8f,z,4.1f,1.3f,stone,16,3.8f)
        b.cone(x,y+.5f,z,3.5f,13f,plaster,16,2.3f)
        for(h in listOf(.5f,4.5f,8.5f,13.3f)) {
            val r=3.5f-(h-.5f)/13f*1.2f
            b.cone(x,y+h,z,r+.10f,.18f,stone,16,r+.10f)
        }
        b.cone(x,y+13.5f,z,2.9f,3.1f,C(.48f,.26f,.20f),16)
        b.box(x,y+.35f,z-3.53f,1.7f,2.6f,.18f,stone.shade(.8f))
        b.box(x,y+.42f,z-3.64f,1.3f,2.30f,.08f,C(.30f,.24f,.18f))
        for(h in listOf(4.9f,8f)) {
            val front=z-(3.5f-(h-.5f)/13f*1.2f)
            b.box(x,y+h,front-.08f,1.15f,1.45f,.18f,stone)
            b.box(x,y+h+.12f,front-.20f,.86f,1.15f,.06f,C(.30f,.44f,.48f))
            b.box(x,y+h+.12f,front-.25f,.07f,1.15f,.06f,plaster)
            b.box(x,y+h+.67f,front-.25f,.86f,.07f,.06f,plaster)
        }
        b.beam(P(x,y+10.2f,z-2.5f),P(x,y+10.2f,z-3.0f),.30f,C(.30f,.25f,.20f),10)
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
        val wood=C(.36f,.28f,.19f); val canvas=C(.93f,.89f,.74f)
        for(i in 0..3) {
            val a=i*PI.toFloat()/2
            fun p(r:Float,w:Float,z:Float=0f)=P(cos(a)*r-sin(a)*w,sin(a)*r+cos(a)*w,z)
            b.beam(p(.2f,0f),p(7.2f,0f),.10f,wood,6)
            b.quad(p(2.7f,.13f),p(7.1f,.13f),p(7.1f,1.12f),p(2.7f,1.12f),canvas)
            for(r in listOf(2.7f,3.8f,4.9f,6f,7.1f)) b.beam(p(r,.10f,-.025f),p(r,1.16f,-.025f),.035f,wood,4)
            b.beam(p(2.7f,1.12f,-.025f),p(7.1f,1.12f,-.025f),.045f,wood,4)
        }
        b.sphere(0f,0f,0f,.40f,wood,10,5)
        return b.build()
    }
}
