package com.Atom2Universe.app.games.golf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.C
import com.Atom2Universe.app.games.golf.classic.render.ClassicLandscape
import com.Atom2Universe.app.games.golf.classic.render.ClassicMesh
import com.Atom2Universe.app.games.golf.classic.render.GroundMesh
import com.Atom2Universe.app.games.golf.classic.render.MeshBuilder
import com.Atom2Universe.app.games.golf.classic.render.OverviewView
import com.Atom2Universe.app.games.golf.classic.render.P
import com.Atom2Universe.app.games.golf.classic.render.SlopeBoard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/**
 * Montre le golf classique sans tablette : les vrais maillages du parcours, la balle, le trou,
 * le drapeau et le guide, vus par la caméra du jeu, dans `app/build/apercu/golf/`. Le sol est
 * texturé pixel par pixel avec une version simplifiée en Kotlin du shader `GroundShader`
 * (tonte, damier, touffes, sable ; ni ombres de nuages, ni fibres, ni reflets d'eau).
 *
 * Il ne vérifie rien : banc d'aperçu, exclu de la suite par défaut.
 * `./gradlew testDebugUnitTest -PbancsMesure --tests "*ClassicGolfApercuTest"`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClassicGolfApercuTest {
    private val dossier = File("build/apercu/golf").also { it.mkdirs() }
    private val w = 360
    private val h = 760

    @Test
    fun vues() {
        for (number in listOf(1, 16)) vues(ClassicCourse.holes[number - 1])
    }

    @Test
    fun bruyere() {
        for (number in listOf(3, 5, 6, 8, 14, 18)) vues(HeatherCourse.holes[number - 1], "bruyere-")
    }

    @Test
    fun jardinsFaciles() {
        for (number in listOf(1, 3, 4, 7, 13, 16, 18)) vues(ClassicCourse.holes[number - 1], "jardins-faciles-")
    }

    @Test
    fun green() {
        val hole = ClassicCourse.holes[0]
        val landscape = ClassicLandscape(hole)
        val ground = groundVertices(landscape.terrain())
        val cup = hole.cup
        val putt = ClassicGame(hole).apply { restore(GolfPoint(cup.x - 1.5f, 0f, cup.z - 4f), 2) }
        val plain = landscape.scenery().map { vertices(it) }
        val holed = ClassicGame(hole).apply {
            restore(GolfPoint(cup.x, 0f, cup.z - 1f), 2); aimAngle = 0f
            hit(1.6f / GolfClub.PUTTER.carry)
            repeat(1200) { if (state == GolfState.ROLLING) update(1f / 120f) }
        }
        val dropping = ClassicGame(hole).apply {
            restore(GolfPoint(cup.x, 0f, cup.z - 1f), 2); aimAngle = 0f
            hit(1.6f / GolfClub.PUTTER.carry)
            // Stop the film as the ball falls past the lip.
            repeat(4000) { if (state == GolfState.ROLLING && ball.y > cup.y - .012f) update(1f / 960f) }
        }
        println("APERCU holed=${holed.state} ball=${holed.ball} dropping=${dropping.state} ball=${dropping.ball} cup=$cup")
        val views = listOf(
            Triple("putt", putt, camera(putt)),
            Triple("putt, jauge", putt, aimingCamera(putt)),
            Triple("trou, de près", holed, Vec(cup.x - .3f, cup.y + .34f, cup.z - .45f) to Vec(cup.x, cup.y - .03f, cup.z)),
            Triple("balle qui tombe", dropping, Vec(cup.x + .32f, cup.y + .16f, cup.z - .26f) to Vec(cup.x, cup.y - .02f, cup.z)),
            Triple("green vu d'en haut", putt, Vec(cup.x - 4f, cup.y + 9f, cup.z - 9f) to Vec(cup.x, cup.y, cup.z)),
            // The same view slid 1.3 m sideways: the squares stay put, the window moves over them.
            Triple("d'en haut, glissé", putt, Vec(cup.x - 5.3f, cup.y + 9f, cup.z - 9f) to Vec(cup.x - 1.3f, cup.y, cup.z)),
        )
        sheet("green", views.map { (label, game, camera) ->
            label to render(hole, ground, plain, game, camera, showGuide = label.startsWith("putt"), aiming = label.contains("jauge"),
                aerial = label.contains("d'en haut"))
        })
    }

    private fun vues(hole: ClassicHole, prefix: String = "") {
        val landscape = ClassicLandscape(hole)
        val ground = groundVertices(landscape.terrain())
        val plain = landscape.scenery().map { vertices(it) }
        val tee = ClassicGame(hole)
        val putt = ClassicGame(hole).apply { restore(GolfPoint(hole.cup.x - 2f, 0f, hole.cup.z - 6f), 2) }
        val flight = ClassicGame(hole).apply { hit(.9f); repeat(150) { update(1f / 60f) } }
        val approachZ = if (hole.par == 3) hole.length - 35f else min(220f, hole.length - 45f)
        val approach = ClassicGame(hole).apply { restore(GolfPoint(hole.fairwayCenter(approachZ), 0f, approachZ), 1) }
        fun aerial(game: ClassicGame): Pair<Vec, Vec> {
            val end = game.preview(power(game)).landing ?: game.ball
            val span = hypot(end.x - game.ball.x, end.z - game.ball.z)
            val view = OverviewView((game.ball.x + end.x) * .5f, (game.ball.z + end.z) * .5f,
                (span * 1.3f + 10f).coerceIn(OverviewView.MIN_DISTANCE * 2f, OverviewView.MAX_DISTANCE), game.aimAngle)
            val e = view.eye(hole); val l = view.look(hole)
            return Vec(e.x, e.y, e.z) to Vec(l.x, l.y, l.z)
        }
        val views = listOf("départ" to (tee to camera(tee)), "visée 80 %" to (tee to aimingCamera(tee)),
            "approche" to (approach to camera(approach)), "putt" to (putt to camera(putt)),
            "en vol" to (flight to camera(flight)), "aérien départ" to (tee to aerial(tee)),
            "aérien putt" to (putt to aerial(putt)), "aérien approche" to (approach to aerial(approach)))
        sheet("${prefix}trou${hole.number}", views.map { (label, pair) ->
            label to render(hole, ground, plain, pair.first, pair.second, showGuide = true, aiming = label.startsWith("visée"),
                aerial = label.startsWith("aérien"))
        })
    }

    private fun sheet(name: String, images: List<Pair<String, Bitmap>>) {
        val columns = 4
        val rows = (images.size + columns - 1) / columns
        val sheet = Bitmap.createBitmap(w * columns, h * rows, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; isFakeBoldText = true }
        images.forEachIndexed { index, (label, image) ->
            val ox = (index % columns) * w.toFloat(); val oy = (index / columns) * h.toFloat()
            canvas.drawBitmap(image, ox, oy, null)
            paint.color = Color.BLACK; canvas.drawText(label, ox + 11f, oy + 25f, paint)
            paint.color = Color.WHITE; canvas.drawText(label, ox + 10f, oy + 24f, paint)
        }
        FileOutputStream(File(dossier, "$name.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(hole: ClassicHole, ground: FloatArray, plain: List<FloatArray>, game: ClassicGame,
                       camera: Pair<Vec, Vec>, showGuide: Boolean, aiming: Boolean = false, aerial: Boolean = false): Bitmap {
        val r = Raster(w, h)
        r.camera(camera.first, camera.second)
        // The slope board, as in ClassicRenderer: only while the shot is prepared; from above around
        // the point looked at, else for a putt.
        val putting = game.club == GolfClub.PUTTER
        val (eye, look) = camera
        val board = if (game.state != GolfState.READY) null else when {
            aerial -> SlopeBoard.around(hole, look.x, look.z, sqrt((look.x - eye.x).pow(2) + (look.y - eye.y).pow(2) + (look.z - eye.z).pow(2)),
                if (putting) hole.heightAt(game.ball.x, game.ball.z) else null)
            putting -> SlopeBoard.forPutt(hole, game.ball, game.preview(if (aiming) gauge(game) else power(game)).landing)
            else -> null
        }
        val lights = MeshBuilder()
        if (board != null) {
            val mx = board.middleX; val mz = board.middleZ
            val (cell, fine) = SlopeBoard.cell(sqrt((mx - eye.x).pow(2) + (hole.heightAt(mx, mz) - eye.y).pow(2) + (mz - eye.z).pow(2)), board)
            r.board = board; r.cell = cell; r.fineWeight = fine; r.holeX = hole.cup.x; r.holeZ = hole.cup.z
            val l = board.lights(hole, if (fine >= .5f) cell else cell * 2f)
            for (i in l.indices step 9) {
                val t = l[i + 7]
                val x = l[i] + (l[i + 3] - l[i]) * t; val y = l[i + 1] + (l[i + 4] - l[i + 1]) * t; val z = l[i + 2] + (l[i + 5] - l[i + 2]) * t
                lights.sphere(x, y + .012f + cell * .006f, z, .02f * cell, C(1f, 1f, .9f), 6, 4)
            }
        }
        r.ground(ground)
        r.plain(vertices(lights.build()))
        plain.forEach { r.plain(it) }
        val extras = MeshBuilder()
        if (showGuide && game.state == GolfState.READY) guide(extras, hole, game.preview(if (aiming) gauge(game) else power(game)), eye)
        val ball = game.ball
        val distance = sqrt((ball.x - eye.x).pow(2) + (ball.y - eye.y).pow(2) + (ball.z - eye.z).pow(2))
        val sunk = ball.y < hole.heightAt(ball.x, ball.z) + ClassicHole.BALL_RADIUS - .002f
        val radius = if (sunk) ClassicHole.BALL_RADIUS else max(ClassicHole.BALL_RADIUS, distance * .0026f)
        val lift = if (sunk) 0f else if (hole.lieAt(ball.x, ball.z) == GolfLie.GREEN) .002f else .012f
        extras.sphere(ball.x, ball.y + radius - ClassicHole.BALL_RADIUS + lift, ball.z, radius, C(1f, .99f, .92f), 14, 8)
        val flagScale = max(1f, hypot(hole.cup.x - eye.x, hole.cup.z - eye.z) * .012f)
        val cy = hole.heightAt(hole.cup.x, hole.cup.z)
        // The flagstick is out for putts, as in the game.
        if (game.club != GolfClub.PUTTER) extras.cone(hole.cup.x, cy - ClassicHole.CUP_DEPTH * flagScale, hole.cup.z, ClassicHole.PIN_RADIUS * flagScale,
            (2.4f + ClassicHole.CUP_DEPTH) * flagScale, C(.98f, .97f, .90f), 6, ClassicHole.PIN_RADIUS * flagScale)
        if (game.club != GolfClub.PUTTER) extras.tri(P(hole.cup.x, cy + 2.4f * flagScale, hole.cup.z), P(hole.cup.x + .95f * flagScale, cy + 2.12f * flagScale, hole.cup.z),
            P(hole.cup.x, cy + 1.82f * flagScale, hole.cup.z), C(1f, .34f, .22f), false)
        if (game.club == GolfClub.PUTTER && !sunk) {
            val near = hypot(hole.cup.x - eye.x, hole.cup.z - eye.z)
            val s = max(near * .03f, min(1f, near * .15f))
            extras.cone(hole.cup.x, cy + .45f * s, hole.cup.z, .014f * s, .5f * s, C(.98f, .97f, .92f), 12, .21f * s)
            extras.cone(hole.cup.x, cy + .95f * s, hole.cup.z, .21f * s, .08f * s, C(1f, .34f, .22f), 12, .21f * s)
        }
        if (game.state == GolfState.READY) golfer(extras, hole, game)
        r.plain(vertices(extras.build()))
        return r.bitmap()
    }

    /** Same placement as ClassicRenderer.camera, without its smoothing. */
    private fun camera(game: ClassicGame): Pair<Vec, Vec> {
        val hole = game.hole
        val b = game.ball
        val sx = sin(game.aimAngle); val sz = cos(game.aimAngle)
        val putting = game.club == GolfClub.PUTTER
        val flying = game.state == GolfState.FLYING || game.state == GolfState.ROLLING
        val behind = if (putting) 3.4f else if (flying) 15f else 7.5f
        val high = if (putting) 1.45f else if (flying) 5.5f else 3.6f
        val ahead = if (putting) 2.5f else if (flying) 8f else (game.club.carry * .3f).coerceIn(6f, 60f)
        val ex = b.x - sx * behind; val ez = b.z - sz * behind
        val ey = max(b.y + high, hole.heightAt(ex, ez) + 1f)
        val lx = b.x + sx * ahead; val lz = b.z + sz * ahead
        val ly = if (flying && !putting) b.y + .5f else hole.heightAt(lx, lz) + if (putting) .1f else 0f
        return Vec(ex, ey, ez) to Vec(lx, ly, lz)
    }

    private fun aimingCamera(game: ClassicGame): Pair<Vec, Vec> {
        val hole = game.hole
        val landing = game.preview(gauge(game)).landing ?: return camera(game)
        val sx = sin(game.aimAngle); val sz = cos(game.aimAngle)
        val reach = hypot(landing.x - game.ball.x, landing.z - game.ball.z)
        if (game.club == GolfClub.PUTTER) {
            // As ClassicRenderer: the putt view slides down the line towards where the ball would stop.
            val b = game.ball
            val slide = max(0f, reach - 2.5f)
            val back = slide - (3.4f + slide * .15f)
            val ex = b.x + sx * back; val ez = b.z + sz * back
            val lx = b.x + sx * (2.5f + slide); val lz = b.z + sz * (2.5f + slide)
            return Vec(ex, max(b.y + 1.45f + slide * .2f, hole.heightAt(ex, ez) + 1f), ez) to Vec(lx, hole.heightAt(lx, lz) + .1f, lz)
        }
        val back = (reach * .26f).coerceIn(20f, 60f); val up = (reach * .1f).coerceIn(10f, 24f)
        val tx = landing.x - sx * back; val tz = landing.z - sz * back
        val lx = landing.x + sx * reach * .08f; val lz = landing.z + sz * reach * .08f
        return Vec(tx, max(landing.y + up, hole.heightAt(tx, tz) + 4f), tz) to Vec(lx, hole.heightAt(lx, lz), lz)
    }

    private fun power(game: ClassicGame) = if (game.club == GolfClub.PUTTER) .3f else 1f

    /** Power shown while drawing the shot back: 80 % of a full shot, or a putt a little past the hole. */
    private fun gauge(game: ClassicGame) = if (game.club == GolfClub.PUTTER) .35f else .8f

    private fun guide(m: MeshBuilder, hole: ClassicHole, shot: ShotPreview, eye: Vec) {
        val end = shot.landing ?: return
        fun ribbon(a: GolfPoint, b: GolfPoint, colour: C) {
            val width = max(.006f, hypot(a.x - eye.x, a.z - eye.z) * .003f)
            val dx = b.x - a.x; val dz = b.z - a.z; val l = hypot(dx, dz).coerceAtLeast(1e-4f)
            val ox = -dz / l * width; val oz = dx / l * width
            m.quad(P(a.x - ox, a.y + .01f, a.z - oz), P(b.x - ox, b.y + .01f, b.z - oz), P(b.x + ox, b.y + .01f, b.z + oz), P(a.x + ox, a.y + .01f, a.z + oz), colour, false)
        }
        shot.flight.zipWithNext().forEachIndexed { i, (a, b) -> if (i % 2 == 0) ribbon(a, b, C(1f, .95f, .67f)) }
        shot.roll.zipWithNext().forEachIndexed { i, (a, b) -> if (i % 2 == 0) ribbon(a, b, C(.8f, .92f, .98f)) }
        if (shot.roll.isEmpty() && shot.carry <= 30f) {
            // The ring where a putt would stop, as drawPreview.
            val ring = (0..24).map { i ->
                val a = i * 2f * PI.toFloat() / 24f
                val x = end.x + cos(a) * .22f; val z = end.z + sin(a) * .22f
                GolfPoint(x, hole.heightAt(x, z) + .04f, z)
            }
            ring.zipWithNext().forEach { (a, b) -> ribbon(a, b, C(1f, .93f, .55f)) }
        }
        if (shot.flight.size > 2 && shot.carry > 30f) {
            val s = max(1f, sqrt((end.x - eye.x).pow(2) + (end.y - eye.y).pow(2) + (end.z - eye.z).pow(2)) * .016f)
            val y = hole.heightAt(end.x, end.z) + .04f
            m.cone(end.x, y, end.z, .03f * s, .45f * s, C(.8f, .69f, .28f), 6, .03f * s)
            m.cone(end.x, y + .45f * s, end.z, .02f * s, .85f * s, C(1f, .86f, .35f), 10, .34f * s)
        }
    }

    /** A stand-in for the player at address (the real rig lives in GolferRenderer). */
    private fun golfer(m: MeshBuilder, hole: ClassicHole, game: ClassicGame) {
        val sx = sin(game.aimAngle); val sz = cos(game.aimAngle)
        val gx = game.ball.x - sz * .983f; val gz = game.ball.z + sx * .983f
        val gy = hole.heightAt(gx, gz)
        m.box(gx, gy, gz, .5f, .85f, .3f, C(.16f, .30f, .43f))
        m.sphere(gx, gy + 1.12f, gz, .34f, C(.22f, .57f, .72f), 9, 5, 1.1f)
        m.sphere(gx, gy + 1.61f, gz, .21f, C(.95f, .72f, .49f), 9, 5, 1.12f)
    }

    private fun vertices(mesh: ClassicMesh): FloatArray =
        ClassicMesh::class.java.getDeclaredField("vertices").apply { isAccessible = true }.get(mesh) as FloatArray

    private fun groundVertices(mesh: GroundMesh): FloatArray =
        GroundMesh::class.java.getDeclaredField("vertices").apply { isAccessible = true }.get(mesh) as FloatArray

    data class Vec(val x: Float, val y: Float, val z: Float)

    /**
     * Small perspective rasteriser with the game's projection (50°, image raised by 0.12) and smooth
     * colours. Ground fragments go through [shade], a simplified copy of GroundShader.
     */
    private class Raster(val width: Int, val height: Int) {
        private val colour = IntArray(width * height) { 0xFFA3D9ED.toInt() }
        private val depth = FloatArray(width * height) { Float.MAX_VALUE }
        private val view = FloatArray(12)
        private var eye = Vec(0f, 0f, 0f)
        private val focal = (height / 2f) / tan(Math.toRadians(25.0).toFloat())
        private val near = .03f
        var board: SlopeBoard? = null
        var cell = 1f; var fineWeight = 1f; var holeX = 0f; var holeZ = 0f
        private val slopeOnScreen = FloatArray(4)

        fun camera(from: Vec, to: Vec) {
            eye = from
            var fx = to.x - from.x; var fy = to.y - from.y; var fz = to.z - from.z
            val fl = sqrt(fx * fx + fy * fy + fz * fz); fx /= fl; fy /= fl; fz /= fl
            // side = forward × up, up' = side × forward (as gluLookAt).
            var sx = -fz; var sy = 0f; var sz = fx
            val sl = sqrt(sx * sx + sy * sy + sz * sz); sx /= sl; sy /= sl; sz /= sl
            val ux = sy * fz - sz * fy; val uy = sz * fx - sx * fz; val uz = sx * fy - sy * fx
            floatArrayOf(sx, sy, sz, -(sx * from.x + sy * from.y + sz * from.z),
                ux, uy, uz, -(ux * from.x + uy * from.y + uz * from.z),
                -fx, -fy, -fz, fx * from.x + fy * from.y + fz * from.z).copyInto(view)
        }

        fun plain(v: FloatArray) = triangles(v, 6, false)
        fun ground(v: FloatArray) = triangles(v, 10, true)

        private fun triangles(v: FloatArray, stride: Int, textured: Boolean) {
            var i = 0
            val poly = ArrayList<FloatArray>(4)
            while (i + stride * 3 <= v.size) {
                // Camera-space x, y, z, then the vertex itself (world position and attributes).
                val tri = Array(3) { k ->
                    val o = i + k * stride
                    val x = v[o]; val y = v[o + 1]; val z = v[o + 2]
                    floatArrayOf(view[0] * x + view[1] * y + view[2] * z + view[3],
                        view[4] * x + view[5] * y + view[6] * z + view[7],
                        view[8] * x + view[9] * y + view[10] * z + view[11]) + v.copyOfRange(o, o + stride)
                }
                poly.clear()
                for (k in 0 until 3) {
                    val a = tri[k]; val b = tri[(k + 1) % 3]
                    val aIn = a[2] <= -near; val bIn = b[2] <= -near
                    if (aIn) poly += a
                    if (aIn != bIn) {
                        val t = (-near - a[2]) / (b[2] - a[2])
                        poly += FloatArray(a.size) { a[it] + (b[it] - a[it]) * t }
                    }
                }
                for (k in 1 until poly.size - 1) raster(poly[0], poly[k], poly[k + 1], textured)
                i += stride * 3
            }
        }

        private fun raster(a: FloatArray, b: FloatArray, c: FloatArray, textured: Boolean) {
            // The game's projection raises the picture by 0.12 in normalised coordinates.
            fun sx(p: FloatArray) = width / 2f + p[0] / -p[2] * focal
            fun sy(p: FloatArray) = height / 2f - (p[1] / -p[2] * focal + .12f * height / 2f)
            val ax = sx(a); val ay = sy(a); val bx = sx(b); val by = sy(b); val cx = sx(c); val cy = sy(c)
            val area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
            if (abs(area) < 1e-6f) return
            val minX = max(0, min(ax, min(bx, cx)).toInt()); val maxX = min(width - 1, max(ax, max(bx, cx)).toInt() + 1)
            val minY = max(0, min(ay, min(by, cy)).toInt()); val maxY = min(height - 1, max(ay, max(by, cy)).toInt() + 1)
            if (minX > maxX || minY > maxY) return
            val iza = 1f / -a[2]; val izb = 1f / -b[2]; val izc = 1f / -c[2]
            val value = FloatArray(a.size)
            for (y in minY..maxY) for (x in minX..maxX) {
                val px = x + .5f; val py = y + .5f
                val w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area
                val w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area
                val w2 = 1f - w0 - w1
                if (w0 < 0f || w1 < 0f || w2 < 0f) continue
                val inv = w0 * iza + w1 * izb + w2 * izc
                val d = 1f / inv
                val index = y * width + x
                if (d >= depth[index]) continue
                depth[index] = d
                for (k in 3 until a.size) value[k] = (w0 * a[k] * iza + w1 * b[k] * izb + w2 * c[k] * izc) / inv
                if (textured && board != null) {
                    // World x, z one pixel to the right and one pixel down: GLSL's fwidth by hand.
                    fun at(qx: Float, qy: Float, k: Int): Float {
                        val u0 = ((bx - qx) * (cy - qy) - (by - qy) * (cx - qx)) / area
                        val u1 = ((cx - qx) * (ay - qy) - (cy - qy) * (ax - qx)) / area
                        val u2 = 1f - u0 - u1
                        return (u0 * a[k] * iza + u1 * b[k] * izb + u2 * c[k] * izc) / (u0 * iza + u1 * izb + u2 * izc)
                    }
                    slopeOnScreen[0] = at(px + 1f, py, 3) - value[3]; slopeOnScreen[1] = at(px + 1f, py, 5) - value[5]
                    slopeOnScreen[2] = at(px, py + 1f, 3) - value[3]; slopeOnScreen[3] = at(px, py + 1f, 5) - value[5]
                }
                val rgb = if (textured) shade(value) else floatArrayOf(value[6], value[7], value[8])
                colour[index] = Color.rgb((rgb[0] * 255).toInt().coerceIn(0, 255), (rgb[1] * 255).toInt().coerceIn(0, 255), (rgb[2] * 255).toInt().coerceIn(0, 255))
            }
        }

        private fun fract(v: Float) = v - floor(v)
        private fun hash(x: Float, y: Float): Float {
            var qx = fract(x * .1031f); var qy = fract(y * .1031f); var qz = fract(x * .1031f)
            val d = qx * (qy + 33.33f) + qy * (qz + 33.33f) + qz * (qx + 33.33f)
            qx += d; qy += d; qz += d
            return fract((qx + qy) * qz)
        }
        private fun noise(x: Float, y: Float): Float {
            val ix = floor(x); val iy = floor(y)
            var fx = x - ix; var fy = y - iy
            fx = fx * fx * (3f - 2f * fx); fy = fy * fy * (3f - 2f * fy)
            val a = hash(ix, iy) + (hash(ix + 1f, iy) - hash(ix, iy)) * fx
            val b = hash(ix, iy + 1f) + (hash(ix + 1f, iy + 1f) - hash(ix, iy + 1f)) * fx
            return a + (b - a) * fy
        }
        private fun smooth(e0: Float, e1: Float, v: Float): Float { val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

        /** Main patterns of GroundShader; [v] holds world xyz at 3..5, colour at 6..8, turf at 9..12. */
        private fun shade(v: FloatArray): FloatArray {
            val wx = v[3]; val wy = v[4]; val wz = v[5]
            val dist = sqrt((wx - eye.x).pow(2) + (wy - eye.y).pow(2) + (wz - eye.z).pow(2))
            val near = 1f - smooth(10f, 70f, dist)
            val mid = 1f - smooth(80f, 420f, dist)
            val fairway = v[9]; val green = v[10]; val sand = v[11]; val water = v[12]
            val rough = (1f - fairway - green - sand - water).coerceIn(0f, 1f)
            var shade = 1f + (noise(wx * .045f, wz * .045f) * .6f + noise(wx * .13f, wz * .13f) * .4f - .5f) * .10f * mid
            val tufts = noise(wx * 2.3f, wz * 2.3f) * .55f + noise(wx * 7.9f, wz * 7.9f) * .45f
            shade += rough * (tufts - .5f) * .22f * near
            val soft = (dist * .004f).coerceIn(.08f, 1f)
            val bands = smooth(-soft, soft, sin((wx * .93f + wz * .37f) * .70f))
            shade += fairway * ((bands - .5f) * .085f * mid + (noise(wx * 5f, wz * 5f) - .5f) * .05f * near)
            val softGreen = (dist * .012f).coerceIn(.1f, 1f)
            val bandsGreen = smooth(-softGreen, softGreen, sin((wx * .42f + wz * .91f) * 1.35f))
            shade += green * ((bandsGreen - .5f) * .032f * mid + (noise(wx * 13f, wz * 13f) - .5f) * .035f * near)
            val rake = sin(wx * 6f + noise(wx * .7f, wz * .7f) * 3f) * .5f + .5f
            shade += sand * ((noise(wx * 17f, wz * 17f) - .5f) * .13f * near + (rake - .5f) * .05f * near)
            val colour = FloatArray(3) { k -> v[6 + k] * shade }
            board?.let { board(it, v, water, colour) }
            val fog = smooth(180f, 850f, dist) * .62f
            val sky = floatArrayOf(.70f, .85f, .86f)
            return FloatArray(3) { k -> colour[k] * (1f - fog) + sky[k] * fog }
        }

        /** GroundShader's slope board, line widths scaled like the game (1/900 of the screen height). */
        private fun board(b: SlopeBoard, v: FloatArray, water: Float, colour: FloatArray) {
            val lineScale = height / 900f
            // The shader's quick test against the window's reach only saves work: the lines' weights say the same.
            val mask = smooth(.10f, .16f, hypot(v[3] - holeX, v[5] - holeZ)) * (1f - water)
            if (mask <= .002f) return
            val s = (v[3] - holeX) / cell; val t = (v[5] - holeZ) / cell
            val d = slopeOnScreen
            val ws = max((abs(d[0]) + abs(d[2])) / cell, 1e-4f) * lineScale
            val wt = max((abs(d[1]) + abs(d[3])) / cell, 1e-4f) * lineScale
            // The window cut along whole squares k board squares wide, as boardLines.
            fun lines(k: Float): Pair<Float, Float> {
                fun shown(gs: Float, gt: Float) = b.weight(holeX + gs * cell, holeZ + gt * cell, k * cell)
                val cs = (floor(s / k) + .5f) * k; val ct = (floor(t / k) + .5f) * k
                val ns = floor(s / k + .5f) * k; val nt = floor(t / k + .5f) * k
                val h = .5f * k
                return max(shown(ns - h, ct), shown(ns + h, ct)) to max(shown(cs, nt - h), shown(cs, nt + h))
            }
            val (coarseS, coarseT) = lines(2f); val (fineS, fineT) = lines(1f)
            val lineS = coarseS + (fineS - coarseS) * fineWeight; val lineT = coarseT + (fineT - coarseT) * fineWeight
            fun line(g: Float) = abs(fract(g - .5f) - .5f)
            val lodFine = (1f - smooth(.07f, .16f, max(ws, wt))) * fineWeight
            val lodCoarse = 1f - smooth(.07f, .16f, max(ws, wt) * .5f)
            fun core(p: Float) = 1f - smooth(.55f, 1.25f, p)
            fun halo(p: Float) = 1f - smooth(1f, 2.6f, p)
            fun coreOf(g: Float, w: Float) = max(core(line(g * .5f) / (w * .5f)) * lodCoarse, core(line(g) / w) * lodFine * .8f)
            fun haloOf(g: Float, w: Float) = max(halo(line(g * .5f) / (w * .5f)) * lodCoarse, halo(line(g) / w) * lodFine * lodFine * .4f)
            val c = max(coreOf(s, ws) * lineS, coreOf(t, wt) * lineT)
            val hl = max(haloOf(s, ws) * lineS, haloOf(t, wt) * lineT)
            val rise = ((v[4] - b.baseY) / b.relief).coerceIn(-1f, 1f)
            val level = floatArrayOf(.94f, .95f, .90f)
            val tint = if (rise > 0f) floatArrayOf(.96f, .36f, .28f) else floatArrayOf(.30f, .86f, .50f)
            for (i in 0..2) {
                var value = colour[i]
                value += (value * .55f - value) * hl * mask * .6f
                val tone = level[i] + (tint[i] - level[i]) * abs(rise)
                colour[i] = value + (tone - value) * c * mask * .85f
            }
        }

        fun bitmap(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { setPixels(colour, 0, width, 0, 0, width, height) }
    }
}
