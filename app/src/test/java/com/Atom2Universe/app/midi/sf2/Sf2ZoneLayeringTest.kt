package com.Atom2Universe.app.midi.sf2

import com.Atom2Universe.app.midi.sf2.TestSf2Writer.Companion.range
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Règles de superposition des générateurs (norme SF2 2.01 §9.4), vérifiées sur les deux
 * parseurs : le parseur complet et le parseur « streaming » des gros fichiers.
 */
class Sf2ZoneLayeringTest {

    private val sampleId = Sf2Generator.SAMPLE_ID.id
    private val instrument = Sf2Generator.INSTRUMENT.id
    private val keyRange = Sf2Generator.KEY_RANGE.id
    private val fineTune = Sf2Generator.FINE_TUNE.id
    private val attenuation = Sf2Generator.INITIAL_ATTENUATION.id
    private val scaleTuning = Sf2Generator.SCALE_TUNING.id
    private val sampleModes = Sf2Generator.SAMPLE_MODES.id
    private val releaseVolEnv = Sf2Generator.RELEASE_VOL_ENV.id
    private val filterFc = Sf2Generator.INITIAL_FILTER_FC.id
    private val rootKey = Sf2Generator.OVERRIDING_ROOT_KEY.id

    private fun writer(
        instrumentZones: List<List<Pair<Int, Int>>>,
        presetZones: List<List<Pair<Int, Int>>> = listOf(listOf(instrument to 0))
    ) = TestSf2Writer().apply {
        samples += TestSf2Writer.Sample("s", ShortArray(400) { 1000 }, loopStart = 100, loopEnd = 300)
        instruments += TestSf2Writer.Instrument("i", instrumentZones)
        presets += TestSf2Writer.Preset("p", 0, 0, presetZones)
    }

    /** Régions du preset 0:0 lues par les deux parseurs (qui doivent être d'accord). */
    private fun regions(w: TestSf2Writer): List<Sf2Region> {
        val bytes = w.build()
        val full = Sf2Parser().parse(bytes).getPreset(0, 0)?.regions.orEmpty()

        val file = File.createTempFile("layering", ".sf2")
        try {
            file.writeBytes(bytes)
            val parser = Sf2StreamingParser()
            val streamed = parser.loadWithMemoryMapping(parser.parseMetadata(file))
            val mmap = streamed.getPreset(0, 0)?.regions.orEmpty()
            streamed.close()
            assertEquals("les deux parseurs doivent produire les mêmes régions", full.size, mmap.size)
            full.zip(mmap).forEach { (a, b) ->
                assertEquals(a.fineTune, b.fineTune)
                assertEquals(a.attenuation, b.attenuation)
                assertEquals(a.scaleTuning, b.scaleTuning)
                assertEquals(a.hasLoop, b.hasLoop)
                assertEquals(a.keyRange, b.keyRange)
                assertEquals(a.volumeEnvelope, b.volumeEnvelope)
                assertEquals(a.filterFc, b.filterFc)
                assertEquals(a.rootKey, b.rootKey)
            }
        } finally {
            file.delete()
        }
        return full
    }

    @Test
    fun localZoneReplacesGlobalZoneInsteadOfAddingToIt() {
        val r = regions(writer(listOf(
            listOf(fineTune to 10, attenuation to 50, scaleTuning to 50),
            listOf(fineTune to 5, attenuation to 30, sampleId to 0)
        ))).single()

        assertEquals("fine tune local remplace le global (avant : 15)", 5, r.fineTune)
        assertEquals("atténuation locale remplace la globale (avant : 80)", 30, r.attenuation)
        assertEquals("scale tuning global conservé (avant : perdu, remis à 100)", 50, r.scaleTuning)
    }

    @Test
    fun localZoneCanTurnOffGlobalLoop() {
        val r = regions(writer(listOf(
            listOf(sampleModes to 1),
            listOf(sampleModes to 0, sampleId to 0)
        ))).single()
        assertEquals("sampleModes = 0 local doit désactiver la boucle globale", false, r.hasLoop)
    }

    @Test
    fun presetGeneratorsAreAddedToInstrumentValues() {
        val r = regions(writer(
            instrumentZones = listOf(listOf(attenuation to 20, releaseVolEnv to -1200, sampleId to 0)),
            presetZones = listOf(listOf(attenuation to 30, releaseVolEnv to 1200, instrument to 0))
        )).single()

        assertEquals(50, r.attenuation)
        // -1200 + 1200 timecents = 1 s
        assertEquals(1f, r.volumeEnvelope!!.release, 1e-4f)
    }

    @Test
    fun presetOffsetWithoutInstrumentValueStartsFromSpecDefault() {
        val r = regions(writer(
            instrumentZones = listOf(listOf(sampleId to 0)),
            presetZones = listOf(listOf(filterFc to -2400, instrument to 0))
        )).single()
        // 13500 (défaut de la norme) - 2400, et non -2400 pris comme valeur absolue
        assertEquals(11100, r.filterFc)
    }

    @Test
    fun sampleGeneratorsAreIgnoredAtPresetLevel() {
        val r = regions(writer(
            instrumentZones = listOf(listOf(sampleId to 0)),
            presetZones = listOf(listOf(sampleModes to 1, rootKey to 72, instrument to 0))
        )).single()
        assertEquals(false, r.hasLoop)
        assertEquals(60, r.rootKey)
    }

    @Test
    fun disjointKeyRangesProduceNoRegion() {
        val result = regions(writer(
            instrumentZones = listOf(listOf(keyRange to range(60, 127), sampleId to 0)),
            presetZones = listOf(
                listOf(keyRange to range(0, 59), attenuation to 100, instrument to 0),
                listOf(instrument to 0)
            )
        ))
        // Avant : la première zone de preset donnait en plus une région 60..60 atténuée,
        // qui se superposait à la touche 60
        assertEquals(1, result.size)
        assertEquals(60..127, result.single().keyRange)
        assertEquals(0, result.single().attenuation)
    }

    @Test
    fun overlappingKeyRangesIntersect() {
        val r = regions(writer(
            instrumentZones = listOf(listOf(keyRange to range(40, 80), sampleId to 0)),
            presetZones = listOf(listOf(keyRange to range(60, 100), instrument to 0))
        )).single()
        assertEquals(60..80, r.keyRange)
    }

    @Test
    fun onlyTheFirstZoneCanBeGlobal() {
        val result = regions(writer(listOf(
            listOf(sampleId to 0),
            listOf(attenuation to 100),       // zone sans échantillon en 2e position : ignorée
            listOf(fineTune to 3, sampleId to 0)
        )))
        assertEquals(2, result.size)
        assertEquals(0, result[1].attenuation)
        assertEquals(3, result[1].fineTune)
    }

    @Test
    fun soundEffectWithSmallScaleTuningIsKept() {
        // Cas réel (TimGM6mb, Applause / Helicopter) : root key 111, 10 cents par touche
        val r = regions(writer(listOf(
            listOf(keyRange to range(0, 108), rootKey to 111, scaleTuning to 10, sampleId to 0)
        ))).single()
        assertEquals(111, r.rootKey)
        assertEquals(10, r.scaleTuning)
    }

    @Test
    fun rootKeyMinusOneMeansSampleRootKey() {
        val r = regions(writer(listOf(listOf(rootKey to -1, sampleId to 0)))).single()
        assertEquals(60, r.rootKey)
    }

    @Test
    fun unsetVolumeEnvelopeStaysNullAndSetOneIsKept() {
        val none = regions(writer(listOf(listOf(sampleId to 0)))).single()
        assertNull(none.volumeEnvelope)

        val some = regions(writer(listOf(listOf(releaseVolEnv to 0, sampleId to 0)))).single()
        assertNotNull(some.volumeEnvelope)
        assertEquals(1f, some.volumeEnvelope!!.release, 1e-4f)
        assertEquals(1f, some.volumeEnvelope!!.sustain, 1e-6f)
    }
}
