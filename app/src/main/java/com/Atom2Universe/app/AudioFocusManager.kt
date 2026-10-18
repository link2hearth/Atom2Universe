package com.Atom2Universe.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gestionnaire centralisé pour l'audio focus Android.
 *
 * Gère les interruptions système (appels, notifications, autres apps audio)
 * et la déconnexion du casque (AUDIO_BECOMING_NOISY).
 *
 * Utilisation:
 * - Appeler requestFocus() avant de démarrer la lecture
 * - Appeler abandonFocus() quand la lecture s'arrête
 * - Implémenter AudioFocusListener pour réagir aux interruptions
 */
object AudioFocusManager {

    private const val TAG = "AudioFocusManager"
    private const val PREFS_NAME = "audio_focus_prefs"
    private const val KEY_AUTO_RESUME = "auto_resume_after_interruption"

    /**
     * Interface pour recevoir les événements d'audio focus.
     */
    interface AudioFocusListener {
        /** Appelé quand il faut mettre en pause (perte temporaire ou permanente) */
        fun onAudioFocusPause()

        /** Appelé quand on peut reprendre la lecture (après perte temporaire) */
        fun onAudioFocusResume()

        /** Appelé quand il faut baisser le volume (notification courte) */
        fun onAudioFocusDuck()

        /** Appelé quand on peut remettre le volume normal */
        fun onAudioFocusUnduck()
    }

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentListener: AudioFocusListener? = null
    private var hasAudioFocus = false
    private var wasPlayingBeforeFocusLoss = false
    private var isDucked = false
    private val isRegistered = AtomicBoolean(false)
    private var appContext: Context? = null

    /**
     * Receiver pour détecter la déconnexion du casque.
     */
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "Audio becoming noisy (headphones unplugged)")
                currentListener?.onAudioFocusPause()
            }
        }
    }

    /**
     * Callback pour les changements d'audio focus.
     */
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasAudioFocus = true
                if (isDucked) {
                    isDucked = false
                    currentListener?.onAudioFocusUnduck()
                }
                if (wasPlayingBeforeFocusLoss && isAutoResumeEnabled()) {
                    wasPlayingBeforeFocusLoss = false
                    currentListener?.onAudioFocusResume()
                } else {
                    wasPlayingBeforeFocusLoss = false
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Perte permanente - une autre app a pris le focus
                Log.d(TAG, "Audio focus lost permanently")
                hasAudioFocus = false
                wasPlayingBeforeFocusLoss = false
                currentListener?.onAudioFocusPause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Perte temporaire - appel téléphonique, GPS, etc.
                Log.d(TAG, "Audio focus lost transiently")
                hasAudioFocus = false
                wasPlayingBeforeFocusLoss = true
                currentListener?.onAudioFocusPause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Perte temporaire avec duck autorisé - notification courte
                Log.d(TAG, "Audio focus lost transiently (can duck)")
                isDucked = true
                currentListener?.onAudioFocusDuck()
            }
        }
    }

    /**
     * Initialise le manager avec le contexte de l'application.
     * Doit être appelé une fois au démarrage (ex: dans Application.onCreate()).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * Demande l'audio focus pour la lecture de musique.
     *
     * @param listener Le listener qui recevra les événements de focus
     * @return true si le focus a été obtenu, false sinon
     */
    fun requestFocus(listener: AudioFocusListener): Boolean {
        val manager = audioManager ?: run {
            Log.e(TAG, "AudioManager not initialized. Call init() first.")
            return false
        }

        currentListener = listener

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        val result = manager.requestAudioFocus(audioFocusRequest!!)

        hasAudioFocus = when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                Log.d(TAG, "Audio focus request granted")
                registerBecomingNoisyReceiver()
                true
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                Log.d(TAG, "Audio focus request delayed")
                registerBecomingNoisyReceiver()
                false
            }
            else -> {
                Log.d(TAG, "Audio focus request failed")
                false
            }
        }

        return hasAudioFocus
    }

    /**
     * Abandonne l'audio focus.
     * Doit être appelé quand la lecture s'arrête.
     */
    fun abandonFocus() {
        val manager = audioManager ?: return
        val request = audioFocusRequest ?: return

        manager.abandonAudioFocusRequest(request)
        hasAudioFocus = false
        wasPlayingBeforeFocusLoss = false
        isDucked = false
        currentListener = null
        audioFocusRequest = null

        unregisterBecomingNoisyReceiver()
        Log.d(TAG, "Audio focus abandoned")
    }

    /**
     * Enregistre le receiver pour AUDIO_BECOMING_NOISY.
     */
    private fun registerBecomingNoisyReceiver() {
        if (!isRegistered.compareAndSet(false, true)) return

        val context = appContext ?: run {
            isRegistered.set(false)
            return
        }
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(
            context,
            becomingNoisyReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "Becoming noisy receiver registered")
    }

    /**
     * Désenregistre le receiver.
     */
    private fun unregisterBecomingNoisyReceiver() {
        if (!isRegistered.compareAndSet(true, false)) return

        val context = appContext ?: return
        try {
            context.unregisterReceiver(becomingNoisyReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver pas enregistré, on ignore
        }
        Log.d(TAG, "Becoming noisy receiver unregistered")
    }

    /**
     * Vérifie si la reprise automatique après interruption est activée.
     * Par défaut : désactivée (false)
     */
    fun isAutoResumeEnabled(): Boolean {
        val context = appContext ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_RESUME, false)
    }

    /**
     * Active ou désactive la reprise automatique après interruption (appel, etc.)
     */
    fun setAutoResumeEnabled(enabled: Boolean) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_AUTO_RESUME, enabled) }
        Log.d(TAG, "Auto resume after interruption: $enabled")
    }
}
