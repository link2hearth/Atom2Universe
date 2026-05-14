package com.Atom2Universe.app.music

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cache d'images optimise pour les tuiles d'artistes.
 * Utilise un LruCache en memoire + cache disque pour les thumbnails.
 */
object ArtistImageCache {

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
    private const val TAG_KEY = 0x7f0a0001

    /**
     * Initialise le cache disque.
     */
    fun init(context: Context) {
        if (diskCacheDir == null) {
            diskCacheDir = File(context.cacheDir, "artist_thumbnails").apply {
                if (!exists()) mkdirs()
            }
        }
    }

    /**
     * Charge une image de maniere asynchrone avec placeholder.
     */
    fun loadArtistImage(
        imageView: ImageView,
        artistName: String,
        customIconPath: String?,
        albumArtUri: Uri?,
        defaultIconResId: Int,
        scope: CoroutineScope
    ) {
        val context = imageView.context
        init(context)

        // Cle unique pour cet artiste
        val cacheKey = generateCacheKey(artistName, customIconPath, albumArtUri)

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
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            return
        }

        // Afficher le placeholder immediatement
        imageView.setImageResource(defaultIconResId)
        imageView.scaleType = ImageView.ScaleType.CENTER

        // Pas d'image a charger
        if (customIconPath == null && albumArtUri == null) {
            return
        }

        // Charger en arriere-plan
        val job = scope.launch {
            try {
                val bitmap = loadBitmapAsync(context, cacheKey, customIconPath, albumArtUri)

                withContext(Dispatchers.Main) {
                    // Verifier que l'ImageView attend toujours cette image
                    if (imageView.getTag(TAG_KEY) == cacheKey && bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
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
        customIconPath: String?,
        albumArtUri: Uri?
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
            val sourceBitmap = when {
                customIconPath != null && File(customIconPath).exists() -> {
                    decodeSampledBitmap(customIconPath)
                }
                albumArtUri != null -> {
                    if (albumArtUri.scheme == "http" || albumArtUri.scheme == "https") {
                        decodeBitmapFromHttp(albumArtUri.toString())
                    } else {
                        decodeSampledBitmapFromUri(context.contentResolver, albumArtUri)
                    }
                }
                else -> null
            }

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
     * Decode un bitmap avec echantillonnage pour economiser la memoire.
     */
    private fun decodeSampledBitmap(path: String): Bitmap? {
        return try {
            // Lire les dimensions sans charger l'image
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            // Calculer le facteur d'echantillonnage
            options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE)
            options.inJustDecodeBounds = false

            BitmapFactory.decodeFile(path, options)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Charge un bitmap depuis une URL HTTP/HTTPS.
     */
    private fun decodeBitmapFromHttp(url: String): Bitmap? {
        return try {
            val response = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
            response.body?.bytes()?.let { bytes ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decode un bitmap depuis une Uri avec echantillonnage.
     */
    private fun decodeSampledBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        return try {
            // Lire les dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            // Calculer le facteur d'echantillonnage
            options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE)
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
    private fun calculateInSampleSize(options: BitmapFactory.Options, size: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > size || width > size) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= size && halfWidth / inSampleSize >= size) {
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
            cropped.scale(THUMBNAIL_SIZE, THUMBNAIL_SIZE, true).also {
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
    private fun generateCacheKey(artistName: String, customIconPath: String?, albumArtUri: Uri?): String {
        val source = when {
            customIconPath != null -> "custom:${File(customIconPath).lastModified()}"
            albumArtUri != null -> "album:${albumArtUri.hashCode()}"
            else -> "default"
        }
        return "${artistName.lowercase().hashCode()}_$source".replace(UNSAFE_CHARS_REGEX, "")
    }

    /**
     * Obtient le fichier cache disque.
     */
    private fun getDiskCacheFile(cacheKey: String): File {
        return File(diskCacheDir, "$cacheKey.jpg")
    }

    /**
     * Invalide le cache pour un artiste (apres changement d'icone).
     */
    fun invalidateArtist(artistName: String) {
        val prefix = "${artistName.lowercase().hashCode()}_"

        // Supprimer du cache memoire
        val keysToRemove = memoryCache.snapshot().keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { memoryCache.remove(it) }

        // Supprimer du cache disque
        diskCacheDir?.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    /**
     * Vide tout le cache.
     */
    @Suppress("unused")
    fun clearAll() {
        memoryCache.evictAll()
        diskCacheDir?.listFiles()?.forEach { it.delete() }
    }
}
