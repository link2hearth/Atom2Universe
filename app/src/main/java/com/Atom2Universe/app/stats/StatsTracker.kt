package com.Atom2Universe.app.stats

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.Atom2Universe.app.stats.data.StatsRepository
import com.Atom2Universe.app.stats.data.UsageSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Singleton pour tracker les sessions d'utilisation des différents modules.
 * Gère le début et la fin des sessions de lecture pour les statistiques.
 *
 * Les séances ouvertes sont notées dans les préférences toutes les minutes : si le processus
 * meurt sans les fermer, le prochain [init] les enregistre jusqu'à leur dernière note.
 * Appelé depuis le fil principal et depuis le service MIDI : tout passe sous le verrou de l'objet.
 */
object StatsTracker {

    private const val TAG = "StatsTracker"
    private const val MIN_SESSION_DURATION_MS = 3000L // 3 secondes minimum

    private const val CHECKPOINT_INTERVAL_MS = 60_000L
    private const val PREFS_OPEN_SESSIONS = "stats_open_sessions"
    private const val KEY_MUSIC = "music"
    private const val KEY_MIDI = "midi"
    private const val KEY_RADIO = "radio"
    private const val KEY_READING = "reading"

    private var repository: StatsRepository? = null
    private var openSessionsPrefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val handler = Handler(Looper.getMainLooper())
    private var checkpointScheduled = false
    private val checkpointRunnable = Runnable { onCheckpointTick() }

    // Sessions en cours
    private var currentMusicSession: MusicSession? = null
    private var currentMidiSession: MidiSession? = null
    private var currentRadioSession: RadioSession? = null
    private var currentReadingSession: ReadingSession? = null

    /**
     * Initialise le tracker avec le contexte de l'application.
     */
    @Synchronized
    fun init(context: Context) {
        if (repository == null) {
            val app = context.applicationContext
            repository = StatsRepository(app)
            openSessionsPrefs = app.getSharedPreferences(PREFS_OPEN_SESSIONS, Context.MODE_PRIVATE)
            recoverOpenSessions()
            Log.d(TAG, "StatsTracker initialized")
        }
    }

    // ===== MUSIQUE =====

    /**
     * Démarre une session d'écoute de musique.
     * La session commence en état "pausé" ; resumeMusicSession() doit être appelé
     * quand ExoPlayer confirme que la lecture est réellement en cours.
     */
    @Synchronized
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
    @Synchronized
    fun resumeMusicSession() {
        val session = currentMusicSession ?: return
        if (session.intervalStartMs == null) {
            session.intervalStartMs = System.currentTimeMillis()
            scheduleCheckpoint()
            Log.d(TAG, "Music session interval started")
        }
    }

    /**
     * Suspend le comptage du temps d'écoute (appelé quand ExoPlayer passe à isPlaying=false).
     * Le temps de pause n'est pas comptabilisé.
     * Sauvegarde immédiatement le temps accumulé en base pour que les stats soient visibles
     * même si la session reste ouverte (lecture en pause, app en background).
     */
    @Synchronized
    fun pauseMusicSession() {
        val session = currentMusicSession ?: return
        val intervalStart = session.intervalStartMs ?: return
        val now = System.currentTimeMillis()
        session.accumulatedMs += now - intervalStart
        session.intervalStartMs = null

        // Sauvegarde immédiate si durée suffisante (checkpoint visible dans les stats)
        session.toEntity(now)?.let { usageSession ->
            saveSession(usageSession)
            // Réinitialiser après sauvegarde pour éviter le double-comptage si endMusicSession() est appelé ensuite
            session.accumulatedMs = 0
        }
        writeCheckpoint(KEY_MUSIC, session.toEntity(now))

        Log.d(TAG, "Music session paused, accumulated: ${session.accumulatedMs}ms")
    }

    /**
     * Termine la session d'écoute de musique en cours.
     * Seul le temps réellement joué (hors pauses) est persisté.
     */
    @Synchronized
    fun endMusicSession() {
        val session = currentMusicSession ?: return
        currentMusicSession = null
        writeCheckpoint(KEY_MUSIC, null)

        val usageSession = session.toEntity(System.currentTimeMillis())
        if (usageSession == null) {
            Log.d(TAG, "Music session too short, skipping")
            return
        }

        saveSession(usageSession)
        Log.d(TAG, "Ended music session: actual play duration ${usageSession.durationMs / 1000}s")
    }

    // ===== MIDI =====

    /**
     * Démarre une session MIDI (écoute ou practice).
     */
    @Synchronized
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
        scheduleCheckpoint()

        Log.d(TAG, "Started MIDI session: $midiFileName (practice: $isPracticeMode)")
    }

    /**
     * Termine la session MIDI en cours.
     * Note: Pour le mode practice, le score est ajouté depuis PracticeSessionResult séparément.
     */
    @Synchronized
    fun endMidiSession(practiceScore: Float? = null) {
        val session = currentMidiSession ?: return
        currentMidiSession = null
        writeCheckpoint(KEY_MIDI, null)

        val usageSession = session.toEntity(System.currentTimeMillis())?.copy(practiceScore = practiceScore)
        if (usageSession == null) {
            Log.d(TAG, "MIDI session too short, skipping")
            return
        }

        saveSession(usageSession)
        Log.d(TAG, "Ended MIDI session (${usageSession.moduleType}): duration ${usageSession.durationMs / 1000}s")
    }

    @Synchronized
    fun pauseMidiSession() {
        val session = currentMidiSession ?: return
        val intervalStart = session.intervalStartMs ?: return
        val now = System.currentTimeMillis()
        session.accumulatedMs += now - intervalStart
        session.intervalStartMs = null
        writeCheckpoint(KEY_MIDI, session.toEntity(now))
        Log.d(TAG, "MIDI session paused, accumulated: ${session.accumulatedMs}ms")
    }

    @Synchronized
    fun resumeMidiSession() {
        val session = currentMidiSession ?: return
        if (session.intervalStartMs == null) {
            session.intervalStartMs = System.currentTimeMillis()
            scheduleCheckpoint()
            Log.d(TAG, "MIDI session resumed")
        }
    }

    // ===== RADIO =====

    /**
     * Démarre une session d'écoute de radio.
     * Comme la musique, elle démarre en "pausé" : resumeRadioSession() est appelé quand
     * le lecteur joue vraiment, pauseRadioSession() quand il s'arrête (pause, tampon, coupure).
     */
    @Synchronized
    fun startRadioSession(stationName: String?) {
        // Terminer la session précédente si elle existe
        endRadioSession()

        currentRadioSession = RadioSession(
            startTimestamp = System.currentTimeMillis(),
            stationName = stationName
        )

        Log.d(TAG, "Started radio session: $stationName")
    }

    @Synchronized
    fun resumeRadioSession() {
        val session = currentRadioSession ?: return
        if (session.intervalStartMs == null) {
            session.intervalStartMs = System.currentTimeMillis()
            scheduleCheckpoint()
            Log.d(TAG, "Radio session resumed")
        }
    }

    @Synchronized
    fun pauseRadioSession() {
        val session = currentRadioSession ?: return
        val intervalStart = session.intervalStartMs ?: return
        val now = System.currentTimeMillis()
        session.accumulatedMs += now - intervalStart
        session.intervalStartMs = null
        writeCheckpoint(KEY_RADIO, session.toEntity(now))
        Log.d(TAG, "Radio session paused, accumulated: ${session.accumulatedMs}ms")
    }

    /**
     * Termine la session radio en cours.
     */
    @Synchronized
    fun endRadioSession() {
        val session = currentRadioSession ?: return
        currentRadioSession = null
        writeCheckpoint(KEY_RADIO, null)

        val usageSession = session.toEntity(System.currentTimeMillis())
        if (usageSession == null) {
            Log.d(TAG, "Radio session too short, skipping")
            return
        }

        saveSession(usageSession)
        Log.d(TAG, "Ended radio session: duration ${usageSession.durationMs / 1000}s")
    }

    // ===== LECTURE (livres / BD) =====

    /**
     * Démarre une session de lecture (livre ou BD). Si une session de lecture est déjà
     * en cours (même titre ou non), elle est terminée et sauvegardée avant de démarrer la nouvelle.
     *
     * @param moduleType StatsRepository.MODULE_BOOK ou MODULE_COMIC
     * @param owner l'écran qui lit : seul lui (ou un appel sans owner) peut fermer la séance
     */
    @Synchronized
    fun startReadingSession(moduleType: String, title: String?, owner: Any? = null) {
        endReadingSession()

        currentReadingSession = ReadingSession(
            startTimestamp = System.currentTimeMillis(),
            moduleType = moduleType,
            title = title,
            ownerId = owner?.let { System.identityHashCode(it) }
        )
        scheduleCheckpoint()

        Log.d(TAG, "Started reading session ($moduleType): $title")
    }

    /**
     * Termine la session de lecture en cours.
     * Avec [owner], ne ferme que la séance ouverte par cet écran : un lecteur qui en ouvre un
     * autre (PDF des livres) ne coupe pas la séance de son successeur en s'arrêtant après lui.
     */
    @Synchronized
    fun endReadingSession(owner: Any? = null) {
        val session = currentReadingSession ?: return
        if (owner != null && session.ownerId != System.identityHashCode(owner)) return
        currentReadingSession = null
        writeCheckpoint(KEY_READING, null)

        val usageSession = session.toEntity(System.currentTimeMillis())
        if (usageSession == null) {
            Log.d(TAG, "Reading session too short, skipping")
            return
        }

        saveSession(usageSession)
        Log.d(TAG, "Ended reading session (${session.moduleType}): duration ${usageSession.durationMs / 1000}s")
    }

    // ===== HELPERS =====

    /**
     * Sauvegarde une session en arrière-plan (la session et le résumé de son jour, ensemble).
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
                Log.d(TAG, "Session saved to database: ${session.moduleType}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving session", e)
            }
        }
    }

    /**
     * Termine toutes les sessions en cours (appelé lors de l'arrêt de l'app).
     */
    @Synchronized
    fun endAllSessions() {
        endMusicSession()
        endMidiSession()
        endRadioSession()
        endReadingSession()
        Log.d(TAG, "All sessions ended")
    }

    // ===== NOTES DES SÉANCES OUVERTES =====

    private fun hasRunningSession(): Boolean =
        currentMusicSession?.intervalStartMs != null ||
            currentMidiSession?.intervalStartMs != null ||
            currentRadioSession?.intervalStartMs != null ||
            currentReadingSession != null

    /** Une note par minute tant qu'une séance compte du temps ; rien pendant les pauses. */
    private fun scheduleCheckpoint() {
        if (checkpointScheduled) return
        checkpointScheduled = true
        handler.postDelayed(checkpointRunnable, CHECKPOINT_INTERVAL_MS)
    }

    @Synchronized
    private fun onCheckpointTick() {
        checkpointScheduled = false
        val prefs = openSessionsPrefs ?: return
        val now = System.currentTimeMillis()
        prefs.edit {
            putOrRemove(KEY_MUSIC, currentMusicSession?.toEntity(now))
            putOrRemove(KEY_MIDI, currentMidiSession?.toEntity(now))
            putOrRemove(KEY_RADIO, currentRadioSession?.toEntity(now))
            putOrRemove(KEY_READING, currentReadingSession?.toEntity(now))
        }
        if (hasRunningSession()) scheduleCheckpoint()
    }

    private fun writeCheckpoint(key: String, entity: UsageSessionEntity?) {
        val prefs = openSessionsPrefs ?: return
        if (entity == null && !prefs.contains(key)) return
        prefs.edit { putOrRemove(key, entity) }
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, entity: UsageSessionEntity?) {
        if (entity == null) remove(key) else putString(key, entity.toCheckpointJson())
    }

    /** Les séances restées ouvertes à la mort du processus précédent : enregistrées jusqu'à leur dernière note. */
    private fun recoverOpenSessions() {
        val prefs = openSessionsPrefs ?: return
        val saved = prefs.all.values.mapNotNull { raw ->
            (raw as? String)?.let { runCatching { checkpointFromJson(it) }.getOrNull() }
        }
        prefs.edit { clear() }
        saved.forEach { saveSession(it) }
        if (saved.isNotEmpty()) Log.d(TAG, "Recovered ${saved.size} open session(s)")
    }

    private fun UsageSessionEntity.toCheckpointJson(): String = JSONObject().apply {
        put("m", moduleType)
        put("s", startTimestamp)
        put("e", endTimestamp)
        put("d", durationMs)
        trackTitle?.let { put("tt", it) }
        trackArtist?.let { put("ta", it) }
        trackAlbum?.let { put("al", it) }
        trackAlbumArtist?.let { put("aa", it) }
        midiFileName?.let { put("mf", it) }
        radioStationName?.let { put("rs", it) }
        readingTitle?.let { put("rt", it) }
    }.toString()

    private fun checkpointFromJson(raw: String): UsageSessionEntity {
        val o = JSONObject(raw)
        fun opt(key: String): String? = if (o.has(key)) o.getString(key) else null
        return UsageSessionEntity(
            moduleType = o.getString("m"),
            startTimestamp = o.getLong("s"),
            endTimestamp = o.getLong("e"),
            durationMs = o.getLong("d"),
            trackTitle = opt("tt"),
            trackArtist = opt("ta"),
            trackAlbum = opt("al"),
            trackAlbumArtist = opt("aa"),
            midiFileName = opt("mf"),
            radioStationName = opt("rs"),
            readingTitle = opt("rt")
        )
    }

    // ===== CLASSES DE DONNÉES =====

    private data class MusicSession(
        val trackTitle: String?,
        val trackArtist: String?,
        val trackAlbum: String?,
        val trackAlbumArtist: String?,
        var accumulatedMs: Long = 0,      // temps de lecture effectif accumulé
        var intervalStartMs: Long? = null  // début de l'intervalle courant (null = pausé)
    ) {
        /** La séance arrêtée à [now], ou null si elle est trop courte pour compter. */
        fun toEntity(now: Long): UsageSessionEntity? {
            val durationMs = accumulatedMs + (intervalStartMs?.let { now - it } ?: 0L)
            if (durationMs < MIN_SESSION_DURATION_MS) return null
            return UsageSessionEntity(
                moduleType = StatsRepository.MODULE_MUSIC,
                startTimestamp = now - durationMs,
                endTimestamp = now,
                durationMs = durationMs,
                trackTitle = trackTitle ?: "",
                trackArtist = trackArtist ?: "",
                trackAlbum = trackAlbum ?: "",
                trackAlbumArtist = trackAlbumArtist ?: ""
            )
        }
    }

    private data class MidiSession(
        val startTimestamp: Long,
        val midiFileName: String?,
        val isPracticeMode: Boolean,
        var accumulatedMs: Long = 0,      // temps de lecture effectif accumulé (hors pauses)
        var intervalStartMs: Long? = null  // début de l'intervalle courant (null = pausé)
    ) {
        fun toEntity(now: Long): UsageSessionEntity? {
            val durationMs = accumulatedMs + (intervalStartMs?.let { now - it } ?: 0L)
            if (durationMs < MIN_SESSION_DURATION_MS) return null
            return UsageSessionEntity(
                moduleType = if (isPracticeMode) StatsRepository.MODULE_MIDI_PRACTICE else StatsRepository.MODULE_MIDI,
                startTimestamp = startTimestamp,
                endTimestamp = now,
                durationMs = durationMs,
                midiFileName = midiFileName ?: ""
            )
        }
    }

    private data class RadioSession(
        val startTimestamp: Long,
        val stationName: String?,
        var accumulatedMs: Long = 0,      // temps d'écoute effectif accumulé (hors pauses)
        var intervalStartMs: Long? = null  // début de l'intervalle courant (null = pausé)
    ) {
        fun toEntity(now: Long): UsageSessionEntity? {
            val durationMs = accumulatedMs + (intervalStartMs?.let { now - it } ?: 0L)
            if (durationMs < MIN_SESSION_DURATION_MS) return null
            return UsageSessionEntity(
                moduleType = StatsRepository.MODULE_RADIO,
                startTimestamp = startTimestamp,
                endTimestamp = now,
                durationMs = durationMs,
                radioStationName = stationName ?: ""
            )
        }
    }

    private data class ReadingSession(
        val startTimestamp: Long,
        val moduleType: String,
        val title: String?,
        val ownerId: Int?
    ) {
        fun toEntity(now: Long): UsageSessionEntity? {
            val durationMs = now - startTimestamp
            if (durationMs < MIN_SESSION_DURATION_MS) return null
            return UsageSessionEntity(
                moduleType = moduleType,
                startTimestamp = startTimestamp,
                endTimestamp = now,
                durationMs = durationMs,
                readingTitle = title ?: ""
            )
        }
    }
}
