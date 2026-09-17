package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.world.NaturalBiomeProfile
import com.Atom2Universe.app.games.caves.world.NaturalTerrain
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Une surface d'eau générée ne doit jamais déborder : chaque bloc d'eau de surface a, sur ses
 * quatre côtés, soit de l'eau au même niveau, soit un sol au moins aussi haut que l'eau.
 */
class LakeWaterLevelTest {
    private fun profiles(): List<NaturalBiomeProfile> {
        // org.json n'est qu'un bouchon en test JVM : lecture minimale des biomes à la main.
        val text = File("src/main/assets/caves/natural_generation.json").readText()
        val biomes = text.substring(text.indexOf("\"biomes\""))
        return Regex("\\{[^{}]*\"id\"[^{}]*\\}").findAll(biomes).map { m ->
            fun num(key: String) = Regex("\"$key\"\\s*:\\s*(-?[0-9.]+)").find(m.value)?.groupValues?.get(1)?.toDouble()
            NaturalBiomeProfile(Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(m.value)!!.groupValues[1],
                num("base") ?: 0.0, num("amplitude") ?: 1.0, num("temperature") ?: .5, num("humidity") ?: .5, num("rarity") ?: 0.0)
        }.toList()
    }

    @Test
    fun surfaceWaterNeverSpillsOntoLowerLand() {
        val profiles = profiles()
        for (seed in listOf(42L, 7L, 123456789L)) {
            val t = NaturalTerrain(seed, profiles)
            val size = 1536
            val h = IntArray(size * size); val w = IntArray(size * size)
            for (z in 0 until size) for (x in 0 until size) {
                h[z * size + x] = t.height(x.toDouble(), z.toDouble()).toInt()
                w[z * size + x] = t.waterLevelAt(x.toDouble(), z.toDouble())
            }
            var lakeColumns = 0; var spills = 0; var example = ""
            for (z in 1 until size - 1) for (x in 1 until size - 1) {
                val i = z * size + x
                if (h[i] >= w[i]) continue
                if (w[i] != NaturalTerrain.SEA_LEVEL) lakeColumns++
                for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val j = (z + dz) * size + x + dx
                    val neighbourWet = h[j] < w[j] && w[j] == w[i]
                    // Le voisin retient l'eau s'il est mouillé au même niveau ou si son sol l'atteint.
                    if (!neighbourWet && h[j] < w[i]) {
                        spills++
                        if (example.isEmpty()) example = "seed $seed ($x,$z) eau=${w[i]} voisin sol=${h[j]} eau=${w[j]}"
                    }
                }
            }
            println("seed $seed : $lakeColumns colonnes de lac, $spills débordements")
            assertTrue("débordement : $example", spills == 0)
        }
    }
}
