package com.Atom2Universe.app.notes.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var onReadyCallback: (() -> Unit)? = null
    private var speechRate = 1.0f
    private var voiceName: String? = null

    fun initialize(onReady: () -> Unit) {
        onReadyCallback = onReady
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(speechRate)
            voiceName?.let { setVoiceByName(it) }
            onReadyCallback?.invoke()
        }
    }

    /**
     * Lit [text]. Au-delà de ce que le moteur accepte d'un coup (~4 000 caractères), le texte est lu en
     * morceaux mis à la suite. [onDone] est appelé à la fin du dernier morceau, ou dès qu'un morceau échoue
     * (la lecture s'arrête alors) : l'écran repasse toujours sur « Lire ». [onRange] reçoit des positions
     * dans le morceau en cours.
     */
    fun speak(text: String, onRange: ((start: Int, end: Int) -> Unit)? = null, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (!isReady || engine == null) return
        val parts = split(text, TextToSpeech.getMaxSpeechInputLength())
        if (parts.isEmpty()) { onDone?.invoke(); return }
        val run = "note_tts_${System.currentTimeMillis()}_"
        val lastId = run + (parts.size - 1)
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            onDone?.invoke()
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == lastId) finish() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId?.startsWith(run) != true) return
                engine.stop()
                finish()
            }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                onRange?.invoke(start, end)
            }
        })
        for ((i, part) in parts.withIndex()) {
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engine.speak(part, mode, Bundle(), run + i) == TextToSpeech.ERROR) {
                engine.stop()
                finish()
                return
            }
        }
    }

    fun stop() { tts?.stop() }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun setVoice(voice: Voice) {
        voiceName = voice.name
        tts?.voice = voice
    }

    fun setVoiceByName(name: String) {
        voiceName = name
        tts?.voices?.find { it.name == name }?.let { tts?.voice = it }
    }

    fun getAvailableVoices(): Set<Voice> = tts?.voices ?: emptySet()

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    fun isReady() = isReady

    companion object {
        /**
         * Coupe [text] en morceaux d'au plus [max] caractères : de préférence à la fin d'un paragraphe ou
         * d'une phrase (dans la seconde moitié du morceau), sinon entre deux mots, sinon net.
         */
        fun split(text: String, max: Int): List<String> {
            val out = ArrayList<String>()
            val limit = max.coerceAtLeast(1)
            var start = 0
            while (start < text.length) {
                var end = minOf(start + limit, text.length)
                if (end < text.length) {
                    val half = start + limit / 2
                    val paragraph = text.lastIndexOf('\n', end - 1)
                    val sentence = maxOf(text.lastIndexOf(". ", end - 2), text.lastIndexOf("! ", end - 2), text.lastIndexOf("? ", end - 2))
                    val word = text.lastIndexOf(' ', end - 1)
                    end = when {
                        paragraph > half -> paragraph + 1
                        sentence > half -> sentence + 2
                        word > start -> word + 1
                        else -> end
                    }
                }
                val part = text.substring(start, end)
                if (part.isNotBlank()) out += part
                start = end
            }
            return out
        }
    }
}
