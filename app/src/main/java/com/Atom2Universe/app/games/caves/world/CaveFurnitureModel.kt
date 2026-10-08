package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FURNITURE_BOOKSHELF
import com.Atom2Universe.app.games.caves.node.FURNITURE_BARREL

/** Immutable 1/32-block furniture geometry. The occupied grid also removes every hidden face. */
internal object CaveFurnitureModel {
    data class Book(val x: Int, val y: Int, val width: Int, val height: Int, val front: Int, val color: Int)
    // Used by the texture recipe as well: spines always line up with the actual individual books.
    val books = listOf(
        Book(4,3,3,10,8,0), Book(8,3,4,11,7,1), Book(13,3,3,8,10,2),
        Book(17,3,4,10,8,3), Book(22,3,3,11,9,4), Book(26,3,2,9,7,5),
        Book(4,17,4,10,9,4), Book(9,17,3,8,7,0), Book(13,17,4,11,8,5),
        Book(18,17,3,10,10,2), Book(22,17,5,9,8,1))
    private const val N = 32
    private fun index(x: Int, y: Int, z: Int) = x + N * (y + N * z)
    private fun grid(shape: Int): IntArray {
        val cells = IntArray(N*N*N)
        fun box(x: Int,y: Int,z: Int,w: Int,h: Int,d: Int,material: Int = 1) {
            for (pz in z until z+d) for (py in y until y+h) for (px in x until x+w)
                cells[index(px,py,pz)] = material
        }
        if (shape == FURNITURE_BOOKSHELF) {
            // Facing zero opens toward -Z, matching the existing ORIENT_FACING convention.
            box(0,0,0,3,32,32); box(29,0,0,3,32,32)
            box(3,0,0,26,3,32); box(3,29,0,26,3,32); box(3,15,0,26,2,32)
            box(3,3,28,26,12,4); box(3,17,28,26,12,4)
            books.forEachIndexed { i,b -> box(b.x,b.y,b.front,b.width,b.height,28-b.front,i+2) }
        } else {
            require(shape == FURNITURE_BARREL)
            for(z in 0 until N) for(y in 0 until N) for(x in 0 until N) {
                val inset = when(y) { in 0..3,in 28..31 -> 4; in 4..7,in 24..27 -> 2; else -> 0 }
                if(x !in inset until N-inset || z !in inset until N-inset) continue
                // Eighth-block corner steps keep the curved silhouette inexpensive to mesh and collide.
                val corner = minOf(x-inset,N-1-inset-x)/4 + minOf(z-inset,N-1-inset-z)/4
                if(corner < if(inset==4) 1 else 2) continue
                // Both end disks sit one sixteenth below their surrounding wooden rim.
                val innerEnd = x in 8..23 && z in 8..23 && minOf(x-8,23-x)/4+minOf(z-8,23-z)/4>=1
                if(innerEnd && (y<2 || y>=30)) continue
                cells[index(x,y,z)] = 1
            }
        }
        return cells
    }
    private fun occupied(cells: IntArray,x: Int,y: Int,z: Int) =
        if(x in 0 until N && y in 0 until N && z in 0 until N) cells[index(x,y,z)] else 0

    /** Merge non-overlapping cuboids for collision and selection; no hull fills the shelf recess. */
    private fun boxes(cells: IntArray): List<PartialBlockModel.Box> {
        val remaining=cells.copyOf()
        return buildList {
            for(z in 0 until N) for(y in 0 until N) for(x in 0 until N) {
                val material=remaining[index(x,y,z)]
                if(material==0) continue
                var width=1
                while(x+width<N && remaining[index(x+width,y,z)]==material) width++
                var depth=1
                while(z+depth<N && (0 until width).all { remaining[index(x+it,y,z+depth)]==material }) depth++
                var height=1
                while(y+height<N && (0 until depth).all { dz ->
                        (0 until width).all { dx -> remaining[index(x+dx,y+height,z+dz)]==material } }) height++
                for(dz in 0 until depth) for(dy in 0 until height) for(dx in 0 until width)
                    remaining[index(x+dx,y+dy,z+dz)]=0
                add(PartialBlockModel.Box(x/32f,y/32f,z/32f,width/32f,height/32f,depth/32f))
            }
        }
    }
    private val normals=arrayOf(intArrayOf(0,1,0),intArrayOf(0,-1,0),intArrayOf(1,0,0),
        intArrayOf(-1,0,0),intArrayOf(0,0,1),intArrayOf(0,0,-1))
    private fun faces(cells: IntArray): List<PartialBlockModel.Face> = buildList {
        val mask=IntArray(N*N)
        for(face in 0..5) for(slice in 0 until N) {
            val normal=normals[face]
            for(b in 0 until N) for(a in 0 until N) {
                val x=when(face){0,1,4,5->a;else->slice}
                val y=if(face<2) slice else b
                val z=when(face){0,1->b;2,3->a;else->slice}
                val here=occupied(cells,x,y,z)
                mask[a+b*N]=if(here!=0 && occupied(cells,x+normal[0],y+normal[1],z+normal[2])==0) here else 0
            }
            for(b in 0 until N) for(a in 0 until N) {
                val material=mask[a+b*N]
                if(material==0) continue
                var width=1
                while(a+width<N && mask[a+width+b*N]==material) width++
                var height=1
                while(b+height<N && (0 until width).all { mask[a+it+(b+height)*N]==material }) height++
                for(db in 0 until height) for(da in 0 until width) mask[a+da+(b+db)*N]=0
                val box=when(face) {
                    0,1->PartialBlockModel.Box(a/32f,slice/32f,b/32f,width/32f,1/32f,height/32f)
                    2,3->PartialBlockModel.Box(slice/32f,b/32f,a/32f,1/32f,height/32f,width/32f)
                    else->PartialBlockModel.Box(a/32f,b/32f,slice/32f,width/32f,height/32f,1/32f)
                }
                val corners=Array(8) { i -> floatArrayOf(box.x+if(i and 1==0) 0f else box.width,
                    box.y+if(i and 2==0) 0f else box.height,box.z+if(i and 4==0) 0f else box.depth) }
                val vertices=TorchModel.faces[face].map { corners[it] }.toTypedArray()
                val book=material>=2
                val texture=if(book) 3 else when(face){0->0;1->1;else->2}
                val uv=vertices.map { v ->
                    if(book && face!=5) floatArrayOf((.5f+2f*(if(face<2) v[0] else v[2]))/32f,
                        if(face<2) v[2] else 1f-v[1]) // Page strip reserved at the left of texture_front.
                    else floatArrayOf(if(face==2 || face==3) v[2] else v[0],if(face<2) v[2] else 1f-v[1])
                }.toTypedArray()
                add(PartialBlockModel.Face(face,vertices,texture,uv))
            }
        }
    }
    private data class Model(val boxes: List<PartialBlockModel.Box>,val faces: List<PartialBlockModel.Face>)
    private fun point(v: FloatArray,turn: Int): FloatArray = when(turn) {
        1->floatArrayOf(1f-v[2],v[1],v[0]);2->floatArrayOf(1f-v[0],v[1],1f-v[2])
        3->floatArrayOf(v[2],v[1],1f-v[0]);else->v.copyOf()
    }
    private fun rotated(model: Model,turn: Int): Model {
        if(turn==0) return model
        val boxes=model.boxes.map { b ->
            val one=point(floatArrayOf(b.x,b.y,b.z),turn)
            val two=point(floatArrayOf(b.x+b.width,b.y+b.height,b.z+b.depth),turn)
            PartialBlockModel.Box(minOf(one[0],two[0]),b.y,minOf(one[2],two[2]),
                kotlin.math.abs(two[0]-one[0]),b.height,kotlin.math.abs(two[2]-one[2]))
        }
        val faces=model.faces.map { f ->
            var direction=f.direction
            repeat(turn){direction=when(direction){2->4;4->3;3->5;5->2;else->direction}}
            f.copy(direction=direction,vertices=f.vertices.map { point(it,turn) }.toTypedArray())
        }
        return Model(boxes,faces)
    }
    private val models = Array(2) { i ->
        val cells=grid(i+1);val base=Model(boxes(cells),faces(cells))
        Array(4){meta->rotated(base,when(meta){1->2;2->1;3->3;else->0})}
    }
    fun boxes(shape: Int,meta: Byte) = models[shape-1][meta.toInt() and 3].boxes
    fun faces(shape: Int,meta: Byte) = models[shape-1][meta.toInt() and 3].faces
}
