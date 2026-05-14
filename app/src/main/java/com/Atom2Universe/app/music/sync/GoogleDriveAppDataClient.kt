@file:Suppress("DEPRECATION")

package com.Atom2Universe.app.music.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Date

/**
 * Client for Google Drive App Data folder operations.
 *
 * The App Data folder is a special hidden folder that:
 * - Is invisible to the user in Google Drive
 * - Can only be accessed by this app
 * - Is automatically deleted when the app is uninstalled
 * - Has no storage quota impact on user's Drive
 */
class GoogleDriveAppDataClient(
    private val context: Context,
    private val account: GoogleSignInAccount
) {
    companion object {
        private const val TAG = "GoogleDriveAppDataClient"
        private const val APP_DATA_FOLDER = "appDataFolder"
    }

    private val driveService: Drive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }

        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("A2U")
            .build()
    }

    /**
     * Reads a JSON file from the App Data folder.
     *
     * @param filename The name of the file to read
     * @return The file contents as a string, or null if not found
     */
    suspend fun readJsonFile(filename: String): String? = withContext(Dispatchers.IO) {
        try {
            val files = driveService.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setQ("name = '$filename'")
                .setFields("files(id, name)")
                .execute()

            val file = files.files.firstOrNull()
            if (file == null) {
                Log.d(TAG, "File not found: $filename")
                return@withContext null
            }

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(file.id)
                .executeMediaAndDownloadTo(outputStream)

            val content = outputStream.toString("UTF-8")
            Log.d(TAG, "Read file: $filename (${content.length} bytes)")
            content
        } catch (e: Exception) {
            Log.e(TAG, "Error reading $filename", e)
            null
        }
    }

    /**
     * Writes a JSON file to the App Data folder.
     * Creates the file if it doesn't exist, updates if it does.
     *
     * @param filename The name of the file to write
     * @param content The content to write
     * @return true if successful, false otherwise
     */
    suspend fun writeJsonFile(filename: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val existingFiles = driveService.files().list()
                    .setSpaces(APP_DATA_FOLDER)
                    .setQ("name = '$filename'")
                    .setFields("files(id)")
                    .execute()

                val mediaContent = ByteArrayContent.fromString("application/json", content)

                if (existingFiles.files.isNotEmpty()) {
                    // Update existing file
                    driveService.files().update(
                        existingFiles.files[0].id,
                        null,
                        mediaContent
                    ).execute()
                } else {
                    // Create new file
                    val fileMetadata = File().apply {
                        name = filename
                        parents = listOf(APP_DATA_FOLDER)
                    }
                    driveService.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute()
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing $filename", e)
                false
            }
        }

    /**
     * Lists all files in the App Data folder.
     *
     * @return List of file names
     */
    @Suppress("unused")
    suspend fun listFiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            val files = driveService.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setFields("files(id, name, modifiedTime)")
                .execute()
            files.files.map { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files", e)
            emptyList()
        }
    }

    /**
     * Lists delta files matching a pattern.
     *
     * @param pattern The pattern to match (e.g., "playcounts_device_")
     * @return List of matching file names
     */
    suspend fun listDeltaFiles(pattern: String = "playcounts_device_"): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val files = driveService.files().list()
                    .setSpaces(APP_DATA_FOLDER)
                    .setQ("name contains '$pattern'")
                    .setFields("files(id, name, modifiedTime)")
                    .execute()
                files.files.map { it.name }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing delta files", e)
                emptyList()
            }
        }

    /**
     * Deletes a file from the App Data folder.
     *
     * @param filename The name of the file to delete
     * @return true if successful or file didn't exist, false on error
     */
    suspend fun deleteFile(filename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val files = driveService.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setQ("name = '$filename'")
                .setFields("files(id)")
                .execute()

            files.files.forEach { file ->
                driveService.files().delete(file.id).execute()
                Log.d(TAG, "Deleted file: $filename")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting $filename", e)
            false
        }
    }

    /**
     * Gets the total storage used by App Data files.
     *
     * @return Storage used in bytes
     */
    @Suppress("unused")
    suspend fun getStorageUsed(): Long = withContext(Dispatchers.IO) {
        try {
            val files = driveService.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setFields("files(id, size)")
                .execute()

            files.files.sumOf { it.getSize() ?: 0L }.also {
                Log.d(TAG, "Storage used: $it bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage used", e)
            0L
        }
    }

    /**
     * Checks if a file exists in the App Data folder.
     *
     * @param filename The name of the file to check
     * @return true if the file exists
     */
    @Suppress("unused")
    suspend fun fileExists(filename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val files = driveService.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setQ("name = '$filename'")
                .setFields("files(id)")
                .execute()

            files.files.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if file exists: $filename", e)
            false
        }
    }

    /**
     * Deletes files older than a specified number of days.
     *
     * @param prefix File name prefix to match
     * @param olderThanDays Delete files older than this many days
     * @return Number of files deleted
     */
    suspend fun deleteOldFiles(prefix: String, olderThanDays: Int): Int =
        withContext(Dispatchers.IO) {
            try {
                val cutoffDate = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -olderThanDays)
                }.time

                val files = driveService.files().list()
                    .setSpaces(APP_DATA_FOLDER)
                    .setQ("name contains '$prefix'")
                    .setFields("files(id, name, modifiedTime)")
                    .execute()

                var deletedCount = 0
                files.files.filter { file ->
                    file.modifiedTime?.value?.let { time ->
                        Date(time).before(cutoffDate)
                    } == true
                }.forEach { file ->
                    driveService.files().delete(file.id).execute()
                    Log.d(TAG, "Deleted old file: ${file.name}")
                    deletedCount++
                }

                Log.d(TAG, "Deleted $deletedCount old files with prefix '$prefix'")
                deletedCount
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting old files", e)
                0
            }
        }

    // ==================== Binary File Operations (for images) ====================

    /**
     * Reads a binary file from the App Data folder.
     *
     * @param filename The name of the file to read
     * @return The file contents as ByteArray, or null if not found
     */
    suspend fun readBinaryFile(filename: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val files = driveService.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setQ("name = '$filename'")
                .setFields("files(id, name)")
                .execute()

            val file = files.files.firstOrNull()
            if (file == null) {
                Log.d(TAG, "Binary file not found: $filename")
                return@withContext null
            }

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(file.id)
                .executeMediaAndDownloadTo(outputStream)

            val bytes = outputStream.toByteArray()
            Log.d(TAG, "Read binary file: $filename (${bytes.size} bytes)")
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Error reading binary file: $filename", e)
            null
        }
    }

    /**
     * Writes a binary file (e.g., image) to the App Data folder.
     * Creates the file if it doesn't exist, updates if it does.
     *
     * @param filename The name of the file to write
     * @param content The binary content to write
     * @param mimeType The MIME type of the file (default: image/jpeg)
     * @return true if successful, false otherwise
     */
    suspend fun writeBinaryFile(
        filename: String,
        content: ByteArray,
        mimeType: String = "image/jpeg"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val existingFiles = driveService.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setQ("name = '$filename'")
                .setFields("files(id)")
                .execute()

            val mediaContent = ByteArrayContent(mimeType, content)

            if (existingFiles.files.isNotEmpty()) {
                // Update existing file
                driveService.files().update(
                    existingFiles.files[0].id,
                    null,
                    mediaContent
                ).execute()
            } else {
                // Create new file
                val fileMetadata = File().apply {
                    name = filename
                    parents = listOf(APP_DATA_FOLDER)
                }
                driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
            }
            Log.d(TAG, "Wrote binary file: $filename (${content.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing binary file: $filename", e)
            false
        }
    }

    /**
     * Lists files matching a prefix pattern.
     *
     * @param prefix The prefix to match (e.g., "artist_img_")
     * @return List of matching file names
     */
    suspend fun listFilesWithPrefix(prefix: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val files = driveService.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setQ("name contains '$prefix'")
                .setFields("files(id, name, modifiedTime)")
                .execute()
            files.files.map { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files with prefix: $prefix", e)
            emptyList()
        }
    }

    /**
     * Deletes multiple files by their names.
     *
     * @param filenames List of file names to delete
     * @return Number of files successfully deleted
     */
    @Suppress("unused")
    suspend fun deleteFiles(filenames: List<String>): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        for (filename in filenames) {
            if (deleteFile(filename)) {
                deletedCount++
            }
        }
        Log.d(TAG, "Deleted $deletedCount of ${filenames.size} files")
        deletedCount
    }

    /**
     * Deletes ALL files in the App Data folder.
     * This is a destructive operation that cannot be undone.
     *
     * @return Number of files deleted, or -1 on error
     */
    suspend fun deleteAllFiles(): Int = withContext(Dispatchers.IO) {
        try {
            val files = driveService.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setFields("files(id, name)")
                .execute()

            var deletedCount = 0
            files.files.forEach { file ->
                try {
                    driveService.files().delete(file.id).execute()
                    Log.d(TAG, "Deleted file: ${file.name}")
                    deletedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete file: ${file.name}", e)
                }
            }

            Log.d(TAG, "Deleted ALL files: $deletedCount total")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all files", e)
            -1
        }
    }
}
