package com.Atom2Universe.app.midi.sf2

/**
 * Les instruments d'Orchestral joués avec de vrais échantillons, tous de sources CC0 (piano,
 * violon et flûte de VSCO 2 Community Edition) : un petit SF2 fabriqué par tools/orchestral/build_bank.py, partagé par le lecteur MIDI et Studio.
 */
internal object OrchestralSamples {
    @Volatile private var loaded: Sf2File? = null

    /** La banque, lue au premier appel (quelques dizaines de millisecondes : jamais depuis le fil audio). */
    val file: Sf2File
        get() = loaded ?: synchronized(this) {
            loaded ?: checkNotNull(javaClass.getResourceAsStream("/studio/orchestral/bank.sf2"))
                .use { Sf2Parser().parse(it.readBytes()) }.also { loaded = it }
        }

    /** La banque si elle est déjà en mémoire. */
    fun ready(): Sf2File? = loaded

    /** Vrai si ce programme GM est joué par des échantillons plutôt que recalculé. */
    fun has(program: Int) = file.getPreset(0, program) != null
}
