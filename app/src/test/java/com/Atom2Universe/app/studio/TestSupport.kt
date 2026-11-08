package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.dsp.Instrument
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackMix

/** Tous les instruments d'un moteur, dans l'ordre des numéros de piste. */
val StudioEngine.allInstruments: List<Instrument>
    get() = (0 until StudioEngine.MAX_TRACKS).mapNotNull { instrument(it) }

/** Applique une liste de mixages aux huit pistes de départ (la i-ième réglage va à la piste i). */
fun StudioEngine.applyMix(mix: List<TrackMix>) {
    for (i in mix.indices) applyMix(i, mix[i])
}
