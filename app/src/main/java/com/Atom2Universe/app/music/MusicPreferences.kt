package com.Atom2Universe.app.music

import android.content.Context
import android.content.SharedPreferences

/**
 * Modes d'affichage des artistes
 */
enum class ArtistDisplayMode {
    LIST,      // Liste avec icones (mode par defaut)
    COMPACT,   // Liste compacte sans icones
    TILES      // Grille de tuiles
}

/**
 * Modes d'affichage des albums
 */
enum class AlbumDisplayMode {
    LIST,      // Liste avec pochettes (mode par defaut)
    COMPACT,   // Liste compacte sans pochettes
    TILES      // Grille de tuiles
}

/**
 * Modes d'affichage des titres (pistes)
 */
enum class TrackDisplayMode {
    LIST,      // Liste detaillee avec artiste (mode par defaut)
    COMPACT    // Liste compacte titre + duree uniquement
}

/**
 * Modes d'affichage des dossiers
 */
enum class FolderDisplayMode {
    LIST,      // Liste avec icones (mode par defaut)
    COMPACT   // Liste compacte sans icones
}

/**
 * Modes d'affichage du menu ROOT
 */
enum class RootDisplayMode {
    LIST,      // Cartes avec icones et sous-titres (mode par defaut)
    COMPACT    // Liste simple sans cartes
}

/**
 * Gestionnaire des préférences du lecteur audio.
 */
class MusicPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "music_preferences"

        // Keys
        private const val KEY_ALBUM_SORT_ORDER = "album_sort_order"
        private const val KEY_SHOW_PLAY_COUNT = "show_play_count"
        private const val KEY_ARTIST_DISPLAY_MODE = "artist_display_mode"
        private const val KEY_ARTIST_TILE_COLUMNS = "artist_tile_columns"
        private const val KEY_ALBUM_DISPLAY_MODE = "album_display_mode"
        private const val KEY_ALBUM_TILE_COLUMNS = "album_tile_columns"
        private const val KEY_FOLDER_DISPLAY_MODE = "folder_display_mode"
        private const val KEY_FOLDER_TILE_COLUMNS = "folder_tile_columns"
        private const val KEY_TRACK_DISPLAY_MODE = "track_display_mode"
        private const val KEY_ROOT_DISPLAY_MODE = "root_display_mode"
        private const val KEY_ALBUMS_TRACK_NUMBER_CHECKED = "albums_track_number_checked"
        private const val KEY_ROOT_OPTION_ORDER = "root_option_order"
        private const val KEY_VISUALIZER_MODES_ORDER = "visualizer_modes_order"
        private const val KEY_HIDDEN_ROOT_OPTIONS = "hidden_root_options"
        private const val KEY_AUTO_FETCH_LYRICS = "auto_fetch_lyrics"
        private const val KEY_SUGGEST_TRACK_NUMBERS = "suggest_track_numbers"
        private const val KEY_LYRICS_AUTO_SCROLL = "lyrics_auto_scroll"
        private const val KEY_WRITE_TAGS_TO_FILES = "write_tags_to_files"

        // Navidrome keys
        private const val KEY_NAVIDROME_SERVER_URL = "navidrome_server_url"
        private const val KEY_NAVIDROME_USERNAME = "navidrome_username"
        private const val KEY_NAVIDROME_PASSWORD = "navidrome_password"

        // Lyrics API keys
        private const val KEY_LYRICS_API_PRIMARY = "lyrics_api_primary"
        private const val KEY_LYRICS_API_FALLBACK = "lyrics_api_fallback"
        private const val KEY_LYRICS_API_PRIMARY_HEADERS = "lyrics_api_primary_headers"
        private const val KEY_LYRICS_API_FALLBACK_HEADERS = "lyrics_api_fallback_headers"
        private const val KEY_LYRICS_API_PRIMARY_LYRICS_PATH = "lyrics_api_primary_lyrics_path"
        private const val KEY_LYRICS_API_PRIMARY_SYNCED_PATH = "lyrics_api_primary_synced_path"
        private const val KEY_LYRICS_API_FALLBACK_LYRICS_PATH = "lyrics_api_fallback_lyrics_path"
        private const val KEY_LYRICS_API_FALLBACK_SYNCED_PATH = "lyrics_api_fallback_synced_path"

        // Equalizer keys
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_GLOBAL_PRESET_ID = "eq_global_preset_id"

        // Defaults
        const val DEFAULT_TILE_COLUMNS = 2
        const val MIN_TILE_COLUMNS = 1
        const val MAX_TILE_COLUMNS = 8

        // Singleton instance
        @Volatile
        private var instance: MusicPreferences? = null

        fun getInstance(context: Context): MusicPreferences {
            return instance ?: synchronized(this) {
                instance ?: MusicPreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Ordre de tri des albums (sauvegardé comme ordinal de l'enum)
     */
    var albumSortOrder: MusicLibrary.AlbumSortOrder
        get() {
            val ordinal = prefs.getInt(KEY_ALBUM_SORT_ORDER, MusicLibrary.AlbumSortOrder.NAME_ASC.ordinal)
            return MusicLibrary.AlbumSortOrder.entries.getOrElse(ordinal) { MusicLibrary.AlbumSortOrder.NAME_ASC }
        }
        set(value) {
            prefs.edit().putInt(KEY_ALBUM_SORT_ORDER, value.ordinal).apply()
        }

    /**
     * Afficher le nombre d'écoutes dans la liste des pistes
     */
    var showPlayCount: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PLAY_COUNT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_PLAY_COUNT, value).apply()
        }

    /**
     * Recherche automatique des paroles lors de la lecture
     */
    var autoFetchLyrics: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FETCH_LYRICS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_FETCH_LYRICS, value).apply()
        }

    /**
     * Proposer automatiquement d'ajouter les numéros de piste manquants
     * quand ils sont détectés dans le nom du fichier.
     * Désactivé par défaut.
     */
    var suggestTrackNumbers: Boolean
        get() = prefs.getBoolean(KEY_SUGGEST_TRACK_NUMBERS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SUGGEST_TRACK_NUMBERS, value).apply()
        }

    /**
     * Défilement automatique des paroles synchronisées.
     * Activé par défaut.
     */
    var lyricsAutoScroll: Boolean
        get() = prefs.getBoolean(KEY_LYRICS_AUTO_SCROLL, true)
        set(value) {
            prefs.edit().putBoolean(KEY_LYRICS_AUTO_SCROLL, value).apply()
        }

    /**
     * Écrire les métadonnées dans les tags ID3 et POPM des fichiers.
     * Si désactivé, les stats et changements sont stockés uniquement en local (Room) et sur Google Drive.
     * Désactivé par défaut.
     */
    var writeTagsToFiles: Boolean
        get() = prefs.getBoolean(KEY_WRITE_TAGS_TO_FILES, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WRITE_TAGS_TO_FILES, value).apply()
        }

    // ========== Navidrome Settings ==========

    var navidromeServerUrl: String
        get() = prefs.getString(KEY_NAVIDROME_SERVER_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_NAVIDROME_SERVER_URL, value.trim()).apply()
        }

    var navidromeUsername: String
        get() = prefs.getString(KEY_NAVIDROME_USERNAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_NAVIDROME_USERNAME, value.trim()).apply()
        }

    var navidromePassword: String
        get() = prefs.getString(KEY_NAVIDROME_PASSWORD, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_NAVIDROME_PASSWORD, value).apply()
        }

    /**
     * URL de l'API principale pour les paroles (vide = désactivée).
     * L'utilisateur doit configurer cette URL manuellement.
     */
    var lyricsApiPrimary: String
        get() = prefs.getString(KEY_LYRICS_API_PRIMARY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LYRICS_API_PRIMARY, value.trim()).apply()
        }

    /**
     * URL de l'API de secours pour les paroles (vide = désactivée).
     * L'utilisateur doit configurer cette URL manuellement.
     */
    var lyricsApiFallback: String
        get() = prefs.getString(KEY_LYRICS_API_FALLBACK, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LYRICS_API_FALLBACK, value.trim()).apply()
        }

    /**
     * Headers for the primary lyrics API (optional).
     */
    var lyricsApiPrimaryHeaders: String
        get() = prefs.getString(KEY_LYRICS_API_PRIMARY_HEADERS, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LYRICS_API_PRIMARY_HEADERS, value).apply()
        }

    /**
     * Headers for the fallback lyrics API (optional).
     */
    var lyricsApiFallbackHeaders: String
        get() = prefs.getString(KEY_LYRICS_API_FALLBACK_HEADERS, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LYRICS_API_FALLBACK_HEADERS, value).apply()
        }

    /**
     * JSON path for primary API lyrics field (optional).
     */
    var lyricsApiPrimaryLyricsPath: String
        get() = prefs.getString(KEY_LYRICS_API_PRIMARY_LYRICS_PATH, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LYRICS_API_PRIMARY_LYRICS_PATH, value.trim()).apply()
        }

    /**
     * JSON path for primary API synced lyrics field (optional).
     */
    var lyricsApiPrimarySyncedPath: String
        get() = prefs.getString(KEY_LYRICS_API_PRIMARY_SYNCED_PATH, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LYRICS_API_PRIMARY_SYNCED_PATH, value.trim()).apply()
        }

    /**
     * JSON path for fallback API lyrics field (optional).
     */
    var lyricsApiFallbackLyricsPath: String
        get() = prefs.getString(KEY_LYRICS_API_FALLBACK_LYRICS_PATH, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LYRICS_API_FALLBACK_LYRICS_PATH, value.trim()).apply()
        }

    /**
     * JSON path for fallback API synced lyrics field (optional).
     */
    var lyricsApiFallbackSyncedPath: String
        get() = prefs.getString(KEY_LYRICS_API_FALLBACK_SYNCED_PATH, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LYRICS_API_FALLBACK_SYNCED_PATH, value.trim()).apply()
        }

    /**
     * Mode d'affichage des artistes
     */
    var artistDisplayMode: ArtistDisplayMode
        get() {
            val ordinal = prefs.getInt(KEY_ARTIST_DISPLAY_MODE, ArtistDisplayMode.LIST.ordinal)
            return ArtistDisplayMode.entries.getOrElse(ordinal) { ArtistDisplayMode.LIST }
        }
        set(value) {
            prefs.edit().putInt(KEY_ARTIST_DISPLAY_MODE, value.ordinal).apply()
        }

    /**
     * Nombre de colonnes en mode tuiles artistes (1-8)
     */
    var artistTileColumns: Int
        get() = prefs.getInt(KEY_ARTIST_TILE_COLUMNS, DEFAULT_TILE_COLUMNS)
            .coerceIn(MIN_TILE_COLUMNS, MAX_TILE_COLUMNS)
        set(value) {
            prefs.edit().putInt(KEY_ARTIST_TILE_COLUMNS, value.coerceIn(MIN_TILE_COLUMNS, MAX_TILE_COLUMNS)).apply()
        }

    /**
     * Mode d'affichage des albums
     */
    var albumDisplayMode: AlbumDisplayMode
        get() {
            val ordinal = prefs.getInt(KEY_ALBUM_DISPLAY_MODE, AlbumDisplayMode.LIST.ordinal)
            return AlbumDisplayMode.entries.getOrElse(ordinal) { AlbumDisplayMode.LIST }
        }
        set(value) {
            prefs.edit().putInt(KEY_ALBUM_DISPLAY_MODE, value.ordinal).apply()
        }

    /**
     * Nombre de colonnes en mode tuiles albums (1-8)
     */
    var albumTileColumns: Int
        get() = prefs.getInt(KEY_ALBUM_TILE_COLUMNS, DEFAULT_TILE_COLUMNS)
            .coerceIn(MIN_TILE_COLUMNS, MAX_TILE_COLUMNS)
        set(value) {
            prefs.edit().putInt(KEY_ALBUM_TILE_COLUMNS, value.coerceIn(MIN_TILE_COLUMNS, MAX_TILE_COLUMNS)).apply()
        }

    /**
     * Mode d'affichage des dossiers
     */
    var folderDisplayMode: FolderDisplayMode
        get() {
            val ordinal = prefs.getInt(KEY_FOLDER_DISPLAY_MODE, FolderDisplayMode.LIST.ordinal)
            return FolderDisplayMode.entries.getOrElse(ordinal) { FolderDisplayMode.LIST }
        }
        set(value) {
            prefs.edit().putInt(KEY_FOLDER_DISPLAY_MODE, value.ordinal).apply()
        }

    /**
     * Nombre de colonnes en mode tuiles dossiers (1-8)
     */
    var folderTileColumns: Int
        get() = prefs.getInt(KEY_FOLDER_TILE_COLUMNS, DEFAULT_TILE_COLUMNS)
            .coerceIn(MIN_TILE_COLUMNS, MAX_TILE_COLUMNS)
        set(value) {
            prefs.edit().putInt(KEY_FOLDER_TILE_COLUMNS, value.coerceIn(MIN_TILE_COLUMNS, MAX_TILE_COLUMNS)).apply()
        }

    /**
     * Mode d'affichage des titres (pistes)
     */
    var trackDisplayMode: TrackDisplayMode
        get() {
            val ordinal = prefs.getInt(KEY_TRACK_DISPLAY_MODE, TrackDisplayMode.LIST.ordinal)
            return TrackDisplayMode.entries.getOrElse(ordinal) { TrackDisplayMode.LIST }
        }
        set(value) {
            prefs.edit().putInt(KEY_TRACK_DISPLAY_MODE, value.ordinal).apply()
        }

    /**
     * Mode d'affichage du menu ROOT
     */
    var rootDisplayMode: RootDisplayMode
        get() {
            val ordinal = prefs.getInt(KEY_ROOT_DISPLAY_MODE, RootDisplayMode.LIST.ordinal)
            return RootDisplayMode.entries.getOrElse(ordinal) { RootDisplayMode.LIST }
        }
        set(value) {
            prefs.edit().putInt(KEY_ROOT_DISPLAY_MODE, value.ordinal).apply()
        }

    /**
     * Ordre personnalisé des options affichées au niveau ROOT.
     */
    var rootOptionOrder: List<String>
        get() {
            val stored = prefs.getString(KEY_ROOT_OPTION_ORDER, "")?.trim().orEmpty()
            if (stored.isBlank()) {
                return emptyList()
            }
            return stored.split("|").filter { it.isNotBlank() }
        }
        set(value) {
            val serialized = value.joinToString(separator = "|")
            prefs.edit().putString(KEY_ROOT_OPTION_ORDER, serialized).apply()
        }

    /**
     * Ordre personnalisé des modes visualiseur (noms d'enum séparés par |).
     * Liste vide = ordre par défaut.
     */
    var visualizerModesOrder: List<String>
        get() {
            val stored = prefs.getString(KEY_VISUALIZER_MODES_ORDER, "")?.trim().orEmpty()
            if (stored.isBlank()) return emptyList()
            return stored.split("|").filter { it.isNotBlank() }
        }
        set(value) {
            prefs.edit().putString(KEY_VISUALIZER_MODES_ORDER, value.joinToString("|")).apply()
        }

    /**
     * IDs des options ROOT masquées par l'utilisateur.
     */
    var hiddenRootOptions: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_ROOT_OPTIONS, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_HIDDEN_ROOT_OPTIONS, value).apply()
        }

    /**
     * Vérifie si une option ROOT est visible (non masquée).
     */
    fun isRootOptionVisible(optionId: String): Boolean {
        return optionId !in hiddenRootOptions
    }

    /**
     * Définit la visibilité d'une option ROOT.
     */
    fun setRootOptionVisible(optionId: String, visible: Boolean) {
        val current = hiddenRootOptions.toMutableSet()
        if (visible) {
            current.remove(optionId)
        } else {
            current.add(optionId)
        }
        hiddenRootOptions = current
    }

    /**
     * Vérifie si un album a déjà été traité pour la proposition de numéros de piste.
     * L'identifiant est construit à partir de l'artiste et du nom de l'album.
     */
    fun isAlbumTrackNumberChecked(artistName: String, albumName: String): Boolean {
        val albumId = "$artistName|$albumName"
        val checkedAlbums = prefs.getStringSet(KEY_ALBUMS_TRACK_NUMBER_CHECKED, emptySet()) ?: emptySet()
        return albumId in checkedAlbums
    }

    /**
     * Marque un album comme traité pour la proposition de numéros de piste.
     */
    fun markAlbumTrackNumberChecked(artistName: String, albumName: String) {
        val albumId = "$artistName|$albumName"
        val checkedAlbums = prefs.getStringSet(KEY_ALBUMS_TRACK_NUMBER_CHECKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        checkedAlbums.add(albumId)
        prefs.edit().putStringSet(KEY_ALBUMS_TRACK_NUMBER_CHECKED, checkedAlbums).apply()
    }

    /**
     * Réinitialise la liste des albums traités (utile pour forcer une nouvelle vérification).
     */
    fun clearAlbumsTrackNumberChecked() {
        prefs.edit().remove(KEY_ALBUMS_TRACK_NUMBER_CHECKED).apply()
    }

    // ========== Equalizer Settings ==========

    /**
     * Equalizer enabled/disabled state
     */
    var eqEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQ_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_EQ_ENABLED, value).apply()
        }

    /**
     * ID of the global/default EQ preset (1 = Flat by default)
     */
    var eqGlobalPresetId: Long
        get() = prefs.getLong(KEY_EQ_GLOBAL_PRESET_ID, 1L)
        set(value) {
            prefs.edit().putLong(KEY_EQ_GLOBAL_PRESET_ID, value).apply()
        }
}
