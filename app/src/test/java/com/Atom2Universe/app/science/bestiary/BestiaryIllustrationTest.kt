package com.Atom2Universe.app.science.bestiary

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest

/** Embedded illustrated catalogue: offline coverage, bounded download footprint, visual review sheets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BestiaryIllustrationTest {
    private val assets = File("src/main/assets/science/bestiary")
    private val output = File("build/reports/bestiary-illustrations").apply { mkdirs() }
    private fun file(animal: AnimalSpecies) = File(assets, "${animal.id}.webp")

    @Test fun everySpeciesHasItsOwnSmallOfflineIllustration() {
        assertTrue("The first illustrated catalogue must contain at least 100 animal sheets", AnimalCatalog.species.size >= 100)
        val failures = mutableListOf<String>()
        val hashes = mutableMapOf<String, MutableList<String>>()
        var totalBytes = 0L
        for (animal in AnimalCatalog.species) {
            val file = file(animal)
            if (!file.isFile) { failures += "${animal.id}: missing offline illustration"; continue }
            totalBytes += file.length()
            if (file.length() !in 1000L..153600L) failures += "${animal.id}: ${file.length()} bytes (maximum 150 KiB)"
            val bytes = file.readBytes()
            val isWebp = bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
            if (!isWebp) failures += "${animal.id}: invalid WebP container"
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            hashes.getOrPut(hash) { mutableListOf() }.add(animal.id)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) { failures += "${animal.id}: image cannot be decoded"; continue }
            if (maxOf(bitmap.width, bitmap.height) !in 512..640 || minOf(bitmap.width, bitmap.height) !in 320..640) failures += "${animal.id}: ${bitmap.width} × ${bitmap.height}, expected 512..640 px longest edge and at least 320 px shortest edge"
            val samples = mutableSetOf<Int>()
            for (y in 0 until bitmap.height step 16) for (x in 0 until bitmap.width step 16) samples += bitmap.getPixel(x, y)
            if (samples.size < 50) failures += "${animal.id}: unexpectedly empty or flat illustration"
            // The approved white-background compositions preserve complete ears, toes and tails.
            fun darkAt(x: Int, y: Int): Boolean {
                val color = bitmap.getPixel(x, y)
                return Color.alpha(color) > 200 && minOf(Color.red(color), Color.green(color), Color.blue(color)) < 220
            }
            var boundaryPixels = 0
            for (x in 0 until bitmap.width) {
                if (darkAt(x, 0)) boundaryPixels++
                if (darkAt(x, bitmap.height - 1)) boundaryPixels++
            }
            for (y in 1 until bitmap.height - 1) {
                if (darkAt(0, y)) boundaryPixels++
                if (darkAt(bitmap.width - 1, y)) boundaryPixels++
            }
            if (boundaryPixels > 3) failures += "${animal.id}: $boundaryPixels dark pixels at image boundary suggest a cropped animal"
            bitmap.recycle()
        }
        hashes.values.filter { it.size > 1 }.forEach { failures += "Repeated artwork: ${it.joinToString(" / ")}" }
        val budget = minOf(50L * 1024 * 1024, AnimalCatalog.species.size * 50L * 1024)
        if (totalBytes > budget) failures += "Illustration catalogue exceeds an average 50 KiB per animal or 50 MiB total: $totalBytes bytes"
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun contactSheetsForIndependentAnatomicalReview() {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 16f }
        AnimalCatalog.species.chunked(20).forEachIndexed { batch, animals ->
            val sheet = Bitmap.createBitmap(1600, 425 * ((animals.size + 3) / 4), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(0xfff5f0e4.toInt())
            animals.forEachIndexed { i, animal ->
                val bitmap = BitmapFactory.decodeFile(file(animal).path)
                assertNotNull("${animal.id}: illustration required for visual review", bitmap)
                bitmap!!
                val x = (i % 4) * 400f; val y = (i / 4) * 425f
                val scale = minOf(400f / bitmap.width, 400f / bitmap.height)
                canvas.save()
                canvas.translate(x + (400 - bitmap.width * scale) / 2f, y + (400 - bitmap.height * scale) / 2f)
                canvas.scale(scale, scale)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                canvas.restore()
                canvas.drawText(animal.id, x + 15f, y + 419f, title)
                bitmap.recycle()
            }
            File(output, "batch-${batch + 1}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sheet.recycle()
        }
    }
}
