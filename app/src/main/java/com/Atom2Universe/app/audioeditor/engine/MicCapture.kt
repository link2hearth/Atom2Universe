package com.Atom2Universe.app.audioeditor.engine

import com.Atom2Universe.app.audioeditor.io.WavWriter
import java.io.File
import java.io.IOException

/** Pourquoi un enregistrement n'a pas pu avoir lieu : l'écran traduit le code, le moteur ne parle pas à l'utilisateur. */
class RecordException(val code: Code, cause: Throwable? = null) : IOException(code.name, cause) {
    enum class Code { NO_PERMISSION, INIT_FAILED, READ_FAILED, WRITE_FAILED, NO_DATA }
}

/** Une entrée micro mono : `AudioRecord` en vrai ([AndroidMicSource]), un faux dans les tests. */
interface MicSource {
    val sampleRate: Int

    fun start()

    /** Remplit [dst] avec au plus [n] échantillons dans ±1 et bloque jusqu'à en avoir ; rend leur nombre, ou un nombre négatif en cas d'erreur. */
    fun read(dst: FloatArray, n: Int): Int

    fun stop()
    fun release()
}

/**
 * Enregistre un [MicSource] dans un WAV 16 bits mono, **au fil de l'eau** : rien n'est gardé en mémoire
 * (un enregistrement d'une heure ne coûte pas plus qu'une minute) et le fichier reste lisible si
 * l'appli est tuée en route, puisque [WavWriter] déduit alors la taille de la longueur du fichier.
 *
 * Les durées viennent du nombre d'échantillons reçus, jamais de l'horloge. [level] est la crête du
 * dernier bloc (0 à 1) pour le vumètre. Un échec (disque plein…) arrête l'enregistrement et se lit
 * dans [error] ; ce qui était déjà écrit reste récupérable avec [stop].
 */
class MicCapture(private val source: MicSource, val file: File) {

    @Volatile var level = 0f
        private set
    @Volatile var frames = 0L
        private set
    @Volatile var error: RecordException? = null
        private set
    @Volatile var onError: ((RecordException) -> Unit)? = null

    val seconds: Double get() = frames.toDouble() / source.sampleRate
    val sampleRate: Int get() = source.sampleRate

    @Volatile private var running = false
    private var thread: Thread? = null

    /** Lance l'enregistrement sur un fil à part. */
    fun start(onThreadStart: () -> Unit = {}) {
        check(thread == null) { "déjà démarré" }
        file.parentFile?.mkdirs()
        try {
            source.start()
        } catch (e: Exception) {
            source.release()
            throw RecordException(RecordException.Code.INIT_FAILED, e)
        }
        running = true
        thread = Thread({ onThreadStart(); loop() }, "AudioEditor-capture").also { it.start() }
    }

    private fun fail(code: RecordException.Code, cause: Throwable? = null) {
        val e = RecordException(code, cause)
        error = e
        running = false
        onError?.invoke(e)
    }

    private fun loop() {
        val buf = FloatArray(BLOCK)
        try {
            WavWriter(file, 1, source.sampleRate, float = false).use { w ->
                val arrays = arrayOf(buf)
                while (running) {
                    val n = source.read(buf, BLOCK)
                    if (n < 0) { fail(RecordException.Code.READ_FAILED); break }
                    if (n == 0) continue
                    var peak = 0f
                    for (i in 0 until n) { val a = if (buf[i] < 0f) -buf[i] else buf[i]; if (a > peak) peak = a }
                    level = peak
                    w.write(arrays, n)
                    frames += n
                }
            }
        } catch (e: IOException) {
            fail(RecordException.Code.WRITE_FAILED, e)
        } catch (e: Throwable) {
            fail(RecordException.Code.READ_FAILED, e)
        } finally {
            level = 0f
        }
    }

    private fun halt() {
        running = false
        val t = thread
        if (t != null) {
            t.join(3000)
            if (t.isAlive) t.interrupt()
        }
        thread = null
        runCatching { source.stop() }
        source.release()
    }

    /**
     * Arrête et rend le fichier enregistré.
     * @throws RecordException (`NO_DATA`) si rien n'a été reçu ; le fichier est alors effacé
     */
    fun stop(): File {
        halt()
        if (frames <= 0L) {
            file.delete()
            throw error ?: RecordException(RecordException.Code.NO_DATA)
        }
        return file
    }

    /** Arrête et efface le fichier. */
    fun cancel() {
        halt()
        file.delete()
    }

    private companion object {
        const val BLOCK = 4096
    }
}
