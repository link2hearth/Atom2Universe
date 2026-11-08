package com.Atom2Universe.app.studio.engine

/**
 * Les réglages de mixage d'une piste : volume, panoramique, envois vers la réverbération et l'écho, et trois effets
 * de piste (distorsion, dégradation « lo-fi », filtre). Les effets à 0 sont coupés : ils ne coûtent rien.
 *
 * [filter] va de −1 (coupe les aigus, son étouffé) à +1 (coupe les graves, son mince) ; 0 = pas de filtre.
 */
data class TrackMix(
    val level: Float,
    val pan: Float,
    val reverbSend: Float,
    val delaySend: Float,
    val drive: Float = 0f,
    val crush: Float = 0f,
    val filter: Float = 0f,
) {
    companion object {
        /**
         * Les réglages d'origine de chaque instrument, dans l'ordre de [Inst]. Niveaux calés sur de vraies boîtes à
         * rythmes : charleston fermé ~20 dB sous la grosse caisse ; tom et cloche mesurés sur une TR-808.
         */
        fun defaults(): List<TrackMix> = listOf(
            TrackMix(level = 1.0f, pan = 0f, reverbSend = 0f, delaySend = 0f),          // grosse caisse
            TrackMix(level = 0.7f, pan = 0f, reverbSend = 0.30f, delaySend = 0.05f),    // caisse claire
            TrackMix(level = 0.6f, pan = 0.1f, reverbSend = 0.45f, delaySend = 0.10f),  // clap
            TrackMix(level = 1.1f, pan = -0.2f, reverbSend = 0.10f, delaySend = 0.05f), // charleston fermé
            TrackMix(level = 0.75f, pan = 0.2f, reverbSend = 0.20f, delaySend = 0.05f), // charleston ouvert
            TrackMix(level = 0.8f, pan = 0.3f, reverbSend = 0.25f, delaySend = 0.10f),  // tom
            TrackMix(level = 0.6f, pan = -0.3f, reverbSend = 0.15f, delaySend = 0.35f), // cloche
            TrackMix(level = 0.5f, pan = 0f, reverbSend = 0.25f, delaySend = 0.18f),    // synthé
        )
    }
}
