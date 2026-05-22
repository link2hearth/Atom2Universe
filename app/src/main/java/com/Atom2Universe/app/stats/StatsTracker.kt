package com.Atom2Universe.app.stats

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.stats.data.StatsRepository
import com.Atom2Universe.app.stats.data.UsageSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Singleton pour tracker les sessions d'utilisation des différents modules.
 * Gère le début et la fin des sessions de lecture pour les statistiques.
 */
object StatsTracker {

    private const val TAG = "StatsTracker"
    private const val MIN_SESSION_DURATION_MS = 3000L // 3 secondes minimum

    private var repository: StatsRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Sessions en cours
    private var currentMusicSession: MusicSession? = null
    private var currentMidiSession: MidiSession? = null
    private var currentRadioSession: RadioSession? = null

    /**
     * Initialise le tracker avec le contexte de l'application.
     */
    fun init(context: Context) {
        if (repository == null) {
            repository = StatsRepository(context.applicationContext)
            Log.d(TAG, "StatsTracker initialized")
        }
    }

    // ===== MUSIQUE =====

    /**
     * Démarre une session d'écoute de musique.
     * La session commence en état "pausé" ; resumeMusicSession() doit être appelé
     * quand ExoPlayer confirme que la lecture est réellement en cours.
     */
    fun startMusicSession(
        trackTitle: String?,
        trackArtist: String?,
        trackAlbum: String?,
        trackAlbumArtist: String?
    ) {
        // Terminer la session précédente si elle existe
        endMusicSession()

        // intervalStartMs = null : la session démarre en "pausé", seul le temps
        // réellement joué sera comptabilisé via resumeMusicSession/pauseMusicSession
        currentMusicSession = MusicSession(
            trackTitle = trackTitle,
            trackArtist = trackArtist,
            trackAlbum = trackAlbum,
            trackAlbumArtist = trackAlbumArtist
        )

        Log.d(TAG, "Started music session: $trackTitle by $trackArtist")
    }

    /**
     * Reprend le comptage du temps d'écoute (appelé quand ExoPlayer passe à isPlaying=true).
     */
    fun resumeMusicSession() {
        val session = currentMusicSession ?: return
        if (session.intervalStartMs == null) {
            session.intervalStartMs = System.currentTimeMillis()
            Log.d(TAG, "Music session interval started")
        }
    }

    /**
     * Suspend le comptage du temps d'écoute (appelé quand ExoPlayer passe à isPlaying=false).
     * Le temps de pause n'est pas comptabilisé.
     * Sauvegarde immédiatement le temps accumulé en base pour que les stats soient visibles
     * même si la session reste ouverte (lecture en pause, app en background).
     */
    fun pauseMusicSession() {
        val session = currentMusicSession ?: return
        val intervalStart = session.intervalStartMs ?: return
        val now = System.currentTimeMillis()
        session.accumulatedMs += now - intervalStart
        session.intervalStartMs = null

        // Sauvegarde immédiate si durée suffisante (checkpoint visible dans les stats)
        if (session.accumulatedMs >= MIN_SESSION_DURATION_MS) {
            val usageSession = UsageSessionEntity(
                moduleType = StatsRepository.MODULE_MUSIC,
                startTimestamp = now - session.accumulatedMs,
                endTimestamp = now,
                durationMs = session.accumulatedMs,
                trackTitle = session.trackTitle ?: "",
                trackArtist = session.trackArtist ?: "",
                trackAlbum = session.trackAlbum ?: "",
                trackAlbumArtist = session.trackAlbumArtist ?: ""
            )
            saveSession(usageSession)
            // Réinitialiser après sauvegarde pour éviter le double-comptage si endMusicSession() est appelé ensuite
            session.accumulatedMs = 0
        }

        Log.d(TAG, "Music session paused, accumulated: ${session.accumulatedMs}ms")
    }

    /**
     * Termine la session d'écoute de musique en cours.
     * Seul le temps réellement joué (hors pauses) est persisté.
     */
    fun endMusicSession() {
        val session = currentMusicSession ?: return
        currentMusicSession = null

        val endTimestamp = System.currentTimeMillis()

        // Accumuler le dernier intervalle en cours si la lecture était active
        val intervalStart = session.intervalStartMs
        val durationMs = session.accumulatedMs + if (intervalStart != null) (endTimestamp - intervalStart) else 0

        // Ne sauvegarder que si la durée est >= 3 secondes
        if (durationMs < MIN_SESSION_DURATION_MS) {
            Log.d(TAG, "Music session too short ($durationMs ms), skipping")
            return
        }

        val usageSession = UsageSessionEntity(
            moduleType = StatsRepository.MODULE_MUSIC,
            startTimestamp = endTimestamp - durationMs,
            endTimestamp = endTimestamp,
            durationMs = durationMs,
            trackTitle = session.trackTitle ?: "",
            trackArtist = session.trackArtist ?: "",
            trackAlbum = session.trackAlbum ?: "",
            trackAlbumArtist = session.trackAlbumArtist ?: ""
        )

        saveSession(usageSession)
        Log.d(TAG, "Ended music session: actual play duration ${durationMs / 1000}s")
    }

    // ===== MIDI =====

    /**
     * Démarre une session MIDI (écoute ou practice).
     */
    fun startMidiSession(
        midiFileName: String?,
        isPracticeMode: Boolean = false
    ) {
        // Terminer la session précédente si elle existe
        endMidiSession()

        val now = System.currentTimeMillis()
        currentMidiSession = MidiSession(
            startTimestamp = now,
            midiFileName = midiFileName,
            isPracticeMode = isPracticeMode,
            intervalStartMs = now  // la session démarre en lecture immédiatement
        )

        Log.d(TAG, "Started MIDI session: $midiFileName (practice: $isPracticeMode)")
    }

    /**
     * Termine la session MIDI en cours.
     * Note: Pour le mode practice, le score est ajouté depuis PracticeSessionResult séparément.
     */
    fun endMidiSession(practiceScore: Float? = null) {
        val session = currentMidiSession ?: return
        currentMidiSession = null

        val endTimestamp = System.currentTimeMillis()
        val durationMs = session.accumulatedMs +
            if (session.intervalStartMs != null) endTimestamp - session.intervalStartMs!! else 0L

        // Ne sauvegarder que si la durée est >= 3 secondes
        if (durationMs < MIN_SESSION_DURATION_MS) {
            Log.d(TAG, "MIDI session too short ($durationMs ms), skipping")
            return
        }

        val moduleType = if (session.isPracticeMode)
            StatsRepository.MODULE_MIDI_PRACTICE
        else
            StatsRepository.MODULE_MIDI

        val usageSession = UsageSessionEntity(
            moduleType = moduleType,
            startTimestamp = session.startTimestamp,
            endTimestamp = endTimestamp,
            durationMs = durationMs,
            midiFileName = session.midiFileName ?: "",
            practiceScore = practiceScore
        )

        saveSession(usageSession)
        Log.d(TAG, "Ended MIDI session ($moduleType): duration ${durationMs / 1000}s")
    }

    fun pauseMidiSession() {
        val session = currentMidiSession ?: return
        val intervalStart = session.intervalStartMs ?: return
        session.accumulatedMs += System.currentTimeMillis() - intervalStart
        session.intervalStartMs = null
        Log.d(TAG, "MIDI session paused, accumulated: ${session.accumulatedMs}ms")
    }

    fun resumeMidiSession() {
        val session = currentMidiSession ?: return
        if (session.intervalStartMs == null) {
            session.intervalStartMs = System.currentTimeMillis()
            Log.d(TAG, "MIDI session resumed")
        }
    }

    // ===== RADIO =====

    /**
     * Démarre une session d'écoute de radio.
     */
    fun startRadioSession(stationName: String?) {
        // Terminer la session précédente si elle existe
        endRadioSession()

        currentRadioSession = RadioSession(
            startTimestamp = System.currentTimeMillis(),
            stationName = stationName
        )

        Log.d(TAG, "Started radio session: $stationName")
    }

    /**
     * Termine la session radio en cours.
     */
    fun endRadioSession() {
        val session = currentRadioSession ?: return
        currentRadioSession = null

        val endTimestamp = System.currentTimeMillis()
        val durationMs = endTimestamp - session.startTimestamp

        // Ne sauvegarder que si la durée est >= 3 secondes
        if (durationMs < MIN_SESSION_DURATION_MS) {
            Log.d(TAG, "Radio session too short ($durationMs ms), skipping")
            return
        }

        val usageSession = UsageSessionEntity(
            moduleType = StatsRepository.MODULE_RADIO,
            startTimestamp = session.startTimestamp,
            endTimestamp = endTimestamp,
            durationMs = durationMs,
            radioStationName = session.stationName ?: ""
        )

        saveSession(usageSession)
        Log.d(TAG, "Ended radio session: duration ${durationMs / 1000}s")
    }

    // ===== HELPERS =====

    /**
     * Sauvegarde une session en arrière-plan.
     */
    private fun saveSession(session: UsageSessionEntity) {
        val repo = repository
        if (repo == null) {
            Log.w(TAG, "StatsTracker not initialized, cannot save session")
            return
        }

        scope.launch {
            try {
                repo.insertSession(session)
                repo.updateDailySummaryForDay(session.startTimestamp)
                Log.d(TAG, "Session saved to database: ${session.moduleType}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving session", e)
            }
        }
    }

    /**
     * Termine toutes les sessions en cours (appelé lors de l'arrêt de l'app).
     */
    fun endAllSessions() {
        endMusicSession()
        endMidiSession()
        endRadioSession()
        Log.d(TAG, "All sessions ended")
    }

    // ===== CLASSES DE DONNÉES =====

    private data class MusicSession(
        val trackTitle: String?,
        val trackArtist: String?,
        val trackAlbum: String?,
        val trackAlbumArtist: String?,
        var accumulatedMs: Long = 0,      // temps de lecture effectif accumulé
        var intervalStartMs: Long? = null  // début de l'intervalle courant (null = pausé)
    )

    private data class MidiSession(
        val startTimestamp: Long,
        val midiFileName: String?,
        val isPracticeMode: Boolean,
        var accumulatedMs: Long = 0,      // temps de lecture effectif accumulé (hors pauses)
        var intervalStartMs: Long? = null  // début de l'intervalle courant (null = pausé)
    )

    private data class RadioSession(
        val startTimestamp: Long,
        val stationName: String?
    )
}
