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

    fun speak(text: String, onRange: ((start: Int, end: Int) -> Unit)? = null, onDone: (() -> Unit)? = null) {
        if (!isReady) return
        val utteranceId = "note_tts_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = onDone?.invoke() ?: Unit
            override fun onError(utteranceId: String?) {}
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                onRange?.invoke(start, end)
            }
        })
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
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
}
