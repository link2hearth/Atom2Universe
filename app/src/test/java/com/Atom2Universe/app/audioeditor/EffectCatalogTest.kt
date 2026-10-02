package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.dsp.NoiseProfile
import com.Atom2Universe.app.audioeditor.dsp.processInMemory
import com.Atom2Universe.app.audioeditor.ui.ChoiceParam
import com.Atom2Universe.app.audioeditor.ui.EffectCatalog
import com.Atom2Universe.app.audioeditor.ui.EffectDef
import com.Atom2Universe.app.audioeditor.ui.Param
import com.Atom2Universe.app.audioeditor.ui.SliderParam
import com.Atom2Universe.app.audioeditor.ui.ToggleParam
import com.Atom2Universe.app.audioeditor.ui.Values
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.sin

/**
 * Le registre est une donnée : on vérifie qu'aucun effet n'est mal déclaré (valeur par défaut hors course,
 * constructeur qui refuse un réglage que le curseur permet) en les faisant tous tourner sur de vrais échantillons.
 */
class EffectCatalogTest {

    private val rate = 44100
    private val all: List<EffectDef> = EffectCatalog.categories.flatMap { it.effects }

    /** Un quart de seconde de stéréo : un sinus à gauche, du bruit à droite (de quoi réveiller filtres et dynamique). */
    private fun input(): Array<FloatArray> {
        val n = rate / 4
        val rnd = Random(7)
        return arrayOf(
            FloatArray(n) { (0.5 * sin(2 * Math.PI * 440 * it / rate)).toFloat() },
            FloatArray(n) { (rnd.nextGaussian() * 0.1).toFloat().coerceIn(-1f, 1f) },
        )
    }

    private fun noiseProfile(): NoiseProfile {
        val rnd = Random(3)
        return NoiseProfile.fromSamples(FloatArray(rate) { (rnd.nextGaussian() * 0.02).toFloat() })!!
    }

    private fun run(def: EffectDef, v: Values): Array<FloatArray> {
        val fx = def.build(v, if (def.needsProfile) noiseProfile() else null)
        assertNotNull("${def.id} doit se construire", fx)
        return fx!!.processInMemory(input(), rate)
    }

    private fun assertFinite(id: String, out: Array<FloatArray>) {
        assertTrue("$id : sortie vide", out.isNotEmpty() && out[0].isNotEmpty())
        for (ch in out) for (x in ch) assertTrue("$id : valeur non finie", x.isFinite())
    }

    @Test
    fun `les identifiants des effets et des parametres sont uniques`() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (d in all) assertEquals("${d.id} : paramètres en double", d.params.size, d.params.map { it.id }.toSet().size)
        for (g in EffectCatalog.generators) assertEquals(g.params.size, g.params.map { it.id }.toSet().size)
    }

    @Test
    fun `chaque curseur a une valeur par defaut dans sa course et un aller-retour exact`() {
        val params: List<Param> = all.flatMap { it.params } + EffectCatalog.generators.flatMap { it.params }
        for (p in params.filterIsInstance<SliderParam>()) {
            assertTrue("${p.id} : min < max", p.min < p.max)
            assertTrue("${p.id} : défaut hors course", p.default in p.min..p.max)
            assertEquals(p.min, p.valueAt(0f), 1e-4f)
            assertEquals(p.max, p.valueAt(1f), 1e-3f * p.max)
            // Le défaut doit se retrouver à un pas près après être passé par le curseur.
            val back = p.valueAt(p.fractionOf(p.default))
            assertTrue("${p.id} : $back au lieu de ${p.default}", abs(back - p.default) <= p.step * 1.01f)
        }
        for (p in params.filterIsInstance<ChoiceParam>()) assertTrue(p.default.toInt() in p.options.indices)
    }

    @Test
    fun `chaque effet tourne avec ses valeurs par defaut`() {
        for (d in all) assertFinite(d.id, run(d, Values.defaults(d.params)))
    }

    @Test
    fun `chaque effet tient aux deux extremes de ses curseurs`() {
        for (d in all) {
            for (end in listOf(0f, 1f)) {
                val v = Values.defaults(d.params)
                for (p in d.params) when (p) {
                    is SliderParam -> v[p.id] = p.valueAt(end)
                    is ChoiceParam -> v[p.id] = (if (end == 0f) 0 else p.options.size - 1).toFloat()
                    is ToggleParam -> v[p.id] = end
                }
                assertFinite("${d.id}@$end", run(d, v))
            }
        }
    }

    @Test
    fun `les generateurs produisent exactement la duree demandee`() {
        for (g in EffectCatalog.generators.filter { !it.insertsSilence }) {
            val v = Values.defaults(g.params)
            val frames = 30_000L
            val reader = g.build(v, rate, frames)!!
            val buf = Array(reader.channels) { FloatArray(4096) }
            var total = 0L
            while (true) {
                val n = reader.read(buf, 4096)
                if (n <= 0) break
                for (ch in buf) for (i in 0 until n) assertTrue(ch[i].isFinite() && abs(ch[i]) <= 1f)
                total += n
            }
            assertEquals(g.id, frames, total)
        }
    }

    @Test
    fun `le silence ne fabrique pas de lecteur et la reduction de bruit exige son profil`() {
        assertTrue(EffectCatalog.findGenerator("silence")!!.insertsSilence)
        val nr = EffectCatalog.find("noise_reduction")!!
        assertTrue(nr.needsProfile)
        assertEquals(null, nr.build(Values.defaults(nr.params), null))
    }
}
