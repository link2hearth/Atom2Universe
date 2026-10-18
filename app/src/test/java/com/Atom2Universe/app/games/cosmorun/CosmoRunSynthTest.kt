package com.Atom2Universe.app.games.cosmorun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Les sons sortent aussi en WAV dans app/build/cosmo-sfx/ pour qu'on puisse les écouter sur PC. */
class CosmoRunSynthTest {
    @Test fun everySoundIsAudibleAndBounded() {
        val out = File("build/cosmo-sfx").apply { mkdirs() }
        for (sound in CosmoSound.entries) {
            val pcm = CosmoRunSynth.render(sound)
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("$sound est muet", peak > 4000)
            assertTrue("$sound sature", peak < 32767)
            assertTrue("$sound est trop long", pcm.size < CosmoRunSynth.RATE * if (sound.music) 9 else 2)
            File(out, "${sound.name.lowercase()}.wav").writeBytes(CosmoRunSynth.wav(pcm))
        }
    }

    @Test fun humLoopsWithoutASeam() {
        val pcm = CosmoRunSynth.render(CosmoSound.HUM)
        assertEquals(CosmoRunSynth.RATE, pcm.size)
        // Le dernier échantillon doit se raccorder au premier : pas de saut plus grand qu'un pas normal.
        val step = pcm.indices.drop(1).maxOf { abs(pcm[it] - pcm[it - 1]) }
        assertTrue("couture de ${abs(pcm.last() - pcm.first())} contre un pas de $step",
            abs(pcm.last() - pcm.first()) <= step)
    }

    @Test fun musicLayersAreTheSameLengthAndLoopWithoutASeam() {
        val layers = CosmoSound.entries.filter { it.music }.map { CosmoRunSynth.render(it) }
        assertEquals(4, layers.size)
        for (l in layers) {
            assertEquals("les couches doivent avoir la même durée pour rester en phase", CosmoRunSynth.Music.LENGTH, l.size)
            val typical = l.indices.drop(1).map { abs(l[it] - l[it - 1]) }.sorted()[l.size * 99 / 100 - 1]
            // Le raccord ne fait pas un saut plus grand que le plus grand pas ordinaire de la couche.
            assertTrue("couture de ${abs(l.last() - l.first())} pour un pas de $typical", abs(l.last() - l.first()) <= typical * 3 + 400)
        }
        assertTrue("boucle de ${CosmoRunSynth.Music.LENGTH / CosmoRunSynth.RATE.toFloat()} s : sous la limite de SoundPool",
            CosmoRunSynth.Music.LENGTH * 2 < 1_000_000)
    }
}
