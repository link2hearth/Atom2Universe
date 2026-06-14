package com.Atom2Universe.app.music.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Source de vérité pour les albums déjà traités par la proposition de numéros
 * de piste, persistée dans [MusicDatabase] (table album_track_number_checks).
 *
 * Conserve un cache mémoire pour exposer un test synchrone ([isChecked]) appelable
 * depuis le main thread, tout en persistant de façon incrémentale (une ligne par
 * album, en IO) au lieu de réécrire un StringSet entier comme avant.
 *
 * Migre une seule fois l'ancien StringSet `albums_track_number_checked` des
 * SharedPreferences `music_preferences`, puis le supprime.
 */
object AlbumTrackCheckStore {

    private const val LEGACY_PREFS = "music_preferences"
    private const val LEGACY_KEY = "albums_track_number_checked"

    private val lock = Any()
    private val cache = mutableSetOf<String>()
    @Volatile private var loaded = false
    @Volatile private var loading = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Déclenche (une seule fois) le chargement asynchrone du cache depuis la base.
     * Appeler tôt (ex. construction de MusicPreferences) pour que le cache soit
     * prêt avant le premier [isChecked].
     */
    fun ensureLoaded(context: Context) {
        if (loaded || loading) return
        synchronized(lock) {
            if (loaded || loading) return
            loading = true
        }
        val appCtx = context.applicationContext
        scope.launch {
            val dao = MusicDatabase.getInstance(appCtx).albumTrackCheckDao()
            migrateLegacyPrefs(appCtx, dao)
            val keys = dao.getAllKeys()
            synchronized(lock) {
                cache.addAll(keys)
                loaded = true
                loading = false
            }
        }
    }

    fun isChecked(context: Context, albumKey: String): Boolean {
        ensureLoaded(context)
        synchronized(lock) { return cache.contains(albumKey) }
    }

    fun markChecked(context: Context, albumKey: String) {
        ensureLoaded(context)
        synchronized(lock) { cache.add(albumKey) }
        val appCtx = context.applicationContext
        scope.launch {
            MusicDatabase.getInstance(appCtx).albumTrackCheckDao()
                .insert(AlbumTrackCheckEntity(albumKey))
        }
    }

    fun clear(context: Context) {
        synchronized(lock) { cache.clear() }
        val appCtx = context.applicationContext
        scope.launch {
            MusicDatabase.getInstance(appCtx).albumTrackCheckDao().clear()
        }
    }

    private suspend fun migrateLegacyPrefs(context: Context, dao: AlbumTrackCheckDao) {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacy = prefs.getStringSet(LEGACY_KEY, null) ?: return
        if (legacy.isNotEmpty()) {
            dao.insertAll(legacy.map { AlbumTrackCheckEntity(it) })
        }
        prefs.edit { remove(LEGACY_KEY) }
    }
}
