package com.Atom2Universe.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.Atom2Universe.app.R
import com.Atom2Universe.app.midi.ui.MidiPlayerActivity
import com.Atom2Universe.app.music.MusicPlayerActivity
import com.Atom2Universe.app.radio.RadioActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

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

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
        super.onReceive(context, intent)

        Log.d(TAG, "onReceive: ${intent.action}")

        when (intent.action) {
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

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_music)
        val state = MusicWidgetController.getCurrentState(context)

        Log.d(TAG, "Updating widget $appWidgetId: source=${state.source}, playing=${state.isPlaying}, title=${state.title}")

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
                // Charger la pochette d'album si disponible
                if (state.albumArtUri != null) {
                    loadAlbumArt(context, state.albumArtUri, views, appWidgetManager, appWidgetId)
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_note)
                }
            }
            MusicWidgetController.ActiveSource.RADIO -> {
                views.setViewVisibility(R.id.widget_source_badge, View.VISIBLE)
                views.setTextViewText(R.id.widget_source_badge, "RADIO")
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_radio)
            }
            MusicWidgetController.ActiveSource.MIDI -> {
                views.setViewVisibility(R.id.widget_source_badge, View.VISIBLE)
                views.setTextViewText(R.id.widget_source_badge, "MIDI")
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_midi)
            }
            MusicWidgetController.ActiveSource.NONE -> {
                // Vérifier si c'est une dernière station radio ou une queue musique
                val isRadioSaved = state.artist == "Radio"
                if (isRadioSaved) {
                    views.setViewVisibility(R.id.widget_source_badge, View.GONE)
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_radio)
                } else if (state.albumArtUri != null) {
                    views.setViewVisibility(R.id.widget_source_badge, View.GONE)
                    loadAlbumArt(context, state.albumArtUri, views, appWidgetManager, appWidgetId)
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
        val isRadioSaved = state.source == MusicWidgetController.ActiveSource.NONE && state.artist == "Radio"
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

        // Mettre à jour le widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun createActionPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MusicWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadAlbumArt(
        context: Context,
        albumArtUri: String,
        views: RemoteViews,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(context, Uri.parse(albumArtUri))
                }
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading album art", e)
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding album art", e)
            null
        }
    }
}
