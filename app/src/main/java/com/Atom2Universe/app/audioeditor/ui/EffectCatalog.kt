package com.Atom2Universe.app.audioeditor.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import com.Atom2Universe.app.audioeditor.core.FadeShape
import com.Atom2Universe.app.audioeditor.dsp.Amplify
import com.Atom2Universe.app.audioeditor.dsp.BassTreble
import com.Atom2Universe.app.audioeditor.dsp.BitCrusher
import com.Atom2Universe.app.audioeditor.dsp.ChangePitch
import com.Atom2Universe.app.audioeditor.dsp.ChangeSpeed
import com.Atom2Universe.app.audioeditor.dsp.ChangeTempo
import com.Atom2Universe.app.audioeditor.dsp.Chorus
import com.Atom2Universe.app.audioeditor.dsp.ClickTrackReader
import com.Atom2Universe.app.audioeditor.dsp.Compressor
import com.Atom2Universe.app.audioeditor.dsp.Distortion
import com.Atom2Universe.app.audioeditor.dsp.DistortionKind
import com.Atom2Universe.app.audioeditor.dsp.Echo
import com.Atom2Universe.app.audioeditor.dsp.Effect
import com.Atom2Universe.app.audioeditor.dsp.FadeEffect
import com.Atom2Universe.app.audioeditor.dsp.FilterEffect
import com.Atom2Universe.app.audioeditor.dsp.Flanger
import com.Atom2Universe.app.audioeditor.dsp.FrameReader
import com.Atom2Universe.app.audioeditor.dsp.GRAPHIC_EQ_BANDS
import com.Atom2Universe.app.audioeditor.dsp.GraphicEq
import com.Atom2Universe.app.audioeditor.dsp.Invert
import com.Atom2Universe.app.audioeditor.dsp.Limiter
import com.Atom2Universe.app.audioeditor.dsp.NoiseGate
import com.Atom2Universe.app.audioeditor.dsp.NoiseKind
import com.Atom2Universe.app.audioeditor.dsp.NoiseProfile
import com.Atom2Universe.app.audioeditor.dsp.NoiseReader
import com.Atom2Universe.app.audioeditor.dsp.NoiseReduction
import com.Atom2Universe.app.audioeditor.dsp.Normalize
import com.Atom2Universe.app.audioeditor.dsp.PaulStretch
import com.Atom2Universe.app.audioeditor.dsp.Phaser
import com.Atom2Universe.app.audioeditor.dsp.RemoveDc
import com.Atom2Universe.app.audioeditor.dsp.Reverb
import com.Atom2Universe.app.audioeditor.dsp.StereoToMono
import com.Atom2Universe.app.audioeditor.dsp.SwapChannels
import com.Atom2Universe.app.audioeditor.dsp.ToneReader
import com.Atom2Universe.app.audioeditor.dsp.ToneShape
import com.Atom2Universe.app.audioeditor.dsp.Tremolo
import com.Atom2Universe.app.audioeditor.dsp.TruncateSilence
import com.Atom2Universe.app.audioeditor.dsp.Vibrato
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Le registre des effets : chaque effet est une donnée (titre, paramètres, fabrique), et une seule
 * feuille générique (voir EffectSheets) montre les curseurs de n'importe lequel. Ajouter un effet =
 * ajouter une ligne ici, rien à dessiner.
 *
 * Ce fichier ne touche à aucune vue : on peut le tester en JVM (construire chaque effet avec ses valeurs
 * par défaut et le faire tourner sur quelques échantillons).
 */

/** L'unité affichée après la valeur d'un curseur ; [format] est un texte à un seul `%1$s` (0 = la valeur nue). */
enum class ParamUnit(@StringRes val format: Int) {
    NONE(0),
    DB(R.string.ae_unit_db),
    HZ(R.string.ae_unit_hz),
    MS(R.string.ae_unit_ms),
    SECONDS(R.string.ae_unit_s),
    PERCENT(R.string.ae_unit_percent),
    RATIO(R.string.ae_unit_ratio),
    SEMITONES(R.string.ae_unit_semitones),
    DB_OCT(R.string.ae_unit_db_oct),
    TIMES(R.string.ae_unit_times),
    BPM(R.string.ae_unit_bpm),
    BITS(R.string.ae_unit_bits),
}

sealed class Param(val id: String, @StringRes val label: Int, val labelArg: String?) {
    /** La valeur de départ, telle qu'elle est rangée dans [Values] (un choix = son rang, une case = 0 ou 1). */
    abstract val default: Float
}

/**
 * Un curseur entre [min] et [max]. Avec [log], la course est exponentielle (fréquences, durées : autant de
 * place pour 100 → 1 000 que pour 1 000 → 10 000). [step] est le pas de la valeur retenue.
 */
class SliderParam(
    id: String,
    @StringRes label: Int,
    val min: Float,
    val max: Float,
    override val default: Float,
    val unit: ParamUnit = ParamUnit.NONE,
    val step: Float = 1f,
    val log: Boolean = false,
    labelArg: String? = null,
) : Param(id, label, labelArg) {

    /** La valeur au point [t] (0…1) de la course. */
    fun valueAt(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        val raw = if (log) min * (max / min).pow(c) else min + c * (max - min)
        val snapped = (raw / step).roundToInt() * step
        return snapped.coerceIn(min, max)
    }

    /** Où se trouve [v] sur la course (0…1). */
    fun fractionOf(v: Float): Float {
        val c = v.coerceIn(min, max)
        return if (log) (kotlin.math.ln(c / min) / kotlin.math.ln(max / min)) else (c - min) / (max - min)
    }
}

/** Un choix parmi plusieurs ; chaque option est un texte. */
class ChoiceParam(id: String, @StringRes label: Int, val options: List<Int>, default: Int = 0) : Param(id, label, null) {
    override val default: Float = default.toFloat()
}

/** Une case à cocher. */
class ToggleParam(id: String, @StringRes label: Int, on: Boolean) : Param(id, label, null) {
    override val default: Float = if (on) 1f else 0f
}

/** Les réglages choisis pour un effet. Un choix est rangé par son rang, une case par 0 / 1. */
class Values(private val map: MutableMap<String, Float>) {
    fun f(id: String): Float = map.getValue(id)
    fun i(id: String): Int = f(id).roundToInt()
    fun b(id: String): Boolean = f(id) >= 0.5f
    operator fun set(id: String, v: Float) { map[id] = v }

    companion object {
        fun defaults(params: List<Param>) = Values(params.associate { it.id to it.default }.toMutableMap())
    }
}

class EffectDef(
    val id: String,
    @StringRes val title: Int,
    val params: List<Param> = emptyList(),
    /** Vrai pour la réduction de bruit : il faut d'abord capturer le profil du bruit. */
    val needsProfile: Boolean = false,
    /** Fabrique l'effet ; `null` s'il manque ce dont il a besoin (le profil de bruit). */
    val build: (Values, NoiseProfile?) -> Effect?,
)

class EffectCategory(val id: String, @StringRes val title: Int, @DrawableRes val icon: Int, val effects: List<EffectDef>)

/**
 * Un générateur de signal : un lecteur mono de [frames] trames. [insertsSilence] marque le silence, qui ne
 * crée aucune source mais ouvre un trou à la place du curseur.
 */
class GeneratorDef(
    val id: String,
    @StringRes val title: Int,
    val params: List<Param>,
    val insertsSilence: Boolean = false,
    val build: (Values, sampleRate: Int, frames: Long) -> FrameReader?,
)

object EffectCatalog {

    private const val PCT = 100f

    private fun db(id: String, @StringRes label: Int, min: Float, max: Float, def: Float, step: Float = 0.1f) =
        SliderParam(id, label, min, max, def, ParamUnit.DB, step)

    private fun pct(id: String, @StringRes label: Int, def: Float, max: Float = 100f) =
        SliderParam(id, label, 0f, max, def, ParamUnit.PERCENT, 1f)

    private fun ms(id: String, @StringRes label: Int, min: Float, max: Float, def: Float, step: Float = 0.1f, log: Boolean = false) =
        SliderParam(id, label, min, max, def, ParamUnit.MS, step, log)

    private fun hz(id: String, @StringRes label: Int, min: Float, max: Float, def: Float, step: Float = 0.01f, log: Boolean = false) =
        SliderParam(id, label, min, max, def, ParamUnit.HZ, step, log)

    private val fadeShapes = listOf(
        R.string.ae_shape_linear, R.string.ae_shape_exp, R.string.ae_shape_log, R.string.ae_shape_s, R.string.ae_shape_equal,
    )

    private fun shapeOf(v: Values): FadeShape = FadeShape.values()[v.i("shape").coerceIn(0, FadeShape.values().size - 1)]

    private val volume = EffectCategory(
        "volume", R.string.ae_cat_volume, R.drawable.ic_px_tune, listOf(
            EffectDef("amplify", R.string.ae_fx_amplify, listOf(db("gain", R.string.ae_p_gain, -40f, 40f, 3f))) { v, _ ->
                Amplify(v.f("gain"))
            },
            EffectDef(
                "normalize", R.string.ae_fx_normalize, listOf(
                    db("target", R.string.ae_p_target, -24f, 0f, -1f, 0.5f),
                    ChoiceParam("mode", R.string.ae_p_measure, listOf(R.string.ae_opt_peak, R.string.ae_opt_rms)),
                    ToggleParam("dc", R.string.ae_p_remove_dc, true),
                    ToggleParam("independent", R.string.ae_p_independent, false),
                    ToggleParam("limit", R.string.ae_p_limit_peak, true),
                ),
            ) { v, _ ->
                Normalize(
                    v.f("target"), v.b("dc"), v.b("independent"),
                    if (v.i("mode") == 1) Normalize.Mode.RMS else Normalize.Mode.PEAK, v.b("limit"),
                )
            },
            EffectDef("fade_in", R.string.ae_fx_fade_in, listOf(ChoiceParam("shape", R.string.ae_fade_curve, fadeShapes))) { v, _ ->
                FadeEffect(true, shapeOf(v))
            },
            EffectDef("fade_out", R.string.ae_fx_fade_out, listOf(ChoiceParam("shape", R.string.ae_fade_curve, fadeShapes))) { v, _ ->
                FadeEffect(false, shapeOf(v))
            },
            EffectDef("invert", R.string.ae_fx_invert) { _, _ -> Invert() },
            EffectDef("remove_dc", R.string.ae_p_remove_dc) { _, _ -> RemoveDc() },
        ),
    )

    private val channels = EffectCategory(
        "channels", R.string.ae_cat_channels, R.drawable.ic_px_mirror_h, listOf(
            EffectDef("mono", R.string.ae_fx_mono) { _, _ -> StereoToMono() },
            EffectDef("swap", R.string.ae_fx_swap) { _, _ -> SwapChannels() },
        ),
    )

    private val filters = EffectCategory(
        "filters", R.string.ae_cat_filters, R.drawable.ic_px_sampling, listOf(
            EffectDef(
                "filter", R.string.ae_fx_filter, listOf(
                    ChoiceParam("kind", R.string.ae_p_type, listOf(R.string.ae_opt_lowpass, R.string.ae_opt_highpass, R.string.ae_opt_bandpass, R.string.ae_opt_notch)),
                    hz("freq", R.string.ae_p_freq, 20f, 20000f, 1000f, 1f, log = true),
                    SliderParam("q", R.string.ae_p_q, 0.1f, 10f, 0.7f, ParamUnit.NONE, 0.1f),
                    SliderParam("slope", R.string.ae_p_slope, 12f, 48f, 12f, ParamUnit.DB_OCT, 12f),
                ),
            ) { v, _ ->
                FilterEffect(FilterEffect.Kind.values()[v.i("kind").coerceIn(0, 3)], v.f("freq"), v.f("q"), v.i("slope"))
            },
            EffectDef("graphic_eq", R.string.ae_fx_eq, GRAPHIC_EQ_BANDS.mapIndexed { i, f ->
                val khz = f >= 1000f
                val text = (if (khz) f / 1000f else f).let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() }
                SliderParam("band$i", if (khz) R.string.ae_band_khz else R.string.ae_band_hz, -15f, 15f, 0f, ParamUnit.DB, 0.5f, labelArg = text)
            }) { v, _ ->
                GraphicEq(FloatArray(GRAPHIC_EQ_BANDS.size) { v.f("band$it") })
            },
            EffectDef(
                "bass_treble", R.string.ae_fx_bass_treble, listOf(
                    db("bass", R.string.ae_p_bass, -15f, 15f, 0f, 0.5f),
                    db("treble", R.string.ae_p_treble, -15f, 15f, 0f, 0.5f),
                ),
            ) { v, _ -> BassTreble(v.f("bass"), v.f("treble")) },
        ),
    )

    private val dynamics = EffectCategory(
        "dynamics", R.string.ae_cat_dynamics, R.drawable.ic_px_layers, listOf(
            EffectDef(
                "compressor", R.string.ae_fx_compressor, listOf(
                    db("threshold", R.string.ae_p_threshold, -60f, 0f, -18f, 0.5f),
                    SliderParam("ratio", R.string.ae_p_ratio, 1f, 20f, 3f, ParamUnit.RATIO, 0.1f),
                    ms("attack", R.string.ae_p_attack, 0.1f, 200f, 10f, 0.1f, log = true),
                    ms("release", R.string.ae_p_release, 5f, 2000f, 150f, 1f, log = true),
                    db("knee", R.string.ae_p_knee, 0f, 24f, 6f, 0.5f),
                    db("makeup", R.string.ae_p_makeup, 0f, 24f, 0f, 0.5f),
                ),
            ) { v, _ -> Compressor(v.f("threshold"), v.f("ratio"), v.f("attack"), v.f("release"), v.f("knee"), v.f("makeup")) },
            EffectDef(
                "limiter", R.string.ae_fx_limiter, listOf(
                    db("ceiling", R.string.ae_p_ceiling, -20f, 0f, -1f, 0.5f),
                    ms("release", R.string.ae_p_release, 1f, 1000f, 100f, 1f, log = true),
                    ms("lookahead", R.string.ae_p_lookahead, 1f, 20f, 5f, 0.5f),
                ),
            ) { v, _ -> Limiter(v.f("ceiling"), v.f("release"), v.f("lookahead")) },
            EffectDef(
                "gate", R.string.ae_fx_gate, listOf(
                    db("threshold", R.string.ae_p_threshold, -80f, -10f, -45f, 0.5f),
                    db("reduction", R.string.ae_p_reduction, -80f, 0f, -80f, 1f),
                    ms("attack", R.string.ae_p_attack, 0.1f, 100f, 5f, 0.1f, log = true),
                    ms("hold", R.string.ae_p_hold, 0f, 500f, 50f, 1f),
                    ms("release", R.string.ae_p_release, 5f, 1000f, 120f, 1f, log = true),
                ),
            ) { v, _ -> NoiseGate(v.f("threshold"), v.f("reduction"), v.f("attack"), v.f("hold"), v.f("release")) },
        ),
    )

    private val timePitch = EffectCategory(
        "time", R.string.ae_cat_time, R.drawable.ic_px_film, listOf(
            EffectDef("speed", R.string.ae_fx_speed, listOf(SliderParam("factor", R.string.ae_p_speed, 0.25f, 4f, 1.25f, ParamUnit.TIMES, 0.01f, log = true))) { v, _ ->
                ChangeSpeed(v.f("factor"))
            },
            EffectDef("tempo", R.string.ae_fx_tempo, listOf(SliderParam("factor", R.string.ae_p_tempo, 0.5f, 2f, 1.25f, ParamUnit.TIMES, 0.01f, log = true))) { v, _ ->
                ChangeTempo(v.f("factor"))
            },
            EffectDef("pitch", R.string.ae_fx_pitch, listOf(SliderParam("semitones", R.string.ae_p_pitch, -12f, 12f, 2f, ParamUnit.SEMITONES, 0.1f))) { v, _ ->
                ChangePitch(v.f("semitones"))
            },
            EffectDef(
                "paulstretch", R.string.ae_fx_paulstretch, listOf(
                    SliderParam("stretch", R.string.ae_p_stretch, 1f, 100f, 8f, ParamUnit.TIMES, 0.5f, log = true),
                    SliderParam("window", R.string.ae_p_window, 0.05f, 1f, 0.25f, ParamUnit.SECONDS, 0.01f),
                ),
            ) { v, _ -> PaulStretch(v.f("stretch"), v.f("window")) },
            EffectDef(
                "truncate", R.string.ae_fx_truncate, listOf(
                    db("threshold", R.string.ae_p_threshold, -80f, -20f, -50f, 1f),
                    ms("min", R.string.ae_p_min_silence, 50f, 2000f, 200f, 10f, log = true),
                    ms("keep", R.string.ae_p_keep, 0f, 500f, 50f, 5f),
                ),
            ) { v, _ -> TruncateSilence(v.f("threshold"), v.f("min"), v.f("keep")) },
        ),
    )

    private val space = EffectCategory(
        "space", R.string.ae_cat_space, R.drawable.ic_px_onion, listOf(
            EffectDef(
                "echo", R.string.ae_fx_echo, listOf(
                    ms("delay", R.string.ae_p_delay, 10f, 2000f, 300f, 5f, log = true),
                    pct("decay", R.string.ae_p_decay, 50f, 95f),
                ),
            ) { v, _ -> Echo(v.f("delay"), v.f("decay") / PCT) },
            EffectDef(
                "reverb", R.string.ae_fx_reverb, listOf(
                    pct("room", R.string.ae_p_room, 50f),
                    pct("damping", R.string.ae_p_damping, 50f),
                    pct("wet", R.string.ae_p_wet, 33f),
                    pct("dry", R.string.ae_p_dry, 100f),
                    pct("width", R.string.ae_p_width, 100f),
                ),
            ) { v, _ -> Reverb(v.f("room") / PCT, v.f("damping") / PCT, v.f("wet") / PCT, v.f("dry") / PCT, v.f("width") / PCT) },
            EffectDef(
                "chorus", R.string.ae_fx_chorus, listOf(
                    hz("rate", R.string.ae_p_rate, 0.1f, 5f, 1.2f, 0.1f),
                    ms("depth", R.string.ae_p_depth, 0.5f, 10f, 3f, 0.5f),
                    ms("delay", R.string.ae_p_delay, 5f, 40f, 20f, 1f),
                    pct("mix", R.string.ae_p_mix, 50f),
                ),
            ) { v, _ -> Chorus(v.f("rate"), v.f("depth"), v.f("delay"), v.f("mix") / PCT) },
            EffectDef(
                "flanger", R.string.ae_fx_flanger, listOf(
                    hz("rate", R.string.ae_p_rate, 0.05f, 5f, 0.25f, 0.05f),
                    ms("depth", R.string.ae_p_depth, 0.1f, 10f, 2f, 0.1f),
                    pct("feedback", R.string.ae_p_feedback, 50f, 95f),
                    pct("mix", R.string.ae_p_mix, 80f),
                ),
            ) { v, _ -> Flanger(v.f("rate"), v.f("depth"), v.f("feedback") / PCT, v.f("mix") / PCT) },
            EffectDef(
                "vibrato", R.string.ae_fx_vibrato, listOf(
                    hz("rate", R.string.ae_p_rate, 0.5f, 14f, 5f, 0.1f),
                    ms("depth", R.string.ae_p_depth, 0.1f, 5f, 1f, 0.1f),
                ),
            ) { v, _ -> Vibrato(v.f("rate"), v.f("depth")) },
            EffectDef(
                "tremolo", R.string.ae_fx_tremolo, listOf(
                    hz("rate", R.string.ae_p_rate, 0.5f, 20f, 5f, 0.1f),
                    pct("depth", R.string.ae_p_depth, 70f),
                ),
            ) { v, _ -> Tremolo(v.f("rate"), v.f("depth") / PCT) },
            EffectDef(
                "phaser", R.string.ae_fx_phaser, listOf(
                    hz("rate", R.string.ae_p_rate, 0.05f, 5f, 0.4f, 0.05f),
                    SliderParam("stages", R.string.ae_p_stages, 2f, 12f, 4f, ParamUnit.NONE, 2f),
                    pct("depth", R.string.ae_p_depth, 80f),
                    pct("feedback", R.string.ae_p_feedback, 30f, 90f),
                    pct("mix", R.string.ae_p_mix, 50f),
                ),
            ) { v, _ -> Phaser(v.f("rate"), v.i("stages"), v.f("depth") / PCT, v.f("feedback") / PCT, v.f("mix") / PCT) },
            EffectDef(
                "distortion", R.string.ae_fx_distortion, listOf(
                    ChoiceParam("kind", R.string.ae_p_type, listOf(R.string.ae_opt_soft, R.string.ae_opt_hard, R.string.ae_opt_tube)),
                    db("drive", R.string.ae_p_drive, 0f, 40f, 12f, 0.5f),
                    db("output", R.string.ae_p_output, -24f, 0f, -6f, 0.5f),
                ),
            ) { v, _ ->
                Distortion(v.f("drive"), DistortionKind.values()[v.i("kind").coerceIn(0, 2)], v.f("output"))
            },
            EffectDef(
                "bitcrusher", R.string.ae_fx_bitcrusher, listOf(
                    SliderParam("bits", R.string.ae_p_bits, 1f, 16f, 8f, ParamUnit.BITS, 1f),
                    SliderParam("downsample", R.string.ae_p_downsample, 1f, 32f, 1f, ParamUnit.TIMES, 1f),
                ),
            ) { v, _ -> BitCrusher(v.i("bits"), v.i("downsample")) },
        ),
    )

    private val repair = EffectCategory(
        "repair", R.string.ae_cat_repair, R.drawable.ic_px_eraser, listOf(
            EffectDef(
                "noise_reduction", R.string.ae_fx_noise_reduction, listOf(
                    db("reduction", R.string.ae_p_reduction, 0f, 40f, 12f, 1f),
                    db("sensitivity", R.string.ae_p_sensitivity, 0f, 24f, 6f, 0.5f),
                    SliderParam("smoothing", R.string.ae_p_smoothing, 0f, 8f, 3f, ParamUnit.NONE, 1f),
                ),
                needsProfile = true,
            ) { v, profile ->
                profile?.let { NoiseReduction(it, v.f("reduction"), v.f("sensitivity"), v.i("smoothing")) }
            },
        ),
    )

    val categories: List<EffectCategory> = listOf(volume, channels, filters, dynamics, timePitch, space, repair)

    fun find(id: String): EffectDef? = categories.firstNotNullOfOrNull { c -> c.effects.firstOrNull { it.id == id } }

    // ---- Générateurs ---------------------------------------------------------------------------------

    private fun duration(def: Float) = SliderParam("duration", R.string.ae_p_duration, 0.1f, 3600f, def, ParamUnit.SECONDS, 0.1f, log = true)

    val generators: List<GeneratorDef> = listOf(
        GeneratorDef("silence", R.string.ae_gen_silence, listOf(duration(5f)), insertsSilence = true) { _, _, _ -> null },
        GeneratorDef(
            "tone", R.string.ae_gen_tone, listOf(
                ChoiceParam("shape", R.string.ae_p_type, listOf(R.string.ae_opt_sine, R.string.ae_opt_square, R.string.ae_opt_saw, R.string.ae_opt_triangle)),
                hz("freq", R.string.ae_p_freq, 20f, 20000f, 440f, 1f, log = true),
                db("amp", R.string.ae_p_level, -60f, 0f, -12f, 0.5f),
                duration(5f),
            ),
        ) { v, rate, frames ->
            ToneReader(ToneShape.values()[v.i("shape").coerceIn(0, 3)], v.f("freq").toDouble(), dbToAmp(v.f("amp")), frames, rate)
        },
        GeneratorDef(
            "noise", R.string.ae_gen_noise, listOf(
                ChoiceParam("kind", R.string.ae_p_type, listOf(R.string.ae_opt_white, R.string.ae_opt_pink, R.string.ae_opt_brown), 1),
                db("amp", R.string.ae_p_level, -60f, 0f, -12f, 0.5f),
                duration(5f),
            ),
        ) { v, _, frames ->
            NoiseReader(NoiseKind.values()[v.i("kind").coerceIn(0, 2)], dbToAmp(v.f("amp")), frames)
        },
        GeneratorDef(
            "click", R.string.ae_gen_click, listOf(
                SliderParam("bpm", R.string.ae_p_tempo, 30f, 300f, 120f, ParamUnit.BPM, 1f),
                SliderParam("beats", R.string.ae_p_beats, 1f, 12f, 4f, ParamUnit.NONE, 1f),
                db("amp", R.string.ae_p_level, -60f, 0f, -6f, 0.5f),
                duration(30f),
            ),
        ) { v, rate, frames -> ClickTrackReader(v.f("bpm"), v.i("beats"), dbToAmp(v.f("amp")), frames, rate) },
    )

    fun findGenerator(id: String): GeneratorDef? = generators.firstOrNull { it.id == id }

    private fun dbToAmp(db: Float): Float = 10f.pow(db / 20f)
}
