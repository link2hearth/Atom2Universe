package com.Atom2Universe.app.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Atom2Universe.app.AudioPlaybackManager
import com.Atom2Universe.app.LocaleHelper
import com.Atom2Universe.app.R
import com.Atom2Universe.app.ThemedActivity
import com.google.android.material.color.MaterialColors
import com.Atom2Universe.app.music.adapter.AlbumAdapter
import com.Atom2Universe.app.music.adapter.ArtistAdapter
import com.Atom2Universe.app.music.adapter.FolderAdapter
import com.Atom2Universe.app.music.adapter.FolderContentAdapter
import com.Atom2Universe.app.music.adapter.FolderContentItem
import com.Atom2Universe.app.music.adapter.MusicPlaylistAdapter
import com.Atom2Universe.app.music.adapter.MusicTrackAdapter
import com.Atom2Universe.app.music.adapter.PlaylistItem
import com.Atom2Universe.app.music.adapter.RootOption
import com.Atom2Universe.app.music.adapter.RootOptionAdapter
import com.Atom2Universe.app.music.model.Album
import com.Atom2Universe.app.music.model.Folder
import com.Atom2Universe.app.music.model.AlbumListItem
import com.Atom2Universe.app.music.sync.CloudSyncManager
import com.Atom2Universe.app.music.model.Artist
import com.Atom2Universe.app.music.model.MusicTrack
import com.Atom2Universe.app.music.model.Playlist
import com.Atom2Universe.app.music.model.PlaylistData
import com.Atom2Universe.app.NoteColorPickerDialog
import com.Atom2Universe.app.AudioHubActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import com.Atom2Universe.app.music.navidrome.NavidromeAlbum
import com.Atom2Universe.app.music.navidrome.NavidromeArtist
import com.Atom2Universe.app.music.navidrome.NavidromeLibrary
import com.Atom2Universe.app.music.navidrome.SubsonicApiClient
import com.Atom2Universe.app.util.enableImmersiveMode

class MusicPlayerActivity : ThemedActivity(), MusicPlaybackHolder.PlayerListener {

    companion object {
        const val EXTRA_OPEN_FULL_PLAYER = "extra_open_full_player"
        const val EXTRA_FROM_CLICKER_WIDGET = "extra_from_clicker_widget"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    // Navigation levels
    enum class NavigationLevel {
        ROOT,           // Choix Artistes / Playlists / Albums / Recherche
        ARTISTS,        // Liste des artistes
        ALBUM_ARTISTS,  // Liste des artistes d'album
        FAVORITE_ARTISTS, // Liste des artistes favoris
        ALBUMS,         // Albums d'un artiste
        ALL_ALBUMS,     // Tous les albums
        FAVORITE_ALBUMS, // Albums favoris
        TRACKS,         // Pistes d'un album
        PLAYLISTS,      // Liste des playlists
        PLAYLIST_TRACKS,// Pistes d'une playlist
        FOLDERS,        // Arborescence des dossiers
        FOLDER_TRACKS,  // Pistes d'un dossier spécifique
        SEARCH,         // Résultats de recherche
        NAVIDROME_ROOT,   // Liste des artistes Navidrome
        NAVIDROME_ARTIST, // Albums d'un artiste Navidrome
        NAVIDROME_ALBUM   // Pistes d'un album Navidrome
    }

    private var currentLevel = NavigationLevel.ROOT
    private var currentArtist: Artist? = null
    private var currentAlbum: Album? = null
    private var currentPlaylist: Playlist? = null
    private var currentFolder: Folder? = null
    private var currentNavidromeArtist: NavidromeArtist? = null
    private var currentNavidromeAlbum: NavidromeAlbum? = null
    private var navidromeApiClient: SubsonicApiClient? = null
    private var currentSearchQuery: String = ""
    private var isInAlbumArtistMode: Boolean = false  // Pour différencier Album Artist vs Artist
    private var displayedTracks: List<MusicTrack> = emptyList()  // Pistes actuellement affichées (pour la lecture)

    // Contexte parent pour la navigation retour
    private var parentAlbumsLevel: NavigationLevel? = null  // D'où on vient quand on est dans TRACKS (ALL_ALBUMS, FAVORITE_ALBUMS, ou ALBUMS)
    private var parentArtistsLevel: NavigationLevel? = null  // D'où on vient quand on est dans ALBUMS (ARTISTS, ALBUM_ARTISTS, ou FAVORITE_ARTISTS)

    // Historique de navigation des dossiers pour les breadcrumbs
    private val folderPathHistory = mutableListOf<Folder>()

    // État sauvegardé pour restauration après configuration change
    private var pendingSavedInstanceState: Bundle? = null

    enum class FolderTrackSort { BY_NAME, BY_TRACK_NUMBER }
    private var folderTrackSort = FolderTrackSort.BY_NAME

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var breadcrumbScroll: HorizontalScrollView
    private lateinit var breadcrumbContainer: LinearLayout
    private lateinit var statsInfo: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var emptyState: TextView
    private lateinit var contentList: RecyclerView
    private lateinit var alphabetIndex: AlphabetIndexView
    // Folder tracks sort bar
    private lateinit var folderTracksSortBar: View
    private lateinit var btnSortByName: TextView
    private lateinit var btnSortByTrackNumber: TextView
    private lateinit var folderTracksCount: TextView

    // Mini player views
    private lateinit var miniPlayer: ConstraintLayout
    private lateinit var miniProgress: ProgressBar
    private lateinit var miniAlbumArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniBtnPrev: ImageButton
    private lateinit var miniBtnPlayPause: ImageButton
    private lateinit var miniBtnNext: ImageButton

    // Adapters
    private lateinit var rootOptionAdapter: RootOptionAdapter
    private lateinit var artistAdapter: ArtistAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var folderContentAdapter: FolderContentAdapter
    private lateinit var trackAdapter: MusicTrackAdapter
    private lateinit var playlistAdapter: MusicPlaylistAdapter

    // Scale gesture detector for pinch-to-zoom in tile mode
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentArtistTileColumns: Int = MusicPreferences.DEFAULT_TILE_COLUMNS
    private var currentAlbumTileColumns: Int = MusicPreferences.DEFAULT_TILE_COLUMNS
    private var rootOptions: MutableList<RootOption> = mutableListOf()
    private var rootItemTouchHelper: ItemTouchHelper? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadMusicLibrary()
        } else {
            showPermissionDeniedMessage()
        }
    }

    // Folder picker for SAF
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Persist permission for this folder
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)

            // Add folder to manager
            MusicFoldersManager.addCustomFolder(uri.toString())

            // Show confirmation and reload
            val folderName = MusicFoldersManager.getFolderDisplayName(this, uri.toString())
            Snackbar.make(contentList, getString(R.string.music_folder_added, folderName), Snackbar.LENGTH_LONG).show()

            // Reload library
            loadMusicLibrary()
        }
    }

    // Album cover handling
    private var pendingCoverAlbum: Album? = null

    // Artist customization handling
    private var pendingCustomizationArtist: Artist? = null

    // Delete handling
    private var pendingDeleteTrack: MusicTrack? = null
    private var pendingDeleteAlbum: Album? = null

    // Job pour la vérification POPM auto à l'ouverture d'un album/playlist
    private var popmVerificationJob: Job? = null

    // Gallery picker for album cover
    private val galleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri ->
            pendingCoverAlbum?.let { album ->
                handleSelectedCoverImage(album, imageUri)
            }
        }
    }

    // Gallery picker for artist icon
    private val artistIconGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri ->
            pendingCustomizationArtist?.let { artist ->
                handleSelectedArtistIcon(artist, imageUri)
            }
        }
    }

    // Web search for album cover
    private val webSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imagePath = result.data?.getStringExtra(AlbumArtSearchActivity.RESULT_IMAGE_PATH)
            if (imagePath != null) {
                pendingCoverAlbum?.let { album ->
                    showCoverPreviewDialog(album, imagePath)
                }
            }
        }
    }

    // Web search for artist icon
    private val artistIconWebSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imagePath = result.data?.getStringExtra(ArtistIconSearchActivity.RESULT_IMAGE_PATH)
            if (imagePath != null) {
                pendingCustomizationArtist?.let { artist ->
                    applyArtistIcon(artist, imagePath)
                }
            }
        }
    }

    // Settings activity launcher
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            MusicSettingsActivity.RESULT_TAG_MIGRATION_REQUESTED -> {
                // L'utilisateur a demandé une migration des tags ID3
                performTagMigration()
            }
        }
    }

    // Delete file launcher for Android 11+
    private val deleteFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingDeleteTrack?.let { track ->
                onTrackDeleteSuccess(track)
                pendingDeleteTrack = null
            }
            pendingDeleteAlbum?.let { album ->
                onAlbumDeleteSuccess(album)
                pendingDeleteAlbum = null
            }
        } else {
            pendingDeleteTrack = null
            pendingDeleteAlbum = null
        }
    }

    private lateinit var preferences: MusicPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        setContentView(R.layout.activity_music_player)

        preferences = MusicPreferences.getInstance(this)

        // Initialise le gestionnaire de synchronisation POPM
        MusicPopmSyncManager.init(this)

        // Initialise le gestionnaire de paroles
        com.Atom2Universe.app.music.lyrics.LyricsManager.init(this)

        // Initialise la sync cloud (syncOnStartup est appelé dans onResume)
        lifecycleScope.launch {
            CloudSyncManager.init(this@MusicPlayerActivity)
        }

        initViews()
        setupToolbar()
        setupAdapters()
        setupSwipeGesture()
        setupMiniPlayer()
        setupAlphabetIndex()
        setupBackPressHandler()
        applyPreferences()

        // Sauvegarder l'état pour restauration après chargement
        pendingSavedInstanceState = savedInstanceState

        checkPermissionAndLoad()

        // Gérer l'ouverture via fichier externe
        handleExternalFileIntent(intent)

        // Bouton retour vers MainClickerActivity si lancé depuis le widget
        if (intent.getBooleanExtra(EXTRA_FROM_CLICKER_WIDGET, false)) {
            setupBackToClickerButton()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalFileIntent(intent)

        // Traiter EXTRA_OPEN_FULL_PLAYER ici (et pas seulement dans onStart) pour éviter
        // qu'un intent "en attente" se rejoue lorsque FullPlayerActivity est fermée.
        // Si l'activité reçoit cet extra pendant que FullPlayer est au dessus d'elle,
        // onStart() ne sera appelé qu'au retour de FullPlayer — trop tard, le bug se produit.
        if (intent.getBooleanExtra(EXTRA_OPEN_FULL_PLAYER, false)) {
            intent.removeExtra(EXTRA_OPEN_FULL_PLAYER)
            if (MusicPlaybackHolder.getCurrentTrack() != null) {
                startActivity(Intent(this, FullPlayerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            }
        }
    }

    /**
     * Gère l'ouverture d'un fichier audio externe (depuis explorateur de fichiers)
     */
    private fun handleExternalFileIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return

        val uri = intent.data ?: return

        lifecycleScope.launch {
            val track = createTrackFromUri(uri)
            // Jouer le fichier
            MusicPlaybackHolder.setPlaylist(listOf(track))
            MusicPlaybackHolder.play(this@MusicPlayerActivity, track)
            AudioPlaybackManager.registerPlayback(AudioPlaybackManager.AudioSource.MUSIC) {
                MusicPlaybackHolder.stop(this@MusicPlayerActivity)
            }

            // Ouvrir le full player
            startActivity(Intent(this@MusicPlayerActivity, FullPlayerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }

        // Clear l'intent pour ne pas rejouer si l'activité est recréée
        setIntent(Intent())
    }

    /**
     * Crée un MusicTrack à partir d'une URI externe
     */
    private suspend fun createTrackFromUri(uri: Uri): MusicTrack = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this@MusicPlayerActivity, uri)

                val title = MusicScanner.fixEncoding(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?: getFileNameFromUri(uri)
                        ?: "Unknown"
                )
                val artist = MusicScanner.fixEncoding(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist")
                val album = MusicScanner.fixEncoding(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album")
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                MusicTrack(
                    id = uri.hashCode().toLong(),
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    uri = uri,
                    filePath = null
                )
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            // En cas d'erreur, créer un track minimal
            MusicTrack(
                id = uri.hashCode().toLong(),
                title = getFileNameFromUri(uri) ?: "Unknown",
                artist = "Unknown Artist",
                album = "Unknown Album",
                duration = 0L,
                uri = uri,
                filePath = null
            )
        }
    }

    /**
     * Extrait le nom de fichier depuis une URI
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "content" -> {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) cursor.getString(nameIndex)?.substringBeforeLast('.') else null
                        } else null
                    }
                }
                "file" -> uri.lastPathSegment?.substringBeforeLast('.')
                else -> uri.lastPathSegment?.substringBeforeLast('.')
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        // Recharger les préférences au retour des settings
        applyPreferences()
        // Sync cloud + application des tags en attente au premier plan
        lifecycleScope.launch {
            CloudSyncManager.syncOnStartup()
            PendingTagEditManager.applyPendingEdits(this@MusicPlayerActivity)
        }

        // Rafraîchir les favoris en cas de sync cloud
        refreshFavoritesAfterSync()

        // Update add to playlist button visibility based on edit mode
        updateAddToPlaylistButtonVisibility()
    }

    /**
     * Met à jour la visibilité du bouton "+" sur les tracks en fonction du mode édition
     */
    private fun updateAddToPlaylistButtonVisibility() {
        val showButton = MusicPlaybackHolder.isPlaylistEditModeEnabled &&
                         MusicPlaybackHolder.currentPlaylistId != null
        trackAdapter.setShowAddToPlaylistButton(showButton)
    }

    /**
     * Ajoute un track à la fin de la queue et de la playlist (si mode édition actif)
     */
    private fun addTrackToQueueAndPlaylist(track: MusicTrack) {
        MusicPlaybackHolder.addToQueue(track)

        // If edit mode is enabled, also add to the playlist
        val playlistId = MusicPlaybackHolder.currentPlaylistId
        if (MusicPlaybackHolder.isPlaylistEditModeEnabled && playlistId != null) {
            lifecycleScope.launch {
                MusicPlaylistManager.addTrackToPlaylist(playlistId, track)
            }
            Snackbar.make(contentList, R.string.music_track_added_to_queue_and_playlist, Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(contentList, R.string.music_added_to_queue, Snackbar.LENGTH_SHORT).show()
        }
    }

    /**
     * Ajoute un track en "jouer ensuite" et l'ajoute à la playlist (si mode édition actif)
     */
    private fun playNextAndAddToPlaylist(track: MusicTrack) {
        MusicPlaybackHolder.playNext(track)

        // If edit mode is enabled, also add to the playlist
        val playlistId = MusicPlaybackHolder.currentPlaylistId
        if (MusicPlaybackHolder.isPlaylistEditModeEnabled && playlistId != null) {
            lifecycleScope.launch {
                MusicPlaylistManager.addTrackToPlaylist(playlistId, track)
            }
            Snackbar.make(contentList, R.string.music_track_will_play_next_and_added_to_playlist, Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(contentList, R.string.music_will_play_next, Snackbar.LENGTH_SHORT).show()
        }
    }

    /**
     * Rafraîchit l'affichage des favoris et play counts après une potentielle sync cloud.
     * Appelé dans onResume pour s'assurer que les données sont à jour.
     */
    private fun refreshFavoritesAfterSync() {
        if (!::trackAdapter.isInitialized) return

        lifecycleScope.launch {
            // Recharger le cache des favoris depuis le fichier (au cas où sync en background)
            MusicFavoritesManager.invalidateCache()
            MusicFavoritesManager.loadFavorites(this@MusicPlayerActivity)

            // Recharger le cache des play counts (au cas où sync en background)
            MusicPlayCountManager.refreshCache(this@MusicPlayerActivity)

            // Mettre à jour l'adapteur avec les nouveaux favoris
            val currentTracks = trackAdapter.currentList
            if (currentTracks.isNotEmpty()) {
                updateAdapterFavorites(currentTracks)
                // Forcer le rafraîchissement de l'affichage des play counts
                if (preferences.showPlayCount) {
                    trackAdapter.refreshPlayCountCache()
                    trackAdapter.notifyItemRangeChanged(0, trackAdapter.itemCount)
                }
            }

            // Mettre à jour aussi les favoris des artistes et albums
            updateArtistAdapterFavorites()
            updateAlbumAdapterFavorites()
        }
    }

    override fun onStart() {
        super.onStart()
        MusicPlaybackHolder.addListener(this)
        restorePlaybackState()

        // Si lancé à froid depuis le widget avec demande d'ouverture du full player.
        // Note : le cas "app déjà en cours" est traité dans onNewIntent() pour éviter
        // que l'extra soit rejoué au retour de FullPlayerActivity.
        if (intent?.getBooleanExtra(EXTRA_OPEN_FULL_PLAYER, false) == true) {
            intent?.removeExtra(EXTRA_OPEN_FULL_PLAYER)
            if (MusicPlaybackHolder.getCurrentTrack() != null) {
                startActivity(Intent(this, FullPlayerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        MusicPlaybackHolder.removeListener(this)
    }

    /**
     * Applique les préférences sauvegardées
     */
    private fun applyPreferences() {
        // Appliquer le tri des albums
        MusicLibrary.setAlbumSortOrder(preferences.albumSortOrder)

        // Appliquer l'affichage du nombre d'écoutes
        if (::trackAdapter.isInitialized) {
            trackAdapter.setShowPlayCount(preferences.showPlayCount)
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        breadcrumbScroll = findViewById(R.id.breadcrumb_scroll)
        breadcrumbContainer = findViewById(R.id.breadcrumb_container)
        statsInfo = findViewById(R.id.stats_info)
        loadingIndicator = findViewById(R.id.loading_indicator)
        emptyState = findViewById(R.id.empty_state)
        contentList = findViewById(R.id.content_list)
        alphabetIndex = findViewById(R.id.alphabet_index)
        folderTracksSortBar = findViewById(R.id.folder_tracks_sort_bar)
        btnSortByName = folderTracksSortBar.findViewById(R.id.btn_sort_name)
        btnSortByTrackNumber = folderTracksSortBar.findViewById(R.id.btn_sort_track)
        folderTracksCount = folderTracksSortBar.findViewById(R.id.folder_tracks_count)
        btnSortByName.setOnClickListener { setFolderTrackSort(FolderTrackSort.BY_NAME) }
        btnSortByTrackNumber.setOnClickListener { setFolderTrackSort(FolderTrackSort.BY_TRACK_NUMBER) }

        miniPlayer = findViewById(R.id.mini_player)
        miniProgress = findViewById(R.id.mini_progress)
        miniAlbumArt = findViewById(R.id.mini_album_art)
        miniTitle = findViewById(R.id.mini_title)
        miniArtist = findViewById(R.id.mini_artist)
        miniBtnPrev = findViewById(R.id.mini_btn_prev)
        miniBtnPlayPause = findViewById(R.id.mini_btn_play_pause)
        miniBtnNext = findViewById(R.id.mini_btn_next)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener { handleBackNavigation() }
        toolbar.setOnMenuItemClickListener { menuItem ->
            handleMenuItemClick(menuItem)
        }

        // Setup long click listener on display mode button for ROOT visibility options
        toolbar.post {
            setupDisplayModeLongClick()
        }
    }

    private fun setupDisplayModeLongClick() {
        val displayModeItem = toolbar.menu.findItem(R.id.action_artist_display_mode) ?: return
        val actionView = displayModeItem.actionView ?: return

        // Setup click listener (replaces menu item click for this action)
        actionView.setOnClickListener {
            when (currentLevel) {
                NavigationLevel.ROOT -> cycleRootDisplayMode()
                NavigationLevel.ARTISTS,
                NavigationLevel.ALBUM_ARTISTS,
                NavigationLevel.FAVORITE_ARTISTS,
                NavigationLevel.NAVIDROME_ROOT -> cycleArtistDisplayMode()
                NavigationLevel.ALL_ALBUMS,
                NavigationLevel.ALBUMS,
                NavigationLevel.FAVORITE_ALBUMS,
                NavigationLevel.NAVIDROME_ARTIST -> cycleAlbumDisplayMode()
                NavigationLevel.FOLDERS -> cycleFolderDisplayMode()
                NavigationLevel.TRACKS,
                NavigationLevel.PLAYLIST_TRACKS,
                NavigationLevel.FOLDER_TRACKS,
                NavigationLevel.SEARCH,
                NavigationLevel.NAVIDROME_ALBUM -> cycleTrackDisplayMode()
                NavigationLevel.PLAYLISTS -> {}
            }
        }

        // Setup long click listener for ROOT visibility options
        actionView.setOnLongClickListener {
            if (currentLevel == NavigationLevel.ROOT) {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showRootVisibilityDialog()
                true
            } else {
                false
            }
        }
    }

    private fun handleMenuItemClick(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_create_playlist -> {
                showCreatePlaylistDialog()
                true
            }
            R.id.action_refresh_fast -> {
                loadMusicLibrary(deepScan = false, forceRescan = true)
                true
            }
            R.id.action_refresh_deep -> {
                loadMusicLibrary(deepScan = true, forceRescan = true)
                true
            }
            R.id.action_refresh_complete -> {
                loadMusicLibrary(deepScan = true, forceRescan = true, withPopmScan = true)
                true
            }
            R.id.action_manage_folders -> {
                showManageFoldersDialog()
                true
            }
            R.id.action_settings -> {
                settingsLauncher.launch(Intent(this, MusicSettingsActivity::class.java))
                true
            }
            R.id.action_artist_display_mode -> {
                // Handle display mode based on current navigation level
                when (currentLevel) {
                    NavigationLevel.ROOT -> cycleRootDisplayMode()
                    NavigationLevel.ARTISTS,
                    NavigationLevel.ALBUM_ARTISTS,
                    NavigationLevel.FAVORITE_ARTISTS -> cycleArtistDisplayMode()
                    NavigationLevel.ALL_ALBUMS,
                    NavigationLevel.ALBUMS,
                    NavigationLevel.FAVORITE_ALBUMS -> cycleAlbumDisplayMode()
                    NavigationLevel.FOLDERS -> cycleFolderDisplayMode()
                    NavigationLevel.TRACKS,
                    NavigationLevel.PLAYLIST_TRACKS,
                    NavigationLevel.FOLDER_TRACKS,
                    NavigationLevel.SEARCH,
                    NavigationLevel.NAVIDROME_ALBUM -> cycleTrackDisplayMode()
                    NavigationLevel.NAVIDROME_ROOT -> cycleArtistDisplayMode()
                    NavigationLevel.NAVIDROME_ARTIST -> cycleAlbumDisplayMode()
                    NavigationLevel.PLAYLISTS -> {} // Playlists n'ont pas de mode d'affichage pour l'instant
                }
                true
            }
            else -> false
        }
    }

    private fun openFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    private fun showManageFoldersDialog() {
        MusicFoldersManager.init(this)
        val customFolders = MusicFoldersManager.getCustomFolderUris()

        if (customFolders.isEmpty()) {
            // Aucun dossier : afficher un dialogue simplifié pour ajouter
            AlertDialog.Builder(this)
                .setTitle(R.string.music_manage_folders)
                .setMessage(R.string.music_no_custom_folders)
                .setPositiveButton(R.string.music_add_folder) { _, _ ->
                    openFolderPicker()
                }
                .setNegativeButton(R.string.music_cancel, null)
                .show()
            return
        }

        // Build folder names list
        val folderNames = customFolders.map { uri ->
            MusicFoldersManager.getFolderDisplayName(this, uri)
        }.toTypedArray()

        val selectedItems = BooleanArray(folderNames.size) { false }

        AlertDialog.Builder(this)
            .setTitle(R.string.music_manage_folders)
            .setMultiChoiceItems(folderNames, selectedItems) { _, which, isChecked ->
                selectedItems[which] = isChecked
            }
            .setPositiveButton(R.string.music_remove_selected) { _, _ ->
                // Remove selected folders
                var removedCount = 0
                for (i in customFolders.indices.reversed()) {
                    if (selectedItems[i]) {
                        MusicFoldersManager.removeCustomFolder(customFolders[i])
                        removedCount++
                    }
                }
                if (removedCount > 0) {
                    Snackbar.make(
                        contentList,
                        resources.getQuantityString(R.plurals.music_folders_removed, removedCount, removedCount),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    loadMusicLibrary(forceRescan = true)
                }
            }
            .setNegativeButton(R.string.music_cancel, null)
            .setNeutralButton(R.string.music_add_new) { _, _ ->
                openFolderPicker()
            }
            .show()
    }

    private fun setupAdapters() {
        rootOptionAdapter = RootOptionAdapter { option ->
            when (option.id) {
                RootOption.OPTION_ARTISTS -> navigateToArtists()
                RootOption.OPTION_ALBUM_ARTISTS -> navigateToAlbumArtists()
                RootOption.OPTION_ALL_ALBUMS -> navigateToAllAlbums()
                RootOption.OPTION_FOLDERS -> navigateToFolders()
                RootOption.OPTION_SEARCH -> navigateToSearch()
                RootOption.OPTION_PLAYLISTS -> navigateToPlaylists()
                RootOption.OPTION_FAVORITE_ARTISTS -> navigateToFavoriteArtists()
                RootOption.OPTION_FAVORITE_ALBUMS -> navigateToFavoriteAlbums()
                RootOption.OPTION_NAVIDROME -> navigateToNavidromeRoot()
            }
        }

        artistAdapter = ArtistAdapter(
            onArtistClick = { artist -> navigateToArtist(artist) },
            onArtistLongClick = { artist, view -> showArtistContextMenu(artist, view) },
            onPlayClick = { artist -> playArtist(artist, shuffle = false) },
            onShuffleClick = { artist -> playArtist(artist, shuffle = true) }
        )

        albumAdapter = AlbumAdapter(
            onAlbumClick = { album -> navigateToAlbum(album) },
            onAlbumLongClick = { album, view -> showAlbumContextMenu(album, view) },
            onPlayClick = { album -> playAlbum(album, shuffle = false) },
            onShuffleClick = { album -> playAlbum(album, shuffle = true) },
            onAllTracksClick = { tracks -> navigateToArtistAllTracks(tracks) },
            onAllTracksPlayClick = { tracks -> playTracks(tracks, shuffle = false) },
            onAllTracksShuffleClick = { tracks -> playTracks(tracks, shuffle = true) }
        )

        folderAdapter = FolderAdapter(
            onFolderClick = { folder -> navigateToFolder(folder) },
            onFolderLongClick = { folder, view -> showFolderContextMenu(folder, view) },
            onPlayClick = { folder -> playFolder(folder, shuffle = false) },
            onShuffleClick = { folder -> playFolder(folder, shuffle = true) }
        )

        folderContentAdapter = FolderContentAdapter(
            onFolderClick = { folder -> navigateToFolder(folder) },
            onFolderLongClick = { folder, view -> showFolderContextMenu(folder, view) },
            onFolderPlayClick = { folder -> playFolder(folder, shuffle = false) },
            onFolderShuffleClick = { folder -> playFolder(folder, shuffle = true) },
            onTrackClick = { track -> playTrack(track) },
            onTrackLongClick = { track, view -> showTrackContextMenu(track, view) }
        )

        trackAdapter = MusicTrackAdapter(
            onTrackClick = { track -> playTrack(track) },
            onFavoriteClick = { track -> toggleFavorite(track) },
            onTrackLongClick = { track, view -> showTrackContextMenu(track, view) },
            onAddToPlaylistClick = { track -> addTrackToQueueAndPlaylist(track) },
            onAddToPlaylistLongClick = { track -> playNextAndAddToPlaylist(track) }
        )

        playlistAdapter = MusicPlaylistAdapter(
            onPlaylistClick = { playlist -> navigateToPlaylistTracks(playlist) },
            onPlaylistLongClick = { playlist, view -> showPlaylistContextMenu(playlist, view) }
        )
    }

    private fun setupRootDragHelper() {
        if (rootItemTouchHelper != null) return

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (currentLevel != NavigationLevel.ROOT) return false

                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                    return false
                }

                Collections.swap(rootOptions, fromPosition, toPosition)
                rootOptionAdapter.submitList(rootOptions.toList())
                return true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveRootOptionOrder()
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe actions.
            }
        }

        rootItemTouchHelper = ItemTouchHelper(callback)
    }

    private fun updateRootDragHelper(isRootLevel: Boolean) {
        if (isRootLevel) {
            setupRootDragHelper()
            rootItemTouchHelper?.attachToRecyclerView(contentList)
        } else {
            rootItemTouchHelper?.attachToRecyclerView(null)
        }
    }

    private fun saveRootOptionOrder() {
        preferences.rootOptionOrder = rootOptions.map { it.id }
    }

    private fun reorderRootOptions(options: List<RootOption>): List<RootOption> {
        val preferredOrder = preferences.rootOptionOrder
        if (preferredOrder.isEmpty()) return options

        val optionMap = options.associateBy { it.id }
        val ordered = mutableListOf<RootOption>()
        val seen = mutableSetOf<String>()

        for (id in preferredOrder) {
            optionMap[id]?.let {
                ordered.add(it)
                seen.add(id)
            }
        }

        for (option in options) {
            if (option.id !in seen) {
                ordered.add(option)
            }
        }

        return ordered
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGesture() {
        val density = resources.displayMetrics.density

        // Swipe thresholds (in dp, converted to pixels)
        val edgeZoneWidth = 50 * density        // Swipe must start within 50dp from left edge
        val minSwipeDistance = 80 * density     // Minimum 80dp horizontal distance to trigger
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop

        // Scale gesture detector for pinch-to-zoom in tile mode
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var scaleFactor = 1f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaleFactor = 1f
                return isInArtistTileMode() || isInAlbumTileMode()
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val inArtistTiles = isInArtistTileMode()
                val inAlbumTiles = isInAlbumTileMode()
                if (!inArtistTiles && !inAlbumTiles) return false

                scaleFactor *= detector.scaleFactor

                val currentColumns = if (inArtistTiles) currentArtistTileColumns else currentAlbumTileColumns

                // Pinch in (zoom out) = more columns
                // Pinch out (zoom in) = fewer columns
                if (scaleFactor < 0.8f) {
                    val newColumns = (currentColumns + 1).coerceIn(
                        MusicPreferences.MIN_TILE_COLUMNS,
                        MusicPreferences.MAX_TILE_COLUMNS
                    )
                    if (newColumns != currentColumns) {
                        if (inArtistTiles) {
                            currentArtistTileColumns = newColumns
                            preferences.artistTileColumns = newColumns
                        } else {
                            currentAlbumTileColumns = newColumns
                            preferences.albumTileColumns = newColumns
                        }
                        updateGridLayoutColumns(newColumns)
                    }
                    scaleFactor = 1f
                } else if (scaleFactor > 1.25f) {
                    val newColumns = (currentColumns - 1).coerceIn(
                        MusicPreferences.MIN_TILE_COLUMNS,
                        MusicPreferences.MAX_TILE_COLUMNS
                    )
                    if (newColumns != currentColumns) {
                        if (inArtistTiles) {
                            currentArtistTileColumns = newColumns
                            preferences.artistTileColumns = newColumns
                        } else {
                            currentAlbumTileColumns = newColumns
                            preferences.albumTileColumns = newColumns
                        }
                        updateGridLayoutColumns(newColumns)
                    }
                    scaleFactor = 1f
                }
                return true
            }
        })

        // Edge swipe gesture using OnItemTouchListener to intercept before RecyclerView scrolls
        contentList.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            private var startX = 0f
            private var startY = 0f
            private var isTrackingEdgeSwipe = false
            private var hasDecidedDirection = false

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                // Always let scale gesture detector see the event
                scaleGestureDetector.onTouchEvent(e)

                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        hasDecidedDirection = false
                        // Start tracking if touch is in edge zone and not at ROOT level
                        isTrackingEdgeSwipe = startX <= edgeZoneWidth && currentLevel != NavigationLevel.ROOT
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isTrackingEdgeSwipe) return false
                        if (hasDecidedDirection) return isTrackingEdgeSwipe

                        val diffX = e.x - startX
                        val diffY = e.y - startY
                        val absDiffX = kotlin.math.abs(diffX)
                        val absDiffY = kotlin.math.abs(diffY)

                        // Wait until we have enough movement to decide direction
                        if (absDiffX > touchSlop || absDiffY > touchSlop) {
                            hasDecidedDirection = true
                            // Intercept if moving horizontally to the right (swipe back gesture)
                            // Horizontal must be > 1.5x vertical to be considered horizontal
                            isTrackingEdgeSwipe = diffX > 0 && absDiffX > absDiffY * 1.5f
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isTrackingEdgeSwipe = false
                        hasDecidedDirection = false
                    }
                }
                return isTrackingEdgeSwipe && hasDecidedDirection
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        // Visual feedback could be added here (e.g., edge indicator)
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = e.x - startX
                        val diffY = e.y - startY
                        val absDiffX = kotlin.math.abs(diffX)
                        val absDiffY = kotlin.math.abs(diffY)

                        // Check if swipe is valid: enough horizontal distance and mostly horizontal
                        if (diffX >= minSwipeDistance && absDiffX > absDiffY * 2f) {
                            handleBackNavigation()
                        }

                        isTrackingEdgeSwipe = false
                        hasDecidedDirection = false
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isTrackingEdgeSwipe = false
                        hasDecidedDirection = false
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                // Reset tracking if child requests to disallow interception
                if (disallowIntercept) {
                    isTrackingEdgeSwipe = false
                    hasDecidedDirection = false
                }
            }
        })
    }

    /**
     * Verifie si on est en mode tuiles artistes
     */
    private fun isInArtistTileMode(): Boolean {
        return (currentLevel == NavigationLevel.ARTISTS ||
                currentLevel == NavigationLevel.ALBUM_ARTISTS ||
                currentLevel == NavigationLevel.FAVORITE_ARTISTS ||
                currentLevel == NavigationLevel.NAVIDROME_ROOT) &&
                preferences.artistDisplayMode == ArtistDisplayMode.TILES
    }

    /**
     * Verifie si on est en mode tuiles albums
     */
    private fun isInAlbumTileMode(): Boolean {
        return (currentLevel == NavigationLevel.ALL_ALBUMS ||
                currentLevel == NavigationLevel.ALBUMS ||
                currentLevel == NavigationLevel.FAVORITE_ALBUMS) &&
                preferences.albumDisplayMode == AlbumDisplayMode.TILES
    }

    /**
     * Met a jour le nombre de colonnes du GridLayoutManager
     */
    private fun updateGridLayoutColumns(columns: Int) {
        val layoutManager = contentList.layoutManager
        if (layoutManager is GridLayoutManager) {
            layoutManager.spanCount = columns
            // Forcer le recalcul des layouts pour les tuiles carrees
            contentList.post {
                contentList.invalidateItemDecorations()
                contentList.requestLayout()
            }
        }
    }

    /**
     * Configure le layout manager selon le mode d'affichage des artistes
     */
    private fun setupLayoutManagerForArtists() {
        val displayMode = preferences.artistDisplayMode
        artistAdapter.displayMode = displayMode

        when (displayMode) {
            ArtistDisplayMode.TILES -> {
                currentArtistTileColumns = preferences.artistTileColumns
                contentList.layoutManager = GridLayoutManager(this, currentArtistTileColumns)
            }
            else -> {
                contentList.layoutManager = LinearLayoutManager(this)
            }
        }
    }

    /**
     * Configure le layout manager selon le mode d'affichage des albums
     */
    private fun setupLayoutManagerForAlbums() {
        val displayMode = preferences.albumDisplayMode
        albumAdapter.displayMode = displayMode

        when (displayMode) {
            AlbumDisplayMode.TILES -> {
                currentAlbumTileColumns = preferences.albumTileColumns
                contentList.layoutManager = GridLayoutManager(this, currentAlbumTileColumns)
            }
            else -> {
                contentList.layoutManager = LinearLayoutManager(this)
            }
        }
    }

    /**
     * Reinitialise le layout manager en mode liste standard
     * Note: GridLayoutManager herite de LinearLayoutManager, donc on doit verifier explicitement
     */
    private fun resetToLinearLayoutManager() {
        val currentLayoutManager = contentList.layoutManager
        if (currentLayoutManager is GridLayoutManager || currentLayoutManager !is LinearLayoutManager) {
            contentList.layoutManager = LinearLayoutManager(this)
        }
    }

    /**
     * Cycle through artist display modes: LIST -> COMPACT -> TILES -> LIST
     */
    private fun cycleArtistDisplayMode() {
        val currentMode = preferences.artistDisplayMode
        val newMode = when (currentMode) {
            ArtistDisplayMode.LIST -> ArtistDisplayMode.COMPACT
            ArtistDisplayMode.COMPACT -> ArtistDisplayMode.TILES
            ArtistDisplayMode.TILES -> ArtistDisplayMode.LIST
        }
        preferences.artistDisplayMode = newMode

        // Update adapter and layout manager
        setupLayoutManagerForArtists()

        // Update menu icon
        updateDisplayModeIcon()

        // Show feedback
        val modeNameRes = when (newMode) {
            ArtistDisplayMode.LIST -> R.string.music_display_mode_list
            ArtistDisplayMode.COMPACT -> R.string.music_display_mode_compact
            ArtistDisplayMode.TILES -> R.string.music_display_mode_tiles
        }
        Snackbar.make(contentList, modeNameRes, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Cycle through album display modes: LIST -> COMPACT -> TILES -> LIST
     */
    private fun cycleAlbumDisplayMode() {
        val currentMode = preferences.albumDisplayMode
        val newMode = when (currentMode) {
            AlbumDisplayMode.LIST -> AlbumDisplayMode.COMPACT
            AlbumDisplayMode.COMPACT -> AlbumDisplayMode.TILES
            AlbumDisplayMode.TILES -> AlbumDisplayMode.LIST
        }
        preferences.albumDisplayMode = newMode

        // Update adapter and layout manager
        setupLayoutManagerForAlbums()

        // Update menu icon
        updateDisplayModeIcon()

        // Show feedback
        val modeNameRes = when (newMode) {
            AlbumDisplayMode.LIST -> R.string.music_display_mode_list
            AlbumDisplayMode.COMPACT -> R.string.music_display_mode_compact
            AlbumDisplayMode.TILES -> R.string.music_display_mode_tiles
        }
        Snackbar.make(contentList, modeNameRes, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Cycle through folder display modes: LIST -> COMPACT -> LIST
     */
    private fun cycleFolderDisplayMode() {
        val currentMode = preferences.folderDisplayMode
        val newMode = when (currentMode) {
            FolderDisplayMode.LIST -> FolderDisplayMode.COMPACT
            FolderDisplayMode.COMPACT -> FolderDisplayMode.LIST
        }
        preferences.folderDisplayMode = newMode

        // Update adapter and layout manager
        folderAdapter.displayMode = newMode
        setupLayoutManagerForFolders()

        // Update menu icon
        updateDisplayModeIcon()

        // Show feedback
        val modeNameRes = when (newMode) {
            FolderDisplayMode.LIST -> R.string.music_display_mode_list
            FolderDisplayMode.COMPACT -> R.string.music_display_mode_compact
        }
        Snackbar.make(contentList, modeNameRes, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Cycle through track display modes: LIST -> COMPACT -> LIST
     */
    private fun cycleTrackDisplayMode() {
        val currentMode = preferences.trackDisplayMode
        val newMode = when (currentMode) {
            TrackDisplayMode.LIST -> TrackDisplayMode.COMPACT
            TrackDisplayMode.COMPACT -> TrackDisplayMode.LIST
        }
        preferences.trackDisplayMode = newMode

        // Update adapter
        trackAdapter.displayMode = newMode

        // Update menu icon
        updateDisplayModeIcon()

        // Show feedback
        val modeNameRes = when (newMode) {
            TrackDisplayMode.LIST -> R.string.music_display_mode_list
            TrackDisplayMode.COMPACT -> R.string.music_display_mode_compact
        }
        Snackbar.make(contentList, modeNameRes, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Cycle through root display modes: LIST -> COMPACT -> LIST
     */
    private fun cycleRootDisplayMode() {
        val currentMode = preferences.rootDisplayMode
        val newMode = when (currentMode) {
            RootDisplayMode.LIST -> RootDisplayMode.COMPACT
            RootDisplayMode.COMPACT -> RootDisplayMode.LIST
        }
        preferences.rootDisplayMode = newMode

        // Update adapter
        rootOptionAdapter.displayMode = newMode

        // Update menu icon
        updateDisplayModeIcon()

        // Show feedback
        val modeNameRes = when (newMode) {
            RootDisplayMode.LIST -> R.string.music_display_mode_list
            RootDisplayMode.COMPACT -> R.string.music_display_mode_compact
        }
        Snackbar.make(contentList, modeNameRes, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Show dialog to toggle visibility of ROOT options.
     * Long press on display mode button triggers this.
     */
    private fun showRootVisibilityDialog() {
        // List all possible ROOT options with their titles
        val allOptions = listOf(
            RootOption.OPTION_ARTISTS to getString(R.string.music_browse_artists),
            RootOption.OPTION_ALBUM_ARTISTS to getString(R.string.music_browse_album_artists),
            RootOption.OPTION_ALL_ALBUMS to getString(R.string.music_browse_all_albums),
            RootOption.OPTION_FOLDERS to getString(R.string.music_browse_folders),
            RootOption.OPTION_SEARCH to getString(R.string.music_browse_search),
            RootOption.OPTION_PLAYLISTS to getString(R.string.music_browse_playlists)
        )

        val optionIds = allOptions.map { it.first }.toTypedArray()
        val optionNames = allOptions.map { it.second }.toTypedArray()
        val checkedItems = optionIds.map { preferences.isRootOptionVisible(it) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.music_root_visibility_title)
            .setMultiChoiceItems(optionNames, checkedItems) { _, which, isChecked ->
                preferences.setRootOptionVisible(optionIds[which], isChecked)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Refresh ROOT display
                navigateToRoot()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Update the display mode menu icon based on current navigation level
     */
    private fun updateDisplayModeIcon() {
        val menu = toolbar.menu
        val displayModeItem = menu.findItem(R.id.action_artist_display_mode) ?: return
        val actionButton = displayModeItem.actionView as? ImageButton

        val isRootLevel = currentLevel == NavigationLevel.ROOT

        val isArtistLevel = currentLevel == NavigationLevel.ARTISTS ||
                currentLevel == NavigationLevel.ALBUM_ARTISTS ||
                currentLevel == NavigationLevel.FAVORITE_ARTISTS

        val isAlbumLevel = currentLevel == NavigationLevel.ALL_ALBUMS ||
                currentLevel == NavigationLevel.ALBUMS ||
                currentLevel == NavigationLevel.FAVORITE_ALBUMS

        val isFolderLevel = currentLevel == NavigationLevel.FOLDERS

        val isTrackLevel = currentLevel == NavigationLevel.TRACKS ||
                currentLevel == NavigationLevel.PLAYLIST_TRACKS ||
                currentLevel == NavigationLevel.SEARCH ||
                currentLevel == NavigationLevel.NAVIDROME_ALBUM

        val isNavidromeArtistLevel = currentLevel == NavigationLevel.NAVIDROME_ROOT
        val isNavidromeAlbumLevel = currentLevel == NavigationLevel.NAVIDROME_ARTIST

        // Visible pour tous les niveaux sauf PLAYLISTS
        displayModeItem.isVisible = isRootLevel || isArtistLevel || isNavidromeArtistLevel ||
                isAlbumLevel || isNavidromeAlbumLevel || isFolderLevel || isTrackLevel

        val iconRes = when {
            isRootLevel -> when (preferences.rootDisplayMode) {
                RootDisplayMode.LIST -> R.drawable.ic_view_list
                RootDisplayMode.COMPACT -> R.drawable.ic_view_compact
            }
            isArtistLevel || isNavidromeArtistLevel -> when (preferences.artistDisplayMode) {
                ArtistDisplayMode.LIST -> R.drawable.ic_view_list
                ArtistDisplayMode.COMPACT -> R.drawable.ic_view_compact
                ArtistDisplayMode.TILES -> R.drawable.ic_view_grid
            }
            isAlbumLevel || isNavidromeAlbumLevel -> when (preferences.albumDisplayMode) {
                AlbumDisplayMode.LIST -> R.drawable.ic_view_list
                AlbumDisplayMode.COMPACT -> R.drawable.ic_view_compact
                AlbumDisplayMode.TILES -> R.drawable.ic_view_grid
            }
            isFolderLevel -> when (preferences.folderDisplayMode) {
                FolderDisplayMode.LIST -> R.drawable.ic_view_list
                FolderDisplayMode.COMPACT -> R.drawable.ic_view_compact
            }
            isTrackLevel -> when (preferences.trackDisplayMode) {
                TrackDisplayMode.LIST -> R.drawable.ic_view_list
                TrackDisplayMode.COMPACT -> R.drawable.ic_view_compact
            }
            else -> R.drawable.ic_view_list
        }

        displayModeItem.setIcon(iconRes)
        actionButton?.setImageResource(iconRes)
    }


    private fun toggleFavorite(track: MusicTrack) {
        lifecycleScope.launch {
            val newState = MusicFavoritesManager.toggleFavorite(track)
            trackAdapter.updateFavorite(track.id, newState)

            val messageRes = if (newState) {
                R.string.music_added_to_favorites
            } else {
                R.string.music_removed_from_favorites
            }
            Snackbar.make(contentList, messageRes, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showTrackContextMenu(track: MusicTrack, anchorView: View): Boolean {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_track_context, popup.menu)

        // If we're in a custom playlist, change "Add to playlist" to "Remove from playlist"
        val isInCustomPlaylist = currentLevel == NavigationLevel.PLAYLIST_TRACKS &&
            currentPlaylist != null &&
            !currentPlaylist!!.isSystemPlaylist

        popup.menu.findItem(R.id.action_add_to_playlist)?.let { menuItem ->
            if (isInCustomPlaylist) {
                menuItem.title = getString(R.string.music_remove_from_playlist)
            }
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add_to_playlist -> {
                    if (isInCustomPlaylist) {
                        // Remove from current playlist
                        removeTrackFromCurrentPlaylist(track)
                    } else {
                        // Show dialog to add to a playlist
                        showAddToPlaylistDialog(track)
                    }
                    true
                }
                R.id.action_play_next -> {
                    MusicPlaybackHolder.playNext(track)
                    Snackbar.make(contentList, R.string.music_will_play_next, Snackbar.LENGTH_SHORT).show()
                    true
                }
                R.id.action_add_to_queue -> {
                    MusicPlaybackHolder.addToQueue(track)
                    Snackbar.make(contentList, R.string.music_added_to_queue, Snackbar.LENGTH_SHORT).show()
                    true
                }
                R.id.action_edit_tags -> {
                    showEditTrackTagsDialog(track)
                    true
                }
                R.id.action_track_info -> {
                    showTrackInfo(track)
                    true
                }
                R.id.action_delete_track -> {
                    showDeleteTrackConfirmation(track)
                    true
                }
                else -> false
            }
        }

        popup.show()
        return true
    }

    private fun showAlbumContextMenu(album: Album, anchorView: View): Boolean {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_album_context, popup.menu)

        // Update favorite menu item text based on current state
        val isFavorite = AlbumFavoritesManager.isFavorite(album.artist, album.name)
        popup.menu.findItem(R.id.action_toggle_album_favorite)?.title = getString(
            if (isFavorite) R.string.music_remove_album_from_favorites
            else R.string.music_add_album_to_favorites
        )

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_play_album -> {
                    requestMusicPlayback {
                        MusicPlaybackHolder.setPlaylist(album.tracks)
                        MusicPlaybackHolder.playAtIndex(this, 0)
                    }
                    true
                }
                R.id.action_shuffle_album -> {
                    requestMusicPlayback {
                        MusicPlaybackHolder.setPlaylist(album.tracks)
                        if (!MusicPlaybackHolder.shuffleEnabled) {
                            MusicPlaybackHolder.toggleShuffle()
                        }
                        MusicPlaybackHolder.playAtIndex(this, (0 until album.tracks.size).random())
                    }
                    true
                }
                R.id.action_toggle_album_favorite -> {
                    toggleAlbumFavorite(album)
                    true
                }
                R.id.action_add_cover -> {
                    showAddCoverDialog(album)
                    true
                }
                R.id.action_edit_album_tags -> {
                    showEditAlbumTagsDialog(album)
                    true
                }
                R.id.action_delete_album -> {
                    showDeleteAlbumConfirmation(album)
                    true
                }
                else -> false
            }
        }

        popup.show()
        return true
    }

    private fun showFolderContextMenu(folder: Folder, anchorView: View): Boolean {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_folder_context, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_play_folder -> {
                    playFolder(folder, shuffle = false)
                    true
                }
                R.id.action_shuffle_folder -> {
                    playFolder(folder, shuffle = true)
                    true
                }
                R.id.action_edit_folder_tags -> {
                    showEditFolderTagsDialog(folder)
                    true
                }
                else -> false
            }
        }

        popup.show()
        return true
    }

    // ========== ARTIST CUSTOMIZATION ==========

    private fun showArtistContextMenu(artist: Artist, anchorView: View): Boolean {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_artist_context, popup.menu)

        // Update favorite menu item text based on current state
        val isFavorite = ArtistCustomizationManager.isArtistFavorite(artist.name)
        popup.menu.findItem(R.id.action_toggle_artist_favorite)?.title = getString(
            if (isFavorite) R.string.music_remove_artist_from_favorites
            else R.string.music_add_artist_to_favorites
        )

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_change_artist_icon -> {
                    showChangeArtistIconDialog(artist)
                    true
                }
                R.id.action_change_artist_color -> {
                    showChangeArtistColorDialog(artist)
                    true
                }
                R.id.action_toggle_artist_favorite -> {
                    toggleArtistFavorite(artist)
                    true
                }
                R.id.action_play_artist -> {
                    playAllArtistTracks(artist)
                    true
                }
                R.id.action_shuffle_artist -> {
                    shuffleAllArtistTracks(artist)
                    true
                }
                else -> false
            }
        }

        popup.show()
        return true
    }

    private fun playAllArtistTracks(artist: Artist) {
        val tracks = buildArtistPlaylist(artist)
        if (tracks.isEmpty()) {
            Snackbar.make(contentList, R.string.music_no_tracks, Snackbar.LENGTH_SHORT).show()
            return
        }

        requestMusicPlayback {
            MusicPlaybackHolder.setPlaylist(tracks)
            if (MusicPlaybackHolder.shuffleEnabled) {
                MusicPlaybackHolder.toggleShuffle()
            }
            MusicPlaybackHolder.playAtIndex(this, 0)
        }
    }

    private fun shuffleAllArtistTracks(artist: Artist) {
        val tracks = buildArtistPlaylist(artist)
        if (tracks.isEmpty()) {
            Snackbar.make(contentList, R.string.music_no_tracks, Snackbar.LENGTH_SHORT).show()
            return
        }

        requestMusicPlayback {
            MusicPlaybackHolder.setPlaylist(tracks)
            if (!MusicPlaybackHolder.shuffleEnabled) {
                MusicPlaybackHolder.toggleShuffle()
            }
            MusicPlaybackHolder.playAtIndex(this, (0 until tracks.size).random())
        }
    }

    private fun buildArtistPlaylist(artist: Artist): List<MusicTrack> {
        val resolvedArtist = if (isInAlbumArtistMode) {
            MusicLibrary.getAlbumArtist(artist.name)
        } else {
            MusicLibrary.getArtist(artist.name)
        } ?: artist

        return resolvedArtist.albums.flatMap { it.tracks }
    }

    private fun showChangeArtistIconDialog(artist: Artist) {
        pendingCustomizationArtist = artist

        val options = arrayOf(
            getString(R.string.music_icon_from_gallery),
            getString(R.string.music_icon_from_web),
            getString(R.string.music_remove_custom_icon)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.music_change_artist_icon)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> artistIconGalleryLauncher.launch("image/*")
                    1 -> {
                        val intent = Intent(this, ArtistIconSearchActivity::class.java).apply {
                            putExtra(ArtistIconSearchActivity.EXTRA_ARTIST_NAME, artist.name)
                        }
                        artistIconWebSearchLauncher.launch(intent)
                    }
                    2 -> removeArtistIcon(artist)
                }
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun handleSelectedArtistIcon(artist: Artist, imageUri: Uri) {
        try {
            // Copy image to a temp file for processing
            val inputStream = contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Snackbar.make(contentList, R.string.music_cover_error, Snackbar.LENGTH_SHORT).show()
                return
            }

            val tempFile = File(cacheDir, "temp_artist_icon_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            // Compress the image (max 512px, max 100Ko)
            lifecycleScope.launch {
                val compressedFile = File(cacheDir, "compressed_artist_icon_${System.currentTimeMillis()}.jpg")
                val success = ImageCompressor.compressArtistIcon(tempFile.absolutePath, compressedFile.absolutePath)
                tempFile.delete()

                runOnUiThread {
                    if (success && compressedFile.exists()) {
                        applyArtistIcon(artist, compressedFile.absolutePath)
                    } else {
                        Snackbar.make(contentList, R.string.music_cover_error, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (_: Exception) {
            Snackbar.make(contentList, R.string.music_cover_error, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun applyArtistIcon(artist: Artist, imagePath: String) {
        // Copy to permanent location
        val permanentFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "artist_${artist.name.hashCode()}.jpg")
        try {
            File(imagePath).copyTo(permanentFile, overwrite = true)

            lifecycleScope.launch {
                ArtistCustomizationManager.setArtistIcon(artist.name, permanentFile.absolutePath)
                ArtistImageCache.invalidateArtist(artist.name)
                runOnUiThread {
                    artistAdapter.notifyArtistChanged(artist.name)
                    Snackbar.make(contentList, R.string.music_artist_icon_updated, Snackbar.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            Snackbar.make(contentList, R.string.music_cover_error, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun removeArtistIcon(artist: Artist) {
        lifecycleScope.launch {
            ArtistCustomizationManager.removeArtistIcon(artist.name)
            ArtistImageCache.invalidateArtist(artist.name)
            runOnUiThread {
                artistAdapter.notifyArtistChanged(artist.name)
                Snackbar.make(contentList, R.string.music_artist_icon_removed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showChangeArtistColorDialog(artist: Artist) {
        val currentColor = ArtistCustomizationManager.getArtistColor(artist.name)

        NoteColorPickerDialog(
            context = this,
            currentColorHex = currentColor,
            currentTextColorMode = "auto"
        ) { colorHex, _ ->
            applyArtistColor(artist, colorHex)
        }.show()
    }

    private fun applyArtistColor(artist: Artist, color: String?) {
        lifecycleScope.launch {
            ArtistCustomizationManager.setArtistColor(artist.name, color)
            runOnUiThread {
                artistAdapter.notifyArtistChanged(artist.name)
                Snackbar.make(contentList, R.string.music_artist_color_updated, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleArtistFavorite(artist: Artist) {
        lifecycleScope.launch {
            val newState = ArtistCustomizationManager.toggleArtistFavorite(artist.name)
            runOnUiThread {
                // Update artist adapter to show/hide favorite icon
                updateArtistAdapterFavorites()
                artistAdapter.notifyArtistChanged(artist.name)

                val messageRes = if (newState) {
                    R.string.music_artist_added_to_favorites
                } else {
                    R.string.music_artist_removed_from_favorites
                }
                Snackbar.make(contentList, messageRes, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateArtistAdapterFavorites() {
        val favoriteArtistNames = ArtistCustomizationManager.getFavoriteArtistNames().toSet()
        artistAdapter.setFavoriteArtists(favoriteArtistNames)
    }

    // ========== ALBUM FAVORITES ==========

    private fun updateAlbumAdapterFavorites() {
        val favorites = AlbumFavoritesManager.getFavorites().toSet()
        albumAdapter.setFavoriteAlbums(favorites)
    }

    private fun toggleAlbumFavorite(album: Album) {
        lifecycleScope.launch {
            val newState = AlbumFavoritesManager.toggleFavorite(album.artist, album.name)
            updateAlbumAdapterFavorites()
            runOnUiThread {
                val messageRes = if (newState) {
                    R.string.music_album_added_to_favorites
                } else {
                    R.string.music_album_removed_from_favorites
                }
                Snackbar.make(contentList, messageRes, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // ========== PLAYLIST MANAGEMENT ==========

    private fun showPlaylistContextMenu(playlist: Playlist, anchorView: View): Boolean {
        // Only show context menu for custom playlists (not system playlists like Favorites)
        if (playlist.isSystemPlaylist) return false

        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_playlist_context, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_rename_playlist -> {
                    showRenamePlaylistDialog(playlist)
                    true
                }
                R.id.action_delete_playlist -> {
                    showDeletePlaylistConfirmation(playlist)
                    true
                }
                else -> false
            }
        }

        popup.show()
        return true
    }

    private fun showCreatePlaylistDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_playlist_name, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.playlist_name_input)

        AlertDialog.Builder(this)
            .setTitle(R.string.music_create_playlist)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text?.toString()?.trim()
                if (!name.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        MusicPlaylistManager.createPlaylist(name)
                        refreshPlaylistsList()
                        Snackbar.make(contentList, R.string.music_playlist_created, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun showRenamePlaylistDialog(playlist: Playlist) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_playlist_name, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.playlist_name_input)
        nameInput.setText(playlist.name)
        nameInput.selectAll()

        AlertDialog.Builder(this)
            .setTitle(R.string.music_rename_playlist)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = nameInput.text?.toString()?.trim()
                if (!newName.isNullOrEmpty() && newName != playlist.name) {
                    lifecycleScope.launch {
                        MusicPlaylistManager.renamePlaylist(playlist.id, newName)
                        refreshPlaylistsList()
                    }
                }
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun showDeletePlaylistConfirmation(playlist: Playlist) {
        AlertDialog.Builder(this)
            .setTitle(R.string.music_delete_playlist)
            .setMessage(getString(R.string.music_confirm_delete_playlist, playlist.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    MusicPlaylistManager.deletePlaylist(playlist.id)
                    refreshPlaylistsList()
                    Snackbar.make(contentList, R.string.music_playlist_deleted, Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun showAddToPlaylistDialog(track: MusicTrack) {
        val playlists = MusicPlaylistManager.getPlaylists()

        if (playlists.isEmpty()) {
            // No playlists yet, offer to create one
            AlertDialog.Builder(this)
                .setTitle(R.string.music_add_to_playlist)
                .setMessage(R.string.music_create_playlist_first)
                .setPositiveButton(R.string.music_create_playlist) { _, _ ->
                    showCreatePlaylistDialogAndAddTrack(track)
                }
                .setNegativeButton(R.string.music_cancel, null)
                .show()
            return
        }

        // Build list of playlist names
        val playlistNames = playlists.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.music_select_playlist)
            .setItems(playlistNames) { _, which ->
                val selectedPlaylist = playlists[which]
                addTrackToPlaylist(track, selectedPlaylist)
            }
            .setNeutralButton(R.string.music_create_playlist) { _, _ ->
                showCreatePlaylistDialogAndAddTrack(track)
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun showCreatePlaylistDialogAndAddTrack(track: MusicTrack) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_playlist_name, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.playlist_name_input)

        AlertDialog.Builder(this)
            .setTitle(R.string.music_create_playlist)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text?.toString()?.trim()
                if (!name.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        val playlistId = MusicPlaylistManager.createPlaylist(name)
                        MusicPlaylistManager.addTrackToPlaylist(playlistId, track)
                        Snackbar.make(
                            contentList,
                            getString(R.string.music_track_added_to_playlist, name),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun addTrackToPlaylist(track: MusicTrack, playlistData: PlaylistData) {
        lifecycleScope.launch {
            if (MusicPlaylistManager.isTrackInPlaylist(playlistData.id, track)) {
                Snackbar.make(contentList, R.string.music_track_already_in_playlist, Snackbar.LENGTH_SHORT).show()
            } else {
                MusicPlaylistManager.addTrackToPlaylist(playlistData.id, track)
                Snackbar.make(
                    contentList,
                    getString(R.string.music_track_added_to_playlist, playlistData.name),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun removeTrackFromCurrentPlaylist(track: MusicTrack) {
        val playlist = currentPlaylist ?: return

        lifecycleScope.launch {
            MusicPlaylistManager.removeTrackFromPlaylist(playlist.id, track)
            Snackbar.make(contentList, R.string.music_track_removed_from_playlist, Snackbar.LENGTH_SHORT).show()

            // Refresh the current playlist view
            val updatedTracks = getTracksForPlaylist(playlist)
            trackAdapter.submitList(updatedTracks)

            // Update stats
            statsInfo.text = resources.getQuantityString(
                R.plurals.music_track_count_plural,
                updatedTracks.size,
                updatedTracks.size
            )

            // Update displayed tracks (will be used if user clicks a track)
            displayedTracks = updatedTracks
        }
    }

    private fun refreshPlaylistsList() {
        if (currentLevel == NavigationLevel.PLAYLISTS) {
            val playlists = buildPlaylistsList()
            playlistAdapter.submitList(null) // Force refresh
            playlistAdapter.submitList(playlists)
        }
    }

    // ========== FILE DELETION ==========

    private fun showDeleteTrackConfirmation(track: MusicTrack) {
        AlertDialog.Builder(this)
            .setTitle(R.string.music_delete_file)
            .setMessage(getString(R.string.music_confirm_delete_track, track.title))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteTrack(track)
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun showDeleteAlbumConfirmation(album: Album) {
        AlertDialog.Builder(this)
            .setTitle(R.string.music_delete_album)
            .setMessage(getString(R.string.music_confirm_delete_album, album.name, album.tracks.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteAlbumTracks(album)
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun deleteTrack(track: MusicTrack) {
        lifecycleScope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ : use createDeleteRequest
                    val uris = listOf(track.uri)
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(contentResolver, uris)
                    pendingDeleteTrack = track
                    deleteFileLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    // Android 10 and below: try direct deletion
                    val deletedRows = contentResolver.delete(track.uri, null, null)
                    if (deletedRows > 0) {
                        onTrackDeleteSuccess(track)
                    } else {
                        // Try file deletion as fallback
                        track.filePath?.let { path ->
                            val file = File(path)
                            if (file.exists() && file.delete()) {
                                // Notify MediaStore
                                contentResolver.delete(track.uri, null, null)
                                onTrackDeleteSuccess(track)
                            } else {
                                Snackbar.make(contentList, R.string.music_delete_error, Snackbar.LENGTH_SHORT).show()
                            }
                        } ?: run {
                            Snackbar.make(contentList, R.string.music_delete_error, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(contentList, R.string.music_delete_error, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteAlbumTracks(album: Album) {
        lifecycleScope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ : use createDeleteRequest for all tracks
                    val uris = album.tracks.map { it.uri }
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(contentResolver, uris)
                    pendingDeleteAlbum = album
                    deleteFileLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    // Android 10 and below: delete each track
                    var deletedCount = 0
                    for (track in album.tracks) {
                        try {
                            val deletedRows = contentResolver.delete(track.uri, null, null)
                            if (deletedRows > 0) {
                                deletedCount++
                            } else {
                                // Try file deletion as fallback
                                track.filePath?.let { path ->
                                    val file = File(path)
                                    if (file.exists() && file.delete()) {
                                        contentResolver.delete(track.uri, null, null)
                                        deletedCount++
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (deletedCount > 0) {
                        onAlbumDeleteSuccess(album, deletedCount)
                    } else {
                        Snackbar.make(contentList, R.string.music_delete_error, Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(contentList, R.string.music_delete_error, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun onTrackDeleteSuccess(track: MusicTrack) {
        Snackbar.make(contentList, R.string.music_track_deleted, Snackbar.LENGTH_SHORT).show()

        // Stop playback if this track is currently playing
        val currentTrack = MusicPlaybackHolder.getCurrentTrack()
        if (currentTrack?.id == track.id) {
            MusicPlaybackHolder.stop(this)
        }

        // Refresh the library
        loadMusicLibrary()
    }

    private fun onAlbumDeleteSuccess(album: Album, count: Int = album.tracks.size) {
        Snackbar.make(
            contentList,
            getString(R.string.music_album_deleted, count),
            Snackbar.LENGTH_SHORT
        ).show()

        // Stop playback if current track is in this album
        val currentTrack = MusicPlaybackHolder.getCurrentTrack()
        if (currentTrack != null && album.tracks.any { it.id == currentTrack.id }) {
            MusicPlaybackHolder.stop(this)
        }

        // Refresh the library
        loadMusicLibrary()
    }

    // ========== END FILE DELETION ==========

    // ========== END PLAYLIST MANAGEMENT ==========

    private fun showTrackInfo(track: MusicTrack) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_track_info, null)

        // References aux vues
        val infoTitle = dialogView.findViewById<TextView>(R.id.info_title)
        val infoArtist = dialogView.findViewById<TextView>(R.id.info_artist)
        val infoAlbum = dialogView.findViewById<TextView>(R.id.info_album)
        val infoYear = dialogView.findViewById<TextView>(R.id.info_year)
        val infoTrackNumber = dialogView.findViewById<TextView>(R.id.info_track_number)
        val infoDuration = dialogView.findViewById<TextView>(R.id.info_duration)
        val infoFormat = dialogView.findViewById<TextView>(R.id.info_format)
        val infoBitrate = dialogView.findViewById<TextView>(R.id.info_bitrate)
        val infoSampleRate = dialogView.findViewById<TextView>(R.id.info_sample_rate)
        val infoChannels = dialogView.findViewById<TextView>(R.id.info_channels)
        val infoFileSize = dialogView.findViewById<TextView>(R.id.info_file_size)
        val infoPlayCount = dialogView.findViewById<TextView>(R.id.info_play_count)
        val infoFilePath = dialogView.findViewById<TextView>(R.id.info_file_path)
        val infoDateModified = dialogView.findViewById<TextView>(R.id.info_date_modified)

        // Étoiles pour la note
        val stars = listOf(
            dialogView.findViewById<ImageView>(R.id.star_1),
            dialogView.findViewById<ImageView>(R.id.star_2),
            dialogView.findViewById<ImageView>(R.id.star_3),
            dialogView.findViewById<ImageView>(R.id.star_4),
            dialogView.findViewById<ImageView>(R.id.star_5)
        )

        // Afficher les infos de base
        infoTitle.text = getString(R.string.music_info_title, track.title)
        infoArtist.text = getString(R.string.music_info_artist, track.artist)
        infoAlbum.text = getString(R.string.music_info_album, track.album)
        infoDuration.text = getString(R.string.music_info_duration, track.durationFormatted)

        // Année
        if (track.year != null && track.year > 0) {
            infoYear.text = getString(R.string.music_info_year, track.year)
            infoYear.visibility = View.VISIBLE
        }

        // Numéro de piste
        if (track.trackNumber != null && track.trackNumber > 0) {
            infoTrackNumber.text = getString(R.string.music_info_track_number, track.trackNumber)
            infoTrackNumber.visibility = View.VISIBLE
        }

        // Chemin du fichier
        track.filePath?.let { path ->
            infoFilePath.text = getString(R.string.music_info_file_path, path)
        }

        // Play count - utilise la version avec MusicTrack pour consulter aussi le JSON
        val playCount = MusicStatsManager.getPlayCount(track)
        infoPlayCount.text = getString(R.string.music_info_play_count, playCount.toInt())

        // Rating - convertir de 0-255 à 0-5 étoiles
        var currentRating = convertRatingToStars(MusicStatsManager.getRating(track.id))
        updateStarsDisplay(stars, currentRating)

        // Gestionnaires de clic pour les étoiles
        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                val newRating = index + 1
                // Si on clique sur la même étoile, on remet à 0
                currentRating = if (currentRating == newRating) 0 else newRating
                updateStarsDisplay(stars, currentRating)

                // Sauvegarder la note
                lifecycleScope.launch {
                    val rating255 = convertStarsToRating(currentRating)
                    MusicStatsManager.setRating(track, rating255)
                    Snackbar.make(contentList, R.string.music_rating_updated, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        // Créer le dialogue
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.music_track_info)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        dialog.show()

        // Charger les infos techniques en arrière-plan
        lifecycleScope.launch {
            val audioInfo = MusicFileInfo.getAudioFileInfo(this@MusicPlayerActivity, track)

            runOnUiThread {
                // Format
                if (audioInfo.format.isNotEmpty()) {
                    infoFormat.text = getString(R.string.music_info_format, audioInfo.format)
                }

                // Bitrate
                if (audioInfo.bitrate.isNotEmpty()) {
                    infoBitrate.text = getString(R.string.music_info_bitrate, audioInfo.bitrate)
                }

                // Sample rate
                if (audioInfo.sampleRate.isNotEmpty()) {
                    infoSampleRate.text = getString(R.string.music_info_sample_rate, audioInfo.sampleRate)
                }

                // Channels
                if (audioInfo.channels > 0) {
                    val channelsStr = when (audioInfo.channels) {
                        1 -> getString(R.string.music_info_channels_mono)
                        2 -> getString(R.string.music_info_channels_stereo)
                        else -> getString(R.string.music_info_channels_format, audioInfo.channels)
                    }
                    infoChannels.text = getString(R.string.music_info_channels, channelsStr)
                }

                // File size
                if (audioInfo.fileSize > 0) {
                    infoFileSize.text = getString(R.string.music_info_file_size, MusicFileInfo.formatFileSize(audioInfo.fileSize))
                }

                // Date modified
                if (audioInfo.dateModified > 0) {
                    infoDateModified.text = getString(R.string.music_info_date_modified, MusicFileInfo.formatDate(audioInfo.dateModified))
                }
            }
        }
    }

    /**
     * Convertit un rating 0-255 en nombre d'étoiles 0-5
     */
    private fun convertRatingToStars(rating: Long): Int {
        return when {
            rating == 0L -> 0
            rating <= 31 -> 1  // 1-31 = 1 étoile (rating POPM Windows)
            rating <= 95 -> 2  // 32-95 = 2 étoiles
            rating <= 159 -> 3 // 96-159 = 3 étoiles
            rating <= 223 -> 4 // 160-223 = 4 étoiles
            else -> 5          // 224-255 = 5 étoiles
        }
    }

    /**
     * Convertit un nombre d'étoiles 0-5 en rating 0-255
     */
    private fun convertStarsToRating(stars: Int): Long {
        return when (stars) {
            0 -> 0L
            1 -> 1L    // Windows Media Player standard
            2 -> 64L
            3 -> 128L
            4 -> 196L
            5 -> 255L
            else -> 0L
        }
    }

    /**
     * Met à jour l'affichage des étoiles
     */
    private fun updateStarsDisplay(stars: List<ImageView>, rating: Int) {
        stars.forEachIndexed { index, star ->
            star.setImageResource(
                if (index < rating) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }
    }

    private fun checkWritePermissionAndEdit(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ needs MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.music_permission_required)
                    .setMessage(R.string.music_write_permission_message)
                    .setPositiveButton(R.string.music_open_settings) { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = "package:$packageName".toUri()
                            startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton(R.string.music_cancel, null)
                    .show()
                return
            }
        }
        // Permission granted or older Android, proceed
        action()
    }

    private fun showEditTrackTagsDialog(track: MusicTrack) {
        checkWritePermissionAndEdit {
            doShowEditTrackTagsDialog(track)
        }
    }

    private fun doShowEditTrackTagsDialog(track: MusicTrack) {
        lifecycleScope.launch {
            val tagInfo = MusicTagEditor.readTags(track)
            if (tagInfo == null) {
                Snackbar.make(contentList, R.string.music_tags_read_error, Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            runOnUiThread {
                val dialogView = layoutInflater.inflate(R.layout.dialog_edit_tags, null)

                // Get references
                val editTitle = dialogView.findViewById<TextInputEditText>(R.id.edit_title)
                val editArtist = dialogView.findViewById<TextInputEditText>(R.id.edit_artist)
                val editAlbumArtist = dialogView.findViewById<TextInputEditText>(R.id.edit_album_artist)
                val editAlbum = dialogView.findViewById<TextInputEditText>(R.id.edit_album)
                val editYear = dialogView.findViewById<TextInputEditText>(R.id.edit_year)
                val editTrackNumber = dialogView.findViewById<TextInputEditText>(R.id.edit_track_number)
                val editDiscNumber = dialogView.findViewById<TextInputEditText>(R.id.edit_disc_number)

                // Populate fields
                editTitle.setText(tagInfo.title)
                editArtist.setText(tagInfo.artist)
                editAlbumArtist.setText(tagInfo.albumArtist)
                editAlbum.setText(tagInfo.album)
                editYear.setText(tagInfo.year)
                editTrackNumber.setText(tagInfo.trackNumber)
                editDiscNumber.setText(tagInfo.discNumber)

                AlertDialog.Builder(this@MusicPlayerActivity)
                    .setTitle(R.string.music_edit_tags)
                    .setView(dialogView)
                    .setPositiveButton(R.string.music_tags_save) { _, _ ->
                        val newTagInfo = MusicTagEditor.TagInfo(
                            title = editTitle.text?.toString() ?: "",
                            artist = editArtist.text?.toString() ?: "",
                            albumArtist = editAlbumArtist.text?.toString() ?: "",
                            album = editAlbum.text?.toString() ?: "",
                            year = editYear.text?.toString() ?: "",
                            trackNumber = editTrackNumber.text?.toString() ?: "",
                            discNumber = editDiscNumber.text?.toString() ?: ""
                        )

                        lifecycleScope.launch {
                            val filePath = track.filePath
                            if (filePath == null) {
                                runOnUiThread {
                                    Snackbar.make(contentList, R.string.music_tags_save_error, Snackbar.LENGTH_SHORT).show()
                                }
                                return@launch
                            }

                            val success = MusicTagEditor.writeTags(this@MusicPlayerActivity, track, newTagInfo)
                            if (success) {
                                // Rescan par filePath (pas par ID car il peut avoir changé)
                                val updatedTrack = MusicScanner.scanSingleTrackByPath(this@MusicPlayerActivity, filePath)
                                runOnUiThread {
                                    Snackbar.make(contentList, R.string.music_tags_saved, Snackbar.LENGTH_SHORT).show()
                                    if (updatedTrack != null) {
                                        val needsReorganization = MusicLibrary.updateTrack(track, updatedTrack)
                                        // Met à jour aussi la queue de lecture (l'ID a pu changer)
                                        MusicPlaybackHolder.updateTracksInPlaylist(listOf(track to updatedTrack))
                                        // Met à jour le cache de la bibliothèque organisée
                                        lifecycleScope.launch { MusicLibrary.saveToCache(this@MusicPlayerActivity) }
                                        if (needsReorganization) {
                                            // L'artiste ou l'album a changé, on doit rafraîchir la navigation
                                            refreshCurrentView()
                                        } else {
                                            // Mise à jour simple, rafraîchir la liste des tracks
                                            refreshTrackList()
                                        }
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    Snackbar.make(contentList, R.string.music_tags_save_error, Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(R.string.music_cancel, null)
                    .show()
            }
        }
    }

    private fun showEditAlbumTagsDialog(album: Album) {
        checkWritePermissionAndEdit {
            doShowEditAlbumTagsDialog(album)
        }
    }

    private fun doShowEditAlbumTagsDialog(album: Album) {
        if (album.tracks.isEmpty()) return

        // Use first track to get current album-level tags
        val firstTrack = album.tracks.first()

        lifecycleScope.launch {
            val tagInfo = MusicTagEditor.readTags(firstTrack)
            if (tagInfo == null) {
                runOnUiThread {
                    Snackbar.make(contentList, R.string.music_tags_read_error, Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }

            runOnUiThread {
                val dialogView = layoutInflater.inflate(R.layout.dialog_edit_tags, null)

                // Get references
                val layoutTitle = dialogView.findViewById<TextInputLayout>(R.id.layout_title)
                val layoutTrackNumber = dialogView.findViewById<TextInputLayout>(R.id.layout_track_number)
                val editArtist = dialogView.findViewById<TextInputEditText>(R.id.edit_artist)
                val editAlbumArtist = dialogView.findViewById<TextInputEditText>(R.id.edit_album_artist)
                val editAlbum = dialogView.findViewById<TextInputEditText>(R.id.edit_album)
                val editYear = dialogView.findViewById<TextInputEditText>(R.id.edit_year)
                val editDiscNumber = dialogView.findViewById<TextInputEditText>(R.id.edit_disc_number)

                // Hide title and track number for album edit (they are track-specific)
                layoutTitle.visibility = View.GONE
                layoutTrackNumber.visibility = View.GONE

                // Populate fields with album-level values
                editArtist.setText(tagInfo.artist)
                editAlbumArtist.setText(tagInfo.albumArtist)
                editAlbum.setText(tagInfo.album)
                editYear.setText(tagInfo.year)
                editDiscNumber.setText(tagInfo.discNumber)

                AlertDialog.Builder(this@MusicPlayerActivity)
                    .setTitle(R.string.music_edit_album_tags)
                    .setView(dialogView)
                    .setPositiveButton(R.string.music_tags_save) { _, _ ->
                        val newTagInfo = MusicTagEditor.TagInfo(
                            title = "", // Not used for album
                            artist = editArtist.text?.toString() ?: "",
                            albumArtist = editAlbumArtist.text?.toString() ?: "",
                            album = editAlbum.text?.toString() ?: "",
                            year = editYear.text?.toString() ?: "",
                            trackNumber = "", // Not used for album
                            discNumber = editDiscNumber.text?.toString() ?: ""
                        )

                        lifecycleScope.launch {
                            val oldTracks = album.tracks.toList()
                            // Garder les filePaths pour matcher après édition (les IDs peuvent changer)
                            val filePaths = oldTracks.mapNotNull { it.filePath }

                            val successCount = MusicTagEditor.writeAlbumTags(this@MusicPlayerActivity, album.tracks, newTagInfo)
                            if (successCount > 0) {
                                // Rescan par filePath (pas par ID car ils peuvent avoir changé)
                                val updatedTracksByPath = MusicScanner.scanTracksByPaths(this@MusicPlayerActivity, filePaths)
                                runOnUiThread {
                                    Snackbar.make(
                                        contentList,
                                        getString(R.string.music_album_tags_saved, successCount, album.tracks.size),
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                    if (updatedTracksByPath.isNotEmpty()) {
                                        // Matcher ancien/nouveau par filePath au lieu de ID
                                        val updates = oldTracks.mapNotNull { old ->
                                            old.filePath?.let { path ->
                                                updatedTracksByPath[path]?.let { new -> old to new }
                                            }
                                        }
                                        val needsReorganization = MusicLibrary.updateTracks(updates)
                                        // Met à jour aussi la queue de lecture (les IDs ont pu changer)
                                        MusicPlaybackHolder.updateTracksInPlaylist(updates)
                                        // Met à jour le cache de la bibliothèque organisée
                                        lifecycleScope.launch { MusicLibrary.saveToCache(this@MusicPlayerActivity) }
                                        if (needsReorganization) {
                                            refreshCurrentView()
                                        } else {
                                            refreshTrackList()
                                        }
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    Snackbar.make(
                                        contentList,
                                        getString(R.string.music_album_tags_saved, successCount, album.tracks.size),
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(R.string.music_cancel, null)
                    .show()
            }
        }
    }

    // ==================== FOLDER TAG EDITING ====================

    private fun showEditFolderTagsDialog(folder: Folder) {
        checkWritePermissionAndEdit {
            doShowEditFolderTagsDialog(folder)
        }
    }

    private fun doShowEditFolderTagsDialog(folder: Folder) {
        // Get all tracks in this folder and all subfolders recursively
        val tracks = folder.getAllTracksRecursive()
        if (tracks.isEmpty()) {
            Snackbar.make(contentList, R.string.music_no_tracks, Snackbar.LENGTH_SHORT).show()
            return
        }

        // Recursive mode when the folder contains subfolders
        val hasSubfolders = folder.subfolders.isNotEmpty()

        // Use first track to get current tags as defaults
        val firstTrack = tracks.first()

        lifecycleScope.launch {
            val tagInfo = MusicTagEditor.readTags(firstTrack)
            if (tagInfo == null) {
                runOnUiThread {
                    Snackbar.make(contentList, R.string.music_tags_read_error, Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }

            runOnUiThread {
                val dialogView = layoutInflater.inflate(R.layout.dialog_edit_tags, null)

                // Get references
                val layoutTitle = dialogView.findViewById<TextInputLayout>(R.id.layout_title)
                val layoutTrackNumber = dialogView.findViewById<TextInputLayout>(R.id.layout_track_number)
                val layoutAlbum = dialogView.findViewById<TextInputLayout>(R.id.layout_album)
                val layoutYear = dialogView.findViewById<TextInputLayout>(R.id.layout_year)
                val layoutDiscNumber = dialogView.findViewById<TextInputLayout>(R.id.layout_disc_number)
                val editArtist = dialogView.findViewById<TextInputEditText>(R.id.edit_artist)
                val editAlbumArtist = dialogView.findViewById<TextInputEditText>(R.id.edit_album_artist)
                val editAlbum = dialogView.findViewById<TextInputEditText>(R.id.edit_album)
                val editYear = dialogView.findViewById<TextInputEditText>(R.id.edit_year)
                val editDiscNumber = dialogView.findViewById<TextInputEditText>(R.id.edit_disc_number)

                // Always hide title and track number for folder edit (they are track-specific)
                layoutTitle.visibility = View.GONE
                layoutTrackNumber.visibility = View.GONE

                // In recursive mode (folder with subfolders), only Artist and Album Artist
                // are meaningful across heterogeneous content — hide album-specific fields
                if (hasSubfolders) {
                    layoutAlbum.visibility = View.GONE
                    layoutYear.visibility = View.GONE
                    layoutDiscNumber.visibility = View.GONE
                }

                // Populate fields with current values from first track
                editArtist.setText(tagInfo.artist)
                editAlbumArtist.setText(tagInfo.albumArtist)
                if (!hasSubfolders) {
                    editAlbum.setText(tagInfo.album)
                    editYear.setText(tagInfo.year)
                    editDiscNumber.setText(tagInfo.discNumber)
                }

                AlertDialog.Builder(this@MusicPlayerActivity)
                    .setTitle(getString(R.string.music_edit_folder_tags, folder.name))
                    .setView(dialogView)
                    .setPositiveButton(R.string.music_tags_save) { _, _ ->
                        val newTagInfo = MusicTagEditor.TagInfo(
                            title = "", // Not used for folder
                            artist = editArtist.text?.toString() ?: "",
                            albumArtist = editAlbumArtist.text?.toString() ?: "",
                            // In recursive mode these fields are intentionally left empty
                            // so writeAlbumTags skips them (only non-empty fields are written)
                            album = if (hasSubfolders) "" else editAlbum.text?.toString() ?: "",
                            year = if (hasSubfolders) "" else editYear.text?.toString() ?: "",
                            trackNumber = "", // Not used for folder
                            discNumber = if (hasSubfolders) "" else editDiscNumber.text?.toString() ?: ""
                        )

                        lifecycleScope.launch {
                            val oldTracks = tracks.toList()
                            // Keep filePaths for matching after edit (IDs may change)
                            val filePaths = oldTracks.mapNotNull { it.filePath }

                            val successCount = MusicTagEditor.writeAlbumTags(this@MusicPlayerActivity, tracks, newTagInfo)
                            if (successCount > 0) {
                                // Rescan by filePath (not by ID as they may have changed)
                                val updatedTracksByPath = MusicScanner.scanTracksByPaths(this@MusicPlayerActivity, filePaths)
                                runOnUiThread {
                                    Snackbar.make(
                                        contentList,
                                        getString(R.string.music_folder_tags_saved, successCount, tracks.size),
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                    if (updatedTracksByPath.isNotEmpty()) {
                                        // Match old/new by filePath instead of ID
                                        val updates = oldTracks.mapNotNull { old ->
                                            old.filePath?.let { path ->
                                                updatedTracksByPath[path]?.let { new -> old to new }
                                            }
                                        }
                                        val needsReorganization = MusicLibrary.updateTracks(updates)
                                        // Update playback queue too (IDs may have changed)
                                        MusicPlaybackHolder.updateTracksInPlaylist(updates)
                                        // Update organized library cache
                                        lifecycleScope.launch { MusicLibrary.saveToCache(this@MusicPlayerActivity) }
                                        if (needsReorganization) {
                                            refreshCurrentView()
                                        } else {
                                            refreshTrackList()
                                        }
                                    }
                                }
                            } else {
                                runOnUiThread {
                                    Snackbar.make(
                                        contentList,
                                        getString(R.string.music_folder_tags_saved, successCount, tracks.size),
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(R.string.music_cancel, null)
                    .show()
            }
        }
    }

    // ==================== ALBUM COVER ====================

    private fun showAddCoverDialog(album: Album) {
        checkWritePermissionAndEdit {
            pendingCoverAlbum = album

            // Vérifie si l'album a déjà une pochette
            val hasExistingCover = album.albumArtUri != null

            val options = arrayOf(
                getString(R.string.music_cover_from_gallery),
                getString(R.string.music_cover_from_web)
            )

            val titleRes = if (hasExistingCover) R.string.music_replace_cover else R.string.music_add_cover

            AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> galleryPickerLauncher.launch("image/*")
                        1 -> {
                            val intent = Intent(this, AlbumArtSearchActivity::class.java).apply {
                                putExtra(AlbumArtSearchActivity.EXTRA_ARTIST, currentArtist?.name ?: "")
                                putExtra(AlbumArtSearchActivity.EXTRA_ALBUM, album.name)
                            }
                            webSearchLauncher.launch(intent)
                        }
                    }
                }
                .setNegativeButton(R.string.music_cancel, null)
                .show()
        }
    }

    private fun handleSelectedCoverImage(album: Album, imageUri: Uri) {
        try {
            // Copy image to a temp file for processing
            val inputStream = contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Snackbar.make(contentList, R.string.music_cover_error, Snackbar.LENGTH_SHORT).show()
                return
            }

            val tempFile = File(cacheDir, "temp_cover_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            // Compress the image (max 800px, max 200Ko)
            lifecycleScope.launch {
                val compressedFile = File(cacheDir, "compressed_cover_${System.currentTimeMillis()}.jpg")
                val success = ImageCompressor.compressAlbumCover(tempFile.absolutePath, compressedFile.absolutePath)
                tempFile.delete()

                runOnUiThread {
                    if (success && compressedFile.exists()) {
                        showCoverPreviewDialog(album, compressedFile.absolutePath)
                    } else {
                        Snackbar.make(contentList, R.string.music_cover_error, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (_: Exception) {
            Snackbar.make(contentList, R.string.music_cover_error, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showCoverPreviewDialog(album: Album, imagePath: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_cover_preview, null)
        val coverPreview = dialogView.findViewById<ImageView>(R.id.cover_preview)
        val albumInfo = dialogView.findViewById<TextView>(R.id.album_info)

        // Load image preview
        val bitmap = BitmapFactory.decodeFile(imagePath)
        if (bitmap != null) {
            coverPreview.setImageBitmap(bitmap)
        } else {
            Snackbar.make(contentList, R.string.music_cover_error, Snackbar.LENGTH_SHORT).show()
            return
        }

        val trackCountText = resources.getQuantityString(
            R.plurals.music_track_count_plural, album.trackCount, album.trackCount
        )
        albumInfo.text = getString(
            R.string.music_album_info_format,
            currentArtist?.name.orEmpty(),
            album.name,
            trackCountText
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.music_cover_preview)
            .setView(dialogView)
            .setPositiveButton(R.string.music_cover_apply) { _, _ ->
                embedCoverArt(album, imagePath)
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    // État de lecture sauvegardé pour restauration après modification de fichiers
    private data class SavedPlaybackState(
        val playlist: List<MusicTrack>,
        val currentIndex: Int,
        val position: Long,
        val wasPlaying: Boolean
    )

    private fun embedCoverArt(album: Album, imagePath: String) {
        lifecycleScope.launch {
            val oldTracks = album.tracks.toList()
            // Garder les filePaths pour matcher après édition (les IDs peuvent changer)
            val filePaths = oldTracks.mapNotNull { it.filePath }.toSet()
            val artistName = album.artist

            // Vérifier si des pistes de l'album sont dans la playlist ExoPlayer
            val currentPlaylist = MusicPlaybackHolder.getPlaylist()
            val tracksInPlaylist = currentPlaylist.filter { it.filePath in filePaths }

            // Si des pistes sont dans la playlist, il faut libérer les fichiers
            var savedState: SavedPlaybackState? = null
            if (tracksInPlaylist.isNotEmpty()) {
                // Sauvegarder l'état de lecture
                savedState = SavedPlaybackState(
                    playlist = currentPlaylist,
                    currentIndex = MusicPlaybackHolder.getCurrentIndex(),
                    position = MusicPlaybackHolder.getPosition(),
                    wasPlaying = MusicPlaybackHolder.isPlaying()
                )
                // Arrêter et libérer le player pour libérer les handles de fichiers
                MusicPlaybackHolder.release(this@MusicPlayerActivity)
            }

            // Invalider le cache de l'ancien album AVANT l'embedding
            AlbumImageCache.invalidateAlbum(album)
            // Invalider aussi le cache artiste (si cet album était utilisé comme icône par défaut)
            ArtistImageCache.invalidateArtist(artistName)

            val successCount = MusicTagEditor.embedAlbumArt(this@MusicPlayerActivity, album.tracks, imagePath)
            if (successCount > 0) {
                // Rescan les fichiers par filePath (pas par ID car ils peuvent avoir changé)
                val allFilePaths = oldTracks.mapNotNull { it.filePath }
                val updatedTracksByPath = MusicScanner.scanTracksByPaths(this@MusicPlayerActivity, allFilePaths)

                // Matcher ancien/nouveau par filePath
                val updates = oldTracks.mapNotNull { old ->
                    old.filePath?.let { path ->
                        updatedTracksByPath[path]?.let { new -> old to new }
                    }
                }

                // Mettre à jour la bibliothèque
                MusicLibrary.updateTracks(updates)
                MusicLibrary.saveToCache(this@MusicPlayerActivity)

                // Restaurer la playlist si on l'avait libérée
                if (savedState != null) {
                    // Reconstruire la playlist avec les nouveaux tracks
                    val updatesMap = updates.associate { (old, new) -> old.filePath to new }
                    val restoredPlaylist = savedState.playlist.map { track ->
                        track.filePath?.let { updatesMap[it] } ?: track
                    }

                    // Restaurer la playlist
                    MusicPlaybackHolder.setPlaylist(restoredPlaylist)

                    // Reprendre à la position sauvegardée
                    if (savedState.currentIndex in restoredPlaylist.indices) {
                        MusicPlaybackHolder.playAtIndex(this@MusicPlayerActivity, savedState.currentIndex)
                        // Attendre que le player soit prêt puis seek à la position
                        kotlinx.coroutines.delay(200)
                        MusicPlaybackHolder.seekTo(savedState.position)
                        if (!savedState.wasPlaying) {
                            MusicPlaybackHolder.pause(this@MusicPlayerActivity)
                        }
                    }
                } else {
                    // Pas de playlist à restaurer, juste mettre à jour la queue si elle existe
                    MusicPlaybackHolder.updateTracksInPlaylist(updates)
                }

                runOnUiThread {
                    Snackbar.make(
                        contentList,
                        getString(R.string.music_cover_added, successCount, album.tracks.size),
                        Snackbar.LENGTH_SHORT
                    ).show()

                    // Invalider le cache avec le nouvel album
                    val updatedAlbum = currentArtist?.albums?.find { it.name == album.name }
                    if (updatedAlbum != null) {
                        AlbumImageCache.invalidateAlbum(updatedAlbum)
                    }
                    ArtistImageCache.invalidateArtist(artistName)

                    // Rafraîchir l'affichage
                    refreshCurrentView()
                }
            } else {
                // Échec de l'embedding - restaurer quand même la lecture si on l'avait arrêtée
                if (savedState != null) {
                    MusicPlaybackHolder.setPlaylist(savedState.playlist)
                    if (savedState.currentIndex in savedState.playlist.indices) {
                        MusicPlaybackHolder.playAtIndex(this@MusicPlayerActivity, savedState.currentIndex)
                        kotlinx.coroutines.delay(200)
                        MusicPlaybackHolder.seekTo(savedState.position)
                        if (!savedState.wasPlaying) {
                            MusicPlaybackHolder.pause(this@MusicPlayerActivity)
                        }
                    }
                }
                runOnUiThread {
                    Snackbar.make(contentList, R.string.music_cover_error, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupMiniPlayer() {
        miniBtnPlayPause.setOnClickListener {
            MusicPlaybackHolder.togglePlayPause(this)
        }

        miniBtnPrev.setOnClickListener {
            MusicPlaybackHolder.skipToPrevious(this)
        }

        miniBtnNext.setOnClickListener {
            MusicPlaybackHolder.skipToNext(this)
        }

        miniPlayer.setOnClickListener {
            // Open full player view
            if (MusicPlaybackHolder.getCurrentTrack() != null) {
                startActivity(Intent(this, FullPlayerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            }
        }
    }

    private fun setupAlphabetIndex() {
        alphabetIndex.setOnLetterSelectedListener(object : AlphabetIndexView.OnLetterSelectedListener {
            override fun onLetterSelected(letter: String) {
                scrollToLetter(letter)
            }
        })
    }

    /**
     * Scroll vers le premier artiste commençant par la lettre donnée.
     * "#" correspond aux chiffres et symboles (tout ce qui n'est pas A-Z).
     */
    private fun scrollToLetter(letter: String) {
        val artists = when (currentLevel) {
            NavigationLevel.ARTISTS -> MusicLibrary.getArtists()
            NavigationLevel.ALBUM_ARTISTS -> MusicLibrary.getAlbumArtists()
            NavigationLevel.FAVORITE_ARTISTS -> {
                // Récupère les artistes favoris comme dans navigateToFavoriteArtists
                val favoriteArtistNames = ArtistCustomizationManager.getFavoriteArtistNames()
                MusicLibrary.getArtists().filter { artist ->
                    favoriteArtistNames.any { it.equals(artist.name, ignoreCase = true) }
                }
            }
            else -> return
        }

        val position = if (letter == "#") {
            // Cherche le premier artiste qui ne commence pas par A-Z (en ignorant "The ")
            artists.indexOfFirst { artist ->
                val sortName = MusicLibrary.getArtistSortName(artist.name)
                val firstChar = sortName.firstOrNull()?.uppercaseChar() ?: '#'
                firstChar !in 'A'..'Z'
            }
        } else {
            // Cherche le premier artiste commençant par la lettre (en ignorant "The ")
            artists.indexOfFirst { artist ->
                val sortName = MusicLibrary.getArtistSortName(artist.name)
                sortName.firstOrNull()?.uppercaseChar() == letter.firstOrNull()
            }
        }

        if (position >= 0) {
            // Utilise LinearLayoutManager.scrollToPositionWithOffset pour un positionnement précis
            // Note: GridLayoutManager extends LinearLayoutManager, so this covers both
            val layoutManager = contentList.layoutManager
            if (layoutManager is LinearLayoutManager) {
                layoutManager.scrollToPositionWithOffset(position, 0)
            } else {
                contentList.scrollToPosition(position)
            }
        }
    }

    /**
     * Affiche ou masque la barre alphabétique selon le niveau de navigation.
     */
    private fun updateAlphabetIndexVisibility() {
        val shouldShow = currentLevel == NavigationLevel.ARTISTS ||
                currentLevel == NavigationLevel.ALBUM_ARTISTS ||
                currentLevel == NavigationLevel.FAVORITE_ARTISTS

        alphabetIndex.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    private fun setupBackToClickerButton() {
        val btn = findViewById<View>(R.id.btn_back_to_clicker) ?: return
        btn.visibility = View.VISIBLE
        btn.setOnClickListener {
            val intent = Intent(this, com.Atom2Universe.app.crypto.MainClickerActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!handleBackNavigation()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun handleBackNavigation(): Boolean {
        return when (currentLevel) {
            NavigationLevel.TRACKS -> {
                val albumToScrollTo = currentAlbum
                when (parentAlbumsLevel) {
                    NavigationLevel.FAVORITE_ALBUMS -> {
                        // Venait des albums favoris
                        navigateToFavoriteAlbums(albumToScrollTo)
                    }
                    NavigationLevel.ALBUMS -> {
                        // Venait des albums d'un artiste
                        if (currentArtist != null) {
                            navigateToArtist(currentArtist!!, albumToScrollTo)
                        } else {
                            navigateToAllAlbums(albumToScrollTo)
                        }
                    }
                    NavigationLevel.ALL_ALBUMS -> {
                        // Venait de tous les albums
                        navigateToAllAlbums(albumToScrollTo)
                    }
                    else -> {
                        // Fallback: si currentArtist existe, c'est qu'on venait d'un artiste
                        if (currentArtist != null) {
                            navigateToArtist(currentArtist!!, albumToScrollTo)
                        } else {
                            navigateToAllAlbums(albumToScrollTo)
                        }
                    }
                }
                true
            }
            NavigationLevel.ALBUMS -> {
                val artistToScrollTo = currentArtist
                when (parentArtistsLevel) {
                    NavigationLevel.FAVORITE_ARTISTS -> {
                        // Venait des artistes favoris
                        navigateToFavoriteArtists(artistToScrollTo)
                    }
                    NavigationLevel.ALBUM_ARTISTS -> {
                        // Venait des artistes d'album
                        navigateToAlbumArtists(artistToScrollTo)
                    }
                    NavigationLevel.ARTISTS -> {
                        // Venait de tous les artistes
                        navigateToArtists(artistToScrollTo)
                    }
                    else -> {
                        // Fallback: utiliser isInAlbumArtistMode
                        if (isInAlbumArtistMode) {
                            navigateToAlbumArtists(artistToScrollTo)
                        } else {
                            navigateToArtists(artistToScrollTo)
                        }
                    }
                }
                true
            }
            NavigationLevel.ARTISTS -> {
                navigateToRoot()
                true
            }
            NavigationLevel.ALBUM_ARTISTS -> {
                navigateToRoot()
                true
            }
            NavigationLevel.FAVORITE_ARTISTS -> {
                navigateToRoot()
                true
            }
            NavigationLevel.ALL_ALBUMS -> {
                navigateToRoot()
                true
            }
            NavigationLevel.FAVORITE_ALBUMS -> {
                navigateToRoot()
                true
            }
            NavigationLevel.FOLDERS -> {
                // Navigate back in folder hierarchy or to root
                val childFolder = currentFolder
                if (folderPathHistory.size > 1) {
                    // Go back to parent folder, scrolling to the folder we came from
                    folderPathHistory.removeAt(folderPathHistory.size - 1)
                    val parentFolder = folderPathHistory.removeAt(folderPathHistory.size - 1)
                    navigateToFolder(parentFolder, scrollToFolder = childFolder)
                } else if (folderPathHistory.isNotEmpty()) {
                    // At root folder level, go back to folders root
                    folderPathHistory.clear()
                    navigateToFolders(scrollToFolder = childFolder)
                } else {
                    // At folders root, go back to ROOT
                    navigateToRoot()
                }
                true
            }
            NavigationLevel.FOLDER_TRACKS -> {
                // Go back to the folder showing subfolders
                val childFolder = currentFolder
                if (folderPathHistory.size > 1) {
                    // Go back to parent folder, scrolling to the folder we came from
                    folderPathHistory.removeAt(folderPathHistory.size - 1)
                    val parentFolder = folderPathHistory.removeAt(folderPathHistory.size - 1)
                    navigateToFolder(parentFolder, scrollToFolder = childFolder)
                } else if (folderPathHistory.isNotEmpty()) {
                    // Go back to folders root, scrolling to the folder we came from
                    folderPathHistory.clear()
                    navigateToFolders(scrollToFolder = childFolder)
                } else {
                    navigateToRoot()
                }
                true
            }
            NavigationLevel.SEARCH -> {
                navigateToRoot()
                true
            }
            NavigationLevel.PLAYLIST_TRACKS -> {
                val playlistToScrollTo = currentPlaylist
                navigateToPlaylists(playlistToScrollTo)
                true
            }
            NavigationLevel.PLAYLISTS -> {
                navigateToRoot()
                true
            }
            NavigationLevel.NAVIDROME_ROOT -> {
                navigateToRoot()
                true
            }
            NavigationLevel.NAVIDROME_ARTIST -> {
                navigateToNavidromeRoot()
                true
            }
            NavigationLevel.NAVIDROME_ALBUM -> {
                currentNavidromeArtist?.let { navigateToNavidromeArtist(it) } ?: navigateToNavidromeRoot()
                true
            }
            NavigationLevel.ROOT -> {
                navigateBackToHub()
                false
            }
        }
    }

    // Navigation methods
    /**
     * Navigue vers le Hub si l'activité est la racine de la tâche (lancée depuis widget/raccourci),
     * sinon termine simplement l'activité pour revenir à l'écran précédent.
     */
    private fun navigateBackToHub() {
        if (isTaskRoot) {
            startActivity(Intent(this, AudioHubActivity::class.java))
        }
        finish()
    }

    /**
     * Rafraîchit la vue courante sans changer de niveau de navigation.
     * Utilisé après édition de tags quand l'artiste/album a changé.
     */
    private fun refreshCurrentView() {
        when (currentLevel) {
            NavigationLevel.ROOT -> navigateToRoot()
            NavigationLevel.ARTISTS -> navigateToArtists()
            NavigationLevel.ALBUM_ARTISTS -> navigateToAlbumArtists()
            NavigationLevel.FAVORITE_ARTISTS -> navigateToFavoriteArtists()
            NavigationLevel.ALL_ALBUMS -> navigateToAllAlbums()
            NavigationLevel.FAVORITE_ALBUMS -> navigateToFavoriteAlbums()
            NavigationLevel.ALBUMS -> {
                // Retrouve l'artiste mis à jour dans la bibliothèque
                val artistName = currentArtist?.name
                if (artistName != null) {
                    val updatedArtist = if (isInAlbumArtistMode) {
                        MusicLibrary.getAlbumArtist(artistName)
                    } else {
                        MusicLibrary.getArtist(artistName)
                    }
                    if (updatedArtist != null) {
                        currentArtist = updatedArtist
                        navigateToArtist(updatedArtist)
                    } else {
                        // L'artiste n'existe plus, retour aux artistes
                        if (isInAlbumArtistMode) navigateToAlbumArtists() else navigateToArtists()
                    }
                } else {
                    navigateToRoot()
                }
            }
            NavigationLevel.TRACKS -> {
                // Retrouve l'album mis à jour
                val artistName = currentArtist?.name
                val albumName = currentAlbum?.name
                if (artistName != null && albumName != null) {
                    val updatedArtist = if (isInAlbumArtistMode) {
                        MusicLibrary.getAlbumArtist(artistName)
                    } else {
                        MusicLibrary.getArtist(artistName)
                    }
                    val updatedAlbum = updatedArtist?.albums?.find { it.name == albumName }
                    if (updatedAlbum != null) {
                        currentArtist = updatedArtist
                        currentAlbum = updatedAlbum
                        navigateToAlbum(updatedAlbum)
                    } else {
                        // L'album n'existe plus, retour aux albums de l'artiste
                        if (updatedArtist != null) {
                            currentArtist = updatedArtist
                            navigateToArtist(updatedArtist)
                        } else {
                            if (isInAlbumArtistMode) navigateToAlbumArtists() else navigateToArtists()
                        }
                    }
                } else {
                    navigateToRoot()
                }
            }
            NavigationLevel.PLAYLISTS -> navigateToPlaylists()
            NavigationLevel.PLAYLIST_TRACKS -> {
                currentPlaylist?.let { navigateToPlaylistTracks(it) } ?: navigateToPlaylists()
            }
            NavigationLevel.FOLDERS -> {
                currentFolder?.let { navigateToFolder(it) } ?: navigateToFolders()
            }
            NavigationLevel.FOLDER_TRACKS -> {
                currentFolder?.let { navigateToFolderTracks(it) } ?: navigateToFolders()
            }
            NavigationLevel.SEARCH -> {
                if (currentSearchQuery.isNotEmpty()) {
                    performSearch(currentSearchQuery)
                } else {
                    navigateToRoot()
                }
            }
            NavigationLevel.NAVIDROME_ROOT -> navigateToNavidromeRoot()
            NavigationLevel.NAVIDROME_ARTIST -> {
                currentNavidromeArtist?.let { navigateToNavidromeArtist(it) } ?: navigateToNavidromeRoot()
            }
            NavigationLevel.NAVIDROME_ALBUM -> {
                currentNavidromeAlbum?.let { navigateToNavidromeAlbum(it) } ?: navigateToNavidromeRoot()
            }
        }
    }

    /**
     * Rafraîchit uniquement la liste des tracks affichée.
     * Utilisé après édition de tags simples (titre, numéro de piste).
     */
    private fun refreshTrackList() {
        when (currentLevel) {
            NavigationLevel.TRACKS -> {
                currentAlbum?.let { album ->
                    trackAdapter.submitList(null)
                    trackAdapter.submitList(album.tracks)
                    displayedTracks = album.tracks
                }
            }
            NavigationLevel.PLAYLIST_TRACKS -> {
                currentPlaylist?.let { playlist ->
                    val tracks = getTracksForPlaylist(playlist)
                    trackAdapter.submitList(null)
                    trackAdapter.submitList(tracks)
                    displayedTracks = tracks
                }
            }
            NavigationLevel.FOLDER_TRACKS -> {
                currentFolder?.let { folder ->
                    trackAdapter.submitList(null)
                    trackAdapter.submitList(folder.tracks)
                    displayedTracks = folder.tracks
                }
            }
            NavigationLevel.SEARCH -> {
                // Relance la recherche pour rafraîchir
                if (currentSearchQuery.isNotEmpty()) {
                    performSearch(currentSearchQuery)
                }
            }
            else -> {
                // Pour les autres niveaux, ne fait rien
            }
        }
    }

    private fun navigateToRoot() {
        currentLevel = NavigationLevel.ROOT
        currentArtist = null
        currentAlbum = null
        currentPlaylist = null
        currentFolder = null
        currentNavidromeArtist = null
        currentNavidromeAlbum = null
        folderPathHistory.clear()
        currentSearchQuery = ""
        isInAlbumArtistMode = false

        updateBreadcrumbs()
        updateStatsForRoot()

        // Clear current adapter before switching
        clearCurrentAdapter()
        resetToLinearLayoutManager()
        rootOptionAdapter.displayMode = preferences.rootDisplayMode
        contentList.adapter = rootOptionAdapter

        val options = mutableListOf(
            RootOption(
                id = RootOption.OPTION_ARTISTS,
                title = getString(R.string.music_browse_artists),
                subtitle = resources.getQuantityString(
                    R.plurals.music_artist_count,
                    MusicLibrary.getTotalArtistCount(),
                    MusicLibrary.getTotalArtistCount()
                ),
                iconResId = android.R.drawable.ic_menu_sort_by_size
            ),
            RootOption(
                id = RootOption.OPTION_ALBUM_ARTISTS,
                title = getString(R.string.music_browse_album_artists),
                subtitle = resources.getQuantityString(
                    R.plurals.music_artist_count,
                    MusicLibrary.getTotalAlbumArtistCount(),
                    MusicLibrary.getTotalAlbumArtistCount()
                ),
                iconResId = R.drawable.ic_music_artist
            ),
            RootOption(
                id = RootOption.OPTION_ALL_ALBUMS,
                title = getString(R.string.music_browse_all_albums),
                subtitle = resources.getQuantityString(
                    R.plurals.music_album_count,
                    MusicLibrary.getTotalAlbumCount(),
                    MusicLibrary.getTotalAlbumCount()
                ),
                iconResId = android.R.drawable.ic_menu_gallery
            ),
            RootOption(
                id = RootOption.OPTION_FOLDERS,
                title = getString(R.string.music_browse_folders),
                subtitle = resources.getQuantityString(
                    R.plurals.music_folder_count,
                    MusicLibrary.getTotalFolderCount(),
                    MusicLibrary.getTotalFolderCount()
                ),
                iconResId = R.drawable.ic_folder_open
            ),
            RootOption(
                id = RootOption.OPTION_SEARCH,
                title = getString(R.string.music_browse_search),
                subtitle = getString(R.string.music_browse_search_desc),
                iconResId = android.R.drawable.ic_menu_search
            ),
            RootOption(
                id = RootOption.OPTION_PLAYLISTS,
                title = getString(R.string.music_browse_playlists),
                subtitle = getString(R.string.music_browse_playlists_desc),
                iconResId = android.R.drawable.ic_menu_my_calendar
            )
        )

        // Add Favorite Artists option if there are favorites
        val favoriteArtistsCount = ArtistCustomizationManager.getFavoriteArtistsCount()
        if (favoriteArtistsCount > 0) {
            options.add(2, RootOption(
                id = RootOption.OPTION_FAVORITE_ARTISTS,
                title = getString(R.string.music_browse_favorite_artists),
                subtitle = resources.getQuantityString(
                    R.plurals.music_artist_count,
                    favoriteArtistsCount,
                    favoriteArtistsCount
                ),
                iconResId = android.R.drawable.btn_star_big_on
            ))
        }

        // Add Favorite Albums option if there are favorites
        val favoriteAlbumsCount = AlbumFavoritesManager.getFavoritesCount()
        if (favoriteAlbumsCount > 0) {
            // Insert after ALL_ALBUMS (or after FAVORITE_ARTISTS if present)
            val insertIndex = options.indexOfFirst { it.id == RootOption.OPTION_SEARCH }
            options.add(insertIndex, RootOption(
                id = RootOption.OPTION_FAVORITE_ALBUMS,
                title = getString(R.string.music_browse_favorite_albums),
                subtitle = resources.getQuantityString(
                    R.plurals.music_album_count,
                    favoriteAlbumsCount,
                    favoriteAlbumsCount
                ),
                iconResId = android.R.drawable.btn_star_big_on
            ))
        }

        // Add Navidrome option if configured
        val navUrl = preferences.navidromeServerUrl
        if (navUrl.isNotBlank()) {
            options.add(RootOption(
                id = RootOption.OPTION_NAVIDROME,
                title = getString(R.string.navidrome),
                subtitle = getString(R.string.navidrome_browse_desc),
                iconResId = R.drawable.ic_cloud_music
            ))
        }

        // Filter out hidden options
        val visibleOptions = options.filter { preferences.isRootOptionVisible(it.id) }
        val orderedOptions = reorderRootOptions(visibleOptions)
        rootOptions = orderedOptions.toMutableList()
        rootOptionAdapter.submitList(orderedOptions)

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    private fun navigateToArtists(scrollToArtist: Artist? = null) {
        currentLevel = NavigationLevel.ARTISTS
        currentArtist = null
        currentAlbum = null

        updateBreadcrumbs()
        updateStatsForArtists()

        // Clear current adapter before switching
        clearCurrentAdapter()
        setupLayoutManagerForArtists()
        contentList.adapter = artistAdapter
        val artists = MusicLibrary.getArtists()
        artistAdapter.submitList(artists)
        updateArtistAdapterFavorites()

        // Scroll to the artist we came from
        scrollToArtist?.let { artist ->
            val position = artists.indexOfFirst { it.name == artist.name }
            if (position >= 0) {
                contentList.post { contentList.scrollToPosition(position) }
            }
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    private fun navigateToAlbumArtists(scrollToArtist: Artist? = null) {
        currentLevel = NavigationLevel.ALBUM_ARTISTS
        currentArtist = null
        currentAlbum = null
        isInAlbumArtistMode = true

        updateBreadcrumbs()
        updateStatsForAlbumArtists()

        // Clear current adapter before switching
        clearCurrentAdapter()
        setupLayoutManagerForArtists()
        contentList.adapter = artistAdapter
        val albumArtists = MusicLibrary.getAlbumArtists()
        artistAdapter.submitList(albumArtists)
        updateArtistAdapterFavorites()

        // Scroll to the artist we came from
        scrollToArtist?.let { artist ->
            val position = albumArtists.indexOfFirst { it.name == artist.name }
            if (position >= 0) {
                contentList.post { contentList.scrollToPosition(position) }
            }
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    private fun navigateToFavoriteArtists(scrollToArtist: Artist? = null) {
        currentLevel = NavigationLevel.FAVORITE_ARTISTS
        currentArtist = null
        currentAlbum = null
        isInAlbumArtistMode = false

        updateBreadcrumbs()

        // Clear current adapter before switching
        clearCurrentAdapter()
        setupLayoutManagerForArtists()
        contentList.adapter = artistAdapter

        // Get favorite artist names and find corresponding Artist objects
        val favoriteArtistNames = ArtistCustomizationManager.getFavoriteArtistNames()
        val allArtists = MusicLibrary.getArtists()
        val favoriteArtists = allArtists.filter { artist ->
            favoriteArtistNames.any { it.equals(artist.name, ignoreCase = true) }
        }

        artistAdapter.submitList(favoriteArtists)
        updateArtistAdapterFavorites()

        statsInfo.text = resources.getQuantityString(
            R.plurals.music_artist_count,
            favoriteArtists.size,
            favoriteArtists.size
        )

        // Scroll to the artist we came from
        scrollToArtist?.let { artist ->
            val position = favoriteArtists.indexOfFirst { it.name == artist.name }
            if (position >= 0) {
                contentList.post { contentList.scrollToPosition(position) }
            }
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    private fun navigateToAllAlbums(scrollToAlbum: Album? = null) {
        currentLevel = NavigationLevel.ALL_ALBUMS
        currentArtist = null
        currentAlbum = null

        updateBreadcrumbs()
        updateStatsForAllAlbums()

        // Clear current adapter before switching
        clearCurrentAdapter()
        setupLayoutManagerForAlbums()
        contentList.adapter = albumAdapter
        val albums = MusicLibrary.getAllAlbums()
        val albumListItems = albums.map { AlbumListItem.AlbumItem(it) }
        albumAdapter.submitList(albumListItems)
        updateAlbumAdapterFavorites()

        // Scroll to the album we came from
        scrollToAlbum?.let { album ->
            val position = albums.indexOfFirst { it.name == album.name && it.artist == album.artist }
            if (position >= 0) {
                contentList.post { contentList.scrollToPosition(position) }
            }
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()

        // Vérification rapide des années manquantes en arrière-plan si le tri est par date.
        // Évite de devoir faire un deep scan complet juste pour que le tri par date fonctionne.
        val currentSort = MusicLibrary.currentAlbumSortOrder
        if ((currentSort == MusicLibrary.AlbumSortOrder.YEAR_ASC || currentSort == MusicLibrary.AlbumSortOrder.YEAR_DESC)
            && albums.any { it.year == null }) {
            val albumsNeedingYear = albums.filter { it.year == null }.map { it.id }.toSet()
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                MusicLibrary.fixMissingYears()
                MusicLibrary.setAlbumSortOrder(MusicLibrary.currentAlbumSortOrder)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (currentLevel == NavigationLevel.ALL_ALBUMS) {
                        val refreshed = MusicLibrary.getAllAlbums().map { AlbumListItem.AlbumItem(it) }
                        albumAdapter.submitList(refreshed) {
                            refreshed.forEachIndexed { pos, item ->
                                if (item.album.id in albumsNeedingYear) {
                                    albumAdapter.notifyItemChanged(pos)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun navigateToFavoriteAlbums(scrollToAlbum: Album? = null) {
        currentLevel = NavigationLevel.FAVORITE_ALBUMS
        currentArtist = null
        currentAlbum = null

        updateBreadcrumbs()

        // Get favorite albums from library
        val favoriteAlbumKeys = AlbumFavoritesManager.getFavorites()
        val allAlbums = MusicLibrary.getAllAlbums()
        val favoriteAlbums = allAlbums.filter { album ->
            favoriteAlbumKeys.any { (artist, albumName) ->
                album.artist.equals(artist, ignoreCase = true) &&
                album.name.equals(albumName, ignoreCase = true)
            }
        }

        statsInfo.text = resources.getQuantityString(
            R.plurals.music_album_count,
            favoriteAlbums.size,
            favoriteAlbums.size
        )

        // Clear current adapter before switching
        clearCurrentAdapter()
        setupLayoutManagerForAlbums()
        contentList.adapter = albumAdapter
        val albumListItems = favoriteAlbums.map { AlbumListItem.AlbumItem(it) }
        albumAdapter.submitList(albumListItems)
        updateAlbumAdapterFavorites()

        // Scroll to the album we came from
        scrollToAlbum?.let { album ->
            val position = favoriteAlbums.indexOfFirst { it.name == album.name && it.artist == album.artist }
            if (position >= 0) {
                contentList.post { contentList.scrollToPosition(position) }
            }
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    /**
     * Navigate to the root of the folder tree (shows all top-level folders).
     * @param scrollToFolder If set, the list will scroll to this folder after rendering.
     */
    private fun navigateToFolders(scrollToFolder: Folder? = null) {
        currentLevel = NavigationLevel.FOLDERS
        currentFolder = null
        folderPathHistory.clear()

        updateBreadcrumbs()

        val rootFolder = MusicLibrary.getFolderTree()
        if (rootFolder == null || rootFolder.subfolders.isEmpty()) {
            statsInfo.text = resources.getQuantityString(R.plurals.music_folder_count, 0, 0)
            clearCurrentAdapter()
            resetToLinearLayoutManager()
            folderAdapter.submitList(emptyList())
            contentList.adapter = folderAdapter
        } else {
            val folderCount = rootFolder.subfolders.size
            statsInfo.text = resources.getQuantityString(R.plurals.music_folder_count, folderCount, folderCount)

            clearCurrentAdapter()
            setupLayoutManagerForFolders()
            folderAdapter.displayMode = preferences.folderDisplayMode
            contentList.adapter = folderAdapter
            folderAdapter.submitList(rootFolder.subfolders)

            scrollToFolder?.let { target ->
                val position = rootFolder.subfolders.indexOfFirst { it.path == target.path }
                if (position >= 0) contentList.post { contentList.scrollToPosition(position) }
            }
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    /**
     * Navigate into a specific folder.
     * Shows subfolders and direct tracks together (like a file explorer).
     * @param scrollToFolder If set, the list will scroll to this folder after rendering.
     */
    private fun navigateToFolder(folder: Folder, scrollToFolder: Folder? = null) {
        // If the folder only has tracks (no subfolders), go directly to FOLDER_TRACKS
        if (folder.subfolders.isEmpty() && folder.tracks.isNotEmpty()) {
            navigateToFolderTracks(folder)
            return
        }

        currentLevel = NavigationLevel.FOLDERS
        currentFolder = folder
        folderPathHistory.add(folder)

        updateBreadcrumbs()

        // Build info text
        val parts = mutableListOf<String>()
        if (folder.subfolderCount > 0) {
            parts.add(resources.getQuantityString(R.plurals.music_subfolder_count, folder.subfolderCount, folder.subfolderCount))
        }
        if (folder.tracks.isNotEmpty()) {
            parts.add(resources.getQuantityString(R.plurals.music_track_count_plural, folder.trackCount, folder.trackCount))
        }
        statsInfo.text = parts.joinToString(" • ")

        clearCurrentAdapter()

        if (folder.subfolders.isNotEmpty() && folder.tracks.isNotEmpty()) {
            // Mixed content: show subfolders first, then direct tracks in the same list
            setupLayoutManagerForFolders()
            folderContentAdapter.displayMode = preferences.folderDisplayMode
            contentList.adapter = folderContentAdapter
            val mixedItems = buildList {
                folder.subfolders.forEach { add(FolderContentItem.FolderItem(it)) }
                folder.tracks.forEach { add(FolderContentItem.TrackItem(it)) }
            }
            folderContentAdapter.submitList(mixedItems)
            // Expose the direct tracks so playTrack() uses them as playlist context
            displayedTracks = folder.tracks

            scrollToFolder?.let { target ->
                val position = mixedItems.indexOfFirst {
                    it is FolderContentItem.FolderItem && it.folder.path == target.path
                }
                if (position >= 0) contentList.post { contentList.scrollToPosition(position) }
            }
        } else if (folder.subfolders.isNotEmpty()) {
            // Only subfolders, no direct tracks
            setupLayoutManagerForFolders()
            folderAdapter.displayMode = preferences.folderDisplayMode
            contentList.adapter = folderAdapter
            folderAdapter.submitList(folder.subfolders)

            scrollToFolder?.let { target ->
                val position = folder.subfolders.indexOfFirst { it.path == target.path }
                if (position >= 0) contentList.post { contentList.scrollToPosition(position) }
            }
        } else {
            // Only tracks, no subfolders
            navigateToFolderTracks(folder)
            return
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    // ==================== FOLDER TRACKS SORT ====================

    private fun applyFolderTrackSort(tracks: List<MusicTrack>): List<MusicTrack> {
        return when (folderTrackSort) {
            FolderTrackSort.BY_NAME -> tracks.sortedBy { it.title.lowercase() }
            FolderTrackSort.BY_TRACK_NUMBER -> tracks.sortedWith(
                compareBy(
                    // Tracks without a number (null or 0) go to the end
                    { it.trackNumber == null || it.trackNumber == 0 },
                    { it.trackNumber ?: Int.MAX_VALUE },
                    { it.title.lowercase() }
                )
            )
        }
    }

    private fun setFolderTrackSort(sort: FolderTrackSort) {
        if (folderTrackSort == sort) return
        folderTrackSort = sort
        updateFolderSortBarButtons()
        // Re-sort the current track list in place
        val resorted = applyFolderTrackSort(displayedTracks)
        trackAdapter.submitList(resorted)
        displayedTracks = resorted
    }

    private fun updateFolderSortBarButtons() {
        val ta = theme.obtainStyledAttributes(intArrayOf(R.attr.a2uMusicAccent))
        val accentColor = ta.getColor(0, ContextCompat.getColor(this, R.color.music_accent))
        ta.recycle()
        val secondaryColor = ContextCompat.getColor(this, R.color.music_text_secondary)

        val isName = folderTrackSort == FolderTrackSort.BY_NAME
        btnSortByName.setTextColor(if (isName) accentColor else secondaryColor)
        btnSortByName.text = if (isName) "↑ ${getString(R.string.music_folder_sort_by_name)}"
                             else getString(R.string.music_folder_sort_by_name)
        btnSortByTrackNumber.setTextColor(if (!isName) accentColor else secondaryColor)
        btnSortByTrackNumber.text = if (!isName) "↑ ${getString(R.string.music_folder_sort_by_track)}"
                                    else getString(R.string.music_folder_sort_by_track)
    }

    /**
     * Navigate to show tracks of a specific folder.
     */
    private fun navigateToFolderTracks(folder: Folder) {
        currentLevel = NavigationLevel.FOLDER_TRACKS
        currentFolder = folder

        // Add to history if not already there
        if (folderPathHistory.lastOrNull() != folder) {
            folderPathHistory.add(folder)
        }

        updateBreadcrumbs()

        val tracks = folder.tracks

        clearCurrentAdapter()
        // Show the sort bar instead of statsInfo for folder tracks
        statsInfo.visibility = View.GONE
        folderTracksSortBar.visibility = View.VISIBLE
        folderTracksCount.text = resources.getQuantityString(R.plurals.music_track_count_plural, tracks.size, tracks.size)
        updateFolderSortBarButtons()

        resetToLinearLayoutManager()
        trackAdapter.displayMode = preferences.trackDisplayMode
        contentList.adapter = trackAdapter
        val sortedTracks = applyFolderTrackSort(tracks)
        // Synchroniser l'indicateur avec la track en cours avant de soumettre la liste
        trackAdapter.setCurrentlyPlaying(MusicPlaybackHolder.getCurrentTrack()?.id ?: -1L)
        trackAdapter.submitList(sortedTracks)
        updateAdapterFavorites(sortedTracks)
        displayedTracks = sortedTracks

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    /**
     * Play all tracks in a folder (recursively).
     */
    private fun playFolder(folder: Folder, shuffle: Boolean) {
        val tracks = folder.getAllTracksRecursive()
        if (tracks.isEmpty()) return
        playTracks(tracks, shuffle)
    }

    /**
     * Setup the layout manager for folders based on display mode.
     */
    private fun setupLayoutManagerForFolders() {
        when (preferences.folderDisplayMode) {
            FolderDisplayMode.LIST, FolderDisplayMode.COMPACT -> resetToLinearLayoutManager()
        }
    }

    private fun navigateToSearch() {
        currentLevel = NavigationLevel.SEARCH
        currentSearchQuery = ""

        updateBreadcrumbs()
        statsInfo.text = getString(R.string.music_browse_search_desc)

        // Clear current adapter before switching
        clearCurrentAdapter()
        resetToLinearLayoutManager()
        trackAdapter.displayMode = preferences.trackDisplayMode
        contentList.adapter = trackAdapter
        trackAdapter.submitList(emptyList())

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()

        // Show search dialog
        showSearchDialog()
    }

    private fun showSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_playlist_name, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.playlist_name_input)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.playlist_name_layout)

        inputLayout.hint = getString(R.string.music_search_hint)
        nameInput.setText(currentSearchQuery)

        AlertDialog.Builder(this)
            .setTitle(R.string.music_browse_search)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val query = nameInput.text?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
            }
            .setNegativeButton(R.string.music_cancel, null)
            .show()
    }

    private fun performSearch(query: String) {
        currentSearchQuery = query
        val queryLower = query.lowercase()

        // Search in all tracks
        val results = MusicLibrary.getAllTracks().filter { track ->
            track.title.lowercase().contains(queryLower) ||
            track.artist.lowercase().contains(queryLower) ||
            track.album.lowercase().contains(queryLower)
        }

        updateBreadcrumbs()
        statsInfo.text = getString(R.string.music_search_results, results.size)

        trackAdapter.submitList(results)

        // Store displayed tracks for playback (will be used when user clicks a track)
        displayedTracks = results

        if (results.isEmpty()) {
            Snackbar.make(contentList, R.string.music_search_no_results, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun navigateToArtist(artist: Artist, scrollToAlbum: Album? = null) {
        // Si on est dans le contexte Navidrome, chercher l'artiste Navidrome correspondant
        if (currentLevel == NavigationLevel.NAVIDROME_ROOT) {
            val navArtist = NavidromeLibrary.artists.find { it.name == artist.name }
            if (navArtist != null) {
                navigateToNavidromeArtist(navArtist)
                return
            }
        }

        // Sauvegarder le contexte parent seulement si on vient d'une liste d'artistes
        // (pas si on revient en arrière depuis TRACKS)
        if (currentLevel == NavigationLevel.ARTISTS ||
            currentLevel == NavigationLevel.ALBUM_ARTISTS ||
            currentLevel == NavigationLevel.FAVORITE_ARTISTS) {
            parentArtistsLevel = currentLevel
        }

        currentLevel = NavigationLevel.ALBUMS
        currentArtist = artist
        currentAlbum = null

        updateBreadcrumbs()
        updateStatsForAlbums(artist)

        // Clear current adapter before switching
        clearCurrentAdapter()
        setupLayoutManagerForAlbums()
        contentList.adapter = albumAdapter

        // Create list with AllTracksItem at the top, then albums
        val allTracks = artist.albums.flatMap { it.tracks }

        // Get artist custom icon if exists
        val customization = ArtistCustomizationManager.getCustomization(artist.name)
        val artistCustomIconPath = customization?.iconPath?.takeIf { File(it).exists() }

        // Get first 4 album art URIs for the 2x2 grid
        val albumArtUris = artist.albums
            .mapNotNull { it.albumArtUri?.toString() }
            .take(4)

        val allTracksItem = AlbumListItem.AllTracksItem(
            artistName = artist.name,
            tracks = allTracks,
            artistCustomIconPath = artistCustomIconPath,
            albumArtUris = albumArtUris
        )
        val albumListItems: List<AlbumListItem> = listOf(allTracksItem) +
            artist.albums.map { AlbumListItem.AlbumItem(it) }

        albumAdapter.submitList(albumListItems)
        updateAlbumAdapterFavorites()

        // Scroll to the album we came from (offset by 1 for AllTracksItem)
        scrollToAlbum?.let { album ->
            val position = artist.albums.indexOfFirst { it.name == album.name }
            if (position >= 0) {
                contentList.post { contentList.scrollToPosition(position + 1) }
            }
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()

        // Auto-check des années manquantes en arrière-plan si le tri est par date
        val currentSort = MusicLibrary.currentAlbumSortOrder
        if ((currentSort == MusicLibrary.AlbumSortOrder.YEAR_ASC || currentSort == MusicLibrary.AlbumSortOrder.YEAR_DESC)
            && artist.albums.any { it.year == null }) {
            // Snapshot des IDs d'albums sans année AVANT la correction (pour forcer le rebind après)
            val albumsNeedingYear = artist.albums.filter { it.year == null }.map { it.id }.toSet()
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                MusicLibrary.fixMissingYears()
                MusicLibrary.setAlbumSortOrder(MusicLibrary.currentAlbumSortOrder)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (currentLevel == NavigationLevel.ALBUMS && currentArtist == artist) {
                        val refreshedAllTracks = artist.albums.flatMap { it.tracks }
                        val refreshedCustomization = ArtistCustomizationManager.getCustomization(artist.name)
                        val refreshedIconPath = refreshedCustomization?.iconPath?.takeIf { File(it).exists() }
                        val refreshedArtUris = artist.albums.mapNotNull { it.albumArtUri?.toString() }.take(4)
                        val refreshedAllTracksItem = AlbumListItem.AllTracksItem(
                            artistName = artist.name,
                            tracks = refreshedAllTracks,
                            artistCustomIconPath = refreshedIconPath,
                            albumArtUris = refreshedArtUris
                        )
                        val refreshed: List<AlbumListItem> = listOf(refreshedAllTracksItem) +
                            artist.albums.map { AlbumListItem.AlbumItem(it) }
                        // submitList avec callback : après que DiffUtil applique les mouvements,
                        // forcer le rebind des items dont l'année vient d'être mise à jour.
                        // (DiffUtil ne détecte pas le changement car album.year est muté en place
                        //  sur le même objet — les deux côtés du diff voient déjà la nouvelle valeur)
                        albumAdapter.submitList(refreshed) {
                            refreshed.forEachIndexed { pos, item ->
                                if (item is AlbumListItem.AlbumItem && item.album.id in albumsNeedingYear) {
                                    albumAdapter.notifyItemChanged(pos)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun navigateToArtistAllTracks(tracks: List<MusicTrack>) {
        // Navigate to a view showing all tracks from the current artist
        currentLevel = NavigationLevel.TRACKS
        currentAlbum = null  // Not viewing a specific album

        updateBreadcrumbs()
        statsInfo.text = resources.getQuantityString(
            R.plurals.music_track_count_plural, tracks.size, tracks.size
        )

        // Clear current adapter before switching
        clearCurrentAdapter()
        resetToLinearLayoutManager()
        trackAdapter.displayMode = preferences.trackDisplayMode
        contentList.adapter = trackAdapter
        // Synchroniser l'indicateur avec la track en cours avant de soumettre la liste
        trackAdapter.setCurrentlyPlaying(MusicPlaybackHolder.getCurrentTrack()?.id ?: -1L)
        trackAdapter.submitList(tracks)
        updateAdapterFavorites(tracks)

        // Store displayed tracks for playback
        displayedTracks = tracks

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    private fun navigateToAlbum(album: Album) {
        // Si on est dans le contexte Navidrome, chercher l'album Navidrome correspondant
        if (currentLevel == NavigationLevel.NAVIDROME_ARTIST) {
            val navAlbum = NavidromeLibrary.albums.find { it.id.hashCode().toLong() == album.id }
            if (navAlbum != null) {
                navigateToNavidromeAlbum(navAlbum)
                return
            }
        }

        // Sauvegarder le contexte parent avant de changer de niveau
        parentAlbumsLevel = currentLevel

        currentLevel = NavigationLevel.TRACKS
        currentAlbum = album

        updateBreadcrumbs()
        updateStatsForTracks(album)

        // Clear current adapter before switching
        clearCurrentAdapter()
        resetToLinearLayoutManager()
        trackAdapter.displayMode = preferences.trackDisplayMode
        contentList.adapter = trackAdapter
        // Synchroniser l'indicateur avec la track en cours avant de soumettre la liste
        trackAdapter.setCurrentlyPlaying(MusicPlaybackHolder.getCurrentTrack()?.id ?: -1L)
        trackAdapter.submitList(album.tracks)
        updateAdapterFavorites(album.tracks)

        // Store displayed tracks for playback (will be used when user clicks a track)
        displayedTracks = album.tracks

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()

        // Vérification automatique des tags POPM pour les albums de ≤ 100 pistes
        triggerPopmVerificationIfNeeded(album.tracks)

        // Vérifier si des numéros de piste peuvent être extraits des noms de fichiers
        checkAndOfferTrackNumberFix(album)
    }

    private data class TrackNumberExtraction(val number: Int, val cleanedName: String)

    private fun extractTrackNumberFromName(name: String): TrackNumberExtraction? {
        val trimmed = name.trim()
        val match = Regex("^(\\d{1,3})\\s*([\\-._]+\\s*|\\s+)(.+)$").find(trimmed) ?: return null
        val number = match.groupValues[1].toIntOrNull() ?: return null
        val cleaned = match.groupValues[3].trim()
        if (cleaned.isBlank()) return null
        return TrackNumberExtraction(number, cleaned)
    }

    private fun computeCleanedTitle(
        title: String,
        baseName: String,
        trackNumber: Int,
        cleanedBaseName: String?
    ): String? {
        val titleExtraction = extractTrackNumberFromName(title)
        if (titleExtraction != null && titleExtraction.number == trackNumber) {
            return titleExtraction.cleanedName
        }
        if (!cleanedBaseName.isNullOrBlank()) {
            val normalizedTitle = title.trim()
            if (normalizedTitle.isBlank() || normalizedTitle.equals(baseName, ignoreCase = true)) {
                return cleanedBaseName
            }
        }
        return null
    }

    /**
     * Vérifie si l'album contient des pistes sans numéro de piste mais dont
     * le nom de fichier commence par un numéro. Propose à l'utilisateur
     * d'écrire automatiquement ces numéros dans les tags ID3.
     */
    private fun checkAndOfferTrackNumberFix(album: Album) {
        // Vérifier si la fonctionnalité est activée dans les préférences
        if (!preferences.suggestTrackNumbers) {
            return
        }

        val artistName = currentArtist?.name ?: album.artist
        val albumName = album.name

        // Vérifier si cet album a déjà été traité (ignoré définitivement)
        if (preferences.isAlbumTrackNumberChecked(artistName, albumName)) {
            return
        }

        // Trouver les pistes sans trackNumber mais avec numéro dans le nom de fichier
        val tracksToFix = album.tracks.mapNotNull { track ->
            if (track.trackNumber != null) {
                // A déjà un numéro de piste, ignorer
                null
            } else {
                // Essayer d'extraire le numéro du nom de fichier
                val filePath = track.filePath ?: return@mapNotNull null
                val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
                val baseName = fileName.substringBeforeLast('.')
                val extraction = extractTrackNumberFromName(baseName)
                extraction?.let { Pair(track, it.number) }
            }
        }

        // Si aucune piste à corriger, ne rien faire (et marquer comme traité)
        if (tracksToFix.isEmpty()) {
            preferences.markAlbumTrackNumberChecked(artistName, albumName)
            return
        }

        // Afficher le dialogue de proposition avec 3 options
        AlertDialog.Builder(this)
            .setTitle(R.string.music_auto_track_number_title)
            .setMessage(getString(R.string.music_auto_track_number_message, tracksToFix.size))
            .setPositiveButton(R.string.music_auto_track_number_apply) { _, _ ->
                // Appliquer et marquer comme traité
                preferences.markAlbumTrackNumberChecked(artistName, albumName)
                applyAutoTrackNumbers(album, tracksToFix)
            }
            .setNeutralButton(R.string.music_auto_track_number_later, null)  // Ne fait rien, reproposera
            .setNegativeButton(R.string.music_auto_track_number_never) { _, _ ->
                // Marquer comme traité sans appliquer (ne reproposera plus)
                preferences.markAlbumTrackNumberChecked(artistName, albumName)
            }
            .show()
    }

    /**
     * Applique les numéros de piste extraits des noms de fichiers aux tags ID3.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun applyAutoTrackNumbers(album: Album, tracksToFix: List<Pair<MusicTrack, Int>>) {
        lifecycleScope.launch {
            var successCount = 0
            val totalCount = tracksToFix.size
            val oldTracks = tracksToFix.map { it.first }
            val filePaths = oldTracks.mapNotNull { it.filePath }
            val renamedPaths = mutableMapOf<String, String>()
            val updatedPaths = mutableSetOf<String>()

            // Vérifier si une piste à modifier est en lecture
            val currentTrackPath = MusicPlaybackHolder.getCurrentTrack()?.filePath
            val trackInPlayback = oldTracks.any { it.filePath == currentTrackPath }
            if (trackInPlayback) {
                runOnUiThread {
                    Snackbar.make(
                        contentList,
                        R.string.music_tags_pending,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                // Mettre en attente les éditions de numéro de piste
                for ((track, trackNumber) in tracksToFix) {
                    track.filePath?.let { path ->
                        val baseName = File(path).nameWithoutExtension
                        val extracted = extractTrackNumberFromName(baseName)
                        val cleanedBaseName = extracted?.takeIf { it.number == trackNumber }?.cleanedName
                        val existingTags = MusicTagEditor.readTags(track)
                        val titleSource = existingTags?.title ?: track.title
                        val cleanedTitle = computeCleanedTitle(titleSource, baseName, trackNumber, cleanedBaseName)
                        PendingTagEditManager.addPendingEdit(
                            context = this@MusicPlayerActivity,
                            filePath = path,
                            trackNumber = trackNumber.toString(),
                            title = cleanedTitle
                        )
                    }
                }
                return@launch
            }

            for ((track, trackNumber) in tracksToFix) {
                try {
                    val filePath = track.filePath ?: continue
                    val file = File(filePath)
                    val baseName = file.nameWithoutExtension
                    val extension = file.extension
                    val extractedFromFile = extractTrackNumberFromName(baseName)
                    val cleanedBaseName = extractedFromFile?.takeIf { it.number == trackNumber }?.cleanedName
                    // Lire les tags existants
                    val existingTags = MusicTagEditor.readTags(track)
                    if (existingTags != null) {
                        val cleanedTitle = computeCleanedTitle(
                            existingTags.title,
                            baseName,
                            trackNumber,
                            cleanedBaseName
                        )
                        // Créer les nouveaux tags avec le numéro de piste
                        val newTags = existingTags.copy(
                            title = cleanedTitle ?: existingTags.title,
                            trackNumber = trackNumber.toString()
                        )
                        // Écrire les tags
                        val success = MusicTagEditor.writeTags(this@MusicPlayerActivity, track, newTags)
                        if (success) {
                            successCount++
                            var updatedPath = filePath
                            if (!cleanedBaseName.isNullOrBlank() && cleanedBaseName != baseName) {
                                val newFileName = if (extension.isNotBlank()) {
                                    "$cleanedBaseName.$extension"
                                } else {
                                    cleanedBaseName
                                }
                                val renamedPath = MusicTagEditor.renameAudioFile(
                                    this@MusicPlayerActivity,
                                    filePath,
                                    newFileName
                                )
                                if (renamedPath != null) {
                                    renamedPaths[filePath] = renamedPath
                                    updatedPath = renamedPath
                                }
                            }
                            updatedPaths.add(updatedPath)
                        }
                    }
                } catch (_: Exception) {
                    // Ignorer les erreurs individuelles
                }
            }

            // Afficher le résultat et rafraîchir la vue
            if (successCount > 0) {
                // Rescanner les pistes modifiées par filePath
                val pathsToScan = if (updatedPaths.isNotEmpty()) {
                    updatedPaths.toList()
                } else {
                    filePaths
                }
                val updatedTracksByPath = MusicScanner.scanTracksByPaths(this@MusicPlayerActivity, pathsToScan)
                runOnUiThread {
                    Snackbar.make(
                        contentList,
                        getString(R.string.music_auto_track_number_success, successCount, totalCount),
                        Snackbar.LENGTH_LONG
                    ).show()

                    if (updatedTracksByPath.isNotEmpty()) {
                        // Matcher ancien/nouveau par filePath
                        val updates = oldTracks.mapNotNull { old ->
                            old.filePath?.let { path ->
                                val lookupPath = renamedPaths[path] ?: path
                                updatedTracksByPath[lookupPath]?.let { new -> old to new }
                            }
                        }
                        MusicLibrary.updateTracks(updates)
                        MusicPlaybackHolder.updateTracksInPlaylist(updates)
                        // Met à jour le cache de la bibliothèque organisée
                        lifecycleScope.launch { MusicLibrary.saveToCache(this@MusicPlayerActivity) }
                        refreshCurrentView()
                    }
                }
            } else {
                runOnUiThread {
                    Snackbar.make(
                        contentList,
                        R.string.music_auto_track_number_error,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun navigateToPlaylists(scrollToPlaylist: Playlist? = null) {
        currentLevel = NavigationLevel.PLAYLISTS
        currentPlaylist = null

        updateBreadcrumbs()
        statsInfo.text = getString(R.string.music_browse_playlists_desc)

        // Clear current adapter before switching
        clearCurrentAdapter()
        resetToLinearLayoutManager()
        contentList.adapter = playlistAdapter

        // Build playlists list with Favorites
        val playlists = buildPlaylistsList()
        playlistAdapter.submitList(playlists)

        // Scroll to the playlist we came from
        scrollToPlaylist?.let { playlist ->
            val position = playlists.indexOfFirst { it.playlist.id == playlist.id }
            if (position >= 0) {
                contentList.post { contentList.scrollToPosition(position) }
            }
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    private fun navigateToPlaylistTracks(playlist: Playlist) {
        currentLevel = NavigationLevel.PLAYLIST_TRACKS
        currentPlaylist = playlist

        updateBreadcrumbs()

        // Get tracks for this playlist
        val tracks = getTracksForPlaylist(playlist)

        statsInfo.text = resources.getQuantityString(
            R.plurals.music_track_count_plural,
            tracks.size,
            tracks.size
        )

        // Clear current adapter before switching
        clearCurrentAdapter()
        resetToLinearLayoutManager()
        trackAdapter.displayMode = preferences.trackDisplayMode
        contentList.adapter = trackAdapter
        // Synchroniser l'indicateur avec la track en cours avant de soumettre la liste
        trackAdapter.setCurrentlyPlaying(MusicPlaybackHolder.getCurrentTrack()?.id ?: -1L)
        trackAdapter.submitList(tracks)
        updateAdapterFavorites(tracks)

        // Store displayed tracks for playback (will be used when user clicks a track)
        displayedTracks = tracks

        // Vérification automatique des tags POPM pour les playlists de ≤ 100 pistes
        triggerPopmVerificationIfNeeded(tracks)

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    // ========== Navidrome Navigation ==========

    private fun getOrCreateNavidromeClient(): SubsonicApiClient? {
        val url = preferences.navidromeServerUrl
        val user = preferences.navidromeUsername
        val pass = preferences.navidromePassword
        if (url.isBlank() || user.isBlank()) return null
        val existing = navidromeApiClient
        if (existing != null && existing.serverUrl == url) return existing
        val client = SubsonicApiClient(url, user, pass)
        navidromeApiClient = client
        return client
    }

    private fun navigateToNavidromeRoot() {
        currentLevel = NavigationLevel.NAVIDROME_ROOT
        currentNavidromeArtist = null
        currentNavidromeAlbum = null

        updateBreadcrumbs()
        statsInfo.text = getString(R.string.navidrome_loading)

        clearCurrentAdapter()
        setupLayoutManagerForArtists()
        artistAdapter.submitList(emptyList())
        contentList.adapter = artistAdapter

        val client = getOrCreateNavidromeClient()
        if (client == null) {
            statsInfo.text = getString(R.string.navidrome_not_configured)
            return
        }

        lifecycleScope.launch {
            NavidromeLibrary.loadArtists(client)
            if (currentLevel != NavigationLevel.NAVIDROME_ROOT) return@launch
            val artists = NavidromeLibrary.artists.map { a ->
                val artist = Artist(name = a.name, albumCountOverride = a.albumCount)
                val artUri = a.coverArtId?.let { Uri.parse(client.buildCoverArtUrl(it)) }
                if (artUri != null) {
                    artist.albums.add(Album(id = 0L, name = "", artist = a.name, albumArtUri = artUri))
                }
                artist
            }
            val count = artists.size
            statsInfo.text = resources.getQuantityString(R.plurals.music_artist_count, count, count)
            artistAdapter.submitList(artists)
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    private fun navigateToNavidromeArtist(navidromeArtist: NavidromeArtist) {
        currentLevel = NavigationLevel.NAVIDROME_ARTIST
        currentNavidromeArtist = navidromeArtist
        currentNavidromeAlbum = null

        updateBreadcrumbs()
        statsInfo.text = getString(R.string.navidrome_loading)

        clearCurrentAdapter()
        setupLayoutManagerForAlbums()

        // Build album list items (without AllTracksItem for Navidrome)
        val placeholderAlbums: List<AlbumListItem> = NavidromeLibrary.albums
            .filter { it.artistId == navidromeArtist.id }
            .map { navAlbum ->
                AlbumListItem.AlbumItem(Album(
                    id = navAlbum.id.hashCode().toLong(),
                    name = navAlbum.name,
                    artist = navAlbum.artist,
                    albumArtUri = navAlbum.coverArtId?.let {
                        navidromeApiClient?.buildCoverArtUrl(it)?.let { url ->
                            Uri.parse(url)
                        }
                    },
                    year = navAlbum.year,
                    trackCountOverride = navAlbum.songCount
                ))
            }
        contentList.adapter = albumAdapter
        albumAdapter.submitList(placeholderAlbums)

        val client = getOrCreateNavidromeClient()
        if (client == null) {
            statsInfo.text = getString(R.string.navidrome_not_configured)
            return
        }

        lifecycleScope.launch {
            NavidromeLibrary.loadAlbums(client, navidromeArtist.id)
            if (currentLevel != NavigationLevel.NAVIDROME_ARTIST) return@launch
            val albums = NavidromeLibrary.albums.map { navAlbum ->
                AlbumListItem.AlbumItem(Album(
                    id = navAlbum.id.hashCode().toLong(),
                    name = navAlbum.name,
                    artist = navAlbum.artist,
                    albumArtUri = navAlbum.coverArtId?.let {
                        client.buildCoverArtUrl(it).let { url ->
                            Uri.parse(url)
                        }
                    },
                    year = navAlbum.year,
                    trackCountOverride = navAlbum.songCount
                ))
            }
            val count = albums.size
            statsInfo.text = resources.getQuantityString(R.plurals.music_album_count, count, count)
            albumAdapter.submitList(albums)
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    private fun navigateToNavidromeAlbum(navidromeAlbum: NavidromeAlbum) {
        currentLevel = NavigationLevel.NAVIDROME_ALBUM
        currentNavidromeAlbum = navidromeAlbum

        updateBreadcrumbs()
        statsInfo.text = getString(R.string.navidrome_loading)

        clearCurrentAdapter()
        resetToLinearLayoutManager()
        trackAdapter.displayMode = preferences.trackDisplayMode
        trackAdapter.setCurrentlyPlaying(MusicPlaybackHolder.getCurrentTrack()?.id ?: -1L)
        contentList.adapter = trackAdapter

        val client = getOrCreateNavidromeClient()
        if (client == null) {
            statsInfo.text = getString(R.string.navidrome_not_configured)
            return
        }

        lifecycleScope.launch {
            val tracks = NavidromeLibrary.loadTracks(client, navidromeAlbum.id)
            if (currentLevel != NavigationLevel.NAVIDROME_ALBUM) return@launch
            val count = tracks.size
            statsInfo.text = resources.getQuantityString(R.plurals.music_track_count_plural, count, count)
            trackAdapter.submitList(tracks)
            displayedTracks = tracks
        }

        updateDisplayModeIcon()
        updateAlphabetIndexVisibility()
    }

    private fun buildPlaylistsList(): List<PlaylistItem> {
        val playlists = mutableListOf<PlaylistItem>()

        // Favorites playlist (always first)
        val favoritesCount = MusicFavoritesManager.getFavoritesCount()
        playlists.add(
            PlaylistItem(
                playlist = Playlist(
                    id = Playlist.FAVORITES_ID,
                    name = getString(R.string.music_playlist_favorites),
                    isSystemPlaylist = true,
                    iconResId = android.R.drawable.btn_star_big_on
                ),
                trackCount = favoritesCount
            )
        )

        // All Library (automatic playlist - all tracks)
        val allTracks = MusicLibrary.getAllTracks()
        playlists.add(
            PlaylistItem(
                playlist = Playlist(
                    id = Playlist.ALL_LIBRARY_ID,
                    name = getString(R.string.music_playlist_all_library),
                    isSystemPlaylist = true,
                    iconResId = R.drawable.ic_music_note
                ),
                trackCount = allTracks.size
            )
        )

        // Top 50 most played (automatic playlist)
        val topPlayedTracks = MusicStatsManager.getTopPlayedTracks(MusicLibrary.getAllTracks(), 50)
        if (topPlayedTracks.isNotEmpty()) {
            playlists.add(
                PlaylistItem(
                    playlist = Playlist(
                        id = Playlist.TOP_PLAYED_ID,
                        name = getString(R.string.music_playlist_top_played),
                        isSystemPlaylist = true,
                        iconResId = android.R.drawable.ic_menu_recent_history
                    ),
                    trackCount = topPlayedTracks.size
                )
            )
        }

        // Discovery - least played tracks (automatic playlist)
        val leastPlayedTracks = MusicStatsManager.getLeastPlayedTracks(MusicLibrary.getAllTracks())
        playlists.add(
            PlaylistItem(
                playlist = Playlist(
                    id = Playlist.LEAST_PLAYED_ID,
                    name = getString(R.string.music_playlist_discovery),
                    isSystemPlaylist = true,
                    iconResId = android.R.drawable.ic_menu_compass
                ),
                trackCount = leastPlayedTracks.size
            )
        )

        // Custom playlists
        MusicPlaylistManager.getPlaylists().forEach { playlistData ->
            val trackCount = MusicPlaylistManager.getTracksForPlaylist(playlistData.id).size
            playlists.add(
                PlaylistItem(
                    playlist = Playlist(
                        id = playlistData.id,
                        name = playlistData.name,
                        isSystemPlaylist = false,
                        iconResId = R.drawable.ic_playlist
                    ),
                    trackCount = trackCount
                )
            )
        }

        return playlists
    }

    private fun getTracksForPlaylist(playlist: Playlist): List<MusicTrack> {
        return when (playlist.id) {
            Playlist.FAVORITES_ID -> {
                // Get all tracks that are favorites
                MusicLibrary.getAllTracks().filter { MusicFavoritesManager.isFavorite(it) }
            }
            Playlist.ALL_LIBRARY_ID -> {
                // Get all tracks in the library
                MusicLibrary.getAllTracks()
            }
            Playlist.TOP_PLAYED_ID -> {
                // Get top 50 most played tracks
                MusicStatsManager.getTopPlayedTracks(MusicLibrary.getAllTracks(), 50)
            }
            Playlist.LEAST_PLAYED_ID -> {
                // Get least played tracks (discovery mode)
                MusicStatsManager.getLeastPlayedTracks(MusicLibrary.getAllTracks())
            }
            else -> {
                // Get tracks for custom playlist
                MusicPlaylistManager.getTracksForPlaylist(playlist.id)
            }
        }
    }

    private fun clearCurrentAdapter() {
        // Hide the folder tracks sort bar whenever we leave FOLDER_TRACKS
        folderTracksSortBar.visibility = View.GONE
        statsInfo.visibility = View.VISIBLE
        updateRootDragHelper(currentLevel == NavigationLevel.ROOT)
        when (contentList.adapter) {
            rootOptionAdapter -> rootOptionAdapter.submitList(emptyList())
            artistAdapter -> artistAdapter.submitList(emptyList())
            albumAdapter -> albumAdapter.submitList(emptyList())
            folderAdapter -> folderAdapter.submitList(emptyList())
            folderContentAdapter -> folderContentAdapter.submitList(emptyList())
            trackAdapter -> trackAdapter.submitList(emptyList())
            playlistAdapter -> playlistAdapter.submitList(emptyList())
        }
    }

    private fun updateBreadcrumbs() {
        // Clear existing breadcrumbs
        breadcrumbContainer.removeAllViews()
        toolbar.title = ""

        // Build breadcrumb segments based on current navigation level
        val segments = mutableListOf<BreadcrumbSegment>()

        // Root is always first
        segments.add(BreadcrumbSegment(getString(R.string.music_player_title)) { navigateToRoot() })

        when (currentLevel) {
            NavigationLevel.ROOT -> {
                // Only root, no more segments
            }
            NavigationLevel.ARTISTS -> {
                segments.add(BreadcrumbSegment(getString(R.string.music_browse_artists), null))
            }
            NavigationLevel.ALBUM_ARTISTS -> {
                segments.add(BreadcrumbSegment(getString(R.string.music_browse_album_artists), null))
            }
            NavigationLevel.FAVORITE_ARTISTS -> {
                segments.add(BreadcrumbSegment(getString(R.string.music_browse_favorite_artists), null))
            }
            NavigationLevel.ALBUMS -> {
                val parentSection = if (isInAlbumArtistMode) {
                    getString(R.string.music_browse_album_artists)
                } else {
                    getString(R.string.music_browse_artists)
                }
                val artistNav = currentArtist
                if (isInAlbumArtistMode) {
                    segments.add(BreadcrumbSegment(parentSection) { navigateToAlbumArtists() })
                } else {
                    segments.add(BreadcrumbSegment(parentSection) { navigateToArtists() })
                }
                segments.add(BreadcrumbSegment(artistNav?.name ?: "", null))
            }
            NavigationLevel.ALL_ALBUMS -> {
                segments.add(BreadcrumbSegment(getString(R.string.music_browse_all_albums), null))
            }
            NavigationLevel.FAVORITE_ALBUMS -> {
                segments.add(BreadcrumbSegment(getString(R.string.music_browse_favorite_albums), null))
            }
            NavigationLevel.TRACKS -> {
                if (currentArtist != null) {
                    val artistNav = currentArtist
                    val parentSection = if (isInAlbumArtistMode) {
                        getString(R.string.music_browse_album_artists)
                    } else {
                        getString(R.string.music_browse_artists)
                    }
                    if (isInAlbumArtistMode) {
                        segments.add(BreadcrumbSegment(parentSection) { navigateToAlbumArtists() })
                    } else {
                        segments.add(BreadcrumbSegment(parentSection) { navigateToArtists() })
                    }
                    segments.add(BreadcrumbSegment(artistNav?.name ?: "") {
                        artistNav?.let { navigateToArtist(it) }
                    })
                    segments.add(BreadcrumbSegment(currentAlbum?.name ?: "", null))
                } else {
                    segments.add(BreadcrumbSegment(getString(R.string.music_browse_all_albums)) { navigateToAllAlbums() })
                    segments.add(BreadcrumbSegment(currentAlbum?.name ?: "", null))
                }
            }
            NavigationLevel.SEARCH -> {
                if (currentSearchQuery.isNotEmpty()) {
                    segments.add(BreadcrumbSegment(getString(R.string.music_browse_search)) { navigateToSearch() })
                    segments.add(BreadcrumbSegment("\"$currentSearchQuery\"", null))
                } else {
                    segments.add(BreadcrumbSegment(getString(R.string.music_browse_search), null))
                }
            }
            NavigationLevel.PLAYLISTS -> {
                segments.add(BreadcrumbSegment(getString(R.string.music_browse_playlists), null))
            }
            NavigationLevel.PLAYLIST_TRACKS -> {
                segments.add(BreadcrumbSegment(getString(R.string.music_browse_playlists)) { navigateToPlaylists() })
                segments.add(BreadcrumbSegment(currentPlaylist?.name ?: "", null))
            }
            NavigationLevel.FOLDERS -> {
                segments.add(BreadcrumbSegment(getString(R.string.music_browse_folders)) { navigateToFolders() })
                // Add intermediate folder segments from history
                for (i in 0 until folderPathHistory.size) {
                    val folder = folderPathHistory[i]
                    if (i < folderPathHistory.size - 1) {
                        // Clickable intermediate segment
                        val folderIndex = i
                        segments.add(BreadcrumbSegment(folder.name) {
                            // Navigate to this folder, trim history
                            folderPathHistory.subList(folderIndex + 1, folderPathHistory.size).clear()
                            navigateToFolder(folderPathHistory[folderIndex])
                        })
                    } else {
                        // Current folder (last segment, not clickable)
                        segments.add(BreadcrumbSegment(folder.name, null))
                    }
                }
            }
            NavigationLevel.FOLDER_TRACKS -> {
                segments.add(BreadcrumbSegment(getString(R.string.music_browse_folders)) { navigateToFolders() })
                // Add intermediate folder segments from history
                for (i in 0 until folderPathHistory.size) {
                    val folder = folderPathHistory[i]
                    if (i < folderPathHistory.size - 1) {
                        // Clickable intermediate segment
                        val folderIndex = i
                        segments.add(BreadcrumbSegment(folder.name) {
                            // Navigate to this folder, trim history
                            folderPathHistory.subList(folderIndex + 1, folderPathHistory.size).clear()
                            navigateToFolder(folderPathHistory[folderIndex])
                        })
                    } else {
                        // Current folder (last segment, not clickable)
                        segments.add(BreadcrumbSegment(folder.name, null))
                    }
                }
            }
            NavigationLevel.NAVIDROME_ROOT -> {
                segments.add(BreadcrumbSegment(getString(R.string.navidrome), null))
            }
            NavigationLevel.NAVIDROME_ARTIST -> {
                segments.add(BreadcrumbSegment(getString(R.string.navidrome)) { navigateToNavidromeRoot() })
                segments.add(BreadcrumbSegment(currentNavidromeArtist?.name ?: "", null))
            }
            NavigationLevel.NAVIDROME_ALBUM -> {
                segments.add(BreadcrumbSegment(getString(R.string.navidrome)) { navigateToNavidromeRoot() })
                val artist = currentNavidromeArtist
                segments.add(BreadcrumbSegment(artist?.name ?: "") {
                    artist?.let { navigateToNavidromeArtist(it) }
                })
                segments.add(BreadcrumbSegment(currentNavidromeAlbum?.name ?: "", null))
            }
        }

        // Create views for each segment
        val density = resources.displayMetrics.density
        val textColorClickable = MaterialColors.getColor(
            this,
            R.attr.a2uMusicAccent,
            ContextCompat.getColor(this, R.color.music_accent)
        )
        val textColorCurrent = ContextCompat.getColor(this, R.color.music_text_primary)

        segments.forEachIndexed { index, segment ->
            // Add separator before this segment (except for first)
            if (index > 0) {
                val separator = ImageView(this).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    layoutParams = LinearLayout.LayoutParams(
                        (18 * density).toInt(),
                        (18 * density).toInt()
                    ).apply {
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        marginStart = (2 * density).toInt()
                        marginEnd = (2 * density).toInt()
                    }
                    setColorFilter(textColorCurrent)
                }
                breadcrumbContainer.addView(separator)
            }

            // Add text view for segment
            val textView = TextView(this).apply {
                text = segment.label
                textSize = 16f
                maxLines = 1
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                if (segment.onClick != null && index < segments.size - 1) {
                    // Clickable segment (not the last one)
                    setTextColor(textColorClickable)
                    setOnClickListener { segment.onClick.invoke() }
                    isClickable = true
                    isFocusable = true
                    background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
                    setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                } else {
                    // Current/last segment (not clickable)
                    setTextColor(textColorCurrent)
                    setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                }
            }
            breadcrumbContainer.addView(textView)
        }

        // Scroll to end to show current location
        breadcrumbScroll.post {
            breadcrumbScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }

        // Update menu visibility based on navigation level
        updateMenuVisibility()
    }

    private data class BreadcrumbSegment(
        val label: String,
        val onClick: (() -> Unit)?
    )

    private fun updateMenuVisibility() {
        val menu = toolbar.menu
        // Show "Create playlist" only when in PLAYLISTS or PLAYLIST_TRACKS level
        menu.findItem(R.id.action_create_playlist)?.isVisible =
            currentLevel == NavigationLevel.PLAYLISTS || currentLevel == NavigationLevel.PLAYLIST_TRACKS
    }

    private fun updateStatsForRoot() {
        statsInfo.text = getString(
            R.string.music_stats_format,
            MusicLibrary.getTotalArtistCount(),
            MusicLibrary.getTotalAlbumCount(),
            MusicLibrary.getTotalTrackCount()
        )
    }

    private fun updateStatsForArtists() {
        statsInfo.text = getString(
            R.string.music_stats_format,
            MusicLibrary.getTotalArtistCount(),
            MusicLibrary.getTotalAlbumCount(),
            MusicLibrary.getTotalTrackCount()
        )
    }

    private fun updateStatsForAlbums(artist: Artist) {
        val albumText = resources.getQuantityString(
            R.plurals.music_album_count, artist.albumCount, artist.albumCount
        )
        val trackText = resources.getQuantityString(
            R.plurals.music_track_count_plural, artist.trackCount, artist.trackCount
        )
        statsInfo.text = getString(R.string.music_album_track_summary, albumText, trackText)
    }

    private fun updateStatsForTracks(album: Album) {
        val trackText = resources.getQuantityString(
            R.plurals.music_track_count_plural, album.trackCount, album.trackCount
        )
        val year = album.year
        statsInfo.text = if (year != null) {
            getString(
                R.string.music_track_summary_with_year,
                year.toString(),
                trackText,
                album.totalDurationFormatted
            )
        } else {
            getString(
                R.string.music_track_summary_no_year,
                trackText,
                album.totalDurationFormatted
            )
        }
    }

    private fun updateStatsForAlbumArtists() {
        statsInfo.text = getString(
            R.string.music_stats_format,
            MusicLibrary.getTotalAlbumArtistCount(),
            MusicLibrary.getTotalAlbumCount(),
            MusicLibrary.getTotalTrackCount()
        )
    }

    private fun updateStatsForAllAlbums() {
        val albumCount = MusicLibrary.getTotalAlbumCount()
        val trackCount = MusicLibrary.getTotalTrackCount()
        val albumText = resources.getQuantityString(
            R.plurals.music_album_count, albumCount, albumCount
        )
        val trackText = resources.getQuantityString(
            R.plurals.music_track_count_plural, trackCount, trackCount
        )
        statsInfo.text = getString(R.string.music_album_track_summary, albumText, trackText)
    }

    /**
     * Demande la permission de lecture avec gestion des conflits audio.
     * Si une autre source audio joue (Radio, MIDI), affiche un dialogue de choix.
     */
    private fun requestMusicPlayback(onGranted: () -> Unit) {
        AudioPlaybackManager.requestPlayback(
            AudioPlaybackManager.AudioSource.MUSIC,
            this
        ) {
            // Enregistre la source MUSIC avec un callback d'arrêt
            AudioPlaybackManager.registerPlayback(AudioPlaybackManager.AudioSource.MUSIC) {
                MusicPlaybackHolder.stop(this)
            }
            onGranted()
        }
    }

    private fun playTrack(track: MusicTrack) {
        requestMusicPlayback {
            // Set the playlist from currently displayed tracks when user clicks
            if (displayedTracks.isNotEmpty()) {
                // Pass playlist ID if playing from a custom playlist
                val playlistId = if (currentLevel == NavigationLevel.PLAYLIST_TRACKS &&
                    currentPlaylist != null &&
                    !currentPlaylist!!.isSystemPlaylist) {
                    currentPlaylist!!.id
                } else {
                    null
                }
                MusicPlaybackHolder.setPlaylist(displayedTracks, playlistId)
            }
            MusicPlaybackHolder.play(this, track)
        }
    }

    /**
     * Play all tracks from an album.
     * @param album The album to play
     * @param shuffle If true, enable shuffle mode before playing
     */
    private fun playAlbum(album: Album, shuffle: Boolean) {
        if (album.tracks.isEmpty()) return
        requestMusicPlayback {
            MusicPlaybackHolder.setPlaylist(album.tracks)
            if (shuffle) {
                // Enable shuffle if not already enabled
                if (!MusicPlaybackHolder.shuffleEnabled) {
                    MusicPlaybackHolder.toggleShuffle()
                }
                MusicPlaybackHolder.playAtIndex(this, (0 until album.tracks.size).random())
            } else {
                // Disable shuffle if enabled
                if (MusicPlaybackHolder.shuffleEnabled) {
                    MusicPlaybackHolder.toggleShuffle()
                }
                MusicPlaybackHolder.playAtIndex(this, 0)
            }
        }
    }

    /**
     * Play all tracks from an artist (all albums).
     * @param artist The artist whose tracks to play
     * @param shuffle If true, enable shuffle mode before playing
     */
    private fun playArtist(artist: Artist, shuffle: Boolean) {
        val allTracks = artist.albums.flatMap { it.tracks }
        if (allTracks.isEmpty()) return
        requestMusicPlayback {
            MusicPlaybackHolder.setPlaylist(allTracks)
            if (shuffle) {
                // Enable shuffle if not already enabled
                if (!MusicPlaybackHolder.shuffleEnabled) {
                    MusicPlaybackHolder.toggleShuffle()
                }
                MusicPlaybackHolder.playAtIndex(this, (0 until allTracks.size).random())
            } else {
                // Disable shuffle if enabled
                if (MusicPlaybackHolder.shuffleEnabled) {
                    MusicPlaybackHolder.toggleShuffle()
                }
                MusicPlaybackHolder.playAtIndex(this, 0)
            }
        }
    }

    /**
     * Play a list of tracks.
     * @param tracks The tracks to play
     * @param shuffle If true, enable shuffle mode before playing
     */
    private fun playTracks(tracks: List<MusicTrack>, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        requestMusicPlayback {
            MusicPlaybackHolder.setPlaylist(tracks)
            if (shuffle) {
                // Enable shuffle if not already enabled
                if (!MusicPlaybackHolder.shuffleEnabled) {
                    MusicPlaybackHolder.toggleShuffle()
                }
                MusicPlaybackHolder.playAtIndex(this, (0 until tracks.size).random())
            } else {
                // Disable shuffle if enabled
                if (MusicPlaybackHolder.shuffleEnabled) {
                    MusicPlaybackHolder.toggleShuffle()
                }
                MusicPlaybackHolder.playAtIndex(this, 0)
            }
        }
    }

    /**
     * Lecture aléatoire "Découverte" - joue les titres les moins écoutés.
     * Commence par les titres à 0 écoutes, puis 1, puis 2, etc.
     */
    private fun shuffleDiscovery() {
        val leastPlayedTracks = MusicStatsManager.getLeastPlayedTracks(MusicLibrary.getAllTracks())
        if (leastPlayedTracks.isNotEmpty()) {
            requestMusicPlayback {
                MusicPlaybackHolder.setPlaylist(leastPlayedTracks)
                MusicPlaybackHolder.toggleShuffle()
                if (!MusicPlaybackHolder.shuffleEnabled) {
                    MusicPlaybackHolder.toggleShuffle()
                }
                MusicPlaybackHolder.playAtIndex(this, (0 until leastPlayedTracks.size).random())

                val minPlayCount = MusicStatsManager.getPlayCount(leastPlayedTracks.first())
                Snackbar.make(
                    contentList,
                    getString(R.string.music_discovery_started, minPlayCount),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        } else {
            Snackbar.make(contentList, R.string.music_no_tracks, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                loadMusicLibrary()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Snackbar.make(contentList, R.string.music_permission_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.music_grant_permission) {
                        requestPermissionLauncher.launch(permission)
                    }
                    .show()
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun loadMusicLibrary(deepScan: Boolean = false, forceRescan: Boolean = false, withPopmScan: Boolean = false) {
        showLoading(true)
        statsInfo.text = getString(R.string.music_scanning)

        lifecycleScope.launch {
            // Initialize folders manager
            MusicFoldersManager.init(this@MusicPlayerActivity)

            // Load favorites, playlists, play counts and customizations first
            MusicFavoritesManager.loadFavorites(this@MusicPlayerActivity)
            MusicPlaylistManager.loadPlaylists()
            MusicPlayCountManager.init(this@MusicPlayerActivity)  // Initialise Room + migration JSON
            ArtistCustomizationManager.loadCustomizations(this@MusicPlayerActivity)
            AlbumFavoritesManager.loadFavorites(this@MusicPlayerActivity)

            MusicLibrary.init(this@MusicPlayerActivity)

            val shouldRescan = forceRescan

            var loadedFromCache = false
            var tracks: List<MusicTrack>

            // Essayer de charger la bibliothèque complète depuis le cache (structure organisée)
            if (!shouldRescan) {
                val cachedLibrary = MusicLibraryCache.loadFromCache(this@MusicPlayerActivity)
                if (cachedLibrary != null) {
                    // Restaurer directement sans réorganiser (instantané!)
                    MusicLibrary.restoreFromCache(cachedLibrary)
                    tracks = cachedLibrary.allTracks
                    loadedFromCache = true
                } else {
                    // Pas de cache, scanner et organiser
                    tracks = MusicScanner.scanConfiguredFolders(this@MusicPlayerActivity)
                    MusicLibrary.setTracks(tracks)
                    // Sauvegarder la structure organisée pour la prochaine fois
                    MusicLibrary.saveToCache(this@MusicPlayerActivity)
                }
            } else {
                // Force rescan demandé
                statsInfo.text = getString(R.string.music_scanning)
                tracks = MusicScanner.scanConfiguredFolders(this@MusicPlayerActivity)
                MusicLibrary.setTracks(tracks)
                // Sauvegarder la structure organisée
                MusicLibrary.saveToCache(this@MusicPlayerActivity)
            }

            // Deep scan: corrige les métadonnées manquantes en lisant les tags ID3
            // (MediaStore ne lit pas toujours correctement l'année, et ne lit ALBUM_ARTIST que sur Android 11+)
            if (deepScan) {
                // Lecture des album artists depuis les tags ID3 (optimisé : 1 fichier par album)
                val albumArtistsFixed = MusicLibrary.fixMissingAlbumArtists { current, total ->
                    statsInfo.text = getString(R.string.music_scanning) + " ($current/$total)"
                }

                // Lecture des années depuis les tags ID3
                MusicLibrary.fixMissingYears()

                // Update cache with fixed metadata
                MusicLibrary.saveToCache(this@MusicPlayerActivity)

                if (albumArtistsFixed) {
                    // Mettre à jour les tracks avec les nouveaux album artists
                    tracks = MusicLibrary.getAllTracks()
                }
            }

            // Complete scan: charge aussi les stats POPM (play counts, ratings)
            if (withPopmScan && tracks.isNotEmpty()) {
                statsInfo.text = getString(R.string.music_loading_stats)
                MusicStatsManager.loadStatsForTracks(tracks)
                MusicStatsManager.syncFavoritesFromRatings(tracks)
            }

            // Appliquer le tri des albums depuis les préférences utilisateur.
            // Doit être fait ICI, après le chargement complet (cache ou scan),
            // car applyPreferences() est appelé avant au démarrage sur une library vide.
            MusicLibrary.setAlbumSortOrder(preferences.albumSortOrder)

            // Build favorite IDs set for the adapter
            updateAdapterFavorites(tracks)

            // Update artist and album favorites in adapter
            updateArtistAdapterFavorites()
            updateAlbumAdapterFavorites()

            showLoading(false)

            if (tracks.isEmpty()) {
                showEmptyState(true)
            } else {
                showEmptyState(false)

                // Restaurer l'état de navigation si disponible, sinon aller à ROOT
                val savedState = pendingSavedInstanceState
                if (savedState != null) {
                    restoreNavigationState(savedState)
                    pendingSavedInstanceState = null  // Clear après restauration
                } else {
                    navigateToRoot()
                }

                // Check for new tracks in background (only if loaded from cache)
                if (loadedFromCache) {
                    checkForNewTracksInBackground()
                }
            }
        }
    }

    /**
     * Vérifie en arrière-plan si de nouvelles pistes ont été ajoutées.
     * Affiche un Snackbar si des nouvelles pistes sont détectées.
     */
    private fun checkForNewTracksInBackground() {
        lifecycleScope.launch {
            val newTracksCount = MusicCacheManager.checkForNewTracks(this@MusicPlayerActivity)
            if (newTracksCount != null && newTracksCount > 0) {
                Snackbar.make(
                    contentList,
                    resources.getQuantityString(R.plurals.music_new_tracks_detected, newTracksCount, newTracksCount),
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.music_refresh) {
                    loadMusicLibrary(forceRescan = true)
                }.show()
            }
        }
    }

    /**
     * Lance un scan complet des tags POPM de tous les fichiers MP3.
     * Lit les play counts et ratings stockés dans les fichiers et les importe.
     * Appelé depuis MusicSettingsActivity.
     */
    @Suppress("unused")
    fun performFullPopmScan() {
        val tracks = MusicLibrary.getAllTracks()
        if (tracks.isEmpty()) return

        showLoading(true)
        statsInfo.text = getString(R.string.music_loading_stats)

        lifecycleScope.launch {
            // Charger les stats POPM de tous les fichiers MP3
            MusicStatsManager.loadStatsForTracks(tracks)

            // Synchroniser les favoris depuis les ratings POPM
            val importedCount = MusicStatsManager.syncFavoritesFromRatings(tracks)
            if (importedCount > 0) {
                updateAdapterFavorites(tracks)
            }

            showLoading(false)

            // Forcer le rebind des play counts dans l'adapteur :
            // DiffUtil ne détecte pas les changements de play count (les MusicTrack
            // objects sont identiques), donc submitList seul ne rebinde pas les items.
            trackAdapter.refreshPlayCountCache()

            // Rafraîchir la vue actuelle (pour les favoris, réorganisation, etc.)
            refreshCurrentView()
        }
    }

    /**
     * Lance la migration des tags ID3 avec un dialogue de progression.
     * Analyse d'abord la bibliothèque, puis effectue la migration si nécessaire.
     */
    private fun performTagMigration() {
        val tracks = MusicLibrary.getAllTracks()
        if (tracks.isEmpty()) return

        // Afficher un dialogue de progression pendant l'analyse
        val progressDialog = AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_tag_migration_title)
            .setMessage(R.string.music_settings_tag_migration_analyzing)
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            // Analyser la bibliothèque
            val analysis = MusicTagMigrationManager.analyzeLibrary(tracks)

            progressDialog.dismiss()

            if (!analysis.needsMigration) {
                // Pas de migration nécessaire
                AlertDialog.Builder(this@MusicPlayerActivity)
                    .setTitle(R.string.music_settings_tag_migration_title)
                    .setMessage(R.string.music_settings_tag_migration_no_migration_needed)
                    .setPositiveButton(R.string.common_ok, null)
                    .show()
                return@launch
            }

            // Afficher le résultat de l'analyse et demander confirmation
            val message = getString(
                R.string.music_settings_tag_migration_analysis_result,
                analysis.id3v22Count,
                analysis.id3v23Count,
                analysis.id3v24Count
            )

            AlertDialog.Builder(this@MusicPlayerActivity)
                .setTitle(R.string.music_settings_tag_migration_title)
                .setMessage(message)
                .setPositiveButton(R.string.music_settings_tag_migration_start) { _, _ ->
                    // Lancer la migration avec progression
                    startTagMigrationWithProgress(tracks)
                }
                .setNegativeButton(R.string.music_cancel, null)
                .show()
        }
    }

    /**
     * Exécute la migration avec un dialogue de progression mis à jour en temps réel.
     */
    private fun startTagMigrationWithProgress(tracks: List<MusicTrack>) {
        // Créer un dialogue de progression
        val progressDialogView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        val artistText = progressDialogView.findViewById<TextView>(android.R.id.text1)
        val fileText = progressDialogView.findViewById<TextView>(android.R.id.text2)

        val progressDialog = AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_tag_migration_progress_title)
            .setView(progressDialogView)
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            // Observer l'état de la migration
            val job = launch {
                MusicTagMigrationManager.migrationState.collect { state ->
                    if (state.isRunning) {
                        artistText.text = getString(
                            R.string.music_settings_tag_migration_progress_artist,
                            state.currentArtist
                        )
                        fileText.text = getString(
                            R.string.music_settings_tag_migration_progress_count,
                            state.processedFiles,
                            state.totalFiles
                        )
                    }
                }
            }

            // Lancer la migration
            val result = MusicTagMigrationManager.migrateAllTags(applicationContext, tracks)

            // Arrêter l'observation
            job.cancel()
            progressDialog.dismiss()

            // Réinitialiser l'état
            MusicTagMigrationManager.resetState()

            // Afficher le résultat
            val resultMessage = getString(
                R.string.music_settings_tag_migration_complete,
                result.upgraded,
                result.errors
            )

            AlertDialog.Builder(this@MusicPlayerActivity)
                .setTitle(R.string.music_settings_tag_migration_title)
                .setMessage(resultMessage)
                .setPositiveButton(R.string.common_ok, null)
                .show()
        }
    }

    private fun updateAdapterFavorites(tracks: List<MusicTrack>) {
        val favoriteIds = tracks
            .filter { MusicFavoritesManager.isFavorite(it) }
            .map { it.id }
            .toSet()
        trackAdapter.setFavorites(favoriteIds)
    }

    /**
     * Lance une vérification automatique des tags POPM pour les albums et playlists
     * de ≤ 100 pistes. Lit les tags ID3 directement depuis les fichiers et met à jour
     * l'affichage uniquement si des différences sont détectées avec le cache.
     */
    private fun triggerPopmVerificationIfNeeded(tracks: List<MusicTrack>) {
        if (tracks.size > 100) return

        // Annuler tout scan précédent en cours (navigation rapide entre albums)
        popmVerificationJob?.cancel()
        popmVerificationJob = lifecycleScope.launch {
            // Capturer les états du cache avant le scan
            val playCountsBefore = tracks.associate { it.id to MusicStatsManager.getPlayCount(it) }
            val favoritesBefore = tracks.associate { it.id to MusicFavoritesManager.isFavorite(it) }

            // Scan POPM : lecture des tags ID3 depuis le disque
            MusicStatsManager.loadStatsForTracks(tracks)

            if (!isActive) return@launch

            // Synchroniser les favoris depuis les ratings POPM
            MusicStatsManager.syncFavoritesFromRatings(tracks)

            if (!isActive) return@launch

            // Détecter les changements et mettre à jour l'affichage si nécessaire
            val favoritesChanged = tracks.any { track ->
                MusicFavoritesManager.isFavorite(track) != (favoritesBefore[track.id] ?: false)
            }
            val playCountsChanged = tracks.any { track ->
                MusicStatsManager.getPlayCount(track) != (playCountsBefore[track.id] ?: 0L)
            }

            if (favoritesChanged) {
                updateAdapterFavorites(tracks)
            }
            if (playCountsChanged && preferences.showPlayCount) {
                trackAdapter.refreshPlayCountCache()
            }
        }
    }

    private fun restorePlaybackState() {
        var currentTrack = MusicPlaybackHolder.getCurrentTrack()

        // Si pas de track chargée, essayer de restaurer depuis la persistance (après kill de l'app)
        if (currentTrack == null) {
            val restored = MusicPlaybackHolder.restoreQueueOnly(this)
            if (restored) {
                currentTrack = MusicPlaybackHolder.getCurrentTrack()
            }
        }

        if (currentTrack != null) {
            showMiniPlayer()
            updateMiniPlayer(currentTrack)
            updateMiniPlayerPlayState(MusicPlaybackHolder.isPlaying())

            // Toujours synchroniser l'indicateur de lecture, quel que soit le niveau de navigation courant.
            // Sans ce fix, si le service a survécu au kill (process non tué), currentTrack != null mais
            // onTrackChanged n'est jamais redéclenché pour la nouvelle instance d'Activity, ce qui laisse
            // currentlyPlayingId à -1 (ou à la valeur périmée du kill) dans l'adapter.
            trackAdapter.setCurrentlyPlaying(currentTrack.id)
            folderContentAdapter.setCurrentlyPlaying(currentTrack.id)
        }
    }

    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
        contentList.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showEmptyState(show: Boolean) {
        emptyState.visibility = if (show) View.VISIBLE else View.GONE
        contentList.visibility = if (show) View.GONE else View.VISIBLE
        statsInfo.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showPermissionDeniedMessage() {
        Snackbar.make(contentList, R.string.music_permission_denied, Snackbar.LENGTH_LONG).show()
        showEmptyState(true)
    }

    private fun showMiniPlayer() {
        miniPlayer.visibility = View.VISIBLE
    }

    private fun updateMiniPlayer(track: MusicTrack) {
        miniTitle.text = track.title
        miniArtist.text = track.artist

        // Try to load album art
        track.albumArtUri?.let { uri ->
            if (uri.scheme == "http" || uri.scheme == "https") {
                // Navidrome: load asynchronously via OkHttp
                AlbumImageCache.loadFromHttpUri(
                    imageView = miniAlbumArt,
                    uri = uri,
                    defaultIconResId = R.drawable.ic_music_album_placeholder,
                    scope = lifecycleScope
                )
            } else {
                try {
                    miniAlbumArt.setImageURI(null)
                    miniAlbumArt.setImageURI(uri)
                    if (miniAlbumArt.drawable == null) {
                        miniAlbumArt.setImageResource(R.drawable.ic_music_album_placeholder)
                    }
                } catch (_: Exception) {
                    miniAlbumArt.setImageResource(R.drawable.ic_music_album_placeholder)
                }
            }
        } ?: run {
            miniAlbumArt.setImageResource(R.drawable.ic_music_album_placeholder)
        }
    }

    private fun updateMiniPlayerPlayState(isPlaying: Boolean) {
        val icon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        miniBtnPlayPause.setImageResource(icon)
    }

    private fun updateMiniPlayerProgress(position: Long, duration: Long) {
        if (duration > 0) {
            val progress = ((position.toFloat() / duration.toFloat()) * 100).toInt()
            miniProgress.progress = progress
        }
    }

    // MusicPlaybackHolder.PlayerListener implementation

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            updateMiniPlayerPlayState(isPlaying)
            MusicPlaybackHolder.updateForegroundService(this)
        }
    }

    override fun onTrackChanged(track: MusicTrack?) {
        // Appliquer les éditions de tags en attente pour le morceau précédent
        // (les éditions sont différées quand le fichier est en cours de lecture)
        lifecycleScope.launch {
            PendingTagEditManager.applyPendingEdits(this@MusicPlayerActivity)
        }

        runOnUiThread {
            if (track != null) {
                showMiniPlayer()
                updateMiniPlayer(track)

                // Met à jour l'indicateur de lecture sur TOUS les niveaux où des tracks sont affichés
                trackAdapter.setCurrentlyPlaying(track.id)
                folderContentAdapter.setCurrentlyPlaying(track.id)
            } else {
                // Aucun track en lecture, effacer l'indicateur
                trackAdapter.setCurrentlyPlaying(-1)
                folderContentAdapter.setCurrentlyPlaying(-1)
            }
        }
    }

    override fun onProgressChanged(position: Long, duration: Long) {
        runOnUiThread {
            updateMiniPlayerProgress(position, duration)
        }
    }

    override fun onError(error: PlaybackException) {
        runOnUiThread {
            Snackbar.make(contentList, getString(R.string.music_playback_error, error.message ?: ""), Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onPlaylistChanged(playlist: List<MusicTrack>) {
        // Not used here
    }

    override fun onPlayCountIncremented(track: MusicTrack, newCount: Long) {
        runOnUiThread {
            // Met à jour le compteur dans la liste des tracks
            trackAdapter.updatePlayCount(track.id)

            // Met à jour le mini player si c'est le track actuel
            val currentTrack = MusicPlaybackHolder.getCurrentTrack()
            if (currentTrack?.id == track.id) {
                updateMiniPlayer(track)
            }

            // Rafraîchir les playlists système si on est dessus (les plus écoutés)
            if (currentLevel == NavigationLevel.PLAYLISTS) {
                // Le compteur a changé, les playlists dynamiques pourraient avoir changé
                refreshPlaylistsList()
            }
        }
    }

    // ========== State Restoration for Configuration Changes ==========

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Sauvegarder l'état de navigation
        outState.putString("currentLevel", currentLevel.name)
        outState.putString("currentSearchQuery", currentSearchQuery)
        outState.putBoolean("isInAlbumArtistMode", isInAlbumArtistMode)

        // Sauvegarder les contextes parents
        parentAlbumsLevel?.let { outState.putString("parentAlbumsLevel", it.name) }
        parentArtistsLevel?.let { outState.putString("parentArtistsLevel", it.name) }

        // Sauvegarder les éléments sélectionnés (par nom pour retrouver après reload)
        currentArtist?.let { outState.putString("currentArtistName", it.name) }
        currentAlbum?.let {
            outState.putString("currentAlbumName", it.name)
            outState.putString("currentAlbumArtist", it.artist)
        }
        currentPlaylist?.let { outState.putString("currentPlaylistId", it.id) }
        currentFolder?.let { outState.putString("currentFolderPath", it.path) }

        // Sauvegarder la position de scroll
        (contentList.layoutManager as? LinearLayoutManager)?.let { layoutManager ->
            outState.putInt("scrollPosition", layoutManager.findFirstVisibleItemPosition())
        }
    }

    private fun restoreNavigationState(savedInstanceState: Bundle) {
        val savedLevel = savedInstanceState.getString("currentLevel")?.let {
            try { NavigationLevel.valueOf(it) } catch (_: Exception) { null }
        }

        if (savedLevel == null || savedLevel == NavigationLevel.ROOT) {
            navigateToRoot()
            return
        }

        currentSearchQuery = savedInstanceState.getString("currentSearchQuery", "")
        isInAlbumArtistMode = savedInstanceState.getBoolean("isInAlbumArtistMode", false)

        // Restaurer les contextes parents
        savedInstanceState.getString("parentAlbumsLevel")?.let {
            try { parentAlbumsLevel = NavigationLevel.valueOf(it) } catch (_: Exception) {}
        }
        savedInstanceState.getString("parentArtistsLevel")?.let {
            try { parentArtistsLevel = NavigationLevel.valueOf(it) } catch (_: Exception) {}
        }

        // Restaurer la navigation selon le niveau
        when (savedLevel) {
            NavigationLevel.ARTISTS -> navigateToArtists()
            NavigationLevel.ALBUM_ARTISTS -> navigateToAlbumArtists()
            NavigationLevel.FAVORITE_ARTISTS -> navigateToFavoriteArtists()
            NavigationLevel.ALL_ALBUMS -> navigateToAllAlbums()
            NavigationLevel.FAVORITE_ALBUMS -> navigateToFavoriteAlbums()
            NavigationLevel.PLAYLISTS -> navigateToPlaylists()
            NavigationLevel.FOLDERS -> navigateToFolders()
            NavigationLevel.SEARCH -> {
                if (currentSearchQuery.isNotEmpty()) {
                    performSearch(currentSearchQuery)
                } else {
                    navigateToRoot()
                }
            }
            NavigationLevel.ALBUMS -> {
                val artistName = savedInstanceState.getString("currentArtistName")
                val artist = artistName?.let { MusicLibrary.getArtist(it) }
                if (artist != null) {
                    navigateToArtist(artist)
                } else {
                    navigateToRoot()
                }
            }
            NavigationLevel.TRACKS -> {
                val albumName = savedInstanceState.getString("currentAlbumName")
                val albumArtist = savedInstanceState.getString("currentAlbumArtist")
                val album = if (albumName != null && albumArtist != null) {
                    MusicLibrary.getAlbum(albumArtist, albumName)
                } else null
                if (album != null) {
                    navigateToAlbum(album)
                } else {
                    navigateToRoot()
                }
            }
            NavigationLevel.PLAYLIST_TRACKS -> {
                val playlistId = savedInstanceState.getString("currentPlaylistId")
                val playlistData = playlistId?.let { MusicPlaylistManager.getPlaylist(it) }
                if (playlistData != null) {
                    navigateToPlaylistTracks(Playlist(id = playlistData.id, name = playlistData.name))
                } else {
                    navigateToPlaylists()
                }
            }
            NavigationLevel.FOLDER_TRACKS -> {
                val folderPath = savedInstanceState.getString("currentFolderPath")
                val folder = folderPath?.let { MusicLibrary.getFolderAtPath(it) }
                if (folder != null) {
                    navigateToFolderTracks(folder)
                } else {
                    navigateToFolders()
                }
            }
            else -> navigateToRoot()
        }

        // Restaurer la position de scroll
        savedInstanceState.getInt("scrollPosition", 0).let { position ->
            if (position > 0) {
                contentList.post {
                    (contentList.layoutManager as? LinearLayoutManager)?.scrollToPosition(position)
                }
            }
        }
    }
}
