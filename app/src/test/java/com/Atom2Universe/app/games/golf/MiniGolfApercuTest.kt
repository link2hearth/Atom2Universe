package com.Atom2Universe.app.games.golf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.Atom2Universe.app.games.golf.ClassicGolfApercuTest.Raster
import com.Atom2Universe.app.games.golf.ClassicGolfApercuTest.Vec
import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.C
import com.Atom2Universe.app.games.golf.classic.render.ClassicLandscape
import com.Atom2Universe.app.games.golf.classic.render.ClassicMesh
import com.Atom2Universe.app.games.golf.classic.render.MeshBuilder
import com.Atom2Universe.app.games.golf.classic.render.MiniScene
import com.Atom2Universe.app.games.golf.classic.render.OverviewView
import com.Atom2Universe.app.games.golf.classic.render.P
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/**
 * Montre le mini-golf sans tablette : les vrais maillages des trous (sol, rebords, obstacles à l'instant
 * choisi), la balle, le trou et le guide, vus par la caméra du jeu, dans `app/build/apercu/minigolf/`.
 * Le sol est texturé par la version simplifiée du shader de `ClassicGolfApercuTest`.
 *
 * Il ne vérifie rien : banc d'aperçu, exclu de la suite par défaut.
 * `./gradlew testDebugUnitTest -PbancsMesure --tests "*MiniGolfApercuTest"`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniGolfApercuTest {
    private val dossier = File("build/apercu/minigolf").also { it.mkdirs() }
    private val w = 360
    private val h = 640

    @Test fun classique() {
        for (number in listOf(1, 5, 6, 10, 13, 16, 18)) trou(MiniGolfCourse.holes[number - 1], "classique")
    }

    @Test fun dejante() {
        for (number in listOf(1, 2, 3, 4, 9, 12, 13, 18)) trou(MiniGolfCrazyCourse.holes[number - 1], "dejante")
    }

    private fun trou(hole: ClassicHole, prefix: String) {
        val landscape = ClassicLandscape(hole)
        val ground = field(landscape.terrain())
        val plain = landscape.scenery().map { vertices(it) }
        val layout = hole.mini!!
        // The moving parts, frozen at a moment where they are not on the tee's line.
        val moving = obstacles(hole, MiniScene(hole), 1.1f)
        val tee = ClassicGame(hole)
        val aiming = ClassicGame(hole)
        val b = layout.bounds
        val view = OverviewView((b[0] + b[2]) * .5f, (b[1] + b[3]) * .5f, max(b[2] - b[0], b[3] - b[1]) * 1.25f + 5f, 0f)
        val eye = view.eye(hole); val look = view.look(hole)
        val views = listOf(
            "départ" to Triple(tee, camera(tee, 0f), false),
            "visée" to Triple(aiming, camera(aiming, .5f), true),
            "aérien" to Triple(tee, Vec(eye.x, eye.y, eye.z) to Vec(look.x, look.y, look.z), false),
        )
        sheet("$prefix-${hole.number}", views.map { (label, v) ->
            label to render(hole, ground, plain + moving, v.first, v.second, v.third)
        })
    }

    /** Same placement as ClassicRenderer.camera for a putt in a mini-golf hole. */
    private fun camera(game: ClassicGame, power: Float): Pair<Vec, Vec> {
        val hole = game.hole
        val b = game.ball
        val sx = sin(game.aimAngle); val sz = cos(game.aimAngle)
        val reach = if (power > 0f) game.preview(power).landing?.let { hypot(it.x - b.x, it.z - b.z) } ?: 0f else 0f
        val slide = max(0f, reach - 2.5f)
        val behind = 2.7f + slide * .15f
        val high = 2.8f + slide * .2f
        val ahead = 3.2f + slide
        val ex = b.x + sx * (slide - behind); val ez = b.z + sz * (slide - behind)
        val lx = b.x + sx * ahead; val lz = b.z + sz * ahead
        return Vec(ex, max(b.y + high, hole.heightAt(ex, ez) + 1f), ez) to Vec(lx, hole.heightAt(lx, lz) + .1f, lz)
    }

    private fun render(hole: ClassicHole, ground: FloatArray, plain: List<FloatArray>, game: ClassicGame,
                       camera: Pair<Vec, Vec>, guide: Boolean): Bitmap {
        val r = Raster(w, h)
        r.camera(camera.first, camera.second)
        r.ground(ground)
        plain.forEach { r.plain(it) }
        val extras = MeshBuilder()
        val ball = game.ball
        extras.sphere(ball.x, ball.y, ball.z, ClassicHole.BALL_RADIUS * 1.6f, C(1f, .99f, .92f), 14, 8)
        val cy = hole.heightAt(hole.cup.x, hole.cup.z)
        val near = hypot(hole.cup.x - camera.first.x, hole.cup.z - camera.first.z)
        val s = max(near * .03f, min(1f, near * .15f))
        extras.cone(hole.cup.x, cy + .45f * s, hole.cup.z, .014f * s, .5f * s, C(.98f, .97f, .92f), 12, .21f * s)
        extras.cone(hole.cup.x, cy + .95f * s, hole.cup.z, .21f * s, .08f * s, C(1f, .34f, .22f), 12, .21f * s)
        if (guide && game.state == GolfState.READY) {
            val shot = game.preview(.5f)
            shot.flight.zipWithNext().forEachIndexed { i, (a, c) ->
                if (i % 2 != 0) return@forEachIndexed
                val dx = c.x - a.x; val dz = c.z - a.z; val l = hypot(dx, dz).coerceAtLeast(1e-4f)
                val ox = -dz / l * .012f; val oz = dx / l * .012f
                extras.quad(P(a.x - ox, a.y + .02f, a.z - oz), P(c.x - ox, c.y + .02f, c.z - oz),
                    P(c.x + ox, c.y + .02f, c.z + oz), P(a.x + ox, a.y + .02f, a.z + oz), C(1f, .95f, .67f), false)
            }
        }
        // A stand-in for the player.
        val sx = sin(game.aimAngle); val sz = cos(game.aimAngle)
        val gx = ball.x - sz * .983f; val gz = ball.z + sx * .983f
        extras.box(gx, 0f, gz, .5f, .85f, .3f, C(.16f, .30f, .43f))
        extras.sphere(gx, 1.12f, gz, .34f, C(.22f, .57f, .72f), 9, 5, 1.1f)
        extras.sphere(gx, 1.61f, gz, .21f, C(.95f, .72f, .49f), 9, 5, 1.12f)
        r.plain(vertices(extras.build()))
        return r.bitmap()
    }

    /** The meshes of the moving obstacles, placed as the renderer places them at [clock]. */
    private fun obstacles(hole: ClassicHole, scene: MiniScene, clock: Float): List<FloatArray> {
        fun place(mesh: ClassicMesh, x: Float, y: Float, z: Float, turn: Float): FloatArray {
            val v = vertices(mesh).copyOf()
            val c = cos(turn); val s = sin(turn)
            for (i in v.indices step 6) {
                val px = v[i]; val pz = v[i + 2]
                v[i] = x + px * c - pz * s; v[i + 1] = y + v[i + 1]; v[i + 2] = z + px * s + pz * c
            }
            return v
        }
        return scene.rotors.map { (rotor, mesh) -> place(mesh, rotor.x, hole.heightAt(rotor.x, rotor.z), rotor.z, rotor.angle(clock)) } +
            scene.sliders.map { (slider, mesh) ->
                val x = slider.centreX(clock); val z = slider.centreZ(clock)
                place(mesh, x, hole.heightAt(x, z), z, 0f)
            } +
            scene.wells.map { (well, mesh) -> place(mesh, well.x, hole.heightAt(well.x, well.z), well.z, 0f) }
    }

    private fun sheet(name: String, images: List<Pair<String, Bitmap>>) {
        val sheet = Bitmap.createBitmap(w * images.size, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; isFakeBoldText = true }
        images.forEachIndexed { index, (label, image) ->
            val ox = index * w.toFloat()
            canvas.drawBitmap(image, ox, 0f, null)
            paint.color = Color.BLACK; canvas.drawText(label, ox + 11f, 25f, paint)
            paint.color = Color.WHITE; canvas.drawText(label, ox + 10f, 24f, paint)
        }
        FileOutputStream(File(dossier, "$name.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun vertices(mesh: ClassicMesh): FloatArray = field(mesh)

    private fun field(mesh: Any): FloatArray =
        mesh.javaClass.getDeclaredField("vertices").apply { isAccessible = true }.get(mesh) as FloatArray
}
