package com.Atom2Universe.app.sf2creator.data

import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.util.Sf2UnitConverter

/**
 * The sample dialogs show times in whole milliseconds, sustain in whole percents and the
 * cutoff in Hz, then convert back on save. Those round trips are not exact (9.9 ms becomes
 * 9 ms, -8000 timecents comes back as -8155), so saving without touching a value would
 * change it. A value that comes back as the round trip of the stored one was not edited:
 * the stored value is kept.
 */
object Sf2EditNoise {

    fun keepUnedited(stored: Sf2SampleEntity, saved: Sf2SampleEntity): Sf2SampleEntity = saved.copy(
        volEnvDelay = time(stored.volEnvDelay, saved.volEnvDelay),
        volEnvAttack = time(stored.volEnvAttack, saved.volEnvAttack),
        volEnvHold = time(stored.volEnvHold, saved.volEnvHold),
        volEnvDecay = time(stored.volEnvDecay, saved.volEnvDecay),
        volEnvSustain = sustain(stored.volEnvSustain, saved.volEnvSustain),
        volEnvRelease = time(stored.volEnvRelease, saved.volEnvRelease),
        modEnvDelay = time(stored.modEnvDelay, saved.modEnvDelay),
        modEnvAttack = time(stored.modEnvAttack, saved.modEnvAttack),
        modEnvHold = time(stored.modEnvHold, saved.modEnvHold),
        modEnvDecay = time(stored.modEnvDecay, saved.modEnvDecay),
        modEnvSustain = sustain(stored.modEnvSustain, saved.modEnvSustain),
        modEnvRelease = time(stored.modEnvRelease, saved.modEnvRelease),
        vibLfoDelay = time(stored.vibLfoDelay, saved.vibLfoDelay),
        modLfoDelay = time(stored.modLfoDelay, saved.modLfoDelay),
        filterFc = cutoff(stored.filterFc, saved.filterFc)
    )

    private fun time(stored: Int, saved: Int): Int =
        if (saved == Sf2UnitConverter.msToTimecents(Sf2UnitConverter.timecentsToMs(stored))) stored else saved

    private fun sustain(stored: Int, saved: Int): Int =
        if (saved == Sf2UnitConverter.sustainPercentToCentibels(Sf2UnitConverter.centibelsToSustainPercent(stored))) stored else saved

    private fun cutoff(stored: Int, saved: Int): Int =
        if (saved == Sf2UnitConverter.hzToFilterCents(Sf2UnitConverter.filterCentsToHz(stored))) stored else saved
}
