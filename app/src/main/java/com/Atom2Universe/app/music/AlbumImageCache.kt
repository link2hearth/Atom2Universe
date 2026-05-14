package com.Atom2Universe.app.music

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import androidx.core.graphics.scale
import com.Atom2Universe.app.music.model.Album
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Cache d'images optimise pour les pochettes d'albums.
 * Utilise un LruCache en memoire + cache disque pour les thumbnails.
 */
object AlbumImageCache {

    // Taille du thumbnail en pixels
    private const val THUMBNAIL_SIZE = 256

    // Regex compilé une seule fois pour la génération des clés de cache
    private val UNSAFE_CHARS_REGEX = Regex("[^a-zA-Z0-9_-]")

    // Cache memoire (1/8 de la memoire disponible)
    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8

        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    // Dossier cache disque
    private var diskCacheDir: File? = null

    // Jobs en cours pour eviter les doublons (utilise ConcurrentHashMap pour thread-safety)
    private val loadingJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    // Tag pour identifier les ImageViews (evite les problemes de recyclage)
    private const val TAG_KEY = 0x7f0a0002

    /**
     * Initialise le cache disque.
     */
    fun init(context: Context) {
        if (diskCacheDir == null) {
            diskCacheDir = File(context.cacheDir, "album_thumbnails").apply {
                if (!exists()) mkdirs()
            }
        }
    }

    /**
     * Charge une pochette d'album de maniere asynchrone avec placeholder.
     */
    fun loadAlbumArt(
        imageView: ImageView,
        album: Album,
        defaultIconResId: Int,
        scope: CoroutineScope
    ) {
        val context = imageView.context
        init(context)

        val albumArtUri = album.albumArtUri

        // Pas de pochette
        if (albumArtUri == null) {
            imageView.setImageResource(defaultIconResId)
            return
        }

        // Cle unique pour cet album
        val cacheKey = generateCacheKey(album)

        // Annuler le chargement precedent pour cette ImageView
        val previousKey = imageView.getTag(TAG_KEY) as? String
        if (previousKey != null && previousKey != cacheKey) {
            loadingJobs[previousKey]?.cancel()
        }
        imageView.setTag(TAG_KEY, cacheKey)

        // Verifier le cache memoire d'abord
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // Afficher le placeholder immediatement
        imageView.setImageResource(defaultIconResId)

        // Charger en arriere-plan
        val job = scope.launch {
            try {
                val bitmap = loadBitmapAsync(context, cacheKey, albumArtUri)

                withContext(Dispatchers.Main) {
                    // Verifier que l'ImageView attend toujours cette image
                    if (imageView.getTag(TAG_KEY) == cacheKey && bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } finally {
                // Nettoyer le job terminé pour éviter les fuites mémoire
                loadingJobs.remove(cacheKey)
            }
        }

        loadingJobs[cacheKey] = job
    }

    /**
     * Charge une image en arriere-plan.
     */
    private suspend fun loadBitmapAsync(
        context: Context,
        cacheKey: String,
        albumArtUri: Uri
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Verifier le cache disque
            val diskFile = getDiskCacheFile(cacheKey)
            if (diskFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
            }

            // Charger depuis la source
            val sourceBitmap = decodeSampledBitmapFromUri(context.contentResolver, albumArtUri)

            if (sourceBitmap != null) {
                // Redimensionner si necessaire
                val thumbnail = createThumbnail(sourceBitmap)
                if (thumbnail != sourceBitmap) {
                    sourceBitmap.recycle()
                }

                // Sauvegarder dans le cache disque
                saveToDiskCache(diskFile, thumbnail)

                // Ajouter au cache memoire
                memoryCache.put(cacheKey, thumbnail)

                return@withContext thumbnail
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decode un bitmap depuis une Uri avec echantillonnage.
     * Supporte les URIs HTTP/HTTPS (pour Navidrome) et les URIs ContentResolver locales.
     */
    private fun decodeSampledBitmapFromUri(
        contentResolver: ContentResolver,
        uri: Uri
    ): Bitmap? {
        return if (uri.scheme == "http" || uri.scheme == "https") {
            decodeBitmapFromHttp(uri.toString())
        } else {
            decodeBitmapFromContentResolver(contentResolver, uri)
        }
    }

    private fun decodeBitmapFromHttp(url: String): Bitmap? {
        return try {
            val httpClient = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBitmapFromContentResolver(
        contentResolver: ContentResolver,
        uri: Uri
    ): Bitmap? {
        return try {
            // Lire les dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            // Calculer le facteur d'echantillonnage
            options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            options.inJustDecodeBounds = false

            // Decoder l'image
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calcule le facteur d'echantillonnage optimal.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Cree un thumbnail carre.
     */
    private fun createThumbnail(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height

        // Calculer le crop carre centre
        val cropSize = minOf(width, height)
        val x = (width - cropSize) / 2
        val y = (height - cropSize) / 2

        // Extraire le carre central
        val cropped = Bitmap.createBitmap(source, x, y, cropSize, cropSize)

        // Redimensionner
        val scaled = if (cropped.width != THUMBNAIL_SIZE) {
            cropped.scale(THUMBNAIL_SIZE, THUMBNAIL_SIZE, filter = true).also {
                if (cropped != source) cropped.recycle()
            }
        } else {
            cropped
        }

        return scaled
    }

    /**
     * Sauvegarde dans le cache disque.
     */
    private fun saveToDiskCache(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (_: Exception) {
            // Ignore
        }
    }

    /**
     * Genere une cle de cache unique.
     * Utilise un regex pré-compilé pour de meilleures performances.
     */
    private fun generateCacheKey(album: Album): String {
        val id = album.albumArtUri?.hashCode() ?: album.name.hashCode()
        return "album_${album.artist.hashCode()}_${album.name.hashCode()}_$id"
            .replace(UNSAFE_CHARS_REGEX, "")
    }

    /**
     * Obtient le fichier cache disque.
     */
    private fun getDiskCacheFile(cacheKey: String): File {
        return File(diskCacheDir, "$cacheKey.jpg")
    }

    /**
     * Charge une image depuis une URI HTTP/HTTPS directement (pour Navidrome).
     * Utilise la même infrastructure de cache que loadAlbumArt.
     */
    fun loadFromHttpUri(
        imageView: ImageView,
        uri: Uri,
        defaultIconResId: Int,
        scope: CoroutineScope
    ) {
        val context = imageView.context
        init(context)

        val cacheKey = "http_${uri.toString().hashCode()}"

        val previousKey = imageView.getTag(TAG_KEY) as? String
        if (previousKey != null && previousKey != cacheKey) {
            loadingJobs[previousKey]?.cancel()
        }
        imageView.setTag(TAG_KEY, cacheKey)

        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        imageView.setImageResource(defaultIconResId)

        val job = scope.launch {
            try {
                val bitmap = loadBitmapAsync(context, cacheKey, uri)
                withContext(Dispatchers.Main) {
                    if (imageView.getTag(TAG_KEY) == cacheKey && bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } finally {
                loadingJobs.remove(cacheKey)
            }
        }
        loadingJobs[cacheKey] = job
    }

    /**
     * Vide tout le cache.
     */
    @Suppress("unused")
    fun clearAll() {
        memoryCache.evictAll()
        diskCacheDir?.listFiles()?.forEach { it.delete() }
    }

    /**
     * Invalide le cache pour un album spécifique.
     * À appeler après avoir remplacé la pochette d'un album.
     */
    fun invalidateAlbum(album: Album) {
        val cacheKey = generateCacheKey(album)

        // Supprimer du cache mémoire
        memoryCache.remove(cacheKey)

        // Supprimer du cache disque
        getDiskCacheFile(cacheKey).delete()

        // Annuler le job de chargement en cours si présent
        loadingJobs[cacheKey]?.cancel()
        loadingJobs.remove(cacheKey)
    }

    /**
     * Invalide le cache pour plusieurs albums.
     */
    @Suppress("unused")
    fun invalidateAlbums(albums: List<Album>) {
        albums.forEach { invalidateAlbum(it) }
    }
}
