package com.Atom2Universe.app.periodic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.Atom2Universe.app.crypto.gacha.rarityOf
import com.Atom2Universe.app.periodic.illuminated.EngravedScenes
import com.Atom2Universe.app.periodic.illuminated.IlluminatedCard
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Garde-fou des cartes d'éléments, qui sert aussi d'aperçu.
 *
 * Il dessine les 118 cartes enluminées et échoue si l'une d'elles lève une exception. Au
 * passage, il écrit dans `app/build/apercu/cartes/` la planche des 118 et quelques cartes en
 * grand à plusieurs instants de l'animation : c'est le seul moyen de juger le dessin sans
 * téléphone. Les textes sont des textes d'essai (le test n'a pas les ressources de l'appli).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ElementCardRenderTest {

    private val dossier = File("build/apercu/cartes").also { it.mkdirs() }
    private val fonts = IlluminatedCard.Fonts(
        title = Typeface.createFromFile(File("src/main/assets/fonts/Chomsky.otf")),
        letter = Typeface.create(Typeface.SERIF, Typeface.BOLD),
        caps = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    )

    private fun carte(z: Int): IlluminatedCard {
        val e = getPeriodicElements().first { it.atomicNumber == z }
        val card = IlluminatedCard(fonts)
        val content = IlluminatedCard.Content(
            rarity = rarityOf(z), number = z.toString(), name = NOMS[z] ?: e.name, symbol = e.symbol,
            mass = "MASSE ATOMIQUE · %.3f".format(e.atomicMass), rarityLabel = rarityOf(z).label.uppercase(),
            toxic = ElementCardHazards.isToxic(z), radioactive = ElementCardHazards.hasNoStableIsotope(z)
        )
        // Sans gravure, l'ancienne scène dans la fenêtre, comme dans l'appli.
        val legacy = ElementCardArt().apply { configure(e) }
        card.set(content, EngravedScenes.forElement(z)) { c, t ->
            legacy.draw(c, 0xFF7FD8FF.toInt(), 0xFFFF8AD8.toInt(), (t / 24f % 1f) * 6.2832f)
        }
        return card
    }

    private fun peindre(card: IlluminatedCard, w: Int, temps: Float): Bitmap {
        val h = (w * 520f / 360f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(36, 32, 40))
        val c = Canvas(bmp)
        val s = w / 360f
        c.scale(s, s)
        card.draw(c, s, temps)
        return bmp
    }

    private fun ecrire(nom: String, bmp: Bitmap) {
        FileOutputStream(File(dossier, "$nom.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Toutes les cartes se dessinent, en petit, sans exception. */
    @Test
    fun lesCentDixHuitCartesSeDessinent() {
        val colonnes = 12
        val w = 150
        val h = (w * 520f / 360f).toInt()
        val elements = getPeriodicElements()
        val lignes = (elements.size + colonnes - 1) / colonnes
        val planche = Bitmap.createBitmap(colonnes * w, lignes * h, Bitmap.Config.ARGB_8888)
        val c = Canvas(planche)
        elements.forEachIndexed { i, e ->
            val bmp = peindre(carte(e.atomicNumber), w, 3f)
            c.drawBitmap(bmp, (i % colonnes) * w.toFloat(), (i / colonnes) * h.toFloat(), null)
        }
        ecrire("toutes", planche)
    }

    /** Quelques cartes en grand, à trois instants : l'animation se juge sur la planche. */
    @Test
    fun apercuEnGrand() {
        // Les gravures, puis deux cartes sans gravure (ancienne scène dans le nouveau cadre).
        val choix = listOf(22, 23, 24, 25, 27)
        val instants = floatArrayOf(0.35f, 1.6f, 2.9f)
        val w = 720
        val h = (w * 520f / 360f).toInt()
        for (z in choix) {
            val card = carte(z)
            val debut = System.nanoTime()
            card.prepare(w / 360f)?.let { card.install(it) }
            println("cuisson carte $z : ${(System.nanoTime() - debut) / 1_000_000} ms")
            val planche = Bitmap.createBitmap(w * instants.size, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(planche)
            instants.forEachIndexed { i, t -> c.drawBitmap(peindre(card, w, t + 4f), i * w.toFloat(), 0f, null) }
            ecrire("carte_%03d".format(z), planche)
            card.release()
        }
    }

    /** Six images le long d'un cycle du reflet : on y suit la bande de lumière sur l'or. */
    @Test
    fun cycleDuReflet() {
        val card = carte(79)
        val w = 360
        val h = (w * 520f / 360f).toInt()
        val planche = Bitmap.createBitmap(w * 6, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(planche)
        for (i in 0 until 6) c.drawBitmap(peindre(card, w, i * 1.25f), i * w.toFloat(), 0f, null)
        ecrire("reflet_079", planche)
    }

    /** Planche des polices disponibles, pour choisir celle du nom et de la lettrine. */
    @Test
    fun specimenDesPolices() {
        val noms = listOf("Chomsky.otf", "alamain1.ttf")
        val bmp = Bitmap.createBitmap(900, 420, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(241, 228, 198))
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(43, 29, 20); textSize = 40f }
        var y = 60f
        for (n in noms) {
            p.typeface = Typeface.createFromFile(File("src/main/assets/fonts/$n"))
            c.drawText("Hydrogène Sélénium Fer Or", 20f, y, p); y += 56f
            c.drawText("H He Fe Au Cu S C Og 118", 20f, y, p); y += 70f
        }
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        c.drawText("Hydrogène Sélénium · H He Fe Au Og 118", 20f, y, p)
        ecrire("polices", bmp)
    }

    private companion object {
        val NOMS = mapOf(22 to "Titane", 21 to "Scandium", 13 to "Aluminium", 1 to "Hydrogène", 2 to "Hélium", 6 to "Carbone", 8 to "Oxygène", 10 to "Néon", 7 to "Azote", 9 to "Fluor", 11 to "Sodium", 14 to "Silicium", 17 to "Chlore", 18 to "Argon", 20 to "Calcium", 80 to "Mercure", 15 to "Phosphore", 19 to "Potassium", 92 to "Uranium", 12 to "Magnésium", 16 to "Soufre", 26 to "Fer", 29 to "Cuivre",
            79 to "Or", 43 to "Technétium", 3 to "Lithium", 47 to "Argent", 104 to "Rutherfordium")
    }
}
