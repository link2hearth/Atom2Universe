package com.Atom2Universe.app.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Classe pour récupérer les métadonnées techniques d'un fichier audio.
 */
object MusicFileInfo {

    private const val TAG = "MusicFileInfo"

    init {
        try {
            Logger.getLogger("org.jaudiotagger").level = Level.OFF
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Informations techniques d'un fichier audio
     */
    data class AudioFileInfo(
        val format: String = "",
        val bitrate: String = "",
        val sampleRate: String = "",
        val channels: Int = 0,
        val fileSize: Long = 0,
        val dateModified: Long = 0,
        val dateAdded: Long = 0,
        val isVBR: Boolean = false,
        val encodingType: String = ""
    )

    /**
     * Récupère les informations techniques d'un fichier audio.
     */
    suspend fun getAudioFileInfo(context: Context, track: MusicTrack): AudioFileInfo = withContext(Dispatchers.IO) {
        try {
            val filePath = track.filePath ?: return@withContext AudioFileInfo()
            val file = File(filePath)

            if (!file.exists()) {
                return@withContext AudioFileInfo()
            }

            // Infos de base du fichier
            val fileSize = file.length()
            val dateModified = file.lastModified()

            // Utiliser jaudiotagger pour les infos audio détaillées
            try {
                val audioFile = AudioFileIO.read(file)
                val audioHeader = audioFile.audioHeader

                return@withContext AudioFileInfo(
                    format = audioHeader.format ?: getFormatFromExtension(filePath),
                    bitrate = formatBitrate(audioHeader.bitRateAsNumber, audioHeader.isVariableBitRate),
                    sampleRate = formatSampleRate(audioHeader.sampleRateAsNumber),
                    channels = getChannelCount(audioHeader.channels),
                    fileSize = fileSize,
                    dateModified = dateModified,
                    isVBR = audioHeader.isVariableBitRate,
                    encodingType = audioHeader.encodingType ?: ""
                )
            } catch (e: Exception) {
                Log.w(TAG, "jaudiotagger failed, falling back to MediaMetadataRetriever: ${e.message}")
            }

            // Fallback: MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, track.uri)

                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""

                return@withContext AudioFileInfo(
                    format = getFormatFromMimeType(mimeType) ?: getFormatFromExtension(filePath),
                    bitrate = if (bitrate > 0) formatBitrate(bitrate, false) else "",
                    sampleRate = "",
                    channels = 0,
                    fileSize = fileSize,
                    dateModified = dateModified
                )
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio file info: ${e.message}")
            return@withContext AudioFileInfo()
        }
    }

    private fun getFormatFromExtension(filePath: String): String {
        return when (filePath.substringAfterLast('.').lowercase()) {
            "mp3" -> "MP3"
            "flac" -> "FLAC"
            "m4a", "aac" -> "AAC"
            "ogg" -> "OGG Vorbis"
            "opus" -> "Opus"
            "wav" -> "WAV"
            "wma" -> "WMA"
            else -> filePath.substringAfterLast('.').uppercase()
        }
    }

    private fun getFormatFromMimeType(mimeType: String): String? {
        return when {
            mimeType.contains("mp3") || mimeType.contains("mpeg") -> "MP3"
            mimeType.contains("flac") -> "FLAC"
            mimeType.contains("m4a") || mimeType.contains("mp4") -> "AAC"
            mimeType.contains("ogg") -> "OGG Vorbis"
            mimeType.contains("opus") -> "Opus"
            mimeType.contains("wav") -> "WAV"
            mimeType.contains("wma") -> "WMA"
            else -> null
        }
    }

    private fun formatBitrate(bitrate: Long, isVBR: Boolean): String {
        return if (isVBR) {
            "~$bitrate kbps (VBR)"
        } else {
            "$bitrate kbps"
        }
    }

    private fun formatSampleRate(sampleRate: Int): String {
        return when {
            sampleRate >= 1000 -> "${sampleRate / 1000.0} kHz"
            sampleRate > 0 -> "$sampleRate Hz"
            else -> ""
        }
    }

    private fun getChannelCount(channelsStr: String?): Int {
        return when (channelsStr?.lowercase()) {
            "mono", "1" -> 1
            "stereo", "2" -> 2
            "joint stereo" -> 2
            else -> channelsStr?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        }
    }

    /**
     * Formate une taille en bytes de manière lisible.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes o"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f Ko", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f Mo", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.getDefault(), "%.2f Go", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Formate un timestamp en date lisible.
     */
    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}
