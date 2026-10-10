package com.Atom2Universe.app.science.mycology

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Montre les planches de l'atlas des champignons sans téléphone : chaque espèce en trois vues
 * (vue, coupe, dessous), avec les vrais repères en français, dans `app/build/apercu/champignons/`.
 * Les textes viennent de `values-fr/strings_mycology.xml` (le test n'a pas les ressources de l'appli).
 *
 * Il ne vérifie presque rien (le catalogue est complet et chaque planche se dessine) : banc d'aperçu,
 * exclu de la suite par défaut.
 * `./gradlew testDebugUnitTest -PbancsMesure --tests "*FungusPlateApercuTest"`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FungusPlateApercuTest {
    private val dossier = File("build/apercu/champignons").also { it.mkdirs() }

    private val texts: Map<Int, String> by lazy {
        val ids = HashMap<String, Int>()
        for (f in Class.forName("com.Atom2Universe.app.R\$string").fields) ids[f.name] = f.getInt(null)
        val out = HashMap<Int, String>()
        for (file in listOf("src/main/res/values/strings_mycology.xml", "src/main/res/values-fr/strings_mycology.xml")) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(file))
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes.getNamedItem("name").nodeValue
                ids[name]?.let { out[it] = node.textContent }
            }
        }
        out
    }

    private fun text(id: Int) = texts[id] ?: "#$id"

    private fun save(bitmap: Bitmap, name: String) {
        FileOutputStream(File(dossier, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun planches() {
        assertTrue(FungusCatalog.species.size >= 19)
        for (sp in FungusCatalog.species) {
            for (mode in PlateMode.entries) {
                val bmp = Bitmap.createBitmap(720, 800, Bitmap.Config.ARGB_8888)
                FungusPlate.draw(Canvas(bmp), ::text, sp.look, mode, 720f, 800f, 2f, true, false)
                save(bmp, "${sp.id}-${mode.name.lowercase()}.png")
            }
        }
    }

    @Test
    fun feuilles() {
        val cols = 4
        val w = 360
        val h = 400
        val rows = (FungusCatalog.species.size + cols - 1) / cols
        for (mode in PlateMode.entries) {
            val sheet = Bitmap.createBitmap(cols * w, rows * h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.parseColor("#20201C"))
            for ((i, sp) in FungusCatalog.species.withIndex()) {
                canvas.save()
                canvas.translate((i % cols) * w.toFloat(), (i / cols) * h.toFloat())
                canvas.clipRect(0f, 0f, w.toFloat(), h.toFloat())
                FungusPlate.draw(canvas, ::text, sp.look, mode, w.toFloat(), h.toFloat(), 1f, true, false)
                canvas.restore()
            }
            save(sheet, "feuille-${mode.name.lowercase()}.png")
        }
    }

    @Test
    fun tuile() {
        // La tuile du hub Science, telle qu'elle sera rendue (carrée, sans le titre posé par la tuile).
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val tile = MycologyHubTileDrawable(org.robolectric.RuntimeEnvironment.getApplication())
        // `render` est protégé : on l'appelle directement, sans passer par la cuisson asynchrone du cache.
        val render = MycologyHubTileDrawable::class.java.getDeclaredMethod("render", Canvas::class.java, Float::class.java, Float::class.java)
        render.isAccessible = true
        render.invoke(tile, Canvas(bmp), 512f, 512f)
        save(bmp, "tuile.png")
    }

    @Test
    fun groupes() {
        // Les sosies côte à côte, à la même échelle, comme sur l'écran des groupes.
        for (group in FungusCatalog.groups) {
            val looks = group.members.mapNotNull { FungusCatalog.get(it)?.look }
            val w = 300
            val h = 360
            for (mode in listOf(PlateMode.SIDE, PlateMode.SECTION, PlateMode.UNDER)) {
                val scale = looks.minOf { FungusPlate.fitScale(it, w.toFloat(), h.toFloat(), 1f, true, mode) }
                val sheet = Bitmap.createBitmap(w * looks.size, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sheet)
                for ((i, look) in looks.withIndex()) {
                    canvas.save()
                    canvas.translate(i * w.toFloat(), 0f)
                    canvas.clipRect(0f, 0f, w.toFloat(), h.toFloat())
                    FungusPlate.draw(canvas, ::text, look, mode, w.toFloat(), h.toFloat(), 1f, false, true, scale)
                    canvas.restore()
                }
                save(sheet, "groupe-${group.id}-${mode.name.lowercase()}.png")
            }
        }
    }
}
