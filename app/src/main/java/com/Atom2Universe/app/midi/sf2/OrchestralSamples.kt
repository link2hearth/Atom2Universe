package com.Atom2Universe.app.midi.sf2

import com.Atom2Universe.app.A2UApplication
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Les instruments d'Orchestral joués avec de vrais échantillons, tous de sources CC0 (piano,
 * violon et flûte de VSCO 2 Community Edition) : un petit SF2 fabriqué par tools/orchestral/build_bank.py, partagé par le lecteur MIDI et Studio.
 */
internal object OrchestralSamples {
    private const val ASSET = "studio/orchestral/bank.sf2"
    @Volatile private var loaded: Sf2File? = null

    /**
     * La banque, lue au premier appel (jamais depuis le fil audio). Ses échantillons commencent
     * après la place des percussions d'OrchestralSoundBank, qui partage ce même tableau.
     */
    val file: Sf2File
        get() = loaded ?: synchronized(this) {
            loaded ?: load(Sf2Parser(OrchestralSoundBank.DRUM_RESERVE)).also { loaded = it }
        }

    /**
     * Rangée sans compression dans l'APK (build.gradle.kts) : projetée telle quelle, puis copiée
     * d'un bloc dans le tableau des échantillons. Sans appli (tests JVM), lue dans les sources.
     */
    private fun load(parser: Sf2Parser): Sf2File {
        val assets = runCatching { A2UApplication.appContext.assets }.getOrNull()
            ?: return parser.parse(File("src/main/assets/$ASSET"))
        return try {
            assets.openFd(ASSET).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    parser.parse(channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length))
                }
            }
        } catch (_: java.io.FileNotFoundException) {
            // Asset compressé (openFd impossible) : lu en flux.
            parser.parse(ByteBuffer.wrap(assets.open(ASSET).use { it.readBytes() }))
        }
    }

    /** La banque si elle est déjà en mémoire. */
    fun ready(): Sf2File? = loaded

    /** Vrai si ce programme GM est joué par des échantillons plutôt que recalculé. */
    fun has(program: Int) = file.getPreset(0, program) != null

    /** Les programmes GM joués par des échantillons, dans l'ordre. */
    val programs: List<Int> by lazy { (0..127).filter(::has) }
}
