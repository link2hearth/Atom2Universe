package com.Atom2Universe.app.sf2creator.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an SF2 Modulator.
 * Modulators define how MIDI controllers and other sources modulate synthesis parameters.
 *
 * SF2 Modulator Structure (10 bytes each):
 * - srcOper (2 bytes): Source modulator (CC number, LFO, envelope, etc.)
 * - destOper (2 bytes): Destination generator (pitch, filter, volume, etc.)
 * - amount (2 bytes signed): Modulation amount
 * - amtSrcOper (2 bytes): Secondary source (for bipolar modulation)
 * - transOper (2 bytes): Transform type (linear, concave, convex, switch)
 *
 * Modulators can be attached to:
 * - A program (programId != null) - Global preset-level modulator (pmod global zone)
 * - A preset zone (presetId != null) - Preset zone modulator (pmod)
 * - An instrument (instrumentId != null) - Instrument global zone modulator (imod global)
 * - A sample (sampleId != null) - Sample zone modulator (imod)
 *
 * Source Operators (srcOper):
 * - Bits 0-6: Controller index (0 = no controller, 1 = note velocity, 2 = note key,
 *             3-5 = reserved, 6-119 = MIDI CC, 120-127 = reserved)
 * - Bit 7: Direction (0 = min to max, 1 = max to min)
 * - Bit 8: Polarity (0 = unipolar, 1 = bipolar)
 * - Bits 9-10: Type (0 = linear, 1 = concave, 2 = convex, 3 = switch)
 * - Bit 15: CC flag (0 = general controller, 1 = MIDI CC)
 *
 * Common source values:
 * - 0x0000: No controller (constant)
 * - 0x0002: Note-on velocity
 * - 0x0003: Note-on key number
 * - 0x000A: Poly pressure
 * - 0x000D: Channel pressure
 * - 0x000E: Pitch wheel
 * - 0x0081: MIDI CC 1 (Modulation wheel)
 * - 0x0087: MIDI CC 7 (Volume)
 * - 0x008A: MIDI CC 10 (Pan)
 * - 0x008B: MIDI CC 11 (Expression)
 * - 0x00C0: MIDI CC 64 (Sustain pedal)
 */
@Entity(
    tableName = "sf2_modulators",
    foreignKeys = [
        ForeignKey(
            entity = Sf2ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Sf2PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Sf2InstrumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Sf2SampleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sampleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("programId"), Index("presetId"), Index("instrumentId"), Index("sampleId")]
)
data class Sf2ModulatorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Parent reference - exactly one should be non-null
    val programId: Long? = null,      // For global preset-level modulators (pmod global zone)
    val presetId: Long? = null,       // For preset zone modulators (pmod)
    val instrumentId: Long? = null,   // For instrument global zone modulators (imod global)
    val sampleId: Long? = null,       // For sample zone modulators (imod)

    // SF2 Modulator fields (10 bytes total in SF2 file)
    val srcOper: Int,             // Source operator (controller type + flags)
    val destOper: Int,            // Destination generator number
    val amount: Int,              // Modulation amount (signed)
    val amtSrcOper: Int,          // Amount source operator (for bipolar)
    val transOper: Int            // Transform operator
) {
    companion object {
        // ==================== Source Controller Types ====================

        /** No controller (constant modulation) */
        const val SRC_NONE = 0x0000

        /** Note-on velocity */
        const val SRC_NOTE_VELOCITY = 0x0002

        /** Note-on key number */
        const val SRC_NOTE_KEY = 0x0003

        /** Polyphonic pressure (aftertouch per note) */
        const val SRC_POLY_PRESSURE = 0x000A

        /** Channel pressure (aftertouch) */
        const val SRC_CHANNEL_PRESSURE = 0x000D

        /** Pitch wheel */
        const val SRC_PITCH_WHEEL = 0x000E

        /** Pitch wheel sensitivity */
        const val SRC_PITCH_WHEEL_SENSITIVITY = 0x0010

        /** MIDI CC flag - add this to CC number */
        const val SRC_MIDI_CC_FLAG = 0x0080

        // Common MIDI CCs
        /** MIDI CC 1 - Modulation wheel */
        const val SRC_CC_MODULATION = SRC_MIDI_CC_FLAG or 1

        /** MIDI CC 7 - Volume */
        const val SRC_CC_VOLUME = SRC_MIDI_CC_FLAG or 7

        /** MIDI CC 10 - Pan */
        const val SRC_CC_PAN = SRC_MIDI_CC_FLAG or 10

        /** MIDI CC 11 - Expression */
        const val SRC_CC_EXPRESSION = SRC_MIDI_CC_FLAG or 11

        /** MIDI CC 64 - Sustain pedal */
        const val SRC_CC_SUSTAIN = SRC_MIDI_CC_FLAG or 64

        /** MIDI CC 91 - Reverb send */
        const val SRC_CC_REVERB = SRC_MIDI_CC_FLAG or 91

        /** MIDI CC 93 - Chorus send */
        const val SRC_CC_CHORUS = SRC_MIDI_CC_FLAG or 93

        // ==================== Source Flags ====================

        /** Direction flag: max to min (inverted) */
        const val SRC_FLAG_DIRECTION = 0x0100

        /** Polarity flag: bipolar (-1 to +1) instead of unipolar (0 to +1) */
        const val SRC_FLAG_BIPOLAR = 0x0200

        /** Type mask for extracting type bits */
        const val SRC_TYPE_MASK = 0x0C00

        /** Linear type */
        const val SRC_TYPE_LINEAR = 0x0000

        /** Concave type (logarithmic) */
        const val SRC_TYPE_CONCAVE = 0x0400

        /** Convex type (exponential) */
        const val SRC_TYPE_CONVEX = 0x0800

        /** Switch type (on/off) */
        const val SRC_TYPE_SWITCH = 0x0C00

        // ==================== Transform Types ====================

        /** Linear transform */
        const val TRANS_LINEAR = 0

        /** Absolute value transform */
        const val TRANS_ABSOLUTE = 2

        // ==================== Default Modulators ====================
        // SF2 spec defines 10 default modulators that are always present

        /**
         * Create default MIDI velocity to initial attenuation modulator.
         * Velocity 127 = 0 dB, velocity 1 = -48 dB (approx)
         */
        fun defaultVelocityToAttenuation(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_NOTE_VELOCITY or SRC_TYPE_CONCAVE,
            destOper = 48, // GEN_INITIAL_ATTENUATION
            amount = 960,  // 96 dB range
            amtSrcOper = SRC_NONE,
            transOper = TRANS_LINEAR
        )

        /**
         * Create default MIDI velocity to filter cutoff modulator.
         */
        fun defaultVelocityToFilter(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_NOTE_VELOCITY or SRC_FLAG_DIRECTION or SRC_TYPE_LINEAR,
            destOper = 8, // GEN_INITIAL_FILTER_FC
            amount = -2400, // -2 octaves
            amtSrcOper = SRC_NONE,
            transOper = TRANS_LINEAR
        )

        /**
         * Create default channel pressure to vibrato LFO pitch modulator.
         */
        fun defaultPressureToVibrato(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_CHANNEL_PRESSURE or SRC_TYPE_LINEAR,
            destOper = 6, // GEN_VIB_LFO_TO_PITCH
            amount = 50,  // 50 cents
            amtSrcOper = SRC_NONE,
            transOper = TRANS_LINEAR
        )

        /**
         * Create default CC1 (mod wheel) to vibrato LFO pitch modulator.
         */
        fun defaultModWheelToVibrato(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_CC_MODULATION or SRC_TYPE_LINEAR,
            destOper = 6, // GEN_VIB_LFO_TO_PITCH
            amount = 50,  // 50 cents
            amtSrcOper = SRC_NONE,
            transOper = TRANS_LINEAR
        )

        /**
         * Create default CC7 (volume) to initial attenuation modulator.
         */
        fun defaultVolumeToAttenuation(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_CC_VOLUME or SRC_TYPE_CONCAVE or SRC_FLAG_DIRECTION,
            destOper = 48, // GEN_INITIAL_ATTENUATION
            amount = 960,  // 96 dB range
            amtSrcOper = SRC_NONE,
            transOper = TRANS_LINEAR
        )

        /**
         * Create default CC10 (pan) to pan modulator.
         */
        fun defaultPanCCToPan(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_CC_PAN or SRC_TYPE_LINEAR or SRC_FLAG_BIPOLAR,
            destOper = 17, // GEN_PAN
            amount = 500,  // Full pan range
            amtSrcOper = SRC_NONE,
            transOper = TRANS_LINEAR
        )

        /**
         * Create default CC11 (expression) to initial attenuation modulator.
         */
        fun defaultExpressionToAttenuation(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_CC_EXPRESSION or SRC_TYPE_CONCAVE or SRC_FLAG_DIRECTION,
            destOper = 48, // GEN_INITIAL_ATTENUATION
            amount = 960,  // 96 dB range
            amtSrcOper = SRC_NONE,
            transOper = TRANS_LINEAR
        )

        /**
         * Create default CC91 (reverb) to reverb send modulator.
         */
        fun defaultReverbCCToReverb(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_CC_REVERB or SRC_TYPE_LINEAR,
            destOper = 16, // GEN_REVERB_EFFECTS_SEND
            amount = 200,  // 20%
            amtSrcOper = SRC_NONE,
            transOper = TRANS_LINEAR
        )

        /**
         * Create default CC93 (chorus) to chorus send modulator.
         */
        fun defaultChorusCCToChorus(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_CC_CHORUS or SRC_TYPE_LINEAR,
            destOper = 15, // GEN_CHORUS_EFFECTS_SEND
            amount = 200,  // 20%
            amtSrcOper = SRC_NONE,
            transOper = TRANS_LINEAR
        )

        /**
         * Create default pitch wheel to initial pitch modulator.
         * Default pitch bend range is +/- 2 semitones (200 cents)
         */
        fun defaultPitchWheelToPitch(sampleId: Long? = null, instrumentId: Long? = null) = Sf2ModulatorEntity(
            instrumentId = instrumentId,
            sampleId = sampleId,
            srcOper = SRC_PITCH_WHEEL or SRC_TYPE_LINEAR or SRC_FLAG_BIPOLAR,
            destOper = 52, // GEN_FINE_TUNE (or a dedicated pitch generator)
            amount = 12700, // ~2 semitones in cents (127 * 100)
            amtSrcOper = SRC_PITCH_WHEEL_SENSITIVITY,
            transOper = TRANS_LINEAR
        )
    }
}
