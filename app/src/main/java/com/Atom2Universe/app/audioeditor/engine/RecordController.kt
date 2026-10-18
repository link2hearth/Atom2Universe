package com.Atom2Universe.app.audioeditor.engine

import android.content.Context
import com.Atom2Universe.app.audioeditor.MicRecordingService
import java.io.File

/**
 * Pilote un enregistrement : service de premier plan (obligatoire depuis Android 14 pour garder le micro),
 * capture dans un WAV du cache, et, en **overdub**, lecture du projet en même temps.
 *
 * Il ne touche pas au projet : [stop] rend le WAV et la trame de départ, l'écran les passe à
 * [RecordingPlacement.place]. Casque conseillé en overdub (sans lui le micro réentend les haut-parleurs).
 */
class RecordController(private val context: Context, private val engine: PlaybackEngine) {

    private var capture: MicCapture? = null

    /** Trame du projet où l'enregistrement a commencé. */
    var startFrame = 0L
        private set

    val isRecording get() = capture != null

    /** Crête du dernier bloc (0 à 1), 0 à l'arrêt. */
    val level get() = capture?.level ?: 0f

    /** Durée enregistrée, d'après les échantillons reçus. */
    val seconds get() = capture?.seconds ?: 0.0

    /** Le premier échec survenu en cours de route (disque plein…), pour que l'écran arrête et prévienne. */
    val error get() = capture?.error

    /** Appelé sur le fil de capture si l'enregistrement s'interrompt (disque plein, micro perdu) ; à poser avant [start]. */
    var onError: ((RecordException) -> Unit)? = null

    /**
     * @param from trame de départ (le curseur)
     * @param overdub joue le projet depuis [from] pendant l'enregistrement
     * @throws RecordException `NO_PERMISSION` ou `INIT_FAILED`
     */
    fun start(from: Long, overdub: Boolean) {
        if (capture != null) return
        val mic = AndroidMicSource.open(context)
        val file = File(File(context.cacheDir, "audio_recordings").also { it.mkdirs() }, "rec_${System.currentTimeMillis()}.wav")
        val c = MicCapture(mic, file)
        c.onError = onError
        // Le service d'abord : sur Android 14+, le micro doit appartenir à un service de premier plan déjà démarré.
        MicRecordingService.start(context)
        try {
            c.start { AndroidMicSource.raiseThreadPriority() }
        } catch (e: RecordException) {
            MicRecordingService.stop(context)
            throw e
        }
        capture = c
        startFrame = from.coerceAtLeast(0)
        if (overdub) engine.play(startFrame)
    }

    /**
     * Arrête (et la lecture avec) et rend le WAV enregistré.
     * @throws RecordException `NO_DATA` si rien n'a été reçu
     */
    fun stop(): File {
        val c = capture ?: throw RecordException(RecordException.Code.NO_DATA)
        capture = null
        engine.stop()
        try {
            return c.stop()
        } finally {
            MicRecordingService.stop(context)
        }
    }

    /** Abandonne l'enregistrement et efface le fichier. */
    fun cancel() {
        val c = capture ?: return
        capture = null
        engine.stop()
        c.cancel()
        MicRecordingService.stop(context)
    }

    /** Anciens enregistrements du cache (plantage en route, import jamais fait) : à effacer au lancement. */
    fun cleanupOldRecordings(maxAgeMs: Long = 24L * 60 * 60 * 1000) {
        val now = System.currentTimeMillis()
        File(context.cacheDir, "audio_recordings").listFiles()?.forEach { if (now - it.lastModified() > maxAgeMs) it.delete() }
    }
}
