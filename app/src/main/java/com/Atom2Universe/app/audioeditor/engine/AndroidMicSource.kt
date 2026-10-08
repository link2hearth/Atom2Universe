package com.Atom2Universe.app.audioeditor.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.core.content.ContextCompat

/**
 * Le micro du téléphone : `AudioRecord` mono 16 bits à 44,1 kHz (la seule fréquence que tous les
 * appareils doivent accepter). L'import rééchantillonne ensuite vers la fréquence du projet.
 */
class AndroidMicSource private constructor(private val rec: AudioRecord) : MicSource {

    override val sampleRate = SAMPLE_RATE
    private var shorts = ShortArray(0)

    override fun start() = rec.startRecording()

    override fun read(dst: FloatArray, n: Int): Int {
        if (shorts.size < n) shorts = ShortArray(n)
        val r = rec.read(shorts, 0, n)
        if (r <= 0) return r
        for (i in 0 until r) dst[i] = shorts[i] / 32768f
        return r
    }

    override fun stop() = rec.stop()
    override fun release() = rec.release()

    companion object {
        const val SAMPLE_RATE = 44100

        fun hasPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** À passer à [MicCapture.start] : le fil de capture prend la priorité « audio urgent ». */
        fun raiseThreadPriority() {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO) }
        }

        /** @throws RecordException `NO_PERMISSION` ou `INIT_FAILED` */
        fun open(context: Context): AndroidMicSource {
            if (!hasPermission(context)) throw RecordException(RecordException.Code.NO_PERMISSION)
            val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) throw RecordException(RecordException.Code.INIT_FAILED)
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4,
                )
            } catch (e: SecurityException) {
                throw RecordException(RecordException.Code.NO_PERMISSION, e)
            } catch (e: Exception) {
                throw RecordException(RecordException.Code.INIT_FAILED, e)
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                throw RecordException(RecordException.Code.INIT_FAILED)
            }
            return AndroidMicSource(rec)
        }
    }
}
