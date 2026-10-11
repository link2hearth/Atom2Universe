package com.Atom2Universe.app.science.parentes

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.hypot
import kotlin.random.Random

class TreeZoomTest {
    @Test fun leZoomMaximalSepareMemeLesVoisinsDuGrandArbre() {
        val archive=LifeArchive.read(File("src/main/assets/parentes/runtime.bin").inputStream())
        val tree=LifeTree(archive.nodes,archive.root,emptySet())
        val positions=TreeLayout.radial(tree.atlas(tree.root))
        val points=positions.values.map{TreeZoom.Point(it.x,it.y)}
        val spacing=TreeZoom.nearestSpacing(points)
        val limit=TreeZoom.limit(points,650f,32f)
        assertTrue("Old x4 limit could not separate neighbours",limit>4f)
        assertTrue(spacing*limit>hypot(650f,32f))
        assertTrue(limit.isFinite())
        // Pure zoom must not remove, insert or relocate any source node.
        assertEquals(tree.atlas(tree.root).parents.keys,positions.keys)
    }
    @Test fun rechercheDuPlusProchePaireCompareeAuCalculExhaustif() {
        val random=Random(27)
        repeat(15) {
            val points=List(160){TreeZoom.Point(random.nextFloat()*1000,random.nextFloat()*1000)}
            val expected=points.indices.minOf { i-> ((i+1) until points.size).minOfOrNull { j->hypot(points[i].x-points[j].x,points[i].y-points[j].y) } ?: Float.POSITIVE_INFINITY }
            assertEquals(expected,TreeZoom.nearestSpacing(points),.00001f)
        }
        assertEquals(1f,TreeZoom.nearestSpacing(List(10000){TreeZoom.Point(0f,it.toFloat())}),0f)
        assertEquals(4f,TreeZoom.limit(listOf(TreeZoom.Point(0f,0f),TreeZoom.Point(0f,0f)),400f,30f),0f)
    }
    @Test fun lePincementGardeSonAncrageEtSuitLeDeplacementDesDeuxDoigts() {
        val before=TreeZoom.Camera(.4f,30f,-10f)
        val after=before.focus(120f,100f,200f,130f,250f)
        val worldX=(100f-before.x)/before.zoom;val worldY=(200f-before.y)/before.zoom
        assertEquals(130f,worldX*after.zoom+after.x,.01f)
        assertEquals(250f,worldY*after.zoom+after.y,.01f)
        val pan=before.focus(before.zoom,100f,200f,130f,250f)
        assertEquals(before.x+30f,pan.x,.01f)
        assertEquals(before.y+50f,pan.y,.01f)
    }
}
