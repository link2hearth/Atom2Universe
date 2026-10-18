package com.Atom2Universe.app.sf2creator.writer

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level writer for SF2 RIFF chunks and data structures.
 * SF2 files use RIFF format with little-endian byte order.
 */
class Sf2ChunkWriter(private val raf: RandomAccessFile) {

    companion object {
        // SF2 Version 2.01
        const val SF2_VERSION_MAJOR = 2
        const val SF2_VERSION_MINOR = 1

        // Standard sound engine name
        const val SOUND_ENGINE = "EMU8000"

        // SF2 Generator types (sfGenOper)
        const val GEN_START_ADDRS_OFFSET = 0
        const val GEN_END_ADDRS_OFFSET = 1
        const val GEN_STARTLOOP_ADDRS_OFFSET = 2
        const val GEN_ENDLOOP_ADDRS_OFFSET = 3
        const val GEN_START_ADDRS_COARSE_OFFSET = 4
        const val GEN_MOD_LFO_TO_PITCH = 5
        const val GEN_VIB_LFO_TO_PITCH = 6
        const val GEN_MOD_ENV_TO_PITCH = 7
        const val GEN_INITIAL_FILTER_FC = 8
        const val GEN_INITIAL_FILTER_Q = 9
        const val GEN_MOD_LFO_TO_FILTER_FC = 10
        const val GEN_MOD_ENV_TO_FILTER_FC = 11
        const val GEN_END_ADDRS_COARSE_OFFSET = 12
        const val GEN_MOD_LFO_TO_VOLUME = 13
        const val GEN_CHORUS_EFFECTS_SEND = 15
        const val GEN_REVERB_EFFECTS_SEND = 16
        const val GEN_PAN = 17
        const val GEN_DELAY_MOD_LFO = 21
        const val GEN_FREQ_MOD_LFO = 22
        const val GEN_DELAY_VIB_LFO = 23
        const val GEN_FREQ_VIB_LFO = 24
        const val GEN_DELAY_MOD_ENV = 25
        const val GEN_ATTACK_MOD_ENV = 26
        const val GEN_HOLD_MOD_ENV = 27
        const val GEN_DECAY_MOD_ENV = 28
        const val GEN_SUSTAIN_MOD_ENV = 29
        const val GEN_RELEASE_MOD_ENV = 30
        const val GEN_KEYNUM_TO_MOD_ENV_HOLD = 31
        const val GEN_KEYNUM_TO_MOD_ENV_DECAY = 32
        const val GEN_DELAY_VOL_ENV = 33
        const val GEN_ATTACK_VOL_ENV = 34
        const val GEN_HOLD_VOL_ENV = 35
        const val GEN_DECAY_VOL_ENV = 36
        const val GEN_SUSTAIN_VOL_ENV = 37
        const val GEN_RELEASE_VOL_ENV = 38
        const val GEN_KEYNUM_TO_VOL_ENV_HOLD = 39
        const val GEN_KEYNUM_TO_VOL_ENV_DECAY = 40
        const val GEN_INSTRUMENT = 41
        const val GEN_KEY_RANGE = 43
        const val GEN_VEL_RANGE = 44
        const val GEN_STARTLOOP_ADDRS_COARSE_OFFSET = 45
        const val GEN_FIXED_KEY = 46          // Fixed key (keynum) - overrides played note
        const val GEN_FIXED_VELOCITY = 47     // Fixed velocity - overrides played velocity
        const val GEN_INITIAL_ATTENUATION = 48
        const val GEN_ENDLOOP_ADDRS_COARSE_OFFSET = 50
        const val GEN_COARSE_TUNE = 51
        const val GEN_FINE_TUNE = 52
        const val GEN_SAMPLE_ID = 53
        const val GEN_SAMPLE_MODES = 54
        const val GEN_SCALE_TUNING = 56
        const val GEN_EXCLUSIVE_CLASS = 57
        const val GEN_OVERRIDING_ROOT_KEY = 58

        // Sample types
        const val SAMPLE_TYPE_MONO = 1
        const val SAMPLE_TYPE_RIGHT = 2
        const val SAMPLE_TYPE_LEFT = 4
        const val SAMPLE_TYPE_LINKED = 8
        const val SAMPLE_TYPE_ROM_MONO = 0x8001
        const val SAMPLE_TYPE_ROM_RIGHT = 0x8002
        const val SAMPLE_TYPE_ROM_LEFT = 0x8004
        const val SAMPLE_TYPE_ROM_LINKED = 0x8008
    }

    private val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)

    /**
     * Write a 4-character chunk ID.
     */
    fun writeChunkId(id: String) {
        require(id.length == 4) { "Chunk ID must be exactly 4 characters" }
        raf.writeBytes(id)
    }

    /**
     * Write a 32-bit unsigned integer (as little-endian).
     */
    fun writeUInt32(value: Long) {
        buffer.clear()
        buffer.putInt(value.toInt())
        raf.write(buffer.array(), 0, 4)
    }

    /**
     * Write a 32-bit signed integer (as little-endian).
     */
    fun writeInt32(value: Int) {
        buffer.clear()
        buffer.putInt(value)
        raf.write(buffer.array(), 0, 4)
    }

    /**
     * Write a 16-bit unsigned integer (as little-endian).
     */
    fun writeUInt16(value: Int) {
        buffer.clear()
        buffer.putShort(value.toShort())
        raf.write(buffer.array(), 0, 2)
    }

    /**
     * Write a 16-bit signed integer (as little-endian).
     */
    fun writeInt16(value: Short) {
        buffer.clear()
        buffer.putShort(value)
        raf.write(buffer.array(), 0, 2)
    }

    /**
     * Write an 8-bit unsigned integer.
     */
    fun writeUInt8(value: Int) {
        raf.write(value and 0xFF)
    }

    /**
     * Write an 8-bit signed integer.
     */
    fun writeInt8(value: Int) {
        raf.write(value)
    }

    /**
     * Write a null-terminated string padded to a specific length.
     */
    fun writeString(value: String, length: Int) {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        val padded = ByteArray(length)
        System.arraycopy(bytes, 0, padded, 0, minOf(bytes.size, length - 1))
        raf.write(padded)
    }

    /**
     * Write raw bytes.
     */
    fun writeBytes(data: ByteArray) {
        raf.write(data)
    }

    /**
     * Write PCM samples (16-bit little-endian).
     */
    fun writeSamples(samples: ShortArray) {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        raf.write(bytes)
    }

    /**
     * Get the current file position.
     */
    fun getPosition(): Long = raf.filePointer

    /**
     * Seek to a specific position.
     */
    fun seek(position: Long) {
        raf.seek(position)
    }

    /**
     * Write the SF2 INFO chunk.
     *
     * @param instrumentName Name of the instrument/soundfont
     * @param comment Optional comment
     * @return Size of the INFO chunk (excluding LIST header)
     */
    fun writeInfoChunk(instrumentName: String, comment: String = ""): Long {
        val startPos = getPosition()

        // ifil - SF2 version
        writeChunkId("ifil")
        writeUInt32(4) // size
        writeUInt16(SF2_VERSION_MAJOR)
        writeUInt16(SF2_VERSION_MINOR)

        // isng - Sound engine
        writeChunkId("isng")
        val engineBytes = SOUND_ENGINE.toByteArray(Charsets.US_ASCII)
        val engineSize = ((engineBytes.size + 1 + 1) / 2) * 2 // Pad to even size
        writeUInt32(engineSize.toLong())
        raf.write(engineBytes)
        // Null terminator and padding
        for (i in engineBytes.size until engineSize) {
            raf.write(0)
        }

        // INAM - Instrument name
        writeChunkId("INAM")
        val nameBytes = instrumentName.toByteArray(Charsets.US_ASCII)
        val nameSize = ((nameBytes.size + 1 + 1) / 2) * 2 // Pad to even size
        writeUInt32(nameSize.toLong())
        raf.write(nameBytes)
        for (i in nameBytes.size until nameSize) {
            raf.write(0)
        }

        // ICMT - Comment (optional)
        if (comment.isNotEmpty()) {
            writeChunkId("ICMT")
            val commentBytes = comment.toByteArray(Charsets.US_ASCII)
            val commentSize = ((commentBytes.size + 1 + 1) / 2) * 2
            writeUInt32(commentSize.toLong())
            raf.write(commentBytes)
            for (i in commentBytes.size until commentSize) {
                raf.write(0)
            }
        }

        // ISFT - Software (creator)
        writeChunkId("ISFT")
        val softwareBytes = "A2U SF2 Creator".toByteArray(Charsets.US_ASCII)
        val softwareSize = ((softwareBytes.size + 1 + 1) / 2) * 2
        writeUInt32(softwareSize.toLong())
        raf.write(softwareBytes)
        for (i in softwareBytes.size until softwareSize) {
            raf.write(0)
        }

        return getPosition() - startPos
    }

    /**
     * Write a preset header (phdr).
     * 38 bytes per preset.
     */
    fun writePresetHeader(
        name: String,
        preset: Int,
        bank: Int,
        presetBagIndex: Int,
        library: Int = 0,
        genre: Int = 0,
        morphology: Int = 0
    ) {
        writeString(name, 20) // achPresetName
        writeUInt16(preset)   // wPreset
        writeUInt16(bank)     // wBank
        writeUInt16(presetBagIndex) // wPresetBagNdx
        writeUInt32(library.toLong())    // dwLibrary
        writeUInt32(genre.toLong())      // dwGenre
        writeUInt32(morphology.toLong()) // dwMorphology
    }

    /**
     * Write an instrument header (inst).
     * 22 bytes per instrument.
     */
    fun writeInstrumentHeader(name: String, instBagIndex: Int) {
        writeString(name, 20) // achInstName
        writeUInt16(instBagIndex) // wInstBagNdx
    }

    /**
     * Write a sample header (shdr).
     * 46 bytes per sample.
     */
    fun writeSampleHeader(
        name: String,
        start: Long,
        end: Long,
        loopStart: Long,
        loopEnd: Long,
        sampleRate: Int,
        originalPitch: Int,
        pitchCorrection: Int,
        sampleLink: Int,
        sampleType: Int
    ) {
        writeString(name, 20)       // achSampleName
        writeUInt32(start)          // dwStart
        writeUInt32(end)            // dwEnd
        writeUInt32(loopStart)      // dwStartloop
        writeUInt32(loopEnd)        // dwEndloop
        writeUInt32(sampleRate.toLong()) // dwSampleRate
        writeUInt8(originalPitch)   // byOriginalPitch (MIDI note)
        writeInt8(pitchCorrection)  // chPitchCorrection (cents)
        writeUInt16(sampleLink)     // wSampleLink
        writeUInt16(sampleType)     // sfSampleType
    }

    /**
     * Write a bag (pbag or ibag).
     * 4 bytes per bag.
     */
    fun writeBag(genIndex: Int, modIndex: Int) {
        writeUInt16(genIndex) // wGenNdx
        writeUInt16(modIndex) // wModNdx
    }

    /**
     * Write a generator (pgen or igen).
     * 4 bytes per generator.
     */
    fun writeGenerator(genOper: Int, amount: Int) {
        writeUInt16(genOper)  // sfGenOper
        writeInt16(amount.toShort()) // genAmount (can be signed)
    }

    /**
     * Write a generator with a range amount (for key/velocity ranges).
     */
    fun writeGeneratorRange(genOper: Int, lo: Int, hi: Int) {
        writeUInt16(genOper)
        writeUInt8(lo)
        writeUInt8(hi)
    }

    /**
     * Write a modulator (pmod or imod).
     * 10 bytes per modulator.
     */
    fun writeModulator(
        srcOper: Int,
        destOper: Int,
        amount: Int,
        amtSrcOper: Int,
        transOper: Int
    ) {
        writeUInt16(srcOper)
        writeUInt16(destOper)
        writeInt16(amount.toShort())
        writeUInt16(amtSrcOper)
        writeUInt16(transOper)
    }

    /**
     * Write padding bytes if needed to make position even.
     */
    fun padToEven() {
        if (getPosition() % 2 != 0L) {
            raf.write(0)
        }
    }
}
