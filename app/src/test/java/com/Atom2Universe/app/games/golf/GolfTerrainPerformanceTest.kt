package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.*
import org.junit.Test
import java.io.File
import kotlin.math.*

/** Explicit CPU benchmark; run with -PbancsMesure and compare reports on the same machine. */
class GolfTerrainPerformanceTest {
    private var sink = 0f
    @Test fun loadingAndAiming() {
        val report = StringBuilder("course,hole,height_100k_ms,preview_median_ms,preview_max_ms,terrain_ms,terrain_triangles,scenery_ms,scenery_triangles\n")
        for ((name, hole) in listOf("vertige" to VertigoCourse.holes[1], "detours" to WildDetoursCourse.holes[1],
            "archipel" to ArchipelagoCourse.holes[1], "jardins" to ClassicCourse.holes[17])) {
            fun sampleHeights() {
                for (i in 0 until 100000) {
                    val x = ((i * 137 % 1000) / 1000f - .5f) * hole.width
                    val z = (i * 73 % 1000) / 1000f * hole.length
                    sink += hole.heightAt(x, z)
                }
            }
            repeat(2) { sampleHeights() }
            val heightMs = ms { sampleHeights() }
            val game = ClassicGame(hole)
            val previews = mutableListOf<Double>()
            val positions = listOf(hole.tee, GolfPoint(hole.fairwayCenter(hole.landingZ), 0f, hole.landingZ),
                GolfPoint(-47.974598f, 0f, 368.97092f))
            for (position in positions) {
                game.restore(position, 1); game.club = GolfClub.DRIVER
                repeat(18) { i ->
                    game.aimAngle = -.4f + i * .045f
                    val time = ms { sink += game.preview(.85f).carry }
                    if (i >= 3) previews += time
                }
            }
            val landscape = ClassicLandscape(hole)
            var triangles = 0; var scenery = 0
            val terrainMs = ms { triangles = landscape.terrain().count / 3 }
            val sceneryMs = ms { scenery = landscape.scenery().sumOf { it.count / 3 } }
            report.append("$name,${hole.number},$heightMs,${previews.sorted()[previews.size/2]},${previews.max()},$terrainMs,$triangles,$sceneryMs,$scenery\n")
        }
        File("build/reports/golf-performance").mkdirs()
        File("build/reports/golf-performance/latest.csv").writeText(report.toString())
        println(report)
        check(sink.isFinite())
    }
    private fun ms(block: () -> Unit): Double {
        val started = System.nanoTime(); block()
        return (System.nanoTime() - started) / 1e6
    }
}
