package com.Atom2Universe.app.games.caves.world

import android.graphics.Bitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/**
 * Banc d'aperçu du relief naturel de Cave World (exclu par défaut : -PbancsMesure).
 *
 * Dans `app/build/apercu/caveworld/`, pour chaque graine :
 *  - `relief_seed*.png` : 4 km × 4 km vus de dessus, 4 blocs par pixel, éclairés du nord-ouest.
 *    Le quadrillage fait 512 blocs, la distance de vue détaillée maximale.
 *  - `profils_seed*.png` : quatre coupes de 1024 blocs à l'échelle 1:1, le relief tel que le
 *    joueur le voit de profil.
 * Et imprime les chiffres qui disent si le relief est plat : dénivelé local, pentes, altitudes.
 * `./gradlew testDebugUnitTest -PbancsMesure --tests "*CaveReliefApercuTest"`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaveReliefApercuTest {
    private val dossier = File("build/apercu/caveworld").also { it.mkdirs() }
    private val sea = NaturalTerrain.SEA_LEVEL

    private fun profiles(): List<NaturalBiomeProfile> {
        val text = File("src/main/assets/caves/natural_generation.json").readText()
        return Regex("\\{[^{}]*\"id\"[^{}]*\\}").findAll(text).map { m ->
            fun number(key: String) = Regex("\"$key\"\\s*:\\s*(-?[0-9.]+)").find(m.value)?.groupValues?.get(1)?.toDouble() ?: 0.0
            NaturalBiomeProfile(Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(m.value)!!.groupValues[1],
                number("temperature"), number("humidity"), number("rarity"))
        }.toList()
    }

    private fun color(parent: String): Int = when (parent) {
        "desert" -> 0xDBC88C; "red_desert" -> 0xC47846; "savanna" -> 0xAAA55A
        "plains" -> 0x82B45A; "forest" -> 0x508C3C; "birch_forest" -> 0x6EA050
        "dark_forest" -> 0x32642D; "jungle", "jungle_edge" -> 0x287832; "redwood_forest" -> 0x466E32
        "taiga" -> 0x3C6E50; "tundra" -> 0xDCE1E6; "mountains" -> 0x8C8C87; "rocky" -> 0x968C78
        "volcanic" -> 0x463C3C; "wetlands" -> 0x5A7846
        else -> if (parent.startsWith("magic_forest")) 0x8264AA else 0x808080
    }

    private fun shade(rgb: Int, k: Double): Int {
        fun c(shift: Int) = ((rgb shr shift and 0xFF) * k).roundToInt().coerceIn(0, 255)
        return (c(16) shl 16) or (c(8) shl 8) or c(0)
    }

    private fun save(pixels: IntArray, width: Int, name: String) {
        val bitmap = Bitmap.createBitmap(width, pixels.size / width, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(IntArray(pixels.size) { pixels[it] or 0xFF000000.toInt() }, 0, width, 0, 0, width, pixels.size / width)
        FileOutputStream(File(dossier, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun percentile(values: List<Double>, p: Double) =
        values.sorted().let { if (it.isEmpty()) 0.0 else it[((it.size - 1) * p).roundToInt()] }

    @Test fun relief() {
        val profiles = profiles()
        for (seed in listOf(42L, 7L)) {
            val t = NaturalTerrain(seed, profiles)
            val size = 1024; val step = 4; val origin = -size * step / 2
            val h = DoubleArray(size * size); val water = IntArray(size * size)
            for (pz in 0 until size) for (px in 0 until size) {
                val x = (origin + px * step).toDouble(); val z = (origin + pz * step).toDouble()
                h[pz * size + px] = t.height(x, z); water[pz * size + px] = t.waterLevelAt(x, z)
            }
            fun at(px: Int, pz: Int) = h[pz.coerceIn(0, size - 1) * size + px.coerceIn(0, size - 1)]

            val image = IntArray(size * size)
            val lx = -1.0 / sqrt(3.0); val ly = 1.0 / sqrt(3.0); val lz = -1.0 / sqrt(3.0)
            val slopes = ArrayList<Double>(); val altitudes = ArrayList<Double>()
            val highClimate = ArrayList<Double>(); val lowClimate = ArrayList<Double>()
            for (pz in 0 until size) for (px in 0 until size) {
                val i = pz * size + px
                val gx = (at(px + 1, pz) - at(px - 1, pz)) / (2.0 * step)
                val gz = (at(px, pz + 1) - at(px, pz - 1)) / (2.0 * step)
                val norm = sqrt(gx * gx + gz * gz + 1)
                val light = (-gx * lx + ly - gz * lz) / norm
                val wet = h[i] < water[i]
                val x = (origin + px * step).toDouble(); val z = (origin + pz * step).toDouble()
                if (!wet) {
                    slopes += hypot(gx, gz); altitudes += h[i] - sea
                    if (h[i] > sea + 50) highClimate += t.temperature(x, z)
                    else if (h[i] < sea + 20) lowClimate += t.temperature(x, z)
                }
                val base = when {
                    wet -> shade(0x3C78C8, 1.0 - min(.55, (water[i] - h[i]) / 40.0))
                    h[i] > sea + 105 && t.temperature(x, z) < .36 -> 0xF0F0F5
                    h[i] > sea + 65 -> 0x87847F
                    else -> color(RegionalBiomes.parent(t.biomeIdAt(x, z)))
                }
                var rgb = if (wet) base else shade(base, .45 + .75 * light.coerceAtLeast(0.0))
                if ((origin + px * step) % 512 == 0 || (origin + pz * step) % 512 == 0) rgb = shade(rgb, .8)
                image[i] = rgb
            }
            save(image, size, "relief_seed$seed.png")

            // Dénivelé dans une fenêtre : ce qu'on voit autour de soi, eau comprise.
            fun localRelief(window: Int): List<Double> {
                val n = window / step; val out = ArrayList<Double>()
                for (wz in 0 until size / n) for (wx in 0 until size / n) {
                    var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
                    for (pz in wz * n until (wz + 1) * n) for (px in wx * n until (wx + 1) * n) {
                        val v = max(h[pz * size + px], water[pz * size + px].toDouble())
                        lo = min(lo, v); hi = max(hi, v)
                    }
                    out += hi - lo
                }
                return out
            }
            val r128 = localRelief(128); val r512 = localRelief(512)
            val land = altitudes.size.toDouble()
            fun share(p: (Double) -> Boolean) = "%.0f %%".format(100.0 * slopes.count(p) / land)
            println("── graine $seed ── terre émergée ${"%.0f".format(100 * land / (size * size))} %")
            println("  altitude au-dessus de la mer : médiane ${percentile(altitudes, .5).roundToInt()}, " +
                "90e centile ${percentile(altitudes, .9).roundToInt()}, 99e ${percentile(altitudes, .99).roundToInt()}, " +
                "max ${altitudes.maxOrNull()?.roundToInt()} ; au-dessus de +50 : ${"%.1f".format(100.0 * altitudes.count { it > 50 } / land)} %")
            println("  pentes : plat (<5 %) ${share { it < .05 }}, douce (5-30 %) ${share { it in .05..0.3 }}, " +
                "raide (30-100 %) ${share { it in 0.3..1.0 }}, falaise (>100 %) ${share { it > 1.0 }}")
            println("  dénivelé sur 128 blocs : médiane ${percentile(r128, .5).roundToInt()}, 90e centile ${percentile(r128, .9).roundToInt()}")
            println("  dénivelé sur 512 blocs : médiane ${percentile(r512, .5).roundToInt()}, 90e centile ${percentile(r512, .9).roundToInt()}, " +
                "max ${r512.maxOrNull()?.roundToInt()}")
            fun climate(temps: List<Double>) = "température moyenne ${"%.2f".format(temps.average())}, " +
                "froid (<0,30) ${"%.0f".format(100.0 * temps.count { it < .30 } / temps.size)} %, " +
                "chaud (>0,63) ${"%.0f".format(100.0 * temps.count { it > .63 } / temps.size)} %"
            println("  au-dessus de +50 : ${climate(highClimate)}")
            println("  sous +20         : ${climate(lowClimate)}")

            // Coupes 1:1 : un pixel = un bloc, de la mer - 40 à la mer + 200.
            val cutWidth = 1024; val cutHeight = 240; val cuts = 4
            val profile = IntArray(cutWidth * cutHeight * cuts)
            for (c in 0 until cuts) {
                val z = (-1536 + c * 1024).toDouble()
                for (px in 0 until cutWidth) {
                    val x = (-512 + px).toDouble()
                    val ground = t.height(x, z); val level = t.waterLevelAt(x, z)
                    for (py in 0 until cutHeight) {
                        val y = sea + 200 - py
                        val rgb = when {
                            y <= ground -> if (ground - y < 4) 0x5A8C3C else 0x6E6A64
                            y <= level -> 0x3C78C8
                            y == sea + 50 || y == sea + 100 -> 0xC8D2DC
                            else -> 0xE6EEF5
                        }
                        profile[(c * cutHeight + py) * cutWidth + px] = if (py == cutHeight - 1) 0x202020 else rgb
                    }
                }
            }
            save(profile, cutWidth, "profils_seed$seed.png")
        }
    }
}
