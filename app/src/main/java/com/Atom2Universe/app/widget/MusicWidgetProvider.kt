package com.Atom2Universe.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.ui.MidiPlayerActivity
import com.Atom2Universe.app.music.MusicArtworkLoader
import com.Atom2Universe.app.music.MusicPlayerActivity
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.radio.RadioActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Provider pour le widget audio unifié.
 *
 * Affiche les informations de lecture et fournit des boutons de contrôle
 * pour Music, Radio et MIDI.
 */
class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "MusicWidgetProvider"

        const val ACTION_PLAY_PAUSE = "com.Atom2Universe.app.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.Atom2Universe.app.widget.NEXT"
        const val ACTION_PREVIOUS = "com.Atom2Universe.app.widget.PREVIOUS"

        /** Jeton secret mis dans nos boutons : une autre appli ne peut pas piloter la lecture. */
        private const val EXTRA_TOKEN = "com.Atom2Universe.app.widget.TOKEN"
        private const val PREFS_NAME = "music_widget_prefs"
        private const val KEY_TOKEN = "action_token"

        private const val ART_SIZE_PX = 256
        // Le temps d'attendre une pochette avant d'afficher la vignette par défaut
        private const val ART_WAIT_MS = 700L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val artworkJobs = mutableMapOf<Int, Job>()

        // Pochette du dernier morceau affiché (null = morceau sans pochette) : un changement
        // d'état (lecture/pause) la remet dans la mise à jour sans relire le disque ni clignoter.
        // Fil principal seulement.
        private var lastArtKey: String? = null
        private var lastArt: Bitmap? = null
        private var artLoadingKey: String? = null
        private var artLoading: Deferred<Bitmap?>? = null

        @Volatile private var token: String? = null

        private fun actionToken(context: Context): String {
            token?.let { return it }
            synchronized(this) {
                token?.let { return it }
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val stored = prefs.getString(KEY_TOKEN, null) ?: UUID.randomUUID().toString().also {
                    prefs.edit().putString(KEY_TOKEN, it).apply()
                }
                token = stored
                return stored
            }
        }

        private fun artKey(track: MusicTrack): String = "${track.uri}|${track.albumArtUri}"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")

        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Keep the receiver alive until the asynchronous artwork updates have finished.
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
            val pending = goAsync()
            val app = context.applicationContext
            scope.launch {
                try {
                    val manager = AppWidgetManager.getInstance(app)
                    val jobs = ids.map { updateWidget(app, manager, it) }
                    // Remote artwork can be slow; do not exceed the receiver's time budget.
                    withTimeoutOrNull(8_000L) { jobs.joinAll() }
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)

        Log.d(TAG, "onReceive: ${intent.action}")

        val action = intent.action
        if (action == ACTION_PLAY_PAUSE || action == ACTION_NEXT || action == ACTION_PREVIOUS) {
            if (intent.getStringExtra(EXTRA_TOKEN) != actionToken(context)) return
        }

        when (action) {
            ACTION_PLAY_PAUSE -> {
                MusicWidgetController.playPause(context)
            }
            ACTION_NEXT -> {
                MusicWidgetController.next(context)
            }
            ACTION_PREVIOUS -> {
                MusicWidgetController.previous(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled")
        MusicWidgetController.init(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { artworkJobs.remove(it)?.cancel() }
        super.onDeleted(context, appWidgetIds)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ): Job {
        artworkJobs.remove(appWidgetId)?.cancel()
        val state = MusicWidgetController.getCurrentState(context)

        Log.d(TAG, "Updating widget $appWidgetId: source=${state.source}, playing=${state.isPlaying}, title=${state.title}")

        val track = state.musicTrack
        if (track == null) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, state, null))
            return Job().apply { complete() }
        }
        val key = artKey(track)
        // Même morceau : une seule mise à jour, pochette comprise
        if (key == lastArtKey) {
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, state, lastArt))
            return Job().apply { complete() }
        }

        // Nouveau morceau : la pochette, souvent déjà en cache, part avec la mise à jour
        // complète ; trop lente (réseau), elle suit à part après la vignette par défaut.
        val job = scope.launch {
            try {
                val loading = artLoad(context, track, key)
                val early = withTimeoutOrNull(ART_WAIT_MS) { loading.await() }
                currentCoroutineContext().ensureActive()
                appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, state, early))
                val bitmap = early ?: loading.await()
                lastArtKey = key
                lastArt = bitmap
                if (early == null && bitmap != null) {
                    // Title and controls belong to the latest full update.
                    val artwork = RemoteViews(context.packageName, R.layout.widget_music)
                    artwork.setImageViewBitmap(R.id.widget_album_art, bitmap)
                    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, artwork)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget", e)
            }
        }
        artworkJobs[appWidgetId] = job
        job.invokeOnCompletion {
            if (artworkJobs[appWidgetId] === job) artworkJobs.remove(appWidgetId)
        }
        return job
    }

    /**
     * Une seule lecture par morceau : les mises à jour rapprochées d'un changement de piste
     * (piste, puis lecture, puis prête) attendent la même au lieu de la relancer.
     */
    private fun artLoad(context: Context, track: MusicTrack, key: String): Deferred<Bitmap?> {
        val current = artLoading
        if (current != null && artLoadingKey == key) return current
        current?.cancel()
        return scope.async { loadArt(context, track) }.also {
            artLoading = it
            artLoadingKey = key
        }
    }

    private suspend fun loadArt(context: Context, track: MusicTrack): Bitmap? = try {
        MusicArtworkLoader.loadTrackBitmap(context, track, size = ART_SIZE_PX)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Log.e(TAG, "Error loading widget album art", e)
        null
    }

    private fun buildViews(
        context: Context,
        state: MusicWidgetController.WidgetState,
        art: Bitmap?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_music)

        // Configurer le titre et l'artiste
        views.setTextViewText(R.id.widget_title, state.title.ifEmpty { context.getString(R.string.widget_no_track) })
        views.setTextViewText(R.id.widget_artist, state.artist)

        // Configurer l'icône play/pause
        val playPauseIcon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

        // Configurer la visibilité des boutons next/prev
        val showNavButtons = state.hasNext || state.hasPrevious
        views.setViewVisibility(R.id.widget_btn_prev, if (showNavButtons) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_next, if (showNavButtons) View.VISIBLE else View.GONE)

        // Configurer le badge de source
        when (state.source) {
            MusicWidgetController.ActiveSource.MUSIC -> {
                views.setViewVisibility(R.id.widget_source_badge, View.GONE)
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_note)
            }
            MusicWidgetController.ActiveSource.RADIO -> {
                views.setViewVisibility(R.id.widget_source_badge, View.VISIBLE)
                views.setTextViewText(R.id.widget_source_badge, context.getString(R.string.music_widget_badge_radio))
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_radio)
            }
            MusicWidgetController.ActiveSource.MIDI -> {
                views.setViewVisibility(R.id.widget_source_badge, View.VISIBLE)
                views.setTextViewText(R.id.widget_source_badge, context.getString(R.string.music_widget_badge_midi))
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_midi)
            }
            MusicWidgetController.ActiveSource.NONE -> {
                // Vérifier si c'est une dernière station radio ou une queue musique
                if (state.isSavedRadio) {
                    views.setViewVisibility(R.id.widget_source_badge, View.GONE)
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_radio)
                } else {
                    views.setViewVisibility(R.id.widget_source_badge, View.GONE)
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_note)
                }
            }
        }

        // Configurer les actions des boutons
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, createActionPendingIntent(context, ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_btn_next, createActionPendingIntent(context, ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, createActionPendingIntent(context, ACTION_PREVIOUS))

        // Clic sur la pochette pour ouvrir l'activité appropriée selon la source
        val isRadioSaved = state.source == MusicWidgetController.ActiveSource.NONE && state.isSavedRadio
        val isMusicSource = state.source == MusicWidgetController.ActiveSource.MUSIC ||
                (state.source == MusicWidgetController.ActiveSource.NONE && !isRadioSaved && state.title.isNotEmpty())
        val targetActivity = when {
            state.source == MusicWidgetController.ActiveSource.MUSIC -> MusicPlayerActivity::class.java
            state.source == MusicWidgetController.ActiveSource.RADIO -> RadioActivity::class.java
            state.source == MusicWidgetController.ActiveSource.MIDI -> MidiPlayerActivity::class.java
            isRadioSaved -> RadioActivity::class.java  // Dernière station radio sauvegardée
            else -> MusicPlayerActivity::class.java // Queue musique sauvegardée
        }
        val openAppIntent = Intent(context, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Ouvrir directement le full player si c'est une source musique
            if (isMusicSource) {
                putExtra(MusicPlayerActivity.EXTRA_OPEN_FULL_PLAYER, true)
            }
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)

        // Pochette posée dans la même mise à jour : pas de passage par la vignette par défaut
        if (art != null) views.setImageViewBitmap(R.id.widget_album_art, art)
        return views
    }

    private fun createActionPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MusicWidgetProvider::class.java).apply {
            this.action = action
            putExtra(EXTRA_TOKEN, actionToken(context))
        }
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}
