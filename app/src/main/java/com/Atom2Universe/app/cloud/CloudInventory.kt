package com.Atom2Universe.app.cloud

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.sync.DeviceIdentity
import com.Atom2Universe.app.music.sync.DriveFileInfo
import com.Atom2Universe.app.music.sync.GoogleDriveAppDataClient
import com.Atom2Universe.app.music.sync.GoogleSignInManager
import com.Atom2Universe.app.music.sync.model.ListenEventsSyncFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ce que l'application occupe dans le dossier applicatif Drive, domaine par domaine.
 *
 * Le classement se fait sur le NOM du fichier, parce que c'est la seule chose que
 * Drive nous donne sans télécharger le contenu. Chaque module publie des noms fixes
 * (`games_state.json`, `usage_sessions.json`…) ou un préfixe (`a2u_events_`), donc
 * la correspondance est exacte, pas devinée.
 *
 * Un fichier qu'aucune règle ne reconnaît tombe dans [CloudCategory.UNKNOWN] plutôt
 * que d'être rangé au hasard : mieux vaut une ligne « non reconnu » visible qu'une
 * taille silencieusement attribuée au mauvais domaine.
 */
object CloudInventory {

    private const val TAG = "CloudInventory"

    /** Ancien système de deltas, remplacé par les journaux par appareil. */
    private const val LEGACY_DELTA_PREFIX = "playcounts_device_"

    /** Images d'artistes, un fichier binaire par artiste favori. */
    private const val ARTIST_IMAGE_PREFIX = "artist_img_"

    /** Fichiers de synchronisation musicale, hors journaux et images. */
    private val MUSIC_SYNC_FILES = setOf(
        "sync_manifest.json",
        "favorites.json",
        "lyrics.json",
        "eq_presets.json",
        "playlists_sync.json",
        "album_favorites_sync.json",
        "artist_favorites_sync.json"
    )

    /** Instantané complet publié par l'appareil principal, pour restauration. */
    private val BACKUP_FILES = setOf(
        "backup_manifest.json",
        "playcounts.json",
        "listen_events.json",
        "album_favorites.json",
        "artist_customizations.json",
        "playlists.json",
        "preferences.json",
        "artist_images_index.json"
    )

    /**
     * Les domaines affichés à l'écran. L'ordre de la déclaration est l'ordre
     * d'affichage : le plus gros et le plus parlant d'abord, le résiduel à la fin.
     */
    enum class CloudCategory(
        val labelRes: Int,
        val descRes: Int,
        /** Ce que l'on perd en supprimant ce domaine. Null = suppression interdite. */
        val deleteWarningRes: Int?,
        /**
         * Faut-il taper le mot de confirmation ?
         *
         * Réservé à ce qui se perd vraiment. Exiger le mot pour des fichiers
         * que plus rien ne lit userait le garde-fou jusqu'à ce qu'on le tape
         * sans lire — c'est le contraire du but.
         */
        val requiresTypedConfirm: Boolean = true,
        /** Couleur de la pastille et du segment de barre. */
        val colorRes: Int
    ) {
        LISTENS(
            R.string.cloud_cat_listens,
            R.string.cloud_cat_listens_desc,
            R.string.cloud_cat_listens_warning,
            colorRes = R.color.cloud_cat_listens_color
        ),
        MUSIC(
            R.string.cloud_cat_music,
            R.string.cloud_cat_music_desc,
            R.string.cloud_cat_music_warning,
            colorRes = R.color.cloud_cat_music_color
        ),
        ARTIST_IMAGES(
            R.string.cloud_cat_artist_images,
            R.string.cloud_cat_artist_images_desc,
            R.string.cloud_cat_artist_images_warning,
            colorRes = R.color.cloud_cat_artist_images_color
        ),
        GAMES(
            R.string.cloud_cat_games,
            R.string.cloud_cat_games_desc,
            R.string.cloud_cat_games_warning,
            colorRes = R.color.cloud_cat_games_color
        ),
        READING(
            R.string.cloud_cat_reading,
            R.string.cloud_cat_reading_desc,
            R.string.cloud_cat_reading_warning,
            colorRes = R.color.cloud_cat_reading_color
        ),
        STATS(
            R.string.cloud_cat_stats,
            R.string.cloud_cat_stats_desc,
            R.string.cloud_cat_stats_warning,
            colorRes = R.color.cloud_cat_stats_color
        ),
        BACKUP(
            R.string.cloud_cat_backup,
            R.string.cloud_cat_backup_desc,
            R.string.cloud_cat_backup_warning,
            colorRes = R.color.cloud_cat_backup_color
        ),
        OBSOLETE(
            R.string.cloud_cat_obsolete,
            R.string.cloud_cat_obsolete_desc,
            R.string.cloud_cat_obsolete_warning,
            requiresTypedConfirm = false,
            colorRes = R.color.cloud_cat_obsolete_color
        ),
        UNKNOWN(
            R.string.cloud_cat_unknown,
            R.string.cloud_cat_unknown_desc,
            null,
            colorRes = R.color.cloud_cat_unknown_color
        )
    }

    /** Le poids d'un domaine, et les fichiers qui le composent. */
    data class CategoryUsage(
        val category: CloudCategory,
        val files: List<DriveFileInfo>
    ) {
        val fileCount: Int get() = files.size
        val bytes: Long get() = files.sumOf { it.size }
        val ids: List<String> get() = files.map { it.id }
    }

    /**
     * Le journal d'écoutes d'un appareil.
     *
     * Celui de l'appareil courant est reconnaissable : c'est le seul que cette
     * installation réécrit. Les autres appartiennent à des appareils dont on ne
     * sait rien d'autre que la date de leur dernière publication — c'est ce qui
     * permet de repérer une tablette qu'on n'utilise plus.
     */
    data class DeviceJournal(
        val file: DriveFileInfo,
        val deviceId: String,
        val isThisDevice: Boolean
    )

    /** L'inventaire complet, prêt à afficher. */
    data class Report(
        val categories: List<CategoryUsage>,
        val journals: List<DeviceJournal>
    ) {
        val totalBytes: Long get() = categories.sumOf { it.bytes }
        val fileCount: Int get() = categories.sumOf { it.fileCount }

        fun usageOf(category: CloudCategory): CategoryUsage? =
            categories.firstOrNull { it.category == category }
    }

    /**
     * Interroge Drive et range tout.
     *
     * @return l'inventaire, ou null si l'on n'est pas connecté ou si Drive répond mal.
     */
    suspend fun load(context: Context): Report? = withContext(Dispatchers.IO) {
        val account = GoogleSignInManager(context).getSignedInAccount() ?: return@withContext null
        val client = GoogleDriveAppDataClient(context, account)
        val files = client.listFileDetails()

        // Une liste vide est ambiguë : soit le cloud est vide, soit Drive a échoué.
        // On la traite comme un inventaire vide, l'écran dira « rien à afficher ».
        val selfDeviceId = DeviceIdentity.getDeviceId(context)

        val grouped = files.groupBy { categorize(it.name) }
        val categories = CloudCategory.entries
            .mapNotNull { cat ->
                val inCat = grouped[cat].orEmpty()
                if (inCat.isEmpty()) null else CategoryUsage(cat, inCat)
            }

        val journals = files
            .filter { it.name.startsWith(ListenEventsSyncFile.FILE_PREFIX) }
            .map { file ->
                val deviceId = file.name
                    .removePrefix(ListenEventsSyncFile.FILE_PREFIX)
                    .removeSuffix(".json")
                DeviceJournal(
                    file = file,
                    deviceId = deviceId,
                    isThisDevice = deviceId == selfDeviceId
                )
            }
            .sortedByDescending { it.file.modifiedTime }

        Log.d(TAG, "Inventory: ${files.size} file(s) in ${categories.size} category(ies)")
        Report(categories, journals)
    }

    /**
     * Supprime les fichiers passés et renvoie combien l'ont réellement été.
     */
    suspend fun delete(context: Context, ids: List<String>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0
        val account = GoogleSignInManager(context).getSignedInAccount() ?: return@withContext 0
        GoogleDriveAppDataClient(context, account).deleteByIds(ids)
    }

    /**
     * À quel domaine appartient ce nom de fichier.
     *
     * Les préfixes passent avant les noms exacts : `playcounts_device_x.json` est
     * obsolète alors que `playcounts.json` est une sauvegarde, et seul l'ordre
     * des tests évite de confondre les deux.
     */
    fun categorize(name: String): CloudCategory = when {
        name.startsWith(ListenEventsSyncFile.FILE_PREFIX) -> CloudCategory.LISTENS
        name.startsWith(LEGACY_DELTA_PREFIX) -> CloudCategory.OBSOLETE
        name.startsWith(ARTIST_IMAGE_PREFIX) -> CloudCategory.ARTIST_IMAGES
        name == "games_state.json" -> CloudCategory.GAMES
        name == "reading_progress.json" -> CloudCategory.READING
        name == "usage_sessions.json" -> CloudCategory.STATS
        name in BACKUP_FILES -> CloudCategory.BACKUP
        name in MUSIC_SYNC_FILES -> CloudCategory.MUSIC
        else -> CloudCategory.UNKNOWN
    }

    /**
     * Taille lisible. On reste sur des multiples de 1024 et une seule décimale :
     * l'écran sert à repérer un domaine qui gonfle, pas à compter les octets.
     */
    fun formatSize(context: Context, bytes: Long): String = when {
        bytes < 1024 -> context.getString(R.string.cloud_size_bytes, bytes)
        bytes < 1024 * 1024 -> context.getString(R.string.cloud_size_kb, bytes / 1024.0)
        else -> context.getString(R.string.cloud_size_mb, bytes / (1024.0 * 1024.0))
    }
}
