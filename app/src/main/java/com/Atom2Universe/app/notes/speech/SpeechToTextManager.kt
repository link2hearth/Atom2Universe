package com.Atom2Universe.app.notes.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechToTextManager(private val context: Context) {

    interface ContinuousSttListener {
        fun onSegmentResult(text: String)
        fun onContinuousStopped()
        fun onError(errorCode: Int) {}
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: ContinuousSttListener? = null
    private var isListening = false
    private var shouldRestart = false

    fun startContinuousListening(sttListener: ContinuousSttListener) {
        listener = sttListener
        shouldRestart = true
        isListening = true
        startSession()
    }

    private fun startSession() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrBlank()) listener?.onSegmentResult(text)
                    if (shouldRestart && isListening) startSession()
                }

                override fun onError(error: Int) {
                    listener?.onError(error)
                    if (shouldRestart && isListening &&
                        error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    ) {
                        startSession()
                    } else if (!shouldRestart) {
                        listener?.onContinuousStopped()
                    }
                }

                override fun onEndOfSpeech() {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            sr.startListening(intent)
        }
    }

    fun stopListening() {
        shouldRestart = false
        isListening = false
        recognizer?.stopListening()
        listener?.onContinuousStopped()
    }

    fun destroy() {
        shouldRestart = false
        isListening = false
        recognizer?.destroy()
        recognizer = null
        listener = null
    }

    fun isActive() = isListening
}
