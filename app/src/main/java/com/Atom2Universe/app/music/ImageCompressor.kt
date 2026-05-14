package com.Atom2Universe.app.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Utilitaire de compression d'images pour le lecteur musique.
 * Limite la taille des images pour eviter de surcharger la memoire et le stockage.
 */
object ImageCompressor {

    private const val TAG = "ImageCompressor"

    // Limites de taille en octets
    const val MAX_SIZE_BYTES = 200 * 1024  // 200 Ko
    const val MAX_SIZE_ARTIST_ICON = 100 * 1024  // 100 Ko pour les icones artistes (petites)
    const val MAX_SIZE_ALBUM_COVER = 200 * 1024  // 200 Ko pour les pochettes d'album

    // Dimensions maximales en pixels
    const val MAX_DIMENSION_ARTIST_ICON = 512  // Les icones sont affichees en 48dp max
    const val MAX_DIMENSION_ALBUM_COVER = 800  // Les pochettes sont affichees plus grandes

    /**
     * Compresse une image pour une icone d'artiste.
     * Redimensionne a 512x512 max et limite a 100Ko.
     *
     * @param inputPath Chemin de l'image source
     * @param outputPath Chemin de l'image de sortie
     * @return true si la compression a reussi
     */
    fun compressArtistIcon(inputPath: String, outputPath: String): Boolean {
        return compressImage(
            inputPath = inputPath,
            outputPath = outputPath,
            maxDimension = MAX_DIMENSION_ARTIST_ICON,
            maxSizeBytes = MAX_SIZE_ARTIST_ICON
        )
    }

    /**
     * Compresse une image pour une pochette d'album.
     * Redimensionne a 800x800 max et limite a 200Ko.
     *
     * @param inputPath Chemin de l'image source
     * @param outputPath Chemin de l'image de sortie
     * @return true si la compression a reussi
     */
    fun compressAlbumCover(inputPath: String, outputPath: String): Boolean {
        return compressImage(
            inputPath = inputPath,
            outputPath = outputPath,
            maxDimension = MAX_DIMENSION_ALBUM_COVER,
            maxSizeBytes = MAX_SIZE_ALBUM_COVER
        )
    }

    /**
     * Compresse une image avec les parametres specifies.
     *
     * @param inputPath Chemin de l'image source
     * @param outputPath Chemin de l'image de sortie
     * @param maxDimension Dimension maximale (largeur ou hauteur)
     * @param maxSizeBytes Taille maximale en octets
     * @return true si la compression a reussi
     */
    fun compressImage(
        inputPath: String,
        outputPath: String,
        maxDimension: Int = MAX_DIMENSION_ALBUM_COVER,
        maxSizeBytes: Int = MAX_SIZE_BYTES
    ): Boolean {
        try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                Log.e(TAG, "Input file does not exist: $inputPath")
                return false
            }

            // Lire les dimensions de l'image sans la charger en memoire
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputPath, options)

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: ${originalWidth}x${originalHeight}")
                return false
            }

            Log.d(TAG, "Original image: ${originalWidth}x${originalHeight}, size: ${inputFile.length() / 1024}Ko")

            // Calculer le facteur de sous-echantillonnage
            val inSampleSize = calculateInSampleSize(originalWidth, originalHeight, maxDimension)

            // Charger l'image avec sous-echantillonnage
            options.apply {
                inJustDecodeBounds = false
                this.inSampleSize = inSampleSize
            }

            var bitmap = BitmapFactory.decodeFile(inputPath, options)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap")
                return false
            }

            // Redimensionner si necessaire
            val scaledBitmap = scaleBitmapToMaxDimension(bitmap, maxDimension)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
                bitmap = scaledBitmap
            }

            Log.d(TAG, "Scaled image: ${bitmap.width}x${bitmap.height}")

            // Compresser en JPEG avec qualite adaptative
            val compressedBytes = compressToTargetSize(bitmap, maxSizeBytes)
            bitmap.recycle()

            if (compressedBytes == null) {
                Log.e(TAG, "Failed to compress to target size")
                return false
            }

            // Ecrire le fichier
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                fos.write(compressedBytes)
            }

            Log.d(TAG, "Compressed image saved: ${outputFile.length() / 1024}Ko -> $outputPath")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            return false
        }
    }

    /**
     * Calcule le facteur de sous-echantillonnage pour le decodage.
     */
    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        val maxOriginal = max(width, height)

        if (maxOriginal > maxDimension) {
            val halfMax = maxOriginal / 2
            while (halfMax / inSampleSize >= maxDimension) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Redimensionne un bitmap pour que sa plus grande dimension ne depasse pas maxDimension.
     */
    private fun scaleBitmapToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxOriginal = max(width, height)

        if (maxOriginal <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / maxOriginal.toFloat()
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Compresse un bitmap en JPEG avec une qualite adaptative pour atteindre la taille cible.
     */
    private fun compressToTargetSize(bitmap: Bitmap, maxSizeBytes: Int): ByteArray? {
        var quality = 90
        val minQuality = 20

        while (quality >= minQuality) {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()

            Log.d(TAG, "Compression at quality $quality: ${bytes.size / 1024}Ko")

            if (bytes.size <= maxSizeBytes) {
                return bytes
            }

            // Reduire la qualite de maniere plus agressive si on est loin de la cible
            val ratio = bytes.size.toFloat() / maxSizeBytes.toFloat()
            quality = when {
                ratio > 3 -> quality - 20
                ratio > 2 -> quality - 15
                ratio > 1.5 -> quality - 10
                else -> quality - 5
            }
        }

        // Derniere tentative avec qualite minimale
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, minQuality, baos)
        val bytes = baos.toByteArray()

        return if (bytes.size <= maxSizeBytes * 1.5) {
            // Accepter jusqu'a 1.5x la taille cible si on ne peut pas faire mieux
            bytes
        } else {
            Log.w(TAG, "Could not compress to target size, final: ${bytes.size / 1024}Ko")
            bytes // Retourner quand meme l'image compressee au maximum
        }
    }

    /**
     * Verifie si une image necessite une compression.
     */
    fun needsCompression(filePath: String, maxSizeBytes: Int = MAX_SIZE_BYTES): Boolean {
        val file = File(filePath)
        return file.exists() && file.length() > maxSizeBytes
    }

    /**
     * Obtient la taille d'un fichier en Ko.
     */
    fun getFileSizeKb(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) file.length() / 1024 else 0
    }
}
