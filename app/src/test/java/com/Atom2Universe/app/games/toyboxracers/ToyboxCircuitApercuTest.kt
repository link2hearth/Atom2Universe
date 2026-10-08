package com.Atom2Universe.app.games.toyboxracers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import com.Atom2Universe.app.games.toyboxracers.render.DecorMeshFactory
import com.Atom2Universe.app.games.toyboxracers.render.MeshBuilder
import com.Atom2Universe.app.games.toyboxracers.render.PrototypeMeshFactory
import com.Atom2Universe.app.games.toyboxracers.track.CircuitKind
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import com.Atom2Universe.app.games.toyboxracers.track.RoomKind
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * L'outil qui **montre** les circuits classiques, faute de pouvoir lancer le jeu.
 *
 * Une planche par circuit dans `app/build/apercu/toybox/` : la pièce vue de dessus
 * (meubles teintés selon leur hauteur, ruban teinté selon son altitude, replis du bord
 * en rouge, vides de saut en blanc) et, dessous, le profil d'altitude le long du tour.
 * Le texte en tête donne la longueur, le rayon de virage le plus serré et la pente la
 * plus raide.
 *
 * Il ne vérifie rien : banc d'aperçu, exclu de la suite par défaut.
 * `./gradlew testDebugUnitTest -PbancsMesure --tests "*ToyboxCircuitApercuTest"`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToyboxCircuitApercuTest {
    private val dossier = File("build/apercu/toybox").also { it.mkdirs() }

    @Test
    fun planches() {
        val only = System.getProperty("circuit") ?: System.getenv("TOYBOX_CIRCUIT")
        for (kind in CircuitKind.entries) {
            if (only != null && kind.name != only) continue
            val room = if (kind.usesFurnitureLayout && !kind.usesHouseLayout) RoomKind.GARAGE else RoomKind.BEDROOM
            val track = PrototypeTrack(scene = SceneChoice(room, kind))
            planche(track)
            vues3d(track)
        }
    }

    /** Tous les modèles du catalogue en planche, avec leurs cotes : de quoi choisir un décor. */
    @Test
    fun catalogue() {
        val models = DecorCatalog.all
        val cell = 240
        val columns = 8
        val rows = (models.size + columns - 1) / columns
        val sheet = Bitmap.createBitmap(cell * columns, (cell + 34) * rows, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF2B3040.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f }
        models.forEachIndexed { index, model ->
            val b = model.bounds
            val rendu = ToyboxRendu(cell, cell)
            rendu.clear(0xFFD9C3A5.toInt())
            val size = maxOf(b.width, b.height, b.depth)
            val center = Vec3(b.x, b.y, b.z)
            rendu.camera(center + Vec3(size * 1.1f, size * .9f, size * 1.5f), center, 40f)
            val builder = MeshBuilder()
            DecorMeshFactory.add(builder, DecorPlacement(model, 0f, 0f, 0f))
            rendu.draw(builder)
            val x = (index % columns) * cell
            val y = (index / columns) * (cell + 34)
            canvas.drawBitmap(rendu.toBitmap(), x.toFloat(), y.toFloat(), null)
            canvas.drawText(model.id, x + 4f, y + cell + 14f, paint)
            canvas.drawText("%.1f × %.1f × %.1f".format(b.width, b.height, b.depth), x + 4f, y + cell + 29f, paint)
            println("${model.id}: l ${"%.1f".format(b.width)} h ${"%.1f".format(b.height)} p ${"%.1f".format(b.depth)}" +
                " bas ${"%.1f".format(b.bottom)} centre ${"%.1f".format(b.x)},${"%.1f".format(b.z)}")
        }
        FileOutputStream(File(dossier, "catalogue.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Une vue d'ensemble en perspective puis six vues de poursuite, caméra du jeu. */
    private fun vues3d(track: PrototypeTrack) {
        val environment = ToyboxRendu.vertices(PrototypeMeshFactory.environment(track))
        val road = ToyboxRendu.vertices(PrototypeMeshFactory.track(track))
        val w = 640
        val h = 360
        val sheet = Bitmap.createBitmap(w * 2, h * 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f; isFakeBoldText = true }
        val house = track.scene.circuit.usesHouseLayout
        val views = ArrayList<Pair<String, Pair<Vec3, Vec3>>>()
        val reach = if (house) 2.1f else 1f
        views += "vue d'ensemble" to (Vec3(0f, 120f * reach, 150f * reach) to Vec3(0f, 0f, -5f))
        views += "vue d'ensemble, côté" to (Vec3(-170f * reach, 90f * reach, -20f) to Vec3(10f, 0f, 0f))
        for (k in 0 until 6) {
            val s = track.sampleAt(track.length * (k + .5f) / 6f)
            val fx = s.tangent.x; val fz = s.tangent.z
            val n = hypot(fx, fz).coerceAtLeast(.001f)
            val forward = Vec3(fx / n, 0f, fz / n)
            val car = s.position + Vec3(0f, PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE, 0f)
            val eye = car - forward * 6f + Vec3(0f, 3.2f, 0f)
            val target = car + forward * 2.1f + Vec3(0f, .6f, 0f)
            views += "${((k + .5f) / 6f * 100).toInt()} %" to (eye to target)
        }
        val carMesh = ToyboxRendu.vertices(PrototypeMeshFactory.car())
        views.forEachIndexed { index, (label, camera) ->
            val rendu = ToyboxRendu(w, h)
            rendu.clear()
            rendu.camera(camera.first, camera.second)
            rendu.draw(environment)
            rendu.draw(road)
            if (index >= 2) {
                // La voiture du joueur, posée sur la piste au point de la vue.
                val s = track.sampleAt(track.length * (index - 2 + .5f) / 6f)
                val yaw = track.headingRadians(s)
                val c = kotlin.math.cos(yaw); val sn = kotlin.math.sin(yaw)
                val placed = carMesh.copyOf()
                var i = 0
                while (i < placed.size) {
                    val x = placed[i]; val z = placed[i + 2]
                    placed[i] = s.position.x + x * c + z * sn
                    placed[i + 1] += s.position.y + PrototypeTrack.ROAD_SURFACE_LIFT + PrototypeTrack.CAR_CLEARANCE - .24f
                    placed[i + 2] = s.position.z - x * sn + z * c
                    val nx = placed[i + 3]; val nz = placed[i + 5]
                    placed[i + 3] = nx * c + nz * sn
                    placed[i + 5] = -nx * sn + nz * c
                    i += 10
                }
                rendu.draw(placed)
            }
            val x = (index % 2) * w
            val y = (index / 2) * h
            canvas.drawBitmap(rendu.toBitmap(), x.toFloat(), y.toFloat(), null)
            paint.color = 0xAA000000.toInt()
            canvas.drawText(label, x + 11f, y + 25f, paint)
            paint.color = Color.WHITE
            canvas.drawText(label, x + 10f, y + 24f, paint)
        }
        FileOutputStream(File(dossier, "${track.scene.circuit.name.lowercase()}_3d.png")).use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun planche(track: PrototypeTrack) {
        val samples = track.allSamples()
        val house = track.scene.circuit.usesHouseLayout
        val halfW = if (house) 220f else PrototypeTrack.ROOM_HALF_WIDTH
        val halfD = if (house) 108f else PrototypeTrack.ROOM_HALF_DEPTH
        val scale = 1180f / (halfW * 2f)
        val mapH = (halfD * 2f * scale).toInt()
        val profileH = 240
        val w = 1200
        val h = 70 + mapH + 20 + profileH + 20
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0xFF2B3040.toInt())
        val ox = 10f
        val oz = 60f
        fun sx(x: Float) = ox + (x + halfW) * scale
        fun sz(z: Float) = oz + (z + halfD) * scale
        fun fill(color: Int) = paint.apply { style = Paint.Style.FILL; this.color = color }

        canvas.drawRect(sx(-halfW), sz(-halfD), sx(halfW), sz(halfD), fill(0xFFD9C3A5.toInt()))
        val maxY = maxOf(16f, samples.maxOf { it.position.y })
        // Meubles : plus c'est haut, plus c'est sombre.
        for (box in track.furnitureSolids.sortedBy { it.top }) {
            if (box.top < .3f) continue
            val shade = (box.top / 30f).coerceIn(0f, 1f)
            canvas.drawRect(sx(box.left), sz(box.back), sx(box.right), sz(box.front),
                fill(Color.rgb((190 - 110 * shade).toInt(), (170 - 100 * shade).toInt(), (160 - 70 * shade).toInt())))
        }
        for (toy in track.toyObstacles) {
            canvas.drawCircle(sx(toy.x), sz(toy.z), toy.radius * scale, fill(0xFF8E6BB5.toInt()))
        }
        // Contour des meubles des huit pièces : la piste doit éviter chacun d'eux.
        if (!house) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = 0x99402020.toInt()
            for (room in RoomKind.entries) {
                val other = PrototypeTrack(sampleCount = 16, scene = SceneChoice(room, track.scene.circuit))
                for (box in other.furnitureSolids) {
                    if (box.top < .3f || box.bottom > 6f) continue
                    canvas.drawRect(sx(box.left), sz(box.back), sx(box.right), sz(box.front), paint)
                }
            }
        }

        // Ruban : un quadrilatère par segment, teinté selon l'altitude.
        var folds = 0
        for (i in samples.indices) {
            val a = samples[i]
            val b = samples[(i + 1) % samples.size]
            val middle = track.sampleAt((a.distance + if (i == samples.lastIndex) track.length else b.distance) * .5f)
            val deck = track.hasDeck(middle)
            val ha = a.roadWidth * .5f + PrototypeTrack.CURB_WIDTH
            val hb = b.roadWidth * .5f + PrototypeTrack.CURB_WIDTH
            val path = Path()
            path.moveTo(sx(a.position.x - a.right.x * ha), sz(a.position.z - a.right.z * ha))
            path.lineTo(sx(b.position.x - b.right.x * hb), sz(b.position.z - b.right.z * hb))
            path.lineTo(sx(b.position.x + b.right.x * hb), sz(b.position.z + b.right.z * hb))
            path.lineTo(sx(a.position.x + a.right.x * ha), sz(a.position.z + a.right.z * ha))
            path.close()
            // Repli : un bord avance à reculons par rapport à l'axe.
            val tx = b.position.x - a.position.x
            val tz = b.position.z - a.position.z
            fun edge(side: Float): Float {
                val ex = (b.position.x + b.right.x * hb * side) - (a.position.x + a.right.x * ha * side)
                val ez = (b.position.z + b.right.z * hb * side) - (a.position.z + a.right.z * ha * side)
                return ex * tx + ez * tz
            }
            val folded = edge(-1f) <= 0f || edge(1f) <= 0f
            if (folded) folds++
            val t = (a.position.y / maxY).coerceIn(0f, 1f)
            canvas.drawPath(path, fill(when {
                folded -> 0xFFFF2040.toInt()
                !deck -> 0x5AFFFFFF
                else -> Color.rgb((90 + 150 * t).toInt(), (110 + 40 * t).toInt(), (150 - 90 * t).toInt())
            }))
        }
        // Axe et sens de marche.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0x78FFFFFF
        for (i in samples.indices step 2) {
            val a = samples[i]; val b = samples[(i + 1) % samples.size]
            canvas.drawLine(sx(a.position.x), sz(a.position.z), sx(b.position.x), sz(b.position.z), paint)
        }
        paint.textSize = 13f
        for (k in 0 until 20) {
            val s = track.sampleAt(track.length * k / 20f)
            val px = sx(s.position.x); val pz = sz(s.position.z)
            val n = hypot(s.tangent.x, s.tangent.z).coerceAtLeast(.001f)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = 2f
            canvas.drawLine(px, pz, px + s.tangent.x / n * 14, pz + s.tangent.z / n * 14, paint)
            paint.style = Paint.Style.FILL
            canvas.drawText("${k * 5}", px + 4, pz - 4, paint)
        }
        val start = track.sampleAt(0f)
        canvas.drawCircle(sx(start.position.x), sz(start.position.z), 6f, fill(0xFF40E080.toInt()))

        // Trajet du pilote automatique : vert au sol, jaune en vol, rouge tombé de la route.
        val ride = ToyboxPilote.drive(track)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        for (i in 1 until ride.points.size) {
            val a = ride.points[i - 1]; val b = ride.points[i]
            paint.color = when (b.state) {
                ToyboxPilote.State.AIR -> 0xFFFFE040.toInt()
                ToyboxPilote.State.FALLEN -> 0xFFFF3030.toInt()
                ToyboxPilote.State.ROAD -> 0xFF30C060.toInt()
            }
            canvas.drawLine(sx(a.x), sz(a.z), sx(b.x), sz(b.z), paint)
        }

        // Mesures : rayon le plus serré, pente la plus raide.
        var minRadius = Float.MAX_VALUE
        var minRadiusAt = 0f
        var maxSlope = 0f
        var maxSlopeAt = 0f
        val probe = 3f
        var d = 0f
        while (d < track.length) {
            val p0 = track.sampleAt(d - probe); val p2 = track.sampleAt(d + probe)
            val turn = abs(angle(atan2(p2.tangent.x, p2.tangent.z) - atan2(p0.tangent.x, p0.tangent.z)))
            val radius = if (turn < 1e-4f) Float.MAX_VALUE else probe * 2f / turn
            if (radius < minRadius) { minRadius = radius; minRadiusAt = d / track.length }
            val s = track.sampleAt(d)
            val slope = abs(s.tangent.y) / hypot(s.tangent.x, s.tangent.z).coerceAtLeast(.001f)
            if (slope > maxSlope && track.hasDeck(s)) { maxSlope = slope; maxSlopeAt = d / track.length }
            d += .5f
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 17f
        paint.isFakeBoldText = true
        canvas.drawText(
            "${track.scene.circuit}  longueur ${track.length.toInt()}  ~${(track.length / 17f).toInt()} s/tour  " +
                "rayon min ${"%.1f".format(minRadius)} (à ${(minRadiusAt * 100).toInt()} %)  " +
                "demi-largeur ${"%.1f".format(samples.maxOf { it.roadWidth } * .5f)}  " +
                "pente max ${(maxSlope * 100).toInt()} % (à ${(maxSlopeAt * 100).toInt()} %)  replis $folds",
            12f, 26f, paint)
        paint.isFakeBoldText = false
        paint.textSize = 13f
        canvas.drawText("altitude max ${"%.1f".format(samples.maxOf { it.position.y })}   pilote : " +
            ride.summary(), 12f, 46f, paint)
        println("${track.scene.circuit}: longueur ${track.length.toInt()} rayon min ${"%.1f".format(minRadius)} " +
            "replis $folds pente ${(maxSlope * 100).toInt()} % | ${ride.summary()}")

        // Profil d'altitude le long du tour.
        val py = 70f + mapH + 20f
        canvas.drawRect(10f, py, w - 10f, py + profileH, fill(0xFF232733.toInt()))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0x28FFFFFF
        var level = 0
        while (level <= maxY) {
            val y = py + profileH - 10 - level / maxY * (profileH - 20)
            canvas.drawLine(10f, y, w - 10f, y, paint)
            level += 4
        }
        for (k in 0..20) {
            val x = 10 + k / 20f * (w - 20)
            canvas.drawLine(x, py, x, py + profileH, paint)
        }
        paint.strokeWidth = 2f
        var previous: Pair<Float, Float>? = null
        for (k in 0..600) {
            val s = track.sampleAt(track.length * k / 600f)
            val x = 10 + k / 600f * (w - 20)
            val y = py + profileH - 10 - s.position.y / maxY * (profileH - 20)
            paint.color = if (track.hasDeck(s)) 0xFFF0C060.toInt() else 0xFFFF6070.toInt()
            previous?.let { canvas.drawLine(it.first, it.second, x, y, paint) }
            previous = x to y
        }
        FileOutputStream(File(dossier, "${track.scene.circuit.name.lowercase()}.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun angle(value: Float): Float {
        var v = value
        while (v > Math.PI) v -= (2 * Math.PI).toFloat()
        while (v < -Math.PI) v += (2 * Math.PI).toFloat()
        return v
    }
}
