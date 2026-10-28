package com.Atom2Universe.app.notes.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * La dictée continue : le micro se relance seul après chaque phrase reconnue, et après un silence
 * (rien reconnu, ou personne n'a parlé). Toute autre erreur (pas de réseau, service occupé…) l'arrête,
 * comme trois silences de suite : sans cela, hors ligne, il se relançait sans fin.
 */
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
    /** Les erreurs depuis la dernière phrase reconnue. */
    private var errorsInRow = 0

    private val handler = Handler(Looper.getMainLooper())
    private val restart = Runnable { if (shouldRestart && isListening) startSession() }

    fun startContinuousListening(sttListener: ContinuousSttListener) {
        handler.removeCallbacks(restart)
        listener = sttListener
        shouldRestart = true
        isListening = true
        errorsInRow = 0
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
                    errorsInRow = 0
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrBlank()) listener?.onSegmentResult(text)
                    if (shouldRestart && isListening) startSession()
                }

                override fun onError(error: Int) {
                    listener?.onError(error)
                    if (!shouldRestart || !isListening) {
                        listener?.onContinuousStopped()
                        return
                    }
                    errorsInRow++
                    val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (silence && errorsInRow < MAX_ERRORS_IN_ROW) {
                        // Un court répit avant de rouvrir le micro : le service n'est pas toujours prêt tout de suite.
                        handler.postDelayed(restart, RESTART_DELAY_MS)
                    } else {
                        stopAfterErrors()
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

    /** La dictée s'arrête d'elle-même : l'écran l'apprend comme un arrêt demandé. */
    private fun stopAfterErrors() {
        handler.removeCallbacks(restart)
        shouldRestart = false
        isListening = false
        recognizer?.destroy()
        recognizer = null
        listener?.onContinuousStopped()
    }

    fun stopListening() {
        handler.removeCallbacks(restart)
        shouldRestart = false
        isListening = false
        recognizer?.stopListening()
        listener?.onContinuousStopped()
    }

    fun destroy() {
        handler.removeCallbacks(restart)
        shouldRestart = false
        isListening = false
        recognizer?.destroy()
        recognizer = null
        listener = null
    }

    fun isActive() = isListening

    private companion object {
        const val MAX_ERRORS_IN_ROW = 3
        const val RESTART_DELAY_MS = 300L
    }
}
