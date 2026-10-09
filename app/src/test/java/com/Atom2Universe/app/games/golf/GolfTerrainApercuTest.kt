package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Test
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/** Atlas of all 108 outdoor holes, sampled from the same surfaces as ball collision.
 * ./gradlew testDebugUnitTest -PbancsMesure --tests "*GolfTerrainApercuTest"
 * Outputs stay in build/apercu/golf/terrain; no Android device required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GolfTerrainApercuTest {
    @Test fun watercourseCarries() {
        val folder=File("build/reports/golf-watercourses").also { it.mkdirs() }
        val report=StringBuilder("hole,target_x,target_z,club,power,state,lie,x,z,penalty\n")
        for(hole in WildDetoursCourse.holes.filter { it.number in listOf(2,6,15,18) }) for(target in hole.attackLandings) {
            for(percent in 65..100) {
                val game=ClassicGame(hole).apply {
                    restore(GolfPoint(target.fromX,0f,target.fromZ),0)
                    windX=0f;windZ=0f;club=target.club
                    aimAngle=atan2(target.x-target.fromX,target.z-target.fromZ);hit(percent/100f)
                }
                var steps=0
                while(game.state in listOf(GolfState.FLYING,GolfState.ROLLING) && steps++<2400)game.update(1f/60f)
                report.append("${hole.number},${target.x},${target.z},${target.club},$percent,${game.state},${game.lie},${game.ball.x},${game.ball.z},${game.lastPenalty}\n")
            }
        }
        File(folder,"carries.csv").writeText(report.toString())
    }

    @Test fun atlas() {
        val folder = File("build/apercu/golf/terrain").also { it.mkdirs() }
        val courses = listOf("jardins" to ClassicCourse.holes, "bruyere" to HeatherCourse.holes,
            "detours" to WildDetoursCourse.holes, "vertige" to VertigoCourse.holes,
            "archipel" to ArchipelagoCourse.holes, "sommets" to SnowPeaksCourse.holes)
        for ((name, holes) in courses) {
            val atlas = Bitmap.createBitmap(1440, 1200, Bitmap.Config.ARGB_8888)
            val g = Canvas(atlas)
            holes.forEachIndexed { index, hole ->
                g.drawBitmap(plan(hole, 240, 400), (index % 6 * 240).toFloat(), (index / 6 * 400).toFloat(), null)
            }
            save(atlas, File(folder, "$name.png"))
            println("TERRAIN $name: ${holes.size} holes -> ${File(folder, "$name.png")}")
        }
        for ((name, hole) in listOf("archipel-2" to ArchipelagoCourse.holes[1],
            "jardins-18" to ClassicCourse.holes[17], "detours-2" to WildDetoursCourse.holes[1],
            "detours-6" to WildDetoursCourse.holes[5], "vertige-2" to VertigoCourse.holes[1])) {
            save(plan(hole, 650, 900), File(folder, "$name.png"))
        }
    }

    private fun save(image: Bitmap, file: File) {
        FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun plan(hole: ClassicHole, w: Int, h: Int): Bitmap {
        val image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val scale = min((w - 12f) / hole.width, (h - 42f) / (hole.length + 65f))
        fun worldX(px: Int) = (px - w * .5f) / scale
        fun worldZ(py: Int) = (h - 12f - py) / scale - 20f
        for (py in 28 until h) for (px in 0 until w) {
            val x = worldX(px); val z = worldZ(py)
            val lie = hole.lieAt(x, z)
            val colour = when (lie) {
                GolfLie.OUT -> 0x263B34
                GolfLie.WATER -> 0x397E9C
                GolfLie.BUNKER -> 0xE7CE91
                GolfLie.GREEN -> 0xA8CB72
                GolfLie.FRINGE -> 0x88AE5B
                GolfLie.FAIRWAY, GolfLie.TEE -> 0x719B53
                GolfLie.SEMI_ROUGH -> 0x597B43
                GolfLie.ROUGH -> 0x425F39
            }
            // Terrain relief, lit from the north-west; lakes remain level and unshaded.
            val light = if (lie == GolfLie.WATER || lie == GolfLie.OUT) 1f else {
                val dx = (hole.heightAt(x + 1f, z) - hole.heightAt(x - 1f, z)) * .5f
                val dz = (hole.heightAt(x, z + 1f) - hole.heightAt(x, z - 1f)) * .5f
                (.58f + .52f * (.82f + .42f * dx - .38f * dz) / sqrt(1f + dx * dx + dz * dz)).coerceIn(.62f, 1.15f)
            }
            fun channel(shift: Int) = (((colour shr shift) and 255) * light).toInt().coerceIn(0, 255) shl shift
            pixels[py * w + px] = channel(16) or channel(8) or channel(0) or (0xFF shl 24)
        }
        image.setPixels(pixels, 0, w, 0, 0, w, h)
        val g = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun colour(rgb: Int) { paint.color = rgb or (0xFF shl 24) }
        fun px(x: Float) = w * .5f + x * scale
        fun py(z: Float) = h - 12f - (z + 20f) * scale
        colour(0x293F30)
        for (tree in hole.trees) g.drawCircle(px(tree.x), py(tree.z), max(1f, tree.radius * scale), paint)
        colour(0xFFFFFF)
        g.drawCircle(px(0f), py(0f), 3f, paint)
        colour(0xEDAD60)
        paint.style = Paint.Style.STROKE
        for (target in hole.attackLandings.distinctBy { it.x to it.z })
            g.drawCircle(px(target.x), py(target.z), 3f, paint)
        paint.style = Paint.Style.FILL
        colour(0xF9EDCB)
        val cx = px(hole.cup.x); val cz = py(hole.cup.z)
        g.drawLine(cx, cz, cx, cz - 9f, paint)
        g.drawRect(cx, cz - 9f, cx + 6f, cz - 5f, paint)
        colour(0x152A26)
        g.drawRect(0f, 0f, w.toFloat(), 27f, paint)
        colour(0xFFFFFF)
        paint.textSize = 13f
        g.drawText("${hole.number}   Par ${hole.par}   ${hole.length.toInt()} m", 9f, 18f, paint)
        return image
    }
}
