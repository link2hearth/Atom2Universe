package com.Atom2Universe.app.audioeditor.engine

import android.content.Context
import com.Atom2Universe.app.audioeditor.MicRecordingService
import java.io.File
import java.util.Collections

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
        busy += file
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
            // Le fichier rendu reste réservé jusqu'à son rangement dans le projet ([release]).
            return c.stop()
        } catch (e: RecordException) {
            busy -= c.file
            throw e
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
        busy -= c.file
        MicRecordingService.stop(context)
    }

    /**
     * Les enregistrements restés dans le cache (appli tuée en route, rangement interrompu), du plus ancien au plus
     * récent : l'écran propose de les récupérer plutôt que de les effacer. Les fichiers sans son sont effacés ; ceux
     * qu'un enregistrement ou un rangement utilise encore sont laissés de côté.
     */
    fun orphanRecordings(): List<File> {
        val out = ArrayList<File>()
        File(context.cacheDir, "audio_recordings").listFiles()?.forEach { f ->
            if (!f.isFile || f in busy) return@forEach
            if (f.length() <= MIN_ORPHAN_BYTES) f.delete() else out += f
        }
        return out.sortedBy { it.lastModified() }
    }

    companion object {
        /** En-tête WAV plus une fraction de seconde : en dessous, il n'y a rien à récupérer. */
        private const val MIN_ORPHAN_BYTES = 4096L

        /** Les WAV en cours d'enregistrement ou de rangement, pour toute l'appli (un écran fermé peut ranger le sien). */
        private val busy: MutableSet<File> = Collections.synchronizedSet(HashSet())

        /** Le WAV rendu par [stop] est rangé (ou abandonné) : il redevient un fichier ordinaire du cache. */
        fun release(file: File) {
            busy -= file
        }
    }
}
